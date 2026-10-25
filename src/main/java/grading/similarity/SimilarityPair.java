package grading.similarity;

import grading.common.Percent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Two students whose answers to one question are similar enough to report.
 *
 * @param questionId     the question part, such as Q2a
 * @param first          one student's username
 * @param second         the other student's username
 * @param winnowing      the winnowing similarity, from 0 to 1
 * @param tiling         the Greedy String Tiling similarity, from 0 to 1; this is the reported similarity
 * @param firstTokens    how many tokens the first student's answer has, after normalization
 * @param secondTokens   how many tokens the second student's answer has
 * @param firstLines     the first student's lines that match, as submitted
 * @param secondLines    the second student's lines that match
 * @param flagged        true when both students should be checked by a person
 */
public record SimilarityPair(String questionId, String first, String second, double winnowing, double tiling,
        int firstTokens, int secondTokens, List<LineRange> firstLines, List<LineRange> secondLines,
        boolean flagged) {

    /** Lines of one file, such as Q2a.java lines 12 to 40. */
    public record LineRange(String file, int from, int to) {

        String numbers() {
            return from == to ? String.valueOf(from) : from + "–" + to;
        }
    }

    /** The ranges grouped by file, as in "Q1b.java lines 15–23, 25–27; Q3.java line 8". */
    public static String describe(List<LineRange> ranges) {
        Map<String, List<LineRange>> byFile = new LinkedHashMap<>();
        ranges.forEach(range -> byFile.computeIfAbsent(range.file(), file -> new ArrayList<>()).add(range));
        return byFile.entrySet().stream()
                .map(file -> describeFile(file.getKey(), file.getValue()))
                .collect(Collectors.joining("; "));
    }

    private static String describeFile(String file, List<LineRange> ranges) {
        boolean oneLine = ranges.size() == 1 && ranges.getFirst().from() == ranges.getFirst().to();
        return file + (oneLine ? " line " : " lines ")
                + ranges.stream().map(LineRange::numbers).collect(Collectors.joining(", "));
    }

    public SimilarityPair {
        firstLines = List.copyOf(firstLines);
        secondLines = List.copyOf(secondLines);
    }

    /** The similarity as a whole percentage, such as 91. */
    public long percent() {
        return Percent.of(tiling);
    }

    /** True when this pair involves the student. */
    public boolean involves(String username) {
        return first.equals(username) || second.equals(username);
    }

    /** The other student in the pair. */
    public String otherThan(String username) {
        return first.equals(username) ? second : first;
    }

    /** The matching lines of one student in the pair. */
    public List<LineRange> linesOf(String username) {
        return first.equals(username) ? firstLines : secondLines;
    }
}
