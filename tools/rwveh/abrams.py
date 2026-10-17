# M1A2 SEPv3 Abrams — GunVehicle node contract (same as t90m.py).
# Spec: yawPivot glTF (0,1.62,0.10) -> Blender TC (0,-0.10,1.62); pitch rel (0,.38,1.25) -> TRN (0,-1.35,2.00); muzzle y -6.00.
# Desert tan (M_Sand). Blender: front = -Y, +X = vehicle left.
reset_scene()
col_get("BUILD"); col_get("RIG")
armor_mats()
P = "M1A2"
G = P + "_hull"
GT = P + "_turret"
GG = P + "_gun"
GB = P + "_barrel"
M = "M_Sand"
TX = 1.43
TW = 0.635
RR = 0.32
ZW = RR + 0.06
ROAD = [(-2.31 + k * 0.77, ZW) for k in range(7)]
SPK = (2.98, 0.70)
IDL = (-3.08, 0.64)
ROLL = [(1.05, 0.90), (-1.05, 0.90)]
TC = Vector((0, -0.10, 1.62))
TRN = Vector((0, -1.35, 2.00))
MUZ_Y = -6.00

# ---------------------------------------------------------------- running gear
WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = running_gear(P, side, x, ROAD, RR, SPK, 0.33, IDL, 0.32, ROLL, 0.11, TW, 0.155, 0.05,
                                   style="us", gap=0.1, teeth=11, mat=M, arm_grp=G)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
for sx in (1, -1):
    for i, (y, z) in enumerate(ROAD):
        rod("%s_arm%d_%d" % (P, sx, i), Vector((sx * 1.10, y - 0.34, z + 0.08)), Vector((sx * 1.2, y, z)), 0.055, M, G, seg=8)
        abox("%s_bump%d_%d" % (P, sx, i), (0.09, 0.12, 0.1), (sx * 1.13, y - 0.16, z + 0.3), "M_Dark", G, bevel=0.01)
    for i in (0, 1, 6):
        y = ROAD[i][0]
        rod("%s_shock%d_%d" % (P, sx, i), Vector((sx * 1.12, y - 0.25, ZW + 0.08)), Vector((sx * 1.12, y - 0.05, ZW + 0.42)), 0.045, "M_Dark", G, seg=8)
    acyl("%s_fdrv%d" % (P, sx), 0.28, 0.24, (sx * 1.17, SPK[0], SPK[1]), M, G, axis=0, seg=16)
    rod("%s_idlarm%d" % (P, sx), Vector((sx * 1.12, IDL[0] + 0.3, IDL[1] + 0.02)), Vector((sx * 1.25, IDL[0], IDL[1])), 0.07, M, G, seg=8)

# ---------------------------------------------------------------- hull: lower tub + sponsons, very low glacis
hull_prof = [(-3.72, 0.48), (-4.02, 0.92), (-2.25, 1.37), (2.05, 1.40), (3.93, 1.37), (3.95, 0.62), (3.60, 0.48)]
prism_x(P + "_hull", hull_prof, 1.08, M, G, bevel=0.03)
spon = [(-3.50, 1.04), (-2.25, 1.37), (2.05, 1.40), (3.93, 1.37), (3.93, 1.04)]
for sx in (1, -1):
    prism_x("%s_spon%d" % (P, sx), spon, 0.30, M, G, bevel=0.02, x0=1.08 if sx > 0 else -1.68)
