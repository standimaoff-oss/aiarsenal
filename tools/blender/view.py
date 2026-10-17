# viewport helper: frame all, solid/material view from a given angle
import bpy, math
from mathutils import Vector, Euler, Quaternion
def set_view(yaw_deg=35, pitch_deg=20, dist=11, target=(0,0.3,1.4), shading='MATERIAL', persp=True):
    for area in bpy.context.screen.areas:
        if area.type == 'VIEW_3D':
            sp = area.spaces.active
            if shading == 'TEX':
                sp.shading.type = 'SOLID'; sp.shading.color_type = 'TEXTURE'; sp.shading.light = 'STUDIO'
            else:
                sp.shading.type = shading
                if shading == 'SOLID': sp.shading.color_type = 'MATERIAL'
            sp.shading.show_backface_culling = False
            sp.overlay.show_overlays = False
            r3d = sp.region_3d
            r3d.view_perspective = 'PERSP' if persp else 'ORTHO'
            r3d.view_location = Vector(target)
            r3d.view_distance = dist
            q = Euler((math.radians(90 - pitch_deg), 0, math.radians(yaw_deg)), 'XYZ').to_quaternion()
            r3d.view_rotation = q
            sp.lens = 50
    # maximise 3D view area
