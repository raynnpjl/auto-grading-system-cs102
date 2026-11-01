#!/bin/sh
# Checks this project's own source against the CS102 Java Coding Style rules the grader applies to
# students (config/checkstyle.xml). Line length is left out: the team's convention is 120 columns.
set -e
cd "$(dirname "$0")"
./mvnw -q -B -DskipTests compile
problems=$(java -cp "lib/checkstyle-14.1.0-all.jar:target/classes" com.puppycrawl.tools.checkstyle.Main \
    -c config/checkstyle.xml src/main/java | grep -E '^\[(WARN|ERROR)\]' | grep -v '\[line-length\]' || true)
if [ -n "$problems" ]; then
    echo "$problems"
    count=$(echo "$problems" | wc -l | tr -d ' ')
else
    count=0
fi
echo "$count style problems (line length excluded)"
[ "$count" -eq 0 ]
