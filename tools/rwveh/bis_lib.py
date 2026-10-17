# Bismarck (reply 10) helper library - exec()'d by tools/rwveh/bismarck.py after run.sh's lib concatenation.
# Ship frame: s = metres from the stern tip (0) to the stem (251); Blender y = 125.5 - s (bow at -Y), +X = port, z = keel up.
# Sources (refs/): kbismarck.com drawing of 24 May 1941 (11.81 px/m, side + top), the-blueprints 1940-41 profile/plan.

SL = 251.0


def P(s, x, z):
    return Vector((x, 125.5 - s, z))


def interp(tab, s):
    if s <= tab[0][0]:
        return tab[0][1]
    for (a, va), (b, vb) in zip(tab, tab[1:]):
        if s <= b:
            t = (s - a) / (b - a) if b > a else 0.0
            return va + (vb - va) * t
    return tab[-1][1]


# deck-edge half-beam, measured every 5 m on the 24 May 1941 top view
HB = [(0, 0.0), (0.4, 1.15), (1.5, 2.15), (3.3, 3.26), (8.3, 5.2), (13.3, 6.65), (18.3, 7.9), (23.3, 9.1), (28.3, 10.1),
      (33.3, 10.97), (38.3, 11.6), (43.3, 12.3), (48.3, 13.1), (53.3, 13.76), (58.3, 14.44), (63.3, 15.03), (68.2, 15.62),
      (73.2, 16.05), (78.2, 16.47), (83.2, 16.9), (88.2, 17.2), (93.2, 17.48), (98.2, 17.74), (103.2, 17.91), (108, 18.0),
      (128, 18.0), (133.2, 17.91), (138.2, 17.74), (143.2, 17.57), (148.2, 17.23), (153.2, 16.9), (158.2, 16.47),
      (163.2, 16.05), (168.2, 15.62), (173.2, 15.11), (178.2, 14.5), (183.1, 13.93), (188.1, 13.25), (193.1, 12.57),
      (198.1, 11.81), (203.1, 11.05), (208.1, 10.37), (213.1, 9.70), (218.1, 8.85), (223.1, 7.75), (228.1, 6.56),
      (233.1, 5.38), (238.1, 4.28), (243.1, 2.92), (248.1, 1.57), (250.3, 0.55), (251, 0.0)]
# deck-edge height (sheer) above the keel
ZD = [(0, 16.4), (18, 15.9), (34, 15.45), (60, 15.15), (190, 15.15), (200, 15.4), (213, 16.2), (232, 17.1), (245, 17.9),
      (251, 18.6)]
# keel / stem / counter profile (centre line underside)
KL = [(0, 16.2), (0.8, 13.5), (2.1, 11.0), (3.8, 9.3), (6.4, 7.8), (9.3, 6.8), (12.7, 6.0), (18.6, 5.5), (25.0, 5.1),
      (27.6, 4.9), (28.6, 2.6), (30.0, 0.6), (31.5, 0.0), (242.5, 0.0), (243.4, 0.4), (243.6, 2.5), (244.3, 5.9), (245.6, 9.3), (247.2, 12.3),
      (249.8, 16.1), (251, 18.6)]
ZWL = 9.3


def hb(s):
    return interp(HB, min(SL, max(0.0, s)))


def zd(s):
    return interp(ZD, s)


def keel(s):
    return interp(KL, s)


def full(s):
    """Section fullness: 1 along the parallel body, 0 at the fine ends."""
    if s < 25 or s > 238:
        return 0.0
    if s < 75:
        t = (s - 25) / 50.0
    elif s > 178:
        t = (238 - s) / 60.0
    else:
        return 1.0
    return t * t * (3 - 2 * t)


def wl_half(s):
    """Waterline half-beam: the deck plan shifted towards midships (flare at the bow, overhanging counter aft)."""
    if s > 170:
        sh = 5.4 * ((s - 170) / 75.6) ** 2
        return max(0.0, hb(s + sh)) if s + sh < SL else 0.0
    if s < 60:
        sh = 3.8 * ((60 - s) / 56.2) ** 2
        return max(0.0, hb(s - sh)) if s - sh > 0 else 0.0
    return hb(s)


