package grading.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import grading.assessment.QuestionFiles;
import grading.config.SimilaritySettings;
import grading.style.Templates;
import grading.submission.SubmissionValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimilarityCheckerTest {

    private static final SimilaritySettings SETTINGS = new SimilaritySettings(0.8, 0.5, 40, 5, 4, 8);

    private static final String TEMPLATE = """
            import java.util.*;

            public class Q1a {
                public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
                    // insert your code here.
                    return new ArrayList<String>(); // to make this code compile.
                }

                // Do not modify the following codes
                public static void main(String[] args) {
                    ArrayList<String> inputs = new ArrayList<>();
                    inputs.add("alice");
                    System.out.println(getIsogramWords(inputs));
                }
            }
            """;

    private static final String ORIGINAL = """
            import java.util.*;

            public class Q1a {
                public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
                    ArrayList<String> result = new ArrayList<>();
                    for (String word : inputs) {
                        Set<Character> seen = new HashSet<>();
                        boolean isogram = true;
                        for (char letter : word.toLowerCase().toCharArray()) {
                            if (!seen.add(letter)) {
                                isogram = false;
                                break;
                            }
                        }
                        if (isogram) {
                            result.add(word);
                        }
                    }
                    return result;
                }

                // Do not modify the following codes
                public static void main(String[] args) {
                    ArrayList<String> inputs = new ArrayList<>();
                    inputs.add("alice");
                    System.out.println(getIsogramWords(inputs));
                }
            }
            """;

    // The same code with every variable renamed, different layout and comments.
    private static final String DISGUISED_COPY = """
            import java.util.*;

            public class Q1a {
              /* my own work */
              public static ArrayList<String> getIsogramWords(ArrayList<String> inputs)
              {
                ArrayList<String> answer = new ArrayList<>();
                for (String w : inputs) {
                  Set<Character> letters = new HashSet<>(); boolean ok = true;
                  for (char c : w.toLowerCase().toCharArray()) {
                    if (!letters.add(c)) { ok = false; break; }
                  }
                  if (ok) { answer.add(w); }
                }
                return answer;
              }

              // Do not modify the following codes
              public static void main(String[] args) {
                ArrayList<String> inputs = new ArrayList<>();
                inputs.add("alice");
                System.out.println(getIsogramWords(inputs));
              }
            }
            """;

    private static final String DIFFERENT_SOLUTION = """
            import java.util.*;

            public class Q1a {
                public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
                    ArrayList<String> out = new ArrayList<>();
                    int index = 0;
                    while (index < inputs.size()) {
                        String lower = inputs.get(index).toLowerCase();
                        int distinct = (int) lower.chars().distinct().count();
                        if (distinct == lower.length()) {
                            out.add(inputs.get(index));
                        }
                        index++;
                    }
                    return out;
                }
            }
            """;

    @TempDir
    Path temporary;

    @Test
    void renamingVariablesReformattingAndCommentsDoNotChangeTheTokens() {
        List<String> original = kinds(ORIGINAL);
        List<String> copy = kinds(DISGUISED_COPY);

        assertEquals(original, copy);
        assertTrue(original.contains("add"), "names after a dot are kept");
        assertFalse(original.contains("result"), "local names are replaced by ID");
    }

    @Test
    void flagsADisguisedCopyAtFullSimilarityAndPointsAtTheMatchingLines() throws IOException {
        SimilarityReport report = check(Map.of("alice.2026", ORIGINAL, "bob.2026", DISGUISED_COPY));

        assertEquals(1, report.pairs().size());
        SimilarityPair pair = report.pairs().getFirst();
        assertEquals(100, pair.percent());
        assertTrue(pair.flagged());
        List<SimilarityPair.LineRange> aliceLines =
                pair.first().equals("alice.2026") ? pair.firstLines() : pair.secondLines();
        assertEquals(List.of(new SimilarityPair.LineRange("Q1a.java", 5, 19)), aliceLines);
        assertEquals(1, report.flaggedFor("bob.2026").size());
        assertEquals("Q1a.java lines 5–19; Q3.java lines 8, 10–12", SimilarityPair.describe(List.of(
                new SimilarityPair.LineRange("Q1a.java", 5, 19), new SimilarityPair.LineRange("Q3.java", 8, 8),
                new SimilarityPair.LineRange("Q3.java", 10, 12))));
    }

    @Test
    void doesNotReportADifferentSolutionOrTheSharedTemplateCode() throws IOException {
        SimilarityReport report = check(Map.of("alice.2026", ORIGINAL, "carol.2026", DIFFERENT_SOLUTION));

        assertTrue(report.pairs().isEmpty(), report.pairs().toString());
        assertEquals(1, report.compared());
    }

    @Test
    void listsButDoesNotFlagIdenticalAnswersThatAreTooShort() throws IOException {
        String shortAnswer = TEMPLATE.replace("// insert your code here.", "inputs.clear();");
        SimilarityReport report = check(Map.of("alice.2026", shortAnswer, "bob.2026", shortAnswer));

        assertTrue(report.pairs().isEmpty() || !report.pairs().getFirst().flagged());
        assertTrue(report.flaggedFor("alice.2026").isEmpty());
    }

    @Test
    void greedyStringTilingMatchesMethodsThatWereMovedAround() {
        List<Token> first = JavaTokenizer.tokenize("A.java", List.of(
                "int a() { int x = 1; while (x < 10) { x = x * 2; } return x; }",
                "String b(String s) { return s.trim().toLowerCase().strip(); }"), Set.of());
        List<Token> moved = JavaTokenizer.tokenize("A.java", List.of(
                "String b(String s) { return s.trim().toLowerCase().strip(); }",
                "int a() { int x = 1; while (x < 10) { x = x * 2; } return x; }"), Set.of());

        assertEquals(1.0, new GreedyStringTiling(8).compare(first, moved).score(), 1e-9);
        assertEquals(1.0, new WinnowingSimilarity(5, 4).compare(first, first).score(), 1e-9);
    }

    private SimilarityReport check(Map<String, String> answers) throws IOException {
        Path templates = Files.createDirectories(temporary.resolve("template"));
        Files.writeString(templates.resolve("Q1a.java"), TEMPLATE);
        List<SubmissionValidationResult> submissions = answers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(answer -> submission(answer.getKey(), answer.getValue()))
                .toList();
        return new SimilarityChecker(SETTINGS, new Templates(templates)).check(submissions);
    }

    private SubmissionValidationResult submission(String username, String code) {
        try {
            Path file = Files.createDirectories(temporary.resolve(username)).resolve("Q1a.java");
            Files.writeString(file, code);
            return new SubmissionValidationResult(file.getParent(), username, file.getParent(),
                    Map.of("Q1a", new QuestionFiles(List.of(file), List.of(), List.of())), List.of());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> kinds(String code) {
        return JavaTokenizer.tokenize("Q1a.java", code.lines().toList(), Set.of()).stream().map(Token::kind).toList();
    }
}
