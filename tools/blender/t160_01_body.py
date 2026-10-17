# Tu-160 STEP 1: scene, materials, fuselage (canopy glazing, cut-outs), wing glove, variable-sweep outer wings,
# engine nacelles (4x NK-32 in two pairs) with intakes and nozzles, main-gear pods, fin + all-moving stabiliser, decals.
# Reference (Tu-160): length 54.1 m, span 55.7 m (20 deg) / 35.6 m (65 deg), height 13.1 m, wing area 232 m2 (20 deg),
# main gear track 5.4 m, wheelbase 17.88 m, 3-axle 6-wheel main bogies, twin nose wheels.
import json
reset_scene()
col_get("BUILD"); col_get("RIG")

# ---------------------------------------------------------------- materials (anti-flash white)
amat("M_Skin", "T_Skin160", 0.38, 0.15)
amat("M_SkinPanel", "T_Skin160", 0.45, 0.15, factor=(0.95, 0.95, 0.94, 1))
amat("M_Radome", "T_Skin160", 0.5, 0.0, factor=(0.90, 0.90, 0.86, 1))
amat("M_Glass", None, 0.05, 0.0, factor=(0.45, 0.52, 0.42, 1), vcol=False, glass_alpha=0.25)    # gold-tinted canopy
amat("M_DarkMetal", "T_DarkMetal", 0.5, 0.75)
amat("M_Burnt", "T_DarkMetal", 0.7, 0.6, factor=(0.55, 0.45, 0.38, 1))
amat("M_Tire", "T_Tire", 0.9)
amat("M_Hub", None, 0.4, 0.6, factor=(0.66, 0.68, 0.68, 1))
amat("M_Gear", None, 0.35, 0.6, factor=(0.82, 0.83, 0.82, 1))
amat("M_Chrome", None, 0.15, 1.0, factor=(0.85, 0.86, 0.88, 1))
amat("M_Well", None, 0.7, 0.0, factor=(0.62, 0.66, 0.58, 1))
amat("M_Decal", "T_Decals160", 0.6, alpha_clip=True, vcol=False)
amat("M_LampRed", None, 0.2, factor=(0.95, 0.1, 0.08, 1), vcol=False, emissive=(1.0, 0.1, 0.05, 1))
amat("M_LampGreen", None, 0.2, factor=(0.1, 0.9, 0.2, 1), vcol=False, emissive=(0.1, 1.0, 0.2, 1))
amat("M_LampWhite", None, 0.2, factor=(1.0, 0.97, 0.88, 1), vcol=False, emissive=(1.0, 0.97, 0.88, 1))
amat("M_Black", None, 0.6, 0.0, factor=(0.05, 0.05, 0.05, 1))
amat("M_Rubber", None, 0.85, 0.0, factor=(0.09, 0.09, 0.09, 1))
amat("M_Flame", None, 0.5, 0.0, factor=(1.0, 0.55, 0.22, 1), vcol=False, emissive=(1.0, 0.45, 0.12, 1), glass_alpha=0.55)
amat("M_Hole", None, 0.5, 0.0, factor=(1, 0, 1, 1), vcol=False)
DR = json.load(open(os.path.join(TEXA, "decal_rects.json")))["T_Decals160.png"]

# ---------------------------------------------------------------- fuselage (nose -Y)
NOSE = -29.6
ZC = [(-29.6, 3.95), (-26.0, 4.05), (-22.0, 4.12), (-18.0, 4.2), (12.0, 4.2), (16.0, 4.35), (20.0, 4.6), (23.6, 4.85)]
HW = [(-26.0, 1.22), (-24.5, 1.42), (-22.0, 1.52), (-18.0, 1.62), (-14.0, 1.75), (12.0, 1.75), (16.0, 1.45), (20.0, 0.95),
      (23.6, 0.42)]
