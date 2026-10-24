package ui;

import grading.progress.ProgressServer;//xuan
import grading.progress.ProgressTracker;//xuan
import grading.assessment.AssessmentDefinition;
import grading.common.Messages;
import grading.config.GradingSettings;
import grading.execution.JavaGradingService;
import grading.execution.QuestionGradingResult;
import grading.execution.StudentGradingResult;
import grading.reporting.AttemptReportWriter;
import grading.reporting.GradingAttempt;
import grading.reporting.GradingReportFormatter;
import grading.reporting.ReportArchive;
import grading.reporting.ScoreSheet;
import grading.reporting.ScoreSheetPreview;
import grading.reporting.ScoreSheetReportFormatter;
import grading.reporting.ScoreSheetUpdater;
import grading.reporting.ValidationReportFormatter;
import grading.scoring.ScoreBreakdown;
import grading.scoring.ScoreSummary;
import grading.style.StyleChecker;
import grading.submission.IdentitySuggestions;
import grading.submission.StudentIdentity;
import grading.submission.SubmissionCorrectionService;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionPreparationService;
import grading.submission.SubmissionValidationResult;
import grading.submission.SubmissionWorkspaces;
import grading.tester.TesterFileValidationService;
import grading.tester.TesterValidationResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/**
 * Handles the main window's actions: importing testers, submissions and the
 * score sheet, running
 * grading in the background, and showing what was found. The work itself is
 * done by the grading
 * services, and the reports are formatted by the reporting classes.
 */
public final class MainViewController {

    private final GradingSettings settings = GradingSettings.current();
    private final SubmissionPreparationService submissionService = new SubmissionPreparationService(settings);
    private final SubmissionCorrectionService correctionService = new SubmissionCorrectionService(submissionService);
    private final TesterFileValidationService testerService = new TesterFileValidationService(settings);
    private final IdentityDialog identityDialog = new IdentityDialog(IdentitySuggestions.fromSettings());
    private final GradingLog gradingLog = new GradingLog(settings.maxLogCharacters());
    private final SubmissionWorkspaces workspaces = SubmissionWorkspaces.fromSettings();
    private volatile boolean closing;
    private Path reportsFolder = settings.reportsFolder();
    // The latest grading attempt's report folder and id, kept until the next
    // grading run.
    private Path latestReports;
    private String latestAttemptId;
    private String testerReport = "";
    private String submissionReport = "";
    private String gradingReport = "";
    private TesterValidationResult latestTesterResult;
    private Task<GradingRun> gradingTask;
    private boolean inputTaskRunning;
    private List<SubmissionValidationResult> latestSubmissionResults = List.of();
    private Path latestSubmissionWorkspace;
    private Path latestSubmissionSource;
    private boolean identityPromptOffered;
    private boolean identityPromptEnabled = true;
    private boolean testerImported;
    private boolean submissionsImported;
    private long readySubmissionCount;
    private long totalSubmissionCount;
    private final ScoreSheetUpdater scoreSheetUpdater = new ScoreSheetUpdater();
    private ScoreSummary latestScores;
    private ScoreSheet scoreSheet;
    // Why the last score sheet import failed; kept on screen until a sheet is
    // imported.
    private String scoreSheetProblem;
    private String scoreSheetReport = "";
    private final ProgressTracker progress = new ProgressTracker(); // xuan
    private ProgressServer dashboard; // xuan

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
    private Button scoreSheetImportButton;

    @FXML
    private Button writeScoresButton;

    @FXML
    private Button saveReportsButton;

    @FXML
    private void initialize() {
        // Removes normalized copies left behind by sessions that have closed or
        // crashed.
        startWorker(workspaces::removeAbandoned, "workspace-cleanup", true);

        // new
        try {
            dashboard = new ProgressServer(progress, settings.port());
            dashboard.start();
        } catch (IOException exception) {
            consoleStatus.setText("Dashboard unavailable: port " + settings.port() + " is already in use");
        }
    }

    @FXML
    private void importTesterFiles() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Tester-Files folder");
        File selected = chooser.showDialog(testerImportButton.getScene().getWindow());
        if (selected == null) {
            return;
        }

