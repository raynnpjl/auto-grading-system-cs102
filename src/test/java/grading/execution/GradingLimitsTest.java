package grading.execution;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GradingLimitsTest {

    @Test
    void releaseNewerThanTheRunningJdkIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GradingLimits(Duration.ofSeconds(1),
                Duration.ofSeconds(1), 64, 1, Runtime.version().feature() + 1));
    }
}
