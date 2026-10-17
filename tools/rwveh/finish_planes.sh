#!/bin/bash
# r11 planes: floatcheck -> quantized full GLB -> LOD1 into the mod resources (plane .blend names have no rw_ prefix)
B=/home/user/.cache/blender/blender-5.2.2-linux-x64/blender
R=/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb
mkdir -p /home/user/.cache/rwveh/lod $R
for v in ${@:-bf109 yak3 he111 pe8}; do
  echo "== $v"
  $B -b /home/user/models/$v.blend --python /home/user/tools/rwveh/floatcheck.py -- 0.02 2>&1 | grep -E "FLOATCHECK|FLOAT |NODE " | head -12
  python3 /home/user/tools/rwveh/glb_quant.py /home/user/.cache/rwveh/raw/$v.glb $R/$v.glb
  raw=/home/user/.cache/rwveh/lod/${v}_lod1.glb
  timeout 900 $B -b /home/user/models/$v.blend --python /home/user/tools/rwveh/make_lod.py -- $raw 1.0 2>&1 | grep -E "LOD1|Error|Traceback"
  STRIP_IMAGES=1 python3 /home/user/tools/rwveh/glb_quant.py $raw $R/${v}_lod1.glb
  echo "  -> $(stat -c %s $R/$v.glb) / lod $(stat -c %s $R/${v}_lod1.glb) B"
done
echo FINISH_DONE
