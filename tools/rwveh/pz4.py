# Pz.Kpfw. IV Ausf. H (1943-44) — real dimensions.
# Hull 5.92 m (7.02 with gun), width 2.88 m (3.29 over Schuerzen), height 2.68 m, clearance 0.40 m.
# 8 twin road wheels 470 mm on 4 leaf-spring bogies, 4 return rollers, FRONT sprocket, Kgs 61/400/120 tracks (99 links).
# 7.5 cm KwK 40 L/48 (3.6 m barrel, ball double-baffle brake), coax + hull MG34, 5 mm Schuerzen on hull and turret.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "PZ4"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
BODY = "M_Gelb"
HL = 5.92 / 2
TWD = 0.40
TX = 2.88 / 2 - TWD / 2          # 1.24
RR = 0.235
TH = 0.04
ZW = RR + TH
ROAD = []
for c in (-1.53, -0.51, 0.51, 1.53):
    ROAD += [(c - 0.265, ZW), (c + 0.265, ZW)]
SPK = (-2.56, 0.64)
IDL = (2.60, 0.56)
ROLL = [(1.80, 0.86), (0.62, 0.88), (-0.58, 0.88), (-1.72, 0.86)]   # rear -> front
TC = Vector((0.0, -0.45, 1.70))
TRN = Vector((0, -1.36, 2.05))
MUZ_Y = -HL - (7.02 - 5.92)      # -4.06

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = ww2_gear(P, side, x, ROAD, RR, "rubber", 0.10, SPK, 0.33, IDL, 0.27, ROLL, 0.11, TWD, 0.120, TH,
                               mat=BODY, twin_gap=0.07, teeth=20, wseg=14, spk_gap=0.08)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
# leaf-spring bogies (one per wheel pair) + bump stops
for sx in (1, -1):
    for b, c in enumerate((-1.53, -0.51, 0.51, 1.53)):
        abox("%s_bog%d_%d" % (P, sx, b), (0.16, 0.62, 0.20), (sx * (TX - 0.23), c, ZW + 0.06), BODY, G, bevel=0.03)
        abox("%s_leaf%d_%d" % (P, sx, b), (0.10, 0.55, 0.06), (sx * (TX - 0.23), c, ZW + 0.20), "M_Dark", G, bevel=0.0)
        abox("%s_bump%d_%d" % (P, sx, b), (0.10, 0.10, 0.12), (sx * (TX - 0.23), c, ZW + 0.36), "M_Dark", G, bevel=0.0)
    for y, z in ROLL:
        rod("%s_rrs%d_%d" % (P, sx, int(y * 10)), Vector((sx * 0.98, y, z)), Vector((sx * (TX - 0.05), y, z)), 0.03, BODY, G, seg=6)
    acyl("%s_fdrv%d" % (P, sx), 0.22, 0.18, (sx * 1.03, SPK[0], SPK[1]), BODY, G, axis=0, seg=14)
    rod("%s_idla%d" % (P, sx), Vector((sx * 0.98, IDL[0] - 0.2, IDL[1] + 0.08)), Vector((sx * (TX - 0.04), IDL[0], IDL[1])), 0.05, BODY, G, seg=8)

# ---------------------------------------------------------------- hull
low = [(-2.66, 0.40), (-HL, 0.66), (-HL, 0.98), (-2.48, 1.13), (2.75, 1.13), (2.90, 0.62), (2.72, 0.40)]
prism_x(P + "_lower", low, 1.00, BODY, G, bevel=0.02)
sup = [(-2.46, 1.0), (-2.44, 1.72), (2.05, 1.72), (2.95, 1.62), (2.95, 1.0)]
prism_x(P + "_super", sup, 1.18, BODY, G, bevel=0.02)
abox(P + "_drvplate", (1.40, 0.08, 0.62), (0.40, -2.48, 1.42), BODY, G, bevel=0.01)       # 80 mm driver plate (left)
abox(P + "_visor", (0.26, 0.10, 0.14), (0.62, -2.54, 1.52), BODY, G, bevel=0.02)
abox(P + "_visslit", (0.18, 0.01, 0.025), (0.62, -2.595, 1.52), "M_Bore", G, bevel=0.0, dirt=False)
ball_mount(P, "hull", (-0.62, -2.47, 1.48), (0, -1, 0), G, r=0.11, mat=BODY)
for sx in (1, -1):        # fenders over the full length + front mud flaps
    abox("%s_fend%d" % (P, sx), (0.44, 5.6, 0.01), (sx * 1.22, 0.0, 1.01), BODY, G, bevel=0.0)
    tilted_box("%s_ffl%d" % (P, sx), (sx * TX, -2.80, 1.01), (sx * TX, -3.05, 0.86), 0.42, 0.01, BODY, G)
