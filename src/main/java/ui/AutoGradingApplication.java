package ui;

import java.io.IOException;
import java.util.Optional;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Starts the desktop application and stops any grading still running when the window closes. */
public class AutoGradingApplication extends Application {

    private static final double PREFERRED_WIDTH = 1320;
    private static final double PREFERRED_HEIGHT = 920;
    private static final double MIN_WIDTH = 960;
    private static final double MIN_HEIGHT = 660;

    private MainViewController controller;

    @Override
    public void start(Stage stage) throws IOException {
        Optional<String> problem = StartupCheck.settingsProblem();
        if (problem.isPresent()) {
            showSettingsProblem(problem.get());
            Platform.exit();
            return;
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main-view.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        controller.useHostServices(getHostServices());
        // The first window never opens larger than the screen, such as a 1280x720 projector.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, Math.min(PREFERRED_WIDTH, screen.getWidth()),
                Math.min(PREFERRED_HEIGHT, screen.getHeight()));
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        stage.setTitle("CS102 Auto Grading System");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    private static void showSettingsProblem(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Settings problem");
        alert.setHeaderText("The grader could not start");
        alert.setContentText(message + "\n\nFix the file in the config folder (or remove it to use the "
                + "defaults) and start the application again.");
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