# composite side skirts: thick armoured front sections, thin rear panels, angled front ends
for sx in (1, -1):
    for k in range(3):
        y = -3.25 + k * 0.82
        abox("%s_skA%d_%d" % (P, sx, k), (0.10, 0.80, 0.70), (sx * 1.80, y, 0.98), M, G, bevel=0.012)
        for j in (-1, 1):
            acyl("%s_skb%d_%d_%d" % (P, sx, k, j), 0.016, 0.02, (sx * 1.86, y + 0.3 * j, 1.26), "M_Steel", G, axis=0, seg=6, dirt=False)
    for k in range(6):
        y = -0.68 + k * 0.72
        abox("%s_skB%d_%d" % (P, sx, k), (0.04, 0.70, 0.62), (sx * 1.78, y, 1.01), M, G, bevel=0.006)
    tilted_box("%s_sknose%d" % (P, sx), (sx * 1.80, -3.66, 1.33), (sx * 1.80, -3.95, 0.98), 0.10, 0.6, M, G)
    abox("%s_skrub%d" % (P, sx), (0.03, 6.9, 0.06), (sx * 1.80, 0.0, 0.64), "M_Rubber", G, bevel=0.0)
    tilted_box("%s_flap%d" % (P, sx), (sx * 1.43, 3.93, 1.06), (sx * 1.43, 4.10, 0.82), 0.62, 0.02, "M_Rubber", G)
# glacis details: driver hatch + 3 periscopes, headlight clusters with guards, tow eyes, lower plate
hatch(P, "drv", (0, -2.55, 1.36), 0.3, G, mat=M)
for k, yaw in enumerate((-0.4, 0.0, 0.4)):
    periscope(P, "drv%d" % k, (math.sin(yaw) * 0.34, -2.88 + abs(yaw) * 0.1, 1.31), G, yaw)
gl0, gl1 = Vector((0, -4.02, 0.92)), Vector((0, -2.25, 1.37))
ang = math.atan2(gl1.z - gl0.z, gl1.y - gl0.y)
for sx in (1, -1):
    c = gl0.lerp(gl1, 0.18) + Vector((sx * 1.25, 0, 0.06))
    abox("%s_hlbox%d" % (P, sx), (0.32, 0.22, 0.14), tuple(c), "M_Dark", G, rot=(ang, 0, 0), bevel=0.01)
    for j in range(2):
        acyl("%s_hll%d_%d" % (P, sx, j), 0.05, 0.02, tuple(c + Vector((-0.08 + j * 0.16, -0.12, 0.02))), "M_HeadLamp", G, axis=1, seg=12, dirt=False)
    for k in range(3):
        rod("%s_hlg%d_%d" % (P, sx, k), c + Vector((-0.17 + k * 0.17, -0.17, -0.05)), c + Vector((-0.17 + k * 0.17, -0.12, 0.13)), 0.011, "M_Steel", G, seg=5)
    lathe_y("%s_hook%d" % (P, sx), [(-0.035, 0.06), (-0.035, 0.11), (0.035, 0.11), (0.035, 0.06)], (sx * 0.8, -3.92, 0.75), ["M_Steel"], G, seg=12, axis='X')
    abox("%s_hookb%d" % (P, sx), (0.12, 0.12, 0.14), (sx * 0.8, -3.84, 0.75), M, G, bevel=0.01)
    # mine-plough mounting lugs
    abox("%s_lug%d" % (P, sx), (0.08, 0.16, 0.2), (sx * 0.45, -3.86, 0.62), M, G, bevel=0.01)
# rear: AGT-1500 turbine exhaust grille, louvred deck, lights, phone box, tow pintle
abox(P + "_grillf", (1.7, 0.06, 0.62), (0, 3.97, 1.0), "M_Dark", G, bevel=0.01)
for k in range(9):
    abox("%s_grill%d" % (P, k), (1.6, 0.05, 0.035), (0, 4.01, 0.76 + k * 0.06), "M_Bore", G, rot=(0.5, 0, 0), bevel=0.0)
for k in range(7):
    abox("%s_grillv%d" % (P, k), (0.02, 0.05, 0.6), (-0.75 + k * 0.25, 4.02, 1.0), "M_Dark", G, bevel=0.0)
