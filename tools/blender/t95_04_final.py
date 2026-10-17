# Tu-95MS STEP 4: weathering, joins per animated node, hinge hierarchy, animation clips, export GLB + save .blend
import json as _json
NAC_X = (13.6, 6.3, -6.3, -13.6)


def t95_weather(w, n, ob):
    g = ob.get("grp", "")
    h = hash3(w)
    r = gg = b = 1.0 + (h - 0.5) * 0.05
    if g in ("Int", "IntR"):
        if n.z > 0.7 and abs(w.x) < 0.35 and w.z < 3.5:          # boot wear along the aisle
            r, gg, b = r * 0.82, gg * 0.80, b * 0.78
        if n.z < -0.5:
            r, gg, b = r * 0.8, gg * 0.8, b * 0.8
        return r, gg, b
    if g.startswith(("Gear", "Bogie")):                          # brake dust + runway grime
        k = 0.78 + 0.12 * h
        return r * k, gg * k * 0.96, b * k * 0.9
    if n.z < -0.5:                                              # undersides: exhaust film + shadowed grime
        r, gg, b = r * 0.80, gg * 0.80, b * 0.80
    if n.z > 0.2:                                               # NK-12 exhaust soot over the wing upper surface
        for nx in NAC_X:
            dx = abs(w.x - nx)
            if dx < 0.9 and 0.3 < w.y < 6.0 and 2.9 < w.z < 4.6:
                s = clamp01((w.y - 0.3) / 2.5) * 0.55 * (1 - dx / 0.9) * (1.0 - clamp01((w.y - 3.5) / 2.5) * 0.5)
                r, gg, b = r * (1 - s), gg * (1 - s), b * (1 - s * 0.95)
    if g == "Body" and w.z < 2.35 and -14 < w.y < 18:            # belly grime / hydraulic film
        s = clamp01((2.35 - w.z) / 0.6) * 0.16
        r, gg, b = r * (1 - s * 0.8), gg * (1 - s), b * (1 - s * 1.3)
    if g.startswith(("Prop", "PropDisc")):
        return 1.0, 1.0, 1.0
    return r, gg, b


weather(t95_weather)

# ---------------------------------------------------------------- join meshes per node
PIV = {k: (Vector(v[0]), Vector(v[1]) if v[1] else None) for k, v in _json.load(open("/home/user/.cache/air/t95_piv.json")).items()}
groups = sorted(set(o.get("grp") for o in col_get("BUILD").objects if o.type == 'MESH'))
J = {}
for g in groups:
    J[g] = join_grp(g, g + "_geo")
bpy.context.view_layer.update()

root = empty("Tu95MS", (0, 0, 0), 1.5, 'PLAIN_AXES')
N = {}
for g in groups:
    if g in PIV:
        p0, p1 = PIV[g]
        if p1 is not None:
            N[g] = hinge(g, p0, p1)
        else:
            N[g] = empty(g, p0, 0.5, 'ARROWS')
    else:
        N[g] = None
bpy.context.view_layer.update()
# hierarchy: bogies ride on their legs, everything else on the root
for g, e in N.items():
    if e is None:
        continue
    par = N.get("Gear_" + g[-1]) if g.startswith("Bogie_") else root
    attach(e, par)
bpy.context.view_layer.update()
for g, ob in J.items():
    attach(ob, N[g] if N[g] is not None else root)
for e in list(col_get("RIG").objects):          # gameplay markers (walk zones, seats, ladders, exits)
    if e.parent is None and e is not root and e.name.split("_")[0] in ("WALK", "SEAT", "LADDER", "EXIT"):
        attach(e, root)
bpy.context.view_layer.update()

# ---------------------------------------------------------------- animation clips (24 fps)
sc = bpy.context.scene
sc.render.fps = 24
D = math.radians
for k in "1234":
    aclip("props", N["Prop_%sF" % k], "rotation_euler", [(0, 0.0), (24, 2 * PI)], index=1)
    aclip("props", N["Prop_%sR" % k], "rotation_euler", [(0, 0.0), (24, -2 * PI)], index=1)
