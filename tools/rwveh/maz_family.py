# S-400 TEL (5P85-type, 4 x 48N6 canisters) and mobile radar (96L6-type panel) on MAZ-543M — Real War node contract.
# VARIANT is prepended by run.sh: VARIANT = "s400" | "radar"
# s400 : root > wheel_0..7, steering_wheel, needle_*, jack_0/1, launcher_pitch (0,1.75,-5.4) > canister_0..3 (at the rear base),
#        lid_0..3 (front top edge hinge, rot -X opens forward/up)
# radar: root > ..., launcher_pitch (empty), radar_yaw (0,3.78,-1.8) > rotating antenna
reset_scene()
col_get("BUILD"); col_get("RIG")
rw_mats()
amat("M_Canister", None, 0.6, 0.08, factor=(0.22, 0.27, 0.17, 1))
amat("M_Panel", None, 0.55, 0.15, factor=(0.30, 0.34, 0.27, 1))
amat("M_Radome", None, 0.7, 0.0, factor=(0.55, 0.58, 0.50, 1))
P = "S400" if VARIANT == "s400" else "RADAR"
cfg = dict(FZ=6.45, RZ=-6.25, AXZ=[4.7, 3.1, -1.9, -3.5], WR=0.75, TX=1.22, cab_back=3.4, cab_top=3.3,
           driver_x=0.75, seat=(1.92, 4.55), steer=(2.19, 4.78), jacks=[(1.35, -5.45, 0.6), (-1.35, -5.45, 0.6)])
ch = maz543(P, cfg)
G = ch["G"]
cb = ch["cb"]
yR = 6.25
DZ = 1.6
PIV = gB(0, 1.75, -5.4)

# ---------------------------------------------------------------- deck + gas-turbine power unit behind the cabs
abox(P + "_deck", (2.9, yR - 0.3 - cb, 0.08), (0, (cb + yR - 0.3) / 2, DZ - 0.04), "M_Plain", G, bevel=0.01)
pu0, pu1 = cb + 0.05, cb + 0.85
abox(P + "_pu", (2.8, pu1 - pu0, 1.25), (0, (pu0 + pu1) / 2, DZ + 0.625), "M_Body", G, bevel=0.04)
for sx in (1, -1):
    slats(P + "_pulv%d" % sx, 1.405 * sx - 0.01, 1.405 * sx + 0.01, pu0 + 0.1, pu1 - 0.1, DZ + 0.75, 7, "M_Dark", G, along='X', tilt=0.0)
    for k in range(6):
        abox("%s_pugr%d_%d" % (P, sx, k), (0.03, pu1 - pu0 - 0.2, 0.035), (1.41 * sx, (pu0 + pu1) / 2, DZ + 0.35 + 0.12 * k), "M_Dark", G, bevel=0.0)
    abox(P + "_pudoor%d" % sx, (0.02, 0.5, 0.9), (1.41 * sx, (pu0 + pu1) / 2, DZ + 0.62), "M_Body", G, bevel=0.005)
acyl(P + "_puexh", 0.14, 0.3, (-0.9, (pu0 + pu1) / 2, DZ + 1.38), "M_Dark", G, seg=14)
acyl(P + "_puexh2", 0.17, 0.05, (-0.9, (pu0 + pu1) / 2, DZ + 1.52), "M_Black", G, seg=14)
slats(P + "_putop", -0.4, 0.6, pu0 + 0.1, pu1 - 0.1, DZ + 1.26, 6, "M_Dark", G)
for k in range(10):
    abox("%s_deckrib%d" % (P, k), (2.9, 0.04, 0.03), (0, pu1 + 0.4 + k * 0.85, DZ + 0.015), "M_Dark", G, bevel=0.0)
# rear: erector hinge brackets, deflector support, ladder, stowage
for sx in (1, -1):
    prism_x(P + "_hbr%d" % sx, [(PIV.y - 0.6, DZ), (PIV.y + 0.45, DZ), (PIV.y + 0.25, PIV.z + 0.22), (PIV.y - 0.25, PIV.z + 0.22)], 0.07,
            "M_Plain", G, bevel=0.015, x0=1.08 * sx - 0.07)
    acyl(P + "_hpin%d" % sx, 0.1, 0.24, (1.08 * sx, PIV.y, PIV.z), "M_Steel", G, axis=0, seg=14)
    rod(P + "_lad%d" % sx, Vector((0.3 * sx, yR - 0.3, 0.6)), Vector((0.3 * sx, yR - 0.35, DZ)), 0.02, "M_Steel", G, seg=6)
    abox(P + "_sbox%d" % sx, (0.6, 1.2, 0.45), (1.1 * sx, pu1 + 0.8, DZ + 0.225), "M_Body", G, bevel=0.03)
