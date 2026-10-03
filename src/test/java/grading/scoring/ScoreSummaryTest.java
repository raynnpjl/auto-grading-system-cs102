package grading.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import grading.execution.QuestionGradingResult;
import grading.execution.QuestionGradingResult.Status;
import grading.execution.StudentGradingResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreSummaryTest {

    @Test
    void keepsEachPartsScoreInAssessmentOrderAndTheGradedTotal() {
        StudentGradingResult graded = student("xing.yan.2023",
                part("Q1a", Status.GRADED, 0), part("Q1b", Status.GRADED, 0),
                part("Q2a", Status.EXECUTION_TIMEOUT, 1), part("Q2b", Status.GRADED, 0),
                part("Q3", Status.COMPILATION_ERROR, 0));

        StudentScore score = StudentScore.from(graded);

        assertEquals("xing.yan.2023", score.username());
        assertEquals(List.of("Q1a", "Q1b", "Q2a", "Q2b", "Q3"), List.copyOf(score.questionScores().keySet()));
        assertEquals(1.0, score.questionScores().get("Q2a"));
        assertEquals(1.0, score.total());
    }

    @Test
    void keepsGradingOrderAndFindsStudentsIgnoringCaseAndSpaces() {
        ScoreSummary summary = ScoreSummary.from(List.of(
                student("ping.lee.2023", part("Q1a", Status.GRADED, 3), part("Q3", Status.GRADED, 4)),
                student("chee.teo.2022", part("Q1a", Status.GRADED, 2.5))));

        assertEquals(List.of("ping.lee.2023", "chee.teo.2022"),
                summary.students().stream().map(StudentScore::username).toList());
        assertEquals(7.0, summary.find(" PING.LEE.2023 ").orElseThrow().total());
        assertEquals(2.5, summary.find("chee.teo.2022").orElseThrow().total());
        assertTrue(summary.find("nobody.2024").isEmpty());
        assertTrue(summary.find(null).isEmpty());
    }

    static StudentGradingResult student(String username, QuestionGradingResult... parts) {
        return new StudentGradingResult(username, List.of(parts));
    }

    static QuestionGradingResult part(String id, Status status, double score) {
        return new QuestionGradingResult(id, status, score, (int) score, 0, null, null, status.name());
    }
}
