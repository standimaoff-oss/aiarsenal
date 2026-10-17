# BM-13N "Katyusha" on the Lend-Lease Studebaker US6 U3 (1943) — real dimensions.
# Length 6.55 m, width 2.24 m, height 2.90 m (travel), wheelbase 4.12 m (+1.12 m bogie), 7.50-20 tyres (dual rear).
# Launcher: 8 I-beam rails 5.0 m, 16 x M-13 132 mm rockets (8 on top, 8 slung under), traverse +-10, elevation +4..+45.
# Armour shutters on the cab windows. Two screw jacks at the rear.
# Nodes (MlrsEntity contract as the BM-21): root > wheel_0..5 (0/1 steer), steering_wheel, launcher_yaw > launcher_pitch >
#   plug_0..15 (rocket on rail k//2, k%2: 0 top / 1 bottom), rocket_load (tail at origin, nose +Z glTF), jack_0/1.
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
P = "BM13"
G = P + "_body"
GT, GG = P + "_yaw", P + "_pitch"
BODY = "M_TruckGreen"
HL = 6.55 / 2
RW = 0.475
WY = [-2.10, 1.46, 2.58]          # front axle, rear bogie axles
YAWC = Vector((0, 1.95, 1.18))     # turntable centre
TRN = Vector((0, 2.95, 2.50))      # rail pivot (rear end of the rails)
RAIL_L = 5.0
RAILS_X = [-0.70 + i * 0.20 for i in range(8)]
JY = 3.05

# ---------------------------------------------------------------- wheels
def tyre(g, c, w=0.21):
    c = Vector(c)
    o = 1 if c.x > 0 else -1
    lathe_y(g + "_t", [(-w / 2, RW * 0.62), (-w / 2, RW * 0.92), (-w * 0.4, RW), (w * 0.4, RW), (w / 2, RW * 0.92), (w / 2, RW * 0.62)], tuple(c), ["M_Rubber"], g, seg=22, axis='X', uv_tile=0.5)
    for k in range(16):           # tread lugs (chevrons alternate)
        a = 2 * math.pi * k / 16
        abox("%s_lug%d" % (g, k), (w * 0.8, 0.05, 0.03), (c.x, c.y + math.cos(a) * (RW + 0.01), c.z + math.sin(a) * (RW + 0.01)), "M_Rubber", g, rot=(a + math.pi / 2, 0, 0.3 * (1 if k % 2 else -1)), bevel=0.0)
    lathe_y(g + "_rim", [(-w * 0.45, RW * 0.62), (o * w * 0.15, RW * 0.45), (o * w * 0.25, RW * 0.2), (o * w * 0.25, 0.0), (-w * 0.45, 0.0)], tuple(c), [BODY], g, seg=18, axis='X')
    acyl(g + "_hub", 0.09, 0.10, (c.x + o * w * 0.30, c.y, c.z), "M_Steel", g, axis=0, seg=10)
    for k in range(8):
        a = 2 * math.pi * k / 8
        acyl("%s_nut%d" % (g, k), 0.014, 0.03, (c.x + o * w * 0.27, c.y + math.cos(a) * 0.16, c.z + math.sin(a) * 0.16), "M_Steel", g, axis=0, seg=6, dirt=False)

WHEELS = []
for i, (y, xs) in enumerate(((WY[0], 0.795), (WY[1], 0.855), (WY[2], 0.855))):
    for sx in (1, -1):
        g = "%s_w%d_%d" % (P, i, sx)
        if i == 0:
            tyre(g, (sx * xs, y, RW))
        else:                      # duals
            tyre(g, (sx * (xs + 0.125), y, RW))
            tyre(g + "i", (sx * (xs - 0.125), y, RW))
            for o in bpy.data.objects:
                if o.get("grp") == g + "i":
                    o["grp"] = g
        WHEELS.append((g, Vector((sx * xs, y, RW))))
# axles, springs, differentials
for i, y in enumerate(WY):
    rod("%s_axle%d" % (P, i), Vector((0.80, y, RW)), Vector((-0.80, y, RW)), 0.05, "M_Dark", G, seg=10)
    lathe_y("%s_diff%d" % (P, i), [(-0.18, 0.06), (-0.12, 0.17), (0.12, 0.17), (0.18, 0.06)], (0.0, y, RW), ["M_Dark"], G, seg=14)
