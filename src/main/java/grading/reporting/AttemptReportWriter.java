package grading.reporting;

import grading.common.Messages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Writes the reports of a grading attempt as PDF files into a new folder named after the attempt,
 * so a later attempt never overwrites an earlier one: {@code summary.pdf} and one file per student
 * under {@code students/}. Writing never throws: a report that cannot be laid out as a PDF is saved
 * as plain text instead, and a report that cannot be written at all is listed in the summary.
 */
public final class AttemptReportWriter {

    private static final DateTimeFormatter ATTEMPT_ID = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
    private static final String SUMMARY_NAME = "summary";
    private static final String PDF = ".pdf";
    private static final String TEXT = ".txt";
    private static final String STUDENTS_FOLDER = "students";
    private static final String UNSAFE_FILE_NAME_CHARACTERS = "[^A-Za-z0-9._-]";

    private final Path reportsRoot;

    public AttemptReportWriter(Path reportsRoot) {
        this.reportsRoot = reportsRoot;
    }

    /**
     * The attempt's folder and any problems; the folder is empty only when not even the attempt folder
     * could be created.
     */
    public record Written(Optional<Path> folder, String attemptId, List<String> problems) {

        public Written {
            problems = List.copyOf(problems);
        }
    }

    public Written write(GradingAttempt attempt) {
        String baseId = attempt.startedAt().format(ATTEMPT_ID);
        Path folder;
        String attemptId;
        try {
            Files.createDirectories(reportsRoot);
            folder = createUnique(baseId);
            attemptId = folder.getFileName().toString().substring("attempt-".length());
        } catch (IOException | RuntimeException exception) {
            return new Written(Optional.empty(), baseId, List.of("The reports folder " + reportsRoot.toAbsolutePath()
                    + " could not be created: " + Messages.describe(exception)));
        }

        List<String> problems = new ArrayList<>();
        Map<StudentOutcome, String> reportFiles = new IdentityHashMap<>();
        Set<String> usedNames = new HashSet<>();
        Path students = folder.resolve(STUDENTS_FOLDER);
        for (StudentOutcome student : attempt.students()) {
            String baseName = uniqueName(student.username(), usedNames);
            try {
                Files.createDirectories(students);
                String report = StudentReportFormatter.format(student, attempt, attemptId);
                reportFiles.put(student, save(report, students, baseName,
                        "Grading report " + student.username() + ", attempt " + attemptId, problems));
            } catch (IOException | RuntimeException exception) {
                problems.add("The report for " + student.username() + " could not be written: "
                        + Messages.describe(exception));
            }
        }
        try {
            String summary = AttemptSummaryFormatter.format(attempt, attemptId, reportFiles, problems);
            save(summary, folder, SUMMARY_NAME, "Grading attempt " + attemptId, problems);
        } catch (IOException | RuntimeException exception) {
            problems.add("The attempt summary could not be written: " + Messages.describe(exception));
        }
        return new Written(Optional.of(folder), attemptId, problems);
    }

    /** The summary of an attempt written by this class, as found in its folder. */
    public static Path summaryOf(Path attemptFolder) {
        Path pdf = attemptFolder.resolve(SUMMARY_NAME + PDF);
        return Files.exists(pdf) ? pdf : attemptFolder.resolve(SUMMARY_NAME + TEXT);
    }

    /**
     * A student's report in an attempt folder written by this class: the PDF, or the text file saved
     * when the PDF could not be laid out. Empty when there is none or the name would leave students/.
     */
    public static Optional<Path> studentReportOf(Path attemptFolder, String username) {
        String base = username.replaceAll(UNSAFE_FILE_NAME_CHARACTERS, "_");
        Path students = attemptFolder.resolve(STUDENTS_FOLDER).normalize();
        for (String extension : List.of(PDF, TEXT)) {
            Path candidate = students.resolve(base + extension).normalize();
            if (candidate.startsWith(students) && Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // Saves the report as a PDF, or as plain text when it cannot be laid out; returns the file name.
    private static String save(String report, Path folder, String baseName, String title, List<String> problems)
            throws IOException {
        try {
            new PdfReportRenderer().render(report, folder.resolve(baseName + PDF), title);
            return baseName + PDF;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(folder.resolve(baseName + PDF));
            Files.writeString(folder.resolve(baseName + TEXT), report, StandardCharsets.UTF_8);
            problems.add(title + " could not be laid out as a PDF (" + Messages.describe(exception)
                    + "), so it was saved as " + baseName + TEXT + ".");
            return baseName + TEXT;
        }
    }

    // Two attempts started in the same second get attempt-<time>, attempt-<time>-2, and so on.
    private Path createUnique(String baseId) throws IOException {
        for (int suffix = 1; ; suffix++) {
            Path candidate = reportsRoot.resolve("attempt-" + baseId + (suffix == 1 ? "" : "-" + suffix));
            try {
                return Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException exception) {
                // taken by an earlier attempt; try the next suffix
            }
        }
    }

    private static String uniqueName(String username, Set<String> usedNames) {
        String base = username.replaceAll(UNSAFE_FILE_NAME_CHARACTERS, "_");
        String name = base;
        for (int suffix = 2; !usedNames.add(name.toLowerCase(Locale.ROOT)); suffix++) {
            name = base + "-" + suffix;
        }
        return name;
    }
}
