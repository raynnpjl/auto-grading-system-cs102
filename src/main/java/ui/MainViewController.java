package ui;

import grading.common.FileTrees;
import grading.common.Messages;
import grading.config.GradingSettings;
import grading.progress.GradingStatus;
import grading.progress.ProgressSnapshot;
import grading.progress.ProgressTracker;
import grading.progress.StudentProgress;
import grading.reporting.AttemptReportWriter;
import grading.reporting.ReportArchive;
import grading.reporting.ScoreSheetPreview;
import grading.session.GradingSession;
import grading.session.SessionRefusal;
import grading.session.WorkFolders;
import grading.submission.IdentitySuggestions;
import grading.submission.StudentIdentity;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterValidationResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/**
 * Connects the main window to the marker's {@link GradingSession}: it passes on what the marker asks for and
 * shows what the session reports. Grading state lives in the session; the score sheet in
 * {@link ScoreSheetActions}; polling in {@link RunMonitor}; and the wording in {@link ConsoleText},
 * {@link Readiness}, {@link FindingsText} and {@link GradingSummaryText}.
 */
public final class MainViewController {

    private static final Logger LOG = Logger.getLogger(MainViewController.class.getName());
    private static final String GRADED_SHEET_SUFFIX = "-graded.csv";
    // How long closing the window waits for a cancelled run to stop its processes before deleting files.
    private static final long SHUTDOWN_WAIT_MILLIS = 30_000;
    private static final long SHUTDOWN_POLL_MILLIS = 50;

    private GradingSettings settings = GradingSettings.current();
    private final ProgressTracker progress = new ProgressTracker();
    private final IdentityDialog identityDialog =
            new IdentityDialog(new IdentitySuggestions(settings.emailDomain()));
    private final ScoreSheetActions scoreSheet = new ScoreSheetActions();
    private final ConsoleText console = new ConsoleText();
    private final RunMonitor monitor = new RunMonitor(progress::snapshot, () -> this.session.grading(),
            this::showRunProgress, this::finishRun);
    private ResultsTable resultsTable;
    private StudentDetailPane detailPane;
    private SimilarityList similarityList;
    private HostServices hostServices;

    private Path workFolder;
    private GradingSession session;
    private List<SubmissionValidationResult> submissions = List.of();
    private boolean inputTaskRunning;
    private boolean runInProgress;
    private boolean testerImported;
    private boolean submissionsImported;
    private boolean identityPromptOffered;
    private boolean identityPromptEnabled = true;

    @FXML
    private Button helpButton;

    @FXML
    private Button testerImportButton;

    @FXML
    private TextField testerFolderField;

    @FXML
    private TreeView<String> testerFiles;

    @FXML
    private VBox testerEmptyState;

    @FXML
    private Button submissionImportButton;

    @FXML
    private TextField submissionFolderField;

    @FXML
    private TreeView<String> submissionFiles;

    @FXML
    private VBox submissionEmptyState;

    @FXML
    private Label statusText;

    @FXML
    private Label consoleStatus;

    @FXML
    private TextArea findingsConsole;

    @FXML
    private Label runReadinessText;

    @FXML
    private Button resolveIssuesButton;

    @FXML
    private Button runGradingButton;

    @FXML
    private VBox progressBox;

    @FXML
    private ProgressBar gradingProgress;

    @FXML
    private Label progressText;

    @FXML
    private Button cancelButton;

    @FXML
    private Button regradeButton;

    @FXML
    private Button scoreSheetImportButton;

    @FXML
    private Button writeScoresButton;

    @FXML
    private Button saveReportsButton;

    @FXML
    private StackPane resultsHolder;

    @FXML
    private StackPane detailHolder;

    @FXML
    private StackPane similarityHolder;

    @FXML
    private Tab similarityTab;

