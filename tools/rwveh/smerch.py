# 9A52-2 "Smerch" 300 mm MLRS on MAZ-543M — Real War node contract (MlrsEntity, fireAxisZ = -1: muzzles at the REAR).
# Run: blender -b --factory-startup --python <lib.py + alib.py + arsenal_lib.py + rwveh_lib.py + this file>
# Nodes: root > wheel_0..7, steering_wheel, needle_*, jack_0/1, launcher_yaw (0,1.3,1.0 glTF) > launcher_pitch (+0,1.2,0)
#        > plug_0..11 (rocket noses in the tubes), rocket_load (tail at origin, nose +Z, 7.6 m)
reset_scene()
col_get("BUILD"); col_get("RIG")
rw_mats()
P = "SMERCH"
cfg = dict(FZ=6.32, RZ=-6.2, AXZ=[4.45, 2.85, -2.05, -3.65], WR=0.74, TX=1.22, cab_back=3.3, cab_top=3.2,
           driver_x=0.82, seat=(1.85, 4.3), steer=(2.12, 4.63), jacks=[(1.25, -5.4, 0.6), (-1.25, -5.4, 0.6)])
ch = maz543(P, cfg)
G = ch["G"]
cb = ch["cb"]
yR = 6.2
DZ = 1.58                                     # deck top

# ---------------------------------------------------------------- deck + equipment behind the cabs
abox(P + "_deck", (2.9, yR - 0.3 - cb, 0.08), (0, (cb + yR - 0.3) / 2, DZ - 0.04), "M_Plain", G, bevel=0.01)
for k in range(12):
    abox("%s_deckrib%d" % (P, k), (2.9, 0.04, 0.03), (0, cb + 0.5 + k * 0.75, DZ + 0.015), "M_Dark", G, bevel=0.0)
for sx in (1, -1):
    x = 0.98 * sx
    abox(P + "_eqbox%d" % sx, (0.92, 1.1, 0.86), (x, cb + 0.58, DZ + 0.43), "M_Body", G, bevel=0.03)
    for k in range(4):
        abox("%s_eqlv%d_%d" % (P, sx, k), (0.02, 0.6, 0.04), (x + 0.465 * sx, cb + 0.58, DZ + 0.25 + 0.1 * k), "M_Dark", G, bevel=0.0)
    abox(P + "_eqdoor%d" % sx, (0.02, 0.8, 0.6), (x + 0.465 * sx, cb + 0.58, DZ + 0.5), "M_Body", G, bevel=0.005)
    abox(P + "_eqlock%d" % sx, (0.03, 0.06, 0.1), (x + 0.48 * sx, cb + 0.95, DZ + 0.5), "M_Steel", G, bevel=0.0)
abox(P + "_cablebox", (0.7, 0.5, 0.4), (0, cb + 0.4, DZ + 0.2), "M_Dark", G, bevel=0.02)
acyl(P + "_reel", 0.28, 0.35, (0, cb + 1.0, DZ + 0.3), "M_Black", G, axis=0, seg=18)
acyl(P + "_reelax", 0.05, 0.6, (0, cb + 1.0, DZ + 0.3), "M_Steel", G, axis=0, seg=8)
# static turntable race ring
TT = gB(0, 1.3, 1.0)                          # yaw pivot (Blender)
lathe_y(P + "_race", [(0.0, 0.85), (0.0, 1.10), (0.12, 1.10), (0.12, 0.85)], (TT.x, TT.y, DZ), ["M_Dark"], G, seg=40, axis='Z')
for k in range(24):
    a = 2 * PI * k / 24
    acyl("%s_racebolt%d" % (P, k), 0.02, 0.04, (TT.x + math.cos(a) * 1.04, TT.y + math.sin(a) * 1.04, DZ + 0.14), "M_Steel", G, seg=6, dirt=False)
