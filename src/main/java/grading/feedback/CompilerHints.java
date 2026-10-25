package grading.feedback;

import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns javac's error messages into plain-language explanations of what to fix. */
final class CompilerHints {

    /** A kind of mistake and the explanation for it. */
    record Hint(String kind, String message) {
    }

    private static final Pattern CONVERSION = Pattern.compile("incompatible types: (.+) cannot be converted to (.+)");
    private static final Pattern LOSSY = Pattern.compile("possible lossy conversion from (\\S+) to (\\S+)");
    private static final Pattern UNREPORTED = Pattern.compile("unreported exception (\\S+);");
    private static final Pattern UNINITIALISED = Pattern.compile("variable (\\S+) might not have been initialized");
    private static final Pattern OPERATOR = Pattern.compile("bad operand types for binary operator '(.+)'");
    private static final Pattern EXPECTED = Pattern.compile("^'?(.+?)'? expected$");
    private static final Pattern WORD = Pattern.compile("[A-Za-z_$][\\w$]*");

    private CompilerHints() {
    }

    static Hint explain(JavacError error) {
        String message = error.message();
        if (message.startsWith("cannot find symbol")) {
            return missingSymbol(error);
        }
        Matcher conversion = CONVERSION.matcher(message);
        if (conversion.find()) {
            return new Hint("Incompatible types", "This value is " + withArticle(conversion.group(1)) + " but "
                    + withArticle(conversion.group(2)) + " is needed here. Convert it (for example `Integer.parseInt(...)` or "
                    + "`String.valueOf(...)`), or change the variable's type.");
        }
        Matcher lossy = LOSSY.matcher(message);
        if (lossy.find()) {
            return new Hint("Lossy conversion", "A value of type `" + lossy.group(1) + "` does not fit in "
                    + withArticle(lossy.group(2)) + " without losing information. Cast it, as in `(" + lossy.group(2) + ") value`, or use a wider "
                    + "type.");
        }
        Matcher unreported = UNREPORTED.matcher(message);
        if (unreported.find()) {
            return new Hint("Unhandled exception", "`" + unreported.group(1) + "` must be handled: put this call in "
                    + "a try/catch, or add `throws " + unreported.group(1) + "` to the method.");
        }
        Matcher uninitialised = UNINITIALISED.matcher(message);
        if (uninitialised.find()) {
            return new Hint("Variable without a value", "`" + uninitialised.group(1) + "` may be used before it "
                    + "gets a value. Give it a value where you declare it.");
        }
        Matcher operator = OPERATOR.matcher(message);
        if (operator.find()) {
            return new Hint("Wrong operand types", "`" + operator.group(1) + "` cannot be used on "
                    + withArticle(error.detail("first type").orElse("?")) + " and "
                    + withArticle(error.detail("second type").orElse("?")) + ". Convert or cast first; an `Object` must be cast, as in `(Integer) value`.");
        }
        if (message.startsWith("primitive patterns")) {
            return new Hint("instanceof with a primitive type", "`instanceof` needs a class type, not a primitive: "
                    + "write `instanceof Integer` rather than `instanceof int`.");
        }
        if (message.equals("missing return statement")) {
            return new Hint("Missing return", "Some way through the method ends without `return`. Make sure every "
                    + "path returns a value, for example with a `return` after the loop.");
        }
        if (message.startsWith("reached end of file while parsing")) {
            return new Hint("Unbalanced braces", "A closing `}` is missing: the braces `{ }` do not pair up.");
        }
        if (message.startsWith("class, interface, enum, or record expected")) {
            return new Hint("Code outside the class", "There is code outside the class. This is often an extra `}` "
                    + "that closes the class too early.");
        }
        if (message.contains("cannot be referenced from a static context")) {
            return new Hint("Static context", "A static method cannot use instance fields or methods directly. "
                    + "Make what it uses static, or call it on an object.");
        }
        if (message.contains("cannot be applied to given types")) {
            return new Hint("Wrong arguments", "The method is called with the wrong number or types of arguments. "
                    + "Compare the call with the method's parameters.");
        }
        if (message.startsWith("incomparable types")) {
            return new Hint("Incomparable types", "These two values cannot be compared with `==`. Compare objects "
                    + "with `.equals(...)` and make both sides the same type.");
        }
        if (message.equals("unreachable statement")) {
            return new Hint("Unreachable code", "This line can never run because it comes after a `return`, "
                    + "`break` or `continue`. Move or remove it.");
        }
        if (message.contains("is already defined")) {
            return new Hint("Declared twice", "This name is declared twice in the same scope. Rename or remove one.");
        }
        Matcher expected = EXPECTED.matcher(message);
        if (expected.matches()) {
            return new Hint("Syntax error", "Java expected `" + expected.group(1) + "` here. Check this line and the "
                    + "one before for a missing `;`, `)` or `}`.");
        }
        return new Hint("Compile error", "javac reported: " + message + ". Fix this line and compile again.");
    }

    private static Hint missingSymbol(JavacError error) {
        String symbol = error.detail("symbol").orElse("");
        String location = error.detail("location").orElse("");
        if (symbol.startsWith("variable ")) {
            String name = symbol.substring("variable ".length()).strip();
            if (error.code().matches(".*\\bfor\\s*\\(\\s*" + Pattern.quote(name) + "\\s*=.*")) {
                return new Hint("Loop variable not declared", "The loop variable `" + name + "` is never declared. "
                        + "Declare it in the loop: `for (int " + name + " = 0; ...)`.");
            }
            if (error.code().matches(".*\\.\\s*" + Pattern.quote(name) + "\\b(?!\\s*\\().*")) {
                return new Hint("Method used without brackets", "`" + name + "` is not a field. If it is a method, "
                        + "call it with brackets, as in `" + name + "(...)`, or pass the object itself.");
            }
            return new Hint("Undeclared variable", "`" + name + "` is used but not declared here: it is misspelt, or "
                    + "declared inside another block. Declare it before this line or check its spelling.");
        }
        if (symbol.startsWith("method ")) {
            String method = symbol.substring("method ".length()).strip();
            Matcher type = Pattern.compile("of type (\\S+)").matcher(location);
            if (type.find()) {
                return new Hint("Method does not exist", "`" + type.group(1) + "` has no method `" + method
                        + "`. Check the exact method name in the class you were given.");
            }
            return new Hint("Method does not exist", "There is no method `" + method + "` here. Check its spelling, "
                    + "or declare it.");
        }
        if (symbol.startsWith("class ")) {
            String name = symbol.substring("class ".length()).strip();
            return new Hint("Unknown class", similarName(name, error.code())
                    .map(match -> "There is no class `" + name + "`. Names are case-sensitive: did you mean `"
                            + match + "`?")
                    .orElse("There is no class `" + name + "`. Check its spelling, or add the import it needs."));
        }
        return new Hint("Unknown name", "javac cannot find `" + symbol + "`. Check its spelling and declaration.");
    }

    // "an `int`", "a `String`".
    private static String withArticle(String type) {
        return ("aeiouAEIOU".indexOf(type.charAt(0)) >= 0 ? "an `" : "a `") + type + "`";
    }

    // A word on the same line that differs only in letter case, such as ShapeComparator for shapeComparator.
    private static Optional<String> similarName(String name, String code) {
        return WORD.matcher(code).results()
                .map(MatchResult::group)
                .filter(word -> !word.equals(name) && word.equalsIgnoreCase(name))
                .findFirst();
    }
}
