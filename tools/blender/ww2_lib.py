# ---- WW2 armour helpers (r8). Prepended after armor_lib by tools/rwveh/run.sh.
# Conventions (as armor_lib): metres, Z up, FRONT = -Y, +X = vehicle LEFT. glTF = (x, z, -y).
import json

WW2_RECTS = json.load(open(os.path.join(TEXA, "ww2_rects.json")))


def ww2_mats():
    armor_mats()
    amat("M_Gelb", "T_WW2Gelb", 0.78, 0.04)
    amat("M_GelbPlain", "T_WW2GelbPlain", 0.78, 0.04)
    amat("M_Grau", "T_WW2Grau", 0.72, 0.08)
    amat("M_Zimm", "T_WW2Zimmerit", 0.88, 0.02)
    amat("M_Green4BO", "T_WW2Green", 0.74, 0.06)
    amat("M_TruckGreen", "T_WW2GreenTruck", 0.72, 0.05)
    amat("M_RatteCamo", "T_WW2Ratte", 0.78, 0.04)
    amat("M_TrackRust", None, 0.7, 0.55, factor=(0.25, 0.20, 0.16, 1))
    amat("M_Wood", None, 0.8, 0.0, factor=(0.46, 0.34, 0.21, 1))
    amat("M_Exhaust", None, 0.85, 0.3, factor=(0.34, 0.18, 0.10, 1))
    amat("M_DecalWW2", "T_DecalsWW2", 0.5, 0.0, alpha_clip=True, vcol=False)


def ww2_decal(name, center, normal, up, w, key, grp, h=None):
    """Flat decal from the WW2 atlas (cross, star, numbers, slogans) placed on a plate."""
    rc = WW2_RECTS[key]
    if h is None:
        h = w * (rc[3] - rc[1]) / max(1e-6, (rc[2] - rc[0]))
    return flat_decal(name, Vector(center), Vector(normal).normalized(), Vector(up).normalized(), w, h, rc, "M_DecalWW2", grp, offset=0.008)


# ------------------------------------------------------------------ outlines / lofts
def superellipse(hw, yf, yr, n=24, e=4.0, x0=0.0, y0=0.0):
    """Closed outline (x, y): half width hw, front extent yf (to -Y), rear extent yr (to +Y), squareness e (2 = ellipse,
    larger = boxier). CCW seen from above. Used for cast turrets / cupolas."""
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        c, s = math.cos(a), math.sin(a)
        x = hw * math.copysign(abs(c) ** (2.0 / e), c)
        ext = yr if s > 0 else yf
        y = ext * math.copysign(abs(s) ** (2.0 / e), s)
        pts.append((x0 + x, y0 + y))
    return pts


def turret_loft(name, levels, mat, grp, bevel=0.0, smooth=False, uvscale=0.5):
    """levels: [(z, outline[(x, y)])] bottom->top, equal point counts. Cast or welded turret shell."""
    rings = [[Vector((x, y, z)) for x, y in ol] for z, ol in levels]
    return loft_rings(name, rings, mat, grp, bevel=bevel, smooth=smooth, uvscale=uvscale)


def hull_loft(name, stations, mat, grp, bevel=0.0, smooth=False, uvscale=0.5):
    """stations: [(y, half[(x, z)])] front->rear; half runs from the bottom centre (x=0) around the LEFT side to the top
    centre (x=0). Mirrored to a closed ring. Sloped-plate hulls (T-34, IS-2 pike nose, KV)."""
    rings = []
    for y, half in stations:
        full = list(half) + [(-x, z) for x, z in reversed(half[1:-1])]
        rings.append([Vector((x, y, z)) for x, z in full])
    return loft_rings(name, rings, mat, grp, bevel=bevel, smooth=smooth, uvscale=uvscale)


