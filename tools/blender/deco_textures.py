#!/usr/bin/env python3
"""r14 (building blocks part 2): procedural textures for flags, lamps, searchlight and armour plates.
Drawn by the agent with numpy + PIL (DejaVu for the Victory Banner lettering) - no third-party images.
Output:
  textures/entity/flag/<id>.png  240x160 cloth textures (rendered by FlagRenderer)
  textures/item/flag_<id>.png    32x32 inventory icons
  textures/block/deco_*.png      block / OBJ textures (64x64)"""
import os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

RES = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/textures"
OUT = RES + "/block"
SRC = open("/home/user/tools/blender/fort_textures.py").read()
exec(SRC[:SRC.index("def burlap")])          # tnoise, fbm, save (writes into OUT)
os.makedirs(RES + "/entity/flag", exist_ok=True)
os.makedirs(RES + "/item", exist_ok=True)
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SS = 4                                       # supersampling for vector drawings

# ============================================================ vector helpers (in supersampled pixel space)
def star_pts(cx, cy, R, r=None, rot=-90, n=5):
    r = R * 0.382 if r is None else r
    pts = []
    for i in range(2 * n):
        a = math.radians(rot + i * 180 / n)
        rr = R if i % 2 == 0 else r
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def poly_rot(pts, cx, cy, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]


def canvas(w, h, col):
    im = Image.new("RGBA", (w * SS, h * SS), col)
    return im, ImageDraw.Draw(im)


def down(im, w, h):
    return im.resize((w, h), Image.LANCZOS)


def hammer_sickle(d, cx, cy, s, col):
    """Hammer and sickle after the 1980 USSR flag construction sheet, inscribed in a square of side s centred at
    (cx, cy).  Sickle: tapering blade on an arc (right side, tip at the middle of the upper edge, pointing left),
    handle down-left; hammer: head at the upper left, handle down to the lower right."""
    x0, y0 = cx - s / 2, cy - s / 2
    P = lambda u, v: (x0 + u * s, y0 + v * s)
    # --- sickle blade: centre line on a circle, width tapers from the heel to the tip
    C, R = (0.53, 0.45), 0.40
    th0, th1, n = 101.0, -97.0, 64
    outer, inner = [], []
    for i in range(n + 1):
        t = i / n
        th = math.radians(th0 + (th1 - th0) * t)
        w = 0.135 * (1 - t) ** 0.75 + 0.004
        ro, ri = R + w * 0.28, R - w * 0.72
        outer.append(P(C[0] + ro * math.cos(th), C[1] + ro * math.sin(th)))
        inner.append(P(C[0] + ri * math.cos(th), C[1] + ri * math.sin(th)))
    d.polygon(outer + inner[::-1], fill=col)
    # --- sickle handle (down-left from the heel), rounded end + ferrule
    hx, hy = C[0] + (R - 0.035) * math.cos(math.radians(th0)), C[1] + (R - 0.035) * math.sin(math.radians(th0))
    ang = math.radians(146)
    ux, uy = math.cos(ang), math.sin(ang)          # along the handle
    px, py = -uy, ux                               # across
    L, hw = 0.30, 0.050
    ex, ey = hx + ux * L, hy + uy * L
    d.polygon([P(hx + px * hw, hy + py * hw), P(ex + px * hw * 0.9, ey + py * hw * 0.9),
               P(ex - px * hw * 0.9, ey - py * hw * 0.9), P(hx - px * hw, hy - py * hw)], fill=col)
    r = hw * 0.9 * s
    ec = P(ex, ey)
    d.ellipse([ec[0] - r, ec[1] - r, ec[0] + r, ec[1] + r], fill=col)
    # --- hammer handle (lower right -> upper left)
    a0, a1 = (0.84, 0.88), (0.30, 0.34)
    dx, dy = a1[0] - a0[0], a1[1] - a0[1]
    ln = math.hypot(dx, dy)
    ux, uy = dx / ln, dy / ln
    px, py = -uy, ux
    hw = 0.046
    d.polygon([P(a0[0] + px * hw, a0[1] + py * hw), P(a1[0] + px * hw, a1[1] + py * hw),
               P(a1[0] - px * hw, a1[1] - py * hw), P(a0[0] - px * hw, a0[1] - py * hw)], fill=col)
    r = hw * s
    e0 = P(*a0)
    d.ellipse([e0[0] - r, e0[1] - r, e0[0] + r, e0[1] + r], fill=col)
    # --- hammer head: perpendicular to the handle; face (square) towards the upper right, tapered peen down-left
    hc = (a1[0] + ux * 0.035, a1[1] + uy * 0.035)
    face, peen, hh = 0.175, 0.17, 0.075
    pts = [(face, -hh), (face, hh), (-peen * 0.55, hh), (-peen, hh * 0.55), (-peen, -hh * 0.55), (-peen * 0.55, -hh)]
    head = []
    for (a, b) in pts:                             # a: along the head axis (= across the handle), b: along the handle
        head.append(P(hc[0] - px * a + ux * b, hc[1] - py * a + uy * b))
    d.polygon(head, fill=col)


