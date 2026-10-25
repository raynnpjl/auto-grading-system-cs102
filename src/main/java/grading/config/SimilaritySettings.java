package grading.config;

/**
 * How submissions are compared with each other to find possible plagiarism.
 *
 * @param threshold       similarity, from 0 to 1, at or above which a pair is reported
 * @param screenThreshold winnowing similarity at or above which a pair is compared in detail
 * @param minTokens       tokens each answer needs before its students are marked for review
 * @param kgramSize       tokens in each sequence that winnowing fingerprints
 * @param windowSize      consecutive sequences from which winnowing keeps one fingerprint
 * @param minMatchTokens  shortest run of identical tokens that Greedy String Tiling counts
 */
public record SimilaritySettings(double threshold, double screenThreshold, int minTokens, int kgramSize,
        int windowSize, int minMatchTokens) {
}
