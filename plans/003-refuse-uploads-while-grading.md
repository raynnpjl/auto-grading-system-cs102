# Plan 003: Refuse tester and submission uploads while grading is running

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> 1. Plan 002 must be DONE (check `plans/README.md`): this plan adds a test to the class plan 002
>    creates (`src/test/java/web/WebApplicationTest.java`). If that file does not exist, STOP.
> 2. `git status --short -- src/main/java/web src/main/resources/web` → must print nothing.
> 3. `git diff --stat 5cad239..HEAD -- src/main/java/web/WebApplication.java src/main/resources/web/index.html`
>    → then compare the "Current state" excerpts with the live files; on a mismatch, STOP.
>    (Plan 002 legitimately changed `WebApplication.start`; that is expected.)

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/002-web-layer-tests.md
- **Category**: bug
- **Planned at**: commit `5cad239` + uncommitted working tree, 2026-10-25

## Why this matters

Grading runs on a background thread. While it runs, the server still accepts a new tester folder or a
new batch of submissions. A new submissions upload replaces the batch and clears the progress table
(`progress.prepare(...)`) while the grading thread keeps writing results for the old batch — the
marker sees a wiped or mixed-up table, and the finished attempt can describe a batch that is no longer
loaded. Other state-changing routes in the same class (confirm identity, upload score sheet, download
score sheet) already refuse with HTTP 409 while grading runs; the two upload routes were missed. The
course's rubric penalises "usability issues (include input validations)", and this is one.

## Current state

- `src/main/java/web/WebApplication.java`
  - A grading flag and a shared message (lines ~61, ~75):
    ```java
    private static final String GRADING_RUNNING = "Wait until grading has finished.";
    ...
    private final AtomicBoolean grading = new AtomicBoolean(false);
    ```
  - The existing guard pattern to copy, from `confirmIdentity` (line ~197):
    ```java
    private synchronized void confirmIdentity(HttpExchange exchange) throws IOException {
        if (grading.get()) {
            sendError(exchange, HTTP_CONFLICT, GRADING_RUNNING);
            return;
        }
    ```
  - The two unguarded routes (lines ~147–188):
    ```java
    /** POST /api/testers: the body is a zip of the Tester-Files folder. */
    private void importTesters(HttpExchange exchange) throws IOException {
        Optional<Path> folder = extractUpload(exchange, "testers-");
        ...
    }

    /**
     * POST /api/submissions: the body is a zip whose top level holds the student ZIPs or folders
     * (or one student's folder on its own).
     */
    private void importSubmissions(HttpExchange exchange) throws IOException {
        Optional<Path> input = extractUpload(exchange, "submissions-");
        ...
        submissions = List.copyOf(prepared);
        submissionWorkspace = workspace;
        latestResults = List.of();
        clearAttempt();
        progress.prepare(prepared);
        ...
    }
    ```
- `src/main/resources/web/index.html` — a Vue 3 page. `state.running` is `true` while grading runs (it
  comes from `GET /api/progress`). The three file inputs today:
  ```html
  <input id="tester-folder" class="form-control" type="file" webkitdirectory :disabled="uploadingTesters"
    @change="chooseFolder($event, 'testers')">
  ...
  <input id="submission-folder" class="form-control" type="file" webkitdirectory
    :disabled="uploadingSubmissions" @change="chooseFolder($event, 'submissions')">
  ...
  <input id="score-sheet" class="form-control" style="max-width: 420px" type="file" accept=".csv,text/csv"
    @change="uploadScoreSheet($event)">
  ```
  Errors from the server are already shown to the user (the upload functions put `data.error` into the
  status line under each input), so a 409 needs no new page code.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Web tests | `./mvnw -q -B test -Dtest=WebApplicationTest -Dsurefire.failIfNoSpecifiedTests=false` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope**:
- `src/main/java/web/WebApplication.java` — `importTesters` and `importSubmissions` only.
- `src/main/resources/web/index.html` — the three `<input>` elements above only.
- `src/test/java/web/WebApplicationTest.java` — add one test (and, if needed, one helper).

