# Preview render of a RW vehicle .blend (Cycles CPU).  usage:
# blender -b X.blend --python preview.py  (eye=x,y,z  -> first-person camera AT that Blender point looking along yaw/pitch, lens 18)
# blender -b X.blend --python preview.py -- out.jpg yaw pitch [W H] [rotX=node:deg ...] [rotZ=node:deg] [zoom=f] [at=x,y,z]
import bpy, sys, math, mathutils
argv = sys.argv[sys.argv.index("--") + 1:]
out, yaw, pitch = argv[0], float(argv[1]), float(argv[2])
W, H = (int(argv[3]), int(argv[4])) if len(argv) > 4 and argv[3].isdigit() else (960, 540)
sc = bpy.context.scene
zoom, at, eye = 1.0, None, None
for a in argv:
    if a.startswith(("rotX=", "rotZ=", "rotY=")):
        n, d = a[5:].split(":")
        ob = bpy.data.objects[n]
        ob.rotation_euler["XYZ".index(a[3])] += math.radians(float(d))
    if a.startswith("zoom="):
        zoom = float(a[5:])
    if a.startswith("eye="):
        eye = mathutils.Vector([float(v) for v in a[4:].split(",")])
    if a.startswith("at="):
        at = mathutils.Vector([float(v) for v in a[3:].split(",")])
bpy.context.view_layer.update()
vis = [o for o in sc.objects if o.type == 'MESH']
pts = [o.matrix_world @ mathutils.Vector(c) for o in vis for c in o.bound_box]
mn = mathutils.Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
mx = mathutils.Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
ctr, rad = (mn + mx) / 2, (mx - mn).length / 2
if at is not None:
    ctr = at
cam_d = mathutils.Vector((math.cos(math.radians(pitch)) * math.sin(math.radians(yaw)), -math.cos(math.radians(pitch)) * math.cos(math.radians(yaw)), math.sin(math.radians(pitch))))
cd = bpy.data.cameras.new("PCam"); cd.lens = 50
cam = bpy.data.objects.new("PCam", cd); sc.collection.objects.link(cam)
cam.location = ctr + cam_d * rad * 2.2 / zoom
cam.rotation_euler = (-cam_d).to_track_quat('-Z', 'Y').to_euler()
if eye is not None:
    cam.location = eye
    cam.rotation_euler = (-cam_d).to_track_quat('-Z', 'Y').to_euler()
    cd.lens = 16
    pl_ = bpy.data.lights.new("CabFill", 'POINT'); pl_.energy = 60; pl_.shadow_soft_size = 0.5
    po_ = bpy.data.objects.new("CabFill", pl_); sc.collection.objects.link(po_); po_.location = eye + mathutils.Vector((0, 0, 0.05))
sc.camera = cam
bpy.ops.mesh.primitive_plane_add(size=200, location=(0, 0, 0))
gp = bpy.context.active_object
gm = bpy.data.materials.new("Ground"); gm.diffuse_color = (0.35, 0.33, 0.28, 1)
gm.node_tree.nodes["Principled BSDF"].inputs[0].default_value = (0.32, 0.30, 0.25, 1)
gp.data.materials.append(gm)
ld = bpy.data.lights.new("Sun", 'SUN'); ld.energy = 3.0; ld.angle = 0.1
lo = bpy.data.objects.new("Sun", ld); sc.collection.objects.link(lo)
lo.rotation_euler = (math.radians(50), math.radians(15), math.radians(35))
if sc.world is None: sc.world = bpy.data.worlds.new("W")
sc.world.use_nodes = True
bg = sc.world.node_tree.nodes.get("Background")
bg.inputs[0].default_value = (0.65, 0.75, 0.9, 1); bg.inputs[1].default_value = 0.6
sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.samples = 20
sc.cycles.use_denoising = True
sc.render.resolution_x, sc.render.resolution_y = W, H
sc.render.resolution_percentage = 100
sc.render.image_settings.file_format = 'JPEG'; sc.render.image_settings.quality = 88
sc.view_settings.view_transform = 'Standard'
sc.render.filepath = out
bpy.ops.render.render(write_still=True)
print("RENDERED", out)
