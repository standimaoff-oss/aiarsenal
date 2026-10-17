# Landkreuzer P.1000 "Ratte" (Grote / Krupp project 1942) — dimensions of the project: 35 m x 14 m x 11 m, ~1000 t.
# 3 tracks of 1.2 m per side (6 total), 2 x MAN V12Z32/44 marine diesels. Main turret of the Gneisenau "Caesar"
# type with 2 x 28 cm SK C/34 L/54.5 (15.26 m barrels) + 10.5 m rangefinder; rear turret with 12.8 cm KwK 44;
# 8 x 2 cm Flak 38 on the deck, 2 x 15 mm MG 151/15 on the main turret. Deck railings + side ladders (walkable deck).
# Nodes: GunVehicle contract; extra "sec_yaw" (rear turret, follows the main turret in Java).
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
amat("M_Elfenbein", None, 0.85, 0.02, factor=(0.80, 0.76, 0.62, 1))       # German interior ivory
amat("M_IntLamp", None, 0.3, 0.0, factor=(1.0, 0.95, 0.78, 1))
amat("M_ShellHE", None, 0.6, 0.3, factor=(0.40, 0.38, 0.16, 1))
P = "RAT"
G, GT, GG, GB = P + "_hull", P + "_turret", P + "_gun", P + "_barrel"
GS = P + "_sec"
BODY = "M_Grau"                  # r9: plain Panzergrau, no camouflage
HL, HW = 17.5, 7.0
TWD = 1.2
TXS = [6.25, 4.90, 3.55]         # track centres per side (outer -> inner)
RR, TH = 1.00, 0.20
ZW = RR + TH
ROAD = [(-12.5 + i * 2.5, ZW) for i in range(11)]
SPK = (14.8, 2.05)               # rear sprocket (engines in the rear)
IDL = (-14.8, 1.95)
TC = Vector((0, -3.0, 7.0))
TRN = Vector((0, -7.4, 8.7))
GX = 1.85                        # gun spacing (half)
MUZ_Y = TRN.y - 14.2
TC2 = Vector((0, 11.0, 7.0))     # rear 12.8 cm turret (yaw pivot; ring on a 0.55 m barbette, faces aft at rest)
GS2 = P + "_secp"
GI = P + "_int"                   # interior (own node so the LOD drops it)
INT = "M_Elfenbein"
# tubs outside the main turret sweep (> 10.1 m from its ring) and clear of the rear barbette; idle guns sit below both turrets' barrels
FLAK = [(5.55, -11.8), (-5.55, -11.8), (5.55, 5.9), (-5.55, 5.9), (5.55, 9.6), (-5.55, 9.6), (2.3, 14.5), (-2.3, 14.5)]
FLAK_Z = 6.20                     # floor of the sunken gun tubs (deck 7.0): idle guns stay below the 28 cm barrels
DOOR = (7.0, 4.0)                 # side door (x, y) on the left hull side, floor at 3.4
FLOOR, CEIL = 3.4, 6.8

WHEELS, LINKS = [], []
for side, sgn in (("L", 1), ("R", -1)):
    for b, tx in enumerate(TXS):
        W, links, pitch = ww2_gear(P, side, sgn * tx, ROAD, RR, "steel", 0.40, SPK, 1.40, IDL, 1.30, [], 0.1, TWD, 1.0, TH,
                                   mat=BODY, twin_gap=0.30, teeth=14, wseg=12, spk_gap=0.25, hidden=True,
                                   idx0=b * len(ROAD), belt=("" if b == 0 else "b%d" % b))
        WHEELS += W
        LINKS += links
print("TRACK_PITCH", pitch, "LINKS", len(LINKS), "NROAD", 3 * len(ROAD))

# ---------------------------------------------------------------- hull
lower = sym(box_pts(0, 2.85, -14.6, 14.6, 1.0, 3.2)) + sym([(2.85, -HL, 3.0), (2.85, HL, 3.2)])
chull(P + "_lower", lower, BODY, G, bevel=0.05)
upper = sym([(HW, -15.6, 3.0), (HW, 15.8, 3.0), (HW, -HL, 3.4), (HW, -13.2, 7.0), (HW, 15.6, 7.0), (HW, HL, 4.0)])
chull(P + "_upper", upper, BODY, G, bevel=0.08, uvscale=0.12)


def cut(obname, cutter_fn):
    """Boolean-subtract a temporary cutter from an armour body (door openings, sunken gun tubs)."""
    ob = bpy.data.objects[obname]
    ct = cutter_fn()
    m = ob.modifiers.new("cut", 'BOOLEAN')
    m.operation = 'DIFFERENCE'
    m.object = ct
    m.solver = 'EXACT'
    with bpy.context.temp_override(object=ob, active_object=ob, selected_objects=[ob], selected_editable_objects=[ob]):
        bpy.ops.object.modifier_apply(modifier="cut")
    bpy.data.objects.remove(ct)


def cutter_box(size, loc):
    def f():
        o = abox("CUTTER", size, loc, "M_Dark", "CUT", bevel=0.0)
        return o if o is not None else bpy.data.objects["CUTTER"]
    return f


def cutter_cyl(r, h, loc):
    def f():
        o = acyl("CUTTER", r, h, loc, "M_Dark", "CUT", seg=20)
        return o if o is not None else bpy.data.objects["CUTTER"]
    return f


