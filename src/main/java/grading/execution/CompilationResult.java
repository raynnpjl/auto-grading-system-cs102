package grading.execution;

import grading.common.FileTrees;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A compiled tester and the temporary workspace that holds it. The workspace is deleted on close,
 * so close it after execution, including after a failed compilation.
 */
public record CompilationResult(
        String questionId,
        String testerClass,
        Path workspace,
        ProcessResult process) implements AutoCloseable {

    public Path classesDirectory() {
        return workspace.resolve("classes");
    }

    @Override
    public void close() throws IOException {
        FileTrees.delete(workspace);
    }
}
