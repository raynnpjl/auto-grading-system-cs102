# Plan 009: Replace the grading status strings with a `GradingStatus` enum and format marks in one place

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**:
> `git diff --stat e820cd4..HEAD -- src/main/java/grading/progress src/main/java/ui src/main/java/grading/reporting/PlainLanguage.java`
> Plan 008 may have changed `MainViewController.finishRun`; that is expected. For any other change, compare with the excerpts; on a mismatch, STOP.

## Status

- **Priority**: P1
- **Effort**: S–M
- **Risk**: LOW
- **Depends on**: plans/008-run-outcome.md (both edit `finishRun`; do 008 first)
- **Category**: tech-debt
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

A student's grading status is a plain `String` that five classes compare by hand. The values are `"queued"`, `"running"`, `"graded"`, `"needs-review"` and `"skipped"`. The UI also checks for `"auto-fixed"`, which **nothing ever sets**: dead code that only exists because the values aren't typed. A typo in any of these would quietly break the table colours, the Regrade button or the summary. The rubric's top band asks for "constants instead of littering values all over". Separately, marks are formatted with `String.format(Locale.ROOT, "%.1f", …)` in three places.

## Current state

Every status literal, verified at `e820cd4`:

- `src/main/java/grading/progress/ProgressTracker.java`:
  - :46 `row.status = "skipped";`
  - :64 `rows(username).status = "running";`
  - :104 `row.status = hasErrors ? "needs-review" : "graded";`
  - :157 `if (row == null || row.status.equals("skipped")) {`
  - :162 `row.status = needsReview ? "needs-review" : "graded";`
  - :172 `row.status = "queued";`
  - :227 `String status = "queued";` (field of the private `Row` class)
  - :241 `return new StudentProgress(username, flags.isEmpty() ? status : "needs-review", ...)`
- `src/main/java/grading/progress/StudentProgress.java:22`: `public record StudentProgress(String username, String status, boolean finished, boolean formatFixed, Map<String, Double> scores, List<String> issues, List<String> flags, List<Feedback> feedback)`. Its Javadoc says `@param status queued, running, graded, needs-review or skipped`.
- `src/main/java/ui/MainViewController.java`:
  - :368 `.filter(student -> !student.status().equals("graded") && !student.status().equals("auto-fixed"))`
  - :798 `.map(student -> student.status().equals("skipped")).orElse(true));`
- `src/main/java/ui/GradingSummaryText.java`: :23 `if (student.status().equals("skipped")) {`, and :31 appends `student.status()`.
- `src/main/java/ui/ResultsTable.java`: the Status column shows `cell.getValue().status()` (:68). `StatusCell` adds the CSS class `"status-" + status`, and `STATUS_STYLES` lists `status-queued … status-auto-fixed`. `styles.css` defines `.status-running`, `.status-graded`, `.status-auto-fixed`, `.status-needs-review`, `.status-skipped` and `.status-queued`.
- `src/main/java/ui/StudentDetailPane.java:81`: the heading shows `next.status()`.
- `src/test/java/ui/MainViewGradingTest.java:292`: `assertEquals("queued", … .getFirst().status())`.
- Mark formatting duplicates: `ui/ResultsTable.java:132`, `ui/GradingSummaryText.java:50`, `grading/reporting/PlainLanguage.java:71` (`static String marks(double value)`, package-private in `grading.reporting`).
- Enum style exemplar: `grading/execution/QuestionGradingResult.java` (`public enum Status { GRADED, COMPILATION_ERROR, ... }`), and `grading/reporting/PlainLanguage.status(...)`, which maps it to text with a `switch`.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Compile | `./mvnw -q -B -DskipTests compile` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `grading/progress/GradingStatus.java` (create), `grading/progress/ProgressTracker.java`, `grading/progress/StudentProgress.java`, `grading/scoring/Marks.java` (create), `grading/reporting/PlainLanguage.java` (only `marks`), `ui/MainViewController.java`, `ui/GradingSummaryText.java`, `ui/ResultsTable.java`, `ui/StudentDetailPane.java`, `src/main/resources/ui/styles.css`, `src/test/java/ui/MainViewGradingTest.java`.

