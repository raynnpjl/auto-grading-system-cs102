# Plan 014: Add an in-app "How to grade" help dialog

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: plans 012 and 013 must be DONE. Read
> `src/main/resources/ui/main-view.fxml` lines 1–35 before editing.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/013-fit-small-screens.md (same FXML title row)
- **Category**: direction (usability)
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

The brief judges usability against Nielsen's 10 heuristics. Number 10, "Help and documentation", is currently met only by the README, which a marker won't have open. A one-click help dialog explaining the five-step workflow, and what each status and button means, closes that gap. It's also a natural demo moment.

## Current state

- The title row in `main-view.fxml` (around lines 22–33) is an `HBox` with the "AGS / Auto Grading System" labels, a `Region HBox.hgrow="ALWAYS"`, and the status badge `HBox` (`styleClass="status-badge"`, containing `statusText`).
- Dialog style exemplar: `src/main/java/ui/ScoreSheetConfirmation.java`. It builds an `Alert`, puts a non-editable `TextArea` in the `DialogPane`, and adds `/ui/styles.css` to the pane's stylesheets. Copy that pattern.
- Workflow steps and their UI names: Tester files **Import folder** → Student submissions **Import folder** (then **Resolve issues** if shown) → **Run grading** (**Cancel**, **Regrade selected**) → **Import score sheet** → **Save filled sheet**, plus **Save reports**.
- Statuses shown in the table: queued, running, graded, needs review, skipped. After plan 009 they come from `grading.progress.GradingStatus.label()`.
- `MainViewWiringTest` asserts FXML wiring via `fxml.contains(...)` and checks `@FXML` annotations by reflection. Extend that test.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `src/main/java/ui/HelpDialog.java` (create), `main-view.fxml` (one button), `MainViewController.java` (one `@FXML` handler), `styles.css` (optional, one rule), `src/test/java/ui/MainViewWiringTest.java`.
**Out of scope:** README; any other UI.

## Steps

### Step 1: `HelpDialog`

Make it a final class with a private constructor and:
- `static String text()`: the help text as plain text. It's package-private so it can be tested.
- `static void show(Window owner)`.

The text has three sections:
1. **Grading in five steps**: one line per step, using the exact button names above.
2. **What the statuses mean**: one line each for queued, running, graded, needs review ("a question could not be graded normally, or the answer is very similar to another student's — check the details panel") and skipped ("the student's identity is not confirmed — use Resolve issues").
3. **Good to know**: the original submissions and score sheet are never changed; settings live in `config/grading.properties` and `config/assessment.properties`; reports are saved under the `grading-reports` folder.

Generate the status lines from `GradingStatus.values()` and `label()`, so they can't drift from the table.

### Step 2: The button

In the title row, between the `Region` and the status badge, add:

```xml
<Button fx:id="helpButton" text="How to grade" onAction="#showHelp" minWidth="-Infinity"
        styleClass="score-sheet-button" accessibleText="Show how to grade a batch of submissions"/>
```

Add the controller handler `@FXML private void showHelp() { HelpDialog.show(helpButton.getScene().getWindow()); }` and an `@FXML private Button helpButton;` field.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 3: Tests

In `MainViewWiringTest`, assert that the FXML contains `onAction="#showHelp"` and that `showHelp` is `@FXML`-annotated. Add `HelpDialogTest.textNamesEveryButtonAndStatus`, which asserts that `HelpDialog.text()` contains "Run grading", "Resolve issues", "Save filled sheet" and every `GradingStatus.label()`.

**Verify**: `./mvnw -q -B clean verify` → exit 0. Also re-run plan 013's 960×660 snapshot (part of `MainViewGradingTest`) and check the title row in `target/grading-results-ui-minimum.png`.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0, with the new tests
- [ ] The 960×660 PNG shows the "How to grade" button without truncation
- [ ] `plans/README.md` row updated

## STOP conditions

- `GradingStatus` doesn't exist (plan 009 not done). Report it rather than hard-coding status words.

## Maintenance notes

- When a button is renamed, update `HelpDialog.text()`. The test above catches the main ones.
