# Tu-160 STEP 3: crew cabin. Two pilots (fighter-style sticks) in front, navigator-operator and navigator behind facing
# forward at their CRT consoles, all on K-36LM ejection seats; rear area with galley, toilet, rest bunk and the floor hatch
# to the entry ladder. Walk zones / seats / ladder / exit markers as empties.
amat("M_Quilt", "T_Quilt160", 0.9)
amat("M_Floor", "T_Floor", 0.75, 0.3)
amat("M_Panel", "T_Panels", 0.5, 0.1)
amat("M_IntPaint", None, 0.55, 0.0, factor=(0.42, 0.47, 0.52, 1))           # grey-blue cockpit paint
amat("M_IntGrey", None, 0.6, 0.0, factor=(0.30, 0.31, 0.33, 1))
amat("M_Seat", "T_Seat", 0.8)
amat("M_Cloth", None, 0.9, 0.0, factor=(0.36, 0.38, 0.30, 1))
amat("M_Yellow", None, 0.5, 0.0, factor=(0.92, 0.75, 0.10, 1))
amat("M_Red", None, 0.45, 0.0, factor=(0.70, 0.07, 0.05, 1))
amat("M_Paper", None, 0.9, 0.0, factor=(0.88, 0.86, 0.78, 1))
amat("M_White", None, 0.5, 0.0, factor=(0.85, 0.86, 0.86, 1))
Q_TU95, Q_TU160, Q_OVER, Q_NAV = (0.0, 0.5, 0.5, 1.0), (0.5, 0.5, 1.0, 1.0), (0.0, 0.0, 0.5, 0.5), (0.5, 0.0, 1.0, 0.5)
I = "Int"
SEATS = {}
CAB_Y0, CAB_Y1 = -24.9, -18.85


def panel(name, size, loc, rect, grp, rot=(0, 0, 0), axes=None, flip=(False, False)):
    ob = abox(name, size, loc, "M_Panel", grp, rot=rot, bevel=0.008)
    uv_planar(ob, rect, axes, flip)
    return ob


def inner_prof(y):
    zc, hw, ht, hb, n = fus_prof(y)[:5]
    return (zc, hw - 0.07, ht - 0.07, hb - 0.07, n)


def ceil_z(y, x=0.0):
    zc, hw, ht, hb, n = inner_prof(y)
    return zc + ht * max(0.0, 1 - abs(x / hw) ** n) ** (1.0 / n)


def k36(name, loc, yaw, role):
    """K-36LM ejection seat: frame with rocket rails, pan, tall back, head box, yellow/black ejection handles."""
    p = Vector(loc)
    R = Matrix.Rotation(yaw, 4, 'Z')
    def L(v):
        return p + (R @ Vector(v).to_4d()).to_3d()
    rz = (0, 0, yaw)
    abox(name + "_bucket", (0.56, 0.55, 0.36), L((0, 0.02, -0.24)), "M_IntGrey", I, rot=rz, bevel=0.03)
    abox(name + "_pan", (0.50, 0.50, 0.10), L((0, 0, -0.02)), "M_Seat", I, rot=rz, bevel=0.035)
    abox(name + "_back", (0.50, 0.12, 0.82), L((0, 0.30, 0.42)), "M_Seat", I, rot=(-0.17, 0, yaw), bevel=0.035)
    abox(name + "_headbox", (0.40, 0.26, 0.34), L((0, 0.42, 1.02)), "M_IntGrey", I, rot=(-0.17, 0, yaw), bevel=0.04)
    abox(name + "_headpad", (0.26, 0.06, 0.20), L((0, 0.28, 0.98)), "M_Seat", I, rot=(-0.17, 0, yaw), bevel=0.02)
    for sx in (-0.30, 0.30):
        rod(name + "_rail%s" % sx, L((sx, 0.40, -0.40)), L((sx, 0.58, 1.10)), 0.03, "M_DarkMetal", I, seg=6)
        rod(name + "_strap%s" % sx, L((sx * 0.5, 0.26, 0.75)), L((sx * 0.35, -0.05, 0.20)), 0.018, "M_Cloth", I, seg=4)
        rod(name + "_handle%s" % sx, L((sx * 0.7, -0.24, -0.02)), L((sx * 0.7, -0.30, 0.16)), 0.016, "M_Yellow", I, seg=6)
    abox(name + "_kit", (0.44, 0.12, 0.08), L((0, -0.22, -0.12)), "M_Cloth", I, rot=rz, bevel=0.02)
    SEATS[role] = (tuple(p), yaw)