# ============================================================ FLAGS (3:2)
FW, FH = 480, 320
W, H = FW * SS, FH * SS
FLAGS = {}


def flag(fn):
    FLAGS[fn.__name__[2:]] = fn
    return fn


def stripes_h(d, cols):
    n = len(cols)
    for i, c in enumerate(cols):
        d.rectangle([0, H * i / n, W, H * (i + 1) / n], fill=c)


def stripes_v(d, cols):
    n = len(cols)
    for i, c in enumerate(cols):
        d.rectangle([W * i / n, 0, W * (i + 1) / n, H], fill=c)


@flag
def f_russia(d):
    stripes_h(d, [(250, 250, 250), (0, 57, 166), (213, 43, 30)])


@flag
def f_ussr(d):
    d.rectangle([0, 0, W, H], fill=(204, 0, 0))
    g = (255, 215, 0)
    ax = H / 3                                      # common vertical axis of the star and the emblem
    hammer_sickle(d, ax, H * 0.19 + H / 8, H / 4, g)
    d.polygon(star_pts(ax, H * 0.10, H / 16), outline=g, width=max(2, int(H * 0.0075)))


@flag
def f_germany(d):
    stripes_h(d, [(0, 0, 0), (221, 0, 0), (255, 206, 0)])


@flag
def f_usa(d):
    red, white, blue = (178, 34, 52), (255, 255, 255), (60, 59, 110)
    for i in range(13):
        d.rectangle([0, H * i / 13, W, H * (i + 1) / 13], fill=red if i % 2 == 0 else white)
    cw, ch = W * 0.4, H * 7 / 13
    d.rectangle([0, 0, cw, ch], fill=blue)
    for row in range(9):
        n = 6 if row % 2 == 0 else 5
        for k in range(n):
            x = cw * ((2 * k + (1 if row % 2 == 0 else 2)) / 12)
            y = ch * (row + 1) / 10
            d.polygon(star_pts(x, y, ch * 0.038), fill=white)


@flag
def f_uk(d):
    blue, red, white = (1, 33, 105), (200, 16, 46), (255, 255, 255)
    d.rectangle([0, 0, W, H], fill=blue)
    t = H / 30 * 6
    d.line([(0, 0), (W, H)], fill=white, width=int(t))
    d.line([(0, H), (W, 0)], fill=white, width=int(t))
    # counter-changed red saltire (offset clockwise)
    tr = H / 30 * 2
    k = H / W
    off = tr * 0.9
    for (x0, y0, x1, y1, ox, oy) in ((0, 0, W / 2, H / 2, 0, off), (W / 2, H / 2, W, H, 0, -off),
                                     (0, H, W / 2, H / 2, off * 0.0, -off * 0.0 + -off), (W / 2, H / 2, W, 0, 0, off)):
        d.line([(x0 + ox, y0 + oy), (x1 + ox, y1 + oy)], fill=red, width=int(tr))
    d.rectangle([W / 2 - H / 6, 0, W / 2 + H / 6, H], fill=white)
    d.rectangle([0, H / 2 - H / 6, W, H / 2 + H / 6], fill=white)
    d.rectangle([W / 2 - H / 10, 0, W / 2 + H / 10, H], fill=red)
    d.rectangle([0, H / 2 - H / 10, W, H / 2 + H / 10], fill=red)


@flag
def f_france(d):
    stripes_v(d, [(0, 35, 149), (255, 255, 255), (237, 41, 57)])


@flag
def f_china(d):
    d.rectangle([0, 0, W, H], fill=(238, 28, 37))
    y = (255, 255, 0)
    u = H / 20
    d.polygon(star_pts(5 * u, 5 * u, 3 * u), fill=y)
    for (sx, sy) in ((10, 2), (12, 4), (12, 7), (10, 9)):
        ang = math.degrees(math.atan2(5 - sy, 5 - sx))
        d.polygon(star_pts(sx * u, sy * u, u, rot=ang), fill=y)


