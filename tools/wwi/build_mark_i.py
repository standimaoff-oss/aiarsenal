#!/usr/bin/env python3
"""Round-32 ground-up rebuild of the Mark I Male GLB (the round-27 geometry is gone).

Everything is re-derived from the Bovington / IWM drawings and built as real surfaces:
the hull is a lofted hexagonal armour section (sloped lower/upper side plates, cambered
roof, raked nose and tail), the sponsons are lofted octagonal casemates with chamfered
top/bottom and a recessed gun port, the running gear is a riveted frame plate with a
toothed sprocket, flanged road wheels and track shoes carrying a central guide horn.
Plate seams carry rivet rows; the paint atlas is a 512 px weathered khaki with seams,
rivet shadows and mud streaks.

Only the rig contract of the previous model is kept (node names, track path and pitch,
wheel radii, sponson pivots) because Java pose code, the deck collision and
tools/wwi/verify_assets.py test exactly those.

Run: python tools/wwi/build_mark_i.py     (needs no Blender)
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

# ---- rig contract (tested by verify_assets.py / GunVehicle.java) -------------------------
TRACK_PATH = [(0.13, -2.45), (0.82, -3.28), (2.28, -3.28), (2.28, 3.28),
              (0.82, 3.28), (0.13, 2.45)]
TRACK_LINKS = 56
TRACK_HALF_THICKNESS = 0.048
ROAD_WHEEL_RADIUS = 0.305
END_WHEEL_RADIUS = 0.385
RETURN_ROLLER_RADIUS = 0.115
ROAD_WHEEL_CENTER_Y = TRACK_PATH[0][0] + TRACK_HALF_THICKNESS + ROAD_WHEEL_RADIUS
RETURN_ROLLER_CENTER_Y = TRACK_PATH[2][0] - TRACK_HALF_THICKNESS - RETURN_ROLLER_RADIUS
SPONSON_X = 1.41
SPONSON_Y = 1.22
BARREL_TIP = 1.15
TRACK_X = 1.50          # centre plane of the belt
FRAME_IN, FRAME_OUT = 1.24, 1.31
HULL_X = 1.16           # hull side plate
SPONSON_OUT = 1.83

_CACHE: dict[tuple, bytes] = {}


# --------------------------------------------------------------------------- textures
def _noise_grid(rng: random.Random, n: int, cells: int) -> list[list[float]]:
    g = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * n for _ in range(n)]
    for y in range(n):
        for x in range(n):
            fx, fy = x / n * cells, y / n * cells
            x0, y0 = int(fx) % cells, int(fy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = fx - int(fx), fy - int(fy)
            tx = tx * tx * (3 - 2 * tx)
            ty = ty * ty * (3 - 2 * ty)
            out[y][x] = (g[y0][x0] * (1 - tx) + g[y0][x1] * tx) * (1 - ty) + \
                        (g[y1][x0] * (1 - tx) + g[y1][x1] * tx) * ty
    return out


def atlas() -> bytes:
    """512 px khaki armour plate: panel seams, rivet shadows, blotches, mud streaks."""
    key = ("atlas",)
    if key in _CACHE:
        return _CACHE[key]
    n = 512
    rng = random.Random(41)
    blotch = _noise_grid(rng, n, 9)
    grain = _noise_grid(rng, n, 64)
    pix = [(0, 0, 0, 255)] * (n * n)
    seams_y = [70, 190, 330, 452]          # horizontal plate seams
    seams_x = [128, 384]
    patches = [(30, 60, 150, 185, 1.10), (200, 75, 325, 185, 0.90), (340, 200, 470, 325, 1.08),
               (60, 340, 120, 448, 0.92), (395, 335, 500, 448, 1.06), (140, 200, 250, 325, 0.94)]
    rivets = [(x, y) for y in (62, 78, 182, 198, 322, 338, 444, 460)
              for x in range(24, n, 48)]
    for y in range(n):
        streak = 0.0
        for x in range(n):
            b = blotch[y][x]
            g = grain[y][x]
            r, gg, bb = 96 + 24 * b, 92 + 22 * b, 78 + 16 * b
            for px0, py0, px1, py1, pf in patches:            # replaced-plate patchwork
                if px0 <= x < px1 and py0 <= y < py1:
                    r, gg, bb = r * pf, gg * pf, bb * pf
            if any(abs(y - s) <= 1 for s in seams_y) or any(abs(x - s) <= 1 for s in seams_x):
                r, gg, bb = r * 0.42, gg * 0.42, bb * 0.40      # seam groove
            elif any(abs(y - s) <= 3 for s in seams_y) or any(abs(x - s) <= 3 for s in seams_x):
                r, gg, bb = r * 1.14, gg * 1.14, bb * 1.10      # lit edge under the seam
            for rx, ry in ((x, y),):
                pass
            streak += 0.0
            v = 0.90 + 0.20 * g
            pix[y * n + x] = (int(min(255, r * v)), int(min(255, gg * v)), int(min(255, bb * v)), 255)
    # rivet heads: bright dome + dark crescent under it
    for rx, ry in rivets:
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                d = math.hypot(dx, dy)
                if d > 3.2:
                    continue
                i = ((ry + dy) % n) * n + ((rx + dx) % n)
                base = pix[i]
                if d < 2.2:
                    f = 1.28 - 0.10 * d
                    pix[i] = (int(min(255, base[0] * f)), int(min(255, base[1] * f)), int(min(255, base[2] * f)), 255)
                elif dy > 0:
                    pix[i] = (int(base[0] * 0.62), int(base[1] * 0.62), int(base[2] * 0.60), 255)
    # mud streaks running down from the seams
    for s in seams_y:
        for k in range(46):
            x0 = rng.randrange(n)
            ln = rng.randrange(24, 96)
            w = rng.choice((1, 1, 2))
            for y in range(s, min(n, s + ln)):
                a = 1.0 - (y - s) / ln
                for dx in range(w):
                    i = y * n + ((x0 + dx) % n)
                    base = pix[i]
                    f = 1.0 - 0.30 * a
                    pix[i] = (int(base[0] * f), int(base[1] * f * 0.97), int(base[2] * f * 0.9), 255)
    png = png_rgba(n, n, pix)
    _CACHE[key] = png
    return png


def steel_atlas() -> bytes:
    key = ("steel",)
    if key in _CACHE:
        return _CACHE[key]
    n = 256
    rng = random.Random(7)
    g = _noise_grid(rng, n, 24)
    pix = [(0, 0, 0, 255)] * (n * n)
    for y in range(n):
        for x in range(n):
            v = 0.72 + 0.5 * g[y][x]
            scratch = 1.12 if (x * 7 + y * 13) % 97 == 0 else 1.0
            pix[y * n + x] = (int(min(255, 96 * v * scratch)), int(min(255, 100 * v * scratch)),
                              int(min(255, 98 * v * scratch)), 255)
    png = png_rgba(n, n, pix)
    _CACHE[key] = png
    return png


def wood_atlas() -> bytes:
    key = ("wood",)
    if key in _CACHE:
        return _CACHE[key]
    n = 128
    rng = random.Random(12)
    pix = [(0, 0, 0, 255)] * (n * n)
    for y in range(n):
        for x in range(n):
            ring = 0.5 + 0.5 * math.sin(x * 0.35 + 2.2 * math.sin(y * 0.05))
            v = 0.75 + 0.35 * ring + 0.08 * rng.random()
            pix[y * n + x] = (int(min(255, 128 * v)), int(min(255, 100 * v)), int(min(255, 62 * v)), 255)
    png = png_rgba(n, n, pix)
    _CACHE[key] = png
    return png


def icon_mark_i() -> bytes:
    n = 64
    pix = [(0, 0, 0, 0)] * (n * n)
    for y in range(n):
        for x in range(n):
            in_hull = 14 <= y <= 40 and 6 <= x <= 58
            in_track = (10 <= y <= 44) and (4 <= x <= 10 or 54 <= x <= 60)
            if in_hull or in_track:
                pix[y * n + x] = (96, 96, 70, 255)
    return png_rgba(n, n, pix)


def icon_shell() -> bytes:
    n = 64
    pix = [(0, 0, 0, 0)] * (n * n)
    for y in range(n):
        for x in range(n):
            if 26 <= x <= 38 and 8 <= y <= 56:
                brass = y < 34
                pix[y * n + x] = (184, 148, 70, 255) if brass else (110, 112, 96, 255)
    return png_rgba(n, n, pix)


# --------------------------------------------------------------------------- helpers
def loop_length(path=TRACK_PATH) -> float:
    tot = 0.0
    for i in range(len(path)):
        ay, az = path[i]
        by, bz = path[(i + 1) % len(path)]
        tot += math.hypot(by - ay, bz - az)
    return tot


def loop_point(distance: float, path=TRACK_PATH):
    """(y, z, roll about X) at an arc length along the closed track loop."""
    d = distance % loop_length(path)
    for i in range(len(path)):
        ay, az = path[i]
        by, bz = path[(i + 1) % len(path)]
        seg = math.hypot(by - ay, bz - az)
        if d <= seg:
            t = d / seg
            y, z = ay + (by - ay) * t, az + (bz - az) * t
            return y, z, math.atan2(by - ay, bz - az)
        d -= seg
    return path[0][0], path[0][1], 0.0


def rivet_row(mesh: Mesh, mat, p0, p1, count: int, r: float = 0.014, out=None) -> None:
    """A row of dome rivets from p0 to p1; out = unit normal the heads face."""
    p0 = tuple(p0)
    p1 = tuple(p1)
    for i in range(count):
        t = (i + 0.5) / count
        p = [p0[k] + (p1[k] - p0[k]) * t for k in range(3)]
        if out is None:
            d = [p1[k] - p0[k] for k in range(3)]
            n = max(range(3), key=lambda k: abs(d[k]))
            o = [0.0, 0.0, 0.0]
            o[n] = 1.0 if p[n] >= 0 else -1.0
        else:
            o = out
        tip = [p[k] + o[k] * r * 0.9 for k in range(3)]
        mesh.cylinder(mat, p, tip, r, 6, mat, 0.12)


def _n3(pts):
    a, b, c = pts[0], pts[1], pts[2]
    ab = (b[0]-a[0], b[1]-a[1], b[2]-a[2]); ac = (c[0]-a[0], c[1]-a[1], c[2]-a[2])
    return (ab[1]*ac[2]-ab[2]*ac[1], ab[2]*ac[0]-ab[0]*ac[2], ab[0]*ac[1]-ab[1]*ac[0])


def _cen(ring):
    n = len(ring)
    return tuple(sum(p[d] for p in ring) / n for d in range(3))


def loft_rings(m: Mesh, mat, rings, tile: float = 0.5) -> None:
    # Loft convex rings of equal point count; winding is corrected against the outward
    # radial direction because Minecraft back-face culls single-sided materials.
    for i in range(len(rings) - 1):
        a, b = rings[i], rings[i+1]
        ca = _cen(a)
        for k in range(len(a)):
            j = (k + 1) % len(a)
            quad = (a[k], a[j], b[j], b[k])
            n = _n3(quad); qc = _cen(quad)
            rad = (qc[0]-ca[0], qc[1]-ca[1], qc[2]-ca[2])
            if n[0]*rad[0] + n[1]*rad[1] + n[2]*rad[2] < 0:
                quad = tuple(reversed(quad))
            m.face(mat, quad, tile)
    for ring, other in ((rings[0], rings[1]), (rings[-1], rings[-2])):
        c = _cen(ring); o = _cen(other)
        d = (c[0]-o[0], c[1]-o[1], c[2]-o[2])
        for k in range(1, len(ring) - 1):
            tri = (ring[0], ring[k], ring[k+1])
            n = _n3(tri)
            if n[0]*d[0] + n[1]*d[1] + n[2]*d[2] < 0:
                tri = (ring[0], ring[k+1], ring[k])
            m.triangle(mat, tri[0], tri[1], tri[2])


def hex_ring(z: float, sx: float, y_lo: float, y_hi: float) -> list[tuple[float, float, float]]:
    """Hull armour section: chamfered belly, sloped side plates, cambered roof."""
    pts2 = [(-0.86 * sx, y_lo), (-HULL_X * sx, y_lo + 0.42), (-HULL_X * sx, y_hi - 0.50),
            (-0.88 * sx, y_hi), (0.0, y_hi + 0.05), (0.88 * sx, y_hi),
            (HULL_X * sx, y_hi - 0.50), (HULL_X * sx, y_lo + 0.42), (0.86 * sx, y_lo)]
    return [(x, y, z) for x, y in pts2]


def hull_mesh(paint, highlight, steel, dark, brass, wood, lod: bool) -> Mesh:
    m = Mesh()
    # ---- lofted hull: raked tail, parallel middle, raked nose
    sections = [
        hex_ring(-3.02, 0.34, 0.86, 1.42),
        hex_ring(-2.86, 0.62, 0.60, 1.78),
        hex_ring(-2.60, 0.86, 0.46, 1.98),
        hex_ring(-1.20, 1.00, 0.44, 2.02),
        hex_ring(0.60, 1.00, 0.44, 2.02),
        hex_ring(2.30, 0.92, 0.46, 1.99),
        hex_ring(2.86, 0.66, 0.62, 1.80),
        hex_ring(3.06, 0.36, 0.90, 1.44),
    ]
    loft_rings(m, paint, sections, 0.55)
    sides = 10 if not lod else 6
    # ---- driver's cab: lofted hood with a raked visor face
    cab = [
        [(-0.60, 2.00, 0.72), (0.60, 2.00, 0.72), (0.60, 2.00, 1.78), (-0.60, 2.00, 1.78)],
        [(-0.55, 2.38, 0.80), (0.55, 2.38, 0.80), (0.55, 2.38, 1.70), (-0.55, 2.38, 1.70)],
        [(-0.38, 2.50, 0.94), (0.38, 2.50, 0.94), (0.38, 2.50, 1.56), (-0.38, 2.50, 1.56)],
    ]
    loft_rings(m, highlight, cab, 0.4)
    m.box(dark, (-0.36, 2.28, 1.56), (0.36, 2.40, 1.64), 0.2)          # visor slit
    m.box(steel, (-0.40, 2.26, 1.52), (0.40, 2.44, 1.68), 0.2)         # slit frame
    # ---- roof furniture: two manhole coamings with lids, ventilator dome
    for cz, r in ((0.10, 0.30), (-1.30, 0.26)):
        m.cylinder(highlight, (0.0, 1.98, cz), (0.0, 2.10, cz), r, sides, highlight, 0.3)
        m.cylinder(steel, (0.0, 2.10, cz), (0.0, 2.145, cz), r * 0.94, sides, steel, 0.3)
        m.cylinder(brass, (0.0, 2.145, cz - r * 0.5), (0.0, 2.175, cz - r * 0.5), 0.03, 6, brass, 0.15)
    m.cylinder(steel, (0.34, 2.02, 0.95), (0.34, 2.16, 0.95), 0.09, sides, steel, 0.2)
    for rz in (-2.1, -0.8, 0.5, 1.8):
        m.box(highlight, (-0.78, 2.04, rz - 0.06), (0.78, 2.10, rz + 0.06), 0.3)
    # ---- tail: radiator louvres and exhaust outlets
    m.box(dark, (-0.60, 0.80, -3.02), (0.60, 1.70, -2.94), 0.3)
    for i in range(6):
        m.box(steel, (-0.55 + i * 0.19, 0.84, -3.06), (-0.47 + i * 0.19, 1.66, -2.99), 0.2)
    # ---- exhaust stacks along the roof edges with brackets and rain caps
    for sx in (-1.00, 1.00):
        # the real Mark I carries its exhausts above the top track run, along the roof edge
        m.cylinder(steel, (sx, 2.30, 0.55), (sx, 2.30, -2.72), 0.055, sides, steel, 0.25)
        m.cylinder(dark, (sx, 2.30, -2.72), (sx, 2.22, -2.86), 0.062, sides, dark, 0.25)
        if not lod:
            for bz in (0.2, -0.9, -2.0):
                m.box(steel, (sx - 0.02, 2.06, bz - 0.05), (sx + 0.02, 2.30, bz + 0.05), 0.15)
    # ---- unditching beam across the roof, tow shackles at the nose
    m.box(wood, (-1.05, 2.06, -0.55), (1.05, 2.20, -0.36), 0.4)
    for sx in (-0.7, 0.7):
        m.cylinder(steel, (sx, 1.10, 3.02), (sx, 1.10, 3.16), 0.035, 6, steel, 0.15)
    # ---- rivet rows along every plate seam of the hull
    if not lod:
        for sx in (-1, 1):
            for y in (0.92, 1.52):
                rivet_row(m, brass, (sx * HULL_X, y, -2.55), (sx * HULL_X, y, 2.25), 26)
            rivet_row(m, brass, (sx * 0.90, 2.03, -2.50), (sx * 0.90, 2.03, 2.20), 24)
        rivet_row(m, brass, (-0.80, 1.30, 2.98), (0.80, 1.30, 2.98), 10)
        rivet_row(m, brass, (-0.46, 2.30, 0.80), (0.46, 2.30, 0.80), 8)
        rivet_row(m, brass, (-0.46, 2.30, 1.64), (0.46, 2.30, 1.64), 8)
    return m


def sponson_mesh(paint, highlight, steel, dark, brass, side: int, lod: bool) -> Mesh:
    """Octagonal side casemate: chamfered top/bottom, raked front corners, gun port."""
    m = Mesh()
    s = side
    plan = [(1.02, 1.10), (1.02, 1.52), (0.70, SPONSON_OUT), (-0.70, SPONSON_OUT),
            (-1.02, 1.52), (-1.02, 1.10)]
    secs = []
    for y, inset in ((0.90, 0.24), (1.06, 0.0), (1.72, 0.0), (1.92, 0.26)):
        ring = [(s * (x - inset * 0.9) if x > 1.2 else s * x, y, z * (1.0 - inset * 0.28)) for z, x in plan]
        secs.append([(px, py, pz) for px, py, pz in ring])
    loft_rings(m, paint, secs, 0.5)
    # gun port: recessed dark disc, mantlet ring and the embrasure frame on the outer face
    m.cylinder(dark, (s * (SPONSON_OUT - 0.06), 1.38, 0.0), (s * (SPONSON_OUT + 0.01), 1.38, 0.0), 0.17, 12, dark, 0.3)
    m.cylinder(steel, (s * (SPONSON_OUT - 0.02), 1.38, 0.0), (s * (SPONSON_OUT + 0.03), 1.38, 0.0), 0.21, 12, steel, 0.3)
    # vision slits in the raked front corners, inspection hatch on the side
    for zc in (-0.80, 0.80):
        m.box(dark, (s * 1.60, 1.44, zc - 0.10), (s * 1.72, 1.52, zc + 0.10), 0.15)
    m.box(steel, (s * (SPONSON_OUT - 0.01), 1.06, -0.62), (s * (SPONSON_OUT + 0.02), 1.30, -0.30), 0.25)
    if not lod:
        for y in (1.06, 1.72):
            rivet_row(m, brass, (s * 1.50, y, -0.90), (s * 1.50, y, 0.90), 12)
        rivet_row(m, brass, (s * SPONSON_OUT, 1.72, -0.60), (s * SPONSON_OUT, 1.72, 0.60), 9)
        rivet_row(m, brass, (s * SPONSON_OUT, 1.06, -0.60), (s * SPONSON_OUT, 1.06, 0.60), 9)
    return m


def wheel_mesh(steel, rubber, radius: float, teeth: int = 0) -> Mesh:
    m = Mesh()
    sides = 14
    m.cylinder(steel, (-0.09, 0, 0), (0.09, 0, 0), radius, sides, steel, 0.4)      # rim
    m.cylinder(rubber, (-0.11, 0, 0), (0.11, 0, 0), radius * 0.86, sides, rubber, 0.4)
    m.cylinder(steel, (-0.13, 0, 0), (0.13, 0, 0), radius * 0.34, 10, steel, 0.3)  # hub
    for i in range(8):
        a = i * math.tau / 8
        m.cylinder(steel, (-0.10, math.sin(a) * radius * 0.55, math.cos(a) * radius * 0.55),
                   (0.10, math.sin(a) * radius * 0.55, math.cos(a) * radius * 0.55), 0.022, 6, steel, 0.2)
    if teeth:
        for i in range(teeth):
            a = i * math.tau / teeth
            y, z = math.sin(a) * radius, math.cos(a) * radius
            m.oriented_box(steel, (0, y * 0.78, z * 0.78), (0.09, 0, 0),
                           (0, y * 0.14, z * 0.14), (0, -z * 0.09, y * 0.09))
    return m


def gun_mesh(steel, dark, brass, lod: bool):
    """Cradle/breech and the recoiling Hotchkiss 6-pdr tube, in sponson-local space (+Z out)."""
    breech = Mesh()
    breech.box(steel, (-0.14, -0.16, -0.52), (0.14, 0.12, -0.02), 0.3)            # breech block
    breech.box(dark, (-0.10, -0.20, -0.46), (0.10, -0.14, -0.06), 0.25)           # recoil cradle
    breech.cylinder(steel, (-0.09, -0.02, -0.44), (-0.09, -0.02, -0.04), 0.035, 8, steel, 0.25)
    breech.cylinder(steel, (0.09, -0.02, -0.44), (0.09, -0.02, -0.04), 0.035, 8, steel, 0.25)
    breech.cylinder(steel, (-0.20, -0.02, 0.0), (0.20, -0.02, 0.0), 0.05, 8, steel, 0.25)   # trunnion
    breech.box(brass, (-0.03, 0.10, -0.30), (0.03, 0.16, -0.24), 0.15)            # sight boss
    barrel = Mesh()
    sides = 10 if not lod else 6
    secs = []
    for z, r in ((-0.06, 0.048), (0.10, 0.042), (0.34, 0.038), (0.62, 0.033), (BARREL_TIP - 0.09, 0.028),
                 (BARREL_TIP - 0.09, 0.033), (BARREL_TIP, 0.031), (BARREL_TIP, 0.023)):
        ring = []
        for i in range(sides):
            a = i * math.tau / sides
            ring.append((math.cos(a) * r, math.sin(a) * r - 0.02, z))
        secs.append(ring)
    loft_rings(barrel, steel, secs, 0.3)
    barrel.cylinder(dark, (0, -0.02, BARREL_TIP - 0.005), (0, -0.02, BARREL_TIP + 0.012), 0.020, sides, dark, 0.2)
    if not lod:
        barrel.cylinder(brass, (0, -0.06, 0.34), (0, -0.06, 0.40), 0.026, 8, brass, 0.2)
    return breech, barrel


def frame_mesh(paint, steel, brass, side: int, lod: bool) -> Mesh:
    """Riveted track-frame armour enclosing the running gear (early British practice)."""
    m = Mesh()
    profile = [(0.20, -2.36), (0.78, -3.16), (2.20, -3.16),
               (2.20, 3.16), (0.78, 3.16), (0.20, 2.36)]
    pts = [(side * FRAME_IN, y, z) for y, z in profile] + [(side * FRAME_OUT, y, z) for y, z in reversed(profile)]
    n = len(profile)
    for i in range(n):
        a, b = i, (i + 1) % n
        m.face(paint, [pts[a], pts[b], pts[n + b], pts[n + a]], 0.6)
    for ring, sgn in ((pts[:n], -1.0), (pts[n:], 1.0)):
        for k in range(1, n - 1):
            tri = (ring[0], ring[k], ring[k + 1])
            nn = _n3(tri)
            if nn[0] * side * sgn < 0:
                tri = (tri[0], tri[2], tri[1])
            m.triangle(paint, tri[0], tri[1], tri[2])
    # axle bosses break the flat plate: big discs at sprocket/idler, small ones at the road wheels
    for cy, cz, r in ((1.20, -3.20, 0.50), (1.20, 3.20, 0.50)):
        m.cylinder(steel, (side * (FRAME_OUT - 0.01), cy, cz), (side * (FRAME_OUT + 0.035), cy, cz), r, 14, steel, 0.35)
        m.cylinder(brass, (side * (FRAME_OUT + 0.035), cy, cz), (side * (FRAME_OUT + 0.06), cy, cz), 0.09, 8, brass, 0.2)
    for i in range(6):
        z = -1.78 + i * 0.70
        m.cylinder(steel, (side * (FRAME_OUT - 0.01), ROAD_WHEEL_CENTER_Y, z),
                   (side * (FRAME_OUT + 0.02), ROAD_WHEEL_CENTER_Y, z), 0.10, 10, steel, 0.25)
    # external exhaust stack along the side, above the sponson line - reads in a side silhouette
    m.cylinder(steel, (side * 1.20, 1.98, 0.90), (side * 1.20, 1.98, -2.50), 0.05, 10, steel, 0.25)
    m.cylinder(steel, (side * 1.20, 1.98, -2.50), (side * 1.16, 1.90, -2.66), 0.056, 10, steel, 0.25)
    if not lod:
        m.box(steel, (side * (FRAME_OUT - 0.01), 0.62, -1.20), (side * (FRAME_OUT + 0.03), 1.02, -0.80), 0.30)
        for y in (0.34, 2.14):
            rivet_row(m, brass, (side * FRAME_OUT, y, -2.9), (side * FRAME_OUT, y, 2.7), 18)
        for z in (-2.6, 2.6):
            rivet_row(m, brass, (side * FRAME_OUT, 0.5, z), (side * FRAME_OUT, 2.0, z), 8)
        for bz in (0.5, -0.8, -2.0):
            m.box(steel, (side * 1.16, 1.90, bz - 0.05), (side * 1.24, 1.98, bz + 0.05), 0.15)
    return m


def build(path: Path, lod=False) -> tuple[int, float]:
    b = GlbBuilder("British Mark I Male (1916), LOD1" if lod else "British Mark I Male (1916)")
    b.add_texture(atlas(), "mark_i_khaki_paint")
    steel_tex = b.add_texture(steel_atlas(), "steel")
    b.add_texture(wood_atlas(), "elm_beam")
    paint = b.add_material("British khaki enamel", (1.0, 1.0, 1.0, 1), 0.06, 0.86)
    highlight = b.add_material("sun-faded khaki plate", (1.0, 1.0, 0.93, 1), 0.05, 0.84)
    steel = b.add_material("blued steel", (0.62, 0.66, 0.66, 1), 0.74, 0.38, steel_tex)
    dark = b.add_material("dark steel", (0.30, 0.33, 0.32, 1), 0.66, 0.46, steel_tex)
    track = b.add_material("cast track steel", (0.50, 0.52, 0.47, 1), 0.56, 0.74, steel_tex)
    grouser = b.add_material("raised tread cleat", (0.72, 0.73, 0.64, 1), 0.58, 0.62, steel_tex)
    rubber = b.add_material("wheel rubber and shadow", (0.29, 0.32, 0.28, 1), 0.02, 0.97, steel_tex)
    wood = b.add_material("unditching beam, rough elm", (0.95, 0.90, 0.82, 1), 0.0, 0.92, 2)
    brass = b.add_material("aged brass fasteners", (0.95, 0.86, 0.62, 1), 0.68, 0.35)

    hull_i = b.add_mesh("mark_i_hull", hull_mesh(paint, highlight, steel, dark, brass, wood, lod))
    spon_r = b.add_mesh("sponson_R", sponson_mesh(paint, highlight, steel, dark, brass, -1, lod))
    spon_l = b.add_mesh("sponson_L", sponson_mesh(paint, highlight, steel, dark, brass, 1, lod))
    wheel_i = b.add_mesh("road_wheel", wheel_mesh(steel, rubber, ROAD_WHEEL_RADIUS))
    sprocket_i = b.add_mesh("end_wheel", wheel_mesh(steel, rubber, END_WHEEL_RADIUS, teeth=0 if lod else 14))
    roller_i = b.add_mesh("return_roller", wheel_mesh(steel, rubber, RETURN_ROLLER_RADIUS))
    breech, barrel = gun_mesh(steel, dark, brass, lod)
    breech_i = b.add_mesh("qf_6pdr_breech", breech)
    barrel_i = b.add_mesh("qf_6pdr_recoiling_barrel", barrel)

    root = b.add_node("mark_i")
    children = [b.add_node("hull", hull_i), b.add_node("sponson_R", spon_r), b.add_node("sponson_L", spon_l)]

    track_animations = []
    pitch = loop_length() / TRACK_LINKS
    for side, name in ((1, "L"), (-1, "R")):
        kids, channels = [], []
        for i in range(TRACK_LINKS):
            y, z, rx = loop_point(i * pitch)
            shoe = Mesh()
            shoe.oriented_box(track, (0, 0, 0), (0.24, 0, 0), (0, TRACK_HALF_THICKNESS, 0), (0, 0, pitch * 0.46))
            if not lod:
                # two grouser bars and the central guide horn that runs in the track race
                shoe.oriented_box(grouser, (0, -0.005, 0.018), (0.036, 0, 0), (0, 0.024, 0), (0, 0, pitch * 0.30))
                shoe.oriented_box(grouser, (0, -0.005, -0.018), (0.036, 0, 0), (0, 0.024, 0), (0, 0, pitch * 0.30))
                shoe.oriented_box(grouser, (0, TRACK_HALF_THICKNESS, 0), (0.030, 0, 0), (0, 0.055, 0), (0, 0, pitch * 0.16))
                for kx in (-0.19, 0.19):
                    shoe.cylinder(dark, (kx, 0, -pitch * 0.34), (kx, 0, pitch * 0.34), 0.026, 6, dark, 0.30)
            shoe_i = b.add_mesh(f"track_shoe_{name}_{i:02d}", shoe)
            link = b.add_node(f"MarkI_link_{name}_{i:02d}", shoe_i, (side * TRACK_X, y, z), axis_quaternion((1, 0, 0), rx))
            kids.append(link)
            y2, z2, rx2 = loop_point((i + 1) * pitch)
            channels.extend([
                (link, "translation", [0.0, 1.0], [side * TRACK_X, y, z, side * TRACK_X, y2, z2]),
                (link, "rotation", [0.0, 1.0], [*axis_quaternion((1, 0, 0), rx), *axis_quaternion((1, 0, 0), rx2)]),
            ])
        children.append(b.add_node("track_links_" + name, children=kids))
        track_animations.append(("track_" + name, channels))
        children.append(b.add_node("track_frame_" + name,
                                   b.add_mesh("track_frame_" + name, frame_mesh(paint, steel, brass, side, lod))))
        for i in range(6):
            z = -1.78 + i * 0.70
            children.append(b.add_node(f"rw_{name}_{i}", wheel_i, (side * 1.38, ROAD_WHEEL_CENTER_Y, z)))
        for i, z in enumerate((-2.0, 0.0, 2.0)):
            children.append(b.add_node(f"rr_{name}_{i}", roller_i, (side * 1.38, RETURN_ROLLER_CENTER_Y, z)))
        children.append(b.add_node("spk_" + name, sprocket_i, (side * 1.38, 1.20, -3.20)))
        children.append(b.add_node("idl_" + name, sprocket_i, (side * 1.38, 1.20, 3.20)))

    for tag, sx, rot in (("right", -SPONSON_X, -math.pi / 2), ("left", SPONSON_X, math.pi / 2)):
        suf = "" if tag == "right" else "_left"
        yaw = b.add_node("launcher_yaw" + suf, translation=(sx, SPONSON_Y, 0.0), rotation=axis_quaternion((0, 1, 0), rot))
        pitch_n = b.add_node("launcher_pitch" + suf)
        br = b.add_node("gun_breech" + suf, breech_i)
        ba = b.add_node("barrel" + suf, barrel_i)
        si = b.add_node("iron_sight" + suf, translation=(0, 0.04, 0.17))
        b.set_children(pitch_n, [br, ba, si])
        b.set_children(yaw, [pitch_n])
        children.append(yaw)
    b.set_children(root, children)
    for name, channels in track_animations:
        b.add_animation(name, channels)
    b.save(path, [root])
    tri = sum(b.accessors[p["indices"]]["count"] // 3 for m in b.meshes for p in m["primitives"])
    return tri, pitch


def main() -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    (TEXTURE_DIR / "paint.png").write_bytes(atlas())
    (ITEM_DIR / "mark_i_vehicle.png").write_bytes(icon_mark_i())
    (ITEM_DIR / "shells_57.png").write_bytes(icon_shell())
    for name, lod in (("mark_i.glb", False), ("mark_i_lod1.glb", True)):
        tris, pitch = build(MODEL_DIR / name, lod)
        print(f"{MODEL_DIR / name}: {(MODEL_DIR / name).stat().st_size:,} bytes; {tris:,} triangles; "
              f"links={TRACK_LINKS} pitch={pitch:.9f} m; LOD1={lod}")


if __name__ == "__main__":
    main()
