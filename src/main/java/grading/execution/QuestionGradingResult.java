package grading.execution;

/**
 * The outcome of grading one question for one student. A timed-out question keeps credit for the
 * test cases that completed; every other failure scores zero.
 *
 * @param questionId  the question, such as Q1a
 * @param status      how grading ended
 * @param score       the marks awarded
 * @param passedTests test cases that printed "Passed"
 * @param failedTests test cases that printed a failure
 * @param compilation the javac run, or null when compilation was not started
 * @param execution   the tester run, or null when the tester was not started
 * @param message     an explanation for the marker
 */
public record QuestionGradingResult(
        String questionId,
        Status status,
        double score,
        int passedTests,
        int failedTests,
        ProcessResult compilation,
        ProcessResult execution,
        String message) {

    /** How grading of a question ended. */
    public enum Status {
        GRADED,
        COMPILATION_ERROR,
        COMPILATION_TIMEOUT,
        EXECUTION_TIMEOUT,
        OUTPUT_LIMIT_EXCEEDED,
        RUNTIME_ERROR,
        INVALID_SCORE,
        MISSING_FILES,
        GRADING_ERROR
    }

    /** A question that earns no marks, with the reason. */
    static QuestionGradingResult zero(String questionId, Status status, ProcessResult compilation,
            ProcessResult execution, String message) {
        return new QuestionGradingResult(questionId, status, 0, 0, 0, compilation, execution, message);
    }
}
