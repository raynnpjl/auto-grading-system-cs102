package grading.submission;

import java.util.Objects;
import java.util.Optional;

public record SubmissionFinding(
        FindingSeverity severity,
        String code,
        String message,
        boolean corrected) {

    public SubmissionFinding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Gives the marker a concrete recovery action when validation cannot safely
     * correct an error automatically.
     */
    public Optional<String> nextStep() {
        if (severity != FindingSeverity.ERROR || corrected) {
            return Optional.empty();
        }

        return Optional.of(switch (code) {
            case "MISSING_DEPENDENCY", "DEPENDENCY_UNREADABLE" ->
                    "Restore the named support class or test data file from the assessment materials to the "
                            + "stated Q folder, ensure it is readable, then import the submissions again.";
            case "MISSING_JAVA_FILE" ->
                    "Retrieve the missing source file from the student's submission or request a resubmission, "
                            + "place it in the stated Q folder, then import the submissions again.";
            case "MISSING_HEADER_EMAIL" ->
                    "Select Resolve issues, confirm the student's username, name, and email from the LMS or "
                            + "class roster, then apply once to update all normalized headers automatically.";
            case "USERNAME_UNKNOWN" ->
                    "Select Resolve issues and enter the identity shown in the LMS upload record or class roster. "
                            + "The application will rename the normalized copy and update every header.";
            case "HEADER_IDENTITY_MISMATCH", "CONFLICTING_HEADER_IDENTITIES" ->
                    "Compare the ZIP/folder owner against the LMS record, then select Resolve issues and enter the "
                            + "single verified identity to replace inconsistent normalized headers automatically.";
            case "HEADER_UNREADABLE" ->
                    "Open the named Java file in an editor, confirm it is genuine source code, save it as UTF-8, "
                            + "and import the submission again.";
            case "JAVA_FILE_TOO_LARGE" ->
                    "Inspect the file manually before opening or running it. If it is not a normal Java source "
                            + "file, request a clean resubmission.";
            case "JAVA_FILE_UNREADABLE" ->
                    "Restore read access or extract/copy the named source file manually into the correct Q folder, "
                            + "then import the submission again.";
            case "UNSAFE_ZIP_ENTRY" ->
                    "Do not use the skipped entry. Inspect the original archive separately and request a clean "
                            + "resubmission if the required source was stored in that unsafe path.";
            case "SUBMISSION_UNREADABLE" ->
                    "Open or extract this submission manually, repair the archive/folder or request a replacement, "
                            + "then import it again. The remaining batch can still be graded.";
            case "MISSING_TESTER_FILE" ->
                    "Add the named official tester file to the selected Tester-Files folder, then import that "
                            + "folder again.";
            case "DUPLICATE_TESTER_FILE" ->
                    "Keep the approved tester version and remove or move the duplicate outside Tester-Files, "
                            + "then validate the folder again.";
            case "WRONG_TESTER_FILENAME_CASE" ->
                    "Rename the file exactly as shown, preserving capitalization, then import Tester-Files again.";
            case "WRONG_TESTER_CLASS" ->
                    "Correct the public class name so it exactly matches the Java filename, then validate again.";
            case "MISSING_TESTER_MAIN" ->
                    "Restore the official tester implementation containing its static main method, then validate "
                            + "the folder again.";
            case "TESTER_FILE_TOO_LARGE" ->
                    "Compare the file with the approved tester source and replace it with the official version "
                            + "before grading.";
            case "TESTER_FILE_UNREADABLE" ->
                    "Restore read access or replace the file with a readable UTF-8 copy of the official tester, "
                            + "then import the folder again.";
            default ->
                    "Inspect the original item, correct the reported problem in a working copy, and run validation "
                            + "again before grading.";
        });
    }
}
