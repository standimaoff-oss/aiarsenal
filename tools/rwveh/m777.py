# M777A2 155 mm lightweight towed howitzer, firing position — GunVehicle node contract (no tracks). Desert tan.
# Spec: yawPivot glTF (0,.55,0) -> TC (0,0,0.55); pitch rel (0,.95,-.9) -> TRN (0,0.90,1.50); muzzle y -5.05.
# Lower carriage (root): firing platform, 2 front outriggers, split rear trails with spades, wheels raised.
# Upper carriage (launcher_yaw, +-22.5 deg): titanium saddle frame, equilibrators, fire-control, seat.
# Cradle (launcher_pitch): long cradle tube + 2 recoil cylinders + recuperator; barrel recoils.
reset_scene()
col_get("BUILD"); col_get("RIG")
armor_mats()
P = "M777"
G = P + "_base"
GT = P + "_upper"
GG = P + "_gun"
GB = P + "_barrel"
M = "M_Sand"
TC = Vector((0, 0, 0.55))
TRN = Vector((0, 0.90, 1.50))
MUZ_Y = -5.05

def leg(name, p0, p1, w, h, mat, grp):
    d = p1 - p0
    yaw = math.atan2(d.x, -d.y)
    pitch_a = math.atan2(p0.z - p1.z, d.xy.length)
    return abox(name, (w, d.length, h), tuple((p0 + p1) / 2), mat, grp, rot=(pitch_a, 0, yaw), bevel=0.012)

# ---------------------------------------------------------------- lower carriage
abox(P + "_plat", (1.5, 1.9, 0.18), (0, 0.1, 0.12), M, G, bevel=0.02)
abox(P + "_platr", (1.1, 1.4, 0.06), (0, 0.1, 0.24), "M_SandPlain", G, bevel=0.01)
lathe_y(P + "_race", [(0.0, 0.3), (0.0, 0.62), (0.08, 0.62), (0.12, 0.55), (0.3, 0.5), (0.3, 0.3)], (0, 0, 0.24), [M], G, seg=32, axis='Z')
for k in range(16):
    a = 2 * math.pi * k / 16
    acyl("%s_rb%d" % (P, k), 0.018, 0.03, (math.cos(a) * 0.57, math.sin(a) * 0.57, 0.33), "M_Steel", G, seg=6, dirt=False)
# front outriggers with pads
for sx in (1, -1):
    a = math.radians(sx * 38)
    d = Vector((math.sin(a), -math.cos(a), 0))
    p0 = Vector((sx * 0.55, -0.7, 0.25))
    p1 = p0 + d * 1.5 + Vector((0, 0, -0.12))
    leg("%s_outr%d" % (P, sx), p0, p1, 0.14, 0.14, M, G)
    acyl("%s_pad%d" % (P, sx), 0.22, 0.05, tuple(p1 + Vector((0, 0, -0.08))), "M_SandPlain", G, seg=20)
    rod("%s_padj%d" % (P, sx), p1 + Vector((0, 0, -0.06)), p1 + Vector((0, 0, 0.15)), 0.04, "M_Steel", G, seg=8)
# split trails (titanium box section) with spades, handles, lunette halves
for sx in (1, -1):
    a = math.radians(180 - sx * 24)
    d = Vector((math.sin(a), -math.cos(a), 0))
    p0 = Vector((sx * 0.45, 0.95, 0.3))
    p1 = p0 + d * 4.1 + Vector((0, 0, -0.2))
    leg("%s_trail%d" % (P, sx), p0, p1, 0.18, 0.24, M, G)
    leg("%s_trailt%d" % (P, sx), p0 + Vector((0, 0, 0.13)), p0.lerp(p1, 0.6) + Vector((0, 0, 0.1)), 0.2, 0.03, "M_SandPlain", G)
    side = Vector((-d.y, d.x, 0))
    for j in range(6):
        q = p0.lerp(p1, 0.12 + j * 0.14)
        for sg in (-1, 1):
            acyl("%s_lh%d_%d_%d" % (P, sx, j, sg), 0.05, 0.01, tuple(q + side * 0.091 * sg), "M_Bore", G, rot=(0, math.pi / 2, math.atan2(d.x, -d.y)), seg=12, dirt=False)
    sp = p1 + d * 0.15
    abox("%s_spade%d" % (P, sx), (0.62, 0.05, 0.5), tuple(sp + Vector((0, 0, -0.1))), M, G, rot=(-0.4, 0, math.atan2(d.x, -d.y)), bevel=0.01)
    for sg in (-1, 1):
        rod("%s_spst%d_%d" % (P, sx, sg), p1 + side * 0.08 * sg + Vector((0, 0, 0.08)), sp + side * 0.25 * sg + Vector((0, 0, 0.1)), 0.02, "M_Steel", G, seg=6)
        rod("%s_hdl%d_%d" % (P, sx, sg), p0.lerp(p1, 0.8) + side * 0.1 * sg + Vector((0, 0, 0.1)), p0.lerp(p1, 0.8) + side * 0.25 * sg + Vector((0, 0, 0.12)), 0.016, "M_Steel", G, seg=6)
    lathe_y("%s_lun%d" % (P, sx), [(-0.04, 0.05), (-0.04, 0.11), (0.04, 0.11), (0.04, 0.05)], tuple(p1 + Vector((0, 0, 0.15))), ["M_Steel"], G, seg=14, axis='X')
