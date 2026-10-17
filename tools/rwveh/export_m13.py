# Flying M-13 rocket model for the RocketRenderer: the BM-13 reload rocket (rocket_load), centred, nose +Z (glTF).
# usage: blender -b models/rw_bm13.blend --python tools/rwveh/export_m13.py -- out.glb
import bpy, sys, mathutils
out = sys.argv[sys.argv.index("--") + 1]
pv = bpy.data.objects["rocket_load"]
geo = [o for o in bpy.data.objects if o.type == 'MESH' and o.parent == pv]
bpy.context.view_layer.update()
rel = [(o, pv.matrix_world.inverted() @ o.matrix_world) for o in geo]
for o in list(bpy.data.objects):
    if o not in geo:
        bpy.data.objects.remove(o, do_unlink=True)
ys = []
for o, m in rel:
    o.parent = None
    o.matrix_world = m
    ys += [(m @ mathutils.Vector(c)).y for c in o.bound_box]
c = (min(ys) + max(ys)) / 2
for o, m in rel:
    o.matrix_world = mathutils.Matrix.Translation((0, -c, 0)) @ m
print("M13 length %.3f" % (max(ys) - min(ys)), "objects", len(geo))
bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', export_animations=False, export_apply=True)
