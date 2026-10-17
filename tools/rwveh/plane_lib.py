# ---- r11 WW2 aircraft helpers. exec()'d by the plane scripts AFTER craft_common.py.
# Kind / glTF frame: +X left, +Y up, +Z forward; Blender: K(x, y, z) = (x, -z, y).  Origin = ground under the main
# wheel axles with the airframe modelled LEVEL (flight attitude); the tail wheel hangs at y = |z_tw| * tan(tailDeg) so
# that PlaneEntity's ground pitch (tailDeg, about the origin) puts it on the ground.
# Node contract (CraftRenderer.posePlane): root, <id>_body, prop[_i] (spins about glTF Z), prop_disc[_i], gear_L/R/T
# (+X rotation retracts; L/R also yaw for outward folding is baked in by the pivot), ail_L/ail_R/elev (about X),
# rud (about Y), flap_L/flap_R, bay_L/bay_R (bomb doors, about Z), bomb_0..n, canopy, tur_<i>_yaw > tur_<i>_pitch.
import math


def KV(p):
    return K(p[0], p[1], p[2])


def plane_mats(top, side, bot, interior, frame=(0.3, 0.3, 0.3)):
    craft_mats()
    ww2_mats()
    amat("M_Top", top, 0.62, 0.08)
    amat("M_Side", side, 0.62, 0.08)
    amat("M_Bot", bot, 0.6, 0.08)
    amat("M_Int", interior, 0.8, 0.05)
    amat("M_Frame", None, 0.6, 0.1, factor=frame + (1,))
    amat("M_Canopy", None, 0.04, 0.2, factor=(0.55, 0.62, 0.66, 1), glass_alpha=0.22)
    amat("M_DialWW2", None, 0.5, 0.0, factor=(0.03, 0.03, 0.03, 1))
    amat("M_Lume", None, 0.4, 0.0, factor=(0.85, 0.9, 0.75, 1), emissive=(0.25, 0.32, 0.2, 1))
    amat("M_Leather", None, 0.75, 0.0, factor=(0.30, 0.18, 0.10, 1))
    amat("M_Oleo", None, 0.15, 1.0, factor=(0.88, 0.88, 0.9, 1))
    amat("M_Spin", None, 0.55, 0.05, factor=(0.12, 0.12, 0.12, 1))
    amat("M_SpinWhite", None, 0.55, 0.0, factor=(0.85, 0.85, 0.8, 1))
    amat("M_Yellow", None, 0.6, 0.0, factor=(0.85, 0.66, 0.05, 1))
    amat("M_RedNose", None, 0.6, 0.0, factor=(0.55, 0.07, 0.05, 1))
    amat("M_BombDE", None, 0.6, 0.15, factor=(0.36, 0.40, 0.36, 1))
    amat("M_Reticle", None, 0.3, 0.0, factor=(1.0, 0.42, 0.08, 1), emissive=(1.0, 0.42, 0.08, 1.0))
    amat("M_BombSU", None, 0.6, 0.15, factor=(0.30, 0.34, 0.24, 1))
    amat("M_Burnt", None, 0.7, 0.5, factor=(0.18, 0.13, 0.10, 1))


def paint(ob, zt=0.34, slots=("M_Top", "M_Side", "M_Bot"), base=(0,)):
    """Re-material faces of `ob` whose current slot is in `base`: top / side / bottom by face normal z (Blender)."""
    me = ob.data
    idx = []
    for mn in slots:
        if mn not in [m.name for m in me.materials]:
            me.materials.append(bpy.data.materials[mn])
        idx.append([m.name for m in me.materials].index(mn))
    for p in me.polygons:
        if p.material_index not in base:
            continue
        nz = p.normal.z
        p.material_index = idx[0] if nz > zt else (idx[2] if nz < -zt else idx[1])
    return ob


# ------------------------------------------------------------------------------------------- lifting surfaces
def _yt(x, t):
    x = min(max(x, 0.0), 1.0)
    return 5 * t * (0.2969 * math.sqrt(x) - 0.1260 * x - 0.3516 * x * x + 0.2843 * x ** 3 - 0.1036 * x ** 4)


def foil_pts(x0, x1, t, nc=10):
    xs = [x0 + (x1 - x0) * (1 - math.cos(PI * k / nc)) / 2 for k in range(nc + 1)]
    up = [(x, _yt(x, t)) for x in reversed(xs)]
    lo = [(x, -_yt(x, t)) for x in xs]
    if x0 < 1e-3:
        lo = lo[1:]
    if x1 > 0.999:
        lo = lo[:-1]
    return up + lo


