package grading.submission;

/**
 * A rejected value in a student identity the marker entered. It names the field that was wrong, so
 * the page can point at that input instead of only showing the message.
 */
public final class InvalidIdentityException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String field;

    /**
     * @param field   the rejected field: "username", "name" or "email"
     * @param message what is wrong, in words the marker can act on
     */
    public InvalidIdentityException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The rejected field: "username", "name" or "email". */
    public String field() {
        return field;
    }
}