    @FXML
    private void initialize() {
        resultsTable = new ResultsTable(this::regrade);
        detailPane = new StudentDetailPane(this::openReport);
        similarityList = new SimilarityList(username -> resultsTable.select(username));
        resultsHolder.getChildren().add(resultsTable.view());
        detailHolder.getChildren().add(detailPane.view());
        similarityHolder.getChildren().add(similarityList.view());
        resultsTable.view().getSelectionModel().selectedItemProperty()
                .addListener((property, before, after) -> {
                    showDetail();
                    updateButtons();
                });
        updateSimilarityTab();
        startSession(Path.of(System.getProperty("java.io.tmpdir")));
    }

    private void startSession(Path temporaryRoot) {
        Thread sweep = new Thread(() -> WorkFolders.removeAbandoned(temporaryRoot, Instant.now()),
                "abandoned-workspace-cleanup");
        sweep.setDaemon(true);
        sweep.start();
        try {
            workFolder = WorkFolders.create(temporaryRoot);
            session = new GradingSession(settings, progress, workFolder);
        } catch (IOException exception) {
            findingsConsole.setText("The temporary work folder could not be created: "
                    + Messages.describe(exception) + "\nFree some disk space, then restart the application.");
            testerImportButton.setDisable(true);
            submissionImportButton.setDisable(true);
        }
    }

    @FXML
    private void showHelp() {
        HelpDialog.show(helpButton.getScene().getWindow());
    }

    /** Lets the controller open report files in the system's PDF viewer. */
    void useHostServices(HostServices services) {
        hostServices = services;
    }

