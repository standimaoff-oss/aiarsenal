# IS-2 (1944, straightened 120 mm upper glacis) — real dimensions.
# Hull 6.77 m (9.90 with gun), width 3.07 m, height 2.73 m, clearance 0.42 m. 6 x 550 mm steel road wheels/side,
# 3 return rollers, REAR sprocket, 650 mm tracks pitch 160 mm (86 links). 122 mm D-25T L/43 with double-baffle brake,
# coax DT, DShK 12.7 mm on the loader's hatch.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "IS2"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
BODY = "M_Green4BO"
HL = 6.77 / 2
TWD = 0.65
TX = 3.07 / 2 - TWD / 2          # 1.21
RR = 0.275
TH = 0.06
ZW = RR + TH
ROAD = [(-2.08 + i * 0.83, ZW) for i in range(6)]
SPK = (2.90, 0.72)
IDL = (-3.00, 0.64)
ROLL = [(1.45, 0.98), (0.0, 1.0), (-1.45, 0.98)]
TC = Vector((0, -1.20, 1.55))      # r9: ring 2.2 m behind the nose (drawing), turret front-heavy
TRN = Vector((0, -2.20, 2.05))
MUZ_Y = -HL - (9.90 - 6.77)      # -6.515

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = ww2_gear(P, side, x, ROAD, RR, "steel", 0.13, SPK, 0.32, IDL, 0.30, ROLL, 0.12, TWD, 0.160, TH,
                               mat=BODY, twin_gap=0.10, arm_grp=G, arm_len=0.25, teeth=14, wseg=16, spk_gap=0.10)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
for sx in (1, -1):
    acyl("%s_fdrv%d" % (P, sx), 0.24, 0.18, (sx * 0.96, SPK[0], SPK[1]), BODY, G, axis=0, seg=14)
    rod("%s_idla%d" % (P, sx), Vector((sx * 0.90, IDL[0] + 0.22, IDL[1] + 0.05)), Vector((sx * (TX - 0.08), IDL[0], IDL[1])), 0.06, BODY, G, seg=8)
    for y, z in ROLL:
        rod("%s_rrs%d_%d" % (P, sx, int(y * 10)), Vector((sx * 0.90, y, z)), Vector((sx * (TX - 0.05), y, z)), 0.035, BODY, G, seg=6)

# ---------------------------------------------------------------- hull
lower = sym(box_pts(0, 0.90, -3.00, 3.05, 0.42, 1.02)) + sym([(0.90, -HL, 0.95), (0.90, HL, 0.98)])
chull(P + "_lower", lower, BODY, G, bevel=0.02)
upper = sym([(1.53, -3.05, 1.0), (1.53, 3.10, 1.0), (1.53, -HL, 0.95), (1.38, -2.45, 1.55), (1.38, 2.55, 1.55),
             (1.53, HL, 1.05)])
chull(P + "_upper", upper, BODY, G, bevel=0.025)
gl_n = Vector((0, -0.60, 0.94)).normalized()
def glacis(y, x):
    return Vector((x, y, 0.95 + (y + HL) * (1.55 - 0.95) / (HL - 2.45)))
abox(P + "_visor", (0.30, 0.10, 0.14), tuple(glacis(-2.75, 0.0) + gl_n * 0.05), BODY, G, rot=(math.atan2(-gl_n.y, gl_n.z), 0, 0), bevel=0.02)
headlamp(P, "f", glacis(-3.10, 1.05) + Vector((0, -0.05, 0.12)), G, mat=BODY)
for sx in (1, -1):
    rod("%s_ftow%d" % (P, sx), Vector((sx * 0.70, -HL + 0.05, 0.78)), Vector((sx * 0.70, -HL - 0.15, 0.78)), 0.045, "M_Steel", G, seg=8)
    abox("%s_fend%d" % (P, sx), (0.36, 6.4, 0.01), (sx * 1.36, 0.0, 1.00), BODY, G, bevel=0.0)
    fuel_drum(P, "r%d_a" % sx, (sx * 1.30, 2.00, 1.20), 0.18, 0.90, G, axis=1, mat=BODY)
    abox("%s_box%d" % (P, sx), (0.32, 1.1, 0.30), (sx * 1.36, -1.65, 1.17), BODY, G, bevel=0.02)
    rod("%s_cab%d" % (P, sx), Vector((sx * 1.47, -1.0, 1.30)), Vector((sx * 1.47, 1.3, 1.30)), 0.018, "M_Steel", G, seg=6)
spare_links(P, "glac", glacis(-2.95, 0.0) + gl_n * 0.01, 3, Vector((0.65, 0, 0)), gl_n, G, w=0.60, pitch=0.16)
abox(P + "_roof", (2.6, 2.3, 0.04), (0, 1.50, 1.56), BODY, G, bevel=0.0)
grille(P, "eng", (0.0, 1.45, 1.59), 1.0, 1.20, G, n=8, mat=BODY)
for sx in (1, -1):
    grille(P, "lv%d" % sx, (sx * 0.90, 1.70, 1.59), 0.45, 1.30, G, n=8, mat=BODY)
    acyl("%s_exh%d" % (P, sx), 0.09, 0.28, (sx * 0.60, HL - 0.15, 1.20), "M_Exhaust", G, rot=(0.9, 0, 0), seg=10)
