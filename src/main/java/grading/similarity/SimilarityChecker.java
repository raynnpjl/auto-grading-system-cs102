package grading.similarity;

import grading.assessment.AssessmentDefinition;
import grading.config.SimilaritySettings;
import grading.style.Templates;
import grading.submission.SubmissionValidationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares each question's code between every pair of students to find possible plagiarism. Each
 * answer is tokenized without the template's code, every pair is screened with winnowing, and the
 * pairs that pass are scored with Greedy String Tiling. Marks are never changed: pairs at or above
 * the threshold are reported, and flagged for a person to check when both answers are long enough
 * to be unlikely to match by chance.
 */
public final class SimilarityChecker {

    private final SimilaritySettings settings;
    private final Templates templates;
    private final SimilarityAlgorithm screening;
    private final SimilarityAlgorithm scoring;

    public SimilarityChecker(SimilaritySettings settings, Templates templates) {
        this.settings = settings;
        this.templates = templates;
        this.screening = new WinnowingSimilarity(settings.kgramSize(), settings.windowSize());
        this.scoring = new GreedyStringTiling(settings.minMatchTokens());
    }

    public SimilarityReport check(List<SubmissionValidationResult> submissions) {
        List<SimilarityPair> pairs = new ArrayList<>();
        int compared = 0;
        for (AssessmentDefinition.Question question : AssessmentDefinition.QUESTIONS) {
            List<SubmissionValidationResult> students = new ArrayList<>();
            List<List<Token>> answers = new ArrayList<>();
            for (SubmissionValidationResult submission : submissions) {
                List<Token> tokens = tokens(submission, question);
                if (!tokens.isEmpty()) {
                    students.add(submission);
                    answers.add(tokens);
                }
            }
            for (int i = 0; i < answers.size(); i++) {
                for (int j = i + 1; j < answers.size(); j++) {
                    compared++;
                    compare(question.id(), students.get(i).username(), answers.get(i), students.get(j).username(),
                            answers.get(j)).ifPresent(pairs::add);
                }
            }
        }
        pairs.sort(Comparator.comparingDouble(SimilarityPair::tiling).reversed()
                .thenComparing(SimilarityPair::questionId));
        return new SimilarityReport(pairs, settings, List.of(screening.name(), scoring.name()), compared, "");
    }

    private Optional<SimilarityPair> compare(String questionId, String firstName, List<Token> first,
            String secondName, List<Token> second) {
        Comparison screen = screening.compare(first, second);
        if (screen.score() < settings.screenThreshold()) {
            return Optional.empty();
        }
        Comparison score = scoring.compare(first, second);
        if (score.score() < settings.threshold()) {
            return Optional.empty();
        }
        boolean flagged = first.size() >= settings.minTokens() && second.size() >= settings.minTokens();
        return Optional.of(new SimilarityPair(questionId, firstName, secondName, screen.score(),
                score.score(), first.size(), second.size(), lines(first, score.matches(), true),
                lines(second, score.matches(), false), flagged));
    }

    /**
     * The student's tokens for one question, from the files as they submitted them, without the
     * template's code. Template code is removed by matching tokens rather than lines, so starter code
     * the student reformatted or renamed is still removed.
     */
    List<Token> tokens(SubmissionValidationResult submission, AssessmentDefinition.Question question) {
        List<Token> tokens = new ArrayList<>();
        for (Path source : submission.files(question).sources()) {
            String name = source.getFileName().toString();
            Path file = submission.originalSources().getOrDefault(name, source);
            try {
                List<Token> own = JavaTokenizer.tokenize(name, Files.readAllLines(file, StandardCharsets.UTF_8),
                        Set.of());
                tokens.addAll(withoutTemplate(own, templateTokens(name)));
            } catch (IOException exception) {
                // An unreadable file has no tokens, so it cannot match anything.
            }
        }
        return tokens;
    }

    private List<Token> templateTokens(String fileName) throws IOException {
        Optional<Path> template = templates.find(fileName);
        if (template.isEmpty()) {
            return List.of();
        }
        return JavaTokenizer.tokenize(fileName, Files.readAllLines(template.get(), StandardCharsets.UTF_8), Set.of());
    }

    // Drops the runs of the student's tokens that Greedy String Tiling matches in the template.
    private List<Token> withoutTemplate(List<Token> tokens, List<Token> template) {
        if (template.isEmpty()) {
            return tokens;
        }
        boolean[] fromTemplate = new boolean[tokens.size()];
        for (Comparison.Match match : scoring.compare(tokens, template).matches()) {
            for (int index = match.first(); index < match.first() + match.length(); index++) {
                fromTemplate[index] = true;
            }
        }
        List<Token> own = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (!fromTemplate[index]) {
                own.add(tokens.get(index));
            }
        }
        return own;
    }

    // The lines covered by matches on one side, merged into ranges per file.
    private static List<SimilarityPair.LineRange> lines(List<Token> tokens, List<Comparison.Match> matches,
            boolean firstSide) {
        Map<String, Set<Integer>> byFile = new TreeMap<>();
        for (Comparison.Match match : matches) {
            int start = firstSide ? match.first() : match.second();
            for (int index = start; index < start + match.length() && index < tokens.size(); index++) {
                Token token = tokens.get(index);
                byFile.computeIfAbsent(token.file(), file -> new TreeSet<>()).add(token.line());
            }
        }
        List<SimilarityPair.LineRange> ranges = new ArrayList<>();
        byFile.forEach((file, lineNumbers) -> {
            int from = -1;
            int to = -1;
            for (int line : lineNumbers) {
                if (from >= 0 && line > to + 1) {
                    ranges.add(new SimilarityPair.LineRange(file, from, to));
                    from = -1;
                }
                if (from < 0) {
                    from = line;
                }
                to = line;
            }
            if (from >= 0) {
                ranges.add(new SimilarityPair.LineRange(file, from, to));
            }
        });
        return ranges;
    }
}
