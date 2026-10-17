# STEP 3: body - hood, fenders, bumper, cab (metal lower + canvas top), deck, tanks, boxes, lights
G = "Body"
# fix duplicate image loads from step 1 naming
# ------------------------------------------------------------------ hood / engine cover
bm = bm_box((1.04, 1.50, 0.70), (0, -2.55, 1.43), bevel=0.07, seg=2)
make_obj("Hood", bm, "M_Paint_Green", G)
box("HoodRidge", (0.06, 1.48, 0.03), (0, -2.55, 1.79), "M_Paint_Green", G, bevel=0.01)
# side louvres (5 per side) - engine cooling
for s in (1, -1):
    for k in range(6):
        box(f"Louvre{s}_{k}", (0.03, 0.05, 0.26), (s * 0.525, -2.95 + k * 0.12, 1.50), "M_Paint_Green", G, rot=(0, 0, s * 0.5), bevel=0.006)
    for k, y in enumerate((-3.1, -2.1)):
        box(f"HoodLatch{s}{k}", (0.03, 0.06, 0.1), (s * 0.53, y, 1.62), "M_Steel", G, bevel=0.006)
    # hood hinge line
    cyl(f"HoodHinge{s}", 0.012, 1.45, (s * 0.25, -2.55, 1.785), "M_Steel", G, axis=1, seg=6)

# radiator grille: frame + 9 horizontal slats + vertical centre bar
box("GrilleFrame_T", (1.02, 0.08, 0.06), (0, -3.32, 1.72), "M_Paint_Green", G, bevel=0.015)
box("GrilleFrame_B", (1.02, 0.08, 0.06), (0, -3.32, 1.10), "M_Paint_Green", G, bevel=0.015)
for s in (1, -1):
    box(f"GrilleFrame_S{s}", (0.06, 0.08, 0.66), (s * 0.49, -3.32, 1.41), "M_Paint_Green", G, bevel=0.015)
box("RadiatorCore", (0.92, 0.05, 0.58), (0, -3.28, 1.41), "M_Bore", G, bevel=0.0)
for k in range(9):
    box(f"GrilleSlat{k}", (0.92, 0.035, 0.022), (0, -3.335, 1.16 + k * 0.064), "M_Paint_Green", G, rot=(0.35, 0, 0), bevel=0.004)
box("GrilleBar", (0.04, 0.05, 0.6), (0, -3.345, 1.41), "M_Paint_Green", G, bevel=0.01)
# starter crank hole / bumper-level towing
cyl("CrankBoss", 0.035, 0.05, (0, -3.36, 1.02), "M_Steel", G, axis=1, seg=10)

# ------------------------------------------------------------------ fenders (sweep: arched over wheel, flat to cab)
path = [(-3.26, 1.00), (-3.24, 1.14), (-3.16, 1.26), (-3.02, 1.34), (-2.80, 1.38), (-2.45, 1.40),
        (-2.10, 1.41), (-1.78, 1.42)]
