# STEP 2: frame, axles, suspension, driveline, wheels (Ural-375D 6x6, real metres)
WR, WW = 0.60, 0.38            # tyre radius / width (14.00-20)
TRACK = 2.00
AXLES = {"F": -2.45, "M": 1.075, "R": 2.475}
ZW = WR


def build_wheel(tag, y, side):
    """side=+1 left(+X), -1 right. Wheel built around its own centre, outward = side*X."""
    cx = side * TRACK / 2
    grp = "Wheel_" + tag + ("L" if side > 0 else "R")
    parts = []
    # --- tyre carcass (lathe of rounded cross-section)
    prof = [(0.355, -0.17), (0.46, -0.19), (0.54, -0.19), (0.583, -0.172), (0.60, -0.13),
            (0.60, 0.13), (0.583, 0.172), (0.54, 0.19), (0.46, 0.19), (0.355, 0.17)]
    bm = bm_lathe(prof, (cx, y, ZW), 0, seg=28)
    uvl = bm.loops.layers.uv.verify()
    for f in bm.faces:
        for l in f.loops:
            co = l.vert.co
            ang = math.atan2(co.z - ZW, co.y - y) / (2 * PI) + 0.5
            rad = math.hypot(co.z - ZW, co.y - y)
            l[uvl].uv = (ang, rad)
        us = [l[uvl].uv[0] for l in f.loops]
        if max(us) - min(us) > 0.5:
            for l in f.loops:
                if l[uvl].uv[0] < 0.5:
                    l[uvl].uv = (l[uvl].uv[0] + 1.0, l[uvl].uv[1])
        tread = abs(f.normal.x) < 0.5
        for l in f.loops:
            co = l.vert.co
            u = l[uvl].uv[0] * 2.0
            if tread:
                l[uvl].uv = (u, 0.5 + (co.x - cx + 0.19) / 0.38 * 0.5)
            else:
                rad = math.hypot(co.z - ZW, co.y - y)
                l[uvl].uv = (u, (rad - 0.35) / 0.25 * 0.5)
    if side < 0:
        bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
    parts.append(make_obj(grp + "_tyre", bm, "M_Tyre", grp, uv=None, smooth=True))
    # --- tread lugs (herringbone, alternating halves) - they change the silhouette
    nl = 22
    for k in range(nl):
        for half in (-1, 1):
            a = 2 * PI * (k + (0.5 if half > 0 else 0)) / nl
            r = 0.605
            ly = y + math.cos(a) * r
            lz = ZW + math.sin(a) * r
            bm = bm_box((0.15, 0.07, 0.03), (cx + half * 0.085, ly, lz),
                        rot=(a - PI / 2, 0, half * 0.35), bevel=0.0)
            uv_box(bm, 2.0)
            parts.append(make_obj(f"{grp}_lug{k}{half}", bm, "M_Tyre", grp, uv=None))
    # --- steel disc wheel: dished disc + rim flange (outer side)
    o = side
    rimprof = [(0.36, 0.17 * o), (0.36, 0.12 * o), (0.30, 0.10 * o), (0.22, 0.06 * o), (0.16, 0.07 * o), (0.10, 0.10 * o), (0.0, 0.10 * o)]
    if o < 0:
        rimprof = rimprof  # orientation fixed below
    bm = bm_lathe(rimprof, (cx, y, ZW), 0, seg=24)
    if side > 0:
        bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
    uv_box(bm, 1.5)
    parts.append(make_obj(grp + "_disc", bm, "M_Paint_Worn", grp, uv=None, smooth=True))
    # disc lightening holes (dark inserts) ×6
    for k in range(6):
        a = 2 * PI * k / 6 + 0.26
        parts.append(cyl(f"{grp}_hole{k}", 0.045, 0.012, (cx + o * 0.075, y + math.cos(a) * 0.235, ZW + math.sin(a) * 0.235),
                         "M_Bore", grp, axis=0, seg=10, smooth=True))
    # hub + cap
    parts.append(cyl(grp + "_hub", 0.13, 0.08, (cx + o * 0.13, y, ZW), "M_Paint_Worn", grp, axis=0, seg=16, bevel=0.01))
    parts.append(cyl(grp + "_cap", 0.075, 0.06, (cx + o * 0.19, y, ZW), "M_Steel", grp, axis=0, seg=12, bevel=0.008))
    # wheel nuts ×8 (hex)
    for k in range(8):
        a = 2 * PI * k / 8
        parts.append(cyl(f"{grp}_nut{k}", 0.018, 0.03, (cx + o * 0.115, y + math.cos(a) * 0.16, ZW + math.sin(a) * 0.16),
                         "M_Steel", grp, axis=0, seg=6, smooth=False))
    # CTIS valve + rotary joint on cap (Ural central tyre inflation)
    parts.append(cyl(grp + "_ctis", 0.022, 0.07, (cx + o * 0.235, y, ZW), "M_Brass", grp, axis=0, seg=8))
    bm = bm_tube_path([(cx + o * 0.24, y, ZW), (cx + o * 0.24, y + 0.06, ZW + 0.08), (cx + o * 0.16, y + 0.12, ZW + 0.20),
                       (cx + o * 0.12, y + 0.10, ZW + 0.30)], 0.011, 6)
    parts.append(make_obj(grp + "_hose", bm, "M_Rubber", grp, smooth=True))
    return grp


