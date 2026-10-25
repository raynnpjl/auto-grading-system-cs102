#!/bin/sh
# Starts the Auto Grading System. Run compile.sh first, then open http://127.0.0.1:8080 in a browser.
# Runs from this folder so the settings, style rules and template in config/ are found.
set -e
cd "$(dirname "$0")"
./mvnw -q -B exec:java
