# Nuclear weapons: RDS-1 ("izdeliye 501", 1949), AN602 "Tsar Bomba" (1961, with parachute), and a Yars-type 3-stage ICBM
# with its 16x16 TEL (canister erects to vertical). Bombs/missile: origin = centre, nose -Y. TEL: origin = ground centre, cab at -Y.
reset_scene()
col_get("BUILD"); col_get("RIG")
ars_common_mats()
amat("M_NukeGreen", "T_SkinNukeGreen", 0.6, 0.15)
amat("M_NukeWhite", "T_SkinNukeWhite", 0.5, 0.2)
amat("M_IcbmGreen", "T_SkinNukeGreen", 0.6, 0.1, factor=(0.85, 0.9, 0.85, 1))
amat("M_RV", None, 0.7, 0.1, factor=(0.18, 0.16, 0.14, 1))
amat("M_Canopy", None, 0.8, 0.0, factor=(0.85, 0.42, 0.12, 1))
amat("M_CanopyW", None, 0.8, 0.0, factor=(0.92, 0.92, 0.88, 1))
amat("M_Line", None, 0.8, 0.0, factor=(0.75, 0.75, 0.70, 1))
amat("M_Camo", "T_CamoGreen", 0.8, 0.1)
amat("M_CabGlass", None, 0.05, 0.3, factor=(0.10, 0.14, 0.16, 1), glass_alpha=0.55)
INFO = {}

# ------------------------------------------------------------------------------------------------ RDS-1
P = "RDS1"
g = P + "_body"
L, R0 = 3.25, 0.75
y0 = -L / 2
prof = [(0.0, 0.0), (0.02, 0.30), (0.06, 0.52), (0.12, 0.70), (0.20, 0.84), (0.30, 0.95), (0.40, 1.0), (0.50, 0.98), (0.62, 0.88),
        (0.74, 0.70), (0.86, 0.48), (0.95, 0.30), (1.0, 0.22), (1.0, 0.0)]
body = lathe_y(P + "_shell", [(f * L, r * R0) for f, r in prof], (0, y0, 0), ["M_NukeGreen"], g, seg=40)
# equatorial assembly flange with bolt heads (implosion sphere joint), nose cap, inspection hatch
lathe_y(P + "_flange", [(-0.03, R0 * 1.0), (-0.03, R0 * 1.02), (0.03, R0 * 1.02), (0.03, R0 * 1.0)], (0, y0 + L * 0.40, 0), ["M_NukeGreen"], g, seg=40)
for k in range(32):
    a = 2 * PI * k / 32
    acyl(P + "_bolt%d" % k, 0.018, 0.04, (math.cos(a) * R0 * 1.02, y0 + L * 0.40, math.sin(a) * R0 * 1.02), "M_Steel", g, axis=1, seg=6)
lathe_y(P + "_nosecap", [(0, 0.0), (0.03, 0.14), (0.05, 0.18), (0.05, 0.0)], (0, y0 - 0.01, 0), ["M_Steel"], g, seg=20)
# box tail stabiliser: 4 fins + square frame
ty0, ty1 = y0 + L * 0.66, y0 + L + 0.30
for k in range(4):
    a = PI / 4 + k * PI / 2
    dv = Vector((math.cos(a), 0, math.sin(a)))
    p0 = dv * (R0 * 0.62) + Vector((0, ty0, 0))
    p1 = dv * 1.0 + Vector((0, ty0 + 0.45, 0))
    panel(P + "_fin%d" % k, tuple(p0), tuple(p1), ty1 - ty0, ty1 - ty0 - 0.45, tuple(dv.cross(Vector((0, 1, 0))).normalized()),
          ["M_NukeGreen"], g, t_root=0.03, t_tip=0.03, n=2)
s_ = 1.0 / math.sqrt(2) * 1.0 + 0.02
for k in range(4):   # square frame joining the fin tips
    a0 = PI / 4 + k * PI / 2
    a1 = a0 + PI / 2
    p0 = Vector((math.cos(a0), 0, math.sin(a0)))
    p1 = Vector((math.cos(a1), 0, math.sin(a1)))
    mid = (p0 + p1) / 2
    ln = (p1 - p0).length
    rot = math.atan2(p1.z - p0.z, p1.x - p0.x)
    abox(P + "_frame%d" % k, (ln + 0.04, ty1 - ty0 - 0.45, 0.03), mid + Vector((0, (ty0 + 0.45 + ty1) / 2, 0)), "M_NukeGreen", g,
         rot=(0, -rot, 0), bevel=0.005)
