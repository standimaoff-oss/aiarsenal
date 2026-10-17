# r18: shell-hole decals for the WT-style damage model (DamageFx renderer). Two nodes, decal normal = glTF +Y
# (Blender +Z), modelled at the origin: hole_large (ship side plating, 1.7 m torn opening with petals bent inward)
# and hole_small (tank armour penetration, 0.36 m, splash of molten metal / soot). Saved as models/damage_fx.blend,
# exported to assets/aiarsenal/models/glb/damage_fx.glb.  usage: blender -b --factory-startup --python damage_fx.py
import bpy, bmesh, math, random, os
random.seed(18)
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.name = "DAMAGE_FX"
root = bpy.data.objects.new("root", None)
sc.collection.objects.link(root)


def mat(name, rgb, rough=0.8, metal=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = rgb + (1.0,)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    return m


M_VOID = mat("M_Void", (0.012, 0.010, 0.009), 1.0)
M_SOOT = mat("M_Soot", (0.045, 0.038, 0.032), 0.95)
M_SCORCH = mat("M_Scorch", (0.16, 0.10, 0.06), 0.9)
M_PETAL = mat("M_Petal", (0.30, 0.29, 0.28), 0.55, 0.8)
M_BRIGHT = mat("M_BareSteel", (0.55, 0.53, 0.50), 0.35, 1.0)


def hole(name, r, petals, depth, soot_r, scorch_r, seed):
    rnd = random.Random(seed)
    me = bpy.data.meshes.new(name)
    ob = bpy.data.objects.new(name, me)
    sc.collection.objects.link(ob)
    ob.parent = root
    for m in (M_VOID, M_SOOT, M_SCORCH, M_PETAL, M_BRIGHT):
        me.materials.append(m)
    bm = bmesh.new()
    n = petals * 2

    def ring(rad, z, jitter):
        out = []
        for i in range(n):
            a = 2 * math.pi * i / n
            rr = rad * (1.0 + rnd.uniform(-jitter, jitter))
            out.append(bm.verts.new((math.cos(a) * rr, math.sin(a) * rr, z)))
        return out

    def fan(c, ring_, mi):
        for i in range(len(ring_)):
            f = bm.faces.new((c, ring_[i], ring_[(i + 1) % len(ring_)]))
            f.material_index = mi

    def band(inner, outer, mi):
        for i in range(len(inner)):
            j = (i + 1) % len(inner)
            f = bm.faces.new((inner[i], outer[i], outer[j], inner[j]))
            f.material_index = mi

    # 1) the dark void: a recessed cone so you look INTO the hull, not at a flat black disc
    c = bm.verts.new((0, 0, -depth))
    rim = ring(r * 0.92, -0.004, 0.18)
    fan(c, [rim[i] for i in range(len(rim))][::-1], 0)
    # 2) petals: torn plate tongues bent inward around the opening (bare steel at the tips)
    for i in range(petals):
        a0 = 2 * math.pi * (i + rnd.uniform(-0.15, 0.15)) / petals
        w = math.pi / petals * rnd.uniform(0.55, 0.85)
        L = r * rnd.uniform(0.45, 0.8)
        bend = rnd.uniform(0.5, 1.1)
        p1 = bm.verts.new((math.cos(a0 - w) * r * 1.02, math.sin(a0 - w) * r * 1.02, 0.006))
        p2 = bm.verts.new((math.cos(a0 + w) * r * 1.02, math.sin(a0 + w) * r * 1.02, 0.006))
        rm = r - L * math.cos(bend)
        tip = bm.verts.new((math.cos(a0) * rm, math.sin(a0) * rm, -L * math.sin(bend)))
        mid = bm.verts.new((math.cos(a0) * (r - L * 0.45), math.sin(a0) * (r - L * 0.45), -L * 0.35 * math.sin(bend)))
        f = bm.faces.new((p1, mid, p2))
        f.material_index = 3
        f = bm.faces.new((p1, tip, mid))
        f.material_index = 4 if i % 2 else 3
        f = bm.faces.new((mid, tip, p2))
        f.material_index = 3
    # 3) soot ring + irregular scorch splash on the plating (slightly off the surface)
    s0 = ring(r * 1.0, 0.004, 0.10)
    s1 = ring(soot_r, 0.004, 0.30)
    band(s0, s1, 1)
    s2 = ring(scorch_r, 0.003, 0.42)
    band(s1, s2, 2)
    bm.normal_update()
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = False
    return ob


hole("hole_large", 0.85, 9, 1.6, 1.35, 2.1, 1)
hole("hole_small", 0.16, 7, 0.45, 0.30, 0.48, 2)
# Blender Z-up -> glTF Y-up is done by the exporter (export_yup)
out_glb = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb/damage_fx.glb"
os.makedirs(os.path.dirname(out_glb), exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath="/home/user/models/damage_fx.blend")
bpy.ops.export_scene.gltf(filepath=out_glb, export_format='GLB', export_yup=True, export_apply=True,
                          export_animations=False, export_extras=False, export_cameras=False, export_lights=False,
                          use_visible=False, use_selection=False)
print("DAMAGE_FX_DONE", os.path.getsize(out_glb))