# rear travel lock / tube rest
for sx in (1, -1):
    rod(P + "_tlpost%d" % sx, Vector((0.62 * sx, 5.15, DZ)), Vector((0.55 * sx, 5.15, 2.52)), 0.06, "M_Plain", G, seg=10)
    rod(P + "_tlbrace%d" % sx, Vector((0.9 * sx, 4.6, DZ)), Vector((0.55 * sx, 5.15, 2.3)), 0.04, "M_Plain", G, seg=8)
abox(P + "_tlbeam", (1.4, 0.18, 0.1), (0, 5.15, 2.55), "M_Plain", G, bevel=0.02)
abox(P + "_tlpad", (1.3, 0.22, 0.03), (0, 5.15, 2.615), "M_Rubber", G, bevel=0.0)
# rear stowage: spare tyre carrier, toolbox, ladder
acyl(P + "_spare", 0.55, 0.42, (0, 3.4, DZ + 0.22), "M_Tire", G, seg=28)
acyl(P + "_sparerim", 0.35, 0.44, (0, 3.4, DZ + 0.22), "M_Plain", G, seg=20)
for sx in (1, -1):
    abox(P + "_rbox%d" % sx, (0.7, 0.9, 0.5), (1.05 * sx, 3.0, DZ + 0.25), "M_Body", G, bevel=0.03)
    rod(P + "_lad%d" % sx, Vector((0.3 * sx, yR - 0.3, 0.6)), Vector((0.3 * sx, yR - 0.35, DZ)), 0.02, "M_Steel", G, seg=6)
for k in range(3):
    rod("%s_ladr%d" % (P, k), Vector((-0.3, yR - 0.31, 0.8 + 0.27 * k)), Vector((0.3, yR - 0.31, 0.8 + 0.27 * k)), 0.015, "M_Steel", G, seg=6)
weather(gen_weather([tuple(ch["exhaust"])], 0.8))

# ---------------------------------------------------------------- turntable (launcher_yaw)
GY = P + "_yaw"
lathe_y(P + "_tt", [(0.0, 0.0), (0.0, 1.02), (0.06, 1.05), (0.16, 1.05), (0.18, 0.98), (0.18, 0.0)], (TT.x, TT.y, DZ + 0.12), ["M_Plain"], GY, seg=40, axis='Z')
PV = TT + Vector((0, 0, 1.2))                 # pitch trunnion (0, 2.5, 1.0 glTF)
for sx in (1, -1):
    x = 0.93 * sx
    prism_x(P + "_cheek%d" % sx, [(TT.y - 0.75, DZ + 0.3), (TT.y + 0.85, DZ + 0.3), (TT.y + 0.3, PV.z + 0.18), (TT.y - 0.35, PV.z + 0.18)],
            0.05, "M_Body", GY, bevel=0.015, x0=x - 0.05)
    acyl(P + "_tpin%d" % sx, 0.12, 0.2, (0.93 * sx + 0.08 * sx, PV.y, PV.z), "M_Steel", GY, axis=0, seg=16)
    arc_shell(P + "_sector%d" % sx, (0.86 * sx, PV.y, PV.z), 0.72, 0.8, 0.05, math.radians(195), math.radians(265), "M_Steel", GY, seg=10)
    for k in range(10):
        a = math.radians(197 + k * 7)
        abox("%s_tooth%d_%d" % (P, sx, k), (0.05, 0.03, 0.04), (0.86 * sx, PV.y + math.cos(a) * 0.82, PV.z + math.sin(a) * 0.82), "M_Steel", GY, rot=(a, 0, 0), bevel=0.0, dirt=False)
    abox(P + "_elmotor%d" % sx, (0.22, 0.3, 0.25), (0.86 * sx, TT.y + 0.55, DZ + 0.55), "M_Dark", GY, bevel=0.02)
