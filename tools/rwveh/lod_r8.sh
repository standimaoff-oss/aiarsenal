#!/bin/bash
# LOD1 only (re-run with a stronger ratio), args: K model...
B=/home/user/.cache/blender/blender-5.2.2-linux-x64/blender
R=/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb
K=$1; shift
for v in "$@"; do
  raw=/home/user/.cache/rwveh/lod/rw_${v}_lod1.glb
  timeout 900 $B -b /home/user/models/rw_$v.blend --python /home/user/tools/rwveh/make_lod.py -- $raw $K 2>&1 | grep -E "LOD1|Error|Traceback"
  STRIP_IMAGES=1 python3 /home/user/tools/rwveh/glb_quant.py $raw $R/${v}_lod1.glb >/dev/null
done
echo LOD_DONE
