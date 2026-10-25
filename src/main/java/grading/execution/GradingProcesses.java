package grading.execution;

import grading.config.GradingSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Helpers shared by the steps that start JDK tools: locating them and logging what they did. */
final class GradingProcesses {

    private GradingProcesses() {
    }

    /** The path of a JDK tool such as javac or java, from the JDK running this application. */
    static String jdkTool(String name) throws IOException {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path tool = Path.of(System.getProperty("java.home"), "bin", name + (windows ? ".exe" : ""));
        if (!Files.isExecutable(tool)) {
            throw new IOException("A full JDK 21 or newer is required; missing tool: " + tool);
        }
        return tool.toString();
    }

    /** The JVM option that caps a javac or tester run's memory, from grading.max-memory-mb. */
    static String maxHeap() {
        return "-Xmx" + GradingSettings.current().maxMemoryMegabytes() + "m";
    }

    static void log(Consumer<String> log, String questionId, String stage, ProcessResult process) {
        log.accept("[" + questionId + "] " + stage + ": " + process.status() + ", exit " + process.exitCode()
                + ", " + process.duration().toMillis() + " ms");
        if (!process.stdout().isBlank()) {
            log.accept(process.stdout());
        }
        if (!process.stderr().isBlank()) {
            log.accept("[" + questionId + "] " + stage + " stderr:\n" + process.stderr());
        }
    }
}
