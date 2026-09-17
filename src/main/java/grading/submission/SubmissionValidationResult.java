package grading.submission;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SubmissionValidationResult(
        Path source,
        String username,
        Path normalizedRoot,
        Map<String, Path> javaFiles,
        List<SubmissionFinding> findings) {

    public SubmissionValidationResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(username, "username");
        javaFiles = Map.copyOf(javaFiles);
        findings = List.copyOf(findings);
    }

    public boolean hasErrors() {
        return findings.stream().anyMatch(finding -> finding.severity() == FindingSeverity.ERROR);
    }

    public long correctedCount() {
        return findings.stream().filter(SubmissionFinding::corrected).count();
    }

    public boolean isReadyForGrading() {
        return !hasErrors() && javaFiles.size() == SubmissionPreparationService.requiredJavaFiles().size();
    }
}
