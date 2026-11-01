# Plan 023: Ask before re-importing over graded results, and before closing during a run

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: confirm the excerpts below match the live files.
> If plan 021 ran first, the import code may live partly in `ui/ImportResults.java`.
> The entry points `importSubmissions()` / `importSubmissions(Path)` are unchanged.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (works before or after 021; read the drift note)
- **Category**: bug (usability: error prevention)
- **Planned at**: commit `e413103` + uncommitted working tree, 2026-11-01

## Why this matters

Two actions throw work away without asking:

1. **Importing a submissions folder after grading.** `GradingSession.importSubmissions` clears the latest attempt, so the results table, stat tiles and "Save filled sheet" all reset. A mis-click on "Change folder" after a 10-minute run loses the run. The PDF reports stay on disk.
2. **Closing the window while grading.** `AutoGradingApplication.stop()` calls `controller.shutdown()`, which cancels the run silently.

These fall under Nielsen #3 and #5, and the rubric's "all prompts, alerts… well placed".

## Current state

- `src/main/java/ui/MainViewController.java`:
  - `@FXML private void importSubmissions()` opens a `DirectoryChooser`, then calls `importSubmissions(selected.toPath())`. `void importSubmissions(Path source)` starts with `if (isBusy()) { return; }`.
  - `session.hasResults()` is true once a run has finished, and `progress.snapshot().students()` holds the graded students.
  - `void shutdown()` cancels a running session and deletes the work folder.
  - `boolean isGrading()` is package-private and true while a run is in progress.
- `src/main/java/ui/AutoGradingApplication.java` `start(Stage)` ends with `stage.setScene(scene); stage.show();`. `stop()` calls `controller.shutdown()`.
- Dialog style exemplar: `ui/ScoreSheetConfirmation.java`. It builds an `Alert`, adds `/ui/styles.css` to the dialog pane, and styles the buttons with `dialog-primary-button` / `dialog-secondary-button`.
- Tests: `src/test/java/ui/MainViewGradingTest.java` calls `controller.importSubmissions(Path)` directly in many tests, including after grading in `regradeKeepsOtherStudentsMarks`-style flows. The confirmation must therefore live in the **chooser** handler (`@FXML importSubmissions()`), not in `importSubmissions(Path)`, so the tests keep working without dialogs.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `ui/MainViewController.java`, `ui/AutoGradingApplication.java`, `ui/Confirmations.java` (create), `src/test/java/ui/ConfirmationsTest.java` (create).
**Out of scope:** `GradingSession` (keep its behaviour); the tester import. Replacing testers keeps results, so it needs no guard.

## Steps

### Step 1: `Confirmations`

Create `ui/Confirmations.java`, a final class with a private constructor, following `ScoreSheetConfirmation`'s styling. It needs two pure builders, so they can be tested without showing a window, and one `ask` helper:
- `static Alert replaceResults(int gradedCount)`. Title "Replace graded results?". Header `"Importing another folder clears the results of the " + gradedCount + " graded students."`. Content: "Their PDF reports stay in the grading-reports folder." Buttons: "Import anyway" (OK_DONE, secondary style) and "Keep results" (CANCEL_CLOSE, primary style, default button).
- `static Alert stopGrading()`. Title "Grading is running". Header "Stop grading and close the window?". Content: "Marks from this run are not kept." Buttons: "Stop and close" (OK_DONE, secondary) and "Keep grading" (CANCEL_CLOSE, primary, default).
- `static boolean ask(Alert alert, Window owner)` calls `initOwner`, `showAndWait`, and returns true only for the OK_DONE button.

### Step 2: Guard re-import

In `@FXML private void importSubmissions()`, **before** opening the chooser: if `session != null && session.hasResults()`, call `Confirmations.ask(Confirmations.replaceResults(n), window)`, where `n` is the number of graded students (`progress.snapshot().students()` that are not `GradingStatus.SKIPPED`). Return if it's declined. Leave `importSubmissions(Path)` unchanged.

### Step 3: Guard closing during a run

In `AutoGradingApplication.start`, after `stage.setScene(scene)`:

```java
stage.setOnCloseRequest(event -> {
    if (controller.isGrading() && !Confirmations.ask(Confirmations.stopGrading(), stage)) {
        event.consume();
    }
});
```

`stop()` stays as it is: after the marker confirms, the existing `shutdown()` cancels and cleans up.

### Step 4: Tests

`ConfirmationsTest` (pattern: `ScoreSheetWorkflowTest.warnsAboutStudentsStillToCheckAndDefaultsToReviewFirst`, which starts the JavaFX toolkit once in `@BeforeAll` and uses an `fx(...)` helper). For both builders, assert:
- the header text contains the count, or "Stop grading";
- the default button is the keep/cancel button (`isDefaultButton()`);
- the OK button's text is "Import anyway" / "Stop and close".

Don't call `ask`, because it shows a window.

**Verify**: `./mvnw -q -B clean verify` → exit 0, with 2 new tests.

## Done criteria

- [ ] `grep -n "setOnCloseRequest" src/main/java/ui/AutoGradingApplication.java` → 1 match
- [ ] `grep -n "replaceResults" src/main/java/ui/MainViewController.java` → 1 match
- [ ] All existing UI tests unchanged and passing
- [ ] `plans/README.md` row updated

## STOP conditions

- Any existing test starts showing a dialog (it hangs). The guard must be in the chooser handler only.

## Maintenance notes

- If the session ever keeps results across a re-import, delete the step 2 guard.
