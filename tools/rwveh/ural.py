# Ural-4320-31 6x6 army truck with troop tent (reply 12) — VehicleEntity wheeled node contract:
# wheel_i (spin X, steer Y; i = Specs.URAL wheel order L/R front, mid, rear), steering_wheel, needle_speed/rpm/fuel/temp.
# Blender: front = -Y, +X = vehicle left. glTF = (x, z, -y). Axles glTF z 2.42 / -1.105 / -2.505 -> Blender y -2.42 / 1.105 / 2.505.
# Real data: L 7.37 m, W 2.5 m, H 2.87 m (tent), wheelbase 3525+1400 mm, tyres 14.00-20 (1.2 m), YaMZ-238 V8 240 hp.
reset_scene()
col_get("BUILD"); col_get("RIG")
amat("M_Body", "T_RuGreen3", 0.7, 0.05, factor=(0.9, 0.95, 0.85, 1))
amat("M_Canvas", None, 0.95, 0.0, factor=(0.30, 0.31, 0.20, 1))
amat("M_CanvasIn", None, 0.95, 0.0, factor=(0.18, 0.19, 0.12, 1))
amat("M_Plank", None, 0.85, 0.0, factor=(0.26, 0.30, 0.17, 1))
rw_mats()
P = "URAL"
G = P + "_body"
M = "M_Body"
WR, WW, WX = 0.60, 0.40, 1.0
AX = [-2.42, 1.105, 2.505]
CAB0, CAB1 = -1.62, -0.12            # cab front / back
ZF, ZR = 1.05, 2.62                  # cab floor / roof
BODY0, BODY1, ZB = 0.0, 3.68, 1.45   # cargo body front / rear / floor top

# ---------------------------------------------------------------- chassis
for sx in (1, -1):
    abox("%s_rail%d" % (P, sx), (0.1, 7.0, 0.24), (sx * 0.45, 0.05, 1.02), "M_Dark", G, bevel=0.01)
for k, y in enumerate((-3.2, -1.7, -0.1, 1.8, 3.3)):
    abox("%s_xm%d" % (P, k), (0.82, 0.1, 0.14), (0, y, 1.02), "M_Dark", G, bevel=0.005)
for i, y in enumerate(AX):
    acyl("%s_axle%d" % (P, i), 0.075, 1.72, (0, y, WR), "M_Dark", G, axis=0, seg=12)
    lathe_y("%s_diff%d" % (P, i), [(-0.2, 0.0), (-0.2, 0.12), (-0.08, 0.22), (0.1, 0.2), (0.18, 0.08), (0.18, 0.0)], (0.1, y, WR), ["M_Dark"], G, seg=16, axis='Y')
    for sx in (1, -1):
        acyl("%s_hubr%d_%d" % (P, i, sx), 0.13, 0.16, (sx * 0.72, y, WR), "M_Dark", G, axis=0, seg=14)
# leaf springs: front pair, rear bogie balancer packs
for sx in (1, -1):
    for k in range(4):
        abox("%s_fleaf%d_%d" % (P, sx, k), (0.09, 1.25 - k * 0.22, 0.025), (sx * 0.45, AX[0], 0.78 + k * 0.028), "M_Dark", G, bevel=0.003)
        abox("%s_rleaf%d_%d" % (P, sx, k), (0.1, 1.5 - k * 0.25, 0.03), (sx * 0.45, (AX[1] + AX[2]) / 2, 0.76 + k * 0.032), "M_Dark", G, bevel=0.003)
    acyl("%s_trun%d" % (P, sx), 0.1, 0.25, (sx * 0.5, (AX[1] + AX[2]) / 2, 0.9), "M_Dark", G, axis=0, seg=12)
    for y in AX[1:]:
        rod("%s_trod%d_%d" % (P, sx, int(y * 10)), Vector((sx * 0.4, (AX[1] + AX[2]) / 2, 0.95)), Vector((sx * 0.3, y, WR + 0.2)), 0.025, "M_Dark", G, seg=6)
abox(P + "_tcase", (0.5, 0.45, 0.4), (0.0, -0.6, 0.85), "M_Dark", G, bevel=0.02)
for y0, y1 in ((-0.6, AX[0]), (-0.6, AX[1]), (AX[1], AX[2])):
    rod("%s_shaft%d" % (P, int(y1 * 10)), Vector((0.05, y0, 0.8)), Vector((0.1, y1 - 0.2 * (1 if y1 > y0 else -1), WR + 0.05)), 0.04, "M_Steel", G, seg=8)