for tag, y in AXLES.items():
    for side in (1, -1):
        build_wheel(tag, y, side)

# ------------------------------------------------------------------ frame (ladder)
G = "Running"
for s in (1, -1):
    box(f"FrameRail{s}", (0.09, 6.75, 0.26), (s * 0.46, 0.02, 0.97), "M_Frame", G, bevel=0.01)
    box(f"FrameFlange{s}", (0.16, 6.75, 0.012), (s * 0.44, 0.02, 1.10), "M_Frame", G, bevel=0.004)
for i, y in enumerate((-3.25, -2.0, -0.9, 0.2, 1.75, 3.2)):
    box(f"CrossMember{i}", (0.84, 0.10, 0.18), (0, y, 0.97), "M_Frame", G, bevel=0.01)
# rivet rows on rails (level-2 detail)
for s in (1, -1):
    for k in range(26):
        y = -3.15 + k * 0.25
        cyl(f"Rivet{s}_{k}", 0.012, 0.012, (s * 0.508, y, 1.06), "M_Frame", G, axis=0, seg=6, smooth=False)
        cyl(f"Rivet{s}_{k}b", 0.012, 0.012, (s * 0.508, y, 0.88), "M_Frame", G, axis=0, seg=6, smooth=False)

# ------------------------------------------------------------------ axles (banjo housings + differentials)
for tag, y in AXLES.items():
    cyl(f"Axle{tag}", 0.065, 1.72, (0, y, ZW), "M_Frame", G, axis=0, seg=12)
    bm = bm_lathe([(0.0, -0.20), (0.16, -0.18), (0.22, -0.06), (0.22, 0.06), (0.16, 0.16), (0.0, 0.18)], (0, y, ZW), 1, seg=16)
    make_obj(f"Diff{tag}", bm, "M_Frame", G, smooth=True)
    cyl(f"DiffCover{tag}", 0.17, 0.03, (0, y + 0.19, ZW), "M_Frame", G, axis=1, seg=16, bevel=0.006)
    for k in range(10):
        a = 2 * PI * k / 10
        cyl(f"DiffBolt{tag}{k}", 0.01, 0.02, (math.cos(a) * 0.15, y + 0.205, ZW + math.sin(a) * 0.15), "M_Steel", G, axis=1, seg=6)
    for s in (1, -1):  # brake drums / hub housings (inboard)
        cyl(f"Drum{tag}{s}", 0.26, 0.14, (s * 0.80, y, ZW), "M_Frame", G, axis=0, seg=18, bevel=0.01)

