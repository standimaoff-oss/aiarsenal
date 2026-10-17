"""QA render of the fortification pieces laid out on a block grid exactly like the multipart blockstates do.
   blender -b models/fortifications.blend --python tools/blender/fort_preview.py -- out.jpg [cam_x cam_y cam_z tgt_x tgt_y tgt_z]
"""
import bpy, math, sys
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
out = argv[0] if argv else "/home/user/shots/fort/fort.jpg"
cam = [float(v) for v in argv[1:7]] if len(argv) >= 7 else [10.0, 5.5, 13.0, 5.0, 0.4, 1.5]

scn = bpy.context.scene
SRC = {o.name: o for o in bpy.data.objects}
for o in SRC.values():
    o.hide_render = True
RX = Matrix.Rotation(math.pi / 2, 4, "X")


def place(name, bx, bz, ry=0, by=0):
    o = SRC[name].copy()
    scn.collection.objects.link(o)
    o.hide_render = False
    m = Matrix.Translation((bx + 0.5, by, bz + 0.5)) @ Matrix.Rotation(math.radians(-ry), 4, "Y") @ Matrix.Translation((-0.5, 0, -0.5))
    o.matrix_world = RX @ m
    return o


def linked(prefix, cells, post=True, single=None):
    s = set(cells)
    for (x, z) in cells:
        sides = [(0, x, z - 1), (90, x + 1, z), (180, x, z + 1), (270, x - 1, z)]
        conn = [r for (r, nx, nz) in sides if (nx, nz) in s]
        if post:
            place(prefix + "_post", x, z)
        if single and not conn:
            place(single, x, z)
        for r in conn:
            place(prefix + "_side", x, z, r)


# sandbag wall with a corner, barbed wire line with a corner, concertina line, hedgehogs, teeth, barriers, camo net canopy
linked("sandbags", [(0, 0), (1, 0), (2, 0), (3, 0), (3, 1), (3, 2)])
place("sandbags_post", 1, 0, by=1); place("sandbags_side", 1, 0, 90, by=1); place("sandbags_side", 1, 0, 270, by=1)
place("sandbags_post", 2, 0, by=1); place("sandbags_side", 2, 0, 270, by=1)
linked("barbed_wire", [(0, 3), (1, 3), (2, 3), (2, 4), (2, 5)])
linked("concertina", [(5, 0), (6, 0), (7, 0), (8, 0)], post=False, single="concertina_single")
place("concertina_single", 5, 2)
for i, (x, z) in enumerate([(5, 4), (7, 4), (6, 5)]):
    place("czech_hedgehog", x, z, ry=90 * i)
for i, x in enumerate(range(9, 12)):
    for j, z in enumerate(range(3, 6)):
        if (i + j) % 2 == 0:
            place("dragon_teeth", x, z, ry=90 * ((i * 3 + j) % 4))
for z in range(0, 3):
    place("concrete_barrier", 10, z, ry=90)
for x in range(4, 7):
    for z in range(-2, 0):
        place("camo_net", x, z, by=2)

# ground
bpy.ops.mesh.primitive_plane_add(size=40, location=(6, -2, 0))
g = bpy.context.object
gm = bpy.data.materials.new("ground"); gm.use_nodes = True
gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.20, 0.26, 0.13, 1)
g.data.materials.append(gm)

sun = bpy.data.lights.new("sun", "SUN"); sun.energy = 3.5; sun.angle = 0.1
so = bpy.data.objects.new("sun", sun); scn.collection.objects.link(so)
so.rotation_euler = (math.radians(50), math.radians(10), math.radians(35))
scn.world = bpy.data.worlds.new("w"); scn.world.use_nodes = True
scn.world.node_tree.nodes["Background"].inputs[0].default_value = (0.55, 0.65, 0.8, 1)
scn.world.node_tree.nodes["Background"].inputs[1].default_value = 0.8

# block space -> Blender: (x, y, z) -> (x, -z, y)
B = lambda x, y, z: Vector((x, -z, y))
c = bpy.data.cameras.new("c"); c.lens = 30
co = bpy.data.objects.new("c", c); scn.collection.objects.link(co)
co.location = B(*cam[:3])
d = B(*cam[3:6]) - co.location
co.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
scn.camera = co
scn.render.engine = "CYCLES"
scn.cycles.device = "CPU"
scn.cycles.samples = 24
scn.render.resolution_x, scn.render.resolution_y = 1400, 800
scn.render.filepath = out
bpy.ops.render.render(write_still=True)
print("PREVIEW", out)