# fuel tanks, battery box, air tanks, steps (between front and middle wheels)
for sx in (1, -1):
    abox("%s_tank%d" % (P, sx), (0.42, 1.1, 0.48), (sx * 0.86, 0.15, 0.95), M, G, bevel=0.05)
    acyl("%s_tankc%d" % (P, sx), 0.05, 0.04, (sx * 0.86, 0.0, 1.21), "M_Dark", G, seg=10)
    for y in (-0.25, 0.55):
        abox("%s_tankst%d_%d" % (P, sx, int(y * 10)), (0.46, 0.04, 0.52), (sx * 0.86, y, 0.95), "M_Dark", G, bevel=0.004)
    for k in range(2):
        abox("%s_step%d_%d" % (P, sx, k), (0.32, 0.22, 0.03), (sx * 1.02, -0.95, 0.62 + k * 0.32), "M_Steel", G, bevel=0.004)
    abox("%s_stepb%d" % (P, sx), (0.04, 0.2, 0.42), (sx * 0.88, -0.95, 0.78), "M_Dark", G, bevel=0.004)
abox(P + "_batt", (0.5, 0.5, 0.32), (0.0, 0.6, 0.78), M, G, bevel=0.02)
acyl(P + "_airt", 0.11, 0.7, (-0.55, -1.25, 0.78), M, G, axis=1, seg=12)

# ---------------------------------------------------------------- front: bumper, winch, grille, hood, fenders, lamps
abox(P + "_bump", (2.3, 0.2, 0.24), (0, -3.58, 0.86), M, G, bevel=0.02)
acyl(P + "_winch", 0.13, 0.75, (0, -3.32, 0.92), "M_Dark", G, axis=0, seg=16)
acyl(P + "_winchc", 0.11, 0.7, (0, -3.32, 0.92), "M_Steel", G, axis=0, seg=16)
for sx in (1, -1):
    lathe_y("%s_hook%d" % (P, sx), [(-0.03, 0.06), (-0.03, 0.1), (0.03, 0.1), (0.03, 0.06)], (sx * 0.8, -3.72, 0.8), ["M_Steel"], G, seg=12, axis='X')
    abox("%s_bumpbr%d" % (P, sx), (0.1, 0.3, 0.2), (sx * 0.45, -3.4, 0.95), "M_Dark", G, bevel=0.01)
abox(P + "_grille", (0.98, 0.08, 0.95), (0, -3.42, 1.42), M, G, bevel=0.02)
for k in range(11):
    abox("%s_gbar%d" % (P, k), (0.03, 0.04, 0.82), (-0.4 + k * 0.08, -3.47, 1.42), "M_Dark", G, bevel=0.0)
for z in (1.05, 1.82):
    abox("%s_gfr%d" % (P, int(z * 10)), (1.0, 0.06, 0.06), (0, -3.47, z), M, G, bevel=0.01)
