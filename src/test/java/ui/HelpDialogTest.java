package ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import grading.progress.GradingStatus;
import org.junit.jupiter.api.Test;

class HelpDialogTest {

    @Test
    void textNamesEveryButtonAndStatus() {
        String text = HelpDialog.text();
        for (String button : new String[] {"Import folder", "Resolve issues", "Run grading", "Cancel",
                "Regrade selected", "Import score sheet", "Save filled sheet", "Save reports"}) {
            assertTrue(text.contains(button), button);
        }
        for (GradingStatus status : GradingStatus.values()) {
            assertTrue(text.contains("- " + status.label() + ": "), status.label());
        }
    }
}
