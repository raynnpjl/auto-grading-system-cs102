package grading.reporting;

import grading.assessment.AssessmentDefinition;
import grading.scoring.Marks;
import grading.scoring.ScoreBreakdown;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Every student's marks per question part as a spreadsheet, written beside the attempt summary so an
 * instructor can moderate or re-weight questions without opening the PDFs.
 */
final class ScoresCsv {

    private static final String RECORD_SEPARATOR = "\r\n";

    private ScoresCsv() {
    }

    /** One header row, then one row per student in the order given. */
    static String format(List<ScoreBreakdown> scores) {
        List<String> questions = AssessmentDefinition.QUESTIONS.stream()
                .map(AssessmentDefinition.Question::id)
                .toList();
        List<String> header = new ArrayList<>();
        header.add("Username");
        header.addAll(questions);
        header.addAll(List.of("Tester total", "Style violations", "Style penalty", "Final mark"));

        StringWriter text = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setRecordSeparator(RECORD_SEPARATOR).get();
        try (CSVPrinter printer = new CSVPrinter(text, format)) {
            printer.printRecord(header);
            for (ScoreBreakdown score : scores) {
                List<String> row = new ArrayList<>();
                row.add(score.username());
                for (String question : questions) {
                    Double marks = score.questionScores().get(question);
                    row.add(marks == null ? "" : Marks.format(marks));
                }
                row.add(Marks.format(score.testerMarks()));
                row.add(String.valueOf(score.styleViolations()));
                row.add(Marks.format(score.stylePenalty()));
                row.add(Marks.format(score.finalMark()));
                printer.printRecord(row);
            }
        } catch (IOException exception) {
            // A StringWriter never fails; this only satisfies the CSVPrinter signature.
            throw new UncheckedIOException(exception);
        }
        return text.toString();
    }
}
