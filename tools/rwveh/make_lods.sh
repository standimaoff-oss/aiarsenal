#!/bin/bash
# LOD1 GLBs for the heavy Real War renderer models: blend -> Blender decimate -> strip textures -> quantize
B=/home/user/.cache/blender/blender-5.2.2-linux-x64/blender
R=/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal
mkdir -p /home/user/.cache/rwveh/lod
for spec in rw_t90m:models/glb/t90m rw_abrams:models/glb/abrams rw_bmp3:models/glb/bmp3 rw_msta:models/glb/msta \
            rw_s400:models/glb/s400 rw_smerch:models/glb/smerch rw_radar_mobile:models/glb/radar_mobile \
            rw_grad:models/glb/grad rw_radar_post:models/glb/radar_post yarstel:glb/yarstel; do
  bl=${spec%%:*}; dst=${spec#*:}
  raw=/home/user/.cache/rwveh/lod/${bl}_lod1.glb
  timeout 600 $B -b /home/user/models/$bl.blend --python /home/user/tools/rwveh/make_lod.py -- $raw 2>&1 | grep -E "LOD1|Error|Traceback"
  STRIP_IMAGES=1 python3 /home/user/tools/rwveh/glb_quant.py $raw $R/${dst}_lod1.glb && echo "  -> ${dst}_lod1.glb $(stat -c %s $R/${dst}_lod1.glb) B"
done
echo LODS_DONE
