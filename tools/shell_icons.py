#!/usr/bin/env python3
"""Procedural 32x32 item icons for the reply-4 gun rounds (shaded brass case + projectile, diagonal)."""
from PIL import Image, ImageDraw
import math
OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/item/"
S = 256
def shade(c, f): return tuple(max(0, min(255, int(v * f))) for v in c) + (255,)
def cyl(d, x0, x1, r, col, cy, taper_to=None):
    # horizontal cylinder from x0 to x1 with vertical shading bands
    for x in range(int(x0), int(x1)):
        rr = r if taper_to is None else r + (taper_to - r) * (x - x0) / max(1, x1 - x0)
        for y in range(int(cy - rr), int(cy + rr) + 1):
            t = (y - (cy - rr)) / max(1, 2 * rr)
            f = 0.55 + 0.75 * math.sin(math.pi * min(1, max(0, t * 1.1)))
            d.point((x, y), shade(col, f))
def ogive(d, x0, L, r, col, cy):
    for x in range(int(x0), int(x0 + L)):
        k = min(1.0, max(0.0, (x - x0) / L))
        rr = r * math.sqrt(max(0.0, 1 - k ** 1.6))
        for y in range(int(cy - rr), int(cy + rr) + 1):
            t = (y - (cy - rr)) / max(1, 2 * rr)
            d.point((x, y), shade(col, 0.55 + 0.75 * math.sin(math.pi * min(1, max(0, t * 1.1)))))
BRASS, STEEL, OLIVE, BLACK, RED, TAN = (196, 150, 64), (150, 150, 155), (82, 92, 56), (40, 40, 40), (170, 40, 30), (180, 160, 110)
def round_icon(name, case_len, r, proj_len, proj_col, ap=False, n=2, band=(184, 115, 51), tip=None):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i in range(n):
        cy = S / 2 + (i - (n - 1) / 2) * (2 * r + 10)
        x = 14
        cyl(d, x, x + 10, r + 3, BRASS, cy)                     # rim
        cyl(d, x + 10, x + case_len, r, BRASS, cy)
        x += case_len
        if ap:
            cyl(d, x, x + 34, r * 0.95, (90, 90, 90), cy)        # sabot
            cyl(d, x + 34, x + proj_len - 22, r * 0.3, STEEL, cy)  # long-rod penetrator
            ogive(d, x + proj_len - 22, 22, r * 0.3, STEEL, cy)
            for k in range(4):                                     # fins
                d.polygon([(x + 36, cy - r * 0.3), (x + 52, cy - r * 0.3), (x + 40, cy - r * 0.9)], fill=shade(STEEL, 0.8))
                d.polygon([(x + 36, cy + r * 0.3), (x + 52, cy + r * 0.3), (x + 40, cy + r * 0.9)], fill=shade(STEEL, 0.6))
        else:
            cyl(d, x, x + 8, r + 1, band, cy)                     # driving band
            body = proj_len * 0.45
            cyl(d, x + 8, x + 8 + body, r, proj_col, cy)
            cyl(d, x + 18, x + 26, r + 0.5, (200, 190, 60), cy)   # HE marking band
            ogive(d, x + 8 + body, proj_len - body - 8, r, proj_col, cy)
            if tip:
                cyl(d, x + proj_len - 14, x + proj_len - 2, r * 0.25, tip, cy)
    im = im.rotate(35, resample=Image.BICUBIC, expand=False)
    bb = im.getbbox()
    im = im.crop(bb)
    w, h = im.size
    k = 30.0 / max(w, h)
    im = im.resize((max(1, int(w * k)), max(1, int(h * k))), Image.LANCZOS)
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    out.paste(im, ((32 - im.size[0]) // 2, (32 - im.size[1]) // 2), im)
    out.save(OUT + name + ".png")
    print(name, out.size)
round_icon("shells_125", 90, 16, 120, OLIVE, ap=False, n=1)
round_icon("shells_120", 95, 15, 130, BLACK, ap=True, n=1)
round_icon("shells_100", 70, 12, 80, OLIVE, n=2)
round_icon("shells_122", 50, 14, 105, OLIVE, n=2, tip=BLACK)
round_icon("shells_152", 0, 18, 140, OLIVE, n=1, tip=BLACK)
round_icon("shells_155", 0, 19, 150, TAN, n=1, tip=(220, 200, 60))
