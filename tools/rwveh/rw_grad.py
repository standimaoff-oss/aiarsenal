# Real War "Grad" vehicle <- our detailed BM-21 (models/bm21_grad.blend), re-rigged to the Real War node contract.
# Run: blender -b models/bm21_grad.blend --python tools/rwveh/rw_grad.py
# Contract (glTF, used by MlrsEntity / LauncherVehicle / VehicleEntity):
#   root > wheel_0..5 (spin X, 0/1 also steer Y), launcher_yaw (rot Y) > launcher_pitch (rot X) > plug_0..39 (rocket in tube k,
#   hidden when empty), rocket_load (reload rocket: tail at origin, nose +Z), jack_0/1 (translate -Y)
import bpy, bmesh, math, os
from mathutils import Vector, Matrix

D = bpy.data
for a in list(D.actions):
    D.actions.remove(a)
for o in D.objects:
    if o.animation_data:
        o.animation_data_clear()


def kill(ob):
    for c in list(ob.children):
        kill(c)
    D.objects.remove(ob, do_unlink=True)


kill(D.objects["Rocket"])
root = D.objects["BM21"]
root.name = "root"
bpy.context.view_layer.update()


def reparent(ob, par):
    mw = ob.matrix_world.copy()
    ob.parent = par
    ob.matrix_world = mw


# wheels: RW order 0 FL(+x) 1 FR 2 ML 3 MR 4 RL 5 RR; one node per wheel (steer + spin on the same node)
for i, n in enumerate(["FL", "FR", "ML", "MR", "RL", "RR"]):
    w = D.objects["Wheel_" + n]
    reparent(w, root)
    w.name = "wheel_%d" % i
for n in ("Steer_FL", "Steer_FR"):
    D.objects.remove(D.objects[n], do_unlink=True)
D.objects["Turret_Yaw"].name = "launcher_yaw"
pitch = D.objects["Launcher_Pitch"]
pitch.name = "launcher_pitch"
for k in range(40):
    D.objects["Rocket_%02d" % (k + 1)].name = "plug_%d" % k
bpy.context.view_layer.update()

# reload rocket: copy of rocket 1, tail (breech end) at the node origin, nose towards glTF +Z (= Blender -Y)
src = D.objects["plug_0"]
me = src.data.copy()
me.name = "rocket_load"
me.transform(src.matrix_world)
ys = [v.co.y for v in me.vertices]
tail = Vector((src.matrix_world.translation.x, min(ys), src.matrix_world.translation.z))
me.transform(Matrix.Translation(-tail))
me.transform(Matrix.Rotation(math.pi, 4, 'Z'))
rl = D.objects.new("rocket_load", me)
src.users_collection[0].objects.link(rl)
rl.matrix_world = Matrix.Translation(pitch.matrix_world.translation)
reparent(rl, pitch)

# hydraulic outriggers at the rear (Real War Grad stabilises on two jacks): fixed housings + moving legs
mats = {m.name: m for m in D.materials}


def add_obj(name, bm, mat_names, parent, loc=(0, 0, 0)):
    me = D.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for mn in mat_names:
        me.materials.append(mats[mn])
    ob = D.objects.new(name, me)
    root.users_collection[0].objects.link(ob)
    ob.location = loc
    bpy.context.view_layer.update()
    reparent(ob, parent)
    for p in me.polygons:
        p.use_smooth = False
    return ob


def bm_box(bm, size, loc, mi=0):
    r = bmesh.ops.create_cube(bm, size=1.0)
    for v in r["verts"]:
        v.co = Vector((v.co.x * size[0] + loc[0], v.co.y * size[1] + loc[1], v.co.z * size[2] + loc[2]))
    for f in {f for v in r["verts"] for f in v.link_faces}:
        f.material_index = mi


def bm_cyl(bm, r, h, loc, mi=0, seg=16):
    res = bmesh.ops.create_cone(bm, cap_ends=True, segments=seg, radius1=r, radius2=r, depth=h)
    for v in res["verts"]:
        v.co += Vector(loc)
    for f in {f for v in res["verts"] for f in v.link_faces}:
        f.material_index = mi


JY, TRAVEL = 3.3, 0.5
hb = bmesh.new()
for sx in (1, -1):
    x = 0.95 * sx
    bm_box(hb, (0.26, 0.26, 0.62), (x, JY, 0.92), 0)                    # housing
    bm_box(hb, (0.55, 0.14, 0.12), (0.62 * sx, JY, 1.12), 0)            # bracket to the frame
    bm_box(hb, (0.30, 0.30, 0.05), (x, JY, 1.25), 1)                    # cap plate
    bm_cyl(hb, 0.06, 0.18, (x + 0.1 * sx, JY - 0.1, 1.33), 1, 10)        # hydraulic fitting
add_obj("jack_housings", hb, ["M_Paint_Green", "M_Steel"], root)
for j, sx in enumerate((1, -1)):
    x = 0.95 * sx
    node = D.objects.new("jack_%d" % j, None)
    root.users_collection[0].objects.link(node)
    node.location = (x, JY, 0.0)
    node.parent = root
    lb = bmesh.new()
    bm_cyl(lb, 0.075, 0.75, (0, 0, TRAVEL + 0.42), 0, 14)              # chromed leg (bottom at TRAVEL+0.05)
    bm_cyl(lb, 0.20, 0.05, (0, 0, TRAVEL + 0.03), 1, 18)               # ground pad
    bm_box(lb, (0.10, 0.10, 0.06), (0, 0, TRAVEL + 0.09), 1)
    add_obj("jack_%d_geo" % j, lb, ["M_Steel", "M_Paint_Green"], node, loc=(x, JY, 0.0))

bpy.context.view_layer.update()
for o in D.objects:
    if o.type == 'MESH' and o.data.color_attributes.get("Col"):
        o.data.color_attributes.active_color = o.data.color_attributes.get("Col")

out = "/home/user/models/rw_grad.glb"
sc = bpy.context.scene
with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
    bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', export_yup=True, export_apply=True, export_animations=False,
                              export_vertex_color='ACTIVE', export_extras=False, export_cameras=False, export_lights=False,
                              export_image_format='AUTO', use_visible=False, use_selection=False, use_active_scene=True)
print("GLB", os.path.getsize(out))
for img in D.images:
    if not img.packed_file and img.users:
        img.pack()
sc.name = "RW_Grad"
bpy.ops.wm.save_as_mainfile(filepath="/home/user/models/rw_grad.blend", compress=True, copy=True)
print("BLEND_OK")
