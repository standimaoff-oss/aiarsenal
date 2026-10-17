"""r19 Su-34 skins (system python3 + numpy/PIL):
  T_JetSu34Top - standard 'sea' two-tone scheme: grey-blue base with large hard-edged darker blue-grey blotches
  T_JetSu34Bot - light blue-grey underside
Reuses the helper defs of jet_textures.py (fbm / seams / weather / save)."""
import ast, os
HERE = os.path.dirname(os.path.abspath(__file__))
def _defs(fn):
    src = open(os.path.join(HERE, fn), encoding="utf-8").read()
    keep = [n for n in ast.parse(src).body if isinstance(n, (ast.Import, ast.ImportFrom, ast.FunctionDef))
            or (isinstance(n, ast.Assign) and all(isinstance(t, ast.Name) and t.id.isupper() for t in n.targets))]
    exec(compile(ast.Module(body=keep, type_ignores=[]), fn + "_defs", "exec"), globals())
for _f in ("plane_textures.py", "air_textures.py", "jet_textures.py"):
    _defs(_f)
import numpy as np

TOP_BASE, TOP_DARK, TOP_LIGHT = (146, 160, 174), (92, 110, 132), (176, 186, 196)
BELLY = (184, 196, 208)

def blotch_camo(name, base, tones, seed, thr=(0.10, 0.40), scale=40.0):
    img = np.zeros((S, S, 3), np.float32)
    img[:] = base
    for i, (col, t) in enumerate(zip(tones, thr)):
        f = fbm(S, S, scale, 3, seed + 11 * i, aspect=(1.0, 1.5))
        m = np.clip((f - t) * 90.0, 0, 1)             # hard edge, 1-2 px antialias
        img = img * (1 - m[..., None]) + np.array(col, np.float32) * m[..., None]
    img = seams(img, seed + 1, dark=0.88, light=1.03, frame=0.6, stringer=0.8, rivets=False)
    img = weather(img, seed + 2, grime=0.06, chips=0.0)
    save(img, name)

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    blotch_camo("T_JetSu34Top.png", TOP_BASE, (TOP_DARK, TOP_LIGHT), 341)
    blotch_camo("T_JetSu34Bot.png", BELLY, (TOP_BASE,), 342, thr=(0.55,))
