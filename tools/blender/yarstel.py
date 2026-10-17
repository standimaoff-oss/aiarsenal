# Yars TEL — MZKT-79221-type 16x16 transporter-erector-launcher, detailed rebuild (replaces the box TEL of nukes.py).
# Run headless:  blender -b --factory-startup --python-expr "<lib.py + alib.py + arsenal_lib.py + this file>"
# Conventions (alib): metres, Z up, cab towards -Y, +X = vehicle LEFT, origin = ground centre.
# Node contract used by the mod (ArsRenderer / LauncherEntity):
#   YARSTEL (root) > YARSTEL_geo (static chassis)
#                  > YARSTEL_Erector (hinge, rot X) > erector geo, YARSTEL_Cap (front cap, hidden after launch), YARSTEL_Missile
#                  > YARSTEL_Ram{L,R} (rot X) > _S1 > _S2 (telescopic stages, translate local Z)
#                  > YARSTEL_Jack{0..3} (translate Z)       > YARSTEL_St{a}{L,R} (steer, rot Z) > YARSTEL_W{a}{L,R} (spin, rot X)
#   clips: yarstel_erect (0..96: erector 0 -> 90 deg + rams), yarstel_jacks (0..40: jacks down)
reset_scene()
col_get("BUILD"); col_get("RIG")
ars_common_mats()
amat("M_TelGreen", "T_CamoGreen", 0.75, 0.05, factor=(0.78, 0.84, 0.72, 1))
amat("M_TelPlain", None, 0.7, 0.05, factor=(0.20, 0.25, 0.15, 1))
amat("M_TpkGreen", None, 0.62, 0.08, factor=(0.25, 0.31, 0.19, 1))
amat("M_TpkRib", None, 0.55, 0.15, factor=(0.21, 0.26, 0.16, 1))
amat("M_Rubber", None, 0.9, 0.0, factor=(0.05, 0.05, 0.05, 1))
amat("M_CabGlass", None, 0.05, 0.3, factor=(0.10, 0.14, 0.16, 1), glass_alpha=0.55)
amat("M_Chrome", None, 0.2, 1.0, factor=(0.8, 0.8, 0.82, 1))
amat("M_HeadLamp", None, 0.1, 0.0, factor=(1.0, 0.97, 0.85, 1), emissive=(0.9, 0.85, 0.7, 1))
amat("M_Amber", None, 0.3, 0.0, factor=(1.0, 0.55, 0.05, 1), emissive=(0.8, 0.4, 0.02, 1))
amat("M_Hydraulic", None, 0.15, 1.0, factor=(0.85, 0.86, 0.88, 1))
amat("M_Stencil", None, 0.6, 0.0, factor=(0.85, 0.85, 0.78, 1))

P = "YARSTEL"
G = P + "_body"
TL = 22.7
FY = -TL / 2                       # front face (cab) y
RY = TL / 2                        # rear y
HW = 1.70                          # half width
WR, WW, TX = 0.80, 0.60, 1.38      # wheel radius, width, track half (wheel centre x)
SP = [1.8, 2.4, 1.8, 2.9, 1.8, 2.4, 1.8]
AX = [FY + 3.0]
for s_ in SP:
    AX.append(AX[-1] + s_)
STEER = (0, 1, 2, 5, 6, 7)         # steered axles (front 3 + rear 3, crab/all-wheel steering)
TPK_R = 1.06
TPK_Z = 3.62
TPK_Y0, TPK_Y1 = FY + 0.55, RY - 0.15        # canister front (cap joint) / rear base
PIV = Vector((0, RY - 0.55, 2.45))            # erector hinge (rear)


def prism_x(name, prof, hw, mat, grp, bevel=0.04, x0=None, uvscale=0.5, dirt=True):
    """Extrude a (y,z) polygon across X (from -hw..hw, or x0..x0+2hw) and bevel all edges."""
    bm = bmesh.new()
    xa, xb = (-hw, hw) if x0 is None else (x0, x0 + 2 * hw)
    va = [bm.verts.new((xa, y, z)) for y, z in prof]
    vb = [bm.verts.new((xb, y, z)) for y, z in prof]
    n = len(prof)
    bm.faces.new(list(reversed(va)))
    bm.faces.new(vb)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((va[i], va[j], vb[j], vb[i]))
    bm.normal_update()
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    if bevel > 0:
        bmesh.ops.bevel(bm, geom=list(bm.edges), offset=bevel, segments=2, affect='EDGES', profile=0.6)
    return aobj(name, bm, [mat], grp, smooth=False, uv="box", uvscale=uvscale, dirt=dirt)


def slats(name, x0, x1, y0, y1, z, n, mat, grp, along='Y', t=0.03, h=0.06, tilt=0.5):
    """Row of louvre slats (engine deck grilles)."""
    for i in range(n):
        f = (i + 0.5) / n
        if along == 'Y':
            abox("%s_%d" % (name, i), (x1 - x0, t, h), ((x0 + x1) / 2, y0 + (y1 - y0) * f, z), mat, grp, rot=(tilt, 0, 0), bevel=0.004)
        else:
            abox("%s_%d" % (name, i), (t, y1 - y0, h), (x0 + (x1 - x0) * f, (y0 + y1) / 2, z), mat, grp, rot=(0, tilt, 0), bevel=0.004)


def bolt_ring(name, center, r, n, br, depth, mat, grp, axis=0, phase=0.0):
    c = Vector(center)
    for k in range(n):
        a = phase + 2 * PI * k / n
        if axis == 0:
            p = c + Vector((0, math.cos(a) * r, math.sin(a) * r))
        elif axis == 1:
            p = c + Vector((math.cos(a) * r, 0, math.sin(a) * r))
        else:
            p = c + Vector((math.cos(a) * r, math.sin(a) * r, 0))
        acyl("%s_%d" % (name, k), br, depth, tuple(p), mat, grp, axis=axis, seg=6, dirt=False)


# ======================================================================================= CHASSIS FRAME
for sx in (1, -1):
    abox(P + "_rail%d" % sx, (0.22, TL - 1.4, 0.42), (0.55 * sx, 0.35, 1.18), "M_TelPlain", G, bevel=0.02)
    abox(P + "_railf%d" % sx, (0.06, TL - 1.4, 0.30), (0.55 * sx + 0.11 * sx, 0.35, 1.18), "M_Dark", G, bevel=0.005)
for k, y in enumerate([FY + 1.2] + [(AX[i] + AX[i + 1]) / 2 for i in range(7)] + [RY - 0.6]):
    abox(P + "_xm%d" % k, (1.32, 0.16, 0.26), (0, y, 1.18), "M_TelPlain", G, bevel=0.01)
