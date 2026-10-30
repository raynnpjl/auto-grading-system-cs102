package ui;

import grading.feedback.Feedback;
import grading.progress.StudentProgress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * Everything known about the student selected in the results table: the submission problems left
 * uncorrected, similar answers, and why each mark was lost, with a button that opens their PDF report.
 */
final class StudentDetailPane {

    private static final double SECTION_SPACING = 6;

    private final VBox content = new VBox(SECTION_SPACING);
    private final ScrollPane scroll = new ScrollPane(content);
    private final Button openReport = new Button("Open PDF report");
    private StudentProgress shown;

    /**
     * @param openReportFor called with the shown student's username when the marker opens their report
     */
    StudentDetailPane(Consumer<String> openReportFor) {
        scroll.setId("studentDetail");
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-pane");
        content.getStyleClass().add("detail-content");
        openReport.setId("openReportButton");
        openReport.getStyleClass().add("score-sheet-button");
        openReport.setOnAction(event -> {
            if (shown != null) {
                openReportFor.accept(shown.username());
            }
        });
        show(Optional.empty(), false);
    }

    ScrollPane view() {
        return scroll;
    }

    /** The text shown, for tests. */
    String text() {
        StringBuilder text = new StringBuilder();
        content.getChildren().forEach(node -> {
            if (node instanceof Label label) {
                text.append(label.getText()).append('\n');
            }
        });
        return text.toString();
    }

    /**
     * Shows the student, redrawing only when their data changed.
     *
     * @param student       the selected student, or empty
     * @param reportPresent whether the latest attempt wrote reports that can be opened
     */
    void show(Optional<StudentProgress> student, boolean reportPresent) {
        openReport.setDisable(student.isEmpty() || !reportPresent);
        StudentProgress next = student.orElse(null);
        if (next != null && next.equals(shown) && !content.getChildren().isEmpty()) {
            return;
        }
        shown = next;
        content.getChildren().clear();
        if (next == null) {
            content.getChildren().add(muted("Select a student in the table to see their details."));
            return;
        }
        Label heading = new Label(next.username() + " — " + next.status()
                + (next.scores().isEmpty() ? "" : String.format(Locale.ROOT, " — total %.1f", next.total())));
        heading.getStyleClass().add("section-title");
        content.getChildren().addAll(heading, openReport);

        section("Submission problems", next.issues(), "None.", false);
        section("Similar answers", next.flags(), "None.", true);
        content.getChildren().add(title("Why marks were lost"));
        if (next.feedback().isEmpty()) {
            content.getChildren().add(muted(next.finished() ? "No marks lost." : "Shown once graded."));
        } else {
            Map<String, List<Feedback>> byQuestion = new LinkedHashMap<>();
            next.feedback().forEach(feedback ->
                    byQuestion.computeIfAbsent(feedback.questionId(), id -> new ArrayList<>())
                            .add(feedback));
            byQuestion.forEach((question, items) -> {
                content.getChildren().add(subtitle(question));
                items.forEach(feedback -> content.getChildren().add(line("• " + feedback.text(), false)));
            });
        }
    }

    private void section(String heading, List<String> items, String empty, boolean flagged) {
        content.getChildren().add(title(heading));
        if (items.isEmpty()) {
            content.getChildren().add(muted(empty));
        } else {
            items.forEach(item -> content.getChildren().add(line("• " + item, flagged)));
        }
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("detail-title");
        return label;
    }

    private static Label subtitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("detail-subtitle");
        return label;
    }

    private static Label muted(String text) {
        Label label = line(text, false);
        label.getStyleClass().add("muted");
        return label;
    }

    private static Label line(String text, boolean flagged) {
        Label label = new Label(text);
        label.setWrapText(true);
        if (flagged) {
            label.getStyleClass().add("flag");
        }
        return label;
    }
}
