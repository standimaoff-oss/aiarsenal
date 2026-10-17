#!/bin/bash
# sheet.sh <out.png> "ROOT:yaw:pitch:dist:tx:ty:tz" ...   -> grid of viewport shots (TEX, culling on), one root visible per tile
OUT=$1; shift
TMP=/home/user/shots/_tiles; mkdir -p $TMP; rm -f $TMP/*.png
i=0
for spec in "$@"; do
  IFS=: read R Y P D X Yy Z C <<< "$spec"
  CL=${C%@*}; FR=${C#*@}; [ -z "$C" ] && { CL=""; FR=0; }
  python3 /home/user/tools/mcp.py code "ONLY=['$R']
exec(open('/home/user/tools/blender/only.py').read())
exec(open('/home/user/tools/blender/pose.py').read())
pose('$CL'.split('+'), $FR)" >/dev/null 2>&1
  sleep 0.7
  CULL=1 /home/user/tools/blender/vs.sh $TMP/t$(printf %02d $i).png $Y $P $D $X $Yy $Z TEX >/dev/null
  i=$((i+1))
done
sleep 1
python3 - "$OUT" <<'PY'
import sys, glob
from PIL import Image, ImageDraw
fs = sorted(glob.glob('/home/user/shots/_tiles/t*.png'))
import numpy as np
def crop(f):
    im = Image.open(f).convert('RGB')
    a = np.asarray(im).astype(int)
    bg = a[2, 2]
    m = np.abs(a - bg).sum(2) > 12
    ys, xs = np.where(m)
    if len(xs) == 0:
        return im.resize((500, 334))
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    w, h = x1 - x0, y1 - y0
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    W = max(w * 1.08, h * 1.08 * 1.5)
    H = W / 1.5
    box = (int(cx - W / 2), int(cy - H / 2), int(cx + W / 2), int(cy + H / 2))
    return im.crop(box).resize((500, 334))
ims = [crop(f) for f in fs]
cols = 2 if len(ims) <= 4 else 3
rows = (len(ims) + cols - 1) // cols
sh = Image.new('RGB', (cols * 500, rows * 334), (40, 40, 40))
for k, im in enumerate(ims):
    sh.paste(im, ((k % cols) * 500, (k // cols) * 334))
sh.save(sys.argv[1]); print("sheet", sys.argv[1], len(ims))
PY