for sx in (1, -1):
    abox("%s_fspring%d" % (P, sx), (0.07, 1.10, 0.06), (sx * 0.43, WY[0], RW + 0.16), "M_Dark", G, bevel=0.0)
    abox("%s_rspring%d" % (P, sx), (0.08, 1.40, 0.10), (sx * 0.43, (WY[1] + WY[2]) / 2, RW + 0.17), "M_Dark", G, bevel=0.0)
    acyl("%s_trun%d" % (P, sx), 0.08, 0.20, (sx * 0.50, (WY[1] + WY[2]) / 2, RW + 0.20), "M_Dark", G, axis=0, seg=10)
rod(P + "_prop1", Vector((0, -1.0, 0.72)), Vector((0, WY[1], RW + 0.05)), 0.04, "M_Dark", G, seg=8)
rod(P + "_prop0", Vector((0, -1.6, 0.72)), Vector((0, WY[0] + 0.1, RW + 0.05)), 0.04, "M_Dark", G, seg=8)

# ---------------------------------------------------------------- frame, bumper, fenders, hood, grille
for sx in (1, -1):
    abox("%s_rail%d" % (P, sx), (0.08, 6.10, 0.22), (sx * 0.43, 0.10, 0.82), "M_Dark", G, bevel=0.0)
for y in (-2.6, -1.4, 0.2, 1.0, 2.0, 3.0):
    abox("%s_xm%d" % (P, int(y * 10)), (0.80, 0.08, 0.16), (0, y, 0.82), "M_Dark", G, bevel=0.0)
abox(P + "_bumper", (2.05, 0.14, 0.20), (0, -3.18, 0.72), BODY, G, bevel=0.02)
for sx in (1, -1):
    rod("%s_hook%d" % (P, sx), Vector((sx * 0.55, -3.25, 0.72)), Vector((sx * 0.55, -3.36, 0.80)), 0.03, "M_Steel", G, seg=6)
abox(P + "_rad", (0.78, 0.10, 0.80), (0, -2.98, 1.28), "M_Dark", G, bevel=0.0)
for k in range(9):                 # vertical grille bars
    abox("%s_gbar%d" % (P, k), (0.03, 0.03, 0.76), (-0.36 + k * 0.09, -3.04, 1.28), BODY, G, bevel=0.0)
abox(P + "_gframe", (0.86, 0.06, 0.06), (0, -3.04, 1.68), BODY, G, bevel=0.0)
hood = sym([(0.42, -3.0, 1.08), (0.42, -1.45, 1.08), (0.40, -3.0, 1.66), (0.40, -1.45, 1.74), (0.30, -3.0, 1.72), (0.30, -1.45, 1.80)])
chull(P + "_hood", hood, BODY, G, bevel=0.02, uvscale=1.0)
for sx in (1, -1):
    for k in range(4):
        abox("%s_hlouv%d_%d" % (P, sx, k), (0.01, 0.20, 0.03), (sx * 0.425, -2.0 + k * 0.10, 1.45), "M_Dark", G, bevel=0.0)
    # front fender: curved crown over the wheel + running board
    pts = []
    for k in range(9):
        a = math.radians(20 + k * 17.5)
        r = RW + 0.12
        pts.append((WY[0] + math.cos(a) * r * 1.5 * (-1), RW + math.sin(a) * r))
    for k in range(len(pts) - 1):
        (y0, z0), (y1, z1) = pts[k], pts[k + 1]
        tilted_box("%s_fend%d_%d" % (P, sx, k), (sx * 0.80, y0, z0), (sx * 0.80, y1, z1), 0.42, 0.012, BODY, G)
    abox("%s_fskirt%d" % (P, sx), (0.012, 1.0, 0.35), (sx * 0.59, -2.25, 1.02), BODY, G, bevel=0.0)
    abox("%s_step%d" % (P, sx), (0.30, 1.10, 0.03), (sx * 0.93, -0.85, 0.68), BODY, G, bevel=0.0)
    headlamp(P, "h%d" % sx, (sx * 0.72, -2.72, 1.33), G, mat=BODY)
    abox("%s_rfend%d" % (P, sx), (0.55, 2.10, 0.012), (sx * 0.86, 2.02, 1.08), BODY, G, bevel=0.0)
    abox("%s_rmud%d" % (P, sx), (0.50, 0.012, 0.40), (sx * 0.86, 3.10, 0.85), "M_Rubber", G, bevel=0.0)
# ---------------------------------------------------------------- cab (closed C-type) with armour shutters
cab = sym([(1.0, -1.45, 1.05), (1.0, -0.30, 1.05), (0.98, -1.45, 1.75), (0.98, -0.30, 2.25), (0.92, -1.25, 2.25),
           (0.90, -1.32, 2.15)])