hood_ring = [(-0.55, 1.15), (-0.53, 1.74), (-0.42, 1.90), (0.42, 1.90), (0.53, 1.74), (0.55, 1.15)]
loft_rings(P + "_hood", [[Vector((x, y, z)) for x, z in hood_ring] for y in (-3.40, CAB0 + 0.02)], M, G, bevel=0.02)
for sx in (1, -1):
    slats("%s_hlouv%d" % (P, sx), sx * 0.555, sx * 0.556, -2.9, -2.0, 1.5, 8, "M_Dark", G, along='Y', t=0.02, h=0.2, tilt=0.0)
    rod("%s_hlatch%d" % (P, sx), Vector((sx * 0.5, -2.6, 1.82)), Vector((sx * 0.5, -2.45, 1.82)), 0.012, "M_Steel", G, seg=5)
    c = Vector((sx * WX, AX[0], WR))
    arc_shell("%s_fend%d" % (P, sx), c, 0.68, 0.72, 0.52, 0.25, 2.95, M, G, seg=16)
    abox("%s_fendin%d" % (P, sx), (0.25, 1.4, 0.03), (sx * 0.66, AX[0], 1.33), M, G, bevel=0.005)
    # headlamps on the fender noses + side lights + blackout lamp
    hl = Vector((sx * 0.86, -3.12, 1.42))
    acyl("%s_hl%d" % (P, sx), 0.11, 0.14, tuple(hl), M, G, axis=1, seg=16)
    acyl("%s_hlc%d" % (P, sx), 0.12, 0.02, tuple(hl + Vector((0, -0.07, 0))), "M_Chrome", G, axis=1, seg=16)
    acyl("%s_hll%d" % (P, sx), 0.095, 0.01, tuple(hl + Vector((0, -0.082, 0))), "M_HeadLamp", G, axis=1, seg=16, dirt=False)
    abox("%s_hlbr%d" % (P, sx), (0.06, 0.1, 0.16), tuple(hl + Vector((0, 0.08, -0.14))), "M_Dark", G, bevel=0.005)
    acyl("%s_side%d" % (P, sx), 0.035, 0.05, (sx * 1.12, -2.95, 1.48), "M_Amber", G, axis=1, seg=10, dirt=False)
    # mirrors on arms
    arm0 = Vector((sx * 1.15, CAB0 + 0.1, 2.15))
    rod("%s_marm%d" % (P, sx), arm0, arm0 + Vector((sx * 0.25, -0.05, 0.05)), 0.012, "M_Dark", G, seg=6)
    abox("%s_mir%d" % (P, sx), (0.04, 0.2, 0.32), tuple(arm0 + Vector((sx * 0.27, -0.05, 0.05))), "M_Black", G, bevel=0.01)
# exhaust stack + air filter behind the cab
rod(P + "_exh", Vector((-0.75, -0.02, 1.0)), Vector((-0.75, -0.02, 2.75)), 0.05, "M_Dark", G, seg=10)
acyl(P + "_exhm", 0.12, 0.6, (-0.75, -0.02, 1.4), "M_Dark", G, seg=14)
acyl(P + "_airf", 0.17, 0.55, (0.75, -0.02, 1.55), M, G, seg=16)
acyl(P + "_airfc", 0.19, 0.04, (0.75, -0.02, 1.85), "M_Dark", G, seg=16)

# ---------------------------------------------------------------- cab (thin walls: the driver looks out through the glass)
W2 = 1.15
abox(P + "_cfloor", (2.3, 1.5, 0.05), (0, (CAB0 + CAB1) / 2, ZF), M, G, bevel=0.005)
abox(P + "_croof", (2.34, 1.62, 0.06), (0, (CAB0 + CAB1) / 2 - 0.05, ZR), M, G, bevel=0.02)
abox(P + "_cvisor", (2.2, 0.16, 0.03), (0, CAB0 - 0.12, ZR - 0.03), M, G, rot=(-0.25, 0, 0), bevel=0.005)
abox(P + "_cback", (2.3, 0.05, ZR - ZF), (0, CAB1, (ZF + ZR) / 2), M, G, bevel=0.005)
abox(P + "_cbackw", (0.7, 0.015, 0.32), (0, CAB1 - 0.02, 2.25), "M_CabGlass", G, bevel=0.0, dirt=False)
abox(P + "_cfront", (2.3, 0.05, 0.82), (0, CAB0, ZF + 0.41), M, G, bevel=0.005)
ZW0, ZW1 = 1.88, 2.50
for k, x in enumerate((-W2 + 0.03, 0.0, W2 - 0.03)):
    abox("%s_cpost%d" % (P, k), (0.07 if k != 1 else 0.06, 0.06, ZR - ZW0 + 0.04), (x, CAB0, (ZW0 + ZR) / 2), M, G, bevel=0.005)
