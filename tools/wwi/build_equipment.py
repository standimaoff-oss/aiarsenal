#!/usr/bin/env python3
"""Scratch-built WWI infantry equipment generator:
  1) High-detail 3D GLB models (mills_bomb.glb, stielhandgranate.glb, wex.glb, wwi_fuel.glb)
     with embedded 256x256 procedural textures and first-person hand anchors (<id>.arm_r / <id>.arm_l).
  2) Reproducible Minecraft item JSON models, 64x64 item textures, shapeless recipes and locales.
"""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path
from typing import Iterable, Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))
from glb_builder import GlbBuilder, Mesh, png_rgba  # noqa: E402

Vec3 = tuple[float, float, float]

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "mods/aiarsenal/src/main/resources/assets/aiarsenal"
MODEL_DIR = ASSETS / "models/glb"
DATA = ROOT / "mods/aiarsenal/src/main/resources/data/aiarsenal"


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
    uv_scale: float = 0.15,
    cap_start: bool = True,
    cap_end: bool = True,
) -> None:
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


def cyl_rings_y(cx: float, cz: float, y_r: Sequence[tuple[float, float]], sides: int = 20) -> list[list[Vec3]]:
    rings: list[list[Vec3]] = []
    for y, r in y_r:
        ring: list[Vec3] = []
        for i in range(sides):
            a = math.tau * i / sides
            ring.append((cx + r * math.cos(a), y, cz + r * math.sin(a)))
        rings.append(ring)
    return rings


def cyl_rings_z(cx: float, cy: float, z_r: Sequence[tuple[float, float]], sides: int = 20) -> list[list[Vec3]]:
    rings: list[list[Vec3]] = []
    for z, r in z_r:
        ring: list[Vec3] = []
        for i in range(sides):
            a = math.tau * i / sides
            ring.append((cx + r * math.cos(a), cy + r * math.sin(a), z))
        rings.append(ring)
    return rings


def glb_tex_olive() -> bytes:
    w = h = 128
    rng = random.Random(1915)
    pix = []
    for y in range(h):
        for x in range(w):
            pit = 5.0 * math.sin(x * 0.85) * math.cos(y * 0.85) + 3.0 * math.sin(x * 0.22 + y * 0.19)
            n = int(pit + rng.randrange(-4, 5))
            pix.append((max(20, min(220, 74 + n)), max(24, min(225, 82 + n)), max(16, min(195, 52 + n)), 255))
    return png_rgba(w, h, pix)


def glb_tex_steel() -> bytes:
    w = h = 128
    rng = random.Random(1916)
    pix = []
    for y in range(h):
        for x in range(w):
            brush = 4.5 * math.sin(x * 0.55) + 2.5 * math.cos(y * 0.38)
            n = int(brush + rng.randrange(-3, 4))
            pix.append((max(24, min(220, 62 + n)), max(28, min(225, 68 + n)), max(32, min(230, 76 + n)), 255))
    return png_rgba(w, h, pix)


def glb_tex_wood() -> bytes:
    w = h = 128
    rng = random.Random(1917)
    pix = []
    for y in range(h):
        for x in range(w):
            g = 9.0 * math.sin(x * 0.45 + 1.4 * math.sin(y * 0.11)) + rng.randrange(-3, 4)
            n = int(g)
            pix.append((max(35, min(230, 132 + n)), max(24, min(190, 86 + int(n * 0.8))), max(14, min(145, 46 + int(n * 0.55))), 255))
    return png_rgba(w, h, pix)


