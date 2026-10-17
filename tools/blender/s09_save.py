# STEP 9: hygiene + packed, compressed save
for sc_ in bpy.data.scenes:
    pass
# remove helper/unused datablocks
for img in list(bpy.data.images):
    if img.users == 0:
        bpy.data.images.remove(img)
bpy.ops.outliner.orphans_purge(do_local_ids=True, do_linked_ids=True, do_recursive=True)
bpy.ops.file.pack_all()
bpy.context.window.scene = bpy.data.scenes["Scene"]
bpy.context.scene.name = "BM21_Grad"
bpy.ops.wm.save_as_mainfile(filepath="/home/user/models/bm21_grad.blend", compress=True, copy=False)
print("saved", os.path.getsize("/home/user/models/bm21_grad.blend"))
