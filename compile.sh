#!/bin/sh
# Compiles every Java source file with the Maven Wrapper; class files are written to target/classes.
# The Checkstyle jar is taken from lib/ (see pom.xml), so it is never downloaded.
set -e
cd "$(dirname "$0")"
./mvnw -q -B -DskipTests compile
echo "Compiled into target/classes."
