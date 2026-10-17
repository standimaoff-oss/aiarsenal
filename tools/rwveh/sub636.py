# Submarine pr. 636.3 "Varshavyanka" (Kilo): 73.8 m, hull dia 9.9, blunt bow with 6 x 533 mm tubes, free-flooding
# casing with limber holes, sail with bridge, retractable bow planes, cruciform tail, 7-blade propeller.
# CraftRenderer nodes: prop (about glTF Z), rudder (about Y), planes_bow / planes_stern (about X), periscope / snorkel
# (translated up), radar (spins).  Kind: axisY 4.95; bridge seats y 14.0 at z 9.6 / 8.5; tube muzzles z ~36.
exec(open("/home/user/tools/rwveh/craft_common.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
craft_mats()
amat("M_SubHull", None, 0.75, 0.05, factor=(0.055, 0.058, 0.06, 1))
amat("M_SubDeck", None, 0.8, 0.05, factor=(0.08, 0.085, 0.085, 1))
amat("M_SubRed", None, 0.7, 0.0, factor=(0.30, 0.06, 0.05, 1))
P = "SB"
G, GP, GRU, GPB, GPS, GPE, GSN, GRD = (P + s for s in ("_body", "_prop", "_rud", "_pb", "_ps", "_peri", "_snork", "_rad"))
M, MDK = "M_SubHull", "M_SubDeck"
AX = 4.95                 # hull axis height (Blender z)
HL = 36.9                 # half length
SAIL0, SAIL1, SAILZ = -12.6, -3.2, 14.6

# ------------------------------------------------------------------------------------------------ pressure / outer hull
hp = [(-36.9, 0.0), (-36.75, 1.2), (-36.4, 2.2), (-35.6, 3.3), (-34.0, 4.25), (-31.5, 4.8), (-28.0, 4.95), (14.0, 4.95), (19.0, 4.75),
      (24.0, 4.2), (28.5, 3.3), (32.0, 2.3), (34.8, 1.4), (36.0, 0.9), (36.3, 0.0)]
def hull_mat(y, ang, z):
    return 1 if z < 2.6 else 0
hull = lathe_y(P + "_hull", hp, (0, 0, AX), [M], G, seg=40)
# red antifouling lower third (separate shell just outside)
red = [(y, r * 1.004 + 0.01) for (y, r) in hp if r > 0.3]
loft(P + "_red", [y for y, r in red], lambda y: (AX, catmull([(a, b * 1.004 + 0.01) for a, b in hp], y), catmull([(a, b * 1.004 + 0.01) for a, b in hp], y),
                                                  catmull([(a, b * 1.004 + 0.01) for a, b in hp], y), 2.0),
     ["M_SubRed"], G, seg=40, angle_range=(232, 308), cap0=False, cap1=False)
# torpedo tube shutters: muzzles K(+-1.1, 5.6, 36.6), K(+-1.6, 4.3, 36.3), K(+-1.1, 3.0, 35.9)
def hull_r(y):
    return catmull(hp, y)
for k, (x, zz, kz) in enumerate(((1.1, 5.6, 36.6), (-1.1, 5.6, 36.6), (1.6, 4.3, 36.3), (-1.6, 4.3, 36.3), (1.1, 3.0, 35.9), (-1.1, 3.0, 35.9))):
    off = math.hypot(x, zz - AX)
    yy = -kz
    n = Vector((x, -1.4, zz - AX)).normalized()
    acyl("%s_tt%d" % (P, k), 0.36, 0.06, (x, yy, zz), "M_Black", G, rot=(math.atan2(n.z, -n.y) + PI / 2, 0, math.atan2(n.x, -n.y)), seg=16, dirt=False)
# sonar window (chin) + casing (free-flooding deck)
lathe_y(P + "_sonar", [(-1.4, 0.0), (-1.4, 0.4), (-0.8, 1.0), (0.8, 1.0), (1.4, 0.4), (1.4, 0.0)], (0, -31.0, 1.0), [MDK], G, seg=20)
Y0C, Y1C = -31.5, 24.0
fuselage(P + "_casing", Y0C, Y1C - Y0C, [(0, 9.4, 0.6, 0.3, 0.6, 3), (0.05, 9.75, 1.5, 0.5, 0.8, 4), (0.5, 9.9, 1.6, 0.5, 0.8, 5),
                                         (0.93, 9.8, 1.5, 0.45, 0.8, 4), (1.0, 9.5, 0.5, 0.3, 0.6, 3)], [MDK], G, n=18, seg=24)
for sx in (1, -1):
    for j in range(34):
        y = -27.5 + j * 1.5
        if SAIL0 - 1 < y < SAIL1 + 1:
            continue
        abox("%s_lh%d_%d" % (P, sx, j), (0.04, 0.6, 0.16), (sx * 1.6, y, 9.6), "M_Black", G, bevel=0.0, dirt=False)
# deck hatches, capstans, safety rail stanchion sockets
for j, y in enumerate((-22.0, -16.0, 6.0, 15.0)):
    acyl("%s_hatch%d" % (P, j), 0.45, 0.08, (0, y, 10.42), MDK, G, seg=16)
for j, y in enumerate((-27.0, 20.0)):
    acyl("%s_cap%d" % (P, j), 0.25, 0.3, (0.8, y, 10.5), "M_Steel", G, seg=10)
# ------------------------------------------------------------------------------------------------ sail with bridge
sail_prof = [(0.0, 0.0), (0.04, 0.55), (0.12, 0.95), (0.3, 1.15), (0.7, 1.15), (0.9, 0.85), (1.0, 0.35)]
def sail_hw(y):
    t = (y - SAIL0) / (SAIL1 - SAIL0)
    return max(0.05, catmull(sail_prof, t))
rings = []
for k in range(21):
    y = SAIL0 + (SAIL1 - SAIL0) * k / 20
    hw = sail_hw(y)
    ring = []
    for z in (9.8, SAILZ):
        pass
    pts = [(hw * 1.08, 9.8), (hw, SAILZ - 0.3), (hw * 0.9, SAILZ), (-hw * 0.9, SAILZ), (-hw, SAILZ - 0.3), (-hw * 1.08, 9.8)]
    rings.append([Vector((x, y, z)) for x, z in pts])
sail = loft_rings(P + "_sail", rings, [M], G, cap0=True, cap1=True, smooth=False)
# bridge: windscreen lip, cockpit well, hand rails
abox(P + "_well", (1.6, 2.4, 0.06), (0, -9.1, SAILZ - 0.55), MDK, G, bevel=0.0)
for sx in (1, -1):
    abox("%s_lip%d" % (P, sx), (0.05, 2.6, 0.4), (sx * 1.0, -9.1, SAILZ + 0.2), M, G, rot=(0, math.radians(sx * 12), 0), bevel=0.0)
abox(P + "_lipf", (1.9, 0.05, 0.45), (0, -10.5, SAILZ + 0.2), M, G, rot=(math.radians(-15), 0, 0), bevel=0.0)
abox(P + "_wscr", (1.7, 0.03, 0.3), (0, -10.6, SAILZ + 0.55), "M_Glass", G, rot=(math.radians(-25), 0, 0), bevel=0.0, dirt=False)
for sx in (1, -1):
    abox("%s_step%d" % (P, sx), (0.06, 0.4, 0.06), (sx * 1.16, -6.0, 11.0), "M_Steel", G, bevel=0.0)
    lamp("%s_nav%d" % (P, sx), (sx * 1.05, -10.0, SAILZ - 0.5), "M_NavRed" if sx > 0 else "M_NavGreen", G, r=0.08)
abox(P + "_bridgec", (0.5, 0.3, 0.4), (0, -10.2, SAILZ - 0.35), "M_Dash", G, bevel=0.02)
# masts (fixed stubs) + retractable periscope / snorkel / radar
for j, (x, y) in enumerate(((0.35, -7.4), (-0.35, -6.6), (0.0, -5.0))):
    acyl("%s_mst%d" % (P, j), 0.15, 0.3, (x, y, SAILZ + 0.15), "M_Steel", G, seg=10)
acyl(P + "_peri", 0.12, 1.4, (0.35, -7.4, SAILZ - 0.2), "M_Steel", GPE, seg=12)
acyl(P + "_perih", 0.16, 0.35, (0.35, -7.4, SAILZ + 0.55), MDK, GPE, seg=12)
abox(P + "_periw", (0.12, 0.04, 0.12), (0.35, -7.57, SAILZ + 0.6), "M_Lens", GPE, bevel=0.0, dirt=False)
acyl(P + "_snk", 0.22, 1.6, (0.0, -5.0, SAILZ - 0.3), "M_Steel", GSN, seg=12)
abox(P + "_snkh", (0.5, 0.7, 0.35), (0.0, -5.1, SAILZ + 0.65), MDK, GSN, bevel=0.05)
acyl(P + "_radm", 0.08, 0.9, (-0.35, -6.6, SAILZ + 0.45), "M_Steel", G, seg=8)
abox(P + "_rada", (0.9, 0.18, 0.2), (-0.35, -6.6, SAILZ + 0.98), "M_SubDeck", GRD, bevel=0.03)
# ------------------------------------------------------------------------------------------------ bow planes (retractable, on the casing)
PB = Vector((0, -26.5, 8.4))
for sx in (1, -1):
    panel("%s_bplane%d" % (P, sx), (sx * 4.2, -26.0, PB.z), (sx * 6.6, -25.7, PB.z), 2.2, 1.6, (0, 0, 1), [M], GPB, t_root=0.12, t_tip=0.1, n=2)
    acyl("%s_bpst%d" % (P, sx), 0.15, 1.3, (sx * 3.85, -25.0, PB.z), "M_Steel", G, axis=0, seg=10)
# ------------------------------------------------------------------------------------------------ cruciform tail, rudders, stern planes, prop
TC = Vector((0, 31.0, AX))
for sx in (1, -1):
    panel("%s_hfin%d" % (P, sx), (sx * 2.2, 29.2, AX), (sx * 5.4, 30.4, AX), 3.4, 2.0, (0, 0, 1), [M], G, t_root=0.12, t_tip=0.1, n=2)
    panel("%s_splane%d" % (P, sx), (sx * 2.1, 32.6, AX), (sx * 5.3, 32.4, AX), 1.5, 1.2, (0, 0, 1), [M], GPS, t_root=0.1, t_tip=0.08, n=2)
for sz in (1, -1):
    panel("%s_vfin%d" % (P, sz), (0, 29.2, AX + sz * 2.2), (0, 30.4, AX + sz * 5.0), 3.4, 2.0, (1, 0, 0), [M], G, t_root=0.12, t_tip=0.1, n=2)
    panel("%s_rud%d" % (P, sz), (0, 32.6, AX + sz * 2.1), (0, 32.4, AX + sz * 4.9), 1.5, 1.2, (1, 0, 0), [M], GRU, t_root=0.1, t_tip=0.08, n=2)
PC = Vector((0, 36.1, AX))
lathe_y(P + "_phub", [(-0.6, 0.0), (-0.6, 0.55), (0.3, 0.5), (0.7, 0.2), (0.8, 0.0)], tuple(PC), ["M_Brass"], GP, seg=16)
prop_blades(P + "_pb", tuple(PC), 1.85, 7, ["M_Brass"], GP, axis=(0, 1, 0), chord=0.75, r_hub=0.45, pitch_root=45, pitch_tip=22, t=0.08)
# draft marks + hull number decal on the sail
for sx in (1, -1):
    decal_on("%s_flag%d" % (P, sx), sail, (sx * 1.16, -8.0, 13.2), (sx, 0, 0), (0, 0, 1), 1.2, "flag_ru", G)

auto_attach([G], bracket_groups=())
weather(gen_weather([], 0.5))
rt = root("root")
group_under(G, "sub636_body", rt)
pp = pivot("prop", tuple(PC), rt)
group_under(GP, "prop_geo", pp)
pr = pivot("rudder", (0, 32.6, AX), rt)
group_under(GRU, "rudder_geo", pr)
pbw = pivot("planes_bow", (0, -25.0, PB.z), rt)
group_under(GPB, "planes_bow_geo", pbw)
pst = pivot("planes_stern", (0, 32.6, AX), rt)
group_under(GPS, "planes_stern_geo", pst)
pe = pivot("periscope", (0.35, -7.4, SAILZ), rt)
group_under(GPE, "periscope_geo", pe)
ps = pivot("snorkel", (0.0, -5.0, SAILZ), rt)
group_under(GSN, "snorkel_geo", ps)
prd = pivot("radar", (-0.35, -6.6, SAILZ + 0.98), rt)
group_under(GRD, "radar_geo", prd)
rw_export("/home/user/.cache/rwveh/raw/sub636.glb", "/home/user/models/sub636.blend", "SUB636")