NH = 16   # points per half section (constant so the rings can be skinned)
# buttock line of the flat run aft (the centre skeg follows KL down to the keel, the run above the props stays high)
BT = [(0, 16.2), (3.8, 9.3), (9.3, 6.8), (18.6, 5.6), (26.0, 4.9), (34.0, 3.0), (40.0, 1.2), (46.0, 0.0)]
SKEG = 0.7


def bfac(s):
    return max(full(s), min(1.0, max(0.0, (44.0 - s) / 14.0)) * 0.85)


def section(s):
    """Half section, keel centre -> deck edge, as NH (x, z) pairs."""
    k, top, d = keel(s), zd(s), hb(s)
    pts = []
    if k < ZWL - 0.3:
        w = max(0.05, wl_half(s))
        zb = max(k, interp(BT, s)) if s < 46 else k
        h = ZWL - zb
        R = min(2.7 + (1 - full(s)) * 3.0, w * 0.85, max(0.05, h * 0.85))
        bf = max(min(SKEG, w * 0.5), (w - R) * bfac(s))
        sk = min(SKEG, bf * 0.9)
        pts.append((0.0, k))
        pts.append((sk, k))
        pts.append((sk, zb if zb > k + 0.01 else k))
        pts.append((bf * 0.6 + sk * 0.4, zb))
        pts.append((bf, zb))
        k = zb
        for i in range(1, 7):                       # bilge arc
            a = math.radians(90.0 * i / 6)
            pts.append((bf + R * math.sin(a), k + R * (1 - math.cos(a))))
        x0, z0 = pts[-1]
        pts.append((x0 + (w - x0) * 0.5, z0 + (ZWL - z0) * 0.5))
        pts.append((w, ZWL))
        for i in (1, 2, 3):                         # topside with flare
            u = i / 3.0
            pts.append((w + (d - w) * u ** 1.4, ZWL + (top - ZWL) * u))
    else:                                           # overhanging counter / stem above the waterline: rounded section
        for i in range(NH):
            a = math.radians(90.0 * i / (NH - 1))
            pts.append((d * math.sin(a), top - (top - k) * math.cos(a)))
    assert len(pts) == NH, (s, len(pts))
    return pts


def stations():
    ss = [0.0, 0.15, 0.4, 0.8, 1.4, 2.1, 3.0, 3.8, 4.8, 6.4, 8.0, 9.3, 11.0, 12.7, 15.0, 17.0, 18.6, 20.3, 22.0, 24.0,
          25.0, 26.5, 27.6, 28.6, 29.3, 30.0, 31.5]
    s = 34.0
    while s < 226:
        ss.append(s)
        s += 3.0 if (s < 70 or s > 180) else 4.0
    ss += [226.0, 229.0, 232.0, 235.0, 237.0, 239.0, 240.5, 241.5, 242.3, 242.9, 243.4, 243.6, 244.0, 244.6, 245.2,
           245.6, 246.3, 247.2, 248.2, 249.0, 249.8, 250.4, 250.8, 251.0]
    return ss


def build_hull(name, grp, mats):
    """Hull shell (sides + bottom, open at the deck) with atlas UVs u = s/251, v = z/20."""
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    rings = []
    for s in stations():
        half = section(s)
        right = [(-x, z) for x, z in reversed(half[1:])]
        loop = right + half                          # starboard deck edge -> keel -> port deck edge
        rings.append((s, [bm.verts.new(P(s, x, z)) for x, z in loop]))
    for (s0, r0), (s1, r1) in zip(rings, rings[1:]):
        for i in range(len(r0) - 1):
            vs = (r0[i], r0[i + 1], r1[i + 1], r1[i])
            try:
                f = bm.faces.new(vs)
            except ValueError:
                continue
            for l in f.loops:
                co = l.vert.co
                l[uvl].uv = ((125.5 - co.y) / SL, co.z / 20.0)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4)
    bm.normal_update()
    # consistent winding from the ring order: flip everything once if the majority faces inwards
    score = 0.0
    for f in bm.faces:
        c = f.calc_center_median()
        score += f.normal.dot(Vector((c.x, 0, min(0.0, c.z - 6.0) * 0.3))) * f.calc_area()
    if score < 0:
        bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
        bm.normal_update()
    return aobj(name, bm, mats, grp, smooth=True, dirt=True)


