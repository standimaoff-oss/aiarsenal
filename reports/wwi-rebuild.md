# WWI visual rebuild — 2026-10-17

Baseline: standimaoff-oss/aimods tag v1.0.1-alpha.12 (0d9f75b2f298d0d043abaaaaf99fccffe0697924).
The connected aiarsenal repository was empty except for its README. Imported the released mod sources and resources;
large unchanged GLBs are stored once as vendor/baseline-models.zip. Run tools/restore_models.py before Gradle.

## Changes
- Mark I: wider track shoes; fixed riveted track-frame panels instead of an open cage around the running gear.
  Existing entity IDs, pivots, track animation pitch and ammunition remain unchanged. Regenerated deck collision (109 boxes).
- Gewehr 98: lofted stock, separate wood and steel textures, smaller iron sights, barrel bands and cleaning rod.
  Preserved animation/node contracts. Reload animation shortened to 2.3 s to match the 46-tick server reload.
- GLB source library: per-material textures and shape/UV helpers, deterministic generation without Blender.
- HUD: manual bolt-action rifles are no longer labelled SEMI; Russian and English labels added.
- CI: distributable build and real Minecraft F2 capture, release only after successful capture.

## Verification
`python tools/wwi/verify_assets.py --rebuild`: PASS, including byte-identical regeneration,
GLB structure, texture identity, running gear, sponson yaw, collision, recipes and language entries.
This is an offline asset test, NOT a Java compile or Minecraft gameplay test.

## Delivery blocker
GitHub run https://github.com/standimaoff-oss/aiarsenal/actions/runs/39047120605
never started: "The job was not started because recent account payments have failed or your spending limit needs to be increased."
Local environment has no Java/Minecraft runtime and cannot access Maven/Forge/Mojang dependency hosts.
No new JAR or authentic gameplay screenshots were produced. Do not substitute the old alpha.12 release or offline
renders as evidence of this candidate. Historical fidelity and first-person framing still need in-game review.

## Public runner — follow-up
Repository is now public. Run 39050246486 built alpha.13 successfully. The test process failed: the inherited Gewehr 98 recipe also matched Kar98k.
The job step was marked successful by continue-on-error, not by a passing test suite. The recipe has now been separated.
Minecraft F2 capture succeeded; release publication failed and its diagnostics are being improved.
The former account-billing block no longer prevents this run. Alpha.14 equipment sources have additional dedicated
GameTests and offline item-resource checks; their runtime result must be recorded independently of alpha.13.
