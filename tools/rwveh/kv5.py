# KV-5 (Object 225, 1941 super-heavy project, LKZ) — dimensions from the project drawings.
# Hull 8.9 m, width 4.0 m, height 3.3 m, ~100 t. 8 x 660 mm steel road wheels/side, 4 return rollers, REAR sprocket,
# 800 mm tracks. Raised driver's tower (left) + DT machine-gun turret (right), 180 mm cast main turret with
# 107 mm ZiS-6, coax DT.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "KV5"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
BODY = "M_Green4BO"
HL = 8.9 / 2
TWD = 0.80
TX = 2.0 - TWD / 2               # 1.60
RR, TH = 0.33, 0.07
ZW = RR + TH
ROAD = [(-2.95 + i * 0.84, ZW) for i in range(8)]
SPK = (3.85, 0.78)
IDL = (-3.95, 0.72)
ROLL = [(3.0, 1.12), (1.0, 1.14), (-1.0, 1.14), (-3.0, 1.12)]
TC = Vector((0, 0.15, 1.88))
TRN = Vector((0, -1.15, 2.42))
MUZ_Y = -6.0

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = ww2_gear(P, side, x, ROAD, RR, "steel", 0.16, SPK, 0.38, IDL, 0.36, ROLL, 0.13, TWD, 0.180, TH,
                               mat=BODY, twin_gap=0.12, arm_grp=G, arm_len=0.28, teeth=14, wseg=14, spk_gap=0.12)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
for sx in (1, -1):
    acyl("%s_fdrv%d" % (P, sx), 0.28, 0.20, (sx * 1.22, SPK[0], SPK[1]), BODY, G, axis=0, seg=14)
    rod("%s_idla%d" % (P, sx), Vector((sx * 1.15, IDL[0] + 0.25, IDL[1] + 0.05)), Vector((sx * (TX - 0.10), IDL[0], IDL[1])), 0.07, BODY, G, seg=8)
    for y, z in ROLL:
        rod("%s_rrs%d_%d" % (P, sx, int(y * 10)), Vector((sx * 1.15, y, z)), Vector((sx * (TX - 0.05), y, z)), 0.04, BODY, G, seg=6)

# ---------------------------------------------------------------- hull (180 mm front)
lower = sym(box_pts(0, 1.15, -4.0, 4.1, 0.47, 1.25)) + sym([(1.15, -HL, 1.10), (1.15, HL, 1.10)])
chull(P + "_lower", lower, BODY, G, bevel=0.02)
upper = sym([(1.98, -4.05, 1.20), (1.98, 4.15, 1.20), (1.98, -HL, 1.10), (1.98, -3.35, 1.88), (1.98, 4.0, 1.88),
             (1.98, HL, 1.25)])
chull(P + "_upper", upper, BODY, G, bevel=0.03)
gl_n = Vector((0, -0.78, 1.10)).normalized()
for sx in (1, -1):
    abox("%s_fend%d" % (P, sx), (0.06, 8.2, 0.06), (sx * 1.98, 0.0, 1.18), BODY, G, bevel=0.0)
    rod("%s_ftow%d" % (P, sx), Vector((sx * 0.9, -HL + 0.05, 0.9)), Vector((sx * 0.9, -HL - 0.18, 0.9)), 0.055, "M_Steel", G, seg=8)
    headlamp(P, "f%d" % sx, (sx * 1.70, -4.30, 1.32), G, mat=BODY)
    rod("%s_cab%d" % (P, sx), Vector((sx * 1.99, -3.0, 1.55)), Vector((sx * 1.99, 3.0, 1.55)), 0.022, "M_Steel", G, seg=6)
    abox("%s_box%d" % (P, sx), (0.40, 1.2, 0.32), (sx * 1.70, 2.6, 2.04), BODY, G, bevel=0.02)
# driver's tower (left = +X) with vision slits and hatch
tw = sym([(0.45, -3.55, 1.80), (0.45, -2.55, 1.80), (0.40, -3.35, 2.42), (0.40, -2.60, 2.42)])
tw = [(x + 0.95, y, z) for x, y, z in tw]
chull(P + "_dtower", tw, BODY, G, bevel=0.03)
for k, (dx, dy, rz) in enumerate(((0.0, -0.47, 0.0), (0.42, 0.0, math.pi / 2), (-0.42, 0.0, math.pi / 2))):
    abox("%s_dslit%d" % (P, k), (0.22, 0.02, 0.03), (0.95 + dx, -3.05 + dy - (0.01 if k == 0 else 0), 2.25), "M_Bore", G, rot=(0, 0, rz), bevel=0.0, dirt=False)
