package grading.progress;

import grading.feedback.Feedback;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One student's line in a {@link ProgressSnapshot}.
 *
 * @param username    the student
 * @param status      queued, running, graded, needs-review or skipped
 * @param finished    whether the student is done, graded or skipped
 * @param formatFixed whether the folder or headers were corrected automatically
 * @param scores      the marks of each question part graded so far, in question order
 * @param issues      the submission problems that were not corrected
 * @param flags       notes that need a person's attention, such as a similar answer
 * @param feedback    explanations of the student's mistakes
 */
public record StudentProgress(
        String username,
        String status,
        boolean finished,
        boolean formatFixed,
        Map<String, Double> scores,
        List<String> issues,
        List<String> flags,
        List<Feedback> feedback) {

    public StudentProgress {
        scores = Collections.unmodifiableMap(new LinkedHashMap<>(scores));
        issues = List.copyOf(issues);
        flags = List.copyOf(flags);
        feedback = List.copyOf(feedback);
    }

    /** The sum of the marks graded so far. */
    public double total() {
        return scores.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
