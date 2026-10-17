#!/usr/bin/env python3
"""Shrink a Blender-exported GLB for the Real War loader (GlbModel reads normalized ints):
NORMAL -> BYTE normalized (stride 4, KHR_mesh_quantization), COLOR_0 -> UNSIGNED_BYTE normalized,
POSITION/TEXCOORD floats rounded (better deflate), buffers repacked with 4-byte alignment.
usage: glb_quant.py in.glb out.glb"""
import json, struct, sys
import numpy as np

src, dst = sys.argv[1], sys.argv[2]
d = open(src, 'rb').read()
jl = struct.unpack('<I', d[12:16])[0]
j = json.loads(d[20:20 + jl])
o = 20 + jl
bl = struct.unpack('<I', d[o:o + 4])[0]
binc = d[o + 8:o + 8 + bl]
NC = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}
DT = {5126: np.float32, 5123: np.uint16, 5121: np.uint8, 5125: np.uint32, 5122: np.int16, 5120: np.int8}


def read(ai):
    a = j['accessors'][ai]
    bv = j['bufferViews'][a['bufferView']]
    dt = np.dtype(DT[a['componentType']])
    nc = NC[a['type']]
    stride = bv.get('byteStride', dt.itemsize * nc)
    off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    raw = np.frombuffer(binc, dtype=np.uint8, count=stride * (a['count'] - 1) + dt.itemsize * nc, offset=off)
    out = np.lib.stride_tricks.as_strided(raw, shape=(a['count'], dt.itemsize * nc), strides=(stride, 1)).copy()
    return out.view(dt).reshape(a['count'], nc), a


views = []      # new buffer views: (bytes, stride, target)
new_acc = {}


def add_view(data, stride=None, target=None):
    views.append((data, stride, target))
    return len(views) - 1


done = {}
for m in j['meshes']:
    for p in m['primitives']:
        for k, ai in list(p['attributes'].items()):
            if ai in done:
                continue
            arr, a = read(ai)
            na = dict(a)
            na.pop('byteOffset', None)
            if k == 'NORMAL' and a['componentType'] == 5126:
                q = np.clip(np.round(arr * 127.0), -127, 127).astype(np.int8)
                q4 = np.zeros((len(q), 4), np.int8); q4[:, :3] = q
                na.update(componentType=5120, normalized=True, bufferView=add_view(q4.tobytes(), 4, 34962))
                na.pop('min', None); na.pop('max', None)
            elif k.startswith('COLOR_') and a['componentType'] != 5121:
                f = arr.astype(np.float32) / (65535.0 if a['componentType'] == 5123 else 1.0)
                q = np.clip(np.round(f * 255.0), 0, 255).astype(np.uint8)
                if q.shape[1] == 3:
                    q4 = np.full((len(q), 4), 255, np.uint8); q4[:, :3] = q; q = q4; na['type'] = 'VEC4'
                na.update(componentType=5121, normalized=True, bufferView=add_view(q.tobytes(), 4, 34962))
            elif a['componentType'] == 5126:
                r = np.round(arr.astype(np.float64) * 4096.0) / 4096.0
                na['bufferView'] = add_view(r.astype(np.float32).tobytes(), None, 34962)
                if 'min' in a:
                    na['min'] = [float(v) for v in r.min(0)]; na['max'] = [float(v) for v in r.max(0)]
            else:
                na['bufferView'] = add_view(arr.tobytes(), None, 34962)
            done[ai] = na
        if 'indices' in p and p['indices'] not in done:
            arr, a = read(p['indices'])
            na = dict(a); na.pop('byteOffset', None)
            mx = int(arr.max()) if arr.size else 0
            if mx < 65536:
                arr = arr.astype(np.uint16); na['componentType'] = 5123
            na['bufferView'] = add_view(arr.tobytes(), None, 34963)
            done[p['indices']] = na
# non-mesh accessors (animations / skins) and images keep their bytes
for ai, a in enumerate(j['accessors']):
    if ai not in done and 'bufferView' in a:
        arr, _ = read(ai)
        na = dict(a); na.pop('byteOffset', None)
        na['bufferView'] = add_view(arr.tobytes())
        done[ai] = na
import os
# STRIP_IMAGES=1 (LOD files): textures come from the full model at runtime -> 1x1 PNG stubs keep the JSON valid
TINY_PNG = bytes.fromhex('89504e470d0a1a0a0000000d4948445200000001000000010806000000'
                         '1f15c4890000000d49444154789c63f8ffff3f0005fe02fea7d6a4590000000049454e44ae426082')
for im in j.get('images', []):
    bv = j['bufferViews'][im['bufferView']]
    if os.environ.get('STRIP_IMAGES') == '1':
        im['bufferView'] = add_view(TINY_PNG); im['mimeType'] = 'image/png'
    else:
        im['bufferView'] = add_view(binc[bv.get('byteOffset', 0):bv.get('byteOffset', 0) + bv['byteLength']])
j['accessors'] = [done.get(i, a) for i, a in enumerate(j['accessors'])]
blob = bytearray()
bvs = []
for data, stride, target in views:
    while len(blob) % 4:
        blob += b'\0'
    v = {'buffer': 0, 'byteOffset': len(blob), 'byteLength': len(data)}
    if stride:
        v['byteStride'] = stride
    if target:
        v['target'] = target
    bvs.append(v)
    blob += data
while len(blob) % 4:
    blob += b'\0'
j['bufferViews'] = bvs
j['buffers'] = [{'byteLength': len(blob)}]
for key in ('extensionsUsed', 'extensionsRequired'):
    s = set(j.get(key, [])); s.add('KHR_mesh_quantization'); j[key] = sorted(s)
js = json.dumps(j, separators=(',', ':')).encode()
while len(js) % 4:
    js += b' '
out = struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob)) + struct.pack('<II', len(js), 0x4E4F534A) + js + struct.pack('<II', len(blob), 0x004E4942) + bytes(blob)
open(dst, 'wb').write(out)
print("%s: %d -> %d bytes" % (dst, len(d), len(out)))
