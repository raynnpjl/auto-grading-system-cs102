package grading.submission;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A student identity confirmed by the marker. Creating one rejects malformed values and an email
 * that belongs to a different username.
 *
 * @param username the student's LMS username
 * @param name     the student's full name
 * @param email    the student's email address, whose local part matches the username
 */
public record StudentIdentity(String username, String name, String email) {

    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 100;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{1,79}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");

    public StudentIdentity {
        username = Objects.requireNonNull(username, "username").trim();
        name = Objects.requireNonNull(name, "name").trim();
        email = Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Username must contain only letters, numbers, dots, underscores, or hyphens.");
        }
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH || name.contains("\n") || name.contains("\r")) {
            throw new IllegalArgumentException("Student name must be between " + MIN_NAME_LENGTH + " and "
                    + MAX_NAME_LENGTH + " characters.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid student email address.");
        }

        String emailUsername = email.substring(0, email.indexOf('@'));
        if (!normalizeIdentifier(username).equals(normalizeIdentifier(emailUsername))) {
            throw new IllegalArgumentException(
                    "The email username must match the verified submission username.");
        }
    }

    private static String normalizeIdentifier(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
