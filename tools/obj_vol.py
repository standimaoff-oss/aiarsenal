#!/usr/bin/env python3
"""Per connected component (shared vertex positions) signed volume: < 0 means the whole piece faces inward
(invisible from outside in MC); also reports open (non-closed) components = single-sided surfaces."""
import sys, os
from collections import defaultdict
def comps(p):
    V, F = [], []
    for l in open(p):
        t = l.split()
        if not t: continue
        if t[0] == 'v': V.append(tuple(round(float(x), 5) for x in t[1:4]))
        elif t[0] == 'f': F.append([int(a.split('/')[0]) - 1 for a in t[1:]])
    key = [V[i] for i in range(len(V))]
    par = {}
    def f(x):
        while par.setdefault(x, x) != x: par[x] = par[par[x]]; x = par[x]
        return x
    for fc in F:
        for a in fc[1:]: par[f(key[a])] = f(key[fc[0]])
    G = defaultdict(list)
    for fc in F: G[f(key[fc[0]])].append(fc)
    out = []
    for r, fs in G.items():
        vol = 0.0; edges = defaultdict(int); mn = [9e9]*3; mx = [-9e9]*3
        for fc in fs:
            P = [V[i] for i in fc]
            for q in P:
                for j in range(3): mn[j] = min(mn[j], q[j]); mx[j] = max(mx[j], q[j])
            for k in range(1, len(P) - 1):
                a, b, c = P[0], P[k], P[k+1]
                vol += (a[0]*(b[1]*c[2]-b[2]*c[1]) - a[1]*(b[0]*c[2]-b[2]*c[0]) + a[2]*(b[0]*c[1]-b[1]*c[0])) / 6
            for k in range(len(P)):
                e = tuple(sorted((P[k], P[(k+1) % len(P)]))); edges[e] += 1
        openE = sum(1 for v in edges.values() if v == 1)
        out.append((len(fs), vol, openE, [mx[j]-mn[j] for j in range(3)], mn))
    return out
for p in sys.argv[1:]:
    cs = comps(p)
    neg = [c for c in cs if c[1] < -1e-9]
    opn = [c for c in cs if c[2] > 0]
    print(f'{os.path.basename(p):26s} comps {len(cs):4d} inward {len(neg):3d} open {len(opn):3d}')
    for c in neg[:4] + opn[:3]:
        print('    faces %d vol %.5f open %d size %.3f %.3f %.3f at %.2f %.2f %.2f' % (c[0], c[1], c[2], *c[3], *c[4]))
