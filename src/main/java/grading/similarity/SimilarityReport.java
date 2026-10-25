package grading.similarity;

import grading.config.SimilaritySettings;
import java.util.List;

/**
 * The result of comparing every pair of students: the pairs at or above the threshold, most similar
 * first, and how they were found.
 *
 * @param pairs      the reported pairs
 * @param settings   the thresholds and algorithm parameters used
 * @param algorithms the names of the screening and the scoring algorithm
 * @param compared   how many pairs of answers were compared
 * @param problem    why the check could not be completed, or empty when it was
 */
public record SimilarityReport(List<SimilarityPair> pairs, SimilaritySettings settings, List<String> algorithms,
        int compared, String problem) {

    public SimilarityReport {
        pairs = List.copyOf(pairs);
        algorithms = List.copyOf(algorithms);
        problem = problem == null ? "" : problem;
    }

    /** A report for a check that could not run, with the reason. */
    public static SimilarityReport failed(SimilaritySettings settings, String problem) {
        return new SimilarityReport(List.of(), settings, List.of(), 0, problem);
    }

    /** The flagged pairs that involve the student. */
    public List<SimilarityPair> flaggedFor(String username) {
        return pairs.stream().filter(SimilarityPair::flagged).filter(pair -> pair.involves(username)).toList();
    }
}
