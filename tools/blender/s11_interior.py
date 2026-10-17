# STEP 11 (headless patch of the saved .blend): hollow cab shell + full cab interior + see-through glass.
# blender -b --factory-startup /home/user/models/bm21_grad.blend --python s11_interior.py
import bpy, bmesh, math, os
from mathutils import Vector, Matrix

exec(open("/home/user/tools/blender/lib.py").read())

sc = bpy.data.scenes["BM21_Grad"]
body = bpy.data.objects["SM_Body"]
bpy.context.view_layer.update()
for c in bpy.context.view_layer.layer_collection.children:
    c.exclude = False
G = "Interior"
CAB_Y0, CAB_Y1 = -1.80, -0.45
CY = (CAB_Y0 + CAB_Y1) / 2

# ---------------------------------------------------------------- 1. remove the solid CabLower block from SM_Body
if not body.get("s11_done"):
    bm = bmesh.new()
    bm.from_mesh(body.data)
    mw = body.matrix_world
    bm.verts.ensure_lookup_table()
    seen = set()
    removed = 0
    for v0 in bm.verts:
        if v0.index in seen:
            continue
        comp, stack = [], [v0]
        seen.add(v0.index)
        while stack:
            v = stack.pop()
            comp.append(v)
            for e in v.link_edges:
                o = e.other_vert(v)
                if o.index not in seen:
                    seen.add(o.index)
                    stack.append(o)
        ws = [mw @ v.co for v in comp]
        mn = Vector((min(p.x for p in ws), min(p.y for p in ws), min(p.z for p in ws)))
        mx = Vector((max(p.x for p in ws), max(p.y for p in ws), max(p.z for p in ws)))
        size, ctr = mx - mn, (mx + mn) / 2
        if (abs(size.x - 2.10) < 0.02 and abs(size.y - 1.35) < 0.02 and abs(size.z - 0.72) < 0.02
                and abs(ctr.z - 1.52) < 0.02 and abs(ctr.y - CY) < 0.02):
            bmesh.ops.delete(bm, geom=comp, context='VERTS')
            removed += 1
            break
    bm.to_mesh(body.data)
    bm.free()
    print("CabLower components removed:", removed)
    assert removed == 1, "CabLower not found"

# ---------------------------------------------------------------- 2. cab shell (5 cm sheet metal) - same outer envelope as before
T = 0.05
Z0, Z1 = 1.16, 1.88
ZC, ZH = (Z0 + Z1) / 2, Z1 - Z0
box("CabWallFront", (2.10, T, ZH), (0, CAB_Y0 + T / 2, ZC), "M_Paint_Green", G, bevel=0.015)
box("CabWallBack", (2.10, T, ZH), (0, CAB_Y1 - T / 2, ZC), "M_Paint_Green", G, bevel=0.015)
for s in (1, -1):
    box(f"CabDoorPanel{s}", (T, 1.35 - 2 * T, ZH), (s * (1.05 - T / 2), CY, ZC), "M_Paint_Green", G, bevel=0.015)
    # rolled door top edge / window sill + inner door trim
    box(f"DoorSill{s}", (0.09, 1.30, 0.035), (s * 1.005, CY, Z1 + 0.012), "M_Paint_Green", G, bevel=0.01)
    box(f"DoorTrim{s}", (0.012, 0.80, 0.40), (s * 0.994, -1.12, 1.55), "M_Paint_Worn", G, bevel=0.004)
    box(f"DoorInnerHandle{s}", (0.03, 0.10, 0.025), (s * 0.975, -0.80, 1.72), "M_Steel", G, bevel=0.006)
    box(f"DoorWinderBase{s}", (0.02, 0.05, 0.05), (s * 0.982, -1.25, 1.66), "M_Steel", G, bevel=0.005)
    box(f"DoorWinder{s}", (0.015, 0.14, 0.018), (s * 0.972, -1.30, 1.66), "M_Steel", G, bevel=0.004)
    cyl(f"DoorWinderKnob{s}", 0.014, 0.04, (s * 0.955, -1.36, 1.66), "M_Bore", G, axis=0, seg=8)
