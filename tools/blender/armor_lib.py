# armor_lib.py — tracked armour / artillery builders (appended after lib.py + alib.py + arsenal_lib.py + rwveh_lib.py).
# Conventions as rwveh_lib: Blender metres, Z up, vehicle front towards -Y, +X = vehicle LEFT, origin = ground centre.
# Track contract (GunVehicle.trackPose):
#   rw_L_i / rw_R_i  road wheels      spk_L / spk_R  drive sprocket     idl_L / idl_R  idler
#   rr_L_i / rr_R_i  return rollers   -> all spun about local X by (-s / r) where s = distance run by that track side.
#   clips track_L / track_R           -> one link pitch of belt travel; Java scrubs time = frac(s / pitch) * duration.
import bpy, bmesh, math
from mathutils import Vector, Matrix

TRACK_FRAMES = 10


# ------------------------------------------------------------------ belt geometry (side view, (y, z) plane)
def belt_path(circles):
    """circles: [(y, z, r)] in CCW order in the (y,z) plane (bottom run travels +y = towards the rear).
    Returns (segments, total) where a segment is ('L', p0, p1) or ('A', c, r, a0, da)."""
    n = len(circles)
    nrm = []
    for i in range(n):
        ya, za, ra = circles[i]
        yb, zb, rb = circles[(i + 1) % n]
        dy, dz = yb - ya, zb - za
        L = math.hypot(dy, dz)
        du, dv = dy / L, dz / L
        ru, rv = dv, -du                      # right-hand normal = outside of a CCW loop
        th = math.asin(max(-1.0, min(1.0, (ra - rb) / L)))
        nrm.append((math.cos(th) * ru + math.sin(th) * du, math.cos(th) * rv + math.sin(th) * dv))
    segs = []
    total = 0.0
    for i in range(n):
        y, z, r = circles[i]
        nin, nout = nrm[i - 1], nrm[i]
        a0 = math.atan2(nin[1], nin[0])
        a1 = math.atan2(nout[1], nout[0])
        da = (a1 - a0) % (2 * math.pi)
        if da > 2 * math.pi - 1e-4:
            da = 0.0
        elif da > 2 * math.pi - 1.2:      # r8: concave corner (belt sagging onto a road wheel) -> short clockwise arc
            da -= 2 * math.pi
        if abs(da) > 1e-6:
            segs.append(('A', (y, z), r, a0, da))
            total += r * abs(da)
        yb, zb, rb = circles[(i + 1) % n]
        p0 = (y + r * nout[0], z + r * nout[1])
        p1 = (yb + rb * nout[0], zb + rb * nout[1])
        segs.append(('L', p0, p1))
        total += math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    return segs, total


def belt_sample(segs, total, s):
    """-> (y, z, ang) at arc length s; ang = travel direction angle in the (y,z) plane."""
    s = s % total
    for sg in segs:
        if sg[0] == 'L':
            (y0, z0), (y1, z1) = sg[1], sg[2]
            L = math.hypot(y1 - y0, z1 - z0)
            if s <= L:
                f = s / max(L, 1e-9)
                return y0 + (y1 - y0) * f, z0 + (z1 - z0) * f, math.atan2(z1 - z0, y1 - y0)
            s -= L
        else:
            (cy, cz), r, a0, da = sg[1], sg[2], sg[3], sg[4]
            L = r * abs(da)
            sgn = 1.0 if da > 0 else -1.0
            if s <= L:
                a = a0 + sgn * s / r
                return cy + r * math.cos(a), cz + r * math.sin(a), a + sgn * math.pi / 2
            s -= L
    sg = segs[-1]
    return sg[2][0], sg[2][1], 0.0


