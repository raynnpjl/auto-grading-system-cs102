package grading.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class TesterOutputTest {

    @Test
    void countsVerdictsAndReadsTheFinalScore() {
        TesterOutput output = TesterOutput.parse("""
                Test 1: first
                Passed
                Test 2: second
                Failed -> Exception
                Test 3: third
                Failed
                1.0

                """);

        assertEquals(1, output.passed());
        assertEquals(2, output.failed());
        assertEquals(OptionalDouble.of(1.0), output.score());
    }

    @Test
    void hasNoScoreWhenTheLastLineIsNotAFiniteNonnegativeNumber() {
        for (String stdout : new String[] {"", "Passed", "-1", "NaN", "Infinity", "1.0\ntrailing"}) {
            assertTrue(TesterOutput.parse(stdout).score().isEmpty(), stdout);
        }
    }
}
