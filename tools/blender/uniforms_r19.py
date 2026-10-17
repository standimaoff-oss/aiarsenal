# r19: six more uniform sets, exec()'d by tools/blender/uniforms.py after its helpers (box / boxes / make / mirror_x / rot_x,
# BODY / ARM_R / LEG_R / FY, palette indices). Same frame and node contract as r15: "<set>.<piece>[.<detail>]".
#   su_afghan  Soviet Army, Afghanistan 1980s: M88 'afghanka' khaki, panama hat, 'СА' field boards, kirza boots
#   su_tanker  Soviet tank crew: black-blue overall, padded tank helmet (shlemofon) with ear cups, kirza boots
#   us_vietnam US Army, Vietnam: OG-107 fatigues, M1 helmet with 'Mitchell' cover, jungle boots
#   us_desert  US Army, Desert Storm: DCU three-colour desert, PASGT helmet + goggles, desert boots
#   ru_vdv     Russian Airborne (VDV): 'Gorka' suit, telnyashka, sky-blue beret, VDV sleeve patch
#   ru_pilot   Russian Aerospace Forces pilot: flight coverall + harness, G-suit, ZSh-7 helmet with KM-34 mask
SKY, HELM_W, VISOR, TANKB, KHAKI_P, GSUIT, STEEL, TAN_DCU = 24, 25, 26, 27, 28, 29, 30, 31
SETS.update({
    "su_afghan": dict(army="su", field=True, style="afghan", rank="sa"),
    "su_tanker": dict(army="su", field=True, style="tanker", rank="none"),
    "us_vietnam": dict(army="us", field=True, style="vietnam", rank="none"),
    "us_desert": dict(army="us", field=True, style="desert", rank="none"),
    "ru_vdv": dict(army="ru", field=True, style="vdv", rank="none"),
    "ru_pilot": dict(army="ru", field=True, style="pilot", rank="none"),
})
FAB19 = {"afghan": "afghanka.png", "tanker": "tank_overall.png", "vietnam": "og107.png", "desert": "dcu.png", "vdv": "gorka.png",
         "pilot": "flight_ru.png"}


def rot_y(deg, pivot):
    px, py, pz = pivot
    return Matrix.Translation((px, py, pz)) @ Matrix.Rotation(math.radians(deg), 4, "Y") @ Matrix.Translation((-px, -py, -pz))


def pocket(P, b, fab, flap=True, button=None):
    """chest / lower pocket on the jacket front plane: b = (x0, x1, z0, z1)"""
    x0, x1, z0, z1 = b
    box(P, (x0, x1, FY - 0.012, FY, z0, z1), tex=fab)
    if flap:
        box(P + "f", (x0 - 0.005, x1 + 0.005, FY - 0.018, FY, z1 - 0.045, z1 + 0.01), tex=fab)
    if button is not None:
        box(P + "b", ((x0 + x1) / 2 - 0.011, (x0 + x1) / 2 + 0.011, FY - 0.022, FY - 0.017, z1 - 0.035, z1 - 0.013), pal=button)