# ------------------------------------------------------------------ track links (shared mesh, animated clip)
def link_mesh(name, width, pitch, th, style="ru"):
    """One track link, low-poly (r5 optimisation: 36..48 tris instead of 188; ~200 links per vehicle):
    shoe (outer face -Z), grouser bar / rubber pad, central guide horn (+Z inward; twin horns for the US T-158)."""
    bm = bmesh.new()
    merge_bm(bm, bm_box((width, pitch * 0.9, th), (0, 0, -th / 2), (0, 0, 0), 0.0, 1))
    if style == "us":
        for sx in (-1, 1):
            merge_bm(bm, bm_box((0.035, pitch * 0.5, th * 1.6), (sx * 0.045, 0, th * 0.75), (0, 0, 0), 0.0, 1))
    else:
        merge_bm(bm, bm_box((width * 0.96, pitch * 0.22, th * 0.45), (0, -pitch * 0.18, -th - th * 0.18), (0, 0, 0), 0.0, 1))
        merge_bm(bm, bm_box((0.05, pitch * 0.55, th * 1.9), (0, 0, th * 0.9), (0, 0, 0), 0.0, 1))
    return bm


def build_track(P, side, x, circles, width, pitch_nom, th, mat="M_Track", style="ru", pad_mat=None):
    """Creates N link objects (linked duplicates) along the belt + clip 'track_<side>' (one pitch of travel).
    Returns (link objects, pitch, N)."""
    segs, total = belt_path(circles)
    N = max(8, int(round(total / pitch_nom)))
    pitch = total / N
    bm = link_mesh(P + "_link_" + side, width, pitch, th, style)
    proto = aobj(P + "_link_" + side + "_000", bm, [mat], "TRK_" + side, smooth=False, uv="box", uvscale=1.0, dirt=True)
    if pad_mat:
        bmp = bm_box((width * 0.82, pitch * 0.6, th * 0.5), (0, 0.02, -th - th * 0.2), (0, 0, 0), 0.0, 1)
        pads = aobj(P + "_pad_tmp", bmp, [pad_mat], "TRK_" + side, smooth=False, uv="box", dirt=False)
        bpy.ops.object.select_all(action='DESELECT')
        pads.select_set(True); proto.select_set(True)
        bpy.context.view_layer.objects.active = proto
        bpy.ops.object.join()
    me = proto.data
    links = []
    for k in range(N):
        ob = proto if k == 0 else bpy.data.objects.new("%s_link_%s_%03d" % (P, side, k), me)
        if k:
            col_get("BUILD").objects.link(ob)
            ob["grp"] = "TRK_" + side
            ob["dirt"] = 1
        y0, z0, a0 = belt_sample(segs, total, k * pitch)
        y1, z1, a1 = belt_sample(segs, total, (k + 1) * pitch)
        a1 = a0 + ((a1 - a0 + math.pi) % (2 * math.pi) - math.pi)
        ob.location = (x, y0, z0)
        ob.rotation_euler = (a0, 0, 0)
        aclip("track_" + side, ob, "location", [(0, (x, y0, z0)), (TRACK_FRAMES, (x, y1, z1))])
        # second action on the same track name would split the clip -> key rotation into the same strip
        act = ob.animation_data.nla_tracks[-1].strips[-1].action
        ob.animation_data.action = act
        ob.rotation_euler = (a0, 0, 0); ob.keyframe_insert("rotation_euler", index=0, frame=0)
        ob.rotation_euler = (a1, 0, 0); ob.keyframe_insert("rotation_euler", index=0, frame=TRACK_FRAMES)
        _linear(act)
        ob.animation_data.action = None
        ob.location = (x, y0, z0)
        ob.rotation_euler = (a0, 0, 0)
        links.append(ob)
    return links, pitch, N


def _linear(act):
    try:
        fcs = act.layers[0].strips[0].channelbags[0].fcurves
    except Exception:
        fcs = getattr(act, "fcurves", [])
    for fc in fcs:
        for kp in fc.keyframe_points:
            kp.interpolation = 'LINEAR'


