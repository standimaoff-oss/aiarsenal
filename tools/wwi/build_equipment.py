#!/usr/bin/env python3
"""Scratch-built WWI infantry equipment models (Mills bomb No. 5, Stielhandgranate M1915,
Wex Flammenwerfer, fuel canister) using 8-sided rotated element profiles and 64x64 PBR-shaded textures."""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from glb_builder import png_rgba  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "mods/aiarsenal/src/main/resources/assets/aiarsenal"
DATA = ROOT / "mods/aiarsenal/src/main/resources/data/aiarsenal"


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
    """8-sided vertical cylinder built from two crossed boxes + a 45-deg rotated pair."""
    s = r * 0.4142
    r_in = r * 0.9239
    orig = [round(cx, 3), round(0.5 * (y0 + y1), 3), round(cz, 3)]
    return [
        box([cx - r_in, y0, cz - s], [cx + r_in, y1, cz + s], tex),
        box([cx - s, y0, cz - r_in], [cx + s, y1, cz + r_in], tex),
        box(
            [cx - r_in, y0, cz - s],
            [cx + r_in, y1, cz + s],
            tex,
            {"origin": orig, "axis": "y", "angle": 45.0},
        ),
        box(
            [cx - s, y0, cz - r_in],
            [cx + s, y1, cz + r_in],
            tex,
            {"origin": orig, "axis": "y", "angle": 45.0},
        ),
    ]


def oct_cyl_z(cx: float, cy: float, z0: float, z1: float, r: float, tex: str) -> list[dict]:
    """8-sided horizontal cylinder along Z."""
    s = r * 0.4142
    r_in = r * 0.9239
    orig = [round(cx, 3), round(cy, 3), round(0.5 * (z0 + z1), 3)]
    return [
        box([cx - r_in, cy - s, z0], [cx + r_in, cy + s, z1], tex),
        box([cx - s, cy - r_in, z0], [cx + s, cy + r_in, z1], tex),
        box(
            [cx - r_in, cy - s, z0],
            [cx + r_in, cy + s, z1],
            tex,
            {"origin": orig, "axis": "z", "angle": 45.0},
        ),
        box(
            [cx - s, cy - r_in, z0],
            [cx + s, cy + r_in, z1],
            tex,
            {"origin": orig, "axis": "z", "angle": 45.0},
        ),
    ]


def build_textures() -> None:
    """Generate 64x64 detailed textures with cast-iron pitting, wood lathe grain, blued steel and brass."""
    w = h = 64
    for name, base in [
        ("olive", (78, 86, 54)),
        ("steel", (66, 72, 80)),
        ("wood", (134, 88, 48)),
        ("brass", (184, 146, 66)),
    ]:
        rng = random.Random("wwi_v34_" + name)
        pix = []
        for y in range(h):
            for x in range(w):
                n = rng.randint(-5, 5)
                if name == "olive":
                    # Cast-iron granular pitting + dark groove grid
                    pit = int(5 * math.sin(x * 0.9) * math.cos(y * 0.9))
                    groove = -18 if (x % 16 in (0, 15) or y % 16 in (0, 15)) else 0
                    n += pit + groove
                elif name == "wood":
                    # Turned beech handle grain with lathe ring shading
                    grain = int(7 * math.sin(x * 0.55 + 1.2 * math.sin(y * 0.14)))
                    ring = -8 if (y % 16 in (0, 1)) else 0
                    n += grain + ring
                elif name == "steel":
                    brush = int(5 * math.sin(x * 0.4 + y * 0.15))
                    edge = 10 if (x in (1, 62) or y in (1, 62)) else 0
                    n += brush + edge
                elif name == "brass":
                    sheen = int(9 * math.cos((x - 24) * 0.12) * math.cos((y - 24) * 0.12))
                    n += sheen
                c = tuple(max(12, min(245, v + n)) for v in base) + (255,)
                pix.append(c)
        p = ASSETS / f"textures/item/wwi/{name}.png"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(png_rgba(w, h, pix))


