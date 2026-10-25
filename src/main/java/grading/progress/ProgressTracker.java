package grading.progress;

import grading.assessment.AssessmentDefinition;
import grading.common.Json;
import grading.feedback.Feedback;
import grading.feedback.SourceLocation;
import grading.similarity.SimilarityPair;
import grading.submission.FindingSeverity;
import grading.submission.SubmissionValidationResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Holds the live state of a grading run so the dashboard can show it. Every method is thread-safe. */
public final class ProgressTracker {

    private static final int MAX_ACTIVITY_LINES = 200;

    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final List<String> activity = new ArrayList<>();
    private final List<SimilarityPair> similarPairs = new ArrayList<>();
    private boolean running;
    private Instant startedAt;
    private Instant endedAt;
    private String currentStudent = "";
    private String currentStep = "";

    /**
     * Fills the table with every submission before grading starts, so the queue is visible.
     *
     * @param submissions the prepared submissions, in the order they will be graded
     */
    public synchronized void prepare(List<SubmissionValidationResult> submissions) {
        rows.clear();
        activity.clear();
        similarPairs.clear();
        running = false;
        startedAt = null;
        endedAt = null;
        currentStudent = "";
        currentStep = "";
        for (SubmissionValidationResult submission : submissions) {
            Row row = new Row(submission.username());
            if (!submission.isReadyForGrading()) {
                row.status = "skipped";
                row.finished = true;
            } else if (submission.correctedCount() > 0) {
                row.status = "auto-fixed";
                row.autoFixed = true;
            }
            submission.findings().stream()
                    .filter(finding -> finding.severity() != FindingSeverity.INFO && !finding.corrected())
                    .forEach(finding -> row.issues.add(finding.message()));
            rows.put(submission.username(), row);
        }
    }

    /**
     * Marks a student as being graded now.
     *
     * @param username the student's username, which must match a row added by prepare()
     */
    public synchronized void studentStarted(String username) {
        rows(username).status = "running";
        running = true;
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        currentStudent = username;
        currentStep = "starting";
    }

    /**
     * Says what the grader is doing right now, shown in the "Grading 4 of 6" line.
     *
     * @param username the student being graded
     * @param step     a short description such as "compiling Q2"
     */
    public synchronized void stepStarted(String username, String step) {
        currentStudent = username;
        currentStep = step;
    }

    /**
     * Records the score of one question part (e.g. Q2a) for a student.
     *
     * @param username   the student
     * @param questionId the question part, such as "Q1a"
     * @param score      the marks awarded for that part
     */
    public synchronized void questionFinished(String username, String questionId, double score) {
        rows(username).scores.put(questionId, score);
    }

    /**
     * Marks a student as done.
     *
     * @param username  the student
     * @param hasErrors true if any question could not be graded normally
     */
    public synchronized void studentFinished(String username, boolean hasErrors) {
        Row row = rows(username);
        row.finished = true;
        if (hasErrors) {
            row.status = "needs-review";
        } else {
            row.status = row.autoFixed ? "auto-fixed" : "graded";
        }
    }

    /**
     * Shows the feedback on a student's mistakes in their row.
     *
     * @param username the student
     * @param feedback the explanations, in question order
     */
    public synchronized void feedback(String username, List<Feedback> feedback) {
        rows(username).feedback = List.copyOf(feedback);
    }

    /**
     * Shows the pairs of similar answers. Students in a flagged pair are marked for review, with a note
     * naming the other student; marks are unchanged.
     *
     * @param pairs every reported pair, flagged or not
     */
    public synchronized void similarity(List<SimilarityPair> pairs) {
        similarPairs.clear();
        similarPairs.addAll(pairs);
        for (SimilarityPair pair : pairs) {
            if (pair.flagged()) {
                flag(pair.first(), pair.questionId() + ": " + pair.percent() + "% similar to " + pair.second());
                flag(pair.second(), pair.questionId() + ": " + pair.percent() + "% similar to " + pair.first());
            }
        }
    }

    private void flag(String username, String note) {
        Row row = rows(username);
        row.status = "needs-review";
        row.flags.add(note);
    }

