package ui;

import grading.similarity.SimilarityPair;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The pairs of answers the plagiarism check found similar. Only the flagged pairs are listed unless the
 * marker asks for every compared pair; double-clicking a pair selects its first student.
 */
final class SimilarityList {

    private static final double SPACING = 8;
    private static final double QUESTION_COLUMN_WIDTH = 70;
    private static final double STUDENT_COLUMN_WIDTH = 160;
    private static final double PERCENT_COLUMN_WIDTH = 80;

    private final ObservableList<SimilarityPair> rows = FXCollections.observableArrayList();
    private final TableView<SimilarityPair> table = new TableView<>(rows);
    private final CheckBox showAll = new CheckBox("Show all compared pairs");
    private final VBox view = new VBox(SPACING, showAll, table);
    private List<SimilarityPair> pairs = List.of();

    /**
     * @param selectStudent called with a student's username when the marker double-clicks a pair
     */
    SimilarityList(Consumer<String> selectStudent) {
        table.setId("similarityTable");
        table.setPlaceholder(new Label("No similar answers were flagged in the latest grading run."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);
        view.getStyleClass().add("similarity-view");

        table.getColumns().add(column("Question", QUESTION_COLUMN_WIDTH, SimilarityPair::questionId));
        table.getColumns().add(column("Student A", STUDENT_COLUMN_WIDTH, SimilarityPair::first));
        table.getColumns().add(column("Student B", STUDENT_COLUMN_WIDTH, SimilarityPair::second));
        table.getColumns().add(column("Similarity", PERCENT_COLUMN_WIDTH, pair -> pair.percent() + "%"));
        TableColumn<SimilarityPair, String> lines = column("Matching lines", 0,
                pair -> SimilarityPair.describe(pair.firstLines()) + "  ↔  "
                        + SimilarityPair.describe(pair.secondLines()));
        table.getColumns().add(lines);

        table.setRowFactory(view -> {
            TableRow<SimilarityPair> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    selectStudent.accept(row.getItem().first());
                }
            });
            return row;
        });
        showAll.selectedProperty().addListener((property, before, after) -> refresh());
    }

    VBox view() {
        return view;
    }

    /** How many pairs are flagged for a person to check. */
    long flaggedCount() {
        return pairs.stream().filter(SimilarityPair::flagged).count();
    }

    /** The pairs listed now, for tests. */
    List<SimilarityPair> listed() {
        return List.copyOf(rows);
    }

    /** Shows the pairs from the latest snapshot; does nothing when they have not changed. */
    void update(List<SimilarityPair> latest) {
        if (latest.equals(pairs)) {
            return;
        }
        pairs = List.copyOf(latest);
        refresh();
    }

    private void refresh() {
        rows.setAll(showAll.isSelected() ? pairs : pairs.stream().filter(SimilarityPair::flagged).toList());
    }

    private static TableColumn<SimilarityPair, String> column(String title, double width,
            Function<SimilarityPair, String> value) {
        TableColumn<SimilarityPair, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        if (width > 0) {
            column.setPrefWidth(width);
        }
        return column;
    }
}