# suspension lugs + crutch pads, arming plug cover
for k, ly in enumerate((y0 + L * 0.30, y0 + L * 0.52)):
    rod(P + "_lugA%d" % k, (0, ly - 0.05, R0 * 0.95), (0, ly - 0.05, R0 * 1.12), 0.02, "M_Steel", g, seg=6)
    rod(P + "_lugB%d" % k, (0, ly + 0.05, R0 * 0.95), (0, ly + 0.05, R0 * 1.12), 0.02, "M_Steel", g, seg=6)
    rod(P + "_lugC%d" % k, (0, ly - 0.05, R0 * 1.12), (0, ly + 0.05, R0 * 1.12), 0.02, "M_Steel", g, seg=6)
abox(P + "_armcover", (0.18, 0.25, 0.04), (0.35, y0 + L * 0.36, R0 * 0.88), "M_Steel", g, rot=(0, -0.45, 0), bevel=0.01)
bpy.context.view_layer.update()
for side in (1, -1):
    decal_on(P + "_d_name%d" % side, body, (R0 * side, y0 + L * 0.50, 0.12), (side, 0, 0), (0, 0, 1), 0.7, "rds1", g)
    decal_on(P + "_d_izd%d" % side, body, (R0 * side, y0 + L * 0.50, -0.12), (side, 0, 0), (0, 0, 1), 0.6, "izd501", g)
    decal_on(P + "_d_tref%d" % side, body, (R0 * side, y0 + L * 0.25, 0.0), (side, 0, 0), (0, 0, 1), 0.32, "trefoil", g)
decal_on(P + "_d_warn", body, (0, y0 + L * 0.45, R0), (0, 0, 1), (0, -1, 0), 0.9, "opasno", g)

# ------------------------------------------------------------------------------------------------ AN602
P = "AN602"
g = P + "_body"
L, R0 = 8.0, 1.05
y0 = -L / 2
prof = [(0.0, 0.0), (0.01, 0.30), (0.04, 0.62), (0.08, 0.82), (0.14, 0.95), (0.20, 1.0), (0.66, 1.0), (0.74, 0.94), (0.84, 0.78),
        (0.93, 0.62), (1.0, 0.56), (1.0, 0.0)]
body = lathe_y(P + "_shell", [(f * L, r * R0) for f, r in prof], (0, y0, 0), ["M_NukeWhite"], g, seg=48)
for k, f in enumerate((0.20, 0.42, 0.66)):
    lathe_y(P + "_ring%d" % k, [(-0.025, R0 * 1.0), (-0.025, R0 * 1.012), (0.025, R0 * 1.012), (0.025, R0 * 1.0)], (0, y0 + L * f, 0),
            ["M_NukeWhite"], g, seg=48)
# 4 large tail fins + parachute container
ty = y0 + L * 0.72
for k in range(4):
    a = PI / 4 + k * PI / 2
    dv = Vector((math.cos(a), 0, math.sin(a)))
    p0 = dv * (R0 * 0.80) + Vector((0, ty, 0))
    p1 = dv * (R0 + 0.75) + Vector((0, ty + 1.2, 0))
    panel(P + "_fin%d" % k, tuple(p0), tuple(p1), 2.0, 1.0, tuple(dv.cross(Vector((0, 1, 0))).normalized()), ["M_NukeWhite"], g,
          t_root=0.05, t_tip=0.05, n=2)
lathe_y(P + "_chutecan", [(0, R0 * 0.56), (0.35, R0 * 0.54), (0.42, R0 * 0.45), (0.45, 0.0)], (0, y0 + L, 0), ["M_Steel"], g, seg=32)
# bomb bay crutches / lugs (Tu-95V carried it semi-recessed)
for k, ly in enumerate((y0 + L * 0.33, y0 + L * 0.55)):
    abox(P + "_lug%d" % k, (0.16, 0.30, 0.14), (0, ly, R0 + 0.05), "M_Steel", g, bevel=0.01)
