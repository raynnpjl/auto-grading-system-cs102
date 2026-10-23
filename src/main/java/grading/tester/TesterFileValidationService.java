package grading.tester;

import grading.assessment.AssessmentDefinition;
import grading.common.Messages;
import grading.config.GradingSettings;

import grading.submission.FindingSeverity;
import grading.submission.SubmissionFinding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Validates the supplied assessment tester folder without modifying it. */
public final class TesterFileValidationService {

    private static final Pattern MAIN_METHOD_PATTERN = Pattern.compile(
            "\\bstatic\\s+void\\s+main\\s*\\(");

    private final long maxTesterFileBytes;

    public TesterFileValidationService() {
        this(GradingSettings.current());
    }

    public TesterFileValidationService(GradingSettings settings) {
        this.maxTesterFileBytes = settings.maxTesterFileBytes();
    }

    public TesterValidationResult validate(Path folder) throws IOException {
        if (folder == null || !Files.isDirectory(folder) || !Files.isReadable(folder)) {
            throw new IOException("Tester folder does not exist or is not readable");
        }

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(folder)) {
            javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }

        List<SubmissionFinding> findings = new ArrayList<>();
        Map<String, Path> validatedFiles = new LinkedHashMap<>();
        for (String requiredFile : AssessmentDefinition.testerFiles()) {
            List<Path> matches = javaFiles.stream()
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(requiredFile))
                    .toList();
            if (matches.isEmpty()) {
                findings.add(finding(
                        FindingSeverity.ERROR,
                        "MISSING_TESTER_FILE",
                        "Required tester file " + requiredFile + " is missing."));
                continue;
            }
            if (matches.size() > 1) {
                findings.add(finding(
                        FindingSeverity.ERROR,
                        "DUPLICATE_TESTER_FILE",
                        "Found multiple copies of " + requiredFile + "."));
                continue;
            }

            Path testerFile = matches.getFirst();
            if (!testerFile.getFileName().toString().equals(requiredFile)) {
                findings.add(finding(
                        FindingSeverity.ERROR,
                        "WRONG_TESTER_FILENAME_CASE",
                        testerFile.getFileName() + " must be named exactly " + requiredFile + "."));
            }
            inspectTesterSource(testerFile, requiredFile, findings);
            validatedFiles.put(requiredFile, testerFile);
        }

        long extraFiles = javaFiles.stream()
                .filter(path -> AssessmentDefinition.testerFiles().stream().noneMatch(
                        required -> required.equalsIgnoreCase(path.getFileName().toString())))
                .count();
        if (extraFiles > 0) {
            findings.add(finding(
                    FindingSeverity.INFO,
                    "EXTRA_TESTER_FILES",
                    "Found " + extraFiles + " additional Java tester file(s)."));
        }

        if (findings.isEmpty()) {
            findings.add(finding(
                    FindingSeverity.INFO,
                    "TESTER_FILES_VALID",
                    "All required tester files are present and readable."));
        }
        return new TesterValidationResult(folder, validatedFiles, findings);
    }

    private void inspectTesterSource(
            Path testerFile,
            String requiredFile,
            List<SubmissionFinding> findings) {
        try {
            if (Files.size(testerFile) > maxTesterFileBytes) {
                findings.add(finding(
                        FindingSeverity.ERROR,
                        "TESTER_FILE_TOO_LARGE",
                        requiredFile + " is too large to inspect safely."));
                return;
            }

            String source = Files.readString(testerFile, StandardCharsets.UTF_8);
            String className = requiredFile.substring(0, requiredFile.length() - ".java".length());
            Pattern classPattern = Pattern.compile("\\bpublic\\s+class\\s+" + Pattern.quote(className) + "\\b");
            if (!classPattern.matcher(source).find()) {
                findings.add(finding(
                        FindingSeverity.ERROR,
                        "WRONG_TESTER_CLASS",
                        requiredFile + " does not declare public class " + className + "."));
            }
            if (!MAIN_METHOD_PATTERN.matcher(source).find()) {
                findings.add(finding(
                        FindingSeverity.ERROR,
                        "MISSING_TESTER_MAIN",
                        requiredFile + " does not contain a static main method."));
            }
        } catch (IOException exception) {
            findings.add(finding(
                    FindingSeverity.ERROR,
                    "TESTER_FILE_UNREADABLE",
                    "Could not inspect " + requiredFile + ": " + Messages.describe(exception)));
        }
    }

    private SubmissionFinding finding(FindingSeverity severity, String code, String message) {
        return new SubmissionFinding(severity, code, message, false);
    }

}
