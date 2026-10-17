"""Per-node triangle statistics of a GLB (counts instanced meshes per node): python3 glbstat.py file.glb [top]"""
import json, struct, sys, re, collections
f = open(sys.argv[1], 'rb').read()
n = int(sys.argv[2]) if len(sys.argv) > 2 else 25
jl = struct.unpack('<I', f[12:16])[0]
g = json.loads(f[20:20 + jl])
acc = g['accessors']
def tris(mi):
    t = 0
    for p in g['meshes'][mi]['primitives']:
        t += (acc[p['indices']]['count'] if 'indices' in p else acc[p['attributes']['POSITION']]['count']) // 3
    return t
groups = collections.Counter(); cnt = collections.Counter(); tot = 0; small = 0
for nd in g['nodes']:
    if 'mesh' not in nd: continue
    t = tris(nd['mesh']); tot += t
    key = re.sub(r'[\d.]+$', '', nd.get('name', '?')).rstrip('_.')
    groups[key] += t; cnt[key] += 1
mats = collections.Counter()
for m in g['meshes']:
    for p in m['primitives']: mats[p.get('material', -1)] += 1
print(f"{sys.argv[1]}: nodes {len(g['nodes'])}, meshes {len(g['meshes'])}, rendered tris {tot}, materials {len(g.get('materials', []))}, images {len(g.get('images', []))}, prims {sum(mats.values())}")
for k, v in groups.most_common(n):
    print(f"  {v:7d} tris  x{cnt[k]:3d}  {k}")
