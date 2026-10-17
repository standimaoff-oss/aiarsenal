# ---- aircraft build library (prepended after lib.py to every Tu-95 / Tu-160 / bomb step via Blender-MCP) ----
# Conventions: metres, Blender Z up, nose towards -Y, +X = aircraft LEFT, origin = ground under the main-gear axle.
import bpy, bmesh, math, os, random
from mathutils import Vector, Matrix, Euler, Quaternion
from mathutils.bvhtree import BVHTree

TEXA = "/home/user/.cache/air/tex"
PI = math.pi
D2R = PI / 180.0


def reset_scene():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for c in list(bpy.data.collections):
        bpy.data.collections.remove(c)
    for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.images, bpy.data.actions, bpy.data.cameras, bpy.data.lights,
                 bpy.data.worlds):
        for x in list(coll):
            coll.remove(x)
    sc = bpy.context.scene
    sc.unit_settings.system = 'METRIC'
    sc.unit_settings.scale_length = 1.0
    sc.render.fps = 24
    sc.frame_start = 0
    sc.frame_end = 96
    try:
        sc.view_settings.view_transform = 'AgX'
    except Exception:
        pass


def amat(name, tex=None, rough=0.6, metal=0.0, factor=(1, 1, 1, 1), emissive=None, alpha_clip=False, vcol=True,
         glass_alpha=None):
    """Principled material: texture x factor x vertex colour 'Col'. glass_alpha -> BLENDED transparency."""
    m = bpy.data.materials.get(name)
    if m:
        return m
    m = bpy.data.materials.new(name)
    nt = m.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metal
    src = None
    if tex:
        img = bpy.data.images.get(tex) or bpy.data.images.load(os.path.join(TEXA, tex + ".png"))
        img.name = tex
        ti = nt.nodes.new("ShaderNodeTexImage")
        ti.image = img
        ti.location = (-700, 200)
        src = ti.outputs[0]
        if alpha_clip:
            gt = nt.nodes.new("ShaderNodeMath")
            gt.operation = 'GREATER_THAN'
            gt.inputs[1].default_value = 0.5
            nt.links.new(ti.outputs[1], gt.inputs[0])
            nt.links.new(gt.outputs[0], bsdf.inputs["Alpha"])
    if tuple(factor) != (1, 1, 1, 1):
        mf = nt.nodes.new("ShaderNodeMix")
        mf.data_type = 'RGBA'; mf.blend_type = 'MULTIPLY'; mf.inputs[0].default_value = 1.0
        mf.location = (-450, 200)
        mf.inputs[7].default_value = factor
        if src is not None:
            nt.links.new(src, mf.inputs[6])
        else:
            mf.inputs[6].default_value = (1, 1, 1, 1)
        src = mf.outputs[2]
    if vcol:
        vc = nt.nodes.new("ShaderNodeVertexColor")
        vc.layer_name = "Col"
        vc.location = (-450, -100)
        mx = nt.nodes.new("ShaderNodeMix")
        mx.data_type = 'RGBA'; mx.blend_type = 'MULTIPLY'; mx.inputs[0].default_value = 1.0
        mx.location = (-220, 100)
        if src is not None:
            nt.links.new(src, mx.inputs[6])
        else:
            mx.inputs[6].default_value = factor
        nt.links.new(vc.outputs[0], mx.inputs[7])
        src = mx.outputs[2]
    if src is not None:
        nt.links.new(src, bsdf.inputs["Base Color"])
    else:
        bsdf.inputs["Base Color"].default_value = factor
    if emissive:
        bsdf.inputs["Emission Color"].default_value = emissive
        bsdf.inputs["Emission Strength"].default_value = 1.0
    if alpha_clip:
        m.surface_render_method = 'DITHERED'
    if glass_alpha is not None:
        bsdf.inputs["Alpha"].default_value = glass_alpha
        m.surface_render_method = 'BLENDED'
    m.use_backface_culling = False
    return m