# wheels on swing arms (raised), tyres
for sx in (1, -1):
    wc = Vector((sx * 1.18, 0.35, 0.68))
    rod("%s_warm%d" % (P, sx), Vector((sx * 0.6, 0.1, 0.32)), wc, 0.07, M, G, seg=10)
    R0, WW = 0.55, 0.32
    lathe_y("%s_tyre%d" % (P, sx), [(-WW / 2, 0.33), (-WW / 2, R0 * 0.88), (-WW * 0.38, R0 * 0.98), (0.0, R0), (WW * 0.38, R0 * 0.98), (WW / 2, R0 * 0.88), (WW / 2, 0.33)],
            tuple(wc), ["M_Tire"], G, seg=32, axis='X', uv_tile=0.5)
    for k in range(22):
        a = 2 * math.pi * k / 22
        abox("%s_tl%d_%d" % (P, sx, k), (WW * 0.8, 0.07, 0.03), tuple(wc + Vector((0, math.cos(a) * R0, math.sin(a) * R0))), "M_Tire", G, rot=(a + math.pi / 2, 0, 0), bevel=0.004)
    lathe_y("%s_rim%d" % (P, sx), [(-WW * 0.45, 0.0), (-WW * 0.45, 0.33), (-WW * 0.2, 0.34), (WW * 0.1, 0.24), (WW * 0.3, 0.13), (WW * 0.3, 0.0)],
            tuple(wc), ["M_SandPlain"], G, seg=24, axis='X')
    for k in range(8):
        a = 2 * math.pi * k / 8
        acyl("%s_wnut%d_%d" % (P, sx, k), 0.02, 0.03, tuple(wc + Vector((sx * WW * 0.32, math.cos(a) * 0.16, math.sin(a) * 0.16))), "M_Steel", G, axis=0, seg=6, dirt=False)
    acyl("%s_hydr%d" % (P, sx), 0.04, 0.6, (sx * 0.85, 0.5, 0.55), "M_Steel", G, rot=(0, sx * 1.0, 0), seg=8)

# ---------------------------------------------------------------- upper carriage: titanium saddle frame
for sx in (1, -1):
    # side frame: triangle of tubes/plates from the race up to the rear trunnion
    leg("%s_sf0%d" % (P, sx), Vector((sx * 0.38, -0.6, 0.62)), Vector((sx * 0.38, 0.9, 1.45)), 0.08, 0.16, M, GT)
    leg("%s_sf1%d" % (P, sx), Vector((sx * 0.38, 0.55, 0.62)), Vector((sx * 0.38, 0.9, 1.45)), 0.08, 0.16, M, GT)
    leg("%s_sf2%d" % (P, sx), Vector((sx * 0.38, -0.6, 0.62)), Vector((sx * 0.38, 0.55, 0.62)), 0.1, 0.14, M, GT)
    abox("%s_tbrg%d" % (P, sx), (0.14, 0.3, 0.3), (sx * 0.38, TRN.y, TRN.z), M, GT, bevel=0.02)
    # equilibrator struts (front of saddle to the cradle)
    rod("%s_eq%d" % (P, sx), Vector((sx * 0.3, -0.55, 0.7)), Vector((sx * 0.3, -0.1, 1.3)), 0.07, M, GT, seg=12)
    rod("%s_eqr%d" % (P, sx), Vector((sx * 0.3, -0.1, 1.3)), Vector((sx * 0.3, 0.1, 1.48)), 0.04, "M_Steel", GT, seg=10)
