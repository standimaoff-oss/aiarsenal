# Render 128px item icons of the aircraft and bombs from the delivered .blend files (Cycles CPU, transparent bg).
# usage: blender -b <file.blend> --python icons.py -- <out.png> <yaw_deg> <pitch_deg> [object_name_filter]
import bpy, sys, math, mathutils
argv = sys.argv[sys.argv.index("--") + 1:]
out, yaw, pitch = argv[0], float(argv[1]), float(argv[2])
flt = argv[3] if len(argv) > 3 else None
sc = bpy.context.scene
for ob in sc.objects:
    if ob.type == 'MESH':
        name = ob.name
        def m(n): return n == flt or n.startswith(flt + "_") or n.startswith(flt + ".")
        def anc(o):
            while o is not None:
                if m(o.name): return True
                o = o.parent
            return False
        hide = name.startswith(("Int", "IntR", "Seat", "Ladder", "AB_Flame", "PropDisc", "ab_", "abx_", "pod_", "kh38_", "fab_")) or (flt and not anc(ob))
        a = ob
        while a is not None:
            if any(t in a.name for t in ("Chute", "chute", "Flame", "Plume", "Disc", "_disc", "Cradle", "cradle")): hide = True
            a = a.parent
        ob.hide_render = bool(hide)
for tr in [o.animation_data for o in sc.objects if o.animation_data]:
    for t in tr.nla_tracks: t.mute = True
    tr.action = None
sc.frame_set(0)
vis = [o for o in sc.objects if o.type == 'MESH' and not o.hide_render]
pts = [o.matrix_world @ mathutils.Vector(c) for o in vis for c in o.bound_box]
mn = mathutils.Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
mx = mathutils.Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
ctr, rad = (mn + mx) / 2, (mx - mn).length / 2
cam_d = mathutils.Vector((math.cos(math.radians(pitch)) * math.sin(math.radians(yaw)), -math.cos(math.radians(pitch)) * math.cos(math.radians(yaw)), math.sin(math.radians(pitch))))
cd = bpy.data.cameras.new("IconCam"); cd.type = 'ORTHO'; cd.ortho_scale = rad * 1.95
cam = bpy.data.objects.new("IconCam", cd); sc.collection.objects.link(cam)
cam.location = ctr + cam_d * rad * 4
cam.rotation_euler = (-cam_d).to_track_quat('-Z', 'Y').to_euler()
sc.camera = cam
bpy.context.view_layer.update()
inv = cam.matrix_world.inverted()
lp = [inv @ p for p in pts]
x0, x1 = min(p.x for p in lp), max(p.x for p in lp)
y0, y1 = min(p.y for p in lp), max(p.y for p in lp)
size = max(x1 - x0, y1 - y0) * 1.12
cd.ortho_scale = size
cd.shift_x = (x0 + x1) / 2 / size
cd.shift_y = (y0 + y1) / 2 / size
for o in [o for o in sc.objects if o.type == 'LIGHT']: o.hide_render = True
ld = bpy.data.lights.new("IconSun", 'SUN'); ld.energy = 2.2
lo = bpy.data.objects.new("IconSun", ld); sc.collection.objects.link(lo)
lo.rotation_euler = (math.radians(40), math.radians(10), math.radians(30))
if sc.world is None: sc.world = bpy.data.worlds.new("W")
sc.world.use_nodes = True
bg = sc.world.node_tree.nodes.get("Background")
if bg: bg.inputs[0].default_value = (0.8, 0.85, 0.9, 1); bg.inputs[1].default_value = 0.35
sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.samples = 24
sc.render.film_transparent = True
sc.render.resolution_x = sc.render.resolution_y = 256; sc.render.resolution_percentage = 100
sc.render.image_settings.file_format = 'PNG'; sc.render.image_settings.color_mode = 'RGBA'
sc.view_settings.view_transform = 'Standard'
sc.render.filepath = out
bpy.ops.render.render(write_still=True)
print("ICON_OK", out)
