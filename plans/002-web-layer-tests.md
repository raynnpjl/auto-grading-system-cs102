# Plan 002: Cover the web application's HTTP routes with end-to-end tests

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> 1. `git status --short -- src/main/java/web src/test/java` → must print nothing. This plan was
>    written against commit `5cad239` *plus uncommitted working-tree changes* (2026-10-25); if files
>    there show as modified, the operator has not committed that work yet — STOP and ask.
> 2. `git diff --stat 5cad239..HEAD -- src/main/java/web/WebApplication.java` → then compare the
>    "Current state" excerpts below with the live file; on a mismatch, STOP.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: tests
- **Planned at**: commit `5cad239` + uncommitted working tree, 2026-10-25

## Why this matters

`web.WebApplication` is the whole user-facing application: a small HTTP server (JDK
`com.sun.net.httpserver.HttpServer`) that a browser page calls to upload tester files and student
submissions, run grading, regrade one student, confirm a student's identity, and download PDF reports
and the filled score sheet. None of its routes are tested; only `UploadedFolder` (a helper) is. These
are the flows shown in the project demo, and plans 003 and 004 change this class — 004 is a
refactor that is only safe with tests that pin today's behaviour. This plan adds a JUnit 5 test class
that starts the real server on a free port and calls it over HTTP.

## Current state

- `src/main/java/web/WebApplication.java` — the server. Routes are a `Map` from `"METHOD /path"` to a
  handler (lines ~76–92):
  ```java
  Map.entry("GET /", HttpResponses::sendPage),
  Map.entry("GET /lib/bootstrap.min.css", library("bootstrap.min.css", HttpResponses.CSS)),
  Map.entry("GET /lib/vue.global.prod.js", library("vue.global.prod.js", HttpResponses.JAVASCRIPT)),
  Map.entry("GET /lib/jszip.min.js", library("jszip.min.js", HttpResponses.JAVASCRIPT)),
  Map.entry("GET /api/progress", exchange -> sendJson(exchange, progressJson())),
  Map.entry("GET /api/reports/zip", this::zipReports),
  Map.entry("GET /api/reports/student", this::studentReport),
  Map.entry("GET /api/scoresheet/preview", this::scoreSheetPreview),
  Map.entry("GET /api/scoresheet/download", this::downloadScoreSheet),
  Map.entry("POST /api/testers", this::importTesters),
  Map.entry("POST /api/submissions", this::importSubmissions),
  Map.entry("POST /api/identity", this::confirmIdentity),
  Map.entry("POST /api/grade", this::startGrading),
  Map.entry("POST /api/grade/student", this::regradeStudent),
  Map.entry("POST /api/scoresheet", this::uploadScoreSheet));
  ```
- How the server starts today (lines ~112–125). `start` returns nothing and the `HttpServer` is a
  local variable, so a test cannot learn the port or stop the server:
  ```java
  public static void main(String[] args) throws IOException {
      int port = GradingSettings.current().port();
      new WebApplication().start(port);
      System.out.println("Auto-grader running at http://" + HOST + ":" + port);
  }

  /** Starts the web server. Only this computer can connect to it. */
  public void start(int port) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress(HOST, port), 0);
      // A thread pool, so a slow upload does not block the progress updates.
      server.setExecutor(Executors.newCachedThreadPool());
      server.createContext("/", this::route);
      server.start();
  }
  ```
