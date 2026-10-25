package grading.config;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Limits and defaults for a grading session, read from {@code grading.properties}.
 *
 * @param compileTimeout     longest a javac run may take
 * @param executionTimeout   longest a tester run may take before its unfinished test case is skipped
 * @param parallelStudents   students graded at the same time
 * @param maxOutputBytes     output kept from each process stream before the process is stopped
 * @param maxJavaFileBytes   largest student source file that is inspected and run
 * @param maxZipBytes        largest total size a submission ZIP may expand to
 * @param maxZipEntries      most entries a submission ZIP may contain
 * @param emailDomain        domain used to complete a student email that is missing from a header
 * @param maxTesterFileBytes largest tester source file that is inspected
 * @param workspaceFolder    folder under the temporary directory that holds normalized copies
 * @param maxLogCharacters   process output kept in the on-screen log
 * @param port               port of the web page on this computer
 * @param style              style checking and scoring
 * @param penalties          penalties deducted from the tester marks
 * @param similarity         detection of submissions that are too similar to each other
 * @param reportsFolder      folder that receives one sub-folder of reports per grading attempt
 */
public record GradingSettings(
        Duration compileTimeout,
        Duration executionTimeout,
        int parallelStudents,
        int maxOutputBytes,
        long maxJavaFileBytes,
        long maxZipBytes,
        int maxZipEntries,
        String emailDomain,
        long maxTesterFileBytes,
        String workspaceFolder,
        int maxLogCharacters,
        int port,
        StyleSettings style,
        PenaltySettings penalties,
        SimilaritySettings similarity,
        Path reportsFolder) {

    private static final GradingSettings CURRENT = load(ConfigFile.load("grading"));

    /** The settings of this run: the packaged defaults with any external overrides applied. */
    public static GradingSettings current() {
        return CURRENT;
    }

    static GradingSettings load(ConfigFile file) {
        return new GradingSettings(
                Duration.ofSeconds(file.positiveNumber("grading.compile-timeout-seconds")),
                Duration.ofSeconds(file.positiveNumber("grading.execution-timeout-seconds")),
                Math.toIntExact(file.positiveNumber("grading.parallel-students")),
                Math.toIntExact(file.positiveNumber("grading.max-output-bytes")),
                file.positiveNumber("submission.max-java-file-bytes"),
                file.positiveNumber("submission.max-zip-bytes"),
                Math.toIntExact(file.positiveNumber("submission.max-zip-entries")),
                file.text("submission.email-domain"),
                file.positiveNumber("tester.max-file-bytes"),
                file.text("workspace.folder"),
                Math.toIntExact(file.positiveNumber("ui.max-log-characters")),
                Math.toIntExact(file.positiveNumber("ui.port")),
                new StyleSettings(
                        Path.of(file.text("style.checkstyle-config")),
                        Path.of(file.text("style.rules")),
                        Path.of(file.text("style.template-folder")),
                        file.nonNegativeDecimal("style.max-score")),
                new PenaltySettings(
                        file.nonNegativeDecimal("penalty.style-per-violation"),
                        file.nonNegativeDecimal("penalty.style-cap")),
                new SimilaritySettings(
                        file.fraction("similarity.threshold"),
                        file.fraction("similarity.screen-threshold"),
                        Math.toIntExact(file.positiveNumber("similarity.min-tokens")),
                        Math.toIntExact(file.positiveNumber("similarity.kgram-size")),
                        Math.toIntExact(file.positiveNumber("similarity.window-size")),
                        Math.toIntExact(file.positiveNumber("similarity.min-match-tokens"))),
                Path.of(file.text("reports.folder")));
    }
}