HT = [(-26.0, 1.15), (-25.0, 1.45), (-24.0, 1.85), (-23.0, 2.02), (-21.5, 2.02), (-20.0, 1.94), (-18.0, 1.86), (-14.0, 1.80),
      (12.0, 1.80), (16.0, 1.5), (20.0, 1.0), (23.6, 0.45)]
HB = [(-26.0, 1.28), (-24.0, 1.48), (-20.0, 1.56), (-14.0, 1.60), (12.0, 1.60), (16.0, 1.30), (20.0, 0.80), (23.6, 0.40)]


def fus_prof(y):
    if y < -26.0:  # ogive radome
        t = (y - NOSE) / (-26.0 - NOSE)
        f = math.sin(PI / 2 * max(0.0, t)) ** 0.85
        return (3.95 + 0.10 * t, 1.22 * f, 1.15 * f, 1.28 * f, 2.0)
    n = 2.0 + 0.3 * clamp01((y + 18.0) / 4.0) * clamp01((14.0 - y) / 4.0)
    return (catmull(ZC, y), catmull(HW, y), catmull(HT, y), catmull(HB, y), n)


ys = [NOSE + (-26.0 - NOSE) * (1 - math.cos(PI / 2 * k / 12)) for k in range(13)]
ys += [-26.0 + 0.2 * k for k in range(1, 41)]                          # cockpit .. -18.0
ys += [-18.0 + 0.6 * k for k in range(1, 51)]                          # .. 12.0
ys += [12.0 + 0.5 * k for k in range(1, 24)]                           # .. 23.5
ys += [23.6, -24.7, -23.9, -22.3, -21.7, -19.8, -19.6, -19.0, -15.6, -13.5, -2.2, 0.6, 11.9]
ys = sorted(set(round(y, 4) for y in ys if NOSE <= y <= 23.6))
GLASS = [(-24.7, -23.9, 63, 88), (-24.7, -23.9, 92, 117),               # windscreen (2 panes + central post)
         (-23.8, -22.6, 38, 58), (-23.8, -22.6, 122, 142),               # pilots' side windows
         (-22.3, -21.7, 34, 46), (-22.3, -21.7, 134, 146)]               # navigators' small windows
HOLES = [(-13.5, -2.2, 240, 300), (0.6, 11.9, 240, 300),                 # two tandem weapons bays
         (-19.8, -15.6, 252, 288)]                                       # nose-gear well (+ crew entry ladder)
BAY1, BAY2 = (-13.5, -2.2), (0.6, 11.9)


def fus_mat(y, a, z):
    if in_panes(y, a, HOLES):
        return 3
    if in_panes(y, a, GLASS):
        return 1
    if y < -26.0:
        return 2
    return 0


fus = loft("Fuselage", ys, fus_prof, ["M_Skin", "M_Glass", "M_Radome", "M_Hole"], "Body", seg=64, mat_fn=fus_mat)
delete_faces_mat(fus, 3)
# canopy frames (window surrounds) + central windscreen post
for (y0, y1, a0, a1) in GLASS:
    for yy in (y0, y1):
        zc, hw, ht, hb, n = fus_prof(yy)
        pa = section_pt(math.radians(a0), zc, hw * 1.004, ht * 1.004, hb, n); pb = section_pt(math.radians(a1), zc, hw * 1.004, ht * 1.004, hb, n)
        pa.y = pb.y = yy
        rod("CanopyFrame_%s_%s" % (yy, a0), pa, pb, 0.025, "M_SkinPanel", "Body", seg=5)
# nose pitot boom + retractable refuelling probe (stowed, fairing ahead of the windscreen)
lathe_y("PitotBoom", [(-0.9, 0.0), (-0.85, 0.03), (0.0, 0.05), (0.05, 0.0)], (0, NOSE, 3.95), ["M_Chrome"], "Body", seg=10)
abox("ProbeFairing", (0.34, 1.9, 0.16), (0, -25.9, 5.12), "M_SkinPanel", "Body", rot=(-0.12, 0, 0), bevel=0.07, smooth=True)

