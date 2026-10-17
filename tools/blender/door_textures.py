#!/usr/bin/env python3
"""r16 (building blocks part 3): procedural textures for doors, hatches, gates and camouflage.
Drawn by the agent with numpy + PIL - no third-party images. Output: textures/block/door_*.png, gate_*.png, camo_*.png
(64x64 tileable unless noted; nets / leaves / mesh / star carry alpha for cutout rendering)."""
import os, math
import numpy as np
from PIL import Image, ImageDraw

SRC = open("/home/user/tools/blender/fort_textures.py").read()
exec(SRC[:SRC.index("def burlap")])          # tnoise, fbm, save (writes into OUT)
rng = np.random.default_rng(1616)


def paint(name, rgb, seed, grain=10.0, wear=0.0, rust=0.0, S=64):
    n = fbm(S, 4, 4, seed)
    img = np.ones((S, S, 3)) * np.array(rgb, float)
    img += n[..., None] * grain
    img += tnoise(S, 32, seed + 3)[..., None] * grain * 0.35          # fine speckle
    if wear > 0:                                                       # scratches / chipped paint to bare steel
        im = Image.new("L", (S, S), 0)
        d = ImageDraw.Draw(im)
        r = np.random.default_rng(seed + 9)
        for _ in range(int(14 * wear)):
            x, y = r.uniform(0, S, 2)
            a = r.uniform(-0.6, 0.6) + (math.pi / 2 if r.random() < 0.3 else 0)
            L = r.uniform(3, 12)
            d.line([(x, y), (x + math.cos(a) * L, y + math.sin(a) * L)], fill=int(r.uniform(90, 200)), width=1)
        m = np.asarray(im, float)[..., None] / 255.0
        img = img * (1 - m) + np.array([128, 128, 124]) * m
    if rust > 0:                                                       # rust blooms
        rn = fbm(S, 6, 3, seed + 21)
        m = np.clip((rn - (0.55 - rust * 0.25)) * 5, 0, 1)[..., None]
        img = img * (1 - m) + (np.array([112, 62, 34]) + fbm(S, 16, 2, seed + 5)[..., None] * 25) * m
    save(img, name)


# ------------------------------------------------------------------ painted surfaces
paint("door_steel_olive.png", (88, 96, 62), 1, grain=9, wear=0.8)
paint("door_steel_grey.png", (104, 112, 104), 2, grain=8, wear=1.0, rust=0.35)
paint("door_lab_white.png", (214, 214, 206), 3, grain=5, wear=0.3)
paint("door_paint_red.png", (168, 30, 26), 4, grain=10, wear=0.6)
paint("door_dark_steel.png", (58, 60, 58), 5, grain=7, wear=0.5)

# army door: vertical planks painted green, chipped to grey wood
S = 64
img = np.zeros((S, S, 3))
x = np.arange(S)
for k in range(4):                                                     # 4 boards per block
    b = slice(k * 16, k * 16 + 16)
    img[:, b] = np.array([62, 92, 54]) + rng.uniform(-6, 6)
img += fbm(S, 4, 4, 33)[..., None] * 8
img += (np.sin(np.mgrid[0:S, 0:S][0] / S * 2 * math.pi * 9 + fbm(S, 4, 3, 34) * 6)[..., None] * 3)   # grain
for k in range(4):
    img[:, k * 16] *= 0.55                                             # board gaps
chip = np.clip((fbm(S, 8, 3, 35) - 0.42) * 6, 0, 1)[..., None]
img = img * (1 - chip) + np.array([120, 108, 88]) * chip
save(img, "door_wood_green.png")