@flag
def f_japan(d):
    d.rectangle([0, 0, W, H], fill=(255, 255, 255))
    r = H * 0.3
    d.ellipse([W / 2 - r, H / 2 - r, W / 2 + r, H / 2 + r], fill=(188, 0, 45))


@flag
def f_balkenkreuz(d):
    d.rectangle([0, 0, W, H], fill=(92, 98, 82))                      # feldgrau
    cx, cy, a = W / 2, H / 2, H * 0.36
    w = a * 0.42
    white, black = (240, 240, 236), (16, 16, 16)
    for ww, col in ((w * 1.55, white), (w, black)):
        d.rectangle([cx - a, cy - ww / 2, cx + a, cy + ww / 2], fill=col)
        d.rectangle([cx - ww / 2, cy - a, cx + ww / 2, cy + a], fill=col)
    # the white edge only on the outer arms (classic Balkenkreuz look)
    e = (w * 1.55 - w) / 2
    for sx in (-1, 1):
        d.rectangle([cx + sx * a - (e if sx > 0 else 0) - (0 if sx > 0 else -0), cy - w * 0.775, cx + sx * a + (e if sx < 0 else 0), cy + w * 0.775], fill=white)


@flag
def f_vdv(d):
    sky, green, gold = (60, 130, 210), (40, 120, 60), (240, 200, 60)
    d.rectangle([0, 0, W, H], fill=sky)
    d.rectangle([0, H * 0.82, W, H], fill=green)
    cx, cy = W / 2, H * 0.40
    R = H * 0.24
    d.pieslice([cx - R, cy - R, cx + R, cy + R], 180, 360, fill=(250, 250, 250))    # canopy
    for k in range(5):                                                         # gores
        x = cx - R + 2 * R * k / 4
        d.line([(x, cy), (cx, cy - R * 0.98)], fill=(200, 200, 200), width=int(H * 0.006))
    for k in range(5):                                                         # lines to the jumper
        d.line([(cx - R + 2 * R * k / 4, cy), (cx, cy + R * 0.9)], fill=(250, 250, 250), width=int(H * 0.006))
    d.ellipse([cx - H * 0.025, cy + R * 0.85, cx + H * 0.025, cy + R * 0.95 + H * 0.05], fill=gold)
    for s in (-1, 1):                                                          # two aircraft
        px, py = cx + s * R * 1.65, cy + R * 0.2
        body = [(px - s * H * 0.09, py), (px + s * H * 0.09, py)]
        d.line(body, fill=gold, width=int(H * 0.025))
        d.polygon([(px, py - H * 0.012), (px - s * H * 0.02, py - H * 0.012), (px - s * H * 0.04, py - H * 0.07),
                   (px - s * H * 0.06, py - H * 0.07)], fill=gold)
        d.polygon([(px, py + H * 0.012), (px - s * H * 0.02, py + H * 0.012), (px - s * H * 0.04, py + H * 0.07),
                   (px - s * H * 0.06, py + H * 0.07)], fill=gold)


@flag
def f_vmf(d):
    d.rectangle([0, 0, W, H], fill=(255, 255, 255))
    t = H * 0.11
    d.line([(0, 0), (W, H)], fill=(0, 82, 165), width=int(t))
    d.line([(0, H), (W, 0)], fill=(0, 82, 165), width=int(t))


@flag
def f_vks(d):
    sky, gold, silver = (80, 160, 230), (250, 205, 40), (225, 228, 232)
    d.rectangle([0, 0, W, H], fill=sky)
    cx, cy = W / 2, H / 2
    for k in range(24):                                                   # sun rays
        a = math.radians(k * 15)
        a2 = math.radians(k * 15 + 4)
        L = W
        d.polygon([(cx, cy), (cx + L * math.cos(a), cy + L * math.sin(a)), (cx + L * math.cos(a2), cy + L * math.sin(a2))], fill=gold)
    R = H * 0.16
    d.ellipse([cx - R * 1.25, cy - R * 1.25, cx + R * 1.25, cy + R * 1.25], fill=sky)
    for s in (-1, 1):                                                     # wings
        for j in range(4):
            y = cy - R * 0.4 + j * R * 0.22
            d.polygon([(cx + s * R * 0.25, y), (cx + s * R * (2.1 - j * 0.3), y - R * 0.25 + j * R * 0.05),
                       (cx + s * R * (1.9 - j * 0.3), y + R * 0.12), (cx + s * R * 0.25, y + R * 0.18)], fill=silver)
    d.polygon([(cx - R * 0.1, cy - R * 0.9), (cx + R * 0.1, cy - R * 0.9), (cx + R * 0.05, cy + R * 0.9), (cx - R * 0.05, cy + R * 0.9)], fill=silver)
    d.ellipse([cx - R * 0.2, cy - R * 0.2, cx + R * 0.2, cy + R * 0.2], fill=gold)


