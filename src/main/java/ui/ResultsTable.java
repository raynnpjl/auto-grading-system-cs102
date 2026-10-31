package ui;

import grading.assessment.AssessmentDefinition;
import grading.progress.GradingStatus;
import grading.progress.ProgressSnapshot;
import grading.progress.StudentProgress;
import grading.scoring.Marks;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * One row per submission: each question's marks, the total, the grading status and, for anyone a person must
 * check, the reason. A filter narrows the table to those students. Rows are updated in place, so the selection
 * survives every progress refresh.
 */
final class ResultsTable {

    private static final double STUDENT_COLUMN_WIDTH = 150;
    private static final double QUESTION_COLUMN_WIDTH = 54;
    private static final double STATUS_COLUMN_WIDTH = 104;
    // Marks and status never need more than this, so the extra width goes to the reason.
    private static final double MARK_COLUMN_MAX_WIDTH = 64;
    private static final double STATUS_COLUMN_MAX_WIDTH = 120;
    private static final double STUDENT_COLUMN_MAX_WIDTH = 200;
    private static final double SPACING = 8;
    private static final List<String> STATUS_STYLES = Arrays.stream(GradingStatus.values())
            .map(GradingStatus::styleClass).toList();

    private final ObservableList<StudentProgress> rows = FXCollections.observableArrayList();
    private final FilteredList<StudentProgress> shown = new FilteredList<>(rows);
    private final TableView<StudentProgress> table = new TableView<>();
    private final CheckBox onlyToCheck = new CheckBox();
    private final VBox view;

    /**
     * @param regrade called with a student's username when the marker picks "Regrade this student"
     */
    ResultsTable(Consumer<String> regrade) {
        SortedList<StudentProgress> sorted = new SortedList<>(shown);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setId("resultsTable");
        table.setPlaceholder(new Label("Import student submissions to list them here."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addMarkColumns();
        addStatusColumns();
        table.setRowFactory(rowView -> regradeRow(regrade));

        onlyToCheck.setId("onlyToCheckFilter");
        onlyToCheck.selectedProperty().addListener((property, before, after) -> applyFilter());
        HBox filterBar = new HBox(SPACING, onlyToCheck);
        filterBar.getStyleClass().add("results-filter");
        VBox.setVgrow(table, Priority.ALWAYS);
        view = new VBox(filterBar, table);
        updateFilterLabel();
    }

    // The student, one column per question part, and the total.
    private void addMarkColumns() {
        TableColumn<StudentProgress, String> student = new TableColumn<>("Student");
        student.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().username()));
        student.setPrefWidth(STUDENT_COLUMN_WIDTH);
        student.setMaxWidth(STUDENT_COLUMN_MAX_WIDTH);
        table.getColumns().add(student);

        for (AssessmentDefinition.Question question : AssessmentDefinition.QUESTIONS) {
            TableColumn<StudentProgress, String> marks = new TableColumn<>(question.id());
            marks.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                    formatMarks(cell.getValue().scores().get(question.id()))));
            marks.setPrefWidth(QUESTION_COLUMN_WIDTH);
            marks.setMaxWidth(MARK_COLUMN_MAX_WIDTH);
            marks.setStyle("-fx-alignment: CENTER-RIGHT;");
            table.getColumns().add(marks);
        }

        TableColumn<StudentProgress, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().scores().isEmpty() ? "" : formatMarks(cell.getValue().total())));
        total.setPrefWidth(QUESTION_COLUMN_WIDTH);
        total.setMaxWidth(MARK_COLUMN_MAX_WIDTH);
        total.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        table.getColumns().add(total);
    }

    private void addStatusColumns() {
        TableColumn<StudentProgress, GradingStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().status()));
        status.setCellFactory(column -> new StatusCell());
        status.setPrefWidth(STATUS_COLUMN_WIDTH);
        status.setMaxWidth(STATUS_COLUMN_MAX_WIDTH);
        table.getColumns().add(status);

        TableColumn<StudentProgress, String> reason = new TableColumn<>("Reason");
        reason.setCellValueFactory(cell -> new ReadOnlyStringWrapper(ReviewReason.of(cell.getValue())));
        reason.setCellFactory(column -> new ReasonCell());
        table.getColumns().add(reason);
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

    /** The filter above the table and the table itself. */
    VBox view() {
        return view;
    }

    TableView<StudentProgress> table() {
        return table;
    }

    Optional<StudentProgress> selected() {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    /** How many students a person needs to check: those needing review and those skipped. */
    long toCheckCount() {
        return rows.stream().filter(ResultsTable::needsChecking).count();
    }

    /** Selects the student's row, if it is shown. */
    void select(String username) {
        List<StudentProgress> items = table.getItems();
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).username().equals(username)) {
                table.getSelectionModel().select(index);
                table.scrollTo(index);
                return;
            }
        }
    }

    /** Shows the snapshot's students, changing only the rows whose data changed. */
    void update(ProgressSnapshot snapshot) {
        List<StudentProgress> students = snapshot.students();
        final String selectedUsername = selected().map(StudentProgress::username).orElse(null);
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
        updateFilterLabel();
        if (selectedUsername != null && !selected().map(StudentProgress::username)
                .filter(selectedUsername::equals).isPresent()) {
            select(selectedUsername);
        }
    }

    private void applyFilter() {
        shown.setPredicate(onlyToCheck.isSelected() ? ResultsTable::needsChecking : null);
    }

    private void updateFilterLabel() {
        long count = toCheckCount();
        onlyToCheck.setText("Show only students to check (" + count + ")");
        if (count == 0) {
            onlyToCheck.setSelected(false);
        }
        onlyToCheck.setDisable(count == 0);
        applyFilter();
    }

    private static boolean needsChecking(StudentProgress student) {
        return student.status() == GradingStatus.NEEDS_REVIEW || student.status() == GradingStatus.SKIPPED;
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

    /** The reason a student needs checking; the full text is in a tooltip when it does not fit. */
    private static final class ReasonCell extends TableCell<StudentProgress, String> {

        @Override
        protected void updateItem(String reason, boolean empty) {
            super.updateItem(reason, empty);
            getStyleClass().remove("review-reason");
            if (empty || reason == null || reason.isEmpty()) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(reason);
            setTooltip(new Tooltip(reason));
            getStyleClass().add("review-reason");
        }
    }
}
