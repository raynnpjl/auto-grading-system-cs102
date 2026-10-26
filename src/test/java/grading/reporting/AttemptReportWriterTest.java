package grading.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import grading.config.GradingSettings;
import grading.execution.ProcessResult;
import grading.execution.QuestionGradingResult;
import grading.execution.StudentGradingResult;
import grading.style.FileStyleResult;
import grading.style.StudentStyleResult;
import grading.style.StyleRules;
import grading.style.StyleViolation;
import grading.submission.FileCorrection;
import grading.submission.FindingSeverity;
import grading.submission.HeaderIdentity;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.zip.ZipFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttemptReportWriterTest {

    private static final LocalDateTime STARTED = LocalDateTime.of(2026, 10, 12, 9, 30, 0);

    @TempDir
    Path temporary;

    @Test
    void everyAttemptGetsItsOwnFolderAndListsStudentsNeedingReviewFirst() throws IOException {
        GradingAttempt attempt = attempt();
        AttemptReportWriter writer = new AttemptReportWriter(temporary.resolve("reports"));

        AttemptReportWriter.Written first = writer.write(attempt);
        AttemptReportWriter.Written second = writer.write(attempt);

        assertEquals("2026-10-12_09-30-00", first.attemptId());
        assertEquals("2026-10-12_09-30-00-2", second.attemptId());
        assertNotEquals(first.folder(), second.folder());
        assertTrue(first.problems().isEmpty(), first.problems().toString());
        Path folder = first.folder().orElseThrow();
        String summary = text(folder.resolve("summary.pdf"));
        assertTrue(summary.indexOf("bob") < summary.indexOf("alice"), "bob needs a manual check: " + summary);
        assertTrue(summary.contains("Manual check needed for 2"), summary);
        assertTrue(summary.contains("Configuration used") && summary.contains("Penalties"), summary);
        assertTrue(Files.exists(folder.resolve("students/alice.pdf")));
        assertTrue(Files.exists(folder.resolve("summary.pdf")));

        String alice = text(folder.resolve("students/alice.pdf"));
        assertTrue(alice.contains("Name in the Java headers Alice Tan"), alice);
        assertTrue(alice.contains("alice.zip/Q1a.java"), alice);
        assertTrue(alice.contains("1 answer() Passed 42 42"), alice);
        assertTrue(alice.contains("Final mark 3.0"), alice);
        assertTrue(alice.contains("Not automatically checked"), alice);
        assertTrue(alice.contains("Page 1 of"), alice);
    }

    @Test
    void missingAndBrokenResultsBecomeNotesInsteadOfErrors() throws IOException {
        Path folder = new AttemptReportWriter(temporary.resolve("reports")).write(attempt()).folder().orElseThrow();

        String bob = text(folder.resolve("students/bob.pdf"));
        assertTrue(bob.contains("not graded in this attempt"), bob);
        assertTrue(bob.contains("Style was not checked"), bob);

        String carol = text(folder.resolve("students/carol.pdf"));
        assertTrue(carol.contains("did not compile"), carol);
        assertTrue(carol.contains("This section could not be produced"), carol);
        assertTrue(carol.contains("Score breakdown Part Marks Q1a 0.0 Tester marks 0.0"),
                "the other sections are still written: " + carol);
    }

    @Test
    void aReportsFolderThatCannotBeCreatedIsReportedNotThrown() throws IOException {
        Path blocked = Files.writeString(temporary.resolve("not-a-folder"), "a file");

        AttemptReportWriter.Written written = new AttemptReportWriter(blocked.resolve("reports")).write(attempt());

        assertTrue(written.folder().isEmpty());
        assertTrue(written.problems().getFirst().contains("could not be created"), written.problems().toString());
    }

    @Test
    void savesAnAttemptAsOneZipFile() throws IOException {
        Path folder = new AttemptReportWriter(temporary.resolve("reports")).write(attempt()).folder().orElseThrow();
        Path zip = temporary.resolve("saved/reports.zip");
        Files.createDirectories(zip.getParent());

        ReportArchive.save(folder, zip);

        try (ZipFile archive = new ZipFile(zip.toFile())) {
            String root = folder.getFileName() + "/";
            assertTrue(archive.getEntry(root + "summary.pdf") != null);
            assertTrue(archive.getEntry(root + "students/alice.pdf") != null);
            assertEquals(4, archive.size());
        }
        try (var leftovers = Files.list(zip.getParent())) {
            assertEquals(List.of(zip), leftovers.toList(), "no temporary file is left behind");
        }
        IOException missing = assertThrows(IOException.class,
                () -> ReportArchive.save(temporary.resolve("gone"), temporary.resolve("other.zip")));
        assertTrue(missing.getMessage().contains("no longer exists"), missing.getMessage());
    }

    private static String text(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    private GradingAttempt attempt() {
        ProcessResult compiled = new ProcessResult(ProcessResult.Status.EXITED, 0, "", "", Duration.ZERO);
        ProcessResult ran = new ProcessResult(ProcessResult.Status.EXITED, 0,
                "Test 1: answer()\nExpected : 42\nActual   : 42\nPassed\n3.0\n", "", Duration.ZERO);
        StudentGradingResult aliceGrading = new StudentGradingResult("alice", List.of(new QuestionGradingResult(
                "Q1a", QuestionGradingResult.Status.GRADED, 3, 1, 0, compiled, ran, "Graded.")));
        SubmissionValidationResult alice = new SubmissionValidationResult(temporary.resolve("alice.zip"), "alice",
                null, Map.of(), List.of(), new HeaderIdentity(Optional.of("Alice Tan"), Optional.empty()),
                List.of(new FileCorrection("alice.zip/Q1a.java", "alice/Q1/Q1a.java", "Moved.")), Map.of());
        StudentStyleResult aliceStyle = new StudentStyleResult("alice", List.of(), OptionalDouble.of(10), 10,
                List.of());

        SubmissionValidationResult bob = new SubmissionValidationResult(temporary.resolve("bob"), "bob", null,
                Map.of(), List.of(new SubmissionFinding(FindingSeverity.ERROR, "USERNAME_UNKNOWN", "Unknown.", false)));

        ProcessResult failed = new ProcessResult(ProcessResult.Status.EXITED, 1, "", "Q1a.java:3: error",
                Duration.ZERO);
        StudentGradingResult carolGrading = new StudentGradingResult("carol", List.of(new QuestionGradingResult(
                "Q1a", QuestionGradingResult.Status.COMPILATION_ERROR, 0, 0, 0, failed, null, null)));
        SubmissionValidationResult carol = new SubmissionValidationResult(temporary.resolve("carol"), "carol", null,
                Map.of(), List.of());
        // a violation without its rule would break the style section; the report must survive it
        StudentStyleResult brokenStyle = new StudentStyleResult("carol", List.of(new FileStyleResult("Q1a.java", true,
                List.of(new StyleViolation(1, 1, null, "Problem.", "code")), 0, "")), OptionalDouble.of(9), 10,
                List.of());

        GradingSettings settings = GradingSettings.current();
        StyleRules rules = StyleRules.load(settings.style().rules());
        List<SubmissionValidationResult> submissions = List.of(alice, bob, carol);
        Map<SubmissionValidationResult, StudentStyleResult> styles = Map.of(alice, aliceStyle, carol, brokenStyle);
        return GradingAttempt.assemble(STARTED, temporary.resolve("testers"), temporary.resolve("submissions"),
                settings, submissions, List.of(aliceGrading, carolGrading), styles::get, rules);
    }
}