# ---------------------------------------------------------------- wing glove (fixed centre section, blended with the body)
GLOVE_ROOT = (1.4, -17.6, 4.45, 26.4)            # x, LE y, z, chord (TE ~ at the nozzle exits)
GLOVE_TIP = (9.75, -2.6, 4.70, 6.6)
PIVOT_X, PIVOT_Y, PIVOT_Z = 9.6, -0.6, 4.70
for side, sn in ((1, "L"), (-1, "R")):
    secs = []
    for k in range(9):
        f = k / 8
        x = GLOVE_ROOT[0] + (GLOVE_TIP[0] - GLOVE_ROOT[0]) * f
        le = GLOVE_ROOT[1] + (GLOVE_TIP[1] - GLOVE_ROOT[1]) * f
        c = GLOVE_ROOT[3] + (GLOVE_TIP[3] - GLOVE_ROOT[3]) * f
        tabs = 1.45 - 0.75 * f                            # absolute thickness (m)
        secs.append(dict(le=(x * side, le, GLOVE_ROOT[2] + (GLOVE_TIP[2] - GLOVE_ROOT[2]) * f), cd=(0, 1, 0), ud=(0, 0, 1), c=c,
                         t=tabs / c, cam=0.0))
    surface("Glove_" + sn, secs, ["M_Skin"], "Body", nc=20, cap_tip=True)
    # pivot fairing (wing slot) at the glove tip
    lathe_y("PivotFairing_" + sn, [(0.0, 0.0), (0.5, 0.32), (2.5, 0.40), (5.5, 0.36), (7.0, 0.0)], (PIVOT_X * side + 0.05 * side, -3.0, PIVOT_Z),
            ["M_Skin"], "Body", seg=14)


def glove_le(x):
    f = (x - GLOVE_ROOT[0]) / (GLOVE_TIP[0] - GLOVE_ROOT[0])
    return GLOVE_ROOT[1] + (GLOVE_TIP[1] - GLOVE_ROOT[1]) * f


def glove_te(x):
    f = (x - GLOVE_ROOT[0]) / (GLOVE_TIP[0] - GLOVE_ROOT[0])
    return glove_le(x) + GLOVE_ROOT[3] + (GLOVE_TIP[3] - GLOVE_ROOT[3]) * f


# ---------------------------------------------------------------- outer wing panels (modelled at 20 deg sweep; node Wing_L/R)
W_ROOT_X, W_ROOT_LE, W_ROOT_C, W_Z0 = 9.9, -3.6, 6.2, 4.70
W_TIP_X, W_TIP_C, W_Z1 = 27.85, 1.9, 4.95
TAN20 = math.tan(math.radians(20))
WFIX = 0.72


def ow_le(x):
    return W_ROOT_LE + (x - W_ROOT_X) * TAN20


def ow_c(x):
    return W_ROOT_C + (W_TIP_C - W_ROOT_C) * (x - W_ROOT_X) / (W_TIP_X - W_ROOT_X)


def ow_z(x):
    return W_Z0 + (W_Z1 - W_Z0) * (x - W_ROOT_X) / (W_TIP_X - W_ROOT_X)


SLAT = 0.13
for side, sn in ((1, "L"), (-1, "R")):
    g = "Wing_" + sn
    # main box between slat (0.13c) and flaps (0.72c)
    secs = []
    for k in range(9):
        x = W_ROOT_X + (W_TIP_X - W_ROOT_X) * k / 8
        c = ow_c(x)
        secs.append(dict(le=(x * side, ow_le(x) + SLAT * c, ow_z(x)), cd=(0, 1, 0), ud=(0, 0, 1), c=(WFIX - SLAT) * c,
                         t=(0.105 - 0.025 * k / 8) / (WFIX - SLAT), cam=0.0))
    surface("OuterWing_" + sn, secs, ["M_Skin"], g, nc=14, cap_root=True, cap_tip=True)
    lp = Vector((W_TIP_X * side + 0.02 * side, ow_le(W_TIP_X) + 0.6, W_Z1))
    aobj("NavLight_" + sn, bm_sphere(0.08, lp, 10, 6, (0.8, 1.6, 0.8)), "M_LampRed" if side > 0 else "M_LampGreen", g, dirt=False)
    for k in range(3):
        fx = 22.0 + k * 1.9
        y1 = ow_le(fx) + ow_c(fx)
        rod("Discharger_%s_%d" % (sn, k), (fx * side, y1 - 0.05, ow_z(fx)), (fx * side, y1 + 0.32, ow_z(fx)), 0.012, "M_Black", g, seg=5)

