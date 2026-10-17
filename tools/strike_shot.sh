#!/usr/bin/env bash
# strike_shot.sh <type> <x> <y> <z> <out.png> [delay_after_detonation_s] — strike and grab a frame at detonation
set -euo pipefail
cd /home/user/tools
d0=$(./r bm21 stats | grep -o 'detonations=[0-9]*' | cut -d= -f2)
./r bm21 strike $1 $2 $3 $4 1 >/dev/null
for i in $(seq 1 100); do d=$(./r bm21 stats | grep -o 'detonations=[0-9]*' | cut -d= -f2); [ "$d" -gt "$d0" ] && break; sleep 0.1; done
sleep ${6:-0}
./screenshot.sh "$5"