MG_OPEN, NG_OPEN = D(100), D(88)
for s in "LR":
    aclip("gear", N["Gear_" + s], "rotation_euler", [(0, 0.0), (6, 0.0), (40, D(105)), (48, D(105))], index=0, interp='BEZIER')
    aclip("gear", N["Bogie_" + s], "rotation_euler", [(0, 0.0), (8, 0.0), (40, D(-105)), (48, D(-105))], index=0, interp='BEZIER')
    for io in "oi":
        nm = "MGDoor_%s_%s" % (s, io)
        ds = 1 if PIV[nm][0].x > (6.3 if s == "L" else -6.3) else -1
        op = -ds * MG_OPEN                 # outward swing about the +Y hinge
        N[nm].rotation_euler.x = op        # rest pose = gear down, doors open
        aclip("gear", N[nm], "rotation_euler", [(0, op), (36, op), (48, 0.0)], index=0, interp='BEZIER')
aclip("gear", N["Gear_N"], "rotation_euler", [(0, 0.0), (4, 0.0), (36, D(92)), (48, D(92))], index=0, interp='BEZIER')
for s, ds in (("L", 1), ("R", -1)):
    N["NGDoor_" + s].rotation_euler.x = -ds * NG_OPEN
    aclip("gear", N["NGDoor_" + s], "rotation_euler", [(0, -ds * NG_OPEN), (36, -ds * NG_OPEN), (48, 0.0)], index=0, interp='BEZIER')
aclip("gear", N["Gear_T"], "rotation_euler", [(0, 0.0), (10, 0.0), (36, D(65)), (48, D(65))], index=0, interp='BEZIER')
for s in "LR":
    aclip("aileron", N["Aileron_" + s], "rotation_euler", [(0, D(20)), (24, 0.0), (48, D(-20))], index=0)
    sg = -1 if s == "L" else 1
    for f in ("FlapIn_", "FlapMid_", "FlapOut_"):
        aclip("flaps", N[f + s], "rotation_euler", [(0, 0.0), (24, sg * D(35))], index=0, interp='BEZIER')
aclip("elevator", N["Elevator_L"], "rotation_euler", [(0, D(-15)), (19, 0.0), (48, D(25))], index=0)
aclip("elevator", N["Elevator_R"], "rotation_euler", [(0, D(15)), (19, 0.0), (48, D(-25))], index=0)
aclip("rudder", N["Rudder"], "rotation_euler", [(0, D(-25)), (24, 0.0), (48, D(25))], index=0)
aclip("bay_doors", N["BayDoor_L"], "rotation_euler", [(0, 0.0), (24, D(-70))], index=0, interp='BEZIER')
aclip("bay_doors", N["BayDoor_R"], "rotation_euler", [(0, 0.0), (24, D(70))], index=0, interp='BEZIER')
# rear hatch and the bay door hinges use their own local X (hinge axis); bay doors were built with axis +Y -> local X
aclip("hatch", N["Hatch_R"], "rotation_euler", [(0, 0.0), (24, D(-100))], index=0, interp='BEZIER')
aclip("turret", N["Turret_T"], "rotation_euler", [(0, 0.0), (24, D(-60)), (72, D(60)), (96, 0.0)], index=2, interp='BEZIER')
finish_nla()
print("STEP4", stats(), "nodes", len(N))

# ---------------------------------------------------------------- export + save
for o in bpy.data.objects:
    o.hide_viewport = False
    o.hide_set(False)
bpy.context.view_layer.update()
_sz = export_glb("/home/user/models/tu95ms.glb")
print("GLB", _sz)
print("BLEND", save_blend("/home/user/models/tu95ms.blend", "Tu95MS"))