def wsurf(name, secs, x0, x1, mats, grp, nc=10, cap0=True, cap1=True):
    """Lifting surface between chord fractions x0..x1. secs: dict(le, cd, ud, c, t) in Blender coords.
    UV: planar projection on the surface's own plane (metres / 4)."""
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    rings = []
    for s in secs:
        le, cd, ud, c = Vector(s["le"]), Vector(s["cd"]).normalized(), Vector(s["ud"]).normalized(), s["c"]
        rings.append([bm.verts.new(le + cd * (x * c) + ud * (y * c)) for (x, y) in foil_pts(x0, x1, s["t"], nc)])
    n = len(rings[0])
    sp = Vector(secs[-1]["le"]) - Vector(secs[0]["le"])
    ax_u = Vector(secs[0]["cd"]).normalized()
    ax_v = (sp - ax_u * sp.dot(ax_u)).normalized() if sp.length > 1e-6 else Vector((1, 0, 0))
    def uv(co):
        return (co.dot(ax_v) / 4.0, co.dot(ax_u) / 4.0)
    for i in range(len(rings) - 1):
        for k in range(n):
            k2 = (k + 1) % n
            try:
                f = bm.faces.new((rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k]))
            except ValueError:
                continue
            for l in f.loops:
                l[uvl].uv = uv(l.vert.co)
    for ring, ok, rev in ((rings[0], cap0, True), (rings[-1], cap1, False)):
        if not ok:
            continue
        try:
            f = bm.faces.new(list(reversed(ring)) if rev else ring)
            for l in f.loops:
                l[uvl].uv = uv(l.vert.co)
        except ValueError:
            pass
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.normal_update()
    ob = aobj(name, bm, mats, grp, smooth=True)
    return ob


def planform(f, cr, ct, sweep, ell0, round_le=0.5):
    """chord + LE offset (m, positive = aft) at span fraction f. ell0: start of the elliptical tip (1 = none)."""
    c_lin = cr + (ct - cr) * f
    e = 1.0
    if f > ell0:
        u = min(1.0, (f - ell0) / max(1e-6, 1 - ell0))
        e = math.sqrt(max(0.0, 1 - u * u))
    c = max(c_lin * e, 0.03 * cr)
    return c, sweep * f + (c_lin - c) * round_le


def wing(P, sx, root, span, cr, ct, sweep, dih, t0, t1, ell0, grp, parts=(), hinge=0.74, nsec=12, ud_axis='Z',
         tip_round=0.5, mats=("M_Top",)):
    """Half wing (sx = +1 left, -1 right) from kind-frame root LE `root` (x, y, z). parts: list of
    (f0, f1, grp_part) movable trailing-edge parts (flaps / ailerons / elevator) that are cut at `hinge`.
    ud_axis 'Z' = horizontal surface, 'X' = vertical fin (span goes up, sx ignored).
    Returns (secs_fn, hinge_pts dict grp -> (p0, p1) kind coords)."""
    d = math.radians(dih)
    fs = set(round(k / nsec, 4) for k in range(nsec + 1))
    for k in range(1, 6):
        fs.add(round(1 - (0.5 ** k) * (1 - ell0) if ell0 < 1 else 1.0, 4))
    for (a, b, g) in parts:
        fs.add(round(a, 4)); fs.add(round(b, 4))
    fs = sorted(f for f in fs if 0 <= f <= 1)
    def sec(f):
        c, dz = planform(f, cr, ct, sweep, ell0, tip_round)
        if ud_axis == 'Z':
            p = (root[0] + sx * span * f * math.cos(d), root[1] + span * f * math.sin(d), root[2] - dz)
            ud = Vector((-sx * math.sin(d), 0, math.cos(d)))
        else:
            p = (root[0], root[1] + span * f, root[2] - dz)
            ud = Vector((1, 0, 0))
        tt = t0 + (t1 - t0) * f
        return dict(le=KV(p), cd=Vector((0, 1, 0)), ud=ud, c=c, t=tt, kp=p)
    # spans: cut points
    cuts = sorted(set([0.0, 1.0] + [a for a, b, g in parts] + [b for a, b, g in parts]))
    hinges = {}
    for i in range(len(cuts) - 1):
        a, b = cuts[i], cuts[i + 1]
        S = [sec(f) for f in fs if a - 1e-6 <= f <= b + 1e-6]
        if len(S) < 2:
            continue
        mov = [g for (pa, pb, g) in parts if pa <= a + 1e-6 and pb >= b - 1e-6]
        nm = "%s_w%d_%d" % (P, sx, i)
        if mov:
            wsurf(nm, S, 0.0, hinge - 0.005, list(mats), grp)
            wsurf(nm + "m", S, hinge, 1.0, list(mats), mov[0])
            p0 = Vector(S[0]["le"]) + Vector((0, 1, 0)) * S[0]["c"] * hinge
            p1 = Vector(S[-1]["le"]) + Vector((0, 1, 0)) * S[-1]["c"] * hinge
            if mov[0] in hinges:
                hinges[mov[0]] = (hinges[mov[0]][0], p1)
            else:
                hinges[mov[0]] = (p0, p1)
        else:
            wsurf(nm, S, 0.0, 1.0, list(mats), grp)
    return sec, hinges


