package grading.reporting;

import grading.scoring.ScoreSummary;
import grading.scoring.StudentScore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fills per-student totals into the score sheet the marker uploaded. The uploaded file is never
 * changed; the filled sheet is new content for the marker to download.
 */
public final class ScoreSheetUpdater {

    /** Matches each graded student to a sheet row by username, and lists who is on only one side. */
    public ScoreSheetPreview preview(ScoreSheet sheet, ScoreSummary scores) {
        List<ScoreSheetPreview.Entry> entries = new ArrayList<>();
        List<String> noSubmission = new ArrayList<>();
        Set<String> matched = new HashSet<>();
        for (int row = 0; row < sheet.rowCount(); row++) {
            String username = sheet.username(row);
            if (username.isBlank()) {
                continue;
            }
            Optional<StudentScore> score = scores.find(username);
            if (score.isPresent()) {
                matched.add(ScoreSummary.normalize(username));
                entries.add(new ScoreSheetPreview.Entry(row, username, sheet.numerator(row).strip(),
                        formatScore(score.get().total())));
            } else {
                noSubmission.add(username);
            }
        }
        List<String> notInSheet = scores.students().stream()
                .map(StudentScore::username)
                .filter(username -> !matched.contains(ScoreSummary.normalize(username)))
                .toList();
        return new ScoreSheetPreview(sheet, entries, notInSheet, noSubmission);
    }

    /** Totals are written as the grading report shows them, e.g. 12.0 or 8.5. */
    static String formatScore(double score) {
        return BigDecimal.valueOf(score).toPlainString();
    }

    /** The sheet's content with each matched row's numerator replaced; every other byte is kept. */
    public byte[] filledContent(ScoreSheetPreview preview) {
        Map<Integer, String> numerators = new HashMap<>();
        preview.entries().forEach(entry -> numerators.put(entry.row(), entry.newScore()));
        return preview.sheet().withNumerators(numerators);
    }
}
