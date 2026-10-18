#!/usr/bin/env python3
"""Build the four handheld WWI equipment GLBs and their Minecraft item wrappers.

Geometry is deterministic, metre-scale triangle mesh built from turnings, lofts, tubes,
plates and rivets. Each GLB embeds its own PBR base-colour textures; this uses no Blender,
network assets or non-standard Python packages. These are historical game approximations,
not scans or manufacturing drawings.

Run: python tools/wwi/build_equipment.py
"""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from glb_builder import GlbBuilder, Mesh, png_rgba  # noqa: E402

ASSETS = ROOT / "mods/aiarsenal/src/main/resources/assets/aiarsenal"
DATA = ROOT / "mods/aiarsenal/src/main/resources/data/aiarsenal"
MODEL_DIR = ASSETS / "models/glb"
ITEM_DIR = ASSETS / "models/item"
TEXTURE_DIR = ASSETS / "textures/item/wwi"
TAU = math.tau


# --------------------------------------------------------------------------- reproducible materials

def build_textures() -> dict[str, bytes]:
    """Generate tiled 128 px cast iron, blued steel, walnut, brass, and rubber albedo maps."""
    size = 128
    palettes = {
        "olive": (78, 85, 53),
        "steel": (67, 74, 82),
        "wood": (112, 72, 42),
        "brass": (166, 125, 49),
        "rubber": (36, 39, 35),
        "ivory": (190, 184, 157),
    }
    result: dict[str, bytes] = {}
    for name, base in palettes.items():
        rng = random.Random("wwi_glb_v42_" + name)
        pixels = []
        for y in range(size):
            for x in range(size):
                if name == "wood":
                    grain = 8.0 * math.sin(x * 0.18 + 1.8 * math.sin(y * 0.045))
                    grain += 3.0 * math.sin(x * 0.55 + y * 0.04)
                    variation = grain + rng.randrange(-3, 4)
                elif name == "steel":
                    brush = 5.0 * math.sin(x * 0.42 + y * 0.11) + 2.0 * math.sin(y * 0.63)
                    variation = brush + rng.randrange(-3, 4)
                elif name == "brass":
                    variation = 5.0 * math.sin(x * 0.14 + math.sin(y * 0.07)) + rng.randrange(-4, 5)
                elif name == "olive":
                    pits = 4.0 * math.sin(x * 0.52) * math.cos(y * 0.49)
                    flecks = -7 if rng.random() < 0.012 else 0
                    variation = pits + flecks + rng.randrange(-4, 5)
                elif name == "rubber":
                    variation = 2.0 * math.sin(x * 0.3 + y * 0.5) + rng.randrange(-3, 4)
                else:
                    variation = 2.0 * math.sin(x * 0.19 + y * 0.21) + rng.randrange(-2, 3)
                n = int(variation)
                pixels.append(tuple(max(0, min(255, c + n)) for c in base) + (255,))
        result[name] = png_rgba(size, size, pixels)
        path = TEXTURE_DIR / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(result[name])
    return result


def materials(builder: GlbBuilder, textures: dict[str, bytes]) -> dict[str, int]:
    images = {name: builder.add_texture(data, f"wwi_{name}_128") for name, data in textures.items()}
    return {
        "olive": builder.add_material("WWI olive enamel / cast iron", (1, 1, 1, 1), 0.16, 0.78, images["olive"]),
        "steel": builder.add_material("Blued and machined steel", (1, 1, 1, 1), 0.76, 0.34, images["steel"]),
        "dark": builder.add_material("Blackened steel", (0.82, 0.84, 0.85, 1), 0.72, 0.48, images["steel"]),
        "wood": builder.add_material("Oil-rubbed hardwood", (1, 1, 1, 1), 0.0, 0.84, images["wood"]),
        "brass": builder.add_material("Aged brass", (1, 1, 1, 1), 0.78, 0.3, images["brass"]),
        "rubber": builder.add_material("Braided rubber hose", (1, 1, 1, 1), 0.0, 0.93, images["rubber"]),
        "ivory": builder.add_material("Gauge enamel", (1, 1, 1, 1), 0.0, 0.58, images["ivory"]),
    }


# --------------------------------------------------------------------------- mesh helpers

def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _dot(a, b) -> float:
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _len(v) -> float:
    return math.sqrt(_dot(v, v))


def _unit(v):
    length = _len(v) or 1.0
    return (v[0] / length, v[1] / length, v[2] / length)


def _oriented_quad(mesh: Mesh, material: int, points, expected_normal, uvs) -> None:
    pts = list(points)
    n = _cross(_sub(pts[1], pts[0]), _sub(pts[2], pts[0]))
    if _dot(n, expected_normal) < 0:
        pts.reverse()
        uvs = tuple(reversed(uvs))
    mesh.quad(material, pts, uvs)


