"""r15: wearable RF / US uniforms by rank, modelled in Blender (boxes + bevelled shells sized to the Minecraft player).

  $B -b --factory-startup --python tools/blender/uniforms.py [-- preview] [-- icons]

Frame: Blender Z up, the wearer faces -Y, wearer's right = -X; feet at z = 0, 1 unit = 1 block (head 1.5..2.0,
body 0.75..1.5, arms x 0.25..0.5, legs x 0..0.25). Every object keeps an identity transform (vertices in absolute
player coordinates) because the Java layer draws node geometry directly after the bone transform (see
client/render/UniformLayer: pivot head/body (0,1.5,0), arms (+-0.3125,1.375,0), legs/boots (+-0.119,0.75,0)).

Node names: "<set>.<piece>[.<detail>]", piece in head/body/arm_r/arm_l/leg_r/leg_l/boot_r/boot_l.
Sets: ru_private ru_sergeant ru_captain ru_colonel ru_general us_private us_sergeant us_captain us_colonel us_general.
Textures: models/uniforms_tex (tools/blender/uniform_textures.py), packed into the .blend and the .glb.
"""
import bpy, bmesh, math, os, sys
from mathutils import Matrix, Vector

ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
TEX = "/home/user/models/uniforms_tex"
BLEND = "/home/user/models/uniforms.blend"
GLB = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb/uniforms.glb"
ICONS = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/item"

bpy.ops.wm.read_factory_settings(use_empty=True)
scn = bpy.context.scene

# ------------------------------------------------------------------ materials
MATS = {}


def mat(tex, rough=0.85):
    if tex in MATS:
        return MATS[tex]
    m = bpy.data.materials.new("u_" + tex.replace(".png", ""))
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes.get("Principled BSDF")
    img = bpy.data.images.load(os.path.join(TEX, tex))
    img.pack()
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = img
    t.interpolation = "Closest"
    nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = rough
    MATS[tex] = m
    return m


# palette swatch indices (tools/blender/uniform_textures.py palette())
GOLD, GOLD_L, RED, SILVER, BLACK, DARK, OLIVE_D, TAN_D = 0, 1, 2, 3, 4, 5, 6, 7
KHAKI, CREAM, BROWN_D, BROWN, NAVY, WHITE, AGSU_D, TAUPE = 8, 9, 10, 11, 12, 13, 14, 15
OLIVE, OLIVE_L, BROWN_X, RUBBER, RED_L, OLIVE_M, TAN_L, MAROON = 16, 17, 18, 19, 20, 21, 22, 23


