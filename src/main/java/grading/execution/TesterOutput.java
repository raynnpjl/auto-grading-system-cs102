package grading.execution;

import java.util.List;
import java.util.OptionalDouble;

/**
 * What a tester printed: one verdict line per test case and its score as the last line.
 *
 * @param passed test cases that printed "Passed"
 * @param failed test cases that printed "Failed" or "Failed -> reason"
 * @param score  the last nonblank line when it is a finite, nonnegative number
 */
record TesterOutput(int passed, int failed, OptionalDouble score) {

    static TesterOutput parse(String stdout) {
        List<String> lines = stdout.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        int passed = (int) lines.stream().filter("Passed"::equals).count();
        int failed = (int) lines.stream().filter(line -> line.equals("Failed") || line.startsWith("Failed ->")).count();
        return new TesterOutput(passed, failed, lines.isEmpty() ? OptionalDouble.empty() : score(lines.getLast()));
    }

    private static OptionalDouble score(String line) {
        try {
            double value = Double.parseDouble(line);
            return Double.isFinite(value) && value >= 0 ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }
}
