package grading.style;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the lines of a student's file that were kept unchanged from the instructor's template, so
 * the template's own code is not counted against the student. Lines are compared ignoring the
 * indentation and trailing spaces, and matched in order with a longest-common-subsequence, so a
 * student's own line that happens to look like a template line elsewhere is still checked.
 */
final class TemplateLines {

    // Above this many line pairs the in-order match would use too much memory; matching by line
    // content alone is used instead.
    private static final long MAX_MATCH_CELLS = 4_000_000;

    private TemplateLines() {
    }

    /** The 1-based numbers of the student's lines that are kept template lines. */
    static Set<Integer> kept(List<String> student, List<String> template) {
        if ((long) student.size() * template.size() > MAX_MATCH_CELLS) {
            return keptByContent(student, template);
        }
        int rows = student.size();
        int columns = template.size();
        int[][] common = new int[rows + 1][columns + 1];
        for (int row = rows - 1; row >= 0; row--) {
            for (int column = columns - 1; column >= 0; column--) {
                common[row][column] = same(student.get(row), template.get(column))
                        ? common[row + 1][column + 1] + 1
                        : Math.max(common[row + 1][column], common[row][column + 1]);
            }
        }
        Set<Integer> kept = new HashSet<>();
        int row = 0;
        int column = 0;
        while (row < rows && column < columns) {
            if (same(student.get(row), template.get(column))) {
                kept.add(row + 1);
                row++;
                column++;
            } else if (common[row + 1][column] >= common[row][column + 1]) {
                row++;
            } else {
                column++;
            }
        }
        return kept;
    }

    private static Set<Integer> keptByContent(List<String> student, List<String> template) {
        Map<String, Integer> available = new HashMap<>();
        template.forEach(line -> available.merge(line.strip(), 1, Integer::sum));
        Set<Integer> kept = new HashSet<>();
        for (int index = 0; index < student.size(); index++) {
            String line = student.get(index).strip();
            if (available.getOrDefault(line, 0) > 0) {
                available.merge(line, -1, Integer::sum);
                kept.add(index + 1);
            }
        }
        return kept;
    }

    private static boolean same(String studentLine, String templateLine) {
        return studentLine.strip().equals(templateLine.strip());
    }
}
