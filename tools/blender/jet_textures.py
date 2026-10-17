#!/usr/bin/env python3
"""r12 jet + modern vehicle paint schemes and cockpit screens, drawn procedurally with numpy + PIL (no third-party
assets). Output /home/user/.cache/air/tex/:
  T_JetSu57Top / T_JetSu57Bot  - Su-57 'digital' pixel camouflage (light blue-grey, blue-grey, dark grey) + belly
  T_JetInt                      - dark grey cockpit
  T_MfdRadar / T_MfdMap / T_MfdSys - multifunction display pages (emissive)
  T_Hud                         - HUD symbology (pitch ladder, heading tape, velocity vector), alpha
  T_AbFlame                     - afterburner flame gradient with shock diamonds
  T_UsTan / T_RuGreen3          - M2A3 Bradley desert tan, TOS-1A / Ural Russian 3-tone green
"""
import ast, os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
_src = open(os.path.join(HERE, "plane_textures.py"), encoding="utf-8").read()
_tree = ast.parse(_src)
_keep = [n for n in _tree.body if isinstance(n, (ast.Import, ast.ImportFrom, ast.FunctionDef))
         or (isinstance(n, ast.Assign) and all(isinstance(t, ast.Name) and t.id.isupper() for t in n.targets))]
exec(compile(ast.Module(body=_keep, type_ignores=[]), "plane_textures_defs", "exec"), globals())
# plane_textures pulls air_textures defs at import time - replicate
_src2 = open(os.path.join(HERE, "air_textures.py"), encoding="utf-8").read()
_t2 = ast.parse(_src2)
_k2 = [n for n in _t2.body if isinstance(n, (ast.Import, ast.ImportFrom, ast.FunctionDef))
       or (isinstance(n, ast.Assign) and all(isinstance(t, ast.Name) and t.id.isupper() for t in n.targets))]
exec(compile(ast.Module(body=_k2, type_ignores=[]), "air_textures_defs", "exec"), globals())
S = 512
PPM = S / 4.0
os.makedirs(OUT, exist_ok=True)

SU_L, SU_M, SU_D = (150, 163, 176), (106, 122, 140), (70, 80, 96)
SU_BELLY = (178, 188, 198)


def pixel_camo(name, base, tones, seed, px=16, thr=(0.10, 0.28), scale=2.0):
    """Digital camouflage: noise sampled on a coarse grid (px pixels = px/128 m), thresholded, nearest upscaled."""
    n = S // px
    img = np.zeros((S, S, 3), np.float32)
    img[:] = base
    for i, (col, t) in enumerate(zip(tones, thr)):
        f = fbm(n, n, scale, 3, seed + 7 * i, aspect=(1.0, 1.8))
        m = (f > t).astype(np.float32)
        m = np.kron(m, np.ones((px, px), np.float32))
        img = img * (1 - m[..., None]) + np.array(col, np.float32) * m[..., None]
    img = seams(img, seed + 1, dark=0.88, light=1.03, frame=0.6, stringer=0.8, rivets=False)
    img = weather(img, seed + 2, grime=0.05, chips=0.0)
    save(img, name)


def screen_base(col=(6, 10, 8)):
    img = Image.new("RGB", (256, 256), col)
    return img, ImageDraw.Draw(img)


def mfd_radar():
    img, d = screen_base((4, 8, 6))
    G, Gd = (60, 255, 110), (20, 110, 50)
    cx, cy = 128, 236
    for r in (60, 120, 180):
        d.arc([cx - r, cy - r, cx + r, cy + r], 210, 330, fill=Gd, width=1)
    for a in (-60, -30, 0, 30, 60):
        t = np.radians(a - 90)
        d.line([cx, cy, cx + 215 * np.cos(t), cy + 215 * np.sin(t)], fill=Gd, width=1)
    # sweep wedge
    for k in range(18):
        t = np.radians(-90 + 22 - k * 0.9)
        c = tuple(int(v * (1 - k / 18)) for v in G)
        d.line([cx, cy, cx + 200 * np.cos(t), cy + 200 * np.sin(t)], fill=c, width=2)
    rng = np.random.default_rng(5)
    for k in range(6):  # contacts: boxes with heading sticks
        t = np.radians(rng.uniform(-150, -30)); r = rng.uniform(50, 190)
        x, y = cx + r * np.cos(t), cy + r * np.sin(t)
        d.rectangle([x - 4, y - 4, x + 4, y + 4], outline=(255, 220, 60) if k == 2 else G, width=2)
        d.line([x, y, x + rng.uniform(-10, 10), y - 10], fill=G, width=1)
    d.text((8, 6), "RLS  N036  80 km", fill=G)
    d.text((200, 6), "TWS", fill=G)
    d.text((8, 22), "TGT 2  H 6400  V 870", fill=(255, 220, 60))
    for i, s in enumerate(("SRCH", "TWS", "GMT", "SAR", "MAP")):
        d.rectangle([4 + i * 50, 240, 48 + i * 50, 254], outline=Gd)
        d.text((10 + i * 50, 242), s, fill=G)
    return img


