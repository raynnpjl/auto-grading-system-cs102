package grading.assessment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The files used by the current CS102 assessment, shared by import and grading. */
public final class AssessmentDefinition {

    public static final List<Question> QUESTIONS = List.of(
            new Question("Q1a", "Q1", List.of("Q1a.java"), List.of(), List.of()),
            new Question("Q1b", "Q1", List.of("Q1b.java"), List.of(), List.of()),
            new Question("Q2a", "Q2", List.of("Q2a.java"), List.of(), List.of("personstester.txt")),
            new Question("Q2b", "Q2", List.of("Q2b.java"), List.of("DataException.class"),
                    List.of("studentstester.txt")),
            new Question("Q3", "Q3", List.of("Q3.java", "ShapeComparator.java"),
                    List.of("Shape.class", "Circle.class", "Rectangle.class"), List.of()));

    private AssessmentDefinition() {
    }

    public static Map<String, String> sourceFiles() {
        return filesByFolder(Question::sources);
    }

    public static Map<String, String> supportClasses() {
        return filesByFolder(Question::supportClasses);
    }

    public static Map<String, String> runtimeFiles() {
        return filesByFolder(Question::runtimeFiles);
    }

    public static List<String> testerFiles() {
        return QUESTIONS.stream().map(Question::testerFilename).toList();
    }

    public static Question forTester(String filename) {
        return QUESTIONS.stream().filter(question -> question.testerFilename().equals(filename))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown assessment tester: " + filename));
    }

    private static Map<String, String> filesByFolder(Function<Question, List<String>> select) {
        Map<String, String> files = new LinkedHashMap<>();
        QUESTIONS.forEach(question -> select.apply(question).forEach(name -> files.put(name, question.folder())));
        return java.util.Collections.unmodifiableMap(files);
    }

    public record Question(String id, String folder, List<String> sources,
            List<String> supportClasses, List<String> runtimeFiles) {
        public Question {
            sources = List.copyOf(sources);
            supportClasses = List.copyOf(supportClasses);
            runtimeFiles = List.copyOf(runtimeFiles);
        }

        public String testerFilename() {
            return id + "Tester.java";
        }
    }
}
