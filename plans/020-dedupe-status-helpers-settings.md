# Plan 020: Decide "needs attention" in one place, share one show/hide helper, and copy settings in one line

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit; the user commits their own work.
>
> **Drift check (run first)**: this plan was written against commit `e413103`
> **plus uncommitted work** in the user's tree, so `git diff` against the SHA
> is not useful. Instead, confirm each excerpt below still matches the live
> file (search for the quoted lines). On a mismatch, STOP.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `e413103` + uncommitted working tree, 2026-11-01

## Why this matters

The rubric's top code band asks for "methods perform one function only" and no repetition. Three kinds of duplication remain in the JavaFX layer:

1. "Does this student need a person to check them?" is decided in four places, two of them with different rules.
2. Two identical two-line show/hide helpers.
3. A 15-argument copy of the settings record, used only to change the reports folder.

If a fifth status is added later, the four checks will disagree and the stat tiles, table and status pill will show different counts.

## Current state

- `src/main/java/grading/progress/GradingStatus.java`: an enum `QUEUED, RUNNING, GRADED, NEEDS_REVIEW, SKIPPED` with `label()`, `styleClass()` and:

```java
    /** Whether the student was graded without anything a person needs to check. */
    public boolean isClean() {
        return this == GRADED;
    }
```

- `src/main/java/ui/ResultsTable.java` (~:177):

```java
    private static boolean needsChecking(StudentProgress student) {
        return student.status() == GradingStatus.NEEDS_REVIEW || student.status() == GradingStatus.SKIPPED;
    }
```

  It is used by `toCheckCount()` (~:133) and `firstToCheck()` (~:138).
- `src/main/java/ui/StatTiles.java` `update` (~:42):

```java
        final long checking = students.stream().filter(student -> student.status() == GradingStatus.NEEDS_REVIEW
                || student.status() == GradingStatus.SKIPPED).count();
```

- `src/main/java/ui/MainViewController.java`:
  - `finishRun` (~:427) computes `long needsReview = snapshot.students().stream().filter(student -> !student.status().isClean()).count();`, beside `long toCheck = resultsTable.toCheckCount();`. `needsReview` is only used for the `consoleStatus` text at ~:439 (plan 025 replaces that text; leave it alone here except as stated in step 2).
  - `writeScoresToSheet` (~:591) and `usernamesWith` (~:599) filter by one status each. That is fine and stays.
  - `setVisible(Node, boolean)` (~:805): `node.setVisible(visible); node.setManaged(visible);`.
  - `useReportsFolder(Path)` (~:495) rebuilds `GradingSettings` with all 15 components to change `reportsFolder`.
- `src/main/java/ui/ImportPanel.java` (~:74): `private static void setShown(Node node, boolean shown) { node.setVisible(shown); node.setManaged(shown); }`
- `src/main/java/grading/config/GradingSettings.java`: `public record GradingSettings(Duration compileTimeout, Duration executionTimeout, int parallelStudents, int maxMemoryMegabytes, int maxOutputBytes, long maxJavaFileBytes, long maxZipBytes, int maxZipEntries, String emailDomain, long maxTesterFileBytes, int javaRelease, StyleSettings style, PenaltySettings penalties, SimilaritySettings similarity, Path reportsFolder)`.
- Conventions: a one-line Javadoc on every package-visible method, 4-space indent, 120 columns, `final` utility classes with a private constructor (see `ui/FindingsText.java`).

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Style self-check | `./style-check.sh` | last line `0 style problems (line length excluded)` |

## Scope

**In scope:** `GradingStatus.java`, `GradingSettings.java`, `ui/ResultsTable.java`, `ui/StatTiles.java`, `ui/MainViewController.java`, `ui/ImportPanel.java`, `ui/Nodes.java` (create), `src/test/java/grading/progress/ProgressTrackerTest.java` (add one test).
**Out of scope:** any user-visible text or behaviour; `consoleStatus` wording (plan 025).

## Steps

### Step 1: `GradingStatus.needsAttention()`

Add to the enum:

```java
    /** Whether a person should look at the student: their grading needs review, or they were skipped. */
    public boolean needsAttention() {
        return this == NEEDS_REVIEW || this == SKIPPED;
    }
```

Then replace:
- `ResultsTable.needsChecking` with `student.status().needsAttention()`. Delete the private method and use a lambda or method reference in its two callers.
- the `checking` filter in `StatTiles.update` with `.filter(student -> student.status().needsAttention())`.

**Verify**: `grep -rn "NEEDS_REVIEW ||\|== GradingStatus.NEEDS_REVIEW\s*||" src/main/java` → no matches.

### Step 2: One count in `finishRun`

`needsReview` (`!isClean()`) and `toCheck` (`needsAttention()`) are equal once a run has finished, because no student is QUEUED or RUNNING then. Replace `needsReview` with `toCheck` in the `consoleStatus` line, and delete the `needsReview` variable. Keep the text format exactly: `(students - toCheck) + " graded, " + toCheck + " need review or were skipped"`.

**Verify**: `grep -n "isClean()" src/main/java/ui/MainViewController.java` → no matches. If `isClean()` now has no callers at all (`grep -rn "isClean" src`), delete it from `GradingStatus`, and from tests if any.

### Step 3: One show/hide helper

Create `ui/Nodes.java`, a final class with a private constructor and:

```java
    /** Shows or hides a node, and frees its space in the layout while hidden. */
    static void show(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
```

Replace `MainViewController.setVisible(...)` and `ImportPanel.setShown(...)` with `Nodes.show(...)` and delete both private helpers.

**Verify**: `grep -rn "setManaged(" src/main/java/ui` → exactly 1 match, in `Nodes.java`.

### Step 4: `GradingSettings.withReportsFolder`

Add to the record:

```java
    /** These settings with the attempt reports written into another folder. */
    public GradingSettings withReportsFolder(Path folder) {
        return new GradingSettings(compileTimeout, executionTimeout, parallelStudents, maxMemoryMegabytes,
                maxOutputBytes, maxJavaFileBytes, maxZipBytes, maxZipEntries, emailDomain, maxTesterFileBytes,
                javaRelease, style, penalties, similarity, folder);
    }
```

`MainViewController.useReportsFolder` becomes `settings = settings.withReportsFolder(folder); session = new GradingSession(settings, progress, workFolder);`.

**Verify**: `grep -c "current\." src/main/java/ui/MainViewController.java` → `0`.

### Step 5: Test and verify

Add `ProgressTrackerTest.onlyNeedsReviewAndSkippedNeedAttention`. It asserts `needsAttention()` is true for exactly `NEEDS_REVIEW` and `SKIPPED`, by looping over `GradingStatus.values()`.

**Verify**: `./mvnw -q -B clean verify` → exit 0; `./style-check.sh` → 0 problems.

## Done criteria

- [ ] All four step greps pass
- [ ] `./mvnw -q -B clean verify` exits 0, with 1 new test
- [ ] `./style-check.sh` reports 0 problems
- [ ] `plans/README.md` row updated

## STOP conditions

- Any UI test that checks status text fails. The wording must not change in this plan.
- `GradingStatus` has gained a value not listed above.

## Maintenance notes

- New statuses must answer `needsAttention()` deliberately. Add a case to the step 5 test.