def lathe_y(mesh: Mesh, material: int, cx: float, cz: float,
            profile: Iterable[tuple[float, float]], sides: int = 16, tile: float = 0.08,
            phase: float = 0.0) -> None:
    """Outward-wound vertical turning, with continuous circumferential UVs and capped ends."""
    stations = list(profile)
    rings = []
    for y, radius in stations:
        ring = [(cx + radius * math.cos(phase + TAU * i / sides), y,
                 cz + radius * math.sin(phase + TAU * i / sides)) for i in range(sides)]
        rings.append(ring)
    max_r = max(r for _, r in stations)
    circumference = TAU * max_r / tile
    for k in range(len(rings) - 1):
        a, b = rings[k], rings[k + 1]
        y0, y1 = stations[k][0], stations[k + 1][0]
        for i in range(sides):
            j = (i + 1) % sides
            angle = phase + TAU * i / sides
            next_angle = phase + TAU * (i + 1) / sides
            pts = (a[i], a[j], b[j], b[i])
            radial = (math.cos((angle + next_angle) * 0.5), 0.0,
                      math.sin((angle + next_angle) * 0.5))
            u0, u1 = i / sides * circumference, (i + 1) / sides * circumference
            v0, v1 = y0 / tile, y1 / tile
            _oriented_quad(mesh, material, pts, radial,
                           ((u0, v0), (u1, v0), (u1, v1), (u0, v1)))
    for end, top in ((rings[0], False), (rings[-1], True)):
        y = end[0][1]
        normal = (0.0, 1.0 if top else -1.0, 0.0)
        center = (cx, y, cz)
        for i in range(sides):
            j = (i + 1) % sides
            p0, p1 = end[i], end[j]
            uv0 = (0.5 + 0.5 * (p0[0] - cx) / max_r, 0.5 + 0.5 * (p0[2] - cz) / max_r)
            uv1 = (0.5 + 0.5 * (p1[0] - cx) / max_r, 0.5 + 0.5 * (p1[2] - cz) / max_r)
            if top:
                mesh.triangle(material, center, p1, p0, ((0.5, 0.5), uv1, uv0))
            else:
                mesh.triangle(material, center, p0, p1, ((0.5, 0.5), uv0, uv1))


def lathe_z(mesh: Mesh, material: int, cx: float, cy: float,
            profile: Iterable[tuple[float, float]], sides: int = 16, tile: float = 0.08,
            phase: float = 0.0) -> None:
    """Outward-wound horizontal turning along +Z."""
    stations = list(profile)
    rings = []
    for z, radius in stations:
        rings.append([(cx + radius * math.cos(phase + TAU * i / sides),
                       cy + radius * math.sin(phase + TAU * i / sides), z) for i in range(sides)])
    max_r = max(r for _, r in stations)
    circumference = TAU * max_r / tile
    for k in range(len(rings) - 1):
        a, b = rings[k], rings[k + 1]
        z0, z1 = stations[k][0], stations[k + 1][0]
        for i in range(sides):
            j = (i + 1) % sides
            angle = phase + TAU * i / sides
            next_angle = phase + TAU * (i + 1) / sides
            # This initial winding faces inward for +Z; _oriented_quad corrects it analytically.
            pts = (a[i], a[j], b[j], b[i])
            radial = (math.cos((angle + next_angle) * 0.5),
                      math.sin((angle + next_angle) * 0.5), 0.0)
            u0, u1 = i / sides * circumference, (i + 1) / sides * circumference
            v0, v1 = z0 / tile, z1 / tile
            _oriented_quad(mesh, material, pts, radial,
                           ((u0, v0), (u1, v0), (u1, v1), (u0, v1)))
    for end, front in ((rings[0], False), (rings[-1], True)):
        z = end[0][2]
        normal = (0.0, 0.0, 1.0 if front else -1.0)
        center = (cx, cy, z)
        for i in range(sides):
            j = (i + 1) % sides
            p0, p1 = end[i], end[j]
            uv0 = (0.5 + 0.5 * (p0[0] - cx) / max_r, 0.5 + 0.5 * (p0[1] - cy) / max_r)
            uv1 = (0.5 + 0.5 * (p1[0] - cx) / max_r, 0.5 + 0.5 * (p1[1] - cy) / max_r)
            if front:
                mesh.triangle(material, center, p0, p1, ((0.5, 0.5), uv0, uv1))
            else:
                mesh.triangle(material, center, p1, p0, ((0.5, 0.5), uv1, uv0))


def add_torus(mesh: Mesh, material: int, center, normal, major: float, minor: float,
              segments: int = 24, tube_sides: int = 6) -> None:
    """Low-profile torus used for grenade pull rings, ignition hoops and pressure fittings."""
    n = _unit(normal)
    ref = (0.0, 1.0, 0.0) if abs(n[1]) < 0.9 else (1.0, 0.0, 0.0)
    u = _unit(_cross(n, ref))
    v = _unit(_cross(n, u))
    rings = []
    for i in range(segments):
        theta = TAU * i / segments
        radial = tuple(u[k] * math.cos(theta) + v[k] * math.sin(theta) for k in range(3))
        ring = []
        for j in range(tube_sides):
            phi = TAU * j / tube_sides
            q = tuple(radial[k] * (major + minor * math.cos(phi)) + n[k] * minor * math.sin(phi)
                      for k in range(3))
            ring.append(tuple(center[k] + q[k] for k in range(3)))
        rings.append(ring)
    for i in range(segments):
        ni = (i + 1) % segments
        theta = TAU * (i + 0.5) / segments
        radial = tuple(u[k] * math.cos(theta) + v[k] * math.sin(theta) for k in range(3))
        for j in range(tube_sides):
            nj = (j + 1) % tube_sides
            phi = TAU * (j + 0.5) / tube_sides
            outward = tuple(radial[k] * math.cos(phi) + n[k] * math.sin(phi) for k in range(3))
            pts = (rings[i][j], rings[ni][j], rings[ni][nj], rings[i][nj])
            u0, u1 = i / segments, (i + 1) / segments
            v0, v1 = j / tube_sides, (j + 1) / tube_sides
            _oriented_quad(mesh, material, pts, outward,
                           ((u0, v0), (u1, v0), (u1, v1), (u0, v1)))