def build_mills_glb() -> None:
    """No. 5 Mk I Mills bomb GLB: cast-iron spheroid with 4x12 beveled pineapple segments,
    brass base plug, filler screw, striker housing, curved safety lever, split pull ring."""
    b = GlbBuilder("No. 5 Mk I Mills bomb (WWI fragmentation grenade)")
    t_olive = b.add_texture(glb_tex_olive(), "mills_cast_olive")
    t_steel = b.add_texture(glb_tex_steel(), "mills_steel")
    olive = b.add_material("cast_iron_olive", (0.95, 0.98, 0.90, 1), 0.22, 0.68, t_olive)
    groove = b.add_material("cast_groove_dark", (0.52, 0.54, 0.48, 1), 0.18, 0.78, t_olive)
    steel = b.add_material("blued_steel", (0.86, 0.90, 0.96, 1), 0.78, 0.32, t_steel)
    brass = b.add_material("brass_plug", (1.0, 0.82, 0.42, 1), 0.82, 0.26, t_steel)

    m = Mesh()
    # Unified closed 24-gon x 19-ring ribbed pineapple spheroid: every quad normal faces strictly outward from (0,y,0)
    n_az = 24  # 12 lugs + 12 vertical grooves
    # Stations (y, base_r, lug_extra)
    stations = [
        (-0.046, 0.013, 0.000),
        (-0.042, 0.021, 0.000),
        (-0.039, 0.023, 0.000),
        (-0.036, 0.024, 0.0045),
        (-0.024, 0.027, 0.0048),
        (-0.021, 0.026, 0.000),
        (-0.018, 0.027, 0.000),
        (-0.015, 0.028, 0.0052),
        (-0.003, 0.029, 0.0052),
        (0.000,  0.027, 0.000),
        (0.003,  0.029, 0.0052),
        (0.015,  0.028, 0.0052),
        (0.018,  0.027, 0.000),
        (0.021,  0.026, 0.000),
        (0.024,  0.027, 0.0048),
        (0.036,  0.024, 0.0045),
        (0.039,  0.023, 0.000),
        (0.042,  0.020, 0.000),
        (0.046,  0.013, 0.000),
    ]
    rings = []
    for y, br, dr in stations:
        ring = []
        for i in range(n_az):
            # 24 vertices: pairs of (lug, lug, groove) -> use 36 vertices (12 segments * 3)
            pass
    n_az = 36
    for idx in range(len(stations) - 1):
        y0, br0, dr0 = stations[idx]
        y1, br1, dr1 = stations[idx + 1]
        in_band = (dr0 > 0.0 and dr1 > 0.0)
        for k in range(12):
            for sub in range(3):
                i0 = k * 3 + sub
                i1 = (i0 + 1) % n_az
                a0 = math.tau * i0 / n_az
                a1 = math.tau * i1 / n_az
                # sub 0,1 are on the raised lug crown; sub 2 is the vertical groove
                e0_0 = dr0 if sub in (0, 1) else 0.0
                e1_0 = dr0 if ((sub + 1) % 3) in (0, 1) else 0.0
                e0_1 = dr1 if sub in (0, 1) else 0.0
                e1_1 = dr1 if ((sub + 1) % 3) in (0, 1) else 0.0
                r00, r10 = br0 + e0_0, br0 + e1_0
                r01, r11 = br1 + e0_1, br1 + e1_1
                p00 = (r00 * math.cos(a0), y0, r00 * math.sin(a0))
                p10 = (r10 * math.cos(a1), y0, r10 * math.sin(a1))
                p11 = (r11 * math.cos(a1), y1, r11 * math.sin(a1))
                p01 = (r01 * math.cos(a0), y1, r01 * math.sin(a0))
                mat = olive if (in_band and sub == 0) else groove
                # Outward normal from (0, y, 0)
                nx, ny, nz = _n3(p00, p10, p11)
                mx, mz = 0.25 * (p00[0] + p10[0] + p11[0] + p01[0]), 0.25 * (p00[2] + p10[2] + p11[2] + p01[2])
                if nx * mx + nz * mz >= 0:
                    m.face(mat, [p00, p10, p11, p01], 0.08)
                else:
                    m.face(mat, [p01, p11, p10, p00], 0.08)

    # Brass base plug with two spanner recesses
    loft_rings(m, brass, cyl_rings_y(0.0, 0.0, [(-0.052, 0.013), (-0.045, 0.015)], 18), 0.08)
    m.cylinder(groove, (-0.006, -0.053, 0.0), (-0.006, -0.050, 0.0), 0.0025, 8, groove, 0.05)
    m.cylinder(groove, (0.006, -0.053, 0.0), (0.006, -0.050, 0.0), 0.0025, 8, groove, 0.05)

    # Top striker column, side brass filler plug and striker ears
    loft_rings(m, steel, cyl_rings_y(0.0, 0.0, [(0.044, 0.013), (0.060, 0.012), (0.064, 0.010)], 18), 0.08)
    m.cylinder(brass, (-0.014, 0.042, 0.004), (-0.014, 0.049, 0.004), 0.0055, 12, brass, 0.06)
    m.box(steel, (-0.007, 0.058, -0.011), (0.007, 0.069, 0.012), 0.08)

    # Curved safety lever hugging the front (+Z) profile from top bridge down to the lower rib
    lever_stations = [
        (0.065, 0.008, 0.016),
        (0.056, 0.020, 0.027),
        (0.036, 0.031, 0.036),
        (0.010, 0.034, 0.039),
        (-0.018, 0.032, 0.037),
        (-0.036, 0.025, 0.030),
    ]
    l_rings = [
        [(-0.0055, y, z0), (0.0055, y, z0), (0.0055, y, z1), (-0.0055, y, z1)]
        for (y, z0, z1) in lever_stations
    ]
    loft_rings(m, steel, l_rings, 0.08)

    # Safety pin & circular split pull ring (12-segment torus on the side of the striker head)
    m.cylinder(brass, (-0.012, 0.063, 0.004), (0.012, 0.063, 0.004), 0.0018, 8, brass, 0.05)
    ring_cx, ring_cy, ring_cz, ring_R = -0.018, 0.056, -0.002, 0.012
    for i in range(12):
        a0 = math.tau * i / 12
        a1 = math.tau * (i + 1) / 12
        p0 = (ring_cx, ring_cy + ring_R * math.cos(a0), ring_cz + ring_R * math.sin(a0))
        p1 = (ring_cx, ring_cy + ring_R * math.cos(a1), ring_cz + ring_R * math.sin(a1))
        m.cylinder(brass, p0, p1, 0.0018, 8, brass, 0.05)

    mesh_i = b.add_mesh("mills_bomb_mesh", m)
    root = b.add_node("mills_bomb")
    body = b.add_node("mills_bomb.body", mesh_i)
    arm_r = b.add_node("mills_bomb.arm_r", translation=(0.0, -0.022, -0.018))
    b.set_children(root, [body, arm_r])
    b.save(MODEL_DIR / "mills_bomb.glb", [root])


