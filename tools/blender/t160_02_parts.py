# Tu-160 STEP 2: landing gear (3-axle main bogies retracting forward-up into the pods, twin nose wheels), doors and wells,
# slats / flaps / flaperons on the swing wings, all-moving stabiliser + upper fin, two weapons bays with doors and racks,
# crew entry ladder in the nose-gear well, antennas, sensors and lights. Pivots dumped for step 4.
FLOOR_Z = 3.50          # crew cabin floor
PIV = {}


def bay_prof(y):
    zc, hw, ht, hb, n = fus_prof(y)[:5]
    return (zc, hw * 0.993, ht * 0.993, hb * 0.993, n)


def pod_prof(y, xc):
    t0 = clamp01((y - POD_Y0) / 1.6)
    t1 = clamp01((POD_Y1 - y) / 2.6)
    f = math.sqrt(max(0.0, 1 - (1 - min(t0, t1)) ** 2))
    return (POD_Z + 0.25 * (1 - f), 0.70 * max(f, 0.02), 0.75, 0.72 * max(f, 0.02), 3.0, xc)


# ---------------------------------------------------------------- main gear: oleo leg + 3-axle bogie (6 wheels 1260x425)
MG_RETRACT = 60.7
for side, sn in ((1, "L"), (-1, "R")):
    x = POD_X * side
    P = Vector((x, -1.5, 3.45))
    B = Vector((x, 0.0, 0.88))
    g, bg = "Gear_" + sn, "Bogie_" + sn
    d = (B - P).normalized()
    rod("MGStrut_" + sn, P, B + Vector((0, 0, 0.0)) - d * 1.0, 0.16, "M_Gear", g, seg=16)
    rod("MGPiston_" + sn, B - d * 1.1, B, 0.11, "M_Chrome", g, seg=14)
    rod("MGSideBrace_" + sn, P + Vector((-0.55 * side, 0.25, 0.05)), P + d * 1.2, 0.06, "M_Gear", g, seg=8)
    rod("MGRetractJack_" + sn, P + Vector((0.0, -0.45, 0.25)), P + d * 0.7 + Vector((0, 0, 0.0)), 0.05, "M_Chrome", g, seg=8)
    for k, dz in enumerate((0.35, 0.65)):
        abox("MGTorque_%s_%d" % (sn, k), (0.06, 0.28, 0.05), B - d * (dz + 0.1) + Vector((0, -0.16, 0)), "M_Gear", g,
             rot=(0.6 if k == 0 else -0.6, 0, 0))
    rod("MGBrakeLine_" + sn, P + Vector((0.13, 0.0, -0.2)), B + Vector((0.13, -0.05, 0.25)), 0.012, "M_Black", g, seg=5)
    abox("BogieBeam_" + sn, (0.24, 3.3, 0.26), B + Vector((0, 0, -0.12)), "M_Gear", bg, bevel=0.05)
    for ay in (-1.45, 0.0, 1.45):
        rod("Axle_%s_%s" % (sn, ay), B + Vector((-0.58, ay, -0.25)), B + Vector((0.58, ay, -0.25)), 0.07, "M_Gear", bg, seg=10)
        for wx in (-0.34, 0.34):
            wheel("MWheel_%s_%s_%s" % (sn, ay, wx), B + Vector((wx, ay, -0.25)), 0.63, 0.40, ["M_Tire", "M_Hub"], bg, seg=24, hub_r=0.33)
            acyl("MHubCap_%s_%s_%s" % (sn, ay, wx), 0.15, 0.05, B + Vector((wx + (0.21 if wx > 0 else -0.21), ay, -0.25)), "M_Hub", bg,
                 axis=0, seg=12)
    rod("BogieDamper_" + sn, B + Vector((0, -1.2, -0.05)), B - d * 0.9 + Vector((0, -0.1, 0)), 0.045, "M_Chrome", bg, seg=8)
    PIV[g] = (tuple(P), None)
    PIV[bg] = (tuple(B), None)
    open_box("MGWell_" + sn, (1.25, 5.2, 1.40), (x, 1.15, 3.32), "M_Well", "Body")
    dys = [POD_HOLE[0] + 0.02 + (POD_HOLE[1] - POD_HOLE[0] - 0.04) * k / 10 for k in range(11)]
    for ds in (1, -1):
        dn = "MGDoor_%s_%s" % (sn, "o" if ds * side > 0 else "i")
        rng = (270.5, 303.5) if ds > 0 else (236.5, 269.5)
        loft(dn, dys, lambda y, x=x: tuple(v * (0.992 if i in (1, 2, 3) else 1.0) for i, v in enumerate(pod_prof(y, x))),
             ["M_Skin"], dn, seg=5, angle_range=rng)
        zc, hw, ht, hb, n, _xc = pod_prof(1.0, x)
        hp = section_pt(math.radians(304 if ds > 0 else 236), zc, hw, ht, hb, n, x)
        for k in range(3):
            abox("%s_rib%d" % (dn, k), (0.40, 0.04, 0.04), (x + ds * 0.28, -0.6 + k * 1.8, 2.70), "M_Well", dn, bevel=0.0)
        PIV[dn] = ((hp.x, POD_HOLE[0], hp.z), (hp.x, POD_HOLE[1], hp.z))

