package grading.style;

import grading.common.Messages;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The style guide rules behind each check: the guide section it enforces, the rule in plain
 * language and its weight, plus the guide rules that are not checked automatically. Read from the
 * style rules file named in the grading settings.
 */
public final class StyleRules {

    /** Weight of a check that the rules file does not describe. */
    static final double DEFAULT_WEIGHT = 1;

    private static final String UNCHECKED_PREFIX = "unchecked.";
    private static final String SECTION_SUFFIX = ".section";
    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");

    private final Map<String, Rule> rules;
    private final List<UncheckedRule> unchecked;
    private final Optional<String> problem;

    private StyleRules(Map<String, Rule> rules, List<UncheckedRule> unchecked, Optional<String> problem) {
        this.rules = rules;
        this.unchecked = unchecked;
        this.problem = problem;
    }

    /** Never fails: a file that cannot be read gives no rule details and a problem to report. */
    public static StyleRules load(Path file) {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
        } catch (IOException | RuntimeException exception) {
            return new StyleRules(Map.of(), List.of(), Optional.of("The style rules file " + file
                    + " could not be read (" + Messages.describe(exception) + "); rules are shown by their check"
                    + " name and every violation has weight " + DEFAULT_WEIGHT + "."));
        }
        Map<String, Rule> rules = new LinkedHashMap<>();
        values.stringPropertyNames().stream()
                .filter(key -> key.endsWith(SECTION_SUFFIX))
                .map(key -> key.substring(0, key.length() - SECTION_SUFFIX.length()))
                .sorted()
                .forEach(id -> rules.put(id, new Rule(id,
                        values.getProperty(id + SECTION_SUFFIX).strip(),
                        values.getProperty(id + ".rule", id).strip(),
                        weight(values.getProperty(id + ".weight")),
                        Boolean.parseBoolean(values.getProperty(id + ".heuristic", "false").strip()))));
        List<UncheckedRule> unchecked = new ArrayList<>();
        values.stringPropertyNames().stream()
                .filter(key -> key.startsWith(UNCHECKED_PREFIX))
                .sorted(Comparator.comparingInt(StyleRules::uncheckedNumber))
                .forEach(key -> unchecked.add(UncheckedRule.parse(values.getProperty(key))));
        return new StyleRules(Map.copyOf(rules), List.copyOf(unchecked), Optional.empty());
    }

    /** The rule behind a check id; a check the file does not describe is shown by its id. */
    public Rule rule(String checkId) {
        String id = checkId == null || checkId.isBlank() ? "unnamed-check" : checkId;
        return rules.getOrDefault(id, new Rule(id, "Not mapped to a guide section", id, DEFAULT_WEIGHT, false));
    }

    /** Every rule that is checked, ordered by guide section. */
    public List<Rule> all() {
        return rules.values().stream()
                .sorted(Comparator.comparingInt((Rule rule) -> sectionNumber(rule.section()))
                        .thenComparing(Rule::section).thenComparing(Rule::id))
                .toList();
    }

    /** The guide rules that are not checked automatically. */
    public List<UncheckedRule> unchecked() {
        return unchecked;
    }

    /** Why the rule details are missing, when the rules file could not be read. */
    public Optional<String> problem() {
        return problem;
    }

    // "10. for loops" sorts after "9. if ..." rather than after "1. Line Length".
    private static int sectionNumber(String section) {
        Matcher number = LEADING_NUMBER.matcher(section);
        return number.find() ? Integer.parseInt(number.group(1)) : Integer.MAX_VALUE;
    }

    private static double weight(String text) {
        try {
            double weight = Double.parseDouble(text == null ? "" : text.strip());
            return weight >= 0 && Double.isFinite(weight) ? weight : DEFAULT_WEIGHT;
        } catch (NumberFormatException exception) {
            return DEFAULT_WEIGHT;
        }
    }

    private static int uncheckedNumber(String key) {
        try {
            return Integer.parseInt(key.substring(UNCHECKED_PREFIX.length()));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * A guide rule enforced by a check.
     *
     * @param id        the check id in the Checkstyle configuration
     * @param section   the guide section it enforces
     * @param text      the rule in plain language
     * @param weight    style points deducted per violation
     * @param heuristic whether the check can be wrong and needs a human look
     */
    public record Rule(String id, String section, String text, double weight, boolean heuristic) {
    }

    /**
     * A guide rule that is not checked automatically.
     *
     * @param section the guide section
     * @param text    the rule in plain language
     */
    public record UncheckedRule(String section, String text) {

        static UncheckedRule parse(String value) {
            int separator = value.indexOf('|');
            return separator < 0
                    ? new UncheckedRule("", value.strip())
                    : new UncheckedRule(value.substring(0, separator).strip(), value.substring(separator + 1).strip());
        }
    }
}
