# Loitering munitions: Shahed-136 (Geran-2), Shahed-131 (Geran-1), Shahed-238 (Geran-3, turbojet), An-196 Liutyi (twin-boom),
# plus the ground rail launcher. Drones: origin = airframe centre. Launcher: origin = ground, rail points -Y (launch direction).
reset_scene()
col_get("BUILD"); col_get("RIG")
ars_common_mats()
amat("M_Shahed", "T_SkinShahed", 0.6, 0.05)
amat("M_ShahedGrey", "T_SkinMisLight", 0.6, 0.05, factor=(0.80, 0.80, 0.78, 1))
amat("M_Liutyi", "T_SkinLiutyi", 0.6, 0.05)
amat("M_Booster", None, 0.5, 0.3, factor=(0.30, 0.31, 0.30, 1))
amat("M_Camo", "T_CamoGreen", 0.8, 0.1)
amat("M_RailSteel", None, 0.45, 0.8, factor=(0.30, 0.31, 0.30, 1))

DELTAS = {
    # length, span, body radius, wing root LE (t), root chord, tip chord, tip LE (t), skin, prop radius, jet
    "S136": dict(L=3.5, span=2.5, r=0.20, root_t=0.18, rc=2.15, tc=0.55, tip_t=0.66, skin="M_Shahed", prop=0.38, jet=False,
                 dec="geran2", wl_up=0.36, wl_dn=0.24),
    "S131": dict(L=2.6, span=2.2, r=0.15, root_t=0.22, rc=1.55, tc=0.42, tip_t=0.66, skin="M_Shahed", prop=0.30, jet=False,
                 dec="geran1", wl_up=0.30, wl_dn=0.0),
    "S238": dict(L=3.2, span=2.5, r=0.20, root_t=0.20, rc=2.00, tc=0.50, tip_t=0.66, skin="M_ShahedGrey", prop=0.0, jet=True,
                 dec="geran3", wl_up=0.34, wl_dn=0.22),
}
INFO = {}
SOOT = []
for P, s in DELTAS.items():
    L, y0, r = s["L"], -s["L"] / 2, s["r"]
    g = P + "_body"
    ctrl = [(0.0, 0.0, 0.0, 0.0, 0.0, 2.0), (0.015, 0.0, r * 0.35, r * 0.35, r * 0.35, 2.0), (0.06, 0.0, r * 0.75, r * 0.75, r * 0.72, 2.0),
            (0.14, 0.0, r, r, r * 0.95, 2.0), (0.80, 0.0, r, r, r * 0.95, 2.0), (0.93, 0.0, r * 0.88, r * 0.85, r * 0.85, 2.0),
            (1.0, 0.0, r * 0.62, r * 0.6, r * 0.6, 2.0)]
    body = loft(P + "_fus", stations(y0, L, 36), ctrl_prof(ctrl, L, y0), [s["skin"]], g, seg=32)
    # nose seam (warhead section joint) + seeker / GNSS antenna bumps
    lathe_y(P + "_seam", [(0, r * 1.005), (0.012, r * 1.012), (0.024, r * 1.005)], (0, y0 + L * 0.30, 0), ["M_Black"], g, seg=32)
    bm = bm_sphere(r * 0.30, (0, y0 + L * 0.42, r * 0.97), 12, 6, (1.0, 1.6, 0.35))
    aobj(P + "_gnss", bm, "M_Black", g)
    # cropped delta wing blended into the fuselage + vertical winglets at the tips
    half = s["span"] / 2
    for side in (1, -1):
        a = (r * 0.6 * side, y0 + L * s["root_t"], -r * 0.15)
        b = (half * side, y0 + L * s["tip_t"], -r * 0.10)
        panel(P + "_wing%d" % side, a, b, s["rc"], s["tc"], (0, 0, 1), [s["skin"]], g, t_root=0.09, t_tip=0.07, n=4, cam=0.0)
        if s["wl_up"] > 0:
            wa = (b[0] + 0.01 * side, b[1] + s["tc"] * 0.05, b[2])
            panel(P + "_wlu%d" % side, wa, (wa[0], wa[1] + s["tc"] * 0.35, wa[2] + s["wl_up"]), s["tc"] * 0.95, s["tc"] * 0.55,
                  (1, 0, 0), [s["skin"]], g, t_root=0.08, t_tip=0.07, n=2)
        if s["wl_dn"] > 0:
            wa = (b[0] + 0.01 * side, b[1] + s["tc"] * 0.15, b[2])
            panel(P + "_wld%d" % side, wa, (wa[0], wa[1] + s["tc"] * 0.35, wa[2] - s["wl_dn"]), s["tc"] * 0.8, s["tc"] * 0.5,
                  (1, 0, 0), [s["skin"]], g, t_root=0.08, t_tip=0.07, n=2)
        # elevon hinge strips (dark lines) and servo fairings
        p0 = Vector(b) + Vector((0, s["tc"] * 0.70, 0.03))
        p1 = Vector((r * 1.2 * side, y0 + L * s["root_t"] + s["rc"] * 0.85, -r * 0.1 + 0.035))
        rod(P + "_elevon%d" % side, p0, p1, 0.006, "M_Black", g, seg=4)
        bm = bm_sphere(0.04, (Vector(b) * 0.55 + Vector((0, 0.25, 0.03))) + Vector((0, L * 0.06, 0)), 8, 4, (0.6, 1.8, 0.5))
        aobj(P + "_servo%d" % side, bm, "M_Black", g)
    if not s["jet"]:
        # MD-550 piston engine in the tail: cooling slots, two exhausts, 2-blade wooden/composite pusher prop
        for k in range(6):
            a = 2 * PI * k / 6 + PI / 6
            dv = Vector((math.cos(a), 0, math.sin(a)))
            abox(P + "_fin_cool%d" % k, (0.012, 0.16, 0.012), dv * (r * 0.84) + Vector((0, y0 + L * 0.91, 0)), "M_Black", g,
                 rot=(0, -a, 0), bevel=0)
        for side in (1, -1):
            ex = Vector((r * 0.55 * side, y0 + L * 0.95, -r * 0.55))
            rod(P + "_exh%d" % side, ex, ex + Vector((0.05 * side, 0.12, -0.04)), 0.022, "M_Nozzle", g, seg=8)
            SOOT.append(ex + Vector((0, 0.25, 0)))
        INFO[P] = dict(py=y0 + L + 0.03, pz=0.0, prop=s["prop"])
    else:
        # TJ-150 class turbojet in a dorsal nacelle at the tail
        ny0 = y0 + L * 0.62
        lathe_y(P + "_nac", [(0, 0.10), (0.04, 0.125), (0.25, 0.135), (0.9, 0.125), (1.25, 0.10)], (0, ny0, r + 0.08), [s["skin"]], g, seg=20)
        lathe_y(P + "_inlet", [(0, 0.085), (0.04, 0.10)], (0, ny0 - 0.001, r + 0.08), ["M_Black"], g, seg=20)
        acyl(P + "_fan", 0.085, 0.01, (0, ny0 + 0.06, r + 0.08), "M_Steel", g, axis=1, seg=20)
        lathe_y(P + "_noz", [(0, 0.098), (0.10, 0.085), (0.13, 0.08)], (0, ny0 + 1.25, r + 0.08), ["M_Nozzle"], g, seg=20)
        abox(P + "_pylon", (0.04, 0.7, 0.10), (0, ny0 + 0.55, r + 0.0), s["skin"], g, bevel=0.01)
        SOOT.append(Vector((0, ny0 + 1.6, r + 0.08)))
        INFO[P] = dict(py=None, noz=(0, ny0 + 1.38, r + 0.08))
    # RATO booster under the rear fuselage (jettisoned after launch) - separate node
    gb = P + "_boost"
    by0 = y0 + L * 0.55
    lathe_y(P + "_rato", [(0, 0.0), (0.05, 0.07), (0.12, 0.085), (L * 0.32, 0.085), (L * 0.32 + 0.06, 0.06)], (0, by0, -r - 0.11),
            ["M_Booster"], gb, seg=16)
    lathe_y(P + "_ratonoz", [(0, 0.05), (0.08, 0.065)], (0, by0 + L * 0.32 + 0.05, -r - 0.11), ["M_Nozzle"], gb, seg=12)
    abox(P + "_ratomount", (0.05, 0.25, 0.08), (0, by0 + L * 0.15, -r - 0.02), "M_Steel", gb, bevel=0)
    INFO[P]["boost"] = (0, by0, -r - 0.11)
    # decals
    bpy.context.view_layer.update()
    for side in (1, -1):
        decal_on(P + "_d_name%d" % side, body, (r * side, y0 + L * 0.50, 0.02), (side, 0, 0), (0, 0, 1), L * 0.22, s["dec"], g)
        decal_on(P + "_d_ser%d" % side, body, (r * side, y0 + L * 0.20, 0.0), (side, 0, 0), (0, 0, 1), L * 0.09,
                 "ser" if P != "S238" else "ser_k", g)
    if P == "S238":
        decal_on(P + "_d_band", body, (0, y0 + L * 0.33, r), (0, 0, 1), (0, -1, 0), 0.30, "band_r", g, h=0.03)
    # propeller node
    if INFO[P].get("py"):
        gp = P + "_propg"
        prop_blades(P + "_prop", (0, INFO[P]["py"] + 0.04, 0), s["prop"], 2, ["M_PropWood"], gp, chord=0.07, r_hub=0.05, t=0.12)
        spinner(P + "_spin", (0, INFO[P]["py"], 0), 0.05, 0.12, "M_Black", gp)

