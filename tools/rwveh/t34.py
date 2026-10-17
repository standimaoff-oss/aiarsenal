# T-34-85 (1944, Factory No.183) — real dimensions.
# Hull 6.10 m (8.15 with gun), width 3.00 m, height 2.72 m, clearance 0.40 m.
# Christie suspension: 5 x 830 mm rubber-tyred road wheels/side, no return rollers, REAR roller-type sprocket,
# 500 mm cast tracks, pitch 172 mm (72 links). 85 mm ZiS-S-53 (4.645 m barrel), coax DT, hull ball DT.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "T34"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
BODY = "M_Green4BO"
HL = 6.10 / 2
TWD = 0.50
TX = 3.00 / 2 - TWD / 2          # 1.25
RR = 0.415
TH = 0.06
ZW = RR + TH
ROAD = [(-1.98, ZW), (-1.15, ZW), (-0.30, ZW), (0.66, ZW), (1.56, ZW)]
SPK = (2.62, 0.62)               # rear drive
IDL = (-2.68, 0.60)              # front idler
TC = Vector((0, -0.85, 1.55))
TRN = Vector((0, -1.86, 2.03))
MUZ_Y = -HL - (8.15 - 6.10)      # -5.10

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = ww2_gear(P, side, x, ROAD, RR, "spoked_rubber", 0.15, SPK, 0.33, IDL, 0.30, [], 0.1, TWD, 0.172, TH,
                               mat=BODY, twin_gap=0.09, arm_grp=G, arm_len=0.25, teeth=10, link="ru", wseg=16, spk_gap=0.10)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))

# ---------------------------------------------------------------- hull: 45 mm plates, 60 deg glacis, 40 deg upper sides
lower = sym(box_pts(0, 0.98, -2.62, 2.80, 0.40, 1.00)) + sym([(0.98, -HL, 0.86), (0.98, -HL, 0.98), (0.98, HL, 0.80)])
chull(P + "_lower", lower, BODY, G, bevel=0.02)
upper = sym([(1.50, -2.70, 1.00), (1.50, 2.85, 1.00), (1.50, -HL + 0.02, 0.92), (1.50, HL - 0.02, 0.92),
             (1.13, -1.98, 1.55), (1.13, 2.30, 1.55)]) + sym([(0.98, -HL, 0.86)])
chull(P + "_upper", upper, BODY, G, bevel=0.025)
for sx in (1, -1):
    abox("%s_fend%d" % (P, sx), (0.50, 5.9, 0.01), (sx * 1.28, 0.0, 1.00), BODY, G, bevel=0.0)
    tilted_box("%s_ffl%d" % (P, sx), (sx * TX, -2.95, 1.00), (sx * TX, -3.20, 0.85), 0.5, 0.01, BODY, G)
    abox("%s_bump%d" % (P, sx), (0.10, 0.28, 0.10), (sx * 1.00, -2.55, 0.62), BODY, G, bevel=0.01)
    acyl("%s_fdrv%d" % (P, sx), 0.22, 0.15, (sx * 1.04, SPK[0], SPK[1]), BODY, G, axis=0, seg=14)
    rod("%s_idla%d" % (P, sx), Vector((sx * 0.98, IDL[0] + 0.22, IDL[1] + 0.05)), Vector((sx * (TX - 0.06), IDL[0], IDL[1])), 0.06, BODY, G, seg=8)
# glacis fittings: driver hatch (left = +X) with 2 periscopes, hull DT ball (right), headlight, tow hooks, spare tracks
gl_n = Vector((0, -0.70, 1.10)).normalized()       # glacis normal (60 deg plate)
def glacis(y, x):            # point on the glacis surface at hull station y
    z = 0.86 + (y + HL) * (1.55 - 0.86) / (HL - 1.98)
    return Vector((x, y, z))
p = glacis(-2.40, 0.45)
abox(P + "_dhatch", (0.55, 0.62, 0.06), tuple(p + gl_n * 0.03), BODY, G, rot=(math.atan2(-gl_n.y, gl_n.z), 0, 0), bevel=0.02)
for sx in (-1, 1):
    abox("%s_dper%d" % (P, sx), (0.13, 0.07, 0.07), tuple(glacis(-2.20, 0.45 + sx * 0.15) + gl_n * 0.07), "M_Lens", G, bevel=0.0, dirt=False)
