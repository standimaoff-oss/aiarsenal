# M2A3 Bradley IFV (reply 12) — GunVehicle node contract (see t90m.py header).
# Blender: front = -Y, +X = vehicle left. glTF = (x, z, -y).
# Spec (Specs.BRADLEY): yawPivot glTF (-0.33, 1.98, -0.15) -> TC (-0.33, 0.15, 1.98);
# pitch rel (0, .34, 1.0) -> TRN (-0.33, -0.85, 2.32); tubes: M242 (0,0,2.75) -> muzzle y -3.60,
# TOW (1.22, .25, .25) -> box front (0.89, -1.10, 2.57) [box on the turret: SACLOS needs no elevation link],
# coax (-0.30, -0.04, 0.55) -> (-0.63, -1.40, 2.28).
# Real data: L 6.55 m, W 3.61 m (BRAT tiles), H 2.98 m (CIV), 6 road wheels 0.61 m, front sprocket, T157i 533 mm track.
reset_scene()
col_get("BUILD"); col_get("RIG")
amat("M_Body", "T_UsTan", 0.74, 0.05, factor=(0.95, 0.92, 0.86, 1))
amat("M_Tile", "T_UsTan", 0.78, 0.05, factor=(0.84, 0.80, 0.72, 1))
amat("M_TanPlain", None, 0.75, 0.05, factor=(0.55, 0.47, 0.33, 1))
amat("M_Canvas", None, 0.95, 0.0, factor=(0.45, 0.40, 0.29, 1))
armor_mats()
P = "BRAD"
G = P + "_hull"
GT = P + "_turret"
GG = P + "_gun"
GB = P + "_barrel"
M = "M_Body"
TX = 1.36
TW = 0.533
RR = 0.305
ZW = RR + 0.07
ROAD = [(-1.95 + k * 0.78, ZW) for k in range(6)]
SPK = (-2.78, 0.80)          # front drive sprocket
IDL = (2.80, 0.62)           # rear idler
ROLL = [(1.30, 0.93), (0.0, 0.94), (-1.30, 0.93)]
TC = Vector((-0.33, 0.15, 1.98))
TRN = Vector((-0.33, -0.85, 2.32))
MUZ_Y = TRN.y - 2.75

WHEELS, LINKS = [], []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = running_gear(P, side, x, ROAD, RR, SPK, 0.30, IDL, 0.27, ROLL, 0.10, TW, 0.152, 0.045,
                                   style="us", gap=0.09, teeth=11, mat=M, pad_mat="M_Rubber", arm_grp=G)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
for sx in (1, -1):
    for i in (0, 1, 5):
        y = ROAD[i][0]
        rod("%s_shock%d_%d" % (P, sx, i), Vector((sx * 1.08, y - 0.2, ZW + 0.06)), Vector((sx * 1.08, y - 0.04, ZW + 0.42)), 0.035, "M_Dark", G, seg=8)
    for i, (y, z) in enumerate(ROAD):
        abox("%s_bump%d_%d" % (P, sx, i), (0.08, 0.12, 0.1), (sx * 1.06, y - 0.18, z + 0.36), "M_Dark", G, bevel=0.01)
    acyl("%s_fdrv%d" % (P, sx), 0.25, 0.22, (sx * 1.13, SPK[0], SPK[1]), M, G, axis=0, seg=16)
    rod("%s_idlarm%d" % (P, sx), Vector((sx * 1.08, IDL[0] - 0.25, IDL[1] + 0.05)), Vector((sx * 1.22, IDL[0], IDL[1])), 0.06, M, G, seg=8)

# ---------------------------------------------------------------- hull (aluminium armour + steel appliqué)
hull_prof = [(-2.95, 0.45), (-3.28, 0.92), (-1.95, 1.98), (3.12, 1.98), (3.27, 1.84), (3.22, 0.55), (2.95, 0.45)]
prism_x(P + "_hull", hull_prof, 1.10, M, G, bevel=0.03)
spon = [(-3.0, 1.12), (-1.95, 1.98), (3.12, 1.98), (3.27, 1.84), (3.27, 1.12)]
for sx in (1, -1):
    prism_x("%s_spon%d" % (P, sx), spon, 0.26, M, G, bevel=0.02, x0=1.10 if sx > 0 else -1.62)
