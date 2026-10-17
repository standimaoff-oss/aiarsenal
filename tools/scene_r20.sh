#!/usr/bin/env bash
# r20 QA scene: player in a T-90M, targets ahead (ground top y=-61, entities at y=-60).
R="python3 /home/user/tools/rcon.py"
$R kill @e[type=!player] >/dev/null
$R forceload add -32 0 64 96 >/dev/null || true
$R fill -30 -63 0 60 -62 90 minecraft:dirt >/dev/null
$R fill -30 -61 0 60 -61 90 minecraft:grass_block >/dev/null
$R fill -30 -60 0 60 -40 90 minecraft:air >/dev/null
$R time set noon; $R weather clear; $R gamerule doDaylightCycle false
$R summon aiarsenal:t90m 0 -60 20 '{Rotation:[0f,0f],Tags:["me"]}'
$R summon aiarsenal:abrams 0 -60 44 '{Rotation:[90f,0f],Tags:["tA"]}'
$R summon aiarsenal:tiger 14 -60 50 '{Rotation:[200f,0f],Tags:["tB"]}'
$R summon aiarsenal:t34 -14 -60 50 '{Rotation:[160f,0f],Tags:["tC"]}'
$R tp TestPlayer 0 -60 17
sleep 2
$R ride TestPlayer mount @e[tag=me,limit=1]
