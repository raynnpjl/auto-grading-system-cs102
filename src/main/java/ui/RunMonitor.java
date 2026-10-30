package ui;

import grading.progress.ProgressSnapshot;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * Asks for the run's progress a few times a second while grading runs, and reports once when it has ended.
 * Runs on the JavaFX thread, so the callbacks may change the window directly.
 */
final class RunMonitor {

    private static final Duration POLL_INTERVAL = Duration.millis(250);
    private static final String ELAPSED_FORMAT = "%d:%02d";
    private static final int SECONDS_PER_MINUTE = 60;

    private final Timeline poller;

    /**
     * @param snapshots  the current state of the run
     * @param running    whether the run is still going
     * @param onTick     called with each snapshot while the run is going
     * @param onFinished called once with the final snapshot when the run has ended
     */
    RunMonitor(Supplier<ProgressSnapshot> snapshots, BooleanSupplier running, Consumer<ProgressSnapshot> onTick,
            Consumer<ProgressSnapshot> onFinished) {
        poller = new Timeline(new KeyFrame(POLL_INTERVAL, event -> {
            ProgressSnapshot snapshot = snapshots.get();
            if (running.getAsBoolean()) {
                onTick.accept(snapshot);
            } else {
                stop();
                onFinished.accept(snapshot);
            }
        }));
        poller.setCycleCount(Animation.INDEFINITE);
    }

    void start() {
        poller.play();
    }

    void stop() {
        poller.stop();
    }

    /** Such as "Grading 3 of 6 · alice.2025 · compiling Q2a · 0:42". */
    static String progressLine(ProgressSnapshot snapshot) {
        StringBuilder line = new StringBuilder("Grading ").append(snapshot.done()).append(" of ")
                .append(snapshot.total());
        if (!snapshot.currentStudent().isEmpty()) {
            line.append(" · ").append(snapshot.currentStudent());
        }
        if (!snapshot.currentStep().isEmpty()) {
            line.append(" · ").append(snapshot.currentStep());
        }
        long seconds = snapshot.elapsedSeconds();
        return line.append(" · ").append(String.format(Locale.ROOT, ELAPSED_FORMAT,
                seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)).toString();
    }
}
