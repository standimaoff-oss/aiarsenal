# Sturmtiger (38 cm RW 61 auf Sturmmoerser Tiger, 1944) — real dimensions.
# Length 6.28 m, width 3.57 m, height 2.85 m. Tiger I chassis (8 interleaved axles, front sprocket, 725 mm tracks).
# Casemate: 150 mm front at 47 deg, 82 mm sides; 38 cm RW 61 rocket mortar (L/5.4, 2.054 m) in a ball mount offset to
# the right; 31 gas-vent holes round the muzzle; rocket crane at the rear right; MG34 ball; Zimmerit.
# Nodes: launcher_yaw = ball mount (+-10 deg traverse) > launcher_pitch (0..85) > barrel.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "STT"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
BODY = "M_Zimm"
HL = 6.316 / 2
TWD = 0.725
TX = 3.547 / 2 - TWD / 2
RR, TH = 0.40, 0.055
ZW = RR + TH
ROAD = [(-1.84 + i * 0.514, ZW) for i in range(8)]
A, B = [0.24, -0.04], [0.10, -0.18]
INTER = [A if i % 2 == 0 else B for i in range(8)]
SPK = (-2.78, 0.80)
IDL = (2.86, 0.66)
BALL = Vector((-0.30, -2.18, 1.86))       # ball-mount centre on the 47 deg front plate
TC = BALL.copy()
TRN = BALL.copy()
MUZ_Y = -3.14

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = ww2_gear(P, side, x, ROAD, RR, "steel", 0.10, SPK, 0.42, IDL, 0.30, [], 0.1, TWD, 0.130, TH,
                               mat="M_Gelb", interleave=INTER, arm_grp=G, arm_len=0.30, teeth=18, wseg=16, spk_gap=0.12)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))

# ---------------------------------------------------------------- Tiger lower hull + rear superstructure
low = [(-2.86, 0.47), (-HL, 0.78), (-HL, 1.06), (-2.70, 1.16), (3.05, 1.16), (3.10, 0.66), (2.88, 0.47)]
prism_x(P + "_lower", low, 0.98, BODY, G, bevel=0.03)
sup = [(-2.70, 1.02), (-2.70, 1.20), (0.60, 1.86), (2.90, 1.86), (3.06, 1.76), (3.08, 1.02)]
prism_x(P + "_super", sup, 1.555, BODY, G, bevel=0.025)
for sx in (1, -1):
    tilted_box("%s_mudg%d" % (P, sx), (sx * TX, -2.70, 1.10), (sx * TX, -3.30, 0.96), TWD + 0.03, 0.01, "M_Gelb", G)
    abox("%s_fend%d" % (P, sx), (0.24, 5.5, 0.01), (sx * 1.66, 0.15, 1.03), "M_Gelb", G, bevel=0.0)
    acyl("%s_fdrv%d" % (P, sx), 0.30, 0.2, (sx * 1.08, SPK[0], SPK[1]), BODY, G, axis=0, seg=16)
    rod("%s_idlarm%d" % (P, sx), Vector((sx * 1.0, IDL[0] - 0.2, IDL[1] + 0.06)), Vector((sx * (TX - 0.08), IDL[0], IDL[1])), 0.07, BODY, G, seg=8)
    acyl("%s_muf%d" % (P, sx), 0.11, 0.62, (sx * 0.55, 3.18, 1.38), "M_Exhaust", G, seg=14)
    acyl("%s_mufg%d" % (P, sx), 0.135, 0.28, (sx * 0.55, 3.18, 1.30), BODY, G, seg=14)
    rod("%s_tow%d" % (P, sx), Vector((sx * 0.88, 3.08, 0.70)), Vector((sx * 0.88, 3.30, 0.70)), 0.05, "M_Steel", G, seg=8)
abox(P + "_deck", (2.6, 2.05, 0.04), (0, 1.80, 1.88), BODY, G, bevel=0.0)
for sx in (1, -1):
    grille(P, "rad%d" % sx, (sx * 1.0, 1.85, 1.91), 0.9, 1.6, G, n=9, mat="M_Gelb")
    acyl("%s_fan%d" % (P, sx), 0.30, 0.05, (sx * 0.55, 2.45, 1.92), BODY, G, seg=20)

# ---------------------------------------------------------------- casemate (front 150 mm @47 deg, sides 82 mm @20 deg)
FY0, FZ0, FZ1 = -2.85, 1.15, 2.62
FY1 = FY0 + (FZ1 - FZ0) * math.tan(math.radians(47))
cas = sym([(1.57, FY0, FZ0), (1.57, 0.70, FZ0), (1.57, 0.70, 1.86), (1.30, FY1, FZ1), (1.30, 0.66, FZ1)])
chull(P + "_casemate", cas, BODY, G, bevel=0.03)
fn = Vector((0, -math.cos(math.radians(47)), math.sin(math.radians(47))))
def front(x, z):
    return Vector((x, FY0 + (z - FZ0) * math.tan(math.radians(47)), z))
