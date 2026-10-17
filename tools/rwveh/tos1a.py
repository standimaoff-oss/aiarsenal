# TOS-1A "Solntsepyok" heavy flamethrower system (reply 12) — GunVehicle node contract.
# T-72A hull/running gear (shared numbers with t90m.py), 24-tube pack (4 rows x 6) on a +-30 deg turntable.
# Spec (Specs.TOS1A): yawPivot glTF (0, 1.47, -0.10) -> TC (0, 0.10, 1.47); pitch rel (0, .31, -.95) -> TRN (0, 1.05, 1.78);
# tubes: x (c-2.5)*0.26, y 0.22+0.255 r, z 4.75 (front) -> muzzles at Blender y -3.70. Rocket props rkt_k (k = r*6+c).
# Elevation rams: B = TRN + glTF(y -0.05, z 1.6), A = TRN + glTF(y 0.05, z 2.4) (GunVehicle.RAM_B / RAM_A).
reset_scene()
col_get("BUILD"); col_get("RIG")
amat("M_Body", "T_RuGreen3", 0.72, 0.05, factor=(0.92, 0.96, 0.86, 1))
armor_mats()
P = "TOS"
G = P + "_hull"
GT = P + "_turret"
GG = P + "_gun"
TX = 1.395
TW = 0.58
RR = 0.375
ZW = RR + 0.07
ROAD = [(-2.15 + k * 0.812, ZW) for k in range(6)]
SPK = (2.86, 0.70)
IDL = (-2.93, 0.62)
ROLL = [(1.25, 0.975), (0.0, 0.985), (-1.25, 0.975)]
TC = Vector((0, 0.10, 1.47))
TRN = Vector((0, 1.05, 1.78))
RAM_B = Vector((0, TRN.y - 1.6, TRN.z - 0.05))
RAM_A = Vector((0, TRN.y - 2.4, TRN.z + 0.05))

# ---------------------------------------------------------------- running gear (both sides)
WHEELS = []
LINKS = []
for side, x in (("L", TX), ("R", -TX)):
    W, links, pitch = running_gear(P, side, x, ROAD, RR, SPK, 0.33, IDL, 0.28, ROLL, 0.115, TW, 0.137, 0.05,
                                   style="ru", gap=0.09, teeth=13, mat="M_Body", arm_grp=G)
    WHEELS += W
    LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS))
# suspension: torsion-bar arms + bump stops + hydraulic shocks on wheels 1,2,6
for sx in (1, -1):
    for i, (y, z) in enumerate(ROAD):
        rod("%s_arm%d_%d" % (P, sx, i), Vector((sx * 1.10, y + 0.32, z + 0.06)), Vector((sx * 1.22, y, z)), 0.05, "M_Body", G, seg=8)
        abox("%s_bump%d_%d" % (P, sx, i), (0.08, 0.12, 0.1), (sx * 1.12, y + 0.18, z + 0.32), "M_Dark", G, bevel=0.01)
    for i in (0, 1, 5):
        y = ROAD[i][0]
        rod("%s_shock%d_%d" % (P, sx, i), Vector((sx * 1.12, y + 0.25, ZW + 0.08)), Vector((sx * 1.12, y + 0.05, ZW + 0.45)), 0.04, "M_Dark", G, seg=8)
    rod("%s_idlarm%d" % (P, sx), Vector((sx * 1.12, IDL[0] + 0.25, IDL[1] + 0.05)), Vector((sx * 1.25, IDL[0], IDL[1])), 0.07, "M_Body", G, seg=8)
    acyl("%s_fdrv%d" % (P, sx), 0.26, 0.22, (sx * 1.17, SPK[0], SPK[1]), "M_Body", G, axis=0, seg=16)

# ---------------------------------------------------------------- hull
hull_prof = [(-3.00, 0.47), (-3.43, 0.86), (-2.05, 1.44), (2.15, 1.47), (3.43, 1.40), (3.40, 0.64), (2.95, 0.47)]
prism_x(P + "_hull", hull_prof, 1.08, "M_Body", G, bevel=0.03)
# fender shelves over the tracks + front mud flaps
for sx in (1, -1):
    abox("%s_fender%d" % (P, sx), (0.66, 6.5, 0.04), (sx * 1.41, 0.12, 1.16), "M_Body", G, bevel=0.01)
    tilted_box("%s_flap%d" % (P, sx), (sx * 1.41, -3.13, 1.16), (sx * 1.41, -3.42, 0.98), 0.66, 0.025, "M_Rubber", G)
    abox("%s_fendlip%d" % (P, sx), (0.03, 6.5, 0.07), (sx * 1.735, 0.12, 1.15), "M_Body", G, bevel=0.005)
