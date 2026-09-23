package grading.execution;

import grading.submission.SubmissionPreparationService;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterFileValidationService;
import grading.tester.TesterValidationResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// Compiles and grades default-package assessment sources
public final class JavaGradingService {

    private static final Pattern TESTER_FILENAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*Tester\\.java");
    private final Duration compilationTimeout;
    private final Duration executionTimeout;
    private final List<Path> supportClasspath;
    private final BoundedProcessRunner runner = new BoundedProcessRunner();

    public JavaGradingService() {
        this(Duration.ofSeconds(30), Duration.ofSeconds(5), List.of());
    }

    public JavaGradingService(Duration compilationTimeout, Duration executionTimeout, List<Path> supportClasspath) {
        this.compilationTimeout = requirePositive(compilationTimeout);
        this.executionTimeout = requirePositive(executionTimeout);
        this.supportClasspath = supportClasspath.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        for (Path entry : this.supportClasspath) {
            if (!Files.isReadable(entry) || (!Files.isDirectory(entry) && !Files.isRegularFile(entry))) {
                throw new IllegalArgumentException("Unreadable support classpath entry: " + entry);
            }
        }
    }

    /**
     * Compiles a tester together with one student's question sources. The caller owns the returned
     * workspace and must close it, even if compilation fails. Source files are never modified.
     */
    public CompilationResult compile(List<Path> studentFiles, Path testerFile)
            throws IOException, InterruptedException {
        String testerFilename = testerFile.getFileName().toString();
        String questionId = questionId(testerFilename);
        if (studentFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one student source file is required.");
        }
        Path workspace = Files.createTempDirectory("cs102-grading-");
        try {
            Path sources = Files.createDirectory(workspace.resolve("sources"));
            Path classes = Files.createDirectory(workspace.resolve("classes"));
            Path emptySourcepath = Files.createDirectory(workspace.resolve("empty-sourcepath"));
            List<String> command = new ArrayList<>(List.of(
                    jdkTool("javac"), "-J-Xmx256m", "-encoding", "UTF-8", "--release", "21",
                    "-proc:none", "-implicit:none", "-classpath", classpath(classes, supportClasspath),
                    "-sourcepath", emptySourcepath.toString(), "-d", classes.toString()));
            for (Path source : Stream.concat(studentFiles.stream(), Stream.of(testerFile)).toList()) {
                String filename = source.getFileName().toString();
                if (!filename.endsWith(".java")) {
                    throw new IllegalArgumentException("Expected a Java source file: " + source);
                }
                Path copy = sources.resolve(filename);
                // no replacement: duplicate filenames must not overwrite a student or official tester
                Files.copy(source, copy);
                command.add(copy.toString());
            }
            ProcessResult process = runner.run(command, workspace, compilationTimeout);
            return new CompilationResult(questionId, testerFilename.replaceFirst("\\.java$", ""),
                    workspace, supportClasspath, process);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            try {
                CompilationResult.deleteWorkspace(workspace);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    // Runs only the tester's main method in a new JVM; does not close the compilation workspace.
    public QuestionGradingResult execute(CompilationResult compilation) throws IOException, InterruptedException {
        ProcessResult compiler = compilation.process();
        if (!compiler.succeeded()) {
            QuestionGradingResult.Status status = switch (compiler.status()) {
                case TIMED_OUT -> QuestionGradingResult.Status.COMPILATION_TIMEOUT;
                case OUTPUT_LIMIT_EXCEEDED -> QuestionGradingResult.Status.OUTPUT_LIMIT_EXCEEDED;
                case EXITED -> QuestionGradingResult.Status.COMPILATION_ERROR;
            };
            return failure(compilation.questionId(), status, compiler, null, "Compilation did not succeed.");
        }
        if (!Files.isDirectory(compilation.classesDirectory())) {
            throw new IOException("Compilation workspace is closed or missing.");
        }
        ProcessResult execution = runner.run(List.of(
                jdkTool("java"), "-Xmx256m", "-XX:-CreateCoredumpOnCrash",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Djava.io.tmpdir=" + compilation.workspace(),
                "-cp", classpath(compilation.classesDirectory(), compilation.supportClasspath()),
                compilation.testerClass()), compilation.workspace(), executionTimeout);
        if (!execution.succeeded()) {
            QuestionGradingResult.Status status = switch (execution.status()) {
                case TIMED_OUT -> QuestionGradingResult.Status.EXECUTION_TIMEOUT;
                case OUTPUT_LIMIT_EXCEEDED -> QuestionGradingResult.Status.OUTPUT_LIMIT_EXCEEDED;
                case EXITED -> QuestionGradingResult.Status.RUNTIME_ERROR;
            };
            return failure(compilation.questionId(), status, compiler, execution, "Tester did not finish successfully.");
        }

        List<String> lines = execution.stdout().lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        double score;
        try {
            score = Double.parseDouble(lines.isEmpty() ? "" : lines.getLast());
            if (!Double.isFinite(score) || score < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            return failure(compilation.questionId(), QuestionGradingResult.Status.INVALID_SCORE, compiler, execution,
                    "Tester must print a finite, nonnegative score as its last nonblank stdout line.");
        }
        int passed = (int) lines.stream().filter("Passed"::equals).count();
        int failed = (int) lines.stream().filter(line -> line.equals("Failed") || line.startsWith("Failed ->")).count();
        return new QuestionGradingResult(compilation.questionId(), QuestionGradingResult.Status.GRADED,
                score, passed, failed, compiler, execution, "Graded using the tester's final score.");
    }

    // compiles, executes, and removes all temporary files, including after failure
    public QuestionGradingResult gradeQuestion(List<Path> studentFiles, Path testerFile) throws InterruptedException {
        String questionId = questionId(testerFile.getFileName().toString());
        try (CompilationResult compilation = compile(studentFiles, testerFile)) {
            return execute(compilation);
        } catch (IOException exception) {
            return failure(questionId, QuestionGradingResult.Status.IO_ERROR, null, null, exception.getMessage());
        }
    }

    // invalid submissions receive explicit zero-score results; invalid shared testers are rejected.
    public StudentGradingResult gradeStudent(SubmissionValidationResult submission, TesterValidationResult testers)
            throws InterruptedException {
        Objects.requireNonNull(submission, "submission");
        if (!testers.isValid()
                || !testers.testerFiles().keySet().containsAll(TesterFileValidationService.requiredTesterFiles())) {
            throw new IllegalArgumentException("Import and validate all required tester files before grading.");
        }
        List<QuestionGradingResult> questions = new ArrayList<>();
        for (String testerFilename : TesterFileValidationService.requiredTesterFiles()) {
            String questionId = questionId(testerFilename);
            List<String> requiredSources = questionId.equals("Q3")
                    ? List.of("Q3.java", "ShapeComparator.java") : List.of(questionId + ".java");
            if (!submission.isReadyForGrading()
                    || !submission.javaFiles().keySet().containsAll(SubmissionPreparationService.requiredJavaFiles().keySet())) {
                questions.add(failure(questionId, QuestionGradingResult.Status.INVALID_SUBMISSION, null, null,
                        "Resolve submission validation errors before grading."));
                continue;
            }
            List<Path> sources = requiredSources.stream().map(submission.javaFiles()::get).toList();
            questions.add(gradeQuestion(sources, testers.testerFiles().get(testerFilename)));
        }
        return new StudentGradingResult(submission.username(), questions);
    }

    // processes students in input order
    // interruption cancels the batch after terminating active processes.
    public List<StudentGradingResult> gradeBatch(List<SubmissionValidationResult> submissions, TesterValidationResult testers)
            throws InterruptedException {
        List<StudentGradingResult> results = new ArrayList<>();
        for (SubmissionValidationResult submission : submissions) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Grading cancelled.");
            }
            results.add(gradeStudent(submission, testers));
        }
        return List.copyOf(results);
    }

    private static QuestionGradingResult failure(String questionId, QuestionGradingResult.Status status,
            ProcessResult compilation, ProcessResult execution, String message) {
        return new QuestionGradingResult(questionId, status, 0, 0, 0, compilation, execution, message);
    }

    private static String questionId(String testerFilename) {
        if (!TESTER_FILENAME.matcher(testerFilename).matches()) {
            throw new IllegalArgumentException("Expected a default-package <Question>Tester.java file.");
        }
        return testerFilename.substring(0, testerFilename.length() - "Tester.java".length());
    }

    private static String jdkTool(String name) throws IOException {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path tool = Path.of(System.getProperty("java.home"), "bin", name + (windows ? ".exe" : ""));
        if (!Files.isExecutable(tool)) {
            throw new IOException("A full JDK 21 or newer is required; missing tool: " + tool);
        }
        return tool.toString();
    }

    private static String classpath(Path classes, List<Path> support) {
        return String.join(File.pathSeparator, Stream.concat(Stream.of(classes), support.stream())
                .map(Path::toString).toList());
    }

    private static Duration requirePositive(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive.");
        }
        timeout.toNanos(); // reject values that cannot be used by the monotonic deadline calculation.
        return timeout;
    }
}
