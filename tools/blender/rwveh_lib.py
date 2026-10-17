# rwveh_lib.py — shared builders for the Real War vehicles (appended after lib.py + alib.py + arsenal_lib.py).
# Conventions: Blender metres, Z up, vehicle front towards -Y, +X = vehicle LEFT, origin = ground centre.
# glTF <-> Blender:  B(x, y, z) = (x, -z_gltf, y_gltf).  All RW specs are written in glTF numbers, hence gB().
# Node contract (VehicleEntity.applyCommonPose): wheel_i (spin X, steer Y), steering_wheel (rot local Z),
# needle_speed/rpm/fuel/temp (rot local Z), jack_j (translate -Y).
import bpy, bmesh, math
from mathutils import Vector, Matrix

PI = math.pi


def gB(x, y, z):
    """glTF point -> Blender point."""
    return Vector((x, -z, y))


def rw_mats():
    ars_common_mats()
    amat("M_Body", "T_CamoGreen", 0.72, 0.05, factor=(0.80, 0.86, 0.74, 1))
    amat("M_Plain", None, 0.7, 0.05, factor=(0.21, 0.26, 0.16, 1))
    amat("M_DarkGreen", None, 0.65, 0.08, factor=(0.16, 0.20, 0.12, 1))
    amat("M_Rubber", None, 0.9, 0.0, factor=(0.05, 0.05, 0.05, 1))
    amat("M_CabGlass", None, 0.05, 0.3, factor=(0.10, 0.14, 0.16, 1), glass_alpha=0.45)
    amat("M_Chrome", None, 0.2, 1.0, factor=(0.8, 0.8, 0.82, 1))
    amat("M_HeadLamp", None, 0.1, 0.0, factor=(1.0, 0.97, 0.85, 1), emissive=(0.9, 0.85, 0.7, 1))
    amat("M_Amber", None, 0.3, 0.0, factor=(1.0, 0.55, 0.05, 1), emissive=(0.8, 0.4, 0.02, 1))
    amat("M_Hydraulic", None, 0.15, 1.0, factor=(0.85, 0.86, 0.88, 1))
    amat("M_Stencil", None, 0.6, 0.0, factor=(0.85, 0.85, 0.78, 1))
    amat("M_Seat", None, 0.8, 0.0, factor=(0.16, 0.13, 0.10, 1))
    amat("M_Interior", None, 0.75, 0.0, factor=(0.30, 0.34, 0.30, 1))
    amat("M_Dash", None, 0.6, 0.1, factor=(0.08, 0.08, 0.085, 1))
    amat("M_Gauge", None, 0.4, 0.0, factor=(0.85, 0.85, 0.80, 1), emissive=(0.25, 0.25, 0.2, 1))
    amat("M_Needle", None, 0.4, 0.0, factor=(1.0, 0.3, 0.05, 1), emissive=(1.0, 0.3, 0.05, 1))
    amat("M_Screen", None, 0.2, 0.0, factor=(0.1, 0.8, 0.3, 1), emissive=(0.1, 0.7, 0.25, 1))
    amat("M_Bore", None, 0.8, 0.2, factor=(0.03, 0.03, 0.03, 1))
    amat("M_Rocket", None, 0.5, 0.2, factor=(0.30, 0.34, 0.26, 1))
    amat("M_Warhead", None, 0.5, 0.2, factor=(0.55, 0.55, 0.50, 1))


def prism_x(name, prof, hw, mat, grp, bevel=0.04, x0=None, uvscale=0.5, dirt=True):
    """Extrude a (y,z) polygon across X (from -hw..hw, or x0..x0+2hw) and bevel all edges."""
    bm = bmesh.new()
    xa, xb = (-hw, hw) if x0 is None else (x0, x0 + 2 * hw)
    va = [bm.verts.new((xa, y, z)) for y, z in prof]
    vb = [bm.verts.new((xb, y, z)) for y, z in prof]
    n = len(prof)
    bm.faces.new(list(reversed(va)))
    bm.faces.new(vb)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((va[i], va[j], vb[j], vb[i]))
    bm.normal_update()
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    if bevel > 0:
        bmesh.ops.bevel(bm, geom=list(bm.edges), offset=bevel, segments=2, affect='EDGES', profile=0.6)
    return aobj(name, bm, [mat], grp, smooth=False, uv="box", uvscale=uvscale, dirt=dirt)


def slats(name, x0, x1, y0, y1, z, n, mat, grp, along='Y', t=0.03, h=0.06, tilt=0.5):
    """Row of louvre slats (engine deck grilles)."""
    for i in range(n):
        f = (i + 0.5) / n
        if along == 'Y':
            abox("%s_%d" % (name, i), (x1 - x0, t, h), ((x0 + x1) / 2, y0 + (y1 - y0) * f, z), mat, grp, rot=(tilt, 0, 0), bevel=0.004)
        else:
            abox("%s_%d" % (name, i), (t, y1 - y0, h), (x0 + (x1 - x0) * f, (y0 + y1) / 2, z), mat, grp, rot=(0, tilt, 0), bevel=0.004)