def tube_path(mesh: Mesh, material: int, points, radius: float, sides: int = 8) -> None:
    for a, b in zip(points, points[1:]):
        mesh.cylinder(material, a, b, radius, sides, material, 0.06)


def corrugated_tube_path(mesh: Mesh, material: int, points, radius: float = 0.0275,
                         spacing: float = 0.024, sides: int = 10) -> None:
    """Flexible hose with a continuous core and closely spaced raised corrugations."""
    samples = [tuple(points[0])]
    for start, end in zip(points, points[1:]):
        length = _len(_sub(end, start))
        steps = max(1, math.ceil(length / spacing))
        for i in range(1, steps + 1):
            t = i / steps
            samples.append(tuple(start[k] + (end[k] - start[k]) * t for k in range(3)))
    tube_path(mesh, material, samples, radius * 0.84, sides)
    for i in range(1, len(samples) - 1):
        if i % 2:
            continue
        tangent = _unit(_sub(samples[i + 1], samples[i - 1]))
        half = spacing * 0.13
        start = tuple(samples[i][k] - tangent[k] * half for k in range(3))
        end = tuple(samples[i][k] + tangent[k] * half for k in range(3))
        mesh.cylinder(material, start, end, radius, sides, material, 0.035)


def add_rivets_y(mesh: Mesh, material: int, cx: float, cz: float, y: float,
                 radius: float, count: int = 12, head: float = 0.0018, phase: float = 0.0) -> None:
    for i in range(count):
        a = phase + TAU * i / count
        x, z = cx + radius * math.cos(a), cz + radius * math.sin(a)
        mesh.cylinder(material, (x, y, z), (x, y + head, z), 0.0016, 6, material, 0.04)


def add_rivets_z(mesh: Mesh, material: int, cx: float, cy: float, z: float,
                 radius: float, count: int = 12, head: float = 0.0018, phase: float = 0.0) -> None:
    for i in range(count):
        a = phase + TAU * i / count
        x, y = cx + radius * math.cos(a), cy + radius * math.sin(a)
        mesh.cylinder(material, (x, y, z), (x, y, z + head), 0.0016, 6, material, 0.04)


def new_glb(name: str, texture_data: dict[str, bytes]) -> tuple[GlbBuilder, dict[str, int]]:
    builder = GlbBuilder(f"WWI {name} historically informed equipment model")
    return builder, materials(builder, texture_data)


def save_glb(name: str, builder: GlbBuilder, root: int) -> tuple[int, int]:
    path = MODEL_DIR / f"{name}.glb"
    builder.save(path, [root])
    triangles = sum(builder.accessors[p["indices"]]["count"] // 3
                    for mesh in builder.meshes for p in mesh["primitives"])
    return triangles, path.stat().st_size


# --------------------------------------------------------------------------- Mills bomb No. 5 Mk I

