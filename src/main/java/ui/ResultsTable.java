package ui;

import grading.assessment.AssessmentDefinition;
import grading.progress.GradingStatus;
import grading.progress.ProgressSnapshot;
import grading.progress.StudentProgress;
import grading.scoring.Marks;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

/**
 * One row per submission: each question's marks, the total, the grading status and how many similar
 * answers were flagged. Rows are updated in place, so the selection survives every progress refresh.
 */
final class ResultsTable {

    private static final double STUDENT_COLUMN_WIDTH = 170;
    private static final double QUESTION_COLUMN_WIDTH = 58;
    private static final double STATUS_COLUMN_WIDTH = 110;
    private static final List<String> STATUS_STYLES = Arrays.stream(GradingStatus.values())
            .map(GradingStatus::styleClass).toList();

    private final ObservableList<StudentProgress> rows = FXCollections.observableArrayList();
    private final TableView<StudentProgress> table = new TableView<>(rows);

    /**
     * @param regrade called with a student's username when the marker picks "Regrade this student"
     */
    ResultsTable(Consumer<String> regrade) {
        table.setId("resultsTable");
        table.setPlaceholder(new Label("Import student submissions to list them here."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addMarkColumns();
        addStatusColumns();
        table.setRowFactory(view -> regradeRow(regrade));
    }

    // The student, one column per question part, and the total.
    private void addMarkColumns() {
        TableColumn<StudentProgress, String> student = new TableColumn<>("Student");
        student.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().username()));
        student.setPrefWidth(STUDENT_COLUMN_WIDTH);
        table.getColumns().add(student);

        for (AssessmentDefinition.Question question : AssessmentDefinition.QUESTIONS) {
            TableColumn<StudentProgress, String> marks = new TableColumn<>(question.id());
            marks.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                    formatMarks(cell.getValue().scores().get(question.id()))));
            marks.setPrefWidth(QUESTION_COLUMN_WIDTH);
            marks.setStyle("-fx-alignment: CENTER-RIGHT;");
            table.getColumns().add(marks);
        }

        TableColumn<StudentProgress, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().scores().isEmpty() ? "" : formatMarks(cell.getValue().total())));
        total.setPrefWidth(QUESTION_COLUMN_WIDTH);
        total.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        table.getColumns().add(total);
    }

    private void addStatusColumns() {
        TableColumn<StudentProgress, GradingStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().status()));
        status.setCellFactory(column -> new StatusCell());
        status.setPrefWidth(STATUS_COLUMN_WIDTH);
        table.getColumns().add(status);

        TableColumn<StudentProgress, Integer> flags = new TableColumn<>("Similar");
        flags.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().flags().size()));
        flags.setCellFactory(column -> new FlagCell());
        table.getColumns().add(flags);
    }

    // A row whose right-click menu regrades its student; empty rows have no menu.
    private static TableRow<StudentProgress> regradeRow(Consumer<String> regrade) {
        TableRow<StudentProgress> row = new TableRow<>();
        MenuItem regradeItem = new MenuItem("Regrade this student");
        regradeItem.setOnAction(event -> regrade.accept(row.getItem().username()));
        ContextMenu menu = new ContextMenu(regradeItem);
        row.contextMenuProperty().bind(Bindings
                .when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
        return row;
    }

    TableView<StudentProgress> view() {
        return table;
    }

    Optional<StudentProgress> selected() {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    /** Selects the student's row, if they are in the table. */
    void select(String username) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).username().equals(username)) {
                table.getSelectionModel().select(index);
                table.scrollTo(index);
                return;
            }
        }
    }

    /** Shows the snapshot's students, changing only the rows whose data changed. */
    void update(ProgressSnapshot snapshot) {
        List<StudentProgress> students = snapshot.students();
        String selectedUsername = selected().map(StudentProgress::username).orElse(null);
        boolean sameStudents = rows.size() == students.size();
        for (int index = 0; sameStudents && index < students.size(); index++) {
            sameStudents = rows.get(index).username().equals(students.get(index).username());
        }
        if (!sameStudents) {
            rows.setAll(students);
        } else {
            for (int index = 0; index < students.size(); index++) {
                if (!rows.get(index).equals(students.get(index))) {
                    rows.set(index, students.get(index));
                }
            }
        }
        if (selectedUsername != null && !selected().map(StudentProgress::username)
                .filter(selectedUsername::equals).isPresent()) {
            select(selectedUsername);
        }
    }

    private static String formatMarks(Double marks) {
        return marks == null ? "" : Marks.format(marks);
    }

    /** The status text, coloured by a style class named after it. */
    private static final class StatusCell extends TableCell<StudentProgress, GradingStatus> {

        @Override
        protected void updateItem(GradingStatus status, boolean empty) {
            super.updateItem(status, empty);
            getStyleClass().removeAll(STATUS_STYLES);
            if (empty || status == null) {
                setText(null);
                return;
            }
            setText(status.label());
            getStyleClass().add(status.styleClass());
        }
    }

    /** The number of flagged similar answers, left blank when there are none. */
    private static final class FlagCell extends TableCell<StudentProgress, Integer> {

        @Override
        protected void updateItem(Integer count, boolean empty) {
            super.updateItem(count, empty);
            getStyleClass().remove("flag");
            if (empty || count == null || count == 0) {
                setText(null);
                return;
            }
            setText(count + (count == 1 ? " match" : " matches"));
            getStyleClass().add("flag");
        }
    }
}
