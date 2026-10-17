"""r14 building blocks part 2 (Blender 5.x headless):
   blender -b --factory-startup --python tools/blender/deco_blocks.py
Flagpole + flag fittings + flag cloth grid, lamps (hurricane lantern standing / hanging, caged bulkhead lamp, street
light, tripod floodlight, red obstruction light, rotating warning beacon + its reflector), searchlight (pedestal,
turning yoke, tilting drum, combined item model). Same block-space conventions and OBJ export as fortifications.py /
field_blocks.py (1 unit = 1 block, origin = block corner, +Y up, model front = NORTH = -Z), real dimensions in metres.
Saves models/deco_blocks.blend (textures packed)."""
import bpy, bmesh, math, os, random
from mathutils import Vector, Matrix

_SRC = open("/home/user/tools/blender/field_blocks.py").read()
exec(_SRC[:_SRC.index("# ================================================================== TRENCH")])
OUT = RES + "/models/block/deco"
os.makedirs(OUT, exist_ok=True)
for k, png, a in [("pole", "deco_pole.png", False), ("brass", "deco_brass.png", False), ("rope", "deco_rope.png", False),
                  ("paint_green", "deco_paint_green.png", False), ("paint_grey", "deco_paint_grey.png", False),
                  ("paint_olive", "deco_paint_olive.png", False), ("black", "deco_black.png", False),
                  ("chrome", "deco_chrome.png", False), ("glass", "deco_glass.png", True), ("glass_red", "deco_glass_red.png", True),
                  ("glass_orange", "deco_glass_orange.png", True), ("frost", "deco_frost.png", False), ("led", "deco_led.png", False),
                  ("flame", "deco_flame.png", True), ("lens", "deco_lens.png", False), ("fins", "deco_fins.png", False),
                  ("cloth", "../entity/flag/russia.png", False)]:
    mat(k, png, a)
ORDER = list(MATS.keys())


def disc_y(p, c, r, matkey, up=True, seg=12):
    """Flat disc facing +Y (or -Y), radial UV."""
    c = Vector(c)
    ring = [tuple(c + Vector((math.cos(TAU * k / seg) * r, 0, math.sin(TAU * k / seg) * r))) for k in range(seg)]
    idx = tuple(range(seg - 1, -1, -1)) if up else tuple(range(seg))
    uv = [(0.5 + 0.5 * math.cos(TAU * k / seg), 0.5 + 0.5 * math.sin(TAU * k / seg)) for k in range(seg)]
    faces_uv(p, ring, [idx], [[uv[i] for i in idx]], matkey)


def lathe(p, c, prof, matkey, seg=12, axis="Y"):
    """Surface of revolution: prof = [(r, h)] bottom -> top around axis through c (open ends: add discs if needed)."""
    c = Vector(c)
    rings = []
    for (r, h) in prof:
        ring = []
        for k in range(seg):
            a = TAU * k / seg
            if axis == "Y":
                ring.append(tuple(c + Vector((math.cos(a) * r, h, math.sin(a) * r))))
            elif axis == "Z":
                ring.append(tuple(c + Vector((math.cos(a) * r, math.sin(a) * r, h))))
            else:
                ring.append(tuple(c + Vector((h, math.cos(a) * r, math.sin(a) * r))))
        rings.append(ring)
    verts = [v for ring in rings for v in ring]
    faces, uvs = [], []
    n = len(prof)
    for i in range(n - 1):
        for k in range(seg):
            j = (k + 1) % seg
            faces.append((i * seg + k, (i + 1) * seg + k, (i + 1) * seg + j, i * seg + j))
            u0, u1 = k / seg, (k + 1) / seg
            v0, v1 = i / (n - 1), (i + 1) / (n - 1)
            uvs.append([(u0, v0), (u0, v1), (u1, v1), (u1, v0)])
    faces_uv(p, verts, faces, uvs, matkey)


