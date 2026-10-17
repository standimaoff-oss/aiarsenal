# Tu-95MS STEP 3: crew compartments. Forward pressurised cabin (navigator nose, cockpit, flight-engineer and radio
# stations, aisle with ladder hole) and the tail-gunner cabin. Walk zones / seats / ladders are exported as empties.
amat("M_Quilt", "T_Quilt95", 0.9)
amat("M_Floor", "T_Floor", 0.75, 0.3)
amat("M_Panel", "T_Panels", 0.5, 0.1)
amat("M_IntPaint", None, 0.55, 0.0, factor=(0.30, 0.55, 0.53, 1))           # Soviet cockpit turquoise
amat("M_IntGrey", None, 0.6, 0.0, factor=(0.36, 0.38, 0.40, 1))
amat("M_Seat", "T_Seat", 0.8)
amat("M_Cloth", None, 0.9, 0.0, factor=(0.42, 0.40, 0.28, 1))              # parachute packs / harness
amat("M_Oxy", None, 0.4, 0.3, factor=(0.18, 0.32, 0.68, 1))
amat("M_Red", None, 0.45, 0.0, factor=(0.70, 0.07, 0.05, 1))
amat("M_Paper", None, 0.9, 0.0, factor=(0.88, 0.86, 0.78, 1))
amat("M_Ammo", None, 0.7, 0.2, factor=(0.30, 0.36, 0.22, 1))
amat("M_Brass", None, 0.35, 1.0, factor=(0.78, 0.60, 0.28, 1))
Q_TU95, Q_TU160, Q_OVER, Q_NAV = (0.0, 0.5, 0.5, 1.0), (0.5, 0.5, 1.0, 1.0), (0.0, 0.0, 0.5, 0.5), (0.5, 0.0, 1.0, 0.5)
I, IR = "Int", "IntR"
SEATS = {}
TFLOOR2_Z = 3.45        # raised gunner floor (tail cone narrows)


def panel(name, size, loc, rect, grp, rot=(0, 0, 0), axes=None, flip=(False, False)):
    ob = abox(name, size, loc, "M_Panel", grp, rot=rot, bevel=0.008)
    uv_planar(ob, rect, axes, flip)
    return ob


def crew_seat(name, loc, yaw, grp, armored=True, role=None):
    """Soviet bomber crew seat: pan, tall back (armour plate for pilots), headrest, arm rests, harness, base + rails."""
    p = Vector(loc)
    R = Matrix.Rotation(yaw, 4, 'Z')
    def L(v):
        return p + (R @ Vector(v).to_4d()).to_3d()
    rz = (0, 0, yaw)
    abox(name + "_base", (0.42, 0.42, 0.30), L((0, 0, -0.32)), "M_IntGrey", grp, rot=rz, bevel=0.02)
    for sx in (-0.17, 0.17):
        abox(name + "_rail%s" % sx, (0.04, 0.70, 0.04), L((sx, 0, -0.47)), "M_Chrome", grp, rot=rz, bevel=0.005)
    abox(name + "_pan", (0.50, 0.50, 0.11), L((0, 0, -0.06)), "M_Seat", grp, rot=rz, bevel=0.035)
    back_h = 0.80 if armored else 0.62
    abox(name + "_back", (0.50, 0.11, back_h), L((0, 0.27, back_h / 2 - 0.02)), "M_Seat", grp, rot=(-0.12, 0, yaw), bevel=0.035)
    if armored:
        abox(name + "_armour", (0.54, 0.04, back_h + 0.1), L((0, 0.35, back_h / 2)), "M_IntGrey", grp, rot=(-0.12, 0, yaw), bevel=0.01)
    abox(name + "_head", (0.30, 0.10, 0.20), L((0, 0.33, back_h + 0.10)), "M_Seat", grp, rot=(-0.12, 0, yaw), bevel=0.03)
    for sx in (-0.29, 0.29):
        abox(name + "_arm%s" % sx, (0.06, 0.40, 0.06), L((sx, 0.02, 0.18)), "M_IntGrey", grp, rot=rz, bevel=0.015)
        rod(name + "_strap%s" % sx, L((sx * 0.5, 0.22, 0.65)), L((sx * 0.35, -0.05, 0.25)), 0.018, "M_Cloth", grp, seg=4)
    abox(name + "_chute", (0.40, 0.12, 0.40), L((0, 0.38 if not armored else 0.40, 0.25)), "M_Cloth", grp, rot=rz, bevel=0.04)
    if role:
        SEATS[role] = (tuple(p), yaw)


