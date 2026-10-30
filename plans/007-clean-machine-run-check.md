# Plan 007: Prove `compile.sh` and `run.sh` work on a clean machine

> **Executor instructions**: This is a verification plan; it changes no code
> unless a check fails. Run each check and record the result in
> `plans/README.md`. If a check fails, STOP and report the exact output.

## Status

- **Priority**: P1 (do it before submission, after plans 001–005)
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/001-restore-backend-onto-javafx.md
- **Category**: dx
- **Planned at**: commit `e43d2e2`, 2026-10-30

## Why this matters

The brief says instructors "will run compile.sh and run.sh on their laptops", with a penalty of up to 50% if any source file "does not compile or run". Both scripts call the Maven Wrapper (`./mvnw`). On a fresh machine that downloads Maven 3.9.16, JavaFX 21.0.12 (platform-specific jars), PDFBox, commons-csv and JUnit. The only jar shipped in `lib/` is Checkstyle. The README says internet access is needed the first time, but nobody has checked a cold start, a Windows laptop, or a laptop whose default JDK isn't 21.

## Checks

1. **Cold cache**: `mv ~/.m2 ~/.m2.bak` (then restore it afterwards with `rm -rf ~/.m2 && mv ~/.m2.bak ~/.m2`). From a fresh unzip of the submission zip, run `./compile.sh` and then `./run.sh`. Expected: "Compiled into target/classes." and the window opens.
2. **JDK version**: `java -version` on this machine reports 25. Repeat check 1 with `JAVA_HOME` pointing to a JDK 21 if one is installed (`/usr/libexec/java_home -V`). Expected: same result.
3. **Execute bit after zipping**: `unzip -Z1 <submission.zip> | grep -E "mvnw$|\.sh$"`, then extract and run `ls -l mvnw compile.sh run.sh`. Expected: `-rwxr-xr-x`. If it's lost, note in the README that `sh compile.sh` works too: `compile.sh` calls `./mvnw`, which needs the bit, so suggest `sh mvnw ...` instead.
4. **Windows**: on any Windows laptop, `mvnw.cmd -q -B -DskipTests compile` then `mvnw.cmd -q -B javafx:run`. Expected: the window opens.
5. **Grading works at runtime** (Checkstyle `system`-scoped jar on the JavaFX classpath): in the running app, grade `test-submissions/`. The PDF summary has a style section and no `NoClassDefFoundError` appears in the activity log.

## Done criteria

- [ ] All 5 checks recorded as PASS in `plans/README.md`, or a failing check reported with its output

## STOP conditions

- Any check fails. Report the command and full output; don't patch the scripts without the user's go-ahead.
