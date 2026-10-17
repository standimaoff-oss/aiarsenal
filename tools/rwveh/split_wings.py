# r18: split the outer wing panels out of a plane's joined body mesh into separate nodes wing_L / wing_R so that the
# damage model can tear a wing off in flight (CraftRenderer hides wing_X + ail_X, a DebrisEntity draws them falling).
# The body is a join of loose, separately modelled parts (inner / outer wing panels, nacelles, fillets...), so whole
# loose parts lying entirely outboard of |x| >= xcut and ahead of y <= ymax (Blender y = rearward; keeps the tailplane)
# move to the new objects. usage: blender -b X.blend --python split_wings.py -- <body> <xcut> <ymax>
import bpy, bmesh, sys
a = sys.argv[sys.argv.index('--') + 1:]
body, xcut, ymax = a[0], float(a[1]), float(a[2])
ob = bpy.data.objects[body]
for n in ('wing_L', 'wing_R'):
    if n in bpy.data.objects:
        raise SystemExit("already split: " + n)


def parts_of(bm):
    bm.verts.ensure_lookup_table()
    seen = set()
    out = []
    for v in bm.verts:
        if v.index in seen:
            continue
        st = [v]
        seen.add(v.index)
        comp = []
        while st:
            q = st.pop()
            comp.append(q.index)
            for e in q.link_edges:
                w = e.other_vert(q)
                if w.index not in seen:
                    seen.add(w.index)
                    st.append(w)
        out.append(comp)
    return out


bm = bmesh.new()
bm.from_mesh(ob.data)
side_of = {}
nl = nr = 0
for comp in parts_of(bm):
    xs = [bm.verts[i].co.x for i in comp]
    ys = [bm.verts[i].co.y for i in comp]
    yc = (min(ys) + max(ys)) * 0.5
    if yc > ymax:
        continue
    if min(xs) >= xcut:
        for i in comp: side_of[i] = 'L'
        nl += 1
    elif max(xs) <= -xcut:
        for i in comp: side_of[i] = 'R'
        nr += 1
bm.free()
print("SPLIT parts L=%d R=%d verts=%d" % (nl, nr, len(side_of)))
if nl == 0 or nr == 0:
    raise SystemExit("nothing to split")


def keep_only(o, pred):
    b = bmesh.new()
    b.from_mesh(o.data)
    b.verts.ensure_lookup_table()
    kill = [v for v in b.verts if not pred(v.index)]
    bmesh.ops.delete(b, geom=kill, context='VERTS')
    b.to_mesh(o.data)
    b.free()
    o.data.update()


for side in ('L', 'R'):
    w = ob.copy()
    w.data = ob.data.copy()
    w.name = 'wing_' + side
    w.data.name = 'wing_' + side
    for c in ob.users_collection:
        c.objects.link(w)
    w.parent = ob.parent
    w.matrix_world = ob.matrix_world.copy()
    keep_only(w, lambda i, s=side: side_of.get(i) == s)
    print("WING", w.name, len(w.data.vertices), "verts", [round(v, 2) for v in w.dimensions])
keep_only(ob, lambda i: i not in side_of)
print("BODY", ob.name, len(ob.data.vertices))
bpy.ops.wm.save_mainfile()
print("SPLIT_DONE")