def arc_shell(name, c, r0, r1, w, a0, a1, mat, grp, seg=14):
    """Curved fender: annular sector around an X axis through c (angles from +Y towards +Z)."""
    bm = bmesh.new()
    rows = []
    for i in range(seg + 1):
        a = a0 + (a1 - a0) * i / seg
        ca, sa = math.cos(a), math.sin(a)
        ring = []
        for x in (-w / 2, w / 2):
            for r in (r0, r1):
                ring.append(bm.verts.new((c[0] + x, c[1] + ca * r, c[2] + sa * r)))
        rows.append(ring)
    for i in range(seg):
        A, Bq = rows[i], rows[i + 1]
        bm.faces.new((A[1], Bq[1], Bq[3], A[3]))      # outer
        bm.faces.new((A[2], Bq[2], Bq[0], A[0]))      # inner
        bm.faces.new((A[0], Bq[0], Bq[1], A[1]))      # side -x
        bm.faces.new((A[3], Bq[3], Bq[2], A[2]))      # side +x
    bm.faces.new((rows[0][0], rows[0][1], rows[0][3], rows[0][2]))
    bm.faces.new((rows[-1][2], rows[-1][3], rows[-1][1], rows[-1][0]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    return aobj(name, bm, [mat], grp, smooth=False, uv="box", uvscale=0.5)


def tilted_box(name, p0, p1, w, t, mat, grp, bevel=0.005, dirt=True):
    """Thin plate between points p0..p1 in a Y-Z plane (x = centre), width w along X, thickness t."""
    p0, p1 = Vector(p0), Vector(p1)
    d = p1 - p0
    c = (p0 + p1) / 2
    ang = math.atan2(d.z, d.y)
    return abox(name, (w, d.length, t), tuple(c), mat, grp, rot=(ang, 0, 0), bevel=bevel, dirt=dirt)


def big_wheel(g, c, WR, WW, sx, mat_rim="M_Plain", lugs=28):
    """Off-road tyre on a dished rim (all parts in group g; spin axis X through c)."""
    prof = [(-WW / 2, 0.45), (-WW / 2 - 0.01, 0.52), (-WW / 2, WR * 0.86), (-WW * 0.40, WR * 0.97), (-WW * 0.2, WR),
            (WW * 0.2, WR), (WW * 0.40, WR * 0.97), (WW / 2, WR * 0.86), (WW / 2 + 0.01, 0.52), (WW / 2, 0.45)]
    lathe_y(g + "_tyre", prof, tuple(c), ["M_Tire"], g, seg=36, axis='X', uv_tile=0.5)
    for k in range(lugs):
        ang = 2 * PI * k / lugs
        for side in (-1, 1):
            off = side * WW * 0.22
            rr = WR + 0.022
            p = c + Vector((off, math.cos(ang) * rr, math.sin(ang) * rr))
            abox("%s_lug%d_%d" % (g, k, side), (WW * 0.36, 0.09, 0.045), tuple(p), "M_Tire", g,
                 rot=(ang + PI / 2, 0, 0.38 * side), bevel=0.008, dirt=False)
    rx = c.x + sx * (WW / 2 - 0.05)
    lathe_y(g + "_rim", [(-0.08, 0.0), (-0.08, 0.20), (-0.02, 0.30), (0.02, 0.43), (0.06, 0.45), (0.06, 0.0)],
            (rx, c.y, c.z), [mat_rim], g, seg=28, axis='X')
    lathe_y(g + "_bead", [(-0.03, 0.43), (-0.03, 0.47), (0.03, 0.47), (0.03, 0.43)], (rx + 0.03 * sx, c.y, c.z), ["M_Steel"], g, seg=28, axis='X')
    acyl(g + "_hub", 0.15, 0.16, (rx + 0.06 * sx, c.y, c.z), "M_Steel", g, axis=0, seg=16)
    acyl(g + "_hubc", 0.09, 0.06, (rx + 0.15 * sx, c.y, c.z), "M_Dark", g, axis=0, seg=12)
    for k in range(10):
        a = 2 * PI * k / 10
        acyl("%s_nut%d" % (g, k), 0.024, 0.05, (rx + 0.08 * sx, c.y + math.cos(a) * 0.21, c.z + math.sin(a) * 0.21), "M_Steel", g, axis=0, seg=6, dirt=False)
    for k in range(6):   # rim ventilation holes (dark discs)
        a = 2 * PI * (k + 0.5) / 6
        acyl("%s_vh%d" % (g, k), 0.05, 0.02, (rx - 0.075 * sx, c.y + math.cos(a) * 0.33, c.z + math.sin(a) * 0.33), "M_Bore", g, axis=0, seg=10, dirt=False)
    rod(g + "_valve", Vector((rx + 0.1 * sx, c.y, c.z + 0.15)), Vector((rx + 0.1 * sx, c.y + 0.1, c.z + 0.3)), 0.012, "M_Black", g, seg=5)


def gauge(name, center, r, grp):
    """Round instrument: bezel + face (+ tick marks) on a dash facing +Y."""
    c = Vector(center)
    acyl(name + "_bz", r + 0.012, 0.03, tuple(c), "M_Chrome", grp, axis=1, seg=20, dirt=False)
    acyl(name + "_fc", r, 0.012, tuple(c + Vector((0, 0.012, 0))), "M_Gauge", grp, axis=1, seg=20, dirt=False)
    for k in range(7):
        a = math.radians(-135 + 45 * k)
        p = c + Vector((math.sin(a) * r * 0.82, 0.02, math.cos(a) * r * 0.82))
        abox("%s_t%d" % (name, k), (0.004, 0.004, r * 0.22), tuple(p), "M_Dash", grp, rot=(0, a, 0), bevel=0.0, dirt=False)


def maz543(P, cfg):
    """MAZ-543M 8x8 chassis with split cabs. Returns dict(G=static group, wheels=[(group, centre)], extra nodes)."""
    G = P + "_body"
    FZ, RZ = cfg["FZ"], cfg["RZ"]
    yF, yR = -FZ, -RZ
    AXY = [-z for z in cfg["AXZ"]]
    WR, WW, TX = cfg["WR"], 0.56, cfg["TX"]
    cb = -cfg["cab_back"]
    zt = cfg["cab_top"]
    zb = 1.56                                  # cab floor underside (above the tyres)
    yC0 = yF + 0.32                            # cab front face
    HW = 1.52
    out = {"G": G, "wheels": [], "jacks": []}

    # ---------------------------------------------------------------- frame, axles, suspension, drive line
    for sx in (1, -1):
        abox(P + "_rail%d" % sx, (0.2, yR - yF - 0.5, 0.40), (0.52 * sx, (yF + yR) / 2 + 0.1, 1.15), "M_Plain", G, bevel=0.02)
        abox(P + "_railf%d" % sx, (0.05, yR - yF - 0.5, 0.28), (0.63 * sx, (yF + yR) / 2 + 0.1, 1.15), "M_Dark", G, bevel=0.005)
    n_x = 9
    for k in range(n_x):
        y = yF + 0.6 + (yR - yF - 1.0) * k / (n_x - 1)
        abox(P + "_xm%d" % k, (1.24, 0.14, 0.24), (0, y, 1.15), "M_Plain", G, bevel=0.01)
    for a, ay in enumerate(AXY):
        abox(P + "_axle%d" % a, (2.0, 0.22, 0.22), (0, ay, WR), "M_Dark", G, bevel=0.02)
        acyl(P + "_diff%d" % a, 0.25, 0.4, (0, ay, WR), "M_Dark", G, axis=1, seg=14)
        for sx in (1, -1):
            rod(P + "_arm%d_%d" % (a, sx), Vector((0.6 * sx, ay - 0.45, 1.02)), Vector((0.95 * sx, ay, WR + 0.05)), 0.06, "M_Dark", G, seg=8)
            rod(P + "_arm2%d_%d" % (a, sx), Vector((0.6 * sx, ay + 0.45, 1.02)), Vector((0.95 * sx, ay, WR + 0.05)), 0.06, "M_Dark", G, seg=8)
            rod(P + "_shk%d_%d" % (a, sx), Vector((0.76 * sx, ay + 0.16, 1.4)), Vector((0.95 * sx, ay + 0.1, WR + 0.12)), 0.05, "M_Steel", G, seg=8)
            rod(P + "_shkb%d_%d" % (a, sx), Vector((0.76 * sx, ay + 0.16, 1.4)), Vector((0.86 * sx, ay + 0.13, 1.1)), 0.07, "M_Black", G, seg=8)
            rod(P + "_tb%d_%d" % (a, sx), Vector((0.45 * sx, ay - 0.6, 1.0)), Vector((0.45 * sx, ay + 0.6, 1.0)), 0.035, "M_Steel", G, seg=6)
    for i in range(len(AXY) - 1):
        rod(P + "_shaft%d" % i, Vector((0, AXY[i] + 0.26, WR + 0.08)), Vector((0, AXY[i + 1] - 0.26, WR + 0.08)), 0.06, "M_Steel", G, seg=8)
    # transfer case + gearbox under the engine
    abox(P + "_gearbox", (0.6, 0.9, 0.5), (0, (AXY[1] + AXY[2]) / 2 - 0.4, 0.95), "M_Dark", G, bevel=0.04)
    abox(P + "_transfer", (0.7, 0.6, 0.45), (0, (AXY[1] + AXY[2]) / 2 + 0.5, 0.9), "M_Dark", G, bevel=0.04)

    # ---------------------------------------------------------------- wheels (RW order: axle-major, left(+x) first)
    for a, ay in enumerate(AXY):
        for sx in (1, -1):
            i = 2 * a + (0 if sx > 0 else 1)
            g = "%s_w%d" % (P, i)
            c = Vector((TX * sx, ay, WR))
            big_wheel(g, c, WR, WW, sx)
            out["wheels"].append((i, g, c))
    out["wheels"].sort()

    # ---------------------------------------------------------------- fenders over every wheel + mud flaps
    for a, ay in enumerate(AXY):
        for sx in (1, -1):
            arc_shell("%s_fend%d_%d" % (P, a, sx), (TX * sx, ay, WR), WR + 0.07, WR + 0.11, WW + 0.12,
                      math.radians(25), math.radians(155), "M_Body", G)
            abox("%s_flap%d_%d" % (P, a, sx), (WW + 0.06, 0.02, 0.38), (TX * sx, ay + WR + 0.1, WR + 0.08), "M_Rubber", G, bevel=0.0)
    # deck stringers joining the fenders (side skirts) + stowage boxes, fuel tanks
    g12 = (AXY[1] + WR + 0.14, AXY[2] - WR - 0.14)
    for sx in (1, -1):
        abox(P + "_tank%d" % sx, (0.42, (g12[1] - g12[0]) * 0.48, 0.5), (1.24 * sx, g12[0] + (g12[1] - g12[0]) * 0.27, 1.12), "M_Plain", G, bevel=0.05)
        for k in range(3):
            acyl("%s_tankb%d_%d" % (P, sx, k), 0.27, 0.03, (1.24 * sx, g12[0] + (g12[1] - g12[0]) * (0.08 + 0.19 * k), 1.12), "M_Dark", G, axis=1, seg=12)
        acyl(P + "_tankcap%d" % sx, 0.06, 0.05, (1.24 * sx, g12[0] + (g12[1] - g12[0]) * 0.27, 1.39), "M_Steel", G, seg=10)
        abox(P + "_box%d" % sx, (0.4, (g12[1] - g12[0]) * 0.40, 0.48), (1.25 * sx, g12[0] + (g12[1] - g12[0]) * 0.74, 1.12), "M_Body", G, bevel=0.03)
        for k in range(2):
            abox("%s_boxl%d_%d" % (P, sx, k), (0.02, 0.08, 0.05), (1.46 * sx, g12[0] + (g12[1] - g12[0]) * (0.64 + 0.2 * k), 1.27), "M_Steel", G, bevel=0.0)
        abox(P + "_skirt%d" % sx, (0.08, g12[1] - g12[0] + 0.3, 0.12), (1.47 * sx, (g12[0] + g12[1]) / 2, 1.42), "M_Body", G, bevel=0.01)
        for k in range(3):   # side steps under the doors
            abox("%s_step%d_%d" % (P, sx, k), (0.32, 0.36, 0.03), (1.42 * sx, AXY[0] - 0.05 - 0.0 * k, 0.75 + 0.3 * k), "M_Steel", G, bevel=0.0)
        rod(P + "_steprail%d" % sx, Vector((1.58 * sx, AXY[0] - 0.25, 0.7)), Vector((1.58 * sx, AXY[0] - 0.25, 1.5)), 0.02, "M_Steel", G, seg=6)

    # ---------------------------------------------------------------- front apron: bumper, lights, grille, hooks
    abox(P + "_bumper", (3.0, 0.30, 0.42), (0, yF + 0.15, 1.08), "M_Plain", G, bevel=0.04)
    abox(P + "_bumplip", (2.9, 0.06, 0.10), (0, yF + 0.0, 0.86), "M_Black", G, bevel=0.01)
    abox(P + "_apron", (3.0, 0.40, 0.36), (0, yF + 0.38, 1.40), "M_Body", G, bevel=0.03)
    for sx in (1, -1):
        acyl(P + "_hl%d" % sx, 0.12, 0.08, (1.12 * sx, yF + 0.0, 1.14), "M_Chrome", G, axis=1, seg=18)
        acyl(P + "_hll%d" % sx, 0.10, 0.04, (1.12 * sx, yF - 0.04, 1.14), "M_HeadLamp", G, axis=1, seg=18, dirt=False)
        abox(P + "_hlg%d" % sx, (0.30, 0.03, 0.03), (1.12 * sx, yF - 0.09, 1.14), "M_Black", G, bevel=0.003)
        acyl(P + "_fog%d" % sx, 0.06, 0.06, (0.75 * sx, yF + 0.0, 1.12), "M_HeadLamp", G, axis=1, seg=12, dirt=False)
        abox(P + "_ind%d" % sx, (0.14, 0.05, 0.08), (1.40 * sx, yF + 0.0, 1.22), "M_Amber", G, bevel=0.01, dirt=False)
        abox(P + "_hook%d" % sx, (0.12, 0.22, 0.14), (0.42 * sx, yF - 0.04, 0.92), "M_Black", G, bevel=0.02)
        acyl(P + "_hookp%d" % sx, 0.03, 0.2, (0.42 * sx, yF - 0.08, 0.92), "M_Steel", G, axis=0, seg=8)
    abox(P + "_plate", (0.5, 0.02, 0.12), (0, yF - 0.01, 1.1), "M_Stencil", G, bevel=0.0, dirt=False)
    abox(P + "_winch", (0.8, 0.3, 0.26), (0, yF + 0.2, 0.78), "M_Dark", G, bevel=0.03)

    # ---------------------------------------------------------------- engine compartment between the cabs
    ey0, ey1 = yF + 0.55, cb + 0.1
    ezt = 2.42
    abox(P + "_eng", (0.86, ey1 - ey0, ezt - 1.45), (0, (ey0 + ey1) / 2, (ezt + 1.45) / 2), "M_Body", G, bevel=0.04)
    abox(P + "_engfront", (0.80, 0.04, 0.8), (0, ey0 - 0.02, 1.95), "M_Black", G, bevel=0.01)
    for k in range(9):
        abox("%s_grill%d" % (P, k), (0.78, 0.035, 0.035), (0, ey0 - 0.05, 1.6 + k * 0.085), "M_Plain", G, bevel=0.004)
    for k in range(3):
        abox("%s_grillv%d" % (P, k), (0.03, 0.04, 0.8), (-0.26 + k * 0.26, ey0 - 0.06, 1.95), "M_Plain", G, bevel=0.004)
    slats(P + "_englv", -0.36, 0.36, ey0 + 0.2, ey0 + 1.2, ezt + 0.01, 10, "M_Dark", G)
    abox(P + "_enghatch", (0.8, 1.1, 0.03), (0, ey0 + 1.95, ezt + 0.01), "M_Body", G, bevel=0.01)
    for sx in (1, -1):
        abox(P + "_enghinge%d" % sx, (0.06, 0.12, 0.04), (0.3 * sx, ey0 + 2.5, ezt + 0.03), "M_Steel", G, bevel=0.0)
    acyl(P + "_aircl", 0.16, 0.5, (0, ey0 + 0.9, ezt + 0.2), "M_Dark", G, seg=14)
    acyl(P + "_airclc", 0.19, 0.06, (0, ey0 + 0.9, ezt + 0.47), "M_Plain", G, seg=14)
    # exhaust: pipe up behind the right cab with a rain cap
    ex = Vector((-0.30, cb + 0.0, ezt))
    rod(P + "_exh", ex, ex + Vector((0, 0, 0.75)), 0.065, "M_Dark", G, seg=10)
    acyl(P + "_exhcap", 0.09, 0.04, tuple(ex + Vector((0, 0.02, 0.78))), "M_Black", G, seg=10, rot=(0.4, 0, 0))
    acyl(P + "_exhguard", 0.09, 0.4, tuple(ex + Vector((0, 0, 0.35))), "M_Steel", G, seg=10, caps=False)
    out["exhaust"] = ex + Vector((0, 0, 0.8))

    # ---------------------------------------------------------------- the two cabs (hollow shells with window openings)
    yS = yC0 + 0.38                 # windscreen top (rake)
    zs = 2.22                       # window sill
    yA = yC0 + 0.62                 # door front (A-pillar back)
    yBp = yC0 + 1.40                # B-pillar
    side_prof = [(yC0, zb), (yC0, zs), (yS, zt - 0.10), (yS + 0.14, zt), (cb, zt), (cb, zb)]
    for sx, role in ((1, "drv"), (-1, "gun")):
        xo, xi = 1.52 * sx, 0.44 * sx           # outer / inner faces
        xc = (xo + xi) / 2
        w = abs(xo - xi)
        cg = G
        pfx = "%s_cab%s" % (P, role)
        # floor + roof + back wall + front lower panel
        abox(pfx + "_floor", (w, cb - yC0, 0.06), (xc, (yC0 + cb) / 2, zb + 0.03), "M_Body", cg, bevel=0.01)
        abox(pfx + "_mat", (w - 0.12, cb - yC0 - 0.5, 0.01), (xc, (yC0 + cb) / 2 + 0.2, zb + 0.065), "M_Rubber", cg, bevel=0.0, dirt=False)
        abox(pfx + "_roof", (w + 0.04, cb - yS - 0.1, 0.07), (xc, (yS + 0.1 + cb) / 2, zt - 0.035), "M_Body", cg, bevel=0.02)
        tilted_box(pfx + "_roofnose", (0, yS - 0.02, zt - 0.12), (0, yS + 0.2, zt - 0.0), w + 0.04, 0.07, "M_Body", cg)
        bpy.data.objects[pfx + "_roofnose"].location.x = xc
        abox(pfx + "_back", (w, 0.06, zt - zb), (xc, cb - 0.03, (zt + zb) / 2), "M_Body", cg, bevel=0.01)
        abox(pfx + "_front", (w, 0.07, zs - zb), (xc, yC0 + 0.035, (zs + zb) / 2), "M_Body", cg, bevel=0.02)
        # raked windscreen: 2 panes + pillars + header + wipers
        for k, fx in enumerate((0.25, 0.75)):
            x = xi + (xo - xi) * fx
            tilted_box("%s_ws%d" % (pfx, k), (0, yC0 + 0.03, zs + 0.03), (0, yS - 0.02, zt - 0.13), w * 0.46, 0.02, "M_CabGlass", cg, bevel=0.0, dirt=False)
            bpy.data.objects["%s_ws%d" % (pfx, k)].location.x = x
            rod("%s_wiper%d" % (pfx, k), Vector((x + 0.1, yC0 - 0.0, zs + 0.06)), Vector((x - 0.05, yC0 + 0.12, zs + 0.38)), 0.01, "M_Black", cg, seg=5)
        for k, fx in enumerate((0.0, 0.5, 1.0)):
            x = xi + (xo - xi) * fx
            tilted_box("%s_wsp%d" % (pfx, k), (0, yC0 + 0.0, zs - 0.02), (0, yS + 0.0, zt - 0.08), 0.06, 0.07, "M_Body", cg)
            bpy.data.objects["%s_wsp%d" % (pfx, k)].location.x = x
        abox(pfx + "_visor", (w + 0.06, 0.28, 0.03), (xc, yS - 0.1, zt - 0.02), "M_Body", cg, rot=(0.15, 0, 0), bevel=0.01)
        # inner wall: solid, faces the engine
        prism_x(pfx + "_inwall", side_prof, 0.025, "M_Body", cg, bevel=0.0, x0=xi - 0.025)
        # outer wall: lower panel, front quarter, door window, B pillar, rear window, header rail
        abox(pfx + "_owl", (0.05, cb - yC0, zs - zb), (xo, (yC0 + cb) / 2, (zs + zb) / 2), "M_Body", cg, bevel=0.01)
        prism_x(pfx + "_owq", [(yC0, zs), (yS, zt - 0.10), (yS + 0.14, zt), (yA, zt), (yA, zs)], 0.025, "M_Body", cg, bevel=0.0, x0=xo - 0.025)
        abox(pfx + "_owb", (0.05, 0.10, zt - zs), (xo, yBp + 0.05, (zt + zs) / 2), "M_Body", cg, bevel=0.005)
        abox(pfx + "_owr", (0.05, 0.10, zt - zs), (xo, cb - 0.05, (zt + zs) / 2), "M_Body", cg, bevel=0.005)
        abox(pfx + "_owt", (0.05, cb - yA, 0.10), (xo, (yA + cb) / 2, zt - 0.05), "M_Body", cg, bevel=0.005)
        abox(pfx + "_gl1", (0.015, yBp - yA, zt - zs - 0.10), (xo, (yA + yBp) / 2, (zt - 0.10 + zs) / 2), "M_CabGlass", cg, bevel=0.0, dirt=False)
        abox(pfx + "_gl2", (0.015, cb - 0.10 - yBp - 0.10, zt - zs - 0.10), (xo, (yBp + 0.10 + cb - 0.10) / 2, (zt - 0.10 + zs) / 2), "M_CabGlass", cg, bevel=0.0, dirt=False)
        # door seams, handle, hinges, mirror
        for y in (yA - 0.01, yBp + 0.11):
            abox("%s_seam%d" % (pfx, int(y * 100)), (0.012, 0.012, zt - zb - 0.12), (xo + 0.028 * sx, y, (zt + zb) / 2 - 0.02), "M_Black", cg, bevel=0.0, dirt=False)
        abox(pfx + "_seamb", (0.012, yBp - yA + 0.12, 0.012), (xo + 0.028 * sx, (yA + yBp) / 2 + 0.05, zb + 0.05), "M_Black", cg, bevel=0.0, dirt=False)
        abox(pfx + "_handle", (0.04, 0.16, 0.03), (xo + 0.04 * sx, yBp - 0.15, zs - 0.12), "M_Steel", cg, bevel=0.005)
        for k in range(2):
            acyl("%s_hinge%d" % (pfx, k), 0.02, 0.12, (xo + 0.03 * sx, yA + 0.02, zb + 0.25 + k * 0.7), "M_Steel", cg, seg=8)
        rod(pfx + "_mirarm", Vector((xo + 0.02 * sx, yA - 0.1, zs + 0.3)), Vector((xo + 0.3 * sx, yA - 0.2, zs + 0.35)), 0.015, "M_Black", cg, seg=6)
        rod(pfx + "_mirarm2", Vector((xo + 0.02 * sx, yA - 0.1, zs - 0.05)), Vector((xo + 0.3 * sx, yA - 0.2, zs + 0.15)), 0.015, "M_Black", cg, seg=6)
        abox(pfx + "_mirror", (0.04, 0.2, 0.3), (xo + 0.32 * sx, yA - 0.2, zs + 0.25), "M_Black", cg, bevel=0.01)
        abox(pfx + "_marker", (0.10, 0.06, 0.05), (xo - 0.1 * sx, yS + 0.3, zt + 0.03), "M_Amber", cg, bevel=0.01, dirt=False)
        abox(pfx + "_vent", (0.4, 0.3, 0.06), (xc, cb - 0.5, zt + 0.03), "M_Body", cg, bevel=0.02)
        # ---- interior
        sy, sz = -cfg["seat"][1] + 0.05, cfg["seat"][0] - 0.18      # rider feet = seat - 0.85, hips ~ seat - 0.1
        xs = cfg["driver_x"] * sx
        abox(pfx + "_seatc", (0.48, 0.46, 0.12), (xs, sy, sz), "M_Seat", cg, bevel=0.03)
        abox(pfx + "_seatb", (0.48, 0.12, 0.62), (xs, sy + 0.28, sz + 0.34), "M_Seat", cg, rot=(-0.18, 0, 0), bevel=0.03)
        abox(pfx + "_seatbase", (0.36, 0.36, sz - zb - 0.06), (xs, sy, (sz + zb) / 2), "M_Dark", cg, bevel=0.01)
        abox(pfx + "_headrest", (0.3, 0.08, 0.18), (xs, sy + 0.36, sz + 0.78), "M_Seat", cg, bevel=0.03)
        dy0 = -cfg["steer"][1] - 0.30          # dash face (towards the driver)
        abox(pfx + "_dash", (w - 0.08, dy0 - yC0 - 0.07, 0.36), (xc, (dy0 + yC0 + 0.07) / 2, zs - 0.02), "M_Dash", cg, bevel=0.02)
        abox(pfx + "_dashtop", (w - 0.08, dy0 - yC0 - 0.07, 0.03), (xc, (dy0 + yC0 + 0.07) / 2, zs + 0.17), "M_Interior", cg, bevel=0.005)
        abox(pfx + "_dashlow", (w - 0.08, 0.3, 0.5), (xc, yC0 + 0.22, zb + 0.3), "M_Interior", cg, bevel=0.01)
        abox(pfx + "_bwpanel", (w - 0.1, 0.04, 0.6), (xc, cb - 0.08, zs + 0.15), "M_Interior", cg, bevel=0.01)
        acyl(pfx + "_extg", 0.06, 0.4, (xc + 0.3 * sx, cb - 0.14, zb + 0.35), "M_NavRed", cg, seg=10)
        if role == "drv":
            sw = gB(cfg["driver_x"], cfg["steer"][0], cfg["steer"][1])
            # gauges on the dash face (needles are separate nodes)
            gc = {"needle_speed": (0.16, 0.10), "needle_rpm": (-0.16, 0.10), "needle_fuel": (0.0, 0.18), "needle_temp": (0.0, 0.02)}
            out["needles"] = {}
            for n, (dx, dz) in gc.items():
                c = Vector((sw.x + dx, dy0 - 0.005, zs - 0.1 + dz))
                r = 0.065 if n in ("needle_speed", "needle_rpm") else 0.035
                gauge(pfx + "_" + n, c - Vector((0, 0.02, 0)), r, cg)
                out["needles"][n] = (c + Vector((0, 0.005, 0)), r)
            abox(pfx + "_lamps", (0.3, 0.02, 0.04), (sw.x, dy0 + 0.0, zs - 0.18), "M_Amber", cg, bevel=0.0, dirt=False)
            # steering column, pedals, gear and transfer levers
            col_end = sw + Vector((0, -0.38, -0.18))
            rod(pfx + "_column", sw + Vector((0, -0.04, -0.02)), col_end, 0.035, "M_Dash", cg, seg=10)
            for k, dx in enumerate((-0.15, 0.0, 0.15)):
                tilted_box("%s_pedal%d" % (pfx, k), (0, sw.y - 0.42, zb + 0.08), (0, sw.y - 0.47, zb + 0.28), 0.07, 0.015, "M_Steel", cg)
                bpy.data.objects["%s_pedal%d" % (pfx, k)].location.x = sw.x + dx
            for k, dy in enumerate((0.0, 0.15)):
                rod("%s_lever%d" % (pfx, k), Vector((xi + 0.15 * sx, sy - 0.25 + dy, zb + 0.07)), Vector((xi + 0.18 * sx, sy - 0.32 + dy, zb + 0.55)), 0.012, "M_Steel", cg, seg=6)
                acyl("%s_knob%d" % (pfx, k), 0.03, 0.05, (xi + 0.18 * sx, sy - 0.32 + dy, zb + 0.57), "M_Black", cg, seg=8)
            out["steer"] = sw
            # roof: searchlight + antenna base
            acyl(pfx + "_slb", 0.05, 0.1, (xo - 0.25 * sx, yS + 0.25, zt + 0.06), "M_Dark", cg, seg=8)
            acyl(pfx + "_sl", 0.09, 0.12, (xo - 0.25 * sx, yS + 0.2, zt + 0.16), "M_Chrome", cg, axis=1, seg=14)
            acyl(pfx + "_sll", 0.07, 0.02, (xo - 0.25 * sx, yS + 0.13, zt + 0.16), "M_HeadLamp", cg, axis=1, seg=14, dirt=False)
        else:
            # commander / gunner: fire-control console with displays, joystick, radio
            abox(pfx + "_fcs", (0.52, 0.26, 0.40), (xs, dy0 + 0.02, zs + 0.05), "M_Dash", cg, rot=(0.25, 0, 0), bevel=0.02)
            for k in range(2):
                abox("%s_scr%d" % (pfx, k), (0.2, 0.01, 0.15), (xs - 0.12 + 0.24 * k, dy0 + 0.16, zs + 0.1), "M_Screen", cg, rot=(0.25, 0, 0), bevel=0.0, dirt=False)
            for k in range(6):
                acyl("%s_btn%d" % (pfx, k), 0.012, 0.02, (xs - 0.2 + 0.08 * k, dy0 + 0.2, zs - 0.06), "M_Amber" if k % 2 else "M_NavGreen", cg, axis=1, seg=8, dirt=False)
            rod(pfx + "_stick", Vector((xs + 0.25 * sx, sy - 0.3, zb + 0.5)), Vector((xs + 0.25 * sx, sy - 0.33, zb + 0.72)), 0.02, "M_Black", cg, seg=8)
            abox(pfx + "_stickb", (0.16, 0.16, 0.45), (xs + 0.25 * sx, sy - 0.3, zb + 0.27), "M_Dash", cg, bevel=0.01)
            abox(pfx + "_radio", (0.42, 0.26, 0.3), (xc, cb - 0.2, zs + 0.3), "M_Plain", cg, bevel=0.02)
            for k in range(3):
                acyl("%s_rknob%d" % (pfx, k), 0.02, 0.03, (xc - 0.12 + 0.12 * k, cb - 0.34, zs + 0.3), "M_Black", cg, axis=1, seg=8)
            acyl(pfx + "_antb", 0.05, 0.1, (xo - 0.2 * sx, cb - 0.3, zt + 0.06), "M_Dark", cg, seg=8)
            rod(pfx + "_ant", Vector((xo - 0.2 * sx, cb - 0.3, zt + 0.1)), Vector((xo - 0.2 * sx, cb - 0.3, zt + 2.2)), 0.008, "M_Black", cg, seg=4)
    out["yC0"], out["zb"], out["zt"], out["cb"], out["yS"] = yC0, zb, zt, cb, yS

    # ---------------------------------------------------------------- rear: bumper, lights, pintle, ladder
    abox(P + "_rbump", (2.9, 0.22, 0.28), (0, yR - 0.11, 1.05), "M_Plain", G, bevel=0.03)
    for sx in (1, -1):
        abox(P + "_tl%d" % sx, (0.30, 0.05, 0.12), (1.2 * sx, yR - 0.24, 1.08), "M_NavRed", G, bevel=0.01, dirt=False)
        abox(P + "_tla%d" % sx, (0.12, 0.05, 0.12), (0.92 * sx, yR - 0.24, 1.08), "M_Amber", G, bevel=0.01, dirt=False)
        abox(P + "_rflap%d" % sx, (0.62, 0.02, 0.4), (TX * sx, yR - 0.3, 0.75), "M_Rubber", G, bevel=0.0)
    acyl(P + "_pintle", 0.08, 0.16, (0, yR - 0.25, 0.95), "M_Black", G, seg=12)
    abox(P + "_rplate", (0.5, 0.02, 0.12), (0, yR - 0.23, 1.25), "M_Stencil", G, bevel=0.0, dirt=False)

    # ---------------------------------------------------------------- jacks (static housing + moving leg)
    for j, (jx, jz, trav) in enumerate(cfg["jacks"]):
        p = gB(jx, 0, jz)
        abox("%s_jh%d" % (P, j), (0.30, 0.30, 0.95), (p.x, p.y, 1.25), "M_Plain", G, bevel=0.03)
        abox("%s_jbr%d" % (P, j), (abs(p.x) - 0.55, 0.2, 0.18), ((abs(p.x) + 0.55) / 2 * (1 if p.x > 0 else -1), p.y, 1.55), "M_Plain", G, bevel=0.02)
        acyl("%s_jc%d" % (P, j), 0.11, 0.22, (p.x, p.y, 1.82), "M_Steel", G, seg=12)
        rod("%s_jhose%d" % (P, j), Vector((p.x, p.y + 0.15, 1.75)), Vector((p.x * 0.6, p.y + 0.3, 1.4)), 0.015, "M_Black", G, seg=5)
        gm = "%s_jack%d" % (P, j)
        acyl(gm + "_leg", 0.09, 1.05, (p.x, p.y, trav + 0.58), "M_Hydraulic", gm, seg=14)
        lathe_y(gm + "_pad", [(0, 0.0), (0, 0.32), (0.04, 0.34), (0.10, 0.26), (0.10, 0.08)], (p.x, p.y, trav), ["M_Steel"], gm, seg=20, axis='Z')
        out["jacks"].append((j, gm, Vector((p.x, p.y, 0.0))))
    return out


def rig_maz(P, ch, rt):
    """Hierarchy for maz543() output: static body, wheel_i, steering_wheel, needles, jack_j."""
    group_under(ch["G"], P + "_body_geo", rt)
    for i, g, c in ch["wheels"]:
        w = pivot("wheel_%d" % i, tuple(c), rt)
        group_under(g, "wheel_%d_geo" % i, w)
    for j, gm, p in ch["jacks"]:
        jn = pivot("jack_%d" % j, tuple(p), rt)
        group_under(gm, "jack_%d_geo" % j, jn)
    # steering wheel: node tilted -155 deg about X; ring perpendicular to its local Y (glTF local Z)
    sw = ch["steer"]
    gs = P + "_sw"
    rot = Matrix.Rotation(math.radians(-155), 4, 'X')
    prof = [(0.018 * math.cos(2 * PI * k / 10), 0.215 + 0.018 * math.sin(2 * PI * k / 10)) for k in range(10)]
    ring = lathe_y(gs + "_ring", prof + [prof[0]], tuple(sw), ["M_Dash"], gs, seg=32, axis='Y', smooth=True, dirt=False)
    ring.data.transform(Matrix.Translation(sw) @ rot @ Matrix.Translation(-sw))
    hub = sw
    acyl(gs + "_hub", 0.06, 0.06, tuple(hub), "M_Dash", gs, rot=(math.radians(-155) + PI / 2, 0, 0), seg=14)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        d = rot @ Vector((math.cos(a) * 0.21, 0, math.sin(a) * 0.21))
        rod("%s_spoke%d" % (gs, k), hub, hub + d, 0.014, "M_Dash", gs, seg=6)
    e = empty("steering_wheel", tuple(sw), 0.1, 'PLAIN_AXES', rot=(math.radians(-155), 0, 0))
    attach(e, rt)
    group_under(gs, "steering_wheel_geo", e)
    for n, (c, r) in ch["needles"].items():
        gn = P + "_" + n + "_g"
        abox(gn + "_n", (0.006, 0.004, r * 0.85), tuple(c + Vector((0, 0, r * 0.38))), "M_Needle", gn, bevel=0.0, dirt=False)
        acyl(gn + "_c", 0.008, 0.01, tuple(c), "M_Dash", gn, axis=1, seg=8, dirt=False)
        nn = pivot(n, tuple(c), rt)
        group_under(gn, n + "_geo", nn)


def rw_export(path_glb, path_blend, scene_name):
    finish_nla()
    bpy.context.view_layer.update()
    print("STATS", stats())
    print("GLB", export_glb(path_glb))
    print("BLEND", save_blend(path_blend, scene_name))
