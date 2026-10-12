package grading.submission;

import grading.assessment.AssessmentDefinition;
import grading.assessment.QuestionFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One student's prepared submission: who it belongs to, its normalized copy, the files found for
 * each question, and every anomaly found or corrected along the way.
 *
 * @param source          the original ZIP or folder
 * @param username        the student it was resolved to
 * @param normalizedRoot  the normalized copy, or null when no source file was found
 * @param questionFiles   the files found for each question id
 * @param findings        what validation found
 * @param author          the name and email declared in the Java headers
 * @param corrections     every change made automatically in the normalized copy
 * @param originalSources each source file name mapped to an unmodified copy of it, as submitted
 */
public record SubmissionValidationResult(
        Path source,
        String username,
        Path normalizedRoot,
        Map<String, QuestionFiles> questionFiles,
        List<SubmissionFinding> findings,
        HeaderIdentity author,
        List<FileCorrection> corrections,
        Map<String, Path> originalSources) implements ValidationResult {

    public SubmissionValidationResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(username, "username");
        questionFiles = Map.copyOf(questionFiles);
        findings = List.copyOf(findings);
        author = author == null ? HeaderIdentity.unknown() : author;
        corrections = List.copyOf(corrections);
        originalSources = Map.copyOf(originalSources);
    }

    /** A result without header details, corrections or original copies. */
    public SubmissionValidationResult(Path source, String username, Path normalizedRoot,
            Map<String, QuestionFiles> questionFiles, List<SubmissionFinding> findings) {
        this(source, username, normalizedRoot, questionFiles, findings, HeaderIdentity.unknown(), List.of(),
                Map.of());
    }

    public long correctedCount() {
        return findings.stream().filter(SubmissionFinding::corrected).count();
    }

    /** A submission can be graded unless its owner is unconfirmed or it could not be read at all. */
    public boolean isReadyForGrading() {
        return findings.stream().noneMatch(SubmissionFinding::blocksGrading);
    }

    /** The question's files from this submission; empty when none were found. */
    public QuestionFiles files(AssessmentDefinition.Question question) {
        return questionFiles.getOrDefault(question.id(), new QuestionFiles(List.of(), List.of(), List.of()));
    }

    /** Question ids that will score zero because some of their files are missing. */
    public List<String> missingQuestions() {
        return AssessmentDefinition.QUESTIONS.stream()
                .filter(question -> !files(question).missingFiles(question).isEmpty())
                .map(AssessmentDefinition.Question::id)
                .toList();
    }
}