- Request formats (what the browser page sends):
  - `POST /api/testers` and `POST /api/submissions`: the request body is a **ZIP of the chosen folder**,
    with entry names that start with the folder name (e.g. `Tester-Files/Q1aTester.java`). A body that
    is not a ZIP gets HTTP 400 with `{"error":"The upload is empty or is not a ZIP file. Choose a folder that contains files."}`.
    Testers reply `{"valid":true|false,"messages":[...]}`; submissions reply `{"total":N,"ready":N,"skipped":N}`.
  - `POST /api/grade` → 202 `{"started":true}`; grading runs on a background thread. Before valid
    testers are uploaded it returns 400 `{"error":"Upload valid tester files first."}`.
  - `POST /api/grade/student?username=X` → 202; unknown username → 404 `{"error":"Unknown student."}`.
  - `GET /api/progress` → one JSON object. Keys used by tests: `"running":true|false`,
    `"reportsReady":true|false`, and `"students":[...]` where each student object starts
    `{"username":"alice","status":"graded",...` and contains `"total":5.0`. The JSON is hand-built
    (no JSON library in the project), field order is stable: `username, status, finished, formatFixed,
    scores, total, issues, flags, feedback`.
  - `GET /api/reports/zip` → 404 before any grading; 200 `application/zip` after.
  - `GET /api/reports/student?username=X` → 200 `application/pdf` after grading.
  - `GET /api/scoresheet/download` with no score sheet uploaded → 404 `{"error":"Upload a score sheet first."}`.
- Reports are written to `grading-reports/` under the current directory (the repo root when Maven runs
  tests). That folder is in `.gitignore`.
- Existing test conventions to match — see `src/test/java/grading/execution/JavaGradingServiceTest.java`:
  JUnit 5 (`org.junit.jupiter.api.Test`), `@TempDir Path temporary`, package-private test class, plain
  `assertEquals`/`assertTrue`. Its helpers build a fake assessment that the real grader can compile
  and run; **reuse the same fixture code** (copied below) so this plan does not invent new fixtures:
  ```java
  // From JavaGradingServiceTest (lines ~518–539): one tester per question that prints the student's score().
  private Path createTesters() throws IOException {
      Path testersFolder = temporary.resolve("testers");
      for (String testerFile : AssessmentDefinition.testerFiles()) {
          String question = testerFile.replace("Tester.java", "");
          write("testers/" + testerFile, "public class " + question + "Tester extends " + question
                  + " { public static void main(String[] args) { System.out.println(score()); } }");
      }
      return testersFolder;
  }

  private void createSubmission(Path batch, String username, double score) throws IOException {
      AssessmentFixtures.writeDependencies(batch.resolve(username));
      for (var entry : AssessmentDefinition.sourceFiles().entrySet()) {
          String type = entry.getKey().replace(".java", "");
          Path file = batch.resolve(username).resolve(entry.getValue()).resolve(entry.getKey());
          Files.createDirectories(file.getParent());
          String expression = type.equals("Q3") ? "ShapeComparator.score()" : Double.toString(score);
          Files.writeString(file, "/* Name: " + username + " Student\n * Email ID: " + username + "@smu.edu.sg */\n"
                  + "public class " + type + " { public static double score() { return " + expression + "; } }");
      }
  }
  ```
  `grading.AssessmentFixtures` is a public test helper (`src/test/java/grading/AssessmentFixtures.java`).
  With these fixtures each of the 5 questions scores `score`, so a student created with `score` 1.0
  totals **5.0**, and with 2.0 totals **10.0**.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| One test class | `./mvnw -q -B test -Dtest=WebApplicationTest -Dsurefire.failIfNoSpecifiedTests=false` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |
| Count tests | `cat target/surefire-reports/*.txt \| grep 'Tests run'` | no `Failures: [1-9]` / `Errors: [1-9]` |

JDK 21 or later is required (`pom.xml` sets `maven.compiler.release` 21).

## Scope

**In scope** (the only files you should create or modify):
- `src/main/java/web/WebApplication.java` — only the `start` method and two new members (step 1).
- `src/test/java/web/WebApplicationTest.java` (create)

**Out of scope** (do NOT touch):
- Any other method of `WebApplication` — behaviour must stay exactly as it is; this plan only *pins*
  it. If a test reveals a bug, write the test to expect today's behaviour, mark it with a
  `// TODO plan-003?` comment only if it matches plan 003's topic, and report it — do not fix it.
- `src/main/resources/web/index.html`, `HttpResponses.java`, `UploadedFolder.java`, everything under
  `src/main/java/grading/`.

## Git workflow

