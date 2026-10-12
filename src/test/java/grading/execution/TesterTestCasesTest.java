package grading.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TesterTestCasesTest {

    // The layout of Q1bTester to Q3Tester: one try statement per test case inside a shared block.
    private static final String SHARED_BLOCK_TESTER = """
            public class Q2bTester {
                private static double score;
                public static void main(String[] args) {
                    int tcNum = 1;
                    {
                        try {
                            System.out.printf("Test %d: first%n", tcNum++);
                            String braces = "{ not a block }"; // nor is this {
                            throw new IllegalStateException();
                        } catch (IllegalStateException ex) {
                            System.out.println("Passed");
                            score += 1;
                        } catch (Exception e) {
                            System.out.println("Failed -> Exception");
                        }
                        System.out.println("-------");
                        try {
                            System.out.printf("Test %d: second%n", tcNum++);
                            while (true) {}
                        } catch (Exception e) {
                            System.out.println("Failed -> Exception");
                        } finally {
                            /* } */
                        }
                        try {
                            System.out.printf("Test %d: third%n", tcNum++);
                            score += 1;
                            System.out.println("Passed");
                        } catch (Exception e) {
                            System.out.println("Failed -> Exception");
                        }
                    }
                    System.out.println(score);
                }
            }
            """;

    @TempDir
    java.nio.file.Path temporary;

    @Test
    void findsOneBlockPerTestCaseInTheSuppliedTester() throws Exception {
        String source;
        try (InputStream input = getClass().getResourceAsStream("/grading/execution/sample/Q1aTester.java")) {
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        TesterTestCases testCases = TesterTestCases.parse(source).orElseThrow();

        assertEquals(3, testCases.count());
        String rewritten = testCases.withSkipped(Set.of(2));
        assertTrue(rewritten.contains("Test case 2 skipped"));
        assertFalse(rewritten.contains("\"ellen\""));
        assertTrue(rewritten.contains("\"alice\"") && rewritten.contains("\"Daniel\""));
    }

    @Test
    void findsTryStatementsInsideASharedBlockAndKeepsTheRewrittenTesterCompilable() throws Exception {
        TesterTestCases testCases = TesterTestCases.parse(SHARED_BLOCK_TESTER).orElseThrow();

        assertEquals(3, testCases.count());
        String rewritten = testCases.withSkipped(Set.of(2));
        assertFalse(rewritten.contains("second"));
        assertTrue(rewritten.contains("first") && rewritten.contains("third"));
        var file = java.nio.file.Files.writeString(temporary.resolve("Q2bTester.java"), rewritten);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", temporary.toString(), file.toString()));
    }

    @Test
    void identifiesTheTestCaseThatStartedWithoutAVerdict() {
        TesterTestCases testCases = TesterTestCases.parse(SHARED_BLOCK_TESTER).orElseThrow();

        assertEquals(OptionalInt.of(2), testCases.unfinishedCase(
                "Test 1: first\nPassed\n-------\nTest 2: second\n", Set.of()));
        // With case 2 skipped, the next header printed belongs to case 3.
        assertEquals(OptionalInt.of(3), testCases.unfinishedCase(
                "Test 1: first\nPassed\nTest case 2 skipped: it did not finish\nFailed -> Timed out\n"
                        + "Test 2: third\n", Set.of(2)));
        assertEquals(OptionalInt.empty(), testCases.unfinishedCase("Test 1: first\nPassed\n", Set.of()));
        assertEquals(OptionalInt.empty(), testCases.unfinishedCase("", Set.of()));
    }

    @Test
    void rejectsTestersWithoutRecognisableTestCases() {
        assertTrue(TesterTestCases.parse("public class T { public static void main(String[] a) {"
                + " System.out.println(1); } }").isEmpty());
        assertTrue(TesterTestCases.parse("public class T { void m() { for (;;) {"
                + " System.out.printf(\"Test %d: loop%n\", 1); } } }").isEmpty());
        assertTrue(TesterTestCases.parse("public class T { { \"Test 1:\" } ").isEmpty());
    }
}
