#!/usr/bin/env python3
"""r20 action-bar icons (drawn by the agent with PIL, no third-party art).
Sheet 256 x 128, 8 x 4 cells of 32 px, white glyphs on transparent (tinted in game).
Each glyph is drawn on a 128 px canvas (4x) and downsampled with LANCZOS for clean edges.
Output: mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/gui/actions.png
"""
import math
from PIL import Image, ImageDraw

OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/gui/actions.png"
S = 128
W = 255
LW = 11


def canvas():
    im = Image.new("L", (S, S), 0)
    return im, ImageDraw.Draw(im)


def arc(d, box, a0, a1, w=LW):
    d.arc(box, a0, a1, fill=W, width=w)


def line(d, pts, w=LW):
    d.line(pts, fill=W, width=w, joint="curve")
    for p in (pts[0], pts[-1]):
        d.ellipse((p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2), fill=W)


def arrow_head(d, tip, ang, size=22):
    a1, a2 = ang + math.radians(150), ang - math.radians(150)
    d.polygon([tip, (tip[0] + size * math.cos(a1), tip[1] + size * math.sin(a1)),
               (tip[0] + size * math.cos(a2), tip[1] + size * math.sin(a2))], fill=W)


def disc(d, c, r, fill=W):
    d.ellipse((c[0] - r, c[1] - r, c[0] + r, c[1] + r), fill=fill)


def ring(d, c, r, w=LW):
    d.ellipse((c[0] - r, c[1] - r, c[0] + r, c[1] + r), outline=W, width=w)


def plane_top(d, cx, cy, s=1.0, rot=0.0):
    """Top view of a jet, nose up."""
    pts = [(0, -50), (7, -30), (8, -8), (46, 12), (46, 22), (8, 14), (6, 34), (20, 44), (20, 50), (0, 46),
           (-20, 50), (-20, 44), (-6, 34), (-8, 14), (-46, 22), (-46, 12), (-8, -8), (-7, -30)]
    c, sn = math.cos(rot), math.sin(rot)
    d.polygon([(cx + s * (x * c - y * sn), cy + s * (x * sn + y * c)) for x, y in pts], fill=W)


# ------------------------------------------------------------------ glyphs
def smoke():
    im, d = canvas()
    for c, r in (((40, 78), 26), ((70, 62), 30), ((96, 80), 22), ((60, 92), 22)):
        disc(d, c, r)
    d.rectangle((20, 104, 108, 114), fill=W)          # ground line
    for k in range(3):                                # grenade arcs
        arc(d, (8 + k * 6, 10 + k * 4, 70 + k * 6, 80), 200, 290, 6)
    return im


def sight():
    im, d = canvas()
    ring(d, (64, 64), 44)
    line(d, [(64, 8), (64, 46)]); line(d, [(64, 82), (64, 120)])
    line(d, [(8, 64), (46, 64)]); line(d, [(82, 64), (120, 64)])
    disc(d, (64, 64), 7)
    return im


def map_icon():
    im, d = canvas()
    d.polygon([(14, 30), (44, 18), (84, 30), (114, 18), (114, 98), (84, 110), (44, 98), (14, 110)], outline=W, width=LW)
    line(d, [(44, 22), (44, 98)], 7); line(d, [(84, 30), (84, 106)], 7)
    return im


def map_arty():
    im, d = canvas()
    d.polygon([(14, 50), (44, 40), (84, 50), (114, 40), (114, 110), (84, 120), (44, 110), (14, 120)], outline=W, width=8)
    arc(d, (22, 8, 106, 110), 190, 330, 8)
    arrow_head(d, (99, 34), math.radians(60), 20)
    disc(d, (100, 80), 8)
    return im


def auto():
    im, d = canvas()
    for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        cx, cy = 64 + sx * 44, 64 + sy * 44
        line(d, [(cx, cy - sy * 22), (cx, cy), (cx - sx * 22, cy)], 9)
    # letter A
    line(d, [(42, 92), (64, 34), (86, 92)], 10); line(d, [(51, 72), (77, 72)], 9)
    return im


def reload_icon():
    im, d = canvas()
    arc(d, (16, 16, 112, 112), 200, 340, 11)
    arc(d, (16, 16, 112, 112), 20, 160, 11)
    arrow_head(d, (108, 52), math.radians(80), 26)
    arrow_head(d, (20, 76), math.radians(260), 26)
    d.rectangle((56, 56, 72, 86), fill=W)            # small round in the middle
    d.pieslice((56, 42, 72, 70), 180, 360, fill=W)
    return im


def radar():
    im, d = canvas()
    d.chord((8, 14, 92, 98), 135, 315, fill=W)          # dish (bowl facing up-right)
    d.chord((22, 4, 106, 88), 135, 315, fill=0)
    line(d, [(46, 58), (82, 24)], 7); disc(d, (84, 22), 8)
    line(d, [(50, 62), (50, 108)], 10); line(d, [(30, 112), (72, 112)], 10)
    arc(d, (64, 40, 120, 96), 300, 360, 7); arc(d, (78, 26, 124, 80), 300, 350, 6)
    return im


