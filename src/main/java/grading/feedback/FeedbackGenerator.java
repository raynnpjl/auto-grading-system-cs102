package grading.feedback;

import grading.assessment.AssessmentDefinition;
import grading.execution.ProcessResult;
import grading.execution.QuestionGradingResult;
import grading.execution.StudentGradingResult;
import grading.execution.TestCaseResult;
import grading.style.Templates;
import grading.submission.SubmissionValidationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes feedback on each question a student did not get full marks for, explaining the mistake at
 * the line of their code where it is. The rules run from the most specific to the most general, and
 * each test case is explained once.
 */
public final class FeedbackGenerator {

    private static final String CLASS_SUFFIX = ".class";

    private final Templates templates;
    private final List<FeedbackRule> rules = List.of(
            new CompilationErrorRule(),
            new StarterCodeRule(),
            new MissingExceptionRule(),
            new RuntimeExceptionRule(),
            new TimeoutRule(),
            new OutputLimitRule(),
            new WrongOutputRule());

    /** @param templates the template sources, so code kept from the template is recognised */
    public FeedbackGenerator(Templates templates) {
        this.templates = templates;
    }

    /** The feedback for one student's graded questions, in assessment order; empty when nothing went wrong. */
    public List<Feedback> forStudent(SubmissionValidationResult submission, StudentGradingResult grading) {
        List<Feedback> feedback = new ArrayList<>();
        for (QuestionGradingResult result : grading.questions()) {
            AssessmentDefinition.QUESTIONS.stream()
                    .filter(question -> question.id().equals(result.questionId()))
                    .findFirst()
                    .ifPresent(question -> feedback.addAll(forQuestion(submission, question, result)));
        }
        return List.copyOf(feedback);
    }

    private List<Feedback> forQuestion(SubmissionValidationResult submission, AssessmentDefinition.Question question,
            QuestionGradingResult result) {
        ProcessResult execution = result.execution();
        boolean timedOut = execution != null && execution.status() == ProcessResult.Status.TIMED_OUT;
        List<TestCaseResult> cases = execution == null ? List.of() : TestCaseResult.parse(execution.stdout(), timedOut);
        boolean fullMarks = result.status() == QuestionGradingResult.Status.GRADED
                && cases.stream().allMatch(test -> test.outcome() == TestCaseResult.Outcome.PASSED);
        if (fullMarks || result.status() == QuestionGradingResult.Status.MISSING_FILES) {
            return List.of();
        }
        List<SourceFile> sources = new ArrayList<>();
        Map<String, List<String>> compiled = new HashMap<>();
        for (Path file : submission.files(question).sources()) {
            String name = file.getFileName().toString();
            List<String> compiledLines = readLines(file);
            List<String> submitted = submission.originalSources().containsKey(name)
                    ? readLines(submission.originalSources().get(name))
                    : compiledLines;
            sources.add(new SourceFile(name, submitted, keptLines(name, submitted)));
            compiled.put(name, compiledLines);
        }
        List<String> givenClasses = question.supportClasses().stream()
                .map(name -> name.endsWith(CLASS_SUFFIX) ? name.substring(0, name.length() - CLASS_SUFFIX.length())
                        : name)
                .toList();
        FeedbackContext context = new FeedbackContext(result, cases, sources, givenClasses, compiled);
        List<Feedback> feedback = new ArrayList<>();
        for (FeedbackRule rule : rules) {
            feedback.addAll(rule.apply(context));
        }
        return feedback;
    }

    private Set<Integer> keptLines(String name, List<String> lines) {
        try {
            return templates.keptLines(name, lines);
        } catch (IOException exception) {
            return Set.of();
        }
    }

    // A file that cannot be read gives no lines; the feedback then has no line numbers for it.
    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return List.of();
        }
    }
}