def pal_uv(i):
    return ((i % 8) * 8 + 4) / 64.0, 1.0 - ((i // 8) * 8 + 4) / 64.0


# ------------------------------------------------------------------ mesh helpers
FACES = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (2, 3, 7, 6), (3, 0, 4, 7), (1, 2, 6, 5)]


def bm_box(bm, b):
    x0, x1, y0, y1, z0, z1 = b
    vs = [bm.verts.new(v) for v in [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                                    (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]]
    return [bm.faces.new([vs[i] for i in f]) for f in FACES], vs


def bm_prism(bm, poly_xz, y0, y1):
    """Polygon in the XZ plane (counter-clockwise seen from -Y) extruded from y0 (front) to y1."""
    n = len(poly_xz)
    fr = [bm.verts.new((x, y0, z)) for x, z in poly_xz]
    bk = [bm.verts.new((x, y1, z)) for x, z in poly_xz]
    fs = [bm.faces.new(fr), bm.faces.new(bk[::-1])]
    for i in range(n):
        j = (i + 1) % n
        fs.append(bm.faces.new([fr[i], bk[i], bk[j], fr[j]]))
    return fs


def make(name, build, tex=None, pal=None, uv="tile", scale=1.3, show=None, flip=False, xform=None, bevel=None):
    """build(bm) adds geometry. Material: texture file (tile / fit UVs) or a palette swatch."""
    bm = bmesh.new()
    build(bm)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    if bevel:
        edges = [e for e in bm.edges if bevel[1](e)]
        bmesh.ops.bevel(bm, geom=edges, offset=bevel[0], segments=bevel[2], affect="EDGES", profile=0.5)
    if xform is not None:
        bmesh.ops.transform(bm, matrix=xform, verts=bm.verts)
    bm.normal_update()
    uvl = bm.loops.layers.uv.new("UVMap")
    if pal is not None:
        u, v = pal_uv(pal)
        for f in bm.faces:
            for l in f.loops:
                l[uvl].uv = (u, v)
        m = mat("palette.png", 0.6)
    else:
        m = mat(tex)
        if uv == "tile":
            for f in bm.faces:
                n = f.normal
                ax = max(range(3), key=lambda i: abs(n[i]))
                for l in f.loops:
                    p = l.vert.co
                    if ax == 0:
                        uu, vv = p.y * (1 if n.x > 0 else -1), p.z
                    elif ax == 1:
                        uu, vv = p.x * (-1 if n.y > 0 else 1), p.z
                    else:
                        uu, vv = p.x, p.y
                    l[uvl].uv = (uu * scale + 0.13, vv * scale + 0.37)
        else:                                     # fit: the 'show' face gets the whole image, other faces its border
            ax, sg = {"+Z": (2, 1), "-Y": (1, -1), "+X": (0, 1), "-X": (0, -1), "-Z": (2, -1)}[show]
            sel = [f for f in bm.faces if f.normal[ax] * sg > 0.7]
            pts = [l.vert.co for f in sel for l in f.loops]

            def rng(i):
                vals = [p[i] for p in pts]
                return min(vals), max(vals)
            if ax == 2:
                (ua, ub), (va, vb) = rng(0), rng(1)
                gu = (lambda p: p.x) if not flip else (lambda p: -p.x)
                if flip:
                    ua, ub = -ub, -ua
                gv = lambda p: p.y
            elif ax == 1:
                (ua, ub), (va, vb) = rng(0), rng(2)
                gu, gv = (lambda p: p.x), (lambda p: p.z)
            else:
                (ua, ub), (va, vb) = rng(1), rng(2)
                if sg > 0:
                    gu = lambda p: p.y
                else:
                    ua, ub = -ub, -ua
                    gu = lambda p: -p.y
                gv = lambda p: p.z
            du, dv = max(ub - ua, 1e-6), max(vb - va, 1e-6)
            for f in bm.faces:
                inside = f in sel
                for l in f.loops:
                    if inside:
                        l[uvl].uv = ((gu(l.vert.co) - ua) / du, (gv(l.vert.co) - va) / dv)
                    else:
                        l[uvl].uv = (0.02, 0.5)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(m)
    ob = bpy.data.objects.new(name, me)
    scn.collection.objects.link(ob)
    return ob


def box(name, b, **kw):
    return make(name, lambda bm: bm_box(bm, b), **kw)


def boxes(name, bl, **kw):
    def f(bm):
        for b in bl:
            bm_box(bm, b)
    return make(name, f, **kw)


def mirror_x(b):
    x0, x1, y0, y1, z0, z1 = b
    return (-x1, -x0, y0, y1, z0, z1)


def rot_x(deg, pivot):
    return Matrix.Translation(pivot) @ Matrix.Rotation(math.radians(deg), 4, "X") @ Matrix.Translation(-Vector(pivot))


# ------------------------------------------------------------------ uniform definitions
SETS = {
    "ru_private":  dict(army="ru", field=True, head="helmet", rank="ru_private"),
    "ru_sergeant": dict(army="ru", field=True, head="helmet", rank="ru_sergeant"),
    "ru_captain":  dict(army="ru", field=True, head="fieldcap", rank="ru_captain"),
    "ru_colonel":  dict(army="ru", field=False, head="service", rank="ru_colonel"),
    "ru_general":  dict(army="ru", field=False, head="service", rank="ru_general", lampas=True),
    "us_private":  dict(army="us", field=True, head="helmet", rank="private"),
    "us_sergeant": dict(army="us", field=True, head="helmet", rank="sergeant"),
    "us_captain":  dict(army="us", field=True, head="fieldcap", rank="captain"),
    "us_colonel":  dict(army="us", field=False, head="service", rank="colonel"),
    "us_general":  dict(army="us", field=False, head="service", rank="general"),
}

E = 0.032          # jacket inflation over the skin
BODY = (-0.272, 0.272, -0.125 - E, 0.125 + E, 0.70, 1.5 + 0.02)
FY = BODY[2]       # jacket front plane (-0.157)
ARM_R = (-0.5 - E, -0.262, -0.150, 0.150, 0.84, 1.5 + E)     # inner face hidden in the jacket, front/back behind it
LEG_R = (-0.244 - 0.025, 0.002, -0.125 - 0.025, 0.125 + 0.025, 0.0, 0.78)


def fabric(s):
    c = SETS[s]
    if c["field"]:
        return "emr.png" if c["army"] == "ru" else "ocp.png"
    return "olive_rf.png" if c["army"] == "ru" else "agsu_coat.png"


def trs_fabric(s):
    c = SETS[s]
    if c["field"]:
        return fabric(s)
    return "olive_rf.png" if c["army"] == "ru" else "agsu_trs.png"


def build_body(s):
    c = SETS[s]
    fab = fabric(s)
    P = s + ".body"
    box(P, BODY, tex=fab)
    if c["field"]:
        # stand collar with the neck opening at the front
        boxes(P + ".collar", [(-0.20, 0.20, 0.10, 0.16, 1.52, 1.57), (0.13, 0.20, -0.16, 0.10, 1.52, 1.57),
                              (-0.20, -0.13, -0.16, 0.10, 1.52, 1.57)], tex=fab)
        box(P + ".zip", (-0.008, 0.008, FY - 0.005, FY, 0.72, 1.52), pal=DARK)
        if c["army"] == "ru":
            for sx in (-1, 1):                       # slanted chest pockets + flaps (Ratnik / VKPO)
                b = (0.05, 0.235, FY - 0.014, FY, 1.15, 1.34)
                box(P + ".pocket%s" % ("l" if sx > 0 else "r"), b if sx > 0 else mirror_x(b), tex=fab)
                fb = (0.045, 0.24, FY - 0.020, FY, 1.31, 1.37)
                box(P + ".flap%s" % ("l" if sx > 0 else "r"), fb if sx > 0 else mirror_x(fb), tex=fab)
            box(P + ".velcro", (0.07, 0.21, FY - 0.022, FY - 0.019, 1.39, 1.45), pal=OLIVE_D)
        else:
            box(P + ".tab", (-0.045, 0.045, FY - 0.011, FY, 1.25, 1.37), tex="tab_us_%s.png" % c["rank"], uv="fit", show="-Y")
            box(P + ".tape_army", (0.06, 0.23, FY - 0.008, FY, 1.35, 1.39), tex="tape_us_army.png", uv="fit", show="-Y")
            box(P + ".tape_name", (-0.23, -0.06, FY - 0.008, FY, 1.35, 1.39), tex="tape_us_name.png", uv="fit", show="-Y")
            for sx in (-1, 1):                       # angled chest pocket zips
                b = (0.08, 0.20, FY - 0.006, FY, 1.10, 1.115)
                box(P + ".pzip%s" % ("l" if sx > 0 else "r"), b if sx > 0 else mirror_x(b), pal=DARK)
        return
    # ---- service coat: shirt + tie V, lapels, buttons, pockets, ribbons, collar insignia (+ AGSU belt)
    shirt = "shirt_rf.png" if c["army"] == "ru" else "shirt_us.png"
    make(P + ".shirt", lambda bm: bm_prism(bm, [(-0.13, 1.52), (0.0, 1.27), (0.13, 1.52)], FY - 0.004, FY), tex=shirt, uv="fit", show="-Y")
    for sx in (-1, 1):
        pts = [(0.13, 1.52), (0.0, 1.27), (0.04, 1.255), (0.215, 1.505)]
        if sx < 0:
            pts = [(-x, z) for x, z in pts][::-1]
        make(P + ".lapel%s" % ("l" if sx > 0 else "r"), lambda bm, pts=pts: bm_prism(bm, pts, FY - 0.010, FY - 0.002), tex=fab)
    boxes(P + ".collar", [(-0.20, 0.20, 0.10, 0.165, 1.52, 1.58), (0.13, 0.205, -0.165, 0.10, 1.52, 1.575),
                          (-0.205, -0.13, -0.165, 0.10, 1.52, 1.575)], tex=fab)
    boxes(P + ".buttons", [(-0.016, 0.016, FY - 0.014, FY, z - 0.016, z + 0.016) for z in (1.21, 1.07, 0.93, 0.79)], pal=GOLD)
    flaps = [(0.06, 0.23, FY - 0.012, FY, 1.27, 1.32), (0.05, 0.25, FY - 0.012, FY, 0.92, 0.97)]
    boxes(P + ".flaps", flaps + [mirror_x(b) for b in flaps], tex=fab)
    rows = 3 if c["rank"].endswith("general") else 2
    box(P + ".ribbons", (0.125, 0.27, FY - 0.012, FY, 1.335, 1.335 + rows * 0.034), tex="ribbons_%s.png" % ("general" if rows == 3 else "colonel"), uv="fit", show="-Y")
    ins = GOLD_L if c["rank"].endswith("general") else GOLD
    boxes(P + ".insignia", [(0.15, 0.19, FY - 0.016, FY - 0.006, 1.465, 1.505), (-0.19, -0.15, FY - 0.016, FY - 0.006, 1.465, 1.505)], pal=ins)
    if c["army"] == "us":
        boxes(P + ".belt", [(BODY[0] - 0.006, BODY[1] + 0.006, BODY[2] - 0.006, BODY[3] + 0.006, 0.855, 0.905)], tex=fab)
        box(P + ".buckle", (-0.035, 0.035, FY - 0.016, FY - 0.004, 0.85, 0.91), pal=GOLD)
        boxes(P + ".lapel_us", [(0.08, 0.12, FY - 0.016, FY - 0.008, 1.36, 1.40), (-0.12, -0.08, FY - 0.016, FY - 0.008, 1.36, 1.40)], pal=GOLD)


def build_arm(s, side):
    c = SETS[s]
    fab = fabric(s)
    sx = -1 if side == "r" else 1
    P = "%s.arm_%s" % (s, side)
    A = ARM_R if sx < 0 else mirror_x(ARM_R)
    box(P, A, tex=fab)
    ox = A[0] if sx < 0 else A[1]                    # outer face x
    outer = "-X" if sx < 0 else "+X"

    def ob(y0, y1, z0, z1, t0, t1):                  # slab on the outer face
        return (ox - t1, ox - t0, y0, y1, z0, z1) if sx < 0 else (ox + t0, ox + t1, y0, y1, z0, z1)
    # shoulder board / loop on the arm top, from the outer edge towards the collar
    pg = None
    if c["army"] == "ru":
        pg = "pogon_%s.png" % c["rank"]
    elif not c["field"]:
        pg = "loop_us_%s.png" % c["rank"]
    if pg:
        b = (-0.495, -0.262, -0.058, 0.058, A[5], A[5] + 0.016)
        box(P + ".board", b if sx < 0 else mirror_x(b), tex=pg, uv="fit", show="+Z", flip=sx > 0)
    if c["field"]:
        box(P + ".cuff", (A[0] - 0.004, A[1] + 0.004, A[2] - 0.004, A[3] + 0.004, 0.84, 0.88), tex=fab)
        if c["army"] == "ru":
            box(P + ".elbow", ob(-0.08, 0.08, 1.00, 1.12, 0, 0.010), tex=fab)
            if sx > 0:
                box(P + ".flag", ob(-0.06, 0.06, 1.33, 1.41, 0, 0.006), tex="patch_ru_flag.png", uv="fit", show=outer)
        else:
            box(P + ".pocket", ob(-0.09, 0.09, 1.10, 1.36, 0, 0.012), tex=fab)
            if sx < 0:
                box(P + ".flag", ob(-0.065, 0.065, 1.22, 1.32, 0.012, 0.017), tex="patch_us_flag.png", uv="fit", show=outer)
            else:
                box(P + ".unit", ob(-0.05, 0.05, 1.22, 1.33, 0.012, 0.017), pal=OLIVE_D)
    else:
        box(P + ".cuff", (A[0] - 0.004, A[1] + 0.004, A[2] - 0.004, A[3] + 0.004, 0.865, 0.89),
            pal=BROWN_D if c["army"] == "us" else OLIVE_D)
        if sx > 0:
            if c["army"] == "ru":
                box(P + ".flag", ob(-0.06, 0.06, 1.33, 1.41, 0, 0.006), tex="patch_ru_flag.png", uv="fit", show=outer)
            else:
                box(P + ".unit", ob(-0.05, 0.05, 1.30, 1.41, 0, 0.006), pal=NAVY)


def build_leg(s, side):
    c = SETS[s]
    fab = trs_fabric(s)
    sx = -1 if side == "r" else 1
    P = "%s.leg_%s" % (s, side)
    L = LEG_R if sx < 0 else mirror_x(LEG_R)
    box(P, L, tex=fab)
    ox = L[0] if sx < 0 else L[1]
    cx = -0.119 * (1 if sx < 0 else -1)

    def ob(y0, y1, z0, z1, t0, t1):
        return (ox - t1, ox - t0, y0, y1, z0, z1) if sx < 0 else (ox + t0, ox + t1, y0, y1, z0, z1)
    if c["field"]:
        box(P + ".cargo", ob(-0.075, 0.075, 0.40, 0.57, 0, 0.014), tex=fab)
        box(P + ".cflap", ob(-0.08, 0.08, 0.55, 0.59, 0, 0.019), tex=fab)
        if c["army"] == "ru":
            box(P + ".knee", (cx - 0.075, cx + 0.075, L[2] - 0.020, L[2], 0.37, 0.50), pal=OLIVE_D)
            box(P + ".strap", (L[0] - 0.003, L[1] + 0.003, L[2] - 0.003, L[3] + 0.003, 0.42, 0.44), pal=DARK)
        else:
            box(P + ".knee", (cx - 0.085, cx + 0.085, L[2] - 0.012, L[2], 0.30, 0.47), tex=fab)
    elif c.get("lampas"):
        boxes(P + ".lampas", [ob(-0.045, -0.02, 0.03, 0.77, 0, 0.004), ob(0.02, 0.045, 0.03, 0.77, 0, 0.004)], pal=RED)


def build_boot(s, side):
    c = SETS[s]
    sx = -1 if side == "r" else 1
    P = "%s.boot_%s" % (s, side)
    m = lambda b: b if sx < 0 else mirror_x(b)
    if c["field"]:
        lt = "leather_black.png" if c["army"] == "ru" else "coyote.png"
        box(P, m((-0.29, -0.002, -0.172, 0.172, 0.0, 0.30)), tex=lt, scale=2.0)
        box(P + ".toe", m((-0.272, -0.02, -0.215, -0.17, 0.0, 0.11)), tex=lt, scale=2.0)
        box(P + ".sole", m((-0.296, 0.004, -0.222, 0.178, 0.0, 0.035)), pal=RUBBER if c["army"] == "ru" else BROWN_X)
        box(P + ".collar", m((-0.295, 0.003, -0.177, 0.177, 0.275, 0.31)), tex=lt, scale=2.0)
        box(P + ".laces", m((-0.19, -0.10, -0.176, -0.172, 0.11, 0.29)), pal=BLACK if c["army"] == "ru" else TAN_D)
    else:
        lt = "leather_black.png" if c["army"] == "ru" else "leather_brown.png"
        box(P, m((-0.285, -0.002, -0.17, 0.17, 0.0, 0.11)), tex=lt, scale=2.0)
        box(P + ".toe", m((-0.27, -0.018, -0.215, -0.168, 0.0, 0.085)), tex=lt, scale=2.0)
        box(P + ".sole", m((-0.29, 0.003, -0.22, 0.175, 0.0, 0.025)), pal=BLACK)


def top_edges(zmin):
    return lambda e: all(v.co.z > zmin for v in e.verts) and abs(e.verts[0].co.z - e.verts[1].co.z) < 1e-6


def build_head(s):
    c = SETS[s]
    P = s + ".head"
    fab = fabric(s)
    if c["head"] == "helmet":
        ru = c["army"] == "ru"
        # shell: rounded box over the square head, cover in camo
        box(P, (-0.305, 0.305, -0.315, 0.315, 1.79, 2.085), tex=fab, scale=1.6, bevel=(0.10, top_edges(2.0), 3))
        box(P + ".rim", (-0.312, 0.312, -0.322, 0.322, 1.775, 1.81), tex=fab, scale=1.6)
        box(P + ".skirt", (-0.307, 0.307, -0.02 if ru else -0.09, 0.317, 1.68 if ru else 1.70, 1.80), tex=fab, scale=1.6)
        box(P + ".nvg", (-0.045, 0.045, -0.336, -0.320, 1.80, 1.865), pal=BLACK)
        boxes(P + ".straps", [(0.252, 0.262, -0.02, 0.02, 1.55, 1.70), (-0.262, -0.252, -0.02, 0.02, 1.55, 1.70)], pal=BLACK if ru else TAN_D)
        if ru:
            box(P + ".band", (-0.316, 0.316, -0.20, 0.326, 1.90, 1.93), pal=OLIVE_D)
        else:
            boxes(P + ".rails", [(0.305, 0.317, -0.13, 0.18, 1.83, 1.86), (-0.317, -0.305, -0.13, 0.18, 1.83, 1.86)], pal=OLIVE_D)
            box(P + ".rank", (-0.035, 0.035, -0.326, -0.315, 1.885, 1.975), tex="tab_us_%s.png" % c["rank"], uv="fit", show="-Y")
            box(P + ".cateye", (-0.12, 0.12, 0.315, 0.322, 1.84, 1.86), pal=TAN_L)
    elif c["head"] == "fieldcap":
        ru = c["army"] == "ru"
        box(P, (-0.275, 0.275, -0.285, 0.285, 1.85, 2.05), tex=fab, scale=1.6, bevel=(0.02, top_edges(2.0), 1))
        box(P + ".visor", (-0.21, 0.21, -0.43, -0.28, 1.85, 1.872), tex=fab, scale=1.6, xform=rot_x(8, (0, -0.28, 1.86)))
        if ru:
            box(P + ".cockade", (-0.022, 0.022, -0.296, -0.285, 1.92, 1.965), pal=GOLD)
        else:
            box(P + ".rank", (-0.04, 0.04, -0.297, -0.285, 1.91, 1.99), tex="tab_us_%s.png" % c["rank"], uv="fit", show="-Y")
    else:
        ru = c["army"] == "ru"
        gen = c["rank"].endswith("general")
        band = "band_red.png" if ru else "agsu_coat.png"
        crown = "olive_rf.png" if ru else "agsu_coat.png"
        box(P, (-0.272, 0.272, -0.282, 0.282, 1.84, 1.94), tex=band, scale=1.6)

        def crown_mesh(bm):
            fs, vs = bm_box(bm, (-0.276, 0.276, -0.286, 0.286, 1.94, 2.04))
            k = 1.26 if ru else 1.17
            for v in vs[4:]:
                v.co.x *= k
                v.co.y *= k * 0.98
                if v.co.y < 0:
                    v.co.z += 0.065 if ru else 0.035
        make(P + ".crown", crown_mesh, tex=crown, scale=1.6)
        vis = ("visor_rf_gold.png" if gen else "visor_black.png") if ru else ("visor_us_gen.png" if gen else "visor_us_col.png")
        box(P + ".visor", (-0.24, 0.24, -0.42, -0.27, 1.835, 1.855), tex=vis, uv="fit", show="+Z",
            xform=rot_x(18, (0, -0.27, 1.845)))
        box(P + ".cord", (-0.25, 0.25, -0.290, -0.282, 1.855, 1.872), pal=GOLD)
        box(P + ".cockade", (-0.026, 0.026, -0.296, -0.282, 1.875, 1.93), pal=GOLD_L)
        box(P + ".emblem", (-0.065, 0.065, -0.333, -0.318, 1.975, 2.03), pal=GOLD)
        if ru:
            box(P + ".piping", (-0.276, 0.276, -0.287, 0.287, 1.935, 1.945), pal=RED_L)


exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "uniforms_r19.py")).read())   # r19 sets + builders

