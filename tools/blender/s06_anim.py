# STEP 6: keyframe animations as NLA tracks -> named glTF animations
# 24 fps. Clip timing contract with the mod (GlbAnimator):
#   wheels_roll      1.0 s  = one full revolution (+X), driven by distance / (2*pi*r)
#   steer            0..2 s : +35deg (left) -> 0 -> -35deg (right), t = 1 + steer
#   turret_traverse  0..4 s : 0 -> 360deg CCW (to the left), t = yawLeftDeg / 90
#   launcher_elevate 0..2.2917 s : 0 -> 55deg elevation, t = deg / 24
#   shields_lower    0..1 s : raised (88deg) -> covering the windscreen
#   fire_recoil      0..0.5 s: pack kick + body rock (one-shot per rocket)
#   engine_idle      0..0.25 s loop: body vibration
sc = bpy.context.scene
sc.render.fps = 24
F = 24.0


def obj(n):
    return bpy.data.objects[n]


def clip(name, ob, path, keys, index=-1, interp='LINEAR'):
    """keys: list of (frame, value) or (frame, tuple)."""
    ad = ob.animation_data_create()
    act = bpy.data.actions.get(name + "_" + ob.name) or bpy.data.actions.new(name + "_" + ob.name)
    ad.action = act
    saved_loc = ob.location.copy()
    saved_rot = ob.rotation_euler.copy()
    for fr, val in keys:
        if path == "rotation_euler":
            if index >= 0:
                r = ob.rotation_euler.copy(); r[index] = val; ob.rotation_euler = r
            else:
                ob.rotation_euler = val
        elif path == "location":
            if index >= 0:
                l = ob.location.copy(); l[index] = val; ob.location = l
            else:
                ob.location = val
        ob.keyframe_insert(path, index=index, frame=fr)
    # interpolation
    try:
        fcs = act.layers[0].strips[0].channelbags[0].fcurves
    except Exception:
        fcs = getattr(act, "fcurves", [])
    for fc in fcs:
        for kp in fc.keyframe_points:
            kp.interpolation = interp
    tr = ad.nla_tracks.new()
    tr.name = name
    tr.strips.new(name, 0, act)
    ad.action = None
    ob.location = saved_loc
    ob.rotation_euler = saved_rot


for o in bpy.data.objects:
    o.rotation_mode = 'XYZ'
    if o.animation_data:
        o.animation_data_clear()
for a in list(bpy.data.actions):
    bpy.data.actions.remove(a)

# wheels_roll: one revolution per second, keys every 90deg
for w in ("Wheel_FL", "Wheel_FR", "Wheel_ML", "Wheel_MR", "Wheel_RL", "Wheel_RR"):
    clip("wheels_roll", obj(w), "rotation_euler", [(k * 6, k * PI / 2) for k in range(5)], index=0)

# steer
for s in ("Steer_FL", "Steer_FR"):
    clip("steer", obj(s), "rotation_euler", [(0, 35 * D2R), (24, 0.0), (48, -35 * D2R)], index=2)

# turret traverse 360
clip("turret_traverse", obj("Turret_Yaw"), "rotation_euler", [(k * 24, k * PI / 2) for k in range(5)], index=2)

# launcher elevation 0..55 deg (1 deg per frame)
clip("launcher_elevate", obj("Launcher_Pitch"), "rotation_euler", [(0, 0.0), (27.5, -27.5 * D2R), (55, -55 * D2R)], index=0)

# shields
clip("shields_lower", obj("Shield_Cab"), "rotation_euler", [(0, 88 * D2R), (8, 70 * D2R), (18, 15 * D2R), (24, 0.0)], index=0, interp='BEZIER')

# fire recoil: pack kicks back along its axis (+Y local), body rocks
pk = obj("Launcher_Pack")
base = pk.location.copy()
clip("fire_recoil", pk, "location", [(0, base.y), (1, base.y + 0.06), (4, base.y - 0.012), (7, base.y + 0.004), (12, base.y)], index=1, interp='BEZIER')
sw = obj("Body_Sway")
clip("fire_recoil", sw, "rotation_euler", [(0, 0.0), (2, -0.8 * D2R), (6, 0.35 * D2R), (10, -0.1 * D2R), (12, 0.0)], index=0, interp='BEZIER')

# engine idle vibration (loop)
z0 = sw.location.z
clip("engine_idle", sw, "location", [(0, z0), (1.5, z0 + 0.004), (3, z0 - 0.002), (4.5, z0 + 0.003), (6, z0)], index=2, interp='BEZIER')

# strips must not influence the rest pose outside their range -> rest pose == modelled pose
for o in bpy.data.objects:
    if o.animation_data:
        for tr in o.animation_data.nla_tracks:
            for st in tr.strips:
                st.extrapolation = 'NOTHING'
            tr.mute = True
sc.frame_set(0)
print("actions:", len(bpy.data.actions), [t.name for o in bpy.data.objects if o.animation_data for t in o.animation_data.nla_tracks][:30])