# ---------------------------------------------------------------- forward cabin shell
def inner_prof(y):
    zc, hw, ht, hb, n = fus_prof(y)[:5]
    return (zc, hw - 0.07, ht - 0.07, hb - 0.07, n)


def ceil_z(y, x=0.0):
    """Inner (quilt) surface height above (x, y)."""
    zc, hw, ht, hb, n = inner_prof(y)
    return zc + ht * max(0.0, 1 - abs(x / hw) ** n) ** (1.0 / n)


iys = [y for y in ys if -21.85 <= y <= -12.6] + [-21.85, -12.6]
iys = sorted(set(round(y, 3) for y in iys))
WIN_IN = [(a - 0.05, b + 0.05, c - 2, d + 2) for (a, b, c, d) in GLASS if a < -15]


def quilt_mat(y, a, z):
    return 1 if in_panes(y, a, WIN_IN) else 0


q = loft("CabinQuilt", iys, inner_prof, ["M_Quilt", "M_Hole"], I, seg=64, mat_fn=quilt_mat, angle_range=(-34, 214), inner=True,
         uv_tile=1.0)
delete_faces_mat(q, 1)
# window reveals (dark frames around the glass, inside)
for (y0, y1, a0, a1) in WIN_IN:
    for yy in (y0, y1):
        zc, hw, ht, hb, n = inner_prof(yy)[:5]
        pa = section_pt(math.radians(a0), zc, hw, ht, hb, n); pb = section_pt(math.radians(a1), zc, hw, ht, hb, n)
        pa.y = pb.y = yy
        rod("WinFrame_%s_%s" % (yy, a0), pa, pb, 0.022, "M_IntGrey", I, seg=5)

# floors (nose 2.5, cockpit 2.9) with the ladder hole at y -13.62 .. -12.98
abox("FloorNose", (1.80, 1.85, 0.05), (0, -20.825, 2.475), "M_Floor", I, uvscale=1.0)
abox("StepRiser", (1.80, 0.05, 0.40), (0, -19.9, 2.70), "M_IntGrey", I)
abox("FloorMain", (2.30, 4.25, 0.05), (0, -17.775, FLOOR_Z - 0.025), "M_Floor", I, uvscale=1.0)
abox("FloorRiser2", (2.30, 0.05, 0.15), (0, -15.65, FLOOR_Z + 0.075), "M_IntGrey", I)
abox("FloorRaised", (2.30, 2.03, 0.05), (0, -14.635, FLOOR2_Z - 0.025), "M_Floor", I, uvscale=1.0)
abox("FloorAft", (2.30, 0.38, 0.05), (0, -12.79, FLOOR2_Z - 0.025), "M_Floor", I, uvscale=1.0)
for sx in (1, -1):
    abox("FloorHoleSide_%d" % sx, (0.83, 0.64, 0.05), (sx * 0.735, -13.30, FLOOR2_Z - 0.025), "M_Floor", I, uvscale=1.0)
for sx in (1, -1):  # railing around the hatch
    rod("HatchRail_%d" % sx, (sx * 0.34, -13.62, FLOOR2_Z), (sx * 0.34, -13.62, FLOOR2_Z + 0.9), 0.02, "M_Chrome", I, seg=6)
    rod("HatchRailH_%d" % sx, (sx * 0.34, -13.62, FLOOR2_Z + 0.9), (sx * 0.34, -12.98, FLOOR2_Z + 0.9), 0.02, "M_Chrome", I, seg=6)
abox("HatchCover", (0.62, 0.05, 0.60), (0, -12.95, FLOOR2_Z + 0.30), "M_IntGrey", I, bevel=0.01)   # opened floor hatch (vertical)
# bulkheads (elliptic discs): nose (radar compartment) and rear pressure bulkhead with door
for name, yy, door in (("BulkNose", -21.86, False), ("BulkRear", -12.58, True)):
    zc, hw, ht, hb, n = inner_prof(yy)[:5]
    bm = bm_cyl(1.0, 0.05, (0, yy, 0), AX_ROT[1], 32)
    for v in bm.verts:
        v.co.x *= hw; v.co.z = zc + v.co.z * (ht if v.co.z > 0 else hb)
    aobj(name, bm, "M_Quilt", I, smooth=False, uv="box", uvscale=1.0)
    if door:
        abox("RearDoor", (0.70, 0.06, 1.60), (0, yy - 0.03, FLOOR2_Z + 0.82), "M_IntPaint", I, bevel=0.03)
        acyl("RearDoorWheel", 0.14, 0.04, (0, yy - 0.08, FLOOR2_Z + 1.05), "M_Chrome", I, axis=1, seg=14)
