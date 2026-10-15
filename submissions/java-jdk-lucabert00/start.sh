#!/usr/bin/env bash
# start.sh: runs the server in the foreground. Configured only by env vars.
set -euo pipefail
: "${SQLITE_PATH:?SQLITE_PATH must name a fresh copy of the seed database}"
: "${JWT_SECRET:?JWT_SECRET is required}"
: "${HOST:=127.0.0.1}"
: "${PORT:=3000}"
export HOST PORT SQLITE_PATH JWT_SECRET

# JVM posture: -Xmx512m is the standing posture, not a tuned value (DX-7 owns tuning).
# shellcheck disable=SC2206
JAVA_OPTS_ARR=(${JAVA_OPTS:-})
JAVA_OPTS_ARR+=(-Xmx512m)
# JDK HttpServer keep-alive posture (Phase 2, planning doc section K):
# - sun.net.httpserver.idleInterval=70 REQUIRED (SPEC needs idle >= 65 s behind
#   Nginx keepalive 64; JDK default 30 s is insufficient; same value class as
#   the reference 70 s pin). This is the only JDK knob changed in this ticket.
# - sun.net.httpserver.maxIdleConnections default 200 recorded, unchanged
#   (covers the 64-idle envelope with headroom; direct-mode ~15k sizing
#   deferred to Phase 10/DX-3).
# - sun.net.httpserver.nodelay default false recorded, unchanged (experimental,
#   deferred to Phase 10).
JAVA_OPTS_ARR+=(-Dsun.net.httpserver.idleInterval=70)

echo "JDK HttpServer flags (startup log):"
for flag in "${JAVA_OPTS_ARR[@]}"; do
  case "$flag" in
    -Dsun.net.httpserver.*) echo "  $flag" ;;
  esac
done
echo "  sun.net.httpserver.maxIdleConnections=200 (default, unchanged)"
echo "  sun.net.httpserver.nodelay=false (default, unchanged)"

if [ -x /opt/java-challenge-25.0.1/bin/java ]; then
  JAVA=/opt/java-challenge-25.0.1/bin/java
elif [ -n "${JAVA_HOME:-}" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

exec "$JAVA" "${JAVA_OPTS_ARR[@]}" -jar "$(dirname "$0")/target/server.jar"
