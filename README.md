# CS102 Auto Grading System

## Requirements

- JDK 21
- A graphical desktop session to launch the application.
- Internet access for the first build to download Maven and its dependencies.

The Maven Wrapper pins Maven 3.9.16, so a separate Maven installation is not needed.

## Build and run

Run these commands from this directory on macOS or Linux:

```sh
./mvnw clean verify
./mvnw javafx:run
```

On Windows Command Prompt:

```bat
mvnw.cmd clean verify
mvnw.cmd javafx:run
```

## Maven layout

- `src/main/java`: application source.
- `src/main/resources/ui`: the FXML view and CSS stylesheet.
- `src/test/java`: future application tests.
- `src/test/resources`: future test resources.
- `target`: generated build output, ignored by Git.

The POM declares JavaFX controls and FXML, the Java compiler plugin, and the JavaFX
launcher plugin. JavaFX graphics is included transitively. Commons CSV and JUnit
can be added when code or tests need them. No assessment configuration, sample
data, or grading APIs are created at this stage.

Build references: [Maven directory layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html),
[Maven Wrapper](https://maven.apache.org/tools/wrapper/),
and [OpenJFX setup](https://openjfx.io/openjfx-docs/).

## Timeout partial-credit fix

A tester may pass a test and then hang in a later student method call. Previously,
`JavaGradingService.execute()` returned zero immediately for every unsuccessful
execution, discarding completed passes. It now retains one mark per exact
`Passed` stdout line when execution times out, as required by the supplied
one-mark-per-test assessment testers. The status remains `EXECUTION_TIMEOUT` and
the report explains that unfinished tests receive no marks. Completed `Failed`
verdicts are counted separately; unfinished tests are not labelled as completed failures.
Normal runs still use the tester's final numeric score, including fractional scores.
Compilation errors, crashes and output-limit failures retain their existing behavior.

This is a general timeout rule, with no student-name checks or fixed student scores.
No student answers, official testers, support classes or runtime data were edited.
For Xing Yan, Q2a passes the missing-file test, then hangs in test 2 because the
Scanner loop does not advance when the surname is absent. Expected result:
Q1 = 0, Q2 = 1, Q3 = 0, total = 1, with a visible Q2a timeout.
The supplied groundtruth.txt has inconsistent component values for Xing Yan;
the one mark is demonstrated by Q2a's actual tester output, not Q1b.

Scope: the timeout fallback assumes the supplied testers' one-mark `Passed`
protocol. A different assessment with weighted tests needs explicit per-test
weights/checkpoints. Like the existing output parser, this is not protection
against student code forging tester output.

### Updating the compile-execution branch

Only these four files changed from the uploaded project:

- `src/main/java/grading/execution/JavaGradingService.java`
- `src/main/java/grading/execution/QuestionGradingResult.java`
- `src/test/java/grading/execution/JavaGradingServiceTest.java`
- `README.md`

In your existing clone of `raynnpjl/auto-grading-system-cs102`, first run:

```bat
git switch compile-execution
git pull --ff-only origin compile-execution
```

Then copy the four changed files from this project into the matching paths in
your clone. If teammates have changed those files since this ZIP was downloaded,
merge the relevant changes instead of overwriting their work. Run:

```bat
mvnw.cmd clean verify
git diff
git add src/main/java/grading/execution/JavaGradingService.java src/main/java/grading/execution/QuestionGradingResult.java src/test/java/grading/execution/JavaGradingServiceTest.java README.md
git commit -m "Retain completed test credit when execution times out"
git push origin compile-execution
```

AI use: ChatGPT assisted with the timeout fix, regression tests and explanation.
Team members should review the diff and be able to explain the partial-credit rule.

### Verification of this fix (23 September 2026)

- Compiled production and test sources using JDK 21 and ran JUnit directly.
- 72 test cases checked: 71 passed initially; the remaining UI screenshot test
  failed because the direct test runner had no `target` directory. After creating
  that directory, all three tests in its UI test class passed on rerun.
- Ran the actual validation and grading pipeline on all six supplied student ZIPs
  with the original official testers. Verified totals:

| Student | Q1 | Q2 | Q3 | Total |
| --- | ---: | ---: | ---: | ---: |
| chee.teo.2022 | 6 | 6 | 0 | 12 |
| david.2024 | 6 | 4 | 0 | 10 |
| fen.lai.2022 | 1 | 9 | 0 | 10 |
| jing.lim.2021 | 0 | 0 | 0 | 0 |
| ping.lee.2023 | 6 | 10 | 4 | 20 |
| xing.yan.2023 | 0 | 1 | 0 | 1 |

Maven verification could not run offline here because Maven Resources Plugin
3.4.0 was not cached. The POM was not changed. Run `mvnw.cmd clean verify` in your
normal development environment to check the Maven build before pushing.
