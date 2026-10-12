package grading.config;

import java.nio.file.Path;

/**
 * Where the style checks and rules are, and how the style score is scaled.
 *
 * @param checkstyleConfig the Checkstyle configuration
 * @param rules            each rule's guide section, plain-language text and weight
 * @param templateFolder   the instructor's template sources, excluded from checks
 * @param maxScore         the style score of a submission without violations
 */
public record StyleSettings(Path checkstyleConfig, Path rules, Path templateFolder, double maxScore) {
}
