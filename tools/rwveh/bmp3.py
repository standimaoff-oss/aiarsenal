# BMP-3 infantry fighting vehicle — GunVehicle node contract.
# Spec: yawPivot glTF (0,1.86,0.35) -> TC (0,-0.35,1.86); pitch rel (0,.27,.62) -> TRN (0,-0.97,2.13);
# tubes: 100 mm 2A70 z 3.25 -> muzzle y -4.22 (recoils: barrel node); 30 mm 2A72 (0.27,-0.02,3.05) -> (0.27,-4.02,2.11) (cradle).
reset_scene()
col_get("BUILD"); col_get("RIG")
armor_mats()
P = "BMP3"
G = P + "_hull"
GT = P + "_turret"
GG = P + "_gun"
GB = P + "_barrel"
M = "M_Body"
TX = 1.30
TW = 0.38
RR = 0.27
ZW = RR + 0.07
ROAD = [(-1.95 + k * 0.78, ZW) for k in range(6)]
SPK = (2.66, 0.66)
IDL = (-2.66, 0.58)
ROLL = [(1.3, 0.80), (0.0, 0.81), (-1.3, 0.80)]
TC = Vector((0, -0.35, 1.86))
TRN = Vector((0, -0.97, 2.13))

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = running_gear(P, side, x, ROAD, RR, SPK, 0.25, IDL, 0.25, ROLL, 0.10, TW, 0.12, 0.04,
                                   style="ru", gap=0.07, teeth=12, mat=M, arm_grp=G)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
for sx in (1, -1):
    for i, (y, z) in enumerate(ROAD):
        rod("%s_arm%d_%d" % (P, sx, i), Vector((sx * 1.04, y + 0.28, z + 0.05)), Vector((sx * 1.12, y, z)), 0.04, M, G, seg=8)
    for i in (0, 5):
        y = ROAD[i][0]
        rod("%s_shock%d_%d" % (P, sx, i), Vector((sx * 1.05, y + 0.2, ZW + 0.06)), Vector((sx * 1.05, y + 0.04, ZW + 0.36)), 0.035, "M_Dark", G, seg=8)

# ---------------------------------------------------------------- hull: sharp amphibious nose, high rear deck
hull_prof = [(-3.18, 0.40), (-3.62, 0.86), (-1.55, 1.74), (2.2, 1.78), (3.2, 1.72), (3.22, 0.60), (2.9, 0.40)]
prism_x(P + "_hull", hull_prof, 1.08, M, G, bevel=0.03)
spon = [(-3.41, 0.95), (-1.55, 1.74), (2.2, 1.78), (3.2, 1.72), (3.2, 0.95)]
for sx in (1, -1):
    prism_x("%s_spon%d" % (P, sx), spon, 0.24, M, G, bevel=0.02, x0=1.08 if sx > 0 else -1.56)
    abox("%s_skirt%d" % (P, sx), (0.03, 6.2, 0.26), (sx * 1.575, -0.05, 0.84), "M_Rubber", G, bevel=0.004)
    for k in range(5):
        abox("%s_float%d_%d" % (P, sx, k), (0.05, 1.0, 0.2), (sx * 1.58, -2.2 + k * 1.15, 1.08), M, G, bevel=0.01)
gl0, gl1 = Vector((0, -3.62, 0.86)), Vector((0, -1.55, 1.74))
ang = math.atan2(gl1.z - gl0.z, gl1.y - gl0.y)
nrm = Vector((0, -math.sin(ang), math.cos(ang)))
# folding trim vane (ribbed) lying on the glacis
c = gl0.lerp(gl1, 0.3) + nrm * 0.05
abox(P + "_vane", (2.3, 0.75, 0.04), tuple(c), M, G, rot=(ang, 0, 0), bevel=0.01)
for k in range(9):
    abox("%s_vrib%d" % (P, k), (0.03, 0.72, 0.06), tuple(c + Vector((-1.0 + k * 0.25, 0, 0)) + nrm * 0.04), M, G, rot=(ang, 0, 0), bevel=0.0)
for sx in (1, -1):
    rod("%s_vanearm%d" % (P, sx), c + Vector((sx * 1.05, 0.25, 0.1)), c + Vector((sx * 1.05, 0.7, 0.32)), 0.03, "M_Steel", G, seg=6)
