package grading.submission;

import java.util.Objects;

public record SubmissionFinding(
        FindingSeverity severity,
        String code,
        String message,
        boolean corrected) {

    public SubmissionFinding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