# side door 1.5 x 2.2 m through the left hull side (+X), and 8 sunken tubs (r 0.95, 0.8 m deep) for the Flak 38
cut(P + "_upper", cutter_box((1.2, 1.5, 2.2), (HW, DOOR[1], FLOOR + 1.1)))
# hollow the armour body: the room volume (slightly larger than the lining) is removed, so the hull becomes a
# 5-8 cm shell whose only opening is the side door - the interior is reachable on foot and the deck collision stays hollow
cut(P + "_upper", cutter_box((13.5, 15.50 + 13.28, 3.67), (0, (15.50 - 13.28) / 2, 3.25 + 3.67 / 2)))
for (x, y) in FLAK:
    cut(P + "_upper", cutter_cyl(0.95, 1.0, (x, y, FLAK_Z + 0.5)))
for k, (x, y) in enumerate(FLAK):                   # tub wells: wall + floor (seen from the deck and from inside)
    lathe_y("%s_tub%d" % (P, k), [(0.0, 0.97), (0.86, 0.97), (0.86, 0.91), (0.0, 0.91)], (x, y, FLAK_Z - 0.04), [BODY], G, seg=20, axis='Z')
    acyl("%s_tubf%d" % (P, k), 0.95, 0.06, (x, y, FLAK_Z - 0.03), BODY, G, seg=20)
    lathe_y("%s_tubc%d" % (P, k), [(0.0, 1.10), (0.12, 1.10), (0.12, 0.96), (0.0, 0.96)], (x, y, 7.0), [BODY], G, seg=20, axis='Z')

for sx in (1, -1):
    abox("%s_skirt%d" % (P, sx), (0.25, 29.5, 1.9), (sx * (HW - 0.12), 0.0, 2.15), BODY, G, bevel=0.04)
    for k in range(20):
        acyl("%s_sb%d_%d" % (P, sx, k), 0.06, 0.06, (sx * HW, -14.0 + k * 1.47, 2.95), "M_Dark", G, axis=0, seg=6, dirt=False)
    for k in range(3):    # ladders up the hull sides (crew access to the deck)
        y = -8.0 + k * 9.0
        if sx == 1 and k == 1:
            y = -4.0                               # keep clear of the door stair
        for j in (-0.25, 0.25):
            rod("%s_lad%d_%d_%d" % (P, sx, k, int(j * 10)), Vector((sx * (HW + 0.12), y + j, 1.2)), Vector((sx * (HW + 0.12), y + j, 7.0)), 0.03, "M_Steel", G, seg=6)
        for r in range(15):
            rod("%s_rung%d_%d_%d" % (P, sx, k, r), Vector((sx * (HW + 0.12), y - 0.25, 1.4 + r * 0.38)), Vector((sx * (HW + 0.12), y + 0.25, 1.4 + r * 0.38)), 0.02, "M_Steel", G, seg=6)
    for k in range(2):
        rod("%s_ftow%d_%d" % (P, sx, k), Vector((sx * (1.5 + k * 2.0), -HL + 0.1, 2.2)), Vector((sx * (1.5 + k * 2.0), -HL - 0.4, 2.2)), 0.14, "M_Steel", G, seg=8)
    headlamp(P, "f%d" % sx, (sx * 5.5, -16.0, 4.8), G, mat=BODY)
# deck railings only outside the main turret's sweep (r 9.1 m about the ring) - lower than the 28 cm barrels
for sx in (1, -1):
    for (y0, y1) in ((-12.5, -9.2), (3.2, 15.0)):
        n = max(2, int((y1 - y0) / 2.2) + 1)
        for k in range(n):
            y = y0 + (y1 - y0) * k / (n - 1)
            rod("%s_st%d_%d_%d" % (P, sx, int(y0), k), Vector((sx * (HW - 0.2), y, 7.0)), Vector((sx * (HW - 0.2), y, 8.0)), 0.03, "M_Steel", G, seg=6)
        for h in (7.5, 8.0):
            rod("%s_rail%d_%d_%d" % (P, sx, int(y0), int(h * 10)), Vector((sx * (HW - 0.2), y0, h)), Vector((sx * (HW - 0.2), y1, h)), 0.025, "M_Steel", G, seg=6)
# front: low driver bridge (clears the 28 cm barrels at 0 deg), vision slits, bow MG positions
br = sym(box_pts(0, 1.6, -13.4, -11.6, 7.0, 8.05))
chull(P + "_bridge", br, BODY, G, bevel=0.06)
for k in range(5):
    abox("%s_brsl%d" % (P, k), (0.35, 0.02, 0.06), (-1.2 + k * 0.6, -13.42, 7.75), "M_Bore", G, bevel=0.0, dirt=False)
hatch(P, "br", (0.0, -12.5, 8.06), 0.35, G, mat=BODY)
gl_n = Vector((0, -3.6, 4.3)).normalized()
for sx in (1, -1):
    ball_mount(P, "bow%d" % sx, (sx * 3.0, -15.6, 4.9), gl_n + Vector((0, -0.6, -0.5)), G, r=0.16, mat=BODY)
