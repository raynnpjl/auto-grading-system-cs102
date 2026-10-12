package grading.style;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The style check of one source file.
 *
 * @param fileName          the file, such as Q1a.java
 * @param analysed          false when the file could not be analysed
 * @param violations        the violations in code the student wrote
 * @param templateLinesKept lines kept unchanged from the instructor's template, which were not checked
 * @param note              a plain-language explanation, such as why the file could not be analysed
 */
public record FileStyleResult(String fileName, boolean analysed, List<StyleViolation> violations,
        int templateLinesKept, String note) {

    public FileStyleResult {
        violations = List.copyOf(violations);
        note = note == null ? "" : note;
    }

    /** Violations that count towards the score: each rule once per line. */
    public int countedViolations() {
        return countedViolationList().size();
    }

    List<StyleViolation> countedViolationList() {
        Set<String> seen = new HashSet<>();
        List<StyleViolation> counted = new ArrayList<>();
        for (StyleViolation violation : violations) {
            // a whole-file violation (line 0) always counts
            String rule = violation.rule() == null ? "unknown" : violation.rule().id();
            if (violation.line() == 0 || seen.add(violation.line() + ":" + rule)) {
                counted.add(violation);
            }
        }
        return counted;
    }

    static FileStyleResult couldNotAnalyse(String fileName, String reason) {
        return new FileStyleResult(fileName, false, List.of(), 0, reason);
    }
}
