# STEP 8: 9M22U 122-mm rocket (flying projectile) in its own scene "Rocket_9M22U"; nose = -Y (glTF +Z)
main = bpy.context.window.scene
sc = bpy.data.scenes.get("Rocket_9M22U") or bpy.data.scenes.new("Rocket_9M22U")
sc.render.fps = 24
for o in list(sc.collection.all_objects):
    bpy.data.objects.remove(o, do_unlink=True)
for c in list(sc.collection.children):
    bpy.data.collections.remove(c)
rc = bpy.data.collections.new("Rocket")
sc.collection.children.link(rc)
if not bpy.data.materials.get("M_Flame"):
    mf = bpy.data.materials.new("M_Flame")
    b = mf.node_tree.nodes.get("Principled BSDF")
    b.inputs["Base Color"].default_value = (1.0, 0.55, 0.12, 1)
    b.inputs["Emission Color"].default_value = (1.0, 0.6, 0.15, 1)
    b.inputs["Emission Strength"].default_value = 4.0


def mk(name, bm, mat, parent=None, smooth=True, loc=(0, 0, 0)):
    me = bpy.data.meshes.new(name)
    bm.normal_update(); bm.to_mesh(me); bm.free()
    me.materials.append(M(mat))
    for p in me.polygons:
        p.use_smooth = smooth
    ob = bpy.data.objects.new(name, me)
    rc.objects.link(ob)
    ob.parent = parent
    ob.location = loc
    return ob


NOSE = -1.30   # origin ~ centre of mass
L = 2.87
root = bpy.data.objects.new("Rocket", None); rc.objects.link(root)
spin = bpy.data.objects.new("Spin", None); rc.objects.link(spin); spin.parent = root
# body (lathe) : fuze, ogive warhead, band, motor, nozzle
prof = [(0.0, 0.0), (0.010, 0.004), (0.016, 0.03), (0.022, 0.06), (0.024, 0.075), (0.030, 0.08), (0.042, 0.13), (0.052, 0.19),
        (0.058, 0.25), (0.061, 0.32), (0.061, 2.62), (0.056, 2.70), (0.052, 2.78), (0.050, L)]
prof = [(r, NOSE + a) for (r, a) in prof]
bm = bm_lathe(prof, (0, 0, 0), 1, seg=16)
bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
uvl = bm.loops.layers.uv.verify()
for f in bm.faces:
    us = []
    for l in f.loops:
        co = l.vert.co
        l[uvl].uv = (math.atan2(co.z, co.x) / (2 * PI) + 0.5, 1.0 - (co.y - NOSE) / L)
        us.append(l[uvl].uv[0])
    if max(us) - min(us) > 0.5:
        for l in f.loops:
            if l[uvl].uv[0] < 0.5:
                l[uvl].uv = (l[uvl].uv[0] + 1, l[uvl].uv[1])
