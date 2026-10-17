"""r19: textures for the six new uniform sets (PIL + numpy, procedural). Run AFTER uniform_textures.py (reuses its helpers).
  afghanka.png  Soviet M88 'afghanka' khaki        tank_overall.png  Soviet black-blue tank crew overall
  og107.png     US OG-107 olive cotton sateen      mitchell.png      US M1 helmet 'Mitchell' cloud cover
  dcu.png       US DCU three-colour desert         gorka.png / gorka_patch.png  Russian 'Gorka' suit + reinforcements
  telnyashka.png  VDV blue-white striped vest      flight_ru.png     VKS flight coverall olive-grey
  pogon_sa.png  Soviet Army khaki field board 'СА' patch_vdv.png  VDV sleeve patch (parachute + wings)
  palette.png   regenerated with 8 more swatches (index 24..31)"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import uniform_textures as U

PAL = [(196, 160, 60), (222, 190, 90), (176, 26, 26), (210, 212, 216), (18, 18, 16), (40, 40, 34), (58, 64, 44), (96, 86, 62),
       (140, 120, 80), (230, 222, 196), (80, 62, 40), (110, 74, 44), (34, 52, 120), (238, 238, 232), (52, 52, 38), (152, 134, 112),
       (70, 76, 54), (120, 124, 96), (64, 48, 36), (26, 24, 22), (200, 40, 40), (90, 98, 70), (180, 170, 140), (60, 30, 24),
       # r19: 24 VDV sky blue, 25 helmet white, 26 visor smoke, 27 tank black-blue, 28 khaki board, 29 G-suit green, 30 steel, 31 DCU tan
       (62, 124, 200), (226, 226, 220), (34, 40, 48), (34, 36, 44), (132, 122, 84), (66, 72, 52), (120, 124, 128), (196, 180, 146)]


def palette():
    S = 8
    im = Image.new("RGB", (S * 8, S * 8), (255, 0, 255))
    d = ImageDraw.Draw(im)
    for i, c in enumerate(PAL):
        x, y = (i % 8) * S, (i // 8) * S
        d.rectangle([x, y, x + S - 1, y + S - 1], fill=c)
    U.save(im, "palette.png")


def blobs(S, base, layers, seed_twigs=None):
    a = np.zeros((S, S, 3), float); a[:] = base
    for col, c, th, sd in layers:
        n = U.fbm(S, c, 5, sd)
        m = np.clip((n - th) * 16, 0, 1)[..., None]
        a = a * (1 - m) + np.array(col, float) * m
    return Image.fromarray(U.weave(a.astype(np.uint8), 6.0, 4), "RGB")


def telnyashka(S=32):
    im = Image.new("RGB", (S, S), (238, 238, 232))
    d = ImageDraw.Draw(im)
    for y in range(0, S, 6):
        d.rectangle([0, y, S, y + 2], fill=(38, 62, 150))
    return im


def pogon_sa():
    W, H = 96, 40
    im, d = U.canvas(W, H, (110, 102, 66))
    S = U.SS
    d.rectangle([2 * S, 2 * S, (W - 2) * S, (H - 2) * S], fill=(132, 122, 84))
    d.ellipse([(W - 14) * S, (H / 2 - 5) * S, (W - 4) * S, (H / 2 + 5) * S], fill=(120, 110, 70), outline=(70, 64, 40), width=S)
    blk = (24, 22, 18)
    # 'С'
    d.arc([18 * S, 9 * S, 40 * S, 31 * S], 50, 310, fill=blk, width=4 * S)
    # 'А'
    d.line([(46 * S, 31 * S), (56 * S, 9 * S), (66 * S, 31 * S)], fill=blk, width=4 * S)
    d.line([(50 * S, 24 * S), (62 * S, 24 * S)], fill=blk, width=3 * S)
    U.save(U.down(im, W, H), "pogon_sa.png")


def patch_vdv():
    W, H = 48, 56
    im, d = U.canvas(W, H, (0, 0, 0, 0))
    S = U.SS
    d.rounded_rectangle([1 * S, 1 * S, (W - 1) * S, (H - 1) * S], radius=6 * S, fill=(62, 124, 200), outline=(214, 176, 70), width=2 * S)
    g = (222, 190, 90)
    d.pieslice([12 * S, 8 * S, 36 * S, 30 * S], 180, 360, fill=(238, 238, 232))       # canopy
    for x in (13, 24, 35):
        d.line([(x * S, 19 * S), (24 * S, 36 * S)], fill=(238, 238, 232), width=S)
    for s in (-1, 1):                                                                    # wings
        pts = [(24 * S, 38 * S), ((24 + s * 18) * S, 32 * S), ((24 + s * 14) * S, 38 * S), ((24 + s * 9) * S, 42 * S)]
        d.polygon(pts, fill=g)
    U.star(d, 24 * S, 46 * S, 4 * S, (176, 26, 26))
    U.save(U.down(im, W, H), "patch_vdv.png")


if __name__ == "__main__":
    palette()
    U.save(U.flat((150, 138, 98), 128, 6.0, 14), "afghanka.png")
    U.save(U.flat((40, 42, 52), 128, 5.0, 15), "tank_overall.png")
    U.save(U.flat((88, 94, 62), 128, 6.0, 16), "og107.png")
    U.save(U.flat((116, 108, 76), 128, 6.0, 17), "gorka.png")
    U.save(U.flat((78, 74, 56), 128, 6.0, 18), "gorka_patch.png")
    U.save(U.flat((104, 112, 92), 128, 5.0, 19), "flight_ru.png")
    U.save(blobs(256, (104, 116, 70), [((70, 86, 48), 4, 0.02, 81), ((122, 102, 64), 5, 0.20, 82), ((60, 64, 40), 7, 0.30, 83)]), "mitchell.png")
    U.save(blobs(256, (204, 188, 154), [((166, 136, 102), 3, 0.08, 91), ((150, 148, 112), 4, 0.16, 92)]), "dcu.png")
    U.save(telnyashka(), "telnyashka.png")
    pogon_sa()
    patch_vdv()
    print("UNIFORM_TEX_R19_DONE")
