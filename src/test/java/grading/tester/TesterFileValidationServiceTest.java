package grading.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import grading.assessment.AssessmentDefinition;
import grading.submission.SubmissionFinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TesterFileValidationServiceTest {

    @TempDir
    Path tempDirectory;

    private final TesterFileValidationService service = new TesterFileValidationService();

    @Test
    void acceptsTheCompleteTesterFolder() throws IOException {
        createAllTesterFiles(tempDirectory);

        TesterValidationResult result = service.validate(tempDirectory);

        assertTrue(result.isValid(), result.findings().toString());
        assertEquals(5, result.testerFiles().size());
        assertFalse(result.hasErrors());
    }

    @Test
    void reportsMissingAndMalformedTesterFilesTogether() throws IOException {
        writeTester(tempDirectory, "Q1aTester.java", "Q1aTester", true);
        writeTester(tempDirectory, "Q1bTester.java", "WrongClass", false);

        TesterValidationResult result = service.validate(tempDirectory);

        assertFalse(result.isValid());
        assertTrue(hasFinding(result, "WRONG_TESTER_CLASS"));
        assertTrue(hasFinding(result, "MISSING_TESTER_MAIN"));
        assertTrue(hasFinding(result, "MISSING_TESTER_FILE"));
    }

    @Test
    void rejectsWrongFilenameCase() throws IOException {
        for (String fileName : AssessmentDefinition.testerFiles()) {
            if (!fileName.equals("Q3Tester.java")) {
                String className = fileName.substring(0, fileName.length() - ".java".length());
                writeTester(tempDirectory, fileName, className, true);
            }
        }
        writeTester(tempDirectory, "q3tester.java", "Q3Tester", true);

        TesterValidationResult result = service.validate(tempDirectory);

        assertFalse(result.isValid());
        assertTrue(hasFinding(result, "WRONG_TESTER_FILENAME_CASE"));
    }

    private void createAllTesterFiles(Path folder) throws IOException {
        for (String fileName : AssessmentDefinition.testerFiles()) {
            String className = fileName.substring(0, fileName.length() - ".java".length());
            writeTester(folder, fileName, className, true);
        }
    }

    private void writeTester(Path folder, String fileName, String className, boolean includeMain)
            throws IOException {
        String mainMethod = includeMain
                ? "    public static void main(String[] args) {}\n"
                : "";
        Files.writeString(folder.resolve(fileName),
                "public class " + className + " {\n" + mainMethod + "}\n");
    }

    private boolean hasFinding(TesterValidationResult result, String code) {
        return result.findings().stream()
                .map(SubmissionFinding::code)
                .anyMatch(code::equals);
    }
}
