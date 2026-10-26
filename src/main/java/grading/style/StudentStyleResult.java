package grading.style;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The style check of one student's submission.
 *
 * @param username the student
 * @param files    the result for each source file
 * @param score    the style score, or empty when no file could be analysed
 * @param maxScore the score of a submission without violations
 * @param notes    plain-language notes about the check as a whole
 */
public record StudentStyleResult(String username, List<FileStyleResult> files, OptionalDouble score,
        double maxScore, List<String> notes) {

    public StudentStyleResult {
        files = List.copyOf(files);
        notes = List.copyOf(notes);
    }

    /** A result for a submission whose style could not be checked, with the reason. */
    public static StudentStyleResult notChecked(String username, double maxScore, String reason) {
        return new StudentStyleResult(username, List.of(), OptionalDouble.empty(), maxScore, List.of(reason));
    }

    /** Every violation found, including repeats on the same line. */
    public int violationCount() {
        return files.stream().mapToInt(file -> file.violations().size()).sum();
    }

    /** True when at least one file could not be analysed, or nothing was checked. */
    public boolean incomplete() {
        return score.isEmpty() || files.stream().anyMatch(file -> !file.analysed());
    }

    /**
     * Violations that count towards the score and penalties: a rule broken more than once on the
     * same line (such as a missing space on both sides of '=') counts once.
     */
    public int countedViolations() {
        return files.stream().mapToInt(FileStyleResult::countedViolations).sum();
    }

    /** Every violation found, file by file. */
    public List<StyleViolation> violations() {
        return files.stream().flatMap(file -> file.violations().stream()).toList();
    }
}
