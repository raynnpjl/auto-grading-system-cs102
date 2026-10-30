package ui;

import grading.common.Messages;
import grading.reporting.ScoreSheet;
import grading.reporting.ScoreSheetPreview;
import grading.reporting.ScoreSheetUpdater;
import grading.scoring.ScoreSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The imported LMS score sheet and saving a filled copy of it. The imported file is never changed. Dialogs
 * stay in the controller; this class gets the marker's answers through the functions it is given.
 */
final class ScoreSheetActions {

    /**
     * What to show after an action; a null field leaves that part of the window as it is.
     *
     * @param consoleText   the score sheet section of the Activity tab
     * @param statusText    the status badge at the top of the window
     * @param consoleStatus the short status beside the console buttons
     */
    record Result(String consoleText, String statusText, String consoleStatus) {
    }

    private final ScoreSheetUpdater updater = new ScoreSheetUpdater();
    private ScoreSheet sheet;
    // Why the last import failed; kept on screen until a sheet is imported.
    private String problem;

    Result importSheet(Path source, boolean hasResults) {
        try {
            sheet = ScoreSheet.load(source);
            problem = null;
            return new Result(status(hasResults), null, "Score sheet imported: " + sheet.rowCount() + " students");
        } catch (IOException exception) {
            sheet = null;
            problem = "Score sheet not imported\n"
                    + "========================\n"
                    + Messages.describe(exception) + "\n"
                    + "Save filled sheet stays unavailable until a score sheet is imported.\n\n";
            return new Result(problem, null, "Score sheet not imported");
        }
    }

    boolean hasSheet() {
        return sheet != null;
    }

    Path sheetPath() {
        return sheet.path();
    }

    /** Which sheet is imported and what to do next, or why the last import failed. */
    String status(boolean hasResults) {
        if (sheet == null) {
            return problem == null ? "" : problem;
        }
        String nextStep = hasResults
                ? "Select Save filled sheet to save a copy filled with this run's totals."
                : "Run grading, then select Save filled sheet.";
        return "Score sheet: " + sheet.path() + " (" + sheet.rowCount() + " students). " + nextStep + "\n\n";
    }

    /**
     * Saves a copy of the sheet with every matched student's total filled in.
     *
     * @param scores       the totals of the latest grading run
     * @param confirmation asks the marker to confirm the preview
     * @param target       asks where to save the filled copy; empty when the marker cancels
     * @return what to show, or empty when the marker cancelled
     */
    Optional<Result> saveFilled(ScoreSummary scores, Predicate<ScoreSheetPreview> confirmation,
            Supplier<Optional<Path>> target) {
        ScoreSheetPreview preview = updater.preview(sheet, scores);
        if (preview.entries().isEmpty()) {
            return Optional.of(new Result("Scores not saved\n================\n"
                    + "No graded student matches a username in the imported score sheet. "
                    + "Check that the correct sheet was imported.\n\n"
                    + ScoreSheetConfirmation.describe(preview) + "\n", null, null));
        }
        if (!confirmation.test(preview)) {
            return Optional.empty();
        }
        Optional<Path> chosen = target.get();
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        Path file = chosen.get().toAbsolutePath().normalize();
        if (file.equals(sheet.path().toAbsolutePath().normalize())) {
            return Optional.of(new Result(null, null,
                    "Choose a different file name; the original sheet is kept unchanged."));
        }
        return Optional.of(write(preview, file));
    }

    private Result write(ScoreSheetPreview preview, Path file) {
        try {
            Files.write(file, updater.filledContent(preview));
            return new Result("Filled score sheet saved\n"
                    + "========================\n"
                    + ScoreSheetConfirmation.describe(preview)
                    + "Saved to: " + file + "\nThe imported sheet was not changed.\n\n",
                    "Filled score sheet saved",
                    preview.entries().size() + " scores saved to " + file.getFileName());
        } catch (IOException exception) {
            return new Result("Scores not saved\n================\n" + Messages.describe(exception)
                    + "\nClose the file if it is open in Excel, then try again.\n\n",
                    "Score sheet not saved", "Score sheet not saved");
        }
    }
}