# ------------------------------------------------------------------------------------------------ An-196 Liutyi (twin boom)
P = "LIUTYI"
g = P + "_body"
L, y0 = 4.4, -2.2
ctrl = [(0.0, 0.0, 0.0, 0.0, 0.0, 2.0), (0.02, 0.0, 0.10, 0.10, 0.10, 2.0), (0.10, 0.02, 0.22, 0.24, 0.20, 2.2),
        (0.30, 0.04, 0.27, 0.30, 0.24, 2.4), (0.55, 0.04, 0.27, 0.30, 0.24, 2.4), (0.68, 0.06, 0.20, 0.22, 0.18, 2.2),
        (0.72, 0.06, 0.14, 0.15, 0.13, 2.0), (0.74, 0.06, 0.08, 0.08, 0.08, 2.0)]
Lb = L * 0.74
body = loft(P + "_fus", stations(y0, Lb, 36), ctrl_prof([(t / 0.74, *rest) for (t, *rest) in ctrl], Lb, y0), ["M_Liutyi"], g, seg=32)
wing_z = -0.18
half = 3.35
for side in (1, -1):
    secs = wing_secs(0.2, y0 + 1.55, wing_z, 0.80, half, y0 + 1.70, wing_z + 0.12, 0.45, t_root=0.15, t_tip=0.11, n=6, twist_tip=-1.5,
                     cam=0.03, side=side)
    surface(P + "_wing%d" % side, secs, ["M_Liutyi"], g, nc=12, cap_root=True)
    tip, _c = wing_at(secs, 1.0, 0.1)
    lamp(P + "_nav%d" % side, tip + Vector((0.02 * side, 0, 0)), "M_NavRed" if side < 0 else "M_NavGreen", g, 0.025)
