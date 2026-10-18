# WWI handheld-equipment GLB pass

**Scope:** first asset pass for the Mills bomb No. 5 Mk I, German Stielhandgranate M1915, German Wechselapparat M.1917 (Wex), and the mod's generic flamethrower-fuel refill item. These are deterministic, metre-scale game meshes, not photogrammetry or manufacturing drawings.

## Historical references and modelling decisions

- **Mills bomb No. 5 Mk I:** [Imperial War Museums, object MUN 3207](https://www.iwm.org.uk/collections/item/object/30023646) identifies the oval cast-iron segmented body, fly-off lever, safety pin/pull-ring, and base plug; its recorded dimensions are 96 × 59 × 59 mm. The GLB is scaled to approximately 96 × 61 × 61 mm and builds the scored cast grid, top striker/lever/pin assembly, and threaded base plug as separate geometry.
- **Stielhandgranate M1915:** [Imperial War Museums, object MUN 2035](https://www.iwm.org.uk/collections/item/object/30020454) records a 340 mm long, 60 mm diameter wooden-handle/tin-cylinder grenade with a belt clip and a friction igniter pulled by a cord from the handle end. The GLB measures about 348 × 61 × 58 mm and includes the hollow-handle silhouette, cylindrical head, belt clip, bottom cap, cord, and pull bead.
- **Wechselapparat M.1917 (“Wex”):** [Imperial War Museums, object FIR 11297](https://www.iwm.org.uk/collections/item/object/30032290) documents the circular backpack reservoir, plated mounting straps, separate hose/nozzle, and component dimensions: 495 mm reservoir diameter, 55 mm × 1,100 mm hose, 1,240 mm nozzle, and 545 mm overall reservoir height. The model uses the circular fuel tank and central spherical pressure vessel described in [Nevington War Museum's portable-flamethrower reference](https://www.nevingtonwarmuseum.com/flame-throwing-equipment---portable.html); it also represents the feeder as a corrugated hose and keeps the lance, brass firing cock, igniter cage, reservoir and harness as distinct named GLB nodes. The assembled inventory representation is approximately 600 × 545 × 1,245 mm because it places the separate lance beside the backpack.
- **Flamethrower fuel:** `wwi_fuel` is a generic refill supply item rather than a claim to reproduce one identified WWI can. Its steel canister, rolled seams, filler closure, and forged bail are a game-readable approximation.

The palette textures are generated locally and embedded into each GLB. Their surface colours and wear are stylised; they are not traced from museum images.

## Build and verification

```sh
python3 tools/wwi/build_equipment.py
python3 tools/wwi/test_equipment_assets.py
python3 tools/wwi/verify_assets.py
```

The offline test checks GLB structure, required named parts, triangle budgets, metre-scale dimensions, item wrappers, recipes, translations, and GLB-renderer hooks. `tools/wwi/requirements.txt` pins NumPy/SciPy/Pillow so the voxel collision-deck rebuild is reproducible as well. `render_preview.py` is a geometry-only inspection aid: it applies back-face culling, but it is **not** a Minecraft screenshot and does not validate Minecraft lighting or item transforms.

## Game verification still required

The asset pass is not complete until the Minecraft client shows the four new GLB-rendered items in inventory/hand and the WWI gametest passes. This checkout has no local Java runtime; the delivery workflow must provide those checks and fresh, unedited in-game F2 captures. Existing captures in `previews/` predate this pass and are not evidence for it.