# axle beams / hub reduction housings (portal axles), suspension arms and shock absorbers
for a, ay in enumerate(AX):
    abox(P + "_axle%d" % a, (2.1, 0.22, 0.22), (0, ay, WR), "M_Dark", G, bevel=0.02)
    acyl(P + "_diff%d" % a, 0.24, 0.38, (0, ay, WR), "M_Dark", G, axis=1, seg=14)
    for sx in (1, -1):
        rod(P + "_arm%d_%d" % (a, sx), Vector((0.62 * sx, ay - 0.45, 1.05)), Vector((1.0 * sx, ay, WR + 0.05)), 0.06, "M_Dark", G, seg=8)
        rod(P + "_arm2%d_%d" % (a, sx), Vector((0.62 * sx, ay + 0.45, 1.05)), Vector((1.0 * sx, ay, WR + 0.05)), 0.06, "M_Dark", G, seg=8)
        rod(P + "_shk%d_%d" % (a, sx), Vector((0.78 * sx, ay + 0.15, 1.45)), Vector((0.98 * sx, ay + 0.1, WR + 0.12)), 0.055, "M_Steel", G, seg=8)
        rod(P + "_shkb%d_%d" % (a, sx), Vector((0.78 * sx, ay + 0.15, 1.45)), Vector((0.88 * sx, ay + 0.12, 1.1)), 0.075, "M_Black", G, seg=8)
        rod(P + "_ctis%d_%d" % (a, sx), Vector((0.9 * sx, ay - 0.2, 1.3)), Vector((1.06 * sx, ay, WR + 0.1)), 0.012, "M_Black", G, seg=5)
# drive shafts along the centre line
for i in range(7):
    rod(P + "_shaft%d" % i, Vector((0, AX[i] + 0.25, WR + 0.08)), Vector((0, AX[i + 1] - 0.25, WR + 0.08)), 0.06, "M_Steel", G, seg=8)

# ======================================================================================= CAB (full width, low, under the TPK nose)
cy0, cy1 = FY, FY + 2.2
cab_prof = [(cy0 + 0.02, 0.78), (cy0, 1.80), (cy0 + 0.28, 2.32), (cy0 + 0.50, 2.42), (cy1, 2.42), (cy1, 0.78)]
cab_shell = prism_x(P + "_cab", cab_prof, HW - 0.02, "M_TelGreen", G, bevel=0.07)
# r3: the cab is a real hollow shell (the mod has a first-person view from the seats): inner volume + window openings are
# cut with Blender boolean modifiers; cutter faces transfer their material (interior trim / black window reveals).
rw_mats()
CUTTERS = []
CUTTERS.append(prism_x(P + "_cab_in", [(cy0 + 0.09, 0.84), (cy0 + 0.07, 1.78), (cy0 + 0.33, 2.26), (cy0 + 0.52, 2.35),
                                        (cy1 - 0.06, 2.35), (cy1 - 0.06, 0.84)], HW - 0.08, "M_Interior", "CUT", bevel=0.0, dirt=False))
# front: 4-pane raked windscreen with frames, sun visor, wipers
ws_lo, ws_hi = Vector((0, cy0 + 0.02, 1.84)), Vector((0, cy0 + 0.25, 2.27))
wn = (ws_hi - ws_lo)
for k in range(4):
    x0 = -1.52 + k * 0.77
    c = (ws_lo + ws_hi) / 2
    abox(P + "_ws%d" % k, (0.70, 0.03, wn.length - 0.06), (x0 + 0.37, c.y - 0.025, c.z), "M_CabGlass", G,
         rot=(-math.atan2(wn.y, wn.z), 0, 0), bevel=0.0, dirt=False)
    CUTTERS.append(abox(P + "_wscut%d" % k, (0.64, 0.6, wn.length - 0.12), (x0 + 0.37, c.y + 0.05, c.z), "M_Black", "CUT",
                        rot=(-math.atan2(wn.y, wn.z), 0, 0), bevel=0.0, dirt=False))
for k in range(5):
    x = -1.53 + k * 0.77
    c = (ws_lo + ws_hi) / 2
    abox(P + "_wsf%d" % k, (0.07, 0.07, wn.length + 0.04), (x + 0.0, c.y - 0.04, c.z), "M_Black", G,
         rot=(-math.atan2(wn.y, wn.z), 0, 0), bevel=0.01)
abox(P + "_visor", (3.1, 0.32, 0.04), (0, cy0 + 0.28, 2.36), "M_TelGreen", G, rot=(-0.25, 0, 0), bevel=0.01)
for k in range(4):
    x = -1.15 + k * 0.77
    rod(P + "_wiper%d" % k, Vector((x, cy0 - 0.02, 1.87)), Vector((x + 0.12, cy0 + 0.1, 2.18)), 0.012, "M_Black", G, seg=5)
# bumper, tow hooks, headlights, indicators, grille, number plate, steps
abox(P + "_bumper", (3.36, 0.30, 0.42), (0, cy0 - 0.06, 1.12), "M_TelPlain", G, bevel=0.04)
abox(P + "_bumplip", (3.30, 0.06, 0.10), (0, cy0 - 0.22, 0.92), "M_Black", G, bevel=0.01)
for sx in (1, -1):
    acyl(P + "_hl%d" % sx, 0.13, 0.08, (1.18 * sx, cy0 - 0.18, 1.18), "M_Chrome", G, axis=1, seg=18)
    acyl(P + "_hll%d" % sx, 0.105, 0.04, (1.18 * sx, cy0 - 0.23, 1.18), "M_HeadLamp", G, axis=1, seg=18, dirt=False)
    acyl(P + "_fog%d" % sx, 0.07, 0.06, (0.78 * sx, cy0 - 0.19, 1.16), "M_HeadLamp", G, axis=1, seg=12, dirt=False)
    abox(P + "_ind%d" % sx, (0.16, 0.05, 0.08), (1.48 * sx, cy0 - 0.2, 1.24), "M_Amber", G, bevel=0.01, dirt=False)
    abox(P + "_hook%d" % sx, (0.12, 0.22, 0.14), (0.45 * sx, cy0 - 0.25, 0.98), "M_Black", G, bevel=0.02)
    abox(P + "_hlguard%d" % sx, (0.36, 0.03, 0.03), (1.18 * sx, cy0 - 0.27, 1.18), "M_Black", G, bevel=0.003)
    abox(P + "_marker%d" % sx, (0.10, 0.06, 0.05), (1.45 * sx, cy0 + 0.6, 2.45), "M_Amber", G, bevel=0.01, dirt=False)
for k in range(5):
    abox(P + "_gr%d" % k, (1.1, 0.04, 0.04), (0, cy0 - 0.02, 1.42 + k * 0.07), "M_Black", G, bevel=0.005)
