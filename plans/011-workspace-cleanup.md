# Plan 011: Delete replaced submission copies, and sweep work folders left by crashed sessions

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**:
> `git diff --stat e820cd4..HEAD -- src/main/java/grading/session src/main/java/ui/MainViewController.java`
> Plans 008/009 may have changed `GradingSession`/`MainViewController` elsewhere. Check that the excerpts below still match.

## Status

- **Priority**: P2
- **Effort**: S–M
- **Risk**: MED (deletes files — follow the safety rules exactly)
- **Depends on**: plans/008-run-outcome.md (same files)
- **Category**: bug (robustness)
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

Each session creates a temporary work folder (`<tmp>/auto-grader-<random>`) holding a normalized copy of every submission. That causes two leaks:

1. Every re-import in the same session adds another `normalized-<uuid>` copy, and the old ones stay until the window closes.
2. If the app crashes or is force-quit, the whole work folder stays in the temp directory forever. The earlier JavaFX version had a startup sweep for this (`SubmissionWorkspaces.removeAbandoned`), which was lost when the back end was restored.

A marker who grades several cohorts accumulates hundreds of MB of student code on disk.

## Current state

- `src/main/java/ui/MainViewController.java`:
  - `:68` `private static final String WORK_FOLDER_PREFIX = "auto-grader-";`
  - `initialize()` (~:199) does `workFolder = Files.createTempDirectory(WORK_FOLDER_PREFIX); session = new GradingSession(settings, progress, workFolder);`
  - `shutdown()` deletes the folder with `FileTrees.delete` (after waiting for a cancelled run).
- `src/main/java/grading/session/GradingSession.java` `importSubmissions(Path input)` (~:115-130):

```java
        refuseWhileGrading();
        Path workspace = Files.createDirectories(workFolder.resolve("normalized-" + UUID.randomUUID()));
        List<SubmissionValidationResult> prepared = submissionService.prepareBatch(input, workspace);
        // Keep the previous import when nothing usable was uploaded.
        if (prepared.isEmpty()) {
            throw new SessionRefusal(SessionRefusal.Reason.INVALID, NO_SUBMISSIONS);
        }
        submissions = List.copyOf(prepared);
        submissionWorkspace = workspace;
```

  `submissionWorkspace` holds the current copy. Identity corrections write `guided-<uuid>` folders **inside** it (`replaceSubmission`).
- `grading.common.FileTrees.delete(Path)` deletes a folder tree (throws `IOException`).
- Reports are written to `settings.reportsFolder()`, **not** the work folder, so deleting old normalized copies never touches reports.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `grading/session/GradingSession.java`, `grading/session/WorkFolders.java` (create), `ui/MainViewController.java` (`initialize` only), `src/test/java/grading/session/WorkFoldersTest.java` (create), `src/test/java/grading/session/GradingSessionTest.java` (create, or extend if plan 008 created it).

**Out of scope:** `reports.folder`; the temp folders created per compile by `TesterCompiler`/`TimeoutRecovery`, which already clean up after themselves.

## Steps

### Step 1: Delete the replaced copy after a successful import

In `GradingSession.importSubmissions`, keep `Path previous = submissionWorkspace;` before replacing it. Then:
- If `prepared.isEmpty()`, delete the **new** `workspace`, because it's useless, and throw as before.
- After `submissionWorkspace = workspace;` succeeds, delete `previous` when it's non-null and different from `workspace`.

Wrap each deletion in `try { FileTrees.delete(...) } catch (IOException e) { LOG.log(Level.WARNING, "Could not delete " + path, e); }`. A failed cleanup must never fail the import.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 2: `WorkFolders`, which creates the folder and sweeps abandoned ones

Create `grading/session/WorkFolders.java` (final, private constructor):

```java
public static final String PREFIX = "auto-grader-";
/** Folders untouched for this long are assumed to belong to a session that has ended. */
public static final Duration ABANDONED_AFTER = Duration.ofHours(12);

public static Path create(Path tempRoot) throws IOException      // Files.createTempDirectory(tempRoot, PREFIX)
public static int removeAbandoned(Path tempRoot, Instant now)    // returns how many were deleted
```

Safety rules for `removeAbandoned`. Follow them exactly:
- Only look at **direct children** of `tempRoot` that are directories, **not symbolic links** (`Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)`), and whose name starts with `PREFIX`.
- Delete one only if its last-modified time is older than `now.minus(ABANDONED_AFTER)`. This avoids deleting the folder of another instance that is still running.
- Catch and log any `IOException` per folder, then continue with the next.

Main code passes `Path.of(System.getProperty("java.io.tmpdir"))` and `Instant.now()`. Tests pass a `@TempDir` and a fixed instant.

In `MainViewController.initialize`, replace `Files.createTempDirectory(WORK_FOLDER_PREFIX)` with `WorkFolders.create(tmp)`, and call `WorkFolders.removeAbandoned(tmp, Instant.now())` on a daemon thread first (it may be slow). Remove the controller's `WORK_FOLDER_PREFIX`.

**Verify**: `grep -rn '"auto-grader-"' src/main/java` → 1 match, in `WorkFolders`.

### Step 3: Tests

`WorkFoldersTest`:
- `removesOldPrefixedFoldersOnly`: in `@TempDir root`, create `auto-grader-old` (mtime set to now − 13h with `Files.setLastModifiedTime`), `auto-grader-new` (now − 1h) and `other-old` (now − 13h). After `removeAbandoned(root, now)`, only `auto-grader-old` is gone, and the return value is 1.
- `doesNotFollowSymbolicLinks`: create `target/` with a file inside, plus a symlink `auto-grader-link → target` with an old mtime. After the sweep, `target/` and its file still exist. Use `assumeTrue` to skip on a filesystem that refuses symlinks.

`GradingSessionTest`:
- `reimportDeletesThePreviousCopy`: create a minimal student folder (see `src/test/java/grading/submission/SubmissionPreparationServiceTest.java` for how fixtures are written). Import it twice and assert that `workFolder` then contains exactly one `normalized-*` directory.

**Verify**: `./mvnw -q -B clean verify` → exit 0, with 3 new tests.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `grep -rn "createTempDirectory(WORK_FOLDER_PREFIX)" src` → no matches
- [ ] `plans/README.md` row updated

## STOP conditions

- `removeAbandoned` would need to delete anything outside direct children of the temp root.
- The UI test `removesNormalizedCopiesWhenClosed` fails.

## Maintenance notes

- If the app ever needs to keep normalized copies across sessions (e.g. to resume), raise `ABANDONED_AFTER` or make it a setting.
