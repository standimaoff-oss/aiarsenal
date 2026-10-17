#!/usr/bin/env bash
# Packs the mod sources as a zip where EVERY file gets an extra ".txt" suffix (build.gradle -> build.gradle.txt),
# as the user requested. Text sources only (java, gradle, json, toml, mcmeta, lang, properties, Blender/py/sh tools);
# binary assets (glb/png/ogg) are not "sources" and are shipped inside the jar.
# usage: tools/src_zip.sh [modid]   -> /home/user/release/<modid>-sources-txt.zip
set -euo pipefail
MODID=${1:-aiarsenal}
SRC=/home/user/mods/$MODID
OUT=/home/user/release/$MODID-sources-txt.zip
TMP=$(mktemp -d /tmp/srczip.XXXX); D=$TMP/$MODID-sources
mkdir -p $D
cd $SRC
find . -type f \( -name '*.java' -o -name '*.gradle' -o -name '*.properties' -o -name '*.toml' -o -name '*.json' \
  -o -name '*.mcmeta' -o -name '*.fsh' -o -name '*.vsh' -o -name '*.md' -o -name '*.cfg' -o -name '*.txt' -o -name 'gradlew' -o -name '*.bat' \) \
  -not -path './build/*' -not -path './.gradle/*' -not -path './run*/*' -not -path './gradle/wrapper/*.jar' \
  | while read -r f; do mkdir -p "$D/$(dirname "$f")"; cp "$f" "$D/$f.txt"; done
mkdir -p $D/_tools
cd /home/user/tools
find . -type f \( -name '*.py' -o -name '*.sh' \) -not -path './__pycache__/*' | while read -r f; do mkdir -p "$D/_tools/$(dirname "$f")"; cp "$f" "$D/_tools/$f.txt"; done
cp /home/user/tools/r "$D/_tools/r.txt" 2>/dev/null || true
# r23: everything needed to rebuild the workspace from this zip + the jar (tools/ws_restore.sh)
find . -type f \( -name '*.json' -o -name '*.png' -o -name '*.txt' -o -name '*.md' \) -not -path './__pycache__/*' \
  | while read -r f; do mkdir -p "$D/_tools/$(dirname "$f")"; cp "$f" "$D/_tools/$f.txt"; done
[ -f $SRC/gradle/wrapper/gradle-wrapper.jar ] && cp $SRC/gradle/wrapper/gradle-wrapper.jar "$D/gradle/wrapper/gradle-wrapper.jar.txt"
mkdir -p $D/_notes
[ -d /home/user/notes ] && (cd /home/user/notes && find . -type f | while read -r f; do mkdir -p "$D/_notes/$(dirname "$f")"; cp "$f" "$D/_notes/$f.txt"; done)
cp /home/user/tools/ws_restore.sh "$D/ws_restore.sh.txt" 2>/dev/null || true
cp /home/user/reports/$MODID.md "$D/_report_$MODID.md.txt" 2>/dev/null || true
rm -f $OUT; (cd $TMP && zip -qr $OUT $MODID-sources)
echo "$OUT $(find $D -type f | wc -l) files $(du -h $OUT | cut -f1)"
rm -rf $TMP
