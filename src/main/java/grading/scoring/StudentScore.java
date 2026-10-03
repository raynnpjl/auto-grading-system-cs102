package grading.scoring;

import grading.execution.QuestionGradingResult;
import grading.execution.StudentGradingResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One student's final marks: each question part in assessment order, plus the total. */
public record StudentScore(String username, Map<String, Double> questionScores, double total) {

    public StudentScore {
        Objects.requireNonNull(username, "username");
        questionScores = Collections.unmodifiableMap(new LinkedHashMap<>(questionScores));
    }

    /** Reads the marks already awarded by grading; no score is recalculated here. */
    public static StudentScore from(StudentGradingResult result) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (QuestionGradingResult question : result.questions()) {
            scores.put(question.questionId(), question.score());
        }
        return new StudentScore(result.username(), scores, result.totalScore());
    }
}