gl0, gl1 = Vector((0, -3.28, 0.92)), Vector((0, -1.95, 1.98))
ang = math.atan2(gl1.z - gl0.z, gl1.y - gl0.y)
nrm = Vector((0, -math.sin(ang), math.cos(ang)))
# bolted steel appliqué on the glacis (A2/A3) with bolt rows, engine air-intake grille on the right
c = gl0.lerp(gl1, 0.5) + nrm * 0.035
abox(P + "_glap", (3.05, 1.55, 0.05), tuple(c), M, G, rot=(ang, 0, 0), bevel=0.01)
for r, f in enumerate((0.12, 0.5, 0.88)):
    for k in range(9):
        p = gl0.lerp(gl1, f) + nrm * 0.065 + Vector((-1.4 + k * 0.35, 0, 0))
        acyl("%s_glb%d_%d" % (P, r, k), 0.018, 0.02, tuple(p), "M_Steel", G, rot=(ang - PI / 2, 0, 0), seg=6, dirt=False)
gi = gl0.lerp(gl1, 0.62) + nrm * 0.07 + Vector((-0.55, 0, 0))
abox(P + "_grillf", (1.0, 0.62, 0.03), tuple(gi), "M_Dark", G, rot=(ang, 0, 0), bevel=0.005)
for k in range(9):
    abox("%s_grill%d" % (P, k), (0.95, 0.025, 0.035), tuple(gi + Vector((0, 0, 0)) + (gl1 - gl0).normalized() * (-0.26 + k * 0.065) + nrm * 0.02), M, G, rot=(ang + 0.5, 0, 0), bevel=0.0)
# lower nose: tow hooks, splash lip, track-tension access plates
abox(P + "_lip", (2.3, 0.08, 0.1), (0, -3.24, 0.95), M, G, bevel=0.01)
for sx in (1, -1):
    lathe_y("%s_hook%d" % (P, sx), [(-0.03, 0.06), (-0.03, 0.1), (0.03, 0.1), (0.03, 0.06)], (sx * 0.72, -3.16, 0.7), ["M_Steel"], G, seg=12, axis='X')
    abox("%s_hookb%d" % (P, sx), (0.12, 0.12, 0.14), (sx * 0.72, -3.08, 0.7), M, G, bevel=0.01)
# headlight clusters with brush guards (front corners of the sponsons)
for sx in (1, -1):
    hc = Vector((sx * 1.36, -2.92, 1.28))
    abox("%s_hlb%d" % (P, sx), (0.3, 0.14, 0.2), tuple(hc), M, G, bevel=0.01)
    for j, dx in enumerate((-0.07, 0.07)):
        acyl("%s_hl%d_%d" % (P, sx, j), 0.05, 0.05, tuple(hc + Vector((dx, -0.08, 0.02))), "M_Dark", G, axis=1, seg=12)
        acyl("%s_hll%d_%d" % (P, sx, j), 0.042, 0.01, tuple(hc + Vector((dx, -0.106, 0.02))), "M_HeadLamp" if j == 0 else "M_Amber", G, axis=1, seg=12, dirt=False)
    for k in range(3):
        rod("%s_hlg%d_%d" % (P, sx, k), hc + Vector((-0.15 + k * 0.15, -0.16, -0.1)), hc + Vector((-0.15 + k * 0.15, -0.16, 0.14)), 0.01, "M_Steel", G, seg=5)
    rod("%s_hlgt%d" % (P, sx), hc + Vector((-0.16, -0.16, 0.14)), hc + Vector((0.16, -0.16, 0.14)), 0.01, "M_Steel", G, seg=5)
