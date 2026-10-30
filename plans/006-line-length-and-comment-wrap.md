# Plan 006: Settle the line-length rule and reflow broken doc comments

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: plans 001–005 must be DONE.

## Status

- **Priority**: P3
- **Effort**: S (option A) / L (option B)
- **Risk**: LOW
- **Depends on**: plans/005-constants-names-logging.md
- **Category**: tech-debt
- **Planned at**: commit `e43d2e2`, 2026-10-30

## Why this matters

The code is wrapped consistently at 120 columns. The project's own `config/checkstyle.xml`, though, sets `LineLength max=80`, which is the CS102 guide and also Oracle's convention, and it flags about 1,700 lines. Some Javadoc was also hard-wrapped at about 80 columns by a formatter, so sentences break mid-phrase (seen on `main` in `ui/MainViewController.java:57-63` and `grading/execution/JavaGradingService.java:18-30`). The rubric asks that code "follows Java coding convention consistently". The team needs one defensible answer here.

## Decision (the user's — ask before executing)

- **Option A (recommended):** Keep 120 columns as the team convention. Write it down: add one line to the README "Code conventions" section at submission prep, and a slide note ("we follow Oracle conventions except a 120-column limit, as modern IDEs do"). Only reflow the broken doc comments.
- **Option B:** Reformat everything to 80 columns. That's about 1,700 lines of churn just before submission, with a real risk of merge conflicts with teammates.

## Steps (Option A)

### Step 1: Find mid-sentence Javadoc breaks

`grep -rn -B1 "^\s*\* [a-z]" src/main/java | grep -E "\*\s+[^@].{0,60}$" | head -50`. This lists comment lines that continue a sentence after a short previous line. Review each hit.

### Step 2: Reflow each hit to fill up to 120 columns

Don't change the wording.

**Verify**: `./mvnw -q -B clean verify` → exit 0 (comments only, so no behaviour change). Also `git diff --stat` touches only `.java` files, and `git diff | grep "^[+-]" | grep -v "^\(+++\|---\)" | grep -vE "^\s*[+-]\s*(\*|/\*\*|//)"` → empty, meaning only comment lines changed.

## Done criteria

- [ ] Decision recorded in `plans/README.md`
- [ ] Step 2 verifications pass

## STOP conditions

- The user hasn't chosen A or B.
- A non-comment line appears in the diff.
