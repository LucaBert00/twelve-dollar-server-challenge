#!/usr/bin/env bash
# Runs as a normal user. Builds SQLite from a pinned, checksummed amalgamation, compiles the
# Java sources, and runs the ported JUnit suite.
#
# The server itself has zero third-party Java dependencies: FFM reaches libc and libsqlite3
# directly. The only third-party artefact is the JUnit console launcher, and it is test-only,
# pinned by version and SHA256, exactly like the SQLite amalgamation.
set -euo pipefail
cd "$(dirname "$0")"

# Prefer the install.sh-pinned JDK: CI runners pre-set JAVA_HOME to a system
# JDK older than 27, which rejects --release 27. Fall back to the env only
# when install.sh has not run. On the droplet /opt/jdk exists, so behaviour
# there is unchanged.
JDK_HOME=/opt/jdk
[ -x "$JDK_HOME/bin/javac" ] || JDK_HOME=${JAVA_HOME:-/opt/jdk}
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

# JUnit 5 console launcher. One artefact carries the JUnit API, the params engine and the
# launcher, so the test compile and the test run need a single pinned download. The version
# matches the sibling submission's junit-jupiter.version (5.13.4) so the two folders stay
# comparable.
JUNIT_VERSION=1.13.4
JUNIT_JAR="junit-platform-console-standalone-${JUNIT_VERSION}.jar"
JUNIT_URL="https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/${JUNIT_VERSION}/${JUNIT_JAR}"
JUNIT_SHA256=3fdfc37e29744a9a67dd5365e81467e26fbde0b7aa204e6f8bbe79eeaa7ae892

mkdir -p build/native build/classes build/test-classes

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

if [ ! -f "build/${JUNIT_JAR}" ] || \
   [ "$(sha256sum "build/${JUNIT_JAR}" | cut -d' ' -f1)" != "${JUNIT_SHA256}" ]; then
  curl -fsSL --retry 3 --retry-delay 2 "${JUNIT_URL}" -o "build/${JUNIT_JAR}.part"
  echo "${JUNIT_SHA256}  build/${JUNIT_JAR}.part" | sha256sum -c - >/dev/null
  mv "build/${JUNIT_JAR}.part" "build/${JUNIT_JAR}"
fi

# --release 27 so the source cannot drift onto an API newer than the pinned JDK.
"${JAVAC}" --release 27 -Xlint:-options -d build/classes src/challenge/*.java
"${JAVAC}" --release 27 -Xlint:-options -cp "build/${JUNIT_JAR}" \
    -d build/test-classes src/test/java/challenge/*.java

# The suite runs from the build, the same way the sibling submission's build runs its tests:
# a suite that never runs is not a suite. test.sh skips loudly when the seed database and
# the goldens are not there, so a missing environment never reads as a passing suite.
bash test.sh gate

echo "build.sh: classes in build/classes, tests in build/test-classes, sqlite in build/native/libsqlite3.so"