# driver: hatch (front left), 4 periscopes, wiper-less vision blocks; exhaust (right side) + louvres
hatch(P, "drv", (0.62, -1.62, 1.98), 0.30, G)
for k, yaw in enumerate((-0.6, -0.2, 0.2, 0.6)):
    periscope(P, "drv%d" % k, (0.62 + math.sin(yaw) * 0.36, -1.92 + abs(yaw) * 0.12, 2.0), G, yaw)
abox(P + "_exh", (0.06, 0.55, 0.3), (-1.645, -2.05, 1.55), "M_Dark", G, bevel=0.01)
slats(P + "_exhs", -1.68, -1.675, -2.28, -1.82, 1.55, 7, "M_Bore", G, along='Y')
slats(P + "_louv", -1.05, -0.15, -1.75, -1.15, 1.985, 6, "M_Dark", G, along='Y')
# roof: troop cargo hatch (rear), ventilators, turret ring guard
ch = Vector((0, 2.15, 1.98))
abox(P + "_cargo", (1.15, 0.95, 0.05), tuple(ch + Vector((0, 0, 0.025))), M, G, bevel=0.03)
acyl(P + "_cargoh", 0.03, 1.0, tuple(ch + Vector((0, -0.48, 0.06))), "M_Steel", G, axis=0, seg=8)
for sx in (1, -1):
    rod("%s_cargohd%d" % (P, sx), ch + Vector((sx * 0.3, 0.3, 0.07)), ch + Vector((sx * 0.3, 0.1, 0.07)), 0.012, "M_Steel", G, seg=5)
    acyl("%s_vent%d" % (P, sx), 0.09, 0.08, (sx * 0.85, 2.8, 2.02), "M_Dark", G, seg=12)
    acyl("%s_ventc%d" % (P, sx), 0.11, 0.03, (sx * 0.85, 2.8, 2.08), M, G, seg=12)
lathe_y(P + "_ringg", [(0.0, 1.18), (0.0, 1.24), (0.06, 1.24), (0.06, 1.18)], (TC.x, TC.y, 1.97), ["M_Dark"], G, seg=40, axis='Z')

# side protection: steel skirts with BRAT reactive tiles (front 2/3) + spaced plates, sponson tiles
for sx in (1, -1):
    x = sx * 1.70
    abox("%s_skirt%d" % (P, sx), (0.04, 6.0, 0.58), (x, 0.0, 0.93), M, G, bevel=0.006)
    tilted_box("%s_flapf%d" % (P, sx), (x - sx * 0.25, -3.0, 1.12), (x - sx * 0.25, -3.28, 0.9), 0.52, 0.02, "M_Rubber", G)
    tilted_box("%s_flapr%d" % (P, sx), (x - sx * 0.25, 3.27, 1.12), (x - sx * 0.25, 3.35, 0.75), 0.52, 0.02, "M_Rubber", G)
    for k in range(7):
        y = -2.62 + k * 0.6
        for r, z in enumerate((0.78, 1.08)):
            abox("%s_brat%d_%d_%d" % (P, sx, k, r), (0.09, 0.56, 0.27), (x + sx * 0.065, y, z), "M_Tile", G, bevel=0.012)
            for j in (-1, 1):
                acyl("%s_bratb%d_%d_%d_%d" % (P, sx, k, r, j), 0.013, 0.012, (x + sx * 0.115, y + j * 0.22, z + 0.09), "M_Steel", G, axis=0, seg=6, dirt=False)
    for k in range(3):
        y = 1.65 + k * 0.5
        abox("%s_spl%d_%d" % (P, sx, k), (0.05, 0.47, 0.5), (x + sx * 0.045, y, 0.93), M, G, bevel=0.01)
        for j in (-1, 1):
            for z in (0.75, 1.11):
                acyl("%s_splb%d_%d_%d_%d" % (P, sx, k, j, int(z * 100)), 0.014, 0.012, (x + sx * 0.075, y + j * 0.18, z), "M_Steel", G, axis=0, seg=6, dirt=False)
    # sponson-top stowage rails + tow cable on the left
    rod("%s_rail%d" % (P, sx), Vector((sx * 1.58, -1.2, 2.04)), Vector((sx * 1.58, 2.9, 2.04)), 0.016, "M_Steel", G, seg=6)
    for k in range(5):
        rod("%s_railp%d_%d" % (P, sx, k), Vector((sx * 1.58, -1.2 + k * 1.025, 1.98)), Vector((sx * 1.58, -1.2 + k * 1.025, 2.04)), 0.012, "M_Steel", G, seg=5)