abox(P + "_plate", (0.52, 0.02, 0.12), (0, cy0 - 0.22, 1.13), "M_Stencil", G, bevel=0.0, dirt=False)
# side: doors (2 per side), windows, handles, mirrors, steps and grab rails
for sx in (1, -1):
    xs = (HW - 0.01) * sx
    for d, (y0, y1) in enumerate([(cy0 + 0.50, cy0 + 1.62)]):
        abox(P + "_door%d_%d" % (d, sx), (0.02, y1 - y0, 0.70), (xs + 0.01 * sx, (y0 + y1) / 2, 1.47), "M_TelGreen", G, bevel=0.01)
        abox(P + "_doortop%d_%d" % (d, sx), (0.02, y1 - y0, 0.08), (xs + 0.01 * sx, (y0 + y1) / 2, 2.27), "M_TelGreen", G, bevel=0.005)
        for yy in (y0 + 0.055, y1 - 0.055):
            abox(P + "_doorst%d_%d_%d" % (d, sx, int(yy * 100)), (0.02, 0.11, 0.42), (xs + 0.01 * sx, yy, 2.03), "M_TelGreen", G, bevel=0.005)
        CUTTERS.append(abox(P + "_dcut%d_%d" % (d, sx), (0.5, y1 - y0 - 0.26, 0.36), (xs, (y0 + y1) / 2, 2.03), "M_Black", "CUT", bevel=0.0, dirt=False))
        for (ya, yb, za, zb) in [(y0, y1, 2.29, 2.31), (y0, y1, 1.12, 1.14), (y0, y0 + 0.02, 1.12, 2.31), (y1 - 0.02, y1, 1.12, 2.31)]:
            abox(P + "_dseam%d_%d_%d_%s" % (d, sx, int(za * 100), int(ya * 100)), (0.025, yb - ya, zb - za),
                 (xs + 0.012 * sx, (ya + yb) / 2, (za + zb) / 2), "M_Black", G, bevel=0.0)
        abox(P + "_dwin%d_%d" % (d, sx), (0.03, y1 - y0 - 0.22, 0.40), (xs + 0.02 * sx, (y0 + y1) / 2, 2.03), "M_CabGlass", G, bevel=0.0, dirt=False)
        for (fy, fz, fw, fh) in [(0, 0.22, y1 - y0 - 0.14, 0.04), (0, -0.22, y1 - y0 - 0.14, 0.04),
                                 ((y1 - y0 - 0.18) / 2, 0, 0.04, 0.48), (-(y1 - y0 - 0.18) / 2, 0, 0.04, 0.48)]:
            abox(P + "_dwinf%d_%d_%d_%d" % (d, sx, int(fy * 100), int(fz * 100)), (0.03, fw, fh), (xs + 0.016 * sx, (y0 + y1) / 2 + fy, 2.03 + fz),
                 "M_Black", G, bevel=0.0)
        abox(P + "_dh%d_%d" % (d, sx), (0.05, 0.18, 0.04), (xs + 0.04 * sx, y1 - 0.2, 1.72), "M_Chrome", G, bevel=0.01)
        for st in range(2):
            abox(P + "_step%d_%d_%d" % (d, sx, st), (0.32, 0.55, 0.04), (xs - 0.05 * sx, (y0 + y1) / 2, 0.55 + st * 0.32), "M_Steel", G, bevel=0.01)
        rod(P + "_grab%d_%d" % (d, sx), Vector((xs + 0.06 * sx, y0 + 0.08, 1.3)), Vector((xs + 0.06 * sx, y0 + 0.08, 1.95)), 0.018, "M_Chrome", G, seg=6)
    # mirror on a tubular arm
    rod(P + "_marm%d" % sx, Vector((xs, cy0 + 0.35, 2.25)), Vector((xs + 0.42 * sx, cy0 + 0.05, 2.28)), 0.02, "M_Black", G, seg=6)
    rod(P + "_marm2%d" % sx, Vector((xs, cy0 + 0.35, 1.85)), Vector((xs + 0.42 * sx, cy0 + 0.05, 2.0)), 0.02, "M_Black", G, seg=6)
    abox(P + "_mirror%d" % sx, (0.06, 0.24, 0.42), (xs + 0.45 * sx, cy0 + 0.05, 2.12), "M_Black", G, bevel=0.03)
    abox(P + "_rwin%d" % sx, (0.03, 0.34, 0.36), (xs + 0.02 * sx, cy1 - 0.3, 2.03), "M_CabGlass", G, bevel=0.0, dirt=False)
    CUTTERS.append(abox(P + "_rwcut%d" % sx, (0.5, 0.30, 0.32), (xs, cy1 - 0.3, 2.03), "M_Black", "CUT", bevel=0.0, dirt=False))
# roof: hatch, antenna bases, beacon, air intake for the cab HVAC
abox(P + "_hatch", (0.55, 0.6, 0.06), (1.2, cy0 + 1.4, 2.45), "M_TelGreen", G, bevel=0.03)
acyl(P + "_beacon", 0.07, 0.12, (-1.4, cy0 + 1.9, 2.48), "M_Amber", G, seg=12, dirt=False)
for sx in (1, -1):
    acyl(P + "_antb%d" % sx, 0.05, 0.08, (1.5 * sx, cy1 - 0.2, 2.46), "M_Black", G, seg=8)
    rod(P + "_ant%d" % sx, Vector((1.5 * sx, cy1 - 0.2, 2.48)), Vector((1.5 * sx, cy1 - 0.1, 4.4)), 0.008, "M_Black", G, seg=4)
abox(P + "_hvac", (0.42, 0.6, 0.16), (-1.3, cy1 - 0.45, 2.5), "M_TelPlain", G, bevel=0.03)
slats(P + "_hvacs", -1.48, -1.12, cy1 - 0.7, cy1 - 0.2, 2.59, 6, "M_Black", G, along='Y', t=0.02, h=0.03, tilt=0.6)

bpy.context.view_layer.update()
for cu in CUTTERS:
    m_ = cab_shell.modifiers.new("cut_" + cu.name, 'BOOLEAN')
    m_.operation = 'DIFFERENCE'
    m_.object = cu
    m_.solver = 'EXACT'
    m_.material_mode = 'TRANSFER'
    cu.hide_render = True
    cu.hide_set(True)
dg_ = bpy.context.evaluated_depsgraph_get()
me_ = bpy.data.meshes.new_from_object(cab_shell.evaluated_get(dg_), preserve_all_data_layers=True, depsgraph=dg_)
cab_shell.modifiers.clear()
old_ = cab_shell.data
cab_shell.data = me_
bpy.data.meshes.remove(old_)
for cu in CUTTERS:
    bpy.data.objects.remove(cu, do_unlink=True)
print("CAB SHELL", len(cab_shell.data.polygons), "faces", [m.name for m in cab_shell.data.materials])