slats(P + "_louv", -0.95, 0.95, 1.9, 3.1, 1.405, 12, "M_Dark", G, along='Y')
slats(P + "_louv2", -0.95, 0.95, 3.25, 3.85, 1.38, 6, "M_Dark", G, along='Y')
for sx in (1, -1):
    abox("%s_deckhd%d" % (P, sx), (0.06, 1.9, 0.04), (sx * 1.0, 2.85, 1.42), M, G, bevel=0.005)
    abox("%s_tail%d" % (P, sx), (0.16, 0.06, 0.12), (sx * 1.25, 3.96, 1.28), "M_Dark", G, bevel=0.005)
    acyl("%s_taill%d" % (P, sx), 0.035, 0.012, (sx * 1.25, 4.0, 1.30), "M_Amber", G, axis=1, seg=8, dirt=False)
    lathe_y("%s_rhook%d" % (P, sx), [(-0.035, 0.06), (-0.035, 0.11), (0.035, 0.11), (0.035, 0.06)], (sx * 1.0, 3.98, 0.66), ["M_Steel"], G, seg=12, axis='X')
abox(P + "_phone", (0.18, 0.1, 0.24), (-1.25, 3.97, 1.05), M, G, bevel=0.01)
acyl(P + "_pintle", 0.06, 0.18, (0, 4.0, 0.62), "M_Steel", G, axis=1, seg=10)
# fender stowage boxes on the sponsons, spare track blocks, tow cable
for sx in (1, -1):
    for k in range(2):
        abox("%s_box%d_%d" % (P, sx, k), (0.32, 0.9, 0.24), (sx * 1.52, 2.2 + k * 1.0, 1.52), M, G, bevel=0.015)
        abox("%s_boxl%d_%d" % (P, sx, k), (0.34, 0.92, 0.025), (sx * 1.52, 2.2 + k * 1.0, 1.645), "M_SandPlain", G, bevel=0.004)
tow_cable(P, "L", (1.55, -3.2, 1.30), (1.55, -1.6, 1.42), 0.03, G)

# ---------------------------------------------------------------- turret (launcher_yaw): flat slab armour + long bustle
def T(x, y, z):
    return Vector((x, TC.y + y, z))
half = [(0.0, -1.62), (0.42, -1.74), (1.30, -1.32), (1.70, -0.55), (1.70, 1.20), (1.52, 1.60), (1.38, 2.55), (0.0, 2.58)]
rings = []
for z, s, dy in ((1.64, 0.93, 0.10), (1.82, 1.0, 0.0), (2.36, 0.93, 0.10)):
    rings.append([T(x * s, y + (dy if y < 0 else -dy * 0.3), z) for x, y in mirror_outline(half)])
loft_rings(P + "_tshell", rings, M, GT, bevel=0.025)
lathe_y(P + "_tring", [(0.0, 1.05), (0.0, 1.13), (0.07, 1.13), (0.07, 1.05)], (0, TC.y, 1.60), ["M_Dark"], GT, seg=40, axis='Z')
# front armour panel seams + lifting eyes
for sx in (1, -1):
    for f in (0.35, 0.7):
        x0, y0 = 0.42, -1.74
        x1, y1 = 1.30, -1.32
        px, py = x0 + (x1 - x0) * f, y0 + (y1 - y0) * f
        abox("%s_fseam%d_%d" % (P, sx, int(f * 10)), (0.012, 0.012, 0.5), tuple(T(sx * px, py - 0.004, 2.06)), "M_Dark", GT, rot=(0, 0, -sx * 0.45), bevel=0.0)
    for y in (-0.9, 1.6):
        lathe_y("%s_lift%d_%d" % (P, sx, int(y * 10)), [(-0.02, 0.03), (-0.02, 0.06), (0.02, 0.06), (0.02, 0.03)], tuple(T(sx * 1.45, y, 2.40)), ["M_Steel"], GT, seg=10, axis='X')
# bustle rack (basket) with stowed bags
for k in range(11):
    x = -1.3 + k * 0.26
    rod("%s_rk%d" % (P, k), T(x, 3.15, 1.95), T(x, 3.15, 2.40), 0.013, "M_Dark", GT, seg=5)
