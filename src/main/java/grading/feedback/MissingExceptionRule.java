package grading.feedback;

import grading.execution.TestCaseResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explains test cases that expected the method to throw an exception, such as a DataException for
 * an invalid file, when the method returned normally instead.
 */
final class MissingExceptionRule implements FeedbackRule {

    // The supplied testers print "Failed -> Expecting a Data Exception".
    private static final String NOT_THROWN = "Exception not thrown";
    private static final Pattern EXPECTING = Pattern.compile("(?i)expecting\\s+(?:an?\\s+)?(.+)");
    private static final Pattern CATCH = Pattern.compile("\\bcatch\\s*\\(");

    @Override
    public List<Feedback> apply(FeedbackContext context) {
        Map<String, List<TestCaseResult>> byException = new LinkedHashMap<>();
        for (TestCaseResult test : context.unexplainedFailures()) {
            Matcher expecting = EXPECTING.matcher(test.detail());
            if (expecting.find()) {
                String exception = expecting.group(1).replaceAll("\\s+", "").replaceAll("\\W", "");
                byException.computeIfAbsent(exception, key -> new ArrayList<>()).add(test);
            }
        }
        List<Feedback> feedback = new ArrayList<>();
        byException.forEach((exception, tests) -> {
            TestCaseResult first = tests.getFirst();
            String intro = FeedbackContext.testNumbers(tests) + " expected " + FeedbackContext.call(first)
                    + " to throw `" + exception + "`, but it returned normally. ";
            Optional<FeedbackContext.Located> method = context.methodUnderTest(first);
            feedback.add(method.map(located -> locate(context, located, exception, intro))
                    .orElse(new Feedback(context.questionId(), NOT_THROWN, intro + "Throw it with `throw new "
                            + exception + "(...)` for this case.", Optional.empty())));
            context.explain(tests);
        });
        return feedback;
    }

    // Points at the code that swallows the problem, the throw that is not reached, or the method itself.
    private static Feedback locate(FeedbackContext context, FeedbackContext.Located located, String exception,
            String intro) {
        SourceFile file = located.file();
        MethodSpan method = located.method();
        Optional<Integer> throwLine = file.firstLine(method,
                Pattern.compile("\\bthrow\\s+new\\s+" + Pattern.quote(exception) + "\\b"));
        if (throwLine.isPresent()) {
            return Feedback.at(context.questionId(), NOT_THROWN, intro + "This `throw` is not "
                    + "reached for that input: check the condition that leads to it.", file.at(throwLine.get()));
        }
        Optional<Integer> catchLine = file.firstLine(method, CATCH);
        if (catchLine.isPresent()) {
            return Feedback.at(context.questionId(), NOT_THROWN, intro + "This `catch` handles the "
                    + "problem itself; throw `new " + exception + "(...)` from it instead.", file.at(catchLine.get()));
        }
        return Feedback.at(context.questionId(), NOT_THROWN, intro + "Add `throw new " + exception
                + "(...)` for this case, and declare it with `throws " + exception + "`.", file.at(method.start()));
    }
}
