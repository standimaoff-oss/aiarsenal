# Patrol boat pr. 03160 "Raptor": 16.9 m aluminium planing hull, armoured wheelhouse / troop cabin, bow landing ramp,
# 14.5 mm KPVT remote weapon station on the cabin roof, 2 surface-piercing drives with rudders.
# CraftRenderer nodes: turret_yaw > turret_pitch, prop_L / prop_R (about glTF Z), rudder_L / rudder_R (about Y), radar.
# Kind: L 16.9, B 4.0, draft 0.9, hullH 2.3; muzzle K(0, 3.25, 5.0); seats y 2.05 (z 0.9) / 1.35 (z -2.6, -4.1).
exec(open("/home/user/tools/rwveh/craft_common.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
craft_mats()
amat("M_Navy", None, 0.6, 0.2, factor=(0.42, 0.45, 0.47, 1))
amat("M_NavyDark", None, 0.6, 0.2, factor=(0.22, 0.24, 0.25, 1))
amat("M_Antifoul", None, 0.7, 0.0, factor=(0.35, 0.07, 0.06, 1))
amat("M_Deck", None, 0.85, 0.0, factor=(0.2, 0.21, 0.2, 1))
P = "RP"
G, GTY, GTP, GPL, GPR, GRL, GRR, GRD = (P + s for s in ("_body", "_ty", "_tp", "_pl", "_pr", "_rl", "_rr", "_rad"))
M, MD = "M_Navy", "M_NavyDark"
LEN, BEAM = 16.9, 4.0
YB = -LEN / 2          # bow (Blender y)

# ------------------------------------------------------------------------------------------------ hull
def hull_at(t):
    """t 0 bow .. 1 transom -> beam, keel z, sheer z, deadrise"""
    tab = [(0.0, 0.10, 1.75, 2.35, 30), (0.04, 1.30, 0.95, 2.30, 28), (0.11, 2.70, 0.45, 2.10, 25), (0.22, 3.60, 0.16, 1.80, 22),
           (0.35, 3.98, 0.04, 1.50, 19), (0.6, 4.0, 0.0, 1.32, 16), (1.0, 3.86, 0.0, 1.30, 14)]
    for (t0, *a), (t1, *b) in zip(tab, tab[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0)
            f = f * f * (3 - 2 * f)
            return [x + (y - x) * f for x, y in zip(a, b)]
    return tab[-1][1:]
rings = []
for k in range(29):
    t = (k / 28) ** 1.25
    bw, kz, sz, dr = hull_at(t)
    rings.append(hull_section(YB + t * LEN, LEN, bw, sz - kz, dr, 0.78, sz, keel_z=kz))
hull = loft_rings(P + "_hull", rings, [M], G, cap0=True, cap1=True, smooth=False)
# antifouling band: separate shell slightly outside, lower part only (boot-topping to the waterline at ~0.9)
bt = []
for k in range(29):
    t = (k / 28) ** 1.25
    bw, kz, sz, dr = hull_at(t)
    ring = hull_section(YB + t * LEN, LEN, bw * 1.004 + 0.01, sz - kz, dr, 0.78, min(sz, 0.88), keel_z=kz - 0.01)
    bt.append(ring)
loft_rings(P + "_af", bt[3:], ["M_Antifoul"], G, cap0=False, cap1=True, smooth=False)
# spray rails / chine strakes, rub rail, deck
for sx in (1, -1):
    pts = []
    for k in range(3, 29):
        t = (k / 28) ** 1.25
        bw, kz, sz, dr = hull_at(t)
        pts.append(Vector((sx * (bw / 2 + 0.03), YB + t * LEN, sz - 0.05)))
    aobj("%s_rub%d" % (P, sx), bm_tube_path(pts, 0.07, 6), ["M_Rubber2"], G, smooth=True, dirt=False)
abox(P + "_deck", (3.6, 9.5, 0.04), (0, 3.3, 1.33), "M_Deck", G, bevel=0.0)
# ------------------------------------------------------------------------------------------------ cabin (open glazing so the crew can see out)
CY0, CY1 = -4.4, 4.8       # front / rear of the cabin (Blender y)
Z0, ZW, ZR = 1.32, 2.15, 3.05
CW = 3.1
for sx in (1, -1):
    abox("%s_cwall%d" % (P, sx), (0.08, CY1 - CY0 - 0.6, ZW - Z0), (sx * CW / 2, (CY0 + CY1) / 2 + 0.3, (Z0 + ZW) / 2), M, G, bevel=0.0)
    for j, y in enumerate([CY0 + 0.6 + i * (CY1 - CY0 - 0.6) / 6 for i in range(7)]):
        abox("%s_pil%d_%d" % (P, sx, j), (0.1, 0.12, ZR - ZW), (sx * CW / 2, y, (ZW + ZR) / 2), M, G, bevel=0.0)
    abox("%s_cglass%d" % (P, sx), (0.03, CY1 - CY0 - 0.6, ZR - ZW - 0.02), (sx * CW / 2, (CY0 + CY1) / 2 + 0.3, (ZW + ZR) / 2), "M_Glass", G, bevel=0.0, dirt=False)
    # front quarter (tapered nose of the cabin)
    tilted_box("%s_cq%d" % (P, sx), Vector((sx * CW / 2, CY0 + 0.6, (Z0 + ZW) / 2)), Vector((sx * 1.1, CY0, (Z0 + ZW) / 2)), ZW - Z0, 0.08, M, G)
abox(P + "_cfront", (2.2, 0.08, ZW - Z0), (0, CY0, (Z0 + ZW) / 2), M, G, bevel=0.0)
abox(P + "_cback", (CW, 0.08, ZR - Z0), (0, CY1, (Z0 + ZR) / 2), M, G, bevel=0.0)
abox(P + "_cdoor", (1.0, 0.1, 1.6), (0.6, CY1 + 0.02, 2.15), MD, G, bevel=0.0)
# raked windscreen (glass) + frames
ws0, ws1 = Vector((0, CY0, ZW)), Vector((0, CY0 + 0.55, ZR))
d = ws1 - ws0
abox(P + "_wscr", (2.2, 0.03, d.length), tuple((ws0 + ws1) / 2), "M_Glass", G, rot=(math.atan2(d.y, d.z), 0, 0), bevel=0.0, dirt=False)
for x in (-1.1, -0.37, 0.37, 1.1):
    rod("%s_wsf%d" % (P, int(x * 10)), ws0 + Vector((x, 0, 0)), ws1 + Vector((x, 0, 0)), 0.04, M, G, seg=6)
abox(P + "_roof", (CW + 0.15, CY1 - CY0 - 0.5, 0.1), (0, (CY0 + CY1) / 2 + 0.25, ZR + 0.05), M, G, bevel=0.0)
abox(P + "_visor", (2.3, 0.4, 0.06), (0, CY0 + 0.45, ZR + 0.05), M, G, bevel=0.0)
# interior: helm console, 2 helm seats, troop benches
abox(P + "_helm", (2.4, 0.5, 0.9), (0, CY0 + 0.6, 1.75), "M_Dash", G, bevel=0.03)
for sx in (1, -1):
    abox("%s_mfd%d" % (P, sx), (0.4, 0.03, 0.28), (sx * 0.55, CY0 + 0.84, 2.25), "M_Screen", G, rot=(math.radians(-20), 0, 0), bevel=0.0, dirt=False)
    abox("%s_hseat%d" % (P, sx), (0.5, 0.5, 0.15), (sx * 0.55, -0.9, 1.98), "M_Seat", G, bevel=0.02)
    abox("%s_hseatb%d" % (P, sx), (0.5, 0.12, 0.7), (sx * 0.55, -0.6, 2.4), "M_Seat", G, bevel=0.02)
    rod("%s_hped%d" % (P, sx), Vector((sx * 0.55, -0.9, 1.33)), Vector((sx * 0.55, -0.9, 1.9)), 0.06, "M_Steel", G, seg=8)
    abox("%s_bench%d" % (P, sx), (0.55, 3.2, 0.12), (sx * 0.75, 3.3, 1.3), "M_Seat", G, bevel=0.02)
    abox("%s_benchb%d" % (P, sx), (0.1, 3.2, 0.6), (sx * 1.38, 3.3, 1.65), "M_Seat", G, bevel=0.02)
rod(P + "_wheel", Vector((0.55, CY0 + 0.95, 2.1)), Vector((0.55, CY0 + 1.0, 2.15)), 0.18, "M_Black", G, seg=12)
# mast with radar, antennas, nav lights, searchlight
rod(P + "_mast", Vector((0, 3.6, ZR + 0.1)), Vector((0, 3.4, ZR + 1.5)), 0.07, "M_Steel", G, seg=8)
abox(P + "_mastx", (1.4, 0.08, 0.08), (0, 3.45, ZR + 1.2), "M_Steel", G, bevel=0.0)
for sx in (1, -1):
    lamp("%s_nav%d" % (P, sx), (sx * 0.7, 3.45, ZR + 1.27), "M_NavRed" if sx > 0 else "M_NavGreen", G, r=0.05)
    rod("%s_whip%d" % (P, sx), Vector((sx * 1.3, 4.5, ZR + 0.1)), Vector((sx * 1.4, 4.7, ZR + 2.4)), 0.015, "M_Black", G, seg=5)
acyl(P + "_radb", 0.12, 0.1, (0, 3.4, ZR + 1.55), MD, G, seg=10)
abox(P + "_radar_a", (1.1, 0.12, 0.14), (0, 3.4, ZR + 1.68), "M_White", GRD, bevel=0.03)
acyl(P + "_slight", 0.12, 0.2, (0.9, CY0 + 0.9, ZR + 0.22), "M_Steel", G, axis=1, seg=12)
acyl(P + "_slightg", 0.1, 0.02, (0.9, CY0 + 0.79, ZR + 0.22), "M_HeadLamp", G, axis=1, seg=12, dirt=False)
# bow ramp, bollards, rails, life rafts, 7.62 mm pintle mounts aft
abox(P + "_ramp", (1.6, 0.1, 1.0), (0, YB + 0.75, 1.95), MD, G, rot=(math.radians(-50), 0, 0), bevel=0.0)
for sx in (1, -1):
    for j, y in enumerate((-6.0, 6.8)):
        acyl("%s_bol%d_%d" % (P, sx, j), 0.07, 0.25, (sx * 1.4, y, 1.45 if y > 0 else 1.95), "M_Steel", G, seg=8)
    rail = [Vector((sx * 1.85, y, 2.1 if y < -5 else 1.95)) for y in (-6.5, -5.5)] + [Vector((sx * 1.88, y, 2.0)) for y in (5.2, 8.0)]
    for a, b in zip(rail[:-1], rail[1:]):
        rod("%s_rl%d_%d" % (P, sx, int(a.y)), a, b, 0.02, "M_Steel", G, seg=5)
    acyl("%s_raft%d" % (P, sx), 0.25, 0.9, (sx * 1.2, 6.3, 1.6), "M_White", G, axis=1, seg=12)
    rod("%s_pkm%d" % (P, sx), Vector((sx * 1.6, 7.3, 1.33)), Vector((sx * 1.6, 7.3, 2.25)), 0.04, "M_Steel", G, seg=6)
    rod("%s_pkb%d" % (P, sx), Vector((sx * 1.6, 7.5, 2.3)), Vector((sx * 1.6, 6.5, 2.32)), 0.02, "M_Black", G, seg=6)
lamp(P + "_stern", (0, 8.48, 1.4), "M_Strobe", G, r=0.05)
for sx in (1, -1):
    decal_on("%s_flag%d" % (P, sx), hull, (sx * 1.98, -2.0, 1.65), (sx, 0, 0), (0, 0, 1), 0.6, "flag_ru", G)
# ------------------------------------------------------------------------------------------------ KPVT remote weapon station
TY = Vector((0, -3.6, ZR + 0.1))
acyl(P + "_tring", 0.45, 0.1, tuple(TY), MD, G, seg=18)
abox(P + "_tbase", (0.75, 0.75, 0.3), tuple(TY + Vector((0, 0.1, 0.22))), M, GTY, bevel=0.06)
abox(P + "_tsight", (0.22, 0.3, 0.22), tuple(TY + Vector((0.42, -0.1, 0.32))), MD, GTY, bevel=0.03)
abox(P + "_tsightw", (0.16, 0.02, 0.14), tuple(TY + Vector((0.42, -0.26, 0.32))), "M_Lens", GTY, bevel=0.0, dirt=False)
TP = Vector((0, -3.6, 3.25))
abox(P + "_gcrad", (0.3, 0.9, 0.24), tuple(TP + Vector((0, 0.05, 0))), M, GTP, bevel=0.04)
abox(P + "_gbox", (0.3, 0.4, 0.3), tuple(TP + Vector((-0.3, 0.1, -0.02))), "M_Olive", GTP, bevel=0.03)
acyl(P + "_gjack", 0.05, 0.5, tuple(TP + Vector((0, -0.6, 0))), "M_Steel", GTP, axis=1, seg=10)
tube(P + "_gbar", tuple(TP + Vector((0, -0.6, 0))), tuple(TP + Vector((0, -1.38, 0))), 0.028, "M_Steel", GTP, seg=10)
lathe_y(P + "_gmuz", [(-0.06, 0.0), (-0.06, 0.045), (0.05, 0.045), (0.05, 0.0)], tuple(TP + Vector((0, -1.42, 0))), ["M_Dark"], GTP, seg=10)
# ------------------------------------------------------------------------------------------------ surface drives + rudders
for sx, gp, gr in ((1, GPL, GRL), (-1, GPR, GRR)):
    c = Vector((sx * 0.85, 8.95, 0.32))
    rod("%s_shaft%d" % (P, sx), Vector((sx * 0.85, 8.2, 0.5)), c, 0.07, "M_Steel", G, seg=10)
    abox("%s_strut%d" % (P, sx), (0.08, 0.5, 0.4), (sx * 0.85, 8.6, 0.55), "M_Steel", G, bevel=0.0)
    acyl("%s_phub%d" % (P, sx), 0.09, 0.22, tuple(c), "M_Brass", gp, axis=1, seg=10)
    prop_blades("%s_pb%d" % (P, sx), tuple(c), 0.42, 5, ["M_Brass"], gp, axis=(0, 1, 0), chord=0.16, r_hub=0.08, pitch_root=42, pitch_tip=22)
    abox("%s_rudder%d" % (P, sx), (0.04, 0.45, 0.95), (sx * 0.85, 9.3, 0.62), "M_Steel", gr, bevel=0.0)
    acyl("%s_rstock%d" % (P, sx), 0.03, 0.4, (sx * 0.85, 9.25, 1.05), "M_Steel", G, seg=6)
    abox("%s_trim%d" % (P, sx), (0.9, 0.25, 0.05), (sx * 1.2, 8.55, 0.1), "M_Steel", G, bevel=0.0)

# ------------------------------------------------------------------------------------------------ rig + export
auto_attach([G, GTY, GTP], bracket_groups=())
weather(gen_weather([(0, 8.4, 1.2)], 0.6))
rt = root("root")
group_under(G, "raptor_body", rt)
ty = pivot("turret_yaw", tuple(TY), rt)
group_under(GTY, "turret_yaw_geo", ty)
tp = pivot("turret_pitch", tuple(TP), ty)
group_under(GTP, "turret_pitch_geo", tp)
for sx, gp, gr, s in ((1, GPL, GRL, "L"), (-1, GPR, GRR, "R")):
    pp = pivot("prop_" + s, (sx * 0.85, 8.95, 0.32), rt)
    group_under(gp, "prop_%s_geo" % s, pp)
    pr = pivot("rudder_" + s, (sx * 0.85, 9.2, 0.55), rt)
    group_under(gr, "rudder_%s_geo" % s, pr)
pd = pivot("radar", (0, 3.4, ZR + 1.68), rt)
group_under(GRD, "radar_geo", pd)
rw_export("/home/user/.cache/rwveh/raw/raptor.glb", "/home/user/models/raptor.blend", "RAPTOR")
