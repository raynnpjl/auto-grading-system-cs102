package grading.reporting;

import java.io.IOException;

/** A score sheet that cannot be read or updated safely; the message tells the marker what to do. */
public class ScoreSheetException extends IOException {

    public ScoreSheetException(String message) {
        super(message);
    }

    public ScoreSheetException(String message, Throwable cause) {
        super(message, cause);
    }
}