abox(P + "_cwsb", (2.3, 0.06, 0.07), (0, CAB0, ZW1 + 0.07), M, G, bevel=0.005)
for sx in (1, -1):
    abox("%s_ws%d" % (P, sx), (1.04, 0.012, ZW1 - ZW0), (sx * 0.56, CAB0, (ZW0 + ZW1) / 2), "M_CabGlass", G, bevel=0.0, dirt=False)
    rod("%s_wiper%d" % (P, sx), Vector((sx * 0.56, CAB0 - 0.03, ZW1 + 0.02)), Vector((sx * 0.3, CAB0 - 0.03, ZW0 + 0.15)), 0.007, "M_Black", G, seg=4)
    x = sx * W2
    # door: lower panel, window frame, glass, handle; rear quarter window
    abox("%s_door%d" % (P, sx), (0.05, 0.95, ZW0 - ZF), (x, CAB0 + 0.55, (ZF + ZW0) / 2), M, G, bevel=0.006)
    abox("%s_cquar%d" % (P, sx), (0.05, 0.48, ZR - ZF), (x, CAB1 - 0.24, (ZF + ZR) / 2), M, G, bevel=0.006)
    abox("%s_cfp%d" % (P, sx), (0.05, 0.08, ZR - ZF), (x, CAB0 + 0.04, (ZF + ZR) / 2), M, G, bevel=0.006)
    abox("%s_dwin%d" % (P, sx), (0.012, 0.86, ZR - ZW0 - 0.1), (x, CAB0 + 0.56, (ZW0 + ZR - 0.1) / 2), "M_CabGlass", G, bevel=0.0, dirt=False)
    abox("%s_dwfr%d" % (P, sx), (0.05, 0.06, ZR - ZW0), (x, CAB0 + 1.04, (ZW0 + ZR) / 2), M, G, bevel=0.005)
    rod("%s_dhdl%d" % (P, sx), Vector((x + sx * 0.03, CAB0 + 0.85, 1.78)), Vector((x + sx * 0.03, CAB0 + 0.98, 1.78)), 0.012, "M_Steel", G, seg=5)
    for z in (1.25, 1.75):
        acyl("%s_dhinge%d_%d" % (P, sx, int(z * 100)), 0.02, 0.1, (x + sx * 0.02, CAB0 + 0.1, z), "M_Dark", G, seg=8)
    abox("%s_marker%d" % (P, sx), (0.08, 0.05, 0.04), (sx * 0.9, CAB0 + 0.05, ZR + 0.05), "M_Amber", G, bevel=0.005, dirt=False)
# interior: dashboard with gauges, seats, levers, rear-wall trim
DY = CAB0 + 0.13                     # dash face (towards the driver)
abox(P + "_dash", (2.2, 0.16, 0.38), (0, CAB0 + 0.05, 1.66), "M_Dash", G, bevel=0.02)
abox(P + "_dashtop", (2.2, 0.2, 0.03), (0, CAB0 + 0.08, 1.86), "M_Interior", G, bevel=0.005)
abox(P + "_engbox", (0.5, 0.6, 0.35), (0, CAB0 + 0.35, ZF + 0.18), "M_Interior", G, bevel=0.04)
SW = Vector((0.5, CAB0 + 0.38, 1.80))
needles = {}
for n, (dx, dz) in {"needle_speed": (0.16, 0.0), "needle_rpm": (-0.16, 0.0), "needle_fuel": (0.0, 0.09), "needle_temp": (0.0, -0.08)}.items():
    c = Vector((SW.x + dx, DY - 0.005, 1.70 + dz))
    r = 0.062 if n in ("needle_speed", "needle_rpm") else 0.033
    gauge(P + "_" + n, c - Vector((0, 0.02, 0)), r, G)
    needles[n] = (c + Vector((0, 0.005, 0)), r)
for k in range(5):
    acyl("%s_sw%d" % (P, k), 0.012, 0.02, (-0.1 + k * 0.08, DY, 1.55), "M_Black", G, axis=1, seg=6, dirt=False)
acyl(P + "_lampw", 0.015, 0.01, (SW.x, DY, 1.82), "M_NavRed", G, axis=1, seg=8, dirt=False)
rod(P + "_column", SW + Vector((0, -0.04, -0.02)), SW + Vector((0, -0.4, -0.25)), 0.035, "M_Dash", G, seg=10)
for k, dx in enumerate((-0.15, 0.0, 0.15)):
    tilted_box("%s_pedal%d" % (P, k), (0, SW.y - 0.3, ZF + 0.08), (0, SW.y - 0.35, ZF + 0.28), 0.07, 0.015, "M_Steel", G)
    bpy.data.objects["%s_pedal%d" % (P, k)].location.x = SW.x + dx
for k, dx in enumerate((0.0, 0.12)):
    rod("%s_lever%d" % (P, k), Vector((0.12 + dx, -0.95, ZF + 0.2)), Vector((0.1 + dx, -1.02, ZF + 0.68)), 0.012, "M_Steel", G, seg=6)
    acyl("%s_knob%d" % (P, k), 0.03, 0.05, (0.1 + dx, -1.02, ZF + 0.7), "M_Black", G, seg=8)
