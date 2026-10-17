"""Handheld weapons for AI Arsenal (Blender 5.x headless):  RPG-7 + PG-7VL, 9K38 Igla + missile, FGM-148 Javelin + missile.
   blender -b --factory-startup --python tools/blender/handheld.py
Units: metres = blocks. glTF +Z forward = Blender -Y; glTF +X (left) = Blender +X; up = +Z.
Origin of every launcher = the point that rests on the shoulder / is held (the Java item renderer places it).
Detail is built from separate parts (sights, grips, shields, caps, fins), each joined per node.
"""
import bpy, math, os
from mathutils import Vector

ROOT = "/home/user"
TEXD = ROOT + "/tools/blender/tex_hand"
GLB = ROOT + "/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb"
FORT = ROOT + "/mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/block"

bpy.ops.wm.read_factory_settings(use_empty=True)
MATS = {}


def mat(key, path, rough=0.6, metal=0.0, alpha=None):
    m = bpy.data.materials.new(key)
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    img = bpy.data.images.load(path)
    img.pack()
    t = nt.nodes.new("ShaderNodeTexImage"); t.image = img
    nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    MATS[key] = m


mat("Olive", TEXD + "/T_Olive.png")
mat("OliveDark", TEXD + "/T_OliveDark.png")
mat("Tan", TEXD + "/T_Tan.png")
mat("Black", TEXD + "/T_Black.png", 0.7)
mat("Gunmetal", TEXD + "/T_Gunmetal.png", 0.45, 0.6)
mat("Glass", TEXD + "/T_Glass.png", 0.1)
mat("Rubber", TEXD + "/T_Rubber.png", 0.9)
mat("Wood", FORT + "/fort_wood.png", 0.7)

D = math.radians
Y = (D(90), 0, 0)          # cylinder axis along Blender Y (= glTF Z, forward/back)
X = (0, D(90), 0)


def _fin(o, m, bevel=0.0, smooth=False):
    o.data.materials.clear()
    o.data.materials.append(MATS[m])
    if bevel > 0:
        md = o.modifiers.new("b", "BEVEL"); md.width = bevel; md.segments = 1; md.limit_method = "ANGLE"
    if smooth:
        for p in o.data.polygons:
            p.use_smooth = True
    return o


def cyl(r, depth, loc, m, rot=Y, v=20, bevel=0.0, smooth=True, r2=None):
    if r2 is None:
        bpy.ops.mesh.primitive_cylinder_add(vertices=v, radius=r, depth=depth, location=loc, rotation=rot)
    else:
        bpy.ops.mesh.primitive_cone_add(vertices=v, radius1=r, radius2=r2, depth=depth, location=loc, rotation=rot)
    o = bpy.context.object
    o = _fin(o, m, bevel, smooth)
    if smooth:
        # keep caps flat-shaded via auto smooth by angle (Blender 5: "Smooth by Angle" modifier-less approach)
        for p in o.data.polygons:
            p.use_smooth = abs(p.normal.y) < 0.9 if rot == Y else abs(p.normal.x) < 0.9 if rot == X else abs(p.normal.z) < 0.9
    return o


def box(size, loc, m, rot=(0, 0, 0), bevel=0.003):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.object
    o.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return _fin(o, m, bevel)


def torus(R, r, loc, m, rot=(0, 0, 0), seg=16):
    bpy.ops.mesh.primitive_torus_add(major_radius=R, minor_radius=r, major_segments=seg, minor_segments=6, location=loc, rotation=rot)
    return _fin(bpy.context.object, m, 0, True)


def uvsphere(r, loc, m, scale=(1, 1, 1), seg=16):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=8, radius=r, location=loc)
    o = bpy.context.object
    o.scale = scale
    bpy.ops.object.transform_apply(scale=True)
    return _fin(o, m, 0, True)


def rod(p0, p1, r, m, v=6):
    a, b = Vector(p0), Vector(p1)
    d = b - a
    bpy.ops.mesh.primitive_cylinder_add(vertices=v, radius=r, depth=d.length, location=(a + b) / 2)
    o = bpy.context.object
    o.rotation_mode = "QUATERNION"
    o.rotation_quaternion = d.to_track_quat("Z", "Y")
    return _fin(o, m, 0, True)


