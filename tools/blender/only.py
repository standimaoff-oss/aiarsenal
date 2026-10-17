# usage: set ONLY = ["ROOT"] before exec; hides all other roots (viewport + render)
import bpy
def _desc(o):
    out = [o]
    for c in o.children:
        out += _desc(c)
    return out
roots = [o for o in bpy.data.objects if o.parent is None]
for r in roots:
    vis = r.name in ONLY
    for o in _desc(r):
        o.hide_set(not vis)
        o.hide_render = bool(not vis)
