package web;

import grading.common.FileTrees;
import grading.config.GradingSettings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Turns an uploaded ZIP (the page zips the folder the user chose) back into a folder on disk. */
final class UploadedFolder {

    private UploadedFolder() {
    }

    /**
     * Extracts the upload into the destination folder.
     *
     * @return the folder that holds the uploaded content, or empty when the upload was empty or not a ZIP
     * @throws IOException if an entry would be written outside the destination folder, or the ZIP is
     *     larger or has more entries than the submission limits in grading.properties allow
     */
    static Optional<Path> extract(byte[] zipBytes, Path destination, GradingSettings settings) throws IOException {
        if (unzip(zipBytes, destination, settings) == 0) {
            return Optional.empty();
        }
        return Optional.of(unwrapSingleFolder(destination));
    }

    // Returns how many entries the ZIP held; a body that is not a ZIP holds none.
    private static int unzip(byte[] zipBytes, Path destination, GradingSettings settings) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        int entries = 0;
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > settings.maxZipEntries()) {
                    throw new IOException("ZIP contains too many entries");
                }
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Unsafe path in the ZIP: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    totalBytes = FileTrees.copyZipEntry(zip, target, totalBytes, settings.maxZipBytes());
                }
                zip.closeEntry();
            }
        }
        return entries;
    }

    // The page zips the chosen folder itself, so a single top-level folder is the real root.
    private static Path unwrapSingleFolder(Path folder) throws IOException {
        try (Stream<Path> stream = Files.list(folder)) {
            List<Path> children = stream.toList();
            if (children.size() == 1 && Files.isDirectory(children.getFirst())) {
                return children.getFirst();
            }
        }
        return folder;
    }
}
