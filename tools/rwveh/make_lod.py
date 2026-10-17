"""Distance LOD (LOD1) for a vehicle .blend — Blender Decimate, no hand-made geometry.

usage: blender -b model.blend --python tools/rwveh/make_lod.py -- out_raw.glb [scale]

Every unique mesh datablock is decimated once (linked duplicates such as track links stay shared):
  >= 2000 tris -> 30 %, 400..2000 -> 40 %, 80..400 -> 60 %, smaller meshes are kept.
`scale` multiplies the ratios (e.g. 0.8 for very heavy models). The GLB is exported without animations: in game the
LOD nodes take their world matrices from the full model's pose (matched by node name), so only mesh data matters.
"""
import bpy, sys, os

argv = sys.argv[sys.argv.index("--") + 1:]
out = argv[0]
K = float(argv[1]) if len(argv) > 1 else 1.0

sc = bpy.context.scene
sc.frame_set(0)
dg = bpy.context.evaluated_depsgraph_get()
users = {}
for ob in sc.objects:
    if ob.type == 'MESH' and ob.data is not None:
        users.setdefault(ob.data.name, []).append(ob)

before = after = 0
# r9: interiors (Ratte) are only seen from close up - the LOD drops their geometry (node kept for the name match)
for ob in list(sc.objects):
    if ob.type == 'MESH' and 'interior' in ob.name:
        users.pop(ob.data.name, None)
        ob.data = bpy.data.meshes.new(ob.name + "_lod_empty")
for name, obs in users.items():
    me = obs[0].data
    tris = sum(len(p.vertices) - 2 for p in me.polygons)
    before += tris * len(obs)
    if tris >= 2000:
        ratio = 0.30
    elif tris >= 400:
        ratio = 0.40
    elif tris >= 80:
        ratio = 0.60
    elif tris >= 24 and K < 0.7 and len(obs) > 8:
        ratio = 0.75          # strong LOD (K < 0.7): also thin out heavily instanced small parts (track links)
    else:
        after += tris * len(obs)
        continue
    ratio = max(0.08, min(1.0, ratio * K))
    tmp = bpy.data.objects.new("LODTMP", me)
    sc.collection.objects.link(tmp)
    mod = tmp.modifiers.new("dec", 'DECIMATE')
    mod.decimate_type = 'COLLAPSE'
    mod.ratio = ratio
    mod.use_collapse_triangulate = True
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    new = bpy.data.meshes.new_from_object(tmp.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
    new.name = me.name + "_lod1"
    for ob in obs:
        ob.data = new
    bpy.data.objects.remove(tmp)
    t2 = sum(len(p.vertices) - 2 for p in new.polygons)
    after += t2 * len(obs)

os.makedirs(os.path.dirname(out), exist_ok=True)
bpy.ops.object.select_all(action='DESELECT')
with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
    bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', export_yup=True, export_apply=True,
                              export_animations=False, export_vertex_color='ACTIVE', export_extras=False,
                              export_cameras=False, export_lights=False, export_image_format='AUTO',
                              use_visible=False, use_selection=False, use_active_scene=True)
print("LOD1 %s: %d -> %d tris (%.0f %%)" % (os.path.basename(out), before, after, 100.0 * after / max(1, before)))
