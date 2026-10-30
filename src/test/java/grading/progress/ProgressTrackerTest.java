package grading.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import grading.similarity.SimilarityPair;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionValidationResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProgressTrackerTest {

    @Test
    void skippedSubmissionsStartSkipped() {
        ProgressTracker tracker = new ProgressTracker();
        tracker.prepare(List.of(new SubmissionValidationResult(Path.of("x"), "x", null, Map.of(),
                List.of(SubmissionFinding.error("USERNAME_UNKNOWN", "Owner unknown")))));
        assertEquals(GradingStatus.SKIPPED, tracker.snapshot().students().getFirst().status());
    }

    @Test
    void flaggedStudentNeedsReview() {
        ProgressTracker tracker = new ProgressTracker();
        tracker.prepare(List.of(ready("a"), ready("b")));
        tracker.studentFinished("a", false);
        assertEquals(GradingStatus.GRADED, tracker.snapshot().students().getFirst().status());
        tracker.similarity(List.of(new SimilarityPair("Q1a", "a", "b", 0.9, 0.9, 50, 50, List.of(), List.of(),
                true)));
        assertEquals(GradingStatus.NEEDS_REVIEW, tracker.snapshot().students().getFirst().status());
    }

    private static SubmissionValidationResult ready(String username) {
        return new SubmissionValidationResult(Path.of(username), username, null, Map.of(), List.of());
    }
}
