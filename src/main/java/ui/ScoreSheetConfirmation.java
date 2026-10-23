package ui;

import grading.reporting.ScoreSheetPreview;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Shows the marker what writing scores will change and asks for confirmation first. */
final class ScoreSheetConfirmation {

    private static final ButtonType WRITE = new ButtonType("Write scores", ButtonBar.ButtonData.OK_DONE);
    private static final int DETAIL_ROWS = 12;

    private ScoreSheetConfirmation() {
    }

    /** True when the marker chose to write the scores. */
    static boolean confirm(ScoreSheetPreview preview, Window owner) {
        Alert alert = create(preview);
        alert.initOwner(owner);
        return alert.showAndWait().filter(WRITE::equals).isPresent();
    }

    static Alert create(ScoreSheetPreview preview) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", WRITE, ButtonType.CANCEL);
        alert.setTitle("Write scores to score sheet");
        alert.setHeaderText("Write " + preview.entries().size() + " scores into "
                + preview.sheet().path().getFileName() + "?\nA backup of the current file is saved first.");
        TextArea details = new TextArea(preview.describe());
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(DETAIL_ROWS);
        DialogPane pane = alert.getDialogPane();
        pane.setContent(details);
        // dialogs open in their own window, so they need the application's stylesheet explicitly
        pane.getStylesheets().add(ScoreSheetConfirmation.class.getResource("/ui/styles.css").toExternalForm());
        pane.lookupButton(WRITE).getStyleClass().add("dialog-primary-button");
        pane.lookupButton(ButtonType.CANCEL).getStyleClass().add("dialog-secondary-button");
        return alert;
    }
}
