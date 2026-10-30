# Plan 012: Split `MainViewController` into small single-purpose UI classes

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: plans 008, 009, 010 and 011 must be DONE in
> `plans/README.md` (they edit this file first). Then read the whole of
> `src/main/java/ui/MainViewController.java` before starting. Line numbers
> below are from `e820cd4` and will have shifted a little. Locate code by
> method name.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (pure refactor of the UI; 17 UI tests are the safety net)
- **Depends on**: plans/008, 009, 010, 011
- **Category**: tech-debt
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

The rubric's top code band reads "Applied good object modularization, methods perform one function only" and "reader can easily figure out the intent of each class". Every other class in the project is under 400 lines. `ui/MainViewController.java` is **878 lines**, with about 20 mutable state fields on top of 22 `@FXML` references. It does eight jobs: importing testers and submissions, running and polling grading, building the file trees, the score sheet, saving reports, identity fixes, composing the console text, and the readiness wording. In a Q&A this is the class that undercuts "clean design". The behaviour must stay exactly the same; this is a move-and-delegate refactor only.

## Current state

Method map at `e820cd4` (line: method):

```
183 initialize            224 importTesterFiles(Path)   256 importSubmissions(Path)
282 runGrading            288 regradeSelected           292 regrade
297 cancelGrading         311 startRun                  333 pollProgress
347 progressLine (static) 361 finishRun                 390 showSnapshot
406 openReport            419 useReportsFolder          464 shutdown
516 importScoreSheet(Path)  549 saveFilledSheet (41 lines)  591 chooseFilledSheetTarget
605 scoreSheetStatus      632 saveReports(Path)         656 showSubmissions
707 applyConfirmedIdentities  727 showTesterResult      751 addNormalizedTree
770 statusSuffix (static) 781 refreshConsole            787 updateButtons (39 lines)
835 formatSubmissionReadiness (static)                  849 runInBackground
```

State fields include `scoreSheet`, `scoreSheetProblem`, `scoreSheetUpdater`, the four console sections (`testerReport`, `submissionReport`, `gradingReport`, `scoreSheetReport`), `poller` (a `Timeline`), the `runInProgress`/`inputTaskRunning`/`testerImported`/`submissionsImported` flags, and `submissions`.

Package-private hooks used by the tests in `src/test/java/ui/` **must keep their names and signatures**: `importTesterFiles(Path)`, `importSubmissions(Path)`, `importScoreSheet(Path)`, `saveFilledSheet(Predicate<ScoreSheetPreview>, Supplier<Optional<Path>>)`, `saveReports(Path)`, `useReportsFolder(Path)`, `latestReportsFolder()`, `workFolder()`, `similarityList()`, `resultsTable()`, `detailText()`, `disableAutomaticIdentityPrompt()`, `shutdown()`, `isGrading()`, `scoreSheetConfirmation(ScoreSheetPreview)`, and the static `formatSubmissionReadiness(long, long)` (used by `MainViewWiringTest`). The `@FXML` handler names are referenced by `main-view.fxml` and checked by `MainViewWiringTest`.

Exemplars of the existing small-class style in the same package: `ui/FindingsText.java` (final class, private constructor, static formatting methods) and `ui/SimilarityList.java` (owns one control, exposes `view()` and `update(...)`).

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Compile | `./mvnw -q -B -DskipTests compile` | exit 0 |
| UI tests | `./mvnw -q -B test -Dtest='MainView*Test,ScoreSheetWorkflowTest,StartupCheckTest'` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Size | `wc -l src/main/java/ui/MainViewController.java` | ≤ 520 |

## Scope

**In scope:** `src/main/java/ui/MainViewController.java`, plus these new files in `src/main/java/ui/`: `ConsoleText.java`, `SubmissionTrees.java`, `ScoreSheetActions.java`, `RunMonitor.java`, `Readiness.java`. New tests in `src/test/java/ui/` for the pure classes.

**Out of scope:** `main-view.fxml` and `styles.css`; anything under `src/main/java/grading/**`; changing any user-visible text. Don't change test assertions except where a hook moved as described below.

## Steps

Run the **UI tests** command after every step; all must pass before moving on.

### Step 1: `ConsoleText`, which holds the console's four sections

A final class with fields `scoreSheet`, `grading`, `testers` and `submissions` (all `String`, default `""`), setters `setScoreSheet(String)` and so on, and `String text()`. `text()` returns exactly what `refreshConsole` builds today: `scoreSheet + grading + testers + separator + submissions`, where the separator is `"\n"` only when both testers and submissions are non-blank. The controller keeps one `ConsoleText console` field; `refreshConsole()` becomes `findingsConsole.setText(console.text()); findingsConsole.positionCaret(0);`. Remove the four `*Report` fields. Note that `submissionReport += …` in `applyConfirmedIdentities` becomes `console.appendSubmissions(String)`.