# ------------------------------------------------------------------ running gear
def road_wheel(g, c, r, hw, gap, style="ru", mat="M_Body"):
    """Twin road wheel, low-poly (r5: ~450 tris instead of 1420): two discs with rubber tyres either side of the
    guide-horn gap, spin axis X through c. Bolts are left to the texture; hub + cap stay as geometry."""
    c = Vector(c)
    SEG = 16
    for sx in (-1, 1):
        xc = c.x + sx * (gap / 2 + hw / 2)
        lathe_y("%s_tyre%d" % (g, sx), [(-hw / 2, r * 0.80), (-hw / 2, r * 0.97), (hw / 2, r * 0.97), (hw / 2, r * 0.80)],
                (xc, c.y, c.z), ["M_Rubber"], g, seg=SEG, axis='X', uv_tile=0.5)
        o = sx * hw / 2      # outer face of this half
        prof = [(-hw / 2, r * 0.80), (0.0, r * 0.45), (0.0, 0.0), (hw * 0.5, 0.0), (hw * 0.5, r * 0.45), (hw / 2, r * 0.80)]
        lathe_y("%s_disc%d" % (g, sx), prof, (xc, c.y, c.z), [mat], g, seg=SEG, axis='X')
        if sx * (1 if c.x > 0 else -1) > 0:   # outer half: hub + cap
            acyl("%s_hub%d" % (g, sx), r * 0.24, hw * 0.5, (xc + o * 0.6, c.y, c.z), "M_Steel" if style == "us" else mat, g, axis=0, seg=10)
            acyl("%s_cap%d" % (g, sx), r * 0.14, 0.05, (xc + o * 1.05, c.y, c.z), "M_Dark", g, axis=0, seg=8)


def sprocket(g, c, r, teeth, hw, gap, mat="M_Body"):
    c = Vector(c)
    for sx in (-1, 1):
        xc = c.x + sx * (gap / 2 + 0.03)
        lathe_y("%s_ring%d" % (g, sx), [(-0.03, r * 0.72), (-0.03, r * 0.86), (0.03, r * 0.86), (0.03, r * 0.72)], (xc, c.y, c.z), [mat], g, seg=teeth * 2, axis='X')
        for k in range(teeth):
            a = 2 * math.pi * (k + 0.5 * (sx > 0)) / teeth
            abox("%s_t%d_%d" % (g, sx, k), (0.06, r * 0.16, r * 0.2), (xc, c.y + math.cos(a) * r * 0.93, c.z + math.sin(a) * r * 0.93), "M_Steel", g, rot=(a + math.pi / 2, 0, 0), bevel=0.0)
        lathe_y("%s_disc%d" % (g, sx), [(-0.02, r * 0.74), (0.06, r * 0.25), (0.06, 0.0), (-0.02, 0.0)], (xc, c.y, c.z), [mat], g, seg=16, axis='X')
    o = 1 if c.x > 0 else -1
    acyl(g + "_hub", r * 0.32, hw + gap, (c.x + o * 0.06, c.y, c.z), mat, g, axis=0, seg=12)
    acyl(g + "_cap", r * 0.13, 0.06, (c.x + o * ((hw + gap) / 2 + 0.09), c.y, c.z), "M_Dark", g, axis=0, seg=8)


def idler(g, c, r, hw, gap, mat="M_Body"):
    c = Vector(c)
    for sx in (-1, 1):
        xc = c.x + sx * (gap / 2 + hw / 2)
        lathe_y("%s_rim%d" % (g, sx), [(-hw / 2, r * 0.82), (-hw / 2, r), (hw / 2, r), (hw / 2, r * 0.82)], (xc, c.y, c.z), ["M_Steel"], g, seg=16, axis='X')
        lathe_y("%s_web%d" % (g, sx), [(-hw / 3, r * 0.84), (0.0, r * 0.5), (0.0, 0.0), (hw / 3, 0.0), (hw / 3, r * 0.5), (0.0, r * 0.84)], (xc, c.y, c.z), [mat], g, seg=16, axis='X')
        for k in range(4):
            a = 2 * math.pi * k / 4
            acyl("%s_h%d_%d" % (g, sx, k), r * 0.12, hw * 0.7, (xc, c.y + math.cos(a) * r * 0.6, c.z + math.sin(a) * r * 0.6), "M_Bore", g, axis=0, seg=6, dirt=False)
    o = 1 if c.x > 0 else -1
    acyl(g + "_hub", r * 0.25, hw * 2 + gap + 0.04, (c.x, c.y, c.z), "M_Steel", g, axis=0, seg=10)
    acyl(g + "_crank", r * 0.18, 0.12, (c.x - o * (hw + gap / 2 + 0.06), c.y, c.z), mat, g, axis=0, seg=8)


