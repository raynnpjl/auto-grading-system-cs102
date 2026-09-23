package grading.execution;

import grading.assessment.AssessmentDefinition;
import grading.assessment.QuestionFiles;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

// Compiles and grades default-package assessment sources
public final class JavaGradingService {

    private final Duration compilationTimeout;
    private final Duration executionTimeout;
    private final Consumer<String> log;
    private final BoundedProcessRunner runner = new BoundedProcessRunner();

    public JavaGradingService() {
        this(System.out::println);
    }

    public JavaGradingService(Consumer<String> log) {
        this(Duration.ofSeconds(30), Duration.ofSeconds(5), log);
    }

    public JavaGradingService(Duration compilationTimeout, Duration executionTimeout) {
        this(compilationTimeout, executionTimeout, System.out::println);
    }

    public JavaGradingService(Duration compilationTimeout, Duration executionTimeout,
            Consumer<String> log) {
        this.compilationTimeout = requirePositive(compilationTimeout);
        this.executionTimeout = requirePositive(executionTimeout);
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Compiles a tester together with one student's question sources. The caller owns the returned
     * workspace and must close it, even if compilation fails. Source files are never modified.
     */
    public CompilationResult compile(QuestionFiles files, Path testerFile)
            throws IOException, InterruptedException {
        String testerFilename = testerFile.getFileName().toString();
        var question = AssessmentDefinition.forTester(testerFilename);
        String questionId = question.id();
        requirePrepared(files, question, testerFile);
        Path workspace = Files.createTempDirectory("cs102-grading-");
        try {
            Path sources = Files.createDirectory(workspace.resolve("sources"));
            Path classes = Files.createDirectory(workspace.resolve("classes"));
            stageDependencies(questionId, files.supportClasses(), classes, "support class");
            // Testers open relative filenames from their JVM's working directory.
            stageDependencies(questionId, files.runtimeFiles(), workspace, "runtime file");
            Path emptySourcepath = Files.createDirectory(workspace.resolve("empty-sourcepath"));
            List<String> command = new ArrayList<>(List.of(
                    jdkTool("javac"), "-J-Xmx256m", "-encoding", "UTF-8", "--release", "21",
                    "-proc:none", "-implicit:none", "-classpath", classes.toString(),
                    "-sourcepath", emptySourcepath.toString(), "-d", classes.toString()));
            for (Path source : Stream.concat(files.sources().stream(), Stream.of(testerFile)).toList()) {
                String filename = source.getFileName().toString();
                Path copy = sources.resolve(filename);
                // no replacement: duplicate filenames must not overwrite a student or official tester
                Files.copy(source, copy);
                command.add(copy.toString());
            }
            log.accept("[" + questionId + "] Compiling " + files.sources().stream()
                    .map(path -> path.getFileName().toString()).toList() + " with " + testerFilename);
            ProcessResult process = runner.run(command, workspace, compilationTimeout);
            logProcess(questionId, "Compilation", process);
            return new CompilationResult(questionId, testerFilename.replaceFirst("\\.java$", ""),
                    workspace, process);
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
        log.accept("[" + compilation.questionId() + "] Running " + compilation.testerClass());
        ProcessResult execution = runner.run(List.of(
                jdkTool("java"), "-Xmx256m", "-XX:-CreateCoredumpOnCrash",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Djava.io.tmpdir=" + compilation.workspace(),
                "-cp", compilation.classesDirectory().toString(),
                compilation.testerClass()), compilation.workspace(), executionTimeout);
        logProcess(compilation.questionId(), "Execution", execution);
        List<String> lines = execution.stdout().lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        int passed = (int) lines.stream().filter("Passed"::equals).count();
        int failed = (int) lines.stream().filter(line -> line.equals("Failed") || line.startsWith("Failed ->")).count();

        // AI-assisted fix (ChatGPT): preserve completed unit-mark tests on timeout.
        // The supplied assessment testers award one mark per exact "Passed" line.
        // Keep the timeout status: remaining tests were not completed or awarded marks.
        if (execution.status() == ProcessResult.Status.TIMED_OUT) {
            return new QuestionGradingResult(compilation.questionId(), QuestionGradingResult.Status.EXECUTION_TIMEOUT,
                    passed, passed, failed, compiler, execution,
                    "Execution timed out. Retained " + passed
                            + " mark(s) from completed one-mark tests; unfinished tests earn no marks.");
        }
        if (!execution.succeeded()) {
            QuestionGradingResult.Status status = switch (execution.status()) {
                case TIMED_OUT -> QuestionGradingResult.Status.EXECUTION_TIMEOUT;
                case OUTPUT_LIMIT_EXCEEDED -> QuestionGradingResult.Status.OUTPUT_LIMIT_EXCEEDED;
                case EXITED -> QuestionGradingResult.Status.RUNTIME_ERROR;
            };
            return failure(compilation.questionId(), status, compiler, execution, "Tester did not finish successfully.");
        }

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
        return new QuestionGradingResult(compilation.questionId(), QuestionGradingResult.Status.GRADED,
                score, passed, failed, compiler, execution, "Graded using the tester's final score.");
    }

    // compiles, executes, and removes all temporary files, including after failure
    public QuestionGradingResult gradeQuestion(QuestionFiles files, Path testerFile)
            throws IOException, InterruptedException {
        String questionId = AssessmentDefinition.forTester(testerFile.getFileName().toString()).id();
        try (CompilationResult compilation = compile(files, testerFile)) {
            QuestionGradingResult result = execute(compilation);
            log.accept("[" + questionId + "] " + result.status() + " | Score: " + result.score()
                    + " | Passed: " + result.passedTests() + " | Failed: " + result.failedTests());
            return result;
        } catch (IOException exception) {
            log.accept("[" + questionId + "] Grading stopped: " + exception.getMessage());
            throw exception;
        }
    }

    // Input/preparation failures stop grading instead of awarding misleading zero scores.
    public StudentGradingResult gradeStudent(SubmissionValidationResult submission, TesterValidationResult testers)
            throws IOException, InterruptedException {
        requirePrepared(submission, testers);
        log.accept("\n=== Student: " + submission.username() + " ===");
        List<QuestionGradingResult> questions = new ArrayList<>();
        for (var question : AssessmentDefinition.QUESTIONS) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Grading cancelled.");
            }
            questions.add(gradeQuestion(submission.questionFiles().get(question.id()),
                    testers.testerFiles().get(question.testerFilename())));
        }
        StudentGradingResult result = new StudentGradingResult(submission.username(), questions);
        log.accept("[" + result.username() + "] Question scores: " + result.questionScores()
                + " | Total: " + result.totalScore());
        return result;
    }

