package ui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import javafx.fxml.FXML;
import org.junit.jupiter.api.Test;

class MainViewWiringTest {

    @Test
    void readinessIndicatorShowsTheBatchCount() {
        assertEquals("6 out of 6 submissions ready", MainViewController.formatSubmissionReadiness(6, 6));
        assertEquals("4 out of 6 submissions ready", MainViewController.formatSubmissionReadiness(4, 6));
    }

    @Test
    void bothFolderButtonsAreConnectedToControllerActions() throws Exception {
        String fxml = readMainView();
        String styles = readResource("/ui/styles.css");

        assertAll(
                () -> assertTrue(fxml.contains("fx:controller=\"ui.MainViewController\"")),
                () -> assertTrue(fxml.contains("fx:id=\"testerImportButton\"")),
                () -> assertTrue(fxml.contains("onAction=\"#importTesterFiles\"")),
                () -> assertTrue(fxml.contains("fx:id=\"submissionImportButton\"")),
                () -> assertTrue(fxml.contains("onAction=\"#importSubmissions\"")),
                () -> assertTrue(fxml.contains("fx:id=\"runGradingButton\"")),
                () -> assertTrue(fxml.contains("onAction=\"#runGrading\"")),
                () -> assertEquals(2, countOccurrences(fxml, "styleClass=\"import-button\"")),
                () -> assertTrue(styles.contains(".import-button:hover")),
                () -> assertTrue(styles.contains(".import-button:pressed")),
                () -> assertTrue(fxml.contains("fx:id=\"resolveIssuesButton\"")),
                () -> assertTrue(fxml.contains("onAction=\"#resolveSubmissionIssues\"")),
                () -> assertTrue(styles.contains(".resolution-button")),
                () -> assertFalse(Pattern.compile("<TreeView[^>]*mouseTransparent=\"true\"")
                        .matcher(fxml)
                        .find()),
                () -> assertTrue(MainViewController.class
                        .getDeclaredMethod("importTesterFiles")
                        .isAnnotationPresent(FXML.class)),
                () -> assertTrue(MainViewController.class
                        .getDeclaredMethod("importSubmissions")
                        .isAnnotationPresent(FXML.class)),
                () -> assertTrue(MainViewController.class
                        .getDeclaredMethod("runGrading")
                        .isAnnotationPresent(FXML.class)),
                () -> assertTrue(MainViewController.class
                        .getDeclaredMethod("resolveSubmissionIssues")
                        .isAnnotationPresent(FXML.class)));
    }

    private String readMainView() throws IOException {
        return readResource("/ui/main-view.fxml");
    }

    private String readResource(String resource) throws IOException {
        try (var input = MainViewWiringTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException(resource + " was not found on the test classpath");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int countOccurrences(String text, String value) {
        return text.split(Pattern.quote(value), -1).length - 1;
    }
}
