#!/usr/bin/env python3
"""Procedural WW2 aircraft paint schemes (r11) - drawn with numpy + PIL by the agent, no third-party assets.
Output: /home/user/.cache/air/tex/T_Pl*.png (+ decals appended to T_DecalsWW2 by ww2_textures.py).
Scale: 512 px = 4 m (same UV convention as the loft/surface helpers: u, v in metres / 4).
Colours: RLM 70/71/65 (He 111 1941-42), RLM 74/75/76 (Bf 109 G-6 1943-44), VVS AMT-11/12/7 (Yak-3 1944),
VVS AMT-4/6 night-bomber scheme (Pe-8, black undersides)."""
import ast, os
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
_src = open(os.path.join(HERE, "air_textures.py"), encoding="utf-8").read()
_tree = ast.parse(_src)
_keep = [n for n in _tree.body if isinstance(n, (ast.Import, ast.ImportFrom, ast.FunctionDef))
         or (isinstance(n, ast.Assign) and all(isinstance(t, ast.Name) and t.id.isupper() for t in n.targets))]
exec(compile(ast.Module(body=_keep, type_ignores=[]), "air_textures_defs", "exec"), globals())
os.makedirs(OUT, exist_ok=True)
S = 512
PPM = S / 4.0

RLM70, RLM71, RLM65 = (42, 52, 40), (64, 76, 52), (132, 158, 172)
RLM74, RLM75, RLM76, RLM02 = (72, 78, 82), (98, 96, 106), (168, 182, 190), (112, 112, 98)
AMT11, AMT12, AMT7 = (104, 118, 128), (62, 68, 76), (148, 176, 192)
AMT4, AMT6 = (72, 84, 46), (26, 28, 26)


def seams(img, seed, dark=0.80, light=1.05, frame=0.5, stringer=1.0, rivets=True):
    """Soft panel seams (low contrast - no mipmaps in game, so no hard periodic lines)."""
    rng = np.random.default_rng(seed)
    out = img.copy()
    for k in range(int(4 / frame)):
        v = int(k * frame * PPM + rng.integers(-3, 4)) % S
        out[v, :] *= dark
        out[(v + 1) % S, :] *= light
    for k in range(int(4 / stringer)):
        u = int(k * stringer * PPM + rng.integers(-4, 5)) % S
        out[:, u] *= dark
    if rivets:
        for k in range(int(4 / frame)):
            v = int(k * frame * PPM + 3) % S
            out[v, ::5] *= 0.93
    # hatches
    im = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    for k in range(4):
        x0, y0 = rng.integers(10, S - 80), rng.integers(10, S - 80)
        w, h = rng.integers(26, 70), rng.integers(20, 50)
        c = tuple(int(x) for x in (out[y0, x0] * 0.82))
        d.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=4, outline=c, width=1)
    return np.asarray(im).astype(np.float32)


def weather(img, seed, grime=0.08, chips=0.03):
    n = fbm(S, S, 50, 4, seed)
    img *= (1 + n[..., None] * 0.06)
    st = pnoise(S, S, 2.0, seed + 3, aspect=(0.12, 1.0))       # airflow streaks along v
    img *= (1 - grime * np.clip(st, 0, 1)[..., None])
    c = fbm(S, S, 3, 2, seed + 5) > (1 - chips * 2.5)
    img[c] = img[c] * 0.6 + np.array([150, 150, 148]) * 0.4    # aluminium showing through
    return img


def splinter(name, a, b, seed, angle_pat=True, base_seams=True):
    """German hard-edged splinter: straight-edged polygons in two greens/greys (tiles seamlessly via wrap)."""
    rng = np.random.default_rng(seed)
    img = np.zeros((S, S, 3), np.float32)
    img[:] = a
    im = Image.fromarray(img.astype(np.uint8))
    d = ImageDraw.Draw(im)
    for k in range(7):
        cx, cy = rng.uniform(0, S), rng.uniform(0, S)
        pts = []
        n = rng.integers(4, 6)
        r0 = rng.uniform(70, 150)
        for j in range(n):
            t = 2 * np.pi * j / n + rng.uniform(-0.3, 0.3)
            r = r0 * rng.uniform(0.6, 1.3)
            pts.append((cx + np.cos(t) * r, cy + np.sin(t) * r * 1.4))
        for ox in (-S, 0, S):
            for oy in (-S, 0, S):
                d.polygon([(x + ox, y + oy) for x, y in pts], fill=b)
    img = np.asarray(im).astype(np.float32)
    img = seams(img, seed + 1) if base_seams else img
    img = weather(img, seed + 2)
    save(img, name)


def soft(name, base, tones, seed, scale=70, thr=(0.16, 0.30), stretch=1.6, mottle=False):
    """Soft-edged camouflage (VVS / late Luftwaffe): blurred blobs; mottle=True -> small dense spots."""
    img = np.zeros((S, S, 3), np.float32)
    img[:] = base
    for i, (col, t) in enumerate(zip(tones, thr)):
        if mottle:
            n = fbm(S, S, 9, 3, seed + 11 * i)
            m = np.clip((n - t) * 9, 0, 1)
        else:
            n = fbm(S, S, scale, 4, seed + 11 * i, aspect=(1.0, stretch))
            m = np.clip((n - t) * 14, 0, 1)
        img = img * (1 - m[..., None]) + np.array(col, np.float32) * m[..., None]
    img = seams(img, seed + 1)
    img = weather(img, seed + 2)
    save(img, name)


def flat(name, col, seed, grime=0.06):
    img = np.zeros((S, S, 3), np.float32)
    img[:] = col
    img = seams(img, seed + 1)
    img = weather(img, seed + 2, grime=grime)
    save(img, name)


def interior(name, col, seed):
    """Cockpit/fuselage interior: RLM 66 / Soviet grey-green primer, ribs every 0.5 m."""
    img = np.zeros((S, S, 3), np.float32)
    img[:] = col
    img *= (1 + fbm(S, S, 30, 3, seed)[..., None] * 0.08)
    for k in range(8):
        v = int(k * 0.5 * PPM)
        img[v:v + 5, :] *= 0.72
    save(img, name)


if __name__ == "__main__":
    splinter("T_PlRLM7071.png", RLM71, RLM70, 111)              # He 111 upper
    flat("T_PlRLM65.png", RLM65, 112)                           # He 111 under
    splinter("T_PlRLM7475.png", RLM75, RLM74, 109)              # Bf 109 upper (74/75 splinter)
    soft("T_PlRLM76M.png", RLM76, (RLM74, RLM02, RLM75), 1091, mottle=True, thr=(0.34, 0.40, 0.42))  # sides mottle
    flat("T_PlRLM76.png", RLM76, 1092)                          # Bf 109 under
    soft("T_PlAMT1112.png", AMT11, (AMT12,), 3, scale=80, thr=(0.10,))           # Yak-3 upper
    flat("T_PlAMT7.png", AMT7, 31)                              # Yak-3 under
    soft("T_PlAMT46.png", AMT4, (AMT6,), 8, scale=110, thr=(0.12,), stretch=1.3)  # Pe-8 upper
    flat("T_PlAMT6.png", AMT6, 81, grime=0.03)                  # Pe-8 black under
    interior("T_PlInteriorDE.png", (96, 100, 96), 7)            # RLM 66 schwarzgrau-ish (lightened)
    interior("T_PlInteriorSU.png", (120, 128, 112), 8)          # A-14 grey-green
    print("PLANE TEXTURES OK")