# engine deck: cooling grilles beside the rear turret, exhaust mufflers on the rear plate, hatches, ventilators
for sx in (1, -1):
    grille(P, "eng%d" % sx, (sx * 4.6, 13.6, 7.05), 2.0, 4.2, G, n=12, mat=BODY)
    acyl("%s_muff%d" % (P, sx), 0.45, 2.4, (sx * 3.4, 16.95, 5.2), "M_Exhaust", G, axis=0, seg=16)
    for j in (-1, 1):
        rod("%s_mpipe%d_%d" % (P, sx, j), Vector((sx * 3.4 + j * 0.8, 16.55, 5.9)), Vector((sx * 3.4 + j * 0.8, 16.95, 5.55)), 0.12, "M_Exhaust", G, seg=10)
        abox("%s_mbr%d_%d" % (P, sx, j), (0.10, 0.50, 0.10), (sx * 3.4 + j * 1.0, 16.75, 4.85), "M_Steel", G, bevel=0.0)
    acyl("%s_mout%d" % (P, sx), 0.16, 0.30, (sx * 4.75, 16.95, 5.2), "M_Dark", G, axis=0, seg=12)
    for (x, y) in ((5.2, 7.6), (6.3, 13.2)):
        lathe_y("%s_vent%d_%d" % (P, sx, int(y)), [(0.0, 0.35), (0.6, 0.30), (0.7, 0.42), (0.85, 0.0)], (sx * x, y, 7.0), [BODY], G, seg=12, axis='Z')
for k, (x, y) in enumerate(((2.5, -12.5), (-2.5, -12.5), (3.6, 7.5), (-3.6, 7.5))):
    hatch(P, "dk%d" % k, (x, y, 7.02), 0.40, G, mat=BODY)


# 2 cm Flak 38 in the tubs: pedestal (hull) + rotating cradle/shield (flak_k) + elevating barrel (flak_k_p); built
# pointing to the bow at 0 deg elevation, Java aims each mount at the battery's target from its own position.
def flak38(k, x, y):
    c = Vector((x, y, FLAK_Z))
    lathe_y("%s_fp_%d" % (P, k), [(0.0, 0.50), (0.10, 0.50), (0.10, 0.16), (0.80, 0.13), (0.80, 0.0), (0.0, 0.0)], tuple(c), ["M_Grau"], G, seg=14, axis='Z')
    g, gp = "%s_fl%d" % (P, k), "%s_flp%d" % (P, k)
    pz = FLAK_Z + 0.95
    acyl("%s_fr_%d" % (P, k), 0.28, 0.16, (x, y, FLAK_Z + 0.86), "M_Grau", g, seg=14)
    for sx in (1, -1):
        abox("%s_fcheek%d_%d" % (P, k, sx), (0.05, 0.40, 0.34), (x + sx * 0.22, y, pz), "M_Grau", g, bevel=0.0)
    abox("%s_fseat_%d" % (P, k), (0.30, 0.25, 0.05), (x, y + 0.55, FLAK_Z + 0.75), "M_Dark", g, bevel=0.0)
    rod("%s_fseatp_%d" % (P, k), Vector((x, y + 0.20, FLAK_Z + 0.86)), Vector((x, y + 0.55, FLAK_Z + 0.75)), 0.02, "M_Dark", g, seg=6)
    abox("%s_fsh_%d" % (P, k), (1.10, 0.02, 0.62), (x, y - 0.42, pz + 0.12), "M_Grau", g, rot=(0.15, 0, 0), bevel=0.0)
    acyl("%s_fsight_%d" % (P, k), 0.06, 0.10, (x + 0.16, y - 0.12, pz + 0.24), "M_Lens", g, axis=1, seg=8, dirt=False)
    abox("%s_fc_%d" % (P, k), (0.30, 0.62, 0.22), (x, y, pz), "M_Grau", gp, bevel=0.02)
    rod("%s_fb_%d" % (P, k), Vector((x, y - 0.30, pz)), Vector((x, y - 1.35, pz)), 0.03, "M_Dark", gp, seg=8)
    acyl("%s_ff_%d" % (P, k), 0.05, 0.16, (x, y - 1.40, pz), "M_Dark", gp, axis=1, seg=8)
    abox("%s_fmag_%d" % (P, k), (0.08, 0.22, 0.18), (x + 0.19, y - 0.05, pz + 0.04), "M_Dark", gp, bevel=0.0)
for k, (x, y) in enumerate(FLAK):
    flak38(k, x, y)

# ---------------------------------------------------------------- walkable interior (r9, visual): ivory-painted
# compartments inside the upper hull - floor 3.4, ceiling 6.8 m. Bow to stern: driving & command station, turret
# basket with the 28 cm shell hoist, magazine, engine room with the two MAN V12Z32/44 diesels. Central passage with
# bulkhead doors; entry through the side door (left) from an external stair.
IX, IY0, IY1 = 6.6, -13.2, 15.4
abox(P + "_ifloor", (2 * IX, IY1 - IY0, 0.20), (0, (IY0 + IY1) / 2, FLOOR - 0.10), "M_Steel", GI, bevel=0.0)
abox(P + "_iceil", (2 * IX, IY1 - IY0, 0.08), (0, (IY0 + IY1) / 2, CEIL + 0.04), INT, GI, bevel=0.0)
for (x, y) in FLAK:                                                    # gun-tub drums come down through the ceiling
    cut(P + "_iceil", cutter_cyl(0.96, 1.0, (x, y, CEIL)))
