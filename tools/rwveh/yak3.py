# Yakovlev Yak-3 (1944, VK-105PF2). Real dimensions: length 8.50 m, span 9.20 m, wing area 14.85 m2, track 2.45 m,
# prop VISh-105SV 3-blade 2.85 m. Teardrop canopy over a cut-down rear deck, ventral radiator tunnel, oil-cooler intakes
# in both wing roots, inward-retracting main gear, retractable tail wheel. AMT-11/AMT-12 over AMT-7 (1944 standard).
exec(open("/home/user/tools/rwveh/craft_common.py").read())
exec(open("/home/user/tools/rwveh/plane_lib.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
plane_mats("T_PlAMT1112", "T_PlAMT1112", "T_PlAMT7", "T_PlInteriorSU", frame=(0.26, 0.29, 0.32))
P = "YK"
G, GP, GD, GL, GR, GT, GAL, GAR, GE, GRU, GFL, GFR = (P + s for s in ("_body", "_prop", "_disc", "_gl", "_gr", "_gt", "_al", "_ar",
                                                                    "_el", "_ru", "_fl", "_fr"))
TAIL = 11.0
Z_NOSE, Z_TAIL = 2.05, -5.72
ctrl = [  # t (0 = cowl front .. 1 = tail cone), yc, hw, ht, hb, n
    (0.000, 1.62, 0.33, 0.33, 0.36, 2.2),
    (0.060, 1.62, 0.41, 0.42, 0.47, 2.3),
    (0.160, 1.63, 0.44, 0.45, 0.55, 2.4),
    (0.255, 1.64, 0.44, 0.40, 0.58, 2.5),
    (0.350, 1.64, 0.43, 0.40, 0.60, 2.5),
    (0.450, 1.64, 0.40, 0.34, 0.56, 2.4),
    (0.560, 1.66, 0.33, 0.30, 0.46, 2.3),
    (0.680, 1.70, 0.25, 0.27, 0.36, 2.2),
    (0.800, 1.75, 0.17, 0.23, 0.25, 2.1),
    (0.910, 1.80, 0.10, 0.18, 0.15, 2.0),
    (1.000, 1.86, 0.035, 0.08, 0.05, 2.0)]
prof = ctrl_at(ctrl, Z_NOSE, Z_TAIL)
def fmat(y, ang, z):
    zk = -y
    if zk > 1.88:
        return 1                 # red nose ring (regimental marking)
    return 0
fusel = fus(P + "_fus", Z_NOSE, Z_TAIL, ctrl, ["M_Top", "M_RedNose"], G, mat_fn=fmat, n=44, seg=36)
paint(fusel, zt=0.3)
inner_tub(P + "_tub", ctrl, Z_NOSE, Z_TAIL, 0.1, -1.7, 47, G)
# teardrop canopy: windscreen (flat front pane) + sliding bubble
CZ0, CZ1, CZ2, CZ3 = 0.02, -0.36, -1.45, -2.05
def cprof(y):
    z = -y
    if z > CZ1:
        u = (CZ0 - z) / (CZ0 - CZ1); ht, hw = 0.03 + 0.31 * u, 0.36 - 0.02 * u
    elif z > CZ2:
        u = (CZ1 - z) / (CZ1 - CZ2); ht, hw = 0.34 + 0.03 * math.sin(u * PI), 0.34 - 0.02 * u
    else:
        u = (CZ2 - z) / (CZ2 - CZ3); ht, hw = 0.36 * (1 - u) ** 1.4 + 0.02, 0.32 * (1 - u * 0.75)
    return (2.03, hw, ht, 0.05, 2.3)
cys = [-CZ0 + (CZ0 - CZ3) * k / 18 for k in range(19)]
loft(P + "_glass", cys, cprof, ["M_Canopy"], G, seg=16, angle_range=(0, 180), smooth=True, dirt=False)
cp = lambda z: cprof(-z)
for i, z in enumerate((CZ0 - 0.005, CZ1, CZ1 - 0.04, CZ2)):
    frame_ring("%s_cfr%d" % (P, i), cp, z, 0, 180, 0.015, G, off=0.006)
for i, a in enumerate((4, 176)):
    frame_line("%s_cfl%d" % (P, i), cp, CZ0, CZ3, a, 0.014, G, off=0.004)
frame_line(P + "_cfl_top", cp, CZ2, CZ3 + 0.05, 90, 0.012, G, off=0.006)
abox(P + "_rail", (0.02, 1.2, 0.02), tuple(KV((0.33, 2.02, -1.6))), "M_Steel", G, bevel=0.0)
abox(P + "_rail2", (0.02, 1.2, 0.02), tuple(KV((-0.33, 2.02, -1.6))), "M_Steel", G, bevel=0.0)
abox(P + "_headarm", (0.30, 0.03, 0.26), tuple(KV((0, 2.22, -1.42))), "M_Steel", G, bevel=0.01)
cut_top(fusel, prof, CZ0, CZ2, 47)    # r12: open cockpit under the hood
fighter_cockpit(P + "_ck", (0.0, 2.27, -0.95), G, -0.28, 0.48, 1.24, nation="SU", coam=2.03)
# cowling: UBS troughs, exhaust stacks (6 per side), carburettor intakes in the wing-root fillets, ventral radiator tunnel
for sx in (1, -1):
    barrel("%s_ubs%d" % (P, sx), (sx * 0.12, 2.0, 1.15), (sx * 0.12, 2.0, 1.85), 0.017, G)
    abox("%s_trough%d" % (P, sx), (0.06, 0.8, 0.03), tuple(KV((sx * 0.12, 2.03, 1.45))), "M_Dark", G, bevel=0.01)
    exhaust_row("%s_x%d" % (P, sx), Vector((sx * 0.42, 1.68, 1.62)), 0.16, 6, G, sx, r=0.04, length=0.12)
    abox("%s_intk%d" % (P, sx), (0.36, 0.06, 0.08), tuple(KV((sx * 0.62, 1.30, 0.62))), "M_Mesh", G, bevel=0.01)
lathe_y(P + "_tun", [(-0.95, 0.0), (-0.95, 0.20), (-0.7, 0.26), (0.2, 0.27), (0.55, 0.22), (0.62, 0.0)], tuple(KV((0, 1.02, -0.85))),
        ["M_Bot"], G, seg=18)
abox(P + "_tunm", (0.40, 0.02, 0.26), tuple(KV((0, 1.04, 0.10))), "M_Mesh", G, bevel=0.0, dirt=False)
abox(P + "_tunf", (0.36, 0.30, 0.02), tuple(KV((0, 0.88, -1.55))), "M_Bot", G, rot=(math.radians(15), 0, 0), bevel=0.0)
# ------------------------------------------------------------------------------------------------ wings
for sx, ga, gf in ((1, GAL, GFL), (-1, GAR, GFR)):
    sec, hg = wing("%s_wing" % P, sx, (sx * 0.38, 1.18, 0.52), 4.22, 2.30, 1.15, 0.42, 6.0, 0.145, 0.10, 0.84, G,
                   parts=[(0.02, 0.50, gf), (0.53, 0.94, ga)], hinge=0.75)
    globals()["HG%d" % sx] = hg
    tip = Vector(sec(0.995)["le"]) + Vector((0, 0.16, 0))
    lamp("%s_nav%d" % (P, sx), tuple(tip + Vector((sx * 0.03, 0, 0))), "M_NavRed" if sx < 0 else "M_NavGreen", G, r=0.03)
    if sx == 1:
        rod(P + "_pitot", Vector(sec(0.8)["le"]) + Vector((0, 0.2, -0.07)), Vector(sec(0.8)["le"]) + Vector((0, -0.3, -0.07)), 0.01, "M_Steel", G, seg=6)
# ------------------------------------------------------------------------------------------------ tail
for sx in (1, -1):
    wing("%s_hs" % P, sx, (sx * 0.06, 1.88, -4.72), 1.40, 0.95, 0.52, 0.10, 0.0, 0.11, 0.09, 0.6, G,
         parts=[(0.06, 0.97, GE)], hinge=0.62)
_, HF = fin(P, (0, 1.92, -4.62), 1.0, 1.20, 0.55, 0.36, 0.11, 0.08, 0.45, G, rud_grp=GRU, hinge=0.60)
lamp(P + "_tail", tuple(KV((0, 1.90, -5.75))), "M_Strobe", G, r=0.025)
rod(P + "_mast", KV((0, 2.0, -2.2)), KV((0, 2.45, -2.35)), 0.016, "M_Top", G, seg=6)
rod(P + "_wire", KV((0, 2.43, -2.35)), KV((0, 2.82, -5.25)), 0.003, "M_Black", G, seg=4)
# ------------------------------------------------------------------------------------------------ propeller
propeller(P, (0, 1.62, 2.05), 1.43, 3, 0.22, GP, GD, 0.26, 0.50, spin_mat="M_RedNose", pitch=(46, 17))
acyl(P + "_canmz", 0.03, 0.03, tuple(KV((0, 1.62, 2.555))), "M_Bore", GP, axis=1, seg=8, dirt=False)
# ------------------------------------------------------------------------------------------------ landing gear
for sx, grp in ((1, GL), (-1, GR)):
    gear_leg("%s_mg%d" % (P, sx), (sx * 1.30, 1.05, 0.50), (sx * 1.27, 0.30, 0.58), 0.30, 0.17, grp, door=(0.4, 0.45), wheel_in=True)
TZ = -5.28
TY = abs(TZ) * math.tan(math.radians(TAIL)) + 0.14
gear_leg(P + "_tg", (0, 1.62, TZ + 0.12), (0, TY, TZ), 0.14, 0.07, GT, fork=True, torque=False)
# ------------------------------------------------------------------------------------------------ markings
for sx in (1, -1):
    wdecal("%s_st%d" % (P, sx), fusel, (sx * 0.3, 1.72, -3.05), (sx, 0, 0), (0, 1, 0), 0.50, "star_red", G)
    wdecal("%s_n%d" % (P, sx), fusel, (sx * 0.42, 1.62, -2.0), (sx, 0, 0), (0, 1, 0), 0.30, "vvs_24", G)
fins = [o for o in col_get("BUILD").objects if o.name.startswith(P + "_fin") and not o.name.endswith("m")]
w_obs = [o for o in col_get("BUILD").objects if o.name.startswith((P + "_wing", P + "_hs", P + "_fin"))]
for o in w_obs:
    paint(o, zt=0.3)
finb = max(fins, key=lambda o: len(o.data.polygons))
for sx in (1, -1):
    wdecal("%s_fst%d" % (P, sx), finb, (sx * 0.1, 2.35, -4.95), (sx, 0, 0), (0, 1, 0), 0.50, "star_red", G)
for sx in (1, -1):
    obs = [o for o in w_obs if o.name.startswith("%s_wing_w%d_" % (P, sx)) and not o.name.endswith("m")]
    t = obs
    wdecal("%s_wsb%d" % (P, sx), t, (sx * 3.2, 1.35, -0.2), (0, -1, 0), (0, 0, 1), 0.80, "star_red", G)
# ------------------------------------------------------------------------------------------------ rig + export
auto_attach([G], bracket_groups=())
weather(gen_weather([KV((s * 0.45, 1.68, 1.0)) for s in (1, -1)], 0.7))
rt = root("root")
group_under(G, "yak3_body", rt)
pp = pivot("prop", tuple(KV((0, 1.62, 2.05))), rt)
group_under(GP, "prop_geo", pp)
group_under(GD, "prop_disc", rt)
for nm, grp, top in (("gear_L", GL, (1.30, 1.05, 0.50)), ("gear_R", GR, (-1.30, 1.05, 0.50)), ("gear_T", GT, (0, 1.62, TZ + 0.12))):
    pv = pivot(nm, tuple(KV(top)), rt)
    group_under(grp, nm + "_geo", pv)
def hinge_node(nm, grp, pts):
    a, b = pts
    pv = pivot(nm, tuple((a + b) / 2), rt)
    group_under(grp, nm + "_geo", pv)
hinge_node("ail_L", GAL, HG1[GAL]); hinge_node("ail_R", GAR, globals()["HG-1"][GAR])
hinge_node("flap_L", GFL, HG1[GFL]); hinge_node("flap_R", GFR, globals()["HG-1"][GFR])
ev = pivot("elev", tuple(KV((0, 1.88, -4.72 - 0.95 * 0.62))), rt)
group_under(GE, "elev_geo", ev)
hinge_node("rud", GRU, HF[GRU])
rw_export("/home/user/.cache/rwveh/raw/yak3.glb", "/home/user/models/yak3.blend", "YAK3")
