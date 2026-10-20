#!/usr/bin/env bash
# Environment probe for submissions/java-epoll-lucabert00.
#
# Captures the numbers the capacity analysis depends on, on the box that will actually serve:
#
#   * the descriptor ceiling - soft limit, hard limit, and the kernel's own view read from
#     inside the server process (the JVM raises the soft limit to the hard limit at startup,
#     so the shell's soft limit is not the ceiling the process runs under)
#   * the conntrack table size (read only - it is a kernel parameter and challenge rule 11
#     forbids changing it)
#   * the box's memory limit and free memory
#   * the seed database size
#   * the server's own startup log line
#
# Usage:
#   bash probe.sh [--server-log FILE] [--seed PATH] [--out FILE]
#   bash probe.sh --help
#
# Reads only. It changes no kernel tunable, no cgroup setting and no box setting.
set -uo pipefail

OUT="probe-output.txt"
SERVER_LOG=""
SEED=""

usage() {
  sed -n '2,19p' "$0" | sed -e 's/^#//' -e 's/^ //'
}

while [ $# -gt 0 ]; do
  case "$1" in
    --server-log) SERVER_LOG="${2:-}"; shift 2 ;;
    --seed)       SEED="${2:-}"; shift 2 ;;
    --out)        OUT="${2:-}"; shift 2 ;;
    --help|-h)    usage; exit 0 ;;
    *) echo "probe.sh: unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

# The repository root, derived from this script's own location.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
[ -n "$SEED" ] || SEED="$REPO_ROOT/seed/feed.db"

section() {
  echo
  echo "== $1"
}

{
  echo "# probe output"
  echo "# generated: $(date -u +%Y-%m-%dT%H:%M:%SZ) (UTC)"
  echo "# host: $(uname -a)"

  section "cpus"
  echo "nproc: $(nproc 2>/dev/null || echo unknown)"

  section "descriptor limits (shell view)"
  echo "ulimit -Sn (soft): $(ulimit -Sn 2>/dev/null || echo unknown)"
  echo "ulimit -Hn (hard): $(ulimit -Hn 2>/dev/null || echo unknown)"

  section "descriptor limits (process view, /proc/self/limits)"
  if [ -r /proc/self/limits ]; then
    grep -E '^Max open files' /proc/self/limits || echo "Max open files: not found"
  else
    echo "/proc/self/limits: not readable"
  fi

  section "server startup line"
  if [ -n "$SERVER_LOG" ] && [ -r "$SERVER_LOG" ]; then
    echo "log: $SERVER_LOG"
    grep -m1 '^startup: ' "$SERVER_LOG" || echo "startup: line not found in $SERVER_LOG"
  else
    echo "no --server-log given, or the log is not readable"
    echo "the server prints its own line as: startup: fd-ceiling=... high=... low=... nofile=[...]"
  fi

  section "conntrack (read only - rule 11 forbids changing it)"
  if [ -r /proc/sys/net/netfilter/nf_conntrack_max ]; then
    echo "nf_conntrack_max: $(cat /proc/sys/net/netfilter/nf_conntrack_max)"
  else
    echo "nf_conntrack_max: not readable"
  fi

  section "memory"
  free -m 2>/dev/null || echo "free: not available"
  if [ -r /sys/fs/cgroup/memory.max ]; then
    echo "cgroup v2 memory.max: $(cat /sys/fs/cgroup/memory.max)"
  fi
  if [ -r /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
    echo "cgroup v1 memory.limit_in_bytes: $(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)"
  fi
  if [ -r /proc/meminfo ]; then
    grep -E '^(MemTotal|MemAvailable):' /proc/meminfo
  fi

  section "seed database"
  if [ -r "$SEED" ]; then
    echo "path: $SEED"
    echo "bytes: $(stat -c %s "$SEED" 2>/dev/null || stat -f %z "$SEED" 2>/dev/null || echo unknown)"
  else
    echo "path: $SEED"
    echo "not readable - run: bash seed/make-seed.sh"
  fi

  section "done"
} > "$OUT"

cat "$OUT"
echo
echo "probe.sh: wrote $OUT"
