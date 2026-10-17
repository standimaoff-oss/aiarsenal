# Cruise missiles: Kh-101, Kh-55SM (air-launched), 3M14 Kalibr, BGM-109 Tomahawk (ground launched, solid boosters),
# plus a 4-tube ground launcher (GLCM-style box on a stand, elevates 0 -> 45 deg).
# Missiles: origin = centre of the cruise body, nose -Y. Rest pose = STOWED (wings/fins folded, engine pod retracted).
# Clips per missile id (lowercase): <id>_deploy (24 frames: wings + fins unfold, engine/intake drops). Launcher: launcher_erect, launcher_lids.
reset_scene()
col_get("BUILD"); col_get("RIG")
ars_common_mats()
amat("M_MisDark", "T_SkinMisDark", 0.55, 0.15)
amat("M_MisLight", "T_SkinMisLight", 0.5, 0.15)
amat("M_MisWhite", "T_SkinMisLight", 0.5, 0.15, factor=(1.05, 1.05, 1.05, 1))
amat("M_Radome", None, 0.4, 0.0, factor=(0.20, 0.21, 0.20, 1))
amat("M_RadomeW", None, 0.4, 0.0, factor=(0.80, 0.80, 0.76, 1))
amat("M_BoosterK", None, 0.5, 0.3, factor=(0.28, 0.30, 0.27, 1))
amat("M_BoosterT", None, 0.5, 0.3, factor=(0.55, 0.42, 0.20, 1))
amat("M_Camo", "T_CamoGreen", 0.8, 0.1)
amat("M_CamoSand", "T_CamoSand", 0.8, 0.1)
amat("M_TubeIn", None, 0.7, 0.2, factor=(0.08, 0.08, 0.08, 1))

