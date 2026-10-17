"""r12 field blocks (part 1 of the building-blocks batch), Blender 5.x headless:
   blender -b --factory-startup --python tools/blender/field_blocks.py
Trench revetments, duckboards, firing step, 200 l drums, Egoza / Bruno razor wire, mines (PMN-2, TM-62M, MON-50, OZM-72,
F-1 tripwire), bear trap, warning signs. Same block-space conventions and OBJ export as fortifications.py (1 unit = 1 block,
origin = block corner, +Y up, model faces NORTH = -Z); real dimensions in metres. Saves models/field_blocks.blend."""
import bpy, bmesh, math, os, random
from mathutils import Vector, Matrix

_SRC = open("/home/user/tools/blender/fortifications.py").read()
exec(_SRC[:_SRC.index("# ================================================================== SANDBAGS")])
OUT = RES + "/models/block/field"
os.makedirs(OUT, exist_ok=True)
for k in ["plank", "tin", "earth", "drum", "drum_top", "olive", "rubber", "rust", "mon50", "sign"]:
    mat(k, "field_" + k + ".png" if k != "drum" and k != "sign" else ("field_drum_olive.png" if k == "drum" else "field_sign_mines.png"))
ORDER = list(MATS.keys())
TAU = 2 * math.pi


# ------------------------------------------------------------------ explicit-UV support (drums, signs, discs)
def faces_uv(p, verts, faces, uvs, matkey):
    """faces with explicit per-loop UVs: uvs[i][q] belongs to verts[faces[i][q]] (face loop order)."""
    out = p.add_faces(verts, faces, matkey)
    if not hasattr(p, "uvfix"):
        p.uvfix = {}
    for fa, uv in zip(out, uvs):
        p.uvfix[fa] = uv
    return out


_orig_finish = Piece.finish


def finish2(self, smooth=False, double_sided=False, coll=None):
    fix = getattr(self, "uvfix", {})
    if not fix:
        return _orig_finish(self, smooth, double_sided, coll)
    # remember explicit UVs by face index (bmesh face refs survive remove_doubles only partially -> store by centre)
    keyed = {}
    for f, uv in fix.items():
        if f.is_valid:
            keyed[tuple(round(c, 5) for c in f.calc_center_median())] = (uv, len(f.verts), [v.co.copy() for v in f.verts])
    ob = _orig_finish(self, smooth, double_sided, coll)
    me = ob.data
    uvl = me.uv_layers[0]
    for poly in me.polygons:
        k = tuple(round(c, 5) for c in poly.center)
        if k in keyed:
            uv, n, cos = keyed[k]
            if n != poly.loop_total:
                continue
            for li in range(poly.loop_total):
                co = me.vertices[me.loops[poly.loop_start + li].vertex_index].co
                j = min(range(n), key=lambda q: (cos[q] - co).length)
                uvl.data[poly.loop_start + li].uv = uv[j]
    return ob


Piece.finish = finish2


