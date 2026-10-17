# Recon / strike MALE drones: MQ-9 Reaper and Orion (Inokhodets). Origin = centre of the airframe (flight entity pivot).
reset_scene()
col_get("BUILD"); col_get("RIG")
ars_common_mats()
amat("M_Reaper", "T_SkinReaper", 0.5, 0.1)
amat("M_Orion", "T_SkinOrion", 0.5, 0.1)
amat("M_Hellfire", None, 0.5, 0.2, factor=(0.30, 0.33, 0.26, 1))
amat("M_KAB", None, 0.5, 0.2, factor=(0.33, 0.36, 0.30, 1))
amat("M_Window", None, 0.05, 0.3, factor=(0.04, 0.06, 0.08, 1), glass_alpha=0.85)

SPECS = {
    # length, span, body ctrl (t, zc, hw, ht, hb, n)
    "MQ9": dict(L=11.0, span=20.1, skin="M_Reaper", prop_r=1.15, blades=3, wing_z=0.22, wing_le=-0.95, rc=1.45, tc=0.55,
                ctrl=[(0.0, 0.02, 0.0, 0.0, 0.0, 2.2), (0.015, 0.05, 0.16, 0.17, 0.14, 2.2), (0.06, 0.20, 0.38, 0.40, 0.26, 2.2),
                      (0.13, 0.30, 0.50, 0.52, 0.30, 2.3), (0.22, 0.28, 0.50, 0.48, 0.34, 2.3), (0.36, 0.16, 0.45, 0.38, 0.38, 2.3),
                      (0.55, 0.10, 0.39, 0.32, 0.34, 2.3), (0.75, 0.10, 0.31, 0.26, 0.27, 2.2), (0.90, 0.10, 0.24, 0.20, 0.20, 2.1),
                      (0.965, 0.10, 0.15, 0.13, 0.13, 2.0), (1.0, 0.10, 0.12, 0.10, 0.10, 2.0)]),
    "ORION": dict(L=8.0, span=16.3, skin="M_Orion", prop_r=0.95, blades=3, wing_z=0.42, wing_le=-0.55, rc=1.15, tc=0.48,
                  ctrl=[(0.0, 0.05, 0.0, 0.0, 0.0, 2.0), (0.02, 0.06, 0.14, 0.14, 0.13, 2.0), (0.08, 0.10, 0.34, 0.34, 0.30, 2.1),
                        (0.18, 0.12, 0.42, 0.44, 0.36, 2.2), (0.40, 0.12, 0.42, 0.44, 0.36, 2.2), (0.62, 0.12, 0.36, 0.38, 0.32, 2.2),
                        (0.82, 0.12, 0.27, 0.27, 0.24, 2.1), (0.95, 0.12, 0.16, 0.15, 0.14, 2.0), (1.0, 0.12, 0.12, 0.10, 0.10, 2.0)]),
}
ROOTS = {}
SOOT = []
for P, s in SPECS.items():
    L, y0 = s["L"], -s["L"] / 2
    g = P + "_body"
    prof = ctrl_prof(s["ctrl"], L, y0)
    body = loft(P + "_fus", stations(y0, L, 48), prof, [s["skin"]], g, seg=40)
    # wing (high aspect ratio, slight dihedral), tips and control surfaces lines are part of the skin texture
    half = s["span"] / 2
    for side in (1, -1):
        secs = wing_secs(0.25, s["wing_le"], s["wing_z"], s["rc"], half, s["wing_le"] + 0.35, s["wing_z"] + 0.45, s["tc"],
                         t_root=0.16, t_tip=0.11, n=8, twist_tip=-2.0, cam=0.03, side=side)
        surface(P + "_wing%d" % side, secs, [s["skin"]], g, nc=14, cap_root=True)
        tip, tc_ = wing_at(secs, 1.0, 0.1)
        lamp(P + "_nav%d" % side, tip + Vector((0.03 * side, 0, 0)), "M_NavRed" if side < 0 else "M_NavGreen", g, 0.035)
        # pylons (2 per side) + stores
        for k, fx in enumerate((0.28, 0.45) if P == "MQ9" else (0.22, 0.36)):
            p, c = wing_at(secs, fx, 0.25)
            gs = P + "_store%d" % ((0 if side > 0 else 2) + k)
            abox(P + "_pyl%d_%d" % (side, k), (0.07, 0.55, 0.16), p + Vector((0, 0.15, -0.14)), "M_Dark", g, bevel=0.01)
            if P == "MQ9":   # M299 rail with 2 x AGM-114 Hellfire
                abox(P + "_rail%d_%d" % (side, k), (0.30, 1.1, 0.06), p + Vector((0, 0.25, -0.26)), "M_Dark", g, bevel=0.01)
                for j in (-1, 1):
                    c0 = p + Vector((0.09 * j, -0.55, -0.36))
                    lathe_y(P + "_hf%d_%d_%d" % (side, k, j), [(0, 0.0), (0.04, 0.05), (0.12, 0.08), (0.16, 0.0889), (1.63, 0.0889),
                                                              (1.63, 0.0)], c0, ["M_Hellfire"], gs, seg=14)
                    for q in range(4):
                        a = PI / 4 + q * PI / 2
                        dv = Vector((math.cos(a), 0, math.sin(a)))
                        abox(P + "_hffin%d_%d_%d_%d" % (side, k, j, q), (0.09, 0.18, 0.005), c0 + Vector((0, 1.45, 0)) + dv * 0.12,
                             "M_Hellfire", gs, rot=(0, -a, 0), bevel=0)
                        abox(P + "_hfw%d_%d_%d_%d" % (side, k, j, q), (0.06, 0.10, 0.005), c0 + Vector((0, 0.45, 0)) + dv * 0.11,
                             "M_Hellfire", gs, rot=(0, -a, 0), bevel=0)
            else:            # KAB-50 guided bombs
                c0 = p + Vector((0, -0.40, -0.40))
                lathe_y(P + "_kab%d_%d" % (side, k), [(0, 0.0), (0.06, 0.07), (0.2, 0.12), (0.9, 0.12), (1.15, 0.08), (1.2, 0.0)],
                        c0, ["M_KAB"], gs, seg=16)
                for q in range(4):
                    a = PI / 4 + q * PI / 2
                    dv = Vector((math.cos(a), 0, math.sin(a)))
                    abox(P + "_kfin%d_%d_%d" % (side, k, q), (0.12, 0.25, 0.006), c0 + Vector((0, 1.0, 0)) + dv * 0.16,
                         "M_KAB", gs, rot=(0, -a, 0), bevel=0)
    # tail
    ty = y0 + L * 0.83
    if P == "MQ9":   # V-tail up (45 deg) + ventral fin protecting the pusher prop
        for side in (1, -1):
            a = (0.12 * side, ty, 0.22)
            b = (1.95 * side, ty + 0.75, 1.75)
            panel(P + "_vt%d" % side, a, b, 1.25, 0.55, plane_normal(a, b), [s["skin"]], g, t_root=0.12, t_tip=0.09)
        a, b = (0, ty - 0.1, -0.12), (0, ty + 0.55, -1.12)
        panel(P + "_vf", a, b, 1.15, 0.60, (1, 0, 0), [s["skin"]], g, t_root=0.12, t_tip=0.10)
        # satcom bulge cap, air intake scoops, exhaust stubs (Honeywell TPE331 turboprop)
        bm = bm_sphere(0.30, (0, y0 + 1.25, 0.55), 18, 9, (1.3, 2.4, 0.55))
        aobj(P + "_satcom", bm, s["skin"], g)
        for side in (1, -1):
            lathe_y(P + "_intake%d" % side, [(0, 0.10), (0.35, 0.12), (0.5, 0.06)], (0.27 * side, y0 + L * 0.60, 0.30), ["M_Dark"], g, seg=12)
            ex = Vector((0.24 * side, y0 + L * 0.88, 0.24))
            rod(P + "_exh%d" % side, ex, ex + Vector((0.12 * side, 0.18, 0.03)), 0.05, "M_Nozzle", g, seg=10)
            SOOT.append(ex + Vector((0, 0.5, 0)))
        abox(P + "_oilcool", (0.30, 0.6, 0.14), (0, y0 + L * 0.66, -0.30), "M_Dark", g, bevel=0.02)
        # antennas
        abox(P + "_ant1", (0.01, 0.12, 0.25), (0, y0 + L * 0.40, 0.65), "M_Dark", g, bevel=0)
        abox(P + "_ant2", (0.01, 0.10, 0.18), (0, y0 + L * 0.55, -0.42), "M_Dark", g, bevel=0)
    else:            # Orion: V-tail with large dihedral, flat rear deck
        for side in (1, -1):
            a = (0.10 * side, ty, 0.30)
            b = (1.70 * side, ty + 0.65, 1.45)
            panel(P + "_vt%d" % side, a, b, 1.05, 0.48, plane_normal(a, b), [s["skin"]], g, t_root=0.12, t_tip=0.09)
        for side in (1, -1):
            lathe_y(P + "_scoop%d" % side, [(0, 0.08), (0.30, 0.09), (0.42, 0.04)], (0.30 * side, y0 + L * 0.58, 0.40), ["M_Dark"], g, seg=12)
            ex = Vector((0.20 * side, y0 + L * 0.86, 0.18))
            rod(P + "_exh%d" % side, ex, ex + Vector((0.08 * side, 0.12, 0.0)), 0.035, "M_Nozzle", g, seg=10)
            SOOT.append(ex + Vector((0, 0.3, 0)))
        abox(P + "_ant1", (0.01, 0.12, 0.22), (0, y0 + L * 0.30, 0.62), "M_Dark", g, bevel=0)
        bm = bm_sphere(0.22, (0, y0 + L * 0.22, 0.55), 16, 8, (1.0, 2.2, 0.5))
        aobj(P + "_satdome", bm, s["skin"], g)
    lamp(P + "_strobe", (0, y0 + L * 0.5, s["ctrl"][4][1] + s["ctrl"][4][3] + 0.03), "M_Strobe", g, 0.03)
    # nose: sensor window (camera bay) and pitot
    bm = bm_sphere(0.13, (0, y0 + 0.25, -0.02), 12, 6, (1.0, 0.6, 0.8))
    aobj(P + "_noseglass", bm, "M_Window", g, dirt=False)
    rod(P + "_pitot", (0.0, y0 + 0.05, 0.05), (0.0, y0 - 0.45, 0.05), 0.012, "M_Metal", g, seg=6)
    # decals (projected on the fuselage)
    bpy.context.view_layer.update()
    for side in (1, -1):
        nrm = (side, 0, 0)
        up = (0, 0, 1)
        if P == "MQ9":
            decal_on(P + "_d_usaf%d" % side, body, (0.3 * side, y0 + L * 0.66, 0.12), nrm, up, 0.75, "usaf", g)
            decal_on(P + "_d_txt%d" % side, body, (0.4 * side, y0 + L * 0.35, 0.18), nrm, up, 1.5, "usaf_txt", g)
            decal_on(P + "_d_num%d" % side, body, (0.3 * side, y0 + L * 0.78, 0.10), nrm, up, 0.9, "af_num", g)
        else:
            decal_on(P + "_d_star%d" % side, body, (0.4 * side, y0 + L * 0.70, 0.10), nrm, up, 0.34, "star_ru", g)
            decal_on(P + "_d_num%d" % side, body, (0.4 * side, y0 + L * 0.30, 0.15), nrm, up, 0.45, "orion_num", g)
            decal_on(P + "_d_flag%d" % side, body, (0.3 * side, y0 + L * 0.80, 0.22), nrm, up, 0.45, "flag_ru", g)
    # ---------------------------------------------------------------- separately animated parts
    # gimbal ball (EO/IR turret) under the nose: yaw node + pitch node
    by = y0 + L * (0.13 if P == "MQ9" else 0.16)
    bz = -(0.30 if P == "MQ9" else 0.34)
    gb = P + "_ballyaw"
    acyl(P + "_ballmount", 0.12, 0.12, (0, by, bz + 0.06), "M_Dark", gb, axis=2, seg=16)
    gp = P + "_ballpitch"
    bm = bm_sphere(0.21 if P == "MQ9" else 0.18, (0, by, bz - 0.17), 18, 9)
    aobj(P + "_ball", bm, "M_Dark", gp)
    for k, (dx, rr) in enumerate(((-0.06, 0.055), (0.05, 0.04), (0.0, 0.025))):
        acyl(P + "_lens%d" % k, rr, 0.03, (dx, by - 0.19, bz - 0.17 + (0.05 if k == 2 else 0)), "M_Lens", gp, axis=1, seg=14)
    # pusher propeller
    py = y0 + L + 0.08
    pz = s["ctrl"][-1][1]
    gpr = P + "_propg"
    prop_blades(P + "_prop", (0, py + 0.06, pz), s["prop_r"], s["blades"], ["M_Prop"], gpr, chord=0.16, r_hub=0.10)
    spinner(P + "_spin", (0, py - 0.02, pz), 0.12, 0.35, "M_Prop", gpr, direction=1)
    # landing gear (retractable tricycle): nose leg folds forward, mains fold inward
    gn = P + "_gearN"
    ny_ = y0 + L * 0.12
    hb = s["ctrl"][3][4]
    nz0 = s["ctrl"][3][1] - hb + 0.05
    rod(P + "_nleg", (0, ny_, nz0), (0, ny_ - 0.05, nz0 - 0.70), 0.035, "M_Metal", gn, seg=8)
    wheel(P + "_nwheel", (0, ny_ - 0.05, nz0 - 0.78), 0.15, 0.10, ["M_Tire", "M_Metal"], gn)
    mains = {}
    for side in (1, -1):
        gm = P + "_gear%s" % ("L" if side > 0 else "R")
        my = y0 + L * 0.47
        mz0 = -0.20 if P == "MQ9" else -0.22
        rod(P + "_mleg%d" % side, (0.25 * side, my, mz0), (1.10 * side, my + 0.05, mz0 - 0.62), 0.04, "M_Metal", gm, seg=8)
        wheel(P + "_mwheel%d" % side, (1.18 * side, my + 0.05, mz0 - 0.70), 0.20, 0.11, ["M_Tire", "M_Metal"], gm)
        mains[side] = (gm, Vector((0.25 * side, my, mz0)))
    ROOTS[P] = dict(by=by, bz=bz, py=py, pz=pz, ny=ny_, nz0=nz0, mains=mains)

