package ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import grading.AssessmentFixtures;
import grading.assessment.AssessmentDefinition;
import grading.progress.GradingStatus;
import java.awt.image.BufferedImage;
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

@Timeout(90)
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
        // An invalid tester folder does not replace the valid one imported earlier.
        assertFalse(fx(() -> button("runGradingButton").isDisabled()));
        assertTrue(console().contains("imported earlier are still valid"), console());
        importTesters(testers);
        assertFalse(fx(() -> button("runGradingButton").isDisabled()));

        // An unreadable folder keeps the submissions imported before it.
        importStudents(temporary.resolve("does-not-exist"));
        assertFalse(fx(() -> button("runGradingButton").isDisabled()));
        assertTrue(console().contains("previously imported submissions are kept"), console());
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
    void removesNormalizedCopiesWhenClosed() throws Exception {
        importStudents(student);
        Path work = fx(() -> controller.workFolder());
        try (var copies = Files.list(work)) {
            Path normalized = copies.findFirst().orElseThrow();
            assertTrue(Files.isDirectory(normalized.resolve("alice")));
        }
        fx(() -> {
            controller.shutdown();
            return null;
        });
        assertFalse(Files.exists(work), "closing the window removes this session's copies");
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
        grade();
        String report = console();
        assertTrue(report.contains("[alice]") && report.contains("Total: 10.0"), report);
        assertTrue(report.contains("Not graded (confirm identity"), report);
        assertTrue(report.contains("Reports\n=======\nFolder: ") && report.contains("summary.pdf"), report);
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
        assertTrue(report.contains("[unknown-"), report);
    }

    @Test
    void gradesFromTheButtonAndShowsGroupedScoresAndTerminalLogs() throws Exception {
        importStudents(student);
        assertTrue(fx(() -> button("runGradingButton").isDisabled()));
        importTesters(testers);
        // A broken part must not prevent its sibling or subsequent questions from being graded.
        Files.writeString(testers.resolve("Q1bTester.java"), "public class Q1bTester { broken }");
        fx(() -> {
            button("runGradingButton").fire();
            assertTrue(button("runGradingButton").isDisabled());
            assertTrue(button("testerImportButton").isDisabled());
            assertTrue(button("submissionImportButton").isDisabled());
            assertTrue(button("cancelButton").isVisible());
            return null;
        });
        await(() -> !controller.isGrading());
        String report = console();
        assertTrue(report.startsWith("Grading results"), report);
        assertTrue(report.contains("[alice] Q1a: 1.5   Q1b: 0.0   Q2a: 1.5   Q2b: 1.5   Q3: 4.0   Total: 8.5"),
                report);
        assertEquals("Grading complete — review errors", fx(() -> ((Label) root.lookup("#statusText")).getText()));
        assertFalse(fx(() -> button("cancelButton").isVisible()));
        assertTrue(report.contains("[Q1a] Compiling"), report);
        assertTrue(report.contains("[Q1a] Running Q1aTester"), report);
        assertTrue(report.contains("Compilation stderr:"), report);
        assertTrue(report.contains("Staged runtime file: personstester.txt"), report);
        assertTrue(report.contains("Staged support class: DataException.class"), report);
        assertTrue(report.contains("[Q1b] COMPILATION_ERROR"), report);
        assertFalse(Files.exists(student.resolve("Q1/Q1aTester.java")));
        assertFalse(Files.exists(student.resolve("Q1/Q1a.class")));
        snapshotAndCheckLayout(1320, 920, "grading-results-ui.png");
        snapshotAndCheckLayout(1280, 720, "grading-results-ui-720p.png");
        snapshotAndCheckLayout(960, 660, "grading-results-ui-minimum.png");
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
        Path pidFile = writeSleepingTester();
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

    @Test
    void resultsTableListsEveryStudentAfterImport() throws Exception {
        Path batch = Files.createDirectory(temporary.resolve("batch"));
        Files.move(student, batch.resolve("alice"));
        writeStudent(batch.resolve("bob"), "Bob Student", "bob@smu.edu.sg", null);
        importStudents(batch);
        assertEquals(2, fx(() -> controller.resultsTable().view().getItems().size()));
        assertFalse(fx(() -> root.lookup("#progressBox").isVisible()), "the import progress is hidden again");
        assertEquals(GradingStatus.QUEUED, fx(() -> controller.resultsTable().view().getItems().getFirst().status()));
    }

    @Test
    void cancelStopsGradingAndKeepsTheInputs() throws Exception {
        Path pidFile = writeSleepingTester();
        importTesters(testers);
        importStudents(student);
        fx(() -> { button("runGradingButton").fire(); return null; });
        await(() -> Files.exists(pidFile) && Files.size(pidFile) > 0);
        long pid = Long.parseLong(Files.readString(pidFile));
        fx(() -> { button("cancelButton").fire(); return null; });
        await(() -> !controller.isGrading());
        await(() -> !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        assertEquals("Grading cancelled", fx(() -> ((Label) root.lookup("#statusText")).getText()));
        assertFalse(fx(() -> button("runGradingButton").isDisabled()));
        assertFalse(fx(() -> button("testerImportButton").isDisabled()));
    }

    @Test
    void regradeKeepsOtherStudentsMarks() throws Exception {
        Path batch = Files.createDirectory(temporary.resolve("batch"));
        Files.move(student, batch.resolve("alice"));
        writeStudent(batch.resolve("bob"), "Bob Student", "bob@smu.edu.sg", null);
        importTesters(testers);
        importStudents(batch);
        grade();
        assertTrue(console().contains("[bob]") && console().contains("Total: 10.0"), console());
        fx(() -> {
            controller.resultsTable().select("alice");
            assertFalse(button("regradeButton").isDisabled());
            button("regradeButton").fire();
            return null;
        });
        await(() -> !controller.isGrading());
        String report = console();
        assertTrue(report.contains("[alice]") && report.contains("[bob]"), report);
        String full = "Q1a: 1.5   Q1b: 1.5   Q2a: 1.5   Q2b: 1.5   Q3: 4.0   Total: 10.0 — graded";
        assertTrue(report.contains("[alice] " + full) && report.contains("[bob] " + full), report);
    }

    @Test
    void identicalAnswersAreListedAsSimilarWithFeedbackForLostMarks() throws Exception {
        String shared = """
                public class Q1a {
                    public static double score() { return 1.5; }
                    public static int countVowels(String word) {
                        int count = 0;
                        for (int index = 0; index < word.length(); index++) {
                            char letter = Character.toLowerCase(word.charAt(index));
                            if (letter == 'a' || letter == 'e' || letter == 'i' || letter == 'o' || letter == 'u') {
                                count++;
                            }
                        }
                        return count;
                    }
                }
                """;
        Path batch = Files.createDirectory(temporary.resolve("batch"));
        writeStudent(batch.resolve("carol"), "Carol Student", "carol@smu.edu.sg", shared);
        writeStudent(batch.resolve("dave"), "Dave Student", "dave@smu.edu.sg", shared);
        // dave's Q1b does not compile, so he loses that question's marks.
        Files.writeString(batch.resolve("dave/Q1/Q1b.java"),
                "// Name: Dave Student\n// Email: dave@smu.edu.sg\npublic class Q1b { broken }");
        importTesters(testers);
        importStudents(batch);
        grade();

        var pairs = fx(() -> controller.similarityList().listed());
        assertTrue(pairs.stream().anyMatch(pair -> pair.questionId().equals("Q1a")
                && pair.involves("carol") && pair.involves("dave")), pairs.toString());
        assertTrue(fx(() -> ((javafx.scene.control.TabPane) root.lookup("#consoleTabs")).getTabs().get(1)
                .getText()).startsWith("Similar answers ("));

        fx(() -> {
            controller.resultsTable().select("dave");
            return null;
        });
        String detail = fx(() -> controller.detailText());
        assertTrue(detail.contains("similar to carol"), detail);
        assertTrue(detail.contains("Q1b"), detail);
    }

    private Path writeSleepingTester() throws Exception {
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
        return pidFile;
    }

    private static void writeStudent(Path folder, String name, String email, String q1a) throws Exception {
        for (var entry : AssessmentDefinition.sourceFiles().entrySet()) {
            String type = entry.getKey().replace(".java", "");
            Path questionFolder = Files.createDirectories(folder.resolve(entry.getValue()));
            String expression = type.equals("Q3") ? "Shape.score()" : "1.5";
            String body = type.equals("Q1a") && q1a != null ? q1a
                    : "public class " + type + " { public static double score() { return " + expression + "; } }";
            Files.writeString(questionFolder.resolve(entry.getKey()),
                    "// Name: " + name + "\n// Email: " + email + "\n" + body);
        }
        AssessmentFixtures.writeDependencies(folder);
    }

    private void grade() throws Exception {
        fx(() -> { button("runGradingButton").fire(); return null; });
        await(() -> !controller.isGrading());
    }

    private String console() throws Exception {
        return fx(() -> ((TextArea) root.lookup("#findingsConsole")).getText());
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
        long deadline = System.nanoTime() + Duration.ofSeconds(40).toNanos();
        while (System.nanoTime() < deadline) {
            if (fx(condition)) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Timed out waiting for UI operation");
    }
}
