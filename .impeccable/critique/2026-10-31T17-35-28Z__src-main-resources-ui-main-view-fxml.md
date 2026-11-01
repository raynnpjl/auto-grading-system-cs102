---
target: critique this based on the 25-30 rubric
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 1
target_identity: "file:/Users/elsbethlee/Desktop/SMU/Y2S1/CS102/Autograder project/auto-grading-system-cs102/.claude/worktrees/cs102-autograder-audit-5da28f/src/main/resources/ui/main-view.fxml"
target_fingerprint: "sha256:f1bd2d915c71a493a0efdd07d0ab87fed31cfc9596a4b0e45f90307cff88ba8c"
target_path: /Users/elsbethlee/Desktop/SMU/Y2S1/CS102/Autograder project/auto-grading-system-cs102/.claude/worktrees/cs102-autograder-audit-5da28f/src/main/resources/ui/main-view.fxml
timestamp: 2026-10-31T17-35-28Z
slug: src-main-resources-ui-main-view-fxml
---
Method: DEGRADED single-context (detector cannot read JavaFX FXML; no CDP). Evidence: offline render harness screenshots of a real 6-student run at 1320x920, 1280x720, 960x660 and the wrong-tester-folder case.

## Design Health Score: 28/40 (Good), up from 23
| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility | 3 | Rejected tester folder replaces "Grading complete — 3 to check" in the pill |
| 2 | Match | 3 | Literal markdown backticks in feedback; "Auto-fixed" unexplained |
| 3 | Control | 3 | Cancel, replace/close confirmations, rejection keeps earlier imports |
| 4 | Consistency | 3 | to check / Need review / needs review; stale "Prepare a grading run" heading |
| 5 | Error prevention | 3 | Green "5 of 5 valid" sits above red rejection |
| 6 | Recognition | 3 | Detail header does not say why the student needs review |
| 7 | Flexibility | 2 | No needs-review filter, no batch regrade |
| 8 | Aesthetic | 3 | Activity log repeats the table; header repeats Graded tile |
| 9 | Error recovery | 3 | Inline, plain, keeps work |
| 10 | Help | 2 | Modal text only, almost no tooltips |

## Priority issues
- [P1] Detail pane says "Submission problems: None." for a student whose Reason is "Compile error Q3"; header total lacks /20. Add "Needs review: <reason>" line, "10.0/20", rename or fill Submission problems. /impeccable clarify
- [P2] Rejected tester folder overwrites run status pill and header ("Tester files need attention") although earlier testers stay valid; tick line contradicts red message. Keep pill on run state; reword tick line. /impeccable clarify
- [P2] Literal backticks in detail-pane feedback. Render as monospace Text runs in a TextFlow or strip. /impeccable typeset
- [P2] 960x660/1280x720: 4 table rows, Reason truncated, run card full height after grading with pre-run heading. Slim it after grading; Reason width + tooltip. /impeccable layout, adapt
- [P3] Activity log raw dump: mid-record wrapping, ===== headings, duplicate results block, /private/tmp paths. /impeccable distill

## Persona red flags
Jordan: "Submission problems: None" vs needs review; Auto-fixed unexplained; stale heading.
Alex: no needs-review filter, no batch regrade, 4 rows at 960x660.
Sam: focus outlines removed by choice; log and trees not focusable.

## Minor
Header "Graded 6 of 6" duplicates tile; three names for one state; grey selected row reads disabled; detail pane Modena #f4f4f4 vs white cards; "Grading 0 of 6" with partial totals looks stuck; disabled teal Run button still looks live; no question maxima in column headers.
