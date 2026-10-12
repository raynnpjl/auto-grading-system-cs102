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

## Grading workflow

1. **Tester files — Import folder:** select the `Tester-Files` folder.
2. **Student submissions — Import folder:** select the folder of student ZIPs or folders.
   Submissions are validated and normalized; select **Resolve issues** if a student cannot be identified.
3. **Run grading:** each question's tester is compiled and run against each student. The console
   lists every student's per-question scores and total.
4. **Import score sheet:** select the score sheet CSV exported from the LMS (for example
   `IS442-ScoreSheet.csv`). It can be imported before or after grading.
5. **Write scores:** review the summary, then confirm. Each student's total is written into the
   `Calculated Final Grade Numerator` column of the imported file, matched by `Username`.

Writing scores changes only the numerator cells of matched students; every other value, the
column order, and the line endings are kept as they were. Before writing, the application saves a
copy beside the sheet named `<sheet>.backup-<yyyyMMdd-HHmmss>.csv`, and it refuses to write if the
file changed after it was imported. Close the sheet in Excel before writing. Students in the sheet
without a graded submission are left unchanged and listed in the console.

## Maven layout

- `src/main/java`: application source, organised by responsibility:
  - `grading.submission`, `grading.tester`: validation and correction of imported files.
  - `grading.execution`: compilation and time-limited execution of testers.
  - `grading.scoring`: per-question and per-student scores of a grading run.
  - `grading.reporting`: the results report and score sheet updates.
  - `ui`: the JavaFX view and controller.
- `src/main/resources/ui`: the FXML view and CSS stylesheet.
- `src/test/java`, `src/test/resources`: unit and UI tests.
- `target`: generated build output, ignored by Git.

The POM declares JavaFX controls and FXML, the Java compiler plugin, and the JavaFX
launcher plugin. JavaFX graphics is included transitively.

## Libraries

- [OpenJFX](https://openjfx.io/) 21: the desktop user interface.
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/) 1.14: reading the LMS
  score sheet, including quoted values.
- [JUnit](https://junit.org/junit5/) 5 (tests only).

Build references: [Maven directory layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html),
[Maven Wrapper](https://maven.apache.org/tools/wrapper/),
and [OpenJFX setup](https://openjfx.io/openjfx-docs/).