def join(objs, name, parent=None):
    for o in objs:
        for md in list(o.modifiers):
            bpy.context.view_layer.objects.active = o
            bpy.ops.object.modifier_apply(modifier=md.name)
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    o = bpy.context.object
    o.name = name; o.data.name = name
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    if parent is not None:
        o.parent = parent
    return o


def root(name):
    e = bpy.data.objects.new(name, None)
    bpy.context.scene.collection.objects.link(e)
    return e


def new_scene(name):
    s = bpy.data.scenes.new(name)
    bpy.context.window_manager.windows[0].scene = s if bpy.context.window_manager.windows else None
    return s


SCENES = {}


def scene(name):
    s = bpy.data.scenes.new(name)
    SCENES[name] = s
    bpy.context.window.scene = s
    return s


def fins(n, y, span, chord, r0, m, thick=0.003, phase=45, sweep=0.0):
    out = []
    for i in range(n):
        a = D(phase + 360 * i / n)
        c = Vector((math.cos(a), 0, math.sin(a)))
        o = box((span, chord, thick), tuple(Vector((0, y, 0)) + c * (r0 + span / 2)), m, rot=(0, -a, 0), bevel=0.0)
        out.append(o)
    return out


# ================================================================== RPG-7 (launcher, PG-7VL loaded)
def pg7_warhead(y0, parts, m="OliveDark"):
    """PG-7VL warhead whose base is at y0 (forward = -Y)."""
    parts.append(cyl(0.020, 0.06, (0, y0 - 0.03, 0), "OliveDark", r2=0.0465, v=24))      # transition cone (rear of warhead)
    parts.append(cyl(0.0465, 0.12, (0, y0 - 0.12, 0), m, v=24))                             # warhead body
    parts.append(cyl(0.0470, 0.012, (0, y0 - 0.075, 0), "Black", v=24, smooth=False))      # black band
    parts.append(cyl(0.0465, 0.13, (0, y0 - 0.245, 0), m, r2=0.013, v=24))                  # ogive
    parts.append(cyl(0.009, 0.05, (0, y0 - 0.335, 0), "Gunmetal", v=10))                   # piezo fuze spike
    parts.append(cyl(0.012, 0.012, (0, y0 - 0.365, 0), "Gunmetal", v=10, r2=0.005))


def rpg7():
    scene("RPG7")
    r = root("RPG7")
    P = []
    # tube 40 mm, 0.95 m: muzzle at y=-0.55, breech at y=+0.40
    P.append(cyl(0.021, 0.95, (0, -0.075, 0), "Gunmetal", v=20))
    P.append(cyl(0.024, 0.03, (0, -0.54, 0), "Gunmetal", v=20, smooth=False))             # muzzle ring
    P.append(cyl(0.022, 0.16, (0, 0.48, 0), "Gunmetal", r2=0.048, v=24))                   # breech blast cone
    P.append(cyl(0.049, 0.01, (0, 0.565, 0), "Gunmetal", v=24, smooth=False))
    for y in (-0.06, 0.13):                                                                  # wooden heat shields (split halves)
        P.append(cyl(0.033, 0.17, (0, y, 0), "Wood", v=20))
        P.append(cyl(0.034, 0.012, (0, y - 0.09, 0), "Gunmetal", v=20, smooth=False))     # clamp bands
        P.append(cyl(0.034, 0.012, (0, y + 0.09, 0), "Gunmetal", v=20, smooth=False))
    # pistol grip + trigger mechanism (under the front shield) and the rear grip
    P.append(box((0.022, 0.06, 0.03), (0, -0.16, -0.035), "Gunmetal"))
    P.append(box((0.026, 0.035, 0.10), (0, -0.15, -0.09), "Black", rot=(D(-14), 0, 0), bevel=0.006))
    P.append(torus(0.022, 0.003, (0, -0.19, -0.06), "Gunmetal", rot=(0, D(90), 0)))
    P.append(box((0.004, 0.006, 0.02), (0, -0.19, -0.055), "Gunmetal"))                     # trigger
    P.append(box((0.026, 0.035, 0.09), (0, 0.04, -0.08), "Wood", rot=(D(-8), 0, 0), bevel=0.006))
    P.append(box((0.02, 0.05, 0.02), (0, 0.04, -0.03), "Gunmetal"))
    # iron sights: front post + rear leaf with range ladder
    P.append(box((0.006, 0.008, 0.045), (0, -0.48, 0.04), "Gunmetal"))
    P.append(box((0.03, 0.006, 0.03), (0, -0.20, 0.035), "Gunmetal"))
    P.append(box((0.008, 0.006, 0.012), (0, -0.20, 0.054), "Gunmetal"))
    # PGO-7 optical sight on the left (+X) with bracket, eyecup and objective
    P.append(box((0.016, 0.05, 0.03), (0.03, -0.10, 0.02), "Gunmetal"))
    P.append(box((0.036, 0.13, 0.05), (0.06, -0.10, 0.045), "Black", bevel=0.006))
    P.append(cyl(0.017, 0.05, (0.06, -0.01, 0.05), "Rubber", v=16))
    P.append(cyl(0.020, 0.02, (0.06, 0.02, 0.05), "Rubber", v=16, r2=0.024))
    P.append(cyl(0.019, 0.025, (0.06, -0.175, 0.05), "Black", v=16))
    P.append(cyl(0.016, 0.004, (0.06, -0.19, 0.05), "Glass", v=16, smooth=False))
    P.append(cyl(0.008, 0.02, (0.06, -0.10, 0.075), "Black", rot=(0, 0, 0), v=10))         # adjustment turret
    # sling swivels
    P.append(torus(0.012, 0.0025, (0.0, -0.40, -0.028), "Gunmetal", rot=(0, D(90), 0), seg=10))
    P.append(torus(0.012, 0.0025, (0.0, 0.30, -0.028), "Gunmetal", rot=(0, D(90), 0), seg=10))
    join(P, "RPG7_Body", r)
    W = []
    pg7_warhead(-0.555, W)
    join(W, "RPG7_Rocket", r)