ball_mount(P, "hull", glacis(-2.50, -0.55) + gl_n * 0.02, gl_n + Vector((0, -0.4, -0.3)), G, r=0.12, mat=BODY, soviet=True)
headlamp(P, "f", glacis(-2.75, 0.95) + Vector((0, -0.08, 0.1)), G, mat=BODY)
acyl(P + "_horn", 0.05, 0.10, tuple(glacis(-2.75, -0.95) + Vector((0, -0.05, 0.08))), "M_Dark", G, axis=1, seg=10)
for sx in (1, -1):
    rod("%s_ftow%d" % (P, sx), Vector((sx * 0.75, -HL + 0.05, 0.75)), Vector((sx * 0.75, -HL - 0.15, 0.75)), 0.045, "M_Steel", G, seg=8)
spare_links(P, "glac", glacis(-2.65, 0.0) + gl_n * 0.01, 4, Vector((0.55, 0, 0)), gl_n, G, w=0.50, pitch=0.17)
# hull roof + engine deck: louvres, central engine hatch, transmission hatch
abox(P + "_roof", (2.22, 1.85, 0.04), (0, -0.85, 1.56), BODY, G, bevel=0.0)
grille(P, "eng", (0.0, 1.05, 1.58), 0.70, 1.20, G, n=6, mat=BODY)
for sx in (1, -1):
    grille(P, "lv%d" % sx, (sx * 0.72, 1.05, 1.58), 0.55, 1.30, G, n=9, mat=BODY)
abox(P + "_trh", (0.95, 0.45, 0.05), (0, 2.05, 1.58), BODY, G, bevel=0.01)
# rear: sloped 45 mm plate with round transmission hatch + two armoured exhaust covers, external fuel drums on the sides
rear_n = Vector((0, 0.62, 0.78)).normalized()
acyl(P + "_rhatch", 0.30, 0.05, (0, 2.68, 1.25), BODY, G, rot=(math.atan2(rear_n.y, rear_n.z), 0, 0), seg=18)
for sx in (1, -1):
    acyl("%s_exh%d" % (P, sx), 0.11, 0.18, (sx * 0.65, 2.75, 1.17), BODY, G, rot=(math.atan2(rear_n.y, rear_n.z), 0, 0), seg=12)
    acyl("%s_exhp%d" % (P, sx), 0.06, 0.10, (sx * 0.65, 2.82, 1.10), "M_Exhaust", G, rot=(math.atan2(rear_n.y, rear_n.z), 0, 0), seg=10)
    fuel_drum(P, "s%d_a" % sx, (sx * 1.40, 0.75, 1.20), 0.18, 0.85, G, axis=1, mat=BODY)
    fuel_drum(P, "s%d_b" % sx, (sx * 1.40, 1.85, 1.20), 0.18, 0.85, G, axis=1, mat=BODY)
    rod("%s_cab%d" % (P, sx), Vector((sx * 1.33, -1.9, 1.25)), Vector((sx * 1.33, 0.2, 1.25)), 0.018, "M_Steel", G, seg=6)
    rod("%s_rail%d" % (P, sx), Vector((sx * 1.18, -1.6, 1.52)), Vector((sx * 1.18, 0.0, 1.52)), 0.015, BODY, G, seg=6)   # tank-rider handrails
tools_set(P, "L", (1.30, -1.3, 1.03), G)
# two MDSh smoke canisters on the rear
for sx in (1, -1):
    acyl("%s_mdsh%d" % (P, sx), 0.15, 0.55, (sx * 0.55, HL + 0.12, 0.88), "M_Dark", G, axis=0, seg=14)