def fin(P, root, height, cr, ct, sweep, t0, t1, ell0, grp, rud_grp=None, hinge=0.68, f0=0.04, f1=0.98):
    parts = [(f0, f1, rud_grp)] if rud_grp else []
    return wing(P + "_fin", 1, root, height, cr, ct, sweep, 0, t0, t1, ell0, grp, parts=parts, hinge=hinge, ud_axis='X')


# ------------------------------------------------------------------------------------------- fuselage helpers
def fus(name, z_nose, z_tail, ctrl, mats, grp, mat_fn=None, n=40, seg=32, cap0=True, cap1=True):
    """ctrl rows (t, y_centre, half_width, half_height_top, half_height_bottom, squareness), t 0 = nose."""
    y0, L = -z_nose, z_nose - z_tail
    return fuselage(name, y0, L, ctrl, list(mats), grp, n=n, seg=seg, mat_fn=mat_fn, cap0=cap0, cap1=cap1)


def ctrl_at(ctrl, z_nose, z_tail):
    """prof(z_kind) -> (yc, hw, ht, hb, n)."""
    pf = ctrl_prof(ctrl, z_nose - z_tail, -z_nose)
    return lambda z: pf(-z)


def surf_pt(prof, z, ang_deg, off=0.0):
    """Point on the loft surface (kind frame) at station z and section angle (0 = left, 90 = top)."""
    yc, hw, ht, hb, nn = prof(z)[:5]
    a = math.radians(ang_deg)
    c, s = math.cos(a), math.sin(a)
    e = 2.0 / nn
    x = (hw + off) * math.copysign(abs(c) ** e, c)
    y = yc + ((ht if s >= 0 else hb) + off) * math.copysign(abs(s) ** e, s)
    return (x, y, z)


def frame_ring(name, prof, z, a0, a1, r, grp, mat="M_Frame", n=14, off=0.012):
    pts = [KV(surf_pt(prof, z, a0 + (a1 - a0) * k / n, off)) for k in range(n + 1)]
    return aobj(name, bm_tube_path(pts, r, seg=5), [mat], grp, smooth=True, dirt=False)


def frame_line(name, prof, z0, z1, ang, r, grp, mat="M_Frame", n=10, off=0.012):
    pts = [KV(surf_pt(prof, z0 + (z1 - z0) * k / n, ang, off)) for k in range(n + 1)]
    return aobj(name, bm_tube_path(pts, r, seg=5), [mat], grp, smooth=True, dirt=False)


def inner_tub(name, ctrl, z_nose, z_tail, z0, z1, sill, grp, inset=0.025, mat="M_Int"):
    """Cockpit/cabin inner skin (visible from inside: closed hull faces are back-face culled) between stations z0>z1,
    covering the opaque part of the section below the glazing sill angle (deg above horizontal)."""
    pf = ctrl_at(ctrl, z_nose, z_tail)
    def prof(y):
        yc, hw, ht, hb, nn = pf(-y)[:5]
        return (yc, max(0.05, hw - inset), max(0.05, ht - inset), max(0.05, hb - inset), nn)
    ys = [-z0 + (z0 - z1) * k / 8 for k in range(9)]
    return loft(name, ys, prof, [mat], grp, seg=20, inner=True, angle_range=(180 - sill, 360 + sill), smooth=True, dirt=False)


# ------------------------------------------------------------------------------------------- props / gear / guns
def propeller(P, hub, r, nb, chord, grp, disc_grp, spin_r, spin_len, spin_mat="M_Spin", pitch=(42, 16), blade_mat="M_Prop"):
    """Tractor propeller centred at kind point hub (spinner points to +Z)."""
    c = KV(hub)
    spinner(P + "_spin", tuple(c), spin_r, spin_len, spin_mat, grp, direction=-1)
    lathe_y(P + "_spback", [(0.0, 0.0), (0.0, spin_r), (0.06, spin_r * 0.98), (0.06, 0.0)], tuple(c), [spin_mat], grp, seg=20)
    obs = prop_blades(P + "_pb", tuple(c + Vector((0, -spin_len * 0.25, 0))), r, nb, [blade_mat], grp, axis=(0, -1, 0),
                      chord=chord, r_hub=spin_r * 0.6, pitch_root=pitch[0], pitch_tip=pitch[1], t=0.09)
    blur_disc(P + "_disc", (c.x, c.y - spin_len * 0.25, c.z), r, disc_grp, r_in=spin_r * 1.05)
    # blur disc lies in Blender XY by default -> rotate into the XZ plane (prop disc faces -Y)
    ob = bpy.data.objects[P + "_disc"]
    me = ob.data
    cc = Vector((c.x, c.y - spin_len * 0.25, c.z))
    for v in me.vertices:
        d = v.co - cc
        v.co = cc + Vector((d.x, d.z, d.y))
    return obs