def build_mills(textures: dict[str, bytes]) -> tuple[int, int]:
    b, mat = new_glb("Mills bomb No. 5 Mk I", textures)
    body = Mesh()
    lathe_y(body, mat["olive"], 0, 0,
            [(-0.052, 0.014), (-0.048, 0.021), (-0.039, 0.026), (-0.027, 0.028),
             (0.020, 0.028), (0.035, 0.025), (0.046, 0.018), (0.049, 0.011)],
            sides=16, tile=0.075, phase=math.pi / 16)
    # The No. 5 Mk I's cast body has a raised pineapple grid separated by shallow grooves.
    rows = ((-0.034, 0.027), (-0.017, 0.028), (0.0, 0.028), (0.017, 0.028), (0.034, 0.026))
    for row_y, row_radius in rows:
        for i in range(8):
            angle = TAU * i / 8 + math.pi / 8
            radial = (math.cos(angle), 0.0, math.sin(angle))
            tangent = (-math.sin(angle), 0.0, math.cos(angle))
            center = (radial[0] * (row_radius + 0.0007), row_y, radial[2] * (row_radius + 0.0007))
            body.oriented_box(mat["olive"], center,
                              (radial[0] * 0.00065, 0, radial[2] * 0.00065),
                              (0, 0.0062, 0), (tangent[0] * 0.0084, 0, tangent[2] * 0.0084), 0.05)
    # Recessed horizontal and vertical score lines define the cast segments rather than painted stripes.
    for y in (-0.026, -0.009, 0.008, 0.025):
        lathe_y(body, mat["dark"], 0, 0, [(y, 0.0285), (y + 0.0008, 0.0285)], 16, 0.05)
    for i in range(8):
        angle = TAU * i / 8 + math.pi / 8
        x, z = 0.0281 * math.cos(angle), 0.0281 * math.sin(angle)
        body.cylinder(mat["dark"], (x, -0.034, z), (x, 0.034, z), 0.00065, 6, mat["dark"], 0.04)
    # Base plug, top boss and fine machining rings.
    lathe_y(body, mat["steel"], 0, 0, [(-0.055, 0.0135), (-0.052, 0.016), (-0.048, 0.016)], 12, 0.04)
    for y in (-0.048, 0.041):
        lathe_y(body, mat["dark"], 0, 0, [(y, 0.018), (y + 0.002, 0.018)], 16, 0.04)

    striker = Mesh()
    lathe_y(striker, mat["steel"], 0, 0, [(0.046, 0.010), (0.050, 0.011), (0.058, 0.009), (0.061, 0.006)], 12, 0.04)
    lathe_y(striker, mat["brass"], 0, 0, [(0.061, 0.0055), (0.063, 0.0055)], 12, 0.04)
    for i in range(4):
        a = TAU * i / 4
        striker.cylinder(mat["dark"], (0.010 * math.cos(a), 0.053, 0.010 * math.sin(a)),
                         (0.012 * math.cos(a), 0.053, 0.012 * math.sin(a)), 0.0011, 6, mat["dark"], 0.03)

    lever = Mesh()
    # Curved spring-steel lever lies against the body and wraps over the striker housing.
    tube_path(lever, mat["steel"], [(0.006, 0.063, 0), (0.014, 0.058, 0), (0.024, 0.045, 0),
                                    (0.027, 0.022, 0), (0.026, -0.008, 0), (0.021, -0.037, 0)], 0.0022, 8)
    lever.box(mat["dark"], (0.010, 0.056, -0.004), (0.018, 0.061, 0.004), 0.03)

    pin = Mesh()
    pin.cylinder(mat["steel"], (0.012, 0.052, 0), (0.021, 0.052, 0), 0.0016, 8, mat["steel"], 0.03)
    add_torus(pin, mat["brass"], (0.026, 0.052, 0), (1, 0, 0), 0.006, 0.0012, 20, 6)

    nodes = [
        b.add_node("mills_bomb", scale=(1.0, 0.8, 1.0)),
        b.add_node("mills_bomb.body", b.add_mesh("mills_cast_body_and_lugs", body)),
        b.add_node("mills_bomb.striker", b.add_mesh("mills_striker_and_filler_boss", striker)),
        b.add_node("mills_bomb.safety_lever", b.add_mesh("mills_spring_safety_lever", lever)),
        b.add_node("mills_bomb.pull_pin", b.add_mesh("mills_split_pull_pin", pin)),
    ]
    b.set_children(nodes[0], nodes[1:])
    return save_glb("mills_bomb", b, nodes[0])


# --------------------------------------------------------------------------- German M1915 Stielhandgranate

def build_stiel(textures: dict[str, bytes]) -> tuple[int, int]:
    b, mat = new_glb("Stielhandgranate M1915", textures)
    handle = Mesh()
    lathe_y(handle, mat["wood"], 0, 0,
            [(-0.205, 0.0065), (-0.198, 0.009), (-0.173, 0.010), (-0.125, 0.0105),
             (-0.080, 0.010), (-0.030, 0.0105), (0.018, 0.012)], 12, 0.10, math.pi / 12)
    # Turned finger grip beads and the lower threaded cap.
    for y in (-0.166, -0.151, -0.136, -0.121):
        lathe_y(handle, mat["wood"], 0, 0, [(y, 0.0106), (y + 0.003, 0.0106)], 12, 0.025)
    lathe_y(handle, mat["steel"], 0, 0, [(-0.212, 0.007), (-0.205, 0.012), (-0.196, 0.012)], 12, 0.04)
    lathe_y(handle, mat["brass"], 0, 0, [(-0.216, 0.004), (-0.212, 0.006)], 10, 0.03)
    lathe_y(handle, mat["steel"], 0, 0, [(0.010, 0.012), (0.020, 0.014), (0.029, 0.014)], 12, 0.04)

    head = Mesh()
    lathe_y(head, mat["olive"], 0, 0,
            [(0.024, 0.018), (0.030, 0.024), (0.036, 0.027), (0.102, 0.027),
             (0.109, 0.025), (0.114, 0.020)], 16, 0.09, math.pi / 16)
    # Rolled seams, top crimp, filling plug and a legible stamped side marking plate.
    for y in (0.031, 0.105):
        lathe_y(head, mat["steel"], 0, 0, [(y, 0.0275), (y + 0.003, 0.0275)], 16, 0.04)
    lathe_y(head, mat["steel"], 0, 0, [(0.112, 0.019), (0.118, 0.019), (0.121, 0.013)], 12, 0.04)
    lathe_y(head, mat["brass"], 0, 0, [(0.120, 0.006), (0.123, 0.006)], 10, 0.025)
    # A narrow stamped identification strip on the front of the pot.
    head.box(mat["steel"], (-0.009, 0.055, 0.0262), (0.009, 0.082, 0.029), 0.03)
    head.box(mat["dark"], (-0.005, 0.061, 0.029), (0.005, 0.076, 0.030), 0.02)
    add_rivets_y(head, mat["dark"], 0, 0, 0.044, 0.027, 8, 0.0013, math.pi / 8)

    fittings = Mesh()
    # Spring-steel belt clip folded down the rear quadrant of the explosive can.
    tube_path(fittings, mat["steel"], [(0.020, 0.100, -0.012), (0.029, 0.092, -0.012),
                                       (0.031, 0.050, -0.012), (0.024, 0.042, -0.012)], 0.0023, 8)
    fittings.box(mat["steel"], (0.025, 0.087, -0.014), (0.031, 0.094, -0.010), 0.03)

    cord = Mesh()
    tube_path(cord, mat["rubber"], [(0.002, -0.205, 0), (0.002, -0.219, 0.002),
                                   (0.006, -0.224, 0.003), (0.009, -0.218, 0.003)], 0.0015, 6)
    lathe_y(cord, mat["ivory"], 0.009, 0.003, [(-0.221, 0.003), (-0.216, 0.003)], 8, 0.02)

    root = b.add_node("stielhandgranate")
    children = [
        b.add_node("stielhandgranate.handle", b.add_mesh("turned_hardwood_handle", handle)),
        b.add_node("stielhandgranate.head", b.add_mesh("M1915_steel_explosive_pot", head)),
        b.add_node("stielhandgranate.belt_clip", b.add_mesh("folded_belt_clip", fittings)),
        b.add_node("stielhandgranate.pull_cord", b.add_mesh("porcelain_pull_bead_and_cord", cord)),
    ]
    b.set_children(root, children)
    return save_glb("stielhandgranate", b, root)


