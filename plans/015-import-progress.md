# Plan 015: Show progress while submissions are being imported

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: plans 011 and 012 must be DONE. Then
> `git diff --stat e820cd4..HEAD -- src/main/java/grading/submission/SubmissionPreparationService.java`
> must be empty. If it isn't, compare with the excerpt below.

## Status

- **Priority**: P3
- **Effort**: S–M
- **Risk**: LOW
- **Depends on**: plans/011-workspace-cleanup.md and plans/012-split-main-view-controller.md (same files)
- **Category**: direction (usability)
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

Importing a folder of ZIPs unpacks, validates and normalizes every submission. For a real cohort (40+ students) that takes a while, and the window only says "Validating submissions" with no indication of progress. That falls short of Nielsen #1 (visibility of system status). Grading already has a progress bar, so import should have one too.

## Current state

- `src/main/java/grading/submission/SubmissionPreparationService.java` `prepareBatch(Path input, Path workspace)` (~:58):

```java
        requireReadableInput(input);
        Files.createDirectories(workspace);
        List<SubmissionValidationResult> results = new ArrayList<>();
        Set<String> outputNames = new HashSet<>();
        for (Path submission : discovery.discover(input, workspace)) {
            try {
                results.add(prepareOne(SubmissionSource.of(submission, zipExtractor), workspace, outputNames));
            } catch (Exception exception) {
                ... // becomes a SUBMISSION_UNREADABLE result
            }
        }
        return List.copyOf(results);
```

- `src/main/java/grading/session/GradingSession.java` `importSubmissions(Path input)` calls `submissionService.prepareBatch(input, workspace)`.
- `src/main/java/ui/MainViewController.java` `importSubmissions(Path)` runs `session.importSubmissions(source)` through the controller's `runInBackground(Callable<T>, String, Consumer<T>, Consumer<Throwable>)`, which wraps a JavaFX `Task`.
- The run toolbar already has `progressBox` (a `VBox`), `gradingProgress` (`ProgressBar`) and `progressText` (`Label`). They're shown during grading with `setVisible(progressBox, true)`.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `grading/submission/ImportProgress.java` (create), `SubmissionPreparationService.java`, `GradingSession.java`, `MainViewController.java`, tests in `src/test/java/grading/submission/SubmissionPreparationServiceTest.java` and `src/test/java/ui/MainViewGradingTest.java`.
**Out of scope:** tester import (it's fast); grading progress.

## Steps

### Step 1: A listener

```java
/** Told about each submission as a batch import prepares it. */
@FunctionalInterface
public interface ImportProgress {
    ImportProgress NONE = (done, total, name) -> { };
    /** @param done how many are prepared so far  @param total how many were found  @param name the one just prepared */
    void prepared(int done, int total, String name);
}
```

(Format it across lines, with proper Javadoc per parameter.)

### Step 2: Report from `prepareBatch`

Add an overload `prepareBatch(Path input, Path workspace, ImportProgress progress)`. Store the discovered list in a variable to get `total`, and call `progress.prepared(i + 1, total, submission.getFileName().toString())` after each submission, including failed ones. The old two-argument method delegates with `ImportProgress.NONE`. In `GradingSession`, add `importSubmissions(Path input, ImportProgress progress)`, and have the old method delegate.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 3: Show it

In the controller's `importSubmissions(Path)`, the background work calls `session.importSubmissions(source, (done, total, name) -> Platform.runLater(() -> showImportProgress(done, total, name)))`. `showImportProgress` makes `progressBox` visible, sets `gradingProgress` to `done / (double) total`, and sets `progressText` to `"Preparing " + done + " of " + total + " · " + name`. Hide `progressBox` again in both the success and failure callbacks.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 4: Tests

- `SubmissionPreparationServiceTest.reportsProgressForEverySubmission`: prepare a folder with 2 student folders (reuse that test class's fixture helpers) with a recording listener. Assert the calls were `(1, 2, …)` and `(2, 2, …)`.
- `MainViewGradingTest`: after an import of the 2-student batch, assert `progressBox` is not visible (it was hidden after import).

**Verify**: `./mvnw -q -B clean verify` → exit 0.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0, with 2 new assertions
- [ ] `plans/README.md` row updated

## STOP conditions

- `discovery.discover` returns something that isn't a list (so the total isn't known up front). Report it; don't switch to an indeterminate bar without asking.

## Maintenance notes

- If import ever becomes parallel, `done` must come from an `AtomicInteger`.
