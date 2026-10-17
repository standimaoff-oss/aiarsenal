#!/bin/bash
# tools/tune_gun3p.sh '<V list>' name -> logs/<name>.jpg (front | top-back); camera must be in FRONT view
V="$1"; N=$2
bash /home/user/tools/tune_view.sh "{'ak12.thirdperson_righthand': $V, 'm4a1.thirdperson_righthand': $V}" /tmp/${N}_f.png "tp TestPlayer 0.5 -60 0.5 0 0"
export DISPLAY=:99
xdotool key F5; sleep 0.5; xdotool key F5; python3 /home/user/tools/rcon.py "tp TestPlayer 0.5 -60 0.5 0 70" >/dev/null; sleep 2
import -window root /tmp/${N}_b.png; xdotool key F5
cd /tmp && convert ${N}_f.png -crop 300x300+250+160 a.png && convert ${N}_b.png -crop 300x300+250+120 b.png && convert a.png b.png +append -quality 88 /home/user/logs/$N.jpg