abox("RadarUnit", (1.1, 0.35, 0.8), (0, -21.66, 3.25), "M_IntGrey", I, bevel=0.03)
panel("RadarFace", (0.9, 0.03, 0.6), (0, -21.47, 3.3), Q_NAV, I, axes=(0, 2), flip=(True, False))

# ---------------------------------------------------------------- cockpit
for role, sx in (("PILOT", 0.55), ("COPILOT", -0.55)):
    crew_seat("Seat" + role, (sx, -18.25, 3.36), 0.0, I, armored=True, role=role)
    # yoke: column + ram's-horn wheel
    rod("Column_" + role, (sx, -19.05, FLOOR_Z), (sx, -18.92, 3.78), 0.035, "M_IntGrey", I, seg=8)
    abox("YokeHub_" + role, (0.10, 0.08, 0.10), (sx, -18.90, 3.80), "M_Black", I, bevel=0.02)
    for hx in (-1, 1):
        rod("YokeArm_%s_%d" % (role, hx), (sx, -18.88, 3.80), (sx + hx * 0.18, -18.86, 3.84), 0.018, "M_Black", I, seg=6)
        rod("YokeHorn_%s_%d" % (role, hx), (sx + hx * 0.18, -18.86, 3.84), (sx + hx * 0.20, -18.84, 3.68), 0.022, "M_Black", I, seg=6)
    for px in (-0.13, 0.13):
        abox("Pedal_%s_%s" % (role, px), (0.09, 0.04, 0.22), (sx + px, -19.15, FLOOR_Z + 0.15), "M_IntGrey", I, rot=(0.4, 0, 0))
    abox("SideConsole_" + role, (0.30, 1.4, 0.45), (sx * 1.85, -18.4, FLOOR_Z + 0.42), "M_IntPaint", I, bevel=0.02)
    panel("SideConsoleTop_" + role, (0.28, 1.35, 0.02), (sx * 1.85, -18.4, FLOOR_Z + 0.655), Q_OVER, I, axes=(0, 1), flip=(True, True))
panel("MainPanel", (2.10, 0.07, 0.62), (0, -19.30, 3.95), Q_TU95, I, rot=(0.25, 0, 0), axes=(0, 2), flip=(True, False))
abox("GlareShield", (2.15, 0.36, 0.05), (0, -19.40, 4.29), "M_Black", I, rot=(0.12, 0, 0), bevel=0.02)
abox("PanelLegs", (2.0, 0.25, 0.22), (0, -19.50, 3.55), "M_IntPaint", I, bevel=0.02)
abox("Pedestal", (0.34, 1.05, 0.62), (0, -18.55, FLOOR_Z + 0.31), "M_IntPaint", I, bevel=0.02)
panel("PedestalTop", (0.32, 1.0, 0.02), (0, -18.55, FLOOR_Z + 0.63), Q_OVER, I, axes=(0, 1), flip=(True, True))
for k in range(4):  # four throttle levers (NK-12MP) + RPM levers
    rod("Throttle_%d" % k, (-0.12 + k * 0.08, -18.75, FLOOR_Z + 0.64), (-0.12 + k * 0.08, -18.80, FLOOR_Z + 0.88), 0.012, "M_Chrome", I, seg=6)
    aobj("ThrottleKnob_%d" % k, bm_sphere(0.028, (-0.12 + k * 0.08, -18.80, FLOOR_Z + 0.89), 8, 4), "M_Black", I)
panel("OverheadPanel", (0.85, 1.05, 0.08), (0, -17.85, ceil_z(-17.85, 0.43) - 0.06), Q_OVER, I, axes=(0, 1), flip=(True, False))
rod("Compass", (0, -19.5, 4.55), (0, -19.5, 4.70), 0.05, "M_Black", I, seg=10)

# flight engineer (right side, facing the right-hand wall) and radio operator (left, facing the left wall)
crew_seat("SeatENG", (-0.45, -15.9, 3.36), -PI / 2, I, armored=False, role="ENG")
panel("EngPanel", (0.06, 1.5, 1.0), (-1.05, -15.9, 3.85), Q_TU160, I, axes=(1, 2))
abox("EngDesk", (0.45, 1.5, 0.05), (-0.90, -15.9, 3.30), "M_IntPaint", I, bevel=0.01)
abox("EngDeskBase", (0.40, 1.4, 0.40), (-0.92, -15.9, 3.08), "M_IntPaint", I, bevel=0.02)
crew_seat("SeatRADIO", (0.45, -15.0, FLOOR2_Z + 0.46), PI / 2, I, armored=False, role="RADIO")
for k in range(3):
    ob = panel("RadioRack_%d" % k, (0.40, 0.55, 0.30), (0.98, -15.35 + k * 0.6 - 0.3, 4.08), Q_OVER if k != 1 else Q_NAV, I, axes=(1, 2), flip=(True, False))
    abox("RadioRackLow_%d" % k, (0.42, 0.55, 0.32), (0.98, -15.35 + k * 0.6 - 0.3, 3.68), "M_IntGrey", I, bevel=0.02)
