"""Find floating (unconnected) parts in a vehicle .blend.

usage: blender -b model.blend --python tools/rwveh/floatcheck.py -- [tol_m] [ignore_regex]

Every mesh object is split into connected components (loose parts). A component counts as attached when at least one
of its sample points (vertices, edge midpoints, face centres) has geometry of ANOTHER component within tol (default
1.5 cm). Track links, wheels and other moving nodes are checked too, but reported in a separate list (they ride on
their own pivots). Output: one line per floating part with owner object, centre (Blender coords), size and the
distance to the nearest foreign geometry.
"""
import bpy, bmesh, sys, re
from mathutils import Vector
from mathutils.bvhtree import BVHTree

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
TOL = float(argv[0]) if argv else 0.015
IGN = re.compile(argv[1]) if len(argv) > 1 else re.compile(r"_link_|Cradle|cradle")

dg = bpy.context.evaluated_depsgraph_get()
verts, faces, fcomp = [], [], []          # global BVH data; fcomp: component id per face
comps = []                                 # (owner, sample points, bbmin, bbmax)
for ob in bpy.context.scene.objects:
    if ob.type != 'MESH' or IGN.search(ob.name) or ob.hide_render:
        continue
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    bm = bmesh.new(); bm.from_mesh(me); ev.to_mesh_clear()
    bm.transform(ob.matrix_world)
    bm.verts.ensure_lookup_table(); bm.faces.ensure_lookup_table()
    seen = set()
    for f0 in bm.faces:
        if f0.index in seen:
            continue
        stack, cf = [f0], []
        seen.add(f0.index)
        while stack:
            f = stack.pop(); cf.append(f)
            for e in f.edges:
                for g in e.link_faces:
                    if g.index not in seen:
                        seen.add(g.index); stack.append(g)
        cid = len(comps)
        vs = {v for f in cf for v in f.verts}
        es = {e for f in cf for e in f.edges}
        pts = [v.co.copy() for v in vs] + [(e.verts[0].co + e.verts[1].co) / 2 for e in es] + [f.calc_center_median() for f in cf]
        lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
        hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
        comps.append((ob.name, pts, lo, hi, len(cf)))
        vmap = {}
        for f in cf:
            idx = []
            for v in f.verts:
                if v.index not in vmap:
                    vmap[v.index] = len(verts); verts.append(v.co.copy())
                idx.append(vmap[v.index])
            faces.append(idx); fcomp.append(cid)
    bm.free()

tree = BVHTree.FromPolygons(verts, faces, all_triangles=False)


par = list(range(len(comps)))
def find(a):
    while par[a] != a:
        par[a] = par[par[a]]; a = par[a]
    return a
for cid, (owner, pts, lo, hi, nf) in enumerate(comps):
    for p in pts:
        for co, n, fi, d in tree.find_nearest_range(p, TOL):
            o = fcomp[fi]
            if o != cid:
                par[find(o)] = find(cid)
clusters = {}
for cid in range(len(comps)):
    clusters.setdefault(find(cid), []).append(cid)
main = max(clusters.values(), key=lambda c: sum(comps[i][4] for i in c))
mset = set(main)
print("FLOATCHECK %s: %d components, %d clusters, main %d comps (tol %.3f m)" % (bpy.data.filepath.split("/")[-1], len(comps), len(clusters), len(main), TOL))
by_owner = {}
for cid, (owner, *_r) in enumerate(comps):
    by_owner.setdefault(owner, set()).add(cid)
rows = []
for root, cl in clusters.items():
    if cl is main:
        continue
    owners = sorted({comps[i][0] for i in cl})
    whole = all(by_owner[o] <= set(cl) for o in owners)   # cluster = complete moving node(s), e.g. a wheel
    lo = Vector((min(comps[i][2].x for i in cl), min(comps[i][2].y for i in cl), min(comps[i][2].z for i in cl)))
    hi = Vector((max(comps[i][3].x for i in cl), max(comps[i][3].y for i in cl), max(comps[i][3].z for i in cl)))
    # distance from the cluster to the main cluster
    best = 9.99
    for i in cl:
        for p in comps[i][1][::3]:
            for r in (0.05, 0.15, 0.5):
                hit = [d for co, n, fi, d in tree.find_nearest_range(p, r) if fcomp[fi] in mset]
                if hit:
                    best = min(best, min(hit)); break
    rows.append((whole, owners, (lo + hi) / 2, hi - lo, len(cl), best))
tiny = [r for r in rows if max(r[3]) < 0.05]
print("  (%d tiny floating clusters < 5 cm, e.g. bolts: %s)" % (len(tiny), ",".join(sorted({o for r in tiny for o in r[1]}))[:120]))
for whole, owners, c, sz, n, d in sorted([r for r in rows if max(r[3]) >= 0.05], key=lambda r: (r[0], -r[5])):
    print("  %s %-34s at (%6.2f %6.2f %6.2f) size (%.2f %.2f %.2f) parts %3d gap-to-body %.3f" % ("NODE " if whole else "FLOAT", ",".join(owners)[:34], c.x, c.y, c.z, sz.x, sz.y, sz.z, n, d))
