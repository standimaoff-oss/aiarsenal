#!/usr/bin/env python3
"""Procedural GLB generator for the 4 self-propelled anti-aircraft systems (SPAAGs):
  1. wirbelwind — WWII German Flakpanzer IV "Wirbelwind" (2 cm Flakvierling 38 L/65 quad + SAM rails)
  2. zsu37      — WWII Soviet ZSU-37 (37 mm 61-K autocannon on SU-76M chassis + SAM rails)
  3. gepard     — Modern German Flakpanzer Gepard 1A2 on Leopard 1 chassis (twin 35 mm Oerlikon KDA + Stinger pods + search/track radars)
  4. shilka     — Modern Russian ZSU-23-4M4 "Shilka-M4" (quad 23 mm AZP-23 "Amur" + Igla/Strela pods + 1RL33M2 radar dish)
"""
from __future__ import annotations

import json
import math
import struct
from pathlib import Path

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
    w = h = 64
    pix = []
    for y in range(h):
        for x in range(w):
            v1 = math.sin(x * 0.14 + y * 0.09 + seed) + 0.5 * math.cos(x * 0.07 - y * 0.16)
            v2 = math.cos(x * 0.11 - y * 0.13 + seed * 1.7) + 0.4 * math.sin(y * 0.21)
            if v1 > 0.55:
                c = patch1
            elif v2 < -0.55:
                c = patch2
            else:
                c = base
            grain = int(4 * math.sin(x * 0.7 + y * 0.5) + 3 * math.cos(x * 0.3 - y * 0.9))
            pix.append((
                max(0, min(255, c[0] + grain)),
                max(0, min(255, c[1] + grain)),
                max(0, min(255, c[2] + grain)),
                255,
            ))
    return png_rgba(w, h, pix)