for s in (1, -1):
    secs = []
    for (y, z) in path:
        xi, xo = 0.53, 1.21
        secs.append([Vector((s * xi, y, z)), Vector((s * (xo - 0.04), y, z)), Vector((s * xo, y, z - 0.04)),
                     Vector((s * xo, y, z - 0.16))])
    bm = bm_sweep_ribbon(secs)
    if s < 0:
        bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
    bmesh.ops.solidify(bm, geom=list(bm.faces), thickness=0.022)
    make_obj(f"Fender{s}", bm, "M_Paint_Green", G, smooth=False)
    # headlight bucket + lens + rim (mounted on fender front)
    hx = s * 0.86
    cyl(f"HeadBucket{s}", 0.13, 0.16, (hx, -3.18, 1.38), "M_Paint_Green", G, axis=1, seg=18, bevel=0.015)
    cyl(f"HeadRim{s}", 0.135, 0.03, (hx, -3.27, 1.38), "M_Steel", G, axis=1, seg=18, bevel=0.006)
    bm = bm_cyl(0.115, 0.02, (hx, -3.28, 1.38), AX_ROT[1], 18)
    uv_cyl(bm, 1, (hx, -3.28, 1.38), scale_len=4.0 / 2.0 * 0.5 / 0.115, u_rep=0.5)
    make_obj(f"HeadLens{s}", bm, "M_Lamp_Head", G, uv=None, smooth=True, dirt=False)
    # blackout / side marker light on top of fender
    box(f"MarkerLight{s}", (0.08, 0.09, 0.07), (s * 1.05, -3.0, 1.40), "M_Paint_Green", G, bevel=0.01)
    box(f"MarkerLens{s}", (0.06, 0.012, 0.045), (s * 1.05, -3.05, 1.40), "M_Lamp_Head", G, bevel=0.0, dirt=False)
    # turn signal (amber) on fender side
    box(f"TurnLens{s}", (0.012, 0.07, 0.04), (s * 1.215, -2.95, 1.30), "M_Lamp_Tail", G, bevel=0.0, dirt=False)
    # fender stays (brackets to frame)
    bm = bm_tube_path([(s * 1.0, -2.2, 1.38), (s * 0.85, -2.2, 1.2), (s * 0.52, -2.2, 1.05)], 0.02, 6)
    make_obj(f"FenderStay{s}", bm, "M_Frame", G, smooth=True)
    # mudguard skirt behind front wheel (rubber)
    box(f"FrontMudflap{s}", (0.38, 0.012, 0.45), (s * 1.0, -1.82, 1.0), "M_Rubber", G, bevel=0.004)

# ------------------------------------------------------------------ front bumper (channel) + tow hooks + plate
box("Bumper", (2.30, 0.16, 0.22), (0, -3.42, 0.95), "M_Paint_Green", G, bevel=0.02)
for s in (1, -1):
    bm = bm_tube_path([(s * 0.55, -3.45, 0.98), (s * 0.55, -3.62, 0.98), (s * 0.55, -3.66, 1.06), (s * 0.55, -3.60, 1.12)], 0.028, 8)
    make_obj(f"TowHook{s}", bm, "M_Steel", G, smooth=True)
    for k in range(3):
        cyl(f"BumperBolt{s}{k}", 0.014, 0.02, (s * (0.65 + k * 0.18), -3.505, 0.95), "M_Steel", G, axis=1, seg=6)
bm = bmesh.new()
v = [bm.verts.new(p) for p in ((-0.26, -3.503, 0.90), (0.26, -3.503, 0.90), (0.26, -3.503, 1.02), (-0.26, -3.503, 1.02))]
bm.faces.new(v)
uvl = bm.loops.layers.uv.verify()
for l, uvv in zip(list(bm.faces)[0].loops, ((0.51, 0.62), (0.99, 0.62), (0.99, 0.88), (0.51, 0.88))):
    l[uvl].uv = uvv
bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
make_obj("PlateFront", bm, "M_Decal", G, uv=None, dirt=False)

