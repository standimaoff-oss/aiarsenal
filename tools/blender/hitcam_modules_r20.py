# r20 (msg 20): small internal-module props for the War Thunder style hit camera and the top-down damage card.
# Blender only (run: blender -b --factory-startup --python tools/blender/hitcam_modules_r20.py).
# Every prop is one root object, unit-ish size, origin at its centre, single white material (tinted in game):
#   mod_engine - V-12 tank diesel: crankcase, two cylinder banks at +-30 deg with 6 heads each, intake, flywheel, oil pan
#   mod_round  - one fixed / separate round lying along +Z: brass case + projectile with ogive, length 1
#   mod_crew   - seated crewman facing +Z: torso, head, helmet rim, thighs, shins, arms forward; hip at origin
#   mod_drive  - turret traverse drive: electric motor + gearbox + pinion
# Output: models/hitcam_modules.blend (+ assets/aiarsenal/models/glb/hitcam_modules.glb)
import bpy, math, os
from mathutils import Vector

OUT_BLEND = "/home/user/models/hitcam_modules.blend"
OUT_GLB = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb/hitcam_modules.glb"

bpy.ops.wm.read_factory_settings(use_empty=True)
mat = bpy.data.materials.new("M_Module")
mat.use_nodes = True
mat.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (1, 1, 1, 1)
mat.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.7


def _fin(o, name):
    o.name = name
    o.data.materials.clear()
    o.data.materials.append(mat)
    return o


def box(name, size, loc, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return _fin(o, name)


def cyl(name, r, depth, loc, rot=(0, 0, 0), seg=12):
    bpy.ops.mesh.primitive_cylinder_add(vertices=seg, radius=r, depth=depth, location=loc, rotation=rot)
    return _fin(bpy.context.active_object, name)


def cone(name, r1, r2, depth, loc, rot=(0, 0, 0), seg=12):
    bpy.ops.mesh.primitive_cone_add(vertices=seg, radius1=r1, radius2=r2, depth=depth, location=loc, rotation=rot)
    return _fin(bpy.context.active_object, name)


def sphere(name, r, loc, seg=10):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=seg // 2 + 1, radius=r, location=loc)
    return _fin(bpy.context.active_object, name)


def join(name, objs):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    o = bpy.context.active_object
    o.name = name
    o.data.name = name
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)   # the game draws the bare mesh
    bpy.ops.object.origin_set(type="ORIGIN_GEOMETRY", center="BOUNDS")
    o.location = (0, 0, 0)
    bpy.ops.object.shade_flat()
    return o


# Blender axes: X right, Y forward (exported as glTF -Z ... we export +Y up, so Blender -Y becomes glTF +Z).
# Props are built "forward = -Y" so that glTF +Z is the prop's forward.
# ------------------------------------------------------------------ engine (1.0 long, 0.8 wide, 0.62 tall)
parts = [box("e_case", (0.46, 1.0, 0.26), (0, 0, 0.13))]
parts.append(box("e_pan", (0.36, 0.86, 0.10), (0, 0, -0.04)))
for sx in (1, -1):
    a = sx * math.radians(30)
    bank = box("e_bank", (0.20, 0.94, 0.30), (sx * 0.15, 0, 0.36), (0, a, 0))
    parts.append(bank)
    for c in range(6):
        y = -0.39 + c * 0.156
        parts.append(box("e_head", (0.17, 0.13, 0.06), (sx * 0.245, y, 0.53), (0, a, 0)))
    parts.append(cyl("e_exh", 0.035, 0.96, (sx * 0.34, 0, 0.40), (math.pi / 2, 0, 0), seg=8))
parts.append(box("e_intake", (0.10, 0.90, 0.10), (0, 0, 0.48)))
parts.append(cyl("e_fly", 0.24, 0.07, (0, 0.53, 0.14), (math.pi / 2, 0, 0), seg=16))
parts.append(cyl("e_pulley", 0.09, 0.05, (0, -0.53, 0.20), (math.pi / 2, 0, 0), seg=10))
eng = join("mod_engine", parts)

# ------------------------------------------------------------------ round (along forward, length 1, r 0.075)
parts = [cyl("r_case", 0.078, 0.52, (0, 0.24, 0), (math.pi / 2, 0, 0), seg=12)]
parts.append(cyl("r_rim", 0.088, 0.03, (0, 0.495, 0), (math.pi / 2, 0, 0), seg=12))
parts.append(cyl("r_body", 0.068, 0.24, (0, -0.14, 0), (math.pi / 2, 0, 0), seg=12))
parts.append(cyl("r_band", 0.074, 0.03, (0, -0.04, 0), (math.pi / 2, 0, 0), seg=12))
parts.append(cone("r_ogive", 0.068, 0.012, 0.24, (0, -0.38, 0), (math.pi / 2, 0, 0), seg=12))
rnd = join("mod_round", parts)

# ------------------------------------------------------------------ seated crewman (faces forward, ~1.0 tall)
parts = [box("c_torso", (0.36, 0.22, 0.50), (0, 0.02, 0.27))]
parts.append(sphere("c_head", 0.11, (0, 0.0, 0.66)))
parts.append(cyl("c_helmet", 0.13, 0.05, (0, 0.01, 0.70), seg=12))
for sx in (1, -1):
    parts.append(box("c_thigh", (0.13, 0.42, 0.14), (sx * 0.10, -0.19, 0.0)))
    parts.append(box("c_shin", (0.12, 0.13, 0.42), (sx * 0.10, -0.40, -0.21)))
    parts.append(box("c_foot", (0.11, 0.22, 0.07), (sx * 0.10, -0.47, -0.42)))
    parts.append(box("c_uarm", (0.09, 0.10, 0.30), (sx * 0.23, 0.0, 0.33)))
    parts.append(box("c_farm", (0.08, 0.30, 0.08), (sx * 0.23, -0.14, 0.19)))
crew = join("mod_crew", parts)

# ------------------------------------------------------------------ turret drive (0.5 long)
parts = [cyl("d_motor", 0.10, 0.30, (0, 0.10, 0.10), (math.pi / 2, 0, 0), seg=14)]
parts.append(box("d_gear", (0.24, 0.16, 0.24), (0, -0.12, 0.10)))
parts.append(cyl("d_pinion", 0.07, 0.16, (0, -0.12, -0.08), seg=12))
parts.append(cyl("d_cap", 0.11, 0.03, (0, 0.26, 0.10), (math.pi / 2, 0, 0), seg=14))
drv = join("mod_drive", parts)

for i, o in enumerate((eng, rnd, crew, drv)):      # side by side in the .blend for a readable file
    o.location = (i * 1.4 - 2.1, 0, 0)
os.makedirs(os.path.dirname(OUT_GLB), exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=OUT_BLEND)
for o in (eng, rnd, crew, drv):                    # export at the origin (the game positions every prop itself)
    o.location = (0, 0, 0)
bpy.ops.export_scene.gltf(filepath=OUT_GLB, export_format="GLB", export_yup=True, export_apply=True,
                          export_normals=True, export_texcoords=False, export_materials="EXPORT", export_animations=False)
for o in (eng, rnd, crew, drv):
    print("PROP", o.name, len(o.data.vertices), "verts", tuple(round(v, 2) for v in o.dimensions))
print("HITCAM_MODULES_OK", os.path.getsize(OUT_GLB))
