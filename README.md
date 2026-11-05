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

The Auto Grading System window opens. `compile.sh` compiles every source file into
`target/classes` (the project uses the Maven layout instead of a `classes` folder), and `run.sh`
starts the application from this folder, so the files in `config/` are found. Both use the Maven
Wrapper; `./mvnw clean verify` also runs the tests, and `./style-check.sh` checks this project's
own code against the same CS102 style rules it applies to students.

On Windows Command Prompt, from this folder:

```bat
mvnw.cmd -DskipTests compile
mvnw.cmd javafx:run
```

`mvnw.cmd clean verify` runs the tests.

## Grading workflow

The next step is always the teal button, and **How to grade** (F1) explains each step in the
application.

1. **Tester files — Import folder:** select the `Tester-Files` folder. Once it is accepted, the
   panel shows a one-line summary; **Show files** lists the files and **Change folder** picks
   another one.
2. **Student submissions — Import folder:** select the folder of student ZIPs or folders. A
   progress bar shows each submission as it is prepared. Submissions are validated and corrected
   where possible: a forgotten rename, a missing username folder, a misplaced file or a missing
   name in a Java header is fixed in a copy, and the original is never changed. A missing or
   unusable source file costs only its own question (it scores 0). A student whose identity cannot
   be confirmed is skipped until you confirm it in **Resolve issues**, which opens by itself after
   the import; everyone else can still be graded.
3. **Run grading:** each question's tester is compiled and run against each student, several
   students at a time. The table fills in as students finish, and **Cancel** (Esc) stops the run
   while keeping the marks from earlier runs. If a tester runs longer than the time limit, the test
   case that did not finish is replaced by a failure and the tester is re-run, so the remaining test
   cases are still graded. Each student's code is then checked against the CS102 Java Coding Style
   guide and against the other students' answers (see *Similar answers*), and the run's reports are
   written (see *Reports*).
4. **Review the results:** the tiles show how many students were graded, the class average, how
   many submissions were fixed automatically, and how many need review. Students that need review
   are listed first with the reason. Selecting a student shows their marks, where each mark was
   lost, folder and file problems, what was fixed automatically, and similar answers, with
   **Download PDF report** to save their report. **Regrade selected** grades one student again.
5. **Import score sheet** (Ctrl/Cmd+I): select the score sheet CSV exported from the LMS (for
   example `IS442-ScoreSheet.csv`). It can be imported before or after grading. A sheet opened and
   saved in Numbers or Excel may no longer be a CSV file even if its name ends in `.csv`; export it
   as CSV instead. If a sheet cannot be imported, the reason is shown in the Activity log.
6. **Save filled sheet** (Ctrl/Cmd+Shift+S): review the preview, which also lists students who
   need review or were skipped, then choose where to save. Each student's final mark (the tester
   marks minus any configured penalties) is written into the `Calculated Final Grade Numerator`
   column, matched by `Username`, in a copy named `<sheet>-graded.csv` by default.

The imported sheet itself is never changed, and the copy cannot be saved over it. In the copy only
the numerator cells of matched students change; every other value, the column order and the line
endings are kept as they were. Students in the sheet without a graded submission are left
unchanged.

Before importing new submissions over graded results, and before closing during a run, the
application asks for confirmation.

## Configuration

Settings are not written into the code. Two files are packaged with the application, in
`src/main/resources`:

- `grading.properties`: time limits, the Java release students are compiled for, how many
  students are graded at the same time (4 by default; lower it on machines with little memory),
  memory, output and file-size limits, the email domain used to complete missing header emails,
  the style files and penalties, the similarity thresholds, and the reports folder.
  Corrected copies of the submissions are kept in a temporary work folder. They are deleted when
  submissions are re-imported and when the application closes; folders left by a crashed session
  are removed at the next start.
- `assessment.properties`: the assessment being graded — its questions, the folder each belongs
  in, the student source files, and the support classes and data files each tester needs.

To change a value without rebuilding, copy the file into `config/` in the folder the application
is started from (for example `config/grading.properties`) and edit the copy, or pass
`-Dgrading.config=<path>` / `-Dassessment.config=<path>`. Values missing from the copy keep their
packaged defaults. A different assessment is graded by describing it in `assessment.properties`.

## Reports

Every grading run is an *attempt* with its own folder, named after the time it started, under
`grading-reports/` (set by `reports.folder`). Re-grading never overwrites an earlier attempt.

