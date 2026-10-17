# Pz.Kpfw. II Ausf. F (1941) — real dimensions.
# Hull 4.81 m, width 2.28 m, height 2.15 m, clearance 0.34 m. 5 x 550 mm road wheels on leaf-spring arms, 4 return
# rollers, FRONT sprocket, Kgs 67/300/90 tracks. 2 cm KwK 30 L/55 + coax MG34, turret cupola with 8 periscopes.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "PZ2"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
BODY = "M_Grau"
HL = 4.81 / 2
TWD = 0.30
TX = 2.28 / 2 - TWD / 2          # 0.99
RR = 0.275
TH = 0.035
ZW = RR + TH
ROAD = [(-1.42 + i * 0.69, ZW) for i in range(5)]
SPK = (-2.02, 0.56)
IDL = (2.12, 0.50)
ROLL = [(1.55, 0.74), (0.62, 0.76), (-0.30, 0.76), (-1.20, 0.74)]
TC = Vector((0.08, -0.30, 1.50))
TRN = Vector((0.08, -1.00, 1.74))
MUZ_Y = -2.42

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = ww2_gear(P, side, x, ROAD, RR, "rubber", 0.08, SPK, 0.27, IDL, 0.24, ROLL, 0.08, TWD, 0.090, TH,
                               mat=BODY, twin_gap=0.05, teeth=20, wseg=14, spk_gap=0.06)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
for sx in (1, -1):
    for i, (y, z) in enumerate(ROAD):          # quarter-elliptic leaf spring arms
        rod("%s_arm%d_%d" % (P, sx, i), Vector((sx * 0.78, y + 0.30, z + 0.10)), Vector((sx * (TX - 0.10), y, z)), 0.04, BODY, G, seg=6)
        abox("%s_leaf%d_%d" % (P, sx, i), (0.08, 0.40, 0.05), (sx * 0.80, y + 0.30, z + 0.20), "M_Dark", G, bevel=0.0)
    for y, z in ROLL:
        rod("%s_rrs%d_%d" % (P, sx, int(y * 10)), Vector((sx * 0.78, y, z)), Vector((sx * (TX - 0.04), y, z)), 0.025, BODY, G, seg=6)
    acyl("%s_fdrv%d" % (P, sx), 0.18, 0.14, (sx * 0.82, SPK[0], SPK[1]), BODY, G, axis=0, seg=14)
    rod("%s_idla%d" % (P, sx), Vector((sx * 0.78, IDL[0] - 0.18, IDL[1] + 0.06)), Vector((sx * (TX - 0.03), IDL[0], IDL[1])), 0.045, BODY, G, seg=8)

# ---------------------------------------------------------------- hull (35 mm front)
low = [(-2.05, 0.34), (-HL, 0.60), (-HL, 0.86), (-2.22, 0.93), (2.25, 0.93), (2.40, 0.55), (2.25, 0.34)]
prism_x(P + "_lower", low, 0.80, BODY, G, bevel=0.02)
sup = [(-2.24, 0.86), (-2.22, 1.50), (1.40, 1.50), (2.30, 1.40), (2.32, 0.86)]
prism_x(P + "_super", sup, 0.98, BODY, G, bevel=0.02)
abox(P + "_visor", (0.30, 0.08, 0.12), (0.48, -2.26, 1.26), BODY, G, bevel=0.02)
abox(P + "_dvisor", (0.30, 0.08, 0.12), (-0.48, -2.26, 1.26), BODY, G, bevel=0.02)   # dummy visor (Ausf. F)
abox(P + "_visslit", (0.20, 0.01, 0.025), (0.48, -2.305, 1.26), "M_Bore", G, bevel=0.0, dirt=False)
for sx in (1, -1):
    abox("%s_fend%d" % (P, sx), (0.34, 4.5, 0.01), (sx * 1.00, 0.0, 0.88), BODY, G, bevel=0.0)
    tilted_box("%s_ffl%d" % (P, sx), (sx * TX, -2.25, 0.88), (sx * TX, -2.50, 0.74), 0.32, 0.01, BODY, G)
    rod("%s_tow%d" % (P, sx), Vector((sx * 0.55, HL - 0.05, 0.55)), Vector((sx * 0.55, HL + 0.12, 0.55)), 0.035, "M_Steel", G, seg=8)
