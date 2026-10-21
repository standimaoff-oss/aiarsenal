#!/usr/bin/env python3
"""Reproducible textures for the AI soldier units (tools convention: byte-identical output).

* skin_steve.png            64x64  base soldier skin in the classic Steve palette
                            (the player's own head and body shape is the base).
* uniform_<class>.png       64x64  transparent uniform overlay drawn OVER the skin
                            (tunic, sleeves, trousers, boots, headgear per class).
* egg_<class>.png           16x16  summon-egg item icons, one colour per class.

Run:  python3 tools/make_soldier_assets.py
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'mods/aiarsenal/src/main/resources/assets/aiarsenal/textures'


def write_png(path: Path, w: int, h: int, px):
    """px: list of rows, each row = list of (r,g,b,a). 8-bit RGBA, byte-identical."""
    def chunk(tag: bytes, data: bytes) -> bytes:
        out = struct.pack('>I', len(data)) + tag + data
        return out + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b''.join(b'\x00' + bytes(c for p in row for c in p) for row in px)
    data = (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


# --------------------------------------------------------------------------- skin
# Classic Steve palette.
HAIR = (92, 64, 41, 255)
HAIR_DARK = (66, 45, 29, 255)
SKIN = (196, 148, 104, 255)
SKIN_SHADE = (178, 128, 88, 255)
WHITE = (255, 255, 255, 255)
EYE = (70, 110, 170, 255)
BROW = (78, 54, 34, 255)
MOUTH = (120, 84, 58, 255)
SHIRT = (0, 150, 145, 255)
SHIRT_DARK = (0, 124, 120, 255)
PANTS = (80, 60, 130, 255)
PANTS_DARK = (66, 49, 108, 255)
SHOES = (110, 110, 110, 255)


def blank(w, h, color=(0, 0, 0, 0)):
    return [[list(color) for _ in range(w)] for _ in range(h)]


def fill(img, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y][x] = list(color)


# --- the vanilla 64x64 skin atlas (post-1.8 layout) ---------------------------
# Every cuboid unwraps to: right | front | left | back in one row, top | bottom above.
HEAD = (0, 0, 8, 8, 8)        # 8x8x8
BODY = (16, 16, 8, 12, 4)     # 8x12x4
RIGHT_ARM = (40, 16, 4, 12, 4)
LEFT_ARM = (32, 48, 4, 12, 4)
RIGHT_LEG = (0, 16, 4, 12, 4)
LEFT_LEG = (16, 48, 4, 12, 4)


def faces(box):
    """(x, y, w, h, d) -> dict of 6 face rectangles, vanilla skin layout."""
    x, y, w, h, d = box
    return {
        'right': (x, y + d, d, h),
        'front': (x + d, y + d, w, h),
        'left': (x + d + w, y + d, d, h),
        'back': (x + 2 * d + w, y + d, w, h),
        'top': (x + d, y, w, d),
        'bottom': (x + d + w, y, w, d),
    }


def poly(img, rect, color):
    fill(img, rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], color)


def skin_steve():
    """64x64 humanoid skin in the classic Steve palette (player head/body shape)."""
    img = blank(64, 64)

    # ---- head: skin everywhere, hair on top/sides/back, Steve face on the front
    for rect in faces(HEAD).values():
        poly(img, rect, SKIN)
    head = faces(HEAD)
    poly(img, head['top'], HAIR)
    poly(img, head['bottom'], SKIN_SHADE)
    for side in ('right', 'left', 'back'):
        rx, ry, rw, rh = head[side]
        fill(img, rx, ry, rx + rw, ry + 3, HAIR)              # hair on the upper rows
    fx, fy, fw, fh = head['front']                            # 8x8 face at (8,8)
    fill(img, fx, fy, fx + fw, fy + 2, HAIR)                  # fringe
    fill(img, fx + 1, fy + 2, fx + 3, fy + 4, WHITE)          # left eye white
    fill(img, fx + 5, fy + 2, fx + 7, fy + 4, WHITE)          # right eye white
    fill(img, fx + 1, fy + 2, fx + 2, fy + 4, EYE)            # pupils
    fill(img, fx + 5, fy + 2, fx + 6, fy + 4, EYE)
    fill(img, fx + 1, fy + 1, fx + 3, fy + 2, BROW)
    fill(img, fx + 5, fy + 1, fx + 7, fy + 2, BROW)
    fill(img, fx + 3, fy + 4, fx + 5, fy + 5, SKIN_SHADE)     # nose
    fill(img, fx + 3, fy + 6, fx + 5, fy + 7, MOUTH)          # mouth
    fill(img, fx, fy + 7, fx + fw, fy + 8, SKIN_SHADE)        # chin shade

    # ---- body: teal shirt with a slightly darker collar/hem
    body = faces(BODY)
    for name, rect in body.items():
        poly(img, rect, SHIRT)
    rx, ry, rw, rh = body['front']
    fill(img, rx, ry, rx + rw, ry + 2, SHIRT_DARK)
    fill(img, rx, ry + rh - 2, rx + rw, ry + rh, SHIRT_DARK)

    # ---- arms: shirt sleeve on the top 4 rows, bare skin (Steve hands) below
    for box in (RIGHT_ARM, LEFT_ARM):
        arm = faces(box)
        for name, rect in arm.items():
            poly(img, rect, SKIN)
        for side in ('front', 'back', 'left', 'right'):
            ax, ay, aw, ah = arm[side]
            fill(img, ax, ay, ax + aw, ay + 4, SHIRT)
        ax, ay, aw, ah = arm['front']
        fill(img, ax, ay + 4, ax + aw, ay + 5, SHIRT_DARK)    # cuff
        poly(img, arm['top'], SHIRT)

    # ---- legs: trousers with shoes at the bottom
    for box in (RIGHT_LEG, LEFT_LEG):
        leg = faces(box)
        for name, rect in leg.items():
            poly(img, rect, PANTS)
        for side in ('front', 'back', 'left', 'right'):
            lx, ly, lw, lh = leg[side]
            fill(img, lx, ly + 9, lx + lw, ly + lh, SHOES)    # shoes
        poly(img, leg['bottom'], SHOES)
        lx, ly, lw, lh = leg['front']
        fill(img, lx, ly, lx + lw, ly + 1, PANTS_DARK)

    return img


# --------------------------------------------------------------------------- uniforms
# Overlay palette per class: tunic / trim / trousers / boots / headgear
UNIFORMS = {
    'commander': dict(
        tunic=(168, 158, 112, 255), trim=(96, 88, 58, 255),
        pants=(120, 116, 88, 255), boots=(70, 60, 45, 255),
        hat=(64, 68, 74, 255), hat_brim=(38, 40, 46, 255)),
    'tanker': dict(
        tunic=(94, 100, 58, 255), trim=(74, 78, 46, 255),
        pants=(88, 94, 54, 255), boots=(85, 70, 50, 255),
        hat=(110, 116, 86, 255), hat_brim=(92, 96, 72, 255)),
    'assault': dict(
        tunic=(109, 111, 94, 255), trim=(86, 88, 74, 255),
        pants=(96, 98, 84, 255), boots=(70, 66, 54, 255),
        hat=(86, 90, 70, 255), hat_brim=(70, 74, 58, 255)),
    'medic': dict(
        tunic=(232, 232, 228, 255), trim=(200, 200, 194, 255),
        pants=(224, 224, 218, 255), boots=(160, 160, 156, 255),
        hat=(240, 240, 236, 255), hat_brim=(206, 206, 200, 255)),
}


def uniform(class_id: str):
    """Transparent overlay: tunic, sleeves, trousers, boots and a helmet.  The face
    (rows below the helmet brim) and the hands stay transparent, so the Steve skin
    underneath provides the head and the hands."""
    c = UNIFORMS[class_id]
    img = blank(64, 64)

    # tunic over the whole torso
    body = faces(BODY)
    for side in ('front', 'back', 'left', 'right'):
        poly(img, body[side], c['tunic'])
    poly(img, body['top'], c['tunic'])
    bx, by, bw, bh = body['front']
    fill(img, bx, by + bh - 2, bx + bw, by + bh, c['trim'])   # hem
    if class_id == 'commander':
        fill(img, bx, by + 7, bx + bw, by + 9, c['trim'])     # belt

    # sleeves (hands stay skin)
    for box in (RIGHT_ARM, LEFT_ARM):
        arm = faces(box)
        for side in ('front', 'back', 'left', 'right'):
            ax, ay, aw, ah = arm[side]
            fill(img, ax, ay, ax + aw, ay + 6, c['tunic'])
            fill(img, ax, ay + 5, ax + aw, ay + 6, c['trim'])
        poly(img, arm['top'], c['tunic'])

    # trousers + boots
    for box in (RIGHT_LEG, LEFT_LEG):
        leg = faces(box)
        for side in ('front', 'back', 'left', 'right'):
            lx, ly, lw, lh = leg[side]
            fill(img, lx, ly, lx + lw, ly + 9, c['pants'])
            fill(img, lx, ly + 9, lx + lw, ly + lh, c['boots'])
        poly(img, leg['top'], c['pants'])
        poly(img, leg['bottom'], c['boots'])

    # helmet: whole top plus the upper 3 rows of every side, face rows stay clear
    head = faces(HEAD)
    poly(img, head['top'], c['hat'])
    for side in ('right', 'left', 'back'):
        rx, ry, rw, rh = head[side]
        fill(img, rx, ry, rx + rw, ry + 3, c['hat'])
    fx, fy, fw, fh = head['front']
    fill(img, fx, fy, fx + fw, fy + 2, c['hat'])              # crown over the fringe
    fill(img, fx, fy + 1, fx + fw, fy + 2, c['hat_brim'])     # brim shade

    if class_id == 'medic':
        fill(img, fx + 3, fy, fx + 5, fy + 2, (198, 46, 46, 255))    # red cross on the helmet
        fill(img, fx + 2, fy, fx + 6, fy + 1, (198, 46, 46, 255))
        ax, ay, aw, ah = faces(LEFT_ARM)['front']                    # armband
        fill(img, ax, ay + 2, ax + aw, ay + 4, (198, 46, 46, 255))
    return img


# --------------------------------------------------------------------------- eggs
EGG_COLORS = {
    'commander': (176, 166, 118, 255),
    'tanker': (122, 132, 82, 255),
    'assault': (146, 150, 124, 255),
    'medic': (238, 238, 232, 255),
}


def egg(class_id: str):
    base = EGG_COLORS[class_id]
    dark = tuple(max(0, int(v * 0.62)) for v in base[:3]) + (255,)
    light = tuple(min(255, int(v * 0.6 + 92)) for v in base[:3]) + (255,)
    img = blank(16, 16)
    # egg: 8 wide, 10 tall, centred (x 4..12, y 3..13), pointier at the top
    for y in range(3, 14):
        t = (y - 3) / 9.0
        hw = 3.4 + 1.2 * (t ** 0.7)  # half width
        x0, x1 = 8 - hw, 8 + hw
        for x in range(int(x0), int(x1) + 1):
            if not (4 <= x < 12 and 3 <= y < 14):
                continue
            if abs(x - 8) > hw:
                continue
            col = base
            if y == 13 or abs(x - 8) >= hw - 0.2:
                col = dark
            elif y <= 4 and x <= 7:
                col = light
            img[y][x] = list(col)
    if class_id == 'medic':
        fill(img, 7, 8, 9, 11, (198, 46, 46, 255))
        fill(img, 8, 7, 9, 12, (198, 46, 46, 255))
    return img


def main():
    ent = OUT / 'entity/soldier'
    write_png(ent / 'skin_steve.png', 64, 64, skin_steve())
    for cls in UNIFORMS:
        write_png(ent / f'uniform_{cls}.png', 64, 64, uniform(cls))
    for cls in EGG_COLORS:
        write_png(OUT / 'item' / f'egg_{cls}.png', 16, 16, egg(cls))
    print('Soldier textures written to', ent)


if __name__ == '__main__':
    main()
