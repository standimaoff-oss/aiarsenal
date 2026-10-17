# Tu-95MS STEP 2: AV-60K coaxial propellers, landing gear (+wells, doors), control surfaces, bomb bay, hatches,
# tail turret, antennas and small exterior hardware. Moving parts get their own grp (joined into GLB nodes in step 4).
FLOOR_Z = 2.90          # forward cabin floor (cockpit)
FLOOR2_Z = 3.05         # raised aft section above the nose-gear well
TFLOOR_Z = 3.05         # tail cabin floor
PIV = {}                # pivots/hinges for step 4: name -> (p0, p1)


def bay_prof(y):
    zc, hw, ht, hb, n = fus_prof(y)[:5]
    return (zc, hw * 0.992, ht * 0.992, hb * 0.992, n)



# ---------------------------------------------------------------- propellers (front rotor CW seen from behind, rear CCW)
def blade(name, hub, phi, s, grp, r0=0.55, r1=2.8, axial=0.0):
    secs = []
    n = 7
    er = Vector((math.cos(phi), 0, math.sin(phi)))
    et = Vector((-math.sin(phi), 0, math.cos(phi)))
    for i in range(n + 1):
        f = i / n
        r = r0 + (r1 - r0) * f
        c = 0.30 + 0.20 * math.sin(min(1.0, f * 1.6) * PI * 0.5) - 0.24 * f ** 3
        beta = (58 - 34 * f) * D2R
        cd = (et * (-s * math.cos(beta)) + Vector((0, 1, 0)) * math.sin(beta)).normalized()
        ud = cd.cross(er).normalized() * s
        le = Vector(hub) + er * r - cd * (0.3 * c) + Vector((0, axial, 0))
        secs.append(dict(le=le, cd=cd, ud=ud, c=c, t=0.11 - 0.06 * f, cam=0.03))
    return surface(name, secs, ["M_Prop", "M_PropTip"], grp, nc=8, cap_tip=True, cap_root=True,
                   mat_fn=lambda i, k: 1 if i >= n - 1 else 0)


for key, (nx, nz, inner) in NAC.items():
    yp = PROP_Y[key]
    hubF = Vector((nx, yp - 0.15, nz))
    hubR = Vector((nx, yp + 0.62, nz))
    for k in range(4):
        blade("BladeF_%s_%d" % (key, k), hubF, k * PI / 2 + PI / 4, 1, "Prop_%sF" % key)
        blade("BladeR_%s_%d" % (key, k), hubR, k * PI / 2, -1, "Prop_%sR" % key)
    lathe_y("SpinnerF_" + key, [(-1.45, 0.0), (-1.3, 0.16), (-1.0, 0.30), (-0.6, 0.42), (-0.2, 0.50), (0.25, 0.54), (0.30, 0.0)],
            (nx, yp, nz), ["M_Skin"], "Prop_%sF" % key, seg=20)
    lathe_y("SpinnerR_" + key, [(0.30, 0.0), (0.32, 0.555), (0.95, 0.60), (1.0, 0.0)], (nx, yp, nz), ["M_Skin"], "Prop_%sR" % key, seg=20)
    for k in range(4):  # blade root cuffs
        a = k * PI / 2 + PI / 4
        acyl("CuffF_%s_%d" % (key, k), 0.11, 0.25, (nx + math.cos(a) * 0.58, yp - 0.15, nz + math.sin(a) * 0.58), "M_Prop",
             "Prop_%sF" % key, rot=(0, -a + PI / 2, 0), seg=10)
    for d, rr in ((-0.15, 2.82), (0.62, 2.82)):
        acyl("PropDisc_%s_%s" % (key, "F" if d < 0 else "R"), rr, 0.01, (nx, yp + d, nz), "M_PropDisc", "PropDisc_" + key, axis=1,
             seg=40, dirt=False)
    PIV["Prop_%sF" % key] = (tuple(hubF), None)
    PIV["Prop_%sR" % key] = (tuple(hubR), None)