def pg7v():
    scene("PG7V")
    r = root("PG7V")
    P = []
    # complete round: warhead forward, sustainer motor and folding fins behind (base at y=0)
    pg7_warhead(-0.20, P)
    P.append(cyl(0.020, 0.40, (0, 0.0, 0), "OliveDark", v=20))
    P.append(cyl(0.024, 0.18, (0, 0.29, 0), "Gunmetal", v=20))                             # booster charge tube
    P.append(cyl(0.012, 0.06, (0, 0.40, 0), "Gunmetal", v=12, r2=0.018))                   # nozzle
    P += fins(4, 0.36, 0.06, 0.05, 0.022, "Gunmetal", thick=0.003)
    join(P, "PG7V_Body", r)


# ================================================================== 9K38 IGLA (MANPADS)
def igla_nose(y0, P, tube=True):
    """Seeker dome + aerospike 'tripod' (nose at y0, forward -Y)."""
    P.append(cyl(0.036, 0.12, (0, y0 + 0.08, 0), "OliveDark", r2=0.026, v=24))
    P.append(uvsphere(0.026, (0, y0 + 0.02, 0), "Glass", scale=(1, 0.8, 1)))
    tip = (0, y0 - 0.12, 0)
    for i in range(3):
        a = D(90 + 120 * i)
        P.append(rod((0.018 * math.cos(a), y0 + 0.01, 0.018 * math.sin(a)), tip, 0.0025, "Gunmetal"))
    P.append(cyl(0.004, 0.03, (0, y0 - 0.12, 0), "Gunmetal", v=8))


