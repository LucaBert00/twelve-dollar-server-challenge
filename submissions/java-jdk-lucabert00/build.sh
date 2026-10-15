#!/usr/bin/env bash
# build.sh: runs as a normal user. Pinned offline-capable build, no prebuilt binaries.
set -euo pipefail
cd "$(dirname "$0")"

if [ -x /opt/java-challenge-25.0.1/bin/java ]; then
  export JAVA_HOME=/opt/java-challenge-25.0.1
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ -x /opt/maven-challenge-3.9.11/bin/mvn ]; then
  MAVEN=/opt/maven-challenge-3.9.11/bin/mvn
else
  MAVEN=mvn
fi

"$MAVEN" --batch-mode --no-transfer-progress clean verify
