package grading.common;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipInputStream;

/** Operations on whole folder trees. */
public final class FileTrees {

    private static final int BUFFER_BYTES = 8192;

    private FileTrees() {
    }

    /**
     * Writes the current entry of a ZIP being read to target and returns the bytes extracted from the
     * ZIP so far, stopping as soon as they pass maxBytes so a ZIP bomb cannot fill the disk.
     */
    public static long copyZipEntry(ZipInputStream zip, Path target, long extractedSoFar, long maxBytes)
            throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        long total = extractedSoFar;
        byte[] buffer = new byte[BUFFER_BYTES];
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = zip.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("ZIP expands beyond the safe size limit");
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    /** Deletes a file or folder and everything inside it; does nothing when it does not exist. */
    public static void delete(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
