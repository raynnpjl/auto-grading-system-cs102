package grading.scoring;

import grading.config.PenaltySettings;
import grading.execution.StudentGradingResult;
import grading.style.StudentStyleResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a student's final mark is made up: the marks the testers awarded, minus any penalties.
 *
 * @param username         the student
 * @param questionScores   the tester marks of each question part
 * @param testerMarks      the total of the tester marks
 * @param styleViolations  the style violations that count towards the penalty
 * @param stylePenalty     the marks deducted for style
 * @param finalMark        the mark written to the score sheet, never below zero
 */
public record ScoreBreakdown(String username, Map<String, Double> questionScores, double testerMarks,
        int styleViolations, double stylePenalty, double finalMark) {

    public ScoreBreakdown {
        questionScores = Collections.unmodifiableMap(new LinkedHashMap<>(questionScores));
    }

    /** The style result may be null when style could not be checked; then no style penalty applies. */
    public static ScoreBreakdown of(StudentGradingResult result, StudentStyleResult style,
            PenaltySettings penalties) {
        StudentScore score = StudentScore.from(result);
        int violations = style == null ? 0 : style.countedViolations();
        double penalty = Math.min(penalties.styleCap(), penalties.stylePerViolation() * violations);
        return new ScoreBreakdown(result.username(), score.questionScores(), score.total(), violations, penalty,
                Math.max(0, score.total() - penalty));
    }

    /** The student's marks as written to the score sheet. */
    public StudentScore toStudentScore() {
        return new StudentScore(username, questionScores, finalMark);
    }
}
