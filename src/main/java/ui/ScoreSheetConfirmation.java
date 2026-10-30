package ui;

import grading.reporting.ScoreSheetPreview;
import java.util.List;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Shows the marker which scores the filled score sheet will contain and asks for confirmation first. */
final class ScoreSheetConfirmation {

    private static final ButtonType SAVE = new ButtonType("Save filled sheet", ButtonBar.ButtonData.OK_DONE);
    private static final int DETAIL_ROWS = 12;

    private ScoreSheetConfirmation() {
    }

    /** True when the marker chose to save the filled sheet. */
    static boolean confirm(ScoreSheetPreview preview, Window owner) {
        Alert alert = create(preview);
        alert.initOwner(owner);
        return alert.showAndWait().filter(SAVE::equals).isPresent();
    }

    static Alert create(ScoreSheetPreview preview) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", SAVE, ButtonType.CANCEL);
        alert.setTitle("Save filled score sheet");
        alert.setHeaderText("Fill " + preview.entries().size() + " scores into a copy of "
                + preview.sheet().path().getFileName()
                + "?\nThe filled sheet is saved as a new file; the original is not changed.");
        TextArea details = new TextArea(describe(preview));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(DETAIL_ROWS);
        DialogPane pane = alert.getDialogPane();
        pane.setContent(details);
        // dialogs open in their own window, so they need the application's stylesheet explicitly
        pane.getStylesheets().add(ScoreSheetConfirmation.class.getResource("/ui/styles.css").toExternalForm());
        pane.lookupButton(SAVE).getStyleClass().add("dialog-primary-button");
        pane.lookupButton(ButtonType.CANCEL).getStyleClass().add("dialog-secondary-button");
        return alert;
    }

    /** What filling the sheet changes: the scores replaced, and the students on only one side. */
    static String describe(ScoreSheetPreview preview) {
        StringBuilder text = new StringBuilder()
                .append("File: ").append(preview.sheet().path()).append('\n')
                .append("Scores to write: ").append(preview.entries().size()).append(" of ")
                .append(preview.sheet().rowCount()).append(" students in the sheet\n");
        for (ScoreSheetPreview.Entry entry : preview.entries()) {
            if (!entry.previousScore().isBlank() && !entry.previousScore().equals(entry.newScore())) {
                text.append("  ").append(entry.username()).append(": ").append(entry.previousScore())
                        .append(" -> ").append(entry.newScore()).append('\n');
            }
        }
        appendList(text, "Graded students not in the sheet (not written)", preview.notInSheet());
        appendList(text, "Sheet students with no graded submission (left unchanged)", preview.noSubmission());
        return text.toString();
    }

    private static void appendList(StringBuilder text, String title, List<String> usernames) {
        text.append(title).append(": ").append(usernames.isEmpty() ? "none" : usernames.size()).append('\n');
        usernames.forEach(username -> text.append("  ").append(username).append('\n'));
    }
}
