#!/usr/bin/env python3
"""Print node hierarchy with world translation and world-space mesh AABB (glTF coords: x left, y up, z forward)."""
import json, struct, sys
import numpy as np
def load(p):
    b = open(p, 'rb').read(); L = struct.unpack('<I', b[12:16])[0]
    j = json.loads(b[20:20 + L]); o = 20 + L; bl = struct.unpack('<I', b[o:o + 4])[0]; bin_ = b[o + 8:o + 8 + bl]
    return j, bin_
def acc(j, bin_, i):
    a = j['accessors'][i]; bv = j['bufferViews'][a['bufferView']]
    n = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[a['type']]
    off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    return np.frombuffer(bin_, dtype=np.float32, count=a['count'] * n, offset=off).reshape(-1, n)
def qm(q):
    x, y, z, w = q
    return np.array([[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)], [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)], [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]])
j, bin_ = load(sys.argv[1]); nodes = j['nodes']
par = {}
for i, n in enumerate(nodes):
    for c in n.get('children', []): par[c] = i
def world(i):
    n = nodes[i]; M = np.eye(4)
    M[:3, :3] = qm(n.get('rotation', [0, 0, 0, 1])) @ np.diag(n.get('scale', [1, 1, 1])); M[:3, 3] = n.get('translation', [0, 0, 0])
    return world(par[i]) @ M if i in par else M
def depth(i): return depth(par[i]) + 1 if i in par else 0
order = []
def walk(i):
    order.append(i)
    for c in nodes[i].get('children', []): walk(c)
for r in j['scenes'][0]['nodes']: walk(r)
filt = sys.argv[2].split(',') if len(sys.argv) > 2 else None
for i in order:
    n = nodes[i]; nm = n.get('name', '?')
    if filt and not any(nm.startswith(f) for f in filt): continue
    W = world(i); s = '  ' * depth(i) + '%-16s t=%s' % (nm, np.round(W[:3, 3], 3).tolist())
    if 'rotation' in n and n['rotation'] != [0, 0, 0, 1]: s += ' r=%s' % np.round(n['rotation'], 3).tolist()
    if 'mesh' in n:
        pts = np.vstack([acc(j, bin_, p['attributes']['POSITION']) for p in j['meshes'][n['mesh']]['primitives']])
        P = (W[:3, :3] @ pts.T).T + W[:3, 3]
        s += ' box=%s..%s' % (np.round(P.min(0), 2).tolist(), np.round(P.max(0), 2).tolist())
    print(s)