def body19(s):
    st = SETS[s]["style"]
    fab = FAB19[st]
    P = s + ".body"
    box(P, BODY, tex=fab)
    if st in ("afghan", "vietnam", "desert"):          # turn-down shirt collar
        boxes(P + ".collar", [(-0.20, 0.20, 0.10, 0.16, 1.51, 1.56), (0.10, 0.21, -0.165, 0.10, 1.50, 1.545),
                              (-0.21, -0.10, -0.165, 0.10, 1.50, 1.545)], tex=fab)
    if st == "afghan":
        for sx in (1, -1):
            b = (0.05, 0.22, 1.18, 1.36)
            pocket(P + ".cp%s" % ("l" if sx > 0 else "r"), b if sx > 0 else (-b[1], -b[0], b[2], b[3]), fab, button=BROWN_D)
            b = (0.06, 0.24, 0.92, 1.08)
            pocket(P + ".lp%s" % ("l" if sx > 0 else "r"), b if sx > 0 else (-b[1], -b[0], b[2], b[3]), fab, button=BROWN_D)
        boxes(P + ".buttons", [(-0.012, 0.012, FY - 0.010, FY, z - 0.012, z + 0.012) for z in (1.42, 1.28, 1.14, 1.00)], pal=BROWN_D)
        box(P + ".belt", (BODY[0] - 0.006, BODY[1] + 0.006, BODY[2] - 0.006, BODY[3] + 0.006, 0.84, 0.89), tex="leather_brown.png", scale=2.0)
        box(P + ".buckle", (-0.04, 0.04, FY - 0.016, FY - 0.004, 0.835, 0.895), pal=GOLD)
        box(P + ".bstar", (-0.014, 0.014, FY - 0.019, FY - 0.015, 0.852, 0.878), pal=RED)
    elif st == "tanker":
        box(P + ".zip", (-0.010, 0.010, FY - 0.006, FY, 0.86, 1.52), pal=DARK)
        pocket(P + ".cp", (0.06, 0.22, 1.20, 1.38), fab, flap=True)
        box(P + ".belt", (BODY[0] - 0.006, BODY[1] + 0.006, BODY[2] - 0.006, BODY[3] + 0.006, 0.84, 0.89), pal=BLACK)
        box(P + ".buckle", (-0.035, 0.035, FY - 0.014, FY - 0.004, 0.835, 0.895), pal=STEEL)
        boxes(P + ".collar", [(-0.20, 0.20, 0.10, 0.165, 1.51, 1.57), (0.02, 0.21, -0.165, 0.10, 1.50, 1.56),
                              (-0.21, -0.02, -0.165, 0.10, 1.50, 1.56)], tex=fab)
    elif st == "vietnam":
        for sx in (1, -1):
            b = (0.04, 0.21, 1.16, 1.36)
            pocket(P + ".cp%s" % ("l" if sx > 0 else "r"), b if sx > 0 else (-b[1], -b[0], b[2], b[3]), fab, button=BLACK)
            b = (0.05, 0.24, 0.88, 1.06)
            pocket(P + ".lp%s" % ("l" if sx > 0 else "r"), b if sx > 0 else (-b[1], -b[0], b[2], b[3]), fab, button=BLACK)
        box(P + ".tape_army", (0.05, 0.22, FY - 0.008, FY, 1.375, 1.415), tex="tape_us_army.png", uv="fit", show="-Y")
        box(P + ".tape_name", (-0.22, -0.05, FY - 0.008, FY, 1.375, 1.415), tex="tape_us_name.png", uv="fit", show="-Y")
        boxes(P + ".buttons", [(-0.010, 0.010, FY - 0.008, FY, z - 0.010, z + 0.010) for z in (1.40, 1.24, 1.08, 0.92)], pal=BLACK)
    elif st == "desert":
        for sx in (1, -1):
            b = (0.05, 0.22, 1.16, 1.34)
            pocket(P + ".cp%s" % ("l" if sx > 0 else "r"), b if sx > 0 else (-b[1], -b[0], b[2], b[3]), fab, button=TAN_D)
            b = (0.05, 0.24, 0.86, 1.04)
            pocket(P + ".lp%s" % ("l" if sx > 0 else "r"), b if sx > 0 else (-b[1], -b[0], b[2], b[3]), fab, button=TAN_D)
        box(P + ".tape_army", (0.05, 0.22, FY - 0.008, FY, 1.365, 1.405), tex="tape_us_army.png", uv="fit", show="-Y")
        box(P + ".tape_name", (-0.22, -0.05, FY - 0.008, FY, 1.365, 1.405), tex="tape_us_name.png", uv="fit", show="-Y")
    elif st == "vdv":
        # telnyashka V at the neck, Gorka reinforcement yoke over the shoulders, rolled hood at the back, pockets
        make(P + ".telnyashka", lambda bm: bm_prism(bm, [(-0.12, 1.52), (0.0, 1.33), (0.12, 1.52)], FY - 0.004, FY), tex="telnyashka.png", uv="fit", show="-Y")
        for sx in (-1, 1):
            pts = [(0.12, 1.52), (0.0, 1.33), (0.04, 1.32), (0.20, 1.50)]
            if sx < 0:
                pts = [(-x, z) for x, z in pts][::-1]
            make(P + ".lapel%s" % ("l" if sx > 0 else "r"), lambda bm, pts=pts: bm_prism(bm, pts, FY - 0.010, FY - 0.002), tex="gorka_patch.png")
        boxes(P + ".yoke", [(BODY[0] - 0.004, BODY[1] + 0.004, BODY[2] - 0.004, BODY[3] + 0.004, 1.40, 1.52)], tex="gorka_patch.png")
        box(P + ".hood", (-0.20, 0.20, BODY[3], BODY[3] + 0.07, 1.42, 1.58), tex=fab, bevel=(0.02, lambda e: True, 1))
        for sx in (1, -1):
            b = (0.06, 0.23, 1.10, 1.32)
            pocket(P + ".cp%s" % ("l" if sx > 0 else "r"), b if sx > 0 else (-b[1], -b[0], b[2], b[3]), "gorka_patch.png")
        box(P + ".belt", (BODY[0] - 0.006, BODY[1] + 0.006, BODY[2] - 0.006, BODY[3] + 0.006, 0.84, 0.885), pal=OLIVE_D)
    elif st == "pilot":
        box(P + ".zip", (-0.008, 0.008, FY - 0.005, FY, 0.75, 1.52), pal=DARK)
        for sx in (1, -1):                               # diagonal zipped chest pockets
            b = (0.06, 0.21, FY - 0.006, FY, 1.30, 1.315)
            box(P + ".pzip%s" % ("l" if sx > 0 else "r"), b if sx > 0 else mirror_x(b), pal=DARK, xform=rot_y(-18 * sx, (0.135 * sx, 0, 1.31)))
        # parachute / seat harness: two shoulder straps, chest strap with buckle, waist
        boxes(P + ".harness", [(0.09, 0.15, FY - 0.016, FY, 0.80, 1.52), (-0.15, -0.09, FY - 0.016, FY, 0.80, 1.52),
                               (-0.20, 0.20, FY - 0.020, FY - 0.004, 1.18, 1.22),
                               (0.09, 0.15, BODY[3], BODY[3] + 0.016, 0.80, 1.52), (-0.15, -0.09, BODY[3], BODY[3] + 0.016, 0.80, 1.52)], pal=BLACK)
        box(P + ".hbuckle", (-0.04, 0.04, FY - 0.026, FY - 0.016, 1.16, 1.24), pal=STEEL)
        boxes(P + ".collar", [(-0.20, 0.20, 0.10, 0.165, 1.51, 1.56), (0.10, 0.21, -0.165, 0.10, 1.50, 1.55),
                              (-0.21, -0.10, -0.165, 0.10, 1.50, 1.55)], tex=fab)
        box(P + ".patch", (-0.24, -0.12, FY - 0.008, FY, 1.36, 1.44), tex="patch_ru_flag.png", uv="fit", show="-Y")