# ------------------------------------------------------------------ object creation (grp = joined node name later)
def aobj(name, bm, mats, grp, smooth=True, uv=None, uvscale=0.25, dirt=True):
    if uv == "box":
        uv_box(bm, uvscale)
    me = bpy.data.meshes.new(name)
    bm.normal_update()
    bm.to_mesh(me)
    bm.free()
    if isinstance(mats, str):
        mats = [mats]
    for mn in mats:
        me.materials.append(bpy.data.materials[mn])
    for p in me.polygons:
        p.use_smooth = smooth
    ob = bpy.data.objects.new(name, me)
    col_get("BUILD").objects.link(ob)
    ob["grp"] = grp
    ob["dirt"] = 1 if dirt else 0
    return ob


def abox(name, size, loc, mat, grp, rot=(0, 0, 0), bevel=0.01, uvscale=1.0, smooth=False, dirt=True):
    return aobj(name, bm_box(size, loc, rot, bevel, 1), mat, grp, smooth=smooth, uv="box", uvscale=uvscale, dirt=dirt)


def acyl(name, r, depth, loc, mat, grp, axis=2, seg=16, r2=None, caps=True, rot=None, uvscale=1.0, smooth=True, dirt=True):
    bm = bm_cyl(r, depth, loc, rot if rot is not None else AX_ROT[axis], seg, r2, caps, 0.0)
    return aobj(name, bm, mat, grp, smooth=smooth, uv="box", uvscale=uvscale, dirt=dirt)


def bm_cyl_between(p0, p1, r, seg=10, r2=None, caps=True):
    p0, p1 = Vector(p0), Vector(p1)
    d = p1 - p0
    q = Vector((0, 0, 1)).rotation_difference(d.normalized())
    mtx = Matrix.Translation((p0 + p1) / 2) @ q.to_matrix().to_4x4()
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=caps, cap_tris=False, segments=seg, radius1=r, radius2=(r if r2 is None else r2),
                          depth=d.length, matrix=mtx)
    return bm


def rod(name, p0, p1, r, mat, grp, seg=10, r2=None, caps=True, dirt=True):
    return aobj(name, bm_cyl_between(p0, p1, r, seg, r2, caps), mat, grp, smooth=True, uv="box", uvscale=1.0, dirt=dirt)


def bm_sphere(r, loc, seg=16, rings=8, scale=(1, 1, 1)):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=seg, v_segments=rings, radius=r,
                              matrix=Matrix.Translation(Vector(loc)) @ Matrix.Diagonal((scale[0], scale[1], scale[2], 1)))
    return bm


def merge_bm(dst, src):
    """Append bmesh src into dst (keeps material indices)."""
    me = bpy.data.meshes.new("_tmp")
    src.to_mesh(me)
    dst.from_mesh(me)
    bpy.data.meshes.remove(me)
    src.free()
    return dst


# ------------------------------------------------------------------ smooth parameter curves
def catmull(ctrl, x):
    """ctrl: sorted list of (x, value). Catmull-Rom interpolation (clamped ends)."""
    xs = [c[0] for c in ctrl]
    if x <= xs[0]:
        return ctrl[0][1]
    if x >= xs[-1]:
        return ctrl[-1][1]
    i = max(k for k in range(len(xs) - 1) if xs[k] <= x)
    x0, x1 = xs[i], xs[i + 1]
    t = (x - x0) / (x1 - x0)
    p0 = ctrl[max(i - 1, 0)][1]
    p1 = ctrl[i][1]
    p2 = ctrl[i + 1][1]
    p3 = ctrl[min(i + 2, len(ctrl) - 1)][1]
    # non-uniform spacing: scale tangents by interval ratios
    xm1 = xs[max(i - 1, 0)]
    x2 = xs[min(i + 2, len(xs) - 1)]
    m1 = (p2 - p0) / max(x1 - xm1, 1e-6) * (x1 - x0)
    m2 = (p3 - p1) / max(x2 - x0, 1e-6) * (x1 - x0)
    t2, t3 = t * t, t * t * t
    return (2 * t3 - 3 * t2 + 1) * p1 + (t3 - 2 * t2 + t) * m1 + (-2 * t3 + 3 * t2) * p2 + (t3 - t2) * m2


def lerp_ctrl(ctrl, x):
    xs = [c[0] for c in ctrl]
    if x <= xs[0]:
        return ctrl[0][1]
    if x >= xs[-1]:
        return ctrl[-1][1]
    i = max(k for k in range(len(xs) - 1) if xs[k] <= x)
    t = (x - xs[i]) / (xs[i + 1] - xs[i])
    return ctrl[i][1] * (1 - t) + ctrl[i + 1][1] * t