def lock():
    im, d = canvas()
    ring(d, (64, 64), 30, 9)
    disc(d, (64, 64), 9)
    for a in range(4):
        ang = a * math.pi / 2
        p0 = (64 + 40 * math.cos(ang), 64 + 40 * math.sin(ang))
        p1 = (64 + 60 * math.cos(ang), 64 + 60 * math.sin(ang))
        line(d, [p0, p1], 9)
    return im


def engine():
    im, d = canvas()
    d.rectangle((26, 40, 102, 96), outline=W, width=LW)
    d.rectangle((40, 22, 88, 40), fill=W)            # valve cover
    d.rectangle((56, 10, 72, 24), fill=W)
    d.rectangle((102, 56, 120, 80), fill=W)          # exhaust
    d.rectangle((8, 52, 26, 84), fill=W)             # fan
    line(d, [(42, 110), (86, 110)], 10)
    disc(d, (64, 68), 12)
    return im


def seat():
    im, d = canvas()
    line(d, [(40, 16), (46, 76), (96, 76)], 14)
    line(d, [(96, 76), (100, 112)], 10)
    line(d, [(50, 78), (46, 112)], 10)
    line(d, [(30, 40), (52, 40)], 8)
    return im


def hover():
    im, d = canvas()
    line(d, [(14, 30), (114, 30)], 8)                # rotor
    line(d, [(64, 30), (64, 44)], 8)
    d.ellipse((36, 42, 84, 80), fill=W)              # cabin
    line(d, [(80, 58), (118, 54)], 9)                # tail
    line(d, [(36, 88), (90, 88)], 6)                 # skids
    for x in (20, 108):                              # level marks
        line(d, [(x - 10, 110), (x + 10, 110)], 7)
    line(d, [(44, 110), (84, 110)], 7)
    return im


def flares():
    im, d = canvas()
    for c, r in (((34, 40), 14), ((92, 34), 12), ((60, 86), 16), ((100, 92), 10)):
        disc(d, c, r * 0.6)
        for a in range(8):
            ang = a * math.pi / 4
            line(d, [(c[0] + r * 0.7 * math.cos(ang), c[1] + r * 0.7 * math.sin(ang)),
                     (c[0] + r * 1.5 * math.cos(ang), c[1] + r * 1.5 * math.sin(ang))], 5)
    return im


def gear():
    im, d = canvas()
    line(d, [(64, 10), (64, 64)], 12)
    line(d, [(64, 30), (96, 12)], 8)
    ring(d, (64, 88), 28, 12)
    disc(d, (64, 88), 8)
    arrow_head(d, (24, 76), math.radians(90), 24)
    line(d, [(24, 30), (24, 64)], 8)
    return im


def zoom():
    im, d = canvas()
    ring(d, (54, 54), 36, 12)
    line(d, [(80, 80), (114, 114)], 18)
    line(d, [(54, 38), (54, 70)], 8); line(d, [(38, 54), (70, 54)], 8)
    return im


def look():
    im, d = canvas()
    d.chord((8, 30, 120, 130), 200, 340, outline=W, width=LW)
    d.chord((8, -2, 120, 98), 20, 160, outline=W, width=LW)
    disc(d, (64, 64), 20)
    disc(d, (64, 64), 8, 0)
    return im


def maneuver():
    im, d = canvas()
    plane_top(d, 52, 74, 0.8, math.radians(-30))
    arc(d, (40, 6, 122, 88), 250, 20, 9)
    arrow_head(d, (118, 62), math.radians(100), 24)
    return im


def flaps():
    im, d = canvas()
    d.polygon([(8, 50), (24, 40), (84, 44), (84, 64), (8, 60)], fill=W)       # wing section
    d.polygon([(90, 50), (120, 78), (112, 86), (88, 66)], fill=W)            # flap down
    arrow_head(d, (104, 108), math.radians(90), 22)
    line(d, [(104, 84), (104, 96)], 7)
    return im


def loadout():
    im, d = canvas()
    d.ellipse((20, 36, 92, 70), fill=W)                    # bomb body
    d.polygon([(10, 36), (26, 50), (26, 56), (10, 70)], fill=W)
    line(d, [(70, 88), (110, 118)], 12)                    # wrench
    ring(d, (64, 84), 14, 9)
    return im


def blow():
    im, d = canvas()
    for c, r in (((40, 90), 12), ((70, 70), 16), ((50, 44), 10), ((88, 36), 12), ((90, 100), 9)):
        ring(d, c, r, 7)
    line(d, [(20, 60), (20, 16)], 9); arrow_head(d, (20, 10), math.radians(-90), 24)
    line(d, [(110, 70), (110, 26)], 9); arrow_head(d, (110, 20), math.radians(-90), 24)
    return im


def flood():
    im, d = canvas()
    for k in range(3):
        y = 72 + k * 18
        pts = [(10 + i * 6, y + 6 * math.sin(i * 0.9)) for i in range(19)]
        d.line(pts, fill=W, width=7)
    line(d, [(64, 8), (64, 48)], 10); arrow_head(d, (64, 58), math.radians(90), 28)
    return im


