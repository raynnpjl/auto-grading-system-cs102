package ui;

/**
 * What the marker should do next, shown under "Prepare a grading run".
 *
 * @param runInProgress       whether grading is running
 * @param validating          whether an import is being validated
 * @param testerImported      whether a tester folder was imported
 * @param submissionsImported whether a submissions folder was imported
 * @param inputsReady         whether valid testers and at least one gradable submission are loaded
 * @param ready               how many submissions can be graded
 * @param total               how many submissions were imported
 * @param hasResults          whether a grading run has finished
 */
record Readiness(boolean runInProgress, boolean validating, boolean testerImported, boolean submissionsImported,
        boolean inputsReady, long ready, long total, boolean hasResults) {

    /** Such as "4 out of 6 submissions ready". */
    static String submissionCount(long ready, long total) {
        return ready + " out of " + total + " submissions ready";
    }

    String message() {
        if (runInProgress) {
            return "Grading in progress. Watch the table; Cancel stops the run.";
        } else if (validating) {
            return "Validating inputs. Please wait.";
        } else if (testerImported && submissionsImported) {
            return bothImported();
        } else if (testerImported) {
            return "Import student submissions to complete input validation.";
        } else if (submissionsImported) {
            return "Import tester files to complete input validation.";
        }
        return "Import both folders to get started.";
    }

    private String bothImported() {
        if (!inputsReady) {
            return "Resolve the reported validation errors before grading.";
        } else if (ready < total) {
            return submissionCount(ready, total) + ". The others are skipped until their identity is confirmed.";
        } else if (hasResults) {
            return "Graded. Select a student to see why marks were lost, or regrade them.";
        }
        return "Both folders are validated and ready.";
    }
}