    @FXML
    private void importTesterFiles() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Tester-Files folder");
        File selected = chooser.showDialog(testerImportButton.getScene().getWindow());
        if (selected != null) {
            importTesterFiles(selected.toPath());
        }
    }

    void importTesterFiles(Path source) {
        if (isBusy()) {
            return;
        }
        testerFolderField.setText(source.toAbsolutePath().toString());
        statusText.setText("Validating tester files");
        consoleStatus.setText("Tester validation in progress");
        runInBackground(() -> session.importTesters(source), "tester-file-validator", this::showTesterResult,
                failure -> {
                    testerImported = true;
                    console.setTesters("Tester-file validation failed\n"
                            + "=============================\n"
                            + Messages.describe(failure)
                            + "\n  Next step for marker: Confirm that the selected folder is readable and contains "
                            + "the official tester sources, then import it again.\n"
                            + keptTestersNote());
                    showImportFailure("Tester import not completed");
                });
    }

    @FXML
    private void importSubmissions() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select student submissions folder");
        File selected = chooser.showDialog(submissionImportButton.getScene().getWindow());
        if (selected != null) {
            importSubmissions(selected.toPath());
        }
    }

    void importSubmissions(Path source) {
        if (isBusy()) {
            return;
        }
        submissionFolderField.setText(source.toAbsolutePath().toString());
        identityPromptOffered = false;
        statusText.setText("Validating submissions");
        consoleStatus.setText("Validation in progress");
        runInBackground(() -> session.importSubmissions(source, (done, total, name) ->
                Platform.runLater(() -> showImportProgress(done, total, name))), "submission-validator",
                this::showSubmissions, failure -> {
                    setVisible(progressBox, false);
                    submissionsImported = true;
                    console.setSubmissions("Submission validation failed\n"
                            + "============================\n"
                            + Messages.describe(failure)
                            + "\n  Next step for marker: Confirm that the selected folder is readable, repair or "
                            + "re-extract it if necessary, then import it again.\n\n"
                            + "No source files were changed."
                            + (submissions.isEmpty() ? "" : " The previously imported submissions are kept.")
                            + "\n");
                    showImportFailure("Import not completed");
                });
    }

    private void showImportProgress(int done, int total, String name) {
        if (!inputTaskRunning) {
            return;
        }
        setVisible(progressBox, true);
        gradingProgress.setProgress((double) done / total);
        progressText.setText("Preparing " + done + " of " + total + " · " + name);
    }

    private void showImportFailure(String status) {
        refreshConsole();
        statusText.setText("Validation failed");
        consoleStatus.setText(status);
    }

    @FXML
    private void runGrading() {
        long ready = submissions.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        startRun(session::startGrading, "Grading " + ready + " students");
    }

    @FXML
    private void regradeSelected() {
        resultsTable.selected().map(StudentProgress::username).ifPresent(this::regrade);
    }

    private void regrade(String username) {
        startRun(() -> session.regrade(username), "Regrading " + username);
    }

    @FXML
    private void cancelGrading() {
        try {
            session.cancelGrading();
            consoleStatus.setText("Cancelling: stopping the running programs");
        } catch (SessionRefusal refusal) {
            consoleStatus.setText(refusal.getMessage());
        }
    }

    /** Whether a grading run is still going on. */
    boolean isGrading() {
        return runInProgress;
    }

    private void startRun(Runnable start, String description) {
        if (isBusy() || session == null) {
            return;
        }
        try {
            start.run();
        } catch (SessionRefusal refusal) {
            consoleStatus.setText(refusal.getMessage());
            return;
        }
        runInProgress = true;
        console.setGrading("");
        statusText.setText("Grading in progress");
        consoleStatus.setText(description);
        gradingProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressText.setText("Starting");
        setVisible(progressBox, true);
        setVisible(cancelButton, true);
        updateButtons();
        monitor.start();
    }

    private void showRunProgress(ProgressSnapshot snapshot) {
        showSnapshot(snapshot);
        gradingProgress.setProgress(snapshot.total() == 0
                ? ProgressBar.INDETERMINATE_PROGRESS : (double) snapshot.done() / snapshot.total());
        progressText.setText(RunMonitor.progressLine(snapshot));
        findingsConsole.setText(String.join("\n", snapshot.activity()));
        findingsConsole.positionCaret(findingsConsole.getLength());
    }

    private void finishRun(ProgressSnapshot snapshot) {
        showSnapshot(snapshot);
        runInProgress = false;
        setVisible(progressBox, false);
        setVisible(cancelButton, false);
        long needsReview = snapshot.students().stream().filter(student -> !student.status().isClean()).count();
        statusText.setText(switch (session.lastOutcome()) {
            case CANCELLED -> "Grading cancelled";
            case FAILED -> "Grading failed";
            case COMPLETED, NONE -> needsReview == 0 ? "Grading complete" : "Grading complete — review errors";
        });
        consoleStatus.setText(snapshot.students().size() - needsReview + " graded, " + needsReview
                + " need review or were skipped");
        if (resultsTable.selected().isEmpty() && !snapshot.students().isEmpty()) {
            resultsTable.select(snapshot.students().getFirst().username());
        }
        console.setGrading(GradingSummaryText.format(snapshot, session.reportsFolder())
                + "\nProcess log\n===========\n" + String.join("\n", snapshot.activity()) + "\n\n");
        console.setScoreSheet(scoreSheet.status(session.hasResults()));
        refreshConsole();
        updateButtons();
    }

    /** Shows the session's table, similar answers and the selected student's details. */
    private void showSnapshot(ProgressSnapshot snapshot) {
        resultsTable.update(snapshot);
        similarityList.update(snapshot.similarPairs());
        updateSimilarityTab();
        showDetail();
    }

    private void showDetail() {
        detailPane.show(resultsTable.selected(), session != null && session.reportsFolder().isPresent());
    }

    private void updateSimilarityTab() {
        long flagged = similarityList.flaggedCount();
        similarityTab.setText(flagged == 0 ? "Similar answers" : "Similar answers (" + flagged + ")");
    }

    private void openReport(String username) {
        Optional<Path> report = session.reportsFolder()
                .flatMap(folder -> AttemptReportWriter.studentReportOf(folder, username));
        if (report.isEmpty()) {
            consoleStatus.setText("No report was written for " + username);
        } else if (hostServices == null) {
            consoleStatus.setText("Report: " + report.get().toAbsolutePath());
        } else {
            hostServices.showDocument(report.get().toUri().toString());
        }
    }

    /** Writes attempt reports into another folder instead of the configured one; used before importing. */
    void useReportsFolder(Path folder) {
        GradingSettings current = settings;
        settings = new GradingSettings(current.compileTimeout(), current.executionTimeout(),
                current.parallelStudents(), current.maxMemoryMegabytes(), current.maxOutputBytes(),
                current.maxJavaFileBytes(), current.maxZipBytes(), current.maxZipEntries(),
                current.emailDomain(), current.maxTesterFileBytes(), current.javaRelease(), current.style(),
                current.penalties(), current.similarity(), folder);
        session = new GradingSession(settings, progress, workFolder);
    }

    /** The latest attempt's reports folder, when grading has written one. */
    Optional<Path> latestReportsFolder() {
        return session.reportsFolder();
    }

    /** The folder holding this session's normalized copies. */
    Path workFolder() {
        return workFolder;
    }

    /** The similar answers listed now. */
    SimilarityList similarityList() {
        return similarityList;
    }

    /** The results table. */
    ResultsTable resultsTable() {
        return resultsTable;
    }

    /** The selected student's details as shown. */
    String detailText() {
        return detailPane.text();
    }

    /** Stops the identity dialog from opening by itself; the Resolve issues button still opens it. */
    void disableAutomaticIdentityPrompt() {
        identityPromptEnabled = false;
    }

    /** Stops grading and removes this session's normalized copies; called when the window closes. */
    void shutdown() {
        monitor.stop();
        Path folder = workFolder;
        workFolder = null;
        if (folder == null || session == null) {
            return;
        }
        if (!session.grading()) {
            deleteWorkFolder(folder);
            return;
        }
        try {
            session.cancelGrading();
        } catch (SessionRefusal refusal) {
            // The run is writing its reports and finishes by itself.
        }
        GradingSession running = session;
        Thread cleanup = new Thread(() -> deleteAfterRun(running, folder), "workspace-cleanup");
        cleanup.start();
    }

    // Waits off the UI thread until the run's processes have stopped, then removes the copies.
    private static void deleteAfterRun(GradingSession running, Path folder) {
        long deadline = System.currentTimeMillis() + SHUTDOWN_WAIT_MILLIS;
        while (running.grading() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(SHUTDOWN_POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        deleteWorkFolder(folder);
    }

    private static void deleteWorkFolder(Path folder) {
        try {
            FileTrees.delete(folder);
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Could not delete the temporary work folder " + folder, exception);
        }
    }

    @FXML
    private void importScoreSheet() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select the score sheet CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File selected = chooser.showOpenDialog(scoreSheetImportButton.getScene().getWindow());
        if (selected != null) {
            importScoreSheet(selected.toPath());
        }
    }

    void importScoreSheet(Path source) {
        show(scoreSheet.importSheet(source, session != null && session.hasResults()));
        updateButtons();
    }

    @FXML
    private void writeScoresToSheet() {
        saveFilledSheet(
                preview -> ScoreSheetConfirmation.confirm(preview, writeScoresButton.getScene().getWindow()),
                this::chooseFilledSheetTarget);
    }

    /**
     * Saves a copy of the imported sheet with every matched student's total filled in. The imported file
     * itself is never changed.
     *
     * @param confirmation asks the marker to confirm the preview
     * @param target       asks where to save the filled copy; empty when the marker cancels
     */
    void saveFilledSheet(Predicate<ScoreSheetPreview> confirmation, Supplier<Optional<Path>> target) {
        if (isBusy() || !scoreSheet.hasSheet() || !session.hasResults()) {
            return;
        }
        scoreSheet.saveFilled(session.currentScores(), confirmation, target).ifPresent(this::show);
    }

    private void show(ScoreSheetActions.Result result) {
        if (result.consoleText() != null) {
            console.setScoreSheet(result.consoleText());
            refreshConsole();
        }
        if (result.statusText() != null) {
            statusText.setText(result.statusText());
        }
        if (result.consoleStatus() != null) {
            consoleStatus.setText(result.consoleStatus());
        }
    }

    private Optional<Path> chooseFilledSheetTarget() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save the filled score sheet");
        Path original = scoreSheet.sheetPath().toAbsolutePath();
        if (original.getParent() != null) {
            chooser.setInitialDirectory(original.getParent().toFile());
        }
        String name = original.getFileName().toString().replaceFirst("(?i)\\.csv$", "");
        chooser.setInitialFileName(name + GRADED_SHEET_SUFFIX);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File selected = chooser.showSaveDialog(writeScoresButton.getScene().getWindow());
        return Optional.ofNullable(selected).map(File::toPath);
    }

    @FXML
    private void saveReports() {
        if (session.reportsFolder().isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save the grading reports");
        chooser.setInitialFileName("grading-reports-" + session.latestAttemptId() + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP files", "*.zip"));
        File selected = chooser.showSaveDialog(saveReportsButton.getScene().getWindow());
        if (selected != null) {
            saveReports(selected.toPath());
        }
    }

    /** Saves the latest attempt's reports as a ZIP file, in the background. */
    void saveReports(Path zipFile) {
        Optional<Path> reports = session.reportsFolder();
        if (isBusy() || reports.isEmpty()) {
            return;
        }
        consoleStatus.setText("Saving the reports");
        runInBackground(() -> {
            ReportArchive.save(reports.get(), zipFile);
            return zipFile;
        }, "report-archive", saved -> showReportsSaved("Reports saved", "Reports saved to " + saved.getFileName(),
                saved.toAbsolutePath().toString()),
                failure -> showReportsSaved("Reports not saved", "Reports not saved", Messages.describe(failure)));
    }

    private void showReportsSaved(String status, String shortStatus, String detail) {
        statusText.setText(status);
        consoleStatus.setText(shortStatus);
        console.setScoreSheet(status + "\n" + "=".repeat(status.length()) + "\n" + detail + "\n\n"
                + scoreSheet.status(session.hasResults()));
        refreshConsole();
    }

    private void showSubmissions(List<SubmissionValidationResult> results) {
        setVisible(progressBox, false);
        long ready = results.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        final long skipped = results.size() - ready;
        submissionsImported = true;
        submissions = List.copyOf(results);
        submissionFiles.setRoot(SubmissionTrees.submissions(results));
        submissionFiles.setShowRoot(true);
        setVisible(submissionEmptyState, results.isEmpty());

        console.setGrading("");
        console.setSubmissions(FindingsText.submissions(results));
        console.setScoreSheet(scoreSheet.status(session.hasResults()));
        showSnapshot(progress.snapshot());
        refreshConsole();
        statusText.setText(skipped == 0 ? Readiness.submissionCount(ready, results.size()) : "Review required");
        consoleStatus.setText(String.format(Locale.ROOT, "%d ready, %d need attention", ready, skipped));
        updateButtons();
        if (identityPromptEnabled && resolveIssuesButton.isVisible() && !identityPromptOffered) {
            identityPromptOffered = true;
            Platform.runLater(this::resolveSubmissionIssues);
        }
    }

    @FXML
    private void resolveSubmissionIssues() {
        if (isBusy()) {
            return;
        }
        List<SubmissionValidationResult> affected = submissions.stream()
                .filter(MainViewController::needsIdentityInput)
                .filter(result -> result.normalizedRoot() != null)
                .toList();
        if (affected.isEmpty()) {
            updateButtons();
            return;
        }
        identityDialog.show(resolveIssuesButton.getScene().getWindow(), affected)
                .ifPresent(this::applyConfirmedIdentities);
    }

    private void applyConfirmedIdentities(Map<SubmissionValidationResult, StudentIdentity> confirmed) {
        if (confirmed.isEmpty()) {
            return;
        }
        statusText.setText("Applying verified identities");
        consoleStatus.setText("Revalidating corrected copies");
        runInBackground(() -> {
            for (Map.Entry<SubmissionValidationResult, StudentIdentity> entry : confirmed.entrySet()) {
                session.confirmIdentity(entry.getKey().username(), entry.getValue());
            }
            return session.submissions();
        }, "submission-identity-correction", this::showSubmissions, failure -> {
            console.appendSubmissions("\nGuided correction could not be completed: " + Messages.describe(failure)
                    + "\nNext step for marker: Review the entered identity values and try Resolve issues again.\n");
            refreshConsole();
            statusText.setText("Correction failed");
            consoleStatus.setText("Verified details were not applied");
        });
    }

    private void showTesterResult(TesterValidationResult result) {
        testerImported = true;
        testerFiles.setRoot(SubmissionTrees.testers(result));
        testerFiles.setShowRoot(true);
        setVisible(testerEmptyState, false);
        console.setTesters(FindingsText.testers(result) + (result.isValid() ? "" : keptTestersNote()));
        refreshConsole();
        statusText.setText(result.isValid() ? "Tester files ready" : "Review required");
        consoleStatus.setText(result.isValid() ? "Tester files valid" : "Tester files need attention");
    }

    private String keptTestersNote() {
        return session.testersReady()
                ? "\nThe tester files imported earlier are still valid and are used for grading.\n" : "";
    }

    private void refreshConsole() {
        findingsConsole.setText(console.text());
        findingsConsole.positionCaret(0);
    }

    private void updateButtons() {
        boolean busy = isBusy();
        final boolean hasResults = session != null && session.hasResults();
        runGradingButton.setDisable(busy || !inputsReady());
        testerImportButton.setDisable(busy || session == null);
        submissionImportButton.setDisable(busy || session == null);
        resolveIssuesButton.setDisable(busy);
        scoreSheetImportButton.setDisable(busy);
        writeScoresButton.setDisable(busy || !hasResults || !scoreSheet.hasSheet());
        saveReportsButton.setDisable(busy || session == null || session.reportsFolder().isEmpty());
        regradeButton.setDisable(busy || !hasResults || resultsTable.selected()
                .map(student -> student.status() == GradingStatus.SKIPPED).orElse(true));
        setVisible(resolveIssuesButton, submissions.stream().anyMatch(MainViewController::needsIdentityInput));
        long ready = submissions.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        runReadinessText.setText(new Readiness(runInProgress, inputTaskRunning, testerImported, submissionsImported,
                inputsReady(), ready, submissions.size(), hasResults).message());
    }

    private boolean isBusy() {
        return inputTaskRunning || runInProgress;
    }

    private boolean inputsReady() {
        return session != null && session.testersReady() && session.submissionsReady();
    }

    static String formatSubmissionReadiness(long ready, long total) {
        return Readiness.submissionCount(ready, total);
    }

    private static boolean needsIdentityInput(SubmissionValidationResult result) {
        return result.findings().stream().anyMatch(SubmissionFinding::needsIdentityConfirmation);
    }

    private static void setVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Runs work off the UI thread, keeping the inputs disabled until it ends. */
    private <T> void runInBackground(Callable<T> work, String name, Consumer<T> succeeded,
            Consumer<Throwable> failed) {
        inputTaskRunning = true;
        updateButtons();
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            inputTaskRunning = false;
            succeeded.accept(task.getValue());
            updateButtons();
        });
        task.setOnFailed(event -> {
            inputTaskRunning = false;
            failed.accept(task.getException());
            updateButtons();
        });
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    /** An alert for the score sheet preview; used by tests to check its text. */
    Alert scoreSheetConfirmation(ScoreSheetPreview preview) {
        return ScoreSheetConfirmation.create(preview);
    }
}
