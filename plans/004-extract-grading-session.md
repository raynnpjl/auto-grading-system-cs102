# Plan 004: Move the grading session out of WebApplication into its own class

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> 1. Plans 002 and 003 must be DONE (check `plans/README.md`). This refactor is only safe because
>    `src/test/java/web/WebApplicationTest.java` (plan 002, 6 tests after plan 003) pins today's
>    behaviour. If that file is missing or has fewer than 6 tests, STOP.
> 2. `git status --short -- src` → must print nothing.
> 3. `./mvnw -q -B clean verify` → exit 0 before you change anything. If it fails, STOP.
> 4. Compare the "Current state" excerpts below with `src/main/java/web/WebApplication.java`. Plans 002
>    and 003 changed `start`/`stop` and added the busy guard to the two import methods; anything else
>    that differs is drift → STOP.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED — a behaviour-preserving refactor of the app's central class; mitigated by plan 002's tests.
- **Depends on**: plans/002-web-layer-tests.md, plans/003-refuse-uploads-while-grading.md
- **Category**: tech-debt
- **Planned at**: commit `5cad239` + uncommitted working tree, 2026-10-25

## Why this matters

This is a CS102 (introductory Java) course project graded on a rubric whose top band for design reads:
"Good attempt on doing object design. There was also the idea of separating the model from the view
… The design was clean. reader can easily figure out the intent of each class." Today
`web.WebApplication` (~450 lines) is both the "view" (HTTP routes, request parsing, JSON responses) and
the "model" (which testers and submissions are loaded, the latest results, running grading on a
thread, regrading one student, confirming an identity). Moving the model into a new
`grading.session.GradingSession` class leaves `WebApplication` as a thin HTTP layer and gives the team
one class to point at when explaining the design. Behaviour must not change at all.

## Current state

`src/main/java/web/WebApplication.java`, package `web`. Its state fields (lines ~69–106):

```java
private final GradingSettings settings = GradingSettings.current();
private final ProgressTracker progress = new ProgressTracker();
private final TesterFileValidationService testerService = new TesterFileValidationService(settings);
private final SubmissionPreparationService submissionService = new SubmissionPreparationService(settings);
private final SubmissionCorrectionService correctionService = new SubmissionCorrectionService(submissionService);
private final ScoreSheetUpdater scoreSheetUpdater = new ScoreSheetUpdater();
private final AtomicBoolean grading = new AtomicBoolean(false);
private final Map<String, Handler> routes = Map.ofEntries( ... );
private final Path uploads;

private volatile TesterValidationResult testers;
private volatile List<SubmissionValidationResult> submissions = List.of();
private volatile Path submissionWorkspace;
// Every student's latest grading result, kept so one student can be regraded without the others.
private volatile List<StudentGradingResult> latestResults = List.of();
// The latest finished attempt. Cleared whenever the submissions change, so links never show stale results.
private volatile GradingAttempt latestAttempt;
private volatile String latestAttemptId;
// The folder AttemptReportWriter wrote for the latest attempt; the ZIP and PDF links read from it.
private volatile Path latestAttemptFolder;
// The score sheet the user uploaded, or null before one is uploaded.
private volatile UploadedScoreSheet scoreSheet;
```

Methods and where each piece goes:

| Today in `WebApplication` | Goes to | Notes |
|---|---|---|
| `importTesters(HttpExchange)` | split | HTTP part stays (extract upload, reply JSON); `testerService.validate(...)` + `testers = result` + busy guard move to `GradingSession.importTesters(Path)` |
| `importSubmissions(HttpExchange)` | split | HTTP part stays; workspace creation, `prepareBatch`, the "no submissions" check, `submissions = ...`, `latestResults = List.of()`, `clearAttempt()`, `progress.prepare(...)` and the busy guard move to `GradingSession.importSubmissions(Path)` |
| `confirmIdentity(HttpExchange)` | split | form parsing and `new StudentIdentity(...)` stay; busy guard, finding the submission, and all of `replaceSubmission(...)` move to `GradingSession.confirmIdentity(String target, StudentIdentity identity)` |
| `replaceSubmission(...)` | `GradingSession` (private) | unchanged body |
| `startGrading(HttpExchange)` | split | checks + `compareAndSet` + thread start move to `GradingSession.startGrading()` |
| `regradeStudent(HttpExchange)` | split | query parsing stays; checks + thread start move to `GradingSession.regrade(String username)` |
| `runGrading(...)` | `GradingSession` (private) | unchanged body |
| `clearAttempt()`, `currentScores()`, `needsReview(...)` | `GradingSession` | `currentScores()` becomes public |
| `zipReports`, `studentReport` | stay | read the folder/id through `GradingSession` getters |
| `uploadScoreSheet`, `scoreSheetPreview`, `downloadScoreSheet`, field `scoreSheet`, `scoreSheetUpdater` | stay | read `grading()`, `hasResults()`, `currentScores()` from the session |
| `progressJson()` | stays | reads `testersReady()`, `submissionsReady()`, `reportsFolder()` from the session |
| `route`, `routes`, `start`, `stop`, `main`, `library`, `extractUpload`, `uploads` | stay | `uploads` is passed into the session's constructor |

