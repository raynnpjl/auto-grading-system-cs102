package grading.reporting;

import grading.common.Messages;
import grading.config.GradingSettings;
import grading.execution.StudentGradingResult;
import grading.feedback.Feedback;
import grading.feedback.FeedbackGenerator;
import grading.scoring.ScoreBreakdown;
import grading.similarity.SimilarityChecker;
import grading.similarity.SimilarityReport;
import grading.style.StudentStyleResult;
import grading.style.StyleChecker;
import grading.style.StyleRules;
import grading.style.Templates;
import grading.submission.SubmissionValidationResult;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * @param similarity        the comparison of every pair of students' answers
 */
public record GradingAttempt(LocalDateTime startedAt, LocalDateTime finishedAt, Path testerFolder,
        Path submissionsFolder, GradingSettings settings, StyleRules styleRules, String checkstyleVersion,
        List<StudentOutcome> students, SimilarityReport similarity) {

    public GradingAttempt {
        students = List.copyOf(students);
    }

    /**
     * Combines each submission with its grading result, its style check, its mark breakdown and the
     * feedback on its mistakes, and compares every pair of students for possible plagiarism. Each
     * graded result is matched to its submission by username, so a student regraded on their own
     * still gets their own result.
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
        Templates templates = new Templates(settings.style().templateFolder());
        FeedbackGenerator feedback = new FeedbackGenerator(templates);
        SimilarityReport similarity = compare(submissions, settings, templates);
        // Two submissions can share a username; each takes the next result with that name, in order.
        Map<String, Deque<StudentGradingResult>> graded = new HashMap<>();
        results.forEach(result -> graded.computeIfAbsent(result.username(), name -> new ArrayDeque<>()).add(result));
        List<StudentOutcome> outcomes = new ArrayList<>();
        for (SubmissionValidationResult submission : submissions) {
            Deque<StudentGradingResult> own = graded.getOrDefault(submission.username(), new ArrayDeque<>());
            StudentGradingResult grading = submission.isReadyForGrading() ? own.poll() : null;
            StudentStyleResult styleResult = checkStyle(submission, style, settings);
            ScoreBreakdown score = grading == null ? null : score(grading, styleResult, settings);
            outcomes.add(new StudentOutcome(submission, grading, styleResult, score,
                    explain(feedback, submission, grading), similarity.flaggedFor(submission.username())));
        }
        return new GradingAttempt(startedAt, LocalDateTime.now(), testerFolder, submissionsFolder, settings, rules,
                StyleChecker.checkstyleVersion(), outcomes, similarity);
    }

    // A problem in the plagiarism check must not stop the attempt; the summary says it was not completed.
    private static SimilarityReport compare(List<SubmissionValidationResult> submissions, GradingSettings settings,
            Templates templates) {
        try {
            return new SimilarityChecker(settings.similarity(), templates).check(submissions);
        } catch (RuntimeException exception) {
            return SimilarityReport.failed(settings.similarity(),
                    "The plagiarism check could not be completed: " + Messages.describe(exception));
        }
    }

    // Feedback that cannot be produced is left out; the test results in the report still show what failed.
    private static List<Feedback> explain(FeedbackGenerator feedback, SubmissionValidationResult submission,
            StudentGradingResult grading) {
        if (grading == null) {
            return List.of();
        }
        try {
            return feedback.forStudent(submission, grading);
        } catch (RuntimeException exception) {
            return List.of();
        }
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