# ---------------------------------------------------------------- cab interior (glTF: seats at (+-0.8, 1.45, 9.75))
CFL = 0.84                                       # inner floor
abox(P + "_cfloor", (2 * HW - 0.2, cy1 - cy0 - 0.25, 0.03), (0, (cy0 + cy1) / 2 + 0.05, CFL + 0.015), "M_Rubber", G, bevel=0.0)
abox(P + "_ctunnel", (0.5, cy1 - cy0 - 0.6, 0.28), (0, (cy0 + cy1) / 2 + 0.15, CFL + 0.14), "M_Interior", G, bevel=0.04)
abox(P + "_cheadl", (2 * HW - 0.2, cy1 - cy0 - 0.6, 0.02), (0, (cy0 + cy1) / 2 + 0.2, 2.34), "M_Interior", G, bevel=0.0)
SEAT_Y = -9.75                                   # Blender y of the rider (glTF z = 9.75)
CUSH = 1.27
for sx in (1, -1):
    xs_ = 0.8 * sx
    abox(P + "_seatc%d" % sx, (0.50, 0.48, 0.12), (xs_, SEAT_Y - 0.05, CUSH), "M_Seat", G, bevel=0.03)
    abox(P + "_seatb%d" % sx, (0.50, 0.12, 0.62), (xs_, SEAT_Y + 0.25, CUSH + 0.36), "M_Seat", G, rot=(-0.18, 0, 0), bevel=0.03)
    abox(P + "_seatbs%d" % sx, (0.36, 0.36, CUSH - CFL - 0.06), (xs_, SEAT_Y - 0.05, (CUSH + CFL) / 2), "M_Dark", G, bevel=0.01)
    abox(P + "_seathr%d" % sx, (0.30, 0.08, 0.16), (xs_, SEAT_Y + 0.33, CUSH + 0.74), "M_Seat", G, bevel=0.03)
    rod(P + "_belt%d" % sx, Vector((xs_ + 0.22 * sx, SEAT_Y + 0.28, CUSH + 0.68)), Vector((xs_ - 0.2 * sx, SEAT_Y - 0.1, CUSH + 0.1)), 0.012, "M_Black", G, seg=4)
# dashboard across the cab under the windscreen; face towards the crew (+Y)
DY0 = cy0 + 0.75                                 # dash face
abox(P + "_dash", (2 * HW - 0.2, DY0 - cy0 - 0.12, 0.40), (0, (DY0 + cy0 + 0.12) / 2, 1.62), "M_Dash", G, bevel=0.02)
abox(P + "_dashtop", (2 * HW - 0.2, DY0 - cy0 - 0.15, 0.03), (0, (DY0 + cy0 + 0.15) / 2, 1.83), "M_Interior", G, bevel=0.005)
abox(P + "_dashlow", (2 * HW - 0.2, 0.30, 0.55), (0, cy0 + 0.30, CFL + 0.28), "M_Interior", G, bevel=0.01)
abox(P + "_bwall", (2 * HW - 0.2, 0.04, 0.9), (0, cy1 - 0.08, 1.75), "M_Interior", G, bevel=0.01)
acyl(P + "_extg", 0.06, 0.40, (0.0, cy1 - 0.14, CFL + 0.35), "M_NavRed", G, seg=10)
# driver (left, +X): gauges, warning lamps, column, pedals, levers
SW = Vector((0.8, -10.28, 1.86))                 # steering wheel centre
NEEDLES = {}
for n, (dx, dz) in {"needle_speed": (0.17, 0.0), "needle_rpm": (-0.17, 0.0), "needle_fuel": (0.0, 0.11), "needle_temp": (0.0, -0.09)}.items():
    c_ = Vector((SW.x + dx, DY0 + 0.015, 1.62 + dz))          # gauge body sits proud of the dash face
    r_ = 0.065 if n in ("needle_speed", "needle_rpm") else 0.035
    gauge(P + "_" + n, c_, r_, G)
    NEEDLES[n] = (c_ + Vector((0, 0.026, 0)), r_)
abox(P + "_lamps", (0.34, 0.02, 0.04), (SW.x, DY0 + 0.0, 1.47), "M_Amber", G, bevel=0.0, dirt=False)
rod(P + "_column", SW + Vector((0, -0.04, -0.02)), SW + Vector((0, -0.40, -0.20)), 0.035, "M_Dash", G, seg=10)
for k, dx in enumerate((-0.15, 0.0, 0.15)):
    tilted_box("%s_pedal%d" % (P, k), (0, SW.y - 0.45, CFL + 0.08), (0, SW.y - 0.50, CFL + 0.28), 0.07, 0.015, "M_Steel", G)
    bpy.data.objects["%s_pedal%d" % (P, k)].location.x = SW.x + dx
for k, dy in enumerate((0.0, 0.15)):
    rod("%s_lever%d" % (P, k), Vector((0.38, SEAT_Y - 0.25 + dy, CFL + 0.28)), Vector((0.40, SEAT_Y - 0.32 + dy, CFL + 0.72)), 0.012, "M_Steel", G, seg=6)
    acyl("%s_knob%d" % (P, k), 0.03, 0.05, (0.40, SEAT_Y - 0.32 + dy, CFL + 0.74), "M_Black", G, seg=8)
# launch officer (right, -X): fire-control console with radar/map displays, launch key panel, joystick, radio
abox(P + "_fcs", (0.62, 0.26, 0.42), (-0.8, DY0 + 0.05, 1.70), "M_Dash", G, rot=(0.25, 0, 0), bevel=0.02)
for k in range(2):
    abox("%s_scr%d" % (P, k), (0.24, 0.01, 0.17), (-0.94 + 0.28 * k, DY0 + 0.19, 1.76), "M_Screen", G, rot=(0.25, 0, 0), bevel=0.0, dirt=False)
for k in range(8):
    acyl("%s_btn%d" % (P, k), 0.012, 0.02, (-1.08 + 0.08 * k, DY0 + 0.21, 1.56), "M_Amber" if k % 2 else "M_NavGreen", G, axis=1, seg=8, dirt=False)
abox(P + "_keypanel", (0.22, 0.16, 0.05), (-0.45, DY0 + 0.06, 1.84), "M_NavRed", G, rot=(0.4, 0, 0), bevel=0.01)
acyl(P + "_keylock", 0.025, 0.04, (-0.45, DY0 + 0.07, 1.88), "M_Chrome", G, seg=10)
abox(P + "_stickb", (0.16, 0.16, 0.45), (-0.38, SEAT_Y - 0.3, CFL + 0.22), "M_Dash", G, bevel=0.01)
rod(P + "_stick", Vector((-0.38, SEAT_Y - 0.3, CFL + 0.45)), Vector((-0.38, SEAT_Y - 0.33, CFL + 0.66)), 0.02, "M_Black", G, seg=8)
abox(P + "_radio", (0.46, 0.26, 0.30), (-0.2, cy1 - 0.22, 2.0), "M_Plain", G, bevel=0.02)
for k in range(3):
    acyl("%s_rknob%d" % (P, k), 0.02, 0.03, (-0.32 + 0.12 * k, cy1 - 0.36, 2.0), "M_Black", G, axis=1, seg=8)
abox(P + "_ptable", (0.5, 0.35, 0.03), (-0.8, DY0 + 0.32, 1.58), "M_Interior", G, bevel=0.005)

