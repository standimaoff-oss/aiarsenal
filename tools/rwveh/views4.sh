#!/bin/bash
# 4-view sheet (side / top / front / rear) of models/rw_$1.blend -> $2 (jpg)
B=/home/user/.cache/blender/blender-5.2.2-linux-x64/blender
v=$1; out=$2; shift 2
for spec in "s 90 0" "t 90 89" "f 0 0" "r 180 0"; do
  set -- $spec
  $B -b /home/user/models/rw_$v.blend --python /home/user/tools/rwveh/preview.py -- /tmp/pv/${v}_$1.jpg $2 $3 480 300 > /tmp/pv/${v}_$1.log 2>&1 &
done
wait
python3 - "$v" "$out" <<'PY'
import sys
from PIL import Image
v, out = sys.argv[1], sys.argv[2]
ims = [Image.open('/tmp/pv/%s_%s.jpg' % (v, k)) for k in 'stfr']
W, H = ims[0].size
sheet = Image.new('RGB', (W * 2, H * 2), 'white')
for i, im in enumerate(ims):
    sheet.paste(im, ((i % 2) * W, (i // 2) * H))
sheet.save(out, quality=82)
PY
