package grading.submission;

import grading.config.GradingSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Suggests a student's name and email from their username, both for automatic header repair and
 * for pre-filling the identity dialog.
 */
public final class IdentitySuggestions {

    /** Prefix of the placeholder username given to a submission whose owner is unknown. */
    public static final String PLACEHOLDER_PREFIX = "unknown-";

    private final String emailDomain;

    public IdentitySuggestions(String emailDomain) {
        this.emailDomain = emailDomain;
    }

    public static IdentitySuggestions fromSettings() {
        return new IdentitySuggestions(GradingSettings.current().emailDomain());
    }

    public static boolean isPlaceholder(String username) {
        return username == null || username.isBlank() || username.startsWith(PLACEHOLDER_PREFIX);
    }

    /** For example "jiagu.wen.2025" becomes "Jiagu Wen"; a username without name parts is kept. */
    public String displayName(String username) {
        String withoutYear = UsernameResolver.TRAILING_YEAR.matcher(username).replaceFirst("");
        List<String> words = new ArrayList<>();
        for (String part : withoutYear.split("[._-]+")) {
            if (!part.isBlank()) {
                words.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
            }
        }
        return words.isEmpty() ? username : String.join(" ", words);
    }

    /** The username at the configured domain; empty for a placeholder username. */
    public Optional<String> email(String username) {
        return isPlaceholder(username)
                ? Optional.empty()
                : Optional.of(username.toLowerCase(Locale.ROOT) + "@" + emailDomain);
    }
}