for sx in (1, -1):
    xs = sx * 0.5
    sy, sz = -0.95 + 0.05, 1.48 - 0.18
    abox("%s_seatc%d" % (P, sx), (0.5, 0.48, 0.12), (xs, sy, sz), "M_Seat", G, bevel=0.03)
    abox("%s_seatb%d" % (P, sx), (0.5, 0.12, 0.65), (xs, sy + 0.3, sz + 0.36), "M_Seat", G, rot=(-0.18, 0, 0), bevel=0.03)
    abox("%s_seatbase%d" % (P, sx), (0.36, 0.36, sz - ZF - 0.06), (xs, sy, (sz + ZF) / 2), "M_Dark", G, bevel=0.01)
abox(P + "_bwpanel", (2.2, 0.03, 0.6), (0, CAB1 - 0.04, 1.85), "M_Interior", G, bevel=0.005)
acyl(P + "_extg", 0.05, 0.38, (-0.95, CAB1 - 0.1, ZF + 0.3), "M_NavRed", G, seg=10)

# ---------------------------------------------------------------- cargo body + troop tent
L = BODY1 - BODY0
YC = (BODY0 + BODY1) / 2
abox(P + "_bfloor", (2.42, L, 0.08), (0, YC, ZB - 0.04), "M_Plank", G, bevel=0.005)
for k in range(6):
    abox("%s_bsill%d" % (P, k), (2.42, 0.08, 0.1), (0, BODY0 + 0.2 + k * 0.66, ZB - 0.13), "M_Dark", G, bevel=0.005)
for sx in (1, -1):
    abox("%s_bsidel%d" % (P, sx), (0.04, L, 0.5), (sx * 1.19, YC, ZB + 0.25), "M_Plank", G, bevel=0.005)
    for k in range(3):
        abox("%s_bplank%d_%d" % (P, sx, k), (0.045, L, 0.012), (sx * 1.19, YC, ZB + 0.12 + k * 0.14), "M_Dark", G, bevel=0.0)
    for k in range(5):
        abox("%s_bpost%d_%d" % (P, sx, k), (0.06, 0.08, 0.52), (sx * 1.21, BODY0 + 0.05 + k * (L - 0.1) / 4, ZB + 0.25), M, G, bevel=0.005)
    # benches (fold-down) for 6 troops per side
    abox("%s_bench%d" % (P, sx), (0.34, L - 0.4, 0.05), (sx * 0.95, YC + 0.1, ZB + 0.2), "M_Plank", G, bevel=0.005)
    for k in range(4):
        abox("%s_benchl%d_%d" % (P, sx, k), (0.04, 0.04, 0.2), (sx * 0.82, BODY0 + 0.5 + k * 0.95, ZB + 0.1), "M_Steel", G, bevel=0.0)
    # mudguards over the rear bogie, reflectors
    abox("%s_mud%d" % (P, sx), (0.5, 2.25, 0.04), (sx * WX, (AX[1] + AX[2]) / 2, 1.30), M, G, bevel=0.005)
    tilted_box("%s_mudf%d" % (P, sx), (sx * WX, AX[2] + 1.12, 1.3), (sx * WX, AX[2] + 1.12, 0.62), 0.48, 0.015, "M_Rubber", G)
abox(P + "_bfront", (2.42, 0.05, 0.62), (0, BODY0, ZB + 0.31), "M_Plank", G, bevel=0.005)
abox(P + "_tgate", (2.42, 0.05, 0.5), (0, BODY1, ZB + 0.25), "M_Plank", G, bevel=0.005)
for sx in (1, -1):
    abox("%s_tlamp%d" % (P, sx), (0.2, 0.06, 0.1), (sx * 0.95, BODY1 + 0.02, 1.2), "M_Dark", G, bevel=0.005)
    acyl("%s_tlampr%d" % (P, sx), 0.03, 0.01, (sx * 0.9, BODY1 + 0.055, 1.2), "M_NavRed", G, axis=1, seg=8, dirt=False)
    acyl("%s_tlampa%d" % (P, sx), 0.03, 0.01, (sx * 1.0, BODY1 + 0.055, 1.2), "M_Amber", G, axis=1, seg=8, dirt=False)
    abox("%s_tlbr%d" % (P, sx), (0.06, 0.25, 0.04), (sx * 0.95, BODY1 - 0.1, 1.27), "M_Dark", G, bevel=0.0)
