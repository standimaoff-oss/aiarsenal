# CB90H fast assault craft (Sweden): 15.9 m deep-V aluminium hull, bow ramp, armoured troop compartment, raised wheelhouse,
# ring-mounted 12.7 mm M2 with shield, 2 KaMeWa waterjets with steering buckets.
# CraftRenderer nodes: turret_yaw > turret_pitch, jet_L / jet_R (about Y).
# Kind: L 15.9, B 3.8; muzzle K(0, 3.70, 0.55); seats driver K(.55, 2.25, -2.3), gunner K(0, 3.10, -0.75), troops y 1.20.
exec(open("/home/user/tools/rwveh/craft_common.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
craft_mats()
amat("M_SwGrey", None, 0.65, 0.1, factor=(0.33, 0.37, 0.33, 1))
amat("M_Antifoul", None, 0.7, 0.0, factor=(0.12, 0.12, 0.12, 1))
amat("M_Deck", None, 0.85, 0.0, factor=(0.16, 0.17, 0.15, 1))
P = "CB"
G, GTY, GTP, GJL, GJR = (P + s for s in ("_body", "_ty", "_tp", "_jl", "_jr"))
M, MS = "M_Body", "M_SwGrey"
LEN, BEAM = 15.9, 3.8
YB = -LEN / 2

def hull_at(t):
    tab = [(0.0, 0.10, 1.9, 2.25, 34), (0.05, 1.5, 1.0, 2.15, 30), (0.13, 2.9, 0.42, 1.9, 26), (0.25, 3.6, 0.12, 1.6, 22),
           (0.4, 3.8, 0.02, 1.35, 20), (0.7, 3.8, 0.0, 1.25, 18), (1.0, 3.7, 0.0, 1.25, 16)]
    for (t0, *a), (t1, *b) in zip(tab, tab[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0)
            f = f * f * (3 - 2 * f)
            return [x + (y - x) * f for x, y in zip(a, b)]
    return tab[-1][1:]
rings, bt = [], []
for k in range(29):
    t = (k / 28) ** 1.25
    bw, kz, sz, dr = hull_at(t)
    rings.append(hull_section(YB + t * LEN, LEN, bw, sz - kz, dr, 0.8, sz, keel_z=kz))
    bt.append(hull_section(YB + t * LEN, LEN, bw * 1.004 + 0.01, sz - kz, dr, 0.8, min(sz, 0.78), keel_z=kz - 0.01))
hull = loft_rings(P + "_hull", rings, [MS], G, smooth=False)
loft_rings(P + "_af", bt[3:], ["M_Antifoul"], G, cap0=False, cap1=True, smooth=False)
for sx in (1, -1):
    pts = []
    for k in range(2, 29):
        t = (k / 28) ** 1.25
        bw, kz, sz, dr = hull_at(t)
        pts.append(Vector((sx * (bw / 2 + 0.04), YB + t * LEN, sz - 0.06)))
    aobj("%s_rub%d" % (P, sx), bm_tube_path(pts, 0.09, 6), ["M_Rubber2"], G, smooth=True, dirt=False)
abox(P + "_deck", (3.4, 4.0, 0.04), (0, 6.0, 1.27), "M_Deck", G, bevel=0.0)
abox(P + "_fdeck", (2.6, 2.6, 0.04), (0, -5.3, 1.75), "M_Deck", G, rot=(math.radians(-10), 0, 0), bevel=0.0)
# bow ramp (folded up), ramp hinges, bow fender
abox(P + "_ramp", (1.7, 0.12, 1.3), (0, -6.75, 2.45), M, G, rot=(math.radians(-25), 0, 0), bevel=0.0)
for sx in (1, -1):
    acyl("%s_rhinge%d" % (P, sx), 0.07, 0.3, (sx * 0.7, -6.5, 1.9), "M_Steel", G, axis=0, seg=8)
    rod("%s_rcab%d" % (P, sx), Vector((sx * 0.8, -6.9, 3.0)), Vector((sx * 0.9, -4.3, 2.55)), 0.015, "M_Steel", G, seg=4)
# ------------------------------------------------------------------------------------------------ troop compartment (armoured, low)
TY0, TY1, TZ = -4.3, 1.1, 2.5
fuselage(P + "_troop", TY0, TY1 - TY0, [(0, 1.85, 1.2, 0.2, 0.6, 3.5), (0.08, 1.88, 1.6, 0.62, 0.6, 5), (0.5, 1.88, 1.66, 0.62, 0.6, 6),
                                       (1.0, 1.88, 1.66, 0.62, 0.6, 6)], [M], G, n=10, seg=28, cap1=False)
for sx in (1, -1):
    for j in range(4):
        abox("%s_tw%d_%d" % (P, sx, j), (0.03, 0.42, 0.22), (sx * 1.665, -3.2 + j * 1.15, 2.1), "M_Glass", G, bevel=0.0, dirt=False)
    abox("%s_tdoor%d" % (P, sx), (0.06, 0.8, 0.9), (sx * 1.67, 0.2, 1.75), "M_Rubber2", G, bevel=0.0)
for sx in (1, -1):
    for j in range(4):
        abox("%s_tseat%d_%d" % (P, sx, j), (0.48, 0.45, 0.1), (sx * 0.7, -3.4 + j * 0.9, 1.22), "M_Seat", G, bevel=0.02)
        abox("%s_tseatb%d_%d" % (P, sx, j), (0.08, 0.45, 0.6), (sx * 1.0, -3.4 + j * 0.9, 1.55), "M_Seat", G, bevel=0.0)
abox(P + "_tfloor", (3.1, 5.2, 0.03), (0, -1.6, 1.27), "M_Deck", G, bevel=0.0)
abox(P + "_hatch", (0.8, 0.8, 0.06), (0, -3.0, TZ + 0.03), "M_SwGrey", G, bevel=0.0)
# ------------------------------------------------------------------------------------------------ gunner ring between troop roof and wheelhouse
TYc = Vector((0, 0.75, TZ))
acyl(P + "_ringb", 0.62, 0.45, tuple(TYc + Vector((0, 0, 0.2))), M, G, seg=24)
acyl(P + "_ring", 0.66, 0.08, tuple(TYc + Vector((0, 0, 0.45))), "M_Steel", GTY, seg=24)
abox(P + "_gseat", (0.4, 0.4, 0.08), (0, 0.85, 3.06), "M_Seat", GTY, bevel=0.02)
rod(P + "_gpost", Vector((0, 0.85, 2.5)), Vector((0, 0.85, 3.02)), 0.04, "M_Steel", GTY, seg=6)
rod(P + "_ped", Vector((0, 0.2, 2.95)), Vector((0, 0.2, 3.6)), 0.05, "M_Steel", GTY, seg=8)
abox(P + "_shield", (1.0, 0.05, 0.6), (0, -0.05, 3.55), MS, GTY, rot=(math.radians(-8), 0, 0), bevel=0.0)
for sx in (1, -1):
    abox("%s_shs%d" % (P, sx), (0.05, 0.45, 0.5), (sx * 0.52, 0.12, 3.5), MS, GTY, rot=(0, 0, math.radians(sx * 20)), bevel=0.0)
TP = Vector((0, 0.2, 3.7))
abox(P + "_m2r", (0.18, 0.75, 0.2), tuple(TP + Vector((0, 0.15, 0))), "M_Dark", GTP, bevel=0.02)
abox(P + "_m2box", (0.12, 0.28, 0.2), tuple(TP + Vector((-0.17, 0.2, -0.05))), "M_Olive", GTP, bevel=0.0)
rod(P + "_m2grip", TP + Vector((0, 0.53, 0)), TP + Vector((0, 0.62, -0.08)), 0.03, "M_Black", GTP, seg=6)
tube(P + "_m2bar", tuple(TP + Vector((0, -0.2, 0))), tuple(TP + Vector((0, -0.72, 0))), 0.024, "M_Steel", GTP, seg=10)
lathe_y(P + "_m2muz", [(-0.04, 0.0), (-0.04, 0.035), (0.04, 0.035), (0.04, 0.0)], tuple(TP + Vector((0, -0.75, 0))), ["M_Dark"], GTP, seg=8)
acyl(P + "_m2jack", 0.04, 0.3, tuple(TP + Vector((0, -0.2, 0))), "M_Dark", GTP, axis=1, seg=10)
# ------------------------------------------------------------------------------------------------ wheelhouse (raised, glazed)
WY0, WY1, WZ0, WZW, WZR = 1.25, 4.4, 1.27, 2.55, 3.35
for sx in (1, -1):
    abox("%s_wwall%d" % (P, sx), (0.08, WY1 - WY0, WZW - WZ0), (sx * 1.55, (WY0 + WY1) / 2, (WZ0 + WZW) / 2), M, G, bevel=0.0)
    for j, y in enumerate((WY0 + 0.05, WY0 + 1.1, WY0 + 2.1, WY1 - 0.05)):
        abox("%s_wpil%d_%d" % (P, sx, j), (0.1, 0.1, WZR - WZW), (sx * 1.5, y, (WZW + WZR) / 2), M, G, rot=(0, math.radians(sx * 6), 0), bevel=0.0)
    abox("%s_wglass%d" % (P, sx), (0.03, WY1 - WY0, WZR - WZW), (sx * 1.5, (WY0 + WY1) / 2, (WZW + WZR) / 2), "M_Glass", G, rot=(0, math.radians(sx * 6), 0), bevel=0.0, dirt=False)
abox(P + "_wfront", (3.1, 0.08, WZW - TZ + 0.0), (0, WY0, (WZW + TZ) / 2), M, G, bevel=0.0)
ws0, ws1 = Vector((0, WY0, WZW)), Vector((0, WY0 + 0.35, WZR))
d = ws1 - ws0
abox(P + "_wscr", (2.9, 0.03, d.length), tuple((ws0 + ws1) / 2), "M_Glass", G, rot=(math.atan2(d.y, d.z), 0, 0), bevel=0.0, dirt=False)
for x in (-1.0, 0.0, 1.0):
    rod("%s_wsf%d" % (P, int(x * 10)), ws0 + Vector((x, 0, 0)), ws1 + Vector((x, 0, 0)), 0.04, M, G, seg=6)
abox(P + "_wback", (3.1, 0.08, WZR - WZ0), (0, WY1, (WZ0 + WZR) / 2), M, G, bevel=0.0)
abox(P + "_wroof", (3.15, WY1 - WY0 - 0.25, 0.1), (0, (WY0 + WY1) / 2 + 0.12, WZR + 0.05), M, G, bevel=0.0)
abox(P + "_wfloor", (3.0, WY1 - WY0, 0.5), (0, (WY0 + WY1) / 2, 1.5), "M_Deck", G, bevel=0.0)
abox(P + "_console", (2.6, 0.4, 0.55), (0, WY0 + 0.3, 2.05), "M_Dash", G, bevel=0.03)
for sx in (1, -1):
    abox("%s_mfd%d" % (P, sx), (0.38, 0.03, 0.26), (sx * 0.55, WY0 + 0.5, 2.42), "M_Screen", G, rot=(math.radians(-25), 0, 0), bevel=0.0, dirt=False)
    abox("%s_dseat%d" % (P, sx), (0.5, 0.5, 0.14), (sx * 0.55, 2.3, 2.18), "M_Seat", G, bevel=0.02)
    abox("%s_dseatb%d" % (P, sx), (0.5, 0.12, 0.7), (sx * 0.55, 2.6, 2.55), "M_Seat", G, bevel=0.02)
rod(P + "_stick", Vector((0.55, 1.75, 2.3)), Vector((0.55, 1.78, 2.45)), 0.14, "M_Black", G, seg=12)
# mast, radar, lights, antennas, life raft canisters, aft deck gear
rod(P + "_mast", Vector((0, 3.8, WZR + 0.1)), Vector((0, 3.8, WZR + 1.4)), 0.06, "M_Steel", G, seg=8)
abox(P + "_radar", (0.9, 0.12, 0.14), (0, 3.8, WZR + 1.45), "M_White", G, bevel=0.03)
abox(P + "_mastx", (1.2, 0.07, 0.07), (0, 3.8, WZR + 1.1), "M_Steel", G, bevel=0.0)
for sx in (1, -1):
    lamp("%s_nav%d" % (P, sx), (sx * 0.6, 3.8, WZR + 1.17), "M_NavRed" if sx > 0 else "M_NavGreen", G, r=0.05)
    rod("%s_whip%d" % (P, sx), Vector((sx * 1.3, 4.2, WZR + 0.1)), Vector((sx * 1.35, 4.4, WZR + 2.3)), 0.015, "M_Black", G, seg=5)
    acyl("%s_raft%d" % (P, sx), 0.25, 0.9, (sx * 1.25, 6.6, 1.55), "M_White", G, axis=1, seg=12)
    acyl("%s_bol%d" % (P, sx), 0.07, 0.25, (sx * 1.45, 7.4, 1.4), "M_Steel", G, seg=8)
    rail = [Vector((sx * 1.82, y, 1.95)) for y in (4.6, 7.7)]
    rod("%s_rail%d" % (P, sx), rail[0], rail[1], 0.022, "M_Steel", G, seg=5)
    for y in (4.6, 6.1, 7.7):
        rod("%s_stn%d_%d" % (P, sx, int(y)), Vector((sx * 1.82, y, 1.27)), Vector((sx * 1.82, y, 1.95)), 0.018, "M_Steel", G, seg=5)
abox(P + "_engh", (2.2, 2.0, 0.25), (0, 6.0, 1.4), "M_SwGrey", G, bevel=0.05)
abox(P + "_engv", (1.8, 0.6, 0.02), (0, 5.8, 1.53), "M_Mesh", G, bevel=0.0)
lamp(P + "_stern", (0, 7.9, 1.45), "M_Strobe", G, r=0.05)
# ------------------------------------------------------------------------------------------------ waterjets
for sx, gj in ((1, GJL), (-1, GJR)):
    c = Vector((sx * 0.8, 8.0, 0.45))
    acyl("%s_jbody%d" % (P, sx), 0.3, 0.45, (sx * 0.8, 7.95, 0.45), "M_Steel", G, axis=1, seg=14)
    lathe_y("%s_jnoz%d" % (P, sx), [(0.0, 0.0), (0.0, 0.26), (0.35, 0.2), (0.38, 0.0)], tuple(c), ["M_Steel"], gj, seg=14)
    acyl("%s_jbore%d" % (P, sx), 0.17, 0.02, tuple(c + Vector((0, 0.38, 0))), "M_Bore", gj, axis=1, seg=12, dirt=False)
    abox("%s_jbucket%d" % (P, sx), (0.5, 0.06, 0.45), tuple(c + Vector((0, 0.55, 0.2))), "M_Steel", gj, rot=(math.radians(30), 0, 0), bevel=0.0)
    for s2 in (1, -1):
        abox("%s_jarm%d_%d" % (P, sx, s2), (0.05, 0.5, 0.08), tuple(c + Vector((s2 * 0.24, 0.4, 0.12))), "M_Steel", gj, bevel=0.0)

auto_attach([G, GTY, GTP], bracket_groups=())
weather(gen_weather([(0, 7.9, 1.0)], 0.6))
rt = root("root")
group_under(G, "cb90_body", rt)
ty = pivot("turret_yaw", tuple(TYc), rt)
group_under(GTY, "turret_yaw_geo", ty)
tp = pivot("turret_pitch", tuple(TP), ty)
group_under(GTP, "turret_pitch_geo", tp)
for sx, gj, s in ((1, GJL, "L"), (-1, GJR, "R")):
    pj = pivot("jet_" + s, (sx * 0.8, 8.0, 0.45), rt)
    group_under(gj, "jet_%s_geo" % s, pj)
rw_export("/home/user/.cache/rwveh/raw/cb90.glb", "/home/user/models/cb90.blend", "CB90")