headlamp(P, "notek", (1.32, -2.55, 1.06), G, mat=BODY, notek=True)
# hull roof
for sx, t in ((1, "drv"), (-1, "rad")):
    abox("%s_hat_%s" % (P, t), (0.52, 0.62, 0.05), (sx * 0.55, -1.95, 1.75), BODY, G, bevel=0.01)
    acyl("%s_hhinge_%s" % (P, t), 0.025, 0.5, (sx * 0.55, -2.25, 1.77), "M_Steel", G, axis=0, seg=6)
abox(P + "_brake", (1.90, 0.40, 0.08), (0, -2.25, 1.73), BODY, G, bevel=0.01)              # brake access hatches
abox(P + "_deck", (2.20, 1.70, 0.04), (0, 1.15, 1.74), BODY, G, bevel=0.0)
grille(P, "eng", (0.0, 1.10, 1.77), 0.9, 0.8, G, n=7, mat=BODY)
for sx in (1, -1):
    grille(P, "side%d" % sx, (sx * 0.85, 1.25, 1.77), 0.4, 1.2, G, n=8, mat=BODY)
    acyl("%s_wheelsp%d" % (P, sx), 0.235, 0.10, (sx * 1.25, -2.2, 1.12), BODY, G, axis=0, seg=14)   # spare road wheel on the fender
lift_hooks(P, "h", [(sx * 1.05, y, 1.72) for sx in (1, -1) for y in (-2.2, 2.5)], G)
# rear: big transverse muffler + small auxiliary generator muffler, tow couplings
acyl(P + "_muf", 0.15, 1.10, (0.15, 3.12, 0.85), "M_Exhaust", G, axis=0, seg=14)
acyl(P + "_mufaux", 0.07, 0.38, (-0.75, 3.06, 1.05), "M_Exhaust", G, axis=0, seg=10)
for sx in (1, -1):
    rod("%s_mufp%d" % (P, sx), Vector((sx * 0.40 + 0.15, 3.12, 0.85)), Vector((sx * 0.40 + 0.15, 3.12, 0.62)), 0.04, "M_Exhaust", G, seg=8)
    rod("%s_tow%d" % (P, sx), Vector((sx * 0.70, 2.85, 0.55)), Vector((sx * 0.70, 3.10, 0.55)), 0.045, "M_Steel", G, seg=8)
abox(P + "_jack", (0.18, 0.16, 0.45), (-0.30, 2.98, 1.48), "M_Dark", G, rot=(0, math.pi / 2, 0), bevel=0.01)
tools_set(P, "L", (1.12, 0.3, 1.04), G)
tools_set(P, "R", (-1.12, 0.6, 1.04), G)
for sx in (1, -1):
    rod("%s_cab%d" % (P, sx), Vector((sx * 1.19, -2.2, 1.40)), Vector((sx * 1.19, 2.2, 1.40)), 0.018, "M_Steel", G, seg=6)
spare_links(P, "glac", (0.0, -2.66, 1.08), 7, Vector((0.26, 0, 0)), (0, -0.55, 0.85), G, w=0.40, pitch=0.12)
spare_links(P, "nose", (0.0, -2.96, 0.84), 6, Vector((0.30, 0, 0)), (0, -1, 0), G, w=0.40, pitch=0.12)
# Schuerzen: 5 mm side skirt panels hung on a rail from the fender
for sx in (1, -1):
    rod("%s_skrail%d" % (P, sx), Vector((sx * 1.50, -1.95, 1.62)), Vector((sx * 1.50, 2.30, 1.62)), 0.02, "M_Steel", G, seg=6)
    for k in range(6):
        y = -1.68 + k * 0.72
        abox("%s_skirt%d_%d" % (P, sx, k), (0.006, 0.70, 0.96), (sx * 1.635, y, 1.15), BODY, G, rot=(0, sx * 0.05, 0), bevel=0.0)
        rod("%s_skhang%d_%d" % (P, sx, k), Vector((sx * 1.50, y, 1.62)), Vector((sx * 1.63, y, 1.62)), 0.012, "M_Steel", G, seg=6)
        rod("%s_skbr%d_%d" % (P, sx, k), Vector((sx * 1.18, y, 1.40)), Vector((sx * 1.50, y, 1.62)), 0.012, "M_Steel", G, seg=6)
antenna(P, "ant", Vector((-1.05, 2.4, 1.74)), 2.0, G)

