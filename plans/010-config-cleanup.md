# Plan 010: Remove the web-only `ui.port` setting and externalize the Java release used for student code

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`. Do **not** commit.
>
> **Drift check (run first)**:
> `git diff --stat e820cd4..HEAD -- src/main/java/grading/config src/main/java/grading/execution src/main/resources/grading.properties src/test/java/grading`

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (it edits one line of `MainViewController.useReportsFolder`; if plan 012 already ran, that method lives in the same file and is unchanged)
- **Category**: tech-debt
- **Planned at**: commit `e820cd4`, 2026-10-31

## Why this matters

The rubric's top band requires "All configuration parameters are externalized". Two things fall short. First, `grading.properties` still has `ui.port=8080` with the comment "Port of the web page", left over from a web UI the team removed. A marker reading the config would be confused, and the value is still loaded, stored and copied around. Second, the Java language level used to compile student code (`--release 21`) is hard-coded. That's an assessment parameter: a course using Java 17 couldn't grade without changing code.

## Current state

- `src/main/resources/grading.properties:25-26`:

```properties
# Port of the web page, opened at http://127.0.0.1:<port>. Change it if another program uses it.
ui.port=8080
```

- `src/main/java/grading/config/GradingSettings.java`: record component `int port` (11th of 15; Javadoc `@param port port of the web page on this computer` at :20). Loaded at :62 with `Math.toIntExact(file.positiveNumber("ui.port")),`.
- `src/test/java/grading/config/GradingSettingsTest.java:40`: `assertEquals(8080, settings.port());`
- `src/main/java/ui/MainViewController.java:421-425` (`useReportsFolder`) copies every component, including `current.port()`, into a new `GradingSettings`.
- `src/main/java/grading/execution/TesterCompiler.java:46-47`:

```java
                    GradingProcesses.jdkTool("javac"), "-J" + limits.maxHeapOption(),
                    "-encoding", "UTF-8", "--release", "21",
```

- `src/main/java/grading/execution/GradingLimits.java`: `record GradingLimits(Duration compileTimeout, Duration executionTimeout, int maxMemoryMegabytes, int parallelStudents)` with a validating compact constructor and `static GradingLimits of(GradingSettings settings)`. It's constructed directly in `src/test/java/grading/execution/JavaGradingServiceTest.java:184` and `:523`.
- `src/main/java/grading/execution/GradingProcesses.java:19` requires "A full JDK 21 or newer".

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Compile all | `./mvnw -q -B test-compile` | exit 0 |
| All tests | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope:** `grading.properties`, `GradingSettings.java`, `GradingSettingsTest.java`, `GradingLimits.java`, `TesterCompiler.java`, `JavaGradingServiceTest.java` (the 2 constructor calls), `MainViewController.java` (the `useReportsFolder` copy only).

**Out of scope:** `assessment.properties`; any other setting.

## Steps

### Step 1: Swap `port` for `javaRelease`

- In `GradingSettings`, rename the component `int port` → `int javaRelease`, in the **same position**, so the argument order elsewhere is unchanged. Update the Javadoc: `@param javaRelease the Java version student code is compiled for (javac --release)`.
- Load it with `Math.toIntExact(file.positiveNumber("grading.java-release"))`.
- In `grading.properties`, delete the two `ui.port` lines. Add these under the time limits:

```properties
# Java version that student and tester code is compiled for (javac --release). It must not be newer
# than the JDK running the grader.
grading.java-release=21
```

- `GradingSettingsTest:40` becomes `assertEquals(21, settings.javaRelease());`.
- `MainViewController.useReportsFolder` becomes `current.javaRelease()` instead of `current.port()`.

**Verify**: `grep -rn "ui.port\|\.port()" src` → no matches; `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 2: Carry it through `GradingLimits` to `javac`

- Add `int javaRelease` as the last component of `GradingLimits`. Validate it in the compact constructor: it must be ≥ 8 and ≤ `Runtime.version().feature()`. Otherwise throw `IllegalArgumentException("grading.java-release must be between 8 and " + Runtime.version().feature() + ".")`. Name both bounds as constants (`MIN_JAVA_RELEASE = 8`).
- `GradingLimits.of` passes `settings.javaRelease()`.
- In `TesterCompiler`, replace `"--release", "21"` with `"--release", String.valueOf(limits.javaRelease())`.
- Update the two `new GradingLimits(...)` calls in `JavaGradingServiceTest` to pass `21` as the new last argument.

**Verify**: `grep -n '"21"' src/main/java/grading/execution/TesterCompiler.java` → no match; `./mvnw -q -B clean verify` → exit 0.

### Step 3: Test the validation

In `JavaGradingServiceTest` (or a new `GradingLimitsTest` in the same package), add `releaseNewerThanTheRunningJdkIsRejected`. It calls `new GradingLimits(Duration.ofSeconds(1), Duration.ofSeconds(1), 64, 1, Runtime.version().feature() + 1)` and expects `IllegalArgumentException`.

**Verify**: `./mvnw -q -B clean verify` → exit 0, with 1 more test.

## Done criteria

- [ ] `grep -rn "ui.port\|port()" src` → no matches
- [ ] `grep -rn '"--release", "21"' src` → no matches
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `plans/README.md` row updated

## STOP conditions

- Anything other than the files listed reads `port()` or `ui.port`.

## Maintenance notes

- A user's `config/grading.properties` override that still contains `ui.port` is harmless: unknown keys are ignored. One that lacks `grading.java-release` still works, because missing keys fall back to the packaged defaults (see `ConfigFile.load`).