# side skirts: plain steel panels with rubber lower edge (T-72A / TOS-1A)
for sx in (1, -1):
    abox("%s_skirt%d" % (P, sx), (0.025, 6.3, 0.46), (sx * 1.74, 0.05, 0.90), "M_Body", G, bevel=0.006)
    abox("%s_skirtr%d" % (P, sx), (0.02, 6.3, 0.08), (sx * 1.74, 0.05, 0.64), "M_Rubber", G, bevel=0.004)
    for k in range(7):
        acyl("%s_skb%d_%d" % (P, sx, k), 0.015, 0.012, (sx * 1.755, -2.9 + k * 0.97, 1.08), "M_Steel", G, axis=0, seg=6, dirt=False)
gl0, gl1 = Vector((0, -3.43, 0.86)), Vector((0, -2.05, 1.44))
ang = math.atan2(gl1.z - gl0.z, gl1.y - gl0.y)
nrm = Vector((0, -math.sin(ang), math.cos(ang)))
# lower front plate: dozer-blade mount + tow hooks + track-width mine-plough lugs
abox(P + "_lfp_blade", (2.0, 0.06, 0.18), (0, -3.28, 0.62), "M_Body", G, rot=(0.6, 0, 0), bevel=0.01)
for sx in (1, -1):
    lathe_y("%s_hook%d" % (P, sx), [(-0.03, 0.06), (-0.03, 0.1), (0.03, 0.1), (0.03, 0.06)], (sx * 0.75, -3.36, 0.74), ["M_Steel"], G, seg=12, axis='X')
    abox("%s_hookb%d" % (P, sx), (0.1, 0.1, 0.12), (sx * 0.75, -3.3, 0.74), "M_Body", G, bevel=0.01)
# headlights with guards (left: IR + white)
for sx in (1, -1):
    c = Vector((sx * 1.15, -3.08, 1.24))
    acyl("%s_hl%d" % (P, sx), 0.085, 0.12, tuple(c), "M_Dark", G, axis=1, seg=14)
    acyl("%s_hll%d" % (P, sx), 0.07, 0.01, tuple(c + Vector((0, -0.065, 0))), "M_HeadLamp", G, axis=1, seg=14, dirt=False)
    for k in range(3):
        rod("%s_hlg%d_%d" % (P, sx, k), c + Vector((-0.12 + k * 0.12, -0.12, -0.08)), c + Vector((-0.12 + k * 0.12, -0.12, 0.12)), 0.01, "M_Steel", G, seg=5)
# driver hatch + 3 periscopes (centre)
hatch(P, "drv", (0, -2.36, 1.46), 0.27, G)
for k, yaw in enumerate((-0.45, 0.0, 0.45)):
    periscope(P, "drv%d" % k, (math.sin(yaw) * 0.3, -2.62 + abs(yaw) * 0.12, 1.47), G, yaw)
abox(P + "_wiper", (0.5, 0.03, 0.06), (0, -2.72, 1.42), "M_Dark", G, bevel=0.0)
# engine deck: louvers, filler caps, unditching beam, two fuel drums, rear lights, exhaust (left side)
slats(P + "_louv", -0.85, 0.85, 1.55, 2.45, 1.475, 11, "M_Dark", G, along='Y')
slats(P + "_louv2", -0.85, 0.85, 2.7, 3.25, 1.43, 6, "M_Dark", G, along='Y')
for sx in (1, -1):
    acyl("%s_filler%d" % (P, sx), 0.07, 0.04, (sx * 0.75, 1.15, 1.49), "M_Steel", G, seg=12)