# ------------------------------------------------------------------ cab (metal lower part)
CAB_Y0, CAB_Y1 = -1.80, -0.45
box("CabLower", (2.10, 1.35, 0.72), (0, (CAB_Y0 + CAB_Y1) / 2, 1.52), "M_Paint_Green", G, bevel=0.05, seg=2)
box("CabFloor", (2.0, 1.3, 0.1), (0, (CAB_Y0 + CAB_Y1) / 2, 1.14), "M_Frame", G, bevel=0.01)
# cowl panel between hood and windscreen
box("Cowl", (1.9, 0.12, 0.10), (0, CAB_Y0 + 0.02, 1.90), "M_Paint_Green", G, bevel=0.02)
# door panel seams + handles + hinges
for s in (1, -1):
    xs = s * 1.052
    for (y, z, sy, sz) in ((-1.62, 1.52, 0.012, 0.68), (-0.70, 1.52, 0.012, 0.68)):
        box(f"DoorSeam{s}{y}", (0.008, sy, sz), (xs, y, z), "M_Frame", G, bevel=0.0)
    box(f"DoorSeamB{s}", (0.008, 0.92, 0.012), (xs, -1.16, 1.17), "M_Frame", G, bevel=0.0)
    box(f"DoorHandle{s}", (0.03, 0.12, 0.025), (s * 1.065, -0.82, 1.76), "M_Steel", G, bevel=0.008)
    for z in (1.35, 1.72):
        cyl(f"DoorHinge{s}{z}", 0.018, 0.09, (s * 1.06, -1.66, z), "M_Steel", G, axis=2, seg=8)
    # tactical number on door (decal)
    bm = bmesh.new()
    vv = [bm.verts.new((s * 1.056, y, z)) for (y, z) in ((-1.45, 1.36), (-0.95, 1.36), (-0.95, 1.62), (-1.45, 1.62))]
    bm.faces.new(vv)
    uvl = bm.loops.layers.uv.verify()
    quv = ((0.02, 0.52), (0.48, 0.52), (0.48, 0.98), (0.02, 0.98))
    if s < 0:
        quv = ((0.48, 0.52), (0.02, 0.52), (0.02, 0.98), (0.48, 0.98))
    for l, uvv in zip(list(bm.faces)[0].loops, quv):
        l[uvl].uv = uvv
    if s > 0:
        bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
        for l, uvv in zip(list(bm.faces)[0].loops, ((0.02, 0.52), (0.02, 0.98), (0.48, 0.98), (0.48, 0.52))):
            pass
    make_obj(f"DoorNumber{s}", bm, "M_Decal", G, uv=None, dirt=False)
    # steps below doors (2 treads)
    for k, z in enumerate((0.78, 1.0)):
        box(f"Step{s}{k}", (0.26, 0.42, 0.03), (s * (1.0 - k * 0.02), -1.15, z), "M_Steel", G, bevel=0.006)
    box(f"StepHanger{s}", (0.03, 0.03, 0.32), (s * 1.08, -1.15, 0.92), "M_Frame", G, bevel=0.0)
    # rear-view mirrors on arms
    bm = bm_tube_path([(s * 1.05, -1.72, 2.05), (s * 1.28, -1.78, 2.15), (s * 1.30, -1.80, 2.35)], 0.012, 6)
    make_obj(f"MirrorArm{s}", bm, "M_Steel", G, smooth=True)
    box(f"Mirror{s}", (0.05, 0.16, 0.24), (s * 1.31, -1.80, 2.40), "M_Paint_Green", G, bevel=0.015)
    box(f"MirrorGlass{s}", (0.012, 0.13, 0.20), (s * 1.31, -1.775, 2.40), "M_Glass", G, rot=(0, 0, 0), bevel=0.0, dirt=False)
    # grab handle next to door
    bm = bm_tube_path([(s * 1.07, -0.50, 1.35), (s * 1.10, -0.50, 1.38), (s * 1.10, -0.50, 1.85), (s * 1.07, -0.50, 1.88)], 0.013, 6)
    make_obj(f"GrabHandle{s}", bm, "M_Steel", G, smooth=True)

# windscreen frame + glass (flat split screen)
WZ0, WZ1 = 1.95, 2.42
box("WsFrameBottom", (2.02, 0.07, 0.06), (0, CAB_Y0 + 0.05, WZ0), "M_Paint_Green", G, bevel=0.012)
box("WsFrameTop", (2.02, 0.07, 0.06), (0, CAB_Y0 + 0.05, WZ1 + 0.02), "M_Paint_Green", G, bevel=0.012)
for x in (-1.0, 0.0, 1.0):
    box(f"WsPillar{x}", (0.06, 0.07, 0.50), (x, CAB_Y0 + 0.05, (WZ0 + WZ1) / 2), "M_Paint_Green", G, bevel=0.012)
for s in (1, -1):
    bm = bm_box((0.92, 0.012, 0.44), (s * 0.5, CAB_Y0 + 0.055, (WZ0 + WZ1) / 2), bevel=0.0)
    uv_box(bm, 1.0)
    uvl = bm.loops.layers.uv.verify()
    for f in bm.faces:
        for l in f.loops:
            co = l.vert.co
            l[uvl].uv = ((co.x - s * 0.5) / 0.92 + 0.5, (co.z - WZ0) / 0.44)
    make_obj(f"Windscreen{s}", bm, "M_Glass", G, uv=None, dirt=False)
    # wiper arm + blade (parked, hanging from top - Ural style)
    box(f"WiperArm{s}", (0.012, 0.012, 0.36), (s * 0.5 - 0.1, CAB_Y0 + 0.035, 2.24), "M_Steel", G, rot=(0, 0.35 * s, 0), bevel=0.0)
    box(f"WiperBlade{s}", (0.02, 0.02, 0.34), (s * 0.5, CAB_Y0 + 0.03, 2.16), "M_Rubber", G, rot=(0, 0.35 * s, 0), bevel=0.0)
