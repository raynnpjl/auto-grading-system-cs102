# Plan 001: Add compile.bat and run.bat so the app builds and starts on Windows

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> 1. `git status --short -- compile.sh run.sh mvnw.cmd pom.xml` → must print nothing. This plan was
>    written against commit `5cad239` *plus uncommitted working-tree changes* (2026-10-25); if these
>    files show as modified, the operator has not committed that work yet — STOP and ask.
> 2. `git diff --stat 5cad239..HEAD -- compile.sh run.sh mvnw.cmd` → compare the "Current state"
>    excerpts below with the live files; on a mismatch, STOP.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: dx
- **Planned at**: commit `5cad239` + uncommitted working tree, 2026-10-25

## Why this matters

This is a CS102 course project. The assignment brief says the instructors "will run compile.sh and
run.sh on their laptops during assessment", describes those laptops as using "the DOS command prompt",
and refers to "compile.bat". A missing or non-working start script on the marker's machine falls under
the brief's harshest penalty: "Up to 50% penalty of your total marks if any of your Java source files
does not compile or run." The repo only has POSIX shell scripts (`compile.sh`, `run.sh`), which do not
run in a Windows Command Prompt. Adding two tiny `.bat` files that call the existing Maven Wrapper for
Windows (`mvnw.cmd`, already in the repo) removes that risk.

## Current state

- `compile.sh` (repo root) — builds the project with the Maven Wrapper:
  ```sh
  #!/bin/sh
  # Compiles every Java source file with the Maven Wrapper; class files are written to target/classes.
  # The Checkstyle jar is taken from lib/ (see pom.xml), so it is never downloaded.
  set -e
  cd "$(dirname "$0")"
  ./mvnw -q -B -DskipTests compile
  echo "Compiled into target/classes."
  ```
- `run.sh` (repo root) — starts the web application (main class `web.WebApplication`, configured in
  `pom.xml` under `exec-maven-plugin`):
  ```sh
  #!/bin/sh
  # Starts the Auto Grading System. Run compile.sh first, then open http://127.0.0.1:8080 in a browser.
  # Runs from this folder so the settings, style rules and template in config/ are found.
  set -e
  cd "$(dirname "$0")"
  ./mvnw -q -B exec:java
  ```
- `mvnw.cmd` (repo root) — the official Maven Wrapper for Windows. It is stored with **CRLF** line
  endings (`file mvnw.cmd` → "ASCII text, with CRLF line terminators"). It accepts the same arguments
  as `./mvnw`.
- There is no `.gitattributes` file in the repo.
- The scripts must run *from the repo root*, because the app reads `config/` relative to the
  current directory. The `.sh` files do this with `cd "$(dirname "$0")"`; the Windows equivalent is
  `cd /d "%~dp0"`.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Build + all tests | `./mvnw -q -B clean verify` | exit 0 |
| Compile only (macOS/Linux) | `./compile.sh` | prints `Compiled into target/classes.` |
| Check CRLF endings | `file compile.bat run.bat` | both say "with CRLF line terminators" |
| (Windows only) Compile | `compile.bat` in `cmd.exe` | prints `Compiled into target\classes.` |
| (Windows only) Run | `run.bat` in `cmd.exe` | app starts; `http://127.0.0.1:8080` shows "CS102 Auto Grading System" |

## Scope

**In scope** (the only files you should create or modify):
- `compile.bat` (create)
- `run.bat` (create)
- `.gitattributes` (create)

**Out of scope** (do NOT touch):
- `compile.sh`, `run.sh`, `mvnw`, `mvnw.cmd`, `pom.xml` — they work; the `.bat` files only call them.
- `README.md` — the team will update it in one pass at the end of the project; do not edit it here.

## Git workflow

- Branch: `advisor/001-windows-start-scripts` from the current branch.
- One commit. The repo uses plain sentence-style messages (e.g. `Fix progress JSON escaping and reject
  empty or non-ZIP uploads`). Example: `Add compile.bat and run.bat for Windows`.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Create `compile.bat`

