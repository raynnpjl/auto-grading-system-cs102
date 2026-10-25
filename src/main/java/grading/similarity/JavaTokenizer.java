package grading.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Splits Java source into normalized tokens, so that changes that do not change the program, such
 * as renaming variables, changing literal values, reformatting or editing comments, do not hide
 * copying. Keywords and operators are kept; local names become {@code ID}; numbers, strings and
 * characters become {@code NUM}, {@code STR} and {@code CHR}. Names after a dot, such as the
 * {@code add} in {@code list.add(x)}, are kept, because renaming variables does not change them.
 * Imports and the lines kept from the template are left out. Code that does not compile is still
 * tokenized.
 */
final class JavaTokenizer {

    static final String IDENTIFIER = "ID";

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if",
            "implements", "instanceof", "int", "interface", "long", "new", "private", "protected", "public",
            "record", "return", "short", "static", "super", "switch", "this", "throw", "throws", "try", "var",
            "void", "while", "true", "false", "null");
    private static final List<String> OPERATORS = List.of(
            ">>>=", "<<=", ">>=", ">>>", "...", "->", "::", "++", "--", "&&", "||", "==", "!=", "<=", ">=",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>");

    private JavaTokenizer() {
    }

    /**
     * The tokens of one file.
     *
     * @param file         the file's name, kept on each token
     * @param lines        the file's lines
     * @param skippedLines 1-based lines to leave out, such as those kept from the template
     */
    static List<Token> tokenize(String file, List<String> lines, Set<Integer> skippedLines) {
        List<Token> tokens = new ArrayList<>();
        Lexer lexer = new Lexer(String.join("\n", lines));
        boolean inImport = false;
        String previous = "";
        while (lexer.hasNext()) {
            Lexer.Lexeme lexeme = lexer.next();
            if (lexeme.text().equals("import") || lexeme.text().equals("package")) {
                inImport = true;
            }
            if (inImport) {
                inImport = !lexeme.text().equals(";");
                continue;
            }
            String kind = normalize(lexeme, previous);
            previous = lexeme.text();
            if (!skippedLines.contains(lexeme.line())) {
                tokens.add(new Token(kind, file, lexeme.line()));
            }
        }
        return tokens;
    }

    private static String normalize(Lexer.Lexeme lexeme, String previous) {
        return switch (lexeme.type()) {
            case NUMBER -> "NUM";
            case STRING -> "STR";
            case CHARACTER -> "CHR";
            case WORD -> KEYWORDS.contains(lexeme.text()) || previous.equals(".") ? lexeme.text() : IDENTIFIER;
            case SYMBOL -> lexeme.text();
        };
    }

    /** Reads lexemes from source text, skipping spaces and comments. */
    private static final class Lexer {

        enum Type { WORD, NUMBER, STRING, CHARACTER, SYMBOL }

        record Lexeme(Type type, String text, int line) {
        }

        private final String text;
        private int position;
        private int line = 1;

        Lexer(String text) {
            this.text = text;
            skipSpaceAndComments();
        }

        boolean hasNext() {
            return position < text.length();
        }

        Lexeme next() {
            int start = position;
            int startLine = line;
            char c = text.charAt(position);
            Type type;
            if (Character.isJavaIdentifierStart(c)) {
                while (position < text.length() && Character.isJavaIdentifierPart(text.charAt(position))) {
                    position++;
                }
                type = Type.WORD;
            } else if (Character.isDigit(c) || (c == '.' && position + 1 < text.length()
                    && Character.isDigit(text.charAt(position + 1)))) {
                while (position < text.length() && (Character.isLetterOrDigit(text.charAt(position))
                        || text.charAt(position) == '.' || text.charAt(position) == '_')) {
                    position++;
                }
                type = Type.NUMBER;
            } else if (c == '"' || c == '\'') {
                skipQuoted(c);
                type = c == '"' ? Type.STRING : Type.CHARACTER;
            } else {
                position += OPERATORS.stream().filter(operator -> text.startsWith(operator, start))
                        .findFirst().map(String::length).orElse(1);
                type = Type.SYMBOL;
            }
            position = Math.min(position, text.length());
            Lexeme lexeme = new Lexeme(type, text.substring(start, position), startLine);
            skipSpaceAndComments();
            return lexeme;
        }

        // Strings and characters, including text blocks; a line break ends an unterminated one.
        private void skipQuoted(char quote) {
            boolean textBlock = text.startsWith("\"\"\"", position);
            position += textBlock ? 3 : 1;
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == '\\') {
                    position += 2;
                    continue;
                }
                if (textBlock ? text.startsWith("\"\"\"", position) : c == quote) {
                    position += textBlock ? 3 : 1;
                    return;
                }
                if (c == '\n') {
                    if (!textBlock) {
                        return;
                    }
                    line++;
                }
                position++;
            }
        }

        private void skipSpaceAndComments() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == '\n') {
                    line++;
                    position++;
                } else if (Character.isWhitespace(c)) {
                    position++;
                } else if (text.startsWith("//", position)) {
                    while (position < text.length() && text.charAt(position) != '\n') {
                        position++;
                    }
                } else if (text.startsWith("/*", position)) {
                    int end = text.indexOf("*/", position + 2);
                    int stop = end < 0 ? text.length() : end + 2;
                    line += (int) text.substring(position, stop).chars().filter(ch -> ch == '\n').count();
                    position = stop;
                } else {
                    return;
                }
            }
        }
    }
}
