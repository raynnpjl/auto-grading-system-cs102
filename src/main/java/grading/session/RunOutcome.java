package grading.session;

/** How the latest grading run ended. */
public enum RunOutcome {
    /** No run has finished since the application started. */
    NONE,
    /** Every requested student was graded and the reports were written. */
    COMPLETED,
    /** The marker cancelled the run; the results from before it are shown again. */
    CANCELLED,
    /** Grading stopped because of an unexpected problem; see the activity log. */
    FAILED
}