bpy.context.view_layer.update()
for side in (1, -1):
    decal_on(P + "_d_name%d" % side, body, (R0 * side, y0 + L * 0.42, 0.25), (side, 0, 0), (0, 0, 1), 1.4, "an602", g)
    decal_on(P + "_d_izd%d" % side, body, (R0 * side, y0 + L * 0.42, -0.25), (side, 0, 0), (0, 0, 1), 1.3, "izd602", g)
    decal_on(P + "_d_tref%d" % side, body, (R0 * side, y0 + L * 0.60, 0.0), (side, 0, 0), (0, 0, 1), 0.55, "trefoil", g)
    decal_on(P + "_d_star%d" % side, body, (R0 * side, y0 + L * 0.24, 0.0), (side, 0, 0), (0, 0, 1), 0.55, "star_ru", g)
decal_on(P + "_d_warn", body, (0, y0 + L * 0.45, R0), (0, 0, 1), (0, -1, 0), 1.8, "opasno", g)
decal_on(P + "_d_nk", body, (0, y0 + L * 0.30, R0), (0, 0, 1), (0, -1, 0), 1.2, "ne_kantovat", g)
INFO["AN602"] = dict(chute_root=(0, y0 + L + 0.45, 0))
# parachute (main canopy, scaled down for the game: r = 9 m), separate node, deployed above (+Y = behind the bomb when falling)
gc = P + "_chute"
cy = y0 + L + 0.45
cr, cdepth = 9.0, 5.0
canopy_y = cy + 22.0
segs = 24
bm = bmesh.new()
uvl = bm.loops.layers.uv.new()
rings = []
for i in range(7):
    t = i / 6
    rr = cr * math.sin(t * PI / 2)
    yy = canopy_y + cdepth * math.cos(t * PI / 2)
    ring = [bm.verts.new((math.cos(2 * PI * k / segs) * rr, yy, math.sin(2 * PI * k / segs) * rr)) for k in range(segs)]
    rings.append(ring)
for i in range(6):
    for k in range(segs):
        k2 = (k + 1) % segs
        try:
            f = bm.faces.new((rings[i][k], rings[i + 1][k], rings[i + 1][k2], rings[i][k2]))
            f.material_index = 0 if (k // 2) % 2 == 0 else 1
        except ValueError:
            pass
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4)
aobj(P + "_canopy", bm, ["M_Canopy", "M_CanopyW"], gc, smooth=True, dirt=False)
for k in range(0, segs, 2):
    a = 2 * PI * k / segs
    rod(P + "_line%d" % k, (0, cy + 0.2, 0), (math.cos(a) * cr, canopy_y, math.sin(a) * cr), 0.03, "M_Line", gc, seg=4, dirt=False)

# ------------------------------------------------------------------------------------------------ Yars-type ICBM (3 stages + bus/RV)
P = "YARS"
Lm, Rm = 22.0, 0.93
y0 = -Lm / 2
stages = [("S1", 0.55, 1.0, Rm), ("S2", 0.30, 0.55, Rm * 0.85), ("S3", 0.14, 0.30, Rm * 0.68)]
for name, f0, f1, rr in stages:
    gs = P + "_" + name
    ya, yb = y0 + Lm * f0, y0 + Lm * f1
    lathe_y(P + "_%s_case" % name, [(0, rr * 0.97), (0.04, rr), (yb - ya - 0.05, rr), (yb - ya, rr * 0.95)], (0, ya, 0), ["M_IcbmGreen"], gs, seg=36)
    # interstage skirt + nozzle
    lathe_y(P + "_%s_noz" % name, [(0, rr * 0.35), (0.6, rr * 0.55)] if name != "S1" else [(0, rr * 0.45), (0.9, rr * 0.80)],
            (0, yb - 0.1, 0), ["M_Nozzle"], gs, seg=24)
    for k in range(4 if name == "S1" else 0):   # S1 grid/aero fins are retracted; small raceway fairings instead
        a = k * PI / 2
        abox(P + "_%s_race%d" % (name, k), (0.10, (yb - ya) * 0.8, 0.06), (math.cos(a) * rr, (ya + yb) / 2, math.sin(a) * rr), "M_IcbmGreen", gs,
             rot=(0, -a, 0), bevel=0.01)
    if name == "S2":   # interstage cone between S1 and S2 radii
        lathe_y(P + "_S2_skirt", [(0, Rm * 0.86), (0.9, Rm * 0.995)], (0, yb - 0.05, 0), ["M_IcbmGreen"], gs, seg=36)
    if name == "S3":
        lathe_y(P + "_S3_skirt", [(0, Rm * 0.69), (0.8, Rm * 0.85)], (0, yb - 0.05, 0), ["M_IcbmGreen"], gs, seg=36)
