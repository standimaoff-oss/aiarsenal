"""r17 building blocks part 4 (Blender 5.x headless):
   blender -b --factory-startup --python tools/blender/wreck_blocks.py
Wrecks in REAL dimensions (metres = blocks), three pieces per vehicle:
  T-72B tank  - hull without turret (tracks, road wheels, gaping turret ring), turret thrown off with the bent 2A46 gun,
                a torn-off track run with a loose road wheel;
  Mi-24V      - forward fuselage (tandem cockpits, engines, stub wings, rocket pod), tail boom with fin and tail rotor,
                a bent main rotor blade;
  MiG-29      - forward / centre fuselage (canopy, LERX, intakes), torn-off wing panel (upside down, pylons),
                tail section (fin, stabilator, RD-33 nozzle).
Each wreck is cut at every block boundary and exported one OBJ per occupied cell (block-local coordinates) so every
cell of the multi-block is a baked chunk model with its own light and a collision box (written to wreck_cells.json).
Loot crates (body + lid hinged at the back for the block entity renderer) and five new traps follow.
Same conventions as door_blocks.py (1 unit = 1 block, origin = block corner, +Y up, model front = NORTH = -Z).
Saves models/wreck_blocks.blend (textures packed)."""
import bpy, bmesh, math, os, json, random
from mathutils import Vector, Matrix

_SRC = open("/home/user/tools/blender/door_blocks.py").read()
exec(_SRC[:_SRC.index("# ====================================================================================== DOORS")])
OUT = RES + "/models/block/wreck"
os.makedirs(OUT, exist_ok=True)
for f in os.listdir(OUT):
    if f.endswith(".obj"):
        os.remove(os.path.join(OUT, f))
for k, png, a in [("tank_paint", "wreck_tank_paint.png", False), ("burnt", "wreck_burnt_steel.png", False),
                  ("wrust", "wreck_rust.png", False), ("soot", "wreck_soot.png", False), ("heli_paint", "wreck_heli_paint.png", False),
                  ("jet_paint", "wreck_plane_paint.png", False), ("track", "wreck_track.png", False), ("wrubber", "wreck_rubber.png", False),
                  ("interior", "wreck_interior.png", False), ("wglass", "wreck_glass.png", True), ("nozzle", "wreck_nozzle.png", False),
                  ("cwood", "crate_wood_green.png", False), ("bare", "crate_wood_bare.png", False), ("omet", "crate_olive_metal.png", False),
                  ("white", "crate_white.png", False), ("fglass", "crate_fiberglass.png", False), ("cblack", "crate_black.png", False),
                  ("latch", "crate_latch.png", False), ("crope", "crate_rope.png", False), ("decal", "crate_decals.png", True),
                  ("bamboo", "trap_bamboo.png", False), ("leafmat", "trap_leafmat.png", True), ("spikes", "trap_spikes.png", False),
                  ("shell", "trap_shell.png", False), ("wires", "trap_wire_colors.png", False), ("tolive", "trap_olive.png", False),
                  ("insul", "trap_insulator.png", False), ("vplate", "trap_voltage_plate.png", False)]:
    mat(k, png, a)
ORDER[:] = list(MATS.keys())
RX = lambda d: Matrix.Rotation(math.radians(d), 3, "X")
RY = lambda d: Matrix.Rotation(math.radians(d), 3, "Y")
RZ = lambda d: Matrix.Rotation(math.radians(d), 3, "Z")


# ------------------------------------------------------------------------------------------ helpers
def mark(p):
    return len(p.bm.verts)


def xform(p, i0, M=None, t=(0, 0, 0), pivot=(0, 0, 0)):
    pv, tv = Vector(pivot), Vector(t)
    for v in list(p.bm.verts)[i0:]:
        c = v.co - pv
        if M is not None:
            c = M @ c
        v.co = c + pv + tv


def loft(p, st, m, n=10, cap0=True, cap1=True, jag1=0.0, capmat=None, seed=0, phase=None):
    """Elliptical sections [(z, cx, cy, rx, ry)] joined by quads; jag1 roughens the last ring (torn metal)."""
    rnd = random.Random(seed)
    ph = math.pi / n if phase is None else phase
    rings = []
    for i, (z, cx, cy, rx, ry) in enumerate(st):
        ring = []
        for k in range(n):
            a = 2 * math.pi * k / n + ph
            zz = z + (rnd.uniform(-jag1, jag1) if (i == len(st) - 1 and jag1) else 0.0)
            ring.append((cx + max(rx, 0.006) * math.cos(a), cy + max(ry, 0.006) * math.sin(a), zz))
        rings.append(ring)
    verts = [v for r in rings for v in r]
    faces = []
    for i in range(len(rings) - 1):
        for k in range(n):
            k1 = (k + 1) % n
            faces.append((i * n + k, i * n + k1, (i + 1) * n + k1, (i + 1) * n + k))
    p.add_faces(verts, faces, m)
    for on, ring, mm in ((cap0, rings[0], m), (cap1, rings[-1], capmat or m)):
        if on:
            p.add_faces(ring, [tuple(range(n))], mm)


def slab(p, poly, yb, yt, m, ytop=None, mside=None):
    """Polygon [(x, z)] extruded vertically; ytop(x, z) optionally varies the top (tapered wings)."""
    n = len(poly)
    vb = [(x, yb, z) for (x, z) in poly]
    vt = [(x, (ytop(x, z) if ytop else yt), z) for (x, z) in poly]
    p.add_faces(vb + vt, [tuple(range(n)), tuple(n + i for i in range(n - 1, -1, -1))], m)
    p.add_faces(vb + vt, [(i, (i + 1) % n, n + (i + 1) % n, n + i) for i in range(n)], mside or m)


def frustum(p, poly, yb, yt, shrink, m, mtop=None):
    cx = sum(x for x, z in poly) / len(poly)
    cz = sum(z for x, z in poly) / len(poly)
    top = [(cx + (x - cx) * shrink, cz + (z - cz) * shrink) for x, z in poly]
    n = len(poly)
    vb = [(x, yb, z) for (x, z) in poly]
    vt = [(x, yt, z) for (x, z) in top]
    p.add_faces(vb + vt, [(i, (i + 1) % n, n + (i + 1) % n, n + i) for i in range(n)], m)
    p.add_faces(vb + vt, [tuple(range(n))], m)
    p.add_faces(vb + vt, [tuple(n + i for i in range(n - 1, -1, -1))], mtop or m)


def ribbon(p, pts, w, th, m, up=(0, 1, 0), side=None):
    """Flat strip (track run, rotor blade) made of oriented boxes between consecutive points.
    up = thickness normal, or side = width axis (tracks running round wheels)."""
    for a, b in zip(pts[:-1], pts[1:]):
        a, b = Vector(a), Vector(b)
        d = b - a
        L = d.length
        f = d.normalized()
        if side is not None:
            s = Vector(side)
            s = (s - f * s.dot(f)).normalized()
            u = f.cross(s).normalized()
        else:
            u = Vector(up)
            s = f.cross(u).normalized()
            u = s.cross(f).normalized()
        R = Matrix((s, u, f)).transposed()
        p.box(tuple((a + b) / 2), (w, th, L + th * 0.6), m, rot=R)


def rod(p, a, b, r, m, sides=6):
    p.tube([a, b], r, m, sides=sides, caps=True)


