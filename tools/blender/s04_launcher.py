# STEP 4: launcher - fixed traverse ring (Body), rotating base (Turret_Yaw), cradle (Launcher_Pitch),
#          40-tube pack (Launcher_Pack), 40 loaded rockets (Rocket_01..40), sight, handwheels
DECK_Z = 1.25
YAW_P = Vector((0.0, 2.15, 1.29))
PITCH_P = Vector((0.0, 2.62, 2.45))
TUBE_Y0, TUBE_L = 0.17, 3.00
TUBE_RO, TUBE_RI = 0.068, 0.0615
COLS = [(i - 4.5) * 0.158 for i in range(10)]
ROWS = [2.55 + j * 0.165 for j in range(4)]

# ------------------------------------------------------------------ fixed traverse ring on deck (Body)
cyl("TraverseRing", 0.80, 0.07, (YAW_P.x, YAW_P.y, DECK_Z + 0.075), "M_Paint_Worn", "Body", seg=32, bevel=0.01)
for k in range(56):  # ring gear teeth
    a = 2 * PI * k / 56
    box(f"RingTooth{k}", (0.03, 0.035, 0.05), (YAW_P.x + math.cos(a) * 0.81, YAW_P.y + math.sin(a) * 0.81, DECK_Z + 0.075),
        "M_Steel", "Body", rot=(0, 0, a), bevel=0.0)
for k in range(12):
    a = 2 * PI * (k + 0.5) / 12
    cyl(f"RingBolt{k}", 0.014, 0.02, (YAW_P.x + math.cos(a) * 0.72, YAW_P.y + math.sin(a) * 0.72, DECK_Z + 0.115), "M_Steel", "Body", seg=6)

# ------------------------------------------------------------------ rotating base (Turret_Yaw)
T = "Turret_Yaw"
cyl("YawDisc", 0.76, 0.09, (YAW_P.x, YAW_P.y, YAW_P.z + 0.10), "M_Paint_Green", T, seg=32, bevel=0.012)
box("YawPlatform", (1.30, 1.45, 0.16), (0, 2.20, YAW_P.z + 0.22), "M_Paint_Green", T, bevel=0.03)
# side standards (triangular plates) carrying the trunnions
for s in (1, -1):
    x = s * 0.50
    prof = [(1.55, 1.58), (2.95, 1.58), (2.86, 2.28), (2.68, 2.55), (2.52, 2.55), (2.30, 2.20)]
    bm = bmesh.new()
    vs = [bm.verts.new((x - 0.025, y, z)) for (y, z) in prof]
    f = bm.faces.new(vs)
    bmesh.ops.extrude_face_region(bm, geom=[f])
    ext = [v for v in bm.verts if abs(v.co.x - (x - 0.025)) < 1e-6 and v not in vs]
    for v in ext:
        v.co.x += 0.05
    bm.normal_update()
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    make_obj(f"Standard{s}", bm, "M_Paint_Green", T)
    box(f"StandardRib{s}", (0.05, 0.05, 0.9), (x + s * 0.04, 2.30, 1.95), "M_Paint_Green", T, rot=(-0.45, 0, 0), bevel=0.01)
    cyl(f"TrunnionBearing{s}", 0.09, 0.14, (s * 0.55, PITCH_P.y, PITCH_P.z), "M_Paint_Green", T, axis=0, seg=16, bevel=0.012)
    cyl(f"TrunnionCap{s}", 0.06, 0.03, (s * 0.635, PITCH_P.y, PITCH_P.z), "M_Steel", T, axis=0, seg=12)
    for k in range(6):
        a = 2 * PI * k / 6
        cyl(f"BearingBolt{s}{k}", 0.01, 0.02, (s * 0.63, PITCH_P.y + math.cos(a) * 0.075, PITCH_P.z + math.sin(a) * 0.075), "M_Steel", T, axis=0, seg=6)
    # elevation pinion housing (meshes with the sector gear on the cradle)
    box(f"PinionBox{s}", (0.12, 0.22, 0.2), (s * 0.60, 2.20, 2.00), "M_Paint_Green", T, bevel=0.02)
    cyl(f"PinionShaft{s}", 0.03, 0.14, (s * 0.66, 2.20, 2.00), "M_Steel", T, axis=0, seg=8)