gb = P + "_Bus"
yb0 = y0 + Lm * 0.14
lathe_y(P + "_fairing", [(0, 0.0), (0.4, 0.22), (1.2, 0.45), (2.2, 0.60), (3.08, 0.63)], (0, y0, 0), ["M_IcbmGreen"], gb, seg=32)
lathe_y(P + "_fairtip", [(0, 0.0), (0.12, 0.05), (0.4, 0.05)], (0, y0 - 0.38, 0), ["M_Steel"], gb, seg=8)
gr = P + "_RV"
lathe_y(P + "_rv", [(0, 0.0), (0.05, 0.05), (1.4, 0.28), (1.55, 0.30), (1.6, 0.0)], (0, y0 + 0.8, 0), ["M_RV"], gr, seg=24)
INFO["YARS"] = dict(L=Lm, sep=[(n, y0 + Lm * f0) for n, f0, f1, rr in stages])

# ------------------------------------------------------------------------------------------------ TEL (MZKT-79221-like, 16x16)
P = "YARSTEL"
g = P + "_body"
TL = 22.7
ty0 = -TL / 2
abox(P + "_frame", (2.6, TL - 1.0, 0.45), (0, 0.5, 1.25), "M_Camo", g, bevel=0.03)
# cab (front, -Y)
abox(P + "_cab", (3.2, 2.6, 1.8), (0, ty0 + 1.4, 2.25), "M_Camo", g, bevel=0.08)
abox(P + "_cabroof", (3.0, 2.2, 0.15), (0, ty0 + 1.5, 3.2), "M_Camo", g, bevel=0.05)
for side in (1, -1):
    abox(P + "_win%d" % side, (0.02, 1.0, 0.6), (1.61 * side, ty0 + 1.1, 2.65), "M_CabGlass", g, bevel=0)
abox(P + "_wsc", (2.8, 0.03, 0.65), (0, ty0 + 0.09, 2.65), "M_CabGlass", g, rot=(0.12, 0, 0), bevel=0)
for side in (1, -1):
    acyl(P + "_head%d" % side, 0.12, 0.05, (1.2 * side, ty0 + 0.08, 1.75), "M_Strobe", g, axis=1, seg=12)
# engine/equipment bays along the sides
for side in (1, -1):
    abox(P + "_side%d" % side, (0.5, TL - 4.5, 0.9), (1.3 * side, 1.0, 1.9), "M_Camo", g, bevel=0.03)
# 8 axles x 2 sides, 1.6 m wheels
axles = [ty0 + 3.6 + k * 2.15 for k in range(4)] + [ty0 + 3.6 + 4 * 2.15 + 1.2 + k * 2.15 for k in range(4)]
for k, ay in enumerate(axles):
    for side in (1, -1):
        wheel(P + "_wh%d_%d" % (k, side), (1.45 * side, ay, 0.80), 0.80, 0.55, ["M_Tire", "M_Steel"], g, seg=24, hub_r=0.40)
# rear jacks (legs down in rest pose for launch readiness)
for side in (1, -1):
    abox(P + "_jack%d" % side, (0.30, 0.30, 1.2), (1.2 * side, ty0 + TL - 0.6, 0.6), "M_Steel", g, bevel=0.01)
    abox(P + "_jpad%d" % side, (0.7, 0.7, 0.08), (1.2 * side, ty0 + TL - 0.6, 0.04), "M_Steel", g, bevel=0.01)