# ------------------------------------------------------------------ fuselage loft (superellipse sections along Y)
def section_pt(ang, zc, hw, ht, hb, n, xc=0.0):
    """ang 0 = +X (left), 90 = top. Superellipse with separate upper/lower half-heights."""
    c, s = math.cos(ang), math.sin(ang)
    e = 2.0 / n
    x = xc + hw * math.copysign(abs(c) ** e, c)
    z = zc + (ht if s >= 0 else hb) * math.copysign(abs(s) ** e, s)
    return Vector((x, 0, z))


def loft(name, ys, prof, mats, grp, seg=48, mat_fn=None, cap0=True, cap1=True, uv_tile=4.0, inner=False, angle_range=None,
         smooth=True, dirt=True):
    """ys: list of stations; prof(y) -> (zc, hw, ht, hb, n[, xc]).
    mat_fn(y_mid, ang_mid_deg, z_mid) -> material slot index for each quad.
    angle_range=(a0,a1) degrees builds an open partial shell. UV: u = arc-length metres / uv_tile, v = y / uv_tile."""
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    rings = []
    if angle_range:
        angs = [angle_range[0] + (angle_range[1] - angle_range[0]) * k / seg for k in range(seg + 1)]
    else:
        angs = [2 * PI * k / seg for k in range(seg)]
    angs_r = [a * D2R for a in angs] if angle_range else angs
    for y in ys:
        p = prof(y)
        zc, hw, ht, hb, n = p[:5]
        xc = p[5] if len(p) > 5 else 0.0
        ring = []
        for a in angs_r:
            v = section_pt(a, zc, hw, ht, hb, n, xc)
            v.y = y
            ring.append(bm.verts.new(v))
        rings.append(ring)
    nseg = len(angs_r) if not angle_range else len(angs_r) - 1
    # arc length coordinate per ring (u in metres)
    def arcs(ring):
        out = [0.0]
        cnt = len(ring)
        for k in range(1, cnt + (0 if angle_range else 1)):
            out.append(out[-1] + (ring[k % cnt].co - ring[k - 1].co).length)
        return out
    arcl = [arcs(r) for r in rings]
    for i in range(len(rings) - 1):
        r0, r1 = rings[i], rings[i + 1]
        for k in range(nseg):
            k2 = (k + 1) % len(r0)
            if (r0[k].co - r0[k2].co).length < 1e-5 and (r1[k].co - r1[k2].co).length < 1e-5:
                continue
            quad = (r0[k], r1[k], r1[k2], r0[k2]) if not inner else (r0[k], r0[k2], r1[k2], r1[k])
            try:
                f = bm.faces.new(quad)
            except ValueError:
                continue
            if mat_fn:
                ym = (r0[k].co.y + r1[k].co.y) / 2
                if angle_range:
                    am = (angs[k] + angs[k + 1]) / 2
                else:
                    am = math.degrees(angs_r[k] + PI / seg)
                zm = (r0[k].co.z + r1[k2].co.z) / 2
                f.material_index = mat_fn(ym, am, zm)
            # uv
            uvs = [(arcl[i][k], r0[k].co.y), (arcl[i + 1][k], r1[k].co.y), (arcl[i + 1][k + 1], r1[k2].co.y), (arcl[i][k + 1], r0[k2].co.y)]
            if inner:
                uvs = [uvs[0], uvs[3], uvs[2], uvs[1]]
            for l, (u, v) in zip(f.loops, uvs):
                l[uvl].uv = (u / uv_tile, -v / uv_tile)
    if not angle_range:
        for idx, ok in ((0, cap0), (len(rings) - 1, cap1)):
            if not ok:
                continue
            ring = rings[idx]
            span = max((a.co - b.co).length for a in ring for b in ring[:1])
            if span < 1e-4:
                continue
            c = sum((v.co for v in ring), Vector()) / len(ring)
            cv = bm.verts.new(c)
            for k in range(len(ring)):
                k2 = (k + 1) % len(ring)
                tri = (ring[k], ring[k2], cv) if idx == 0 else (ring[k2], ring[k], cv)
                if inner:
                    tri = tri[::-1]
                try:
                    f = bm.faces.new(tri)
                    for l in f.loops:
                        l[uvl].uv = (l.vert.co.x / uv_tile, l.vert.co.z / uv_tile)
                except ValueError:
                    pass
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bm.normal_update()
    return aobj(name, bm, mats, grp, smooth=smooth, dirt=dirt)


