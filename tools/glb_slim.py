#!/usr/bin/env python3
"""Re-encode opaque PNG textures inside a GLB as JPEG (keeps alpha textures as PNG) and repack the binary chunk.
usage: glb_slim.py in.glb out.glb [quality]"""
import io, json, struct, sys
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
q = int(sys.argv[3]) if len(sys.argv) > 3 else 85
d = open(src, 'rb').read()
jl = struct.unpack('<I', d[12:16])[0]
j = json.loads(d[20:20 + jl])
o = 20 + jl
bl = struct.unpack('<I', d[o:o + 4])[0]
binc = d[o + 8:o + 8 + bl]
views = [binc[v.get('byteOffset', 0):v.get('byteOffset', 0) + v['byteLength']] for v in j['bufferViews']]
for im in j.get('images', []):
    if im.get('mimeType') != 'image/png':
        continue
    data = views[im['bufferView']]
    img = Image.open(io.BytesIO(data))
    has_alpha = img.mode in ('RGBA', 'LA') and img.getchannel('A').getextrema()[0] < 250
    if has_alpha:
        continue
    out = io.BytesIO()
    img.convert('RGB').save(out, 'JPEG', quality=q, optimize=True, progressive=False)
    if out.tell() < len(data):
        print("  %-12s png %7d -> jpg %7d" % (im.get('name'), len(data), out.tell()))
        views[im['bufferView']] = out.getvalue()
        im['mimeType'] = 'image/jpeg'
blob = bytearray()
for v, data in zip(j['bufferViews'], views):
    while len(blob) % 4:
        blob.append(0)
    v['byteOffset'] = len(blob)
    v['byteLength'] = len(data)
    blob += data
while len(blob) % 4:
    blob.append(0)
j['buffers'][0]['byteLength'] = len(blob)
js = json.dumps(j, separators=(',', ':')).encode()
while len(js) % 4:
    js += b' '
total = 12 + 8 + len(js) + 8 + len(blob)
with open(dst, 'wb') as f:
    f.write(struct.pack('<III', 0x46546C67, 2, total))
    f.write(struct.pack('<I', len(js)) + b'JSON' + js)
    f.write(struct.pack('<I', len(blob)) + b'BIN\x00' + blob)
print("%s: %d -> %d bytes" % (dst, len(d), total))