# --------------------------------------------------------------------------- German Wechselapparat M.1917 ("Wex")

def build_wex(textures: dict[str, bytes]) -> tuple[int, int]:
    b, mat = new_glb("German Wechselapparat M.1917 Wex flamethrower", textures)
    tank_x = -0.15
    lance_x = 0.14

    # The Wex's defining feature is a roughly 495 mm annular fuel reservoir with
    # a separate spherical compressed-gas vessel in its centre.
    tank = Mesh()
    add_torus(tank, mat["olive"], (tank_x, 0.0, 0.0), (0, 0, 1), 0.165, 0.0825, 64, 16)
    # Welded side seams, rolled outer edge, and fasteners around the reservoir shell.
    for z in (-0.052, 0.052):
        add_torus(tank, mat["dark"], (tank_x, 0.0, z), (0, 0, 1), 0.165, 0.0022, 64, 6)
    add_torus(tank, mat["steel"], (tank_x, 0.0, 0.0), (0, 0, 1), 0.244, 0.0022, 64, 6)
    for i in range(24):
        a = TAU * i / 24
        x, y = tank_x + 0.165 * math.cos(a), 0.165 * math.sin(a)
        tank.cylinder(mat["brass"], (x, y, 0.077), (x, y, 0.084), 0.0022, 6, mat["brass"], 0.025)

    propellant = Mesh()
    sphere_radius = 0.068
    sphere_stations = []
    for i in range(17):
        dz = -0.067 + i * (0.134 / 16)
        radius = sphere_radius * math.sqrt(max(0.0008, 1.0 - (dz / sphere_radius) ** 2))
        sphere_stations.append((dz, radius))
    lathe_z(propellant, mat["steel"], tank_x, 0.0, sphere_stations, 24, 0.06, math.pi / 24)
    # Neck, pressure valve and cap sit on the forward face of the spherical vessel.
    lathe_z(propellant, mat["brass"], tank_x, 0.0,
            [(0.062, 0.018), (0.071, 0.018), (0.078, 0.013), (0.090, 0.013)], 12, 0.035)
    lathe_z(propellant, mat["dark"], tank_x, 0.0, [(0.088, 0.015), (0.091, 0.015)], 12, 0.03)

    harness = Mesh()
    # Steel-plated mounting straps follow the back of the ring and clear the centre vessel.
    for x in (tank_x - 0.055, tank_x + 0.055):
        harness.oriented_box(mat["steel"], (x, 0.0, -0.079), (0.009, 0, 0),
                             (0, 0.19, 0), (0, 0, 0.003), 0.04)
    harness.oriented_box(mat["steel"], (tank_x, 0.0, -0.080), (0.15, 0, 0),
                         (0, 0.010, 0), (0, 0, 0.003), 0.04)
    # Padded shoulder/back straps and a lower retaining band, modelled as separate metal-backed runs.
    tube_path(harness, mat["rubber"], [(tank_x - 0.11, 0.20, -0.08), (tank_x - 0.17, 0.12, -0.14),
                                       (tank_x - 0.16, -0.10, -0.15), (tank_x - 0.07, -0.20, -0.13)], 0.009, 8)
    tube_path(harness, mat["rubber"], [(tank_x + 0.11, 0.20, -0.08), (tank_x + 0.17, 0.12, -0.14),
                                       (tank_x + 0.16, -0.10, -0.15), (tank_x + 0.07, -0.20, -0.13)], 0.009, 8)
    harness.box(mat["steel"], (tank_x - 0.13, -0.202, -0.145),
                (tank_x + 0.13, -0.187, -0.136), 0.04)

    fittings = Mesh()
    # Hinged fill closure on the top of the annular tank and a brass fuel outlet at the right quadrant.
    lathe_y(fittings, mat["steel"], tank_x, 0.0,
            [(0.223, 0.020), (0.244, 0.021), (0.280, 0.015)], 12, 0.035)
    lathe_y(fittings, mat["brass"], tank_x, 0.0, [(0.280, 0.010), (0.2975, 0.010)], 10, 0.025)
    outlet = (0.015, -0.045, 0.020)
    fittings.cylinder(mat["brass"], outlet, (outlet[0], outlet[1], outlet[2] + 0.040), 0.012, 10, mat["brass"], 0.035)
    fittings.cylinder(mat["steel"], (outlet[0], outlet[1], outlet[2] + 0.035),
                      (outlet[0], outlet[1], outlet[2] + 0.060), 0.008, 8, mat["steel"], 0.03)
    # A screw-coupling and shutoff cock on the tank-side connection.
    add_torus(fittings, mat["dark"], (outlet[0], outlet[1], outlet[2] + 0.043), (0, 0, 1), 0.011, 0.0018, 16, 6)

    lance = Mesh()
    # IWM records a 1,240 mm nozzle. The lance is built on that scale, with a tapered tube and machined collars.
    lathe_z(lance, mat["steel"], lance_x, 0.0,
            [(-0.620, 0.010), (-0.610, 0.015), (-0.595, 0.020), (-0.570, 0.020),
             (-0.545, 0.014), (-0.470, 0.012), (-0.300, 0.010), (-0.080, 0.009),
             (0.180, 0.010), (0.390, 0.011), (0.560, 0.013), (0.620, 0.015)], 16, 0.12)
    # Igniter head: burner collar, spring-loaded magnesium cartridge, guard ring and hinged fork.
    lathe_z(lance, mat["dark"], lance_x, 0.0,
            [(-0.625, 0.014), (-0.620, 0.022), (-0.596, 0.022), (-0.586, 0.016)], 16, 0.04)
    add_torus(lance, mat["brass"], (lance_x, 0.0, -0.594), (0, 0, 1), 0.020, 0.0018, 24, 6)
    add_torus(lance, mat["steel"], (lance_x, 0.0, -0.568), (0, 0, 1), 0.017, 0.0015, 24, 6)
    lathe_z(lance, mat["ivory"], lance_x, 0.0, [(-0.590, 0.006), (-0.582, 0.006), (-0.566, 0.005)], 10, 0.025)
    for i in range(8):
        a = TAU * i / 8
        x, y = lance_x + 0.019 * math.cos(a), 0.019 * math.sin(a)
        lance.cylinder(mat["steel"], (x, y, -0.606),
                       (lance_x + 0.015 * math.cos(a), 0.015 * math.sin(a), -0.565), 0.0015, 6, mat["steel"], 0.03)
    for z, radius in ((-0.22, 0.0115), (0.16, 0.012), (0.535, 0.014)):
        lathe_z(lance, mat["dark"], lance_x, 0.0, [(z, radius), (z + 0.006, radius)], 12, 0.04)

    stopcock = Mesh()
    stopcock.box(mat["brass"], (lance_x - 0.025, -0.060, 0.315),
                 (lance_x + 0.025, -0.012, 0.390), 0.035)
    stopcock.cylinder(mat["steel"], (lance_x - 0.031, -0.034, 0.355),
                      (lance_x + 0.033, -0.034, 0.355), 0.008, 10, mat["steel"], 0.03)
    # Brass hand wheel for the firing cock, with four real spokes.
    wheel_center = (lance_x + 0.042, -0.034, 0.355)
    add_torus(stopcock, mat["brass"], wheel_center, (0, 1, 0), 0.018, 0.0022, 20, 6)
    for i in range(4):
        a = TAU * i / 4
        end = (wheel_center[0] + 0.016 * math.cos(a), wheel_center[1],
               wheel_center[2] + 0.016 * math.sin(a))
        stopcock.cylinder(mat["brass"], wheel_center, end, 0.0016, 6, mat["brass"], 0.02)
    stopcock.cylinder(mat["steel"], (lance_x + 0.042, -0.044, 0.355),
                      (lance_x + 0.042, -0.022, 0.355), 0.003, 8, mat["steel"], 0.02)
    # Hose nipple and threaded union.
    stopcock.cylinder(mat["brass"], (lance_x, -0.065, 0.340),
                      (lance_x, -0.065, 0.300), 0.011, 10, mat["brass"], 0.03)
    add_torus(stopcock, mat["dark"], (lance_x, -0.065, 0.315), (0, 0, 1), 0.010, 0.0016, 16, 6)

    hose = Mesh()
    hose_path = [outlet,
                 (0.075, -0.100, 0.025), (0.118, -0.174, -0.025),
                 (0.128, -0.205, -0.112), (0.095, -0.185, -0.200),
                 (0.047, -0.118, -0.218), (0.045, -0.052, -0.158),
                 (0.105, -0.036, -0.055), (lance_x, -0.040, 0.112),
                 (lance_x, -0.058, 0.285)]
    # The museum specimen's feeder is a 55 mm corrugated rubber hose, about 1,100 mm long.
    corrugated_tube_path(hose, mat["rubber"], hose_path, 0.0275, 0.023, 10)
    hose.cylinder(mat["brass"], (outlet[0], outlet[1], outlet[2] + 0.035),
                  (outlet[0], outlet[1], outlet[2] + 0.060), 0.010, 10, mat["brass"], 0.03)
    hose.cylinder(mat["brass"], (lance_x, -0.065, 0.282), (lance_x, -0.065, 0.310), 0.010, 10, mat["brass"], 0.03)

    root = b.add_node("wex", translation=(0.12, 0.0, 0.0))
    children = [
        b.add_node("wex.lance_and_igniter", b.add_mesh("1_240_mm_lance_and_magnesium_igniter", lance)),
        b.add_node("wex.lance_stopcock", b.add_mesh("brass_firing_stopcock_and_handwheel", stopcock)),
        b.add_node("wex.annular_fuel_tank", b.add_mesh("495_mm_circular_fuel_reservoir", tank)),
        b.add_node("wex.central_propellant_vessel", b.add_mesh("spherical_compressed_gas_vessel", propellant)),
        b.add_node("wex.backpack_harness", b.add_mesh("steel_back_straps_and_harness", harness)),
        b.add_node("wex.tank_filler_and_outlet", b.add_mesh("tank_fill_cap_and_screw_outlet", fittings)),
        b.add_node("wex.feed_hose", b.add_mesh("1_100_mm_corrugated_rubber_hose", hose)),
    ]
    b.set_children(root, children)
    return save_glb("wex", b, root)


