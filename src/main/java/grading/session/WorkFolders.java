package grading.session;

import grading.common.FileTrees;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The temporary folder each session normalizes submissions into, and the sweep that removes the folders of
 * sessions that crashed or were force-quit before they could clean up after themselves.
 */
public final class WorkFolders {

    public static final String PREFIX = "auto-grader-";
    /** Folders untouched for this long are assumed to belong to a session that has ended. */
    public static final Duration ABANDONED_AFTER = Duration.ofHours(12);

    private static final Logger LOG = Logger.getLogger(WorkFolders.class.getName());

    private WorkFolders() {
    }

    /** Creates a new, empty work folder for this session directly inside {@code temporaryRoot}. */
    public static Path create(Path temporaryRoot) throws IOException {
        return Files.createTempDirectory(temporaryRoot, PREFIX);
    }

    /**
     * Deletes the work folders of ended sessions: direct children of {@code temporaryRoot} named with
     * {@link #PREFIX} that are real directories (never links) and have not changed for {@link #ABANDONED_AFTER}.
     * A folder that cannot be read or deleted is logged and skipped.
     *
     * @return how many folders were deleted
     */
    public static int removeAbandoned(Path temporaryRoot, Instant now) {
        Instant cutoff = now.minus(ABANDONED_AFTER);
        int removed = 0;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(temporaryRoot, PREFIX + "*")) {
            for (Path child : children) {
                if (isAbandoned(child, cutoff)) {
                    removed += delete(child);
                }
            }
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Could not look for abandoned work folders in " + temporaryRoot, exception);
        }
        return removed;
    }

    private static boolean isAbandoned(Path folder, Instant cutoff) {
        try {
            return Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)
                    && Files.getLastModifiedTime(folder, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff);
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Could not check " + folder, exception);
            return false;
        }
    }

    private static int delete(Path folder) {
        try {
            FileTrees.delete(folder);
            return 1;
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Could not delete the abandoned work folder " + folder, exception);
            return 0;
        }
    }
}