def tyre(name, c, r, w, grp, hub_mat="M_Steel"):
    lathe_y(name, [(-w / 2, r * 0.45), (-w / 2, r * 0.86), (-w * 0.36, r * 0.98), (0.0, r), (w * 0.36, r * 0.98), (w / 2, r * 0.86), (w / 2, r * 0.45)],
            tuple(c), ["M_Tire"], grp, seg=22, axis='X', uv_tile=0.5)
    lathe_y(name + "h", [(-w * 0.45, 0.0), (-w * 0.45, r * 0.47), (-w * 0.3, r * 0.5), (w * 0.3, r * 0.5), (w * 0.45, r * 0.47), (w * 0.45, 0.0)],
            tuple(c), [hub_mat], grp, seg=16, axis='X')


def gear_leg(P, top, axle, r, w, grp, fork=False, door=None, door_mat="M_Bot", torque=True, wheel_in=False):
    """Oleo leg from kind point `top` to the axle (wheel outside, +X side for left legs). door = (w, h) leg fairing."""
    t, a = KV(top), KV(axle)
    up = (t - a).normalized()
    rod(P + "_oleo", a + up * 0.01, a + up * ((t - a).length * 0.55), 0.045, "M_Oleo", grp, seg=10)
    rod(P + "_leg", a + up * ((t - a).length * 0.45), t + up * 0.24, 0.065, "M_Steel", grp, seg=10)   # r11: runs up into the wing root (no gap)
    sgn = 1 if axle[0] >= 0 else -1
    if wheel_in:
        sgn = -sgn
    if fork:
        for s in (1, -1):
            rod(P + "_fk%d" % s, a + Vector((s * (w * 0.5 + 0.03), 0, 0)) + up * 0.02, a + up * (r * 0.9) + Vector((s * (w * 0.5 + 0.03), 0, 0)), 0.03, "M_Steel", grp, seg=8)
        abox(P + "_fkx", (w + 0.12, 0.08, 0.06), tuple(a + up * (r * 0.95)), "M_Steel", grp, bevel=0.01)
        acyl(P + "_ax", 0.035, w + 0.12, tuple(a), "M_Steel", grp, axis=0, seg=10)
        tyre(P + "_wh", a, r, w, grp)
    else:
        acyl(P + "_ax", 0.035, w * 0.6, tuple(a + Vector((sgn * w * 0.3, 0, 0))), "M_Steel", grp, axis=0, seg=10)
        tyre(P + "_wh", a + Vector((sgn * (w * 0.5 + 0.04), 0, 0)), r, w, grp)
    if torque:
        m = a + up * ((t - a).length * 0.5)
        rod(P + "_tl1", m, m + Vector((0, 0.18, -0.12)), 0.018, "M_Steel", grp, seg=6)
        rod(P + "_tl2", m + Vector((0, 0.18, -0.12)), a + up * (r * 0.45), 0.018, "M_Steel", grp, seg=6)
    if door:
        dw, dh = door
        side = Vector((sgn, 0, 0))
        mid = a + up * (r + dh * 0.5)
        abox(P + "_door", (0.012, dw, dh), tuple(mid + side * 0.09 + Vector((0, 0, 0))), door_mat, grp, bevel=0.004)


def barrel(name, base, tip, r, grp, mat="M_Dark", jacket=None):
    rod(name, KV(base), KV(tip), r, mat, grp, seg=10)
    if jacket:
        jr, jl = jacket
        b, t = KV(base), KV(tip)
        d = (t - b).normalized()
        rod(name + "_j", t - d * jl, t - d * 0.02, jr, mat, grp, seg=12)
    acyl(name + "_bore", r * 0.6, 0.02, tuple(KV(tip)), "M_Bore", grp, axis=1, seg=8, dirt=False)


def exhaust_row(P, start, step, n, grp, sx, r=0.035, length=0.16, angle=-30):
    """Row of ejector exhaust stubs along -Z from kind point start, pointing outward/aft."""
    for i in range(n):
        p = Vector(start) + Vector((0, 0, -step * i))
        a = KV(p)
        d = Vector((sx * 0.6, 0.9, 0.0)).normalized()
        rod("%s_ex%d" % (P, i), a, a + d * length, r, "M_Exhaust", grp, seg=8)
        acyl("%s_exb%d" % (P, i), r * 0.65, 0.01, tuple(a + d * length), "M_Burnt", grp, axis=1, seg=6, dirt=False)