- Branch: `advisor/002-web-layer-tests`.
- Commit per step; plain sentence messages, e.g. `Let tests start the web server on a free port`,
  `Add end-to-end tests for the web routes`.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Let tests start the server on a free port and stop it

In `WebApplication.java`, replace the `start` method with the version below and add the two fields.
`main` keeps working unchanged (it ignores the returned port).

```java
private HttpServer server;
private ExecutorService requestThreads;

/**
 * Starts the web server and returns the port it listens on. Pass 0 to let the system choose a free
 * port, as the tests do. Only this computer can connect to it.
 */
public int start(int port) throws IOException {
    server = HttpServer.create(new InetSocketAddress(HOST, port), 0);
    // A thread pool, so a slow upload does not block the progress updates.
    requestThreads = Executors.newCachedThreadPool();
    server.setExecutor(requestThreads);
    server.createContext("/", this::route);
    server.start();
    return server.getAddress().getPort();
}

/** Stops the web server. Used by the tests; the application itself runs until the process ends. */
public void stop() {
    if (server != null) {
        server.stop(0);
        requestThreads.shutdownNow();
    }
}
```

Add `import java.util.concurrent.ExecutorService;` next to the existing
`import java.util.concurrent.Executors;`. Put the two fields with the other instance fields (after
`private final Path uploads;` is a good place).

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.
**Verify**: `./mvnw -q -B clean verify` → exit 0 (all existing tests still pass).

### Step 2: Create the test class with its helpers

Create `src/test/java/web/WebApplicationTest.java`, package `web`. It needs:

- Fields: `@TempDir Path temporary;`, `private WebApplication app;`, `private String base;`,
  `private final HttpClient http = HttpClient.newHttpClient();`
- `@BeforeEach void startServer()`: `app = new WebApplication(); base = "http://127.0.0.1:" + app.start(0);`
- `@AfterEach void stopServer()`: `app.stop();`
- Helpers (write them; keep each small):
  - `HttpResponse<String> get(String path)` and `HttpResponse<String> post(String path, byte[] body)`
    using `HttpRequest.newBuilder(URI.create(base + path))` and `BodyHandlers.ofString()`.
  - `HttpResponse<byte[]> getBytes(String path)` for the PDF/ZIP downloads (`BodyHandlers.ofByteArray()`).
  - `byte[] zipFolder(Path folder)` — walks `folder` and writes every regular file into a ZIP in
    memory, with entry names `folder.getFileName() + "/" + relativePath` using `/` separators (this is
    what the browser page sends). Use `java.util.zip.ZipOutputStream` over a `ByteArrayOutputStream`.
  - `createTesters()` and `createSubmission(Path batch, String username, double score)` — copy them
    from "Current state" above, plus the small `write(String relativePath, String content)` helper
    they use (creates parent folders under `temporary`, then `Files.writeString`).
  - `String waitForGradingToFinish()` — polls `GET /api/progress` every 250 ms until the body contains
    both `"running":false` and `"reportsReady":true`, and returns that body. Fail the test
    (`fail("grading did not finish: " + body)`) after 120 seconds.
  - `double totalOf(String progressJson, String username)` — use the regex
    `"\"username\":\"" + Pattern.quote(username) + "\"[^\\]]*?\"total\":([0-9.]+)"` and return
    `Double.parseDouble(matcher.group(1))`. It works because nothing between a student's `username`
    and `total` contains `]` (the nested `"scores":{...}` object has no brackets), while the next
    student's fields come after an `"issues":[...]` array. Fail the test if there is no match.
  - `String statusOf(String progressJson, String username)` — regex `"username":"<name>","status":"([a-z-]+)"`.

