# Petlyakov Pe-8 (TB-7), AM-35A series (1941-42). Real dimensions: length 23.59 m, span 39.13 m, wing area 188.7 m2,
# height 6.2 m; props AV-5L-116 3-blade 4.1 m. Glazed navigator nose under a twin-ShKAS nose turret, stepped pilots'
# greenhouse, dorsal TAT turret (ShVAK), tail turret (ShVAK), UBT gunners in the rear of both inner nacelles.
# Bay: 16 x FAB-250 / 4 x FAB-1000 / 1 x FAB-5000NG (semi-recessed). AMT-4/AMT-6 night-bomber scheme, black undersides.
exec(open("/home/user/tools/rwveh/craft_common.py").read())
exec(open("/home/user/tools/rwveh/plane_lib.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
plane_mats("T_PlAMT46", "T_PlAMT46", "T_PlAMT6", "T_PlInteriorSU", frame=(0.20, 0.23, 0.15))
P = "PE"
G = P + "_body"
GL, GR, GT, GAL, GAR, GE, GRU, GFL, GFR, GBL, GBR = (P + s for s in ("_gl", "_gr", "_gt", "_al", "_ar", "_el", "_ru", "_fl", "_fr", "_bl", "_br"))
TAIL = 10.0
Z_NOSE, Z_TAIL = 8.70, -14.9
ctrl = [
    (0.000, 3.30, 0.10, 0.10, 0.10, 2.0),
    (0.015, 3.25, 0.55, 0.55, 0.55, 2.0),
    (0.040, 3.15, 0.95, 1.00, 0.95, 2.1),
    (0.080, 3.10, 1.15, 1.18, 1.15, 2.3),
    (0.140, 3.10, 1.22, 1.25, 1.22, 2.5),
    (0.300, 3.10, 1.25, 1.28, 1.25, 2.6),
    (0.450, 3.12, 1.20, 1.22, 1.20, 2.6),
    (0.600, 3.20, 1.00, 1.05, 1.00, 2.5),
    (0.750, 3.35, 0.72, 0.80, 0.72, 2.4),
    (0.880, 3.50, 0.45, 0.55, 0.45, 2.2),
    (0.960, 3.58, 0.40, 0.45, 0.40, 2.0),
    (1.000, 3.60, 0.10, 0.10, 0.10, 2.0)]
prof = ctrl_at(ctrl, Z_NOSE, Z_TAIL)
def fmat(y, ang, z):
    zk = -y
    if zk > 7.0 and not (235 < ang < 305):
        return 1
    if zk < -13.95:
        return 1
    return 0
fusel = fus(P + "_fus", Z_NOSE, Z_TAIL, ctrl, ["M_Top", "M_Canopy"], G, mat_fn=fmat, n=60, seg=40)
paint(fusel, zt=0.5)
for i, z in enumerate((7.0, 7.4, 7.8, 8.15)):
    frame_ring("%s_nr%d" % (P, i), prof, z, -55, 235, 0.02, G, n=24, off=0.006)
for i, a in enumerate((-45, -15, 15, 45, 75, 105, 135, 165, 195, 225)):
    frame_line("%s_nl%d" % (P, i), prof, 7.0, 8.4, a, 0.017, G, n=10, off=0.006)
for i, z in enumerate((-13.95, -14.35)):
    frame_ring("%s_tr%d" % (P, i), prof, z, 0, 360, 0.02, G, n=24, off=0.006)
inner_tub(P + "_tubn", ctrl, Z_NOSE, Z_TAIL, 8.3, 7.0, -30, G)
inner_tub(P + "_tubc", ctrl, Z_NOSE, Z_TAIL, 7.02, 6.3, 89, G)
inner_tub(P + "_tubp", ctrl, Z_NOSE, Z_TAIL, 6.32, 3.88, 52, G)    # r12: open pilots' cabin
inner_tub(P + "_tubr", ctrl, Z_NOSE, Z_TAIL, 3.9, 2.8, 89, G)
# cabin side windows
for sx in (1, -1):
    for k in range(5):
        z = 6.4 - 0.55 * k
        p = surf_pt(prof, z, 10 if sx > 0 else 170, 0.006)
        abox("%s_sw%d%d" % (P, sx, k), (0.01, 0.30, 0.26), tuple(KV((p[0], p[1], z))), "M_Canopy", G, bevel=0.0, dirt=False)
        abox("%s_swf%d%d" % (P, sx, k), (0.012, 0.34, 0.30), tuple(KV((p[0] - sx * 0.004, p[1], z))), "M_Frame", G, bevel=0.0, dirt=False)
# pilots' greenhouse (stepped)
CZ0, CZ1, CZ2, CZ3 = 6.3, 5.8, 3.9, 3.3
def cprof(y):
    z = -y
    if z > CZ1:
        u = (CZ0 - z) / (CZ0 - CZ1); ht, hw = 0.04 + 0.5 * u, 0.80 + 0.1 * u
    elif z > CZ2:
        ht, hw = 0.54, 0.90
    else:
        u = (CZ2 - z) / (CZ2 - CZ3); ht, hw = 0.54 * (1 - u) + 0.04, 0.90 - 0.1 * u
    return (4.28, hw, ht, 0.05, 3.2)
cys = [-CZ0 + (CZ0 - CZ3) * k / 16 for k in range(17)]
loft(P + "_gh", cys, cprof, ["M_Canopy"], G, seg=14, angle_range=(0, 180), smooth=False, dirt=False)
cp = lambda z: cprof(-z)
for i, z in enumerate((CZ0 - 0.01, 6.05, CZ1, 5.35, 4.85, 4.35, CZ2, CZ3 + 0.02)):
    frame_ring("%s_gr%d" % (P, i), cp, z, 0, 180, 0.018, G, off=0.006)
for i, a in enumerate((8, 35, 62, 90, 118, 145, 172)):
    frame_line("%s_gl%d" % (P, i), cp, CZ0, CZ3, a, 0.015, G, off=0.006)
for sx in (1, -1):
    bomber_seat("%s_ps%d" % (P, sx), (sx * 0.45, 3.62, 4.8), G)
    yoke("%s_yk%d" % (P, sx), (sx * 0.45, 3.35, 5.45), G)
abox(P + "_cfloor", (1.9, 2.6, 0.04), tuple(KV((0, 3.35, 4.9))), "M_Int", G, bevel=0.0)
cut_top(fusel, prof, CZ0, CZ2, 52)    # r12: the hull skin no longer closes under the greenhouse
abox(P + "_panel", (1.6, 0.04, 0.36), tuple(KV((0, 4.12, 5.9))), "M_Dash", G, bevel=0.01)
for k in range(10):
    dial("%s_dl%d" % (P, k), (-0.68 + 0.15 * k, 4.20 - 0.12 * (k % 2), 5.87), 0.045, G)
abox(P + "_pedst", (0.25, 0.5, 0.45), tuple(KV((0, 3.6, 5.2))), "M_Dash", G, bevel=0.02)
for k in range(4):
    rod("%s_thl%d" % (P, k), KV((-0.09 + 0.06 * k, 3.86, 5.2)), KV((-0.09 + 0.06 * k, 4.0, 5.32)), 0.01, "M_Steel", G, seg=6)
bomber_seat(P + "_nav", (0.0, 2.55, 7.0), G, armour=False)
abox(P + "_navt", (0.8, 0.5, 0.04), tuple(KV((-0.4, 2.85, 6.6))), "M_Wood", G, bevel=0.0)
abox(P + "_bulk", (2.2, 0.05, 2.2), tuple(KV((0, 3.1, 2.75))), "M_Int", G, bevel=0.0)
# ------------------------------------------------------------------------------------------------ wings + engines
NAC = [(4.75, 2.62, 6.0, -7.2, True), (10.1, 2.92, 5.4, -3.0, False)]
for sx, ga, gf in ((1, GAL, GFL), (-1, GAR, GFR)):
    sec, hg = wing("%s_wing" % P, sx, (sx * 1.10, 2.45, 3.40), 18.45, 7.0, 2.6, 2.3, 3.0, 0.18, 0.12, 0.88, G,
                   parts=[(0.03, 0.47, gf), (0.53, 0.95, ga)], hinge=0.76, nsec=16)
    globals()["HG%d" % sx] = hg
    tip = Vector(sec(0.997)["le"]) + Vector((0, 0.5, 0))
    lamp("%s_nav%d" % (P, sx), tuple(tip + Vector((sx * 0.04, 0, 0))), "M_NavRed" if sx < 0 else "M_NavGreen", G, r=0.05)
    for k, (nx, ny, z0, z1, inner) in enumerate(NAC):
        x = sx * nx
        nctrl = [(0.0, ny, 0.48, 0.48, 0.48, 2.0, x), (0.08, ny, 0.62, 0.62, 0.66, 2.1, x), (0.30, ny, 0.64, 0.64, 0.80, 2.3, x),
                 (0.55, ny, 0.56, 0.58, 0.74, 2.3, x), (0.85, ny + 0.02, 0.40, 0.42, 0.46, 2.2, x), (1.0, ny + 0.04, 0.12, 0.14, 0.14, 2.0, x)]
        def nmat(y, ang, zz, inner=inner, z1=z1):
            return 1 if inner and -y < z1 + 0.7 else 0
        nac = fus("%s_nac%d%d" % (P, sx, k), z0, z1, nctrl, ["M_Top", "M_Canopy"], G, mat_fn=nmat, n=30, seg=28)
        paint(nac, zt=0.4)
        abox("%s_chin%d%d" % (P, sx, k), (0.5, 1.4, 0.36), tuple(KV((x, ny - 0.72, z0 - 1.4))), "M_Bot", G, bevel=0.08)
        abox("%s_chinm%d%d" % (P, sx, k), (0.42, 0.02, 0.28), tuple(KV((x, ny - 0.72, z0 - 0.69))), "M_Mesh", G, bevel=0.0, dirt=False)
        for s2 in (1, -1):
            exhaust_row("%s_x%d%d%d" % (P, sx, k, s2), Vector((x + s2 * 0.6, ny + 0.12, z0 - 0.7)), 0.22, 6, G, s2, r=0.045, length=0.14)
        idx = (0 if sx > 0 else 2) + k            # prop_0 = left outer? -> keep: 0 L-inner, 1 L-outer, 2 R-inner, 3 R-outer
        propeller("%s_pr%d" % (P, idx), (x, ny, z0), 2.05, 3, 0.32, P + "_p%d" % idx, P + "_d%d" % idx, 0.32, 0.55,
                  spin_mat="M_Spin", pitch=(44, 18))
        globals()["PROP%d" % idx] = (x, ny, z0)
    grp = GL if sx > 0 else GR
    gear_leg("%s_mg%d" % (P, sx), (sx * 4.75, 2.0, 1.4), (sx * 4.75, 0.78, 1.0), 0.78, 0.36, grp, fork=True)
# ------------------------------------------------------------------------------------------------ tail
for sx in (1, -1):
    wing("%s_hs" % P, sx, (sx * 0.2, 3.95, -12.4), 5.0, 2.8, 1.4, 0.5, 0.0, 0.11, 0.09, 0.6, G,
         parts=[(0.05, 0.97, GE)], hinge=0.64)
_, HF = fin(P, (0, 4.1, -11.6), 2.5, 3.6, 1.5, 1.2, 0.11, 0.08, 0.45, G, rud_grp=GRU, hinge=0.6)
TZ = -13.2
TY = abs(TZ) * math.tan(math.radians(TAIL)) + 0.30
gear_leg(P + "_tg", (0, 3.15, TZ + 0.12), (0, TY, TZ), 0.30, 0.16, GT, fork=True, torque=False)
rod(P + "_mast", KV((0, 4.35, 2.8)), KV((0, 4.95, 2.6)), 0.025, "M_Top", G, seg=6)
rod(P + "_wire", KV((0, 4.93, 2.6)), KV((0, 6.5, -12.5)), 0.005, "M_Black", G, seg=4)
# ------------------------------------------------------------------------------------------------ bomb bay
for i in range(16):
    x = 0.42 if i % 2 == 0 else -0.42
    y = 2.35 if (i // 2) % 2 == 0 else 2.80
    z = 1.95 - 1.3 * (i // 4)
    bomb_geo("%s_f250_%d" % (P, i), (x, y, z), 1.9, 0.30, P + "_b%d" % i, mat="M_BombSU")
for i in range(4):
    x = 0.45 if i % 2 == 0 else -0.45
    bomb_geo("%s_f1000_%d" % (P, i), (x, 2.5, 1.2 - 2.7 * (i // 2)), 3.0, 0.63, P + "_k%d" % i, mat="M_BombSU")
bomb_geo(P + "_f5000", (0, 2.3, -0.2), 5.2, 1.0, P + "_f0", mat="M_BombSU", ring=False)
for sx, grp in ((1, GBL), (-1, GBR)):
    abox("%s_door%d" % (P, sx), (0.92, 5.5, 0.03), tuple(KV((sx * 0.47, 1.87, -0.15))), "M_Bot", grp, bevel=0.0)
abox(P + "_bayroof", (1.8, 5.5, 0.03), tuple(KV((0, 3.4, -0.15))), "M_Int", G, bevel=0.0)
# ------------------------------------------------------------------------------------------------ turrets
TUR = [((0, 3.30, 8.15), True, 0.0, 0.9, "mg"), ((0, 4.62, -1.0), False, 0.0, 1.6, "hmg"), ((0, 3.60, -14.45), False, 0.30, 1.4, "hmg"),
       ((4.75, 2.66, -7.0), False, 0.0, 1.2, "hmg"), ((-4.75, 2.66, -7.0), False, 0.0, 1.2, "hmg")]
for i, (b, twin, ball, ln, cal) in enumerate(TUR):
    turret_gun(P, i, b, P + "_ty%d" % i, P + "_tp%d" % i, ln, twin=twin, ball=ball, cal=cal, ring=0.62 if i == 1 else 0.0)
bm = bm_sphere(0.72, KV((0, 4.42, -1.0)), seg=20, rings=10, scale=(1, 1, 0.75))
dome = aobj(P + "_tatdome", bm, ["M_Canopy"], G, smooth=True, dirt=False)
# ------------------------------------------------------------------------------------------------ markings
for sx in (1, -1):
    wdecal("%s_st%d" % (P, sx), fusel, (sx * 0.8, 3.35, -8.4), (sx, 0, 0), (0, 1, 0), 1.2, "star_red", G)
    wdecal("%s_n%d" % (P, sx), fusel, (sx * 1.2, 3.0, 6.1), (sx, 0, 0), (0, 1, 0), 0.7, "vvs_42", G)
w_obs = [o for o in col_get("BUILD").objects if o.name.startswith((P + "_wing", P + "_hs", P + "_fin"))]
for o in w_obs:
    paint(o, zt=0.3)
fins = [o for o in w_obs if o.name.startswith(P + "_fin") and not o.name.endswith("m")]
for sx in (1, -1):
    wdecal("%s_fst%d" % (P, sx), fins, (sx * 0.2, 5.25, -12.4), (sx, 0, 0), (0, 1, 0), 1.1, "star_red", G)
    obs = [o for o in w_obs if o.name.startswith("%s_wing_w%d_" % (P, sx)) and not o.name.endswith("m")]
    wdecal("%s_wsb%d" % (P, sx), obs, (sx * 13.0, 2.0, 0.4), (0, -1, 0), (0, 0, 1), 2.0, "star_red", G)
    wdecal("%s_wst%d" % (P, sx), obs, (sx * 13.0, 3.6, 0.4), (0, 1, 0), (0, 0, 1), 2.0, "star_red", G)
# ------------------------------------------------------------------------------------------------ rig + export
auto_attach([G], bracket_groups=())
weather(gen_weather([KV((s * nx + d, ny + 0.12, z0 - 1.0)) for s in (1, -1) for (nx, ny, z0, z1, i) in NAC for d in (0.6, -0.6)], 1.4))
rt = root("root")
group_under(G, "pe8_body", rt)
for i in range(4):
    pv = pivot("prop_%d" % i, tuple(KV(globals()["PROP%d" % i])), rt)
    group_under(P + "_p%d" % i, "prop_%d_geo" % i, pv)
    group_under(P + "_d%d" % i, "prop_disc_%d" % i, rt)
for nm, grp, top in (("gear_L", GL, (4.75, 2.0, 1.4)), ("gear_R", GR, (-4.75, 2.0, 1.4)), ("gear_T", GT, (0, 3.15, TZ + 0.12))):
    pv = pivot(nm, tuple(KV(top)), rt)
    group_under(grp, nm + "_geo", pv)
def hinge_node(nm, grp, pts):
    a, b = pts
    pv = pivot(nm, tuple((a + b) / 2), rt)
    group_under(grp, nm + "_geo", pv)
hinge_node("ail_L", GAL, HG1[GAL]); hinge_node("ail_R", GAR, globals()["HG-1"][GAR])
hinge_node("flap_L", GFL, HG1[GFL]); hinge_node("flap_R", GFR, globals()["HG-1"][GFR])
ev = pivot("elev", tuple(KV((0, 3.95, -12.4 - 2.8 * 0.64))), rt)
group_under(GE, "elev_geo", ev)
hinge_node("rud", GRU, HF[GRU])
for nm, grp, sx in (("bay_L", GBL, 1), ("bay_R", GBR, -1)):
    pv = pivot(nm, tuple(KV((sx * 0.93, 1.87, -0.15))), rt)
    group_under(grp, nm + "_geo", pv)
for i in range(16):
    group_under(P + "_b%d" % i, "bomb_%d" % i, rt)
for i in range(4):
    group_under(P + "_k%d" % i, "bombk_%d" % i, rt)
group_under(P + "_f0", "bombf_0", rt)
for i, (b, *_r) in enumerate(TUR):
    ty = pivot("tur_%d_yaw" % i, tuple(KV(b)), rt)
    group_under(P + "_ty%d" % i, "tur_%d_ring" % i, ty)
    tp = pivot("tur_%d_pitch" % i, tuple(KV(b)), ty)
    group_under(P + "_tp%d" % i, "tur_%d_gun" % i, tp)
rw_export("/home/user/.cache/rwveh/raw/pe8.glb", "/home/user/models/pe8.blend", "PE8")