# ------------------------------------------------------------------ chain-link mesh (128, alpha) - diamond weave
S = 128
im = Image.new("RGBA", (S * 4, S * 4), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
step = 16 * 4
for i in range(-S * 4, S * 8, step):
    d.line([(i, 0), (i + S * 4, S * 4)], fill=(150, 154, 150, 255), width=6)
    d.line([(i, S * 4), (i + S * 4, 0)], fill=(132, 136, 132, 255), width=6)
im = im.resize((S, S), Image.LANCZOS)
a = np.asarray(im).astype(float)
a[..., 3] = np.where(a[..., 3] > 90, 255, 0)
save(a[..., :3], "gate_mesh.png", a[..., 3])

# ------------------------------------------------------------------ red star with gold rim (alpha) for the swing gate
S = 64
im = Image.new("RGBA", (S * 4, S * 4), (0, 0, 0, 0))
d = ImageDraw.Draw(im)


def star(cx, cy, R, r):
    return [(cx + (R if i % 2 == 0 else r) * math.cos(math.radians(-90 + i * 36)),
             cy + (R if i % 2 == 0 else r) * math.sin(math.radians(-90 + i * 36))) for i in range(10)]


c = S * 2
d.polygon(star(c, c + 8, 120, 47), fill=(212, 170, 60, 255))
d.polygon(star(c, c + 8, 104, 40), fill=(190, 24, 22, 255))
for i in range(5):                                                     # facet shading of the 3D star
    a0 = math.radians(-90 + i * 72)
    d.polygon([(c, c + 8), (c + 104 * math.cos(a0), c + 8 + 104 * math.sin(a0)),
               (c + 40 * math.cos(a0 + math.radians(36)), c + 8 + 40 * math.sin(a0 + math.radians(36)))], fill=(150, 16, 16, 255))
im = im.resize((S, S), Image.LANCZOS)
a = np.asarray(im).astype(float)
a[..., 3] = np.where(a[..., 3] > 110, 255, 0)
save(a[..., :3], "gate_star.png", a[..., 3])

# ------------------------------------------------------------------ boom stripes: red / white bands every half block
S = 64
img = np.zeros((S, S, 3))
img[:, :32] = (196, 30, 28)
img[:, 32:] = (232, 232, 226)
img += fbm(S, 4, 3, 41)[..., None] * 6
img[:2] *= 0.8
img[-2:] *= 0.8
save(img, "gate_boom.png")


# ------------------------------------------------------------------ camouflage net (128, alpha): knotted cord + garland strips
def camo_net(name, cols, cord, seed, cover=0.68):
    S = 128
    r = np.random.default_rng(seed)
    rgb = np.zeros((S, S, 3))
    alpha = np.zeros((S, S))
    yy, xx = np.mgrid[0:S, 0:S]
    # cord diamond mesh, 10 cells per block
    u = ((xx + yy) % 13 < 1.6) | ((xx - yy) % 13 < 1.6)
    rgb[u] = cord
    alpha[u] = 255
    # garland: irregular leaf-cut strips (tileable noise thresholded) in 3-4 colours
    n = fbm(S, 4, 4, seed + 1) + 0.35 * fbm(S, 16, 2, seed + 2)
    thr = np.quantile(n, 1 - cover)
    m = n > thr
    sel = fbm(S, 4, 3, seed + 3)
    q = np.quantile(sel, [0.33, 0.66])
    col = np.where(sel[..., None] < q[0], cols[0], np.where(sel[..., None] < q[1], cols[1], cols[2]))
    rgb[m] = col[m]
    rgb += (fbm(S, 32, 2, seed + 4)[..., None] * 14) * m[..., None]
    # leaf cut edges: punch small holes into the garland
    holes = (tnoise(S, 32, seed + 5) > 0.55) & m
    alpha[m] = 255
    alpha[holes] = 0
    rgb[holes & u] = cord
    alpha[holes & u] = 255
    save(rgb, name, alpha)


camo_net("camo_net_forest.png", [(58, 78, 40), (88, 98, 52), (78, 64, 42)], (54, 60, 40), 51)
camo_net("camo_net_desert.png", [(186, 160, 112), (160, 132, 88), (204, 186, 140)], (120, 104, 72), 52)
camo_net("camo_net_winter.png", [(236, 238, 240), (210, 214, 218), (186, 190, 194)], (170, 172, 172), 53)


# ------------------------------------------------------------------ bush leaf cards (64, alpha)
def leaves(name, cols, seed, stem=(70, 56, 40)):
    S = 64
    im = Image.new("RGBA", (S * 4, S * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r = np.random.default_rng(seed)
    for _ in range(6):                                                 # twigs
        x0 = r.uniform(60, 196)
        d.line([(x0, 256), (x0 + r.uniform(-60, 60), r.uniform(30, 120))], fill=stem + (255,), width=6)
    for _ in range(150):                                               # leaves: rotated ellipses
        cx, cy = r.uniform(14, 242), r.uniform(10, 220)
        L, W = r.uniform(16, 30), r.uniform(7, 12)
        a = r.uniform(0, math.pi)
        c = cols[r.integers(len(cols))]
        sh = r.uniform(0.82, 1.1)
        pts = [(cx + math.cos(a) * L * math.cos(t) - math.sin(a) * W * math.sin(t),
                cy + math.sin(a) * L * math.cos(t) + math.cos(a) * W * math.sin(t)) for t in np.linspace(0, 2 * math.pi, 10)]
        d.polygon(pts, fill=tuple(int(min(255, v * sh)) for v in c) + (255,))
    im = im.resize((S, S), Image.LANCZOS)
    a = np.asarray(im).astype(float)
    a[..., 3] = np.where(a[..., 3] > 120, 255, 0)
    save(a[..., :3], name, a[..., 3])


leaves("camo_leaf_forest.png", [(52, 84, 36), (72, 104, 44), (40, 66, 30), (92, 112, 54)], 61)
leaves("camo_leaf_dry.png", [(150, 128, 70), (126, 110, 60), (170, 150, 92), (110, 96, 56)], 62, stem=(96, 78, 52))
leaves("camo_leaf_snow.png", [(240, 242, 246), (220, 226, 230), (64, 86, 52), (232, 236, 240)], 63)
print("DOOR_TEX_DONE")