MIS = {
    "KH101": dict(L=7.45, r=0.37, n=1.45, skin="M_MisDark", nose="M_Radome", span=3.0, wc=0.55, wt=0.32, wing_t=0.40, wz=0.20,
                  tail="kh101", pod="ventral", dec="kh101"),
    "KH55": dict(L=6.04, r=0.257, n=2.0, skin="M_MisWhite", nose="M_RadomeW", span=3.10, wc=0.42, wt=0.26, wing_t=0.42, wz=0.0,
                 tail="kh55", pod="ventral", dec="kh55"),
    "KALIBR": dict(L=6.20, r=0.2665, n=2.0, skin="M_MisLight", nose="M_Radome", span=3.10, wc=0.40, wt=0.25, wing_t=0.46, wz=-0.05,
                   tail="x", pod="intake", dec="kalibr", booster=dict(L=2.0, mat="M_BoosterK")),
    "TOMAHAWK": dict(L=5.56, r=0.259, n=2.0, skin="M_MisLight", nose="M_MisLight", span=2.67, wc=0.38, wt=0.24, wing_t=0.42, wz=0.0,
                     tail="x", pod="intake", dec="tomahawk", booster=dict(L=0.70, mat="M_BoosterT")),
}
ANIMS = []   # (clip, hinge empty, path, keys, index)
STOW = []    # (empty, attr, index, value) applied after the clips are recorded = rest pose
INFO = {}
for P, s in MIS.items():
    L, r, y0 = s["L"], s["r"], -s["L"] / 2
    g = P + "_body"
    nose_len = {"KH101": 0.12, "KH55": 0.14, "KALIBR": 0.12, "TOMAHAWK": 0.10}[P]
    ctrl = [(0.0, 0.0, 0.0, 0.0, 0.0, s["n"]), (0.01, 0.0, r * 0.28, r * 0.28, r * 0.28, s["n"]),
            (nose_len * 0.45, 0.0, r * 0.80, r * 0.80, r * 0.80, s["n"]), (nose_len, 0.0, r, r, r, s["n"]),
            (0.88, 0.0, r, r, r, s["n"]), (0.96, 0.0, r * 0.86, r * 0.86, r * 0.86, s["n"]), (1.0, 0.0, r * 0.62, r * 0.62, r * 0.62, s["n"])]
    if P == "KH101":   # flat-bottomed, faceted stealth section, boat-tail
        ctrl = [(0.0, -0.05, 0.0, 0.0, 0.0, 1.3), (0.01, -0.05, r * 0.30, r * 0.20, r * 0.16, 1.3), (0.06, -0.03, r * 0.80, r * 0.62, r * 0.45, 1.35),
                (0.14, 0.0, r, r * 0.80, r * 0.60, 1.4), (0.85, 0.0, r, r * 0.80, r * 0.60, 1.4), (0.95, 0.0, r * 0.80, r * 0.62, r * 0.48, 1.4),
                (1.0, 0.0, r * 0.50, r * 0.42, r * 0.34, 1.4)]
    nose_y = y0 + L * nose_len * 0.9

    def mf(ym, am, zm, ny=nose_y):
        return 1 if ym < ny else 0
    body = loft(P + "_fus", stations(y0, L, 44), ctrl_prof(ctrl, L, y0), [s["skin"], s["nose"]], g, seg=36, mat_fn=mf)
    lathe_y(P + "_seam1", [(0, r * 1.002), (0.01, r * 1.006), (0.02, r * 1.002)], (0, y0 + L * 0.35, 0), ["M_Black"], g, seg=36) if P != "KH101" else None
    # wing slot fairing and pivot
    wy = y0 + L * s["wing_t"]
    abox(P + "_wslot", (0.06, s["wc"] * 0.9, 0.012), (0, wy + s["wc"] * 0.45, (r if P != "KH101" else r * 0.8) + 0.003), "M_Black", g, bevel=0)
    # ---- folding wings (built deployed, then stowed by rotating around the vertical pivot)
    for side in (1, -1):
        gw = P + "_wing%d" % side
        zz = s["wz"] + 0.012 * side
        root_le = (r * 0.2 * side, wy, zz)
        tip_le = (s["span"] / 2 * side, wy + (s["wc"] - s["wt"]) * 0.8, zz)
        panel(P + "_w%d" % side, root_le, tip_le, s["wc"], s["wt"], (0, 0, 1), [s["skin"]], gw, t_root=0.07, t_tip=0.06, n=4)
        INFO.setdefault(P, {})["wing%d" % side] = (Vector((r * 0.25 * side, wy + s["wc"] * 0.25, zz)), side)
    # ---- tail
    ty = y0 + L * 0.90
    fins = []
    if s["tail"] in ("kh55", "kh101"):
        # 2 horizontal stabilisers + 1 vertical fin (Kh-101: twin canted fins), folding about the body axis
        hs = 0.62 if P == "KH55" else 0.70
        for side in (1, -1):
            fins.append(((r * 0.6 * side, ty, 0.0), (hs * side, ty + 0.16, 0.0), 0.36, 0.20, (0, 0, 1), -1.45 * side))
        if P == "KH55":
            fins.append(((0, ty, r * 0.6), (0, ty + 0.20, 0.62), 0.38, 0.20, (1, 0, 0), 1.45))
        else:
            for side in (1, -1):
                fins.append(((0.12 * side, ty, r * 0.55), (0.42 * side, ty + 0.22, 0.62), 0.40, 0.20,
                             tuple(plane_normal((0.12 * side, ty, r * 0.55), (0.42 * side, ty + 0.22, 0.62))), 1.0 * side))
    else:
        # cruciform X tail
        for k in range(4):
            a = PI / 4 + k * PI / 2
            dv = Vector((math.cos(a), 0, math.sin(a)))
            p0 = Vector((0, ty, 0)) + dv * (r * 0.75)
            p1 = Vector((0, ty + 0.16, 0)) + dv * (r + 0.30)
            nrm = dv.cross(Vector((0, 1, 0))).normalized()
            fins.append((tuple(p0), tuple(p1), 0.34, 0.20, tuple(nrm), 1.3))
    for k, (a, b, rc, tc, ud, fold) in enumerate(fins):
        gf = P + "_fin%d" % k
        panel(P + "_f%d" % k, a, b, rc, tc, ud, [s["skin"]], gf, t_root=0.08, t_tip=0.06, n=2)
        INFO[P]["fin%d" % k] = (Vector(a), fold)
    # ---- engine pod (Kh) or intake scoop (Kalibr/Tomahawk), retracted in the rest pose
    gp = P + "_pod"
    if s["pod"] == "ventral":
        py = y0 + L * 0.80
        drop = 0.42 if P == "KH55" else 0.36
        pl = 1.05
        lathe_y(P + "_podb", [(0, 0.10), (0.05, 0.155), (0.25, 0.17), (0.80, 0.165), (pl, 0.12)], (0, py, -r - drop + 0.02), [s["skin"]], gp, seg=20)
        lathe_y(P + "_podin", [(0, 0.075), (0.03, 0.10)], (0, py - 0.001, -r - drop + 0.02), ["M_Black"], gp, seg=20)
        lathe_y(P + "_podnoz", [(0, 0.115), (0.10, 0.09)], (0, py + pl, -r - drop + 0.02), ["M_Nozzle"], gp, seg=20)
        abox(P + "_podpyl", (0.05, 0.75, drop + 0.04), (0, py + 0.45, -r - drop / 2 + 0.03), s["skin"], gp, bevel=0.01)
        INFO[P]["pod"] = (py, drop)
    else:
        py = y0 + L * 0.66
        drop = 0.16
        bm = bm_box((0.22, 0.50, 0.16), (0, py, -r - drop / 2 + 0.05), (0, 0, 0), 0.02, 1)
        aobj(P + "_scoop", bm, s["skin"], gp, smooth=False, uv="box", uvscale=1.0)
        abox(P + "_scoopin", (0.18, 0.02, 0.12), (0, py - 0.25, -r - drop / 2 + 0.05), "M_Black", gp, bevel=0)
        INFO[P]["pod"] = (py, drop)
        acyl(P + "_noz", r * 0.45, 0.05, (0, y0 + L + 0.01, 0), "M_Nozzle", g, axis=1, seg=20)
    # ---- booster (Kalibr / Tomahawk), separate node, jettisoned after burnout
    if s.get("booster"):
        bL = s["booster"]["L"]
        gb = P + "_boost"
        lathe_y(P + "_bst", [(0, r * 0.98), (0.04, r), (bL - 0.12, r), (bL, r * 0.75), (bL + 0.06, r * 0.6)], (0, y0 + L + 0.005, 0),
                [s["booster"]["mat"]], gb, seg=28)
        lathe_y(P + "_bstnoz", [(0, r * 0.5), (0.12, r * 0.58)], (0, y0 + L + bL + 0.03, 0), ["M_Nozzle"], gb, seg=20)
        for k in range(4):
            a = k * PI / 2
            dv = Vector((math.cos(a), 0, math.sin(a)))
            a0 = Vector((0, y0 + L + bL - 0.35, 0)) + dv * r
            panel(P + "_bfin%d" % k, tuple(a0), tuple(a0 + dv * 0.18 + Vector((0, 0.12, 0))), 0.30, 0.16,
                  tuple(dv.cross(Vector((0, 1, 0))).normalized()), [s["booster"]["mat"]], gb, t_root=0.06, t_tip=0.05, n=1)
        INFO[P]["boost"] = (0, y0 + L, 0)
    # ---- decals
    bpy.context.view_layer.update()
    for side in (1, -1):
        decal_on(P + "_d_name%d" % side, body, (r * side, y0 + L * 0.55, 0.0), (side, 0, 0), (0, 0, 1), 0.55, s["dec"], g)
        decal_on(P + "_d_ser%d" % side, body, (r * side, y0 + L * 0.25, 0.0), (side, 0, 0), (0, 0, 1), 0.25, "ser_k", g)
    if P in ("KH101", "KH55"):
        decal_on(P + "_d_star", body, (0, y0 + L * 0.62, r), (0, 0, 1), (0, -1, 0), 0.30, "star_ru", g)
        decal_on(P + "_d_lift", body, (0, y0 + L * 0.45, r), (0, 0, 1), (0, -1, 0), 0.40, "lift", g)
    else:
        decal_on(P + "_d_band", body, (0, y0 + L * 0.15, r), (0, 0, 1), (1, 0, 0), 0.30, "band_k" if P == "KALIBR" else "band_b", g, h=0.04)
        if P == "TOMAHAWK":
            decal_on(P + "_d_usn", body, (0, y0 + L * 0.40, r), (0, 0, 1), (0, -1, 0), 0.6, "usn", g)