# floor mat on top of the existing CabFloor (z 1.19)
box("FloorMat", (1.96, 1.20, 0.012), (0, CY, 1.196), "M_Rubber", G, bevel=0.0)
# engine hump (Ural cab-behind-engine: gearbox tunnel between seats)
box("EngineHump", (0.50, 0.70, 0.20), (0.02, -1.42, 1.30), "M_Paint_Green", G, bevel=0.04, seg=2)
box("HumpCover", (0.46, 0.62, 0.015), (0.02, -1.42, 1.405), "M_Canvas", G, bevel=0.005)

# ---------------------------------------------------------------- 3. dashboard + instruments
DY = CAB_Y0 + T + 0.11          # dash centre (y)
box("Dashboard", (1.98, 0.22, 0.20), (0, DY, 1.80), "M_Paint_Green", G, bevel=0.02)
box("DashPad", (1.98, 0.26, 0.04), (0, DY + 0.01, 1.915), "M_Rubber", G, bevel=0.015)
box("DashUnder", (1.98, 0.10, 0.18), (0, CAB_Y0 + T + 0.05, 1.61), "M_Paint_Green", G, bevel=0.01)
FY = DY + 0.11                  # dash face (towards the crew)
box("GaugePanel", (0.56, 0.015, 0.17), (0.48, FY + 0.006, 1.80), "M_Bore", G, bevel=0.004)
for i, x in enumerate((0.28, 0.41, 0.55, 0.68)):
    r = 0.045 if i in (1, 2) else 0.032
    z = 1.81 if i in (1, 2) else 1.79
    cyl(f"GaugeRim{i}", r + 0.008, 0.012, (x, FY + 0.016, z), "M_Steel", G, axis=1, seg=16)
    cyl(f"GaugeFace{i}", r, 0.006, (x, FY + 0.022, z), "M_Lamp_Head", G, axis=1, seg=16, dirt=False)
    box(f"GaugeNeedle{i}", (0.004, 0.003, r * 0.85), (x + r * 0.2, FY + 0.027, z + r * 0.2), "M_Red", G,
        rot=(0, -0.8 + i * 0.5, 0), bevel=0.0, dirt=False)
for k in range(6):  # toggle switches + warning lamps on the centre panel
    x = -0.08 + k * 0.06
    box(f"Toggle{k}", (0.008, 0.03, 0.008), (x, FY + 0.02, 1.74), "M_Steel", G, rot=(0.5, 0, 0), bevel=0.0)
    cyl(f"TellTale{k}", 0.009, 0.01, (x, FY + 0.006, 1.84), "M_Lamp_Tail" if k % 2 else "M_Lamp_Head", G, axis=1, seg=8, dirt=False)
box("GloveBox", (0.40, 0.012, 0.13), (-0.50, FY + 0.006, 1.79), "M_Paint_Worn", G, bevel=0.004)
box("GloveBoxLatch", (0.05, 0.015, 0.02), (-0.50, FY + 0.014, 1.84), "M_Steel", G, bevel=0.003)
box("GrabBarBase", (0.02, 0.02, 0.02), (-0.72, FY + 0.01, 1.89), "M_Steel", G, bevel=0.0)
bm = bm_tube_path([(-0.78, FY, 1.88), (-0.76, FY + 0.06, 1.90), (-0.30, FY + 0.06, 1.90), (-0.28, FY, 1.88)], 0.012, 6)
make_obj("CommanderGrabBar", bm, "M_Steel", G, smooth=True)
# heater box + vent on the firewall
box("Heater", (0.30, 0.16, 0.18), (0.02, CAB_Y0 + T + 0.08, 1.40), "M_Paint_Worn", G, bevel=0.02)
for k in range(4):
    box(f"HeaterVent{k}", (0.24, 0.01, 0.012), (0.02, CAB_Y0 + T + 0.165, 1.35 + k * 0.03), "M_Bore", G, bevel=0.0)

