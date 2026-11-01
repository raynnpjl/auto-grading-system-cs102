# Plan 026: Test the stat tiles, the collapsing import panels and the "to check" status

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: plans 020–025 should be DONE, so these tests
> assert the final behaviour. Confirm `ui/StatTiles.java` and
> `ui/ImportPanel.java` match the excerpts below.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW (tests only)
- **Depends on**: plans 020–025 (run last among the code plans)
- **Category**: tests
- **Planned at**: commit `e413103` + uncommitted working tree, 2026-11-01

## Why this matters

The rubric's application criterion asks for core features "tested end-to-end". The newest UI has no assertions at all:
- the stat tiles (Graded, Class average out of 20, Auto-fixed, the amber Need review value);
- the import panels collapsing to a summary with "Change folder" and "Show files";
- the Reason column text.

These are exactly what the demo shows. `grep -rln "StatTiles\|ImportPanel\|Show files\|Change folder" src/test` → none.

## Current state

- `src/main/java/ui/StatTiles.java`:
  - `StatTiles()` creates four labels in tiles and gives the Need review value the id `needReviewCount`.
  - `HBox view()`.
  - `void update(ProgressSnapshot snapshot)` sets:
    - Graded `done/total`, where done means finished and not SKIPPED;
    - Class average `Marks.outOf(mean total of done, AssessmentDefinition.totalMarks())`, or `—` when none are done;
    - Auto-fixed: the count of `formatFixed`;
    - Need review: the count of NEEDS_REVIEW + SKIPPED, with the `stat-attention` style class when it's above 0.

  The labels are private fields, so tests read them through `view().lookupAll(".stat-value")` in order: graded, average, auto-fixed, need review.
- `grading.progress.ProgressSnapshot(long done, long elapsedSeconds, String currentStudent, String currentStep, List<StudentProgress> students, List<String> activity, List<SimilarityPair> similarPairs)`. This is the order after a later cleanup removed `running`; check the record before writing the test.
- `grading.progress.StudentProgress(String username, GradingStatus status, boolean finished, boolean formatFixed, Map<String, Double> scores, List<String> issues, List<String> flags, List<Feedback> feedback, Map<String, QuestionGradingResult.Status> problems)`.
- `src/main/resources/assessment.properties` sets the marks to Q1a 3, Q1b 3, Q2a 5, Q2b 5 and Q3 4, so `AssessmentDefinition.totalMarks()` is 20.
- `src/main/java/ui/ImportPanel.java`: `collapse(String)` shows the summary HBox, hides the hint, sets the import button text to "Change folder", and hides the path field and the file area. The "Show files"/"Hide files" hyperlink toggles them.
- FXML ids: `testerSummary`, `testerSummaryText`, `testerFilesToggle`, `testerBrowser`, `testerImportButton`, and the same with the `submission` prefix.
- Pattern for JavaFX UI tests: `src/test/java/ui/MainViewGradingTest.java`. It starts the toolkit once in `@BeforeAll`, uses `fx(...)`/`await(...)` helpers, and has `importTesters(...)` / `importStudents(...)` helpers.
- Pattern for plain unit tests: `src/test/java/ui/StudentIssuesTest.java`.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| New tests | `./mvnw -q -B test -Dtest='StatTilesTest,MainViewGradingTest'` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `src/test/java/ui/StatTilesTest.java` (create), `src/test/java/ui/MainViewGradingTest.java` (add tests).
**Out of scope:** production code. If a test reveals a bug, STOP and report it.

## Steps

### Step 1: `StatTilesTest`

`StatTiles` creates JavaFX `Label`s. Constructing a `Label` needs the toolkit started, so copy the `@BeforeAll` toolkit start-up from `MainViewGradingTest`. Then add these tests:
- `emptyBeforeImport`: an empty snapshot gives all four values as "—".
- `countsGradedAverageFixesAndReviews`: three students:
  - a: GRADED, finished, total 12, `formatFixed` true;
  - b: NEEDS_REVIEW, finished, total 8;
  - c: SKIPPED, finished, no scores.

  Expect Graded "2/3", Class average "10.0/20", Auto-fixed "1", Need review "2", with the `stat-attention` class on the last value.

Run the assertions on the FX thread with the `fx(...)` helper pattern.

### Step 2: Panels and statuses in `MainViewGradingTest`

Add `importPanelsCollapseAfterImport`:
1. After `importTesters(testers)`:
   - `#testerSummary` is visible;
   - `#testerSummaryText` reads "5 of 5 tester files valid";
   - `#testerImportButton` reads "Change folder";
   - `#testerBrowser` is not visible.
2. Fire `#testerFilesToggle`. `#testerBrowser` becomes visible, and the toggle reads "Hide files".
3. After `importStudents(student)`: `#submissionSummaryText` reads "1 submission · 1 ready".

Add an assertion to the existing grading test: after grading, the Reason cell text for the student who has a compile error contains "Compile error Q1b". Read it from `controller.resultsTable().view().getItems()` through `StudentIssues.reason(...)`, not from rendered cells.

**Verify**: `./mvnw -q -B clean verify` → exit 0, with 3 new tests.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0; the new tests are present and passing
- [ ] `git diff --stat -- src/main` → empty (tests only)
- [ ] `plans/README.md` row updated

## STOP conditions

- An expected value differs (e.g. the average excludes or includes the wrong student). Report it; don't change production code to fit.

## Maintenance notes

- If a tile is added or reordered, update the `lookupAll(".stat-value")` order in `StatTilesTest`.