# ---------------------------------------------------------------- cabin shell
iys = sorted(set([round(y, 3) for y in ys if CAB_Y0 <= y <= CAB_Y1] + [CAB_Y0, CAB_Y1]))
WIN_IN = [(a - 0.05, b + 0.05, c - 2, d + 2) for (a, b, c, d) in GLASS]
q = loft("CabinQuilt", iys, inner_prof, ["M_Quilt", "M_Hole"], I, seg=64, mat_fn=lambda y, a, z: 1 if in_panes(y, a, WIN_IN) else 0,
         angle_range=(-28, 208), inner=True, uv_tile=1.0)
delete_faces_mat(q, 1)
for (y0, y1, a0, a1) in WIN_IN:
    for yy in (y0, y1):
        zc, hw, ht, hb, n = inner_prof(yy)[:5]
        pa = section_pt(math.radians(a0), zc, hw, ht, hb, n); pb = section_pt(math.radians(a1), zc, hw, ht, hb, n)
        pa.y = pb.y = yy
        rod("WinFrame_%s_%s" % (yy, a0), pa, pb, 0.022, "M_IntGrey", I, seg=5)
# floor with the ladder opening y -19.6 .. -19.0, x +-0.30
abox("FloorMain", (2.40, 5.30, 0.05), (0, -22.25, FLOOR_Z - 0.025), "M_Floor", I, uvscale=1.0)
abox("FloorAft", (2.80, 0.15, 0.05), (0, -18.925, FLOOR_Z - 0.025), "M_Floor", I, uvscale=1.0)
for sx in (1, -1):
    abox("FloorHoleSide_%d" % sx, (1.10, 0.60, 0.05), (sx * 0.85, -19.30, FLOOR_Z - 0.025), "M_Floor", I, uvscale=1.0)
    rod("HatchRail_%d" % sx, (sx * 0.34, -19.6, FLOOR_Z), (sx * 0.34, -19.6, FLOOR_Z + 0.9), 0.02, "M_Chrome", I, seg=6)
    rod("HatchRailH_%d" % sx, (sx * 0.34, -19.6, FLOOR_Z + 0.9), (sx * 0.34, -19.0, FLOOR_Z + 0.9), 0.02, "M_Chrome", I, seg=6)
abox("HatchCover", (0.58, 0.05, 0.58), (0, -18.97, FLOOR_Z + 0.29), "M_IntGrey", I, bevel=0.01)
for name, yy, door in (("BulkFront", CAB_Y0 - 0.01, False), ("BulkRear", CAB_Y1 + 0.02, True)):
    zc, hw, ht, hb, n = inner_prof(yy)[:5]
    bm = bm_cyl(1.0, 0.05, (0, yy, 0), AX_ROT[1], 32)
    for v in bm.verts:
        v.co.x *= hw; v.co.z = zc + v.co.z * (ht if v.co.z > 0 else hb)
    aobj(name, bm, "M_Quilt", I, smooth=False, uv="box", uvscale=1.0)
    if door:
        abox("RearDoor", (0.70, 0.06, 1.75), (0.45, yy - 0.03, FLOOR_Z + 0.9), "M_IntPaint", I, bevel=0.03)
        acyl("RearDoorWheel", 0.12, 0.04, (0.45, yy - 0.08, FLOOR_Z + 1.1), "M_Chrome", I, axis=1, seg=14)

