#!/usr/bin/env bash
# r23: rebuilds the full working tree (mods/aiarsenal sources + binary assets, tools/, notes/, reports/) from the two
# files kept in the saved workspace: release/aiarsenal-sources-txt.zip and release/aiarsenal-1.0.0.jar.
#   1. unzip the sources, strip the ".txt" suffix from every file
#   2. _tools -> tools, _notes -> notes, _report_*.md -> reports/
#   3. binary assets (GLB models, PNG textures, OGG sounds) are taken back out of the jar (assets/ + data/)
# usage: bash ws_restore.sh [modid]   (run from anywhere; writes into /home/user)
set -euo pipefail
MODID=${1:-aiarsenal}
H=${WS_HOME:-/home/user}
ZIP=$H/release/$MODID-sources-txt.zip
JAR=$H/release/$MODID-1.0.0.jar
T=$(mktemp -d /tmp/wsr.XXXX)
unzip -q "$ZIP" -d $T
S=$T/$MODID-sources
find $S -type f -name '*.txt' | while read -r f; do mv "$f" "${f%.txt}"; done
mkdir -p $H/mods/$MODID $H/tools $H/notes $H/reports
cp -r $S/_tools/. $H/tools/
[ -d $S/_notes ] && cp -r $S/_notes/. $H/notes/
for r in $S/_report_*.md; do [ -f "$r" ] && cp "$r" "$H/reports/$(basename "$r" | sed 's/^_report_//')"; done
rm -rf $S/_tools $S/_notes $S/_report_*.md $S/ws_restore.sh
cp -r $S/. $H/mods/$MODID/
chmod +x $H/mods/$MODID/gradlew 2>/dev/null || true
R=$H/mods/$MODID/src/main/resources
mkdir -p $R
(cd $R && unzip -qo "$JAR" 'assets/*' 'data/*')
mkdir -p $H/.cache/mc/runs/client
[ -f $H/tools/client_options.txt ] && cp $H/tools/client_options.txt $H/.cache/mc/runs/client/options.txt
rm -rf $T
echo "restored: $(find $H/mods/$MODID/src -type f | wc -l) source/asset files, tools $(find $H/tools -type f | wc -l), notes $(find $H/notes -type f | wc -l)"