# front steering: tie rod + drag link + steering box
cyl("TieRod", 0.022, 1.55, (0, AXLES["F"] - 0.22, ZW - 0.05), "M_Steel", G, axis=0, seg=8)
bm = bm_tube_path([(0.55, -2.95, 1.0), (0.62, -2.7, 0.85), (0.72, -2.45, 0.72)], 0.025, 8)
make_obj("DragLink", bm, "M_Steel", G, smooth=True)
box("SteeringBox", (0.16, 0.22, 0.2), (0.58, -3.05, 1.02), "M_Frame", G, bevel=0.02)

# ------------------------------------------------------------------ leaf springs
def leaf_pack(name, x, y0, y1, zc, camber, leaves=6, inverted=False):
    for i in range(leaves):
        L = (y1 - y0) * (1 - i * 0.12)
        yc = (y0 + y1) / 2
        n = 8
        secs = []
        for k in range(n + 1):
            t = k / n
            yy_ = yc - L / 2 + L * t
            zz = zc + (camber * (1 - (2 * t - 1) ** 2)) * (-1 if inverted else 1) - i * 0.018 * (1 if not inverted else -1)
            secs.append([Vector((x - 0.04, yy_, zz)), Vector((x + 0.04, yy_, zz))])
        bm = bm_sweep_ribbon(secs)
        bmesh.ops.solidify(bm, geom=list(bm.faces), thickness=0.014)
        make_obj(f"{name}_{i}", bm, "M_Frame", G)


for s in (1, -1):
    leaf_pack(f"LeafF{s}", s * 0.46, -3.05, -1.85, 0.72, -0.06)
    leaf_pack(f"LeafR{s}", s * 0.46, AXLES["M"] - 0.1, AXLES["R"] + 0.1, 0.70, 0.07, leaves=8, inverted=True)
    box(f"BogiePivot{s}", (0.16, 0.24, 0.30), (s * 0.46, (AXLES["M"] + AXLES["R"]) / 2, 0.80), "M_Frame", G, bevel=0.02)
    cyl(f"BogieShaft{s}", 0.07, 0.26, (s * 0.56, (AXLES["M"] + AXLES["R"]) / 2, 0.72), "M_Steel", G, axis=0, seg=12)
    for tag in ("M", "R"):  # U-bolts
        for dy in (-0.07, 0.07):
            box(f"UBolt{s}{tag}{dy}", (0.02, 0.02, 0.22), (s * 0.46 + 0.05, AXLES[tag] + dy, ZW + 0.06), "M_Steel", G, bevel=0.0)
    # torque rods (upper/lower reaction rods)
    for tag in ("M", "R"):
        cyl(f"TorqueRod{s}{tag}", 0.03, 0.62, (s * 0.35, (AXLES[tag] + (AXLES["M"] + AXLES["R"]) / 2) / 2, 0.95), "M_Frame", G, axis=1, seg=8)
    box(f"FrontShock{s}", (0.07, 0.07, 0.36), (s * 0.38, -2.25, 0.86), "M_Frame", G, rot=(0.25, 0, 0), bevel=0.01)

# ------------------------------------------------------------------ driveline
box("Gearbox", (0.42, 0.55, 0.42), (0, -1.55, 0.95), "M_Frame", G, bevel=0.04)
box("TransferCase", (0.50, 0.40, 0.38), (0.0, -0.55, 0.82), "M_Frame", G, bevel=0.04)
for (a, b) in (((0, -1.25, 0.92), (0, -0.75, 0.84)), ((0, -0.75, 0.74), (0, AXLES["F"] + 0.2, 0.62)),
               ((0, -0.35, 0.76), (0, AXLES["M"] - 0.2, 0.64)), ((0, AXLES["M"] + 0.2, 0.62), (0, AXLES["R"] - 0.2, 0.62))):
    va, vb = Vector(a), Vector(b)
    bm = bm_tube_path([va, vb], 0.045, 8)
    make_obj("Shaft", bm, "M_Steel", G, smooth=True)
    for p in (va, vb):  # U-joints
        box("UJoint", (0.1, 0.07, 0.1), tuple(p), "M_Steel", G, bevel=0.012)
print("chassis parts:", len(col_get("BUILD").objects))