H = CEIL - FLOOR
abox(P + "_iwallF", (2 * IX, 0.10, H), (0, IY0 - 0.05, FLOOR + H / 2), INT, GI, bevel=0.0)
abox(P + "_iwallB", (2 * IX, 0.10, H), (0, IY1 + 0.05, FLOOR + H / 2), INT, GI, bevel=0.0)
abox(P + "_iwallR", (0.10, IY1 - IY0, H), (-IX - 0.05, (IY0 + IY1) / 2, FLOOR + H / 2), INT, GI, bevel=0.0)
dy0, dy1, dz = DOOR[1] - 0.75, DOOR[1] + 0.75, FLOOR + 2.2         # left wall with the door opening
abox(P + "_iwallL1", (0.10, dy0 - IY0, H), (IX + 0.05, (IY0 + dy0) / 2, FLOOR + H / 2), INT, GI, bevel=0.0)
abox(P + "_iwallL2", (0.10, IY1 - dy1, H), (IX + 0.05, (dy1 + IY1) / 2, FLOOR + H / 2), INT, GI, bevel=0.0)
abox(P + "_iwallL3", (0.10, 1.5, CEIL - dz), (IX + 0.05, DOOR[1], (dz + CEIL) / 2), INT, GI, bevel=0.0)
for j, y in enumerate((dy0, dy1)):                                     # door tunnel through the 0.4 m wall gap
    abox("%s_djamb%d" % (P, j), (0.55, 0.06, 2.2), (IX + 0.22, y, FLOOR + 1.1), "M_Steel", GI, bevel=0.0)
abox(P + "_dlint", (0.55, 1.56, 0.06), (IX + 0.22, DOOR[1], dz + 0.03), "M_Steel", GI, bevel=0.0)
abox(P + "_dsill", (0.55, 1.56, 0.04), (IX + 0.22, DOOR[1], FLOOR + 0.02), "M_Steel", GI, bevel=0.0)
abox(P + "_dleaf", (0.12, 1.45, 2.15), (HW + 0.10, DOOR[1] + 1.55, FLOOR + 1.1), BODY, G, rot=(0, 0, 0.35), bevel=0.02)   # door leaf, open
for j in (-1, 1):
    acyl("%s_dhinge%d" % (P, j), 0.05, 0.25, (HW + 0.02, dy1 + 0.02, FLOOR + 1.1 + j * 0.8), "M_Steel", G, seg=8)


def bulkhead(tag, y, door_w=1.6):
    for sx in (1, -1):
        w = IX - door_w / 2
        abox("%s_bh%s%d" % (P, tag, sx), (w, 0.12, H), (sx * (door_w / 2 + w / 2), y, FLOOR + H / 2), INT, GI, bevel=0.0)
        abox("%s_bhf%s%d" % (P, tag, sx), (0.10, 0.20, 2.2), (sx * (door_w / 2 + 0.05), y, FLOOR + 1.1), "M_Steel", GI, bevel=0.0)
    abox("%s_bht%s" % (P, tag), (door_w, 0.12, CEIL - dz), (0, y, (dz + CEIL) / 2), INT, GI, bevel=0.0)
    abox("%s_bhs%s" % (P, tag), (door_w + 0.2, 0.20, 0.10), (0, y, dz + 0.05), "M_Steel", GI, bevel=0.0)
for tag, y in (("a", -8.4), ("b", 2.4), ("c", 6.6)):
    bulkhead(tag, y)

# lighting, cable trays, pipes along both walls, fire extinguishers
for k in range(12):
    y = IY0 + 1.2 + k * 2.4
    abox("%s_lamp%d" % (P, k), (0.40, 0.18, 0.08), (0, y, CEIL - 0.04), "M_IntLamp", GI, bevel=0.0, dirt=False)
    abox("%s_lampc%d" % (P, k), (0.46, 0.24, 0.03), (0, y, CEIL - 0.015), "M_Steel", GI, bevel=0.0)
for sx in (1, -1):
    for j, z in enumerate((6.35, 6.55)):
        rod("%s_pipe%d_%d" % (P, sx, j), Vector((sx * (IX - 0.12 - j * 0.12), IY0 + 0.2, z)), Vector((sx * (IX - 0.12 - j * 0.12), IY1 - 0.2, z)), 0.05, "M_Dark" if j else "M_Steel", GI, seg=8)
    abox("%s_tray%d" % (P, sx), (0.30, IY1 - IY0 - 0.4, 0.04), (sx * (IX - 0.2), (IY0 + IY1) / 2, 6.15), "M_Dark", GI, bevel=0.0)
    for j, y in enumerate((-11.0, 1.8, 7.2)):
        acyl("%s_fext%d_%d" % (P, sx, j), 0.09, 0.55, (sx * (IX - 0.12), y, FLOOR + 1.2), "M_ShellHE" if False else "M_Exhaust", GI, seg=10)

# --- driving & command station (bow): driver seat, levers, instrument board, periscopes to the bridge, radio, chart table
abox(P + "_dseat", (0.55, 0.50, 0.10), (0, -12.0, FLOOR + 0.55), "M_Dark", GI, bevel=0.0)
abox(P + "_dseatb", (0.55, 0.08, 0.60), (0, -11.72, FLOOR + 0.90), "M_Dark", GI, bevel=0.0)
rod(P + "_dseatp", Vector((0, -12.0, FLOOR)), Vector((0, -12.0, FLOOR + 0.52)), 0.05, "M_Steel", GI, seg=8)
abox(P + "_dboard", (2.4, 0.30, 0.90), (0, -12.95, FLOOR + 1.25), "M_Grau", GI, bevel=0.02)
for k in range(8):
    acyl("%s_dial%d" % (P, k), 0.07, 0.03, (-1.0 + k * 0.28, -13.11, FLOOR + 1.45 + (k % 2) * 0.22), "M_Lens", GI, axis=1, seg=12, dirt=False)
