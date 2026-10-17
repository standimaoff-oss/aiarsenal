# Tu-160 STEP 4: weathering, joins per animated node, hierarchy (wing children ride on the swing wings), clips, export, save
import json as _json


def t160_weather(w, n, ob):
    g = ob.get("grp", "")
    h = hash3(w)
    r = gg = b = 1.0 + (h - 0.5) * 0.04
    if g == "Int":
        if n.z > 0.7 and abs(w.x) < 0.3 and w.z < 3.6:
            r, gg, b = r * 0.82, gg * 0.80, b * 0.78
        if n.z < -0.5:
            r, gg, b = r * 0.8, gg * 0.8, b * 0.8
        return r, gg, b
    if g.startswith(("Gear", "Bogie")):
        k = 0.80 + 0.12 * h
        return r * k, gg * k * 0.96, b * k * 0.9
    if g.startswith("AB_Flame"):
        return 1.0, 1.0, 1.0
    if n.z < -0.5:                                    # white paint gets grimy underneath
        r, gg, b = r * 0.86, gg * 0.85, b * 0.83
    if g == "Body" and w.z < 3.2 and abs(w.x) < 1.8 and -14 < w.y < 13:
        s = clamp01((3.2 - w.z) / 0.6) * 0.10
        r, gg, b = r * (1 - s * 0.8), gg * (1 - s), b * (1 - s * 1.3)
    if g == "Body" and abs(abs(w.x) - NAC_X) < 1.7 and w.y > NAC_Y1 - 0.5 and w.z < 3.9:   # soot around the nozzles
        s = clamp01((w.y - (NAC_Y1 - 0.5)) / 1.5) * 0.35
        r, gg, b = r * (1 - s), gg * (1 - s), b * (1 - s * 0.9)
    if g == "Body" and -NAC_Y0 * 0 + NAC_Y0 - 0.3 < w.y < NAC_Y0 + 1.6 and abs(abs(w.x) - NAC_X) < 1.6 and w.z < 3.9:  # intake lips
        r, gg, b = r * 0.93, gg * 0.93, b * 0.92
    return r, gg, b


weather(t160_weather)
PIV = {k: (Vector(v[0]), Vector(v[1]) if v[1] else None) for k, v in _json.load(open("/home/user/.cache/air/t160_piv.json")).items()}
groups = sorted(set(o.get("grp") for o in col_get("BUILD").objects if o.type == 'MESH'))
J = {g: join_grp(g, g + "_geo") for g in groups}
bpy.context.view_layer.update()
root = empty("Tu160", (0, 0, 0), 1.5, 'PLAIN_AXES')
N = {}
for g in groups + [k for k in PIV if k not in groups]:
    if g in PIV:
        p0, p1 = PIV[g]
        N[g] = hinge(g, p0, p1) if p1 is not None else empty(g, p0, 0.5, 'ARROWS')
    else:
        N[g] = None
bpy.context.view_layer.update()
WING_CHILD = ("Slat_", "FlapIn_", "FlapMid_", "FlapOut_", "Aileron_")
for g, e in N.items():
    if e is None:
        continue
    if g.startswith("Bogie_"):
        par = N["Gear_" + g[-1]]
    elif g.startswith(WING_CHILD):
        par = N["Wing_" + g[-1]]
    else:
        par = root
    attach(e, par)
bpy.context.view_layer.update()
for g, ob in J.items():
    attach(ob, N[g] if N.get(g) is not None else root)
for e in list(col_get("RIG").objects):
    if e.parent is None and e is not root and e.name.split("_")[0] in ("WALK", "SEAT", "LADDER", "EXIT"):
        attach(e, root)
bpy.context.view_layer.update()

sc = bpy.context.scene
sc.render.fps = 24
D = math.radians
aclip("sweep", N["Wing_L"], "rotation_euler", [(0, 0.0), (48, D(45))], index=2)
aclip("sweep", N["Wing_R"], "rotation_euler", [(0, 0.0), (48, D(-45))], index=2)
MG_OPEN, NG_OPEN = D(100), D(90)
for s in "LR":
    aclip("gear", N["Gear_" + s], "rotation_euler", [(0, 0.0), (6, 0.0), (40, D(MG_RETRACT)), (48, D(MG_RETRACT))], index=0, interp='BEZIER')
    aclip("gear", N["Bogie_" + s], "rotation_euler", [(0, 0.0), (6, 0.0), (40, D(-MG_RETRACT)), (48, D(-MG_RETRACT))], index=0, interp='BEZIER')
    for io in "oi":
        nm = "MGDoor_%s_%s" % (s, io)
        ds = 1 if PIV[nm][0].x > POD_X * (1 if s == "L" else -1) else -1
        op = -ds * MG_OPEN
        N[nm].rotation_euler.x = op
        aclip("gear", N[nm], "rotation_euler", [(0, op), (36, op), (48, 0.0)], index=0, interp='BEZIER')
aclip("gear", N["Gear_N"], "rotation_euler", [(0, 0.0), (4, 0.0), (36, D(90)), (48, D(90))], index=0, interp='BEZIER')
for s, ds in (("L", 1), ("R", -1)):
    N["NGDoor_" + s].rotation_euler.x = -ds * NG_OPEN
    aclip("gear", N["NGDoor_" + s], "rotation_euler", [(0, -ds * NG_OPEN), (36, -ds * NG_OPEN), (48, 0.0)], index=0, interp='BEZIER')
for s in "LR":
    sg = 1 if s == "L" else -1
    aclip("slats", N["Slat_" + s], "rotation_euler", [(0, 0.0), (24, sg * D(20))], index=0, interp='BEZIER')
    for f in ("FlapIn_", "FlapMid_", "FlapOut_"):
        aclip("flaps", N[f + s], "rotation_euler", [(0, 0.0), (24, -sg * D(35))], index=0, interp='BEZIER')
    aclip("aileron", N["Aileron_" + s], "rotation_euler", [(0, D(20)), (24, 0.0), (48, D(-20))], index=0)
    aclip("elevator", N["Stab_" + s], "rotation_euler", [(0, sg * D(-15)), (19, 0.0), (48, sg * D(20))], index=0)
aclip("rudder", N["Rudder"], "rotation_euler", [(0, D(-20)), (24, 0.0), (48, D(20))], index=0)
for bi in (1, 2):
    aclip("bay_doors", N["BayDoor%d_L" % bi], "rotation_euler", [(0, 0.0), (24, D(-90))], index=0, interp='BEZIER')
    aclip("bay_doors", N["BayDoor%d_R" % bi], "rotation_euler", [(0, 0.0), (24, D(90))], index=0, interp='BEZIER')
finish_nla()
for o in bpy.data.objects:
    o.hide_viewport = False
    o.hide_set(False)
bpy.context.view_layer.update()
print("STEP4", stats(), "nodes", len(N))
print("GLB", export_glb("/home/user/models/tu160.glb"))
print("BLEND", save_blend("/home/user/models/tu160.blend", "Tu160"))