tow_cable(P, "L", (1.45, -1.1, 2.01), (1.45, 1.8, 2.01), 0.015, G)
# rear: power ramp with door + hinges + rams, corner stowage boxes, lights, tow pintle
abox(P + "_ramp", (1.95, 0.07, 1.26), (0, 3.26, 1.16), M, G, bevel=0.015)
abox(P + "_rdoor", (0.62, 0.03, 1.05), (0.42, 3.31, 1.18), M, G, bevel=0.008)
rod(P + "_rdoorh", Vector((0.2, 3.335, 1.2)), Vector((0.2, 3.335, 1.4)), 0.012, "M_Steel", G, seg=5)
for sx in (1, -1):
    acyl("%s_rhinge%d" % (P, sx), 0.05, 0.3, (sx * 0.7, 3.25, 0.56), "M_Steel", G, axis=0, seg=10)
    rod("%s_rram%d" % (P, sx), Vector((sx * 0.95, 3.2, 0.62)), Vector((sx * 0.95, 3.24, 1.3)), 0.03, "M_Hydraulic", G, seg=8)
    bx = Vector((sx * 1.36, 3.12, 1.55))
    abox("%s_rbox%d" % (P, sx), (0.48, 0.42, 0.55), tuple(bx), M, G, bevel=0.02)
    abox("%s_rboxl%d" % (P, sx), (0.5, 0.04, 0.5), tuple(bx + Vector((0, 0.22, 0))), "M_TanPlain", G, bevel=0.005)
    rod("%s_rboxh%d" % (P, sx), bx + Vector((-0.12, 0.25, 0.1)), bx + Vector((0.12, 0.25, 0.1)), 0.01, "M_Steel", G, seg=5)
    abox("%s_tail%d" % (P, sx), (0.14, 0.05, 0.1), (sx * 1.36, 3.35, 1.18), "M_Dark", G, bevel=0.005)
    acyl("%s_taill%d" % (P, sx), 0.03, 0.01, (sx * 1.36 - 0.03, 3.38, 1.18), "M_NavRed", G, axis=1, seg=8, dirt=False)
    acyl("%s_tailb%d" % (P, sx), 0.03, 0.01, (sx * 1.36 + 0.03, 3.38, 1.18), "M_Amber", G, axis=1, seg=8, dirt=False)
    lathe_y("%s_rhook%d" % (P, sx), [(-0.03, 0.06), (-0.03, 0.1), (0.03, 0.1), (0.03, 0.06)], (sx * 0.95, 3.27, 0.62), ["M_Steel"], G, seg=12, axis='X')
abox(P + "_pintle", (0.16, 0.2, 0.12), (0, 3.33, 0.58), "M_Steel", G, bevel=0.01)
# interior visible through the open hatches: dark tub + troop benches + driver seat
abox(P + "_tub", (2.1, 5.9, 0.04), (0, 0.15, 0.62), "M_Interior", G, bevel=0.0)
for sx in (1, -1):
    abox("%s_bench%d" % (P, sx), (0.42, 2.2, 0.08), (sx * 0.55, 1.6, 0.92), "M_Seat", G, bevel=0.02)
    abox("%s_benchb%d" % (P, sx), (0.06, 2.2, 0.55), (sx * 0.92, 1.6, 1.25), "M_Seat", G, bevel=0.02)
abox(P + "_dseat", (0.45, 0.42, 0.1), (0.62, -1.45, 1.05), "M_Seat", G, bevel=0.03)
abox(P + "_dseatb", (0.45, 0.1, 0.55), (0.62, -1.22, 1.33), "M_Seat", G, rot=(-0.15, 0, 0), bevel=0.03)

