#!/usr/bin/env python3
"""Procedural base-colour textures for the Tu-95MS / Tu-160 / FAB bombs models.
Everything is drawn by the agent (numpy + PIL) - no third-party assets.
Output: /home/user/.cache/air/tex/*.png  (packed into the .blend files and embedded in the GLBs)
Scale contract (used by the UV code in alib.py):
  skin tiles  : 1024 px == 4 m  (256 px/m)
  floor/quilt : 512 px == 1 m
"""
import os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = "/home/user/.cache/air/tex"
os.makedirs(OUT, exist_ok=True)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONTB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONTC = "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf"
if not os.path.exists(FONTC):
    FONTC = FONTB


def font(sz, bold=True, cond=False):
    return ImageFont.truetype(FONTC if cond else (FONTB if bold else FONT), sz)


def pnoise(h, w, sigma, seed, aspect=(1.0, 1.0)):
    r = np.random.default_rng(seed)
    white = r.standard_normal((h, w))
    fy = np.fft.fftfreq(h)[:, None] * aspect[0]
    fx = np.fft.fftfreq(w)[None, :] * aspect[1]
    filt = np.exp(-(fx ** 2 + fy ** 2) * (sigma ** 2) * 2 * np.pi ** 2)
    n = np.real(np.fft.ifft2(np.fft.fft2(white) * filt))
    n -= n.mean()
    return n / (np.abs(n).max() + 1e-9)


def fbm(h, w, s0, oct_, seed, aspect=(1.0, 1.0)):
    acc, amp, tot = 0, 1.0, 0
    for o in range(oct_):
        acc = acc + pnoise(h, w, s0 / (2 ** o), seed + o, aspect) * amp
        tot += amp
        amp *= 0.5
    return acc / tot


def save(arr, name, mode="RGB"):
    arr = np.clip(arr, 0, 255).astype(np.uint8)
    Image.fromarray(arr, mode).save(os.path.join(OUT, name), optimize=True)
    print("tex", name, arr.shape)