# ------------------------------------------------------------------ running gear
def ww2_wheel(g, c, r, w, style, mat, detail=True, seg=18):
    """One road-wheel disc, spin axis X through c. Styles:
    'rubber' - pressed steel dish + rubber tyre (Tiger early, Pz.II F, T-34 rubber), 'steel' - steel-rimmed with
    internal rubber cushioning (Tiger late, KV/IS, T-34-85 'starfish' solid wheels), 'spoked' - cast spoked (T-34 1942)."""
    c = Vector(c)
    o = 1 if c.x > 0 else -1
    SEG = seg
    if style in ("rubber", "spoked_rubber"):
        lathe_y(g + "_tyre", [(-w / 2, r * 0.80), (-w / 2, r * 0.98), (w / 2, r * 0.98), (w / 2, r * 0.80)], tuple(c), ["M_Rubber"], g, seg=SEG, axis='X', uv_tile=0.5)
        lathe_y(g + "_disc", [(-w * 0.45, r * 0.81), (o * w * 0.10, r * 0.55), (o * w * 0.18, r * 0.28), (o * w * 0.18, 0.0), (-w * 0.45, 0.0)], tuple(c), [mat], g, seg=SEG, axis='X')
    else:
        lathe_y(g + "_rim", [(-w / 2, r * 0.86), (-w / 2, r), (w / 2, r), (w / 2, r * 0.86)], tuple(c), ["M_Steel"], g, seg=SEG, axis='X', uv_tile=0.5)
        lathe_y(g + "_cush", [(-w * 0.3, r * 0.74), (-w * 0.3, r * 0.86), (w * 0.3, r * 0.86), (w * 0.3, r * 0.74)], tuple(c), ["M_Rubber"], g, seg=SEG, axis='X', uv_tile=0.5)
        lathe_y(g + "_disc", [(-w * 0.4, r * 0.75), (o * w * 0.12, r * 0.5), (o * w * 0.2, r * 0.25), (o * w * 0.2, 0.0), (-w * 0.4, 0.0)], tuple(c), [mat], g, seg=SEG, axis='X')
    if style in ("spoked", "spoked_rubber") and detail:
        if True:
            for k in range(6):
                a = 2 * math.pi * k / 6
                acyl("%s_hole%d" % (g, k), r * 0.12, 0.012, (c.x + o * w * 0.17, c.y + math.cos(a) * r * 0.5, c.z + math.sin(a) * r * 0.5), "M_Bore", g, axis=0, seg=8, dirt=False)
    if not detail:
        return
    acyl(g + "_hub", r * 0.22, w * 0.6, (c.x + o * w * 0.35, c.y, c.z), mat, g, axis=0, seg=10)
    acyl(g + "_cap", r * 0.12, 0.05, (c.x + o * w * 0.68, c.y, c.z), "M_Dark", g, axis=0, seg=8)
    for k in range(8):   # wheel nuts on the hub flange
        a = 2 * math.pi * k / 8
        acyl("%s_nut%d" % (g, k), 0.018, 0.03, (c.x + o * w * 0.66, c.y + math.cos(a) * r * 0.17, c.z + math.sin(a) * r * 0.17), "M_Steel", g, axis=0, seg=6, dirt=False)