# ---------------------------------------------------------------- 4. steering wheel (driver = +X), column, pedals, levers
WX = 0.48
WC = Vector((WX, -1.40, 1.93))            # wheel hub
n = Vector((0, 0.55, 0.835)).normalized()  # column axis (towards driver, steep bus-like column)
u = Vector((1, 0, 0))
v = n.cross(u).normalized()
RW = 0.21
ring = [WC + (u * math.cos(2 * PI * k / 28) + v * math.sin(2 * PI * k / 28)) * RW for k in range(29)]
make_obj("SteeringWheel", bm_tube_path(ring, 0.017, 8), "M_Rubber", G, smooth=True)
for k in range(3):
    a = PI / 2 + k * 2 * PI / 3
    tip = WC + (u * math.cos(a) + v * math.sin(a)) * (RW - 0.01)
    make_obj(f"WheelSpoke{k}", bm_tube_path([WC - n * 0.02, tip], 0.009, 6), "M_Steel", G, smooth=True)
make_obj("WheelHub", bm_tube_path([WC - n * 0.05, WC + n * 0.01], 0.04, 12), "M_Bore", G, smooth=True)
col_bot = WC - n * 0.42
make_obj("SteeringColumn", bm_tube_path([col_bot, WC - n * 0.04], 0.032, 10), "M_Bore", G, smooth=True)
box("ColumnBracket", (0.10, 0.06, 0.06), (WX, DY + 0.12, 1.86), "M_Frame", G, bevel=0.01)
for k, x in enumerate((0.30, 0.47, 0.62)):
    w = 0.07 if k < 2 else 0.10
    box(f"Pedal{k}", (w, 0.02, 0.09), (x, -1.58, 1.30), "M_Steel", G, rot=(0.55, 0, 0), bevel=0.004)
    make_obj(f"PedalArm{k}", bm_tube_path([(x, -1.60, 1.34), (x, -1.66, 1.52)], 0.01, 6), "M_Frame", G, smooth=True)
# gear lever + transfer-case levers on the hump
make_obj("GearLever", bm_tube_path([(0.12, -1.30, 1.41), (0.14, -1.22, 1.62), (0.14, -1.18, 1.70)], 0.011, 6), "M_Steel", G, smooth=True)
cyl("GearKnob", 0.028, 0.05, (0.14, -1.18, 1.72), "M_Bore", G, axis=2, seg=12, bevel=0.01)
box("GearBoot", (0.12, 0.12, 0.04), (0.12, -1.30, 1.425), "M_Rubber", G, bevel=0.015)
for k, x in enumerate((-0.05, 0.02)):
    make_obj(f"TransferLever{k}", bm_tube_path([(x, -1.25, 1.41), (x, -1.20, 1.58)], 0.009, 6), "M_Steel", G, smooth=True)
    cyl(f"TransferKnob{k}", 0.02, 0.035, (x, -1.20, 1.60), "M_Red" if k else "M_Bore", G, axis=2, seg=10)
make_obj("Handbrake", bm_tube_path([(0.27, -1.10, 1.30), (0.27, -1.18, 1.58)], 0.012, 6), "M_Steel", G, smooth=True)
cyl("HandbrakeGrip", 0.017, 0.07, (0.27, -1.185, 1.60), "M_Rubber", G, axis=2, seg=10)

# ---------------------------------------------------------------- 5. seats: driver (+X) and 2-place commander bench (-X)
def seat(name, x, w):
    box(f"{name}Base", (w - 0.08, 0.42, 0.22), (x, -1.00, 1.30), "M_Frame", G, bevel=0.01)
    box(f"{name}Cushion", (w, 0.50, 0.12), (x, -1.02, 1.46), "M_Canvas", G, bevel=0.035, seg=2)
    box(f"{name}Back", (w, 0.11, 0.52), (x, -0.64, 1.80), "M_Canvas", G, rot=(-0.14, 0, 0), bevel=0.035, seg=2)
    box(f"{name}BackFrame", (w - 0.04, 0.03, 0.46), (x, -0.575, 1.79), "M_Frame", G, rot=(-0.14, 0, 0), bevel=0.005)