# ------------------------------------------------------------------ lifting surfaces
def airfoil(nc=14, t=0.12, camber=0.0, cos_space=True):
    """Closed airfoil loop (x in 0..1 chord, y thickness): TE upper -> LE -> TE lower. NACA 4-digit thickness."""
    xs = [(1 - math.cos(PI * k / nc)) / 2 if cos_space else k / nc for k in range(nc + 1)]
    def yt(x):
        return 5 * t * (0.2969 * math.sqrt(max(x, 0)) - 0.1260 * x - 0.3516 * x * x + 0.2843 * x ** 3 - 0.1036 * x ** 4)
    def yc(x):
        p, m = 0.4, camber
        if m == 0:
            return 0.0
        return m / p ** 2 * (2 * p * x - x * x) if x < p else m / (1 - p) ** 2 * ((1 - 2 * p) + 2 * p * x - x * x)
    upper = [(x, yc(x) + yt(x)) for x in reversed(xs)]
    lower = [(x, yc(x) - yt(x)) for x in xs[1:-1]]
    return upper + lower  # TE point only once (upper[0]); loop closes lower[-1] -> upper[0]


def surface(name, secs, mats, grp, nc=14, cap_root=False, cap_tip=True, uv_tile=4.0, smooth=True, mat_fn=None, dirt=True):
    """secs: list of dict(le=Vector, cd=chord dir unit, ud=thickness dir unit, c=chord, t=thick ratio, cam=camber).
    Builds a lofted lifting surface. Box-free UV: u = chord metres, v = span metres."""
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    rings = []
    spans = [0.0]
    for i in range(1, len(secs)):
        spans.append(spans[-1] + (Vector(secs[i]["le"]) - Vector(secs[i - 1]["le"])).length)
    for s in secs:
        foil = airfoil(nc, s.get("t", 0.12), s.get("cam", 0.0))
        le, cd, ud, c = Vector(s["le"]), Vector(s["cd"]).normalized(), Vector(s["ud"]).normalized(), s["c"]
        rings.append([bm.verts.new(le + cd * (x * c) + ud * (y * c)) for (x, y) in foil])
    n = len(rings[0])
    for i in range(len(rings) - 1):
        for k in range(n):
            k2 = (k + 1) % n
            try:
                f = bm.faces.new((rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k]))
            except ValueError:
                continue
            if mat_fn:
                f.material_index = mat_fn(i, k)
            for l in f.loops:
                vi = rings[i].index(l.vert) if l.vert in rings[i] else rings[i + 1].index(l.vert)
                sp = spans[i] if l.vert in rings[i] else spans[i + 1]
                c = secs[i]["c"] if l.vert in rings[i] else secs[i + 1]["c"]
                u = (vi / n) * 2 * c
                l[uvl].uv = (u / uv_tile, sp / uv_tile)
    for idx, ok in ((0, cap_root), (len(rings) - 1, cap_tip)):
        if ok:
            ring = rings[idx] if idx == 0 else list(reversed(rings[idx]))
            try:
                f = bm.faces.new(ring)
                for l in f.loops:
                    l[uvl].uv = (l.vert.co.x / uv_tile, l.vert.co.y / uv_tile)
            except ValueError:
                pass
    bm.normal_update()
    # outward normals: flip if the volume is inside-out
    if bm.faces:
        cen = sum((v.co for v in bm.verts), Vector()) / len(bm.verts)
        f0 = max(bm.faces, key=lambda f: (f.calc_center_median() - cen).length)
        if f0.normal.dot(f0.calc_center_median() - cen) < 0:
            bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
    ob = aobj(name, bm, mats, grp, smooth=smooth, dirt=dirt)
    if smooth:
        ob.data.polygons.foreach_set("use_smooth", [True] * len(ob.data.polygons))
    return ob


