#!/usr/bin/env python3
"""Procedural textures for the AI Arsenal drones / missiles / nukes (drawn by the agent, numpy + PIL, no 3rd-party assets).
Re-uses the helper functions of air_textures.py (skin, pnoise, fbm, font, star) without re-running its outputs.
Output: /home/user/.cache/air/tex/  (T_Skin*.png, T_DecalsArs.png, ars_rects.json)
"""
import ast, os, math, json
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
_src = open(os.path.join(HERE, "air_textures.py"), encoding="utf-8").read()
_tree = ast.parse(_src)
_keep = [n for n in _tree.body if isinstance(n, (ast.Import, ast.ImportFrom, ast.FunctionDef))
         or (isinstance(n, ast.Assign) and all(isinstance(t, ast.Name) and t.id.isupper() for t in n.targets))
         or (isinstance(n, ast.If))]
exec(compile(ast.Module(body=_keep, type_ignores=[]), "air_textures_defs", "exec"), globals())

# ------------------------------------------------------------------------------------------------ skins (1024 px = 4 m)
skin("T_SkinReaper.png", (142, 146, 150), (104, 108, 112), (160, 164, 168), (120, 124, 128), 901, 0.015, 0.06, flush=True)
skin("T_SkinOrion.png", (196, 199, 201), (150, 153, 156), (214, 216, 218), (170, 173, 176), 902, 0.015, 0.06, flush=True)
skin("T_SkinShahed.png", (54, 56, 58), (30, 31, 32), (70, 72, 74), (40, 41, 42), 903, 0.05, 0.12, flush=True)
skin("T_SkinLiutyi.png", (112, 118, 104), (78, 82, 72), (130, 136, 120), (90, 94, 84), 904, 0.04, 0.10, flush=True)
skin("T_SkinMisDark.png", (78, 82, 84), (52, 55, 57), (96, 100, 102), (62, 65, 67), 905, 0.02, 0.08, flush=True)
skin("T_SkinMisLight.png", (188, 190, 188), (140, 142, 140), (205, 207, 205), (160, 162, 160), 906, 0.02, 0.06, flush=True)
skin("T_SkinNukeGreen.png", (74, 86, 60), (50, 58, 40), (92, 104, 76), (58, 68, 46), 907, 0.03, 0.10, flush=False)
skin("T_SkinNukeWhite.png", (214, 214, 206), (160, 160, 152), (232, 232, 226), (180, 180, 172), 908, 0.02, 0.07, flush=False)


def camo(name, cols, seed, scale=70):
    """Blotchy camouflage (canisters, launch trucks, Yars TEL): 512 px = 4 m."""
    S = 512
    n1 = fbm(S, S, scale, 4, seed)
    n2 = fbm(S, S, scale * 0.8, 4, seed + 5)
    img = np.zeros((S, S, 3), np.float32)
    img[:] = cols[0]
    img[n1 > 0.12] = cols[1]
    img[n2 > 0.22] = cols[2]
    img *= (1 + fbm(S, S, 3, 2, seed + 9)[..., None] * 0.06)
    save(img, name)


camo("T_CamoGreen.png", [(70, 84, 56), (52, 62, 42), (96, 100, 74)], 911)
camo("T_CamoSand.png", [(150, 138, 108), (120, 110, 84), (92, 96, 74)], 912)

# ------------------------------------------------------------------------------------------------ decal atlas (RGBA)
S = 1024
R = {}
im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)


def rect(key, box, pad=2):
    x0, y0, x1, y1 = box
    R[key] = [max(0, x0 - pad) / S, 1 - min(S, y1 + pad) / S, min(S, x1 + pad) / S, 1 - max(0, y0 - pad) / S]


def text(key, xy, t, sz, fill, cond=True, bold=True, stroke=0, stroke_fill=None):
    f = font(sz, bold=bold, cond=cond)
    d.text(xy, t, font=f, fill=fill, anchor="lm", stroke_width=stroke, stroke_fill=stroke_fill)
    rect(key, d.textbbox(xy, t, font=f, anchor="lm", stroke_width=stroke))


# row 0: Russian star (0..200), USAF low-vis roundel (210..520), radiation trefoils (530..1020)
star(d, 100, 100, 92, (200, 16, 24, 255), outline=(245, 245, 245, 255), ow=8)
star(d, 100, 100, 70, (200, 16, 24, 255))
rect("star_ru", (0, 0, 200, 200))
g = (70, 72, 76, 255)
d.rectangle([220, 80, 510, 120], fill=g)
d.ellipse([305, 40, 425, 160], fill=g)
star(d, 365, 101, 56, (142, 146, 150, 255))
rect("usaf", (215, 35, 515, 165))


def trefoil(cx, cy, r, fg, bg):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=bg)
    for k in range(3):
        a0 = -90 + k * 120 - 30
        d.pieslice([cx - r * 0.9, cy - r * 0.9, cx + r * 0.9, cy + r * 0.9], a0, a0 + 60, fill=fg)
    d.ellipse([cx - r * 0.24, cy - r * 0.24, cx + r * 0.24, cy + r * 0.24], fill=bg)
    d.ellipse([cx - r * 0.15, cy - r * 0.15, cx + r * 0.15, cy + r * 0.15], fill=fg)