abox(P + "_ttbox", (0.6, 0.5, 0.4), (0.0, TT.y + 0.6, DZ + 0.5), "M_Body", GY, bevel=0.03)
abox(P + "_sightbox", (0.3, 0.35, 0.35), (1.12, TT.y - 0.3, DZ + 0.62), "M_Body", GY, bevel=0.02)
acyl(P + "_sight", 0.06, 0.25, (1.12, TT.y - 0.45, DZ + 0.88), "M_Dark", GY, seg=10)
acyl(P + "_sightlens", 0.05, 0.02, (1.12, TT.y - 0.52, DZ + 0.95), "M_Lens", GY, axis=1, seg=10, dirt=False)
for sx in (1, -1):
    rod(P + "_hyd%d" % sx, Vector((0.5 * sx, TT.y + 0.3, DZ + 0.3)), Vector((0.86 * sx, TT.y + 0.55, DZ + 0.45)), 0.018, "M_Black", GY, seg=5)

# ---------------------------------------------------------------- launch package (launcher_pitch): 12 x 300 mm tubes
GP = P + "_pitch"
TUBE_R, TL = 0.17, 7.6
y_br, y_mz = PV.y - 0.35, PV.y + 7.25          # breech (front) / muzzle (rear)
TX4 = [-0.578, -0.193, 0.193, 0.578]
TZ3 = [0.3, 0.685, 1.07]
tubes = []
for r_, tz in enumerate(TZ3):
    for c_, tx in enumerate(TX4):
        tubes.append(Vector((tx, 0, PV.z + tz)))
yc = (y_br + y_mz) / 2
for k, t in enumerate(tubes):
    acyl("%s_tube%d" % (P, k), TUBE_R, TL, (t.x, yc, t.z), "M_Body", GP, axis=1, seg=22, caps=False)
    acyl("%s_bore%d" % (P, k), TUBE_R - 0.02, TL - 0.05, (t.x, yc + 0.02, t.z), "M_Bore", GP, axis=1, seg=22, caps=False, dirt=False)
    lathe_y("%s_mzr%d" % (P, k), [(-0.06, TUBE_R - 0.02), (-0.06, TUBE_R + 0.015), (0.0, TUBE_R + 0.015), (0.0, TUBE_R - 0.02)],
            (t.x, y_mz, t.z), ["M_Plain"], GP, seg=22, axis='Y')
    acyl("%s_brc%d" % (P, k), TUBE_R + 0.01, 0.06, (t.x, y_br + 0.03, t.z), "M_Plain", GP, axis=1, seg=22)
    acyl("%s_brp%d" % (P, k), 0.05, 0.08, (t.x, y_br - 0.03, t.z), "M_Dark", GP, axis=1, seg=10)
    acyl("%s_bdeep%d" % (P, k), TUBE_R - 0.02, 0.02, (t.x, y_br + 0.08, t.z), "M_Bore", GP, axis=1, seg=22, dirt=False)
# bulkhead frames around the bundle + side beams + top cable trays
FR_Y = [y_br + 0.25, y_br + 2.3, y_br + 4.3, y_mz - 1.3, y_mz - 0.25]
zlo, zhi = PV.z + 0.3 - 0.21, PV.z + 1.07 + 0.21
for i, fy in enumerate(FR_Y):
    for sx in (1, -1):
        abox("%s_frs%d_%d" % (P, i, sx), (0.07, 0.14, zhi - zlo + 0.07), (0.8 * sx, fy, (zlo + zhi) / 2), "M_Plain", GP, bevel=0.01)
    abox("%s_frt%d" % (P, i), (1.67, 0.14, 0.07), (0, fy, zhi), "M_Plain", GP, bevel=0.01)
    abox("%s_frb%d" % (P, i), (1.67, 0.14, 0.07), (0, fy, zlo), "M_Plain", GP, bevel=0.01)
    for r_ in range(2):
        abox("%s_frh%d_%d" % (P, i, r_), (1.6, 0.06, 0.03), (0, fy, PV.z + 0.4925 + 0.385 * r_), "M_Plain", GP, bevel=0.0)
