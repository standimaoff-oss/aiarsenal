# 2S19 Msta-S 152 mm self-propelled howitzer — GunVehicle node contract.
# Spec: yawPivot glTF (0,1.62,-0.6) -> TC (0,0.60,1.62); pitch rel (0,.68,1.6) -> TRN (0,-1.00,2.30); muzzle y -8.30.
# T-80 running gear: 6 road wheels r .335, 5 return rollers, rear sprocket.
reset_scene()
col_get("BUILD"); col_get("RIG")
armor_mats()
P = "MSTA"
G = P + "_hull"
GT = P + "_turret"
GG = P + "_gun"
GB = P + "_barrel"
M = "M_Body"
TX = 1.45
TW = 0.58
RR = 0.335
ZW = RR + 0.07
ROAD = [(-2.05 + k * 0.80, ZW) for k in range(6)]
SPK = (2.95, 0.70)
IDL = (-3.02, 0.60)
ROLL = [(2.0, 0.93), (1.0, 0.94), (0.0, 0.95), (-1.0, 0.94), (-2.0, 0.93)]
TC = Vector((0, 0.60, 1.62))
TRN = Vector((0, -1.00, 2.30))
MUZ_Y = -8.30

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = running_gear(P, side, x, ROAD, RR, SPK, 0.33, IDL, 0.30, ROLL, 0.11, TW, 0.137, 0.05,
                                   style="ru", gap=0.09, teeth=12, mat=M, pad_mat="M_Rubber", arm_grp=G)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
for sx in (1, -1):
    for i, (y, z) in enumerate(ROAD):
        rod("%s_arm%d_%d" % (P, sx, i), Vector((sx * 1.12, y + 0.3, z + 0.06)), Vector((sx * 1.22, y, z)), 0.05, M, G, seg=8)
    for i in (0, 1, 5):
        y = ROAD[i][0]
        rod("%s_shock%d_%d" % (P, sx, i), Vector((sx * 1.13, y + 0.24, ZW + 0.08)), Vector((sx * 1.13, y + 0.05, ZW + 0.44)), 0.04, "M_Dark", G, seg=8)
    acyl("%s_fdrv%d" % (P, sx), 0.26, 0.22, (sx * 1.18, SPK[0], SPK[1]), M, G, axis=0, seg=16)

# ---------------------------------------------------------------- hull (T-80 based)
hull_prof = [(-2.98, 0.45), (-3.46, 0.86), (-2.10, 1.42), (2.6, 1.46), (3.55, 1.40), (3.55, 0.62), (3.1, 0.45)]
prism_x(P + "_hull", hull_prof, 1.08, M, G, bevel=0.03)
for sx in (1, -1):
    abox("%s_fender%d" % (P, sx), (0.68, 6.7, 0.04), (sx * 1.42, 0.12, 1.18), M, G, bevel=0.01)
    abox("%s_skirt%d" % (P, sx), (0.03, 6.5, 0.5), (sx * 1.77, 0.08, 0.95), "M_Rubber", G, bevel=0.005)
    for k in range(7):
        abox("%s_skp%d_%d" % (P, sx, k), (0.04, 0.88, 0.46), (sx * 1.79, -2.75 + k * 0.92, 0.97), M, G, bevel=0.008)
    tilted_box("%s_flap%d" % (P, sx), (sx * 1.42, -3.2, 1.18), (sx * 1.42, -3.48, 1.0), 0.68, 0.025, "M_Rubber", G)
# self-entrenching dozer blade on the lower front plate
abox(P + "_blade", (2.3, 0.08, 0.42), (0, -3.38, 0.56), M, G, rot=(0.25, 0, 0), bevel=0.01)
for sx in (1, -1):
    rod("%s_bladea%d" % (P, sx), Vector((sx * 0.8, -3.3, 0.6)), Vector((sx * 0.8, -3.05, 0.6)), 0.04, "M_Steel", G, seg=8)
# folded gun travel lock (A-frame) on the glacis
gl0, gl1 = Vector((0, -3.46, 0.86)), Vector((0, -2.10, 1.42))
ang = math.atan2(gl1.z - gl0.z, gl1.y - gl0.y)
nrm = Vector((0, -math.sin(ang), math.cos(ang)))
base = gl0.lerp(gl1, 0.25) + nrm * 0.05
for sx in (1, -1):
    rod("%s_tlock%d" % (P, sx), base + Vector((sx * 0.45, 0, 0)), base + Vector((sx * 0.12, 0.95, 0.4)), 0.035, M, G, seg=8)
rod(P + "_tlockx", base + Vector((-0.12, 0.95, 0.4)), base + Vector((0.12, 0.95, 0.4)), 0.05, M, G, seg=8)
abox(P + "_tlockc", (0.3, 0.12, 0.1), tuple(base + Vector((0, 0.98, 0.46))), "M_Dark", G, bevel=0.01)
hatch(P, "drv", (0, -2.35, 1.44), 0.27, G)
for k, yaw in enumerate((-0.45, 0.0, 0.45)):
    periscope(P, "drv%d" % k, (math.sin(yaw) * 0.3, -2.62 + abs(yaw) * 0.12, 1.45), G, yaw)
