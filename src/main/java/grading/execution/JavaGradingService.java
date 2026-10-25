package grading.execution;

import grading.assessment.AssessmentDefinition;
import grading.assessment.QuestionFiles;
import grading.config.GradingSettings;
import grading.progress.ProgressTracker;
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

/**
 * Grades students by compiling each question's official tester together with
 * the student's sources
 * and running it in a separate, time-limited JVM. Each question is graded
 * independently, so a
 * missing file, compilation error or crash costs only that question.
 *
 * <p>
 * The steps are delegated: {@link TesterCompiler} compiles,
 * {@link TesterRunner} runs,
 * {@link TimeoutRecovery} re-runs a tester whose test case hung, and
 * {@link BatchGrader} grades
 * several students at the same time.
 */
public final class JavaGradingService {

    private final Duration compilationTimeout;
    private final Duration executionTimeout;
    private final int parallelStudents;
    private final Consumer<String> log;
    private final ProcessRunner runner;
    private final TesterCompiler compiler;
    private final TesterRunner testerRunner;
    private final TimeoutRecovery timeoutRecovery;
    private final ProgressTracker progress;

    public JavaGradingService() {
        this(System.out::println);
    }

    /** Uses the time limits from the grading settings. */
    public JavaGradingService(Consumer<String> log) {
        this(GradingSettings.current().compileTimeout(), GradingSettings.current().executionTimeout(), log);
    }

    public JavaGradingService(Duration compilationTimeout, Duration executionTimeout) {
        this(compilationTimeout, executionTimeout, System.out::println);
    }

    public JavaGradingService(Duration compilationTimeout, Duration executionTimeout,
            Consumer<String> log) {
        this(compilationTimeout, executionTimeout, GradingSettings.current().parallelStudents(), log,
                new BoundedProcessRunner(GradingSettings.current().maxOutputBytes()));
    }

    /** Uses the settings' limits and reports each student's progress to the tracker the page reads. */
    public JavaGradingService(Consumer<String> log, ProgressTracker progress) {
        this(GradingSettings.current().compileTimeout(),
                GradingSettings.current().executionTimeout(),
                GradingSettings.current().parallelStudents(),
                log,
                new BoundedProcessRunner(GradingSettings.current().maxOutputBytes()),
                progress);
    }

    /** For callers that don't show live progress; the tracker is updated but never read. */
    JavaGradingService(Duration compilationTimeout, Duration executionTimeout, int parallelStudents,
            Consumer<String> log, ProcessRunner runner) {
        this(compilationTimeout, executionTimeout, parallelStudents, log, runner, new ProgressTracker());
    }

    JavaGradingService(Duration compilationTimeout, Duration executionTimeout, int parallelStudents,
            Consumer<String> log, ProcessRunner runner, ProgressTracker progress) {
        this.compilationTimeout = requirePositive(compilationTimeout);
        this.executionTimeout = requirePositive(executionTimeout);
        this.parallelStudents = parallelStudents;
        this.log = Objects.requireNonNull(log, "log");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.compiler = new TesterCompiler(this.compilationTimeout, runner, log);
        this.testerRunner = new TesterRunner(this.executionTimeout, runner, log);
        this.timeoutRecovery = new TimeoutRecovery(this::compileAndExecute, this.executionTimeout, log);
    }

    /** The same grader writing its log somewhere else. */
    private JavaGradingService withLog(Consumer<String> otherLog) {
        return new JavaGradingService(compilationTimeout, executionTimeout, parallelStudents, otherLog, runner,
                progress);
    }

    /**
     * Compiles a tester together with one student's question sources. The caller
     * owns the returned
     * workspace and must close it, even if compilation fails. Source files are
     * never modified.
     */
    public CompilationResult compile(QuestionFiles files, Path testerFile)
            throws IOException, InterruptedException {
        return compiler.compile(files, testerFile);
    }

    /**
     * Runs only the tester's main method in a new JVM; does not close the
     * compilation workspace.
     */
    public QuestionGradingResult execute(CompilationResult compilation) throws IOException, InterruptedException {
        return testerRunner.run(compilation);
    }

