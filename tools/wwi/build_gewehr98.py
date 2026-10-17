#!/usr/bin/env python3
"""Deterministically build the WWI Gewehr 98 GLB, its textures and the small item icon.

The 1898 Mauser is rebuilt from its real dimensions: approximately 1250 mm overall and a 7.92 mm bore,
a 200 mm rectangular receiver with a straight bolt handle, a five-round stripper clip, the tangent rear
sight with its sliding leaf, the protected front post, the cleaning rod under the barrel, the sling swivels
and the butt plate. The stock is a lofted beech profile (butt, comb, wrist, pistol
grip, forestock) rather than a stack of boxes, so the 1898 silhouette reads in first person.

Run from any working directory:  python tools/wwi/build_gewehr98.py
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from glb_builder import GlbBuilder, Mesh, axis_quaternion, png_rgba  # noqa: E402

ASSETS = ROOT / "mods/aiarsenal/src/main/resources/assets/aiarsenal"
MODEL_DIR = ASSETS / "models/glb"
TEXTURE_DIR = ASSETS / "textures/entity/gewehr98"
ITEM_DIR = ASSETS / "textures/item"

# Bore axis and the overall layout (model space: +Z forward towards the muzzle, +Y up, metres).
BORE_Y = 0.022
BUTT_Z = -0.495
MUZZLE_Z = 0.725
RECEIVER_Z0, RECEIVER_Z1 = -0.090, 0.120
# Butt -> comb -> wrist -> pistol grip -> forestock (z, half width, y bottom, y top).
STOCK = [
    (BUTT_Z, 0.0355, -0.0300, 0.0560),
    (-0.4700, 0.0395, -0.0360, 0.0585),
    (-0.4200, 0.0405, -0.0430, 0.0560),
    (-0.3600, 0.0400, -0.0490, 0.0470),
    (-0.3000, 0.0375, -0.0540, 0.0330),
    (-0.2400, 0.0330, -0.0580, 0.0210),
    (-0.1800, 0.0290, -0.0600, 0.0120),
    (-0.1300, 0.0262, -0.0600, 0.0080),
    (-0.0900, 0.0272, -0.0570, 0.0090),
    (-0.0400, 0.0292, -0.0530, 0.0110),
    (0.0100, 0.0305, -0.0490, 0.0120),
    (0.0700, 0.0312, -0.0440, 0.0125),
    (0.1400, 0.0318, -0.0390, 0.0130),
    (0.2500, 0.0325, -0.0355, 0.0135),
    (0.3300, 0.0325, -0.0340, 0.0140),
    (0.3700, 0.0300, -0.0330, 0.0140),
]


def beech_texture() -> bytes:
    """Seasoned beech stock: fine straight grain with a few darker figure streaks."""
    w, h = 128, 64
    rng = random.Random(1898)
    pixels = []
    for y in range(h):
        for x in range(w):
            grain = 5.0 * math.sin(x * 0.55 + 0.9 * math.sin(y * 0.07)) + 2.5 * math.sin(x * 1.7 + y * 0.2)
            fine = rng.randrange(-3, 4)
            blotch = 4 * math.sin(x * 0.08 + math.sin(y * 0.11) * 2.0)
            base = (176, 138, 88)
            n = int(grain + fine + blotch)
            c = tuple(max(0, min(255, v + n)) for v in base)
            pixels.append(c + (255,))
    return png_rgba(w, h, pixels)


def steel_texture() -> bytes:
    """Blued gun steel with a fine polish and light wear on the edges."""
    w = h = 128
    rng = random.Random(98)
    pixels = []
    for y in range(h):
        for x in range(w):
            n = int(rng.randrange(-5, 6) + 3 * math.sin(x * 0.35) + 2 * math.sin(y * 0.9))
            c = tuple(max(0, min(255, v + n)) for v in (74, 80, 86))
            pixels.append(c + (255,))
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
    """Hand-drawn side view of the Gewehr 98 for the inventory slot."""
    w = h = 64
    p = [(0, 0, 0, 0)] * (w * h)

    def poly(points, color):
        _poly_fill(p, w, h, points, color)

    def rect(x0, y0, x1, y1, color):
        poly(((x0, y0), (x1, y0), (x1, y1), (x0, y1)), color)

    wood = (150, 112, 66, 255)
    wood_dark = (108, 78, 44, 255)
    steel = (78, 84, 90, 255)
    steel_light = (116, 122, 128, 255)
    ink = (34, 30, 26, 255)
    # butt, wrist, grip and fore-end
    poly(((3, 34), (7, 31), (12, 31), (14, 33), (22, 33), (24, 30), (30, 29), (33, 28),
          (44, 28), (46, 30), (30, 31), (26, 33), (17, 36), (12, 39), (6, 40), (3, 38)), wood)
    poly(((3, 34), (6, 34), (6, 39), (3, 38)), wood_dark)
    poly(((24, 30), (30, 29), (33, 28), (33, 30), (27, 31)), wood_dark)
    # barrel, muzzle, front sight and cleaning rod
    rect(44, 27, 60, 29, steel)
    rect(58, 25, 60, 30, steel_light)
    rect(49, 23, 51, 27, steel_light)
    rect(35, 30, 44, 31, steel)
    # receiver and bolt handle
    rect(30, 25, 42, 30, steel)
    rect(31, 22, 34, 25, steel)
    poly(((34, 24), (37, 20), (39, 21), (36, 25)), steel_light)
    # trigger guard
    poly(((24, 36), (27, 36), (27, 39), (24, 39)), ink)
    return png_rgba(w, h, p)


def stock_mesh(wood: int, wood_dark: int, steel: int, dark: int) -> Mesh:
    """Beech stock, butt plate, bands, trigger group and the cleaning rod."""
    m = Mesh()
    m.loft(wood, STOCK, 0.22)
    # Butt plate (tilted back) with the Felddiskus disc.
    m.oriented_box(wood_dark, (0.0, 0.010, BUTT_Z - 0.004), (0.0, 0.0, 0.0), (0.0, 0.048, 0.0), (0.0, 0.0, 0.010))
    m.cylinder(dark, (-0.0, 0.012, BUTT_Z - 0.010), (-0.0, 0.012, BUTT_Z - 0.014), 0.026, 14, dark, 0.2)
    # Finger grooves in the forestock.
    for i in range(6):
        z = 0.155 + i * 0.031
        for x in (-0.018, 0.018):
            m.box(wood_dark, (x - 0.005, 0.006, z), (x + 0.005, 0.0135, z + 0.016), 0.12)
    # Receiver: the rectangular Mauser action with its bolt track and the side plate line.
    m.box(steel, (-0.0225, -0.0165, RECEIVER_Z0), (0.0225, 0.0405, RECEIVER_Z1), 0.16)
    m.box(dark, (-0.0245, 0.0405, RECEIVER_Z0 + 0.012), (0.0245, 0.0470, RECEIVER_Z1 - 0.012), 0.16)
    m.box(dark, (-0.0245, -0.0165, RECEIVER_Z0 + 0.004), (0.0245, -0.0235, RECEIVER_Z1 - 0.004), 0.16)
    # Barrel: 750 mm from the breech face, with a slight taper and the muzzle crown.
    m.cylinder(steel, (0.0, BORE_Y, RECEIVER_Z1 - 0.010), (0.0, BORE_Y, 0.700), 0.0138, 14, steel, 0.20)
    m.cylinder(dark, (0.0, BORE_Y, 0.700), (0.0, BORE_Y, MUZZLE_Z), 0.0148, 14, dark, 0.20)
    # Barrel bands holding the fore-end.
    for z in (0.135, 0.352):
        m.box(steel, (-0.0330, -0.0300, z), (0.0330, 0.0180, z + 0.014), 0.12)
    m.cylinder(steel, (0.0, BORE_Y, 0.120), (0.0, BORE_Y, 0.140), 0.0175, 14, steel, 0.12)
    # Cleaning rod under the barrel.
    m.cylinder(dark, (0.0, 0.004, 0.120), (0.0, 0.004, 0.660), 0.0035, 6, dark, 0.12)
    m.cylinder(dark, (0.0, 0.004, 0.660), (0.0, 0.004, 0.680), 0.0055, 6, dark, 0.12)
    # Trigger, trigger guard and the magazine floor plate.
    m.oriented_box(steel, (0.0, -0.0180, -0.010), (0.0, 0.0, 0.0), (0.0, 0.0140, 0.0), (0.0, 0.0, 0.0045))
    m.box(steel, (-0.0210, -0.0430, -0.012), (0.0210, -0.0350, 0.020), 0.12)
    m.box(dark, (-0.0210, -0.0400, -0.010), (-0.0165, -0.0270, 0.018), 0.12)
    m.box(dark, (0.0165, -0.0400, -0.010), (0.0210, -0.0270, 0.018), 0.12)
    m.box(dark, (-0.0210, -0.0400, -0.010), (0.0210, -0.0355, -0.004), 0.12)
    # Sling swivels, front and rear.
    m.cylinder(dark, (0.030, -0.020, -0.395), (0.038, -0.020, -0.395), 0.0055, 8, dark, 0.1)
    m.cylinder(dark, (0.030, -0.020, 0.330), (0.038, -0.020, 0.330), 0.0055, 8, dark, 0.1)
    # Front sight: a post with protective ears on a ramp.
    m.box(dark, (-0.011, 0.030, 0.628), (0.011, 0.037, 0.660), 0.10)
    m.box(steel, (-0.0045, 0.037, 0.638), (0.0045, 0.052, 0.650), 0.10)
    m.box(dark, (-0.010, 0.037, 0.636), (-0.005, 0.049, 0.652), 0.10)
    m.box(dark, (0.005, 0.037, 0.636), (0.010, 0.049, 0.652), 0.10)
    # Rear sight: tangent leaf on a base with the aperture slider.
    m.box(steel, (-0.019, 0.0405, 0.150), (0.019, 0.0470, 0.210), 0.10)
    m.box(dark, (-0.011, 0.0470, 0.168), (0.011, 0.0560, 0.192), 0.10)
    m.box(steel, (-0.015, 0.0545, 0.170), (0.015, 0.0615, 0.190), 0.10)
    m.box(dark, (-0.0022, 0.0520, 0.178), (0.0022, 0.0600, 0.182), 0.10)
    # Magazine floor plate with the latch.
    m.box(dark, (-0.020, -0.0250, 0.030), (0.020, -0.0215, 0.105), 0.12)
    m.cylinder(dark, (0.0, -0.0260, 0.060), (0.0, -0.0330, 0.060), 0.0032, 6, dark, 0.1)
    return m


def bolt_mesh(steel: int, dark: int) -> Mesh:
    """Straight-handle Mauser bolt: sleeve, stem, handle and the extractor."""
    m = Mesh()
    m.cylinder(steel, (-0.021, 0.0, 0.0), (0.021, 0.0, 0.0), 0.0105, 10, steel, 0.12)
    m.box(dark, (-0.022, -0.011, -0.010), (0.022, 0.011, 0.010), 0.12)
    m.cylinder(steel, (0.020, 0.0, 0.0), (0.049, 0.0, 0.0), 0.0080, 8, steel, 0.12)
    m.oriented_box(dark, (0.052, 0.0, 0.0), (0.008, 0.0, 0.0), (0.0, 0.0075, 0.0), (0.0, 0.0, 0.0165))
    m.box(steel, (-0.026, -0.004, -0.0035), (-0.020, 0.010, 0.0035), 0.10)
    return m


def clip_mesh(brass: int, steel: int) -> Mesh:
    """Five pointed 7.92 mm rounds in a stripper clip; only visible during the reload."""
    m = Mesh()
    for i in range(5):
        x = (i - 2) * 0.0115
        m.cylinder(brass, (x, -0.018, -0.020), (x, -0.018, 0.019), 0.0034, 6, brass, 0.1)
        m.cylinder(steel, (x, -0.018, 0.013), (x, -0.018, 0.022), 0.0043, 6, steel, 0.1)
    m.box(steel, (-0.033, -0.0225, -0.021), (0.033, -0.0170, 0.021), 0.1)
    return m


def round_mesh(brass: int, steel: int) -> Mesh:
    m = Mesh()
    m.cylinder(brass, (0, 0, -0.018), (0, 0, 0.018), 0.004, 8, brass, 0.1)
    m.cylinder(steel, (0, 0, 0.014), (0, 0, 0.026), 0.0045, 8, steel, 0.1)
    return m


def flash_mesh(brass: int) -> Mesh:
    """Small radial flash, hidden for all but the brief shot event by GunRenderer."""
    m = Mesh()
    for i in range(8):
        a = math.tau * i / 8
        u = (math.cos(a), math.sin(a), 0)
        v = (-math.sin(a), math.cos(a), 0)
        c = (0, 0, 0)
        p1 = (u[0] * .020 + v[0] * .006, u[1] * .020 + v[1] * .006, .015)
        p2 = (u[0] * .020 - v[0] * .006, u[1] * .020 - v[1] * .006, .015)
        tip = (u[0] * .075, u[1] * .075, .015)
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
    wood = b.add_material("seasoned beech stock", (1.0, 0.98, 0.95, 1), 0.02, 0.74, wood_i_tex)
    wood_dark = b.add_material("darkened beech", (0.74, 0.70, 0.66, 1), 0.03, 0.66, wood_i_tex)
    steel = b.add_material("blued barrel and receiver", (0.92, 0.95, 1.0, 1), 0.76, 0.30, steel_i_tex)
    dark = b.add_material("blackened fittings", (0.68, 0.70, 0.72, 1), 0.70, 0.42, steel_i_tex)
    brass = b.add_material("brass case", (1.0, 0.82, 0.46, 1), 0.72, 0.30, steel_i_tex)

    main_i = b.add_mesh("gewehr98_stock_receiver_barrel", stock_mesh(wood, wood_dark, steel, dark))
    bolt_i = b.add_mesh("gewehr98_bolt_mesh", bolt_mesh(steel, dark))
    clip_i = b.add_mesh("gewehr98_stripper_clip_mesh", clip_mesh(brass, steel))
    round_i = b.add_mesh("gewehr98_chambered_round_mesh", round_mesh(brass, steel))
    flash_i = b.add_mesh("gewehr98_muzzle_flash_mesh", flash_mesh(brass))

    root = b.add_node("gewehr98")
    body = b.add_node("gewehr98.body", main_i)
    bolt = b.add_node("gewehr98.bolt", bolt_i, (0, 0.0290, 0.0620))
    clip = b.add_node("gewehr98.clip", clip_i, (0, 0.1180, 0.0650))
    bullet = b.add_node("gewehr98.round", round_i, (0, 0.0690, 0.0750))
    flash = b.add_node("gewehr98.flash", flash_i, (0, BORE_Y, MUZZLE_Z + 0.002))
    sight = b.add_node("gewehr98.sight", translation=(0, 0.0560, 0.1800))
    arm_r = b.add_node("gewehr98.arm_r", translation=(0, -0.0400, -0.0500))
    arm_l = b.add_node("gewehr98.arm_l", translation=(0, -0.0350, 0.3180))
    b.set_children(root, [body, bolt, clip, bullet, flash, sight, arm_r, arm_l])

    # Bolt cycle: lift, draw the bolt rearward, then return and lock. Times are seconds.
    q0 = axis_quaternion((0, 0, 1), 0)
    q1 = axis_quaternion((0, 0, 1), .55)
    q2 = axis_quaternion((0, 0, 1), .55)
    q3 = q0
    b.add_animation("gewehr98_bolt", [
        (bolt, "translation", [0, .10, .25, .38], [0, .0290, .0620, 0, .0290, -0.0430, 0, .0290, -0.0430, 0, .0290, .0620]),
        (bolt, "rotation", [0, .10, .25, .38], [*q0, *q1, *q2, *q3]),
    ])
    # Reload: open the bolt, press the five-round clip home, drop the clip and close the bolt.
    up = axis_quaternion((1, 0, 0), -.42)
    down = axis_quaternion((1, 0, 0), 0)
    b.add_animation("gewehr98_reload", [
        (bolt, "translation", [0, .24, .42, 1.75, 2.08, 2.50],
         [0, .0290, .0620, 0, .0290, -0.0430, 0, .0290, -0.0430, 0, .0290, -0.0430, 0, .0290, .0620, 0, .0290, .0620]),
        (bolt, "rotation", [0, .24, .42, 1.75, 2.08, 2.50], [*down, *up, *up, *up, *down, *down]),
        (clip, "translation", [0, .24, .55, 1.72, 2.05, 2.50],
         [0, .1180, .0650, 0, .1450, .0650, 0, .0780, .0650, 0, .0780, .0650, 0, .1180, .0650, 0, .1180, .0650]),
        (clip, "rotation", [0, .24, .55, 1.72, 2.05, 2.50], [*down, *up, *down, *down, *up, *down]),
    ])

    path = MODEL_DIR / "gewehr98.glb"
    b.save(path, [root])
    triangles = sum(b.accessors[p["indices"]]["count"] // 3 for mesh in b.meshes for p in mesh["primitives"])
    print(f"{path}: {path.stat().st_size:,} bytes; {triangles:,} triangles; embedded beech/steel textures; 2 clips")


if __name__ == "__main__":
    main()