# booms + twin fins + stabiliser
bx = 0.85
for side in (1, -1):
    lathe_y(P + "_boom%d" % side, [(0, 0.0), (0.12, 0.05), (0.4, 0.065), (2.3, 0.05), (2.6, 0.035)], (bx * side, y0 + 1.40, wing_z + 0.02),
            ["M_Liutyi"], g, seg=14)
    a = (bx * side, y0 + 3.55, wing_z + 0.06)
    panel(P + "_fin%d" % side, a, (bx * side, y0 + 3.80, wing_z + 0.62), 0.55, 0.36, (1, 0, 0), ["M_Liutyi"], g, t_root=0.10, t_tip=0.08)
    panel(P + "_vfd%d" % side, (bx * side, y0 + 3.62, wing_z), (bx * side, y0 + 3.75, wing_z - 0.20), 0.42, 0.30, (1, 0, 0), ["M_Liutyi"], g,
          t_root=0.10, t_tip=0.08)
panel(P + "_stab", (-bx, y0 + 3.62, wing_z + 0.50), (bx, y0 + 3.62, wing_z + 0.50), 0.40, 0.40, (0, 0, 1), ["M_Liutyi"], g, t_root=0.09,
      t_tip=0.09, n=4, cap_root=True)
# engine (pusher) at the fuselage tail, cooling scoop, exhaust
lathe_y(P + "_cowl", [(0, 0.10), (0.15, 0.14), (0.35, 0.13), (0.45, 0.07)], (0, y0 + Lb - 0.05, 0.06), ["M_Dark"], g, seg=18)
abox(P + "_scoop", (0.14, 0.30, 0.08), (0, y0 + Lb - 0.35, 0.33), "M_Dark", g, bevel=0.02)
ex = Vector((0.10, y0 + Lb + 0.20, -0.05))
rod(P + "_exh", ex, ex + Vector((0.05, 0.10, -0.05)), 0.02, "M_Nozzle", g, seg=8)
SOOT.append(ex + Vector((0, 0.2, 0)))
# fixed tricycle gear
rod(P + "_nleg", (0, y0 + 0.45, -0.18), (0, y0 + 0.40, -0.55), 0.02, "M_Metal", g, seg=8)
wheel(P + "_nwh", (0, y0 + 0.40, -0.60), 0.08, 0.05, ["M_Tire", "M_Metal"], g)
for side in (1, -1):
    rod(P + "_mleg%d" % side, (0.12 * side, y0 + 1.95, -0.22), (0.45 * side, y0 + 2.05, -0.55), 0.022, "M_Metal", g, seg=8)
    wheel(P + "_mwh%d" % side, (0.48 * side, y0 + 2.05, -0.60), 0.10, 0.05, ["M_Tire", "M_Metal"], g)
