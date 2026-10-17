# KV-2 (1941, MT-2 turret) — real dimensions.
# Length 6.95 m (hull 6.75), width 3.32 m, height 3.24 m, clearance 0.43 m. 6 x 600 mm steel road wheels/side,
# 3 return rollers, REAR sprocket, 700 mm tracks pitch 160 mm. 152 mm M-10T howitzer (L/24.3), hull + turret-rear DT.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "KV2"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
BODY = "M_Green4BO"
HL = 6.75 / 2
TWD = 0.70
TX = 3.32 / 2 - TWD / 2          # 1.31
RR = 0.30
TH = 0.06
ZW = RR + TH
ROAD = [(-2.05 + i * 0.82, ZW) for i in range(6)]
SPK = (2.88, 0.70)
IDL = (-2.98, 0.64)
ROLL = [(1.40, 0.98), (0.0, 1.0), (-1.40, 0.98)]
TC = Vector((0, -0.38, 1.60))
TRN = Vector((0, -1.43, 2.32))
MUZ_Y = -3.70                    # r9: muzzle 0.33 m ahead of the hull (drawing)

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = ww2_gear(P, side, x, ROAD, RR, "steel", 0.14, SPK, 0.33, IDL, 0.32, ROLL, 0.12, TWD, 0.160, TH,
                               mat=BODY, twin_gap=0.10, arm_grp=G, arm_len=0.25, teeth=16, wseg=16, spk_gap=0.10)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
for sx in (1, -1):
    acyl("%s_fdrv%d" % (P, sx), 0.25, 0.18, (sx * 1.02, SPK[0], SPK[1]), BODY, G, axis=0, seg=14)
    rod("%s_idla%d" % (P, sx), Vector((sx * 0.96, IDL[0] + 0.22, IDL[1] + 0.05)), Vector((sx * (TX - 0.08), IDL[0], IDL[1])), 0.06, BODY, G, seg=8)
    for y, z in ROLL:
        rod("%s_rrs%d_%d" % (P, sx, int(y * 10)), Vector((sx * 0.96, y, z)), Vector((sx * (TX - 0.05), y, z)), 0.035, BODY, G, seg=6)

# ---------------------------------------------------------------- hull (75 mm rolled plates, bolted/welded)
lower = sym(box_pts(0, 0.96, -2.95, 3.05, 0.43, 1.10)) + sym([(0.96, -HL, 0.95), (0.96, HL, 0.95)])
chull(P + "_lower", lower, BODY, G, bevel=0.02)
upper = sym([(1.60, -2.98, 1.05), (1.60, 3.00, 1.05), (1.60, -HL, 0.95), (1.60, -2.95, 1.22), (1.60, -2.72, 1.60),
             (1.60, 2.70, 1.60), (1.60, HL, 1.02)])
chull(P + "_upper", upper, BODY, G, bevel=0.025)
abox(P + "_visor", (0.36, 0.12, 0.16), (0.35, -2.80, 1.42), BODY, G, rot=(-0.1, 0, 0), bevel=0.02)
abox(P + "_visslit", (0.24, 0.01, 0.03), (0.35, -2.865, 1.42), "M_Bore", G, bevel=0.0, dirt=False)
ball_mount(P, "hull", (-0.62, -2.80, 1.40), (0, -1, -0.05), G, r=0.12, mat=BODY, soviet=True)
headlamp(P, "f", (1.20, -3.05, 1.30), G, mat=BODY)
for sx in (1, -1):
    abox("%s_fend%d" % (P, sx), (0.06, 6.0, 0.06), (sx * 1.60, 0.0, 1.03), BODY, G, bevel=0.0)
    rod("%s_ftow%d" % (P, sx), Vector((sx * 0.70, -HL + 0.05, 0.80)), Vector((sx * 0.70, -HL - 0.15, 0.80)), 0.045, "M_Steel", G, seg=8)
    rod("%s_rtow%d" % (P, sx), Vector((sx * 0.70, HL - 0.05, 0.80)), Vector((sx * 0.70, HL + 0.15, 0.80)), 0.045, "M_Steel", G, seg=8)
    abox("%s_box%d" % (P, sx), (0.36, 1.0, 0.30), (sx * 1.40, 1.85, 1.20), BODY, G, bevel=0.02)       # fender stowage boxes
    rod("%s_cab%d" % (P, sx), Vector((sx * 1.61, -2.3, 1.40)), Vector((sx * 1.61, 1.2, 1.40)), 0.018, "M_Steel", G, seg=6)
hatch(P, "drv", (0.0, -2.20, 1.62), 0.28, G, mat=BODY)
abox(P + "_roof", (2.6, 1.8, 0.04), (0, 1.75, 1.62), BODY, G, bevel=0.0)
grille(P, "eng", (0.0, 1.70, 1.65), 1.10, 1.00, G, n=8, mat=BODY)
for sx in (1, -1):
    grille(P, "lv%d" % sx, (sx * 0.95, 1.95, 1.65), 0.45, 1.20, G, n=8, mat=BODY)
    acyl("%s_exh%d" % (P, sx), 0.09, 0.30, (sx * 0.55, HL - 0.15, 1.30), "M_Exhaust", G, rot=(0.9, 0, 0), seg=10)