for k in range(3):
    rod("%s_ladr%d" % (P, k), Vector((-0.3, yR - 0.31, 0.8 + 0.27 * k)), Vector((0.3, yR - 0.31, 0.8 + 0.27 * k)), 0.015, "M_Steel", G, seg=6)

extra_nodes = []
if VARIANT == "s400":
    # front canister rest (saddle) + transport locks
    sy = -1.9
    for sx in (1, -1):
        rod(P + "_rest%d" % sx, Vector((0.95 * sx, sy, DZ)), Vector((0.85 * sx, sy, 1.76)), 0.07, "M_Plain", G, seg=10)
    abox(P + "_restbeam", (2.0, 0.22, 0.1), (0, sy, 1.78), "M_Plain", G, bevel=0.02)
    abox(P + "_restpad", (1.9, 0.24, 0.02), (0, sy, 1.84), "M_Rubber", G, bevel=0.0)
    # rear erector ram bases (static)
    for sx in (1, -1):
        acyl(P + "_rambase%d" % sx, 0.16, 0.5, (0.75 * sx, PIV.y - 1.2, DZ + 0.2), "M_Plain", G, axis=1, seg=14)
    weather(gen_weather([tuple(ch["exhaust"]), (-0.9, (pu0 + pu1) / 2, DZ + 1.4)], 0.7))

    # ---------------------------------------------------------------- erector (launcher_pitch)
    GP = P + "_erector"
    y0, y1 = -2.35, PIV.y
    for sx in (1, -1):
        abox(P + "_ebeam%d" % sx, (0.16, y1 - y0, 0.2), (0.98 * sx, (y0 + y1) / 2, 1.9), "M_Plain", GP, bevel=0.02)
        abox(P + "_ebeamt%d" % sx, (0.1, y1 - y0 - 0.4, 0.12), (1.0 * sx, (y0 + y1) / 2 + 0.2, 3.72), "M_Plain", GP, bevel=0.015)
        posts = [y0 + 0.2 + (y1 - y0 - 0.5) * k / 5 for k in range(6)]
        for k, py in enumerate(posts):
            rod("%s_epost%d_%d" % (P, sx, k), Vector((1.0 * sx, py, 1.95)), Vector((1.0 * sx, py, 3.68)), 0.045, "M_Plain", GP, seg=8)
            if k < 5:
                rod("%s_ediag%d_%d" % (P, sx, k), Vector((1.0 * sx, py, 1.98)), Vector((1.0 * sx, posts[k + 1], 3.65)), 0.03, "M_Plain", GP, seg=6)
        acyl(P + "_eboss%d" % sx, 0.13, 0.2, (0.92 * sx, PIV.y, PIV.z), "M_Plain", GP, axis=0, seg=14)
        prism_x(P + "_eknee%d" % sx, [(PIV.y - 0.5, 1.8), (PIV.y + 0.1, 1.8), (PIV.y + 0.15, PIV.z - 0.1), (PIV.y - 0.2, PIV.z - 0.12)],
                0.05, "M_Plain", GP, bevel=0.01, x0=0.92 * sx - 0.05)
    for k in range(5):
        y = y0 + 0.3 + (y1 - y0 - 0.6) * k / 4
        abox("%s_ecross%d" % (P, k), (1.96, 0.14, 0.14), (0, y, 1.82), "M_Plain", GP, bevel=0.01)
        # canister clamp bands (half rings on the side of each column)
        for sx in (1, -1):
            for zc in (2.3, 3.25):
                arc_shell("%s_band%d_%d_%d" % (P, k, sx, int(zc * 10)), (0.5 * sx, y, zc), 0.445, 0.48, 0.1,
                          math.radians(-60 if sx > 0 else 120), math.radians(60 if sx > 0 else 240), "M_Plain", GP, seg=8)
                rod("%s_bandt%d_%d_%d" % (P, k, sx, int(zc * 10)), Vector((0.5 * sx + 0.42 * sx, y, zc)), Vector((1.0 * sx, y, zc)), 0.035, "M_Plain", GP, seg=6)
    abox(P + "_eupper", (1.96, 0.12, 0.1), (0, y0 + 0.3, 3.75), "M_Plain", GP, bevel=0.01)
    # rear deflector plate behind the canister bases
    abox(P + "_deflect", (2.0, 0.08, 2.0), (0, PIV.y - 0.1, 2.78), "M_Dark", GP, bevel=0.02)
    for k in range(4):
        abox("%s_defrib%d" % (P, k), (0.06, 0.14, 1.9), (-0.75 + 0.5 * k, PIV.y - 0.05, 2.78), "M_Plain", GP, bevel=0.005)
    rod(P + "_ecable", Vector((0.0, y0 + 0.4, 3.82)), Vector((0.0, y1 - 0.4, 3.82)), 0.03, "M_Black", GP, seg=6)

    # ---------------------------------------------------------------- 4 transport-launch canisters + lids
    CAN = [(0.5, 2.3), (-0.5, 2.3), (0.5, 3.25), (-0.5, 3.25)]
    CR = 0.44
    cy0, cy1 = -2.4, 5.2
    cans, lids = [], []
    for c, (cx, cz) in enumerate(CAN):
        g = "%s_can%d" % (P, c)
        acyl(g + "_tube", CR, cy1 - cy0, (cx, (cy0 + cy1) / 2, cz), "M_Canister", g, axis=1, seg=28)
        for k in range(7):
            yy = cy0 + 0.2 + (cy1 - cy0 - 0.4) * k / 6
            acyl("%s_rib%d" % (g, k), CR + 0.018, 0.07, (cx, yy, cz), "M_Canister", g, axis=1, seg=28)
        for k in range(4):
            a = PI / 4 + k * PI / 2
            abox("%s_str%d" % (g, k), (0.04, cy1 - cy0 - 0.3, 0.04), (cx + math.cos(a) * (CR + 0.01), (cy0 + cy1) / 2, cz + math.sin(a) * (CR + 0.01)),
                 "M_Canister", g, rot=(0, -a, 0), bevel=0.0)
        acyl(g + "_flange", CR + 0.03, 0.08, (cx, cy0 + 0.04, cz), "M_Plain", g, axis=1, seg=28)
        lathe_y(g + "_base", [(0.0, CR + 0.02), (0.08, CR + 0.02), (0.12, CR - 0.06), (0.16, 0.2), (0.17, 0.0)], (cx, cy1, cz),
                ["M_Plain"], g, seg=28, axis='Y')
        acyl(g + "_conn", 0.06, 0.12, (cx + 0.3 * (1 if cx > 0 else -1), cy1 + 0.1, cz + 0.25), "M_Dark", g, axis=1, seg=10)
        for k in (0, 1):
            abox("%s_lug%d" % (g, k), (0.06, 0.12, 0.08), (cx, cy0 + 1.4 + 4.2 * k, cz + CR + 0.04), "M_Steel", g, bevel=0.01)
        abox(g + "_stencil", (0.02, 1.2, 0.12), (cx + (CR + 0.01) * (1 if cx > 0 else -1), 1.2, cz), "M_Stencil", g, bevel=0.0, dirt=False)
        cans.append((c, g, Vector((cx, PIV.y, cz))))
        gl = "%s_lid%d" % (P, c)
        lathe_y(gl + "_dome", [(0.06, 0.0), (0.06, CR + 0.03), (0.0, CR + 0.03), (-0.03, CR - 0.05), (-0.07, 0.25), (-0.09, 0.0)],
                (cx, cy0 - 0.05, cz), ["M_Canister"], gl, seg=28, axis='Y')
        acyl(gl + "_hinge", 0.03, 0.3, (cx, cy0 - 0.05, cz + CR + 0.02), "M_Steel", gl, axis=0, seg=8)
        for k in range(6):
            a = 2 * PI * k / 6
            acyl("%s_clip%d" % (gl, k), 0.025, 0.05, (cx + math.cos(a) * (CR + 0.03), cy0 - 0.02, cz + math.sin(a) * (CR + 0.03)), "M_Steel", gl, axis=1, seg=6, dirt=False)
        lids.append((c, gl, Vector((cx, cy0 - 0.03, cz + 0.43))))