for sx in (1, -1):
    rod("%s_lever%d" % (P, sx), Vector((sx * 0.35, -12.45, FLOOR + 0.05)), Vector((sx * 0.35, -12.35, FLOOR + 1.05)), 0.025, "M_Steel", GI, seg=8)
    acyl("%s_leverk%d" % (P, sx), 0.05, 0.10, (sx * 0.35, -12.35, FLOOR + 1.10), "M_Dark", GI, seg=8)
    rod("%s_peri%d" % (P, sx), Vector((sx * 0.6, -12.9, FLOOR + 1.70)), Vector((sx * 0.6, -12.9, CEIL)), 0.07, "M_Dark", GI, seg=8)
for k in range(3):                                                     # ladder up to the bridge hatch
    pass
for j in (-0.22, 0.22):
    rod("%s_blad%d" % (P, int(j * 10)), Vector((1.5 + j, -12.2, FLOOR)), Vector((1.5 + j, -12.2, CEIL)), 0.025, "M_Steel", GI, seg=6)
for r in range(8):
    rod("%s_bladr%d" % (P, r), Vector((1.28, -12.2, FLOOR + 0.35 + r * 0.38)), Vector((1.72, -12.2, FLOOR + 0.35 + r * 0.38)), 0.018, "M_Steel", GI, seg=6)
for k in range(3):                                                     # radio rack (Fu 5 / Fu 2 sets) on the right wall
    abox("%s_radio%d" % (P, k), (0.45, 0.70, 0.42), (-IX + 0.25, -11.2 + k * 0.8, FLOOR + 1.3), "M_Grau", GI, bevel=0.02)
    for j in range(3):
        acyl("%s_rknob%d_%d" % (P, k, j), 0.035, 0.03, (-IX + 0.49, -11.45 + k * 0.8 + j * 0.25, FLOOR + 1.35), "M_Dark", GI, axis=0, seg=8)
abox(P + "_radtab", (0.70, 2.6, 0.06), (-IX + 0.35, -10.4, FLOOR + 0.95), "M_Wood", GI, bevel=0.0)
abox(P + "_chart", (1.4, 0.9, 0.06), (4.4, -10.0, FLOOR + 0.95), "M_Wood", GI, bevel=0.0)
for sx in (1, -1):
    for sy in (1, -1):
        rod("%s_chl%d%d" % (P, sx, sy), Vector((4.4 + sx * 0.6, -10.0 + sy * 0.35, FLOOR)), Vector((4.4 + sx * 0.6, -10.0 + sy * 0.35, FLOOR + 0.92)), 0.025, "M_Steel", GI, seg=6)
for k in range(2):                                                     # crew bunks along the left wall
    for j in range(2):
        abox("%s_bunk%d_%d" % (P, k, j), (0.75, 2.0, 0.12), (IX - 0.42, -11.3 + k * 2.1, FLOOR + 0.5 + j * 0.95), "M_Canvas", GI, bevel=0.0)

# --- turret basket under the main turret: ring wall, ring gear on the ceiling, central shell hoist, ready rounds
TR = 4.4
for k in range(28):
    a0 = 2 * math.pi * k / 28
    am = a0 + math.pi / 28
    if abs(math.sin(am)) > 0.985:                                      # openings fore and aft for the passage
        continue
    cx, cy = TR * math.cos(am), TC.y + TR * math.sin(am)
    abox("%s_bask%d" % (P, k), (TR * 2 * math.pi / 28 * 1.02, 0.06, 1.4), (cx, cy, FLOOR + 0.7), "M_Steel", GI, rot=(0, 0, am + math.pi / 2), bevel=0.0)
    abox("%s_ring%d" % (P, k), (5.0 * 2 * math.pi / 28 * 1.02, 0.30, 0.16), (5.0 * math.cos(am), TC.y + 5.0 * math.sin(am), CEIL - 0.08), "M_Dark", GI, rot=(0, 0, am + math.pi / 2), bevel=0.0)
lathe_y(P + "_hoist", [(0.0, 0.70), (0.25, 0.70), (0.25, 0.55), (CEIL - FLOOR, 0.55), (CEIL - FLOOR, 0.0), (0.0, 0.0)], (0, TC.y, FLOOR), ["M_Grau"], GI, seg=20, axis='Z')
abox(P + "_hoistdoor", (0.62, 0.04, 1.4), (0, TC.y - 0.56, FLOOR + 1.2), "M_Dark", GI, bevel=0.0)
for k in range(16):                                                    # 28 cm shells (330 kg, 1.26 m) standing round the basket
    a = 2 * math.pi * (k + 0.5) / 16
    if abs(math.sin(a)) > 0.92:
        continue
    r = 3.85
    lathe_y("%s_shell%d" % (P, k), [(0.0, 0.0), (0.0, 0.14), (0.80, 0.14), (1.05, 0.10), (1.24, 0.02), (1.26, 0.0)],
            (r * math.cos(a), TC.y + r * math.sin(a), FLOOR), ["M_ShellHE"], GI, seg=12, axis='Z')
    acyl("%s_sband%d" % (P, k), 0.145, 0.05, (r * math.cos(a), TC.y + r * math.sin(a), FLOOR + 0.18), "M_Brass", GI, seg=12)
    rod("%s_shold%d" % (P, k), Vector((r * math.cos(a) * 1.07, TC.y + r * math.sin(a) * 1.07, FLOOR + 0.9)),
        Vector((TR * math.cos(a) * 0.99, TC.y + TR * math.sin(a) * 0.99, FLOOR + 0.9)), 0.02, "M_Steel", GI, seg=6)
