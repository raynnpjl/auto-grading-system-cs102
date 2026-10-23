package grading.execution;

import grading.submission.SubmissionValidationResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Grades several students at the same time. Results are returned in input order, and each student's
 * log is written as one block when that student finishes, so students never interleave in the log.
 */
final class BatchGrader {

    /** Grades one student, writing that student's log to the given destination. */
    @FunctionalInterface
    interface StudentGrader {
        StudentGradingResult grade(SubmissionValidationResult submission, Consumer<String> log)
                throws InterruptedException;
    }

    // Long enough for interrupted graders to kill their processes, which each take at most a few seconds.
    private static final Duration WORKER_SHUTDOWN_WAIT = Duration.ofSeconds(30);

    private final int parallelStudents;
    private final Consumer<String> log;
    private final Object logLock = new Object();

    BatchGrader(int parallelStudents, Consumer<String> log) {
        if (parallelStudents < 1) {
            throw new IllegalArgumentException("At least one student must be graded at a time.");
        }
        this.parallelStudents = parallelStudents;
        this.log = log;
    }

    /** Interruption cancels the batch once every running grading process has been terminated. */
    List<StudentGradingResult> grade(List<SubmissionValidationResult> submissions, StudentGrader grader)
            throws InterruptedException {
        if (submissions.isEmpty()) {
            return List.of();
        }
        AtomicInteger finished = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(Math.min(parallelStudents, submissions.size()),
                Thread.ofPlatform().name("grading-worker-", 1).factory());
        try {
            List<Future<StudentGradingResult>> pending = new ArrayList<>();
            for (SubmissionValidationResult submission : submissions) {
                pending.add(workers.submit(() -> gradeOne(submission, grader, finished, submissions.size())));
            }
            List<StudentGradingResult> results = new ArrayList<>();
            for (Future<StudentGradingResult> result : pending) {
                results.add(await(result));
            }
            return List.copyOf(results);
        } finally {
            stop(workers);
        }
    }

    private StudentGradingResult gradeOne(SubmissionValidationResult submission, StudentGrader grader,
            AtomicInteger finished, int total) throws InterruptedException {
        List<String> studentLog = new ArrayList<>();
        try {
            return grader.grade(submission, studentLog::add);
        } finally {
            synchronized (logLock) {
                studentLog.forEach(log);
                log.accept("[progress] " + finished.incrementAndGet() + " of " + total + " students graded");
            }
        }
    }

    private static StudentGradingResult await(Future<StudentGradingResult> result) throws InterruptedException {
        try {
            return result.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof InterruptedException interrupted) {
                throw interrupted;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Grading a student failed unexpectedly.", cause);
        }
    }

    // Interrupts unfinished graders and waits for them, so no grading process outlives the batch.
    private static void stop(ExecutorService workers) {
        workers.shutdownNow();
        boolean interrupted = Thread.interrupted();
        try {
            workers.awaitTermination(WORKER_SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            interrupted = true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