- `summary.pdf`: students needing a manual check listed first with the reasons, every graded
  student, class statistics (marks, each question's pass rate, common style and submission
  problems), and the configuration used: time limits, students graded at the same time, the
  style checker, and the style rules and their weights.
- `students/<username>.pdf`: the student's identity (username, ZIP or folder name, name and email
  from the Java headers), structure problems and every automatic correction (original path to
  corrected path), each question's test cases (passed, failed or timed out, with expected and
  actual output when the tester prints them), explanations of where marks were lost, the score
  breakdown, and the style results.
- `scores.csv`: one row per student with each question's marks, the tester total, style
  violations, the style penalty and the final mark.

Reports are written in plain language as PDF files. A result that is missing or cannot be read
appears as a note in the report; writing reports never stops grading. If a report cannot be laid
out as a PDF, it is saved as a `.txt` file instead and the summary says so.

**Save reports** saves the latest attempt's folder as one ZIP file wherever you choose, and
**Download PDF report** in a student's details saves just their report.

## Similar answers

Each question's answers are compared between every pair of students, leaving out the template's
code. Pairs are screened with winnowing (fingerprints of `similarity.kgram-size` tokens) and the
ones that pass `similarity.screen-threshold` are scored with Greedy String Tiling. Pairs at or
above `similarity.threshold` (80%) are listed in the **Similar answers** tab, and the students are
marked *Needs review* when both answers have at least `similarity.min-tokens` tokens. Marks are
never changed: a person decides.

## Test submissions

`test-submissions/` holds four submissions the team made for cases the six sample submissions do
not cover. Import it as the student submissions folder, together with the course's `Tester-Files`:

- `RenameToYourUsername`: the original resource folder, never renamed, with blank name and email
  headers. Its owner cannot be identified, so it is skipped until confirmed in **Resolve issues**.
- `missing.files.2025`: `Q2b.java` and the whole `Q3` folder are missing. Only those questions
  score 0; the rest is graded.
- `loop.q1b.2025`: `Q1b` never ends for some inputs. The test case that times out counts as
  failed and the other test cases still count. **Cancel** can be tried while it runs.
- `copied.answer.2025`: answers copied from another submission, for trying the **Similar
  answers** tab.

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
`grading.properties` is 0, so marks are unchanged and the PDF reports leave out the penalty row.
Setting a penalty deducts it from the mark written to the score sheet.

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
  - `grading.feedback`: plain-language explanations of lost marks; each `FeedbackRule`
    recognises one kind of problem (compile error, timeout, wrong output, and so on).
  - `grading.similarity`: comparing answers between students.
  - `grading.reporting`: the PDF and CSV reports of each attempt, and filling the score sheet.
  - `grading.style`: style checking with Checkstyle; `grading.style.checks` holds the custom checks.
  - `grading.progress`: each student's live status, which the window shows during a run.
  - `grading.session`: one marker's session: the imported files, grading runs, cancelling and
    regrading, and the temporary work folder.
  - `grading.common`: small helpers shared by the packages.
  - `ui`: the JavaFX view and its controller, with one class per part of the window (results
    table, student details, import panels, status, dialogs) and the wording kept in its own
    classes.
- `src/main/resources`: the settings files, and under `ui` the FXML view and CSS stylesheet.
- `src/test/java`, `src/test/resources`: unit and UI tests.
- `docs/uml`: PlantUML class diagrams of the design, one per package group.
- `target`: generated build output, ignored by Git.

The POM declares JavaFX controls and FXML, the Java compiler plugin, and the JavaFX
launcher plugin. JavaFX graphics is included transitively.

## Libraries

- [OpenJFX](https://openjfx.io/) 21.0.12: the desktop user interface.
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/) 1.14.1: reading the LMS
  score sheet, including quoted values.
- [JUnit](https://junit.org/junit5/) 5.14.0 (tests only).
- [Apache PDFBox](https://pdfbox.apache.org/) 3.0.8: writing the grading reports as PDF files.
- [Checkstyle](https://checkstyle.org/) 14.1.0: style checking. The self-contained release jar is
  kept in `lib/checkstyle-14.1.0-all.jar` (SHA-256
  `51e2bc7fed1bb56808aa39045f655a316194997acd24bac5195253dcf342b380`) and referenced from
  `pom.xml`, so it is never downloaded during the build.

Build references: [Maven directory layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html),
[Maven Wrapper](https://maven.apache.org/tools/wrapper/),
and [OpenJFX setup](https://openjfx.io/openjfx-docs/).
