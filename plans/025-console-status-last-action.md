# Plan 025: Stop repeating the stat tiles in the console status line

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: plan 020 must be DONE (it simplified the same
> line). Search `MainViewController.java` for `" need review or were skipped"`.

## Status

- **Priority**: P3
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/020-dedupe-status-helpers-settings.md; run it with or after 024 (same header area)
- **Category**: direction (usability: minimalist design)
- **Planned at**: commit `e413103` + uncommitted working tree, 2026-11-01

## Why this matters

After grading, the dark "Results & reports" header ends with a status label reading "3 graded, 3 need review or were skipped". Directly below it, the stat tiles show the same numbers: Graded 6/6, Need review 3. Showing the same numbers twice, in different words, is noise (Nielsen #8). At 960×660 the label also truncates to "3 graded, 3 need review or …". The label is more useful reporting the **last action** ("Grading complete", "Reports saved to …zip"), which is what it already does for every other action.

## Current state

- `ui/MainViewController.java` `finishRun(...)` sets:

```java
        consoleStatus.setText(snapshot.students().size() - toCheck + " graded, " + toCheck
                + " need review or were skipped");
```

  This is after plan 020; before 020 it used a `needsReview` variable.
- Every other path already sets `consoleStatus` to a short last-action text, e.g. `"Reports saved to " + saved.getFileName()`, `"Score sheet imported: N students"` and `"Cancelling: stopping the running programs"`.
- `main-view.fxml`: `consoleStatus` has `maxWidth="320"` and `textOverrun="ELLIPSIS"`.
- No test asserts this exact text: `grep -rn "need review or were skipped" src/test` → none.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `ui/MainViewController.java` (the one line in `finishRun`), one assertion in `src/test/java/ui/MainViewGradingTest.java`.
**Out of scope:** the stat tiles, the status pill, and every other `consoleStatus` text.

## Steps

### Step 1: Report the outcome, not the counts

Replace the line with an outcome text:
- **COMPLETED / NONE:** `"Graded " + (graded) + " of " + students.size()`, where graded is the students that are not SKIPPED (for example "Graded 5 of 6"). It's short, and it complements the tiles instead of repeating "need review".
- **CANCELLED:** "Run cancelled; earlier marks kept".
- **FAILED:** "Grading stopped; see the Activity log".

Use the same `switch (session.lastOutcome())` the status pill uses in `finishRun`. If plan 021 ran, use the equivalent in the controller.

**Verify**: `grep -n "need review or were skipped" src/main/java` → no matches.

### Step 2: Test

In `MainViewGradingTest.gradesFromTheButtonAndShowsGroupedScoresAndTerminalLogs`, after grading, assert `((Label) root.lookup("#consoleStatus")).getText()` equals `"Graded 1 of 1"`. Then check `cancelStopsGradingAndKeepsTheInputs` for the cancelled text.

**Verify**: `./mvnw -q -B clean verify` → exit 0.

## Done criteria

- [ ] Step 1 grep passes; 2 new assertions pass
- [ ] `plans/README.md` row updated

## STOP conditions

- Another feature relies on the old text, for example a test or the help text mentioning it. Report it.

## Maintenance notes

- `consoleStatus` reports the last action only; counts belong to the stat tiles.