def igla():
    scene("IGLA")
    r = root("IGLA")
    P = []
    P.append(cyl(0.040, 1.50, (0, -0.10, 0), "Olive", v=24))                                # launch tube 1.5 m (72 mm missile)
    for y in (-0.80, -0.55, 0.20, 0.55):
        P.append(cyl(0.042, 0.02, (0, y, 0), "OliveDark", v=24, smooth=False))             # reinforcing rings
    P.append(cyl(0.040, 0.08, (0, 0.69, 0), "OliveDark", r2=0.05, v=24))                   # rear flare
    P.append(cyl(0.046, 0.04, (0, -0.86, 0), "OliveDark", v=24))                           # front ring (cap removed)
    igla_nose(-0.88, P)
    # gripstock (trigger mechanism) under the tube with the ground power source (НИП) cylinder in front
    P.append(box((0.04, 0.22, 0.04), (0, -0.05, -0.055), "Black", bevel=0.006))
    P.append(box((0.03, 0.04, 0.11), (0, 0.02, -0.12), "Black", rot=(D(-15), 0, 0), bevel=0.008))
    P.append(torus(0.024, 0.003, (0, -0.02, -0.09), "Black", rot=(0, D(90), 0)))
    P.append(box((0.004, 0.008, 0.02), (0, -0.025, -0.083), "Gunmetal"))
    P.append(cyl(0.030, 0.17, (0, -0.24, -0.075), "OliveDark", v=16))                      # battery/coolant unit
    P.append(cyl(0.031, 0.02, (0, -0.33, -0.075), "Black", v=16, smooth=False))
    # sights: folding front frame + rear peep on the left
    P.append(box((0.004, 0.004, 0.07), (0.048, -0.36, 0.06), "Black", bevel=0))
    P.append(box((0.05, 0.004, 0.004), (0.07, -0.36, 0.095), "Black", bevel=0))
    P.append(box((0.004, 0.004, 0.07), (0.095, -0.36, 0.06), "Black", bevel=0))
    P.append(box((0.05, 0.004, 0.004), (0.07, -0.36, 0.025), "Black", bevel=0))
    P.append(box((0.02, 0.02, 0.05), (0.055, 0.08, 0.045), "Black"))
    P.append(torus(0.008, 0.002, (0.06, 0.08, 0.075), "Black", rot=(D(90), 0, 0), seg=10))
    # IFF interrogator antenna (folded) + shoulder rest + carry strap anchors
    P.append(box((0.012, 0.18, 0.012), (-0.045, -0.15, 0.03), "OliveDark"))
    P.append(box((0.06, 0.12, 0.03), (0, 0.42, -0.05), "Rubber", bevel=0.008))
    P.append(torus(0.012, 0.003, (0, -0.7, 0.042), "Gunmetal", rot=(0, D(90), 0), seg=10))
    P.append(torus(0.012, 0.003, (0, 0.5, 0.042), "Gunmetal", rot=(0, D(90), 0), seg=10))
    join(P, "IGLA_Body", r)


def igla_missile():
    scene("IGLA_M")
    r = root("IGLA_M")
    P = []
    P.append(cyl(0.036, 1.20, (0, 0.0, 0), "OliveDark", v=20))
    igla_nose(-0.70, P)
    P += fins(4, -0.48, 0.03, 0.03, 0.036, "Black", phase=0)                                # canards
    P += fins(4, 0.56, 0.055, 0.07, 0.036, "Black", phase=45)                               # tail fins
    P.append(cyl(0.028, 0.05, (0, 0.62, 0), "Gunmetal", v=16, r2=0.022))
    P.append(cyl(0.037, 0.02, (0, -0.25, 0), "Black", v=20, smooth=False))
    join(P, "IGLA_M_Body", r)