# ======================================================================================= ENGINE COMPARTMENT (behind the cab)
ey0, ey1 = cy1 + 0.05, cy1 + 3.0
prism_x(P + "_engine", [(ey0, 1.30), (ey0, 2.32), (ey0 + 0.2, 2.42), (ey1 - 0.2, 2.42), (ey1, 2.30), (ey1, 1.30)], 1.0, "M_TelGreen", G, bevel=0.05)
slats(P + "_louv", -0.85, 0.85, ey0 + 0.35, ey1 - 0.35, 2.445, 18, "M_Black", G, along='Y', t=0.025, h=0.05, tilt=0.55)
for sx in (1, -1):
    abox(P + "_louvf%d" % sx, (0.05, ey1 - ey0 - 0.6, 0.05), (0.88 * sx, (ey0 + ey1) / 2, 2.45), "M_TelPlain", G, bevel=0.01)
    # side air intakes with mesh
    abox(P + "_intake%d" % sx, (0.12, 1.6, 0.5), (1.02 * sx, (ey0 + ey1) / 2, 2.0), "M_TelPlain", G, bevel=0.03)
    for k in range(9):
        abox(P + "_inm%d_%d" % (sx, k), (0.03, 0.03, 0.44), (1.09 * sx, (ey0 + ey1) / 2 - 0.72 + k * 0.18, 2.0), "M_Black", G, bevel=0.0)
    # exhaust stacks with heat shields and rain caps
    ex = Vector((1.3 * sx, ey1 - 0.35, 2.3))
    acyl(P + "_exh%d" % sx, 0.09, 1.25, tuple(ex + Vector((0, 0, 0.6))), "M_Nozzle", G, seg=12)
    acyl(P + "_exhs%d" % sx, 0.13, 0.75, tuple(ex + Vector((0, 0, 0.45))), "M_Steel", G, seg=12)
    abox(P + "_exhcap%d" % sx, (0.2, 0.22, 0.03), tuple(ex + Vector((0, -0.04, 1.26))), "M_Nozzle", G, rot=(0.5, 0, 0), bevel=0.0)
# filters and fuel tanks between the axle groups
for sx in (1, -1):
    acyl(P + "_aircl%d" % sx, 0.22, 0.8, (1.32 * sx, ey0 + 0.75, 2.2), "M_TelPlain", G, axis=1, seg=16)
    abox(P + "_airclb%d" % sx, (0.3, 0.12, 0.5), (1.32 * sx, ey0 + 0.75, 1.9), "M_Dark", G, bevel=0.01)

# ======================================================================================= LAUNCHER DECK, FENDERS, LOCKERS
dy0 = ey1 + 0.05
abox(P + "_deck", (2.9, RY - dy0 - 0.2, 0.10), (0, (dy0 + RY - 0.2) / 2, 1.52), "M_TelPlain", G, bevel=0.02)
for k in range(int((RY - dy0) / 0.6)):
    abox(P + "_tread%d" % k, (2.7, 0.03, 0.015), (0, dy0 + 0.3 + k * 0.6, 1.58), "M_Steel", G, bevel=0.0)
# continuous side fenders over the wheels (the 79221 has one long fender per side), arch lips and mud flaps
for sx in (1, -1):
    for gi, (a0, a1) in enumerate([(0, 3), (4, 7)]):
        y0, y1 = AX[a0] - WR - 0.25, AX[a1] + WR + 0.25
        abox(P + "_fend%d_%d" % (gi, sx), (0.72, y1 - y0, 0.05), (1.38 * sx, (y0 + y1) / 2, WR * 2 + 0.12), "M_TelGreen", G, bevel=0.02)
        abox(P + "_fendlip%d_%d" % (gi, sx), (0.04, y1 - y0, 0.22), (1.73 * sx, (y0 + y1) / 2, WR * 2 + 0.02), "M_TelGreen", G, bevel=0.01)
        for e_, yy in enumerate((y0, y1)):
            abox(P + "_flap%d_%d_%d" % (gi, sx, e_), (0.62, 0.02, 0.62), (1.38 * sx, yy, WR * 2 - 0.25), "M_Rubber", G, bevel=0.0)
    # lockers / tool boxes above the fenders, along the deck sides
    for k, (y0, y1) in enumerate([(dy0 + 0.1, dy0 + 2.1), (dy0 + 2.3, dy0 + 4.7), (dy0 + 5.7, dy0 + 8.0), (dy0 + 8.2, dy0 + 11.0), (dy0 + 11.2, RY - 1.65)]):
        abox(P + "_lock%d_%d" % (k, sx), (0.42, y1 - y0, 0.62), (1.47 * sx, (y0 + y1) / 2, 2.05), "M_TelGreen", G, bevel=0.03)
        abox(P + "_lockl%d_%d" % (k, sx), (0.02, y1 - y0 - 0.12, 0.5), (1.685 * sx, (y0 + y1) / 2, 2.05), "M_TelGreen", G, bevel=0.01)
        for hh in (0.25, 0.75):
            abox(P + "_lockh%d_%d_%d" % (k, sx, int(hh * 100)), (0.04, 0.12, 0.04), (1.70 * sx, y0 + (y1 - y0) * hh, 2.22), "M_Chrome", G, bevel=0.005)
    # side marker lamps along the fender
    for k in range(6):
        abox(P + "_sml%d_%d" % (sx, k), (0.03, 0.08, 0.05), (1.75 * sx, FY + 3.2 + k * 3.3, 1.66), "M_Amber", G, bevel=0.0, dirt=False)
    # ladder to the deck
    for r_ in range(4):
        abox(P + "_lad%d_%d" % (sx, r_), (0.04, 0.42, 0.03), (1.76 * sx, dy0 - 0.25, 0.55 + r_ * 0.28), "M_Steel", G, bevel=0.0)
    for yy in (dy0 - 0.45, dy0 - 0.05):
        rod(P + "_ladr%d_%d" % (sx, int(yy * 10)), Vector((1.76 * sx, yy, 0.45)), Vector((1.76 * sx, yy, 1.6)), 0.018, "M_Steel", G, seg=6)
# cable reels and the hydraulic power unit just behind the engine bay
for sx in (1, -1):
    acyl(P + "_reel%d" % sx, 0.42, 0.28, (0.55 * sx, dy0 + 0.55, 1.98), "M_TelPlain", G, axis=0, seg=20)
    acyl(P + "_reelc%d" % sx, 0.30, 0.30, (0.55 * sx, dy0 + 0.55, 1.98), "M_Black", G, axis=0, seg=20)
abox(P + "_hpu", (0.9, 0.9, 0.6), (0, dy0 + 2.0, 1.88), "M_TelPlain", G, bevel=0.04)
for k in range(5):
    rod(P + "_hose%d" % k, Vector((-0.3 + k * 0.15, dy0 + 2.45, 1.75)), Vector((-0.3 + k * 0.15, dy0 + 5.0, 1.62)), 0.02, "M_Black", G, seg=5)