@flag
def f_nato(d):
    navy, white = (0, 41, 107), (255, 255, 255)
    d.rectangle([0, 0, W, H], fill=navy)
    cx, cy = W / 2, H / 2
    R = H * 0.27
    d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=white, width=int(H * 0.018))
    for a in (0, 90, 180, 270):
        ra = math.radians(a)
        ex, ey = cx + math.cos(ra) * H * 0.47, cy + math.sin(ra) * H * 0.47
        sx, sy = cx + math.cos(ra) * R * 1.12, cy + math.sin(ra) * R * 1.12
        d.line([(sx, sy), (ex, ey)], fill=white, width=int(H * 0.012))
    pts = star_pts(cx, cy, R * 0.98, R * 0.22, rot=-90, n=4)
    d.polygon(pts, fill=white)
    # shaded halves (compass rose look)
    for i in range(4):
        tip = pts[2 * i]
        nxt = pts[(2 * i + 1) % 8]
        d.polygon([(cx, cy), tip, nxt], fill=(0, 41, 107))
        d.line([(cx, cy), tip], fill=white, width=int(H * 0.006))


@flag
def f_un(d):
    blue, white = (91, 146, 229), (255, 255, 255)
    d.rectangle([0, 0, W, H], fill=blue)
    cx, cy = W / 2, H * 0.47
    R = H * 0.26
    lw = int(H * 0.007)
    for k in range(1, 5):
        r = R * k / 4
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=white, width=lw)
    for k in range(8):
        a = math.radians(k * 45)
        d.line([(cx, cy), (cx + R * math.cos(a), cy + R * math.sin(a))], fill=white, width=lw)
    rng = np.random.default_rng(4)
    for _ in range(40):                                                   # continents (stylised blobs)
        a = rng.uniform(0, 2 * math.pi); r = R * math.sqrt(rng.uniform(0.05, 0.9))
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        s = R * rng.uniform(0.04, 0.10)
        d.ellipse([x - s, y - s * 0.7, x + s, y + s * 0.7], fill=white)
    for s in (-1, 1):                                                     # olive branches
        for k in range(9):
            a = math.radians(100 + k * 18)
            x = cx + s * (R * 1.25) * math.cos(a) * -1
            y = cy + (R * 1.25) * math.sin(a)
            d.ellipse([x - H * 0.022, y - H * 0.012, x + H * 0.022, y + H * 0.012], fill=white)


@flag
def f_white(d):
    d.rectangle([0, 0, W, H], fill=(236, 234, 226))


@flag
def f_victory(d):
    """Victory Banner (150th Idritsa rifle division): silver-white star and hammer and sickle, white lettering."""
    red, white = (186, 12, 14), (246, 242, 236)
    d.rectangle([0, 0, W, H], fill=red)
    ax = H * 0.30
    d.polygon(star_pts(ax, H * 0.115, H * 0.075), fill=white)
    hammer_sickle(d, ax, H * 0.20 + H * 0.135, H * 0.27, white)
    lines = ["150 СТР. ОРДЕНА КУТУЗОВА II СТ.", "ИДРИЦК. ДИВ.", "79 С.К. 3 У.А. 1 Б.Ф."]
    sz = int(H * 0.075)
    while True:
        f = ImageFont.truetype(FB, sz)
        if max(d.textlength(t, font=f) for t in lines) <= W * 0.50 or sz < 8:
            break
        sz -= 2
    for i, t in enumerate(lines):
        d.text((W * 0.08, H * (0.60 + i * 0.095)), t, font=f, fill=white)


