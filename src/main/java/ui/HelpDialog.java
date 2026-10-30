package ui;

import grading.progress.GradingStatus;
import java.util.Map;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Explains the grading workflow, the statuses in the results table and what is safe to do. */
final class HelpDialog {

    private static final int TEXT_ROWS = 22;
    private static final Map<GradingStatus, String> STATUS_MEANINGS = Map.of(
            GradingStatus.QUEUED, "waiting to be graded",
            GradingStatus.RUNNING, "being graded now",
            GradingStatus.GRADED, "graded with nothing to check",
            GradingStatus.NEEDS_REVIEW, "a question could not be graded normally, or the answer is very similar"
                    + " to another student's - check the details panel",
            GradingStatus.SKIPPED, "the student's identity is not confirmed - use Resolve issues");

    private HelpDialog() {
    }

    static void show(Window owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle("How to grade");
        alert.setHeaderText("Grading a batch of submissions");
        TextArea content = new TextArea(text());
        content.setEditable(false);
        content.setWrapText(true);
        content.setPrefRowCount(TEXT_ROWS);
        DialogPane pane = alert.getDialogPane();
        pane.setContent(content);
        // dialogs open in their own window, so they need the application's stylesheet explicitly
        pane.getStylesheets().add(HelpDialog.class.getResource("/ui/styles.css").toExternalForm());
        alert.showAndWait();
    }

    static String text() {
        StringBuilder text = new StringBuilder()
                .append("Grading in five steps\n")
                .append("1. Tester files - Import folder: choose the folder with the official testers.\n")
                .append("2. Student submissions - Import folder: choose the folder of student ZIPs or folders.")
                .append(" If Resolve issues appears, confirm who the unidentified submissions belong to.\n")
                .append("3. Run grading. Watch the table; Cancel stops the run. Select a student to see why")
                .append(" marks were lost, and Regrade selected grades them again.\n")
                .append("4. Import score sheet: choose the CSV exported from the LMS.\n")
                .append("5. Save filled sheet: saves a copy with every student's total filled in.")
                .append(" Save reports saves all PDF reports as one ZIP.\n\n")
                .append("What the statuses mean\n");
        for (GradingStatus status : GradingStatus.values()) {
            text.append("- ").append(status.label()).append(": ").append(STATUS_MEANINGS.get(status)).append('\n');
        }
        return text.append("\nGood to know\n")
                .append("- The original submissions and the imported score sheet are never changed.\n")
                .append("- Settings are in config/grading.properties and config/assessment.properties.\n")
                .append("- Every run's reports, including scores.csv, are saved in the grading-reports folder.\n")
                .toString();
    }
}
