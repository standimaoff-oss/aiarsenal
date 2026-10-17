# Messerschmitt Bf 109 G-6 (1943-44, DB 605A, Erla-Haube style canopy, "Beulen" MG 131 bulges, underwing radiators).
# Real dimensions: length 9.02 m, span 9.925 m, wing area 16.05 m2, track 2.06 m, prop VDM 9-12087 3.0 m.
# Kind frame (x left, y up, z fwd). Origin = ground under the main axles, airframe LEVEL; ground attitude 12 deg.
exec(open("/home/user/tools/rwveh/craft_common.py").read())
exec(open("/home/user/tools/rwveh/plane_lib.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
plane_mats("T_PlRLM7475", "T_PlRLM76M", "T_PlRLM76", "T_PlInteriorDE", frame=(0.30, 0.31, 0.33))
P = "BF"
G, GP, GD, GL, GR, GT, GAL, GAR, GE, GRU, GFL, GFR = (P + s for s in ("_body", "_prop", "_disc", "_gl", "_gr", "_gt", "_al", "_ar",
                                                                    "_el", "_ru", "_fl", "_fr"))
TAIL = 12.0
Z_NOSE, Z_TAIL = 2.25, -6.0
ctrl = [  # t (0 = cowl front z 2.25 .. 1 = z -6.0), yc, hw, ht, hb, n
    (0.000, 1.70, 0.36, 0.36, 0.40, 2.2),
    (0.055, 1.69, 0.44, 0.44, 0.52, 2.3),
    (0.152, 1.70, 0.47, 0.47, 0.60, 2.4),
    (0.248, 1.72, 0.47, 0.42, 0.62, 2.5),
    (0.345, 1.70, 0.46, 0.42, 0.62, 2.5),
    (0.467, 1.70, 0.42, 0.45, 0.58, 2.4),
    (0.588, 1.72, 0.34, 0.40, 0.48, 2.3),
    (0.709, 1.75, 0.26, 0.33, 0.38, 2.2),
    (0.830, 1.80, 0.18, 0.26, 0.26, 2.1),
    (0.927, 1.86, 0.11, 0.20, 0.16, 2.0),
    (1.000, 1.92, 0.04, 0.10, 0.05, 2.0)]
prof = ctrl_at(ctrl, Z_NOSE, Z_TAIL)

def fmat(y, ang, z):
    zk = -y
    if 0.6 < zk < 1.9 and ang > 200 and ang < 340 and z < 1.3:
        return 1                 # yellow cowling underside (Eastern-front theatre marking)
    return 0
fusel = fus(P + "_fus", Z_NOSE, Z_TAIL, ctrl, ["M_Top", "M_Yellow"], G, mat_fn=fmat, n=44, seg=36)
paint(fusel, zt=0.55)
inner_tub(P + "_tub", ctrl, Z_NOSE, Z_TAIL, 0.15, -1.62, 48, G)
# cockpit opening edge (sill coaming) + windscreen / hood (angular, flat panes -> squareness 4)
CZ0, CZ1, CZ2, CZ3 = 0.06, -0.40, -1.52, -1.78
def cprof(y):
    z = -y
    if z > CZ1:
        u = (CZ0 - z) / (CZ0 - CZ1); ht, hw = 0.03 + 0.25 * u, 0.40 - 0.03 * u
    elif z > CZ2:
        u = (CZ1 - z) / (CZ1 - CZ2); ht, hw = 0.28 + 0.01 * math.sin(u * PI), 0.37 - 0.03 * u
    else:
        u = (CZ2 - z) / (CZ2 - CZ3); ht, hw = 0.29 - 0.19 * u, 0.34 - 0.04 * u
    return (2.12, hw, ht, 0.05, 3.6)
cys = [-CZ0 + (CZ0 - CZ3) * k / 16 for k in range(17)]
loft(P + "_glass", cys, cprof, ["M_Canopy"], G, seg=12, angle_range=(0, 180), smooth=False, dirt=False)
cp = lambda z: (lambda r: (r[0], r[1], r[2], r[3], r[4]))(cprof(-z))
for i, z in enumerate((CZ0 - 0.005, CZ1, -0.98, CZ2, CZ3 + 0.01)):
    frame_ring("%s_cfr%d" % (P, i), cp, z, 0, 180, 0.016, G, off=0.006)
for i, a in enumerate((8, 42, 138, 172)):
    frame_line("%s_cfl%d" % (P, i), cp, CZ0, CZ3, a, 0.013, G, off=0.006)
abox(P + "_armglass", (0.28, 0.012, 0.22), tuple(KV((0, 2.32, -0.5))), "M_Canopy", G, bevel=0.0, dirt=False)  # 90 mm windscreen
abox(P + "_headarm", (0.36, 0.02, 0.24), tuple(KV((0, 2.28, -1.45))), "M_Steel", G, bevel=0.01)
cut_top(fusel, prof, CZ0, CZ2, 48)    # r12: open cockpit under the hood
fighter_cockpit(P + "_ck", (0.0, 2.30, -0.98), G, -0.30, 0.5, 1.30, nation="DE", coam=2.12)
# cowling details: MG 131 bulges, gun troughs, supercharger intake, oil cooler, exhausts, cowl seams
for sx in (1, -1):
    lathe_y("%s_beule%d" % (P, sx), [(0.0, 0.0), (0.0, 0.05), (0.18, 0.10), (0.42, 0.11), (0.62, 0.07), (0.72, 0.0)],
            tuple(KV((sx * 0.25, 2.06, 0.62))), ["M_Top"], G, seg=14, axis='Y')
    barrel("%s_mg131_%d" % (P, sx), (sx * 0.16, 2.10, 1.30), (sx * 0.16, 2.10, 1.98), 0.016, G)
    abox("%s_trough%d" % (P, sx), (0.06, 0.7, 0.03), tuple(KV((sx * 0.16, 2.12, 1.55))), "M_Dark", G, bevel=0.01)
    exhaust_row("%s_x%d" % (P, sx), Vector((sx * 0.45, 1.60, 1.55)), 0.17, 6, G, sx)
    abox("%s_exs%d" % (P, sx), (0.02, 1.1, 0.14), tuple(KV((sx * 0.455, 1.60, 1.12))), "M_Steel", G, bevel=0.01)   # flame shield
lathe_y(P + "_scoop", [(0.0, 0.0), (0.0, 0.085), (0.06, 0.1), (0.36, 0.09), (0.5, 0.04), (0.52, 0.0)], tuple(KV((0.52, 1.66, 1.0))),
        ["M_Side"], G, seg=14)
acyl(P + "_scoopm", 0.07, 0.02, tuple(KV((0.52, 1.66, 1.005))), "M_Dark", G, axis=1, seg=12, dirt=False)
abox(P + "_oil", (0.30, 0.62, 0.20), tuple(KV((0, 1.06, 1.15))), "M_Yellow", G, bevel=0.05)
abox(P + "_oilm", (0.24, 0.02, 0.13), tuple(KV((0, 1.06, 1.465))), "M_Mesh", G, bevel=0.0, dirt=False)
# ------------------------------------------------------------------------------------------------ wings
for sx, ga, gf in ((1, GAL, GFL), (-1, GAR, GFR)):
    sec, hg = wing("%s_wing" % P, sx, (sx * 0.40, 1.22, 0.55), 4.56, 2.05, 1.02, 0.30, 6.5, 0.15, 0.11, 0.80, G,
                   parts=[(0.02, 0.47, gf), (0.50, 0.92, ga)], hinge=0.74)
    globals()["HG%d" % sx] = hg
    S = sec(0.62)
    rod("%s_slat%d" % (P, sx), Vector(sec(0.46)["le"]) + Vector((0, -0.02, 0)), Vector(sec(0.86)["le"]) + Vector((0, -0.02, 0)), 0.035, "M_Top", G, seg=8)
    # underwing radiator bath + flap
    rp = Vector(sec(0.24)["le"]) + Vector((0, 0.55, -0.20))
    abox("%s_rad%d" % (P, sx), (0.48, 0.95, 0.17), tuple(rp), "M_Bot", G, bevel=0.04)
    abox("%s_radm%d" % (P, sx), (0.42, 0.02, 0.12), tuple(rp + Vector((0, -0.48, 0))), "M_Mesh", G, bevel=0.0, dirt=False)
    abox("%s_radf%d" % (P, sx), (0.44, 0.22, 0.02), tuple(rp + Vector((0, 0.56, 0.05))), "M_Bot", G, rot=(math.radians(-12), 0, 0), bevel=0.0)
    tip = Vector(sec(0.995)["le"]) + Vector((0, 0.14, 0))
    lamp("%s_nav%d" % (P, sx), tuple(tip + Vector((sx * 0.03, 0, 0))), "M_NavRed" if sx < 0 else "M_NavGreen", G, r=0.03)
rod(P + "_pitot", Vector(sec(0.88)["le"]) + Vector((0, 0.2, -0.06)), Vector(sec(0.88)["le"]) + Vector((0, -0.35, -0.06)), 0.01, "M_Steel", G, seg=6)
# ------------------------------------------------------------------------------------------------ tail
for sx in (1, -1):
    wing("%s_hs" % P, sx, (sx * 0.08, 2.20, -4.95), 1.42, 0.98, 0.55, 0.12, 0.0, 0.11, 0.09, 0.62, G,
         parts=[(0.06, 0.97, GE)], hinge=0.64)
_, HF = fin(P, (0, 2.0, -4.85), 1.02, 1.30, 0.55, 0.28, 0.11, 0.08, 0.5, G, rud_grp=GRU, hinge=0.60)
lamp(P + "_tail", tuple(KV((0, 2.05, -6.02))), "M_Strobe", G, r=0.025)
# antenna mast + wire, D/F loop
rod(P + "_mast", KV((0, 2.20, -2.25)), KV((0, 2.62, -2.30)), 0.018, "M_Top", G, seg=6)
rod(P + "_wire", KV((0, 2.60, -2.30)), KV((0, 2.95, -5.55)), 0.003, "M_Black", G, seg=4)
lathe_y(P + "_df", [(-0.01, 0.0), (-0.01, 0.12), (0.01, 0.12), (0.01, 0.0)], tuple(KV((0, 2.22, -2.85))), ["M_Dark"], G, seg=12, axis='Y')
# ------------------------------------------------------------------------------------------------ propeller
propeller(P, (0, 1.70, 2.25), 1.50, 3, 0.26, GP, GD, 0.28, 0.52, spin_mat="M_Spin", pitch=(48, 18))
acyl(P + "_canmz", 0.03, 0.03, tuple(KV((0, 1.70, 2.76))), "M_Bore", GP, axis=1, seg=8, dirt=False)   # MG 151/20 muzzle
# ------------------------------------------------------------------------------------------------ landing gear
for sx, grp in ((1, GL), (-1, GR)):
    gear_leg("%s_mg%d" % (P, sx), (sx * 0.40, 1.22, 0.35), (sx * 0.86, 0.33, 0.40), 0.33, 0.15, grp, door=(0.42, 0.42))
TZ = -5.62
TY = abs(TZ) * math.tan(math.radians(TAIL)) + 0.16
gear_leg(P + "_tg", (0, 1.70, TZ + 0.12), (0, TY, TZ), 0.16, 0.08, GT, fork=True, torque=False)
# ------------------------------------------------------------------------------------------------ markings
for sx in (1, -1):
    wdecal("%s_bk%d" % (P, sx), fusel, (sx * 0.4, 1.76, -3.0), (sx, 0, 0), (0, 1, 0), 0.56, "cross", G)
    wdecal("%s_n7%d" % (P, sx), fusel, (sx * 0.45, 1.74, -2.15), (sx, 0, 0), (0, 1, 0), 0.30, "lw_7", G)
    wdecal("%s_band%d" % (P, sx), fusel, (sx * 0.24, 1.80, -4.15), (sx, 0, 0), (0, 0, 1), 0.44, "lw_band", G, h=0.30)
w_obs = [o for o in col_get("BUILD").objects if o.name.startswith(P + "_wing")]
for o in w_obs:
    paint(o, zt=0.3)
for o in col_get("BUILD").objects:
    if o.name.startswith((P + "_hs", P + "_fin")):
        paint(o, zt=0.3)
wingL = [o for o in w_obs if o.name.startswith(P + "_wing_w1_") and not o.name.endswith("m")]
wingR = [o for o in w_obs if o.name.startswith(P + "_wing_w-1_") and not o.name.endswith("m")]
import functools
def _big(obs):
    return max(obs, key=lambda o: len(o.data.polygons))
for sx, obs in ((1, wingL), (-1, wingR)):
    t = obs
    wdecal("%s_wct%d" % (P, sx), t, (sx * 3.4, 1.62, -0.25), (0, 1, 0), (0, 0, 1), 0.85, "cross_late", G)
    wdecal("%s_wcb%d" % (P, sx), t, (sx * 3.4, 1.42, -0.25), (0, -1, 0), (0, 0, 1), 0.85, "cross", G)

# ------------------------------------------------------------------------------------------------ rig + export
auto_attach([G], bracket_groups=())
weather(gen_weather([KV((s * 0.5, 1.6, 0.9)) for s in (1, -1)], 0.7))
rt = root("root")
group_under(G, "bf109_body", rt)
pp = pivot("prop", tuple(KV((0, 1.70, 2.25))), rt)
group_under(GP, "prop_geo", pp)
group_under(GD, "prop_disc", rt)
for nm, grp, top in (("gear_L", GL, (0.42, 1.08, 0.35)), ("gear_R", GR, (-0.42, 1.08, 0.35)), ("gear_T", GT, (0, 1.70, TZ + 0.12))):
    pv = pivot(nm, tuple(KV(top)), rt)
    group_under(grp, nm + "_geo", pv)
def hinge_node(nm, grp, pts):
    a, b = pts
    pv = pivot(nm, tuple((a + b) / 2), rt)
    group_under(grp, nm + "_geo", pv)
hinge_node("ail_L", GAL, HG1[GAL]); hinge_node("ail_R", GAR, globals()["HG-1"][GAR])
hinge_node("flap_L", GFL, HG1[GFL]); hinge_node("flap_R", GFR, globals()["HG-1"][GFR])
ev = pivot("elev", tuple(KV((0, 2.20, -4.95 - 0.98 * 0.64))), rt)
group_under(GE, "elev_geo", ev)
hinge_node("rud", GRU, HF[GRU])
rw_export("/home/user/.cache/rwveh/raw/bf109.glb", "/home/user/models/bf109.blend", "BF109")