# ---------------------------------------------------------------- turret (launcher_yaw)
def T(x, y, z):
    return Vector((TC.x + x, TC.y + y, z))
hb = [(0.0, -1.05), (0.72, -1.05), (0.98, -0.78), (1.0, 0.95), (0.86, 1.25), (0.0, 1.25)]
hm = [(0.0, -1.12), (0.76, -1.10), (1.02, -0.80), (1.03, 0.98), (0.88, 1.30), (0.0, 1.30)]
ht = [(0.0, -0.86), (0.6, -0.86), (0.84, -0.62), (0.88, 0.95), (0.74, 1.18), (0.0, 1.18)]
rings = [[T(x, y, z) for x, y in mirror_outline(h)] for h, z in ((hb, 1.99), (hm, 2.30), (ht, 2.64))]
loft_rings(P + "_tshell", rings, M, GT, bevel=0.025)
lathe_y(P + "_tring", [(0.0, 1.05), (0.0, 1.12), (0.05, 1.12), (0.05, 1.05)], (TC.x, TC.y, 1.99), ["M_Dark"], GT, seg=40, axis='Z')
# spaced armour on the turret sides and front (A3 appliqué) with bolt heads
for sx in (1, -1):
    for k in range(3):
        y = -0.55 + k * 0.55
        p = T(sx * 1.06, y, 2.28)
        abox("%s_tap%d_%d" % (P, sx, k), (0.05, 0.5, 0.5), tuple(p), M, GT, bevel=0.01)
        for j in (-1, 1):
            for dz in (-0.18, 0.18):
                acyl("%s_tapb%d_%d_%d_%d" % (P, sx, k, j, int(dz * 100)), 0.013, 0.012, tuple(p + Vector((sx * 0.03, j * 0.19, dz))), "M_Steel", GT, axis=0, seg=6, dirt=False)
abox(P + "_tfront", (1.55, 0.06, 0.52), tuple(T(0.0, -1.14, 2.26)), M, GT, bevel=0.01)
# mantlet housing + 25 mm shroud base
abox(P + "_manth", (0.62, 0.22, 0.46), tuple(T(0.0, -1.22, 2.30)), M, GT, bevel=0.03)
# IBAS gunner's sight (right of the gun) with ballistic doors, CIV commander's viewer (rear right)
S = T(-0.55, -0.80, 2.80)
abox(P + "_ibas", (0.46, 0.5, 0.32), tuple(S), M, GT, bevel=0.02)
abox(P + "_ibasp", (0.36, 0.36, 0.14), tuple(S + Vector((0, 0.05, -0.2))), M, GT, bevel=0.01)
abox(P + "_ibasw", (0.32, 0.02, 0.2), tuple(S + Vector((0, -0.26, 0.0))), "M_Lens", GT, bevel=0.0, dirt=False)
for sx in (1, -1):
    abox("%s_ibasd%d" % (P, sx), (0.2, 0.03, 0.26), tuple(S + Vector((sx * 0.29, -0.24, 0.0))), M, GT, rot=(0, 0, sx * 0.95), bevel=0.005)
C = T(-0.52, 0.62, 2.64)
acyl(P + "_civp", 0.11, 0.16, tuple(C + Vector((0, 0, 0.08))), M, GT, seg=14)
acyl(P + "_civh", 0.17, 0.2, tuple(C + Vector((0, 0, 0.26))), M, GT, seg=16)
abox(P + "_civw", (0.18, 0.02, 0.12), tuple(C + Vector((0, -0.17, 0.27))), "M_Lens", GT, bevel=0.0, dirt=False)
acyl(P + "_civc", 0.15, 0.03, tuple(C + Vector((0, 0, 0.37))), "M_Dark", GT, seg=16)
# hatches: commander (right) with periscope ring, gunner (left)
hatch(P, "cmd", T(-0.45, 0.05, 2.64), 0.30, GT)
for k in range(6):
    a = math.radians(-75 + k * 30)
    periscope(P, "cmd%d" % k, T(-0.45 + math.sin(a) * 0.4, 0.05 - math.cos(a) * 0.4, 2.66), GT, a)
