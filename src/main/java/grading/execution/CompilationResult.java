package grading.execution;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

// Owns the temporary workspace. Close after execution, including failed compilations.
public record CompilationResult(
        String questionId,
        String testerClass,
        Path workspace,
        List<Path> supportClasspath,
        ProcessResult process) implements AutoCloseable {

    public CompilationResult {
        supportClasspath = List.copyOf(supportClasspath);
    }

    public Path classesDirectory() {
        return workspace.resolve("classes");
    }

    @Override
    public void close() throws IOException {
        deleteWorkspace(workspace);
    }

    static void deleteWorkspace(Path workspace) throws IOException {
        if (!Files.exists(workspace)) {
            return;
        }
        Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
