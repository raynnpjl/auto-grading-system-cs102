package ui;

/**
 * The text of the Activity tab, kept as four sections that are replaced independently: the score sheet,
 * the latest grading run, the tester import and the submission import.
 */
final class ConsoleText {

    private String scoreSheet = "";
    private String grading = "";
    private String testers = "";
    private String submissions = "";

    void setScoreSheet(String text) {
        scoreSheet = text;
    }

    void setGrading(String text) {
        grading = text;
    }

    void setTesters(String text) {
        testers = text;
    }

    void setSubmissions(String text) {
        submissions = text;
    }

    void appendSubmissions(String text) {
        submissions += text;
    }

    /** All sections in display order; the two import reports are separated by a blank line. */
    String text() {
        String separator = !testers.isBlank() && !submissions.isBlank() ? "\n" : "";
        return scoreSheet + grading + testers + separator + submissions;
    }
}