hatch(P, "gnr", T(0.42, 0.05, 2.64), 0.27, GT)
for k in range(2):
    periscope(P, "gnr%d" % k, T(0.42 + (k - 0.5) * 0.3, -0.38, 2.66), GT, (k - 0.5) * 0.5)
# M257 smoke grenade launchers (4 tubes, front corners)
for sx in (1, -1):
    smoke_dischargers(P, "s%d" % sx, T(sx * 0.88, -0.98, 2.56), 4, 7.0, sx * 25.0, 20.0, GT, r=0.035, L=0.2, mat="M_TanPlain")
# turret stowage: bustle rack (wire basket) + side boxes, antennas, wind sensor
for k in range(9):
    x = -0.9 + k * 0.225
    rod("%s_bk%d" % (P, k), T(x, 1.62, 2.12), T(x, 1.62, 2.55), 0.011, "M_Dark", GT, seg=5)
for z in (2.14, 2.55):
    rod("%s_bkh%d" % (P, int(z * 100)), T(-0.92, 1.62, z), T(0.92, 1.62, z), 0.013, "M_Dark", GT, seg=5)
    for sx in (1, -1):
        rod("%s_bks%d_%d" % (P, sx, int(z * 100)), T(sx * 0.92, 1.62, z), T(sx * 0.86, 1.22, z), 0.013, "M_Dark", GT, seg=5)
abox(P + "_bkfloor", (1.8, 0.4, 0.03), tuple(T(0, 1.43, 2.13)), "M_Dark", GT, bevel=0.0)
for k, (dx, w) in enumerate(((-0.55, 0.62), (0.15, 0.55), (0.62, 0.3))):
    abox("%s_bag%d" % (P, k), (w, 0.32, 0.3), tuple(T(dx, 1.42, 2.30)), "M_Canvas", GT, bevel=0.06)
abox(P + "_tbox", (0.22, 0.75, 0.32), tuple(T(-1.13, 0.55, 2.2)), M, GT, bevel=0.015)
antenna(P, "a1", T(0.75, 1.1, 2.64), 2.3, GT)
antenna(P, "a2", T(-0.75, 1.1, 2.64), 2.3, GT)
rod(P + "_mast", T(0.1, 0.95, 2.64), T(0.1, 0.95, 2.95), 0.012, "M_Dark", GT, seg=6)
acyl(P + "_mastv", 0.035, 0.05, tuple(T(0.1, 0.95, 2.97)), "M_Dark", GT, seg=8)
# TOW-2B twin launcher in the raised (firing) position on its arm, left side of the turret
TB = Vector((0.89, -0.425, 2.57))
abox(P + "_towbox", (0.46, 1.35, 0.40), tuple(TB), M, GT, bevel=0.025)
abox(P + "_towrib", (0.48, 0.06, 0.42), tuple(TB + Vector((0, 0.35, 0))), M, GT, bevel=0.01)
for sx in (1, -1):
    acyl("%s_towt%d" % (P, sx), 0.085, 0.04, tuple(TB + Vector((sx * 0.11, -0.69, 0.02))), "M_TanPlain", GT, axis=1, seg=16)
    acyl("%s_towbo%d" % (P, sx), 0.072, 0.01, tuple(TB + Vector((sx * 0.11, -0.711, 0.02))), "M_Bore", GT, axis=1, seg=16, dirt=False)