for sx in (1, -1):                                                     # turret-crew seats + gun breech housings seen through the ring
    abox("%s_bseat%d" % (P, sx), (0.5, 0.5, 0.08), (sx * 2.2, TC.y + 1.0, FLOOR + 0.5), "M_Dark", GI, bevel=0.0)
    rod("%s_bseatp%d" % (P, sx), Vector((sx * 2.2, TC.y + 1.0, FLOOR)), Vector((sx * 2.2, TC.y + 1.0, FLOOR + 0.48)), 0.04, "M_Steel", GI, seg=8)
    abox("%s_breech%d" % (P, sx), (1.0, 1.6, 0.6), (sx * GX, TC.y - 1.6, CEIL - 0.35), "M_Grau", GI, bevel=0.03)

# --- magazine: racks of 28 cm shells lying in three tiers, brass main charges, 12.8 cm rounds
for sx in (1, -1):
    for t in range(3):
        z = FLOOR + 0.35 + t * 0.62
        # r20: the left rack used to span y 1.4..5.0 - through bulkhead b AND across the side door (the only way in);
        # it now sits aft of the door (y 4.95..6.45) and the door tunnel + landing stay clear
        abox("%s_rack%d_%d" % (P, sx, t), (2.0, 3.6 if sx < 0 else 1.5, 0.05), (sx * (IX - 1.1), 4.5 if sx < 0 else 5.7, z - 0.17), "M_Steel", GI, bevel=0.0)
        n = 9 if sx < 0 else 5
        for k in range(n):
            y = (2.85 + k * 0.38) if sx < 0 else (5.1 + k * 0.30)
            lathe_y("%s_mshell%d_%d_%d" % (P, sx, t, k), [(0.0, 0.0), (0.0, 0.14), (0.80, 0.14), (1.05, 0.10), (1.26, 0.0)],
                    (sx * (IX - 2.05), y, z), ["M_ShellHE"], GI, seg=10, axis='X' if sx > 0 else 'X')
    for (y0, y1) in ((2.65, 6.35),) if sx < 0 else ((DOOR[1] + 0.95, 6.45),):
        for xo in (0.15, 2.0):
            for yy in (y0, y1):
                rod("%s_rpost%d_%d_%d" % (P, sx, int(xo * 10), int(yy * 10)), Vector((sx * (IX - xo), yy, FLOOR)), Vector((sx * (IX - xo), yy, FLOOR + 1.9)), 0.03, "M_Steel", GI, seg=6)
for k in range(10):                                                    # brass cartridge cases (Hauptkartusche) along the passage
    acyl("%s_cart%d" % (P, k), 0.16, 0.85, (-2.0, 2.85 + k * 0.36, FLOOR + 0.43), "M_Brass", GI, seg=12)
for k in range(8):                                                     # 12.8 cm two-piece rounds in a floor rack
    lathe_y("%s_r128_%d" % (P, k), [(0.0, 0.0), (0.0, 0.065), (0.60, 0.065), (0.72, 0.03), (0.75, 0.0)], (2.2, 5.2 + k * 0.16, FLOOR + 0.1), ["M_ShellHE"], GI, seg=10, axis='X')
abox(P + "_r128rack", (0.9, 1.4, 0.08), (2.55, 5.75, FLOOR + 0.04), "M_Wood", GI, bevel=0.0)

# --- engine room: two MAN V12Z32/44 marine diesels (each ~8,500 PS), generators, fuel tanks, catwalk gratings
for sx in (1, -1):
    ex = sx * 2.9
    abox("%s_ebase%d" % (P, sx), (1.9, 6.4, 1.0), (ex, 10.9, FLOOR + 0.5), "M_Grau", GI, bevel=0.04)
    for bank in (1, -1):
        abox("%s_ebank%d_%d" % (P, sx, bank), (0.70, 6.0, 0.95), (ex + bank * 0.42, 10.9, FLOOR + 1.35), "M_Grau", GI, rot=(0, bank * -0.42, 0), bevel=0.03)
        for c in range(6):
            abox("%s_ehead%d_%d_%d" % (P, sx, bank, c), (0.50, 0.78, 0.22), (ex + bank * 0.66, 8.2 + c * 0.95 + 0.55, FLOOR + 1.88), "M_Dark", GI, rot=(0, bank * -0.42, 0), bevel=0.02)
        rod("%s_eexh%d_%d" % (P, sx, bank), Vector((ex + bank * 0.95, 8.2, FLOOR + 1.6)), Vector((ex + bank * 0.95, 13.9, FLOOR + 1.6)), 0.14, "M_Exhaust", GI, seg=10)
    acyl("%s_egen%d" % (P, sx), 0.75, 1.6, (ex, 14.6 - 0.8, FLOOR + 0.85), "M_Grau", GI, axis=1, seg=20)
    acyl("%s_efly%d" % (P, sx), 0.85, 0.30, (ex, 7.55, FLOOR + 0.95), "M_Dark", GI, axis=1, seg=20)
    rod("%s_eup%d" % (P, sx), Vector((ex, 13.4, FLOOR + 1.9)), Vector((ex, 13.4, CEIL)), 0.22, "M_Exhaust", GI, seg=12)
    acyl("%s_ftank%d" % (P, sx), 0.55, 7.6, (sx * (IX - 0.65), 11.0, FLOOR + 0.75), "M_Grau", GI, axis=1, seg=16)
    acyl("%s_ftank2%d" % (P, sx), 0.55, 7.6, (sx * (IX - 0.65), 11.0, FLOOR + 2.0), "M_Grau", GI, axis=1, seg=16)
