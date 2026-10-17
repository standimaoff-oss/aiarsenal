# Tu-95MS STEP 1: scene, materials, fuselage (with glazing + cut-outs), radome, wings, nacelles, tail, decals.
# Reference (Tu-95MS): length 46.2 m (49.5 m incl. probe), span 50.10 m, height 12.12 m, wing area 289.9 m2,
# 35 deg quarter-chord sweep, 4x NK-12MP, AV-60K coaxial 4+4 props 5.6 m, main gear track 12.55 m, wheelbase ~14.8 m.
import json
reset_scene()
col_get("BUILD"); col_get("RIG")

# ---------------------------------------------------------------- materials
amat("M_Skin", "T_Skin95", 0.42, 0.35)
amat("M_SkinPanel", "T_Skin95", 0.5, 0.3, factor=(0.93, 0.94, 0.95, 1))      # replaced / repainted panels
amat("M_Radome", "T_Skin95", 0.55, 0.0, factor=(0.36, 0.38, 0.38, 1))       # dielectric radome, dark grey
amat("M_Glass", None, 0.05, 0.0, factor=(0.55, 0.66, 0.72, 1), vcol=False, glass_alpha=0.22)
amat("M_DarkMetal", "T_DarkMetal", 0.55, 0.7)
amat("M_Tire", "T_Tire", 0.9)
amat("M_Hub", None, 0.4, 0.6, factor=(0.62, 0.64, 0.64, 1))
amat("M_Gear", None, 0.35, 0.8, factor=(0.72, 0.74, 0.74, 1))
amat("M_Chrome", None, 0.15, 1.0, factor=(0.85, 0.86, 0.88, 1))
amat("M_Well", None, 0.7, 0.0, factor=(0.52, 0.58, 0.40, 1))                # yellow-green primer (wells, bay)
amat("M_Prop", None, 0.45, 0.6, factor=(0.30, 0.31, 0.32, 1))
amat("M_PropTip", None, 0.5, 0.0, factor=(0.85, 0.70, 0.12, 1))
amat("M_PropDisc", None, 0.5, 0.0, factor=(0.25, 0.25, 0.26, 1), vcol=False, glass_alpha=0.18)
amat("M_Decal", "T_Decals95", 0.6, alpha_clip=True, vcol=False)
amat("M_LampRed", None, 0.2, factor=(0.95, 0.1, 0.08, 1), vcol=False, emissive=(1.0, 0.1, 0.05, 1))
amat("M_LampGreen", None, 0.2, factor=(0.1, 0.9, 0.2, 1), vcol=False, emissive=(0.1, 1.0, 0.2, 1))
amat("M_LampWhite", None, 0.2, factor=(1.0, 0.97, 0.88, 1), vcol=False, emissive=(1.0, 0.97, 0.88, 1))
amat("M_Black", None, 0.6, 0.0, factor=(0.05, 0.05, 0.05, 1))
amat("M_Rubber", None, 0.85, 0.0, factor=(0.09, 0.09, 0.09, 1))
amat("M_Hole", None, 0.5, 0.0, factor=(1, 0, 1, 1), vcol=False)               # placeholder slot, faces deleted
DR = json.load(open(os.path.join(TEXA, "decal_rects.json")))["T_Decals95.png"]

# ---------------------------------------------------------------- fuselage
Y_NOSE, Y_TAIL = -23.0, 23.2
ZC = [(-21.0, 3.30), (-19.0, 3.35), (-12.0, 3.40), (10.0, 3.40), (13.0, 3.45), (16.0, 3.62), (18.5, 3.85), (21.0, 4.02), (22.4, 4.10)]
HW = [(-21.0, 1.30), (-20.0, 1.40), (-18.5, 1.45), (12.0, 1.45), (15.0, 1.36), (18.0, 1.16), (20.5, 0.97), (22.4, 0.76)]
HT = [(-21.0, 1.24), (-20.2, 1.42), (-19.4, 1.66), (-18.6, 1.82), (-17.6, 1.86), (-16.6, 1.80), (-15.5, 1.66), (-14.2, 1.52),
      (-13.0, 1.46), (-11.5, 1.45), (12.0, 1.45), (15.0, 1.36), (18.0, 1.14), (20.5, 0.95), (22.4, 0.75)]