# rear: launch support beam, erector hinge brackets, tail lights, rear jacks housings, rear bumper with steps
abox(P + "_rbeam", (3.1, 0.5, 0.6), (0, RY - 0.4, 1.35), "M_TelPlain", G, bevel=0.04)
for sx in (1, -1):
    abox(P + "_hbr%d" % sx, (0.28, 0.9, 1.1), (0.82 * sx, PIV.y, 1.95), "M_TelPlain", G, bevel=0.03)
    acyl(P + "_hpin%d" % sx, 0.16, 0.34, (0.82 * sx, PIV.y, PIV.z), "M_Steel", G, axis=0, seg=16)
    abox(P + "_tl%d" % sx, (0.34, 0.05, 0.12), (1.35 * sx, RY - 0.13, 1.42), "M_NavRed", G, bevel=0.01, dirt=False)
    abox(P + "_tla%d" % sx, (0.12, 0.05, 0.12), (1.08 * sx, RY - 0.13, 1.42), "M_Amber", G, bevel=0.01, dirt=False)
abox(P + "_rplate", (0.52, 0.02, 0.12), (0, RY - 0.14, 1.2), "M_Stencil", G, bevel=0.0, dirt=False)

# ======================================================================================= WHEELS (16 nodes, 6 steered axles)
WHEELS = []
for a, ay in enumerate(AX):
    for sx, sn in ((1, "L"), (-1, "R")):
        g = "%s_w%d%s" % (P, a, sn)
        c = Vector((TX * sx, ay, WR))
        # tyre carcass: rounded off-road profile, wide shoulders
        prof = [(-WW / 2, 0.47), (-WW / 2 - 0.01, 0.55), (-WW / 2, WR * 0.86), (-WW * 0.40, WR * 0.97), (-WW * 0.2, WR),
                (WW * 0.2, WR), (WW * 0.40, WR * 0.97), (WW / 2, WR * 0.86), (WW / 2 + 0.01, 0.55), (WW / 2, 0.47)]
        lathe_y(g + "_tyre", prof, tuple(c), ["M_Tire"], g, seg=36, axis='X', uv_tile=0.5)
        # chevron tread lugs (extra objects, not mesh density)
        for k in range(30):
            ang = 2 * PI * k / 30
            for side in (-1, 1):
                off = side * WW * 0.22
                rr = WR + 0.025
                p = c + Vector((off, math.cos(ang) * rr, math.sin(ang) * rr))
                abox("%s_lug%d_%d" % (g, k, side), (WW * 0.36, 0.09, 0.05), tuple(p), "M_Tire", g,
                     rot=(ang + PI / 2, 0, 0.35 * side), bevel=0.01, dirt=False)
        # rim: dished disc, bead ring, 10 nuts, hub reduction cap, CTIS valve
        rx = c.x + sx * (WW / 2 - 0.05)
        lathe_y(g + "_rim", [(-0.08, 0.0), (-0.08, 0.20), (-0.02, 0.32), (0.02, 0.46), (0.06, 0.48), (0.06, 0.0)],
                (rx, ay, WR), ["M_TelPlain"], g, seg=28, axis='X')
        lathe_y(g + "_bead", [(-0.03, 0.46), (-0.03, 0.50), (0.03, 0.50), (0.03, 0.46)], (rx + 0.03 * sx, ay, WR), ["M_Steel"], g, seg=28, axis='X')
        acyl(g + "_hub", 0.15, 0.16, (rx + 0.06 * sx, ay, WR), "M_Steel", g, axis=0, seg=16)
        acyl(g + "_hubc", 0.09, 0.06, (rx + 0.15 * sx, ay, WR), "M_Dark", g, axis=0, seg=12)
        bolt_ring(g + "_nut", (rx + 0.08 * sx, ay, WR), 0.22, 10, 0.025, 0.05, "M_Steel", g, axis=0)
        rod(g + "_valve", Vector((rx + 0.1 * sx, ay, WR + 0.15)), Vector((rx + 0.1 * sx, ay + 0.1, WR + 0.3)), 0.012, "M_Black", g, seg=5)
        WHEELS.append((a, sn, g, c))

# ======================================================================================= JACKS (4: rear pair + mid pair)
JACKS = [(1.45, RY - 1.35), (-1.45, RY - 1.35), (1.5, (AX[3] + AX[4]) / 2), (-1.5, (AX[3] + AX[4]) / 2)]
for j, (jx, jy) in enumerate(JACKS):
    gj = P + "_jackfix%d" % j
    abox(gj + "_h", (0.36, 0.36, 0.95), (jx, jy, 1.55), "M_TelPlain", G, bevel=0.03)        # housing on the body
    acyl(gj + "_c", 0.13, 0.25, (jx, jy, 2.1), "M_Steel", G, seg=12)
    gm = P + "_jack%d" % j
    acyl(gm + "_leg", 0.11, 1.2, (jx, jy, 1.0 + 0.0), "M_Hydraulic", gm, seg=14)               # moving leg
    lathe_y(gm + "_pad", [(0, 0.0), (0, 0.38), (0.04, 0.40), (0.12, 0.30), (0.12, 0.10)], (jx, jy, 0.40), ["M_Steel"], gm, seg=20, axis='Z')
    for k in range(4):
        a_ = PI / 4 + k * PI / 2
        rod(gm + "_rib%d" % k, Vector((jx + math.cos(a_) * 0.11, jy + math.sin(a_) * 0.11, 0.62)),
            Vector((jx + math.cos(a_) * 0.33, jy + math.sin(a_) * 0.33, 0.44)), 0.02, "M_Steel", gm, seg=5)

# ======================================================================================= ERECTOR + TPK (transport-launch canister)
GE = P + "_erector"
L_TPK = TPK_Y1 - TPK_Y0
tc = Vector((0, (TPK_Y0 + TPK_Y1) / 2, TPK_Z))
lathe_y(P + "_tpk", [(-L_TPK / 2, TPK_R * 0.97), (-L_TPK / 2 + 0.04, TPK_R), (L_TPK / 2 - 0.04, TPK_R), (L_TPK / 2, TPK_R * 0.97)],
        tuple(tc), ["M_TpkGreen"], GE, seg=48, uv_tile=6.0)
# circumferential stiffening ribs (Yars TPK is visibly corrugated) + heavier load bands at the cradle clamps
for k in range(34):
    y = TPK_Y0 + 0.5 + k * (L_TPK - 1.0) / 33
    lathe_y(P + "_rib%d" % k, [(-0.035, TPK_R), (-0.035, TPK_R + 0.035), (0.035, TPK_R + 0.035), (0.035, TPK_R)],
            (0, y, TPK_Z), ["M_TpkRib"], GE, seg=48)
