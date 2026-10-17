"""Fortification blocks for AI Arsenal (Blender 5.x, headless).
   blender -b --factory-startup --python tools/blender/fortifications.py
Builds every piece in Minecraft block space (1 unit = 1 block, origin = block corner, +Y up, -Z north) on objects rotated
Rx(+90) so the OBJ exporter (forward -Z, up Y) writes block coordinates directly. Exports one OBJ per piece for the Forge
OBJ loader (baked into chunk meshes - cheap even for long walls) and saves models/fortifications.blend (textures packed).
"""
import bpy, bmesh, math, os, random
from mathutils import Vector, Matrix

ROOT = "/home/user"
RES = ROOT + "/mods/aiarsenal/src/main/resources/assets/aiarsenal"
TEX = RES + "/textures/block"
OUT = RES + "/models/block/fort"
os.makedirs(OUT, exist_ok=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
scn = bpy.context.scene

# ------------------------------------------------------------------ materials (names = keys of fort.mtl / model json textures)
MATS = {}


def mat(key, png, alpha=False):
    m = bpy.data.materials.new(key)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    img = bpy.data.images.load(os.path.join(TEX, png))
    img.pack()
    tx = nt.nodes.new("ShaderNodeTexImage")
    tx.image = img
    tx.interpolation = "Closest"
    nt.links.new(tx.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.9
    if alpha:
        nt.links.new(tx.outputs["Alpha"], bsdf.inputs["Alpha"])
        try:
            m.surface_render_method = "DITHERED"
        except Exception:
            pass
    MATS[key] = m
    return m


for k, f, a in [("sandbag", "fort_sandbag.png", False), ("sandbag_olive", "fort_sandbag_olive.png", False),
                ("concrete", "fort_concrete.png", False), ("concrete_dark", "fort_concrete_dark.png", False),
                ("steel", "fort_steel.png", False), ("wire", "fort_wire.png", False), ("wood", "fort_wood.png", False),
                ("camo_net", "fort_camo_net.png", True), ("hazard", "fort_hazard.png", False)]:
    mat(k, f, a)

ORDER = list(MATS.keys())


# ------------------------------------------------------------------ mesh helpers (all coordinates in block space)
class Piece:
    def __init__(self, name):
        self.name = name
        self.bm = bmesh.new()
        self.uvscale = {}

    def add_faces(self, verts, faces, matkey, uv=1.0):
        vs = [self.bm.verts.new(v) for v in verts]
        mi = ORDER.index(matkey)
        out = []
        for f in faces:
            try:
                fa = self.bm.faces.new([vs[i] for i in f])
            except ValueError:
                continue
            fa.material_index = mi
            fa.smooth = False
            self.uvscale[fa] = uv
            out.append(fa)
        return out

    def box(self, c, size, matkey, rot=None, uv=1.0, taper=None):
        """Oriented box: centre c, full size (sx, sy, sz), rot = 3x3 Matrix. taper: top scale (x, z)."""
        sx, sy, sz = size[0] / 2, size[1] / 2, size[2] / 2
        tx, tz = taper if taper else (1, 1)
        p = []
        for (x, y, z) in [(-1, -1, -1), (1, -1, -1), (1, -1, 1), (-1, -1, 1), (-1, 1, -1), (1, 1, -1), (1, 1, 1), (-1, 1, 1)]:
            v = Vector((x * sx * (tx if y > 0 else 1), y * sy, z * sz * (tz if y > 0 else 1)))
            if rot is not None:
                v = rot @ v
            p.append(Vector(c) + v)
        F = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
        return self.add_faces(p, F, matkey, uv)

    def tube(self, pts, r, matkey, sides=4, uv=1.0, caps=False, twist=0.0):
        """Polyline tube with constant radius (wires, coils)."""
        rings = []
        n = len(pts)
        prev_side = None
        for i, pnt in enumerate(pts):
            a = Vector(pts[max(0, i - 1)]); b = Vector(pts[min(n - 1, i + 1)])
            t = (b - a).normalized()
            up = Vector((0, 1, 0)) if abs(t.y) < 0.9 else Vector((1, 0, 0))
            s = t.cross(up).normalized() if prev_side is None else (prev_side - t * prev_side.dot(t)).normalized()
            prev_side = s
            u = s.cross(t).normalized()
            ring = []
            for k in range(sides):
                ang = 2 * math.pi * k / sides + twist + math.pi / sides
                ring.append(Vector(pnt) + (s * math.cos(ang) + u * math.sin(ang)) * r)
            rings.append(ring)
        verts = [v for ring in rings for v in ring]
        faces = []
        for i in range(n - 1):
            for k in range(sides):
                a = i * sides + k; b = i * sides + (k + 1) % sides
                faces.append((a, a + sides, b + sides, b))      # r13: outward (was inside-out -> culled in MC)
        if caps:
            faces.append(tuple(range(sides)))
            faces.append(tuple((n - 1) * sides + k for k in range(sides - 1, -1, -1)))
        return self.add_faces(verts, faces, matkey, uv)

    def prism(self, prof, x0, x1, matkey, uv=1.0, caps=True):
        """Profile [(z, y)...] (counter-clockwise seen from +X) extruded along X from x0 to x1."""
        m = len(prof)
        verts = [(x0, y, z) for (z, y) in prof] + [(x1, y, z) for (z, y) in prof]
        faces = []
        for i in range(m):
            j = (i + 1) % m
            faces.append((i, j, j + m, i + m))
        if caps:
            faces.append(tuple(range(m - 1, -1, -1)))
            faces.append(tuple(m + i for i in range(m)))
        return self.add_faces(verts, faces, matkey, uv)

    def finish(self, smooth=False, double_sided=False, coll=None):
        bm = self.bm
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
        if not double_sided:
            orient_for_mc(bm, self.uvscale)
        if double_sided:
            # explicit back faces (MC culls back faces even in cutout)
            orig = list(bm.faces)
            for f in orig:
                vs = [bm.verts.new(v.co.copy()) for v in reversed(f.verts)]
                nf = bm.faces.new(vs)
                nf.material_index = f.material_index
                self.uvscale[nf] = self.uvscale.get(f, 1.0)
        uvl = bm.loops.layers.uv.new("UVMap")
        bm.faces.ensure_lookup_table()
        for f in bm.faces:
            n = f.normal
            ax = max(range(3), key=lambda i: abs(n[i]))
            sc = self.uvscale.get(f, 1.0)
            uvs = []
            for lp in f.loops:
                co = lp.vert.co
                if ax == 0:
                    u, v = (co.z if n.x < 0 else 1 - co.z), co.y
                elif ax == 1:
                    u, v = co.x, (co.z if n.y > 0 else 1 - co.z)
                else:
                    u, v = (co.x if n.z > 0 else 1 - co.x), co.y
                uvs.append([u * sc, v * sc])
            # keep each face inside one texture repeat (atlas sprites do not wrap)
            mu = math.floor(min(p[0] for p in uvs) + 1e-6); mv = math.floor(min(p[1] for p in uvs) + 1e-6)
            for lp, p in zip(f.loops, uvs):
                lp[uvl].uv = (min(1.0, max(0.0, p[0] - mu)), min(1.0, max(0.0, p[1] - mv)))
        for f in bm.faces:
            f.smooth = smooth
        me = bpy.data.meshes.new(self.name)
        bm.to_mesh(me)
        bm.free()
        for k in ORDER:
            me.materials.append(MATS[k])
        ob = bpy.data.objects.new(self.name, me)
        ob.rotation_euler = (math.pi / 2, 0, 0)      # block space (x, y, z) -> Blender (x, -z, y)
        (coll or scn.collection).objects.link(ob)
        return ob


def orient_for_mc(bm, uvscale=None):
    """r13: Minecraft culls back faces, Blender renders both -> make every face visible from where it is seen.
    Coincident face pairs (hand-made double-sided quads) are left alone. Every other face island is made consistent,
    then: (almost) closed islands face outward by signed volume; open sheets (signs, tin, lids) get explicit back faces."""
    bm.verts.index_update(); bm.faces.ensure_lookup_table()
    seen, pair = {}, set()
    for f in bm.faces:
        k = frozenset(v.index for v in f.verts)
        if k in seen:
            pair.add(f); pair.add(seen[k])
        else:
            seen[k] = f
    rest = [f for f in bm.faces if f not in pair]
    if rest:
        bmesh.ops.recalc_face_normals(bm, faces=rest)
    bm.normal_update()
    todo, islands = set(rest), []
    while todo:
        f0 = todo.pop(); isl = [f0]; st = [f0]
        while st:
            f = st.pop()
            for e in f.edges:
                for g in e.link_faces:
                    if g in todo:
                        todo.remove(g); isl.append(g); st.append(g)
        islands.append(isl)
    flip, dup = [], []
    for isl in islands:
        sset = set(isl)
        es = {e for f in isl for e in f.edges}
        bnd = sum(1 for e in es if sum(1 for g in e.link_faces if g in sset) == 1)
        area = sum(f.calc_area() for f in isl)
        if area < 1e-12:
            continue
        C = sum((f.calc_center_median() * f.calc_area() for f in isl), Vector()) / area
        if bnd <= 0.12 * len(es):
            vol = 0.0
            for f in isl:
                vs = [v.co - C for v in f.verts]
                for k in range(1, len(vs) - 1):
                    vol += vs[0].dot(vs[k].cross(vs[k + 1]))
            if vol < 0:
                flip.extend(isl)
        else:
            num = sum(f.calc_area() * f.normal.dot(f.calc_center_median() - C) for f in isl)
            den = sum(f.calc_area() * (f.calc_center_median() - C).length for f in isl)
            S = num / den if den > 1e-3 * area * math.sqrt(area) else 0.0      # relative: lone quads give ~1e-11 noise
            if abs(S) < 0.3:
                dup.extend(isl)
            elif S < 0:
                flip.extend(isl)
    if flip:
        bmesh.ops.reverse_faces(bm, faces=flip)
    for f in dup:
        vs = [bm.verts.new(v.co.copy()) for v in reversed(f.verts)]
        try:
            nf = bm.faces.new(vs)
        except ValueError:
            continue
        nf.material_index = f.material_index
        nf.smooth = f.smooth
        if uvscale is not None:
            uvscale[nf] = uvscale.get(f, 1.0)
    bm.normal_update()
    ORIENT_STATS.append((len(flip), len(dup)))


ORIENT_STATS = []


def rot_y(deg):
    return Matrix.Rotation(math.radians(deg), 3, "Y")


def bag(p, c, L, W, H, yaw, matkey, rng):
    """Pillow-shaped sandbag (3x3x3 lattice, squeezed ends, rounded corners): 48 faces."""
    N = 3
    grid = {}
    pts = []

    def vid(i, j, k):
        key = (i, j, k)
        if key not in grid:
            x, y, z = (i - 1), (j - 1), (k - 1)          # -1..1
            ax = abs(x) + abs(y) + abs(z)
            f = 0.80 if ax == 3 else (0.92 if ax == 2 else 1.0)
            vx, vy, vz = x * f, y * f, z * f
            if j == 2 and i == 1 and k == 1:
                vy = 1.12      # bulging top
            ex = 1 - 0.30 * vx * vx                     # squeezed (tied / folded) ends
            v = Vector((vx * L / 2, vy * H / 2 * ex, vz * W / 2 * (1 - 0.12 * vx * vx)))
            v = rot_y(yaw) @ v + Vector(c)
            grid[key] = len(pts)
            pts.append(v)
        return grid[key]

    faces = []
    r = range(N - 1)
    for a in r:
        for b in r:
            faces.append((vid(a, 0, b + 1), vid(a + 1, 0, b + 1), vid(a + 1, 0, b), vid(a, 0, b)))
            faces.append((vid(a, 2, b), vid(a, 2, b + 1), vid(a + 1, 2, b + 1), vid(a + 1, 2, b)))
            faces.append((vid(0, a, b), vid(0, a, b + 1), vid(0, a + 1, b + 1), vid(0, a + 1, b)))
            faces.append((vid(2, a, b), vid(2, a + 1, b), vid(2, a + 1, b + 1), vid(2, a, b + 1)))
            faces.append((vid(a, b, 0), vid(a, b + 1, 0), vid(a + 1, b + 1, 0), vid(a + 1, b, 0)))
            faces.append((vid(a, b, 2), vid(a + 1, b, 2), vid(a + 1, b + 1, 2), vid(a, b + 1, 2)))
    fs = p.add_faces(pts, faces, matkey, 1.0)
    return fs


def fix_normals(p):
    bmesh.ops.recalc_face_normals(p.bm, faces=p.bm.faces)


COLL = {}


def coll(name):
    c = bpy.data.collections.new(name)
    scn.collection.children.link(c)
    COLL[name] = c
    return c


# ================================================================== SANDBAGS
LAYERS, PITCH, BL, BW, BH = 5, 0.192, 0.56, 0.28, 0.20
cs = coll("Sandbags")


def sandbag_post(p, seed):
    rng = random.Random(seed)
    for k in range(LAYERS):
        y = 0.10 + k * PITCH
        for s in (-1, 1):
            m = "sandbag_olive" if rng.random() < 0.28 else "sandbag"
            j = rng.uniform(-0.012, 0.012)
            if k % 2 == 0:
                bag(p, (0.5 + j, y, 0.5 + s * 0.14), BL, BW, BH, rng.uniform(-3, 3), m, rng)
            else:
                bag(p, (0.5 + s * 0.14, y, 0.5 + j), BL, BW, BH, 90 + rng.uniform(-3, 3), m, rng)


def sandbag_side(p, seed):
    """North arm (z 0 .. 0.22): header bags across the wall + a half-offset stretcher on alternate layers."""
    rng = random.Random(seed)
    for k in range(LAYERS):
        y = 0.10 + k * PITCH
        m = "sandbag_olive" if rng.random() < 0.28 else "sandbag"
        bag(p, (0.5 + rng.uniform(-0.01, 0.01), y, 0.115), BL * 0.98, 0.235, BH, rng.uniform(-2.5, 2.5), m, rng)


p = Piece("sandbags_post"); sandbag_post(p, 7); fix_normals(p); p.finish(smooth=True, coll=cs)
p = Piece("sandbags_side"); sandbag_side(p, 8); fix_normals(p); p.finish(smooth=True, coll=cs)
p = Piece("sandbags_inv"); sandbag_post(p, 7)
sandbag_side(p, 8)
q = Piece("_tmp"); sandbag_side(q, 9)
for v in q.bm.verts:
    v.co = Vector((1 - v.co.x, v.co.y, 1 - v.co.z))
tmp_me = bpy.data.meshes.new("_tmp"); q.bm.to_mesh(tmp_me); q.bm.free()
p.bm.from_mesh(tmp_me)
for f in p.bm.faces:
    if f not in p.uvscale:
        p.uvscale[f] = 1.0
fix_normals(p); p.finish(smooth=True, coll=cs)


# ================================================================== BARBED WIRE FENCE
cw = coll("BarbedWire")
STRANDS = [0.17, 0.39, 0.61, 0.83]


def wire_post(p):
    # wooden picket, octagonal, slightly sharpened, with wire wraps
    sides = 8; r = 0.055
    ring = lambda y, rr: [(0.5 + rr * math.cos(2 * math.pi * i / sides), y, 0.5 + rr * math.sin(2 * math.pi * i / sides)) for i in range(sides)]
    verts = ring(0.0, r) + ring(0.96, r) + [(0.5, 1.0, 0.5)]
    faces = []
    for i in range(sides):
        j = (i + 1) % sides
        faces.append((i, i + sides, j + sides, j))
        faces.append((i + sides, 2 * sides, j + sides))
    faces.append(tuple(range(sides)))
    p.add_faces(verts, faces, "wood")
    for y in STRANDS:
        pts = [(0.5 + 0.066 * math.cos(a), y, 0.5 + 0.066 * math.sin(a)) for a in [2 * math.pi * i / 8 for i in range(9)]]
        p.tube(pts, 0.012, "wire", sides=3)


def strand(p, y0, sag, seed, side=0.0):
    rng = random.Random(seed)
    N = 6
    pts = []
    for i in range(N + 1):
        t = i / N                                    # 0 at the post, 1 at the block edge (midspan)
        z = 0.5 - 0.5 * t
        y = y0 - sag * (1 - (1 - t) ** 2)            # flat tangent at midspan -> seamless with the neighbour block
        pts.append((0.5 + side, y, z))
    p.tube(pts, 0.011, "wire", sides=4)
    # twisted second strand
    pts2 = []
    for i in range(N * 2 + 1):
        t = i / (N * 2)
        z = 0.5 - 0.5 * t
        y = y0 - sag * (1 - (1 - t) ** 2)
        a = t * 6 * math.pi
        pts2.append((0.5 + side + 0.010 * math.cos(a), y + 0.010 * math.sin(a), z))
    p.tube(pts2, 0.007, "wire", sides=3)
    # 4-point barbs every ~0.125
    for zc in (0.44, 0.315, 0.19, 0.065):
        t = (0.5 - zc) / 0.5
        y = y0 - sag * (1 - (1 - t) ** 2)
        for a in (45, 135):
            ar = math.radians(a + rng.uniform(-15, 15))
            d = Vector((math.cos(ar), math.sin(ar), 0)) * 0.034
            c = Vector((0.5 + side, y, zc))
            p.tube([tuple(c - d), tuple(c + d)], 0.006, "wire", sides=3, caps=True)


def wire_side(p):
    for i, y in enumerate(STRANDS):
        strand(p, y, 0.035, 100 + i)
    # diagonal tie wire between the outer strands
    pts = [(0.5, STRANDS[0] + 0.01, 0.48), (0.5, STRANDS[-1] - 0.03, 0.02)]
    p.tube(pts, 0.006, "wire", sides=3)


p = Piece("barbed_wire_post"); wire_post(p); p.finish(coll=cw)
p = Piece("barbed_wire_side"); wire_side(p); p.finish(coll=cw)
p = Piece("barbed_wire_inv"); wire_post(p); wire_side(p)
q = Piece("_tmp2"); wire_side(q)
for v in q.bm.verts:
    v.co = Vector((1 - v.co.x, v.co.y, 1 - v.co.z))
tmp_me = bpy.data.meshes.new("_tmp2"); q.bm.to_mesh(tmp_me); q.bm.free()
p.bm.from_mesh(tmp_me)
p.finish(coll=cw)

# ================================================================== CONCERTINA (razor wire coil)
cc = coll("Concertina")
CR, CH, LOOPS = 0.40, 0.44, 3          # radius, axis height, loops per half block (phase 90 deg at z = 0 and z = 0.5)


def coil_arm(p, seed):
    rng = random.Random(seed)
    seg = 18 * LOOPS
    pts = []
    for i in range(seg + 1):
        t = i / seg
        z = 0.5 - 0.5 * t
        a = math.pi / 2 + t * LOOPS * 2 * math.pi
        rr = CR * (1 + 0.04 * math.sin(a * 3 + seed))
        pts.append((0.5 + rr * math.cos(a), CH + rr * math.sin(a), z))
    p.tube(pts, 0.010, "wire", sides=3)
    # razor barbs: small double-sided diamonds every few segments
    for i in range(2, seg, 3):
        c = Vector(pts[i]); d = (Vector(pts[i + 1]) - Vector(pts[i - 1])).normalized()
        n = (c - Vector((0.5, CH, c.z))).normalized()
        s = d.cross(n).normalized()
        L = 0.030; Wd = 0.010
        quad = [c - d * L, c + s * Wd, c + d * L, c - s * Wd]
        p.add_faces([tuple(v) for v in quad], [(0, 1, 2, 3), (3, 2, 1, 0)], "wire")
    # clips joining adjacent loops
    for i in range(0, seg, 9):
        a = Vector(pts[i]); b = Vector(pts[min(seg, i + 18)]) if i + 18 <= seg else None
        if b is not None:
            p.tube([tuple(a), tuple(b)], 0.006, "wire", sides=3)


def flip(p_src_fn, seed):
    q = Piece("_tmpc")
    p_src_fn(q, seed)
    for v in q.bm.verts:
        v.co = Vector((1 - v.co.x, v.co.y, 1 - v.co.z))
    me = bpy.data.meshes.new("_tmpc"); q.bm.to_mesh(me); q.bm.free()
    return me


p = Piece("concertina_side"); coil_arm(p, 3); p.finish(coll=cc)
p = Piece("concertina_single"); coil_arm(p, 3); p.bm.from_mesh(flip(coil_arm, 3))
# ground pickets holding a lone coil
for z in (0.12, 0.88):
    p.box((0.5, 0.42, z), (0.03, 0.84, 0.03), "steel")
p.finish(coll=cc)

# ================================================================== CZECH HEDGEHOG
ch = coll("CzechHedgehog")


def hedgehog(p):
    Lb, leg, t = 1.62, 0.10, 0.016
    prof = [(0, 0), (leg, 0), (leg, t), (t, t), (t, leg), (0, leg)]       # L-angle in (z, y)
    # orientation: three beams along the axes, the (1,1,1) diagonal turned up
    up = Vector((1, 1, 1)).normalized()
    R = up.rotation_difference(Vector((0, 1, 0))).to_matrix()
    R = rot_y(15) @ R
    hgt = Lb / math.sqrt(3) / 2
    centre = Vector((0.5, hgt + 0.01, 0.5))
    for axis in range(3):
        q = Piece("_beam")
        q.prism([(z - leg / 2 + 0.01, y - leg / 2 + 0.01) for (z, y) in prof], -Lb / 2, Lb / 2, "steel", uv=0.6)
        # rotate beam from X to its axis (and twist it so the angles nest)
        A = [Matrix.Identity(3), Matrix.Rotation(math.radians(90), 3, "Z"), Matrix.Rotation(math.radians(-90), 3, "Y")][axis]
        tw = Matrix.Rotation(math.radians(90 * axis), 3, "X")
        for v in q.bm.verts:
            v.co = R @ (A @ (tw @ v.co)) + centre
        me = bpy.data.meshes.new("_beam"); q.bm.to_mesh(me); q.bm.free()
        p.bm.from_mesh(me)
    # gusset plates + bolts at the crossing
    for axis in range(3):
        n = R @ [Vector((1, 0, 0)), Vector((0, 1, 0)), Vector((0, 0, 1))][axis]
        p.box(tuple(centre + n * 0.0), (0.24, 0.24, 0.012), "steel", rot=R @ [Matrix.Rotation(math.radians(90), 3, "Y"), Matrix.Rotation(math.radians(90), 3, "X"), Matrix.Identity(3)][axis], uv=1.0)
    for i in range(6):
        a = 2 * math.pi * i / 6
        d = R @ Vector((math.cos(a), math.sin(a), 0)) * 0.08
        p.box(tuple(centre + d + (R @ Vector((0, 0, 0.012)))), (0.025, 0.025, 0.025), "steel", rot=R)
    # cut feet: small base plates where the three lower ends touch the ground
    for axis in range(3):
        e = R @ ([Vector((-1, 0, 0)), Vector((0, -1, 0)), Vector((0, 0, -1))][axis] * (Lb / 2)) + centre
        p.box((e.x, 0.006, e.z), (0.13, 0.012, 0.13), "steel")


p = Piece("czech_hedgehog"); hedgehog(p); p.finish(coll=ch)

# ================================================================== DRAGON'S TEETH
cd = coll("DragonTeeth")


def teeth(p, seed):
    rng = random.Random(seed)
    B, T, H = 0.86, 0.34, 0.92
    lv = 4
    rings = []
    for k in range(lv + 1):
        f = k / lv
        y = 0.04 + H * f
        hw = (B + (T - B) * f) / 2
        ring = []
        for (sx, sz) in [(-1, -1), (1, -1), (1, 1), (-1, 1)]:
            for e in range(3):          # 3 points per side: corner, mid-chamfer pair
                pass
        # chamfered square: 8 points
        c = 0.035
        pts = [(-hw + c, -hw), (hw - c, -hw), (hw, -hw + c), (hw, hw - c), (hw - c, hw), (-hw + c, hw), (-hw, hw - c), (-hw, -hw + c)]
        jit = 0.0 if k in (0, lv) else 0.012
        ring = [(0.5 + x + rng.uniform(-jit, jit), y + rng.uniform(-jit, jit) * 0.5, 0.5 + z + rng.uniform(-jit, jit)) for (x, z) in pts]
        rings.append(ring)
    verts = [v for r in rings for v in r]
    faces = []
    for k in range(lv):
        for i in range(8):
            j = (i + 1) % 8
            a, b = k * 8 + i, k * 8 + j
            faces.append((a, b, b + 8, a + 8))
    faces.append(tuple(lv * 8 + i for i in range(8)))
    fs = p.add_faces(verts, faces, "concrete")
    for f in fs[:8]:
        f.material_index = ORDER.index("concrete_dark")   # damp, dirty foot
    # low foundation pad
    p.box((0.5, 0.02, 0.5), (0.98, 0.04, 0.98), "concrete_dark")
    # spalled chips on two edges
    for _ in range(3):
        y = rng.uniform(0.25, 0.75)
        hw = (B + (T - B) * (y - 0.04) / H) / 2
        sx, sz = rng.choice([(-1, -1), (1, 1), (1, -1)])
        p.box((0.5 + sx * hw * 0.95, y, 0.5 + sz * hw * 0.95), (0.05, 0.04, 0.05), "concrete_dark", rot=rot_y(45))


p = Piece("dragon_teeth"); teeth(p, 5); fix_normals(p); p.finish(coll=cd)

# ================================================================== JERSEY BARRIER
cj = coll("ConcreteBarrier")


def barrier(p):
    # F-shape profile (z across the wall, y up), base 0.62 wide, 0.82 high; z centred on 0.5
    half = [(0.31, 0.0), (0.31, 0.075), (0.235, 0.255), (0.08, 0.80), (0.07, 0.82)]
    prof = [(0.5 + z, y) for (z, y) in half] + [(0.5 - z, y) for (z, y) in reversed(half)]
    prof = list(reversed(prof))
    x0, x1 = 0.004, 0.996
    p.prism(prof, x0, x1, "concrete")
    # forklift slots: dark recess boxes slightly proud of the faces (cheap, no booleans in a block mesh)
    for xc in (0.25, 0.75):
        for s in (-1, 1):
            p.box((xc, 0.045, 0.5 + s * 0.311), (0.16, 0.07, 0.004), "concrete_dark")
    # reflective hazard panel on both upper faces
    for s in (-1, 1):
        a = math.atan2(0.80 - 0.255, 0.235 - 0.08)
        y = 0.6
        z = 0.5 + s * (0.08 + (0.80 - y) / math.tan(a) + 0.003)
        rot = Matrix.Rotation(-s * (math.pi / 2 - a), 3, "X")
        p.box((0.5, y, z), (0.36, 0.09, 0.004), "hazard", rot=rot)
    # lifting loops on top
    for xc in (0.22, 0.78):
        pts = [(xc + 0.05 * math.cos(a), 0.82 + 0.05 * math.sin(a), 0.5) for a in [math.pi * i / 6 for i in range(7)]]
        p.tube(pts, 0.009, "steel", sides=4)


p = Piece("concrete_barrier"); barrier(p); fix_normals(p); p.finish(coll=cj)

# ================================================================== CAMO NET
cn = coll("CamoNet")


def camo(p, seed):
    rng = random.Random(seed)
    N = 6
    verts = []
    for j in range(N + 1):
        for i in range(N + 1):
            x, z = i / N, j / N
            sag = 0.10 * math.sin(math.pi * x) * math.sin(math.pi * z)
            edge = i in (0, N) or j in (0, N)
            jx = 0.0 if edge else rng.uniform(-0.015, 0.015)
            verts.append((x, 0.93 - sag + (0.0 if edge else rng.uniform(-0.02, 0.02)), z + jx))
    faces = []
    for j in range(N):
        for i in range(N):
            a = j * (N + 1) + i
            faces.append((a, a + N + 1, a + N + 2, a + 1))
    p.add_faces(verts, faces, "camo_net")
    # hanging garnish tufts
    for _ in range(5):
        x, z = rng.uniform(0.15, 0.85), rng.uniform(0.15, 0.85)
        y = 0.93 - 0.10 * math.sin(math.pi * x) * math.sin(math.pi * z)
        h = rng.uniform(0.10, 0.20); w = 0.09
        a = math.radians(rng.uniform(0, 180))
        dx, dz = math.cos(a) * w, math.sin(a) * w
        p.add_faces([(x - dx, y, z - dz), (x + dx, y, z + dz), (x + dx * 0.6, y - h, z + dz * 0.6), (x - dx * 0.6, y - h, z - dz * 0.6)],
                    [(0, 1, 2, 3)], "camo_net")


p = Piece("camo_net"); camo(p, 9); p.finish(double_sided=True, coll=cn)

# ------------------------------------------------------------------ cleanup temp meshes, export, save
for me in list(bpy.data.meshes):
    if me.name.startswith("_") and me.users == 0:
        bpy.data.meshes.remove(me)

EXPORT = ["sandbags_post", "sandbags_side", "sandbags_inv", "barbed_wire_post", "barbed_wire_side", "barbed_wire_inv",
          "concertina_side", "concertina_single", "czech_hedgehog", "dragon_teeth", "concrete_barrier", "camo_net"]
bpy.context.view_layer.update()
stats = {}
for name in EXPORT:
    ob = bpy.data.objects[name]
    for o in bpy.data.objects:
        o.select_set(o == ob)
    bpy.context.view_layer.objects.active = ob
    path = os.path.join(OUT, name + ".obj")
    bpy.ops.wm.obj_export(filepath=path, export_selected_objects=True, export_triangulated_mesh=True, export_materials=True,
                          export_uv=True, export_normals=True, forward_axis="NEGATIVE_Z", up_axis="Y", apply_modifiers=True,
                          export_animation=False)
    mtl = path[:-4] + ".mtl"
    if os.path.exists(mtl):
        os.remove(mtl)
    stats[name] = sum(len(p.vertices) - 2 for p in ob.data.polygons)
print("FORT_TRIS", stats)

# lights/camera for previews + save
bpy.ops.wm.save_as_mainfile(filepath=ROOT + "/models/fortifications.blend", compress=True)
print("FORT_DONE")