def ww2_gear(P, side, x, road, r_road, style, ww, spk, r_spk, idl, r_idl, rollers, r_rr, track_w, pitch, th,
             mat="M_Body", interleave=None, twin_gap=None, arm_grp=None, arm_len=0.45, teeth=14, link="ru",
             track_mat="M_TrackRust", hidden=False, wseg=18, spk_gap=0.09, idx0=0, belt=""):
    """Running gear of one side. road: [(y, z)] front->rear; interleave[i] = list of x offsets of the discs on axle i
    (Tiger/Maus 'Schachtellaufwerk'); twin_gap = twin wheel pairs (Pz.IV, KV); hidden = wheels covered by skirts (Maus,
    Ratte) -> discs only, no detail. Returns (wheel tuples for track_rig, links, pitch)."""
    sgn = "L" if side == "L" else "R"
    sx_out = 1 if x > 0 else -1
    W = []
    for i, (y, z) in enumerate(road):
        g = "%s_rw_%s%d" % (P, sgn, i + idx0)
        offs = interleave[i] if interleave else ([-(twin_gap + ww) / 2, (twin_gap + ww) / 2] if twin_gap else [0.0])
        omax = max(offs)
        for k, dx in enumerate(offs):
            cc = Vector((x + dx * sx_out, y, z))          # offsets are measured OUTWARDS from the track centre
            if hidden:
                lathe_y("%s_d%d" % (g, k), [(-ww / 2, r_road), (ww / 2, r_road), (ww / 2, r_road * 0.3), (-ww / 2, r_road * 0.3)], tuple(cc), [mat], g, seg=14, axis='X')
            else:
                ww2_wheel("%s_d%d" % (g, k), cc, r_road, ww, style, mat, detail=(dx == omax), seg=wseg)
        W.append(("rw_%s_%d" % (sgn, i + idx0), g, Vector((x, y, z))))
        if arm_grp:
            xi = x + (min(offs) - ww / 2) * sx_out
            rod("%s_arm_%s%d" % (P, sgn, i), Vector((xi, y, z)), Vector((xi - sx_out * arm_len, y + 0.3 * (1 if spk[0] < idl[0] else -1), z + 0.14)),
                r_road * 0.13, mat, arm_grp, seg=8)
    hw = (track_w - spk_gap) / 2 * 0.86
    g = "%s_spk%s_%s" % (P, belt, sgn)
    sprocket(g, (x, spk[0], spk[1]), r_spk, teeth, hw, spk_gap, mat)
    W.append(("spk%s_%s" % (belt, sgn), g, Vector((x, spk[0], spk[1]))))
    g = "%s_idl%s_%s" % (P, belt, sgn)
    idler(g, (x, idl[0], idl[1]), r_idl, hw, spk_gap, mat)
    W.append(("idl%s_%s" % (belt, sgn), g, Vector((x, idl[0], idl[1]))))
    for i, (y, z) in enumerate(rollers):
        g = "%s_rr%s_%s%d" % (P, belt, sgn, i)
        return_roller(g, (x, y, z), r_rr, track_w * 0.5, mat)
        W.append(("rr%s_%s_%d" % (belt, sgn, i), g, Vector((x, y, z))))
    circ = [(y, z, r_road) for (y, z) in road]
    if spk[0] > idl[0]:      # rear sprocket (Soviet)
        circ += [(spk[0], spk[1], r_spk * 0.9)] + [(y, z, r_rr) for (y, z) in rollers] + [(idl[0], idl[1], r_idl)]
    else:                    # front sprocket (German)
        circ += [(idl[0], idl[1], r_idl)] + [(y, z, r_rr) for (y, z) in rollers] + [(spk[0], spk[1], r_spk * 0.9)]
    # no return rollers (T-34 Christie, Tiger, Maus): the belt sags onto the tops of the big wheels
    if not rollers:
        circ = [(y, z, r_road) for (y, z) in road]
        if spk[0] > idl[0]:
            circ += [(spk[0], spk[1], r_spk * 0.9)] + [(y, z, r_road) for (y, z) in reversed(road[1:-1])] + [(idl[0], idl[1], r_idl)]
        else:
            circ += [(idl[0], idl[1], r_idl)] + [(y, z, r_road) for (y, z) in reversed(road[1:-1])] + [(spk[0], spk[1], r_spk * 0.9)]
    links, pitch, n = build_track(P + belt, sgn, x, circ, track_w, pitch, th, mat=track_mat, style=link)
    return W, links, pitch


# ------------------------------------------------------------------ small arms / fittings
def mg34(P, tag, base, d, grp, L=1.22):
    """MG 34: perforated barrel jacket + receiver + butt (hull ball / cupola AA mount). d = unit direction."""
    base, d = Vector(base), Vector(d).normalized()
    rod("%s_mg_%s_jk" % (P, tag), base, base + d * (L * 0.48), 0.024, "M_Dark", grp, seg=10)
    rod("%s_mg_%s_br" % (P, tag), base + d * (L * 0.48), base + d * (L * 0.62), 0.011, "M_Dark", grp, seg=8)
    acyl("%s_mg_%s_fh" % (P, tag), 0.02, 0.05, tuple(base + d * (L * 0.63)), "M_Dark", grp, seg=8,
         rot=(math.atan2(math.hypot(d.x, d.y), d.z), 0, math.atan2(-d.x, d.y) + math.pi))
    rod("%s_mg_%s_rc" % (P, tag), base - d * 0.32, base, 0.035, "M_Dark", grp, seg=8)
    rod("%s_mg_%s_bt" % (P, tag), base - d * 0.62, base - d * 0.32, 0.03, "M_Black", grp, seg=6)


