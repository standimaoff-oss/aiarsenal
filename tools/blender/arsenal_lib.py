# ---- AI Arsenal shared Blender helpers (prepended after lib.py + alib.py) ----
# Conventions as alib: metres, Z up, nose -Y, +X = LEFT. Every model lives under a root empty named after the model id;
# all models of a family share one GLB and the game shows one root at a time.
import json

ARS_RECTS = json.load(open(os.path.join(TEXA, "ars_rects.json")))


def ars_common_mats():
    amat("M_Dark", None, 0.5, 0.4, factor=(0.10, 0.10, 0.11, 1))
    amat("M_Black", None, 0.7, 0.0, factor=(0.04, 0.04, 0.045, 1))
    amat("M_Metal", None, 0.35, 0.9, factor=(0.62, 0.62, 0.64, 1))
    amat("M_Steel", None, 0.45, 0.8, factor=(0.36, 0.37, 0.38, 1))
    amat("M_Prop", None, 0.55, 0.0, factor=(0.12, 0.12, 0.13, 1))
    amat("M_PropWood", None, 0.6, 0.0, factor=(0.42, 0.30, 0.18, 1))
    amat("M_Tire", "T_Tire", 0.9, 0.0)
    amat("M_Lens", None, 0.05, 0.4, factor=(0.03, 0.05, 0.08, 1))
    amat("M_Decal", "T_DecalsArs", 0.5, 0.0, alpha_clip=True, vcol=False)
    amat("M_NavRed", None, 0.3, 0.0, factor=(1.0, 0.08, 0.06, 1), emissive=(1.0, 0.08, 0.06, 1))
    amat("M_NavGreen", None, 0.3, 0.0, factor=(0.1, 1.0, 0.2, 1), emissive=(0.1, 1.0, 0.2, 1))
    amat("M_Strobe", None, 0.3, 0.0, factor=(1, 1, 1, 1), emissive=(1, 1, 1, 1))
    amat("M_Nozzle", "T_DarkMetal", 0.45, 0.8)
    amat("M_Copper", None, 0.35, 1.0, factor=(0.72, 0.42, 0.25, 1))
    amat("M_PCB", None, 0.5, 0.0, factor=(0.05, 0.25, 0.10, 1))
    amat("M_Carbon", None, 0.35, 0.2, factor=(0.06, 0.06, 0.065, 1))
    amat("M_Foam", None, 0.8, 0.0, factor=(0.55, 0.55, 0.52, 1))
    amat("M_Tape", None, 0.6, 0.0, factor=(0.15, 0.2, 0.45, 1))
    amat("M_Olive", None, 0.6, 0.1, factor=(0.24, 0.28, 0.18, 1))


def R(key):
    return ARS_RECTS[key]


def ctrl_prof(ctrl, L, y0):
    """ctrl: list of (t, zc, hw, ht, hb, n) with t = 0 nose .. 1 tail. Returns prof(y) for loft()."""
    cols = list(zip(*ctrl))
    ts = cols[0]
    tabs = [list(zip(ts, c)) for c in cols[1:]]
    def prof(y):
        t = (y - y0) / L
        return tuple(max(0.0, catmull(tb, t)) if i in (1, 2, 3) else catmull(tb, t) for i, tb in enumerate(tabs))
    return prof


def stations(y0, L, n=40, nose_bias=2.0):
    """Stations from y0 to y0+L, denser at both ends."""
    out = []
    for k in range(n + 1):
        u = k / n
        u = 0.5 - 0.5 * math.cos(u * PI)
        out.append(y0 + L * u)
    return out


def panel(name, root_le, tip_le, root_c, tip_c, ud, mats, grp, t_root=0.12, t_tip=0.10, n=4, cd=(0, 1, 0), cam=0.0,
          nc=12, cap_root=True, smooth=True):
    """Generic straight lifting surface from root LE to tip LE; chord direction cd, thickness direction ud."""
    span = (Vector(tip_le) - Vector(root_le)).normalized()
    ud = Vector(ud).normalized()
    if Vector(cd).cross(ud).dot(span) < 0:   # keep the airfoil loop winding consistent with the caps
        ud = -ud
    secs = []
    for i in range(n + 1):
        f = i / n
        le = Vector(root_le).lerp(Vector(tip_le), f)
        secs.append(dict(le=le, cd=cd, ud=ud, c=root_c + (tip_c - root_c) * f, t=t_root + (t_tip - t_root) * f, cam=cam))
    ob = surface(name, secs, mats, grp, nc=nc, cap_root=cap_root, cap_tip=True, smooth=smooth)
    fix_thin_normals(ob, [Vector(sc["le"]) + Vector(cd).normalized() * sc["c"] * 0.4 for sc in secs])
    return ob


