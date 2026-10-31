---
target: critique this based on the 25-30 rubric
total_score: 23
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:/Users/elsbethlee/Desktop/SMU/Y2S1/CS102/Autograder project/auto-grading-system-cs102/.claude/worktrees/cs102-autograder-audit-5da28f/src/main/resources/ui/main-view.fxml"
target_fingerprint: "sha256:3cfb3a28b067e68cc57ca7e269b7cc581f0a386f789c07253374215815acf46a"
target_path: /Users/elsbethlee/Desktop/SMU/Y2S1/CS102/Autograder project/auto-grading-system-cs102/.claude/worktrees/cs102-autograder-audit-5da28f/src/main/resources/ui/main-view.fxml
timestamp: 2026-10-30T17-15-29Z
slug: src-main-resources-ui-main-view-fxml
---
Method: dual-agent (A: design review sub-agent · B: detector + measured-evidence sub-agent). Detector cannot read JavaFX FXML; B substituted measured contrast, type, keyboard and screenshot evidence.

## Design Health Score: 23/40 (Acceptable). Rubric: top of the 20–24 band.
| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of System Status | 3 | Live progress line is excellent; status pill stays grey at "review errors" with no count; needs-review reason not in table |
| 2 | Match System / Real World | 3 | Marker language mostly; "Normalized submissions", INFO/ERROR log prefixes, config paths in Help |
| 3 | User Control and Freedom | 2 | Cancel exists; re-import after grading silently clears results; closing mid-run cancels silently |
| 4 | Consistency and Standards | 2 | needs attention / needs review / review errors / Resolve issues drift; finding text names a control that does not exist; score-sheet-button style on 7 unrelated buttons; Modena tabs/selection clash |
| 5 | Error Prevention | 3 | Copy-not-overwrite and refusal are strong; save confirmation hides needs-review and skipped students |
| 6 | Recognition Rather Than Recall | 2 | No question maxima; reason needs a click per row; regrade on hidden right-click; similarity double-click only |
| 7 | Flexibility and Efficiency | 1 | No mnemonics/accelerators, no filter/search, no batch regrade |
| 8 | Aesthetic and Minimalist Design | 2 | Finished import cards keep 40% height; 9 visible actions after grading; monospace log is half the results area |
| 9 | Error Recovery | 3 | Per-error "Next step for marker" is good but lives in the log, far from the student |
| 10 | Help and Documentation | 2 | One modal help text; no contextual hints/tooltips |

## Priority issues
- [P1] Layout overlaps at 1280×720 and 960×660: the run toolbar draws over both import panels (main-view.fxml:37 minHeight 170 vs content), table shows 2–5 rows. Collapse finished import cards to summary chips; give the table the height. /impeccable adapt, /impeccable layout
- [P1] Review reasons and errors live in the Activity log, not on the student row. Add Reason column, "Needs review (n)" filter, amber status pill with count; demote log. /impeccable clarify, /impeccable layout
- [P1] Save filled sheet confirmation does not mention needs-review or skipped students before writing LMS grades. Name them; default to "Review first". /impeccable harden
- [P2] Keyboard/accessibility: no visible focus ring (styles.css .button/.text-field replace Modena background, no :focused rule), log/trees not focusable, no accelerators, checkbox label 1.29:1 on console, status colours 2.5–4.4:1 on selected rows. /impeccable audit, /impeccable harden
- [P2] Primary action never moves through the workflow; re-import and close-during-run have no guard. /impeccable clarify, /impeccable harden

## Persona red flags
Alex: no filter/search/batch regrade/accelerators; 1,000-node expanded tree at 200 students; 2 rows visible at 960×660.
Sam: no focus ring; log and trees unreachable; two identical "Import folder" names without accessibleText; double-click-only similarity rows.
Jordan: jargon (Normalized, INFO); Resolve issues far from the submissions card; nothing says "next: import score sheet".
Riley: silent re-import wipe; silent cancel on close; identity dialog single bottom message.

## Minor
No "/max" in column headers; blank Similar cells; status dot never changes; terminal "=====" headings; run-button has no hover/pressed; detail pane renders Modena #f4f4f4 not white; Modena blue progress bar off-palette; 3 unused CSS rules; 42 unique hex colours, no tokens.
