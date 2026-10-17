#!/bin/bash
# r8 WW2 vehicles: floatcheck -> quantized full GLB -> LOD1 (decimated, textures stripped) into the mod resources
B=/home/user/.cache/blender/blender-5.2.2-linux-x64/blender
R=/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb
mkdir -p /home/user/.cache/rwveh/lod $R
for v in ${@:-pz2 pz4 tiger sturmtiger maus ratte t34 kv2 is2 kv5 bm13}; do
  echo "== $v"
  $B -b /home/user/models/rw_$v.blend --python /home/user/tools/rwveh/floatcheck.py -- 0.02 2>&1 | grep -E "FLOATCHECK|FLOAT |NODE " | head -8
  python3 /home/user/tools/rwveh/glb_quant.py /home/user/.cache/rwveh/raw/rw_$v.glb $R/$v.glb
  K=1.0; [ $v = ratte ] && K=0.8
  raw=/home/user/.cache/rwveh/lod/rw_${v}_lod1.glb
  timeout 900 $B -b /home/user/models/rw_$v.blend --python /home/user/tools/rwveh/make_lod.py -- $raw $K 2>&1 | grep -E "LOD1|Error|Traceback"
  STRIP_IMAGES=1 python3 /home/user/tools/rwveh/glb_quant.py $raw $R/${v}_lod1.glb
  echo "  -> $(stat -c %s $R/$v.glb) / lod $(stat -c %s $R/${v}_lod1.glb) B"
done
echo FINISH_DONE