def depth_hold():
    im, d = canvas()
    d.ellipse((10, 56, 112, 86), fill=W)                   # hull
    d.rectangle((50, 40, 72, 60), fill=W)                  # sail
    line(d, [(10, 22), (118, 22)], 6)
    line(d, [(10, 110), (118, 110)], 6)
    arrow_head(d, (24, 34), math.radians(-90), 18); arrow_head(d, (24, 98), math.radians(90), 18)
    return im


def periscope():
    im, d = canvas()
    line(d, [(70, 120), (70, 28), (40, 28)], 14)
    d.polygon([(20, 14), (44, 14), (44, 42), (20, 42)], fill=W)
    for k in range(3):
        y = 96 + k * 10
        d.line([(10 + i * 8, y + 4 * math.sin(i)) for i in range(14)], fill=W, width=5)
    return im


def brake():
    im, d = canvas()
    ring(d, (64, 64), 46, 12)
    ring(d, (64, 64), 20, 8)
    arc(d, (4, 4, 124, 124), 120, 240, 10)
    arc(d, (4, 4, 124, 124), 300, 60, 10)
    return im


def bay():
    im, d = canvas()
    d.rectangle((14, 20, 114, 36), fill=W)                 # fuselage floor
    d.polygon([(30, 36), (40, 36), (22, 66), (14, 62)], fill=W)   # doors open
    d.polygon([(88, 36), (98, 36), (114, 62), (106, 66)], fill=W)
    d.ellipse((46, 54, 82, 100), fill=W)                   # bomb
    d.polygon([(54, 98), (74, 98), (80, 118), (48, 118)], fill=W)
    return im


def nose_down():
    im, d = canvas()
    d.polygon([(10, 40), (80, 56), (112, 74), (80, 74), (10, 54)], fill=W)     # side view, nose down-right
    d.polygon([(10, 40), (22, 18), (32, 44)], fill=W)
    arc(d, (40, 30, 120, 120), 300, 30, 8)
    arrow_head(d, (112, 92), math.radians(110), 22)
    return im


def rudder():
    im, d = canvas()
    d.polygon([(52, 20), (76, 20), (80, 100), (48, 100)], fill=W)
    line(d, [(14, 64), (38, 64)], 9); arrow_head(d, (8, 64), math.radians(180), 22)
    line(d, [(90, 64), (114, 64)], 9); arrow_head(d, (120, 64), 0.0, 22)
    return im


def person():
    im, d = canvas()
    disc(d, (64, 24), 14)
    d.rounded_rectangle((44, 42, 84, 82), 10, fill=W)
    line(d, [(52, 82), (48, 118)], 12); line(d, [(76, 82), (80, 118)], 12)
    line(d, [(44, 48), (28, 76)], 10); line(d, [(84, 48), (100, 76)], 10)
    return im


def eye_off():
    im = look()
    d = ImageDraw.Draw(im)
    line(d, [(18, 110), (110, 18)], 12)
    return im


def yars_lock():
    im, d = canvas()
    d.polygon([(64, 6), (76, 26), (76, 96), (86, 112), (42, 112), (52, 96), (52, 26)], fill=W)
    for sx in (1, -1):
        line(d, [(64 + sx * 34, 30), (64 + sx * 52, 30), (64 + sx * 52, 52)], 8)
        line(d, [(64 + sx * 34, 120), (64 + sx * 52, 120), (64 + sx * 52, 98)], 8)
    return im


def telegraph():
    im, d = canvas()
    d.pieslice((10, 18, 118, 126), 180, 360, outline=W, width=LW)
    line(d, [(64, 72), (96, 34)], 10)
    disc(d, (64, 72), 10)
    line(d, [(64, 72), (64, 118)], 12)
    line(d, [(40, 120), (88, 120)], 10)
    return im


def engines():
    im = engine().resize((88, 88), Image.LANCZOS)
    out = Image.new("L", (S, S), 0)
    out.paste(im, (0, 0), im)
    out.paste(im, (40, 40), im)
    return out


GLYPHS = [smoke, sight, map_icon, map_arty, auto, reload_icon, radar, lock,
          engine, seat, hover, flares, gear, zoom, look, maneuver,
          flaps, loadout, blow, flood, depth_hold, periscope, brake, bay,
          nose_down, rudder, person, eye_off, yars_lock, telegraph, engines]


def main():
    sheet = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    for i, fn in enumerate(GLYPHS):
        a = fn().resize((32, 32), Image.LANCZOS)
        cell = Image.new("RGBA", (32, 32), (255, 255, 255, 0))
        cell.putalpha(a)
        sheet.paste(cell, ((i % 8) * 32, (i // 8) * 32))
    import os
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.save(OUT)
    # preview on dark background, 4x
    pv = Image.new("RGBA", (1024, 512), (24, 28, 32, 255))
    big = sheet.resize((1024, 512), Image.NEAREST)
    pv.alpha_composite(big)
    pv.convert("RGB").save("/tmp/actions_preview.png")
    print("actions.png", len(GLYPHS), "icons")


if __name__ == "__main__":
    main()