def arc_pts(c, R, a0, a1, n, plane="XY"):
    c = Vector(c)
    out = []
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        if plane == "XY":
            out.append(tuple(c + Vector((math.cos(a) * R, math.sin(a) * R, 0))))
        elif plane == "ZY":
            out.append(tuple(c + Vector((0, math.sin(a) * R, math.cos(a) * R))))
        else:
            out.append(tuple(c + Vector((math.cos(a) * R, 0, math.sin(a) * R))))
    return out


def chain(p, x, z, y0, y1, link=0.05, r=0.008):
    y = y1
    k = 0
    while y - link > y0 - 1e-6:
        axis = "X" if k % 2 == 0 else "Z"
        c = (x, y - link / 2, z)
        pts = []
        for i in range(9):
            a = TAU * i / 8
            dy, dh = math.sin(a) * link / 2, math.cos(a) * link * 0.28
            pts.append((c[0] + (dh if axis == "X" else 0), c[1] + dy, c[2] + (dh if axis == "Z" else 0)))
        p.tube(pts, r, "black", sides=4)
        y -= link * 0.8
        k += 1


# ================================================================== FLAGPOLE (galvanised tube 60 mm, MC-readable 100 mm)
cf = coll("Flag")
POLE_R = 0.05

p = Piece("flagpole_pole")
cyl(p, (0.5, 0.5, 0.5), POLE_R, 1.0, "pole", "pole", seg=10, caps=(False, False))
done(p)

p = Piece("flagpole_finial")                      # truck (pulley housing) + gilded ball
cyl(p, (0.5, 1.015, 0.5), 0.062, 0.03, "pole", "pole", seg=10)
lathe(p, (0.5, 1.03, 0.5), [(0.0, 0.0), (0.03, 0.004), (0.058, 0.03), (0.07, 0.07), (0.058, 0.11), (0.03, 0.136), (0.0, 0.14)], "brass", seg=12)
done(p)

p = Piece("flagpole_base")                        # concrete footing, base flange, 4 anchor bolts, gusset ring
p.box((0.5, 0.05, 0.5), (0.42, 0.10, 0.42), "concrete", taper=(0.9, 0.9))
cyl(p, (0.5, 0.108, 0.5), 0.12, 0.016, "steel", "steel", seg=12)
cyl(p, (0.5, 0.16, 0.5), 0.07, 0.09, "pole", "pole", seg=10, r2=POLE_R)
for sx in (-1, 1):
    for sz in (-1, 1):
        cyl(p, (0.5 + sx * 0.085, 0.124, 0.5 + sz * 0.085), 0.012, 0.022, "steel", "steel", seg=6)
done(p)

p = Piece("flag_fittings")                        # halyard (two runs), snap hooks, cleat; cloth flies NORTH (-Z)
for dx in (-0.012, 0.012):
    p.tube([(0.5 + dx, 0.0, 0.5 - POLE_R - 0.012), (0.5 + dx, 1.0, 0.5 - POLE_R - 0.012)], 0.0045, "rope", sides=4)
for y in (0.95, 0.05):
    ring_torus(p, (0.5, y, 0.5 - POLE_R - 0.02), 0.018, 0.005, "steel", axis="X", seg=8, sides=4)
p.box((0.5 + POLE_R + 0.01, 0.30, 0.5), (0.02, 0.10, 0.03), "steel")                          # cleat
p.box((0.5 + POLE_R + 0.025, 0.30, 0.5), (0.012, 0.012, 0.11), "steel")
done(p)

# cloth grid: 1.5 x 1.0 (3:2), 16 x 10 quads, local frame: u along the fly (-Z), v down; hoist at the origin.
# The renderer animates this grid; geometry + UVs come from here.
p = Piece("flag_cloth")
NU, NV, CL, CH = 16, 10, 1.5, 1.0
verts, faces, uvs = [], [], []
for j in range(NV + 1):
    for i in range(NU + 1):
        verts.append((0.0, -CH * j / NV, -CL * i / NU))
for j in range(NV):
    for i in range(NU):
        a = j * (NU + 1) + i
        faces.append((a, a + 1, a + NU + 2, a + NU + 1))
        uvs.append([(i / NU, 1 - j / NV), ((i + 1) / NU, 1 - j / NV), ((i + 1) / NU, 1 - (j + 1) / NV), (i / NU, 1 - (j + 1) / NV)])