def wing_secs(root_x, root_le_y, root_z, root_c, tip_x, tip_le_y, tip_z, tip_c, t_root=0.14, t_tip=0.10, n=6, twist_tip=-2.0,
              cam=0.02, side=1, vertical=False):
    """Straight-tapered swept wing (or fin if vertical) between root and tip. side=+1 left (+X), -1 right."""
    secs = []
    for i in range(n + 1):
        f = i / n
        x = root_x + (tip_x - root_x) * f
        y = root_le_y + (tip_le_y - root_le_y) * f
        z = root_z + (tip_z - root_z) * f
        c = root_c + (tip_c - root_c) * f
        tw = twist_tip * f * D2R
        if vertical:
            secs.append(dict(le=(x, y, z), cd=(0, 1, 0), ud=(1, 0, 0), c=c, t=t_root + (t_tip - t_root) * f, cam=0.0))
        else:
            cd = Vector((0, math.cos(tw), -math.sin(tw)))
            ud = Vector((0, math.sin(tw), math.cos(tw)))
            secs.append(dict(le=(x * side, y, z), cd=cd, ud=ud, c=c, t=t_root + (t_tip - t_root) * f, cam=cam))
    return secs


def wing_at(secs, f, chord_frac=0.0):
    """Point on the wing reference plane at span fraction f and chord fraction."""
    n = len(secs) - 1
    i = min(int(f * n), n - 1)
    t = f * n - i
    a, b = secs[i], secs[i + 1]
    le = Vector(a["le"]).lerp(Vector(b["le"]), t)
    c = a["c"] + (b["c"] - a["c"]) * t
    return le + Vector(a["cd"]).normalized() * (c * chord_frac), c


# ------------------------------------------------------------------ lathe along Y (nacelles, spinners, wheels, bombs)
def lathe_y(name, prof, center, mats, grp, seg=24, uv_tile=2.0, smooth=True, mat_fn=None, dirt=True, axis='Y'):
    """prof: list of (y_offset, radius) from front to back. Revolved around an axis parallel to Y through center."""
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    c = Vector(center)
    rings = []
    for (yo, r) in prof:
        ring = []
        for k in range(seg):
            a = 2 * PI * k / seg
            if axis == 'Y':
                p = c + Vector((math.cos(a) * r, yo, math.sin(a) * r))
            elif axis == 'X':
                p = c + Vector((yo, math.cos(a) * r, math.sin(a) * r))
            else:
                p = c + Vector((math.cos(a) * r, math.sin(a) * r, yo))
            ring.append(bm.verts.new(p))
        rings.append(ring)
    acc = [0.0]
    for i in range(1, len(prof)):
        acc.append(acc[-1] + math.hypot(prof[i][0] - prof[i - 1][0], prof[i][1] - prof[i - 1][1]))
    for i in range(len(rings) - 1):
        if prof[i][1] < 1e-5 and prof[i + 1][1] < 1e-5:
            continue
        for k in range(seg):
            k2 = (k + 1) % seg
            vs = (rings[i][k], rings[i + 1][k], rings[i + 1][k2], rings[i][k2])
            if prof[i][1] < 1e-5:
                vs = (rings[i][k], rings[i + 1][k], rings[i + 1][k2])
            elif prof[i + 1][1] < 1e-5:
                vs = (rings[i][k], rings[i + 1][k], rings[i][k2])
            try:
                f = bm.faces.new(vs)
            except ValueError:
                continue
            if mat_fn:
                f.material_index = mat_fn(i, k)
            for l in f.loops:
                ii = i if l.vert in rings[i] else i + 1
                kk = rings[ii].index(l.vert)
                if kk == 0 and (k2 == 0 and l.vert in (rings[i][k2], rings[min(i + 1, len(rings) - 1)][k2])):
                    kk = seg
                rr = max(prof[ii][1], 0.05)
                l[uvl].uv = (kk / seg * 2 * PI * rr / uv_tile, acc[ii] / uv_tile)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    bm.normal_update()
    cen = c
    # outward normals
    bad = sum(1 for f in bm.faces if f.normal.dot(f.calc_center_median() - cen) < 0)
    if bad > len(bm.faces) / 2:
        bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
    return aobj(name, bm, mats, grp, smooth=smooth, dirt=dirt)


