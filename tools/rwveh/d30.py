# D-30 (2A18) 122 mm towed howitzer, firing position — GunVehicle node contract (no tracks).
# Spec: yawPivot glTF (0,.62,0) -> TC (0,0,0.62); pitch rel (0,.46,.15) -> TRN (0,-0.15,1.08); muzzle y -4.50.
# Lower carriage (root): central firing jack/base plate, 3 trails at 180 / +-60 deg with spades, wheels raised.
# Upper carriage (launcher_yaw): saddle, shield, wheel axle arms; cradle with recoil system ABOVE the barrel.
reset_scene()
col_get("BUILD"); col_get("RIG")
armor_mats()
P = "D30"
G = P + "_base"
GT = P + "_upper"
GG = P + "_gun"
GB = P + "_barrel"
M = "M_Body"
TC = Vector((0, 0, 0.62))
TRN = Vector((0, -0.15, 1.08))
MUZ_Y = -4.50

# ---------------------------------------------------------------- lower carriage
lathe_y(P + "_plate", [(0.0, 0.0), (0.0, 0.58), (0.05, 0.6), (0.08, 0.5), (0.08, 0.0)], (0, 0, 0.0), [M], G, seg=24, axis='Z')
lathe_y(P + "_jack", [(0.0, 0.32), (0.45, 0.30), (0.55, 0.38), (0.62, 0.42), (0.62, 0.0)], (0, 0, 0.0), [M], G, seg=24, axis='Z')
for k, deg in enumerate((180.0, 60.0, -60.0)):
    a = math.radians(deg)
    d = Vector((math.sin(a), -math.cos(a), 0.0))          # deg 0 = forward (-Y)
    side = Vector((-d.y, d.x, 0.0))
    p0 = Vector((0, 0, 0.42)) + d * 0.35
    p1 = Vector((0, 0, 0.12)) + d * 3.0
    # box-section trail tapering towards the end
    L = (p1 - p0).length
    mid = (p0 + p1) / 2
    yaw = math.atan2(d.x, -d.y)
    pitch_a = math.atan2(p0.z - p1.z, L)
    abox("%s_trail%d" % (P, k), (0.2, L, 0.2), tuple(mid), M, G, rot=(pitch_a, 0, yaw), bevel=0.015)
    abox("%s_trailt%d" % (P, k), (0.24, L * 0.5, 0.04), tuple(p0.lerp(p1, 0.3) + Vector((0, 0, 0.12))), M, G, rot=(pitch_a, 0, yaw), bevel=0.006)
    # spade + towing eye on the end
    sp = p1 + d * 0.12
    abox("%s_spade%d" % (P, k), (0.5, 0.05, 0.38), tuple(sp + Vector((0, 0, -0.05))), M, G, rot=(-0.35, 0, yaw), bevel=0.01)
    for j in (-1, 1):
        rod("%s_spstay%d_%d" % (P, k, j), p1 + side * 0.08 * j + Vector((0, 0, 0.05)), sp + side * 0.2 * j + Vector((0, 0, 0.1)), 0.018, "M_Steel", G, seg=6)
    acyl("%s_peg%d" % (P, k), 0.03, 0.4, tuple(sp + d * 0.15 + Vector((0, 0, 0.0))), "M_Steel", G, seg=8)
    if k == 0:
        lathe_y(P + "_lunette", [(-0.04, 0.05), (-0.04, 0.12), (0.04, 0.12), (0.04, 0.05)], tuple(p1 + d * 0.35 + Vector((0, 0, 0.12))), ["M_Steel"], G, seg=14, axis='X')
        abox(P + "_lunb", (0.12, 0.3, 0.1), tuple(p1 + d * 0.2 + Vector((0, 0, 0.12))), M, G, rot=(0, 0, yaw), bevel=0.01)
    # handspikes stowed on the trail
    rod("%s_hspk%d" % (P, k), p0.lerp(p1, 0.45) + side * 0.14 + Vector((0, 0, 0.1)), p0.lerp(p1, 0.85) + side * 0.14 + Vector((0, 0, 0.0)), 0.015, "M_Seat", G, seg=6)
    # hinge at the jack
    acyl("%s_hinge%d" % (P, k), 0.07, 0.28, tuple(Vector((0, 0, 0.42)) + d * 0.38), "M_Steel", G, rot=(0, math.pi / 2, yaw), seg=12)

    # lightening holes + tie-down rings along the trail
    for j in range(4):
        q = p0.lerp(p1, 0.2 + j * 0.2)
        for sgn in (-1, 1):
            acyl("%s_lh%d_%d_%d" % (P, k, j, sgn), 0.045, 0.01, tuple(q + side * 0.101 * sgn), "M_Bore", G, rot=(0, math.pi / 2, yaw), seg=12, dirt=False)
    for sgn in (-1, 1):
        lathe_y("%s_tie%d_%d" % (P, k, sgn), [(-0.012, 0.03), (-0.012, 0.05), (0.012, 0.05), (0.012, 0.03)], tuple(p0.lerp(p1, 0.7) + side * 0.12 * sgn + Vector((0, 0, 0.12))), ["M_Steel"], G, seg=10, axis='X')
