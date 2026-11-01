# Plan 021: Move the status pill and the import flows out of `MainViewController`

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: plan 020 must be DONE. Then read the whole of
> `src/main/java/ui/MainViewController.java`; locate code by method name, not line.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED (refactor of the UI controller; the UI tests are the safety net)
- **Depends on**: plans/020-dedupe-status-helpers-settings.md
- **Category**: tech-debt
- **Planned at**: commit `e413103` + uncommitted working tree, 2026-11-01

## Why this matters

An earlier refactor (plan 012) brought `MainViewController` down to 725 lines. The stat tiles, status pill, import panels and tabs added since then have pushed it back to about 840 lines. Every method is short, but the class still owns about 40 fields and both import flows. The rubric's top band asks that a "reader can easily figure out the intent of each class". This plan moves two cohesive slices into their own classes. Behaviour and all user-visible text must stay exactly the same.

## Current state

- `src/main/java/ui/MainViewController.java`, the relevant members:
  - **Status pill:** constants `BADGE_WORKING = "working"`, `BADGE_ATTENTION = "attention"`, `BADGE_DONE = "done"`; `@FXML HBox statusBadge`, `@FXML Label statusText`; `private void setBadgeTone(String tone)`, which removes all three classes from `statusBadge` and adds `tone` when non-null. It is called from `startRun` (`BADGE_WORKING`) and from `finishRun` (a `switch` on `session.lastOutcome()`). `statusText.setText(...)` is called from about 15 places.
  - **Import flows:** `@FXML private void importTesterFiles()` and `void importTesterFiles(Path)`; `@FXML private void importSubmissions()` and `void importSubmissions(Path)`; `showTesterResult(TesterValidationResult)`, `showSubmissions(List<SubmissionValidationResult>)`, `showImportProgress(int, int, String)`, `showImportFailure(String)`, `keptTestersNote()`; the `ImportPanel testerPanel, submissionPanel` fields.
- Tests in `src/test/java/ui/` call these package-private hooks. They must keep their names and signatures: `importTesterFiles(Path)`, `importSubmissions(Path)`, `importScoreSheet(Path)`, `saveFilledSheet(...)`, `saveReports(Path)`, `useReportsFolder(Path)`, `workFolder()`, `similarityList()`, `resultsTable()`, `detailText()`, `disableAutomaticIdentityPrompt()`, `shutdown()`, `isGrading()`, `scoreSheetConfirmation(...)`. The `@FXML` handler names are referenced by `main-view.fxml` and checked by `MainViewWiringTest`.
- Exemplar of the target style: `ui/ScoreSheetActions.java`. It owns its own state, returns a small `Result` record, and leaves dialogs and `@FXML` fields to the controller. Also `ui/RunMonitor.java`.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| UI tests | `./mvnw -q -B test -Dtest='MainView*Test,ScoreSheetWorkflowTest,ReadinessTest,ConsoleTextTest,HelpDialogTest,StudentIssuesTest'` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Style | `./style-check.sh` | 0 problems |
| Size | `wc -l src/main/java/ui/MainViewController.java` | ≤ 700 |

## Scope

**In scope:** `ui/MainViewController.java`, `ui/StatusPill.java` (create), `ui/ImportResults.java` (create).
**Out of scope:** `main-view.fxml`, `styles.css`, anything in `grading/**`, test assertions, and any visible wording.

## Steps

Run the **UI tests** after every step.

### Step 1: `StatusPill`

Create `ui/StatusPill.java`. It wraps the `HBox` badge and its `Label`, and has:
- `void working(String text)`
- `void attention(String text)`
- `void done(String text)`
- `void neutral(String text)`

Each sets the text and the tone class: the badge constants move here, and `neutral` sets no tone. In `initialize()`, the controller creates `statusPill = new StatusPill(statusBadge, statusText)`. Replace `setBadgeTone` and every `statusText.setText(...)` with the matching `statusPill` call. Plain progress messages ("Validating submissions" and similar) use `neutral`. Delete `setBadgeTone` and the `BADGE_*` constants from the controller.

**Verify**: `grep -n "statusText.setText\|setBadgeTone\|BADGE_" src/main/java/ui/MainViewController.java` → no matches. The UI tests pass; they assert `#statusText` text and the `attention` class on `#statusBadge`.

### Step 2: `ImportResults`

Create `ui/ImportResults.java`. It holds the pure formatting the import flows do today:
- the tester summary line (`"5 of 5 tester files valid"`);
- the submission summary line (`"6 submissions · 6 ready · 1 need attention"`);
- the failure reports. Copy the "Tester-file validation failed" and "Submission validation failed" text blocks verbatim, as static methods taking the `Throwable` and a `boolean` for the kept-testers or kept-submissions note.

The controller calls these instead of building the strings inline. The JavaFX wiring (choosers, `runInBackground`, tree roots) stays in the controller.

**Verify**: `grep -n "validation failed" src/main/java/ui/MainViewController.java` → no matches. The UI tests pass.

### Step 3: Size and checks

**Verify**: `wc -l src/main/java/ui/MainViewController.java` → ≤ 700; `./mvnw -q -B clean verify` → exit 0; `./style-check.sh` → 0 problems.

## Test plan

Add `src/test/java/ui/ImportResultsTest.java` (pattern: `ReadinessTest.java`, plain JUnit, no JavaFX). It checks the two summary lines, singular and plural, and that each failure text contains "Next step for marker".

## Done criteria

- [ ] Step greps pass; the controller is ≤ 700 lines
- [ ] `./mvnw -q -B clean verify` exits 0, with the new test
- [ ] `git diff --stat -- src/main/resources src/main/java/grading` → empty
- [ ] `plans/README.md` row updated

## STOP conditions

- A UI test fails and fixing it would change visible text or a test assertion.
- The controller can't reach ≤ 700 lines with these two moves. Report the actual count; don't move `@FXML` handlers out.

## Maintenance notes

- New status messages go through `StatusPill`, so tone and text stay in one place.