# ---------------------------------------------------------------- turret (25 deg sloped sides, 50 mm front) + skirts
tz0, tz1 = 1.72, 2.40
lv0 = superellipse(0.92, 0.90, 1.15, n=24, e=5.0, y0=TC.y)
lv1 = superellipse(0.72, 0.78, 1.00, n=24, e=5.0, y0=TC.y)
turret_loft(P + "_turret", [(tz0, lv0), (tz1, lv1)], BODY, GT, bevel=0.02)
abox(P + "_tface", (0.95, 0.10, 0.58), (0, TC.y - 0.86, 2.04), BODY, GT, bevel=0.02)
cupola_de(P, "cmd", (0.0, TC.y + 0.80, tz1), 0.30, GT, mat=BODY, blocks=5, h=0.25, aa_mg=True)
for sx in (1, -1):
    abox("%s_sdoor%d" % (P, sx), (0.04, 0.55, 0.50), (sx * 0.82, TC.y + 0.25, 2.05), BODY, GT, rot=(0, -sx * 0.40, 0), bevel=0.01)
    abox("%s_tvis%d" % (P, sx), (0.04, 0.20, 0.08), (sx * 0.77, TC.y - 0.40, 2.20), BODY, GT, rot=(0, -sx * 0.40, 0), bevel=0.0)
    abox("%s_tsk%d" % (P, sx), (0.006, 1.75, 0.62), (sx * 1.20, TC.y + 0.30, 2.08), BODY, GT, bevel=0.0)
    for y in (TC.y - 0.35, TC.y + 0.9):
        rod("%s_tskb%d_%d" % (P, sx, int(y * 10)), Vector((sx * 0.86, y, 2.25)), Vector((sx * 1.20, y, 2.25)), 0.015, "M_Steel", GT, seg=6)
abox(P + "_tskr", (2.40, 0.006, 0.62), (0, TC.y + 1.45, 2.08), BODY, GT, bevel=0.0)
for sx in (1, -1):
    abox("%s_tskc%d" % (P, sx), (0.006, 0.30, 0.62), (sx * 1.20, TC.y + 1.32, 2.08), BODY, GT, rot=(0, 0, sx * 0.5), bevel=0.0)
rod(P + "_tskrb", Vector((0, TC.y + 1.15, 2.20)), Vector((0, TC.y + 1.45, 2.20)), 0.015, "M_Steel", GT, seg=6)
acyl(P + "_tvent", 0.08, 0.06, (0.0, TC.y - 0.20, tz1 + 0.03), BODY, GT, seg=12)
abox(P + "_tsig", (0.10, 0.10, 0.08), (-0.35, TC.y - 0.35, tz1 + 0.04), BODY, GT, bevel=0.01)   # signal port

# ---------------------------------------------------------------- mantlet + gun
abox(P + "_mant", (0.80, 0.16, 0.44), (0, TC.y - 0.98, TRN.z), BODY, GG, bevel=0.04)
acyl(P + "_mantc", 0.15, 0.24, (0, TC.y - 1.10, TRN.z), BODY, GG, axis=1, seg=14)
acyl(P + "_coax", 0.025, 0.10, (0.24, TC.y - 1.06, TRN.z - 0.02), "M_Dark", GG, axis=1, seg=8)
acyl(P + "_sight", 0.03, 0.08, (-0.22, TC.y - 1.06, TRN.z + 0.04), "M_Lens", GG, axis=1, seg=8, dirt=False)
gun_simple(P, GB, TC.y - 1.20, MUZ_Y + 0.30, TRN.z, 0.055, 0.075, collar=False, steps=[(0.45, 0.066)], mat=BODY)
brake_de(P, GB, MUZ_Y + 0.30, TRN.z, 0.095, 0.30, mat=BODY)

auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
ww2_decal(P + "_crossL", (1.645, -0.6, 1.20), (1, 0, 0), (0, 0, 1), 0.50, "cross", G)
ww2_decal(P + "_crossR", (-1.645, -0.6, 1.20), (-1, 0, 0), (0, 0, 1), 0.50, "cross", G)
ww2_decal(P + "_numL", (1.207, TC.y + 0.35, 2.10), (1, 0, 0), (0, 0, 1), 0.66, "num_712", GT)
ww2_decal(P + "_numR", (-1.207, TC.y + 0.35, 2.10), (-1, 0, 0), (0, 0, 1), 0.66, "num_712", GT)
ww2_decal(P + "_crossB", (0, 3.03, 1.30), (0, 1, 0), (0, 0, 1), 0.36, "cross", G)
weather(gen_weather([(0.15, 3.12, 0.85)], 0.6))
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_pz4.glb", "/home/user/models/rw_pz4.blend", "RW_PZ4")
