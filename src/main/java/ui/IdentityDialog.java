package ui;

import grading.submission.IdentitySuggestions;
import grading.submission.StudentIdentity;
import grading.submission.SubmissionValidationResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Asks the marker to confirm the identity of every submission whose owner is unknown or
 * inconsistent, pre-filled with suggestions from the username.
 */
final class IdentityDialog {

    private static final double DIALOG_WIDTH = 940;
    private static final double MAX_LIST_HEIGHT = 430;
    private static final double HEADER_ROW_HEIGHT = 72;
    private static final double STUDENT_ROW_HEIGHT = 48;
    private static final double COLUMN_GAP = 12;
    private static final double ROW_GAP = 10;

    private final IdentitySuggestions suggestions;

    IdentityDialog(IdentitySuggestions suggestions) {
        this.suggestions = suggestions;
    }

    /** The confirmed identity of each submission, or empty when the marker cancels. */
    Optional<Map<SubmissionValidationResult, StudentIdentity>> show(Window owner,
            List<SubmissionValidationResult> submissions) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Resolve student identity issues");
        dialog.setHeaderText("Complete all verified student details once; the application will update every "
                + "normalized Java header and validate the submissions again.");
        ButtonType applyButton = new ButtonType("Apply and revalidate", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(DIALOG_WIDTH);

        GridPane grid = new GridPane();
        grid.setHgap(COLUMN_GAP);
        grid.setVgap(ROW_GAP);
        grid.setPadding(new Insets(4));
        grid.addRow(0, new Label("Submission"), new Label("Verified username"), new Label("Student name"),
                new Label("Student email"));
        Map<SubmissionValidationResult, IdentityFields> fields = new LinkedHashMap<>();
        int row = 1;
        for (SubmissionValidationResult submission : submissions) {
            IdentityFields entry = prefilledFields(submission.username());
            grid.addRow(row++, new Label(submission.source().getFileName().toString()),
                    entry.username(), entry.name(), entry.email());
            fields.put(submission, entry);
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(MAX_LIST_HEIGHT,
                HEADER_ROW_HEIGHT + submissions.size() * STUDENT_ROW_HEIGHT));
        Label validationMessage = new Label();
        validationMessage.setWrapText(true);
        validationMessage.getStyleClass().add("validation-message");
        Label safetyNote = new Label(
                "Original submissions will not be changed. Only normalized working copies are updated.");
        safetyNote.setWrapText(true);
        VBox content = new VBox(ROW_GAP, safetyNote, scrollPane, validationMessage);
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        Map<SubmissionValidationResult, StudentIdentity> confirmed = new LinkedHashMap<>();
        Button apply = (Button) dialog.getDialogPane().lookupButton(applyButton);
        // Keeps the dialog open until every row is a valid identity.
        apply.addEventFilter(ActionEvent.ACTION, event -> {
            confirmed.clear();
            try {
                fields.forEach((submission, entry) -> confirmed.put(submission, entry.toIdentity()));
                validationMessage.setText("");
            } catch (IllegalArgumentException exception) {
                validationMessage.setText(exception.getMessage());
                event.consume();
            }
        });

        return dialog.showAndWait().filter(applyButton::equals).map(selected -> Map.copyOf(confirmed));
    }

    private IdentityFields prefilledFields(String username) {
        boolean known = !IdentitySuggestions.isPlaceholder(username);
        TextField usernameField = new TextField(known ? username : "");
        usernameField.setPromptText("e.g. jane.tan.2026");
        TextField nameField = new TextField(known ? suggestions.displayName(username) : "");
        nameField.setPromptText("Full name");
        TextField emailField = new TextField(suggestions.email(username).orElse(""));
        emailField.setPromptText("username@school.edu");
        return new IdentityFields(usernameField, nameField, emailField);
    }

    private record IdentityFields(TextField username, TextField name, TextField email) {

        StudentIdentity toIdentity() {
            return new StudentIdentity(username.getText(), name.getText(), email.getText());
        }
    }
}
