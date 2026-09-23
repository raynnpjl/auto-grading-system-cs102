package grading.execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class BoundedProcessRunner {

    static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private static final long POLL_MILLIS = 20;
    private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(2);

    ProcessResult run(List<String> command, Path directory, Duration timeout)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        // keep ambient Java options and application classpaths out of grading processes.
        for (String variable : List.of("CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
            builder.environment().remove(variable);
        }
        long started = System.nanoTime();
        Process process = builder.start();
        OutputCapture stdout = new OutputCapture(process.getInputStream());
        OutputCapture stderr = new OutputCapture(process.getErrorStream());
        Thread stdoutReader = Thread.ofPlatform().daemon().name("grading-stdout").start(stdout);
        Thread stderrReader = Thread.ofPlatform().daemon().name("grading-stderr").start(stderr);
        Set<ProcessHandle> descendants = new LinkedHashSet<>();
        ProcessResult.Status status = ProcessResult.Status.EXITED;
        try {
            process.getOutputStream().close();
            while (true) {
                rememberDescendants(process, descendants);
                if (stdout.overflow || stderr.overflow) {
                    status = ProcessResult.Status.OUTPUT_LIMIT_EXCEEDED;
                    break;
                }
                if (!process.isAlive()) {
                    break;
                }
                long remaining = timeout.toNanos() - (System.nanoTime() - started);
                if (remaining <= 0) {
                    status = ProcessResult.Status.TIMED_OUT;
                    break;
                }
                process.waitFor(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS)),
                        TimeUnit.NANOSECONDS);
            }
        } finally {
            // cleanup also runs on caller interruption, and kills observed children after normal exit
            terminate(process, descendants);
        }
        boolean terminated = status != ProcessResult.Status.EXITED;
        finishReader(stdoutReader, stdout, terminated);
        finishReader(stderrReader, stderr, terminated);
        if (status == ProcessResult.Status.EXITED && (stdout.overflow || stderr.overflow)) {
            status = ProcessResult.Status.OUTPUT_LIMIT_EXCEEDED;
        }
        return new ProcessResult(status, process.exitValue(), stdout.text(), stderr.text(),
                Duration.ofNanos(System.nanoTime() - started));
    }

    private void terminate(Process process, Set<ProcessHandle> descendants) throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            IOException discoveryFailure = null;
            try {
                rememberDescendants(process, descendants);
            } catch (IOException exception) {
                discoveryFailure = exception;
            }
            descendants.forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            long deadline = System.nanoTime() + CLEANUP_TIMEOUT.toNanos();
            while (process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
                if (System.nanoTime() >= deadline) {
                    throw new IOException("Could not confirm termination of the grading process tree.");
                }
                try {
                    Thread.sleep(POLL_MILLIS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (discoveryFailure != null) {
                throw discoveryFailure;
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void rememberDescendants(Process process, Set<ProcessHandle> descendants) throws IOException {
        try (var children = process.descendants()) {
            children.forEach(descendants::add);
        } catch (RuntimeException exception) {
            throw new IOException("Process-tree inspection is unavailable; grading requires process-control permissions.", exception);
        }
    }

    private void finishReader(Thread reader, OutputCapture output, boolean terminated)
            throws IOException, InterruptedException {
        reader.join(CLEANUP_TIMEOUT.toMillis());
        if (reader.isAlive()) {
            throw new IOException("Timed out reading grading process output.");
        }
        // destroyForcibly can close a pipe while its reader is draining buffered output.
        // The time/output limit remains the result of an intentionally terminated process.
        if (output.failure != null && !terminated) {
            throw output.failure;
        }
    }

    private static final class OutputCapture implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private volatile boolean overflow;
        private IOException failure;

        private OutputCapture(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try (input) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    int available = MAX_OUTPUT_BYTES - bytes.size();
                    bytes.write(buffer, 0, Math.min(available, count));
                    if (count > available) {
                        overflow = true;
                    }
                }
            } catch (IOException exception) {
                failure = exception;
            }
        }

        private String text() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