abox(P + "_trh", (1.1, 0.5, 0.05), (0, 2.85, 1.45), BODY, G, rot=(0.7, 0, 0), bevel=0.01)
tools_set(P, "L", (1.40, -1.2, 1.08), G)

# ---------------------------------------------------------------- MT-2 turret (75 mm, 1.66 m tall box)
# r9 (per the 1940/41 drawings): plan is an elongated hexagon - 1.25 m front plate, cheek plates angled in from 0.75 m
# behind the face, 1.96 m wide parallel sides, vertical rear with a chamfered rear roof edge.
tp = sym([(0.63, TC.y - 1.30, 1.60), (0.63, TC.y - 1.26, 3.18), (0.98, TC.y - 0.55, 1.60), (0.98, TC.y - 0.52, 3.18),
          (0.98, TC.y + 1.42, 1.60), (0.98, TC.y + 1.42, 3.02), (0.98, TC.y + 1.28, 3.18)])
chull(P + "_turret", tp, BODY, GT, bevel=0.03)
for sx in (1, -1):              # welded/bolted plate joints of the cheek plates
    rod("%s_cheek%d" % (P, sx), Vector((sx * 0.985, TC.y - 0.54, 1.66)), Vector((sx * 0.985, TC.y - 0.54, 3.14)), 0.015, BODY, GT, seg=5)
acyl(P + "_ring", 0.98, 0.10, (TC.x, TC.y, 1.62), BODY, GT, seg=32)
abox(P + "_tdoor", (0.75, 0.05, 0.75), (0, TC.y + 1.44, 2.30), BODY, GT, bevel=0.02)
ball_mount(P, "trear", (0, TC.y + 1.46, 2.35), (0, 1, 0), GT, r=0.10, mat=BODY, soviet=True)
hatch(P, "tt", (0.0, TC.y + 0.60, 3.20), 0.35, GT, mat=BODY)
for sx in (1, -1):
    periscope(P, "pt%d" % sx, Vector((sx * 0.55, TC.y - 0.20, 3.19)), GT)
    abox("%s_tvis%d" % (P, sx), (0.04, 0.24, 0.08), (sx * 0.99, TC.y - 0.10, 2.55), BODY, GT, bevel=0.0)
    acyl("%s_pport%d" % (P, sx), 0.05, 0.05, (sx * 0.99, TC.y + 0.70, 2.30), BODY, GT, axis=0, seg=10)
    lift_hooks(P, "t%d" % sx, [(sx * 0.62, TC.y - 1.0, 3.18), (sx * 0.85, TC.y + 1.2, 3.18)], GT)
for k in range(10):            # bolt heads along the turret roof edges
    for sx in (1, -1):
        acyl("%s_bolt%d_%d" % (P, k, sx), 0.025, 0.03, (sx * 0.96, TC.y - 0.45 + k * 0.17, 3.19), "M_Dark", GT, seg=6, dirt=False)

# ---------------------------------------------------------------- mantlet + M-10T
abox(P + "_mant", (1.08, 0.30, 0.98), (0, TC.y - 1.44, TRN.z - 0.02), BODY, GG, bevel=0.07)     # cast mantlet box
acyl(P + "_sleeve", 0.20, 0.28, (0, TC.y - 1.70, TRN.z), BODY, GG, axis=1, seg=18)
for sx in (1, -1):
    for sz in (1, -1):
        acyl("%s_mbolt%d%d" % (P, sx, sz), 0.03, 0.03, (sx * 0.44, TC.y - 1.60, TRN.z + sz * 0.38), "M_Dark", GG, axis=1, seg=6, dirt=False)
for sx in (1, -1):                       # recoil cylinders above / below the barrel
    acyl("%s_rec%d" % (P, sx), 0.09, 0.42, (0, TC.y - 1.90, TRN.z + sx * 0.26), BODY, GG, axis=1, seg=12)
acyl(P + "_sight", 0.04, 0.08, (0.40, TC.y - 1.60, TRN.z + 0.24), "M_Lens", GG, axis=1, seg=8, dirt=False)
gun_simple(P, GB, TC.y - 1.70, MUZ_Y, TRN.z, 0.105, 0.135, collar=True, mat=BODY)

auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
ww2_decal(P + "_slL", (0.99, TC.y + 0.40, 2.70), (1, 0, 0), (0, 0, 1), 1.50, "ru_stalin", GT)
ww2_decal(P + "_slR", (-0.99, TC.y + 0.40, 2.70), (-1, 0, 0), (0, 0, 1), 1.55, "ru_stalin", GT)
ww2_decal(P + "_numL", (0.99, TC.y + 0.40, 2.15), (1, 0, 0), (0, 0, 1), 0.50, "ru_num_335", GT)
ww2_decal(P + "_numR", (-0.99, TC.y + 0.40, 2.15), (-1, 0, 0), (0, 0, 1), 0.50, "ru_num_335", GT)
weather(gen_weather([(0.55, HL, 1.3), (-0.55, HL, 1.3)], 0.6))
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_kv2.glb", "/home/user/models/rw_kv2.blend", "RW_KV2")
