# STEP 5: vertex colour weathering (story-driven), lamp UVs, join per animated node, pivots + hierarchy, markers
YAW_P = Vector((0.0, 2.15, 1.29))
PITCH_P = Vector((0.0, 2.62, 2.45))
TUBE_Y0, TUBE_L = 0.17, 3.00
SHIELD_P = Vector((0.0, -1.82, 2.58))

build = col_get("BUILD")


def clamp01(x):
    return 0.0 if x < 0 else (1.0 if x > 1 else x)


def hash3(v):
    return (math.sin(v.x * 12.9898 + v.y * 78.233 + v.z * 37.719) * 43758.5453) % 1.0


# ---------------------------------------------------------------- 1. weathering into colour attribute "Col"
for ob in list(build.objects):
    me = ob.data
    if me is None or me.color_attributes.get("Col"):
        continue
    ca = me.color_attributes.new("Col", 'BYTE_COLOR', 'CORNER')
    mw = ob.matrix_world
    dirt = ob.get("dirt", 1)
    is_tube = ob.get("tube", 0)
    grp = ob.get("grp", "")
    for poly in me.polygons:
        nz = (mw.to_3x3() @ poly.normal).normalized().z
        for li in poly.loop_indices:
            v = me.vertices[me.loops[li].vertex_index].co
            w = mw @ v
            r = g = b = 1.0
            if dirt:
                # mud/dust: thrown up by the wheels -> strongest low, fading to ~1.1 m; more on horizontal ledges
                mud = clamp01((1.10 - w.z) / 0.85) ** 1.2 * 0.75
                if nz > 0.7 and w.z < 1.5:
                    mud = max(mud, 0.30)  # dust settles on low horizontal surfaces
                if nz > 0.7:
                    mud += 0.12  # every top surface collects some dust
                mud = clamp01(mud + (hash3(w) - 0.5) * 0.12)
                r, g, b = (1 - mud * 0.05, 1 - mud * 0.16, 1 - mud * 0.38)
                # cavity / underside occlusion
                if nz < -0.5:
                    r, g, b = r * 0.62, g * 0.62, b * 0.62
                # rocket exhaust soot: rear deck behind the tube pack, and the pack's rear ends
                if grp in ("Body", "Turret_Yaw") and w.y > 2.7 and nz > 0.3:
                    s = clamp01((w.y - 2.7) / 0.9) * 0.55
                    r, g, b = r * (1 - s), g * (1 - s), b * (1 - s)
                if grp == "Launcher_Pack" and w.y > TUBE_Y0 + TUBE_L - 0.35:
                    s = clamp01((w.y - (TUBE_Y0 + TUBE_L - 0.35)) / 0.35) * 0.6
                    r, g, b = r * (1 - s), g * (1 - s), b * (1 - s)
            if is_tube:
                # inside of the bore: burnt black; muzzle ring: light soot
                cx = round((w.x + 0.711) / 0.158) * 0.158 - 0.711
                rows = [2.55 + j * 0.165 for j in range(4)]
                cz = min(rows, key=lambda z: abs(z - w.z))
                rad = math.hypot(w.x - cx, w.z - cz)
                if rad < 0.064:
                    r, g, b = 0.09, 0.085, 0.08
                elif w.y < TUBE_Y0 + 0.05:
                    r, g, b = r * 0.75, g * 0.75, b * 0.75
            ca.data[li].color = (r, g, b, 1.0)
    me.color_attributes.active_color = ca

# ---------------------------------------------------------------- 2. lamp / decal UV remap into atlas quadrants
QUAD = {"head": (0.0, 0.5), "tail": (0.5, 0.5), "amber": (0.0, 0.0)}
for ob in build.objects:
    mats = [m.name for m in ob.data.materials]
    if not mats or mats[0] not in ("M_Lamp_Head", "M_Lamp_Tail"):
        continue
    q = "head"
    if mats[0] == "M_Lamp_Tail":
        q = "amber" if ob.name.startswith("TurnLens") else "tail"
    me = ob.data
    xs = [v.co for v in me.vertices]
    mn = Vector((min(c.x for c in xs), min(c.y for c in xs), min(c.z for c in xs)))
    mx = Vector((max(c.x for c in xs), max(c.y for c in xs), max(c.z for c in xs)))
    ext = mx - mn
    axes = sorted(range(3), key=lambda i: -ext[i])[:2]
    uvl = me.uv_layers.active or me.uv_layers.new()
    for li, loop in enumerate(me.loops):
        c = me.vertices[loop.vertex_index].co
        u = (c[axes[0]] - mn[axes[0]]) / max(ext[axes[0]], 1e-6)
        v = (c[axes[1]] - mn[axes[1]]) / max(ext[axes[1]], 1e-6)
        uvl.data[li].uv = (QUAD[q][0] + 0.02 + u * 0.46, QUAD[q][1] + 0.02 + v * 0.46)

# ---------------------------------------------------------------- 3. join per group
def join_group(grp, name):
    obs = [o for o in build.objects if o.get("grp") == grp]
    if not obs:
        return None
    bpy.ops.object.select_all(action='DESELECT')
    for o in obs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = obs[0]
    if len(obs) > 1:
        bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active
    ob.name = name
    ob.data.name = name
    return ob


