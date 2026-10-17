#!/bin/bash
# r20: rebuild the Ratte (interior rack moved off the side door) -> GLB + LOD -> walk collision (.deck)
set -x
cd /home/user
[ "$1" = notex ] || { python3 tools/blender/air_textures.py | tail -1; python3 tools/blender/arsenal_textures.py | tail -1; python3 tools/blender/ww2_textures.py | tail -1; }
bash tools/rwveh/run.sh tools/rwveh/ratte.py 2>&1 | tail -30
bash tools/rwveh/finish_r8.sh ratte
python3 tools/deck/gen_boxes.py ratte 2>&1 | tail -5
echo RATTE_EXIT $?
