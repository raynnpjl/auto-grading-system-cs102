package grading.reporting;

import grading.common.Messages;
import grading.config.GradingSettings;
import grading.execution.StudentGradingResult;
import grading.scoring.ScoreBreakdown;
import grading.style.StudentStyleResult;
import grading.style.StyleChecker;
import grading.style.StyleRules;
import grading.submission.SubmissionValidationResult;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One grading attempt: when it ran, what it graded with which settings, and the outcome for every
 * student. Each attempt is reported in its own folder, so re-grading never overwrites an earlier one.
 *
 * @param startedAt         when grading started
 * @param finishedAt        when grading finished
 * @param testerFolder      the tester folder that was used
 * @param submissionsFolder the submissions folder that was imported
 * @param settings          the settings in effect
 * @param styleRules        the style rules in effect
 * @param checkstyleVersion the Checkstyle release used
 * @param students          the outcome for every imported submission, in import order
 */
public record GradingAttempt(LocalDateTime startedAt, LocalDateTime finishedAt, Path testerFolder,
        Path submissionsFolder, GradingSettings settings, StyleRules styleRules, String checkstyleVersion,
        List<StudentOutcome> students) {

    public GradingAttempt {
        students = List.copyOf(students);
    }

    /**
     * Combines each submission with its grading result, its style check and its mark breakdown.
     * Graded results are matched to the submissions that were ready for grading, in import order.
     */
    public static GradingAttempt assemble(LocalDateTime startedAt, Path testerFolder, Path submissionsFolder,
            GradingSettings settings, List<SubmissionValidationResult> submissions,
            List<StudentGradingResult> results, StyleChecker styleChecker) {
        return assemble(startedAt, testerFolder, submissionsFolder, settings, submissions, results,
                styleChecker::check, styleChecker.rules());
    }

    static GradingAttempt assemble(LocalDateTime startedAt, Path testerFolder, Path submissionsFolder,
            GradingSettings settings, List<SubmissionValidationResult> submissions,
            List<StudentGradingResult> results, Function<SubmissionValidationResult, StudentStyleResult> style,
            StyleRules rules) {
        Iterator<StudentGradingResult> graded = results.iterator();
        List<StudentOutcome> outcomes = new ArrayList<>();
        for (SubmissionValidationResult submission : submissions) {
            StudentGradingResult grading = submission.isReadyForGrading() && graded.hasNext() ? graded.next() : null;
            StudentStyleResult styleResult = checkStyle(submission, style, settings);
            ScoreBreakdown score = grading == null ? null : score(grading, styleResult, settings);
            outcomes.add(new StudentOutcome(submission, grading, styleResult, score));
        }
        return new GradingAttempt(startedAt, LocalDateTime.now(), testerFolder, submissionsFolder, settings, rules,
                StyleChecker.checkstyleVersion(), outcomes);
    }

    // One student's style problem must not stop the attempt; it is reported as not checked.
    private static StudentStyleResult checkStyle(SubmissionValidationResult submission,
            Function<SubmissionValidationResult, StudentStyleResult> style, GradingSettings settings) {
        try {
            return style.apply(submission);
        } catch (RuntimeException exception) {
            return StudentStyleResult.notChecked(submission.username(), settings.style().maxScore(),
                    "Style could not be checked: " + Messages.describe(exception));
        }
    }

    // When the style result cannot be used for penalties, the tester marks stand without a penalty.
    private static ScoreBreakdown score(StudentGradingResult grading, StudentStyleResult style,
            GradingSettings settings) {
        try {
            return ScoreBreakdown.of(grading, style, settings.penalties());
        } catch (RuntimeException exception) {
            return ScoreBreakdown.of(grading, null, settings.penalties());
        }
    }

    /** The mark breakdown of every graded student, for the score sheet. */
    public List<ScoreBreakdown> scores() {
        return students.stream().map(StudentOutcome::score).filter(Objects::nonNull).toList();
    }
}