# ---------------------------------------------------------------- nose gear (twin 1080x400, retracts rearwards)
NP = Vector((0, -18.9, 3.10))
NB = Vector((0, -18.75, 0.54))
nd = (NB - NP).normalized()
rod("NGStrut", NP, NB - nd * 0.95, 0.13, "M_Gear", "Gear_N", seg=14)
rod("NGPiston", NB - nd * 1.05, NB + Vector((0, 0, 0.05)), 0.09, "M_Chrome", "Gear_N", seg=14)
rod("NGDrag", NP + Vector((0, -0.3, 0.1)), NP + nd * 1.4, 0.055, "M_Gear", "Gear_N", seg=8)
abox("NGFork", (0.80, 0.18, 0.16), NB + Vector((0, 0, 0.14)), "M_Gear", "Gear_N", bevel=0.03)
rod("NGAxle", NB + Vector((-0.55, 0, 0)), NB + Vector((0.55, 0, 0)), 0.05, "M_Gear", "Gear_N", seg=10)
for wx in (-0.32, 0.32):
    wheel("NWheel_%s" % wx, NB + Vector((wx, 0, 0)), 0.54, 0.38, ["M_Tire", "M_Hub"], "Gear_N", seg=22, hub_r=0.27)
for sx in (1, -1):
    abox("NGTaxiLight_%d" % sx, (0.15, 0.12, 0.15), NB + Vector((sx * 0.2, -0.15, 1.35)), "M_LampWhite", "Gear_N", bevel=0.02, dirt=False)
abox("NGMudGuard", (1.0, 0.5, 0.05), NB + Vector((0, 0.05, 0.66)), "M_Gear", "Gear_N", bevel=0.02)
PIV["Gear_N"] = (tuple(NP), None)
# well: forward part under the cabin floor (with the ladder opening), aft part taller (equipment bay above)
open_box("NGWellF", (0.98, 0.95, 0.85), (0, -19.325, 3.03), "M_Well", "Body", open_faces=("-Z", "+Z", "+Y"))
open_box("NGWellA", (0.98, 3.25, 1.35), (0, -17.225, 3.28), "M_Well", "Body", open_faces=("-Z", "-Y"))
abox("NGWellStep", (0.98, 0.03, 0.50), (0, -18.85, 3.70), "M_Well", "Body", bevel=0.0)
abox("NGWellRoofF", (0.98, 0.20, 0.03), (0, -19.70, 3.45), "M_Well", "Body", bevel=0.0)
abox("NGWellRoofA", (0.98, 0.15, 0.03), (0, -18.925, 3.45), "M_Well", "Body", bevel=0.0)
for sx in (1, -1):
    abox("NGWellRoofS_%d" % sx, (0.18, 0.60, 0.03), (sx * 0.40, -19.30, 3.45), "M_Well", "Body", bevel=0.0)
ngys = [-19.78 + 4.16 * k / 8 for k in range(9)]
for ds in (1, -1):
    dn = "NGDoor_%s" % ("L" if ds > 0 else "R")
    loft(dn, ngys, bay_prof, ["M_Skin"], dn, seg=3, angle_range=(270.5, 287.5) if ds > 0 else (252.5, 269.5))
    _zc, _hw, _ht, _hb, _n = fus_prof(-17.7)[:5]
    _pt = section_pt(math.radians(288 if ds > 0 else 252), _zc, _hw, _ht, _hb, _n)
    PIV[dn] = ((_pt.x, -19.8, _pt.z), (_pt.x, -15.6, _pt.z))
# crew ladder (extends from the floor hatch down to the ground)
for k in range(12):
    abox("LadderF_rung_%d" % k, (0.46, 0.035, 0.035), (0, -19.30, 0.28 + k * 0.29), "M_Chrome", "Ladder_F", bevel=0.005)
