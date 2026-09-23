package grading.execution;

import java.time.Duration;

public record ProcessResult(
        Status status, int exitCode, String stdout, String stderr, Duration duration) {

    public enum Status {
        EXITED, TIMED_OUT, OUTPUT_LIMIT_EXCEEDED
    }

    public boolean succeeded() {
        return status == Status.EXITED && exitCode == 0;
    }
}