chull(P + "_cab", cab, BODY, G, bevel=0.03, uvscale=1.0)
for sx in (1, -1):
    abox("%s_door%d" % (P, sx), (0.02, 0.80, 0.92), (sx * 1.0, -0.88, 1.55), BODY, G, bevel=0.0)
    abox("%s_dwin%d" % (P, sx), (0.012, 0.55, 0.32), (sx * 1.006, -0.85, 1.85), "M_CabGlass", G, bevel=0.0, dirt=False)
    abox("%s_dhand%d" % (P, sx), (0.03, 0.12, 0.03), (sx * 1.02, -0.58, 1.48), "M_Steel", G, bevel=0.0)
    rod("%s_mirr%d" % (P, sx), Vector((sx * 0.98, -1.30, 1.85)), Vector((sx * 1.20, -1.38, 2.00)), 0.012, "M_Steel", G, seg=6)
    abox("%s_mirg%d" % (P, sx), (0.02, 0.10, 0.16), (sx * 1.21, -1.39, 2.05), "M_CabGlass", G, bevel=0.0, dirt=False)
    # armoured shutter in front of each windscreen pane, observation slit
    abox("%s_shut%d" % (P, sx), (0.80, 0.025, 0.40), (sx * 0.45, -1.42, 1.96), BODY, G, rot=(0.20, 0, 0), bevel=0.0)
    abox("%s_shsl%d" % (P, sx), (0.40, 0.01, 0.03), (sx * 0.45, -1.44, 2.02), "M_Bore", G, rot=(0.20, 0, 0), bevel=0.0, dirt=False)
abox(P + "_cabback", (1.9, 0.02, 1.1), (0, -0.29, 1.65), BODY, G, bevel=0.0)
abox(P + "_rwin", (0.6, 0.012, 0.25), (0, -0.28, 1.95), "M_CabGlass", G, bevel=0.0, dirt=False)
abox(P + "_dash", (1.8, 0.25, 0.20), (0, -1.22, 1.55), "M_Dark", G, bevel=0.0)
acyl(P + "_swcol", 0.025, 0.45, (0.45, -1.05, 1.52), "M_Dark", G, rot=(0.6, 0, 0), seg=8)
GSW = P + "_sw"
lathe_y(P + "_swr", [(-0.015, 0.19), (-0.015, 0.21), (0.015, 0.21), (0.015, 0.19)], (0.45, -1.00, 1.70), ["M_Black"], GSW, seg=20, axis='Z')
for k in range(3):
    a = 2 * math.pi * k / 3
    rod("%s_swsp%d" % (P, k), Vector((0.45, -1.0, 1.70)), Vector((0.45 + math.cos(a) * 0.19, -1.0 + math.sin(a) * 0.19, 1.70)), 0.01, "M_Black", GSW, seg=6)
abox(P + "_seat", (1.6, 0.45, 0.12), (0, -0.55, 1.35), "M_Canvas", G, bevel=0.02)
abox(P + "_seatb", (1.6, 0.10, 0.50), (0, -0.36, 1.62), "M_Canvas", G, bevel=0.02)
# fuel tank under the right door, spare wheel behind the cab, tool box
acyl(P + "_tank", 0.22, 0.95, (-0.72, -0.10, 0.82), BODY, G, axis=1, seg=14)
abox(P + "_tbox", (0.40, 0.70, 0.35), (0.75, 0.0, 0.85), BODY, G, bevel=0.02)
acyl(P + "_spare", RW * 0.95, 0.20, (0.0, -0.10, 1.30), "M_Rubber", G, axis=1, seg=20)
# ---------------------------------------------------------------- launcher subframe, turntable, jacks
abox(P + "_subf", (1.70, 3.20, 0.14), (0, 1.55, 1.04), BODY, G, bevel=0.01)
for sx in (1, -1):
    abox("%s_subrail%d" % (P, sx), (0.10, 3.2, 0.20), (sx * 0.80, 1.55, 0.94), BODY, G, bevel=0.0)
acyl(P + "_ring", 0.62, 0.08, (YAWC.x, YAWC.y, 1.13), "M_Dark", G, seg=28)
abox(P + "_jackbar", (2.0, 0.16, 0.16), (0, JY, 0.90), BODY, G, bevel=0.0)
for sx in (1, -1):
    acyl("%s_jhous%d" % (P, sx), 0.08, 0.40, (sx * 0.95, JY, 0.80), "M_Dark", G, seg=10)