# ------------------------------------------------------------------------------------------------ skins
def skin(name, base, seam_dark, seam_light, rivet_dark, seed, tone_var, grime, flush=False):
    """1024 px = 4 m. Frames every 0.5 m along v (fuselage axis), stringer seams every ~1 m along u,
    panels with individual tone, rivet rows along every seam, airflow-oriented streaks."""
    S = 1024
    ppm = S / 4.0
    rng = np.random.default_rng(seed)
    img = np.zeros((S, S, 3), np.float32)
    img[:] = base
    # panel tone patches: panels 1.0 m (u) x 2.0 m (v) with a staggered layout
    for pu in range(4):
        for pv in range(2):
            off = (pu % 2) * 1.0
            v0 = int(((pv * 2.0 + off) % 4.0) * ppm)
            u0 = int(pu * ppm)
            t = 1.0 + rng.uniform(-tone_var, tone_var)
            tint = np.array([t, t, t * (1 + rng.uniform(-0.01, 0.01))], np.float32)
            rows = (np.arange(v0, v0 + int(2.0 * ppm)) % S)
            img[rows[:, None], np.arange(u0, u0 + int(ppm))[None, :]] *= tint
    # grime: large blotches + airflow streaks (stretched along v == rows)
    g = fbm(S, S, 60, 4, seed + 10) * 0.5 + pnoise(S, S, 18, seed + 20, aspect=(0.15, 1.0)) * 0.5
    img *= (1.0 - grime * (g[..., None] * 0.5 + 0.5) * 0.6)
    fine = pnoise(S, S, 1.2, seed + 30)
    img *= (1.0 + fine[..., None] * 0.025)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    # frames (rows) every 0.5 m, stringer seams (columns) every 1.0 m, plus a staggered panel break
    seams_v = [int(i * 0.5 * ppm) for i in range(8)]
    seams_u = [int(i * 1.0 * ppm) for i in range(4)]
    for v in seams_v:
        major = (v % int(ppm)) == 0
        w = 2 if major else 1
        if flush and not major:
            continue
        d.line([(0, v), (S, v)], fill=seam_dark, width=w)
        d.line([(0, v + w), (S, v + w)], fill=seam_light, width=1)
    for u in seams_u:
        d.line([(u, 0), (u, S)], fill=seam_dark, width=2)
        d.line([(u + 2, 0), (u + 2, S)], fill=seam_light, width=1)
    # rivets: rows along each seam, 25 mm pitch (~6 px); flush rivets are fainter
    pitch = 6
    for v in seams_v:
        for side in (-4, 5):
            for u in range(2, S, pitch):
                c = rivet_dark if not flush else tuple(int(x * 0.5 + b * 0.5) for x, b in zip(rivet_dark, base))
                d.point((u, (v + side) % S), fill=c)
                if not flush:
                    d.point((u + 1, (v + side + 1) % S), fill=seam_light)
    for u in seams_u:
        for side in (-4, 6):
            for v in range(2, S, pitch):
                c = rivet_dark if not flush else tuple(int(x * 0.5 + b * 0.5) for x, b in zip(rivet_dark, base))
                d.point(((u + side) % S, v), fill=c)
    # intermediate stringer rivet lines every 0.25 m (u) - only on unflush skins
    if not flush:
        for k in range(16):
            u = int(k * 0.25 * ppm) + 2
            if any(abs(u - s) < 8 for s in seams_u):
                continue
            for v in range(0, S, pitch * 2):
                d.point((u, v), fill=tuple(int(x * 0.35 + b * 0.65) for x, b in zip(rivet_dark, base)))
    # inspection hatches: small screwed panels
    for k in range(5):
        x0, y0 = rng.integers(20, S - 120), rng.integers(20, S - 120)
        wv, hv = rng.integers(40, 110), rng.integers(30, 80)
        d.rounded_rectangle([x0, y0, x0 + wv, y0 + hv], radius=6, outline=seam_dark, width=1)
        for sx in range(x0 + 5, x0 + wv - 2, 14):
            d.point((sx, y0 + 4), fill=rivet_dark); d.point((sx, y0 + hv - 4), fill=rivet_dark)
    im.save(os.path.join(OUT, name), optimize=True)
    print("tex", name)


skin("T_Skin95.png", (176, 180, 182), (92, 96, 98), (205, 208, 210), (120, 124, 126), 95, 0.035, 0.10)
skin("T_Skin160.png", (232, 233, 230), (168, 170, 170), (246, 246, 244), (196, 198, 198), 160, 0.018, 0.07, flush=True)


# ------------------------------------------------------------------------------------------------ dark metal / nozzles
def dark_metal():
    S = 512
    n = fbm(S, S, 30, 4, 7)
    stripes = pnoise(S, S, 6, 9, aspect=(1.0, 0.08))
    base = np.array([62, 60, 58], np.float32)
    img = base * (1 + n[..., None] * 0.18 + stripes[..., None] * 0.08)
    # heat tint (titanium straw/blue) bands across v
    v = np.linspace(0, 1, S)[:, None]
    heat = np.clip(np.sin(v * np.pi * 3) * 0.5 + 0.5, 0, 1)
    img[..., 0] += heat * 18
    img[..., 2] += (1 - heat) * 14
    save(img, "T_DarkMetal.png")


dark_metal()


def tire():
    S = 256
    img = np.full((S, S, 3), 34, np.float32)
    img *= (1 + fbm(S, S, 8, 3, 3)[..., None] * 0.15)
    im = Image.fromarray(img.astype(np.uint8))
    d = ImageDraw.Draw(im)
    for k in range(0, S, 32):  # circumferential grooves (aircraft tyres: straight ribs)
        d.rectangle([k + 12, 0, k + 18, S], fill=(18, 18, 18))
    im.save(os.path.join(OUT, "T_Tire.png"))


