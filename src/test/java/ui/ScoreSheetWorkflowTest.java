package ui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import grading.AssessmentFixtures;
import grading.assessment.AssessmentDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(45)
class ScoreSheetWorkflowTest {

    private static final String SHEET = "OrgDefinedId,Username,Last Name,First Name,Email,"
            + "Calculated Final Grade Numerator,Calculated Final Grade Denominator,End-of-Line Indicator\r\n"
            + "#01400001,#alice,_,ALICE STUDENT,alice@smu.edu.sg,,20,#\r\n"
            + "#01400002,#bob.absent.2024,_,BOB ABSENT,bob.absent.2024@smu.edu.sg,,20,#\r\n";

    @TempDir
    Path temporary;
    private MainViewController controller;
    private Parent root;
    private Path testers;
    private Path student;
    private Path sheet;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @AfterAll
    static void stopToolkit() {
        Platform.exit();
    }

    @BeforeEach
    void createViewAssessmentAndSheet() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main-view.fxml"));
            root = loader.load();
            controller = loader.getController();
            new Scene(root, 1320, 920);
            return null;
        });
        testers = Files.createDirectory(temporary.resolve("testers"));
        student = Files.createDirectory(temporary.resolve("alice"));
        for (var entry : AssessmentDefinition.sourceFiles().entrySet()) {
            String type = entry.getKey().replace(".java", "");
            Path folder = Files.createDirectories(student.resolve(entry.getValue()));
            String expression = type.equals("Q3") ? "Shape.score()" : "1.5";
            Files.writeString(folder.resolve(entry.getKey()),
                    "// Name: Alice Student\n// Email: alice@smu.edu.sg\n"
                    + "public class " + type + " { public static double score() { return " + expression + "; } }");
        }
        AssessmentFixtures.writeDependencies(student);
        for (String filename : AssessmentDefinition.testerFiles()) {
            String type = filename.replace(".java", "");
            String question = type.replace("Tester", "");
            Files.writeString(testers.resolve(filename), "public class " + type + " extends " + question
                    + " { public static void main(String[] args) { System.out.println(score()); } }");
        }
        sheet = Files.writeString(Files.createDirectory(temporary.resolve("lms")).resolve("ScoreSheet.csv"), SHEET);
    }

    @Test
    void scoreSheetButtonsAreConnectedToControllerActions() throws Exception {
        String fxml;
        try (var input = getClass().getResourceAsStream("/ui/main-view.fxml")) {
            fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertAll(
                () -> assertTrue(fxml.contains("fx:id=\"downloadScoresheetButton\"")),
                () -> assertTrue(fxml.contains("onAction=\"#downloadScoresheet\"")),
                () -> assertTrue(MainViewController.class.getDeclaredMethod("downloadScoresheet")
                        .isAnnotationPresent(FXML.class)));
    }

    @Test
    void writesGradedTotalsToCsv() throws Exception {
        assertTrue(fx(() -> button("downloadScoresheetButton").isDisabled()));

        grade();
        assertFalse(fx(() -> button("downloadScoresheetButton").isDisabled()));

        Path destination = temporary.resolve("downloaded_scoresheet.csv");
        fx(() -> { controller.downloadScoresheet(destination); return null; });

        assertTrue(Files.exists(destination));
        String content = Files.readString(destination);
        assertTrue(content.contains("Username,Score"));
        assertTrue(content.contains("alice,10.0"));

        String report = console();
        assertTrue(report.startsWith("Scores written to CSV:"), report);
    }

    @Test
    void requiresANewRunAfterReimportingSubmissions() throws Exception {
        assertTrue(fx(() -> button("downloadScoresheetButton").isDisabled()), "nothing graded yet");

        grade();
        assertFalse(fx(() -> button("downloadScoresheetButton").isDisabled()));

        fx(() -> { controller.importSubmissions(student); return null; });
        await(() -> !button("submissionImportButton").isDisabled());
        assertTrue(fx(() -> button("downloadScoresheetButton").isDisabled()), "scores from the earlier run were cleared");
    }

    private void grade() throws Exception {
        fx(() -> { controller.importTesterFiles(testers); return null; });
        await(() -> !button("testerImportButton").isDisabled());
        fx(() -> { controller.importSubmissions(student); return null; });
        await(() -> !button("submissionImportButton").isDisabled());
        try {
            fx(() -> { button("runGradingButton").fire(); return null; });
            await(() -> !button("runGradingButton").isDisabled());
        } finally {
            fx(() -> { controller.shutdown(); return null; });
        }
    }

    private String console() throws Exception {
        return fx(() -> ((TextArea) root.lookup("#findingsConsole")).getText());
    }

    private Button button(String id) {
        return (Button) root.lookup("#" + id);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (fx(condition)) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Timed out waiting for UI operation");
    }
}