# ---------------------------------------------------------------- cast turret (90 mm front) with 3-man layout
tl = [(1.55, superellipse(1.02, 1.12, 1.48, n=24, e=2.3, y0=TC.y)),
      (2.00, superellipse(1.00, 1.08, 1.50, n=24, e=2.3, y0=TC.y)),
      (2.40, superellipse(0.86, 0.92, 1.35, n=24, e=2.2, y0=TC.y)),
      (2.47, superellipse(0.72, 0.75, 1.15, n=24, e=2.2, y0=TC.y))]
turret_loft(P + "_turret", tl, BODY, GT, bevel=0.0, smooth=True)
cupola_ru(P, "cmd", (0.45, TC.y + 0.55, 2.42), 0.33, GT, mat=BODY, h=0.24)
hatch(P, "ld", (-0.45, TC.y + 0.30, 2.48), 0.27, GT, mat=BODY)
periscope(P, "ld", Vector((-0.45, TC.y - 0.10, 2.48)), GT)
periscope(P, "gn", Vector((0.25, TC.y - 0.35, 2.48)), GT)
for sx in (-0.2, 0.25):                                   # two ventilator domes at the turret rear
    lathe_y("%s_vent%d" % (P, int(sx * 10)), [(0.0, 0.16), (0.06, 0.16), (0.12, 0.08), (0.13, 0.0)], (sx, TC.y + 0.95, 2.40), [BODY], GT, seg=14, axis='Z')
for sx in (1, -1):
    acyl("%s_pport%d" % (P, sx), 0.05, 0.06, (sx * 0.98, TC.y + 0.65, 1.95), BODY, GT, axis=0, seg=10)
    lift_hooks(P, "t%d" % sx, [(sx * 0.80, TC.y - 0.40, 2.40), (sx * 0.75, TC.y + 1.00, 2.40)], GT)
for k in range(3):                                         # hand rails on the turret sides (tank riders)
    pass
for sx in (1, -1):
    rod("%s_trail%d" % (P, sx), Vector((sx * 1.04, TC.y - 0.4, 2.10)), Vector((sx * 1.04, TC.y + 0.9, 2.10)), 0.014, BODY, GT, seg=6)
antenna(P, "ant", Vector((0.85, TC.y + 0.25, 2.25)), 2.2, GT)

# ---------------------------------------------------------------- cast mantlet + gun
lathe_y(P + "_mant", [(0.0, 0.0), (0.0, 0.38), (0.20, 0.36), (0.32, 0.25), (0.36, 0.0)], (0, TC.y - 1.02, TRN.z), [BODY], GG, seg=20)
abox(P + "_mantw", (0.95, 0.22, 0.55), (0, TC.y - 1.08, TRN.z), BODY, GG, bevel=0.08)
acyl(P + "_coax", 0.022, 0.12, (0.30, TC.y - 1.22, TRN.z), "M_Dark", GG, axis=1, seg=8)
acyl(P + "_sight", 0.035, 0.10, (-0.25, TC.y - 1.20, TRN.z + 0.05), "M_Lens", GG, axis=1, seg=8, dirt=False)
acyl(P + "_sleeve", 0.12, 0.45, (0, TC.y - 1.50, TRN.z), BODY, GG, axis=1, seg=16)
gun_simple(P, GB, TC.y - 1.70, MUZ_Y, TRN.z, 0.058, 0.085, collar=False, steps=[(0.30, 0.075)], mat=BODY)

auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
ww2_decal(P + "_rodL", (1.02, TC.y - 0.05, 1.86), (1, 0, 0.05), (0, 0, 1), 0.90, "ru_rodina", GT)
ww2_decal(P + "_rodR", (-1.02, TC.y - 0.05, 1.86), (-1, 0, 0.05), (0, 0, 1), 0.90, "ru_rodina", GT)
ww2_decal(P + "_numL", (1.0, TC.y + 0.62, 2.18), (1, 0.12, 0), (0, 0, 1), 0.34, "ru_num_24", GT)
ww2_decal(P + "_numR", (-1.0, TC.y + 0.62, 2.18), (-1, 0.12, 0), (0, 0, 1), 0.34, "ru_num_24", GT)
weather(gen_weather([(0.65, 2.8, 1.15), (-0.65, 2.8, 1.15)], 0.6))
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_t34.glb", "/home/user/models/rw_t34.blend", "RW_T34")