def wheel_x(p, c, r, h, side_m="wrubber", cap_m="burnt", hub=True, seg=10):
    cyl(p, c, r, h, side_m, cap_m, axis="X", seg=seg)
    if hub:
        cyl(p, c, r * 0.42, h + 0.05, "burnt", "burnt", axis="X", seg=8)


# ------------------------------------------------------------------------------------------ wreck export
CELLS = {}
WRECK_OBJS = []
wcoll = coll("Wrecks")


def wreck(name, W, H, L, build_fn, M=None, dx=0.0, dz=0.0, sink=0.0):
    """Build in a centred frame (x right, y up, -z forward), tilt by M, rest on the ground, place in the footprint
    x 0..W, z 1-L..1, cut at every block boundary and export one OBJ per occupied cell."""
    p = Piece("wreck_" + name)
    build_fn(p)
    bm = p.bm
    if M is not None:
        for v in bm.verts:
            v.co = M @ v.co
    ymin = min(v.co.y for v in bm.verts)
    cx0 = (min(v.co.x for v in bm.verts) + max(v.co.x for v in bm.verts)) / 2
    cz0 = (min(v.co.z for v in bm.verts) + max(v.co.z for v in bm.verts)) / 2
    for v in bm.verts:
        v.co.y -= ymin + sink
        v.co.x += W / 2 + dx - cx0
        v.co.z += 1 - L / 2 + dz - cz0
    lo = [min(v.co[i] for v in bm.verts) for i in range(3)]
    hi = [max(v.co[i] for v in bm.verts) for i in range(3)]
    print("WRECK_BBOX", name, [round(x, 2) for x in lo], [round(x, 2) for x in hi], "footprint", W, H, L)
    geom = lambda: bm.verts[:] + bm.edges[:] + bm.faces[:]
    # clip to the footprint (ground at y = 0); anything outside is reported above
    for co, no in (((0, 0, 0), (0, 1, 0)), ((0, H, 0), (0, -1, 0)), ((0, 0, 0), (1, 0, 0)), ((W, 0, 0), (-1, 0, 0)),
                   ((0, 0, 1 - L), (0, 0, 1)), ((0, 0, 1), (0, 0, -1))):
        bmesh.ops.bisect_plane(bm, geom=geom(), dist=1e-5, plane_co=co, plane_no=no, clear_inner=True)
    for i in range(1, W):
        bmesh.ops.bisect_plane(bm, geom=geom(), dist=1e-5, plane_co=(i, 0, 0), plane_no=(1, 0, 0))
    for i in range(1, H):
        bmesh.ops.bisect_plane(bm, geom=geom(), dist=1e-5, plane_co=(0, i, 0), plane_no=(0, 1, 0))
    for i in range(1, L):
        bmesh.ops.bisect_plane(bm, geom=geom(), dist=1e-5, plane_co=(0, 0, 1 - i), plane_no=(0, 0, 1))
    p.uvfix = {}
    ob = p.finish(coll=wcoll)
    WRECK_OBJS.append(ob.name)
    EXPORT.append(ob.name)                     # full model -> item model
    me = ob.data
    # face -> cell (centre pushed slightly into the solid so faces on a cut plane go to their own block)
    groups = {}
    for poly in me.polygons:
        c = poly.center - poly.normal * 2e-3
        cx, cy, cz = math.floor(c.x), math.floor(c.y), math.floor(c.z)
        cx, cy = min(max(cx, 0), W - 1), min(max(cy, 0), H - 1)
        oz = min(max(-cz, 0), L - 1)
        groups.setdefault((cx, cy, oz), []).append(poly.index)
    cells = []
    for (cx, cy, oz), polys in sorted(groups.items()):
        bm2 = bmesh.new()
        bm2.from_mesh(me)
        keep = set(polys)
        bm2.faces.ensure_lookup_table()
        bmesh.ops.delete(bm2, geom=[f for f in bm2.faces if f.index not in keep], context="FACES")
        for v in bm2.verts:
            v.co.x -= cx
            v.co.y -= cy
            v.co.z += oz
        if not bm2.faces:
            bm2.free()
            continue
        xs = [v.co.x for v in bm2.verts]
        ys = [v.co.y for v in bm2.verts]
        zs = [v.co.z for v in bm2.verts]
        area = sum(f.calc_area() for f in bm2.faces)
        box = [max(0.0, min(xs)), max(0.0, min(ys)), max(0.0, min(zs)), min(1.0, max(xs)), min(1.0, max(ys)), min(1.0, max(zs))]
        cname = "wreck_%s_c%d_%d_%d" % (name, cx, cy, oz)
        me2 = bpy.data.meshes.new(cname)
        for mm in me.materials:
            me2.materials.append(mm)
        bm2.to_mesh(me2)
        bm2.free()
        ob2 = bpy.data.objects.new(cname, me2)
        ob2.rotation_euler = (math.pi / 2, 0, 0)
        scn.collection.objects.link(ob2)
        for o in bpy.data.objects:
            o.select_set(o == ob2)
        bpy.context.view_layer.objects.active = ob2
        path = os.path.join(OUT, cname + ".obj")
        bpy.ops.wm.obj_export(filepath=path, export_selected_objects=True, export_triangulated_mesh=True, export_materials=True,
                              export_uv=True, export_normals=True, forward_axis="NEGATIVE_Z", up_axis="Y", apply_modifiers=True,
                              export_animation=False)
        if os.path.exists(path[:-4] + ".mtl"):
            os.remove(path[:-4] + ".mtl")
        for pp in me2.polygons:
            USED.add(me2.materials[pp.material_index].name)
        cells.append({"x": cx, "y": cy, "z": oz, "box": [round(b * 16, 2) for b in box], "solid": area > 0.08})
        bpy.data.objects.remove(ob2)
        bpy.data.meshes.remove(me2)
    CELLS[name] = {"w": W, "h": H, "l": L, "cells": cells, "tris": sum(len(pp.vertices) - 2 for pp in me.polygons)}
    print("WRECK", name, "cells", len(cells), "tris", CELLS[name]["tris"])


USED = set()