# ---------------------------------------------------------------- main gear (4-wheel bogies, retract rearwards into the inner nacelles)
for side, sn, key in ((1, "L", "2"), (-1, "R", "3")):
    x = 6.3 * side
    P = Vector((x, 0.35, 3.00))
    B = Vector((x, 0.0, 0.95))
    g = "Gear_" + sn
    rod("MGStrut_" + sn, P, B + Vector((0, 0, 0.75)), 0.15, "M_Gear", g, seg=14)
    rod("MGPiston_" + sn, B + Vector((0, 0, 0.8)), B, 0.105, "M_Chrome", g, seg=14)
    rod("MGDrag_" + sn, P + Vector((0, -0.35, 0.0)), B + Vector((0, -0.08, 1.2)), 0.07, "M_Gear", g, seg=8)
    rod("MGSide_" + sn, P + Vector((-0.55 * side, 0.2, 0.0)), B + Vector((0, 0.05, 1.3)), 0.055, "M_Gear", g, seg=8)
    for dz in (0.25, 0.55):  # torque links
        abox("MGTorque_%s_%s" % (sn, dz), (0.06, 0.26, 0.05), B + Vector((0, -0.14, dz + 0.1)), "M_Gear", g, rot=(0.6 if dz < 0.4 else -0.6, 0, 0))
    rod("MGBrakeLine_" + sn, P + Vector((0.12, -0.1, -0.2)), B + Vector((0.12, -0.12, 0.25)), 0.012, "M_Black", g, seg=5)
    bg = "Bogie_" + sn
    abox("Bogie_" + sn, (0.22, 1.95, 0.24), B + Vector((0, 0, -0.2)), "M_Gear", bg, bevel=0.04)
    for ay in (-0.85, 0.85):
        rod("Axle_%s_%s" % (sn, ay), B + Vector((-0.6, ay, -0.2)), B + Vector((0.6, ay, -0.2)), 0.07, "M_Gear", bg, seg=10)
        for wx in (-0.36, 0.36):
            wheel("MWheel_%s_%s_%s" % (sn, ay, wx), B + Vector((wx, ay, -0.2)), 0.75, 0.45, ["M_Tire", "M_Hub"], bg, seg=24, hub_r=0.40)
            acyl("MHubCap_%s_%s_%s" % (sn, ay, wx), 0.18, 0.06, B + Vector((wx + (0.24 if wx > 0 else -0.24), ay, -0.2)), "M_Hub", bg, axis=0, seg=12)
    PIV[g] = (tuple(P), None)
    PIV[bg] = (tuple(B + Vector((0, 0, -0.2))), None)
    # wheel well liner in the nacelle + two long curved doors (lofted from the nacelle skin), hinged at the hole edges
    open_box("MGWell_" + sn, (1.18, 5.3, 1.65), (x, 1.95, 3.375), "M_Well", "Body")
    nprof, nyp = NAC_PROF[key]

    def nac_door_prof(y, nprof=nprof, nyp=nyp, x=x):
        t = y - nyp
        for (a0, r0), (a1, r1) in zip(nprof, nprof[1:]):
            if a0 <= t <= a1:
                r = r0 + (r1 - r0) * (t - a0) / (a1 - a0)
                break
        else:
            r = nprof[-1][1]
        r *= 0.992
        return (3.30, r, r, r, 2.0, x)
    dys = [-0.68 + 5.26 * k / 10 for k in range(11)]
    for ds in (1, -1):
        dn = "MGDoor_%s_%s" % (sn, "o" if ds * side > 0 else "i")
        rng = (270.5, 308.1) if ds > 0 else (231.9, 269.5)
        loft(dn, dys, nac_door_prof, ["M_Skin"], dn, seg=5, angle_range=rng)
        ha = math.radians(308.6 if ds > 0 else 231.4)
        hx, hz = x + 0.95 * math.cos(ha), 3.30 + 0.95 * math.sin(ha)
        for k in range(4):  # inner stiffeners
            abox("%s_rib%d" % (dn, k), (0.45, 0.04, 0.04), (x + ds * 0.30, -0.2 + k * 1.5, 2.44), "M_Well", dn, bevel=0.0)
        PIV[dn] = ((hx, -0.7, hz), (hx, 4.6, hz))