def cloth_finish(im, seed):
    """Cloth weave + slight fading, hem on the hoist side."""
    a = np.asarray(im).astype(float)
    h, w = a.shape[:2]
    y, x = np.mgrid[0:h, 0:w]
    weave = ((x % 2) ^ (y % 2)) * 5.0 - 2.5
    S = 1 << max(h, w - 1).bit_length()
    n = fbm(S, 4, 4, seed)[:h, :w] * 9
    a[..., :3] += (weave + n)[..., None]
    hem = max(5, w // 48)
    a[:, :hem, :3] *= 0.82                                                # sewn hoist sleeve
    a[:, hem:hem + 1, :3] *= 0.7
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def icon(cloth, name):
    """32x32 inventory icon: pole on the left, waving cloth on top."""
    S = 32 * SS
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([3 * SS, 2 * SS, 5 * SS, 31 * SS], fill=(150, 152, 156, 255))
    d.rectangle([3 * SS, 2 * SS, 4 * SS, 31 * SS], fill=(200, 202, 206, 255))
    d.ellipse([2.4 * SS, 0.6 * SS, 5.6 * SS, 3.4 * SS], fill=(230, 190, 60, 255))
    cw, ch = 25, 17
    src = cloth.resize((cw * SS, ch * SS), Image.LANCZOS)
    src = np.asarray(src).astype(float)
    out = np.asarray(im).astype(float)
    for xx in range(cw * SS):
        t = xx / (cw * SS)
        dy = int(math.sin(t * math.pi * 2.0) * 1.3 * SS * t)
        shade = 1.0 + 0.12 * math.cos(t * math.pi * 2.0)
        y0 = 3 * SS + dy
        x0 = 5 * SS + xx
        if x0 >= S:
            break
        col = src[:, xx, :].copy()
        col[:, :3] *= shade
        y1 = min(S, y0 + ch * SS)
        out[y0:y1, x0, :] = col[:y1 - y0]
    im = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA").resize((32, 32), Image.LANCZOS)
    # 1-px dark outline for readability
    a = np.asarray(im).copy()
    alpha = a[..., 3] > 40
    edge = np.zeros_like(alpha)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(alpha, dx, 1), dy, 0)
    ring = edge & ~alpha
    a[ring] = (30, 30, 34, 200)
    a[alpha, 3] = 255
    Image.fromarray(a, "RGBA").save(RES + "/item/flag_%s.png" % name)


for i, (name, fn) in enumerate(FLAGS.items()):
    im, d = canvas(FW, FH, (0, 0, 0, 255))
    fn(d)
    im = cloth_finish(down(im, FW, FH), 300 + i)
    im.save(RES + "/entity/flag/%s.png" % name)
    icon(im, name)
print("flags", len(FLAGS), list(FLAGS))

# ============================================================ BLOCK TEXTURES (64x64)
S = 64
yy, xx = np.mgrid[0:S, 0:S]


def base(c, seed, k=10, cells=4):
    return np.zeros((S, S, 3)) + np.array(c, float) + (fbm(S, cells, 4, seed) * k)[..., None]


def wear(img, seed, col=(60, 58, 54), amt=0.35):
    m = np.clip(fbm(S, 6, 4, seed) * 2.4 - 0.9, 0, 1) * amt
    return img * (1 - m[..., None]) + np.array(col) * m[..., None]


# pole: galvanised steel, horizontal shading = round tube look
pole = base((168, 170, 172), 501, 6) + (np.cos((xx / S - 0.5) * math.pi * 1.6) * 26 - 8)[..., None]
pole += (np.sin(yy * 0.9) * 2)[..., None]
save(pole, "deco_pole.png")
brass = base((205, 160, 50), 502, 12) + (np.cos((xx / S - 0.35) * math.pi * 2) * 30)[..., None]
save(brass, "deco_brass.png")
rope = base((222, 216, 200), 503, 8) + (np.sin((xx + yy) * 0.8) * 20)[..., None]
save(rope, "deco_rope.png")

# painted metals
lg = wear(base((74, 92, 60), 511, 9), 512, (100, 92, 80), 0.3)              # lantern / lamp green
save(lg, "deco_paint_green.png")
gr = wear(base((112, 116, 118), 513, 8), 514, (80, 70, 60), 0.25)           # lamp grey
save(gr, "deco_paint_grey.png")
ol = wear(base((86, 94, 62), 515, 8), 516, (70, 64, 50), 0.25)              # searchlight olive drab
ol += ((np.abs((xx % 32) - 16) < 1) * -18)[..., None]                       # panel seams
save(ol, "deco_paint_olive.png")
blk = base((34, 35, 37), 517, 6)
save(blk, "deco_black.png")
chrome = base((200, 204, 210), 518, 8) + (np.sin(xx / S * math.pi * 3) * 35)[..., None]
save(chrome, "deco_chrome.png")

