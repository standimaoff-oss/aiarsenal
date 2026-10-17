#!/usr/bin/env python3
"""Procedural textures for the fortification blocks (numpy + PIL, drawn by the agent, no third-party assets).
Output: mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/block/fort_*.png  (tileable, power of two)
"""
import os
import numpy as np
from PIL import Image

OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/block"
os.makedirs(OUT, exist_ok=True)


def tnoise(S, cells, seed):
    """Tileable value noise in [-1, 1]."""
    r = np.random.default_rng(seed)
    g = r.uniform(-1, 1, (cells, cells))
    y, x = np.mgrid[0:S, 0:S] / S * cells
    x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int)
    fx = x - x0; fy = y - y0
    fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
    x1 = (x0 + 1) % cells; y1 = (y0 + 1) % cells
    x0 %= cells; y0 %= cells
    a = g[y0, x0] * (1 - fx) + g[y0, x1] * fx
    b = g[y1, x0] * (1 - fx) + g[y1, x1] * fx
    return a * (1 - fy) + b * fy


def fbm(S, base, oct, seed):
    v = np.zeros((S, S)); amp = 1.0; tot = 0
    for i in range(oct):
        c = base * (2 ** i)
        if c > S: break
        v += tnoise(S, c, seed + i * 17) * amp; tot += amp; amp *= 0.5
    return v / tot


def save(img, name, alpha=None):
    img = np.clip(img, 0, 255).astype(np.uint8)
    if alpha is not None:
        a = np.clip(alpha, 0, 255).astype(np.uint8)
        Image.fromarray(np.dstack([img, a]), "RGBA").save(os.path.join(OUT, name))
    else:
        Image.fromarray(img, "RGB").save(os.path.join(OUT, name))
    print("wrote", name)


def burlap(name, base, seed):
    """Woven jute: warp/weft threads, fibre noise, dirt."""
    S = 64
    y, x = np.mgrid[0:S, 0:S]
    warp = (np.sin(x * np.pi / 2.0) * 0.5 + 0.5)
    weft = (np.sin(y * np.pi / 2.0) * 0.5 + 0.5)
    over = ((x // 2 + y // 2) % 2 == 0)
    weave = np.where(over, warp, weft) * 0.22 - 0.11
    fib = fbm(S, 8, 3, seed) * 0.10
    dirt = np.clip(fbm(S, 2, 3, seed + 3), 0, 1) * 0.25
    k = 1 + weave + fib - dirt
    img = np.array(base, np.float32)[None, None, :] * k[..., None]
    # faint printed seam band
    pass
    save(img, name)


burlap("fort_sandbag.png", (178, 158, 114), 11)
burlap("fort_sandbag_olive.png", (128, 124, 86), 12)


def concrete(name, base, seed, stain=True):
    S = 64
    n = fbm(S, 4, 4, seed)
    pores = tnoise(S, 32, seed + 5)
    img = np.array(base, np.float32)[None, None, :] * (1 + n[..., None] * 0.10)
    img[pores > 0.78] *= 0.72
    img[pores < -0.85] *= 1.08
    if stain:
        y = np.mgrid[0:S, 0:S][0]
        streak = np.clip(tnoise(S, 16, seed + 9), 0, 1) * (y / S) * 0.18
        img *= (1 - streak[..., None])
    save(img, name)


concrete("fort_concrete.png", (156, 154, 148), 21)
concrete("fort_concrete_dark.png", (122, 120, 114), 22)


def steel(name, seed, rust=0.5):
    S = 64
    base = np.array((70, 66, 62), np.float32)
    rustc = np.array((122, 66, 38), np.float32)
    n = fbm(S, 4, 4, seed)
    m = np.clip((fbm(S, 2, 4, seed + 1) + rust * 0.6) * 1.6, 0, 1)[..., None]
    img = base * (1 - m) + rustc * m
    img *= (1 + n[..., None] * 0.15)
    pits = tnoise(S, 32, seed + 4) > 0.8
    img[pits] *= 0.7
    save(img, name)


steel("fort_steel.png", 31, 0.35)
steel("fort_wire.png", 32, -0.2)


def wood(name, seed):
    S = 32
    y, x = np.mgrid[0:S, 0:S]
    rings = np.sin((x + tnoise(S, 4, seed) * 3) * 1.3) * 0.5 + 0.5
    img = np.array((112, 86, 58), np.float32)[None, None, :] * (0.85 + rings[..., None] * 0.2 + fbm(S, 4, 3, seed + 1)[..., None] * 0.08)
    save(img, name)


wood("fort_wood.png", 41)


def camo_net(name, seed):
    """Military camouflage net: diamond knotted mesh + cut garnish patches (woodland), alpha holes."""
    S = 128
    y, x = np.mgrid[0:S, 0:S]
    # diamond mesh (cords 2 px wide)
    a = (x + y) % 16
    b = (x - y) % 16
    cord = (a < 2) | (b < 2)
    # garnish patches
    n1 = fbm(S, 4, 4, seed)
    n2 = fbm(S, 4, 4, seed + 7)
    garn = n1 > -0.05
    cols = np.array([(64, 76, 44), (92, 98, 56), (58, 50, 36), (34, 40, 28)], np.float32)
    idx = np.digitize(n2, [-0.25, 0.0, 0.25])
    img = cols[idx]
    img *= (1 + tnoise(S, 32, seed + 3)[..., None] * 0.12)
    cordc = np.array((70, 66, 50), np.float32)
    img[cord & ~garn] = cordc
    alpha = np.where(garn | cord, 255, 0).astype(np.float32)
    # leaf-cut holes inside garnish
    holes = (tnoise(S, 16, seed + 11) > 0.55) & ~cord
    alpha[holes] = 0
    save(img, name, alpha)


camo_net("fort_camo_net.png", 51)


def hazard(name):
    """Weathered yellow/black reflector stripe for the Jersey barrier."""
    S = 32
    y, x = np.mgrid[0:S, 0:S]
    stripe = ((x + y) // 8) % 2 == 0
    img = np.where(stripe[..., None], np.array((206, 168, 40), np.float32), np.array((34, 32, 30), np.float32))
    img = img * (1 + fbm(S, 4, 3, 61)[..., None] * 0.15)
    save(img, name)


hazard("fort_hazard.png")
