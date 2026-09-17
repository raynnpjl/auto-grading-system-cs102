package grading.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubmissionPreparationServiceTest {

    private static final String COMPLETE_HEADER = """
            // Name: Alice Example
            // Email: alice.2026@school.example
            """;

    @TempDir
    Path tempDirectory;

    private final SubmissionPreparationService service = new SubmissionPreparationService();

    @Test
    void acceptsAValidSubmissionFolder() throws IOException {
        Path batch = Files.createDirectory(tempDirectory.resolve("batch"));
        Path student = Files.createDirectory(batch.resolve("alice.2026"));
        createRequiredFiles(student, COMPLETE_HEADER);

        List<SubmissionValidationResult> results = service.prepareBatch(
                batch, tempDirectory.resolve("normalized"));

        assertEquals(1, results.size());
        SubmissionValidationResult result = results.getFirst();
        assertTrue(result.isReadyForGrading(), result.findings().toString());
        assertEquals("alice.2026", result.username());
        assertEquals(6, result.javaFiles().size());
        assertFalse(result.hasErrors());
    }

    @Test
    void repairsTemplateWrapperAndMissingHeadersInsideZip() throws IOException {
        Path zip = tempDirectory.resolve("bob.2026.zip");
        Map<String, String> entries = Map.of(
                "RenameToYourUsername/Q1/Q1a.java",
                javaSource("// Name: Bob Example\n// Email: bob.2026@school.example\n"),
                "RenameToYourUsername/Q1/Q1b.java", javaSource(""),
                "RenameToYourUsername/Q2/Q2a.java", javaSource(""),
                "RenameToYourUsername/Q2/Q2b.java", javaSource(""),
                "RenameToYourUsername/Q3/Q3.java", javaSource(""),
                "RenameToYourUsername/Q3/ShapeComparator.java", javaSource(""));
        createZip(zip, entries);

        SubmissionValidationResult result = service.prepareBatch(
                zip, tempDirectory.resolve("normalized")).getFirst();

        assertTrue(result.isReadyForGrading(), result.findings().toString());
        assertTrue(hasFinding(result, "WRONG_USERNAME_FOLDER", true));
        assertTrue(hasFinding(result, "MISSING_HEADER_EMAIL", true));
        String repaired = Files.readString(result.normalizedRoot().resolve("Q2/Q2a.java"));
        assertTrue(repaired.contains("Student name: Bob Example"));
        assertTrue(repaired.contains("Student email: bob.2026@school.example"));
    }

    @Test
    void movesFilesDumpedDirectlyIntoQuestionFolders() throws IOException {
        Path student = Files.createDirectory(tempDirectory.resolve("carol.2026"));
        for (String fileName : SubmissionPreparationService.requiredJavaFiles().keySet()) {
            Files.writeString(student.resolve(fileName), javaSource(
                    "// Name: Carol Example\n// Email: carol.2026@school.example\n"));
        }

        SubmissionValidationResult result = service.prepareBatch(
                student, tempDirectory.resolve("normalized")).getFirst();

        assertTrue(result.isReadyForGrading(), result.findings().toString());
        assertTrue(hasFinding(result, "MISSING_QUESTION_FOLDER", true));
        assertTrue(hasFinding(result, "JAVA_FILE_RELOCATED", true));
        assertTrue(Files.exists(result.normalizedRoot().resolve("Q1/Q1a.java")));
        assertTrue(Files.exists(result.normalizedRoot().resolve("Q3/Q3.java")));
    }

    @Test
    void malformedSubmissionDoesNotStopTheBatch() throws IOException {
        Path batch = Files.createDirectory(tempDirectory.resolve("batch"));
        Path malformed = Files.createDirectory(batch.resolve("broken.2026"));
        Files.createDirectories(malformed.resolve("Q1"));
        Files.writeString(malformed.resolve("Q1/Q1a.java"), javaSource(
                "// Name: Broken Student\n// Email: broken.2026@school.example\n"));

        Path valid = Files.createDirectory(batch.resolve("dana.2026"));
        createRequiredFiles(valid,
                "// Name: Dana Example\n// Email: dana.2026@school.example\n");

        List<SubmissionValidationResult> results = service.prepareBatch(
                batch, tempDirectory.resolve("normalized"));

        assertEquals(2, results.size());
        SubmissionValidationResult brokenResult = resultFor(results, "broken.2026");
        SubmissionValidationResult validResult = resultFor(results, "dana.2026");
        assertTrue(brokenResult.hasErrors());
        assertTrue(hasFinding(brokenResult, "MISSING_JAVA_FILE", false));
        assertTrue(validResult.isReadyForGrading(), validResult.findings().toString());
    }

    @Test
    void unsafeZipEntryIsReportedAndSkipped() throws IOException {
        Path zip = tempDirectory.resolve("erin.2026.zip");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("../outside.java", "class Outside {}");
        entries.put("erin.2026/Q1/Q1a.java", javaSource(
                "// Name: Erin Example\n// Email: erin.2026@school.example\n"));
        entries.put("erin.2026/Q1/Q1b.java", javaSource(
                "// Name: Erin Example\n// Email: erin.2026@school.example\n"));
        entries.put("erin.2026/Q2/Q2a.java", javaSource(
                "// Name: Erin Example\n// Email: erin.2026@school.example\n"));
        entries.put("erin.2026/Q2/Q2b.java", javaSource(
                "// Name: Erin Example\n// Email: erin.2026@school.example\n"));
        entries.put("erin.2026/Q3/Q3.java", javaSource(
                "// Name: Erin Example\n// Email: erin.2026@school.example\n"));
        entries.put("erin.2026/Q3/ShapeComparator.java", javaSource(
                "// Name: Erin Example\n// Email: erin.2026@school.example\n"));
        createZip(zip, entries);

        SubmissionValidationResult result = service.prepareBatch(
                zip, tempDirectory.resolve("normalized")).getFirst();

        assertTrue(result.hasErrors());
        assertTrue(hasFinding(result, "UNSAFE_ZIP_ENTRY", false));
        assertFalse(Files.exists(tempDirectory.resolve("outside.java")));
    }

    @Test
    void unknownTemplateFolderIsIsolatedWithoutCrashing() throws IOException {
        Path batch = Files.createDirectory(tempDirectory.resolve("batch"));
        Path template = Files.createDirectory(batch.resolve("RenameToYourUsername"));
        createRequiredFiles(template, "// Name: Unknown Student\n");

        SubmissionValidationResult result = service.prepareBatch(
                batch, tempDirectory.resolve("normalized")).getFirst();

        assertTrue(result.hasErrors());
        assertTrue(result.username().startsWith("unknown-"));
        assertTrue(hasFinding(result, "USERNAME_UNKNOWN", false));
        assertTrue(hasFinding(result, "MISSING_HEADER_EMAIL", false));
    }

    @Test
    void treatsTheFolderNameAsTheAuthoritativeUsername() throws IOException {
        Path wrongName = Files.createDirectory(tempDirectory.resolve("student_12345"));
        createRequiredFiles(wrongName,
                "// Name: Frank Example\n// Email: frank.2026@school.example\n");

        SubmissionValidationResult result = service.prepareBatch(
                wrongName, tempDirectory.resolve("normalized")).getFirst();

        assertEquals("student_12345", result.username());
        assertTrue(result.hasErrors());
        assertTrue(hasFinding(result, "HEADER_IDENTITY_MISMATCH", false));
    }

    @Test
    void stripsAcademicYearPrefixAndBuildsMissingSmuEmailFromUsername() throws IOException {
        Path zip = tempDirectory.resolve("2023-2024-chee.teo.2022.zip");
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<String, String> requirement
                : SubmissionPreparationService.requiredJavaFiles().entrySet()) {
            entries.put(requirement.getValue() + "/" + requirement.getKey(),
                    javaSource("// Name: Chee Teo\n"));
        }
        createZip(zip, entries);

        SubmissionValidationResult result = service.prepareBatch(
                zip, tempDirectory.resolve("normalized")).getFirst();

        assertEquals("chee.teo.2022", result.username());
        assertTrue(result.isReadyForGrading(), result.findings().toString());
        assertTrue(hasFinding(result, "MISSING_HEADER_EMAIL", true));
        String repaired = Files.readString(result.normalizedRoot().resolve("Q1/Q1a.java"));
        assertTrue(repaired.contains("Student email: chee.teo.2022@smu.edu.sg"));
    }

    @Test
    void reportsAndNormalizesAnExtraUsernameWrapper() throws IOException {
        Path student = Files.createDirectory(tempDirectory.resolve("gina.2026"));
        Path extraWrapper = Files.createDirectory(student.resolve("gina.2026"));
        createRequiredFiles(extraWrapper,
                "// Name: Gina Example\n// Email: gina.2026@school.example\n");

        SubmissionValidationResult result = service.prepareBatch(
                student, tempDirectory.resolve("normalized")).getFirst();

        assertTrue(result.isReadyForGrading(), result.findings().toString());
        assertTrue(hasFinding(result, "JAVA_FILE_RELOCATED", true));
        assertTrue(Files.exists(result.normalizedRoot().resolve("Q1/Q1a.java")));
    }

    @Test
    void unreadableHeaderDoesNotPreventOtherRequiredFilesFromBeingPrepared() throws IOException {
        Path student = Files.createDirectory(tempDirectory.resolve("hank.2026"));
        createRequiredFiles(student,
                "// Name: Hank Example\n// Email: hank.2026@school.example\n");
        Files.write(student.resolve("Q2/Q2a.java"), new byte[] {(byte) 0xC3, (byte) 0x28});

        SubmissionValidationResult result = service.prepareBatch(
                student, tempDirectory.resolve("normalized")).getFirst();

        assertTrue(result.hasErrors());
        assertEquals(6, result.javaFiles().size());
        assertTrue(hasFinding(result, "HEADER_UNREADABLE", false));
        assertTrue(Files.exists(result.normalizedRoot().resolve("Q3/Q3.java")));
    }

    private void createRequiredFiles(Path root, String header) throws IOException {
        for (Map.Entry<String, String> requirement
                : SubmissionPreparationService.requiredJavaFiles().entrySet()) {
            Path question = Files.createDirectories(root.resolve(requirement.getValue()));
            Files.writeString(question.resolve(requirement.getKey()), javaSource(header));
        }
    }

    private String javaSource(String header) {
        return header + "public class Placeholder {}\n";
    }

    private void createZip(Path zip, Map<String, String> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private boolean hasFinding(
            SubmissionValidationResult result,
            String code,
            boolean corrected) {
        return result.findings().stream()
                .anyMatch(finding -> finding.code().equals(code) && finding.corrected() == corrected);
    }

    private SubmissionValidationResult resultFor(
            List<SubmissionValidationResult> results,
            String username) {
        return results.stream()
                .filter(result -> result.username().equals(username))
                .findFirst()
                .orElseThrow();
    }
}