for sx in (1, -1):
    c = Vector((sx * 1.15, -3.1, 1.25))
    acyl("%s_hl%d" % (P, sx), 0.085, 0.12, tuple(c), "M_Dark", G, axis=1, seg=14)
    acyl("%s_hll%d" % (P, sx), 0.07, 0.01, tuple(c + Vector((0, -0.065, 0))), "M_HeadLamp", G, axis=1, seg=14, dirt=False)
    lathe_y("%s_hook%d" % (P, sx), [(-0.03, 0.06), (-0.03, 0.1), (0.03, 0.1), (0.03, 0.06)], (sx * 0.75, -3.4, 0.76), ["M_Steel"], G, seg=12, axis='X')
    lathe_y("%s_rhook%d" % (P, sx), [(-0.03, 0.06), (-0.03, 0.1), (0.03, 0.1), (0.03, 0.06)], (sx * 0.9, 3.57, 0.72), ["M_Steel"], G, seg=12, axis='X')
    abox("%s_tail%d" % (P, sx), (0.12, 0.05, 0.08), (sx * 1.25, 3.56, 1.3), "M_Dark", G, bevel=0.005)
    acyl("%s_taill%d" % (P, sx), 0.03, 0.01, (sx * 1.25, 3.59, 1.3), "M_Amber", G, axis=1, seg=8, dirt=False)
# engine deck (behind turret overhang), exhaust left side, unditching log, stowage
slats(P + "_louv", -0.85, 0.85, 3.0, 3.45, 1.43, 5, "M_Dark", G, along='Y')
abox(P + "_exh", (0.06, 0.6, 0.22), (1.75, 2.6, 1.26), "M_Dark", G, bevel=0.01)
slats(P + "_exhs", 1.775, 1.785, 2.35, 2.85, 1.26, 6, "M_Bore", G, along='Y')
acyl(P + "_log", 0.14, 2.9, (0, 3.72, 1.0), "M_Seat", G, axis=0, seg=14)
for sx in (1, -1):
    for k in range(3):
        abox("%s_box%d_%d" % (P, sx, k), (0.5, 0.7, 0.3), (sx * 1.43, -1.7 + k * 0.85, 1.35), M, G, bevel=0.015)
tow_cable(P, "R", (-1.15, -2.9, 1.5), (-1.15, -1.0, 1.5), 0.02, G)

# ---------------------------------------------------------------- turret: big welded box with rear overhang
def T(x, y, z):
    return Vector((x, TC.y + y, z))
half = [(0.0, -2.0), (0.95, -2.0), (1.48, -1.62), (1.58, -1.0), (1.58, 2.2), (1.45, 2.42), (0.0, 2.42)]
rings = []
for z, s, dy in ((1.64, 0.96, 0.05), (1.78, 1.0, 0.0), (2.95, 0.97, 0.22)):
    rings.append([T(x * s, y + (dy if y < -1.5 else 0.0), z) for x, y in mirror_outline(half)])
loft_rings(P + "_tshell", rings, M, GT, bevel=0.03)
# r5: turret-ring collar reaches down into the hull roof (z 1.44) — the turret no longer hovers 14 cm above the hull
lathe_y(P + "_tring", [(0.0, 0.95), (0.0, 1.05), (0.27, 1.05), (0.27, 0.95)], (0, TC.y, 1.40), ["M_Dark"], GT, seg=32, axis='Z')
# side panel seams, handrails, stowage boxes; rear ammo conveyor hatch
for sx in (1, -1):
    for y in (-0.6, 0.5, 1.5):
        abox("%s_seam%d_%d" % (P, sx, int(y * 10)), (0.012, 0.012, 1.1), tuple(T(sx * 1.585, y, 2.35)), "M_Dark", GT, bevel=0.0)
    rod("%s_rail%d" % (P, sx), T(sx * 1.63, -0.9, 2.6), T(sx * 1.63, 1.9, 2.6), 0.015, "M_Steel", GT, seg=6)
    for y in (-0.9, 0.5, 1.9):
        rod("%s_railp%d_%d" % (P, sx, int(y * 10)), T(sx * 1.58, y, 2.6), T(sx * 1.63, y, 2.6), 0.013, "M_Steel", GT, seg=5)
    abox("%s_tbox%d" % (P, sx), (0.26, 1.3, 0.48), tuple(T(sx * 1.72, 0.9, 2.05)), M, GT, bevel=0.015)
    smoke_dischargers(P, "s%d" % sx, T(sx * 1.45, -1.55, 2.62), 6, 6.0, sx * 25.0, 12.0, GT)
