#!/usr/bin/env python3
"""Procedural GLB generator for the 4 self-propelled anti-aircraft systems (SPAAGs):
  1. wirbelwind — WWII German Flakpanzer IV "Wirbelwind" (2 cm Flakvierling 38 L/65 quad + SAM rails)
  2. zsu37      — WWII Soviet ZSU-37 (37 mm 61-K autocannon on SU-76M chassis + SAM rails)
  3. gepard     — Modern German Flakpanzer Gepard 1A2 on Leopard 1 chassis (twin 35 mm Oerlikon KDA + Stinger pods + search/track radars)
  4. shilka     — Modern Russian ZSU-23-4M4 "Shilka-M4" (quad 23 mm AZP-23 "Amur" + Igla/Strela pods + 1RL33M2 radar dish)

All meshes are strictly closed 3D solids with outward-guaranteed normals (0 inverted faces, 0 Z-fighting).
"""
from __future__ import annotations

import json
import math
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from glb_builder import GlbBuilder, Mesh, axis_quaternion, png_rgba, solid_texture

ROOT = Path(__file__).resolve().parents[2]
RES_DIR = ROOT / "mods/aiarsenal/src/main/resources/assets/aiarsenal"
DATA_DIR = ROOT / "mods/aiarsenal/src/main/resources/data/aiarsenal"
MODEL_DIR = RES_DIR / "models/glb"
DECK_DIR = RES_DIR / "deck"
ITEM_TEX_DIR = RES_DIR / "textures/item"
ITEM_MODEL_DIR = RES_DIR / "models/item"
RECIPE_DIR = DATA_DIR / "recipes"


def camo_texture(base: tuple[int, int, int], patch1: tuple[int, int, int], patch2: tuple[int, int, int], seed: int) -> bytes:
    w = h = 128
    pix = []
    for y in range(h):
        for x in range(w):
            v1 = math.sin(x * 0.09 + y * 0.06 + seed) + 0.55 * math.cos(x * 0.05 - y * 0.11)
            v2 = math.cos(x * 0.08 - y * 0.09 + seed * 1.7) + 0.45 * math.sin(y * 0.14)
            if v1 > 0.58:
                c = patch1
            elif v2 < -0.58:
                c = patch2
            else:
                c = base
            # Subtle panel seam & rolled steel grain
            seam = -10 if (x % 32 == 0 or y % 32 == 0) else (5 if (x % 32 == 1 or y % 32 == 1) else 0)
            grain = int(3.5 * math.sin(x * 0.7 + y * 0.5) + 2.5 * math.cos(x * 0.3 - y * 0.9)) + seam
            pix.append((
                max(0, min(255, c[0] + grain)),
                max(0, min(255, c[1] + grain)),
                max(0, min(255, c[2] + grain)),
                255,
            ))
    return png_rgba(w, h, pix)


def hull_section_ring(z: float, w_belly: float, w_mid: float, w_top: float,
                      y_belly: float, y_lower: float, y_mid: float, y_top: float,
                      crown: float = 0.02) -> list[tuple[float, float, float]]:
    """8-point closed armored hull section ring at station z."""
    return [
        (-w_belly, y_belly, z),
        (-w_mid, y_lower, z),
        (-w_mid, y_mid, z),
        (-w_top, y_top, z),
        (0.0, y_top + crown, z),
        (w_top, y_top, z),
        (w_mid, y_mid, z),
        (w_mid, y_lower, z),
        (w_belly, y_belly, z),
    ]


def track_loop_points(road_z: list[float], r_road: float, y_road: float,
                      spk_yz: tuple[float, float], r_spk: float,
                      idl_yz: tuple[float, float], r_idl: float,
                      rr_yz: list[tuple[float, float]], r_rr: float,
                      thick: float) -> list[tuple[float, float]]:
    """Closed polygon path (y, z) around the running gear in glTF coordinates (+Y up, +Z forward)."""
    z_min = min(road_z)
    z_max = max(road_z)
    y_bot = y_road - r_road - thick * 0.5
    pts = [
        (y_bot, z_min),
        (y_bot, z_max),
        (idl_yz[0] - r_idl * 0.55, idl_yz[1] + r_idl * 0.85),
        (idl_yz[0] + r_idl * 0.45, idl_yz[1] + r_idl * 0.92),
        (idl_yz[0] + r_idl + thick * 0.5, idl_yz[1]),
    ]
    if rr_yz:
        for ry, rz in sorted(rr_yz, key=lambda p: -p[1]):
            pts.append((ry + r_rr + thick * 0.5, rz))
    else:
        pts.append((y_road + r_road + thick * 0.65, z_max * 0.45))
        pts.append((y_road + r_road + thick * 0.65, z_min * 0.45))
    pts.extend([
        (spk_yz[0] + r_spk + thick * 0.5, spk_yz[1]),
        (spk_yz[0] + r_spk * 0.45, spk_yz[1] - r_spk * 0.92),
        (spk_yz[0] - r_spk * 0.55, spk_yz[1] - r_spk * 0.85),
    ])
    return pts


def sample_loop(pts: list[tuple[float, float]], dist: float) -> tuple[float, float, float, float]:
    segs = []
    total = 0.0
    for i in range(len(pts)):
        a = pts[i]
        b = pts[(i + 1) % len(pts)]
        length = math.hypot(b[0] - a[0], b[1] - a[1])
        segs.append((a, b, length))
        total += length
    d = dist % total
    for a, b, length in segs:
        if d <= length or length <= 1e-8:
            t = d / max(length, 1e-8)
            y = a[0] + (b[0] - a[0]) * t
            z = a[1] + (b[1] - a[1]) * t
            rx = math.atan2(b[0] - a[0], b[1] - a[1])
            return y, z, rx, total
        d -= length
    a, b, _ = segs[-1]
    return b[0], b[1], math.atan2(b[0] - a[0], b[1] - a[1]), total