for sx in (-0.25, 0.25):
    abox("LadderF_rail_%s" % sx, (0.04, 0.05, 3.55), (sx, -19.30, 1.75), "M_Chrome", "Ladder_F", bevel=0.01)
    rod("LadderF_hand_%s" % sx, (sx, -19.30, 3.5), (sx, -19.45, 4.3), 0.02, "M_Chrome", "Ladder_F", seg=6)
PIV["Ladder_F"] = ((0, -19.3, FLOOR_Z), None)


# ---------------------------------------------------------------- swing-wing control surfaces (children of Wing_L/R in step 4)
def ctrl_surface(name, x0, x1, side, le_fn, c_fn, z_fn, hinge_frac, t, grp, front=False, mat="M_SkinPanel"):
    secs = []
    for x in (x0, x1):
        c = c_fn(x)
        if front:   # slat: LE .. hinge_frac
            secs.append(dict(le=(x * side, le_fn(x) - 0.02, z_fn(x) - 0.02), cd=(0, 1, 0), ud=(0, 0, 1), c=hinge_frac * c + 0.05, t=t, cam=0.0))
        else:
            secs.append(dict(le=(x * side, le_fn(x) + hinge_frac * c - 0.04, z_fn(x)), cd=(0, 1, 0), ud=(0, 0, 1),
                             c=(1 - hinge_frac) * c + 0.04, t=t, cam=0.0))
    surface(name, secs, [mat], grp, nc=8, cap_root=True, cap_tip=True)
    p0 = Vector((x0 * side, le_fn(x0) + hinge_frac * c_fn(x0), z_fn(x0)))
    p1 = Vector((x1 * side, le_fn(x1) + hinge_frac * c_fn(x1), z_fn(x1)))
    if side < 0:
        pass
    PIV[grp] = (tuple(p0), tuple(p1))


for side, sn in ((1, "L"), (-1, "R")):
    ctrl_surface("Slat_" + sn, 10.4, 27.4, side, ow_le, ow_c, ow_z, SLAT, 0.42, "Slat_" + sn, front=True)
    ctrl_surface("FlapIn_" + sn, 10.4, 15.2, side, ow_le, ow_c, ow_z, WFIX, 0.20, "FlapIn_" + sn)
    ctrl_surface("FlapMid_" + sn, 15.3, 20.1, side, ow_le, ow_c, ow_z, WFIX, 0.20, "FlapMid_" + sn)
    ctrl_surface("FlapOut_" + sn, 20.2, 23.9, side, ow_le, ow_c, ow_z, WFIX, 0.20, "FlapOut_" + sn)
    ctrl_surface("Aileron_" + sn, 24.0, 27.4, side, ow_le, ow_c, ow_z, WFIX, 0.20, "Aileron_" + sn)
    # spoiler panels on the upper surface (static detail lines on the wing node)
    for k in range(4):
        fx = 13.0 + k * 3.0
        abox("SpoilerLine_%s_%d" % (sn, k), (2.8, 0.03, 0.01), (fx * side, ow_le(fx) + 0.62 * ow_c(fx), ow_z(fx) + 0.06 * ow_c(fx) * 0.5),
             "M_SkinPanel", "Wing_" + sn, bevel=0.0)
    PIV["Wing_" + sn] = ((PIVOT_X * side, PIVOT_Y, PIVOT_Z), None)
    # all-moving stabiliser: pivot axis at 40 % chord
    p0 = Vector((STAB_ROOT[0] * side, STAB_ROOT[1] + 0.4 * STAB_ROOT[3], STAB_ROOT[2]))
    p1 = Vector((STAB_TIP[0] * side, STAB_TIP[1] + 0.4 * STAB_TIP[3], STAB_TIP[2]))
    PIV["Stab_" + sn] = (tuple(p0), tuple(p1))
PIV["Rudder"] = ((0, fin_le(FIN_SPLIT) + 0.35 * fin_c(FIN_SPLIT), FIN_SPLIT), (0, FIN_LE1 + 0.35 * FIN_C1, FIN_Z1))