def deck_patch_mat(s):
    # painted steel deck patches over the anchor gear (bow) and the stern (24 May 1941 plan view)
    return 1 if (8.9 <= s <= 21.2 or 221.8 <= s <= 234.1) else 0


def build_deck(name, grp, mats, camber=0.25, nx=10):
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    rows = []
    for s in stations():
        d, z = hb(s), zd(s)
        rows.append((s, [bm.verts.new(P(s, d * (2 * i / nx - 1), z + camber * (1 - (2 * i / nx - 1) ** 2))) for i in range(nx + 1)]))
    for (s0, r0), (s1, r1) in zip(rows, rows[1:]):
        for i in range(nx):
            try:
                f = bm.faces.new((r0[i], r1[i], r1[i + 1], r0[i + 1]))
            except ValueError:
                continue
            f.material_index = deck_patch_mat((s0 + s1) / 2)
            for l in f.loops:
                co = l.vert.co
                l[uvl].uv = (co.x / 4.0, (125.5 - co.y) / 4.0)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4)
    bm.normal_update()
    for f in bm.faces:
        if f.normal.z < 0:
            f.normal_flip()
    return aobj(name, bm, mats, grp, smooth=False, dirt=True)


def arc_pts(cs, cx, r, a0, a1, n):
    """Points (s, x) on a circle around (cs, cx), angles in degrees (0 = +s / forward, 90 = +x / port)."""
    out = []
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        out.append((cs + r * math.cos(a), cx + r * math.sin(a)))
    return out


def mirror_sx(half):
    """half: [(s, x>=0)] listed from the aft centre line round the port side to the fore centre line -> full outline."""
    full_ = list(half) + [(s, -x) for s, x in reversed(half) if abs(x) > 1e-6]
    return full_


def prism(name, outline, z0, z1, mat, grp, bevel=0.0, uvscale=0.25, top_mat=None, dirt=True):
    """Vertical extrusion of a (possibly concave) plan outline [(s, x)] between z0 and z1."""
    bm = bmesh.new()
    pts = [P(s, x, z0) for s, x in outline]
    # ensure counter-clockwise seen from above (+Z) so the extrusion normals face out
    area = 0.0
    for a, b in zip(pts, pts[1:] + pts[:1]):
        area += a.x * b.y - b.x * a.y
    if area < 0:
        pts.reverse()
    vs = [bm.verts.new(p) for p in pts]
    bmesh.ops.remove_doubles(bm, verts=vs, dist=1e-4)
    vs = [v for v in bm.verts]
    f = bm.faces.new(vs)
    r = bmesh.ops.extrude_face_region(bm, geom=[f])
    nv = [g for g in r["geom"] if isinstance(g, bmesh.types.BMVert)]
    bmesh.ops.translate(bm, verts=nv, vec=(0, 0, z1 - z0))
    bm.normal_update()
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    if top_mat is not None:
        for f in bm.faces:
            if f.normal.z > 0.9:
                f.material_index = 1
    if bevel > 0:
        bmesh.ops.bevel(bm, geom=[e for e in bm.edges if abs(e.verts[0].co.z - e.verts[1].co.z) > 1e-3], offset=bevel,
                        segments=1, affect='EDGES', profile=0.5)
    mats = [mat] if top_mat is None else [mat, top_mat]
    return aobj(name, bm, mats, grp, smooth=False, uv="box", uvscale=uvscale, dirt=dirt)