def build_mills_bomb() -> list[dict]:
    """No. 5 Mk I Mills bomb: ovoid 5-tier 8-sided body, 4 rows of 8 cast segmentation lugs,
    base plug, top filler boss, striker housing, curved safety lever and split-ring pin."""
    e: list[dict] = []
    # Ovoid core (5 stacked 8-sided rings)
    tiers = [
        (3.2, 4.4, 1.85),
        (4.4, 6.2, 2.55),
        (6.2, 9.2, 2.85),
        (9.2, 11.0, 2.55),
        (11.0, 12.1, 1.85),
    ]
    for y0, y1, r in tiers:
        e.extend(oct_cyl_y(8.0, 8.0, y0, y1, r, "olive"))
    # 4 rows of protruding pineapple serration blocks around the circumference (cardinal + 45 deg)
    ring_spec = [
        (4.5, 6.0, 2.75),
        (6.3, 7.6, 3.08),
        (7.9, 9.2, 3.08),
        (9.4, 10.8, 2.75),
    ]
    for y0, y1, ro in ring_spec:
        for ang in (0.0, 45.0):
            rot = None if ang == 0.0 else {"origin": [8.0, round(0.5 * (y0 + y1), 3), 8.0], "axis": "y", "angle": 45.0}
            e.append(box([8.0 - ro, y0, 7.15], [8.0 + ro, y1, 8.85], "olive", rot))
            e.append(box([7.15, y0, 8.0 - ro], [8.85, y1, 8.0 + ro], "olive", rot))
    # Brass base plug with spanner holes + top striker boss & filler screw
    e.extend(oct_cyl_y(8.0, 8.0, 2.5, 3.25, 1.35, "brass"))
    e.extend(oct_cyl_y(8.0, 8.0, 12.1, 13.4, 1.25, "steel"))
    e.extend(oct_cyl_y(6.7, 8.0, 12.0, 12.7, 0.55, "brass"))
    # Top striker ears + curved safety lever hugging the right profile + pull ring
    e.append(box([7.3, 13.3, 7.2], [9.4, 14.2, 8.8], "steel"))
    e.append(box([9.2, 12.8, 7.45], [11.2, 13.6, 8.55], "steel", {"origin": [9.4, 13.3, 8.0], "axis": "z", "angle": -22.5}))
    e.append(box([10.7, 5.2, 7.5], [11.35, 12.9, 8.5], "steel"))
    e.append(box([9.8, 4.1, 7.55], [11.1, 5.5, 8.45], "steel", {"origin": [10.8, 5.2, 8.0], "axis": "z", "angle": 22.5}))
    # Split pull-ring on the left of the striker head
    e.append(box([6.1, 12.6, 6.8], [7.5, 14.0, 7.25], "brass", {"origin": [6.8, 13.3, 7.0], "axis": "z", "angle": 22.5}))
    return e


def build_stielhandgranate() -> list[dict]:
    """M1915 Stielhandgranate: 8-sided sheet-steel warhead can with rolled top/bottom seams,
    belt hook, tapered turned wooden handle with grip ribs, and bottom screw cap with pull porcelain bead."""
    e: list[dict] = []
    # Turned wooden handle (tapered 8-sided stations from bottom ferrule to warhead neck)
    handle_tiers = [
        (1.2, 2.8, 1.18),
        (2.8, 5.2, 1.02),
        (5.2, 7.8, 0.96),
        (7.8, 9.6, 1.08),
    ]
    for y0, y1, r in handle_tiers:
        e.extend(oct_cyl_y(8.0, 8.0, y0, y1, r, "wood"))
    # Bottom threaded steel ferrule + knurled screw cap (Abreißzünder cover) + porcelain pull bead
    e.extend(oct_cyl_y(8.0, 8.0, 0.9, 1.4, 1.25, "steel"))
    e.extend(oct_cyl_y(8.0, 8.0, 0.1, 0.95, 1.38, "brass"))
    e.extend(oct_cyl_y(8.0, 8.0, -0.35, 0.15, 0.55, "steel"))
    # Steel collar joining handle to the warhead pot
    e.extend(oct_cyl_y(8.0, 8.0, 9.5, 10.2, 1.45, "steel"))
    # Cylindrical explosive pot (8-sided) + rolled top/bottom crimp rims + top filler boss
    e.extend(oct_cyl_y(8.0, 8.0, 10.1, 15.3, 2.75, "olive"))
    e.extend(oct_cyl_y(8.0, 8.0, 10.0, 10.45, 2.92, "steel"))
    e.extend(oct_cyl_y(8.0, 8.0, 15.05, 15.55, 2.92, "steel"))
    e.extend(oct_cyl_y(8.0, 8.0, 15.5, 15.85, 1.15, "steel"))
    # Spring steel belt clip on the side of the pot
    e.append(box([10.75, 11.2, 7.55], [11.25, 15.1, 8.45], "steel"))
    e.append(box([10.95, 10.6, 7.6], [11.55, 11.4, 8.4], "steel", {"origin": [11.1, 11.2, 8.0], "axis": "z", "angle": -22.5}))
    return e