# glasses (alpha for translucent render type)
def glass(tint, a0, seed, name, glow=0.0):
    img = np.zeros((S, S, 3)) + np.array(tint, float)
    hl = np.exp(-((xx - 18) ** 2) / 30.0) * 90 + np.exp(-((xx - 26) ** 2) / 6.0) * 50
    img += hl[..., None] + glow
    alpha = np.zeros((S, S)) + a0 + hl * 0.9
    save(img, name, alpha)


glass((200, 215, 220), 60, 521, "deco_glass.png")
glass((230, 40, 30), 175, 522, "deco_glass_red.png", 25)
glass((255, 150, 20), 165, 523, "deco_glass_orange.png", 25)
glass((80, 110, 255), 165, 524, "deco_glass_blue.png", 25)
glass((255, 196, 110), 120, 525, "deco_glass_warm.png", 40)        # lit kerosene-lantern globe
# frosted / emitting surfaces (opaque, bright)
fr = base((246, 240, 214), 525, 5) + (np.exp(-((xx - 32) ** 2 + (yy - 32) ** 2) / 500.0) * 12)[..., None]
save(fr, "deco_frost.png")
led = np.zeros((S, S, 3)) + (235, 238, 245)
cell = ((xx % 8 < 6) & (yy % 8 < 6))
led[cell] = (255, 255, 250)
led[~cell] = (150, 152, 156)
save(led, "deco_led.png")
save(led * 0.42 + np.array((18, 19, 22)), "deco_led_off.png")
save(fr * 0.5 + np.array((30, 30, 28)), "deco_frost_off.png")
flame = np.zeros((S, S, 3))
fy = yy / S                                   # image row 0 = top of the flame = narrow tip
core = np.exp(-((xx - 32) ** 2) / (40 + 300 * fy))
flame[..., 0] = 255
flame[..., 1] = 140 + 110 * core
flame[..., 2] = 30 + 170 * core * core
save(flame, "deco_flame.png", np.clip(core * 400 * (0.25 + fy), 0, 255))

# searchlight lens: concentric Fresnel rings + warm glass
r = np.sqrt((xx - 31.5) ** 2 + (yy - 31.5) ** 2) / 32
lens = np.zeros((S, S, 3)) + (225, 228, 215)
lens += (np.sin(r * 40) * 18)[..., None]
lens += (np.exp(-r * r * 6) * 25)[..., None]
lens[r > 0.97] = (60, 62, 64)
save(lens, "deco_lens.png")
lens_off = lens * 0.55 + np.array((20, 22, 26))
save(lens_off, "deco_lens_off.png")
fins = base((70, 76, 56), 531, 6) + ((yy % 6 < 2) * -30 + (yy % 6 == 2) * 18)[..., None]
save(fins, "deco_fins.png")

# ------------------------------------------------------------ ARMOUR PLATES
def armour_base(seed):
    img = base((88, 92, 86), seed, 10, 3)
    img += (fbm(S, 12, 3, seed + 1) * 6)[..., None]
    img = wear(img, seed + 2, (110, 74, 46), 0.22)                          # rust bloom
    edge = np.minimum(np.minimum(xx, S - 1 - xx), np.minimum(yy, S - 1 - yy))
    img += ((edge == 0) * -30 + (edge == 1) * 16)[..., None]                # plate bevel
    for p in range(4, S, 10):                                               # rivets along the border
        for (rx, ry) in ((p, 3), (p, S - 4), (3, p), (S - 4, p)):
            rr = (xx - rx) ** 2 + (yy - ry) ** 2
            img[rr <= 3] += 26
            img[(rr > 3) & (rr <= 5)] -= 22
    return img


