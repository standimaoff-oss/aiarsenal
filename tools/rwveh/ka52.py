# Ka-52 "Alligator" — coaxial attack helicopter, side-by-side crew. CraftRenderer node contract:
# rotor_upper (+rot Y) / rotor_lower (-rot), rotor_disc / rotor_disc2 (blur), gun_yaw > gun_pitch (2A42 on the right side),
# gear_nose / gear_L / gear_R (retract by +X rotation), tads (GOES-451 ball), atgm_0..11 (Vikhr tubes, hidden when fired).
# Kind -> Blender: K(x, y, z) = (x, -z, y).  Hubs Y 3.65 / 4.75, R 7.25 (CraftKind.KA52).
exec(open("/home/user/tools/rwveh/craft_common.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
craft_mats()
amat("M_Ka", None, 0.62, 0.15, factor=(0.25, 0.27, 0.25, 1))
amat("M_KaDark", None, 0.6, 0.15, factor=(0.17, 0.18, 0.17, 1))
amat("M_KaBelly", None, 0.6, 0.15, factor=(0.45, 0.48, 0.47, 1))
P = "KA"
G, GRL, GRU, GD1, GD2, GG, GN, GL, GR, GT = (P + s for s in ("_body", "_rl", "_ru", "_d1", "_d2", "_gun", "_gn", "_gl", "_gr", "_tads"))
M, MD = "M_Ka", "M_KaDark"
HUB_L, HUB_U, RR = 3.65, 4.75, 7.25

# ------------------------------------------------------------------------------------------------ fuselage
Y0, L = -6.25, 13.6
ctrl = [  # t, zc, hw, ht, hb, n
    (0.00, 1.55, 0.04, 0.04, 0.04, 2.0),
    (0.035, 1.55, 0.40, 0.40, 0.44, 2.1),
    (0.085, 1.60, 0.62, 0.56, 0.62, 2.4),
    (0.13, 1.70, 0.82, 0.78, 0.74, 2.7),
    (0.22, 1.76, 0.96, 0.96, 0.86, 3.0),
    (0.33, 1.80, 0.97, 0.98, 0.92, 3.3),
    (0.45, 1.86, 0.92, 0.92, 0.94, 3.3),
    (0.55, 1.96, 0.76, 0.76, 0.80, 3.0),
    (0.65, 2.10, 0.50, 0.50, 0.55, 2.6),
    (0.80, 2.20, 0.34, 0.36, 0.33, 2.4),
    (0.93, 2.30, 0.26, 0.30, 0.25, 2.2),
    (1.00, 2.36, 0.14, 0.18, 0.14, 2.0)]
prof = ctrl_prof(ctrl, L, Y0)
GL0, GL1 = -4.75, -2.55   # glazing stations (Blender y)

def fus_mat(y, ang, z):
    zc = prof(y)[0]
    if GL0 < y < GL1 and 16 < ang < 164 and z > zc + 0.12:
        return 1
    if GL0 + 0.5 < y < GL1 - 0.2 and (8 < ang < 20 or 160 < ang < 172):
        return 1          # side windows (doors)
    if ang > 200 and ang < 340:
        return 2          # lighter belly
    return 0
fus = fuselage(P + "_fus", Y0, L, ctrl, [M, "M_Glass", "M_KaBelly"], G, n=44, seg=36, mat_fn=fus_mat)
# canopy frames (bows + centre beam)
def ring_pts(y, a0, a1, k=12, infl=0.02):
    zc, hw, ht, hb, n = prof(y)[:5]
    return [section_pt(math.radians(a0 + (a1 - a0) * i / k), zc, hw + infl, ht + infl, hb + infl, n) + Vector((0, y, 0)) for i in range(k + 1)]
for j, y in enumerate((GL0 + 0.02, -4.05, -3.3, GL1 - 0.02)):
    aobj("%s_bow%d" % (P, j), bm_tube_path(ring_pts(y, 10, 170), 0.025, 6), [MD], G, smooth=True, dirt=False)
cl = [Vector((0, y, prof(y)[0] + prof(y)[2] + 0.02)) for y in [GL0 + (GL1 - GL0) * i / 10 for i in range(11)]]
aobj(P + "_bowc", bm_tube_path(cl, 0.03, 6), [MD], G, smooth=True, dirt=False)
for sx in (1, -1):
    sill = [section_pt(math.radians(14 if sx > 0 else 166), prof(y)[0], prof(y)[1] + 0.02, prof(y)[2] + 0.02, prof(y)[3] + 0.02, prof(y)[4]) + Vector((0, y, 0))
            for y in [GL0 + (GL1 - GL0) * i / 10 for i in range(11)]]
    aobj("%s_sill%d" % (P, sx), bm_tube_path(sill, 0.03, 6), [MD], G, smooth=True, dirt=False)
# radome seam, pitots, wipers
lathe_y(P + "_radring", [(-0.03, 0.0), (-0.03, 0.635), (0.03, 0.635), (0.03, 0.0)], (0, -5.1, 1.6), [MD], G, seg=32)
for sx in (1, -1):
    rod("%s_pitot%d" % (P, sx), Vector((sx * 0.55, -4.75, 2.05)), Vector((sx * 0.55, -5.55, 2.05)), 0.018, "M_Steel", G, seg=6)
    rod("%s_pitotb%d" % (P, sx), Vector((sx * 0.55, -4.75, 2.05)), Vector((sx * 0.62, -4.45, 1.95)), 0.03, M, G, seg=6)
# cockpit interior: seats, dashboard with MFDs, sticks
for sx in (1, -1):
    x = sx * 0.45
    abox("%s_seat%d" % (P, sx), (0.5, 0.5, 0.12), (x, -3.5, 1.24), "M_Seat", G, bevel=0.02)
    abox("%s_seatb%d" % (P, sx), (0.5, 0.14, 0.85), (x, -3.18, 1.7), "M_Seat", G, rot=(math.radians(-12), 0, 0), bevel=0.02)
    abox("%s_head%d" % (P, sx), (0.32, 0.16, 0.22), (x, -3.1, 2.22), "M_Seat", G, bevel=0.02)
    abox("%s_mfd%d" % (P, sx), (0.26, 0.03, 0.2), (x, -4.33, 1.86), "M_Screen", G, rot=(math.radians(25), 0, 0), bevel=0.0, dirt=False)
    rod("%s_stick%d" % (P, sx), Vector((x, -3.95, 1.0)), Vector((x, -3.98, 1.55)), 0.022, "M_Black", G, seg=6)
    abox("%s_coll%d" % (P, sx), (0.05, 0.4, 0.05), (x + sx * 0.3, -3.6, 1.35), "M_Black", G, rot=(math.radians(-20), 0, 0), bevel=0.0)
abox(P + "_dash", (1.6, 0.35, 0.5), (0, -4.35, 1.62), "M_Dash", G, rot=(math.radians(25), 0, 0), bevel=0.03)
abox(P + "_dashc", (0.3, 0.03, 0.28), (0, -4.42, 1.92), "M_Screen", G, rot=(math.radians(25), 0, 0), bevel=0.0, dirt=False)
abox(P + "_floor", (1.7, 1.9, 0.05), (0, -3.6, 0.98), "M_Interior", G, bevel=0.0)
abox(P + "_rearw", (1.8, 0.06, 1.4), (0, -2.75, 1.75), "M_Interior", G, bevel=0.0)
# rotor pylon hump + engines
fuselage(P + "_hump", -1.9, 4.2, [(0, 2.62, 0.25, 0.05, 0.05, 2.0), (0.12, 2.72, 0.55, 0.32, 0.1, 2.4), (0.55, 2.78, 0.62, 0.42, 0.12, 2.8),
                                  (0.85, 2.72, 0.5, 0.3, 0.1, 2.5), (1.0, 2.62, 0.2, 0.05, 0.05, 2.0)], [M], G, n=14, seg=24)
for sx in (1, -1):
    c = Vector((sx * 0.98, -0.15, 2.62))
    lathe_y("%s_eng%d" % (P, sx), [(-1.55, 0.0), (-1.55, 0.27), (-1.42, 0.36), (-0.9, 0.39), (0.7, 0.39), (1.3, 0.33), (1.55, 0.27), (1.55, 0.0)],
            tuple(c), [M], G, seg=20)
    acyl("%s_intk%d" % (P, sx), 0.26, 0.02, tuple(c + Vector((0, -1.56, 0))), "M_Bore", G, axis=1, seg=18, dirt=False)
    lathe_y("%s_dust%d" % (P, sx), [(-0.25, 0.0), (-0.25, 0.2), (0.0, 0.31), (0.25, 0.31), (0.25, 0.0)], tuple(c + Vector((0, -1.8, 0))), [MD], G, seg=16)
    rod("%s_engp%d" % (P, sx), c + Vector((-sx * 0.3, -0.6, -0.15)), Vector((sx * 0.55, -0.6, 2.45)), 0.08, M, G, seg=8)
    rod("%s_engp2%d" % (P, sx), c + Vector((-sx * 0.3, 0.8, -0.15)), Vector((sx * 0.5, 0.8, 2.5)), 0.08, M, G, seg=8)
    # exhaust deflector (EVU) bent outward-down
    e0 = c + Vector((0, 1.5, 0))
    e1 = e0 + Vector((sx * 0.35, 0.55, -0.15))
    rod("%s_exh%d" % (P, sx), e0, e1, 0.25, "M_Nozzle", G, r2=0.21, seg=16)
    acyl("%s_exhb%d" % (P, sx), 0.19, 0.02, tuple(e1 + Vector((sx * 0.02, 0.02, 0))), "M_Bore", G, rot=(math.radians(-90), 0, math.radians(-sx * 32)), seg=16, dirt=False)
    abox("%s_grill%d" % (P, sx), (0.02, 0.6, 0.25), (sx * 1.33, -0.3, 2.65), "M_Mesh", G, bevel=0.0)
# APU exhaust, antennas, beacons
acyl(P + "_apu", 0.09, 0.2, (0.0, 1.95, 2.86), "M_Nozzle", G, axis=1, seg=12)
rod(P + "_ant1", Vector((0, 3.0, 2.48)), Vector((0, 3.3, 2.95)), 0.015, MD, G, seg=5)
rod(P + "_ant2", Vector((0, -1.0, 0.92)), Vector((0, -1.15, 0.55)), 0.015, MD, G, seg=5)
abox(P + "_antb", (0.05, 0.4, 0.2), (0, 4.2, 2.6), MD, G, rot=(math.radians(-25), 0, 0), bevel=0.0)
lamp(P + "_beac", (0, 1.2, 1.13), "M_NavRed", G, r=0.06)
# ------------------------------------------------------------------------------------------------ stub wings + stores
for sx in (1, -1):
    w = panel("%s_wing%d" % (P, sx), (sx * 0.78, -0.95, 1.84), (sx * 3.3, -0.85, 1.74), 1.35, 1.0, (0, 0, 1), [M], G, t_root=0.16, t_tip=0.13, n=3)
    abox("%s_wfair%d" % (P, sx), (0.25, 1.4, 0.32), (sx * 0.86, -0.4, 1.84), M, G, bevel=0.06)
    # wingtip EW / flare pods (UV-26 + L-370 Vitebsk)
    pod("%s_tip%d" % (P, sx), (sx * 3.48, -0.4, 1.68), 2.1, 0.19, M, G, seg=14)
    for k in range(3):
        acyl("%s_flr%d_%d" % (P, sx, k), 0.05, 0.02, (sx * 3.67, 0.15 + k * 0.14, 1.68), "M_Bore", G, axis=0, seg=8, dirt=False)
    abox("%s_mws%d" % (P, sx), (0.1, 0.14, 0.12), (sx * 3.48, -1.42, 1.8), "M_Lens", G, bevel=0.0)
    lamp("%s_nav%d" % (P, sx), (sx * 3.6, -1.15, 1.7), "M_NavRed" if sx > 0 else "M_NavGreen", G, r=0.045)
    # inner pylon + B-8V20 rocket pod (S-8): muzzle K(+-1.70, 1.12, 1.30)
    px = sx * 1.70
    abox("%s_pyl1%d" % (P, sx), (0.1, 0.9, 0.42), (px, -0.35, 1.55), MD, G, bevel=0.0)
    pod("%s_b8%d" % (P, sx), (px, -0.2, 1.12), 2.2, 0.26, M, G, seg=18, open_front=1, tail=0.35)
    acyl("%s_b8f%d" % (P, sx), 0.23, 0.02, (px, -1.31, 1.12), "M_Bore", G, axis=1, seg=18, dirt=False)
    for k in range(7):
        a = 2 * PI * k / 7
        acyl("%s_b8t%d_%d" % (P, sx, k), 0.035, 0.03, (px + 0.15 * math.cos(a), -1.33, 1.12 + 0.15 * math.sin(a)), "M_Warhead", G, axis=1, seg=8, dirt=False)
    # outer pylon + Vikhr launcher rack: muzzle K(+-2.78, 1.25, 1.10)
    ox = sx * 2.78
    abox("%s_pyl2%d" % (P, sx), (0.1, 0.9, 0.3), (ox, -0.3, 1.6), MD, G, bevel=0.0)
    abox("%s_rack%d" % (P, sx), (0.62, 1.9, 0.08), (ox, 0.1, 1.43), MD, G, bevel=0.0)
# Vikhr tubes: atgm_0..11 (even = left / odd = right, as CraftEntity cycles the two muzzles)
for i in range(12):
    sx = 1 if i % 2 == 0 else -1
    j = i // 2
    cx = sx * 2.78 + (j % 3 - 1) * 0.19
    cz = 1.33 - (j // 3) * 0.19
    grp = "%s_m%d" % (P, i)
    tube("%s_mt%d" % (P, i), (cx, -1.1, cz), (cx, 1.25, cz), 0.085, "M_Olive", grp, seg=10)
    acyl("%s_mc%d" % (P, i), 0.075, 0.02, (cx, -1.11, cz), "M_Warhead", grp, axis=1, seg=10, dirt=False)
    acyl("%s_mb%d" % (P, i), 0.09, 0.05, (cx, 0.9, cz), MD, grp, axis=1, seg=10)
# ------------------------------------------------------------------------------------------------ 2A42 gun (right side)
GP = K(-1.05, 1.12, 0.45)
abox(P + "_gunfair", (0.36, 1.6, 0.42), (-0.86, -0.45, 1.28), M, G, bevel=0.08)
abox(P + "_feed", (0.25, 0.5, 0.12), (-0.72, -0.2, 1.08), MD, G, bevel=0.0)
abox(P + "_grec", (0.24, 1.2, 0.28), (GP.x, GP.y - 0.25, GP.z), MD, GG, bevel=0.04)
abox(P + "_gbox", (0.18, 0.5, 0.2), (GP.x + 0.1, GP.y + 0.05, GP.z - 0.06), "M_Steel", GG, bevel=0.02)
acyl(P + "_gsleeve", 0.075, 0.6, (GP.x, GP.y - 1.05, GP.z + 0.02), MD, GG, axis=1, seg=12)
tube(P + "_gbar", (GP.x, GP.y - 1.3, GP.z + 0.02), (GP.x, -3.25, GP.z + 0.02), 0.045, "M_Steel", GG, seg=10)
lathe_y(P + "_gmuz", [(-0.12, 0.0), (-0.12, 0.06), (0.1, 0.065), (0.12, 0.05), (0.12, 0.0)], (GP.x, -3.32, GP.z + 0.02), ["M_Dark"], GG, seg=10)
# ------------------------------------------------------------------------------------------------ GOES-451 ball (tads)
TD = Vector((0, -5.0, 0.86))
lathe_y(P + "_tdm", [(-0.06, 0.0), (-0.06, 0.2), (0.06, 0.2), (0.06, 0.0)], tuple(TD + Vector((0, 0, 0.34))), [MD], G, seg=16, axis='Z')
aobj(P + "_tdb", bm_sphere(0.3, tuple(TD), 20, 12), [M], GT, smooth=True)
abox(P + "_tdw1", (0.22, 0.05, 0.16), tuple(TD + Vector((0.07, -0.28, 0.02))), "M_Lens", GT, bevel=0.0, dirt=False)
abox(P + "_tdw2", (0.1, 0.05, 0.1), tuple(TD + Vector((-0.13, -0.27, 0.02))), "M_Lens", GT, bevel=0.0, dirt=False)
# ------------------------------------------------------------------------------------------------ tail
for sx in (1, -1):
    panel("%s_hst%d" % (P, sx), (sx * 0.22, 5.4, 2.25), (sx * 1.72, 5.55, 2.25), 0.9, 0.72, (0, 0, 1), [M], G, t_root=0.12, t_tip=0.1, n=2)
    panel("%s_epl%d" % (P, sx), (sx * 1.74, 5.3, 1.85), (sx * 1.74, 5.62, 2.8), 0.95, 0.62, (1, 0, 0), [M], G, t_root=0.1, t_tip=0.08, n=2)
panel(P + "_fin", (0, 6.15, 2.42), (0, 6.95, 3.95), 1.3, 0.75, (1, 0, 0), [M], G, t_root=0.12, t_tip=0.1, n=3)
panel(P + "_vfin", (0, 6.35, 2.15), (0, 6.75, 1.62), 0.9, 0.5, (1, 0, 0), [M], G, t_root=0.1, t_tip=0.08, n=2)
abox(P + "_rud", (0.05, 0.3, 1.1), (0, 7.25, 3.05), MD, G, rot=(math.radians(-25), 0, 0), bevel=0.0)
lamp(P + "_strobe", (0, 7.38, 3.98), "M_Strobe", G, r=0.05)
abox(P + "_tskid", (0.06, 0.5, 0.06), (0, 6.75, 1.58), "M_Steel", G, bevel=0.0)
for sx in (1, -1):
    decal_on("%s_star%d" % (P, sx), fus, (sx * 0.36, 3.9, 2.2), (sx, 0, 0), (0, 0, 1), 0.48, "star_ru", G)
    decal_on("%s_flag%d" % (P, sx), fus, (sx * 0.93, -2.2, 1.55), (sx, 0, 0), (0, 0, 1), 0.36, "flag_ru", G)
# ------------------------------------------------------------------------------------------------ landing gear
def tyre(name, c, r, w, grp, hub_mat="M_Steel"):
    lathe_y(name, [(-w / 2, r * 0.45), (-w / 2, r * 0.86), (-w * 0.36, r * 0.98), (0.0, r), (w * 0.36, r * 0.98), (w / 2, r * 0.86), (w / 2, r * 0.45)],
            tuple(c), ["M_Tire"], grp, seg=20, axis='X', uv_tile=0.5)
    lathe_y(name + "h", [(-w * 0.45, 0.0), (-w * 0.45, r * 0.47), (w * 0.45, r * 0.47), (w * 0.45, 0.0)], tuple(c), [hub_mat], grp, seg=14, axis='X')
NP = Vector((0, -4.3, 0.98))
rod(P + "_nleg", NP, Vector((0, -4.3, 0.3)), 0.06, "M_Hydraulic", GN, seg=10)
rod(P + "_nleg2", NP + Vector((0, 0.05, 0)), Vector((0, -4.3, 0.55)), 0.085, "M_Steel", GN, seg=10)
acyl(P + "_naxle", 0.04, 0.42, (0, -4.3, 0.3), "M_Steel", GN, axis=0, seg=8)
for sx in (1, -1):
    tyre("%s_nw%d" % (P, sx), (sx * 0.15, -4.3, 0.3), 0.3, 0.13, GN)
for sx, grp in ((1, GL), (-1, GR)):
    mp = Vector((sx * 0.95, 0.25, 1.06))
    abox("%s_spon%d" % (P, sx), (0.3, 1.6, 0.36), (sx * 0.9, 0.45, 1.05), M, G, bevel=0.1)
    rod("%s_mleg%d" % (P, sx), mp, Vector((sx * 1.04, 0.25, 0.38)), 0.07, "M_Hydraulic", grp, seg=10)
    rod("%s_mleg2%d" % (P, sx), mp, Vector((sx * 1.01, 0.25, 0.72)), 0.095, "M_Steel", grp, seg=10)
    rod("%s_mstr%d" % (P, sx), mp + Vector((0, -0.35, 0)), Vector((sx * 1.02, 0.25, 0.6)), 0.04, "M_Steel", grp, seg=8)
    tyre("%s_mw%d" % (P, sx), (sx * 1.13, 0.25, 0.38), 0.38, 0.2, grp)
# ------------------------------------------------------------------------------------------------ coaxial rotors
acyl(P + "_mast", 0.17, 2.3, (0, 0.1, 3.9), "M_Steel", G, seg=16)
abox(P + "_swash", (0.62, 0.62, 0.1), (0, 0.1, 3.18), "M_Steel", G, bevel=0.02)
for k in range(3):
    a = 2 * PI * k / 3
    rod("%s_plnk%d" % (P, k), Vector((0.25 * math.cos(a), 0.1 + 0.25 * math.sin(a), 3.2)), Vector((0.25 * math.cos(a), 0.1 + 0.25 * math.sin(a), 3.55)), 0.025, "M_Hydraulic", G, seg=6)
for grp, z, cw, ph in ((GRL, HUB_L, True, 0.0), (GRU, HUB_U, False, 60.0)):
    c = Vector((0, 0.1, z))
    lathe_y(grp + "_hub", [(-0.17, 0.0), (-0.17, 0.42), (-0.05, 0.47), (0.12, 0.44), (0.17, 0.3), (0.17, 0.0)], tuple(c), [MD], grp, seg=18, axis='Z')
    for k in range(3):
        a = ph + 120 * k
        er = Vector((math.cos(math.radians(a)), math.sin(math.radians(a)), 0))
        rod("%s_grip%d" % (grp, k), c + er * 0.35, c + er * 0.85, 0.09, "M_Steel", grp, seg=10)
        rod("%s_damp%d" % (grp, k), c + er * 0.3 + Vector((0, 0, 0.1)), c + er * 0.75 + Vector((0, 0, 0.06)), 0.035, "M_Hydraulic", grp, seg=6)
        rotor_blade("%s_bl%d" % (grp, k), c, 0.8, RR, 0.52, a, grp, pitch_deg=7, twist_deg=-8, droop=0.18 if cw else 0.12, t=0.11, cw=cw)
        rotor_blade("%s_tip%d" % (grp, k), c + Vector((0, 0, -(0.18 if cw else 0.12))), RR - 0.02, RR + 0.02, 0.52, a, grp, mats=("M_BladeTip",), pitch_deg=-1, twist_deg=0, t=0.11, n=1, cw=cw)
lathe_y(P + "_cap", [(0.0, 0.0), (0.0, 0.3), (0.18, 0.27), (0.38, 0.15), (0.45, 0.0)], (0, 0.1, HUB_U + 0.17), [MD], GRU, seg=18, axis='Z')
blur_disc("rotor_disc_m", (0, 0.1, HUB_U + 0.02), RR + 0.05, GD1)
blur_disc("rotor_disc2_m", (0, 0.1, HUB_L - 0.08), RR + 0.05, GD2)

# ------------------------------------------------------------------------------------------------ rig + export
auto_attach([G, GG, GT], bracket_groups=())
weather(gen_weather([(0.98 * s, 1.9, 2.5) for s in (1, -1)], 0.6))
rt = root("root")
group_under(G, "ka52_body", rt)
for i in range(12):
    group_under("%s_m%d" % (P, i), "atgm_%d" % i, rt)
pl = pivot("rotor_lower", (0, 0.1, HUB_L), rt)
group_under(GRL, "rotor_lower_geo", pl)
pu = pivot("rotor_upper", (0, 0.1, HUB_U), rt)
group_under(GRU, "rotor_upper_geo", pu)
group_under(GD1, "rotor_disc", rt)
group_under(GD2, "rotor_disc2", rt)
gy = pivot("gun_yaw", tuple(GP), rt)
gp = pivot("gun_pitch", tuple(GP), gy)
group_under(GG, "gun_geo", gp)
gn = pivot("gear_nose", tuple(NP), rt)
group_under(GN, "gear_nose_geo", gn)
gl = pivot("gear_L", (0.95, 0.25, 1.06), rt)
group_under(GL, "gear_L_geo", gl)
gr = pivot("gear_R", (-0.95, 0.25, 1.06), rt)
group_under(GR, "gear_R_geo", gr)
td = pivot("tads", tuple(TD), rt)
group_under(GT, "tads_geo", td)
rw_export("/home/user/.cache/rwveh/raw/ka52.glb", "/home/user/models/ka52.blend", "KA52")
