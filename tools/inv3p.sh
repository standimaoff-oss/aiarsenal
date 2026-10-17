#!/bin/bash
# inv3p.sh '<V>' name [gun] : third-person check in the survival inventory (player faces left, side profile)
export DISPLAY=:99
G=${3:-ak12}
python3 /home/user/tools/gun_view.py "{'$G.thirdperson_righthand':$1}"; sleep 2
xdotool key e; sleep 0.8; xdotool mousemove 30 230; sleep 0.8
import -window root /tmp/inv.png; xdotool key e; sleep 0.5
convert /tmp/inv.png -crop 180x130+205+150 -scale 250% -quality 90 /home/user/logs/$2.jpg
