package grading.session;

import grading.common.Messages;
import grading.config.GradingSettings;
import grading.execution.JavaGradingService;
import grading.execution.QuestionGradingResult;
import grading.execution.StudentGradingResult;
import grading.progress.ProgressTracker;
import grading.reporting.AttemptReportWriter;
import grading.reporting.GradingAttempt;
import grading.scoring.ScoreBreakdown;
import grading.scoring.ScoreSummary;
import grading.style.StyleChecker;
import grading.submission.StudentIdentity;
import grading.submission.SubmissionCorrectionService;
import grading.submission.SubmissionPreparationService;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterFileValidationService;
import grading.tester.TesterValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One marker's grading session: the tester files and submissions that are loaded, the latest results,
 * and grading itself, which runs on its own thread. It knows nothing about HTTP; the web application
 * calls it and shows what it reports through the ProgressTracker.
 */
public final class GradingSession {

    public static final String GRADING_RUNNING = "Wait until grading has finished.";
    public static final String NO_SUBMISSIONS = "No student submissions were found. Choose the folder "
            + "that holds the student ZIPs or folders, or one student's folder.";
    private static final String NO_TESTERS = "Upload valid tester files first.";
    private static final String ALREADY_RUNNING = "Grading is already running.";
    private static final String UNKNOWN_STUDENT = "Unknown student.";

    private final GradingSettings settings;
    private final ProgressTracker progress;
    private final TesterFileValidationService testerService;
    private final SubmissionPreparationService submissionService;
    private final SubmissionCorrectionService correctionService;
    private final AtomicBoolean grading = new AtomicBoolean(false);
    private final Path workFolder;

    private volatile TesterValidationResult testers;
    private volatile List<SubmissionValidationResult> submissions = List.of();
    private volatile Path submissionWorkspace;
    // Every student's latest grading result, kept so one student can be regraded without the others.
    private volatile List<StudentGradingResult> latestResults = List.of();
    // The latest finished attempt. Cleared whenever the submissions change, so links never show stale results.
    private volatile GradingAttempt latestAttempt;
    private volatile String latestAttemptId;
    // The folder AttemptReportWriter wrote for the latest attempt; the ZIP and PDF links read from it.
    private volatile Path latestAttemptFolder;

    public GradingSession(GradingSettings settings, ProgressTracker progress, Path workFolder) {
        this.settings = settings;
        this.progress = progress;
        this.workFolder = workFolder;
        this.testerService = new TesterFileValidationService(settings);
        this.submissionService = new SubmissionPreparationService(settings);
        this.correctionService = new SubmissionCorrectionService(submissionService);
    }

    /** Validates an uploaded tester folder and keeps it. Refused (BUSY) while grading runs. */
    public TesterValidationResult importTesters(Path folder) throws IOException {
        refuseWhileGrading();
        TesterValidationResult result = testerService.validate(folder);
        testers = result;
        return result;
    }

    /**
     * Prepares an uploaded batch and makes it the current one; returns the prepared submissions.
     * Refused (INVALID, NO_SUBMISSIONS) when no submission was found, and (BUSY) while grading runs.
     */
    public List<SubmissionValidationResult> importSubmissions(Path input) throws IOException {
        refuseWhileGrading();
        Path workspace = Files.createDirectories(workFolder.resolve("normalized-" + UUID.randomUUID()));
        List<SubmissionValidationResult> prepared = submissionService.prepareBatch(input, workspace);
        // Keep the previous import when nothing usable was uploaded.
        if (prepared.isEmpty()) {
            throw new SessionRefusal(SessionRefusal.Reason.INVALID, NO_SUBMISSIONS);
        }
        submissions = List.copyOf(prepared);
        submissionWorkspace = workspace;
        latestResults = List.of();
        clearAttempt();
        progress.prepare(prepared);
        return prepared;
    }

    /** Confirms who a skipped submission belongs to; everyone else keeps their results. */
    public synchronized void confirmIdentity(String target, StudentIdentity identity) throws IOException {
        refuseWhileGrading();
        Optional<SubmissionValidationResult> current = submissions.stream()
                .filter(submission -> submission.username().equals(target))
                .findFirst();
        if (current.isEmpty()) {
            throw new SessionRefusal(SessionRefusal.Reason.NOT_FOUND, UNKNOWN_STUDENT);
        }
        replaceSubmission(current.get(), identity);
    }

    /** Re-validates one submission with the identity the marker confirmed and swaps it into the batch. */
    private void replaceSubmission(SubmissionValidationResult current, StudentIdentity identity) throws IOException {
        Path correctionFolder = Files.createDirectories(
                submissionWorkspace.resolve("guided-" + UUID.randomUUID()));
        SubmissionValidationResult updated = correctionService.applyIdentity(current, identity, correctionFolder);

        List<SubmissionValidationResult> list = new ArrayList<>(submissions);
        list.replaceAll(submission -> submission == current ? updated : submission);
        submissions = List.copyOf(list);
        progress.prepare(submissions);
        // Everyone else keeps their marks; the confirmed student is queued, ready to be regraded.
        for (StudentGradingResult result : latestResults) {
            Map<String, Double> scores = new LinkedHashMap<>();
            result.questions().forEach(question -> scores.put(question.questionId(), question.score()));
            progress.showResult(result.username(), scores, needsReview(result));
        }
        GradingAttempt attempt = latestAttempt;
        if (attempt != null) {
            attempt.students().forEach(student -> progress.feedback(student.username(), student.feedback()));
            progress.similarity(attempt.similarity().pairs());
        }
    }

