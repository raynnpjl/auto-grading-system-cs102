# Plan 003: Show plagiarism matches and per-mistake feedback in the JavaFX window

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: plans 001 and 002 must be DONE in
> `plans/README.md`; `ls src/main/java/ui/ResultsTable.java` must exist.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: plans/002-live-progress-cancel-regrade.md
- **Category**: direction (restores lost bonus features)
- **Planned at**: commit `e43d2e2`, 2026-10-30

## Why this matters

Two bonus features the team presents are "plagiarism detection that ignores starter code and renamed variables" and "feedback for each lost mark, pointing at the line that caused it". After plan 001 both are computed again (`grading.similarity.SimilarityChecker`, `grading.feedback.FeedbackGenerator`) and written into the PDFs. The window doesn't show them, though, so the demo would mean opening PDFs by hand. This plan adds a student detail pane and a "Similar answers" list, which also lets the marker open a student's PDF straight away.

## Current state

- `ProgressSnapshot.similarPairs()` is a `List<SimilarityPair>`. `SimilarityPair(String questionId, String first, String second, double winnowing, double tiling, int firstTokens, int secondTokens, List<LineRange> firstLines, List<LineRange> secondLines, boolean flagged)` has `long percent()`, and there's a static `SimilarityPair.describe(List<LineRange>)` that returns e.g. `"Q1b.java lines 15–23, 25–27"`.
- `StudentProgress` has `issues` (submission problems not auto-corrected), `flags` (e.g. `"Q2a: 91% similar to bob.2025"`) and `List<Feedback> feedback`.
- `grading.feedback.Feedback(String questionId, String kind, String message, Optional<SourceLocation> location)` has `String text()`, which gives `"Q2a.java line 14: <message>"` when a location exists. `SourceLocation(String file, int line, String code)`.
- `grading.reporting.AttemptReportWriter.studentReportOf(Path attemptFolder, String username)` returns `Optional<Path>`, the student's PDF (or the `.txt` fallback). `GradingSession.reportsFolder()` gives the attempt folder.
- `ui/AutoGradingApplication extends Application`, so `getHostServices().showDocument(uri)` can open files in the OS viewer. Pass the `HostServices` into the controller after `loader.load()` through a package-private setter.
- `ResultsTable` (from plan 002) exposes `Optional<StudentProgress> selected()` and owns a `TableView<StudentProgress>`.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./mvnw -q -B -DskipTests compile` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `src/main/java/ui/MainViewController.java`, `src/main/java/ui/AutoGradingApplication.java`, `src/main/java/ui/StudentDetailPane.java` (create), `src/main/java/ui/SimilarityList.java` (create), `src/main/resources/ui/main-view.fxml`, `src/main/resources/ui/styles.css`, `src/test/java/ui/**`.

**Out of scope:** `src/main/java/grading/**`; the thresholds in `grading.properties`; `README.md`. Do **not** commit.

## Steps

### Step 1: Student detail pane

Create `ui/StudentDetailPane.java`, a `VBox` with:
- a header: username, total, status;
- "Submission problems": `issues`, one per line, or "None";
- "Similar answers": `flags`, highlighted with style class `flag`, or "None";
- "Why marks were lost": `feedback`, grouped by `questionId`, each line `feedback.text()`, or "No marks lost";
- a button "Open PDF report". It's enabled when `session.reportsFolder()` is present and `studentReportOf(...)` returns a file, and it calls `hostServices.showDocument(path.toUri().toString())`.

Show it to the right of the results table (a horizontal `SplitPane`), bound to the table's selection. Refresh it on each poll tick only when the selected student's data changed (compare `StudentProgress` with `equals`), so the text doesn't flicker.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 2: Similar answers list

Create `ui/SimilarityList.java`, a `TableView<SimilarityPair>` with columns Question, Student A, Student B, Similarity (`percent() + "%"`), Matching lines (`SimilarityPair.describe(firstLines)` and `describe(secondLines)` joined by " ↔ "). Show only `flagged` pairs by default, with a "Show all compared pairs" checkbox. Put it in a `TabPane` alongside the console: tab "Activity" holds `findingsConsole` and tab "Similar answers (N)" holds this list, where N is the flagged count. Double-clicking a row selects Student A in the results table.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 3: Tests

Pattern: `src/test/java/ui/MainViewGradingTest.java`. Add:
- `copiedAnswerIsListedAsSimilar`: grade `test-submissions/` with the course testers fixture used by the existing tests. The similar-answers list has at least one flagged pair involving `copied.answer.2025`. If the existing test fixture lacks a second student with the same answer, build one in `@TempDir` by copying `copied.answer.2025` under another username.
- `detailPaneShowsFeedbackForLostMarks`: after grading, selecting a student whose total is below full marks shows at least one feedback line.

**Verify**: `./mvnw -q -B clean verify` → exit 0, with 2 new tests passing.

### Step 4: Manual check

`./run.sh`, then grade `test-submissions/`. The "Similar answers" tab shows `copied.answer.2025` matched with the student it copied. Selecting a student shows their problems, flags and feedback with line numbers, and "Open PDF report" opens their PDF.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `git diff --name-only | grep "src/main/java/grading/"` → empty
- [ ] `plans/README.md` row updated

## STOP conditions

- `similarPairs` is always empty when grading `test-submissions/`. The similarity check may be failing quietly; look in the activity log for `Similarity check failed` and report it.
- `HostServices.showDocument` does nothing on the test machine. Leave the button and report; don't swap in `Desktop.open` without asking.

## Maintenance notes

- Similarity thresholds (`similarity.*` in `grading.properties`) change what counts as `flagged`. The UI doesn't hard-code any threshold, so keep it that way.
- Statuses are plain strings from `ProgressTracker`. If they ever become an enum, update `StudentDetailPane`'s header and `ResultsTable` together.
