"""Crop an RGBA icon to its alpha bbox, pad square (6 %), resize to N px. usage: icon_fit.py file.png [N]"""
import sys
from PIL import Image
p = sys.argv[1]; N = int(sys.argv[2]) if len(sys.argv) > 2 else 64
im = Image.open(p).convert("RGBA")
bb = im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
im = im.crop(bb)
s = int(max(im.size) * 1.06)
c = Image.new("RGBA", (s, s), (0, 0, 0, 0))
c.paste(im, ((s - im.size[0]) // 2, (s - im.size[1]) // 2))
c.resize((N, N), Image.LANCZOS).save(p)
print("icon", p, bb)