bpy.context.view_layer.update()
for side in (1, -1):
    decal_on(P + "_d_name%d" % side, body, (0.3 * side, y0 + 1.0, 0.05), (side, 0, 0), (0, 0, 1), 0.85, "liutyi", g)
INFO[P] = dict(py=y0 + Lb + 0.42, pz=0.06, prop=0.55)
gp = P + "_propg"
prop_blades(P + "_prop", (0, INFO[P]["py"] + 0.04, 0.06), 0.55, 2, ["M_Prop"], gp, chord=0.09, r_hub=0.05)
spinner(P + "_spin", (0, INFO[P]["py"], 0.06), 0.07, 0.16, "M_Prop", gp)

# ------------------------------------------------------------------------------------------------ rail launcher
P = "RAIL"
g = P + "_body"
ang = math.radians(15.0)
rail_len = 6.0
# rail: rear (low) end at +Y, muzzle (high) end towards -Y; d = unit vector rear -> muzzle
d = Vector((0, -math.cos(ang), math.sin(ang)))
r0 = Vector((0, 2.6, 0.75))                       # rear (low) end of the rail
for side in (1, -1):
    p0 = r0 + Vector((0.16 * side, 0, 0))
    abox(P + "_rail%d" % side, (0.08, rail_len, 0.12), p0 + d * (rail_len / 2), "M_RailSteel", g, rot=(-ang, 0, 0), bevel=0.005)
for k in range(9):
    p = r0 + d * (0.35 + k * 0.66) + Vector((0, 0, -0.08))
    abox(P + "_tie%d" % k, (0.52, 0.08, 0.08), p, "M_RailSteel", g, rot=(-ang, 0, 0), bevel=0.005)
# A-frame supports
for k, f in enumerate((0.15, 0.55, 0.92)):
    top = r0 + d * (rail_len * f) + Vector((0, 0, -0.12))
    for side in (1, -1):
        foot = Vector((0.75 * side, top.y, 0.0))
        rod(P + "_leg%d_%d" % (k, side), top + Vector((0.2 * side, 0, 0)), foot, 0.045, "M_RailSteel", g, seg=8)
        abox(P + "_pad%d_%d" % (k, side), (0.30, 0.30, 0.04), foot + Vector((0, 0, 0.02)), "M_RailSteel", g, bevel=0.005)
    rod(P + "_xbar%d" % k, Vector((0.6, top.y, top.z * 0.35)), Vector((-0.6, top.y, top.z * 0.35)), 0.03, "M_RailSteel", g, seg=6)
