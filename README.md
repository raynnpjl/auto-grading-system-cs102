# CS102 Auto Grading System

A web application that grades a whole class of CS102 Java submissions at once. It checks and fixes
each submission's folders and headers, compiles and runs the official tester files with a time
limit, fills in the LMS score sheet, and writes a PDF report for every student.

## Requirements

- JDK 21 (`java` and `javac` on the PATH).
- An internet connection the first time you build: the Maven Wrapper downloads Maven and the
  libraries once. You do not need to install Maven yourself.

## Build and run

This project uses the standard Maven layout, so compiled classes go to `target/classes`.

macOS or Linux:

```sh
./compile.sh
./run.sh
```

Windows: `compile.sh` and `run.sh` are shell scripts, so they run in Git Bash or WSL but not in
Command Prompt or PowerShell. Use the Maven Wrapper commands they contain instead, run from this
folder (the one that holds `pom.xml`):

Command Prompt:

```bat
mvnw.cmd -q -B -DskipTests compile
mvnw.cmd -q -B exec:java
```

PowerShell (it needs the `.\` prefix to run a file in the current folder):

```powershell
.\mvnw.cmd -q -B -DskipTests compile
.\mvnw.cmd -q -B exec:java
```

Git Bash: `./compile.sh` then `./run.sh`, as on macOS.

The first command compiles into `target/classes`; the second starts the application. If
`mvnw.cmd` reports that `JAVA_HOME` is not set, set it to your JDK 21 folder, for example `set JAVA_HOME=C:\Program Files\Java\jdk-21` in Command Prompt.

The grading page opens in your browser at `http://127.0.0.1:8080`. If it does not, open that
address yourself. If port 8080 is busy, the application picks a free port and prints the address.

## How to grade

1. **Tester files:** choose the `Tester-Files` folder.
2. **Student submissions:** choose the folder that holds the student ZIPs or folders. Each
   submission is checked and, where possible, fixed in a copy (the originals are never changed).
   A student who cannot be identified is listed under *Confirm the identity of skipped students*:
   enter their username, name and email, then save.
3. **Run grading:** every student is compiled and tested, four at a time, with live progress. A test
   case that runs past the time limit is skipped and the rest of the tester still runs. You can
   cancel a run; the previous results come back. *Regrade* grades one student again.
4. **Score sheet:** choose the score sheet CSV exported from the LMS, then *Download filled CSV*.
   Only the `Calculated Final Grade Numerator` of matched students is filled in; the file you
   chose is not changed.

*Download all reports (ZIP)* saves the summary and every student's report.

## Test submissions

`test-submissions/` holds extra submissions, one folder each, that show cases the provided samples
do not. Copy these folders into the folder of student ZIPs before step 2.

| Submission | What it shows |
|---|---|
| `RenameToYourUsername` | The resource folder submitted without renaming it or filling in the headers. The student is skipped until you confirm who it is. |
| `missing.files.2025` | No `Q3` folder and no `Q2b.java`. Only those questions score 0; the rest are graded. |
| `loop.q1b.2025` | Q1b loops forever on test case 2 only. That test is skipped after the time limit; tests 1 and 3 still earn marks. |
| `copied.answer.2025` | ping.lee.2023's Q1a and Q2a with the variables renamed. Flagged as possible plagiarism. |

## Reports

Each grading run writes a folder under `grading-reports/`, named after the time it started:

- `summary.pdf`: students who need a manual check and why, all marks, class statistics, common
  mistakes, possible plagiarism, and the settings used.
- `students/<username>.pdf`: one student's identity, the fixes made to their submission, every test
  case with expected and actual output, their marks, and their style results.

## Configuration

The settings are in `src/main/resources`:

- `grading.properties`: time limits, how many students are graded at once, memory and size limits,
  the email domain used to complete missing emails, the port, penalties, and plagiarism thresholds.
- `assessment.properties`: the questions, the folder each belongs in, and the files each tester
  needs. Change this file to grade a different assessment.

To change a setting without rebuilding, copy the file to `config/` (for example
`config/grading.properties`) and edit the copy. Values you leave out keep their defaults.

Style checking uses the files in `config/`: `checkstyle.xml` (the checks), `style-rules.properties`
(each rule's guide section and weight) and `template/` (the starter code, whose lines are not
checked). Style is reported only; set `penalty.style-per-violation` to deduct marks for it.

## Project structure

- `src/main/java/web`: the web server and its routes.
- `src/main/java/grading/session`: what is loaded and the grading run.
- `src/main/java/grading/submission`: opening, checking and fixing submissions.
- `src/main/java/grading/tester`: checking the tester folder.
- `src/main/java/grading/execution`: compiling and running testers with time limits.
- `src/main/java/grading/scoring`: per-question and per-student marks.
- `src/main/java/grading/reporting`: PDF reports and the score sheet.
- `src/main/java/grading/style`: style checking against the CS102 guide.
- `src/main/java/grading/similarity`: plagiarism detection.
- `src/main/java/grading/feedback`: explanations of each student's mistakes.
- `src/main/java/grading/progress`: the live progress shown on the page.
- `src/main/java/grading/config`, `assessment`, `common`: settings, the assessment's files, and
  small shared helpers.
- `src/main/resources/web`: the web page.
- `src/test/java`: automated tests. Run them with `./mvnw verify` (Windows: `mvnw.cmd verify`).

## Libraries

- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/): reads the score sheet.
- [Apache PDFBox](https://pdfbox.apache.org/): writes the PDF reports.
- [Checkstyle](https://checkstyle.org/): style checking. The jar is in `lib/`.
- [Vue](https://vuejs.org/), [Bootstrap](https://getbootstrap.com/) and
  [JSZip](https://stuk.github.io/jszip/): the web page. They are bundled, so no internet is needed.
- [JUnit 5](https://junit.org/junit5/): the automated tests.
