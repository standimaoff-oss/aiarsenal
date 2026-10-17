"""r15: procedural textures for the RF / US uniforms (painted with PIL + numpy, nothing downloaded).
Writes models/uniforms_tex/*.png - packed into models/uniforms.blend and the exported uniforms.glb.

  emr.png            RF EMR "цифра" (pixel camo)          ocp.png            US OCP (Scorpion W2 like blobs + twigs)
  olive_rf.png       RF officers' everyday olive wool      agsu_coat/_trs     US Army Green Service Uniform coat / taupe trousers
  leather_*.png      boots / shoes / visors                palette.png        8x8 colour swatches for small details
  pogon_ru_*.png     RF shoulder boards (field + service)  tab_us_*.png       OCP chest rank tabs, loop_us_*: AGSU shoulder loops
  patch_*.png        flags, tapes                          ribbons_*.png      ribbon bars       shirt_*.png  shirt + tie V
  visor_*.png        cap visors (plain / gold embroidery)
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = "/home/user/models/uniforms_tex"
os.makedirs(OUT, exist_ok=True)
SS = 4


def save(im, name):
    im.save(os.path.join(OUT, name))


def vnoise(S, cells, seed):
    r = np.random.default_rng(seed)
    g = r.uniform(-1, 1, (cells + 1, cells + 1))
    g[-1, :] = g[0, :]
    g[:, -1] = g[:, 0]
    y, x = np.mgrid[0:S, 0:S] / S * cells
    x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int)
    fx = x - x0; fy = y - y0
    fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
    a = g[y0, x0]; b = g[y0, x0 + 1]; c = g[y0 + 1, x0]; d = g[y0 + 1, x0 + 1]
    return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy


def fbm(S, base, octv, seed):
    v = np.zeros((S, S)); amp = 1.0; tot = 0
    for i in range(octv):
        c = base * 2 ** i
        if c > S:
            break
        v += vnoise(S, c, seed + 17 * i) * amp; tot += amp; amp *= 0.5
    return v / tot


def weave(a, amt=6.0, seed=3):
    S = a.shape[0]
    y, x = np.mgrid[0:S, 0:a.shape[1]]
    w = (((x // 1) % 2) ^ ((y // 1) % 2)) * amt - amt / 2
    n = np.random.default_rng(seed).normal(0, amt * 0.35, a.shape[:2])
    a = a.astype(float)
    a[..., :3] += (w + n)[..., None]
    return np.clip(a, 0, 255).astype(np.uint8)


def flat(col, S=64, amt=7.0, seed=1, grain=0.10):
    n = fbm(S, 4, 4, seed)
    a = np.zeros((S, S, 3), float)
    a[:] = col
    a *= (1 + n[..., None] * grain)
    return Image.fromarray(weave(a, amt, seed), "RGB")


# ---------------------------------------------------------------- camo
def emr(S=256):
    """EMR: 4 colours in square 'pixels', larger clusters built from noise thresholds on a coarse grid."""
    px = 4
    G = S // px
    base = (122, 128, 96)                   # light olive-khaki
    cols = [(76, 92, 58), (92, 74, 56), (36, 36, 30)]   # green, brown, black
    n1 = fbm(G, 6, 4, 11); n2 = fbm(G, 8, 3, 23); n3 = fbm(G, 12, 2, 37)
    a = np.zeros((G, G, 3), float); a[:] = base
    a[n1 > 0.10] = cols[0]
    a[(n2 > 0.22)] = cols[1]
    a[(n3 > 0.32) & (n1 > -0.05)] = cols[2]
    r = np.random.default_rng(5).random((G, G))
    a[(r > 0.985)] = cols[0]
    im = Image.fromarray(a.astype(np.uint8), "RGB").resize((S, S), Image.NEAREST)
    return Image.fromarray(weave(np.asarray(im), 7.0, 2), "RGB")


def ocp(S=256):
    """OCP: tan base, soft olive / brown / dark blobs, pale highlights and thin dark twigs."""
    base = np.array((176, 160, 122), float)
    a = np.zeros((S, S, 3), float); a[:] = base
    lay = [((140, 132, 92), 3, 0.05, 41), ((120, 98, 70), 4, 0.18, 52), ((206, 194, 160), 5, 0.28, 63), ((82, 74, 52), 6, 0.36, 74)]
    for col, c, th, sd in lay:
        n = fbm(S, c, 5, sd)
        m = np.clip((n - th) * 14, 0, 1)[..., None]
        a = a * (1 - m) + np.array(col, float) * m
    im = Image.fromarray(a.astype(np.uint8), "RGB")
    d = ImageDraw.Draw(im)
    rng = np.random.default_rng(9)
    for _ in range(26):                     # twigs
        x, y = rng.uniform(0, S, 2); ang = rng.uniform(0, math.pi)
        L = rng.uniform(10, 26)
        for k in range(3):
            x2, y2 = x + math.cos(ang) * L, y + math.sin(ang) * L
            for ox in (-S, 0, S):
                for oy in (-S, 0, S):
                    d.line([(x + ox, y + oy), (x2 + ox, y2 + oy)], fill=(70, 62, 44), width=2)
            x, y = x2, y2; ang += rng.uniform(-0.8, 0.8); L *= 0.7
    return Image.fromarray(weave(np.asarray(im), 6.0, 4), "RGB")


# ---------------------------------------------------------------- vector helpers
def star(d, cx, cy, R, col, outline=None, rot=-90, width=SS):
    pts = []
    for i in range(10):
        a = math.radians(rot + i * 36)
        r = R if i % 2 == 0 else R * 0.40
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(pts, fill=col, outline=outline, width=width if outline else 0)


def canvas(w, h, col):
    im = Image.new("RGBA", (w * SS, h * SS), col)
    return im, ImageDraw.Draw(im)


def down(im, w, h):
    return im.resize((w, h), Image.LANCZOS).convert("RGB")


# ---------------------------------------------------------------- RF shoulder boards (u along the board, collar end at u=1)
def pogon(name, field, edge, stripes=(), stars=(), bars=0, zigzag=False, button=(196, 160, 60), field_tex=None):
    W, H = 96, 40
    im, d = canvas(W, H, edge)
    S = SS
    inner = [S * 3, S * 3, (W - 3) * S, (H - 3) * S]
    if field_tex is not None:
        tile = field_tex.resize((W * S, H * S), Image.NEAREST).convert("RGBA")
        m = Image.new("L", im.size, 0)
        ImageDraw.Draw(m).rectangle(inner, fill=255)
        im.paste(tile, (0, 0), m)
    else:
        d.rectangle(inner, fill=field)
    if zigzag:                                  # woven general's board
        for x in range(0, W * S, 6 * S):
            d.line([(x, 4 * S), (x + 3 * S, (H - 4) * S)], fill=tuple(max(0, c - 40) for c in field), width=S)
            d.line([(x + 3 * S, (H - 4) * S), (x + 6 * S, 4 * S)], fill=tuple(max(0, c - 40) for c in field), width=S)
    # collar end: trapezoid + button
    d.polygon([((W - 3) * S, 3 * S), ((W - 3) * S, (H - 3) * S), ((W + 8) * S, H * S / 2)], fill=edge)
    for yy in stripes:                          # прoсветы, along the board
        y = H * S / 2 + yy * H * S
        d.rectangle([4 * S, y - 1.6 * S, (W - 6) * S, y + 1.6 * S], fill=(176, 26, 26) if not field_tex else (40, 46, 30))
    for i in range(bars):                       # lychki, across the board near the outer end
        x = (12 + i * 7) * S
        d.rectangle([x, 4 * S, x + 4 * S, (H - 4) * S], fill=(212, 178, 70))
    for (u, v, r) in stars:
        star(d, u * W * S, H * S / 2 + v * H * S, r * H * S, (236, 200, 86) if not zigzag else (250, 222, 120),
             outline=(140, 108, 30) if not zigzag else (96, 60, 10), width=S if not zigzag else 2 * S)
    bx = (W - 9) * S
    d.ellipse([bx - 4 * S, H * S / 2 - 4 * S, bx + 4 * S, H * S / 2 + 4 * S], fill=button, outline=(120, 96, 36))
    save(down(im, W, H), "pogon_%s.png" % name)


# ---------------------------------------------------------------- US rank
def chevron(d, cx, cy, w, h, t, col):
    d.polygon([(cx - w, cy + h), (cx, cy), (cx + w, cy + h), (cx + w, cy + h + t), (cx, cy + t), (cx - w, cy + h + t)], fill=col)


def tab_us(name, draw):
    W, H = 40, 48
    tile = ocp(128).resize((W * SS * 2, H * SS * 2)).crop((0, 0, W * SS, H * SS)).convert("RGBA")
    im = tile
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W * SS - 1, H * SS - 1], outline=(96, 86, 62), width=2 * SS)
    draw(d, W * SS, H * SS)
    save(down(im, W, H), "tab_us_%s.png" % name)


INK = (40, 34, 26)


def r_pv2(d, W, H):
    chevron(d, W / 2, H * 0.34, W * 0.36, H * 0.24, H * 0.13, INK)


def r_sgt(d, W, H):
    for i in range(3):
        chevron(d, W / 2, H * (0.20 + i * 0.17), W * 0.36, H * 0.20, H * 0.09, INK)


def r_cpt(d, W, H):
    for i in range(2):
        x = W * (0.30 + i * 0.40)
        d.rectangle([x - W * 0.09, H * 0.22, x + W * 0.09, H * 0.78], fill=INK)


def eagle(d, cx, cy, s, col, dark):
    """Colonel's eagle (stylised): spread wings, shield, head turned to the left (viewer's)."""
    wing = [(-1.0, -0.15), (-0.75, -0.55), (-0.35, -0.25), (-0.15, -0.05), (0.15, -0.05), (0.35, -0.25), (0.75, -0.55), (1.0, -0.15),
            (0.55, 0.20), (0.25, 0.30), (0.15, 0.62), (0.0, 0.75), (-0.15, 0.62), (-0.25, 0.30), (-0.55, 0.20)]
    d.polygon([(cx + x * s, cy + y * s) for x, y in wing], fill=col, outline=dark)
    d.ellipse([cx - 0.16 * s - 0.08 * s, cy - 0.42 * s, cx + 0.04 * s, cy - 0.18 * s], fill=col, outline=dark)
    d.rectangle([cx - 0.13 * s, cy - 0.05 * s, cx + 0.13 * s, cy + 0.32 * s], fill=dark)
    for i in range(3):
        d.line([(cx - 0.9 * s + i * 0.18 * s, cy - 0.12 * s), (cx - 0.6 * s + i * 0.12 * s, cy + 0.12 * s)], fill=dark, width=max(1, int(s * 0.04)))
        d.line([(cx + 0.9 * s - i * 0.18 * s, cy - 0.12 * s), (cx + 0.6 * s - i * 0.12 * s, cy + 0.12 * s)], fill=dark, width=max(1, int(s * 0.04)))


def loop_us(name, draw):
    W, H = 96, 40
    im, d = canvas(W, H, (52, 52, 38))
    d.rectangle([0, 0, W * SS - 1, H * SS - 1], outline=(34, 34, 26), width=2 * SS)
    d.polygon([((W - 3) * SS, 0), ((W + 10) * SS, H * SS / 2), ((W - 3) * SS, H * SS)], fill=(40, 40, 30))
    draw(d, W * SS, H * SS)
    bx = (W - 10) * SS
    d.ellipse([bx - 4 * SS, H * SS / 2 - 4 * SS, bx + 4 * SS, H * SS / 2 + 4 * SS], fill=(196, 160, 60), outline=(120, 96, 36))
    save(down(im, W, H), "loop_us_%s.png" % name)


def l_col(d, W, H):
    eagle(d, W * 0.42, H * 0.48, H * 0.44, (214, 216, 220), (110, 112, 118))


def l_gen(d, W, H):
    for i in range(4):
        star(d, W * (0.14 + i * 0.19), H / 2, H * 0.30, (222, 224, 228), outline=(120, 122, 128))


# ---------------------------------------------------------------- patches, ribbons, shirts, visors
def patches():
    W, H = 48, 32
    im, d = canvas(W, H, (255, 255, 255))
    for i, c in enumerate([(245, 245, 245), (0, 57, 166), (213, 43, 30)]):
        d.rectangle([0, i * H * SS / 3, W * SS, (i + 1) * H * SS / 3], fill=c)
    d.rectangle([0, 0, W * SS - 1, H * SS - 1], outline=(40, 40, 30), width=2 * SS)
    save(down(im, W, H), "patch_ru_flag.png")
    # US subdued flag, reversed (stars at the right = towards the front on the right sleeve)
    tan, brown = (170, 156, 120), (92, 76, 54)
    im, d = canvas(W, H, tan)
    for i in range(13):
        if i % 2 == 0:
            d.rectangle([0, i * H * SS / 13, W * SS, (i + 1) * H * SS / 13], fill=brown)
    d.rectangle([W * SS * 0.55, 0, W * SS, H * SS * 7 / 13], fill=brown)
    for r in range(4):
        for c in range(5):
            star(d, W * SS * (0.60 + c * 0.085), H * SS * (0.08 + r * 0.12), SS * 1.6, tan)
    d.rectangle([0, 0, W * SS - 1, H * SS - 1], outline=(70, 60, 44), width=2 * SS)
    save(down(im, W, H), "patch_us_flag.png")
    # name tapes "U.S. ARMY" (left breast) and a blank name tape
    from PIL import ImageFont
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 9 * SS)
    for nm, txt in (("tape_us_army", "U.S. ARMY"), ("tape_us_name", "SMITH")):
        W2, H2 = 64, 12
        im = ocp(128).resize((W2 * SS * 2, W2 * SS * 2)).crop((0, 0, W2 * SS, H2 * SS)).convert("RGBA")
        d = ImageDraw.Draw(im)
        tw = d.textlength(txt, font=font)
        d.text((W2 * SS / 2 - tw / 2, 0.8 * SS), txt, font=font, fill=INK)
        save(down(im, W2, H2), nm + ".png")


def ribbons(name, rows):
    cols = [((160, 20, 30), (240, 200, 60)), ((30, 60, 140), (240, 240, 240)), ((40, 120, 60), (240, 200, 60)),
            ((200, 40, 40), (30, 60, 140)), ((240, 200, 60), (40, 40, 40)), ((120, 30, 120), (240, 240, 240)),
            ((250, 140, 20), (30, 30, 30)), ((30, 100, 160), (220, 40, 40)), ((240, 240, 240), (160, 20, 30))]
    W, H = 64, 32
    im, d = canvas(W, H, (0, 0, 0, 0))
    k = 0
    rh = H * SS / rows
    for r in range(rows):
        n = 3 if r < rows - 1 else 2
        rw = W * SS / 3
        x0 = (W * SS - n * rw) / 2
        for c in range(n):
            a, b = cols[(k + c) % len(cols)]
            x = x0 + c * rw
            d.rectangle([x, r * rh, x + rw - SS, (r + 1) * rh - SS], fill=a)
            d.rectangle([x + rw * 0.38, r * rh, x + rw * 0.62 - SS, (r + 1) * rh - SS], fill=b)
        k += n
    im = im.resize((W, H), Image.LANCZOS)
    bg = Image.new("RGB", (W, H), (60, 60, 50))
    bg.paste(im, (0, 0), im)
    save(bg, "ribbons_%s.png" % name)


def shirt(name, shirt_col, tie_col):
    W, H = 32, 64
    im, d = canvas(W, H, shirt_col)
    d.polygon([(W * SS * 0.38, 0), (W * SS * 0.62, 0), (W * SS * 0.56, H * SS * 0.12), (W * SS * 0.44, H * SS * 0.12)], fill=tie_col)
    d.polygon([(W * SS * 0.44, H * SS * 0.12), (W * SS * 0.56, H * SS * 0.12), (W * SS * 0.62, H * SS * 0.95), (W * SS * 0.5, H * SS), (W * SS * 0.38, H * SS * 0.95)], fill=tie_col)
    d.line([(W * SS * 0.2, 0), (W * SS * 0.42, H * SS * 0.16)], fill=tuple(c - 30 for c in shirt_col), width=SS)
    d.line([(W * SS * 0.8, 0), (W * SS * 0.58, H * SS * 0.16)], fill=tuple(c - 30 for c in shirt_col), width=SS)
    save(down(im, W, H), "shirt_%s.png" % name)


def visor(name, base, rows=0, gold=(214, 176, 70)):
    W, H = 64, 32
    im, d = canvas(W, H, base)
    for i in range(0, W * SS, 2 * SS):                 # leather sheen
        d.line([(i, 0), (i, H * SS)], fill=tuple(min(255, c + (8 if (i // (2 * SS)) % 2 else 0)) for c in base), width=SS)
    rng = np.random.default_rng(len(name))
    for r in range(rows):                              # oak leaves along the front edge
        y = H * SS * (0.78 - r * 0.30)
        for k in range(14):
            x = W * SS * (0.04 + k * 0.07)
            a = rng.uniform(-0.4, 0.4)
            for s in (-1, 1):
                pts = [(x, y), (x + s * 2.2 * SS, y - 3.5 * SS), (x + s * 0.6 * SS, y - 6 * SS)]
                d.polygon(pts, fill=gold)
            d.ellipse([x - SS, y - SS, x + SS, y + SS], fill=(240, 210, 110))
    save(down(im, W, H), "visor_%s.png" % name)


def palette():
    """8x8 swatches; index = row * 8 + col. Keep in sync with PAL in tools/blender/uniforms.py."""
    P = [(196, 160, 60), (222, 190, 90), (176, 26, 26), (210, 212, 216), (18, 18, 16), (40, 40, 34), (58, 64, 44), (96, 86, 62),
         (140, 120, 80), (230, 222, 196), (80, 62, 40), (110, 74, 44), (34, 52, 120), (238, 238, 232), (52, 52, 38), (152, 134, 112),
         (70, 76, 54), (120, 124, 96), (64, 48, 36), (26, 24, 22), (200, 40, 40), (90, 98, 70), (180, 170, 140), (60, 30, 24)]
    S = 8
    im = Image.new("RGB", (S * 8, S * 8), (255, 0, 255))
    d = ImageDraw.Draw(im)
    for i, c in enumerate(P):
        x, y = (i % 8) * S, (i // 8) * S
        d.rectangle([x, y, x + S - 1, y + S - 1], fill=c)
    save(im, "palette.png")


if __name__ == "__main__":
    E = emr(); save(E, "emr.png")
    save(ocp(), "ocp.png")
    save(flat((86, 94, 64), 128, 6.0, 7), "olive_rf.png")
    save(flat((62, 62, 44), 128, 5.0, 8), "agsu_coat.png")
    save(flat((156, 140, 118), 128, 5.0, 9), "agsu_trs.png")
    save(flat((24, 23, 21), 64, 4.0, 10, 0.25), "leather_black.png")
    save(flat((78, 50, 32), 64, 4.0, 11, 0.25), "leather_brown.png")
    save(flat((146, 116, 82), 64, 8.0, 12, 0.18), "coyote.png")
    save(flat((176, 30, 30), 32, 3.0, 13, 0.05), "band_red.png")
    palette()
    fld, edge = (84, 92, 62), (60, 66, 44)
    pogon("ru_private", fld, edge)
    pogon("ru_sergeant", fld, edge, bars=3)
    pogon("ru_captain", fld, edge, stripes=[0.0], stars=[(0.42, -0.22, 0.13), (0.42, 0.22, 0.13), (0.62, 0.0, 0.13), (0.80, 0.0, 0.13)])
    pogon("ru_colonel", fld, edge, stripes=[-0.14, 0.14], stars=[(0.40, -0.14, 0.21), (0.40, 0.14, 0.21), (0.66, 0.0, 0.21)])
    pogon("ru_general", (214, 172, 56), (176, 26, 26), stars=[(0.28, 0.0, 0.30), (0.50, 0.0, 0.30), (0.72, 0.0, 0.30)], zigzag=True)
    tab_us("private", r_pv2); tab_us("sergeant", r_sgt); tab_us("captain", r_cpt)
    loop_us("colonel", l_col); loop_us("general", l_gen)
    patches()
    ribbons("colonel", 2); ribbons("general", 3)
    shirt("rf", (186, 196, 160), (60, 70, 46))
    shirt("us", (196, 178, 146), (64, 46, 30))
    visor("black", (22, 22, 20)); visor("rf_gold", (22, 22, 20), rows=1); visor("brown", (60, 40, 26)); visor("us_col", (60, 40, 26), rows=1)
    visor("us_gen", (60, 40, 26), rows=2)
    print("UNIFORM_TEX_DONE", len(os.listdir(OUT)))