# ------------------------------------------------------------------------------------------------ small guided munitions (drones)
amat("M_Hellfire", None, 0.5, 0.2, factor=(0.30, 0.33, 0.26, 1))
amat("M_KAB", None, 0.5, 0.2, factor=(0.33, 0.36, 0.30, 1))
for P, L, r, mat in (("HELLFIRE", 1.63, 0.0889, "M_Hellfire"), ("KAB50", 1.20, 0.12, "M_KAB")):
    g = P + "_body"
    y0 = -L / 2
    if P == "HELLFIRE":
        lathe_y(P + "_b", [(0, 0.0), (0.04, 0.05), (0.12, 0.08), (0.16, r), (L, r), (L, 0.0)], (0, y0, 0), [mat], g, seg=16)
        acyl(P + "_seeker", 0.05, 0.02, (0, y0 + 0.01, 0), "M_Lens", g, axis=1, seg=12)
        for q in range(4):
            a = PI / 4 + q * PI / 2
            dv = Vector((math.cos(a), 0, math.sin(a)))
            abox(P + "_fin%d" % q, (0.09, 0.18, 0.005), Vector((0, y0 + L - 0.18, 0)) + dv * 0.12, mat, g, rot=(0, -a, 0), bevel=0)
            abox(P + "_wing%d" % q, (0.06, 0.10, 0.005), Vector((0, y0 + 0.45, 0)) + dv * 0.11, mat, g, rot=(0, -a, 0), bevel=0)
        lathe_y(P + "_noz", [(0, 0.05), (0.04, 0.06)], (0, y0 + L, 0), ["M_Nozzle"], g, seg=12)
    else:
        lathe_y(P + "_b", [(0, 0.0), (0.06, 0.07), (0.2, r), (0.9, r), (1.15, 0.08), (L, 0.0)], (0, y0, 0), [mat], g, seg=16)
        for q in range(4):
            a = PI / 4 + q * PI / 2
            dv = Vector((math.cos(a), 0, math.sin(a)))
            abox(P + "_fin%d" % q, (0.12, 0.25, 0.006), Vector((0, y0 + 1.0, 0)) + dv * 0.16, mat, g, rot=(0, -a, 0), bevel=0)
            abox(P + "_can%d" % q, (0.06, 0.10, 0.004), Vector((0, y0 + 0.25, 0)) + dv * 0.13, mat, g, rot=(0, -a, 0), bevel=0)