acyl(P + "_ballcol", 0.56, 0.16, tuple(front(BALL.x, BALL.z) + fn * 0.02), BODY, G, rot=(math.atan2(-fn.y, fn.z), 0, 0), seg=28)
ball_mount(P, "mg", front(0.85, 1.60), fn + Vector((0, -0.5, -0.4)), G, r=0.11, mat=BODY)
abox(P + "_visor", (0.30, 0.10, 0.12), tuple(front(0.70, 2.25) + fn * 0.05), BODY, G, rot=(math.atan2(-fn.y, fn.z), 0, 0), bevel=0.02)
headlamp(P, "f", front(1.25, 1.30) + Vector((0, -0.05, 0.1)), G, mat="M_Gelb")
# roof: big loading hatch (rear), commander hatch + periscope, Nahverteidigungswaffe, ventilator
abox(P + "_lhatch", (1.10, 0.90, 0.06), (0.0, 0.05, FZ1 + 0.03), BODY, G, bevel=0.01)
acyl(P + "_lhinge", 0.03, 1.0, (0.0, 0.48, FZ1 + 0.06), "M_Steel", G, axis=0, seg=8)
hatch(P, "cmd", (0.75, -0.95, FZ1 + 0.01), 0.27, G, mat=BODY)
periscope(P, "cmd", Vector((0.75, -1.25, FZ1 + 0.01)), G)
periscope(P, "gun", Vector((-0.30, -1.30, FZ1 + 0.01)), G)
acyl(P + "_nvw", 0.07, 0.16, (-0.70, -0.70, FZ1 + 0.08), "M_Dark", G, seg=12)
acyl(P + "_vent", 0.12, 0.06, (0.0, -0.95, FZ1 + 0.03), BODY, G, seg=14)
# rocket crane at the rear right corner: post, jib, winch drum, hook
crane = Vector((-1.10, 0.45, FZ1))
acyl(P + "_crbase", 0.13, 0.08, tuple(crane + Vector((0, 0, 0.04))), "M_Gelb", G, seg=14)
rod(P + "_crpost", crane, crane + Vector((0, 0, 0.85)), 0.06, "M_Gelb", G, seg=10)
rod(P + "_crjib", crane + Vector((0, 0, 0.80)), crane + Vector((0.0, -1.30, 0.95)), 0.05, "M_Gelb", G, seg=8)
rod(P + "_crbrace", crane + Vector((0, 0, 0.30)), crane + Vector((0.0, -0.70, 0.88)), 0.035, "M_Gelb", G, seg=8)
acyl(P + "_crwinch", 0.07, 0.14, tuple(crane + Vector((0.10, 0.0, 0.45))), "M_Dark", G, axis=0, seg=12)
rod(P + "_crrope", crane + Vector((0.0, -1.30, 0.95)), crane + Vector((0.0, -1.30, 0.55)), 0.008, "M_Steel", G, seg=6)
acyl(P + "_crhook", 0.04, 0.08, tuple(crane + Vector((0.0, -1.30, 0.52))), "M_Steel", G, seg=8)
for sx in (1, -1):
    abox("%s_rockrack%d" % (P, sx), (0.30, 1.6, 0.06), (sx * 1.38, -0.6, 2.10), "M_Steel", G, rot=(0, sx * 0.35, 0), bevel=0.0)
    rod("%s_cab%d" % (P, sx), Vector((sx * 1.50, -2.0, 1.55)), Vector((sx * 1.50, 0.5, 1.55)), 0.022, "M_Steel", G, seg=6)
spare_links(P, "rear", (0.0, 3.10, 1.55), 5, Vector((0.32, 0, 0)), (0, 1, 0), G, w=0.30, pitch=0.13)
lift_hooks(P, "h", [(sx * 1.15, y, FZ1) for sx in (1, -1) for y in (-1.3, 0.5)], G)
antenna(P, "ant", Vector((1.10, 0.55, FZ1)), 1.6, G)

# ---------------------------------------------------------------- ball mount (yaw) + mortar (pitch)
lathe_y(P + "_ball", [(0.0, 0.0), (0.0, 0.30), (0.15, 0.46), (0.40, 0.48), (0.62, 0.30), (0.64, 0.0)], (BALL.x, BALL.y - 0.62 + 0.30, BALL.z), [BODY], GT, seg=24)
acyl(P + "_ballring", 0.40, 0.06, (BALL.x, BALL.y - 0.38, BALL.z), "M_Gelb", GG, axis=1, seg=24)
bl = BALL.y - 0.40
lathe_y(P + "_tube", [(0.0, 0.32), (-0.35, 0.31), (-0.36, 0.33), (-0.48, 0.33), (-0.49, 0.30), (MUZ_Y - bl, 0.30)], (BALL.x, bl, BALL.z), ["M_Gelb"], GB, seg=28)
acyl(P + "_bore", 0.19, 0.01, (BALL.x, MUZ_Y - 0.004, BALL.z), "M_Bore", GB, axis=1, seg=20, dirt=False)
acyl(P + "_cw", 0.36, 0.22, (BALL.x, MUZ_Y + 0.25, BALL.z), "M_Gelb", GB, axis=1, seg=28)          # muzzle counterweight ring
for k in range(31):                                     # gas vent holes around the bore
    a = 2 * math.pi * k / 31
    acyl("%s_vh%d" % (P, k), 0.018, 0.012, (BALL.x + math.cos(a) * 0.25, MUZ_Y - 0.005, BALL.z + math.sin(a) * 0.25), "M_Bore", GB, axis=1, seg=6, dirt=False)

auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
for sx in (1, -1):
    ww2_decal("%s_cross%d" % (P, sx), (sx * 1.48, -0.80, 1.65), (sx, 0, 0.18), (0, 0, 1), 0.55, "cross", G)
ww2_decal(P + "_crossB", (0.0, 3.09, 1.30), (0, 1, 0), (0, 0, 1), 0.42, "cross", G)
weather(gen_weather([(0.55, 3.18, 1.7), (-0.55, 3.18, 1.7)], 0.7))
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_sturmtiger.glb", "/home/user/models/rw_sturmtiger.blend", "RW_STURMTIGER")