# ========================================================================================== T-72B HULL (W4 H2 L7)
def tank_hull(p):
    rnd = random.Random(72)
    # lower hull tub with glacis: profile (z, y), extruded across the hull
    p.prism([(3.30, 0.45), (3.43, 1.45), (-1.75, 1.45), (-3.43, 0.95), (-3.15, 0.45)], -1.10, 1.10, "tank_paint")
    # fenders over the tracks (left front segment torn and bent down)
    for s in (-1, 1):
        x0, x1 = (1.10, 1.74) if s > 0 else (-1.74, -1.10)
        zs = (-3.10, 3.35) if s > 0 else (-1.6, 3.35)
        p.box(((x0 + x1) / 2, 1.39, sum(zs) / 2), (x1 - x0, 0.05, zs[1] - zs[0]), "tank_paint")
    i0 = mark(p)
    p.box((-1.42, 1.39, -2.35), (0.64, 0.05, 1.5), "tank_paint")
    xform(p, i0, RX(-28) @ RZ(-9), pivot=(-1.42, 1.39, -1.6))
    # gaping turret ring: lip, soot hole, carousel debris
    ring = [(1.02 * math.cos(a * math.pi / 10), 1.50, -0.25 + 1.02 * math.sin(a * math.pi / 10)) for a in range(21)]
    p.tube(ring, 0.06, "burnt", sides=4)
    disc = [(0.98 * math.cos(a * math.pi / 8), 1.462, -0.25 + 0.98 * math.sin(a * math.pi / 8)) for a in range(16)]
    p.add_faces(disc, [tuple(range(15, -1, -1))], "soot")
    for k in range(6):
        a = rnd.uniform(0, 2 * math.pi)
        r = rnd.uniform(0.2, 0.7)
        c = (r * math.cos(a), 1.6, -0.25 + r * math.sin(a))
        rod(p, c, (c[0] + rnd.uniform(-0.3, 0.3), 1.62 + rnd.uniform(0.05, 0.22), c[2] + rnd.uniform(-0.3, 0.3)), 0.065, "wrust")
    i0 = mark(p)
    p.box((0.35, 1.66, -0.5), (0.05, 0.38, 0.42), "burnt")                           # bent carousel plate
    xform(p, i0, RZ(24) @ RX(14), pivot=(0.35, 1.5, -0.5))
    # engine deck: grilles with louvres, blown-open access panel
    p.box((0, 1.47, 2.3), (1.9, 0.04, 1.8), "burnt")
    for k in range(7):
        p.box((-0.85 + k * 0.283, 1.50, 2.3), (0.06, 0.04, 1.7), "burnt")
    i0 = mark(p)
    p.box((-0.55, 1.47, 1.15), (0.9, 0.05, 0.62), "tank_paint")
    xform(p, i0, RX(-52), pivot=(-0.55, 1.47, 0.84))
    # driver hatch, periscopes, headlights, spare links on the glacis
    cyl(p, (0, 1.47, -2.05), 0.3, 0.06, "tank_paint", "tank_paint", seg=10)
    for xx in (-0.18, 0, 0.18):
        p.box((xx, 1.52, -2.42), (0.12, 0.08, 0.06), "black")
    for s in (-1, 1):
        cyl(p, (s * 0.85, 1.20, -3.12), 0.07, 0.12, "dark_steel", "black", axis="Z", seg=8)
    a = math.radians(-16.6)
    R = RX(-16.6)
    n = Vector((0, math.cos(a), math.sin(a)))
    for row, t in enumerate((0.25, 0.62)):
        P = Vector((0, 1.45, -1.75)) + t * Vector((0, -0.5, -1.68))
        for k in range(5):
            if (row, k) in ((0, 3), (1, 0), (1, 4)):
                continue                                                           # brick blown off
            c = P + n * 0.045 + Vector((-0.86 + k * 0.43, 0, 0))
            p.box(tuple(c), (0.4, 0.08, 0.36), "tank_paint", rot=R)
    for k in range(3):
        c = Vector((0.2 + k * 0.17, 1.2, -2.85)) + n * 0.03
        p.box(tuple(c), (0.14, 0.05, 0.58), "track", rot=R @ RY(90))
    # side skirts: right side panels (one hanging), left side almost gone
    for k in range(5):
        z0 = -3.0 + k * 1.2
        i0 = mark(p)
        p.box((1.75, 1.08, z0 + 0.58), (0.03, 0.58, 1.12), "wrubber" if k % 2 else "tank_paint")
        if k == 3:
            xform(p, i0, RZ(-22) @ RX(8), pivot=(1.75, 1.37, z0 + 0.58))
    p.box((-1.75, 1.10, 2.4), (0.03, 0.52, 1.0), "tank_paint")
    # running gear
    WZ = (-2.40, -1.60, -0.80, 0.00, 0.80, 1.60)
    for s in (-1, 1):
        x = s * 1.43
        for i, z in enumerate(WZ):
            if s < 0 and i in (1, 4):
                cyl(p, (x + 0.05 * s, 0.375, z), 0.12, 0.42, "burnt", "burnt", axis="X", seg=6)   # bare hub
                continue
            wheel_x(p, (x, 0.375, z), 0.375, 0.36)
        for z in (-1.6, -0.2, 1.2):
            cyl(p, (x, 0.80, z), 0.12, 0.22, "burnt", "burnt", axis="X", seg=8)
        cyl(p, (x, 0.58, 2.95), 0.33, 0.30, "burnt", "burnt", axis="X", seg=12)          # drive sprocket
        for k in range(10):
            aa = k * 2 * math.pi / 10
            p.box((x, 0.58 + 0.36 * math.sin(aa), 2.95 + 0.36 * math.cos(aa)), (0.12, 0.08, 0.08), "burnt", rot=RX(-math.degrees(aa)))
        cyl(p, (x, 0.55, -3.0), 0.30, 0.30, "burnt", "burnt", axis="X", seg=10)          # idler
    # right track: complete loop of straight runs
    xr = 1.43
    loop = [(xr, 0.03, 2.95), (xr, 0.03, -2.9), (xr, 0.30, -3.30), (xr, 0.82, -3.05), (xr, 0.93, -1.6), (xr, 0.90, 1.2),
            (xr, 0.94, 2.9), (xr, 0.60, 3.30), (xr, 0.03, 2.95)]
    ribbon(p, loop, 0.58, 0.06, "track", side=(1, 0, 0))
    # left track: bottom run under the rear half, front part fell off and lies flat ahead
    xl = -1.43
    ribbon(p, [(xl, 0.03, 2.95), (xl, 0.03, -0.9)], 0.58, 0.06, "track", side=(1, 0, 0))
    ribbon(p, [(xl, 0.60, 3.30), (xl, 0.30, 3.30), (xl, 0.03, 2.95)], 0.58, 0.06, "track", side=(1, 0, 0))
    ribbon(p, [(xl - 0.05, 0.03, -0.95), (xl - 0.25, 0.03, -2.2), (xl - 0.38, 0.06, -3.38)], 0.58, 0.06, "track", side=(1, 0, 0))
    # rear: exhaust louvre on the left fender, unditching beam brackets, tow cables
    p.box((-1.42, 1.48, 1.2), (0.5, 0.12, 0.7), "soot")
    for s in (-1, 1):
        p.box((s * 0.75, 1.15, 3.40), (0.12, 0.3, 0.06), "burnt")
        p.tube([(s * 1.3, 1.44, -2.8), (s * 1.32, 1.45, 0.0), (s * 1.3, 1.44, 2.9)], 0.025, "wrust", sides=4)
    # scattered fire debris on the deck
    for k in range(5):
        c = (rnd.uniform(-0.9, 0.9), 1.49, rnd.uniform(0.9, 3.1))
        p.box(c, (rnd.uniform(0.1, 0.3), 0.03, rnd.uniform(0.1, 0.35)), "wrust", rot=RY(rnd.uniform(0, 90)))


wreck("tank_hull", 4, 2, 7, tank_hull, M=RZ(1.5))


