# ---- BM-21 build library (sent through Blender-MCP execute_code, prepended to each step) ----
import bpy, bmesh, math, os
from mathutils import Vector, Matrix, Euler, Quaternion

TEX = "/home/user/.cache/bm21/tex"
PI = math.pi
D2R = PI / 180.0


def col_get(name):
    c = bpy.data.collections.get(name)
    if c is None:
        c = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(c)
    return c


def M(name):
    return bpy.data.materials[name]


# ------------------------------------------------------------------ UV helpers
def uv_box(bm, scale=1.0, offset=(0.0, 0.0)):
    """World-scale box projection: 1 texture tile == 1/scale metres."""
    uvl = bm.loops.layers.uv.verify()
    for f in bm.faces:
        n = f.normal
        ax = max(range(3), key=lambda i: abs(n[i]))
        for l in f.loops:
            co = l.vert.co
            if ax == 0:
                u, v = co.y * (1 if n.x > 0 else -1), co.z
            elif ax == 1:
                u, v = co.x * (-1 if n.y > 0 else 1), co.z
            else:
                u, v = co.x, co.y * (1 if n.z > 0 else -1)
            l[uvl].uv = (u * scale + offset[0], v * scale + offset[1])


def uv_cyl(bm, axis, center, scale_len=1.0, u_rep=1.0, v_off=0.0):
    """Cylindrical projection around axis (0=X,1=Y,2=Z); caps get planar."""
    uvl = bm.loops.layers.uv.verify()
    a = axis
    b, c = [i for i in range(3) if i != a]
    for f in bm.faces:
        n = f.normal
        cap = abs(n[a]) > 0.8
        angs = []
        for l in f.loops:
            co = l.vert.co - Vector(center)
            angs.append(math.atan2(co[c], co[b]) / (2 * PI) + 0.5)
        # fix seam
        if max(angs) - min(angs) > 0.5:
            angs = [x + 1.0 if x < 0.5 else x for x in angs]
        for l, ang in zip(f.loops, angs):
            co = l.vert.co - Vector(center)
            if cap:
                l[uvl].uv = (co[b] * scale_len + 0.5, co[c] * scale_len + 0.5 + v_off)
            else:
                l[uvl].uv = (ang * u_rep, co[a] * scale_len + v_off)


# ------------------------------------------------------------------ mesh object creation
def make_obj(name, bm, mat, grp, uv="box", uvscale=1.0, smooth=False, dirt=True, uvargs=None):
    if uv == "box":
        uv_box(bm, uvscale)
    elif uv == "cyl":
        uv_cyl(bm, **(uvargs or {}))
    me = bpy.data.meshes.new(name)
    bm.normal_update()
    bm.to_mesh(me)
    bm.free()
    if isinstance(mat, str):
        mat = [mat]
    for m in mat:
        me.materials.append(M(m))
    for p in me.polygons:
        p.use_smooth = smooth
    ob = bpy.data.objects.new(name, me)
    col_get("BUILD").objects.link(ob)
    ob["grp"] = grp
    ob["dirt"] = 1 if dirt else 0
    return ob


def bm_box(size, loc=(0, 0, 0), rot=(0, 0, 0), bevel=0.012, seg=1):
    bm = bmesh.new()
    mtx = Matrix.Translation(Vector(loc)) @ Euler(rot).to_matrix().to_4x4() @ Matrix.Diagonal((size[0], size[1], size[2], 1))
    bmesh.ops.create_cube(bm, size=1.0, matrix=mtx)
    if bevel > 0:
        bmesh.ops.bevel(bm, geom=list(bm.edges), offset=min(bevel, min(size) * 0.45), segments=seg,
                        affect='EDGES', profile=0.5, clamp_overlap=True)
    return bm


def box(name, size, loc, mat, grp, rot=(0, 0, 0), bevel=0.012, seg=1, uvscale=1.0, dirt=True):
    return make_obj(name, bm_box(size, loc, rot, bevel, seg), mat, grp, uvscale=uvscale, dirt=dirt)


def bm_cyl(r, depth, loc=(0, 0, 0), rot=(0, 0, 0), seg=16, r2=None, caps=True, bevel=0.0):
    bm = bmesh.new()
    mtx = Matrix.Translation(Vector(loc)) @ Euler(rot).to_matrix().to_4x4()
    bmesh.ops.create_cone(bm, cap_ends=caps, cap_tris=False, segments=seg, radius1=r,
                          radius2=(r if r2 is None else r2), depth=depth, matrix=mtx)
    if bevel > 0 and caps:
        rim = [e for e in bm.edges if len(e.link_faces) == 2 and
               abs(e.link_faces[0].normal.dot(e.link_faces[1].normal)) < 0.5]
        bmesh.ops.bevel(bm, geom=rim, offset=bevel, segments=1, affect='EDGES', clamp_overlap=True)
    return bm