# bow PKT ball mounts at the front corners + driver hatch / periscopes
for sx in (1, -1):
    b = gl0.lerp(gl1, 0.72) + Vector((sx * 0.85, 0, 0)) + nrm * 0.04
    lathe_y("%s_ballm%d" % (P, sx), [(0.0, 0.0), (0.0, 0.2), (0.12, 0.17), (0.12, 0.0)], tuple(b), [M], G, seg=18, axis='Y')
    acyl("%s_pkt%d" % (P, sx), 0.016, 0.45, tuple(b + Vector((0, -0.3, 0.04))), "M_Dark", G, axis=1, seg=8)
    periscope(P, "bow%d" % sx, b + Vector((0, 0.42, 0.22)), G, 0.0)
hatch(P, "drv", (0, -1.75, 1.76), 0.27, G)
for k, yaw in enumerate((-0.4, 0.0, 0.4)):
    periscope(P, "drv%d" % k, (math.sin(yaw) * 0.3, -2.02 + abs(yaw) * 0.08, 1.70), G, yaw)
for sx in (1, -1):
    hc = gl0.lerp(gl1, 0.45) + Vector((sx * 1.2, 0, 0)) + nrm * 0.05
    acyl("%s_hl%d" % (P, sx), 0.075, 0.1, tuple(hc), "M_Dark", G, axis=1, seg=14)
    acyl("%s_hll%d" % (P, sx), 0.06, 0.01, tuple(hc + Vector((0, -0.055, 0))), "M_HeadLamp", G, axis=1, seg=14, dirt=False)
    lathe_y("%s_hook%d" % (P, sx), [(-0.03, 0.05), (-0.03, 0.09), (0.03, 0.09), (0.03, 0.05)], (sx * 0.7, -3.5, 0.66), ["M_Steel"], G, seg=12, axis='X')
# rear: troop roof hatches (2 pairs), rear doors with steps, waterjet ports, engine louvres
for sx in (1, -1):
    for k in range(2):
        abox("%s_rhatch%d_%d" % (P, sx, k), (0.62, 0.7, 0.04), (sx * 0.42, 1.55 + k * 0.78, 1.79), M, G, bevel=0.01)
        rod("%s_rhh%d_%d" % (P, sx, k), Vector((sx * 0.42 - 0.15, 1.55 + k * 0.78, 1.83)), Vector((sx * 0.42 + 0.15, 1.55 + k * 0.78, 1.83)), 0.01, "M_Steel", G, seg=5)
    abox("%s_door%d" % (P, sx), (0.62, 0.05, 0.95), (sx * 0.4, 3.23, 1.12), M, G, bevel=0.01)
    abox("%s_step%d" % (P, sx), (0.5, 0.25, 0.03), (sx * 0.4, 3.34, 0.72), "M_Steel", G, bevel=0.004)
    rod("%s_dhdl%d" % (P, sx), Vector((sx * 0.15, 3.27, 1.15)), Vector((sx * 0.15, 3.27, 1.35)), 0.012, "M_Steel", G, seg=5)
    abox("%s_jet%d" % (P, sx), (0.32, 0.04, 0.26), (sx * 0.95, 3.23, 0.68), "M_Bore", G, bevel=0.005)
    abox("%s_jetc%d" % (P, sx), (0.36, 0.03, 0.3), (sx * 0.95, 3.27, 0.72), M, G, rot=(-0.5, 0, 0), bevel=0.004)
    abox("%s_tail%d" % (P, sx), (0.12, 0.05, 0.08), (sx * 1.3, 3.22, 1.5), "M_Dark", G, bevel=0.005)
    acyl("%s_taill%d" % (P, sx), 0.03, 0.01, (sx * 1.3, 3.25, 1.5), "M_Amber", G, axis=1, seg=8, dirt=False)
slats(P + "_louv", -0.95, 0.95, 0.75, 1.15, 1.785, 6, "M_Dark", G, along='Y')
abox(P + "_exh", (0.05, 0.5, 0.18), (-1.57, 1.9, 1.5), "M_Dark", G, bevel=0.01)
for sx in (1, -1):
    abox("%s_box%d" % (P, sx), (0.36, 0.9, 0.26), (sx * 1.33, 2.55, 1.91), M, G, bevel=0.015)
tow_cable(P, "L", (1.35, -1.2, 1.80), (1.35, 0.6, 1.80), 0.02, G)

# ---------------------------------------------------------------- turret: low, cone-sided
def T(x, y, z):
    return Vector((x, TC.y + y, z))