# ---------------------------------------------------------------- pilots
for role, sx in (("PILOT", 0.62), ("COPILOT", -0.62)):
    k36("Seat" + role, (sx, -23.6, FLOOR_Z + 0.50), 0.0, role)
    rod("Stick_" + role, (sx, -24.05, FLOOR_Z + 0.02), (sx, -24.02, FLOOR_Z + 0.62), 0.025, "M_IntGrey", I, seg=8)
    abox("StickGrip_" + role, (0.06, 0.07, 0.14), (sx, -24.02, FLOOR_Z + 0.70), "M_Black", I, bevel=0.02)
    acyl("StickBoot_" + role, 0.09, 0.08, (sx, -24.05, FLOOR_Z + 0.04), "M_Black", I, seg=10)
    for px in (-0.15, 0.15):
        abox("Pedal_%s_%s" % (role, px), (0.09, 0.04, 0.22), (sx + px, -24.6, FLOOR_Z + 0.16), "M_IntGrey", I, rot=(0.4, 0, 0))
    abox("SideConsole_" + role, (0.32, 1.3, 0.50), (sx * 1.62, -23.6, FLOOR_Z + 0.45), "M_IntPaint", I, bevel=0.02)
    panel("SideConsoleTop_" + role, (0.30, 1.25, 0.02), (sx * 1.62, -23.6, FLOOR_Z + 0.71), Q_OVER, I, axes=(0, 1), flip=(True, True))
panel("MainPanel", (2.10, 0.07, 0.62), (0, -24.62, FLOOR_Z + 1.02), Q_TU160, I, rot=(0.22, 0, 0), axes=(0, 2), flip=(True, False))
abox("GlareShield", (2.15, 0.34, 0.05), (0, -24.72, FLOOR_Z + 1.36), "M_Black", I, rot=(0.10, 0, 0), bevel=0.02)
abox("PanelBase", (2.0, 0.25, 0.25), (0, -24.75, FLOOR_Z + 0.60), "M_IntPaint", I, bevel=0.02)
abox("CenterConsole", (0.36, 1.1, 0.62), (0, -23.75, FLOOR_Z + 0.31), "M_IntPaint", I, bevel=0.02)
panel("CenterConsoleTop", (0.34, 1.05, 0.02), (0, -23.75, FLOOR_Z + 0.63), Q_OVER, I, axes=(0, 1), flip=(True, True))
for k in range(4):  # four NK-32 throttles with afterburner gate
    rod("Throttle_%d" % k, (-0.12 + k * 0.08, -23.95, FLOOR_Z + 0.64), (-0.12 + k * 0.08, -24.02, FLOOR_Z + 0.90), 0.012, "M_Chrome", I, seg=6)
    aobj("ThrottleKnob_%d" % k, bm_sphere(0.03, (-0.12 + k * 0.08, -24.02, FLOOR_Z + 0.91), 8, 4), "M_Black", I)
rod("ABGate", (-0.16, -23.88, FLOOR_Z + 0.66), (0.16, -23.88, FLOOR_Z + 0.66), 0.008, "M_Red", I, seg=4)
abox("HUDCombiner", (0.30, 0.02, 0.22), (0.62, -24.60, FLOOR_Z + 1.55), "M_Glass", I, rot=(0.35, 0, 0), bevel=0.0, dirt=False)

# ---------------------------------------------------------------- navigator-operator (left) and navigator (right, bombing station)
k36("SeatOPER", (0.62, -22.0, FLOOR_Z + 0.50), 0.0, "OPERATOR")
k36("SeatNAV", (-0.62, -22.0, FLOOR_Z + 0.50), 0.0, "NAV")
for sx, rect in ((0.62, Q_NAV), (-0.62, Q_NAV)):
    abox("NavConsole_%s" % sx, (0.80, 0.40, 0.75), (sx, -22.75, FLOOR_Z + 0.375), "M_IntPaint", I, bevel=0.02)
    panel("NavScreen_%s" % sx, (0.74, 0.06, 0.62), (sx, -22.92, FLOOR_Z + 1.12), rect, I, rot=(0.18, 0, 0), axes=(0, 2), flip=(True, False))
    abox("NavDesk_%s" % sx, (0.78, 0.30, 0.04), (sx, -22.52, FLOOR_Z + 0.77), "M_IntGrey", I, bevel=0.01)
    abox("NavTrackball_%s" % sx, (0.10, 0.10, 0.04), (sx + 0.2, -22.50, FLOOR_Z + 0.80), "M_Black", I, bevel=0.02)
