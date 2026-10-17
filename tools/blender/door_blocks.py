"""r16 building blocks part 3 (Blender 5.x headless):
   blender -b --factory-startup --python tools/blender/door_blocks.py
Doors (frame + left / right hinged leaf, bunker wheel), hatches (frame + lid, bunker wheel), multi-block gates
(static frame + moving leaves), camouflage nets (pane-like post / side), net canopy (sheet + fringe), camouflage bushes.
Same block-space conventions and OBJ export as field_blocks.py / deco_blocks.py (1 unit = 1 block, origin = block
corner, +Y up, model front = NORTH = -Z), real dimensions in metres. Moving parts are separate OBJ models posed by the
block entity renderers (pivots documented below and mirrored in ru.aiforge.realwar.deco.DoorKind / GateKind).
Saves models/door_blocks.blend (textures packed) and writes models/block/door/door_texmap.json (material -> texture)."""
import bpy, bmesh, math, os, json
from mathutils import Vector, Matrix

_SRC = open("/home/user/tools/blender/deco_blocks.py").read()
exec(_SRC[:_SRC.index("# ================================================================== FLAGPOLE")])
OUT = RES + "/models/block/door"
os.makedirs(OUT, exist_ok=True)
NEW = [("steel_olive", "door_steel_olive.png", False), ("steel_grey", "door_steel_grey.png", False),
       ("lab_white", "door_lab_white.png", False), ("paint_red", "door_paint_red.png", False),
       ("dark_steel", "door_dark_steel.png", False), ("wood_green", "door_wood_green.png", False),
       ("mesh", "gate_mesh.png", True), ("star", "gate_star.png", True), ("boom", "gate_boom.png", False),
       ("net", "camo_net_forest.png", True), ("leaf", "camo_leaf_forest.png", True)]
for k, png, a in NEW:
    mat(k, png, a)
ORDER[:] = list(MATS.keys())
T = 3 / 16                                   # vanilla door / trapdoor slab thickness


