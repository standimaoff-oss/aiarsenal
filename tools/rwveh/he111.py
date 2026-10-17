# Heinkel He 111 H-6 (1941-43, 2x Jumo 211F-1). Real dimensions: length 16.40 m, span 22.60 m, wing area 87.6 m2,
# height 4.0 m; props VS 11 3-blade 3.5 m. Fully glazed "Kanzel" nose (offset Ikaria MG 15 ball), ventral "Bola"
# gondola (MG FF fixed fwd, MG 81Z rear), dorsal B-Stand (MG 131), 8 vertical ESAC cells (SC 250 nose-up) + 2 external
# PVC 1006 racks (SC 1000). RLM 70/71 splinter over RLM 65. Hakenkreuz deliberately omitted.
exec(open("/home/user/tools/rwveh/craft_common.py").read())
exec(open("/home/user/tools/rwveh/plane_lib.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
plane_mats("T_PlRLM7071", "T_PlRLM7071", "T_PlRLM65", "T_PlInteriorDE", frame=(0.17, 0.21, 0.16))
P = "HE"
G = P + "_body"
GPL, GPR, GDL, GDR, GL, GR, GT = (P + s for s in ("_pl", "_pr", "_dl", "_dr", "_gl", "_gr", "_gt"))
GAL, GAR, GE, GRU, GFL, GFR, GBL, GBR = (P + s for s in ("_al", "_ar", "_el", "_ru", "_fl", "_fr", "_bl", "_br"))
TAIL = 12.0
Z_NOSE, Z_TAIL = 4.65, -11.2
ctrl = [  # t, yc, hw, ht, hb, n
    (0.000, 2.55, 0.05, 0.05, 0.05, 2.0),
    (0.020, 2.55, 0.45, 0.50, 0.45, 2.0),
    (0.050, 2.58, 0.66, 0.72, 0.66, 2.0),
    (0.090, 2.60, 0.76, 0.88, 0.80, 2.1),
    (0.150, 2.60, 0.78, 0.95, 0.90, 2.3),
    (0.300, 2.60, 0.78, 0.98, 0.92, 2.4),
    (0.450, 2.62, 0.74, 0.92, 0.86, 2.4),
    (0.600, 2.70, 0.62, 0.78, 0.70, 2.3),
    (0.750, 2.80, 0.45, 0.60, 0.50, 2.2),
    (0.880, 2.90, 0.28, 0.42, 0.32, 2.1),
    (1.000, 3.00, 0.06, 0.12, 0.06, 2.0)]
prof = ctrl_at(ctrl, Z_NOSE, Z_TAIL)
GZ = 2.62    # glazing aft edge
def fmat(y, ang, z):
    zk = -y
    if zk > GZ and not (238 < ang < 302):
        return 1
    if -3.25 < zk < -2.25 and 60 < ang < 120:
        return 1        # B-Stand sliding hood
    return 0
fusel = fus(P + "_fus", Z_NOSE, Z_TAIL, ctrl, ["M_Top", "M_Canopy"], G, mat_fn=fmat, n=56, seg=40)
paint(fusel, zt=0.45)
# Kanzel frames: rings + longitudinal glazing bars
for i, z in enumerate((GZ, 3.05, 3.45, 3.85, 4.2, 4.45)):
    frame_ring("%s_kr%d" % (P, i), prof, z, -58, 238, 0.018, G, n=24, off=0.006)
for i, a in enumerate((-50, -20, 10, 40, 70, 90, 110, 140, 170, 200, 230)):
    frame_line("%s_kl%d" % (P, i), prof, GZ, 4.52, a, 0.015, G, n=12, off=0.006)
inner_tub(P + "_tubn", ctrl, Z_NOSE, Z_TAIL, 4.35, GZ, -30, G)
inner_tub(P + "_tubc", ctrl, Z_NOSE, Z_TAIL, GZ + 0.02, -3.6, 89, G)
# B-Stand: sliding hood frames + MG windscreen
for i, z in enumerate((-2.25, -2.75, -3.25)):
    frame_ring("%s_br%d" % (P, i), prof, z, 55, 125, 0.016, G, n=8, off=0.006)
# beam (waist) windows
for sx in (1, -1):
    for k, z in enumerate((-4.0, -4.6)):
        p = surf_pt(prof, z, 0 if sx > 0 else 180, 0.006)
        abox("%s_bw%d%d" % (P, sx, k), (0.01, 0.42, 0.34), tuple(KV((p[0], 2.85, z))), "M_Canopy", G, bevel=0.0, dirt=False)
        abox("%s_bwf%d%d" % (P, sx, k), (0.012, 0.46, 0.38), tuple(KV((p[0] - sx * 0.004, 2.85, z))), "M_Frame", G, bevel=0.0, dirt=False)
# ventral gondola (Bola)
gctrl = [(0.0, 1.72, 0.10, 0.10, 0.10, 2.0), (0.08, 1.68, 0.36, 0.22, 0.28, 2.4), (0.25, 1.64, 0.42, 0.25, 0.33, 2.6),
         (0.70, 1.62, 0.42, 0.25, 0.33, 2.6), (0.92, 1.66, 0.36, 0.22, 0.25, 2.4), (1.0, 1.70, 0.30, 0.18, 0.18, 2.2)]
gprof = ctrl_at(gctrl, 2.35, -1.25)
def gmat(y, ang, z):
    zk = -y
    return 1 if (zk < -0.55 and 200 < ang < 340) or (zk > 1.95 and 210 < ang < 330) else 0
gond = fus(P + "_gond", 2.35, -1.25, gctrl, ["M_Bot", "M_Canopy"], G, mat_fn=gmat, n=20, seg=24)
barrel(P + "_mgff", (0, 1.55, 1.9), (0, 1.55, 2.55), 0.022, G, jacket=(0.035, 0.25))
# cockpit: pilot seat (left) + roof-hung panel, yoke on swing arm, bombardier pad, navigator seat, throttles
bomber_seat(P + "_ps", (0.32, 2.12, 2.75), G)
yoke(P + "_yk", (0.32, 1.80, 3.25), G)
abox(P + "_floor", (1.3, 2.6, 0.03), tuple(KV((0, 1.82, 2.6))), "M_Int", G, bevel=0.0)
abox(P + "_rpanel", (0.50, 0.04, 0.24), tuple(KV((0.06, 3.38, 3.25))), "M_Dash", G, rot=(math.radians(-35), 0, 0), bevel=0.01)
for k, (dx, dy) in enumerate(((-0.17, 0.06), (0.0, 0.06), (0.17, 0.06), (-0.09, -0.07), (0.09, -0.07))):
    dial("%s_dl%d" % (P, k), (0.06 + dx * 0.9, 3.38 + dy * 0.82, 3.23 + dy * 0.57), 0.04, G)
abox(P + "_thr", (0.12, 0.3, 0.12), tuple(KV((0.66, 2.20, 2.8))), "M_Dash", G, bevel=0.01)
abox(P + "_pad", (0.6, 1.2, 0.06), tuple(KV((-0.15, 1.90, 3.7))), "M_Leather", G, bevel=0.02)
bomber_seat(P + "_ns", (-0.38, 2.10, 2.35), G, armour=False)
abox(P + "_bsight", (0.12, 0.12, 0.3), tuple(KV((-0.15, 1.95, 4.05))), "M_Dark", G, bevel=0.01)
abox(P + "_bulk", (1.4, 0.04, 1.4), tuple(KV((0, 2.55, -0.25))), "M_Int", G, bevel=0.0)
# ------------------------------------------------------------------------------------------------ wings + nacelles
NX = 3.15
for sx, ga, gf in ((1, GAL, GFL), (-1, GAR, GFR)):
    sec, hg = wing("%s_wing" % P, sx, (sx * 0.70, 1.95, 1.55), 10.6, 4.95, 1.60, 1.10, 5.0, 0.17, 0.11, 0.70, G,
                   parts=[(0.27, 0.45, gf), (0.48, 0.93, ga)], hinge=0.76, nsec=14)
    globals()["HG%d" % sx] = hg
    tip = Vector(sec(0.995)["le"]) + Vector((0, 0.3, 0))
    lamp("%s_nav%d" % (P, sx), tuple(tip + Vector((sx * 0.03, 0, 0))), "M_NavRed" if sx < 0 else "M_NavGreen", G, r=0.04)
    nctrl = [(0.0, 2.30, 0.56, 0.56, 0.56, 2.0, sx * NX), (0.05, 2.30, 0.62, 0.62, 0.62, 2.0, sx * NX),
             (0.25, 2.28, 0.60, 0.60, 0.68, 2.1, sx * NX), (0.50, 2.26, 0.52, 0.55, 0.70, 2.3, sx * NX),
             (0.75, 2.24, 0.38, 0.40, 0.50, 2.3, sx * NX), (1.0, 2.22, 0.05, 0.06, 0.06, 2.0, sx * NX)]
    nac = fus("%s_nac%d" % (P, sx), 3.75, -3.3, nctrl, ["M_Top"], G, n=26, seg=28)
    paint(nac, zt=0.4)
    lathe_y("%s_rad%d" % (P, sx), [(0.0, 0.32), (0.0, 0.55), (0.03, 0.55), (0.03, 0.32)], tuple(KV((sx * NX, 2.30, 3.76))), ["M_Mesh"], G, seg=28)
    exhaust_row("%s_xo%d" % (P, sx), Vector((sx * (NX + 0.55), 2.42, 3.25)), 0.2, 6, G, sx, r=0.04, length=0.13)
    exhaust_row("%s_xi%d" % (P, sx), Vector((sx * (NX - 0.55), 2.42, 3.25)), 0.2, 6, G, -sx, r=0.04, length=0.13)
    abox("%s_scoop%d" % (P, sx), (0.24, 0.6, 0.16), tuple(KV((sx * NX, 1.66, 2.6))), "M_Bot", G, bevel=0.04)
    gp, gd = (GPL, GDL) if sx > 0 else (GPR, GDR)
    propeller("%s_pr%d" % (P, sx), (sx * NX, 2.30, 3.80), 1.75, 3, 0.30, gp, gd, 0.30, 0.45, spin_mat="M_Spin", pitch=(44, 18))
    grp = GL if sx > 0 else GR
    gear_leg("%s_mg%d" % (P, sx), (sx * NX, 1.75, 0.45), (sx * NX, 0.58, 0.25), 0.58, 0.40, grp, fork=True)
rod(P + "_pitot", Vector(sec(0.86)["le"]) + Vector((0, 0.3, -0.05)), Vector(sec(0.86)["le"]) + Vector((0, -0.4, -0.05)), 0.012, "M_Steel", G, seg=6)
# ------------------------------------------------------------------------------------------------ tail
for sx in (1, -1):
    wing("%s_hs" % P, sx, (sx * 0.10, 2.95, -9.55), 3.30, 1.95, 0.95, 0.35, 0.0, 0.11, 0.09, 0.55, G,
         parts=[(0.05, 0.97, GE)], hinge=0.62)
_, HF = fin(P, (0, 3.05, -9.45), 1.75, 2.10, 0.90, 0.75, 0.11, 0.08, 0.45, G, rud_grp=GRU, hinge=0.58)
TZ = -10.3
TY = abs(TZ) * math.tan(math.radians(TAIL)) + 0.22
gear_leg(P + "_tg", (0, 2.70, TZ + 0.1), (0, TY, TZ), 0.22, 0.12, GT, fork=True, torque=False)
rod(P + "_mast", KV((0, 3.55, 1.6)), KV((0, 4.0, 1.4)), 0.02, "M_Top", G, seg=6)
rod(P + "_wire", KV((0, 3.98, 1.4)), KV((0, 4.6, -10.0)), 0.004, "M_Black", G, seg=4)
# ------------------------------------------------------------------------------------------------ bomb bay (ESAC) + racks
for i in range(8):
    x = 0.33 if i % 2 == 0 else -0.33
    z = 0.45 - 0.62 * (i // 2)
    bomb_geo("%s_sc250_%d" % (P, i), (x, 2.48, z), 1.64, 0.368, P + "_b%d" % i, axis='Z', nose_up=True, ring=False)
    abox("%s_cell%d" % (P, i), (0.5, 0.5, 0.03), tuple(KV((x, 3.32, z))), "M_Int", G, bevel=0.0)
for i, x in enumerate((0.45, -0.45)):
    bomb_geo("%s_sc1000_%d" % (P, i), (x, 1.36, -0.5), 2.58, 0.654, P + "_x%d" % i, ring=False)
    abox("%s_pvc%d" % (P, i), (0.14, 1.0, 0.1), tuple(KV((x, 1.66, -0.5))), "M_Dark", G, bevel=0.02)
for sx, grp in ((1, GBL), (-1, GBR)):
    abox("%s_door%d" % (P, sx), (0.56, 2.55, 0.025), tuple(KV((sx * 0.33, 1.69, -0.48))), "M_Bot", grp, bevel=0.0)
# ------------------------------------------------------------------------------------------------ defensive positions
TUR = [((0.08, 2.52, 4.42), False, 0.12, 0.85), ((0, 3.50, -2.75), False, 0.0, 1.10), ((0, 1.45, -1.18), True, 0.0, 0.85)]
for i, (b, twin, ball, ln) in enumerate(TUR):
    turret_gun(P, i, b, P + "_ty%d" % i, P + "_tp%d" % i, ln, twin=twin, ball=ball, cal="hmg" if i == 1 else "mg")
abox(P + "_bscreen", (0.6, 0.04, 0.3), tuple(KV((0, 3.58, -2.2))), "M_Canopy", G, rot=(math.radians(-30), 0, 0), bevel=0.0, dirt=False)
# ------------------------------------------------------------------------------------------------ markings
for sx in (1, -1):
    wdecal("%s_bk%d" % (P, sx), fusel, (sx * 0.6, 2.75, -5.6), (sx, 0, 0), (0, 1, 0), 0.95, "cross", G)
    wdecal("%s_code%d" % (P, sx), fusel, (sx * 0.7, 2.70, -3.9), (sx, 0, 0), (0, 1, 0), 1.25, "lw_code_he", G)
w_obs = [o for o in col_get("BUILD").objects if o.name.startswith((P + "_wing", P + "_hs", P + "_fin"))]
for o in w_obs:
    paint(o, zt=0.3)
for sx in (1, -1):
    obs = [o for o in w_obs if o.name.startswith("%s_wing_w%d_" % (P, sx)) and not o.name.endswith("m")]
    t = obs
    c = (sx * 7.4, 2.75, 0.0)
    wdecal("%s_wct%d" % (P, sx), t, (sx * 6.3, 2.95, 0.15), (0, 1, 0), (0, 0, 1), 1.5, "cross", G)
    wdecal("%s_wcb%d" % (P, sx), t, (sx * 6.3, 2.30, 0.15), (0, -1, 0), (0, 0, 1), 1.5, "cross", G)
# ------------------------------------------------------------------------------------------------ rig + export
auto_attach([G], bracket_groups=())
weather(gen_weather([KV((s * NX + d, 2.42, 2.4)) for s in (1, -1) for d in (0.55, -0.55)], 1.2))
rt = root("root")
group_under(G, "he111_body", rt)
for nm, gp, gd, sx in (("prop_0", GPL, GDL, 1), ("prop_1", GPR, GDR, -1)):
    pv = pivot(nm, tuple(KV((sx * NX, 2.30, 3.80))), rt)
    group_under(gp, nm + "_geo", pv)
    group_under(gd, "prop_disc_" + nm[-1], rt)
for nm, grp, top in (("gear_L", GL, (NX, 1.75, 0.45)), ("gear_R", GR, (-NX, 1.75, 0.45)), ("gear_T", GT, (0, 2.70, TZ + 0.1))):
    pv = pivot(nm, tuple(KV(top)), rt)
    group_under(grp, nm + "_geo", pv)
def hinge_node(nm, grp, pts):
    a, b = pts
    pv = pivot(nm, tuple((a + b) / 2), rt)
    group_under(grp, nm + "_geo", pv)
hinge_node("ail_L", GAL, HG1[GAL]); hinge_node("ail_R", GAR, globals()["HG-1"][GAR])
hinge_node("flap_L", GFL, HG1[GFL]); hinge_node("flap_R", GFR, globals()["HG-1"][GFR])
ev = pivot("elev", tuple(KV((0, 2.95, -9.55 - 1.95 * 0.62))), rt)
group_under(GE, "elev_geo", ev)
hinge_node("rud", GRU, HF[GRU])
for nm, grp, sx in (("bay_L", GBL, 1), ("bay_R", GBR, -1)):
    pv = pivot(nm, tuple(KV((sx * 0.61, 1.69, -0.48))), rt)
    group_under(grp, nm + "_geo", pv)
for i in range(8):
    group_under(P + "_b%d" % i, "bomb_%d" % i, rt)
for i in range(2):
    group_under(P + "_x%d" % i, "bombx_%d" % i, rt)
for i, (b, *_r) in enumerate(TUR):
    ty = pivot("tur_%d_yaw" % i, tuple(KV(b)), rt)
    group_under(P + "_ty%d" % i, "tur_%d_ring" % i, ty)
    tp = pivot("tur_%d_pitch" % i, tuple(KV(b)), ty)
    group_under(P + "_tp%d" % i, "tur_%d_gun" % i, tp)
rw_export("/home/user/.cache/rwveh/raw/he111.glb", "/home/user/models/he111.blend", "HE111")