# ------------------------------------------------------------------------------------------------ ground launcher (4 tubes)
P = "LAUNCHER"
g = P + "_base"
abox(P + "_frame", (2.6, 9.0, 0.35), (0, 0.3, 0.75), "M_Camo", g, bevel=0.03)
for side in (1, -1):
    for k, yy in enumerate((-3.6, 0.3, 4.2)):
        abox(P + "_leg%d_%d" % (side, k), (0.20, 0.20, 0.75), (1.15 * side, yy, 0.375), "M_Steel", g, bevel=0.01)
        abox(P + "_pad%d_%d" % (side, k), (0.5, 0.5, 0.06), (1.15 * side, yy, 0.03), "M_Steel", g, bevel=0.01)
    abox(P + "_hinge%d" % side, (0.25, 0.5, 0.45), (0.95 * side, 4.2, 1.05), "M_Steel", g, bevel=0.02)
abox(P + "_genset", (1.2, 1.4, 1.1), (0, 5.5, 0.55 + 0.38), "M_Camo", g, bevel=0.03)
abox(P + "_cab", (1.2, 0.9, 0.9), (1.9, 4.5, 0.45), "M_Camo", g, bevel=0.03)
for k in range(3):
    acyl(P + "_lamp%d" % k, 0.03, 0.02, (1.9 + (k - 1) * 0.15, 4.04, 0.65), ["M_NavRed", "M_NavGreen", "M_Strobe"][k], g, axis=1, seg=8)
# elevating tube box: pivot at the rear (+Y) top of the frame
gt = P + "_box"
piv = Vector((0, 4.2, 1.05))
tube_len = 8.0
box_c = piv + Vector((0, -tube_len / 2, 0.80))
abox(P + "_shell", (1.75, tube_len, 1.55), box_c, "M_Camo", gt, bevel=0.03)
for side in (1, -1):   # elevation rams riding with the box (cylinder body along the box underside)
    rod(P + "_ram%d" % side, piv + Vector((0.6 * side, -1.0, 0.05)), piv + Vector((0.6 * side, -4.6, 0.05)), 0.09, "M_Steel", gt, seg=10)
    rod(P + "_rod%d" % side, piv + Vector((0.6 * side, -4.6, 0.05)), piv + Vector((0.6 * side, -5.4, 0.05)), 0.05, "M_Metal", gt, seg=8)
TUBES = []
for ix in (-1, 1):
    for iz in (-1, 1):
        c = box_c + Vector((ix * 0.42, 0, iz * 0.38))
        lathe_y(P + "_tin%d%d" % (ix, iz), [(-tube_len / 2 - 0.001, 0.33), (tube_len / 2 + 0.001, 0.33)], c, ["M_TubeIn"], gt, seg=20)
        TUBES.append(c)
