package grading.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import grading.submission.SubmissionPreparationService;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterFileValidationService;
import grading.tester.TesterValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class JavaGradingServiceTest {

    @TempDir
    Path temporary;

    private final JavaGradingService service = new JavaGradingService();

    @Test
    void gradesTheProvidedSampleAndKeepsOriginalsUnchanged() throws Exception {
        Path student = copySample("Q1a.java");
        Path tester = copySample("Q1aTester.java");
        byte[] originalStudent = Files.readAllBytes(student);
        byte[] originalTester = Files.readAllBytes(tester);
        Path workspace;
        try (CompilationResult compiled = service.compile(List.of(student), tester)) {
            workspace = compiled.workspace();
            assertTrue(compiled.process().succeeded(), compiled.process().stderr());
            assertTrue(Files.exists(compiled.classesDirectory().resolve("Q1aTester.class")));

            QuestionGradingResult result = service.execute(compiled);

            assertEquals(QuestionGradingResult.Status.GRADED, result.status(), result.message());
            assertEquals(0, result.score());
            assertEquals(0, result.passedTests());
            assertEquals(3, result.failedTests());
            assertTrue(result.execution().stdout().contains("getIsogramWords([alice, ben, cathy])"));
        }
        assertFalse(Files.exists(workspace));
        org.junit.jupiter.api.Assertions.assertArrayEquals(originalStudent, Files.readAllBytes(student));
        org.junit.jupiter.api.Assertions.assertArrayEquals(originalTester, Files.readAllBytes(tester));
        assertFalse(Files.exists(student.resolveSibling("Q1a.class")));
    }

    @Test
    void awardsFullAndPartialCreditUsingTheOfficialTester() throws Exception {
        Path tester = copySample("Q1aTester.java");
        Path student = write("student/Q1a.java", """
                import java.util.*;
                public class Q1a {
                    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
                        ArrayList<String> result = new ArrayList<>();
                        for (String word : inputs) {
                            String lower = word.toLowerCase(Locale.ROOT);
                            if (lower.chars().distinct().count() == lower.length()) result.add(word);
                        }
                        return result;
                    }
                    public static void main(String[] args) { throw new AssertionError("Do not run student main"); }
                }
                """);

        QuestionGradingResult full = service.gradeQuestion(List.of(student), tester);
        assertEquals(QuestionGradingResult.Status.GRADED, full.status(), full.message());
        assertEquals(3, full.score());
        assertEquals(3, full.passedTests());
        assertEquals(0, full.failedTests());

        Files.writeString(student, """
                import java.util.*;
                public class Q1a {
                    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) { return inputs; }
                }
                """);
        QuestionGradingResult partial = service.gradeQuestion(List.of(student), tester);
        assertEquals(1, partial.score());
        assertEquals(1, partial.passedTests());
        assertEquals(2, partial.failedTests());
    }

    @Test
    void batchesImportedSubmissionsWithIndependentQuestionAndStudentScores() throws Exception {
        Path batch = temporary.resolve("batch");
        createSubmission(batch, "alice", 1.5);
        createSubmission(batch, "bob", 2.5);
        Files.writeString(batch.resolve("alice/Q1/Q1b.java"), """
                /* Name: alice Student
                 * Email ID: alice@smu.edu.sg */
                public class Q1b { broken source }
                """);
        Path testersFolder = temporary.resolve("testers");
        for (String testerFile : TesterFileValidationService.requiredTesterFiles()) {
            String question = testerFile.replace("Tester.java", "");
            write("testers/" + testerFile, "public class " + question + "Tester extends " + question
                    + " { public static void main(String[] args) { System.out.println(score()); } }");
        }
        List<SubmissionValidationResult> submissions = new SubmissionPreparationService()
                .prepareBatch(batch, temporary.resolve("normalized"));
        assertTrue(submissions.stream().allMatch(SubmissionValidationResult::isReadyForGrading), submissions.toString());
        TesterValidationResult testers = new TesterFileValidationService().validate(testersFolder);

        List<StudentGradingResult> results = service.gradeBatch(submissions, testers);

        assertEquals(List.of("alice", "bob"), results.stream().map(StudentGradingResult::username).toList());
        assertEquals(6, results.getFirst().totalScore());
        assertEquals(12.5, results.getLast().totalScore());
        assertEquals(List.of("Q1a", "Q1b", "Q2a", "Q2b", "Q3"),
                results.getFirst().questions().stream().map(QuestionGradingResult::questionId).toList());
        QuestionGradingResult broken = results.getFirst().questions().get(1);
        assertEquals(QuestionGradingResult.Status.COMPILATION_ERROR, broken.status());
        assertEquals(0, broken.score());
        assertFalse(broken.compilation().stderr().isBlank());
        assertNull(broken.execution());
        assertTrue(results.getLast().questions().stream().allMatch(q -> q.status() == QuestionGradingResult.Status.GRADED));

        SubmissionValidationResult invalid = new SubmissionValidationResult(batch, "missing", null,
                java.util.Map.of(), List.of());
        StudentGradingResult skipped = service.gradeStudent(invalid, testers);
        assertEquals(0, skipped.totalScore());
        assertTrue(skipped.questions().stream().allMatch(q -> q.status() == QuestionGradingResult.Status.INVALID_SUBMISSION));
        TesterValidationResult missingTesters = new TesterValidationResult(testersFolder, java.util.Map.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.gradeStudent(submissions.getFirst(), missingTesters));
    }

    @Test
    void reportsTesterCompilationErrorsWithoutRunningAnything() throws Exception {
        Path student = write("Q1a.java", "public class Q1a {}");
        Path tester = write("Q1aTester.java", "public class Q1aTester extends Missing {}");
        QuestionGradingResult result = service.gradeQuestion(List.of(student), tester);
        assertEquals(QuestionGradingResult.Status.COMPILATION_ERROR, result.status());
        assertTrue(result.compilation().stderr().contains("Missing"));
        assertNull(result.execution());
        assertEquals(0, result.score());
    }

    @Test
    void doesNotAwardPrintedScoresWhenTheTesterCrashes() throws Exception {
        QuestionGradingResult result = runTester("System.out.println(99); throw new AssertionError(\"failure\");", service);
        assertEquals(QuestionGradingResult.Status.RUNTIME_ERROR, result.status());
        assertEquals(0, result.score());
        assertTrue(result.execution().stderr().contains("failure"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "NaN", "Infinity", "-1.5", "Passed", "1.0\ntrailing text"})
    void rejectsMissingOrInvalidFinalScores(String output) throws Exception {
        String literal = output.replace("\n", "\\n");
        QuestionGradingResult result = runTester("System.out.println(\"" + literal + "\");", service);
        assertEquals(QuestionGradingResult.Status.INVALID_SCORE, result.status());
        assertEquals(0, result.score());
    }

    @Test
    void keepsStderrSeparateAndPreservesWeightedScores() throws Exception {
        QuestionGradingResult result = runTester("""
                System.out.println("Passed");
                System.out.println("Failed -> Exception");
                System.out.println("1.25\\n\\n");
                System.err.println("diagnostic after score");
                """, service);
        assertEquals(QuestionGradingResult.Status.GRADED, result.status());
        assertEquals(1.25, result.score());
        assertEquals(1, result.passedTests());
        assertEquals(1, result.failedTests());
        assertTrue(result.execution().stderr().contains("diagnostic after score"));
    }

    @Test
    void terminatesInfiniteLoopsAndHangingShutdownHooks() throws Exception {
        JavaGradingService limited = new JavaGradingService(Duration.ofSeconds(10), Duration.ofMillis(500), List.of());
        QuestionGradingResult result = runTester("""
                System.out.println(ProcessHandle.current().pid());
                Runtime.getRuntime().addShutdownHook(new Thread(() -> { while (true) {} }));
                while (true) {}
                """, limited);
        assertEquals(QuestionGradingResult.Status.EXECUTION_TIMEOUT, result.status(), result.message());
        assertEquals(0, result.score());
        assertTrue(result.execution().duration().compareTo(Duration.ofSeconds(5)) < 0);
        long pid = Long.parseLong(result.execution().stdout().strip());
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        assertEquals(2, runTester("System.out.println(2);", limited).score());
    }

    @Test
    void boundsCompilationTimeToo() throws Exception {
        JavaGradingService limited = new JavaGradingService(Duration.ofNanos(1), Duration.ofSeconds(1), List.of());
        QuestionGradingResult result = runTester("System.out.println(1);", limited);
        assertEquals(QuestionGradingResult.Status.COMPILATION_TIMEOUT, result.status(), result.message());
        assertNull(result.execution());
    }

    @Test
    void boundsBothOutputStreamsWithoutDeadlock() throws Exception {
        QuestionGradingResult result = runTester("""
                while (true) {
                    System.out.print("x".repeat(8192));
                    System.err.print("y".repeat(8192));
                }
                """, service);
        assertEquals(QuestionGradingResult.Status.OUTPUT_LIMIT_EXCEEDED, result.status(), result.message());
        assertEquals(0, result.score());
        assertTrue(result.execution().stdout().length() <= BoundedProcessRunner.MAX_OUTPUT_BYTES);
        assertTrue(result.execution().stderr().length() <= BoundedProcessRunner.MAX_OUTPUT_BYTES);
    }

    @Test
    void closesStdinInsteadOfLeavingInteractiveProgramsWaiting() throws Exception {
        QuestionGradingResult result = runTester("System.out.println(System.in.read() == -1 ? 1 : 0);", service);
        assertEquals(1, result.score(), result.message());
    }

    @Test
    void supportsInstructorClassesWithoutCompilingUnrelatedSources() throws Exception {
        Path helper = write("support/Shape.java", "public class Shape { public static int score() { return 4; } }");
        Path support = Files.createDirectory(temporary.resolve("support-classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", support.toString(), helper.toString()));
        write("support-classes/Broken.java", "this must not be compiled");
        JavaGradingService withSupport = new JavaGradingService(Duration.ofSeconds(10), Duration.ofSeconds(2), List.of(support));
        QuestionGradingResult result = runTester("System.out.println(Shape.score());", withSupport);
        assertEquals(QuestionGradingResult.Status.GRADED, result.status(), result.message());
        assertEquals(4, result.score());
    }

    @Test
    void terminatesSpawnedChildrenOnTimeout() throws Exception {
        JavaGradingService limited = new JavaGradingService(Duration.ofSeconds(10), Duration.ofSeconds(1), List.of());
        QuestionGradingResult result = runTester("""
                if (args.length > 0) { Thread.sleep(60000); return; }
                String executable = java.nio.file.Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
                Process child = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"),
                    "Q1aTester", "child").start();
                System.out.println(child.pid());
                Thread.sleep(60000);
                """, limited);
        assertEquals(QuestionGradingResult.Status.EXECUTION_TIMEOUT, result.status(), result.message());
        long pid = Long.parseLong(result.execution().stdout().strip());
        try {
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        } finally {
            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    void interruptionKillsTheActiveJvmAndClosesWorkspace() throws Exception {
        Path student = write("Q1a.java", "public class Q1a {}");
        Path tester = write("Q1aTester.java", """
                public class Q1aTester {
                    public static void main(String[] args) throws Exception {
                        java.nio.file.Files.writeString(java.nio.file.Path.of("pid"), "" + ProcessHandle.current().pid());
                        Thread.sleep(60000);
                    }
                }
                """);
        Path workspace;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (CompilationResult compilation = service.compile(List.of(student), tester)) {
            workspace = compilation.workspace();
            Thread worker = Thread.ofPlatform().start(() -> {
                try {
                    service.execute(compilation);
                } catch (Throwable exception) {
                    failure.set(exception);
                }
            });
            try {
                Path pidFile = workspace.resolve("pid");
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (!Files.exists(pidFile) && worker.isAlive() && System.nanoTime() < deadline) {
                    Thread.sleep(20);
                }
                assertTrue(Files.exists(pidFile));
                long pid = Long.parseLong(Files.readString(pidFile));
                worker.interrupt();
                worker.join(5000);
                assertFalse(worker.isAlive());
                assertTrue(failure.get() instanceof InterruptedException, String.valueOf(failure.get()));
                assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
            } finally {
                worker.interrupt();
                worker.join(5000);
            }
        }
        assertFalse(Files.exists(workspace));
    }

    private QuestionGradingResult runTester(String body, JavaGradingService grading) throws Exception {
        Path student = write("Q1a.java", "public class Q1a {}");
        Path tester = write("Q1aTester.java", "public class Q1aTester extends Q1a { public static void main(String[] args)"
                + " throws Exception { " + body + " } }");
        return grading.gradeQuestion(List.of(student), tester);
    }

    private Path copySample(String filename) throws Exception {
        Path destination = temporary.resolve(filename);
        try (var source = getClass().getResourceAsStream("/grading/execution/sample/" + filename)) {
            Files.copy(source, destination);
        }
        return destination;
    }

    private Path write(String filename, String content) throws IOException {
        Path file = temporary.resolve(filename);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private void createSubmission(Path batch, String username, double score) throws IOException {
        for (var entry : SubmissionPreparationService.requiredJavaFiles().entrySet()) {
            String type = entry.getKey().replace(".java", "");
            Path file = batch.resolve(username).resolve(entry.getValue()).resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            String expression = type.equals("Q3") ? "ShapeComparator.score()" : Double.toString(score);
            Files.writeString(file, "/* Name: " + username + " Student\n * Email ID: " + username + "@smu.edu.sg */\n"
                    + "public class " + type + " { public static double score() { return " + expression + "; } }");
        }
    }
}
