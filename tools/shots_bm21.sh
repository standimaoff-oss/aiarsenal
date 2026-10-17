#!/usr/bin/env bash
# Full screenshot set for bm21grad (TZ §8). Requires server + client running and TestPlayer online.
set -uo pipefail
cd /home/user/tools; export DISPLAY=:99
O=/home/user/shots
W=$(xdotool search --name Minecraft | head -1)
key() { xdotool windowfocus $W; xdotool key --window $W "$@"; }
V='@e[type=bm21grad:bm21,limit=1]'
./scene_day.sh --mod bm21grad --preset base; ./spawn.sh; ./r recipe give TestPlayer '*' >/dev/null
./r bm21 load $V 9m22u >/dev/null
./r tp TestPlayer 7 -58.5 10 145 8 >/dev/null; ./r item replace entity TestPlayer hotbar.0 with bm21grad:bm21 >/dev/null
sleep 4; ./screenshot.sh $O/01-spawn.png
# inventory: creative tab of the mod (page 2) + tooltip of the vehicle item
key e; sleep 1.5; xdotool mousemove --window $W 573 83 click 1; sleep 0.7; xdotool mousemove --window $W 229 140 click 1; sleep 0.7
xdotool mousemove --window $W 237 215; sleep 1; ./screenshot.sh $O/03-items.png
xdotool mousemove --window $W 310 215; sleep 1; ./screenshot.sh $O/03-items-rocket.png
key Escape; sleep 1
./r item replace entity TestPlayer weapon.mainhand with air >/dev/null
key F1
./r tp TestPlayer 6.5 -58.3 8.5 145 6 >/dev/null; sleep 3; ./screenshot.sh $O/02-vehicle.png
./r tp TestPlayer -7 -58.2 -9 -35 6 >/dev/null; sleep 3; ./screenshot.sh $O/02-vehicle-rear.png
./r bm21 aim $V 35 40 >/dev/null; sleep 11
./r tp TestPlayer 8 -57.8 -9 60 8 >/dev/null; sleep 3; ./screenshot.sh $O/05-feature-combat-position.png
./r tp TestPlayer 4.5 -58.5 5.5 145 5 >/dev/null; sleep 3; ./screenshot.sh $O/05-feature-cab-shield.png
# salvo
./r forceload add -270 -20 -230 20 >/dev/null
./r bm21 target $V -250 -61 0 >/dev/null
./r tp TestPlayer 12 -57.5 12 135 -4 >/dev/null
./r bm21 fire $V >/dev/null
for i in $(seq 1 60); do ./r bm21 status $V | grep -q 'phase=FIRING' && break; sleep 0.5; done
sleep 2; ./screenshot.sh $O/05-feature-salvo-1.png; sleep 1; ./screenshot.sh $O/05-feature-salvo-2.png
sleep 22; ./r bm21 stow $V >/dev/null
# warheads
./r fill -30 -63 -70 30 -62 -20 minecraft:dirt >/dev/null; ./r fill -30 -61 -70 30 -61 -20 minecraft:grass_block >/dev/null
./r fill -30 -60 -70 30 -50 -20 minecraft:air >/dev/null
./r tp TestPlayer 12 -58 -22 146 8 >/dev/null; sleep 3
./strike_shot.sh 9m22u 0 -61 -40 $O/05-feature-he-frag.png 0.1; sleep 6
./strike_shot.sh 9m28f -6 -61 -48 $O/05-feature-he-blast.png 0.1; sleep 6
./screenshot.sh $O/05-feature-craters.png
./strike_shot.sh 9m22s 6 -61 -45 $O/05-feature-incendiary.png 1.5; sleep 6
./strike_shot.sh 9m218 -3 -61 -45 $O/05-feature-cluster.png 1.0; sleep 8
./strike_shot.sh 9m43 0 -61 -40 $O/05-feature-smoke.png 8
key F1
# driver HUD + fire control computer
./r kill @e[type=bm21grad:smoke_screen] >/dev/null
./r bm21 target $V -120 -61 -200 >/dev/null
./r tp TestPlayer 0 -60 3 180 10 >/dev/null; ./r ride TestPlayer mount $V >/dev/null; sleep 3
./screenshot.sh $O/05-feature-hud-driver.png
key c; sleep 2; xdotool mousemove --window $W 150 250; sleep 1; ./screenshot.sh $O/06-gui-fire-control.png
key Escape; sleep 1
key F5; sleep 1
xdotool keydown --window $W w; sleep 2.5; ./screenshot.sh $O/05-feature-driving.png; xdotool keyup --window $W w; sleep 2
key F5; key F5
./r ride TestPlayer dismount >/dev/null
./r tp $V 0 -60 0 0 0 >/dev/null; sleep 1
# destruction
./r tp TestPlayer 9 -58 8 135 10 >/dev/null
./r bm21 damage $V 5000 >/dev/null; sleep 6; key F1; ./screenshot.sh $O/05-feature-destroyed.png; key F1