for z in (1.95, 2.40):
    rod("%s_rkh%d" % (P, int(z * 100)), T(-1.32, 3.15, z), T(1.32, 3.15, z), 0.016, "M_Dark", GT, seg=5)
    for sx in (1, -1):
        rod("%s_rks%d_%d" % (P, sx, int(z * 100)), T(sx * 1.32, 3.15, z), T(sx * 1.40, 2.4, z), 0.016, "M_Dark", GT, seg=5)
abox(P + "_rkfloor", (2.6, 0.62, 0.03), tuple(T(0, 2.86, 1.95)), "M_Mesh", GT, bevel=0.0)
for k, (x, w) in enumerate(((-0.85, 0.7), (0.0, 0.8), (0.82, 0.6))):
    abox("%s_bag%d" % (P, k), (w, 0.5, 0.32), tuple(T(x, 2.85, 2.13)), "M_Canvas", GT, bevel=0.06)
# mantlet + gun shield recess
abox(P + "_mantf", (0.72, 0.30, 0.62), tuple(T(0, -1.62, 2.02)), M, GT, bevel=0.03)
# gunner's primary sight doghouse (right front) + CITV (left front)
S = T(-0.72, -1.05, 2.48)
abox(P + "_gps", (0.55, 0.62, 0.26), tuple(S), M, GT, bevel=0.02)
abox(P + "_gpsw", (0.42, 0.02, 0.15), tuple(S + Vector((0, -0.32, 0.02))), "M_Lens", GT, bevel=0.0, dirt=False)
abox(P + "_gpsd", (0.48, 0.04, 0.2), tuple(S + Vector((0, -0.33, 0.12))), M, GT, rot=(-0.9, 0, 0), bevel=0.004)
CI = T(0.72, -0.95, 2.38)
acyl(P + "_citvb", 0.16, 0.2, tuple(CI + Vector((0, 0, 0.08))), M, GT, seg=20)
abox(P + "_citvh", (0.34, 0.36, 0.3), tuple(CI + Vector((0, 0, 0.33))), M, GT, bevel=0.03)
abox(P + "_citvw", (0.24, 0.02, 0.16), tuple(CI + Vector((0, -0.19, 0.34))), "M_Lens", GT, bevel=0.0, dirt=False)
# commander cupola + CROWS II RWS (M2HB) on the right, loader hatch + M240 skate mount on the left
C = T(-0.68, 0.35, 2.36)
lathe_y(P + "_cup", [(0.0, 0.0), (0.0, 0.40), (0.14, 0.36), (0.14, 0.0)], tuple(C), [M], GT, seg=28, axis='Z')
acyl(P + "_cuplid", 0.3, 0.05, tuple(C + Vector((0, 0.05, 0.17))), M, GT, seg=24)
for k in range(6):
    a = math.radians(-75 + k * 30)
    periscope(P, "cup%d" % k, C + Vector((math.sin(a) * 0.37, -math.cos(a) * 0.37, 0.09)), GT, a)
