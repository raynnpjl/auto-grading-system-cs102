package grading.execution;

import grading.assessment.AssessmentDefinition;
import grading.assessment.QuestionFiles;
import grading.common.FileTrees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Compiles a question's official tester together with one student's sources in a fresh temporary
 * workspace, with the question's support classes and data files staged beside them.
 */
final class TesterCompiler {

    private final GradingLimits limits;
    private final ProcessRunner runner;
    private final Consumer<String> log;

    TesterCompiler(GradingLimits limits, ProcessRunner runner, Consumer<String> log) {
        this.limits = limits;
        this.runner = runner;
        this.log = log;
    }

    /** The caller owns the returned workspace and must close it, even if compilation failed. */
    CompilationResult compile(QuestionFiles files, Path testerFile) throws IOException, InterruptedException {
        String testerFilename = testerFile.getFileName().toString();
        var question = AssessmentDefinition.forTester(testerFilename);
        String questionId = question.id();
        requirePrepared(files, question, testerFile);
        Path workspace = Files.createTempDirectory("cs102-grading-");
        try {
            Path sources = Files.createDirectory(workspace.resolve("sources"));
            Path classes = Files.createDirectory(workspace.resolve("classes"));
            stage(questionId, files.supportClasses(), classes, "support class");
            // Testers open relative filenames from their JVM's working directory.
            stage(questionId, files.runtimeFiles(), workspace, "runtime file");
            Path emptySourcepath = Files.createDirectory(workspace.resolve("empty-sourcepath"));
            List<String> command = new ArrayList<>(List.of(
                    GradingProcesses.jdkTool("javac"), "-J" + limits.maxHeapOption(),
                    "-encoding", "UTF-8", "--release", "21",
                    "-proc:none", "-implicit:none", "-classpath", classes.toString(),
                    "-sourcepath", emptySourcepath.toString(), "-d", classes.toString()));
            for (Path source : Stream.concat(files.sources().stream(), Stream.of(testerFile)).toList()) {
                Path copy = sources.resolve(source.getFileName().toString());
                // no replacement: duplicate filenames must not overwrite a student or official tester
                Files.copy(source, copy);
                command.add(copy.toString());
            }
            log.accept("[" + questionId + "] Compiling " + files.sources().stream()
                    .map(path -> path.getFileName().toString()).toList() + " with " + testerFilename);
            ProcessResult process = runner.run(command, workspace, limits.compileTimeout());
            GradingProcesses.log(log, questionId, "Compilation", process);
            return new CompilationResult(questionId, testerFilename.replaceFirst("\\.java$", ""),
                    workspace, process);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            try {
                FileTrees.delete(workspace);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void stage(String questionId, List<Path> files, Path destination, String role) throws IOException {
        for (Path file : files) {
            Files.copy(file, destination.resolve(file.getFileName()));
            log.accept("[" + questionId + "] Staged " + role + ": " + file.getFileName());
        }
    }

    private static void requirePrepared(QuestionFiles files, AssessmentDefinition.Question question, Path tester)
            throws IOException {
        if (!files.matches(question)) {
            throw new IOException("Incomplete or unexpected files for " + question.id()
                    + ". Import the submission again.");
        }
        files.requireReadable();
        if (!Files.isRegularFile(tester) || !Files.isReadable(tester)) {
            throw new IOException("Required tester is missing or unreadable: " + tester
                    + ". Import the testers again.");
        }
    }
}
