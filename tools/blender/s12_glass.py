# STEP 12 (headless): clearer cab glass (alpha 0.24), re-export GLB in the canonical rest pose, re-save.
import bpy, os
from mathutils import Vector
sc = bpy.data.scenes["BM21_Grad"]
g = bpy.data.materials["M_Glass"]
g.node_tree.nodes.get("Principled BSDF").inputs["Alpha"].default_value = 0.24
g.surface_render_method = 'BLENDED'
SWAY_Z = 1.0
want = {"Seat_Driver": (0.52, -1.02, 1.52), "Seat_Commander": (-0.48, -1.02, 1.52), "FX_Exhaust": (-1.12, 1.12, 0.64),
        "FX_Headlight_L": (0.86, -3.30, 1.38), "FX_Headlight_R": (-0.86, -3.30, 1.38)}
def rest():
    for n in ("Launcher_Pack", "Wheel_FL", "Wheel_FR"):
        bpy.data.objects[n].rotation_euler = (0, 0, 0)
    for n, w in want.items():
        bpy.data.objects[n].location = Vector(w) - Vector((0, 0, SWAY_Z))
rest()
bpy.context.view_layer.update()
OUT = "/home/user/mods/bm21grad/src/main/resources/assets/bm21grad/glb/bm21.glb"
with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
    sc.frame_set(0)
    bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', export_yup=True, export_apply=True,
                              export_animations=True, export_animation_mode='NLA_TRACKS', export_force_sampling=True,
                              export_vertex_color='ACTIVE', export_extras=False, export_cameras=False, export_lights=False,
                              export_image_format='AUTO', use_visible=False, use_selection=False, use_active_scene=True)
print("GLB bytes:", os.path.getsize(OUT))
rest()
bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath, compress=True, copy=False)
b1 = bpy.data.filepath + "1"
if os.path.exists(b1):
    os.remove(b1)
print("saved", bpy.data.filepath, os.path.getsize(bpy.data.filepath))
