# Re-export the raw (unquantized) GLB from a saved vehicle/plane .blend after a sandbox reset
# (same glTF settings as alib.export_glb).  usage: blender -b X.blend --python reexport.py -- out.glb
import bpy, sys, os
out = sys.argv[sys.argv.index("--") + 1]
os.makedirs(os.path.dirname(out), exist_ok=True)
sc = bpy.context.scene
sc.frame_set(0)
with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
    bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', export_yup=True, export_apply=True,
                              export_animations=True, export_animation_mode='NLA_TRACKS', export_force_sampling=True,
                              export_vertex_color='ACTIVE', export_extras=False, export_cameras=False, export_lights=False,
                              export_image_format='AUTO', use_visible=False, use_selection=False, use_active_scene=True)
print("REEXPORT", out, os.path.getsize(out))
