# FPV kamikaze quadcopters: 7" with a PG-7VL anti-tank warhead, 10" with a TM-62M anti-tank mine.
# Origin = frame centre. Nose (camera) towards -Y. Props are separate nodes FPVx_Prop1..4 (spin around local Z).
reset_scene()
col_get("BUILD"); col_get("RIG")
ars_common_mats()
amat("M_PG7", None, 0.5, 0.3, factor=(0.24, 0.27, 0.20, 1))
amat("M_TM62", None, 0.7, 0.1, factor=(0.26, 0.31, 0.20, 1))
amat("M_Battery", None, 0.5, 0.0, factor=(0.10, 0.10, 0.12, 1))
amat("M_BattLabel", None, 0.5, 0.0, factor=(0.75, 0.12, 0.10, 1))
amat("M_PropCl", None, 0.3, 0.0, factor=(0.20, 0.22, 0.25, 1), glass_alpha=0.85)
amat("M_Motor", None, 0.35, 0.9, factor=(0.10, 0.12, 0.16, 1))
amat("M_Strap", None, 0.8, 0.0, factor=(0.08, 0.08, 0.08, 1))

QUADS = {
    "FPV7": dict(arm=0.165, prop=0.089, plate=(0.075, 0.13), motor=(0.0145, 0.022), batt=(0.045, 0.16, 0.045), load="PG7"),
    "FPV10": dict(arm=0.235, prop=0.127, plate=(0.095, 0.17), motor=(0.020, 0.028), batt=(0.060, 0.19, 0.055), load="TM62"),
}
INFO = {}
for P, s in QUADS.items():
    g = P + "_body"
    A = s["arm"]
    pw, pl = s["plate"]
    th = 0.005
    # carbon bottom + top plates, standoffs
    abox(P + "_plateB", (pw, pl, th), (0, 0, 0), "M_Carbon", g, bevel=0.003, dirt=False)
    abox(P + "_plateT", (pw * 0.85, pl * 0.75, th * 0.6), (0, 0.005, 0.032), "M_Carbon", g, bevel=0.002, dirt=False)
    for sx in (1, -1):
        for sy in (1, -1):
            acyl(P + "_stand%d%d" % (sx, sy), 0.003, 0.030, (sx * pw * 0.36, sy * pl * 0.32, 0.017), "M_Metal", g, seg=6)
    # stack (FC + ESC boards)
    abox(P + "_esc", (0.032, 0.032, 0.004), (0, 0.01, 0.010), "M_PCB", g, bevel=0)
    abox(P + "_fc", (0.030, 0.030, 0.003), (0, 0.01, 0.019), "M_PCB", g, bevel=0)
    abox(P + "_cap", (0.008, 0.02, 0.008), (0.022, 0.03, 0.010), "M_Black", g, bevel=0.002)
    motors = []
    for k, ang in enumerate((45, 135, 225, 315)):
        a = math.radians(ang)
        dv = Vector((math.cos(a), math.sin(a), 0))
        # arm: tapered carbon bar
        mid = dv * (A / 2)
        abox(P + "_arm%d" % k, (A * 1.05, 0.016 if P == "FPV7" else 0.022, 0.006), mid, "M_Carbon", g, rot=(0, 0, a), bevel=0.002, dirt=False)
        mp = dv * A
        mr, mh = s["motor"]
        acyl(P + "_mbase%d" % k, mr * 1.05, 0.006, mp + Vector((0, 0, 0.006)), "M_Motor", g, seg=16)
        acyl(P + "_mbell%d" % k, mr, mh, mp + Vector((0, 0, 0.009 + mh / 2)), "M_Motor", g, seg=20)
        for w in range(3):  # bell windows
            wa = w * 2 * PI / 3
            abox(P + "_mwin%d_%d" % (k, w), (0.004, 0.004, mh * 0.5), mp + Vector((math.cos(wa) * mr, math.sin(wa) * mr, 0.009 + mh * 0.5)),
                 "M_Copper", g, bevel=0)
        motors.append(mp + Vector((0, 0, 0.009 + mh + 0.004)))
        # ESC wires along the arm
        rod(P + "_wire%d" % k, dv * 0.03 + Vector((0, 0, 0.006)), dv * (A - mr * 1.2) + Vector((0, 0, 0.006)), 0.0025, "M_Copper" if k % 2 else "M_Black", g, seg=4)
    # battery on top with strap and XT60 lead
    bw, bl, bh = s["batt"]
    abox(P + "_batt", (bw, bl, bh), (0, 0.01, 0.035 + bh / 2), "M_Battery", g, bevel=0.004)
    abox(P + "_blabel", (bw + 0.001, bl * 0.6, bh * 0.4), (0, 0.01, 0.035 + bh / 2), "M_BattLabel", g, bevel=0.001)
    abox(P + "_bstrap", (bw + 0.006, 0.02, bh + 0.006), (0, 0.0, 0.035 + bh / 2), "M_Strap", g, bevel=0.002)
    rod(P + "_lead1", (0.01, 0.01 + bl / 2, 0.04), (0.01, 0.01 + bl / 2 + 0.03, 0.03), 0.003, "M_NavRed", g, seg=4)
    rod(P + "_lead2", (-0.01, 0.01 + bl / 2, 0.04), (-0.01, 0.01 + bl / 2 + 0.03, 0.03), 0.003, "M_Black", g, seg=4)
    # FPV camera (front, tilted up 30 deg) in a TPU mount, VTX antenna (rear), RX antennas
    cy = -pl / 2 + 0.005
    abox(P + "_cammount", (0.032, 0.010, 0.026), (0, cy, 0.017), "M_Tape", g, bevel=0.002)
    abox(P + "_cam", (0.019, 0.019, 0.019), (0, cy - 0.006, 0.018), "M_Black", g, rot=(math.radians(30), 0, 0), bevel=0.002)
    acyl(P + "_camlens", 0.007, 0.012, (0, cy - 0.019, 0.022), "M_Lens", g, axis=1, rot=(PI / 2 + math.radians(30), 0, 0), seg=12)
    rod(P + "_vtxant", (0, pl / 2 - 0.01, 0.03), (0, pl / 2 + 0.02, 0.09), 0.004, "M_Black", g, seg=6)
    bm = bm_sphere(0.009, (0, pl / 2 + 0.022, 0.095), 10, 5)
    aobj(P + "_vtxcap", bm, "M_Black", g)
    for sx in (1, -1):
        rod(P + "_rxant%d" % sx, (sx * 0.012, pl / 2 - 0.02, 0.02), (sx * 0.06, pl / 2 + 0.03, 0.03), 0.0015, "M_Black", g, seg=4)
    # payload under the frame
    if s["load"] == "PG7":
        # PG-7VL: copper liner cone + body (93 mm) + piezo nose fuze; strapped along Y, nose forward (-Y)
        c0 = Vector((0, -0.24, -0.06))
        lathe_y(P + "_pg_head", [(0, 0.0), (0.012, 0.012), (0.03, 0.018), (0.15, 0.040), (0.25, 0.0465), (0.30, 0.0465), (0.33, 0.035),
                                 (0.40, 0.020), (0.52, 0.020), (0.52, 0.0)], c0, ["M_PG7"], g, seg=20)
        acyl(P + "_pg_fuze", 0.010, 0.02, c0 + Vector((0, -0.005, 0)), "M_Metal", g, axis=1, seg=10)
        lathe_y(P + "_pg_band", [(0, 0.047), (0.03, 0.047)], c0 + Vector((0, 0.25, 0)), ["M_Black"], g, seg=20)
        for k, yy in enumerate((0.05, -0.05)):
            lathe_y(P + "_pg_tape%d" % k, [(0, 0.024), (0.018, 0.024)], Vector((0, yy, -0.06)), ["M_Tape"], g, seg=16)
            abox(P + "_pg_tie%d" % k, (0.008, 0.016, 0.06), (0, yy + 0.009, -0.03), "M_Tape", g, bevel=0.001)
        # detonator lead (impact switch) to FC
        rod(P + "_pg_det", (0, 0.24, -0.06), (0, 0.06, 0.005), 0.002, "M_NavRed", g, seg=4)
    else:
        # TM-62M: 320 mm steel mine body, MVCh-62 fuze well on top; hung under the frame upside-down with the fuze facing down
        c0 = Vector((0, 0.0, -0.11))
        lathe_y(P + "_tm_body", [(-0.055, 0.0), (-0.055, 0.145), (-0.050, 0.160), (0.045, 0.160), (0.055, 0.150), (0.055, 0.0)], c0,
                ["M_TM62"], g, seg=32, axis='Z')
        lathe_y(P + "_tm_rim", [(-0.012, 0.162), (0.012, 0.162)], c0, ["M_TM62"], g, seg=32, axis='Z')
        lathe_y(P + "_tm_fuze", [(-0.10, 0.0), (-0.10, 0.04), (-0.055, 0.046), (-0.055, 0.0)], c0, ["M_Olive"], g, seg=20, axis='Z')
        acyl(P + "_tm_pin", 0.006, 0.03, c0 + Vector((0.035, 0, -0.085)), "M_Metal", g, seg=8)
        for k, xx in enumerate((0.07, -0.07)):
            rod(P + "_tm_strap%d" % k, Vector((xx, -0.14, -0.05)), Vector((xx, 0.14, -0.05)), 0.006, "M_Strap", g, seg=6)
            rod(P + "_tm_hang%d" % k, Vector((xx, -0.06, -0.05)), Vector((xx * 0.5, -0.04, -0.002)), 0.005, "M_Strap", g, seg=6)
            rod(P + "_tm_hangb%d" % k, Vector((xx, 0.06, -0.05)), Vector((xx * 0.5, 0.04, -0.002)), 0.005, "M_Strap", g, seg=6)
        rod(P + "_tm_det", (0.02, 0.02, -0.05), (0.01, 0.03, 0.005), 0.002, "M_NavRed", g, seg=4)
        lathe_y(P + "_tm_band", [(-0.02, 0.161), (0.0, 0.161)], c0 + Vector((0, 0, 0.03)), ["M_Black"], g, seg=32, axis='Z')
    INFO[P] = dict(motors=[tuple(round(c, 4) for c in m) for m in motors], prop=s["prop"])
    # props (tri-blade), each in its own group
    for k, mp in enumerate(motors):
        gp = P + "_prop%d" % k
        cw = 1 if k % 2 == 0 else -1
        prop_blades(P + "_pr%d" % k, mp, s["prop"], 3, ["M_PropCl"], gp, axis=(0, 0, cw), chord=s["prop"] * 0.30, r_hub=0.010,
                    pitch_root=28, pitch_tip=12, t=0.08, n=4)
        acyl(P + "_pnut%d" % k, 0.006, 0.010, mp + Vector((0, 0, 0.006)), "M_Metal", gp, seg=8)

weather(gen_weather((), 0.1))
for P in QUADS:
    rt = root(P)
    group_under(P + "_body", P + "_geo", rt)
    for k, mp in enumerate(INFO[P]["motors"]):
        pv = pivot(P + "_Prop%d" % (k + 1), mp, rt)
        group_under(P + "_prop%d" % k, P + "_prop%d_geo" % (k + 1), pv)
    pivot(P + "_Cam", (0, -QUADS[P]["plate"][1] / 2 - 0.02, 0.022), rt)
finish_nla()
bpy.context.view_layer.update()
print("INFO", INFO)
print("FPV", stats())
print("GLB", export_glb("/home/user/models/fpv.glb"))
print("BLEND", save_blend("/home/user/models/fpv.blend", "FPV"))