def build_stiel_glb() -> None:
    """M1915 Stielhandgranate GLB: 24-sided rolled steel pot, belt clip, lathe-turned beech handle,
    knurled brass base cap and porcelain pull bead."""
    b = GlbBuilder("Stielhandgranate M1915 (WWI stick grenade)")
    t_olive = b.add_texture(glb_tex_olive(), "stiel_pot_olive")
    t_steel = b.add_texture(glb_tex_steel(), "stiel_steel")
    t_wood = b.add_texture(glb_tex_wood(), "stiel_beech")
    olive = b.add_material("pot_olive", (0.95, 0.98, 0.90, 1), 0.28, 0.62, t_olive)
    steel = b.add_material("rolled_steel", (0.86, 0.90, 0.96, 1), 0.78, 0.34, t_steel)
    brass = b.add_material("brass_cap", (1.0, 0.82, 0.44, 1), 0.80, 0.28, t_steel)
    wood = b.add_material("turned_beech", (1.0, 0.96, 0.90, 1), 0.04, 0.68, t_wood)

    m = Mesh()
    # Lathe-turned beech handle (y = -0.145 .. +0.035 m) with grip swell and finger grooves
    handle_profile = [
        (-0.142, 0.0155),
        (-0.115, 0.0145),
        (-0.085, 0.0132),
        (-0.055, 0.0126),
        (-0.025, 0.0124),
        (0.005, 0.0132),
        (0.028, 0.0148),
        (0.036, 0.0155),
    ]
    loft_rings(m, wood, cyl_rings_y(0.0, 0.0, handle_profile, 20), 0.14)
    # Bottom threaded steel ferrule + knurled brass screw cap + pull cord & bead
    loft_rings(m, steel, cyl_rings_y(0.0, 0.0, [(-0.150, 0.0165), (-0.140, 0.0165)], 20), 0.10)
    loft_rings(m, brass, cyl_rings_y(0.0, 0.0, [(-0.168, 0.0175), (-0.165, 0.0185), (-0.150, 0.0185)], 20), 0.10)
    loft_rings(m, steel, cyl_rings_y(0.0, 0.0, [(-0.180, 0.0045), (-0.174, 0.0075), (-0.168, 0.0045)], 14), 0.08)

    # Neck collar & cylindrical sheet-steel explosive head (y = +0.036 .. +0.132 m, radius 0.036 m)
    loft_rings(m, steel, cyl_rings_y(0.0, 0.0, [(0.034, 0.018), (0.045, 0.022), (0.048, 0.0375)], 24), 0.10)
    loft_rings(m, olive, cyl_rings_y(0.0, 0.0, [(0.048, 0.0365), (0.128, 0.0365)], 24), 0.14)
    # Rolled steel bottom and top crimp seams + top central stamping boss
    loft_rings(m, steel, cyl_rings_y(0.0, 0.0, [(0.046, 0.0385), (0.053, 0.0385)], 24), 0.10)
    loft_rings(m, steel, cyl_rings_y(0.0, 0.0, [(0.124, 0.0385), (0.132, 0.0385), (0.134, 0.035)], 24), 0.10)
    loft_rings(m, steel, cyl_rings_y(0.0, 0.0, [(0.132, 0.014), (0.138, 0.012)], 16), 0.08)
    # Side spring-steel belt hook
    hook = [
        (0.126, 0.036, 0.041),
        (0.085, 0.038, 0.043),
        (0.052, 0.039, 0.044),
        (0.040, 0.043, 0.048),
    ]
    h_rings = [[(-0.005, y, z0), (0.005, y, z0), (0.005, y, z1), (-0.005, y, z1)] for (y, z0, z1) in hook]
    loft_rings(m, steel, h_rings, 0.08)

    mesh_i = b.add_mesh("stielhandgranate_mesh", m)
    root = b.add_node("stielhandgranate")
    body = b.add_node("stielhandgranate.body", mesh_i)
    arm_r = b.add_node("stielhandgranate.arm_r", translation=(0.0, -0.055, -0.012))
    b.set_children(root, [body, arm_r])
    b.save(MODEL_DIR / "stielhandgranate.glb", [root])