else:
    # ---------------------------------------------------------------- equipment shelter on the deck
    s0, s1 = pu1 + 0.15, yR - 0.55
    SZ = 3.6
    abox(P + "_shel", (2.84, s1 - s0, SZ - DZ), (0, (s0 + s1) / 2, (SZ + DZ) / 2), "M_Body", G, bevel=0.05)
    abox(P + "_shelroof", (2.9, s1 - s0 + 0.06, 0.05), (0, (s0 + s1) / 2, SZ + 0.02), "M_Plain", G, bevel=0.01)
    for sx in (1, -1):
        x = 1.425 * sx
        # side doors, louvres, cable ports, handles
        abox(P + "_sdoor%d" % sx, (0.02, 0.85, 1.75), (x, s0 + 1.0, DZ + 1.0), "M_Body", G, bevel=0.005)
        for e in (-0.43, 0.43):
            abox("%s_sdseam%d_%d" % (P, sx, int(e * 100)), (0.012, 0.012, 1.75), (x + 0.012 * sx, s0 + 1.0 + e, DZ + 1.0), "M_Black", G, bevel=0.0, dirt=False)
        abox(P + "_sdh%d" % sx, (0.04, 0.03, 0.18), (x + 0.03 * sx, s0 + 1.3, DZ + 1.0), "M_Steel", G, bevel=0.0)
        for k in range(8):
            abox("%s_slv%d_%d" % (P, sx, k), (0.03, 1.1, 0.04), (x + 0.01 * sx, s0 + 3.1, DZ + 0.7 + 0.1 * k), "M_Dark", G, bevel=0.0)
        abox(P + "_ac%d" % sx, (0.25, 0.9, 0.6), (x + 0.12 * sx, s1 - 0.9, DZ + 1.3), "M_Plain", G, bevel=0.03)
        acyl(P + "_acfan%d" % sx, 0.22, 0.04, (x + 0.25 * sx, s1 - 0.9, DZ + 1.3), "M_Black", G, axis=0, seg=16)
        for k in range(3):
            acyl("%s_port%d_%d" % (P, sx, k), 0.05, 0.08, (x + 0.03 * sx, s0 + 2.2 + 0.2 * k, DZ + 0.4), "M_Dark", G, axis=0, seg=8)
        rod(P + "_rail%d" % sx, Vector((1.38 * sx, s0 + 0.2, SZ + 0.35)), Vector((1.38 * sx, s1 - 0.2, SZ + 0.35)), 0.025, "M_Steel", G, seg=6)
        for k in range(6):
            yy = s0 + 0.2 + (s1 - s0 - 0.4) * k / 5
            rod("%s_railp%d_%d" % (P, sx, k), Vector((1.38 * sx, yy, SZ + 0.04)), Vector((1.38 * sx, yy, SZ + 0.35)), 0.02, "M_Steel", G, seg=6)
    # rear door + ladder, folded mast, GPS, roof hatches
    abox(P + "_rdoor", (0.9, 0.02, 1.8), (0.4, s1 + 0.01, DZ + 1.0), "M_Body", G, bevel=0.005)
    abox(P + "_rdh", (0.04, 0.03, 0.2), (0.75, s1 + 0.03, DZ + 1.0), "M_Steel", G, bevel=0.0)
    acyl(P + "_mast", 0.09, 3.5, (-1.0, (s0 + s1) / 2 + 0.6, SZ + 0.15), "M_Plain", G, axis=1, seg=10)
    for k in range(3):
        abox("%s_mastclamp%d" % (P, k), (0.24, 0.1, 0.12), (-1.0, (s0 + s1) / 2 - 0.8 + 1.3 * k, SZ + 0.1), "M_Dark", G, bevel=0.01)
    acyl(P + "_gps", 0.08, 0.06, (1.0, s1 - 0.4, SZ + 0.08), "M_Radome", G, seg=14)
    acyl(P + "_gps2", 0.08, 0.06, (1.0, s0 + 0.4, SZ + 0.08), "M_Radome", G, seg=14)
    abox(P + "_hatch", (0.7, 0.7, 0.05), (0.8, s1 - 1.6, SZ + 0.06), "M_Plain", G, bevel=0.01)
    # fixed turntable ring on the roof
    RY = gB(0, 3.78, -1.8)
    lathe_y(P + "_ring", [(0.0, 0.7), (0.0, 1.0), (0.1, 1.0), (0.1, 0.7)], (RY.x, RY.y, SZ + 0.04), ["M_Dark"], G, seg=36, axis='Z')
    weather(gen_weather([tuple(ch["exhaust"]), (-0.9, (pu0 + pu1) / 2, DZ + 1.4)], 0.7))

    # ---------------------------------------------------------------- rotating antenna (radar_yaw)
    GR = P + "_ant"
    lathe_y(P + "_rot", [(0.0, 0.0), (0.0, 0.95), (0.18, 0.95), (0.2, 0.0)], (RY.x, RY.y, SZ + 0.14), ["M_Plain"], GR, seg=36, axis='Z')
    abox(P + "_ped", (1.4, 0.9, 0.7), (0, RY.y + 0.25, SZ + 0.68), "M_Body", GR, bevel=0.04)
    tilt = math.radians(15)
    ah, aw = 5.4, 6.3
    bot = Vector((0, RY.y - 0.25, 4.0))
    up = Vector((0, math.sin(tilt), math.cos(tilt)))      # top leans back (+Y)
    nrm = Vector((0, -math.cos(tilt), math.sin(tilt)))     # face looks forward/up
    ctr = bot + up * (ah / 2)
    rot = (-tilt, 0, 0)
    abox(P + "_panel", (aw, 0.32, ah), tuple(ctr), "M_Panel", GR, rot=rot, bevel=0.03)
    abox(P + "_rim", (aw + 0.1, 0.36, 0.12), tuple(bot + up * 0.03), "M_Plain", GR, rot=rot, bevel=0.02)
    abox(P + "_rimt", (aw + 0.1, 0.36, 0.12), tuple(bot + up * (ah - 0.03)), "M_Plain", GR, rot=rot, bevel=0.02)
    for sx in (1, -1):
        abox(P + "_rims%d" % sx, (0.12, 0.36, ah), tuple(ctr + Vector((sx * aw / 2, 0, 0))), "M_Plain", GR, rot=rot, bevel=0.02)
    # radiating face: element rows + sub-array seams
    face = ctr + nrm * 0.17
    for r_ in range(26):
        f = (r_ + 0.5) / 26 - 0.5
        abox("%s_row%d" % (P, r_), (aw - 0.2, 0.02, 0.05), tuple(face + up * (f * (ah - 0.25))), "M_Radome", GR, rot=rot, bevel=0.0, dirt=False)
    for c_ in range(1, 8):
        f = c_ / 8 - 0.5
        abox("%s_col%d" % (P, c_), (0.04, 0.03, ah - 0.15), tuple(face + Vector((f * aw, 0, 0)) + nrm * 0.005), "M_Dark", GR, rot=rot, bevel=0.0, dirt=False)
    # IFF array on top, side-lobe cancellers, back truss, electronics boxes, waveguide
    abox(P + "_iff", (aw * 0.7, 0.18, 0.45), tuple(bot + up * (ah + 0.3) + nrm * 0.05), "M_Radome", GR, rot=rot, bevel=0.03)
    for sx in (1, -1):
        abox(P + "_slc%d" % sx, (0.5, 0.15, 0.9), tuple(ctr + Vector((sx * (aw / 2 + 0.35), 0, -0.8))), "M_Radome", GR, rot=rot, bevel=0.03)
        back_hi = ctr + up * 1.6 - nrm * 0.16 + Vector((sx * 1.8, 0, 0))
        back_lo = ctr - up * 1.8 - nrm * 0.16 + Vector((sx * 2.2, 0, 0))
        base = Vector((sx * 0.6, RY.y + 0.6, SZ + 1.0))
        rod(P + "_truss%d" % sx, base, back_hi, 0.07, "M_Plain", GR, seg=8)
        rod(P + "_truss2%d" % sx, base, back_lo, 0.06, "M_Plain", GR, seg=8)
        rod(P + "_truss3%d" % sx, back_hi, back_lo, 0.04, "M_Plain", GR, seg=6)
    for k in range(3):
        bx = ctr - nrm * 0.33 + Vector((-1.6 + 1.6 * k, 0, 0)) - up * 0.4
        abox("%s_ebox%d" % (P, k), (0.9, 0.3, 1.2), tuple(bx), "M_Body", GR, rot=rot, bevel=0.03)
    rod(P + "_wg", Vector((0, RY.y + 0.25, SZ + 1.03)), ctr - nrm * 0.16 - up * 1.5, 0.08, "M_Steel", GR, seg=8)
    # foot hinges on the pedestal
    for sx in (1, -1):
        rod(P + "_foot%d" % sx, Vector((sx * 0.55, RY.y - 0.1, SZ + 1.0)), bot + Vector((sx * 0.9, 0.02, 0.06)), 0.08, "M_Plain", GR, seg=8)

# ---------------------------------------------------------------- hierarchy + export
rt = root("root")
rig_maz(P, ch, rt)
pit = pivot("launcher_pitch", tuple(PIV), rt)
if VARIANT == "s400":
    group_under(GP, "launcher_pitch_geo", pit)
    for c, g, p in cans:
        n = pivot("canister_%d" % c, tuple(p), pit)
        group_under(g, "canister_%d_geo" % c, n)
    for c, g, p in lids:
        n = pivot("lid_%d" % c, tuple(p), pit)
        group_under(g, "lid_%d_geo" % c, n)
    rw_export("/home/user/models/rw_s400.glb", "/home/user/models/rw_s400.blend", "RW_S400")
else:
    ry = pivot("radar_yaw", tuple(RY), rt)
    group_under(GR, "radar_yaw_geo", ry)
    rw_export("/home/user/models/rw_radar_mobile.glb", "/home/user/models/rw_radar_mobile.blend", "RW_RadarMobile")
