package grading.feedback;

import grading.execution.QuestionGradingResult;
import grading.execution.TestCaseResult;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explains test cases that never finished, by finding the loop in the tested method that cannot
 * end: a scanner that is not moved forward on every pass, or a counter the loop never changes.
 */
final class TimeoutRule implements FeedbackRule {

    private static final Pattern SCANNER_LOOP = Pattern.compile("\\bwhile\\s*\\(\\s*(\\w+)\\.hasNext\\w*\\s*\\(");
    private static final Pattern COUNTER_LOOP = Pattern.compile("\\bwhile\\s*\\(\\s*(\\w+)\\s*(?:<=?|>=?|!=)");

    @Override
    public List<Feedback> apply(FeedbackContext context) {
        List<TestCaseResult> tests = context.unexplainedFailures().stream()
                .filter(test -> test.outcome() == TestCaseResult.Outcome.TIMED_OUT
                        || test.outcome() == TestCaseResult.Outcome.DID_NOT_FINISH)
                .toList();
        boolean timedOut = context.result().status() == QuestionGradingResult.Status.EXECUTION_TIMEOUT;
        if (tests.isEmpty() && !timedOut) {
            return List.of();
        }
        String intro = tests.isEmpty()
                ? "The tester did not finish within the time limit. "
                : FeedbackContext.testNumbers(tests) + " did not finish within the time limit. ";
        Optional<FeedbackContext.Located> method = tests.isEmpty()
                ? Optional.empty()
                : context.methodUnderTest(tests.getFirst());
        context.explain(tests);
        if (method.isEmpty()) {
            return List.of(new Feedback(context.questionId(), "Infinite loop", intro + "A loop probably never ends: "
                    + "check that each loop's condition eventually becomes false.", Optional.empty()));
        }
        return List.of(explainLoop(context, method.get(), intro));
    }

    private static Feedback explainLoop(FeedbackContext context, FeedbackContext.Located located, String intro) {
        SourceFile file = located.file();
        List<Integer> loops = file.loops(located.method());
        for (int loop : loops) {
            Optional<String> reason = scannerNotAdvanced(file, loop).or(() -> counterNotChanged(file, loop));
            if (reason.isPresent()) {
                return Feedback.at(context.questionId(), "Infinite loop", intro + reason.get(), file.at(loop));
            }
        }
        if (!loops.isEmpty()) {
            return Feedback.at(context.questionId(), "Infinite loop", intro + "Check this loop: something inside it "
                    + "must eventually make its condition false.", file.at(loops.getFirst()));
        }
        Pattern selfCall = Pattern.compile("\\b" + Pattern.quote(located.method().name()) + "\\s*\\(");
        Optional<Integer> recursion = file.firstLine(
                new MethodSpan(located.method().name(), located.method().start() + 1, located.method().end()),
                selfCall);
        return recursion
                .map(line -> Feedback.at(context.questionId(), "Infinite recursion", intro + "The method calls "
                        + "itself here without reaching a case that stops: check the base case.", file.at(line)))
                .orElse(Feedback.at(context.questionId(), "Infinite loop", intro + "The method never returned: "
                        + "check the loops in the methods it calls.", file.at(located.method().start())));
    }

    // while (sc.hasNext...()) must call sc.next...() on every pass, not only inside an if.
    private static Optional<String> scannerNotAdvanced(SourceFile file, int loop) {
        Matcher scanner = SCANNER_LOOP.matcher(file.code(loop));
        if (!scanner.find()) {
            return Optional.empty();
        }
        String name = scanner.group(1);
        Pattern advance = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\.\\s*next\\w*\\s*\\(");
        int shallowest = shallowestDepth(file, loop, advance);
        if (shallowest == 1) {
            return Optional.empty();
        }
        return Optional.of(shallowest == Integer.MAX_VALUE
                ? "This loop never reads from `" + name + "` (no `" + name + ".next...()` inside it), so `" + name
                        + ".hasNext...()` stays true for ever. Read the next value on every pass."
                : "`" + name + "` is only moved forward inside an `if` in this loop. When the `if` is false, "
                        + "nothing is read and the loop repeats for ever. Read the next line on every pass, before "
                        + "the `if`.");
    }

    // while (i < n) must change i inside the loop.
    private static Optional<String> counterNotChanged(SourceFile file, int loop) {
        Matcher counter = COUNTER_LOOP.matcher(file.code(loop));
        if (!counter.find()) {
            return Optional.empty();
        }
        String name = counter.group(1);
        Pattern change = Pattern.compile("(\\+\\+|--)\\s*" + Pattern.quote(name) + "\\b|\\b" + Pattern.quote(name)
                + "\\s*(\\+\\+|--|[-+*/]?=(?!=))");
        return shallowestDepth(file, loop, change) == Integer.MAX_VALUE
                ? Optional.of("`" + name + "` never changes inside this loop, so its condition never becomes false. "
                        + "Update `" + name + "` on every pass.")
                : Optional.empty();
    }

    // How deeply nested the shallowest match is in the loop's body: 1 is directly in the body.
    private static int shallowestDepth(SourceFile file, int loop, Pattern pattern) {
        int end = file.blockEnd(loop);
        int depth = 0;
        int shallowest = Integer.MAX_VALUE;
        for (int line = loop; line <= end; line++) {
            String code = SourceFile.withoutStrings(SourceFile.withoutComment(file.lines().get(line - 1)));
            if (line > loop && depth >= 1 && pattern.matcher(code).find()) {
                shallowest = Math.min(shallowest, depth);
            }
            for (char c : code.toCharArray()) {
                depth += c == '{' ? 1 : c == '}' ? -1 : 0;
            }
        }
        return shallowest;
    }
}
