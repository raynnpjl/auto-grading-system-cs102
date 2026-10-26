package grading.reporting;

import grading.common.Percent;
import grading.config.SimilaritySettings;
import grading.similarity.SimilarityPair;
import grading.similarity.SimilarityReport;
import java.util.List;

/**
 * Writes the "Possible plagiarism" section of the attempt summary: the pairs of students whose
 * answers to a question are very similar, the lines that match, and how they were found. It is only
 * in the summary, never in a student's own report.
 */
final class SimilarityReportFormatter {

    private SimilarityReportFormatter() {
    }

    static String format(SimilarityReport report) {
        StringBuilder text = new StringBuilder();
        if (!report.problem().isEmpty()) {
            return text.append(report.problem()).append('\n').toString();
        }
        SimilaritySettings settings = report.settings();
        long threshold = Percent.of(settings.threshold());
        List<SimilarityPair> pairs = report.pairs();
        if (pairs.isEmpty()) {
            text.append("No two students' answers to the same question are ").append(threshold)
                    .append("% similar or more (").append(report.compared()).append(" pairs of answers compared).\n\n");
        } else {
            long flagged = pairs.stream().filter(SimilarityPair::flagged).count();
            text.append(pairs.size()).append(" pair(s) of answers are ").append(threshold)
                    .append("% similar or more (").append(report.compared()).append(" pairs compared). ")
                    .append(flagged).append(" are flagged, and those students are listed under \"Students needing a ")
                    .append("manual check\". Marks are not changed.\n\n")
                    .append("| Question | Students | Similarity | Matching lines | Flagged |\n|---|---|---|---|---|\n");
            for (SimilarityPair pair : pairs) {
                text.append(PlainLanguage.row(pair.questionId(),
                        PlainLanguage.cell(pair.first() + " and " + pair.second()), pair.percent() + "%",
                        PlainLanguage.cell(pair.first() + ": " + ranges(pair.firstLines())
                                + "\n" + pair.second() + ": " + ranges(pair.secondLines())),
                        pair.flagged() ? "yes" : "no: answers shorter than " + settings.minTokens() + " tokens"));
            }
            text.append('\n');
        }
        text.append("How answers are compared: comments, layout, variable names, literal values, imports and the ")
                .append("template's code are ignored. Every pair is screened with ")
                .append(report.algorithms().getFirst())
                .append("; pairs scoring ").append(Percent.of(settings.screenThreshold()))
                .append("% or more are scored with ").append(report.algorithms().getLast())
                .append(", the share of both answers covered by matching runs of code. A high similarity is a reason ")
                .append("to look, not proof of copying: short answers can only be written a few ways.\n");
        return text.toString();
    }

    private static String ranges(List<SimilarityPair.LineRange> ranges) {
        return ranges.isEmpty() ? "—" : SimilarityPair.describe(ranges);
    }
}