weather(gen_weather(SOOT, 0.7))

for P, s in SPECS.items():
    d = ROOTS[P]
    rt = root(P)
    group_under(P + "_body", P + "_geo", rt)
    for n in range(4):
        st_ = pivot(P + "_Store%d" % n, (0, 0, 0), rt)
        group_under(P + "_store%d" % n, P + "_store%d_geo" % n, st_)
    yaw = pivot(P + "_BallYaw", (0, d["by"], d["bz"] + 0.06), rt)
    group_under(P + "_ballyaw", P + "_ballmount_geo", yaw)
    pit = pivot(P + "_BallPitch", (0, d["by"], d["bz"] - 0.17), yaw)
    group_under(P + "_ballpitch", P + "_ball_geo", pit)
    pr = pivot(P + "_Prop", (0, d["py"], d["pz"]), rt)
    group_under(P + "_propg", P + "_prop_geo", pr)
    hn = hinge(P + "_GearN", (0.0, d["ny"], d["nz0"]), (1.0, d["ny"], d["nz0"]), parent=rt)
    group_under(P + "_gearN", P + "_gearN_geo", hn)
    aclip(P.lower() + "_gear", hn, "rotation_euler", [(0, 0.0), (24, -1.75), (48, -1.75)], index=0)
    for side, (gm, pv) in d["mains"].items():
        hm = hinge(P + "_Gear%s" % ("L" if side > 0 else "R"), pv, pv + Vector((1, 0, 0)), parent=rt)
        group_under(gm, gm + "_geo", hm)
        aclip(P.lower() + "_gear", hm, "rotation_euler", [(0, 0.0), (24, 1.40), (48, 1.40)], index=0)
finish_nla()
bpy.context.view_layer.update()
print("RECON", stats())
print("GLB", export_glb("/home/user/models/recon.glb"))
print("BLEND", save_blend("/home/user/models/recon.blend", "Recon"))