# balancing mechanism cylinders (spring equilibrators) between standards
for s in (1, -1):
    cyl(f"Equilibrator{s}", 0.07, 0.75, (s * 0.30, 2.25, 1.78), "M_Paint_Green", T, axis=1, seg=12, bevel=0.01)
# gunner handwheels (traverse + elevation) on the left side
for k, (z, yy) in enumerate(((1.72, 1.85), (1.98, 2.05))):
    ring = bm_lathe([(0.10, -0.012), (0.115, 0.0), (0.10, 0.012), (0.085, 0.0), (0.10, -0.012)], (0.80, yy, z), 0, seg=16)
    make_obj(f"Handwheel{k}", ring, "M_Steel", T, smooth=True)
    cyl(f"HandwheelHub{k}", 0.03, 0.06, (0.80, yy, z), "M_Steel", T, axis=0, seg=8)
    for j in range(3):
        a = 2 * PI * j / 3
        bm = bm_tube_path([(0.80, yy, z), (0.80, yy + math.cos(a) * 0.1, z + math.sin(a) * 0.1)], 0.008, 4)
        make_obj(f"HandwheelSpoke{k}{j}", bm, "M_Steel", T)
    cyl(f"HandwheelKnob{k}", 0.015, 0.07, (0.84, yy + 0.1, z), "M_Wood", T, axis=0, seg=6)
    box(f"HandwheelGear{k}", (0.14, 0.14, 0.14), (0.68, yy, z), "M_Paint_Green", T, bevel=0.015)
# panoramic sight PG-1M on bracket (left side, towards the rear)
box("SightBracket", (0.10, 0.10, 0.45), (0.78, 2.55, 1.80), "M_Paint_Green", T, bevel=0.012)
box("SightBody", (0.16, 0.20, 0.18), (0.80, 2.55, 2.10), "M_Paint_Olive2", T, bevel=0.02)
cyl("SightHead", 0.06, 0.12, (0.80, 2.55, 2.25), "M_Paint_Olive2", T, seg=12, bevel=0.01)
box("SightHeadWin", (0.07, 0.03, 0.04), (0.80, 2.50, 2.26), "M_Glass", T, bevel=0.0, dirt=False)
cyl("SightEyepiece", 0.025, 0.16, (0.88, 2.62, 2.08), "M_Rubber", T, axis=1, seg=8)
cyl("SightLevelVial", 0.012, 0.06, (0.80, 2.45, 2.18), "M_Brass", T, axis=0, seg=6)
box("SightCase", (0.12, 0.26, 0.18), (0.80, 1.75, 1.52), "M_Wood", T, bevel=0.01)  # wooden sight box
# junction box + cable harness to the pack
box("JunctionBox", (0.26, 0.16, 0.22), (-0.62, 2.72, 1.62), "M_Paint_Olive2", T, bevel=0.02)
bm = bm_tube_path([(-0.62, 2.8, 1.66), (-0.62, 2.95, 1.9), (-0.55, 2.9, 2.30), (-0.45, 2.80, 2.43)], 0.025, 6)
make_obj("HarnessA", bm, "M_Rubber", T, smooth=True)

