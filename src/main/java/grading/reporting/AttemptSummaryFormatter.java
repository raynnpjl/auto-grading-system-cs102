package grading.reporting;

import grading.assessment.AssessmentDefinition;
import grading.config.GradingSettings;
import grading.config.PenaltySettings;
import grading.execution.QuestionGradingResult;
import grading.scoring.ScoreBreakdown;
import grading.style.StudentStyleResult;
import grading.style.StyleRules;
import grading.submission.SubmissionFinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes the summary of a grading attempt: who needs a manual check (listed first), class
 * statistics, and the configuration used, so the results can be reproduced.
 */
public final class AttemptSummaryFormatter {

    private static final int TOP_ITEMS = 5;

    private AttemptSummaryFormatter() {
    }

    /** {@code reportFiles} maps each student to the file name of their report, when it was written. */
    public static String format(GradingAttempt attempt, String attemptId, Map<StudentOutcome, String> reportFiles,
            List<String> problems) {
        StringBuilder report = new StringBuilder()
                .append("# Grading attempt ").append(attemptId).append("\n\n")
                .append("- Started: ").append(attempt.startedAt().format(StudentReportFormatter.WHEN)).append('\n')
                .append("- Finished: ").append(attempt.finishedAt().format(StudentReportFormatter.WHEN)).append('\n')
                .append("- Tester folder: `").append(attempt.testerFolder()).append("`\n")
                .append("- Submissions folder: `").append(attempt.submissionsFolder()).append("`\n\n");
        StudentReportFormatter.section(report, "Overview", () -> overview(attempt));
        StudentReportFormatter.section(report, "Students needing a manual check",
                () -> needingReview(attempt, reportFiles));
        StudentReportFormatter.section(report, "Other students", () -> others(attempt, reportFiles));
        StudentReportFormatter.section(report, "Class statistics", () -> statistics(attempt));
        StudentReportFormatter.section(report, "Configuration used", () -> configuration(attempt));
        if (!problems.isEmpty()) {
            StudentReportFormatter.section(report, "Problems while writing reports", () -> problems.stream()
                    .map(problem -> "- " + problem + "\n").collect(Collectors.joining()));
        }
        return report.toString();
    }

    private static String overview(GradingAttempt attempt) {
        long graded = attempt.students().stream().filter(StudentOutcome::graded).count();
        long review = attempt.students().stream().filter(student -> !student.reviewReasons().isEmpty()).count();
        return attempt.students().size() + " submission(s): " + graded + " graded, "
                + (attempt.students().size() - graded) + " not graded. "
                + (review == 0 ? "None needs a manual check.\n"
                        : "Manual check needed for " + review + "; they are listed first.\n");
    }

    private static String needingReview(GradingAttempt attempt, Map<StudentOutcome, String> reportFiles) {
        List<StudentOutcome> review = attempt.students().stream()
                .filter(student -> !student.reviewReasons().isEmpty())
                .toList();
        if (review.isEmpty()) {
            return "None: every student was graded without problems.\n";
        }
        StringBuilder text = new StringBuilder("| Student | Final mark | Why | Report |\n|---|---|---|---|\n");
        for (StudentOutcome student : review) {
            text.append("| ").append(PlainLanguage.cell(student.username()))
                    .append(" | ").append(finalMark(student))
                    .append(" | ").append(PlainLanguage.cell(String.join(" ", student.reviewReasons())))
                    .append(" | ").append(link(student, reportFiles)).append(" |\n");
        }
        return text.toString();
    }

    private static String others(GradingAttempt attempt, Map<StudentOutcome, String> reportFiles) {
        List<StudentOutcome> others = attempt.students().stream()
                .filter(student -> student.reviewReasons().isEmpty())
                .toList();
        if (others.isEmpty()) {
            return "None.\n";
        }
        StringBuilder text = new StringBuilder("| Student | Final mark | Style score | Report |\n|---|---|---|---|\n");
        for (StudentOutcome student : others) {
            text.append("| ").append(PlainLanguage.cell(student.username()))
                    .append(" | ").append(finalMark(student))
                    .append(" | ").append(styleScore(student.style()))
                    .append(" | ").append(link(student, reportFiles)).append(" |\n");
        }
        return text.toString();
    }

