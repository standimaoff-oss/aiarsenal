"""Offline contracts for the reproducible WWI GLB models and equipment resources."""
from __future__ import annotations

import json
import math
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "mods/aiarsenal/src/main/resources/assets/aiarsenal"
DATA = ROOT / "mods/aiarsenal/src/main/resources/data/aiarsenal"
sys.path.insert(0, str(ROOT / "tools/wwi"))
from verify_assets import parse_glb, validate_glb  # noqa: E402


EQUIPMENT = {
    "mills_bomb": ({"mills_bomb.body", "mills_bomb.striker", "mills_bomb.safety_lever", "mills_bomb.pull_pin"}, 1500,
                   ((0.05, 0.09, 0.05), (0.08, 0.105, 0.08))),
    "stielhandgranate": ({"stielhandgranate.head", "stielhandgranate.handle", "stielhandgranate.belt_clip", "stielhandgranate.pull_cord"}, 1400,
                          ((0.05, 0.32, 0.05), (0.08, 0.38, 0.08))),
    "wex": ({"wex.lance_and_igniter", "wex.lance_stopcock", "wex.annular_fuel_tank", "wex.central_propellant_vessel", "wex.backpack_harness", "wex.tank_filler_and_outlet", "wex.feed_hose"}, 11000,
            ((0.55, 0.47, 1.20), (0.67, 0.57, 1.30))),
    "wwi_fuel": ({"wwi_fuel.body", "wwi_fuel.filler_cap", "wwi_fuel.carry_handle"}, 1600,
                 ((0.13, 0.24, 0.13), (0.18, 0.30, 0.18))),
}


class EquipmentAssets(unittest.TestCase):
    def test_equipment_items_use_custom_glb_models(self):
        for item, (nodes, min_triangles, (min_size, max_size)) in EQUIPMENT.items():
            with self.subTest(item=item):
                model = json.loads((ASSETS / f"models/item/{item}.json").read_text(encoding="utf-8"))
                self.assertEqual(model["parent"], "minecraft:builtin/entity")
                self.assertNotIn("elements", model, "the visible mesh must come from GLB, not block cubes")
                self.assertIn("firstperson_righthand", model["display"])
                particle = model["textures"]["particle"].split(":", 1)[-1]
                self.assertTrue((ASSETS / f"textures/{particle}.png").is_file())

                glb_path = ASSETS / f"models/glb/{item}.glb"
                stats = validate_glb(glb_path, set(), nodes)
                gltf, _ = parse_glb(glb_path)
                self.assertGreaterEqual(stats["triangles"], min_triangles)
                self.assertGreaterEqual(len(gltf.get("images", [])), 5)
                low, high = stats["bounds"]
                dimensions = tuple(high[axis] - low[axis] for axis in range(3))
                self.assertTrue(all(min_size[axis] <= dimensions[axis] <= max_size[axis]
                                    for axis in range(3)), f"{item} scale {dimensions} is out of range")
                self.assertGreater(stats["bytes"], 10_000)

    def test_recipes_locales_and_glb_item_render_registration(self):
        for item in EQUIPMENT:
            recipe = json.loads((DATA / f"recipes/{item}.json").read_text(encoding="utf-8"))
            self.assertEqual(recipe["result"]["item"], f"aiarsenal:{item}")
            for lang in ("en_us", "ru_ru"):
                strings = json.loads((ASSETS / f"lang/{lang}.json").read_text(encoding="utf-8"))
                self.assertIn(f"item.aiarsenal.{item}", strings)

        source = ROOT / "mods/aiarsenal/src/main/java/ru/aiforge/realwar/wwi"
        for item_file in ("GrenadeItem.java", "FlamethrowerItem.java", "WwiEquipmentItem.java"):
            text = (source / item_file).read_text(encoding="utf-8")
            self.assertIn("GlbItemExt.apply", text, f"{item_file} must register the GLB item renderer")


