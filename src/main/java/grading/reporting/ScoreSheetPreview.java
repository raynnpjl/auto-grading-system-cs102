package grading.reporting;

import java.util.List;

// Generated with Claude Code (Claude Opus 5.5); reviewed and adapted by the team.
/** What writing scores into a score sheet would change, shown to the marker before anything is written. */
public record ScoreSheetPreview(
        ScoreSheet sheet,
        List<Entry> entries,
        List<String> notInSheet,
        List<String> noSubmission) {

    public ScoreSheetPreview {
        entries = List.copyOf(entries);
        notInSheet = List.copyOf(notInSheet);
        noSubmission = List.copyOf(noSubmission);
    }

    /** One sheet row that will receive a score; previousScore is blank when the cell was empty. */
    public record Entry(int row, String username, String previousScore, String newScore) {

        public boolean overwritesExistingScore() {
            return !previousScore.isBlank();
        }
    }

    public long overwrittenCount() {
        return entries.stream().filter(Entry::overwritesExistingScore).count();
    }

    public String describe() {
        StringBuilder text = new StringBuilder()
                .append("File: ").append(sheet.path()).append('\n')
                .append("Scores to write: ").append(entries.size()).append(" of ").append(sheet.rowCount())
                .append(" students in the sheet\n")
                .append("Existing scores that will be replaced: ").append(overwrittenCount()).append('\n');
        for (Entry entry : entries) {
            if (entry.overwritesExistingScore()) {
                text.append("  ").append(entry.username()).append(": ").append(entry.previousScore())
                        .append(" -> ").append(entry.newScore()).append('\n');
            }
        }
        appendList(text, "Graded students not in the sheet (not written)", notInSheet);
        appendList(text, "Sheet students with no graded submission (left unchanged)", noSubmission);
        return text.toString();
    }

    private static void appendList(StringBuilder text, String title, List<String> usernames) {
        text.append(title).append(": ").append(usernames.isEmpty() ? "none" : usernames.size()).append('\n');
        usernames.forEach(username -> text.append("  ").append(username).append('\n'));
    }
}