def sbox(p, x0, x1, y0, y1, z0, z1, m, rot=None):
    """Axis box split at whole-block boundaries in x and y (keeps UVs inside 0..1 for the atlas)."""
    xs = [x0] + [float(i) for i in range(math.floor(x0) + 1, math.ceil(x1)) if x0 < i < x1] + [x1]
    ys = [y0] + [float(i) for i in range(math.floor(y0) + 1, math.ceil(y1)) if y0 < i < y1] + [y1]
    for i in range(len(xs) - 1):
        for j in range(len(ys) - 1):
            p.box(((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2, (z0 + z1) / 2),
                  (xs[i + 1] - xs[i], ys[j + 1] - ys[j], z1 - z0), m)


def zbox(p, x0, x1, y0, y1, z0, z1, m):
    """Same, split along z and x (horizontal plates longer than a block)."""
    xs = [x0] + [float(i) for i in range(math.floor(x0) + 1, math.ceil(x1)) if x0 < i < x1] + [x1]
    zs = [z0] + [float(i) for i in range(math.floor(z0) + 1, math.ceil(z1)) if z0 < i < z1] + [z1]
    for i in range(len(xs) - 1):
        for j in range(len(zs) - 1):
            p.box(((xs[i] + xs[i + 1]) / 2, (y0 + y1) / 2, (zs[j] + zs[j + 1]) / 2), (xs[i + 1] - xs[i], y1 - y0, zs[j + 1] - zs[j]), m)


def quad2(p, a, b, c_, d, m, uv=((0, 0), (1, 0), (1, 1), (0, 1))):
    """Double-sided textured quad (two coincident faces, kept by orient_for_mc)."""
    quad_uv(p, a, b, c_, d, m, uv)
    quad_uv(p, d, c_, b, a, m, (uv[3], uv[2], uv[1], uv[0]))


def rivets(p, pts, m="dark_steel", r=0.012, axis="Z", h=0.012):
    for c in pts:
        cyl(p, c, r, h, m, m, axis=axis, seg=6)


def build(name, *fns, ds=False):
    p = Piece(name)
    for f in fns:
        f(p)
    if ds:
        done(p, double_sided=True)
    else:
        done(p)
    return p


def build_mirrored(name, fn):
    p = Piece(name)
    fn(p)
    for v in p.bm.verts:
        v.co.x = 1.0 - v.co.x
    for f in p.bm.faces:                      # mirroring reverses the winding: flip back (orient_for_mc re-checks anyway)
        f.normal_flip()
    uvf = getattr(p, "uvfix", {})
    for f in list(uvf.keys()):
        uvf[f] = [(1 - u, v) for (u, v) in uvf[f]]
    done(p)
    return p


# ====================================================================================== DOORS
# Canonical door (blockstate facing=north, hinge left): closed leaf at the NORTH edge of the lower block, hinge on the
# WEST (x small) side, opens towards +Z. Leaf pivot (x, z) per kind = DoorKind.pivot; right-hinged models are mirrored.
FR = 0.08                                     # frame jamb width
LX0, LX1, LY1 = FR, 1 - FR, 2 - FR            # leaf extent


def frame(p, m, depth=(0.0, T), head=True, sill=False, w=FR):
    z0, z1 = depth
    sbox(p, 0.0, w, 0.0, 2.0, z0, z1, m)
    sbox(p, 1 - w, 1.0, 0.0, 2.0, z0, z1, m)
    if head:
        sbox(p, w, 1 - w, 2 - w, 2.0, z0, z1, m)
    if sill:
        sbox(p, w, 1 - w, 0.0, 0.02, z0, z1, m)


def hinges(p, x, z, ys, r=0.022, h=0.12, m="dark_steel"):
    for y in ys:
        cyl(p, (x, y, z), r, h, m, m, seg=8)


def lever_handle(p, x, y, zf, zb, m="dark_steel", side=1):
    """Lever handles on both faces + escutcheons. side=+1 lever points to +x (towards the hinge for a right... )."""
    for z, s in ((zf, -1), (zb, 1)):
        p.box((x, y - 0.04, z + s * 0.004), (0.035, 0.20, 0.008), m)            # escutcheon plate
        cyl(p, (x, y, z + s * 0.025), 0.009, 0.05, m, m, axis="Z", seg=6)        # spindle
        p.box((x - side * 0.055, y, z + s * 0.05), (0.12, 0.018, 0.018), m)       # lever
        cyl(p, (x, y - 0.11, z + s * 0.008), 0.008, 0.016, "black", "black", axis="Z", seg=6)   # keyhole


# ---- 1. steel armoured door (olive, riveted, 7 cm leaf)
def steel_frame(p):
    frame(p, "dark_steel", (0.0, 0.16))
    sbox(p, 0.0, 1.0, 1.96, 2.0, -0.02, 0.0, "dark_steel")


def steel_leaf(p):
    z0, z1 = 0.05, 0.12
    sbox(p, LX0, LX1, 0.01, LY1, z0, z1, "steel_olive")
    for z, s in ((z0, -1), (z1, 1)):                                   # raised border + horizontal stiffener on both faces
        zz0, zz1 = (z - 0.012, z) if s < 0 else (z, z + 0.012)
        sbox(p, LX0, LX0 + 0.05, 0.01, LY1, zz0, zz1, "steel_olive")
        sbox(p, LX1 - 0.05, LX1, 0.01, LY1, zz0, zz1, "steel_olive")
        sbox(p, LX0 + 0.05, LX1 - 0.05, LY1 - 0.05, LY1, zz0, zz1, "steel_olive")
        sbox(p, LX0 + 0.05, LX1 - 0.05, 0.01, 0.06, zz0, zz1, "steel_olive")
        sbox(p, LX0 + 0.05, LX1 - 0.05, 0.93, 0.98, zz0, zz1, "steel_olive")
        zr = zz0 if s < 0 else zz1
        pts = []
        for i in range(9):
            y = 0.04 + i * (LY1 - 0.08) / 8
            pts += [(LX0 + 0.025, y, zr + s * 0.004), (LX1 - 0.025, y, zr + s * 0.004)]
        for i in range(1, 6):
            x = LX0 + 0.05 + i * (LX1 - LX0 - 0.1) / 6
            pts += [(x, LY1 - 0.025, zr + s * 0.004), (x, 0.035, zr + s * 0.004), (x, 0.955, zr + s * 0.004)]
        rivets(p, pts)
    hinges(p, LX0 - 0.01, 0.085, (0.25, 1.0, 1.7))
    lever_handle(p, LX1 - 0.08, 1.0, z0 - 0.012, z1 + 0.012, side=1)
    p.box((LX1 - 0.08, 1.45, z0 - 0.02), (0.05, 0.05, 0.02), "black")           # peephole cover
    cyl(p, (LX1 - 0.08, 1.45, z0 - 0.032), 0.012, 0.006, "glass", "glass", axis="Z", seg=8)


# ---- 2. bunker pressure door (thick, seal, 4 dogs, central wheel)
def bunker_frame(p):
    frame(p, "concrete_dark", (-0.04, 0.22), w=0.1)
    sbox(p, 0.1, 0.9, 0.0, 0.03, -0.04, 0.22, "steel")                   # threshold
    sbox(p, 0.085, 0.1, 0.03, 1.9, -0.045, 0.0, "rubber")
    sbox(p, 0.9, 0.915, 0.03, 1.9, -0.045, 0.0, "rubber")
    for y in (0.5, 1.5):                                                # dog strikers on the latch jamb
        p.box((0.93, y, 0.15), (0.05, 0.08, 0.06), "dark_steel")


BZ0, BZ1 = 0.02, 0.18


def bunker_leaf(p):
    x0, x1 = 0.1, 0.9
    sbox(p, x0, x1, 0.03, 1.9, BZ0, BZ1, "steel_grey")
    for z, s in ((BZ0, -1), (BZ1, 1)):                                 # pressed ribs (outer face) / stiffening (inner)
        zz = (z - 0.02, z) if s < 0 else (z, z + 0.02)
        for y in (0.35, 0.96, 1.57):
            sbox(p, x0 + 0.06, x1 - 0.06, y - 0.03, y + 0.03, *zz, "steel_grey")
        for x in (x0 + 0.06, x1 - 0.06):
            sbox(p, x - 0.03, x + 0.03, 0.12, 1.8, *zz, "steel_grey")
        rivets(p, [(xx, yy, zz[0] if s < 0 else zz[1]) for xx in (x0 + 0.03, x1 - 0.03) for yy in (0.1, 0.5, 0.9, 1.3, 1.7)], r=0.014)
    for y in (0.45, 1.5):                                              # 4 clamp dogs on the latch edge (both faces)
        for z in (BZ0 - 0.03, BZ1 + 0.03):
            cyl(p, (x1 - 0.07, y, z), 0.02, 0.04, "dark_steel", "dark_steel", axis="Z", seg=8)
            p.box((x1 - 0.02, y, z), (0.12, 0.025, 0.025), "paint_red")
    hinges(p, x0 - 0.02, 0.10, (0.35, 1.55), r=0.04, h=0.22)
    for y in (0.35, 1.55):
        p.box((x0 + 0.03, y, BZ1 + 0.015), (0.14, 0.16, 0.03), "dark_steel")       # hinge straps
    cyl(p, (0.5, 1.0, (BZ0 + BZ1) / 2), 0.03, BZ1 - BZ0 + 0.1, "dark_steel", "dark_steel", axis="Z", seg=8)   # wheel shaft
    cyl(p, (0.5, 1.0, BZ0 - 0.025), 0.06, 0.05, "dark_steel", "dark_steel", axis="Z", seg=10)
    cyl(p, (0.5, 1.0, BZ1 + 0.025), 0.06, 0.05, "dark_steel", "dark_steel", axis="Z", seg=10)


def wheel(p, c, R, axis, m="paint_red", spokes=4, r=0.022):
    c = Vector(c)
    ring_torus(p, tuple(c), R, r, m, axis=axis, seg=20, sides=6)
    for k in range(spokes):
        a = TAU * k / spokes
        if axis == "Z":
            d = Vector((math.cos(a), math.sin(a), 0))
        else:
            d = Vector((math.cos(a), 0, math.sin(a)))
        p.tube([tuple(c), tuple(c + d * R)], r * 0.75, m, sides=6)
        p.box(tuple(c + d * R * 1.0), (0.03, 0.03, 0.03), m)                    # knuckle
    cyl(p, tuple(c), 0.045, 0.05, "dark_steel", "dark_steel", axis=axis, seg=10)


def bunker_wheel(p):                                                    # DoorKind.BUNKER wheel: centre (0.5, 1.0, 0.10), axis Z
    wheel(p, (0.5, 1.0, BZ0 - 0.07), 0.2, "Z")
    wheel(p, (0.5, 1.0, BZ1 + 0.07), 0.2, "Z")


# ---- 3. bar door (cell / cage)
def bars_frame(p):
    frame(p, "dark_steel", (0.02, 0.16))


def bars_leaf(p):
    z = 0.09
    for y0, y1 in ((0.04, 0.1), (0.96, 1.02), (1.84, LY1)):                       # flat rails
        sbox(p, LX0, LX1, y0, y1, z - 0.02, z + 0.02, "dark_steel")
    sbox(p, LX0, LX0 + 0.05, 0.04, LY1, z - 0.025, z + 0.025, "dark_steel")
    sbox(p, LX1 - 0.05, LX1, 0.04, LY1, z - 0.025, z + 0.025, "dark_steel")
    n = 8
    for i in range(n):
        x = LX0 + 0.05 + (i + 0.5) * (LX1 - LX0 - 0.1) / n
        cyl(p, (x, 0.95, z), 0.013, 1.82, "dark_steel", "dark_steel", seg=6)
    p.box((LX1 - 0.09, 1.0, z), (0.12, 0.2, 0.08), "steel")                       # lock box
    cyl(p, (LX1 - 0.09, 1.0, z - 0.042), 0.01, 0.006, "black", "black", axis="Z", seg=6)
    hinges(p, LX0 - 0.01, z, (0.2, 1.0, 1.8), r=0.02, h=0.1)


# ---- 4. army wooden door (green planks, 4-light window, brass handle)
def wood_frame(p):
    frame(p, "wood", (0.0, 0.17))
    sbox(p, 0.0, 1.0, 1.92, 2.0, -0.02, 0.0, "wood")                            # architrave head (outer side)


def wood_leaf(p):
    z0, z1 = 0.05, 0.11
    sbox(p, LX0, LX1, 0.01, 1.2, z0, z1, "wood_green")
    sbox(p, LX0, LX1, 1.72, LY1, z0, z1, "wood_green")
    sbox(p, LX0, LX0 + 0.12, 1.2, 1.72, z0, z1, "wood_green")
    sbox(p, LX1 - 0.12, LX1, 1.2, 1.72, z0, z1, "wood_green")
    p.box((0.5, 1.46, (z0 + z1) / 2), (LX1 - LX0 - 0.24, 0.52, 0.006), "glass")
    for x in (0.5,):                                                              # muntins
        p.box((x, 1.46, (z0 + z1) / 2), (0.025, 0.52, 0.03), "wood_green")
    p.box((0.5, 1.46, (z0 + z1) / 2), (LX1 - LX0 - 0.24, 0.025, 0.03), "wood_green")
    for y in (0.25, 1.05):                                                         # ledges on the inner face + Z brace
        sbox(p, LX0 + 0.03, LX1 - 0.03, y - 0.05, y + 0.05, z1, z1 + 0.025, "wood_green")
    L = Vector((LX1 - 0.1 - (LX0 + 0.1), 0.8 - 0.1, 0))
    ang = math.atan2(L.y, L.x)
    p.box((0.5, 0.65, z1 + 0.0125), (L.length, 0.09, 0.025), "wood_green", rot=Matrix.Rotation(ang, 3, "Z"))
    hinges(p, LX0 - 0.01, 0.08, (0.25, 1.75), r=0.018)
    for z, s in ((z0, -1), (z1 + 0.025, 1)):
        cyl(p, (LX1 - 0.08, 1.0, z + s * 0.02), 0.022, 0.012, "brass", "brass", axis="Z", seg=10)
        p.box((LX1 - 0.12, 1.0, z + s * 0.04), (0.09, 0.016, 0.016), "brass")


# ---- 5. laboratory door (white, porthole, hazard kick band, push bar)
def lab_frame(p):
    frame(p, "lab_white", (0.0, 0.17))
    sbox(p, 0.0, 1.0, 1.99, 2.0, 0.0, 0.17, "lab_white")
    p.box((0.5, 1.96, -0.02), (0.36, 0.06, 0.04), "black")                       # "access" sign box
    p.box((0.5, 1.96, -0.041), (0.32, 0.04, 0.002), "led")


def lab_leaf(p):
    z0, z1 = 0.05, 0.12
    sbox(p, LX0, LX1, 0.01, 1.22, z0, z1, "lab_white")
    sbox(p, LX0, LX1, 1.62, LY1, z0, z1, "lab_white")
    sbox(p, LX0, 0.34, 1.22, 1.62, z0, z1, "lab_white")
    sbox(p, 0.66, LX1, 1.22, 1.62, z0, z1, "lab_white")
    p.box((0.5, 1.42, (z0 + z1) / 2), (0.32, 0.4, 0.006), "glass")
    for z in (z0 - 0.006, z1 + 0.006):                                           # window gasket
        for (cx, cy, sx, sy) in ((0.5, 1.22, 0.36, 0.03), (0.5, 1.62, 0.36, 0.03), (0.33, 1.42, 0.03, 0.43), (0.67, 1.42, 0.03, 0.43)):
            p.box((cx, cy, z), (sx, sy, 0.012), "black")
    for z, s in ((z0, -1), (z1, 1)):
        sbox(p, LX0 + 0.02, LX1 - 0.02, 0.06, 0.26, z + s * 0.004, z + s * 0.004 + s * 0.002, "hazard")
    p.box((0.5, 1.0, z1 + 0.06), (0.62, 0.035, 0.035), "chrome")                   # push bar (inner face)
    for x in (0.22, 0.78):
        p.box((x, 1.0, z1 + 0.03), (0.03, 0.035, 0.06), "chrome")
    p.box((LX1 - 0.1, 1.0, z0 - 0.03), (0.04, 0.16, 0.03), "chrome")             # pull handle (outer)
    hinges(p, LX0 - 0.01, 0.085, (0.25, 1.0, 1.75), m="chrome")


DOORS = [("steel", steel_frame, steel_leaf), ("bunker", bunker_frame, bunker_leaf), ("bars", bars_frame, bars_leaf),
         ("wood", wood_frame, wood_leaf), ("lab", lab_frame, lab_leaf)]
for nm, fr, lf in DOORS:
    build("door_%s_frame" % nm, fr)
    build_mirrored("door_%s_frame_r" % nm, fr)
    build("door_%s_leaf_l" % nm, lf)
    build_mirrored("door_%s_leaf_r" % nm, lf)
    build("door_%s_item" % nm, fr, lf, *( [bunker_wheel] if nm == "bunker" else []))
build("door_bunker_wheel", bunker_wheel)

# ====================================================================================== HATCHES
# Canonical hatch (trapdoor facing so that the open slab is at the NORTH edge, half=bottom): lid closed lying at the
# bottom, hinge along z = HZ at height HY, opens up to vertical at the north edge. Frame = coaming ring.
HY, HZ = 0.05, 0.05


def hatch_frame(p, m="dark_steel"):
    zbox(p, 0.0, 1.0, 0.0, 0.03, 0.0, 0.06, m)
    zbox(p, 0.0, 1.0, 0.0, 0.03, 0.94, 1.0, m)
    zbox(p, 0.0, 0.06, 0.0, 0.03, 0.06, 0.94, m)
    zbox(p, 0.94, 1.0, 0.0, 0.03, 0.06, 0.94, m)


def hatch_steel_lid(p):
    zbox(p, 0.06, 0.94, 0.02, 0.09, 0.06, 0.94, "steel")
    for x in (0.3, 0.7):                                                           # chequer ribs on top
        zbox(p, x - 0.02, x + 0.02, 0.09, 0.105, 0.12, 0.88, "steel")
    for z in (0.3, 0.7):
        zbox(p, 0.12, 0.88, 0.09, 0.105, z - 0.02, z + 0.02, "steel")
    ring_torus(p, (0.5, 0.1, 0.8), 0.05, 0.01, "dark_steel", axis="Y", seg=12, sides=4)   # recessed lift ring
    for x in (0.25, 0.75):
        cyl(p, (x, HY, HZ), 0.025, 0.14, "dark_steel", "dark_steel", axis="X", seg=8)        # hinges
    zbox(p, 0.1, 0.9, 0.0, 0.02, 0.1, 0.9, "dark_steel")                               # underside stiffener plate


def hatch_bunker_frame(p):
    hatch_frame(p, "concrete_dark")


def hatch_bunker_lid(p):
    zbox(p, 0.06, 0.94, 0.02, 0.05, 0.06, 0.94, "steel_grey")                          # square base plate
    cyl(p, (0.5, 0.08, 0.5), 0.40, 0.06, "steel_grey", "steel_grey", seg=20)           # round lid
    cyl(p, (0.5, 0.115, 0.5), 0.34, 0.012, "steel_grey", "steel_grey", seg=20)
    ring_torus(p, (0.5, 0.05, 0.5), 0.405, 0.012, "rubber", axis="Y", seg=20, sides=4)
    for k in range(4):                                                                  # clamp dogs
        a = TAU * (k + 0.5) / 4
        p.box((0.5 + math.cos(a) * 0.38, 0.1, 0.5 + math.sin(a) * 0.38), (0.06, 0.03, 0.06), "paint_red")
    cyl(p, (0.5, 0.06, 0.5), 0.03, 0.2, "dark_steel", "dark_steel", seg=8)              # wheel shaft
    for x in (0.3, 0.7):
        cyl(p, (x, HY, HZ), 0.03, 0.16, "dark_steel", "dark_steel", axis="X", seg=8)


def hatch_bunker_wheel(p):                       # DoorKind.HATCH_BUNKER wheel: centre (0.5, 0.06, 0.5), axis Y
    wheel(p, (0.5, 0.155, 0.5), 0.2, "Y")
    wheel(p, (0.5, -0.02, 0.5), 0.12, "Y", r=0.016)


def hatch_grate_lid(p):
    zbox(p, 0.06, 0.94, 0.02, 0.08, 0.06, 0.12, "dark_steel")
    zbox(p, 0.06, 0.94, 0.02, 0.08, 0.88, 0.94, "dark_steel")
    zbox(p, 0.06, 0.12, 0.02, 0.08, 0.12, 0.88, "dark_steel")
    zbox(p, 0.88, 0.94, 0.02, 0.08, 0.12, 0.88, "dark_steel")
    for i in range(9):                                                                   # bearing bars
        x = 0.12 + (i + 0.5) * 0.76 / 9
        zbox(p, x - 0.008, x + 0.008, 0.02, 0.08, 0.12, 0.88, "dark_steel")
    for z in (0.37, 0.63):                                                              # cross bars
        zbox(p, 0.12, 0.88, 0.04, 0.06, z - 0.006, z + 0.006, "dark_steel")
    for x in (0.25, 0.75):
        cyl(p, (x, HY, HZ), 0.022, 0.12, "dark_steel", "dark_steel", axis="X", seg=8)


HATCHES = [("steel", hatch_frame, hatch_steel_lid), ("bunker", hatch_bunker_frame, hatch_bunker_lid), ("grate", hatch_frame, hatch_grate_lid)]
for nm, fr, lid in HATCHES:
    build("hatch_%s_frame" % nm, fr)
    build("hatch_%s_lid" % nm, lid)
    build("hatch_%s_item" % nm, fr, lid, *([hatch_bunker_wheel] if nm == "bunker" else []))
build("hatch_bunker_wheel", hatch_bunker_wheel)

# ====================================================================================== GATES
# Canonical gate: facing north, master block at the origin, the gate extends to +X (player's right), plane at z = 0.5.
# Pivots / travel live in GateKind (Java).


def post(p, x0, x1, h, m, cap=None, z0=0.38, z1=0.62):
    sbox(p, x0, x1, 0.0, h, z0, z1, m)
    if cap:
        sbox(p, x0 - 0.02, x1 + 0.02, h, h + 0.04, z0 - 0.02, z1 + 0.02, cap)


def tube_frame_rect(p, x0, x1, y0, y1, z, r, m):
    """Rectangular frame of square tube (split per block)."""
    sbox(p, x0, x1, y0, y0 + r, z - r / 2, z + r / 2, m)
    sbox(p, x0, x1, y1 - r, y1, z - r / 2, z + r / 2, m)
    sbox(p, x0, x0 + r, y0 + r, y1 - r, z - r / 2, z + r / 2, m)
    sbox(p, x1 - r, x1, y0 + r, y1 - r, z - r / 2, z + r / 2, m)


def mesh_panel(p, x0, x1, y0, y1, z):
    xs = [x0] + [float(i) for i in range(math.floor(x0) + 1, math.ceil(x1)) if x0 < i < x1] + [x1]
    ys = [y0] + [float(i) for i in range(math.floor(y0) + 1, math.ceil(y1)) if y0 < i < y1] + [y1]
    for i in range(len(xs) - 1):
        for j in range(len(ys) - 1):
            a, b = xs[i], xs[i + 1]
            c_, d = ys[j], ys[j + 1]
            uv = ((a - math.floor(a + 1e-6), c_ - math.floor(c_ + 1e-6)), (b - math.floor(a + 1e-6), c_ - math.floor(c_ + 1e-6)),
                  (b - math.floor(a + 1e-6), d - math.floor(c_ + 1e-6)), (a - math.floor(a + 1e-6), d - math.floor(c_ + 1e-6)))
            quad2(p, (a, c_, z), (b, c_, z), (b, d, z), (a, d, z), "mesh", uv)


def barbed(p, x0, x1, y, z):
    p.tube([(x0, y, z), (x1, y, z)], 0.006, "wire", sides=4)
    x = x0 + 0.1
    while x < x1 - 0.05:
        p.tube([(x - 0.02, y - 0.02, z - 0.02), (x + 0.02, y + 0.02, z + 0.02)], 0.004, "wire", sides=3)
        p.tube([(x - 0.02, y + 0.02, z - 0.02), (x + 0.02, y - 0.02, z + 0.02)], 0.004, "wire", sides=3)
        x += 0.2


# ---- sliding gate 5 x 2 (cantilever, chain-link infill, barbed wire); leaf slides -X by 4.6
def sliding_static(p):
    post(p, 0.0, 0.18, 2.15, "dark_steel", cap="dark_steel", z0=0.58, z1=0.82)     # main post stands behind the leaf plane
    post(p, 4.82, 5.0, 2.15, "dark_steel", cap="dark_steel")
    sbox(p, 4.74, 4.82, 0.4, 1.8, 0.36, 0.40, "dark_steel")                       # catcher
    sbox(p, 4.74, 4.82, 0.4, 1.8, 0.60, 0.64, "dark_steel")
    for z in (0.43, 0.57):                                                        # guide rollers: the leaf runs between them
        cyl(p, (0.12, 1.92, z), 0.03, 0.06, "black", "black", seg=10)
        p.box((0.12, 1.99, 0.5), (0.08, 0.03, 0.2), "dark_steel")
        cyl(p, (0.12, 0.04, z), 0.035, 0.08, "black", "black", seg=10)
    sbox(p, 0.04, 0.2, 1.9, 2.02, 0.58, 0.62, "dark_steel")                       # roller bracket to the post
    p.box((0.09, 1.3, 0.87), (0.12, 0.2, 0.1), "steel")                            # motor / lock box
    cyl(p, (0.09, 2.2, 0.7), 0.04, 0.05, "glass_orange", "glass_orange", seg=8)  # flasher


def sliding_leaf(p):
    x0, x1 = 0.2, 4.8
    tube_frame_rect(p, x0, x1, 0.08, 1.95, 0.5, 0.06, "dark_steel")
    for k in range(1, 5):                                                          # verticals
        x = x0 + k * (x1 - x0) / 5
        sbox(p, x - 0.025, x + 0.025, 0.14, 1.89, 0.475, 0.525, "dark_steel")
    sbox(p, x0 + 0.06, x1 - 0.06, 0.98, 1.02, 0.48, 0.52, "dark_steel")
    mesh_panel(p, x0 + 0.06, x1 - 0.06, 0.14, 1.89, 0.5)
    for k in range(6):                                                             # V-arms for the barbed wire
        x = x0 + 0.05 + k * (x1 - x0 - 0.1) / 5
        p.tube([(x, 1.95, 0.5), (x, 2.12, 0.42)], 0.01, "dark_steel", sides=4)
        p.tube([(x, 1.95, 0.5), (x, 2.12, 0.58)], 0.01, "dark_steel", sides=4)
    for (y, z) in ((2.04, 0.46), (2.1, 0.43), (2.04, 0.54), (2.1, 0.57)):
        barbed(p, x0, x1, y, z)
    sbox(p, x0, x1, 0.0, 0.08, 0.47, 0.53, "dark_steel")                           # bottom beam (rides on rollers)


# ---- swing gate 4 x 3 (two leaves with red stars)
def swing_static(p):
    for x0 in (0.0, 3.68):
        post(p, x0, x0 + 0.32, 3.1, "concrete", cap="concrete_dark", z0=0.34, z1=0.66)
        for y in (0.6, 2.2):
            p.box((x0 + (0.32 if x0 < 1 else 0.0) + (0.015 if x0 < 1 else -0.015), y, 0.5), (0.03, 0.12, 0.06), "dark_steel")   # hinge pins


def swing_leaf(p, x0, x1, hinge_left):
    z = 0.5
    tube_frame_rect(p, x0, x1, 0.12, 2.65, z, 0.07, "steel_olive")
    sbox(p, x0 + 0.07, x1 - 0.07, 0.19, 2.58, z - 0.012, z + 0.012, "steel_olive")     # sheet infill
    for y in (0.9, 1.85):
        sbox(p, x0 + 0.07, x1 - 0.07, y - 0.025, y + 0.025, z - 0.03, z + 0.03, "steel_olive")
    xh = x0 if hinge_left else x1
    xd = x1 - x0
    p.tube([(x0 + 0.07, 0.25, z + 0.03), (x1 - 0.07, 2.5, z + 0.03)] if hinge_left else
           [(x1 - 0.07, 0.25, z + 0.03), (x0 + 0.07, 2.5, z + 0.03)], 0.02, "steel_olive", sides=4)   # diagonal brace (inner)
    cx = (x0 + x1) / 2
    for zz in (z - 0.035, z + 0.035):                                                # star on both faces
        s = 0.62
        quad2(p, (cx - s, 1.38 - s, zz), (cx + s, 1.38 - s, zz), (cx + s, 1.38 + s, zz), (cx - s, 1.38 + s, zz), "star")
    for y in (0.6, 2.2):
        cyl(p, (xh, y, z), 0.04, 0.16, "dark_steel", "dark_steel", seg=8)
    if not hinge_left:                                                                 # drop bolt + latch on the meeting stile
        cyl(p, (x0 + 0.1, 0.4, z - 0.06), 0.012, 0.6, "dark_steel", "dark_steel", seg=6)
        p.box((x0 + 0.05, 1.3, z - 0.05), (0.12, 0.06, 0.04), "dark_steel")


# ---- boom barrier (post housing + 4.4 m striped arm), arm pivot (0.5, 0.95, 0.24) about Z
BOOM_PIV = (0.5, 0.95, 0.24)


def boom_static(p):
    sbox(p, 0.3, 0.7, 0.0, 1.02, 0.3, 0.7, "boom")
    sbox(p, 0.28, 0.72, 1.02, 1.08, 0.28, 0.72, "dark_steel")                      # hood
    sbox(p, 0.25, 0.75, 0.0, 0.04, 0.25, 0.75, "concrete")                         # plinth
    cyl(p, (0.5, BOOM_PIV[1], 0.29), 0.06, 0.03, "dark_steel", "dark_steel", axis="Z", seg=10)   # pivot boss
    cyl(p, (0.5, 1.14, 0.5), 0.05, 0.08, "glass_red", "glass_red", seg=8)          # signal lamp
    sbox(p, 4.55, 4.65, 0.0, 0.88, 0.19, 0.29, "boom")                              # arm rest at the far end
    sbox(p, 4.5, 4.7, 0.88, 0.9, 0.17, 0.31, "dark_steel")
    sbox(p, 4.5, 4.7, 0.0, 0.03, 0.15, 0.33, "concrete")


def boom_arm(p):
    y, z = BOOM_PIV[1], BOOM_PIV[2]
    sbox(p, 0.5, 4.9, y - 0.045, y + 0.045, z - 0.035, z + 0.035, "boom")
    sbox(p, 0.0, 0.5, y - 0.06, y + 0.06, z - 0.045, z + 0.045, "dark_steel")       # counterweight
    for x in (1.5, 2.5, 3.5, 4.5):                                                  # reflectors
        cyl(p, (x, y, z - 0.037), 0.025, 0.004, "glass_red", "glass_red", axis="Z", seg=8)
        cyl(p, (x, y, z + 0.037), 0.025, 0.004, "glass_red", "glass_red", axis="Z", seg=8)
    p.box((2.7, y - 0.17, z), (4.2, 0.012, 0.012), "boom")                          # hanging skirt bar
    for k in range(8):
        x = 0.9 + k * 0.5
        p.box((x, y - 0.11, z), (0.012, 0.12, 0.012), "dark_steel")


# ---- blast gate 3 x 3: two halves slide apart by 1.5 (into the walls)
def blast_static(p):
    sbox(p, 0.0, 3.0, 2.82, 3.0, 0.22, 0.78, "concrete_dark")
    sbox(p, 0.0, 3.0, 2.84, 2.98, 0.215, 0.22, "hazard")
    sbox(p, 0.0, 3.0, 0.0, 0.03, 0.25, 0.75, "steel")                               # floor track
    cyl(p, (1.5, 2.78, 0.2), 0.05, 0.06, "glass_orange", "glass_orange", seg=8)    # warning beacon
    p.box((1.5, 2.8, 0.24), (0.14, 0.04, 0.06), "dark_steel")


def blast_leaf(p, x0, x1, meet_right):
    sbox(p, x0, x1, 0.03, 2.82, 0.3, 0.7, "steel_grey")
    for zz, s in ((0.3, -1), (0.7, 1)):
        for y in (0.5, 1.4, 2.3):
            sbox(p, x0 + 0.05, x1 - 0.05, y - 0.04, y + 0.04, *((zz - 0.03, zz) if s < 0 else (zz, zz + 0.03)), "steel_grey")
        xm = x1 if meet_right else x0
        a, b = (xm - 0.22, xm) if meet_right else (xm, xm + 0.22)
        sbox(p, a, b, 0.03, 2.82, *((zz - 0.004, zz) if s < 0 else (zz, zz + 0.004)), "hazard")
        rivets(p, [(x, y, zz + s * 0.03) for x in (x0 + 0.1, (x0 + x1) / 2, x1 - 0.1) for y in (0.5, 1.4, 2.3)], r=0.016)
    xm = x1 if meet_right else x0
    sbox(p, xm - 0.02 if meet_right else xm, xm if meet_right else xm + 0.02, 0.03, 2.82, 0.32, 0.68, "rubber")


GATES = {
    "sliding": (sliding_static, [("leaf", sliding_leaf)]),
    "swing": (swing_static, [("leaf_l", lambda p: swing_leaf(p, 0.32, 2.0, True)), ("leaf_r", lambda p: swing_leaf(p, 2.0, 3.68, False))]),
    "boom": (boom_static, [("arm", boom_arm)]),
    "blast": (blast_static, [("leaf_l", lambda p: blast_leaf(p, 0.0, 1.5, True)), ("leaf_r", lambda p: blast_leaf(p, 1.5, 3.0, False))]),
}
for nm, (st, parts) in GATES.items():
    build("gate_%s_static" % nm, st)
    for pn, fn in parts:
        build("gate_%s_%s" % (nm, pn), fn)
    build("gate_%s_item" % nm, st, *[fn for _, fn in parts])

# ====================================================================================== CAMOUFLAGE


def net_post(p):
    cyl(p, (0.5, 0.53, 0.5), 0.028, 1.06, "wood", "wood", seg=8)
    ring_torus(p, (0.5, 0.97, 0.5), 0.034, 0.008, "rope", axis="Y", seg=8, sides=4)


def net_side(p):
    """North half of a net screen hung in the plane x = 0.5 (z 0 .. 0.5): top rope + sagging sheet + garland strips."""
    p.tube([(0.5, 0.985, 0.0), (0.5, 0.965, 0.25), (0.5, 0.975, 0.5)], 0.008, "rope", sides=4)
    n = 4
    for i in range(n):                                                # sheet with a slight wave in x
        z0, z1 = 0.5 * i / n, 0.5 * (i + 1) / n
        w0, w1 = math.sin(z0 * TAU * 2) * 0.03, math.sin(z1 * TAU * 2) * 0.03
        for (ya, yb) in ((0.0, 0.5), (0.5, 0.975)):
            quad2(p, (0.5 + w0 * 0.6, ya, z0), (0.5 + w1 * 0.6, ya, z1), (0.5 + w1, yb, z1), (0.5 + w0, yb, z0), "net",
                  ((z0, ya), (z1, ya), (z1, yb), (z0, yb)))
    for (zc, dx, y0, y1) in ((0.12, 0.035, 0.25, 0.95), (0.33, -0.04, 0.1, 0.9), (0.42, 0.045, 0.4, 0.97)):   # garland layers
        quad2(p, (0.5 + dx, y0, zc - 0.09), (0.5 + dx, y0, zc + 0.09), (0.5 + dx * 0.4, y1, zc + 0.09), (0.5 + dx * 0.4, y1, zc - 0.09),
              "net", ((0.6, y0), (0.78, y0), (0.78, y1), (0.6, y1)))


def canopy_center(p):
    """Draped sheet: edges at y = 0.93, sagging to 0.82 in the middle; a second garland layer for depth."""
    n = 4
    hgt = lambda x, z: 0.93 - 0.11 * math.sin(math.pi * x) * math.sin(math.pi * z)
    for i in range(n):
        for j in range(n):
            x0, x1, z0, z1 = i / n, (i + 1) / n, j / n, (j + 1) / n
            quad2(p, (x0, hgt(x0, z0), z0), (x1, hgt(x1, z0), z0), (x1, hgt(x1, z1), z1), (x0, hgt(x0, z1), z1), "net",
                  ((x0, z0), (x1, z0), (x1, z1), (x0, z1)))
    for (cx, cz, s) in ((0.3, 0.35, 0.22), (0.7, 0.6, 0.2), (0.45, 0.78, 0.16)):
        y = hgt(cx, cz) + 0.025
        quad2(p, (cx - s, y, cz - s), (cx + s, y + 0.01, cz - s), (cx + s, y, cz + s), (cx - s, y - 0.01, cz + s), "net",
              ((0.1, 0.1), (0.45, 0.1), (0.45, 0.45), (0.1, 0.45)))


def canopy_fringe(p):
    """North edge skirt hanging from the sheet edge (z = 0.01), jagged by the net alpha."""
    for (x0, x1) in ((0.0, 0.5), (0.5, 1.0)):
        quad2(p, (x0, 0.45, 0.012), (x1, 0.40, 0.012), (x1, 0.93, 0.0), (x0, 0.93, 0.0), "net", ((x0, 0.0), (x1, 0.0), (x1, 0.55), (x0, 0.55)))


def bush(p):
    """Artificial camouflage bush: leaf cards in three clumps (crossed planes at varying tilt), twig stems."""
    import random as _r
    rnd = _r.Random(16)
    for (cx, cz, r, h) in ((0.5, 0.5, 0.48, 0.95), (0.3, 0.62, 0.32, 0.7), (0.7, 0.36, 0.3, 0.62)):
        for k in range(5):
            a = math.pi * k / 5 + rnd.uniform(-0.15, 0.15)
            dx, dz = math.cos(a) * r, math.sin(a) * r
            tilt = rnd.uniform(-0.12, 0.12)
            quad2(p, (cx - dx, 0.0, cz - dz), (cx + dx, 0.0, cz + dz), (cx + dx + tilt, h, cz + dz), (cx - dx + tilt, h, cz - dz), "leaf")
        for k in range(3):                                            # horizontal-ish cards make the crown dense from above
            y = h * rnd.uniform(0.55, 0.9)
            s = r * 0.8
            a = rnd.uniform(0, math.pi)
            c, s_ = math.cos(a) * s, math.sin(a) * s
            quad2(p, (cx - c, y, cz - s_), (cx + s_, y + 0.05, cz - c), (cx + c, y, cz + s_), (cx - s_, y - 0.05, cz + c), "leaf")


build("net_post", net_post)
build("net_side", net_side)
build("canopy_center", canopy_center)
build("canopy_fringe", canopy_fringe)
build("bush", bush)

# net item: post + both halves (second half = side rotated 180 about the block centre)
p = Piece("net_item")
net_post(p)
net_side(p)
n0 = len(p.bm.verts)
net_side(p)
for v in list(p.bm.verts)[n0:]:
    v.co.z = 1 - v.co.z
    v.co.x = 1 - v.co.x
done(p)
p = Piece("canopy_item")
canopy_center(p)
for k in range(4):
    n0 = len(p.bm.verts)
    canopy_fringe(p)
    for v in list(p.bm.verts)[n0:]:
        x, z = v.co.x - 0.5, v.co.z - 0.5
        for _ in range(k):
            x, z = -z, x
        v.co.x, v.co.z = x + 0.5, z + 0.5
done(p)

# ------------------------------------------------------------------ export
bpy.context.view_layer.update()
stats, used = {}, set()
for name in EXPORT:
    ob = bpy.data.objects[name]
    for o in bpy.data.objects:
        o.select_set(o == ob)
    bpy.context.view_layer.objects.active = ob
    path = os.path.join(OUT, name + ".obj")
    bpy.ops.wm.obj_export(filepath=path, export_selected_objects=True, export_triangulated_mesh=True, export_materials=True,
                          export_uv=True, export_normals=True, forward_axis="NEGATIVE_Z", up_axis="Y", apply_modifiers=True,
                          export_animation=False)
    mtl = path[:-4] + ".mtl"
    if os.path.exists(mtl):
        os.remove(mtl)
    stats[name] = sum(len(pp.vertices) - 2 for pp in ob.data.polygons)
    for pp in ob.data.polygons:
        used.add(ob.data.materials[pp.material_index].name)
tex = {}
for k in ORDER:
    if k in used:
        img = next(n.image for n in MATS[k].node_tree.nodes if n.type == "TEX_IMAGE")
        tex[k] = os.path.basename(img.filepath)[:-4]
json.dump(tex, open(os.path.join(OUT, "door_texmap.json"), "w"), indent=1)
with open(os.path.join(OUT, "door.mtl"), "w") as f:
    for k in ORDER:
        if k in used:
            f.write("newmtl %s\nKa 0.0 0.0 0.0\nKd 1.0 1.0 1.0\nKs 0.0 0.0 0.0\nd 1.0\nmap_Kd #%s\n\n" % (k, k))
print("DOOR_TRIS", stats)
print("DOOR_ORIENT", ORIENT_STATS[-5:])
bpy.ops.wm.save_as_mainfile(filepath=ROOT + "/models/door_blocks.blend", compress=True)
print("DOOR_DONE", len(stats))
