# Sukhoi Su-34 (T-10V, serial 2014+) strike aircraft for the r19 radio air strikes.
# Real dimensions: length 23.34 m, span 14.70 m, height 6.09 m, wing area 62 m2, 2 x AL-31F-M1 (13.5 tf AB).
# Recognition features modelled: flat wide "platypus" nose with sharp chines, side-by-side two-seat cockpit under a wide
# canopy, canards on the LERX, Su-27 wing (LE sweep 42 deg) with Khibiny EW wingtip pods, widely spaced nacelles with
# rectangular intakes, vertical twin fins on booms, all-moving stabilators, long tail sting (rear radar), 12 hardpoints.
# Kind frame (x left, y up, z fwd). The gear is not modelled (strike aircraft only fly); origin = ground line, airframe level.
# Store nodes: fab500_0..7 (FAB-500M-62) and fab1000_0..3 (FAB-1000), hidden per remaining count by StrikePlaneRenderer.
exec(open("/home/user/tools/rwveh/craft_common.py").read())
exec(open("/home/user/tools/rwveh/plane_lib.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
plane_mats("T_JetSu34Top", "T_JetSu34Top", "T_JetSu34Bot", "T_JetInt", frame=(0.24, 0.26, 0.28))
amat("M_Decal", "T_DecalsArs", 0.5, 0.0, alpha_clip=True, vcol=False)
amat("M_CanopyS", None, 0.03, 0.4, factor=(0.45, 0.52, 0.55, 1), glass_alpha=0.28)
amat("M_RAM", None, 0.7, 0.1, factor=(0.34, 0.37, 0.40, 1))
amat("M_Radome", None, 0.6, 0.0, factor=(0.55, 0.60, 0.62, 1))
amat("M_Nozzle", None, 0.38, 0.85, factor=(0.36, 0.33, 0.31, 1))
amat("M_NozIn", None, 0.6, 0.5, factor=(0.12, 0.11, 0.11, 1))
amat("M_Glow", None, 0.5, 0.0, factor=(1.0, 0.45, 0.12, 1), emissive=(1.0, 0.40, 0.10, 1))
amat("M_Flame", None, 0.5, 0.0, factor=(1.0, 0.62, 0.30, 1), emissive=(1.0, 0.55, 0.25, 1), glass_alpha=0.45)
amat("M_FlameCore", None, 0.5, 0.0, factor=(0.70, 0.80, 1.0, 1), emissive=(0.6, 0.7, 1.0, 1), glass_alpha=0.6)
amat("M_Helmet", None, 0.5, 0.0, factor=(0.80, 0.80, 0.76, 1))
amat("M_Visor", None, 0.1, 0.6, factor=(0.08, 0.08, 0.08, 1))
amat("M_Suit", None, 0.85, 0.0, factor=(0.24, 0.27, 0.22, 1))
amat("M_Pylon", None, 0.6, 0.1, factor=(0.52, 0.57, 0.62, 1))
P = "S34"
G = P + "_body"
GAL, GAR, GFL, GFR = P + "_al", P + "_ar", P + "_fl", P + "_fr"
GEL, GER, GRL, GRR, GCL, GCR = P + "_el", P + "_er", P + "_rl", P + "_rr", P + "_cl", P + "_cr"
NOZ = {s: (P + "_nz%s" % s, P + "_glow%s" % s, P + "_ab%s" % s, P + "_abx%s" % s) for s in ("L", "R")}
YC = 2.30                       # fuselage datum height
Z_NOSE, Z_TAIL = 12.00, -9.00
# t (0 = radome tip .. 1 = rear body z -9), yc, hw, ht, hb, squareness. The nose is wide and flat (chines), the cockpit
# section is the widest point of the forebody (side-by-side armoured tub, 2.2 m)
ctrl = [
    (0.000, YC + 0.00, 0.03, 0.03, 0.03, 2.0),
    (0.029, YC + 0.00, 0.34, 0.19, 0.22, 2.2),
    (0.076, YC + 0.02, 0.64, 0.32, 0.38, 2.5),
    (0.124, YC + 0.05, 0.86, 0.42, 0.50, 2.9),
    (0.171, YC + 0.08, 1.02, 0.52, 0.60, 3.1),
    (0.248, YC + 0.10, 1.12, 0.60, 0.66, 3.3),
    (0.324, YC + 0.08, 1.24, 0.56, 0.62, 3.6),
    (0.429, YC + 0.04, 1.28, 0.48, 0.56, 3.8),
    (0.571, YC + 0.00, 1.16, 0.44, 0.50, 3.6),
    (0.714, YC - 0.02, 1.00, 0.40, 0.45, 3.4),
    (0.857, YC - 0.02, 0.80, 0.35, 0.38, 3.0),
    (1.000, YC - 0.02, 0.50, 0.28, 0.30, 2.5)]
prof = ctrl_at(ctrl, Z_NOSE, Z_TAIL)
CZ0, CZ1, CZ2, CZ3 = 8.75, 7.75, 5.55, 4.35           # windscreen foot, windscreen bow, canopy max, canopy end

def fmat(y, ang, z):
    zk = -y
    if zk > 10.3:
        return 1                                     # radome
    if CZ3 + 0.1 < zk < CZ0 - 0.1 and 40 < ang < 140:
        return 2                                     # cockpit floor under the glass
    return 0
fusel = fus(P + "_fus", Z_NOSE, Z_TAIL, ctrl, ["M_Top", "M_Radome", "M_Int"], G, mat_fn=fmat, n=70, seg=44)
paint(fusel, zt=0.45)
# ------------------------------------------------------------------------------------------------ cockpit (side-by-side)
def sill(z):
    yc, hw, ht, hb, n = prof(z)[:5]
    return yc + ht * 0.86
SILL = sill(6.4)
def cprof(y):
    z = -y
    if z > CZ1:
        u = (CZ0 - z) / (CZ0 - CZ1); ht, hw = 0.04 + 0.62 * math.sin(u * PI / 2), 0.62 + 0.30 * u
    elif z > CZ2:
        u = (CZ1 - z) / (CZ1 - CZ2); ht, hw = 0.66 + 0.06 * math.sin(u * PI), 0.92 - 0.02 * u
    else:
        u = (CZ2 - z) / (CZ2 - CZ3); ht, hw = 0.68 * (1 - u) ** 1.5 + 0.03, 0.90 - 0.30 * u
    return (SILL, hw, ht, 0.05, 2.6)
cys = [-CZ0 + (CZ0 - CZ3) * k / 26 for k in range(27)]
loft(P + "_glass", cys, cprof, ["M_CanopyS"], G, seg=18, angle_range=(0, 180), smooth=True, dirt=False)
cp = lambda z: cprof(-z)
frame_ring(P + "_cfr0", cp, CZ1, 0, 180, 0.022, G, off=0.006)          # windscreen bow
frame_ring(P + "_cfr1", cp, 6.55, 0, 180, 0.02, G, off=0.006)          # canopy split (two upward-hinged halves)
frame_ring(P + "_cfr2", cp, CZ3 + 0.04, 0, 180, 0.024, G, off=0.006)
frame_line(P + "_cfc", cp, CZ1, CZ3, 90, 0.022, G, off=0.006)          # centre canopy beam
for i, a in enumerate((4, 176)):
    frame_line("%s_cfl%d" % (P, i), cp, CZ0, CZ3, a, 0.024, G, off=0.004)
# crew: two K-36DM seats side by side (pilot left, navigator right), helmets visible through the canopy
for k, sx in enumerate((0.48, -0.48)):
    ez = 6.25
    abox("%s_seat%d" % (P, k), (0.52, 0.30, 0.95), tuple(KV((sx, SILL + 0.10, ez - 0.25))), "M_Int", G, bevel=0.02)
    abox("%s_head%d" % (P, k), (0.40, 0.22, 0.32), tuple(KV((sx, SILL + 0.58, ez - 0.38))), "M_Int", G, bevel=0.03)
    abox("%s_body%d" % (P, k), (0.44, 0.28, 0.55), tuple(KV((sx, SILL + 0.10, ez + 0.02))), "M_Suit", G, bevel=0.04)
    s = bm_sphere(0.15, KV((sx, SILL + 0.52, ez - 0.10)), 14, 8)
    aobj("%s_helm%d" % (P, k), s, "M_Helmet", G, smooth=True, dirt=False)
    abox("%s_visor%d" % (P, k), (0.20, 0.02, 0.09), tuple(KV((sx, SILL + 0.50, ez + 0.05))), "M_Visor", G, bevel=0.01, dirt=False)
abox(P + "_panel", (1.70, 0.30, 0.30), tuple(KV((0, SILL + 0.10, 7.20))), "M_Dash", G, rot=(math.radians(-15), 0, 0), bevel=0.02)
abox(P + "_glare", (1.74, 0.06, 0.40), tuple(KV((0, SILL + 0.27, 7.35))), "M_Black", G, bevel=0.02)
for k, sx in enumerate((0.48, -0.48)):
    abox("%s_hud%d" % (P, k), (0.20, 0.004, 0.16), tuple(KV((sx, SILL + 0.42, 7.30))), "M_Canopy", G, rot=(math.radians(-25), 0, 0), bevel=0.0, dirt=False)
# retractable refuelling probe fairing (left of the windscreen) + nose air-data boom
lathe_y(P + "_probef", [(0.0, 0.0), (0.0, 0.06), (0.4, 0.10), (2.6, 0.10), (2.9, 0.0)], tuple(KV((0.92, SILL - 0.05, 9.4))), ["M_Top"], G, seg=10, axis='Y')
rod(P + "_pitot", KV((0, YC, 11.95)), KV((0, YC, 12.95)), 0.022, "M_Steel", G, seg=8, r2=0.012)
for sx in (1, -1):
    abox("%s_aoa%d" % (P, sx), (0.012, 0.10, 0.05), tuple(KV((sx * 0.66, YC + 0.18, 9.9))), "M_Steel", G, bevel=0.0)
    abox("%s_chine%d" % (P, sx), (0.06, 3.2, 0.03), tuple(KV((sx * 0.84, YC - 0.02, 9.6))), "M_Top", G, rot=(0, 0, 0), bevel=0.01)
# ------------------------------------------------------------------------------------------------ nacelles + intakes
NX = 1.48
nac = [(4.40, 1.72, 0.50, 0.56, 0.56, 4.2), (3.00, 1.72, 0.58, 0.62, 0.62, 3.8), (-2.0, 1.78, 0.62, 0.60, 0.62, 3.0),
       (-6.5, 1.84, 0.60, 0.58, 0.58, 2.6), (-8.4, 1.90, 0.56, 0.56, 0.56, 2.2), (-9.25, 1.90, 0.53, 0.53, 0.53, 2.0)]
def nprof_fn(sx):
    def f(y):
        z = -y
        for i in range(len(nac) - 1):
            if nac[i][0] >= z >= nac[i + 1][0]:
                u = (nac[i][0] - z) / (nac[i][0] - nac[i + 1][0])
                u = u * u * (3 - 2 * u)
                a, b = nac[i], nac[i + 1]
                v = [a[k] + (b[k] - a[k]) * u for k in range(1, 6)]
                return (v[0], v[1], v[2], v[3], v[4], sx * NX)
        r = nac[0] if z > nac[0][0] else nac[-1]
        return (r[1], r[2], r[3], r[4], r[5], sx * NX)
    return f
nys = [-(4.40 - (4.40 + 9.25) * k / 44) for k in range(45)]
for sx in (1, -1):
    ob = loft("%s_nac%d" % (P, sx), nys, nprof_fn(sx), ["M_Bot"], G, seg=28, cap0=False, cap1=True)
    paint(ob, zt=0.45)
    # rectangular intake (sloped upper lip, variable ramps, auxiliary louvres)
    abox("%s_duct%d" % (P, sx), (0.90, 0.04, 1.02), tuple(KV((sx * NX, 1.72, 4.10))), "M_Black", G, bevel=0.0, dirt=False)
    for k, (dx, dy, w, h) in enumerate(((0, 0.55, 1.02, 0.06), (0, -0.56, 1.02, 0.06), (0.50, 0, 0.06, 1.16), (-0.50, 0, 0.06, 1.16))):
        abox("%s_lip%d_%d" % (P, sx, k), (w, 0.12, h), tuple(KV((sx * NX + dx, 1.72 + dy, 4.42 - (0.25 if k == 1 else 0)))), "M_Bot", G, bevel=0.012)
    abox("%s_ramp%d" % (P, sx), (0.80, 1.2, 0.04), tuple(KV((sx * NX, 2.10, 3.60))), "M_Int", G, rot=(math.radians(-12), 0, 0), bevel=0.0)
    for j in range(5):
        abox("%s_louv%d_%d" % (P, sx, j), (0.05, 0.14, 0.02), tuple(KV((sx * NX + (j - 2) * 0.12, 1.14, 3.2))), "M_Black", G, bevel=0.0, dirt=False)
    # nozzle with petals, glow disc, afterburner cones (shown on the climb-out)
    gN, gG, gA, gX = NOZ["L" if sx > 0 else "R"]
    c = KV((sx * NX, 1.90, -9.25))
    lathe_y("%s_noz%d" % (P, sx), [(0.0, 0.53), (0.30, 0.52), (0.92, 0.46), (0.94, 0.43), (0.40, 0.41), (0.10, 0.39)], tuple(c),
            ["M_Nozzle"], gN, seg=24, axis='Y', smooth=True)
    for k in range(16):
        a = 2 * PI * k / 16
        p0 = c + Vector((math.cos(a) * 0.535, 0.05, math.sin(a) * 0.535))
        p1 = c + Vector((math.cos(a) * 0.465, 0.90, math.sin(a) * 0.465))
        rod("%s_pet%d_%d" % (P, sx, k), p0, p1, 0.012, "M_NozIn", gN, seg=4, dirt=False)
    acyl("%s_nzr%d" % (P, sx), 0.39, 0.04, tuple(c + Vector((0, 0.12, 0))), "M_NozIn", gN, axis=1, seg=20, dirt=False)
    acyl("%s_glw%d" % (P, sx), 0.35, 0.02, tuple(c + Vector((0, 0.40, 0))), "M_Glow", gG, axis=1, seg=20, dirt=False)
    lathe_y("%s_ab%d" % (P, sx), [(0.0, 0.0), (0.0, 0.36), (0.7, 0.30), (1.8, 0.12), (2.1, 0.0)], tuple(c + Vector((0, 0.92, 0))),
            ["M_Flame"], gA, seg=16, axis='Y', dirt=False)
    lathe_y("%s_abc%d" % (P, sx), [(0.0, 0.0), (0.0, 0.22), (0.5, 0.18), (1.1, 0.0)], tuple(c + Vector((0, 0.92, 0))),
            ["M_FlameCore"], gA, seg=12, axis='Y', dirt=False)
    lathe_y("%s_abx%d" % (P, sx), [(0.0, 0.0), (0.0, 0.40), (1.0, 0.36), (2.4, 0.22), (3.4, 0.08), (3.9, 0.0)], tuple(c + Vector((0, 0.92, 0))),
            ["M_Flame"], gX, seg=16, axis='Y', dirt=False)
    # tail boom outboard of the nacelle (carries fin + stabilator), ventral fin underneath
    bys = [-(-3.4 - 6.3 * k / 14) for k in range(15)]
    def bprof(y, sx=sx):
        z = -y
        u = (-3.4 - z) / 6.3
        return (2.05, 0.26 * (1 - 0.45 * u * u), 0.18 * (1 - 0.3 * u), 0.22 * (1 - 0.4 * u), 2.4, sx * 2.25)
    ob = loft("%s_boom%d" % (P, sx), bys, bprof, ["M_Top"], G, seg=16, cap0=True, cap1=True)
    paint(ob, zt=0.45)
    abox("%s_vent%d" % (P, sx), (0.05, 2.0, 0.80), tuple(KV((sx * 2.30, 1.55, -6.9))), "M_Bot", G, rot=(math.radians(-14), 0, math.radians(sx * 12)), bevel=0.01)
# central tunnel fairing between the nacelles (FAB stations), tail sting with the rear-hemisphere radar
abox(P + "_tunnel", (1.10, 9.5, 0.10), tuple(KV((0, 1.80, -1.6))), "M_Bot", G, bevel=0.04)
lathe_y(P + "_sting", [(0.0, 0.0), (0.0, 0.50), (0.6, 0.48), (1.6, 0.40), (2.1, 0.30), (2.3, 0.18), (2.42, 0.0)], tuple(KV((0, YC - 0.05, -9.0))),
        ["M_Top"], G, seg=18, axis='Y')
lathe_y(P + "_stingr", [(0.0, 0.19), (0.12, 0.17), (0.30, 0.0)], tuple(KV((0, YC - 0.05, -11.15))), ["M_Radome"], G, seg=14, axis='Y')
for sx in (1, -1):
    abox("%s_flr%d" % (P, sx), (0.16, 0.36, 0.05), tuple(KV((sx * 0.40, YC - 0.40, -9.6))), "M_Black", G, bevel=0.005)
# ------------------------------------------------------------------------------------------------ LERX, canards, wings
for sx in (1, -1):
    wing("%s_lerx" % P, sx, (sx * 1.05, YC - 0.02, 6.2), 1.30, 6.2, 2.4, 3.9, 0.0, 0.03, 0.025, 1.0, G, nsec=3)
for sx, gc in ((1, GCL), (-1, GCR)):
    wing("%s_can" % P, sx, (sx * 1.30, YC + 0.06, 6.55), 1.55, 1.95, 0.55, 1.55, 0.0, 0.04, 0.035, 1.0, gc, nsec=4)
WR = (2.30, YC - 0.06, 1.85)
for sx, ga, gf in ((1, GAL, GFL), (-1, GAR, GFR)):
    sec, hg = wing("%s_wing" % P, sx, (sx * WR[0], WR[1], WR[2]), 5.05, 5.20, 1.40, 4.55, -2.5, 0.05, 0.035, 1.0, G,
                   parts=[(0.03, 0.55, gf), (0.55, 0.95, ga)], hinge=0.78, nsec=10)
    globals()["HG%d" % sx] = hg
    tip = Vector(sec(1.0)["le"])
    # Khibiny L175V EW pod on the wingtip
    lathe_y("%s_ewpod%d" % (P, sx), [(0.0, 0.0), (0.0, 0.05), (0.5, 0.17), (2.6, 0.17), (3.1, 0.10), (3.2, 0.0)],
            tuple(tip + Vector((sx * 0.05, -0.55, 0))), ["M_Top"], G, seg=14, axis='Y')
    lamp("%s_nav%d" % (P, sx), tuple(tip + Vector((sx * 0.22, -0.4, 0))), "M_NavRed" if sx < 0 else "M_NavGreen", G, r=0.04)
    rod("%s_lef%d" % (P, sx), Vector(sec(0.03)["le"]) + Vector((0, 0.35, 0.0)), Vector(sec(0.97)["le"]) + Vector((0, 0.35, 0.0)), 0.012, "M_Top", G, seg=6, dirt=False)
# ------------------------------------------------------------------------------------------------ tail: fins + stabilators
for sx, ge in ((1, GEL), (-1, GER)):
    wing("%s_hs" % P, sx, (sx * 2.42, 2.02, -6.95), 2.55, 2.90, 0.95, 2.60, -3.0, 0.045, 0.035, 1.0, ge, nsec=5)
for sx, gr in ((1, GRL), (-1, GRR)):
    tmp = "%s_tmpfin%d" % (P, sx)
    wing("%s_fin%d" % (P, sx), 1, (0, 0, 0), 3.50, 3.90, 1.30, 3.00, 0, 0.045, 0.035, 1.0, tmp, ud_axis='X', nsec=6)
    M = Matrix.Translation(KV((sx * 2.25, 2.20, -4.55)))
    for o in list(col_get("BUILD").objects):
        if o.get("grp") == tmp:
            o.matrix_world = M @ o.matrix_world
            o["grp"] = gr
            paint(o, zt=0.3)
    tp = M @ KV((0, 3.50, -3.0 - 0.6))
    acyl("%s_fintip%d" % (P, sx), 0.06, 1.2, tuple(tp), "M_RAM", gr, axis=1, seg=8)
    lamp("%s_strobe%d" % (P, sx), tuple(M @ KV((0, 3.45, -3.0 - 1.3))), "M_Strobe", gr, r=0.03)
lamp(P + "_beacon", tuple(KV((0, YC + 0.45, 0.5))), "M_NavRed", G, r=0.05)
abox(P + "_antd", (0.04, 0.35, 0.18), tuple(KV((0, YC + 0.50, 2.0))), "M_RAM", G, bevel=0.02)
# ------------------------------------------------------------------------------------------------ pylons + stores
PYL = [(1, NX, 1.15, 1.3), (-1, -NX, 1.15, 1.3), (0, 0.0, 1.75, 0.9), (0, 0.0, 1.75, -2.6),
       (1, 3.55, 2.06, -0.9), (-1, -3.55, 2.06, -0.9), (1, 4.80, 2.00, -1.8), (-1, -4.80, 2.00, -1.8)]
for i, (sd, px, py, pz) in enumerate(PYL):
    abox("%s_pyl%d" % (P, i), (0.08, 1.8, 0.22), tuple(KV((px, py - 0.10, pz + 0.1))), "M_Pylon", G, bevel=0.02)
    abox("%s_rack%d" % (P, i), (0.14, 1.2, 0.08), tuple(KV((px, py - 0.24, pz + 0.1))), "M_Steel", G, bevel=0.01)
STORES = []
for i, (sd, px, py, pz) in enumerate(PYL):           # FAB-500M-62: 2.48 m x 0.40 m
    bomb_geo("%s_f5_%d" % (P, i), (px, py - 0.47, pz), 2.48, 0.40, "%s_f5g%d" % (P, i), mat="M_BombSU", ring=False)
    STORES.append(("fab500_%d" % i, "%s_f5g%d" % (P, i)))
for i, k in enumerate((0, 1, 4, 5)):                  # FAB-1000: 2.80 m x 0.63 m on the intake and inner wing stations
    sd, px, py, pz = PYL[k]
    bomb_geo("%s_f10_%d" % (P, i), (px, py - 0.57, pz), 2.80, 0.63, "%s_f10g%d" % (P, i), mat="M_BombSU", ring=False)
    STORES.append(("fab1000_%d" % i, "%s_f10g%d" % (P, i)))
# ------------------------------------------------------------------------------------------------ markings
for o in list(col_get("BUILD").objects):
    if o.name.startswith((P + "_wing", P + "_hs", P + "_can", P + "_lerx")):
        paint(o, zt=0.3)
fins = {sx: [o for o in col_get("BUILD").objects if o.get("grp") == (GRL if sx > 0 else GRR) and o.type == 'MESH' and "_fin" in o.name] for sx in (1, -1)}
for sx in (1, -1):
    M = Matrix.Translation(KV((sx * 2.25, 2.20, -4.55)))
    for side, nn in (("o", Vector((sx, 0, 0))), ("i", Vector((-sx, 0, 0)))):
        cpt = M @ KV((0, 1.6, -2.0)) + nn * 0.06
        decal_on("%s_fstar%d%s" % (P, sx, side), max(fins[sx], key=lambda o: len(o.data.polygons)), cpt, nn, Vector((0, 0, 1)), 0.95, "star_ru", GRL if sx > 0 else GRR)
w_obs = [o for o in col_get("BUILD").objects if o.name.startswith(P + "_wing")]
wingL = [o for o in w_obs if o.name.startswith(P + "_wing_w1_") and not o.name.endswith("m")]
wingR = [o for o in w_obs if o.name.startswith(P + "_wing_w-1_") and not o.name.endswith("m")]
for sx, obs in ((1, wingL), (-1, wingR)):
    big = max(obs, key=lambda o: len(o.data.polygons))
    decal_on("%s_wst%d" % (P, sx), big, KV((sx * 4.4, 2.5, -1.3)), Vector((0, 0, 1)), Vector((0, -1, 0)), 1.1, "star_ru", G)
    decal_on("%s_wsb%d" % (P, sx), big, KV((sx * 4.4, 1.8, -1.3)), Vector((0, 0, -1)), Vector((0, -1, 0)), 1.1, "star_ru", G)
for sx in (1, -1):
    decal_on("%s_flag%d" % (P, sx), fusel, KV((sx * 1.15, YC + 0.20, 3.2)), Vector((sx, 0, 0.3)), Vector((0, 0, 1)), 0.5, "flag_ru", G)
    decal_on("%s_vks%d" % (P, sx), fusel, KV((sx * 0.95, YC + 0.10, 9.0)), Vector((sx, 0, 0.2)), Vector((0, 0, 1)), 1.0, "vks", G)

# ------------------------------------------------------------------------------------------------ rig + export
auto_attach([G], bracket_groups=())
weather(gen_weather([KV((s * NX, 1.90, -9.2)) for s in (1, -1)], 1.0))
rt = root("root")
group_under(G, "su34_body", rt)
def hinge_node(nm, grp, pts):
    a, b = pts
    pv = pivot(nm, tuple((a + b) / 2), rt)
    group_under(grp, nm + "_geo", pv)
hinge_node("ail_L", GAL, HG1[GAL]); hinge_node("ail_R", GAR, globals()["HG-1"][GAR])
hinge_node("flap_L", GFL, HG1[GFL]); hinge_node("flap_R", GFR, globals()["HG-1"][GFR])
for nm, grp, p in (("elev_L", GEL, (2.70, 2.02, -8.20)), ("elev_R", GER, (-2.70, 2.02, -8.20)),
                   ("rud_L", GRL, (2.25, 2.20, -6.00)), ("rud_R", GRR, (-2.25, 2.20, -6.00)),
                   ("canard_L", GCL, (1.50, 2.36, 5.60)), ("canard_R", GCR, (-1.50, 2.36, 5.60))):
    pv = pivot(nm, tuple(KV(p)), rt)
    group_under(grp, nm + "_geo", pv)
for sd, sx in (("L", 1), ("R", -1)):
    gN, gG, gA, gX = NOZ[sd]
    pp = pivot("noz_" + sd, tuple(KV((sx * NX, 1.90, -9.25))), rt)
    group_under(gN, "noz_geo_" + sd, pp)
    for nm, g in (("glow_", gG), ("ab_", gA), ("abx_", gX)):
        e = pivot(nm + sd, tuple(KV((sx * NX, 1.90, -9.25))), pp)
        group_under(g, nm + sd + "_geo", e)
for nm, g in STORES:
    e = pivot(nm, (0, 0, 0), rt)
    group_under(g, nm + "_geo", e)
rw_export("/home/user/.cache/rwveh/raw/su34.glb", "/home/user/models/su34.blend", "SU34")
