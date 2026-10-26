package grading.assessment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Explicit inputs for one question part; the grader never searches neighbouring files. */
public record QuestionFiles(List<Path> sources, List<Path> supportClasses, List<Path> runtimeFiles) {

    public QuestionFiles {
        sources = List.copyOf(sources);
        supportClasses = List.copyOf(supportClasses);
        runtimeFiles = List.copyOf(runtimeFiles);
    }

    /** Whether these are exactly the files the question needs, by name. */
    public boolean matches(AssessmentDefinition.Question question) {
        return hasNames(sources, question.sources())
                && hasNames(supportClasses, question.supportClasses())
                && hasNames(runtimeFiles, question.runtimeFiles());
    }

    /** The question's required files that are absent: sources, support classes and runtime files. */
    public List<String> missingFiles(AssessmentDefinition.Question question) {
        Set<String> present = Stream.of(sources, supportClasses, runtimeFiles).flatMap(List::stream)
                .map(file -> file.getFileName().toString())
                .collect(Collectors.toSet());
        return Stream.of(question.sources(), question.supportClasses(), question.runtimeFiles())
                .flatMap(List::stream)
                .filter(name -> !present.contains(name))
                .toList();
    }

    /**
     * Checks that every file still exists and can be read before it is compiled.
     *
     * @throws IOException naming the first file that is missing or unreadable
     */
    public void requireReadable() throws IOException {
        for (Path file : Stream.of(sources, supportClasses, runtimeFiles).flatMap(List::stream).toList()) {
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
                throw new IOException("Required file is missing or unreadable: " + file
                        + ". Import the submission again.");
            }
        }
    }

    private static boolean hasNames(List<Path> files, List<String> expected) {
        return files.stream().map(file -> file.getFileName().toString()).sorted().toList()
                .equals(expected.stream().sorted().toList());
    }
}