seat("SeatDriver", 0.52, 0.52)
seat("SeatBench", -0.45, 0.92)
for s, x in ((1, 0.52), (-1, -0.45)):  # stitched seams on the cushions
    box(f"CushionSeam{s}", (0.012, 0.44, 0.005), (x, -1.02, 1.522), "M_Rubber", G, bevel=0.0, dirt=False)

# ---------------------------------------------------------------- 6. crew equipment
RY = CAB_Y1 - T - 0.13
box("RadioShelf", (0.52, 0.28, 0.02), (-0.56, RY, 1.95), "M_Frame", G, bevel=0.004)
box("RadioR123", (0.44, 0.24, 0.26), (-0.56, RY, 2.09), "M_Paint_Worn", G, bevel=0.012)
box("RadioFace", (0.40, 0.01, 0.22), (-0.56, RY - 0.125, 2.09), "M_Bore", G, bevel=0.0)
for k in range(4):
    cyl(f"RadioKnob{k}", 0.016, 0.03, (-0.72 + k * 0.10, RY - 0.14, 2.03), "M_Rubber", G, axis=1, seg=10)
cyl("RadioDial", 0.045, 0.01, (-0.46, RY - 0.132, 2.13), "M_Lamp_Head", G, axis=1, seg=16, dirt=False)
box("RadioScale", (0.12, 0.008, 0.035), (-0.66, RY - 0.132, 2.15), "M_Lamp_Head", G, bevel=0.0, dirt=False)
box("Headset", (0.16, 0.05, 0.03), (-0.56, RY, 2.235), "M_Rubber", G, bevel=0.01)
box("Handset", (0.05, 0.19, 0.04), (-0.26, RY - 0.02, 1.99), "M_Rubber", G, bevel=0.012)
cyl("Extinguisher", 0.055, 0.36, (0.86, -0.62, 1.40), "M_Red", G, axis=2, seg=14, bevel=0.01)
cyl("ExtinguisherHead", 0.025, 0.07, (0.86, -0.62, 1.615), "M_Steel", G, axis=2, seg=10)
box("ExtinguisherStrap", (0.13, 0.13, 0.02), (0.86, -0.62, 1.46), "M_Frame", G, bevel=0.004)
box("MapCase", (0.36, 0.05, 0.26), (0.20, RY + 0.08, 2.03), "M_Canvas", G, bevel=0.015)
box("FirstAidBox", (0.22, 0.10, 0.14), (-0.86, -0.62, 1.27), "M_Paint_Worn", G, bevel=0.01)
box("FirstAidCross", (0.08, 0.004, 0.022), (-0.86, -0.672, 1.28), "M_Red", G, bevel=0.0, dirt=False)
box("FirstAidCross2", (0.022, 0.004, 0.08), (-0.86, -0.672, 1.28), "M_Red", G, bevel=0.0, dirt=False)
box("RearViewMirror", (0.20, 0.02, 0.07), (0.0, CAB_Y0 + 0.12, 2.36), "M_Steel", G, rot=(0.2, 0, 0), bevel=0.01)
box("MirrorStem", (0.015, 0.06, 0.015), (0.0, CAB_Y0 + 0.08, 2.39), "M_Steel", G, bevel=0.0)
for s in (1, -1):
    box(f"SunVisor{s}", (0.36, 0.16, 0.012), (s * 0.48, CAB_Y0 + 0.16, 2.40), "M_Canvas", G, rot=(0.5, 0, 0), bevel=0.004)
    box(f"CabLamp{s}", (0.10, 0.06, 0.03), (s * 0.30, -0.62, 2.47), "M_Lamp_Head", G, bevel=0.01, dirt=False)
    # roof bows seen from inside
