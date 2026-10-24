package grading.reporting;

import grading.scoring.ScoreSummary;
import grading.scoring.StudentScore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Writes per-student totals into the score sheet the marker imported. The file is backed up first
 * and replaced in one step, so a failed write never leaves a half-written score sheet.
 */
public final class ScoreSheetUpdater {

    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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

    public Result write(ScoreSheetPreview preview) throws IOException {
        ScoreSheet sheet = preview.sheet();
        Path path = sheet.path();
        if (preview.entries().isEmpty()) {
            throw new ScoreSheetException("No graded student matches a username in " + path.getFileName()
                    + ". Check that the correct score sheet was imported.");
        }
        if (!sheet.matchesFileOnDisk()) {
            throw new ScoreSheetException(path.getFileName() + " changed after it was imported. "
                    + "Import the score sheet again so no other edits are overwritten.");
        }
        Map<Integer, String> numerators = new HashMap<>();
        preview.entries().forEach(entry -> numerators.put(entry.row(), entry.newScore()));
        byte[] content = sheet.withNumerators(numerators);

        Path backup = createBackup(path);
        Path temporary = Files.createTempFile(path.getParent(), "." + path.getFileName() + "-", ".tmp");
        try {
            Files.write(temporary, content);
            copyPermissions(path, temporary);
            replace(temporary, path);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new Result(path, backup, preview.entries().size(), ScoreSheet.load(path));
    }

    /** Totals are written as the grading report shows them, e.g. 12.0 or 8.5. */
    static String formatScore(double score) {
        return BigDecimal.valueOf(score).toPlainString();
    }

    private static Path createBackup(Path path) throws IOException {
        String name = path.getFileName().toString();
        int extension = name.toLowerCase(Locale.ROOT).endsWith(".csv") ? name.length() - 4 : name.length();
        String stem = name.substring(0, extension) + ".backup-" + LocalDateTime.now().format(BACKUP_TIMESTAMP);
        String suffix = name.substring(extension);
        Path backup = path.resolveSibling(stem + suffix);
        for (int attempt = 2; Files.exists(backup); attempt++) {
            backup = path.resolveSibling(stem + "-" + attempt + suffix);
        }
        try {
            Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (FileSystemException exception) {
            throw new ScoreSheetException("Could not save a backup next to " + name
                    + ". Make sure the folder is writable, then try again. No scores were written.", exception);
        }
        return backup;
    }

    private static void copyPermissions(Path source, Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(source));
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX file systems (e.g. Windows) keep the folder's default permissions
        }
    }

    private static void replace(Path temporary, Path path) throws ScoreSheetException {
        try {
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ScoreSheetException("Could not save " + path.getFileName() + ". If it is open in Excel "
                    + "or another program, close it and try again. The original file was not changed.", exception);
        }
    }

    
    public byte[] filledContent(ScoreSheetPreview preview) {
        Map<Integer, String> numerators = new HashMap<>();
        preview.entries().forEach(entry -> numerators.put(entry.row(), entry.newScore()));
        return preview.sheet().withNumerators(numerators);
    }

    /** The saved file, its backup, and the sheet as re-read after writing. */
    public record Result(Path sheet, Path backup, int scoresWritten, ScoreSheet updatedSheet) {
    }
}
