# CS102 Auto Grading System

## Requirements

- JDK 21
- A graphical desktop session to launch the application.
- Internet access for the first build to download Maven and its dependencies.

The Maven Wrapper pins Maven 3.9.16, so a separate Maven installation is not needed.

## Build and run

Run these commands from this directory on macOS or Linux:

```sh
./compile.sh
./run.sh
```

`compile.sh` compiles every source file into `target/classes` and `run.sh` starts the
application from this folder, so the files in `config/` are found. Both use the Maven Wrapper;
`./mvnw clean verify` also runs the tests.

On Windows Command Prompt:

```bat
mvnw.cmd clean verify
mvnw.cmd javafx:run
```

## Grading workflow

1. **Tester files — Import folder:** select the `Tester-Files` folder.
2. **Student submissions — Import folder:** select the folder of student ZIPs or folders.
   Submissions are validated and normalized. A missing or unusable source file costs only its own
   question (it scores 0). A student whose identity cannot be confirmed is skipped until you select
   **Resolve issues**; everyone else can still be graded.
3. **Run grading:** each question's tester is compiled and run against each student. The console
   lists every student's per-question scores and total, and the students that were skipped. If a
   tester runs longer than the time limit, the test case that did not finish is replaced by a
   failure and the tester is re-run, so the remaining test cases are still graded. Each student's
   code is then checked against the CS102 Java Coding Style guide, and the attempt's reports are
   written (see *Reports* below).
4. **Import score sheet:** select the score sheet CSV exported from the LMS (for example
   `IS442-ScoreSheet.csv`). It can be imported before or after grading. A sheet opened and saved
   in Numbers or Excel may no longer be a CSV file even if its name ends in `.csv`; export it as
   CSV instead. If a sheet cannot be imported, the reason stays at the top of the console and
   Write scores stays unavailable.
5. **Write scores:** review the summary, then confirm. Each student's final mark (the tester marks
   minus any configured penalties) is written into the
   `Calculated Final Grade Numerator` column of the imported file, matched by `Username`.

Writing scores changes only the numerator cells of matched students; every other value, the
column order, and the line endings are kept as they were. Before writing, the application saves a
copy beside the sheet named `<sheet>.backup-<yyyyMMdd-HHmmss>.csv`, and it refuses to write if the
file changed after it was imported. Close the sheet in Excel before writing. Students in the sheet
without a graded submission are left unchanged and listed in the console.

## Configuration

Settings are not written into the code. Two files are packaged with the application:

- `grading.properties`: time limits, how many students are graded at the same time (4 by
  default; lower it on machines with little memory), output and file-size limits, the email
  domain used to complete missing header emails, and the temporary workspace folder.
  Normalized copies in that folder are deleted when submissions are re-imported and when the
  application closes; copies left by a crashed session are removed at the next start.
- `assessment.properties`: the assessment being graded — its questions, the folder each belongs
  in, the student source files, and the support classes and data files each tester needs.

To change a value without rebuilding, copy the file to `config/` in the folder the application
is started from (for example `config/grading.properties`) and edit the copy, or pass
`-Dgrading.config=<path>` / `-Dassessment.config=<path>`. Values missing from the copy keep their
packaged defaults. A different assessment is graded by describing it in `assessment.properties`.

## Reports

Every grading run is an *attempt* with its own folder, named after the time it started, under
`grading-reports/` (set by `reports.folder`). Re-grading never overwrites an earlier attempt.

- `summary.pdf`: students needing a manual check listed first with the reasons, the other
  students, class statistics (marks, each question's pass rate, common style and submission
  problems), and the configuration used: time limits, marks awarded per test case, penalties,
  style rules and weights, and fingerprints of the style files, so results can be reproduced.
- `students/<username>.pdf`: the student's identity (username, ZIP or folder name, name and email
  from the Java headers), structure problems and every automatic correction (original path to
  corrected path), each question's test cases (passed, failed or timed out, with expected and
  actual output when the tester prints them), the score breakdown, and the style results.

Reports are written in plain language as PDF files. A result that is missing or cannot be read
appears as a note in the report; writing reports never stops grading. If a report cannot be laid
out as a PDF, it is saved as a `.txt` file instead and the summary says so.

**Save reports** saves the latest attempt's folder as one ZIP file wherever you choose.

## Style scoring

Each student's source files are checked with [Checkstyle](https://checkstyle.org/) against the
CS102 Java Coding Style guide. The files are checked as the student submitted them, before any
header repair, and lines kept unchanged from the instructor's template (`config/template`) are
not checked. A file that cannot be analysed, for example because it does not parse, is recorded
as "could not analyse" and the run continues.

- `config/checkstyle.xml`: the checks, each with an id and a comment naming the guide section it
  enforces. Three checks are written for this project in `grading.style.checks`: break or
  continue in an infinite loop (section 10), declaring a variable without a value (section 3),
  and, as a heuristic marked for a person to confirm, first/last-iteration conditionals in loops
  (section 8).
- `config/style-rules.properties`: each check's guide section, plain-language rule and weight,
  and the guide rules that are not checked automatically, which every report lists.

The style score starts at `style.max-score` (10) and loses each violation's weight; a rule
broken more than once on the same line counts once. Style is reported only: `penalty.*` in
`grading.properties` is 0, so marks are unchanged. Setting a penalty deducts it from the mark
written to the score sheet.

## Maven layout

- `src/main/java`: application source, organised by responsibility:
  - `grading.config`: loading the settings files.
  - `grading.assessment`: the assessment's questions and the files each needs.
  - `grading.submission`: opening, checking and normalizing submissions. `SubmissionSource`
    has one subclass per kind of submission (ZIP or folder), and each `SubmissionCheck`
    implementation reports one kind of anomaly; new checks can be added without changing
    the preparation service.
  - `grading.tester`: validation of the tester folder.
  - `grading.execution`: compilation and time-limited execution of testers.
  - `grading.scoring`: per-question and per-student scores of a grading run.
  - `grading.reporting`: the validation and results reports, attempt reports, and score sheet updates.
  - `grading.style`: style checking with Checkstyle; `grading.style.checks` holds the custom checks.
  - `grading.common`: small helpers shared by the packages.
  - `ui`: the JavaFX view, its controller and dialogs.
- `src/main/resources`: the settings files, and under `ui` the FXML view and CSS stylesheet.
- `src/test/java`, `src/test/resources`: unit and UI tests.
- `target`: generated build output, ignored by Git.

The POM declares JavaFX controls and FXML, the Java compiler plugin, and the JavaFX
launcher plugin. JavaFX graphics is included transitively.

## Libraries

- [OpenJFX](https://openjfx.io/) 21: the desktop user interface.
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/) 1.14: reading the LMS
  score sheet, including quoted values.
- [JUnit](https://junit.org/junit5/) 5 (tests only).
- [Apache PDFBox](https://pdfbox.apache.org/) 3.0.8: writing the grading reports as PDF files.
- [Checkstyle](https://checkstyle.org/) 14.1.0: style checking. The self-contained release jar is
  kept in `lib/checkstyle-14.1.0-all.jar` (SHA-256
  `51e2bc7fed1bb56808aa39045f655a316194997acd24bac5195253dcf342b380`) and referenced from
  `pom.xml`, so it is never downloaded during the build.

Build references: [Maven directory layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html),
[Maven Wrapper](https://maven.apache.org/tools/wrapper/),
and [OpenJFX setup](https://openjfx.io/openjfx-docs/).