HB = [(-21.0, 1.40), (-20.0, 1.48), (-19.0, 1.47), (-17.0, 1.45), (12.0, 1.45), (15.0, 1.28), (18.0, 1.02), (20.5, 0.86), (22.4, 0.70)]


def fus_prof(y):
    if y < -21.0:  # ellipsoidal nose, 2.0 m long
        f = math.sqrt(max(0.0, 1 - ((-21.0 - y) / 2.0) ** 2))
        return (3.30 - 0.15 * (1 - f), 1.30 * f, 1.24 * f, 1.40 * f, 2.0)
    if y > 22.4:  # tail cap (gunner glazing), 0.8 m
        f = math.sqrt(max(0.0, 1 - ((y - 22.4) / 0.8) ** 2))
        return (4.10, 0.76 * f, 0.75 * f, 0.70 * f, 2.0)
    n = 2.0 + (0.15 if -19.6 < y < -15.5 else 0.0)
    return (catmull(ZC, y), catmull(HW, y), catmull(HT, y), catmull(HB, y), n)


ys = [-21.0 - 2.0 * math.cos(PI / 2 * k / 10) for k in range(10)]          # -23 .. -21 dense at the tip
ys += [-21.0 + 0.3 * k for k in range(1, 28)]                              # cockpit -20.7 .. -12.9
ys += [-12.9 + 0.75 * k for k in range(1, 33)]                             # -12.15 .. 11.1
ys += [11.1 + 0.45 * k for k in range(1, 25)]                              # .. 21.9
ys += [22.4 - 0.5 + 0.5 * k for k in range(1, 2)] + [22.4 + 0.8 * math.sin(PI / 2 * k / 6) for k in range(1, 7)]
ys += [-12.4]
ys = sorted(set(round(y, 4) for y in ys))

GLASS = [(-19.5, -18.7, 62, 84), (-19.5, -18.7, 87, 93), (-19.5, -18.7, 96, 118),
         (-18.5, -17.0, 40, 58), (-18.5, -17.0, 122, 140), (-18.5, -17.3, 64, 82), (-18.5, -17.3, 98, 116),
         (-16.8, -16.2, 45, 58), (-16.8, -16.2, 122, 135),
         (-21.6, -20.9, 8, 26), (-21.6, -20.9, 154, 172),
         (21.4, 22.3, 35, 80), (21.4, 22.3, 100, 145), (22.35, 23.3, 20, 160)]
HOLES = [(-5.5, 5.5, 228, 312),          # bomb bay
         (-15.6, -12.4, 254, 286),        # nose gear well (+ crew entry hatch in its roof)
         (17.3, 18.1, 252, 288)]          # rear (tail cabin) entry hatch
BAY_Y0, BAY_Y1 = -5.5, 5.5


def fus_mat(y, a, z):
    if in_panes(y, a, HOLES):
        return 3
    if in_panes(y, a, GLASS):
        return 1
    if y < -20.4 and 205 < a < 335:
        return 2
    return 0


fus = loft("Fuselage", ys, fus_prof, ["M_Skin", "M_Glass", "M_Radome", "M_Hole"], "Body", seg=64, mat_fn=fus_mat)
delete_faces_mat(fus, 3)

