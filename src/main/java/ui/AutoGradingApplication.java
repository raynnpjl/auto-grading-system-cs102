package ui;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AutoGradingApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/ui/main-view.fxml"));
        Scene scene = new Scene(root, 1320, 920);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        stage.setTitle("CS102 Auto Grading System");
        stage.setMinWidth(1060);
        stage.setMinHeight(840);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
