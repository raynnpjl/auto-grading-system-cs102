package ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConsoleTextTest {

    @Test
    void separatesTheTwoImportReportsOnlyWhenBothArePresent() {
        ConsoleText console = new ConsoleText();
        console.setTesters("testers\n");
        assertEquals("testers\n", console.text());
        console.setSubmissions("submissions\n");
        assertEquals("testers\n\nsubmissions\n", console.text());
        console.setScoreSheet("sheet\n");
        console.setGrading("grading\n");
        assertEquals("sheet\ngrading\ntesters\n\nsubmissions\n", console.text());
    }
}
