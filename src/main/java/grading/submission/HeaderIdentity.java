package grading.submission;

import java.util.Optional;

/**
 * The student name and email written in a submission's Java header comments, when present.
 *
 * @param name  the name shared by the headers, or empty when absent or inconsistent
 * @param email the email shared by the headers, or empty when absent or inconsistent
 */
public record HeaderIdentity(Optional<String> name, Optional<String> email) {

    /** No name or email was found. */
    public static HeaderIdentity unknown() {
        return new HeaderIdentity(Optional.empty(), Optional.empty());
    }
}
