package grading.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import grading.execution.QuestionGradingResult;
import grading.execution.QuestionGradingResult.Status;
import grading.execution.StudentGradingResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradingReportFormatterTest {

    @Test
    void listsQuestionGroupsTotalsAndEachPartWithFailureReasons() {
        StudentGradingResult fen = new StudentGradingResult("fen.lai.2022", List.of(
                new QuestionGradingResult("Q1a", Status.GRADED, 1, 1, 2, null, null, "Graded."),
                new QuestionGradingResult("Q1b", Status.GRADED, 0, 0, 3, null, null, "Graded."),
                new QuestionGradingResult("Q3", Status.COMPILATION_ERROR, 0, 0, 0, null, null,
                        "Compilation did not succeed.")));

        assertEquals("""
                Grading results
                ===============

                [fen.lai.2022]
                Q1: 1.0   Q3: 0.0   Total: 1.0
                  Q1a: 1.0 — GRADED (passed 1, failed 2)
                  Q1b: 0.0 — GRADED (passed 0, failed 3)
                  Q3: 0.0 — COMPILATION_ERROR (passed 0, failed 0)
                    Compilation did not succeed.
                """, GradingReportFormatter.format(List.of(fen)));
    }
}
