# Pz.Kpfw. VI Tiger Ausf. E (mid 1943, "131" of s.Pz.Abt. 504) — real dimensions.
# Hull 6.316 m, width over combat tracks 3.547 m, height 3.00 m, length with gun 8.45 m, clearance 0.47 m.
# Running gear: 8 torsion-bar axles/side, interleaved 800 mm wheels, FRONT drive sprocket, Kgs 63/725/130 tracks.
# Gun 8.8 cm KwK 36 L/56 (4.93 m barrel, double-baffle brake), coax MG34, hull ball MG34, cupola.
# Nodes: GunVehicle contract (as T-90M). Blender: front = -Y, +X = vehicle LEFT.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "TIG"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
BODY = "M_Gelb"
HL = 6.316 / 2                 # half hull length
TWD = 0.725                    # track width
TX = 3.547 / 2 - TWD / 2       # track centre x = 1.411
RR = 0.40                      # road wheel radius (800 mm)
TH = 0.055
ZW = RR + TH                   # wheel centre height
ROAD = [(-1.84 + i * 0.514, ZW) for i in range(8)]
A = [0.24, -0.04]              # odd axles: outer + inner disc (outward offsets)
B = [0.10, -0.18]              # even axles: middle layers
INTER = [A if i % 2 == 0 else B for i in range(8)]
SPK = (-2.78, 0.80)            # front sprocket
IDL = (2.86, 0.66)             # rear idler
TC = Vector((0, -0.30, 1.86))  # turret ring centre on the hull roof
TRN = Vector((0, -1.36, 2.24)) # gun trunnion
MUZ_Y = -HL - (8.45 - 6.316)   # -5.292

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = ww2_gear(P, side, x, ROAD, RR, "rubber", 0.10, SPK, 0.42, IDL, 0.30, [], 0.1, TWD, 0.130, TH,
                               mat=BODY, interleave=INTER, arm_grp=G, arm_len=0.30, teeth=18, wseg=16, spk_gap=0.12)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))

# ---------------------------------------------------------------- hull
# lower hull between the tracks (half width 0.98): nose plate, glacis deck, belly, rear plate
low = [(-2.86, 0.47), (-HL, 0.78), (-HL, 1.06), (-2.70, 1.16), (3.05, 1.16), (3.10, 0.66), (2.88, 0.47)]
prism_x(P + "_lower", low, 0.98, BODY, G, bevel=0.03)
# superstructure over the tracks (half width 1.57): vertical 100 mm front plate, 80 mm sides, flat roof
sup = [(-2.70, 1.02), (-2.62, 1.86), (2.90, 1.86), (3.06, 1.76), (3.08, 1.02)]
prism_x(P + "_super", sup, 1.57, BODY, G, bevel=0.025)
# interlocked plate ends (visible joints on the front corners) + front track guards
for sx in (1, -1):
    abox("%s_jnt%d" % (P, sx), (0.10, 0.10, 0.84), (sx * 1.57, -2.66, 1.44), BODY, G, bevel=0.01)
    tilted_box("%s_mudg%d" % (P, sx), (sx * TX, -2.70, 1.10), (sx * TX, -3.30, 0.96), TWD + 0.03, 0.01, BODY, G)
    abox("%s_fend%d" % (P, sx), (0.24, 5.5, 0.01), (sx * 1.66, 0.15, 1.03), BODY, G, bevel=0.0)
    abox("%s_rmud%d" % (P, sx), (TWD + 0.03, 0.35, 0.01), (sx * TX, 3.10, 1.08), BODY, G, bevel=0.0)
    acyl("%s_fdrv%d" % (P, sx), 0.30, 0.2, (sx * 1.08, SPK[0], SPK[1]), BODY, G, axis=0, seg=16)   # final drive housing
    rod("%s_idlarm%d" % (P, sx), Vector((sx * 1.0, IDL[0] - 0.2, IDL[1] + 0.06)), Vector((sx * (TX - 0.08), IDL[0], IDL[1])), 0.07, BODY, G, seg=8)
# driver visor (left = +X) and hull ball MG (radio operator, right = -X)
abox(P + "_visor", (0.34, 0.12, 0.16), (0.62, -2.70, 1.62), BODY, G, bevel=0.02)
abox(P + "_visslit", (0.24, 0.01, 0.03), (0.62, -2.765, 1.62), "M_Bore", G, bevel=0.0, dirt=False)
ball_mount(P, "hull", (-0.66, -2.69, 1.56), (0, -1, -0.12), G, r=0.12, mat=BODY)
for sx in (1, -1):
    headlamp(P, "f%d" % sx, (sx * 1.12, -2.70, 1.98), G, mat=BODY)