def dt_mg(P, tag, base, d, grp):
    """DT tank MG (Soviet): barrel + flat 63-round pan drum on top + telescopic stock."""
    base, d = Vector(base), Vector(d).normalized()
    rod("%s_dt_%s_br" % (P, tag), base, base + d * 0.62, 0.016, "M_Dark", grp, seg=8)
    rod("%s_dt_%s_rc" % (P, tag), base - d * 0.4, base, 0.04, "M_Dark", grp, seg=8)
    acyl("%s_dt_%s_pan" % (P, tag), 0.12, 0.05, tuple(base - d * 0.2 + Vector((0, 0, 0.07))), "M_Dark", grp, seg=16)
    rod("%s_dt_%s_st" % (P, tag), base - d * 0.75, base - d * 0.4, 0.014, "M_Steel", grp, seg=6)


def ball_mount(P, tag, c, n, grp, r=0.13, mat="M_Body", soviet=False):
    """Hull/turret ball mount with the MG poking out along the plate normal n."""
    c, n = Vector(c), Vector(n).normalized()
    lathe_y("%s_ball_%s" % (P, tag), [(0.0, r * 1.25), (0.02, r * 1.25), (0.05, r * 1.0), (0.11, r * 0.55), (0.12, 0.02)], (0, 0, 0), [mat], grp, seg=14)
    ob = bpy.data.objects["%s_ball_%s" % (P, tag)]
    ob.location = c
    ob.rotation_euler = n.to_track_quat('Y', 'Z').to_euler()
    if soviet:
        rod("%s_ballmg_%s" % (P, tag), c, c + n * 0.42, 0.017, "M_Dark", grp, seg=8)
        acyl("%s_ballmgh_%s" % (P, tag), 0.035, 0.12, tuple(c + n * 0.12), "M_Dark", grp, seg=10, rot=n.to_track_quat('Z', 'Y').to_euler())
    else:
        rod("%s_ballmg_%s" % (P, tag), c, c + n * 0.34, 0.024, "M_Dark", grp, seg=10)
        rod("%s_ballmgb_%s" % (P, tag), c + n * 0.34, c + n * 0.46, 0.011, "M_Dark", grp, seg=8)


def headlamp(P, tag, c, grp, mat="M_Body", notek=False):
    c = Vector(c)
    if notek:   # Notek blackout light: slit housing
        abox("%s_notek_%s" % (P, tag), (0.12, 0.1, 0.08), tuple(c), mat, grp, bevel=0.01)
        abox("%s_notekg_%s" % (P, tag), (0.08, 0.01, 0.012), tuple(c + Vector((0, -0.051, 0))), "M_HeadLamp", grp, bevel=0.0, dirt=False)
        return
    lathe_y("%s_lamp_%s" % (P, tag), [(-0.09, 0.0), (-0.09, 0.085), (-0.05, 0.1), (0.06, 0.08), (0.07, 0.0)], tuple(c), [mat], grp, seg=14)
    acyl("%s_lampg_%s" % (P, tag), 0.082, 0.01, tuple(c + Vector((0, -0.093, 0))), "M_HeadLamp", grp, axis=1, seg=14, dirt=False)
    rod("%s_lampb_%s" % (P, tag), c + Vector((0, 0.02, -0.08)), c + Vector((0, 0.02, -0.2)), 0.015, mat, grp, seg=6)