plain = armour_base(601)
save(plain, "deco_armor.png")
side = armour_base(611)
side += ((np.abs(yy - 32) < 1) * -24)[..., None]                            # weld seam
save(side, "deco_armor_side.png")
hz = armour_base(621)
band = (((xx + yy) // 8) % 2 == 0)
inner = (np.minimum(np.minimum(xx, S - 1 - xx), np.minimum(yy, S - 1 - yy)) > 6)
hz[band & inner] = hz[band & inner] * 0.25 + np.array((235, 190, 20)) * 0.75
hz[~band & inner] = hz[~band & inner] * 0.35 + np.array((20, 20, 20)) * 0.65
save(hz, "deco_armor_hazard.png")


def emblem(name, draw, seed):
    img = armour_base(seed)
    E = S * SS
    im = Image.new("RGBA", (E, E), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    draw(d, E)
    em = np.asarray(im.resize((S, S), Image.LANCZOS)).astype(float)
    a = em[..., 3:4] / 255.0
    paint = em[..., :3] + (fbm(S, 8, 3, seed + 5) * 10)[..., None]
    chip = np.clip(fbm(S, 10, 3, seed + 6) * 3 - 1.9, 0, 1)[..., None]      # chipped paint shows steel
    a = a * (1 - chip)
    out = img * (1 - a) + paint * a
    save(out, "deco_armor_%s.png" % name)


def e_star_red(d, E):
    c = E / 2
    d.polygon(star_pts(c, c * 1.04, E * 0.36), fill=(240, 240, 235))
    d.polygon(star_pts(c, c * 1.04, E * 0.31), fill=(190, 20, 20))


def e_balkenkreuz(d, E):
    c, a = E / 2, E * 0.34
    for w, col in ((a * 0.66, (240, 240, 236)), (a * 0.40, (15, 15, 15))):
        d.rectangle([c - a, c - w / 2, c + a, c + w / 2], fill=col)
        d.rectangle([c - w / 2, c - a, c + w / 2, c + a], fill=col)


def e_us_star(d, E):
    c = E / 2
    d.ellipse([c - E * 0.36, c - E * 0.36, c + E * 0.36, c + E * 0.36], fill=(240, 240, 235))
    d.ellipse([c - E * 0.32, c - E * 0.32, c + E * 0.32, c + E * 0.32], fill=(70, 80, 60))
    d.polygon(star_pts(c, c * 1.03, E * 0.31), fill=(240, 240, 235))


def e_roundel(d, E):
    c = E / 2
    for r, col in ((0.38, (240, 240, 235)), (0.34, (0, 50, 140)), (0.24, (240, 240, 235)), (0.13, (200, 16, 30))):
        d.ellipse([c - E * r, c - E * r, c + E * r, c + E * r], fill=col)


def e_vdv(d, E):
    c = E / 2
    R = E * 0.28
    d.pieslice([c - R, c - R * 0.9, c + R, c + R * 1.1], 180, 360, fill=(235, 235, 230))
    for k in range(5):
        d.line([(c - R + 2 * R * k / 4, c + R * 0.1), (c, c + R * 1.0)], fill=(235, 235, 230), width=int(E * 0.012))
    d.ellipse([c - E * 0.03, c + R * 0.95, c + E * 0.03, c + R * 1.15], fill=(235, 200, 60))
    gold = (235, 200, 60)
    for s in (-1, 1):                         # two transport aircraft (top view) flanking the canopy, nose up
        px, py, L = c + s * R * 1.30, c + R * 0.05, E * 0.155
        d.polygon([(px, py - L * 0.5), (px + L * 0.07, py - L * 0.38), (px + L * 0.06, py + L * 0.5),
                   (px - L * 0.06, py + L * 0.5), (px - L * 0.07, py - L * 0.38)], fill=gold)            # fuselage
        d.polygon([(px - L * 0.55, py - L * 0.02), (px + L * 0.55, py - L * 0.02), (px + L * 0.55, py + L * 0.10),
                   (px - L * 0.55, py + L * 0.10)], fill=gold)                                          # wing
        d.polygon([(px - L * 0.22, py + L * 0.40), (px + L * 0.22, py + L * 0.40), (px + L * 0.22, py + L * 0.48),
                   (px - L * 0.22, py + L * 0.48)], fill=gold)                                          # tailplane


def e_vmf(d, E):
    c = E / 2
    col = (235, 235, 230)
    w = int(E * 0.05)
    d.line([(c, c - E * 0.30), (c, c + E * 0.30)], fill=col, width=w)                    # shank
    d.line([(c - E * 0.16, c - E * 0.18), (c + E * 0.16, c - E * 0.18)], fill=col, width=w)  # stock
    d.ellipse([c - E * 0.06, c - E * 0.40, c + E * 0.06, c - E * 0.28], outline=col, width=w)  # ring
    d.arc([c - E * 0.28, c - E * 0.05, c + E * 0.28, c + E * 0.34], 20, 160, fill=col, width=w)  # arms
    for s in (-1, 1):
        x = c + s * E * 0.27
        d.polygon([(x, c + E * 0.12), (x - s * E * 0.07, c + E * 0.10), (x + s * E * 0.02, c + E * 0.02)], fill=col)


def pogon(d, E, field, edge, stripes, stars, bars=0, zigzag=False):
    """Russian shoulder board, vertical, pointed (trapezoid) top with a button."""
    c = E / 2
    w, top, bot = E * 0.34, E * 0.12, E * 0.92
    shape = [(c - w / 2, bot), (c - w / 2, top + E * 0.10), (c, top), (c + w / 2, top + E * 0.10), (c + w / 2, bot)]
    d.polygon(shape, fill=edge)
    inset = E * 0.025
    inner = [(c - w / 2 + inset, bot - inset), (c - w / 2 + inset, top + E * 0.10 + inset * 0.3), (c, top + inset * 1.4),
             (c + w / 2 - inset, top + E * 0.10 + inset * 0.3), (c + w / 2 - inset, bot - inset)]
    d.polygon(inner, fill=field)
    if zigzag:
        for k in range(int(E * 0.8 / (E * 0.03))):
            y = top + E * 0.12 + k * E * 0.03
            if y > bot - E * 0.03:
                break
            d.line([(c - w / 2 + inset, y), (c + w / 2 - inset, y + E * 0.015)], fill=(180, 140, 40), width=int(E * 0.006))
    for sx in stripes:
        d.rectangle([c + sx * E - E * 0.012, top + E * 0.14, c + sx * E + E * 0.012, bot - inset], fill=(170, 20, 20))
    for (sx, sy, sr) in stars:
        d.polygon(star_pts(c + sx * E, sy * E, sr * E), fill=(250, 215, 80))
    for k in range(bars):
        y = bot - E * 0.10 - k * E * 0.06
        d.rectangle([c - w / 2 + inset, y - E * 0.018, c + w / 2 - inset, y + E * 0.018], fill=(230, 190, 60))
    d.ellipse([c - E * 0.035, top + E * 0.06, c + E * 0.035, top + E * 0.13], fill=(230, 190, 60))      # button


def e_pogon_general(d, E):       # colonel general: gold woven field, red piping, 3 large stars in a column
    pogon(d, E, (215, 170, 50), (180, 20, 20), [], [(0, 0.40, 0.06), (0, 0.56, 0.06), (0, 0.72, 0.06)], zigzag=True)


def e_pogon_colonel(d, E):       # colonel: olive field, 2 red stripes, 3 stars
    pogon(d, E, (80, 92, 58), (60, 70, 44), [-0.05, 0.05], [(-0.09, 0.62, 0.036), (0.09, 0.62, 0.036), (0, 0.46, 0.036)])


def e_pogon_lieutenant(d, E):    # senior lieutenant: 1 stripe, 3 small stars
    pogon(d, E, (80, 92, 58), (60, 70, 44), [0.0], [(-0.07, 0.66, 0.026), (0.07, 0.66, 0.026), (0, 0.52, 0.026)])


def e_pogon_sergeant(d, E):      # sergeant: 3 gold bars (lychki)
    pogon(d, E, (80, 92, 58), (60, 70, 44), [], [], bars=3)


for i, (n, f) in enumerate((("star_red", e_star_red), ("balkenkreuz", e_balkenkreuz), ("us_star", e_us_star),
                            ("roundel", e_roundel), ("vdv", e_vdv), ("vmf", e_vmf))):    # r15: shoulder-board plates removed - shoulder boards are uniforms now
    emblem(n, f, 700 + i * 10)
print("DECO_TEX_DONE")

# ------------------------------------------------------------ misc: transparent "no flame" + flagpole icon
Image.new("RGBA", (16, 16), (0, 0, 0, 0)).save(OUT + "/deco_none.png")
ic = Image.new("RGBA", (32 * SS, 32 * SS), (0, 0, 0, 0))
d = ImageDraw.Draw(ic)
d.rectangle([14 * SS, 3 * SS, 18 * SS, 29 * SS], fill=(150, 152, 156, 255))
d.rectangle([14 * SS, 3 * SS, 15.5 * SS, 29 * SS], fill=(205, 207, 210, 255))
d.ellipse([12.5 * SS, 0.3 * SS, 19.5 * SS, 5 * SS], fill=(230, 190, 60, 255))
d.rectangle([10 * SS, 28 * SS, 22 * SS, 31.5 * SS], fill=(150, 150, 146, 255))
d.line([(13.4 * SS, 5 * SS), (13.4 * SS, 28 * SS)], fill=(225, 220, 205, 255), width=SS)
ic = ic.resize((32, 32), Image.LANCZOS)
a = np.asarray(ic).copy(); al = a[..., 3] > 40
edge = np.zeros_like(al)
for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    edge |= np.roll(np.roll(al, dx, 1), dy, 0)
a[edge & ~al] = (30, 30, 34, 200); a[al, 3] = 255
Image.fromarray(a, "RGBA").save(RES + "/item/flagpole.png")
print("MISC_DONE")
