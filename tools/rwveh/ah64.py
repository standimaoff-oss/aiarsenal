# AH-64D Apache (Longbow) — tandem attack helicopter. CraftRenderer node contract:
# rotor_main (+rot Y), rotor_tail (rot X, left side of the fin), rotor_disc / tail_disc (blur), gun_yaw > gun_pitch (M230 chin
# turret), tads (TADS/PNVS nose turret), atgm_0..7 (Hellfire, hidden when fired). Fixed gear (static, part of the body).
# Kind -> Blender: K(x, y, z) = (x, -z, y). Hub Y 3.85, R 7.3; tail rotor R 1.4 at Z -8.25 (CraftKind.AH64).
exec(open("/home/user/tools/rwveh/craft_common.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
craft_mats()
amat("M_Ap", None, 0.66, 0.1, factor=(0.16, 0.19, 0.14, 1))
amat("M_ApDark", None, 0.6, 0.15, factor=(0.09, 0.1, 0.085, 1))
P = "AP"
G, GR, GT, GD, GTD, GG, GS = (P + s for s in ("_body", "_rot", "_tail", "_disc", "_tdisc", "_gun", "_tads"))
M, MD = "M_Ap", "M_ApDark"
HUB, RR = 3.85, 7.3
TR = Vector((0.45, 8.25, 3.0))

# ------------------------------------------------------------------------------------------------ fuselage
Y0, L = -4.75, 13.65
ctrl = [
    (0.00, 1.20, 0.26, 0.30, 0.30, 2.2),
    (0.04, 1.25, 0.45, 0.46, 0.50, 2.5),
    (0.10, 1.36, 0.52, 0.66, 0.62, 2.8),
    (0.20, 1.55, 0.55, 0.92, 0.80, 3.0),
    (0.30, 1.75, 0.60, 0.86, 0.90, 3.2),
    (0.42, 1.86, 0.56, 0.76, 0.86, 3.2),
    (0.52, 2.00, 0.40, 0.56, 0.60, 2.8),
    (0.62, 2.20, 0.28, 0.36, 0.36, 2.4),
    (0.85, 2.45, 0.22, 0.28, 0.25, 2.2),
    (1.00, 2.60, 0.15, 0.20, 0.15, 2.0)]
prof = ctrl_prof(ctrl, L, Y0)

def fus_mat(y, ang, z):
    zc = prof(y)[0]
    if (-4.05 < y < -2.75 or -2.6 < y < -1.15) and 14 < ang < 166 and z > zc + 0.18:
        return 1
    return 0
fus = fuselage(P + "_fus", Y0, L, ctrl, [M, "M_Glass"], G, n=44, seg=32, mat_fn=fus_mat)
def ring_pts(y, a0, a1, k=10, infl=0.02):
    zc, hw, ht, hb, n = prof(y)[:5]
    return [section_pt(math.radians(a0 + (a1 - a0) * i / k), zc, hw + infl, ht + infl, hb + infl, n) + Vector((0, y, 0)) for i in range(k + 1)]
for j, y in enumerate((-4.04, -3.4, -2.72, -2.58, -1.9, -1.17)):
    aobj("%s_bow%d" % (P, j), bm_tube_path(ring_pts(y, 12, 168), 0.028, 6), [MD], G, smooth=True, dirt=False)
cl = [Vector((0, y, prof(y)[0] + prof(y)[2] + 0.025)) for y in [-4.04 + 2.87 * i / 12 for i in range(13)]]
aobj(P + "_bowc", bm_tube_path(cl, 0.03, 6), [MD], G, smooth=True, dirt=False)
abox(P + "_blast", (0.9, 0.04, 0.6), (0, -2.66, 2.15), "M_Glass", G, bevel=0.0, dirt=False)   # blast shield between crew
# interior
for k, (y, z) in enumerate(((-3.55, 1.30), (-2.15, 1.90))):
    abox("%s_seat%d" % (P, k), (0.5, 0.5, 0.12), (0, y, z - 0.06), "M_Seat", G, bevel=0.02)
    abox("%s_seatb%d" % (P, k), (0.5, 0.14, 0.8), (0, y + 0.3, z + 0.38), "M_Seat", G, rot=(math.radians(-10), 0, 0), bevel=0.02)
    abox("%s_dash%d" % (P, k), (0.9, 0.3, 0.42), (0, y - 0.8, z + 0.32), "M_Dash", G, rot=(math.radians(28), 0, 0), bevel=0.03)
    for sx in (1, -1):
        abox("%s_mfd%d_%d" % (P, k, sx), (0.22, 0.03, 0.2), (sx * 0.17, y - 0.92, z + 0.55), "M_Screen", G, rot=(math.radians(28), 0, 0), bevel=0.0, dirt=False)
    rod("%s_stick%d" % (P, k), Vector((0, y - 0.35, z - 0.2)), Vector((0, y - 0.38, z + 0.3)), 0.022, "M_Black", G, seg=6)
abox(P + "_ortd", (0.35, 0.25, 0.3), (0, -4.05, 1.75), "M_Dash", G, bevel=0.03)
# avionics sponsons ("cheeks") + main gear (fixed, trailing arm)
for sx in (1, -1):
    abox("%s_cheek%d" % (P, sx), (0.55, 3.6, 0.82), (sx * 0.72, -1.2, 1.32), M, G, bevel=0.16)
    abox("%s_cheekv%d" % (P, sx), (0.02, 0.8, 0.3), (sx * 1.0, -0.2, 1.35), "M_Mesh", G, bevel=0.0)
    abox("%s_step%d" % (P, sx), (0.25, 0.3, 0.04), (sx * 0.95, -2.6, 0.75), "M_Steel", G, bevel=0.0)
    wc = Vector((sx * 1.05, -1.75, 0.37))
    rod("%s_mleg%d" % (P, sx), Vector((sx * 0.8, -1.4, 1.0)), wc + Vector((-sx * 0.12, 0, 0.05)), 0.08, "M_Steel", G, seg=10)
    rod("%s_moleo%d" % (P, sx), Vector((sx * 0.85, -1.25, 1.1)), wc + Vector((-sx * 0.12, 0.1, 0.25)), 0.06, "M_Hydraulic", G, seg=8)
    lathe_y("%s_mw%d" % (P, sx), [(-0.1, 0.17), (-0.1, 0.32), (-0.07, 0.36), (0.0, 0.37), (0.07, 0.36), (0.1, 0.32), (0.1, 0.17)],
            tuple(wc), ["M_Tire"], G, seg=20, axis='X', uv_tile=0.5)
    lathe_y("%s_mwh%d" % (P, sx), [(-0.09, 0.0), (-0.09, 0.18), (0.09, 0.18), (0.09, 0.0)], tuple(wc), ["M_Steel"], G, seg=12, axis='X')
# engines with "black hole" IR suppressors, pylon hump, APU
fuselage(P + "_hump", -1.4, 3.9, [(0, 2.45, 0.2, 0.05, 0.05, 2.0), (0.15, 2.58, 0.5, 0.3, 0.1, 2.4), (0.6, 2.64, 0.55, 0.38, 0.1, 2.8),
                                  (0.9, 2.55, 0.42, 0.25, 0.1, 2.5), (1.0, 2.45, 0.2, 0.05, 0.05, 2.0)], [M], G, n=14, seg=24)
for sx in (1, -1):
    c = Vector((sx * 0.98, 0.6, 2.3))
    lathe_y("%s_eng%d" % (P, sx), [(-1.3, 0.0), (-1.3, 0.26), (-1.18, 0.34), (-0.7, 0.37), (0.8, 0.37), (1.2, 0.33), (1.2, 0.0)], tuple(c), [M], G, seg=20)
    acyl("%s_intk%d" % (P, sx), 0.25, 0.02, tuple(c + Vector((0, -1.31, 0))), "M_Bore", G, axis=1, seg=16, dirt=False)
    abox("%s_bh%d" % (P, sx), (0.42, 0.8, 0.5), tuple(c + Vector((sx * 0.1, 1.5, 0.0))), MD, G, rot=(0, 0, math.radians(sx * 12)), bevel=0.06)
    abox("%s_bhx%d" % (P, sx), (0.04, 0.5, 0.36), tuple(c + Vector((sx * 0.33, 1.65, 0.0))), "M_Bore", G, rot=(0, 0, math.radians(sx * 12)), bevel=0.0, dirt=False)
    rod("%s_engp%d" % (P, sx), c + Vector((-sx * 0.3, -0.5, -0.15)), Vector((sx * 0.5, 0.1, 2.25)), 0.08, M, G, seg=8)
acyl(P + "_apu", 0.08, 0.2, (0.0, 2.3, 2.75), "M_Nozzle", G, axis=1, seg=10)
lamp(P + "_beac", (0, 1.5, 2.6), "M_NavRed", G, r=0.06)
rod(P + "_ant", Vector((0, 3.4, 2.5)), Vector((0, 3.7, 2.95)), 0.014, MD, G, seg=5)
# ------------------------------------------------------------------------------------------------ wings + stores
for sx in (1, -1):
    panel("%s_wing%d" % (P, sx), (sx * 0.55, -0.75, 1.82), (sx * 2.65, -0.7, 1.76), 1.15, 0.9, (0, 0, 1), [M], G, t_root=0.15, t_tip=0.12, n=3)
    abox("%s_wtip%d" % (P, sx), (0.14, 0.95, 0.16), (sx * 2.7, -0.3, 1.76), M, G, bevel=0.05)
    lamp("%s_nav%d" % (P, sx), (sx * 2.78, -0.75, 1.78), "M_NavRed" if sx > 0 else "M_NavGreen", G, r=0.04)
    abox("%s_cmd%d" % (P, sx), (0.25, 0.35, 0.18), (sx * 0.9, 2.6, 1.3), MD, G, bevel=0.03)   # M130 flare dispensers
    # inner pylon + M261 19-tube Hydra pod: muzzle K(+-1.55, 1.40, 1.05)
    hx = sx * 1.55
    abox("%s_pyl1%d" % (P, sx), (0.1, 0.8, 0.3), (hx, -0.3, 1.62), MD, G, bevel=0.0)
    pod("%s_m261%d" % (P, sx), (hx, -0.2, 1.40), 1.72, 0.21, M, G, seg=18, open_front=1, tail=0.3)
    acyl("%s_m261f%d" % (P, sx), 0.19, 0.02, (hx, -1.07, 1.40), "M_Bore", G, axis=1, seg=16, dirt=False)
    for k in range(6):
        a = 2 * PI * k / 6
        acyl("%s_hyt%d_%d" % (P, sx, k), 0.03, 0.03, (hx + 0.12 * math.cos(a), -1.08, 1.40 + 0.12 * math.sin(a)), "M_Warhead", G, axis=1, seg=8, dirt=False)
    # outer pylon + M299 launcher (4 rails): muzzle K(+-2.38, 1.45, 0.95)
    ox = sx * 2.38
    abox("%s_pyl2%d" % (P, sx), (0.1, 0.8, 0.12), (ox, -0.3, 1.68), MD, G, bevel=0.0)
    abox("%s_m299%d" % (P, sx), (0.1, 1.5, 0.5), (ox, -0.15, 1.45), MD, G, bevel=0.0)
    abox("%s_m299r%d" % (P, sx), (0.5, 1.4, 0.06), (ox, -0.15, 1.45), MD, G, bevel=0.0)
for i in range(8):
    sx = 1 if i % 2 == 0 else -1
    j = i // 2
    cx = sx * 2.38 + (0.2 if j % 2 == 0 else -0.2)
    cz = 1.45 + (0.17 if j < 2 else -0.17)
    grp = "%s_m%d" % (P, i)
    lathe_y("%s_hf%d" % (P, i), [(-0.82, 0.0), (-0.8, 0.05), (-0.72, 0.085), (-0.6, 0.09), (0.8, 0.09), (0.82, 0.0)], (cx, -0.13, cz), ["M_Olive"], grp, seg=12)
    acyl("%s_hfs%d" % (P, i), 0.05, 0.02, (cx, -0.955, cz), "M_Lens", grp, axis=1, seg=10, dirt=False)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        abox("%s_hff%d_%d" % (P, i, k), (0.01, 0.2, 0.09), (cx + math.cos(a) * 0.12, 0.5, cz + math.sin(a) * 0.12), "M_Olive", grp, rot=(0, PI / 2 - a, 0), bevel=0.0)
        abox("%s_hfc%d_%d" % (P, i, k), (0.01, 0.12, 0.06), (cx + math.cos(a) * 0.11, -0.45, cz + math.sin(a) * 0.11), "M_Olive", grp, rot=(0, PI / 2 - a, 0), bevel=0.0)
# ------------------------------------------------------------------------------------------------ M230 chin turret
GP = Vector((0, -2.95, 0.62))
abox(P + "_gmnt", (0.3, 0.4, 0.3), (0, -2.95, 0.88), MD, G, bevel=0.04)
acyl(P + "_gdrum", 0.24, 0.2, tuple(GP), MD, GG, seg=16)
abox(P + "_garm", (0.3, 0.9, 0.24), (0, -3.25, 0.5), M, GG, bevel=0.05)
abox(P + "_grec", (0.14, 0.7, 0.14), (0, -3.6, 0.45), "M_Steel", GG, bevel=0.0)
tube(P + "_gbar", (0, -3.7, 0.45), (0, -4.25, 0.45), 0.035, "M_Steel", GG, seg=10)
lathe_y(P + "_gmuz", [(-0.06, 0.0), (-0.06, 0.05), (0.06, 0.05), (0.06, 0.0)], (0, -4.28, 0.45), ["M_Dark"], GG, seg=10)
abox(P + "_gchute", (0.12, 0.3, 0.12), (0.12, -3.0, 0.62), "M_Steel", GG, bevel=0.0)
# ------------------------------------------------------------------------------------------------ TADS / PNVS nose turret
TD = Vector((0, -5.0, 1.22))
acyl(P + "_tdm", 0.18, 0.12, (0, -4.85, 1.22), MD, G, axis=1, seg=14)
acyl(P + "_tdd", 0.3, 0.62, tuple(TD), M, GS, seg=18)
for sx in (1, -1):
    abox("%s_tde%d" % (P, sx), (0.14, 0.5, 0.5), tuple(TD + Vector((sx * 0.36, 0.0, -0.02))), M, GS, bevel=0.05)
abox(P + "_tdw1", (0.2, 0.04, 0.18), tuple(TD + Vector((0.1, -0.3, 0.1))), "M_Lens", GS, bevel=0.0, dirt=False)
abox(P + "_tdw2", (0.14, 0.04, 0.14), tuple(TD + Vector((-0.1, -0.3, -0.12))), "M_Lens", GS, bevel=0.0, dirt=False)
acyl(P + "_pnvs", 0.15, 0.22, tuple(TD + Vector((0, 0.15, 0.42))), M, GS, seg=14)
abox(P + "_pnvsw", (0.12, 0.04, 0.1), tuple(TD + Vector((0, 0.0, 0.42))), "M_Lens", GS, bevel=0.0, dirt=False)
# ------------------------------------------------------------------------------------------------ tail
panel(P + "_fin", (0, 7.3, 2.45), (0, 8.35, 4.15), 1.55, 0.9, (1, 0, 0), [M], G, t_root=0.13, t_tip=0.1, n=3)
panel(P + "_vfin", (0, 7.55, 2.3), (0, 7.85, 1.35), 1.2, 0.9, (1, 0, 0), [M], G, t_root=0.12, t_tip=0.1, n=2)
for sx in (1, -1):
    panel("%s_stab%d" % (P, sx), (sx * 0.08, 7.75, 1.45), (sx * 1.7, 7.85, 1.45), 0.95, 0.75, (0, 0, 1), [M], G, t_root=0.1, t_tip=0.09, n=2)
abox(P + "_tgb", (0.32, 0.55, 0.45), (0.12, 8.25, 3.0), M, G, bevel=0.08)
rod(P + "_tlegt", Vector((0, 7.8, 1.4)), Vector((0, 7.95, 0.27)), 0.05, "M_Steel", G, seg=8)
lathe_y(P + "_tw", [(-0.06, 0.1), (-0.06, 0.2), (0.0, 0.21), (0.06, 0.2), (0.06, 0.1)], (0, 7.95, 0.21), ["M_Tire"], G, seg=16, axis='X')
lamp(P + "_strobe", (0, 9.2, 4.17), "M_Strobe", G, r=0.05)
# ------------------------------------------------------------------------------------------------ rotors
acyl(P + "_mast", 0.15, 1.75, (0, 0, 3.35), "M_Steel", G, seg=14)
lathe_y(P + "_fcr", [(0.0, 0.0), (0.0, 0.25), (0.12, 0.52), (0.32, 0.55), (0.52, 0.45), (0.62, 0.0)], (0, 0, HUB + 0.32), [M], G, seg=24, axis='Z')
abox(P + "_swash", (0.55, 0.55, 0.1), (0, 0, 2.95), "M_Steel", G, bevel=0.02)
c = Vector((0, 0, HUB))
lathe_y(P + "_hub", [(-0.15, 0.0), (-0.15, 0.36), (0.0, 0.42), (0.15, 0.3), (0.15, 0.0)], tuple(c), [MD], GR, seg=16, axis='Z')
for k in range(4):
    a = 45 + 90 * k
    er = Vector((math.cos(math.radians(a)), math.sin(math.radians(a)), 0))
    rod("%s_grip%d" % (P, k), c + er * 0.3, c + er * 0.85, 0.085, "M_Steel", GR, seg=10)
    rod("%s_plk%d" % (P, k), c + er * 0.24 + Vector((0, 0, -0.86)), c + er * 0.45 + Vector((0, 0, -0.05)), 0.025, "M_Hydraulic", GR, seg=6)
    rotor_blade("%s_bl%d" % (P, k), c, 0.8, RR, 0.53, a, GR, pitch_deg=7, twist_deg=-9, droop=0.2, t=0.1, tip_sweep=0.12)
    rotor_blade("%s_bt%d" % (P, k), c + Vector((0, 0, -0.2)), RR - 0.03, RR + 0.01, 0.4, a, GR, mats=("M_BladeTip",), pitch_deg=-2, twist_deg=0, n=1)
blur_disc("rotor_disc_m", (0, 0, HUB - 0.08), RR + 0.05, GD)
# tail rotor: 4 blades in the 55/125 deg "scissor" layout, axis X
acyl(P + "_trhub", 0.12, 0.42, tuple(TR + Vector((-0.08, 0, 0))), MD, GT, axis=0, seg=12)
for k, a in enumerate((0, 55, 180, 235)):
    ar = math.radians(a)
    d = Vector((0, -math.sin(ar), math.cos(ar)))
    abox("%s_trb%d" % (P, k), (0.03, 0.25, 1.25), tuple(TR + Vector((0.04 * (k % 2), 0, 0)) + d * 0.78), "M_Blade", GT, rot=(ar, 0, 0), bevel=0.0)
    abox("%s_trt%d" % (P, k), (0.031, 0.25, 0.12), tuple(TR + Vector((0.04 * (k % 2), 0, 0)) + d * 1.34), "M_BladeTip", GT, rot=(ar, 0, 0), bevel=0.0, dirt=False)
acyl("tail_disc_m", 1.42, 0.01, tuple(TR + Vector((0.03, 0, 0))), "M_Blur", GTD, axis=0, seg=28, dirt=False)

# ------------------------------------------------------------------------------------------------ rig + export
auto_attach([G, GG, GS], bracket_groups=())
weather(gen_weather([(0.98 * s, 2.3, 2.3) for s in (1, -1)], 0.6))
rt = root("root")
group_under(G, "ah64_body", rt)
for i in range(8):
    group_under("%s_m%d" % (P, i), "atgm_%d" % i, rt)
pm = pivot("rotor_main", (0, 0, HUB), rt)
group_under(GR, "rotor_main_geo", pm)
ptl = pivot("rotor_tail", tuple(TR), rt)
group_under(GT, "rotor_tail_geo", ptl)
group_under(GD, "rotor_disc", rt)
group_under(GTD, "tail_disc", rt)
gy = pivot("gun_yaw", tuple(GP), rt)
gp = pivot("gun_pitch", (0, -3.25, 0.5), gy)
group_under(GG, "gun_geo", gp)
td = pivot("tads", tuple(TD), rt)
group_under(GS, "tads_geo", td)
rw_export("/home/user/.cache/rwveh/raw/ah64.glb", "/home/user/models/ah64.blend", "AH64")
