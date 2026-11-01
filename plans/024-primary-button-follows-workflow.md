# Plan 024: Give exactly one primary (teal) button per stage, moving with the workflow

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: confirm the excerpts below match the live
> `main-view.fxml`, `styles.css` and `MainViewController.updateButtons()`.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plan 021 if it is planned to run (it edits the same controller). Otherwise none.
- **Category**: direction (usability: visual hierarchy)
- **Planned at**: commit `e413103` + uncommitted working tree, 2026-11-01

## Why this matters

The marker's path is: import testers → import submissions → run grading → import score sheet → save filled sheet. The window doesn't show that order:
- After both imports, the two "Change folder" buttons stay strong teal (`import-button`).
- After grading, "Run grading" (`run-button`) is still the strongest button on screen.
- The real next steps, "Import score sheet" and "Save filled sheet", stay pale (`score-sheet-button`).

A design critique flagged "the primary action never moves through the workflow", which is part of the rubric's "intuitive… prompts well placed".

## Current state

- `main-view.fxml`:
  - `testerImportButton` and `submissionImportButton` use `styleClass="import-button"`.
  - `runGradingButton` uses `styleClass="run-button"`.
  - `scoreSheetImportButton`, `writeScoresButton`, `saveReportsButton`, `cancelButton`, `regradeButton` and `helpButton` use `score-sheet-button`.
- `styles.css`: `.run-button, .import-button { -fx-padding: 12 20; -fx-background-color: #2e686a; -fx-text-fill: white; -fx-cursor: hand; }`, with hover `#397c7e` and pressed `#245759` (import only). `.score-sheet-button { -fx-background-color: #e3eeee; -fx-text-fill: #1f4f51; ... }`.
- `MainViewController.updateButtons()` sets `setDisable(...)` on every button and knows `testerImported`, `submissionsImported`, `session.hasResults()`, `scoreSheet.hasSheet()` and `inputsReady()`.
- `MainViewWiringTest` asserts `assertEquals(2, countOccurrences(fxml, "styleClass=\"import-button\""))` and that `styles.css` contains `.import-button:hover` / `:pressed`. Keep those classes; this plan adds a `primary` class on top.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Look | `target/grading-results-ui.png`, `target/grading-results-ui-720p.png` (written by `MainViewGradingTest`) | one teal button per screen |

## Scope

**In scope:** `styles.css`, `ui/MainViewController.java` (`updateButtons()` plus a tiny helper), `src/test/java/ui/MainViewGradingTest.java`.
**Out of scope:** FXML structure; button text; colours other than moving teal between buttons.

## Steps

### Step 1: A `primary` class

In `styles.css`, make the teal look a separate class, so any button can wear it:

```css
.button.primary {
    -fx-padding: 12 20;
    -fx-background-color: #2e686a;
    -fx-text-fill: white;
    -fx-cursor: hand;
}
.button.primary:hover { -fx-background-color: #397c7e; }
.button.primary:pressed { -fx-background-color: #245759; }
```

Change the `.import-button` and `.run-button` rules to the secondary look (the same colours as `.score-sheet-button`), but keep the selectors themselves (tests check them). Keep their padding so the layout doesn't shift.

### Step 2: Move `primary` with the stage

In `updateButtons()`, decide one primary button with a pure function, so it can be tested:

| Condition (first match wins) | Primary |
|---|---|
| no valid testers imported | `testerImportButton` |
| no submissions imported, or none ready | `submissionImportButton` |
| no results yet | `runGradingButton` |
| results, no score sheet | `scoreSheetImportButton` |
| results and score sheet | `writeScoresButton` |

Remove `primary` from every button, then add it to the chosen one. A disabled primary button keeps the `.button:disabled` opacity, which is fine.

### Step 3: Test

In `MainViewGradingTest`, add `primaryButtonMovesThroughTheWorkflow`:
- After setup (nothing imported): only `#testerImportButton` has `primary`.
- After importing testers: `#submissionImportButton`.
- After importing submissions: `#runGradingButton`.
- After grading: `#scoreSheetImportButton`.

Count the `primary` classes across all buttons (`root.lookupAll(".button")`) and assert exactly 1 at each stage.

**Verify**: `./mvnw -q -B clean verify` → exit 0. Open the PNGs: exactly one teal button is visible.

## Done criteria

- [ ] New test passes; `MainViewWiringTest` unchanged and passing
- [ ] `plans/README.md` row updated

## STOP conditions

- The run-toolbar or the console heading wraps or truncates at 960×660 after the padding change. Report it with `target/grading-results-ui-minimum.png`.

## Maintenance notes

- If a stage is added (e.g. "Resolve issues" as a blocking step), add a row to the step 2 table, before "no results yet".
