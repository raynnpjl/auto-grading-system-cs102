package grading.execution;

import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * What a tester printed: a "Test N: ..." header and one verdict line per test case, and its score as
 * the last line.
 *
 * @param passed test cases that printed "Passed"
 * @param failed test cases that printed "Failed" or "Failed -> reason"
 * @param score  the last nonblank line when it is a finite, nonnegative number
 */
record TesterOutput(int passed, int failed, OptionalDouble score) {

    // "Test 2: ..." from the tester, or "Test case 2 skipped: ..." from a test case skipped after a timeout.
    private static final Pattern HEADER = Pattern.compile("^Test\\s*(?:case\\s*)?\\d+");

    static TesterOutput parse(String stdout) {
        List<String> lines = stdout.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        boolean hasHeaders = lines.stream().anyMatch(line -> HEADER.matcher(line).find());
        int passed = 0;
        int failed = 0;
        // Only the first verdict after each header counts, so a student's own "Passed" lines earn nothing.
        // A tester without headers has every verdict counted.
        boolean awaitingVerdict = !hasHeaders;
        for (String line : lines) {
            boolean isPass = line.equals("Passed");
            boolean isFail = line.equals("Failed") || line.startsWith("Failed ->");
            if (HEADER.matcher(line).find()) {
                awaitingVerdict = true;
            } else if ((isPass || isFail) && awaitingVerdict) {
                if (isPass) {
                    passed++;
                } else {
                    failed++;
                }
                awaitingVerdict = !hasHeaders;
            }
        }
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