def build_wex() -> list[dict]:
    """Wex (1917) portable flamethrower: forward-extending Strahlrohr lance with dual wooden grips,
    brass stopcock valve, pressure gauge, ignition cage at the nozzle, corrugated feed hose and
    compact toroidal fuel pack at the rear (positioned so first-person view shows the lance!)."""
    e: list[dict] = []
    # Main steel lance tube (Strahlrohr) running forward from z=8 down to z=-11
    e.extend(oct_cyl_z(8.0, 7.6, -9.5, 8.2, 0.82, "steel"))
    # Tapered nozzle barrel + perforated hydrogen/match igniter shroud at the muzzle
    e.extend(oct_cyl_z(8.0, 7.6, -12.8, -9.4, 0.58, "steel"))
    e.extend(oct_cyl_z(8.0, 7.6, -14.2, -12.2, 1.05, "brass"))
    e.extend(oct_cyl_z(8.0, 7.6, -14.6, -14.0, 0.72, "steel"))
    # Brass main valve body, lever cock and top pressure gauge right in front of the operator
    e.extend(oct_cyl_z(8.0, 7.6, -1.8, 1.4, 1.25, "brass"))
    e.append(box([7.55, 8.6, -0.8], [8.45, 9.9, 2.4], "brass", {"origin": [8.0, 8.8, -0.2], "axis": "x", "angle": -22.5}))
    e.extend(oct_cyl_y(6.5, -0.4, 8.2, 9.6, 0.85, "brass"))
    # Parallel nitrogen pressure feed line under the main barrel
    e.extend(oct_cyl_z(8.0, 6.2, -12.2, 5.5, 0.36, "brass"))
    e.append(box([7.6, 6.2, -10.5], [8.4, 7.6, -9.7], "steel"))
    e.append(box([7.6, 6.2, -4.5], [8.4, 7.6, -3.7], "steel"))
    # Front and rear angled wooden pistol grips on the lance
    e.append(box([7.3, 3.2, -6.2], [8.7, 7.2, -4.6], "wood", {"origin": [8.0, 7.0, -5.4], "axis": "x", "angle": 22.5}))
    e.append(box([7.3, 3.2, 1.2], [8.7, 7.2, 2.8], "wood", {"origin": [8.0, 7.0, 2.0], "axis": "x", "angle": 22.5}))
    # Corrugated flexible rubber/braided hose curving from the rear of the lance to the pack
    for z in (8.0, 9.2, 10.4):
        e.extend(oct_cyl_z(8.0, 6.8, z, z + 0.9, 0.95, "olive"))
        e.extend(oct_cyl_z(8.0, 6.8, z + 0.85, z + 1.2, 1.08, "steel"))
    # Compact toroidal Wex ring tank at the back (z=11.2..15.2) + central spherical propellant dome + brass caps
    e.extend(oct_cyl_z(8.0, 7.5, 11.4, 15.0, 3.8, "olive"))
    e.extend(oct_cyl_z(8.0, 7.5, 10.8, 15.5, 2.1, "steel"))
    e.extend(oct_cyl_y(5.6, 13.2, 10.8, 12.2, 0.75, "brass"))
    e.extend(oct_cyl_y(10.4, 13.2, 10.8, 12.2, 0.75, "brass"))
    return e


def build_fuel() -> list[dict]:
    """Heavy riveted WWI Flammöl cylindrical drum with reinforcing ribs, brass filler cap and carry handle."""
    e: list[dict] = []
    e.extend(oct_cyl_y(8.0, 8.0, 1.8, 12.6, 3.6, "olive"))
    for y in (2.0, 5.4, 8.8, 12.1):
        e.extend(oct_cyl_y(8.0, 8.0, y, y + 0.55, 3.85, "steel"))
    e.extend(oct_cyl_y(8.0, 8.0, 12.5, 13.2, 2.8, "olive"))
    e.extend(oct_cyl_y(6.4, 8.0, 13.1, 14.3, 0.95, "brass"))
    e.extend(oct_cyl_y(9.7, 8.0, 13.1, 13.9, 0.62, "steel"))
    # Top wrought-iron carry handle arch
    e.append(box([7.4, 13.1, 5.6], [8.6, 14.6, 6.3], "steel"))
    e.append(box([7.4, 13.1, 9.7], [8.6, 14.6, 10.4], "steel"))
    e.append(box([7.4, 14.2, 5.6], [8.6, 14.9, 10.4], "wood"))
    return e


def main() -> None:
    build_textures()
    models = {
        "mills_bomb": build_mills_bomb(),
        "stielhandgranate": build_stielhandgranate(),
        "wex": build_wex(),
        "wwi_fuel": build_fuel(),
    }
    for name, elements in models.items():
        if name == "wex":
            # In first-person, put the Strahlrohr lance, valve, gauge and nozzle in view (pack behind the hand)
            display = {
                "gui": {"rotation": [24, 142, 0], "translation": [0.0, -0.5, 0.0], "scale": [0.58, 0.58, 0.58]},
                "firstperson_righthand": {"rotation": [8, 16, -4], "translation": [1.2, 3.0, 4.8], "scale": [0.58, 0.58, 0.58]},
                "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0.0, 1.5, -1.5], "scale": [0.55, 0.55, 0.55]},
                "ground": {"translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
            }
        elif name == "stielhandgranate":
            display = {
                "gui": {"rotation": [22, 138, -18], "translation": [0, 0, 0], "scale": [0.78, 0.78, 0.78]},
                "firstperson_righthand": {"rotation": [8, 28, -10], "translation": [0.8, 3.6, 0.5], "scale": [0.56, 0.56, 0.56]},
                "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 1.5, 0], "scale": [0.55, 0.55, 0.55]},
                "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            }
        else:
            display = {
                "gui": {"rotation": [25, 140, 0], "translation": [0, 0, 0], "scale": [0.82, 0.82, 0.82]},
                "firstperson_righthand": {"rotation": [10, 32, -8], "translation": [0.8, 4.2, 0.6], "scale": [0.56, 0.56, 0.56]},
                "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 1.5, 0], "scale": [0.55, 0.55, 0.55]},
                "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            }
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
    print("WWI equipment: 4 octagonal detailed item models, 64x64 textures, recipes and translations generated.")


if __name__ == "__main__":
    main()