    /**
     * Adds one line to the Activity panel. Only the newest lines are kept.
     *
     * @param message the text to show
     */
    public synchronized void log(String message) {
        activity.add(message);
        if (activity.size() > MAX_ACTIVITY_LINES) {
            activity.remove(0);
        }
    }

    /** Called when the whole batch has ended, successfully or not. Freezes the elapsed time. */
    public synchronized void finish() {
        running = false;
        endedAt = Instant.now();
        currentStudent = "";
        currentStep = "";
    }

    /**
     * Builds the JSON sent to the browser.
     *
     * @return the whole state of the run as a JSON string
     */
    public synchronized String toJson() {
        long done = rows.values().stream().filter(row -> row.finished).count();
        List<String> questionIds = AssessmentDefinition.QUESTIONS.stream()
                .map(AssessmentDefinition.Question::id)
                .toList();
        return "{"
                + "\"running\":" + running
                + ",\"done\":" + done
                + ",\"total\":" + rows.size()
                + ",\"elapsedSeconds\":" + elapsedSeconds()
                + ",\"currentStudent\":" + Json.quote(currentStudent)
                + ",\"currentStep\":" + Json.quote(currentStep)
                + ",\"questions\":" + Json.strings(questionIds)
                + ",\"students\":" + rows.values().stream().map(Row::toJson)
                        .collect(Collectors.joining(",", "[", "]"))
                + ",\"activity\":" + Json.strings(activity)
                + ",\"similarPairs\":" + Json.array(similarPairs.stream().map(ProgressTracker::pairJson).toList())
                + "}";
    }

    private static String pairJson(SimilarityPair pair) {
        return "{\"question\":" + Json.quote(pair.questionId())
                + ",\"first\":" + Json.quote(pair.first())
                + ",\"second\":" + Json.quote(pair.second())
                + ",\"percent\":" + pair.percent()
                + ",\"flagged\":" + pair.flagged()
                + ",\"firstLines\":" + Json.quote(SimilarityPair.describe(pair.firstLines()))
                + ",\"secondLines\":" + Json.quote(SimilarityPair.describe(pair.secondLines()))
                + "}";
    }

    private Row rows(String username) {
        return rows.computeIfAbsent(username, Row::new);
    }

    private long elapsedSeconds() {
        if (startedAt == null) {
            return 0;
        }
        Instant end = endedAt != null ? endedAt : Instant.now();
        return Duration.between(startedAt, end).getSeconds();
    }

    /** One student's row in the table. */
    private static final class Row {
        final String username;
        String status = "queued";
        boolean autoFixed;
        boolean finished;
        final Map<String, Double> scores = new LinkedHashMap<>();
        final List<String> issues = new ArrayList<>();
        final List<String> flags = new ArrayList<>();
        List<Feedback> feedback = List.of();

        Row(String username) {
            this.username = username;
        }

        String toJson() {
            double total = scores.values().stream().mapToDouble(Double::doubleValue).sum();
            return "{"
                    + "\"username\":" + Json.quote(username)
                    + ",\"status\":" + Json.quote(status)
                    + ",\"finished\":" + finished
                    + ",\"scores\":" + scores.entrySet().stream()
                            .map(entry -> Json.quote(entry.getKey()) + ":" + entry.getValue())
                            .collect(Collectors.joining(",", "{", "}"))
                    + ",\"total\":" + total
                    + ",\"issues\":" + Json.strings(issues)
                    + ",\"flags\":" + Json.strings(flags)
                    + ",\"feedback\":" + Json.array(feedback.stream().map(Row::feedbackJson).toList())
                    + "}";
        }

        private static String feedbackJson(Feedback feedback) {
            return "{\"question\":" + Json.quote(feedback.questionId())
                    + ",\"kind\":" + Json.quote(feedback.kind())
                    + ",\"where\":" + Json.quote(feedback.location().map(SourceLocation::label).orElse(""))
                    + ",\"code\":" + Json.quote(feedback.location().map(SourceLocation::code).orElse(""))
                    + ",\"message\":" + Json.quote(feedback.message())
                    + "}";
        }
    }
}