abox(P + "_sbase", (0.9, 1.4, 0.14), (0, 0.0, 0.62), M, GT, bevel=0.02)
abox(P + "_xbeam", (0.84, 0.14, 0.14), (0, 0.55, 0.75), M, GT, bevel=0.01)
# digital fire control (left), gunner display, handwheels, seat
F = Vector((0.6, 0.4, 1.15))
abox(P + "_dfcs", (0.22, 0.4, 0.34), tuple(F), "M_Olive", GT, bevel=0.02)
abox(P + "_dfcsd", (0.02, 0.26, 0.18), tuple(F + Vector((0.12, 0, 0.04))), "M_Lens", GT, bevel=0.0, dirt=False)
rod(P + "_dfcsa", Vector((0.42, 0.4, 1.1)), F, 0.025, "M_Steel", GT, seg=6)
acyl(P + "_sight", 0.05, 0.3, (0.52, 0.0, 1.45), "M_Dark", GT, axis=1, seg=12)
for k, (c, ax) in enumerate((((0.5, 0.75, 1.0), 0), ((0.5, 0.95, 0.82), 0))):
    acyl("%s_hw%d" % (P, k), 0.12, 0.02, c, "M_Steel", GT, axis=ax, seg=18)
    rod("%s_hwh%d" % (P, k), Vector(c) + Vector((0.02, 0, 0.09)), Vector(c) + Vector((0.08, 0, 0.09)), 0.012, "M_Seat", GT, seg=5)
abox(P + "_seat", (0.32, 0.3, 0.05), (0.8, 0.95, 0.72), "M_Seat", GT, bevel=0.01)
rod(P + "_seatp", Vector((0.8, 0.95, 0.7)), Vector((0.5, 0.75, 0.62)), 0.025, "M_Steel", GT, seg=6)

# ---------------------------------------------------------------- cradle (pitch) and barrel
cy0, cy1 = TRN.y + 0.15, TRN.y - 2.9
acyl(P + "_cradle", 0.19, abs(cy1 - cy0), (0, (cy0 + cy1) / 2, TRN.z), M, GG, axis=1, seg=20)
acyl(P + "_trun", 0.07, 0.68, tuple(TRN), "M_Steel", GG, axis=0, seg=12)
for sx in (1, -1):
    acyl("%s_rcyl%d" % (P, sx), 0.075, 2.6, (sx * 0.24, TRN.y - 1.35, TRN.z - 0.02), M, GG, axis=1, seg=14)
    abox("%s_rcylb%d" % (P, sx), (0.12, 0.14, 0.18), (sx * 0.2, TRN.y - 2.65, TRN.z - 0.02), M, GG, bevel=0.01)
acyl(P + "_recup", 0.09, 2.3, (0, TRN.y - 1.2, TRN.z + 0.25), M, GG, axis=1, seg=14)
for y in (-0.3, -1.6, -2.6):
    abox("%s_cband%d" % (P, int(-y * 10)), (0.62, 0.1, 0.5), (0, TRN.y + y, TRN.z + 0.05), M, GG, bevel=0.01)
abox(P + "_tray", (0.32, 0.6, 0.05), (0, TRN.y + 0.7, TRN.z - 0.2), "M_Steel", GG, bevel=0.005)
abox(P + "_breech", (0.38, 0.5, 0.38), (0, TRN.y + 0.4, TRN.z), M, GB, bevel=0.02)
abox(P + "_breechh", (0.06, 0.25, 0.07), (0.21, TRN.y + 0.45, TRN.z), "M_Steel", GB, bevel=0.005)
gun_tube(P, GB, TRN.y + 0.15, MUZ_Y, TRN.z, 0.095, 0.14, mat=M, sleeve=False, brake=("m777", 0.62))
acyl(P + "_mvr", 0.04, 0.12, (0, TRN.y - 3.0, TRN.z + 0.18), "M_Dark", GB, axis=1, seg=10)

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
rw_export("/home/user/.cache/rwveh/raw/rw_m777.glb", "/home/user/models/rw_m777.blend", "RW_M777")