faces_uv(p, verts, faces, uvs, "cloth")
_orient = orient_for_mc
orient_for_mc = lambda bm, uvscale=None: None    # keep the grid one-sided as modelled (renderer draws it NoCull)
p.finish()
orient_for_mc = _orient
EXPORT.append("flag_cloth")

# ================================================================== HURRICANE LANTERN ("летучая мышь"), 0.38 m tall
cl = coll("Lamps")


def hurricane(p, y0):
    c = (0.5, y0, 0.5)
    lathe(p, (0.5, y0, 0.5), [(0.0, 0.0), (0.105, 0.0), (0.11, 0.012), (0.11, 0.05), (0.095, 0.072), (0.06, 0.085), (0.0, 0.088)], "paint_green", seg=14)
    lathe(p, (0.5, y0, 0.5), [(0.045, 0.085), (0.072, 0.13), (0.085, 0.19), (0.078, 0.24), (0.055, 0.28)], "glass", seg=14)   # globe
    for a in (0, 90):                                                                  # flame: crossed quads
        r = math.radians(a)
        dx, dz = math.cos(r) * 0.022, math.sin(r) * 0.022
        quad_uv(p, (0.5 - dx, y0 + 0.10, 0.5 - dz), (0.5 + dx, y0 + 0.10, 0.5 + dz), (0.5 + dx, y0 + 0.175, 0.5 + dz),
                (0.5 - dx, y0 + 0.175, 0.5 - dz), "flame", ((0, 0), (1, 0), (1, 1), (0, 1)))
    cyl(p, (0.5, y0 + 0.094, 0.5), 0.016, 0.014, "brass", "brass", seg=8)                    # burner
    for k in range(4):                                                                 # guard wires
        a = TAU * (k + 0.5) / 4
        x, z = 0.5 + math.cos(a) * 0.093, 0.5 + math.sin(a) * 0.093
        p.tube([(x, y0 + 0.08, z), (0.5 + math.cos(a) * 0.101, y0 + 0.19, 0.5 + math.sin(a) * 0.101), (0.5 + math.cos(a) * 0.07, y0 + 0.285, 0.5 + math.sin(a) * 0.07)], 0.005, "paint_green", sides=4)
    ring_torus(p, (0.5, y0 + 0.19, 0.5), 0.101, 0.005, "paint_green", seg=14, sides=4)
    lathe(p, (0.5, y0, 0.5), [(0.0, 0.28), (0.085, 0.282), (0.085, 0.30), (0.05, 0.325), (0.03, 0.345), (0.0, 0.347)], "paint_green", seg=14)   # cap + chimney
    for s in (-1, 1):                                                                  # side air tubes
        p.tube([(0.5 + s * 0.112, y0 + 0.05, 0.5), (0.5 + s * 0.112, y0 + 0.30, 0.5), (0.5 + s * 0.06, y0 + 0.335, 0.5)], 0.011, "paint_green", sides=6)
    pts = arc_pts((0.5, y0 + 0.30, 0.5), 0.118, 0, 180, 12, "XY")                      # bail handle
    p.tube(pts, 0.006, "steel", sides=4)


p = Piece("lantern_hurricane")
hurricane(p, 0.0)
done(p)

p = Piece("lantern_hurricane_hanging")
hurricane(p, 0.42)
p.box((0.5, 0.985, 0.5), (0.10, 0.03, 0.10), "paint_green")                         # ceiling hook plate
chain(p, 0.5, 0.5, 0.42 + 0.418, 0.97)
done(p)

