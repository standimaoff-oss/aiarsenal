#!/bin/bash
# usage: tools/fps_scene.sh near|far|empty <out.png>  — 3 tanks in front of the player, F3 FPS screenshot after 12 s
D=${1:-near}; OUT=${2:-shots/fps.png}
case $D in near) Z=13;; far) Z=48;; empty) Z=0;; esac
{
echo "time set 6000"; echo "gamerule doDaylightCycle false"; echo "weather clear"
echo "forceload add -64 -16 64 96"
echo "kill @e[type=!player]"
if [ "$D" != empty ]; then
echo "summon aiarsenal:t90m 0 -60 $Z {Rotation:[30f,0f]}"
echo "summon aiarsenal:abrams -7 -60 $((Z+2)) {Rotation:[60f,0f]}"
echo "summon aiarsenal:msta 7 -60 $((Z+2)) {Rotation:[-30f,0f]}"
fi
echo "tp TestPlayer 0.5 -60 -1.5 0 8"
} | /home/user/tools/rcon_many.sh > /dev/null
export DISPLAY=:99
W=$(xdotool search --name "Minecraft" | head -1); xdotool windowactivate --sync $W 2>/dev/null; xdotool windowfocus --sync $W 2>/dev/null
sleep 12
/home/user/tools/screenshot.sh "$OUT" > /dev/null