    /**
     * Compiles, runs and, after a timeout, recovers one question; temporary files
     * are always removed.
     */
    public QuestionGradingResult gradeQuestion(QuestionFiles files, Path testerFile)
            throws IOException, InterruptedException {
        String questionId = AssessmentDefinition.forTester(testerFile.getFileName().toString()).id();
        QuestionGradingResult result = compileAndExecute(files, testerFile);
        if (result.status() == QuestionGradingResult.Status.EXECUTION_TIMEOUT) {
            result = timeoutRecovery.recover(files, testerFile, result);
        }
        log.accept("[" + questionId + "] " + result.status() + " | Score: " + result.score()
                + " | Passed: " + result.passedTests() + " | Failed: " + result.failedTests());
        return result;
    }

    /**
     * Grades every question of one confirmed submission. A missing question scores zero and a grading
     * failure is recorded against its own question, so one problem never stops the rest.
     */
    public StudentGradingResult gradeStudent(SubmissionValidationResult submission, TesterValidationResult testers)
            throws InterruptedException {
        requireValid(testers);
        if (!submission.isReadyForGrading()) {
            throw new IllegalArgumentException("Confirm the identity of " + submission.username()
                    + " before grading this submission.");
        }
        String username = submission.username();
        log.accept("\n=== Student: " + username + " ===");

        progress.studentStarted(username);

        List<QuestionGradingResult> questions = new ArrayList<>();
        for (var question : AssessmentDefinition.QUESTIONS) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Grading cancelled.");
            }
            progress.stepStarted(username, "compiling " + question.id());
            QuestionGradingResult questionResult = gradeSubmittedQuestion(submission, question, testers);
            questions.add(questionResult);
            progress.questionFinished(username, question.id(), questionResult.score());
        }

        StudentGradingResult result = new StudentGradingResult(username, questions);
        progress.studentFinished(username, result.needsReview());
        log.accept("[" + username + "] Question scores: " + result.questionScores()
                + " | Total: " + result.totalScore());
        return result;
    }

    /**
     * Grades every submission whose owner is confirmed, several students at a time, and returns the
     * results in input order; the others are skipped and logged. Interruption cancels the batch after
     * every running process has been terminated.
     */
    public List<StudentGradingResult> gradeBatch(List<SubmissionValidationResult> submissions,
            TesterValidationResult testers) throws IOException, InterruptedException {
        // Testers are shared by every student, so check them before calculating any scores.
        requireValid(testers);
        requireReadable(testers);
        List<SubmissionValidationResult> gradable = new ArrayList<>();
        for (SubmissionValidationResult submission : submissions) {
            if (submission.isReadyForGrading()) {
                gradable.add(submission);
            } else {
                log.accept("\n=== Skipped: " + submission.username()
                        + " (confirm the student's identity, then grade again) ===");
            }
        }
        return new BatchGrader(parallelStudents, log).grade(gradable,
                (submission, studentLog) -> withLog(studentLog).gradeStudent(submission, testers));
    }

    private QuestionGradingResult gradeSubmittedQuestion(SubmissionValidationResult submission,
            AssessmentDefinition.Question question, TesterValidationResult testers) throws InterruptedException {
        QuestionFiles files = submission.files(question);
        List<String> missing = files.missingFiles(question);
        if (!missing.isEmpty()) {
            log.accept("[" + question.id() + "] Not graded: missing " + missing);
            return QuestionGradingResult.zero(question.id(), QuestionGradingResult.Status.MISSING_FILES, null, null,
                    "Missing " + String.join(", ", missing) + "; this question scores 0.");
        }
        try {
            return gradeQuestion(files, testers.testerFiles().get(question.testerFilename()));
        } catch (IOException | RuntimeException exception) {
            log.accept("[" + question.id() + "] Grading stopped: " + exception.getMessage());
            return QuestionGradingResult.zero(question.id(), QuestionGradingResult.Status.GRADING_ERROR, null, null,
                    "Grading stopped: " + exception.getMessage() + " This question scores 0 until it is graded again.");
        }
    }

    private QuestionGradingResult compileAndExecute(QuestionFiles files, Path testerFile)
            throws IOException, InterruptedException {
        try (CompilationResult compilation = compile(files, testerFile)) {
            return execute(compilation);
        }
    }

    private static void requireValid(TesterValidationResult testers) {
        if (!testers.isValid()) {
            throw new IllegalArgumentException("Import and validate all required tester files before grading.");
        }
    }

    private static void requireReadable(TesterValidationResult testers) throws IOException {
        for (Path file : testers.testerFiles().values()) {
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
                throw new IOException("Required tester file is missing or unreadable: " + file
                        + ". Import the testers again.");
            }
        }
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
