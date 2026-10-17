"""QA render of the fortification pieces laid out on a block grid exactly like the multipart blockstates do.
   blender -b models/fortifications.blend --python tools/blender/fort_preview.py -- out.jpg [cam_x cam_y cam_z tgt_x tgt_y tgt_z]
"""
import bpy, math, sys, os
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



# trench: 1-wide trench along x at z=1 (walls at z=0 facing south, z=2 facing north), duckboards, firing step
for x in range(0, 4):
    place("trench_wall", x, 2, ry=0)
    place("trench_wall_tin" if x >= 2 else "trench_wall", x, 0, ry=180)
    place("duckboard", x, 1)
place("firing_step", 4, 1, ry=270)
# drums
place("drum_up", 6, 0); place("drum_up", 7, 0, ry=40); place("drum_up", 6, 1, ry=80); place("drum_side", 7, 1, ry=90); place("drum_side", 8, 0)
# razor wire lines
linked("egoza", [(0, 4), (1, 4), (2, 4), (3, 4)], post=True, single="egoza_single")
linked("bruno", [(0, 6), (1, 6), (2, 6), (3, 6)], post=False, single="bruno_single")
place("bruno_single", 5, 6)
# mines and traps
for i, n in enumerate(["mine_pmn2", "mine_tm62", "mine_mon50", "mine_ozm72", "bear_trap_open", "bear_trap_closed"]):
    place(n, 6 + i % 3, 3 + i // 3)
place("tripwire_f1", 9, 3)
# signs
place("sign_post", 10, 1); place("sign_post", 11, 1, ry=20)
place("sign_wall", 10, 0)
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
if os.environ.get("CULL"):
    # r13: emulate Minecraft back-face culling (Cycles shades both sides): back faces become fully transparent
    for m in bpy.data.materials:
        if m.name == "ground" or not m.use_nodes:
            continue
        nt = m.node_tree
        outn = next(n for n in nt.nodes if n.type == "OUTPUT_MATERIAL")
        src = outn.inputs["Surface"].links[0].from_socket
        geo = nt.nodes.new("ShaderNodeNewGeometry"); tr = nt.nodes.new("ShaderNodeBsdfTransparent")
        mix = nt.nodes.new("ShaderNodeMixShader")
        nt.links.new(geo.outputs["Backfacing"], mix.inputs[0])
        nt.links.new(src, mix.inputs[1]); nt.links.new(tr.outputs[0], mix.inputs[2])
        nt.links.new(mix.outputs[0], outn.inputs["Surface"])
scn.render.engine = "CYCLES"
scn.cycles.device = "CPU"
scn.cycles.samples = 24
scn.render.resolution_x, scn.render.resolution_y = 1400, 800
scn.render.filepath = out
bpy.ops.render.render(write_still=True)
print("PREVIEW", out)
