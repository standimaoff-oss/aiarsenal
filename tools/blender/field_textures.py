#!/usr/bin/env python3
"""Procedural textures for the r12 field blocks (trenches, drums, mines, traps, warning signs).
Drawn by the agent with numpy + PIL (DejaVu font for sign lettering), no third-party images.
Output: src/main/resources/assets/aiarsenal/textures/block/field_*.png"""
import os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/block"
os.makedirs(OUT, exist_ok=True)
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SRC = open("/home/user/tools/blender/fort_textures.py").read()
exec(SRC[:SRC.index("def burlap")])          # tnoise, fbm, save (writes into OUT)


def rgb(c, n=None, k=1.0):
    S = n.shape[0]
    img = np.zeros((S, S, 3)) + np.array(c, float)
    if n is not None:
        img += n[..., None] * k
    return img


# ---------------------------------------------------------------- trench revetment boards (horizontal planks, 4 per block)
def planks():
    S = 64
    y, x = np.mgrid[0:S, 0:S]
    n = fbm(S, 4, 4, 11)
    grain = np.sin((y + 6 * fbm(S, 2, 3, 12)) * 1.9 + x * 0.05) * 6
    img = rgb((112, 88, 60), n, 18) + grain[..., None]
    board = (y // 16)
    shade = np.array([0, -10, 6, -4])[board]
    img += shade[..., None]
    gap = (y % 16) == 15
    img[gap] = (40, 32, 24)
    for b in range(4):                                 # nail heads near the stakes
        for nx in (7, 56):
            img[b * 16 + 7:b * 16 + 9, nx:nx + 2] = (70, 70, 72)
    dirt = np.clip(fbm(S, 3, 3, 13) * 2 - 0.4, 0, 1) * (y / S)
    img = img * (1 - dirt[..., None] * 0.45) + np.array((70, 55, 38)) * dirt[..., None] * 0.45
    save(img, "field_plank.png")


def tin():
    S = 64
    y, x = np.mgrid[0:S, 0:S]
    c = np.sin(x / S * 2 * math.pi * 8)                # 8 corrugations per block, light from the left
    img = rgb((138, 140, 136), fbm(S, 4, 4, 21), 10) + (c * 22)[..., None]
    rust = np.clip(fbm(S, 3, 4, 22) * 2.2 + (y / S) * 0.8 - 0.6, 0, 1)
    img = img * (1 - rust[..., None]) + np.array((122, 66, 34)) * rust[..., None] + (fbm(S, 8, 2, 23) * 10)[..., None]
    save(img, "field_tin.png")


def earth():
    S = 64
    n = fbm(S, 4, 5, 31)
    img = rgb((96, 72, 50), n, 22)
    r = np.random.default_rng(32)
    for _ in range(40):                                # pebbles / roots
        px, py = r.integers(0, S, 2)
        img[py, px] = (130, 120, 108) if r.random() < 0.6 else (60, 44, 30)
    save(img, "field_earth.png")


# ---------------------------------------------------------------- 200 l drums: side unwrap (u = angle, v = height)
def drum(name, base, stencil, seed):
    S = 128
    y, x = np.mgrid[0:S, 0:S]
    img = rgb(base, fbm(S, 4, 4, seed), 9)
    shade = 1.0 + 0.10 * np.cos(x / S * 2 * math.pi)        # baked soft cylinder light
    img *= shade[..., None]
    for yy in (S // 3, 2 * S // 3):                         # rolling hoops (bright top edge, dark bottom)
        img[yy - 2:yy, :] *= 1.18; img[yy:yy + 2, :] *= 0.72
    img[:3, :] *= 0.8; img[-3:, :] *= 0.8                   # chimes
    scr = np.clip(fbm(S, 6, 3, seed + 1) * 3 - 1.6, 0, 1)   # scratches to bare steel
    img = img * (1 - scr[..., None]) + np.array((110, 108, 100)) * scr[..., None]
    rust = np.clip(fbm(S, 3, 4, seed + 2) * 2 + (y / S) ** 3 * 1.2 - 0.9, 0, 1)
    img = img * (1 - rust[..., None] * 0.8) + np.array((112, 58, 30)) * rust[..., None] * 0.8
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(FB, 11)
    for cx in (S * 0.25, S * 0.75):                          # stencil twice around
        for i, line in enumerate(stencil):
            w = d.textlength(line, font=f)
            d.text((cx - w / 2, 50 + i * 17), line, fill=(232, 226, 200) if sum(base) < 330 else (30, 30, 30), font=f)
    a = np.array(im).astype(float)
    a = a * 0.93 + np.array(img) * 0.07
    save(a, name)


def drum_top():
    S = 64
    y, x = np.mgrid[0:S, 0:S]
    r = np.hypot(x - 31.5, y - 31.5) / 32
    img = rgb((92, 94, 88), fbm(S, 4, 3, 41), 10)
    img[(r > 0.88)] *= 0.75
    img[(r > 0.80) & (r < 0.86)] *= 1.2
    for cx, cy, rr in ((44, 22, 6), (20, 42, 4)):            # 2" and 3/4" bungs
        rb = np.hypot(x - cx, y - cy)
        img[rb < rr] = (150, 150, 140); img[(rb < rr) & (rb > rr - 1.5)] = (60, 60, 58)
    save(img, "field_drum_top.png")


# ---------------------------------------------------------------- mines / traps
def olive():
    S = 64
    img = rgb((78, 86, 52), fbm(S, 4, 4, 51), 12)
    chip = np.clip(fbm(S, 8, 2, 52) * 3 - 1.8, 0, 1)
    img = img * (1 - chip[..., None]) + np.array((60, 58, 50)) * chip[..., None]
    save(img, "field_olive.png")


def rubber():
    S = 64
    img = rgb((34, 34, 32), fbm(S, 8, 3, 61), 8)
    save(img, "field_rubber.png")


def rust_steel():
    S = 64
    n = fbm(S, 4, 5, 71)
    img = rgb((96, 64, 42), n, 30)
    bare = np.clip(fbm(S, 6, 3, 72) * 2.5 - 1.0, 0, 1)
    img = img * (1 - bare[..., None]) + np.array((92, 92, 92)) * bare[..., None]
    save(img, "field_rust.png")


def mon50():
    S = 64
    im = Image.new("RGB", (S, S), (80, 88, 56))
    a = np.array(im).astype(float) + fbm(S, 4, 3, 81)[..., None] * 10
    im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(FB, 11)
    d.text((6, 8), "МОН-50", fill=(40, 44, 28), font=f)
    d.text((5, 7), "МОН-50", fill=(108, 116, 78), font=f)
    f2 = ImageFont.truetype(FB, 9)
    d.text((6, 34), "К ПРОТ.", fill=(36, 40, 26), font=f2)
    d.polygon([(30, 54), (24, 46), (36, 46)], fill=(40, 44, 28))
    save(np.array(im), "field_mon50.png")


# ---------------------------------------------------------------- warning signs (128 px, front face)
def sign(name, bg, border, draw_fn, lines, fg=(20, 20, 20), size=15):
    S = 128
    im = Image.new("RGB", (S, S), bg)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=border, width=6)
    draw_fn(d, S)
    f = ImageFont.truetype(FB, size)
    for i, ln in enumerate(lines):
        w = d.textlength(ln, font=f)
        d.text(((S - w) / 2, 84 + i * (size + 2)), ln, fill=fg, font=f)
    a = np.array(im).astype(float)
    wear = np.clip(fbm(S, 4, 4, hash(name) % 997) * 2 - 0.7, 0, 1) * 0.35
    a = a * (1 - wear[..., None]) + np.array((150, 140, 120)) * wear[..., None]
    a += fbm(S, 16, 2, 7)[..., None] * 6
    for _ in range(4):                                       # rivets
        pass
    for (rx, ry) in ((10, 10), (S - 11, 10), (10, S - 11), (S - 11, S - 11)):
        a[ry - 2:ry + 2, rx - 2:rx + 2] = (110, 110, 110)
    save(a, name)


def tri(d, S, fill, edge, cy=44, r=40):
    pts = [(S / 2, cy - r * 0.9), (S / 2 - r, cy + r * 0.8), (S / 2 + r, cy + r * 0.8)]
    d.polygon(pts, fill=edge)
    k = 0.72
    pts2 = [(S / 2, cy - r * 0.9 * k + 4), (S / 2 - r * k, cy + r * 0.8 * k), (S / 2 + r * k, cy + r * 0.8 * k)]
    d.polygon(pts2, fill=fill)


def skull(d, cx, cy, s, c):
    d.ellipse([cx - 10 * s, cy - 11 * s, cx + 10 * s, cy + 7 * s], fill=c)
    d.rectangle([cx - 6 * s, cy + 4 * s, cx + 6 * s, cy + 11 * s], fill=c)
    for ex in (-5, 5):
        d.ellipse([cx + ex * s - 3.5 * s, cy - 4 * s, cx + ex * s + 3.5 * s, cy + 3 * s], fill=(255, 255, 255))
    d.polygon([(cx, cy + 3 * s), (cx - 2 * s, cy + 6 * s), (cx + 2 * s, cy + 6 * s)], fill=(255, 255, 255))
    for bx in (-1, 1):
        d.line([(cx - 14 * s, cy + 13 * s * (1 if bx > 0 else 0.3)), (cx + 14 * s, cy + 13 * s * (0.3 if bx > 0 else 1))], fill=c, width=int(3 * s))


def trefoil(d, cx, cy, R, c):
    for k in range(3):
        a0 = -90 + k * 120 - 30
        d.pieslice([cx - R, cy - R, cx + R, cy + R], a0, a0 + 60, fill=c)
    d.ellipse([cx - R * 0.32, cy - R * 0.32, cx + R * 0.32, cy + R * 0.32], fill=(250, 210, 30))
    d.ellipse([cx - R * 0.2, cy - R * 0.2, cx + R * 0.2, cy + R * 0.2], fill=c)


def bio(d, cx, cy, R, c, bg):
    for k in range(3):
        a = math.radians(-90 + k * 120)
        ox, oy = cx + math.cos(a) * R * 0.45, cy + math.sin(a) * R * 0.45
        d.ellipse([ox - R * 0.55, oy - R * 0.55, ox + R * 0.55, oy + R * 0.55], outline=c, width=7)
    d.ellipse([cx - R * 0.25, cy - R * 0.25, cx + R * 0.25, cy + R * 0.25], outline=c, width=5)


def make_signs():
    RED, YEL, WHT, BLK = (196, 30, 30), (250, 210, 30), (238, 236, 228), (20, 20, 20)
    sign("field_sign_mines.png", WHT, RED, lambda d, S: (tri(d, S, WHT, RED), skull(d, S / 2, 52, 1.05, BLK)), ["ОСТОРОЖНО!", "МИНЫ"], fg=RED, size=13)
    sign("field_sign_radiation.png", YEL, BLK, lambda d, S: trefoil(d, S / 2, 46, 34, BLK), ["РАДИАЦИОННАЯ", "ОПАСНОСТЬ"], size=10)
    sign("field_sign_voltage.png", WHT, BLK, lambda d, S: (tri(d, S, YEL, BLK),
         d.polygon([(66, 18), (52, 50), (62, 50), (56, 74), (76, 42), (65, 42), (72, 18)], fill=BLK)), ["ВЫСОКОЕ", "НАПРЯЖЕНИЕ"], size=14)
    sign("field_sign_biohazard.png", YEL, BLK, lambda d, S: bio(d, S / 2, 48, 34, BLK, YEL), ["БИОЛОГИЧЕСКАЯ", "ОПАСНОСТЬ"], size=11)

    def stop(d, S):
        pts = [(S / 2 + 38 * math.cos(math.radians(22.5 + 45 * k)), 44 + 38 * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]
        d.polygon(pts, fill=RED, outline=WHT)
        f = ImageFont.truetype(FB, 22)
        w = d.textlength("СТОП", font=f)
        d.text((S / 2 - w / 2, 31), "СТОП", fill=WHT, font=f)
    sign("field_sign_checkpoint.png", WHT, BLK, stop, ["КПП", "ПРОПУСК!"], size=14)

    def noentry(d, S):
        d.ellipse([S / 2 - 38, 6, S / 2 + 38, 82], fill=RED)
        d.rectangle([S / 2 - 28, 38, S / 2 + 28, 50], fill=WHT)
    sign("field_sign_noentry.png", WHT, RED, noentry, ["ПРОХОД", "ЗАПРЕЩЁН"], fg=RED, size=14)


planks(); tin(); earth()
drum("field_drum_olive.png", (74, 84, 50), ["ДТ", "200 л"], 101)
drum("field_drum_red.png", (150, 34, 28), ["БЕНЗИН", "АИ-92"], 111)
drum("field_drum_blue.png", (40, 70, 128), ["МАСЛО", "М-10Г2"], 121)
drum_top(); olive(); rubber(); rust_steel(); mon50(); make_signs()