def cupola_de(P, tag, c, r, grp, mat="M_Body", blocks=7, h=0.24, aa_mg=False):
    """German commander's cupola (cast drum with vision blocks + split / swivel hatch)."""
    c = Vector(c)
    lathe_y("%s_cup_%s" % (P, tag), [(0.0, r + 0.04), (0.04, r + 0.04), (h * 0.55, r), (h, r * 0.92), (h, 0.0), (0.0, 0.0)], tuple(c), [mat], grp, seg=24, axis='Z')
    for k in range(blocks):
        a = 2 * math.pi * k / blocks
        abox("%s_cupv_%s%d" % (P, tag, k), (0.15, 0.03, 0.07), tuple(c + Vector((math.sin(a) * (r + 0.005), -math.cos(a) * (r + 0.005), h * 0.6))),
             "M_Lens", grp, rot=(0, 0, a), bevel=0.0, dirt=False)
    acyl("%s_cuph_%s" % (P, tag), r * 0.85, 0.05, tuple(c + Vector((0, 0, h + 0.03))), mat, grp, seg=20)
    acyl("%s_cuphh_%s" % (P, tag), 0.03, r * 0.9, tuple(c + Vector((0, r * 0.6, h + 0.06))), "M_Steel", grp, axis=0, seg=8)
    if aa_mg:
        acyl("%s_cupring_%s" % (P, tag), r + 0.07, 0.025, tuple(c + Vector((0, 0, h * 0.85))), "M_Steel", grp, seg=24)
        mg34(P, "aa" + tag, c + Vector((r * 0.5, -r - 0.1, h + 0.18)), (0, -1, 0.15), grp)


def cupola_ru(P, tag, c, r, grp, mat="M_Body", h=0.26):
    """Soviet MK-4 / cast commander cupola with 5 vision slits and a two-part hatch."""
    c = Vector(c)
    lathe_y("%s_rcup_%s" % (P, tag), [(0.0, r + 0.03), (h * 0.7, r), (h, r * 0.8), (h, 0.0), (0.0, 0.0)], tuple(c), [mat], grp, seg=22, axis='Z')
    for k in range(5):
        a = math.radians(-120 + k * 60)
        abox("%s_rcupv_%s%d" % (P, tag, k), (0.12, 0.025, 0.035), tuple(c + Vector((math.sin(a) * r, -math.cos(a) * r, h * 0.55))), "M_Bore", grp,
             rot=(0, 0, a), bevel=0.0, dirt=False)
    for sx in (-1, 1):
        abox("%s_rcuph_%s%d" % (P, tag, sx), (r * 0.9, r * 1.6, 0.04), tuple(c + Vector((sx * r * 0.46, 0, h + 0.02))), mat, grp, bevel=0.01)
    periscope(P, "mk4" + tag, c + Vector((0, -r * 0.3, h + 0.1)), grp)


def spare_links(P, tag, c, n, step, normal, grp, w=0.5, pitch=0.15, mat="M_TrackRust"):
    """Row of n spare track links stowed on a plate (glacis, turret side). step = Vector between links."""
    c, step, normal = Vector(c), Vector(step), Vector(normal).normalized()
    rot = normal.to_track_quat('Z', 'Y').to_euler()
    for k in range(n):
        p = c + step * (k - (n - 1) / 2)
        abox("%s_spl_%s%d" % (P, tag, k), (w, pitch * 0.9, 0.05), tuple(p + normal * 0.03), mat, grp, rot=rot, bevel=0.0)
        abox("%s_splh_%s%d" % (P, tag, k), (0.05, pitch * 0.5, 0.06), tuple(p + normal * 0.08), mat, grp, rot=rot, bevel=0.0)
    rod("%s_splbar_%s" % (P, tag), c - step * (n / 2) + normal * 0.07, c + step * (n / 2) + normal * 0.07, 0.012, "M_Steel", grp, seg=6)


def fuel_drum(P, tag, c, r, L, grp, axis=1, mat="M_Body"):
    """External cylindrical fuel tank (T-34 / IS-2 hull-side drums) with straps."""
    c = Vector(c)
    acyl("%s_drum_%s" % (P, tag), r, L, tuple(c), mat, grp, axis=axis, seg=16)
    for f in (-0.3, 0.3):
        off = Vector((0, 0, 0))
        off[axis] = f * L
        acyl("%s_drums_%s%d" % (P, tag, int(f * 10)), r + 0.012, 0.035, tuple(c + off), "M_Steel", grp, axis=axis, seg=16, dirt=False)
    off = Vector((0, 0, 0))
    off[axis] = -L / 2 - 0.02
    acyl("%s_drumc_%s" % (P, tag), r * 0.25, 0.04, tuple(c + off + Vector((0, 0, r * 0.4))), "M_Steel", grp, axis=axis, seg=8)