headlamp(P, "notek", (-1.30, -2.68, 1.60), G, mat=BODY, notek=True)
# hull roof: driver/radio hatches + periscopes, turret ring collar, engine deck
for sx, t in ((1, "drv"), (-1, "rad")):
    hatch(P, t, (sx * 0.62, -2.05, 1.87), 0.27, G, mat=BODY)
    periscope(P, "p" + t, Vector((sx * 0.62, -2.40, 1.87)), G)
acyl(P + "_ring", 1.02, 0.06, (TC.x, TC.y, 1.88), BODY, G, seg=32)
abox(P + "_deck", (2.6, 2.05, 0.04), (0, 1.80, 1.88), BODY, G, bevel=0.0)
for sx in (1, -1):        # radiator grilles and fan covers on the engine deck
    grille(P, "rad%d" % sx, (sx * 1.0, 1.85, 1.91), 0.9, 1.6, G, n=9, mat=BODY)
    acyl("%s_fan%d" % (P, sx), 0.30, 0.05, (sx * 0.55, 2.45, 1.92), BODY, G, seg=20)
    grille(P, "fan%d" % sx, (sx * 0.55, 2.45, 1.96), 0.42, 0.42, G, n=5, mat=BODY)
abox(P + "_engh", (0.9, 1.0, 0.06), (0, 1.75, 1.92), BODY, G, bevel=0.01)       # engine access hatch
lift_hooks(P, "h", [(sx * 1.42, y, 1.86) for sx in (1, -1) for y in (-2.45, 2.75)], G)
# rear plate: two tall Maybach HL 230 mufflers with guards, tow shackles, track jack
for sx in (1, -1):
    acyl("%s_muf%d" % (P, sx), 0.11, 0.62, (sx * 0.55, 3.18, 1.38), "M_Exhaust", G, seg=14)
    acyl("%s_mufg%d" % (P, sx), 0.135, 0.28, (sx * 0.55, 3.18, 1.30), BODY, G, seg=14)
    acyl("%s_mufp%d" % (P, sx), 0.05, 0.18, (sx * 0.55, 3.18, 1.75), "M_Exhaust", G, seg=8)
    rod("%s_tow%d" % (P, sx), Vector((sx * 0.88, 3.08, 0.70)), Vector((sx * 0.88, 3.30, 0.70)), 0.05, "M_Steel", G, seg=8)
    rod("%s_ftow%d" % (P, sx), Vector((sx * 0.80, -3.10, 0.80)), Vector((sx * 0.80, -3.35, 0.80)), 0.05, "M_Steel", G, seg=8)
abox(P + "_jack", (0.2, 0.18, 0.55), (0, 3.18, 1.0), "M_Dark", G, rot=(0, math.pi / 2, 0), bevel=0.01)
acyl(P + "_starter", 0.06, 0.08, (0, 3.10, 0.82), "M_Steel", G, axis=1, seg=10)
# tools + tow cables along the sponson tops, spare track links on the front plate
tools_set(P, "L", (1.30, 0.6, 1.90), G)
tools_set(P, "R", (-1.30, 0.6, 1.90), G)
for sx in (1, -1):
    rod("%s_cab%d" % (P, sx), Vector((sx * 1.59, -2.3, 1.62)), Vector((sx * 1.59, 2.6, 1.62)), 0.022, "M_Steel", G, seg=6)
spare_links(P, "fr", (0.0, -2.70, 1.24), 5, Vector((0.36, 0, 0)), (0, -1, 0.0), G, w=0.32, pitch=0.13)

# ---------------------------------------------------------------- turret (horseshoe, 80 mm, 1.85 m ring)
def tur_outline(z):
    ol = superellipse(1.30, 1.25, 1.62, n=28, e=2.25, y0=TC.y)
    return [(x, max(y, TC.y - 1.02)) for x, y in ol]          # flat 100 mm front face
