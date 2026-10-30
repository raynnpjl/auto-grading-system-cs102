# Plan 008: Report how a grading run ended as a typed outcome, not by reading log text

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit; the user commits their own work.
>
> **Drift check (run first)**:
> `git diff --stat e820cd4..HEAD -- src/main/java/grading/session src/main/java/ui/MainViewController.java`
> If either changed, compare the excerpts below with the live code; on a mismatch, STOP.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

When a run ends, the JavaFX controller decides whether it was cancelled or failed by checking whether the **last log line** starts with `"Grading cancelled"` or `"Grading failed"`. Those strings are written in a different class (`GradingSession`). Rewording a log message, or any log line arriving after it, would show the wrong status to the marker. The rubric's top code band asks for a clean design where the relationships between classes are obvious; string-matching log output is the opposite.

## Current state

- `src/main/java/grading/session/GradingSession.java`: owns grading. `runGrading(...)` (around lines 256–300) ends with:

```java
        } catch (Exception | LinkageError exception) {
            if (cancelRequested) {
                // Cancelling interrupts the run wherever it is, so any failure after it is the cancel itself.
                restore(previous, batch);
                log.accept("Grading cancelled. " + (previous.attempt() == null
                        ? "No marks were kept." : "The results from before are shown again."));
            } else {
                // LinkageError: a library the grader needs is missing, which must not fail silently.
                log.accept("Grading failed: " + Messages.describe(exception));
                LOG.log(Level.SEVERE, "Grading failed", exception);
            }
        } finally {
            endCancellablePart();
            progress.finish();
            grading.set(false);
        }
```

  The success path ends with `latestAttempt = attempt;` just before the `catch`.
- `src/main/java/ui/MainViewController.java:361-375` (`finishRun`):

```java
        String lastLine = snapshot.activity().isEmpty() ? "" : snapshot.activity().getLast();
        ...
        if (lastLine.startsWith("Grading cancelled")) {
            statusText.setText("Grading cancelled");
        } else if (lastLine.startsWith("Grading failed")) {
            statusText.setText("Grading failed");
        } else {
            statusText.setText(needsReview == 0 ? "Grading complete" : "Grading complete — review errors");
        }
```

- Convention: small public types each get their own file with a one-line Javadoc. `grading/session/SessionRefusal.java` has a nested `public enum Reason { ... }`; use it as the style exemplar.
- There is no existing `GradingSession` unit test. The UI tests in `src/test/java/ui/MainViewGradingTest.java` (`cancelStopsGradingAndKeepsTheInputs`, `gradesFromTheButtonAndShowsGroupedScoresAndTerminalLogs`) already check the "Grading cancelled" and "Grading complete — review errors" status texts end to end.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Compile | `./mvnw -q -B -DskipTests compile` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Test totals | `grep -h "Tests run" target/surefire-reports/*.txt \| awk -F'[:,]' '{r+=$2;f+=$4;e+=$6} END{print r,f,e}'` | `<N> 0 0` |

## Scope

**In scope:** `src/main/java/grading/session/RunOutcome.java` (create), `src/main/java/grading/session/GradingSession.java`, `src/main/java/ui/MainViewController.java` (only `finishRun`), `src/test/java/grading/session/GradingSessionTest.java` (create).

**Out of scope:** the log message wording (leave it); `ProgressTracker`; any other UI change.

## Steps

### Step 1: Add the enum

Create `grading/session/RunOutcome.java`:

```java
package grading.session;

/** How the latest grading run ended. */
public enum RunOutcome {
    /** No run has finished since the application started. */
    NONE,
    /** Every requested student was graded and the reports were written. */
    COMPLETED,
    /** The marker cancelled the run; the results from before it are shown again. */
    CANCELLED,
    /** Grading stopped because of an unexpected problem; see the activity log. */
    FAILED
}
```

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 2: Record the outcome in `GradingSession`

- Add a field `private volatile RunOutcome lastOutcome = RunOutcome.NONE;`.
- In `runGrading`, set it to `COMPLETED` right after `latestAttempt = attempt;`, to `CANCELLED` inside `if (cancelRequested)`, and to `FAILED` in the `else` branch.
- Add a public getter with Javadoc: `/** How the latest run ended; read it once {@link #grading()} is false. */ public RunOutcome lastOutcome()`.

Assign the outcome **before** the `finally` block sets `grading` to false. This makes sure a reader who sees `grading() == false` also sees the final outcome.

**Verify**: compile → exit 0.

### Step 3: Use it in the controller

Replace the `lastLine` logic in `MainViewController.finishRun` with a `switch (session.lastOutcome())`. Map `CANCELLED` → "Grading cancelled" and `FAILED` → "Grading failed". `COMPLETED` and `NONE` keep the existing complete/review-errors text. Delete the `lastLine` variable.

**Verify**: `grep -n 'startsWith("Grading' src/main/java/ui/MainViewController.java` → no matches.

### Step 4: Unit test

Create `src/test/java/grading/session/GradingSessionTest.java` with one test, `outcomeIsNoneBeforeAnyRun`. It builds a session with `new GradingSession(GradingSettings.current(), new ProgressTracker(), tempDir)` (JUnit `@TempDir`) and asserts `lastOutcome() == RunOutcome.NONE`. The CANCELLED, FAILED and COMPLETED paths are covered by the existing UI tests in step 5.

**Verify**: `./mvnw -q -B clean verify` → exit 0, and the test totals line ends in `0 0`.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `grep -rn 'startsWith("Grading' src/main/java` → no matches
- [ ] `MainViewGradingTest.cancelStopsGradingAndKeepsTheInputs` passes (it checks the "Grading cancelled" status)
- [ ] `plans/README.md` row updated

## STOP conditions

- `runGrading`'s `catch` block doesn't look like the excerpt.
- Any UI test that checks the status text fails after step 3.

## Maintenance notes

- If a new way of ending a run is ever added (e.g. a time limit for the whole batch), add a `RunOutcome` value instead of a new log message.