R = C + Vector((0.0, -0.25, 0.42))
acyl(P + "_crowsb", 0.14, 0.16, tuple(R + Vector((0, 0, -0.12))), "M_Dark", GT, seg=16)
abox(P + "_crowsc", (0.26, 0.44, 0.24), tuple(R + Vector((0.22, 0, 0.06))), M, GT, bevel=0.02)
abox(P + "_crowsw", (0.18, 0.02, 0.12), tuple(R + Vector((0.22, -0.23, 0.08))), "M_Lens", GT, bevel=0.0, dirt=False)
abox(P + "_m2r", (0.14, 0.6, 0.16), tuple(R + Vector((-0.05, 0.0, 0.06))), "M_Dark", GT, bevel=0.01)
acyl(P + "_m2", 0.026, 1.15, tuple(R + Vector((-0.05, -0.85, 0.08))), "M_Dark", GT, axis=1, seg=10)
acyl(P + "_m2j", 0.045, 0.6, tuple(R + Vector((-0.05, -0.55, 0.08))), "M_Dark", GT, axis=1, seg=10)
abox(P + "_m2box", (0.16, 0.3, 0.22), tuple(R + Vector((-0.25, 0.05, 0.02))), "M_Olive", GT, bevel=0.01)
L = T(0.62, 0.45, 2.36)
hatch(P, "ldr", L, 0.3, GT, mat=M)
rod(P + "_skate", L + Vector((-0.4, 0.0, 0.1)), L + Vector((0.4, 0.0, 0.1)), 0.02, "M_Steel", GT, seg=6)
abox(P + "_m240r", (0.1, 0.5, 0.12), tuple(L + Vector((0.0, -0.35, 0.28))), "M_Dark", GT, bevel=0.008)
acyl(P + "_m240", 0.018, 0.55, tuple(L + Vector((0.0, -0.85, 0.30))), "M_Dark", GT, axis=1, seg=8)
rod(P + "_m240post", L + Vector((0.0, -0.2, 0.1)), L + Vector((0.0, -0.3, 0.26)), 0.02, "M_Steel", GT, seg=6)
abox(P + "_m240sh", (0.42, 0.03, 0.3), tuple(L + Vector((0.0, -0.62, 0.33))), M, GT, bevel=0.005)
# M250 smoke grenade launchers (2x6), wind sensor mast, antennas, SEPv3 ADL bump, exhaust deflector stub
for sx in (1, -1):
    smoke_dischargers(P, "s%d" % sx, T(sx * 1.6, -0.55, 2.2), 6, 7.0, sx * 25.0, 15.0, GT, r=0.042, L=0.2, mat="M_SandPlain")
    abox("%s_tbox%d" % (P, sx), (0.22, 1.1, 0.36), tuple(T(sx * 1.78, 0.75, 1.98)), M, GT, bevel=0.015)
rod(P + "_mast", T(0.05, 1.9, 2.37), T(0.05, 1.9, 2.85), 0.015, "M_Dark", GT, seg=6)
acyl(P + "_mastv", 0.045, 0.06, tuple(T(0.05, 1.9, 2.88)), "M_Dark", GT, seg=8)
antenna(P, "a1", T(1.2, 2.2, 2.37), 2.2, GT)
antenna(P, "a2", T(-1.2, 2.2, 2.37), 1.4, GT)
abox(P + "_adl", (0.4, 0.3, 0.12), tuple(T(-0.25, -1.25, 2.42)), M, GT, bevel=0.02)

# ---------------------------------------------------------------- gun + barrel
abox(P + "_cradle", (0.52, 0.8, 0.44), (0, TRN.y + 0.18, TRN.z), M, GG, bevel=0.02)
abox(P + "_mantm", (0.50, 0.24, 0.44), (0, TRN.y - 0.3, TRN.z), M, GG, bevel=0.03)
lathe_y(P + "_dustc", [(0.0, 0.18), (0.12, 0.21), (0.22, 0.16)], (0, TRN.y - 0.44, TRN.z), ["M_Canvas"], GG, seg=16, axis='Y')
acyl(P + "_coax", 0.02, 0.25, (-0.34, TRN.y - 0.45, TRN.z - 0.04), "M_Dark", GG, axis=1, seg=8)
gun_tube(P, GB, TRN.y - 0.35, MUZ_Y, TRN.z, 0.088, 0.14, mat=M, sleeve=True, evac=(TRN.y - 2.1, 0.17, 0.55), mrs=True)

# ---------------------------------------------------------------- hierarchy + export
# r5: every part must touch its node body (smoke dischargers, skirts, brackets, handwheels ...)
auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
weather(gen_weather([(0.0, 4.0, 1.0)], 0.9))
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
rw_export("/home/user/.cache/rwveh/raw/rw_abrams.glb", "/home/user/models/rw_abrams.blend", "RW_M1A2")
