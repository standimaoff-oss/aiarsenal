# STEP 1: clean scene, units, materials (PBR, texture x vertex-colour, packed images)
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
for c in list(bpy.data.collections):
    bpy.data.collections.remove(c)
for m in list(bpy.data.meshes):
    bpy.data.meshes.remove(m)
for m in list(bpy.data.materials):
    bpy.data.materials.remove(m)
for i in list(bpy.data.images):
    bpy.data.images.remove(i)
for a in list(bpy.data.actions):
    bpy.data.actions.remove(a)

sc = bpy.context.scene
sc.unit_settings.system = 'METRIC'
sc.unit_settings.scale_length = 1.0
sc.render.fps = 24
sc.frame_start = 0
sc.frame_end = 96
try:
    sc.view_settings.view_transform = 'AgX'
except Exception:
    pass


def make_mat(name, tex, rough=0.6, metal=0.0, factor=(1, 1, 1, 1), emissive=None, alpha_clip=False, vcol=True, double=False):
    m = bpy.data.materials.new(name)
    nt = m.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    out = nt.nodes.get("Material Output")
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metal
    src = None
    if tex:
        img = bpy.data.images.get(tex) or bpy.data.images.load(os.path.join(TEX, tex + ".png"))
        img.name = tex
        ti = nt.nodes.new("ShaderNodeTexImage")
        ti.image = img
        ti.location = (-700, 200)
        src = ti.outputs[0]
        if alpha_clip:
            gt = nt.nodes.new("ShaderNodeMath")
            gt.operation = 'GREATER_THAN'
            gt.inputs[1].default_value = 0.5
            nt.links.new(ti.outputs[1], gt.inputs[0])
            nt.links.new(gt.outputs[0], bsdf.inputs["Alpha"])
    if factor != (1, 1, 1, 1):
        mf = nt.nodes.new("ShaderNodeMix")
        mf.data_type = 'RGBA'; mf.blend_type = 'MULTIPLY'; mf.inputs[0].default_value = 1.0
        mf.location = (-450, 200)
        mf.inputs[7].default_value = factor
        if src is not None:
            nt.links.new(src, mf.inputs[6])
        else:
            mf.inputs[6].default_value = (1, 1, 1, 1)
        src = mf.outputs[2]
    if vcol:
        vc = nt.nodes.new("ShaderNodeVertexColor")
        vc.layer_name = "Col"
        vc.location = (-450, -100)
        mx = nt.nodes.new("ShaderNodeMix")
        mx.data_type = 'RGBA'; mx.blend_type = 'MULTIPLY'; mx.inputs[0].default_value = 1.0
        mx.location = (-220, 100)
        if src is not None:
            nt.links.new(src, mx.inputs[6])
        nt.links.new(vc.outputs[0], mx.inputs[7])
        src = mx.outputs[2]
    if src is not None:
        nt.links.new(src, bsdf.inputs["Base Color"])
    else:
        bsdf.inputs["Base Color"].default_value = factor
    if emissive:
        bsdf.inputs["Emission Color"].default_value = emissive
        bsdf.inputs["Emission Strength"].default_value = 1.0
    if alpha_clip:
        m.surface_render_method = 'DITHERED'
    m.use_backface_culling = not double
    return m


make_mat("M_Paint_Green", "T_Paint_BC", 0.62)
make_mat("M_Paint_Worn", "T_PaintWorn_BC", 0.7)
make_mat("M_Paint_Olive2", "T_Paint_BC", 0.66, factor=(0.93, 0.95, 0.86, 1))   # replaced panels, slightly different batch
make_mat("M_Frame", "T_Frame_BC", 0.8, 0.2)
make_mat("M_Steel", "T_Steel_BC", 0.45, 0.8)
make_mat("M_Bore", "T_Steel_BC", 0.7, 0.5, factor=(0.22, 0.21, 0.2, 1))
make_mat("M_Canvas", "T_Canvas_BC", 0.9, double=True)
make_mat("M_Tyre", "T_Tyre_BC", 0.9)
make_mat("M_Rubber", "T_Rubber_BC", 0.85)
make_mat("M_Glass", "T_Glass_BC", 0.1, vcol=False)
make_mat("M_Lamp_Head", "T_Lamps_BC", 0.2, vcol=False, emissive=(1.0, 0.95, 0.8, 1))
make_mat("M_Lamp_Tail", "T_Lamps_BC", 0.3, vcol=False, emissive=(0.9, 0.1, 0.05, 1))
make_mat("M_Decal", "T_Decals_BC", 0.7, alpha_clip=True, vcol=False, double=True)
make_mat("M_Rocket", "T_Rocket_BC", 0.55, 0.2)
make_mat("M_Rocket_Band", None, 0.6, factor=(0.75, 0.10, 0.08, 1), vcol=False)
make_mat("M_Wood", "T_Wood_BC", 0.8)
make_mat("M_Jerrycan", "T_Paint_BC", 0.6, factor=(0.80, 0.86, 0.70, 1))
make_mat("M_Brass", None, 0.35, 1.0, factor=(0.78, 0.60, 0.28, 1), vcol=False)
make_mat("M_Red", None, 0.5, factor=(0.60, 0.07, 0.05, 1), vcol=False)

col_get("BUILD")
print("materials:", len(bpy.data.materials), "images:", len(bpy.data.images))