# ---------------------------------------------------------------- engine nacelles (two pairs), intakes, nozzles
NAC_X, NAC_Z, NAC_HW, NAC_H = 5.0, 3.02, 1.52, 0.86
NAC_Y0, NAC_Y1 = -6.6, 7.4
for side, sn in ((1, "L"), (-1, "R")):
    xc = NAC_X * side

    def nprof(y, xc=xc):
        f = 1.0
        if y > 5.6:
            f = 1.0 - 0.05 * (y - 5.6) / 1.8
        return (NAC_Z, NAC_HW * f, NAC_H, NAC_H * f, 4.0, xc)
    nys = [NAC_Y0 + (NAC_Y1 - NAC_Y0) * k / 20 for k in range(21)]
    loft("Nacelle_" + sn, nys, nprof, ["M_Skin"], "Body", seg=40, cap0=False, cap1=True)
    # intake duct liner (dark), splitter and variable ramps
    loft("IntakeDuct_" + sn, [NAC_Y0 + 0.02, NAC_Y0 + 1.8], lambda y, xc=xc: (NAC_Z, NAC_HW - 0.06, NAC_H - 0.06, NAC_H - 0.06, 4.0, xc),
         ["M_DarkMetal"], "Body", seg=40, inner=True, cap0=False, cap1=False)
    abox("IntakeFace_" + sn, (NAC_HW * 2 - 0.1, 0.04, NAC_H * 2 - 0.1), (xc, NAC_Y0 + 1.8, NAC_Z), "M_Black", "Body", bevel=0.0, dirt=False)
    abox("IntakeSplitter_" + sn, (0.10, 2.2, NAC_H * 2 - 0.08), (xc, NAC_Y0 + 1.05, NAC_Z), "M_Skin", "Body", bevel=0.02)
    for sx in (1, -1):
        abox("IntakeRamp_%s_%d" % (sn, sx), (0.05, 1.6, NAC_H * 2 - 0.14), (xc + sx * 0.42, NAC_Y0 + 1.0, NAC_Z), "M_SkinPanel", "Body",
             rot=(0, 0, sx * 0.10), bevel=0.01)
    abox("IntakeLipTop_" + sn, (NAC_HW * 2 + 0.04, 0.18, 0.06), (xc, NAC_Y0 + 0.02, NAC_Z + NAC_H - 0.02), "M_SkinPanel", "Body", bevel=0.02)
    # auxiliary (blow-in) doors along the nacelle sides
    for k in range(4):
        for sx in (1, -1):
            abox("BlowIn_%s_%d_%d" % (sn, k, sx), (0.02, 0.45, 0.32), (xc + sx * (NAC_HW + 0.002), NAC_Y0 + 2.2 + k * 0.55, NAC_Z + 0.15),
                 "M_SkinPanel", "Body", bevel=0.005)
    # nozzles: outer petals, inner liner, turbine face; afterburner flame cones (node AB_Flame_n)
    for j, dx in enumerate((0.76, -0.76)):
        key = "%d" % (1 + (0 if side > 0 else 2) + (j if side > 0 else 1 - j))       # 1,2 left (outer->inner), 3,4 right
        cx = xc + dx
        def nm(i, k):
            return 1 if i >= 3 else 0
        lathe_y("Nozzle_" + key, [(-0.3, 0.70), (0.6, 0.72), (1.6, 0.70), (2.4, 0.60), (2.4, 0.55), (1.5, 0.60), (1.1, 0.0)],
                (cx, NAC_Y1, NAC_Z - 0.02), ["M_DarkMetal", "M_Burnt"], "Body", seg=24, mat_fn=nm)
        for k in range(12):  # petal seams
            a = 2 * PI * k / 12
            p0 = Vector((cx + math.cos(a) * 0.715, NAC_Y1 + 1.0, NAC_Z - 0.02 + math.sin(a) * 0.715))
            p1 = Vector((cx + math.cos(a) * 0.61, NAC_Y1 + 2.38, NAC_Z - 0.02 + math.sin(a) * 0.61))
            rod("Petal_%s_%d" % (key, k), p0, p1, 0.012, "M_Black", "Body", seg=4)
        lathe_y("ABFlame_" + key, [(0.0, 0.0), (0.05, 0.5), (1.5, 0.48), (4.0, 0.30), (6.5, 0.0)], (cx, NAC_Y1 + 2.0, NAC_Z - 0.02),
                ["M_Flame"], "AB_Flame_" + key, seg=16, dirt=False)
        aobj("ABDiamond_" + key, bm_sphere(0.28, (cx, NAC_Y1 + 3.2, NAC_Z - 0.02), 10, 6, (1, 1.8, 1)), "M_Flame", "AB_Flame_" + key, dirt=False)

