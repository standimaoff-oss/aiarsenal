#!/usr/bin/env bash
# r15 QA scene: 10 armour stands wearing the uniform sets (row z=90 RF, z=94 US), ground top y=-61.
R="python3 /home/user/tools/rcon.py"
$R forceload add -16 80 32 112 >/dev/null || true
$R fill -10 -61 84 30 -61 100 minecraft:smooth_stone >/dev/null
$R fill -10 -60 84 30 -50 100 minecraft:air >/dev/null
$R kill @e[type=armor_stand] >/dev/null
$R time set noon >/dev/null; $R gamerule doDaylightCycle false >/dev/null; $R weather clear >/dev/null
i=0
for s in ru_private ru_sergeant ru_captain ru_colonel ru_general us_private us_sergeant us_captain us_colonel us_general; do
  x=$(( (i % 5) * 2 )); z=$(( 90 + (i / 5) * 4 ))
  $R "summon armor_stand $x.5 -60 $z.5 {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:[{id:\"aiarsenal:uniform_${s}_feet\",Count:1b},{id:\"aiarsenal:uniform_${s}_legs\",Count:1b},{id:\"aiarsenal:uniform_${s}_chest\",Count:1b},{id:\"aiarsenal:uniform_${s}_head\",Count:1b}],Pose:{RightArm:[0f,0f,0f],LeftArm:[0f,0f,0f]}}" >/dev/null
  i=$((i+1))
done
echo SCENE_UNIFORMS_OK