def return_roller(g, c, r, w, mat="M_Body"):
    c = Vector(c)
    lathe_y(g + "_r", [(-w / 2, 0.0), (-w / 2, r), (w / 2, r), (w / 2, 0.0)], tuple(c), ["M_Rubber"], g, seg=10, axis='X')
    acyl(g + "_cap", r * 0.45, w + 0.03, tuple(c), mat, g, axis=0, seg=8)


def track_rig(rt, P, side, wheels):
    """wheels: list of (node_name, group, centre). Pivot + join each wheel group under rt."""
    for n, g, c in wheels:
        pv = pivot(n, tuple(c), rt)
        group_under(g, n + "_geo", pv)


def running_gear(P, side, x, road, r_road, spk, r_spk, idl, r_idl, rollers, r_rr, track_w, pitch, th,
                 style="ru", gap=0.09, teeth=12, mat="M_Body", pad_mat=None, arm_grp=None, arm_len=0.42):
    """Builds wheels + belt for one side. road: list of (y, z) of road-wheel centres front->rear; spk/idl: (y, z);
    rollers: list of (y, z) rear->front. Returns (wheel tuples for track_rig, links, pitch)."""
    sgn = "L" if side == "L" else "R"
    hw = (track_w - gap) / 2 * 0.86
    W = []
    for i, (y, z) in enumerate(road):
        g = "%s_rw_%s%d" % (P, sgn, i)
        road_wheel(g, (x, y, z), r_road, hw, gap, style, mat)
        W.append(("rw_%s_%d" % (sgn, i), g, Vector((x, y, z))))
        if arm_grp:   # torsion-bar swing arm: from the inner wheel face into the hull side (static, wheel spins on it)
            xi = x - (1 if x > 0 else -1) * (gap / 2 + hw)
            xo = x - (1 if x > 0 else -1) * (gap / 2 + hw + arm_len)
            rod("%s_arm_%s%d" % (P, sgn, i), Vector((xi + (0.02 if x > 0 else -0.02), y, z)), Vector((xo, y + 0.28 * (1 if spk[0] > idl[0] else -1), z + 0.12)), r_road * 0.16, mat, arm_grp, seg=8)
    g = "%s_spk_%s" % (P, sgn)
    sprocket(g, (x, spk[0], spk[1]), r_spk, teeth, hw, gap, mat)
    W.append(("spk_" + sgn, g, Vector((x, spk[0], spk[1]))))
    g = "%s_idl_%s" % (P, sgn)
    idler(g, (x, idl[0], idl[1]), r_idl, hw, gap, mat)
    W.append(("idl_" + sgn, g, Vector((x, idl[0], idl[1]))))
    for i, (y, z) in enumerate(rollers):
        g = "%s_rr_%s%d" % (P, sgn, i)
        return_roller(g, (x, y, z), r_rr, track_w * 0.55, mat)
        W.append(("rr_%s_%d" % (sgn, i), g, Vector((x, y, z))))
    # belt: CCW in (y,z): road wheels front->rear (bottom), sprocket/idler at the ends, rollers rear->front on top
    circ = [(y, z, r_road) for (y, z) in road]
    if spk[0] > idl[0]:   # sprocket at the rear (y larger)
        circ += [(spk[0], spk[1], r_spk * 0.9)] + [(y, z, r_rr) for (y, z) in rollers] + [(idl[0], idl[1], r_idl)]
    else:                 # front sprocket (BMP-3 has rear sprocket too; kept for completeness)
        circ += [(idl[0], idl[1], r_idl)] + [(y, z, r_rr) for (y, z) in rollers] + [(spk[0], spk[1], r_spk * 0.9)]
    links, pitch, n = build_track(P, sgn, x, circ, track_w, pitch, th, style=style, pad_mat=pad_mat)
    return W, links, pitch


