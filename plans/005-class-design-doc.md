# Plan 005: Write an up-to-date class design document with a UML class diagram

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> 1. Plan 004 should be DONE (check `plans/README.md`), so the diagram shows the final design with
>    `grading.session.GradingSession`. If plan 004 is not DONE, either wait, or write the document
>    without the `grading.session` package and say so in the status row.
> 2. Every class named in this plan was confirmed to exist at planning time. Re-check with the command
>    in step 1; any missing class is drift → STOP and report which.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW (documentation only)
- **Depends on**: plans/004-extract-grading-session.md
- **Category**: docs
- **Planned at**: commit `5cad239` + uncommitted working tree, 2026-10-25

## Why this matters

This is a CS102 course project. 7 of its marks are for a presentation that must "State your team's
object-oriented design (system architecture). Use UML class diagrams if necessary", followed by Q&A
on the design. The team's only design document, `AutoGradingSystem_ClassDesign.md` (with
`auto_grading_system_class_diagram.html`), sits *outside* the repo in the parent folder and dates from
8 September, before most of the code was written: it names classes that do not exist
(`ExecutionResult`, `ReportExporter`, `ScoreParser`, `SubmissionValidator`) and has nothing on the
feedback or plagiarism features. Presenting it would contradict the code in Q&A. A short, accurate
document *inside* the repo, with a Mermaid class diagram the team can paste into slides, fixes that.

## Current state

- Java sources: `src/main/java`, 15 packages. The app is a local web app: `web.WebApplication` serves
  one page (`src/main/resources/web/index.html`) and a JSON API.
- There is no `docs/` folder in the repo.
- The design to document (verified against the code at planning time; `grading.session` assumes plan
  004 has landed). Package → classes → role:

| Package | Key classes | Role / OOP point to show |
|---|---|---|
| `web` | `WebApplication`, `HttpResponses`, `UploadedFolder`, `UploadedScoreSheet` | the view: HTTP routes (a route table) translating requests into session calls |
| `grading.session` | `GradingSession`, `SessionRefusal` | the model: what is loaded, latest results, runs grading on a thread |
| `grading.config` | `GradingSettings`, `SimilaritySettings`, `ConfigFile` | settings read from `.properties` files (externalised config) |
| `grading.assessment` | `AssessmentDefinition` | the questions and files of the assessment, from `assessment.properties` |
| `grading.submission` | `SubmissionPreparationService`, `SubmissionDiscovery`, `SubmissionSource` (abstract, sealed) ← `ZipSubmission`, `FolderSubmission`; `SubmissionCheck` (interface) ← `UsernameFolderCheck`, `QuestionFolderCheck`, `ExtraJavaFilesCheck`; `SubmissionValidationResult`, `SubmissionCorrectionService` | validation and auto-correction of submissions; **inheritance** (`SubmissionSource`) and **polymorphism** (`SubmissionCheck`) |
| `grading.tester` | `TesterFileValidationService`, `TesterValidationResult` | checks the tester folder; both result types implement `ValidationResult` |
| `grading.execution` | `JavaGradingService`, `BatchGrader`, `TesterCompiler`, `TesterRunner`, `ProcessRunner` (interface) ← `BoundedProcessRunner`, `TimeoutRecovery`, `StudentGradingResult`, `QuestionGradingResult` | compiles and runs testers with time limits; recovers from infinite loops |
| `grading.feedback` | `FeedbackGenerator`, `FeedbackRule` (interface) ← `CompilationErrorRule`, `StarterCodeRule`, `MissingExceptionRule`, `RuntimeExceptionRule`, `TimeoutRule`, `OutputLimitRule`, `WrongOutputRule`; `Feedback` | explains mistakes at the student's line; **polymorphism** via rules |
| `grading.similarity` | `SimilarityChecker`, `SimilarityAlgorithm` (interface) ← `WinnowingSimilarity`, `GreedyStringTiling`; `JavaTokenizer`, `SimilarityReport`, `SimilarityPair` | plagiarism check with two algorithms behind one interface |
| `grading.style` | `StyleChecker`, `Templates` | Checkstyle-based style scoring |
| `grading.scoring` | `ScoreBreakdown`, `ScoreSummary` | marks per student |
| `grading.reporting` | `GradingAttempt`, `StudentOutcome`, `AttemptReportWriter`, `PdfReportRenderer`, `ScoreSheet`, `ScoreSheetUpdater` | assembles one attempt and writes PDF reports and the filled score sheet |
| `grading.progress` | `ProgressTracker` | live progress shown on the page |
| `grading.common` | `Json`, `Messages`, `Percent` | small shared helpers |