log = Vector((0, 3.64, 1.52))
acyl(P + "_log", 0.15, 3.0, tuple(log), "M_Seat", G, axis=0, seg=14)
for sx in (1, -1):
    rod("%s_logch%d" % (P, sx), log + Vector((sx * 1.1, 0, 0.15)), log + Vector((sx * 1.1, -0.25, 0.0)), 0.012, "M_Steel", G, seg=5)
    drum = Vector((sx * 0.62, 3.62, 0.98))
    acyl("%s_drum%d" % (P, sx), 0.29, 0.88, tuple(drum), "M_DarkGreen", G, axis=0, seg=20)
    for dx in (-0.3, 0.0, 0.3):
        acyl("%s_drumr%d_%d" % (P, sx, int(dx * 10)), 0.30, 0.025, tuple(drum + Vector((dx, 0, 0))), "M_DarkGreen", G, axis=0, seg=20)
    abox("%s_drumbr%d" % (P, sx), (0.92, 0.08, 0.06), tuple(drum + Vector((0, -0.25, -0.22))), "M_Body", G, bevel=0.005)
    abox("%s_tail%d" % (P, sx), (0.12, 0.05, 0.08), (sx * 1.25, 3.44, 1.3), "M_Dark", G, bevel=0.005)
    acyl("%s_taill%d" % (P, sx), 0.03, 0.01, (sx * 1.25, 3.47, 1.3), "M_Amber", G, axis=1, seg=8, dirt=False)
    lathe_y("%s_rhook%d" % (P, sx), [(-0.03, 0.06), (-0.03, 0.1), (0.03, 0.1), (0.03, 0.06)], (sx * 0.9, 3.45, 0.72), ["M_Steel"], G, seg=12, axis='X')
abox(P + "_exh", (0.06, 0.6, 0.22), (1.74, 2.55, 1.25), "M_Dark", G, bevel=0.01)
slats(P + "_exhs", 1.765, 1.775, 2.3, 2.8, 1.25, 6, "M_Bore", G, along='Y')
# fender stowage: tool boxes + spare track links + tow cable
for sx in (1, -1):
    for k in range(3):
        abox("%s_box%d_%d" % (P, sx, k), (0.5, 0.7, 0.32), (sx * 1.42, -1.6 + k * 0.85, 1.34), "M_Body", G, bevel=0.015)
        abox("%s_boxl%d_%d" % (P, sx, k), (0.52, 0.72, 0.03), (sx * 1.42, -1.6 + k * 0.85, 1.5), "M_Plain", G, bevel=0.005)
        abox("%s_boxh%d_%d" % (P, sx, k), (0.02, 0.12, 0.03), (sx * 1.68, -1.6 + k * 0.85, 1.42), "M_Steel", G, bevel=0.0)
    for k in range(4):
        abox("%s_spare%d_%d" % (P, sx, k), (0.5, 0.13, 0.04), (sx * 1.4, 1.3 + k * 0.15, 1.2), "M_Track", G, bevel=0.005)
tow_cable(P, "L", (1.15, -2.8, 1.52), (1.15, -0.6, 1.52), 0.02, G)

# travel rest for the pack on the glacis (A-frame with rubber pad)
for sx in (1, -1):
    rod("%s_rest%d" % (P, sx), Vector((sx * 0.62, -3.0, 1.04)), Vector((sx * 0.45, -3.0, 1.80)), 0.035, "M_Body", G, seg=8)
abox(P + "_restbar", (1.05, 0.12, 0.08), (0, -3.0, 1.80), "M_Body", G, bevel=0.01)
abox(P + "_restpad", (0.8, 0.14, 0.03), (0, -3.0, 1.84), "M_Rubber", G, bevel=0.005)

# ---------------------------------------------------------------- turntable (launcher_yaw)
def T(x, y, z):
    return Vector((x, TC.y + y, z))
hb = [(0.0, -1.15), (0.9, -1.10), (1.25, -0.6), (1.28, 0.7), (1.0, 1.25), (0.0, 1.3)]
ht = [(0.0, -1.05), (0.85, -1.0), (1.17, -0.55), (1.2, 0.65), (0.94, 1.17), (0.0, 1.22)]
loft_rings(P + "_table", [[T(x, y, 1.48) for x, y in mirror_outline(hb)], [T(x, y, 1.70) for x, y in mirror_outline(ht)]], "M_Body", GT, bevel=0.02)
lathe_y(P + "_tring", [(0.0, 1.0), (0.0, 1.08), (0.05, 1.08), (0.05, 1.0)], (0, TC.y, 1.47), ["M_Dark"], GT, seg=40, axis='Z')
# trunnion pedestals (rear) with bearing bosses
for sx in (1, -1):
    abox("%s_ped%d" % (P, sx), (0.14, 0.5, 0.2), (sx * 1.0, TRN.y, 1.76), "M_Body", GT, bevel=0.015)
    acyl("%s_boss%d" % (P, sx), 0.11, 0.12, (sx * 1.0, TRN.y, TRN.z), "M_Body", GT, axis=0, seg=16)
    abox("%s_rambr%d" % (P, sx), (0.16, 0.14, 0.08), (sx * 0.45, RAM_B.y, 1.72), "M_Body", GT, bevel=0.01)
