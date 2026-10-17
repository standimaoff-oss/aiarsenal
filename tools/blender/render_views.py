# quick Cycles CPU check renders of the saved .blend (no modification saved)
import bpy, math, sys
from mathutils import Vector
sc = bpy.data.scenes["BM21_Grad"]
bpy.context.window.scene = sc if bpy.context.window else None
sc.render.engine = 'CYCLES'
sc.cycles.device = 'CPU'
sc.cycles.samples = 12
sc.cycles.use_denoising = True
sc.render.resolution_x, sc.render.resolution_y = 560, 380
sc.render.resolution_percentage = 100
if sc.world is None:
    sc.world = bpy.data.worlds.new("W")
sc.world.use_nodes = True
bg = sc.world.node_tree.nodes.get("Background")
bg.inputs[0].default_value = (0.75, 0.82, 0.95, 1); bg.inputs[1].default_value = 1.2
sun = bpy.data.lights.new("SunChk", 'SUN'); sun.energy = 3.5
so = bpy.data.objects.new("SunChk", sun); sc.collection.objects.link(so); so.rotation_euler = (math.radians(40), 0, math.radians(30))
cam = bpy.data.cameras.new("CamChk"); co = bpy.data.objects.new("CamChk", cam); sc.collection.objects.link(co); sc.camera = co
views = {
 "int_driver": ((0.52, -0.95, 2.15), (0.45, -3.0, 1.75), 70),
 "int_side":   ((-0.6, -0.9, 2.2), (0.6, -1.5, 1.6), 80),
 "ext_door":   ((3.2, -2.6, 2.4), (0.0, -1.1, 1.8), 45),
 "ext_front":  ((1.2, -5.5, 2.6), (0.0, -1.2, 2.0), 45),
}
for n, (p, t, lens) in views.items():
    co.location = Vector(p)
    d = Vector(t) - Vector(p)
    co.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    cam.lens_unit = 'FOV'; cam.angle = math.radians(lens)
    sc.render.filepath = f"/home/user/shots/tmp/bl_{n}.png"
    bpy.ops.render.render(write_still=True, scene=sc.name)
    print("rendered", n)
