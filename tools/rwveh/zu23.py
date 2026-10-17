# ZU-23-2 twin 23 mm AA gun on its towed carriage — Real War node contract (FlakEntity).
# Nodes: root > wheel_0/1, turret_yaw (0,0.52,0) > gun_pitch (0,0.95,0) > barrel_0/1 (x +-0.23, recoil along +Z), ammo_box_0/1
reset_scene()
col_get("BUILD"); col_get("RIG")
rw_mats()
P = "ZU23"
G = P + "_base"
YAW = gB(0, 0.52, 0)
PV = gB(0, 0.95, 0)
WR = 0.36

# ---------------------------------------------------------------- carriage: triangular platform, 3 levelling jacks, axle, tow bar
tri = [(0.0, -1.05), (0.95, 0.55), (-0.95, 0.55)]
for i in range(3):
    a, b = tri[i], tri[(i + 1) % 3]
    rod("%s_frame%d" % (P, i), Vector((a[0], a[1], 0.36)), Vector((b[0], b[1], 0.36)), 0.07, "M_Body", G, seg=10)
    abox("%s_jbox%d" % (P, i), (0.16, 0.16, 0.3), (a[0], a[1], 0.33), "M_Body", G, bevel=0.02)
    acyl("%s_jscrew%d" % (P, i), 0.035, 0.35, (a[0], a[1], 0.18), "M_Steel", G, seg=10)
    acyl("%s_jpad%d" % (P, i), 0.13, 0.04, (a[0], a[1], 0.02), "M_Steel", G, seg=14)
    acyl("%s_jcrank%d" % (P, i), 0.05, 0.03, (a[0], a[1], 0.5), "M_Dark", G, seg=10)
abox(P + "_plat", (1.0, 1.0, 0.08), (0, -0.05, 0.40), "M_Body", G, bevel=0.02)
for sx in (1, -1):
    rod(P + "_spoke%d" % sx, Vector((0, -0.05, 0.4)), Vector((0.95 * sx, 0.55, 0.36)), 0.05, "M_Body", G, seg=8)
rod(P + "_spokef", Vector((0, -0.05, 0.4)), Vector((0, -1.05, 0.36)), 0.05, "M_Body", G, seg=8)
lathe_y(P + "_race", [(0.0, 0.36), (0.0, 0.47), (0.08, 0.47), (0.08, 0.36)], (0, 0, 0.44), ["M_Dark"], G, seg=32, axis='Z')
# axle + suspension arms + mudguards
for sx in (1, -1):
    rod(P + "_axle%d" % sx, Vector((0.3 * sx, 0.05, 0.36)), Vector((0.78 * sx, 0.05, WR)), 0.045, "M_Dark", G, seg=8)
    arc_shell(P + "_fend%d" % sx, (0.92 * sx, 0.05, WR), WR + 0.06, WR + 0.09, 0.26, math.radians(20), math.radians(160), "M_Body", G, seg=10)
    rod(P + "_fendst%d" % sx, Vector((0.78 * sx, 0.05, 0.45)), Vector((0.92 * sx, 0.05, WR + 0.4)), 0.015, "M_Dark", G, seg=6)
# tow bar at the rear (glTF -Z = Blender +Y) with lunette
rod(P + "_tow", Vector((0, 0.55, 0.36)), Vector((0, 1.65, 0.42)), 0.045, "M_Body", G, seg=8)
lathe_y(P + "_lunette", [(-0.03, 0.06), (-0.03, 0.1), (0.03, 0.1), (0.03, 0.06)], (0, 1.75, 0.42), ["M_Steel"], G, seg=16, axis='Z')
rod(P + "_towleg", Vector((0, 1.4, 0.4)), Vector((0, 1.4, 0.04)), 0.025, "M_Steel", G, seg=6)
# gunner seat (static, at the spec seat position: rider hips ~ seat - 0.1) on a post
S = gB(0, 0.98, -0.85)
abox(P + "_seat", (0.4, 0.36, 0.08), (S.x, S.y, S.z - 0.16), "M_Seat", G, bevel=0.02)
abox(P + "_seatb", (0.4, 0.06, 0.35), (S.x, S.y + 0.2, S.z + 0.04), "M_Seat", G, rot=(-0.2, 0, 0), bevel=0.02)
rod(P + "_seatpost", Vector((S.x, S.y, S.z - 0.2)), Vector((S.x, 0.2, 0.42)), 0.04, "M_Body", G, seg=8)
for sx in (1, -1):
    tilted_box(P + "_foot%d" % sx, (0, -0.05, 0.46), (0, 0.15, 0.5), 0.18, 0.02, "M_Steel", G)
    bpy.data.objects[P + "_foot%d" % sx].location.x = 0.22 * sx
weather(gen_weather([], 0.5))

