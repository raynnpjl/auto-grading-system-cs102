package grading.assessment;

import grading.config.ConfigFile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The files of the assessment being graded, shared by import and grading. They are read from
 * {@code assessment.properties}, so another assessment can be graded by changing that file.
 */
public final class AssessmentDefinition {

    private static final ConfigFile CONFIG = ConfigFile.load("assessment");

    public static final List<Question> QUESTIONS = loadQuestions(CONFIG);

    private static final String TEMPLATE_FOLDER = CONFIG.text("assessment.template-folder");

    private AssessmentDefinition() {
    }

    /** The folder name students are told to rename to their username. */
    public static String templateFolder() {
        return TEMPLATE_FOLDER;
    }

    /** The question folders (Q1, Q2, ...) in assessment order, without repeats. */
    public static List<String> questionFolders() {
        return QUESTIONS.stream().map(Question::folder).distinct().toList();
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
        return Collections.unmodifiableMap(files);
    }

    private static List<Question> loadQuestions(ConfigFile config) {
        List<String> ids = config.list("assessment.questions");
        if (ids.isEmpty()) {
            throw new IllegalStateException("assessment.questions in assessment.properties lists no questions");
        }
        return ids.stream().map(id -> {
            String prefix = "question." + id + ".";
            List<String> sources = config.list(prefix + "sources");
            if (sources.isEmpty()) {
                throw new IllegalStateException(prefix + "sources in assessment.properties lists no files");
            }
            return new Question(id, config.text(prefix + "folder"), sources,
                    config.list(prefix + "support-classes"), config.list(prefix + "runtime-files"));
        }).toList();
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
