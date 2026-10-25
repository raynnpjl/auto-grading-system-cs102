package grading.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** Runs a compiled tester's main method in a new, time-limited JVM and turns its output into a result. */
final class TesterRunner {

    private final Duration timeout;
    private final ProcessRunner runner;
    private final Consumer<String> log;

    TesterRunner(Duration timeout, ProcessRunner runner, Consumer<String> log) {
        this.timeout = timeout;
        this.runner = runner;
        this.log = log;
    }

    /** Does not close the compilation workspace. */
    QuestionGradingResult run(CompilationResult compilation) throws IOException, InterruptedException {
        String questionId = compilation.questionId();
        ProcessResult compiler = compilation.process();
        if (!compiler.succeeded()) {
            QuestionGradingResult.Status status = switch (compiler.status()) {
                case TIMED_OUT -> QuestionGradingResult.Status.COMPILATION_TIMEOUT;
                case OUTPUT_LIMIT_EXCEEDED -> QuestionGradingResult.Status.OUTPUT_LIMIT_EXCEEDED;
                case EXITED -> QuestionGradingResult.Status.COMPILATION_ERROR;
            };
            return QuestionGradingResult.zero(questionId, status, compiler, null, "Compilation did not succeed.");
        }
        if (!Files.isDirectory(compilation.classesDirectory())) {
            throw new IOException("Compilation workspace is closed or missing.");
        }
        log.accept("[" + questionId + "] Running " + compilation.testerClass());
        ProcessResult execution = runner.run(List.of(
                GradingProcesses.jdkTool("java"), GradingProcesses.maxHeap(), "-XX:-CreateCoredumpOnCrash",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Djava.io.tmpdir=" + compilation.workspace(),
                "-cp", compilation.classesDirectory().toString(),
                compilation.testerClass()), compilation.workspace(), timeout);
        GradingProcesses.log(log, questionId, "Execution", execution);
        return result(compilation, execution);
    }

    private static QuestionGradingResult result(CompilationResult compilation, ProcessResult execution) {
        String questionId = compilation.questionId();
        ProcessResult compiler = compilation.process();
        TesterOutput output = TesterOutput.parse(execution.stdout());
        // The supplied testers award one mark per exact "Passed" line, so completed test cases keep
        // their marks; the test case that was still running earns none.
        if (execution.status() == ProcessResult.Status.TIMED_OUT) {
            return new QuestionGradingResult(questionId, QuestionGradingResult.Status.EXECUTION_TIMEOUT,
                    output.passed(), output.passed(), output.failed(), compiler, execution,
                    "Execution timed out. Retained " + output.passed()
                            + " mark(s) from completed one-mark tests; unfinished tests earn no marks.");
        }
        if (!execution.succeeded()) {
            QuestionGradingResult.Status status = execution.status() == ProcessResult.Status.OUTPUT_LIMIT_EXCEEDED
                    ? QuestionGradingResult.Status.OUTPUT_LIMIT_EXCEEDED
                    : QuestionGradingResult.Status.RUNTIME_ERROR;
            return QuestionGradingResult.zero(questionId, status, compiler, execution,
                    "Tester did not finish successfully.");
        }
        if (output.score().isEmpty()) {
            return QuestionGradingResult.zero(questionId, QuestionGradingResult.Status.INVALID_SCORE, compiler,
                    execution, "Tester must print a finite, nonnegative score as its last nonblank stdout line.");
        }
        return new QuestionGradingResult(questionId, QuestionGradingResult.Status.GRADED,
                output.score().getAsDouble(), output.passed(), output.failed(), compiler, execution,
                "Graded using the tester's final score.");
    }
}