# --------------------------------------------------------------------------- WWI fuel canister

def build_fuel(textures: dict[str, bytes]) -> tuple[int, int]:
    b, mat = new_glb("WWI flamethrower fuel canister", textures)
    body = Mesh()
    lathe_y(body, mat["olive"], 0, 0,
            [(-0.113, 0.046), (-0.106, 0.063), (-0.094, 0.071), (-0.074, 0.074),
             (0.060, 0.074), (0.082, 0.068), (0.098, 0.049), (0.102, 0.034)], 16, 0.12, math.pi / 16)
    # Rolled steel seams and longitudinal reinforcing ribs.
    for y in (-0.092, -0.036, 0.032, 0.077):
        lathe_y(body, mat["steel"], 0, 0, [(y, 0.075), (y + 0.004, 0.075)], 16, 0.04)
    for i in range(8):
        a = TAU * i / 8
        x, z = 0.072 * math.cos(a), 0.072 * math.sin(a)
        body.cylinder(mat["dark"], (x, -0.060, z), (x, 0.056, z), 0.0017, 6, mat["dark"], 0.05)
    add_rivets_y(body, mat["brass"], 0, 0, 0.082, 0.057, 8, 0.0015, math.pi / 8)

    cap = Mesh()
    lathe_y(cap, mat["steel"], 0, 0, [(0.098, 0.030), (0.105, 0.029), (0.112, 0.023), (0.116, 0.014)], 12, 0.04)
    lathe_y(cap, mat["brass"], 0, 0, [(0.114, 0.010), (0.122, 0.010), (0.124, 0.007)], 10, 0.025)
    # Fill/pressure fittings and a sealing chain.
    lathe_y(cap, mat["dark"], 0.034, 0.0, [(0.105, 0.008), (0.116, 0.008)], 10, 0.025)
    tube_path(cap, mat["steel"], [(0.030, 0.110, 0), (0.042, 0.105, 0), (0.046, 0.098, 0)], 0.0014, 6)

    handle = Mesh()
    # A pair of forged lugs and a thick carry bail above the shoulder.
    handle.cylinder(mat["steel"], (-0.038, 0.094, 0), (-0.038, 0.133, 0), 0.0045, 8, mat["steel"], 0.03)
    handle.cylinder(mat["steel"], (0.038, 0.094, 0), (0.038, 0.133, 0), 0.0045, 8, mat["steel"], 0.03)
    tube_path(handle, mat["steel"], [(-0.038, 0.133, 0), (-0.030, 0.148, 0),
                                     (0.030, 0.148, 0), (0.038, 0.133, 0)], 0.0045, 8)
    # Two perimeter rows of raised fasteners.
    for y in (-0.075, 0.060):
        add_rivets_y(handle, mat["brass"], 0, 0, y, 0.073, 8, 0.0012, math.pi / 8)

    root = b.add_node("wwi_fuel")
    children = [
        b.add_node("wwi_fuel.body", b.add_mesh("riveted_flammol_container", body)),
        b.add_node("wwi_fuel.filler_cap", b.add_mesh("threaded_filler_and_seal", cap)),
        b.add_node("wwi_fuel.carry_handle", b.add_mesh("forged_carry_bail", handle)),
    ]
    b.set_children(root, children)
    return save_glb("wwi_fuel", b, root)


