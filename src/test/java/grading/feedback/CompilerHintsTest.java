package grading.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompilerHintsTest {

    @Test
    void explainsEachKnownMessageWithItsKind() {
        assertEquals("Incompatible types", kind("incompatible types: String cannot be converted to int"));
        assertEquals("Lossy conversion", kind("incompatible types: possible lossy conversion from double to int"));
        assertEquals("Unhandled exception",
                kind("unreported exception FileNotFoundException; must be caught or declared to be thrown"));
        assertEquals("Variable without a value", kind("variable total might not have been initialized"));
        assertEquals("instanceof with a primitive type",
                kind("primitive patterns are a preview feature and are disabled by default."));
        assertEquals("Missing return", kind("missing return statement"));
        assertEquals("Unbalanced braces", kind("reached end of file while parsing"));
        assertEquals("Code outside the class", kind("class, interface, enum, or record expected"));
        assertEquals("Static context", kind("non-static variable count cannot be referenced from a static context"));
        assertEquals("Wrong arguments", kind("method add in class Q1 cannot be applied to given types;"));
        assertEquals("Incomparable types", kind("incomparable types: int and String"));
        assertEquals("Unreachable code", kind("unreachable statement"));
        assertEquals("Declared twice", kind("variable i is already defined in method main(String[])"));
        assertEquals("Syntax error", kind("';' expected"));
        assertEquals("Compile error", kind("something javac rarely says"));
    }

    @Test
    void namesTheTypesInvolvedWithTheRightArticle() {
        CompilerHints.Hint conversion = hint("incompatible types: String cannot be converted to int", List.of());
        assertTrue(conversion.message().startsWith("This value is a `String` but an `int` is needed here."));

        CompilerHints.Hint operator = hint("bad operand types for binary operator '+'",
                List.of("first type:  int", "second type: Object"));
        assertTrue(operator.message().startsWith("`+` cannot be used on an `int` and an `Object`."));
    }

    @Test
    void suggestsTheClassNameThatDiffersOnlyInLetterCase() {
        JavacError error = new JavacError("Q3.java", 22, "cannot find symbol",
                "ShapeComparator comparator = new shapeComparator();", List.of("symbol:   class shapeComparator"));

        assertTrue(CompilerHints.explain(error).message().contains("did you mean `ShapeComparator`?"));
    }

    private static String kind(String message) {
        return hint(message, List.of()).kind();
    }

    private static CompilerHints.Hint hint(String message, List<String> details) {
        return CompilerHints.explain(new JavacError("Q1a.java", 1, message, "", details));
    }
}