def build_wex_glb() -> None:
    """Wex (1917) Flammenwerfer GLB: cylindrical Strahlrohr lance, conical nozzle, brass igniter shroud,
    stopcock valve with lever, pressure gauge, dual rounded wooden grips, corrugated hose and toroidal tank."""
    b = GlbBuilder("Wex (1917) Flammenwerfer")
    t_olive = b.add_texture(glb_tex_olive(), "wex_tank_olive")
    t_steel = b.add_texture(glb_tex_steel(), "wex_steel")
    t_wood = b.add_texture(glb_tex_wood(), "wex_grip_wood")
    olive = b.add_material("tank_olive", (0.94, 0.97, 0.88, 1), 0.30, 0.60, t_olive)
    steel = b.add_material("lance_steel", (0.86, 0.90, 0.96, 1), 0.80, 0.30, t_steel)
    dark = b.add_material("hose_rubber", (0.42, 0.44, 0.46, 1), 0.25, 0.75, t_steel)
    brass = b.add_material("valve_brass", (1.0, 0.82, 0.42, 1), 0.84, 0.25, t_steel)
    wood = b.add_material("grip_wood", (1.0, 0.95, 0.88, 1), 0.05, 0.66, t_wood)

    m = Mesh()
    # Forward Strahlrohr lance (+Z is towards muzzle in GunRenderer model space, just like Gewehr 98!)
    lance_profile = [
        (-0.22, 0.016),
        (-0.08, 0.016),
        (-0.06, 0.022),  # brass valve block
        (0.04, 0.022),
        (0.06, 0.015),
        (0.36, 0.014),
        (0.46, 0.010),
        (0.54, 0.010),
    ]
    loft_rings(m, steel, cyl_rings_z(0.0, 0.020, lance_profile, 20), 0.14)
    # Brass stopcock body, top lever handle and side pressure gauge (Manometer)
    loft_rings(m, brass, cyl_rings_z(0.0, 0.020, [(-0.065, 0.024), (0.045, 0.024)], 20), 0.10)
    m.cylinder(brass, (0.0, 0.042, -0.010), (0.0, 0.056, -0.095), 0.0055, 10, brass, 0.08)
    m.cylinder(brass, (-0.028, 0.038, -0.010), (-0.042, 0.052, -0.010), 0.018, 18, brass, 0.08)
    # Brass hydrogen/match igniter shroud at the muzzle (z = 0.44 .. 0.53 m)
    loft_rings(
        m,
        brass,
        cyl_rings_z(0.0, 0.020, [(0.43, 0.018), (0.45, 0.023), (0.52, 0.023), (0.54, 0.016)], 20),
        0.10,
    )
    # Parallel brass ignition/nitrogen tube underneath the main barrel
    m.cylinder(brass, (0.0, -0.002, -0.14), (0.0, -0.002, 0.45), 0.0055, 12, brass, 0.10)
    for zc in (-0.10, 0.12, 0.34):
        m.box(steel, (-0.008, -0.006, zc - 0.008), (0.008, 0.024, zc + 0.008), 0.08)

    # Rear and front rounded wooden pistol grips
    for gz in (-0.08, 0.22):
        g_rings = []
        for k, (dy, dz, rx, rz) in enumerate(
            ((0.012, 0.000, 0.013, 0.017), (-0.025, -0.008, 0.014, 0.019), (-0.065, -0.018, 0.015, 0.020), (-0.085, -0.022, 0.012, 0.016))
        ):
            ring = []
            for i in range(14):
                a = math.tau * i / 14
                ring.append((rx * math.cos(a), dy, gz + dz + rz * math.sin(a)))
            g_rings.append(ring)
        loft_rings(m, wood, g_rings, 0.10)

    # Corrugated hose behind the rear grip (z = -0.36 .. -0.22 m)
    hose_profile = []
    for i in range(9):
        z = -0.22 - i * 0.018
        hose_profile.append((z, 0.018 if i % 2 == 0 else 0.014))
    loft_rings(m, dark, cyl_rings_z(0.0, 0.016, hose_profile, 16), 0.10)

    mesh_i = b.add_mesh("wex_mesh", m)
    root = b.add_node("wex")
    body = b.add_node("wex.body", mesh_i)
    arm_r = b.add_node("wex.arm_r", translation=(0.0, -0.048, -0.088))
    arm_l = b.add_node("wex.arm_l", translation=(0.0, -0.048, 0.212))
    b.set_children(root, [body, arm_r, arm_l])
    b.save(MODEL_DIR / "wex.glb", [root])