# ------------------------------------------------------------------ cradle (Launcher_Pitch)
P = "Launcher_Pitch"
cyl("TrunnionShaft", 0.055, 1.05, (0, PITCH_P.y, PITCH_P.z), "M_Steel", P, axis=0, seg=12)
for s in (1, -1):
    box(f"CradleBeam{s}", (0.10, 3.0, 0.10), (s * 0.45, 1.66, 2.43), "M_Paint_Green", P, bevel=0.012)
    # elevation sector gear: arc plate below/forward of trunnion
    arc = []
    for k in range(9):
        a = -PI / 2 - 0.9 + k * (0.9 / 8) * 1.9
        arc.append((math.cos(a), math.sin(a)))
    bm = bmesh.new()
    inner = [bm.verts.new((s * 0.60, PITCH_P.y + ca * 0.30, PITCH_P.z + sa * 0.30)) for (ca, sa) in arc]
    outer = [bm.verts.new((s * 0.60, PITCH_P.y + ca * 0.52, PITCH_P.z + sa * 0.52)) for (ca, sa) in arc]
    for k in range(len(arc) - 1):
        bm.faces.new((inner[k], inner[k + 1], outer[k + 1], outer[k]))
    bmesh.ops.solidify(bm, geom=list(bm.faces), thickness=0.04)
    make_obj(f"SectorGear{s}", bm, "M_Steel", P)
    for k in range(18):  # teeth on outer edge
        a = -PI / 2 - 0.9 + k * (0.9 * 1.9 / 17)
        box(f"SectorTooth{s}{k}", (0.04, 0.03, 0.03), (s * 0.60, PITCH_P.y + math.cos(a) * 0.535, PITCH_P.z + math.sin(a) * 0.535),
            "M_Steel", P, rot=(a, 0, 0), bevel=0.0)
    bm = bm_tube_path([(s * 0.45, 2.9, 2.42), (s * 0.55, PITCH_P.y, PITCH_P.z)], 0.03, 6)
    make_obj(f"CradleArm{s}", bm, "M_Paint_Green", P, smooth=True)
for y in (0.4, 1.4, 2.4, 2.95):
    box(f"CradleCross{y}", (1.0, 0.08, 0.08), (0, y, 2.43), "M_Paint_Green", P, bevel=0.01)

# ------------------------------------------------------------------ tube pack (Launcher_Pack, recoil node)
K = "Launcher_Pack"
tube_prof = [(0.0, 0.32), (TUBE_RI, 0.32), (TUBE_RI, 0.0), (TUBE_RO * 1.04, 0.0), (TUBE_RO * 1.04, 0.07), (TUBE_RO, 0.07),
             (TUBE_RO, TUBE_L - 0.07), (TUBE_RO * 1.04, TUBE_L - 0.07), (TUBE_RO * 1.04, TUBE_L), (TUBE_RI, TUBE_L),
             (TUBE_RI, TUBE_L - 0.35), (0.0, TUBE_L - 0.35)]
bm0 = bm_lathe(tube_prof, (0, 0, 0), 1, seg=10)
bmesh.ops.reverse_faces(bm0, faces=list(bm0.faces))
uv_cyl(bm0, 1, (0, 0, 0), scale_len=1.0, u_rep=1.0)
tube_me = bpy.data.meshes.new("Tube_src")
bm0.to_mesh(tube_me)
bm0.free()
for j, z in enumerate(ROWS):
    for i, x in enumerate(COLS):
        me = tube_me.copy()
        me.transform(Matrix.Translation((x, TUBE_Y0, z)))
        for p in me.polygons:
            p.use_smooth = True
        me.materials.append(M("M_Paint_Green"))
        ob = bpy.data.objects.new(f"Tube_{j}_{i}", me)
        col_get("BUILD").objects.link(ob)
        ob["grp"] = K
        ob["dirt"] = 1
        ob["tube"] = 1