**Out of scope**:
- `startGrading`, `regradeStudent`, `runGrading` and every other method — their behaviour is correct.
- Making the guard race-free with locks (see Maintenance notes) — deliberately not done.
- Any styling or text changes on the page.

## Git workflow

- Branch: `advisor/003-refuse-uploads-while-grading`.
- Plain sentence commit message, e.g. `Refuse uploads while grading is running`.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Write the failing test first

In `src/test/java/web/WebApplicationTest.java` add `refusesUploadsWhileGradingRuns`:

- Build the testers with the existing `createTesters()` helper, then **overwrite** `Q1aTester.java` in
  that folder so grading stays busy for a few seconds:
  ```java
  write("testers/Q1aTester.java", "public class Q1aTester extends Q1a { public static void main(String[] args)"
          + " throws InterruptedException { Thread.sleep(3000); System.out.println(score()); } }");
  ```
  (3 s is below the 5 s execution limit in `src/main/resources/grading.properties`, so it still passes.)
- Create one student with `createSubmission(batch, "alice", 1.0)`; upload testers and submissions;
  `POST /api/grade` → 202.
- Immediately: `POST /api/testers` with the same tester ZIP → **409**, body contains
  `Wait until grading has finished.`; `POST /api/submissions` with the same batch ZIP → **409**, same text.
- Then `waitForGradingToFinish()` and assert alice's total is 5.0 (the refused upload changed nothing).

**Verify**: `./mvnw -q -B test -Dtest=WebApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
→ the new test FAILS (it gets 200 instead of 409). The other 5 tests pass. If the new test passes
before step 2, STOP.

### Step 2: Guard the two routes

At the very start of both `importTesters` and `importSubmissions`, before `extractUpload(...)`, add the
same guard `confirmIdentity` uses:

```java
if (grading.get()) {
    sendError(exchange, HTTP_CONFLICT, GRADING_RUNNING);
    return;
}
```

Add one line to each method's Javadoc: `Refused while grading is running.`

**Verify**: `./mvnw -q -B test -Dtest=WebApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
→ exit 0; `target/surefire-reports/web.WebApplicationTest.txt` shows `Tests run: 6, Failures: 0, Errors: 0`.

### Step 3: Disable the file inputs on the page while grading runs

In `index.html` change only the `:disabled` bindings:

- tester input: `:disabled="uploadingTesters || state.running"`
- submission input: `:disabled="uploadingSubmissions || state.running"`
- score-sheet input: add `:disabled="state.running"`

**Verify**: `grep -c 'state.running' src/main/resources/web/index.html` → a number at least 3 higher
than before the change (run the same grep before editing and note it).
**Verify (manual, optional)**: `./compile.sh && ./run.sh`, open `http://127.0.0.1:8080`, start a
grading run, and confirm the three file inputs are greyed out until it finishes.

### Step 4: Full suite

**Verify**: `./mvnw -q -B clean verify` → exit 0.

## Test plan

One new test, `refusesUploadsWhileGradingRuns`, in `WebApplicationTest` (pattern: the existing
`gradesABatchThenRegradesOneStudent` test from plan 002). It covers both routes returning 409 during a
run and the run's results being unaffected.

## Done criteria

- [ ] `grep -n 'GRADING_RUNNING' src/main/java/web/WebApplication.java` shows uses inside
      `importTesters` and `importSubmissions`
- [ ] `WebApplicationTest` has 6 tests, all passing
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `git status --short` lists only the three in-scope files
- [ ] `plans/README.md` status row updated

## STOP conditions

- `WebApplicationTest.java` or its helpers (`createTesters`, `createSubmission`, `zipFolder`,
  `waitForGradingToFinish`, `write`) do not exist — plan 002 has not landed.
- The new test is flaky (passes/fails across two runs) — report the timing you saw rather than
  raising the sleep above 4 s.
- The 409 response breaks the page's upload code (for example the status line shows "Upload failed"
  instead of the server's message) — report; do not rewrite the page's upload functions.

## Maintenance notes

- The guard is check-then-act: a request that arrives in the same instant grading starts could still
  slip through. That is accepted for a single-user local tool; making it airtight means guarding
  every state change with one lock, which plan 004's `GradingSession` is the natural place for.
- Any new route that changes the batch must get the same guard.
