# Shared helpers for the reply-6 craft models (helicopters, boats, submarine). exec()'d from the model scripts
# after run.sh has concatenated lib/alib/arsenal_lib/rwveh_lib/armor_lib.
# Kind frame (CraftKind.java): +X left, +Y up, +Z forward.  Blender: +X left, -Y forward, +Z up.

def K(x, y, z):
    """CraftKind / glTF point -> Blender point."""
    return Vector((x, -z, y))


def craft_mats():
    armor_mats()
    amat("M_Blur", None, 0.9, 0.0, factor=(0.05, 0.05, 0.05, 1), glass_alpha=0.18)
    amat("M_Glass", None, 0.05, 0.3, factor=(0.12, 0.16, 0.18, 1), glass_alpha=0.35)
    amat("M_Blade", None, 0.6, 0.1, factor=(0.10, 0.10, 0.10, 1))
    amat("M_BladeTip", None, 0.5, 0.0, factor=(0.85, 0.75, 0.1, 1))
    amat("M_Rubber2", None, 0.9, 0.0, factor=(0.06, 0.06, 0.06, 1))
    amat("M_Rescue", None, 0.6, 0.0, factor=(0.85, 0.35, 0.05, 1))
    amat("M_White", None, 0.6, 0.0, factor=(0.85, 0.85, 0.82, 1))
    amat("M_Red", None, 0.6, 0.0, factor=(0.55, 0.08, 0.06, 1))


def rotor_blade(name, hub, r0, r1, chord, ang_deg, grp, mats=("M_Blade",), pitch_deg=8.0, twist_deg=-8.0, droop=0.0,
                t=0.11, n=6, tip_sweep=0.0, nc=8, cw=False):
    """One rotor blade from radius r0 to r1 at azimuth ang (deg, about +Z), chord along the rotation direction."""
    a = math.radians(ang_deg)
    er = Vector((math.cos(a), math.sin(a), 0))
    et = Vector((-math.sin(a), math.cos(a), 0)) * (-1 if cw else 1)   # direction of travel
    up = Vector((0, 0, 1))
    secs = []
    for i in range(n + 1):
        f = i / n
        r = r0 + (r1 - r0) * f
        beta = math.radians(pitch_deg + twist_deg * f)
        cd = (-et * math.cos(beta) + up * -math.sin(beta)).normalized()     # LE leads, TE trails, nose-up pitch
        ud = er.cross(cd).normalized()
        if ud.z < 0:
            ud = -ud
        c = chord * (0.75 if f == 0 else 1.0) * (0.55 if (tip_sweep and f == 1.0) else 1.0)
        le = Vector(hub) + er * r + et * (c * 0.25) + up * (-droop * f * f)
        if tip_sweep and f == 1.0:
            le = le - et * tip_sweep
        secs.append(dict(le=le, cd=cd, ud=ud, c=c, t=t, cam=0.0))
    ob = surface(name, secs, list(mats), grp, nc=nc, cap_root=True, cap_tip=True)
    fix_thin_normals(ob, [Vector(s["le"]) + Vector(s["cd"]) * s["c"] * 0.4 for s in secs])
    return ob


def tube(name, p0, p1, r, mat, grp, seg=12, caps=True):
    return rod(name, Vector(p0), Vector(p1), r, mat, grp, seg=seg, caps=caps)


def pod(name, c, L, r, mat, grp, axis='Y', seg=16, nose=0.35, tail=0.2, open_front=0.0):
    """Streamlined pod along Blender Y, nose towards -Y. open_front: leave a rocket-pod face (flat) instead of a nose."""
    if open_front > 0:
        prof = [(-L / 2, 0.0), (-L / 2, r * 0.98), (-L / 2 + 0.06, r), (L / 2 - tail, r), (L / 2, r * 0.55), (L / 2, 0.0)]
    else:
        prof = [(-L / 2, 0.0), (-L / 2 + nose * 0.3, r * 0.55), (-L / 2 + nose, r), (L / 2 - tail, r), (L / 2, r * 0.6),
                (L / 2, 0.0)]
    return lathe_y(name, prof, tuple(c), [mat], grp, seg=seg)


def blur_disc(name, c, r, grp, r_in=0.5, seg=40):
    bm = bmesh.new()
    for dz, flip in ((0.0, False), (-0.01, True)):   # two sheets = double-sided
        outer, inner = [], []
        for k in range(seg):
            a = 2 * PI * k / seg
            outer.append(bm.verts.new((c[0] + r * math.cos(a), c[1] + r * math.sin(a), c[2] + dz)))
            inner.append(bm.verts.new((c[0] + r_in * math.cos(a), c[1] + r_in * math.sin(a), c[2] + dz)))
        for k in range(seg):
            j = (k + 1) % seg
            q = (inner[k], inner[j], outer[j], outer[k])
            bm.faces.new(tuple(reversed(q)) if flip else q)
    return aobj(name, bm, ["M_Blur"], grp, smooth=False, dirt=False)


def fuselage(name, y0, L, ctrl, mats, grp, n=36, seg=32, mat_fn=None, cap0=True, cap1=True):
    return loft(name, stations(y0, L, n), ctrl_prof(ctrl, L, y0), mats, grp, seg=seg, mat_fn=mat_fn, cap0=cap0, cap1=cap1)


def hull_section(y, L, beam, depth, deadrise, chine, sheer_z, flare=0.08, keel_z=0.0, bow_up=0.0, n=9):
    """Planing-hull cross-section (Blender frame, X left) as a closed loop: keel -> chine -> gunwale (left), mirrored.
    y in [y0, y0+L] (bow at y0). Returns list of Vectors."""
    hb = beam / 2
    pts_half = []
    kz = keel_z + bow_up
    cz = kz + math.tan(math.radians(deadrise)) * chine * hb
    pts_half.append((0.0, kz))
    pts_half.append((chine * hb * 0.5, kz + (cz - kz) * 0.5))
    pts_half.append((chine * hb, cz))
    pts_half.append((hb * (1 - flare * 0.5), cz + 0.06))
    pts_half.append((hb, sheer_z * 0.55 + cz * 0.45))
    pts_half.append((hb * (1 + flare * 0.2), sheer_z))
    full = [Vector((x, y, z)) for x, z in pts_half] + [Vector((-x, y, z)) for x, z in reversed(pts_half[1:])]
    return full