abox("NavChart", (0.42, 0.28, 0.004), (-0.55, -22.52, FLOOR_Z + 0.793), "M_Paper", I, bevel=0.0)
abox("BombsightScope", (0.22, 0.30, 0.22), (-0.95, -22.70, FLOOR_Z + 1.0), "M_IntGrey", I, bevel=0.03)
rod("BombsightEye", (-0.95, -22.56, FLOOR_Z + 1.05), (-0.95, -22.46, FLOOR_Z + 1.08), 0.035, "M_Black", I, seg=8)

# ---------------------------------------------------------------- rear area: galley, toilet, bunk, oxygen, chutes
abox("Galley", (0.55, 0.70, 0.95), (1.05, -20.7, FLOOR_Z + 0.48), "M_White", I, bevel=0.02)
abox("GalleyPlate", (0.35, 0.30, 0.02), (1.0, -20.75, FLOOR_Z + 0.96), "M_DarkMetal", I, bevel=0.0)
acyl("GalleyThermos", 0.06, 0.30, (1.05, -20.45, FLOOR_Z + 1.11), "M_Chrome", I, seg=10)
abox("Toilet", (0.75, 0.85, 1.85), (-1.0, -20.3, FLOOR_Z + 0.93), "M_IntPaint", I, bevel=0.02)
abox("ToiletDoor", (0.03, 0.60, 1.60), (-0.62, -20.3, FLOOR_Z + 0.85), "M_IntGrey", I, bevel=0.01)
abox("Bunk", (0.62, 1.5, 0.12), (1.05, -19.4, FLOOR_Z + 0.55), "M_Seat", I, bevel=0.03)
abox("BunkFrame", (0.64, 1.52, 0.04), (1.05, -19.4, FLOOR_Z + 0.47), "M_DarkMetal", I, bevel=0.0)
for k in range(2):
    acyl("OxyBottle_%d" % k, 0.10, 0.75, (-1.15, -19.5 + k * 0.25, FLOOR_Z + 0.42), "M_Cloth", I, seg=12)
acyl("Extinguisher", 0.08, 0.45, (1.25, -21.25, FLOOR_Z + 0.75), "M_Red", I, seg=12)
for k, yy in enumerate((-23.0, -21.0, -19.6)):
    abox("DomeLamp_%d" % k, (0.18, 0.18, 0.04), (0, yy, ceil_z(yy) - 0.03), "M_LampWhite", I, dirt=False)
for sx in (1, -1):
    rod("HandRail_%d" % sx, (sx * 0.5, -21.4, ceil_z(-21.4, 0.5) - 0.09), (sx * 0.5, -19.0, ceil_z(-19.0, 0.5) - 0.09), 0.018, "M_Chrome", I, seg=6)

# ---------------------------------------------------------------- gameplay markers
MK = {}


def walk(name, x0, y0, z0, x1, y1, z1=None):
    if z1 is None:
        z1 = min(ceil_z(y0), ceil_z(y1)) - 0.02
    box_marker("WALK_" + name, (x0, y0, z0), (x1, y1, z1))
    MK["WALK_" + name] = ((x0, y0, z0), (x1, y1, z1))


walk("aisle", -0.24, -23.2, FLOOR_Z, 0.24, -18.95)
walk("rear", -0.55, -21.4, FLOOR_Z, 0.65, -18.95)
box_marker("LADDER_F", (-0.28, -19.58, 0.0), (0.28, -19.02, FLOOR_Z))
MK["LADDER_F"] = ((-0.28, -19.58, 0.0), (0.28, -19.02, FLOOR_Z))
empty("EXIT_F", (0, -19.3, 0.0), 0.4)
for role, (p, yaw) in SEATS.items():
    empty("SEAT_" + role, p, 0.35, 'ARROWS', rot=(0, 0, yaw))
json.dump({"seats": {k: [list(v[0]), v[1]] for k, v in SEATS.items()}, "markers": MK}, open("/home/user/.cache/air/t160_int.json", "w"), indent=1)
print("STEP3", stats(), sorted(SEATS))