abox(P + "_towarm", (0.14, 0.5, 0.18), tuple(TB + Vector((-0.24, 0.45, -0.2))), M, GT, rot=(0.35, 0, 0), bevel=0.01)
rod(P + "_towram", TB + Vector((-0.2, 0.1, -0.2)), T(1.0, -0.2, 2.1), 0.025, "M_Hydraulic", GT, seg=8)
# visible TOW-2B noses in the tubes (hidden when fired: nodes tow_l / tow_r)
GTL, GTR = P + "_towl", P + "_towr"
for g, sx in ((GTL, 1), (GTR, -1)):
    c0 = TB + Vector((sx * 0.11, -0.64, 0.02))
    lathe_y(g + "_nose", [(0.0, 0.0), (0.0, 0.06), (-0.06, 0.06), (-0.10, 0.03), (-0.11, 0.0)], tuple(c0), ["M_Olive"], g, seg=14, axis='Y')
    acyl(g + "_probe", 0.012, 0.12, tuple(c0 + Vector((0, -0.12, 0))), "M_Dark", g, axis=1, seg=6)

# ---------------------------------------------------------------- gun (launcher_pitch) + barrel (recoil)
abox(P + "_cradle", (0.36, 0.7, 0.3), (TRN.x, TRN.y + 0.25, TRN.z), M, GG, bevel=0.02)
abox(P + "_mantm", (0.5, 0.2, 0.36), (TRN.x, TRN.y - 0.48, TRN.z), M, GG, bevel=0.03)
acyl(P + "_shroud", 0.075, 0.5, (TRN.x, TRN.y - 0.82, TRN.z), M, GG, axis=1, seg=16)
acyl(P + "_shroudr", 0.085, 0.05, (TRN.x, TRN.y - 1.06, TRN.z), "M_Dark", GG, axis=1, seg=16)
# M240C coax port + barrel stub (right of the gun)
acyl(P + "_coaxp", 0.04, 0.08, (TRN.x - 0.30, TRN.y - 0.54, TRN.z - 0.04), "M_Dark", GG, axis=1, seg=10)
acyl(P + "_coax", 0.014, 0.2, (TRN.x - 0.30, TRN.y - 0.66, TRN.z - 0.04), "M_Dark", GG, axis=1, seg=8)
# M242 Bushmaster: slim barrel, flash suppressor with slots
acyl(P + "_brl", 0.034, MUZ_Y + 1.05 - TRN.y, (TRN.x, (TRN.y - 1.05 + MUZ_Y + 0.2) / 2, TRN.z), "M_Dark", GB, axis=1, seg=14, r2=0.03)
acyl(P + "_brlm", 0.045, 0.22, (TRN.x, MUZ_Y + 0.11, TRN.z), "M_Dark", GB, axis=1, seg=14)
for k in range(3):
    for sx in (1, -1):
        abox("%s_brls%d_%d" % (P, k, sx), (0.01, 0.04, 0.06), (TRN.x + sx * 0.045, MUZ_Y + 0.05 + k * 0.06, TRN.z), "M_Bore", GB, bevel=0.0, dirt=False)
acyl(P + "_bore", 0.016, 0.01, (TRN.x, MUZ_Y - 0.002, TRN.z), "M_Bore", GB, axis=1, seg=10, dirt=False)

# ---------------------------------------------------------------- hierarchy + export
auto_attach([G, GT, GG, GB, GTL, GTR], bracket_groups=(GB, GTL, GTR))
weather(gen_weather([(-1.645, -2.05, 1.55)], 0.8))
rt = root("root")
group_under(G, P + "_hull_geo", rt)
track_rig(rt, P, "", WHEELS)
for o in LINKS:
    attach(o, rt)
ty = pivot("launcher_yaw", tuple(TC), rt)
group_under(GT, "launcher_yaw_geo", ty)
for g, n in ((GTL, "tow_l"), (GTR, "tow_r")):
    e = pivot(n, tuple(TB), ty)
    group_under(g, n + "_geo", e)
gp = pivot("launcher_pitch", tuple(TRN), ty)
group_under(GG, "launcher_pitch_geo", gp)
br = pivot("barrel", tuple(TRN), gp)
group_under(GB, "barrel_geo", br)
rw_export("/home/user/.cache/rwveh/raw/rw_bradley.glb", "/home/user/models/rw_bradley.blend", "RW_M2A3")
