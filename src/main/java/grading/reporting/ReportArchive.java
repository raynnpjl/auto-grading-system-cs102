package grading.reporting;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Saves an attempt's report folder as a single ZIP file for the marker to keep or share. */
public final class ReportArchive {

    private ReportArchive() {
    }

    /**
     * Writes every file of {@code attemptFolder} into {@code zipFile}, under a folder with the
     * attempt's name. The ZIP is completed in a temporary file first, so a failed save never leaves
     * a half-written archive behind.
     */
    public static void save(Path attemptFolder, Path zipFile) throws IOException {
        if (!Files.isDirectory(attemptFolder)) {
            throw new IOException("The reports folder " + attemptFolder + " no longer exists. Grade again to "
                    + "produce new reports.");
        }
        Path parent = zipFile.toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(parent, ".reports-", ".zip.tmp");
        try {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(attemptFolder)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            try (OutputStream output = Files.newOutputStream(temporary);
                    ZipOutputStream zip = new ZipOutputStream(output)) {
                String root = attemptFolder.getFileName().toString();
                for (Path file : files) {
                    String entry = root + "/" + attemptFolder.relativize(file).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entry));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            Files.move(temporary, zipFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