def track_loop_points(road_z: list[float], r_road: float, y_road: float,
                      spk_yz: tuple[float, float], r_spk: float,
                      idl_yz: tuple[float, float], r_idl: float,
                      rr_yz: list[tuple[float, float]], r_rr: float,
                      thick: float) -> list[tuple[float, float]]:
    """ Closed polygon path (y, z) around the running gear in glTF coordinates (+Y up, +Z forward). """
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
    offsets = (-width * 0.34, width * 0.34) if twin else (0.0,)
    w_each = width * 0.26 if twin else width * 0.46
    r_core = radius - 0.032 if teeth > 0 else radius
    for ox in offsets:
        m.cylinder(rubber if teeth == 0 else steel, (ox - w_each, 0, 0), (ox + w_each, 0, 0),
                   r_core, sides, paint if not dished else dark, 0.35)
        m.cylinder(paint, (ox - w_each * 1.08, 0, 0), (ox + w_each * 1.08, 0, 0),
                   r_core * 0.72, sides, paint, 0.30)
        m.cylinder(dark, (ox - w_each * 1.22, 0, 0), (ox + w_each * 1.22, 0, 0),
                   r_core * 0.26, max(8, sides // 2), steel, 0.22)
        for b_i in range(bolts):
            ang = math.tau * b_i / bolts
            by = math.sin(ang) * (r_core * 0.48)
            bz = math.cos(ang) * (r_core * 0.48)
            m.cylinder(steel, (ox - w_each * 1.16, by, bz), (ox + w_each * 1.16, by, bz),
                       r_core * 0.048, 6, steel, 0.12)
        if teeth > 0:
            for k in range(teeth):
                ang = math.tau * k / teeth
                cy = math.sin(ang) * (r_core + 0.014)
                cz = math.cos(ang) * (r_core + 0.014)
                m.oriented_box(steel, (ox, cy, cz),
                               (w_each * 0.55, 0, 0),
                               (0, math.sin(ang) * 0.018, math.cos(ang) * 0.018),
                               (0, -math.cos(ang) * 0.014, math.sin(ang) * 0.014), 0.2)
    if twin:
        m.cylinder(dark, (-width * 0.45, 0, 0), (width * 0.45, 0, 0), radius * 0.30, max(8, sides // 2), dark, 0.25)
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
            shoe.oriented_box(dark, (0, 0, 0), (hw, 0, 0), (0, ht, 0), (0, 0, pitch * 0.46), 0.35)
            if not lod:
                # Dual grouser bars, center guide horn, and track hinge pin lugs
                shoe.oriented_box(steel, (0, -ht - 0.010, -pitch * 0.18), (hw * 0.92, 0, 0), (0, 0.011, 0), (0, 0, pitch * 0.12), 0.25)
                shoe.oriented_box(steel, (0, -ht - 0.010, pitch * 0.18), (hw * 0.92, 0, 0), (0, 0.011, 0), (0, 0, pitch * 0.12), 0.25)
                shoe.oriented_box(steel, (0, ht + 0.024, 0), (0.020, 0, 0), (0, 0.026, 0), (0, 0, pitch * 0.18), 0.25)
                shoe.oriented_box(dark, (-hw * 0.48, 0, pitch * 0.42), (hw * 0.22, 0, 0), (0, ht * 1.15, 0), (0, 0, 0.014), 0.20)
                shoe.oriented_box(dark, (hw * 0.48, 0, pitch * 0.42), (hw * 0.22, 0, 0), (0, ht * 1.15, 0), (0, 0, 0.014), 0.20)
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
    """Twin radar-guided SAM canister pod with seeker domes and mounting struts."""
    m.box(paint, (cx - 0.16, cy - 0.11, cz - length * 0.5), (cx + 0.16, cy + 0.11, cz + length * 0.5), 0.45)
    for dx in (-0.075, 0.075):
        m.cylinder(dark, (cx + dx, cy, cz - length * 0.54), (cx + dx, cy, cz + length * 0.54),
                   0.058, 8 if lod else 14, dark, 0.30)
        if not lod:
            m.cylinder(missile_white, (cx + dx, cy, cz + length * 0.48), (cx + dx, cy, cz + length * 0.60),
                       0.044, 12, brass, 0.22)


def add_radar_dish(m: Mesh, paint: int, steel: int, dark: int,
                   radius: float, depth: float, lod: bool) -> None:
    """Parabolic search/tracking radar dish with feed horn centered at (0, 0, 0)."""
    m.cylinder(steel, (0, -0.16, 0), (0, 0.04, 0), 0.08, 8 if lod else 14, dark, 0.25)
    m.box(paint, (-0.14, -0.06, -0.14), (0.14, 0.14, 0.06), 0.30)
    segs = 10 if lod else 20
    for i in range(segs):
        a0 = math.tau * i / segs
        a1 = math.tau * (i + 1) / segs
        p0 = (math.cos(a0) * radius, radius + 0.08 + math.sin(a0) * radius * 0.68, depth * (1.0 - math.cos(a0) * 0.25))
        p1 = (math.cos(a1) * radius, radius + 0.08 + math.sin(a1) * radius * 0.68, depth * (1.0 - math.cos(a1) * 0.25))
        c0 = (0.0, radius + 0.08, 0.0)
        m.triangle(paint, c0, p0, p1)
        m.triangle(dark, c0, p1, p0)
    if not lod:
        m.cylinder(steel, (0, radius + 0.08, 0.0), (0, radius + 0.08, depth + 0.26), 0.028, 10, dark, 0.20)
        m.cylinder(dark, (0, radius + 0.08, depth + 0.22), (0, radius + 0.08, depth + 0.30), 0.055, 12, dark, 0.20)


# ==============================================================================
# 1. WIRBELWIND (Flakpanzer IV "Wirbelwind", WWII Germany)
# ==============================================================================
def build_wirbelwind(path: Path, lod: bool = False) -> tuple[int, float]:
    b = GlbBuilder("Flakpanzer IV Wirbelwind" + (" LOD1" if lod else ""))
    camo = b.add_texture(camo_texture((168, 148, 96), (92, 108, 66), (118, 76, 54), 41), "dunkelgelb_camo")
    steel_tex = b.add_texture(solid_texture(64, 64, (130, 134, 138), 42, 9), "steel")
    paint = b.add_material("Dunkelgelb ambush camo", (1.0, 1.0, 1.0, 1.0), 0.08, 0.82, camo)
    olive = b.add_material("Olivgruen trim", (0.78, 0.82, 0.72, 1.0), 0.08, 0.84, camo)
    steel = b.add_material("gunmetal steel", (0.58, 0.60, 0.62, 1.0), 0.72, 0.38, steel_tex)
    dark = b.add_material("dark ordnance steel", (0.28, 0.30, 0.32, 1.0), 0.65, 0.48, steel_tex)
    rubber = b.add_material("road wheel rubber", (0.20, 0.21, 0.22, 1.0), 0.04, 0.94, steel_tex)
    missile = b.add_material("missile body", (0.86, 0.88, 0.84, 1.0), 0.25, 0.55, steel_tex)
    brass = b.add_material("brass", (0.88, 0.76, 0.45, 1.0), 0.75, 0.32, steel_tex)

    hull = Mesh()
    # Lower tub & sloped glacis of Pz.Kpfw. IV Ausf. H (+Z forward, +Y up)
    hull.loft(paint, [
        (-2.90, 1.00, 0.42, 1.12),
        (-2.65, 1.02, 0.38, 1.58),
        (2.15, 1.02, 0.38, 1.68),
        (2.55, 1.02, 0.40, 1.18),
        (2.94, 0.98, 0.62, 1.02),
    ], 1.1)
    # Upper superstructure & fenders
    hull.box(paint, (-1.18, 1.02, -2.55), (1.18, 1.72, 2.18), 1.0)
    for sx in (-1.0, 1.0):
        x0, x1 = (1.00, 1.44) if sx > 0 else (-1.44, -1.00)
        hull.box(paint, (x0, 0.98, -2.85), (x1, 1.05, 2.85), 0.8)
        for b_idx, cz in enumerate((-1.53, -0.51, 0.51, 1.53)):
            bx0, bx1 = (0.98, 1.16) if sx > 0 else (-1.16, -0.98)
            hull.box(olive, (bx0, 0.36, cz - 0.28), (bx1, 0.62, cz + 0.28), 0.35)
    # Driver visor, bow MG ball, engine deck louvres, rear transverse exhaust muffler
    hull.box(paint, (0.35, 1.42, 2.15), (0.82, 1.62, 2.26), 0.35)
    hull.cylinder(dark, (-0.58, 1.46, 2.14), (-0.58, 1.46, 2.34), 0.11, 10 if lod else 14, dark, 0.25)
    hull.cylinder(dark, (-0.55, 0.84, -3.05), (0.55, 0.84, -3.05), 0.15, 10 if lod else 16, dark, 0.35)
    if not lod:
        for i in range(6):
            z = -1.85 + i * 0.24
            hull.box(dark, (-0.82, 1.71, z - 0.04), (0.82, 1.75, z + 0.04), 0.3)
        for sx in (-1.0, 1.0):
            hull.box(steel, (sx * 1.15 - 0.12, 1.05, -0.8), (sx * 1.15 + 0.12, 1.24, 0.9), 0.4)

    # Characteristic 9-sided open-topped faceted Wirbelwind turret centered at yawPivot (0, 1.72, -0.35)
    turret = Mesh()
    n_sides = 9
    r_bot, r_mid, r_top = 1.08, 1.26, 1.04
    y0, y1, y2 = 0.0, 0.46, 0.98
    wall_t = 0.08
    for i in range(n_sides):
        a0 = math.tau * i / n_sides + math.pi / n_sides
        a1 = math.tau * (i + 1) / n_sides + math.pi / n_sides
        # Skip the front lower slot on segment facing +Z so the quad 20mm mount projects cleanly
        for r_in_b, r_out_b, r_in_t, r_out_t, ya, yb in (
            (r_bot - wall_t, r_bot, r_mid - wall_t, r_mid, y0, y1),
            (r_mid - wall_t, r_mid, r_top - wall_t, r_top, y1, y2),
        ):
            p0 = (math.sin(a0) * r_out_b, ya, math.cos(a0) * r_out_b)
            p1 = (math.sin(a1) * r_out_b, ya, math.cos(a1) * r_out_b)
            p2 = (math.sin(a1) * r_out_t, yb, math.cos(a1) * r_out_t)
            p3 = (math.sin(a0) * r_out_t, yb, math.cos(a0) * r_out_t)
            q0 = (math.sin(a0) * r_in_b, ya, math.cos(a0) * r_in_b)
            q1 = (math.sin(a1) * r_in_b, ya, math.cos(a1) * r_in_b)
            q2 = (math.sin(a1) * r_in_t, yb, math.cos(a1) * r_in_t)
            q3 = (math.sin(a0) * r_in_t, yb, math.cos(a0) * r_in_t)
            turret.face(paint, [p0, p1, p2, p3], 0.8)
            turret.face(olive, [q3, q2, q1, q0], 0.8)
            turret.face(paint, [q3, p3, p2, q2], 0.5)
    turret.cylinder(dark, (0, 0.0, 0), (0, 0.14, 0), 0.92, 12 if lod else 18, dark, 0.6)

    # Cradle + quad 2 cm Flakvierling 38 L/65 barrels
    cradle = Mesh()
    cradle.box(olive, (-0.42, -0.24, -0.35), (0.42, 0.28, 0.35), 0.45)
    for sx in (-0.32, 0.32):
        cradle.box(dark, (sx - 0.08, -0.18, -0.15), (sx + 0.08, 0.26, 0.42), 0.30)

    barrels = Mesh()
    quad_offsets = [(-0.22, 0.14), (0.22, 0.14), (-0.22, -0.10), (0.22, -0.10)]
    for bx, by in quad_offsets:
        barrels.box(dark, (bx - 0.055, by - 0.06, -0.20), (bx + 0.055, by + 0.06, 0.48), 0.25)
        # 20-round box magazine sticking out at the side of each breech
        msx = -1.0 if bx < 0 else 1.0
        barrels.box(steel, (bx + msx * 0.04, by + 0.02, 0.02), (bx + msx * 0.22, by + 0.16, 0.24), 0.22)
        barrels.cylinder(dark, (bx, by, 0.45), (bx, by, 1.98), 0.026, 8 if lod else 12, dark, 0.25)
        barrels.cylinder(steel, (bx, by, 1.98), (bx, by, 2.15), 0.038, 8 if lod else 12, dark, 0.20)

    sam_l_mesh = Mesh()
    add_sam_pod(sam_l_mesh, olive, dark, missile, brass, 0.88, 0.18, 0.25, 1.15, lod)
    sam_r_mesh = Mesh()
    add_sam_pod(sam_r_mesh, olive, dark, missile, brass, -0.88, 0.18, 0.25, 1.15, lod)
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
    dish_n = b.add_node("radar_dish", b.add_mesh("radar_dish_mesh", dish_mesh), translation=(0.0, 0.96, -0.78))
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
    green_tex = b.add_texture(camo_texture((74, 92, 54), (58, 74, 42), (92, 108, 66), 53), "4bo_green")
    steel_tex = b.add_texture(solid_texture(64, 64, (124, 128, 132), 54, 9), "steel")
    paint = b.add_material("Soviet 4BO protective green", (1.0, 1.0, 1.0, 1.0), 0.06, 0.84, green_tex)
    dark_green = b.add_material("shaded 4BO green", (0.78, 0.82, 0.74, 1.0), 0.08, 0.86, green_tex)
    steel = b.add_material("ordnance steel", (0.60, 0.62, 0.64, 1.0), 0.72, 0.38, steel_tex)
    dark = b.add_material("blued barrel steel", (0.26, 0.28, 0.30, 1.0), 0.68, 0.44, steel_tex)
    rubber = b.add_material("road wheel rubber", (0.20, 0.21, 0.22, 1.0), 0.04, 0.94, steel_tex)
    missile = b.add_material("missile olive", (0.62, 0.68, 0.52, 1.0), 0.22, 0.58, green_tex)
    brass = b.add_material("brass", (0.88, 0.76, 0.45, 1.0), 0.75, 0.32, steel_tex)

    hull = Mesh()
    # SU-76M stepped hull: forward engine/transmission compartment, rear fighting compartment
    hull.loft(paint, [
        (-2.55, 0.94, 0.38, 1.28),
        (-0.15, 0.96, 0.36, 1.38),
        (1.85, 0.96, 0.36, 1.46),
        (2.38, 0.94, 0.42, 1.08),
        (2.62, 0.90, 0.62, 0.92),
    ], 1.0)
    for sx in (-1.0, 1.0):
        x0, x1 = (0.94, 1.34) if sx > 0 else (-1.34, -0.94)
        hull.box(paint, (x0, 0.92, -2.52), (x1, 0.99, 2.52), 0.8)
    # Driver hatch on glacis, starboard engine deck louvres, fuel drums at rear
    hull.box(dark_green, (-0.32, 1.22, 1.55), (0.32, 1.50, 2.12), 0.4)
    hull.box(dark, (0.22, 1.38, 0.15), (0.84, 1.49, 1.45), 0.35)
    if not lod:
        for sx in (-0.65, 0.65):
            hull.cylinder(dark_green, (sx - 0.22, 1.12, -2.58), (sx + 0.22, 1.12, -2.58), 0.16, 12, dark, 0.3)

    # Open-topped octagonal ZSU-37 turret at yawPivot (0, 1.48, -0.75)
    turret = Mesh()
    n_sides = 8
    for i in range(n_sides):
        a0 = math.tau * i / n_sides + math.pi / n_sides
        a1 = math.tau * (i + 1) / n_sides + math.pi / n_sides
        p0 = (math.sin(a0) * 0.98, 0.0, math.cos(a0) * 1.06)
        p1 = (math.sin(a1) * 0.98, 0.0, math.cos(a1) * 1.06)
        p2 = (math.sin(a1) * 0.88, 0.86, math.cos(a1) * 0.96)
        p3 = (math.sin(a0) * 0.88, 0.86, math.cos(a0) * 0.96)
        q0 = (math.sin(a0) * 0.90, 0.0, math.cos(a0) * 0.98)
        q1 = (math.sin(a1) * 0.90, 0.0, math.cos(a1) * 0.98)
        q2 = (math.sin(a1) * 0.80, 0.86, math.cos(a1) * 0.88)
        q3 = (math.sin(a0) * 0.80, 0.86, math.cos(a0) * 0.88)
        turret.face(paint, [p0, p1, p2, p3], 0.8)
        turret.face(dark_green, [q3, q2, q1, q0], 0.8)
        turret.face(paint, [q3, p3, p2, q2], 0.5)
    turret.cylinder(dark, (0, 0.0, 0), (0, 0.12, 0), 0.82, 12 if lod else 16, dark, 0.5)

    # 37 mm 61-K autocannon cradle & long barrel with recoil cylinders and clip tray
    cradle = Mesh()
    cradle.box(dark_green, (-0.28, -0.20, -0.35), (0.28, 0.24, 0.42), 0.4)
    cradle.cylinder(steel, (0, 0.16, -0.20), (0, 0.16, 0.68), 0.065, 10 if lod else 14, dark, 0.25)
    cradle.cylinder(dark, (0, -0.14, -0.25), (0, -0.14, 0.55), 0.075, 10 if lod else 14, dark, 0.25)

    barrel = Mesh()
    barrel.box(dark, (-0.12, -0.10, -0.45), (0.12, 0.14, 0.35), 0.30)
    barrel.box(brass, (-0.08, 0.14, -0.12), (0.08, 0.28, 0.22), 0.22)  # 5-round 37mm clip in feed tray
    barrel.cylinder(dark, (0, 0.0, 0.35), (0, 0.0, 1.65), 0.052, 10 if lod else 16, dark, 0.30)
    barrel.cylinder(dark, (0, 0.0, 1.65), (0, 0.0, 2.55), 0.038, 10 if lod else 16, dark, 0.25)
    barrel.cylinder(steel, (0, 0.0, 2.55), (0, 0.0, 2.75), 0.056, 10 if lod else 16, dark, 0.20)

    sam_l_mesh = Mesh()
    add_sam_pod(sam_l_mesh, dark_green, dark, missile, brass, 0.82, 0.16, 0.15, 1.20, lod)
    sam_r_mesh = Mesh()
    add_sam_pod(sam_r_mesh, dark_green, dark, missile, brass, -0.82, 0.16, 0.15, 1.20, lod)
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
    dish_n = b.add_node("radar_dish", b.add_mesh("radar_dish_mesh", dish_mesh), translation=(0.0, 0.86, -0.82))
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
    nato_tex = b.add_texture(camo_texture((72, 86, 62), (52, 46, 42), (108, 82, 58), 67), "nato_flecktarn")
    steel_tex = b.add_texture(solid_texture(64, 64, (132, 136, 140), 68, 8), "steel")
    paint = b.add_material("NATO 3-tone Flecktarn", (1.0, 1.0, 1.0, 1.0), 0.08, 0.80, nato_tex)
    olive = b.add_material("Bundeswehr Bronze-gruen", (0.76, 0.82, 0.72, 1.0), 0.10, 0.82, nato_tex)
    steel = b.add_material("machined steel", (0.64, 0.66, 0.68, 1.0), 0.75, 0.36, steel_tex)
    dark = b.add_material("Oerlikon gunmetal", (0.25, 0.27, 0.29, 1.0), 0.68, 0.44, steel_tex)
    rubber = b.add_material("track pad & skirt rubber", (0.18, 0.19, 0.20, 1.0), 0.03, 0.95, steel_tex)
    radome = b.add_material("radome tan-grey", (0.76, 0.74, 0.66, 1.0), 0.05, 0.70, steel_tex)
    brass = b.add_material("sensor lens", (0.88, 0.74, 0.40, 1.0), 0.75, 0.28, steel_tex)

    hull = Mesh()
    # Leopard 1 hull with well-sloped glacis, side sponsons, wavy rubber side skirts, and rear MTU engine grilles
    hull.loft(paint, [
        (-3.65, 1.22, 0.48, 1.48),
        (-3.25, 1.28, 0.42, 1.66),
        (2.35, 1.28, 0.42, 1.68),
        (3.25, 1.24, 0.46, 1.32),
        (3.68, 1.16, 0.78, 1.08),
    ], 1.2)
    for sx in (-1.0, 1.0):
        x_in = 1.22 if sx > 0 else -1.68
        x_out = 1.68 if sx > 0 else -1.22
        hull.box(paint, (x_in, 1.04, -3.55), (x_out, 1.64, 3.35), 1.0)
        # Characteristic Leopard 1 / Gepard heavy side skirts covering upper track run
        sx0 = 1.64 if sx > 0 else -1.70
        sx1 = 1.70 if sx > 0 else -1.64
        hull.box(rubber, (sx0, 0.72, -3.35), (sx1, 1.12, 3.25), 0.8)
        # Side engine exhaust grilles on rear hull quarters
        ex0 = 1.66 if sx > 0 else -1.72
        ex1 = 1.72 if sx > 0 else -1.66
        hull.box(dark, (ex0, 1.18, -3.10), (ex1, 1.54, -1.45), 0.4)
    # Front left auxiliary power unit (APU) housing + driver hatch on front right
    hull.box(olive, (0.35, 1.45, 2.35), (1.18, 1.78, 3.20), 0.5)
    hull.cylinder(olive, (-0.52, 1.64, 2.45), (-0.52, 1.74, 2.45), 0.32, 10 if lod else 16, dark, 0.35)
    if not lod:
        hull.cylinder(steel, (-0.85, 1.66, -2.35), (0.85, 1.66, -2.35), 0.58, 16, dark, 0.5)

    # Gepard turret at yawPivot (0, 1.72, 0.05) with front Ku-band tracking radar dome and commander/gunner periscopes
    turret = Mesh()
    turret.loft(paint, [
        (-1.68, 0.96, 0.0, 0.86),
        (-0.85, 1.06, 0.0, 0.96),
        (0.55, 0.94, 0.0, 0.92),
        (1.25, 0.62, 0.08, 0.76),
    ], 0.9)
    # Front Ku-band tracking radar dome between the gun pods
    turret.cylinder(olive, (0.0, 0.44, 1.15), (0.0, 0.44, 1.52), 0.36, 12 if lod else 18, olive, 0.35)
    turret.cylinder(radome, (0.0, 0.44, 1.50), (0.0, 0.44, 1.74), 0.34, 12 if lod else 18, radome, 0.30)
    # Optical periscopes on turret roof
    for sx in (-0.34, 0.34):
        turret.cylinder(steel, (sx, 0.92, 0.25), (sx, 1.18, 0.25), 0.12, 8 if lod else 12, brass, 0.25)

    # Twin outboard 35 mm Oerlikon KDA gun pods + muzzle-velocity V0 radar rings
    cradle = Mesh()
    for sx in (-0.92, 0.92):
        cradle.box(paint, (sx - 0.24, -0.28, -0.65), (sx + 0.24, 0.32, 0.55), 0.5)
        cradle.cylinder(olive, (sx - 0.26, 0.0, 0.0), (sx + 0.26, 0.0, 0.0), 0.30, 10 if lod else 16, dark, 0.35)

    barrels = Mesh()
    for sx in (-0.92, 0.92):
        barrels.box(olive, (sx - 0.14, -0.14, 0.45), (sx + 0.14, 0.16, 1.25), 0.35)
        barrels.cylinder(dark, (sx, 0.0, 1.20), (sx, 0.0, 2.88), 0.048, 10 if lod else 16, dark, 0.30)
        # Oerlikon KDA muzzle velocity sensor cage at the muzzle (z = 2.88 .. 3.15)
        barrels.cylinder(steel, (sx, 0.0, 2.86), (sx, 0.0, 2.94), 0.072, 10 if lod else 16, dark, 0.20)
        barrels.cylinder(steel, (sx, 0.0, 3.08), (sx, 0.0, 3.15), 0.072, 10 if lod else 16, dark, 0.20)
        if not lod:
            for a_i in range(4):
                ang = math.tau * a_i / 4 + math.pi / 4
                barrels.cylinder(steel,
                                 (sx + math.cos(ang) * 0.064, math.sin(ang) * 0.064, 2.88),
                                 (sx + math.cos(ang) * 0.064, math.sin(ang) * 0.064, 3.14),
                                 0.010, 6, steel, 0.15)

    sam_l_mesh = Mesh()
    add_sam_pod(sam_l_mesh, olive, dark, radome, brass, 1.22, 0.18, 0.10, 1.45, lod)
    sam_r_mesh = Mesh()
    add_sam_pod(sam_r_mesh, olive, dark, radome, brass, -1.22, 0.18, 0.10, 1.45, lod)
    dish_mesh = Mesh()
    add_radar_dish(dish_mesh, radome, steel, dark, 0.56, 0.14, lod)

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
    dish_n = b.add_node("radar_dish", b.add_mesh("radar_dish_mesh", dish_mesh), translation=(0.0, 0.96, -1.35))
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
    ru_tex = b.add_texture(camo_texture((68, 84, 58), (118, 112, 82), (42, 48, 38), 81), "russian_camo")
    steel_tex = b.add_texture(solid_texture(64, 64, (126, 130, 134), 82, 8), "steel")
    paint = b.add_material("Russian protective camo", (1.0, 1.0, 1.0, 1.0), 0.07, 0.82, ru_tex)
    dark_green = b.add_material("dark khaki trim", (0.76, 0.80, 0.72, 1.0), 0.08, 0.84, ru_tex)
    steel = b.add_material("steel", (0.62, 0.64, 0.66, 1.0), 0.72, 0.38, steel_tex)
    dark = b.add_material("AZP-23 gunmetal", (0.24, 0.26, 0.28, 1.0), 0.70, 0.42, steel_tex)
    rubber = b.add_material("road wheel rubber", (0.19, 0.20, 0.21, 1.0), 0.04, 0.94, steel_tex)
    missile = b.add_material("Igla pod olive", (0.58, 0.66, 0.50, 1.0), 0.20, 0.60, ru_tex)
    brass = b.add_material("seeker optics", (0.88, 0.76, 0.44, 1.0), 0.75, 0.30, steel_tex)

    hull = Mesh()
    # GM-575 hull: low-profile wedge nose with trim vane, wide flat roof deck, rear gas-turbine/diesel louvres
    hull.loft(paint, [
        (-3.18, 1.08, 0.42, 1.38),
        (-2.65, 1.14, 0.38, 1.48),
        (1.95, 1.14, 0.38, 1.50),
        (2.85, 1.10, 0.46, 1.18),
        (3.26, 1.04, 0.76, 0.98),
    ], 1.1)
    for sx in (-1.0, 1.0):
        x0, x1 = (1.08, 1.52) if sx > 0 else (-1.52, -1.08)
        hull.box(paint, (x0, 0.92, -3.12), (x1, 1.46, 2.95), 0.9)
    # Trim vane on glacis + driver vision hood
    hull.box(dark_green, (-0.98, 1.18, 2.75), (0.98, 1.34, 2.84), 0.5)
    hull.box(dark_green, (0.20, 1.44, 1.85), (0.72, 1.64, 2.38), 0.35)
    if not lod:
        for i in range(6):
            z = -2.55 + i * 0.22
            hull.box(dark, (-0.88, 1.47, z - 0.04), (0.88, 1.52, z + 0.04), 0.3)

    # Wide low-slung welded Shilka turret at yawPivot (0, 1.50, -0.10)
    turret = Mesh()
    turret.loft(paint, [
        (-1.55, 1.08, 0.0, 0.72),
        (-0.65, 1.24, 0.0, 0.80),
        (0.55, 1.16, 0.0, 0.76),
        (1.18, 0.78, 0.04, 0.62),
    ], 0.9)
    for sx in (-0.48, 0.48):
        turret.cylinder(dark_green, (sx, 0.76, -0.15), (sx, 0.88, -0.15), 0.28, 10 if lod else 14, dark, 0.3)

    # AZP-23 "Amur" quad 23 mm mount in front embrasure with water-cooling jackets & flash hiders
    cradle = Mesh()
    cradle.box(dark_green, (-0.52, -0.24, -0.35), (0.52, 0.26, 0.38), 0.45)

    barrels = Mesh()
    quad_23 = [(-0.24, 0.12), (0.24, 0.12), (-0.24, -0.10), (0.24, -0.10)]
    for bx, by in quad_23:
        barrels.cylinder(olive := dark_green, (bx, by, 0.15), (bx, by, 1.15), 0.056, 8 if lod else 12, dark, 0.25)
        barrels.cylinder(dark, (bx, by, 1.15), (bx, by, 2.05), 0.028, 8 if lod else 12, dark, 0.22)
        barrels.cylinder(steel, (bx, by, 2.05), (bx, by, 2.25), 0.042, 8 if lod else 12, dark, 0.18)
        if not lod:
            barrels.cylinder(steel, (bx, by + 0.055, 0.25), (bx, by + 0.055, 1.65), 0.012, 6, steel, 0.15)

    sam_l_mesh = Mesh()
    add_sam_pod(sam_l_mesh, dark_green, dark, missile, brass, 1.05, 0.25, -0.35, 1.35, lod)
    sam_r_mesh = Mesh()
    add_sam_pod(sam_r_mesh, dark_green, dark, missile, brass, -1.05, 0.25, -0.35, 1.35, lod)
    dish_mesh = Mesh()
    add_radar_dish(dish_mesh, dark_green, steel, dark, 0.48, 0.16, lod)

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
    dish_n = b.add_node("radar_dish", b.add_mesh("radar_dish_mesh", dish_mesh), translation=(0.0, 0.78, -1.28))
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
    # Subdivide hull into a 4x6 grid of walkable boxes so DeckShapes.count() > 20
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
    # Rotating turret group (group 1) centered around (0, turret_pz)
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

    # Running gear & tracks
    rect(8, 44, 55, 54, (42, 44, 46))
    for wx in range(13, 52, 7):
        rect(wx - 2, 46, wx + 2, 52, (86, 90, 94))
    # Hull
    rect(6, 35, 57, 45, hull_rgb)
    # Turret
    rect(16, 22, 42, 35, turret_rgb)
    # Radar dish at rear of turret
    if has_dish:
        rect(11, 13, 18, 24, (165, 168, 160))
        rect(15, 20, 19, 24, (90, 94, 98))
    # SAM pod on turret side
    rect(21, 25, 36, 29, (74, 86, 68))
    # Elevated AA barrels pointing up-right
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