# ---------------------------------------------------------------- nose gear (twin wheels, retracts rearwards)
NG_Y = -14.8
NP = Vector((0, NG_Y - 0.1, 2.35))
NB = Vector((0, NG_Y, 0.55))
rod("NGStrut", NP, NB + Vector((0, 0, 0.65)), 0.12, "M_Gear", "Gear_N", seg=14)
rod("NGPiston", NB + Vector((0, 0, 0.7)), NB + Vector((0, 0, 0.05)), 0.085, "M_Chrome", "Gear_N", seg=14)
rod("NGDrag", NP + Vector((0, -0.3, 0.1)), NB + Vector((0, -0.04, 1.1)), 0.055, "M_Gear", "Gear_N", seg=8)
abox("NGFork", (0.75, 0.16, 0.14), NB + Vector((0, 0, 0.12)), "M_Gear", "Gear_N", bevel=0.03)
rod("NGAxle", NB + Vector((-0.5, 0, 0)), NB + Vector((0.5, 0, 0)), 0.05, "M_Gear", "Gear_N", seg=10)
for wx in (-0.30, 0.30):
    wheel("NWheel_%s" % wx, NB + Vector((wx, 0, 0)), 0.55, 0.34, ["M_Tire", "M_Hub"], "Gear_N", seg=22, hub_r=0.28)
abox("NGTaxiLight", (0.16, 0.12, 0.16), NB + Vector((0, -0.12, 1.25)), "M_LampWhite", "Gear_N", bevel=0.02, dirt=False)
abox("NGMudGuard", (0.95, 0.5, 0.05), NB + Vector((0, 0.05, 0.68)), "M_Gear", "Gear_N", bevel=0.02)
PIV["Gear_N"] = (tuple(NP), None)
open_box("NGWell", (0.80, 3.2, 1.03), (0, -14.0, 2.485), "M_Well", "Body", open_faces=("-Z", "+Z"))
# well roof with the crew-entry opening (ladder) at y -13.62 .. -12.98
abox("NGWellRoofF", (0.80, 1.98, 0.03), (0, -14.61, 3.0), "M_Well", "Body", bevel=0.0)
for sx in (1, -1):
    abox("NGWellRoofS_%d" % sx, (0.08, 0.64, 0.03), (sx * 0.36, -13.30, 3.0), "M_Well", "Body", bevel=0.0)
abox("NGWellRoofA", (0.80, 0.58, 0.03), (0, -12.69, 3.0), "M_Well", "Body", bevel=0.0)
ngys = [-15.58 + 3.16 * k / 7 for k in range(8)]
for ds in (1, -1):
    dn = "NGDoor_%s" % ("L" if ds > 0 else "R")
    loft(dn, ngys, bay_prof, ["M_Skin"], dn, seg=3, angle_range=(270.5, 285.5) if ds > 0 else (254.5, 269.5))
    _zc, _hw, _ht, _hb, _n = fus_prof(-14.25)[:5]
    _pt = section_pt(math.radians(286 if ds > 0 else 254), _zc, _hw, _ht, _hb, _n)
    PIV[dn] = ((_pt.x, -15.6, _pt.z), (_pt.x, -12.4, _pt.z))

# tail bumper (retractable skid)
rod("TailBumper", (0, 15.2, 2.25), (0, 15.6, 1.35), 0.06, "M_Gear", "Gear_T", seg=8)
abox("TailSkid", (0.22, 0.45, 0.08), (0, 15.62, 1.32), "M_Rubber", "Gear_T", bevel=0.03)
PIV["Gear_T"] = ((0, 15.2, 2.25), None)

