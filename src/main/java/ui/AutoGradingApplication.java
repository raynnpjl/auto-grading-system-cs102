package ui;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Starts the desktop application and stops any grading still running when the window closes. */
public class AutoGradingApplication extends Application {

    private static final int WIDTH = 1320;
    private static final int HEIGHT = 920;
    private static final int MIN_WIDTH = 1060;
    private static final int MIN_HEIGHT = 840;

    private MainViewController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main-view.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        stage.setTitle("CS102 Auto Grading System");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.show();
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
