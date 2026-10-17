# Soviet general-purpose HE bombs, M-54 pattern: FAB-250, FAB-500, FAB-1500. Axis Y (nose -Y), origin at the centre.
reset_scene()
col_get("BUILD"); col_get("RIG")
amat("M_Bomb", "T_Bomb", 0.55, 0.15)
amat("M_BombFin", None, 0.6, 0.15, factor=(0.36, 0.39, 0.33, 1))
amat("M_Brass", None, 0.35, 1.0, factor=(0.78, 0.60, 0.28, 1))
amat("M_DarkMetal", None, 0.5, 0.7, factor=(0.20, 0.20, 0.21, 1))
BOMBS = [("FAB250", 1.59, 0.325, 0, 2), ("FAB500", 2.37, 0.45, 1, 2), ("FAB1500", 3.00, 0.58, 2, 1)]
PROF = [(0.0, 0.0), (0.012, 0.30), (0.04, 0.60), (0.09, 0.84), (0.16, 0.97), (0.22, 1.0), (0.62, 1.0), (0.72, 0.93), (0.82, 0.74),
        (0.90, 0.55), (0.93, 0.50), (0.93, 0.0)]
info = {}
for name, L, D, band, nlugs in BOMBS:
    R = D / 2
    g = name
    y0 = -L / 2
    body = lathe_y(name + "_body", [(f * L, r * R) for f, r in PROF], (0, y0, 0), ["M_Bomb"], g, seg=24)
    # stencil UVs: u around, v along; stencil band of this type on the parallel mid-body, plain paint elsewhere
    me = body.data
    uvl = me.uv_layers.active
    bv0, bv1 = 1 - (120 + 110 * band) / 512, 1 - (10 + 110 * band) / 512
    for li, lp in enumerate(me.loops):
        c = me.vertices[lp.vertex_index].co
        t = (c.y - y0) / L
        u = math.atan2(c.z, c.x) / (2 * PI) + 0.5
        if 0.28 <= t <= 0.60:
            v = bv0 + (t - 0.28) / 0.32 * (bv1 - bv0)
        else:
            v = 0.12
        uvl.data[li].uv = (u * 2.0, v)
    # nose fuze + ballistic ring on struts
    acyl(name + "_fuze", R * 0.16, L * 0.035, (0, y0 - L * 0.012, 0), "M_Brass", g, axis=1, seg=12)
    ry = y0 + L * 0.10
    rr = R * 0.86
    lathe_y(name + "_bring", [(-L * 0.02, rr * 1.04), (-L * 0.02, rr * 1.10), (L * 0.02, rr * 1.10), (L * 0.02, rr * 1.04),
                              (-L * 0.02, rr * 1.04)], (0, ry, 0), ["M_BombFin"], g, seg=24)
    # tail: 4 fins (X pattern) with a box/ring stabiliser
    ty0, ty1 = y0 + L * 0.74, y0 + L * 1.02
    for k in range(4):
        a = PI / 4 + k * PI / 2
        dirv = Vector((math.cos(a), 0, math.sin(a)))
        rin = R * 0.55
        rout = R * 1.06
        bm = bmesh.new()
        pts = [dirv * rin + Vector((0, ty0 + L * 0.06, 0)), dirv * rout + Vector((0, ty0 + L * 0.12, 0)), dirv * rout + Vector((0, ty1, 0)),
               dirv * rin + Vector((0, ty1, 0))]
        th = Vector((-math.sin(a), 0, math.cos(a))) * max(0.006, R * 0.025)
        vs = [bm.verts.new(p + th) for p in pts] + [bm.verts.new(p - th) for p in pts]
        for f in ((0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)):
            bm.faces.new([vs[i] for i in f])
        aobj(name + "_fin%d" % k, bm, "M_BombFin", g, smooth=False, uv="box", uvscale=1.0)
    lathe_y(name + "_tailring", [(0.0, R * 1.02), (0.0, R * 1.07), (L * 0.14, R * 1.07), (L * 0.14, R * 1.02), (0.0, R * 1.02)],
            (0, ty1 - L * 0.14, 0), ["M_BombFin"], g, seg=24)
    acyl(name + "_tailplug", R * 0.5, 0.02, (0, y0 + L * 0.93, 0), "M_DarkMetal", g, axis=1, seg=16)
    # suspension lugs on top (+Z)
    lug_ys = [y0 + L * 0.36, y0 + L * 0.36 + 0.25] if nlugs == 2 else [y0 + L * 0.42]
    for k, ly in enumerate(lug_ys):
        rod(name + "_lugA%d" % k, (0, ly - 0.03, R * 0.97), (0, ly - 0.03, R * 1.12), 0.012, "M_DarkMetal", g, seg=6)
        rod(name + "_lugB%d" % k, (0, ly + 0.03, R * 0.97), (0, ly + 0.03, R * 1.12), 0.012, "M_DarkMetal", g, seg=6)
        rod(name + "_lugC%d" % k, (0, ly - 0.03, R * 1.12), (0, ly + 0.03, R * 1.12), 0.012, "M_DarkMetal", g, seg=6)
    info[name] = dict(L=L, D=D)


def bomb_weather(w, n, ob):
    h = hash3(w)
    k = 0.94 + 0.08 * h
    if n.z < -0.4:
        k *= 0.85
    return k, k, k


weather(bomb_weather)
J = {}
for name, *_ in BOMBS:
    J[name] = join_grp(name, name + "_geo")
for name, *_ in BOMBS:
    e = empty(name, (0, 0, 0), 0.5, 'ARROWS')
    attach(J[name], e)
bpy.context.view_layer.update()
print("BOMBS", stats(), info)
print("GLB", export_glb("/home/user/models/bombs.glb"))
print("BLEND", save_blend("/home/user/models/bombs.blend", "Bombs"))
