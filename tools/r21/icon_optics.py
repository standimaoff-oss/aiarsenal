#!/usr/bin/env python3
"""r21: optics icon (cell 31 of the action sheet) - binocular sight eyepiece with a half-moon (night channel)
and heat waves. Drawn with PIL by the agent, white on transparent like the other r20 icons."""
import math
from PIL import Image, ImageDraw

OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/gui/actions.png"
S, W, LW = 128, 255, 11
im = Image.new("L", (S, S), 0)
d = ImageDraw.Draw(im)
# eyepiece ring
d.ellipse((14, 30, 94, 110), outline=W, width=LW)
# crescent moon inside
d.ellipse((30, 46, 78, 94), fill=W)
d.ellipse((42, 40, 90, 88), fill=0)
# heat waves top-right
for k in range(3):
    x0 = 82 + k * 14
    pts = [(x0 + 5 * math.sin(t / 4.0), 10 + t * 2) for t in range(0, 26)]
    d.line(pts, fill=W, width=7, joint="curve")
small = im.resize((32, 32), Image.LANCZOS)
sheet = Image.open(OUT).convert("RGBA")
cell = Image.new("RGBA", (32, 32), (255, 255, 255, 0))
cell.putalpha(small)
idx = 31
x, y = (idx % 8) * 32, (idx // 8) * 32
sheet.paste((0, 0, 0, 0), (x, y, x + 32, y + 32))
sheet.paste(cell, (x, y))
sheet.save(OUT)
sheet.crop((x, y, x + 32, y + 32)).resize((128, 128), Image.NEAREST).save("/home/user/logs/icon_optics_preview.png")
print("ok", sheet.size)
