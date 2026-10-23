package grading.tester;

import grading.assessment.AssessmentDefinition;
import grading.submission.SubmissionFinding;
import grading.submission.ValidationResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The validated tester folder: the tester found for each question and any problems with them. */
public record TesterValidationResult(
        Path source,
        Map<String, Path> testerFiles,
        List<SubmissionFinding> findings) implements ValidationResult {

    public TesterValidationResult {
        Objects.requireNonNull(source, "source");
        testerFiles = Map.copyOf(testerFiles);
        findings = List.copyOf(findings);
    }

    public boolean isValid() {
        return !hasErrors() && testerFiles.keySet().equals(Set.copyOf(AssessmentDefinition.testerFiles()));
    }
}
