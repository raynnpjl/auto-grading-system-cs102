#!/bin/sh
# Starts the Auto Grading System; run compile.sh first. The page opens in the default browser, and its
# address is printed in case it does not.
# Runs from this folder so the settings, style rules and template in config/ are found.
set -e
cd "$(dirname "$0")"
./mvnw -q -B exec:java
