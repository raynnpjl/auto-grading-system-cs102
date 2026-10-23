package grading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import grading.assessment.AssessmentDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.tools.ToolProvider;

/** Minimal valid bytecode and data for tests that execute synthetic assessments. */
public final class AssessmentFixtures {
    private AssessmentFixtures() {
    }

    public static void writeDependencies(Path student) throws IOException {
        Path support = Files.createTempDirectory("assessment-test-support-");
        try {
            Path source = Files.writeString(support.resolve("Support.java"), """
                    class Shape { public static double score() { return 4; } }
                    class Circle {}
                    class Rectangle {}
                    class DataException extends RuntimeException {
                        public DataException() {}
                        public DataException(String message) { super(message); }
                    }
                    """);
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "--release", "21", "-d", support.toString(), source.toString()));
            for (var file : AssessmentDefinition.supportClasses().entrySet()) {
                Path folder = Files.createDirectories(student.resolve(file.getValue()));
                Files.copy(support.resolve(file.getKey()), folder.resolve(file.getKey()));
            }
            for (var file : AssessmentDefinition.runtimeFiles().entrySet()) {
                Path folder = Files.createDirectories(student.resolve(file.getValue()));
                Files.writeString(folder.resolve(file.getKey()), "test data");
            }
        } finally {
            try (var files = Files.walk(support)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }
}
