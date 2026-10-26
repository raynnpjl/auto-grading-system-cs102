package grading.execution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates the test cases in a tester's source so that a test case which never finishes can be
 * replaced by a failure and the tester re-run, as a marker would do by hand.
 *
 * <p>A test case is the outermost bare block or try statement that prints exactly one
 * {@code "Test N: ..."} header. This covers both layouts used by the supplied testers: one block
 * per test case, and one try statement per test case inside a shared block.
 */
final class TesterTestCases {

    static final String SKIPPED_VERDICT = "Failed -> Timed out";
    private static final Pattern HEADER_LITERAL = Pattern.compile("^Test\\s*(?:%d|\\d+)");
    private static final Pattern HEADER_LINE = Pattern.compile("^Test\\s*\\d+\\s*:");

    private final String source;
    private final List<Span> cases;

    private TesterTestCases(String source, List<Span> cases) {
        this.source = source;
        this.cases = List.copyOf(cases);
    }

    /** Returns the test cases, or empty when the tester's structure is not recognised. */
    static Optional<TesterTestCases> parse(String source) {
        Scanner scanner = new Scanner(source);
        if (!scanner.scan() || scanner.headers.isEmpty()) {
            return Optional.empty();
        }
        List<Span> cases = new ArrayList<>();
        for (int header : scanner.headers) {
            Span testCase = scanner.testCaseAround(header);
            if (testCase == null) {
                return Optional.empty();
            }
            if (!cases.contains(testCase)) {
                cases.add(testCase);
            }
        }
        cases.sort(Comparator.comparingInt(Span::start));
        return Optional.of(new TesterTestCases(source, cases));
    }

    /**
     * The tester source with the given 1-based test cases replaced by a timed-out failure that names
     * what the test case was calling, so reports can still describe it.
     */
    String withSkipped(Map<Integer, String> descriptions) {
        StringBuilder rewritten = new StringBuilder(source);
        for (int index = cases.size(); index >= 1; index--) {
            if (descriptions.containsKey(index)) {
                Span testCase = cases.get(index - 1);
                String description = javaText(descriptions.get(index));
                rewritten.replace(testCase.start(), testCase.end(), "{ System.out.println(\"Test case " + index
                        + " skipped: it did not finish within the time limit."
                        + (description.isEmpty() ? "" : " Test: " + description) + "\"); System.out.println(\""
                        + SKIPPED_VERDICT + "\"); }");
            }
        }
        return rewritten.toString();
    }

    /** What the most recently started test case was calling, from its "Test N: ..." line. */
    static String lastStartedDescription(String stdout) {
        String description = "";
        for (String line : stdout.lines().map(String::strip).toList()) {
            Matcher header = HEADER_LINE.matcher(line);
            if (header.find()) {
                description = line.substring(header.end()).strip();
            }
        }
        return description;
    }

    // Safe inside a Java string literal: no quotes, backslashes or control characters.
    private static String javaText(String text) {
        return text.replaceAll("[\\\\\"\\p{Cntrl}]", "'");
    }

