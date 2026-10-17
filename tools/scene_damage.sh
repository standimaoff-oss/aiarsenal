#!/usr/bin/env bash
# r18 QA scene: module damage on a flat pad (ground top y=-61, entities at y=-60).
R="python3 /home/user/tools/rcon.py"
$R kill @e[type=!player] >/dev/null
$R forceload add -32 0 64 96 >/dev/null || true
$R fill -30 -63 20 60 -62 90 minecraft:dirt >/dev/null
$R fill -30 -61 20 60 -61 90 minecraft:grass_block >/dev/null
$R fill -30 -60 20 60 -40 90 minecraft:air >/dev/null
$R summon aiarsenal:t90m 0 -60 40
$R summon aiarsenal:abrams 12 -60 40
$R summon aiarsenal:t90m 24 -60 40
$R summon aiarsenal:ka52 0 -60 58
$R summon aiarsenal:bf109 14 -60 58
$R summon aiarsenal:ah64 28 -60 58
