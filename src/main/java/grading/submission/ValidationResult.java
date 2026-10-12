package grading.submission;

import java.util.List;

/** The outcome of validating an imported input: what was found and whether any of it is an error. */
public interface ValidationResult {

    List<SubmissionFinding> findings();

    default boolean hasErrors() {
        return findings().stream().anyMatch(finding -> finding.severity() == FindingSeverity.ERROR);
    }
}