**Out of scope:** `QuestionGradingResult.Status` (a different, per-question status); report text in the PDFs.

## Steps

### Step 1: Create the enum

`grading/progress/GradingStatus.java`:

```java
/** Where a student is in a grading run, as shown in the results table. */
public enum GradingStatus {
    QUEUED("queued"), RUNNING("running"), GRADED("graded"), NEEDS_REVIEW("needs review"), SKIPPED("skipped");

    private final String label;
    GradingStatus(String label) { this.label = label; }

    /** The word shown to the marker. */
    public String label() { return label; }

    /** The CSS class that colours this status, such as status-needs-review. */
    public String styleClass() { return "status-" + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'); }

    /** Whether the student was graded without anything a person needs to check. */
    public boolean isClean() { return this == GRADED; }
}
```

(Format it over several lines and use imports, matching the repo's style.)

### Step 2: Use it in the progress package

Change `Row.status` and the `StudentProgress.status` component to `GradingStatus`, and replace every literal listed under `ProgressTracker`. Update the `StudentProgress` Javadoc.

**Verify**: `grep -rnE '"(queued|running|graded|needs-review|skipped|auto-fixed)"' src/main/java/grading` → no matches.

### Step 3: Use it in the UI

- `MainViewController`: change `:368` to `!student.status().isClean()` and `:798` to `student.status() == GradingStatus.SKIPPED`.
- `GradingSummaryText`: compare with `GradingStatus.SKIPPED` and print `status().label()`.
- `ResultsTable`: change the Status column to `TableColumn<StudentProgress, GradingStatus>`. The cell shows `label()` and adds `styleClass()`. Build `STATUS_STYLES` from `GradingStatus.values()`.
- `StudentDetailPane`: show `status().label()`.
- `styles.css`: delete the unused `.status-auto-fixed` selector (keep `.status-graded`).

**Verify**: `grep -rnE '"(queued|running|graded|needs-review|skipped|auto-fixed)"' src/main/java` → no matches.

### Step 4: One mark formatter

Create `grading/scoring/Marks.java` as a final utility class with a private constructor and `public static String format(double marks)`, returning `String.format(Locale.ROOT, "%.1f", marks)`. Make `PlainLanguage.marks` delegate to it, or replace its callers. Use `Marks.format` in `ResultsTable` and `GradingSummaryText`.

**Verify**: `grep -rn '"%.1f"' src/main/java` → exactly 1 match, in `Marks.java`.

### Step 5: Tests

Change `MainViewGradingTest:292` to `assertEquals(GradingStatus.QUEUED, …)`. Add `src/test/java/grading/progress/ProgressTrackerTest.java` (pattern: any small test in `src/test/java/grading/scoring/`) with these tests:
- `skippedSubmissionsStartSkipped`: `prepare` with one submission that is not ready → status `SKIPPED`.
- `flaggedStudentNeedsReview`: `prepare`, `studentFinished(u, false)`, then `similarity(List.of(flaggedPair))` → status `NEEDS_REVIEW`. Build the pair with `new SimilarityPair("Q1a", "a", "b", 0.9, 0.9, 50, 50, List.of(), List.of(), true)`.

A ready submission for `prepare` can be built with the 5-argument `SubmissionValidationResult` constructor: `(Path.of("a"), "a", null, Map.of(), List.of())`. For a not-ready one, give it an error finding that blocks grading: `SubmissionFinding.error("USERNAME_UNKNOWN", "x")`.

**Verify**: `./mvnw -q -B clean verify` → exit 0.

## Done criteria

- [ ] Both greps in steps 2–3 return nothing; the `"%.1f"` grep returns 1
- [ ] `./mvnw -q -B clean verify` exits 0, with 2 new tests
- [ ] `plans/README.md` row updated

## STOP conditions

- A status literal exists that isn't in the list above (other than in tests). Report it rather than guessing its enum value.
- `SubmissionFinding.error("USERNAME_UNKNOWN", …)` turns out not to block grading. Find a code that does, using `SubmissionFinding.blocksGrading()`, and don't change that method.

## Maintenance notes

- A new status needs an enum value **and** a CSS rule `.status-<name>`.
