#!/usr/bin/env bash
# Build from any checkout location. Java 17 must be installed/on PATH (or JAVA_HOME).
# Usage: MODID=aiarsenal tools/build.sh [Gradle tasks/arguments...]
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
MODID=${MODID:-aiarsenal}
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! command -v java >/dev/null; then
  echo 'Java 17 is required. Install a JDK or set JAVA_HOME before building.' >&2
  exit 1
fi
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-$HOME/.cache/mc/gradle-home}
CACHE=${MC_PROJECT_CACHE:-$HOME/.cache/mc/gradle-$MODID}
cd "$ROOT/mods/$MODID"
# GLBs are tracked in this repository; never restore them from an unrelated old release.
if [[ $# -eq 0 ]]; then set -- build; fi
exec ./gradlew --no-daemon --project-cache-dir "$CACHE" --gradle-user-home "$GRADLE_USER_HOME" "$@"