def mfd_map():
    img, d = screen_base((10, 12, 14))
    rng = np.random.default_rng(9)
    f = fbm(256, 256, 30, 4, 77)
    terr = np.zeros((256, 256, 3), np.float32)
    lo, hi = np.array([30, 60, 40]), np.array([120, 100, 60])
    terr[:] = lo + (hi - lo) * np.clip(f[..., None] * 0.8 + 0.4, 0, 1)
    terr[f < -0.25] = (30, 60, 110)
    img = Image.fromarray(terr.astype(np.uint8))
    d = ImageDraw.Draw(img)
    for k in range(14):  # contour lines
        y0 = rng.integers(0, 256)
        d.line([(0, y0), (256, y0 + rng.integers(-40, 40))], fill=(80, 80, 60), width=1)
    pts = [(30, 220), (90, 160), (150, 120), (200, 50)]
    d.line(pts, fill=(255, 255, 255), width=2)
    for p in pts:
        d.ellipse([p[0] - 4, p[1] - 4, p[0] + 4, p[1] + 4], outline=(255, 255, 255), width=2)
    d.polygon([(128, 128), (120, 146), (128, 140), (136, 146)], fill=(255, 230, 60))     # own aircraft
    for c, (x, y) in (((255, 60, 60), (180, 80)), ((255, 60, 60), (60, 70))):            # SAM rings
        d.ellipse([x - 30, y - 30, x + 30, y + 30], outline=c, width=2)
    d.text((6, 6), "NAV  WPT 3  42 km", fill=(240, 240, 240))
    return img


def mfd_sys():
    img, d = screen_base((6, 8, 10))
    W, C, A = (230, 235, 240), (60, 220, 255), (255, 200, 60)
    # top view of the aircraft with weapon stations
    d.polygon([(128, 20), (138, 70), (200, 130), (196, 150), (150, 140), (160, 200), (140, 200), (128, 186),
               (116, 200), (96, 200), (106, 140), (60, 150), (56, 130), (118, 70)], outline=C)
    for x, y in ((80, 135), (100, 128), (176, 135), (156, 128), (122, 110), (134, 110)):
        d.rectangle([x - 4, y - 7, x + 4, y + 7], outline=A, width=2)
    for i, (lab, v) in enumerate((("N1", 0.86), ("N2", 0.86), ("EGT", 0.7), ("FUEL", 0.62))):
        x = 10 + i * 62
        d.rectangle([x, 212, x + 52, 222], outline=W)
        d.rectangle([x + 1, 213, x + 1 + int(50 * v), 221], fill=C)
        d.text((x, 228), lab, fill=W)
    d.text((8, 6), "SMS  AL-41F1 x2", fill=W)
    return img


def hud():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    G = (90, 255, 140, 255)
    for k in (-2, -1, 1, 2):     # pitch ladder
        y = 128 - k * 40
        dash = k < 0
        for x0, x1 in ((50, 100), (156, 206)):
            if dash:
                for xx in range(x0, x1, 10):
                    d.line([xx, y, xx + 6, y], fill=G, width=2)
            else:
                d.line([x0, y, x1, y], fill=G, width=2)
        d.text((30, y - 6), str(abs(k) * 10), fill=G)
    d.line([20, 128, 236, 128], fill=G, width=1)   # horizon
    d.ellipse([120, 120, 136, 136], outline=G, width=2)      # velocity vector
    d.line([110, 128, 120, 128], fill=G, width=2); d.line([136, 128, 146, 128], fill=G, width=2); d.line([128, 112, 128, 120], fill=G, width=2)
    for i in range(9):            # heading tape
        x = 48 + i * 20
        d.line([x, 8, x, 16 if i % 2 else 22], fill=G, width=2)
    d.rectangle([4, 110, 40, 146], outline=G, width=2); d.text((8, 122), "870", fill=G)
    d.rectangle([216, 110, 252, 146], outline=G, width=2); d.text((218, 122), "6400", fill=G)
    img.save(os.path.join(OUT, "T_Hud.png"))


def ab_flame():
    w, h = 64, 256
    a = np.zeros((h, w, 3), np.float32)
    v = np.linspace(0, 1, h)[:, None]            # 0 = nozzle, 1 = tail
    core = np.array([255, 250, 235]); blue = np.array([120, 160, 255]); org = np.array([255, 140, 40])
    col = core * np.clip(1 - v * 3, 0, 1) + blue * np.clip(1 - np.abs(v - 0.3) * 4, 0, 1) + org * np.clip(v * 1.5, 0, 1) * (1 - v)
    diam = (0.6 + 0.4 * np.cos(v * 2 * np.pi * 5) ** 8) * np.exp(-v * 1.5)
    a[:] = (col * diam)[:, None, :] if col.ndim == 2 else col
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(os.path.join(OUT, "T_AbFlame.png"))


if __name__ == "__main__":
    pixel_camo("T_JetSu57Top.png", SU_L, (SU_M, SU_D), 571)
    pixel_camo("T_JetSu57Bot.png", SU_BELLY, (SU_L,), 572, thr=(0.35,))
    interior("T_JetInt.png", (62, 66, 72), 573)
    mfd_radar().save(os.path.join(OUT, "T_MfdRadar.png"))
    mfd_map().save(os.path.join(OUT, "T_MfdMap.png"))
    mfd_sys().save(os.path.join(OUT, "T_MfdSys.png"))
    hud()
    ab_flame()
    soft("T_UsTan.png", (176, 150, 108), ((160, 134, 92),), 601, scale=90, thr=(0.25,))
    soft("T_RuGreen3.png", (78, 92, 52), ((50, 56, 36), (120, 112, 76)), 602, scale=80, thr=(0.18, 0.32))
    print("JET_TEX_OK")