# --------------------------------------------------------------------------- item models, crafting and entry point

def save(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def item_display(name: str) -> dict:
    common = {
        "gui": {"rotation": [25, 140, 0], "translation": [0, 0, 0], "scale": [0.92, 0.92, 0.92]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.72, 0.72, 0.72]},
        "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 1.5, 0], "scale": [0.66, 0.66, 0.66]},
        "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 1.5, 0], "scale": [0.66, 0.66, 0.66]},
        "firstperson_righthand": {"rotation": [8, 24, -8], "translation": [0.6, 3.4, 0.2], "scale": [0.95, 0.95, 0.95]},
        "firstperson_lefthand": {"rotation": [8, 156, 8], "translation": [0.6, 3.4, 0.2], "scale": [0.95, 0.95, 0.95]},
    }
    if name == "wex":
        common.update({
            "gui": {"rotation": [22, 148, -4], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.40, 0.40, 0.40]},
            "firstperson_righthand": {"rotation": [2, 12, -4], "translation": [-0.8, 2.2, -1.8], "scale": [0.55, 0.55, 0.55]},
            "firstperson_lefthand": {"rotation": [2, 168, 4], "translation": [0.8, 2.2, -1.8], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 1.5, -0.5], "scale": [0.52, 0.52, 0.52]},
            "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 1.5, 0], "scale": [0.52, 0.52, 0.52]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.32, 0.32, 0.32]},
        })
    elif name == "stielhandgranate":
        common.update({
            "gui": {"rotation": [20, 140, -12], "translation": [0, 0, 0], "scale": [0.82, 0.82, 0.82]},
            "firstperson_righthand": {"rotation": [6, 18, -7], "translation": [0.6, 2.8, 0.1], "scale": [0.72, 0.72, 0.72]},
            "firstperson_lefthand": {"rotation": [6, 162, 7], "translation": [0.6, 2.8, 0.1], "scale": [0.72, 0.72, 0.72]},
        })
    elif name == "wwi_fuel":
        common.update({
            "gui": {"rotation": [24, 140, 0], "translation": [0, 0, 0], "scale": [0.86, 0.86, 0.86]},
            "firstperson_righthand": {"rotation": [8, 26, -8], "translation": [0.8, 3.8, 0.4], "scale": [0.74, 0.74, 0.74]},
            "firstperson_lefthand": {"rotation": [8, 154, 8], "translation": [0.8, 3.8, 0.4], "scale": [0.74, 0.74, 0.74]},
        })
    else:
        common.update({
            "gui": {"rotation": [24, 142, 0], "translation": [0, 0, 0], "scale": [1.15, 1.15, 1.15]},
            "firstperson_righthand": {"rotation": [8, 30, -8], "translation": [0.8, 4.0, 0.6], "scale": [1.45, 1.45, 1.45]},
            "firstperson_lefthand": {"rotation": [8, 150, 8], "translation": [0.8, 4.0, 0.6], "scale": [1.45, 1.45, 1.45]},
        })
    return common


