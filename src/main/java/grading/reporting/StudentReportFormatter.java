package grading.reporting;

import grading.assessment.AssessmentDefinition;
import grading.common.Messages;
import grading.config.PenaltySettings;
import grading.execution.ProcessResult;
import grading.execution.QuestionGradingResult;
import grading.execution.TestCaseResult;
import grading.feedback.Feedback;
import grading.scoring.ScoreBreakdown;
import grading.style.FileStyleResult;
import grading.style.StudentStyleResult;
import grading.style.StyleRules;
import grading.style.StyleViolation;
import grading.submission.FileCorrection;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionValidationResult;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Writes one student's report for a grading attempt in Markdown and plain language. Each section is
 * produced independently: a missing or broken result becomes a note in the report, never an error.
 */
public final class StudentReportFormatter {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ROOT);
    private static final int MAX_COMPILER_LINES = 15;
    // The temporary folder javac compiled in, which means nothing to a reader of the report.
    private static final Pattern GRADING_FOLDER = Pattern.compile(
            "(?m)^\\S*?[/\\\\]cs102-grading-[^/\\\\]+[/\\\\]sources[/\\\\]");

    private StudentReportFormatter() {
    }

    public static String format(StudentOutcome student, GradingAttempt attempt, String attemptId) {
        StringBuilder report = new StringBuilder()
                .append("# Grading report: ").append(student.username()).append("\n\n")
                .append("Attempt ").append(attemptId).append(", graded ")
                .append(attempt.finishedAt().format(WHEN)).append(".\n\n");
        section(report, "Summary", () -> summary(student));
        section(report, "Identity", () -> identity(student.submission()));
        section(report, "Submission structure", () -> structure(student.submission()));
        section(report, "Questions and test cases", () -> questions(student));
        section(report, "Score breakdown", () -> breakdown(student, attempt.settings().penalties()));
        section(report, "Style", () -> style(student.style(), attempt.styleRules()));
        return report.toString();
    }

    // A section that cannot be produced is replaced by a note, so the rest of the report survives.
    static void section(StringBuilder report, String title, Supplier<String> content) {
        report.append("## ").append(title).append("\n\n");
        try {
            report.append(content.get());
        } catch (RuntimeException exception) {
            report.append("This section could not be produced: ").append(Messages.describe(exception))
                    .append(". The rest of the report is unaffected.\n");
        }
        report.append('\n');
    }

    private static String summary(StudentOutcome student) {
        StringBuilder text = new StringBuilder();
        if (student.score() == null) {
            text.append("- **Final mark:** not graded in this attempt.\n");
        } else {
            ScoreBreakdown score = student.score();
            text.append("- **Final mark:** ").append(PlainLanguage.marks(score.finalMark()))
                    .append(" (tester marks ").append(PlainLanguage.marks(score.testerMarks()))
                    .append(", penalties ").append(PlainLanguage.marks(score.stylePenalty())).append(")\n");
        }
        StudentStyleResult style = student.style();
        if (style == null || style.score().isEmpty()) {
            text.append("- **Style score:** not available (see the Style section).\n");
        } else {
            text.append("- **Style score:** ").append(PlainLanguage.marks(style.score().getAsDouble()))
                    .append(" out of ").append(PlainLanguage.marks(style.maxScore())).append(" (")
                    .append(style.violationCount()).append(" violation(s) found)\n");
        }
        List<String> reasons = student.ownReviewReasons();
        if (reasons.isEmpty()) {
            text.append("- **Manual check needed:** no.\n");
        } else {
            text.append("- **Manual check needed:** yes.\n");
            reasons.forEach(reason -> text.append("  - ").append(reason).append('\n'));
        }
        return text.toString();
    }

    private static String identity(SubmissionValidationResult submission) {
        return "| | |\n|---|---|\n"
                + "| Username | " + PlainLanguage.cell(submission.username()) + " |\n"
                + "| Submitted as | " + PlainLanguage.cell(submission.source().getFileName().toString()) + " |\n"
                + "| Name in the Java headers | "
                + PlainLanguage.cell(submission.author().name().orElse("none found")) + " |\n"
                + "| Email in the Java headers | "
                + PlainLanguage.cell(submission.author().email().orElse("none found")) + " |\n";
    }

    private static String structure(SubmissionValidationResult submission) {
        StringBuilder text = new StringBuilder();
        List<SubmissionFinding> issues = submission.findings().stream()
                .filter(finding -> !finding.corrected())
                .toList();
        if (issues.isEmpty()) {
            text.append("No unresolved problems were found in how the submission is arranged.\n\n");
        } else {
            text.append("Problems found:\n\n");
            for (SubmissionFinding finding : issues) {
                text.append("- **").append(finding.severity()).append(":** ").append(finding.message());
                finding.nextStep().ifPresent(step -> text.append(" *Next step:* ").append(step));
                text.append('\n');
            }
            text.append('\n');
        }
        List<FileCorrection> corrections = submission.corrections();
        if (corrections.isEmpty()) {
            text.append("No automatic corrections were needed.\n");
        } else {
            text.append("Automatic corrections (made in a copy; the original submission is unchanged):\n\n")
                    .append("| As submitted | After correction | Change |\n|---|---|---|\n");
            for (FileCorrection correction : corrections) {
                text.append("| ").append(PlainLanguage.code(correction.original()))
                        .append(" | ").append(PlainLanguage.code(correction.corrected()))
                        .append(" | ").append(PlainLanguage.cell(correction.change())).append(" |\n");
            }
        }
        return text.toString();
    }

    private static String questions(StudentOutcome student) {
        if (student.grading() == null) {
            return "This submission was not graded in this attempt, so there are no test results. "
                    + "Confirm the student's identity on the grading page, then grade them.\n";
        }
        StringBuilder text = new StringBuilder();
        for (AssessmentDefinition.Question question : AssessmentDefinition.QUESTIONS) {
            QuestionGradingResult result = student.grading().questions().stream()
                    .filter(candidate -> candidate.questionId().equals(question.id()))
                    .findFirst().orElse(null);
            text.append("### ").append(question.id()).append("\n\n");
            if (result == null) {
                text.append("No result was recorded for this question.\n\n");
                continue;
            }
            text.append(question(result)).append(feedback(student, question.id())).append('\n');
        }
        return text.toString();
    }

    // What went wrong in the question, each point at the line of the student's code it is about.
    private static String feedback(StudentOutcome student, String questionId) {
        List<Feedback> points = student.feedback().stream()
                .filter(feedback -> feedback.questionId().equals(questionId))
                .toList();
        if (points.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("\n**What went wrong:**\n\n");
        for (Feedback point : points) {
            text.append("- ");
            point.location().ifPresent(location -> text.append("**").append(location.label()).append("** (")
                    .append(PlainLanguage.inlineCode(location.code())).append("): "));
            text.append(point.message()).append('\n');
        }
        return text.toString();
    }

    private static String question(QuestionGradingResult result) {
        ProcessResult execution = result.execution();
        boolean timedOut = execution != null && execution.status() == ProcessResult.Status.TIMED_OUT;
        List<TestCaseResult> cases = execution == null ? List.of() : TestCaseResult.parse(execution.stdout(), timedOut);
        long passed = cases.stream().filter(test -> test.outcome() == TestCaseResult.Outcome.PASSED).count();
        StringBuilder text = new StringBuilder()
                .append("**").append(PlainLanguage.marks(result.score())).append(" marks**");
        if (!cases.isEmpty()) {
            text.append(", ").append(passed).append(" of ").append(cases.size()).append(" test cases passed");
        }
        text.append(". Result: ").append(PlainLanguage.status(result.status())).append(".\n\n");
        if (result.status() != QuestionGradingResult.Status.GRADED && result.message() != null) {
            text.append(result.message()).append("\n\n");
        }
        if (cases.isEmpty()) {
            text.append(execution == null
                    ? "No test case ran.\n"
                    : "The tester's output did not identify individual test cases.\n");
        } else {
            text.append("| # | Test case | Result | Expected | Actual | Note |\n|---|---|---|---|---|---|\n");
            for (TestCaseResult test : cases) {
                text.append("| ").append(test.number())
                        .append(" | ").append(PlainLanguage.code(test.description()))
                        .append(" | ").append(PlainLanguage.outcome(test.outcome()))
                        .append(" | ").append(PlainLanguage.code(test.expected().orElse("")))
                        .append(" | ").append(PlainLanguage.code(test.actual().orElse("")))
                        .append(" | ").append(PlainLanguage.cell(test.detail())).append(" |\n");
            }
        }
        ProcessResult compilation = result.compilation();
        if (compilation != null && !compilation.succeeded() && !compilation.stderr().isBlank()) {
            text.append("\nCompiler output (first ").append(MAX_COMPILER_LINES).append(" lines):\n\n```\n")
                    .append(GRADING_FOLDER.matcher(String.join("\n",
                            compilation.stderr().lines().limit(MAX_COMPILER_LINES).toList())).replaceAll(""))
                    .append("\n```\n");
        }
        return text.toString();
    }

    private static String breakdown(StudentOutcome student, PenaltySettings penalties) {
        ScoreBreakdown score = student.score();
        if (score == null) {
            return "No marks were calculated because the submission was not graded in this attempt.\n";
        }
        StringBuilder text = new StringBuilder("| Part | Marks |\n|---|---|\n");
        score.questionScores().forEach((question, marks) ->
                text.append("| ").append(question).append(" | ").append(PlainLanguage.marks(marks)).append(" |\n"));
        text.append("| **Tester marks** | **").append(PlainLanguage.marks(score.testerMarks())).append("** |\n")
                .append("| Style penalty | -").append(PlainLanguage.marks(score.stylePenalty())).append(" |\n")
                .append("| **Final mark** | **").append(PlainLanguage.marks(score.finalMark())).append("** |\n\n");
        if (penalties.none()) {
            text.append("No penalties are configured: style is reported only and does not change the mark.\n");
        } else {
            text.append("The style penalty is ").append(PlainLanguage.marks(penalties.stylePerViolation()))
                    .append(" per counted violation (").append(score.styleViolations())
                    .append(" counted), capped at ").append(PlainLanguage.marks(penalties.styleCap()))
                    .append(".\n");
        }
        return text.toString();
    }

    private static String style(StudentStyleResult style, StyleRules rules) {
        if (style == null) {
            return "Style was not checked in this attempt.\n";
        }
        StringBuilder text = new StringBuilder();
        if (style.score().isPresent()) {
            text.append("Style score: **").append(PlainLanguage.marks(style.score().getAsDouble())).append(" out of ")
                    .append(PlainLanguage.marks(style.maxScore())).append("**. Each violation deducts its rule's ")
                    .append("weight; a rule broken more than once on the same line counts once. Lines kept ")
                    .append("unchanged from the instructor's template are not checked.\n\n");
        } else {
            text.append("No style score: no file could be analysed.\n\n");
        }
        style.notes().forEach(note -> text.append("- ").append(note).append('\n'));
        if (!style.notes().isEmpty()) {
            text.append('\n');
        }
        style.files().forEach(file -> text.append(fileStyle(file)));
        text.append(uncheckedRules(rules));
        return text.toString();
    }

    // One file's style results: its violations as a table, or why it could not be analysed.
    private static String fileStyle(FileStyleResult file) {
        StringBuilder text = new StringBuilder("### ").append(file.fileName()).append("\n\n");
        if (!file.analysed()) {
            return text.append("**Could not analyse.** ").append(file.note()).append("\n\n").toString();
        }
        text.append(file.violations().size()).append(" violation(s); ").append(file.templateLinesKept())
                .append(" template line(s) not checked.");
        if (!file.note().isBlank()) {
            text.append(' ').append(file.note());
        }
        text.append("\n\n");
        if (!file.violations().isEmpty()) {
            text.append("| Line | Guide section | Problem | Code |\n|---|---|---|---|\n");
            for (StyleViolation violation : file.violations()) {
                text.append("| ").append(violation.line() == 0 ? "file" : violation.line())
                        .append(" | ").append(PlainLanguage.cell(violation.rule().section()))
                        .append(" | ").append(PlainLanguage.cell(violation.message()))
                        .append(violation.rule().heuristic() ? " *(heuristic: please confirm)*" : "")
                        .append(" | ").append(PlainLanguage.code(violation.code())).append(" |\n");
            }
            text.append('\n');
        }
        return text.toString();
    }

    // The guide rules that a person has to check, because no automatic check covers them.
    private static String uncheckedRules(StyleRules rules) {
        if (rules.unchecked().isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder(
                "### Not automatically checked\n\nThese guide rules need a person to check them:\n\n");
        for (StyleRules.UncheckedRule rule : rules.unchecked()) {
            text.append("- ").append(rule.section().isBlank() ? "" : "Section " + rule.section() + ": ")
                    .append(rule.text()).append('\n');
        }
        return text.toString();
    }
}