# ================================================================== CAGED BULKHEAD LAMP (mounted on the floor, faces UP)
def bulkhead(p, frost="frost"):
    lathe(p, (0.5, 0.0, 0.5), [(0.0, 0.0), (0.16, 0.0), (0.165, 0.012), (0.155, 0.04), (0.0, 0.042)], "paint_grey", seg=16)
    lathe(p, (0.5, 0.0, 0.5), [(0.128, 0.04), (0.13, 0.075), (0.118, 0.11), (0.085, 0.135), (0.0, 0.142)], frost, seg=16)
    ring_torus(p, (0.5, 0.045, 0.5), 0.136, 0.009, "paint_grey", seg=16, sides=4)
    for k in range(3):                                                                 # cage bows
        a = math.radians(k * 60)
        pts = []
        for i in range(13):
            t = math.radians(i * 15)
            rr = math.cos(t) * 0.146
            pts.append((0.5 + math.cos(a) * rr, 0.045 + math.sin(t) * 0.112, 0.5 + math.sin(a) * rr))
        p.tube(pts, 0.007, "paint_grey", sides=4)
    ring_torus(p, (0.5, 0.11, 0.5), 0.108, 0.006, "paint_grey", seg=16, sides=4)
    for k in range(4):
        a = TAU * (k + 0.5) / 4
        cyl(p, (0.5 + math.cos(a) * 0.15, 0.012, 0.5 + math.sin(a) * 0.15), 0.012, 0.008, "steel", "steel", seg=6)


p = Piece("lamp_bulkhead")
bulkhead(p)
done(p)

# ================================================================== STREET LIGHT ("cobra" head on a wall bracket), wall = SOUTH
p = Piece("lamp_street")
p.box((0.5, 0.42, 0.985), (0.16, 0.26, 0.03), "paint_grey")                          # wall plate
for y in (0.32, 0.52):
    cyl(p, (0.5, y, 0.968), 0.012, 0.01, "steel", "steel", axis="Z", seg=6)
pts = [(0.5, 0.42, 0.97), (0.5, 0.47, 0.82), (0.5, 0.56, 0.64), (0.5, 0.62, 0.50), (0.5, 0.645, 0.40)]
p.tube(pts, 0.024, "paint_grey", sides=8)
p.tube([(0.5, 0.42, 0.95), (0.5, 0.58, 0.95), (0.5, 0.62, 0.62)], 0.012, "paint_grey", sides=6)   # brace
head_prof = [(0.0, 0.40), (0.06, 0.39), (0.10, 0.34), (0.11, 0.20), (0.10, 0.10), (0.06, 0.04), (0.0, 0.03)]
# cobra head: lathe around Z, squashed vertically by building it as stacked ellipses
verts, faces, uvs = [], [], []
seg = 14
for i, (r, z) in enumerate(head_prof):
    for k in range(seg):
        a = TAU * k / seg
        verts.append((0.5 + math.cos(a) * r * 1.0, 0.655 + math.sin(a) * r * 0.42 + (0.012 if math.sin(a) > 0 else 0), z))
for i in range(len(head_prof) - 1):
    for k in range(seg):
        j = (k + 1) % seg
        faces.append((i * seg + k, i * seg + j, (i + 1) * seg + j, (i + 1) * seg + k))
        uvs.append([(k / seg, i / 6), ((k + 1) / seg, i / 6), ((k + 1) / seg, (i + 1) / 6), (k / seg, (i + 1) / 6)])
faces_uv(p, verts, faces, uvs, "paint_grey")
quad_uv(p, (0.43, 0.603, 0.31), (0.57, 0.603, 0.31), (0.57, 0.603, 0.11), (0.43, 0.603, 0.11), "led")    # lens panel (down)
done(p)

# ================================================================== TRIPOD FLOODLIGHT (LED work light), shines NORTH
p = Piece("lamp_flood")
hub = Vector((0.5, 0.66, 0.5))
for k in range(3):
    a = TAU * k / 3 + math.pi / 2
    foot = Vector((0.5 + math.cos(a) * 0.36, 0.0, 0.5 + math.sin(a) * 0.36))
    p.tube([tuple(foot), tuple(hub)], 0.014, "paint_grey", sides=6)
    p.box(tuple(foot + Vector((0, 0.008, 0))), (0.05, 0.016, 0.05), "rubber")