# ---------------------------------------------------------------- control surfaces (hinge empties built in step 4)
def ctrl_surface(name, x0, x1, side, le_fn, c_fn, z_fn, hinge_frac=0.73, t=0.24, grp=None):
    secs = []
    for x in (x0, x1):
        c = c_fn(x)
        secs.append(dict(le=(x * side, le_fn(x) + hinge_frac * c - 0.04, z_fn(x)), cd=(0, 1, 0), ud=(0, 0, 1), c=(1 - hinge_frac) * c + 0.04,
                         t=t, cam=0.0))
    surface(name, secs, ["M_SkinPanel"], grp or name, nc=8, cap_root=True, cap_tip=True)
    p0 = Vector((x0 * side, le_fn(x0) + hinge_frac * c_fn(x0), z_fn(x0)))
    p1 = Vector((x1 * side, le_fn(x1) + hinge_frac * c_fn(x1), z_fn(x1)))
    PIV[grp or name] = (tuple(p0), tuple(p1))


for side, sn in ((1, "L"), (-1, "R")):
    ctrl_surface("Aileron_" + sn, 17.3, 24.2, side, wing_le, wing_c, wing_z, t=0.22)
    ctrl_surface("FlapIn_" + sn, 1.5, 5.35, side, wing_le, wing_c, wing_z, t=0.24)
    ctrl_surface("FlapMid_" + sn, 7.25, 12.75, side, wing_le, wing_c, wing_z, t=0.24)
    ctrl_surface("FlapOut_" + sn, 14.45, 17.15, side, wing_le, wing_c, wing_z, t=0.24)
    # flap-track fairings under the wing (canoes)
    for fx in (4.0, 10.0, 16.0):
        c = wing_c(fx)
        lathe_y("FlapCanoe_%s_%d" % (sn, fx), [(0, 0.0), (0.4, 0.12), (1.6, 0.15), (2.6, 0.08), (3.0, 0.0)],
                (fx * side, wing_le(fx) + 0.55 * c, wing_z(fx) - 0.22), ["M_Skin"], "Body", seg=10)
    # trim tab on the aileron (static detail on the aileron node)
    abox("AilTrim_" + sn, (1.2, 0.30, 0.03), ((20.5 * side), wing_le(20.5) + wing_c(20.5) - 0.13, wing_z(20.5)), "M_SkinPanel", "Aileron_" + sn, bevel=0.005)

STAB_LE = lambda x: STAB_ROOT[1] + (x - STAB_ROOT[0]) * 0.84
STAB_C = lambda x: STAB_ROOT[3] + (STAB_TIP[3] - STAB_ROOT[3]) * (x - STAB_ROOT[0]) / (STAB_TIP[0] - STAB_ROOT[0])
STAB_Z = lambda x: STAB_ROOT[2] + (STAB_TIP[2] - STAB_ROOT[2]) * (x - STAB_ROOT[0]) / (STAB_TIP[0] - STAB_ROOT[0])
for side, sn in ((1, "L"), (-1, "R")):
    ctrl_surface("Elevator_" + sn, 1.05, 7.25, side, STAB_LE, STAB_C, STAB_Z, hinge_frac=0.70, t=0.22)

# rudder: vertical hinge surface
FIN_LE = lambda z: 11.2 + (z - 4.6) * (19.3 - 11.2) / (12.1 - 4.6)
FIN_C = lambda z: 8.6 + (2.8 - 8.6) * (z - 4.6) / (12.1 - 4.6)
secs = []
for z in (4.95, 11.85):
    c = FIN_C(z)
    secs.append(dict(le=(0, FIN_LE(z) + 0.70 * c - 0.04, z), cd=(0, 1, 0), ud=(1, 0, 0), c=0.30 * c + 0.04, t=0.22))
surface("Rudder", secs, ["M_SkinPanel"], "Rudder", nc=8, cap_root=True, cap_tip=True)
PIV["Rudder"] = ((0, FIN_LE(4.95) + 0.7 * FIN_C(4.95), 4.95), (0, FIN_LE(11.85) + 0.7 * FIN_C(11.85), 11.85))

