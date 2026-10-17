#!/usr/bin/env bash
# r13 QA scene: every fortification + field block on a flat grass pad (ground top y=-61, blocks at y=-60).
R="python3 /home/user/tools/rcon.py"
$R kill @e[type=!player] >/dev/null
$R forceload add -32 -32 48 48 >/dev/null || true
$R fill -10 -63 -10 40 -62 30 minecraft:dirt >/dev/null
$R fill -10 -61 -10 40 -61 30 minecraft:grass_block >/dev/null
$R fill -10 -60 -10 40 -50 30 minecraft:air >/dev/null
$R time set noon >/dev/null; $R weather clear >/dev/null; $R gamerule doDaylightCycle false >/dev/null
Y=-60
$R fill 0 $Y 0 4 $Y 0 aiarsenal:egoza >/dev/null
$R setblock 7 $Y 0 aiarsenal:egoza >/dev/null
$R fill 0 $Y 3 4 $Y 3 aiarsenal:bruno_spiral >/dev/null
$R setblock 7 $Y 3 aiarsenal:bruno_spiral >/dev/null
$R fill 0 $Y 6 4 $Y 6 aiarsenal:concertina_wire >/dev/null
$R fill 0 $Y 8 4 $Y 8 aiarsenal:barbed_wire >/dev/null
$R fill 0 $Y 10 4 $Y 10 aiarsenal:sandbags >/dev/null
$R setblock 10 $Y 0 aiarsenal:fuel_drum_olive >/dev/null
$R setblock 11 $Y 0 aiarsenal:fuel_drum_red >/dev/null
$R setblock 12 $Y 0 aiarsenal:fuel_drum_blue >/dev/null
$R setblock 10 $Y 1 "aiarsenal:fuel_drum_olive[lying=true,facing=east]" >/dev/null
$R setblock 12 $Y 1 "aiarsenal:fuel_drum_red[lying=true]" >/dev/null
i=0; for b in mine_pmn2 mine_tm62 mine_mon50 mine_ozm72 tripwire_f1 bear_trap; do $R setblock $((10+i)) $Y 4 aiarsenal:$b >/dev/null; i=$((i+1)); done
i=0; for b in warning_sign_mines warning_sign_radiation warning_sign_voltage warning_sign_biohazard warning_sign_checkpoint warning_sign_noentry; do $R setblock $((10+i)) $Y 7 "aiarsenal:$b[facing=south]" >/dev/null; i=$((i+1)); done
$R setblock 10 $Y 10 aiarsenal:czech_hedgehog >/dev/null
$R setblock 12 $Y 10 aiarsenal:dragon_teeth >/dev/null
$R setblock 14 $Y 10 aiarsenal:concrete_barrier >/dev/null
$R setblock 16 $Y 10 aiarsenal:camo_net >/dev/null
echo SCENE_OK