# door window frames + glass
for s in (1, -1):
    box(f"DoorWinFrameF{s}", (0.05, 0.05, 0.50), (s * 1.03, -1.62, 2.17), "M_Paint_Green", G, bevel=0.01)
    box(f"DoorWinFrameR{s}", (0.05, 0.05, 0.50), (s * 1.03, -0.72, 2.17), "M_Paint_Green", G, bevel=0.01)
    box(f"DoorWinFrameT{s}", (0.05, 0.95, 0.05), (s * 1.03, -1.17, 2.42), "M_Paint_Green", G, bevel=0.01)
    bm = bm_box((0.01, 0.86, 0.44), (s * 1.035, -1.17, 2.17), bevel=0.0)
    uvl = bm.loops.layers.uv.verify()
    for f in bm.faces:
        for l in f.loops:
            co = l.vert.co
            l[uvl].uv = ((co.y + 1.6) / 0.86, (co.z - 1.95) / 0.44)
    make_obj(f"DoorGlass{s}", bm, "M_Glass", G, uv=None, dirt=False)

# canvas soft top (Ural-375D): bows + tarp
secs = []
for k in range(7):
    y = -1.74 + k * (1.24 / 6)
    sec = []
    for j in range(9):
        t = j / 8
        x = -1.03 + 2.06 * t
        z = 2.46 + 0.10 * math.sin(PI * t) + (0.006 * math.sin(k * 2.1 + j))  # slight sag irregularity
        sec.append(Vector((x, y, z)))
    secs.append(sec)
bm = bm_sweep_ribbon(secs)
bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
make_obj("CanvasRoof", bm, "M_Canvas", G, smooth=True)
box("CanvasBack", (2.06, 0.02, 0.62), (0, -0.50, 2.20), "M_Canvas", G, bevel=0.0)
box("CabRearWindow", (0.6, 0.012, 0.22), (0, -0.487, 2.25), "M_Glass", G, bevel=0.0, dirt=False)
for s in (1, -1):
    box(f"CanvasSide{s}", (0.02, 0.36, 0.52), (s * 1.035, -0.62, 2.20), "M_Canvas", G, bevel=0.0)
    for k in range(5):  # tie-down straps along canvas edge
        box(f"CanvasStrap{s}{k}", (0.03, 0.025, 0.12), (s * 1.04, -1.65 + k * 0.28, 2.40), "M_Rubber", G, bevel=0.0)
for k in range(3):
    bm = bm_tube_path([(-1.03, -1.60 + k * 0.52, 2.45), (-0.5, -1.60 + k * 0.52, 2.53), (0.5, -1.60 + k * 0.52, 2.53), (1.03, -1.60 + k * 0.52, 2.45)], 0.012, 6)
    make_obj(f"CanvasBow{k}", bm, "M_Steel", G, smooth=True)
# antenna base + whip (radio R-123) on rear-left corner of cab
cyl("AntennaBase", 0.04, 0.10, (0.95, -0.55, 2.05), "M_Rubber", G, seg=10)
cyl("AntennaWhip", 0.008, 2.20, (0.95, -0.55, 3.15), "M_Steel", G, seg=5, r2=0.004)

# ------------------------------------------------------------------ BM-21 armoured windscreen shields (animated node)
SH = "Shield"
for s in (1, -1):
    box(f"ShieldPlate{s}", (0.98, 0.022, 0.56), (s * 0.5, CAB_Y0 - 0.02, 2.30), "M_Paint_Olive2", SH, bevel=0.01)
    box(f"ShieldSlot{s}", (0.30, 0.03, 0.03), (s * 0.5, CAB_Y0 - 0.025, 2.28), "M_Bore", SH, bevel=0.0)   # vision slot
    for x in (-0.35, 0.35):
        box(f"ShieldRib{s}{x}", (0.03, 0.03, 0.50), (s * 0.5 + x, CAB_Y0 - 0.035, 2.30), "M_Paint_Olive2", SH, bevel=0.006)
