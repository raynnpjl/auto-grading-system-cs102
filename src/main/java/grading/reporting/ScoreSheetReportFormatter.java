package grading.reporting;

import grading.common.Messages;

/** Formats what happened to the imported score sheet for the console. */
public final class ScoreSheetReportFormatter {

    private ScoreSheetReportFormatter() {
    }

    /** Which sheet is imported and what the marker should do next; empty when none is imported. */
    public static String status(ScoreSheet sheet, boolean graded) {
        if (sheet == null) {
            return "";
        }
        String nextStep = graded
                ? "Select Write scores to fill it with this run's totals."
                : "Run grading, then select Write scores to fill it.";
        return "Score sheet: " + sheet.path() + " (" + sheet.rowCount() + " students). " + nextStep + "\n\n";
    }

    public static String importFailed(Throwable failure) {
        return "Score sheet not imported\n"
                + "========================\n"
                + Messages.describe(failure) + "\n"
                + "Write scores stays unavailable until a score sheet is imported.\n\n";
    }

    public static String noMatchingStudents(ScoreSheetPreview preview) {
        return "Scores not written\n==================\n"
                + "No graded student matches a username in the imported score sheet. "
                + "Check that the correct sheet was imported.\n\n" + preview.describe() + "\n";
    }

    public static String written(ScoreSheetPreview preview, ScoreSheetUpdater.Result result) {
        return "Scores written to score sheet\n"
                + "=============================\n"
                + preview.describe()
                + "Backup of the previous file: " + result.backup() + "\n\n";
    }

    public static String writeFailed(Throwable failure) {
        return "Scores not written\n==================\n" + Messages.describe(failure) + "\n\n";
    }
}
