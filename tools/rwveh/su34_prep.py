# r19: unpack the packed images of su57.blend into TEXA (shared jet textures for su34.py) + grey placeholders for any
# other texture referenced by the material libraries (they stay unused by the Su-34)
import bpy, os, re, glob
TEXA = "/home/user/.cache/air/tex"
os.makedirs(TEXA, exist_ok=True)
bpy.ops.wm.open_mainfile(filepath="/home/user/models/su57.blend")
for img in bpy.data.images:
    p = os.path.join(TEXA, img.name + ".png")
    if os.path.exists(p):
        continue
    img.filepath_raw = p
    img.file_format = 'PNG'
    img.save()
    print("SAVED", img.name)
names = set()
for f in glob.glob("/home/user/tools/blender/*lib*.py") + glob.glob("/home/user/tools/rwveh/*_common.py") + ["/home/user/tools/rwveh/plane_lib.py"]:
    names |= set(re.findall(r'"(T_[A-Za-z0-9_]+)"', open(f, encoding="utf-8").read()))
n = 0
for nm in sorted(names):
    p = os.path.join(TEXA, nm + ".png")
    if not os.path.exists(p):
        im = bpy.data.images.new(nm + "_ph", 8, 8)
        im.pixels = [0.5, 0.5, 0.5, 1.0] * 64
        im.filepath_raw = p
        im.file_format = 'PNG'
        im.save()
        n += 1
print("PLACEHOLDERS", n)
