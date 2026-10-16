#!/usr/bin/env bash
# Runs once as root on a clean Ubuntu 24.04 x86_64 box (challenge rule 10).
# Installs the JDK, a C compiler for the SQLite amalgamation, and nothing else.
set -euo pipefail

JDK_BUILD=35
JDK_VERSION=27
JDK_HOME=/opt/jdk
TARBALL="OpenJDK${JDK_VERSION}U-jdk_x64_linux_hotspot_${JDK_VERSION}_${JDK_BUILD}.tar.gz"
URL="https://github.com/adoptium/temurin${JDK_VERSION}-binaries/releases/download/jdk-${JDK_VERSION}%2B${JDK_BUILD}/${TARBALL}"
SHA256=1cf69a4848ffb728b3b260dfd45206a51566ab571a02a30092271d4c580bccbc

# The challenge box is x86_64 (README.md rule 8). -march=native in build.sh makes any other
# architecture's artefact meaningless, so fail loudly here rather than at benchmark time.
if [ "$(uname -m)" != "x86_64" ]; then
  echo "install.sh: challenge box is x86_64 (README.md rule 8); this machine is $(uname -m)" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y --no-install-recommends curl ca-certificates gcc libc6-dev unzip

if [ -x "$JDK_HOME/bin/javac" ]; then
  echo "install.sh: JDK already present at $JDK_HOME"
  "$JDK_HOME/bin/javac" -version
  exit 0
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl -fsSL --retry 3 --retry-delay 2 "$URL" -o "$tmp/jdk.tar.gz"
echo "$SHA256  $tmp/jdk.tar.gz" | sha256sum -c - >/dev/null
mkdir -p "$JDK_HOME"
tar -xzf "$tmp/jdk.tar.gz" -C "$JDK_HOME" --strip-components=1
"$JDK_HOME/bin/javac" -version