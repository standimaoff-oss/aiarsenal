"""Contact sheet of the exported handheld GLBs (imports the GLB files themselves)."""
import bpy, math, sys
from mathutils import Vector
argv = sys.argv[sys.argv.index("--") + 1:]
out = argv[0]; yaw = float(argv[1]) if len(argv) > 1 else 35.0
bpy.ops.wm.read_factory_settings(use_empty=True)
G = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb/"
rows = [("rpg7", "pg7v"), ("igla", "igla_missile"), ("javelin", "javelin_missile")]
for i, (a, b) in enumerate(rows):
    for j, n in enumerate((a, b)):
        before = set(bpy.data.objects)
        bpy.ops.import_scene.gltf(filepath=G + n + ".glb")
        for o in set(bpy.data.objects) - before:
            if o.parent is None:
                o.location = (j * 0.75 - 0.35, 0, -i * 0.42)
sc = bpy.context.scene
sun = bpy.data.lights.new("s", "SUN"); sun.energy = 4
so = bpy.data.objects.new("s", sun); sc.collection.objects.link(so); so.rotation_euler = (math.radians(40), 0, math.radians(30))
fl = bpy.data.lights.new("f", "AREA"); fl.energy = 60; fl.size = 3
fo = bpy.data.objects.new("f", fl); sc.collection.objects.link(fo); fo.location = (2.0, 1.0, 1.5); fo.rotation_euler = (math.radians(60), 0, math.radians(120))
sc.world = bpy.data.worlds.new("w"); sc.world.use_nodes = True
sc.world.node_tree.nodes["Background"].inputs[0].default_value = (0.6, 0.62, 0.66, 1)
cd = bpy.data.cameras.new("c"); cd.type = "ORTHO"; cd.ortho_scale = 2.1
co = bpy.data.objects.new("c", cd); sc.collection.objects.link(co)
y = math.radians(yaw)
d = Vector((math.sin(y) * 0.9, -math.cos(y) * 0.9, 0.45)).normalized()
ctr = Vector((0.02, 0, -0.42))
co.location = ctr + d * 5
co.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
sc.camera = co
sc.render.engine = "CYCLES"; sc.cycles.samples = 24; sc.cycles.device = "CPU"
sc.render.resolution_x, sc.render.resolution_y = 1400, 1000
sc.render.filepath = out
bpy.ops.render.render(write_still=True)
