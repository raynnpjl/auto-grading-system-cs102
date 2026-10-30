# Plan 018: Make the grader pass its own style check (apart from the documented line length)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**: run this plan **after** every other code plan
> (008–017), because they add code. Re-run the style command below first and
> work from its live output; the list in "Current state" is from `e820cd4`.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans 008–017 (runs last among code plans)
- **Category**: tech-debt
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

The app grades students against the CS102 Java Coding Style guide using `config/checkstyle.xml`. Run against **the app's own source**, that config still reports 15 structural warnings: 12 "declared without a value", 2 nested `if`s and 1 `while (true)`. It also reports about 1,700 lines over 80 columns. A marker in the Q&A may well ask "does your code pass your own style checker?" The team decided to use 120 columns (plan 006, option A), so line length is a documented exception. Everything else should be zero, and there should be a script to prove it.

## Current state

- Style command. It needs compiled classes, because the config uses the project's own custom checks in `grading.style.checks`:

```sh
./mvnw -q -B -DskipTests compile
java -cp "lib/checkstyle-14.1.0-all.jar:target/classes" com.puppycrawl.tools.checkstyle.Main -c config/checkstyle.xml src/main/java | grep -E "^\[(WARN|ERROR)" | grep -v "\[line-length\]"
```

- Hits at `e820cd4` (path: line → rule):
  - `grading/reporting/AttemptReportWriter.java:58,59` → initialise-at-declaration (`Path folder; String attemptId;` assigned inside a `try`)
  - `grading/reporting/ScoreSheet.java:92` `records`; `grading/reporting/ReportArchive.java:33` `files`; `grading/similarity/GreedyStringTiling.java:37` `longest` (a do/while); `grading/similarity/JavaTokenizer.java:101` `type` (if/else chain); `grading/style/checks/LoopEdgeConditionalCheck.java:66` `other`; `grading/style/StyleChecker.java:132` `lines`; `grading/execution/TesterTestCases.java:214` `block` → initialise-at-declaration
  - `grading/common/FileTrees.java:32` `read`; `grading/execution/BoundedProcessRunner.java:152` `count`; `grading/submission/ZipExtractor.java:36` `entry` → initialise-at-declaration on the idiomatic `while ((x = …) != …)` read loop
  - `grading/feedback/SourceFile.java:154` and `grading/execution/TesterTestCases.java:165` → nested-if-depth
  - `grading/execution/BoundedProcessRunner.java:49` → infinite-loop-exit (`while (true) { … break; }`)
- `BoundedProcessRunner.java:47-65`, the loop to restructure:

```java
            while (true) {
                rememberDescendants(process, descendants);
                if (stdout.overflow || stderr.overflow) {
                    status = ProcessResult.Status.OUTPUT_LIMIT_EXCEEDED;
                    break;
                }
                if (!process.isAlive()) {
                    break;
                }
                long remaining = timeout.toNanos() - (System.nanoTime() - started);
                if (remaining <= 0) {
                    status = ProcessResult.Status.TIMED_OUT;
                    break;
                }
                process.waitFor(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS)),
                        TimeUnit.NANOSECONDS);
            }
```

- `SourceFile.withoutStrings` (`:143-167`) has `if (quote != 0) { if (character == quote) {…} else {…} } else {…}`.
- Tests exist for both: `BoundedProcessRunner` is exercised by `JavaGradingServiceTest` (timeouts, output limits). `SourceFile` is exercised by `FeedbackGeneratorTest`.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Style | `./style-check.sh` (created in step 1) | prints `0 style problems (line length excluded)`, exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `style-check.sh` (create, repo root, executable), and the Java files listed above.
**Out of scope:** `config/checkstyle.xml`. Don't weaken the rules or add suppressions. No behaviour change.

## Steps

### Step 1: The script

Create `style-check.sh`, with a style matching `compile.sh` (`#!/bin/sh`, `set -e`, `cd "$(dirname "$0")"`). It should:
- compile with `./mvnw -q -B -DskipTests compile`;
- run the style command;
- filter out `[line-length]`;
- print the remaining lines;
- end with `N style problems (line length excluded)`, exiting 1 when N > 0.

Add a comment saying line length is excluded because the team uses a 120-column limit. Run `chmod +x style-check.sh`.

**Verify**: `./style-check.sh` → lists the current hits and exits 1.

### Step 2: The loop and the nested ifs

- `BoundedProcessRunner`: turn the `while (true)` into a loop whose condition is the stopping test. One correct shape: compute `status` with a small private method `ProcessResult.Status poll(...)` that returns `null` while the process should keep running, and loop `while (status == null)`. Keep the order of the checks exactly: output overflow, then process exit, then timeout.
- `SourceFile.withoutStrings` and `TesterTestCases:165`: flatten with `else if` or extract a small private method, keeping the logic identical.

**Verify**: `./mvnw -q -B clean verify` → exit 0.

### Step 3: Declarations

For each initialise-at-declaration hit, either:
- (a) restructure so the variable is assigned where it's declared (extract a method that returns the value, as `folder`/`attemptId` can be returned from a small helper); or
- (b) for the three read-loop variables, rewrite to `for (int read = in.read(buffer); read > 0; read = in.read(buffer)) { … }`. Use `!= -1` or `!= null` where the original loop compares against those.

**Verify**: `./style-check.sh` → `0 style problems (line length excluded)`, exit 0; `./mvnw -q -B clean verify` → exit 0.

## Done criteria

- [ ] `./style-check.sh` exits 0
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `git diff --stat -- config/` → empty
- [ ] `plans/README.md` row updated

## STOP conditions

- A `JavaGradingServiceTest` timeout or output-limit test fails after step 2. Revert that step and report it; don't adjust the tests.
- A fix would need changing `config/checkstyle.xml`.

## Maintenance notes

- Run `./style-check.sh` before each submission and before the demo. It's also a good slide: "our grader's own code passes its style checks".
