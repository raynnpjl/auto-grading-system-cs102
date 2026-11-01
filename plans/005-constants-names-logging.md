# Plan 005: Name the remaining magic numbers and one-letter variables, and stop printing to `System.out`

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: plans 001–003 must be DONE. Line numbers below
> were taken from `origin/for-submission` @ `0379d5f`. Re-locate each by its
> code text if they moved.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/003-plagiarism-and-feedback-views.md (so it runs after the UI files settle)
- **Category**: tech-debt
- **Planned at**: commit `e43d2e2`, 2026-10-30

## Why this matters

The top code band asks for "Constants are used instead of littering values all over" and "follows Java coding convention consistently". The project's own `config/checkstyle.xml` (the CS102 style rules the grader enforces on students) still reports, on the restored back end, **16 magic numbers, 12 one-letter names and 12 "declared without a value" variables**. Markers may well ask, "does your grader pass its own style check?" `GradingSession` also prints the grading log with `System.out.println`, while the rest of the code uses `java.util.logging`.

## Current state

Verified hits (back end as restored by plan 001):

- Magic numbers:
  - `grading/reporting/ScoreSheet.java:173-174,188`: ZIP signature `0x50 0x4B 0x03 0x04`, OLE signature `0xD0 0xCF 0x11 0xE0`, byte mask `0xFF`. Use `private static final byte[] ZIP_SIGNATURE`, `OLE2_SIGNATURE` and `int BYTE_MASK`.
  - `grading/reporting/PdfReportRenderer.java:91,93`: markdown heading prefix lengths `4`/`3` (for `"### "`/`"## "`). Use `"### ".length()` via named constants.
  - `grading/similarity/JavaTokenizer.java:129,137`, `grading/feedback/JavacError.java:53` and `grading/execution/TesterTestCases.java:150-151`: each is a literal `3`. Read each site and name it after what it means (e.g. a text-block delimiter length `"\"\"\"".length()`).
- One-letter names: `PdfTable.java:67 x`, `PdfPages.java:33 y` (field), `:75,82,98 x` (params), `:114 x`, `GreedyStringTiling.java:32-33 a,b`, `JavaTokenizer.java:98,131,152 c`, `SourceFile.java:147 c`. Rename to `left`/`top`/`first`/`second`/`character` or similar, matching meaning.
- `initialise-at-declaration` (12, e.g. `AttemptReportWriter.java:58-59`, `ScoreSheet.java:88`, `ZipExtractor.java:36`): fix only where a value can be assigned at declaration without restructuring. **Skip** the ones that are loop-assigned reads (`FileTrees.java:32 read`, `ZipExtractor.java:36 entry`, `BoundedProcessRunner.java:152 count`), because the idiomatic `while ((read = in.read(buf)) > 0)` form needs them.
- Logging: `grading/session/GradingSession.java:263`, `System.out.println(message);` in the `log` consumer of `runGrading`. Replace it with `LOG.info(message)`; the class already has `private static final Logger LOG`.
- UI (after plans 001–003): `ui/AutoGradingApplication.java` window sizes `1320, 920, 1060, 840` become `PREFERRED_WIDTH`, `PREFERRED_HEIGHT`, `MIN_WIDTH`, `MIN_HEIGHT`; `ui/IdentityDialog.java:58,81` literal `4` becomes a named constant.

How to list them yourself (needs compiled classes, because the config uses the project's custom checks):

```sh
./mvnw -q -B -DskipTests compile
java -cp "lib/checkstyle-14.1.0-all.jar:target/classes" com.puppycrawl.tools.checkstyle.Main -c config/checkstyle.xml src/main/java | grep -E "magic-numbers|variable-names|initialise-at-declaration"
```

## Scope

**In scope:** only the files and lines listed above.
**Out of scope:** `line-length` hits (plan 006); any behaviour change; renaming public API. Do **not** commit.

## Steps

### Step 1: Constants for the magic numbers

Fix each magic-number site above.

**Verify**: running the Checkstyle command with `| grep -c magic-numbers` → `0`.

### Step 2: Rename one-letter variables

Fix each one-letter name above.

**Verify**: running the Checkstyle command with `| grep -c variable-names` → `0`.

### Step 3: Initialise at declaration where trivial; logging

Fix the trivial `initialise-at-declaration` sites and switch `GradingSession` to `LOG.info`.

**Verify**: `grep -rn "System\.out\|System\.err" src/main/java | grep -v TesterTestCases` → no matches. (`TesterTestCases` generates Java source that contains `System.out`, which is correct.)

### Step 4: Full test run

**Verify**: `./mvnw -q -B clean verify` → exit 0, 0 failures.

## Done criteria

- [ ] Checkstyle: 0 `magic-numbers`, 0 `variable-names`
- [ ] No `System.out/err` outside `TesterTestCases`
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `plans/README.md` row updated

## STOP conditions

- A rename touches a `public` method signature or record component. Report it and don't rename.
- Tests that compare log output to stdout fail after step 3.

## Maintenance notes

- Once the codebase has 0 hits, add the Checkstyle command above to the team's pre-submission checklist so new code stays clean.
