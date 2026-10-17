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