GJ = []
for j, sx in enumerate((1, -1)):
    gj = "%s_jack%d" % (P, j)
    rod(gj + "_leg", Vector((sx * 0.95, JY, 0.95)), Vector((sx * 0.95, JY, 0.48)), 0.04, "M_Steel", gj, seg=8)
    acyl(gj + "_pad", 0.14, 0.04, (sx * 0.95, JY, 0.47), BODY, gj, seg=14)
    acyl(gj + "_crank", 0.02, 0.18, (sx * 0.95 + sx * 0.10, JY, 0.98), "M_Steel", gj, axis=0, seg=6)
    GJ.append(gj)
abox(P + "_tail", (0.12, 0.05, 0.08), (0.80, 3.24, 1.0), "M_Amber", G, bevel=0.0, dirt=False)

# ---------------------------------------------------------------- launcher_yaw: turntable deck + A-frames + elevation screws
acyl(P + "_tt", 0.60, 0.10, (YAWC.x, YAWC.y, YAWC.z + 0.02), BODY, GT, seg=28)
abox(P + "_ttdeck", (1.50, 1.90, 0.08), (0, YAWC.y + 0.30, YAWC.z + 0.10), BODY, GT, bevel=0.01)
for sx in (1, -1):
    rod("%s_af%d_a" % (P, sx), Vector((sx * 0.70, YAWC.y - 0.40, YAWC.z + 0.14)), Vector((sx * 0.72, TRN.y, TRN.z - 0.08)), 0.05, BODY, GT, seg=8)
    rod("%s_af%d_b" % (P, sx), Vector((sx * 0.70, TRN.y + 0.20, YAWC.z + 0.14)), Vector((sx * 0.72, TRN.y, TRN.z - 0.08)), 0.05, BODY, GT, seg=8)
    acyl("%s_trn%d" % (P, sx), 0.08, 0.10, (sx * 0.78, TRN.y, TRN.z), "M_Steel", GT, axis=0, seg=10)
    rod("%s_elev%d" % (P, sx), Vector((sx * 0.55, YAWC.y - 0.55, YAWC.z + 0.14)), Vector((sx * 0.55, YAWC.y - 0.55, YAWC.z + 0.85)), 0.04, "M_Steel", GT, seg=8)
    acyl("%s_elevh%d" % (P, sx), 0.07, 0.30, (sx * 0.55, YAWC.y - 0.55, YAWC.z + 0.30), "M_Dark", GT, seg=10)
abox(P + "_seatg", (0.40, 0.40, 0.08), (0.55, YAWC.y + 0.90, YAWC.z + 0.55), "M_Canvas", GT, bevel=0.01)
rod(P + "_seatgp", Vector((0.55, YAWC.y + 0.90, YAWC.z + 0.14)), Vector((0.55, YAWC.y + 0.90, YAWC.z + 0.51)), 0.03, "M_Dark", GT, seg=8)
abox(P + "_handw", (0.05, 0.25, 0.25), (0.82, YAWC.y + 0.55, YAWC.z + 0.70), "M_Dark", GT, bevel=0.0)

# ---------------------------------------------------------------- launcher_pitch: 8 I-beam rails + lattice girders
yF = TRN.y - RAIL_L
for i, x in enumerate(RAILS_X):
    abox("%s_rweb%d" % (P, i), (0.012, RAIL_L, 0.17), (x, TRN.y - RAIL_L / 2, TRN.z), "M_Steel", GG, bevel=0.0)
    for s in (1, -1):
        abox("%s_rfl%d_%d" % (P, i, s), (0.07, RAIL_L, 0.012), (x, TRN.y - RAIL_L / 2, TRN.z + s * 0.085), "M_Steel", GG, bevel=0.0)
for sx in (1, -1):
    for h in (0.0, -0.24):
        abox("%s_gird%d_%d" % (P, sx, int(h * 100)), (0.06, RAIL_L, 0.06), (sx * 0.84, TRN.y - RAIL_L / 2, TRN.z + h), BODY, GG, bevel=0.0)
    for k in range(9):
        y0 = TRN.y - k * RAIL_L / 9
        y1 = TRN.y - (k + 1) * RAIL_L / 9
        rod("%s_diag%d_%d" % (P, sx, k), Vector((sx * 0.84, y0, TRN.z + (0.0 if k % 2 else -0.24))), Vector((sx * 0.84, y1, TRN.z + (-0.24 if k % 2 else 0.0))), 0.02, BODY, GG, seg=6)
for k in range(5):
    y = TRN.y - 0.05 - k * (RAIL_L - 0.1) / 4
    abox("%s_xb%d" % (P, k), (1.74, 0.06, 0.06), (0, y, TRN.z - 0.24), BODY, GG, bevel=0.0)
    abox("%s_xt%d" % (P, k), (1.74, 0.05, 0.03), (0, y, TRN.z + 0.10), BODY, GG, bevel=0.0) if k in (0, 4) else None
