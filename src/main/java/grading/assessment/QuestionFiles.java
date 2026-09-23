package grading.assessment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Explicit inputs for one question part; the grader never searches neighbouring files. */
public record QuestionFiles(List<Path> sources, List<Path> supportClasses, List<Path> runtimeFiles) {

    public QuestionFiles {
        sources = List.copyOf(sources);
        supportClasses = List.copyOf(supportClasses);
        runtimeFiles = List.copyOf(runtimeFiles);
    }

    public boolean matches(AssessmentDefinition.Question question) {
        return hasNames(sources, question.sources())
                && hasNames(supportClasses, question.supportClasses())
                && hasNames(runtimeFiles, question.runtimeFiles());
    }

    public void requireReadable() throws IOException {
        for (Path file : Stream.of(sources, supportClasses, runtimeFiles).flatMap(List::stream).toList()) {
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
                throw new IOException("Required file is missing or unreadable: " + file + ". Import the submission again.");
            }
        }
    }

    private static boolean hasNames(List<Path> files, List<String> expected) {
        return files.stream().map(file -> file.getFileName().toString()).sorted().toList()
                .equals(expected.stream().sorted().toList());
    }
}
