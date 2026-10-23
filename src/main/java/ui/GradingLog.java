package ui;

/**
 * The grading process output shown on screen. Only the most recent output is kept, so a long run
 * cannot exhaust memory; the terminal still receives everything.
 */
final class GradingLog {

    private final StringBuilder text = new StringBuilder();
    private final int maxCharacters;
    private boolean truncated;

    GradingLog(int maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    void append(String message) {
        text.append(message).append('\n');
        if (text.length() > maxCharacters) {
            text.delete(0, text.length() - maxCharacters);
            truncated = true;
        }
    }

    void clear() {
        text.setLength(0);
        truncated = false;
    }

    String text() {
        return (truncated ? "[Earlier process output omitted from this view; see terminal.]\n" : "") + text;
    }
}
