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
# -Xms == -Xmx: no heap resizing during the 60 s ramp. The heap buys a young generation, not
# retention: the score is connection capacity, and the RAM budget is the binding ceiling, so
# the fixed cost per worker is kept as small as the workload allows. The figure is a
# placeholder, not a measured optimum - the bench phase owns the tuning.
# The worker count reaches the worker as a system property so it can divide the box's RAM
# between the workers sharing it. It is an in-process setting, not a fifth environment
# variable: the server is configured by SQLITE_PATH, JWT_SECRET, HOST and PORT only.
JVM_FLAGS=(
  -XX:+UseSerialGC
  -XX:+UseCompactObjectHeaders
  -XX:+AlwaysPreTouch
  -XX:-UsePerfData
  -XX:MaxDirectMemorySize=256m
  -XX:ActiveProcessorCount=1
  -Xms128m -Xmx128m
  --enable-native-access=ALL-UNNAMED
)

pids=()
for i in $(seq 0 $((WORKERS - 1))); do
  WORKER_ID="$i" "$JAVA" "${JVM_FLAGS[@]}" \
    -Dsqlite.lib="$PWD/build/native/libsqlite3.so" \
    -Dchallenge.workers="$WORKERS" \
    -cp build/classes challenge.Main &
  pids+=($!)
done

# A dead worker means half the connections are unroutable, so stop the whole set rather than
# limp on at half service.
trap 'kill "${pids[@]}" 2>/dev/null || true' TERM INT
wait -n || true
kill "${pids[@]}" 2>/dev/null || true
wait || true