- Main flow (for the sequence description): page uploads testers and submissions →
  `WebApplication` → `GradingSession.importTesters/importSubmissions` (→ `TesterFileValidationService`,
  `SubmissionPreparationService`) → `GradingSession.startGrading` → `JavaGradingService.gradeBatch` →
  `GradingAttempt.assemble` (adds style, `FeedbackGenerator`, `SimilarityChecker`) →
  `AttemptReportWriter` (PDFs) → `ScoreSheetUpdater` (filled CSV).

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Check classes exist | see step 1 | prints `checked` and no `MISSING` lines |
| Build (unchanged) | `./mvnw -q -B clean verify` | exit 0 |

## Scope

**In scope**:
- `docs/class-design.md` (create)

**Out of scope**:
- Any file outside the repo, including the old `../AutoGradingSystem_ClassDesign.md` and
  `../auto_grading_system_class_diagram.html` — do not edit or delete them; tell the operator they are
  superseded.
- `README.md` — the team will update it at the end of the project.
- Any Java source.

## Git workflow

- Branch: `advisor/005-class-design-doc`.
- One commit: `Add class design document with UML class diagram`.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Confirm the classes still exist

```sh
for c in WebApplication GradingSession SessionRefusal HttpResponses UploadedFolder UploadedScoreSheet \
  GradingSettings SimilaritySettings ConfigFile AssessmentDefinition SubmissionPreparationService \
  SubmissionDiscovery SubmissionSource ZipSubmission FolderSubmission SubmissionCheck UsernameFolderCheck \
  QuestionFolderCheck ExtraJavaFilesCheck SubmissionValidationResult SubmissionCorrectionService \
  ValidationResult TesterFileValidationService TesterValidationResult JavaGradingService BatchGrader \
  TesterCompiler TesterRunner ProcessRunner BoundedProcessRunner TimeoutRecovery StudentGradingResult \
  QuestionGradingResult FeedbackGenerator FeedbackRule CompilationErrorRule StarterCodeRule \
  MissingExceptionRule RuntimeExceptionRule TimeoutRule OutputLimitRule WrongOutputRule Feedback \
  SimilarityChecker SimilarityAlgorithm WinnowingSimilarity GreedyStringTiling JavaTokenizer \
  SimilarityReport SimilarityPair StyleChecker Templates ScoreBreakdown ScoreSummary GradingAttempt \
  StudentOutcome AttemptReportWriter PdfReportRenderer ScoreSheet ScoreSheetUpdater ProgressTracker \
  Json Messages Percent; do
  [ -z "$(find src/main/java -name "$c.java")" ] && echo "MISSING $c"; done; echo checked
```

**Verify**: output is exactly `checked`. (If plan 004 has not landed, `GradingSession` and
`SessionRefusal` will be MISSING — that is the only acceptable exception; leave them out of the doc.)

### Step 2: Check the relationships before drawing them

For each "implements"/"extends" arrow you will draw, confirm it in the code:

