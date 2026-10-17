"""QA render of the r16 pieces (doors / hatches / gates / camo) posed half-open, optional MC back-face culling (CULL=1).
   blender -b models/door_blocks.blend --python tools/blender/door_preview.py -- out.png cx cy cz tx ty tz [open01]"""
import bpy, math, sys, os
from mathutils import Matrix, Vector
argv = sys.argv[sys.argv.index("--") + 1:]
out = argv[0]; cam = [float(v) for v in argv[1:7]]; op = float(argv[7]) if len(argv) > 7 else 0.6
scn = bpy.context.scene
SRC = {o.name: o for o in bpy.data.objects}
for o in SRC.values():
    o.hide_render = True
RX = Matrix.Rotation(math.pi / 2, 4, "X"); T = Matrix.Translation
R = lambda deg, ax: Matrix.Rotation(math.radians(deg), 4, ax)
def place(name, bx, by, bz, m=Matrix()):
    o = SRC[name].copy(); scn.collection.objects.link(o); o.hide_render = False
    o.matrix_world = RX @ T((bx, by, bz)) @ m
    return o
piv = {"steel": 0.085, "bunker": 0.10, "bars": 0.09, "wood": 0.08, "lab": 0.085}
for i, k in enumerate(["steel", "bunker", "bars", "wood", "lab"]):
    x = i * 1.6
    place("door_%s_frame" % k, x, 0, 0)
    hx = 0.1 if k == "bunker" else 0.08
    a = 90 * op
    place("door_%s_leaf_l" % k, x, 0, 0, T((hx, 0, piv[k])) @ R(-a, "Y") @ T((-hx, 0, -piv[k])))
    if k == "bunker":
        place("door_bunker_wheel", x, 0, 0, T((hx, 0, piv[k])) @ R(-a, "Y") @ T((-hx, 0, -piv[k])) @ T((0.5, 1.0, 0.1)) @ R(200 * op, "Z") @ T((-0.5, -1.0, -0.1)))
for i, k in enumerate(["steel", "bunker", "grate"]):
    x = 9 + i * 1.5
    place("hatch_%s_frame" % k, x, 0, 0)
    m = T((0, 0.05, 0.05)) @ R(-80 * op, "X") @ T((0, -0.05, -0.05))
    place("hatch_%s_lid" % k, x, 0, 0, m)
    if k == "bunker":
        place("hatch_bunker_wheel", x, 0, 0, m)
place("gate_sliding_static", 0, 0, 4); place("gate_sliding_leaf", 0, 0, 4, T((-2.3 * op, 0, 0)))
place("gate_swing_static", 6, 0, 4)
place("gate_swing_leaf_l", 6, 0, 4, T((0.32, 0, 0.5)) @ R(70 * op, "Y") @ T((-0.32, 0, -0.5)))
place("gate_swing_leaf_r", 6, 0, 4, T((3.68, 0, 0.5)) @ R(-70 * op, "Y") @ T((-3.68, 0, -0.5)))
place("gate_boom_static", 11, 0, 4); place("gate_boom_arm", 11, 0, 4, T((0.5, 0.95, 0.24)) @ R(60 * op, "Z") @ T((-0.5, -0.95, -0.24)))
place("gate_blast_static", 17, 0, 4); place("gate_blast_leaf_l", 17, 0, 4, T((-0.7 * op, 0, 0))); place("gate_blast_leaf_r", 17, 0, 4, T((0.7 * op, 0, 0)))
for z in range(3):
    place("net_post" if z in (0, 2) else "net_side", 0, 0, 8 + z)
for z in range(3):
    place("net_side", 0, 0, 8 + z); place("net_side", 0, 0, 8 + z, T((0.5, 0, 0.5)) @ R(180, "Y") @ T((-0.5, 0, -0.5)))
for x in range(3):
    for z in range(2):
        place("canopy_center", 3 + x, 1, 8 + z)
        if z == 0: place("canopy_fringe", 3 + x, 1, 8 + z)
        if x == 0: place("canopy_fringe", 3 + x, 1, 8 + z, T((0.5, 0, 0.5)) @ R(-90, "Y") @ T((-0.5, 0, -0.5)))
for i in range(3):
    place("bush", 8 + i, 0, 8, T((0.5, 0, 0.5)) @ R(i * 50, "Y") @ T((-0.5, 0, -0.5)))
place("net_item", 12, 0, 8); place("canopy_item", 13.5, 0, 8)
bpy.ops.mesh.primitive_plane_add(size=80, location=(8, -6, 0)); g = bpy.context.object
gm = bpy.data.materials.new("ground"); gm.use_nodes = True
gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.20, 0.26, 0.13, 1); g.data.materials.append(gm)
sun = bpy.data.lights.new("sun", "SUN"); sun.energy = 3.5; sun.angle = 0.1
so = bpy.data.objects.new("sun", sun); scn.collection.objects.link(so); so.rotation_euler = (math.radians(50), math.radians(10), math.radians(200))
scn.world = bpy.data.worlds.new("w"); scn.world.use_nodes = True
scn.world.node_tree.nodes["Background"].inputs[0].default_value = (0.55, 0.65, 0.8, 1); scn.world.node_tree.nodes["Background"].inputs[1].default_value = 0.9
B = lambda x, y, z: Vector((x, -z, y))
c = bpy.data.cameras.new("c"); c.lens = float(os.environ.get("LENS", 32))
co = bpy.data.objects.new("c", c); scn.collection.objects.link(co)
co.location = B(*cam[:3]); d = B(*cam[3:6]) - co.location
co.rotation_euler = d.to_track_quat("-Z", "Y").to_euler(); scn.camera = co
if os.environ.get("CULL"):
    for m in bpy.data.materials:
        if m.name == "ground" or not m.use_nodes: continue
        nt = m.node_tree; outn = next(n for n in nt.nodes if n.type == "OUTPUT_MATERIAL")
        src = outn.inputs["Surface"].links[0].from_socket
        geo = nt.nodes.new("ShaderNodeNewGeometry"); tr = nt.nodes.new("ShaderNodeBsdfTransparent"); mix = nt.nodes.new("ShaderNodeMixShader")
        nt.links.new(geo.outputs["Backfacing"], mix.inputs[0]); nt.links.new(src, mix.inputs[1]); nt.links.new(tr.outputs[0], mix.inputs[2])
        nt.links.new(mix.outputs[0], outn.inputs["Surface"])
scn.render.engine = "CYCLES"; scn.cycles.device = "CPU"; scn.cycles.samples = 20
scn.render.resolution_x, scn.render.resolution_y = int(os.environ.get("RW", 1500)), int(os.environ.get("RH", 800))
scn.render.filepath = out
bpy.ops.render.render(write_still=True)
print("PREVIEW", out)
