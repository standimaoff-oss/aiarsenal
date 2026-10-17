import bpy, sys
def pose(clips, frame):
    for o in bpy.data.objects:
        if o.animation_data:
            for tr in o.animation_data.nla_tracks:
                tr.mute = tr.name not in clips
    bpy.context.scene.frame_set(frame)