# refuelling probe (on the nose, offset to the left of the windscreen)
probe = [(0.0, 0.07), (0.15, 0.09), (3.6, 0.09), (3.9, 0.07), (4.1, 0.11), (4.3, 0.06), (4.35, 0.0)]
lathe_y("Probe", [(-p[0], p[1]) for p in probe][::-1], (0.0, -19.4, 5.05), ["M_Skin"], "Body", seg=12)
abox("ProbeFairing", (0.32, 2.2, 0.22), (0.0, -19.9, 4.98), "M_Skin", "Body", rot=(-6 * D2R, 0, 0), bevel=0.08)

# ---------------------------------------------------------------- wings (fixed part 0 .. 0.73 chord)
TANLE = 0.754
ROOT_X, ROOT_LE, ROOT_C, ROOT_Z = 1.2, -7.75, 8.24, 3.90
TIP_X, TIP_LE, TIP_C, TIP_Z = 25.05, -7.6 + (25.05 - 1.4) * TANLE, 3.0, 3.28
FIX = 0.73


def wing_le(x):
    return -7.6 + (x - 1.4) * TANLE


def wing_c(x):
    return ROOT_C + (TIP_C - ROOT_C) * (x - ROOT_X) / (TIP_X - ROOT_X)


def wing_z(x):
    return ROOT_Z + (TIP_Z - ROOT_Z) * (x - ROOT_X) / (TIP_X - ROOT_X)


for side, sn in ((1, "L"), (-1, "R")):
    secs = wing_secs(ROOT_X, ROOT_LE, ROOT_Z, ROOT_C * FIX, TIP_X, TIP_LE, TIP_Z, TIP_C * FIX, t_root=0.15 / FIX,
                     t_tip=0.10 / FIX, n=10, twist_tip=-2.5, cam=0.02, side=side)
    # rounded tip: one extra short section
    w = surface("Wing_" + sn, secs, ["M_Skin"], "Body", nc=16, cap_tip=True)
    # leading-edge tip fairing + nav light (left red / right green)
    lp = Vector((TIP_X * side, TIP_LE + 0.9, TIP_Z + 0.02))
    aobj("NavLight_" + sn, bm_sphere(0.09, lp, 10, 6, (1.0, 1.6, 0.8)), "M_LampRed" if side > 0 else "M_LampGreen", "Body", dirt=False)
    # wing fences (three per wing, along the airflow)
    for fx in (9.6, 16.2, 20.6):
        c = wing_c(fx)
        y0 = wing_le(fx)
        zt = wing_z(fx) + 0.07 * c
        abox("Fence_%s_%d" % (sn, fx), (0.025, c * 0.80, 0.24), (fx * side, y0 + c * 0.40, zt + 0.10), "M_Skin", "Body", bevel=0.008)
    # static dischargers on the trailing edge outboard
    for k in range(4):
        fx = 19.0 + k * 1.6
        y1 = wing_le(fx) + wing_c(fx)
        rod("Discharger_%s_%d" % (sn, k), (fx * side, y1 - 0.05, wing_z(fx)), (fx * side, y1 + 0.35, wing_z(fx)), 0.012, "M_Black", "Body", seg=5)

# wing-root fairing (fillet) both sides
for side, sn in ((1, "L"), (-1, "R")):
    lathe_y("WingRootFillet_" + sn, [(0, 0.05), (0.8, 0.45), (3.0, 0.62), (6.0, 0.62), (8.5, 0.35), (9.6, 0.05)],
            (1.25 * side, ROOT_LE - 0.3, 3.95), ["M_Skin"], "Body", seg=16)

