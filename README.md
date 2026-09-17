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

## Submission validation

Use **Import folder** under **Student submissions** to select either a single
student folder or a batch folder containing student folders and ZIP files. The
application checks the required assessment layout:

```text
username/
  Q1/Q1a.java
  Q1/Q1b.java
  Q2/Q2a.java
  Q2/Q2b.java
  Q3/Q3.java
  Q3/ShapeComparator.java
```

The import also checks leading Java comments for a student name and email. It
creates corrected copies in a temporary normalized workspace and leaves the
original submissions unchanged. Common folder-name, hierarchy, and incomplete
header issues are corrected when the student identity can be established. Any
unresolved issue is shown in **Findings & reports**, and one malformed
submission does not stop the rest of the batch.

The **Tester files** import validates `Q1aTester.java`, `Q1bTester.java`,
`Q2aTester.java`, `Q2bTester.java`, and `Q3Tester.java`, including their public
class names and `main` methods.

Run the validation tests with:

```bat
mvnw.cmd test
```

On macOS, select an installed JDK 21 before running these commands:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
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