def tools_set(P, tag, c, grp, along=(0, 1, 0)):
    """Pioneer tools on a fender / hull side: shovel, axe, crowbar, jack block."""
    c, a = Vector(c), Vector(along).normalized()
    rod("%s_shov_%s" % (P, tag), c - a * 0.55, c + a * 0.35, 0.016, "M_Wood", grp, seg=6)
    abox("%s_shovb_%s" % (P, tag), (0.2, 0.28, 0.015), tuple(c + a * 0.5), "M_Steel", grp, rot=(0, 0, math.atan2(-a.x, a.y)), bevel=0.0)
    off = Vector((a.y, -a.x, 0)) * 0.1
    rod("%s_axe_%s" % (P, tag), c + off - a * 0.4, c + off + a * 0.3, 0.015, "M_Wood", grp, seg=6)
    abox("%s_axeh_%s" % (P, tag), (0.04, 0.14, 0.16), tuple(c + off + a * 0.33), "M_Steel", grp, rot=(0, 0, math.atan2(-a.x, a.y)), bevel=0.0)
    rod("%s_crow_%s" % (P, tag), c - off - a * 0.7, c - off + a * 0.7, 0.012, "M_Steel", grp, seg=6)
    for e in (-0.45, 0.25):
        abox("%s_clamp_%s%d" % (P, tag, int(e * 100)), (0.28, 0.04, 0.04), tuple(c + a * e), "M_Steel", grp, rot=(0, 0, math.atan2(-a.x, a.y)), bevel=0.0)


def exhaust_box(P, tag, c, grp, r=0.11, L=0.55, axis=0, mat="M_Exhaust"):
    c = Vector(c)
    acyl("%s_exh_%s" % (P, tag), r, L, tuple(c), mat, grp, axis=axis, seg=14)
    acyl("%s_exhp_%s" % (P, tag), r * 0.35, 0.18, tuple(c + Vector((0, 0.0, r + 0.05))), mat, grp, seg=10)


def grille(P, tag, c, w, d, grp, n=8, mat="M_Body"):
    """Engine deck louvre grille: frame + n slats along X."""
    c = Vector(c)
    abox("%s_grf_%s" % (P, tag), (w, d, 0.03), tuple(c), mat, grp, bevel=0.0)
    for k in range(n):
        abox("%s_grs_%s%d" % (P, tag, k), (w * 0.92, 0.02, 0.05), tuple(c + Vector((0, -d / 2 + (k + 0.5) * d / n, 0.03))), "M_Mesh", grp, rot=(0.5, 0, 0), bevel=0.0)


def lift_hooks(P, tag, pts, grp, mat="M_Steel"):
    for k, p in enumerate(pts):
        lathe_y("%s_lhk_%s%d" % (P, tag, k), [(-0.025, 0.05), (-0.025, 0.08), (0.025, 0.08), (0.025, 0.05)], tuple(Vector(p) + Vector((0, 0, 0.06))), [mat], grp, seg=10, axis='X')


def rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, out_glb, out_blend, scene, extra=None):
    """Common hierarchy + export: root > hull geo, wheels, links, launcher_yaw > launcher_pitch > barrel."""
    rt = root("root")
    group_under(G, P + "_hull_geo", rt)
    track_rig(rt, P, "", WHEELS)
    for o in LINKS:
        attach(o, rt)
    ty = pivot("launcher_yaw", tuple(TC), rt)
    group_under(GT, "launcher_yaw_geo", ty)
    gp = pivot("launcher_pitch", tuple(TRN), ty)
    group_under(GG, "launcher_pitch_geo", gp)
    br = pivot("barrel", tuple(TRN), gp)
    group_under(GB, "barrel_geo", br)
    if extra:
        extra(rt, ty, gp, br)
    rw_export(out_glb, out_blend, scene)


