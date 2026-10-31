package ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * One of the two import panels. Before a folder is imported it shows the instructions, the folder path and the
 * file tree; once an import succeeds it shrinks to a one-line summary, so the results table gets the room. The
 * marker can still open the files from the summary.
 */
final class ImportPanel {

    private static final String IMPORT_TEXT = "Import folder";
    private static final String CHANGE_TEXT = "Change folder";
    private static final String SHOW_FILES = "Show files";
    private static final String HIDE_FILES = "Hide files";

    private final Button importButton;
    private final Label hint;
    private final TextField folderField;
    private final Node browser;
    private final Node summary;
    private final Label summaryText;
    private final Hyperlink filesToggle;
    private boolean filesShown;

    ImportPanel(Button importButton, Label hint, TextField folderField, Node browser, Node summary,
            Label summaryText, Hyperlink filesToggle) {
        this.importButton = importButton;
        this.hint = hint;
        this.folderField = folderField;
        this.browser = browser;
        this.summary = summary;
        this.summaryText = summaryText;
        this.filesToggle = filesToggle;
        filesToggle.setOnAction(event -> showFiles(!filesShown));
    }

    /** Shrinks the panel to one line, such as "5 of 5 tester files valid". */
    void collapse(String text) {
        summaryText.setText(text);
        importButton.setText(CHANGE_TEXT);
        setShown(summary, true);
        setShown(hint, false);
        showFiles(false);
    }

    /** Shows the instructions and files again, for an import that failed or needs attention. */
    void expand() {
        importButton.setText(summary.isVisible() ? CHANGE_TEXT : IMPORT_TEXT);
        setShown(hint, !summary.isVisible());
        showFiles(true);
    }

    private void showFiles(boolean shown) {
        filesShown = shown;
        filesToggle.setText(shown ? HIDE_FILES : SHOW_FILES);
        setShown(folderField, shown);
        setShown(browser, shown);
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}
