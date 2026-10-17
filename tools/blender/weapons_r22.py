"""r22/r23 player weapons, built from real dimensions (Blender 5.x headless):
     ORSIS T-5000M .338 LM + 5-25x56   1270 mm, barrel 700, 5-rd box, folding skeleton stock, Harris bipod   (r23)
     PKP Pecheneg 6P41                1155 mm, barrel 658 (finned, shrouded), 100-rd box, carry handle      (r23)
     Saiga-12K isp.030 (12/76)        910 mm, barrel 430, 8-rd box, AK-74M folding stock, railed cover      (r24)
     Remington 870 Police Magnum      978 mm, 18" barrel + rifle sights, 4+1 tube, pump with action bars   (r24)
     9K338 Igla-S / FIM-92 Stinger    1700 / 1520 mm tubes, gripstocks, NIP / BCU, IFF grid, sights      (r25)
     WW2: PPSh-41, MP 40, Mosin-Nagant 1891/30, Karabiner 98k (stripper clips)                        (r26)
   First-person hands = the player's vanilla arms (r23); the GLB carries only the hand anchors <w>.arm_r / <w>.arm_l.
     AK-12 6P70 (2018)   945 mm, barrel 415, 30-rd 6L31 magazine, diopter rear sight, telescoping folding stock
     M4A1 + Aimpoint CompM4 on QRP2   838 mm (stock extended), 14.5" barrel, STANAG 30, A2 FSB / grip / birdcage
     RPG-7V2 + PGO-7V3 + PG-7VL       950 mm, 40 mm tube, 93 mm warhead
   blender -b --factory-startup --python tools/blender/weapons_r22.py

Side-profile coordinates are written in millimetres exactly as read off the drawings:
   u = distance from the rear end of the weapon (butt / venturi) towards the muzzle,  v = height above the bore axis,
   x = towards the LEFT side of the weapon.
Blender: metres, Z up, forward = -Y, +X = left (glTF +Z forward / +X left like every model in the mod).
Origin of every weapon = the bore axis above the trigger (u = U0).
Nodes per weapon <w>:   <w>.gun (animated root) > <w>.body, <w>.mag | <w>.round, <w>.bolt, <w>.trigger, <w>.flash,
                        <w>.arm_r, <w>.arm_l (first person only), empties <w>.sight (eye point) and <w>.muzzle.
Animations (glTF, 24 fps): <w>_reload, <w>_reload_empty (AK-12 / M4A1), rpg7_reload.
"""
import bpy, bmesh, math, os, sys
from pathlib import Path
from mathutils import Vector, Matrix, Euler

ROOT = str(Path(__file__).resolve().parents[2])
TEX = ROOT + "/tools/blender/tex_r22"
GLB = ROOT + "/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb"
D = math.radians

if "--library-only" not in sys.argv:
    bpy.ops.wm.read_factory_settings(use_empty=True)
MATS = {}


def mat(key, tex, rough=0.6, metal=0.0, emissive=False, clip=False, glass=None):
    m = bpy.data.materials.new(key)
    try:
        m.use_nodes = True
    except Exception:
        pass
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    img = bpy.data.images.load(os.path.join(TEX, tex + ".png"))
    img.pack()
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = img
    nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    if emissive:
        nt.links.new(t.outputs["Color"], b.inputs["Emission Color"])
        b.inputs["Emission Strength"].default_value = 1.0
    if clip:
        gt = nt.nodes.new("ShaderNodeMath")
        gt.operation = 'GREATER_THAN'
        gt.inputs[1].default_value = 0.5
        nt.links.new(t.outputs["Alpha"], gt.inputs[0])
        nt.links.new(gt.outputs[0], b.inputs["Alpha"])
        m.surface_render_method = 'DITHERED'
    if glass is not None:
        b.inputs["Alpha"].default_value = glass
        m.surface_render_method = 'DITHERED'
    m.use_backface_culling = False
    MATS[key] = m
    return m


mat("Polymer", "T_Polymer", 0.75)
mat("Stipple", "T_Stipple", 0.9)
mat("Parker", "T_Parker", 0.55, 0.5)
mat("Anod", "T_Anod", 0.5, 0.4)
mat("Steel", "T_Steel", 0.4, 0.8)
mat("Brass", "T_Brass", 0.35, 0.9)
mat("RpgGreen", "T_RpgGreen", 0.6, 0.3)
mat("Wood", "T_Wood", 0.55)
mat("Warhead", "T_Warhead", 0.6, 0.2)
mat("Rubber", "T_Rubber", 0.95)
mat("Lens", "T_Lens", 0.05, 0.0, glass=0.22)
mat("LensSolid", "T_Lens", 0.05)
mat("EMR", "T_EMR", 0.95)
mat("OCP", "T_OCP", 0.95)
mat("GloveBlack", "T_GloveBlack", 0.85)
mat("GloveCoyote", "T_GloveCoyote", 0.85)
mat("Flash", "T_Flash", 0.9, emissive=True, clip=True)
mat("RedDot", "T_RedDot", 0.9, emissive=True, clip=True)
mat("ShellRed", "T_ShellRed", 0.6)
mat("Blued", "T_Blued", 0.45, 0.7)
mat("Birch", "T_Birch", 0.6)
mat("Walnut", "T_Walnut", 0.5)
mat("Bakelite", "T_Bakelite", 0.35)
mat("Copper", "T_Copper", 0.35, 0.85)
mat("ClipSteel", "T_ClipSteel", 0.5, 0.6)

# ------------------------------------------------------------------ build state
U0 = 0.0
PARTS = {}          # node name -> [objects]
COLL = None


def P(u, v, x=0.0):
    """drawing mm -> Blender metres"""
    return Vector((x / 1000.0, (U0 - u) / 1000.0, v / 1000.0))


def _mk(name, bm, m, grp, smooth=False, bevel=0.0):
    bm.normal_update()
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(MATS[m])
    for p in me.polygons:
        p.use_smooth = smooth and len(p.vertices) <= 4      # n-gon caps stay flat (no dome shading)
    ob = bpy.data.objects.new(name, me)
    COLL.objects.link(ob)
    if bevel > 0:
        md = ob.modifiers.new("bev", "BEVEL")
        md.width = bevel / 1000.0
        md.segments = 2
        md.limit_method = 'ANGLE'
        md.angle_limit = D(40)
        md.harden_normals = False
    # simple box UVs (texture is a uniform grain, any mapping works)
    uv = me.uv_layers.new(name="UVMap")
    for poly in me.polygons:
        n = poly.normal
        for li in poly.loop_indices:
            co = me.vertices[me.loops[li].vertex_index].co
            if abs(n.x) >= abs(n.y) and abs(n.x) >= abs(n.z):
                uv.data[li].uv = (co.y * 6, co.z * 6)
            elif abs(n.y) >= abs(n.z):
                uv.data[li].uv = (co.x * 6, co.z * 6)
            else:
                uv.data[li].uv = (co.x * 6, co.y * 6)
    PARTS.setdefault(grp, []).append(ob)
    return ob


def prism(grp, pts, x0, x1, m, bevel=0.8):
    """side outline (u, v) mm extruded across the weapon from x0 to x1 (mm, +x = left)"""
    bm = bmesh.new()
    a = [bm.verts.new(P(u, v, x0)) for (u, v) in pts]
    b = [bm.verts.new(P(u, v, x1)) for (u, v) in pts]
    n = len(pts)
    bm.faces.new(a)
    bm.faces.new(list(reversed(b)))
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((a[i], a[j], b[j], b[i]))
    return _mk(grp + "_p", bm, m, grp, False, bevel)


def xsec(grp, pts, u0, u1, m, bevel=0.8):
    """cross-section outline (x, v) mm extruded along the bore from u0 to u1"""
    bm = bmesh.new()
    a = [bm.verts.new(P(u0, v, x)) for (x, v) in pts]
    b = [bm.verts.new(P(u1, v, x)) for (x, v) in pts]
    n = len(pts)
    bm.faces.new(a)
    bm.faces.new(list(reversed(b)))
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((a[i], a[j], b[j], b[i]))
    return _mk(grp + "_x", bm, m, grp, False, bevel)


def box(grp, u0, u1, v0, v1, x0, x1, m, bevel=0.6):
    return prism(grp, [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], x0, x1, m, bevel)


def lathe(grp, prof, m, seg=24, cx=0.0, cv=0.0, closed=False, sx=1.0, sv=1.0, smooth=True):
    """surface of revolution about an axis parallel to the bore through (x=cx, v=cv). prof = [(u, r)];
       every profile segment is its own band (sharp profile corners, smooth around); caps where r > 0 at the ends."""
    bm = bmesh.new()
    segs = list(zip(prof[:-1], prof[1:]))
    if closed:
        segs.append((prof[-1], prof[0]))
    for (ua, ra), (ub, rb) in segs:
        ring_a, ring_b = [], []
        for k in range(seg):
            t = 2 * math.pi * k / seg
            ring_a.append(bm.verts.new(P(ua, cv + ra * math.sin(t) * sv, cx + ra * math.cos(t) * sx)))
            ring_b.append(bm.verts.new(P(ub, cv + rb * math.sin(t) * sv, cx + rb * math.cos(t) * sx)))
        for k in range(seg):
            j = (k + 1) % seg
            if ra < 1e-6 and rb < 1e-6:
                continue
            bm.faces.new((ring_a[k], ring_a[j], ring_b[j], ring_b[k]))
    if not closed:
        for (u, r) in (prof[0], prof[-1]):
            if r > 1e-6:
                bm.faces.new([bm.verts.new(P(u, cv + r * math.sin(2 * math.pi * k / seg) * sv,
                                             cx + r * math.cos(2 * math.pi * k / seg) * sx)) for k in range(seg)])
    bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=1e-7)
    return _mk(grp + "_l", bm, m, grp, smooth)


def cylx(grp, u, v, x0, x1, r, m, seg=14):
    """cylinder across the weapon (pins, knobs)"""
    bm = bmesh.new()
    a = [bm.verts.new(P(u + r * math.cos(2 * math.pi * k / seg), v + r * math.sin(2 * math.pi * k / seg), x0)) for k in range(seg)]
    b = [bm.verts.new(P(u + r * math.cos(2 * math.pi * k / seg), v + r * math.sin(2 * math.pi * k / seg), x1)) for k in range(seg)]
    bm.faces.new(a)
    bm.faces.new(list(reversed(b)))
    for k in range(seg):
        j = (k + 1) % seg
        bm.faces.new((a[k], a[j], b[j], b[k]))
    return _mk(grp + "_cx", bm, m, grp, True)


def cylz(grp, u, x, v0, v1, r, m, seg=14):
    """vertical cylinder (posts, turrets, knobs)"""
    bm = bmesh.new()
    a = [bm.verts.new(P(u + r * math.cos(2 * math.pi * k / seg), v0, x + r * math.sin(2 * math.pi * k / seg))) for k in range(seg)]
    b = [bm.verts.new(P(u + r * math.cos(2 * math.pi * k / seg), v1, x + r * math.sin(2 * math.pi * k / seg))) for k in range(seg)]
    bm.faces.new(a)
    bm.faces.new(list(reversed(b)))
    for k in range(seg):
        j = (k + 1) % seg
        bm.faces.new((a[k], a[j], b[j], b[k]))
    return _mk(grp + "_cz", bm, m, grp, True)


def rod(grp, p0, p1, r0, r1, m, seg=10, caps=True):
    """tapered rod between Blender-space points (metres; radii in mm)"""
    p0, p1 = Vector(p0), Vector(p1)
    d = p1 - p0
    q = Vector((0, 0, 1)).rotation_difference(d.normalized())
    bm = bmesh.new()
    a, b = [], []
    for k in range(seg):
        t = 2 * math.pi * k / seg
        c = Vector((math.cos(t), math.sin(t), 0))
        a.append(bm.verts.new(p0 + q @ (c * r0 / 1000.0)))
        b.append(bm.verts.new(p1 + q @ (c * r1 / 1000.0)))
    for k in range(seg):
        j = (k + 1) % seg
        bm.faces.new((a[k], a[j], b[j], b[k]))
    if caps:
        bm.faces.new(a)
        bm.faces.new(list(reversed(b)))
    return _mk(grp + "_r", bm, m, grp, True)


def sphere(grp, c, r, m, sc=(1, 1, 1), seg=10):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=seg, v_segments=seg // 2 + 1, radius=r / 1000.0,
                              matrix=Matrix.Translation(Vector(c)) @ Matrix.Diagonal((sc[0], sc[1], sc[2], 1)))
    return _mk(grp + "_s", bm, m, grp, True)


def obox(grp, c, axes, size, m, bevel=2.0):
    """oriented box: centre c (Blender m), axes = 3 unit vectors, size (mm) along them"""
    bm = bmesh.new()
    vs = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            for sz in (-1, 1):
                vs.append(bm.verts.new(Vector(c) + axes[0] * (sx * size[0] / 2000.0) + axes[1] * (sy * size[1] / 2000.0)
                                       + axes[2] * (sz * size[2] / 2000.0)))
    F = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    for f in F:
        bm.faces.new([vs[i] for i in f])
    return _mk(grp + "_ob", bm, m, grp, False, bevel)