def wheel(name, center, r, w, mats, grp, axis='X', seg=24, hub_r=None):
    """Aircraft tyre: rounded profile (lathe around X). mats = [tyre, hub]."""
    hub_r = hub_r or r * 0.55
    prof = [(-w / 2, hub_r * 0.6), (-w / 2, hub_r), (-w / 2 - 0.0, r * 0.82), (-w * 0.42, r * 0.95), (-w * 0.25, r),
            (w * 0.25, r), (w * 0.42, r * 0.95), (w / 2, r * 0.82), (w / 2, hub_r), (w / 2, hub_r * 0.6)]
    def mf(i, k):
        return 1 if (i in (0, len(prof) - 2)) else 0
    return lathe_y(name, prof, center, mats, grp, seg=seg, axis=axis, mat_fn=mf, uv_tile=0.5)


# ------------------------------------------------------------------ hinges / markers / hierarchy
def empty(name, loc, size=0.3, disp='PLAIN_AXES', rot=(0, 0, 0), scale=(1, 1, 1), parent=None):
    e = bpy.data.objects.new(name, None)
    e.empty_display_type = disp
    e.empty_display_size = size
    e.location = Vector(loc)
    e.rotation_euler = Euler(rot)
    e.scale = Vector(scale)
    col_get("RIG").objects.link(e)
    if parent:
        e.parent = parent
        e.matrix_parent_inverse = parent.matrix_world.inverted()
    return e


def hinge(name, p0, p1, parent=None):
    """Empty at p0 whose local +X axis runs along p1-p0 (animate rotation_euler.x)."""
    a = (Vector(p1) - Vector(p0)).normalized()
    beta = -math.asin(max(-1, min(1, a.z)))
    gamma = math.atan2(a.y, a.x)
    e = empty(name, p0, 0.4, 'ARROWS', (0, beta, gamma))
    bpy.context.view_layer.update()
    if parent:
        mw = e.matrix_world.copy()
        e.parent = parent
        e.matrix_world = mw
    return e


def box_marker(name, mn, mx, parent=None):
    """Gameplay volume (walk zone / bay): empty CUBE, location = centre, scale = half extents."""
    mn, mx = Vector(mn), Vector(mx)
    return empty(name, (mn + mx) / 2, 1.0, 'CUBE', scale=((mx.x - mn.x) / 2, (mx.y - mn.y) / 2, (mx.z - mn.z) / 2), parent=parent)


def attach(child, parent):
    bpy.context.view_layer.update()
    mw = child.matrix_world.copy()
    child.parent = parent
    child.matrix_world = mw


# ------------------------------------------------------------------ decals projected onto a target surface
def decal(name, target, center, normal, up, w, h, uvrect, mat, grp, nx=10, ny=5, offset=0.012):
    """Curved decal: a grid in the plane (center, normal, up) is ray-cast onto `target` (world-space mesh)
    and lifted by `offset`. uvrect = (u0, v0, u1, v1) inside the decal atlas (v up)."""
    bvh = BVHTree.FromObject(target, bpy.context.evaluated_depsgraph_get())
    n = Vector(normal).normalized()
    upv = Vector(up)
    upv = (upv - n * upv.dot(n)).normalized()
    rt = upv.cross(n).normalized()
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    grid = []
    for j in range(ny + 1):
        row = []
        for i in range(nx + 1):
            fu, fv = i / nx, j / ny
            p = Vector(center) + rt * ((fu - 0.5) * w) + upv * ((fv - 0.5) * h)
            hit = bvh.ray_cast(p + n * 2.0, -n, 6.0)
            if hit[0] is not None:
                p = hit[0] + hit[1].normalized() * offset if hit[1].dot(n) > 0 else hit[0] + n * offset
            row.append((bm.verts.new(p), fu, fv))
        grid.append(row)
    for j in range(ny):
        for i in range(nx):
            q = [grid[j][i], grid[j][i + 1], grid[j + 1][i + 1], grid[j + 1][i]]
            f = bm.faces.new([x[0] for x in q])
            for l, x in zip(f.loops, q):
                l[uvl].uv = (uvrect[0] + (uvrect[2] - uvrect[0]) * x[1], uvrect[1] + (uvrect[3] - uvrect[1]) * x[2])
    bm.normal_update()
    return aobj(name, bm, mat, grp, smooth=True, dirt=False)


