# Plan 016: Write a per-question `scores.csv` with every grading attempt

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**:
> `git diff --stat e820cd4..HEAD -- src/main/java/grading/reporting/AttemptReportWriter.java src/main/java/grading/scoring`

## Status

- **Priority**: P3
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (independent of the UI plans)
- **Category**: direction
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

The LMS score sheet only receives each student's **total**. Instructors often want the per-question breakdown in a spreadsheet, for moderation, to spot a question everyone failed, or to re-weight. Today that breakdown exists only inside PDFs. A `scores.csv` beside `summary.pdf` in every attempt folder gives it to them at no extra effort. It also goes straight into "Save reports" (which zips the attempt folder) and into the demo.

## Current state

- `src/main/java/grading/reporting/AttemptReportWriter.java` `write(GradingAttempt attempt)` (~:56-91) creates the attempt folder, writes one report per student under `students/`, then `summary` via `save(...)`. Problems are collected in a `List<String> problems`; a failure is added there, never thrown. Constants at :28-33 (`SUMMARY_NAME = "summary"`, etc.).
- `GradingAttempt.scores()` returns `List<ScoreBreakdown>`. `grading/scoring/ScoreBreakdown.java` is `record ScoreBreakdown(String username, Map<String, Double> questionScores, double testerMarks, int styleViolations, double stylePenalty, double finalMark)`. `questionScores` keeps question order (it's a `LinkedHashMap` copy).
- Question ids in order: `AssessmentDefinition.QUESTIONS` (each `.id()`).
- The CSV library `org.apache.commons.csv` (`CSVFormat`, `CSVPrinter`) is already a dependency (`pom.xml`), used in `reporting/ScoreSheet.java`.
- Mark formatting: after plan 009 it's `grading.scoring.Marks.format(double)`. If 009 isn't done, use `String.format(Locale.ROOT, "%.1f", value)`.
- Test pattern: `src/test/java/grading/reporting/AttemptReportWriterTest.java` (builds an attempt with its `attempt()` helper and writes into `@TempDir`).

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Tests | `./mvnw -q -B test -Dtest=AttemptReportWriterTest` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `grading/reporting/ScoresCsv.java` (create), `AttemptReportWriter.java`, `AttemptReportWriterTest.java`.
**Out of scope:** the LMS score sheet flow; the UI (the file appears in the attempt folder and in the ZIP automatically).

## Steps

### Step 1: `ScoresCsv`

A final class with `static String format(List<ScoreBreakdown> scores)`. The header row is `Username`, then one column per `AssessmentDefinition.QUESTIONS` id, then `Tester total`, `Style violations`, `Style penalty`, `Final mark`. Write one row per breakdown in list order; a question missing from `questionScores` is an empty cell. Use `CSVPrinter` with `CSVFormat.DEFAULT` and `\r\n` record separators (the LMS export uses CRLF). Format marks with the shared formatter.

### Step 2: Write it

In `AttemptReportWriter.write`, after the summary, write `folder.resolve(SCORES_FILE)` with `SCORES_FILE = "scores.csv"`, UTF-8. Wrap it in the same `try/catch (IOException | RuntimeException)` → `problems.add("The scores CSV could not be written: " + Messages.describe(exception))` pattern as the summary.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 3: Test

In `AttemptReportWriterTest`, add `writesAScoresCsvWithOneColumnPerQuestion`. Write the helper's attempt, read `scores.csv`, and assert:
- the header equals `Username,Q1a,Q1b,Q2a,Q2b,Q3,Tester total,Style violations,Style penalty,Final mark`;
- there's one line per student in `attempt.scores()`.

**Verify**: `./mvnw -q -B clean verify` → exit 0.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0, with 1 new test
- [ ] `plans/README.md` row updated

## STOP conditions

- `AttemptReportWriterTest.attempt()` doesn't exist or builds no scores. Report it; don't build a large new fixture.

## Maintenance notes

- If penalties gain new kinds, add columns here too. The column order is part of what users rely on.
