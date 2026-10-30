package grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import grading.config.GradingSettings;
import grading.progress.ProgressTracker;
import grading.reporting.ScoreSheet;
import grading.reporting.ScoreSheetPreview;
import grading.reporting.ScoreSheetUpdater;
import grading.scoring.StudentScore;
import grading.session.GradingSession;
import grading.session.RunOutcome;
import grading.submission.StudentIdentity;
import grading.submission.SubmissionValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Grades the course's sample assessment (official testers, the six sample students, the LMS score sheet) and
 * compares every mark with the course's ground truth. The materials are not in this public repository, so
 * the test runs only when {@code -Dsample.assessment.dir} points at the course's "Given materials" folder.
 */
@Timeout(300)
class SampleAssessmentEndToEndTest {

    private static final String DIRECTORY_PROPERTY = "sample.assessment.dir";
    private static final String UNCONFIRMED_STUDENT = "jing.lim.2021";
    private static final Duration GRADING_LIMIT = Duration.ofSeconds(240);
    private static final long POLL_MILLIS = 200;
    private static final double TOLERANCE = 1e-9;
    private static final Pattern GROUND_TRUTH_LINE =
            Pattern.compile("^\\s*([\\w.]+):\\s*(.*?)\\s*Total:\\s*([\\d.]+)");
    private static final Pattern QUESTION_MARK = Pattern.compile("(Q\\d[A-Za-z]?):\\s*([\\d.]+)");

    @TempDir
    Path temporary;

    @Test
    void gradesTheSampleAssessmentLikeTheGroundTruth() throws Exception {
        Path materials = materialsFolder();
        GradingSession session = new GradingSession(withReportsFolder(temporary.resolve("reports")),
                new ProgressTracker(), Files.createDirectory(temporary.resolve("work")));

        assertTrue(session.importTesters(materials.resolve("Tester-Files")).isValid(), "official testers");
        List<SubmissionValidationResult> imported = session.importSubmissions(materials.resolve("student-submission"));
        assertEquals(6, imported.size(), "sample submissions");
        confirmUnidentifiedStudent(session, imported);

        session.startGrading();
        long deadline = System.nanoTime() + GRADING_LIMIT.toNanos();
        while (session.grading()) {
            if (System.nanoTime() > deadline) {
                fail("Grading took longer than " + GRADING_LIMIT);
            }
            Thread.sleep(POLL_MILLIS);
        }
        assertEquals(RunOutcome.COMPLETED, session.lastOutcome());

        List<String> mismatches = new ArrayList<>();
        for (Expected expected : groundTruth(materials.resolve("groundtruth.txt"))) {
            Optional<StudentScore> actual = session.currentScores().find(expected.username());
            if (actual.isEmpty()) {
                mismatches.add(expected.username() + " was not graded");
                continue;
            }
            if (Math.abs(actual.get().total() - expected.total()) > TOLERANCE) {
                mismatches.add(expected.username() + " total expected " + expected.total() + " actual "
                        + actual.get().total());
            }
            if (expected.consistent()) {
                expected.marks().forEach((question, mark) -> {
                    Double graded = actual.get().questionScores().get(question);
                    if (graded == null || Math.abs(graded - mark) > TOLERANCE) {
                        mismatches.add(expected.username() + " " + question + " expected " + mark + " actual "
                                + graded);
                    }
                });
            }
        }
        assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));

        ScoreSheetPreview preview = new ScoreSheetUpdater().preview(
                ScoreSheet.load(materials.resolve("IS442-ScoreSheet.csv")), session.currentScores());
        assertEquals(List.of(), preview.notInSheet(), "every graded student matches a score sheet row");
    }

    private static Path materialsFolder() {
        String directory = System.getProperty(DIRECTORY_PROPERTY, "");
        boolean set = !directory.isBlank() && !directory.startsWith("${");
        Assumptions.assumeTrue(set && Files.isDirectory(Path.of(directory, "Tester-Files")),
                "Set -D" + DIRECTORY_PROPERTY + " to the course 'Given materials' folder to run this test.");
        return Path.of(directory);
    }

    // The brief's jing.lim.2021 submitted without a username folder or header names, so the marker confirms it.
    private static void confirmUnidentifiedStudent(GradingSession session, List<SubmissionValidationResult> imported)
            throws Exception {
        for (SubmissionValidationResult result : imported) {
            if (result.isReadyForGrading()) {
                continue;
            }
            boolean known = result.username().contains(UNCONFIRMED_STUDENT)
                    || result.source().getFileName().toString().contains(UNCONFIRMED_STUDENT);
            if (!known) {
                fail("Unexpected submission needing attention: " + result.username() + " " + result.findings());
            }
            session.confirmIdentity(result.username(), new StudentIdentity(UNCONFIRMED_STUDENT, "Jing Lim",
                    UNCONFIRMED_STUDENT + "@computing.smu.edu.sg"));
        }
    }

    /**
     * One ground-truth line. The course file has a line whose question marks do not add up to its total (a
     * student who submitted mostly starter code); its total is right, so only consistent lines are checked per
     * question.
     */
    private record Expected(String username, Map<String, Double> marks, double total) {

        boolean consistent() {
            double sum = marks.values().stream().mapToDouble(Double::doubleValue).sum();
            return Math.abs(sum - total) <= TOLERANCE;
        }
    }

    private static List<Expected> groundTruth(Path file) throws Exception {
        List<Expected> expected = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            Matcher student = GROUND_TRUTH_LINE.matcher(line);
            if (!student.find()) {
                continue;
            }
            Map<String, Double> marks = new LinkedHashMap<>();
            Matcher question = QUESTION_MARK.matcher(student.group(2));
            while (question.find()) {
                String id = question.group(1);
                String normalized = id.length() > 2 ? id.substring(0, 2) + id.substring(2).toLowerCase() : id;
                marks.put(normalized, Double.parseDouble(question.group(2)));
            }
            expected.add(new Expected(student.group(1), marks, Double.parseDouble(student.group(3))));
        }
        return expected;
    }

    private static GradingSettings withReportsFolder(Path folder) {
        GradingSettings current = GradingSettings.current();
        return new GradingSettings(current.compileTimeout(), current.executionTimeout(),
                current.parallelStudents(), current.maxMemoryMegabytes(), current.maxOutputBytes(),
                current.maxJavaFileBytes(), current.maxZipBytes(), current.maxZipEntries(),
                current.emailDomain(), current.maxTesterFileBytes(), current.javaRelease(), current.style(),
                current.penalties(), current.similarity(), folder);
    }
}