def wdecal(name, target, c, n, up, w, key, grp, h=None):
    """Decal from the WW2 atlas projected onto `target` (object or list of objects, e.g. all wing segments)."""
    rc = WW2_RECTS[key]
    if h is None:
        h = w * (rc[3] - rc[1]) / max(1e-6, (rc[2] - rc[0]))
    tg = target if isinstance(target, (list, tuple)) else [target]
    if len(tg) == 1:
        tobj = tg[0]
    else:
        verts, faces = [], []
        for o in tg:
            base = len(verts)
            verts += [o.matrix_world @ v.co for v in o.data.vertices]
            faces += [[base + i for i in p.vertices] for p in o.data.polygons]
        me = bpy.data.meshes.new(name + "_tmp")
        me.from_pydata([tuple(v) for v in verts], [], faces)
        tobj = bpy.data.objects.new(name + "_tmp", me)
        bpy.context.scene.collection.objects.link(tobj)
        bpy.context.view_layer.update()
    ob = decal(name, tobj, KV(c), Vector((n[0], -n[2], n[1])), Vector((up[0], -up[2], up[1])), w, h, rc, "M_DecalWW2", grp,
               nx=10, ny=6, offset=0.008)
    if len(tg) > 1:
        bpy.data.objects.remove(tobj, do_unlink=True)
    return ob


def dial(name, c, r, grp, facing=(0, 0, -1)):
    """WW2 black instrument with luminous ticks, facing the pilot (kind -Z by default)."""
    cc = KV(c)
    acyl(name + "_bz", r + 0.008, 0.025, tuple(cc), "M_Steel", grp, axis=1, seg=16, dirt=False)
    acyl(name + "_fc", r, 0.008, tuple(cc + Vector((0, 0.013, 0))), "M_DialWW2", grp, axis=1, seg=16, dirt=False)
    for k in range(8):
        a = math.radians(-135 + 270 * k / 7)
        p = cc + Vector((math.sin(a) * r * 0.78, 0.018, math.cos(a) * r * 0.78))
        abox("%s_t%d" % (name, k), (0.004, 0.003, r * 0.24), tuple(p), "M_Lume", grp, rot=(0, a, 0), bevel=0.0, dirt=False)
    abox(name + "_n", (0.004, 0.003, r * 0.7), tuple(cc + Vector((r * 0.2, 0.02, r * 0.2))), "M_Lume", grp, rot=(0, 0.8, 0), bevel=0.0, dirt=False)


def cut_top(ob, prof, z0, z1, ang):
    """r12: open the cockpit - delete the hull faces above section angle `ang` (deg above horizontal) between kind
    stations z0 > z1. Before, the closed fuselage skin ran just under the canopy and hid the whole cockpit."""
    import bmesh
    me = ob.data
    bm = bmesh.new()
    bm.from_mesh(me)
    mw = ob.matrix_world
    kill = []
    for f in bm.faces:
        c = mw @ f.calc_center_median()
        x, y, z = c.x, c.z, -c.y
        if not (z1 < z < z0):
            continue
        yc, hw, ht, hb, nn = prof(z)[:5]
        if y <= yc:
            continue
        a = math.degrees(math.atan2((y - yc) / max(ht, 1e-3), abs(x) / max(hw, 1e-3)))
        if a > ang:
            kill.append(f)
    bmesh.ops.delete(bm, geom=kill, context='FACES')
    bm.to_mesh(me)
    bm.free()
    me.update()
    print("CUT_TOP", ob.name, len(kill), "faces")


