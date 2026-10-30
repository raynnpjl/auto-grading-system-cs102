package grading.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkFoldersTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @TempDir
    Path root;

    @Test
    void removesOldPrefixedFoldersOnly() throws IOException {
        Path old = folder("auto-grader-old", Duration.ofHours(13));
        Path recent = folder("auto-grader-new", Duration.ofHours(1));
        Path unrelated = folder("other-old", Duration.ofHours(13));

        assertEquals(1, WorkFolders.removeAbandoned(root, NOW));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(unrelated));
    }

    @Test
    void doesNotFollowSymbolicLinks() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Path kept = Files.writeString(target.resolve("keep.txt"), "keep");
        Path link;
        try {
            link = Files.createSymbolicLink(root.resolve("auto-grader-link"), target);
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.abort("This file system cannot create symbolic links.");
            return;
        }
        Files.setLastModifiedTime(target, FileTime.from(NOW.minus(Duration.ofHours(13))));

        WorkFolders.removeAbandoned(root, NOW);
        assertTrue(Files.exists(kept));
        assertTrue(Files.isSymbolicLink(link));
    }

    private Path folder(String name, Duration age) throws IOException {
        Path folder = Files.createDirectory(root.resolve(name));
        Files.setLastModifiedTime(folder, FileTime.from(NOW.minus(age)));
        return folder;
    }
}