def brake_de(P, g, y_back, z, r, L, x=0.0, mat="M_Body"):
    """German double-baffle muzzle brake (KwK 36 / KwK 40 / D-25T copied it): front at y_back - L.
    Two chambers with side ports, front and rear faces flat."""
    yb = y_back
    prof = [(0.0, r * 0.55), (0.0, r), (L * 0.42, r), (L * 0.42, r * 0.62), (L * 0.52, r * 0.62), (L * 0.52, r),
            (L, r), (L, r * 0.35)]
    lathe_y(g + "_brk", [(-p0, rr) for p0, rr in prof], (x, yb, z), [mat], g, seg=18)
    for k, (a, b) in enumerate(((0.06, 0.38), (0.58, 0.94))):
        for sx in (-1, 1):
            abox("%s_brkp%d_%d" % (g, k, sx), (0.012, L * (b - a) * 0.9, r * 1.1), (x + sx * (r - 0.004), yb - L * (a + b) / 2, z), "M_Bore", g, bevel=0.0, dirt=False)
    acyl(g + "_brkb", r * 0.32, 0.01, (x, yb - L - 0.004, z), "M_Bore", g, axis=1, seg=12, dirt=False)


def gun_simple(P, g, y0, y1, z, r_muzzle, r_breech, x=0.0, mat="M_Body", collar=True, steps=None):
    """WW2 gun barrel (tapered tube with optional step rings) from y0 (breech end at the mantlet) to y1 (muzzle).
    steps: list of (fraction, radius) for stepped barrels (KwK 36 has two shoulders)."""
    prof = [(0.0, r_breech)]
    if steps:
        for f, rr in steps:
            prof += [(f * (y1 - y0), prof[-1][1]), (f * (y1 - y0) + 0.01 * (1 if y1 > y0 else -1), rr)]
    prof += [(y1 - y0, r_muzzle)]
    lathe_y(g + "_tube", prof, (x, y0, z), [mat], g, seg=16)
    acyl(g + "_bore", r_muzzle * 0.62, 0.01, (x, y1 - 0.004 * (1 if y1 > y0 else -1), z), "M_Bore", g, axis=1, seg=12, dirt=False)
    if collar:
        acyl(g + "_col", r_breech * 1.25, 0.12, (x, y0 - 0.06 * (1 if y1 < y0 else -1), z), mat, g, axis=1, seg=16)


def chull(name, pts, mat, grp, bevel=0.02, uvscale=0.5, dirt=True):
    """Convex hull of a point cloud -> faceted armour body (sloped glacis, cast turret, casemate). Coplanar faces are
    merged so every armour plate is one flat n-gon, then edges are bevelled (welded / rolled plate edges)."""
    bm = bmesh.new()
    vs = [bm.verts.new(Vector(p)) for p in pts]
    r = bmesh.ops.convex_hull(bm, input=vs)
    dead = list({g for g in r["geom_interior"] + r["geom_unused"] if isinstance(g, bmesh.types.BMVert)})
    if dead:
        bmesh.ops.delete(bm, geom=dead, context='VERTS')
    bmesh.ops.dissolve_limit(bm, angle_limit=math.radians(1.0), verts=list(bm.verts), edges=list(bm.edges))
    bm.normal_update()
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    if bevel > 0:
        bmesh.ops.bevel(bm, geom=list(bm.edges), offset=bevel, segments=1, affect='EDGES', profile=0.5)
    bmesh.ops.triangulate(bm, faces=[f for f in bm.faces if len(f.verts) > 4])
    return aobj(name, bm, [mat], grp, smooth=False, uv="box", uvscale=uvscale, dirt=dirt)


def box_pts(x0, x1, y0, y1, z0, z1):
    return [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]


def sym(pts):
    """mirror a list of (x, y, z) points to both sides (x -> +-x)."""
    out = []
    for x, y, z in pts:
        out.append((x, y, z))
        out.append((-x, y, z))
    return out


def ring_pts(cx, cy, z, hw, yf, yr, e=2.3, n=20):
    return [(x, y, z) for x, y in superellipse(hw, yf, yr, n=n, e=e, x0=cx, y0=cy)]
