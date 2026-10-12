package grading.reporting;

import grading.assessment.AssessmentDefinition;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionValidationResult;
import grading.submission.ValidationResult;
import grading.tester.TesterValidationResult;
import java.nio.file.Path;
import java.util.List;

/** Formats the outcome of importing the tester folder and the student submissions for the console. */
public final class ValidationReportFormatter {

    private ValidationReportFormatter() {
    }

    public static String submissions(List<SubmissionValidationResult> results, Path workspace) {
        long ready = results.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        long skipped = results.size() - ready;
        long incomplete = results.stream()
                .filter(result -> result.isReadyForGrading() && !result.missingQuestions().isEmpty())
                .count();
        long corrections = results.stream().mapToLong(SubmissionValidationResult::correctedCount).sum();

        StringBuilder report = new StringBuilder()
                .append("Submission validation complete\n")
                .append("==============================\n")
                .append("Processed: ").append(results.size()).append('\n')
                .append("Ready to grade: ").append(ready).append('\n')
                .append("  of which missing files (those questions score 0): ").append(incomplete).append('\n')
                .append("Needs attention (skipped by grading): ").append(skipped).append('\n')
                .append("Automatic corrections: ").append(corrections).append('\n')
                .append("Normalized workspace: ").append(workspace.toAbsolutePath()).append("\n\n");
        if (skipped > 0) {
            report.append("MARKER ACTION REQUIRED\n")
                    .append("Students marked NEEDS ATTENTION are skipped until their identity is confirmed. ")
                    .append("Select Resolve issues, or grade the other students now.\n\n");
        }
        for (SubmissionValidationResult result : results) {
            report.append('[').append(result.username()).append("] ")
                    .append(readiness(result))
                    .append("\nSource: ").append(result.source()).append('\n');
            if (result.findings().isEmpty()) {
                report.append("  OK - structure, required files, and headers are valid.\n");
            } else {
                appendFindings(report, result);
            }
            report.append('\n');
        }
        return report.toString();
    }

    public static String testers(TesterValidationResult result) {
        StringBuilder report = new StringBuilder()
                .append("Tester-file validation complete\n")
                .append("===============================\n")
                .append("Folder: ").append(result.source().toAbsolutePath()).append('\n')
                .append("Required files found: ").append(result.testerFiles().size())
                .append('/').append(AssessmentDefinition.testerFiles().size()).append("\n\n");
        if (result.hasErrors()) {
            report.append("MARKER ACTION REQUIRED\n")
                    .append("Follow the Next step shown under each ERROR, then import Tester-Files again.\n\n");
        }
        appendFindings(report, result);
        return report.toString();
    }

    /** Whether the submission will be graded, and which questions will score 0 for missing files. */
    public static String readiness(SubmissionValidationResult result) {
        if (!result.isReadyForGrading()) {
            return "NEEDS ATTENTION - skipped by grading";
        }
        if (!result.missingQuestions().isEmpty()) {
            return "READY - " + String.join(", ", result.missingQuestions()) + " will score 0 (missing files)";
        }
        return "READY";
    }

    private static void appendFindings(StringBuilder report, ValidationResult result) {
        for (SubmissionFinding finding : result.findings()) {
            report.append("  ")
                    .append(finding.severity())
                    .append(finding.corrected() ? " (corrected)" : "")
                    .append(" - ")
                    .append(finding.message())
                    .append('\n');
            finding.nextStep().ifPresent(nextStep -> report
                    .append("    Next step for marker: ")
                    .append(nextStep)
                    .append('\n'));
        }
    }
}
