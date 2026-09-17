package ui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import javafx.fxml.FXML;
import org.junit.jupiter.api.Test;

class MainViewWiringTest {

    @Test
    void bothFolderButtonsAreConnectedToControllerActions() throws Exception {
        String fxml = readMainView();

        assertAll(
                () -> assertTrue(fxml.contains("fx:controller=\"ui.MainViewController\"")),
                () -> assertTrue(fxml.contains("fx:id=\"testerImportButton\"")),
                () -> assertTrue(fxml.contains("onAction=\"#importTesterFiles\"")),
                () -> assertTrue(fxml.contains("fx:id=\"submissionImportButton\"")),
                () -> assertTrue(fxml.contains("onAction=\"#importSubmissions\"")),
                () -> assertFalse(Pattern.compile("<TreeView[^>]*mouseTransparent=\"true\"")
                        .matcher(fxml)
                        .find()),
                () -> assertTrue(MainViewController.class
                        .getDeclaredMethod("importTesterFiles")
                        .isAnnotationPresent(FXML.class)),
                () -> assertTrue(MainViewController.class
                        .getDeclaredMethod("importSubmissions")
                        .isAnnotationPresent(FXML.class)));
    }

    private String readMainView() throws IOException {
        try (var input = MainViewWiringTest.class.getResourceAsStream("/ui/main-view.fxml")) {
            if (input == null) {
                throw new IOException("main-view.fxml was not found on the test classpath");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