def arm19(s, side):
    st = SETS[s]["style"]
    fab = FAB19[st]
    sx = -1 if side == "r" else 1
    P = "%s.arm_%s" % (s, side)
    A = ARM_R if sx < 0 else mirror_x(ARM_R)
    box(P, A, tex=fab)
    ox = A[0] if sx < 0 else A[1]
    outer = "-X" if sx < 0 else "+X"

    def ob(y0, y1, z0, z1, t0, t1):
        return (ox - t1, ox - t0, y0, y1, z0, z1) if sx < 0 else (ox + t0, ox + t1, y0, y1, z0, z1)
    box(P + ".cuff", (A[0] - 0.004, A[1] + 0.004, A[2] - 0.004, A[3] + 0.004, 0.84, 0.88), tex=fab)
    if st == "afghan":
        b = (-0.495, -0.262, -0.058, 0.058, A[5], A[5] + 0.016)
        box(P + ".board", b if sx < 0 else mirror_x(b), tex="pogon_sa.png", uv="fit", show="+Z", flip=sx > 0)
    elif st == "desert":
        box(P + ".pocket", ob(-0.09, 0.09, 1.08, 1.30, 0, 0.012), tex=fab)
        if sx < 0:
            box(P + ".flag", ob(-0.065, 0.065, 1.33, 1.42, 0, 0.006), tex="patch_us_flag.png", uv="fit", show=outer)
    elif st == "vietnam":
        if sx > 0:
            box(P + ".unit", ob(-0.05, 0.05, 1.30, 1.42, 0, 0.006), pal=OLIVE_D)
    elif st == "vdv":
        box(P + ".elbow", ob(-0.10, 0.10, 0.98, 1.14, 0, 0.010), tex="gorka_patch.png")
        if sx > 0:
            box(P + ".patch", ob(-0.06, 0.06, 1.26, 1.40, 0, 0.006), tex="patch_vdv.png", uv="fit", show=outer)
    elif st == "pilot":
        if sx > 0:
            box(P + ".pocket", ob(-0.07, 0.07, 1.06, 1.30, 0, 0.012), tex=fab)
            boxes(P + ".pens", [ob(-0.06 + 0.03 * k, -0.045 + 0.03 * k, 1.27, 1.34, 0.012, 0.022) for k in range(4)], pal=DARK)
        else:
            box(P + ".zip", ob(-0.07, 0.07, 1.20, 1.212, 0, 0.006), pal=DARK)


