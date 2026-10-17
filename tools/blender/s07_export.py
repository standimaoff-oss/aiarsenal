# STEP 7: export GLB (Y-up, embedded textures, NLA tracks -> animations); restore rest pose afterwards
OUT_GLB = "/home/user/mods/bm21grad/src/main/resources/assets/bm21grad/glb/bm21.glb"
os.makedirs(os.path.dirname(OUT_GLB), exist_ok=True)
# canonical rest pose (guards against preview poses left in the scene)
for _n in ("Launcher_Pack", "Launcher_Pitch", "Turret_Yaw", "Steer_FL", "Steer_FR", "Body_Sway",
           "Wheel_FL", "Wheel_FR", "Wheel_ML", "Wheel_MR", "Wheel_RL", "Wheel_RR"):
    if bpy.data.objects.get(_n):
        bpy.data.objects[_n].rotation_euler = (0, 0, 0)
rest = {o.name: (o.location.copy(), o.rotation_euler.copy()) for o in bpy.data.objects}
bpy.context.scene.frame_set(0)
bpy.ops.object.select_all(action='DESELECT')
bpy.ops.export_scene.gltf(filepath=OUT_GLB, export_format='GLB', export_yup=True, export_apply=True,
                          export_animations=True, export_animation_mode='NLA_TRACKS', export_force_sampling=True,
                          export_vertex_color='ACTIVE', export_extras=False, export_cameras=False, export_lights=False,
                          export_image_format='AUTO', use_visible=False, use_selection=False, use_active_scene=True)
for n, (l, r) in rest.items():
    o = bpy.data.objects.get(n)
    if o:
        o.location = l; o.rotation_euler = r
print("GLB bytes:", os.path.getsize(OUT_GLB))
