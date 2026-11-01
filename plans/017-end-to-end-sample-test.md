# Plan 017: Add an end-to-end test that grades the course's sample assessment against its ground truth

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**:
> `git diff --stat e820cd4..HEAD -- src/main/java/grading/session src/test/java/grading`
> Plans 008/011 add `GradingSession` methods and tests; that is expected.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW (test-only)
- **Depends on**: plans/008-run-outcome.md (uses `RunOutcome`)
- **Category**: tests
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

The rubric's application criterion is "core features complete, tested end-to-end", and the top band says "the solution works perfectly". All 167 current tests use one-line synthetic testers. Nothing checks that the app gives the right marks on the **actual** course materials: the 6 sample student ZIPs, the 5 official tester files and the LMS score sheet. Those are exactly what the demo and the instructors will use. The course ships a ground-truth file with the expected marks, so this test is cheap to write and catches real regressions (timeouts, header fixes, wrong folder structure).

**Important: the GitHub repository is public** (`gh repo view --json visibility` → `PUBLIC`). The course materials (exam tester files, sample student work) must **not** be committed. The test reads them from a local folder given by a system property and is **skipped** when it's absent.

## Current state

- Course materials live outside the repo, in the course's "Given materials" folder, which each team member keeps next to their clone of the repository. It contains:
  - `Tester-Files/` (`Q1aTester.java`, `Q1bTester.java`, `Q2aTester.java`, `Q2bTester.java`, `Q3Tester.java`, `readme.txt`)
  - `student-submission/` (6 ZIPs named like `2023-2024-<username>.zip`)
  - `IS442-ScoreSheet.csv` (usernames prefixed with `#`)
  - `groundtruth.txt`
  - `RenameToYourUsername/` (starter code)
- `groundtruth.txt` has one line per student in this format (marks are examples):

```
some.student.2024: Q1A:3.0   Q1B:3.0    Q2A:5.0    Q2B:1.0   Q3:0.0  Total: 12.0
```

  **Note:** at least one line's stated `Total` doesn't equal the sum of its per-question marks (a typo in the course file). Treat the per-question marks as authoritative and assert the sum. Ignore the stated total.
- The problem brief says `jing.lim.2021` submitted Q1–Q3 without a username folder and without names in the headers. The app will therefore list that student as needing identity confirmation (skipped). Confirm the identity in the test.
- Back-end API (`grading.session.GradingSession`), usable without JavaFX:
  - `new GradingSession(GradingSettings.current(), new ProgressTracker(), workFolder)`
  - `importTesters(Path)` → `TesterValidationResult` (`isValid()`)
  - `importSubmissions(Path)` → `List<SubmissionValidationResult>` (`username()`, `isReadyForGrading()`)
  - `confirmIdentity(String currentUsername, StudentIdentity)`; `StudentIdentity(String username, String name, String email)`
  - `startGrading()` returns immediately; poll `grading()` until false.
  - After plan 008, `lastOutcome()` must be `RunOutcome.COMPLETED`.
  - `currentScores()` → `ScoreSummary`; `find(username)` → `Optional<StudentScore>`; `StudentScore.questionScores()` is keyed by question id (`Q1a`, `Q1b`, `Q2a`, `Q2b`, `Q3`), and `total()`.
  - `submissions()` → the current list (after identity confirmation).
- Reports are written to `settings.reportsFolder()` (`grading-reports` relative to the working directory). For the test, copy the settings with another reports folder. `ui/MainViewController.useReportsFolder` shows how to rebuild a `GradingSettings` record with a different last component.
- Test style exemplar: `src/test/java/grading/execution/JavaGradingServiceTest.java` (JUnit 5, `@TempDir`, `@Timeout`).

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Run with materials | `./mvnw -q -B test -Dtest=SampleAssessmentEndToEndTest -Dsample.assessment.dir="<path to Given materials>"` | exit 0, 1 test run, 0 skipped |
| Run without | `./mvnw -q -B test -Dtest=SampleAssessmentEndToEndTest` | exit 0, test **skipped** |
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `src/test/java/grading/SampleAssessmentEndToEndTest.java` (create); `pom.xml`, only to pass the system property through to Surefire (`<systemPropertyVariables><sample.assessment.dir>${sample.assessment.dir}</sample.assessment.dir></systemPropertyVariables>` in the existing `maven-surefire-plugin` configuration).
**Out of scope:** copying any course material into the repository; changing grading code to make the test pass. A mismatch is a finding to report.