def cyl(p, c, r, h, matside, matcap=None, axis="Y", seg=16, uv_side=True, caps=(True, True), r2=None):
    """Cylinder centred at c along axis; side UV unwrapped (u = angle, v = height) when uv_side, discs mapped to the cap texture."""
    c = Vector(c); r2 = r if r2 is None else r2
    if axis == "Y":
        A, U, W = Vector((0, 1, 0)), Vector((1, 0, 0)), Vector((0, 0, 1))
    elif axis == "X":
        A, U, W = Vector((1, 0, 0)), Vector((0, 0, 1)), Vector((0, 1, 0))
    else:
        A, U, W = Vector((0, 0, 1)), Vector((0, 1, 0)), Vector((1, 0, 0))   # r13: U x W = -A like the Y / X frames
    bot = [c - A * h / 2 + (U * math.cos(TAU * k / seg) + W * math.sin(TAU * k / seg)) * r for k in range(seg)]
    top = [c + A * h / 2 + (U * math.cos(TAU * k / seg) + W * math.sin(TAU * k / seg)) * r2 for k in range(seg)]
    for k in range(seg):
        j = (k + 1) % seg
        vs = [tuple(bot[k]), tuple(bot[j]), tuple(top[j]), tuple(top[k])]
        if uv_side:
            u0, u1 = 1 - k / seg, 1 - (k + 1) / seg              # seen from outside the angle runs right-to-left
            faces_uv(p, vs, [(0, 3, 2, 1)], [[(u0, 0), (u0, 1), (u1, 1), (u1, 0)]], matside)   # uv listed in face order
        else:
            p.add_faces(vs, [(0, 3, 2, 1)], matside)
    mc = matcap or matside
    for ring, s, on in ((bot, -1, caps[0]), (top, 1, caps[1])):
        if not on:
            continue
        uv = [(0.5 + 0.5 * math.cos(TAU * k / seg), 0.5 + 0.5 * math.sin(TAU * k / seg)) for k in range(seg)]
        idx = tuple(range(seg)) if s < 0 else tuple(range(seg - 1, -1, -1))   # r13: caps face outward
        faces_uv(p, [tuple(v) for v in ring], [idx], [[uv[i] for i in idx]], mc)


def ring_torus(p, c, R, r, matkey, axis="Y", seg=20, sides=4):
    pts = []
    c = Vector(c)
    for k in range(seg + 1):
        a = TAU * k / seg
        if axis == "Y":
            pts.append(tuple(c + Vector((math.cos(a) * R, 0, math.sin(a) * R))))
        elif axis == "X":
            pts.append(tuple(c + Vector((0, math.sin(a) * R, math.cos(a) * R))))
        else:
            pts.append(tuple(c + Vector((math.cos(a) * R, math.sin(a) * R, 0))))
    p.tube(pts, r, matkey, sides=sides)


def quad_uv(p, a, b, c_, d, matkey, uv=((0, 0), (1, 0), (1, 1), (0, 1))):
    faces_uv(p, [a, b, c_, d], [(0, 1, 2, 3)], [list(uv)], matkey)


EXPORT = []


def done(p, **kw):
    fix_normals(p) if kw.pop("fixn", False) else None
    p.finish(**kw)
    EXPORT.append(p.name)


# ================================================================== TRENCH: revetments, duckboard, firing step
ct = coll("Trench")


def stake(p, x, z, top=1.0, r=0.045, mat="wood"):
    sides = 7
    ring = lambda y, rr: [(x + rr * math.cos(TAU * i / sides), y, z + rr * math.sin(TAU * i / sides)) for i in range(sides)]
    verts = ring(0.0, r) + ring(top, r * 0.92)
    faces = [(i, i + sides, (i + 1) % sides + sides, (i + 1) % sides) for i in range(sides)]
    faces.append(tuple(range(2 * sides - 1, sides - 1, -1)))     # r13: top cap faces up
    p.add_faces(verts, faces, mat)


p = Piece("trench_wall")                       # full earth block, revetment boards on the NORTH face (the trench side) + stakes
p.box((0.5, 0.5, 0.52), (1.0, 1.0, 0.96), "earth")
rng = random.Random(5)
for k in range(4):
    y0 = k * 0.25 + 0.004; y1 = y0 + 0.238
    off = rng.uniform(-0.004, 0.004)
    p.box((0.5, (y0 + y1) / 2, 0.022 + off), (1.0, y1 - y0, 0.03), "plank")
for x in (0.14, 0.86):
    stake(p, x, 0.0, top=1.0, r=0.036)
    for y in (0.3, 0.8):                       # wire ties stake -> board
        p.tube([(x - 0.05, y, 0.04), (x, y, -0.035), (x + 0.05, y, 0.04)], 0.006, "wire", sides=3)
done(p)

