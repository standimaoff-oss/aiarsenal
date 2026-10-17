#!/usr/bin/env python3
"""r25 textures for the MANPADS (PIL, no external images): fibreglass tube paints, black paint, stencil decals.
   python3 tools/blender/tex_r25.py  -> tools/blender/tex_r22/T_*.png"""
import os, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tex_r22")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONTB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
random.seed(25)


def grain(base, n=128, fibre=0.0, noise=7, spots=0, name=None):
    im = Image.new("RGB", (n, n), base)
    px = im.load()
    for y in range(n):
        row = random.uniform(-fibre, fibre)            # wound / pultruded fibre streaks along one axis
        for x in range(n):
            d = random.gauss(0, noise) + row
            r, g, b = base
            px[x, y] = (max(0, min(255, int(r + d))), max(0, min(255, int(g + d))), max(0, min(255, int(b + d * 0.8))))
    dr = ImageDraw.Draw(im)
    for _ in range(spots):                              # handling wear: lighter scuffs
        x, y = random.randrange(n), random.randrange(n)
        r = random.randint(1, 3)
        c = tuple(min(255, v + 18) for v in base)
        dr.ellipse((x - r, y - r // 2, x + r, y + r // 2), fill=c)
    im = im.filter(ImageFilter.SMOOTH)
    im.save(os.path.join(OUT, name + ".png"))


def stencil(name, lines, size=(512, 64), col=(235, 235, 225, 255), font=FONT, fs=22, band=None):
    im = Image.new("RGBA", size, (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    y = 4
    if band:
        dr.rectangle((0, 0, size[0], size[1]), fill=band)
    for ln in lines:
        f = ImageFont.truetype(font, fs)
        dr.text((8, y), ln, font=f, fill=col)
        y += fs + 4
    # stencil bridges / paint wear
    px = im.load()
    for _ in range(size[0] * size[1] // 40):
        x, yy = random.randrange(size[0]), random.randrange(size[1])
        if px[x, yy][3] > 0 and not band:
            px[x, yy] = (0, 0, 0, 0)
    im.save(os.path.join(OUT, name + ".png"))


grain((66, 76, 50), fibre=6, noise=5, spots=40, name="T_IglaTube")        # 9P338 tube: olive green fibreglass
grain((86, 82, 58), fibre=4, noise=5, spots=30, name="T_StingerTube")     # FIM-92 launch tube: olive drab
grain((58, 62, 46), noise=6, spots=10, name="T_GripGreen")                # 9P522 / M171 gripstock castings
grain((26, 27, 26), noise=4, name="T_BlackPaint")
grain((196, 160, 40), noise=5, name="T_YellowBand")                       # US HE band
grain((112, 76, 40), noise=5, name="T_BrownBand")                         # US rocket motor band
grain((210, 212, 214), noise=4, name="T_MissileWhite")                    # 9M342 / FIM-92 airframe
grain((150, 152, 150), noise=5, name="T_MissileGrey")
stencil("T_StencilIgla", ["9П338  9М342", "ИГЛА-С   ПАРТ.  04-12"], fs=22)
stencil("T_StencilStinger", ["LAUNCHER AND MISSILE ROUND", "GUIDED MISSILE  FIM-92  STINGER"], fs=20)
stencil("T_StencilDanger", ["ОПАСНО! ЗОНА ЗА ТРУБОЙ 30 м"], size=(512, 32), fs=20)
stencil("T_StencilBlast", ["BACKBLAST AREA  KEEP CLEAR"], size=(512, 32), fs=20, col=(20, 20, 20, 255),
        band=(196, 160, 40, 255))
print("TEX_R25_DONE")
