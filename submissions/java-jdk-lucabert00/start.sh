#!/usr/bin/env bash
# start.sh: runs the server in the foreground. Configured only by env vars.
set -euo pipefail
: "${SQLITE_PATH:?SQLITE_PATH must name a fresh copy of the seed database}"
: "${JWT_SECRET:?JWT_SECRET is required}"
: "${HOST:=127.0.0.1}"
: "${PORT:=3000}"
export HOST PORT SQLITE_PATH JWT_SECRET

# JVM flags placeholder for later tickets (no tuning in Phase 1).
# shellcheck disable=SC2206
JAVA_OPTS_ARR=(${JAVA_OPTS:-})
JAVA_OPTS_ARR+=(-Xmx512m)

if [ -x /opt/java-challenge-25.0.1/bin/java ]; then
  JAVA=/opt/java-challenge-25.0.1/bin/java
elif [ -n "${JAVA_HOME:-}" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

exec "$JAVA" "${JAVA_OPTS_ARR[@]}" -jar "$(dirname "$0")/target/server.jar"