def fix_thin_normals(ob, spine):
    """Majority vote: face normals must point away from the nearest spine point (mid-chord line of a thin surface)."""
    me = ob.data
    good = 0
    for p in me.polygons:
        c = p.center
        q = min(spine, key=lambda s: (s - c).length)
        good += 1 if p.normal.dot(c - q) > 0 else -1
    if good < 0:
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
        bm.to_mesh(me)
        bm.free()


def plane_normal(a, b, cd=(0, 1, 0)):
    """Normal of a surface spanning from a to b with chord along cd (for tilted fins)."""
    span = (Vector(b) - Vector(a)).normalized()
    return span.cross(Vector(cd)).normalized()


def prop_blades(name, center, r_tip, nblades, mats, grp, axis=(0, 1, 0), chord=0.12, r_hub=0.08, pitch_root=40, pitch_tip=14,
                phase=0.0, t=0.10, n=5):
    """Propeller blades around `axis` (unit, blade disc normal) at `center`."""
    ax = Vector(axis).normalized()
    ref = Vector((0, 0, 1)) if abs(ax.z) < 0.9 else Vector((1, 0, 0))
    e1 = ax.cross(ref).normalized()
    e2 = ax.cross(e1).normalized()
    obs = []
    for b in range(nblades):
        a = phase + 2 * PI * b / nblades
        er = e1 * math.cos(a) + e2 * math.sin(a)
        et = ax.cross(er).normalized()
        secs = []
        for i in range(n + 1):
            f = i / n
            r = r_hub + (r_tip - r_hub) * f
            beta = math.radians(pitch_root + (pitch_tip - pitch_root) * f)
            c = chord * (0.75 + 0.5 * math.sin(PI * min(1, f * 1.15))) * (0.6 if f > 0.95 else 1.0)
            cd = (et * math.cos(beta) + ax * math.sin(beta)).normalized()
            ud = er.cross(cd).normalized()
            le = Vector(center) + er * r - cd * (c * 0.35)
            secs.append(dict(le=le, cd=cd, ud=ud, c=c, t=t, cam=0.03))
        obs.append(surface("%s_b%d" % (name, b), secs, mats, grp, nc=8, cap_root=True, cap_tip=True))
    return obs


def spinner(name, center, r, length, mat, grp, direction=1):
    """Spinner cone along Y; direction +1 points to +Y (pusher), -1 to -Y (tractor)."""
    prof = [(0.0, r), (0.35 * length, r * 0.92), (0.7 * length, r * 0.6), (0.92 * length, r * 0.22), (length, 0.0)]
    if direction < 0:
        prof = [(-y, rr) for (y, rr) in prof][::-1]
    return lathe_y(name, prof, center, [mat], grp, seg=20)


def gen_weather(soot_pts=(), soot_r=0.8):
    """Generic weathering: darker undersides, slight noise, soot near engine points."""
    sp = [Vector(p) for p in soot_pts]
    def fn(w, n, ob):
        h = hash3(w * 3.1)
        k = 0.93 + 0.08 * h
        if n.z < -0.3:
            k *= 0.92
        for p in sp:
            d = (w - p).length
            if d < soot_r:
                k *= 0.55 + 0.45 * (d / soot_r)
        return k, k, k
    return fn


def decal_on(name, target, center, normal, up, w, key, grp, h=None, flip=False):
    rc = R(key)
    if h is None:
        aspect = (rc[3] - rc[1]) / max(1e-6, (rc[2] - rc[0]))
        h = w * aspect
    if flip:
        rc = [rc[2], rc[1], rc[0], rc[3]]
    return decal(name, target, center, normal, up, w, h, rc, "M_Decal", grp, nx=8, ny=4, offset=0.006)


def root(name):
    return empty(name, (0, 0, 0), 0.6, 'ARROWS')


def group_under(grp, joined_name, parent, origin=None):
    ob = join_grp(grp, joined_name)
    if ob is None:
        return None
    if origin is not None:
        set_origin(ob, origin)
    attach(ob, parent)
    return ob


def pivot(name, loc, parent):
    e = empty(name, loc, 0.25, 'PLAIN_AXES')
    attach(e, parent)
    return e


def lamp(name, loc, mat, grp, r=0.03):
    bm = bm_sphere(r, loc, 8, 4)
    return aobj(name, bm, mat, grp, smooth=True, dirt=False)
