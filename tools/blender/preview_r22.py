"""Preview renders of the r22 weapons: python args after -- : scene names. Workbench, textured, ortho side + 3/4 views."""
import bpy, sys, math
from mathutils import Vector
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else ["AK12", "M4A1", "RPG7"]
out = "/tmp/prev_r22"
import os
os.makedirs(out, exist_ok=True)
for name in [a for a in argv if a != "arms"]:
    sc = bpy.data.scenes[name]
    bpy.context.window.scene = sc
    sc.render.engine = 'BLENDER_WORKBENCH'
    sc.display.shading.light = 'STUDIO'
    sc.display.shading.color_type = 'TEXTURE'
    sc.display.shading.show_cavity = True
    sc.display.shading.show_shadows = False
    sc.render.resolution_x, sc.render.resolution_y = 1400, 700
    sc.render.film_transparent = False
    sc.world = sc.world or bpy.data.worlds.new("w")
    hide_arms = len(argv) and True
    for o in sc.objects:
        if ".arm_" in o.name or o.name.endswith(".flash") or o.name.endswith(".dot"):
            o.hide_render = "arms" not in sys.argv
    mn = Vector((1e9, 1e9, 1e9)); mx = -mn
    for o in sc.objects:
        if o.type == 'MESH' and not o.hide_render:
            for c in o.bound_box:
                w = o.matrix_world @ Vector(c)
                mn = Vector(map(min, mn, w)); mx = Vector(map(max, mx, w))
    ctr = (mn + mx) / 2
    size = max(mx - mn)
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    sc.collection.objects.link(cam)
    sc.camera = cam
    views = {"R": (Vector((-1, 0, 0)), True), "L": (Vector((1, 0, 0)), True), "Q": (Vector((-0.8, 0.9, 0.5)), False),
             "F": (Vector((0.35, 1, 0.12)), False)}
    if "arms" in sys.argv:
        e = bpy.data.objects[name.lower() + ".sight"]
        views = {"P": (None, False), "R": (Vector((-1, 0, 0)), True), "Q": (Vector((-0.8, -0.9, -0.3)), False), "L": (Vector((1, 0.2, -0.2)), False)}
    for tag, (d, ortho) in views.items():
        if d is None:
            bpy.context.view_layer.update()
            eye = e.matrix_world.translation + Vector((-0.10, 0.22, -0.05))
            cam.location = eye
            cam.rotation_euler = Vector((0.10, -1, -0.03)).to_track_quat('-Z', 'Y').to_euler()
            cam.data.type = 'PERSP'
            cam.data.lens = 18
            cam.data.clip_start = 0.01
            sc.render.filepath = f"{out}/{name}_P_arms.png"
            bpy.ops.render.render(write_still=True)
            continue
        d = d.normalized()
        cam.location = ctr + d * size * 1.6
        cam.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
        cam.data.type = 'ORTHO' if ortho else 'PERSP'
        cam.data.ortho_scale = size * 1.05
        cam.data.lens = 50
        cam.data.clip_end = 100
        sc.render.filepath = f"{out}/{name}_{tag}{'_arms' if 'arms' in sys.argv else ''}.png"
        bpy.ops.render.render(write_still=True)
    bpy.data.objects.remove(cam)
print("PREV_OK")