def build_fuel_glb() -> None:
    """Riveted WWI Flammöl cylindrical fuel drum GLB."""
    b = GlbBuilder("WWI Flammöl fuel canister")
    t_olive = b.add_texture(glb_tex_olive(), "fuel_olive")
    t_steel = b.add_texture(glb_tex_steel(), "fuel_steel")
    olive = b.add_material("drum_olive", (0.94, 0.97, 0.88, 1), 0.28, 0.62, t_olive)
    steel = b.add_material("hoop_steel", (0.86, 0.90, 0.96, 1), 0.78, 0.34, t_steel)
    brass = b.add_material("cap_brass", (1.0, 0.82, 0.42, 1), 0.82, 0.26, t_steel)

    m = Mesh()
    drum = [
        (-0.085, 0.052),
        (-0.080, 0.056),
        (0.075, 0.056),
        (0.085, 0.044),
        (0.090, 0.020),
    ]
    loft_rings(m, olive, cyl_rings_y(0.0, 0.0, drum, 24), 0.14)
    for y in (-0.068, -0.020, 0.028, 0.070):
        loft_rings(m, steel, cyl_rings_y(0.0, 0.0, [(y - 0.005, 0.0585), (y + 0.005, 0.0585)], 24), 0.10)
    loft_rings(m, brass, cyl_rings_y(-0.020, 0.0, [(0.084, 0.014), (0.102, 0.016)], 16), 0.08)

    mesh_i = b.add_mesh("wwi_fuel_mesh", m)
    root = b.add_node("wwi_fuel")
    body = b.add_node("wwi_fuel.body", mesh_i)
    arm_r = b.add_node("wwi_fuel.arm_r", translation=(0.0, -0.020, -0.035))
    b.set_children(root, [body, arm_r])
    b.save(MODEL_DIR / "wwi_fuel.glb", [root])