cyl(p, (0.5, 0.76, 0.5), 0.016, 0.24, "steel", "steel", seg=8)
p.tube(arc_pts((0.5, 0.86, 0.5), 0.2, 180, 360, 10, "XY"), 0.01, "paint_grey", sides=4)   # yoke (under the housing)
tilt = Matrix.Rotation(math.radians(-12), 3, "X")
cH = Vector((0.5, 0.86, 0.5))
p.box(tuple(cH), (0.34, 0.24, 0.07), "paint_grey", rot=tilt)
fr = cH + tilt @ Vector((0, 0, -0.0375))
a_ = [tuple(fr + tilt @ Vector(v)) for v in ((0.15, -0.10, 0), (-0.15, -0.10, 0), (-0.15, 0.10, 0), (0.15, 0.10, 0))]
quad_uv(p, a_[0], a_[1], a_[2], a_[3], "led")
for i in range(7):                                                                     # rear cooling fins
    x = -0.15 + i * 0.05
    p.box(tuple(cH + tilt @ Vector((x, 0, 0.055))), (0.008, 0.22, 0.04), "paint_grey", rot=tilt)
p.box(tuple(cH + tilt @ Vector((0, 0.135, 0.0))), (0.12, 0.02, 0.02), "black", rot=tilt)  # carry handle
done(p)

# ================================================================== RED OBSTRUCTION LIGHT (glow animated by the renderer)
p = Piece("beacon_red")
lathe(p, (0.5, 0.0, 0.5), [(0.0, 0.0), (0.08, 0.0), (0.08, 0.05), (0.06, 0.06), (0.0, 0.061)], "paint_grey", seg=12)
lathe(p, (0.5, 0.0, 0.5), [(0.055, 0.058), (0.058, 0.10), (0.045, 0.14), (0.02, 0.158), (0.0, 0.16)], "glass_red", seg=12)
for k in range(4):
    a = TAU * k / 4
    p.tube([(0.5 + math.cos(a) * 0.06, 0.06, 0.5 + math.sin(a) * 0.06), (0.5 + math.cos(a) * 0.062, 0.11, 0.5 + math.sin(a) * 0.062),
            (0.5, 0.17, 0.5)], 0.004, "paint_grey", sides=4)
done(p)

# ================================================================== ROTATING WARNING BEACON (orange dome; reflector spun by the renderer)
p = Piece("beacon_rotating")
lathe(p, (0.5, 0.0, 0.5), [(0.0, 0.0), (0.12, 0.0), (0.125, 0.012), (0.12, 0.06), (0.0, 0.062)], "black", seg=16)
lathe(p, (0.5, 0.0, 0.5), [(0.105, 0.06), (0.105, 0.18), (0.095, 0.215), (0.06, 0.235), (0.0, 0.24)], "glass_orange", seg=16)
cyl(p, (0.5, 0.075, 0.5), 0.02, 0.03, "steel", "steel", seg=8)                       # motor spindle
done(p)

p = Piece("beacon_reflector")                     # half-parabolic mirror + bulb, spins about (0.5, *, 0.5)
seg = 8
faces, uvs = [], []
verts = []
for i in range(6):                                # dish: rows along the radius, columns across a half circle (YZ plane)
    rr = 0.015 + 0.075 * (i / 5)
    back = 0.045 * (i / 5) ** 2
    for k in range(seg + 1):
        a = math.pi * k / seg
        verts.append((0.5 + math.cos(a) * rr, 0.135 + math.sin(a) * rr * 0.75 - rr * 0.375, 0.5 + 0.05 - back))
for i in range(5):
    for k in range(seg):
        a0 = i * (seg + 1) + k
        faces.append((a0, a0 + 1, a0 + seg + 2, a0 + seg + 1))
        uvs.append([(k / seg, i / 5), ((k + 1) / seg, i / 5), ((k + 1) / seg, (i + 1) / 5), (k / seg, (i + 1) / 5)])
faces_uv(p, verts, faces, uvs, "chrome")
lathe(p, (0.5, 0.09, 0.5), [(0.0, 0.0), (0.018, 0.005), (0.022, 0.03), (0.016, 0.06), (0.0, 0.066)], "frost", seg=8)   # bulb
done(p)

