package grading.reporting;

import grading.feedback.Feedback;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes the "Common mistakes" section of the attempt summary: the same kind of mistake on the same
 * question, with how many students made it, so the instructor can see what the class found hard.
 */
final class CommonMistakesFormatter {

    /** One kind of mistake on one question. */
    private record Mistake(String questionId, String kind) {
    }

    private CommonMistakesFormatter() {
    }

    static String format(GradingAttempt attempt) {
        Map<Mistake, Set<String>> students = new LinkedHashMap<>();
        for (StudentOutcome student : attempt.students()) {
            for (Feedback feedback : student.feedback()) {
                students.computeIfAbsent(new Mistake(feedback.questionId(), feedback.kind()), key -> new TreeSet<>())
                        .add(student.username());
            }
        }
        if (students.isEmpty()) {
            return "No mistakes were found to explain: every graded question passed all its test cases.\n";
        }
        List<Map.Entry<Mistake, Set<String>>> rows = new ArrayList<>(students.entrySet());
        rows.sort(Comparator.<Map.Entry<Mistake, Set<String>>>comparingInt(entry -> entry.getValue().size())
                .reversed().thenComparing(entry -> entry.getKey().questionId()));
        long graded = attempt.students().stream().filter(StudentOutcome::graded).count();
        StringBuilder text = new StringBuilder("Each student's report explains their own mistakes at the line of "
                + "their code. Across the class:\n\n| Question | Mistake | Students | Who |\n|---|---|---|---|\n");
        for (Map.Entry<Mistake, Set<String>> row : rows) {
            text.append(PlainLanguage.row(row.getKey().questionId(), PlainLanguage.cell(row.getKey().kind()),
                    row.getValue().size() + " of " + graded, PlainLanguage.cell(String.join(", ", row.getValue()))));
        }
        return text.toString();
    }
}
