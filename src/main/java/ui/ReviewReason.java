package ui;

import grading.execution.QuestionGradingResult;
import grading.progress.GradingStatus;
import grading.progress.StudentProgress;
import java.util.ArrayList;
import java.util.List;

/** Why a student needs a person to look at them, in a few words, for the results table. */
final class ReviewReason {

    private static final String SEPARATOR = "; ";

    private ReviewReason() {
    }

    /** Such as "Q3 did not compile; Q2a: 91% similar to david.2024"; empty when nothing needs checking. */
    static String of(StudentProgress student) {
        if (student.status() == GradingStatus.SKIPPED) {
            return "Identity not confirmed. Use Resolve issues";
        }
        List<String> reasons = new ArrayList<>();
        student.problems().forEach((question, status) -> reasons.add(question + " " + describe(status)));
        reasons.addAll(student.flags());
        return String.join(SEPARATOR, reasons);
    }

    private static String describe(QuestionGradingResult.Status status) {
        return switch (status) {
            case GRADED -> "graded";
            case COMPILATION_ERROR -> "did not compile";
            case COMPILATION_TIMEOUT -> "took too long to compile";
            case EXECUTION_TIMEOUT -> "timed out";
            case OUTPUT_LIMIT_EXCEEDED -> "printed too much output";
            case RUNTIME_ERROR -> "crashed while running";
            case INVALID_SCORE -> "reported no score";
            case MISSING_FILES -> "has missing files";
            case GRADING_ERROR -> "could not be graded";
        };
    }
}