headlamp(P, "f", (0.85, -2.20, 1.05), G, mat=BODY)
headlamp(P, "notek", (-0.85, -2.25, 0.96), G, mat=BODY, notek=True)
for sx, t in ((1, "drv"), (-1, "brk")):
    abox("%s_hat_%s" % (P, t), (0.48, 0.42, 0.04), (sx * 0.45, -1.85, 1.52), BODY, G, bevel=0.01)
abox(P + "_deck", (1.80, 1.50, 0.04), (0, 1.25, 1.52), BODY, G, bevel=0.0)
grille(P, "eng", (0.0, 1.20, 1.55), 0.9, 0.9, G, n=7, mat=BODY)
for sx in (1, -1):
    grille(P, "side%d" % sx, (sx * 0.75, 1.30, 1.55), 0.30, 1.00, G, n=7, mat=BODY)
acyl(P + "_muf", 0.10, 0.60, (0.25, HL + 0.06, 0.95), "M_Exhaust", G, axis=0, seg=12)
tools_set(P, "L", (0.98, 0.4, 0.91), G)
abox(P + "_jack", (0.14, 0.14, 0.40), (-0.55, 2.0, 0.95), "M_Dark", G, bevel=0.01)
antenna(P, "ant", Vector((-0.80, 1.80, 1.52)), 1.6, G)

# ---------------------------------------------------------------- turret (30 mm, offset left)
tl = [(1.50, superellipse(0.72, 0.80, 0.82, n=20, e=4.0, x0=TC.x, y0=TC.y)),
      (2.00, superellipse(0.62, 0.70, 0.75, n=20, e=4.0, x0=TC.x, y0=TC.y))]
turret_loft(P + "_turret", tl, BODY, GT, bevel=0.015)
lathe_y(P + "_cup", [(0.0, 0.25), (0.15, 0.23), (0.15, 0.0), (0.0, 0.0)], (TC.x + 0.05, TC.y + 0.25, 2.0), [BODY], GT, seg=16, axis='Z')
for k in range(8):
    a = 2 * math.pi * k / 8
    abox("%s_cupv%d" % (P, k), (0.09, 0.02, 0.05), (TC.x + 0.05 + math.sin(a) * 0.235, TC.y + 0.25 - math.cos(a) * 0.235, 2.08), "M_Lens", GT, rot=(0, 0, a), bevel=0.0, dirt=False)
for sx in (1, -1):
    abox("%s_tvis%d" % (P, sx), (0.03, 0.16, 0.06), (TC.x + sx * 0.69, TC.y - 0.10, 1.80), BODY, GT, bevel=0.0)
    for k in range(3):
        rod("%s_nbk%d_%d" % (P, sx, k), Vector((TC.x + sx * 0.55, TC.y + 0.55 + k * 0.10, 1.95)), Vector((TC.x + sx * 0.66, TC.y + 0.58 + k * 0.10, 2.06)), 0.035, BODY, GT, seg=8)

# ---------------------------------------------------------------- mantlet + 2 cm KwK 30 + MG34
abox(P + "_mant", (0.58, 0.14, 0.34), (TC.x, TC.y - 0.80, TRN.z), BODY, GG, bevel=0.03)
acyl(P + "_sleeve", 0.06, 0.25, (TC.x - 0.10, TC.y - 0.95, TRN.z), BODY, GG, axis=1, seg=12)
mg34(P, "coax", Vector((TC.x + 0.13, TC.y - 0.92, TRN.z)), (0, -1, 0), GG, L=0.55)
gun_simple(P, GB, TC.y - 1.05, MUZ_Y, TRN.z, 0.022, 0.035, x=TC.x - 0.10, collar=False, mat=BODY)
acyl(P + "_flash", 0.03, 0.10, (TC.x - 0.10, MUZ_Y + 0.05, TRN.z), "M_Dark", GB, axis=1, seg=10)

auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
ww2_decal(P + "_crossL", (0.985, 0.6, 1.20), (1, 0, 0), (0, 0, 1), 0.40, "cross", G)
ww2_decal(P + "_crossR", (-0.985, 0.6, 1.20), (-1, 0, 0), (0, 0, 1), 0.40, "cross", G)
ww2_decal(P + "_numL", (TC.x + 0.705, TC.y + 0.30, 1.72), (1, 0, 0), (0, 0, 1), 0.42, "num_401", GT)
ww2_decal(P + "_numR", (TC.x - 0.705, TC.y + 0.30, 1.72), (-1, 0, 0), (0, 0, 1), 0.42, "num_401", GT)
weather(gen_weather([(0.25, HL, 0.95)], 0.5))
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_pz2.glb", "/home/user/models/rw_pz2.blend", "RW_PZ2")