# crew hatches + periscopes (commander right / operator left), sight block, smoke dischargers, antennas
for sx, tag in ((-1, "cmd"), (1, "opr")):
    hatch(P, tag, T(sx * 1.06, -0.70, 1.70), 0.18, GT)
    periscope(P, tag + "p", T(sx * 1.06, -0.95, 1.73), GT, 0.0)
S = T(-0.95, -0.82, 1.86)
abox(P + "_sight", (0.3, 0.32, 0.3), tuple(S), "M_Body", GT, bevel=0.02)
abox(P + "_sightw", (0.22, 0.02, 0.14), tuple(S + Vector((0, -0.165, 0.03))), "M_Lens", GT, bevel=0.0, dirt=False)
abox(P + "_sightl", (0.08, 0.02, 0.06), tuple(S + Vector((0.08, -0.165, -0.08))), "M_Lens", GT, bevel=0.0, dirt=False)
for sx in (1, -1):
    smoke_dischargers(P, "s%d" % sx, T(sx * 1.12, -0.5, 1.76), 3, 9.0, sx * 40.0, 15.0, GT)
    abox("%s_tbox%d" % (P, sx), (0.25, 0.65, 0.3), tuple(T(sx * 1.08, 0.35, 1.84)), "M_Body", GT, bevel=0.015)
antenna(P, "a1", T(0.95, 1.0, 1.70), 2.2, GT)
antenna(P, "a2", T(-0.95, 1.0, 1.70), 1.3, GT)

# ---------------------------------------------------------------- launch pack (launcher_pitch)
Y0, Y1 = TRN.y + 0.15, TRN.y - 4.75          # rear / front (muzzles)
YC, L = (Y0 + Y1) / 2, Y0 - Y1
ZT = [TRN.z + 0.22 + 0.255 * r for r in range(4)]
XT = [(c - 2.5) * 0.26 for c in range(6)]
for r, z in enumerate(ZT):
    for c, x in enumerate(XT):
        acyl("%s_tube%d_%d" % (P, r, c), 0.12, L + 0.04, (x, YC, z), "M_Body", GG, axis=1, seg=16)
        acyl("%s_bf%d_%d" % (P, r, c), 0.104, 0.01, (x, Y1 - 0.021, z), "M_Bore", GG, axis=1, seg=14, dirt=False)
        acyl("%s_br%d_%d" % (P, r, c), 0.104, 0.01, (x, Y0 + 0.021, z), "M_Bore", GG, axis=1, seg=14, dirt=False)
        acyl("%s_tbandf%d_%d" % (P, r, c), 0.128, 0.06, (x, Y1 + 0.05, z), "M_Body", GG, axis=1, seg=16)
zb, zt = ZT[0] - 0.155, ZT[-1] + 0.155
zm = (zb + zt) / 2
for sx in (1, -1):
    abox("%s_side%d" % (P, sx), (0.06, L, zt - zb + 0.04), (sx * 0.89, YC, zm), "M_Body", GG, bevel=0.012)
    for k in range(8):
        y = Y0 - 0.3 - k * 0.62
        abox("%s_rib%d_%d" % (P, sx, k), (0.04, 0.08, zt - zb), (sx * 0.935, y, zm), "M_Body", GG, bevel=0.006)
    abox("%s_sstrip%d" % (P, sx), (0.045, L * 0.9, 0.06), (sx * 0.935, YC, zt - 0.08), "M_Body", GG, bevel=0.005)
    acyl("%s_pin%d" % (P, sx), 0.08, 0.1, (sx * 0.94, TRN.y, TRN.z), "M_Steel", GG, axis=0, seg=14)
    abox("%s_pinb%d" % (P, sx), (0.06, 0.4, 0.36), (sx * 0.91, TRN.y - 0.05, TRN.z + 0.12), "M_Body", GG, bevel=0.01)
    abox("%s_rama%d" % (P, sx), (0.14, 0.16, 0.06), (sx * 0.45, RAM_A.y, zb - 0.03), "M_Body", GG, bevel=0.01)
    for k in (0, 1):
        lathe_y("%s_eye%d_%d" % (P, sx, k), [(-0.02, 0.04), (-0.02, 0.07), (0.02, 0.07), (0.02, 0.04)], (sx * 0.7, Y0 - 0.6 - k * 3.4, zt + 0.06), ["M_Steel"], GG, seg=10, axis='X')
