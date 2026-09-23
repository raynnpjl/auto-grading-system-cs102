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