# ---------------------------------------------------------------- bomb bay: liner, frames, racks, doors
open_box("BayLiner", (1.94, 11.0, 1.55), (0, 0.0, 3.075), "M_Well", "Body")
for k in range(12):
    yy = BAY_Y0 + 0.5 + k * 0.92
    abox("BayFrame_%d" % k, (1.9, 0.08, 0.10), (0, yy, 3.80), "M_Well", "Body", bevel=0.01)
    for sx in (1, -1):
        abox("BayRib_%d_%d" % (k, sx), (0.06, 0.08, 1.4), (0.94 * sx, yy, 3.1), "M_Well", "Body", bevel=0.01)
for rx in (-0.45, 0.0, 0.45):
    abox("BayRackBeam_%s" % rx, (0.10, 10.6, 0.14), (rx, 0.0, 3.72), "M_Gear", "Body", bevel=0.02)
for k in range(10):
    rod("BayHose_%d" % k, (0.85, BAY_Y0 + 0.4 + k * 1.1, 3.75), (0.85, BAY_Y0 + 1.3 + k * 1.1, 3.75), 0.025, "M_Black", "Body", seg=6)
abox("BayLamp", (0.25, 0.12, 0.06), (0, -4.8, 3.82), "M_LampWhite", "Body", bevel=0.01, dirt=False)
# doors = partial lofts of the fuselage skin, slightly inset, hinged at their outer edges


bys = [BAY_Y0 + 0.02 + (BAY_Y1 - BAY_Y0 - 0.04) * k / 12 for k in range(13)]
loft("BayDoor_L", bys, bay_prof, ["M_Skin"], "BayDoor_L", seg=6, angle_range=(270.5, 311.5))
loft("BayDoor_R", bys, bay_prof, ["M_Skin"], "BayDoor_R", seg=6, angle_range=(228.5, 269.5))
for sn, a in (("L", 312), ("R", 228)):
    _zc, _hw, _ht, _hb, _n = bay_prof(0.0)
    _pt = section_pt(math.radians(a), _zc, _hw, _ht, _hb, _n)
    px, pz = _pt.x, _pt.z
    PIV["BayDoor_" + sn] = ((px, BAY_Y0, pz), (px, BAY_Y1, pz))
    for k in range(6):  # door stiffeners inside
        abox("BayDoorRib_%s_%d" % (sn, k), (0.6, 0.05, 0.05), (px * 0.55, BAY_Y0 + 1 + k * 1.8, 2.10), "M_Well", "BayDoor_" + sn)

# ---------------------------------------------------------------- crew hatches / ladders
# forward: ladder inside the nose-gear well up to the cabin floor opening
for k in range(10):
    abox("LadderF_rung_%d" % k, (0.48, 0.035, 0.035), (0, -13.30, 0.3 + k * 0.30), "M_Chrome", "Ladder_F", bevel=0.005)
for sx in (-0.26, 0.26):
    abox("LadderF_rail_%s" % sx, (0.04, 0.05, 3.10), (sx, -13.30, 1.53), "M_Chrome", "Ladder_F", bevel=0.01)
PIV["Ladder_F"] = ((0, -13.3, FLOOR2_Z), None)
# rear: hatch door (hinged at its front edge) + ladder
rh = loft("Hatch_R", [17.32 + 0.76 * k / 3 for k in range(4)], bay_prof, ["M_Skin"], "Hatch_R", seg=4, angle_range=(252.5, 287.5))
PIV["Hatch_R"] = ((-0.4, 17.3, 2.45), (0.4, 17.3, 2.45))
for k in range(9):
    abox("LadderR_rung_%d" % k, (0.42, 0.035, 0.035), (0, 17.75, 0.3 + k * 0.32), "M_Chrome", "Ladder_R", bevel=0.005)
for sx in (-0.23, 0.23):
    abox("LadderR_rail_%s" % sx, (0.04, 0.05, 3.05), (sx, 17.75, 1.5), "M_Chrome", "Ladder_R", bevel=0.01)
PIV["Ladder_R"] = ((0, 17.75, 3.05), None)