def leg19(s, side):
    st = SETS[s]["style"]
    fab = FAB19[st]
    sx = -1 if side == "r" else 1
    P = "%s.leg_%s" % (s, side)
    L = LEG_R if sx < 0 else mirror_x(LEG_R)
    box(P, L, tex=fab)
    ox = L[0] if sx < 0 else L[1]
    cx = -0.119 * (1 if sx < 0 else -1)

    def ob(y0, y1, z0, z1, t0, t1):
        return (ox - t1, ox - t0, y0, y1, z0, z1) if sx < 0 else (ox + t0, ox + t1, y0, y1, z0, z1)
    if st in ("afghan", "vietnam", "desert", "vdv"):
        cf = "gorka_patch.png" if st == "vdv" else fab
        box(P + ".cargo", ob(-0.075, 0.075, 0.40, 0.57, 0, 0.014), tex=cf)
        box(P + ".cflap", ob(-0.08, 0.08, 0.55, 0.59, 0, 0.019), tex=cf)
    if st == "vdv":
        box(P + ".knee", (cx - 0.09, cx + 0.09, L[2] - 0.014, L[2], 0.30, 0.50), tex="gorka_patch.png")
        box(P + ".seat", (L[0], L[1], L[3], L[3] + 0.012, 0.56, 0.76), tex="gorka_patch.png")
    elif st == "tanker":
        box(P + ".knee", (cx - 0.085, cx + 0.085, L[2] - 0.022, L[2], 0.32, 0.48), pal=BLACK)
        box(P + ".pocket", ob(-0.07, 0.07, 0.50, 0.66, 0, 0.012), tex=fab)
    elif st == "desert":
        box(P + ".knee", (cx - 0.085, cx + 0.085, L[2] - 0.012, L[2], 0.30, 0.47), tex=fab)
    elif st == "pilot":
        # PPK-1U anti-g suit: thigh and calf bladders, lacing, abdominal part on the body line
        g = (L[0] - 0.018, L[1] + 0.018, L[2] - 0.018, L[3] + 0.018)
        box(P + ".gthigh", (g[0], g[1], g[2], g[3], 0.42, 0.74), pal=GSUIT)
        box(P + ".gcalf", (g[0], g[1], g[2], g[3], 0.10, 0.36), pal=GSUIT)
        boxes(P + ".lacing", [ob(-0.012, 0.012, 0.42, 0.74, 0.018, 0.026), ob(-0.012, 0.012, 0.10, 0.36, 0.018, 0.026)], pal=BLACK)
        box(P + ".gknee", (L[0] - 0.006, L[1] + 0.006, L[2] - 0.006, L[3] + 0.006, 0.36, 0.42), tex=fab)
        box(P + ".hose", ob(0.04, 0.07, 0.62, 0.76, 0.018, 0.05), pal=BLACK)


