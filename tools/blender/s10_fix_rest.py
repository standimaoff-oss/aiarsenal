# STEP 10 (headless patch of the saved .blend): canonical rest pose + marker heights, re-export GLB, re-save.
# blender -b --factory-startup /home/user/models/bm21_grad.blend --python s10_fix_rest.py
import bpy, os
from mathutils import Vector
sc = bpy.data.scenes["BM21_Grad"]
for n in ("Launcher_Pack", "Wheel_FL", "Wheel_FR"):
    bpy.data.objects[n].rotation_euler = (0, 0, 0)
SWAY_Z = 1.0
want = {"Seat_Driver": (0.48, -1.15, 1.30), "Seat_Commander": (-0.48, -1.15, 1.30), "FX_Exhaust": (-1.12, 1.12, 0.64),
        "FX_Headlight_L": (0.86, -3.30, 1.38), "FX_Headlight_R": (-0.86, -3.30, 1.38)}
for n, w in want.items():
    o = bpy.data.objects[n]
    o.location = Vector(w) - Vector((0, 0, SWAY_Z))
bpy.context.view_layer.update()
for n in want:
    print(n, tuple(round(v, 3) for v in bpy.data.objects[n].matrix_world.translation))
OUT = "/home/user/mods/bm21grad/src/main/resources/assets/bm21grad/glb/bm21.glb"
win = bpy.context.window
with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
    print("export scene:", bpy.context.scene.name)
    sc.frame_set(0)
    bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', export_yup=True, export_apply=True,
                              export_animations=True, export_animation_mode='NLA_TRACKS', export_force_sampling=True,
                              export_vertex_color='ACTIVE', export_extras=False, export_cameras=False, export_lights=False,
                              export_image_format='AUTO', use_visible=False, use_selection=False, use_active_scene=True)
print("GLB bytes:", os.path.getsize(OUT))
for n in ("Launcher_Pack", "Wheel_FL", "Wheel_FR"):
    bpy.data.objects[n].rotation_euler = (0, 0, 0)
for n, w in want.items():
    bpy.data.objects[n].location = Vector(w) - Vector((0, 0, SWAY_Z))
bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath, compress=True, copy=False)
b1 = bpy.data.filepath + "1"
if os.path.exists(b1):
    os.remove(b1)
print("saved", bpy.data.filepath, os.path.getsize(bpy.data.filepath))