    private static String statistics(GradingAttempt attempt) {
        List<Double> marks = attempt.scores().stream().map(ScoreBreakdown::finalMark).sorted().toList();
        StringBuilder text = new StringBuilder();
        if (marks.isEmpty()) {
            text.append("No student was graded, so there are no mark statistics.\n\n");
        } else {
            double mean = marks.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double spread = Math.sqrt(marks.stream().mapToDouble(mark -> (mark - mean) * (mark - mean))
                    .average().orElse(0));
            text.append("Final marks of the ").append(marks.size()).append(" graded student(s): mean ")
                    .append(PlainLanguage.marks(mean)).append(", median ").append(PlainLanguage.marks(median(marks)))
                    .append(", lowest ").append(PlainLanguage.marks(marks.getFirst()))
                    .append(", highest ").append(PlainLanguage.marks(marks.getLast()))
                    .append(", standard deviation ").append(PlainLanguage.marks(spread)).append(".\n\n");
        }
        text.append(questionStatistics(attempt)).append('\n').append(styleStatistics(attempt)).append('\n')
                .append(commonProblems(attempt));
        return text.toString();
    }

    private static String questionStatistics(GradingAttempt attempt) {
        StringBuilder text = new StringBuilder(
                "| Question | Mean marks | Test cases passed | Not fully graded |\n|---|---|---|---|\n");
        for (AssessmentDefinition.Question question : AssessmentDefinition.QUESTIONS) {
            List<QuestionGradingResult> results = results(attempt, question.id());
            double mean = results.stream().mapToDouble(QuestionGradingResult::score).average().orElse(0);
            int passed = results.stream().mapToInt(QuestionGradingResult::passedTests).sum();
            int run = passed + results.stream().mapToInt(QuestionGradingResult::failedTests).sum();
            long problems = results.stream()
                    .filter(result -> result.status() != QuestionGradingResult.Status.GRADED)
                    .count();
            text.append("| ").append(question.id())
                    .append(" | ").append(results.isEmpty() ? "—" : PlainLanguage.marks(mean))
                    .append(" | ").append(run == 0 ? "—" : passed + " of " + run + " ("
                            + Math.round(100.0 * passed / run) + "%)")
                    .append(" | ").append(problems).append(" |\n");
        }
        return text.toString();
    }

    private static String styleStatistics(GradingAttempt attempt) {
        List<StudentStyleResult> styles = attempt.students().stream()
                .map(StudentOutcome::style)
                .filter(style -> style != null && style.score().isPresent())
                .toList();
        if (styles.isEmpty()) {
            return "Style: no submission could be analysed.\n";
        }
        double mean = styles.stream().mapToDouble(style -> style.score().getAsDouble()).average().orElse(0);
        StringBuilder text = new StringBuilder("Style: mean score ").append(PlainLanguage.marks(mean))
                .append(" out of ").append(PlainLanguage.marks(styles.getFirst().maxScore())).append(".");
        Map<String, Long> byRule = styles.stream()
                .flatMap(style -> style.violations().stream())
                .collect(Collectors.groupingBy(
                        violation -> violation.rule() == null ? "Unrecognised style check" : violation.rule().text(),
                        Collectors.counting()));
        if (!byRule.isEmpty()) {
            text.append(" Most common style problems:\n\n");
            top(byRule).forEach(entry -> text.append("- ").append(entry.getKey()).append(" (")
                    .append(entry.getValue()).append(" times)\n"));
        } else {
            text.append('\n');
        }
        return text.toString();
    }

