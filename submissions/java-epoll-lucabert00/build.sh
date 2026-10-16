#!/usr/bin/env bash
# Runs as a normal user. Builds SQLite from a pinned, checksummed amalgamation and compiles
# the Java sources. Zero third-party Java dependencies: FFM reaches libc and libsqlite3 directly.
set -euo pipefail
cd "$(dirname "$0")"

JDK_HOME=${JAVA_HOME:-/opt/jdk}
[ -x "$JDK_HOME/bin/javac" ] || JDK_HOME=/opt/jdk
JAVAC="$JDK_HOME/bin/javac"

# SQLite 3.54.0. Built here, not downloaded as a binary: rule 10 forbids shipping prebuilt
# artefacts, and -march=native is only safe because this runs on the box that will serve.
SQLITE_VERSION=3540000
SQLITE_ZIP="sqlite-amalgamation-${SQLITE_VERSION}.zip"
SQLITE_URL="https://sqlite.org/2026/${SQLITE_ZIP}"
SQLITE_SHA256=68e913b0fe8ec6b4e5f391c594a28872884e80d6cd042a1e50c957be46b5aa4d
# THREADSAFE=2 (multi-thread mode) is required: the checkpoint thread has its own connection.
# QPSG makes prepared statements safe to share across threads at zero cost to single-threaded use.
SQLITE_FLAGS="-DSQLITE_THREADSAFE=2 -DSQLITE_DEFAULT_MEMSTATUS=0 -DSQLITE_DEFAULT_WAL_SYNCHRONOUS=1 -DSQLITE_DQS=0 -DSQLITE_LIKE_DOESNT_MATCH_BLOBS -DSQLITE_OMIT_DECLTYPE -DSQLITE_OMIT_DEPRECATED -DSQLITE_OMIT_PROGRESS_CALLBACK -DSQLITE_OMIT_SHARED_CACHE -DSQLITE_OMIT_LOAD_EXTENSION -DSQLITE_USE_ALLOCA -DSQLITE_ENABLE_QPSG"
CFLAGS="-O2 -march=native -fPIC -shared"

mkdir -p build/native build/classes

if [ ! -f "build/${SQLITE_ZIP}" ] || \
   [ "$(sha256sum "build/${SQLITE_ZIP}" | cut -d' ' -f1)" != "${SQLITE_SHA256}" ]; then
  curl -fsSL --retry 3 --retry-delay 2 "${SQLITE_URL}" -o "build/${SQLITE_ZIP}.part"
  echo "${SQLITE_SHA256}  build/${SQLITE_ZIP}.part" | sha256sum -c - >/dev/null
  mv "build/${SQLITE_ZIP}.part" "build/${SQLITE_ZIP}"
fi

STAMP="${SQLITE_VERSION} ${CFLAGS} ${SQLITE_FLAGS}"
if [ ! -f build/native/libsqlite3.so ] || [ "$(cat build/sqlite.stamp 2>/dev/null)" != "${STAMP}" ]; then
  rm -rf "build/sqlite-amalgamation-${SQLITE_VERSION}"
  unzip -qo "build/${SQLITE_ZIP}" -d build
  # shellcheck disable=SC2086
  gcc ${CFLAGS} ${SQLITE_FLAGS} \
      -o build/native/libsqlite3.so "build/sqlite-amalgamation-${SQLITE_VERSION}/sqlite3.c"
  printf '%s' "${STAMP}" > build/sqlite.stamp
fi

# --release 27 so the source cannot drift onto an API newer than the pinned JDK.
"${JAVAC}" --release 27 -Xlint:-options -d build/classes src/challenge/*.java
echo "build.sh: classes in build/classes, sqlite in build/native/libsqlite3.so"