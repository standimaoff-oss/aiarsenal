#!/usr/bin/env python3
"""r26 textures for the WW2 small arms (PIL, procedural, no external images):
   birch (PPSh-41 / Mosin 91/30 stocks, shellac reddish), walnut (Kar98k), bakelite (MP 40 grip / fore panel),
   blued steel, copper bullet jackets, parkerized clip steel.   python3 tools/blender/tex_r26.py"""
import os, random, math
from PIL import Image, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tex_r22")
random.seed(26)


def wood(name, base, dark, n=256, waves=5, knots=2):
    """straight-grained stock wood: many thin streaks of varying tone (1-D noise across the grain),
    only a gentle warp along the grain, sparse pores; tileable in x and y"""
    im = Image.new("RGB", (n, n))
    px = im.load()
    comps = [(random.randint(4, 40), random.uniform(0.2, 1.0), random.uniform(0, 6.28)) for _ in range(14)]
    norm = sum(c[1] for c in comps)
    ph = [random.uniform(0, 6.28) for _ in range(4)]
    for y in range(n):
        for x in range(n):
            w = y + 1.6 * math.sin(x / n * 6.283 + ph[0]) + 0.8 * math.sin(x / n * 6.283 * 2 + ph[1])
            v = sum(a * math.sin(w / n * 6.283 * k + p) for k, a, p in comps) / norm
            s = 0.5 + 0.5 * v
            s = 0.25 + 0.75 * s ** 1.6
            f = s + random.gauss(0, 0.035)
            px[x, y] = tuple(int(max(0, min(255, b + (d - b) * f))) for b, d in zip(base, dark))
    for _ in range(n * 3):                                  # pores: short dark dashes along the grain
        x, y, L = random.randrange(n), random.randrange(n), random.randint(2, 6)
        for i in range(L):
            c = px[(x + i) % n, y]
            px[(x + i) % n, y] = tuple(int(v * 0.85) for v in c)
    for _ in range(knots):
        cx, cy, r = random.randrange(n), random.randrange(n), random.randint(3, 5)
        for y in range(cy - r * 2, cy + r * 2):
            for x in range(cx - r * 3, cx + r * 3):
                dd = ((x - cx) / 3.0) ** 2 + ((y - cy) / 1.5) ** 2
                if dd < r * r:
                    xx, yy = x % n, y % n
                    c = px[xx, yy]
                    k = 1 - dd / (r * r)
                    px[xx, yy] = tuple(int(v + (dk - v) * k * 0.5) for v, dk in zip(c, dark))
    im.save(os.path.join(OUT, name + ".png"))


def metal(name, base, noise=6, streak=0.0, n=128, spots=0, spot_col=None):
    im = Image.new("RGB", (n, n))
    px = im.load()
    for y in range(n):
        row = random.gauss(0, streak)
        for x in range(n):
            d = random.gauss(0, noise) + row
            px[x, y] = tuple(max(0, min(255, int(c + d))) for c in base)
    for _ in range(spots):                          # holster / handling wear: bright edges
        x, y, r = random.randrange(n), random.randrange(n), random.randint(1, 3)
        for yy in range(y - r, y + r):
            for xx in range(x - r * 2, x + r * 2):
                px[xx % n, yy % n] = spot_col
    im.filter(ImageFilter.SMOOTH).save(os.path.join(OUT, name + ".png"))


wood("T_Birch", (150, 82, 44), (96, 46, 22), waves=6)          # Soviet birch, shellac finish (reddish)
wood("T_Walnut", (110, 70, 42), (58, 34, 18), waves=4, knots=1)  # German walnut / laminated Kar98k
metal("T_Blued", (40, 42, 48), 5, 2.0, spots=14, spot_col=(92, 94, 98))
metal("T_Bakelite", (52, 34, 26), 4, 1.0)                      # MP 40 phenolic (dark brown)
metal("T_Copper", (176, 104, 62), 6)                           # bullet jackets (gilding metal)
metal("T_ClipSteel", (92, 92, 88), 6, 1.5)
print("TEX_R26_DONE")