p = Piece("trench_wall_tin")                   # full earth block, corrugated sheet on the NORTH face held by angle-iron pickets
p.box((0.5, 0.5, 0.53), (1.0, 1.0, 0.94), "earth")
N = 32
prof = []
for i in range(N + 1):
    x = i / N
    prof.append((x, 0.035 + 0.02 * math.sin(x * TAU * 8)))
for i in range(N):
    (xa, za), (xb, zb) = prof[i], prof[i + 1]
    p.add_faces([(xa, 0.0, za), (xb, 0.0, zb), (xb, 1.0, zb), (xa, 1.0, za)], [(0, 3, 2, 1)], "tin")
for x in (0.10, 0.90):
    p.box((x, 0.5, 0.008), (0.05, 1.0, 0.006), "rust")
    p.box((x + 0.022, 0.5, 0.03), (0.006, 1.0, 0.04), "rust")
    for y in (0.15, 0.5, 0.85):
        cyl(p, (x, y, 0.0), 0.012, 0.012, "steel", axis="Z", seg=6, uv_side=False)
done(p)

p = Piece("duckboard")                         # trench grating: 2 stringers + 7 slats (top at 2 px)
for x in (0.2, 0.8):
    p.box((x, 0.035, 0.5), (0.09, 0.07, 1.0), "wood")
for k in range(7):
    z = (k + 0.5) / 7
    p.box((0.5, 0.098, z), (0.98, 0.028, 0.105), "plank")
done(p)

p = Piece("firing_step")                       # half-height earth step, plank riser (north) + 3 top boards
p.box((0.5, 0.23, 0.53), (1.0, 0.46, 0.94), "earth")
p.box((0.5, 0.24, 0.035), (1.0, 0.48, 0.05), "plank")
for k in range(3):
    p.box((0.5, 0.48, 0.17 + k * 0.32), (1.0, 0.04, 0.30), "plank")
for x in (0.1, 0.9):
    stake(p, x, 0.065, top=0.5, r=0.035)
done(p)

# ================================================================== 200 l DRUMS (d 585 mm, h 880 mm)
cd = coll("Drums")
DR, DH = 0.2925, 0.88


def drum(p, c, axis):
    c = Vector(c)
    cyl(p, c, DR, DH - 0.03, "drum", "drum_top", axis=axis, seg=24)
    A = {"Y": Vector((0, 1, 0)), "X": Vector((1, 0, 0)), "Z": Vector((0, 0, 1))}[axis]
    for t in (-DH / 6, DH / 6):                                         # rolling hoops
        ring_torus(p, c + A * t, DR + 0.004, 0.012, "drum", axis=axis, seg=24)
    for t in (-DH / 2 + 0.012, DH / 2 - 0.012):                         # chimes (rolled seams)
        ring_torus(p, c + A * t, DR - 0.004, 0.016, "steel", axis=axis, seg=24)
    for (u, w, rr) in ((0.17, -0.09, 0.03), (-0.11, 0.15, 0.02)):        # bungs on the top lid
        if axis == "Y":
            q = c + Vector((u, DH / 2 - 0.012, w))
        elif axis == "Z":
            q = c + Vector((u, w, -DH / 2 + 0.012))
        else:
            q = c + Vector((-DH / 2 + 0.012, w, u))
        cyl(p, q, rr, 0.03, "steel", axis=axis, seg=8, uv_side=False)


p = Piece("drum_up"); drum(p, (0.5, DH / 2, 0.5), "Y"); done(p)
p = Piece("drum_side"); drum(p, (0.5, DR + 0.005, 0.5), "Z")
for x in (0.22, 0.78):                                                   # wooden chocks
    p.box((x, 0.04, 0.5), (0.10, 0.08, 0.5), "wood", taper=(0.5, 1))
done(p)

# ================================================================== RAZOR WIRE: Egoza (flat barrier), Bruno spiral
cw = coll("RazorWire")