## Steps

### Step 1: Skeleton with a skip

```java
@Timeout(300)
class SampleAssessmentEndToEndTest {
    @TempDir Path temporary;

    @Test
    void gradesTheSampleAssessmentLikeTheGroundTruth() throws Exception {
        String dir = System.getProperty("sample.assessment.dir", "");
        Assumptions.assumeTrue(!dir.isBlank() && Files.isDirectory(Path.of(dir, "Tester-Files")),
                "Set -Dsample.assessment.dir to the course 'Given materials' folder to run this test.");
        ...
    }
}
```

Surefire turns an unset `${sample.assessment.dir}` into the literal string `${sample.assessment.dir}`. Treat any value starting with `${` as unset, too.

**Verify**: the "Run without" command → exit 0, and `target/surefire-reports/grading.SampleAssessmentEndToEndTest.txt` shows `Skipped: 1`.

### Step 2: Grade

1. Create the session with a settings copy whose reports folder is `temporary.resolve("reports")`.
2. `importTesters(dir/Tester-Files)` → assert `isValid()`.
3. `importSubmissions(dir/student-submission)` → assert 6 results.
4. For every result that isn't `isReadyForGrading()`: if its username or source file name contains `jing.lim.2021`, call `confirmIdentity(result.username(), new StudentIdentity("jing.lim.2021", "Jing Lim", "jing.lim.2021@computing.smu.edu.sg"))`; otherwise fail with the username and findings.
5. `startGrading()`, then poll `grading()` every 200 ms for up to 240 s.
6. Assert `lastOutcome() == RunOutcome.COMPLETED`.

### Step 3: Compare with the ground truth

Parse `groundtruth.txt` in the test. Each line is `username: Q1A:x Q1B:x Q2A:x Q2B:x Q3:x Total: y`; ignore the stated total. Map `Q1A`→`Q1a`, `Q1B`→`Q1b`, `Q2A`→`Q2a`, `Q2B`→`Q2b`, `Q3`→`Q3`. For each student, `currentScores().find(username)` must be present. Each question mark must equal the ground truth (delta 1e-9), and the total must equal the sum of the per-question marks. Collect **all** mismatches into one message (`username question expected actual`) before failing, so a single run shows everything.

### Step 4: Score sheet

Load `dir/IS442-ScoreSheet.csv` with `ScoreSheet.load`. Build `new ScoreSheetUpdater().preview(sheet, session.currentScores())` and assert `preview.notInSheet()` is empty: every graded student matched a sheet row, despite the `#` prefix.

**Verify**: the "Run with materials" command → exit 0, with 1 test run and 0 skipped.

## Done criteria

- [ ] The test **passes** with `-Dsample.assessment.dir=…` and is **skipped** without it
- [ ] `git status --short` shows no course material added (`git status --short | grep -iE "Tester-Files|student-submission|groundtruth|IS442"` → empty)
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `plans/README.md` row updated, **including the list of any mismatches found**

## STOP conditions

- Any per-question mark differs from the ground truth. Do **not** change grading code or the expected values. Report the full mismatch list. This is the most valuable possible outcome of this plan, because it's a real bug for the team.
- `jing.lim.2021` can't be matched to any skipped result.
- A grading run takes longer than 240 s.

## Maintenance notes

- Teammates run this before every demo with their own path to the materials.
- If the course provides a different sample next term, update the property, not the test.