The error messages the page and tests depend on (keep them byte-for-byte):

| Situation | Status | Message |
|---|---|---|
| grading running (uploads, identity, score sheet) | 409 | `Wait until grading has finished.` (constant `GRADING_RUNNING`) |
| grade/regrade while already running | 409 | `Grading is already running.` |
| no valid testers | 400 | `Upload valid tester files first.` |
| nothing gradable | 400 | `No submission is ready for grading.` |
| regrade an unconfirmed student | 400 | `Confirm this student's identity before grading them.` |
| unknown student (identity, regrade) | 404 | `Unknown student.` |
| upload with no submissions | 400 | `No student submissions were found. Choose the folder that holds the student ZIPs or folders, or one student's folder.` (constant `NO_SUBMISSIONS`) |

Today the route wrapper turns any unexpected exception into HTTP 500 (lines ~131–145):

```java
} catch (Exception exception) {
    exception.printStackTrace();
    sendError(exchange, HTTP_INTERNAL_ERROR, Messages.describe(exception));
}
```

Conventions to match: every class has a one-paragraph Javadoc saying what it is for; comments explain
*why*; constants for repeated strings; `volatile` fields for state read by the polling thread; plain
sentences in messages. Exemplar of a small focused class: `src/main/java/web/UploadedScoreSheet.java`.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./mvnw -q -B -DskipTests compile` | exit 0 |
| Web tests | `./mvnw -q -B test -Dtest=WebApplicationTest -Dsurefire.failIfNoSpecifiedTests=false` | exit 0, `Tests run: 6, Failures: 0, Errors: 0` in `target/surefire-reports/web.WebApplicationTest.txt` |
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Line count | `wc -l src/main/java/web/WebApplication.java src/main/java/grading/session/*.java` | see done criteria |

## Scope

**In scope**:
- `src/main/java/grading/session/GradingSession.java` (create)
- `src/main/java/grading/session/SessionRefusal.java` (create)
- `src/main/java/web/WebApplication.java`

**Out of scope** (do NOT touch):
- `src/test/java/web/WebApplicationTest.java` — it is the safety net; it must pass unchanged. If it
  needs editing to pass, the refactor changed behaviour → STOP.
- `ProgressTracker`, `JavaGradingService`, `GradingAttempt`, `AttemptReportWriter`, all of
  `grading.submission`, `index.html`, `HttpResponses`, `UploadedFolder`, `UploadedScoreSheet`.
- Renaming routes, changing JSON shapes, or changing any message text.

## Git workflow

- Branch: `advisor/004-extract-grading-session`.
- One commit per step below; plain sentence messages, e.g. `Add GradingSession with the grading state`,
  `Let WebApplication delegate to GradingSession`.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Create the refusal exception

Create `src/main/java/grading/session/SessionRefusal.java`:

```java
package grading.session;

/**
 * Thrown when the grading session cannot do what was asked right now, with a message for the user.
 * The web layer turns the reason into an HTTP status.
 */
public final class SessionRefusal extends RuntimeException {

    /** Why the request was refused. */
    public enum Reason {
        /** The request cannot be done with what has been uploaded, such as grading without testers. */
        INVALID,
        /** The named student is not in the batch. */
        NOT_FOUND,
        /** Grading is running; try again when it has finished. */
        BUSY
    }

    private final Reason reason;