# ---------------------------------------------------------------- main-gear pods (between fuselage and nacelles)
POD_X, POD_Z = 2.72, 3.30
POD_Y0, POD_Y1 = -3.6, 6.0
POD_HOLE = (-1.4, 3.7)
for side, sn in ((1, "L"), (-1, "R")):
    xc = POD_X * side

    def pprof(y, xc=xc):
        t0 = clamp01((y - POD_Y0) / 1.6)
        t1 = clamp01((POD_Y1 - y) / 2.6)
        f = math.sqrt(max(0.0, 1 - (1 - min(t0, t1)) ** 2))
        return (POD_Z + 0.25 * (1 - f), 0.70 * max(f, 0.02), 0.75, 0.72 * max(f, 0.02), 3.0, xc)
    pys = [POD_Y0 + (POD_Y1 - POD_Y0) * k / 24 for k in range(25)] + list(POD_HOLE)
    pys = sorted(set(round(y, 4) for y in pys))
    pod = loft("GearPod_" + sn, pys, pprof, ["M_Skin", "M_Hole"], "Body", seg=32,
               mat_fn=lambda y, a, z: 1 if (POD_HOLE[0] < y < POD_HOLE[1] and 236 < a < 304) else 0)
    delete_faces_mat(pod, 1)

# ---------------------------------------------------------------- fin (fixed lower part) + all-moving upper fin, stabiliser
FIN_LE0, FIN_Z0, FIN_C0 = 10.6, 5.30, 11.3
FIN_LE1, FIN_Z1, FIN_C1 = 19.6, 13.1, 4.0
FIN_SPLIT = 8.25


def fin_le(z):
    return FIN_LE0 + (FIN_LE1 - FIN_LE0) * (z - FIN_Z0) / (FIN_Z1 - FIN_Z0)


def fin_c(z):
    return FIN_C0 + (FIN_C1 - FIN_C0) * (z - FIN_Z0) / (FIN_Z1 - FIN_Z0)


surface("Fin", wing_secs(0.0, FIN_LE0, FIN_Z0, FIN_C0, 0.0, fin_le(FIN_SPLIT), FIN_SPLIT, fin_c(FIN_SPLIT), t_root=0.085, t_tip=0.075,
                         n=4, vertical=True), ["M_Skin"], "Body", nc=14, cap_tip=True)
