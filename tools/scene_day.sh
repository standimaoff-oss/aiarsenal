#!/usr/bin/env bash
# usage: scene_day.sh --mod bm21grad [--preset NAME] — builds the scene; presets: base | battery | range
set -euo pipefail
PRESET=base; while [ $# -gt 0 ]; do case $1 in --preset) PRESET=$2; shift;; esac; shift; done
R="python3 /home/user/tools/rcon.py"
$R kill @e[type=!player] >/dev/null
$R forceload add -48 -48 48 48 >/dev/null || true
$R fill -40 -63 -40 40 -62 40 minecraft:dirt >/dev/null
$R fill -40 -61 -40 40 -61 40 minecraft:grass_block >/dev/null
$R fill -40 -60 -40 40 -50 40 minecraft:air >/dev/null
$R time set day >/dev/null; $R weather clear >/dev/null
case $PRESET in
  base)    $R bm21 spawn 0 -60 0 0 >/dev/null ;;
  battery) for x in -9 0 9; do $R bm21 spawn $x -60 0 0 >/dev/null; done ;;
  range)   $R bm21 spawn 0 -60 0 0 >/dev/null
           for x in -12 -8 -4 0 4 8 12; do $R fill $x -60 -30 $((x+2)) -57 -28 minecraft:stone_bricks >/dev/null; done ;;
esac