# ------------------------------------------------------------------ shells / armour
def loft_rings(name, rings, mats, grp, cap0=True, cap1=True, bevel=0.0, smooth=False, uvscale=0.5, dirt=True):
    """rings: list of equal-length point lists (closed loops). Skins consecutive rings, caps the ends."""
    bm = bmesh.new()
    vs = [[bm.verts.new(tuple(p)) for p in r] for r in rings]
    n = len(rings[0])
    for a, b in zip(vs[:-1], vs[1:]):
        for i in range(n):
            j = (i + 1) % n
            try:
                bm.faces.new((a[i], a[j], b[j], b[i]))
            except ValueError:
                pass
    if cap0:
        bm.faces.new(list(reversed(vs[0])))
    if cap1:
        bm.faces.new(vs[-1])
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bm.normal_update()
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    if bevel > 0:
        bmesh.ops.bevel(bm, geom=list(bm.edges), offset=bevel, segments=1, affect='EDGES', profile=0.5)
    return aobj(name, bm, mats if isinstance(mats, list) else [mats], grp, smooth=smooth, uv="box", uvscale=uvscale, dirt=dirt)


def ring_xy(pts, z):
    return [Vector((x, y, z)) for x, y in pts]


def mirror_outline(half):
    """half: [(x>=0, y)] from front centre around the left side to rear centre -> full closed outline (CCW from above)."""
    full = list(half) + [(-x, y) for x, y in reversed(half) if abs(x) > 1e-6]
    return full


def era_row(P, tag, p0, p1, n, size, rot, grp, mat="M_Body", bolts=True):
    """Row of n ERA bricks between centres p0..p1 (Vectors) with a common rotation."""
    p0, p1 = Vector(p0), Vector(p1)
    for k in range(n):
        f = k / max(n - 1, 1)
        c = p0.lerp(p1, f)
        abox("%s_era_%s%d" % (P, tag, k), size, tuple(c), mat, grp, rot=rot, bevel=0.01)
        if bolts:
            R = bpy.data.objects["%s_era_%s%d" % (P, tag, k)].rotation_euler.to_matrix()
            up = R @ Vector((0, 0, size[2] / 2 + 0.006))
            for sx in (-1, 1):
                off = R @ Vector((sx * size[0] * 0.35, 0, 0))
                acyl("%s_erab_%s%d_%d" % (P, tag, k, sx), 0.012, 0.012, tuple(c + up + off), "M_Steel", grp, rot=bpy.data.objects["%s_era_%s%d" % (P, tag, k)].rotation_euler, seg=6, dirt=False)


def smoke_dischargers(P, tag, c, n, spread_deg, yaw0_deg, elev_deg, grp, r=0.045, L=0.22, mat="M_DarkGreen"):
    """Fan of smoke-grenade tubes (902A Tucha style) centred at c, pointing forward (-Y) with a yaw spread."""
    c = Vector(c)
    bases = []
    for k in range(n):
        yaw = math.radians(yaw0_deg + (k - (n - 1) / 2) * spread_deg)
        el = math.radians(elev_deg)
        d = Vector((math.sin(yaw) * math.cos(el), -math.cos(yaw) * math.cos(el), math.sin(el)))
        base = c + Vector((math.cos(yaw), math.sin(yaw), 0)) * ((k - (n - 1) / 2) * 0.11)
        rod("%s_smk_%s%d" % (P, tag, k), base, base + d * L, r, mat, grp, seg=10)
        acyl("%s_smkc_%s%d" % (P, tag, k), r * 0.75, 0.01, tuple(base + d * (L + 0.004)), "M_Bore", grp, rot=(math.pi / 2 - el, 0, -yaw), seg=10, dirt=False)
        bases.append(base)
    # r5: common mounting bar under the tube breeches -> the fan is one rigid unit that auto_attach seats on the turret
    if len(bases) > 1:
        rod("%s_smkbar_%s" % (P, tag), bases[0] - (bases[-1] - bases[0]).normalized() * r, bases[-1] + (bases[-1] - bases[0]).normalized() * r,
            r * 0.9, mat, grp, seg=8)