cyl("ShieldHinge", 0.02, 2.0, (0, CAB_Y0 - 0.02, 2.58), "M_Steel", SH, axis=0, seg=8)

# ------------------------------------------------------------------ fuel tanks (2×) + battery box + tool boxes
for s in (1, -1):
    cyl(f"FuelTank{s}", 0.26, 0.95, (s * 0.80, -1.05, 0.78), "M_Paint_Worn", G, axis=1, seg=18, bevel=0.03)
    for dy in (-0.3, 0.3):
        cyl(f"TankStrap{s}{dy}", 0.268, 0.04, (s * 0.80, -1.05 + dy, 0.78), "M_Frame", G, axis=1, seg=18)
    cyl(f"TankCap{s}", 0.05, 0.05, (s * 0.88, -1.05, 1.05), "M_Steel", G, seg=10, bevel=0.008)
box("BatteryBox", (0.40, 0.55, 0.36), (-0.82, -0.25, 0.86), "M_Paint_Worn", G, bevel=0.02)
box("BatteryLid", (0.42, 0.57, 0.03), (-0.82, -0.25, 1.05), "M_Paint_Worn", G, bevel=0.01)
for k in range(2):
    box(f"BatteryLatch{k}", (0.02, 0.05, 0.08), (-1.03, -0.4 + k * 0.3, 0.98), "M_Steel", G, bevel=0.004)
box("ToolBoxL", (0.40, 0.55, 0.36), (0.82, -0.25, 0.86), "M_Paint_Worn", G, bevel=0.02)
box("ToolBoxLLid", (0.42, 0.57, 0.03), (0.82, -0.25, 1.05), "M_Paint_Worn", G, bevel=0.01)
# exhaust: muffler under right side, pipe exits behind cab to the right
cyl("Muffler", 0.13, 0.7, (-0.62, 0.35, 0.80), "M_Frame", G, axis=1, seg=14, bevel=0.02)
bm = bm_tube_path([(-0.62, 0.72, 0.80), (-0.70, 0.95, 0.78), (-1.02, 1.05, 0.74), (-1.10, 1.10, 0.66)], 0.045, 8)
make_obj("ExhaustPipe", bm, "M_Frame", G, smooth=True)

# ------------------------------------------------------------------ rear platform (deck) + mudguards + rear lights
DECK_Z = 1.25
box("Deck", (2.36, 4.0, 0.08), (0, 1.58, DECK_Z), "M_Paint_Worn", G, bevel=0.012)
box("DeckSkirtL", (0.05, 4.0, 0.22), (1.16, 1.58, DECK_Z - 0.13), "M_Paint_Worn", G, bevel=0.01)
box("DeckSkirtR", (0.05, 4.0, 0.22), (-1.16, 1.58, DECK_Z - 0.13), "M_Paint_Worn", G, bevel=0.01)
box("DeckRear", (2.36, 0.06, 0.26), (0, 3.56, DECK_Z - 0.10), "M_Paint_Worn", G, bevel=0.01)
# anti-slip treads (raised strips) - 70/20/10: deck rear is a hot spot
for k in range(10):
    box(f"DeckTread{k}", (0.9, 0.03, 0.012), (-0.2, 0.2 + k * 0.12, DECK_Z + 0.045), "M_Steel", G, bevel=0.0)