def fighter_cockpit(P, eye, grp, panel_z, panel_w, floor_y, nation="DE", sight="revi", coam=None):
    """Seat (armoured back), stick, pedals, instrument panel, reflector gunsight, throttle quadrant, trim wheel,
    side consoles. eye = the IN-GAME pilot eye (seat y + 0.77). r12: the panel sits BELOW the coaming `coam` (fuselage
    top at the panel), the gunsight glass stands on the coaming just under the line of sight."""
    ex, ey, ez = eye
    if coam is None:
        coam = ey - 0.17
    seat_z = ez - 0.25
    sy = ey - 0.80                      # cushion top: sitting eye height ~0.8 m
    # seat bucket + armour
    abox(P + "_seat", (0.42, 0.40, 0.06), tuple(KV((ex, sy - 0.06, seat_z))), "M_Int", grp, bevel=0.02)
    for s in (1, -1):
        abox("%s_seatw%d" % (P, s), (0.03, 0.40, 0.30), tuple(KV((ex + s * 0.21, sy + 0.08, seat_z))), "M_Int", grp, bevel=0.01)
    abox(P + "_seatb", (0.44, 0.06, 0.66), tuple(KV((ex, sy + 0.30, seat_z - 0.24))), "M_Steel", grp, bevel=0.015)
    abox(P + "_head", (0.30, 0.06, 0.26), tuple(KV((ex, ey + 0.02, seat_z - 0.40))), "M_Steel", grp, bevel=0.02)
    abox(P + "_cush", (0.36, 0.34, 0.06), tuple(KV((ex, sy - 0.01, seat_z + 0.02))), "M_Leather", grp, bevel=0.02)
    abox(P + "_backc", (0.34, 0.05, 0.40), tuple(KV((ex, sy + 0.26, seat_z - 0.19))), "M_Leather", grp, bevel=0.02)
    for s in (1, -1):    # harness straps over the shoulders + lap belt
        abox("%s_belt%d" % (P, s), (0.05, 0.012, 0.5), tuple(KV((ex + s * 0.1, sy + 0.30, seat_z - 0.19))), "M_Canvas", grp, bevel=0.0, dirt=False)
    abox(P + "_lap", (0.36, 0.05, 0.012), tuple(KV((ex, sy + 0.04, seat_z + 0.12))), "M_Canvas", grp, bevel=0.0, dirt=False)
    abox(P + "_buckle", (0.06, 0.06, 0.015), tuple(KV((ex, sy + 0.06, seat_z + 0.13))), "M_Chrome", grp, bevel=0.0, dirt=False)
    # floor + foot troughs
    abox(P + "_floor", (0.62, 1.5, 0.03), tuple(KV((ex, floor_y, ez + 0.35))), "M_Int", grp, bevel=0.0)
    # stick (between the knees, grip at about seat + 0.45)
    sb = KV((ex, floor_y + 0.02, ez + 0.32))
    gy = sy + 0.42 - floor_y
    rod(P + "_stick", sb, sb + Vector((0, -0.04, gy)), 0.016, "M_Dark", grp, seg=8)
    rod(P + "_grip", sb + Vector((0, -0.04, gy - 0.04)), sb + Vector((0, -0.05, gy + 0.12)), 0.026, "M_Leather" if nation == "DE" else "M_Dark", grp, seg=8)
    abox(P + "_trig", (0.012, 0.03, 0.04), tuple(sb + Vector((0, -0.08, gy + 0.05))), "M_Chrome", grp, bevel=0.0, dirt=False)
    if nation == "SU":   # brake lever loop on the RK stick
        rod(P + "_brk", sb + Vector((0.0, -0.02, gy + 0.10)), sb + Vector((0.0, 0.06, gy + 0.02)), 0.006, "M_Steel", grp, seg=6)
    lathe_y(P + "_boot", [(0.0, 0.0), (0.0, 0.08), (0.06, 0.05), (0.12, 0.02), (0.12, 0.0)], tuple(sb), ["M_Leather"], grp, seg=10, axis='Z')
    # rudder pedals
    for s in (1, -1):
        p = KV((ex + s * 0.16, floor_y + 0.12, panel_z + 0.12))
        abox("%s_ped%d" % (P, s), (0.09, 0.03, 0.2), tuple(p), "M_Steel", grp, rot=(math.radians(-25), 0, 0), bevel=0.005)
    rod(P + "_pedbar", KV((ex + 0.2, floor_y + 0.06, panel_z + 0.16)), KV((ex - 0.2, floor_y + 0.06, panel_z + 0.16)), 0.012, "M_Steel", grp, seg=6)
    # instrument panel (vertical, facing aft) - its top 3 cm under the coaming
    ph = 0.30
    top = coam - 0.03
    pz = panel_z
    abox(P + "_panel", (panel_w, 0.03, ph), tuple(KV((ex, top - ph / 2, pz))), "M_Dash", grp, bevel=0.01)
    abox(P + "_coam", (panel_w + 0.04, 0.12, 0.035), tuple(KV((ex, coam - 0.02, pz - 0.02))), "M_Leather", grp, bevel=0.012)
    abox(P + "_subp", (panel_w * 0.6, 0.18, 0.03), tuple(KV((ex, top - ph - 0.02, pz - 0.08))), "M_Dash", grp, rot=(math.radians(50), 0, 0), bevel=0.008)
    rows = [(-0.06, (-0.16, 0.0, 0.16)), (-0.165, (-0.21, -0.07, 0.07, 0.21)), (-0.255, (-0.1, 0.1))]
    k = 0
    for dy, xs in rows:
        for dx in xs:
            dial("%s_dl%d" % (P, k), (ex + dx * panel_w / 0.6, top + dy, pz - 0.02), 0.040 if k < 3 else 0.033, grp)
            k += 1
    for i in range(6):   # switch row + warning lamps
        abox("%s_ps%d" % (P, i), (0.012, 0.02, 0.012), tuple(KV((ex - 0.15 + 0.06 * i, top - ph + 0.025, pz - 0.025))), "M_Chrome", grp, bevel=0.0, dirt=False)
    # reflector gunsight on the coaming: body, inclined glass, illuminated ring reticle (just below the line of sight)
    if sight:
        gz = pz - 0.06
        abox(P + "_sight", (0.065, 0.10, 0.07), tuple(KV((ex, coam + 0.03, gz))), "M_Dark", grp, bevel=0.01)
        acyl(P + "_slamp", 0.02, 0.05, tuple(KV((ex, coam + 0.03, gz - 0.06))), "M_Steel", grp, axis=1, seg=10)
        gy0 = coam + 0.105
        abox(P + "_reflg", (0.075, 0.003, 0.065), tuple(KV((ex, gy0, gz - 0.01))), "M_Canopy", grp, rot=(math.radians(-25), 0, 0), bevel=0.0, dirt=False)
        ring = [(-0.0008, 0.019), (-0.0008, 0.021), (0.0008, 0.021), (0.0008, 0.019)]
        lathe_y(P + "_ret", ring, tuple(KV((ex, gy0, gz - 0.008))), ["M_Reticle"], grp, seg=20, axis='Y', dirt=False)
        abox(P + "_retx", (0.03, 0.0015, 0.0015), tuple(KV((ex, gy0, gz - 0.008))), "M_Reticle", grp, bevel=0.0, dirt=False)
        abox(P + "_rety", (0.0015, 0.0015, 0.03), tuple(KV((ex, gy0, gz - 0.008))), "M_Reticle", grp, bevel=0.0, dirt=False)
    # throttle quadrant (left wall) + trim wheel (left) + right electrical console, oxygen, compass
    abox(P + "_thrq", (0.05, 0.30, 0.03), tuple(KV((ex + 0.27, sy + 0.30, ez + 0.05))), "M_Dash", grp, bevel=0.01)
    rod(P + "_thr", KV((ex + 0.27, sy + 0.30, ez + 0.05)), KV((ex + 0.24, sy + 0.45, ez + 0.12)), 0.012, "M_Steel", grp, seg=6)
    acyl(P + "_thrk", 0.025, 0.03, tuple(KV((ex + 0.24, sy + 0.45, ez + 0.12))), "M_Black", grp, axis=0, seg=10)
    acyl(P + "_trim", 0.11, 0.025, tuple(KV((ex + 0.27, sy + 0.10, seat_z + 0.05))), "M_Black", grp, axis=0, seg=18)
    abox(P + "_lcon", (0.05, 0.5, 0.10), tuple(KV((ex + 0.27, sy + 0.20, ez - 0.05))), "M_Dash", grp, bevel=0.01)
    abox(P + "_rcon", (0.05, 0.5, 0.12), tuple(KV((ex - 0.27, sy + 0.25, ez - 0.05))), "M_Dash", grp, bevel=0.01)
    for i in range(5):
        abox("%s_sw%d" % (P, i), (0.012, 0.02, 0.012), tuple(KV((ex - 0.25, sy + 0.32, ez + 0.1 - 0.06 * i))), "M_Chrome", grp, bevel=0.0, dirt=False)
    acyl(P + "_oxy", 0.045, 0.05, tuple(KV((ex - 0.26, sy + 0.40, ez + 0.22))), "M_DialWW2", grp, axis=0, seg=14)
    acyl(P + "_comp", 0.05, 0.04, tuple(KV((ex, sy + 0.08, ez + 0.52))), "M_Black", grp, axis=1, seg=14)
    abox(P + "_mapc", (0.03, 0.25, 0.18), tuple(KV((ex - 0.28, sy + 0.05, seat_z + 0.05))), "M_Canvas", grp, bevel=0.01)


