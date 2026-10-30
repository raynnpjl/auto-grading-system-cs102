package grading.progress;

import java.util.Locale;

/** Where a student is in a grading run, as shown in the results table. */
public enum GradingStatus {
    QUEUED("queued"),
    RUNNING("running"),
    GRADED("graded"),
    NEEDS_REVIEW("needs review"),
    SKIPPED("skipped");

    private final String label;

    GradingStatus(String label) {
        this.label = label;
    }

    /** The word shown to the marker. */
    public String label() {
        return label;
    }

    /** The CSS class that colours this status, such as status-needs-review. */
    public String styleClass() {
        return "status-" + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Whether the student was graded without anything a person needs to check. */
    public boolean isClean() {
        return this == GRADED;
    }
}