def gun_tube(P, g, y0, y1, z, r_muzzle, r_breech, mat="M_Body", sleeve=True, evac=None, brake=None, mrs=False, x=0.0):
    """Gun barrel along -Y from y0 (trunnion side) to y1 (muzzle), with thermal sleeve bands, fume evacuator
    (evac=(y, r, len)), muzzle brake (brake=('ru'|'d30'|'m777'|'2s19', len)), muzzle-reference sensor."""
    L = abs(y1 - y0)
    acyl(g + "_tube", r_breech, L, (x, (y0 + y1) / 2, z), mat, g, axis=1, seg=20, r2=r_muzzle)
    if sleeve:
        nb = 5
        for k in range(nb):
            yy = y0 - (k + 0.6) * L / (nb + 0.5)
            rr = r_breech + (r_muzzle - r_breech) * ((y0 - yy) / L)
            acyl("%s_band%d" % (g, k), rr + 0.012, 0.05, (x, yy, z), "M_Plain", g, axis=1, seg=20)
    if evac:
        ey, er, el = evac
        acyl(g + "_evac", er, el, (x, ey, z), mat, g, axis=1, seg=22)
        acyl(g + "_evac0", er * 0.9, 0.04, (x, ey + el / 2 + 0.02, z), mat, g, axis=1, seg=22, r2=r_breech * 1.1)
        acyl(g + "_evac1", er * 0.9, 0.04, (x, ey - el / 2 - 0.02, z), mat, g, axis=1, seg=22, r2=r_breech)
    if brake:
        kind, bl = brake
        yb = y1 - bl / 2
        if kind == "d30":    # multi-baffle (D-30 / 2A18)
            acyl(g + "_mb", r_muzzle * 1.9, bl, (x, yb, z), mat, g, axis=1, seg=20)
            for k in range(5):
                for sx in (-1, 1):
                    abox("%s_mbs%d_%d" % (g, k, sx), (0.012, bl * 0.11, r_muzzle * 2.2), (x + sx * r_muzzle * 1.9, yb + (k - 2) * bl * 0.18, z), "M_Bore", g, bevel=0.0, dirt=False)
        elif kind in ("m777", "2s19"):   # double-baffle
            acyl(g + "_mb", r_muzzle * 1.7, bl, (x, yb, z), mat, g, axis=1, seg=20)
            for k in (-1, 1):
                for sx in (-1, 1):
                    abox("%s_mbs%d_%d" % (g, k, sx), (0.014, bl * 0.28, r_muzzle * 2.0), (x + sx * r_muzzle * 1.7, yb + k * bl * 0.22, z), "M_Bore", g, bevel=0.0, dirt=False)
        acyl(g + "_bore", r_muzzle * 0.7, 0.01, (x, y1 - 0.002, z), "M_Bore", g, axis=1, seg=14, dirt=False)
    else:
        acyl(g + "_bore", r_muzzle * 0.72, 0.01, (x, y1 - 0.002, z), "M_Bore", g, axis=1, seg=14, dirt=False)
    if mrs:
        abox(g + "_mrs", (0.05, 0.06, 0.05), (x, y1 + 0.08, z + r_muzzle + 0.03), "M_Dark", g, bevel=0.005)


def hatch(P, tag, c, r, grp, mat="M_Body", open_deg=0.0):
    c = Vector(c)
    acyl("%s_hring_%s" % (P, tag), r + 0.05, 0.08, tuple(c), mat, grp, seg=20)
    acyl("%s_hlid_%s" % (P, tag), r, 0.05, tuple(c + Vector((0, 0, 0.06))), mat, grp, seg=20)
    rod("%s_hhdl_%s" % (P, tag), c + Vector((-r * 0.4, 0, 0.1)), c + Vector((r * 0.4, 0, 0.1)), 0.012, "M_Steel", grp, seg=6)
    acyl("%s_hhinge_%s" % (P, tag), 0.035, r * 0.8, tuple(c + Vector((0, r, 0.06))), "M_Steel", grp, axis=0, seg=8)


