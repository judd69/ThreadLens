#!/usr/bin/env bash
# Regenerates docs/sample-report.html from examples/deadlock.txt through
# ThreadLens's real parse -> analyse -> render pipeline.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -d "$HOME/workspace/tools/jdk-21.0.12.1+1" ]; then
  export JAVA_HOME="$HOME/workspace/tools/jdk-21.0.12.1+1"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

BUILD_DIR="build"
mkdir -p "$BUILD_DIR/buildtools-classes"
find src/buildtools/java -name '*.java' > "$BUILD_DIR/buildtools-sources.txt"
javac -cp "$BUILD_DIR/classes" -d "$BUILD_DIR/buildtools-classes" @"$BUILD_DIR/buildtools-sources.txt"

java -cp "$BUILD_DIR/classes:$BUILD_DIR/buildtools-classes" \
  dev.threadlens.buildtools.SampleMain \
  examples/deadlock.txt \
  docs/sample-report.html