abox(P + "_conv", (1.1, 0.05, 0.8), tuple(T(0.3, 2.44, 2.1)), M, GT, bevel=0.01)
rod(P + "_convh", T(0.0, 2.48, 2.2), T(0.6, 2.48, 2.2), 0.013, "M_Steel", GT, seg=5)
abox(P + "_rbox", (2.6, 0.38, 0.4), tuple(T(0, 2.62, 2.65)), M, GT, bevel=0.015)
# armoured mantlet frame
abox(P + "_mantf", (1.0, 0.3, 1.0), tuple(T(0, -2.03, 2.28)), M, GT, bevel=0.03)
# roof: commander cupola (right) + NSVT 12.7 mm, gunner sights + 1P22 panoramic (left), loader hatch, vent domes
C = T(-0.85, -0.3, 2.95)
lathe_y(P + "_cup", [(0.0, 0.0), (0.0, 0.42), (0.16, 0.38), (0.16, 0.0)], tuple(C), [M], GT, seg=28, axis='Z')
acyl(P + "_cuplid", 0.32, 0.05, tuple(C + Vector((0, 0.05, 0.19))), M, GT, seg=24)
for k in range(5):
    a = math.radians(-60 + k * 30)
    periscope(P, "cup%d" % k, C + Vector((math.sin(a) * 0.38, -math.cos(a) * 0.38, 0.12)), GT, a)
abox(P + "_nsvtc", (0.12, 0.5, 0.14), tuple(C + Vector((0.0, -0.3, 0.42))), "M_Dark", GT, bevel=0.01)
acyl(P + "_nsvt", 0.024, 1.2, tuple(C + Vector((0.0, -1.1, 0.44))), "M_Dark", GT, axis=1, seg=10)
abox(P + "_nsvtb", (0.16, 0.26, 0.2), tuple(C + Vector((-0.2, -0.25, 0.36))), "M_DarkGreen", GT, bevel=0.01)
rod(P + "_nsvtp", C + Vector((0, -0.25, 0.2)), C + Vector((0, -0.3, 0.36)), 0.02, "M_Steel", GT, seg=6)
S = T(0.85, -1.3, 2.98)
abox(P + "_gsight", (0.36, 0.45, 0.3), tuple(S), M, GT, bevel=0.02)
abox(P + "_gsw", (0.26, 0.02, 0.16), tuple(S + Vector((0, -0.23, 0.03))), "M_Lens", GT, bevel=0.0, dirt=False)
pk = T(0.85, -0.55, 2.95)
acyl(P + "_pano", 0.12, 0.34, tuple(pk + Vector((0, 0, 0.17))), M, GT, seg=16)
acyl(P + "_panoh", 0.14, 0.2, tuple(pk + Vector((0, 0, 0.42))), "M_Dark", GT, seg=16)
abox(P + "_panow", (0.12, 0.02, 0.09), tuple(pk + Vector((0, -0.14, 0.43))), "M_Lens", GT, bevel=0.0, dirt=False)
hatch(P, "gnr", T(0.85, 0.35, 2.95), 0.28, GT)
hatch(P, "ldr", T(0.0, 1.3, 2.95), 0.28, GT)
for k, (x, y) in enumerate(((-0.6, 0.8), (-0.6, 1.6), (0.6, 1.8))):
    lathe_y("%s_vent%d" % (P, k), [(0.0, 0.0), (0.0, 0.18), (0.06, 0.16), (0.1, 0.0)], tuple(T(x, y, 2.95)), ["M_Dark"], GT, seg=16, axis='Z')
antenna(P, "a1", T(1.2, 2.1, 2.95), 2.4, GT)
antenna(P, "a2", T(-1.2, 2.1, 2.95), 1.4, GT)
rod(P + "_mast", T(-0.2, 2.2, 2.95), T(-0.2, 2.2, 3.4), 0.015, "M_Dark", GT, seg=6)

# ---------------------------------------------------------------- gun 2A64 L/47 + barrel
abox(P + "_cradle", (0.6, 1.0, 0.55), (0, TRN.y + 0.25, TRN.z), M, GG, bevel=0.02)
abox(P + "_mantm", (0.86, 0.28, 0.8), (0, TRN.y - 0.55, TRN.z), M, GG, bevel=0.04)
for sx in (1, -1):
    acyl("%s_recup%d" % (P, sx), 0.07, 1.3, (sx * 0.2, TRN.y - 0.3, TRN.z + 0.3), M, GG, axis=1, seg=12)
gun_tube(P, GB, TRN.y - 0.65, MUZ_Y, TRN.z, 0.1, 0.17, mat=M, sleeve=False, evac=(-3.9, 0.21, 1.0), brake=("2s19", 0.62))

# r5: every part must touch its node body (smoke dischargers, skirts, brackets, handwheels ...)
auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
weather(gen_weather([(1.75, 2.6, 1.26), (0, MUZ_Y, TRN.z)], 0.8))
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
rw_export("/home/user/.cache/rwveh/raw/rw_msta.glb", "/home/user/models/rw_msta.blend", "RW_2S19")