**Verify**: `./mvnw -q -B test -Dtest=WebApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
→ exit 0 (the class compiles; with no tests yet, surefire reports 0 tests or "No tests to run").

### Step 3: Add the tests

Add these test methods (names are suggestions; keep the intent):

1. `servesThePageAndItsBundledLibraries` — `GET /` → 200 and body contains
   `CS102 Auto Grading System`; `GET /lib/vue.global.prod.js` → 200; `GET /lib/nope.js` → 404.
2. `rejectsAnUploadThatIsNotAZip` — `POST /api/testers` with body `"garbage".getBytes()` → 400 and
   body contains `not a ZIP file`. Same for `POST /api/submissions`.
3. `refusesToGradeBeforeTestersAreUploaded` — `POST /api/grade` → 400, body contains
   `Upload valid tester files first.`
4. `offersNoDownloadsBeforeGrading` — `GET /api/reports/zip` → 404;
   `GET /api/scoresheet/download` → 404 with `Upload a score sheet first.`
5. `gradesABatchThenRegradesOneStudent` (the only slow test, ~10–30 s):
   - Build testers with `createTesters()`; batch folder `temporary.resolve("batch")` with
     `createSubmission(batch, "alice", 1.0)` and `createSubmission(batch, "bob", 2.0)`.
   - `POST /api/testers` with `zipFolder(testers)` → 200, body contains `"valid":true`.
   - `POST /api/submissions` with `zipFolder(batch)` → 200, body equals
     `{"total":2,"ready":2,"skipped":0}`.
   - `POST /api/grade` → 202. `String progress = waitForGradingToFinish();`
   - Assert `totalOf(progress, "alice") == 5.0` and `totalOf(progress, "bob") == 10.0`.
   - `getBytes("/api/reports/student?username=alice")` → 200, header `content-type` starts with
     `application/pdf`, body starts with the bytes `%PDF`.
   - `getBytes("/api/reports/zip")` → 200, `content-type` `application/zip`.
   - `POST /api/grade/student?username=alice` (empty body) → 202; wait again; assert bob's total is
     still 10.0 and alice's is 5.0.
   - `POST /api/grade/student?username=nobody` → 404, body contains `Unknown student.`

**Verify**: `./mvnw -q -B test -Dtest=WebApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
→ exit 0; `cat target/surefire-reports/web.WebApplicationTest.txt` → `Tests run: 5, Failures: 0, Errors: 0`.

### Step 4: Full suite

**Verify**: `./mvnw -q -B clean verify` → exit 0, and every line of
`cat target/surefire-reports/*.txt | grep 'Tests run'` shows `Failures: 0, Errors: 0`.

## Test plan

Covered by step 3: the page and libraries, bad uploads, grading refused without testers, downloads
refused before grading, the full upload → grade → reports → regrade-one flow, and regrading an unknown
student. Pattern: `src/test/java/grading/execution/JavaGradingServiceTest.java` (fixtures and style)
and `src/test/java/web/UploadedFolderTest.java` (same package, `@TempDir`).

## Done criteria

- [ ] `WebApplication.start(int)` returns the bound port and `WebApplication.stop()` exists
- [ ] `src/test/java/web/WebApplicationTest.java` exists with 5 passing tests
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `git status --short` shows only `WebApplication.java` (modified) and `WebApplicationTest.java` (new)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:

- The route table or the `start`/`main` code differs from the excerpts (the file has drifted).
- `POST /api/submissions` for the alice/bob batch does not return `"ready":2` — the fixtures no longer
  match the configured assessment (`src/main/resources/assessment.properties`); do not edit fixtures
  to force it.
- The totals are not 5.0 and 10.0. Report the observed progress JSON instead of changing the
  expected numbers.
- The slow test takes over 120 seconds or is flaky across two runs.
- Making a test pass seems to require changing any `WebApplication` method other than `start`/`stop`.

## Maintenance notes

- Plan 004 moves the grading state out of `WebApplication` into a new class; these tests must keep
  passing unchanged through that refactor — that is their purpose.
- Plan 003 adds a test to this class for "uploads are refused while grading runs".
- The test writes PDF reports into the repo's `grading-reports/` folder (ignored by Git). If that ever
  needs to go to a temp folder, it requires making `reports.folder` overridable per test — deferred,
  because `GradingSettings.current()` is loaded once per JVM.
