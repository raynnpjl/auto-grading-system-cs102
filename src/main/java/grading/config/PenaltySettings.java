package grading.config;

/**
 * Penalties deducted from the marks awarded by the testers.
 *
 * @param stylePerViolation marks deducted for each style violation
 * @param styleCap          the most a student can lose to style penalties
 */
public record PenaltySettings(double stylePerViolation, double styleCap) {

    /** True when no penalty can change a student's marks. */
    public boolean none() {
        return stylePerViolation == 0 || styleCap == 0;
    }
}
