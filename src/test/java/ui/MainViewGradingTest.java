package ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import grading.AssessmentFixtures;
import grading.assessment.AssessmentDefinition;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(45)
class MainViewGradingTest {

    @TempDir
    Path temporary;
    private MainViewController controller;
    private Parent root;
    private Path testers;
    private Path student;

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

    // Removes the normalized copies this test's imports created.
    @AfterEach
    void closeView() throws Exception {
        fx(() -> {
            controller.shutdown();
            return null;
        });
    }

    @BeforeEach
    void createViewAndAssessment() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main-view.fxml"));
            root = loader.load();
            controller = loader.getController();
            // Dialogs open real windows; keep test runs off the screen.
            controller.disableAutomaticIdentityPrompt();
            controller.useReportsFolder(temporary.resolve("reports"));
            Scene scene = new Scene(root, 1320, 920);
            scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            root.applyCss();
            root.layout();
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
    }

    @Test
    void requiresBothValidImportsAndInvalidatesReadinessWhenReimporting() throws Exception {
        assertTrue(fx(() -> button("runGradingButton").isDisabled()));
        importTesters(testers);
        assertTrue(fx(() -> button("runGradingButton").isDisabled()));
        importStudents(student);
        assertFalse(fx(() -> button("runGradingButton").isDisabled()));

        // A missing dependency is reported but costs only its question, so grading stays available.
        for (String relative : java.util.List.of("Q2/personstester.txt", "Q2/DataException.class")) {
            Path file = student.resolve(relative);
            byte[] content = Files.readAllBytes(file);
            Files.delete(file);
            importStudents(student);
            assertFalse(fx(() -> button("runGradingButton").isDisabled()));
            assertTrue(fx(() -> ((TextArea) root.lookup("#findingsConsole")).getText().contains(relative)));
            Files.write(file, content);
            importStudents(student);
            assertFalse(fx(() -> button("runGradingButton").isDisabled()));
        }

        Path incomplete = Files.createDirectory(temporary.resolve("incomplete-testers"));
        Files.copy(testers.resolve("Q1aTester.java"), incomplete.resolve("Q1aTester.java"));
        fx(() -> {
            controller.importTesterFiles(incomplete);
            assertTrue(button("runGradingButton").isDisabled());
            assertTrue(button("submissionImportButton").isDisabled());
            return null;
        });
        await(() -> !button("testerImportButton").isDisabled());
        assertTrue(fx(() -> button("runGradingButton").isDisabled()));
        importTesters(testers);
        assertFalse(fx(() -> button("runGradingButton").isDisabled()));

        importStudents(temporary.resolve("does-not-exist"));
        assertTrue(fx(() -> button("runGradingButton").isDisabled()));
        importStudents(student);
        assertFalse(fx(() -> button("runGradingButton").isDisabled()));

        // A missing file costs only its question, so grading stays available.
        Files.delete(student.resolve("Q1/Q1a.java"));
        importStudents(student);
        assertFalse(fx(() -> button("runGradingButton").isDisabled()));
        assertTrue(fx(() -> ((TextArea) root.lookup("#findingsConsole")).getText()
                .contains("READY - Q1a will score 0 (missing files)")));

        // A student whose owner is unconfirmed is skipped; with no one else to grade, Run stays off.
        Path unknown = Files.createDirectory(temporary.resolve("RenameToYourUsername"));
        Files.createDirectories(unknown.resolve("Q1"));
        Files.writeString(unknown.resolve("Q1/Q1a.java"), "public class Q1a {}");
        importStudents(unknown);
        assertTrue(fx(() -> button("runGradingButton").isDisabled()));
    }

    @Test
    void removesNormalizedCopiesWhenReplacedAndWhenClosed() throws Exception {
        importStudents(student);
        Path first = normalizedWorkspace();
        assertTrue(Files.isDirectory(first.resolve("alice")));

        importStudents(student);
        Path second = normalizedWorkspace();
        assertFalse(Files.exists(first), "re-importing replaces the previous copies");
        assertTrue(Files.isDirectory(second.resolve("alice")));

        fx(() -> {
            controller.shutdown();
            return null;
        });
        assertFalse(Files.exists(second), "closing the window removes this session's copies");
    }

    private Path normalizedWorkspace() throws Exception {
        String report = fx(() -> ((TextArea) root.lookup("#findingsConsole")).getText());
        String marker = "Normalized workspace: ";
        int start = report.indexOf(marker) + marker.length();
        return Path.of(report.substring(start, report.indexOf('\n', start)));
    }

    @Test
    void gradesConfirmedStudentsAndReportsTheSkippedOnes() throws Exception {
        Path batch = Files.createDirectory(temporary.resolve("batch"));
        Files.move(student, batch.resolve("alice"));
        Path unknown = Files.createDirectories(batch.resolve("RenameToYourUsername/Q1"));
        Files.writeString(unknown.resolve("Q1a.java"), "public class Q1a {}");
        importTesters(testers);
        importStudents(batch);
        assertFalse(fx(() -> button("runGradingButton").isDisabled()));
        assertTrue(fx(() -> ((Label) root.lookup("#runReadinessText")).getText()
                .contains("1 out of 2 submissions ready")));
        try {
            fx(() -> { button("runGradingButton").fire(); return null; });
            await(() -> !button("runGradingButton").isDisabled());
        } finally {
            fx(() -> { controller.shutdown(); return null; });
        }
        String report = fx(() -> ((TextArea) root.lookup("#findingsConsole")).getText());
        assertTrue(report.contains("[alice]") && report.contains("Total: 10.0"), report);
        assertTrue(report.contains("Not graded (confirm identity"), report);
        assertTrue(report.contains("Reports\n=======\nAttempt ") && report.contains("summary.pdf"), report);
        try (var attempts = Files.list(temporary.resolve("reports"))) {
            Path attempt = attempts.findFirst().orElseThrow();
            assertTrue(Files.exists(attempt.resolve("students/alice.pdf")));
            assertTrue(Files.exists(attempt.resolve("summary.pdf")));
        }
        assertFalse(fx(() -> button("saveReportsButton").isDisabled()));
        Path zip = temporary.resolve("saved.zip");
        fx(() -> {
            controller.saveReports(zip);
            return null;
        });
        await(() -> Files.exists(zip) && !button("saveReportsButton").isDisabled());
        assertTrue(fx(() -> ((Label) root.lookup("#consoleStatus")).getText()).contains("Reports saved"));
        assertTrue(report.contains("  unknown-"), report);
    }

    @Test
    void gradesFromTheButtonAndShowsGroupedScoresAndTerminalLogs() throws Exception {
        importStudents(student);
        assertTrue(fx(() -> button("runGradingButton").isDisabled()));
        importTesters(testers);
        // A broken part must not prevent its sibling or subsequent questions from being graded.
        Files.writeString(testers.resolve("Q1bTester.java"), "public class Q1bTester { broken }");
        ByteArrayOutputStream terminal = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (PrintStream captured = new PrintStream(terminal, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            fx(() -> {
                button("runGradingButton").fire();
                assertTrue(button("runGradingButton").isDisabled());
                assertTrue(button("testerImportButton").isDisabled());
                assertTrue(button("submissionImportButton").isDisabled());
                return null;
            });
            await(() -> !button("runGradingButton").isDisabled());
        } finally {
            System.setOut(previous);
            fx(() -> { controller.shutdown(); return null; });
        }
        String report = fx(() -> ((TextArea) root.lookup("#findingsConsole")).getText());
        assertTrue(report.startsWith("Grading results"), report);
        assertTrue(report.contains("[alice]"));
        assertTrue(report.contains("Q1: 1.5   Q2: 3.0   Q3: 4.0   Total: 8.5"), report);
        assertTrue(report.contains("Q1b: 0.0 — COMPILATION_ERROR"));
        assertTrue(report.contains("Q3: 4.0 — GRADED"));
        assertEquals("Grading complete — review errors", fx(() -> ((Label) root.lookup("#statusText")).getText()));
        String output = terminal.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("[Q1a] Compiling"));
        assertTrue(output.contains("[Q1a] Running Q1aTester"));
        assertTrue(output.contains("Compilation stderr:"));
        assertTrue(output.contains("Staged runtime file: personstester.txt"));
        assertTrue(output.contains("Staged support class: DataException.class"));
        assertTrue(output.contains("Question scores: {Q1=1.5, Q2=3.0, Q3=4.0} | Total: 8.5"));
        assertFalse(Files.exists(student.resolve("Q1/Q1aTester.java")));
        assertFalse(Files.exists(student.resolve("Q1/Q1a.class")));
        snapshotAndCheckLayout(1320, 920, "grading-results-ui.png");
        snapshotAndCheckLayout(1060, 840, "grading-results-ui-minimum.png");
    }

    private void snapshotAndCheckLayout(int sceneWidth, int sceneHeight, String filename) throws Exception {
        fx(() -> {
            root.getScene().setRoot(new javafx.scene.Group());
            Scene scene = new Scene(root, sceneWidth, sceneHeight);
            scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            WritableImage snapshot = scene.snapshot(null);
            var console = root.lookup(".console-panel");
            var imports = root.lookup(".file-panel");
            var consoleBounds = console.localToScene(console.getBoundsInLocal());
            var importBounds = imports.localToScene(imports.getBoundsInLocal());
            assertTrue(consoleBounds.getMinY() > importBounds.getMaxY());
            assertTrue(consoleBounds.getHeight() >= 210);
            assertTrue(button("runGradingButton").localToScene(button("runGradingButton").getBoundsInLocal()).getMaxY()
                    <= sceneHeight);
            int width = (int) snapshot.getWidth();
            int height = (int) snapshot.getHeight();
            int[] pixels = new int[width * height];
            snapshot.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0,
                    width);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            ImageIO.write(image, "png", Path.of("target", filename).toFile());
            return null;
        });
    }

    @Test
    void shutdownCancelsTheActiveGradingProcess() throws Exception {
        Path pidFile = temporary.resolve("tester-pid");
        String literal = pidFile.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        Files.writeString(testers.resolve("Q1aTester.java"), """
                public class Q1aTester {
                    public static void main(String[] args) throws Exception {
                        java.nio.file.Files.writeString(java.nio.file.Path.of("%s"), "" + ProcessHandle.current()
                                .pid());
                        Thread.sleep(60000);
                    }
                }
                """.formatted(literal));
        importTesters(testers);
        importStudents(student);
        try {
            fx(() -> { button("runGradingButton").fire(); return null; });
            await(() -> Files.exists(pidFile) && Files.size(pidFile) > 0);
            long pid = Long.parseLong(Files.readString(pidFile));
            fx(() -> { controller.shutdown(); return null; });
            await(() -> !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        } finally {
            fx(() -> { controller.shutdown(); return null; });
        }
    }

    private void importTesters(Path folder) throws Exception {
        fx(() -> { controller.importTesterFiles(folder); return null; });
        await(() -> !button("testerImportButton").isDisabled());
    }

    private void importStudents(Path folder) throws Exception {
        fx(() -> { controller.importSubmissions(folder); return null; });
        await(() -> !button("submissionImportButton").isDisabled());
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
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (fx(condition)) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Timed out waiting for UI operation");
    }
}