abox(P + "_egrate", (1.6, 8.6, 0.04), (0, 11.0, FLOOR + 0.02), "M_Mesh", GI, bevel=0.0)
abox(P + "_epanel", (2.0, 0.25, 1.6), (0, IY1 - 0.15, FLOOR + 1.3), "M_Grau", GI, bevel=0.02)
for k in range(10):
    acyl("%s_edial%d" % (P, k), 0.07, 0.03, (-0.8 + (k % 5) * 0.4, IY1 - 0.29, FLOOR + 1.45 + (k // 5) * 0.4), "M_Lens", GI, axis=1, seg=12, dirt=False)

# --- external stair to the side door: 8 treads x 0.40 m rise + landing at floor height, handrails
SX = HW + 0.75
for k in range(8):
    z = 0.40 * (k + 1)
    y = DOOR[1] - 1.05 - (8 - k) * 0.50
    abox("%s_tread%d" % (P, k), (1.20, 0.50, 0.08), (SX, y, z - 0.04), "M_Steel", G, bevel=0.0)
abox(P + "_landing", (1.40, 2.0, 0.10), (SX - 0.05, DOOR[1], FLOOR - 0.05), "M_Steel", G, bevel=0.0)
for j in (-0.62, 0.62):
    rod("%s_stringer%d" % (P, int(j * 10)), Vector((SX + j, DOOR[1] - 1.05 - 8 * 0.5 - 0.25, 0.0)), Vector((SX + j, DOOR[1] - 1.05 + 0.25, FLOOR - 0.05)), 0.05, "M_Steel", G, seg=6)
    rod("%s_hrail%d" % (P, int(j * 10)), Vector((SX + j, DOOR[1] - 1.05 - 8 * 0.5, 1.0)), Vector((SX + j, DOOR[1] - 1.05, FLOOR + 0.95)), 0.025, "M_Steel", G, seg=6)
for (x, y) in ((SX + 0.65, DOOR[1] - 0.95), (SX + 0.65, DOOR[1] + 0.95)):
    rod("%s_lpost%d" % (P, int(y * 10)), Vector((x, y, 0.0)), Vector((x, y, FLOOR + 1.0)), 0.05, "M_Steel", G, seg=6)
rod(P + "_lrail", Vector((SX + 0.65, DOOR[1] - 0.95, FLOOR + 1.0)), Vector((SX + 0.65, DOOR[1] + 0.95, FLOOR + 1.0)), 0.025, "M_Steel", G, seg=6)
# ---------------------------------------------------------------- main turret (Drh LC/34 type, 360 mm face)
tb = sym([(5.1, TC.y - 4.9, 7.0), (5.1, TC.y + 7.4, 7.0), (4.7, TC.y - 3.9, 10.9), (4.7, TC.y + 7.1, 10.9)])
chull(P + "_turret", tb, BODY, GT, bevel=0.10, uvscale=0.15)
acyl(P + "_barbette", 5.0, 0.30, (TC.x, TC.y, 7.12), BODY, GT, seg=40)
rfy = TC.y + 5.6                 # 10.5 m rangefinder across the turret rear
acyl(P + "_rf", 0.36, 10.5, (0.0, rfy, 11.45), BODY, GT, axis=0, seg=16)
for sx in (1, -1):
    acyl("%s_rfh%d" % (P, sx), 0.48, 0.55, (sx * 5.25, rfy, 11.45), BODY, GT, axis=0, seg=16)
    acyl("%s_rfl%d" % (P, sx), 0.20, 0.02, (sx * 5.53, rfy, 11.45), "M_Lens", GT, axis=0, seg=12, dirt=False)
    abox("%s_rfs%d" % (P, sx), (0.5, 0.5, 0.55), (sx * 1.8, rfy, 11.15), BODY, GT, bevel=0.03)
    # MG 151/15 twin mounts on the front roof corners
    acyl("%s_mgb%d" % (P, sx), 0.40, 0.25, (sx * 3.7, TC.y - 2.6, 11.02), "M_Grau", GT, seg=14)
    rod("%s_mg151_%d" % (P, sx), Vector((sx * 3.7, TC.y - 2.6, 11.4)), Vector((sx * 3.7, TC.y - 4.4, 11.55)), 0.04, "M_Dark", GT, seg=8)
    abox("%s_mgr%d" % (P, sx), (0.25, 0.70, 0.30), (sx * 3.7, TC.y - 2.5, 11.35), "M_Dark", GT, bevel=0.02)
for k, (x, y) in enumerate(((-2.8, TC.y + 1.0), (2.8, TC.y + 1.0), (0.0, TC.y + 3.4))):
    hatch(P, "tt%d" % k, (x, y, 10.92), 0.40, GT, mat=BODY)
for k, x in enumerate((-3.5, 3.5)):
    lathe_y("%s_cmdh%d" % (P, k), [(0.0, 0.50), (0.45, 0.45), (0.55, 0.0)], (x, TC.y - 0.6, 10.9), [BODY], GT, seg=16, axis='Z')
lift_hooks(P, "t", [(sx * 4.2, y, 10.9) for sx in (1, -1) for y in (TC.y - 3.0, TC.y + 6.0)], GT)
antenna(P, "ant", Vector((-3.6, TC.y + 6.6, 10.9)), 3.0, GT)

# ---------------------------------------------------------------- guns (one elevating cradle for both)
for sx in (1, -1):
    x = sx * GX
    lathe_y("%s_bag%d" % (P, sx), [(0.0, 0.0), (0.0, 0.95), (0.35, 0.80), (0.55, 0.55), (0.60, 0.0)], (x, TC.y - 4.55, TRN.z), ["M_Canvas"], GG, seg=18)
    gun_simple(P + "g%d" % sx, GB, TC.y - 5.0, MUZ_Y, TRN.z, 0.23, 0.48, x=x, collar=True,
               steps=[(0.18, 0.42), (0.45, 0.34), (0.75, 0.28)], mat=BODY)
    acyl("%s_mzr%d" % (P, sx), 0.26, 0.30, (x, MUZ_Y + 0.15, TRN.z), BODY, GB, axis=1, seg=18)

# ---------------------------------------------------------------- rear turret, 12.8 cm Pak 44 (r9: own director)
# Faces aft at rest. Ring on a 0.55 m barbette; the gun (6.6 m from trunnion to muzzle) clears the deck fittings and
# the tub-mounted Flak; the bow sector (+-50 deg) is blocked by the main turret.
TRN2 = Vector((0, TC2.y + 1.55, 8.45))
acyl(P + "_sbarb", 2.25, 0.55, (TC2.x, TC2.y, 7.0 + 0.275), BODY, G, seg=36)
st = [(7.55, superellipse(1.75, 1.90, 2.05, n=28, e=2.6, x0=TC2.x, y0=TC2.y)),
      (8.75, superellipse(1.55, 1.75, 1.95, n=28, e=2.6, x0=TC2.x, y0=TC2.y)),
      (8.92, superellipse(1.25, 1.45, 1.75, n=28, e=2.6, x0=TC2.x, y0=TC2.y))]
st = [(z, [(x, 2 * TC2.y - y) for (x, y) in pts][::-1]) for (z, pts) in st]      # mirror: nose to the stern
turret_loft(P + "_sturret", st, BODY, GS, bevel=0.0, smooth=True)
hatch(P, "st", (0.55, TC2.y - 0.6, 8.94), 0.32, GS, mat=BODY)
periscope(P, "st", Vector((-0.55, TC2.y + 0.3, 8.94)), GS)
for sx in (1, -1):
    acyl("%s_spp%d" % (P, sx), 0.05, 0.05, (sx * 1.66, TC2.y - 0.4, 8.1), BODY, GS, axis=0, seg=10)
lathe_y(P + "_smant", [(0.0, 0.0), (0.0, 0.42), (0.30, 0.34), (0.45, 0.0)], (0.0, TRN2.y + 0.45, TRN2.z), [BODY], GS2, seg=18)
gun_simple(P + "s", GS2, TRN2.y + 0.85, TRN2.y + 6.6, TRN2.z, 0.085, 0.15, collar=False, steps=[(0.25, 0.12)], mat=BODY)
brake_de(P + "s", GS2, TRN2.y + 6.6 + 0.0, TRN2.z, 0.13, 0.42, mat=BODY) if False else None
acyl(P + "_srec", 0.08, 1.2, (0.0, TRN2.y + 1.2, TRN2.z + 0.24), BODY, GS2, axis=1, seg=10)
auto_attach([G, GT, GG, GB, GS, GS2, GI] + ["%s_fl%d" % (P, k) for k in range(8)] + ["%s_flp%d" % (P, k) for k in range(8)], bracket_groups=(GB,))
for sx in (1, -1):
    ww2_decal("%s_cross%d" % (P, sx), (sx * (HW + 0.01), -4.0, 5.0), (sx, 0, 0), (0, 0, 1), 2.8, "cross", G)
    ww2_decal("%s_crosst%d" % (P, sx), (sx * 4.95, TC.y + 1.5, 9.0), (sx, 0, 0.1), (0, 0, 1), 2.0, "cross_late", GT)
ww2_decal(P + "_name", (0.0, HL + 0.01, 3.2), (0, 1, 0), (0, 0, 1), 3.0, "de_ratte", G)
weather(gen_weather([(4.75, 17.2, 5.2), (-4.75, 17.2, 5.2)], 1.8))

def extra(rt, ty, gp, br):
    sy = pivot("sec_yaw", tuple(TC2), rt)
    group_under(GS, "sec_yaw_geo", sy)
    sp = pivot("sec_pitch", tuple(TRN2), sy)
    group_under(GS2, "sec_pitch_geo", sp)
    group_under(GI, "interior_geo", rt)
    for k, (x, y) in enumerate(FLAK):
        fy = pivot("flak_%d" % k, (x, y, FLAK_Z + 0.95), rt)
        group_under("%s_fl%d" % (P, k), "flak_%d_geo" % k, fy)
        fp = pivot("flak_%d_p" % k, (x, y, FLAK_Z + 0.95), fy)
        group_under("%s_flp%d" % (P, k), "flak_%d_p_geo" % k, fp)
rig_tank(P, G, GT, GG, GB, WHEELS, LINKS, TC, TRN, "/home/user/.cache/rwveh/raw/rw_ratte.glb", "/home/user/models/rw_ratte.blend", "RW_RATTE", extra=extra)