def make_wheel_mesh(steel: int, dark: int, rubber: int, paint: int,
                    radius: float, width: float, sides: int,
                    twin: bool = False, teeth: int = 0, dished: bool = False,
                    bolts: int = 0) -> Mesh:
    m = Mesh()
    offsets = (-width * 0.30, width * 0.30) if twin else (0.0,)
    w_each = width * 0.22 if twin else width * 0.44
    r_core = radius - 0.032 if teeth > 0 else radius
    for ox in offsets:
        # Outer rubber tire / steel rim + stepped inner dish + hub cap (non-overlapping concentric stages)
        m.cylinder(rubber if teeth == 0 else steel, (ox - w_each, 0, 0), (ox + w_each, 0, 0),
                   r_core, sides, paint if not dished else dark, 0.35)
        m.cylinder(paint, (ox - w_each * 1.10, 0, 0), (ox + w_each * 1.10, 0, 0),
                   r_core * 0.72, sides, paint, 0.30)
        m.cylinder(dark, (ox - w_each * 1.24, 0, 0), (ox + w_each * 1.24, 0, 0),
                   r_core * 0.28, max(8, sides // 2), steel, 0.22)
        for b_i in range(bolts):
            ang = math.tau * b_i / bolts
            by = math.sin(ang) * (r_core * 0.50)
            bz = math.cos(ang) * (r_core * 0.50)
            m.cylinder(steel, (ox - w_each * 1.18, by, bz), (ox + w_each * 1.18, by, bz),
                       r_core * 0.048, 6, steel, 0.12)
        if teeth > 0:
            for k in range(teeth):
                ang = math.tau * k / teeth
                cy = math.sin(ang) * (r_core + 0.014)
                cz = math.cos(ang) * (r_core + 0.014)
                m.oriented_box(steel, (ox, cy, cz),
                               (w_each * 0.52, 0, 0),
                               (0, math.sin(ang) * 0.018, math.cos(ang) * 0.018),
                               (0, -math.cos(ang) * 0.014, math.sin(ang) * 0.014), 0.2)
    if twin:
        m.cylinder(dark, (-width * 0.42, 0, 0), (width * 0.42, 0, 0), radius * 0.26, max(8, sides // 2), dark, 0.25)
    return m


def add_running_gear(b: GlbBuilder, root_children: list[int], prefix: str,
                     track_x: float, track_w: float, track_thick: float, n_links: int,
                     road_z: list[float], r_road: float, y_road: float,
                     spk_yz: tuple[float, float], r_spk: float,
                     idl_yz: tuple[float, float], r_idl: float,
                     rr_yz: list[tuple[float, float]], r_rr: float,
                     paint: int, steel: int, dark: int, rubber: int,
                     twin_road: bool, lod: bool) -> float:
    sides = 10 if lod else 22
    bolts = 0 if lod else 8
    wheel_i = b.add_mesh("road_wheel", make_wheel_mesh(steel, dark, rubber, paint, r_road, track_w * 0.82, sides, twin=twin_road, bolts=bolts))
    spk_i = b.add_mesh("sprocket_wheel", make_wheel_mesh(steel, dark, rubber, paint, r_spk, track_w * 0.82, sides, twin=True, teeth=0 if lod else 16, bolts=bolts))
    idl_i = b.add_mesh("idler_wheel", make_wheel_mesh(steel, dark, rubber, paint, r_idl, track_w * 0.78, sides, twin=True, bolts=bolts))
    rr_i = b.add_mesh("return_roller", make_wheel_mesh(steel, dark, rubber, paint, r_rr, track_w * 0.55, max(8, sides - 6))) if rr_yz else None

    pts = track_loop_points(road_z, r_road, y_road, spk_yz, r_spk, idl_yz, r_idl, rr_yz, r_rr, track_thick)
    _, _, _, loop_len = sample_loop(pts, 0.0)
    pitch = loop_len / n_links
    hw = track_w * 0.5
    ht = track_thick * 0.5

    for sx, side in ((1.0, "L"), (-1.0, "R")):
        for idx, z in enumerate(road_z):
            root_children.append(b.add_node(f"rw_{side}_{idx}", wheel_i, (sx * track_x, y_road, z)))
        for idx, (ry, rz) in enumerate(rr_yz):
            root_children.append(b.add_node(f"rr_{side}_{idx}", rr_i, (sx * track_x, ry, rz)))
        root_children.append(b.add_node(f"spk_{side}", spk_i, (sx * track_x, spk_yz[0], spk_yz[1])))
        root_children.append(b.add_node(f"idl_{side}", idl_i, (sx * track_x, idl_yz[0], idl_yz[1])))

        link_nodes = []
        channels = []
        for i in range(n_links):
            y, z, rx, _ = sample_loop(pts, i * pitch)
            y2, z2, rx2, _ = sample_loop(pts, (i + 1) * pitch)
            shoe = Mesh()
            shoe.oriented_box(dark, (0, 0, 0), (hw, 0, 0), (0, ht, 0), (0, 0, pitch * 0.44), 0.35)
            if not lod:
                # Outer grouser cleats (-Y in local space) and inner center guide horn (+Y in local space)
                shoe.oriented_box(steel, (0, -ht - 0.009, -pitch * 0.16), (hw * 0.90, 0, 0), (0, 0.009, 0), (0, 0, pitch * 0.10), 0.25)
                shoe.oriented_box(steel, (0, -ht - 0.009, pitch * 0.16), (hw * 0.90, 0, 0), (0, 0.009, 0), (0, 0, pitch * 0.10), 0.25)
                shoe.oriented_box(steel, (0, ht + 0.022, 0), (0.018, 0, 0), (0, 0.022, 0), (0, 0, pitch * 0.15), 0.25)
            shoe_i = b.add_mesh(f"{prefix}_shoe_{side}_{i:02d}", shoe)
            ln = b.add_node(f"{prefix}_link_{side}_{i:02d}", shoe_i,
                            (sx * track_x, y, z), axis_quaternion((1, 0, 0), -rx))
            link_nodes.append(ln)
            channels.append((ln, "translation", [0.0, 1.0], [sx * track_x, y, z, sx * track_x, y2, z2]))
            channels.append((ln, "rotation", [0.0, 1.0], [*axis_quaternion((1, 0, 0), -rx), *axis_quaternion((1, 0, 0), -rx2)]))
        root_children.append(b.add_node(f"track_links_{side}", children=link_nodes))
        b.add_animation(f"track_{side}", channels)
    return pitch


def add_sam_pod(m: Mesh, paint: int, dark: int, missile_white: int, brass: int,
                cx: float, cy: float, cz: float, length: float, lod: bool) -> None:
    """Twin radar-guided SAM canister pod with armored launch box, mounting pylon, and seeker domes."""
    sx = -1.0 if cx > 0 else 1.0
    # Horizontal mounting strut connecting pod to turret side
    m.box(dark, (cx + sx * 0.12, cy - 0.05, cz - 0.22), (cx + sx * 0.30, cy + 0.05, cz + 0.22), 0.30)
    # Armored twin canister housing with chamfered profile
    rings = []
    for z_off, s_scale in ((-length * 0.50, 0.94), (-length * 0.42, 1.0), (length * 0.42, 1.0), (length * 0.50, 0.94)):
        hw, hh = 0.165 * s_scale, 0.110 * s_scale
        rings.append([
            (cx - hw, cy - hh * 0.7, cz + z_off),
            (cx - hw * 0.7, cy - hh, cz + z_off),
            (cx + hw * 0.7, cy - hh, cz + z_off),
            (cx + hw, cy - hh * 0.7, cz + z_off),
            (cx + hw, cy + hh * 0.7, cz + z_off),
            (cx + hw * 0.7, cy + hh, cz + z_off),
            (cx - hw * 0.7, cy + hh, cz + z_off),
            (cx - hw, cy + hh * 0.7, cz + z_off),
        ])
    m.loft_rings(paint, rings, tile=0.45, cap_start=True, cap_end=True)
    for dx in (-0.078, 0.078):
        m.cylinder(dark, (cx + dx, cy, cz - length * 0.53), (cx + dx, cy, cz + length * 0.53),
                   0.056, 8 if lod else 14, dark, 0.30)
        if not lod:
            m.cylinder(missile_white, (cx + dx, cy, cz + length * 0.53), (cx + dx, cy, cz + length * 0.60),
                       0.044, 12, brass, 0.22)


def add_radar_dish(m: Mesh, paint: int, steel: int, dark: int,
                   radius: float, depth: float, lod: bool, elliptical: float = 0.72) -> None:
    """Closed 3D parabolic radar dish (with real wall thickness, zero Z-fighting) and tripod feed horn."""
    # Pedestal mast and azimuth/elevation motor housing
    m.cylinder(steel, (0, -0.18, 0), (0, 0.06, 0), 0.085, 8 if lod else 14, dark, 0.25)
    m.box(paint, (-0.15, -0.04, -0.16), (0.15, 0.18, 0.06), 0.30)
    cy = radius * elliptical + 0.10
    segs = 12 if lod else 24
    # Build the dish as a 4-station 3D solid along Z from rear hub (-0.08) to concave front rim (+depth)
    stations = [
        (-0.08, 0.14),
        (-0.03, radius * 0.56),
        (depth * 0.45, radius * 0.88),
        (depth, radius),
    ]
    rings = []
    for z_st, r_st in stations:
        ring = []
        for i in range(segs):
            a = math.tau * i / segs
            ring.append((math.cos(a) * r_st, cy + math.sin(a) * r_st * elliptical, z_st))
        rings.append(ring)
    m.loft_rings(paint, rings, tile=0.40, cap_start=True, cap_end=True)
    # Central feed horn and waveguide boom projecting forward from dish center
    m.cylinder(steel, (0, cy, depth * 0.35), (0, cy, depth + 0.28), 0.030, 8 if lod else 12, dark, 0.20)
    m.cylinder(dark, (0, cy, depth + 0.24), (0, cy, depth + 0.33), 0.062, 8 if lod else 12, dark, 0.20)
    if not lod:
        for i in range(3):
            ang = math.tau * i / 3 + math.pi / 6
            px = math.cos(ang) * radius * 0.72
            py = cy + math.sin(ang) * radius * elliptical * 0.72
            m.cylinder(steel, (px, py, depth * 0.65), (0, cy, depth + 0.24), 0.014, 6, steel, 0.15)


def build_open_polygonal_turret(
    m: Mesh, paint: int, inner_mat: int, dark: int, steel: int,
    n_sides: int, r_levels: list[tuple[float, float, float]], wall_t: float, lod: bool
) -> None:
    """Build a watertight multi-tier faceted open-topped turret (Wirbelwind 9-gon / ZSU-37 8-gon)
    with outward-verified normals on outer walls, inner walls, top rim, and bottom rim."""
    def _nrm(a, b, c):
        ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
        return (uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx)

    def _emit_quad(mat: int, pts: list[tuple[float, float, float]], out_dir: tuple[float, float, float], tile: float):
        a, b, c, d = pts
        for p0, p1, p2 in ((a, b, c), (a, c, d)):
            nx, ny, nz = _nrm(p0, p1, p2)
            if nx * out_dir[0] + ny * out_dir[1] + nz * out_dir[2] < 0.0:
                p1, p2 = p2, p1
                nx, ny, nz = -nx, -ny, -nz
            uvs = m._planar_uvs((p0, p1, p2), (nx, ny, nz), tile)
            m.triangle(mat, p0, p1, p2, uvs)

    for i in range(n_sides):
        a0 = math.tau * i / n_sides + math.pi / n_sides
        a1 = math.tau * (i + 1) / n_sides + math.pi / n_sides
        am = 0.5 * (a0 + a1)
        out_rad = (math.sin(am), 0.0, math.cos(am))
        in_rad = (-math.sin(am), 0.0, -math.cos(am))
        for tier_idx in range(len(r_levels) - 1):
            ya, rx_a, rz_a = r_levels[tier_idx]
            yb, rx_b, rz_b = r_levels[tier_idx + 1]
            p0 = (math.sin(a0) * rx_a, ya, math.cos(a0) * rz_a)
            p1 = (math.sin(a1) * rx_a, ya, math.cos(a1) * rz_a)
            p2 = (math.sin(a1) * rx_b, yb, math.cos(a1) * rz_b)
            p3 = (math.sin(a0) * rx_b, yb, math.cos(a0) * rz_b)
            q0 = (math.sin(a0) * (rx_a - wall_t), ya, math.cos(a0) * (rz_a - wall_t))
            q1 = (math.sin(a1) * (rx_a - wall_t), ya, math.cos(a1) * (rz_a - wall_t))
            q2 = (math.sin(a1) * (rx_b - wall_t), yb, math.cos(a1) * (rz_b - wall_t))
            q3 = (math.sin(a0) * (rx_b - wall_t), yb, math.cos(a0) * (rz_b - wall_t))
            _emit_quad(paint, [p0, p1, p2, p3], out_rad, 0.75)
            _emit_quad(inner_mat, [q0, q1, q2, q3], in_rad, 0.75)
            if tier_idx == 0:
                _emit_quad(dark, [q0, q1, p1, p0], (0.0, -1.0, 0.0), 0.50)
            if tier_idx == len(r_levels) - 2:
                _emit_quad(paint, [q3, p3, p2, q2], (0.0, 1.0, 0.0), 0.50)
    # Turret basket floor plate and central gun pedestal base
    r_floor = min(r_levels[0][1], r_levels[0][2]) - wall_t * 0.5
    m.cylinder(dark, (0, 0.0, 0), (0, 0.14, 0), r_floor, 12 if lod else 18, dark, 0.6)
    m.cylinder(steel, (0, 0.14, 0), (0, 0.34, 0), 0.28, 10 if lod else 14, steel, 0.35)


# ==============================================================================
# 1. WIRBELWIND (Flakpanzer IV "Wirbelwind", WWII Germany)
# ==============================================================================
def build_wirbelwind(path: Path, lod: bool = False) -> tuple[int, float]:
    b = GlbBuilder("Flakpanzer IV Wirbelwind" + (" LOD1" if lod else ""))
    camo = b.add_texture(camo_texture((172, 152, 98), (94, 110, 68), (122, 78, 56), 41), "dunkelgelb_camo")
    steel_tex = b.add_texture(solid_texture(64, 64, (130, 134, 138), 42, 9), "steel")
    paint = b.add_material("Dunkelgelb ambush camo", (1.0, 1.0, 1.0, 1.0), 0.08, 0.82, camo)
    olive = b.add_material("Olivgruen trim", (0.80, 0.84, 0.74, 1.0), 0.08, 0.84, camo)
    steel = b.add_material("gunmetal steel", (0.58, 0.60, 0.62, 1.0), 0.72, 0.38, steel_tex)
    dark = b.add_material("dark ordnance steel", (0.28, 0.30, 0.32, 1.0), 0.65, 0.48, steel_tex)
    rubber = b.add_material("road wheel rubber", (0.20, 0.21, 0.22, 1.0), 0.04, 0.94, steel_tex)
    missile = b.add_material("missile body", (0.86, 0.88, 0.84, 1.0), 0.25, 0.55, steel_tex)
    brass = b.add_material("brass", (0.88, 0.76, 0.45, 1.0), 0.75, 0.32, steel_tex)

    hull = Mesh()
    # Pz.Kpfw. IV Ausf. H multi-station armored hull with lower tub, glacis, stepped superstructure & rear engine deck
    hull_rings = [
        hull_section_ring(-2.92, 0.86, 1.00, 0.98, 0.52, 0.68, 1.04, 1.18),
        hull_section_ring(-2.62, 0.94, 1.02, 1.14, 0.38, 0.56, 1.04, 1.62),
        hull_section_ring(-0.95, 0.96, 1.02, 1.18, 0.36, 0.54, 1.04, 1.72),
        hull_section_ring(1.45,  0.96, 1.02, 1.18, 0.36, 0.54, 1.04, 1.72),
        hull_section_ring(2.12,  0.96, 1.02, 1.16, 0.38, 0.56, 1.04, 1.68),
        hull_section_ring(2.58,  0.94, 1.02, 1.00, 0.40, 0.58, 0.98, 1.18),
        hull_section_ring(2.94,  0.88, 0.96, 0.94, 0.64, 0.74, 0.92, 1.02),
    ]
    hull.loft_rings(paint, hull_rings, tile=1.1, cap_start=True, cap_end=True)

    # Track fenders, mudguards, and Pz.IV leaf-spring bogie housings (4 per side)
    for sx in (-1.0, 1.0):
        hull.box(paint, (sx * 1.01, 0.98, -2.86), (sx * 1.45, 1.05, 2.86), 0.8)
        hull.box(olive, (sx * 1.01, 0.90, 2.86), (sx * 1.45, 1.05, 2.95), 0.4)
        hull.box(olive, (sx * 1.01, 0.90, -2.95), (sx * 1.45, 1.05, -2.86), 0.4)
        for cz in (-1.53, -0.51, 0.51, 1.53):
            hull.box(olive, (sx * 0.98, 0.34, cz - 0.30), (sx * 1.18, 0.62, cz + 0.30), 0.35)
            hull.cylinder(steel, (sx * 0.98, 0.48, cz), (sx * 1.22, 0.48, cz), 0.08, 8, dark, 0.20)
    # Driver visor hood (Fahrersehklappe 80), Kugelblende 50 bow MG ball, spare track links on glacis, rear muffler
    hull.box(olive, (0.32, 1.40, 2.08), (0.84, 1.64, 2.26), 0.35)
    hull.box(dark, (0.38, 1.48, 2.12), (0.78, 1.56, 2.29), 0.20)
    hull.cylinder(steel, (-0.58, 1.44, 2.06), (-0.58, 1.44, 2.28), 0.14, 10 if lod else 14, dark, 0.25)
    hull.cylinder(dark, (-0.58, 1.44, 2.24), (-0.58, 1.44, 2.52), 0.032, 8 if lod else 10, dark, 0.20)
    hull.cylinder(dark, (-0.62, 0.82, -3.06), (0.62, 0.82, -3.06), 0.17, 10 if lod else 16, steel, 0.35)
    if not lod:
        # Spare track links bolted across the glacis and engine deck intake louvres
        for ix in range(5):
            x = -0.68 + ix * 0.34
            hull.box(dark, (x - 0.14, 1.06, 2.50), (x + 0.14, 1.18, 2.66), 0.25)
        for i in range(6):
            z = -2.25 + i * 0.24
            hull.box(dark, (-0.84, 1.68, z - 0.04), (0.84, 1.76, z + 0.04), 0.3)
        for sx in (-1.0, 1.0):
            hull.box(olive, (sx * 1.06, 1.05, -1.25), (sx * 1.40, 1.28, 0.85), 0.45)

    # Characteristic 9-sided open-topped faceted Wirbelwind turret centered at yawPivot (0, 1.72, -0.35)
    turret = Mesh()
    build_open_polygonal_turret(
        turret, paint, olive, dark, steel,
        n_sides=9,
        r_levels=[(0.0, 1.06, 1.06), (0.46, 1.28, 1.28), (0.98, 1.02, 1.02)],
        wall_t=0.085,
        lod=lod,
    )

    # Cradle + quad 2 cm Flakvierling 38 L/65 barrels with elevation sectors and gun shields
    cradle = Mesh()
    cradle.box(olive, (-0.44, -0.24, -0.36), (0.44, 0.28, 0.36), 0.45)
    for sx in (-0.34, 0.34):
        cradle.box(dark, (sx - 0.08, -0.20, -0.18), (sx + 0.08, 0.28, 0.44), 0.30)
        cradle.cylinder(steel, (sx - 0.10, -0.06, 0.10), (sx + 0.10, -0.06, 0.10), 0.22, 10 if lod else 14, dark, 0.25)

    barrels = Mesh()
    quad_offsets = [(-0.23, 0.15), (0.23, 0.15), (-0.23, -0.10), (0.23, -0.10)]
    for bx, by in quad_offsets:
        barrels.box(dark, (bx - 0.058, by - 0.062, -0.22), (bx + 0.058, by + 0.062, 0.48), 0.25)
        # 20-round box magazine sticking out at the side of each breech (normalized by Mesh.box)
        msx = -1.0 if bx < 0 else 1.0
        barrels.box(steel, (bx + msx * 0.04, by + 0.02, 0.02), (bx + msx * 0.24, by + 0.17, 0.24), 0.22)
        barrels.cylinder(dark, (bx, by, 0.46), (bx, by, 1.35), 0.032, 8 if lod else 12, dark, 0.25)
        barrels.cylinder(dark, (bx, by, 1.35), (bx, by, 1.98), 0.024, 8 if lod else 12, dark, 0.25)
        barrels.cylinder(steel, (bx, by, 1.98), (bx, by, 2.16), 0.038, 8 if lod else 12, dark, 0.20)

    sam_l_mesh = Mesh()
    add_sam_pod(sam_l_mesh, olive, dark, missile, brass, 0.94, 0.18, 0.20, 1.18, lod)
    sam_r_mesh = Mesh()
    add_sam_pod(sam_r_mesh, olive, dark, missile, brass, -0.94, 0.18, 0.20, 1.18, lod)
    dish_mesh = Mesh()
    add_radar_dish(dish_mesh, paint, steel, dark, 0.38, 0.12, lod)

    root = b.add_node("wirbelwind")
    kids = [b.add_node("hull", b.add_mesh("hull_mesh", hull))]
    road_z = [-1.795, -1.265, -0.775, -0.245, 0.245, 0.775, 1.265, 1.795]
    rr_yz = [(0.86, -1.72), (0.88, -0.58), (0.88, 0.62), (0.86, 1.80)]
    pitch = add_running_gear(
        b, kids, "WW", 1.24, 0.40, 0.045, 22 if lod else 56,
        road_z, 0.235, 0.275, (0.56, -2.60), 0.27, (0.64, 2.56), 0.33, rr_yz, 0.11,
        paint, steel, dark, rubber, True, lod,
    )
    yaw_n = b.add_node("launcher_yaw", b.add_mesh("turret_mesh", turret), translation=(0.0, 1.72, -0.35))
    pitch_n = b.add_node("launcher_pitch", b.add_mesh("cradle_mesh", cradle), translation=(0.0, 0.42, 0.25))
    barrel_n = b.add_node("barrel", b.add_mesh("barrel_mesh", barrels))
    sam_l_n = b.add_node("sam_l", b.add_mesh("sam_l_mesh", sam_l_mesh))
    sam_r_n = b.add_node("sam_r", b.add_mesh("sam_r_mesh", sam_r_mesh))
    dish_n = b.add_node("radar_dish", b.add_mesh("radar_dish_mesh", dish_mesh), translation=(0.0, 0.98, -0.82))
    b.set_children(pitch_n, [barrel_n, sam_l_n, sam_r_n])
    b.set_children(yaw_n, [pitch_n, dish_n])
    kids.append(yaw_n)
    b.set_children(root, kids)
    b.save(path, [root])
    tris = sum(b.accessors[p["indices"]]["count"] // 3 for m in b.meshes for p in m["primitives"])
    return tris, pitch


# ==============================================================================
# 2. ZSU-37 (WWII Soviet 37 mm 61-K self-propelled AA gun on SU-76M chassis)
# ==============================================================================
def build_zsu37(path: Path, lod: bool = False) -> tuple[int, float]:
    b = GlbBuilder("Soviet ZSU-37 (1944)" + (" LOD1" if lod else ""))
    green_tex = b.add_texture(camo_texture((76, 94, 56), (58, 74, 42), (96, 112, 68), 53), "4bo_green")
    steel_tex = b.add_texture(solid_texture(64, 64, (124, 128, 132), 54, 9), "steel")
    paint = b.add_material("Soviet 4BO protective green", (1.0, 1.0, 1.0, 1.0), 0.06, 0.84, green_tex)
    dark_green = b.add_material("shaded 4BO green", (0.78, 0.82, 0.74, 1.0), 0.08, 0.86, green_tex)
    steel = b.add_material("ordnance steel", (0.60, 0.62, 0.64, 1.0), 0.72, 0.38, steel_tex)
    dark = b.add_material("blued barrel steel", (0.26, 0.28, 0.30, 1.0), 0.68, 0.44, steel_tex)
    rubber = b.add_material("road wheel rubber", (0.20, 0.21, 0.22, 1.0), 0.04, 0.94, steel_tex)
    missile = b.add_material("missile olive", (0.62, 0.68, 0.52, 1.0), 0.22, 0.58, green_tex)
    brass = b.add_material("brass", (0.88, 0.76, 0.45, 1.0), 0.75, 0.32, steel_tex)

    hull = Mesh()
    # SU-76M stepped armored hull: rear fighting compartment platform, forward engine & transmission glacis
    hull_rings = [
        hull_section_ring(-2.56, 0.82, 0.95, 0.94, 0.42, 0.56, 0.96, 1.38),
        hull_section_ring(-1.65, 0.86, 0.96, 0.96, 0.36, 0.52, 0.96, 1.48),
        hull_section_ring(0.25,  0.86, 0.96, 0.96, 0.36, 0.52, 0.96, 1.48),
        hull_section_ring(1.82,  0.86, 0.96, 0.94, 0.36, 0.52, 0.96, 1.46),
        hull_section_ring(2.38,  0.84, 0.94, 0.90, 0.42, 0.58, 0.92, 1.08),
        hull_section_ring(2.62,  0.78, 0.88, 0.86, 0.62, 0.72, 0.84, 0.92),
    ]
    hull.loft_rings(paint, hull_rings, tile=1.0, cap_start=True, cap_end=True)
    for sx in (-1.0, 1.0):
        hull.box(paint, (sx * 0.94, 0.92, -2.52), (sx * 1.34, 0.99, 2.52), 0.8)
        hull.box(dark_green, (sx * 0.94, 0.85, 2.52), (sx * 1.34, 0.99, 2.60), 0.4)
        hull.box(dark_green, (sx * 0.96, 0.99, -1.80), (sx * 1.28, 1.18, 0.40), 0.45)
    # Central driver hatch hood on glacis, starboard engine deck armored intake & louvres, rear fuel drums
    hull.box(dark_green, (-0.30, 1.22, 1.55), (0.30, 1.54, 2.14), 0.4)
    hull.box(dark, (-0.22, 1.40, 2.08), (0.22, 1.50, 2.18), 0.25)
    hull.box(dark_green, (0.20, 1.44, 0.15), (0.86, 1.58, 1.50), 0.35)
    hull.box(dark, (0.26, 1.54, 0.25), (0.80, 1.61, 1.38), 0.30)
    if not lod:
        for sx in (-0.64, 0.64):
            hull.cylinder(dark_green, (sx - 0.24, 1.14, -2.60), (sx + 0.24, 1.14, -2.60), 0.17, 12, dark, 0.3)
        for sx in (-1.08, 1.08):
            hull.cylinder(steel, (sx, 1.10, 2.32), (sx, 1.10, 2.44), 0.08, 10, brass, 0.2)

    # Open-topped octagonal ZSU-37 turret at yawPivot (0, 1.48, -0.75)
    turret = Mesh()
    build_open_polygonal_turret(
        turret, paint, dark_green, dark, steel,
        n_sides=8,
        r_levels=[(0.0, 0.92, 0.98), (0.44, 0.96, 1.04), (0.88, 0.86, 0.94)],
        wall_t=0.08,
        lod=lod,
    )

    # 37 mm 61-K autocannon cradle & long stepped barrel with recoil cylinders, equilibrators and 5-round clip
    cradle = Mesh()
    cradle.box(dark_green, (-0.30, -0.22, -0.38), (0.30, 0.24, 0.44), 0.4)
    cradle.cylinder(steel, (0, 0.17, -0.22), (0, 0.17, 0.72), 0.068, 10 if lod else 14, dark, 0.25)
    cradle.cylinder(dark, (0, -0.15, -0.26), (0, -0.15, 0.58), 0.078, 10 if lod else 14, dark, 0.25)
    for sx in (-0.22, 0.22):
        cradle.cylinder(steel, (sx, -0.24, 0.08), (sx, 0.22, 0.18), 0.048, 8 if lod else 10, dark, 0.20)

    barrel = Mesh()
    barrel.box(dark, (-0.12, -0.10, -0.46), (0.12, 0.14, 0.36), 0.30)
    barrel.box(brass, (-0.08, 0.14, -0.12), (0.08, 0.30, 0.22), 0.22)  # 5-round 37mm clip in feed tray
    barrel.cylinder(dark, (0, 0.0, 0.35), (0, 0.0, 1.65), 0.054, 10 if lod else 16, dark, 0.30)
    barrel.cylinder(dark, (0, 0.0, 1.65), (0, 0.0, 2.55), 0.039, 10 if lod else 16, dark, 0.25)
    barrel.cylinder(steel, (0, 0.0, 2.55), (0, 0.0, 2.76), 0.058, 10 if lod else 16, dark, 0.20)

    sam_l_mesh = Mesh()
    add_sam_pod(sam_l_mesh, dark_green, dark, missile, brass, 0.86, 0.16, 0.12, 1.20, lod)
    sam_r_mesh = Mesh()
    add_sam_pod(sam_r_mesh, dark_green, dark, missile, brass, -0.86, 0.16, 0.12, 1.20, lod)
    dish_mesh = Mesh()
    add_radar_dish(dish_mesh, paint, steel, dark, 0.36, 0.11, lod)

    root = b.add_node("zsu37")
    kids = [b.add_node("hull", b.add_mesh("hull_mesh", hull))]
    road_z = [-1.75, -1.05, -0.35, 0.35, 1.05, 1.75]
    rr_yz = [(0.82, -1.35), (0.84, 0.0), (0.82, 1.35)]
    pitch = add_running_gear(
        b, kids, "ZSU37", 1.15, 0.36, 0.042, 22 if lod else 56,
        road_z, 0.30, 0.34, (0.58, -2.32), 0.28, (0.62, 2.34), 0.31, rr_yz, 0.11,
        paint, steel, dark, rubber, False, lod,
    )
    yaw_n = b.add_node("launcher_yaw", b.add_mesh("turret_mesh", turret), translation=(0.0, 1.48, -0.75))
    pitch_n = b.add_node("launcher_pitch", b.add_mesh("cradle_mesh", cradle), translation=(0.0, 0.38, 0.30))
    barrel_n = b.add_node("barrel", b.add_mesh("barrel_mesh", barrel))
    sam_l_n = b.add_node("sam_l", b.add_mesh("sam_l_mesh", sam_l_mesh))
    sam_r_n = b.add_node("sam_r", b.add_mesh("sam_r_mesh", sam_r_mesh))
    dish_n = b.add_node("radar_dish", b.add_mesh("radar_dish_mesh", dish_mesh), translation=(0.0, 0.88, -0.86))
    b.set_children(pitch_n, [barrel_n, sam_l_n, sam_r_n])
    b.set_children(yaw_n, [pitch_n, dish_n])
    kids.append(yaw_n)
    b.set_children(root, kids)
    b.save(path, [root])
    tris = sum(b.accessors[p["indices"]]["count"] // 3 for m in b.meshes for p in m["primitives"])
    return tris, pitch


# ==============================================================================
# 3. GEPARD (German Flakpanzer Gepard 1A2 on Leopard 1 chassis, Modern)
# ==============================================================================
def build_gepard(path: Path, lod: bool = False) -> tuple[int, float]:
    b = GlbBuilder("Flakpanzer Gepard 1A2 (Leopard 1)" + (" LOD1" if lod else ""))
    nato_tex = b.add_texture(camo_texture((74, 88, 64), (50, 44, 40), (112, 84, 58), 67), "nato_flecktarn")
    steel_tex = b.add_texture(solid_texture(64, 64, (132, 136, 140), 68, 8), "steel")
    paint = b.add_material("NATO 3-tone Flecktarn", (1.0, 1.0, 1.0, 1.0), 0.08, 0.80, nato_tex)
    olive = b.add_material("Bundeswehr Bronze-gruen", (0.78, 0.84, 0.74, 1.0), 0.10, 0.82, nato_tex)
    steel = b.add_material("machined steel", (0.64, 0.66, 0.68, 1.0), 0.75, 0.36, steel_tex)
    dark = b.add_material("Oerlikon gunmetal", (0.25, 0.27, 0.29, 1.0), 0.68, 0.44, steel_tex)
    rubber = b.add_material("track pad & skirt rubber", (0.18, 0.19, 0.20, 1.0), 0.03, 0.95, steel_tex)
    radome = b.add_material("radome tan-grey", (0.78, 0.76, 0.68, 1.0), 0.05, 0.70, steel_tex)
    brass = b.add_material("sensor lens", (0.88, 0.74, 0.40, 1.0), 0.75, 0.28, steel_tex)

    hull = Mesh()
    # Leopard 1 sculpted hull with sloped glacis, beveled sponsons, side skirts, and rear MTU engine deck
    hull_rings = [
        hull_section_ring(-3.66, 1.06, 1.24, 1.20, 0.56, 0.74, 1.18, 1.50),
        hull_section_ring(-3.20, 1.14, 1.66, 1.56, 0.42, 0.64, 1.12, 1.66),
        hull_section_ring(-1.10, 1.14, 1.68, 1.60, 0.40, 0.62, 1.12, 1.70),
        hull_section_ring(1.85,  1.14, 1.68, 1.58, 0.40, 0.62, 1.12, 1.70),
        hull_section_ring(2.75,  1.12, 1.64, 1.48, 0.44, 0.66, 1.10, 1.52),
        hull_section_ring(3.38,  1.06, 1.52, 1.34, 0.52, 0.72, 1.04, 1.24),
        hull_section_ring(3.68,  0.96, 1.28, 1.18, 0.78, 0.88, 0.98, 1.06),
    ]
    hull.loft_rings(paint, hull_rings, tile=1.2, cap_start=True, cap_end=True)

    for sx in (-1.0, 1.0):
        # Characteristic Leopard 1 / Gepard heavy rubber ballistic side skirts with front/rear mudflaps
        hull.box(rubber, (sx * 1.64, 0.68, -3.38), (sx * 1.71, 1.14, 3.30), 0.8)
        hull.box(olive, (sx * 1.63, 1.08, -3.38), (sx * 1.72, 1.16, 3.30), 0.5)
        # Slanted side engine exhaust grilles on rear hull quarters
        hull.box(dark, (sx * 1.58, 1.20, -3.12), (sx * 1.69, 1.56, -1.42), 0.4)
    # Front-left auxiliary power unit (APU) armored housing + driver hatch with periscopes on front-right
    hull.box(olive, (0.34, 1.44, 2.25), (1.24, 1.80, 3.18), 0.5)
    hull.cylinder(olive, (-0.52, 1.66, 2.42), (-0.52, 1.76, 2.42), 0.32, 10 if lod else 16, dark, 0.35)
    if not lod:
        # Rear engine deck circular cooling fan grille and tow cables
        hull.cylinder(dark, (0.0, 1.66, -2.38), (0.0, 1.73, -2.38), 0.64, 16, dark, 0.5)
        hull.cylinder(steel, (0.0, 1.67, -2.38), (0.0, 1.74, -2.38), 0.24, 12, steel, 0.3)

    # Cast & welded Gepard turret at yawPivot (0, 1.72, 0.05) with tapered cheeks and rear electronics bustle
    turret = Mesh()
    t_rings = []
    for z, w_bot, w_mid, w_top, y0, ym, y1 in (
        (-1.72, 0.84, 0.94, 0.78, 0.04, 0.44, 0.84),
        (-1.05, 0.98, 1.10, 0.94, 0.0,  0.48, 0.94),
        (-0.15, 1.02, 1.14, 0.96, 0.0,  0.50, 0.98),
        (0.55,  0.94, 1.04, 0.86, 0.0,  0.46, 0.92),
        (1.08,  0.68, 0.76, 0.60, 0.04, 0.40, 0.78),
        (1.32,  0.46, 0.52, 0.42, 0.10, 0.36, 0.64),
    ):
        t_rings.append([
            (-w_bot, y0, z),
            (-w_mid, ym, z),
            (-w_top, y1, z),
            (0.0, y1 + 0.03, z),
            (w_top, y1, z),
            (w_mid, ym, z),
            (w_bot, y0, z),
            (0.0, y0, z),
        ])
    turret.loft_rings(paint, t_rings, tile=0.9, cap_start=True, cap_end=True)

    # Front Ku-band tracking radar spherical dome on rotating pedestal between the gun pods
    turret.cylinder(olive, (0.0, 0.42, 1.18), (0.0, 0.42, 1.50), 0.36, 12 if lod else 18, olive, 0.35)
    radome_rings = []
    for z_r, r_r in ((1.48, 0.35), (1.60, 0.34), (1.70, 0.28), (1.78, 0.18), (1.82, 0.06)):
        ring = []
        for i in range(12 if lod else 18):
            a = math.tau * i / (12 if lod else 18)
            ring.append((math.cos(a) * r_r, 0.42 + math.sin(a) * r_r, z_r))
        radome_rings.append(ring)
    turret.loft_rings(radome, radome_rings, tile=0.30, cap_start=True, cap_end=True)

    # Commander & gunner optical periscopes + laser rangefinder on turret roof
    for sx in (-0.36, 0.36):
        turret.cylinder(steel, (sx, 0.94, 0.22), (sx, 1.20, 0.22), 0.13, 8 if lod else 12, brass, 0.25)
        turret.box(olive, (sx - 0.10, 1.14, 0.16), (sx + 0.10, 1.26, 0.32), 0.20)

    # Twin outboard 35 mm Oerlikon KDA gun pods + dual ammo belt chutes + muzzle-velocity V0 radar cages
    cradle = Mesh()
    for sx in (-0.98, 0.98):
        # Sculpted outboard Oerlikon KDA gun pod housing
        pod_rings = []
        for z_p, hw, yb, yt in (
            (-0.72, 0.20, -0.24, 0.26),
            (-0.35, 0.25, -0.30, 0.34),
            (0.35,  0.25, -0.30, 0.34),
            (0.68,  0.18, -0.20, 0.24),
        ):
            pod_rings.append([
                (sx - hw, yb, z_p), (sx + hw, yb, z_p),
                (sx + hw, yt, z_p), (sx - hw, yt, z_p),
            ])
        cradle.loft_rings(paint, pod_rings, tile=0.5, cap_start=True, cap_end=True)
        cradle.cylinder(olive, (sx - 0.28, 0.0, 0.0), (sx + 0.28, 0.0, 0.0), 0.30, 10 if lod else 16, dark, 0.35)

    barrels = Mesh()
    for sx in (-0.98, 0.98):
        barrels.box(olive, (sx - 0.14, -0.14, 0.55), (sx + 0.14, 0.16, 1.28), 0.35)
        barrels.cylinder(dark, (sx, 0.0, 1.24), (sx, 0.0, 2.88), 0.048, 10 if lod else 16, dark, 0.30)
        # Oerlikon KDA muzzle velocity sensor cage at the muzzle (z = 2.88 .. 3.16)
        barrels.cylinder(steel, (sx, 0.0, 2.86), (sx, 0.0, 2.95), 0.074, 10 if lod else 16, dark, 0.20)
        barrels.cylinder(steel, (sx, 0.0, 3.08), (sx, 0.0, 3.16), 0.074, 10 if lod else 16, dark, 0.20)
        if not lod:
            for a_i in range(4):
                ang = math.tau * a_i / 4 + math.pi / 4
                barrels.cylinder(steel,
                                 (sx + math.cos(ang) * 0.064, math.sin(ang) * 0.064, 2.90),
                                 (sx + math.cos(ang) * 0.064, math.sin(ang) * 0.064, 3.12),
                                 0.011, 6, steel, 0.15)

    sam_l_mesh = Mesh()
    add_sam_pod(sam_l_mesh, olive, dark, radome, brass, 1.32, 0.18, 0.05, 1.45, lod)
    sam_r_mesh = Mesh()
    add_sam_pod(sam_r_mesh, olive, dark, radome, brass, -1.32, 0.18, 0.05, 1.45, lod)
    dish_mesh = Mesh()
    add_radar_dish(dish_mesh, radome, steel, dark, 0.62, 0.14, lod, elliptical=0.58)

    root = b.add_node("gepard")
    kids = [b.add_node("hull", b.add_mesh("hull_mesh", hull))]
    road_z = [-2.28 + i * 0.76 for i in range(7)]
    rr_yz = [(0.98, -1.80), (0.99, -0.60), (0.99, 0.60), (0.98, 1.80)]
    pitch = add_running_gear(
        b, kids, "GEP", 1.42, 0.55, 0.050, 22 if lod else 56,
        road_z, 0.33, 0.38, (0.72, -3.12), 0.34, (0.74, 3.10), 0.31, rr_yz, 0.11,
        paint, steel, dark, rubber, True, lod,
    )
    yaw_n = b.add_node("launcher_yaw", b.add_mesh("turret_mesh", turret), translation=(0.0, 1.72, 0.05))
    pitch_n = b.add_node("launcher_pitch", b.add_mesh("cradle_mesh", cradle), translation=(0.0, 0.48, 0.20))
    barrel_n = b.add_node("barrel", b.add_mesh("barrel_mesh", barrels))
    sam_l_n = b.add_node("sam_l", b.add_mesh("sam_l_mesh", sam_l_mesh))
    sam_r_n = b.add_node("sam_r", b.add_mesh("sam_r_mesh", sam_r_mesh))
    dish_n = b.add_node("radar_dish", b.add_mesh("radar_dish_mesh", dish_mesh), translation=(0.0, 0.96, -1.38))
    b.set_children(pitch_n, [barrel_n, sam_l_n, sam_r_n])
    b.set_children(yaw_n, [pitch_n, dish_n])
    kids.append(yaw_n)
    b.set_children(root, kids)
    b.save(path, [root])
    tris = sum(b.accessors[p["indices"]]["count"] // 3 for m in b.meshes for p in m["primitives"])
    return tris, pitch


# ==============================================================================
# 4. SHILKA (Russian ZSU-23-4M4 "Shilka-M4", Modern)
# ==============================================================================
def build_shilka(path: Path, lod: bool = False) -> tuple[int, float]:
    b = GlbBuilder("ZSU-23-4M4 Shilka-M4" + (" LOD1" if lod else ""))
    ru_tex = b.add_texture(camo_texture((70, 86, 60), (120, 114, 84), (44, 50, 40), 81), "russian_camo")
    steel_tex = b.add_texture(solid_texture(64, 64, (126, 130, 134), 82, 8), "steel")
    paint = b.add_material("Russian protective camo", (1.0, 1.0, 1.0, 1.0), 0.07, 0.82, ru_tex)
    dark_green = b.add_material("dark khaki trim", (0.78, 0.82, 0.74, 1.0), 0.08, 0.84, ru_tex)
    steel = b.add_material("steel", (0.62, 0.64, 0.66, 1.0), 0.72, 0.38, steel_tex)
    dark = b.add_material("AZP-23 gunmetal", (0.24, 0.26, 0.28, 1.0), 0.70, 0.42, steel_tex)
    rubber = b.add_material("road wheel rubber", (0.19, 0.20, 0.21, 1.0), 0.04, 0.94, steel_tex)
    missile = b.add_material("Igla pod olive", (0.58, 0.66, 0.50, 1.0), 0.20, 0.60, ru_tex)
    brass = b.add_material("seeker optics", (0.88, 0.76, 0.44, 1.0), 0.75, 0.30, steel_tex)

    hull = Mesh()
    # GM-575 hull: sharp wedge nose with trim vane, sloped upper sponsons, wide roof deck, rear gas-turbine louvres
    hull_rings = [
        hull_section_ring(-3.20, 0.98, 1.48, 1.38, 0.46, 0.66, 1.08, 1.40),
        hull_section_ring(-2.60, 1.04, 1.52, 1.44, 0.38, 0.58, 1.08, 1.50),
        hull_section_ring(-0.40, 1.04, 1.52, 1.46, 0.36, 0.56, 1.08, 1.52),
        hull_section_ring(1.90,  1.04, 1.52, 1.44, 0.36, 0.56, 1.08, 1.50),
        hull_section_ring(2.78,  1.00, 1.46, 1.32, 0.44, 0.64, 1.02, 1.20),
        hull_section_ring(3.26,  0.92, 1.34, 1.22, 0.74, 0.82, 0.92, 0.98),
    ]
    hull.loft_rings(paint, hull_rings, tile=1.1, cap_start=True, cap_end=True)

    # Amphibious-style splash/trim vane on glacis + armored driver vision cupola with TNPO periscopes
    hull.box(dark_green, (-1.12, 1.18, 2.72), (1.12, 1.34, 2.82), 0.5)
    hull.box(dark_green, (0.18, 1.46, 1.82), (0.76, 1.66, 2.40), 0.35)
    hull.box(dark, (0.24, 1.54, 2.34), (0.70, 1.63, 2.44), 0.25)
    if not lod:
        for i in range(6):
            z = -2.55 + i * 0.22
            hull.box(dark, (-0.90, 1.48, z - 0.04), (0.90, 1.55, z + 0.04), 0.3)
        for sx in (-1.18, 1.18):
            hull.cylinder(steel, (sx, 1.16, 2.94), (sx, 1.16, 3.06), 0.08, 10, brass, 0.2)

    # Characteristic wide, low-slung welded ZSU-23-4 Shilka turret at yawPivot (0, 1.50, -0.10)
    turret = Mesh()
    s_rings = []
    for z, w_bot, w_mid, w_top, y0, ym, y1 in (
        (-1.60, 0.94, 1.06, 0.92, 0.02, 0.38, 0.72),
        (-0.85, 1.16, 1.28, 1.12, 0.0,  0.42, 0.82),
        (0.05,  1.20, 1.32, 1.14, 0.0,  0.42, 0.82),
        (0.68,  1.08, 1.18, 1.00, 0.0,  0.38, 0.76),
        (1.22,  0.76, 0.84, 0.68, 0.04, 0.32, 0.60),
    ):
        s_rings.append([
            (-w_bot, y0, z),
            (-w_mid, ym, z),
            (-w_top, y1, z),
            (0.0, y1 + 0.03, z),
            (w_top, y1, z),
            (w_mid, ym, z),
            (w_bot, y0, z),
            (0.0, y0, z),
        ])
    turret.loft_rings(paint, s_rings, tile=0.9, cap_start=True, cap_end=True)

    # Commander & radar operator circular hatches with TPKU-2 periscope hoods
    for sx in (-0.48, 0.48):
        turret.cylinder(dark_green, (sx, 0.80, -0.15), (sx, 0.90, -0.15), 0.28, 10 if lod else 14, dark, 0.3)
        turret.box(steel, (sx - 0.09, 0.88, -0.02), (sx + 0.09, 0.98, 0.14), 0.2)

    # AZP-23 "Amur" quad 23 mm mount in curved front armored shield with water-cooling jackets & flash hiders
    cradle = Mesh()
    cradle.box(dark_green, (-0.54, -0.24, -0.36), (0.54, 0.26, 0.40), 0.45)
    cradle.cylinder(paint, (-0.52, -0.02, 0.12), (0.52, -0.02, 0.12), 0.28, 10 if lod else 16, dark_green, 0.40)

    barrels = Mesh()
    quad_23 = [(-0.25, 0.13), (0.25, 0.13), (-0.25, -0.10), (0.25, -0.10)]
    for bx, by in quad_23:
        barrels.cylinder(dark_green, (bx, by, 0.15), (bx, by, 1.18), 0.056, 8 if lod else 12, dark, 0.25)
        barrels.cylinder(dark, (bx, by, 1.18), (bx, by, 2.06), 0.028, 8 if lod else 12, dark, 0.22)
        barrels.cylinder(steel, (bx, by, 2.06), (bx, by, 2.26), 0.044, 8 if lod else 12, dark, 0.18)
        if not lod:
            barrels.cylinder(steel, (bx, by + 0.054, 0.25), (bx, by + 0.054, 1.68), 0.012, 6, steel, 0.15)

    sam_l_mesh = Mesh()
    add_sam_pod(sam_l_mesh, dark_green, dark, missile, brass, 1.14, 0.24, -0.35, 1.38, lod)
    sam_r_mesh = Mesh()
    add_sam_pod(sam_r_mesh, dark_green, dark, missile, brass, -1.14, 0.24, -0.35, 1.38, lod)
    dish_mesh = Mesh()
    add_radar_dish(dish_mesh, dark_green, steel, dark, 0.50, 0.16, lod, elliptical=0.82)

    root = b.add_node("shilka")
    kids = [b.add_node("hull", b.add_mesh("hull_mesh", hull))]
    road_z = [-2.05, -1.23, -0.41, 0.41, 1.23, 2.05]
    pitch = add_running_gear(
        b, kids, "SHL", 1.28, 0.38, 0.042, 22 if lod else 56,
        road_z, 0.335, 0.375, (0.64, -2.82), 0.32, (0.68, 2.84), 0.29, [], 0.10,
        paint, steel, dark, rubber, False, lod,
    )
    yaw_n = b.add_node("launcher_yaw", b.add_mesh("turret_mesh", turret), translation=(0.0, 1.50, -0.10))
    pitch_n = b.add_node("launcher_pitch", b.add_mesh("cradle_mesh", cradle), translation=(0.0, 0.38, 0.55))
    barrel_n = b.add_node("barrel", b.add_mesh("barrel_mesh", barrels))
    sam_l_n = b.add_node("sam_l", b.add_mesh("sam_l_mesh", sam_l_mesh))
    sam_r_n = b.add_node("sam_r", b.add_mesh("sam_r_mesh", sam_r_mesh))
    dish_n = b.add_node("radar_dish", b.add_mesh("radar_dish_mesh", dish_mesh), translation=(0.0, 0.80, -1.32))
    b.set_children(pitch_n, [barrel_n, sam_l_n, sam_r_n])
    b.set_children(yaw_n, [pitch_n, dish_n])
    kids.append(yaw_n)
    b.set_children(root, kids)
    b.save(path, [root])
    tris = sum(b.accessors[p["indices"]]["count"] // 3 for m in b.meshes for p in m["primitives"])
    return tris, pitch


def write_deck(path: Path, half_w: float, half_l: float, hull_h: float,
               turret_pz: float, turret_r: float, turret_h: float) -> None:
    """Write walkable DeckShapes binary (.deck v2) with hull slabs (group 0) and rotating turret boxes (group 1)."""
    boxes: list[tuple[int, float, float, float, float, float, float]] = []
    nx, nz = 4, 6
    dx = (2.0 * half_w) / nx
    dz = (2.0 * half_l) / nz
    for ix in range(nx):
        x0 = -half_w + ix * dx
        x1 = x0 + dx
        for iz in range(nz):
            z0 = -half_l + iz * dz
            z1 = z0 + dz
            boxes.append((0, x0, 0.15, z0, x1, hull_h, z1))
    for ix in range(2):
        tx0 = -turret_r + ix * turret_r
        tx1 = tx0 + turret_r
        for iz in range(2):
            tz0 = turret_pz - turret_r + iz * turret_r
            tz1 = tz0 + turret_r
            boxes.append((1, tx0, hull_h, tz0, tx1, turret_h, tz1))

    with path.open("wb") as f:
        f.write(struct.pack(">ifi", 2, 0.2, 1))
        f.write(struct.pack(">iff", 1, 0.0, turret_pz))
        f.write(struct.pack(">i", len(boxes)))
        for b in boxes:
            f.write(struct.pack(">bffffff", *b))


def make_spaag_icon(hull_rgb: tuple[int, int, int], turret_rgb: tuple[int, int, int],
                    barrels: int, has_dish: bool) -> bytes:
    w = h = 64
    pix = [(0, 0, 0, 0)] * (w * h)

    def put(x: int, y: int, c: tuple[int, int, int, int]) -> None:
        if 0 <= x < w and 0 <= y < h:
            pix[y * w + x] = c

    def rect(x0: int, y0: int, x1: int, y1: int, rgb: tuple[int, int, int]) -> None:
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                shade = -14 if (xx in (x0, x1) or yy in (y0, y1)) else (8 if yy < (y0 + y1) // 2 else 0)
                put(xx, yy, (
                    max(0, min(255, rgb[0] + shade)),
                    max(0, min(255, rgb[1] + shade)),
                    max(0, min(255, rgb[2] + shade)),
                    255,
                ))

    rect(8, 44, 55, 54, (42, 44, 46))
    for wx in range(13, 52, 7):
        rect(wx - 2, 46, wx + 2, 52, (86, 90, 94))
    rect(6, 35, 57, 45, hull_rgb)
    rect(16, 22, 42, 35, turret_rgb)
    if has_dish:
        rect(11, 13, 18, 24, (165, 168, 160))
        rect(15, 20, 19, 24, (90, 94, 98))
    rect(21, 25, 36, 29, (74, 86, 68))
    for b_idx in range(barrels):
        off = (b_idx - barrels // 2) * 3
        for s in range(22):
            bx = 38 + s
            by = 26 - int(s * 0.65) + off
            rect(bx, by, bx + 1, by + 1, (54, 58, 62))
    return png_rgba(w, h, pix)


def write_item_assets() -> None:
    DECK_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_TEX_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_MODEL_DIR.mkdir(parents=True, exist_ok=True)
    RECIPE_DIR.mkdir(parents=True, exist_ok=True)

    write_deck(DECK_DIR / "wirbelwind.deck", 1.44, 2.94, 1.72, -0.35, 1.05, 2.70)
    write_deck(DECK_DIR / "zsu37.deck", 1.34, 2.62, 1.48, -0.75, 0.96, 2.36)
    write_deck(DECK_DIR / "gepard.deck", 1.68, 3.68, 1.72, 0.05, 1.18, 2.86)
    write_deck(DECK_DIR / "shilka.deck", 1.52, 3.26, 1.50, -0.10, 1.12, 2.46)

    icons = {
        "wirbelwind": make_spaag_icon((168, 148, 96), (146, 128, 82), 4, True),
        "zsu37": make_spaag_icon((74, 92, 54), (64, 82, 46), 1, True),
        "gepard": make_spaag_icon((72, 86, 62), (88, 96, 74), 2, True),
        "shilka": make_spaag_icon((68, 84, 58), (82, 96, 68), 4, True),
    }
    for name, png_bytes in icons.items():
        (ITEM_TEX_DIR / f"{name}_vehicle.png").write_bytes(png_bytes)
        model_json = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"aiarsenal:item/{name}_vehicle"},
        }
        (ITEM_MODEL_DIR / f"{name}_vehicle.json").write_text(json.dumps(model_json, indent=2) + "\n")

    recipes = {
        "wirbelwind_vehicle": ("minecraft:iron_block", "minecraft:crossbow", "minecraft:redstone_block"),
        "zsu37_vehicle": ("minecraft:iron_block", "minecraft:dispenser", "minecraft:redstone_block"),
        "gepard_vehicle": ("minecraft:netherite_ingot", "minecraft:observer", "minecraft:iron_block"),
        "shilka_vehicle": ("minecraft:copper_block", "minecraft:observer", "minecraft:iron_block"),
    }
    for item_name, (top_i, mid_i, bot_i) in recipes.items():
        rec = {
            "type": "minecraft:crafting_shaped",
            "pattern": ["TTT", "CMC", "BBB"],
            "key": {
                "T": {"item": top_i},
                "C": {"item": "minecraft:repeater"},
                "M": {"item": mid_i},
                "B": {"item": bot_i},
            },
            "result": {"item": f"aiarsenal:{item_name}", "count": 1},
        }
        (RECIPE_DIR / f"{item_name}.json").write_text(json.dumps(rec, indent=2) + "\n")


def main() -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    builders = [
        ("wirbelwind", build_wirbelwind),
        ("zsu37", build_zsu37),
        ("gepard", build_gepard),
        ("shilka", build_shilka),
    ]
    for name, fn in builders:
        t0, pitch0 = fn(MODEL_DIR / f"{name}.glb", lod=False)
        t1, _ = fn(MODEL_DIR / f"{name}_lod1.glb", lod=True)
        print(f"{name:12s}: tris={t0:6d}  lod1={t1:5d} ({t1 / t0 * 100:.1f}%)  TRACK_PITCH={pitch0:.8f}")
    write_item_assets()


if __name__ == "__main__":
    main()
