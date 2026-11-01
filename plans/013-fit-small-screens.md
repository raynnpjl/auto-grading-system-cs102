# Plan 013: Make the window usable on 13-inch laptops and 1280×720 projectors

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: plan 012 must be DONE. Then
> `git diff --stat e820cd4..HEAD -- src/main/resources/ui src/main/java/ui/AutoGradingApplication.java src/test/java/ui/MainViewGradingTest.java`.

## Status

- **Priority**: P1 (the demo depends on it)
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/012-split-main-view-controller.md
- **Category**: bug (usability)
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

The window can't be made smaller than **1060×840**. On a 13-inch MacBook at default scaling, the usable height under the menu bar and Dock is often about 800–860 px. On a 1280×720 projector, which is common in classrooms, the window doesn't fit at all, so the Run button or the results table gets cut off during the live demo. The rubric's application band asks that "all prompts, alerts, statuses, etc. are well placed". The presentation band penalises having to switch laptops or restart apps.

## Current state

- `src/main/java/ui/AutoGradingApplication.java:16-19`:

```java
    private static final double PREFERRED_WIDTH = 1320;
    private static final double PREFERRED_HEIGHT = 920;
    private static final double MIN_WIDTH = 1060;
    private static final double MIN_HEIGHT = 840;
```

  `start()` creates `new Scene(root, PREFERRED_WIDTH, PREFERRED_HEIGHT)` and calls `stage.setMinWidth/MinHeight`.
- `src/main/resources/ui/main-view.fxml`: the root `VBox` has `spacing="20"` and style class `workspace` (`-fx-padding: 28 32;` in `styles.css`). Its children are:
  - the title row;
  - the file-panels `HBox` (`minHeight="230" prefHeight="270"`, line 35), with `browser-body` StackPanes at `minHeight="90"`;
  - the run toolbar `HBox` (line 102);
  - the console panel `VBox` (`minHeight="260" prefHeight="420" VBox.vgrow="ALWAYS"`, line 126), holding a vertical `SplitPane` of the results table (`minHeight="90"`) and the `TabPane` (`minHeight="90"`).
- `src/test/java/ui/MainViewGradingTest.java:239-240` calls `snapshotAndCheckLayout(1320, 920, …)` and `(1060, 840, …)`. That helper (line 243) asserts:
  - the console panel is below the file panels;
  - `consoleBounds.getHeight() >= 210`;
  - the Run button's bottom is ≤ the scene height.

  It also saves a PNG under `target/`.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Layout tests | `./mvnw -q -B test -Dtest=MainViewGradingTest` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Look at it | open `target/grading-results-ui-720p.png` and `target/grading-results-ui-minimum.png` | nothing cut off, no "…" on buttons |

## Scope

**In scope:** `AutoGradingApplication.java`, `main-view.fxml` (sizes only), `styles.css` (padding/spacing only), `MainViewGradingTest.java` (layout sizes only).
**Out of scope:** removing any control or text; the controller.

## Steps

### Step 1: Lower the minimum and the fixed heights

- Set `MIN_WIDTH = 960` and `MIN_HEIGHT = 660`.
- Keep the preferred size, but cap it to the screen. In `start()`, use `Screen.getPrimary().getVisualBounds()` and create the scene at `min(PREFERRED, visual bounds)` for each dimension. Then the first window always fits.
- In the FXML: file panels `HBox` `minHeight` 230 → 170; console `VBox` `minHeight` 260 → 220.
- In the CSS: `.workspace` padding `28 32` → `20 24`, and root `spacing` 20 → 14.

### Step 2: Test the small sizes

In `MainViewGradingTest`, replace the `(1060, 840)` call with `(960, 660, "grading-results-ui-minimum.png")` and add `(1280, 720, "grading-results-ui-720p.png")`. The helper's assertions stay as they are.

**Verify**: `./mvnw -q -B test -Dtest=MainViewGradingTest` → exit 0. Then **look at both PNGs**: every button's full text is visible (no "…"), the table header row is visible, and the Activity/Similar answers tabs are visible.

### Step 3: Fallback, only if step 2's PNG or assertions fail

Wrap the root in a `ScrollPane` in `AutoGradingApplication.start()`:

```java
ScrollPane scroll = new ScrollPane(root);
scroll.setFitToWidth(true);
scroll.setFitToHeight(true);
```

Use it as the scene root, with `.scroll-pane` background transparent in CSS, and keep `MIN_HEIGHT = 660`. Then re-run step 2. The test helper looks things up via `root.lookup`, which still works.

**Verify**: `./mvnw -q -B clean verify` → exit 0.

## Done criteria

- [ ] `MIN_HEIGHT` ≤ 660 and `MIN_WIDTH` ≤ 960
- [ ] Both PNGs show the full UI with no truncated buttons
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `plans/README.md` row updated

## STOP conditions

- Even with the step 3 ScrollPane, the 960×660 snapshot clips the Run button. Report the PNG instead of removing controls.

## Maintenance notes

- Any new toolbar button must be checked at 960 px wide (the console heading row is the tightest).