def bomber_seat(P, pos, grp, armour=True):
    x, y, z = pos
    abox(P + "_s", (0.46, 0.44, 0.07), tuple(KV((x, y, z))), "M_Int", grp, bevel=0.02)
    abox(P + "_c", (0.40, 0.38, 0.06), tuple(KV((x, y + 0.05, z))), "M_Leather", grp, bevel=0.02)
    abox(P + "_b", (0.46, 0.06, 0.62), tuple(KV((x, y + 0.34, z - 0.22))), "M_Steel" if armour else "M_Int", grp, bevel=0.015)
    abox(P + "_p", (0.06, 0.06, 0.3), tuple(KV((x, y - 0.17, z))), "M_Steel", grp, bevel=0.0)


def yoke(P, base, grp):
    """Bomber control column with a W-shaped yoke (He 111 swing-over arm style simplified)."""
    b = KV(base)
    rod(P + "_col", b, b + Vector((0, 0.15, 0.6)), 0.025, "M_Dark", grp, seg=8)
    t = b + Vector((0, 0.15, 0.62))
    rod(P + "_yk", t + Vector((0.16, 0, 0)), t + Vector((-0.16, 0, 0)), 0.018, "M_Dark", grp, seg=8)
    for s in (1, -1):
        rod("%s_yh%d" % (P, s), t + Vector((s * 0.16, 0, 0)), t + Vector((s * 0.17, 0, 0.1)), 0.022, "M_Leather", grp, seg=8)