abox("RadioDesk", (0.45, 1.6, 0.05), (0.85, -15.0, 3.45), "M_IntPaint", I, bevel=0.01)
abox("MorseKey", (0.08, 0.12, 0.04), (0.78, -15.0, 3.50), "M_Black", I, bevel=0.01)
acyl("Headset_R", 0.06, 0.03, (0.8, -14.7, 3.51), "M_Black", I, axis=0, seg=10)

# navigator (nose, floor 2.5)
crew_seat("SeatNAV", (0.0, -20.65, 2.96), 0.0, I, armored=False, role="NAV")
abox("NavDesk", (1.2, 0.5, 0.05), (0, -21.28, 3.20), "M_IntPaint", I, bevel=0.01)
abox("NavDeskBase", (1.1, 0.45, 0.6), (0, -21.30, 2.88), "M_IntPaint", I, bevel=0.02)
abox("NavChart", (0.55, 0.38, 0.005), (0.2, -21.25, 3.228), "M_Paper", I, bevel=0.0)
rod("Bombsight", (-0.35, -21.2, 2.5), (-0.35, -21.2, 3.45), 0.07, "M_IntGrey", I, seg=12)
rod("BombsightEye", (-0.35, -21.2, 3.45), (-0.35, -21.0, 3.52), 0.04, "M_Black", I, seg=8)
abox("BombsightBox", (0.28, 0.32, 0.26), (-0.35, -21.22, 3.30), "M_IntGrey", I, bevel=0.03)
abox("NavLamp", (0.12, 0.06, 0.04), (0.45, -21.3, 3.70), "M_LampWhite", I, dirt=False)

# aisle equipment
for k in range(3):
    acyl("OxyBottle_%d" % k, 0.11, 0.85, (-0.98, -14.0 + k * 0.26, FLOOR2_Z + 0.45), "M_Oxy", I, seg=12)
acyl("Extinguisher", 0.08, 0.45, (0.95, -12.85, FLOOR2_Z + 0.75), "M_Red", I, seg=12)
rod("ExtNozzle", (0.95, -12.85, FLOOR2_Z + 0.98), (0.95, -12.95, FLOOR2_Z + 1.05), 0.02, "M_Black", I, seg=6)
for k in range(3):
    abox("ChutePack_%d" % k, (0.36, 0.30, 0.36), (0.92, -14.3 + k * 0.4, FLOOR2_Z + 0.95), "M_Cloth", I, bevel=0.05)
abox("ChuteShelf", (0.42, 1.3, 0.03), (0.92, -13.9, FLOOR2_Z + 0.75), "M_IntGrey", I)
acyl("Thermos", 0.06, 0.32, (-0.95, -12.85, FLOOR2_Z + 1.0), "M_Chrome", I, seg=10)
abox("FirstAid", (0.30, 0.10, 0.22), (-0.9, -12.68, FLOOR2_Z + 1.4), "M_Paper", I, bevel=0.02)
for sx in (1, -1):
    rod("HandRail_%d" % sx, (sx * 0.45, -16.6, ceil_z(-16.6, 0.45) - 0.09), (sx * 0.45, -12.8, ceil_z(-12.8, 0.45) - 0.09), 0.018,
        "M_Chrome", I, seg=6)
for k, yy in enumerate((-20.8, -17.0, -14.2)):
    abox("DomeLamp_%d" % k, (0.18, 0.18, 0.04), (0, yy, ceil_z(yy) - 0.03), "M_LampWhite", I, dirt=False)

# ---------------------------------------------------------------- tail cabin (gunner + observer)
tys = [y for y in ys if 16.6 <= y <= 21.4] + [16.6, 21.4]
tys = sorted(set(round(y, 3) for y in tys))
TWIN = [(a - 0.05, b + 0.05, c - 2, d + 2) for (a, b, c, d) in GLASS if a > 20]
qr = loft("TailQuilt", tys, inner_prof, ["M_Quilt", "M_Hole"], IR, seg=64, mat_fn=lambda y, a, z: 1 if in_panes(y, a, TWIN) else 0,
          angle_range=(-48, 228), inner=True, uv_tile=1.0)