# wheels (towing): GAZ-type tyre on a disc rim
WH = []
for i, sx in enumerate((1, -1)):
    g = "%s_w%d" % (P, i)
    c = Vector((0.92 * sx, 0.05, WR))
    WW = 0.22
    prof = [(-WW / 2, 0.2), (-WW / 2, WR * 0.85), (-WW * 0.35, WR * 0.98), (WW * 0.35, WR * 0.98), (WW / 2, WR * 0.85), (WW / 2, 0.2)]
    lathe_y(g + "_tyre", prof, tuple(c), ["M_Tire"], g, seg=28, axis='X', uv_tile=0.5)
    for k in range(20):
        a = 2 * PI * k / 20
        abox("%s_lug%d" % (g, k), (WW * 0.8, 0.05, 0.03), (c.x, c.y + math.cos(a) * (WR + 0.005), c.z + math.sin(a) * (WR + 0.005)), "M_Tire", g, rot=(a + PI / 2, 0, 0), bevel=0.005, dirt=False)
    lathe_y(g + "_rim", [(-0.06, 0.0), (-0.06, 0.12), (0.0, 0.2), (0.04, 0.21), (0.04, 0.0)], (c.x + 0.03 * sx, c.y, c.z), ["M_Body"], g, seg=24, axis='X')
    acyl(g + "_hub", 0.06, 0.08, (c.x + 0.09 * sx, c.y, c.z), "M_Steel", g, axis=0, seg=12)
    for k in range(6):
        a = 2 * PI * k / 6
        acyl("%s_nut%d" % (g, k), 0.012, 0.03, (c.x + 0.07 * sx, c.y + math.cos(a) * 0.1, c.z + math.sin(a) * 0.1), "M_Steel", g, axis=0, seg=6, dirt=False)
    WH.append((i, g, c))

# ---------------------------------------------------------------- upper carriage (turret_yaw)
GY = P + "_upper"
lathe_y(P + "_tt", [(0.0, 0.0), (0.0, 0.45), (0.06, 0.45), (0.06, 0.0)], (0, 0, 0.52), ["M_Body"], GY, seg=32, axis='Z')
for sx in (1, -1):
    prism_x(P + "_cheek%d" % sx, [(-0.35, 0.58), (0.35, 0.58), (0.18, PV.z + 0.08), (-0.12, PV.z + 0.08)], 0.02, "M_Body", GY, bevel=0.006, x0=0.38 * sx - 0.02)
    acyl(P + "_trun%d" % sx, 0.06, 0.08, (0.40 * sx, 0, PV.z), "M_Steel", GY, axis=0, seg=12)
    # hand wheels: elevation (left) / traverse (right)
    hw = Vector((0.48 * sx, 0.5, 0.7))
    lathe_y(P + "_hw%d" % sx, [(0.01 * math.cos(2 * PI * k / 8), 0.11 + 0.01 * math.sin(2 * PI * k / 8)) for k in range(9)], tuple(hw), ["M_Dark"], GY, seg=20, axis='X')
    rod(P + "_hwax%d" % sx, hw, hw - Vector((0.09 * sx, 0, 0)), 0.012, "M_Steel", GY, seg=6)
    rod(P + "_hwh%d" % sx, hw + Vector((0, 0.08, 0.06)), hw + Vector((0.07 * sx, 0.08, 0.06)), 0.012, "M_Black", GY, seg=6)
    abox(P + "_gearbox%d" % sx, (0.1, 0.18, 0.18), (0.36 * sx, 0.42, 0.66), "M_Dark", GY, bevel=0.01)
# balancing spring cylinders
for sx in (1, -1):
    rod(P + "_bal%d" % sx, Vector((0.3 * sx, -0.25, 0.6)), Vector((0.3 * sx, -0.05, PV.z - 0.12)), 0.04, "M_Body", GY, seg=10)
# collimator sight post in front of the gunner
rod(P + "_sightpost", Vector((0, 0.38, 0.6)), Vector((0, 0.5, 1.1)), 0.025, "M_Body", GY, seg=8)
abox(P + "_sight", (0.12, 0.2, 0.14), (0, 0.48, 1.18), "M_Dark", GY, bevel=0.015)
acyl(P + "_sightlens", 0.04, 0.02, (0, 0.37, 1.2), "M_Lens", GY, axis=1, seg=10, dirt=False)
abox(P + "_sightglass", (0.1, 0.01, 0.1), (0, 0.38, 1.3), "M_CabGlass", GY, bevel=0.0, dirt=False)

