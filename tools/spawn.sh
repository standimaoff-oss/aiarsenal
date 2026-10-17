#!/usr/bin/env bash
# usage: spawn.sh [--player NAME] — day, clear, creative, tp to the test platform, give all mod items.
set -euo pipefail
P=TestPlayer; [ "${1:-}" = "--player" ] && P=$2
R="python3 /home/user/tools/rcon.py"
$R time set day >/dev/null; $R gamerule doDaylightCycle false >/dev/null; $R weather clear 999999 >/dev/null
$R gamerule doWeatherCycle false >/dev/null; $R gamemode creative $P >/dev/null
$R tp $P 0 -60 12 180 10 >/dev/null
$R clear $P >/dev/null
for it in bm21 rangefinder rocket_9m22u rocket_9m28f rocket_9m22s rocket_9m218 rocket_9m43; do $R give $P bm21grad:$it 16 >/dev/null; done
