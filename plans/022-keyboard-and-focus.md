# Plan 022: Make the window usable from the keyboard: visible focus, reachable panes, shortcuts, distinct names

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: confirm the excerpts below still match
> `src/main/resources/ui/styles.css` and `src/main/resources/ui/main-view.fxml`.
> If plan 024 already ran, button style classes may differ. That is expected;
> add the focus rule to whatever button classes exist.

## Status

- **Priority**: P1
- **Effort**: S–M
- **Risk**: LOW
- **Depends on**: none (independent of 020/021)
- **Category**: bug (accessibility / usability)
- **Planned at**: commit `e413103` + uncommitted working tree, 2026-11-01

## Why this matters

The rubric judges the application against Nielsen's 10 heuristics and asks for "highly user-centric" polish. Currently:
- **Focus is invisible.** `styles.css` replaces `-fx-background-color` on `.button` and `.text-field`, which removes JavaFX's built-in focus ring, and there is no `:focused` rule.
- **Keyboard users can't reach key panes.** Five controls are set to `focusTraversable="false"`, including the Activity log and both file trees.
- **There are no keyboard shortcuts.**
- **Two buttons share a name.** Both import buttons read "Import folder" (later "Change folder"), so a screen reader can't tell them apart.

A design critique scored heuristic 7 (flexibility and efficiency) at 1 out of 4 for this reason.

## Current state

- `styles.css` (~:105-125):

```css
.text-field {
    -fx-padding: 10 12;
    -fx-font-size: 12px;
    -fx-background-color: #f7f9fc;
    ...
}
.button {
    -fx-padding: 10 16;
    -fx-font-weight: bold;
    -fx-background-color: #e8edf4;
    -fx-background-radius: 5;
    -fx-text-fill: #52647c;
}
```

  Button classes in use: `.import-button`, `.run-button`, `.score-sheet-button`, `.resolution-button`, `.dialog-primary-button`, `.dialog-secondary-button`; plus `.files-toggle`, `.details-link` (hyperlinks); and the `.student-tabs` tabs. The teal brand colour is `#2e686a`.
- `main-view.fxml`: `focusTraversable="false"` on `testerFolderField` (~:60), `testerFiles` (~:65), `submissionFolderField` (~:101), `submissionFiles` (~:106) and `findingsConsole` (~:179). `testerImportButton` (~:46) and `submissionImportButton` (~:87) have no `accessibleText`.
- `ui/AutoGradingApplication.java` `start(Stage)` builds the `Scene` and holds `controller`. The controller has `@FXML` handlers `runGrading()`, `cancelGrading()`, `writeScoresToSheet()` and `importScoreSheet()`, plus `showHelp()`.
- `ui/SimilarityList.java`: rows open a student only on double-click (`row.setOnMouseClicked`, ~:56).
- `MainViewWiringTest` checks FXML text with `fxml.contains(...)`. Extend it.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Look | `open target/grading-results-ui.png` (written by `MainViewGradingTest`) | layout unchanged |

## Scope

**In scope:** `styles.css`, `main-view.fxml`, `ui/AutoGradingApplication.java` (accelerators), `ui/MainViewController.java` (a package-private `installShortcuts(Scene)` method, if you prefer it there), `ui/SimilarityList.java` (the Enter key), `src/test/java/ui/MainViewWiringTest.java`.
**Out of scope:** colours, other than the focus outline; layout.

## Steps

### Step 1: A visible focus outline

Add one rule after the button rules:

```css
.button:focused,
.text-field:focused,
.hyperlink:focused,
.check-box:focused > .box,
.tree-view:focused,
.table-view:focused,
.text-area:focused,
.student-tabs .tab:focused {
    -fx-border-color: #2e686a;
    -fx-border-width: 2;
    -fx-border-radius: 6;
}
```

If a control already sets `-fx-border-*` (e.g. `.text-field`, `.dialog-secondary-button`), the focused rule's border wins, which is intended. Check `.run-button:focused` on the dark-teal fill: the border should use white (`-fx-border-color: white`) there. Add `.run-button:focused, .import-button:focused, .dialog-primary-button:focused { -fx-border-color: white; }`.

### Step 2: Reachable panes and distinct names

- Remove `focusTraversable="false"` from the two TreeViews and the console TextArea. Keep it on the two read-only path TextFields: they repeat the summary and would add two empty tab stops.
- Add `accessibleText="Import or change the tester files folder"` to `testerImportButton` and `accessibleText="Import or change the student submissions folder"` to `submissionImportButton`.

### Step 3: Shortcuts

In `AutoGradingApplication.start`, after the scene is created, add accelerators with `KeyCombination` (`SHORTCUT` is ⌘ on macOS and Ctrl elsewhere):

| Keys | Action | Guard |
|---|---|---|
| Shortcut+R | `runGrading` | only if `#runGradingButton` is enabled: call `button.fire()` |
| Shortcut+Shift+S | Save filled sheet | fire `#writeScoresButton` if enabled |
| Shortcut+I | Import score sheet | fire `#scoreSheetImportButton` if enabled |
| Escape | Cancel grading | fire `#cancelButton` if visible |
| F1 | How to grade | fire `#helpButton` |

Firing the buttons (looked up with `root.lookup("#…")`) reuses every existing guard. Put the shortcuts in the help text too: add a "Keyboard shortcuts" section to `HelpDialog.text()`.

### Step 4: Enter opens a similar answer

In `SimilarityList`, also handle `KeyEvent.KEY_PRESSED` with `KeyCode.ENTER` on the table: select `first()` of the selected pair, as the double-click does.

### Step 5: Tests

In `MainViewWiringTest`, assert that:
- the FXML no longer contains `focusTraversable="false"` on `findingsConsole`, `testerFiles` or `submissionFiles`;
- both import buttons have `accessibleText`;
- `styles.css` contains `.button:focused`.

In `HelpDialogTest`, assert that the text contains "F1" and "Escape".

**Verify**: `./mvnw -q -B clean verify` → exit 0.

## Done criteria

- [ ] `grep -c 'focusTraversable="false"' src/main/resources/ui/main-view.fxml` → `2` (only the path fields)
- [ ] `grep -n ':focused' src/main/resources/ui/styles.css` → at least 1
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `plans/README.md` row updated

## STOP conditions

- The focus border visibly shifts layout (buttons jump 2px when focused). Then use `-fx-border-insets: -2` or switch to an `-fx-effect: dropshadow(...)` ring, and report which one was chosen.
- An accelerator fires while a dialog is open. Report it; don't add global key filters.

## Maintenance notes

- Any new button style class needs a focused state. Copy the step 1 selectors.