bpy.data.meshes.remove(tube_me)
# clamp frames around the bundle at 3 stations
X0, X1 = -0.80, 0.80
Z0, Z1 = ROWS[0] - 0.078, ROWS[-1] + 0.078
for y in (TUBE_Y0 + 0.30, TUBE_Y0 + 1.45, TUBE_Y0 + 2.70):
    box(f"PackFrameT{y}", (1.70, 0.10, 0.05), (0, y, Z1 + 0.02), "M_Paint_Green", K, bevel=0.01)
    box(f"PackFrameB{y}", (1.70, 0.10, 0.05), (0, y, Z0 - 0.02), "M_Paint_Green", K, bevel=0.01)
    for s in (1, -1):
        box(f"PackFrameS{y}{s}", (0.05, 0.10, Z1 - Z0 + 0.09), (s * 0.83, y, (Z0 + Z1) / 2), "M_Paint_Green", K, bevel=0.01)
    for j in range(3):  # separator strips between rows
        box(f"PackSep{y}{j}", (1.62, 0.06, 0.012), (0, y, (ROWS[j] + ROWS[j + 1]) / 2), "M_Paint_Green", K, bevel=0.0)
    for s in (1, -1):
        for zz in (Z0 - 0.02, Z1 + 0.02):
            cyl(f"PackBolt{y}{s}{zz}", 0.018, 0.03, (s * 0.86, y, zz), "M_Steel", K, axis=0, seg=6)
# longitudinal top rails (lifting points) + side stiffeners
for s in (1, -1):
    box(f"PackRailTop{s}", (0.05, 2.9, 0.04), (s * 0.60, TUBE_Y0 + 1.5, Z1 + 0.05), "M_Paint_Green", K, bevel=0.008)
    box(f"PackSideRail{s}", (0.03, 2.9, 0.05), (s * 0.855, TUBE_Y0 + 1.5, (Z0 + Z1) / 2), "M_Paint_Green", K, bevel=0.006)
    for y in (TUBE_Y0 + 0.9, TUBE_Y0 + 2.1):
        bm = bm_tube_path([(s * 0.60, y - 0.08, Z1 + 0.07), (s * 0.60, y - 0.05, Z1 + 0.14), (s * 0.60, y + 0.05, Z1 + 0.14), (s * 0.60, y + 0.08, Z1 + 0.07)], 0.012, 6)
        make_obj(f"LiftEye{s}{y}", bm, "M_Steel", K, smooth=True)
# contact-device bar at tube rears (electric firing contacts) + cable
box("ContactBar", (1.62, 0.05, 0.04), (0, TUBE_Y0 + TUBE_L - 0.03, Z0 - 0.05), "M_Paint_Olive2", K, bevel=0.006)
for i, x in enumerate(COLS):
    box(f"Contact{i}", (0.03, 0.03, 0.03), (x, TUBE_Y0 + TUBE_L - 0.03, Z0 - 0.02), "M_Brass", K, bevel=0.0)

# ------------------------------------------------------------------ loaded rockets (instanced mesh; nose at origin, body along +Y)
rk_prof = [(0.0, -0.02), (0.012, -0.015), (0.03, 0.03), (0.045, 0.09), (0.055, 0.16), (0.0605, 0.26), (0.0605, 2.60),
           (0.055, 2.66), (0.058, 2.80), (0.045, 2.86), (0.03, 2.87), (0.0, 2.87)]
bm = bm_lathe(rk_prof, (0, 0, 0), 1, seg=10)
bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
uvl = bm.loops.layers.uv.verify()
for f in bm.faces:
    for l in f.loops:
        co = l.vert.co
        l[uvl].uv = ((math.atan2(co.z, co.x) / (2 * PI) + 0.5), 1.0 - max(0.0, min(1.0, co.y / 2.87)))
rk_me = bpy.data.meshes.new("SM_Rocket_Loaded")
bm.to_mesh(rk_me)
bm.free()
for p in rk_me.polygons:
    p.use_smooth = True
rk_me.materials.append(M("M_Rocket"))
n = 0
for j, z in enumerate(ROWS):
    for i, x in enumerate(COLS):
        n += 1
        ob = bpy.data.objects.new(f"Rocket_{n:02d}", rk_me)   # linked data -> one glTF mesh, 40 nodes
        ob.location = (x, TUBE_Y0 + 0.10, z)
        col_get("BUILD").objects.link(ob)
        ob["grp"] = "ROCKET"
print("parts:", len(col_get("BUILD").objects))