def build_item_wrappers() -> None:
    names = ("mills_bomb", "stielhandgranate", "wex", "wwi_fuel")
    for name in names:
        save(ITEM_DIR / f"{name}.json", {
            "parent": "minecraft:builtin/entity",
            "gui_light": "side",
            "textures": {"particle": "aiarsenal:item/wwi/olive"},
            "display": item_display(name),
        })
        ingredients = {
            "mills_bomb": ["minecraft:iron_ingot", "minecraft:gunpowder"],
            "stielhandgranate": ["minecraft:iron_ingot", "minecraft:gunpowder", "minecraft:stick"],
            "wex": ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:leather"],
            "wwi_fuel": ["minecraft:coal", "minecraft:blaze_powder"],
        }[name]
        save(DATA / f"recipes/{name}.json", {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [{"item": item} for item in ingredients],
            "result": {"item": f"aiarsenal:{name}"},
        })
    translations = {
        "en_us": {
            "mills_bomb": "Mills bomb",
            "stielhandgranate": "Stick grenade (1915)",
            "wex": "Wex flame projector",
            "wwi_fuel": "Flamethrower fuel",
        },
        "ru_ru": {
            "mills_bomb": "Граната Миллса",
            "stielhandgranate": "Ручная граната обр. 1915 г.",
            "wex": "Огнемёт Wex",
            "wwi_fuel": "Топливо для огнемёта",
        },
    }
    for locale, labels in translations.items():
        path = ASSETS / f"lang/{locale}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update({f"item.aiarsenal.{name}": value for name, value in labels.items()})
        save(path, data)


def main() -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    textures = build_textures()
    builders = {
        "mills_bomb": build_mills,
        "stielhandgranate": build_stiel,
        "wex": build_wex,
        "wwi_fuel": build_fuel,
    }
    for name, build in builders.items():
        triangles, size = build(textures)
        print(f"{name}: {triangles:,} triangles, {size:,} bytes, embedded PBR textures")
    build_item_wrappers()
    print("WWI handheld equipment: 4 deterministic GLB models, item wrappers, textures, recipes and translations")


if __name__ == "__main__":
    main()