AX_ROT = {0: (0, PI / 2, 0), 1: (PI / 2, 0, 0), 2: (0, 0, 0)}


def cyl(name, r, depth, loc, mat, grp, axis=2, seg=16, r2=None, caps=True, bevel=0.0, smooth=True, uvscale=1.0, dirt=True):
    bm = bm_cyl(r, depth, loc, AX_ROT[axis], seg, r2, caps, bevel)
    return make_obj(name, bm, mat, grp, uv="cyl", smooth=smooth, dirt=dirt,
                    uvargs=dict(axis=axis, center=loc, scale_len=uvscale, u_rep=max(1.0, round(2 * PI * r * uvscale))))


def bm_lathe(profile, axis_pt, axis, seg=24, angle=2 * PI):
    """profile: list of (radius, along) points; revolve around axis through axis_pt."""
    bm = bmesh.new()
    a = axis
    b, c = [i for i in range(3) if i != a]
    rings = []
    for k in range(seg):
        t = angle * k / seg
        ring = []
        for (rad, al) in profile:
            co = [0.0, 0.0, 0.0]
            co[a] = axis_pt[a] + al
            co[b] = axis_pt[b] + rad * math.cos(t)
            co[c] = axis_pt[c] + rad * math.sin(t)
            ring.append(bm.verts.new(co))
        rings.append(ring)
    n = len(profile)
    for k in range(seg):
        r0, r1 = rings[k], rings[(k + 1) % seg]
        for i in range(n - 1):
            try:
                bm.faces.new((r0[i], r0[i + 1], r1[i + 1], r1[i]))
            except ValueError:
                pass
    bm.normal_update()
    return bm


def bm_sweep_ribbon(sections):
    """sections: list of lists of Vector (same count) -> quad strip surface."""
    bm = bmesh.new()
    vs = [[bm.verts.new(p) for p in sec] for sec in sections]
    for i in range(len(vs) - 1):
        for j in range(len(vs[i]) - 1):
            bm.faces.new((vs[i][j], vs[i + 1][j], vs[i + 1][j + 1], vs[i][j + 1]))
    return bm


def bm_tube_path(points, r, seg=6):
    """Pipe/hose along a polyline."""
    bm = bmesh.new()
    pts = [Vector(p) for p in points]
    rings = []
    for i, p in enumerate(pts):
        if i == 0:
            t = (pts[1] - pts[0]).normalized()
        elif i == len(pts) - 1:
            t = (pts[-1] - pts[-2]).normalized()
        else:
            t = ((pts[i + 1] - pts[i]).normalized() + (pts[i] - pts[i - 1]).normalized()).normalized()
        up = Vector((0, 0, 1)) if abs(t.z) < 0.9 else Vector((1, 0, 0))
        n1 = t.cross(up).normalized()
        n2 = t.cross(n1).normalized()
        rings.append([bm.verts.new(p + (n1 * math.cos(2 * PI * k / seg) + n2 * math.sin(2 * PI * k / seg)) * r) for k in range(seg)])
    for i in range(len(rings) - 1):
        for k in range(seg):
            bm.faces.new((rings[i][k], rings[i][(k + 1) % seg], rings[i + 1][(k + 1) % seg], rings[i + 1][k]))
    return bm


def mirror_x(bm):
    """Mirror a bmesh across X=0 (fixes winding)."""
    for v in bm.verts:
        v.co.x = -v.co.x
    bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
    return bm


def dup_mirror(ob, newname=None):
    me = ob.data.copy()
    bm = bmesh.new()
    bm.from_mesh(me)
    mirror_x(bm)
    bm.to_mesh(me)
    bm.free()
    o2 = bpy.data.objects.new(newname or (ob.name + "_R"), me)
    col_get("BUILD").objects.link(o2)
    for k in ob.keys():
        o2[k] = ob[k]
    return o2


def objs_in_grp(grp):
    return [o for o in col_get("BUILD").objects if o.get("grp") == grp]

# ---- shared vehicle constants (metres, Blender Z-up, front = -Y)
WR, WW = 0.60, 0.38
TRACK = 2.00
AXLES = {"F": -2.45, "M": 1.075, "R": 2.475}
ZW = WR
DECK_Z = 1.25
