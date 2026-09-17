package ui;

import grading.submission.SubmissionFinding;
import grading.submission.SubmissionPreparationService;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterFileValidationService;
import grading.tester.TesterValidationResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

public final class MainViewController {

    private final SubmissionPreparationService submissionService = new SubmissionPreparationService();
    private final TesterFileValidationService testerService = new TesterFileValidationService();
    private String testerReport = "";
    private String submissionReport = "";
    private boolean testerImported;
    private boolean testerValid;
    private boolean submissionsImported;
    private boolean submissionsValid;

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
    private void importTesterFiles() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Tester-Files folder");
        File selected = chooser.showDialog(testerImportButton.getScene().getWindow());
        if (selected == null) {
            return;
        }

        Path source = selected.toPath();
        testerFolderField.setText(source.toAbsolutePath().toString());
        testerImportButton.setDisable(true);
        statusText.setText("Validating tester files");
        consoleStatus.setText("Tester validation in progress");

        Task<TesterValidationResult> task = new Task<>() {
            @Override
            protected TesterValidationResult call() throws IOException {
                return testerService.validate(source);
            }
        };
        task.setOnSucceeded(event -> {
            showTesterResult(task.getValue());
            testerImportButton.setDisable(false);
        });
        task.setOnFailed(event -> {
            testerImported = true;
            testerValid = false;
            testerReport = "Tester-file validation failed\n"
                    + "=============================\n"
                    + safeMessage(task.getException())
                    + "\n";
            refreshReports();
            statusText.setText("Validation failed");
            consoleStatus.setText("Tester import not completed");
            updateReadiness();
            testerImportButton.setDisable(false);
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

        Path source = selected.toPath();
        Path workspace = Path.of(
                System.getProperty("java.io.tmpdir"),
                "cs102-auto-grading",
                "normalized-" + UUID.randomUUID());
        submissionFolderField.setText(source.toAbsolutePath().toString());
        submissionImportButton.setDisable(true);
        statusText.setText("Validating submissions");
        consoleStatus.setText("Validation in progress");

        Task<List<SubmissionValidationResult>> task = new Task<>() {
            @Override
            protected List<SubmissionValidationResult> call() throws IOException {
                return submissionService.prepareBatch(source, workspace);
            }
        };
        task.setOnSucceeded(event -> {
            List<SubmissionValidationResult> results = task.getValue();
            showResults(results, workspace);
            submissionImportButton.setDisable(false);
        });
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            submissionsImported = true;
            submissionsValid = false;
            submissionReport = "Submission validation failed\n"
                    + "============================\n"
                    + safeMessage(failure)
                    + "\n\nNo source files were changed. Select another folder and try again.\n";
            refreshReports();
            statusText.setText("Validation failed");
            consoleStatus.setText("Import not completed");
            updateReadiness();
            submissionImportButton.setDisable(false);
        });

        Thread worker = new Thread(task, "submission-validator");
        worker.setDaemon(true);
        worker.start();
    }

    private void showResults(List<SubmissionValidationResult> results, Path workspace) {
        long ready = results.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        long errors = results.stream().filter(SubmissionValidationResult::hasErrors).count();
        long corrections = results.stream().mapToLong(SubmissionValidationResult::correctedCount).sum();
        submissionsImported = true;
        submissionsValid = !results.isEmpty() && ready == results.size();

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

        for (SubmissionValidationResult result : results) {
            report.append('[').append(result.username()).append("] ")
                    .append(result.isReadyForGrading() ? "READY" : "NEEDS ATTENTION")
                    .append("\nSource: ").append(result.source()).append('\n');
            if (result.findings().isEmpty()) {
                report.append("  OK - structure, required files, and headers are valid.\n");
            } else {
                for (SubmissionFinding finding : result.findings()) {
                    report.append("  ")
                            .append(finding.severity())
                            .append(finding.corrected() ? " (corrected)" : "")
                            .append(" - ")
                            .append(finding.message())
                            .append('\n');
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
    }

    private void showTesterResult(TesterValidationResult result) {
        testerImported = true;
        testerValid = result.isValid();

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
                .append('/').append(TesterFileValidationService.requiredTesterFiles().size()).append("\n\n");
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
            report.append("  ")
                    .append(finding.severity())
                    .append(finding.corrected() ? " (corrected)" : "")
                    .append(" - ")
                    .append(finding.message())
                    .append('\n');
        }
    }

    private void refreshReports() {
        String separator = !testerReport.isBlank() && !submissionReport.isBlank() ? "\n" : "";
        findingsConsole.setText(testerReport + separator + submissionReport);
        findingsConsole.positionCaret(0);
    }

    private void updateReadiness() {
        if (testerImported && submissionsImported) {
            if (testerValid && submissionsValid) {
                runReadinessText.setText("Both folders are validated and ready.");
                statusText.setText("Inputs ready");
            } else {
                runReadinessText.setText("Resolve the reported validation errors before grading.");
                statusText.setText("Review required");
            }
        } else if (testerImported) {
            runReadinessText.setText("Import student submissions to complete input validation.");
        } else if (submissionsImported) {
            runReadinessText.setText("Import tester files to complete input validation.");
        } else {
            runReadinessText.setText("Import both folders to get started.");
        }
    }

    private String safeMessage(Throwable failure) {
        if (failure == null) {
            return "Unknown validation error";
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