# ---------------- JSON item fallback & test_equipment_assets contract ----------------
def save(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def box(lo: list[float], hi: list[float], tex: str, rot: dict | None = None) -> dict:
    el = {
        "from": [round(v, 3) for v in lo],
        "to": [round(v, 3) for v in hi],
        "faces": {
            f: {"texture": "#" + tex, "uv": [0, 0, 16, 16]}
            for f in ("up", "down", "north", "south", "east", "west")
        },
    }
    if rot is not None:
        el["rotation"] = rot
    return el


def oct_cyl_y(cx: float, cz: float, y0: float, y1: float, r: float, tex: str) -> list[dict]:
    s = r * 0.4142
    r_in = r * 0.9239
    orig = [round(cx, 3), round(0.5 * (y0 + y1), 3), round(cz, 3)]
    return [
        box([cx - r_in, y0, cz - s], [cx + r_in, y1, cz + s], tex),
        box([cx - s, y0, cz - r_in], [cx + s, y1, cz + r_in], tex),
        box([cx - r_in, y0, cz - s], [cx + r_in, y1, cz + s], tex, {"origin": orig, "axis": "y", "angle": 45.0}),
        box([cx - s, y0, cz - r_in], [cx + s, y1, cz + r_in], tex, {"origin": orig, "axis": "y", "angle": 45.0}),
    ]


def build_json_items() -> None:
    w = h = 64
    for name, base in [("olive", (78, 86, 54)), ("steel", (66, 72, 80)), ("wood", (134, 88, 48)), ("brass", (184, 146, 66))]:
        rng = random.Random("wwi_v35_" + name)
        pix = []
        for y in range(h):
            for x in range(w):
                n = rng.randint(-5, 5)
                c = tuple(max(12, min(245, v + n)) for v in base) + (255,)
                pix.append(c)
        p = ASSETS / f"textures/item/wwi/{name}.png"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(png_rgba(w, h, pix))

    models = {
        "mills_bomb": oct_cyl_y(8.0, 8.0, 3.5, 11.5, 2.8, "olive") + oct_cyl_y(8.0, 8.0, 11.5, 13.2, 1.3, "steel"),
        "stielhandgranate": oct_cyl_y(8.0, 8.0, 1.0, 10.0, 1.05, "wood") + oct_cyl_y(8.0, 8.0, 10.0, 15.2, 2.75, "olive"),
        "wex": oct_cyl_y(8.0, 8.0, 3.0, 12.0, 3.2, "olive") + [box([7.2, 6.5, -10.0], [8.8, 8.2, 8.0], "steel")],
        "wwi_fuel": oct_cyl_y(8.0, 8.0, 2.0, 12.5, 3.5, "olive") + oct_cyl_y(6.5, 8.0, 12.5, 14.0, 0.9, "brass"),
    }
    display = {
        "gui": {"rotation": [25, 140, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [0, 0, -8], "translation": [0, 5, 0], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 1, 0], "scale": [0.6, 0.6, 0.6]},
        "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    }
    for name, elements in models.items():
        save(
            ASSETS / f"models/item/{name}.json",
            {
                "gui_light": "side",
                "textures": {k: f"aiarsenal:item/wwi/{k}" for k in ("olive", "steel", "wood", "brass")}
                | {"particle": "aiarsenal:item/wwi/olive"},
                "elements": elements,
                "display": display,
            },
        )
        ingredients = {
            "mills_bomb": ["minecraft:iron_ingot", "minecraft:gunpowder"],
            "stielhandgranate": ["minecraft:iron_ingot", "minecraft:gunpowder", "minecraft:stick"],
            "wex": ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:leather"],
            "wwi_fuel": ["minecraft:coal", "minecraft:blaze_powder"],
        }[name]
        save(
            DATA / f"recipes/{name}.json",
            {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": i} for i in ingredients],
                "result": {"item": f"aiarsenal:{name}"},
            },
        )
    for language, labels in [
        (
            "en_us",
            {
                "mills_bomb": "Mills bomb",
                "stielhandgranate": "Stick grenade (1915)",
                "wex": "Wex flame projector",
                "wwi_fuel": "Flamethrower fuel",
            },
        ),
        (
            "ru_ru",
            {
                "mills_bomb": "Граната Миллса",
                "stielhandgranate": "Ручная граната обр. 1915 г.",
                "wex": "Огнемёт Wex",
                "wwi_fuel": "Топливо для огнемёта",
            },
        ),
    ]:
        p = ASSETS / f"lang/{language}.json"
        d = json.loads(p.read_text(encoding="utf-8"))
        d.update({f"item.aiarsenal.{k}": v for k, v in labels.items()})
        save(p, d)


def main() -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    build_mills_glb()
    build_stiel_glb()
    build_wex_glb()
    build_fuel_glb()
    build_json_items()
    print("WWI equipment: 4 high-detail GLBs + JSON models, textures, recipes and translations generated.")


if __name__ == "__main__":
    main()
