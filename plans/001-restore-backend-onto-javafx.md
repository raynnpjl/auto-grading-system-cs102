# Plan 001: Restore the lost back end from `origin/for-submission` and run the JavaFX UI on `GradingSession`

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat e43d2e2..HEAD -- src pom.xml config test-submissions`
> and `git rev-parse --short origin/for-submission` (expected `0379d5f`).
> If either differs, compare the "Current state" facts against the live code
> before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED
- **Depends on**: none
- **Category**: migration
- **Planned at**: commit `e43d2e2`, 2026-10-30 (source of restored code: `origin/for-submission` @ `0379d5f`)

## Why this matters

The team's finished back end lives only on branch `origin/for-submission`. `main` was rebuilt from an older commit (`4fc0e71`, 24 Oct) and is missing 29 commits. Missing features include plagiarism detection, per-mistake feedback, the `GradingSession` model (cancel, regrade), the progress snapshot, the common-mistakes report and the `test-submissions/` folder. The team has decided to keep the **JavaFX desktop UI** that is on `main`, not the web UI on `for-submission`. This plan brings the whole `grading` back end over from `for-submission`, keeps `main`'s JavaFX `ui` package, and moves the controller onto `GradingSession`. The app then works as before on the restored back end. New screens for progress, plagiarism and feedback come in plans 002 and 003.

## Current state

The facts, all checked on 2026-10-30:

- `origin/for-submission` (`0379d5f`) is a **web app**: `src/main/java/web/**`, `src/main/resources/web/**`, `pom.xml` runs `web.WebApplication` via `exec-maven-plugin`. It has **no** `src/main/java/ui/`.
- `main` (`e43d2e2`) is a **JavaFX app**: `src/main/java/ui/{AutoGradingApplication, MainViewController, IdentityDialog, ScoreSheetConfirmation, GradingLog}.java`, `src/main/resources/ui/{main-view.fxml, styles.css}`, `pom.xml` runs `ui.AutoGradingApplication` via `javafx-maven-plugin`. The UI tests are `src/test/java/ui/{MainViewGradingTest, MainViewWiringTest, ScoreSheetWorkflowTest}.java`.
- A trial merge was done in a scratch folder: the `for-submission` tree without `web/`, plus `main`'s `pom.xml`. That combination **compiles and all 150 back-end tests pass**. Only the files below break when the old JavaFX controller is added on top:
  - `ui/MainViewController.java`: calls `IdentitySuggestions.fromSettings()`, `settings.maxLogCharacters()`, `new JavaGradingService(log, progress)`, `AttemptReportWriter.summaryOf(..)`, `scoreSheetUpdater.write(preview)` and `ScoreSheetUpdater.Result`. None of these exist on `for-submission`.
  - `ui/ScoreSheetConfirmation.java:32`: calls `preview.describe()`, which doesn't exist on `for-submission`.
  - `main`-only back-end files that no longer compile and are **dropped**: `grading/reporting/{GradingReportFormatter, ScoreSheetReportFormatter, ValidationReportFormatter}.java`, `grading/submission/{SubmissionWorkspaces, ValidationResult}.java` and their tests `grading/reporting/GradingReportFormatterTest.java` and `grading/submission/SubmissionWorkspacesTest.java`.
- `for-submission` APIs the new controller uses (exact signatures):
  - `grading.session.GradingSession(GradingSettings settings, ProgressTracker progress, Path workFolder)`
  - `TesterValidationResult importTesters(Path)`, `List<SubmissionValidationResult> importSubmissions(Path)`, `void confirmIdentity(String username, StudentIdentity)`, `void startGrading()`, `void regrade(String username)`, `void cancelGrading()`, `boolean grading()`, `boolean testersReady()`, `boolean submissionsReady()`, `boolean hasResults()`, `Optional<Path> reportsFolder()`, `String latestAttemptId()`, `ScoreSummary currentScores()`.
  - Refusals are thrown as `grading.session.SessionRefusal` (a `RuntimeException`) with a marker-readable `getMessage()`.
  - `startGrading()` returns immediately; grading runs on its own thread. Completion is visible as `session.grading() == false`, and `progress.snapshot().running()` goes false.
  - `grading.progress.ProgressTracker#snapshot()` returns a `ProgressSnapshot(boolean running, long done, long elapsedSeconds, String currentStudent, String currentStep, List<StudentProgress> students, List<String> activity, List<SimilarityPair> similarPairs)`, with `int total()`. It is thread-safe.
  - `grading.reporting.ScoreSheetUpdater`: `ScoreSheetPreview preview(ScoreSheet, ScoreSummary)` and `byte[] filledContent(ScoreSheetPreview)`. **It never writes the original file.** The filled sheet is new content to save elsewhere.
  - `ScoreSheetPreview(ScoreSheet sheet, List<Entry> entries, List<String> notInSheet, List<String> noSubmission)`, with `Entry(int row, String username, String previousScore, String newScore)`.
  - `IdentitySuggestions(String emailDomain)` is a constructor; there is no `fromSettings()`. `GradingSettings` has `emailDomain()`.
  - `grading.reporting.ReportArchive.save(Path attemptFolder, Path zipFile)` and `grading.common.FileTrees.delete(Path)` both exist.
  - `SubmissionValidationResult` has `username()`, `source()`, `normalizedRoot()`, `findings()`, `correctedCount()`, `isReadyForGrading()`, `hasErrors()`. It has **no** `missingQuestions()`.
  - `SubmissionFinding` has `severity()`, `message()`, `corrected()`, `nextStep()` (an `Optional<String>`) and `needsIdentityConfirmation()`.
- How the web UI managed the work folder, to copy: `WebApplication` created `Files.createTempDirectory("auto-grader-web-")` at start and deleted it with `FileTrees.delete` on exit.
- `grading.properties` on `for-submission` adds `grading.max-memory-mb`, `ui.port` and six `similarity.*` keys, all required by `GradingSettings.load`. Keep that file whole. `ui.port` is unused by JavaFX but must stay, or settings loading fails.
- `javafx-maven-plugin` 0.0.8 builds its classpath from Maven's *compile* classpath, so the `system`-scoped `lib/checkstyle-14.1.0-all.jar` is available when the app runs. No change is needed.
- Conventions: 4-space indents, Javadoc on public types, `final` classes, `Task<>` + `startWorker(...)` for background work, and `Messages.describe(Throwable)` for user-facing errors. `ui/MainViewController.java` is the exemplar for the JavaFX style.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./mvnw -q -B -DskipTests compile` | exit 0 |
| Compile tests | `./mvnw -q -B test-compile` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Test totals | `grep -h "Tests run" target/surefire-reports/*.txt \| awk -F'[:,]' '{r+=$2;f+=$4;e+=$6} END{print r,f,e}'` | `<N> 0 0` |
| Run app (needs a display) | `./run.sh` | window opens |

## Scope

**In scope:**
- Everything under `src/main/java/grading/**`, `src/test/java/grading/**` (replaced by `for-submission`'s versions)
- `src/main/resources/grading.properties`, `src/main/resources/assessment.properties`, `src/main/resources/grading/**`
- `config/**`, `test-submissions/**` (restored)
- `src/main/java/ui/MainViewController.java` (rewritten), `src/main/java/ui/ScoreSheetConfirmation.java`, `src/main/java/ui/AutoGradingApplication.java`, `src/main/java/ui/GradingLog.java` (may be deleted)
- `src/main/resources/ui/main-view.fxml` (button text and handlers only)
- `src/test/java/ui/**` (updated to the new controller)

**Out of scope:**
- `src/main/java/web/**`, `src/main/resources/web/**`: do **not** restore them. The team chose JavaFX.
- `pom.xml`: keep `main`'s version (JavaFX). Only remove the stale `<!-- <mainClass>web.WebApplication</mainClass> -->` comment line.
- New UI features (progress table, cancel, regrade, plagiarism or feedback panels): those are plans 002 and 003.
- `README.md`: deliberately deferred to final submission prep.
- Do **not** commit. The user commits their own work. Work in the current checkout on the current branch.

## Steps

### Step 1: Restore the back end, config and test submissions from `for-submission`

```sh
git checkout origin/for-submission -- src/main/java/grading src/test/java/grading src/main/resources/grading.properties src/main/resources/assessment.properties src/main/resources/grading config test-submissions src/test/resources
git rm -q -r --cached --ignore-unmatch src/main/java/grading/reporting/GradingReportFormatter.java src/main/java/grading/reporting/ScoreSheetReportFormatter.java src/main/java/grading/reporting/ValidationReportFormatter.java src/main/java/grading/submission/SubmissionWorkspaces.java src/main/java/grading/submission/ValidationResult.java src/test/java/grading/reporting/GradingReportFormatterTest.java src/test/java/grading/submission/SubmissionWorkspacesTest.java
rm -f src/main/java/grading/reporting/GradingReportFormatter.java src/main/java/grading/reporting/ScoreSheetReportFormatter.java src/main/java/grading/reporting/ValidationReportFormatter.java src/main/java/grading/submission/SubmissionWorkspaces.java src/main/java/grading/submission/ValidationResult.java src/test/java/grading/reporting/GradingReportFormatterTest.java src/test/java/grading/submission/SubmissionWorkspacesTest.java
```

(`git checkout <branch> -- <paths>` stages the files but does not commit. That's fine. Do not run `git commit`.)

**Verify**: `ls src/main/java/grading` → includes `feedback session similarity`; `ls test-submissions` → `RenameToYourUsername copied.answer.2025 loop.q1b.2025 missing.files.2025`; `grep -c "^similarity\." src/main/resources/grading.properties` → `6`.

### Step 2: Check the back end compiles on its own

Temporarily move the UI out of the way so only the back end compiles: `mkdir -p /tmp/ui-hold && mv src/main/java/ui src/test/java/ui /tmp/ui-hold/` (use different names for the two folders: `mv src/main/java/ui /tmp/ui-hold/main-ui && mv src/test/java/ui /tmp/ui-hold/test-ui`).

**Verify**: `./mvnw -q -B clean verify` → exit 0, and test totals show about 150 runs with `0 0` failures and errors.

Then move them back: `mv /tmp/ui-hold/main-ui src/main/java/ui && mv /tmp/ui-hold/test-ui src/test/java/ui`.

### Step 3: Fix `ScoreSheetConfirmation` (no `preview.describe()`)

In `src/main/java/ui/ScoreSheetConfirmation.java`, add a private static `describe(ScoreSheetPreview preview)`. Use the text `main` used to produce in `ScoreSheetPreview.describe()`, reproduced here:

```java
StringBuilder text = new StringBuilder()
        .append("File: ").append(preview.sheet().path()).append('\n')
        .append("Scores to write: ").append(preview.entries().size()).append(" of ")
        .append(preview.sheet().rowCount()).append(" students in the sheet\n");
for (ScoreSheetPreview.Entry entry : preview.entries()) {
    if (!entry.previousScore().isBlank() && !entry.previousScore().equals(entry.newScore())) {
        text.append("  ").append(entry.username()).append(": ").append(entry.previousScore())
                .append(" -> ").append(entry.newScore()).append('\n');
    }
}
appendList(text, "Graded students not in the sheet (not written)", preview.notInSheet());
appendList(text, "Sheet students with no graded submission (left unchanged)", preview.noSubmission());
return text.toString();
```

(`appendList` prints `title: none|count` followed by one indented username per line.) Change the header text from "A backup of the current file is saved first." to "The filled sheet is saved as a new file; the original is not changed." Rename the confirm button label from "Write" to "Save filled sheet". Make `describe` package-private so the controller can reuse it.

**Verify**: none yet. The controller is fixed in step 4, and the full compile runs at the end of step 4.

### Step 4: Rewrite `MainViewController` on top of `GradingSession`

Replace the controller's state machine with a thin binding to the session. Target shape (keep the existing `@FXML` fields and `initialize()` method names exactly as they are, because `main-view.fxml` references them):

```java
private final GradingSettings settings = GradingSettings.current();
private final ProgressTracker progress = new ProgressTracker();
private final IdentityDialog identityDialog = new IdentityDialog(new IdentitySuggestions(settings.emailDomain()));
private final ScoreSheetUpdater scoreSheetUpdater = new ScoreSheetUpdater();
private Path workFolder;          // created in initialize(), deleted in shutdown()
private GradingSession session;   // created in initialize()
private Timeline poller;          // 250 ms; started when grading starts, stopped when it ends
private ScoreSheet scoreSheet;
private List<SubmissionValidationResult> submissions = List.of();
```

Behaviour, handler by handler:

- `initialize()`: `workFolder = Files.createTempDirectory("auto-grader-")`; `session = new GradingSession(settings, progress, workFolder)`. If creating the folder fails, show the error in `findingsConsole` and disable the import buttons.
- `importTesterFiles(Path)`: in a background `Task`, call `session.importTesters(path)`. On success, rebuild the tester tree as before. Write a console section from `result.findings()` using the format in "Console text format" below. Catch `SessionRefusal` and show its message in `consoleStatus`.
- `importSubmissions(Path)`: in a background `Task`, call `session.importSubmissions(path)` and keep the returned list in `submissions`. Rebuild the submission tree as before (`statusSuffix` loses the missing-questions branch, since `missingQuestions()` is gone). Write the console section from each result's findings. Auto-open the identity dialog when any result `needsIdentityConfirmation`, as today.
- `applyConfirmedIdentities(Map<SubmissionValidationResult, StudentIdentity>)`: in a background `Task`, call `session.confirmIdentity(result.username(), identity)` for each entry. Then refresh `submissions` by re-reading usernames: rebuild the list from `progress.snapshot().students()` order, or keep a local copy updated the same way `GradingSession.replaceSubmission` does. Simplest correct option: re-run `importSubmissions` is **not** allowed, because it would discard corrections. If you can't get the updated `SubmissionValidationResult` list without changing `GradingSession`, add a single public getter `public List<SubmissionValidationResult> submissions()` to `GradingSession` that returns the current `submissions` field. This is the **only** permitted change to `grading/session/GradingSession.java`.
- `runGrading()`: call `session.startGrading()` (catch `SessionRefusal` → `consoleStatus`). Disable the inputs, then start `poller`. Each tick reads `ProgressSnapshot s = progress.snapshot()`, sets `statusText` to `"Grading " + s.done() + " of " + s.total()` and shows `String.join("\n", s.activity())` in `findingsConsole`, scrolled to the end. When `!session.grading()`, stop the poller and show the final results: one line per `StudentProgress` with username, per-question scores, total and status. Enable Save reports when `session.reportsFolder().isPresent()`. Enable Write scores when `scoreSheet != null && session.hasResults()`.
- `importScoreSheet(Path)`: unchanged logic using `ScoreSheet.load`.
- `writeScoresToSheet(Predicate<ScoreSheetPreview> confirmation)`: `preview = scoreSheetUpdater.preview(scoreSheet, session.currentScores())`. If `preview.entries().isEmpty()`, report "No graded student matches a username in the imported score sheet." and return. If confirmed, open a `FileChooser` save dialog. The initial folder is the sheet's folder and the initial name is `<sheet name without .csv>-graded.csv`. Write `Files.write(target, scoreSheetUpdater.filledContent(preview))`. If the chosen target equals `scoreSheet.path()`, refuse with "Choose a different file name; the original sheet is kept unchanged." Keep the package-private overload so tests can pass the target path: `void writeScoresToSheet(Predicate<ScoreSheetPreview> confirmation, Path target)`.
- `saveReports(Path zipFile)`: `ReportArchive.save(session.reportsFolder().orElseThrow(), zipFile)` in a background `Task`. Initial file name `"grading-reports-" + session.latestAttemptId() + ".zip"`.
- `shutdown()`: if `session.grading()`, call `session.cancelGrading()` and ignore any `SessionRefusal`. Then `FileTrees.delete(workFolder)` and ignore `IOException`.
- Keep the package-private test hooks used by `src/test/java/ui/**` (`importTesterFiles(Path)`, `importSubmissions(Path)`, `importScoreSheet(Path)`, `writeScoresToSheet(...)`, `saveReports(Path)`, `useReportsFolder(Path)`, `disableAutomaticIdentityPrompt()`, `formatSubmissionReadiness`). `useReportsFolder` can no longer redirect output, because `GradingSession` uses `settings.reportsFolder()`. Remove it and update the tests to read the reports folder from `session.reportsFolder()` through a new package-private getter `Optional<Path> latestReportsFolder()`.
- Delete `GradingLog.java` if nothing uses it any more. The activity list in the snapshot is already capped at 200 lines.
- Remove every `// xuan` / `edited by xuan` comment and every commented-out code block in `ui/`.

**Console text format** for findings (replaces the dropped `ValidationReportFormatter`): put it in a new package-private `ui/FindingsText.java` with static methods `testers(TesterValidationResult)` and `submissions(List<SubmissionValidationResult>)`. For each finding the output is `"  " + severity + (corrected ? " (corrected)" : "") + " - " + message`, followed by `"    Next step for marker: " + nextStep` when present. For submissions, start with a header giving the counts: processed, ready, needs attention, automatic corrections.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0; `grep -rn "xuan\|summaryOf\|maxLogCharacters\|fromSettings" src/main/java` → no matches.

### Step 5: Update the UI tests

`src/test/java/ui/{MainViewGradingTest, MainViewWiringTest, ScoreSheetWorkflowTest}.java` exercise the old controller. Update them to:
- wait for grading by polling `!controller.isGrading()` (add a package-private `boolean isGrading()` that returns `session.grading()`), with a timeout, instead of the old task;
- assert that the score sheet flow writes a **new** file (`*-graded.csv`) and leaves the original byte-for-byte unchanged (`Files.mismatch(original, copyBefore) == -1`);
- drop the assertions about the `.backup-*.csv` file and the "file changed after import" refusal, because the original is never written.

Use `src/test/java/ui/ScoreSheetWorkflowTest.java` as the structural pattern (it already starts JavaFX once per JVM; `pom.xml` sets `reuseForks=false`).

**Verify**: `./mvnw -q -B clean verify` → exit 0; the test totals line shows `0 0` for failures and errors.

### Step 6: Smoke-test the real app with the restored test submissions

`./run.sh`. Then, in the app: import a `Tester-Files` folder (the course-provided one; ask the user for its path if it's not in the repo), import `test-submissions/`, click Run grading, import a score sheet CSV and save the filled sheet.

**Verify** (manual): grading finishes. `grading-reports/<attempt>/summary.pdf` exists and contains a plagiarism section that lists `copied.answer.2025`. The saved `*-graded.csv` has numerators filled and the original CSV is unchanged.

## Test plan

- Back-end tests come back from `for-submission` unchanged (about 150, including `SimilarityCheckerTest`, `FeedbackGeneratorTest`, `CompilerHintsTest`, `GradingAttemptTest` and `StudentOutcomeTest`).
- UI tests are updated as in step 5. Add one new test, `ScoreSheetWorkflowTest#savingRefusesToOverwriteTheOriginalSheet`.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0; 0 failures and 0 errors
- [ ] `ls src/main/java/grading/similarity src/main/java/grading/feedback src/main/java/grading/session` all succeed
- [ ] `ls test-submissions` lists 4 folders
- [ ] `test ! -d src/main/java/web` (the web UI was not restored)
- [ ] `grep -rn "xuan" src` → no matches; `grep -n "web.WebApplication" pom.xml` → no matches
- [ ] `git diff --cached --stat -- src/main/java/grading/session/GradingSession.java` shows at most the one added getter
- [ ] Nothing committed (`git log -1 --format=%h` is still `e43d2e2`)
- [ ] `plans/README.md` row for 001 updated

## STOP conditions

- `git rev-parse --short origin/for-submission` is not `0379d5f`. Someone pushed to it, so re-plan.
- Step 2 (back end alone) does not pass. The restored code is not what was trialled.
- The controller needs any change to `grading/**` beyond the single `GradingSession.submissions()` getter.
- The app cannot run Checkstyle at runtime (`NoClassDefFoundError: com/puppycrawl/...` in the activity log).
- You are tempted to restore `src/main/java/web/**`. Don't; report instead.

## Maintenance notes

- `GradingSession` is now the only owner of grading state. Plans 002 and 003 build their views from `ProgressTracker.snapshot()`. Do not add grading state back into the controller.
- Score sheets are now saved as a new file instead of being overwritten in place with a backup. This is a behaviour change from the old `main`; the README must describe it at submission prep.
- `ui.port` in `grading.properties` is unused by the JavaFX app. Removing it means also removing `port` from `GradingSettings`. That is deferred because it touches back-end tests.