surface("FinTop", wing_secs(0.0, fin_le(FIN_SPLIT + 0.03), FIN_SPLIT + 0.03, fin_c(FIN_SPLIT + 0.03), 0.0, FIN_LE1, FIN_Z1, FIN_C1,
                            t_root=0.075, t_tip=0.06, n=5, vertical=True), ["M_Skin"], "Rudder", nc=14, cap_root=True, cap_tip=True)
surface("FinFillet", [dict(le=(0, 4.0, 5.05), cd=(0, 1, 0), ud=(1, 0, 0), c=8.0, t=0.05), dict(le=(0, 9.2, 5.85), cd=(0, 1, 0), ud=(1, 0, 0), c=3.0, t=0.10)],
        ["M_Skin"], "Body", nc=12, cap_tip=True)
STAB_ROOT = (0.35, 16.4, 7.0, 6.4)
STAB_TIP = (6.62, 21.3, 7.0, 2.3)
for side, sn in ((1, "L"), (-1, "R")):
    st = wing_secs(STAB_ROOT[0], STAB_ROOT[1], STAB_ROOT[2], STAB_ROOT[3], STAB_TIP[0], STAB_TIP[1], STAB_TIP[2], STAB_TIP[3],
                   t_root=0.07, t_tip=0.055, n=4, twist_tip=0.0, cam=0.0, side=side)
    surface("Stab_" + sn, st, ["M_Skin"], "Stab_" + sn, nc=12, cap_root=True, cap_tip=True)
lathe_y("StabFairing", [(0.0, 0.0), (0.8, 0.30), (3.0, 0.38), (5.6, 0.30), (6.8, 0.0)], (0, 16.0, 7.0), ["M_Skin"], "Body", seg=14)
lathe_y("TailCone", [(0.0, 0.42), (0.5, 0.40), (1.4, 0.26), (1.6, 0.0)], (0, 23.55, 4.85), ["M_SkinPanel"], "Body", seg=16)
aobj("FinTipLight", bm_sphere(0.07, (0, FIN_LE1 + FIN_C1 - 0.2, FIN_Z1 + 0.03), 10, 6), "M_LampWhite", "Rudder", dirt=False)
aobj("BeaconTop", bm_sphere(0.12, (0, -6.0, 6.02), 12, 6, (1, 1, 0.7)), "M_LampRed", "Body", dirt=False)
aobj("BeaconBottom", bm_sphere(0.12, (0, 13.4, 2.66), 12, 6, (1, 1, 0.7)), "M_LampRed", "Body", dirt=False)

# ---------------------------------------------------------------- decals
fin_ob = bpy.data.objects["Fin"]
fintop = bpy.data.objects["FinTop"]
for side in (1, -1):
    decal("DecalFinStar_%d" % side, fin_ob, (0, 16.0, 6.9), (side, 0, 0), (0, 0, 1), 1.9, 1.9, DR["star"], "M_Decal", "Body", nx=4, ny=4)
    decal("DecalFinFlag_%d" % side, fintop, (0, 20.0, 10.6), (side, 0, 0), (0, 0, 1), 1.6, 1.07, DR["flag"], "M_Decal", "Rudder", nx=3, ny=2)
    decal("DecalNumber_%d" % side, fus, (1.4 * side, -22.3, 3.45), (side, 0, 0), (0, 0, 1), 1.1, 0.66, DR["number"], "M_Decal", "Body", nx=6, ny=3)
    decal("DecalName_%d" % side, fus, (1.5 * side, -19.9, 4.85), (side, 0, 0), (0, 0, 1), 3.4, 0.4, DR["name"], "M_Decal", "Body", nx=12, ny=2)
for side, sn in ((1, "L"), (-1, "R")):
    wob = bpy.data.objects["OuterWing_" + sn]
    decal("DecalWingStar_" + sn, wob, (21.5 * side, ow_le(21.5) + 1.4, ow_z(21.5) - 0.3), (0, 0, -1), (0, -1, 0), 2.0, 2.0, DR["star"],
          "M_Decal", "Wing_" + sn, nx=4, ny=4)
print("STEP1", stats())
