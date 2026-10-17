#!/usr/bin/env python3
"""Procedural soft smoke-puff sprites (8 frames, 32x32, white RGBA -> tinted in game). No external art."""
import json, os
import numpy as np
from PIL import Image
OUT = "/home/user/mods/bm21grad/src/main/resources/assets/bm21grad"
os.makedirs(OUT + "/textures/particle", exist_ok=True)
os.makedirs(OUT + "/particles", exist_ok=True)
rng = np.random.default_rng(2112)
N = 32
yy, xx = np.mgrid[0:N, 0:N]
def fbm(seed, octaves=4):
    r = np.random.default_rng(seed)
    acc = np.zeros((N, N))
    amp = 1.0
    for o in range(octaves):
        n = 2 ** (o + 2)
        g = r.random((n + 1, n + 1))
        img = Image.fromarray((g * 255).astype(np.uint8)).resize((N, N), Image.BICUBIC)
        acc += amp * (np.asarray(img) / 255.0)
        amp *= 0.5
    return acc / acc.max()
frames = 8
for f in range(frames):
    t = f / (frames - 1)
    # several overlapping lobes -> cauliflower silhouette; frames grow more ragged / thinner
    a = np.zeros((N, N))
    lobes = rng.integers(4, 7)
    for _ in range(lobes):
        cx, cy = N / 2 + rng.normal(0, 3.2), N / 2 + rng.normal(0, 3.2)
        rad = rng.uniform(6.5, 9.5) * (1 + 0.12 * t)
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / rad
        a = np.maximum(a, np.clip(1 - d ** 2, 0, 1))
    n = fbm(100 + f)
    a = np.clip(a * (0.55 + 0.6 * n) - 0.08 * t, 0, 1) ** (1.1 + 0.5 * t)
    edge = np.sqrt((xx - N / 2 + 0.5) ** 2 + (yy - N / 2 + 0.5) ** 2) / (N / 2)
    a *= np.clip((1 - edge) * 3, 0, 1)
    shade = np.clip(0.78 + 0.22 * (1 - (yy / N)) + 0.1 * (n - 0.5), 0, 1)  # lit from above
    rgb = (shade * 255).astype(np.uint8)
    img = np.dstack([rgb, rgb, rgb, (a * 255).astype(np.uint8)])
    Image.fromarray(img, "RGBA").save(f"{OUT}/textures/particle/puff_{f}.png")
tex = [f"bm21grad:puff_{f}" for f in range(frames)]
for name in ("smoke_wall", "fireball", "blast_smoke"):
    json.dump({"textures": tex}, open(f"{OUT}/particles/{name}.json", "w"), indent=2)
# preview sheet
sheet = Image.new("RGBA", (N * frames * 4, N * 4), (40, 90, 160, 255))
for f in range(frames):
    im = Image.open(f"{OUT}/textures/particle/puff_{f}.png").resize((N * 4, N * 4), Image.NEAREST)
    sheet.alpha_composite(im, (f * N * 4, 0))
sheet.save("/home/user/shots/tmp/puffs.png")
print("ok")
