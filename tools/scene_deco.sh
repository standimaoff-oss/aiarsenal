#!/usr/bin/env bash
# r14 QA scene: flags on poles, armour plates, lamps, searchlights on a flat pad (ground top y=-61, blocks at y=-60).
R="python3 /home/user/tools/rcon.py"
$R kill @e[type=!player] >/dev/null
$R forceload add -16 24 48 80 >/dev/null || true
$R fill -10 -63 30 40 -62 70 minecraft:dirt >/dev/null
$R fill -10 -61 30 40 -61 70 minecraft:grass_block >/dev/null
$R fill -10 -60 30 40 -45 70 minecraft:air >/dev/null
$R weather clear >/dev/null; $R gamerule doDaylightCycle false >/dev/null; $R gamerule doMobSpawning false >/dev/null
Y=-60
i=0
for f in russia ussr germany usa uk france china japan balkenkreuz vdv vmf vks nato un white victory; do
  x=$(( (i % 8) * 3 )); z=$(( 40 + (i / 8) * 6 ))
  $R setblock $x $Y $z "aiarsenal:flagpole[top=false,bottom=true]" >/dev/null
  $R setblock $x $((Y+1)) $z "aiarsenal:flagpole[top=false,bottom=false]" >/dev/null
  $R setblock $x $((Y+2)) $z "aiarsenal:flagpole[top=false,bottom=false]" >/dev/null
  $R setblock $x $((Y+3)) $z "aiarsenal:flag_$f[facing=east,top=true,bottom=false]" >/dev/null
  i=$((i+1))
done
i=0
for a in armor_plate armor_plate_hazard armor_plate_star_red armor_plate_balkenkreuz armor_plate_us_star armor_plate_roundel armor_plate_vdv armor_plate_vmf; do
  st="aiarsenal:$a"; [ "$a" != armor_plate ] && [ "$a" != armor_plate_hazard ] && st="aiarsenal:$a[facing=south]"
  $R setblock $((26 + i % 6)) $((Y + i / 6)) 52 "$st" >/dev/null
  i=$((i+1))
done
# lamps (row z=58; a stone wall at z=61 carries the wall lamps, a stone lintel the hanging lantern)
$R fill 0 $Y 61 14 $((Y+4)) 61 minecraft:stone_bricks >/dev/null
$R setblock 1 $Y 58 "aiarsenal:lantern_hurricane[hanging=false]" >/dev/null
$R setblock 3 $Y 58 "aiarsenal:lamp_bulkhead[facing=up]" >/dev/null
$R setblock 5 $Y 58 "aiarsenal:lamp_flood[facing=south]" >/dev/null
$R setblock 7 $Y 58 "aiarsenal:beacon_red" >/dev/null
$R setblock 9 $Y 58 "aiarsenal:beacon_rotating" >/dev/null
$R setblock 11 $((Y+2)) 60 "aiarsenal:lamp_street[facing=north]" >/dev/null
$R setblock 13 $((Y+1)) 60 "aiarsenal:lamp_bulkhead[facing=north]" >/dev/null
$R fill 0 $((Y+3)) 60 2 $((Y+3)) 60 minecraft:stone_bricks >/dev/null
$R setblock 1 $((Y+2)) 60 "aiarsenal:lantern_hurricane[hanging=true]" >/dev/null
# searchlights: one sweeping across the field, one fixed on the wall
$R setblock 20 $Y 64 "aiarsenal:searchlight[facing=north,lit=true]" >/dev/null
$R data merge block 20 $Y 64 '{Mode:2,Yaw:335f,Pitch:6f}' >/dev/null
$R setblock 6 $Y 66 "aiarsenal:searchlight[facing=north,lit=true]" >/dev/null
$R data merge block 6 $Y 66 '{Mode:1,Yaw:0f,Pitch:2f}' >/dev/null
# a few r12/r13 field blocks next to the lamps: night lighting check (they must NOT glow on their own)
$R fill 16 $Y 58 18 $Y 58 aiarsenal:sandbags >/dev/null
$R setblock 16 $Y 56 aiarsenal:fuel_drum_olive >/dev/null
$R fill 17 $Y 55 19 $Y 55 aiarsenal:egoza >/dev/null
$R setblock 18 $Y 56 aiarsenal:mine_tm62 >/dev/null
echo SCENE_OK
