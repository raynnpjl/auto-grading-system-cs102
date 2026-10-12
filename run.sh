#!/bin/sh
# Starts the Auto Grading System. Run compile.sh first.
# Runs from this folder so the settings, style rules and template in config/ are found.
set -e
cd "$(dirname "$0")"
./mvnw -q -B javafx:run