# ========================================================================================== T-72B TURRET + 2A46 GUN (W3 H2 L9)
def tank_turret(p):
    poly = []
    for k in range(9):                                     # rear arc
        a = math.radians(-20 + k * 220 / 8 + 0)
        poly.append((1.05 * math.cos(a), 1.05 * math.sin(a)))
    poly = [(x, z) for (x, z) in poly]
    poly += [(-1.0, -0.55), (-0.62, -1.18), (0.62, -1.18), (1.0, -0.55)]
    poly = sorted(poly, key=lambda q: math.atan2(q[1], q[0]))
    frustum(p, poly, 0.0, 0.72, 0.76, "tank_paint")
    cyl(p, (0, -0.14, 0), 0.92, 0.28, "burnt", "soot", seg=16)                     # ring / underside
    # Kontakt-1 bricks on the cheeks (V)
    for s in (-1, 1):
        for k in range(4):
            c = (s * (0.25 + k * 0.2), 0.70 - k * 0.02, -0.70 - k * 0.12)
            p.box(c, (0.19, 0.08, 0.34), "tank_paint", rot=RY(s * 28) @ RX(-6))
    # mantlet and gun (thermal sleeve, bore evacuator); the barrel bent down
    p.box((0, 0.38, -1.25), (0.52, 0.46, 0.36), "tank_paint")
    pts = []
    for k in range(13):
        t = k / 12
        z = -1.42 - 6.0 * t
        pts.append((0.02 * t, 0.40 - 0.75 * t ** 2.2, z))
    p.tube(pts[:9], 0.10, "tank_paint", sides=8)
    p.tube(pts[8:], 0.085, "tank_paint", sides=8, caps=True)
    a, b = Vector(pts[2]), Vector(pts[3])
    p.tube([tuple(a + (b - a) * 0.1), tuple(a + (b - a) * 0.9)], 0.135, "tank_paint", sides=8, caps=True)
    cyl(p, pts[-1], 0.05, 0.02, "soot", "soot", axis="Z", seg=6)
    # cupola with open hatch and bent NSVT, gunner sight, smoke dischargers, stowage, snorkel
    cyl(p, (0.42, 0.80, 0.15), 0.38, 0.18, "tank_paint", "burnt", seg=12)
    i0 = mark(p)
    cyl(p, (0.42, 0.92, 0.55), 0.33, 0.05, "tank_paint", "tank_paint", seg=12)
    xform(p, i0, RX(70), pivot=(0.42, 0.9, 0.2))
    rod(p, (0.30, 0.95, 0.05), (0.15, 1.20, -0.45), 0.03, "burnt")
    rod(p, (0.15, 1.20, -0.45), (0.05, 1.05, -1.2), 0.025, "burnt")
    p.box((-0.45, 0.82, -0.48), (0.3, 0.22, 0.4), "tank_paint")
    p.box((-0.45, 0.85, -0.69), (0.22, 0.12, 0.02), "black")
    for s in (-1, 1):
        for k in range(4):
            rod(p, (s * (0.62 + k * 0.08), 0.55, -0.85 + k * 0.1), (s * (0.66 + k * 0.08), 0.72, -1.05 + k * 0.1), 0.045, "dark_steel")
    for k, a in enumerate((60, 90, 120)):
        aa = math.radians(a)
        p.box((1.08 * math.cos(aa), 0.32, 1.08 * math.sin(aa)), (0.62, 0.32, 0.26), "tank_paint", rot=RY(-a + 90))
    cyl(p, (0, 0.52, 1.08), 0.12, 1.4, "tank_paint", "tank_paint", axis="X", seg=8)


wreck("tank_turret", 3, 2, 9, tank_turret, M=RY(4) @ RZ(-24))


# ========================================================================================== TORN TRACK RUN (W2 H1 L6)
def tank_track(p):
    pts = [(0.32 * math.sin(z * 0.9), 0.03 + 0.02 * math.sin(z * 2.1) ** 2, z) for z in [ -2.85 + i * 0.475 for i in range(13)]]
    ribbon(p, pts, 0.58, 0.06, "track")
    for k in range(0, 13, 2):                                # guide horns every link pair
        x, y, z = pts[k]
        p.box((x, y + 0.08, z), (0.1, 0.12, 0.12), "burnt")
    for e in (pts[0], pts[-1]):                              # broken end pins
        rod(p, (e[0] - 0.32, e[1] + 0.03, e[2]), (e[0] + 0.32, e[1] + 0.03, e[2]), 0.02, "dark_steel")
    cyl(p, (-0.45, 0.11, 1.85), 0.375, 0.2, "wrubber", "burnt", seg=12)         # loose road wheel lying flat
    cyl(p, (-0.45, 0.22, 1.85), 0.15, 0.06, "burnt", "burnt", seg=8)


wreck("tank_track", 2, 1, 6, tank_track)


