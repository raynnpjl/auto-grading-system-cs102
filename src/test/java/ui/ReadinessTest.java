package ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReadinessTest {

    @Test
    void tellsTheMarkerWhatToDoNext() {
        assertEquals("Grading in progress. Watch the table; Cancel stops the run.",
                new Readiness(true, false, true, true, true, 2, 2, false).message());
        assertEquals("Validating inputs. Please wait.",
                new Readiness(false, true, false, false, false, 0, 0, false).message());
        assertEquals("Import both folders to get started.",
                new Readiness(false, false, false, false, false, 0, 0, false).message());
        assertEquals("Import student submissions to complete input validation.",
                new Readiness(false, false, true, false, false, 0, 0, false).message());
        assertEquals("Import tester files to complete input validation.",
                new Readiness(false, false, false, true, false, 1, 1, false).message());
        assertEquals("Resolve the reported validation errors before grading.",
                new Readiness(false, false, true, true, false, 0, 1, false).message());
        assertEquals("1 out of 2 submissions ready. The others are skipped until their identity is confirmed.",
                new Readiness(false, false, true, true, true, 1, 2, false).message());
        assertEquals("Both folders are validated and ready.",
                new Readiness(false, false, true, true, true, 2, 2, false).message());
        assertEquals("Graded. Select a student to see why marks were lost, or regrade them.",
                new Readiness(false, false, true, true, true, 2, 2, true).message());
    }
}