    public SessionRefusal(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
```

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 2: Create `GradingSession` by moving code, not rewriting it

Create `src/main/java/grading/session/GradingSession.java` with this public shape. Fill every method
body by **moving** the matching code from `WebApplication` (see the table in "Current state") —
replace each `sendError(...)` + `return` with `throw new SessionRefusal(<reason>, <same message>)`
using: 400 → `INVALID`, 404 → `NOT_FOUND`, 409 → `BUSY`.

```java
package grading.session;

/**
 * One marker's grading session: the tester files and submissions that are loaded, the latest results,
 * and grading itself, which runs on its own thread. It knows nothing about HTTP; the web application
 * calls it and shows what it reports through the ProgressTracker.
 */
public final class GradingSession {

    public static final String GRADING_RUNNING = "Wait until grading has finished.";
    public static final String NO_SUBMISSIONS = "No student submissions were found. Choose the folder "
            + "that holds the student ZIPs or folders, or one student's folder.";

    // fields moved from WebApplication: settings, progress, testerService, submissionService,
    // correctionService, grading (AtomicBoolean), workFolder (was `uploads`), testers, submissions,
    // submissionWorkspace, latestResults, latestAttempt, latestAttemptId, latestAttemptFolder

    public GradingSession(GradingSettings settings, ProgressTracker progress, Path workFolder) { ... }

    /** Validates an uploaded tester folder and keeps it. Refused (BUSY) while grading runs. */
    public TesterValidationResult importTesters(Path folder) throws IOException { ... }

    /**
     * Prepares an uploaded batch and makes it the current one; returns the prepared submissions.
     * Refused (INVALID, NO_SUBMISSIONS) when no submission was found, and (BUSY) while grading runs.
     */
    public List<SubmissionValidationResult> importSubmissions(Path input) throws IOException { ... }

    /** Confirms who a skipped submission belongs to; everyone else keeps their results. */
    public synchronized void confirmIdentity(String target, StudentIdentity identity) throws IOException { ... }

    /** Starts grading every submission on a background thread. */
    public void startGrading() { ... }

    /** Starts grading one student again on a background thread; everyone else keeps their result. */
    public void regrade(String username) { ... }

    public boolean grading() { ... }            // grading.get()
    public boolean testersReady() { ... }       // testers != null && testers.isValid()
    public boolean submissionsReady() { ... }   // any submission isReadyForGrading()
    public boolean hasResults() { ... }         // latestAttempt != null
    public Optional<Path> reportsFolder() { ... }   // Optional.ofNullable(latestAttemptFolder)
    public String latestAttemptId() { ... }
    public ScoreSummary currentScores() { ... } // moved unchanged

    // private: replaceSubmission, runGrading, clearAttempt, needsReview — moved unchanged
}
```

Details that must stay exactly as they are today:
- `importSubmissions` creates the workspace as `workFolder.resolve("normalized-" + UUID.randomUUID())`.
- `confirmIdentity` creates the correction folder as `submissionWorkspace.resolve("guided-" + UUID.randomUUID())`.
- `runGrading` passes `workFolder` where it passed `uploads` to `GradingAttempt.assemble(...)`, keeps the
  `catch (Exception | LinkageError exception)` block, and sets `latestAttemptId`, `latestAttemptFolder`
  and `latestAttempt` last, in that order.
- The thread name stays `"grading-runner"`.

Do not change `WebApplication` in this step; the new class just has to compile next to it.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 3: Make `WebApplication` delegate to the session

In `WebApplication`:
1. Replace the moved fields with `private final GradingSession session;`, created in the constructor
   after `uploads`: `session = new GradingSession(settings, progress, uploads);` (keep the `settings` and
   `progress` fields — `progress` is still used by `progressJson()`).
2. Rewrite each handler from the table to: parse the request → call the session → send the reply.
   For example:
   ```java
   /** POST /api/grade: starts grading in the background and returns straight away. */
   private void startGrading(HttpExchange exchange) throws IOException {
       session.startGrading();
       sendJson(exchange, HTTP_ACCEPTED, "{\"started\":true}");
   }
   ```
   `importTesters` still checks the empty upload first (`EMPTY_UPLOAD`, 400) — but the busy check must
   come first, so call a guard before extracting, e.g. `if (session.grading()) throw new
   SessionRefusal(SessionRefusal.Reason.BUSY, GradingSession.GRADING_RUNNING);` at the top of both
   import handlers, then let the session re-check.
3. In `route(...)`, add a catch **before** the existing `catch (Exception ...)`:
   ```java
   } catch (SessionRefusal refusal) {
       sendError(exchange, statusOf(refusal.reason()), refusal.getMessage());
   } catch (Exception exception) {
   ```
   with
   ```java
   private static int statusOf(SessionRefusal.Reason reason) {
       return switch (reason) {
           case INVALID -> HTTP_BAD_REQUEST;
           case NOT_FOUND -> HTTP_NOT_FOUND;
           case BUSY -> HTTP_CONFLICT;
       };
   }
   ```
4. `uploadScoreSheet` and `downloadScoreSheet` keep their own 409 checks but read `session.grading()`;
   `downloadScoreSheet` and `scoreSheetPreview` use `session.hasResults()` and `session.currentScores()`.
5. `zipReports`/`studentReport` use `session.reportsFolder()` and `session.latestAttemptId()`.
6. Delete the constants `GRADING_RUNNING` and `NO_SUBMISSIONS` from `WebApplication` (use the ones on
   `GradingSession`), and delete every now-unused import.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.
**Verify**: `./mvnw -q -B test -Dtest=WebApplicationTest -Dsurefire.failIfNoSpecifiedTests=false` →
`Tests run: 6, Failures: 0, Errors: 0`.

### Step 4: Full suite and size check

**Verify**: `./mvnw -q -B clean verify` → exit 0.
**Verify**: `grep -c 'volatile' src/main/java/web/WebApplication.java` → `1` (only `scoreSheet` remains).
**Verify**: `grep -n 'JavaGradingService\|GradingAttempt.assemble\|new Thread' src/main/java/web/WebApplication.java`
→ no matches.
**Verify**: `wc -l src/main/java/web/WebApplication.java` → under 300.

### Step 5 (manual smoke test)

`./compile.sh && ./run.sh`, open `http://127.0.0.1:8080`, choose a tester folder and a submissions
folder, run grading, and download one student's PDF. Stop with Ctrl+C.

## Test plan

No new tests: plan 002's `WebApplicationTest` (6 tests, including plan 003's) is the contract this
refactor must keep. Optionally add `src/test/java/grading/session/GradingSessionTest.java` with one test
that `startGrading()` without testers throws `SessionRefusal` with reason `INVALID` and message
`Upload valid tester files first.` (pattern: `src/test/java/grading/reporting/GradingAttemptTest.java`).

## Done criteria

- [ ] `src/main/java/grading/session/GradingSession.java` and `SessionRefusal.java` exist
- [ ] `WebApplicationTest` passes unchanged (`git diff --stat -- src/test/java/web` is empty)
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `WebApplication.java` is under 300 lines and contains no `JavaGradingService`, `GradingAttempt.assemble` or `new Thread`
- [ ] `git status --short` lists only the three in-scope files (plus the optional test)
- [ ] `plans/README.md` status row updated

## STOP conditions

- Any `WebApplicationTest` test fails after step 3 and the cause is not a typo in moved code — the
  refactor changed behaviour; report which test and why instead of editing the test.
- Moving a method seems to require changing `ProgressTracker`, `GradingAttempt` or any other
  out-of-scope class.
- You find a second caller of `WebApplication`'s state outside `WebApplication` itself
  (`grep -rn 'new WebApplication' src` should show only `main` and the test).
- A circular package dependency appears: `grading.session` must not import anything from `web`.

## Maintenance notes

- New features that change what is loaded or graded belong in `GradingSession`; new routes belong in
  `WebApplication` and should only translate HTTP ↔ session calls.
- `SessionRefusal.Reason` is the only place HTTP status meaning is decided; keep it to three reasons.
- Plan 003 noted that its busy guard is check-then-act. With all state changes now inside
  `GradingSession`, making `importTesters`, `importSubmissions`, `startGrading` and `regrade`
  `synchronized` (like `confirmIdentity`) would close that gap; deliberately left out of this
  behaviour-preserving plan.
- Plan 005 (class design doc) should be written after this plan, so the diagram shows `GradingSession`.