def periscope(P, tag, c, grp, yaw=0.0):
    c = Vector(c)
    abox("%s_peri_%s" % (P, tag), (0.16, 0.1, 0.09), tuple(c), "M_Dark", grp, rot=(0, 0, yaw), bevel=0.01)
    abox("%s_perig_%s" % (P, tag), (0.13, 0.01, 0.05), tuple(c + Matrix.Rotation(yaw, 3, 'Z') @ Vector((0, -0.051, 0.0))), "M_Lens", grp, rot=(0, 0, yaw), bevel=0.0, dirt=False)


def antenna(P, tag, base, h, grp):
    base = Vector(base)
    acyl("%s_antb_%s" % (P, tag), 0.04, 0.08, tuple(base), "M_Dark", grp, seg=10)
    rod("%s_ant_%s" % (P, tag), base, base + Vector((0, 0.15, h)), 0.007, "M_Black", grp, seg=5)


def tow_cable(P, tag, p0, p1, sag, grp, n=10):
    p0, p1 = Vector(p0), Vector(p1)
    pts = []
    for k in range(n + 1):
        f = k / n
        p = p0.lerp(p1, f)
        p.z -= sag * 4 * f * (1 - f)
        pts.append(p)
    for k in range(n):
        rod("%s_cab_%s%d" % (P, tag, k), pts[k], pts[k + 1], 0.016, "M_Steel", grp, seg=6, dirt=False)
    for p in (p0, p1):
        acyl("%s_cabe_%s%d" % (P, tag, int(p.y * 100)), 0.04, 0.12, tuple(p), "M_Steel", grp, axis=1, seg=8)


def armor_mats():
    rw_mats()
    amat("M_Track", None, 0.55, 0.6, factor=(0.20, 0.19, 0.17, 1))
    amat("M_Sand", "T_CamoSand", 0.75, 0.05, factor=(0.95, 0.88, 0.70, 1))
    amat("M_SandPlain", None, 0.75, 0.05, factor=(0.62, 0.53, 0.36, 1))
    amat("M_Olive", None, 0.72, 0.05, factor=(0.26, 0.29, 0.17, 1))
    amat("M_Lens", None, 0.05, 0.6, factor=(0.10, 0.25, 0.30, 1))
    amat("M_Brass", None, 0.3, 1.0, factor=(0.78, 0.6, 0.25, 1))
    amat("M_Mesh", None, 0.8, 0.3, factor=(0.12, 0.12, 0.11, 1))
    amat("M_Canvas", None, 0.95, 0.0, factor=(0.32, 0.31, 0.22, 1))


# ------------------------------------------------------------------ r5: attach floating parts (before group joins)
def _attach_pts(ob):
    me = ob.data
    mw = ob.matrix_world
    vs = [mw @ v.co for v in me.vertices]
    pts = list(vs)
    pts += [(vs[e.vertices[0]] + vs[e.vertices[1]]) / 2 for e in me.edges]
    pts += [mw @ p.center for p in me.polygons]
    return vs, pts


