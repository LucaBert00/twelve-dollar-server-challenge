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
JAVA=${JAVA_HOME:-/opt/jdk}/bin/java
[ -x "$JAVA" ] || JAVA=/opt/jdk/bin/java
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

# A dead worker means half the connections are unroutable, so stop the whole set rather than
# limp on at half service.
trap 'kill "${pids[@]}" 2>/dev/null || true' TERM INT
wait -n || true
kill "${pids[@]}" 2>/dev/null || true
wait || true