for side in (1, -1):
    rod(P + "_long%d" % side, Vector((0.62 * side, r0.y + d.y * rail_len * 0.15, 0.25)), Vector((0.62 * side, r0.y + d.y * rail_len * 0.92, 0.7)),
        0.03, "M_RailSteel", g, seg=6)
# rear buffer + blast deflector + control box with cable
abox(P + "_buffer", (0.6, 0.15, 0.35), r0 + Vector((0, 0.08, 0.05)), "M_RailSteel", g, rot=(-ang, 0, 0))
abox(P + "_deflector", (1.6, 0.06, 1.0), Vector((0, r0.y + 1.8, 0.5)), "M_Camo", g, rot=(0.35, 0, 0), bevel=0.01)
abox(P + "_ctrlbox", (0.45, 0.30, 0.55), Vector((1.4, 0.4, 0.275)), "M_Camo", g, bevel=0.02)
abox(P + "_ctrllid", (0.47, 0.32, 0.03), Vector((1.4, 0.4, 0.565)), "M_Olive", g, bevel=0.005)
for k in range(3):
    acyl(P + "_lampc%d" % k, 0.025, 0.02, (1.4 + (k - 1) * 0.1, 0.24, 0.45), ["M_NavRed", "M_NavGreen", "M_Strobe"][k], g, axis=1, seg=8)
pts = [Vector((1.4, 0.55, 0.05)), Vector((1.0, 0.9, 0.02)), Vector((0.6, 1.6, 0.02)), Vector((0.25, 2.2, 0.5))]
for k in range(len(pts) - 1):
    rod(P + "_cable%d" % k, pts[k], pts[k + 1], 0.012, "M_Black", g, seg=6)
# sliding cradle (shoe) the drone sits on: moves along the rail during launch
gc = P + "_cradle"
cpos = r0 + d * (rail_len * 0.22)
abox(P + "_shoe", (0.5, 0.6, 0.06), cpos + Vector((0, 0, 0.10)), "M_Steel", gc, rot=(-ang, 0, 0), bevel=0.01)
for side in (1, -1):
    abox(P + "_shoearm%d" % side, (0.04, 0.4, 0.25), cpos + Vector((0.24 * side, 0, 0.24)), "M_Steel", gc, rot=(-ang, 0, 0), bevel=0.005)
INFO["RAIL"] = dict(cradle=tuple(cpos + Vector((0, 0, 0.13))), muzzle=tuple(r0 + d * rail_len), ang=15.0)

weather(gen_weather(SOOT, 0.4))

for P in list(DELTAS) + ["LIUTYI"]:
    rt = root(P)
    group_under(P + "_body", P + "_geo", rt)
    if INFO[P].get("py"):
        pr = pivot(P + "_Prop", (0, INFO[P]["py"], INFO[P].get("pz", 0.0)), rt)
        group_under(P + "_propg", P + "_prop_geo", pr)
    if P in DELTAS:
        bo = pivot(P + "_Booster", INFO[P]["boost"], rt)
        group_under(P + "_boost", P + "_booster_geo", bo)
        if INFO[P].get("noz"):
            pivot(P + "_Nozzle", INFO[P]["noz"], rt)
rt = root("RAIL")
group_under("RAIL_body", "RAIL_geo", rt)
cr = pivot("RAIL_Cradle", INFO["RAIL"]["cradle"], rt)
group_under("RAIL_cradle", "RAIL_cradle_geo", cr)
pivot("RAIL_Seat", INFO["RAIL"]["cradle"], rt)
finish_nla()
bpy.context.view_layer.update()
print("INFO", {k: {kk: (tuple(round(x, 3) for x in vv) if isinstance(vv, (tuple, Vector)) else vv) for kk, vv in v.items()} for k, v in INFO.items()})
print("SHAHED", stats())
print("GLB", export_glb("/home/user/models/shahed.glb"))
print("BLEND", save_blend("/home/user/models/shahed.blend", "Shahed"))
