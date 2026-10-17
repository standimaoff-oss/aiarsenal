# Pz.Kpfw. VIII Maus (V2 prototype, 1944) — real dimensions.
# Hull 9.03 m (10.09 with gun), width 3.67 m, height 3.63 m, clearance 0.50 m, 188 t.
# 1100 mm tracks, REAR sprockets (petrol-electric drive), 24 x 550 mm wheels/side in 12 twin stations hidden behind
# 100 mm side skirts. Turret 220 mm front: 12.8 cm KwK 44 L/55 + coaxial 7.5 cm KwK 44 L/36.5 + MG34.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "MAUS"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
BODY = "M_Gelb"
HL = 9.03 / 2
TWD = 1.10
TX = 1.725 - TWD / 2             # 1.175 (track outer edge just inside the 100 mm skirt)
RR, TH = 0.275, 0.07
ZW = RR + TH
ROAD = [(-3.60 + i * 0.655, ZW) for i in range(12)]
SPK = (4.02, 0.86)
IDL = (-4.05, 0.80)
TC = Vector((0, 1.25, 2.20))      # r9: turret ring well aft of the centre (engine bay in the middle, per the drawings)
TRN = Vector((0, -0.62, 2.80))
MUZ_Y = -HL - (10.09 - 9.03)     # -5.575

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = ww2_gear(P, side, x, ROAD, RR, "steel", 0.16, SPK, 0.45, IDL, 0.42, [], 0.1, TWD, 0.160, TH,
                               mat=BODY, twin_gap=0.22, teeth=16, wseg=12, spk_gap=0.20, hidden=True)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))

# ---------------------------------------------------------------- hull (r9, per the factory drawings)
# beak nose: upper glacis 200 mm @55 deg and lower glacis 200 mm @55 deg meeting at z 1.25; the wide upper hull
# with 180 mm sides carries the 100 mm skirts down to 0.40 m, so only the bottom track run is visible from the side.
NOSE_Z = 1.25
lower = sym([(0.60, -HL + 0.95, 0.50), (0.60, 4.15, 0.50), (0.60, -HL, NOSE_Z), (0.60, HL, 1.30), (0.60, HL - 0.25, 0.62)])
chull(P + "_lower", lower, BODY, G, bevel=0.02)
upper = sym([(1.835, -HL + 0.98, 0.42), (1.835, HL - 0.30, 0.42), (1.835, -HL, NOSE_Z), (1.835, -3.16, 2.20),
             (1.835, 4.05, 2.20), (1.835, HL, 1.55), (1.835, HL, 0.95)])
chull(P + "_upper", upper, BODY, G, bevel=0.03)
for sx in (1, -1):               # skirt plate joints, bolt rows, final drive housings at the rear
    for k in range(5):
        abox("%s_sjoint%d_%d" % (P, sx, k), (0.012, 0.02, 1.05), (sx * 1.842, -2.6 + k * 1.55, 1.0), BODY, G, bevel=0.0, dirt=False)
    for k in range(14):
        acyl("%s_sbolt%d_%d" % (P, sx, k), 0.03, 0.03, (sx * 1.842, -3.9 + k * 0.60, 1.55), "M_Dark", G, axis=0, seg=6, dirt=False)
        acyl("%s_sboltl%d_%d" % (P, sx, k), 0.025, 0.03, (sx * 1.842, -3.7 + k * 0.58, 0.55), "M_Dark", G, axis=0, seg=6, dirt=False)
    acyl("%s_fdrv%d" % (P, sx), 0.32, 0.20, (sx * 0.62, SPK[0], SPK[1]), BODY, G, axis=0, seg=16)
    rod("%s_ftow%d" % (P, sx), Vector((sx * 0.95, -HL + 0.42, 0.98)), Vector((sx * 0.95, -HL + 0.10, 0.98)), 0.06, "M_Steel", G, seg=8)
    rod("%s_rtow%d" % (P, sx), Vector((sx * 0.95, HL - 0.05, 1.0)), Vector((sx * 0.95, HL + 0.18, 1.0)), 0.06, "M_Steel", G, seg=8)
    abox("%s_mud%d" % (P, sx), (0.55, 0.40, 0.04), (sx * 1.25, -HL + 0.55, 0.70), BODY, G, rot=(0.6, 0, 0), bevel=0.01)
