#!/bin/bash
# live-tune handheld_view.json: tools/tune_view.sh '<python dict updates>' out.png [rcon tp]
SRC=/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/handheld_view.json
python3 - "$1" <<P
import json,sys
d=json.load(open("$SRC")); d.update(eval(sys.argv[1])); json.dump(d,open("$SRC",'w'),indent=1)
P
cp $SRC /home/user/mods/aiarsenal/build/resources/main/assets/aiarsenal/handheld_view.json
export DISPLAY=:99
xdotool keydown F3 key t keyup F3; sleep 7
[ -n "$3" ] && echo "$3" | /home/user/tools/rcon_many.sh >/dev/null && sleep 1.5
/home/user/tools/screenshot.sh "$2" >/dev/null
