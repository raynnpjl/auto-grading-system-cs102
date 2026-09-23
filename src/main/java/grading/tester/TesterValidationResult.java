package grading.tester;

import grading.assessment.AssessmentDefinition;
import grading.submission.FindingSeverity;
import grading.submission.SubmissionFinding;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TesterValidationResult(
        Path source,
        Map<String, Path> testerFiles,
        List<SubmissionFinding> findings) {

    public TesterValidationResult {
        Objects.requireNonNull(source, "source");
        testerFiles = Map.copyOf(testerFiles);
        findings = List.copyOf(findings);
    }

    public boolean hasErrors() {
        return findings.stream().anyMatch(finding -> finding.severity() == FindingSeverity.ERROR);
    }

    public boolean isValid() {
        return !hasErrors() && testerFiles.keySet().equals(java.util.Set.copyOf(AssessmentDefinition.testerFiles()));
    }
}
