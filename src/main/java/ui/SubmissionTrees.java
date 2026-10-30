package ui;

import grading.assessment.AssessmentDefinition;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javafx.scene.control.TreeItem;

/** Builds the file trees shown under the two import panels. */
final class SubmissionTrees {

    private SubmissionTrees() {
    }

    /** The tester folder and the tester files found in it. */
    static TreeItem<String> testers(TesterValidationResult result) {
        TreeItem<String> root = new TreeItem<>(
                result.source().getFileName() + (result.isValid() ? " - valid" : " - needs attention"));
        root.setExpanded(true);
        result.testerFiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> root.getChildren().add(new TreeItem<>(entry.getKey())));
        return root;
    }

    /** Every student with their readiness, and the question folders of their normalized copy. */
    static TreeItem<String> submissions(List<SubmissionValidationResult> results) {
        TreeItem<String> root = new TreeItem<>("Normalized submissions");
        root.setExpanded(true);
        for (SubmissionValidationResult result : results) {
            TreeItem<String> student = new TreeItem<>(result.username() + statusSuffix(result));
            student.setExpanded(true);
            if (result.normalizedRoot() != null) {
                addNormalizedTree(student, result.normalizedRoot());
            }
            root.getChildren().add(student);
        }
        return root;
    }

    private static void addNormalizedTree(TreeItem<String> student, Path normalizedRoot) {
        try {
            for (String question : AssessmentDefinition.questionFolders()) {
                Path questionPath = normalizedRoot.resolve(question);
                if (!Files.isDirectory(questionPath)) {
                    continue;
                }
                TreeItem<String> questionItem = new TreeItem<>(question);
                try (var children = Files.list(questionPath)) {
                    children.sorted().forEach(file -> questionItem.getChildren()
                            .add(new TreeItem<>(file.getFileName().toString())));
                }
                student.getChildren().add(questionItem);
            }
        } catch (IOException exception) {
            student.getChildren().add(new TreeItem<>("Could not display normalized files"));
        }
    }

    private static String statusSuffix(SubmissionValidationResult result) {
        if (!result.isReadyForGrading()) {
            return " - needs attention";
        }
        List<String> missing = FindingsText.missingQuestions(result);
        if (!missing.isEmpty()) {
            return " - ready, missing " + String.join(", ", missing);
        }
        return result.correctedCount() > 0 ? " - ready (corrected)" : " - ready";
    }
}