abox(P + "_top", (1.84, L, 0.05), (0, YC, zt + 0.02), "M_Body", GG, bevel=0.01)
abox(P + "_bot", (1.78, L, 0.04), (0, YC, zb - 0.0), "M_Body", GG, bevel=0.01)
for yy, tag in ((Y1 + 0.03, "f"), (Y0 - 0.03, "r")):
    for z in (zb + 0.01, zt - 0.01):
        abox("%s_fr%s%d" % (P, tag, int(z * 100)), (1.78, 0.07, 0.05), (0, yy, z), "M_Body", GG, bevel=0.005)
    for x in (-0.5, 0.5):
        abox("%s_frv%s%d" % (P, tag, int(x * 10)), (0.04, 0.05, zt - zb), (x * 0.78, yy, zm), "M_Body", GG, bevel=0.004)
abox(P + "_rgrill", (1.7, 0.03, 0.04), (0, Y0 + 0.06, zm), "M_Dark", GG, bevel=0.0)
# rocket props (warhead nose + nozzle) per tube, hidden when that tube has fired
RK = []
for r, z in enumerate(ZT):
    for c, x in enumerate(XT):
        k = r * 6 + c
        g = "%s_rkt%d" % (P, k)
        p = Vector((x, Y1 + 0.18, z))
        lathe_y(g + "_n", [(0.10, 0.0), (0.10, 0.10), (0.0, 0.10), (-0.10, 0.06), (-0.15, 0.0)], tuple(p), ["M_Warhead"], g, seg=14, axis='Y')
        acyl(g + "_z", 0.085, 0.04, (x, Y0 - 0.08, z), "M_Dark", g, axis=1, seg=12)
        RK.append((k, g, p))

# ---------------------------------------------------------------- elevation rams (3-stage telescopic)
u = (RAM_A - RAM_B).normalized()
L0 = (RAM_A - RAM_B).length
RAMS = []
for sx, sd in ((1, "L"), (-1, "R")):
    b = RAM_B + Vector((sx * 0.45, 0, 0))
    g0, g1, g2 = "%s_ram0%s" % (P, sd), "%s_ram1%s" % (P, sd), "%s_ram2%s" % (P, sd)
    rod(g0 + "_c", b, b + u * 0.80, 0.075, "M_Body", g0, seg=12)
    acyl(g0 + "_e", 0.05, 0.14, tuple(b), "M_Steel", g0, axis=0, seg=10)
    rod(g1 + "_c", b + u * 0.02, b + u * 0.80, 0.058, "M_Hydraulic", g1, seg=12)
    rod(g2 + "_c", b + u * 0.04, b + u * (L0 - 0.02), 0.042, "M_Hydraulic", g2, seg=10)
    acyl(g2 + "_e", 0.045, 0.12, tuple(b + u * L0), "M_Steel", g2, axis=0, seg=10)
    RAMS.append((sd, b, g0, g1, g2))

# ---------------------------------------------------------------- hierarchy + export
groups = [G, GT, GG] + [g for _, g, _ in RK] + [x for r in RAMS for x in r[2:]]
auto_attach([G, GT, GG], bracket_groups=())
weather(gen_weather([(1.74, 2.55, 1.25)], 0.9))
rt = root("root")
group_under(G, P + "_hull_geo", rt)
track_rig(rt, P, "", WHEELS)
for o in LINKS:
    attach(o, rt)
ty = pivot("launcher_yaw", tuple(TC), rt)
group_under(GT, "launcher_yaw_geo", ty)
for sd, b, g0, g1, g2 in RAMS:
    e0 = pivot("ram_" + sd, tuple(b), ty)
    group_under(g0, "ram_%s_geo" % sd, e0)
    e1 = pivot("ram1_" + sd, tuple(b), e0)
    group_under(g1, "ram1_%s_geo" % sd, e1)
    e2 = pivot("ram2_" + sd, tuple(b), e1)
    group_under(g2, "ram2_%s_geo" % sd, e2)
gp = pivot("launcher_pitch", tuple(TRN), ty)
group_under(GG, "launcher_pitch_geo", gp)
br = pivot("barrel", tuple(TRN), gp)        # contract node (no recoil)
for k, g, p in RK:
    e = pivot("rkt_%d" % k, tuple(p), gp)
    group_under(g, "rkt_%d_geo" % k, e)
rw_export("/home/user/.cache/rwveh/raw/rw_tos1a.glb", "/home/user/models/rw_tos1a.blend", "RW_TOS1A")