def flat_decal(name, center, normal, up, w, h, uvrect, mat, grp, offset=0.012):
    n = Vector(normal).normalized()
    upv = Vector(up)
    upv = (upv - n * upv.dot(n)).normalized()
    rt = upv.cross(n).normalized()
    c = Vector(center) + n * offset
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    corners = [(-0.5, -0.5), (0.5, -0.5), (0.5, 0.5), (-0.5, 0.5)]
    vs = [bm.verts.new(c + rt * (a * w) + upv * (b * h)) for a, b in corners]
    f = bm.faces.new(vs)
    for l, (a, b) in zip(f.loops, corners):
        l[uvl].uv = (uvrect[0] + (uvrect[2] - uvrect[0]) * (a + 0.5), uvrect[1] + (uvrect[3] - uvrect[1]) * (b + 0.5))
    return aobj(name, bm, mat, grp, smooth=False, dirt=False)


def uv_planar(ob, rect, axes=None, flip=(False, False)):
    """Map the object's faces planarly into atlas rect (u0,v0,u1,v1) using its two largest extents."""
    me = ob.data
    cs = [v.co for v in me.vertices]
    mn = Vector((min(c.x for c in cs), min(c.y for c in cs), min(c.z for c in cs)))
    mx = Vector((max(c.x for c in cs), max(c.y for c in cs), max(c.z for c in cs)))
    ext = mx - mn
    if axes is None:
        axes = sorted(range(3), key=lambda i: -ext[i])[:2]
    uvl = me.uv_layers.active or me.uv_layers.new()
    for li, loop in enumerate(me.loops):
        c = me.vertices[loop.vertex_index].co
        u = (c[axes[0]] - mn[axes[0]]) / max(ext[axes[0]], 1e-6)
        v = (c[axes[1]] - mn[axes[1]]) / max(ext[axes[1]], 1e-6)
        if flip[0]:
            u = 1.0 - u
        if flip[1]:
            v = 1.0 - v
        uvl.data[li].uv = (rect[0] + (rect[2] - rect[0]) * u, rect[1] + (rect[3] - rect[1]) * v)


# ------------------------------------------------------------------ finishing: weathering, joins, smoothing
def clamp01(x):
    return 0.0 if x < 0 else (1.0 if x > 1 else x)


def hash3(v):
    return (math.sin(v.x * 12.9898 + v.y * 78.233 + v.z * 37.719) * 43758.5453) % 1.0


def weather(fn):
    """fn(world_pos, world_normal, obj) -> (r,g,b) multiplier; written into colour attribute 'Col'."""
    for ob in list(col_get("BUILD").objects):
        me = ob.data
        if me is None or me.color_attributes.get("Col"):
            continue
        ca = me.color_attributes.new("Col", 'BYTE_COLOR', 'CORNER')
        mw = ob.matrix_world
        m3 = mw.to_3x3()
        for poly in me.polygons:
            nw = (m3 @ poly.normal).normalized()
            for li in poly.loop_indices:
                w = mw @ me.vertices[me.loops[li].vertex_index].co
                if ob.get("dirt", 1):
                    r, g, b = fn(w, nw, ob)
                else:
                    r, g, b = 1.0, 1.0, 1.0
                ca.data[li].color = (clamp01(r), clamp01(g), clamp01(b), 1.0)
        me.color_attributes.active_color = ca


def join_grp(grp, name):
    obs = [o for o in col_get("BUILD").objects if o.get("grp") == grp]
    if not obs:
        return None
    for c in bpy.context.view_layer.layer_collection.children:
        c.exclude = False
    for o in obs:  # every part needs the colour attribute before joining (else black vertices)
        if not o.data.color_attributes.get("Col"):
            ca = o.data.color_attributes.new("Col", 'BYTE_COLOR', 'CORNER')
            for d in ca.data:
                d.color = (1, 1, 1, 1)
    bpy.ops.object.select_all(action='DESELECT')
    for o in obs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = obs[0]
    if len(obs) > 1:
        bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active
    ob.name = name
    ob.data.name = name
    ob.data.color_attributes.active_color = ob.data.color_attributes.get("Col")
    return ob


def set_origin(ob, p):
    """Move object origin to world point p without moving geometry."""
    p = Vector(p)
    mw = ob.matrix_world.copy()
    local = mw.inverted() @ p
    ob.data.transform(Matrix.Translation(-local))
    ob.matrix_world = mw @ Matrix.Translation(local)