# jack screw handles + base-plate bolts
for k in range(4):
    a = math.radians(45 + k * 90)
    rod("%s_jh%d" % (P, k), Vector((math.cos(a) * 0.45, math.sin(a) * 0.45, 0.33)), Vector((math.cos(a) * 0.7, math.sin(a) * 0.7, 0.36)), 0.018, "M_Steel", G, seg=6)
for k in range(12):
    a = 2 * math.pi * k / 12
    acyl("%s_pb%d" % (P, k), 0.02, 0.03, (math.cos(a) * 0.52, math.sin(a) * 0.52, 0.09), "M_Steel", G, seg=6, dirt=False)
# two opened ammo crates with 122 mm rounds beside the gun (right rear)
for c_i, (cx, cy, cyaw) in enumerate(((-1.55, 1.0, 0.3), (-2.35, 1.25, -0.15))):
    R3 = Matrix.Rotation(cyaw, 3, 'Z')
    cc = Vector((cx, cy, 0.14))
    abox("%s_crate%d" % (P, c_i), (0.5, 1.3, 0.26), tuple(cc), "M_Olive", G, rot=(0, 0, cyaw), bevel=0.012)
    abox("%s_cratei%d" % (P, c_i), (0.44, 1.24, 0.02), tuple(cc + Vector((0, 0, 0.12))), "M_Dark", G, rot=(0, 0, cyaw), bevel=0.0, dirt=False)
    abox("%s_lid%d" % (P, c_i), (0.5, 1.3, 0.04), tuple(cc + R3 @ Vector((0.42, 0, 0.0))), "M_Olive", G, rot=(0, 0.25, cyaw), bevel=0.008)
    for h in (-0.4, 0.4):
        abox("%s_crh%d_%d" % (P, c_i, int(h * 10)), (0.52, 0.06, 0.04), tuple(cc + R3 @ Vector((0, h, 0.06))), "M_Steel", G, rot=(0, 0, cyaw), bevel=0.0)
    for r_i in range(2):
        rc = cc + R3 @ Vector((-0.1 + r_i * 0.2, 0.0, 0.16))
        d = R3 @ Vector((0, 1, 0))
        rod("%s_case%d_%d" % (P, c_i, r_i), rc + d * 0.55, rc + d * 0.05, 0.062, "M_Brass", G, seg=14)
        rod("%s_proj%d_%d" % (P, c_i, r_i), rc + d * 0.05, rc - d * 0.38, 0.061, "M_Olive", G, seg=14, r2=0.02)
        rod("%s_band%d_%d" % (P, c_i, r_i), rc + d * 0.03, rc - d * 0.0, 0.064, "M_Brass", G, seg=14)

