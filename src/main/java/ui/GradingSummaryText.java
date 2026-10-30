package ui;

import grading.progress.GradingStatus;
import grading.progress.ProgressSnapshot;
import grading.progress.StudentProgress;
import grading.scoring.Marks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

/** Formats the end of a grading run for the Activity tab: each student's marks and where the reports are. */
final class GradingSummaryText {

    private static final String SUMMARY_REPORT = "summary.pdf";

    private GradingSummaryText() {
    }

    static String format(ProgressSnapshot snapshot, Optional<Path> reportsFolder) {
        StringBuilder text = new StringBuilder("Grading results\n===============\n");
        for (StudentProgress student : snapshot.students()) {
            text.append('[').append(student.username()).append("] ");
            if (student.status() == GradingStatus.SKIPPED) {
                text.append("Not graded (confirm identity first)\n");
                continue;
            }
            String marks = student.scores().entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + Marks.format(entry.getValue()))
                    .collect(Collectors.joining("   "));
            text.append(marks).append("   Total: ").append(Marks.format(student.total()))
                    .append(" — ").append(student.status().label()).append('\n');
            student.flags().forEach(flag -> text.append("  Similar answer: ").append(flag).append('\n'));
        }
        text.append("\nReports\n=======\n");
        if (reportsFolder.isEmpty()) {
            text.append("No reports were written for this run.\n");
        } else {
            Path folder = reportsFolder.get().toAbsolutePath();
            Path summary = folder.resolve(SUMMARY_REPORT);
            text.append("Folder: ").append(folder).append('\n');
            if (Files.exists(summary)) {
                text.append("Class summary: ").append(summary).append('\n');
            }
            text.append("Select a student and choose Open PDF report to see their report.\n");
        }
        return text.toString();
    }
}