# ---------------------------------------------------------------- weapons bays: liners, frames, racks, doors
for bi, (y0, y1) in enumerate((BAY1, BAY2), start=1):
    ym, L = (y0 + y1) / 2, (y1 - y0)
    open_box("Bay%d" % bi, (1.92, L - 0.04, 1.75), (0, ym, 3.48), "M_Well", "Body")
    for k in range(int(L / 1.25) + 1):
        yy = y0 + 0.2 + k * 1.25
        if yy < y1 - 0.1:
            abox("Bay%dFrame_%d" % (bi, k), (1.90, 0.06, 0.10), (0, yy, 4.30), "M_Well", "Body", bevel=0.0)
    for rx in (-0.55, 0.0, 0.55):
        abox("Bay%dRail_%s" % (bi, rx), (0.08, L - 0.4, 0.12), (rx, ym, 4.20), "M_DarkMetal", "Body", bevel=0.01)
    abox("Bay%dLamp" % bi, (0.25, 0.12, 0.05), (0.8, ym, 4.30), "M_LampWhite", "Body", bevel=0.01, dirt=False)
    bys = [y0 + 0.02 + (L - 0.04) * k / 12 for k in range(13)]
    for sn, rng, a in (("L", (270.5, 299.5), 300), ("R", (240.5, 269.5), 240)):
        dn = "BayDoor%d_%s" % (bi, sn)
        loft(dn, bys, bay_prof, ["M_Skin"], dn, seg=6, angle_range=rng)
        _zc, _hw, _ht, _hb, _n = bay_prof(ym)
        _pt = section_pt(math.radians(a), _zc, _hw, _ht, _hb, _n)
        for k in range(5):
            abox("%s_rib%d" % (dn, k), (0.6, 0.05, 0.05), (_pt.x * 0.5, y0 + 1.0 + k * (L - 2) / 4, 2.72), "M_Well", dn, bevel=0.0)
        PIV[dn] = ((_pt.x, y0, _pt.z), (_pt.x, y1, _pt.z))

# ---------------------------------------------------------------- sensors, antennas, lights
for sx in (1, -1):
    rod("AoAVane_%d" % sx, (1.2 * sx, -25.0, 4.0), (1.32 * sx, -25.15, 4.0), 0.015, "M_Black", "Body", seg=5)
    acyl("StaticPort_%d" % sx, 0.06, 0.02, (1.45 * sx, -21.2, 3.8), "M_Chrome", "Body", axis=0, seg=10)
    abox("LandingLight_%d" % sx, (0.30, 0.42, 0.06), (0.6 * sx, -22.5, 2.58), "M_LampWhite", "Body", bevel=0.02, dirt=False)
    abox("FormationLight_%d" % sx, (0.03, 0.5, 0.06), (1.76 * sx, -8.0, 4.4), "M_LampGreen" if sx < 0 else "M_LampRed", "Body", dirt=False)
    abox("ECMFairing_%d" % sx, (0.10, 1.8, 0.35), (0.42 * sx, 23.0, 5.0), "M_Radome", "Body", bevel=0.04, smooth=True)
abox("SightFairing", (0.55, 1.3, 0.35), (0, -24.6, 2.68), "M_Radome", "Body", bevel=0.12, smooth=True)
abox("SightWindow", (0.40, 0.03, 0.22), (0, -25.26, 2.66), "M_Glass", "Body", bevel=0.0, dirt=False)
for i, (yy, top) in enumerate(((-15.0, True), (-6.0, True), (8.0, True), (-12.0, False), (14.5, False))):
    zc, hw, ht, hb, n = fus_prof(yy)[:5]
    z = zc + ht if top else zc - hb
    abox("BladeAnt_%d" % i, (0.03, 0.35, 0.40), (0, yy, z + (0.18 if top else -0.18)), "M_SkinPanel", "Body",
         rot=(0.35 if top else -0.35, 0, 0), bevel=0.01)
aobj("Astrodome", bm_sphere(0.22, (0, -20.4, 6.02), 12, 6, (1, 1, 0.55)), "M_Glass", "Body", dirt=False)
# escape-hatch outlines above the four ejection seats (K-36LM fire upwards through them)
for (hx, hy) in ((0.62, -23.6), (-0.62, -23.6), (0.62, -22.0), (-0.62, -22.0)):
    zc, hw, ht, hb, n = fus_prof(hy)[:5]
    z = zc + ht * (1 - (abs(hx) / hw) ** n) ** (1 / n)
    for (dx, dy, w, l) in ((0, -0.42, 0.75, 0.03), (0, 0.42, 0.75, 0.03), (-0.37, 0, 0.03, 0.84), (0.37, 0, 0.03, 0.84)):
        abox("EjHatch_%s_%s_%s_%s" % (hx, hy, dx, dy), (w, l, 0.012), (hx + dx, hy + dy, z + 0.01), "M_Black", "Body", bevel=0.0, dirt=False)
json.dump({k: [list(v[0]), list(v[1]) if v[1] else None] for k, v in PIV.items()}, open("/home/user/.cache/air/t160_piv.json", "w"))
print("STEP2", stats())
