#!/bin/bash
# LOD1 for the arsenal/bm21 renderer models (assets/aiarsenal/glb/<name>_lod1.glb)
B=/home/user/.cache/blender/blender-5.2.2-linux-x64/blender
R=/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/glb
mkdir -p /home/user/.cache/rwveh/lod
for spec in bm21_grad:bm21 tu95ms:tu95ms tu160:tu160; do
  bl=${spec%%:*}; dst=${spec#*:}
  raw=/home/user/.cache/rwveh/lod/${dst}_lod1.glb
  timeout 900 $B -b /home/user/models/$bl.blend --python /home/user/tools/rwveh/make_lod.py -- $raw 2>&1 | grep -E "LOD1|Error|Traceback"
  python3 - $R/$dst.glb $raw <<'PY'
import json,struct,sys
def js(p):
    b=open(p,'rb').read(); n=struct.unpack('<I',b[12:16])[0]; return json.loads(b[20:20+n])
a,l=js(sys.argv[1]),js(sys.argv[2])
an={n.get('name') for n in a['nodes']}
miss=[n.get('name') for n in l['nodes'] if 'mesh' in n and n.get('name') not in an]
print('  nodes main',len(a['nodes']),'lod',len(l['nodes']),'missing mesh nodes',len(miss),miss[:6],'skins',len(a.get('skins',[])))
PY
  STRIP_IMAGES=1 python3 /home/user/tools/rwveh/glb_quant.py $raw $R/${dst}_lod1.glb
done
echo LODS2_DONE