turret_loft(P + "_turret", [(1.86, tur_outline(1.86)), (2.70, tur_outline(2.70))], BODY, GT, bevel=0.03)
abox(P + "_tfront", (2.05, 0.10, 0.80), (0, TC.y - 1.04, 2.28), BODY, GT, bevel=0.02)
abox(P + "_troof", (2.1, 2.2, 0.04), (0, TC.y + 0.20, 2.72), BODY, GT, bevel=0.0)
cupola_de(P, "cmd", (0.62, TC.y + 0.62, 2.72), 0.36, GT, mat=BODY, blocks=5, h=0.26)
hatch(P, "ld", (-0.50, TC.y + 0.15, 2.74), 0.27, GT, mat=BODY)
acyl(P + "_vent", 0.11, 0.06, (0.0, TC.y - 0.55, 2.75), BODY, GT, seg=14)
periscope(P, "ld", Vector((-0.50, TC.y - 0.35, 2.74)), GT)
abox(P + "_bin", (1.30, 0.48, 0.55), (0, TC.y + 1.82, 2.26), BODY, GT, bevel=0.03)          # Rommelkiste
for sx in (1, -1):
    abox("%s_binarm%d" % (P, sx), (0.05, 0.3, 0.05), (sx * 0.55, TC.y + 1.55, 2.12), "M_Steel", GT, bevel=0.0)
    acyl("%s_pport%d" % (P, sx), 0.06, 0.05, (sx * 1.28, TC.y + 0.55, 2.30), BODY, GT, axis=0, seg=10)
    abox("%s_tvis%d" % (P, sx), (0.04, 0.22, 0.09), (sx * 1.25, TC.y - 0.35, 2.40), BODY, GT, bevel=0.0)
    for k in range(3):    # NbK 39 smoke dischargers on the turret front corners
        rod("%s_nbk%d_%d" % (P, sx, k), Vector((sx * 1.06, TC.y - 0.95 + k * 0.12, 2.48)), Vector((sx * 1.22, TC.y - 1.05 + k * 0.12, 2.62)), 0.045, BODY, GT, seg=8)
    spare_links(P, "t%d" % sx, (sx * 1.30, TC.y + 0.95, 2.25), 3, Vector((0, 0.15, 0)), (sx, 0, 0), GT, w=0.36, pitch=0.13)
antenna(P, "ant", Vector((-1.25, 2.85, 1.88)), 2.0, G)

# ---------------------------------------------------------------- mantlet + gun (elevates)
abox(P + "_mant", (1.55, 0.22, 0.72), (0, TC.y - 1.20, TRN.z), BODY, GG, bevel=0.06)
abox(P + "_mantc", (0.72, 0.18, 0.50), (0, TC.y - 1.38, TRN.z), BODY, GG, bevel=0.05)
acyl(P + "_coax", 0.03, 0.10, (0.30, TC.y - 1.33, TRN.z + 0.06), "M_Bore", GG, axis=1, seg=8, dirt=False)
for sx in (1, -1):
    acyl("%s_sight%d" % (P, sx), 0.045, 0.10, (sx * 0.24 + 0.12, TC.y - 1.32, TRN.z + 0.18), "M_Lens", GG, axis=1, seg=10, dirt=False)
gun_simple(P, GB, TC.y - 1.45, MUZ_Y + 0.50, TRN.z, 0.072, 0.105, steps=[(0.35, 0.095), (0.72, 0.082)], mat=BODY)
brake_de(P, GB, MUZ_Y + 0.50, TRN.z, 0.14, 0.50, mat=BODY)

auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
# decals AFTER auto_attach (otherwise they get pulled into the plates)
ww2_decal(P + "_crossL", (1.575, 0.70, 1.42), (1, 0, 0), (0, 0, 1), 0.62, "cross", G)
ww2_decal(P + "_crossR", (-1.575, 0.70, 1.42), (-1, 0, 0), (0, 0, 1), 0.62, "cross", G)
ww2_decal(P + "_crossB", (0.0, 3.09, 1.30), (0, 1, 0), (0, 0, 1), 0.42, "cross", G)
ww2_decal(P + "_numL", (1.31, TC.y + 0.2, 2.38), (1, 0, 0), (0, 0, 1), 0.70, "num_131", GT)
ww2_decal(P + "_numR", (-1.31, TC.y + 0.2, 2.38), (-1, 0, 0), (0, 0, 1), 0.70, "num_131", GT)
weather(gen_weather([(0.55, 3.18, 1.7), (-0.55, 3.18, 1.7)], 0.7))
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_tiger.glb", "/home/user/models/rw_tiger.blend", "RW_TIGER")