hatch(P, "drv", (0.95, -3.0, 2.43), 0.25, G, mat=BODY)
# MG turret (right): static ring + small turret with DT
lathe_y(P + "_mgt", [(0.0, 0.50), (0.42, 0.46), (0.50, 0.30), (0.52, 0.0), (0.0, 0.0)], (-0.95, -3.0, 1.82), [BODY], G, seg=22, axis='Z')
dt_mg(P, "tur", Vector((-0.95, -3.40, 2.08)), (0, -1, 0), G)
abox(P + "_mgtm", (0.24, 0.14, 0.20), (-0.95, -3.46, 2.08), BODY, G, bevel=0.03)
hatch(P, "mgt", (-0.95, -2.95, 2.35), 0.22, G, mat=BODY)
# engine deck
abox(P + "_deck", (3.6, 2.8, 0.04), (0, 2.8, 1.89), BODY, G, bevel=0.0)
for sx in (1, -1):
    grille(P, "eng%d" % sx, (sx * 0.95, 2.6, 1.92), 1.1, 1.6, G, n=10, mat=BODY)
    acyl("%s_exh%d" % (P, sx), 0.11, 0.30, (sx * 0.70, HL - 0.12, 1.55), "M_Exhaust", G, rot=(0.9, 0, 0), seg=10)
abox(P + "_trh", (1.2, 0.6, 0.05), (0, 3.95, 1.91), BODY, G, bevel=0.01)
tools_set(P, "L", (1.75, 0.3, 1.91), G)
lift_hooks(P, "h", [(sx * 1.75, y, 1.88) for sx in (1, -1) for y in (-3.2, 4.0)], G)

# ---------------------------------------------------------------- cast main turret (180 mm)
tl = [(1.88, superellipse(1.40, 1.40, 1.85, n=26, e=2.3, y0=TC.y)),
      (2.55, superellipse(1.36, 1.36, 1.85, n=26, e=2.3, y0=TC.y)),
      (2.95, superellipse(1.15, 1.15, 1.65, n=26, e=2.2, y0=TC.y)),
      (3.02, superellipse(0.95, 0.95, 1.40, n=26, e=2.2, y0=TC.y))]
turret_loft(P + "_turret", tl, BODY, GT, bevel=0.0, smooth=True)
cupola_ru(P, "cmd", (0.55, TC.y + 0.55, 2.97), 0.34, GT, mat=BODY, h=0.24)
hatch(P, "ld", (-0.55, TC.y + 0.40, 3.03), 0.28, GT, mat=BODY)
periscope(P, "gn", Vector((0.30, TC.y - 0.50, 3.02)), GT)
for sx in (1, -1):
    acyl("%s_pport%d" % (P, sx), 0.05, 0.05, (sx * 1.38, TC.y + 0.7, 2.30), BODY, GT, axis=0, seg=10)
    lift_hooks(P, "t%d" % sx, [(sx * 1.0, TC.y - 0.6, 2.98), (sx * 0.95, TC.y + 1.3, 2.96)], GT)
abox(P + "_tbal", (0.32, 0.22, 0.26), (0.0, TC.y + 1.88, 2.25), BODY, GT, bevel=0.03)
antenna(P, "ant", Vector((1.0, TC.y + 1.1, 2.9)), 2.4, GT)

# ---------------------------------------------------------------- mantlet + ZiS-6
abox(P + "_mant", (1.25, 0.32, 0.72), (0, TC.y - 1.45, TRN.z), BODY, GG, bevel=0.10)
lathe_y(P + "_mantc", [(0.0, 0.0), (0.0, 0.28), (0.25, 0.22), (0.45, 0.16), (0.50, 0.0)], (0, TC.y - 1.60, TRN.z), [BODY], GG, seg=18)
acyl(P + "_coax", 0.022, 0.10, (0.36, TC.y - 1.65, TRN.z), "M_Dark", GG, axis=1, seg=8)
acyl(P + "_sight", 0.035, 0.08, (-0.33, TC.y - 1.65, TRN.z + 0.12), "M_Lens", GG, axis=1, seg=8, dirt=False)
gun_simple(P, GB, TC.y - 2.05, MUZ_Y, TRN.z, 0.070, 0.105, collar=False, steps=[(0.35, 0.088)], mat=BODY)

auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
for sx in (1, -1):
    ww2_decal("%s_name%d" % (P, sx), (sx * 1.375, TC.y + 0.30, 2.25), (sx, 0, 0.04), (0, 0, 1), 0.85, "ru_kv", GT)
    ww2_decal("%s_gv%d" % (P, sx), (sx * 1.99, 0.5, 1.55), (sx, 0, 0), (0, 0, 1), 1.4, "ru_gvard", G)
    ww2_decal("%s_star%d" % (P, sx), (sx * 1.335, TC.y - 0.50, 2.30), (sx, -0.12, 0), (0, 0, 1), 0.42, "star_red", GT)
weather(gen_weather([(0.7, HL, 1.55), (-0.7, HL, 1.55)], 0.7))
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_kv5.glb", "/home/user/models/rw_kv5.blend", "RW_KV5")