def auto_attach(groups, tol=0.012, move_max=0.4, small=0.9, bracket_groups=(), mat="M_Dark", iters=4, skip=r"_link_"):
    """Every part of a vehicle must touch the body. For each group (= one joined node later), parts are clustered by
    contact (within tol); the cluster with the most geometry is the body. Floating clusters that are small (max size
    < small) and close (< move_max) are moved flush onto the nearest body surface; large ones, far ones and clusters of
    bracket_groups (coaxial sleeves, muzzle brakes) get mounting brackets (rods) instead. Returns a report list."""
    import re as _re
    from mathutils.bvhtree import BVHTree
    rx = _re.compile(skip)
    report = []
    for grp in groups:
        for it in range(iters):
            objs = [o for o in col_get("BUILD").objects if o.type == 'MESH' and o.get("grp") == grp and not rx.search(o.name) and len(o.data.polygons)]
            if len(objs) < 2:
                break
            data = [_attach_pts(o) for o in objs]
            verts, faces, fobj = [], [], []
            for oi, o in enumerate(objs):
                base = len(verts)
                verts += data[oi][0]
                for p in o.data.polygons:
                    faces.append([base + i for i in p.vertices]); fobj.append(oi)
            tree = BVHTree.FromPolygons(verts, faces, all_triangles=False)
            par = list(range(len(objs)))

            def find(a):
                while par[a] != a:
                    par[a] = par[par[a]]; a = par[a]
                return a
            for oi in range(len(objs)):
                for p in data[oi][1]:
                    for co, n, fi, d in tree.find_nearest_range(p, tol):
                        o2 = fobj[fi]
                        if o2 != oi:
                            par[find(o2)] = find(oi)
            cl = {}
            for oi in range(len(objs)):
                cl.setdefault(find(oi), []).append(oi)
            main = max(cl.values(), key=lambda c: sum(len(objs[i].data.polygons) for i in c))
            mv, mf = [], []
            for oi in main:
                base = len(mv)
                mv += data[oi][0]
                for p in objs[oi].data.polygons:
                    mf.append([base + i for i in p.vertices])
            mtree = BVHTree.FromPolygons(mv, mf, all_triangles=False)
            changed = False
            for c in cl.values():
                if c is main:
                    continue
                pts = [p for oi in c for p in data[oi][1]]
                lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
                hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
                size = hi - lo
                best = None
                for p in pts:
                    q, n, fi, d = mtree.find_nearest(p)
                    if q is not None and (best is None or d < best[2]):
                        best = (p, q, d)
                if best is None or best[2] <= tol:
                    continue
                p, q, d = best
                names = ",".join(objs[i].name for i in c[:3]) + ("..." if len(c) > 3 else "")
                if grp not in bracket_groups and max(size) < small and d < move_max:
                    v = (q - p).normalized() * (d + 0.004)
                    for oi in c:
                        objs[oi].data.transform(Matrix.Translation(objs[oi].matrix_world.inverted().to_3x3() @ v))
                        objs[oi].data.update()
                    report.append("move %s %.3f m [%s]" % (grp, d, names))
                    changed = True
                elif d < 0.6:
                    # brackets: one per ~1 m along the cluster's longest axis
                    ax = max(range(3), key=lambda a: size[a])
                    nseg = max(1, int(size[ax] / 1.0 + 0.5))
                    r = max(0.012, min(0.04, 0.25 * max(0.02, min(size))))
                    for sgi in range(nseg):
                        a0 = lo[ax] + size[ax] * sgi / nseg
                        a1 = lo[ax] + size[ax] * (sgi + 1) / nseg
                        seg_best = None
                        for pp in pts:
                            if a0 - 1e-4 <= pp[ax] <= a1 + 1e-4:
                                qq, n, fi, dd = mtree.find_nearest(pp)
                                if qq is not None and (seg_best is None or dd < seg_best[2]):
                                    seg_best = (pp, qq, dd)
                        if seg_best and tol < seg_best[2] < 0.6:
                            pp, qq, dd = seg_best
                            dv = (qq - pp).normalized()
                            rod("%s_brk_%d" % (grp, len(report)), pp - dv * 0.01, qq + dv * 0.01, r, mat, grp, seg=6)
                    report.append("bracket %s %.3f m x%d [%s]" % (grp, d, nseg, names))
                    changed = True
                else:
                    report.append("FAR %s %.3f m [%s] — not fixed" % (grp, d, names))
            if not changed:
                break
    for r in report:
        print("AUTO_ATTACH", r)
    return report


# r5 optimisation: bevels on parts smaller than 12 cm are invisible in game but cost ~32 extra tris per box
_abox_full = abox


def abox(name, size, loc, mat, grp, rot=(0, 0, 0), bevel=0.01, **kw):
    if min(size) < 0.12:
        bevel = 0.0
    return _abox_full(name, size, loc, mat, grp, rot=rot, bevel=bevel, **kw)
