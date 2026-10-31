package ui;

import grading.reporting.ScoreSheetPreview;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Shows the marker which scores the filled score sheet will contain and asks for confirmation first. */
final class ScoreSheetConfirmation {

    private static final ButtonType SAVE = new ButtonType("Save filled sheet", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType REVIEW_FIRST = new ButtonType("Review first", ButtonBar.ButtonData.CANCEL_CLOSE);
    private static final int DETAIL_ROWS = 12;

    private ScoreSheetConfirmation() {
    }

    /**
     * True when the marker chose to save the filled sheet.
     *
     * @param toReview students whose totals still need a person to check them
     * @param skipped  students who were not graded, so get no score
     */
    static boolean confirm(ScoreSheetPreview preview, List<String> toReview, List<String> skipped, Window owner) {
        Alert alert = create(preview, toReview, skipped);
        alert.initOwner(owner);
        return alert.showAndWait().filter(SAVE::equals).isPresent();
    }

    static Alert create(ScoreSheetPreview preview) {
        return create(preview, List.of(), List.of());
    }

    // With students still to check, the dialog warns first and "Review first" is the default answer.
    static Alert create(ScoreSheetPreview preview, List<String> toReview, List<String> skipped) {
        boolean warn = !toReview.isEmpty() || !skipped.isEmpty();
        ButtonType back = warn ? REVIEW_FIRST : ButtonType.CANCEL;
        Alert alert = new Alert(warn ? Alert.AlertType.WARNING : Alert.AlertType.CONFIRMATION, "", SAVE, back);
        alert.setTitle("Save filled score sheet");
        alert.setHeaderText(warn ? warningHeader(toReview, skipped)
                : "Fill " + preview.entries().size() + " scores into a copy of " + preview.sheet().path().getFileName()
                        + "?\nThe filled sheet is saved as a new file; the original is not changed.");
        TextArea details = new TextArea(warnings(toReview, skipped) + describe(preview));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(DETAIL_ROWS);
        DialogPane pane = alert.getDialogPane();
        pane.setContent(details);
        // dialogs open in their own window, so they need the application's stylesheet explicitly
        pane.getStylesheets().add(ScoreSheetConfirmation.class.getResource("/ui/styles.css").toExternalForm());
        Button save = (Button) pane.lookupButton(SAVE);
        Button cancel = (Button) pane.lookupButton(back);
        save.getStyleClass().add(warn ? "dialog-secondary-button" : "dialog-primary-button");
        cancel.getStyleClass().add(warn ? "dialog-primary-button" : "dialog-secondary-button");
        if (warn) {
            save.setText("Save anyway");
            save.setDefaultButton(false);
            cancel.setDefaultButton(true);
        }
        return alert;
    }

    private static String warningHeader(List<String> toReview, List<String> skipped) {
        List<String> parts = new ArrayList<>();
        if (!toReview.isEmpty()) {
            parts.add(count(toReview.size(), "total still needs", "totals still need") + " review");
        }
        if (!skipped.isEmpty()) {
            parts.add(count(skipped.size(), "student was", "students were") + " skipped and will get no score");
        }
        return "Before you save: " + String.join(", and ", parts) + ".\nThe original sheet is not changed either way.";
    }

    private static String warnings(List<String> toReview, List<String> skipped) {
        StringBuilder text = new StringBuilder();
        if (!toReview.isEmpty()) {
            text.append("Need review (a question could not be graded normally, or the answer is similar to another")
                    .append(" student's): ").append(String.join(", ", toReview)).append('\n');
        }
        if (!skipped.isEmpty()) {
            text.append("Skipped, identity not confirmed: ").append(String.join(", ", skipped)).append('\n');
        }
        return text.isEmpty() ? "" : text.append('\n').toString();
    }

    private static String count(int number, String one, String many) {
        return number + " " + (number == 1 ? one : many);
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