# ---------------------------------------------------------------- tail turret DK-12 (2x GSh-23) + PRS-4 radar radome
TT = Vector((0, 22.75, 3.62))
aobj("TurretDome", bm_sphere(0.42, TT, 16, 8, (1.0, 0.9, 0.85)), "M_Skin", "Turret_T")
for gx in (-0.13, 0.13):
    rod("GunBarrel_%s" % gx, TT + Vector((gx, 0.1, 0)), TT + Vector((gx, 1.75, 0)), 0.035, "M_DarkMetal", "Turret_T", seg=8)
    rod("GunMuzzle_%s" % gx, TT + Vector((gx, 1.6, 0)), TT + Vector((gx, 1.8, 0)), 0.05, "M_DarkMetal", "Turret_T", seg=8)
    abox("GunJacket_%s" % gx, (0.12, 0.6, 0.14), TT + Vector((gx, 0.55, 0)), "M_DarkMetal", "Turret_T", bevel=0.02)
PIV["Turret_T"] = (tuple(TT), None)
lathe_y("TailRadome", [(-0.9, 0.0), (-0.7, 0.22), (-0.3, 0.33), (0.3, 0.33), (0.55, 0.15), (0.6, 0.0)], (0, 22.1, 4.82), ["M_Radome"], "Body", seg=16)

# ---------------------------------------------------------------- antennas, probes, lights (level-2/3 hardware)
for i, (yy, top) in enumerate(((-11.5, True), (-2.0, True), (6.5, True), (-9.0, False), (11.0, False))):
    z = 4.84 if top else 1.95
    abox("BladeAnt_%d" % i, (0.03, 0.35, 0.42), (0, yy, z + (0.2 if top else -0.2)), "M_Skin", "Body", rot=(0.35 if top else -0.35, 0, 0), bevel=0.01)
for sx in (1, -1):
    rod("Pitot_%d" % sx, (1.05 * sx, -20.9, 3.55), (1.25 * sx, -21.8, 3.55), 0.025, "M_Chrome", "Body", seg=6)
    acyl("StaticPort_%d" % sx, 0.06, 0.02, (1.43 * sx, -16.0, 3.0), "M_Chrome", "Body", axis=0, seg=10)
    abox("LandingLight_%d" % sx, (0.28, 0.4, 0.06), (0.55 * sx, -18.5, 1.95), "M_LampWhite", "Body", bevel=0.02, dirt=False)
    abox("AoAVane_%d" % sx, (0.02, 0.18, 0.10), (1.40 * sx, -19.6, 3.9), "M_Black", "Body", bevel=0.005)
    abox("FormationLight_%d" % sx, (0.03, 0.4, 0.06), (1.45 * sx, 2.0, 3.6), "M_LampGreen" if sx < 0 else "M_LampRed", "Body", dirt=False)
aobj("Astrodome", bm_sphere(0.30, (0, -13.4, 4.86), 12, 6, (1, 1, 0.6)), "M_Glass", "Body", dirt=False)
rod("WireAntenna", (0, -11.0, 4.85), (0, 12.4, 6.6), 0.008, "M_Black", "Body", seg=4)
rod("WireMast", (0, -11.0, 4.85), (0, -11.0, 5.15), 0.02, "M_Black", "Body", seg=5)
for k in range(6):  # fuel vents / drains under the wing roots and fuselage
    sx = 1 if k % 2 else -1
    rod("Drain_%d" % k, (sx * (2.5 + k), wing_le(2.5 + k) + 4.0, wing_z(2.5 + k) - 0.55), (sx * (2.5 + k), wing_le(2.5 + k) + 4.25,
        wing_z(2.5 + k) - 0.75), 0.018, "M_Chrome", "Body", seg=5)
print("STEP2", stats())
import json as _j
_j.dump({k: [list(v[0]) if v[0] else None, list(v[1]) if v[1] else None] for k, v in PIV.items()}, open("/home/user/.cache/air/t95_piv.json", "w"))
