package grading.feedback;

import grading.execution.TestCaseResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Explains test cases whose result was wrong by comparing the expected and actual values: an empty
 * or placeholder result, the same answer for every input, letter case, missing or extra items, the
 * wrong order, a number that is off, or text that is cut short.
 */
final class WrongOutputRule implements FeedbackRule {

    private static final Set<String> PLACEHOLDERS = Set.of("", "[]", "{}", "null", "0", "0.0", "-1", "-1.0");
    private static final int MAX_LISTED_ITEMS = 3;

    /** What is wrong with one test case's result. */
    record Diagnosis(String kind, String explanation) {
    }

    @Override
    public List<Feedback> apply(FeedbackContext context) {
        List<TestCaseResult> failed = context.unexplainedFailures().stream()
                .filter(test -> test.outcome() == TestCaseResult.Outcome.FAILED)
                .filter(test -> test.expected().isPresent() && test.actual().isPresent())
                .toList();
        if (failed.isEmpty()) {
            return List.of();
        }
        List<Feedback> feedback = new ArrayList<>(sameAnswerEveryTime(context, failed));
        // Test cases with the same kind of mistake are explained together, using the first as the example.
        Map<String, List<TestCaseResult>> byKind = new LinkedHashMap<>();
        for (TestCaseResult test : context.unexplainedFailures()) {
            if (failed.contains(test)) {
                byKind.computeIfAbsent(diagnose(test.expected().get(), test.actual().get()).kind(),
                        key -> new ArrayList<>()).add(test);
            }
        }
        byKind.forEach((kind, tests) -> {
            TestCaseResult first = tests.getFirst();
            Diagnosis diagnosis = diagnose(first.expected().get(), first.actual().get());
            String message = FeedbackContext.testNumbers(tests) + ": " + (tests.size() > 1 ? "for example, " : "")
                    + FeedbackContext.call(first) + " should give `" + first.expected().get() + "` but gave `"
                    + first.actual().get() + "`. " + diagnosis.explanation();
            feedback.add(new Feedback(context.questionId(), diagnosis.kind(), message,
                    context.methodUnderTest(first).map(located -> located.file().at(located.method().start()))));
            context.explain(tests);
        });
        return feedback;
    }

    // The same result for different inputs means the method ignores its input or never builds a result.
    private static List<Feedback> sameAnswerEveryTime(FeedbackContext context, List<TestCaseResult> failed) {
        Map<String, List<TestCaseResult>> byActual = new LinkedHashMap<>();
        failed.forEach(test -> byActual.computeIfAbsent(test.actual().get(), key -> new ArrayList<>()).add(test));
        List<Feedback> feedback = new ArrayList<>();
        byActual.forEach((actual, tests) -> {
            long differentExpected = tests.stream().map(test -> test.expected().get()).distinct().count();
            if (tests.size() < 2 || differentExpected < 2) {
                return;
            }
            boolean placeholder = PLACEHOLDERS.contains(actual.strip());
            String message = FeedbackContext.testNumbers(tests) + " all gave `" + actual + "` although they expect "
                    + "different answers. " + (placeholder
                            ? "The result is never filled in: check that the code that builds it is reached, for "
                                    + "example the condition inside your loop."
                            : "The method does not use its input to choose the answer: check that it uses every "
                                    + "parameter, such as the name it should look for.");
            Optional<SourceLocation> location = context.methodUnderTest(tests.getFirst()).map(located -> {
                List<Integer> returns = located.file().returns(located.method());
                return located.file().at(returns.size() == 1 ? returns.getFirst() : located.method().start());
            });
            feedback.add(new Feedback(context.questionId(), placeholder ? "Result never filled in"
                    : "Same result for every input", message, location));
            context.explain(tests);
        });
        return feedback;
    }

    static Diagnosis diagnose(String expected, String actual) {
        if (PLACEHOLDERS.contains(actual.strip()) && !PLACEHOLDERS.contains(expected.strip())) {
            return new Diagnosis("Empty result", "Nothing was found or added: check the condition that decides "
                    + "what goes into the result.");
        }
        if (expected.equalsIgnoreCase(actual)) {
            return new Diagnosis("Letter case", "Only the letter case differs: compare text with "
                    + "`equalsIgnoreCase`, or keep the case as it is in the data.");
        }
        if (expected.replaceAll("\\s+", "").equals(actual.replaceAll("\\s+", ""))) {
            return new Diagnosis("Spacing", "Only the spaces differ: check the spaces in your output and use "
                    + "`trim()` on values you read.");
        }
        Optional<Diagnosis> list = compareLists(expected, actual);
        if (list.isPresent()) {
            return list.get();
        }
        Optional<Diagnosis> number = compareNumbers(expected, actual);
        if (number.isPresent()) {
            return number.get();
        }
        if (!actual.isEmpty() && expected.startsWith(actual)) {
            return new Diagnosis("Incomplete result", "The end is missing (`" + expected.substring(actual.length())
                    + "`): check the format the question asks for.");
        }
        if (!expected.isEmpty() && actual.startsWith(expected)) {
            return new Diagnosis("Extra text", "There is extra text at the end (`" + actual.substring(expected.length())
                    + "`): return only what the question asks for.");
        }
        return new Diagnosis("Wrong result", "Work through this input by hand and compare each step with your code.");
    }

    private static Optional<Diagnosis> compareLists(String expected, String actual) {
        if (!isList(expected) || !isList(actual)) {
            return Optional.empty();
        }
        List<String> wanted = items(expected);
        List<String> got = items(actual);
        List<String> missing = new ArrayList<>(wanted);
        got.forEach(missing::remove);
        List<String> extra = new ArrayList<>(got);
        wanted.forEach(extra::remove);
        if (missing.isEmpty() && extra.isEmpty()) {
            return Optional.of(new Diagnosis("Wrong order", "The right items are there in the wrong order: check "
                    + "how you sort them, for example the comparator's return values."));
        }
        if (missing.isEmpty()) {
            return Optional.of(new Diagnosis("Extra items", "It kept items that should have been left out ("
                    + listed(extra) + "): check the condition that filters them."));
        }
        if (extra.isEmpty()) {
            return Optional.of(new Diagnosis("Missing items", "It left out items that belong in the result ("
                    + listed(missing) + "): check the condition that filters them."));
        }
        return Optional.of(new Diagnosis("Wrong items", "It is missing " + listed(missing) + " and has "
                + listed(extra) + " that do not belong: check the condition that chooses the items."));
    }

    private static Optional<Diagnosis> compareNumbers(String expected, String actual) {
        try {
            BigDecimal wanted = new BigDecimal(expected.strip());
            BigDecimal got = new BigDecimal(actual.strip());
            BigDecimal difference = got.subtract(wanted);
            return Optional.of(new Diagnosis("Wrong number", "It is off by " + difference.stripTrailingZeros()
                    .toPlainString() + ": check which values are counted and what you divide by."));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static boolean isList(String value) {
        String text = value.strip();
        return text.startsWith("[") && text.endsWith("]");
    }

    private static List<String> items(String list) {
        String inside = list.strip().substring(1, list.strip().length() - 1).strip();
        return inside.isEmpty() ? List.of() : Arrays.stream(inside.split(",\\s*")).map(String::strip).toList();
    }

    private static String listed(List<String> values) {
        List<String> shown = values.stream().limit(MAX_LISTED_ITEMS).map(value -> "`" + value + "`").toList();
        return String.join(", ", shown) + (values.size() > MAX_LISTED_ITEMS ? " and "
                + (values.size() - MAX_LISTED_ITEMS) + " more" : "");
    }
}