    // processes students in input order
    // interruption cancels the batch after terminating active processes.
    public List<StudentGradingResult> gradeBatch(List<SubmissionValidationResult> submissions, TesterValidationResult testers)
            throws IOException, InterruptedException {
        // Check the entire batch before calculating any scores; compile rechecks before copying.
        for (SubmissionValidationResult submission : submissions) {
            requirePrepared(submission, testers);
        }
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

    private void logProcess(String question, String stage, ProcessResult process) {
        log.accept("[" + question + "] " + stage + ": " + process.status() + ", exit " + process.exitCode()
                + ", " + process.duration().toMillis() + " ms");
        if (!process.stdout().isBlank()) {
            log.accept(process.stdout());
        }
        if (!process.stderr().isBlank()) {
            log.accept("[" + question + "] " + stage + " stderr:\n" + process.stderr());
        }
    }

    private void stageDependencies(String question, List<Path> files, Path destination, String role) throws IOException {
        for (Path file : files) {
            Files.copy(file, destination.resolve(file.getFileName()));
            log.accept("[" + question + "] Staged " + role + ": " + file.getFileName());
        }
    }

    private static void requirePrepared(SubmissionValidationResult submission, TesterValidationResult testers)
            throws IOException {
        if (!testers.isValid()) {
            throw new IllegalArgumentException("Import and validate all required tester files before grading.");
        }
        if (!submission.isReadyForGrading()) {
            throw new IllegalArgumentException("Resolve submission validation errors before grading: " + submission.username());
        }
        for (var question : AssessmentDefinition.QUESTIONS) {
            requirePrepared(submission.questionFiles().get(question.id()), question,
                    testers.testerFiles().get(question.testerFilename()));
        }
    }

    private static void requirePrepared(QuestionFiles files, AssessmentDefinition.Question question, Path tester)
            throws IOException {
        if (!files.matches(question)) {
            throw new IOException("Incomplete or unexpected files for " + question.id() + ". Import the submission again.");
        }
        files.requireReadable();
        if (!Files.isRegularFile(tester) || !Files.isReadable(tester)) {
            throw new IOException("Required tester is missing or unreadable: " + tester + ". Import the testers again.");
        }
    }

    private static String jdkTool(String name) throws IOException {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path tool = Path.of(System.getProperty("java.home"), "bin", name + (windows ? ".exe" : ""));
        if (!Files.isExecutable(tool)) {
            throw new IOException("A full JDK 21 or newer is required; missing tool: " + tool);
        }
        return tool.toString();
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