    private static String commonProblems(GradingAttempt attempt) {
        Map<String, Long> byKind = attempt.students().stream()
                .flatMap(student -> student.submission().findings().stream()
                        .map(SubmissionFinding::code)
                        .collect(Collectors.toCollection(LinkedHashSet::new)).stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        if (byKind.isEmpty()) {
            return "Submission problems: none were found.\n";
        }
        StringBuilder text = new StringBuilder("Most common submission problems (students affected):\n\n");
        top(byKind).forEach(entry -> text.append("- ").append(entry.getKey().toLowerCase(Locale.ROOT).replace('_', ' '))
                .append(": ").append(entry.getValue()).append('\n'));
        return text.toString();
    }

    private static String configuration(GradingAttempt attempt) {
        GradingSettings settings = attempt.settings();
        PenaltySettings penalties = settings.penalties();
        StringBuilder text = new StringBuilder()
                .append("- Time limits: compiling ").append(settings.compileTimeout().toSeconds())
                .append(" s, running a tester ").append(settings.executionTimeout().toSeconds()).append(" s.\n")
                .append("- Students graded at the same time: ").append(settings.parallelStudents()).append(".\n")
                .append("- Output kept per process stream: ").append(settings.maxOutputBytes()).append(" bytes.\n")
                .append("- Marks per test case: awarded by each tester. ").append(observedMarks(attempt)).append('\n')
                .append("- Penalties: ").append(PlainLanguage.marks(penalties.stylePerViolation()))
                .append(" per counted style violation, capped at ").append(PlainLanguage.marks(penalties.styleCap()))
                .append(penalties.none() ? " (none applied: style is reported only).\n" : ".\n")
                .append("- Style: Checkstyle ").append(attempt.checkstyleVersion()).append(", configuration `")
                .append(settings.style().checkstyleConfig()).append("` (SHA-256 ")
                .append(fingerprint(settings.style().checkstyleConfig())).append("), rules `")
                .append(settings.style().rules()).append("` (SHA-256 ").append(fingerprint(settings.style().rules()))
                .append("), template `").append(settings.style().templateFolder()).append("`, maximum score ")
                .append(PlainLanguage.marks(settings.style().maxScore())).append(".\n\n");
        StyleRules rules = attempt.styleRules();
        rules.problem().ifPresent(problem -> text.append(problem).append("\n\n"));
        if (!rules.all().isEmpty()) {
            text.append("| Style rule | Guide section | Weight |\n|---|---|---|\n");
            rules.all().forEach(rule -> text.append("| ").append(PlainLanguage.cell(rule.text()))
                    .append(rule.heuristic() ? " *(heuristic)*" : "")
                    .append(" | ").append(PlainLanguage.cell(rule.section()))
                    .append(" | ").append(PlainLanguage.marks(rule.weight())).append(" |\n"));
            text.append('\n');
        }
        if (!rules.unchecked().isEmpty()) {
            text.append("Guide rules not checked automatically:\n\n");
            rules.unchecked().forEach(rule -> text.append("- Section ").append(rule.section()).append(": ")
                    .append(rule.text()).append('\n'));
            text.append('\n');
        }
        text.append("Questions: ");
        text.append(AssessmentDefinition.QUESTIONS.stream()
                .map(question -> question.id() + " (" + String.join(", ", question.sources()) + ")")
                .collect(Collectors.joining("; "))).append(".\n");
        return text.toString();
    }

    // The marks per passed test case that the testers actually awarded in this attempt.
    private static String observedMarks(GradingAttempt attempt) {
        StringBuilder text = new StringBuilder("Observed:");
        for (AssessmentDefinition.Question question : AssessmentDefinition.QUESTIONS) {
            Set<String> rates = results(attempt, question.id()).stream()
                    .filter(result -> result.status() == QuestionGradingResult.Status.GRADED)
                    .filter(result -> result.passedTests() > 0)
                    .map(result -> PlainLanguage.marks(result.score() / result.passedTests()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            text.append(' ').append(question.id()).append(' ')
                    .append(rates.isEmpty() ? "not observed" : rates.size() == 1 ? rates.iterator().next() + " per pass"
                            : "varied")
                    .append(';');
        }
        text.setLength(text.length() - 1);
        return text.append('.').toString();
    }

    private static List<QuestionGradingResult> results(GradingAttempt attempt, String questionId) {
        return attempt.students().stream()
                .map(StudentOutcome::grading)
                .filter(Objects::nonNull)
                .flatMap(grading -> grading.questions().stream())
                .filter(result -> result.questionId().equals(questionId))
                .toList();
    }

    private static List<Map.Entry<String, Long>> top(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_ITEMS)
                .toList();
    }

    private static double median(List<Double> sorted) {
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static String finalMark(StudentOutcome student) {
        return student.score() == null ? "not graded" : PlainLanguage.marks(student.score().finalMark());
    }

    private static String styleScore(StudentStyleResult style) {
        return style == null || style.score().isEmpty()
                ? "—"
                : PlainLanguage.marks(style.score().getAsDouble()) + " / " + PlainLanguage.marks(style.maxScore());
    }

    private static String link(StudentOutcome student, Map<StudentOutcome, String> reportFiles) {
        String file = reportFiles.get(student);
        return file == null ? "not written" : "[" + file + "](students/" + file + ")";
    }

    private static String fingerprint(Path file) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (IOException exception) {
            return "file not readable";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", exception);
        }
    }
}