# ================================================================== FGM-148 JAVELIN
def javelin():
    scene("JAVELIN")
    r = root("JAVELIN")
    P = []
    P.append(cyl(0.072, 1.10, (0, -0.10, 0), "Olive", v=28))                                # launch tube (127 mm)
    P.append(cyl(0.088, 0.09, (0, -0.68, 0), "Rubber", v=28))                               # front end cap/bumper
    P.append(cyl(0.088, 0.09, (0, 0.48, 0), "Rubber", v=28))                                # rear cap
    P.append(cyl(0.070, 0.004, (0, -0.727, 0), "Black", v=28, smooth=False))
    for y in (-0.35, 0.15):
        P.append(cyl(0.075, 0.03, (0, y, 0), "OliveDark", v=28, smooth=False))
    P.append(cyl(0.040, 0.16, (-0.10, -0.50, -0.02), "OliveDark", v=16))                  # BCU (battery coolant unit)
    # Command Launch Unit on the left (+X): housing, day/IR optics, eyepiece, handles with triggers
    P.append(box((0.03, 0.10, 0.04), (0.085, -0.12, 0.0), "Black"))                         # mount
    P.append(box((0.20, 0.30, 0.18), (0.20, -0.12, 0.03), "Tan", bevel=0.012))
    P.append(box((0.18, 0.04, 0.13), (0.20, -0.29, 0.04), "Tan", bevel=0.01))
    P.append(cyl(0.045, 0.05, (0.16, -0.32, 0.06), "Black", v=20))
    P.append(cyl(0.040, 0.003, (0.16, -0.346, 0.06), "Glass", v=20, smooth=False))
    P.append(cyl(0.028, 0.04, (0.25, -0.32, 0.075), "Black", v=16))
    P.append(cyl(0.024, 0.003, (0.25, -0.341, 0.075), "Glass", v=16, smooth=False))
    P.append(cyl(0.032, 0.06, (0.20, 0.06, 0.06), "Rubber", v=16))                          # eyepiece
    P.append(cyl(0.040, 0.03, (0.20, 0.10, 0.06), "Rubber", v=16, r2=0.046))
    for x in (0.08, 0.32):
        P.append(box((0.03, 0.04, 0.15), (x, -0.08, -0.03), "Black", rot=(D(-10), 0, 0), bevel=0.008))
        P.append(box((0.008, 0.012, 0.016), (x, -0.105, 0.03), "Gunmetal"))
    P.append(box((0.06, 0.08, 0.02), (0.20, -0.12, 0.13), "Black"))                         # top controls
    # shoulder pad, front grip, strap
    P.append(box((0.08, 0.16, 0.04), (0.0, 0.26, -0.085), "Rubber", bevel=0.01))
    P.append(box((0.03, 0.04, 0.12), (0, -0.45, -0.12), "Black", rot=(D(-5), 0, 0), bevel=0.008))
    P.append(torus(0.018, 0.004, (0, -0.60, 0.075), "Gunmetal", rot=(0, D(90), 0), seg=10))
    P.append(torus(0.018, 0.004, (0, 0.40, 0.075), "Gunmetal", rot=(0, D(90), 0), seg=10))
    join(P, "JAVELIN_Body", r)


def javelin_missile():
    scene("JAVELIN_M")
    r = root("JAVELIN_M")
    P = []
    P.append(cyl(0.0635, 0.95, (0, 0.0, 0), "OliveDark", v=24))
    P.append(cyl(0.0635, 0.10, (0, -0.525, 0), "OliveDark", r2=0.05, v=24))
    P.append(uvsphere(0.05, (0, -0.575, 0), "Glass", scale=(1, 0.7, 1)))
    P += fins(8, -0.05, 0.075, 0.16, 0.0635, "Black", phase=22.5)                          # mid-body wings
    P += fins(4, 0.43, 0.06, 0.06, 0.0635, "Black", phase=45)                              # tail control fins
    P.append(cyl(0.03, 0.04, (0, 0.495, 0), "Gunmetal", v=16, r2=0.04))
    P.append(cyl(0.064, 0.02, (0, -0.30, 0), "Black", v=24, smooth=False))
    join(P, "JAVELIN_M_Body", r)


for fn in (rpg7, pg7v, igla, igla_missile, javelin, javelin_missile):
    fn()

OUTN = {"RPG7": "rpg7", "PG7V": "pg7v", "IGLA": "igla", "IGLA_M": "igla_missile", "JAVELIN": "javelin", "JAVELIN_M": "javelin_missile"}
for sname, fname in OUTN.items():
    sc = SCENES[sname]
    bpy.context.window.scene = sc
    tris = 0
    for o in sc.objects:
        if o.type == "MESH":
            tris += sum(len(p.vertices) - 2 for p in o.data.polygons)
    path = os.path.join(GLB, fname + ".glb")
    with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
        bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", export_yup=True, export_apply=True, export_animations=False,
                                  export_extras=False, export_cameras=False, export_lights=False, export_image_format="AUTO",
                                  use_visible=False, use_selection=False, use_active_scene=True)
    print("HAND", fname, tris, os.path.getsize(path))

# drop the default empty "Scene" so the .blend opens on the RPG-7
for s in list(bpy.data.scenes):
    if s.name not in SCENES:
        bpy.data.scenes.remove(s)
bpy.context.window.scene = SCENES["RPG7"]
for img in bpy.data.images:
    if not img.packed_file:
        img.pack()
bpy.ops.wm.save_as_mainfile(filepath=ROOT + "/models/handheld.blend", compress=True)
print("HAND_DONE")