for s in SETS:
    r19 = "style" in SETS[s]
    (head19 if r19 else build_head)(s)
    (body19 if r19 else build_body)(s)
    for side in ("r", "l"):
        (arm19 if r19 else build_arm)(s, side)
        (leg19 if r19 else build_leg)(s, side)
        (boot19 if r19 else build_boot)(s, side)

# ------------------------------------------------------------------ export + save
tris = sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in scn.objects if o.type == "MESH")
os.makedirs(os.path.dirname(GLB), exist_ok=True)
bpy.ops.export_scene.gltf(filepath=GLB, export_format="GLB", export_yup=True, export_apply=True, export_animations=False,
                          export_extras=False, export_cameras=False, export_lights=False, export_image_format="AUTO",
                          use_visible=False, use_selection=False, use_active_scene=True)
print("UNIFORMS objects %d tris %d glb %d" % (len(scn.objects), tris, os.path.getsize(GLB)))
bpy.ops.wm.save_as_mainfile(filepath=BLEND, compress=True)

# ------------------------------------------------------------------ preview / icons (not saved)
SKIN = None


def mannequin(dx, name):
    global SKIN
    if SKIN is None:
        SKIN = bpy.data.materials.new("skin")
        SKIN.use_nodes = True
        SKIN.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.75, 0.55, 0.42, 1)
    parts = [(-0.25, 0.25, -0.25, 0.25, 1.5, 2.0), (-0.25, 0.25, -0.125, 0.125, 0.75, 1.5), (-0.5, -0.25, -0.125, 0.125, 0.75, 1.5),
             (0.25, 0.5, -0.125, 0.125, 0.75, 1.5), (-0.244, 0.006, -0.125, 0.125, 0, 0.75), (-0.006, 0.244, -0.125, 0.125, 0, 0.75)]
    bm = bmesh.new()
    for p in parts:
        bm_box(bm, (p[0] + dx, p[1] + dx, p[2], p[3], p[4], p[5]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    me.materials.append(SKIN)
    o = bpy.data.objects.new(name, me)
    scn.collection.objects.link(o)
    return o


def setup_render(w, h, transparent):
    scn.render.engine = "CYCLES"
    scn.cycles.device = "CPU"
    scn.cycles.samples = 24
    scn.render.resolution_x, scn.render.resolution_y = w, h
    scn.render.film_transparent = transparent
    if scn.world is None:
        scn.world = bpy.data.worlds.new("w")
    scn.world.use_nodes = True
    scn.world.node_tree.nodes["Background"].inputs[0].default_value = (0.55, 0.6, 0.65, 1)
    scn.world.node_tree.nodes["Background"].inputs[1].default_value = 0.8
    if not any(o.type == "LIGHT" for o in scn.objects):
        ld = bpy.data.lights.new("sun", "SUN")
        ld.energy = 3.0
        lo = bpy.data.objects.new("sun", ld)
        lo.rotation_euler = (math.radians(50), math.radians(10), math.radians(-30))
        scn.collection.objects.link(lo)


def camera(loc, look, ortho=None):
    cd = bpy.data.cameras.new("cam")
    if ortho:
        cd.type = "ORTHO"
        cd.ortho_scale = ortho
    co = bpy.data.objects.new("cam", cd)
    scn.collection.objects.link(co)
    co.location = loc
    d = Vector(look) - Vector(loc)
    co.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    scn.camera = co
    return co


if "preview19" in ARGS:
    order = [k for k in SETS if "style" in SETS[k]]
    orig = [o for o in scn.objects if o.type == "MESH"]
    for o in orig:
        o.hide_render = not any(o.name.startswith(k + ".") for k in order)
    for i, s in enumerate(order):
        dx = (i - 2.5) * 1.3
        m = mannequin(0, "man_" + s)
        m.location = (dx, 0, 0)
        for o in orig:
            if o.name.startswith(s + "."):
                o.location = (dx, 0, 0)
    setup_render(1500, 560, False)
    cam = camera((0, -20, 1.1), (0, 0, 1.1), ortho=8.2)
    scn.render.filepath = "/home/user/.cache/tmpv/u19_front.png"
    bpy.ops.render.render(write_still=True)
    cam.location = (7, 18, 5)
    cam.rotation_euler = (Vector((0, 0, 1.1)) - cam.location).to_track_quat("-Z", "Y").to_euler()
    scn.render.filepath = "/home/user/.cache/tmpv/u19_back.png"
    bpy.ops.render.render(write_still=True)
    cam.data.ortho_scale = 4.4
    scn.render.resolution_x, scn.render.resolution_y = 1500, 700
    for j, nm in ((0, "a"), (3, "b")):
        c = Vector(((j - 2.5 + 1) * 1.3, 0, 1.55))
        cam.location = c + Vector((-6, -12, 4))
        cam.rotation_euler = (c - cam.location).to_track_quat("-Z", "Y").to_euler()
        scn.render.filepath = "/home/user/.cache/tmpv/u19_close_%s.png" % nm
        bpy.ops.render.render(write_still=True)
    print("PREVIEW_DONE")

if "preview" in ARGS:
    order = list(SETS)
    orig = [o for o in scn.objects if o.type == "MESH"]
    for i, s in enumerate(order):
        dx, dz = ((i % 5) - 2) * 1.3, (2.45 if i < 5 else 0.0)
        m = mannequin(0, "man_" + s)
        m.location = (dx, 0, dz)
        for o in orig:
            if o.name.startswith(s + "."):
                o.location = (dx, 0, dz)
    setup_render(1600, 1150, False)
    cam = camera((0, -20, 2.25), (0, 0, 2.25), ortho=7.0)
    scn.render.filepath = "/home/user/tmp/uniforms_front.png"
    bpy.ops.render.render(write_still=True)
    cam.location = (8, 18, 6)
    cam.rotation_euler = (Vector((0, 0, 2.25)) - cam.location).to_track_quat("-Z", "Y").to_euler()
    scn.render.filepath = "/home/user/tmp/uniforms_back.png"
    bpy.ops.render.render(write_still=True)
    # close-up 3/4 of the RF general and the US sergeant
    cam.data.ortho_scale = 2.6
    cam.location = Vector((1.3 + 8, -12, 2.45 + 1.0 + 4))
    cam.rotation_euler = (Vector((1.3, 0, 2.45 + 1.0)) - cam.location).to_track_quat("-Z", "Y").to_euler()
    scn.render.resolution_x, scn.render.resolution_y = 900, 900
    scn.render.filepath = "/home/user/tmp/uniforms_close1.png"
    bpy.ops.render.render(write_still=True)
    cam.location = Vector((-1.3 - 8, -12, 1.0 + 4))
    cam.rotation_euler = (Vector((-1.3, 0, 1.0)) - cam.location).to_track_quat("-Z", "Y").to_euler()
    scn.render.filepath = "/home/user/tmp/uniforms_close2.png"
    bpy.ops.render.render(write_still=True)
    print("PREVIEW_DONE")

if "icons" in ARGS or "icons19" in ARGS:
    setup_render(96, 96, True)
    cam = camera((0, 0, 0), (0, 1, 0), ortho=1.0)
    groups = {"head": ["head"], "chest": ["body", "arm_r", "arm_l"], "legs": ["leg_r", "leg_l"], "feet": ["boot_r", "boot_l"]}
    allm = [o for o in scn.objects if o.type == "MESH"]
    for s in [k for k in SETS if "icons" in ARGS or "style" in SETS[k]]:
        for slot, pieces in groups.items():
            sel = [o for o in allm if any(o.name == s + "." + p or o.name.startswith(s + "." + p + ".") for p in pieces)]
            for o in allm:
                o.hide_render = o not in sel
            pts = [o.matrix_world @ Vector(c) for o in sel for c in o.bound_box]
            mn = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
            mx = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
            ctr = (mn + mx) / 2
            size = max(mx.x - mn.x, mx.z - mn.z, mx.y - mn.y)
            dirv = Vector((-0.55, -1.0, 0.45)).normalized()
            cam.location = ctr + dirv * 6
            cam.rotation_euler = (-dirv).to_track_quat("-Z", "Y").to_euler()
            cam.data.ortho_scale = size * 1.25
            scn.render.filepath = os.path.join(ICONS, "uniform_%s_%s.png" % (s, slot))
            bpy.ops.render.render(write_still=True)
    print("ICONS_DONE")
