#!/usr/bin/env python3
"""Procedural PBR base-color textures for the BM-21 model.
All textures are drawn by the agent (numpy/PIL) - no third-party assets.
Output: /home/user/.cache/bm21/tex/T_*.png  (then packed into the .blend / .glb)
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = "/home/user/.cache/bm21/tex"
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1963)  # year BM-21 entered service


def pnoise(size, sigma, seed=None, aspect=(1.0, 1.0)):
    """Seamless (periodic) gaussian-filtered noise in [-1,1]."""
    r = np.random.default_rng(seed) if seed is not None else rng
    h, w = size if isinstance(size, tuple) else (size, size)
    white = r.standard_normal((h, w))
    fy = np.fft.fftfreq(h)[:, None] * aspect[0]
    fx = np.fft.fftfreq(w)[None, :] * aspect[1]
    filt = np.exp(-(fx ** 2 + fy ** 2) * (sigma ** 2) * 2 * np.pi ** 2)
    n = np.real(np.fft.ifft2(np.fft.fft2(white) * filt))
    n -= n.mean()
    n /= (np.abs(n).max() + 1e-9)
    return n


def fbm(size, base_sigma, octaves=4, seed=0, aspect=(1.0, 1.0)):
    acc = 0
    amp = 1.0
    tot = 0
    for o in range(octaves):
        acc = acc + amp * pnoise(size, base_sigma / (2 ** o), seed + o, aspect)
        tot += amp
        amp *= 0.5
    return acc / tot


def srgb(c):
    return np.array(c, dtype=np.float32) / 255.0


def save(name, arr, alpha=None):
    arr = np.clip(arr, 0, 1)
    img = (arr * 255 + 0.5).astype(np.uint8)
    if alpha is not None:
        a = (np.clip(alpha, 0, 1) * 255 + 0.5).astype(np.uint8)
        im = Image.fromarray(np.dstack([img, a]), "RGBA")
    else:
        im = Image.fromarray(img, "RGB")
    p = os.path.join(OUT, name + ".png")
    im.save(p, optimize=True)
    print("wrote", p, im.size)


def mix(a, b, t):
    t = np.clip(t, 0, 1)[..., None] if np.ndim(t) == 2 else t
    return a * (1 - t) + b * t


S = 512
yy, xx = np.mgrid[0:S, 0:S] / S

# ---------------------------------------------------------------- paint (4BO protective green)
def paint(name, base, seed, chips=True, streak=True, dust=0.0):
    col = np.ones((S, S, 3)) * srgb(base)
    mott = fbm(S, 60, 4, seed)
    col *= (1 + 0.07 * mott)[..., None]
    grain = pnoise(S, 1.2, seed + 10)
    col *= (1 + 0.035 * grain)[..., None]
    # sun-faded, slightly yellowish blotches
    fade = np.clip(fbm(S, 90, 3, seed + 20) * 1.6 - 0.35, 0, 1)
    col = mix(col, col * srgb((1.10 * 255, 1.08 * 255, 0.92 * 255)), fade * 0.35)
    if streak:  # rain streaks: long vertical (along V) structures
        st = pnoise((S, S), 3, seed + 30, aspect=(0.06, 1.0))
        st = np.clip(st * 2.2 - 0.6, 0, 1)
        col = mix(col, col * 0.80, st * 0.45)
    if chips:  # paint chips: primer red-brown ring + bare steel core
        chipn = fbm(S, 6, 3, seed + 40)
        edge = (chipn > 0.52) & (chipn <= 0.60)
        core = chipn > 0.60
        col[edge] = col[edge] * 0.55 + srgb((110, 62, 40)) * 0.45
        col[core] = srgb((96, 94, 90)) * (1 + 0.1 * grain[core][..., None])
    if dust > 0:
        d = np.clip(fbm(S, 25, 4, seed + 50) * 1.3 + 0.2, 0, 1) * dust
        col = mix(col, srgb((150, 132, 100)), d)
    save(name, col)


paint("T_Paint_BC", (78, 88, 55), 1)
paint("T_PaintWorn_BC", (72, 80, 52), 2, dust=0.35)

# ---------------------------------------------------------------- chassis frame / running gear (black-grey, mud, rust)
col = np.ones((S, S, 3)) * srgb((38, 38, 36))
col *= (1 + 0.12 * fbm(S, 40, 4, 60))[..., None]
rust = np.clip(fbm(S, 12, 4, 61) * 2.0 - 0.55, 0, 1)
col = mix(col, srgb((105, 58, 30)) * (1 + 0.2 * pnoise(S, 2, 62))[..., None], rust * 0.8)
mud = np.clip(fbm(S, 30, 4, 63) * 1.4 + 0.1, 0, 1)
col = mix(col, srgb((92, 78, 58)), mud * 0.45)
save("T_Frame_BC", col)

# ---------------------------------------------------------------- bare / oily steel (tube bores, gears, screws)
col = np.ones((S, S, 3)) * srgb((92, 94, 96))
brushed = pnoise((S, S), 1.5, 70, aspect=(1.0, 0.04))
col *= (1 + 0.12 * brushed)[..., None]
oil = np.clip(fbm(S, 30, 3, 71) * 1.5 - 0.1, 0, 1)
col = mix(col, srgb((40, 36, 30)), oil * 0.6)
save("T_Steel_BC", col)

# ---------------------------------------------------------------- canvas (cab soft top, tarp roll)
weave = (np.sin(xx * S * np.pi * 0.9) * np.sin(yy * S * np.pi * 0.9))
col = np.ones((S, S, 3)) * srgb((88, 90, 62))
col *= (1 + 0.06 * weave)[..., None]
col *= (1 + 0.10 * fbm(S, 50, 4, 80))[..., None]
folds = pnoise((S, S), 18, 81, aspect=(1.0, 0.25))
col *= (1 + 0.10 * folds)[..., None]
stain = np.clip(fbm(S, 35, 4, 82) * 1.8 - 0.4, 0, 1)
col = mix(col, col * 0.72, stain * 0.5)
save("T_Canvas_BC", col)

# ---------------------------------------------------------------- tyre (Ural 14.00-20 "herringbone" tread + sidewall)
tyre = np.ones((S, S, 3)) * srgb((30, 30, 31))
# top half: tread (U = around circumference, V = across width)
u = xx[:S // 2]
v = yy[:S // 2] * 2  # 0..1 across tread
lug = ((u * 24 + np.abs(v - 0.5) * 1.6) % 1.0)
lugmask = (lug < 0.42) & (np.abs(v - 0.5) > 0.06) & (np.abs(v - 0.5) < 0.47)
tread = np.where(lugmask, 1.0, 0.45)
tread *= 1 + 0.12 * pnoise((S // 2, S), 1.5, 90)
dust_t = np.clip(fbm((S // 2, S), 20, 3, 91) * 1.2 + 0.1, 0, 1)
tt = tyre[:S // 2] * tread[..., None]
tt = mix(tt, srgb((96, 84, 64)), dust_t * (1 - tread) * 0.9 + dust_t * 0.12)
tyre[:S // 2] = tt
# bottom half: sidewall, radial mapping handled by UV: V = radius, U = angle
sw = tyre[S // 2:]
vv = yy[S // 2:] * 2 - 1  # 0..1 radius
ring = (np.abs(vv - 0.55) < 0.015) | (np.abs(vv - 0.80) < 0.01)
sw[ring] *= 1.6
sw *= (1 + 0.1 * pnoise((S // 2, S), 2, 92))[..., None]
dust_s = np.clip(fbm((S // 2, S), 25, 3, 93) + (1 - vv) * 0.4, 0, 1)
sw[:] = mix(sw, srgb((105, 92, 70)), dust_s * 0.5)
tyre[S // 2:] = sw
save("T_Tyre_BC", tyre)

# ---------------------------------------------------------------- glass (dusty, dark)
g = np.ones((S, S, 3)) * srgb((34, 44, 50))
g = mix(g, srgb((70, 88, 96)), np.clip(1 - yy * 1.3, 0, 1) * 0.5)
edge = np.minimum(np.minimum(xx, 1 - xx), np.minimum(yy, 1 - yy))
g = mix(g, srgb((120, 108, 86)), np.clip(0.12 - edge, 0, 0.12) / 0.12 * 0.8)
# wiper arcs (clean) on lower half
cx1, cx2, cy = 0.3, 0.72, 1.05
for cx in (cx1, cx2):
    rr = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    inarc = (rr > 0.25) & (rr < 0.62) & (yy < 1.0)
    g[~inarc] = mix(g[~inarc], srgb((110, 100, 80)), 0.12)
save("T_Glass_BC", g)

# ---------------------------------------------------------------- rocket body (9M22U: grey-green with stencils; band tinted in-game)
R = np.ones((S, S, 3)) * srgb((104, 110, 88))
R *= (1 + 0.05 * fbm(S, 30, 3, 100))[..., None]
# U around, V along length (0 = nose, 1 = tail)
V = yy
R[(V > 0.20) & (V < 0.215)] = srgb((30, 30, 30))
R[(V > 0.58) & (V < 0.60)] = srgb((30, 30, 30))
nozz = V > 0.965
R[nozz] = srgb((58, 56, 52))
save("T_Rocket_BC", R)
im = Image.open(os.path.join(OUT, "T_Rocket_BC.png"))
d = ImageDraw.Draw(im)
try:
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
except Exception:
    font = ImageFont.load_default()
for k in range(2):
    ox = 60 + k * 256
    d.text((ox, 150), "9M22U", fill=(20, 20, 20), font=font)
    d.text((ox, 180), "122 OF", fill=(20, 20, 20), font=font)
im.save(os.path.join(OUT, "T_Rocket_BC.png"), optimize=True)

# ---------------------------------------------------------------- decals (RGBA, alpha MASK): tactical number, plates, stencils
W, H = 512, 256
dec = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(dec)
try:
    big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 90)
    mid = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 34)
    sml = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 20)
except Exception:
    big = mid = sml = ImageFont.load_default()
# [0..256]x[0..128]: tactical number "215" (white, stencil-worn)
d.text((18, 10), "215", fill=(226, 226, 214, 255), font=big)
# [256..512]x[0..128]: military plate black with white text
d.rectangle((262, 30, 506, 98), fill=(18, 18, 18, 255), outline=(220, 220, 220, 255), width=3)
d.text((276, 42), "47-18 ЯБ", fill=(235, 235, 235, 255), font=mid)
# [0..256]x[128..256]: guards' badge-like white rhombus + "ОГНЕОПАСНО" stencil
d.polygon([(64, 140), (112, 190), (64, 240), (16, 190)], outline=(226, 226, 214, 255), width=6)
d.text((130, 170), "ОГНЕ-", fill=(226, 226, 214, 255), font=sml)
d.text((130, 195), "ОПАСНО", fill=(226, 226, 214, 255), font=sml)
# [256..512]x[128..256]: "НЕ СТОЯТЬ СЗАДИ" warning (red on white)
d.rectangle((262, 150, 506, 230), fill=(232, 228, 214, 255))
d.text((276, 160), "НЕ СТОЯТЬ", fill=(170, 24, 20, 255), font=mid)
d.text((300, 195), "СЗАДИ!", fill=(170, 24, 20, 255), font=mid)
arr = np.asarray(dec).astype(np.float32) / 255
wear = fbm((H, W), 3, 3, 110)
a = arr[..., 3] * (wear > -0.55)
save("T_Decals_BC", arr[..., :3], alpha=a)

# ---------------------------------------------------------------- lamps (headlight lens / tail / marker) 256x256 atlas
L = np.zeros((256, 256, 3))
cy, cx = np.mgrid[0:128, 0:128] / 128.0
rr = np.sqrt((cx - 0.5) ** 2 + (cy - 0.5) ** 2)
head = np.ones((128, 128, 3)) * srgb((200, 205, 190))
head *= (1 - 0.35 * (np.sin(cx * 60) > 0.6))[..., None]  # fresnel ridges
head *= (0.75 + 0.25 * np.clip(1 - rr * 2, 0, 1))[..., None]
L[:128, :128] = head
L[:128, 128:] = srgb((170, 20, 16)) * (0.8 + 0.2 * (np.sin(cx * 40) > 0))[..., None]
L[128:, :128] = srgb((220, 130, 20)) * (0.8 + 0.2 * (np.sin(cy * 40) > 0))[..., None]
L[128:, 128:] = srgb((40, 40, 40))
Image.fromarray((np.clip(L, 0, 1) * 255).astype(np.uint8)).save(os.path.join(OUT, "T_Lamps_BC.png"))
print("wrote lamps")

# ---------------------------------------------------------------- rubber / hoses, soot (for vertex-color free parts)
col = np.ones((256, 256, 3)) * srgb((26, 26, 26))
col *= (1 + 0.1 * pnoise(256, 2, 120))[..., None]
Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8)).save(os.path.join(OUT, "T_Rubber_BC.png"))

# ---------------------------------------------------------------- wood (tool handles, crate) - small colour accent
S2 = 256
yy2, xx2 = np.mgrid[0:S2, 0:S2] / S2
grain = np.sin((xx2 * 30 + fbm(S2, 20, 3, 130) * 3) * np.pi)
col = np.ones((S2, S2, 3)) * srgb((122, 86, 52))
col *= (1 + 0.12 * grain)[..., None]
Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8)).save(os.path.join(OUT, "T_Wood_BC.png"))
print("done")
