package grading.submission;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts a submission ZIP without trusting it: entries that would land outside the destination
 * are skipped and reported, and archives that expand too far are rejected.
 */
final class ZipExtractor {

    private static final int BUFFER_BYTES = 8192;

    private final long maxUncompressedBytes;
    private final int maxEntries;

    ZipExtractor(long maxUncompressedBytes, int maxEntries) {
        this.maxUncompressedBytes = maxUncompressedBytes;
        this.maxEntries = maxEntries;
    }

    void extract(Path zipFile, Path destination, List<SubmissionFinding> findings) throws IOException {
        long totalBytes = 0;
        int entries = 0;
        Path normalizedDestination = destination.toAbsolutePath().normalize();

        try (InputStream input = new BufferedInputStream(Files.newInputStream(zipFile));
                ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_BYTES];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > maxEntries) {
                    throw new IOException("ZIP contains too many entries");
                }

                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.toAbsolutePath().normalize().startsWith(normalizedDestination)) {
                    findings.add(new SubmissionFinding(
                            FindingSeverity.ERROR,
                            "UNSAFE_ZIP_ENTRY",
                            "Skipped unsafe ZIP entry " + entry.getName() + ".",
                            false));
                    zip.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    totalBytes = copyEntry(zip, target, buffer, totalBytes);
                }
                zip.closeEntry();
            }
        }
    }

    // Writes the current entry to target and returns the bytes extracted so far, stopping a ZIP bomb.
    private long copyEntry(ZipInputStream zip, Path target, byte[] buffer, long totalBytes) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        long total = totalBytes;
        try (var output = Files.newOutputStream(target)) {
            int read;
            while ((read = zip.read(buffer)) != -1) {
                total += read;
                if (total > maxUncompressedBytes) {
                    throw new IOException("ZIP expands beyond the safe size limit");
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }
}