CLAMPS = [TPK_Y0 + 2.0, TPK_Y0 + 6.5, TPK_Y0 + 11.0, TPK_Y0 + 15.5, TPK_Y1 - 2.2]
for k, y in enumerate(CLAMPS):
    lathe_y(P + "_band%d" % k, [(-0.12, TPK_R), (-0.12, TPK_R + 0.07), (0.12, TPK_R + 0.07), (0.12, TPK_R)], (0, y, TPK_Z), ["M_Steel"], GE, seg=48)
    for sx in (1, -1):    # clamp lugs with bolts
        abox(P + "_clug%d_%d" % (k, sx), (0.16, 0.26, 0.22), (0.86 * sx, y, TPK_Z - 0.68), "M_Steel", GE, rot=(0, 0.75 * sx, 0), bevel=0.01)
        acyl(P + "_cbolt%d_%d" % (k, sx), 0.03, 0.34, (0.86 * sx, y, TPK_Z - 0.68), "M_Dark", GE, axis=0, seg=6)
EB0, EB1 = ey1 + 0.1, TPK_Y1 - 0.3
# longitudinal erector beams (box section) + cross members + saddles under the TPK
for sx in (1, -1):
    abox(P + "_ebeam%d" % sx, (0.26, EB1 - EB0, 0.38), (0.52 * sx, (EB0 + EB1) / 2, TPK_Z - TPK_R - 0.22), "M_TelPlain", GE, bevel=0.02)
    abox(P + "_ebeamf%d" % sx, (0.05, EB1 - EB0, 0.26), (0.52 * sx + 0.13 * sx, (EB0 + EB1) / 2, TPK_Z - TPK_R - 0.22), "M_Dark", GE, bevel=0.005)
    # ram attachment lugs (forward third of the erector)
    abox(P + "_rlug%d" % sx, (0.35, 0.5, 0.45), (1.05 * sx, PIV.y - 9.0, TPK_Z - TPK_R - 0.12), "M_Steel", GE, bevel=0.02)
    # cable trunk along the canister side with junction boxes
    rod(P + "_trunk%d" % sx, Vector((0.92 * sx, TPK_Y0 + 1.0, TPK_Z + 0.48)), Vector((0.92 * sx, TPK_Y1 - 1.0, TPK_Z + 0.48)), 0.06, "M_TelPlain", GE, seg=8)
    for k in range(4):
        abox(P + "_jbox%d_%d" % (sx, k), (0.16, 0.42, 0.32), (1.0 * sx, TPK_Y0 + 3.5 + k * 4.6, TPK_Z + 0.42), "M_TelPlain", GE, bevel=0.02)
for k in range(8):
    y = EB0 + 0.3 + k * (EB1 - EB0 - 0.6) / 7
    abox(P + "_ecross%d" % k, (1.25, 0.14, 0.2), (0, y, TPK_Z - TPK_R - 0.28), "M_TelPlain", GE, bevel=0.01)
    abox(P + "_saddle%d" % k, (1.1, 0.22, 0.14), (0, y, TPK_Z - TPK_R - 0.06), "M_Rubber", GE, bevel=0.02)
# rear base (thrust ring with gas-generator housing) and hinge arms down to the pivot
lathe_y(P + "_base", [(0, TPK_R + 0.02), (0, TPK_R + 0.12), (0.32, TPK_R + 0.12), (0.32, 0.25), (0.55, 0.25), (0.55, 0.0)],
        (0, TPK_Y1 - 0.12, TPK_Z), ["M_Steel"], GE, seg=40)
bolt_ring(P + "_bbolt", (0, TPK_Y1 - 0.13, TPK_Z), TPK_R + 0.07, 24, 0.025, 0.05, "M_Dark", GE, axis=1)
for sx in (1, -1):
    abox(P + "_harm%d" % sx, (0.24, 0.9, 1.25), (0.82 * sx, PIV.y - 0.15, (PIV.z + TPK_Z) / 2 + 0.05), "M_TelPlain", GE, bevel=0.03)
# umbilical connector plate + stencils (light grey) on the canister
abox(P + "_umb", (0.04, 0.6, 0.4), (TPK_R + 0.03, TPK_Y1 - 3.0, TPK_Z), "M_TelPlain", GE, bevel=0.01)
for k, y in enumerate((TPK_Y0 + 4.0, TPK_Y1 - 5.0)):
    for sx in (1, -1):
        abox(P + "_sten%d_%d" % (k, sx), (0.01, 1.2, 0.14), ((TPK_R + 0.005) * sx, y, TPK_Z + 0.25), "M_Stencil", GE, bevel=0.0, dirt=False)
# front cap (blown off at launch): domed cover with rim, lifting eyes and 12 latches
GC = P + "_cap"
cap_c = Vector((0, TPK_Y0, TPK_Z))
lathe_y(P + "_capd", [(-0.55, 0.0), (-0.52, 0.32), (-0.42, 0.66), (-0.25, 0.92), (-0.08, TPK_R + 0.03), (0.0, TPK_R + 0.06), (0.10, TPK_R + 0.06), (0.10, TPK_R)],
        tuple(cap_c), ["M_TpkGreen"], GC, seg=48)
lathe_y(P + "_caprim", [(0.0, TPK_R + 0.06), (0.0, TPK_R + 0.11), (0.08, TPK_R + 0.11), (0.08, TPK_R + 0.06)], tuple(cap_c), ["M_Steel"], GC, seg=48)
for k in range(12):
    a_ = 2 * PI * k / 12
    abox(P + "_latch%d" % k, (0.08, 0.16, 0.06), (math.cos(a_) * (TPK_R + 0.12), TPK_Y0 + 0.05, TPK_Z + math.sin(a_) * (TPK_R + 0.12)), "M_Dark", GC,
         rot=(0, -a_, 0), bevel=0.01)
for sx in (1, -1):
    lathe_y(P + "_eye%d" % sx, [(-0.02, 0.05), (-0.02, 0.09), (0.02, 0.09), (0.02, 0.05)], (0.35 * sx, TPK_Y0 - 0.38, TPK_Z + 0.55), ["M_Steel"], GC, seg=12, axis='X')

# ======================================================================================= RAMS (telescopic, 3 stages; animated)
RAM_B = {1: Vector((1.05, PIV.y - 16.0, 1.75)), -1: Vector((-1.05, PIV.y - 16.0, 1.75))}
RAM_D = 9.0                          # attachment distance from the hinge along the erector
A_LOC = Vector((0, -RAM_D, (TPK_Z - TPK_R - 0.12) - PIV.z))      # attachment relative to the hinge, erector frame


def attach_pt(theta, sx):
    """World (y,z) of the ram eye on the erector raised by theta (radians)."""
    y = PIV.y + A_LOC.y * math.cos(theta) - A_LOC.z * math.sin(theta)
    z = PIV.z - A_LOC.y * math.sin(theta) + A_LOC.z * math.cos(theta)
    return Vector((1.05 * sx, y, z))


