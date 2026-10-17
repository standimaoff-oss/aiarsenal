# Sukhoi Su-57 (T-50, serial standard 2020+): integrated lifting body over widely spaced AL-41F1 nacelles,
# LEVCONs, all-moving canted fins and stabilators, tandem internal main bays, 3D thrust vectoring nozzles.
# Real dimensions: length 20.1 m, span 14.1 m, height 4.6 m, wing area 78.8 m2.
# Kind frame (x left, y up, z fwd). Origin = ground under the main axles' line, airframe LEVEL.
exec(open("/home/user/tools/rwveh/craft_common.py").read())
exec(open("/home/user/tools/rwveh/plane_lib.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
plane_mats("T_JetSu57Top", "T_JetSu57Top", "T_JetSu57Bot", "T_JetInt", frame=(0.22, 0.24, 0.27))
amat("M_Decal", "T_DecalsArs", 0.5, 0.0, alpha_clip=True, vcol=False)
amat("M_CanopyGold", None, 0.03, 0.6, factor=(0.62, 0.52, 0.30, 1), glass_alpha=0.20)
amat("M_RAM", None, 0.7, 0.1, factor=(0.30, 0.33, 0.37, 1))
amat("M_Nozzle", None, 0.38, 0.85, factor=(0.36, 0.33, 0.31, 1))
amat("M_NozIn", None, 0.6, 0.5, factor=(0.12, 0.11, 0.11, 1))
amat("M_Glow", None, 0.5, 0.0, factor=(1.0, 0.45, 0.12, 1), emissive=(1.0, 0.40, 0.10, 1))
amat("M_Flame", None, 0.5, 0.0, factor=(1.0, 0.62, 0.30, 1), emissive=(1.0, 0.55, 0.25, 1), glass_alpha=0.45)
amat("M_FlameCore", None, 0.5, 0.0, factor=(0.70, 0.80, 1.0, 1), emissive=(0.6, 0.7, 1.0, 1), glass_alpha=0.6)
amat("M_Mfd1", "T_MfdRadar", 0.3, 0.0, emissive=(0.35, 0.35, 0.35, 1), vcol=False)
amat("M_Mfd2", "T_MfdMap", 0.3, 0.0, emissive=(0.35, 0.35, 0.35, 1), vcol=False)
amat("M_Mfd3", "T_MfdSys", 0.3, 0.0, emissive=(0.35, 0.35, 0.35, 1), vcol=False)
amat("M_HudSym", "T_Hud", 0.3, 0.0, emissive=(0.3, 0.9, 0.5, 1), alpha_clip=True, vcol=False)
amat("M_Button", None, 0.5, 0.0, factor=(0.75, 0.75, 0.68, 1), emissive=(0.25, 0.22, 0.12, 1))
amat("M_Pack", None, 0.85, 0.0, factor=(0.30, 0.33, 0.24, 1))
amat("M_Kh38", None, 0.55, 0.15, factor=(0.80, 0.82, 0.80, 1))
amat("M_Pod", None, 0.6, 0.2, factor=(0.40, 0.44, 0.38, 1))
P = "SU"
G = P + "_body"
GL, GR, GN, GDC = P + "_gl", P + "_gr", P + "_gn", P + "_gdc"
GAL, GAR, GFL, GFR = P + "_al", P + "_ar", P + "_fl", P + "_fr"
GEL, GER, GRL, GRR, GLL, GLR = P + "_el", P + "_er", P + "_rl", P + "_rr", P + "_ll", P + "_lr"
GBL, GBR = P + "_bl", P + "_br"
NOZ = {s: (P + "_nz%s" % s, P + "_glow%s" % s, P + "_ab%s" % s, P + "_abx%s" % s) for s in ("L", "R")}
Z_NOSE, Z_TAIL = 10.30, -8.60
ctrl = [  # t (0 = radome tip z 10.3 .. 1 = beaver tail z -8.6), yc, hw, ht, hb, n
    (0.000, 2.05, 0.03, 0.03, 0.03, 2.0),
    (0.037, 2.05, 0.30, 0.28, 0.30, 2.0),
    (0.095, 2.07, 0.50, 0.42, 0.58, 2.1),
    (0.164, 2.10, 0.62, 0.50, 0.72, 2.3),
    (0.228, 2.12, 0.70, 0.48, 0.70, 2.6),
    (0.307, 2.08, 0.95, 0.45, 0.66, 3.0),
    (0.386, 2.02, 1.55, 0.40, 0.60, 3.4),
    (0.492, 1.98, 2.00, 0.36, 0.58, 3.8),
    (0.651, 1.96, 2.05, 0.34, 0.56, 3.8),
    (0.810, 1.95, 1.95, 0.30, 0.52, 3.6),
    (0.915, 1.92, 1.40, 0.26, 0.40, 3.2),
    (1.000, 1.90, 0.45, 0.10, 0.12, 2.5)]
prof = ctrl_at(ctrl, Z_NOSE, Z_TAIL)

def fmat(y, ang, z):
    return 1 if -y > 10.0 else 0          # dark radome tip
fusel = fus(P + "_fus", Z_NOSE, Z_TAIL, ctrl, ["M_Top", "M_RAM"], G, mat_fn=fmat, n=64, seg=40)
paint(fusel, zt=0.45)
# ------------------------------------------------------------------------------------------------ cockpit
CZ0, CZ1, CZ2, CZ3 = 7.55, 6.55, 4.35, 3.55
EYE = (0.0, 2.95, 5.30)
SILL = 2.50
inner_tub(P + "_tub", ctrl, Z_NOSE, Z_TAIL, 7.45, 3.95, 50, G)
cut_top(fusel, prof, 7.40, 4.05, 50)
def cprof(y):
    z = -y
    if z > CZ1:
        u = (CZ0 - z) / (CZ0 - CZ1); ht, hw = 0.03 + 0.60 * math.sin(u * PI / 2), 0.40 + 0.18 * u
    elif z > CZ2:
        u = (CZ1 - z) / (CZ1 - CZ2); ht, hw = 0.63 + 0.08 * math.sin(u * PI), 0.58 - 0.06 * u
    else:
        u = (CZ2 - z) / (CZ2 - CZ3); ht, hw = 0.63 * (1 - u) ** 1.4 + 0.03, 0.52 - 0.22 * u
    return (SILL, hw, ht, 0.05, 2.2)
cys = [-CZ0 + (CZ0 - CZ3) * k / 24 for k in range(25)]
loft(P + "_glass", cys, cprof, ["M_CanopyGold"], G, seg=16, angle_range=(0, 180), smooth=True, dirt=False)
cp = lambda z: (lambda r: (r[0], r[1], r[2], r[3], r[4]))(cprof(-z))
frame_ring(P + "_cfr0", cp, CZ1, 0, 180, 0.018, G, off=0.006)     # windscreen bow
frame_ring(P + "_cfr1", cp, CZ3 + 0.03, 0, 180, 0.022, G, off=0.006)
for i, a in enumerate((3, 177)):
    frame_line("%s_cfl%d" % (P, i), cp, CZ0, CZ3, a, 0.022, G, off=0.004)
for sx in (1, -1):                       # rear-view mirrors on the bow
    abox("%s_mirror%d" % (P, sx), (0.10, 0.012, 0.05), tuple(KV((sx * 0.30, 3.08, CZ1 - 0.04))), "M_Chrome", G, bevel=0.003, dirt=False)
abox(P + "_seatrail", (0.08, 0.25, 0.10), tuple(KV((0, 3.05, CZ3 + 0.25))), "M_Int", G, bevel=0.01)   # canopy actuator fairing


def jet_cockpit(eye, floor_y, coam, panel_z):
    ex, ey, ez = eye
    sy = ey - 0.80                     # cushion top
    sz = ez - 0.18                     # seat reference
    # ---- K-36D-5 ejection seat
    abox(P + "_kbuck", (0.50, 0.46, 0.20), tuple(KV((ex, sy - 0.12, sz + 0.02))), "M_Int", G, bevel=0.02)
    abox(P + "_kcush", (0.44, 0.42, 0.06), tuple(KV((ex, sy - 0.01, sz + 0.04))), "M_Leather", G, bevel=0.02)
    abox(P + "_kback", (0.50, 0.10, 0.92), tuple(KV((ex, sy + 0.45, sz - 0.28))), "M_Int", G, rot=(math.radians(-10), 0, 0), bevel=0.02)
    abox(P + "_kbackc", (0.40, 0.05, 0.70), tuple(KV((ex, sy + 0.38, sz - 0.22))), "M_Pack", G, rot=(math.radians(-10), 0, 0), bevel=0.02)
    abox(P + "_khead", (0.40, 0.24, 0.30), tuple(KV((ex, ey + 0.13, sz - 0.42))), "M_Int", G, bevel=0.03)
    abox(P + "_kheadp", (0.26, 0.04, 0.18), tuple(KV((ex, ey + 0.10, sz - 0.30))), "M_Leather", G, bevel=0.02)
    for s in (1, -1):
        rod("%s_krail%d" % (P, s), KV((ex + s * 0.27, sy - 0.15, sz - 0.30)), KV((ex + s * 0.27, ey + 0.25, sz - 0.48)), 0.025, "M_Steel", G, seg=8)
        abox("%s_karm%d" % (P, s), (0.05, 0.36, 0.05), tuple(KV((ex + s * 0.26, sy + 0.18, sz + 0.02))), "M_Int", G, bevel=0.01)
        abox("%s_kbelt%d" % (P, s), (0.06, 0.012, 0.6), tuple(KV((ex + s * 0.11, sy + 0.35, sz - 0.19))), "M_Canvas", G, rot=(math.radians(-10), 0, 0), bevel=0.0, dirt=False)
        abox("%s_kwing%d" % (P, s), (0.04, 0.12, 0.32), tuple(KV((ex + s * 0.25, ey + 0.05, sz - 0.40))), "M_Int", G, bevel=0.01)
    # yellow-black ejection handles (between the knees)
    for s in (1, -1):
        rod("%s_kh%d" % (P, s), KV((ex + s * 0.07, sy + 0.01, sz + 0.24)), KV((ex + s * 0.07, sy + 0.12, sz + 0.27)), 0.012, "M_Yellow", G, seg=6)
    rod(P + "_khx", KV((ex + 0.07, sy + 0.12, sz + 0.27)), KV((ex - 0.07, sy + 0.12, sz + 0.27)), 0.013, "M_Yellow", G, seg=6)
    for k in range(3):
        acyl("%s_khb%d" % (P, k), 0.0135, 0.02, tuple(KV((ex - 0.04 + 0.04 * k, sy + 0.12, sz + 0.27))), "M_Black", G, axis=0, seg=6, dirt=False)
    abox(P + "_klap", (0.40, 0.05, 0.012), tuple(KV((ex, sy + 0.04, sz + 0.15))), "M_Canvas", G, bevel=0.0, dirt=False)
    abox(P + "_kbuck2", (0.08, 0.08, 0.02), tuple(KV((ex, sy + 0.06, sz + 0.16))), "M_Chrome", G, bevel=0.0, dirt=False)
    # ---- floor, pedals
    abox(P + "_floor", (0.80, 1.7, 0.03), tuple(KV((ex, floor_y, ez + 0.35))), "M_Int", G, bevel=0.0)
    for s in (1, -1):
        p = KV((ex + s * 0.17, floor_y + 0.16, panel_z - 0.10))
        abox("%s_ped%d" % (P, s), (0.10, 0.03, 0.24), tuple(p), "M_Steel", G, rot=(math.radians(-30), 0, 0), bevel=0.005)
        abox("%s_pedg%d" % (P, s), (0.11, 0.035, 0.03), tuple(p + Vector((0, 0.02, 0.10))), "M_Black", G, bevel=0.005)
    # ---- centre stick (RUS) with grip, trigger, hat switches
    sb = KV((ex, floor_y + 0.03, ez + 0.36))
    gy = sy + 0.40 - floor_y
    lathe_y(P + "_boot", [(0.0, 0.0), (0.0, 0.09), (0.05, 0.06), (0.10, 0.025), (0.10, 0.0)], tuple(sb), ["M_Leather"], G, seg=12, axis='Z')
    rod(P + "_stick", sb, sb + Vector((0, 0.02, gy)), 0.017, "M_Dark", G, seg=8)
    grip0 = sb + Vector((0, 0.02, gy - 0.02))
    rod(P + "_grip", grip0, grip0 + Vector((0, 0.03, 0.15)), 0.028, "M_Black", G, seg=10)
    abox(P + "_gtop", (0.06, 0.07, 0.04), tuple(grip0 + Vector((0, 0.01, 0.17))), "M_Black", G, bevel=0.012)
    abox(P + "_trig", (0.012, 0.03, 0.05), tuple(grip0 + Vector((0, -0.04, 0.08))), "M_Chrome", G, bevel=0.0, dirt=False)
    for k, (dx, dz) in enumerate(((0.0, 0.195), (0.022, 0.18), (-0.022, 0.18))):
        acyl("%s_hat%d" % (P, k), 0.007, 0.012, tuple(grip0 + Vector((dx, -0.01, dz))), "M_NavRed" if k == 0 else "M_Chrome", G, axis=2, seg=6, dirt=False)
    # ---- throttles (RUD, left console) twin levers + right console
    for i in range(2):
        b0 = KV((ex + 0.33, sy + 0.25, ez + 0.05 - 0.02 * i))
        rod("%s_thr%d" % (P, i), b0, b0 + Vector((0.0 if i else 0.01, -0.12, 0.16)), 0.012, "M_Steel", G, seg=6)
        abox("%s_thrk%d" % (P, i), (0.045, 0.07, 0.06), tuple(b0 + Vector((0.0, -0.13, 0.19))), "M_Black", G, bevel=0.012)
    for s in (1, -1):
        abox("%s_con%d" % (P, s), (0.16, 0.78, 0.12), tuple(KV((ex + s * 0.36, sy + 0.18, ez - 0.10))), "M_Int", G, bevel=0.01)
        for r in range(3):
            for c in range(4):
                abox("%s_sw%d_%d_%d" % (P, s, r, c), (0.016, 0.016, 0.02), tuple(KV((ex + s * (0.31 + 0.03 * c), sy + 0.25, ez - 0.32 + 0.12 * r))),
                     "M_Button" if (r + c) % 3 == 0 else "M_Chrome", G, bevel=0.0, dirt=False)
        abox("%s_cpnl%d" % (P, s), (0.12, 0.20, 0.008), tuple(KV((ex + s * 0.36, sy + 0.245, ez + 0.12))), "M_Dash", G, bevel=0.0)
    # ---- main instrument panel: 2 x 15" MFD + system display + UFCP + standby instruments
    top = coam - 0.02
    ph = 0.42
    tilt = math.radians(-14)
    pc = Vector(KV((ex, top - ph / 2, panel_z)))
    abox(P + "_panel", (0.82, 0.04, ph), tuple(pc), "M_Dash", G, rot=(tilt, 0, 0), bevel=0.012)
    abox(P + "_glare", (0.86, 0.08, 0.03), tuple(KV((ex, coam, panel_z + 0.01))), "M_Black", G, bevel=0.015)
    nrm = Vector((0, math.cos(tilt), math.sin(-tilt) * -1)).normalized()   # facing aft and slightly up
    nrm = Vector((0, 1, 0))
    nrm.rotate(Matrix.Rotation(tilt, 3, 'X'))
    upv = Vector((0, 0, 1))
    upv.rotate(Matrix.Rotation(tilt, 3, 'X'))
    for k, (dx, mat) in enumerate(((0.205, "M_Mfd1"), (-0.205, "M_Mfd2"))):
        c = pc + Vector((dx, 0, 0.04)) + nrm * 0.024
        abox("%s_mfdb%d" % (P, k), (0.37, 0.02, 0.30), tuple(c), "M_Black", G, rot=(tilt, 0, 0), bevel=0.01)
        flat_decal("%s_mfd%d" % (P, k), c, nrm, upv, 0.30, 0.235, (0, 0, 1, 1), mat, G, offset=0.012)
        for j in range(5):         # bezel push-buttons
            for side in (-1, 1):
                b = c + upv * (side * 0.135) + Vector((-0.12 + 0.06 * j, 0, 0)) + nrm * 0.012
                abox("%s_mb%d_%d_%d" % (P, k, j, side), (0.03, 0.012, 0.016), tuple(b), "M_Button", G, rot=(tilt, 0, 0), bevel=0.0, dirt=False)
    c3 = pc + Vector((0, 0, -0.15)) + nrm * 0.03
    abox(P + "_mfdb2", (0.20, 0.02, 0.12), tuple(c3), "M_Black", G, rot=(tilt, 0, 0), bevel=0.008)
    flat_decal(P + "_mfd2s", c3, nrm, upv, 0.16, 0.10, (0, 0, 1, 1), "M_Mfd3", G, offset=0.012)
    for k, dx in enumerate((-0.36, 0.36)):   # standby ADI / altimeter
        dial("%s_sb%d" % (P, k), (ex + dx, top - 0.16, panel_z - 0.03), 0.035, G)
    # up-front control panel + HUD (wide-angle ILS-31 style combiner)
    abox(P + "_ufcp", (0.22, 0.07, 0.08), tuple(KV((ex, coam - 0.05, panel_z - 0.05))), "M_Dash", G, rot=(math.radians(-30), 0, 0), bevel=0.01)
    for j in range(8):
        abox("%s_uk%d" % (P, j), (0.022, 0.012, 0.018), tuple(KV((ex - 0.085 + 0.024 * j, coam - 0.02, panel_z - 0.09))), "M_Button", G, bevel=0.0, dirt=False)
    abox(P + "_hudb", (0.16, 0.12, 0.05), tuple(KV((ex, coam + 0.03, panel_z + 0.0))), "M_Black", G, bevel=0.012)
    hz = panel_z - 0.10
    for k, (dy, a) in enumerate(((0.16, -32), (0.17, -22))):
        abox("%s_hudg%d" % (P, k), (0.20, 0.004, 0.17), tuple(KV((ex, coam + dy, hz - 0.03 * k))), "M_Canopy", G, rot=(math.radians(a), 0, 0), bevel=0.0, dirt=False)
    hn = Vector((0, 1, 0)); hn.rotate(Matrix.Rotation(math.radians(-32), 3, 'X'))
    hu = Vector((0, 0, 1)); hu.rotate(Matrix.Rotation(math.radians(-32), 3, 'X'))
    flat_decal(P + "_hudsym", KV((ex, coam + 0.16, hz)), hn, hu, 0.18, 0.15, (0, 0, 1, 1), "M_HudSym", G, offset=0.004)
    # side walls: canopy sill rails, map case, oxygen hose, lights
    for s in (1, -1):
        abox("%s_sill%d" % (P, s), (0.05, 2.6, 0.06), tuple(KV((s * 0.53, SILL - 0.01, ez + 0.2))), "M_Int", G, bevel=0.012)
    rod(P + "_oxyh", KV((ex - 0.26, sy + 0.10, sz)), KV((ex - 0.12, sy + 0.35, sz + 0.10)), 0.018, "M_Black", G, seg=8)
    acyl(P + "_oxyc", 0.03, 0.05, tuple(KV((ex - 0.30, sy + 0.25, sz - 0.05))), "M_Dash", G, axis=0, seg=10)


jet_cockpit(EYE, 1.86, 2.68, 6.20)
# IRST (OLS-50M) ball ahead of the windscreen, offset to starboard
lathe_y(P + "_irstb", [(0.0, 0.0), (0.0, 0.14), (0.05, 0.16), (0.24, 0.16), (0.30, 0.0)], tuple(KV((-0.22, 2.53, 7.95))), ["M_RAM"], G, seg=14, axis='Y')
s = bm_sphere(0.13, KV((-0.22, 2.62, 8.05)), 14, 8)
aobj(P + "_irst", s, "M_CanopyGold", G, smooth=True, dirt=False)
# ------------------------------------------------------------------------------------------------ nacelles + intakes
NX = 1.22
nac = [(3.60, 1.50, 0.55, 0.42, 0.55, 4.0), (2.60, 1.48, 0.62, 0.50, 0.62, 3.6), (-2.0, 1.45, 0.64, 0.55, 0.63, 3.0),
       (-6.5, 1.50, 0.60, 0.55, 0.58, 2.6), (-8.2, 1.55, 0.56, 0.56, 0.56, 2.1), (-8.95, 1.55, 0.54, 0.54, 0.54, 2.0)]
def nprof_fn(sx):
    zs = [r[0] for r in nac]
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
nys = [-(3.60 - (3.60 + 8.95) * k / 40) for k in range(41)]
for sx in (1, -1):
    ob = loft("%s_nac%d" % (P, sx), nys, nprof_fn(sx), ["M_Bot"], G, seg=28, cap0=False, cap1=True)
    paint(ob, zt=0.45)
    # intake: dark duct face + RAM-coated lips (slanted Caret-style)
    abox("%s_duct%d" % (P, sx), (1.00, 0.04, 0.84), tuple(KV((sx * NX, 1.47, 3.25))), "M_Black", G, bevel=0.0, dirt=False)
    for k, (dx, dy, w, h) in enumerate(((0, 0.43, 1.10, 0.05), (0, -0.56, 1.10, 0.05), (0.56, 0, 0.05, 1.0), (-0.56, 0, 0.05, 1.0))):
        abox("%s_lip%d_%d" % (P, sx, k), (w, 0.10, h), tuple(KV((sx * NX + dx, 1.48 + dy, 3.62))), "M_RAM", G, bevel=0.01)
    abox("%s_ramp%d" % (P, sx), (0.06, 0.6, 0.9), tuple(KV((sx * (NX - 0.50), 1.48, 3.35))), "M_RAM", G, bevel=0.01)   # splitter
    # nozzle: yaw node > pitch node > petals, glow, afterburner cones
    gN, gG, gA, gX = NOZ["L" if sx > 0 else "R"]
    c = KV((sx * NX, 1.55, -8.95))
    lathe_y("%s_noz%d" % (P, sx), [(0.0, 0.54), (0.30, 0.53), (0.86, 0.47), (0.88, 0.44), (0.40, 0.42), (0.10, 0.40)], tuple(c),
            ["M_Nozzle"], gN, seg=24, axis='Y', smooth=True)
    for k in range(16):
        a = 2 * PI * k / 16
        p0 = c + Vector((math.cos(a) * 0.545, 0.05, math.sin(a) * 0.545))
        p1 = c + Vector((math.cos(a) * 0.475, 0.85, math.sin(a) * 0.475))
        rod("%s_pet%d_%d" % (P, sx, k), p0, p1, 0.012, "M_NozIn", gN, seg=4, dirt=False)
    acyl("%s_nzr%d" % (P, sx), 0.40, 0.04, tuple(c + Vector((0, 0.12, 0))), "M_NozIn", gN, axis=1, seg=20, dirt=False)
    acyl("%s_glw%d" % (P, sx), 0.36, 0.02, tuple(c + Vector((0, 0.40, 0))), "M_Glow", gG, axis=1, seg=20, dirt=False)
    lathe_y("%s_ab%d" % (P, sx), [(0.0, 0.0), (0.0, 0.36), (0.6, 0.30), (1.6, 0.12), (1.9, 0.0)], tuple(c + Vector((0, 0.88, 0))),
            ["M_Flame"], gA, seg=16, axis='Y', dirt=False)
    lathe_y("%s_abc%d" % (P, sx), [(0.0, 0.0), (0.0, 0.22), (0.5, 0.18), (1.0, 0.0)], tuple(c + Vector((0, 0.88, 0))),
            ["M_FlameCore"], gA, seg=12, axis='Y', dirt=False)
    lathe_y("%s_abx%d" % (P, sx), [(0.0, 0.0), (0.0, 0.40), (1.0, 0.36), (2.4, 0.22), (3.4, 0.08), (3.9, 0.0)], tuple(c + Vector((0, 0.88, 0))),
            ["M_Flame"], gX, seg=16, axis='Y', dirt=False)
    for k in range(4):          # shock diamonds
        acyl("%s_dia%d_%d" % (P, sx, k), 0.20 - 0.03 * k, 0.12, tuple(c + Vector((0, 1.4 + 0.6 * k, 0))), "M_FlameCore", gX, axis=1, seg=12, dirt=False)
    # tail boom (carries the stabilator) + rear fairing
    bys = [-(-3.6 - 5.4 * k / 12) for k in range(13)]
    def bprof(y, sx=sx):
        z = -y
        u = (-3.6 - z) / 5.4
        return (1.80, 0.24 * (1 - 0.5 * u * u), 0.17 * (1 - 0.4 * u), 0.20 * (1 - 0.4 * u), 2.4, sx * 2.02)
    ob = loft("%s_boom%d" % (P, sx), bys, bprof, ["M_Top"], G, seg=16, cap0=True, cap1=True)
    paint(ob, zt=0.45)
# ------------------------------------------------------------------------------------------------ weapon bays
for sx, gb in ((1, GBL), (-1, GBR)):
    for k, (z0, z1) in enumerate(((1.8, -2.2), (-2.6, -6.0))):
        abox("%s_door%d_%d" % (P, sx, k), (0.60, z0 - z1, 0.03), tuple(KV((sx * 0.31, 1.41, (z0 + z1) / 2))), "M_Bot", gb, bevel=0.006)
        abox("%s_doorin%d_%d" % (P, sx, k), (0.52, z0 - z1 - 0.1, 0.012), tuple(KV((sx * 0.31, 1.43, (z0 + z1) / 2))), "M_Int", gb, bevel=0.0)
        for j in range(4):       # door stiffeners (visible when open)
            abox("%s_drib%d_%d_%d" % (P, sx, k, j), (0.5, 0.04, 0.04), tuple(KV((sx * 0.31, 1.45, z0 - 0.4 - (z0 - z1 - 0.8) * j / 3))), "M_Int", gb, bevel=0.0)
for k, (z0, z1) in enumerate(((1.8, -2.2), (-2.6, -6.0))):
    abox("%s_bayc%d" % (P, k), (1.18, z0 - z1, 0.02), tuple(KV((0, 1.95, (z0 + z1) / 2))), "M_Int", G, bevel=0.0)
    for sx in (1, -1):
        abox("%s_bayw%d_%d" % (P, k, sx), (0.02, z0 - z1, 0.54), tuple(KV((sx * 0.60, 1.68, (z0 + z1) / 2))), "M_Int", G, bevel=0.0)
    for j in range(2):           # bomb racks
        abox("%s_rack%d_%d" % (P, k, j), (0.10, 1.2, 0.08), tuple(KV(((j * 2 - 1) * 0.33, 1.90, (z0 + z1) / 2))), "M_Steel", G, bevel=0.01)
abox(P + "_baymid", (0.10, 0.4, 0.5), tuple(KV((0, 1.68, -2.4))), "M_Int", G, bevel=0.0)
# ------------------------------------------------------------------------------------------------ wings
WR = (1.95, 1.96, 1.20)
for sx, ga, gf in ((1, GAL, GFL), (-1, GAR, GFR)):
    sec, hg = wing("%s_wing" % P, sx, (sx * WR[0], WR[1], WR[2]), 5.10, 6.60, 1.40, 5.66, -2.0, 0.045, 0.035, 1.0, G,
                   parts=[(0.04, 0.50, gf), (0.53, 0.95, ga)], hinge=0.82, nsec=10)
    globals()["HG%d" % sx] = hg
    tip = Vector(sec(1.0)["le"])
    lamp("%s_nav%d" % (P, sx), tuple(tip + Vector((sx * 0.04, 0.5, 0))), "M_NavRed" if sx < 0 else "M_NavGreen", G, r=0.04)
    # LE flap line + wingtip EW fairing
    rod("%s_lef%d" % (P, sx), Vector(sec(0.05)["le"]) + Vector((0, 0.35, 0.0)), Vector(sec(0.95)["le"]) + Vector((0, 0.35, 0.0)), 0.012, "M_RAM", G, seg=6, dirt=False)
    acyl("%s_tipew%d" % (P, sx), 0.06, 1.0, tuple(tip + Vector((0, 0.6, 0))), "M_RAM", G, axis=1, seg=10)
    # pylons P1 / P2 / P3
    for k, (px, lez) in enumerate(((3.0, 0.04), (4.3, -1.41), (5.5, -2.74))):
        f = (px - WR[0]) / 5.10
        wy = WR[1] + (px - WR[0]) * math.tan(math.radians(-2.0)) - 0.06
        L = 1.9 if k < 2 else 1.5
        abox("%s_pyl%d_%d" % (P, sx, k), (0.07, L, 0.32), tuple(KV((sx * px, wy - 0.12, lez - 0.8 - L / 2 + 0.6))), "M_Bot", G, bevel=0.02)
# LEVCONs (all-moving leading-edge root extensions)
for sx, gl in ((1, GLL), (-1, GLR)):
    wing("%s_lev" % P, sx, (sx * 1.02, 2.06, 5.05), 1.15, 2.6, 0.55, 2.35, -1.0, 0.035, 0.03, 1.0, gl, nsec=4)
# ------------------------------------------------------------------------------------------------ tail: stabilators + canted fins
for sx, ge in ((1, GEL), (-1, GER)):
    wing("%s_hs" % P, sx, (sx * 2.02, 1.80, -5.90), 2.30, 2.80, 0.90, 2.10, -3.0, 0.045, 0.035, 1.0, ge, nsec=5)
CANT = 26.0
for sx, gr in ((1, GRL), (-1, GRR)):
    tmp = "%s_tmpfin%d" % (P, sx)
    wing("%s_fin%d" % (P, sx), 1, (0, 0, 0), 2.45, 3.10, 1.05, 2.25, 0, 0.045, 0.035, 1.0, tmp, ud_axis='X', nsec=6)
    M = Matrix.Translation(KV((sx * 1.85, 2.10, -4.20))) @ Matrix.Rotation(math.radians(CANT * sx), 4, 'Y')
    for o in list(col_get("BUILD").objects):
        if o.get("grp") == tmp:
            o.matrix_world = M @ o.matrix_world
            o["grp"] = gr
            paint(o, zt=0.3)
    # fin-tip antenna fairing + strobe
    tp = M @ KV((0, 2.45, -2.25 - 0.45))
    acyl("%s_fintip%d" % (P, sx), 0.05, 0.9, tuple(tp), "M_RAM", gr, axis=1, seg=8)
    lamp("%s_strobe%d" % (P, sx), tuple(M @ KV((0, 2.40, -2.25 - 1.0))), "M_Strobe", gr, r=0.03)
# beaver-tail stinger between the nozzles (rear radar / chaff-flare dispensers)
lathe_y(P + "_sting", [(0.0, 0.0), (0.0, 0.30), (0.8, 0.26), (1.5, 0.14), (1.7, 0.0)], tuple(KV((0, 1.85, -8.4))), ["M_Top"], G, seg=16, axis='Y')
for sx in (1, -1):
    abox("%s_flr%d" % (P, sx), (0.14, 0.30, 0.04), tuple(KV((sx * 0.55, 1.62, -7.6))), "M_Black", G, bevel=0.005)
# ------------------------------------------------------------------------------------------------ gun, probes, antennas, lights
lathe_y(P + "_gunf", [(0.0, 0.0), (0.0, 0.07), (0.3, 0.09), (0.9, 0.06), (1.0, 0.0)], tuple(KV((-0.80, 2.22, 5.40))), ["M_Top"], G, seg=12, axis='Y')
acyl(P + "_gunmz", 0.035, 0.05, tuple(KV((-0.80, 2.22, 5.42))), "M_Bore", G, axis=1, seg=10, dirt=False)
for sx in (1, -1):
    rod("%s_pitot%d" % (P, sx), KV((sx * 0.38, 2.05, 8.9)), KV((sx * 0.40, 2.05, 9.45)), 0.012, "M_Steel", G, seg=6)
    abox("%s_aoa%d" % (P, sx), (0.012, 0.10, 0.05), tuple(KV((sx * 0.55, 2.10, 7.4))), "M_Steel", G, bevel=0.0)
    abox("%s_cheek%d" % (P, sx), (0.02, 0.9, 0.35), tuple(KV((sx * 0.70, 2.05, 6.6))), "M_RAM", G, bevel=0.01)   # side-looking AESA
rod(P + "_pitotn", KV((0, 2.05, 10.25)), KV((0, 2.05, 10.65)), 0.015, "M_Steel", G, seg=6)
abox(P + "_antd", (0.04, 0.35, 0.18), tuple(KV((0, 2.36, 1.0))), "M_RAM", G, bevel=0.02)
abox(P + "_antv", (0.04, 0.25, 0.14), tuple(KV((0, 1.38, 4.5))), "M_RAM", G, bevel=0.02)
lamp(P + "_beacon", tuple(KV((0, 2.33, 0.0))), "M_NavRed", G, r=0.05)
# ------------------------------------------------------------------------------------------------ landing gear
for sx, grp in ((1, GL), (-1, GR)):
    gear_leg("%s_mg%d" % (P, sx), (sx * 1.60, 1.10, -1.0), (sx * 1.62, 0.53, -1.55), 0.53, 0.30, grp, door=(0.9, 0.55), door_mat="M_Bot")
    rod("%s_brace%d" % (P, sx), KV((sx * 1.62, 0.85, -1.38)), KV((sx * 1.55, 1.12, -0.35)), 0.03, "M_Steel", grp, seg=8)
    abox("%s_mdoor%d" % (P, sx), (0.02, 1.4, 0.5), tuple(KV((sx * 1.88, 1.05, -1.2))), "M_Bot", grp, rot=(0, math.radians(sx * 8), 0), bevel=0.005)
NZ = 6.75
gear_leg(P + "_ng", (0, 1.62, NZ + 0.15), (0.0, 0.34, NZ), 0.34, 0.20, GN, fork=False)
tyre(P + "_ngw2", KV((-0.15, 0.34, NZ)), 0.34, 0.18, GN)
acyl(P + "_ngax", 0.03, 0.34, tuple(KV((0, 0.34, NZ))), "M_Steel", GN, axis=0, seg=8)
lamp(P + "_ldl", tuple(KV((0, 0.95, NZ + 0.15))), "M_Lume", GN, r=0.06)
for sx in (1, -1):
    abox("%s_ndoor%d" % (P, sx), (0.02, 1.2, 0.40), tuple(KV((sx * 0.30, 1.25, NZ))), "M_Bot", GN, bevel=0.005)
# closed gear doors (shown when the gear is up)
for sx in (1, -1):
    abox("%s_mclosed%d" % (P, sx), (0.70, 1.7, 0.03), tuple(KV((sx * 1.55, 0.86, -1.1))), "M_Bot", GDC, bevel=0.006)
abox(P + "_nclosed", (0.44, 1.5, 0.03), tuple(KV((0, 1.40, NZ + 0.4))), "M_Bot", GDC, bevel=0.006)
# ------------------------------------------------------------------------------------------------ stores (hidden by count)
def kh38(name, c, grp):
    lathe_y(name, [(0.0, 0.0), (0.0, 0.03), (0.25, 0.12), (0.6, 0.155), (4.0, 0.155), (4.2, 0.10), (4.2, 0.0)],
            tuple(KV((c[0], c[1], c[2] + 2.1))), ["M_Kh38"], grp, seg=14, axis='Y')
    for a in range(4):
        t = a * PI / 2 + PI / 4
        for z0, sz in ((c[2] + 1.2, 0.5), (c[2] - 1.7, 0.4)):
            abox("%s_f%d_%d" % (name, a, int(sz * 10)), (0.012, sz, 0.20), tuple(KV((c[0] + math.cos(t) * 0.19, c[1] + math.sin(t) * 0.19, z0))),
                 "M_Kh38", grp, rot=(0, -t + PI / 2, 0), bevel=0.0)
    acyl(name + "_sk", 0.07, 0.02, tuple(KV((c[0], c[1], c[2] + 2.12))), "M_Black", grp, axis=1, seg=10, dirt=False)
def b8pod(name, c, grp):
    lathe_y(name, [(0.0, 0.20), (0.05, 0.26), (2.5, 0.26), (2.7, 0.18), (2.7, 0.0)], tuple(KV((c[0], c[1], c[2] + 1.35))), ["M_Pod"], grp, seg=18, axis='Y')
    acyl(name + "_face", 0.21, 0.02, tuple(KV((c[0], c[1], c[2] + 1.36))), "M_Black", grp, axis=1, seg=18, dirt=False)
    for k in range(7):
        a = 2 * PI * k / 7
        acyl("%s_t%d" % (name, k), 0.045, 0.03, tuple(KV((c[0] + math.cos(a) * 0.13, c[1] + math.sin(a) * 0.13, c[2] + 1.37))), "M_Steel", grp, axis=1, seg=8, dirt=False)
STORES = []
for k, (px, py, pz) in enumerate(((4.3, 1.42, -2.4), (5.5, 1.42, -3.5), (3.0, 1.47, -1.3))):
    for j, sx in enumerate((1, -1)):
        i = k * 2 + j
        kh38("%s_kh%d" % (P, i), (sx * px, py, pz), "%s_khg%d" % (P, i)); STORES.append(("kh38_%d" % i, "%s_khg%d" % (P, i)))
for k, (px, py, pz) in enumerate(((3.0, 1.35, -1.2), (4.3, 1.30, -2.5))):
    for j, sx in enumerate((1, -1)):
        i = k * 2 + j
        b8pod("%s_pod%d" % (P, i), (sx * px, py, pz), "%s_podg%d" % (P, i)); STORES.append(("pod_%d" % i, "%s_podg%d" % (P, i)))
for i, (bx, by, bz) in enumerate(((0.33, 1.65, -0.4), (-0.33, 1.65, -0.4), (0.33, 1.65, -4.2), (-0.33, 1.65, -4.2), (3.0, 1.43, -1.5), (-3.0, 1.43, -1.5))):
    bomb_geo("%s_fab%d" % (P, i), (bx, by, bz), 2.4, 0.40, "%s_fabg%d" % (P, i), mat="M_BombSU")
    STORES.append(("fab_%d" % i, "%s_fabg%d" % (P, i)))
# ------------------------------------------------------------------------------------------------ markings
w_obs = [o for o in col_get("BUILD").objects if o.name.startswith(P + "_wing")]
for o in w_obs:
    paint(o, zt=0.3)
for o in col_get("BUILD").objects:
    if o.name.startswith((P + "_hs", P + "_lev")):
        paint(o, zt=0.3)
fins = {sx: [o for o in col_get("BUILD").objects if o.get("grp") == (GRL if sx > 0 else GRR) and o.type == 'MESH' and "_fin" in o.name] for sx in (1, -1)}
for sx in (1, -1):
    M = Matrix.Translation(KV((sx * 1.85, 2.10, -4.20))) @ Matrix.Rotation(math.radians(CANT * sx), 4, 'Y')
    nO = M.to_3x3() @ Vector((sx, 0, 0))
    for side, nn in (("o", nO), ("i", -nO)):
        cpt = M @ KV((0, 1.25, -1.6)) + nn * 0.05
        decal_on("%s_fstar%d%s" % (P, sx, side), max(fins[sx], key=lambda o: len(o.data.polygons)), cpt, nn, M.to_3x3() @ Vector((0, 0, 1)), 0.75, "star_ru", GRL if sx > 0 else GRR)
wingL = [o for o in w_obs if o.name.startswith(P + "_wing_w1_") and not o.name.endswith("m")]
wingR = [o for o in w_obs if o.name.startswith(P + "_wing_w-1_") and not o.name.endswith("m")]
for sx, obs in ((1, wingL), (-1, wingR)):
    big = max(obs, key=lambda o: len(o.data.polygons))
    decal_on("%s_wst%d" % (P, sx), big, KV((sx * 4.6, 2.2, -3.3)), Vector((0, 0, 1)), Vector((0, -1, 0)), 1.0, "star_ru", G)
    decal_on("%s_wsb%d" % (P, sx), big, KV((sx * 4.6, 1.5, -3.3)), Vector((0, 0, -1)), Vector((0, -1, 0)), 1.0, "star_ru", G)
for sx in (1, -1):
    decal_on("%s_flag%d" % (P, sx), fusel, KV((sx * 0.75, 2.30, 3.6)), Vector((sx, 0, 0.4)), Vector((0, 0, 1)), 0.45, "flag_ru", G)
    decal_on("%s_vks%d" % (P, sx), fusel, KV((sx * 0.60, 2.20, 8.2)), Vector((sx, 0, 0)), Vector((0, 0, 1)), 0.9, "vks", G)

# ------------------------------------------------------------------------------------------------ rig + export
auto_attach([G], bracket_groups=())
weather(gen_weather([KV((s * NX, 1.55, -8.9)) for s in (1, -1)], 1.0))
rt = root("root")
group_under(G, "su57_body", rt)
for nm, grp, top in (("gear_L", GL, (1.60, 1.10, -1.0)), ("gear_R", GR, (-1.60, 1.10, -1.0)), ("gear_N", GN, (0, 1.62, NZ + 0.15))):
    pv = pivot(nm, tuple(KV(top)), rt)
    group_under(grp, nm + "_geo", pv)
group_under(GDC, "gear_door_closed", rt)
def hinge_node(nm, grp, pts):
    a, b = pts
    pv = pivot(nm, tuple((a + b) / 2), rt)
    group_under(grp, nm + "_geo", pv)
hinge_node("ail_L", GAL, HG1[GAL]); hinge_node("ail_R", GAR, globals()["HG-1"][GAR])
hinge_node("flap_L", GFL, HG1[GFL]); hinge_node("flap_R", GFR, globals()["HG-1"][GFR])
for nm, grp, p in (("elev_L", GEL, (2.45, 1.80, -7.10)), ("elev_R", GER, (-2.45, 1.80, -7.10)),
                   ("rud_L", GRL, (1.85, 2.10, -5.40)), ("rud_R", GRR, (-1.85, 2.10, -5.40)),
                   ("levcon_L", GLL, (1.30, 2.05, 3.10)), ("levcon_R", GLR, (-1.30, 2.05, 3.10)),
                   ("bay_L", GBL, (0.61, 1.41, -2.1)), ("bay_R", GBR, (-0.61, 1.41, -2.1))):
    pv = pivot(nm, tuple(KV(p)), rt)
    group_under(grp, nm + "_geo", pv)
for sd, sx in (("L", 1), ("R", -1)):
    gN, gG, gA, gX = NOZ[sd]
    py = pivot("nozy_" + sd, tuple(KV((sx * NX, 1.55, -8.95))), rt)
    pp = pivot("noz_" + sd, tuple(KV((sx * NX, 1.55, -8.95))), py)
    group_under(gN, "noz_geo_" + sd, pp)
    for nm, g in (("glow_", gG), ("ab_", gA), ("abx_", gX)):
        e = pivot(nm + sd, tuple(KV((sx * NX, 1.55, -8.95))), pp)
        group_under(g, nm + sd + "_geo", e)
for nm, g in STORES:
    e = pivot(nm, (0, 0, 0), rt)
    group_under(g, nm + "_geo", e)
rw_export("/home/user/.cache/rwveh/raw/su57.glb", "/home/user/models/su57.blend", "SU57")
