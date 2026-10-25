package grading.feedback;

import grading.execution.QuestionGradingResult;
import grading.execution.TestCaseResult;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Explains a tester that was stopped because the student's code printed too much. */
final class OutputLimitRule implements FeedbackRule {

    private static final Pattern PRINT = Pattern.compile("\\bSystem\\.(?:out|err)\\.print");

    @Override
    public List<Feedback> apply(FeedbackContext context) {
        if (context.result().status() != QuestionGradingResult.Status.OUTPUT_LIMIT_EXCEEDED) {
            return List.of();
        }
        List<TestCaseResult> tests = context.unexplainedFailures();
        String message = "The tester was stopped because your code printed too much output. This is usually a "
                + "print statement inside a loop that never ends: remove debugging prints and check the loop.";
        Optional<SourceLocation> location = tests.stream()
                .map(context::methodUnderTest)
                .flatMap(Optional::stream)
                .findFirst()
                .flatMap(located -> located.file().firstLine(located.method(), PRINT).map(located.file()::at));
        context.explain(tests);
        return List.of(new Feedback(context.questionId(), "Too much output", message, location));
    }
}