# front cover strips
for k in range(4):
    abox(P + "_rib%d" % k, (1.80, 0.10, 1.60), box_c + Vector((0, -tube_len / 2 + 0.6 + k * 2.3, 0)), "M_Camo", gt, bevel=0.01)
# lids: one per tube, front end
lids = []
for k, c in enumerate(TUBES):
    gl = P + "_lid%d" % k
    acyl(P + "_lidc%d" % k, 0.36, 0.06, c + Vector((0, -tube_len / 2 - 0.035, 0)), "M_Olive", gl, axis=1, seg=20)
    lids.append((gl, c + Vector((0, -tube_len / 2 - 0.035, 0.36))))
INFO["LAUNCHER"] = dict(pivot=tuple(piv), tubes=[tuple(round(v, 3) for v in (c - piv)) for c in TUBES], tube_len=tube_len)

weather(gen_weather((), 0.1))

# ------------------------------------------------------------------------------------------------ hierarchy + clips
for P, s in MIS.items():
    rt = root(P)
    group_under(P + "_body", P + "_geo", rt)
    clip = P.lower() + "_deploy"
    for side in (1, -1):
        p, sd = INFO[P]["wing%d" % side]
        h = pivot(P + "_Wing%s" % ("L" if side > 0 else "R"), p, rt)   # unrotated empty: animate Z (no gimbal lock)
        group_under(P + "_wing%d" % side, P + "_wing%s_geo" % ("L" if side > 0 else "R"), h)
        stow = 1.50 * side   # rotate about world Z: wing tip swings back along the body
        aclip(clip, h, "rotation_euler", [(0, stow), (24, 0.0), (48, 0.0)], index=2)
        STOW.append((h, 2, stow))
    k = 0
    while ("fin%d" % k) in INFO[P]:
        p, fold = INFO[P]["fin%d" % k]
        h = hinge(P + "_Fin%d" % k, p, p + Vector((0, 1, 0)), parent=rt)
        group_under(P + "_fin%d" % k, P + "_fin%d_geo" % k, h)
        aclip(clip, h, "rotation_euler", [(0, fold), (12, fold), (24, 0.0), (48, 0.0)], index=0)
        STOW.append((h, 0, fold))
        k += 1
    py, drop = INFO[P]["pod"]
    pe = pivot(P + "_Pod", (0, py, 0), rt)
    group_under(P + "_pod", P + "_pod_geo", pe)
    aclip(clip, pe, "location", [(0, drop), (6, drop), (24, 0.0), (48, 0.0)], index=2)
    pe.location.z = drop   # rest = retracted (moved up into the body by `drop`)
    if INFO[P].get("boost"):
        bo = pivot(P + "_Booster", INFO[P]["boost"], rt)
        group_under(P + "_boost", P + "_booster_geo", bo)
for (h, i, v) in STOW:
    h.rotation_euler[i] = v
for P in ("HELLFIRE", "KAB50"):
    rt = root(P)
    group_under(P + "_body", P + "_geo", rt)
rt = root("LAUNCHER")
group_under("LAUNCHER_base", "LAUNCHER_geo", rt)
bx_h = hinge("LAUNCHER_Elev", INFO["LAUNCHER"]["pivot"], Vector(INFO["LAUNCHER"]["pivot"]) + Vector((1, 0, 0)), parent=rt)
group_under("LAUNCHER_box", "LAUNCHER_box_geo", bx_h)
for k, (gl, hp) in enumerate(lids):
    lh = hinge("LAUNCHER_Lid%d" % k, hp, hp + Vector((1, 0, 0)), parent=bx_h)
    group_under(gl, "LAUNCHER_lid%d_geo" % k, lh)
    aclip("launcher_lids", lh, "rotation_euler", [(0, 0.0), (12, -1.9), (48, -1.9)], index=0)
aclip("launcher_erect", bx_h, "rotation_euler", [(0, 0.0), (48, -math.radians(45))], index=0)
for k, c in enumerate(TUBES):
    pivot("LAUNCHER_Tube%d" % k, c, bx_h)
finish_nla()
for (h, i, v) in STOW:
    h.rotation_euler[i] = v
bpy.context.view_layer.update()
print("INFO", {k: {kk: (tuple(round(x, 3) for x in vv) if isinstance(vv, Vector) else vv) for kk, vv in v.items()} for k, v in INFO.items()})
print("CRUISE", stats())
print("GLB", export_glb("/home/user/models/cruise.glb"))
print("BLEND", save_blend("/home/user/models/cruise.blend", "Cruise"))