def boot19(s, side):
    st = SETS[s]["style"]
    sx = -1 if side == "r" else 1
    P = "%s.boot_%s" % (s, side)
    m = lambda b: b if sx < 0 else mirror_x(b)
    if st in ("afghan", "tanker"):                       # kirza: tall tarpaulin-leather boots
        lt = "leather_black.png"
        box(P, m((-0.29, -0.002, -0.172, 0.172, 0.0, 0.44)), tex=lt, scale=2.0)
        box(P + ".toe", m((-0.272, -0.02, -0.215, -0.17, 0.0, 0.11)), tex=lt, scale=2.0)
        box(P + ".sole", m((-0.296, 0.004, -0.222, 0.178, 0.0, 0.035)), pal=RUBBER)
        box(P + ".top", m((-0.298, 0.006, -0.18, 0.18, 0.41, 0.45)), tex=lt, scale=2.0)
        box(P + ".heel", m((-0.27, -0.02, 0.13, 0.19, 0.0, 0.06)), pal=RUBBER)
    elif st == "vietnam":                                # jungle boots: black leather foot, OD canvas upper
        box(P, m((-0.29, -0.002, -0.172, 0.172, 0.0, 0.15)), tex="leather_black.png", scale=2.0)
        box(P + ".upper", m((-0.288, -0.004, -0.170, 0.170, 0.14, 0.32)), tex="og107.png")
        box(P + ".toe", m((-0.272, -0.02, -0.215, -0.17, 0.0, 0.11)), tex="leather_black.png", scale=2.0)
        box(P + ".sole", m((-0.296, 0.004, -0.222, 0.178, 0.0, 0.035)), pal=RUBBER)
        box(P + ".laces", m((-0.19, -0.10, -0.176, -0.172, 0.11, 0.31)), pal=BLACK)
        box(P + ".collar", m((-0.292, 0.002, -0.174, 0.174, 0.30, 0.325)), tex="leather_black.png", scale=2.0)
    else:
        lt = "coyote.png" if st == "desert" else "leather_black.png"
        box(P, m((-0.29, -0.002, -0.172, 0.172, 0.0, 0.30)), tex=lt, scale=2.0)
        box(P + ".toe", m((-0.272, -0.02, -0.215, -0.17, 0.0, 0.11)), tex=lt, scale=2.0)
        box(P + ".sole", m((-0.296, 0.004, -0.222, 0.178, 0.0, 0.035)), pal=BROWN_X if st == "desert" else RUBBER)
        box(P + ".collar", m((-0.295, 0.003, -0.177, 0.177, 0.275, 0.31)), tex=lt, scale=2.0)
        box(P + ".laces", m((-0.19, -0.10, -0.176, -0.172, 0.11, 0.29)), pal=TAN_D if st == "desert" else BLACK)


