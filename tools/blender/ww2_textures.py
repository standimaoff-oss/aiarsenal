#!/usr/bin/env python3
"""Procedural WW2 armour textures (drawn by the agent with numpy + PIL - no third-party assets).
Output: /home/user/.cache/air/tex/  T_WW2*.png + T_DecalsWW2.png + ww2_rects.json
Scale: 512 px = 4 m (same as T_CamoGreen) unless noted.
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

S = 512
DUNKELGELB = (176, 156, 104)     # RAL 7028
OLIVGRUEN = (72, 82, 52)         # RAL 6003
ROTBRAUN = (98, 62, 44)          # RAL 8017
DUNKELGRAU = (62, 66, 68)        # RAL 7021
SOV_4BO = (74, 88, 52)           # Soviet 4BO protective green


def wear(img, seed, chips=0.10, dust=0.10, streaks=0.06):
    """Paint chips (dark steel showing), dust towards the bottom of the tile, vertical rain streaks."""
    h, w = img.shape[:2]
    n = fbm(h, w, 6, 3, seed)
    chip = n > (1.0 - chips * 2.2)
    img[chip] = img[chip] * 0.45 + np.array([40, 36, 32]) * 0.55
    yy = np.linspace(0, 1, h)[:, None, None]
    img[:] = img * (1 - dust * yy) + np.array([150, 132, 100]) * dust * yy
    st = pnoise(h, w, 1.5, seed + 3, aspect=(18.0, 1.0))
    img *= (1 - streaks * np.clip(st, 0, 1)[..., None])
    img *= (1 + fbm(h, w, 3, 2, seed + 9)[..., None] * 0.07)
    return img


def three_tone(name, seed, scale=60, base=DUNKELGELB, tones=(OLIVGRUEN, ROTBRAUN), stretch=2.2, size=S):
    """German 1943-45 three-tone: hard-edged stretched bands of olive green and red-brown over dark yellow."""
    img = np.zeros((size, size, 3), np.float32)
    img[:] = base
    n1 = fbm(size, size, scale, 4, seed, aspect=(1.0, stretch))
    n2 = fbm(size, size, scale * 0.9, 4, seed + 7, aspect=(1.0, stretch))
    img[n1 > 0.18] = tones[0]
    img[(n2 > 0.24) & (n1 <= 0.18)] = tones[1]
    img = wear(img, seed + 11, chips=0.06, dust=0.14, streaks=0.05)
    save(img, name)


def plain(name, col, seed, chips=0.06, dust=0.12):
    img = np.zeros((S, S, 3), np.float32)
    img[:] = col
    img *= (1 + fbm(S, S, 40, 3, seed)[..., None] * 0.08)
    img = wear(img, seed + 1, chips=chips, dust=dust)
    save(img, name)


def zimmerit(name, seed):
    """Anti-magnetic paste: horizontal comb-ridged waffle (ridges every ~4 cm = 5 px at 512px/4m) over three-tone."""
    img = np.zeros((S, S, 3), np.float32)
    img[:] = DUNKELGELB
    n1 = fbm(S, S, 60, 4, seed, aspect=(1.0, 2.4))
    n2 = fbm(S, S, 54, 4, seed + 7, aspect=(1.0, 2.4))
    img[n1 > 0.2] = OLIVGRUEN
    img[(n2 > 0.26) & (n1 <= 0.2)] = ROTBRAUN
    y = np.arange(S)[:, None]
    x = np.arange(S)[None, :]
    wob = np.sin(x / 9.0 + np.sin(y / 23.0) * 2.0) * 1.2
    ridge = 0.5 + 0.5 * np.sin((y + wob) * 2 * math.pi / 5.0)
    cells = ((x // 28 + y // 28) % 2) * 0.08          # applied in ~22 cm squares
    shade = 0.82 + 0.22 * ridge + cells
    img *= shade[..., None]
    img = wear(img, seed + 13, chips=0.09, dust=0.16, streaks=0.04)
    save(img, name)


three_tone("T_WW2Gelb.png", 2101)
three_tone("T_WW2Ratte.png", 2102, scale=150, stretch=2.8)       # 35-m hull: much bigger blotches
plain("T_WW2Grau.png", DUNKELGRAU, 2103, chips=0.05, dust=0.16)
plain("T_WW2GelbPlain.png", DUNKELGELB, 2104, chips=0.08, dust=0.12)
zimmerit("T_WW2Zimmerit.png", 2105)
plain("T_WW2Green.png", SOV_4BO, 2106, chips=0.10, dust=0.15)
plain("T_WW2GreenTruck.png", (70, 80, 50), 2107, chips=0.05, dust=0.18)

# ------------------------------------------------------------------------------------------- decal atlas (RGBA)
D = 1024
R = {}
im = Image.new("RGBA", (D, D), (0, 0, 0, 0))
d = ImageDraw.Draw(im)


def rect(key, box, pad=2):
    x0, y0, x1, y1 = box
    R[key] = [max(0, x0 - pad) / D, 1 - min(D, y1 + pad) / D, min(D, x1 + pad) / D, 1 - max(0, y0 - pad) / D]


def text(key, xy, t, sz, fill, cond=True, bold=True, stroke=0, stroke_fill=None):
    f = font(sz, bold=bold, cond=cond)
    d.text(xy, t, font=f, fill=fill, anchor="lm", stroke_width=stroke, stroke_fill=stroke_fill)
    rect(key, d.textbbox(xy, t, font=f, anchor="lm", stroke_width=stroke))


def balkenkreuz(key, cx, cy, s, outline_only=False):
    """1942+ Balkenkreuz: black cross with white open 'hook' borders; outline_only = white-only late-war variant."""
    a, b = s * 0.5, s * 0.14
    white = (236, 236, 228, 255)
    black = (16, 16, 16, 255)
    d.rectangle([cx - a, cy - b * 1.9, cx + a, cy + b * 1.9], fill=white)
    d.rectangle([cx - b * 1.9, cy - a, cx + b * 1.9, cy + a], fill=white)
    if not outline_only:
        d.rectangle([cx - a + b * 0.9, cy - b, cx + a - b * 0.9, cy + b], fill=black)
        d.rectangle([cx - b, cy - a + b * 0.9, cx + b, cy + a - b * 0.9], fill=black)
    else:
        d.rectangle([cx - a + b * 0.5, cy - b * 1.4, cx + a - b * 0.5, cy + b * 1.4], fill=(0, 0, 0, 0))
        d.rectangle([cx - b * 1.4, cy - a + b * 0.5, cx + b * 1.4, cy + a - b * 0.5], fill=(0, 0, 0, 0))
        d.rectangle([cx - a + b * 0.9, cy - b, cx + a - b * 0.9, cy + b], fill=black)
        d.rectangle([cx - b, cy - a + b * 0.9, cx + b, cy + a - b * 0.9], fill=black)
    rect(key, (cx - a, cy - a, cx + a, cy + a))


def star(key, cx, cy, r, fill, edge=None):
    pts = []
    for k in range(10):
        ang = -math.pi / 2 + k * math.pi / 5
        rr = r if k % 2 == 0 else r * 0.40
        pts.append((cx + math.cos(ang) * rr, cy + math.sin(ang) * rr))
    if edge:
        big = [(cx + (x - cx) * 1.16, cy + (y - cy) * 1.16) for x, y in pts]
        d.polygon(big, fill=edge)
    d.polygon(pts, fill=fill)
    rect(key, (cx - r * 1.16, cy - r * 1.16, cx + r * 1.16, cy + r * 1.16))


balkenkreuz("cross", 90, 90, 160)
balkenkreuz("cross_late", 270, 90, 160, outline_only=True)
star("star_red", 450, 90, 72, (190, 24, 20, 255), edge=(236, 236, 228, 255))
star("star_white", 620, 90, 72, (236, 236, 228, 255))
WHITE = (236, 236, 228, 255)
RED = (176, 28, 22, 255)
BLACK = (14, 14, 14, 255)
CUR = [20, 230, 0]          # x, y-centre of the current row, row height


def put(key, t, sz, fill, stroke=0, stroke_fill=None):
    """auto-packed text decal: wraps to a new row when the line is full (no overlaps)."""
    f = font(sz, bold=True, cond=True)
    bb = d.textbbox((0, 0), t, font=f, anchor="lm", stroke_width=stroke)
    w, h = bb[2] - bb[0], bb[3] - bb[1]
    if CUR[0] + w > D - 16:
        CUR[0] = 20
        CUR[1] += CUR[2] // 2 + 24 + h // 2
        CUR[2] = 0
    text(key, (CUR[0] - bb[0], CUR[1]), t, sz, fill, stroke=stroke, stroke_fill=stroke_fill)
    CUR[0] += w + 28
    CUR[2] = max(CUR[2], h)


put("num_131", "131", 100, WHITE)                  # Tiger "131" (sPzAbt 504, Bovington)
put("num_712", "712", 100, RED, stroke=4, stroke_fill=WHITE)
put("num_401", "401", 100, BLACK, stroke=5, stroke_fill=WHITE)
put("num_II", "II", 100, WHITE)
put("num_r1", "R01", 90, WHITE)
put("ru_rodina", "ЗА РОДИНУ!", 80, WHITE)
put("ru_num_24", "24", 100, WHITE)
put("ru_stalin", "ЗА СТАЛИНА!", 80, WHITE)
put("ru_num_335", "335", 100, WHITE)
put("ru_berlin", "НА БЕРЛИН!", 80, WHITE)
put("ru_gvard", "ГВАРДИЯ", 76, WHITE)
put("ru_kv", "КВ-5", 90, WHITE)
put("de_maus", "MAUS", 80, WHITE)
put("de_ratte", "RATTE", 80, WHITE)
put("de_sturm", "STURM", 80, WHITE)
put("us_star", "U.S.A. W-4134", 60, WHITE)       # lend-lease Studebaker registration
put("ru_katyusha", "КАТЮША", 72, WHITE)
# ---- r11 aircraft markings
YEL = (238, 196, 20, 255)
put("lw_code_he", "1H+GK", 84, BLACK, stroke=4, stroke_fill=WHITE)     # KG 26 style fuselage code
put("lw_7", "7", 110, YEL, stroke=6, stroke_fill=BLACK)
put("lw_chev", "<<", 100, BLACK, stroke=5, stroke_fill=WHITE)
put("vvs_24", "24", 100, WHITE, stroke=6, stroke_fill=RED)
text("vvs_42", (730, 100), "42", 100, RED, stroke=6, stroke_fill=WHITE)
# hazard / tactical: white turret band, yellow divisional sign
d.rectangle([30, 940, 330, 980], fill=WHITE); rect("band_white", (30, 940, 330, 980))
d.rectangle([880, 30, 1010, 62], fill=YEL); rect("lw_band", (880, 30, 1010, 62))
d.ellipse([360, 920, 440, 1000], outline=(230, 200, 40, 255), width=10); d.line([400, 930, 400, 990], fill=(230, 200, 40, 255), width=10)
rect("div_yellow", (360, 920, 440, 1000))
im.save(os.path.join(OUT, "T_DecalsWW2.png"))
json.dump(R, open(os.path.join(OUT, "ww2_rects.json"), "w"), indent=0)
print("WW2 textures OK", len(R), "decals, last row y", CUR[1])
