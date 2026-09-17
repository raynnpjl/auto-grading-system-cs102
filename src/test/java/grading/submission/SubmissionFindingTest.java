package grading.submission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SubmissionFindingTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "MISSING_JAVA_FILE",
        "MISSING_HEADER_EMAIL",
        "USERNAME_UNKNOWN",
        "HEADER_IDENTITY_MISMATCH",
        "CONFLICTING_HEADER_IDENTITIES",
        "HEADER_UNREADABLE",
        "JAVA_FILE_TOO_LARGE",
        "JAVA_FILE_UNREADABLE",
        "UNSAFE_ZIP_ENTRY",
        "SUBMISSION_UNREADABLE",
        "MISSING_TESTER_FILE",
        "DUPLICATE_TESTER_FILE",
        "WRONG_TESTER_FILENAME_CASE",
        "WRONG_TESTER_CLASS",
        "MISSING_TESTER_MAIN",
        "TESTER_FILE_TOO_LARGE",
        "TESTER_FILE_UNREADABLE"
    })
    void everyKnownUnresolvedErrorHasAnActionForTheMarker(String code) {
        SubmissionFinding finding = new SubmissionFinding(
                FindingSeverity.ERROR, code, "Validation failed", false);

        assertTrue(finding.nextStep().isPresent());
        assertFalse(finding.nextStep().orElseThrow().isBlank());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "WARNING"})
    void nonErrorsDoNotPromptForManualCorrection(String severity) {
        SubmissionFinding finding = new SubmissionFinding(
                FindingSeverity.valueOf(severity), "EXAMPLE", "Example finding", false);

        assertTrue(finding.nextStep().isEmpty());
    }
}