tools_set(P, "R", (-1.36, 0.6, 1.02), G)

# ---------------------------------------------------------------- cast turret (100 mm)
# r9: per the factory drawing - long heavy front cheeks (1.38 m ahead of the ring centre), short rear bustle (1.06 m),
# 2.26 m wide, roof at 2.50 m; the sides fall in strongly near the roof (cast).
tl = [(1.55, superellipse(1.13, 1.38, 1.06, n=30, e=2.4, y0=TC.y)),
      (1.95, superellipse(1.12, 1.36, 1.05, n=30, e=2.4, y0=TC.y)),
      (2.25, superellipse(1.02, 1.22, 0.98, n=30, e=2.3, y0=TC.y)),
      (2.44, superellipse(0.86, 1.02, 0.86, n=30, e=2.2, y0=TC.y)),
      (2.52, superellipse(0.70, 0.80, 0.72, n=30, e=2.1, y0=TC.y))]
turret_loft(P + "_turret", tl, BODY, GT, bevel=0.0, smooth=True)
cupola_ru(P, "cmd", (0.50, TC.y + 0.30, 2.47), 0.34, GT, mat=BODY, h=0.20)
hatch(P, "ld", (-0.45, TC.y + 0.20, 2.52), 0.28, GT, mat=BODY)
# DShK on the loader's hatch ring
rod(P + "_dshk_post", Vector((-0.45, TC.y + 0.05, 2.56)), Vector((-0.45, TC.y + 0.05, 2.95)), 0.03, "M_Dark", GT, seg=8)
rod(P + "_dshk_br", Vector((-0.45, TC.y - 0.85, 3.0)), Vector((-0.45, TC.y + 0.05, 3.0)), 0.022, "M_Dark", GT, seg=8)
rod(P + "_dshk_rc", Vector((-0.45, TC.y + 0.05, 3.0)), Vector((-0.45, TC.y + 0.45, 3.0)), 0.05, "M_Dark", GT, seg=8)
acyl(P + "_dshk_mb", 0.035, 0.08, (-0.45, TC.y - 0.88, 3.0), "M_Dark", GT, axis=1, seg=8)
abox(P + "_dshk_box", (0.10, 0.18, 0.14), (-0.33, TC.y + 0.20, 3.0), "M_DarkGreen", GT, bevel=0.0)
for sx in (1, -1):
    acyl("%s_pport%d" % (P, sx), 0.05, 0.05, (sx * 1.10, TC.y + 0.35, 2.00), BODY, GT, axis=0, seg=10)
    lift_hooks(P, "t%d" % sx, [(sx * 0.80, TC.y - 0.60, 2.45), (sx * 0.72, TC.y + 0.60, 2.47)], GT)
abox(P + "_tbal", (0.30, 0.20, 0.25), (0.0, TC.y + 1.08, 1.95), BODY, GT, bevel=0.03)        # rear DT ball housing
antenna(P, "ant", Vector((0.80, TC.y + 0.55, 2.40)), 2.2, GT)

# ---------------------------------------------------------------- mantlet + D-25T
abox(P + "_mant", (1.15, 0.30, 0.66), (0, TC.y - 1.42, TRN.z), BODY, GG, bevel=0.10)
lathe_y(P + "_mantc", [(0.0, 0.0), (0.0, 0.26), (0.18, 0.22), (0.36, 0.17), (0.40, 0.0)], (0, TC.y - 1.56, TRN.z), [BODY], GG, seg=18)
acyl(P + "_coax", 0.022, 0.10, (0.30, TC.y - 1.60, TRN.z), "M_Dark", GG, axis=1, seg=8)
acyl(P + "_sight", 0.035, 0.08, (-0.28, TC.y - 1.60, TRN.z + 0.10), "M_Lens", GG, axis=1, seg=8, dirt=False)
gun_simple(P, GB, TC.y - 1.90, MUZ_Y + 0.45, TRN.z, 0.075, 0.105, collar=False, steps=[(0.25, 0.092)], mat=BODY)
brake_de(P, GB, MUZ_Y + 0.45, TRN.z, 0.13, 0.45, mat=BODY)

auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
ww2_decal(P + "_slL", (1.13, TC.y + 0.05, 1.85), (1, 0, 0.05), (0, 0, 1), 1.00, "ru_berlin", GT)
ww2_decal(P + "_slR", (-1.13, TC.y + 0.05, 1.85), (-1, 0, 0.05), (0, 0, 1), 1.00, "ru_berlin", GT)
ww2_decal(P + "_band", (0.0, TC.y - 0.35, 2.525), (0, 0, 1), (0, -1, 0), 0.9, "band_white", GT)       # air-recognition stripe
weather(gen_weather([(0.6, HL, 1.2), (-0.6, HL, 1.2)], 0.6))
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_is2.glb", "/home/user/models/rw_is2.blend", "RW_IS2")