half = [(0.0, -1.0), (0.58, -0.88), (0.98, -0.42), (1.02, 0.32), (0.82, 0.86), (0.0, 0.98)]
rings = [[T(x * s, y * s, z) for x, y in mirror_outline(half)] for s, z in ((1.0, 1.84), (0.97, 1.96), (0.78, 2.32))]
loft_rings(P + "_tshell", rings, M, GT, bevel=0.02)
lathe_y(P + "_tring", [(0.0, 0.86), (0.0, 0.94), (0.06, 0.94), (0.06, 0.86)], (0, TC.y, 1.81), ["M_Dark"], GT, seg=36, axis='Z')
# wide mantlet over both guns
abox(P + "_mantf", (0.95, 0.26, 0.42), tuple(T(0.08, -0.95, 2.08)), M, GT, bevel=0.03)
# gunner sight 2K23 (left of guns) with doors, commander sight (right), hatches
S = T(0.62, -0.55, 2.42)
abox(P + "_gsight", (0.32, 0.42, 0.26), tuple(S), M, GT, bevel=0.02)
abox(P + "_gsw", (0.24, 0.02, 0.14), tuple(S + Vector((0, -0.22, 0.02))), "M_Lens", GT, bevel=0.0, dirt=False)
CS = T(-0.55, -0.45, 2.38)
acyl(P + "_csb", 0.12, 0.12, tuple(CS), M, GT, seg=16)
abox(P + "_csh", (0.24, 0.28, 0.2), tuple(CS + Vector((0, 0, 0.15))), M, GT, bevel=0.02)
abox(P + "_csw", (0.16, 0.02, 0.1), tuple(CS + Vector((0, -0.145, 0.16))), "M_Lens", GT, bevel=0.0, dirt=False)
hatch(P, "gnr", T(0.38, 0.25, 2.30), 0.26, GT)
hatch(P, "cmd", T(-0.38, 0.25, 2.30), 0.26, GT)
for k in range(3):
    periscope(P, "cmd%d" % k, T(-0.38 + (k - 1) * 0.2, -0.1, 2.33), GT, (k - 1) * 0.4)
# 902V smoke dischargers (3 per side), ammo boxes, antennas, wind sensor
for sx in (1, -1):
    smoke_dischargers(P, "s%d" % sx, T(sx * 0.9, -0.35, 2.12), 3, 8.0, sx * 30.0, 15.0, GT)
    abox("%s_tbox%d" % (P, sx), (0.18, 0.6, 0.3), tuple(T(sx * 1.0, 0.45, 2.0)), M, GT, bevel=0.012)
antenna(P, "a1", T(0.55, 0.75, 2.27), 2.0, GT)
antenna(P, "a2", T(-0.55, 0.75, 2.27), 1.1, GT)
rod(P + "_mast", T(0.0, 0.8, 2.27), T(0.0, 0.8, 2.6), 0.013, "M_Dark", GT, seg=6)

# ---------------------------------------------------------------- guns
abox(P + "_cradle", (0.7, 0.7, 0.36), (0.1, TRN.y + 0.2, TRN.z), M, GG, bevel=0.02)
abox(P + "_mantm", (0.78, 0.22, 0.34), (0.1, TRN.y - 0.22, TRN.z), M, GG, bevel=0.03)
# 30 mm 2A72 on the cradle (no barrel recoil animation)
acyl(P + "_2a72j", 0.05, 0.45, (0.27, TRN.y - 0.55, TRN.z - 0.02), M, GG, axis=1, seg=12)
acyl(P + "_2a72", 0.022, 2.6, (0.27, TRN.y - 1.75, TRN.z - 0.02), "M_Dark", GG, axis=1, seg=10)
acyl(P + "_2a72m", 0.035, 0.18, (0.27, TRN.y - 3.05 - 0.08, TRN.z - 0.02), "M_Dark", GG, axis=1, seg=10)   # r5: flush on the 2A72 muzzle
acyl(P + "_pkt", 0.016, 0.3, (-0.3, TRN.y - 0.45, TRN.z - 0.06), "M_Dark", GG, axis=1, seg=8)
# 100 mm 2A70 launcher-gun: thick short tube with sleeve bands
gun_tube(P, GB, TRN.y - 0.3, -4.22, TRN.z, 0.075, 0.115, mat=M, sleeve=True)

# r5: every part must touch its node body (smoke dischargers, skirts, brackets, handwheels ...)
auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
weather(gen_weather([(-1.57, 1.9, 1.5)], 0.7))
rt = root("root")
group_under(G, P + "_hull_geo", rt)
track_rig(rt, P, "", WHEELS)
for o in LINKS:
    attach(o, rt)
ty = pivot("launcher_yaw", tuple(TC), rt)
group_under(GT, "launcher_yaw_geo", ty)
gp = pivot("launcher_pitch", tuple(TRN), ty)
group_under(GG, "launcher_pitch_geo", gp)
br = pivot("barrel", tuple(TRN), gp)
group_under(GB, "barrel_geo", br)
rw_export("/home/user/.cache/rwveh/raw/rw_bmp3.glb", "/home/user/models/rw_bmp3.blend", "RW_BMP3")
