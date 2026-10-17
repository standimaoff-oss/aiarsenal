"""QA render of the r14 deco pieces (flag, lamps, searchlight) with optional Minecraft back-face culling (CULL=1).
   blender -b models/deco_blocks.blend --python tools/blender/deco_preview.py -- out.png cx cy cz tx ty tz"""
import bpy, math, sys, os
from mathutils import Matrix, Vector
argv = sys.argv[sys.argv.index("--") + 1:]
out = argv[0]
cam = [float(v) for v in argv[1:7]]
scn = bpy.context.scene
SRC = {o.name: o for o in bpy.data.objects}
for o in SRC.values():
    o.hide_render = True
RX = Matrix.Rotation(math.pi / 2, 4, "X")
T = Matrix.Translation
def place(name, bx, by, bz, ry=0, extra=None):
    o = SRC[name].copy(); scn.collection.objects.link(o); o.hide_render = False
    m = T((bx + 0.5, by, bz + 0.5)) @ Matrix.Rotation(math.radians(-ry), 4, "Y") @ T((-0.5, 0, -0.5))
    if extra is not None:
        m = m @ extra
    o.matrix_world = RX @ m
    return o
for y in range(3):
    place("flagpole_pole", 0, y, 0)
place("flagpole_base", 0, 0, 0)
place("flagpole_pole", 0, 3, 0); place("flagpole_finial", 0, 3, 0); place("flag_fittings", 0, 3, 0)
# cloth: hoist at (0.5, 0.97, 0.5 - 0.07), light wave for the preview
cl = place("flag_cloth", 0, 3, 0, extra=T((0, 0.97, 0.43)) @ T((-0.5, 0, -0.5)) @ T((0.5, 0, 0.5)))
me = cl.data.copy(); cl.data = me
for v in me.vertices:          # object space is block space rotated by RX: block (x, y, z) = (vx, vz, -vy)
    u = max(0.0, -(-v.co.y)) if False else max(0.0, v.co.y)
    v.co.x += math.sin(u * 5.0) * 0.08 * u
place("lantern_hurricane", 2, 0, 0)
place("lantern_hurricane_hanging", 3, 0, 0)
place("lamp_bulkhead", 4, 0, 0)
place("lamp_street", 5, 0, 0)
place("lamp_flood", 6, 0, 0)
place("beacon_red", 7, 0, 0)
place("beacon_rotating", 8, 0, 0); place("beacon_reflector", 8, 0, 0, ry=35)
piv = Vector((0.5, 1.02, 0.5))
yaw, pit = 25, 18
place("searchlight_base", 10, 0, 0)
place("searchlight_yoke", 10, 0, 0, ry=yaw)
place("searchlight_drum", 10, 0, 0, ry=yaw, extra=T(piv) @ Matrix.Rotation(math.radians(pit), 4, "X") @ T(-piv))
bpy.ops.mesh.primitive_plane_add(size=60, location=(6, -2, 0))
g = bpy.context.object
gm = bpy.data.materials.new("ground"); gm.use_nodes = True
gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.20, 0.26, 0.13, 1)
g.data.materials.append(gm)
sun = bpy.data.lights.new("sun", "SUN"); sun.energy = 3.5; sun.angle = 0.1
so = bpy.data.objects.new("sun", sun); scn.collection.objects.link(so)
so.rotation_euler = (math.radians(50), math.radians(10), math.radians(200))
scn.world = bpy.data.worlds.new("w"); scn.world.use_nodes = True
scn.world.node_tree.nodes["Background"].inputs[0].default_value = (0.55, 0.65, 0.8, 1)
scn.world.node_tree.nodes["Background"].inputs[1].default_value = 0.9
B = lambda x, y, z: Vector((x, -z, y))
c = bpy.data.cameras.new("c"); c.lens = 32
co = bpy.data.objects.new("c", c); scn.collection.objects.link(co)
co.location = B(*cam[:3]); d = B(*cam[3:6]) - co.location
co.rotation_euler = d.to_track_quat("-Z", "Y").to_euler(); scn.camera = co
if os.environ.get("CULL"):
    for m in bpy.data.materials:
        if m.name == "ground" or not m.use_nodes or m.name == "cloth":
            continue
        nt = m.node_tree
        outn = next(n for n in nt.nodes if n.type == "OUTPUT_MATERIAL")
        src = outn.inputs["Surface"].links[0].from_socket
        geo = nt.nodes.new("ShaderNodeNewGeometry"); tr = nt.nodes.new("ShaderNodeBsdfTransparent")
        mix = nt.nodes.new("ShaderNodeMixShader")
        nt.links.new(geo.outputs["Backfacing"], mix.inputs[0])
        nt.links.new(src, mix.inputs[1]); nt.links.new(tr.outputs[0], mix.inputs[2])
        nt.links.new(mix.outputs[0], outn.inputs["Surface"])
scn.render.engine = "CYCLES"; scn.cycles.device = "CPU"; scn.cycles.samples = 24
scn.render.resolution_x, scn.render.resolution_y = int(os.environ.get("RW", 1400)), int(os.environ.get("RH", 800))
scn.render.filepath = out
bpy.ops.render.render(write_still=True)
print("PREVIEW", out)
