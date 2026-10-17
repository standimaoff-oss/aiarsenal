#!/bin/bash
# tools/tune3pb.sh '<V>' name : front + back(15deg) views, camera assumed in FIRST person at start
V="$1"; N=$2; export DISPLAY=:99
/home/user/tools/tune_view.sh "{'rpg7.thirdperson_righthand': $V, 'igla.thirdperson_righthand': $V, 'javelin.thirdperson_righthand': $V}" /tmp/x.png "tp TestPlayer 4.5 -60 -2.5 0 15"
xdotool key F5; sleep 2; /home/user/tools/screenshot.sh /home/user/shots/hand/${N}_b.png >/dev/null
xdotool key F5; echo "tp TestPlayer 4.5 -60 -2.5 0 0" | /home/user/tools/rcon_many.sh >/dev/null; sleep 2; /home/user/tools/screenshot.sh /home/user/shots/hand/${N}_f.png >/dev/null
xdotool key F5; sleep 3
cd /home/user/shots/hand && convert ${N}_f.png -crop 240x240+280+160 -resize 300x300 a.png && convert ${N}_b.png -crop 300x300+250+120 -resize 300x300 b.png && convert a.png b.png +append $N.png && rm -f a.png b.png ${N}_f.png ${N}_b.png; ls $N.png