tire()


# ------------------------------------------------------------------------------------------------ interior
def quilt(name, col, seed):
    """Quilted insulation pads (cabin walls): 512 px = 1 m, diamond stitching, buttons."""
    S = 512
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    cell = 64.0
    a = ((xx + yy) % cell) / cell
    b = ((xx - yy) % cell) / cell
    puff = (np.sin(a * np.pi) * np.sin(b * np.pi)) ** 0.6
    img = np.array(col, np.float32) * (0.72 + 0.28 * puff[..., None])
    img *= (1 + fbm(S, S, 10, 3, seed)[..., None] * 0.06)
    stitch = (np.minimum(a, 1 - a) < 0.025) | (np.minimum(b, 1 - b) < 0.025)
    img[stitch] *= 0.7
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    # pad seams (each pad 0.5 m)
    for k in (0, 256):
        d.line([(k, 0), (k, S)], fill=tuple(int(c * 0.45) for c in col), width=3)
        d.line([(0, k), (S, k)], fill=tuple(int(c * 0.45) for c in col), width=3)
    im.save(os.path.join(OUT, name))
    print("tex", name)


quilt("T_Quilt95.png", (126, 140, 118), 11)   # olive-grey padded insulation (Tu-95)
quilt("T_Quilt160.png", (150, 156, 160), 12)  # light grey (Tu-160)