# ---------------------------------------------------------------- upper carriage: saddle, wheels on swing arms (raised), shield
abox(P + "_saddle", (0.7, 0.9, 0.22), (0, -0.05, 0.75), M, GT, bevel=0.02)
for sx in (1, -1):
    abox("%s_cheek%d" % (P, sx), (0.06, 0.55, 0.42), (sx * 0.28, -0.15, 0.98), M, GT, bevel=0.01)
    # wheel swing arm + raised wheel (road wheel off the ground in firing position)
    wc = Vector((sx * 0.98, 0.25, 0.62))
    rod("%s_warm%d" % (P, sx), Vector((sx * 0.32, -0.05, 0.72)), wc, 0.05, M, GT, seg=10)
    R0, WW = 0.47, 0.26
    lathe_y("%s_tyre%d" % (P, sx), [(-WW / 2, 0.30), (-WW / 2, R0 * 0.88), (-WW * 0.38, R0 * 0.98), (0.0, R0), (WW * 0.38, R0 * 0.98), (WW / 2, R0 * 0.88), (WW / 2, 0.30)],
            tuple(wc), ["M_Tire"], GT, seg=32, axis='X', uv_tile=0.5)
    for k in range(20):
        a = 2 * math.pi * k / 20
        abox("%s_tl%d_%d" % (P, sx, k), (WW * 0.8, 0.06, 0.025), tuple(wc + Vector((0, math.cos(a) * R0, math.sin(a) * R0))), "M_Tire", GT, rot=(a + math.pi / 2, 0, 0), bevel=0.004)
    lathe_y("%s_rim%d" % (P, sx), [(-WW * 0.45, 0.0), (-WW * 0.45, 0.30), (-WW * 0.2, 0.31), (WW * 0.1, 0.22), (WW * 0.3, 0.12), (WW * 0.3, 0.0)],
            tuple(wc), [M], GT, seg=24, axis='X')
    for k in range(6):
        a = 2 * math.pi * k / 6
        acyl("%s_wnut%d_%d" % (P, sx, k), 0.018, 0.03, tuple(wc + Vector((sx * WW * 0.32, math.cos(a) * 0.14, math.sin(a) * 0.14))), "M_Steel", GT, axis=0, seg=6, dirt=False)
    acyl("%s_hub%d" % (P, sx), 0.1, 0.1, tuple(wc + Vector((sx * 0.14, 0, 0))), "M_Steel", GT, axis=0, seg=14)
    abox("%s_mud%d" % (P, sx), (0.3, 0.9, 0.04), tuple(wc + Vector((0, 0, 0.52))), M, GT, rot=(0, -sx * 0.25, 0), bevel=0.008)
# shield: bent armour plate in front of the cradle, two wings
shield = [(-0.62, -0.45), (0.62, -0.45)]
abox(P + "_shield", (1.24, 0.03, 0.82), (0, -0.62, 1.12), M, GT, rot=(0.12, 0, 0), bevel=0.006)
for sx in (1, -1):
    abox("%s_shieldw%d" % (P, sx), (0.6, 0.03, 0.72), (sx * 0.86, -0.52, 1.08), M, GT, rot=(0.12, 0, sx * 0.4), bevel=0.006)
    abox("%s_shieldl%d" % (P, sx), (0.4, 0.03, 0.22), (sx * 0.55, -0.6, 0.68), M, GT, rot=(-0.3, 0, 0), bevel=0.006)
    rod("%s_shst%d" % (P, sx), Vector((sx * 0.5, -0.6, 0.85)), Vector((sx * 0.3, -0.1, 0.8)), 0.018, "M_Steel", GT, seg=6)
for k in range(7):
    for zz in (0.74, 1.5):
        acyl("%s_riv%d_%d" % (P, k, int(zz * 10)), 0.014, 0.02, (-0.54 + k * 0.18, -0.655 + (zz - 1.12) * 0.12, zz), "M_Steel", GT, axis=1, seg=6, dirt=False)
