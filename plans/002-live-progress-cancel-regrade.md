# Plan 002: Show live grading progress in a results table, with Cancel and Regrade

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: confirm plan 001 is DONE in `plans/README.md`,
> and `ls src/main/java/grading/session/GradingSession.java` exists.
> Then `git diff --stat -- src/main/java/ui src/main/resources/ui` and read
> the current `MainViewController` before editing; plan 001 rewrote it.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: plans/001-restore-backend-onto-javafx.md
- **Category**: direction (restores lost features)
- **Planned at**: commit `e43d2e2`, 2026-10-30

## Why this matters

The team's feature list promises "One click grades everyone, 4 at a time, with live progress. You can cancel a run or regrade one student." The back end for all of this is already in `grading.session.GradingSession` (`startGrading`, `cancelGrading`, `regrade(username)`) and `grading.progress.ProgressTracker#snapshot()`, restored by plan 001. The JavaFX window only shows a text console, though. The rubric's top application band asks for "all prompts, alerts, statuses… well placed", and Nielsen heuristics #1 (visibility of system status) and #3 (user control) need this view.

## Current state

- After plan 001, `ui/MainViewController` holds a `GradingSession session` and a `ProgressTracker progress`. It polls `progress.snapshot()` with a 250 ms `javafx.animation.Timeline` while `session.grading()` is true, and prints activity into the `TextArea findingsConsole`.
- `ProgressSnapshot` (in `grading.progress`) contains: `running, done, elapsedSeconds, currentStudent, currentStep, List<StudentProgress> students, List<String> activity, List<SimilarityPair> similarPairs`, plus `int total()`.
- `StudentProgress` contains: `username, status` (one of `queued`, `running`, `graded`, `needs-review`, `skipped`, `auto-fixed`), `finished, formatFixed, Map<String, Double> scores` (keyed by question id such as `Q1a`, in question order), `issues, flags, List<Feedback> feedback`, plus `double total()`.
- Question ids for the column headers come from `grading.assessment.AssessmentDefinition.QUESTIONS` (each `Question` has `id()`).
- `GradingSession.cancelGrading()` throws `SessionRefusal` (BUSY) once reports are being written. Show its `getMessage()` and do nothing else.
- `GradingSession.regrade(username)` throws `SessionRefusal` for an unknown student, an unconfirmed identity, or while busy.
- Layout: `src/main/resources/ui/main-view.fxml` has a top row with two file panels, a "run toolbar" `HBox` (`runReadinessText` and `runGradingButton`), and a "console panel" `VBox` (buttons plus `findingsConsole`). Styles are in `src/main/resources/ui/styles.css` (classes such as `run-button`, `score-sheet-button`, `console`).

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./mvnw -q -B -DskipTests compile` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Run app | `./run.sh` | window opens |

## Scope

**In scope:** `src/main/java/ui/MainViewController.java`, `src/main/java/ui/ResultsTable.java` (create), `src/main/resources/ui/main-view.fxml`, `src/main/resources/ui/styles.css`, `src/test/java/ui/**`.

**Out of scope:** everything under `src/main/java/grading/**`; the plagiarism and feedback panels (plan 003); `README.md`.

Do **not** commit; the user commits.

## Steps

### Step 1: Add the progress bar and Cancel button to the run toolbar

In `main-view.fxml`, inside the run-toolbar `HBox`, add:
- `<ProgressBar fx:id="gradingProgress" visible="false" managed="false" prefWidth="220"/>`
- `<Label fx:id="progressText" styleClass="muted"/>`, which shows `"Grading 3 of 6 · alice.2025 · compiling Q2a · 0:42"` built from `done/total/currentStudent/currentStep/elapsedSeconds`
- `<Button fx:id="cancelButton" text="Cancel" visible="false" managed="false" onAction="#cancelGrading" styleClass="score-sheet-button"/>`

In the controller, `cancelGrading()` calls `session.cancelGrading()`, catches `SessionRefusal` and shows the message in `consoleStatus`. While grading, show the bar, label and Cancel button. Hide them when grading ends. Set `gradingProgress.setProgress(total == 0 ? -1 : (double) done / total)`.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 2: Add a results table (`ResultsTable`)

Create `ui/ResultsTable.java`, a small final class that owns a `TableView<StudentProgress>`:
- Columns: Student (`username`); one column per `AssessmentDefinition.QUESTIONS` id, showing `scores.get(id)` or blank; Total (`total()`, one decimal); Status (`status`, with a CSS class per status, e.g. `status-needs-review` in amber and `status-skipped` in grey); Flags (`flags.size()` as a count, empty when 0).
- `void update(ProgressSnapshot snapshot)` replaces the items **in place**, matched by username. That keeps the selection and scroll position across poll ticks: the `TableView` must not flicker or lose the selected row.
- `Optional<StudentProgress> selected()`.

In `main-view.fxml`, put the table into the console panel above `findingsConsole`, so both are visible: the table about 60% of the height and the console 40%. Wrap them in a `SplitPane` with vertical orientation. Call `resultsTable.update(snapshot)` on every poll tick and once when grading ends. Also call it once after `importSubmissions` (`progress.prepare` already filled the rows as `queued` or `skipped`).

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 3: Regrade the selected student

Add a `Regrade selected` button next to Cancel (`fx:id="regradeButton"`, `onAction="#regradeSelected"`). It's enabled only when not grading, `session.hasResults()`, and a row is selected whose status is not `skipped`. It calls `session.regrade(username)` and starts the same poller as `runGrading`. Also add a row context menu item "Regrade this student" that does the same thing.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 4: Tests

In `src/test/java/ui/MainViewGradingTest.java` (pattern: its existing grading test), add:
- `progressTableListsEveryStudentAfterImport`: after `importSubmissions`, the table has one row per submission.
- `cancelStopsGradingAndRestoresInputs`: start grading with a submission that loops forever (copy `test-submissions/loop.q1b.2025`). Call `cancelGrading()`; within 10 s `isGrading()` is false and the Run button is enabled.
- `regradeKeepsOtherStudentsScores`: grade, regrade one student, and check that the other rows' totals are unchanged.

**Verify**: `./mvnw -q -B clean verify` → exit 0, with 3 new tests passing.

### Step 5: Manual check

`./run.sh`, import the testers and `test-submissions/`, then Run. The bar advances, rows change from queued → running → graded, and Cancel during `loop.q1b.2025` stops the run. Regrade one student and only that row changes.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `grep -n "cancelGrading\|regradeSelected" src/main/resources/ui/main-view.fxml` → 2 matches
- [ ] `grep -rn "grading/" <(git diff --name-only)` → no files under `src/main/java/grading/` changed by this plan
- [ ] `plans/README.md` row updated

## STOP conditions

- `GradingSession` lacks `cancelGrading` or `regrade` (plan 001 was not done as written).
- Updating the table requires changing `ProgressTracker` or `StudentProgress`.
- The poller causes visible UI freezes (more than 100 ms per tick). Report this instead of adding threads.

## Maintenance notes

- Statuses are strings set in `ProgressTracker`. If they're ever turned into an enum, `ResultsTable`'s CSS mapping must follow.
- Plan 003 adds a detail pane bound to `resultsTable.selected()`. Keep `selected()` public within the `ui` package.