    /**
     * Identifies the test case that was running when the tester was stopped: the last test case
     * that printed its header without a verdict. Skipped test cases print no header.
     */
    OptionalInt unfinishedCase(String stdout, Set<Integer> skipped) {
        int headers = 0;
        boolean finished = false;
        for (String line : stdout.lines().map(String::strip).toList()) {
            if (HEADER_LINE.matcher(line).find()) {
                headers++;
                finished = false;
            } else if (line.equals("Passed") || line.equals("Failed") || line.startsWith("Failed ->")) {
                finished = true;
            }
        }
        if (headers == 0 || finished) {
            return OptionalInt.empty();
        }
        int started = 0;
        for (int index = 1; index <= cases.size(); index++) {
            if (!skipped.contains(index) && ++started == headers) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    private record Span(int start, int end) {
    }

    /** Finds braces, string literals and comments without being confused by braces inside text. */
    private static final class Scanner {
        private final String text;
        private final boolean[] comment;
        private final boolean[] code;
        private final Map<Integer, Integer> closingBrace = new HashMap<>();
        private final List<Integer> headers = new ArrayList<>();

        private Scanner(String text) {
            this.text = text;
            this.comment = new boolean[text.length()];
            this.code = new boolean[text.length()];
        }

        private boolean scan() {
            Deque<Integer> openBraces = new ArrayDeque<>();
            int index = 0;
            while (index < text.length()) {
                char current = text.charAt(index);
                if (text.startsWith("//", index)) {
                    index = markComment(index, endOfLine(index));
                } else if (text.startsWith("/*", index)) {
                    int end = text.indexOf("*/", index + 2);
                    index = markComment(index, end < 0 ? text.length() : end + 2);
                } else if (text.startsWith("\"\"\"", index)) {
                    int end = text.indexOf("\"\"\"", index + 3);
                    index = end < 0 ? text.length() : end + 3;
                } else if (current == '"' || current == '\'') {
                    int end = endOfLiteral(index, current);
                    if (current == '"' && HEADER_LITERAL.matcher(text.substring(index + 1, end)).find()) {
                        headers.add(index);
                    }
                    index = end + 1;
                } else {
                    code[index] = true;
                    if (current == '{') {
                        openBraces.push(index);
                    } else if (current == '}') {
                        if (openBraces.isEmpty()) {
                            return false;
                        }
                        closingBrace.put(openBraces.pop(), index);
                    }
                    index++;
                }
            }
            return openBraces.isEmpty();
        }

        // Walks outwards from the header and keeps the widest block or try statement that still
        // contains no other test case's header.
        private Span testCaseAround(int header) {
            List<Integer> enclosing = closingBrace.keySet().stream()
                    .filter(open -> open < header && header < closingBrace.get(open))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            Span widest = null;
            for (int open : enclosing) {
                Span statement = statementOpenedBy(open);
                if (statement == null || headersWithin(statement) != 1) {
                    break;
                }
                widest = statement;
            }
            return widest;
        }

        private Span statementOpenedBy(int open) {
            int previous = previousSignificant(open - 1);
            if (previous < 0) {
                return null;
            }
            char before = text.charAt(previous);
            if (before == ';' || before == '{' || before == '}') {
                return new Span(open, closingBrace.get(open) + 1);
            }
            if (wordEndingAt(previous).equals("try")) {
                return new Span(previous - 2, endOfTryStatement(closingBrace.get(open)) + 1);
            }
            return null;
        }

        private int endOfTryStatement(int tryBlockEnd) {
            int end = tryBlockEnd;
            while (true) {
                int next = nextSignificant(end + 1);
                String word = wordStartingAt(next);
                int block;
                if (word.equals("catch")) {
                    int parameters = nextSignificant(next + word.length());
                    if (parameters < 0 || text.charAt(parameters) != '(') {
                        return end;
                    }
                    block = nextSignificant(closingParenthesis(parameters) + 1);
                } else if (word.equals("finally")) {
                    block = nextSignificant(next + word.length());
                } else {
                    return end;
                }
                if (block < 0 || !closingBrace.containsKey(block)) {
                    return end;
                }
                end = closingBrace.get(block);
            }
        }

        private long headersWithin(Span span) {
            return headers.stream().filter(header -> span.start() <= header && header < span.end()).count();
        }

        private int closingParenthesis(int open) {
            int depth = 0;
            for (int index = open; index < text.length(); index++) {
                if (!code[index]) {
                    continue;
                }
                if (text.charAt(index) == '(') {
                    depth++;
                } else if (text.charAt(index) == ')' && --depth == 0) {
                    return index;
                }
            }
            return text.length();
        }

        private int previousSignificant(int index) {
            while (index >= 0 && (comment[index] || Character.isWhitespace(text.charAt(index)))) {
                index--;
            }
            return index;
        }

        private int nextSignificant(int index) {
            while (index < text.length() && (comment[index] || Character.isWhitespace(text.charAt(index)))) {
                index++;
            }
            return index < text.length() ? index : -1;
        }

        private String wordEndingAt(int end) {
            int start = end;
            while (start >= 0 && code[start] && Character.isJavaIdentifierPart(text.charAt(start))) {
                start--;
            }
            return text.substring(start + 1, end + 1);
        }

        private String wordStartingAt(int start) {
            if (start < 0) {
                return "";
            }
            int end = start;
            while (end < text.length() && code[end] && Character.isJavaIdentifierPart(text.charAt(end))) {
                end++;
            }
            return text.substring(start, end);
        }

        private int markComment(int start, int end) {
            for (int index = start; index < end; index++) {
                comment[index] = true;
            }
            return end;
        }

        private int endOfLine(int index) {
            int end = text.indexOf('\n', index);
            return end < 0 ? text.length() : end;
        }

        private int endOfLiteral(int start, char quote) {
            int index = start + 1;
            while (index < text.length() && text.charAt(index) != quote && text.charAt(index) != '\n') {
                index += text.charAt(index) == '\\' ? 2 : 1;
            }
            return Math.min(index, text.length() - 1);
        }
    }
}
