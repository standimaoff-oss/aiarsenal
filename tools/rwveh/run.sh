#!/bin/bash
# usage: tools/rwveh/run.sh <script.py>  — concat Blender libs + rwveh_lib + script, run headless
B=/home/user/tools/blender
mkdir -p /home/user/.cache/rwveh
OUT=/home/user/.cache/rwveh/$(basename "$1" .py)_full.py
OUT=/home/user/.cache/rwveh/$(basename "$1" .py)_${2//[^a-z0-9]/}_full.py
{ cat $B/lib.py $B/alib.py $B/arsenal_lib.py $B/rwveh_lib.py $B/armor_lib.py $B/ww2_lib.py; echo "${2:-}"; cat "$1"; } > "$OUT"
exec /home/user/.cache/blender/blender-5.2.2-linux-x64/blender -b --factory-startup --python "$OUT" 2>&1 | grep -vE "^(Blender|Read|$)|Warning: .*rgba"