# ---------------------------------------------------------------- cradle + receivers (gun_pitch), barrels, magazines
GP = P + "_cradle"
abox(P + "_cradle", (0.72, 0.9, 0.12), (0, -0.15, PV.z - 0.06), "M_Body", GP, bevel=0.015)
acyl(P + "_trunax", 0.04, 0.84, tuple(PV), "M_Steel", GP, axis=0, seg=10)
BAR = []
for i, sx in enumerate((1, -1)):
    x = 0.23 * sx
    # 2A14 receiver: box, feed cover, cocking handle, gas cylinder
    abox("%s_rcv%d" % (P, i), (0.17, 1.0, 0.2), (x, 0.0, 0.98), "M_Dark", GP, bevel=0.015)
    abox("%s_feed%d" % (P, i), (0.18, 0.4, 0.05), (x, -0.08, 1.1), "M_Body", GP, bevel=0.01)
    rod("%s_cock%d" % (P, i), Vector((x + 0.09 * sx, 0.25, 0.98)), Vector((x + 0.15 * sx, 0.25, 0.98)), 0.012, "M_Steel", GP, seg=6)
    acyl("%s_buffer%d" % (P, i), 0.035, 0.35, (x, 0.65, 0.98), "M_Dark", GP, axis=1, seg=10)
    # barrel (moves on recoil): tube, jacket, gas block, carrying handle, slotted flash hider
    g = "%s_bar%d" % (P, i)
    b0, b1 = -0.5, -2.55
    acyl(g + "_tube", 0.032, abs(b1 - b0), (x, (b0 + b1) / 2, 0.96), "M_Dark", g, axis=1, seg=12)
    acyl(g + "_jacket", 0.05, 0.45, (x, b0 - 0.22, 0.96), "M_Dark", g, axis=1, seg=14)
    acyl(g + "_gas", 0.025, 0.7, (x, b0 - 0.5, 1.02), "M_Dark", g, axis=1, seg=8)
    abox(g + "_gblock", (0.08, 0.06, 0.1), (x, b0 - 0.85, 0.99), "M_Steel", g, bevel=0.005)
    rod(g + "_handle", Vector((x, b0 - 0.35, 1.0)), Vector((x, b0 - 0.75, 1.0)), 0.012, "M_Black", g, seg=6)
    rod(g + "_handle2", Vector((x, b0 - 0.35, 1.0)), Vector((x, b0 - 0.35, 1.08)), 0.012, "M_Black", g, seg=6)
    rod(g + "_handle3", Vector((x, b0 - 0.75, 1.0)), Vector((x, b0 - 0.75, 1.08)), 0.012, "M_Black", g, seg=6)
    rod(g + "_handle4", Vector((x, b0 - 0.35, 1.08)), Vector((x, b0 - 0.75, 1.08)), 0.015, "M_Black", g, seg=6)
    lathe_y(g + "_fh", [(0.0, 0.0), (0.0, 0.05), (-0.04, 0.055), (-0.22, 0.06), (-0.24, 0.045), (-0.24, 0.0)], (x, b1 + 0.24, 0.96), ["M_Dark"], g, seg=12, axis='Y')
    for k in range(6):
        a = 2 * PI * k / 6
        abox("%s_slot%d" % (g, k), (0.012, 0.13, 0.012), (x + math.cos(a) * 0.058, b1 + 0.12, 0.96 + math.sin(a) * 0.058), "M_Bore", g, bevel=0.0, dirt=False)
    acyl(g + "_bore", 0.013, 0.01, (x, b1 - 0.001, 0.96), "M_Bore", g, axis=1, seg=8, dirt=False)
    BAR.append((i, g, gB(x, 0.96, 0.5)))
AMMO = []
for i, sx in enumerate((1, -1)):
    g = "%s_ammo%d" % (P, i)
    c = gB(0.52 * sx, 0.85, -0.12)
    abox(g + "_box", (0.2, 0.48, 0.32), tuple(c), "M_Body", g, bevel=0.02)
    abox(g + "_lid", (0.21, 0.49, 0.03), tuple(c + Vector((0, 0, 0.17))), "M_Plain", g, bevel=0.005)
    for k in (-1, 1):
        abox("%s_latch%d" % (g, k), (0.03, 0.04, 0.06), tuple(c + Vector((0.1 * sx, 0.15 * k, 0.1))), "M_Steel", g, bevel=0.0)
    abox(g + "_stencil", (0.01, 0.25, 0.08), tuple(c + Vector((0.101 * sx, 0, -0.02))), "M_Stencil", g, bevel=0.0, dirt=False)
    abox(g + "_belt", (0.12, 0.1, 0.06), tuple(c + Vector((-0.15 * sx, -0.05, 0.12))), "M_Copper", g, bevel=0.0)
    AMMO.append((i, g, c))

# ---------------------------------------------------------------- hierarchy + export
rt = root("root")
group_under(G, P + "_base_geo", rt)
for i, g, c in WH:
    w = pivot("wheel_%d" % i, tuple(c), rt)
    group_under(g, "wheel_%d_geo" % i, w)
ty = pivot("turret_yaw", tuple(YAW), rt)
group_under(GY, "turret_yaw_geo", ty)
gp = pivot("gun_pitch", tuple(PV), ty)
group_under(GP, "gun_pitch_geo", gp)
for i, g, c in BAR:
    n = pivot("barrel_%d" % i, tuple(c), gp)
    group_under(g, "barrel_%d_geo" % i, n)
for i, g, c in AMMO:
    n = pivot("ammo_box_%d" % i, tuple(c), gp)
    group_under(g, "ammo_box_%d_geo" % i, n)
rw_export("/home/user/models/rw_zu23.glb", "/home/user/models/rw_zu23.blend", "RW_ZU23")
