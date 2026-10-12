package grading.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One test case of a tester run, read from what the tester printed.
 *
 * @param number      the test case's position in the tester, starting at 1
 * @param description what the test case calls, as the tester printed it
 * @param outcome     how the test case ended
 * @param expected    the expected output, when the tester printed it
 * @param actual      the student's output, when the tester printed it
 * @param detail      a plain-language explanation, such as the reason a test case failed
 */
public record TestCaseResult(int number, String description, Outcome outcome, Optional<String> expected,
        Optional<String> actual, String detail) {

    private static final Pattern HEADER = Pattern.compile("^Test\\s*\\d+\\s*:\\s*(.*)$");
    private static final Pattern SKIPPED = Pattern.compile("^Test case \\d+ skipped\\b.*?(?: Test: (.*))?$");
    private static final Pattern EXPECTED = Pattern.compile("^Expected\\s*:\\s*(.*)$");
    private static final Pattern ACTUAL = Pattern.compile("^Actual\\s*:\\s*(.*)$");
    private static final String FAILED_WITH_REASON = "Failed ->";

    /** How a test case ended. */
    public enum Outcome {
        PASSED, FAILED, TIMED_OUT, DID_NOT_FINISH
    }

    /**
     * Reads the test cases from a tester's output. A test case that started but printed no result
     * is reported as timed out when the run was stopped for taking too long, and otherwise as not
     * finished.
     */
    public static List<TestCaseResult> parse(String stdout, boolean timedOut) {
        List<TestCaseResult> results = new ArrayList<>();
        Builder current = null;
        for (String line : (stdout == null ? "" : stdout).lines().map(String::strip).toList()) {
            Matcher header = HEADER.matcher(line);
            Matcher skipped = SKIPPED.matcher(line);
            if (header.matches() || skipped.matches()) {
                finish(current, results, timedOut);
                String description = header.matches() ? header.group(1) : skipped.group(1);
                current = new Builder(results.size() + 1, description == null ? "" : description);
                if (!header.matches()) {
                    current.outcome = Outcome.TIMED_OUT;
                    current.detail = "Did not finish within the time limit, so it was skipped and earned no marks.";
                }
                continue;
            }
            Matcher expected = EXPECTED.matcher(line);
            Matcher actual = ACTUAL.matcher(line);
            if (current != null && expected.matches()) {
                current.expected = unwrap(expected.group(1));
            } else if (current != null && actual.matches()) {
                current.actual = unwrap(actual.group(1));
            } else if (line.equals("Passed") || line.equals("Failed") || line.startsWith(FAILED_WITH_REASON)) {
                if (current == null) {
                    current = new Builder(results.size() + 1, "");
                }
                if (current.outcome == null) {
                    current.outcome = line.equals("Passed") ? Outcome.PASSED : Outcome.FAILED;
                    current.detail = line.startsWith(FAILED_WITH_REASON)
                            ? line.substring(FAILED_WITH_REASON.length()).strip()
                            : "";
                }
                finish(current, results, timedOut);
                current = null;
            }
        }
        finish(current, results, timedOut);
        return List.copyOf(results);
    }

    private static void finish(Builder builder, List<TestCaseResult> results, boolean timedOut) {
        if (builder == null) {
            return;
        }
        if (builder.outcome == null) {
            builder.outcome = timedOut ? Outcome.TIMED_OUT : Outcome.DID_NOT_FINISH;
            builder.detail = timedOut
                    ? "Was still running when the time limit was reached."
                    : "Started but printed no result; the tester stopped before it finished.";
        }
        results.add(new TestCaseResult(builder.number, builder.description, builder.outcome,
                Optional.ofNullable(builder.expected), Optional.ofNullable(builder.actual), builder.detail));
    }

    // The supplied testers print values between bars, as in |[cat, dog]|.
    private static String unwrap(String value) {
        String text = value.strip();
        if (text.length() >= 2 && text.startsWith("|") && text.endsWith("|")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static final class Builder {
        private final int number;
        private final String description;
        private Outcome outcome;
        private String expected;
        private String actual;
        private String detail = "";

        private Builder(int number, String description) {
            this.number = number;
            this.description = description;
        }
    }
}