# ---------------------------------------------------------------- nacelles
NAC = {"1": (13.6, 3.15, False), "2": (6.3, 3.30, True), "3": (-6.3, 3.30, True), "4": (-13.6, 3.15, False)}  # 1 = outer left
PROP_Y = {}
NAC_PROF = {}
for key, (nx, nz, inner) in NAC.items():
    yp = wing_le(abs(nx)) - 4.6
    PROP_Y[key] = yp
    NAC_PROF[key] = None
    if inner:
        # inner nacelles carry the main-gear bogies -> deep fairing extending well aft of the trailing edge
        prof = [(1.0, 0.62), (1.4, 0.74), (2.2, 0.84), (3.5, 0.90), (5.0, 0.94), (6.5, 0.95), (8.5, 0.95), (10.5, 0.95),
                (12.0, 0.93), (13.5, 0.84), (15.0, 0.62), (16.3, 0.32), (17.0, 0.0)]
    else:
        prof = [(1.0, 0.60), (1.4, 0.72), (2.2, 0.82), (3.5, 0.85), (5.0, 0.83), (6.5, 0.78), (8.5, 0.66), (10.0, 0.45),
                (11.2, 0.15), (11.6, 0.0)]
    def nac_mf(i, k, prof=prof, yp=yp, inner=inner):
        a = 360.0 * (k + 0.5) / 28
        yy = yp + (prof[i][0] + prof[min(i + 1, len(prof) - 1)][0]) / 2
        return 1 if (inner and 230 < a < 310 and -0.7 < yy < 4.6) else 0
    NAC_PROF[key] = (prof, yp)
    nob = lathe_y("Nacelle_" + key, prof, (nx, yp, nz), ["M_Skin", "M_Hole"], "Body", seg=28, mat_fn=nac_mf)
    delete_faces_mat(nob, 1)
    # nacelle / wing pylon fairing (vertical web joining the nacelle top to the wing)
    wz = wing_z(abs(nx))
    abox("NacPylon_" + key, (0.9, 7.0 if inner else 5.5, max(0.2, wz - nz - 0.55)), (nx, yp + 6.8, (nz + 0.6 + wz) / 2),
         "M_Skin", "Body", bevel=0.25, smooth=True)
    # chin oil-cooler intake + dark mouth
    lathe_y("OilScoop_" + key, [(0.0, 0.26), (0.06, 0.31), (0.9, 0.34), (1.7, 0.22), (2.2, 0.0)], (nx, yp + 1.35, nz - 0.66),
            ["M_Skin"], "Body", seg=16)
    acyl("OilScoopMouth_" + key, 0.25, 0.04, (nx, yp + 1.37, nz - 0.66), "M_Black", "Body", axis=1, seg=16, dirt=False)
    # exhaust stubs (outboard side, discharging over the wing)
    sx = 1 if nx > 0 else -1
    for k, dy in enumerate((0.0, 0.55)):
        p0 = Vector((nx + sx * 0.70, wing_le(abs(nx)) - 0.9 + dy, nz + 0.42))
        rod("Exhaust_%s_%d" % (key, k), p0, p0 + Vector((sx * 0.12, 0.85, 0.22)), 0.17, "M_DarkMetal", "Body", seg=12, r2=0.15)
        acyl("ExhaustMouth_%s_%d" % (key, k), 0.14, 0.03, p0 + Vector((sx * 0.12, 0.86, 0.22)), "M_Black", "Body", axis=1, seg=12, dirt=False)
    # cowling access panels: latches row
    for k in range(5):
        abox("CowlLatch_%s_%d" % (key, k), (0.05, 0.12, 0.02), (nx + sx * 0.80, yp + 2.6 + k * 0.7, nz + 0.30), "M_Chrome", "Body", bevel=0.005)