for s in (1, -1):
    for k in range(4):
        box(f"DeckBracket{s}{k}", (0.04, 0.12, 0.2), (s * 0.62, -0.1 + k * 1.2, DECK_Z - 0.15), "M_Frame", G, bevel=0.005)
    # rear mudflaps
    box(f"RearMudflap{s}", (0.40, 0.012, 0.48), (s * 1.0, AXLES["R"] + 0.68, 0.88), "M_Rubber", G, bevel=0.004)
    # tail light cluster + reflector
    box(f"TailHousing{s}", (0.20, 0.08, 0.12), (s * 0.95, 3.60, DECK_Z - 0.10), "M_Paint_Worn", G, bevel=0.01)
    box(f"TailLens{s}", (0.08, 0.012, 0.07), (s * 0.99, 3.645, DECK_Z - 0.10), "M_Lamp_Tail", G, bevel=0.0, dirt=False)
    box(f"TailLensB{s}", (0.06, 0.012, 0.07), (s * 0.90, 3.645, DECK_Z - 0.10), "M_Lamp_Head", G, bevel=0.0, dirt=False)
    cyl(f"Reflector{s}", 0.035, 0.012, (s * 0.72, 3.595, DECK_Z - 0.12), "M_Red", G, axis=1, seg=10, dirt=False)
    # rear tow shackle
    bm = bm_tube_path([(s * 0.35, 3.58, 0.95), (s * 0.35, 3.72, 0.95), (s * 0.35, 3.72, 1.05)], 0.025, 6)
    make_obj(f"RearShackle{s}", bm, "M_Steel", G, smooth=True)
# rear number plate + "do not stand behind" warning (story: rocket exhaust blast)
for (nm, uvq, quad) in (("PlateRear", ((0.51, 0.62), (0.99, 0.62), (0.99, 0.88), (0.51, 0.88)),
                         ((0.26, 3.593, 1.08), (-0.26, 3.593, 1.08), (-0.26, 3.593, 1.20), (0.26, 3.593, 1.20))),
                        ("WarnRear", ((0.51, 0.10), (0.99, 0.10), (0.99, 0.42), (0.51, 0.42)),
                         ((1.10, 3.593, 1.30), (0.60, 3.593, 1.30), (0.60, 3.593, 1.46), (1.10, 3.593, 1.46)))):
    bm = bmesh.new()
    vv = [bm.verts.new(p) for p in quad]
    bm.faces.new(vv)
    uvl = bm.loops.layers.uv.verify()
    for l, uvv in zip(list(bm.faces)[0].loops, uvq):
        l[uvl].uv = uvv
    make_obj(nm, bm, "M_Decal", G, uv=None, dirt=False)
box("WarnPanel", (0.52, 0.02, 0.18), (0.85, 3.585, 1.38), "M_Paint_Worn", G, bevel=0.004)

# ------------------------------------------------------------------ deck equipment (level-2/3 objects with a story)
# spare wheel lying flat on the front of the deck (right), in a cradle with clamp
spare_y, spare_x = 0.55, -0.58
bm = bm_lathe([(0.355, -0.17), (0.46, -0.19), (0.54, -0.19), (0.583, -0.172), (0.60, -0.13), (0.60, 0.13), (0.583, 0.172), (0.54, 0.19), (0.46, 0.19), (0.355, 0.17)],
              (spare_x, spare_y, DECK_Z + 0.24), 2, seg=24)
uv_box(bm, 1.0)
make_obj("SpareTyre", bm, "M_Tyre", G, uv=None, smooth=True)
cyl("SpareDisc", 0.36, 0.14, (spare_x, spare_y, DECK_Z + 0.24), "M_Paint_Worn", G, axis=2, seg=20)
cyl("SpareHub", 0.13, 0.08, (spare_x, spare_y, DECK_Z + 0.33), "M_Paint_Worn", G, axis=2, seg=14, bevel=0.01)
box("SpareCradle", (0.9, 0.1, 0.05), (spare_x, spare_y, DECK_Z + 0.05), "M_Frame", G, bevel=0.01)
box("SpareCradle2", (0.1, 0.9, 0.05), (spare_x, spare_y, DECK_Z + 0.05), "M_Frame", G, bevel=0.01)
cyl("SpareStud", 0.02, 0.3, (spare_x, spare_y, DECK_Z + 0.35), "M_Steel", G, seg=6)
cyl("SpareClampNut", 0.05, 0.04, (spare_x, spare_y, DECK_Z + 0.42), "M_Steel", G, seg=6, smooth=False)
# travel lock post (holds the tube pack front on the march)
box("TravelLockPost", (0.10, 0.10, 1.1), (0.15, 0.30, DECK_Z + 0.58), "M_Paint_Worn", G, bevel=0.01)
box("TravelLockFork", (0.36, 0.08, 0.06), (0.15, 0.30, DECK_Z + 1.15), "M_Paint_Worn", G, bevel=0.01)
for s in (1, -1):
    box(f"TravelLockHorn{s}", (0.04, 0.08, 0.10), (0.15 + s * 0.16, 0.30, DECK_Z + 1.2), "M_Paint_Worn", G, bevel=0.006)
    bm = bm_tube_path([(0.15, 0.30, DECK_Z + 0.9), (0.15 + s * 0.35, 0.30, DECK_Z + 0.04)], 0.018, 6)
    make_obj(f"TravelLockBrace{s}", bm, "M_Frame", G, smooth=True)