def stats():
    tris = 0
    for o in bpy.data.objects:
        if o.type == 'MESH':
            tris += sum(len(p.vertices) - 2 for p in o.data.polygons)
    return dict(objects=len([o for o in bpy.data.objects if o.type == 'MESH']), tris=tris, materials=len(bpy.data.materials),
                images=len(bpy.data.images))


# ------------------------------------------------------------------ keyframe clips -> NLA tracks (glTF animations)
def aclip(name, ob, path, keys, index=-1, interp='LINEAR'):
    ad = ob.animation_data_create()
    act = bpy.data.actions.new(name + "_" + ob.name)
    ad.action = act
    saved_loc, saved_rot = ob.location.copy(), ob.rotation_euler.copy()
    for fr, val in keys:
        if path == "rotation_euler":
            r = ob.rotation_euler.copy()
            if index >= 0:
                r[index] = val
            else:
                r = Euler(val)
            ob.rotation_euler = r
        else:
            l = ob.location.copy()
            if index >= 0:
                l[index] = val
            else:
                l = Vector(val)
            ob.location = l
        ob.keyframe_insert(path, index=index, frame=fr)
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
    ob.location, ob.rotation_euler = saved_loc, saved_rot


def finish_nla():
    for o in bpy.data.objects:
        if o.animation_data:
            for tr in o.animation_data.nla_tracks:
                for st in tr.strips:
                    st.extrapolation = 'NOTHING'
                tr.mute = True
    bpy.context.scene.frame_set(0)


def export_glb(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    rest = {o.name: (o.location.copy(), o.rotation_euler.copy(), o.scale.copy()) for o in bpy.data.objects}
    bpy.context.scene.frame_set(0)
    bpy.ops.object.select_all(action='DESELECT')
    sc = bpy.context.scene
    with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
        bpy.ops.export_scene.gltf(filepath=path, export_format='GLB', export_yup=True, export_apply=True,
                                  export_animations=True, export_animation_mode='NLA_TRACKS', export_force_sampling=True,
                                  export_vertex_color='ACTIVE', export_extras=False, export_cameras=False, export_lights=False,
                                  export_image_format='AUTO', use_visible=False, use_selection=False, use_active_scene=True)
    for n, (l, r, s) in rest.items():
        o = bpy.data.objects.get(n)
        if o:
            o.location, o.rotation_euler, o.scale = l, r, s
    return os.path.getsize(path)


def save_blend(path, scene_name):
    for img in list(bpy.data.images):
        if img.users == 0:
            bpy.data.images.remove(img)
    bpy.ops.outliner.orphans_purge(do_local_ids=True, do_linked_ids=True, do_recursive=True)
    for img in bpy.data.images:
        if not img.packed_file:
            img.pack()
    sc = bpy.context.scene
    sc.name = scene_name
    for k in [k for k in sc.keys() if k.startswith("blendermcp")]:
        del sc[k]
    bpy.ops.wm.save_as_mainfile(filepath=path, compress=True, copy=True)
    return os.path.getsize(path)


def delete_faces_mat(ob, idx):
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if f.material_index == idx], context='FACES')
    bm.to_mesh(ob.data)
    bm.free()
    # drop the placeholder slot if it is the last one
    if idx == len(ob.data.materials) - 1:
        ob.data.materials.pop(index=idx)


def in_panes(y, a, panes):
    for (y0, y1, a0, a1) in panes:
        if y0 <= y <= y1 and a0 <= a <= a1:
            return True
    return False


def open_box(name, size, loc, mat, grp, open_faces=("-Z",)):
    """Box liner with chosen faces removed (wells, bays). Normals point inwards."""
    bm = bm_box(size, loc, (0, 0, 0), 0.0, 1)
    c = Vector(loc)
    kill = []
    for f in bm.faces:
        n = f.normal
        for o in open_faces:
            ax = "XYZ".index(o[1])
            sgn = 1 if o[0] == "+" else -1
            if n[ax] * sgn > 0.9:
                kill.append(f)
    bmesh.ops.delete(bm, geom=kill, context='FACES')
    bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
    return aobj(name, bm, mat, grp, smooth=False, uv="box", uvscale=1.0)