for sx in (1, -1):
    abox(P + "_beam%d" % sx, (0.1, y_mz - y_br - 0.3, 0.16), (0.82 * sx, yc, zlo + 0.02), "M_Plain", GP, bevel=0.015)
    abox(P + "_beamt%d" % sx, (0.06, y_mz - y_br - 0.3, 0.08), (0.8 * sx, yc, zhi), "M_Plain", GP, bevel=0.01)
    prism_x(P + "_tarm%d" % sx, [(PV.y - 0.25, PV.z - 0.12), (PV.y + 0.25, PV.z - 0.12), (PV.y + 0.6, zlo + 0.1), (PV.y - 0.45, zlo + 0.1)],
            0.04, "M_Plain", GP, bevel=0.01, x0=0.84 * sx - 0.04)
    acyl(P + "_tboss%d" % sx, 0.1, 0.12, (0.86 * sx, PV.y, PV.z), "M_Plain", GP, axis=0, seg=14)
    rod(P + "_cable%d" % sx, Vector((0.4 * sx, y_br + 0.3, zhi + 0.05)), Vector((0.4 * sx, y_mz - 0.3, zhi + 0.05)), 0.02, "M_Black", GP, seg=6)
abox(P + "_stencil", (0.02, 1.4, 0.18), (0.855, yc + 1.0, (zlo + zhi) / 2), "M_Stencil", GP, bevel=0.0, dirt=False)

# loaded rockets: nose cones visible inside the open muzzles (plug_k, hidden when the tube is empty)
PLUG = []
for k, t in enumerate(tubes):
    g = "%s_plug%d" % (P, k)
    lathe_y(g + "_nose", [(-0.25, 0.0), (-0.18, 0.06), (-0.05, 0.12), (0.12, 0.145), (0.4, 0.145)], (t.x, y_mz - 0.5, t.z),
            ["M_Warhead"], g, seg=16, axis='Y')
    lathe_y(g + "_tip", [(-0.03, 0.0), (0.0, 0.02), (0.03, 0.0)], (t.x, y_mz - 0.75, t.z), ["M_Steel"], g, seg=8, axis='Y')
    PLUG.append((k, g, Vector((t.x, y_mz - 0.3, t.z))))
# reload rocket 9M55: tail at the node origin, nose towards glTF +Z (Blender -Y)
RL = P + "_rload"
O = PV.copy()
lathe_y(RL + "_body", [(0.0, 0.0), (0.0, 0.12), (-0.15, 0.15), (-5.9, 0.15), (-6.4, 0.145), (-6.9, 0.11), (-7.35, 0.05), (-7.6, 0.0)],
        tuple(O), ["M_Rocket"], RL, seg=20, axis='Y')
lathe_y(RL + "_war", [(-5.9, 0.152), (-6.4, 0.147), (-6.4, 0.0)], tuple(O), ["M_Warhead"], RL, seg=20, axis='Y')
acyl(RL + "_band", 0.155, 0.08, tuple(O + Vector((0, -2.5, 0))), "M_Stencil", RL, axis=1, seg=20, dirt=False)
lathe_y(RL + "_noz", [(0.12, 0.0), (0.12, 0.09), (0.0, 0.11), (-0.1, 0.12)], tuple(O), ["M_Nozzle"], RL, seg=16, axis='Y')
for k in range(4):
    a = PI / 4 + k * PI / 2
    d = Vector((math.cos(a), 0, math.sin(a)))
    abox("%s_fin%d" % (RL, k), (0.012, 0.42, 0.20), tuple(O + Vector((0, -0.45, 0)) + d * 0.2), "M_Steel", RL,
         rot=(0, -a + PI / 2, 0), bevel=0.0)

# ---------------------------------------------------------------- hierarchy + export
rt = root("root")
rig_maz(P, ch, rt)
yaw = pivot("launcher_yaw", tuple(TT), rt)
group_under(GY, "launcher_yaw_geo", yaw)
pit = pivot("launcher_pitch", tuple(PV), yaw)
group_under(GP, "launcher_pitch_geo", pit)
for k, g, c in PLUG:
    pn = pivot("plug_%d" % k, tuple(c), pit)
    group_under(g, "plug_%d_geo" % k, pn)
rl = pivot("rocket_load", tuple(O), pit)
group_under(RL, "rocket_load_geo", rl)
rw_export("/home/user/models/rw_smerch.glb", "/home/user/models/rw_smerch.blend", "RW_Smerch")
