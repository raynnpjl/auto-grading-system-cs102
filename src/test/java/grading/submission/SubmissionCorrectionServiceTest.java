package grading.submission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubmissionCorrectionServiceTest {

    @TempDir
    Path tempDirectory;

    private final SubmissionPreparationService preparationService = new SubmissionPreparationService();
    private final SubmissionCorrectionService correctionService =
            new SubmissionCorrectionService(preparationService);

    @Test
    void appliesOneVerifiedIdentityToEveryNormalizedJavaFileAndRevalidates() throws IOException {
        Path student = Files.createDirectory(tempDirectory.resolve("jane.tan.2026"));
        createRequiredFilesWithConflictingEmails(student);
        SubmissionValidationResult initial = preparationService.prepareBatch(
                student, tempDirectory.resolve("first-pass")).getFirst();
        assertTrue(initial.hasErrors());

        StudentIdentity identity = new StudentIdentity(
                "jane.tan.2026", "Jane Tan", "jane.tan.2026@school.example");
        SubmissionValidationResult corrected = correctionService.applyIdentity(
                initial, identity, tempDirectory.resolve("guided-correction"));

        assertTrue(corrected.isReadyForGrading(), corrected.findings().toString());
        assertTrue(corrected.findings().stream().anyMatch(
                finding -> finding.code().equals("IDENTITY_CONFIRMED") && finding.corrected()));
        for (Path javaFile : corrected.javaFiles().values()) {
            String source = Files.readString(javaFile);
            assertTrue(source.contains("Verified student username: jane.tan.2026"));
            assertTrue(source.contains("Verified student name: Jane Tan"));
            assertTrue(source.contains("Verified student email: jane.tan.2026@school.example"));
        }

        String original = Files.readString(student.resolve("Q1/Q1a.java"));
        assertFalse(original.contains("Verified student"));
    }

    @Test
    void rejectsAnEmailThatDoesNotMatchTheVerifiedUsername() {
        assertThrows(IllegalArgumentException.class, () -> new StudentIdentity(
                "jane.tan.2026", "Jane Tan", "someone.else@school.example"));
    }

    private void createRequiredFilesWithConflictingEmails(Path root) throws IOException {
        int index = 0;
        for (Map.Entry<String, String> requirement
                : SubmissionPreparationService.requiredJavaFiles().entrySet()) {
            Path question = Files.createDirectories(root.resolve(requirement.getValue()));
            String email = index++ % 2 == 0
                    ? "jane.tan.2026@school.example"
                    : "someone.else@school.example";
            Files.writeString(
                    question.resolve(requirement.getKey()),
                    "// Name: Jane Tan\n// Email: " + email + "\npublic class Placeholder {}\n");
        }
    }
}