L0 = (attach_pt(0, 1) - RAM_B[1]).length
RAMG = {}
for sx, sn in ((1, "L"), (-1, "R")):
    b = RAM_B[sx]
    for st, (r, lab) in enumerate([(0.16, "c"), (0.125, "s1"), (0.095, "s2")]):
        g = "%s_ram%s_%s" % (P, sn, lab)
        z0 = 0.0 if st == 0 else 0.25 * st
        acyl(g + "_tube", r, L0 - z0, (b.x, b.y, b.z + z0 + (L0 - z0) / 2), "M_Hydraulic" if st else "M_TelPlain", g, seg=16)
        if st == 0:
            acyl(g + "_gland", r + 0.03, 0.12, (b.x, b.y, b.z + L0 - 0.06), "M_Steel", g, seg=16)
            acyl(g + "_trun", 0.09, 0.42, (b.x, b.y, b.z), "M_Steel", g, axis=0, seg=12)
        else:
            acyl(g + "_gland", r + 0.025, 0.1, (b.x, b.y, b.z + L0 - 0.05), "M_Steel", g, seg=16)
        if st == 2:
            acyl(g + "_eye", 0.1, 0.3, (b.x, b.y, b.z + L0), "M_Steel", g, axis=0, seg=12)
        RAMG[(sn, st)] = g
    # chassis mount for the ram trunnion
    abox(P + "_rmount%d" % sx, (0.4, 0.5, 0.35), (b.x, b.y, b.z - 0.12), "M_TelPlain", G, bevel=0.02)

bpy.context.view_layer.update()
weather(gen_weather([(1.3, ey1 - 0.35, 3.5), (-1.3, ey1 - 0.35, 3.5)], 0.8))

# ======================================================================================= HIERARCHY
rt = root(P)
group_under(G, P + "_geo", rt)
eh = hinge(P + "_Erector", tuple(PIV), tuple(PIV + Vector((1, 0, 0))), parent=rt)
group_under(GE, P + "_erector_geo", eh)
cp = pivot(P + "_Cap", tuple(cap_c), eh)
group_under(GC, P + "_cap_geo", cp)
pivot(P + "_Missile", tuple(tc), eh)
# r3: steering wheel + instrument needles for the drivable vehicle (Real War common pose: rot local Z)
gs = P + "_sw"
rot_ = Matrix.Rotation(math.radians(-155), 4, 'X')
prof_ = [(0.018 * math.cos(2 * PI * k / 10), 0.215 + 0.018 * math.sin(2 * PI * k / 10)) for k in range(10)]
ring_ = lathe_y(gs + "_ring", prof_ + [prof_[0]], tuple(SW), ["M_Dash"], gs, seg=32, axis='Y', smooth=True, dirt=False)
ring_.data.transform(Matrix.Translation(SW) @ rot_ @ Matrix.Translation(-SW))
acyl(gs + "_hub", 0.06, 0.06, tuple(SW), "M_Dash", gs, rot=(math.radians(-155) + PI / 2, 0, 0), seg=14)
for k in range(3):
    a_ = math.radians(90 + 120 * k)
    d_ = rot_ @ Vector((math.cos(a_) * 0.21, 0, math.sin(a_) * 0.21))
    rod("%s_spoke%d" % (gs, k), SW, SW + d_, 0.014, "M_Dash", gs, seg=6)
e_ = empty("steering_wheel", tuple(SW), 0.1, 'PLAIN_AXES', rot=(math.radians(-155), 0, 0))
attach(e_, rt)
group_under(gs, "steering_wheel_geo", e_)
for n, (c_, r_) in NEEDLES.items():
    gn = P + "_" + n + "_g"
    abox(gn + "_n", (0.006, 0.004, r_ * 0.85), tuple(c_ + Vector((0, 0, r_ * 0.38))), "M_Needle", gn, bevel=0.0, dirt=False)
    acyl(gn + "_c", 0.008, 0.01, tuple(c_), "M_Dash", gn, axis=1, seg=8, dirt=False)
    nn = pivot(n, tuple(c_), rt)
    group_under(gn, n + "_geo", nn)
for a, sn, g, c in WHEELS:
    par = rt
    if a in STEER:
        par = pivot("%s_St%d%s" % (P, a, sn), tuple(c), rt)
    w = pivot("%s_W%d%s" % (P, a, sn), tuple(c), par)
    group_under(g, g + "_geo", w)
JN = []
for j, (jx, jy) in enumerate(JACKS):
    jn = pivot("%s_Jack%d" % (P, j), (jx, jy, 1.0), rt)
    group_under(P + "_jack%d" % j, P + "_jack%d_geo" % j, jn)
    JN.append(jn)
RAMN = {}
for sx, sn in ((1, "L"), (-1, "R")):
    b = RAM_B[sx]
    rn = pivot("%s_Ram%s" % (P, sn), tuple(b), rt)
    group_under(RAMG[(sn, 0)], RAMG[(sn, 0)] + "_geo", rn)
    s1 = pivot("%s_Ram%s_S1" % (P, sn), tuple(b), rn)
    group_under(RAMG[(sn, 1)], RAMG[(sn, 1)] + "_geo", s1)
    s2 = pivot("%s_Ram%s_S2" % (P, sn), tuple(b), s1)
    group_under(RAMG[(sn, 2)], RAMG[(sn, 2)] + "_geo", s2)
    RAMN[sn] = (rn, s1, s2, sx)
    d0 = attach_pt(0.0, sx) - b
    rn.rotation_euler.x = math.atan2(-d0.y, d0.z)
bpy.context.view_layer.update()

# ======================================================================================= CLIPS
# erector: eased 0 -> 90 deg over 96 frames, keyed every 4 frames (LINEAR) so the rams can follow it exactly
def ease(f):
    return f * f * (3 - 2 * f)


frames = list(range(0, 97, 4))
aclip("yarstel_erect", eh, "rotation_euler", [(fr, -math.radians(90) * ease(fr / 96)) for fr in frames], index=0, interp='LINEAR')
for sn, (rn, s1, s2, sx) in RAMN.items():
    b = RAM_B[sx]
    rk, k1, k2 = [], [], []
    for fr in frames:
        th = math.radians(90) * ease(fr / 96)
        d = attach_pt(th, sx) - b
        phi = math.atan2(-d.y, d.z)          # rotate local +Z onto the ram axis (rotation about X)
        e = max(0.0, d.length - L0)
        rk.append((fr, phi))
        k1.append((fr, e / 2))
        k2.append((fr, e / 2))
    aclip("yarstel_erect", rn, "rotation_euler", rk, index=0, interp='LINEAR')
    aclip("yarstel_erect", s1, "location", [(fr, s1.location.z + v) for fr, v in k1], index=2, interp='LINEAR')
    aclip("yarstel_erect", s2, "location", [(fr, s2.location.z + v) for fr, v in k2], index=2, interp='LINEAR')
for jn in JN:
    aclip("yarstel_jacks", jn, "location", [(0, jn.location.z), (40, jn.location.z - 0.42)], index=2, interp='BEZIER')
finish_nla()
bpy.context.view_layer.update()
print("RAM L0", round(L0, 3), "AXLES", [round(a, 2) for a in AX])
print("YARSTEL", stats())
print("GLB", export_glb("/home/user/models/yarstel.glb"))
print("BLEND", save_blend("/home/user/models/yarstel.blend", "YarsTEL"))
