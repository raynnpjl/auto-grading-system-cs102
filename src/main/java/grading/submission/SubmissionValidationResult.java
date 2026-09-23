package grading.submission;

import grading.assessment.AssessmentDefinition;
import grading.assessment.QuestionFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SubmissionValidationResult(
        Path source,
        String username,
        Path normalizedRoot,
        Map<String, QuestionFiles> questionFiles,
        List<SubmissionFinding> findings) {

    public SubmissionValidationResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(username, "username");
        questionFiles = Map.copyOf(questionFiles);
        findings = List.copyOf(findings);
    }

    public boolean hasErrors() {
        return findings.stream().anyMatch(finding -> finding.severity() == FindingSeverity.ERROR);
    }

    public long correctedCount() {
        return findings.stream().filter(SubmissionFinding::corrected).count();
    }

    public boolean isReadyForGrading() {
        return !hasErrors() && questionFiles.size() == AssessmentDefinition.QUESTIONS.size()
                && AssessmentDefinition.QUESTIONS.stream().allMatch(question ->
                        questionFiles.containsKey(question.id()) && questionFiles.get(question.id()).matches(question));
    }
}