acyl(P + "_pivbar", 0.06, 1.70, (0, TRN.y, TRN.z), "M_Steel", GG, axis=0, seg=10)
abox(P + "_stop", (1.6, 0.05, 0.25), (0, TRN.y + 0.03, TRN.z), BODY, GG, bevel=0.0)       # rocket stop / igniter plate

# ---------------------------------------------------------------- M-13 rockets (tail at the rail rear, nose forward)
RL = 1.41
def m13(g, tail, nose_dir=Vector((0, -1, 0))):
    t = Vector(tail)
    c = t + nose_dir * (RL / 2)
    lathe_y(g + "_b", [(-RL / 2, 0.0), (-RL / 2 + 0.05, 0.035), (-RL / 2 + 0.25, 0.066), (RL / 2 - 0.30, 0.066), (RL / 2 - 0.28, 0.06), (RL / 2, 0.06), (RL / 2, 0.0)],
            tuple(c), ["M_DarkGreen"], g, seg=12)
    for k in range(4):            # tail stabiliser: 4 fins in a square shroud
        a = math.pi / 4 + k * math.pi / 2
        abox("%s_fin%d" % (g, k), (0.008, 0.22, 0.13), (t.x + math.cos(a) * 0.10, t.y - 0.13, t.z + math.sin(a) * 0.10), "M_Dark", g, rot=(0, -a, 0), bevel=0.0)
    abox(g + "_band", (0.14, 0.02, 0.14), (t.x, t.y - 0.40, t.z), "M_Brass", g, bevel=0.0)
    acyl(g + "_fz", 0.025, 0.06, (c.x, c.y - RL / 2 - 0.03, c.z), "M_Brass", g, axis=1, seg=8)
PLUGS = []
for i, x in enumerate(RAILS_X):
    for s, dz in ((0, 0.085 + 0.075), (1, -(0.085 + 0.075))):
        k = i * 2 + s
        g = "%s_plug%d" % (P, k)
        m13(g, (x, TRN.y - 0.08, TRN.z + dz))
        PLUGS.append((k, g))
GRL = P + "_rload"
m13(GRL, (TRN.x, TRN.y, TRN.z))

auto_attach([G, GT, GG], tol=0.02, bracket_groups=())
ww2_decal(P + "_star", (1.005, -0.88, 1.40), (1, 0, 0), (0, 0, 1), 0.40, "star_red", G)
ww2_decal(P + "_starR", (-1.005, -0.88, 1.40), (-1, 0, 0), (0, 0, 1), 0.40, "star_red", G)
ww2_decal(P + "_usa", (0.427, -2.30, 1.30), (1, 0, 0), (0, 0, 1), 0.55, "us_star", G)
ww2_decal(P + "_usaR", (-0.427, -2.30, 1.30), (-1, 0, 0), (0, 0, 1), 0.55, "us_star", G)
ww2_decal(P + "_kat", (0.0, -0.30 + 0.012, 1.40), (0, 1, 0), (0, 0, 1), 0.70, "ru_katyusha", G)
weather(gen_weather([(-0.3, -0.5, 0.75)], 0.5))

# ---------------------------------------------------------------- hierarchy
rt = root("root")
group_under(G, P + "_body_geo", rt)
for i, (g, c) in enumerate(WHEELS):
    pv = pivot("wheel_%d" % i, tuple(c), rt)
    group_under(g, "wheel_%d_geo" % i, pv)
sw = pivot("steering_wheel", (0.45, -1.00, 1.70), rt)
group_under(GSW, "steering_wheel_geo", sw)
ty = pivot("launcher_yaw", tuple(YAWC), rt)
group_under(GT, "launcher_yaw_geo", ty)
gp = pivot("launcher_pitch", tuple(TRN), ty)
group_under(GG, "launcher_pitch_geo", gp)
for k, g in PLUGS:
    pk = pivot("plug_%d" % k, tuple(TRN), gp)
    group_under(g, "plug_%d_geo" % k, pk)
rl = pivot("rocket_load", tuple(TRN), gp)
group_under(GRL, "rocket_load_geo", rl)
for j, gj in enumerate(GJ):
    pj = pivot("jack_%d" % j, ((1 if j == 0 else -1) * 0.95, JY, 0.0), rt)
    group_under(gj, "jack_%d_geo" % j, pj)
rw_export("/home/user/.cache/rwveh/raw/rw_bm13.glb", "/home/user/models/rw_bm13.blend", "RW_BM13")