abox(P + "_shslot", (0.36, 0.035, 0.48), (0, -0.635, 1.12), "M_Bore", GT, rot=(0.12, 0, 0), bevel=0.0, dirt=False)
# sights (left), handwheels, gunner seat
abox(P + "_sightb", (0.12, 0.22, 0.26), (0.42, -0.25, 1.2), M, GT, bevel=0.01)
acyl(P + "_pg1m", 0.05, 0.22, (0.45, -0.22, 1.42), "M_Dark", GT, axis=1, seg=12)
acyl(P + "_pg1me", 0.035, 0.08, (0.45, -0.08, 1.42), "M_Dark", GT, axis=1, seg=10)
acyl(P + "_op4", 0.045, 0.35, (0.48, -0.42, 1.25), "M_Dark", GT, axis=1, seg=12)
for k, (c, ax) in enumerate((((0.5, 0.05, 0.98), 0), ((0.5, -0.2, 0.82), 0))):
    acyl("%s_hw%d" % (P, k), 0.11, 0.02, c, "M_Steel", GT, axis=ax, seg=18)
    rod("%s_hwh%d" % (P, k), Vector(c) + Vector((0.02, 0, 0.08)), Vector(c) + Vector((0.08, 0, 0.08)), 0.012, "M_Seat", GT, seg=5)
abox(P + "_seat", (0.3, 0.26, 0.05), (0.62, 0.45, 0.62), "M_Seat", GT, bevel=0.01)

# ---------------------------------------------------------------- cradle (pitch): recoil system above the barrel
abox(P + "_cradle", (0.3, 1.4, 0.3), (0, TRN.y - 0.1, TRN.z), M, GG, bevel=0.02)
acyl(P + "_trun", 0.07, 0.62, tuple(TRN), "M_Steel", GG, axis=0, seg=12)
acyl(P + "_recA", 0.085, 2.0, (0.08, TRN.y - 0.55, TRN.z + 0.25), M, GG, axis=1, seg=14)
acyl(P + "_recB", 0.085, 2.0, (-0.08, TRN.y - 0.55, TRN.z + 0.25), M, GG, axis=1, seg=14)
abox(P + "_recband", (0.38, 0.1, 0.32), (0, TRN.y - 1.45, TRN.z + 0.15), M, GG, bevel=0.01)
for sx in (1, -1):
    acyl("%s_equil%d" % (P, sx), 0.06, 0.7, (sx * 0.22, TRN.y + 0.35, TRN.z - 0.2), M, GG, rot=(0.6, 0, 0), seg=12)
# barrel: 38 calibre tube, heavy breech block behind, multi-baffle muzzle brake
abox(P + "_breech", (0.32, 0.5, 0.32), (0, TRN.y + 0.45, TRN.z), M, GB, bevel=0.02)
abox(P + "_breechh", (0.06, 0.2, 0.06), (0.18, TRN.y + 0.55, TRN.z), "M_Steel", GB, bevel=0.005)
gun_tube(P, GB, TRN.y + 0.2, MUZ_Y, TRN.z, 0.07, 0.11, mat=M, sleeve=False, brake=("d30", 0.5))
acyl(P + "_recrod", 0.05, 0.3, (0, TRN.y - 1.6, TRN.z + 0.18), "M_Steel", GB, axis=1, seg=10)

# r5: every part must touch its node body (smoke dischargers, skirts, brackets, handwheels ...)
auto_attach([G, GT, GG, GB], bracket_groups=(GB,))
weather(gen_weather([(0, MUZ_Y, TRN.z)], 0.4))
rt = root("root")
group_under(G, P + "_base_geo", rt)
ty = pivot("launcher_yaw", tuple(TC), rt)
group_under(GT, "launcher_yaw_geo", ty)
gp = pivot("launcher_pitch", tuple(TRN), ty)
group_under(GG, "launcher_pitch_geo", gp)
br = pivot("barrel", tuple(TRN), gp)
group_under(GB, "barrel_geo", br)
rw_export("/home/user/.cache/rwveh/raw/rw_d30.glb", "/home/user/models/rw_d30.blend", "RW_D30")