```sh
grep -rn 'implements FeedbackRule' src/main/java/grading/feedback | wc -l          # expect 7
grep -rn 'implements SimilarityAlgorithm' src/main/java/grading/similarity | wc -l # expect 2
grep -rn 'implements SubmissionCheck' src/main/java/grading/submission | wc -l     # expect 3
grep -rn 'extends SubmissionSource' src/main/java/grading/submission | wc -l       # expect 2
grep -rn 'implements ProcessRunner' src/main/java/grading/execution | wc -l        # expect 1
grep -rn 'implements ValidationResult' src/main/java/grading | wc -l               # expect 2
```

**Verify**: each count matches the comment. A different count → draw what the code says and note it.

### Step 3: Write `docs/class-design.md`

Sections, in this order:

1. **Overview** — 3–5 sentences: a local web app; `WebApplication` is the view, `GradingSession` the
   model; the grading pipeline packages; settings in `.properties` files.
2. **Packages** — the table from "Current state" (package, key classes, role).
3. **Class diagram** — one Mermaid `classDiagram` block. Keep it readable for a slide: show only the
   classes in the table, no fields; show at most 1–3 key public methods per class (e.g.
   `GradingSession: +startGrading()`, `+regrade(username)`; `SimilarityAlgorithm: +compare(first, second)`;
   `FeedbackRule: +apply(context)`; `SubmissionCheck`'s single method — read it from
   `src/main/java/grading/submission/SubmissionCheck.java`). Use `<|..` for implements, `<|--` for
   extends, `-->` for "uses". Mark interfaces with `<<interface>>` and `SubmissionSource` with
   `<<abstract>>`.
4. **Where OOP principles show** — four short bullets with one example each: encapsulation (records
   with validated constructors, e.g. `SubmissionFinding`), inheritance (`SubmissionSource`),
   polymorphism (`FeedbackRule`, `SimilarityAlgorithm`, `SubmissionCheck`), abstraction (`ProcessRunner`).
5. **Grading flow** — the main flow from "Current state" as a numbered list (or a Mermaid
   `sequenceDiagram` if you keep it under ~12 messages).
6. **Algorithms** — two short paragraphs: Winnowing (fast screening of every pair, as in MOSS) vs
   Greedy String Tiling (accurate scoring of pairs that pass, as in JPlag; finds moved code); both work
   on normalised tokens from `JavaTokenizer`, with the template's code removed first. Read the Javadoc
   of `WinnowingSimilarity.java` and `GreedyStringTiling.java` and summarise it — do not invent numbers.

**Verify**: `grep -c '```mermaid' docs/class-design.md` → `1` or `2`.
**Verify**: every class name inside the Mermaid block(s) appears in the step 1 list (spot-check by
reading; no names that are not in the code).

### Step 4: Check the diagram renders

Paste the Mermaid block into https://mermaid.live (or any Mermaid renderer available to you).

**Verify**: it renders with no syntax error. If you cannot open a renderer, say so in the status row.

## Test plan

No code tests (documentation only). Step 2's greps verify every inheritance/implements arrow; step 4
verifies the diagram syntax.

## Done criteria

- [ ] `docs/class-design.md` exists with the six sections above
- [ ] The Mermaid class diagram renders, and every class in it exists under `src/main/java`
- [ ] `./mvnw -q -B clean verify` exits 0 (nothing else changed)
- [ ] `git status --short` lists only `docs/class-design.md`
- [ ] `plans/README.md` status row updated, noting that `../AutoGradingSystem_ClassDesign.md` and
      `../auto_grading_system_class_diagram.html` are now superseded

## STOP conditions

- More than two classes from step 1 are MISSING (beyond the plan-004 exception) — the design has
  moved on; report instead of documenting a guess.
- A relationship count in step 2 is far off (for example 0 `FeedbackRule` implementations) — report.
- The operator asks for the diagram as an image file in slides — that is a separate, manual step.

## Maintenance notes

- Update this document whenever a package or an interface is added or removed; the presentation and
  Q&A rely on it matching the code.
- The diagram deliberately omits small records and helpers; keep it slide-sized.