def mg_mount(P, pivot_k, aim, length, grp, r=0.012, ammo=True, twin=False, cal="mg"):
    """Flexible MG (MG 15 / MG 81Z / ShKAS / UBT) at kind point pivot, pointing along kind dir `aim`."""
    p = KV(pivot_k)
    d = Vector((aim[0], -aim[2], aim[1])).normalized()
    offs = [Vector((0.05, 0, 0)), Vector((-0.05, 0, 0))] if twin else [Vector()]
    for i, o in enumerate(offs):
        abox("%s_rcv%d" % (P, i), (0.06, 0.32, 0.08), tuple(p + o + d * 0.05), "M_Dark", grp, bevel=0.01,
             rot=(math.asin(max(-1, min(1, d.z))), 0, math.atan2(-d.x, -d.y) if abs(d.x) + abs(d.y) > 1e-4 else 0))
        rod("%s_brl%d" % (P, i), p + o + d * 0.2, p + o + d * length, r * (1.6 if cal == "hmg" else 1.0), "M_Dark", grp, seg=8)
        rod("%s_jkt%d" % (P, i), p + o + d * 0.2, p + o + d * (0.2 + length * 0.45), r * 1.9, "M_Dark", grp, seg=10)
        if ammo:
            acyl("%s_drum%d" % (P, i), 0.07, 0.07, tuple(p + o + Vector((0, 0, 0.08))), "M_Dark", grp, axis=0, seg=14)
    abox(P + "_grip", (0.03, 0.04, 0.1), tuple(p - d * 0.14 + Vector((0, 0, -0.05))), "M_Wood", grp, bevel=0.005)


def bomb_geo(name, c, L, dia, grp, mat="M_BombDE", axis='Y', nose_up=False, ring=True):
    """WW2 GP bomb (SC / FAB): ogive nose, cylindrical body, tapered tail cone, 4 box fins (+ strut ring).
    c = kind centre; axis 'Y' = horizontal (nose to +Z kind), 'Z' = vertical (nose up if nose_up)."""
    r = dia / 2
    tl = L * 0.36                         # tail unit length
    bl = L - tl
    prof = [(-bl / 2, 0.0), (-bl / 2 + bl * 0.06, r * 0.55), (-bl / 2 + bl * 0.2, r * 0.95), (-bl / 2 + bl * 0.3, r),
            (bl / 2 - bl * 0.12, r), (bl / 2, r * 0.82), (bl / 2 + tl * 0.7, r * 0.28), (bl / 2 + tl * 0.72, 0.0)]
    cc = KV(c)
    if axis == 'Y':
        center = cc + Vector((0, tl / 2, 0))
        lathe_y(name, prof, tuple(center), [mat], grp, seg=14, axis='Y')
        tail_c = center + Vector((0, bl / 2 + tl * 0.55, 0))
        for k in range(4):
            a = PI / 4 + k * PI / 2
            d = Vector((math.cos(a), 0, math.sin(a)))
            abox("%s_f%d" % (name, k), (0.008, tl * 0.75, r * 1.0), tuple(tail_c + d * r * 0.75), mat, grp,
                 rot=(0, -a + PI / 2, 0), bevel=0.0)
        if ring:
            lathe_y(name + "_rg", [(-tl * 0.12, r * 1.18), (-tl * 0.12, r * 1.24), (tl * 0.12, r * 1.24), (tl * 0.12, r * 1.18)],
                    tuple(tail_c + Vector((0, tl * 0.2, 0))), [mat], grp, seg=14, axis='Y')
    else:
        sgn = -1 if nose_up else 1           # Blender Z: lathe profile runs along +Z; nose at -bl/2
        prof2 = [(sgn * y, rr) for (y, rr) in prof]
        if sgn < 0:
            prof2 = prof2[::-1]
        center = cc + Vector((0, 0, -sgn * tl / 2))
        lathe_y(name, prof2, tuple(center), [mat], grp, seg=14, axis='Z')
        tail_c = center + Vector((0, 0, sgn * (bl / 2 + tl * 0.55)))
        for k in range(4):
            a = PI / 4 + k * PI / 2
            d = Vector((math.cos(a), math.sin(a), 0))
            abox("%s_f%d" % (name, k), (0.008, r * 1.0, tl * 0.75), tuple(tail_c + d * r * 0.75), mat, grp,
                 rot=(0, 0, a - PI / 2), bevel=0.0)


def turret_gun(P, tur, base, grp_y, grp_p, length, twin=False, cal="mg", ball=0.0, ring=0.0):
    """Gun group pointing to +Z (kind) at rest: CraftRenderer applies the absolute azimuth / elevation."""
    if ball:
        bm = bm_sphere(ball, KV(base), seg=16, rings=10)
        aobj("%s_t%d_ball" % (P, tur), bm, ["M_Frame"], grp_p, smooth=True)
    if ring:
        lathe_y("%s_t%d_ring" % (P, tur), [(-0.04, ring - 0.05), (-0.04, ring), (0.04, ring), (0.04, ring - 0.05)],
                tuple(KV(base)), ["M_Steel"], grp_y, seg=24, axis='Z')
        abox("%s_t%d_yoke" % (P, tur), (0.08, 0.08, 0.3), tuple(KV((base[0], base[1] - 0.18, base[2]))), "M_Steel", grp_y, bevel=0.01)
    mg_mount("%s_t%d" % (P, tur), base, (0, 0, 1), length, grp_p, twin=twin, cal=cal, ammo=not ball)
