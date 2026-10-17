#!/usr/bin/env python3
"""Procedural textures for the Bismarck (reply 10) - written with PIL, no downloaded imagery.

T_ShipHull.png  4096 x 512: hull side atlas, U = s / 251 m (stern -> bow), V = z / 20 m (keel at the bottom row).
               Red-brown antifouling below the 9.3 m waterline, 1.3 m dark-grey boot-top, light-grey sides
               (painted over for Rheinuebung, May 1941), plate seams, two porthole rows fore and aft of the citadel.
T_Teak.png      512 x 512 tile = 4 x 4 m: 0.2 m teak planks running fore-aft, caulking seams, butt joints.
T_ShipGrey.png  512 x 512 tile = 4 x 4 m: light grey paint with faint plating seams / rivet lines and grime noise.
T_SteelDeck.png 256 x 256 tile: dark grey steel deck plates (bow / stern patches, turret roofs).
"""
import os, random
from PIL import Image, ImageDraw, ImageFilter

TEXA = "/home/user/.cache/air/tex"
os.makedirs(TEXA, exist_ok=True)
rnd = random.Random(1941)


def noise(img, amp, seed=0, blur=0.0):
    r = random.Random(seed)
    w, h = img.size
    n = Image.new("L", (w // 4, h // 4))
    n.putdata([int(128 + r.uniform(-amp, amp)) for _ in range(n.size[0] * n.size[1])])
    n = n.resize((w, h), Image.BILINEAR)
    if blur:
        n = n.filter(ImageFilter.GaussianBlur(blur))
    px, npx = img.load(), n.load()
    for y in range(h):
        for x in range(w):
            k = (npx[x, y] - 128) / 255.0
            c = px[x, y]
            px[x, y] = tuple(max(0, min(255, int(v * (1 + k)))) for v in c[:3])
    return img


# ------------------------------------------------------------------------------------------------- hull atlas
W, H = 4096, 512
PXM_U, PXM_V = W / 251.0, H / 20.0
GREY, BOOT, RED = (172, 174, 178), (58, 60, 63), (112, 46, 30)
hull = Image.new("RGB", (W, H), GREY)
d = ImageDraw.Draw(hull)
zv = lambda z: int(H - z * PXM_V)
d.rectangle([0, zv(9.3), W, H], fill=RED)
d.rectangle([0, zv(10.6), W, zv(9.3)], fill=BOOT)
# plate seams: strakes every ~2.1 m in height, butts every ~8 m (staggered)
for z in (2.0, 4.1, 6.2, 8.3, 11.6, 13.4, 15.6, 17.4):
    col = (150, 152, 156) if z > 10.6 else ((92, 38, 24) if z < 9.3 else (48, 50, 52))
    d.line([0, zv(z), W, zv(z)], fill=col, width=1)
for i, z0 in enumerate((0.0, 2.0, 4.1, 6.2, 8.3, 10.6, 11.6, 13.4, 15.6, 17.4)):
    z1 = (2.0, 4.1, 6.2, 8.3, 10.6, 11.6, 13.4, 15.6, 17.4, 20.0)[i]
    off = (i % 2) * 4.0
    s = off
    while s < 251:
        col = (150, 152, 156) if z0 >= 10.6 else ((92, 38, 24) if z0 < 9.3 else (48, 50, 52))
        d.line([int(s * PXM_U), zv(z1), int(s * PXM_U), zv(z0)], fill=col, width=1)
        s += 8.0
# portholes (with eyebrow rigols) - two rows aft of Dora and forward of Anton, none along the armoured citadel
def porthole(s, z):
    x, y = int(s * PXM_U), zv(z)
    d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(40, 44, 48), outline=(120, 122, 126))
    d.arc([x - 5, y - 7, x + 5, y + 1], 200, 340, fill=(120, 122, 126))
for s0, s1, rows in ((2.5, 36.0, (14.6, 12.3)), (205.0, 244.0, (15.2, 12.3)), (36.0, 44.0, (14.6,))):
    for z in rows:
        s = s0
        while s < s1:
            porthole(s + rnd.uniform(-0.2, 0.2), z)
            s += 2.3 + rnd.uniform(0, 0.8)
# draft marks at bow / stern (white numerals as small ticks)
for s in (6.0, 243.5):
    for z in range(2, 11):
        d.rectangle([int(s * PXM_U) - 4, zv(z) - 2, int(s * PXM_U) + 4, zv(z) + 2], fill=(220, 220, 214))
noise(hull, 9, seed=3)
# streaks: rust / salt under the hawses and scuppers
sd = ImageDraw.Draw(hull)
for i in range(70):
    s = rnd.uniform(3, 248)
    z = rnd.uniform(12.5, 16.5)
    x = int(s * PXM_U)
    sd.line([x, zv(z), x + rnd.randint(-2, 2), zv(z - rnd.uniform(0.8, 2.5))], fill=(140, 132, 124), width=1)
hull.save(os.path.join(TEXA, "T_ShipHull.png"))

# ------------------------------------------------------------------------------------------------- teak deck
T = 512
teak = Image.new("RGB", (T, T), (176, 132, 86))
d = ImageDraw.Draw(teak)
pw = T / 20.0                     # 20 planks of 0.2 m across the 4 m tile
for i in range(20):
    x0 = int(i * pw)
    base = rnd.randint(-14, 14)
    col = (176 + base, 132 + base, 86 + base // 2)
    d.rectangle([x0, 0, int((i + 1) * pw) - 1, T], fill=col)
    # butt joints every ~6 m (staggered, wraps in the 4 m tile)
    y = rnd.randint(0, T)
    d.line([x0, y, int((i + 1) * pw) - 1, y], fill=(128, 96, 66), width=1)
    for k in range(6):
        gy = rnd.randint(0, T)
        d.line([x0 + 2, gy, int((i + 1) * pw) - 3, gy + rnd.randint(-1, 1)], fill=(col[0] - 12, col[1] - 10, col[2] - 8), width=1)
    # caulking (pitch): one soft seam - a hard 2 px black line every 0.2 m aliases into moire rings at range
    # (the GLB renderer has no mip-maps), so the seam is only ~35 % darker than the wood
    d.line([x0, 0, x0, T], fill=(112, 84, 58), width=1)
    d.line([x0 + 1, 0, x0 + 1, T], fill=(150, 113, 76), width=1)
noise(teak, 14, seed=5)
teak.save(os.path.join(TEXA, "T_Teak.png"))

# ------------------------------------------------------------------------------------------------- grey paint
g = Image.new("RGB", (T, T), (176, 178, 182))
d = ImageDraw.Draw(g)
for x in (0, 256):
    d.line([x, 0, x, T], fill=(150, 152, 157), width=1)
for y in (0, 170, 340):
    d.line([0, y, T, y], fill=(150, 152, 157), width=1)
for y in (0, 170, 340):
    for x in range(0, T, 8):
        d.point((x, y + 3), fill=(158, 160, 165))
noise(g, 12, seed=7, blur=1.0)
g.save(os.path.join(TEXA, "T_ShipGrey.png"))

# ------------------------------------------------------------------------------------------------- steel deck
S = 256
st = Image.new("RGB", (S, S), (82, 84, 88))
d = ImageDraw.Draw(st)
for x in (0, 128):
    d.line([x, 0, x, S], fill=(64, 66, 70), width=1)
for y in (0, 64, 128, 192):
    d.line([0, y, S, y], fill=(64, 66, 70), width=1)
noise(st, 16, seed=9)
st.save(os.path.join(TEXA, "T_SteelDeck.png"))
print("SHIP_TEX_OK")