def floor():
    S = 512
    img = np.full((S, S, 3), 70, np.float32)
    img *= (1 + fbm(S, S, 20, 4, 21)[..., None] * 0.12)
    # wear: lighter, polished in the middle
    yy, xx = np.mgrid[0:S, 0:S]
    img *= (1 + 0.10 * np.exp(-((xx - S / 2) / 120.0) ** 2)[..., None])
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    for y in range(8, S, 32):  # tread pattern (raised diamonds)
        for x in range(8 + (y // 32 % 2) * 16, S, 32):
            d.line([(x - 6, y - 3), (x + 6, y + 3)], fill=(102, 102, 100), width=3)
    d.rectangle([0, 0, S - 1, S - 1], outline=(40, 40, 40), width=3)
    for p in [(10, 10), (S - 11, 10), (10, S - 11), (S - 11, S - 11), (S // 2, 10), (S // 2, S - 11)]:
        d.ellipse([p[0] - 4, p[1] - 4, p[0] + 4, p[1] + 4], fill=(120, 120, 118), outline=(30, 30, 30))
    im.save(os.path.join(OUT, "T_Floor.png"))
    print("tex T_Floor.png")


floor()


def seat():
    S = 256
    img = np.array((92, 64, 44), np.float32) * np.ones((S, S, 3))
    img *= (1 + fbm(S, S, 6, 3, 31)[..., None] * 0.12)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    for k in range(0, S, 42):  # cushion tufts
        d.line([(0, k), (S, k)], fill=(52, 36, 26), width=3)
    im.save(os.path.join(OUT, "T_Seat.png"))


seat()


# ---------------------------------------------------------------------- instrument atlas (1024: 4 quadrants)
def gauge(d, cx, cy, r, rng, face=(16, 16, 16), label=None, kind="dial"):
    d.ellipse([cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3], fill=(30, 30, 30), outline=(8, 8, 8), width=2)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=face)
    if kind == "horizon":  # attitude indicator: sky / ground
        d.pieslice([cx - r, cy - r, cx + r, cy + r], 180, 360, fill=(60, 110, 170))
        d.pieslice([cx - r, cy - r, cx + r, cy + r], 0, 180, fill=(120, 76, 40))
        d.line([(cx - r * 0.6, cy), (cx + r * 0.6, cy)], fill=(250, 210, 40), width=3)
        d.line([(cx - r * 0.95, cy), (cx + r * 0.95, cy)], fill=(240, 240, 240), width=1)
        return
    n = 10 if r < 30 else 12
    for k in range(n * 5 + 1):
        ang = math.radians(225 - 270 * k / (n * 5))
        big = k % 5 == 0
        r0 = r * (0.78 if big else 0.86)
        d.line([(cx + math.cos(ang) * r0, cy - math.sin(ang) * r0), (cx + math.cos(ang) * r * 0.95, cy - math.sin(ang) * r * 0.95)],
               fill=(235, 235, 225), width=2 if big else 1)
    if r >= 22:
        f = font(max(8, int(r * 0.22)), bold=False)
        for k in range(0, n + 1, 2):
            ang = math.radians(225 - 270 * k / n)
            t = str(k)
            d.text((cx + math.cos(ang) * r * 0.6, cy - math.sin(ang) * r * 0.6), t, fill=(235, 235, 225), font=f, anchor="mm")
    a = math.radians(225 - 270 * rng.uniform(0.1, 0.9))
    d.line([(cx, cy), (cx + math.cos(a) * r * 0.85, cy - math.sin(a) * r * 0.85)], fill=(250, 250, 240), width=3)
    d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=(60, 60, 60))
    if label:
        d.text((cx, cy + r * 0.45), label, fill=(220, 200, 120), font=font(max(7, int(r * 0.18))), anchor="mm")


def switches(d, x0, y0, x1, y1, rng, col=(30, 30, 30)):
    for y in range(y0 + 10, y1 - 10, 28):
        for x in range(x0 + 10, x1 - 10, 18):
            d.rectangle([x - 4, y - 6, x + 4, y + 6], fill=(20, 20, 20))
            up = rng.random() > 0.4
            d.rectangle([x - 1, y - (9 if up else -1), x + 1, y + (1 if up else 9)], fill=(200, 200, 200))
        d.text((x0 + 6, y + 9), "".join(rng.choice(list("АБВГДЕЖЗИКЛМНОПРСТ")) for _ in range(6)),
               fill=(230, 230, 220), font=font(8, bold=False))


def atlas():
    S = 1024
    im = Image.new("RGB", (S, S), (40, 40, 40))
    d = ImageDraw.Draw(im)
    rng = np.random.default_rng(1952)
    # Q0 top-left: Tu-95 main panel (Soviet turquoise)
    d.rectangle([0, 0, 511, 511], fill=(74, 138, 132))
    labels = ["V км/ч", "H м", "Vy", "КУР", "ГМК", "ТВ", "об/мин", "Тг", "Pм", "Т", "Р", "%"]
    k = 0
    for row in range(4):
        for col in range(6):
            cx, cy = 45 + col * 84, 60 + row * 112
            kind = "horizon" if (row == 1 and col in (1, 4)) else "dial"
            gauge(d, cx, cy, 34 if row < 2 else 28, rng, label=labels[k % len(labels)], kind=kind)
            k += 1
    d.rectangle([0, 470, 511, 511], fill=(50, 96, 92))
    switches(d, 0, 470, 511, 511, rng)
    # Q1 top-right: Tu-160 main panel (grey-blue) with two CRTs
    d.rectangle([512, 0, 1023, 511], fill=(86, 98, 108))
    for i, cx in enumerate((640, 896)):
        d.rectangle([cx - 70, 30, cx + 70, 150], fill=(10, 14, 10), outline=(20, 20, 20), width=6)
        for j in range(7):
            d.line([(cx - 60, 50 + j * 14), (cx + 60 - rng.integers(0, 60), 50 + j * 14)], fill=(70, 220, 90), width=2)
        d.ellipse([cx - 22, 100, cx + 22, 144], outline=(70, 220, 90), width=2)
    for row in range(3):
        for col in range(6):
            cx, cy = 555 + col * 84, 210 + row * 100
            gauge(d, cx, cy, 32 if row == 0 else 26, rng, label=labels[(row * 6 + col) % len(labels)],
                  kind="horizon" if (row == 0 and col in (1, 4)) else "dial")
    switches(d, 512, 450, 1023, 511, rng)
    # Q2 bottom-left: overhead / side consoles (switch fields, breakers, warning lamps)
    d.rectangle([0, 512, 511, 1023], fill=(64, 72, 76))
    switches(d, 0, 512, 511, 860, rng)
    for x in range(14, 500, 24):  # circuit breakers
        for y in (880, 910, 940):
            d.ellipse([x - 6, y - 6, x + 6, y + 6], fill=(15, 15, 15))
            d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(230, 230, 230))
    for i, c in enumerate([(220, 40, 30), (230, 180, 30), (60, 200, 70), (220, 40, 30), (230, 180, 30), (60, 200, 70)]):
        d.rectangle([20 + i * 80, 970, 80 + i * 80, 1005], fill=tuple(int(x * 0.6) for x in c), outline=(10, 10, 10), width=2)
        d.text((50 + i * 80, 987), ["ПОЖАР", "ШАССИ", "ГОТОВ", "ОТКАЗ", "ТОПЛ", "ЛЮКИ"][i], fill=(250, 250, 240), font=font(11), anchor="mm")
    # Q3 bottom-right: navigator: radar scope + sight panel
    d.rectangle([512, 512, 1023, 1023], fill=(70, 80, 70))
    d.ellipse([560, 560, 840, 840], fill=(8, 22, 10), outline=(20, 20, 20), width=10)
    for rr in (40, 80, 120):
        d.ellipse([700 - rr, 700 - rr, 700 + rr, 700 + rr], outline=(40, 140, 60), width=1)
    d.pieslice([580, 580, 820, 820], 260, 300, fill=(60, 200, 90))
    for _ in range(40):
        x, y = rng.integers(600, 800), rng.integers(600, 800)
        if (x - 700) ** 2 + (y - 700) ** 2 < 118 ** 2:
            d.point((x, y), fill=(120, 255, 140))
    for row in range(2):
        for col in range(2):
            gauge(d, 900 + col * 70, 600 + row * 100, 28, rng, label=["ДИСС", "Т", "Курс", "ВБ"][row * 2 + col])
    switches(d, 512, 860, 1023, 1023, rng)
    im.save(os.path.join(OUT, "T_Panels.png"))
    print("tex T_Panels.png")


atlas()


# ------------------------------------------------------------------------------------------------ decals (RGBA)
def star(d, cx, cy, r, fill, outline=None, ow=0):
    pts = []
    for k in range(10):
        ang = math.radians(90 + k * 36)
        rr = r if k % 2 == 0 else r * 0.382
        pts.append((cx + math.cos(ang) * rr, cy - math.sin(ang) * rr))
    if outline:
        # Russian AF star: red star, white border, thin red outline
        big = []
        for k in range(10):
            ang = math.radians(90 + k * 36)
            rr = (r + ow) if k % 2 == 0 else (r + ow) * 0.382
            big.append((cx + math.cos(ang) * rr, cy - math.sin(ang) * rr))
        d.polygon(big, fill=outline)
    d.polygon(pts, fill=fill)


DECAL_RECTS = {}


def uvr(box, S=1024, pad=6):
    x0, y0, x1, y1 = box
    return [max(0, x0 - pad) / S, 1 - min(S, y1 + pad) / S, min(S, x1 + pad) / S, 1 - max(0, y0 - pad) / S]


def decals(name, number, aircraft_name, num_col, outline_col):
    S = 1024
    R = DECAL_RECTS.setdefault(name, {})
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # star (top-left 512x512): red / white / red
    star(d, 256, 270, 250, (200, 16, 24, 255))
    star(d, 256, 270, 205, (245, 245, 245, 255))
    star(d, 256, 270, 175, (200, 16, 24, 255))
    # flag (top-right 512x256)
    d.rectangle([540, 40, 1000, 110], fill=(245, 245, 245, 255))
    d.rectangle([540, 110, 1000, 180], fill=(0, 57, 166, 255))
    d.rectangle([540, 180, 1000, 250], fill=(213, 43, 30, 255))
    # tail number (right, rows 256..512)
    f = font(230, cond=True)
    d.text((770, 400), number, font=f, fill=num_col, anchor="mm", stroke_width=10, stroke_fill=outline_col)
    R["number"] = uvr(d.textbbox((770, 400), number, font=f, anchor="mm", stroke_width=10))
    R["star"] = uvr((6, 20, 506, 520))
    R["flag"] = uvr((540, 40, 1000, 250), pad=0)
    # aircraft name (bottom half rows 560..680)
    f2 = font(86, cond=True)
    d.text((512, 620), aircraft_name, font=f2, fill=(200, 16, 24, 255), anchor="mm")
    R["name"] = uvr(d.textbbox((512, 620), aircraft_name, font=f2, anchor="mm"))
    # stencils (rows 720..1024): warnings, walkway, "НЕ СТУПАТЬ", towing, earthing
    f3 = font(30, bold=True)
    for i, t in enumerate(["НЕ СТУПАТЬ", "ОСТОРОЖНО ВИНТ", "ЗАЗЕМЛЕНИЕ", "ТОЧКА ПОДЪЁМА", "ВВС РОССИИ"]):
        d.text((30, 730 + i * 56), t, font=f3, fill=(30, 30, 30, 255))
        R["st%d" % i] = uvr(d.textbbox((30, 730 + i * 56), t, font=f3), pad=3)
    R["prop_stripe"] = uvr((600, 730, 990, 760), pad=0)
    R["walk"] = uvr((600, 800, 990, 812), pad=0)
    d.rectangle([600, 730, 990, 760], fill=(200, 16, 24, 255))  # red prop warning stripe
    d.rectangle([600, 800, 990, 812], fill=(30, 30, 30, 255))   # walkway line
    d.text((795, 880), "ТУ", font=font(90, cond=True), fill=(30, 30, 30, 255), anchor="mm")
    im.save(os.path.join(OUT, name))
    print("tex", name)


decals("T_Decals95.png", "21", "САРАТОВ", (200, 16, 24, 255), (245, 245, 245, 255))
decals("T_Decals160.png", "12", "ВАЛЕРИЙ ЧКАЛОВ", (200, 16, 24, 255), (30, 30, 30, 255))


# ------------------------------------------------------------------------------------------------ bombs
def bomb():
    """512x512: rows 0..340 painted body (UV u = around, v = along) with stencil bands for 3 types;
    rows 340..512: plain paint for fins/ring."""
    S = 512
    base = np.array([98, 104, 92], np.float32)  # Soviet bomb grey-green
    img = base * (1 + fbm(S, S, 16, 4, 41)[..., None] * 0.10)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    for i, (t, sub) in enumerate([("ФАБ-250 М-54", "ТГАФ-5  1987"), ("ФАБ-500 М-54", "ТГАФ-5  1985"), ("ФАБ-1500 М-54", "ТГАФ-5  1979")]):
        y0 = 10 + i * 110
        d.rectangle([0, y0, S, y0 + 12], fill=(30, 30, 30))          # black band = HE filler marking
        d.text((S // 2, y0 + 48), t, font=font(34, cond=True), fill=(18, 18, 18), anchor="mm")
        d.text((S // 2, y0 + 86), sub, font=font(22, bold=False), fill=(18, 18, 18), anchor="mm")
    d.rectangle([0, 340, S, S], fill=(92, 98, 86))
    im.save(os.path.join(OUT, "T_Bomb.png"))
    print("tex T_Bomb.png")


bomb()
import json
json.dump(DECAL_RECTS, open(os.path.join(OUT, "decal_rects.json"), "w"), indent=1)
print("done")
