package grading.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import grading.assessment.AssessmentDefinition;
import grading.config.GradingSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubmissionCheckTest {

    @TempDir
    Path temporary;

    @Test
    void additionalChecksRunOnEverySubmissionWithoutChangingTheService() throws IOException {
        Path batch = Files.createDirectory(temporary.resolve("batch"));
        createSubmission(batch.resolve("alice.2026"));
        createSubmission(batch.resolve("bob.2026"));
        List<String> inspected = new ArrayList<>();
        SubmissionCheck countJavaFiles = (submission, findings) -> {
            inspected.add(submission.username());
            findings.add(new SubmissionFinding(FindingSeverity.INFO, "CUSTOM_CHECK",
                    submission.javaFiles().size() + " Java files", false));
        };

        List<SubmissionValidationResult> results = new SubmissionPreparationService(
                GradingSettings.current(), List.of(new QuestionFolderCheck(), countJavaFiles))
                .prepareBatch(batch, temporary.resolve("normalized"));

        assertEquals(List.of("alice.2026", "bob.2026"), inspected);
        assertTrue(results.stream().allMatch(result -> result.findings().stream()
                .anyMatch(finding -> finding.code().equals("CUSTOM_CHECK") && finding.message()
                        .equals("6 Java files"))));
    }

    @Test
    void zipAndFolderSubmissionsExpectDifferentLayouts() throws IOException {
        Path zip = Files.writeString(temporary.resolve("alice.2026.zip"), "");
        Path folder = Files.createDirectory(temporary.resolve("alice.2026"));
        ZipExtractor extractor = new ZipExtractor(1, 1);

        SubmissionSource zipped = SubmissionSource.of(zip, extractor);
        SubmissionSource extracted = SubmissionSource.of(folder, extractor);

        assertTrue(zipped instanceof ZipSubmission);
        assertTrue(extracted instanceof FolderSubmission);
        assertEquals("alice.2026/Q1/Q1a.java", zipped.expectedLocation("alice.2026", "Q1", "Q1a.java"));
        assertEquals("Q1/Q1a.java", extracted.expectedLocation("alice.2026", "Q1", "Q1a.java"));
        assertTrue(extracted.checkUsernameFolder(folder, "alice.2026").isEmpty());
        assertEquals("WRONG_USERNAME_FOLDER",
                extracted.checkUsernameFolder(folder, "bob.2026").orElseThrow().code());
    }

    private void createSubmission(Path student) throws IOException {
        for (var entry : AssessmentDefinition.sourceFiles().entrySet()) {
            Path folder = Files.createDirectories(student.resolve(entry.getValue()));
            Files.writeString(folder.resolve(entry.getKey()), "// Name: Student\n// Email: "
                    + student.getFileName() + "@school.example\npublic class Placeholder {}\n");
        }
    }
}
