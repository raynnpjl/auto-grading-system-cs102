package grading.execution;

// Timed-out executions may retain credit for completed one-mark tests; other failures earn zero.
// process results are null when that stage was not started
public record QuestionGradingResult(
        String questionId,
        Status status,
        double score,
        int passedTests,
        int failedTests,
        ProcessResult compilation,
        ProcessResult execution,
        String message) {

    public enum Status {
        GRADED,
        COMPILATION_ERROR,
        COMPILATION_TIMEOUT,
        EXECUTION_TIMEOUT,
        OUTPUT_LIMIT_EXCEEDED,
        RUNTIME_ERROR,
        INVALID_SCORE
    }
}