# ================================================================== SEARCHLIGHT (Soviet 60-cm pattern), lens faces NORTH
cs_ = coll("Searchlight")
PIV = Vector((0.5, 1.02, 0.5))                    # tilt pivot (trunnion axis, along X)


def sl_base(p):
    p.box((0.5, 0.03, 0.5), (0.86, 0.06, 0.86), "paint_olive")
    for sx in (-1, 1):
        for sz in (-1, 1):
            p.box((0.5 + sx * 0.38, 0.015, 0.5 + sz * 0.38), (0.1, 0.03, 0.1), "rubber")
            cyl(p, (0.5 + sx * 0.33, 0.07, 0.5 + sz * 0.33), 0.015, 0.02, "steel", "steel", seg=6)
    lathe(p, (0.5, 0.0, 0.5), [(0.18, 0.06), (0.16, 0.10), (0.12, 0.13), (0.11, 0.40), (0.15, 0.43), (0.21, 0.44), (0.21, 0.47), (0.0, 0.471)], "paint_olive", seg=16)
    for k in range(4):                                                                 # column gussets
        a = TAU * k / 4 + math.pi / 4
        p.box((0.5 + math.cos(a) * 0.16, 0.12, 0.5 + math.sin(a) * 0.16), (0.02, 0.12, 0.12), "paint_olive", rot=Matrix.Rotation(-a, 3, "Y"))
    p.box((0.5, 0.22, 0.385), (0.10, 0.12, 0.04), "black")                            # junction box + cable
    p.tube([(0.5, 0.16, 0.37), (0.5, 0.08, 0.30), (0.5, 0.065, 0.10), (0.5, 0.065, 0.0)], 0.014, "rubber", sides=6)


def sl_yoke(p):
    cyl(p, (0.5, 0.49, 0.5), 0.2, 0.04, "paint_olive", "paint_olive", seg=16)        # turntable
    for s in (-1, 1):                                                                  # fork arms
        x = 0.5 + s * 0.44
        p.prism([(0.42, 0.50), (0.58, 0.50), (0.55, 1.07), (0.45, 1.07)], x - 0.025, x + 0.025, "paint_olive")
        cyl(p, (x, PIV.y, 0.5), 0.05, 0.07, "paint_olive", "steel", axis="X", seg=12)  # trunnion hubs
    p.box((0.5, 0.53, 0.5), (0.92, 0.05, 0.14), "paint_olive")                        # cross beam
    x = 0.5 + 0.49                                                                     # elevation hand wheel
    ring_torus(p, (x + 0.01, 0.80, 0.5), 0.07, 0.008, "black", axis="X", seg=14, sides=4)
    for k in range(3):
        a = TAU * k / 3
        p.tube([(x + 0.01, 0.80, 0.5), (x + 0.01, 0.80 + math.sin(a) * 0.07, 0.5 + math.cos(a) * 0.07)], 0.005, "black", sides=4)
    cyl(p, (x - 0.01, 0.80, 0.5), 0.015, 0.05, "steel", "steel", axis="X", seg=6)
    p.tube([(0.5 - 0.47, 0.62, 0.58), (0.5 - 0.47, 0.62, 0.70)], 0.012, "black", sides=6)   # traverse handle
    cyl(p, (0.5 - 0.47, 0.62, 0.72), 0.02, 0.05, "rubber", "rubber", axis="Z", seg=8)