### Step 2: `SubmissionTrees`, for the file trees

A final utility class with `static TreeItem<String> testers(TesterValidationResult)` (the tree part of `showTesterResult`) and `static TreeItem<String> submissions(List<SubmissionValidationResult>)` (the tree part of `showSubmissions`, plus `addNormalizedTree` and `statusSuffix` moved in as private static methods). The controller calls `testerFiles.setRoot(SubmissionTrees.testers(result))`.

### Step 3: `ScoreSheetActions`, the score sheet logic without dialogs

It owns `scoreSheet`, `scoreSheetProblem` and the `ScoreSheetUpdater`, and has these methods:
- `String importSheet(Path)`: returns the console text, either the status text or the "Score sheet not imported" text, exactly as today.
- `boolean hasSheet()`, `Path sheetPath()`, `int rowCount()`.
- `String status(boolean hasResults)`: today's `scoreSheetStatus()`.
- `Result saveFilled(ScoreSummary scores, Predicate<ScoreSheetPreview> confirmation, Supplier<Optional<Path>> target)`, where `Result` is a nested record `(String consoleText, String statusText, String consoleStatus)` and `consoleText` may be null when nothing changed (cancelled). Copy the current logic and messages verbatim, including the "Choose a different file name; the original sheet is kept unchanged." refusal.

`chooseFilledSheetTarget()` (FileChooser) **stays in the controller**, since it needs the window. The controller's `saveFilledSheet(...)` hook keeps its signature and just delegates, then applies the three texts.

### Step 4: `RunMonitor`, for polling during a run

It owns the `Timeline`. Constructor: `RunMonitor(Supplier<ProgressSnapshot> snapshots, BooleanSupplier running, Consumer<ProgressSnapshot> onTick, Consumer<ProgressSnapshot> onFinished)`. Methods: `start()`, `stop()`, and `static String progressLine(ProgressSnapshot)` (moved verbatim with its `SECONDS_FORMAT`/`SECONDS_PER_MINUTE` constants and `POLL_INTERVAL`). `pollProgress()` in the controller disappears. `onTick` does today's "still grading" branch, and `onFinished` is today's `finishRun`.

### Step 5: `Readiness`, the readiness wording

A record `Readiness(boolean runInProgress, boolean validating, boolean testerImported, boolean submissionsImported, boolean inputsReady, long ready, long total, boolean hasResults)` with `String message()`. It returns exactly the strings the `if/else` chain in `updateButtons()` sets on `runReadinessText` today. Move `formatSubmissionReadiness` there as a static method, and keep a one-line delegating `static String formatSubmissionReadiness(long, long)` in the controller for `MainViewWiringTest`. `updateButtons()` then only sets the `disable`/`visible` flags plus `runReadinessText.setText(new Readiness(...).message())`.

### Step 6: Unit tests for the pure classes

Create `src/test/java/ui/ReadinessTest.java` with one assertion per branch: 8 messages, including "1 out of 2 submissions ready. The others are skipped until their identity is confirmed."

Create `src/test/java/ui/ConsoleTextTest.java` covering the separator rule.

These don't need JavaFX started. `TreeItem` isn't used here; `Readiness` and `ConsoleText` are plain Java.

### Step 7: Check size and method length

**Verify**: `wc -l src/main/java/ui/MainViewController.java` → ≤ 520. Run this check for methods over 30 lines:

```sh
awk '/^    [a-zA-Z@].*\) (throws [A-Za-z, ]+ )?\{$/{s=NR;n=$0} /^    \}$/{if(s&&NR-s>30)print FILENAME": "n" "NR-s; s=0}' src/main/java/ui/*.java
```

→ no output.

**Verify**: `./mvnw -q -B clean verify` → exit 0.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0 (all existing UI tests unchanged and passing, plus the new ones)
- [ ] `MainViewController.java` ≤ 520 lines; no method in `ui/` over 30 lines
- [ ] `git diff --stat -- src/main/resources src/main/java/grading` → empty
- [ ] `plans/README.md` row updated

## STOP conditions

- A UI test fails and the fix would mean changing what the user sees, or a test assertion.
- A moved method turns out to need the `Window` or an `@FXML` control in a class other than the controller.
- The controller can't get under 520 lines without moving `@FXML` handlers out. Report the actual count instead.

## Maintenance notes

- New UI features go in their own class with `view()`/`update()` or pure formatting methods; the controller only wires them.
- Plans 013–015 add UI. Write them against these new classes.