# erector + canister (TPK): pivot at the rear, rises from horizontal (canister nose to the cab) to vertical
ge = P + "_erector"
piv = Vector((0, ty0 + TL - 0.4, 2.25))
can_len, can_r = 22.4, 1.06
can_c = piv + Vector((0, -can_len / 2 - 0.2, 0.85))
lathe_y(P + "_can", [(-can_len / 2, can_r * 0.98), (-can_len / 2 + 0.05, can_r), (can_len / 2 - 0.05, can_r), (can_len / 2, can_r * 0.98)],
        can_c, ["M_Camo"], ge, seg=36)
for k in range(8):
    lathe_y(P + "_band%d" % k, [(-0.08, can_r * 1.0), (-0.08, can_r * 1.03), (0.08, can_r * 1.03), (0.08, can_r * 1.0)],
            can_c + Vector((0, -can_len / 2 + 1.0 + k * 2.9, 0)), ["M_Steel"], ge, seg=36)
abox(P + "_cradle", (1.6, can_len - 2.0, 0.35), can_c + Vector((0, 0.5, -can_len * 0 - can_r - 0.1)), "M_Steel", ge, bevel=0.02)
lathe_y(P + "_baseplate", [(0, 0.0), (0.0, can_r * 1.08), (0.3, can_r * 1.08), (0.3, 0.0)], can_c + Vector((0, can_len / 2, 0)), ["M_Steel"], ge, seg=36)
# front cap (blown off at launch): separate node
gcap = P + "_cap"
lathe_y(P + "_capd", [(0, 0.0), (0.25, can_r * 0.85), (0.45, can_r * 1.02), (0.5, can_r * 1.02)], can_c + Vector((0, -can_len / 2 - 0.45, 0)),
        ["M_Camo"], gcap, seg=36)
for side in (1, -1):   # erector rams
    rod(P + "_ram%d" % side, Vector((0.9 * side, ty0 + 9.0, 1.5)), Vector((0.9 * side, ty0 + 11.0, 2.4)), 0.13, "M_Steel", g, seg=10)
INFO["YARSTEL"] = dict(pivot=tuple(piv), can_c=tuple(can_c), can_len=can_len)
bpy.context.view_layer.update()

weather(gen_weather((), 0.1))

for P in ("RDS1", "AN602"):
    rt = root(P)
    group_under(P + "_body", P + "_geo", rt)
ch = pivot("AN602_Chute", INFO["AN602"]["chute_root"], bpy.data.objects["AN602"])
group_under("AN602_chute", "AN602_chute_geo", ch)
rt = root("YARS")
for name, ya in INFO["YARS"]["sep"]:
    st = pivot("YARS_" + name, (0, ya, 0), rt)
    group_under("YARS_" + name, "YARS_%s_geo" % name, st)
bus = pivot("YARS_Bus", (0, -INFO["YARS"]["L"] / 2, 0), rt)
group_under("YARS_Bus", "YARS_bus_geo", bus)
rv = pivot("YARS_RVNode", (0, -INFO["YARS"]["L"] / 2 + 0.8, 0), rt)
group_under("YARS_RV", "YARS_rv_geo", rv)
rt = root("YARSTEL")
group_under("YARSTEL_body", "YARSTEL_geo", rt)
eh = hinge("YARSTEL_Erector", INFO["YARSTEL"]["pivot"], Vector(INFO["YARSTEL"]["pivot"]) + Vector((1, 0, 0)), parent=rt)
group_under("YARSTEL_erector", "YARSTEL_erector_geo", eh)
cn = Vector(INFO["YARSTEL"]["can_c"]) + Vector((0, -INFO["YARSTEL"]["can_len"] / 2 - 0.45, 0))
cp = pivot("YARSTEL_Cap", cn, eh)
group_under("YARSTEL_cap", "YARSTEL_cap_geo", cp)
pivot("YARSTEL_Missile", INFO["YARSTEL"]["can_c"], eh)
aclip("yarstel_erect", eh, "rotation_euler", [(0, 0.0), (96, -math.radians(90))], index=0, interp='BEZIER')
finish_nla()
bpy.context.view_layer.update()
print("INFO", INFO)
print("NUKES", stats())
print("GLB", export_glb("/home/user/models/nukes.glb"))
print("BLEND", save_blend("/home/user/models/nukes.blend", "Nukes"))
