package grading.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One error reported by javac, read from its output.
 *
 * @param file     the file name, such as Q1a.java
 * @param line     the 1-based line in the compiled copy
 * @param message  the error message, such as "cannot find symbol"
 * @param code     the line of code javac quoted
 * @param details  javac's extra lines, such as "symbol: variable i"
 */
record JavacError(String file, int line, String message, String code, List<String> details) {

    private static final Pattern ERROR = Pattern.compile("^(?:.*[/\\\\])?([^/\\\\]+\\.java):(\\d+): error: (.*)$");
    private static final Pattern CARET = Pattern.compile("^\\s*\\^\\s*$");

    JavacError {
        details = List.copyOf(details);
    }

    /** The errors in javac's output, in the order it reported them. */
    static List<JavacError> parse(String stderr) {
        List<JavacError> errors = new ArrayList<>();
        Matcher current = null;
        String code = null;
        List<String> details = new ArrayList<>();
        for (String line : (stderr == null ? "" : stderr).lines().toList()) {
            Matcher error = ERROR.matcher(line);
            if (error.matches()) {
                add(errors, current, code, details);
                current = error;
                code = null;
                details = new ArrayList<>();
            } else if (current != null && code == null) {
                code = line.strip();
            } else if (current != null && !CARET.matcher(line).matches() && line.startsWith(" ")) {
                details.add(line.strip());
            }
        }
        add(errors, current, code, details);
        return List.copyOf(errors);
    }

    private static void add(List<JavacError> errors, Matcher error, String code, List<String> details) {
        if (error != null) {
            errors.add(new JavacError(error.group(1), Integer.parseInt(error.group(2)), error.group(3).strip(),
                    code == null ? "" : code, details));
        }
    }

    /** The value of a detail line such as "symbol:   variable i", or empty. */
    Optional<String> detail(String label) {
        return details.stream()
                .filter(detail -> detail.startsWith(label + ":"))
                .map(detail -> detail.substring(label.length() + 1).strip())
                .findFirst();
    }

    /** All of javac's text for this error, for matching type names. */
    String fullText() {
        return message + " " + String.join(" ", details);
    }
}