gl_n = Vector((0, -0.95, 1.08)).normalized()
def glacis(y, x):
    return Vector((x, y, NOSE_Z + (y + HL) * (2.20 - NOSE_Z) / (HL - 3.16)))
for sx in (1, -1):
    headlamp(P, "f%d" % sx, glacis(-4.30, sx * 1.45) + Vector((0, -0.05, 0.12)), G, mat=BODY)
abox(P + "_dvis", (0.30, 0.08, 0.10), tuple(glacis(-3.55, 0.75) + gl_n * 0.04), BODY, G, rot=(math.atan2(-gl_n.y, gl_n.z), 0, 0), bevel=0.02)
# hull roof: driver + radio hatches with periscopes, huge engine and cooling grilles, central exhaust, fording snorkel
for sx, t in ((1, "drv"), (-1, "rad")):
    hatch(P, t, (sx * 0.75, -3.0, 2.21), 0.30, G, mat=BODY)
    periscope(P, "p" + t, Vector((sx * 0.75, -3.30, 2.21)), G)
abox(P + "_roof", (3.4, 2.4, 0.04), (0, -1.9, 2.21), BODY, G, bevel=0.0)
for sx in (1, -1):
    grille(P, "eng%d" % sx, (sx * 0.95, -1.75, 2.24), 1.1, 1.4, G, n=10, mat=BODY)
    grille(P, "cool%d" % sx, (sx * 1.15, 3.35, 2.24), 0.9, 1.0, G, n=8, mat=BODY)
    abox("%s_rexh%d" % (P, sx), (0.28, 0.30, 0.28), (sx * 0.45, 3.60, 2.35), "M_Exhaust", G, bevel=0.03)
acyl(P + "_snork", 0.30, 0.10, (0.0, 3.55, 2.26), BODY, G, seg=20)
lift_hooks(P, "h", [(sx * 1.6, y, 2.20) for sx in (1, -1) for y in (-3.2, 4.0)], G)
tools_set(P, "L", (1.55, -1.5, 2.23), G)
tools_set(P, "R", (-1.55, -1.5, 2.23), G)
for sx in (1, -1):
    rod("%s_cab%d" % (P, sx), Vector((sx * 1.85, -3.6, 1.80)), Vector((sx * 1.85, 3.6, 1.80)), 0.025, "M_Steel", G, seg=6)

# ---------------------------------------------------------------- turret (r9): long casting, 220 mm bull-nose front that
# curves down from the roof, 200 mm sides inclined 30 deg, 200 mm rear @15 deg, flat 60 mm roof; narrower at the nose.
def maus_plan(hw, yf, yr, nose=0.78, n=10):
    """Plan outline (x, y) of a turret level: rounded nose of half-width hw*nose at yf, straight converging sides,
    rear corners rounded 0.18 m, flat rear at yr."""
    fw = hw * nose
    pts = []
    for i in range(n + 1):                                  # nose half-ellipse
        a = math.pi * i / n
        pts.append((fw * math.cos(a), yf + fw * 0.62 * (1 - math.sin(a))))
    rc = 0.18
    pts.append((-hw, yr - rc - 0.05))
    for i in range(4):
        a = math.pi / 2 * (i / 3)
        pts.append((-(hw - rc) - rc * math.cos(a), yr - rc + rc * math.sin(a)))
    for i in range(4):
        a = math.pi / 2 * (i / 3)
        pts.append(((hw - rc) + rc * math.sin(a), yr - rc + rc * math.cos(a)))
    pts.append((hw, yr - rc - 0.05))
    pts = list(reversed(pts))
    return [(x, y) for (x, y) in pts]
TL = [(2.20, 1.62, -0.72, 3.22), (2.50, 1.50, -0.90, 3.17), (2.85, 1.32, -0.96, 3.10), (3.15, 1.16, -0.86, 3.03),
      (3.40, 1.02, -0.62, 2.98), (3.55, 0.94, -0.30, 2.94)]
