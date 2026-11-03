# UML class diagrams

One PlantUML file per slide, in the order to present them. Each file is self-contained and
uses the same style.

| # | File | What it shows | Design point to make |
|---|---|---|---|
| 1 | `01-architecture-overview.puml` | The packages as layers: UI → session → services → reporting → configuration (`grading.common` helpers left out) | Each package has one responsibility; the UI talks only to the session and the progress model; settings come from one place |
| 2 | `02-submission-validation.puml` | Opening ZIPs and folders, the checks, automatic corrections and identity confirmation | **Inheritance and polymorphism**: `ZipSubmission` and `FolderSubmission` override `SubmissionSource`. **Open/closed**: a new anomaly check is one new `SubmissionCheck` class |
| 3 | `03-grading-session.puml` | `GradingSession` runs imports, grading, cancel and regrade; `ProgressTracker` feeds the live table | **Encapsulation**: all grading state is private to the session; the UI only reads snapshots. `SessionRefusal` turns misuse into a clear message |
| 4 | `04-test-harness.puml` | Validating testers, compiling, running with time and output limits, recovering from infinite loops | **Abstraction**: `ProcessRunner` hides how processes are limited. `CompilationResult` is `AutoCloseable`, so temporary folders are always removed |
| 5 | `05-similarity.puml` | Tokenizing answers and comparing every pair with two algorithms | **Strategy pattern**: `WinnowingSimilarity` screens quickly and `GreedyStringTiling` scores precisely, both behind `SimilarityAlgorithm` |
| 6 | `06-feedback-and-style.puml` | Seven feedback rules, and Checkstyle with three custom checks | **Strategy pattern** again (`FeedbackRule`); **inheritance** from Checkstyle's `AbstractCheck` |
| 7 | `07-reporting-and-scoring.puml` | Assembling an attempt, the PDF and CSV reports, filling the LMS score sheet | Formatters only build text and the renderer only draws PDFs (**single responsibility**). The imported sheet is never changed |
| 8 | `08-ui-mvc.puml` | JavaFX application, FXML view, controller and its helpers | **Model–view separation**: the model (`GradingSession`, `ProgressTracker`) knows nothing about JavaFX |
| 9 | `09-configuration.puml` | The two `.properties` files and the records they load into | **Externalized configuration**: no limit, threshold or path is written into the code |

## Reading the diagrams

- `+` public, `-` private, `#` protected, `~` package-private (Java's default access). In the source
  each member starts with `<U+00A0>`, an invisible space, so that every renderer (draw.io included)
  prints these markers as text instead of icons. A package-private `~` is written `~~`, because a
  single `~` escapes the next character.
- `<<record>>`: a Java record. Its fields are private and final, and each has a public accessor of the
  same name, which is not repeated in the box.
- Grey boxes are classes from another diagram or from a library (JavaFX, Checkstyle, the JDK); their
  details are on their own slide.
- Arrows: `<|--` inheritance, `<|..` interface implementation, `-->` association (a field), `..>`
  dependency (a parameter, a return value or an object it creates), `+--` a nested type. Numbers are
  multiplicities.
- Only the essential fields and the main methods are shown, to keep each slide readable.

## Rendering

The files use standard PlantUML syntax. To make PNGs for the slides, run this from the project
folder with [PlantUML](https://plantuml.com/download) and Graphviz installed:

```sh
java -jar plantuml.jar -tpng docs/uml/*.puml
```

Or open the files with the PlantUML plugin for IntelliJ IDEA or VS Code. Graphviz is not needed if
`!pragma layout smetana` is added after `@startuml`.