    /** Starts grading every submission on a background thread. */
    public void startGrading() {
        TesterValidationResult testerResult = testers;
        List<SubmissionValidationResult> batch = submissions;
        if (testerResult == null || !testerResult.isValid()) {
            throw new SessionRefusal(SessionRefusal.Reason.INVALID, NO_TESTERS);
        }
        if (batch.stream().noneMatch(SubmissionValidationResult::isReadyForGrading)) {
            throw new SessionRefusal(SessionRefusal.Reason.INVALID, "No submission is ready for grading.");
        }
        if (!grading.compareAndSet(false, true)) {
            throw new SessionRefusal(SessionRefusal.Reason.BUSY, ALREADY_RUNNING);
        }

        clearAttempt();
        progress.prepare(batch);
        new Thread(() -> runGrading(batch, batch, List.of(), testerResult), "grading-runner").start();
    }

    /** Starts grading one student again on a background thread; everyone else keeps their result. */
    public void regrade(String username) {
        TesterValidationResult testerResult = testers;
        List<SubmissionValidationResult> batch = submissions;
        Optional<SubmissionValidationResult> student = batch.stream()
                .filter(submission -> submission.username().equals(username))
                .findFirst();
        if (testerResult == null || !testerResult.isValid()) {
            throw new SessionRefusal(SessionRefusal.Reason.INVALID, NO_TESTERS);
        }
        if (student.isEmpty()) {
            throw new SessionRefusal(SessionRefusal.Reason.NOT_FOUND, UNKNOWN_STUDENT);
        }
        if (!student.get().isReadyForGrading()) {
            throw new SessionRefusal(SessionRefusal.Reason.INVALID,
                    "Confirm this student's identity before grading them.");
        }
        if (!grading.compareAndSet(false, true)) {
            throw new SessionRefusal(SessionRefusal.Reason.BUSY, ALREADY_RUNNING);
        }

        List<StudentGradingResult> others = latestResults.stream()
                .filter(result -> !result.username().equals(username))
                .toList();
        clearAttempt();
        progress.regradeStarted(username);
        new Thread(() -> runGrading(batch, List.of(student.get()), others, testerResult), "grading-runner").start();
    }

    /**
     * Grades {@code toGrade}, adds the results kept from earlier, and writes a new attempt for the whole
     * batch. Runs in its own thread so the page can keep asking for progress.
     */
    private void runGrading(List<SubmissionValidationResult> batch, List<SubmissionValidationResult> toGrade,
            List<StudentGradingResult> kept, TesterValidationResult testerResult) {
        Consumer<String> log = message -> {
            System.out.println(message);
            progress.log(message);
        };
        try {
            LocalDateTime started = LocalDateTime.now();
            List<StudentGradingResult> results = new ArrayList<>(kept);
            results.addAll(new JavaGradingService(log, progress).gradeBatch(toGrade, testerResult));
            latestResults = List.copyOf(results);
            GradingAttempt attempt = GradingAttempt.assemble(started, testerResult.source(), workFolder,
                    settings, batch, results, new StyleChecker(settings.style()));
            attempt.students().forEach(student -> progress.feedback(student.username(), student.feedback()));
            progress.similarity(attempt.similarity().pairs());
            AttemptReportWriter.Written written = new AttemptReportWriter(settings.reportsFolder()).write(attempt);
            log.accept("Grading complete. Reports: " + written.folder().map(Path::toString).orElse("not written"));

            // Set last, so download links appear only once the reports exist.
            latestAttemptId = written.attemptId();
            latestAttemptFolder = written.folder().orElse(null);
            latestAttempt = attempt;
        } catch (Exception | LinkageError exception) {
            // LinkageError: a library the grader needs is missing, which must not fail silently.
            log.accept("Grading failed: " + Messages.describe(exception));
            exception.printStackTrace();
        } finally {
            progress.finish();
            grading.set(false);
        }
    }

    public boolean grading() {
        return grading.get();
    }

    public boolean testersReady() {
        TesterValidationResult result = testers;
        return result != null && result.isValid();
    }

    public boolean submissionsReady() {
        return submissions.stream().anyMatch(SubmissionValidationResult::isReadyForGrading);
    }

    public boolean hasResults() {
        return latestAttempt != null;
    }

    public Optional<Path> reportsFolder() {
        return Optional.ofNullable(latestAttemptFolder);
    }

    public String latestAttemptId() {
        return latestAttemptId;
    }

    /** The totals of the latest attempt, as they are written to a score sheet. */
    public ScoreSummary currentScores() {
        GradingAttempt attempt = latestAttempt;
        if (attempt == null) {
            return new ScoreSummary(List.of());
        }
        return new ScoreSummary(attempt.scores().stream().map(ScoreBreakdown::toStudentScore).toList());
    }

    private void refuseWhileGrading() {
        if (grading.get()) {
            throw new SessionRefusal(SessionRefusal.Reason.BUSY, GRADING_RUNNING);
        }
    }

    // A student needs review when any question could not be graded normally, as in JavaGradingService.
    private static boolean needsReview(StudentGradingResult result) {
        return result.questions().stream()
                .anyMatch(question -> question.status() != QuestionGradingResult.Status.GRADED);
    }

    /** Forgets the latest attempt, so no link points at results from an older run. */
    private void clearAttempt() {
        latestAttempt = null;
        latestAttemptId = null;
        latestAttemptFolder = null;
    }
}
