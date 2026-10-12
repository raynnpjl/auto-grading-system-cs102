package grading.reporting;

import grading.execution.QuestionGradingResult;
import grading.execution.TestCaseResult;
import java.util.Locale;

/** Wording and Markdown helpers shared by the attempt reports. */
final class PlainLanguage {

    private PlainLanguage() {
    }

    static String status(QuestionGradingResult.Status status) {
        if (status == null) {
            return "no result was recorded";
        }
        return switch (status) {
            case GRADED -> "graded";
            case COMPILATION_ERROR -> "did not compile, so no test case could run";
            case COMPILATION_TIMEOUT -> "took too long to compile, so no test case could run";
            case EXECUTION_TIMEOUT -> "some test cases did not finish within the time limit";
            case OUTPUT_LIMIT_EXCEEDED -> "printed too much output and was stopped";
            case RUNTIME_ERROR -> "the tester stopped with an error before reporting a score";
            case INVALID_SCORE -> "the tester finished without reporting a score";
            case MISSING_FILES -> "not graded because files are missing";
            case GRADING_ERROR -> "could not be graded because of a problem with the grading itself";
        };
    }

    static String outcome(TestCaseResult.Outcome outcome) {
        return switch (outcome) {
            case PASSED -> "Passed";
            case FAILED -> "Failed";
            case TIMED_OUT -> "Timed out";
            case DID_NOT_FINISH -> "Did not finish";
        };
    }

    static String marks(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Text that is safe inside a Markdown table cell. */
    static String cell(String text) {
        if (text == null || text.isBlank()) {
            return "—";
        }
        return text.replace("\\", "\\\\").replace("|", "\\|").replace("\r", "").replace("\n", "<br>");
    }

    /** Code shown inline inside a table cell. */
    static String code(String text) {
        if (text == null || text.isBlank()) {
            return "—";
        }
        // a line break would split the code span, so multi-line output is joined with " / "
        String oneLine = text.strip().replace("\r", "").replace("\n", " / ");
        return "`" + cell(oneLine).replace("`", "'") + "`";
    }
}