tl = [(z, maus_plan(hw, yf + TC.y - 1.25, yr + TC.y - 1.25)) for (z, hw, yf, yr) in TL]
turret_loft(P + "_turret", tl, BODY, GT, bevel=0.0, smooth=True)
abox(P + "_troof", (1.70, 2.60, 0.03), (0, TC.y + 0.25, 3.555), BODY, GT, bevel=0.01)
for sx in (1, -1):              # roof plate weld seams + side plate joints
    rod("%s_tseam%d" % (P, sx), Vector((sx * 0.84, TC.y - 0.95, 3.565)), Vector((sx * 0.84, TC.y + 1.55, 3.565)), 0.012, BODY, GT, seg=5)
for sx in (1, -1):               # stereoscopic rangefinder blisters + pistol ports
    lathe_y("%s_rfb%d" % (P, sx), [(0.0, 0.0), (0.0, 0.16), (0.30, 0.16), (0.30, 0.0)], (sx * 1.04, TC.y - 0.55, 3.38), [BODY], GT, seg=12, axis='X')
    acyl("%s_pport%d" % (P, sx), 0.06, 0.06, (sx * 1.40, TC.y + 0.70, 2.70), BODY, GT, axis=0, seg=10)
    lift_hooks(P, "t%d" % sx, [(sx * 1.0, TC.y - 1.0, 3.56), (sx * 1.0, TC.y + 1.3, 3.56)], GT)
hatch(P, "ld", (-0.55, TC.y + 0.35, 3.58), 0.32, GT, mat=BODY)
hatch(P, "cm", (0.55, TC.y + 0.55, 3.58), 0.32, GT, mat=BODY)
periscope(P, "cm", Vector((0.55, TC.y - 0.05, 3.58)), GT)
periscope(P, "gn", Vector((0.25, TC.y - 0.85, 3.57)), GT)
acyl(P + "_vent", 0.12, 0.06, (-0.20, TC.y - 0.60, 3.60), BODY, GT, seg=14)
abox(P + "_rhatch", (0.80, 0.06, 0.70), (0, TC.y + 1.98, 2.75), BODY, GT, rot=(-0.26, 0, 0), bevel=0.02)        # rear ammo hatch

FY = TC.y - 0.31                 # gun-mount reference (turret nose at the trunnion height)
# ---------------------------------------------------------------- gun mount: 12.8 cm + 7.5 cm (right) + MG34
lathe_y(P + "_mant", [(0.0, 0.0), (0.0, 0.40), (0.25, 0.36), (0.45, 0.22), (0.50, 0.0)], (0.0, FY - 1.72, TRN.z), [BODY], GG, seg=22)
lathe_y(P + "_mant75", [(0.0, 0.0), (0.0, 0.18), (0.20, 0.14), (0.25, 0.0)], (-0.42, FY - 1.70, TRN.z + 0.02), [BODY], GG, seg=16)
acyl(P + "_coax", 0.025, 0.10, (0.36, FY - 1.80, TRN.z + 0.05), "M_Dark", GG, axis=1, seg=8)
acyl(P + "_sight", 0.04, 0.10, (0.30, FY - 1.78, TRN.z + 0.30), "M_Lens", GG, axis=1, seg=8, dirt=False)
gun_simple(P, GB, FY - 2.15, MUZ_Y, TRN.z, 0.090, 0.150, collar=False, steps=[(0.22, 0.125), (0.55, 0.105)], mat=BODY)
gun_simple(P + "75", GB, FY - 1.90, FY - 1.90 - 2.05, TRN.z + 0.02, 0.055, 0.075, x=-0.42, collar=False, mat=BODY)

auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
for sx in (1, -1):
    ww2_decal("%s_cross%d" % (P, sx), (sx * 1.84, -0.60, 1.85), (sx, 0, 0), (0, 0, 1), 0.70, "cross", G)
ww2_decal(P + "_crossB", (0.0, HL - 0.05, 1.10), (0, 1, 0), (0, 0, 1), 0.55, "cross", G)
weather(gen_weather([(0.45, 3.6, 2.4), (-0.45, 3.6, 2.4)], 0.8))
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_maus.glb", "/home/user/models/rw_maus.blend", "RW_MAUS")