# make BUILD visible in view layer for operators
for c in bpy.context.view_layer.layer_collection.children:
    c.exclude = False

groups = {"Running": "SM_Running", "Body": "SM_Body", "Shield": "Shield_Cab", "Turret_Yaw": "Turret_Yaw",
          "Launcher_Pitch": "Launcher_Pitch", "Launcher_Pack": "Launcher_Pack"}
for w in ("FL", "FR", "ML", "MR", "RL", "RR"):
    groups["Wheel_" + w] = "Wheel_" + w
joined = {}
for g, n in groups.items():
    joined[g] = join_group(g, n)
    print("joined", n, len(joined[g].data.polygons) if joined[g] else 0)

# smooth by angle (hard-surface: 35 deg)
bpy.ops.object.select_all(action='DESELECT')
for ob in joined.values():
    ob.select_set(True)
bpy.context.view_layer.objects.active = joined["Body"]
bpy.ops.object.shade_smooth_by_angle(angle=math.radians(35))

# ---------------------------------------------------------------- 4. pivots + hierarchy
def set_pivot(ob, p):
    p = Vector(p)
    ob.data.transform(Matrix.Translation(-p))
    ob.location = p


def new_empty(name, loc, parent=None, size=0.2):
    e = bpy.data.objects.get(name) or bpy.data.objects.new(name, None)
    if e.name not in build.objects:
        build.objects.link(e)
    e.empty_display_size = size
    # NB: matrix_world of a freshly parented object is stale until depsgraph update -> use known rest positions
    e.location = Vector(loc) - (parent_world[parent.name] if parent else Vector())
    e.parent = parent
    return e


def parent_keep(ob, parent):
    wp = ob.location.copy()
    ob.parent = parent
    ob.matrix_parent_inverse = Matrix.Identity(4)
    ob.location = wp - parent_world[parent.name]


root = new_empty("BM21", (0, 0, 0), None, 1.0)
parent_world = {"BM21": Vector((0, 0, 0))}
sway = new_empty("Body_Sway", (0, 0, 1.0), root, 0.5)
parent_world["Body_Sway"] = Vector((0, 0, 1.0))

set_pivot(joined["Running"], (0, 0, 0)); parent_keep(joined["Running"], root)
set_pivot(joined["Body"], (0, 0, 1.0)); parent_keep(joined["Body"], sway)
set_pivot(joined["Shield"], SHIELD_P); parent_keep(joined["Shield"], sway)
set_pivot(joined["Turret_Yaw"], YAW_P); parent_keep(joined["Turret_Yaw"], sway)
parent_world["Turret_Yaw"] = YAW_P
set_pivot(joined["Launcher_Pitch"], PITCH_P); parent_keep(joined["Launcher_Pitch"], joined["Turret_Yaw"])
parent_world["Launcher_Pitch"] = PITCH_P
set_pivot(joined["Launcher_Pack"], PITCH_P); parent_keep(joined["Launcher_Pack"], joined["Launcher_Pitch"])
parent_world["Launcher_Pack"] = PITCH_P

for tag, y in AXLES.items():
    for side, sn in ((1, "L"), (-1, "R")):
        w = joined["Wheel_" + tag + sn]
        p = Vector((side * TRACK / 2, y, ZW))
        set_pivot(w, p)
        if tag == "F":
            st = new_empty("Steer_F" + sn, p, root, 0.3)
            parent_world[st.name] = p
            parent_keep(w, st)
        else:
            parent_keep(w, root)

for ob in [o for o in build.objects if o.get("grp") == "ROCKET"]:
    ob.parent = joined["Launcher_Pack"]
    ob.matrix_parent_inverse = Matrix.Identity(4)
    ob.location = ob.location - PITCH_P
    ob.data.materials.clear()
    ob.data.materials.append(M("M_Rocket"))

# markers (read by the mod by name)
new_empty("Seat_Driver", (0.48, -1.15, 1.30), sway, 0.15)
new_empty("Seat_Commander", (-0.48, -1.15, 1.30), sway, 0.15)
new_empty("FX_Exhaust", (-1.12, 1.12, 0.64), sway, 0.1)
new_empty("FX_Headlight_L", (0.86, -3.30, 1.38), sway, 0.1)
new_empty("FX_Headlight_R", (-0.86, -3.30, 1.38), sway, 0.1)

# rest pose: shields raised (lying on the cab roof) for the march
joined["Shield"].rotation_mode = 'XYZ'
joined["Shield"].rotation_euler = (math.radians(88), 0, 0)

for o in build.objects:
    for k in list(o.keys()):
        if k in ("grp", "dirt", "tube"):
            del o[k]
build.name = "BM21_Grad"
print("objects:", len(build.objects), "tris:", sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in build.objects if o.type == 'MESH' and o.name.startswith(("SM_", "Wheel", "Turret", "Launcher", "Shield"))),
      "+ rockets 40x", len(bpy.data.meshes["SM_Rocket_Loaded"].polygons))