mk("RocketBody", bm, "M_Rocket", spin)
# MRV fuze (brass) + safety ring
bm = bm_lathe([(0.0, NOSE - 0.005), (0.012, NOSE + 0.01), (0.017, NOSE + 0.04), (0.017, NOSE + 0.07), (0.0, NOSE + 0.07)], (0, 0, 0), 1, seg=12)
bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
uv_box(bm, 4.0)
mk("Fuze", bm, "M_Brass", spin)
# type band (tinted per ammo type in-game)
bm = bm_cyl(0.0625, 0.07, (0, NOSE + 0.40, 0), AX_ROT[1], 16, caps=False)
uv_box(bm, 1.0)
mk("TypeBand", bm, "M_Rocket_Band", spin)
bm = bm_cyl(0.0625, 0.03, (0, NOSE + 1.05, 0), AX_ROT[1], 16, caps=False)
uv_box(bm, 1.0)
mk("TypeBand2", bm, "M_Rocket_Band", spin)
# nozzle bell + throat (dark steel)
bm = bm_lathe([(0.040, NOSE + L - 0.005), (0.047, NOSE + L + 0.05), (0.052, NOSE + L + 0.09), (0.046, NOSE + L + 0.09), (0.034, NOSE + L + 0.02), (0.0, NOSE + L + 0.02)], (0, 0, 0), 1, seg=14)
bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
uv_box(bm, 3.0)
mk("Nozzle", bm, "M_Bore", spin)
# 4 curved stabiliser fins; pivot on the hinge line (parallel to the axis), modelled DEPLOYED
for k in range(4):
    a = 2 * PI * k / 4 + PI / 4
    hinge = Vector((math.cos(a) * 0.058, 0, math.sin(a) * 0.058))
    radial = Vector((math.cos(a), 0, math.sin(a)))
    tang = Vector((-math.sin(a), 0, math.cos(a)))
    secs = []
    y0, y1 = NOSE + L - 0.26, NOSE + L - 0.02
    for i in range(5):
        y = y0 + (y1 - y0) * i / 4
        sec = []
        for jj in range(4):
            t = jj / 3
            h = 0.12 * t * (0.6 + 0.4 * (i / 4))   # swept tip
            curve = 0.012 * math.sin(PI * t)        # curved blade
            p = radial * h + tang * curve
            sec.append(Vector((p.x, y, p.z)))
        secs.append(sec)
    bm = bm_sweep_ribbon(secs)
    bmesh.ops.solidify(bm, geom=list(bm.faces), thickness=0.004)
    uv_box(bm, 3.0)
    fin = mk(f"Fin_{k + 1}", bm, "M_Frame", spin, smooth=False)
    fin.data.transform(Matrix.Translation((0, 0, 0)))
    fin.location = hinge   # mesh built around hinge at origin (radial offset applied through location)
# flame / exhaust plume (emissive, toggled while the motor burns)
bm = bm_lathe([(0.035, NOSE + L + 0.06), (0.07, NOSE + L + 0.25), (0.06, NOSE + L + 0.6), (0.03, NOSE + L + 0.95), (0.0, NOSE + L + 1.1)], (0, 0, 0), 1, seg=10)
bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
uv_box(bm, 1.0)
flame = mk("Flame", bm, "M_Flame", root)
bm = bm_lathe([(0.02, NOSE + L + 0.06), (0.04, NOSE + L + 0.2), (0.02, NOSE + L + 0.45), (0.0, NOSE + L + 0.55)], (0, 0, 0), 1, seg=8)
bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
uv_box(bm, 1.0)
mk("FlameCore", bm, "M_Lamp_Head", flame)

for o in rc.objects:
    o.rotation_mode = 'XYZ'


def nla_clip(name, ob, idx, keys, interp='LINEAR'):
    ad = ob.animation_data_create()
    act = bpy.data.actions.new(name + "_" + ob.name)
    ad.action = act
    for fr, v in keys:
        r = ob.rotation_euler.copy(); r[idx] = v; ob.rotation_euler = r
        ob.keyframe_insert("rotation_euler", index=idx, frame=fr)
    tr = ad.nla_tracks.new(); tr.name = name
    st = tr.strips.new(name, 0, act); st.extrapolation = 'NOTHING'
    tr.mute = True
    ad.action = None
    r = ob.rotation_euler.copy(); r[idx] = 0.0; ob.rotation_euler = r


# fins_deploy: folded (wrapped tangentially, -90deg about hinge axis Y) -> deployed in 0.25 s
for k in range(4):
    nla_clip("fins_deploy", bpy.data.objects[f"Fin_{k + 1}"], 1, [(0, 88 * D2R), (3, 60 * D2R), (6, 0.0)], interp='LINEAR')
# spin: 1 revolution / 0.5 s about the long axis
nla_clip("spin", spin, 1, [(k * 3, -k * PI / 2) for k in range(5)])

bpy.context.window.scene = sc
OUT = "/home/user/mods/bm21grad/src/main/resources/assets/bm21grad/glb/rocket_9m22u.glb"
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', export_yup=True, export_animations=True,
                          export_animation_mode='NLA_TRACKS', export_force_sampling=True, export_vertex_color='NONE',
                          export_cameras=False, export_lights=False, use_active_scene=True)
for o in rc.objects:
    o.rotation_euler = (0, 0, 0)
bpy.context.window.scene = main
print("rocket glb", os.path.getsize(OUT), "tris", sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in rc.objects if o.type == 'MESH'))