trefoil(630, 100, 95, (20, 20, 20, 255), (250, 210, 20, 255))
rect("trefoil", (535, 5, 725, 195))
trefoil(830, 100, 95, (20, 20, 20, 255), (245, 245, 245, 255))
rect("trefoil_w", (735, 5, 925, 195))
# warhead / danger stripes (black-yellow) 930..1020
for k in range(8):
    d.polygon([(930 + k * 24, 200), (942 + k * 24, 200), (942 + k * 24 - 12, 0), (930 + k * 24 - 12, 0)], fill=(250, 200, 20, 255))
d.rectangle([930, 0, 1024, 200], outline=None)
for k in range(10):
    x = 930 + k * 18
    d.polygon([(x, 200), (x + 9, 200), (x + 9 + 40, 0), (x + 40, 0)], fill=(20, 20, 20, 255))
rect("hazard", (935, 5, 1020, 195), pad=0)

# stencils: shelf-packed below the emblem row (y >= 210), widths measured
WH = (240, 240, 240, 255)
BK = (22, 22, 22, 255)
GR = (70, 72, 76, 255)
items = [("geran2", "ГЕРАНЬ-2", 56, WH), ("geran1", "ГЕРАНЬ-1", 56, WH), ("geran3", "ГЕРАНЬ-3", 56, WH), ("liutyi", "ЛЮТИЙ", 56, BK),
         ("usaf_txt", "U.S. AIR FORCE", 48, GR), ("af_num", "AF 09-4057", 48, GR), ("ch", "CH", 110, GR),
         ("orion_num", "01", 110, (30, 60, 160, 255)), ("vks", "ВКС РОССИИ", 40, BK), ("kh101", "Х-101", 56, BK),
         ("kh55", "Х-55СМ", 56, BK), ("kalibr", "3М-14", 56, BK), ("tomahawk", "BGM-109", 52, BK), ("usn", "U.S. NAVY", 40, BK),
         ("tm62", "ТМ-62М", 40, BK), ("rds1", "РДС-1", 84, BK), ("an602", "АН602", 84, BK), ("yars", "ЯРС", 84, WH),
         ("pg7", "ПГ-7ВЛ", 40, BK), ("opasno", "ОПАСНО! ЯДЕРНЫЙ ЗАРЯД", 40, (200, 16, 24, 255)), ("ne_kantovat", "НЕ КАНТОВАТЬ", 34, BK),
         ("izd501", "ИЗДЕЛИЕ 501", 34, BK), ("izd602", "ИЗДЕЛИЕ 602", 34, BK), ("lift", "ТОЧКА ПОДЪЁМА", 28, BK),
         ("nostep", "НЕ СТУПАТЬ", 28, BK), ("nostep_en", "NO STEP", 28, GR), ("danger_en", "DANGER  PROPELLER", 28, GR),
         ("ser", "№ 0137", 34, WH), ("ser_k", "№ 0137", 34, BK)]
cx, cy, rowh = 10, 212, 0
for key, t, sz, col in items:
    f = font(sz, cond=True)
    x0, y0, x1, y1 = d.textbbox((0, 0), t, font=f, anchor="lt")
    w, h = x1 - x0 + 8, y1 - y0 + 8
    if cx + w > S - 4:
        cx, cy, rowh = 10, cy + rowh + 6, 0
    d.text((cx + 4 - x0, cy + 4 - y0), t, font=f, fill=col, anchor="lt")
    rect(key, (cx, cy, cx + w, cy + h), pad=0)
    cx += w + 8
    rowh = max(rowh, h)
assert cy + rowh < 836, cy + rowh
# Shahed white mid-body number band
d.rectangle([20, 840, 500, 880], fill=WH)
rect("band_w", (20, 840, 500, 880), pad=0)
d.rectangle([520, 840, 1000, 880], fill=(200, 16, 24, 255))
rect("band_r", (520, 840, 1000, 880), pad=0)
d.rectangle([20, 900, 500, 940], fill=(30, 60, 160, 255))
rect("band_b", (20, 900, 500, 940), pad=0)
d.rectangle([520, 900, 1000, 940], fill=BK)
rect("band_k", (520, 900, 1000, 940), pad=0)
# Orion / generic tail flags (Russian tricolour)
d.rectangle([20, 960, 200, 980], fill=(245, 245, 245, 255))
d.rectangle([20, 980, 200, 1000], fill=(0, 57, 166, 255))
d.rectangle([20, 1000, 200, 1020], fill=(213, 43, 30, 255))
rect("flag_ru", (20, 960, 200, 1020), pad=0)
im.save(os.path.join(OUT, "T_DecalsArs.png"))
json.dump(R, open(os.path.join(OUT, "ars_rects.json"), "w"), indent=1)
print("tex T_DecalsArs.png", len(R), "rects")