def picatinny(grp, u0, u1, vb, m, down=False, x=0.0):
    """MIL-STD-1913 rail: 21.2 mm wide, 9.4 mm high, recoil slots 5.23 mm on a 10.01 mm pitch (separate cross teeth)"""
    s = -1 if down else 1
    base = [(-8.0 + x, vb), (8.0 + x, vb), (8.0 + x, vb + s * 3.0), (-8.0 + x, vb + s * 3.0)]
    xsec(grp, base, u0, u1, m, 0.3)
    tooth = [(-10.6 + x, vb + s * 3.0), (10.6 + x, vb + s * 3.0), (10.6 + x, vb + s * 5.5), (8.4 + x, vb + s * 9.4),
             (-8.4 + x, vb + s * 9.4), (-10.6 + x, vb + s * 5.5)]
    n = int((u1 - u0 - 4.8) // 10.01) + 1
    off = (u1 - u0 - ((n - 1) * 10.01 + 4.78)) / 2
    for i in range(n):
        a = u0 + off + i * 10.01
        xsec(grp, tooth, a, a + 4.78, m, 0.3)


def ring_rod_loop(grp, center, r_loop, r_wire, m, axis='x', n=8):
    """sling swivel loop (polygonal torus out of short rods). center Blender m."""
    pts = []
    for k in range(n + 1):
        t = 2 * math.pi * k / n
        if axis == 'x':
            pts.append(Vector(center) + Vector((0, math.cos(t), math.sin(t))) * r_loop / 1000.0)
        else:
            pts.append(Vector(center) + Vector((math.cos(t), 0, math.sin(t))) * r_loop / 1000.0)
    for a, b in zip(pts[:-1], pts[1:]):
        rod(grp, a, b, r_wire, r_wire, m, 6, caps=False)


def arc_outline(cu, cv, r_out, r_in, t0, t1, n=12, r_out1=None, r_in1=None):
    """outline of a curved magazine: arc centre (cu, cv) ahead of the magazine, angles from 'straight back' downwards"""
    r_out1 = r_out if r_out1 is None else r_out1
    r_in1 = r_in if r_in1 is None else r_in1
    rear, front = [], []
    for i in range(n + 1):
        f = i / n
        t = t0 + (t1 - t0) * f
        ro = r_out + (r_out1 - r_out) * f
        ri = r_in + (r_in1 - r_in) * f
        rear.append((cu - ro * math.cos(t), cv - ro * math.sin(t)))
        front.append((cu - ri * math.cos(t), cv - ri * math.sin(t)))
    return rear + list(reversed(front))


# ------------------------------------------------------------------ hands (first person)
def _frame(zdir, palm_dir, mirror=False):
    Z = Vector(zdir).normalized()
    Pd = Vector(palm_dir)
    Pd = (Pd - Z * Pd.dot(Z)).normalized()
    Q = Z.cross(Pd)
    c, s = math.cos(D(150)), math.sin(D(150))
    X = Pd * c - Q * s
    Y = Pd * s + Q * c
    if mirror:
        X = -X
    return X, Y, Z


HANDS = {}


def hand(grp, centre, *unused, **kw):
    """r23: first-person hands are the player's own vanilla Minecraft arms (drawn by the mod's GunRenderer).
    The weapon only carries an anchor empty <w>.arm_r / <w>.arm_l on the grip axis (animated by the reload clips)."""
    HANDS[grp] = Vector(centre)


# ------------------------------------------------------------------ joining / scenes / clips
def join_node(grp, parent=None, origin=None):
    if grp in HANDS:                                  # hand anchor = empty on the grip axis
        e = empty(grp, HANDS.pop(grp), parent)
        PARTS[grp] = [e]
        return e
    obs = PARTS.get(grp, [])
    if not obs:
        return None
    for o in obs:
        for md in list(o.modifiers):
            with bpy.context.temp_override(object=o, active_object=o, selected_objects=[o], selected_editable_objects=[o]):
                bpy.ops.object.modifier_apply(modifier=md.name)
    bpy.ops.object.select_all(action='DESELECT')
    for o in obs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = obs[0]
    if len(obs) > 1:
        bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active
    ob.name = grp
    ob.data.name = grp
    if origin is not None:
        o = Vector(origin)
        ob.data.transform(Matrix.Translation(-o))
        ob.location = o
    if parent is not None:
        bpy.context.view_layer.update()
        mw = ob.matrix_world.copy()
        ob.parent = parent
        ob.matrix_world = mw
    PARTS[grp] = [ob]
    return ob


def empty(name, loc, parent=None):
    e = bpy.data.objects.new(name, None)
    e.empty_display_size = 0.03
    COLL.objects.link(e)
    e.location = Vector(loc)
    if parent is not None:
        bpy.context.view_layer.update()
        mw = e.matrix_world.copy()
        e.parent = parent
        e.matrix_world = mw
    return e


SCENES = {}


def new_scene(name):
    global COLL, PARTS
    s = bpy.data.scenes.new(name)
    s.render.fps = 24
    SCENES[name] = s
    bpy.context.window.scene = s
    COLL = s.collection
    PARTS = {}
    return s


def clip(name, ob, keys):
    """keys = [(frame, (dx, dy, dz) metres, (rx, ry, rz) degrees)] relative to the rest transform -> NLA track `name`"""
    ad = ob.animation_data_create()
    act = bpy.data.actions.new(name + "_" + ob.name)
    ad.action = act
    l0, r0 = ob.location.copy(), ob.rotation_euler.copy()
    for fr, dl, dr in keys:
        ob.location = l0 + Vector(dl)
        ob.rotation_euler = Euler((r0[0] + D(dr[0]), r0[1] + D(dr[1]), r0[2] + D(dr[2])))
        ob.keyframe_insert("location", frame=fr)
        ob.keyframe_insert("rotation_euler", frame=fr)
    tr = ad.nla_tracks.new()
    tr.name = name
    st = tr.strips.new(name, 0, act)
    st.extrapolation = 'NOTHING'
    tr.mute = True                      # never evaluated in the scene: the rest pose must stay the modelled one
    ad.action = None
    ob.location, ob.rotation_euler = l0, r0


def finish(sc):
    for o in sc.objects:
        if o.animation_data:
            for tr in o.animation_data.nla_tracks:
                tr.mute = True
    sc.frame_set(0)


def export(sc, fname, anim=True):
    bpy.context.window.scene = sc
    finish(sc)
    rest = {o.name: (o.location.copy(), o.rotation_euler.copy(), o.scale.copy()) for o in sc.objects}
    path = os.path.join(GLB, fname + ".glb")
    tris = 0
    for o in sc.objects:
        if o.type == 'MESH':
            tris += sum(len(p.vertices) - 2 for p in o.data.polygons)
    with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
        bpy.ops.export_scene.gltf(filepath=path, export_format='GLB', export_yup=True, export_apply=True,
                                  export_animations=anim, export_animation_mode='NLA_TRACKS', export_force_sampling=True,
                                  export_extras=False, export_cameras=False, export_lights=False, export_image_format='AUTO',
                                  use_visible=False, use_selection=False, use_active_scene=True)
    for n, (l, r, s_) in rest.items():
        o = bpy.data.objects.get(n)
        if o:
            o.location, o.rotation_euler, o.scale = l, r, s_
    print("WPN", fname, "tris", tris, "bytes", os.path.getsize(path))


def flash(grp, u, v, length=110, r=42):
    """muzzle flash: three crossed emissive cut-out quads + a front-facing star (hidden unless just fired)"""
    for k in range(3):
        a = D(60 * k)
        bm = bmesh.new()
        dx, dv = math.cos(a) * r, math.sin(a) * r
        vs = [bm.verts.new(P(u, v - dv, -dx)), bm.verts.new(P(u + length, v - dv, -dx)),
              bm.verts.new(P(u + length, v + dv, dx)), bm.verts.new(P(u, v + dv, dx))]
        bm.faces.new(vs)
        ob = _mk(grp + "_q", bm, "Flash", grp)
        me = ob.data
        uvl = me.uv_layers[0]
        for li, (uu, vv) in zip(me.polygons[0].loop_indices, [(0.5, 0), (0.5, 1), (1, 1), (1, 0)]):
            pass
        quv = [(0, 0), (1, 0), (1, 1), (0, 1)]
        for n, li in enumerate(me.polygons[0].loop_indices):
            uvl.data[li].uv = (0.5 + 0.5 * quv[n][1] * 0 + 0.0, 0)
        # map: along the flash length = texture radius (centre -> edge), across = full width
        for n, li in enumerate(me.polygons[0].loop_indices):
            uvl.data[li].uv = [(0.5, 0.0), (0.5, 1.0), (1.0, 1.0), (1.0, 0.0)][n] if False else [(0.0, 0.5), (1.0, 0.5), (1.0, 1.0), (0.0, 1.0)][n]
    bm = bmesh.new()
    rr = r * 1.3
    vs = [bm.verts.new(P(u + 4, v - rr, -rr)), bm.verts.new(P(u + 4, v - rr, rr)), bm.verts.new(P(u + 4, v + rr, rr)),
          bm.verts.new(P(u + 4, v + rr, -rr))]
    bm.faces.new(vs)
    ob = _mk(grp + "_star", bm, "Flash", grp)
    uvl = ob.data.uv_layers[0]
    for n, li in enumerate(ob.data.polygons[0].loop_indices):
        uvl.data[li].uv = [(0, 0), (1, 0), (1, 1), (0, 1)][n]


# ====================================================================================== AK-12
def ak12_mag(g, top_only=False):
    # 6L31: 30 x 5.45x39, polymer, arc of R ~380 mm; 73 mm front-to-back at the lips, 64 at the floorplate
    cu, cv, R = 433 + 380, -44, 380
    prism(g, [(400, -30), (466, -30), (466, -44), (400, -44)], -12.5, 12.5, "Polymer", 1.0)       # body inside the well
    prism(g, arc_outline(cu, cv, R + 33, R - 33, 0.0, 0.47, 14, R + 30, R - 30), -13, 13, "Polymer", 2.0)
    for (ro, ri) in ((R + 26, R + 21), (R - 21, R - 26)):                                       # side ribs following the arc
        prism(g, arc_outline(cu, cv, ro, ri, 0.04, 0.43, 12), 13, 14.2, "Polymer", 0.4)
        prism(g, arc_outline(cu, cv, ro, ri, 0.04, 0.43, 12), -14.2, -13, "Polymer", 0.4)
    for t in (0.10, 0.20, 0.30):                                                                  # cross ribs
        a0, a1 = t, t + 0.012
        prism(g, arc_outline(cu, cv, R + 30, R - 30, a0, a1, 1), 13, 14.0, "Polymer", 0.3)
        prism(g, arc_outline(cu, cv, R + 30, R - 30, a0, a1, 1), -14.0, -13, "Polymer", 0.3)
    t1 = 0.47
    fp = arc_outline(cu, cv, R + 33, R - 33, t1 - 0.03, t1 + 0.012, 2, R + 31, R - 31)
    prism(g, fp, -15, 15, "Polymer", 1.5)                                                         # floorplate
    box(g, 464, 474, -42, -31, -6, 6, "Steel", 0.4)                                               # front locking lug
    box(g, 397, 402, -52, -44, -6, 6, "Steel", 0.4)                                               # rear catch lug
    lathe(g, [(408, 0), (408, 4.6), (440, 4.9), (446, 4.5), (446, 0)], "Brass", 12, 0, -33)       # top round
    lathe(g, [(446, 4.5), (456, 4.4), (466, 2.0), (468, 0)], "Steel", 12, 0, -33)


def ak12_body(g):
    # receiver (stamped steel)
    prism(g, [(262, -38), (262, 12), (270, 16), (522, 16), (522, -36), (478, -40), (472, -48), (396, -48), (392, -40),
              (300, -40)], -16, 16, "Parker", 1.2)
    xsec(g, [(-17, 12), (17, 12), (17, 20), (14, 26), (8, 29), (-8, 29), (-14, 26), (-17, 20)], 268, 520, "Parker", 0.8)
    box(g, 262, 269, 17, 25, -5, 5, "Steel")                                                      # cover latch button
    picatinny(g, 272, 518, 29, "Parker")
    # rear diopter sight
    box(g, 279, 302, 38, 44, -11, 11, "Parker")
    for s in (1, -1):
        prism(g, [(280, 44), (301, 44), (299, 62), (284, 62)], 8.5 * s, 11.5 * s, "Parker", 0.6)
    lathe(g, [(288, 2.6), (288, 6.5), (293, 6.5), (293, 2.6)], "Steel", 16, 0, 55, closed=True)
    box(g, 288, 293, 44, 49, -2, 2, "Steel")
    cylx(g, 291, 41, 11, 15, 3.8, "Steel")
    # ejection port (dark recess) and selector lever (doubles as the dust cover)
    box(g, 405, 482, 0, 16, -16.4, -16.0, "Rubber", 0.1)
    prism(g, [(300, 6), (394, 6), (402, 1), (394, -5), (318, -3), (300, 1)], -18.6, -16.0, "Steel", 0.4)
    box(g, 394, 405, -11, 1, -24, -18.6, "Steel", 0.6)
    cylx(g, 306, 2, -20, -18.6, 4.2, "Steel")
    # rivets / pins both sides
    for (u, v) in ((285, -28), (330, -28), (382, -31), (410, -42), (455, -42), (505, -12), (505, -28)):
        cylx(g, u, v, 16, 17.2, 2.4, "Steel", 10)
        cylx(g, u, v, -17.2, -16, 2.4, "Steel", 10)
    # trigger guard, magazine catch
    prism(g, [(336, -40), (342, -40), (345, -56), (391, -58), (393, -40), (399, -40), (398, -63), (341, -62)], -5, 5, "Steel", 0.6)
    prism(g, [(393, -48), (398, -48), (401, -60), (397, -65), (391, -62)], -7, 7, "Steel", 0.6)
    # pistol grip (2018 ergonomic, stippled) and its cap
    prism(g, [(300, -40), (346, -40), (344, -50), (337, -60), (332, -75), (334, -88), (328, -100), (322, -118), (312, -136),
              (272, -138), (270, -130), (282, -90), (290, -60), (296, -48)], -15, 15, "Stipple", 3.0)
    prism(g, [(268, -134), (314, -132), (313, -141), (270, -144)], -15.6, 15.6, "Polymer", 1.5)
    # stock: hinge block (folds to the left), telescoping rods, skeleton stock, cheek riser, butt pad, QD cup
    box(g, 246, 264, -32, 24, -17, 17, "Polymer", 3)
    cylz(g, 252, 18, -30, 22, 4, "Steel")
    for v in (22, -18):
        lathe(g, [(196, 5), (247, 5)], "Steel", 12, 0, v)
    prism(g, [(22, 18), (200, 18), (200, 34), (30, 40), (22, 40)], -17, 17, "Polymer", 2)
    prism(g, [(22, -104), (40, -104), (200, -36), (200, -10), (190, -10), (24, -80)], -16, 16, "Polymer", 2)
    box(g, 190, 214, -40, 40, -18, 18, "Polymer", 3)                                             # stock head on the rods
    prism(g, [(110, -62), (125, -66), (130, 18), (115, 18)], -12, 12, "Polymer", 1.5)
    prism(g, [(60, 40), (178, 36), (178, 45), (66, 52)], -15, 15, "Polymer", 2)
    prism(g, [(4, -112), (30, -112), (34, 40), (4, 42)], -20, 20, "Polymer", 3)
    prism(g, [(-8, -114), (4, -114), (4, 44), (-8, 44)], -21, 21, "Rubber", 3)
    box(g, 150, 172, -52, -44, -7, 7, "Steel", 0.6)                                               # length latch
    cylx(g, 30, -92, 20, 22.5, 6, "Steel")
    # lower handguard (polymer, vented), bottom rail, end cap
    xsec(g, [(-20, -30), (20, -30), (23, -24), (23, 10), (18, 15), (-18, 15), (-23, 10), (-23, -24)], 524, 768, "Polymer", 2)
    for i in range(4):
        for s in (1, -1):
            box(g, 540 + i * 55, 576 + i * 55, -18, 2, 22.6 * s, 23.4 * s, "Rubber", 0.3)
    picatinny(g, 560, 740, -30, "Polymer", down=True)
    box(g, 764, 772, -33, 17, -24, 24, "Polymer", 1.2)
    # gas tube cover with its rail, cover lever
    xsec(g, [(-15, 14), (15, 14), (16, 20), (12, 26), (-12, 26), (-16, 20)], 522, 772, "Parker", 0.8)
    picatinny(g, 526, 766, 26, "Parker")
    box(g, 522, 534, 6, 16, 16, 22, "Steel")
    # barrel, gas block, front sight tower, bayonet lug
    lathe(g, [(522, 10), (846, 9.5)], "Parker", 18)
    box(g, 772, 794, -12, 22, -12, 12, "Parker", 2)
    prism(g, [(782, 22), (800, 22), (800, 30), (797, 46), (785, 46), (782, 30)], -7, 7, "Parker", 1)
    for s in (1, -1):
        prism(g, [(782, 22), (800, 22), (799, 56), (785, 56)], 8 * s, 11 * s, "Parker", 0.6)
    cylz(g, 791, 0, 44, 53, 1.5, "Steel", 8)
    box(g, 784, 816, -12, -20, -6, 6, "Parker")
    # muzzle brake-compensator: collar, body, side and top ports, bore
    lathe(g, [(826, 0), (826, 14), (838, 14), (838, 0)], "Parker", 20)
    lathe(g, [(838, 0), (838, 11), (842, 13), (940, 13), (944, 11.5), (945, 8), (945, 0)], "Parker", 24)
    for k in range(4):
        for s in (1, -1):
            box(g, 852 + k * 21, 865 + k * 21, -4.5, 4.5, 12.4 * s, 13.6 * s, "Rubber", 0.2)
        box(g, 852 + k * 21, 862 + k * 21, 12.4, 13.6, -3, 3, "Rubber", 0.2)
    lathe(g, [(944.8, 3.6), (945.6, 3.6)], "Rubber", 12)
    # sling swivel ahead of the handguard
    ring_rod_loop(g, P(758, -38, 0), 7, 1.6, "Steel")


def ak12():
    global U0
    sc = new_scene("AK12")
    U0 = 372.0
    root = empty("ak12", (0, 0, 0))
    gun = empty("ak12.gun", (0, 0, 0), root)
    ak12_body("ak12.body")
    ak12_mag("ak12.mag")
    # trigger blade
    prism("ak12.trigger", [(366, -40), (372, -40), (374, -48), (373, -56), (369, -57), (370, -50)], -3, 3, "Steel", 0.5)
    # bolt carrier seen through the port + charging handle (right side)
    box("ak12.bolt", 405, 505, 2, 14, -16.3, -14.0, "Steel", 0.4)
    box("ak12.bolt", 498, 512, 3, 13, -24, -16.3, "Steel", 0.6)
    cylx("ak12.bolt", 507, 8, -36, -24, 7.5, "Steel", 14)
    flash("ak12.flash", 946, 0)
    # first-person arms: Ratnik EMR sleeves, black gloves
    gz = Vector((0, -0.030, 0.082)).normalized()
    hand("ak12.arm_r", P(311, -86, 0), gz, Vector((-0.55, 0.83, 0)), 16, "GloveBlack", "EMR", (-0.40, 0.84, -0.36),
         trigger=P(371, -50, -1))
    hand("ak12.arm_l", P(662, -8, 0), Vector((0, -1, 0)), Vector((0.75, 0, -0.66)), 27, "GloveBlack", "EMR",
         (0.46, 0.80, -0.38), mirror=True)
    body = join_node("ak12.body", gun)
    join_node("ak12.mag", gun, P(470, -40, 0))
    join_node("ak12.trigger", gun, P(369, -40, 0))
    join_node("ak12.bolt", gun, P(450, 8, 0))
    join_node("ak12.flash", gun, P(946, 0, 0))
    arm_r = join_node("ak12.arm_r", gun, P(300, -120, -20))
    arm_l = join_node("ak12.arm_l", gun, P(640, -30, 20))
    empty("ak12.sight", P(200, 51.5, 0), gun)          # eye on the line rear aperture (v 55) -> front post tip (v 53)
    empty("ak12.muzzle", P(946, 0, 0), gun)
    G = bpy.data.objects["ak12.gun"]
    M = bpy.data.objects["ak12.mag"]
    A = bpy.data.objects["ak12.arm_l"]
    B = bpy.data.objects["ak12.bolt"]
    Z = (0, 0, 0)
    tilt = ((0.02, 0.0, 0.03), (-8, -26, 6))
    common_g = [(0, Z, Z), (8, *tilt), (44, *tilt), (54, Z, Z), (60, Z, Z)]
    mag_keys = [(0, Z, Z), (12, Z, Z), (16, Z, (-18, 0, 0)), (20, (0, -0.01, -0.08), (-25, 0, 0)),
                (26, (0, 0.05, -0.36), (-40, 0, 0)), (34, (0, 0.05, -0.36), (-40, 0, 0)), (40, (0, -0.012, -0.06), (-22, 0, 0)),
                (44, (0, 0, 0), (-15, 0, 0)), (47, Z, Z)]
    arm_keys = [(0, Z, Z), (8, (0, 0.17, -0.07), (0, 0, -10)), (12, (0, 0.17, -0.07), (0, 0, -10)),
                (20, (0, 0.18, -0.15), (0, 0, -10)), (26, (0.03, 0.22, -0.42), (0, 0, -10)), (34, (0.03, 0.22, -0.42), (0, 0, -10)),
                (40, (0, 0.19, -0.13), (0, 0, -10)), (44, (0, 0.18, -0.08), (0, 0, -10)), (48, (0, 0.17, -0.07), (0, 0, -10))]
    clip("ak12_reload", G, common_g)
    clip("ak12_reload", M, mag_keys + [(60, Z, Z)])
    clip("ak12_reload", A, arm_keys + [(56, Z, Z), (60, Z, Z)])
    rack = ((0.0, 0.01, 0.02), (-6, 22, -4))
    clip("ak12_reload_empty", G, [(0, Z, Z), (8, *tilt), (44, *tilt), (52, *rack), (64, *rack), (72, Z, Z), (76, Z, Z)])
    clip("ak12_reload_empty", M, mag_keys + [(76, Z, Z)])
    clip("ak12_reload_empty", A, arm_keys + [(53, (-0.05, 0.15, 0.03), (0, 30, 0)), (56, (-0.05, 0.15, 0.025), (0, 30, 0)),
                                             (60, (-0.05, 0.25, 0.025), (0, 30, 0)), (63, (-0.05, 0.25, 0.03), (0, 30, 0)),
                                             (71, Z, Z), (76, Z, Z)])
    clip("ak12_reload_empty", B, [(0, Z, Z), (56, Z, Z), (60, (0, 0.10, 0), Z), (61, (0, 0.10, 0), Z), (63, Z, Z), (76, Z, Z)])
    return sc


# ====================================================================================== M4A1
def stanag(g):
    # USGI 30-rd aluminium: straight upper half, curved lower half; 22.5 mm thick
    prism(g, [(342, -26), (408, -26), (408, -110), (424, -192), (362, -198), (342, -110)], -11.4, 11.4, "Anod", 1.2)
    for s in (1, -1):
        prism(g, [(351, -42), (356, -42), (356, -110), (369, -186), (364, -187), (351, -110)], 11.4 * s, 12.3 * s, "Anod", 0.3)
        prism(g, [(392, -42), (397, -42), (397, -110), (410, -184), (405, -185), (392, -110)], 11.4 * s, 12.3 * s, "Anod", 0.3)
    prism(g, [(358, -194), (428, -186), (429, -196), (358, -205)], -13.5, 13.5, "Polymer", 1.5)    # floorplate
    lathe(g, [(352, 0), (352, 4.7), (380, 4.8), (386, 4.5), (386, 0)], "Brass", 12, 3, -24)        # staggered top rounds
    lathe(g, [(386, 4.5), (395, 4.0), (402, 1.6), (404, 0)], "Brass", 12, 3, -24)
    lathe(g, [(352, 0), (352, 4.7), (380, 4.8), (386, 4.5), (386, 0)], "Brass", 12, -3, -32)


def m4a1_body(g):
    # stock (6 position, extended) + buffer tube, castle nut, end plate
    prism(g, [(0, -104), (14, -106), (32, -100), (70, -72), (120, -50), (176, -24), (176, 24), (0, 26)], -17, 17, "Polymer", 3)
    for s_ in (1, -1):                                                                             # recessed side panels
        prism(g, [(22, -78), (60, -62), (150, -26), (150, 12), (22, 12)], 17 * s_, 17.6 * s_, "Polymer", 0.5)
        box(g, 150, 176, -20, 18, 17 * s_, 18.2 * s_, "Polymer", 0.6)
    prism(g, [(-10, -106), (0, -106), (0, 27), (-10, 27)], -19, 19, "Rubber", 3)
    box(g, 128, 166, -31, -23, -6, 6, "Polymer", 1)
    box(g, 40, 80, -30, -24, -19, 19, "Rubber", 0.4)                                              # sling slot
    lathe(g, [(150, 14.5), (247, 14.5)], "Anod", 20)
    for k in range(6):
        box(g, 182 + k * 10, 186 + k * 10, -15.2, -14.2, -2.5, 2.5, "Rubber", 0.1)
    lathe(g, [(234, 0), (234, 17.5), (243, 17.5), (243, 0)], "Steel", 20)
    for k in range(4):
        a = D(45 + 90 * k)
        box(g, 236, 241, 17 * math.sin(a) - 2, 17 * math.sin(a) + 2, 17 * math.cos(a) - 2, 17 * math.cos(a) + 2, "Rubber", 0.1)
    # lower receiver with flared magwell
    prism(g, [(246, -20), (246, -6), (250, -4), (425, -4), (425, -20), (418, -24), (418, -36), (412, -40), (410, -72),
              (336, -74), (334, -40), (300, -36), (262, -26)], -11, 11, "Anod", 1.2)
    prism(g, [(334, -66), (412, -64), (414, -76), (334, -78)], -13.5, 13.5, "Anod", 1.0)
    for (u, v) in ((258, -14), (420, -12)):
        cylx(g, u, v, 11, 12.4, 3.6, "Steel", 12)
        cylx(g, u, v, -12.4, -11, 3.6, "Steel", 12)
    prism(g, [(326, -18), (342, -18), (342, -30), (334, -34), (326, -28)], 11, 13.6, "Steel", 0.4)  # bolt catch (left)
    cylx(g, 322, -30, -15, -11, 5, "Steel")                                                          # mag release (right)
    prism(g, [(312, -22), (334, -22), (334, -38), (312, -38)], -12.5, -11, "Anod", 0.6)              # release fence
    cylx(g, 270, -20, 11, 13.5, 6.5, "Steel")                                                        # selector (left)
    prism(g, [(266, -17), (288, -14), (289, -21), (266, -23)], 13.5, 15.5, "Steel", 0.4)
    # upper receiver (flat-top), rail, charging handle, forward assist, brass deflector, open dust cover
    prism(g, [(244, -4), (426, -4), (426, 18), (250, 18), (244, 12)], -13, 13, "Anod", 1.2)
    picatinny(g, 247, 424, 18, "Anod")
    box(g, 234, 252, 6, 16, -17, 17, "Anod", 1.2)
    box(g, 236, 244, 10, 16, 17, 24, "Anod", 0.8)
    box(g, 300, 356, 0, 12, -13.4, -13.0, "Rubber", 0.1)
    box(g, 300, 356, -10, -3, -15.5, -13, "Anod", 0.4)
    prism(g, [(284, 2), (298, 2), (298, 16), (290, 15)], -17, -13, "Anod", 0.6)
    rod(g, P(276, 7, -13), P(258, 7, -24), 8, 8, "Anod", 14)
    rod(g, P(258, 7, -24), P(255, 7, -26), 9.5, 9.5, "Steel", 14)
    # trigger guard, A2 pistol grip
    prism(g, [(290, -38), (294, -38), (296, -54), (334, -56), (334, -40), (338, -40), (338, -60), (292, -58)], -5.5, 5.5, "Anod", 0.6)
    prism(g, [(262, -26), (296, -30), (292, -44), (286, -56), (288, -62), (282, -70), (278, -84), (272, -100), (268, -114),
              (266, -120), (242, -118), (240, -110), (248, -80), (254, -50), (256, -34)], -13, 13, "Polymer", 3)
    # delta ring, M4 ribbed handguard, front cap
    lathe(g, [(425, 0), (425, 22), (428, 33), (440, 33), (442, 22), (442, 0)], "Anod", 24)
    lathe(g, [(442, 12), (442, 25), (447, 29), (605, 29), (611, 25), (611, 12)], "Polymer", 24, 0, 2, closed=True)
    for k in range(14):
        a = D(-80 + k * (340 / 13))
        if abs(math.degrees(a) - 90) < 15:
            continue
        cx, cv = 29 * math.cos(a), 2 + 29 * math.sin(a)
        bm_pts = [(cx - 2.2 * math.sin(a) * -1, cv), ]
        rr = [(28.5, -2.4), (31, -1.6), (31, 1.6), (28.5, 2.4)]
        pts = [((r) * math.cos(a) - w * math.sin(a), 2 + r * math.sin(a) + w * math.cos(a)) for (r, w) in rr]
        xsec(g, pts, 450, 600, "Polymer", 0.2)
    lathe(g, [(606, 0), (606, 30), (617, 30), (617, 0)], "Anod", 24)
    # barrel (M203 step), front sight base: ring, tower, ears, post, bayonet lug, swivel
    lathe(g, [(425, 9.5), (700, 9.5), (700, 10.8), (720, 10.8), (720, 9.0), (792, 9.0)], "Parker", 18)
    box(g, 618, 646, -15, 15, -13, 13, "Parker", 2.5)
    prism(g, [(620, 15), (644, 15), (637, 40), (627, 40)], -7, 7, "Parker", 1)
    for s in (1, -1):
        prism(g, [(622, 30), (641, 30), (638, 64), (626, 64)], 7.5 * s, 10.5 * s, "Parker", 0.5)
    cylz(g, 632, 0, 40, 60, 1.6, "Steel", 8)
    box(g, 636, 652, -15, -26, -5, 5, "Parker")
    ring_rod_loop(g, P(630, -30, 0), 7, 1.5, "Steel")
    # A2 birdcage (closed bottom), bore
    lathe(g, [(790, 0), (790, 11), (795, 11.5), (836, 11.5), (838, 10), (838, 0)], "Parker", 24)
    for k in range(4):
        u = 802 + k * 8.5
        box(g, u, u + 5, 11.0, 11.9, -2.5, 2.5, "Rubber", 0.1)
        for s in (1, -1):
            box(g, u, u + 5, -2.5, 2.5, 11.0 * s, 11.9 * s, "Rubber", 0.1)
    lathe(g, [(837.8, 3.0), (838.6, 3.0)], "Rubber", 12)
    # folded rear BUIS
    box(g, 248, 270, 27, 34, -10, 10, "Anod", 1)
    # Aimpoint CompM4 on a QRP2 mount: base, throw knob, riser, ring, 30 mm tube, turrets, battery housing, lenses
    box(g, 298, 346, 27, 37, -14, 14, "Anod", 1)
    cylx(g, 322, 32, 14, 24, 7, "Anod")
    box(g, 318, 326, 24, 40, 22, 26, "Anod", 0.6)
    box(g, 306, 340, 37, 52, -9, 9, "Anod", 1)
    # ring and tube are open (hollow): the eye looks through the 30 mm tube at the dot
    lathe(g, [(306, 19.6), (306, 21.5), (340, 21.5), (340, 19.6)], "Anod", 24, 0, 68, closed=True)
    lathe(g, [(268, 16), (268, 18), (272, 19.5), (396, 19.5), (400, 20.5), (404, 20.5), (404, 17.5), (272, 16)], "Polymer", 28, 0, 68,
          closed=True)
    cylz(g, 352, 0, 86, 96, 10, "Polymer", 16)
    cylz(g, 352, 0, 96, 99, 8.5, "Rubber", 16)
    cylx(g, 352, 68, -28, -18, 10, "Polymer", 16)
    cylx(g, 352, 68, -31, -28, 8.5, "Rubber", 16)
    cylx(g, 384, 58, -30, -16, 9, "Polymer", 16)
    cylx(g, 384, 58, -34, -30, 11, "Rubber", 16)
    lathe(g, [(271.5, 15.5), (272.2, 15.5)], "Lens", 24, 0, 68)
    lathe(g, [(402.8, 16.5), (403.5, 16.5)], "Lens", 24, 0, 68)
    # sling swivel at the stock
    ring_rod_loop(g, P(30, -102, 0), 7, 1.5, "Steel")


def m4a1():
    global U0
    sc = new_scene("M4A1")
    U0 = 305.0
    root = empty("m4a1", (0, 0, 0))
    gun = empty("m4a1.gun", (0, 0, 0), root)
    m4a1_body("m4a1.body")
    stanag("m4a1.mag")
    prism("m4a1.trigger", [(302, -30), (308, -30), (310, -40), (309, -50), (305, -51), (306, -40)], -3, 3, "Steel", 0.5)
    box("m4a1.bolt", 300, 356, 1, 11, -13.0, -11.0, "Steel", 0.4)
    cylx("m4a1.bolt", 344, 6, -13.2, -12.8, 3, "Steel", 10)
    # the red dot (emissive), drawn inside the tube on the optical axis
    bm = bmesh.new()
    r = 0.9
    vs = [bm.verts.new(P(360, 68 - r, -r)), bm.verts.new(P(360, 68 - r, r)), bm.verts.new(P(360, 68 + r, r)),
          bm.verts.new(P(360, 68 + r, -r))]
    bm.faces.new(vs)
    ob = _mk("dot", bm, "RedDot", "m4a1.dot")
    for n, li in enumerate(ob.data.polygons[0].loop_indices):
        ob.data.uv_layers[0].data[li].uv = [(0, 0), (1, 0), (1, 1), (0, 1)][n]
    flash("m4a1.flash", 839, 0, 90, 34)
    gz = Vector((0, -0.025, 0.084)).normalized()
    hand("m4a1.arm_r", P(268, -76, 0), gz, Vector((-0.55, 0.83, 0)), 15, "GloveCoyote", "OCP", (-0.40, 0.84, -0.36),
         trigger=P(307, -44, -1))
    hand("m4a1.arm_l", P(545, 2, 0), Vector((0, -1, 0)), Vector((0.75, 0, -0.66)), 30, "GloveCoyote", "OCP",
         (0.46, 0.80, -0.38), mirror=True)
    join_node("m4a1.body", gun)
    join_node("m4a1.mag", gun, P(375, -30, 0))
    join_node("m4a1.trigger", gun, P(305, -30, 0))
    join_node("m4a1.bolt", gun, P(328, 6, 0))
    join_node("m4a1.dot", gun, P(360, 68, 0))
    join_node("m4a1.flash", gun, P(839, 0, 0))
    join_node("m4a1.arm_r", gun, P(255, -110, -20))
    join_node("m4a1.arm_l", gun, P(530, -26, 20))
    empty("m4a1.sight", P(190, 68, 0), gun)
    empty("m4a1.muzzle", P(839, 0, 0), gun)
    G, M, A, B = (bpy.data.objects[n] for n in ("m4a1.gun", "m4a1.mag", "m4a1.arm_l", "m4a1.bolt"))
    Z = (0, 0, 0)
    tilt = ((0.015, 0.0, 0.025), (-8, -20, 4))
    mk = [(0, Z, Z), (10, Z, Z), (13, (0, 0, -0.05), Z), (18, (0, 0.02, -0.36), (-8, 0, 0)), (28, (0, 0.02, -0.36), (-8, 0, 0)),
          (34, (0, 0, -0.07), Z), (38, (0, 0, 0.006), Z), (40, Z, Z)]
    ak = [(0, Z, Z), (6, (0, 0.16, -0.10), (0, 0, -8)), (10, (0, 0.16, -0.10), (0, 0, -8)), (13, (0, 0.16, -0.15), (0, 0, -8)),
          (18, (0.03, 0.2, -0.43), (0, 0, -8)), (28, (0.03, 0.2, -0.43), (0, 0, -8)), (34, (0, 0.165, -0.17), (0, 0, -8)),
          (38, (0, 0.165, -0.10), (0, 0, -8)), (40, (0, 0.165, -0.10), (0, 0, -8))]
    clip("m4a1_reload", G, [(0, Z, Z), (6, *tilt), (40, *tilt), (50, Z, Z), (56, Z, Z)])
    clip("m4a1_reload", M, mk + [(56, Z, Z)])
    clip("m4a1_reload", A, ak + [(48, Z, Z), (56, Z, Z)])
    clip("m4a1_reload_empty", G, [(0, Z, Z), (6, *tilt), (48, *tilt), (58, Z, Z), (64, Z, Z)])
    clip("m4a1_reload_empty", M, mk + [(64, Z, Z)])
    clip("m4a1_reload_empty", A, ak + [(44, (0.03, 0.205, -0.03), Z), (46, (0.028, 0.205, -0.038), Z), (48, (0.03, 0.2, -0.03), Z),
                                       (56, Z, Z), (64, Z, Z)])
    clip("m4a1_reload_empty", B, [(0, (0, 0.075, 0), Z), (46, (0, 0.075, 0), Z), (47, Z, Z), (64, Z, Z)])
    return sc


# ====================================================================================== RPG-7V2
def pg7vl(g, u0, full=False):
    """PG-7VL round. u0 = position of the launcher muzzle face (the round's datum). full=True adds the booster."""
    o = u0 - 950
    lathe(g, [(o + 880, 0), (o + 880, 19), (o + 952, 19), (o + 960, 21)], "Steel", 18)                 # motor in the muzzle
    lathe(g, [(o + 960, 21), (o + 990, 24), (o + 1015, 38), (o + 1040, 46.5)], "Warhead", 28)           # rear ogive
    lathe(g, [(o + 1040, 46.5), (o + 1160, 46.5)], "Warhead", 32)                                        # 93 mm body
    lathe(g, [(o + 1060, 47), (o + 1072, 47)], "Rubber", 32)                                             # black band
    lathe(g, [(o + 1160, 46.5), (o + 1190, 40), (o + 1240, 25), (o + 1290, 13), (o + 1322, 8)], "Warhead", 28)
    lathe(g, [(o + 1322, 8), (o + 1345, 6), (o + 1348, 0)], "Steel", 14)
    lathe(g, [(o + 1345, 3.2), (o + 1372, 2.8), (o + 1375, 0)], "Steel", 10)                             # piezo probe
    for k in range(4):                                                                                    # motor fins (folded)
        a = D(45 + 90 * k)
        pts = [((19) * math.cos(a) - 1 * math.sin(a), 19 * math.sin(a) + 1 * math.cos(a)),
               ((23) * math.cos(a) - 1 * math.sin(a), 23 * math.sin(a) + 1 * math.cos(a)),
               ((23) * math.cos(a) + 1 * math.sin(a), 23 * math.sin(a) - 1 * math.cos(a)),
               ((19) * math.cos(a) + 1 * math.sin(a), 19 * math.sin(a) - 1 * math.cos(a))]
        xsec(g, pts, o + 962, o + 1000, "Steel", 0.1)
    if full:
        lathe(g, [(o + 420, 0), (o + 420, 19.5), (o + 880, 19.5)], "Polymer", 18)                        # booster charge
        lathe(g, [(o + 405, 0), (o + 405, 14), (o + 420, 16)], "Steel", 14)
        for k in range(4):
            a = D(90 * k)
            pts = [(18 * math.cos(a) - 1 * math.sin(a), 18 * math.sin(a) + 1 * math.cos(a)),
                   (40 * math.cos(a) - 1 * math.sin(a), 40 * math.sin(a) + 1 * math.cos(a)),
                   (40 * math.cos(a) + 1 * math.sin(a), 40 * math.sin(a) - 1 * math.cos(a)),
                   (18 * math.cos(a) + 1 * math.sin(a), 18 * math.sin(a) - 1 * math.cos(a))]
            xsec(g, pts, o + 408, o + 450, "Steel", 0.1)


def rpg7_body(g):
    # venturi bell (hollow) and tube sections
    lathe(g, [(0, 44), (6, 45.5), (40, 40), (100, 30), (140, 24), (150, 23), (150, 18), (100, 25), (40, 35), (0, 39)],
          "RpgGreen", 28, closed=True)
    lathe(g, [(150, 23), (192, 23)], "RpgGreen", 24)
    # rear heat shield (shoulder), clamp bands
    lathe(g, [(190, 24), (196, 33.5), (384, 34), (390, 25)], "Wood", 28, closed=False)
    for u in (198, 376):
        lathe(g, [(u, 0), (u, 35.2), (u + 9, 35.2), (u + 9, 0)], "RpgGreen", 28)
    # breech / chamber section with the firing mechanism
    lathe(g, [(388, 26.5), (472, 26.5)], "RpgGreen", 24)
    prism(g, [(395, -22), (472, -22), (472, -36), (462, -44), (400, -44)], -11, 11, "RpgGreen", 1.2)
    box(g, 394, 410, -36, -22, -6, 6, "Steel", 0.6)                                             # hammer housing
    cylx(g, 452, -33, 11, 14.5, 3.6, "Steel")                                                    # safety
    cylx(g, 452, -33, -14.5, -11, 3.6, "Steel")
    prism(g, [(440, -44), (468, -44), (466, -60), (456, -100), (452, -128), (424, -128), (426, -112), (434, -64)],
          -14, 14, "Stipple", 3)                                                                 # pistol grip
    prism(g, [(470, -44), (500, -44), (500, -66), (462, -67), (462, -62), (495, -61), (495, -48), (470, -48)], -5, 5, "Steel", 0.5)
    # front heat shield, clamps
    lathe(g, [(470, 24), (476, 33.5), (644, 34), (650, 24)], "Wood", 28)
    for u in (478, 634):
        lathe(g, [(u, 0), (u, 35.2), (u + 9, 35.2), (u + 9, 0)], "RpgGreen", 28)
    # front tube (40 mm), muzzle ring
    lathe(g, [(648, 20), (930, 20)], "RpgGreen", 24)
    lathe(g, [(930, 19), (930, 22.5), (950, 22.5), (950, 19)], "RpgGreen", 24, closed=False)
    lathe(g, [(950, 19.5), (950, 20.5)], "Rubber", 24)
    # iron sights: front folding post, rear leaf with range slider
    box(g, 884, 906, 19, 26, -7, 7, "RpgGreen")
    prism(g, [(891, 26), (899, 26), (897, 52), (893, 52)], -2, 2, "Steel", 0.4)
    box(g, 456, 484, 26, 32, -9, 9, "RpgGreen")
    box(g, 467, 474, 32, 54, -9, 9, "Steel", 0.4)
    box(g, 465, 476, 40, 44, -10, 10, "Steel", 0.4)
    # PGO-7V3 on the left bracket: bracket, dovetail clamp, body, objective + hood, eyepiece, rubber eyecup,
    # range drum, reticle illumination, battery cap
    prism(g, [(402, -12), (468, -12), (468, 20), (402, 20)], 24, 34, "RpgGreen", 1)
    box(g, 410, 462, 18, 34, 32, 50, "RpgGreen", 1.5)
    box(g, 384, 500, 32, 82, 46, 84, "RpgGreen", 5)
    lathe(g, [(500, 0), (500, 18), (520, 19), (526, 21.5), (530, 21.5), (530, 0)], "RpgGreen", 24, 65, 57)
    lathe(g, [(528.5, 15), (529.5, 15)], "LensSolid", 24, 65, 57)
    lathe(g, [(366, 0), (366, 14), (384, 14), (384, 0)], "RpgGreen", 20, 65, 57)
    lathe(g, [(318, 0), (318, 20), (326, 22), (352, 17), (366, 15.5), (366, 0)], "Rubber", 20, 65, 57)
    lathe(g, [(318.5, 11), (319.3, 11)], "LensSolid", 18, 65, 57)
    cylz(g, 446, 65, 82, 92, 12, "RpgGreen", 18)
    cylz(g, 446, 65, 92, 98, 10, "Rubber", 18)
    cylx(g, 412, 64, 84, 92, 7, "Rubber", 12)
    cylx(g, 476, 50, 84, 94, 8, "RpgGreen", 12)
    # sling swivels
    ring_rod_loop(g, P(206, -40, 0), 8, 1.8, "Steel")
    ring_rod_loop(g, P(880, -26, 0), 8, 1.8, "Steel")


def rpg7():
    global U0
    sc = new_scene("RPG7")
    U0 = 452.0
    root = empty("rpg7", (0, 0, 0))
    gun = empty("rpg7.gun", (0, 0, 0), root)
    rpg7_body("rpg7.body")
    pg7vl("rpg7.round", 950)
    prism("rpg7.trigger", [(476, -44), (482, -44), (484, -52), (483, -58), (479, -59), (480, -52)], -3, 3, "Steel", 0.5)
    flash("rpg7.flash", 951, 0, 160, 70)
    gz = Vector((0, -0.016, 0.084)).normalized()
    hand("rpg7.arm_r", P(446, -86, 0), gz, Vector((-0.55, 0.83, 0)), 15, "GloveBlack", "EMR", (-0.40, 0.84, -0.36),
         trigger=P(480, -52, -1))
    hand("rpg7.arm_l", P(570, -2, 0), Vector((0, -1, 0)), Vector((0.75, 0, -0.66)), 34, "GloveBlack", "EMR",
         (0.46, 0.80, -0.38), mirror=True)
    join_node("rpg7.body", gun)
    join_node("rpg7.round", gun, P(950, 0, 0))
    join_node("rpg7.trigger", gun, P(479, -44, 0))
    join_node("rpg7.flash", gun, P(951, 0, 0))
    join_node("rpg7.arm_r", gun, P(436, -120, -20))
    join_node("rpg7.arm_l", gun, P(555, -36, 20))
    empty("rpg7.sight", P(300, 57, 65), gun)            # eye behind the PGO-7V3 eyecup
    empty("rpg7.irons", P(330, 52, 0), gun)             # eye on the iron-sight line (rear leaf -> front post)
    empty("rpg7.muzzle", P(951, 0, 0), gun)
    G, R, A = (bpy.data.objects[n] for n in ("rpg7.gun", "rpg7.round", "rpg7.arm_l"))
    Z = (0, 0, 0)
    low = ((0.05, 0.12, -0.16), (-30, -12, 14))
    clip("rpg7_reload", G, [(0, Z, Z), (12, *low), (64, *low), (78, Z, Z), (84, Z, Z)])
    clip("rpg7_reload", R, [(0, (0.06, -0.10, -0.42), (55, 0, 0)), (14, (0.05, -0.30, -0.36), (40, 0, 0)),
                            (30, (0, -0.46, -0.02), (6, 0, 0)), (36, (0, -0.44, 0), Z), (50, (0, -0.06, 0), Z), (54, Z, Z), (84, Z, Z)])
    clip("rpg7_reload", A, [(0, Z, Z), (8, (0.05, 0.05, -0.30), (0, 0, -10)), (14, (0.05, -0.08, -0.30), (0, 0, -10)),
                            (30, (0.0, -0.24, -0.02), (0, 0, -5)), (36, (0.0, -0.22, 0.0), (0, 0, -5)),
                            (50, (0.0, 0.0, 0.0), Z), (84, Z, Z)])
    return sc


def mag_scene(name, fn, u0, origin):
    global U0
    sc = new_scene(name)
    U0 = u0
    fn(name.lower())
    join_node(name.lower(), None, origin)
    return sc


# ====================================================================================== ORSIS T-5000 (.338 LM)
# ORSIS T-5000M .338 Lapua Magnum: 1270 mm (stock unfolded) / 1020 folded, barrel 700 mm, 5-rd box, 6.2-7.5 kg,
# aluminium chassis + side-folding skeleton stock, two-lug bolt, Harris-type bipod, 5-25x56 scope (34 mm tube).
SV = 78.0           # scope axis above the bore (rail top 37 + 34 mm rings)


def t5000_mag(g):
    # AICS-pattern steel 5-rd magazine for .338 LM (93.5 mm cartridge, 14.9 mm case head, double stack)
    box(g, 446, 538, -104, -50, -16, 16, "Steel", 1.0)
    for s in (1, -1):
        box(g, 456, 528, -96, -60, 16 * s, 16.8 * s, "Steel", 0.3)                  # pressed side panel
    box(g, 442, 542, -112, -104, -18, 18, "Polymer", 1.5)                            # floorplate
    lathe(g, [(450, 0), (450, 7.4), (500, 7.4), (512, 4.8), (520, 4.8)], "Brass", 12, 4, -46)    # top round
    lathe(g, [(520, 4.3), (536, 3.0), (543, 0)], "Brass", 10, 4, -46)


def t5000_body(g):
    # --- skeleton stock (folds to the right), butt pad, cheek riser, monopod knob
    prism(g, [(0, -124), (16, -124), (16, 44), (0, 44)], -21, 21, "Rubber", 3)
    prism(g, [(16, -126), (40, -126), (40, 46), (16, 46)], -19, 19, "Anod", 2)
    prism(g, [(40, 20), (252, 20), (252, 36), (40, 36)], -14, 14, "Anod", 1.5)
    prism(g, [(40, -126), (88, -126), (252, -42), (252, -22), (232, -22), (74, -104), (40, -104)], -14, 14, "Anod", 1.5)
    prism(g, [(150, -64), (168, -72), (172, 20), (154, 20)], -10, 10, "Anod", 1)
    prism(g, [(66, 36), (218, 36), (218, 60), (78, 64)], -16, 16, "Polymer", 3)      # cheek riser
    for u in (100, 190):
        cylz(g, u, 0, 36, 40, 5, "Steel", 10)
    cylx(g, 120, 50, 16, 22, 6, "Steel")                                            # riser lock
    cylx(g, 230, 0, 14, 24, 8, "Steel")                                             # length-of-pull lock
    box(g, 52, 76, -140, -126, -9, 9, "Steel", 1)                                   # rear monopod knob
    cylz(g, 64, 0, -152, -140, 12, "Rubber", 16)
    # --- hinge block (right side)
    box(g, 252, 288, -50, 34, -20, 20, "Anod", 2)
    cylz(g, 270, -22, -44, 28, 6, "Steel")
    box(g, 255, 266, -10, 20, 20, 24, "Steel", 0.5)                                 # fold latch
    # --- chassis around the action, magazine well, trigger guard
    prism(g, [(288, -50), (288, 14), (560, 14), (560, -34), (546, -50), (540, -58), (438, -58), (432, -50)], -24, 24, "Anod", 2)
    box(g, 438, 542, -80, -58, -21, 21, "Anod", 1.5)                                # mag-well flare
    box(g, 430, 440, -84, -60, -6, 6, "Steel", 0.6)                                 # mag release lever
    prism(g, [(372, -50), (380, -50), (384, -66), (426, -68), (430, -50), (436, -50), (434, -74), (378, -73)], -6, 6, "Anod", 0.8)
    for (u, v) in ((300, -20), (420, -30), (530, -20), (530, 2)):
        cylx(g, u, v, 24, 25.2, 3.4, "Steel", 10)
        cylx(g, u, v, -25.2, -24, 3.4, "Steel", 10)
    # --- round stainless action, recoil lug, ejection port, bolt shroud stays with the bolt node
    lathe(g, [(300, 19.5), (548, 19.5)], "Parker", 28)
    lathe(g, [(548, 0), (548, 22), (560, 22), (560, 0)], "Parker", 28)              # recoil lug / barrel nut
    box(g, 392, 474, -1, 15, -20.4, -18.8, "Rubber", 0.1)                           # ejection port
    box(g, 300, 312, 12, 22, -14, -8, "Steel", 0.5)                                 # 3-position safety
    # --- 20 MOA one-piece rail
    prism(g, [(292, 16), (612, 16), (612, 28), (292, 25)], -11, 11, "Anod", 0.8)
    picatinny(g, 294, 610, 28, "Anod")
    # --- pistol grip (vertical, stippled) and cap
    prism(g, [(330, -50), (372, -50), (370, -62), (366, -82), (360, -112), (354, -140), (316, -142), (312, -132), (320, -102),
              (326, -62)], -15, 15, "Stipple", 3)
    prism(g, [(312, -138), (356, -136), (355, -146), (314, -148)], -15.6, 15.6, "Polymer", 1.5)
    # --- M-LOK forend, slots, bottom rail stub for the bipod, front QD cup
    xsec(g, [(-26, -60), (26, -60), (31, -52), (31, 10), (26, 17), (-26, 17), (-31, 10), (-31, -52)], 560, 900, "Anod", 2)
    for i in range(5):
        for s in (1, -1):
            box(g, 590 + i * 60, 628 + i * 60, -36, -20, 31.0 * s, 31.6 * s, "Rubber", 0.3)
    for i in range(4):
        box(g, 610 + i * 70, 640 + i * 70, 17, 17.6, -8, 8, "Rubber", 0.2)
    picatinny(g, 812, 880, -60, "Anod", down=True)
    cylx(g, 880, -40, 31, 33.5, 7, "Steel")
    # --- 700 mm heavy barrel (26 -> 21 mm), muzzle brake with three side ports
    lathe(g, [(560, 13), (900, 12.2), (1190, 10.5)], "Parker", 24)
    lathe(g, [(1188, 0), (1188, 14), (1270, 14), (1270, 0)], "Parker", 24)
    for k in range(3):
        for s in (1, -1):
            box(g, 1200 + k * 22, 1214 + k * 22, -7, 7, 13.4 * s, 14.6 * s, "Rubber", 0.2)
        box(g, 1204 + k * 22, 1212 + k * 22, 13.4, 14.6, -3, 3, "Rubber", 0.2)
    lathe(g, [(1269.8, 4.6), (1270.6, 4.6)], "Rubber", 12)
    # --- Harris-type bipod, legs folded forward under the barrel
    box(g, 826, 866, -84, -66, -16, 16, "Steel", 1.5)
    cylx(g, 846, -76, -22, 22, 5, "Steel")
    for s in (1, -1):
        rod(g, P(852, -78, 19 * s), P(1086, -82, 19 * s), 6.5, 6.0, "Anod", 10)
        rod(g, P(1000, -82, 19 * s), P(1094, -84, 19 * s), 5.0, 5.0, "Steel", 10)
        sphere(g, P(1100, -84, 19 * s), 9, "Rubber", seg=10)
    # --- 5-25x56 scope: eyepiece, power ring, tube, turret saddle + turrets, objective bell, lenses
    lathe(g, [(290, 21), (294, 23.5), (350, 23.5), (360, 21), (376, 19), (384, 18.5)], "Anod", 28, 0, SV)
    lathe(g, [(384, 19.6), (414, 19.6)], "Rubber", 28, 0, SV)
    for k in range(7):
        lathe(g, [(387 + k * 4, 20.6), (389 + k * 4, 20.6)], "Rubber", 28, 0, SV)
    lathe(g, [(414, 17), (476, 17)], "Anod", 28, 0, SV)
    box(g, 476, 534, SV - 20, SV + 20, -22, 22, "Anod", 4)
    lathe(g, [(534, 17), (626, 17), (664, 30), (700, 33.5), (738, 33.5)], "Anod", 32, 0, SV)
    lathe(g, [(736, 34.2), (738, 34.2)], "Rubber", 32, 0, SV)
    lathe(g, [(737, 30), (737.6, 30)], "LensSolid", 28, 0, SV)
    lathe(g, [(291, 19.5), (291.6, 19.5)], "LensSolid", 24, 0, SV)
    cylz(g, 505, 0, SV + 20, SV + 36, 15, "Anod", 20)                               # elevation turret (MRAD)
    cylz(g, 505, 0, SV + 36, SV + 44, 16, "Rubber", 20)
    cylx(g, 505, SV, -22, -36, 14, "Anod", 20)                                      # windage (right)
    cylx(g, 505, SV, -36, -42, 15, "Rubber", 20)
    cylx(g, 505, SV, 22, 34, 14, "Anod", 20)                                        # side focus / parallax wheel
    cylx(g, 505, SV, 34, 38, 27, "Anod", 24)
    cylx(g, 470, SV, 19, 27, 6, "Rubber", 10)                                       # illumination knob
    # rings (34 mm) with cross-bolts on the rail
    for u in (430, 588):
        lathe(g, [(u, 22), (u + 20, 22)], "Anod", 28, 0, SV)
        box(g, u, u + 20, 36, SV - 16, -12, 12, "Anod", 1.5)
        cylx(g, u + 10, 42, -16, 16, 4.5, "Steel")
        for s in (1, -1):
            cylx(g, u + 10, SV + 12, 14 * s, 17 * s, 2.6, "Steel", 8)
    # sling QD cups
    cylx(g, 30, -110, 19, 21.5, 6, "Steel")


def t5000():
    global U0
    sc = new_scene("T5000")
    U0 = 400.0
    root = empty("t5000", (0, 0, 0))
    gun = empty("t5000.gun", (0, 0, 0), root)
    t5000_body("t5000.body")
    t5000_mag("t5000.mag")
    prism("t5000.trigger", [(394, -50), (400, -50), (402, -58), (401, -65), (397, -66), (398, -58)], -3, 3, "Steel", 0.5)
    # bolt: shroud + cocking indicator behind the action, body through the port, handle with the knob (right side)
    lathe("t5000.bolt", [(270, 0), (270, 12), (300, 14), (302, 0)], "Steel", 20)
    box("t5000.bolt", 396, 470, 1, 13, -19.0, -16, "Steel", 0.3)
    rod("t5000.bolt", P(338, 0, -16), P(346, -26, -48), 6.0, 5.0, "Steel", 12)
    rod("t5000.bolt", P(346, -26, -48), P(350, -34, -64), 9.5, 10.5, "Steel", 14)
    sphere("t5000.bolt", P(350, -34, -64), 10.5, "Steel", seg=12)
    flash("t5000.flash", 1271, 0, 130, 46)
    hand("t5000.arm_r", P(342, -94, 0), None, None, 0, None, None, None)
    hand("t5000.arm_l", P(720, -70, 0), None, None, 0, None, None, None)
    join_node("t5000.body", gun)
    join_node("t5000.mag", gun, P(490, -58, 0))
    join_node("t5000.trigger", gun, P(397, -50, 0))
    bolt = join_node("t5000.bolt", gun, P(340, 0, 0))
    join_node("t5000.flash", gun, P(1271, 0, 0))
    join_node("t5000.arm_r", gun)
    join_node("t5000.arm_l", gun)
    empty("t5000.sight", P(200, SV, 0), gun)            # eye behind the eyepiece (90 mm eye relief)
    empty("t5000.muzzle", P(1271, 0, 0), gun)
    G, M, AL, AR = (bpy.data.objects[n] for n in ("t5000.gun", "t5000.mag", "t5000.arm_l", "t5000.arm_r"))
    Z = (0, 0, 0)
    # bolt cycle: lift 80 deg, pull 105 mm, push, lock (the right hand rides on the knob)
    knob = P(350, -34, -64) - P(340, 0, 0)
    grip = P(342, -94, 0) - P(340, 0, 0)
    bk = [(0, Z, 0), (4, Z, 80), (9, (0, 0.105, 0), 80), (13, (0, 0.105, 0), 80), (17, Z, 80), (21, Z, 0)]

    def knob_at(t, a):
        return Matrix.Rotation(D(a), 3, 'Y') @ knob + Vector(t)

    def bolt_clip(name, f0, total):
        keys = [(0, Z, Z)] if f0 > 0 else []
        keys += [(f0 + f, t, (0, a, 0)) for (f, t, a) in bk]
        keys.append((total, Z, Z))
        clip(name, bolt, keys)
        hk = [(0, Z, Z)] if f0 > 0 else [(0, Z, Z)]
        hk.append((f0 + 2, tuple(knob_at(Z, 0) - grip + Vector((0, 0.01, -0.02))), Z))
        for (f, t, a) in bk[1:-1]:
            hk.append((f0 + f, tuple(knob_at(t, a) - grip + Vector((0, 0.01, -0.02))), Z))
        hk.append((f0 + 23, tuple(knob_at(Z, 0) - grip + Vector((0, 0.01, -0.02))), Z))
        hk.append((f0 + 27, Z, Z))
        hk.append((max(total, f0 + 28), Z, Z))
        clip(name, AR, hk)

    clip("t5000_bolt", G, [(0, Z, Z), (3, (0, 0, 0.006), (2, 6, 0)), (19, (0, 0, 0.006), (2, 6, 0)), (26, Z, Z), (28, Z, Z)])
    bolt_clip("t5000_bolt", 0, 28)
    tilt = ((0.02, 0.0, 0.03), (-8, -22, 5))
    mag_keys = [(0, Z, Z), (10, Z, Z), (16, (0, 0, -0.06), Z), (24, (0, 0.03, -0.36), (-10, 0, 0)), (36, (0, 0.03, -0.36), (-10, 0, 0)),
                (46, (0, 0, -0.06), Z), (50, Z, Z)]
    to_mag = (0, (720 - 490) / 1000.0, -0.048)
    arm_keys = [(0, Z, Z), (8, to_mag, Z), (12, to_mag, Z), (16, (0, 0.23, -0.108), Z), (24, (0.03, 0.26, -0.42), Z),
                (36, (0.03, 0.26, -0.42), Z), (46, (0, 0.23, -0.108), Z), (50, to_mag, Z), (54, to_mag, Z)]
    clip("t5000_reload", G, [(0, Z, Z), (8, *tilt), (52, *tilt), (64, Z, Z), (72, Z, Z)])
    clip("t5000_reload", M, mag_keys + [(72, Z, Z)])
    clip("t5000_reload", AL, arm_keys + [(64, Z, Z), (72, Z, Z)])
    clip("t5000_reload_empty", G, [(0, Z, Z), (8, *tilt), (52, *tilt), (60, Z, Z), (88, Z, Z), (91, Z, Z)])
    clip("t5000_reload_empty", M, mag_keys + [(91, Z, Z)])
    clip("t5000_reload_empty", AL, arm_keys + [(64, Z, Z), (91, Z, Z)])
    bolt_clip("t5000_reload_empty", 60, 91)
    return sc


# ====================================================================================== PKP Pecheneg (6P41)
# 6P41 "Pecheneg": 7.62x54R, 1155 mm, barrel 658 mm (finned, in a forced-air shroud), 650 rds/min, 825 m/s,
# 100-rd belt box under the receiver, carrying handle over the barrel, bipod at the muzzle, rear sight to 1500 m.

def pkp_round(g, cx, cv, u=430):
    lathe(g, [(u, 0), (u, 7.2), (u + 2, 7.2), (u + 3, 6.1), (u + 48, 5.8), (u + 53, 4.2), (u + 60, 4.2)], "Brass", 10, cx, cv)
    lathe(g, [(u + 60, 3.9), (u + 70, 3.6), (u + 77, 0)], "Steel", 8, cx, cv)
    lathe(g, [(u + 12, 7.0), (u + 20, 7.0)], "Steel", 10, cx, cv)                   # belt link loops
    lathe(g, [(u + 36, 6.6), (u + 44, 6.6)], "Steel", 10, cx, cv)


def pkp_box(g):
    # 100-rd steel box: 180 x 142 x 88 mm, stamped ribs, lid, latch, hook; the belt rises from it into the feed tray
    box(g, 405, 585, -190, -56, -46, 42, "RpgGreen", 4)
    for v in (-84, -112, -140, -168):
        for s, x in ((1, 42), (-1, -46)):
            box(g, 418, 572, v, v + 9, x, x + s * 0.9, "RpgGreen", 0.4)
    box(g, 400, 590, -56, -48, -47, 43, "RpgGreen", 1.2)                            # lid
    box(g, 584, 598, -118, -80, -10, 10, "Steel", 1)                                # latch
    box(g, 470, 522, -48, -40, -20, 20, "Steel", 0.8)                               # mounting hook
    cylz(g, 495, -2, -192, -190, 30, "RpgGreen", 16)                                # bottom boss
    for i, (x, v) in enumerate(((-38, -42), (-48, -28), (-52, -12), (-46, 4), (-34, 16))):
        pkp_round(g, x, v)


def pkp_body(g):
    # --- skeleton stock (polymer) with butt plate
    prism(g, [(0, -110), (16, -110), (16, 34), (0, 34)], -20, 20, "Rubber", 3)
    prism(g, [(16, 12), (292, 20), (292, 36), (16, 34)], -18, 18, "Polymer", 2)
    prism(g, [(16, -110), (62, -110), (205, -40), (292, -40), (292, -22), (200, -24), (52, -88), (16, -88)], -18, 18, "Polymer", 2)
    prism(g, [(16, -112), (40, -112), (40, 36), (16, 36)], -19, 19, "Polymer", 2)
    box(g, 250, 300, -46, 38, -20, 20, "Polymer", 2)
    # --- stamped receiver, rivets, side dovetail (left), trunnion
    prism(g, [(290, -46), (290, 30), (300, 38), (560, 38), (575, 32), (592, 22), (592, -30), (562, -46)], -22, 22, "Parker", 1.2)
    for (u, v) in ((310, -30), (360, -36), (430, -36), (500, -36), (560, -20), (575, 10)):
        cylx(g, u, v, 22, 23.2, 2.6, "Steel", 10)
        cylx(g, u, v, -23.2, -22, 2.6, "Steel", 10)
    box(g, 370, 480, -12, 8, 22, 26, "Parker", 0.6)                                 # optic dovetail
    box(g, 575, 612, -34, 24, -21, 21, "Parker", 1.5)
    box(g, 310, 330, 30, 46, -10, 10, "Steel", 0.8)                                 # cover latch
    # --- rear sight (tangent leaf to 1500 m) ahead of the cover hinge
    box(g, 524, 576, 38, 46, -12, 12, "Parker", 0.8)
    prism(g, [(536, 46), (566, 46), (566, 50), (538, 58)], -7, 7, "Parker", 0.4)
    box(g, 548, 557, 47, 54, -9, 9, "Steel", 0.4)
    box(g, 538, 541, 54, 59, -7, -2.2, "Parker", 0.2)                               # notch ears
    box(g, 538, 541, 54, 59, 2.2, 7, "Parker", 0.2)
    # --- pistol grip, trigger guard, charging-handle slot
    prism(g, [(318, -46), (352, -46), (350, -58), (345, -80), (340, -112), (334, -140), (300, -142), (296, -132), (304, -100),
              (310, -60)], -15, 15, "Stipple", 3)
    prism(g, [(298, -138), (336, -136), (335, -146), (300, -148)], -15.6, 15.6, "Polymer", 1.5)
    prism(g, [(352, -46), (358, -46), (362, -62), (404, -64), (408, -46), (414, -46), (412, -70), (356, -69)], -5, 5, "Steel", 0.6)
    box(g, 420, 545, -36, -26, -22.6, -22.0, "Rubber", 0.1)
    # --- gas tube under the barrel, gas block with regulator
    lathe(g, [(592, 10), (895, 10)], "Parker", 16, 0, -30)
    box(g, 885, 925, -42, 12, -13, 13, "Parker", 2)
    cylx(g, 905, -30, -18, 18, 7, "Steel")
    # --- barrel: finned forced-air section in the shroud, front extension, front sight / bipod block, flash hider
    lathe(g, [(612, 23), (1012, 21)], "Parker", 28)
    for k in range(9):                                                               # shroud ribs
        lathe(g, [(640 + k * 40, 23.6), (650 + k * 40, 23.6)], "Parker", 28)
    for k in range(8):
        a = D(22.5 + 45 * k)
        pts = [(21.2 * math.cos(a) - 3, 21.2 * math.sin(a) - 3), (21.2 * math.cos(a) + 3, 21.2 * math.sin(a) - 3),
               (21.2 * math.cos(a) + 3, 21.2 * math.sin(a) + 3), (21.2 * math.cos(a) - 3, 21.2 * math.sin(a) + 3)]
        xsec(g, pts, 990, 1004, "Rubber", 0.1)
    lathe(g, [(1012, 13), (1082, 12)], "Parker", 20)
    box(g, 1036, 1076, -28, 16, -16, 16, "Parker", 2)
    for s in (1, -1):
        prism(g, [(1044, 16), (1068, 16), (1066, 64), (1050, 64)], 8 * s, 11 * s, "Parker", 0.6)
    prism(g, [(1053, 16), (1062, 16), (1060, 58), (1055, 58)], -2.4, 2.4, "Parker", 0.3)
    lathe(g, [(1080, 0), (1080, 14), (1155, 14), (1155, 0)], "Parker", 22)
    for k in range(4):
        a = D(45 + 90 * k)
        pts = [(13.4 * math.cos(a) - 2.5, 13.4 * math.sin(a) - 2.5), (13.4 * math.cos(a) + 2.5, 13.4 * math.sin(a) - 2.5),
               (13.4 * math.cos(a) + 2.5, 13.4 * math.sin(a) + 2.5), (13.4 * math.cos(a) - 2.5, 13.4 * math.sin(a) + 2.5)]
        xsec(g, pts, 1110, 1150, "Rubber", 0.1)
    lathe(g, [(1154.8, 4.4), (1155.6, 4.4)], "Rubber", 12)
    # --- carrying handle (polymer) over the barrel; open in the middle so the sight line passes through it
    prism(g, [(856, 22), (886, 22), (866, 92), (842, 92)], -8, 8, "Polymer", 2)
    for s in (1, -1):
        prism(g, [(594, 30), (624, 30), (648, 92), (618, 92)], 7 * s, 12 * s, "Polymer", 1.5)
    prism(g, [(618, 80), (866, 80), (866, 96), (618, 96)], -11, 11, "Polymer", 2)
    for k in range(6):
        box(g, 680 + k * 26, 692 + k * 26, 96, 99, -9, 9, "Rubber", 0.4)
    # --- bipod: legs folded back under the gas tube
    for s in (1, -1):
        rod(g, P(1050, -24, 14 * s), P(770, -44, 17 * s), 6.5, 6.0, "Parker", 10)
        box(g, 762, 786, -50, -38, 12 * s, 23 * s, "Parker", 1)
    cylx(g, 1050, -24, -20, 20, 6, "Steel")
    # sling swivels
    ring_rod_loop(g, P(40, -100, 20), 7, 1.6, "Steel")
    ring_rod_loop(g, P(900, -46, 0), 7, 1.6, "Steel")


def pkp():
    global U0
    sc = new_scene("PKP")
    U0 = 380.0
    root = empty("pkp", (0, 0, 0))
    gun = empty("pkp.gun", (0, 0, 0), root)
    pkp_body("pkp.body")
    pkp_box("pkp.mag")
    prism("pkp.cover", [(330, 38), (520, 38), (520, 52), (346, 52)], -21, 21, "Parker", 1.0)
    box("pkp.cover", 360, 500, 52, 55, -6, 6, "Parker", 0.4)                        # cover rib
    box("pkp.cover", 400, 500, 30, 38, -24, 24, "Steel", 0.4)                       # feed pawls under the cover
    prism("pkp.trigger", [(374, -46), (380, -46), (382, -54), (381, -61), (377, -62), (378, -54)], -3, 3, "Steel", 0.5)
    box("pkp.bolt", 496, 520, -38, -24, -38, -22, "Steel", 0.8)
    cylx("pkp.bolt", 510, -31, -52, -38, 7, "Steel")
    flash("pkp.flash", 1156, 0, 120, 44)
    hand("pkp.arm_r", P(324, -96, 0), None, None, 0, None, None, None)
    hand("pkp.arm_l", P(720, -40, 0), None, None, 0, None, None, None)
    join_node("pkp.body", gun)
    join_node("pkp.mag", gun, P(495, -48, 0))
    join_node("pkp.cover", gun, P(520, 45, 0))
    join_node("pkp.trigger", gun, P(377, -46, 0))
    join_node("pkp.bolt", gun, P(510, -31, 0))
    join_node("pkp.flash", gun, P(1156, 0, 0))
    join_node("pkp.arm_r", gun)
    join_node("pkp.arm_l", gun)
    empty("pkp.sight", P(400, 57, 0), gun)              # eye on the line rear notch (v 57) -> front post tip (v 58)
    empty("pkp.muzzle", P(1156, 0, 0), gun)
    G, M, C, A, B = (bpy.data.objects[n] for n in ("pkp.gun", "pkp.mag", "pkp.cover", "pkp.arm_l", "pkp.bolt"))
    Z = (0, 0, 0)
    tilt = ((0.02, 0.0, 0.04), (-6, -24, 6))
    hold = (0, 0.239, 0.25)                    # left hand on the raised cover
    cov = [(0, Z, Z), (10, Z, Z), (18, Z, (75, 0, 0)), (106, Z, (75, 0, 0)), (116, Z, Z)]
    box_k = [(0, Z, Z), (20, Z, Z), (28, (0, 0, -0.05), Z), (40, (0.06, 0.02, -0.40), (0, 0, -15)), (66, (0.06, 0.02, -0.40), (0, 0, -15)),
             (80, (0, 0, -0.05), Z), (86, Z, Z)]
    arm = [(0, Z, Z), (8, (0, 0.375, 0.09), Z), (10, (0, 0.375, 0.09), Z), (18, hold, Z), (22, (0, 0.225, -0.16), Z),
           (28, (0, 0.225, -0.21), Z), (40, (0.06, 0.245, -0.56), Z), (66, (0.06, 0.245, -0.56), Z), (80, (0, 0.225, -0.21), Z),
           (86, (0, 0.225, -0.16), Z), (94, (-0.03, 0.27, 0.07), Z), (102, (-0.03, 0.27, 0.07), Z), (106, hold, Z),
           (116, (0, 0.375, 0.09), Z)]
    clip("pkp_reload", G, [(0, Z, Z), (10, *tilt), (128, *tilt), (140, Z, Z), (144, Z, Z)])
    clip("pkp_reload", C, cov + [(144, Z, Z)])
    clip("pkp_reload", M, box_k + [(144, Z, Z)])
    clip("pkp_reload", A, arm + [(132, Z, Z), (144, Z, Z)])
    clip("pkp_reload_empty", G, [(0, Z, Z), (10, *tilt), (138, *tilt), (150, Z, Z), (156, Z, Z)])
    clip("pkp_reload_empty", C, cov + [(156, Z, Z)])
    clip("pkp_reload_empty", M, box_k + [(156, Z, Z)])
    clip("pkp_reload_empty", A, arm + [(124, (-0.05, 0.21, 0.01), Z), (128, (-0.05, 0.21, 0.01), Z), (134, (-0.05, 0.32, 0.01), Z),
                                       (136, (-0.05, 0.32, 0.01), Z), (146, Z, Z), (156, Z, Z)])
    clip("pkp_reload_empty", B, [(0, Z, Z), (128, Z, Z), (134, (0, 0.11, 0), Z), (136, Z, Z), (156, Z, Z)])
    return sc


# ====================================================================================== shotguns (r24)
def shell(g, uc, cv, x=0.0, length=70.0):
    """12/70 shell lying along the bore, centred at u = uc: brass head (rim r 11.2, 16 mm) + red ribbed hull, crimp"""
    u0 = uc - length / 2
    lathe(g, [(u0, 0), (u0, 11.2), (u0 + 1.6, 11.2), (u0 + 1.6, 10.4), (u0 + 16, 10.4)], "Brass", 16, x, cv, smooth=True)
    lathe(g, [(u0 + 16, 10.25), (u0 + length - 3, 10.25), (u0 + length, 8.0), (u0 + length, 0)], "ShellRed", 16, x, cv)


# Saiga-12K isp.030 (Izhmash / Kalashnikov Concern): 12/76, barrel 430 mm, 910 mm (670 folded), 3.8 kg, 8-rd box,
# AK-74M pattern gas system, side-folding polymer stock, hinged receiver cover with a Picatinny rail, magwell funnel,
# front sight in the gas-chamber housing with a short rail below, removable slotted flash hider (M22x0.75).
def saiga_mag(g):
    # 8-rd staggered box: 92 mm front-to-back (70 mm shells), 42 mm thick, slight forward sweep, ribbed sides
    prism(g, [(428, -30), (520, -30), (520, -62), (530, -206), (442, -212), (428, -62)], -21, 21, "Polymer", 2.0)
    for s in (1, -1):
        for k in range(4):
            v0 = -78 - k * 32
            prism(g, [(436 + k * 2.5, v0), (514 + k * 2.6, v0), (515 + k * 2.6, v0 - 6), (437 + k * 2.5, v0 - 6)],
                  21 * s, 22.2 * s, "Polymer", 0.4)
    prism(g, [(436, -206), (536, -200), (538, -214), (438, -222)], -23, 23, "Polymer", 1.5)          # floorplate
    box(g, 518, 530, -44, -32, -7, 7, "Steel", 0.4)                                              # front lug
    box(g, 422, 428, -54, -46, -7, 7, "Steel", 0.4)                                              # rear catch lug
    shell(g, 474, -36, 5.5)


def saiga_body(g):
    # --- receiver (stamped, taller than the AK), hinged top cover with Picatinny rail, cover latch
    prism(g, [(262, -42), (262, 14), (270, 20), (560, 20), (560, -40), (532, -44), (528, -48), (424, -48), (420, -44),
              (300, -44)], -19, 19, "Parker", 1.2)
    xsec(g, [(-20, 16), (20, 16), (20, 24), (16, 30), (-16, 30), (-20, 24)], 268, 556, "Parker", 0.8)
    picatinny(g, 290, 540, 30, "Parker")
    box(g, 262, 270, 18, 26, -5, 5, "Steel")
    for s in (1, -1):                                                                            # cover hinge
        cylx(g, 552, 22, 20 * s, 22 * s, 5, "Steel", 12)
    # magwell funnel
    prism(g, [(414, -44), (534, -44), (540, -68), (408, -68)], -25, 25, "Polymer", 2.5)
    box(g, 404, 416, -66, -54, -8, 8, "Steel", 0.6)                                              # mag catch paddle
    # ejection port (12 ga: long) and the AK safety lever (right side)
    box(g, 418, 528, -2, 18, -19.4, -19.0, "Rubber", 0.1)
    prism(g, [(300, 6), (408, 6), (416, 1), (408, -6), (318, -4), (300, 1)], -21.6, -19.0, "Steel", 0.4)
    box(g, 408, 418, -12, 1, -27, -21.6, "Steel", 0.6)
    cylx(g, 306, 2, -23, -21.6, 4.2, "Steel")
    for (u, v) in ((285, -30), (332, -30), (386, -34), (440, -46), (500, -46), (545, -12), (545, -30)):
        cylx(g, u, v, 19, 20.2, 2.6, "Steel", 10)
        cylx(g, u, v, -20.2, -19, 2.6, "Steel", 10)
    # trigger guard (bolt-hold-open lever ahead of it), pistol grip AK-74M pattern, cap
    prism(g, [(336, -44), (342, -44), (345, -60), (391, -62), (393, -44), (399, -44), (398, -67), (341, -66)], -5, 5, "Steel", 0.6)
    prism(g, [(300, -44), (344, -44), (342, -54), (334, -66), (330, -80), (331, -92), (325, -104), (318, -122), (308, -140),
              (270, -142), (268, -134), (280, -94), (288, -64), (294, -52)], -15, 15, "Polymer", 3.0)
    for k in range(5):
        for s in (1, -1):
            box(g, 284 + k * 4, 288 + k * 4, -126 + k * 14, -114 + k * 14, 15 * s, 15.7 * s, "Polymer", 0.3)
    # --- AK-74M side-folding polymer stock (folds left), hinge block, lightening grooves, rubber butt pad, sling loop
    box(g, 240, 262, -38, 22, -18, 18, "Polymer", 3)
    cylz(g, 250, 20, -36, 22, 4, "Steel")
    box(g, 244, 256, -8, 6, -22, -18, "Steel", 0.6)                                              # fold latch button
    prism(g, [(10, -116), (40, -116), (240, -38), (240, 14), (34, 4), (10, 4)], -17, 17, "Polymer", 2.5)
    for s in (1, -1):
        prism(g, [(74, -78), (200, -40), (200, 4), (74, -4)], 17 * s, 17.8 * s, "Polymer", 0.6)
        prism(g, [(86, -64), (190, -36), (190, -4), (86, -10)], 17.8 * s, 18.2 * s, "Rubber", 0.2)
    prism(g, [(-6, -118), (10, -118), (10, 6), (-6, 6)], -20, 20, "Rubber", 3)
    ring_rod_loop(g, P(30, -106, 0), 7, 1.6, "Steel")
    # --- AK rear sight (leaf + slider with the notch) on the trunnion block
    box(g, 556, 602, 14, 30, -14, 14, "Parker", 1)
    prism(g, [(560, 30), (600, 30), (600, 35), (560, 39)], -10, 10, "Parker", 0.6)
    box(g, 588, 598, 35, 47, -9, 9, "Parker", 0.4)
    box(g, 587.6, 598.4, 42, 47.4, -1.8, 1.8, "Rubber", 0.1)                                    # notch
    box(g, 580, 588, 30, 36, -11, 11, "Steel", 0.4)                                              # range slider
    # --- upper handguard on the gas tube, lower handguard (polymer, ribbed), cap
    xsec(g, [(-17, 12), (17, 12), (19, 20), (15, 30), (-15, 30), (-19, 20)], 602, 762, "Polymer", 1.5)
    xsec(g, [(-22, -32), (22, -32), (26, -24), (26, 8), (20, 12), (-20, 12), (-26, 8), (-26, -24)], 604, 756, "Polymer", 2)
    for i in range(5):
        for s in (1, -1):
            box(g, 616 + i * 27, 628 + i * 27, -24, 4, 25.6 * s, 26.6 * s, "Polymer", 0.3)
    box(g, 756, 764, -34, 14, -27, 27, "Steel", 1)
    # --- barrel (12 ga, OD 25 mm) and the gas-chamber housing with the adjustable front post, rail below
    lathe(g, [(560, 12.8), (868, 12.3)], "Parker", 20)
    box(g, 764, 802, -18, 30, -15, 15, "Parker", 2)
    lathe(g, [(764, 0), (764, 8), (770, 8), (770, 0)], "Steel", 12, 0, 22)
    prism(g, [(774, 30), (794, 30), (792, 40), (776, 40)], -6, 6, "Parker", 0.8)
    for s in (1, -1):
        prism(g, [(774, 30), (794, 30), (793, 54), (777, 54)], 8 * s, 11 * s, "Parker", 0.6)
    cylz(g, 785, 0, 38, 44, 1.6, "Steel", 8)                                                     # post tip v 44
    picatinny(g, 766, 800, -18, "Parker", down=True)
    # --- removable slotted flash hider, bore
    lathe(g, [(848, 0), (848, 16), (862, 16), (866, 15), (910, 15), (910, 0)], "Parker", 24)
    for k in range(4):
        a = D(45 + 90 * k)
        obox(g, P(890, math.sin(a) * 15, math.cos(a) * 15), [Vector((0, 1, 0)), Vector((-math.sin(a), 0, math.cos(a))),
             Vector((math.cos(a), 0, math.sin(a)))], (34, 4.5, 1.6), "Rubber", 0.2)
    lathe(g, [(909.8, 9.4), (910.6, 9.4)], "Rubber", 16)


def saiga12():
    global U0
    sc = new_scene("SAIGA12")
    U0 = 372.0
    root = empty("saiga12", (0, 0, 0))
    gun = empty("saiga12.gun", (0, 0, 0), root)
    saiga_body("saiga12.body")
    saiga_mag("saiga12.mag")
    prism("saiga12.trigger", [(366, -44), (372, -44), (374, -52), (373, -60), (369, -61), (370, -54)], -3, 3, "Steel", 0.5)
    box("saiga12.bolt", 418, 528, 2, 16, -19.3, -16.5, "Steel", 0.4)                            # carrier through the port
    box("saiga12.bolt", 520, 536, 3, 15, -27, -19.3, "Steel", 0.6)
    cylx("saiga12.bolt", 530, 9, -40, -27, 8, "Steel", 14)                                     # charging handle knob
    flash("saiga12.flash", 911, 0, 120, 52)
    hand("saiga12.arm_r", P(305, -92, 0))
    hand("saiga12.arm_l", P(680, -12, 0))
    join_node("saiga12.body", gun)
    join_node("saiga12.mag", gun, P(474, -45, 0))
    join_node("saiga12.trigger", gun, P(369, -44, 0))
    join_node("saiga12.bolt", gun, P(470, 8, 0))
    join_node("saiga12.flash", gun, P(911, 0, 0))
    join_node("saiga12.arm_r", gun)
    join_node("saiga12.arm_l", gun)
    empty("saiga12.sight", P(200, 44, 0), gun)          # eye on the line rear notch (v 42..47) -> front post tip (v 44)
    empty("saiga12.muzzle", P(911, 0, 0), gun)
    G, M, A, B = (bpy.data.objects[n] for n in ("saiga12.gun", "saiga12.mag", "saiga12.arm_l", "saiga12.bolt"))
    Z = (0, 0, 0)
    tilt = ((0.02, 0.0, 0.03), (-8, -26, 6))
    mag_keys = [(0, Z, Z), (12, Z, Z), (16, Z, (-12, 0, 0)), (20, (0, -0.01, -0.09), (-18, 0, 0)),
                (26, (0, 0.05, -0.40), (-30, 0, 0)), (34, (0, 0.05, -0.40), (-30, 0, 0)), (40, (0, -0.012, -0.07), (-16, 0, 0)),
                (44, Z, (-10, 0, 0)), (47, Z, Z)]
    to_mag = (0, (680 - 474) / 1000.0, -0.10)
    arm_keys = [(0, Z, Z), (8, to_mag, Z), (12, to_mag, Z), (20, (0, 0.21, -0.19), Z), (26, (0.03, 0.24, -0.48), Z),
                (34, (0.03, 0.24, -0.48), Z), (40, (0, 0.21, -0.17), Z), (44, (0, 0.21, -0.11), Z), (48, to_mag, Z)]
    clip("saiga12_reload", G, [(0, Z, Z), (8, *tilt), (44, *tilt), (54, Z, Z), (60, Z, Z)])
    clip("saiga12_reload", M, mag_keys + [(60, Z, Z)])
    clip("saiga12_reload", A, arm_keys + [(56, Z, Z), (60, Z, Z)])
    rack = ((0.0, 0.01, 0.02), (-6, 22, -4))
    clip("saiga12_reload_empty", G, [(0, Z, Z), (8, *tilt), (44, *tilt), (52, *rack), (64, *rack), (72, Z, Z), (76, Z, Z)])
    clip("saiga12_reload_empty", M, mag_keys + [(76, Z, Z)])
    hk = (680 - 530) / 1000.0
    clip("saiga12_reload_empty", A, arm_keys + [(53, (-0.05, hk, 0.02), (0, 30, 0)), (56, (-0.05, hk, 0.015), Z),
                                                (60, (-0.05, hk + 0.11, 0.015), Z), (63, (-0.05, hk + 0.11, 0.02), Z),
                                                (71, Z, Z), (76, Z, Z)])
    clip("saiga12_reload_empty", B, [(0, Z, Z), (56, Z, Z), (60, (0, 0.11, 0), Z), (61, (0, 0.11, 0), Z), (63, Z, Z), (76, Z, Z)])
    return sc


# Remington 870 Police Magnum: 12 ga 2 3/4 & 3", 18" (457 mm) cylinder-bore barrel with rifle sights, 38.5" (978 mm),
# 4+1 tube magazine, steel receiver, twin action bars, short (police) ribbed walnut forend, walnut stock (LOP 14"),
# cross-bolt safety behind the trigger, action release ahead of the guard, bottom loading port, right ejection port.
def r870_body(g):
    # --- walnut stock (drop at comb 1.5", heel 2.5"), black recoil pad, sling stud
    prism(g, [(22, -150), (22, -34), (200, -4), (300, 12), (320, 18), (320, -46), (298, -60), (264, -68)], -18, 18, "Wood", 5)
    prism(g, [(0, -152), (22, -150), (22, -34), (0, -32)], -20, 20, "Rubber", 4)
    for k in range(6):
        box(g, 2 + k * 3, 3.2 + k * 3, -146, -38, -20.4, 20.4, "Rubber", 0.1)
    cylz(g, 70, 0, -132, -124, 4, "Steel", 10)
    # --- milled steel receiver: tang, flat top, bottom loading port, ejection port, pins
    prism(g, [(318, -46), (318, 14), (330, 21), (528, 21), (528, -46)], -17.5, 17.5, "Parker", 1.5)
    box(g, 300, 330, 12, 20, -7, 7, "Parker", 1.5)                                             # upper tang
    box(g, 396, 494, -46.6, -45.8, -12, 12, "Rubber", 0.1)                                      # loading port
    box(g, 404, 484, -6, 15, -17.9, -17.5, "Rubber", 0.1)                                      # ejection port
    for (u, v) in ((360, -36), (470, -36)):
        cylx(g, u, v, -18.4, 18.4, 3.0, "Steel", 12)                                            # trigger plate pins
    # --- trigger plate / guard, cross-bolt safety, action release lever
    box(g, 330, 430, -52, -46, -11, 11, "Anod", 1)
    prism(g, [(338, -52), (344, -52), (350, -74), (408, -76), (414, -52), (420, -52), (416, -82), (344, -80)], -6, 6, "Anod", 1)
    cylx(g, 336, -58, -14, 14, 4.2, "Steel", 12)
    box(g, 416, 428, -60, -52, 11, 14, "Steel", 0.5)
    # --- 18" barrel with rifle sights, magazine tube, barrel ring + cap, sling swivel
    lathe(g, [(520, 10.4), (978, 10.4)], "Parker", 20)
    lathe(g, [(518, 0), (518, 13), (532, 13), (532, 0)], "Parker", 20)                          # barrel extension collar
    box(g, 590, 626, 9, 15, -6, 6, "Parker", 0.6)                                               # rear sight base
    prism(g, [(600, 15), (620, 15), (620, 25), (604, 25)], -7, 7, "Parker", 0.4)
    box(g, 603.6, 620.4, 20.5, 25.4, -1.6, 1.6, "Rubber", 0.1)                                 # rear notch
    prism(g, [(936, 9), (972, 9), (972, 24), (964, 24)], -2.6, 2.6, "Parker", 0.4)              # front ramp blade, tip v 24
    box(g, 936, 974, 8, 11, -5, 5, "Parker", 0.4)
    lathe(g, [(977.8, 9.2), (978.6, 9.2)], "Rubber", 16)
    lathe(g, [(528, 11.5), (882, 11.5)], "Parker", 18, 0, -23)                                  # magazine tube
    lathe(g, [(866, 13.6), (882, 13.6)], "Parker", 18, 0, -23)                                  # barrel ring lug
    box(g, 866, 882, -14, -8, -6, 6, "Parker", 0.4)
    lathe(g, [(882, 0), (882, 12.6), (914, 12.6), (918, 8), (918, 0)], "Parker", 20, 0, -23)   # magazine cap
    for k in range(5):
        lathe(g, [(886 + k * 5, 13.1), (888 + k * 5, 13.1)], "Steel", 20, 0, -23)
    ring_rod_loop(g, P(904, -40, 0), 7, 1.6, "Steel")


def r870():
    global U0
    sc = new_scene("R870")
    U0 = 360.0
    root = empty("r870", (0, 0, 0))
    gun = empty("r870.gun", (0, 0, 0), root)
    r870_body("r870.body")
    prism("r870.trigger", [(356, -52), (362, -52), (364, -60), (363, -68), (359, -69), (360, -60)], -3, 3, "Steel", 0.5)
    # pump: short ribbed walnut forend on the tube + twin action bars into the receiver
    xsec("r870.pump", [(-22, -50), (22, -50), (27, -40), (27, -18), (20, -12), (-20, -12), (-27, -18), (-27, -40)], 600, 770, "Wood", 3)
    for i in range(9):
        for s in (1, -1):
            box("r870.pump", 612 + i * 17, 619 + i * 17, -44, -18, 26.6 * s, 27.6 * s, "Rubber", 0.3)
    xsec("r870.pump", [(-14, -36), (14, -36), (15, -14), (-15, -14)], 770, 778, "Parker", 0.8)  # front collar
    for s in (1, -1):
        box("r870.pump", 520, 602, -30, -25, 12 * s, 14 * s, "Steel", 0.3)                      # action bars
    # bolt seen through the ejection port, a shell for the loading animations (rests hidden inside the receiver)
    box("r870.bolt", 410, 478, -2, 12, -17.6, -15.5, "Steel", 0.3)
    shell("r870.shell", 455, -30)
    flash("r870.flash", 979, 0, 110, 50)
    hand("r870.arm_r", P(300, -40, 0))
    hand("r870.arm_l", P(686, -40, 0))
    join_node("r870.body", gun)
    join_node("r870.trigger", gun, P(359, -52, 0))
    pump = join_node("r870.pump", gun, P(686, -23, 0))
    bolt = join_node("r870.bolt", gun, P(444, 5, 0))
    sh = join_node("r870.shell", gun, P(455, -30, 0))
    join_node("r870.flash", gun, P(979, 0, 0))
    join_node("r870.arm_r", gun)
    join_node("r870.arm_l", pump)                       # the left hand rides on the pump
    empty("r870.sight", P(220, 23, 0), gun)             # eye on the rear notch (v 20.5..25) -> front blade tip (v 24)
    empty("r870.muzzle", P(979, 0, 0), gun)
    G, A = bpy.data.objects["r870.gun"], bpy.data.objects["r870.arm_l"]
    Z = (0, 0, 0)
    back = (0, 0.089, 0)                                # 3.5" pump stroke
    # pump cycle after each shot (0.5 s)
    clip("r870_pump", G, [(0, Z, Z), (3, (0, 0.004, 0.006), (3, 0, -3)), (9, (0, 0.002, 0.003), (1, 0, -1)), (12, Z, Z)])
    clip("r870_pump", pump, [(0, Z, Z), (2, Z, Z), (5, back, Z), (6, back, Z), (9, Z, Z), (12, Z, Z)])
    clip("r870_pump", bolt, [(0, Z, Z), (2, Z, Z), (5, back, Z), (6, back, Z), (9, Z, Z), (12, Z, Z)])
    # one shell through the bottom loading port (0.65 s, repeated per shell)
    tilt = ((0.01, 0.0, 0.02), (6, 0, -22))
    clip("r870_reload", G, [(0, Z, Z), (3, *tilt), (12, *tilt), (15, Z, Z)])
    clip("r870_reload", A, [(0, Z, Z), (4, (0, 0.23, -0.085), Z), (7, (0, 0.23, -0.03), Z), (9, (0, 0.20, -0.025), Z),
                            (13, Z, Z), (15, Z, Z)])
    clip("r870_reload", sh, [(0, Z, Z), (1, (0, 0.0, -0.09), Z), (4, (0, 0.0, -0.085), Z), (7, (0, 0.0, -0.028), Z),
                             (9, (0, -0.03, -0.004), Z), (10, Z, Z), (15, Z, Z)])
    # empty: pump back, a shell into the ejection port, pump forward (1.2 s)
    tilt2 = ((0.01, 0.0, 0.02), (4, 0, 18))
    clip("r870_reload_empty", G, [(0, Z, Z), (4, *tilt2), (22, *tilt2), (27, Z, Z), (28, Z, Z)])
    clip("r870_reload_empty", pump, [(0, Z, Z), (3, back, Z), (21, back, Z), (25, Z, Z), (28, Z, Z)])
    clip("r870_reload_empty", bolt, [(0, Z, Z), (3, back, Z), (21, back, Z), (25, Z, Z), (28, Z, Z)])
    hy = (686 - 445) / 1000.0 - 0.089
    clip("r870_reload_empty", A, [(0, Z, Z), (4, Z, Z), (9, (-0.035, hy, -0.07), Z), (13, (-0.035, hy, 0.040), Z),
                                  (16, (-0.03, hy, 0.03), Z), (21, Z, Z), (28, Z, Z)])
    clip("r870_reload_empty", sh, [(0, Z, Z), (6, Z, Z), (7, (-0.03, 0.01, -0.08), Z), (9, (-0.03, 0.01, -0.05), Z),
                                   (13, (-0.03, 0.01, 0.036), Z), (15, (0, 0.0, 0.03), Z), (21, (0, 0.0, 0.03), Z),
                                   (25, (0, -0.10, 0.03), Z), (26, Z, Z), (28, Z, Z)])
    return sc


def shell_item(g):
    shell(g, 400, 0)


# ====================================================================================== MANPADS (r25)
# 9K338 Igla-S: 9P338 launch tube (glass fibre, 1700 mm) + 9P522 launching mechanism + 9B238 power/coolant unit (NIP),
#   9M342 missile 1635 mm x 72 mm with the aerospike needle; sight on the left (front frame + post, rear peep with the
#   target-lock light). In the combat position the front cover is off: the seeker dome and needle sit in the muzzle.
# FIM-92 Stinger: launch tube 1520 mm (~90 mm OD) with frangible front / aft covers, M171 gripstock, BCU screwed into
#   the front of the gripstock, folding IFF antenna grid (top left), sight assembly on the left (range-ring front frame,
#   rubber eyecup, acquisition indicator at the cheek), shoulder support; missile 1520 mm at launch / 1370 mm in flight.
mat("IglaTube", "T_IglaTube", 0.7)
mat("StingerTube", "T_StingerTube", 0.7)
mat("GripGreen", "T_GripGreen", 0.6, 0.2)
mat("BlackPaint", "T_BlackPaint", 0.6, 0.1)
mat("YellowBand", "T_YellowBand", 0.6)
mat("BrownBand", "T_BrownBand", 0.6)
mat("MissileWhite", "T_MissileWhite", 0.45, 0.1)
mat("MissileGrey", "T_MissileGrey", 0.5, 0.3)
mat("StencilIgla", "T_StencilIgla", 0.8, clip=True)
mat("StencilStinger", "T_StencilStinger", 0.8, clip=True)
mat("StencilDanger", "T_StencilDanger", 0.8, clip=True)
mat("StencilBlast", "T_StencilBlast", 0.8, clip=True)


def decal(grp, u0, u1, v0, v1, x, m, left=True):
    """flat stencil quad in the plane x = const (mm), text reading front->rear on the left side, rear->front on the right"""
    bm = bmesh.new()
    vs = [bm.verts.new(P(u0, v0, x)), bm.verts.new(P(u1, v0, x)), bm.verts.new(P(u1, v1, x)), bm.verts.new(P(u0, v1, x))]
    bm.faces.new(vs)
    ob = _mk(grp + "_dc", bm, m, grp)
    me = ob.data
    uvl = me.uv_layers[0]
    for li in me.polygons[0].loop_indices:
        co = me.vertices[me.loops[li].vertex_index].co
        u = U0 - co.y * 1000.0
        v = co.z * 1000.0
        fu = (u1 - u) / (u1 - u0) if left else (u - u0) / (u1 - u0)
        uvl.data[li].uv = (fu, (v - v0) / (v1 - v0))
    return ob


def ring(grp, u0, u1, r_in, r_out, m, seg=32, cx=0.0, cv=0.0):
    """hollow band (no end discs)"""
    return lathe(grp, [(u0, r_in), (u0, r_out), (u1, r_out), (u1, r_in)], m, seg, cx, cv, closed=True)


def fin(grp, u_root0, u_root1, u_tip0, u_tip1, r0, r1, ang, m, t=2.0):
    """thin trapezoid fin / canard in the plane through the bore at roll angle ang (deg, 0 = left, 90 = up)"""
    a = D(ang)
    rad = Vector((math.cos(a), 0, math.sin(a)))
    nrm = Vector((-math.sin(a), 0, math.cos(a)))
    bm = bmesh.new()
    pts = [(u_root0, r0), (u_root1, r0), (u_tip1, r1), (u_tip0, r1)]
    A = [bm.verts.new(P(u, 0, 0) + rad * (r / 1000.0) + nrm * (t / 2000.0)) for (u, r) in pts]
    B = [bm.verts.new(P(u, 0, 0) + rad * (r / 1000.0) - nrm * (t / 2000.0)) for (u, r) in pts]
    bm.faces.new(A)
    bm.faces.new(list(reversed(B)))
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((A[i], A[j], B[j], B[i]))
    return _mk(grp + "_fin", bm, m, grp, False, 0.3)


def frame_xv(grp, u0, u1, x0, x1, v0, v1, t, m):
    """rectangular sight frame in the cross-section plane"""
    xsec(grp, [(x0, v0), (x1, v0), (x1, v0 + t), (x0, v0 + t)], u0, u1, m, 0.3)
    xsec(grp, [(x0, v1 - t), (x1, v1 - t), (x1, v1), (x0, v1)], u0, u1, m, 0.3)
    xsec(grp, [(x0, v0 + t), (x0 + t, v0 + t), (x0 + t, v1 - t), (x0, v1 - t)], u0, u1, m, 0.3)
    xsec(grp, [(x1 - t, v0 + t), (x1, v0 + t), (x1, v1 - t), (x1 - t, v1 - t)], u0, u1, m, 0.3)


# ------------------------------------------------------------------ 9K338 Igla-S
IG_SX, IG_SV = 78.0, 30.0          # sight line (left of / above the bore)


def igla_tube(g):
    T = "IglaTube"
    # rear bell (hollow, reinforced lip) - the rear cover is blown out at launch
    lathe(g, [(0, 52), (8, 52), (30, 49), (90, 44), (140, 41), (140, 36), (90, 39), (30, 44), (0, 47)], T, 32, closed=True)
    ring(g, 2, 14, 51.5, 53.2, "BlackPaint")
    lathe(g, [(134, 36.5), (137, 36.5)], "BlackPaint", 24)                         # dark bore seen from behind
    # glass-fibre tube body and the wider front section (seeker compartment), front lip
    lathe(g, [(140, 40), (1460, 40)], T, 32)
    lathe(g, [(1460, 40), (1476, 45), (1560, 45)], T, 32)
    ring(g, 1560, 1688, 41, 45, T)                                                  # open muzzle (front cover off)
    lathe(g, [(1562, 41.2), (1565, 41.2)], "BlackPaint", 24)
    ring(g, 1688, 1700, 41, 47, T)
    ring(g, 1694, 1700, 47, 47.8, "BlackPaint")
    # clamp bands with sling swivels, front-cover hinge lug
    for u in (300, 1262):
        ring(g, u, u + 24, 39.5, 42.6, "BlackPaint")
        box(g, u + 6, u + 18, -47, -41, -3.5, 3.5, "BlackPaint", 0.5)
        ring_rod_loop(g, P(u + 12, -54, 0), 8, 1.8, "Steel")
    box(g, 1670, 1694, 44, 52, -8, 8, "BlackPaint", 0.8)
    cylx(g, 1682, 52, -10, 10, 3.2, "Steel")
    # connector strip under the tube for the launching mechanism (electrical + mechanical lock)
    box(g, 780, 1064, -46, -38, -22, 22, "BlackPaint", 0.8)
    for u in (800, 1040):
        cylx(g, u, -44, -24, 24, 3.5, "Steel")
    # front sight (u 1330): band, folding arm, frame 52 x 52 with the post and two lead marks
    ring(g, 1318, 1342, 39.5, 42.8, "GripGreen")
    box(g, 1322, 1338, 8, 22, 36, 56, "GripGreen", 1)
    cylz(g, 1330, 50, 4, 26, 4.5, "Steel")                                          # fold hinge pin
    frame_xv(g, 1328, 1332, 52, 104, 4, 56, 4, "BlackPaint")
    xsec(g, [(IG_SX - 1.2, 8), (IG_SX + 1.2, 8), (IG_SX + 1.2, IG_SV), (IG_SX - 1.2, IG_SV)], 1328.5, 1331.5, "Steel", 0.2)
    for dx in (-16, 16):
        xsec(g, [(IG_SX + dx - 3, IG_SV - 0.8), (IG_SX + dx + 3, IG_SV - 0.8), (IG_SX + dx + 3, IG_SV + 0.8),
                 (IG_SX + dx - 3, IG_SV + 0.8)], 1328.5, 1331.5, "Steel", 0.1)
    # rear sight (u 750): band, arm, peep ring on the sight line, lock-light housing with its lens towards the eye
    ring(g, 738, 762, 39.5, 42.8, "GripGreen")
    box(g, 742, 758, 8, 20, 36, 62, "GripGreen", 1)
    box(g, 744, 756, 14, 24, 60, 96, "GripGreen", 1)
    ring(g, 744, 756, 7.5, 11, "BlackPaint", 24, IG_SX, IG_SV)                       # ghost-ring peep
    box(g, 728, 790, 18, 56, 92, 116, "GripGreen", 2.5)
    lathe(g, [(724, 4.5), (728, 4.5)], "LensSolid", 16, 104, 44)
    ring(g, 724, 729, 4.5, 6.5, "BlackPaint", 16, 104, 44)
    cylx(g, 770, 30, 116, 120, 6, "Rubber")                                         # lamp test button
    # stencils: left side (type / lot), right side (backblast warning)
    decal(g, 380, 700, -15, 15, 43.4, "StencilIgla", True)
    decal(g, 170, 460, -7, 7, -43.4, "StencilDanger", False)


def igla_grip(g):
    G_ = "GripGreen"
    # 9P522 launching mechanism housing (cast, rounded) under the tube
    prism(g, [(790, -46), (1064, -46), (1072, -60), (1064, -96), (1012, -104), (902, -104), (860, -98), (800, -80)], -26, 26, G_, 3)
    box(g, 930, 1000, -104, -100, -18, 18, "BlackPaint", 0.6)                     # name plate
    # pistol grip (ribbed plastic), trigger guard
    prism(g, [(838, -98), (884, -98), (880, -120), (872, -180), (868, -214), (862, -222), (830, -222), (826, -212), (832, -170),
              (836, -120)], -15, 15, "Stipple", 3)
    for i in range(6):
        box(g, 828 + i * 0.6, 836 + i * 0.6, -128 - i * 14, -122 - i * 14, -15.5, 15.5, "Polymer", 0.4)
    prism(g, [(884, -104), (944, -104), (944, -112), (934, -140), (884, -142), (884, -136), (926, -134), (936, -112),
              (884, -110)], -4, 4, G_, 0.6)
    # launch key lever + pivot (left), switch, IFF interrogator ("Parol") connector with its rubber cap
    box(g, 918, 984, -72, -64, 26, 30, "Steel", 0.6)
    cylx(g, 980, -68, 25, 32, 4.5, "Steel")
    cylx(g, 1030, -64, 26, 34, 5, "BlackPaint")
    cylx(g, 1000, -84, 26, 40, 9, "BlackPaint")
    cylx(g, 1000, -84, 40, 44, 10, "Rubber")
    # 9B238 NIP (battery + coolant gas bottle) plugged in at the front, pointing forward-down
    a, b = P(1060, -82, 0), P(1252, -112, 0)
    rod(g, P(1048, -80, 0), P(1074, -84, 0), 31, 31, "BlackPaint", 20)
    rod(g, a, b, 27, 27, G_, 20)
    for f in (0.30, 0.55, 0.80):
        c = a.lerp(b, f)
        rod(g, c, c + (b - a).normalized() * 0.006, 28, 28, "BlackPaint", 20)
    rod(g, b, b + (b - a).normalized() * 0.012, 28.5, 25, "Rubber", 20)
    rod(g, a.lerp(b, 0.45) + Vector((0.028, 0, 0)), a.lerp(b, 0.45) + Vector((0.036, 0, 0)), 5, 5, "Steel", 10)


def igla_nose(g, u):
    """9M342 seeker dome + aerospike (sits in the open muzzle in the combat position)"""
    lathe(g, [(u - 60, 35.8), (u - 20, 35.8), (u, 30), (u + 12, 22)], "Warhead", 28)
    lathe(g, [(u + 12, 22), (u + 22, 15), (u + 28, 7), (u + 30, 0)], "LensSolid", 20)
    rod(g, P(u + 28, 0, 0), P(u + 80, 0, 0), 2.6, 1.6, "Steel", 10)
    lathe(g, [(u + 80, 2.2), (u + 88, 3.6), (u + 94, 0)], "Steel", 12)


def igla_round(g):
    igla_nose(g, 1610)
    # remains of the rear cover seat (blown out on launch): the cover itself
    lathe(g, [(-6, 44), (-2, 51), (8, 51)], "Rubber", 32)
    box(g, -8, 10, 46, 60, -6, 6, "Rubber", 0.5)                                       # pull tab


def igla_missile(g):
    """9M342 in flight: u 0 = nozzle exit, 1635 to the dome tip, needle in front"""
    lathe(g, [(0, 26), (6, 30), (22, 35.8)], "Steel", 24)
    lathe(g, [(0, 0), (0, 22)], "BlackPaint", 20)
    lathe(g, [(22, 35.8), (1100, 35.8)], "Warhead", 28)
    lathe(g, [(1100, 35.8), (1440, 35.8)], "MissileGrey", 28)                         # warhead + fuze section
    ring(g, 1180, 1200, 35, 36.4, "BlackPaint", 28)
    ring(g, 600, 640, 35, 36.4, "BlackPaint", 28)
    lathe(g, [(1440, 35.8), (1560, 35.8), (1590, 30), (1602, 22)], "Warhead", 28)
    lathe(g, [(1602, 22), (1612, 15), (1618, 7), (1620, 0)], "LensSolid", 20)
    rod(g, P(1618, 0, 0), P(1670, 0, 0), 2.6, 1.6, "Steel", 10)
    lathe(g, [(1670, 2.2), (1678, 3.6), (1684, 0)], "Steel", 12)
    for k in range(4):                                                                 # tail fins (deployed, 160 span)
        fin(g, 30, 120, 70, 130, 35, 80, 45 + 90 * k, "Warhead", 2.0)
    for k in range(2):                                                                 # control canards
        fin(g, 1450, 1500, 1462, 1492, 35, 62, 90 * k, "MissileGrey", 2.2)
    for k in range(2):                                                                 # fixed destabilisers
        fin(g, 1530, 1552, 1536, 1550, 35, 52, 90 * k + 180, "MissileGrey", 1.6)


def igla():
    global U0
    sc = new_scene("IGLA")
    U0 = 900.0
    root = empty("igla", (0, 0, 0))
    gun = empty("igla.gun", (0, 0, 0), root)
    igla_tube("igla.tube")
    igla_grip("igla.grip")
    igla_round("igla.round")
    prism("igla.trigger", [(896, -104), (904, -104), (906, -116), (905, -126), (901, -127), (902, -116)], -3, 3, "Steel", 0.5)
    flash("igla.flash", 1701, 0, 260, 110)
    hand("igla.arm_r", P(852, -160, 0))
    hand("igla.arm_l", P(1150, -56, 0))
    tube = join_node("igla.tube", gun)
    join_node("igla.grip", gun)
    join_node("igla.round", tube)
    join_node("igla.trigger", gun, P(900, -104, 0))
    join_node("igla.flash", tube, P(1701, 0, 0))
    join_node("igla.arm_r", gun)
    al = join_node("igla.arm_l", gun)
    empty("igla.sight", P(735, IG_SV, IG_SX), gun)
    empty("igla.muzzle", P(1702, 0, 0), gun)
    manpads_reload("igla", bpy.data.objects["igla.gun"], tube, al)
    return sc


def manpads_reload(w, G, tube, al):
    """the launching mechanism is moved onto a fresh tube: spent tube off forward / dropped right, new tube in from the
    left, locked onto the connector (100 ticks = 5 s -> 120 frames)"""
    Z = (0, 0, 0)
    low = ((0.05, 0.10, -0.15), (-24, -10, 14))
    clip(w + "_reload", G, [(0, Z, Z), (14, *low), (100, *low), (116, Z, Z), (120, Z, Z)])
    k = [(0, Z, Z), (14, Z, Z), (22, (0, -0.05, 0), Z), (40, (-0.36, -0.30, -0.55), (30, 0, -40)),
         (41, (0.48, 0.25, -0.62), (-20, 0, 30)), (72, (0.04, -0.07, -0.05), (0, 0, 4)), (84, (0, -0.05, 0), Z),
         (92, Z, Z), (120, Z, Z)]
    clip(w + "_reload", tube, k)
    clip(w + "_reload", al, k)


# ------------------------------------------------------------------ FIM-92 Stinger
ST_SX, ST_SV = 88.0, 42.0


def stinger_tube(g):
    T = "StingerTube"
    ring(g, 0, 40, 42, 48.5, T)
    ring(g, 2, 10, 48.4, 49.5, "BlackPaint")
    lathe(g, [(34, 41.5), (37, 41.5)], "BlackPaint", 24)
    lathe(g, [(40, 45), (1480, 45)], T, 32)
    ring(g, 1480, 1520, 42, 49, T)
    ring(g, 1512, 1520, 48.9, 50, "BlackPaint")
    lathe(g, [(1483, 41.5), (1486, 41.5)], "BlackPaint", 24)
    for u in (250, 700, 1180, 1400):                                                   # moulded stiffening rings
        ring(g, u, u + 18, 44.5, 47.2, T)
    # shoulder support (rubber saddle) and sling swivels
    xsec(g, [(-36, -30), (36, -30), (44, -46), (36, -60), (-36, -60), (-44, -46)], 380, 620, "Rubber", 3)
    for u in (300, 1300):
        box(g, u - 6, u + 6, -52, -44, -3.5, 3.5, "BlackPaint", 0.5)
        ring_rod_loop(g, P(u, -58, 0), 8, 1.8, "Steel")
    # sight assembly (left): front frame with the range ring at u 1290, rear eyepiece at u 616-660
    box(g, 1280, 1300, -2, 14, 40, 66, "BlackPaint", 1)
    cylz(g, 1290, 60, -4, 18, 4.5, "Steel")
    frame_xv(g, 1288, 1292, 58, 118, 8, 76, 3.2, "BlackPaint")
    ring(g, 1288.5, 1291.5, 21, 23, "BlackPaint", 28, ST_SX, ST_SV)                   # range ring
    for d in ((0, 23, 0, 30), (0, -30, 0, -23), (23, 0, 30, 0), (-30, 0, -23, 0)):     # ring ticks
        x0, v0, x1, v1 = d
        xsec(g, [(ST_SX + min(x0, x1) - 0.7, ST_SV + min(v0, v1) - 0.7), (ST_SX + max(x0, x1) + 0.7, ST_SV + min(v0, v1) - 0.7),
                 (ST_SX + max(x0, x1) + 0.7, ST_SV + max(v0, v1) + 0.7), (ST_SX + min(x0, x1) - 0.7, ST_SV + max(v0, v1) + 0.7)],
             1289, 1291, "BlackPaint", 0.1)
    box(g, 630, 662, 2, 26, 60, 75, "BlackPaint", 1)                                 # eyepiece bracket
    ring(g, 616, 660, 8, 10.5, "BlackPaint", 24, ST_SX, ST_SV)
    ring(g, 594, 616, 9, 15.5, "Rubber", 24, ST_SX, ST_SV)
    # acquisition indicator (bone-conduction speaker at the cheek)
    box(g, 556, 606, 22, 56, 50, 70, "Polymer", 2)
    cylx(g, 581, 39, 70, 73, 11, "Rubber")
    # stencils
    decal(g, 150, 560, -14, 14, 48.0, "StencilStinger", True)
    decal(g, 60, 380, -8, 8, -48.0, "StencilBlast", False)


def stinger_grip(g):
    G_ = "GripGreen"
    # M171 gripstock housing
    prism(g, [(650, -44), (982, -44), (992, -58), (982, -92), (930, -100), (780, -100), (720, -96), (660, -70)], -30, 30, G_, 3)
    box(g, 650, 990, -48, -40, -26, 26, "BlackPaint", 0.8)                           # tube interface
    prism(g, [(690, -94), (736, -94), (732, -120), (724, -180), (720, -214), (714, -222), (684, -222), (680, -212), (686, -170),
              (690, -120)], -15, 15, "Stipple", 3)
    for i in range(6):
        box(g, 680 + i * 0.6, 688 + i * 0.6, -128 - i * 14, -122 - i * 14, -15.5, 15.5, "Polymer", 0.4)
    prism(g, [(736, -100), (800, -100), (800, -108), (790, -138), (736, -140), (736, -134), (784, -132), (792, -108),
              (736, -106)], -4, 4, G_, 0.6)
    # safety & actuator device (thumb bar, left) and the uncage switch
    box(g, 800, 872, -62, -52, 30, 38, "BlackPaint", 0.8)
    cylx(g, 800, -57, 29, 39, 5, "Steel")
    cylx(g, 846, -82, 30, 37, 6.5, "Rubber")
    # IFF interrogator cable connector (right)
    cylx(g, 900, -70, -44, -30, 8, "BlackPaint")
    cylx(g, 900, -70, -48, -44, 9, "Rubber")
    # BCU receptacle + battery coolant unit screwed in from the front (black, knurled cap)
    lathe(g, [(975, 31), (1000, 31)], "BlackPaint", 24, 0, -72)
    lathe(g, [(1000, 24.5), (1128, 24.5)], "BlackPaint", 24, 0, -72)
    for u in (1030, 1070, 1100):
        ring(g, u, u + 4, 24, 25.8, "Steel", 24, 0, -72)
    lathe(g, [(1128, 26.5), (1146, 26.5), (1150, 22)], "Rubber", 24, 0, -72)
    # IFF antenna: hinge block on the top left, folding arm, wire grid perpendicular to the bore (deployed)
    box(g, 948, 992, -12, 10, 40, 60, "BlackPaint", 1)
    cylz(g, 970, 58, -14, 12, 5, "Steel")
    rod(g, P(970, 6, 54), P(1002, 76, 74), 4.2, 3.6, "BlackPaint", 10)
    U, x0, x1, v0, v1 = 1004, 30.0, 200.0, 76.0, 186.0
    for (a, b) in (((v0, x0), (v0, x1)), ((v1, x0), (v1, x1)), ((v0, x0), (v1, x0)), ((v0, x1), (v1, x1))):
        rod(g, P(U, a[0], a[1]), P(U, b[0], b[1]), 2.6, 2.6, "BlackPaint", 8)
    for i in range(1, 6):
        x = x0 + (x1 - x0) * i / 6
        rod(g, P(U, v0, x), P(U, v1, x), 1.3, 1.3, "Steel", 6, caps=False)
    rod(g, P(U, (v0 + v1) / 2, x0), P(U, (v0 + v1) / 2, x1), 1.3, 1.3, "Steel", 6, caps=False)
    box(g, U - 8, U + 8, 70, 82, 54, 90, "BlackPaint", 1)                              # feed block


def stinger_round(g):
    # frangible front cover and the aft cover (both broken out at launch)
    lathe(g, [(1514, 50.2), (1522, 50.2), (1528, 44), (1531, 0)], "BlackPaint", 32)
    ring(g, 1522, 1524, 30, 44, "Rubber", 28)
    lathe(g, [(-8, 40), (-4, 49.6), (6, 49.6)], "BlackPaint", 32)


def stinger_missile(g):
    """FIM-92 in flight (ejection motor gone): u 0 = nozzle exit, 1370 dome tip"""
    lathe(g, [(0, 24), (8, 30), (24, 35)], "Steel", 24)
    lathe(g, [(0, 0), (0, 20)], "BlackPaint", 20)
    lathe(g, [(24, 35), (1180, 35)], "MissileWhite", 28)
    ring(g, 500, 530, 34.5, 35.8, "BrownBand", 28)                                    # rocket motor
    ring(g, 1080, 1110, 34.5, 35.8, "YellowBand", 28)                                 # HE warhead
    lathe(g, [(1180, 35), (1290, 35), (1330, 29)], "MissileWhite", 28)
    lathe(g, [(1330, 29), (1350, 22), (1364, 11), (1370, 0)], "LensSolid", 8, smooth=False)   # faceted IR/UV dome
    for k in range(4):                                                                 # tail fins (pop-out)
        fin(g, 26, 110, 64, 116, 35, 70, 45 + 90 * k, "MissileGrey", 1.8)
    for k in range(2):                                                                 # control canards
        fin(g, 1230, 1280, 1244, 1272, 35, 60, 90 * k, "MissileGrey", 2.0)
    for k in range(2):                                                                 # fixed canards
        fin(g, 1236, 1262, 1242, 1258, 35, 50, 90 * k + 180, "MissileGrey", 1.6)


def stinger():
    global U0
    sc = new_scene("STINGER")
    U0 = 752.0
    root = empty("stinger", (0, 0, 0))
    gun = empty("stinger.gun", (0, 0, 0), root)
    stinger_tube("stinger.tube")
    stinger_grip("stinger.grip")
    stinger_round("stinger.round")
    prism("stinger.trigger", [(748, -100), (756, -100), (758, -112), (757, -122), (753, -123), (754, -112)], -3, 3, "Steel", 0.5)
    flash("stinger.flash", 1525, 0, 260, 110)
    hand("stinger.arm_r", P(706, -160, 0))
    hand("stinger.arm_l", P(1062, -40, 0))
    tube = join_node("stinger.tube", gun)
    join_node("stinger.grip", gun)
    join_node("stinger.round", tube)
    join_node("stinger.trigger", gun, P(752, -100, 0))
    join_node("stinger.flash", tube, P(1525, 0, 0))
    join_node("stinger.arm_r", gun)
    al = join_node("stinger.arm_l", gun)
    empty("stinger.sight", P(588, ST_SV, ST_SX), gun)
    empty("stinger.muzzle", P(1530, 0, 0), gun)
    manpads_reload("stinger", bpy.data.objects["stinger.gun"], tube, al)
    return sc


# ====================================================================================== r26 WW2 small arms
# PPSh-41 (USSR 1941): 7.62x25 TT, 843 mm, barrel 269 mm, 71-rd drum, ~1000 rds/min, 488 m/s, birch stock, perforated
#   barrel jacket whose slanted front is the compensator, hooded front post, L-flip rear sight 100/200 m.
# MP 40 (Germany 1940): 9x19, 833 mm stock open, barrel 251 mm, 32-rd stick magazine, ~550 rds/min, 400 m/s, receiver tube,
#   bakelite grip frame + ribbed fore panel, under-folding steel stock, barrel rest bar, bolt handle on the LEFT.
# Mosin-Nagant 1891/30 (USSR): 7.62x54R, 1232 mm (no bayonet), barrel 730 mm, 5-rd box, stripper clips, 865 m/s,
#   straight bolt handle, hooded front post, tangent rear sight, two barrel bands, cleaning rod.
# Karabiner 98k (Germany): 7.92x57 Mauser, 1110 mm, barrel 600 mm, 5 rds flush magazine, stripper clips, 760 m/s,
#   turned-down bolt handle, wing safety, semi-pistol grip walnut stock, H nose cap with bayonet lug, hooded barleycorn.

def arc_xsec(r_out, r_in, a0, a1, n=12, cv=0.0):
    pts = [(r_out * math.cos(D(a0 + (a1 - a0) * i / n)), cv + r_out * math.sin(D(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]
    pts += [(r_in * math.cos(D(a1 - (a1 - a0) * i / n)), cv + r_in * math.sin(D(a1 - (a1 - a0) * i / n))) for i in range(n + 1)]
    return pts


def cartridge(g, u_rim, cv, case_len, rim_r, base_r, shoulder_r, neck_r, oal, bullet_r, x=0.0, seg=12):
    sh = case_len * 0.80
    lathe(g, [(u_rim, 0), (u_rim, rim_r), (u_rim + 1.6, rim_r), (u_rim + 1.6, base_r * 0.86), (u_rim + 3.2, base_r),
              (u_rim + sh, shoulder_r), (u_rim + sh + 4.5, neck_r), (u_rim + case_len, neck_r)], "Brass", seg, x, cv)
    lathe(g, [(u_rim + case_len, bullet_r), (u_rim + case_len + (oal - case_len) * 0.42, bullet_r),
              (u_rim + oal - 1.2, bullet_r * 0.30), (u_rim + oal, 0)], "Copper", seg, x, cv)


def band_xsec(top, bot, half):
    return [(-half + 1.5, bot), (half - 1.5, bot), (half + 1.5, bot + 7), (half + 1.5, top - 12), (half - 4, top - 3),
            (6, top), (-6, top), (-half + 4, top - 3), (-half - 1.5, top - 12), (-half - 1.5, bot + 7)]


def rifle_bolt_clip(name, bolt, AR, knob_rel, grip_rel, f0, total, lift=85, back=0.09, hold=4):
    """bolt: lift, pull back, (hold), push, lock; the right hand rides on the knob (T-5000 scheme, generalised)"""
    Z = (0, 0, 0)
    bk = [(0, Z, 0), (4, Z, lift), (9, (0, back, 0), lift), (9 + hold, (0, back, 0), lift), (13 + hold, Z, lift), (17 + hold, Z, 0)]

    def knob_at(t, a):
        return Matrix.Rotation(D(a), 3, 'Y') @ knob_rel + Vector(t)

    keys = [(0, Z, Z)] if f0 > 0 else []
    keys += [(f0 + f, t, (0, a, 0)) for (f, t, a) in bk]
    keys.append((max(total, f0 + 18 + hold), Z, Z))
    clip(name, bolt, keys)
    off = Vector((0, 0.01, -0.02))
    hk = [(0, Z, Z), (f0 + 2, tuple(knob_at(Z, 0) - grip_rel + off), Z)]
    for (f, t, a) in bk[1:-1]:
        hk.append((f0 + f, tuple(knob_at(t, a) - grip_rel + off), Z))
    hk.append((f0 + 19 + hold, tuple(knob_at(Z, 0) - grip_rel + off), Z))
    hk.append((f0 + 23 + hold, Z, Z))
    hk.append((max(total, f0 + 24 + hold), Z, Z))
    clip(name, AR, hk)


def clip_reload(w, G, bolt, AR, AL, CL, knob_rel, grip_rel, arm_to_clip, up, back, total=60):
    """stripper-clip reload (bolt rifles, only from an empty magazine): bolt open, clip into the charger guides,
       thumb strips the five rounds down, clip out, bolt closed"""
    Z = (0, 0, 0)
    tilt = ((0.015, 0.0, 0.03), (-6, -18, 6))
    clip(w + "_reload", G, [(0, Z, Z), (6, *tilt), (total - 10, *tilt), (total - 2, Z, Z), (total, Z, Z)])
    rifle_bolt_clip(w + "_reload", bolt, AR, knob_rel, grip_rel, 2, total, back=back, hold=total - 30)
    clip(w + "_reload", CL, [(0, Z, Z), (13, Z, Z), (14, (0, 0, up), Z), (22, (0, 0, up), Z), (34, (0, 0, up * 0.52), Z),
                             (36, (0, 0, up * 0.52), Z), (37, Z, Z), (total, Z, Z)])
    a = Vector(arm_to_clip)
    clip(w + "_reload", AL, [(0, Z, Z), (8, tuple(a * 0.6 + Vector((0, 0, -0.02))), Z), (13, tuple(a), Z), (22, tuple(a + Vector((0, 0, 0.01))), Z),
                             (34, tuple(a + Vector((0, 0, -up * 0.48 + 0.01))), Z), (38, tuple(a + Vector((0, -0.02, 0.02))), Z),
                             (46, Z, Z), (total, Z, Z)])


def smg_reload(w, G, M, A, B, frames, frames_e, to_mag, drop=(0, 0.04, -0.36), side=1, rack_arm=(-0.05, 0.15, 0.03)):
    """AK-style magazine change scaled to the reload time; on an empty gun the bolt is racked afterwards"""
    Z = (0, 0, 0)
    s = frames / 60.0
    f = lambda x: max(1, int(round(x * s)))
    tm = Vector(to_mag)
    dv = Vector(drop)
    tilt = ((0.02, 0.0, 0.03), (-8, -24 * side, 6 * side))
    mag_keys = [(0, Z, Z), (f(12), Z, Z), (f(16), (0, 0, -0.012), (-6, 0, 0)), (f(20), (0, -0.005, -0.07), (-10, 0, 0)),
                (f(26), tuple(dv), (-16, 0, 0)), (f(34), tuple(dv), (-16, 0, 0)), (f(40), (0, -0.006, -0.06), (-8, 0, 0)),
                (f(44), (0, 0, -0.01), (-3, 0, 0)), (f(47), Z, Z)]
    arm_keys = [(0, Z, Z), (f(8), tuple(tm), Z), (f(12), tuple(tm), Z), (f(20), tuple(tm + Vector((0, -0.005, -0.07))), Z),
                (f(26), tuple(tm + dv), Z), (f(34), tuple(tm + dv), Z), (f(40), tuple(tm + Vector((0, -0.006, -0.06))), Z),
                (f(44), tuple(tm), Z), (f(48), tuple(tm), Z)]
    clip(w + "_reload", G, [(0, Z, Z), (f(8), *tilt), (f(44), *tilt), (f(54), Z, Z), (frames, Z, Z)])
    clip(w + "_reload", M, mag_keys + [(frames, Z, Z)])
    clip(w + "_reload", A, arm_keys + [(f(56), Z, Z), (frames, Z, Z)])
    e = frames_e
    rack = ((0.0, 0.01, 0.02), (-6, 22 * side, -4 * side))
    clip(w + "_reload_empty", G, [(0, Z, Z), (f(8), *tilt), (f(44), *tilt), (f(52), *rack), (f(52) + 12, *rack), (e - 4, Z, Z), (e, Z, Z)])
    clip(w + "_reload_empty", M, mag_keys + [(e, Z, Z)])
    r = Vector(rack_arm)
    clip(w + "_reload_empty", A, arm_keys + [(f(53), tuple(r), Z), (f(53) + 3, tuple(r), Z), (f(53) + 7, tuple(r + Vector((0, 0.10, 0))), Z),
                                             (f(53) + 10, tuple(r + Vector((0, 0.10, 0))), Z), (e - 5, Z, Z), (e, Z, Z)])
    if B is not None:
        clip(w + "_reload_empty", B, [(0, Z, Z), (f(53) + 3, Z, Z), (f(53) + 7, (0, 0.10, 0), Z), (f(53) + 8, (0, 0.10, 0), Z),
                                      (f(53) + 10, Z, Z), (e, Z, Z)])


# -------------------------------------------------------------------------------------- PPSh-41
def ppsh_mag(g):
    c_u, c_v = 478, -112
    # Drum axis is PARALLEL to the bore: the circular covers face forward/back,
    # not sideways. Keep the feed tower and top cartridge aligned with the receiver.
    lathe(g, [(c_u - 27, 75), (c_u + 27, 75)], "Blued", 40, cv=c_v)
    lathe(g, [(c_u - 2.2, 76.6), (c_u + 2.2, 76.6)], "Blued", 40, cv=c_v)
    lathe(g, [(c_u - 28, 62), (c_u + 28, 62)], "Blued", 36, cv=c_v)
    lathe(g, [(c_u - 29, 36), (c_u + 29, 36)], "Blued", 28, cv=c_v)
    lathe(g, [(c_u - 33, 9), (c_u - 29, 9)], "Steel", 12, cv=c_v)
    box(g, c_u - 34.5, c_u - 32, c_v - 3, c_v + 3, -13, 13, "Steel", 0.6)
    lathe(g, [(c_u - 29.5, 4), (c_u - 27.5, 4)], "Steel", 10, cx=40, cv=c_v - 40)
    box(g, 460, 496, -46, -22, -9, 9, "Blued", 0.8)                    # feed tower into the magazine well
    box(g, 496, 499, -40, -30, -4, 4, "Steel", 0.3)                    # catch notch
    cartridge(g, 466, -25, 25, 5.0, 4.9, 4.7, 4.2, 35, 3.9, 0, 10)   # top round 7.62x25 in the lips


def ppsh_body(g):
    prism(g, [(0, -44), (6, -42), (6, -168), (0, -170)], -20.5, 20.5, "Blued", 1.0)                      # butt plate
    prism(g, [(6, -42), (100, -34), (200, -26), (290, -20), (330, -20), (352, -22), (352, -56), (330, -60), (290, -64),
              (250, -72), (200, -88), (140, -112), (70, -142), (6, -168)], -20, 20, "Birch", 3.0)          # butt + wrist (comb drops to the heel)
    prism(g, [(345, -20), (560, -20), (568, -28), (565, -42), (552, -50), (352, -50), (345, -40)], -18, 18, "Birch", 2.5)
    ring_rod_loop(g, P(120, -127, 0), 7, 1.6, "Steel")                                                    # sling swivel
    box(g, 112, 128, -122, -116, -5, 5, "Steel", 0.4)
    ring_rod_loop(g, P(548, -55, 0), 6, 1.5, "Steel")
    # stamped receiver (hinged upper), rear cap with the latch
    R = [(-16, -20), (16, -20), (17, 8), (13, 17), (6, 21.5), (-6, 21.5), (-13, 17), (-17, 8)]
    xsec(g, R, 352, 560, "Blued", 1.2)
    xsec(g, [(-14, -20), (14, -20), (14.5, 6), (9, 14), (-9, 14), (-14.5, 6)], 336, 352, "Blued", 1.5)
    box(g, 328, 340, -4, 8, -6, 6, "Steel", 1.0)
    box(g, 440, 500, 19.5, 22.2, -6, 6, "BlackPaint", 0.2)            # ejection port (bolt visible through it)
    box(g, 392, 476, 1, 8, -17.6, -16.4, "BlackPaint", 0.2)           # cocking slot (right)
    box(g, 392, 404, 8, 12, -17.4, -16.2, "BlackPaint", 0.2)          # safety notch
    # L-flip rear sight 100 / 200 m
    box(g, 512, 546, 21, 24.5, -7, 7, "Blued", 0.4)
    box(g, 528, 531, 24.5, 36, -6, -1.6, "Blued", 0.2)
    box(g, 528, 531, 24.5, 36, 1.6, 6, "Blued", 0.2)
    box(g, 528, 531, 24.5, 31, -1.6, 1.6, "Blued", 0.2)
    box(g, 531, 546, 24.5, 26, -6, 6, "Blued", 0.2)
    # perforated barrel jacket, slots as recessed dark bands, slanted front = compensator
    lathe(g, [(560, 16), (815, 16)], "Blued", 24)
    for ang in (0, 38, -38, 180, 142, 218, 90):
        a = D(ang)
        nrm = Vector((math.cos(a), 0, math.sin(a)))
        tan = Vector((0, -1, 0)).cross(nrm)
        for i in range(7):
            u = 585 + i * 31
            if ang == 90 and u > 770:
                continue
            c = P(u, 16.2 * math.sin(a), 16.2 * math.cos(a))
            obox(g, c, (Vector((0, -1, 0)), nrm, tan), (19, 0.8, 7.5), "BlackPaint", 0.3)
    prism(g, [(815, -16), (843, -16), (843, -6), (815, 12)], -16, 16, "Blued", 0.8)
    lathe(g, [(824, 4.6), (846, 4.6)], "BlackPaint", 12)              # bore opening through the brake face
    # hooded front post
    box(g, 798, 820, 15, 20, -5, 5, "Blued", 0.4)
    box(g, 807, 810, 20, 30, -1.2, 1.2, "Blued", 0.2)
    ring(g, 800, 818, 8.5, 10, "Blued", 20, 0, 26)
    # trigger guard, selector, magazine well + catch
    box(g, 385, 446, -77, -73, -5, 5, "Blued", 0.6)
    box(g, 440, 446, -73, -50, -5, 5, "Blued", 0.6)
    box(g, 385, 391, -73, -52, -5, 5, "Blued", 0.6)
    box(g, 428, 434, -56, -50, -3, 3, "Steel", 0.3)
    box(g, 453, 503, -54, -49, -15, 15, "Blued", 0.6)
    box(g, 446, 452, -58, -50, -4, 4, "Steel", 0.3)


def ppsh41():
    global U0
    sc = new_scene("PPSH41")
    U0 = 412.0
    root = empty("ppsh41", (0, 0, 0))
    gun = empty("ppsh41.gun", (0, 0, 0), root)
    ppsh_body("ppsh41.body")
    ppsh_mag("ppsh41.mag")
    prism("ppsh41.trigger", [(408, -50), (414, -50), (416, -58), (415, -66), (411, -67), (412, -58)], -3, 3, "Steel", 0.5)
    box("ppsh41.bolt", 440, 498, 14, 20, -5, 5, "Steel", 0.4)          # bolt top seen through the port
    box("ppsh41.bolt", 455, 470, 2.5, 7.5, -24, -16.5, "Steel", 0.6)   # cocking handle (right)
    cylx("ppsh41.bolt", 463, 5, -31, -23, 5, "Steel", 12)
    flash("ppsh41.flash", 846, 0, 100, 40)
    hand("ppsh41.arm_r", P(330, -46, 0))
    hand("ppsh41.arm_l", P(585, -34, 0))
    join_node("ppsh41.body", gun)
    join_node("ppsh41.mag", gun, P(478, -60, 0))
    join_node("ppsh41.trigger", gun, P(411, -50, 0))
    join_node("ppsh41.bolt", gun, P(465, 8, 0))
    join_node("ppsh41.flash", gun, P(846, 0, 0))
    join_node("ppsh41.arm_r", gun)
    join_node("ppsh41.arm_l", gun)
    empty("ppsh41.sight", P(430, 31, 0), gun)            # rear notch (v 31) -> hooded post tip (v 30)
    empty("ppsh41.muzzle", P(846, 0, 0), gun)
    G, M, A, B = (bpy.data.objects[n] for n in ("ppsh41.gun", "ppsh41.mag", "ppsh41.arm_l", "ppsh41.bolt"))
    smg_reload("ppsh41", G, M, A, B, 84, 100, (0, (585 - 478) / 1000.0, (-34 + 112) / -1000.0), drop=(0.03, 0.04, -0.34),
               rack_arm=(-0.045, (585 - 463) / 1000.0, 0.038))
    return sc


# -------------------------------------------------------------------------------------- MP 40
def mp40_mag(g):
    box(g, 490, 528, -250, -20, -11.5, 11.5, "Blued", 0.8)
    box(g, 487, 531, -258, -250, -13, 13, "Blued", 1.2)                # floorplate
    for s in (1, -1):
        box(g, 497, 521, -244, -104, 11.4 * s, 12.2 * s, "Blued", 0.3)  # pressed stiffening ribs
        for i in range(5):
            cylx(g, 509, -120 - i * 26, 11.6 * s, 12.6 * s, 2.2, "BlackPaint", 8)   # round-count witness holes
    cartridge(g, 495, -22, 19, 4.9, 4.9, 4.8, 4.8, 29.7, 4.5, 0, 10)


def mp40_body(g):
    # receiver tube + rear cap, barrel housing, barrel
    lathe(g, [(240, 15), (248, 17.5), (250, 19), (545, 19)], "Blued", 24)
    lathe(g, [(234, 13.5), (242, 13.5)], "Steel", 18)
    lathe(g, [(545, 16), (565, 16), (565, 14), (610, 14)], "Blued", 20)
    lathe(g, [(610, 10.5), (800, 10.5), (800, 11), (815, 11), (815, 9.5), (833, 9.5)], "Blued", 18)
    lathe(g, [(830, 3.6), (834, 3.6)], "BlackPaint", 10)
    # front sight: base, post, hood
    lathe(g, [(798, 13), (822, 13)], "Blued", 18)
    box(g, 808, 811, 12, 30, -1.2, 1.2, "Blued", 0.2)
    ring(g, 800, 820, 8, 9.5, "Blued", 20, 0, 24)
    # flip rear sight 100 / 200 m
    box(g, 468, 500, 18, 22, -8, 8, "Blued", 0.4)
    box(g, 484, 487, 22, 33, -6, -1.5, "Blued", 0.2)
    box(g, 484, 487, 22, 33, 1.5, 6, "Blued", 0.2)
    box(g, 484, 487, 22, 30, -1.5, 1.5, "Blued", 0.2)
    box(g, 487, 500, 22, 23.5, -6, 6, "Blued", 0.2)
    # bolt handle slot + safety notch (left side), sling bar
    box(g, 330, 472, 0, 6, 17.8, 19.5, "BlackPaint", 0.2)
    box(g, 330, 342, 6, 12, 16.8, 19.0, "BlackPaint", 0.2)
    box(g, 250, 262, -12, -6, 18, 22, "Steel", 0.4)
    # grip frame (steel) + bakelite pistol grip and ribbed fore panel
    box(g, 285, 470, -38, -16, -15, 15, "Blued", 1.0)
    prism(g, [(320, -38), (372, -38), (376, -48), (360, -128), (348, -136), (316, -134), (306, -124), (322, -48)], -16, 16, "Bakelite", 2.0)
    cylx(g, 341, -86, -16.6, 16.6, 3.5, "Steel", 10)
    box(g, 425, 482, -52, -16, -16, 16, "Bakelite", 1.5)
    for i in range(5):
        v0 = -48 + i * 6
        box(g, 428, 479, v0, v0 + 2.5, -16.8, 16.8, "Bakelite", 0.4)
    # trigger guard
    box(g, 376, 428, -70, -66, -5, 5, "Blued", 0.6)
    box(g, 422, 428, -66, -50, -5, 5, "Blued", 0.6)
    box(g, 376, 382, -66, -38, -5, 5, "Blued", 0.6)
    # magazine housing with release button (left)
    box(g, 482, 538, -98, -16, -15, 15, "Blued", 1.0)
    for v0 in (-94, -80):
        box(g, 484, 536, v0, v0 + 3, -15.6, 15.6, "Blued", 0.3)
    cylx(g, 505, -40, 15, 19.5, 5, "Steel", 12)
    # barrel rest bar ("Laufhalter")
    prism(g, [(700, -9), (782, -9), (787, -14), (782, -27), (768, -30), (748, -20), (700, -16)], -6, 6, "Blued", 1.0)
    # under-folding stock: hinge, two struts, butt plate
    cylx(g, 290, -27, -16.5, 16.5, 6, "Steel", 14)
    for s in (1, -1):
        rod(g, P(290, -27, 13 * s), P(30, -44, 13 * s), 5.5, 5.5, "Blued", 10)
    box(g, 22, 40, -52, -36, -15, 15, "Blued", 1.0)
    prism(g, [(8, -12), (24, -12), (26, -112), (14, -118), (6, -110)], -21, 21, "Blued", 2.0)
    cylx(g, 302, -8, 15, 20, 4, "Steel", 10)                          # stock release button


def mp40():
    global U0
    sc = new_scene("MP40")
    U0 = 395.0
    root = empty("mp40", (0, 0, 0))
    gun = empty("mp40.gun", (0, 0, 0), root)
    mp40_body("mp40.body")
    mp40_mag("mp40.mag")
    prism("mp40.trigger", [(392, -40), (398, -40), (400, -48), (399, -56), (395, -57), (396, -48)], -3, 3, "Steel", 0.5)
    rod("mp40.bolt", P(340, 3, 18), P(340, 3, 33), 3.6, 3.6, "Steel", 10)    # bolt handle (left), knob
    lathe("mp40.bolt", [(334, 0), (334, 6), (346, 6.5), (346, 0)], "Steel", 12, 38, 3)
    box("mp40.bolt", 336, 344, 0.5, 5.5, 14, 19, "Steel", 0.3)
    flash("mp40.flash", 834, 0, 90, 34)
    hand("mp40.arm_r", P(345, -82, 0))
    hand("mp40.arm_l", P(580, -26, 0))
    join_node("mp40.body", gun)
    join_node("mp40.mag", gun, P(509, -60, 0))
    join_node("mp40.trigger", gun, P(395, -40, 0))
    join_node("mp40.bolt", gun, P(340, 3, 19))
    join_node("mp40.flash", gun, P(834, 0, 0))
    join_node("mp40.arm_r", gun)
    join_node("mp40.arm_l", gun)
    empty("mp40.sight", P(400, 30, 0), gun)              # rear notch (v 30) -> hooded post tip (v 30)
    empty("mp40.muzzle", P(834, 0, 0), gun)
    G, M, A, B = (bpy.data.objects[n] for n in ("mp40.gun", "mp40.mag", "mp40.arm_l", "mp40.bolt"))
    smg_reload("mp40", G, M, A, B, 60, 74, (0, (580 - 510) / 1000.0, (-26 + 212) / -1000.0), drop=(0.0, 0.03, -0.34),
               rack_arm=(0.035, (580 - 340) / 1000.0, (26 + 3) / 1000.0))
    return sc


# -------------------------------------------------------------------------------------- Mosin-Nagant 1891/30
def mosin_clip(g):
    for i, v in enumerate((-60, -47, -34, -21, -8)):
        cartridge(g, 482, v, 53.7, 7.2, 6.2, 5.8, 4.3, 77.0, 3.9, 0, 12)
    box(g, 478, 484, -67, -1, -7.5, 7.5, "ClipSteel", 0.4)             # stripper clip spine
    for s in (1, -1):
        box(g, 484, 487, -67, -1, 6.6 * s, 7.6 * s, "ClipSteel", 0.2)


def mosin_body(g):
    prism(g, [(0, -38), (7, -38), (7, -164), (0, -166)], -21, 21, "Blued", 1.0)                         # butt plate
    prism(g, [(7, -38), (200, -26), (330, -15), (400, -12), (565, -12), (565, -44), (480, -44), (440, -45), (400, -47),
              (345, -62), (260, -82), (150, -118), (7, -164)], -20, 20, "Birch", 3.0)
    prism(g, [(560, -12), (1150, -6), (1160, -16), (1152, -30), (900, -36), (570, -42)], -16, 16, "Birch", 2.5)
    xsec(g, arc_xsec(17, 11, -20, 200, 14), 690, 1105, "Birch", 1.0)                                    # upper handguard
    xsec(g, band_xsec(19, -38, 18), 860, 878, "Blued", 0.8)                                              # rear band
    xsec(g, band_xsec(19, -33, 17), 1112, 1130, "Blued", 0.8)                                            # front band
    box(g, 880, 900, -14, -8, -17.2, -16, "Steel", 0.3)                                                  # band springs
    box(g, 1132, 1148, -14, -8, -17.2, -16, "Steel", 0.3)
    box(g, 160, 190, -108, -96, -20.6, 20.6, "BlackPaint", 0.5)                                          # sling slots
    box(g, 980, 1004, -31, -23, -16.6, 16.6, "BlackPaint", 0.5)
    # receiver (round 1930s pattern), tang, loading port, charger guides at the bridge
    lathe(g, [(398, 10), (405, 14), (420, 17), (560, 17), (560, 15)], "Blued", 24)
    box(g, 380, 405, -14, -6, -6, 6, "Blued", 0.8)
    cylz(g, 392, 0, -6, -5, 2.5, "Steel", 8)
    box(g, 440, 530, 14.5, 17.7, -7, 7, "BlackPaint", 0.2)
    box(g, 420, 432, 17, 23, -9, 9, "Blued", 0.4)
    box(g, 422, 430, 19, 23.5, -3, 3, "BlackPaint", 0.2)
    # magazine box with floorplate latch, trigger guard
    prism(g, [(468, -40), (566, -40), (562, -72), (478, -74)], -13, 13, "Blued", 1.0)
    box(g, 552, 562, -70, -64, -4, 4, "Steel", 0.3)
    box(g, 402, 470, -66, -62, -5, 5, "Blued", 0.6)
    box(g, 402, 408, -62, -44, -5, 5, "Blued", 0.6)
    # barrel with the muzzle, tangent rear sight, hooded front post, cleaning rod
    lathe(g, [(560, 14.5), (575, 14.5), (575, 13.5), (700, 12.8), (900, 11.6), (1100, 10.6), (1232, 10)], "Blued", 20)
    lathe(g, [(1228, 3.8), (1233, 3.8)], "BlackPaint", 10)
    box(g, 590, 670, 13, 18.5, -10, 10, "Blued", 0.6)
    box(g, 600, 668, 18.5, 21, -5.5, 5.5, "Blued", 0.3)
    box(g, 625, 640, 18.5, 23, -7, 7, "Blued", 0.3)
    box(g, 600, 604, 21, 27.5, -5.5, -1.4, "Blued", 0.2)
    box(g, 600, 604, 21, 27.5, 1.4, 5.5, "Blued", 0.2)
    box(g, 600, 604, 21, 25, -1.4, 1.4, "Blued", 0.2)
    lathe(g, [(1192, 12.5), (1216, 12.5)], "Blued", 18)
    box(g, 1202, 1206, 10, 24.5, -1.1, 1.1, "Blued", 0.2)
    ring(g, 1194, 1216, 9.5, 11, "Blued", 20, 0, 19)
    rod(g, P(1150, -17, 0), P(1236, -17, 0), 3, 3, "Steel", 8)
    sphere(g, P(1236, -17, 0), 3.8, "Steel", seg=8)


def mosin():
    global U0
    sc = new_scene("MOSIN")
    U0 = 432.0
    root = empty("mosin", (0, 0, 0))
    gun = empty("mosin.gun", (0, 0, 0), root)
    mosin_body("mosin.body")
    mosin_clip("mosin.clip")
    prism("mosin.trigger", [(428, -44), (434, -44), (437, -52), (436, -62), (431, -63), (432, -52)], -3, 3, "Steel", 0.5)
    lathe("mosin.bolt", [(445, 0), (445, 9), (530, 9), (530, 0)], "Steel", 16)
    lathe("mosin.bolt", [(372, 0), (372, 9.5), (398, 9.5), (398, 7), (404, 7), (404, 0)], "Steel", 20)
    for i in range(6):
        lathe("mosin.bolt", [(374 + i * 4, 9.9), (376 + i * 4, 9.9)], "Steel", 20)                   # knurled cocking knob
    rod("mosin.bolt", P(418, 1, -9), P(418, -1, -57), 4.8, 4.6, "Steel", 12)
    sphere("mosin.bolt", P(418, -1, -63), 8, "Steel", seg=12)
    flash("mosin.flash", 1233, 0, 130, 46)
    hand("mosin.arm_r", P(360, -50, 0))
    hand("mosin.arm_l", P(780, -30, 0))
    join_node("mosin.body", gun)
    cl = join_node("mosin.clip", gun, P(520, -34, 0))
    join_node("mosin.trigger", gun, P(431, -44, 0))
    bolt = join_node("mosin.bolt", gun, P(450, 0, 0))
    join_node("mosin.flash", gun, P(1233, 0, 0))
    join_node("mosin.arm_r", gun)
    join_node("mosin.arm_l", gun)
    empty("mosin.sight", P(480, 25, 0), gun)             # rear notch (v 25) -> hooded post tip (v 24.5)
    empty("mosin.muzzle", P(1233, 0, 0), gun)
    G, AL, AR = (bpy.data.objects[n] for n in ("mosin.gun", "mosin.arm_l", "mosin.arm_r"))
    knob = P(418, -1, -63) - P(450, 0, 0)
    grip = P(360, -50, 0) - P(450, 0, 0)
    Z = (0, 0, 0)
    clip("mosin_bolt", G, [(0, Z, Z), (3, (0, 0, 0.006), (2, 6, 0)), (19, (0, 0, 0.006), (2, 6, 0)), (26, Z, Z), (30, Z, Z)])
    rifle_bolt_clip("mosin_bolt", bolt, AR, knob, grip, 0, 30, back=0.095)
    clip_reload("mosin", G, bolt, AR, AL, cl, knob, grip, (0, (780 - 520) / 1000.0, (30 + 90 + 30) / 1000.0), 0.090, 0.095)
    return sc


# -------------------------------------------------------------------------------------- Karabiner 98k
def k98_clip(g):
    for i in range(5):
        cartridge(g, 476, -34.5 + i * 10.8, 57.0, 5.9, 5.95, 5.6, 4.4, 80.0, 4.1, 0, 12)
    box(g, 472, 478, -41, 15, -7, 7, "ClipSteel", 0.4)
    for s in (1, -1):
        box(g, 478, 481, -41, 15, 6.0 * s, 7.0 * s, "ClipSteel", 0.2)


def k98_body(g):
    prism(g, [(0, -40), (8, -40), (8, -160), (0, -163)], -21, 21, "Blued", 1.0)                          # cupped butt plate
    prism(g, [(8, -40), (200, -27), (320, -15), (395, -13), (605, -13), (605, -40), (470, -40), (440, -42), (405, -44),
              (385, -62), (372, -86), (352, -92), (325, -86), (300, -80), (150, -118), (8, -160)], -20, 20, "Walnut", 3.0)
    prism(g, [(600, -13), (1000, -7), (1004, -10), (1004, -30), (900, -34), (640, -37), (600, -40)], -16, 16, "Walnut", 2.5)
    box(g, 640, 800, -24, -21.5, -16.4, 16.4, "Walnut", 0.2)                                              # finger grooves (edges)
    box(g, 640, 800, -27.5, -25, -16.4, 16.4, "Walnut", 0.2)
    xsec(g, arc_xsec(16, 10.5, -20, 200, 14), 665, 858, "Walnut", 1.0)                                    # upper handguard
    xsec(g, band_xsec(18, -36, 17), 855, 873, "Blued", 0.8)                                               # barrel band
    ring_rod_loop(g, P(864, -26, 19.5), 6, 1.4, "Steel")                                                  # sling swivel (left)
    xsec(g, [(-14, -32), (14, -32), (15, -12), (12, 4), (7, 12), (-7, 12), (-12, 4), (-15, -12)], 1000, 1040, "Blued", 1.0)
    box(g, 1010, 1058, -40, -32, -5, 5, "Blued", 0.5)                                                     # bayonet lug
    rod(g, P(1040, -21, 0), P(1070, -21, 0), 3, 3, "Steel", 8)                                            # cleaning rod
    sphere(g, P(1070, -21, 0), 4, "Steel", seg=8)
    box(g, 170, 200, -112, -100, -20.6, 20.6, "BlackPaint", 0.5)                                          # sling slot
    cylx(g, 150, -90, -20.8, -19.5, 13, "Blued", 18)                                                      # butt disc (right)
    # barrel, rear sight, hooded barleycorn
    lathe(g, [(600, 14.5), (660, 14.5), (680, 13), (860, 11.5), (1110, 9.2)], "Blued", 20)
    lathe(g, [(1106, 3.9), (1111, 3.9)], "BlackPaint", 10)
    box(g, 600, 660, 13, 18, -10, 10, "Blued", 0.6)
    box(g, 612, 660, 18, 20.5, -5.5, 5.5, "Blued", 0.3)
    box(g, 630, 644, 18, 22.5, -7, 7, "Blued", 0.3)
    box(g, 612, 616, 20.5, 28, -5.5, -1.3, "Blued", 0.2)
    box(g, 612, 616, 20.5, 28, 1.3, 5.5, "Blued", 0.2)
    box(g, 612, 616, 20.5, 25, -1.3, 1.3, "Blued", 0.2)
    lathe(g, [(1078, 11.5), (1102, 11.5)], "Blued", 18)
    prism(g, [(1086, 10), (1096, 10), (1093, 25), (1089, 25)], -1.1, 1.1, "Blued", 0.2)
    ring(g, 1080, 1100, 11, 12.5, "Blued", 20, 0, 15)
    # receiver, bridge with charger guides, tang, floorplate, trigger guard
    lathe(g, [(398, 12), (405, 16), (420, 17), (560, 17), (565, 18.5), (600, 18.5), (600, 14.5)], "Blued", 24)
    box(g, 440, 520, 14.5, 17.6, -7, 7, "BlackPaint", 0.2)
    box(g, 422, 434, 17, 20.5, -9, 9, "Blued", 0.4)                   # bridge + charger guides (below the sight line)
    box(g, 424, 432, 19, 21, -3, 3, "BlackPaint", 0.2)
    box(g, 378, 400, -14, -7, -6, 6, "Blued", 0.8)
    box(g, 470, 562, -42, -38, -12, 12, "Blued", 0.6)
    box(g, 410, 472, -64, -60, -5, 5, "Blued", 0.6)
    box(g, 410, 416, -60, -44, -5, 5, "Blued", 0.6)


def k98k():
    global U0
    sc = new_scene("K98K")
    U0 = 445.0
    root = empty("k98k", (0, 0, 0))
    gun = empty("k98k.gun", (0, 0, 0), root)
    k98_body("k98k.body")
    k98_clip("k98k.clip")
    prism("k98k.trigger", [(441, -44), (447, -44), (450, -52), (449, -60), (444, -61), (445, -52)], -3, 3, "Steel", 0.5)
    lathe("k98k.bolt", [(440, 0), (440, 9), (520, 9), (520, 0)], "Steel", 16)
    lathe("k98k.bolt", [(380, 0), (380, 11), (400, 12), (400, 0)], "Blued", 20)                     # bolt shroud
    box("k98k.bolt", 386, 396, -1.5, 1.5, -26, -10, "Steel", 0.4)                                    # wing safety (fire)
    rod("k98k.bolt", P(432, 0, -10), P(438, -18, -40), 5, 4.5, "Steel", 12)                          # turned-down handle
    sphere("k98k.bolt", P(440, -26, -44), 9.5, "Steel", seg=12)
    flash("k98k.flash", 1111, 0, 130, 46)
    hand("k98k.arm_r", P(365, -62, 0))
    hand("k98k.arm_l", P(740, -30, 0))
    join_node("k98k.body", gun)
    cl = join_node("k98k.clip", gun, P(516, -13, 0))
    join_node("k98k.trigger", gun, P(444, -44, 0))
    bolt = join_node("k98k.bolt", gun, P(450, 0, 0))
    join_node("k98k.flash", gun, P(1111, 0, 0))
    join_node("k98k.arm_r", gun)
    join_node("k98k.arm_l", gun)
    empty("k98k.sight", P(500, 25, 0), gun)              # V notch (v 25) -> barleycorn tip (v 25)
    empty("k98k.muzzle", P(1111, 0, 0), gun)
    G, AL, AR = (bpy.data.objects[n] for n in ("k98k.gun", "k98k.arm_l", "k98k.arm_r"))
    knob = P(440, -26, -44) - P(450, 0, 0)
    grip = P(365, -62, 0) - P(450, 0, 0)
    Z = (0, 0, 0)
    clip("k98k_bolt", G, [(0, Z, Z), (3, (0, 0, 0.006), (2, 6, 0)), (19, (0, 0, 0.006), (2, 6, 0)), (26, Z, Z), (30, Z, Z)])
    rifle_bolt_clip("k98k_bolt", bolt, AR, knob, grip, 0, 30, back=0.088)
    clip_reload("k98k", G, bolt, AR, AL, cl, knob, grip, (0, (740 - 516) / 1000.0, (30 + 60 + 25) / 1000.0), 0.060, 0.088, total=55)
    return sc



# Small fixes must not rewrite every unrelated GLB in the repository.
if "--library-only" in sys.argv:
    pass
elif "--only-ppsh" in sys.argv:
    pp = ppsh41()
    sm = mag_scene("MAG_PPSH", ppsh_mag, 478.0, None)
    export(pp, "ppsh41")
    export(sm, "mag_ppsh", False)
    bpy.context.window.scene = pp
    for scene in list(bpy.data.scenes):
        if scene.name not in SCENES:
            bpy.data.scenes.remove(scene)
    os.makedirs(ROOT + "/models", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=ROOT + "/models/ppsh41.blend", compress=True)
    print("PPSH_DONE")
else:
    ak = ak12()
    m4 = m4a1()
    rp = rpg7()
    t5 = t5000()
    pk = pkp()
    sg = saiga12()
    rm = r870()
    # stand-alone item models
    U0 = 433.0
    sm1 = mag_scene("MAG_AK12", ak12_mag, 433.0, None)
    sm2 = mag_scene("MAG_STANAG", stanag, 375.0, None)
    sm3 = mag_scene("MAG_T5000", t5000_mag, 490.0, None)
    sm4 = mag_scene("BOX_PKP", pkp_box, 495.0, None)
    sm5 = mag_scene("MAG_SAIGA12", saiga_mag, 474.0, None)
    sm6 = mag_scene("SHELL_12", shell_item, 400.0, None)
    ig = igla()
    sn = stinger()
    sm7 = mag_scene("IGLA_MISSILE", igla_missile, 820.0, None)
    sm8 = mag_scene("STINGER_MISSILE", stinger_missile, 690.0, None)
    pp = ppsh41()
    mp = mp40()
    mo = mosin()
    kk = k98k()
    sm9 = mag_scene("MAG_PPSH", ppsh_mag, 478.0, None)
    sm10 = mag_scene("MAG_MP40", mp40_mag, 509.0, None)
    sm11 = mag_scene("CLIP_MOSIN", mosin_clip, 520.0, None)
    sm12 = mag_scene("CLIP_K98", k98_clip, 516.0, None)
    sc = new_scene("PG7V")
    U0 = 950.0
    pg7vl("pg7v", 950, full=True)
    join_node("pg7v")

    export(ak, "ak12")
    export(m4, "m4a1")
    export(rp, "rpg7")
    export(sm1, "mag_ak12", False)
    export(sm2, "mag_stanag", False)
    export(sc, "pg7v", False)
    export(t5, "t5000")
    export(pk, "pkp")
    export(sm3, "mag_t5000", False)
    export(sm4, "box_pkp", False)
    export(sg, "saiga12")
    export(rm, "r870")
    export(sm5, "mag_saiga12", False)
    export(sm6, "shell_12", False)
    export(ig, "igla")
    export(sn, "stinger")
    export(sm7, "igla_missile", False)
    export(sm8, "stinger_missile", False)
    export(pp, "ppsh41")
    export(mp, "mp40")
    export(mo, "mosin")
    export(kk, "k98k")
    export(sm9, "mag_ppsh", False)
    export(sm10, "mag_mp40", False)
    export(sm11, "clip_mosin", False)
    export(sm12, "clip_k98", False)

    for s in list(bpy.data.scenes):
        if s.name not in SCENES:
            bpy.data.scenes.remove(s)
    bpy.context.window.scene = SCENES["AK12"]
    for img in bpy.data.images:
        if not img.packed_file:
            img.pack()
    os.makedirs(ROOT + "/models", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=ROOT + "/models/weapons_r22.blend", compress=True)
    print("WPN_DONE")
