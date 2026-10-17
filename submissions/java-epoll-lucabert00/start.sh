#!/usr/bin/env bash
# Runs the server in the foreground. Configured only by SQLITE_PATH, JWT_SECRET, HOST, PORT.
#
# SERVE MODE: DIRECT on 0.0.0.0:80. No Nginx (challenge rule 9). Nginx's worker_connections cap
# would bound us at ~16,384 sockets; we want the fd budget, not the hop.
set -euo pipefail
cd "$(dirname "$0")"

export HOST=${HOST:-0.0.0.0}
export PORT=${PORT:-80}

# RLIMIT_NOFILE is per process, so one process can never hold more than ~65,535 sockets. Each
# worker binds the same port with SO_REUSEPORT and the kernel spreads connections across them:
# N workers can hold N x 65,535. Two workers is the whole win - above ~100k VUs two processes
# sharing one SQLite file stop being able to reset the WAL, and a third worker makes that worse.
WORKERS=${WORKERS:-2}
# Prefer the install.sh-pinned runtime: CI runners pre-set JAVA_HOME to a system
# JDK whose flags predate this submission (e.g. no UseCompactObjectHeaders),
# so its JVM refuses to start. Fall back to the env only when install.sh has
# not run. On the droplet /opt/jdk exists, so behaviour there is unchanged.
JAVA=/opt/jdk/bin/java
[ -x "$JAVA" ] || JAVA=${JAVA_HOME:-/opt/jdk}/bin/java
[ -x "$JAVA" ] || { echo "start.sh: no java at $JAVA (install.sh first)" >&2; exit 1; }
[ -f "$PWD/build/native/libsqlite3.so" ] || { echo "start.sh: no $PWD/build/native/libsqlite3.so (build.sh first)" >&2; exit 1; }

# 1 vCPU. SerialGC because G1/Parallel spawn GC threads that only contend with request work.
# -Xms == -Xmx: no heap resizing during the 60 s ramp. Live data is tiny (per-connection state is
# a few dozen bytes and buffers are shared per worker), so the heap buys a young gen, not retention.
JVM_FLAGS=(
  -XX:+UseSerialGC
  -XX:+UseCompactObjectHeaders
  -XX:+AlwaysPreTouch
  -XX:-UsePerfData
  -XX:MaxDirectMemorySize=256m
  -XX:ActiveProcessorCount=1
  -Xms256m -Xmx256m -Xmn64m
  --enable-native-access=ALL-UNNAMED
)

pids=()
for i in $(seq 0 $((WORKERS - 1))); do
  WORKER_ID="$i" "$JAVA" "${JVM_FLAGS[@]}" \
    -Dsqlite.lib="$PWD/build/native/libsqlite3.so" \
    -cp build/classes challenge.Main &
  pids+=($!)
done

# TEMPORARY E07 DIAGNOSTIC - remove once the loop is proven under the gate.
#
# test/run.sh polls /health with `curl -sf` and no --max-time, so a worker that never
# answers leaves curl blocked and the job burns its entire 6 h ceiling with an empty
# log. This bounds the *startup* window only: the first successful /health disarms
# the watcher for good, so it can never fire during a benchmark hold. 8 x 10 s is
# 80 s, past the rule 10 budget of 60 s, so any server that reaches it is already
# broken by rule 10 and killing the set is correct rather than merely convenient.
# The sampling below runs after that trigger and only adds to an already-failed run.
# When it does fire it prints each worker's thread stacks three times over, which run.sh then
# shows via `cat server.log` on its own 60 s deadline, and killing the workers is what
# unblocks the harness curl in the first place.
if command -v curl >/dev/null 2>&1; then
  JCMD="$(dirname "$JAVA")/jcmd"
  (
    for _ in $(seq 1 8); do
      sleep 10
      curl -sf -m 5 "http://$HOST:$PORT/health" >/dev/null 2>&1 && exit 0
    done
    echo "start.sh: /health unanswered for 80 s (rule 10 budget); worker threads:" >&2
    # Three samples ~2 s apart per worker, not one: a tight spin sits in the same frame
    # every time, so agreement across samples is what distinguishes a spin from a block.
    for p in "${pids[@]}"; do
      for s in 1 2 3; do
        sleep 2
        echo "--- pid $p sample $s ---" >&2
        "$JCMD" "$p" Thread.print 2>&1 | head -200 >&2 || true
      done
    done
    kill "${pids[@]}" 2>/dev/null || true
  ) &
fi

# A dead worker means half the connections are unroutable, so stop the whole set rather than
# limp on at half service.
trap 'kill "${pids[@]}" 2>/dev/null || true' TERM INT
wait -n || true
kill "${pids[@]}" 2>/dev/null || true
wait || true