def rounded_rect(s0, s1, hw, r, n=4):
    """Full outline of a rectangle s0..s1, +-hw with corner radius r."""
    pts = []
    pts += arc_pts(s0 + r, hw - r, r, 180, 90, n)       # aft-port corner
    pts += arc_pts(s1 - r, hw - r, r, 90, 0, n)         # fore-port
    pts += arc_pts(s1 - r, -hw + r, r, 0, -90, n)       # fore-starboard
    pts += arc_pts(s0 + r, -hw + r, r, -90, -180, n)    # aft-starboard
    return pts


def oval(sc, xc, ls, lx, n=24):
    return [(sc + ls * math.cos(2 * PI * i / n), xc + lx * math.sin(2 * PI * i / n)) for i in range(n)]


class Acc:
    """Accumulates many small primitives into one bmesh per (group, material) -> one object (cheap to build)."""
    def __init__(self, name, grp, mats):
        self.name, self.grp, self.mats = name, grp, mats if isinstance(mats, list) else [mats]
        self.bm = bmesh.new()

    def add(self, bm2, mi=0):
        for f in bm2.faces:
            f.material_index = mi
        merge_bm(self.bm, bm2)

    def box(self, size, loc, rot=(0, 0, 0), mi=0):
        self.add(bm_box(size, loc, rot, 0.0, 1), mi)

    def rod(self, p0, p1, r, seg=6, mi=0, r2=None):
        if (Vector(p1) - Vector(p0)).length < 1e-4:
            return
        self.add(bm_cyl_between(p0, p1, r, seg, r2, True), mi)

    def tube(self, pts, r, seg=4, mi=0):
        self.add(bm_tube_path(pts, r, seg), mi)

    def done(self, smooth=False, uvscale=0.5, dirt=True):
        if len(self.bm.verts) == 0:
            self.bm.free()
            return None
        return aobj(self.name, self.bm, self.mats, self.grp, smooth=smooth, uv="box", uvscale=uvscale, dirt=dirt)


def new_objs(fn, *a, **kw):
    before = set(bpy.data.objects.keys())
    fn(*a, **kw)
    return [bpy.data.objects[n] for n in set(bpy.data.objects.keys()) - before]


def place(objs, M):
    for o in objs:
        if o.type == 'MESH':
            o.data.transform(M)
            o.data.update()


def turret_matrix(s, x, z, aft):
    """Turret-local (forward = -Y, origin on the pivot at the house floor) -> ship."""
    return Matrix.Translation(P(s, x, z)) @ (Matrix.Rotation(PI, 4, 'Z') if aft else Matrix.Identity(4))


# ------------------------------------------------------------------------------------------------ Arado Ar 196 A-3
# 11.0 m long, 12.44 m span, 4.45 m high; BMW 132K radial (r 0.68 cowl), 3-blade VDM prop (r 1.5), twin floats 7.4 m.
# Local frame: nose towards -Y, origin = midpoint between the float keels (the catapult trolley / water line), z up.
def ar_mats():
    amat("M_ArGreen", None, 0.7, 0.05, factor=(0.10, 0.14, 0.10, 1))     # RLM 72/73 splinter (upper)
    amat("M_ArBlue", None, 0.65, 0.05, factor=(0.40, 0.48, 0.58, 1))     # RLM 65 (floats / under)
    amat("M_ArCowl", None, 0.5, 0.3, factor=(0.08, 0.09, 0.08, 1))
    amat("M_ArGlass", None, 0.05, 0.3, factor=(0.15, 0.2, 0.22, 1), glass_alpha=0.35)
    amat("M_ArBlack", None, 0.6, 0.2, factor=(0.02, 0.02, 0.02, 1))
    amat("M_ArWhite", None, 0.6, 0.0, factor=(0.85, 0.85, 0.85, 1))


