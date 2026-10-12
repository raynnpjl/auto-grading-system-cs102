package grading.reporting;

import grading.execution.QuestionGradingResult;
import grading.execution.StudentGradingResult;
import java.util.List;

/** Formats per-question and per-student scores for the results console. */
public final class GradingReportFormatter {

    private GradingReportFormatter() {
    }

    public static String format(List<StudentGradingResult> results) {
        StringBuilder report = new StringBuilder("Grading results\n===============\n");
        for (StudentGradingResult student : results) {
            report.append('\n').append('[').append(student.username()).append("]\n");
            student.questionScores().forEach((question, score) -> report.append(question)
                    .append(": ").append(score).append("   "));
            report.append("Total: ").append(student.totalScore()).append('\n');
            for (QuestionGradingResult question : student.questions()) {
                report.append("  ").append(question.questionId()).append(": ").append(question.score())
                        .append(" — ").append(question.status())
                        .append(" (passed ").append(question.passedTests())
                        .append(", failed ").append(question.failedTests()).append(")\n");
                if (question.status() != QuestionGradingResult.Status.GRADED) {
                    report.append("    ").append(question.message()).append('\n');
                }
            }
        }
        return report.toString();
    }
}
