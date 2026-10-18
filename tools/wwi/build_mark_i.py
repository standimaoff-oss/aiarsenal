#!/usr/bin/env python3
"""Build a textured Mark I Male tank GLB and matching LOD from source geometry.

Deterministic, Blender-free reconstruction of the 1916 British lozenge tank, dimensioned
against the Bovington drawings: 7.20 m over the track horns, 3.64 m over the sponsons,
2.35 m to the top of the return run.  The hull is assembled from extruded armour prisms
(sloped glacis, chamfered belly, octagonal sponsons) rather than boxes, and carries the
fittings that make the silhouette read at gameplay scale: driver's hood and visor, manhole
coamings, radiator louvres, exhaust stacks with rain caps, mud chutes, unditching beam,
towing shackles and riveted plate seams.

Run from any working directory: python tools/wwi/build_mark_i.py
Then generate its walkable collision asset: python tools/deck/gen_boxes.py mark_i
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from glb_builder import GlbBuilder, Mesh, axis_quaternion, png_rgba

ASSETS = ROOT / "mods/aiarsenal/src/main/resources/assets/aiarsenal"
MODEL_DIR = ASSETS / "models/glb"
TEXTURE_DIR = ASSETS / "textures/entity/mark_i"
ITEM_DIR = ASSETS / "textures/item"

# Model space (+X left, +Y up, +Z forward), matching VehicleSpec and DeckShapes.
TRACK_PATH = [(0.13, -2.45), (0.82, -3.28), (2.28, -3.28), (2.28, 3.28),
              (0.82, 3.28), (0.13, 2.45)]
TRACK_LINKS = 56
TRACK_HALF_THICKNESS = 0.048
# Place running gear tangent to the inside face of the flat lower/upper runs, not visibly floating inside the belt.
ROAD_WHEEL_RADIUS = 0.305
END_WHEEL_RADIUS = 0.385
RETURN_ROLLER_RADIUS = 0.115
ROAD_WHEEL_CENTER_Y = TRACK_PATH[0][0] + TRACK_HALF_THICKNESS + ROAD_WHEEL_RADIUS
RETURN_ROLLER_CENTER_Y = TRACK_PATH[2][0] - TRACK_HALF_THICKNESS - RETURN_ROLLER_RADIUS
SPONSON_X = 1.41
SPONSON_Y = 1.22
BARREL_TIP = 1.15  # 6-pdr muzzle, metres from the trunnion (0.74 m proud of the sponson face)


_TEXTURE_CACHE: dict[tuple, bytes] = {}


def _weather(res: int, base: tuple[int, int, int], seed: int,
             blotches: int, streaks: int, amp: int = 16) -> bytes:
    """Weathered plate: soft blotches, vertical rust/mud streaks and a fine grain.

    Deterministic (seeded), tileable in both axes, and cheap enough to run in CI.
    """
    key = (res, base, seed, blotches, streaks, amp)
    if key in _TEXTURE_CACHE:
        return _TEXTURE_CACHE[key]
    rng = random.Random(seed)
    pix = [base] * (res * res)
    for _ in range(blotches):
        cx = rng.uniform(0, res)
        cy = rng.uniform(0, res)
        r = rng.uniform(res * 0.06, res * 0.32)
        dr, dg, db = rng.uniform(-amp, amp * 0.6), rng.uniform(-amp, amp * 0.6), rng.uniform(-amp * 0.8, amp * 0.5)
        x0, x1 = max(0, int(cx - r)), min(res, int(cx + r) + 1)
        y0, y1 = max(0, int(cy - r)), min(res, int(cy + r) + 1)
        for y in range(y0, y1):
            for x in range(x0, x1):
                d = math.hypot(x - cx, y - cy) / r
                if d < 1.0:
                    a = (1.0 - d * d) * 0.6
                    i = y * res + x
                    pix[i] = (max(0, min(255, int(pix[i][0] + dr * a))),
                              max(0, min(255, int(pix[i][1] + dg * a))),
                              max(0, min(255, int(pix[i][2] + db * a))))
    for _ in range(streaks):
        cx = rng.uniform(0, res)
        top = rng.uniform(0, res * 0.6)
        h = rng.uniform(res * 0.25, res * 0.9)
        w = rng.uniform(1.0, 2.5)
        dr, dg, db = rng.uniform(-22, 8), rng.uniform(-24, 8), rng.uniform(-14, 12)
        for y in range(int(top), min(res, int(top + h))):
            fade = 1.0 - (y - top) / h
            for x in range(max(0, int(cx - w)), min(res, int(cx + w) + 1)):
                a = fade * (1.0 - abs(x - cx) / w) * 0.7
                i = y * res + x
                pix[i] = (max(0, min(255, int(pix[i][0] + dr * a))),
                          max(0, min(255, int(pix[i][1] + dg * a))),
                          max(0, min(255, int(pix[i][2] + db * a))))
    out = []
    for i, (r, g, b) in enumerate(pix):
        n = rng.randrange(-5, 6)
        out.append((max(0, min(255, r + n)), max(0, min(255, g + n)),
                    max(0, min(255, b + n)), 255))
    png = png_rgba(res, res, out)
    _TEXTURE_CACHE[key] = png
    return png


def atlas() -> bytes:
    """British khaki enamel: 256 px, blotched and streaked like a month in the Salient."""
    return _weather(256, (126, 123, 84), 1916, 30, 48, 15)


def steel_atlas() -> bytes:
    """Blued, oily steel with heavier streaking -- running gear, guns and fasteners."""
    return _weather(256, (150, 156, 158), 1917, 22, 90, 18)


def wood_atlas() -> bytes:
    """Unditching beam: rough-sawn elm, grain along +x of the texture."""
    return _weather(128, (104, 80, 46), 1918, 16, 26, 12)


def brass_atlas() -> bytes:
    """Aged brass fittings and rivet heads."""
    return _weather(64, (158, 132, 66), 1919, 8, 6, 10)


def icon_mark_i() -> bytes:
    """A hand-drawn, side-on item icon (not a rendered screenshot)."""
    w = h = 64
    p = [(0, 0, 0, 0)] * (w * h)

    def poly(points, color):
        for y in range(max(0, int(min(q[1] for q in points))), min(h, int(max(q[1] for q in points)) + 1)):
            for x in range(max(0, int(min(q[0] for q in points))), min(w, int(max(q[0] for q in points)) + 1)):
                inside = False
                j = len(points) - 1
                for i, (xi, yi) in enumerate(points):
                    xj, yj = points[j]
                    if (yi > y + 0.5) != (yj > y + 0.5) and x + 0.5 < (xj - xi) * (y + 0.5 - yi) / ((yj - yi) or 1e-9) + xi:
                        inside = not inside
                    j = i
                if inside:
                    p[y * w + x] = color

    def rect(x0, y0, x1, y1, color):
        poly(((x0,y0),(x1,y0),(x1,y1),(x0,y1)), color)

    ink=(34,39,31,255); track=(65,68,54,255); khaki=(109,116,76,255)
    light=(144,143,99,255); steel=(103,109,101,255); dark=(48,54,47,255)
    poly(((4,43),(12,28),(50,28),(60,43),(51,54),(12,54)),ink)
    poly(((7,42),(15,31),(48,31),(56,42),(48,51),(15,51)),track)
    poly(((14,39),(19,33),(45,33),(51,39),(51,44),(44,49),(19,49),(13,44)),khaki)
    poly(((21,31),(24,27),(41,27),(45,31),(45,35),(21,35)),light)
    rect(23,35,43,42,khaki); rect(39,37,56,39,steel); rect(52,36,57,40,light)
    for x in range(12,56,5):
        rect(x,42,x+1,44,(132,127,94,255)); rect(x,49,x+1,51,(132,127,94,255))
    for x in (18,25,32,39,46):
        rect(x,43,x+2,47,dark); rect(x,44,x+1,46,light)
    return png_rgba(w,h,p)


def icon_shell() -> bytes:
    w=h=16
    p=[(0,0,0,0)]*(w*h)
    def fill(points,color):
        for y in range(h):
            for x in range(w):
                inside=False; j=len(points)-1
                for i,(xi,yi) in enumerate(points):
                    xj,yj=points[j]
                    if (yi>y+.5)!=(yj>y+.5) and x+.5<(xj-xi)*(y+.5-yi)/((yj-yi) or 1e-9)+xi: inside=not inside
                    j=i
                if inside:p[y*w+x]=color
    fill(((6,14),(11,14),(11,10),(6,10)),(151,112,46,255))
    fill(((7,10),(10,10),(10,5),(7,5)),(106,116,111,255))
    fill(((7,5),(10,5),(9,3),(8,2)),(183,188,171,255))
    fill(((6,11),(11,11),(11,10),(6,10)),(218,176,71,255))
    return png_rgba(w,h,p)


def loop_length(path=TRACK_PATH) -> float:
    return sum(math.hypot(path[(i+1)%len(path)][0]-p[0], path[(i+1)%len(path)][1]-p[1]) for i,p in enumerate(path))


def loop_point(distance: float, path=TRACK_PATH):
    """Position and tangent at arclength distance around the closed side-profile path."""
    lengths=[math.hypot(path[(i+1)%len(path)][0]-p[0], path[(i+1)%len(path)][1]-p[1]) for i,p in enumerate(path)]
    total=sum(lengths)
    d=distance%total
    for i,length in enumerate(lengths):
        if d<=length or i==len(lengths)-1:
            a=path[i]; b=path[(i+1)%len(path)]
            u=0.0 if length<=1e-9 else d/length
            dy=(b[0]-a[0])/length; dz=(b[1]-a[1])/length
            y=a[0]+(b[0]-a[0])*u; z=a[1]+(b[1]-a[1])*u
            # Local +Z is rotated about X to align to the path tangent (dy,dz).
            rx=math.atan2(-dy,dz)
            return y,z,rx
        d-=length
    raise AssertionError("unreachable loop point")


def extrude(m: Mesh, material: int, profile, lo: float, hi: float, tile: float = 1.0) -> None:
    """Extrude a convex (y, z) profile along x with outward-facing winding.

    Materials are single-sided and Minecraft culls back faces, so the profile winding must
    agree with the extrusion direction; get it wrong and the plate renders inside-out (this
    silently affected the right-hand track frame of the earlier model).
    """
    if abs(hi - lo) < 1e-9:
        return
    n = len(profile)
    area = 0.5 * sum(profile[i][0] * profile[(i + 1) % n][1] - profile[(i + 1) % n][0] * profile[i][1]
                     for i in range(n))
    if (area > 0) != (hi > lo):
        profile = list(reversed(profile))
    m.prism(material, profile, "x", min(lo, hi), max(lo, hi), tile)


def rivets_x(mesh: Mesh, material: int, x: float, points, radius=0.019) -> None:
    """Domed rivet heads proud of a plate whose outward normal is +-x."""
    out = 0.033 if x >= 0 else -0.033
    for y, z in points:
        mesh.cylinder(material, (x, y, z), (x + out, y, z), radius, 6, material)


def rivets_z(mesh: Mesh, material: int, z: float, points, radius=0.019) -> None:
    """Domed rivet heads proud of a plate whose outward normal is +-z."""
    out = 0.033 if z >= 0 else -0.033
    for x, y in points:
        mesh.cylinder(material, (x, y, z), (x, y, z + out), radius, 6, material)


def rivets_y(mesh: Mesh, material: int, y: float, points, radius=0.019) -> None:
    """Domed rivet heads proud of a horizontal plate."""
    for x, z in points:
        mesh.cylinder(material, (x, y, z), (x, y + 0.028, z), radius, 6, material)


def ring_x(cx: float, cy: float, cz: float, radius: float, count: int):
    """(y, z) rivet positions around a circle in the plane x = cx."""
    return [(cy + radius * math.cos(math.tau * i / count),
             cz + radius * math.sin(math.tau * i / count)) for i in range(count)]


def sponson_profile():
    """Octagonal male sponson cross-section in (y, z), centred on the gun axis."""
    return [(0.86, -0.52), (0.86, 0.52), (1.02, 0.87), (1.44, 0.87),
            (1.60, 0.52), (1.60, -0.52), (1.44, -0.87), (1.02, -0.87)]


def hull_mesh(paint: int, highlight: int, steel: int, dark: int, brass: int, wood: int, lod=False) -> Mesh:
    m = Mesh()
    # ---- Armoured hull, as extruded plate profiles --------------------------------
    # Belly: chamfered so mud sheds, 12 mm plate, x +-1.15 m.
    extrude(m, paint, [(0.55, -2.24), (0.43, -1.92), (0.43, 1.92), (0.55, 2.28),
                       (1.29, 2.28), (1.29, -2.24)], -1.15, 1.15, 0.60)
    # Crew compartment: roof sloping up to the driver's hood.
    extrude(m, highlight, [(1.29, -2.16), (1.29, 1.62), (1.77, 2.08), (1.77, -2.16)],
            -0.94, 0.94, 0.60)
    # Driver's hood: tall armoured cab, front plate leaning out over the visor.
    extrude(m, paint, [(1.48, 1.20), (1.48, 2.02), (2.12, 2.18), (2.12, 1.20)],
            -0.78, 0.78, 0.60)
    # Engine deck: raised over the Daimler, sloping down to the tail.
    extrude(m, paint, [(1.29, -2.14), (1.29, -1.31), (1.80, -1.31), (1.80, -1.80)],
            -0.84, 0.84, 0.60)
    # Mud chutes and the skirt plate that screens the return rollers.
    for side in (-1, 1):
        m.box(paint, (side * 0.98, 1.31, -2.02), (side * 1.16, 1.40, 2.02), 0.60)
        extrude(m, dark, [(1.40, -1.55), (1.40, 1.55), (1.90, 1.35), (1.90, -1.35)],
                side * 1.10, side * 1.24, 0.60)
        m.box(steel, (side * 1.10, 1.44, 1.30), (side * 1.24, 1.47, 1.40), 0.30)
    # ---- Fixed male sponsons, one each side ---------------------------------------
    for side in (-1, 1):
        extrude(m, highlight, sponson_profile(), side * 1.00, side * 1.82, 0.45)
        x_out = side * 1.82
        # Gun port: collar, mantlet ring and the four plates that leave it open.
        # Kept inside |x| = 1.87 m: the walkable deck is voxelised from this silhouette and the
        # tank is 3.64 m wide over the sponsons, so nothing may stick out past the armour face.
        m.cylinder(steel, (x_out - side * 0.03, SPONSON_Y, 0.0), (x_out + side * 0.03, SPONSON_Y, 0.0), 0.29, 14, dark, 0.40)
        m.cylinder(dark, (x_out + side * 0.03, SPONSON_Y, 0.0), (x_out + side * 0.055, SPONSON_Y, 0.0), 0.235, 14, dark, 0.40)
        for z in (-0.55, 0.55):
            m.box(steel, (x_out - side * 0.02, 0.95, z - 0.05), (x_out + side * 0.02, 1.51, z + 0.05), 0.30)
        # Layer's vision slit either side of the port.
        for z in (-0.62, 0.62):
            m.box(dark, (x_out - side * 0.03, 1.42, z - 0.11), (x_out + side * 0.03, 1.49, z + 0.11), 0.30)
            m.box(brass, (x_out - side * 0.03, 1.45, z - 0.07), (x_out + side * 0.03, 1.47, z + 0.07), 0.30)
        # Escape door on the inboard rear face, with hinge and handle.
        m.box(steel, (side * 1.00, 0.90, -0.78), (side * 1.04, 1.50, -0.34), 0.30)
        m.cylinder(steel, (side * 1.00, 1.20, -0.74), (side * 1.00, 1.20, -0.66), 0.02, 6, steel)
        m.cylinder(brass, (side * 1.04, 1.20, -0.56), (side * 1.10, 1.20, -0.56), 0.035, 8, brass)
        # Plate seams: a rivet ring around the port and rows along the octagon edges.
        if not lod:
            rivets_x(m, brass, x_out, ring_x(x_out, SPONSON_Y, 0.0, 0.31, 12), 0.017)
            for y in (0.94, 1.52):
                rivets_x(m, brass, x_out, [(y, z) for z in (-0.72, -0.42, 0.42, 0.72)])
    # ---- Driver's hood fittings ---------------------------------------------------
    # Four vision slits with armoured covers, and a hood roof hatch.
    for x in (-0.48, -0.16, 0.16, 0.48):
        m.box(dark, (x - 0.09, 1.82, 2.135), (x + 0.09, 1.91, 2.175), 0.30)
        m.box(steel, (x - 0.10, 1.91, 2.130), (x + 0.10, 1.94, 2.180), 0.30)
    m.cylinder(highlight, (0.0, 2.12, 1.60), (0.0, 2.19, 1.60), 0.21, 10, steel, 0.30)
    m.cylinder(steel, (0.0, 2.185, 1.60), (0.0, 2.23, 1.60), 0.235, 10, dark, 0.30)
    m.cylinder(brass, (0.0, 2.23, 1.60), (0.0, 2.26, 1.60), 0.035, 6, brass)
    # ---- Engine deck fittings -----------------------------------------------------
    m.box(steel, (-0.36, 1.80, -1.72), (0.36, 1.84, -1.34), 0.30)
    m.box(dark, (-0.28, 1.84, -1.66), (0.28, 1.85, -1.40), 0.30)
    for i in range(7):  # radiator louvres, angled slats
        x = -0.55 + i * 0.18
        m.box(dark, (x, 1.72, -1.28), (x + 0.07, 1.79, -0.66), 0.30)
    for z in (-0.30, 0.25):  # crew compartment manhole coamings with lids
        m.cylinder(highlight, (0.0, 1.77, z), (0.0, 1.85, z), 0.25, 10, steel, 0.30)
        m.cylinder(steel, (0.0, 1.845, z), (0.0, 1.88, z), 0.275, 10, dark, 0.30)
        m.cylinder(brass, (0.0, 1.88, z), (0.0, 1.91, z), 0.04, 6, brass)
    for x in (-0.58, 0.58):  # exhaust stacks with rain caps
        m.cylinder(steel, (x, 1.80, -2.02), (x, 2.06, -2.02), 0.075, 10, steel, 0.30)
        m.cylinder(dark, (x, 2.06, -2.02), (x, 2.10, -2.02), 0.085, 10, dark, 0.30)
        m.cylinder(steel, (x, 2.10, -2.02), (x, 2.14, -2.02), 0.055, 10, steel, 0.30)
    # ---- Unditching beam stowed across the tail, chained to the hull --------------
    m.box(wood, (-1.28, 0.96, -2.60), (1.28, 1.09, -2.44), 0.40)
    for side in (-1, 1):
        m.box(wood, (side * 0.94, 0.93, -2.60), (side * 1.06, 1.12, -2.44), 0.40)
        m.cylinder(steel, (side * 0.99, 1.12, -2.52), (side * 0.99, 1.36, -2.30), 0.012, 6, steel)
    # ---- Towing shackles, front and rear ------------------------------------------
    for side in (-1, 1):
        for z, y in ((2.40, 0.50), (-2.42, 0.48)):
            m.cylinder(steel, (side * 0.76, y, z * 0.93), (side * 0.76, y, z), 0.05, 8, steel)
    # ---- Riveting ------------------------------------------------------------------
    if not lod:
        zs = [-2.05, -1.72, -1.39, -1.06, -0.73, -0.40, -0.07, 0.26, 0.59, 0.92, 1.25, 1.58]
        for side in (-1, 1):
            rivets_x(m, brass, side * 1.118, [(y, z) for y in (0.56, 0.91, 1.26) for z in zs])
            rivets_x(m, brass, side * 0.908, [(1.36, z) for z in zs if -2.0 <= z <= 1.5])
            rivets_x(m, brass, side * 0.748, [(1.56, z) for z in (1.30, 1.55, 1.80, 2.05)])
            rivets_x(m, brass, side * 0.748, [(1.98, z) for z in (1.30, 1.55, 1.80, 2.05)])
        rivets_z(m, brass, 2.075, [(x, 1.66) for x in (-0.70, -0.35, 0.0, 0.35, 0.70)])
        rivets_y(m, brass, 1.885, [(x, z) for x in (-0.35, 0.35) for z in (-0.30, 0.25)], 0.017)
        rivets_y(m, brass, 1.845, [(x, -1.50) for x in (-0.60, -0.20, 0.20, 0.60)], 0.017)
        for y in (1.59, 1.98):
            rivets_z(m, brass, 2.175, [(-0.63 + i * 0.18, y) for i in range(8)])
    else:
        for side in (-1, 1):
            rivets_x(m, brass, side * 1.118, [(0.58, z) for z in (-1.8, -0.8, 0.2, 1.2)], 0.022)
    return m


def wheel_mesh(steel: int, rubber: int, radius=0.31) -> Mesh:
    """Flanged steel wheel: rim at the declared radius, web, hub boss, bolt circle."""
    m = Mesh()
    m.cylinder(steel, (-0.105, 0, 0), (0.105, 0, 0), radius, 16, steel)      # tyre band
    m.cylinder(rubber, (-0.121, 0, 0), (0.121, 0, 0), radius * 0.90, 16, rubber)
    m.cylinder(steel, (-0.133, 0, 0), (0.133, 0, 0), radius * 0.62, 14, steel)  # web
    m.cylinder(steel, (-0.145, 0, 0), (0.145, 0, 0), radius * 0.24, 10, steel)  # hub boss
    for i in range(6):
        a = math.tau * i / 6
        m.cylinder(steel, (-0.148, radius * 0.44 * math.cos(a), radius * 0.44 * math.sin(a)),
                   (0.148, radius * 0.44 * math.cos(a), radius * 0.44 * math.sin(a)),
                   max(0.012, radius * 0.075), 6, steel)
    return m


def gun_mesh(steel: int, dark: int, brass: int, lod=False) -> tuple[int, int]:
    """Two local meshes: the fixed cradle/breech and the recoiling QF 6-pdr tube.

    Barrel axis is +z in launcher_pitch space; the muzzle sits at z=1.15 m, exactly where
    VehicleDamage.tubeWorld puts the hit tube, and Armour.tubeRadius gives it 49 mm.
    """
    breech = Mesh()
    # Cradle: the yoke bolted to the sponson face, with trunnion boxes.
    breech.box(steel, (-0.20, -0.20, 0.10), (0.20, 0.20, 0.30), 0.40)
    for side in (-1, 1):
        breech.box(dark, (side * 0.155, -0.13, 0.02), (side * 0.245, 0.13, 0.28), 0.40)
    # Hydraulic buffer and spring recuperator housings, fixed to the cradle.
    for y, r in ((0.135, 0.052), (-0.135, 0.048)):
        breech.cylinder(steel, (0, y, 0.28), (0, y, 0.40), r, 10, dark, 0.40)
        for z in (0.36,):
            breech.cylinder(dark, (0, y, z), (0, y, z + 0.06), r + 0.008, 10, dark, 0.40)
    # Breech ring, block and the casing behind it.
    breech.cylinder(steel, (0, 0, -0.02), (0, 0, 0.30), 0.115, 12, steel, 0.40)
    breech.box(dark, (-0.135, -0.135, -0.30), (0.135, 0.135, -0.02), 0.40)
    breech.cylinder(brass, (0, 0, -0.42), (0, 0, -0.30), 0.06, 10, brass, 0.40)
    # Elevating handwheel and the loader's grips.
    breech.cylinder(steel, (-0.26, -0.15, 0.02), (-0.16, -0.15, 0.02), 0.011, 6, steel)
    breech.cylinder(steel, (-0.16, -0.15, 0.02), (-0.16, -0.26, 0.02), 0.09, 12, dark, 0.40)
    breech.cylinder(steel, (-0.16, -0.15, 0.02), (-0.15, -0.15, 0.02), 0.024, 6, steel)
    breech.cylinder(steel, (0.11, 0.07, -0.06), (0.27, 0.17, -0.06), 0.014, 6, steel)  # firing lever
    breech.cylinder(steel, (0.11, -0.10, 0.04), (0.25, -0.14, 0.04), 0.016, 6, steel)
    if not lod:
        breech.box(brass, (-0.045, 0.115, -0.22), (0.045, 0.155, -0.10), 0.40)  # elevation scale

    barrel = Mesh()
    # Chase, reinforcing ring and the swelled muzzle; 16-gon so it reads round in game.
    barrel.cylinder(steel, (0, 0, 0.28), (0, 0, 0.62), 0.056, 16, steel, 0.40)
    barrel.cylinder(dark, (0, 0, 0.58), (0, 0, 0.70), 0.063, 16, dark, 0.40)
    barrel.cylinder(steel, (0, 0, 0.70), (0, 0, 1.05), 0.048, 16, steel, 0.40)
    barrel.cylinder(steel, (0, 0, 1.03), (0, 0, 1.10), 0.057, 16, steel, 0.40)
    barrel.cylinder(dark, (0, 0, 1.10), (0, 0, BARREL_TIP), 0.051, 16, dark, 0.40)
    # Piston rods of the buffer and recuperator, travelling with the gun.
    for y, r in ((0.135, 0.021), (-0.135, 0.019)):
        barrel.cylinder(dark, (0, y, 0.30), (0, y, 0.44), r, 8, dark, 0.40)
    # Iron front blade and rear notch; no modern optic.
    barrel.box(steel, (-0.025, 0.050, 1.96), (0.025, 0.100, 2.01), 0.30)
    barrel.box(steel, (-0.07, 0.075, 0.42), (0.07, 0.095, 0.48), 0.30)
    if not lod:
        barrel.cylinder(brass, (0, 0.095, 0.45), (0, 0.15, 0.45), 0.011, 6, brass)
        barrel.cylinder(brass, (0, -0.06, 0.34), (0, -0.06, 0.40), 0.026, 8, brass)
    return (breech, barrel)


def build(path: Path, lod=False) -> tuple[int, float]:
    b = GlbBuilder("British Mark I Male (1916), LOD1" if lod else "British Mark I Male (1916)")
    b.add_texture(atlas(), "mark_i_khaki_paint")          # texture 0 == textures/entity/mark_i/paint.png
    steel_tex = b.add_texture(steel_atlas(), "steel")
    b.add_texture(wood_atlas(), "elm_beam")
    brass_tex = b.add_texture(brass_atlas(), "brass")
    paint = b.add_material("British khaki enamel", (1.0, 1.0, 1.0, 1), 0.06, 0.86)
    highlight = b.add_material("sun-faded khaki plate", (1.0, 1.0, 0.93, 1), 0.05, 0.84)
    steel = b.add_material("blued steel", (0.62, 0.66, 0.66, 1), 0.74, 0.38, steel_tex)
    dark = b.add_material("dark steel", (0.38, 0.42, 0.40, 1), 0.66, 0.46, steel_tex)
    track = b.add_material("cast track steel", (0.50, 0.52, 0.47, 1), 0.56, 0.74, steel_tex)
    grouser = b.add_material("raised tread cleat", (0.72, 0.73, 0.64, 1), 0.58, 0.62, steel_tex)
    rubber = b.add_material("wheel rubber and shadow", (0.29, 0.32, 0.28, 1), 0.02, 0.97, steel_tex)
    wood = b.add_material("unditching beam, rough elm", (0.95, 0.90, 0.82, 1), 0.0, 0.92, 2)
    brass = b.add_material("aged brass fasteners", (0.95, 0.86, 0.62, 1), 0.68, 0.35, brass_tex)

    hull_i = b.add_mesh("mark_i_hull", hull_mesh(paint, highlight, steel, dark, brass, wood, lod))
    wheel_i = b.add_mesh("road_wheel", wheel_mesh(steel, rubber, ROAD_WHEEL_RADIUS))
    sprocket_i = b.add_mesh("end_wheel", wheel_mesh(steel, rubber, END_WHEEL_RADIUS))
    return_roller_i = b.add_mesh("return_roller", wheel_mesh(steel, rubber, RETURN_ROLLER_RADIUS))
    breech, barrel = gun_mesh(steel, dark, brass, lod)
    breech_i = b.add_mesh("qf_6pdr_breech", breech)
    barrel_i = b.add_mesh("qf_6pdr_recoiling_barrel", barrel)

    root = b.add_node("mark_i")
    hull = b.add_node("hull", hull_i)
    children = [hull]

    # Individual links follow a closed lozenge path; each link's translation and tangent rotation are animated
    # one pitch per clip cycle, so a complete cycle permutes identical shoes with no visible reset/pop.
    track_animations = []
    for side, name in ((1, "L"), (-1, "R")):
        children_side = []
        channels = []
        pitch = loop_length() / TRACK_LINKS
        poses = []
        for i in range(TRACK_LINKS):
            y, z, rx = loop_point(i * pitch)
            poses.append((side * 1.50, y, z, rx))
        for i, (x, y, z, rx) in enumerate(poses):
            shoe = Mesh()
            shoe.oriented_box(track, (0, 0, 0), (0.25, 0, 0), (0, TRACK_HALF_THICKNESS, 0), (0, 0, pitch * 0.46))
            if not lod:
                shoe.oriented_box(grouser, (0, 0, 0.017), (0.038, 0, 0), (0, 0.022, 0), (0, 0, pitch * 0.31))
                shoe.oriented_box(grouser, (0, 0, -0.017), (0.038, 0, 0), (0, 0.022, 0), (0, 0, pitch * 0.31))
                # Hinge knuckles either side, where the pin runs through.
                for kx in (-0.195, 0.195):
                    shoe.cylinder(dark, (kx, 0, -pitch * 0.34), (kx, 0, pitch * 0.34), 0.028, 6, dark, 0.30)
            shoe_i = b.add_mesh(f"track_shoe_{name}_{i:02d}", shoe)
            link = b.add_node(f"MarkI_link_{name}_{i:02d}", shoe_i, (x, y, z), axis_quaternion((1, 0, 0), rx))
            children_side.append(link)
            y2, z2, rx2 = loop_point((i + 1) * pitch)
            channels.extend([
                (link, "translation", [0.0, 1.0], [x, y, z, x, y2, z2]),
                (link, "rotation", [0.0, 1.0], [*axis_quaternion((1, 0, 0), rx), *axis_quaternion((1, 0, 0), rx2)]),
            ])
        group = b.add_node("track_links_" + name, children=children_side)
        children.append(group)
        track_animations.append(("track_" + name, channels))

    # Riveted track-frame armour: early British tanks enclose the running gear,
    # not exposed modern road wheels floating between a narrow belt and the hull.
    for side, name in ((1, "L"), (-1, "R")):
        frame = Mesh()
        profile = [(0.26, -2.40), (0.87, -3.12), (2.13, -3.12),
                   (2.13, 3.12), (0.87, 3.12), (0.26, 2.40)]
        extrude(frame, paint, profile, side * 1.25, side * 1.31, 0.80)
        if not lod:  # inspection hatch and a rivet row along the frame plate
            frame.box(steel, (side * 1.30, 0.62, -1.20), (side * 1.34, 1.02, -0.80), 0.30)
            rivets_x(frame, brass, side * 1.31, [(y, z) for y in (0.34, 2.06) for z in
                                                 (-2.9, -2.2, -1.5, -0.8, -0.1, 0.6, 1.3, 2.0, 2.7)])
        children.append(b.add_node("track_frame_" + name, b.add_mesh("track_frame_" + name, frame)))

    # Six road wheels plus the driven rear sprocket and front idler, animated by the existing tracked-vehicle pose.
    for side, name in ((1, "L"), (-1, "R")):
        for i in range(6):
            z = -1.78 + i * 0.70
            children.append(b.add_node(f"rw_{name}_{i}", wheel_i, (side * 1.38, ROAD_WHEEL_CENTER_Y, z)))
        for i, z in enumerate((-2.0, 0.0, 2.0)):
            children.append(b.add_node(f"rr_{name}_{i}", return_roller_i, (side * 1.38, RETURN_ROLLER_CENTER_Y, z)))
        children.append(b.add_node("spk_" + name, sprocket_i, (side * 1.38, 1.20, -3.20)))
        children.append(b.add_node("idl_" + name, sprocket_i, (side * 1.38, 1.20, 3.20)))
    # Independent side-sponson pivots are fixed outward by +/-90 deg; the runtime counter-rotates both to the aimed line.
    yaw_right = b.add_node("launcher_yaw", translation=(-SPONSON_X, SPONSON_Y, 0.0), rotation=axis_quaternion((0, 1, 0), -math.pi / 2))
    pitch_right = b.add_node("launcher_pitch")
    breech_right = b.add_node("gun_breech", breech_i)
    barrel_right = b.add_node("barrel", barrel_i)
    sight_right = b.add_node("iron_sight", translation=(0, 0.04, 0.17))
    b.set_children(pitch_right, [breech_right, barrel_right, sight_right])
    b.set_children(yaw_right, [pitch_right])
    yaw_left = b.add_node("launcher_yaw_left", translation=(SPONSON_X, SPONSON_Y, 0.0), rotation=axis_quaternion((0, 1, 0), math.pi / 2))
    pitch_left = b.add_node("launcher_pitch_left")
    breech_left = b.add_node("gun_breech_left", breech_i)
    barrel_left = b.add_node("barrel_left", barrel_i)
    sight_left = b.add_node("iron_sight_left", translation=(0, 0.04, 0.17))
    b.set_children(pitch_left, [breech_left, barrel_left, sight_left])
    b.set_children(yaw_left, [pitch_left])
    children.extend([yaw_right, yaw_left])
    b.set_children(root, children)
    for name, channels in track_animations:
        b.add_animation(name, channels)
    b.save(path, [root])
    # Independent stats for logs and test budgets.
    tri = sum(b.accessors[p["indices"]]["count"] // 3 for m in b.meshes for p in m["primitives"])
    return tri, loop_length() / TRACK_LINKS


def main() -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    (TEXTURE_DIR / "paint.png").write_bytes(atlas())
    (ITEM_DIR / "mark_i_vehicle.png").write_bytes(icon_mark_i())
    (ITEM_DIR / "shells_57.png").write_bytes(icon_shell())
    for name, lod in (("mark_i.glb", False), ("mark_i_lod1.glb", True)):
        tris, pitch = build(MODEL_DIR / name, lod)
        print(f"{MODEL_DIR / name}: {(MODEL_DIR / name).stat().st_size:,} bytes; {tris:,} triangles; links={TRACK_LINKS} pitch={pitch:.9f} m; LOD1={lod}")
    print("Regenerate walkable collision: python tools/deck/gen_boxes.py mark_i")


if __name__ == "__main__":
    main()
