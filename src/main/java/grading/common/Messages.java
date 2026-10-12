package grading.common;

/** Turns failures into text a marker can read. */
public final class Messages {

    private Messages() {
    }

    /** The failure's message, or its type when it has no message. */
    public static String describe(Throwable failure) {
        if (failure == null) {
            return "Unknown error";
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
