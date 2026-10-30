package grading.submission;

/** Told about each submission as a batch import prepares it, so the window can show progress. */
@FunctionalInterface
public interface ImportProgress {

    /** Ignores every report; for callers that do not show progress. */
    ImportProgress NONE = (done, total, name) -> { };

    /**
     * Called after each submission, including one that could not be prepared.
     *
     * @param done  how many submissions are prepared so far
     * @param total how many submissions were found
     * @param name  the file or folder name of the submission just prepared
     */
    void prepared(int done, int total, String name);
}
