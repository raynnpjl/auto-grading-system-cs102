package web;

import grading.assessment.AssessmentDefinition;
import grading.feedback.Feedback;
import grading.feedback.SourceLocation;
import grading.progress.ProgressSnapshot;
import grading.progress.StudentProgress;
import grading.similarity.SimilarityPair;
import java.util.stream.Collectors;

/** Turns a grading run's progress into the JSON the page polls for. */
final class ProgressJson {

    private ProgressJson() {
    }

    /**
     * The progress JSON, with the flags the page needs to know what is ready.
     *
     * @param snapshot         the state of the run
     * @param testersReady     whether valid tester files are loaded
     * @param submissionsReady whether any submission can be graded
     * @param reportsReady     whether the latest attempt's reports can be downloaded
     * @param scoreSheetName   the uploaded score sheet's name, or empty
     * @return a JSON object
     */
    static String of(ProgressSnapshot snapshot, boolean testersReady, boolean submissionsReady,
            boolean reportsReady, String scoreSheetName) {
        return "{\"testersReady\":" + testersReady
                + ",\"submissionsReady\":" + submissionsReady
                + ",\"reportsReady\":" + reportsReady
                + ",\"scoreSheetName\":" + Json.quote(scoreSheetName)
                + ",\"running\":" + snapshot.running()
                + ",\"done\":" + snapshot.done()
                + ",\"total\":" + snapshot.total()
                + ",\"elapsedSeconds\":" + snapshot.elapsedSeconds()
                + ",\"currentStudent\":" + Json.quote(snapshot.currentStudent())
                + ",\"currentStep\":" + Json.quote(snapshot.currentStep())
                + ",\"questions\":" + Json.strings(AssessmentDefinition.QUESTIONS.stream()
                        .map(AssessmentDefinition.Question::id).toList())
                + ",\"students\":" + Json.array(snapshot.students().stream().map(ProgressJson::student).toList())
                + ",\"activity\":" + Json.strings(snapshot.activity())
                + ",\"similarPairs\":" + Json.array(snapshot.similarPairs().stream().map(ProgressJson::pair).toList())
                + "}";
    }

    private static String student(StudentProgress student) {
        return "{\"username\":" + Json.quote(student.username())
                + ",\"status\":" + Json.quote(student.status())
                + ",\"finished\":" + student.finished()
                + ",\"formatFixed\":" + student.formatFixed()
                + ",\"scores\":" + student.scores().entrySet().stream()
                        .map(entry -> Json.quote(entry.getKey()) + ":" + entry.getValue())
                        .collect(Collectors.joining(",", "{", "}"))
                + ",\"total\":" + student.total()
                + ",\"issues\":" + Json.strings(student.issues())
                + ",\"flags\":" + Json.strings(student.flags())
                + ",\"feedback\":" + Json.array(student.feedback().stream().map(ProgressJson::feedback).toList())
                + "}";
    }

    private static String feedback(Feedback feedback) {
        return "{\"question\":" + Json.quote(feedback.questionId())
                + ",\"kind\":" + Json.quote(feedback.kind())
                + ",\"where\":" + Json.quote(feedback.location().map(SourceLocation::label).orElse(""))
                + ",\"code\":" + Json.quote(feedback.location().map(SourceLocation::code).orElse(""))
                + ",\"message\":" + Json.quote(feedback.message())
                + "}";
    }

    private static String pair(SimilarityPair pair) {
        return "{\"question\":" + Json.quote(pair.questionId())
                + ",\"first\":" + Json.quote(pair.first())
                + ",\"second\":" + Json.quote(pair.second())
                + ",\"percent\":" + pair.percent()
                + ",\"flagged\":" + pair.flagged()
                + ",\"firstLines\":" + Json.quote(SimilarityPair.describe(pair.firstLines()))
                + ",\"secondLines\":" + Json.quote(SimilarityPair.describe(pair.secondLines()))
                + "}";
    }
}