        importTesterFiles(selected.toPath());
    }

    void importTesterFiles(Path source) {
        if (isBusy()) {
            return;
        }
        inputTaskRunning = true;
        latestTesterResult = null;
        clearGradingReport();
        testerFolderField.setText(source.toAbsolutePath().toString());
        updateReadiness();
        statusText.setText("Validating tester files");
        consoleStatus.setText("Tester validation in progress");

        Task<TesterValidationResult> task = new Task<>() {
            @Override
            protected TesterValidationResult call() throws IOException {
                return testerService.validate(source);
            }
        };
        task.setOnSucceeded(event -> {
            inputTaskRunning = false;
            showTesterResult(task.getValue());
        });
        task.setOnFailed(event -> {
            inputTaskRunning = false;
            testerImported = true;
            testerReport = "Tester-file validation failed\n"
                    + "=============================\n"
                    + Messages.describe(task.getException())
                    + "\n  Next step for marker: Confirm that the selected folder is readable and contains "
                    + "the official tester sources, then import it again.\n";
            refreshReports();
            statusText.setText("Validation failed");
            consoleStatus.setText("Tester import not completed");
            updateReadiness();
        });

        startWorker(task, "tester-file-validator", true);
    }

    @FXML
    private void importSubmissions() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select student submissions folder");
        File selected = chooser.showDialog(submissionImportButton.getScene().getWindow());
        if (selected == null) {
            return;
        }

        importSubmissions(selected.toPath());
    }

    void importSubmissions(Path source) {
        if (isBusy()) {
            return;
        }
        inputTaskRunning = true;
        latestSubmissionResults = List.of();
        Path previousWorkspace = latestSubmissionWorkspace;
        latestSubmissionWorkspace = null;
        clearGradingReport();
        submissionFolderField.setText(source.toAbsolutePath().toString());
        latestSubmissionSource = source.toAbsolutePath();
        identityPromptOffered = false;
        readySubmissionCount = 0;
        totalSubmissionCount = 0;
        updateReadiness();
        updateResolveIssuesButton();
        statusText.setText("Validating submissions");
        consoleStatus.setText("Validation in progress");

        Task<ImportedSubmissions> task = new Task<>() {
            @Override
            protected ImportedSubmissions call() throws IOException {
                // The previous import's copies are replaced, so they are no longer needed.
                workspaces.delete(previousWorkspace);
                Path workspace = workspaces.create();
                try {
                    return new ImportedSubmissions(submissionService.prepareBatch(source, workspace), workspace);
                } catch (IOException | RuntimeException failure) {
                    workspaces.delete(workspace);
                    throw failure;
                }
            }
        };
        task.setOnSucceeded(event -> {
            inputTaskRunning = false;
            showResults(task.getValue().results(), task.getValue().workspace());
        });
        task.setOnFailed(event -> {
            inputTaskRunning = false;
            Throwable failure = task.getException();
            submissionsImported = true;
            submissionReport = "Submission validation failed\n"
                    + "============================\n"
                    + Messages.describe(failure)
                    + "\n  Next step for marker: Confirm that the selected folder is readable, repair or "
                    + "re-extract it if necessary, then import it again.\n\n"
                    + "No source files were changed.\n";
            refreshReports();
            statusText.setText("Validation failed");
            consoleStatus.setText("Import not completed");
            updateReadiness();
        });

        startWorker(task, "submission-validator", true);
    }

    @FXML
    private void runGrading() {
        if (isBusy() || !inputsReady()) {
            return;
        }
        List<SubmissionValidationResult> submissions = List.copyOf(latestSubmissionResults);
        List<String> skipped = submissions.stream()
                .filter(Predicate.not(SubmissionValidationResult::isReadyForGrading))
                .map(SubmissionValidationResult::username)
                .toList();
        int gradable = submissions.size() - skipped.size();
        TesterValidationResult testers = latestTesterResult;
        Path workspace = latestSubmissionWorkspace;
        Path submissionsFolder = latestSubmissionSource;
        Path reportsFolder = this.reportsFolder;
        latestReports = null;
        clearGradingReport();
        gradingTask = new Task<>() {
            @Override
            protected GradingRun call() throws IOException, InterruptedException {
                Consumer<String> log = message -> {
                    System.out.println(message);
                    progress.log(message); 
                };
                LocalDateTime started = LocalDateTime.now();
                // List<StudentGradingResult> results = new
                // JavaGradingService(log).gradeBatch(submissions, testers);
                List<StudentGradingResult> results = new JavaGradingService(log, progress).gradeBatch(submissions,
                        testers); // xuan
                log.accept("Checking style against the CS102 Java Coding Style guide.");
                GradingAttempt attempt = GradingAttempt.assemble(started, testers.source(), submissionsFolder,
                        settings, submissions, results, new StyleChecker(settings.style()));
                log.accept("Writing the reports for this attempt.");
                return new GradingRun(results, attempt, new AttemptReportWriter(reportsFolder).write(attempt));
            }
        };
        Task<GradingRun> task = gradingTask;
        task.setOnSucceeded(event -> {
            progress.finish(); //xuan
            gradingTask = null;
            updateReadiness();
            GradingRun run = task.getValue();
            List<StudentGradingResult> results = run.results();
            long failedParts = results.stream().flatMap(student -> student.questions().stream())
                    .filter(question -> question.status() != QuestionGradingResult.Status.GRADED).count();
            latestReports = run.reports().folder().orElse(null);
            latestAttemptId = run.reports().attemptId();
            latestScores = new ScoreSummary(run.attempt().scores().stream()
                    .map(ScoreBreakdown::toStudentScore)
                    .toList());
            gradingReport = GradingReportFormatter.format(results, skipped) + reportLocation(run.reports())
                    + "\nProcess log\n===========\n" + gradingLog.text() + "\n";
            scoreSheetReport = scoreSheetStatus();
            updateWriteScoresButton();
            refreshReports();
            boolean clean = failedParts == 0 && skipped.isEmpty();
            statusText.setText(clean ? "Grading complete" : "Grading complete — review errors");
            consoleStatus.setText("Students graded: " + results.size() + " | Skipped: " + skipped.size()
                    + " | Parts with errors: " + failedParts);
            System.out.println("Grading complete: " + results.size() + " students, " + skipped.size()
                    + " skipped, " + failedParts + " parts with errors.");
        });
        task.setOnFailed(event -> {
            progress.finish(); //xuan
            gradingTask = null;
            updateReadiness();
            String message = "Grading failed: " + Messages.describe(task.getException());
            gradingReport = message + "\n\n" + gradingLog.text() + "\n";
            refreshReports();
            statusText.setText("Grading failed");
            consoleStatus.setText("Review the process log, then retry");
            System.err.println(message);
        });
        task.setOnCancelled(event -> {
            progress.finish(); //xuan
            gradingTask = null;
            updateReadiness();
            System.out.println("Grading cancelled.");
        });
        updateReadiness();
        statusText.setText("Grading in progress");
        consoleStatus.setText("Grading " + gradable + " students");
        String start = "Starting grading for " + gradable + " students"
                + (skipped.isEmpty() ? "." : "; skipping " + skipped.size() + " with unconfirmed identity.");
        appendGradingLog(start);
        System.out.println(start);
        // Keep the worker alive on application exit until interrupted process cleanup
        // has finished,
        // then remove the normalized copies it was grading.
        startWorker(() -> {
            try {
                task.run();
            } finally {
                if (closing) {
                    workspaces.delete(workspace);
                }
            }
        }, "grading-runner", false);
    }

    private static String reportLocation(AttemptReportWriter.Written reports) {
        StringBuilder text = new StringBuilder("\nReports\n=======\n");
        reports.folder().ifPresentOrElse(
                folder -> text.append("Attempt ").append(reports.attemptId()).append(": ")
                        .append(AttemptReportWriter.summaryOf(folder).toAbsolutePath()).append('\n'),
                () -> text.append("The reports could not be written.\n"));
        reports.problems().forEach(problem -> text.append("  ").append(problem).append('\n'));
        return text.toString();
    }

    /** Writes attempt reports into another folder instead of the configured one. */
    void useReportsFolder(Path folder) {
        reportsFolder = folder;
    }

    /**
     * Stops the identity dialog from opening by itself; the Resolve issues button
     * still opens it.
     */
    void disableAutomaticIdentityPrompt() {
        identityPromptEnabled = false;
    }

    /**
     * Stops grading and removes this session's normalized copies; called when the
     * window closes.
     */
    void shutdown() {

        //xuan
        closing = true;
        if (dashboard != null) {
            dashboard.stop();
            dashboard = null;
        }
        closing = true;
        //xuan

        if (gradingTask != null) {
            // the grading worker removes the copies once its processes have been terminated
            gradingTask.cancel(true);
        } else {
            workspaces.delete(latestSubmissionWorkspace);
        }
    }

    private void clearGradingReport() {
        gradingReport = "";
        gradingLog.clear();
        latestScores = null;
        scoreSheetReport = scoreSheetStatus();
        updateWriteScoresButton();
        refreshReports();
    }

    private void appendGradingLog(String message) {
        gradingLog.append(message);
        findingsConsole.setText(gradingLog.text());
        findingsConsole.positionCaret(findingsConsole.getLength());
    }

    private static void startWorker(Runnable work, String name, boolean daemon) {
        Thread worker = new Thread(work, name);
        worker.setDaemon(daemon);
        worker.start();
    }

    @FXML
    private void importScoreSheet() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select the score sheet CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File selected = chooser.showOpenDialog(scoreSheetImportButton.getScene().getWindow());
        if (selected == null) {
            return;
        }

        importScoreSheet(selected.toPath());
    }

    void importScoreSheet(Path source) {
        try {
            scoreSheet = ScoreSheet.load(source);
            scoreSheetProblem = null;
            scoreSheetReport = scoreSheetStatus();
            consoleStatus.setText("Score sheet imported: " + scoreSheet.rowCount() + " students");
        } catch (IOException exception) {
            scoreSheet = null;
            scoreSheetProblem = ScoreSheetReportFormatter.importFailed(exception);
            scoreSheetReport = scoreSheetStatus();
            consoleStatus.setText("Score sheet not imported");
        }
        updateWriteScoresButton();
        refreshReports();
    }

    @FXML
    private void writeScoresToSheet() {
        writeScoresToSheet(
                preview -> ScoreSheetConfirmation.confirm(preview, writeScoresButton.getScene().getWindow()));
    }

    void writeScoresToSheet(Predicate<ScoreSheetPreview> confirmation) {
        if (isBusy() || latestScores == null || scoreSheet == null) {
            return;
        }
        ScoreSheetPreview preview = scoreSheetUpdater.preview(scoreSheet, latestScores);
        if (preview.entries().isEmpty()) {
            scoreSheetReport = ScoreSheetReportFormatter.noMatchingStudents(preview);
            refreshReports();
            return;
        }
        if (!confirmation.test(preview)) {
            return;
        }
        try {
            ScoreSheetUpdater.Result result = scoreSheetUpdater.write(preview);
            scoreSheet = result.updatedSheet();
            scoreSheetReport = ScoreSheetReportFormatter.written(preview, result);
            statusText.setText("Scores written to score sheet");
            consoleStatus.setText(result.scoresWritten() + " scores written to " + result.sheet().getFileName());
        } catch (IOException exception) {
            scoreSheetReport = ScoreSheetReportFormatter.writeFailed(exception);
            statusText.setText("Score sheet not updated");
            consoleStatus.setText("Score sheet not updated");
        }
        updateWriteScoresButton();
        refreshReports();
    }

    Alert scoreSheetConfirmation(ScoreSheetPreview preview) {
        return ScoreSheetConfirmation.create(preview);
    }

    private String scoreSheetStatus() {
        if (scoreSheet == null && scoreSheetProblem != null) {
            return scoreSheetProblem;
        }
        return ScoreSheetReportFormatter.status(scoreSheet, latestScores != null);
    }

    private void updateWriteScoresButton() {
        writeScoresButton.setDisable(isBusy() || latestScores == null || scoreSheet == null);
        saveReportsButton.setDisable(isBusy() || latestReports == null);
    }

    @FXML
    private void saveReports() {
        if (latestReports == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save the grading reports");
        chooser.setInitialFileName("grading-reports-" + latestAttemptId + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP files", "*.zip"));
        File selected = chooser.showSaveDialog(saveReportsButton.getScene().getWindow());
        if (selected != null) {
            saveReports(selected.toPath());
        }
    }

    /** Saves the latest attempt's reports as a ZIP file, in the background. */
    void saveReports(Path zipFile) {
        if (isBusy() || latestReports == null) {
            return;
        }
        Path reports = latestReports;
        inputTaskRunning = true;
        updateReadiness();
        updateWriteScoresButton();
        consoleStatus.setText("Saving the reports");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException {
                ReportArchive.save(reports, zipFile);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            inputTaskRunning = false;
            updateReadiness();
            updateWriteScoresButton();
            statusText.setText("Reports saved");
            consoleStatus.setText("Reports saved to " + zipFile.getFileName());
            scoreSheetReport = "Reports saved\n=============\n" + zipFile.toAbsolutePath() + "\n\n"
                    + scoreSheetStatus();
            refreshReports();
        });
        task.setOnFailed(event -> {
            inputTaskRunning = false;
            updateReadiness();
            updateWriteScoresButton();
            statusText.setText("Reports not saved");
            consoleStatus.setText("Reports not saved");
            scoreSheetReport = "Reports not saved\n=================\n" + Messages.describe(task.getException())
                    + "\n\n" + scoreSheetStatus();
            refreshReports();
        });
        startWorker(task, "report-archive", true);
    }

    private void showResults(List<SubmissionValidationResult> results, Path workspace) {
        long ready = results.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        long skipped = results.size() - ready;
        submissionsImported = true;
        readySubmissionCount = ready;
        totalSubmissionCount = results.size();
        latestSubmissionResults = List.copyOf(results);

        progress.prepare(results);

        latestSubmissionWorkspace = workspace;

        TreeItem<String> root = new TreeItem<>("Normalized submissions");
        root.setExpanded(true);
        for (SubmissionValidationResult result : results) {
            TreeItem<String> student = new TreeItem<>(result.username() + statusSuffix(result));
            student.setExpanded(true);
            if (result.normalizedRoot() != null) {
                addNormalizedTree(student, result.normalizedRoot());
            }
            root.getChildren().add(student);
        }
        submissionFiles.setRoot(root);
        submissionFiles.setShowRoot(true);
        submissionEmptyState.setVisible(results.isEmpty());
        submissionEmptyState.setManaged(results.isEmpty());

        submissionReport = ValidationReportFormatter.submissions(results, workspace);
        refreshReports();
        statusText.setText(skipped == 0 ? "Submissions ready" : "Review required");
        consoleStatus.setText(String.format(Locale.ROOT, "%d ready, %d need attention", ready, skipped));
        updateReadiness();
        updateResolveIssuesButton();
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
        List<SubmissionValidationResult> affected = latestSubmissionResults.stream()
                .filter(this::needsIdentityInput)
                .filter(result -> result.normalizedRoot() != null)
                .toList();
        if (affected.isEmpty()) {
            updateResolveIssuesButton();
            return;
        }
        identityDialog.show(resolveIssuesButton.getScene().getWindow(), affected)
                .ifPresent(this::applyConfirmedIdentities);
    }

    private void applyConfirmedIdentities(
            Map<SubmissionValidationResult, StudentIdentity> confirmed) {
        if (confirmed.isEmpty() || latestSubmissionWorkspace == null) {
            return;
        }

        inputTaskRunning = true;
        clearGradingReport();
        updateReadiness();
        statusText.setText("Applying verified identities");
        consoleStatus.setText("Revalidating corrected copies");
        Path correctionRoot = latestSubmissionWorkspace.resolve("guided-corrections-" + UUID.randomUUID());
        List<SubmissionValidationResult> currentResults = latestSubmissionResults;

        Task<List<SubmissionValidationResult>> task = new Task<>() {
            @Override
            protected List<SubmissionValidationResult> call() throws IOException {
                List<SubmissionValidationResult> updated = new ArrayList<>();
                int index = 0;
                for (SubmissionValidationResult result : currentResults) {
                    StudentIdentity identity = confirmed.get(result);
                    if (identity == null) {
                        updated.add(result);
                    } else {
                        Path studentWorkspace = correctionRoot.resolve("student-" + index++);
                        updated.add(correctionService.applyIdentity(result, identity, studentWorkspace));
                    }
                }
                return List.copyOf(updated);
            }
        };
        task.setOnSucceeded(event -> {
            inputTaskRunning = false;
            showResults(task.getValue(), latestSubmissionWorkspace);
        });
        task.setOnFailed(event -> {
            inputTaskRunning = false;
            updateReadiness();
            submissionReport += "\nGuided correction could not be completed: "
                    + Messages.describe(task.getException())
                    + "\nNext step for marker: Review the entered identity values and try Resolve issues again.\n";
            refreshReports();
            statusText.setText("Correction failed");
            consoleStatus.setText("Verified details were not applied");
        });

        startWorker(task, "submission-identity-correction", true);
    }

    private void showTesterResult(TesterValidationResult result) {
        testerImported = true;
        latestTesterResult = result;

        TreeItem<String> root = new TreeItem<>(
                result.source().getFileName() + (result.isValid() ? " - valid" : " - needs attention"));
        root.setExpanded(true);
        result.testerFiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> root.getChildren().add(new TreeItem<>(entry.getKey())));
        testerFiles.setRoot(root);
        testerFiles.setShowRoot(true);
        testerEmptyState.setVisible(false);
        testerEmptyState.setManaged(false);

        testerReport = ValidationReportFormatter.testers(result);
        refreshReports();
        statusText.setText(result.isValid() ? "Tester files ready" : "Review required");
        consoleStatus.setText(result.isValid() ? "Tester files valid" : "Tester files need attention");
        updateReadiness();
    }

    private void addNormalizedTree(TreeItem<String> student, Path normalizedRoot) {
        try {
            for (String question : AssessmentDefinition.questionFolders()) {
                Path questionPath = normalizedRoot.resolve(question);
                if (!Files.isDirectory(questionPath)) {
                    continue;
                }
                TreeItem<String> questionItem = new TreeItem<>(question);
                try (var children = Files.list(questionPath)) {
                    children.sorted().forEach(file -> questionItem.getChildren()
                            .add(new TreeItem<>(file.getFileName().toString())));
                }
                student.getChildren().add(questionItem);
            }
        } catch (IOException exception) {
            student.getChildren().add(new TreeItem<>("Could not display normalized files"));
        }
    }

    private String statusSuffix(SubmissionValidationResult result) {
        if (!result.isReadyForGrading()) {
            return " - needs attention";
        }
        if (!result.missingQuestions().isEmpty()) {
            return " - ready, missing " + String.join(", ", result.missingQuestions());
        }
        return result.correctedCount() > 0 ? " - ready (corrected)" : " - ready";
    }

    private void refreshReports() {
        String separator = !testerReport.isBlank() && !submissionReport.isBlank() ? "\n" : "";
        findingsConsole.setText(scoreSheetReport + gradingReport + testerReport + separator + submissionReport);
        findingsConsole.positionCaret(0);
    }

    private void updateReadiness() {
        boolean busy = isBusy();
        runGradingButton.setDisable(busy || !inputsReady());
        testerImportButton.setDisable(busy);
        submissionImportButton.setDisable(busy);
        resolveIssuesButton.setDisable(busy);
        if (gradingTask != null) {
            runReadinessText.setText("Grading in progress. Scores and logs appear below.");
        } else if (inputTaskRunning) {
            runReadinessText.setText("Validating inputs. Please wait.");
        } else if (testerImported && submissionsImported) {
            if (!inputsReady()) {
                runReadinessText.setText("Resolve the reported validation errors before grading.");
            } else if (readySubmissionCount < totalSubmissionCount) {
                runReadinessText.setText(formatSubmissionReadiness(readySubmissionCount, totalSubmissionCount)
                        + ". The others are skipped until their identity is confirmed.");
            } else {
                runReadinessText.setText("Both folders are validated and ready.");
            }
        } else if (testerImported) {
            runReadinessText.setText("Import student submissions to complete input validation.");
        } else if (submissionsImported) {
            runReadinessText.setText("Import tester files to complete input validation.");
        } else {
            runReadinessText.setText("Import both folders to get started.");
        }

        if (totalSubmissionCount > 0) {
            statusText.setText(formatSubmissionReadiness(readySubmissionCount, totalSubmissionCount));
        }
    }

    private boolean isBusy() {
        return inputTaskRunning || gradingTask != null;
    }

    private boolean inputsReady() {
        return latestTesterResult != null && latestTesterResult.isValid()
                && latestSubmissionResults.stream().anyMatch(SubmissionValidationResult::isReadyForGrading);
    }

    static String formatSubmissionReadiness(long ready, long total) {
        return ready + " out of " + total + " submissions ready";
    }

    private void updateResolveIssuesButton() {
        boolean hasIdentityIssues = latestSubmissionResults.stream().anyMatch(this::needsIdentityInput);
        resolveIssuesButton.setVisible(hasIdentityIssues);
        resolveIssuesButton.setManaged(hasIdentityIssues);
    }

    private boolean needsIdentityInput(SubmissionValidationResult result) {
        return result.findings().stream().anyMatch(SubmissionFinding::needsIdentityConfirmation);
    }

    private record ImportedSubmissions(List<SubmissionValidationResult> results, Path workspace) {
    }

    private record GradingRun(List<StudentGradingResult> results, GradingAttempt attempt,
            AttemptReportWriter.Written reports) {
    }
}