class GlbWinding(unittest.TestCase):
    """GLB materials are single-sided and Minecraft back-face culls; plate winding must face outward."""

    def test_prism_faces_outward_in_every_winding(self):
        from glb_builder import Mesh

        ccw = [(0.0, -1.0), (0.0, 1.0), (1.0, 1.0), (1.0, -1.0)]
        cw = list(reversed(ccw))
        for profile, tag in ((ccw, "ccw"), (cw, "cw")):
            for a, b in ((-0.5, 0.5), (0.5, -0.5)):
                mesh = Mesh()
                mesh.prism(0, profile, "x", a, b, 1.0)
                verts = mesh.groups[0]["p"]
                norms = mesh.groups[0]["n"]
                self.assertEqual(len(verts) % 9, 0)
                centre = ((a + b) / 2.0,
                          sum(point[0] for point in profile) / len(profile),
                          sum(point[1] for point in profile) / len(profile))
                for tri in range(len(verts) // 9):
                    points = [tuple(verts[tri * 9 + k * 3:tri * 9 + k * 3 + 3]) for k in range(3)]
                    normal = tuple(norms[tri * 9:tri * 9 + 3])
                    face = tuple(sum(point[axis] for point in points) / 3.0 for axis in range(3))
                    outward = tuple(face[axis] - centre[axis] for axis in range(3))
                    dot = sum(normal[axis] * outward[axis] for axis in range(3))
                    self.assertGreater(dot, 1e-9, f"{tag} a={a} b={b} triangle {tri} faces inward")

    def test_lathes_torus_and_raised_lugs_wind_outward(self):
        from build_equipment import _dot, _sub, add_torus, lathe_y, lathe_z
        from glb_builder import Mesh

        for axis in ("y", "z"):
            mesh = Mesh()
            if axis == "y":
                lathe_y(mesh, 0, 0.0, 0.0, [(-0.5, 0.25), (0.5, 0.25)], sides=12)
            else:
                lathe_z(mesh, 0, 0.0, 0.0, [(-0.5, 0.25), (0.5, 0.25)], sides=12)
            group = mesh.groups[0]
            for offset in range(0, len(group["p"]), 9):
                points = [tuple(group["p"][offset + k * 3:offset + k * 3 + 3]) for k in range(3)]
                center = tuple(sum(point[axis_i] for point in points) / 3.0 for axis_i in range(3))
                normal = tuple(group["n"][offset:offset + 3])
                if axis == "y" and abs(abs(center[1]) - 0.5) < 1e-7:
                    expected = (0.0, 1.0 if center[1] > 0 else -1.0, 0.0)
                elif axis == "z" and abs(abs(center[2]) - 0.5) < 1e-7:
                    expected = (0.0, 0.0, 1.0 if center[2] > 0 else -1.0)
                else:
                    expected = (center[0], 0.0, center[2]) if axis == "y" else (center[0], center[1], 0.0)
                self.assertGreater(_dot(normal, expected), 1e-8, f"{axis}-axis lathe triangle faces inward")

        torus = Mesh()
        add_torus(torus, 0, (0, 0, 0), (0, 0, 1), 0.5, 0.1, 24, 8)
        group = torus.groups[0]
        for offset in range(0, len(group["p"]), 9):
            points = [tuple(group["p"][offset + k * 3:offset + k * 3 + 3]) for k in range(3)]
            center = tuple(sum(point[axis_i] for point in points) / 3.0 for axis_i in range(3))
            radius = math.hypot(center[0], center[1])
            tube_center = (0.5 * center[0] / radius, 0.5 * center[1] / radius, 0.0)
            normal = tuple(group["n"][offset:offset + 3])
            self.assertGreater(_dot(normal, _sub(center, tube_center)), 1e-8,
                               "torus triangle faces into its tube")

        lug = Mesh()
        angle = 0.37
        radial = (math.cos(angle), 0.0, math.sin(angle))
        tangent = (-math.sin(angle), 0.0, math.cos(angle))
        lug.oriented_box(0, (radial[0] * 0.03, 0.0, radial[2] * 0.03),
                         (radial[0] * 0.001, 0, radial[2] * 0.001),
                         (0, 0.005, 0), (tangent[0] * 0.005, 0, tangent[2] * 0.005))
        group = lug.groups[0]
        for offset in range(0, len(group["p"]), 9):
            points = [tuple(group["p"][offset + k * 3:offset + k * 3 + 3]) for k in range(3)]
            center = tuple(sum(point[axis_i] for point in points) / 3.0 for axis_i in range(3))
            normal = tuple(group["n"][offset:offset + 3])
            outward = (center[0] - radial[0] * 0.03, center[1], center[2] - radial[2] * 0.03)
            self.assertGreater(_dot(normal, outward), 1e-8, "Mills-bomb raised lug faces inward")


if __name__ == "__main__":
    unittest.main()
