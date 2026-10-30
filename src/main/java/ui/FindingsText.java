package ui;

import grading.assessment.AssessmentDefinition;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterValidationResult;
import java.util.List;

/** Formats what importing the tester folder and the student submissions found, for the Activity tab. */
final class FindingsText {

    private FindingsText() {
    }

    static String testers(TesterValidationResult result) {
        StringBuilder text = new StringBuilder()
                .append("Tester-file validation complete\n")
                .append("===============================\n")
                .append("Folder: ").append(result.source().toAbsolutePath()).append('\n')
                .append("Required files found: ").append(result.testerFiles().size())
                .append('/').append(AssessmentDefinition.testerFiles().size()).append("\n\n");
        if (result.hasErrors()) {
            text.append("MARKER ACTION REQUIRED\n")
                    .append("Follow the Next step shown under each ERROR, then import Tester-Files again.\n\n");
        }
        appendFindings(text, result.findings());
        return text.toString();
    }

    static String submissions(List<SubmissionValidationResult> results) {
        long ready = results.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        long skipped = results.size() - ready;
        long corrections = results.stream().mapToLong(SubmissionValidationResult::correctedCount).sum();

        StringBuilder text = new StringBuilder()
                .append("Submission validation complete\n")
                .append("==============================\n")
                .append("Processed: ").append(results.size()).append('\n')
                .append("Ready to grade: ").append(ready).append('\n')
                .append("Needs attention (skipped by grading): ").append(skipped).append('\n')
                .append("Automatic corrections: ").append(corrections).append("\n\n");
        if (skipped > 0) {
            text.append("MARKER ACTION REQUIRED\n")
                    .append("Students marked NEEDS ATTENTION are skipped until their identity is confirmed. ")
                    .append("Select Resolve issues, or grade the other students now.\n\n");
        }
        for (SubmissionValidationResult result : results) {
            text.append('[').append(result.username()).append("] ").append(readiness(result))
                    .append("\nSource: ").append(result.source()).append('\n');
            if (result.findings().isEmpty()) {
                text.append("  OK - structure, required files, and headers are valid.\n");
            } else {
                appendFindings(text, result.findings());
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** Whether the submission will be graded, and which questions will score 0 for missing files. */
    static String readiness(SubmissionValidationResult result) {
        if (!result.isReadyForGrading()) {
            return "NEEDS ATTENTION - skipped by grading";
        }
        List<String> missing = missingQuestions(result);
        if (!missing.isEmpty()) {
            return "READY - " + String.join(", ", missing) + " will score 0 (missing files)";
        }
        return "READY";
    }

    /** The questions whose source files are missing from the submission. */
    static List<String> missingQuestions(SubmissionValidationResult result) {
        return AssessmentDefinition.QUESTIONS.stream()
                .filter(question -> !result.files(question).missingFiles(question).isEmpty())
                .map(AssessmentDefinition.Question::id)
                .toList();
    }

    private static void appendFindings(StringBuilder text, List<SubmissionFinding> findings) {
        for (SubmissionFinding finding : findings) {
            text.append("  ")
                    .append(finding.severity())
                    .append(finding.corrected() ? " (corrected)" : "")
                    .append(" - ")
                    .append(finding.message())
                    .append('\n');
            finding.nextStep().ifPresent(nextStep -> text
                    .append("    Next step for marker: ")
                    .append(nextStep)
                    .append('\n'));
        }
    }
}