def head19(s):
    st = SETS[s]["style"]
    fab = FAB19[st]
    P = s + ".head"
    if st == "afghan":                                   # panama hat with a red star
        box(P, (-0.272, 0.272, -0.282, 0.282, 1.86, 2.06), tex=fab, scale=1.6, bevel=(0.05, top_edges(2.0), 2))
        box(P + ".brim", (-0.40, 0.40, -0.41, 0.41, 1.845, 1.868), tex=fab, scale=1.6)
        box(P + ".band", (-0.278, 0.278, -0.288, 0.288, 1.868, 1.90), pal=KHAKI_P)
        box(P + ".star", (-0.025, 0.025, -0.296, -0.288, 1.905, 1.955), pal=RED)
        boxes(P + ".vents", [(0.274, 0.278, -0.02, 0.02, 1.96, 1.99), (-0.278, -0.274, -0.02, 0.02, 1.96, 1.99)], pal=STEEL)
    elif st == "tanker":                                 # padded tank helmet: open face, three top ribs, ear cups, strap
        T = "leather_black.png"
        boxes(P, [(-0.30, 0.30, -0.31, 0.31, 1.95, 2.07), (-0.25, 0.25, 0.10, 0.31, 1.66, 1.95),
                  (0.25, 0.30, -0.31, 0.31, 1.66, 1.95), (-0.30, -0.25, -0.31, 0.31, 1.66, 1.95),
                  (-0.25, 0.25, -0.31, -0.25, 1.82, 1.95)], tex=T, scale=2.0)
        boxes(P + ".ribs", [(x - 0.035, x + 0.035, -0.30, 0.30, 2.07, 2.11) for x in (-0.15, 0.0, 0.15)], pal=BLACK)
        boxes(P + ".ears", [(0.30, 0.34, -0.08, 0.08, 1.70, 1.84), (-0.34, -0.30, -0.08, 0.08, 1.70, 1.84)], pal=BLACK)
        boxes(P + ".strap", [(0.25, 0.27, -0.03, 0.03, 1.52, 1.66), (-0.27, -0.25, -0.03, 0.03, 1.52, 1.66)], pal=BLACK)
        box(P + ".cable", (0.30, 0.32, 0.06, 0.09, 1.50, 1.70), pal=DARK)
    elif st == "vietnam":                                # M1 steel pot with Mitchell cover and elastic band
        box(P, (-0.33, 0.33, -0.34, 0.34, 1.79, 2.11), tex="mitchell.png", scale=1.6, bevel=(0.12, top_edges(2.0), 3))
        box(P + ".rim", (-0.345, 0.345, -0.355, 0.355, 1.775, 1.80), tex="mitchell.png", scale=1.6)
        box(P + ".band", (-0.336, 0.336, -0.346, 0.346, 1.86, 1.895), pal=OLIVE_D)
        boxes(P + ".chin", [(0.28, 0.295, -0.02, 0.02, 1.58, 1.78), (-0.295, -0.28, -0.02, 0.02, 1.58, 1.78)], pal=OLIVE_D)
        box(P + ".dope", (0.10, 0.17, -0.349, -0.345, 1.87, 1.885), pal=WHITE)      # bottle of insect repellent under the band
    elif st == "desert":                                 # PASGT 'Fritz' with DCU cover and sun/dust goggles
        box(P, (-0.31, 0.31, -0.32, 0.32, 1.79, 2.09), tex="dcu.png", scale=1.6, bevel=(0.10, top_edges(2.0), 3))
        box(P + ".rim", (-0.318, 0.318, -0.328, 0.328, 1.775, 1.81), tex="dcu.png", scale=1.6)
        box(P + ".skirt", (-0.312, 0.312, -0.09, 0.322, 1.70, 1.80), tex="dcu.png", scale=1.6)
        box(P + ".gstrap", (-0.322, 0.322, -0.332, 0.332, 1.90, 1.93), pal=BLACK)
        boxes(P + ".lens", [(0.02, 0.15, -0.345, -0.330, 1.885, 1.95), (-0.15, -0.02, -0.345, -0.330, 1.885, 1.95)], pal=VISOR)
        box(P + ".gframe", (-0.165, 0.165, -0.338, -0.328, 1.875, 1.96), pal=BLACK)
        box(P + ".cateye", (-0.12, 0.12, 0.322, 0.329, 1.84, 1.86), pal=TAN_L)
    elif st == "vdv":                                    # sky-blue beret pulled to the right, cockade on the left front
        box(P, (-0.272, 0.272, -0.282, 0.282, 1.84, 1.88), pal=BLACK)
        box(P + ".crown", (-0.285, 0.30, -0.27, 0.31, 1.875, 2.055), pal=SKY, bevel=(0.05, top_edges(2.0), 2), xform=rot_y(-8, (0.0, 0.0, 1.88)))
        box(P + ".cockade", (0.09, 0.15, -0.294, -0.268, 1.885, 1.945), pal=GOLD)
        box(P + ".flag", (0.165, 0.205, -0.290, -0.266, 1.89, 1.93), pal=RED_L)
    elif st == "pilot":                                  # ZSh-7 flight helmet, raised smoke visor, KM-34 oxygen mask + hose
        boxes(P, [(-0.32, 0.32, -0.33, 0.34, 1.97, 2.12), (-0.26, 0.26, 0.06, 0.34, 1.62, 1.97),
                  (0.26, 0.32, -0.33, 0.34, 1.62, 1.97), (-0.32, -0.26, -0.33, 0.34, 1.62, 1.97)], pal=HELM_W)
        box(P + ".dome", (-0.30, 0.30, -0.31, 0.32, 2.12, 2.16), pal=HELM_W, bevel=(0.03, top_edges(2.13), 1))
        box(P + ".vhouse", (-0.29, 0.29, -0.355, -0.33, 1.98, 2.08), pal=HELM_W)
        box(P + ".visor", (-0.27, 0.27, -0.352, -0.335, 1.90, 1.98), pal=VISOR)
        box(P + ".mask", (-0.10, 0.10, -0.345, -0.25, 1.54, 1.72), pal=OLIVE_D, bevel=(0.03, lambda e: True, 1))
        boxes(P + ".mstrap", [(0.10, 0.27, -0.30, -0.28, 1.64, 1.67), (-0.27, -0.10, -0.30, -0.28, 1.64, 1.67)], pal=BLACK)
        box(P + ".hose", (-0.03, 0.03, -0.33, -0.27, 1.40, 1.55), pal=BLACK)
        box(P + ".stripe", (-0.035, 0.035, -0.345, 0.345, 2.155, 2.165), pal=RED)