delete_faces_mat(qr, 1)
abox("TFloorA", (1.5, 0.55, 0.05), (0, 17.03, TFLOOR_Z - 0.025), "M_Floor", IR, uvscale=1.0)
abox("TFloorB", (1.5, 0.95, 0.05), (0, 18.525, TFLOOR_Z - 0.025), "M_Floor", IR, uvscale=1.0)
abox("TStepRiser", (1.3, 0.05, 0.40), (0, 19.0, TFLOOR_Z + 0.20), "M_IntGrey", IR)
abox("TFloorC", (1.3, 1.6, 0.05), (0, 19.8, TFLOOR2_Z - 0.025), "M_Floor", IR, uvscale=1.0)
for sx in (1, -1):
    abox("TFloorS_%d" % sx, (0.45, 0.62, 0.05), (sx * 0.525, 17.62, TFLOOR_Z - 0.025), "M_Floor", IR, uvscale=1.0)
crew_seat("SeatGUNNER", (0.0, 20.75, TFLOOR2_Z + 0.46), PI, IR, armored=True, role="GUNNER")
abox("GunSight", (0.22, 0.28, 0.24), (0.0, 21.30, 4.38), "M_IntGrey", IR, bevel=0.03)
abox("GunSightGlass", (0.18, 0.02, 0.14), (0.0, 21.45, 4.55), "M_Glass", IR, rot=(0.5, 0, 0), bevel=0.0, dirt=False)
for sx in (1, -1):
    rod("GunHandle_%d" % sx, (sx * 0.22, 21.2, 4.0), (sx * 0.22, 21.25, 4.2), 0.02, "M_Black", IR, seg=6)
    abox("AmmoBox_%d" % sx, (0.30, 0.6, 0.40), (sx * 0.45, 20.2, TFLOOR2_Z + 0.21), "M_Ammo", IR, bevel=0.02)
abox("ArmourGlass", (0.60, 0.07, 0.42), (0, 21.62, 4.48), "M_Glass", IR, bevel=0.0, dirt=False)
crew_seat("SeatOBS", (0.30, 16.85, TFLOOR_Z + 0.46), PI / 2, IR, armored=False, role="OBSERVER")
panel("ObsPanel", (0.04, 0.6, 0.4), (-0.85, 17.0, 3.9), Q_OVER, IR, axes=(1, 2))
acyl("TailOxy", 0.10, 0.8, (-0.5, 19.5, TFLOOR2_Z + 0.42), "M_Oxy", IR, seg=12)
abox("TailLamp", (0.16, 0.16, 0.04), (0, 18.6, ceil_z(18.6) - 0.03), "M_LampWhite", IR, dirt=False)

# ---------------------------------------------------------------- gameplay markers (exported as empties -> mod data)
MK = {}
def walk(name, x0, y0, z0, x1, y1, z1=None):
    if z1 is None:
        z1 = min(ceil_z(y0), ceil_z(y1)) - 0.02
    box_marker("WALK_" + name, (x0, y0, z0), (x1, y1, z1))
    MK["WALK_" + name] = ((x0, y0, z0), (x1, y1, z1))
walk("nose", -0.55, -21.4, 2.5, 0.55, -19.95)
walk("strip", -0.12, -19.95, FLOOR_Z, 0.12, -18.0)
walk("aisle", -0.30, -18.6, FLOOR_Z, 0.30, -15.65)
walk("aisle2", -0.30, -15.65, FLOOR2_Z, 0.30, -12.95)
walk("rear", -0.60, -14.6, FLOOR2_Z, 0.60, -12.95)
walk("tail", -0.45, 16.9, TFLOOR_Z, 0.45, 19.0)
walk("gun", -0.35, 19.0, TFLOOR2_Z, 0.35, 20.3)
for nm, (mn, mx) in (("F", ((-0.28, -13.60, 0.0), (0.28, -13.00, FLOOR2_Z))), ("R", ((-0.22, 17.50, 0.0), (0.22, 18.00, TFLOOR_Z)))):
    box_marker("LADDER_" + nm, mn, mx)
    empty("EXIT_" + nm, ((mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, 0.0), 0.4)
    MK["LADDER_" + nm] = (mn, mx)
for role, (p, yaw) in SEATS.items():
    empty("SEAT_" + role, p, 0.35, 'ARROWS', rot=(0, 0, yaw))
json.dump({"seats": {k: [list(v[0]), v[1]] for k, v in SEATS.items()}, "markers": MK},
          open("/home/user/.cache/air/t95_int.json", "w"), indent=1)
print("STEP3", stats(), sorted(SEATS))
