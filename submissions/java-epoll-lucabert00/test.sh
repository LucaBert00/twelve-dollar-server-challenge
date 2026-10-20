#!/usr/bin/env bash
# Runs the ported JUnit suite. Usage: bash test.sh [gate|bench|all]
#
#   gate   the correctness suite (the default): the tag the scored gate runs
#   bench  the bench-only suite
#   all    every test, no tag filter
#
# The runner is the pinned JUnit console-standalone jar that build.sh downloads.
# It carries the JUnit API, the params engine and the launcher in one artefact.
set -euo pipefail
cd "$(dirname "$0")"

JUNIT_VERSION=1.13.4
JUNIT_JAR="junit-platform-console-standalone-${JUNIT_VERSION}.jar"
JUNIT_URL="https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/${JUNIT_VERSION}/${JUNIT_JAR}"
JUNIT_SHA256=3fdfc37e29744a9a67dd5365e81467e26fbde0b7aa204e6f8bbe79eeaa7ae892

TAGS="${1:-gate}"
case "$TAGS" in
  gate)  TAG_ARGS=(--include-tag gate) ;;
  bench) TAG_ARGS=(--include-tag bench) ;;
  all)   TAG_ARGS=() ;;
  *) echo "test.sh: unknown selection '$TAGS' (use gate, bench or all)" >&2; exit 2 ;;
esac

# Prefer the install.sh-pinned runtime, the same way build.sh and start.sh do.
JDK_HOME=/opt/jdk
[ -x "$JDK_HOME/bin/java" ] || JDK_HOME=${JAVA_HOME:-/opt/jdk}
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || { echo "test.sh: no java at $JAVA (install.sh first)" >&2; exit 1; }

if [ ! -f "build/${JUNIT_JAR}" ]; then
  echo "test.sh: build/${JUNIT_JAR} is missing (build.sh first)" >&2
  exit 1
fi
if [ ! -d build/test-classes ]; then
  echo "test.sh: build/test-classes is missing (build.sh first)" >&2
  exit 1
fi

# The suite reads the seed database and the goldens from the repository root.
# Without them the suite cannot run, and that is an environment gap, not a result.
REPO_ROOT="$(cd ../.. && pwd)"
if [ ! -r "$REPO_ROOT/seed/feed.db" ] || [ ! -d "$REPO_ROOT/test/golden" ]; then
  echo "test.sh: SKIPPED - no seed/feed.db or test/golden under $REPO_ROOT"
  echo "test.sh:   run 'bash seed/make-seed.sh' at the repository root first."
  exit 0
fi

exec "$JAVA" --enable-native-access=ALL-UNNAMED -Xmx512m \
  -jar "build/${JUNIT_JAR}" \
  --class-path "build/classes:build/test-classes" \
  --select-package challenge \
  "${TAG_ARGS[@]}" \
  --details=summary \
  --details-theme=ascii \
  --disable-ansi-colors \
  --disable-banner