for k in range(4):
    y = -1.66 + k * 0.36
    secs = [(-1.0 + 2.0 * j / 8, y, 2.455 + 0.10 * math.sin(PI * j / 8)) for j in range(9)]
    make_obj(f"RoofBowIn{k}", bm_tube_path(secs, 0.012, 6), "M_Frame", G, smooth=True)

# ---------------------------------------------------------------- 7. vertex colour + join into SM_Body
new = [o for o in col_get("BUILD").objects if o.get("grp") == G]
uvname = body.data.uv_layers.active.name
for ob in new:
    me = ob.data
    if me.uv_layers:
        me.uv_layers[0].name = uvname
    else:
        me.uv_layers.new(name=uvname)
    ca = me.color_attributes.new("Col", 'BYTE_COLOR', 'CORNER')
    for poly in me.polygons:
        nz = poly.normal.z
        for li in poly.loop_indices:
            w = me.vertices[me.loops[li].vertex_index].co
            k = 1.0
            if ob.get("dirt", 1):
                if nz > 0.7 and w.z < 1.5:
                    k = 0.86  # boot dust on the floor/pedals
                if nz < -0.5:
                    k *= 0.7
            ca.data[li].color = (k, k * 0.97, k * 0.92, 1.0)
    me.color_attributes.active_color = ca
    for p in me.polygons:
        p.use_smooth = p.use_smooth
print("interior parts:", len(new))
bpy.ops.object.select_all(action='DESELECT')
for o in new:
    o.select_set(True)
body.select_set(True)
bpy.context.view_layer.objects.active = body
with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0], active_object=body,
                               selected_editable_objects=new + [body], selected_objects=new + [body]):
    bpy.ops.object.join()
body["s11_done"] = 1
print("SM_Body polys:", len(body.data.polygons), "materials:", [m.name for m in body.data.materials])

# ---------------------------------------------------------------- 8. glass: transparent (BLEND), slight blue-grey tint
g = bpy.data.materials["M_Glass"]
bsdf = g.node_tree.nodes.get("Principled BSDF")
bsdf.inputs["Alpha"].default_value = 0.32
g.surface_render_method = 'BLENDED'
g.use_backface_culling = False
print("glass alpha", bsdf.inputs["Alpha"].default_value, g.surface_render_method)

# seat markers: on top of the new cushions
SWAY_Z = 1.0
want = {"Seat_Driver": (0.52, -1.02, 1.52), "Seat_Commander": (-0.48, -1.02, 1.52), "FX_Exhaust": (-1.12, 1.12, 0.64),
        "FX_Headlight_L": (0.86, -3.30, 1.38), "FX_Headlight_R": (-0.86, -3.30, 1.38)}
for nme, w in want.items():
    bpy.data.objects[nme].location = Vector(w) - Vector((0, 0, SWAY_Z))
for nme in ("Launcher_Pack", "Wheel_FL", "Wheel_FR"):
    bpy.data.objects[nme].rotation_euler = (0, 0, 0)
bpy.context.view_layer.update()

OUT = "/home/user/mods/bm21grad/src/main/resources/assets/bm21grad/glb/bm21.glb"
with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
    sc.frame_set(0)
    bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', export_yup=True, export_apply=True,
                              export_animations=True, export_animation_mode='NLA_TRACKS', export_force_sampling=True,
                              export_vertex_color='ACTIVE', export_extras=False, export_cameras=False, export_lights=False,
                              export_image_format='AUTO', use_visible=False, use_selection=False, use_active_scene=True)
print("GLB bytes:", os.path.getsize(OUT))
for nme in ("Launcher_Pack", "Wheel_FL", "Wheel_FR"):
    bpy.data.objects[nme].rotation_euler = (0, 0, 0)
for nme, w in want.items():
    bpy.data.objects[nme].location = Vector(w) - Vector((0, 0, SWAY_Z))
bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath, compress=True, copy=False)
b1 = bpy.data.filepath + "1"
if os.path.exists(b1):
    os.remove(b1)
print("saved", bpy.data.filepath, os.path.getsize(bpy.data.filepath))