# ---------------------------------------------------------------- tail
fin = wing_secs(0.0, 11.2, 4.6, 8.6 * 0.70, 0.0, 19.3, 12.1, 2.8 * 0.70, t_root=0.12 / 0.7, t_tip=0.09 / 0.7, n=6, vertical=True)
surface("Fin", fin, ["M_Skin"], "Body", nc=14, cap_tip=True)
dors = [dict(le=(0, 6.8, 4.70), cd=(0, 1, 0), ud=(1, 0, 0), c=7.4, t=0.07), dict(le=(0, 11.0, 5.75), cd=(0, 1, 0), ud=(1, 0, 0), c=3.6, t=0.10)]
surface("DorsalFin", dors, ["M_Skin"], "Body", nc=12, cap_tip=True)
STAB_ROOT = (0.9, 14.6, 3.78, 4.9)
STAB_TIP = (7.39, 14.6 + 6.49 * 0.84, 3.80, 1.9)
for side, sn in ((1, "L"), (-1, "R")):
    st = wing_secs(STAB_ROOT[0], STAB_ROOT[1], STAB_ROOT[2], STAB_ROOT[3] * 0.70, STAB_TIP[0], STAB_TIP[1], STAB_TIP[2],
                   STAB_TIP[3] * 0.70, t_root=0.11 / 0.7, t_tip=0.08 / 0.7, n=4, twist_tip=0.0, cam=0.0, side=side)
    surface("Stab_" + sn, st, ["M_Skin"], "Body", nc=12, cap_tip=True)
# fin-top light + tail cone antenna fairing
aobj("FinTipLight", bm_sphere(0.08, (0, 19.3 + 1.0, 12.13), 10, 6), "M_LampWhite", "Body", dirt=False)
# anti-collision beacons (top + bottom)
aobj("BeaconTop", bm_sphere(0.12, (0, -4.0, 4.86), 12, 6, (1, 1, 0.7)), "M_LampRed", "Body", dirt=False)
aobj("BeaconBottom", bm_sphere(0.12, (0, 8.0, 1.96), 12, 6, (1, 1, 0.7)), "M_LampRed", "Body", dirt=False)

# side observation blisters (rear fuselage) - glass domes in a metal ring
for side, sn in ((1, "L"), (-1, "R")):
    p = Vector((1.24 * side, 16.2, 3.75))
    aobj("Blister_" + sn, bm_sphere(0.42, p, 16, 8, (0.55, 1.0, 1.0)), "M_Glass", "Body", dirt=False)
    lathe_y("BlisterRing_" + sn, [(-0.03, 0.47), (0.0, 0.50), (0.03, 0.47)], (p.x - 0.1 * side, p.y, p.z), ["M_Skin"], "Body", seg=16, axis='X')

# ---------------------------------------------------------------- decals
fin_ob = bpy.data.objects["Fin"]
for side in (1, -1):
    decal("DecalFinStar_%d" % side, fin_ob, (0, 16.4, 8.4), (side, 0, 0), (0, 0, 1), 2.2, 2.2, DR["star"], "M_Decal", "Body", nx=4, ny=4)
    decal("DecalFinFlag_%d" % side, fin_ob, (0, 18.2, 10.7), (side, 0, 0), (0, 0, 1), 1.5, 0.68, DR["flag"], "M_Decal", "Body", nx=3, ny=2)
    decal("DecalNumber_%d" % side, fus, (1.3 * side, -21.2, 2.95), (side, 0, 0), (0, 0, 1), 1.2, 0.72, DR["number"], "M_Decal", "Body",
          nx=8, ny=3)
    decal("DecalName_%d" % side, fus, (1.45 * side, -14.6, 3.85), (side, 0, 0), (0, 0, 1), 2.0, 0.33, DR["name"], "M_Decal", "Body",
          nx=10, ny=2)
    decal("DecalProp_%d" % side, fus, (1.45 * side, -8.5, 3.3), (side, 0, 0), (0, 0, 1), 0.4, 3.0, DR["prop_stripe"], "M_Decal", "Body",
          nx=2, ny=8)
for side, sn in ((1, "L"), (-1, "R")):
    wob = bpy.data.objects["Wing_" + sn]
    decal("DecalWingStar_" + sn, wob, (19.5 * side, wing_le(19.5) + 2.0, wing_z(19.5) - 0.3), (0, 0, -1), (0, -1, 0), 2.4, 2.4, DR["star"],
          "M_Decal", "Body", nx=4, ny=4)
print("STEP1", stats(), "props at", PROP_Y)