# ========================================================================================== Mi-24V FORWARD FUSELAGE (W6 H4 L10)
def heli_body(p):
    rnd = random.Random(24)
    loft(p, [(-4.9, 0, 0.75, 0.12, 0.12), (-4.5, 0, 0.78, 0.42, 0.45), (-3.6, 0, 0.85, 0.66, 0.70), (-2.6, 0, 0.95, 0.76, 0.82),
             (-1.4, 0, 1.05, 0.84, 0.92), (0.0, 0, 1.08, 0.86, 0.96), (2.0, 0, 1.08, 0.86, 0.96), (3.6, 0, 1.06, 0.80, 0.92),
             (4.6, 0, 1.02, 0.74, 0.86)], "heli_paint", n=12, jag1=0.25, capmat="soot", seed=1)
    # tandem canopies (crazed, holed glass) with frames
    loft(p, [(-4.35, 0, 1.30, 0.18, 0.12), (-4.0, 0, 1.42, 0.5, 0.35), (-3.3, 0, 1.48, 0.6, 0.42), (-2.85, 0, 1.46, 0.55, 0.36)], "wglass", n=10)
    loft(p, [(-2.7, 0, 1.72, 0.3, 0.2), (-2.3, 0, 1.88, 0.58, 0.42), (-1.5, 0, 1.98, 0.62, 0.45), (-0.9, 0, 1.92, 0.55, 0.34)], "wglass", n=10)
    for z, y, r in ((-3.95, 1.42, 0.52), (-3.3, 1.48, 0.62), (-2.3, 1.88, 0.6), (-1.5, 1.98, 0.64)):
        arc = [(r * math.cos(a * math.pi / 8), y + 0.95 * r * math.sin(a * math.pi / 8) * 0.7, z) for a in range(9)]
        p.tube(arc, 0.025, "dark_steel", sides=4)
    # cockpit tubs seen through the holes
    p.box((0, 1.25, -3.4), (0.9, 0.06, 0.9), "interior")
    p.box((0, 1.62, -1.8), (0.95, 0.06, 1.1), "interior")
    # chin turret with YakB-12.7, pitot boom
    cyl(p, (0, 0.42, -4.55), 0.24, 0.24, "heli_paint", "burnt", seg=10)
    for k in range(4):
        rod(p, (0.03 * (k % 2) - 0.015, 0.40 + 0.03 * (k // 2), -4.7), (0.03 * (k % 2) - 0.015, 0.38 + 0.03 * (k // 2), -4.98), 0.012, "dark_steel", 4)
    rod(p, (-0.32, 1.25, -4.4), (-0.42, 1.12, -4.96), 0.02, "dark_steel", 4)
    # engines with dust protectors and exhausts, gearbox fairing, rotor head with broken blades
    for s in (-1, 1):
        loft(p, [(-1.1, s * 0.62, 2.20, 0.3, 0.3), (-0.8, s * 0.62, 2.22, 0.45, 0.45), (1.8, s * 0.62, 2.25, 0.45, 0.45),
                 (2.6, s * 0.68, 2.25, 0.36, 0.36)], "heli_paint", n=10)
        cyl(p, (s * 0.62, 2.20, -1.32), 0.42, 0.45, "heli_paint", "soot", axis="Z", seg=10)
        p.tube([(s * 0.85, 2.25, 2.55), (s * 1.12, 2.18, 2.95), (s * 1.3, 2.05, 3.3)], 0.22, "nozzle", sides=8, caps=True)
    p.box((0, 2.45, 1.2), (0.9, 0.6, 2.4), "heli_paint")
    cyl(p, (0, 3.08, 1.2), 0.17, 0.5, "burnt", "burnt", seg=8)
    cyl(p, (0, 3.42, 1.2), 0.5, 0.26, "burnt", "burnt", seg=10)
    for k in range(5):
        a = math.radians(18 + k * 72)
        Lb = (2.2 if k == 1 else rnd.uniform(0.5, 0.9))
        base = Vector((0.5 * math.cos(a), 3.42, 1.2 + 0.5 * math.sin(a)))
        d = Vector((math.cos(a), -0.08 if k != 1 else -0.35, math.sin(a))).normalized()
        ribbon(p, [tuple(base), tuple(base + d * Lb)], 0.5, 0.07, "jet_paint" if False else "burnt")
    # stub wings (12 deg anhedral): left intact with B-8V20 pod and tip rail, right snapped off
    for s in (-1, 1):
        span = 2.5 if s < 0 else 1.05
        i0 = mark(p)
        tip = [(s * (0.8 + span), 0.0 + 0.0)]
        if s < 0:
            poly = [(-0.8, -0.2), (-0.8, 1.5), (-3.3, 1.3), (-3.3, 0.1)]
        else:
            poly = [(0.8, -0.2), (0.8, 1.5), (1.85, 1.38), (1.65, 1.0), (1.95, 0.6), (1.7, 0.25), (1.85, -0.1)]
        slab(p, poly, 1.10, 1.22, "heli_paint")
        if s < 0:
            for xx in (-1.8, -2.75):
                p.box((xx, 0.98, 0.6), (0.12, 0.26, 1.0), "heli_paint")
            cyl(p, (-1.8, 0.62, 0.55), 0.26, 1.6, "heli_paint", "soot", axis="Z", seg=10)
            p.box((-3.32, 0.98, 0.65), (0.1, 0.16, 1.5), "dark_steel")
        xform(p, i0, RZ(-s * 12), pivot=(s * 0.8, 1.16, 0))
    # open cabin door hole, antennas, wires hanging from the break
    p.box((-0.86, 0.95, 1.1), (0.02, 0.95, 1.0), "soot")
    rod(p, (0, 2.0, -0.4), (0, 2.45, -0.9), 0.015, "dark_steel", 4)
    for k in range(5):
        a = (rnd.uniform(-0.5, 0.5), rnd.uniform(0.4, 1.6), 4.5)
        p.tube([a, (a[0] + rnd.uniform(-0.2, 0.2), a[1] - 0.25, 4.75), (a[0] + rnd.uniform(-0.3, 0.3), max(0.05, a[1] - 0.6), 4.85)],
               0.02, "wires", sides=4)


wreck("heli_body", 6, 4, 10, heli_body, M=RZ(9) @ RX(-2))


# ========================================================================================== Mi-24 TAIL BOOM (W3 H3 L7)
def heli_tail(p):
    loft(p, [(-3.4, 0, 0.55, 0.46, 0.5), (-1.0, 0, 0.7, 0.36, 0.4), (1.6, 0, 0.95, 0.25, 0.28), (2.6, 0, 1.15, 0.22, 0.24)],
         "heli_paint", n=10, jag1=0.18, capmat="soot", seed=3, cap0=True)
    xs = 0.05
    p.prism([(1.8, 1.05), (2.95, 1.05), (3.38, 2.85), (2.72, 2.85)], -xs, xs, "heli_paint")
    for s in (-1, 1):                                       # stabilisers
        slab(p, [(0, 1.0), (0, 1.75), (s * 1.35, 1.6), (s * 1.35, 1.15)] if s > 0 else [(0, 1.0), (s * 1.35, 1.15), (s * 1.35, 1.6), (0, 1.75)],
             0.88, 0.94, "heli_paint")
    cyl(p, (0.18, 2.3, 3.0), 0.12, 0.25, "burnt", "burnt", axis="X", seg=8)
    for k, Lb in enumerate((1.5, 0.45, 0.35)):
        a = math.radians(30 + k * 120)
        base = Vector((0.3, 2.3, 3.0))
        d = Vector((0, math.sin(a), math.cos(a)))
        ribbon(p, [tuple(base), tuple(base + d * Lb)], 0.24, 0.04, "burnt", up=(1, 0, 0))
    p.tube([(0, 0.85, 1.4), (0, 0.45, 1.9), (0, 0.42, 2.4)], 0.04, "dark_steel", sides=4)      # tail skid
    p.box((0, 0.9, -3.0), (0.6, 0.06, 0.8), "wires")


wreck("heli_tail", 3, 3, 8, heli_tail, M=RZ(24) @ RY(-6))


# ========================================================================================== Mi-24 MAIN ROTOR BLADE (W2 H1 L9)
def heli_rotor(p):
    pts = []
    for k in range(10):
        t = k / 9
        z = -4.3 + 8.4 * t
        y = 0.05 + 0.42 * math.sin(min(1, max(0, (t - 0.45) / 0.45)) * math.pi) * (t > 0.45)
        pts.append((0.25 * math.sin(t * 2.4), y, z))
    ribbon(p, pts, 0.58, 0.07, "burnt")
    p.box((0.0, 0.15, -4.25), (0.5, 0.22, 0.5), "burnt")                       # root fitting
    cyl(p, (0.0, 0.16, -4.6), 0.18, 0.3, "burnt", "burnt", axis="X", seg=8)
    ribbon(p, [(-0.7, 0.04, -1.2), (0.1, 0.06, 0.6), (0.65, 0.1, 1.5)], 0.5, 0.06, "burnt")      # second broken blade piece


wreck("heli_rotor", 2, 1, 9, heli_rotor)


# ========================================================================================== MiG-29 FORWARD FUSELAGE (W4 H2 L9)
def jet_body(p):
    loft(p, [(-4.05, 0, 0.95, 0.05, 0.05), (-3.2, 0, 0.95, 0.32, 0.32), (-2.2, 0, 0.98, 0.48, 0.5), (-1.0, 0, 1.0, 0.58, 0.6),
             (0.4, 0, 1.0, 0.62, 0.62), (2.0, 0, 0.98, 0.66, 0.58), (3.3, 0, 0.95, 0.7, 0.55), (4.3, 0, 0.92, 0.72, 0.52)],
         "jet_paint", n=12, jag1=0.22, capmat="soot", seed=5)
    loft(p, [(-1.8, 0, 1.46, 0.25, 0.15), (-1.2, 0, 1.6, 0.45, 0.32), (0.0, 0, 1.66, 0.48, 0.34), (0.9, 0, 1.58, 0.4, 0.25),
             (1.6, 0, 1.48, 0.25, 0.12)], "wglass", n=10)
    for z, r in ((-1.2, 0.46), (0.0, 0.5), (0.9, 0.42)):
        arc = [(r * math.cos(a * math.pi / 8), 1.5 + 0.34 * math.sin(a * math.pi / 8), z) for a in range(9)]
        p.tube(arc, 0.025, "dark_steel", sides=4)
    p.box((0, 1.36, -0.3), (0.75, 0.05, 1.6), "interior")                      # empty cockpit (seat ejected)
    p.box((0, 1.5, -1.15), (0.6, 0.25, 0.12), "black")                           # panel
    cyl(p, (0.18, 1.55, -2.1), 0.12, 0.1, "chrome", "chrome", seg=8)              # IRST ball
    rod(p, (0, 0.95, -4.05), (0.05, 0.9, -4.45), 0.02, "dark_steel", 4)          # pitot
    for s in (-1, 1):                                                              # LERX
        poly = [(s * 0.55, -1.0), (s * 1.0, 0.5), (s * 1.65, 2.6), (s * 1.75, 4.3), (s * 0.55, 4.3)]
        slab(p, poly if s > 0 else list(reversed(poly)), 0.95, 1.01, "jet_paint")
        p.prism([(0.2, 0.08), (4.3, 0.06), (4.3, 0.62), (0.6, 0.62)], s * 0.55 if s > 0 else -1.25, 1.25 if s > 0 else -0.55, "jet_paint")
        p.box((s * 0.9, 0.35, 0.22), (0.62, 0.46, 0.04), "soot")                 # intake mouth
    p.box((-0.75, 1.02, 0.9), (0.12, 0.04, 0.4), "soot")                          # GSh-30 port
    for k in range(4):
        a = (random.Random(k).uniform(-0.4, 0.4), random.Random(k + 9).uniform(0.6, 1.3), 4.25)
        p.tube([a, (a[0], a[1] - 0.2, 4.4), (a[0] + 0.1, max(0.05, a[1] - 0.5), 4.45)], 0.02, "wires", sides=4)


wreck("jet_body", 4, 2, 9, jet_body, M=RZ(-6) @ RX(-3))


# ========================================================================================== MiG-29 WING PANEL (W5 H1 L5)
def jet_wing(p):
    poly = [(-2.4, -2.35), (1.7, 1.25), (1.7, 2.4), (-2.4, 1.05), (-2.25, 0.4), (-2.45, -0.3), (-2.3, -1.2)]
    slab(p, poly, 0.0, 0.14, "jet_paint", ytop=lambda x, z: 0.14 - 0.09 * (x + 2.4) / 4.1)
    i0 = mark(p)
    slab(p, [(0.2, 1.65), (1.6, 2.38), (1.6, 2.75), (0.2, 2.02)], 0.0, 0.05, "jet_paint")      # aileron hanging off
    xform(p, i0, RX(-10), pivot=(0.9, 0.05, 2.0))
    for xx, zz in ((-1.2, -0.2), (0.3, 0.9)):                                      # pylons (wing lies upside down)
        p.box((xx, 0.24, zz), (0.12, 0.22, 1.1), "jet_paint")
    p.box((0.3, 0.4, 0.9), (0.14, 0.1, 1.4), "dark_steel")                        # APU-73 rail
    cyl(p, (-0.2, 0.09, -1.75), 0.085, 1.3, "white", "soot", axis="Z", seg=8)   # broken R-73 half
    for k in range(4):
        p.box((-0.2 + 0.12 * math.cos(k * math.pi / 2), 0.09 + 0.12 * math.sin(k * math.pi / 2), -1.25), (0.02, 0.02, 0.18), "white",
              rot=RZ(k * 90))


wreck("jet_wing", 5, 1, 6, jet_wing, M=RZ(3))


# ========================================================================================== MiG-29 TAIL SECTION (W3 H4 L5)
def jet_tail(p):
    p.box((0, 0.6, -0.2), (0.75, 0.6, 3.6), "jet_paint")
    loft(p, [(0.8, 0.0, 0.62, 0.52, 0.52), (1.6, 0.0, 0.62, 0.55, 0.55), (2.2, 0.0, 0.62, 0.5, 0.5)], "nozzle", n=12, cap1=False)
    disc = [(0.38 * math.cos(a * math.pi / 6), 0.62 + 0.38 * math.sin(a * math.pi / 6), 2.1) for a in range(12)]
    p.add_faces(disc, [tuple(range(12))], "soot")
    for k in range(12):                                                            # nozzle petals
        a = k * math.pi / 6
        p.box((0.53 * math.cos(a), 0.62 + 0.53 * math.sin(a), 2.33), (0.22, 0.03, 0.3), "nozzle", rot=RZ(math.degrees(a) + 90))
    p.prism([(-1.65, 0.9), (0.85, 0.9), (2.35, 3.68), (1.35, 3.68)], -0.05, 0.05, "jet_paint")
    cyl(p, (0, 3.68, 1.85), 0.07, 1.0, "jet_paint", "jet_paint", axis="Z", seg=6)
    for s, span in ((1, 1.55), (-1, 0.9)):
        poly = [(s * 0.35, -0.4), (s * span, 0.9), (s * span, 1.6), (s * 0.35, 1.9)]
        slab(p, poly if s > 0 else list(reversed(poly)), 0.6, 0.66, "jet_paint")
    p.box((0, 0.6, -2.05), (0.7, 0.55, 0.1), "soot")                               # torn front


wreck("jet_tail", 3, 4, 5, jet_tail, M=RZ(10))


# ========================================================================================== LOOT CRATES (body + lid)
# Lid pivot (y, z) per crate is CrateKind in Java: lid hinged on the back (+z) top edge, rotates about X.
CRATES = {}


def crate(key, Lx, H, D, hl, body_m, lid_m, extra_body, extra_lid):
    x0, x1, z0, z1 = 0.5 - Lx / 2, 0.5 + Lx / 2, 0.5 - D / 2, 0.5 + D / 2
    hb = H - hl
    pb = Piece("crate_%s_body" % key)
    pb.box((0.5, hb / 2, 0.5), (Lx, hb, D), body_m)
    pb.box((0.5, hb - 0.004, 0.5), (Lx - 0.04, 0.008, D - 0.04), "soot")       # dark inside (seen when open)
    extra_body(pb, x0, x1, z0, z1, hb)
    done(pb)
    pl = Piece("crate_%s_lid" % key)
    pl.box((0.5, hb + hl / 2, 0.5), (Lx + 0.01, hl, D + 0.01), lid_m)
    extra_lid(pl, x0, x1, z0, z1, hb, H)
    done(pl)
    pi = Piece("crate_%s_item" % key)
    pi.box((0.5, hb / 2, 0.5), (Lx, hb, D), body_m)
    extra_body(pi, x0, x1, z0, z1, hb)
    pi.box((0.5, hb + hl / 2, 0.5), (Lx + 0.01, hl, D + 0.01), lid_m)
    extra_lid(pi, x0, x1, z0, z1, hb, H)
    done(pi)
    CRATES[key] = {"pivot_y": round(hb + hl, 4), "pivot_z": round(z1 + 0.005, 4), "box": [x0, 0, z0, x1, H, z1]}


def decal_front(p, x0, x1, y0, y1, z, uv):
    quad_uv(p, (x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z), "decal", uv)


def latches(p, xs, y, z):
    for x in xs:
        p.box((x, y, z - 0.012), (0.06, 0.08, 0.02), "latch")
        p.box((x, y + 0.045, z - 0.018), (0.04, 0.02, 0.03), "latch")


def ammo_body(p, x0, x1, z0, z1, hb):
    for x in (x0 + 0.03, x1 - 0.03):                                         # corner battens
        for z in (z0 - 0.008, z1 + 0.008):
            p.box((x, hb / 2, z), (0.05, hb, 0.016), "cwood")
    for s, x in ((-1, x0), (1, x1)):                                         # rope handles
        p.tube([(x + s * 0.01, hb * 0.65, 0.42), (x + s * 0.06, hb * 0.55, 0.45), (x + s * 0.06, hb * 0.55, 0.55), (x + s * 0.01, hb * 0.65, 0.58)],
               0.016, "crope", sides=4)
        p.box((x + s * 0.008, hb * 0.65, 0.5), (0.016, 0.06, 0.22), "dark_steel")
    latches(p, (0.32, 0.68), hb - 0.04, z0)
    decal_front(p, 0.38, 0.62, 0.06, 0.2, z0 - 0.003, ((0.52, 0.76), (0.98, 0.76), (0.98, 0.98), (0.52, 0.98)))


def ammo_lid(p, x0, x1, z0, z1, hb, H):
    for x in (0.3, 0.7):
        p.box((x, H + 0.008, 0.5), (0.05, 0.016, z1 - z0), "cwood")
    decal_front(p, 0.4, 0.6, H + 0.0175, H + 0.0175, 0, ((0, 0), (0, 0), (0, 0), (0, 0))) if False else None


def med_body(p, x0, x1, z0, z1, hb):
    latches(p, (0.33, 0.67), hb - 0.04, z0)
    decal_front(p, 0.4, 0.6, 0.06, 0.26, z0 - 0.003, ((0.03, 0.53), (0.47, 0.53), (0.47, 0.97), (0.03, 0.97)))
    for x in (x0, x1):
        p.box((x, hb * 0.6, 0.5), (0.03, 0.04, 0.2), "dark_steel")


def med_lid(p, x0, x1, z0, z1, hb, H):
    quad_uv(p, (0.38, H + 0.003, 0.62), (0.62, H + 0.003, 0.62), (0.62, H + 0.003, 0.38), (0.38, H + 0.003, 0.38), "decal",
            ((0.03, 0.53), (0.47, 0.53), (0.47, 0.97), (0.03, 0.97)))
    p.tube([(0.42, H, 0.5), (0.44, H + 0.05, 0.5), (0.56, H + 0.05, 0.5), (0.58, H, 0.5)], 0.012, "dark_steel", sides=4)


def mil_body(p, x0, x1, z0, z1, hb):
    for x in (x0, x1):
        for z in (z0, z1):
            p.box((x, hb / 2, z), (0.06, hb + 0.01, 0.06), "dark_steel")          # corner protectors
    for s, x in ((-1, x0), (1, x1)):
        p.tube([(x, hb * 0.6, 0.42), (x + s * 0.05, hb * 0.6, 0.44), (x + s * 0.05, hb * 0.6, 0.56), (x, hb * 0.6, 0.58)], 0.014, "dark_steel", sides=4)
    for z in (z0 - 0.006, z1 + 0.006):
        p.box((0.5, hb * 0.5, z), (x1 - x0 - 0.1, 0.03, 0.012), "omet")           # ribs
    latches(p, (0.25, 0.5, 0.75), hb - 0.05, z0)
    decal_front(p, 0.34, 0.66, 0.08, 0.24, z0 - 0.004, ((0.52, 0.53), (0.98, 0.53), (0.98, 0.75), (0.52, 0.75)))


def mil_lid(p, x0, x1, z0, z1, hb, H):
    for x in (x0, x1):
        for z in (z0, z1):
            p.box((x, H - 0.03, z), (0.065, 0.07, 0.065), "dark_steel")


def wpn_body(p, x0, x1, z0, z1, hb):
    latches(p, (0.16, 0.38, 0.62, 0.84), hb - 0.03, z0)
    for x in (0.08, 0.92):
        p.box((x, hb / 2, 0.5), (0.04, hb, z1 - z0 + 0.016), "fglass")
    decal_front(p, 0.42, 0.58, 0.05, 0.17, z0 - 0.003, ((0.52, 0.53), (0.98, 0.53), (0.98, 0.75), (0.52, 0.75)))


def wpn_lid(p, x0, x1, z0, z1, hb, H):
    for x in (0.25, 0.5, 0.75):
        p.box((x, H + 0.006, 0.5), (0.03, 0.012, z1 - z0 - 0.04), "fglass")
    quad_uv(p, (0.12, H + 0.0035, 0.56), (0.88, H + 0.0035, 0.56), (0.88, H + 0.0035, 0.44), (0.12, H + 0.0035, 0.44), "decal",
            ((0.02, 0.02), (0.48, 0.02), (0.48, 0.12), (0.02, 0.12)))


def sec_body(p, x0, x1, z0, z1, hb):
    for x in (x0, x1):
        for z in (z0, z1):
            p.box((x, hb / 2, z), (0.05, hb + 0.01, 0.05), "chrome")
    latches(p, (0.3, 0.7), hb - 0.04, z0)
    p.box((0.5, hb - 0.05, z0 - 0.012), (0.1, 0.07, 0.02), "black")             # keypad
    for k in range(6):
        p.box((0.47 + 0.03 * (k % 3), hb - 0.04 - 0.02 * (k // 3), z0 - 0.023), (0.018, 0.012, 0.004), "chrome")
    p.box((0.56, hb - 0.03, z0 - 0.023), (0.012, 0.012, 0.004), "paint_red")


def sec_lid(p, x0, x1, z0, z1, hb, H):
    quad_uv(p, (x0 + 0.02, H + 0.003, 0.62), (x1 - 0.02, H + 0.003, 0.62), (x1 - 0.02, H + 0.003, 0.38), (x0 + 0.02, H + 0.003, 0.38), "decal",
            ((0.52, 0.02), (0.98, 0.02), (0.98, 0.48), (0.52, 0.48)))
    p.tube([(0.4, H, 0.5), (0.42, H + 0.045, 0.5), (0.58, H + 0.045, 0.5), (0.6, H, 0.5)], 0.014, "black", sides=4)


crate("ammo", 0.86, 0.38, 0.48, 0.08, "cwood", "cwood", ammo_body, ammo_lid)
crate("medical", 0.70, 0.40, 0.44, 0.10, "white", "white", med_body, med_lid)
crate("military", 0.92, 0.56, 0.60, 0.14, "omet", "omet", mil_body, mil_lid)
crate("weapons", 0.98, 0.32, 0.52, 0.10, "fglass", "fglass", wpn_body, wpn_lid)
crate("secret", 0.78, 0.36, 0.52, 0.10, "cblack", "cblack", sec_body, sec_lid)


# ========================================================================================== TRAPS
def punji(p, covered):
    rnd = random.Random(31)
    for (a, b, c_, d) in (((0, 0, 0.002), (1, 0, 0.002), (1, 1, 0.002), (0, 1, 0.002)),):
        pass
    # the pit walls / floor are the neighbouring blocks' own faces (the trap does not occlude)
    for i in range(4):
        for j in range(4):
            x, z = 0.14 + i * 0.24 + rnd.uniform(-0.05, 0.05), 0.14 + j * 0.24 + rnd.uniform(-0.05, 0.05)
            h = rnd.uniform(0.5, 0.72)
            tx, tz = rnd.uniform(-0.06, 0.06), rnd.uniform(-0.06, 0.06)
            pts = [(x, 0.0, z), (x + tx * 0.5, h * 0.6, z + tz * 0.5), (x + tx, h, z + tz)]
            p.tube(pts[:2], 0.022, "bamboo", sides=5)
            cyl(p, (x + tx * 0.75, h * 0.8, z + tz * 0.75), 0.022, h * 0.4, "bamboo", "bamboo", seg=5, r2=0.002)
    if covered:
        quad2(p, (0, 0.965, 0), (1, 0.965, 0), (1, 0.965, 1), (0, 0.965, 1), "leafmat")
        for k in range(5):
            a = (rnd.uniform(0, 1), 0.97, rnd.uniform(0, 1))
            rod(p, a, (min(1, max(0, a[0] + rnd.uniform(-0.4, 0.4))), 0.975, min(1, max(0, a[2] + rnd.uniform(-0.4, 0.4)))), 0.012, "wood", 4)


p = Piece("trap_punji_open")
punji(p, False)
done(p)
p = Piece("trap_punji_covered")
punji(p, True)
done(p)


def spike_strip(p):
    p.box((0.5, 0.015, 0.5), (0.98, 0.03, 0.26), "dark_steel")
    for i in range(8):
        for j in range(3):
            x, z = 0.08 + i * 0.12, 0.42 + j * 0.08
            p.box((x, 0.06, z), (0.035, 0.07, 0.035), "spikes", taper=(0.1, 0.1))
    for x in (0.02, 0.98):
        p.box((x, 0.03, 0.5), (0.03, 0.05, 0.3), "paint_red")


p = Piece("trap_spike_strip")
spike_strip(p)
done(p)


def flare(p, spent):
    cyl(p, (0.5, 0.25, 0.5), 0.03, 0.5, "wood", "wood", seg=6)
    cyl(p, (0.5, 0.03, 0.5), 0.03, 0.06, "wood", "wood", seg=6, r2=0.005) if False else None
    if not spent:
        cyl(p, (0.5, 0.62, 0.5), 0.045, 0.28, "tolive", "paint_red", seg=8)
        p.box((0.5, 0.78, 0.5), (0.02, 0.03, 0.02), "dark_steel")                    # pull ring post
    else:
        cyl(p, (0.5, 0.6, 0.5), 0.045, 0.22, "soot", "soot", seg=8, caps=(True, False))
    p.tube([(0.5, 0.18, 0.5), (0.75, 0.16, 0.5), (1.0, 0.15, 0.5)], 0.004, "wire", sides=3)


p = Piece("trap_flare")
flare(p, False)
done(p)
p = Piece("trap_flare_spent")
flare(p, True)
done(p)


def ied(p):
    i0 = mark(p)
    cyl(p, (0.5, 0.12, 0.5), 0.076, 0.55, "shell", "shell", axis="X", seg=10)
    cyl(p, (0.83, 0.12, 0.5), 0.076, 0.11, "shell", "shell", axis="X", seg=10, r2=0.03)
    for x in (0.38, 0.6):
        cyl(p, (x, 0.12, 0.5), 0.08, 0.05, "black", "black", axis="X", seg=10)       # duct tape bands
    xform(p, i0, RY(25), pivot=(0.5, 0.12, 0.5))
    p.box((0.24, 0.05, 0.28), (0.16, 0.1, 0.12), "tolive")                         # battery / receiver
    rod(p, (0.2, 0.1, 0.25), (0.15, 0.4, 0.22), 0.006, "dark_steel", 3)           # antenna
    for k, (dx, dz) in enumerate(((0.02, 0.0), (0.05, 0.02), (0.08, -0.01))):
        p.tube([(0.3, 0.06 + 0.01 * k, 0.3 + dz), (0.38 + dx, 0.02, 0.4 + dz), (0.45 + dx, 0.13, 0.46)], 0.006, "wires", sides=3)
    for k in range(6):                                                              # rubble to hide it
        r = random.Random(k)
        p.box((r.uniform(0.15, 0.85), 0.03, r.uniform(0.15, 0.85)), (0.09, 0.06, 0.08), "stone" if "stone" in MATS else "burnt",
              rot=RY(r.uniform(0, 90)))


p = Piece("trap_ied")
ied(p)
done(p)


def efence_post(p):
    p.box((0.5, 0.5, 0.5), (0.09, 1.0, 0.09), "dark_steel")
    for y in (0.25, 0.5, 0.75, 0.95):
        for d in (0, 90, 180, 270):
            a = math.radians(d)
            cyl(p, (0.5 + 0.07 * math.sin(a), y, 0.5 - 0.07 * math.cos(a)), 0.022, 0.05, "insul", "insul", seg=6)
    p.box((0.5, 0.62, 0.44), (0.18, 0.18, 0.01), "vplate")


def efence_side(p):
    """North half: four wires from the post to the edge (z 0 .. 0.5)."""
    for y in (0.25, 0.5, 0.75, 0.95):
        p.tube([(0.5, y, 0.43), (0.5, y - 0.01, 0.2), (0.5, y, 0.0)], 0.007, "wire", sides=4)


p = Piece("trap_efence_post")
efence_post(p)
done(p)
p = Piece("trap_efence_side")
efence_side(p)
done(p)
p = Piece("trap_efence_item")
efence_post(p)
efence_side(p)
n0 = mark(p)
efence_side(p)
for v in list(p.bm.verts)[n0:]:
    v.co.z = 1 - v.co.z
done(p)

# ------------------------------------------------------------------ export the non-cell pieces + item models
bpy.context.view_layer.update()
stats = {}
for name in EXPORT:
    ob = bpy.data.objects[name]
    for o in bpy.data.objects:
        o.select_set(o == ob)
    bpy.context.view_layer.objects.active = ob
    fname = name + ("_item" if name in WRECK_OBJS else "")
    path = os.path.join(OUT, fname + ".obj")
    bpy.ops.wm.obj_export(filepath=path, export_selected_objects=True, export_triangulated_mesh=True, export_materials=True,
                          export_uv=True, export_normals=True, forward_axis="NEGATIVE_Z", up_axis="Y", apply_modifiers=True,
                          export_animation=False)
    if os.path.exists(path[:-4] + ".mtl"):
        os.remove(path[:-4] + ".mtl")
    stats[fname] = sum(len(pp.vertices) - 2 for pp in ob.data.polygons)
    for pp in ob.data.polygons:
        USED.add(ob.data.materials[pp.material_index].name)
tex = {}
for k in ORDER:
    if k in USED:
        img = next(n.image for n in MATS[k].node_tree.nodes if n.type == "TEX_IMAGE")
        tex[k] = os.path.basename(img.filepath)[:-4]
json.dump(tex, open(os.path.join(OUT, "wreck_texmap.json"), "w"), indent=1)
json.dump({"wrecks": CELLS, "crates": CRATES}, open(os.path.join(OUT, "wreck_cells.json"), "w"), indent=1)
with open(os.path.join(OUT, "wreck.mtl"), "w") as f:
    for k in ORDER:
        if k in USED:
            f.write("newmtl %s\nKa 0.0 0.0 0.0\nKd 1.0 1.0 1.0\nKs 0.0 0.0 0.0\nd 1.0\nmap_Kd #%s\n\n" % (k, k))
print("WRECK_TRIS", stats)
print("WRECK_ORIENT", ORIENT_STATS[-5:])
bpy.ops.wm.save_as_mainfile(filepath=ROOT + "/models/wreck_blocks.blend", compress=True)
print("WRECK_DONE", len(stats), sum(len(v["cells"]) for v in CELLS.values()))