# jerry cans (2) on rack - left front of deck; one from a different batch (colour variation)
for k, m in enumerate(("M_Jerrycan", "M_Paint_Olive2")):
    x = 0.95 - k * 0.0
    y = 0.05 + k * 0.40
    box(f"Jerrycan{k}", (0.17, 0.35, 0.47), (x, y, DECK_Z + 0.28), m, G, bevel=0.02)
    box(f"JerryHandle{k}", (0.12, 0.10, 0.04), (x, y - 0.08, DECK_Z + 0.53), m, G, bevel=0.008)
    cyl(f"JerryCap{k}", 0.025, 0.05, (x, y + 0.12, DECK_Z + 0.53), "M_Steel", G, seg=8)
box("JerryRack", (0.22, 0.9, 0.04), (0.95, 0.25, DECK_Z + 0.06), "M_Frame", G, bevel=0.005)
box("JerryStrap", (0.20, 0.9, 0.03), (0.95, 0.25, DECK_Z + 0.36), "M_Rubber", G, bevel=0.0)
# shovel + crowbar clamped along the left deck skirt (wood colour accent)
cyl("ShovelHandle", 0.02, 1.1, (1.20, 2.3, DECK_Z - 0.10), "M_Wood", G, axis=1, seg=8)
box("ShovelBlade", (0.012, 0.25, 0.20), (1.20, 2.95, DECK_Z - 0.10), "M_Steel", G, bevel=0.004)
cyl("Crowbar", 0.015, 1.2, (1.20, 1.2, DECK_Z - 0.18), "M_Frame", G, axis=1, seg=6)
for y in (1.9, 2.6, 0.8, 1.5):
    box(f"ToolClamp{y}", (0.04, 0.04, 0.06), (1.19, y, DECK_Z - 0.13), "M_Steel", G, bevel=0.004)
# rolled tarp (canvas) with straps - right side front
cyl("TarpRoll", 0.14, 0.8, (0.62, 0.98, DECK_Z + 0.18), "M_Canvas", G, axis=1, seg=14)
for dy in (-0.25, 0.25):
    cyl(f"TarpStrap{dy}", 0.145, 0.04, (0.62, 0.98 + dy, DECK_Z + 0.18), "M_Rubber", G, axis=1, seg=14)
# remote firing cable reel (story: crew fires from cover up to 60 m away)
cyl("CableReel", 0.20, 0.22, (0.85, 3.2, DECK_Z + 0.25), "M_Paint_Olive2", G, axis=0, seg=16, bevel=0.01)
cyl("CableCoil", 0.16, 0.18, (0.85, 3.2, DECK_Z + 0.25), "M_Rubber", G, axis=0, seg=16)
box("ReelStand", (0.28, 0.08, 0.2), (0.85, 3.2, DECK_Z + 0.1), "M_Frame", G, bevel=0.01)
# rear boarding ladder
for s in (1, -1):
    box(f"LadderRail{s}", (0.03, 0.03, 0.55), (s * 0.18 - 0.55, 3.62, 0.95), "M_Steel", G, bevel=0.0)
for k in range(3):
    cyl(f"LadderRung{k}", 0.012, 0.36, (-0.55, 3.62, 0.75 + k * 0.17), "M_Steel", G, axis=0, seg=6)
print("parts:", len(col_get("BUILD").objects))
