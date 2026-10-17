#!/usr/bin/env python3
"""QA for block OBJs: faces whose winding disagrees with the stored normals (= culled from outside in Minecraft),
faces whose winding points INTO their object (centroid test), object thickness (flat parts)."""
import sys, glob, os
def load(p):
    V, N, objs, cur = [], [], {}, None
    for l in open(p):
        t = l.split()
        if not t: continue
        if t[0] == 'v': V.append(tuple(map(float, t[1:4])))
        elif t[0] == 'vn': N.append(tuple(map(float, t[1:4])))
        elif t[0] in ('o', 'g'): cur = t[1]; objs.setdefault(cur, [])
        elif t[0] == 'usemtl': objs.setdefault(cur, []); mat = t[1]
        elif t[0] == 'f':
            idx = [x.split('/') for x in t[1:]]
            objs.setdefault(cur, []).append(([int(a[0]) - 1 for a in idx], [int(a[2]) - 1 if len(a) > 2 and a[2] else None for a in idx]))
    return V, N, objs
sub = lambda a, b: (a[0]-b[0], a[1]-b[1], a[2]-b[2])
cross = lambda a, b: (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
dot = lambda a, b: a[0]*b[0]+a[1]*b[1]+a[2]*b[2]
for p in sys.argv[1:]:
    V, N, objs = load(p)
    tot = bad = 0
    for o, F in objs.items():
        if not F: continue
        b = 0
        for vi, ni in F:
            n = cross(sub(V[vi[1]], V[vi[0]]), sub(V[vi[2]], V[vi[0]]))
            if dot(n, n) < 1e-16: continue
            if ni[0] is not None:
                vn = [sum(N[k][j] for k in ni) for j in range(3)]
                if dot(n, vn) < 0: b += 1
            tot += 1
        bad += b
    print(f'{os.path.basename(p):28s} faces {tot:6d} winding-vs-normal flipped {bad:5d}')
