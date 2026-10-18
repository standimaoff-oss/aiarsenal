# World War I verification — 2026-10-17

## Build and gameplay checks

Alpha.15 source: `dada31ce2a46e241c3f6ba7f9b2c8fd66f6ea950`.
[Public build, regression tests and Minecraft capture](https://github.com/standimaoff-oss/aiarsenal/actions/runs/39051543629): successful.
The recorded regression outcome is `success`, not merely a green continue-on-error step; see `previews/verification.txt`.

Equipment tests cover fuse storage/clamping, empty/refuel consumption, grenade throw ownership/consumption,
and flame occlusion by a wall while consuming fuel. Offline resource tests cover models, texture references,
recipes and translations. These are targeted checks, not a guarantee of compatibility with every modpack.

## In-game visual review

Seven genuine Minecraft F2 screenshots were captured and saved in `previews/`.
The tank, rifle iron sights, grenade and active flame screenshots were personally inspected.
Alpha.15 still placed the rifle camera too near the receiver and produced flame particles too close to the camera.
Alpha.16 increases eye relief, opens the rear sight notch, extends the tank gun barrels and moves the flame
particle stream forward. Item faces now use explicit atlas-safe UVs. Its build and regression tests passed; seven new F2 screenshots were captured and personally inspected.
Rifle eye relief and the notch are visible, the longer tank barrel exits the sponson and the Wex nozzle points forward.
The flame-use animation still raises the bulky integrated fuel assembly; this is not a wearable-backpack implementation.

Alpha.16 source: `321ef847753e5bdd8c819bd43bcf6d846c899d15`.
[Build, tests and capture](https://github.com/standimaoff-oss/aiarsenal/actions/runs/39052885694): passed;
release creation failed with integration HTTP 403. The release was created with the authenticated CLI,
and [recovery run 39054140254](https://github.com/standimaoff-oss/aiarsenal/actions/runs/39054140254)
uploaded the exact tested artifact without rebuilding. Publication is now complete:
[alpha.16](https://github.com/standimaoff-oss/aiarsenal/releases/tag/v1.0.1-alpha.16), JAR 52,166,618 bytes,
seven screenshots and SHA256SUMS.
No offline render is used as gameplay evidence.

## Reproducible resources

`python tools/wwi/verify_assets.py --rebuild`: PASS for alpha.16 resources, including byte-identical regeneration,
GLB structure, textures, running gear, sponson yaw, collision, recipes and language entries.
`python tools/wwi/test_equipment_assets.py`: 2 tests PASS.
The Mark I has 90 generated walkable collision boxes. The Gewehr 98 has 1,320 triangles and two animation clips.

The baseline is standimaoff-oss/aimods `v1.0.1-alpha.12` (`0d9f75b2f298d0d043abaaaaf99fccffe0697924`).
Unchanged baseline GLBs are archived in `vendor/baseline-models.zip`; run `tools/restore_models.py` before Gradle.
The early private-run billing block was resolved by making this repository public. Alpha.13's inherited
ambiguous rifle recipe was caught by regression tests and corrected before alpha.15 passed.

## Scope

Gameplay and visuals are adapted to Minecraft, not a historical simulation. The Wex fuel assembly is part of the
held model, not a separately wearable backpack. Flames do not ignite blocks. This remains an alpha release.

## Mark I model rebuild (alpha.18)

The Mark I hull, sponsons, running gear and stowage were rebuilt from the same historical
references at a higher level of detail: `tools/wwi/build_mark_i.py` now emits 18,420 triangles
(was 9,252) with a driver's hood and vision slits, raised engine deck with radiator louvres,
manhole coamings and lids, exhaust stacks with rain caps, mud chutes and return-roller skirts,
sponson gun-port collars with mantlet rings, rivet rings and vision slits, escape doors,
an unditching beam chained across the tail, and a track frame with inspection hatch.
The khaki paint atlas (`textures/entity/mark_i/paint.png`) is regenerated with plate seams,
rust streaks and dirt so the flat panels read as armour plate in game.

`tools/wwi/glb_builder.py:Mesh.prism` gained winding safety: profiles of either orientation and
either extrusion direction now produce outward-facing side walls and end caps. The previous
implementation fanned both caps with the same vertex order, so one cap of every prism was
inverted and Minecraft back-face culled it (the shipped right-hand track frame rendered
inside-out). `tools/wwi/test_equipment_assets.py:GlbWinding` reproduces the old behaviour
(24 inward-facing triangles) and asserts the new one (0), for every profile orientation and
extrusion direction. Walkable collision was regenerated: 136 boxes at 0.20 m (was 90).

`tools/wwi/render_preview.py` renders any shipped GLB offline as a flat-shaded orthographic
PNG with the same back-face culling Minecraft applies, so geometry can be inspected without
a game client: `python tools/wwi/render_preview.py mark_i.glb --azim -32 --elev 14`.
These renders are development aids; in-game F2 captures remain the gameplay evidence.
`python tools/wwi/verify_assets.py --rebuild` passes byte-identical for the new resources.

## Sponson guns and release alpha.27

The in-game capture of alpha.18 showed the flaw the offline renderer could not: with the shared
aim line at the bow, both QF 6-pdrs lay along the hull side. Alpha.27 fixes the whole chain -
per-sponson traverse sectors (+/-35 deg about broadside, elevation -10..+20 deg), an aim line
that reaches into either sector (+/-125 deg), and barrels re-proportioned to the real sponson
depth (muzzle 1.15 m from the trunnion, 0.74 m proud of the armour face, was 2.23 m / 1.82 m).
Pose, ballistics, the barrel collider, reticle traces, muzzle blast, hit-cam X-ray and wreck
poses all derive from the same clamped angles, and
`WWITests.mark_i_sponsons_traverse_only_their_historical_sector` fires both sponsons in a
gametest and checks each projectile's flight direction against its own line.
Released as [v1.0.1-alpha.27](https://github.com/standimaoff-oss/aiarsenal/releases/tag/v1.0.1-alpha.27).