def arado(pre, grp, prop_grp=None):
    """Builds the Ar 196 in its local frame; returns the propeller hub position (local)."""
    FZ = 2.55                                   # fuselage axis height above the float keels
    fus = [(-4.35, 0.0), (-4.3, 0.55), (-4.0, 0.70), (-3.2, 0.72), (-2.4, 0.70), (-1.0, 0.66), (0.5, 0.60), (2.0, 0.48),
           (3.6, 0.32), (5.2, 0.18), (6.1, 0.08), (6.25, 0.0)]
    o = lathe_y(pre + "_fus", fus, (0, 0, FZ), ["M_ArGreen"], grp, seg=16)
    o.scale = (1.0, 1.0, 1.0)
    # deepen the fuselage (oval): scale the mesh in z around the axis
    o.data.transform(Matrix.Translation((0, 0, FZ)) @ Matrix.Diagonal((0.92, 1, 1.18, 1)) @ Matrix.Translation((0, 0, -FZ)))
    lathe_y(pre + "_cowl", [(-4.55, 0.25), (-4.5, 0.62), (-4.3, 0.70), (-3.5, 0.72), (-3.1, 0.70)], (0, 0, FZ + 0.05), ["M_ArCowl"], grp, seg=18)
    for k in range(9):                          # cylinder heads behind the cowl ring
        a = 2 * PI * k / 9
        acyl("%s_cyl%d" % (pre, k), 0.1, 0.25, (0.62 * math.cos(a), -4.52, FZ + 0.05 + 0.62 * math.sin(a)), "M_ArBlack", grp, axis=1, seg=6)
    hub = Vector((0, -4.95, FZ + 0.05))
    pg = prop_grp or grp
    lathe_y(pre + "_spin", [(-0.35, 0.0), (-0.25, 0.12), (0.0, 0.22), (0.3, 0.24)], tuple(hub), ["M_ArBlack"], pg, seg=12)
    for k in range(3):
        a = 2 * PI * k / 3 + 0.3
        d = Vector((math.cos(a), 0, math.sin(a)))
        abox("%s_blade%d" % (pre, k), (0.2, 0.05, 1.45), tuple(hub + d * 0.85), "M_ArBlack", pg, rot=(0, -a + PI / 2, 0), bevel=0.0)
    # canopy (long glazed greenhouse for pilot + observer) with frames
    chull(pre + "_canopy", [(x, y, z) for x in (-0.42, 0.42) for y, z in ((-2.4, FZ + 0.62), (-2.0, FZ + 1.12), (0.4, FZ + 1.15), (1.1, FZ + 0.75))] +
          [(0, -2.6, FZ + 0.55)], "M_ArGlass", grp, bevel=0.0, dirt=False)
    for y in (-2.0, -1.2, -0.4, 0.4):
        rod("%s_cfr%d" % (pre, int(y * 10)), (-0.44, y, FZ + 0.68), (0.44, y, FZ + 0.68), 0.025, "M_ArGreen", grp, seg=4)
    # rear MG 15 twin
    rod(pre + "_mg", (0, 0.8, FZ + 0.62), (0, 1.9, FZ + 0.85), 0.035, "M_ArBlack", grp, seg=5)
    # wing: low/mid wing, 12.44 m span, root chord 2.6 at y -2.4..0.2, tip chord 1.3; dihedral 4 deg
    for sx in (1, -1):
        pts = []
        for x, yl, c, t in ((0.5, -2.45, 2.6, 0.32), (2.6, -2.35, 2.35, 0.28), (5.0, -2.1, 1.85, 0.2), (6.22, -1.85, 1.2, 0.12)):
            z = FZ - 0.45 + abs(x) * math.tan(math.radians(4))
            for yy, tt in ((yl, 0.0), (yl + c * 0.3, t), (yl + c, 0.0)):
                pts += [(sx * x, yy, z + tt * 0.55), (sx * x, yy, z - tt * 0.45)]
        chull("%s_wing%d" % (pre, sx), pts, "M_ArGreen", grp, bevel=0.0)
        # wingtip balance / aileron hinge line, underside light blue panel
        abox("%s_wbelly%d" % (pre, sx), (5.2, 1.5, 0.02), (sx * 3.4, -1.3, FZ - 0.52 + 3.4 * math.tan(math.radians(4)) - 0.08),
             "M_ArBlue", grp, rot=(0, -sx * math.radians(4), 0), bevel=0.0)
        # national markings: Balkenkreuz (black cross with white borders) on the upper wing
        cz = FZ - 0.45 + 4.6 * math.tan(math.radians(4)) + 0.08
        for w_, h_, m_ in ((1.0, 0.34, "M_ArWhite"), (0.34, 1.0, "M_ArWhite"), (0.8, 0.18, "M_ArBlack"), (0.18, 0.8, "M_ArBlack")):
            abox("%s_bk%d_%s_%d" % (pre, sx, m_[-5:], int(w_ * 100)), (w_, h_, 0.09), (sx * 4.6, -1.45, cz + (0.012 if "Black" in m_ else 0.0)),
                 m_, grp, rot=(0, -sx * math.radians(4), 0), bevel=0.0, dirt=False)
        # floats (twin), struts
        fl = [(-3.9, 0.0), (-3.7, 0.18), (-3.0, 0.36), (-1.8, 0.45), (0.0, 0.45), (1.6, 0.36), (2.8, 0.22), (3.5, 0.0)]
        f = lathe_y("%s_float%d" % (pre, sx), fl, (sx * 1.8, -0.4, 0.42), ["M_ArBlue"], grp, seg=12)
        f.data.transform(Matrix.Translation((sx * 1.8, 0, 0.42)) @ Matrix.Diagonal((0.9, 1, 0.95, 1)) @ Matrix.Translation((-sx * 1.8, 0, -0.42)))
        for y0, y1 in ((-2.3, -1.9), (-0.2, 0.0)):
            rod("%s_fs%d_%d" % (pre, sx, int(y0 * 10)), (sx * 1.8, y0 - 0.2, 0.8), (sx * 1.3, y1, FZ - 0.5), 0.05, "M_ArBlue", grp, seg=6)
            rod("%s_fsb%d_%d" % (pre, sx, int(y0 * 10)), (sx * 1.8, y0, 0.8), (sx * 0.4, y1, FZ - 0.55), 0.04, "M_ArBlue", grp, seg=6)
        # bombs (SC 50) under the wings and 20 mm MG FF muzzles in the leading edge
        lathe_y("%s_bomb%d" % (pre, sx), [(-0.55, 0.0), (-0.45, 0.1), (-0.2, 0.1), (0.4, 0.07), (0.6, 0.0)], (sx * 2.8, -1.4, FZ - 0.78),
                ["M_ArBlack"], grp, seg=8)
        abox("%s_pylon%d" % (pre, sx), (0.06, 0.6, 0.3), (sx * 2.8, -1.45, FZ - 0.6), "M_ArBlue", grp, bevel=0.0)
        rod("%s_mgff%d" % (pre, sx), (sx * 2.2, -2.36, FZ - 0.36), (sx * 2.2, -2.8, FZ - 0.36), 0.04, "M_ArBlack", grp, seg=6)
    # tail: fin + rudder, tailplane with struts
    chull(pre + "_fin", [(x, y, z) for x in (-0.07, 0.07) for y, z in ((4.4, FZ + 0.3), (5.9, FZ + 0.1), (6.3, FZ + 1.75), (5.6, FZ + 1.75))],
          "M_ArGreen", grp, bevel=0.0)
    for sx in (1, -1):
        chull("%s_tail%d" % (pre, sx), [(sx * x, y, FZ + 0.42 + dz) for x, y in ((0.1, 4.9), (0.1, 6.1), (2.2, 5.35), (2.2, 5.95))
                                         for dz in (-0.04, 0.04)], "M_ArGreen", grp, bevel=0.0)
        rod("%s_tstrut%d" % (pre, sx), (sx * 1.2, 5.6, FZ + 0.4), (sx * 0.15, 5.4, FZ - 0.05), 0.025, "M_ArGreen", grp, seg=4)
    # exhaust, antenna mast, pitot
    rod(pre + "_ant", (0, -1.4, FZ + 0.7), (0, -1.2, FZ + 1.5), 0.02, "M_ArBlack", grp, seg=4)
    rod(pre + "_pitot", (5.5, -2.0, FZ - 0.25), (5.5, -2.6, FZ - 0.25), 0.015, "M_ArBlack", grp, seg=4)
    return hub