Create `compile.bat` in the repo root with exactly this content:

```bat
@echo off
rem Compiles every Java source file with the Maven Wrapper; class files are written to target\classes.
rem The Checkstyle jar is taken from lib\ (see pom.xml), so it is never downloaded.
cd /d "%~dp0"
call mvnw.cmd -q -B -DskipTests compile
if errorlevel 1 exit /b 1
echo Compiled into target\classes.
```

Notes: `call` is required — without it, control never returns to the script after `mvnw.cmd`
finishes. `if errorlevel 1 exit /b 1` makes a failed build return a non-zero exit code, like
`set -e` in `compile.sh`.

**Verify**: `grep -c 'call mvnw.cmd -q -B -DskipTests compile' compile.bat` → `1`

### Step 2: Create `run.bat`

Create `run.bat` in the repo root with exactly this content:

```bat
@echo off
rem Starts the Auto Grading System. Run compile.bat first, then open http://127.0.0.1:8080 in a browser.
rem Runs from this folder so the settings, style rules and template in config\ are found.
cd /d "%~dp0"
call mvnw.cmd -q -B exec:java
```

**Verify**: `grep -c 'call mvnw.cmd -q -B exec:java' run.bat` → `1`

### Step 3: Give both files Windows (CRLF) line endings and keep them that way

Create `.gitattributes` in the repo root with exactly:

```
# Windows scripts must keep CRLF line endings, or cmd.exe can misread them.
*.bat text eol=crlf
*.cmd text eol=crlf
```

Then convert the two new files to CRLF:

```sh
perl -pi -e 's/\r?\n/\r\n/' compile.bat run.bat
```

**Verify**: `file compile.bat run.bat` → both lines contain `with CRLF line terminators`.
**Verify**: `git add --renormalize . && git status --short` → shows only `compile.bat`, `run.bat`,
`.gitattributes` as new (`A` or `??`). If `mvnw.cmd` appears as modified, STOP (see STOP conditions).

### Step 4: Make sure nothing else broke

**Verify**: `./compile.sh` → `Compiled into target/classes.`
**Verify**: `./mvnw -q -B clean verify` → exit 0.

### Step 5 (only if a Windows machine is available)

In `cmd.exe`, from any folder, run the full path to `compile.bat`, then the full path to `run.bat`.

**Verify**: `compile.bat` prints `Compiled into target\classes.`; after `run.bat`, opening
`http://127.0.0.1:8080` shows the page titled "CS102 Auto Grading System". Stop the app with Ctrl+C.

If no Windows machine is available, record "Windows run not verified" in the `plans/README.md` status
row instead of skipping silently.

## Test plan

No Java tests: these are launcher scripts. Verification is the content checks in steps 1–3, the
existing suite in step 4, and the manual Windows run in step 5.

## Done criteria

- [ ] `compile.bat`, `run.bat`, `.gitattributes` exist in the repo root with the content above
- [ ] `file compile.bat run.bat` reports CRLF line terminators for both
- [ ] `./mvnw -q -B clean verify` exits 0
- [ ] `git status --short` shows no modified tracked files other than the three new ones
- [ ] `plans/README.md` status row updated (note whether step 5 ran on Windows)

## STOP conditions

Stop and report back (do not improvise) if:

- `compile.sh` or `run.sh` no longer match the excerpts above (for example `run.sh` no longer uses
  `exec:java`) — the `.bat` files must call the same Maven goals.
- After adding `.gitattributes`, `git add --renormalize .` marks `mvnw.cmd` or any other existing file
  as modified — the attributes file would be rewriting files outside scope.
- On Windows, `run.bat` starts but the page does not load (for example "port already in use").

## Maintenance notes

- If `run.sh` or `compile.sh` ever change their Maven goals, change the `.bat` files the same way;
  they must stay equivalent.
- The README still describes Windows start-up with `mvnw.cmd javafx:run`, which no longer works. The
  team is rewriting the README at the end of the project; mention `compile.bat`/`run.bat` there.