def sl_drum(p, lens="lens"):
    R, L = 0.34, 0.66
    zf, zb = 0.5 - L / 2 - 0.02, 0.5 + L / 2 - 0.02
    cyl(p, (0.5, PIV.y, (zf + zb) / 2), R, L, "paint_olive", lens, axis="Z", seg=20, caps=(True, False))
    ring_torus(p, (0.5, PIV.y, zf - 0.005), R + 0.01, 0.022, "paint_olive", axis="Z", seg=20, sides=6)      # bezel
    for k in range(8):                                                                 # bezel clamps
        a = TAU * k / 8
        p.box((0.5 + math.cos(a) * (R + 0.02), PIV.y + math.sin(a) * (R + 0.02), zf + 0.02), (0.035, 0.035, 0.05), "steel",
              rot=Matrix.Rotation(a, 3, "Z"))
    for i in range(5):                                                                 # rear cooling rings
        ring_torus(p, (0.5, PIV.y, zb - 0.22 + i * 0.05), R + 0.012, 0.012, "fins", axis="Z", seg=20, sides=4)
    lathe(p, (0.5, PIV.y, 0.0), [(R, zb), (R * 0.92, zb + 0.06), (R * 0.62, zb + 0.10), (0.20, zb + 0.11), (0.20, zb + 0.24), (0.16, zb + 0.27), (0.0, zb + 0.275)],
          "paint_olive", seg=20, axis="Z")                                             # rear dome + lamp house
    for i in range(4):                                                                 # vent louvres on the lamp house
        p.box((0.5, PIV.y + 0.205, zb + 0.13 + i * 0.03), (0.16, 0.012, 0.012), "black")
    for s in (-1, 1):                                                                  # side handles
        x = 0.5 + s * (R + 0.04)
        p.tube([(x - s * 0.04, PIV.y + 0.08, 0.30), (x, PIV.y + 0.08, 0.32), (x, PIV.y + 0.08, 0.62), (x - s * 0.04, PIV.y + 0.08, 0.64)], 0.011, "steel", sides=6)
        cyl(p, (0.5 + s * (R + 0.015), PIV.y, 0.5), 0.045, 0.05, "paint_olive", "steel", axis="X", seg=10)   # trunnion pins
    p.tube([(0.44, PIV.y + R, 0.36), (0.44, PIV.y + R + 0.07, 0.40), (0.44, PIV.y + R + 0.07, 0.58), (0.44, PIV.y + R, 0.62)], 0.012, "steel", sides=6)
    p.tube([(0.56, PIV.y + R, 0.36), (0.56, PIV.y + R + 0.07, 0.40), (0.56, PIV.y + R + 0.07, 0.58), (0.56, PIV.y + R, 0.62)], 0.012, "steel", sides=6)
    p.box((0.5, PIV.y + R + 0.07, 0.49), (0.16, 0.02, 0.06), "black")                  # carry handle grip
    cyl(p, (0.5 + 0.22, PIV.y + R - 0.02, 0.42), 0.025, 0.26, "black", "glass", axis="Z", seg=8)   # spotting scope
    p.box((0.5 + 0.22, PIV.y + R - 0.04, 0.42), (0.03, 0.05, 0.05), "paint_olive")


p = Piece("searchlight_base")
sl_base(p)
done(p)
p = Piece("searchlight_yoke")
sl_yoke(p)
done(p)
p = Piece("searchlight_drum")
sl_drum(p)
done(p)
p = Piece("searchlight_item")
sl_base(p); sl_yoke(p); sl_drum(p)
done(p)

# ------------------------------------------------------------------ export (same exporter settings as field_blocks.py)
for me in list(bpy.data.meshes):
    if me.name.startswith("_") and me.users == 0:
        bpy.data.meshes.remove(me)
bpy.context.view_layer.update()
stats = {}
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
# Forge OBJ: with emissive_ambient the ambient colour Ka becomes a baked light level (Ka 1 = full bright, no shading),
# so normal surfaces get Ka 0 and only the glowing parts of switched-on lamps get Ka 1 (deco_lit.mtl).
GLOW_MATS = {"flame", "frost", "led", "lens", "glass", "glass_red", "glass_orange"}
for fn, glow in (("deco.mtl", set()), ("deco_lit.mtl", GLOW_MATS)):
    with open(os.path.join(OUT, fn), "w") as f:
        for k in ORDER:
            ka = "1.0 1.0 1.0" if k in glow else "0.0 0.0 0.0"
            f.write("newmtl %s\nKa %s\nKd 1.0 1.0 1.0\nKs 0.0 0.0 0.0\nd 1.0\nmap_Kd #%s\n\n" % (k, ka, k))
print("DECO_TRIS", stats)
print("DECO_ORIENT", ORIENT_STATS)
bpy.ops.wm.save_as_mainfile(filepath=ROOT + "/models/deco_blocks.blend", compress=True)
print("DECO_DONE")
