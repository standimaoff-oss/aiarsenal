#!/usr/bin/env python3
"""Scratch-built WWI Mauser Gewehr 98 (1898 pattern, 7.92x57 mm) GLB generator.

Constructed from blueprints and period rifles:
  * Rounded 10-sided walnut/beech stock lofted through 22 anatomical stations (buttplate heel/toe,
    fluted comb, grasping grooves, slender wrist, semi-pistol grip cap, recoil cross-bolt,
    finger-grooved fore-end, upper handguard ahead of the Lange Visier).
  * Cylindrical Mauser 98 receiver with front locking ring (dia 35 mm), thumb cut, ejection port,
    stripper-clip charger bridge, rear tang and three-position flag safety wing.
  * 740 mm stepped barrel with nockform chamber taper, Lange Visier ("roller-coaster" tangent rear
    sight with curved ramp walls and sliding crossbar), Oberring H-type nose cap with bayonet bar,
    Mittelring with sling loop, cleaning rod, trigger guard bow + floorplate + curved trigger.
  * Straight Mauser bolt with cylindrical stem, spherical bolt knob, extractor collar and cocking piece.
  * Five-round 7.92x57 mm S-Patrone stripper clip (bottlenecked brass case + cupronickel spitzer bullet).
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path
from typing import Iterable, Sequence

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from glb_builder import GlbBuilder, Mesh, axis_quaternion, png_rgba  # noqa: E402

Vec3 = tuple[float, float, float]

ASSETS = ROOT / "mods/aiarsenal/src/main/resources/assets/aiarsenal"
MODEL_DIR = ASSETS / "models/glb"
TEXTURE_DIR = ASSETS / "textures/entity/gewehr98"
ITEM_DIR = ASSETS / "textures/item"

BORE_Y = 0.022
BUTT_Z = -0.495
MUZZLE_Z = 0.728
RECEIVER_Z0, RECEIVER_Z1 = -0.092, 0.122


def _n3(a: Vec3, b: Vec3, c: Vec3) -> Vec3:
    ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
    nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
    l = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
    return (nx / l, ny / l, nz / l)


def _cen(pts: Iterable[Vec3]) -> Vec3:
    pts = list(pts)
    n = len(pts) or 1
    return (
        sum(p[0] for p in pts) / n,
        sum(p[1] for p in pts) / n,
        sum(p[2] for p in pts) / n,
    )


def loft_rings(
    m: Mesh,
    mat: int,
    rings: Sequence[Sequence[Vec3]],
    uv_scale: float = 0.25,
    cap_start: bool = True,
    cap_end: bool = True,
) -> None:
    """Loft arbitrary N-gon cross-section rings with outward-guaranteed winding."""
    k = len(rings[0])
    all_pts = [p for r in rings for p in r]
    cx, cy, cz = _cen(all_pts)
    for r0, r1 in zip(rings, rings[1:]):
        for i in range(k):
            j = (i + 1) % k
            a, b, c, d = r0[i], r0[j], r1[j], r1[i]
            mx, my, mz = _cen((a, b, c, d))
            nx, ny, nz = _n3(a, b, c)
            if nx * (mx - cx) + ny * (my - cy) + nz * (mz - cz) >= 0:
                m.face(mat, [a, b, c, d], uv_scale)
            else:
                m.face(mat, [d, c, b, a], uv_scale)
    if cap_start:
        r0 = rings[0]
        c0 = _cen(r0)
        nx, ny, nz = _n3(c0, r0[1], r0[0])
        flip = (nx * (c0[0] - cx) + ny * (c0[1] - cy) + nz * (c0[2] - cz)) < 0
        for i in range(k):
            j = (i + 1) % k
            if not flip:
                m.triangle(mat, c0, r0[j], r0[i])
            else:
                m.triangle(mat, c0, r0[i], r0[j])
    if cap_end:
        r1 = rings[-1]
        c1 = _cen(r1)
        nx, ny, nz = _n3(c1, r1[0], r1[1])
        flip = (nx * (c1[0] - cx) + ny * (c1[1] - cy) + nz * (c1[2] - cz)) < 0
        for i in range(k):
            j = (i + 1) % k
            if not flip:
                m.triangle(mat, c1, r1[i], r1[j])
            else:
                m.triangle(mat, c1, r1[j], r1[i])


def stock_ring(z: float, hw: float, y_bot: float, y_top: float, crown: float = 0.25) -> list[Vec3]:
    """10-point rounded rifle stock cross-section at station z."""
    ym = 0.5 * (y_bot + y_top)
    hh = 0.5 * (y_top - y_bot)
    # Angles around the section from top-right clockwise; top is open/crowned, bottom is rounded keel
    pts: list[Vec3] = []
    angles = (0.18, 0.42, 0.68, 0.90, 1.0, 0.90, 0.68, 0.42, 0.18)
    # Build 10 vertices: top-left, upper-left, mid-left, lower-left, bottom-left,
    # bottom-right, lower-right, mid-right, upper-right, top-right
    profile = [
        (-hw * 0.62, y_top),
        (-hw * 0.92, y_top - hh * (0.18 * (1.0 - crown * 0.3))),
        (-hw * 1.00, ym + hh * 0.12),
        (-hw * 0.92, ym - hh * 0.42),
        (-hw * 0.52, y_bot),
        (hw * 0.52, y_bot),
        (hw * 0.92, ym - hh * 0.42),
        (hw * 1.00, ym + hh * 0.12),
        (hw * 0.92, y_top - hh * (0.18 * (1.0 - crown * 0.3))),
        (hw * 0.62, y_top),
    ]
    for x, y in profile:
        pts.append((x, y, z))
    return pts


def cyl_rings_z(
    cx: float, cy: float, z_r: Sequence[tuple[float, float]], sides: int = 16
) -> list[list[Vec3]]:
    rings: list[list[Vec3]] = []
    for z, r in z_r:
        ring: list[Vec3] = []
        for i in range(sides):
            a = math.tau * i / sides
            ring.append((cx + r * math.cos(a), cy + r * math.sin(a), z))
        rings.append(ring)
    return rings


def beech_texture() -> bytes:
    """256x128 oil-rubbed walnut/beech stock texture with rich figure, pores and dark oil shading."""
    w, h = 256, 128
    rng = random.Random(1898)
    pixels = []
    for y in range(h):
        for x in range(w):
            u = x / w
            v = y / h
            wave = math.sin(u * 22.0 + 1.8 * math.sin(v * 9.5) + 0.6 * math.cos(u * 7.0 + v * 14.0))
            cathedral = math.sin(u * 9.0 + 2.6 * math.sin(v * 5.2))
            fine = math.sin(x * 1.35 + y * 0.18) * 3.5 + rng.randrange(-4, 5)
            pore = -10 if (rng.random() < 0.035 and (x % 5 == 0)) else 0
            # Warm dark walnut / linseed-oiled beech tone (not bright yellow)
            r0, g0, b0 = 118, 74, 42
            delta = int(11.0 * wave + 7.0 * cathedral + fine + pore)
            r = max(28, min(210, r0 + delta))
            g = max(20, min(165, g0 + int(delta * 0.78)))
            b = max(14, min(120, b0 + int(delta * 0.55)))
            pixels.append((r, g, b, 255))
    return png_rgba(w, h, pixels)


def steel_texture() -> bytes:
    """256x256 rust-blued Mauser gun steel with subtle brushed sheen and oil variation."""
    w = h = 256
    rng = random.Random(98)
    pixels = []
    for y in range(h):
        for x in range(w):
            brush = 4.0 * math.sin(x * 0.65) + 2.5 * math.cos(y * 0.42 + x * 0.15)
            patina = 5.0 * math.sin(x * 0.07 + 1.4 * math.sin(y * 0.09))
            n = int(brush + patina + rng.randrange(-3, 4))
            # Deep charcoal-blue steel
            r = max(24, min(150, 56 + n))
            g = max(28, min(160, 62 + n))
            b = max(34, min(175, 70 + n))
            pixels.append((r, g, b, 255))
    return png_rgba(w, h, pixels)


def _poly_fill(pixels, w, h, points, color):
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
                pixels[y * w + x] = color


def item_icon() -> bytes:
    """Crisp 64x64 inventory silhouette of the Gewehr 98."""
    w = h = 64
    p = [(0, 0, 0, 0)] * (w * h)

    def poly(points, color):
        _poly_fill(p, w, h, points, color)

    def rect(x0, y0, x1, y1, color):
        poly(((x0, y0), (x1, y0), (x1, y1), (x0, y1)), color)

    wood = (128, 82, 46, 255)
    wood_dark = (88, 54, 30, 255)
    steel = (64, 70, 78, 255)
    steel_light = (118, 126, 136, 255)
    ink = (30, 26, 24, 255)
    poly(((3, 34), (7, 31), (12, 31), (14, 33), (22, 33), (24, 30), (30, 29), (33, 28),
          (48, 28), (50, 30), (30, 31), (26, 33), (17, 36), (12, 39), (6, 40), (3, 38)), wood)
    poly(((3, 34), (6, 34), (6, 39), (3, 38)), wood_dark)
    poly(((24, 30), (30, 29), (33, 28), (33, 30), (27, 31)), wood_dark)
    rect(44, 27, 61, 29, steel)
    rect(59, 25, 61, 30, steel_light)
    rect(49, 24, 52, 27, steel_light)
    rect(35, 30, 56, 31, steel)
    rect(29, 25, 42, 30, steel)
    rect(31, 22, 34, 25, steel)
    poly(((34, 24), (37, 20), (39, 21), (36, 25)), steel_light)
    poly(((24, 36), (28, 36), (28, 39), (24, 39)), ink)
    return png_rgba(w, h, p)


# 22 anatomical stations (z, half-width, y_bottom, y_top) for the rounded 10-sided stock
STOCK_STATIONS = [
    (BUTT_Z, 0.0225, -0.0560, 0.0480),
    (-0.472, 0.0240, -0.0540, 0.0510),
    (-0.425, 0.0245, -0.0480, 0.0495),
    (-0.365, 0.0235, -0.0410, 0.0445),
    (-0.305, 0.0220, -0.0350, 0.0365),
    (-0.250, 0.0195, -0.0330, 0.0255),
    (-0.205, 0.0175, -0.0420, 0.0175),  # semi-pistol grip swell
    (-0.170, 0.0168, -0.0440, 0.0135),  # pistol grip cap
    (-0.138, 0.0172, -0.0310, 0.0115),  # slender wrist behind tang
    (-0.095, 0.0210, -0.0260, 0.0145),  # rear of receiver bed
    (-0.035, 0.0228, -0.0250, 0.0165),
    (0.025, 0.0232, -0.0240, 0.0170),
    (0.090, 0.0228, -0.0220, 0.0175),
    (0.145, 0.0218, -0.0190, 0.0175),
    (0.215, 0.0208, -0.0165, 0.0175),
    (0.295, 0.0198, -0.0145, 0.0170),
    (0.365, 0.0188, -0.0125, 0.0165),
    (0.435, 0.0176, -0.0105, 0.0160),
    (0.505, 0.0164, -0.0085, 0.0155),
    (0.555, 0.0152, -0.0070, 0.0150),
    (0.585, 0.0140, -0.0055, 0.0140),
]


def stock_mesh(wood: int, wood_dark: int, steel: int, dark: int, brass: int) -> Mesh:
    m = Mesh()
    # 1) Rounded 10-sided walnut stock
    rings = [stock_ring(z, hw, yb, yt) for (z, hw, yb, yt) in STOCK_STATIONS]
    loft_rings(m, wood, rings, 0.25)

    # 2) Upper wooden handguard between the Lange Visier rear sight and the middle band
    hg_rings = []
    for z, r_out in ((0.220, 0.0178), (0.285, 0.0172), (0.350, 0.0165), (0.398, 0.0158)):
        ring: list[Vec3] = []
        for i in range(9):
            a = math.pi * (0.08 + 0.84 * i / 8.0)  # arch over the top of the barrel
            ring.append((r_out * math.cos(a), BORE_Y - 0.002 + r_out * math.sin(a), z))
        hg_rings.append(ring)
    loft_rings(m, wood_dark, hg_rings, 0.20)

    # 3) Cupped steel buttplate + Stempelplatte (brass takedown disc in the buttstock)
    bp_rings = [
        stock_ring(BUTT_Z - 0.010, 0.0218, -0.0550, 0.0470),
        stock_ring(BUTT_Z + 0.004, 0.0230, -0.0565, 0.0485),
    ]
    loft_rings(m, steel, bp_rings, 0.18)
    m.cylinder(brass, (-0.0218, -0.008, -0.375), (0.0218, -0.008, -0.375), 0.0115, 14, brass, 0.15)
    m.cylinder(dark, (-0.0222, -0.008, -0.375), (0.0222, -0.008, -0.375), 0.0040, 10, dark, 0.12)

    # 4) Grasping grooves (Fingerrillen) milled into the fore-end sides
    for i in range(7):
        z = 0.150 + i * 0.024
        for sx in (-1.0, 1.0):
            m.cylinder(wood_dark, (sx * 0.0205, 0.005, z), (sx * 0.0205, 0.005, z + 0.015), 0.0042, 8, wood_dark, 0.12)

    # 5) Recoil cross-bolt (Querschraube) under the receiver ring
    m.cylinder(steel, (-0.0238, 0.004, 0.095), (0.0238, 0.004, 0.095), 0.0055, 10, steel, 0.12)

    # 6) Cylindrical Mauser 98 receiver: front ring (dia 35 mm), sidewalls, charger bridge, tang
    rec_stations = [
        (RECEIVER_Z0 - 0.018, 0.0125),
        (RECEIVER_Z0, 0.0168),
        (-0.045, 0.0172),
        (0.055, 0.0172),
        (0.078, 0.0182),   # heavy front locking-lug ring
        (RECEIVER_Z1, 0.0182),
        (RECEIVER_Z1 + 0.008, 0.0155),
    ]
    loft_rings(m, steel, cyl_rings_z(0.0, BORE_Y, rec_stations, 16), 0.18)
    # Stripper-clip charger guide bridge (Hülsenbrücke) with milled slot
    m.box(steel, (-0.0175, BORE_Y + 0.012, -0.058), (0.0175, BORE_Y + 0.0225, -0.028), 0.14)
    m.box(dark, (-0.0065, BORE_Y + 0.016, -0.060), (0.0065, BORE_Y + 0.0235, -0.026), 0.12)
    # Left receiver wall thumb cut + dark ejection port recess on top-right
    m.cylinder(steel, (-0.0165, BORE_Y + 0.004, -0.025), (-0.0165, BORE_Y + 0.004, 0.065), 0.0065, 10, steel, 0.14)
    m.box(dark, (-0.004, BORE_Y + 0.011, -0.024), (0.0165, BORE_Y + 0.0188, 0.068), 0.12)

    # 7) Stepped 740 mm barrel (Nockform chamber cone -> main taper -> muzzle crown + bore)
    barrel_profile = [
        (RECEIVER_Z1, 0.0152),
        (0.165, 0.0145),
        (0.225, 0.0132),
        (0.395, 0.0112),
        (0.585, 0.0096),
        (0.710, 0.0088),
        (MUZZLE_Z, 0.0088),
    ]
    loft_rings(m, steel, cyl_rings_z(0.0, BORE_Y, barrel_profile, 16), 0.20)
    m.cylinder(dark, (0.0, BORE_Y, MUZZLE_Z - 0.002), (0.0, BORE_Y, MUZZLE_Z + 0.0015), 0.0052, 12, dark, 0.10)

    # 8) Lange Visier ("roller-coaster" tangent rear sight, iconic to the WWI Gewehr 98)
    m.cylinder(dark, (0.0, BORE_Y, 0.152), (0.0, BORE_Y, 0.222), 0.0152, 14, dark, 0.14)
    # Left & right curved quadrant walls
    for sx in (-0.0115, 0.0085):
        m.box(steel, (sx, BORE_Y + 0.012, 0.154), (sx + 0.003, BORE_Y + 0.024, 0.220), 0.10)
        m.box(steel, (sx, BORE_Y + 0.022, 0.166), (sx + 0.003, BORE_Y + 0.030, 0.204), 0.10)
    # Tangent sight leaf + cross-slider + V-notch rear aperture
    m.box(steel, (-0.0085, BORE_Y + 0.022, 0.156), (0.0085, BORE_Y + 0.0265, 0.216), 0.10)
    m.box(dark, (-0.0145, BORE_Y + 0.021, 0.180), (0.0145, BORE_Y + 0.029, 0.194), 0.10)
    m.box(steel, (-0.0095, BORE_Y + 0.0265, 0.156), (-0.0018, BORE_Y + 0.0335, 0.164), 0.10)
    m.box(steel, (0.0018, BORE_Y + 0.0265, 0.156), (0.0095, BORE_Y + 0.0335, 0.164), 0.10)

    # 9) Barrel bands: Mittelring (z=0.398) with sling swivel, and H-type Oberring nose cap (z=0.560..0.600)
    m.cylinder(steel, (0.0, BORE_Y - 0.004, 0.396), (0.0, BORE_Y - 0.004, 0.414), 0.0205, 14, steel, 0.14)
    m.cylinder(dark, (-0.023, -0.014, 0.405), (0.023, -0.014, 0.405), 0.0065, 10, dark, 0.10)
    # H-shaped Oberring nose cap + S98/05 bayonet lug under the nose cap
    m.cylinder(steel, (0.0, BORE_Y - 0.003, 0.558), (0.0, BORE_Y - 0.003, 0.598), 0.0172, 14, steel, 0.14)
    m.box(dark, (-0.0045, -0.012, 0.562), (0.0045, 0.004, 0.608), 0.12)
    # Cleaning rod (Putzstock) under the barrel
    m.cylinder(steel, (0.0, 0.006, 0.560), (0.0, 0.006, 0.698), 0.0028, 8, steel, 0.12)
    m.cylinder(steel, (0.0, 0.006, 0.695), (0.0, 0.006, 0.706), 0.0042, 8, steel, 0.12)

    # 10) Front sight ramp, dovetail block and blade post
    m.cylinder(dark, (0.0, BORE_Y, 0.680), (0.0, BORE_Y, 0.706), 0.0108, 12, dark, 0.10)
    m.box(dark, (-0.0055, BORE_Y + 0.008, 0.682), (0.0055, BORE_Y + 0.016, 0.704), 0.10)
    m.box(steel, (-0.0014, BORE_Y + 0.015, 0.688), (0.0014, BORE_Y + 0.026, 0.698), 0.10)

    # 11) Magazine box floorplate, curved trigger guard bow and curved trigger blade
    m.box(dark, (-0.0155, -0.0285, -0.022), (0.0155, -0.0225, 0.088), 0.14)
    # Curved trigger guard loop built from 7 segments
    bow_pts = [
        (-0.082, -0.025),
        (-0.076, -0.040),
        (-0.062, -0.050),
        (-0.044, -0.052),
        (-0.026, -0.048),
        (-0.016, -0.036),
        (-0.012, -0.025),
    ]
    for (z0, y0), (z1, y1) in zip(bow_pts, bow_pts[1:]):
        m.cylinder(steel, (0.0, y0, z0), (0.0, y1, z1), 0.0052, 8, steel, 0.10)
    # Curved trigger inside the bow
    m.cylinder(steel, (0.0, -0.022, -0.046), (0.0, -0.039, -0.050), 0.0028, 8, steel, 0.10)
    m.cylinder(steel, (0.0, -0.039, -0.050), (0.0, -0.044, -0.044), 0.0026, 8, steel, 0.10)
    return m


def bolt_mesh(steel: int, dark: int) -> Mesh:
    """Mauser 98 bolt: cylindrical body, shroud, flag safety wing, straight stem and spherical knob."""
    m = Mesh()
    # Cylindrical bolt body running along Z in bolt node space
    m.cylinder(steel, (0.0, 0.0, -0.105), (0.0, 0.0, 0.030), 0.0092, 14, steel, 0.14)
    # Extractor rib along the right side of the bolt body
    m.box(dark, (0.0075, -0.0028, -0.075), (0.0115, 0.0028, 0.025), 0.12)
    # Bolt sleeve (Schlösschen), cocking piece (Schlagbolzenmutter) and flag safety (Sicherung)
    m.cylinder(dark, (0.0, 0.0, -0.128), (0.0, 0.0, -0.102), 0.0112, 12, dark, 0.12)
    m.cylinder(steel, (0.0, 0.0, -0.144), (0.0, 0.0, -0.126), 0.0075, 10, steel, 0.12)
    m.box(steel, (-0.0022, 0.008, -0.132), (0.0022, 0.024, -0.112), 0.10)
    m.cylinder(steel, (-0.0025, 0.023, -0.122), (0.0025, 0.023, -0.122), 0.0065, 10, steel, 0.10)
    # Straight horizontal bolt handle stem + spherical Kammerstengel knob (5-station lathe profile)
    m.cylinder(steel, (0.006, 0.0, -0.088), (0.050, 0.0, -0.088), 0.0052, 10, steel, 0.12)
    knob_rings: list[list[Vec3]] = []
    for x, r in ((0.046, 0.0055), (0.051, 0.0102), (0.058, 0.0125), (0.065, 0.0102), (0.069, 0.0045)):
        ring: list[Vec3] = []
        for i in range(12):
            a = math.tau * i / 12
            ring.append((x, r * math.cos(a), -0.088 + r * math.sin(a)))
        knob_rings.append(ring)
    loft_rings(m, dark, knob_rings, 0.14)
    return m


def _cartridge(m: Mesh, brass: int, steel: int, cx: float, cy: float, cz: float) -> None:
    """Single 7.92x57 mm Mauser S-Patrone: rim, bottleneck brass case, spitzer bullet."""
    profile = [
        (cz - 0.028, 0.0058),
        (cz + 0.010, 0.0054),
        (cz + 0.015, 0.0042),
        (cz + 0.022, 0.0041),
    ]
    loft_rings(m, brass, cyl_rings_z(cx, cy, profile, 10), 0.12)
    bullet = [
        (cz + 0.022, 0.0041),
        (cz + 0.036, 0.0036),
        (cz + 0.048, 0.0018),
        (cz + 0.053, 0.0005),
    ]
    loft_rings(m, steel, cyl_rings_z(cx, cy, bullet, 10), 0.12)


def clip_mesh(brass: int, steel: int) -> Mesh:
    """Five-round Mauser stripper clip (Ladestreifen) with staggered 7.92x57 mm spitzer rounds."""
    m = Mesh()
    m.box(steel, (-0.0075, -0.032, -0.032), (0.0075, 0.028, -0.025), 0.12)
    for i in range(5):
        y = (i - 2) * 0.0115
        x = (0.0022 if i % 2 == 0 else -0.0022)
        _cartridge(m, brass, steel, x, y, 0.0)
    return m


def round_mesh(brass: int, steel: int) -> Mesh:
    m = Mesh()
    _cartridge(m, brass, steel, 0.0, 0.0, 0.0)
    return m


def flash_mesh(brass: int) -> Mesh:
    m = Mesh()
    for i in range(8):
        a = math.tau * i / 8
        u = (math.cos(a), math.sin(a), 0)
        v = (-math.sin(a), math.cos(a), 0)
        c = (0.0, 0.0, 0.0)
        p1 = (u[0] * 0.020 + v[0] * 0.006, u[1] * 0.020 + v[1] * 0.006, 0.015)
        p2 = (u[0] * 0.020 - v[0] * 0.006, u[1] * 0.020 - v[1] * 0.006, 0.015)
        tip = (u[0] * 0.075, u[1] * 0.075, 0.015)
        m.triangle(brass, c, p1, tip)
        m.triangle(brass, c, tip, p2)
    return m


def main() -> None:
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    wood_tex = beech_texture()
    steel_tex = steel_texture()
    (TEXTURE_DIR / "beech.png").write_bytes(wood_tex)
    (TEXTURE_DIR / "steel.png").write_bytes(steel_tex)
    (ITEM_DIR / "gewehr98.png").write_bytes(item_icon())

    b = GlbBuilder("Gewehr 98 infantry rifle (1898 pattern, WWI use)")
    wood_i_tex = b.add_texture(wood_tex, "gewehr98_beech_stock")
    steel_i_tex = b.add_texture(steel_tex, "gewehr98_blued_steel")
    wood = b.add_material("seasoned beech stock", (1.0, 0.96, 0.92, 1), 0.04, 0.68, wood_i_tex)
    wood_dark = b.add_material("darkened beech", (0.78, 0.72, 0.66, 1), 0.05, 0.62, wood_i_tex)
    steel = b.add_material("blued barrel and receiver", (0.88, 0.92, 0.98, 1), 0.82, 0.28, steel_i_tex)
    dark = b.add_material("blackened fittings", (0.58, 0.62, 0.66, 1), 0.78, 0.36, steel_i_tex)
    brass = b.add_material("brass case", (1.0, 0.82, 0.44, 1), 0.78, 0.26, steel_i_tex)

    main_i = b.add_mesh("gewehr98_stock_receiver_barrel", stock_mesh(wood, wood_dark, steel, dark, brass))
    bolt_i = b.add_mesh("gewehr98_bolt_mesh", bolt_mesh(steel, dark))
    clip_i = b.add_mesh("gewehr98_stripper_clip_mesh", clip_mesh(brass, steel))
    round_i = b.add_mesh("gewehr98_chambered_round_mesh", round_mesh(brass, steel))
    flash_i = b.add_mesh("gewehr98_muzzle_flash_mesh", flash_mesh(brass))

    root = b.add_node("gewehr98")
    body = b.add_node("gewehr98.body", main_i)
    bolt = b.add_node("gewehr98.bolt", bolt_i, (0, 0.0220, 0.0450))
    clip = b.add_node("gewehr98.clip", clip_i, (0, 0.1180, -0.0150))
    bullet = b.add_node("gewehr98.round", round_i, (0, 0.0690, 0.0150))
    flash = b.add_node("gewehr98.flash", flash_i, (0, BORE_Y, MUZZLE_Z + 0.002))
    sight = b.add_node("gewehr98.sight", translation=(0, 0.0510, 0.1800))
    arm_r = b.add_node("gewehr98.arm_r", translation=(0, -0.0400, -0.0500))
    arm_l = b.add_node("gewehr98.arm_l", translation=(0, -0.0350, 0.3180))
    b.set_children(root, [body, bolt, clip, bullet, flash, sight, arm_r, arm_l])

    q0 = axis_quaternion((0, 0, 1), 0)
    q1 = axis_quaternion((0, 0, 1), 1.15)
    q2 = axis_quaternion((0, 0, 1), 1.15)
    q3 = q0
    b.add_animation("gewehr98_bolt", [
        (bolt, "translation", [0, 0.10, 0.25, 0.38],
         [0, 0.0220, 0.0450, 0, 0.0220, -0.0620, 0, 0.0220, -0.0620, 0, 0.0220, 0.0450]),
        (bolt, "rotation", [0, 0.10, 0.25, 0.38], [*q0, *q1, *q2, *q3]),
    ])
    up = axis_quaternion((0, 0, 1), 1.15)
    down = axis_quaternion((0, 0, 1), 0)
    c_up = axis_quaternion((1, 0, 0), -0.35)
    c_dn = axis_quaternion((1, 0, 0), 0)
    b.add_animation("gewehr98_reload", [
        (bolt, "translation", [0, 0.24, 0.42, 1.75, 2.08, 2.30],
         [0, 0.0220, 0.0450, 0, 0.0220, -0.0620, 0, 0.0220, -0.0620,
          0, 0.0220, -0.0620, 0, 0.0220, 0.0450, 0, 0.0220, 0.0450]),
        (bolt, "rotation", [0, 0.24, 0.42, 1.75, 2.08, 2.30], [*down, *up, *up, *up, *down, *down]),
        (clip, "translation", [0, 0.24, 0.55, 1.72, 2.05, 2.30],
         [0, 0.1180, -0.0150, 0, 0.1450, -0.0150, 0, 0.0650, -0.0150,
          0, 0.0650, -0.0150, 0, 0.1180, -0.0150, 0, 0.1180, -0.0150]),
        (clip, "rotation", [0, 0.24, 0.55, 1.72, 2.05, 2.30], [*c_dn, *c_up, *c_dn, *c_dn, *c_up, *c_dn]),
    ])

    path = MODEL_DIR / "gewehr98.glb"
    b.save(path, [root])
    triangles = sum(b.accessors[p["indices"]]["count"] // 3 for mesh in b.meshes for p in mesh["primitives"])
    print(f"{path}: {path.stat().st_size:,} bytes; {triangles:,} triangles; embedded beech/steel textures; 2 clips")


if __name__ == "__main__":
    main()
