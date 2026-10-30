# Plan 004: Show a readable dialog, not a stack trace, when a settings file is wrong

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: plan 001 must be DONE. Then
> `sed -n 40,50p src/main/java/grading/config/GradingSettings.java` must show
> `private static final GradingSettings CURRENT = load(ConfigFile.load("grading"));`.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/001-restore-backend-onto-javafx.md
- **Category**: bug
- **Planned at**: commit `e43d2e2`, 2026-10-30

## Why this matters

Settings are loaded in static initialisers: `GradingSettings.CURRENT` and `AssessmentDefinition.CONFIG`/`QUESTIONS`. `MainViewController` reads them in a field initialiser while FXML is loading. If a marker's `config/grading.properties` or `config/assessment.properties` has a typo, the class fails to initialise with `ExceptionInInitializerError` and JavaFX prints a stack trace without opening a window. The rubric's 15–19 application band names "stack trace, crashes", and there's a penalty of up to 10% for usability issues, input validation included. `ConfigFile` already produces clear messages such as `Setting grading.parallel-students in grading.properties must be a number of one or more`. They just never reach the user.

## Current state

- `src/main/java/grading/config/ConfigFile.java`: `load(name)` throws `IllegalStateException`/`UncheckedIOException` with marker-readable messages. `text(key)`, `positiveNumber(key)` and similar do the same.
- `src/main/java/grading/config/GradingSettings.java:~43`: `private static final GradingSettings CURRENT = load(ConfigFile.load("grading"));`
- `src/main/java/grading/assessment/AssessmentDefinition.java:16-20`: `CONFIG = ConfigFile.load("assessment")`, `QUESTIONS = loadQuestions(CONFIG)`, `TEMPLATE_FOLDER = CONFIG.text(...)`.
- `src/main/java/ui/AutoGradingApplication.java` `start(Stage)` loads `/ui/main-view.fxml` directly.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Tests | `./mvnw -q -B clean verify` | exit 0 |
| Manual | `mkdir -p config && printf 'grading.parallel-students=abc\n' > config/grading.properties && ./run.sh` | a dialog, not a stack trace |

## Scope

**In scope:** `src/main/java/ui/AutoGradingApplication.java`, `src/main/java/ui/StartupCheck.java` (create), `src/test/java/ui/StartupCheckTest.java` (create).
**Out of scope:** the static-initialiser design in `grading/**`. Changing it ripples through about 20 call sites; it's recorded as deferred below. Do **not** commit.

## Steps

### Step 1: Force config loading before the window is built

Create `ui/StartupCheck.java` with `static Optional<String> settingsProblem()`. It calls `GradingSettings.current()` and `AssessmentDefinition.QUESTIONS.size()` inside `try`. It catches `ExceptionInInitializerError` (use `getCause()`'s message), `IllegalStateException` and `UncheckedIOException`, and returns `Optional.of(message)`. Otherwise it returns empty. Also catch `NoClassDefFoundError`, which is thrown on the second access after a failed initialiser, and return a generic message: "The settings could not be loaded; see the first error above." This makes it safe to call twice.

In `AutoGradingApplication.start`, call it **before** `FXMLLoader.load`. On a problem, show `Alert(AlertType.ERROR)` with title "Settings problem", header "The grader could not start", and content holding the message plus "Fix the file in config/ (or remove it to use the defaults) and start the application again." Then `Platform.exit()` and return.

**Verify**: `./mvnw -q -B -DskipTests compile` → exit 0.

### Step 2: Test

`StartupCheckTest`: with the default packaged settings, `settingsProblem()` is empty. (Testing a broken file needs a fresh JVM because of the static initialisers. Cover that with the manual check in step 3 rather than adding fork configuration.)

**Verify**: `./mvnw -q -B clean verify` → exit 0.

### Step 3: Manual check

Run the "Manual" command from the table, which runs with a broken override. A dialog appears with the `parallel-students` message and the app exits cleanly. Afterwards, **delete** `config/grading.properties` (`rm config/grading.properties`). Leaving it there would break later runs.

## Done criteria

- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `git status --short config/` shows no new `grading.properties`
- [ ] `plans/README.md` row updated

## STOP conditions

- `config/grading.properties` already exists before step 3. Don't overwrite it; use `-Dgrading.config=/tmp/bad.properties` with `./mvnw -q -B javafx:run` instead.

## Maintenance notes

- Deferred: replace the static `GradingSettings.current()` and `AssessmentDefinition` globals with instances passed in through constructors. That's cleaner OOP, but it's a wide change. Only do it if time allows after plans 001–003.