abox(P + "_plate", (0.5, 0.02, 0.14), (0.0, BODY1 + 0.03, ZB + 0.2), "M_Stencil", G, bevel=0.0, dirt=False)
abox(P + "_rbump", (2.0, 0.12, 0.12), (0, BODY1 - 0.1, 1.1), "M_Dark", G, bevel=0.005)
acyl(P + "_pintle", 0.06, 0.12, (0, BODY1 + 0.02, 0.95), "M_Steel", G, axis=1, seg=10)
# tent: side walls + chamfers + roof + front wall (thin boxes = visible from inside and out), rolled rear flap, hoops
ZT0, ZT1, ZT2 = ZB + 0.5, 2.66, 2.84
for sx in (1, -1):
    abox("%s_tside%d" % (P, sx), (0.025, L - 0.05, ZT1 - ZT0), (sx * 1.20, YC, (ZT0 + ZT1) / 2), "M_Canvas", G, bevel=0.0)
    tilted_box("%s_tch%d" % (P, sx), (0, BODY0, ZT1), (0, BODY0 + 0.01, ZT1), 0.1, 0.02, "M_Canvas", G)
    p0, p1 = Vector((sx * 1.20, YC, ZT1)), Vector((sx * 1.04, YC, ZT2))
    c = (p0 + p1) / 2
    d = p1 - p0
    abox("%s_tchm%d" % (P, sx), (d.length + 0.02, L - 0.05, 0.025), tuple(c), "M_Canvas", G, rot=(0, -math.atan2(d.z, d.x), 0), bevel=0.0)
    for k in range(6):   # tie-down straps
        abox("%s_tstrap%d_%d" % (P, sx, k), (0.03, 0.04, ZT1 - ZT0), (sx * 1.215, BODY0 + 0.3 + k * 0.62, (ZT0 + ZT1) / 2), "M_CanvasIn", G, bevel=0.0)
for o in ("%s_tch1" % P, "%s_tch-1" % P):
    bpy.data.objects.remove(bpy.data.objects[o], do_unlink=True)
abox(P + "_troof", (2.1, L - 0.05, 0.025), (0, YC, ZT2), "M_Canvas", G, bevel=0.0)
abox(P + "_tfront", (2.4, 0.025, ZT2 - ZT0), (0, BODY0 + 0.03, (ZT0 + ZT2) / 2), "M_Canvas", G, bevel=0.0)
acyl(P + "_troll", 0.1, 2.3, (0, BODY1 - 0.08, ZT2 - 0.12), "M_Canvas", G, axis=0, seg=12)
for k in range(5):
    y = BODY0 + 0.3 + k * (L - 0.5) / 4
    for sx in (1, -1):
        rod("%s_hoopv%d_%d" % (P, k, sx), Vector((sx * 1.17, y, ZT0)), Vector((sx * 1.17, y, ZT1)), 0.015, "M_Steel", G, seg=5)
        rod("%s_hoopc%d_%d" % (P, k, sx), Vector((sx * 1.17, y, ZT1)), Vector((sx * 1.02, y, ZT2 - 0.03)), 0.015, "M_Steel", G, seg=5)
    rod("%s_hoopt%d" % (P, k), Vector((1.02, y, ZT2 - 0.03)), Vector((-1.02, y, ZT2 - 0.03)), 0.015, "M_Steel", G, seg=5)
# number plates / tactical marking stencils
abox(P + "_fplate", (0.52, 0.02, 0.12), (0.0, -3.69, 0.86), "M_Stencil", G, bevel=0.0, dirt=False)

# ---------------------------------------------------------------- wheels
WHEELS = []
i = 0
for y in AX:
    for sx in (1, -1):
        g = "%s_w%d" % (P, i)
        c = Vector((sx * WX, y, WR))
        big_wheel(g, c, WR, WW, sx)
        WHEELS.append((i, g, c))
        i += 1

# ---------------------------------------------------------------- hierarchy + export
auto_attach([G], bracket_groups=())
weather(gen_weather([(-0.75, -0.02, 2.75)], 0.6))
rt = root("root")
rig_maz(P, {"G": G, "wheels": WHEELS, "jacks": [], "steer": SW, "needles": needles}, rt)
rw_export("/home/user/.cache/rwveh/raw/rw_ural.glb", "/home/user/models/rw_ural.blend", "RW_URAL4320")