def closed_loop(p, pts, r, matkey, sides=4):
    """Closed tube around a planar loop (frames from the loop plane -> the seam closes, no open ends)."""
    n = len(pts)
    P = [Vector(q) for q in pts]
    C = sum(P, Vector()) / n
    nrm = Vector()
    for k in range(n):
        nrm += (P[k] - C).cross(P[(k + 1) % n] - C)
    nrm.normalize()
    verts = []
    for k in range(n):
        rad = (P[k] - C).normalized()
        for j in range(sides):
            a = TAU * j / sides + math.pi / sides
            verts.append(tuple(P[k] + (rad * math.cos(a) + nrm * math.sin(a)) * r))
    faces = []
    for k in range(n):
        for j in range(sides):
            a = k * sides + j; b = k * sides + (j + 1) % sides
            a2 = ((k + 1) % n) * sides + j; b2 = ((k + 1) % n) * sides + (j + 1) % sides
            faces.append((a, b, b2, a2))
    p.add_faces(verts, faces, matkey)


def razor_coil_loop(p, centre, R, tilt_deg, seed, nb=10, squash=0.96):
    """One loop of a stretched razor coil: a circle in the plane z = const (perpendicular to the wire line), leaned
    about X by tilt_deg so neighbouring loops cross -> the diamond pattern seen from the side, a round coil end-on.
    Razor tape = flat 4-sided tube + diamond razor barbs standing out of the tape."""
    rng = random.Random(seed)
    th = math.radians(tilt_deg)
    seg = 20
    c = Vector(centre)
    pts = []
    for i in range(seg):
        a = TAU * i / seg + rng.uniform(-0.03, 0.03)
        rr = R * (1 + 0.035 * math.sin(3 * a + seed))
        x = math.cos(a) * rr
        yy = math.sin(a) * rr * squash
        pts.append(c + Vector((x, yy * math.cos(th), yy * math.sin(th))))
    closed_loop(p, [tuple(q) for q in pts], 0.0080, "wire", sides=3)
    step = max(1, seg // nb)
    for i in range(0, seg, step):
        q = pts[i]; d = (pts[(i + 1) % seg] - pts[i - 1]).normalized()
        out = (q - c); out -= d * out.dot(d); out.normalize()
        s_ = d.cross(out).normalized()
        L = 0.026 + rng.uniform(-0.004, 0.004); Wd = 0.012
        for sg in ((1,) if (i // step) % 2 == 0 else (-1,)):  # barbs alternate between the tape edges
            b0 = q + s_ * sg * 0.006
            quad = [b0 - d * L * 0.5, b0 + s_ * sg * Wd + out * 0.004, b0 + d * L * 0.5, b0 + s_ * sg * 0.002]
            p.add_faces([tuple(v) for v in quad], [(0, 1, 2, 3), (3, 2, 1, 0)], "wire")
    return pts


def clip(p, a, b):
    p.tube([tuple(a), tuple(b)], 0.005, "steel", sides=4, caps=True)


def coil_arm(p, seed, R, H, loops, tilt, inner=None):
    """Arm of a coil from the block centre (z 0.5) to the north edge (z 0): `loops` loops spaced evenly, tilt alternates
    so the neighbour block's mirrored arm continues the sequence; adjacent loops clipped where they cross."""
    dz = 0.5 / loops
    prev = None
    for k in range(loops):
        z = 0.5 - (k + 0.5) * dz
        t = tilt if k % 2 == 0 else -tilt
        pts = razor_coil_loop(p, (0.5, H, z), R, t, seed + k)
        if prev is not None:
            for i in (2, 9, 16):                              # 3 clips per loop pair (alternating positions)
                j = (i + 3 * (k % 2)) % len(pts)
                clip(p, prev[j], pts[j])
        prev = pts
        if inner:
            Ri, Hi = inner
            razor_coil_loop(p, (0.5, Hi, z), Ri, -t, seed + 70 + k, nb=8)


def egoza_arm(p, seed):
    """SBB "Egoza": one razor-tape coil d 0.9 m (AKL-type) stretched along the line, axis 0.49 m above ground, carried by
    two tensioning wires (bottom + top of the coil) to the pickets."""
    coil_arm(p, seed, 0.44, 0.49, 4, 26)
    for y in (0.07, 0.91):
        p.tube([(0.5, y, 0.5), (0.5, y - 0.012, 0.25), (0.5, y, 0.0)], 0.005, "wire", sides=4)


def egoza_post(p):
    p.box((0.5, 0.52, 0.5), (0.05, 1.04, 0.006), "steel")       # angle-iron picket
    p.box((0.5 + 0.022, 0.52, 0.5), (0.006, 1.04, 0.05), "steel")
    for y in (0.07, 0.49, 0.91):
        ring_torus(p, (0.5, y, 0.5), 0.035, 0.006, "wire", seg=8, sides=4)


def bruno_arm(p, seed):
    """Bruno spiral: a double coil lying on the ground - outer loops d 0.95 m, inner coil d 0.62 m leaned the other way,
    so the side view shows the dense crossing mesh of the real thing."""
    coil_arm(p, seed, 0.47, 0.47, 5, 30, inner=(0.31, 0.32))
    for k in range(0, 5, 2):                                     # ground staples
        z = 0.5 - (k + 0.5) * 0.1
        for sx in (-1, 1):
            x = 0.5 + sx * 0.38
            p.tube([(x - 0.04 * sx, 0.0, z), (x, 0.09, z), (x + 0.04 * sx, 0.0, z)], 0.006, "steel", sides=4)


def flipz(fn, seed):
    q = Piece("_tmpf")
    fn(q, seed)
    for v in q.bm.verts:
        v.co = Vector((1 - v.co.x, v.co.y, 1 - v.co.z))
    me = bpy.data.meshes.new("_tmpf"); q.bm.to_mesh(me); q.bm.free()
    return me


p = Piece("egoza_post"); egoza_post(p); done(p)
p = Piece("egoza_side"); egoza_arm(p, 10); done(p)
p = Piece("egoza_single"); egoza_post(p); egoza_arm(p, 10); p.bm.from_mesh(flipz(egoza_arm, 20)); done(p)
p = Piece("bruno_side"); bruno_arm(p, 30); done(p)
p = Piece("bruno_single"); bruno_arm(p, 30); p.bm.from_mesh(flipz(bruno_arm, 40))
for z in (0.1, 0.9):
    p.box((0.5, 0.45, z), (0.03, 0.9, 0.03), "steel")
done(p)

# ================================================================== MINES & TRAPS
cm = coll("Mines")

p = Piece("mine_pmn2")            # PMN-2: 121 mm x 54 mm, black rubber cross-shaped pressure cap, side fuze boss
cyl(p, (0.5, 0.027, 0.5), 0.0605, 0.054, "olive", "olive", seg=16)
cyl(p, (0.5, 0.058, 0.5), 0.042, 0.010, "rubber", "rubber", seg=16)
for a in (0, 90):
    p.box((0.5, 0.064, 0.5), (0.07, 0.006, 0.018), "rubber", rot=rot_y(a))
cyl(p, (0.5, 0.03, 0.5 - 0.068), 0.012, 0.02, "steel", axis="Z", seg=8, uv_side=False)
# loose earth / grass ring so the mine sits "in" the ground
for k in range(10):
    a = TAU * k / 10
    p.box((0.5 + 0.085 * math.cos(a), 0.008, 0.5 + 0.085 * math.sin(a)), (0.04, 0.010, 0.025), "earth", rot=rot_y(-math.degrees(a)))
done(p)

p = Piece("mine_tm62")            # TM-62M: 320 mm x 128 mm, MVCh-62 fuze, carrying strap lugs
cyl(p, (0.5, 0.055, 0.5), 0.16, 0.11, "olive", "olive", seg=20)
ring_torus(p, (0.5, 0.11, 0.5), 0.155, 0.01, "olive", seg=20)
cyl(p, (0.5, 0.125, 0.5), 0.055, 0.03, "olive", "steel", seg=14)
cyl(p, (0.5, 0.145, 0.5), 0.032, 0.012, "steel", "rubber", seg=12)
for s in (-1, 1):
    p.box((0.5 + s * 0.16, 0.08, 0.5), (0.02, 0.03, 0.05), "steel")
done(p)

p = Piece("mine_mon50")           # MON-50: curved box 226 x 155 x 66 mm on scissor legs, fuze well on top, faces north (-Z)
Wm, Hm, Tm = 0.226, 0.155, 0.066
N = 8
yb, cz = 0.13, 0.5
front, back = [], []
for i in range(N + 1):
    t = i / N - 0.5
    x = 0.5 + t * Wm
    bow = 0.022 * (1 - (2 * t) ** 2)
    front.append((x, cz - Tm / 2 - bow))
    back.append((x, cz + Tm / 2 - bow * 0.6))
for i in range(N):
    (xa, za), (xb, zb) = front[i], front[i + 1]
    u0, u1 = i / N, (i + 1) / N
    quad_uv(p, (xa, yb, za), (xb, yb, zb), (xb, yb + Hm, zb), (xa, yb + Hm, za), "mon50",
            ((1 - u0, 0), (1 - u1, 0), (1 - u1, 1), (1 - u0, 1)))
    (xa2, za2), (xb2, zb2) = back[i], back[i + 1]
    p.add_faces([(xa2, yb, za2), (xb2, yb, zb2), (xb2, yb + Hm, zb2), (xa2, yb + Hm, za2)], [(3, 2, 1, 0)], "olive")
    p.add_faces([(xa, yb + Hm, za), (xb, yb + Hm, zb), (xb2, yb + Hm, zb2), (xa2, yb + Hm, za2)], [(0, 1, 2, 3)], "olive")
    p.add_faces([(xa, yb, za), (xb, yb, zb), (xb2, yb, zb2), (xa2, yb, za2)], [(3, 2, 1, 0)], "olive")
for (f, b) in ((front[0], back[0]), (front[-1], back[-1])):
    p.add_faces([(f[0], yb, f[1]), (b[0], yb, b[1]), (b[0], yb + Hm, b[1]), (f[0], yb + Hm, f[1])], [(0, 1, 2, 3), (3, 2, 1, 0)], "olive")
cyl(p, (0.5 + 0.06, yb + Hm + 0.012, cz), 0.014, 0.024, "olive", "steel", seg=8)        # fuze well + MD-5M detonator
cyl(p, (0.5 - 0.06, yb + Hm + 0.012, cz), 0.014, 0.024, "olive", "steel", seg=8)
p.tube([(0.56, yb + Hm + 0.024, cz), (0.62, yb + Hm + 0.05, cz + 0.02), (0.70, 0.02, cz + 0.12), (0.9, 0.01, cz + 0.2)], 0.004, "rubber", sides=3)
for s in (-1, 1):                                                                       # scissor legs
    x = 0.5 + s * 0.08
    p.tube([(x - 0.03, 0.0, cz - 0.05), (x + 0.03, yb + 0.02, cz + 0.03)], 0.005, "steel", sides=3)
    p.tube([(x + 0.03, 0.0, cz - 0.05), (x - 0.03, yb + 0.02, cz + 0.03)], 0.005, "steel", sides=3)
    p.tube([(x, 0.0, cz + 0.08), (x, yb + 0.02, cz + 0.03)], 0.005, "steel", sides=3)
done(p)

p = Piece("mine_ozm72")           # OZM-72 bounding mine: 108 x 172 mm, buried to the neck, MUV-2 fuze + trip loop
cyl(p, (0.5, 0.03, 0.5), 0.054, 0.06, "olive", "olive", seg=16)
cyl(p, (0.5, 0.075, 0.5), 0.02, 0.03, "steel", "steel", seg=10)
cyl(p, (0.5, 0.105, 0.5), 0.008, 0.035, "steel", "steel", seg=6)
p.tube([(0.5, 0.12, 0.5), (0.5, 0.135, 0.46), (0.5, 0.10, 0.42)], 0.003, "steel", sides=3)    # safety ring / trip loop
for k in range(12):
    a = TAU * k / 12
    p.box((0.5 + 0.075 * math.cos(a), 0.012, 0.5 + 0.075 * math.sin(a)), (0.05, 0.024, 0.035), "earth", rot=rot_y(-math.degrees(a)))
done(p)

p = Piece("tripwire_f1")          # F-1 grenade tied to a stake, trip wire runs 2 blocks to the EAST (+X) onto an end stake
stake(p, 0.42, 0.5, top=0.32, r=0.022)
stake(p, 2.58, 0.5, top=0.26, r=0.02)
# F-1 body: segmented "egg" (8 x 5 lattice) 55 mm x 117 mm with UZRGM fuze
gc = Vector((0.42 + 0.035, 0.22, 0.5))
rings = []
for j in range(7):
    t = j / 6
    yy = -0.05 + t * 0.1
    rr = 0.028 * math.sin(math.pi * (0.08 + 0.84 * t)) + 0.002
    rr *= 1.06 if j % 2 == 0 else 0.94                       # ridges
    rings.append([tuple(gc + Vector((rr * math.cos(TAU * k / 8), yy, rr * math.sin(TAU * k / 8)))) for k in range(8)])
verts = [v for r_ in rings for v in r_]
faces = [(j * 8 + k, j * 8 + (k + 1) % 8, (j + 1) * 8 + (k + 1) % 8, (j + 1) * 8 + k) for j in range(6) for k in range(8)]
faces += [tuple(range(7, -1, -1)), tuple(48 + k for k in range(8))]
p.add_faces(verts, faces, "olive")
cyl(p, tuple(gc + Vector((0, 0.065, 0))), 0.009, 0.03, "steel", "steel", seg=8)
p.tube([tuple(gc + Vector((0.012, 0.075, 0))), tuple(gc + Vector((0.03, 0.06, 0)))], 0.004, "steel", sides=3)   # lever
p.tube([(0.42, 0.18, 0.5), (0.48, 0.18, 0.5)], 0.006, "wire", sides=3)                     # lashing
p.tube([tuple(gc + Vector((0.0, 0.08, 0))), (0.6, 0.12, 0.5), (1.5, 0.10, 0.5), (2.58, 0.12, 0.5)], 0.0035, "wire", sides=3)
done(p)


def jaw(p, closed, s, rng):
    """Half of a bear-trap jaw (semicircle, r 0.17) with teeth; open = lying flat, closed = standing up."""
    seg = 10
    pts = []
    for i in range(seg + 1):
        a = math.pi * i / seg
        x = 0.5 + 0.17 * math.cos(a)
        w = 0.17 * math.sin(a)
        pts.append((x, w))
    tube = []
    for (x, w) in pts:
        if closed:
            tube.append((x, 0.05 + w * 0.98, 0.5 + s * 0.012))
        else:
            tube.append((x, 0.05, 0.5 + s * (0.02 + w)))
    p.tube(tube, 0.011, "rust", sides=4)
    for i in range(1, seg):
        a = Vector(tube[i])
        if closed:
            d = Vector((0, -0.035, -s * 0.0))
            tip = a + Vector((0, -0.03, -s * 0.012))
        else:
            tip = a + Vector((0, 0.035, 0))
        p.add_faces([tuple(a + Vector((-0.012, 0, 0))), tuple(a + Vector((0.012, 0, 0))), tuple(tip)], [(0, 1, 2), (2, 1, 0)], "rust")


for name, closed in (("bear_trap_open", False), ("bear_trap_closed", True)):
    p = Piece(name)
    rng = random.Random(4)
    p.box((0.5, 0.02, 0.5), (0.36, 0.02, 0.05), "rust")                                     # base bar
    cyl(p, (0.5, 0.035, 0.5), 0.04, 0.012, "rust", "rust", seg=10, uv_side=False)            # pan (trigger plate)
    for s in (-1, 1):
        jaw(p, closed, s, rng)
        x = 0.5 + s * 0.30                                                                   # leaf springs
        p.box((x, 0.03, 0.5), (0.26, 0.025, 0.045), "rust")
        ring_torus(p, (0.5 + s * 0.20, 0.05, 0.5), 0.03, 0.007, "rust", axis="X", seg=8, sides=3)
    pts = [(0.5 + 0.43, 0.02, 0.5)]                                                          # chain + stake
    for k in range(6):
        pts.append((0.93 + 0.012 * (k % 2), 0.015, 0.5 + 0.06 * (k + 1)))
    p.tube(pts, 0.008, "steel", sides=3)
    stake(p, 0.93, 0.9, top=0.08, r=0.018, mat="steel")
    done(p)

# ================================================================== WARNING SIGNS (front faces north = -Z)
csg = coll("Signs")


def sign_panel(p, cx, cy, z, size=0.62):
    h = size / 2
    t = 0.012
    quad_uv(p, (cx + h, cy - h, z), (cx - h, cy - h, z), (cx - h, cy + h, z), (cx + h, cy + h, z), "sign",
            ((0, 0), (1, 0), (1, 1), (0, 1)))
    p.add_faces([(cx - h, cy - h, z + t), (cx + h, cy - h, z + t), (cx + h, cy + h, z + t), (cx - h, cy + h, z + t)], [(0, 1, 2, 3)], "steel")
    for (a, b) in (((cx - h, cy - h), (cx + h, cy - h)), ((cx + h, cy - h), (cx + h, cy + h)), ((cx + h, cy + h), (cx - h, cy + h)),
                   ((cx - h, cy + h), (cx - h, cy - h))):
        p.add_faces([(a[0], a[1], z), (b[0], b[1], z), (b[0], b[1], z + t), (a[0], a[1], z + t)], [(3, 2, 1, 0)], "steel")


p = Piece("sign_post")
sign_panel(p, 0.5, 1.02, 0.44)
cyl(p, (0.5, 0.62, 0.48), 0.028, 1.24, "steel", "steel", seg=8, uv_side=False)
for y in (0.84, 1.20):                                                                       # pipe clamps
    p.box((0.5, y, 0.465), (0.12, 0.03, 0.03), "steel")
p.box((0.5, 0.02, 0.48), (0.16, 0.04, 0.16), "concrete")                                     # footing
done(p)

p = Piece("sign_wall")
sign_panel(p, 0.5, 0.55, 0.975)
for x in (0.24, 0.76):
    for y in (0.29, 0.81):
        cyl(p, (x, y, 0.972), 0.011, 0.008, "steel", "steel", axis="Z", seg=6, uv_side=False)
done(p)

# ------------------------------------------------------------------ cleanup temp meshes, export, save
for me in list(bpy.data.meshes):
    if me.name.startswith("_") and me.users == 0:
        bpy.data.meshes.remove(me)
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
# shared material file: every material samples a JSON texture variable of the same name
with open(os.path.join(OUT, "field.mtl"), "w") as f:
    for k in ORDER:
        f.write("newmtl %s\nKa 0.0 0.0 0.0\nKd 1.0 1.0 1.0\nKs 0.0 0.0 0.0\nd 1.0\nmap_Kd #%s\n\n" % (k, k))
print("FIELD_TRIS", stats)
print("FIELD_ORDER", ORDER)
bpy.ops.wm.save_as_mainfile(filepath=ROOT + "/models/field_blocks.blend", compress=True)
print("FIELD_DONE")
