package ui;

import grading.assessment.AssessmentDefinition;
import grading.execution.JavaGradingService;
import grading.execution.QuestionGradingResult;
import grading.execution.StudentGradingResult;
import grading.reporting.GradingReportFormatter;
import grading.reporting.ScoreSheet;
import grading.reporting.ScoreSheetPreview;
import grading.reporting.ScoreSheetUpdater;
import grading.scoring.ScoreSummary;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionCorrectionService;
import grading.submission.SubmissionPreparationService;
import grading.submission.SubmissionValidationResult;
import grading.submission.StudentIdentity;
import grading.tester.TesterFileValidationService;
import grading.tester.TesterValidationResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public final class MainViewController {

    private final SubmissionPreparationService submissionService = new SubmissionPreparationService();
    private final SubmissionCorrectionService correctionService =
            new SubmissionCorrectionService(submissionService);
    private final TesterFileValidationService testerService = new TesterFileValidationService();
    private static final Set<String> IDENTITY_ERROR_CODES = Set.of(
            "MISSING_HEADER_EMAIL",
            "USERNAME_UNKNOWN",
            "HEADER_IDENTITY_MISMATCH",
            "CONFLICTING_HEADER_IDENTITIES");
    private String testerReport = "";
    private String submissionReport = "";
    private String gradingReport = "";
    private final StringBuilder gradingLog = new StringBuilder();
    private boolean gradingLogTruncated;
    private static final int MAX_LOG_CHARACTERS = 100_000;
    private static final ButtonType SCORE_SHEET_WRITE =
            new ButtonType("Write scores", ButtonBar.ButtonData.OK_DONE);
    private TesterValidationResult latestTesterResult;
    private Task<List<StudentGradingResult>> gradingTask;
    private boolean inputTaskRunning;
    private List<SubmissionValidationResult> latestSubmissionResults = List.of();
    private Path latestSubmissionWorkspace;
    private boolean identityPromptOffered;
    private boolean testerImported;
    private boolean submissionsImported;
    private long readySubmissionCount;
    private long totalSubmissionCount;
    private final ScoreSheetUpdater scoreSheetUpdater = new ScoreSheetUpdater();
    private ScoreSummary latestScores;
    private ScoreSheet scoreSheet;
    private String scoreSheetReport = "";

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
                    + safeMessage(task.getException())
                    + "\n  Next step for marker: Confirm that the selected folder is readable and contains "
                    + "the official tester sources, then import it again.\n";
            refreshReports();
            statusText.setText("Validation failed");
            consoleStatus.setText("Tester import not completed");
            updateReadiness();
        });

        Thread worker = new Thread(task, "tester-file-validator");
        worker.setDaemon(true);
        worker.start();
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
        latestSubmissionWorkspace = null;
        clearGradingReport();
        Path workspace = Path.of(
                System.getProperty("java.io.tmpdir"),
                "cs102-auto-grading",
                "normalized-" + UUID.randomUUID());
        submissionFolderField.setText(source.toAbsolutePath().toString());
        identityPromptOffered = false;
        readySubmissionCount = 0;
        totalSubmissionCount = 0;
        updateReadiness();
        updateResolveIssuesButton();
        statusText.setText("Validating submissions");
        consoleStatus.setText("Validation in progress");

        Task<List<SubmissionValidationResult>> task = new Task<>() {
            @Override
            protected List<SubmissionValidationResult> call() throws IOException {
                return submissionService.prepareBatch(source, workspace);
            }
        };
        task.setOnSucceeded(event -> {
            inputTaskRunning = false;
            List<SubmissionValidationResult> results = task.getValue();
            showResults(results, workspace);
        });
        task.setOnFailed(event -> {
            inputTaskRunning = false;
            Throwable failure = task.getException();
            submissionsImported = true;
            submissionReport = "Submission validation failed\n"
                    + "============================\n"
                    + safeMessage(failure)
                    + "\n  Next step for marker: Confirm that the selected folder is readable, repair or "
                    + "re-extract it if necessary, then import it again.\n\n"
                    + "No source files were changed.\n";
            refreshReports();
            statusText.setText("Validation failed");
            consoleStatus.setText("Import not completed");
            updateReadiness();
        });

        Thread worker = new Thread(task, "submission-validator");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void runGrading() {
        if (isBusy() || !inputsReady()) {
            return;
        }
        List<SubmissionValidationResult> submissions = List.copyOf(latestSubmissionResults);
        TesterValidationResult testers = latestTesterResult;
        clearGradingReport();
        gradingTask = new Task<>() {
            @Override
            protected List<StudentGradingResult> call() throws IOException, InterruptedException {
                JavaGradingService grader = new JavaGradingService(message -> {
                    System.out.println(message);
                    Platform.runLater(() -> {
                        if (gradingTask == this) {
                            appendGradingLog(message);
                        }
                    });
                });
                return grader.gradeBatch(submissions, testers);
            }
        };
        Task<List<StudentGradingResult>> task = gradingTask;
        task.setOnSucceeded(event -> {
            gradingTask = null;
            updateReadiness();
            List<StudentGradingResult> results = task.getValue();
            long failedParts = results.stream().flatMap(student -> student.questions().stream())
                    .filter(question -> question.status() != QuestionGradingResult.Status.GRADED).count();
            latestScores = ScoreSummary.from(results);
            gradingReport = GradingReportFormatter.format(results)
                    + "\nProcess log\n===========\n" + displayedGradingLog() + "\n";
            scoreSheetReport = scoreSheetStatus();
            updateWriteScoresButton();
            refreshReports();
            statusText.setText(failedParts == 0 ? "Grading complete" : "Grading complete — review errors");
            consoleStatus.setText("Students graded: " + results.size() + " | Parts with errors: " + failedParts);
            System.out.println("Grading complete: " + results.size() + " students, " + failedParts + " parts with errors.");
        });
        task.setOnFailed(event -> {
            gradingTask = null;
            updateReadiness();
            String message = "Grading failed: " + safeMessage(task.getException());
            gradingReport = message + "\n\n" + displayedGradingLog() + "\n";
            refreshReports();
            statusText.setText("Grading failed");
            consoleStatus.setText("Review the process log, then retry");
            System.err.println(message);
        });
        task.setOnCancelled(event -> {
            gradingTask = null;
            updateReadiness();
            System.out.println("Grading cancelled.");
        });
        updateReadiness();
        statusText.setText("Grading in progress");
        consoleStatus.setText("Grading " + submissions.size() + " students");
        appendGradingLog("Starting grading for " + submissions.size() + " students.");
        System.out.println("Starting grading for " + submissions.size() + " students.");
        // Keep the worker alive on application exit until interrupted process cleanup has finished.
        Thread worker = new Thread(task, "grading-runner");
        worker.setDaemon(false);
        worker.start();
    }

    void shutdown() {
        if (gradingTask != null) {
            gradingTask.cancel(true);
        }
    }

    private void clearGradingReport() {
        gradingReport = "";
        gradingLog.setLength(0);
        gradingLogTruncated = false;
        latestScores = null;
        scoreSheetReport = scoreSheetStatus();
        updateWriteScoresButton();
        refreshReports();
    }

    private void appendGradingLog(String message) {
        gradingLog.append(message).append('\n');
        if (gradingLog.length() > MAX_LOG_CHARACTERS) {
            gradingLog.delete(0, gradingLog.length() - MAX_LOG_CHARACTERS);
            gradingLogTruncated = true;
        }
        findingsConsole.setText(displayedGradingLog());
        findingsConsole.positionCaret(findingsConsole.getLength());
    }

    private String displayedGradingLog() {
        return (gradingLogTruncated ? "[Earlier process output omitted from this view; see terminal.]\n" : "")
                + gradingLog;
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
            scoreSheetReport = scoreSheetStatus();
            consoleStatus.setText("Score sheet imported: " + scoreSheet.rowCount() + " students");
        } catch (IOException exception) {
            scoreSheet = null;
            scoreSheetReport = "Score sheet not imported\n"
                    + "========================\n"
                    + safeMessage(exception) + "\n\n";
            consoleStatus.setText("Score sheet not imported");
        }
        updateWriteScoresButton();
        refreshReports();
    }

    @FXML
    private void writeScoresToSheet() {
        writeScoresToSheet(this::confirmScoreSheetWrite);
    }

    void writeScoresToSheet(Predicate<ScoreSheetPreview> confirmation) {
        if (isBusy() || latestScores == null || scoreSheet == null) {
            return;
        }
        ScoreSheetPreview preview = scoreSheetUpdater.preview(scoreSheet, latestScores);
        if (preview.entries().isEmpty()) {
            scoreSheetReport = "Scores not written\n==================\n"
                    + "No graded student matches a username in the imported score sheet. "
                    + "Check that the correct sheet was imported.\n\n" + preview.describe() + "\n";
            refreshReports();
            return;
        }
        if (!confirmation.test(preview)) {
            return;
        }
        try {
            ScoreSheetUpdater.Result result = scoreSheetUpdater.write(preview);
            scoreSheet = result.updatedSheet();
            scoreSheetReport = "Scores written to score sheet\n"
                    + "=============================\n"
                    + preview.describe()
                    + "Backup of the previous file: " + result.backup() + "\n\n";
            statusText.setText("Scores written to score sheet");
            consoleStatus.setText(result.scoresWritten() + " scores written to " + result.sheet().getFileName());
        } catch (IOException exception) {
            scoreSheetReport = "Scores not written\n==================\n" + safeMessage(exception) + "\n\n";
            statusText.setText("Score sheet not updated");
            consoleStatus.setText("Score sheet not updated");
        }
        updateWriteScoresButton();
        refreshReports();
    }

    private boolean confirmScoreSheetWrite(ScoreSheetPreview preview) {
        Alert alert = scoreSheetConfirmation(preview);
        alert.initOwner(writeScoresButton.getScene().getWindow());
        return alert.showAndWait().filter(SCORE_SHEET_WRITE::equals).isPresent();
    }

    Alert scoreSheetConfirmation(ScoreSheetPreview preview) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", SCORE_SHEET_WRITE, ButtonType.CANCEL);
        alert.setTitle("Write scores to score sheet");
        alert.setHeaderText("Write " + preview.entries().size() + " scores into "
                + preview.sheet().path().getFileName() + "?\nA backup of the current file is saved first.");
        TextArea details = new TextArea(preview.describe());
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(12);
        DialogPane pane = alert.getDialogPane();
        pane.setContent(details);
        // dialogs open in their own window, so they need the application's stylesheet explicitly
        pane.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        pane.lookupButton(SCORE_SHEET_WRITE).getStyleClass().add("dialog-primary-button");
        pane.lookupButton(ButtonType.CANCEL).getStyleClass().add("dialog-secondary-button");
        return alert;
    }

    private String scoreSheetStatus() {
        if (scoreSheet == null) {
            return "";
        }
        String nextStep = latestScores == null
                ? "Run grading, then select Write scores to fill it."
                : "Select Write scores to fill it with this run's totals.";
        return "Score sheet: " + scoreSheet.path() + " (" + scoreSheet.rowCount() + " students). "
                + nextStep + "\n\n";
    }

    private void updateWriteScoresButton() {
        writeScoresButton.setDisable(isBusy() || latestScores == null || scoreSheet == null);
    }

    private void showResults(List<SubmissionValidationResult> results, Path workspace) {
        long ready = results.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        long errors = results.stream().filter(SubmissionValidationResult::hasErrors).count();
        long corrections = results.stream().mapToLong(SubmissionValidationResult::correctedCount).sum();
        submissionsImported = true;
        readySubmissionCount = ready;
        totalSubmissionCount = results.size();
        latestSubmissionResults = List.copyOf(results);
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

        StringBuilder report = new StringBuilder();
        report.append("Submission validation complete\n")
                .append("==============================\n")
                .append("Processed: ").append(results.size()).append('\n')
                .append("Ready: ").append(ready).append('\n')
                .append("Needs attention: ").append(errors).append('\n')
                .append("Automatic corrections: ").append(corrections).append('\n')
                .append("Normalized workspace: ").append(workspace.toAbsolutePath()).append("\n\n");
        if (errors > 0) {
            report.append("MARKER ACTION REQUIRED\n")
                    .append("Follow the Next step shown under each ERROR, then import the corrected copy again.\n\n");
        }

        for (SubmissionValidationResult result : results) {
            report.append('[').append(result.username()).append("] ")
                    .append(result.isReadyForGrading() ? "READY" : "NEEDS ATTENTION")
                    .append("\nSource: ").append(result.source()).append('\n');
            if (result.findings().isEmpty()) {
                report.append("  OK - structure, required files, and headers are valid.\n");
            } else {
                for (SubmissionFinding finding : result.findings()) {
                    appendFinding(report, finding);
                }
            }
            report.append('\n');
        }

        submissionReport = report.toString();
        refreshReports();
        statusText.setText(errors == 0 ? "Submissions ready" : "Review required");
        consoleStatus.setText(String.format(
                Locale.ROOT, "%d ready, %d need attention", ready, errors));
        updateReadiness();
        updateResolveIssuesButton();
        if (resolveIssuesButton.isVisible() && !identityPromptOffered) {
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

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(resolveIssuesButton.getScene().getWindow());
        dialog.setTitle("Resolve student identity issues");
        dialog.setHeaderText("Complete all verified student details once; the application will update every "
                + "normalized Java header and validate the submissions again.");

        ButtonType applyButton = new ButtonType("Apply and revalidate", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(940);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(4));
        grid.addRow(0,
                new Label("Submission"),
                new Label("Verified username"),
                new Label("Student name"),
                new Label("Student email"));

        Map<SubmissionValidationResult, IdentityFields> fields = new LinkedHashMap<>();
        int row = 1;
        for (SubmissionValidationResult result : affected) {
            TextField username = new TextField(suggestedUsername(result));
            username.setPromptText("e.g. jane.tan.2026");
            TextField name = new TextField(suggestedName(result.username()));
            name.setPromptText("Full name");
            TextField email = new TextField(suggestedEmail(result));
            email.setPromptText("username@school.edu");
            grid.addRow(row++, new Label(result.source().getFileName().toString()), username, name, email);
            fields.put(result, new IdentityFields(username, name, email));
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(430, 72 + affected.size() * 48));
        Label validationMessage = new Label();
        validationMessage.setWrapText(true);
        validationMessage.setStyle("-fx-text-fill: #b42318; -fx-font-weight: bold;");
        Label safetyNote = new Label(
                "Original submissions will not be changed. Only normalized working copies are updated.");
        safetyNote.setWrapText(true);
        VBox content = new VBox(10, safetyNote, scrollPane, validationMessage);
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);

        Map<SubmissionValidationResult, StudentIdentity> confirmed = new LinkedHashMap<>();
        Button apply = (Button) dialog.getDialogPane().lookupButton(applyButton);
        apply.addEventFilter(ActionEvent.ACTION, event -> {
            confirmed.clear();
            try {
                for (Map.Entry<SubmissionValidationResult, IdentityFields> entry : fields.entrySet()) {
                    IdentityFields values = entry.getValue();
                    confirmed.put(entry.getKey(), new StudentIdentity(
                            values.username().getText(),
                            values.name().getText(),
                            values.email().getText()));
                }
                validationMessage.setText("");
            } catch (IllegalArgumentException exception) {
                validationMessage.setText(exception.getMessage());
                event.consume();
            }
        });

        Optional<ButtonType> selected = dialog.showAndWait();
        if (selected.filter(applyButton::equals).isPresent()) {
            applyConfirmedIdentities(confirmed);
        }
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
                    + safeMessage(task.getException())
                    + "\nNext step for marker: Review the entered identity values and try Resolve issues again.\n";
            refreshReports();
            statusText.setText("Correction failed");
            consoleStatus.setText("Verified details were not applied");
        });

        Thread worker = new Thread(task, "submission-identity-correction");
        worker.setDaemon(true);
        worker.start();
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

        StringBuilder report = new StringBuilder();
        report.append("Tester-file validation complete\n")
                .append("===============================\n")
                .append("Folder: ").append(result.source().toAbsolutePath()).append('\n')
                .append("Required files found: ").append(result.testerFiles().size())
                .append('/').append(AssessmentDefinition.testerFiles().size()).append("\n\n");
        if (result.hasErrors()) {
            report.append("MARKER ACTION REQUIRED\n")
                    .append("Follow the Next step shown under each ERROR, then import Tester-Files again.\n\n");
        }
        appendFindings(report, result.findings());
        testerReport = report.toString();
        refreshReports();
        statusText.setText(result.isValid() ? "Tester files ready" : "Review required");
        consoleStatus.setText(result.isValid() ? "Tester files valid" : "Tester files need attention");
        updateReadiness();
    }

    private void addNormalizedTree(TreeItem<String> student, Path normalizedRoot) {
        try {
            for (String question : List.of("Q1", "Q2", "Q3")) {
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
        if (result.isReadyForGrading()) {
            return result.correctedCount() > 0 ? " - ready (corrected)" : " - ready";
        }
        return " - needs attention";
    }

    private void appendFindings(StringBuilder report, List<SubmissionFinding> findings) {
        for (SubmissionFinding finding : findings) {
            appendFinding(report, finding);
        }
    }

    private void appendFinding(StringBuilder report, SubmissionFinding finding) {
        report.append("  ")
                .append(finding.severity())
                .append(finding.corrected() ? " (corrected)" : "")
                .append(" - ")
                .append(finding.message())
                .append('\n');
        finding.nextStep().ifPresent(nextStep -> report
                .append("    Next step for marker: ")
                .append(nextStep)
                .append('\n'));
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
            if (inputsReady()) {
                runReadinessText.setText("Both folders are validated and ready.");
            } else {
                runReadinessText.setText("Resolve the reported validation errors before grading.");
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
                && !latestSubmissionResults.isEmpty()
                && latestSubmissionResults.stream().allMatch(SubmissionValidationResult::isReadyForGrading);
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
        return result.findings().stream()
                .anyMatch(finding -> finding.severity() == grading.submission.FindingSeverity.ERROR
                        && IDENTITY_ERROR_CODES.contains(finding.code()));
    }

    private String suggestedUsername(SubmissionValidationResult result) {
        return result.username().startsWith("unknown-") ? "" : result.username();
    }

    private String suggestedName(String username) {
        if (username == null || username.startsWith("unknown-")) {
            return "";
        }
        String cleaned = username.replaceFirst("[._-](?:19|20)\\d{2}$", "");
        List<String> words = new ArrayList<>();
        for (String part : cleaned.split("[._-]+")) {
            if (!part.isBlank()) {
                words.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
            }
        }
        return String.join(" ", words);
    }

    private String suggestedEmail(SubmissionValidationResult result) {
        String username = suggestedUsername(result);
        return username.isBlank() ? "" : username.toLowerCase(Locale.ROOT) + "@smu.edu.sg";
    }

    private String safeMessage(Throwable failure) {
        if (failure == null) {
            return "Unknown validation error";
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record IdentityFields(TextField username, TextField name, TextField email) {
    }
}
