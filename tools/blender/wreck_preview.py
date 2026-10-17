"""QA render of the r17 wrecks / crates (lid open) / traps, optional MC back-face culling (CULL=1).
   blender -b models/wreck_blocks.blend --python tools/blender/door_preview.py -- out.png cx cy cz tx ty tz [open01]"""
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
import json
CR = json.load(open("/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/block/wreck/wreck_cells.json"))["crates"]
X = 0
for k in ["tank_hull", "tank_turret", "tank_track", "heli_body", "heli_tail", "heli_rotor", "jet_body", "jet_wing", "jet_tail"]:
    w = json.load(open("/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/block/wreck/wreck_cells.json"))["wrecks"][k]["w"]
    place("wreck_" + k, X, 0, 0); X += w + 1
for i, k in enumerate(["ammo", "medical", "military", "weapons", "secret"]):
    place("crate_%s_body" % k, i * 1.5, 0, 4)
    py, pz = CR[k]["pivot_y"], CR[k]["pivot_z"]
    place("crate_%s_lid" % k, i * 1.5, 0, 4, T((0, py, pz)) @ R(-95 * op, "X") @ T((0, -py, -pz)))
for i, k in enumerate(["trap_punji_open", "trap_punji_covered", "trap_spike_strip", "trap_flare", "trap_flare_spent", "trap_ied", "trap_efence_item"]):
    place(k, 8.5 + i * 1.5, 0, 4)
for z in range(3):
    place("trap_efence_post", 19.5, 0, 4 + z); place("trap_efence_side", 19.5, 0, 4 + z, T((0.5, 0, 0.5)) @ R(180, "Y") @ T((-0.5, 0, -0.5)))
    if z: place("trap_efence_side", 19.5, 0, 4 + z)
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
