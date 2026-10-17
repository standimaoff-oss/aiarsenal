# Schlachtschiff "Bismarck" as of 24 May 1941 (Unternehmen Rheinuebung): camouflage stripes and deck swastikas painted
# over, turret roofs dark grey. Real dimensions: LOA 251.0 m, LWL 241.6, beam 36.0, draft 9.3 m.
# Armament: 4 x 2 38 cm SK C/34 (Anton, Bruno, Caesar, Dora), 6 x 2 15 cm SK C/28, 8 x 2 10.5 cm SK C/33,
# 8 x 2 3.7 cm SK C/30, 10 x 2 cm Flak 30 + 2 x 2 cm Flakvierling 38, catapult + Arado Ar 196 A-3.
# References: kbismarck.com drawing 24 May 1941 (measured on a 1 m grid), the-blueprints 1940-41 profile / plan.
# Nodes (CraftRenderer + BattleshipEntity): t{A..D}_yaw > t{X}_pitch > t{X}_g0/_g1 (recoil), s0..s5_yaw > _pitch,
# h0..h7 (10.5), f0..f7 (3.7), l0..l9 (2 cm), q0..q1 (Vierling) _yaw > _pitch, rf_fore/rf_aft/rf_fwd (rangefinders),
# arado_cat, prop / prop_L / prop_R, rudder_L / rudder_R.
exec(open("/home/user/tools/rwveh/bis_lib.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
ar_mats()
amat("M_SHull", "T_ShipHull", 0.68, 0.05)
amat("M_STeak", "T_Teak", 0.85, 0.0)
amat("M_SSteel", "T_SteelDeck", 0.7, 0.15)
amat("M_SGrey", "T_ShipGrey", 0.66, 0.08)
amat("M_SDark", None, 0.65, 0.1, factor=(0.13, 0.14, 0.15, 1))
amat("M_SBlack", None, 0.8, 0.1, factor=(0.025, 0.025, 0.025, 1))
amat("M_SRed", None, 0.7, 0.0, factor=(0.26, 0.07, 0.04, 1))
amat("M_Bronze", None, 0.35, 1.0, factor=(0.62, 0.45, 0.22, 1))
amat("M_Bag", None, 0.9, 0.0, factor=(0.78, 0.76, 0.70, 1))
amat("M_SGlass", None, 0.05, 0.4, factor=(0.08, 0.10, 0.12, 1))
amat("M_SLens", None, 0.1, 0.3, factor=(0.6, 0.65, 0.7, 1), emissive=(0.05, 0.05, 0.06, 1))
amat("M_BoatHull", None, 0.6, 0.05, factor=(0.55, 0.56, 0.58, 1))
amat("M_Chain", None, 0.6, 0.7, factor=(0.10, 0.09, 0.08, 1))
G = "BIS_body"


def dz(s, x):
    """Deck surface height (sheer + 0.25 m camber) at (s, x)."""
    d = max(0.3, hb(s))
    return zd(s) + 0.25 * (1 - min(1.0, (x / d) ** 2)) - 0.02
GR = "BIS_rig"           # rigging / antenna wires (excluded from the walk boxes)
GREY, DARK = "M_SGrey", "M_SDark"

# ================================================================================================ hull + deck
build_hull("BIS_hull", G, ["M_SHull"])
build_deck("BIS_deck", G, ["M_STeak", "M_SSteel"])
# bilge keels along the parallel body
for sx in (1, -1):
    for s0 in range(78, 172, 12):
        s1 = s0 + 12
        def bk(s):
            half = section(s)
            return Vector((sx * half[5][0], 125.5 - s, half[5][1]))
        a, b = bk(s0), bk(s1)
        rod("BIS_bilge%d_%d" % (sx, s0), a, b, 0.32, "M_SRed", G, seg=4)

# ================================================================================================ superstructure
DH1 = [(77.5, 0), (77.5, 9.0), (80, 10.5), (96, 12.5)] + arc_pts(103, 14.7, 5.6, -157, -23, 8) + [(116.5, 12.5)] + \
      arc_pts(132.5, 14.7, 5.6, -157, -23, 8) + [(146, 12.5), (149, 13.5), (158, 13.5), (162, 9.0), (166.5, 4.5), (167.5, 0)]
prism("BIS_dh1", mirror_sx(DH1), 15.0, 19.6, GREY, G, top_mat="M_STeak", uvscale=0.25)
# sponsons for the 3.7 cm beside the catapult
for sx in (1, -1):
    prism("BIS_spon%d" % sx, [(118, sx * 12.4), (122, sx * 12.4), (121.6, sx * 16.3), (118.4, sx * 16.3)], 19.25, 19.6, GREY, G, top_mat="M_STeak")
    for s in (118.8, 121.2):
        rod("BIS_sponb%d_%d" % (sx, int(s)), P(s, sx * 12.4, 17.2), P(s, sx * 15.9, 19.28), 0.12, GREY, G, seg=4)
# second level
prism("BIS_dh2a", rounded_rect(80, 96, 7.5, 2.0), 19.5, 22.4, GREY, G, top_mat="M_STeak")
prism("BIS_dh2b", rounded_rect(96, 112.5, 7.5, 1.0), 19.5, 22.5, GREY, G, top_mat="M_STeak")
prism("BIS_hangar", rounded_rect(100, 108.5, 4.0, 0.5), 22.4, 26.8, GREY, G, top_mat="M_SSteel")
prism("BIS_dh2c", rounded_rect(118.5, 139.5, 8.4, 1.5), 19.5, 22.5, GREY, G, top_mat="M_SSteel")
prism("BIS_dh2d", mirror_sx([(146, 0), (146, 5.0), (156, 5.0), (160.5, 3.2), (161.5, 0)]), 19.5, 22.5, GREY, G, top_mat="M_SSteel")
# aft command post + aft director tower
prism("BIS_aftcp", rounded_rect(84.5, 92, 4.0, 1.0), 22.3, 24.7, GREY, G, top_mat="M_SSteel")
prism("BIS_aftcp2", rounded_rect(85, 91, 3.2, 0.8), 24.6, 26.3, GREY, G, top_mat="M_SDark")
acyl("BIS_aftdir_ped", 1.3, 5.0, tuple(P(79.5, 0, 22.1)), GREY, G, seg=16)
lathe_y("BIS_aftdir", [(0.0, 0.0), (0.0, 1.7), (0.6, 1.9), (1.4, 1.9), (1.8, 1.4), (1.85, 0.0)], tuple(P(79.5, 0, 24.55)), [GREY], G, seg=18, axis='Z')
# funnel
prism("BIS_funbase", oval(125, 0, 6.5, 4.2, 28), 22.4, 27.0, GREY, G)
prism("BIS_funnel", oval(125, 0, 5.5, 3.3, 28), 26.9, 34.0, GREY, G)
cap = []
for k in range(28):
    a = 2 * PI * k / 28
    s, x = 125 + 5.6 * math.cos(a), 3.4 * math.sin(a)
    cap += [tuple(P(s, x, 33.9)), tuple(P(s, x * 0.98, 34.5))]
chull("BIS_funcap", cap, GREY, G, bevel=0.03)
prism("BIS_fungrille", oval(125, 0, 4.9, 2.7, 24), 34.42, 34.62, "M_SBlack", G)
for sx in (1, -1):
    rod("BIS_steam%d" % sx, P(130.2, sx * 1.2, 27), P(130.6, sx * 1.2, 35.6), 0.18, GREY, G, seg=8)
prism("BIS_funplat", oval(125, 0, 7.6, 7.0, 32), 29.25, 29.55, GREY, G, top_mat="M_SSteel")
for k in range(10):
    a = 2 * PI * k / 10
    rod("BIS_funbr%d" % k, P(125 + 5.4 * math.cos(a), 3.4 * math.sin(a), 27.4), P(125 + 7.2 * math.cos(a), 6.6 * math.sin(a), 29.26), 0.1, GREY, G, seg=4)
# hangar roof crane girders (X) and mainmast
acc = Acc("BIS_mm", G, [GREY])
acc.rod(P(106.3, 0, 26.7), P(106.3, 0, 46.8), 0.55, seg=12, r2=0.42)
acc.rod(P(106.3, 0, 46.8), P(106.3, 0, 58.0), 0.25, seg=8, r2=0.12)
acc.box((2.6, 2.6, 1.8), P(106.3, 0, 40.9))
acc.box((3.6, 3.4, 0.2), P(106.3, 0, 40.0))
for sx in (1, -1):
    acc.rod(P(106.3, sx * 0.3, 46.8), P(106.3, sx * 6.0, 46.8), 0.12, seg=6)
acc.rod(P(106.3, 0, 51.5), P(99.4, 0, 57.0), 0.1, seg=6)
for sx in (1, -1):
    acc.rod(P(103.0, sx * 2.6, 26.8), P(106.3, sx * 0.4, 36.0), 0.25, seg=6)
for d in ((100.5, 3.0, 108.0, -3.0), (100.5, -3.0, 108.0, 3.0)):
    acc.rod(P(d[0], d[1], 27.1), P(d[2], d[3], 27.1), 0.18, seg=4)
acc.done()
# foremast
acc = Acc("BIS_fm", G, [GREY])
acc.rod(P(133.3, 0, 22.4), P(133.3, 0, 46.0), 0.45, seg=10, r2=0.3)
acc.rod(P(133.3, 0, 46.0), P(133.3, 0, 52.5), 0.18, seg=6, r2=0.1)
for sx in (1, -1):
    acc.rod(P(133.3, sx * 0.3, 30.8), P(133.3, sx * 8.0, 30.8), 0.12, seg=6)
    acc.rod(P(133.3, sx * 0.25, 45.0), P(133.3, sx * 4.0, 45.0), 0.08, seg=6)
acc.done()

# forward tower
prism("BIS_t1", rounded_rect(138.5, 146, 4.5, 1.0), 19.5, 30.1, GREY, G)
prism("BIS_b1", rounded_rect(136.5, 148.5, 7.2, 1.5), 30.0, 30.4, GREY, G, top_mat="M_STeak")
prism("BIS_bridge", rounded_rect(138.0, 147.0, 5.0, 1.2), 30.3, 32.6, GREY, G, top_mat="M_SSteel")
prism("BIS_bwin", rounded_rect(138.1, 147.08, 5.04, 1.2), 31.55, 32.25, "M_SGlass", G, dirt=False)
prism("BIS_t2", rounded_rect(139, 145, 3.5, 1.0), 32.5, 37.3, GREY, G)
prism("BIS_b2", rounded_rect(137.5, 147.5, 5.0, 1.2), 37.2, 37.55, GREY, G, top_mat="M_SSteel")
prism("BIS_t3", rounded_rect(139.5, 144.5, 3.0, 0.8), 37.5, 40.25, GREY, G)
prism("BIS_b3", rounded_rect(133.5, 145, 5.5, 1.2), 40.2, 40.55, GREY, G, top_mat="M_SSteel")
prism("BIS_t3win", rounded_rect(139.55, 144.53, 3.03, 0.8), 38.6, 39.3, "M_SGlass", G, dirt=False)
prism("BIS_pfwd", rounded_rect(145.5, 148.5, 4.0, 0.6), 19.5, 26.6, GREY, G, top_mat="M_STeak")
acyl("BIS_ct", 3.4, 4.8, tuple(P(152.5, 0, 24.8)), GREY, G, seg=24, smooth=False)
acc = Acc("BIS_ctslit", G, ["M_SBlack"])
for k in range(9):
    a = math.radians(-80 + k * 20)
    acc.box((0.9, 0.12, 0.18), P(152.5 + 3.42 * math.cos(a), 3.42 * math.sin(a), 26.4), rot=(0, 0, -a))
acc.done(dirt=False)
# forward / aft AA directors (SL-8 stabilised domes)
for nm, s, x, z0, zc, r in (("fd", 141.8, 7.6, 19.5, 27.3, 1.75), ("ad", 99.5, 4.8, 22.4, 26.0, 1.6)):
    for sx in (1, -1):
        acyl("BIS_%sp%d" % (nm, sx), 0.75, zc - r * 0.6 - z0, tuple(P(s, sx * x, (z0 + zc - r * 0.6) / 2)), GREY, G, seg=12)
        b = bm_sphere(r, P(s, sx * x, zc), 20, 10, (1, 1, 0.92))
        aobj("BIS_%sdome%d" % (nm, sx), b, [DARK], G, smooth=True)
        abox("BIS_%sslot%d" % (nm, sx), (0.6, 0.1, 0.35), tuple(P(s + r * 0.98, sx * x, zc + 0.3)), "M_SBlack", G, bevel=0.0, dirt=False)

# catapult (athwartships, double-ended)
acc = Acc("BIS_cat", G, [GREY, "M_SDark"])
for s in (115.0, 116.0):
    acc.box((32.0, 0.3, 0.55), P(s, 0, 20.35))
    acc.box((32.0, 0.12, 0.1), P(s, 0, 20.68), mi=1)
for k in range(32):
    x = -15.5 + k
    acc.rod(P(115.0, x, 20.1), P(116.0, x + 0.9, 20.6), 0.05, seg=4)
for x in (-15.0, -9.0, 9.0, 15.0):
    acc.rod(P(115.5, x, dz(115.5, x) if abs(x) > 12.4 else 19.5), P(115.5, x, 20.1), 0.18, seg=6)
acc.box((2.2, 1.6, 0.5), P(115.5, 0, 20.9))
acc.done()

# searchlights (150 cm) on the funnel platform, the tower and the mainmast
def searchlight(tag, s, x, z, yaw=0.0):
    acyl("BIS_slp_" + tag, 0.35, 0.7, tuple(P(s, x, z + 0.35)), GREY, G, seg=8)
    lathe_y("BIS_sl_" + tag, [(-0.5, 0.0), (-0.5, 0.82), (-0.35, 0.86), (0.6, 0.86), (0.75, 0.5), (0.8, 0.0)], tuple(P(s, x, z + 1.45)),
            [GREY], G, seg=16)
    acyl("BIS_sll_" + tag, 0.78, 0.03, tuple(P(s + 0.52, x, z + 1.45)), "M_SLens", G, axis=1, seg=16, dirt=False)
for sx in (1, -1):
    searchlight("fun%d" % sx, 127.8, sx * 4.6, 29.55)
    searchlight("funa%d" % sx, 120.3, sx * 4.6, 29.55)
    searchlight("tw%d" % sx, 139.0, sx * 4.4, 37.55)
searchlight("mm", 107.5, 0, 40.1)

# ================================================================================================ main deck fittings
acc = Acc("BIS_fit", G, [GREY, "M_SDark", "M_SBlack", "M_Chain", "M_SRed"])
# breakwater ahead of Anton
for sx in (1, -1):
    a, b = (207.0, sx * 11.0), (213.0, 0.0)
    L = math.hypot(b[0] - a[0], b[1] - a[1])
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    acc.box((0.12, L, 1.3), P(mid[0], mid[1], dz(mid[0], mid[1]) + 0.6), rot=(0, 0, ang))
    for t in (0.15, 0.4, 0.65, 0.9):
        p = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        acc.rod(P(p[0] - 0.9, p[1], dz(p[0] - 0.9, p[1])), P(p[0], p[1], dz(p[0], p[1]) + 1.2), 0.06, seg=4)
# windlasses / capstans, chains, hawse pipes, anchors
for sx in (1, -1):
    for s, x in ((216.0, 3.2), (16.0, 1.6)):
        acc.rod(P(s, sx * x, dz(s, x)), P(s, sx * x, dz(s, x) + 1.0), 0.75, seg=14)
        acc.rod(P(s, sx * x, dz(s, x) + 1.0), P(s, sx * x, dz(s, x) + 1.3), 0.55, seg=14, mi=1)
    # chain lying on the deck: windlass -> deck hawse (links as alternating thin boxes)
    a, b = Vector((216.8, sx * 3.2)), Vector((238.4, sx * 2.8))
    n = int((b - a).length / 0.45)
    for k in range(n):
        p = a + (b - a) * (k / n)
        acc.box((0.42, 0.14, 0.12) if k % 2 else (0.42, 0.06, 0.2), P(p.x, p.y, dz(p.x, p.y) + 0.08), rot=(0, 0, math.atan2(b.y - a.y, b.x - a.x)), mi=3)
    acc.rod(P(238.4, sx * 2.8, dz(238.4, 2.8) + 0.1), P(238.4, sx * 2.8, dz(238.4, 2.8) - 0.05), 0.5, seg=12, mi=2)
    # hawse at the hull side and the stockless anchor stowed in it
    s = 238.8
    hx = hb(s) - 0.15
    acc.rod(P(s, sx * (hx - 0.3), 16.4), P(s - 0.4, sx * (hx + 0.12), 16.4), 0.55, seg=10, mi=2)
    acc.box((0.5, 0.35, 3.4), P(s - 0.3, sx * (hx + 0.2), 15.0), rot=(0, sx * 0.25, 0), mi=1)
    acc.box((1.8, 0.5, 0.5), P(s - 0.3, sx * (hx + 0.35), 13.4), rot=(0, sx * 0.25, 0), mi=1)
# bollards (pairs) and fairleads along the deck edges
for s in list(range(6, 70, 12)) + list(range(178, 246, 12)):
    for sx in (1, -1):
        x = sx * (hb(s) - 1.1)
        for ds in (-0.45, 0.45):
            acc.rod(P(s + ds, x, dz(s, x)), P(s + ds, x, dz(s, x) + 0.75), 0.2, seg=8)
        xf = sx * (hb(s + 1.5) - 0.3)
        acc.box((0.7, 0.35, 0.3), P(s + 1.5, xf, dz(s + 1.5, xf) + 0.15), mi=0)
# ventilator mushrooms and hatches on the fore- and quarterdeck
for s, x in ((30, 4.5), (36, 7.0), (42, 3.0), (58, 6.5), (182, 7.0), (186, 3.0), (205, 6.0), (220, 1.2), (226, 4.8)):
    for sx in (1, -1):
        acc.rod(P(s, sx * x, dz(s, x)), P(s, sx * x, dz(s, x) + 0.9), 0.22, seg=8)
        acc.rod(P(s, sx * x, dz(s, x) + 0.9), P(s, sx * x, dz(s, x) + 1.15), 0.45, seg=10)
for s, x in ((24, 0), (40, 5.0), (60, 9.5), (172, 9.0), (185, 0), (219, 6.0)):
    for sx in ((1, -1) if x else (1,)):
        acc.box((1.6, 1.4, 0.45), P(s, sx * x, dz(s, x) + 0.2), mi=1)
# ensign staff (stern) and jack staff (stem)
acc.rod(P(0.9, 0, 16.3), P(0.9, 0, 23.0), 0.08, seg=6)
acc.rod(P(250.2, 0, 18.4), P(250.2, 0, 23.5), 0.07, seg=6)
# Carley floats on the superstructure sides
for s in (82, 88, 94, 113, 140, 158):
    for sx in (1, -1):
        x = sx * (interp([(77.5, 9.0), (80, 10.5), (96, 12.5), (146, 12.5), (149, 13.5), (158, 13.5), (162, 9.0)], s) + 0.12)
        acc.box((2.2, 0.35, 0.9), P(s, x, 17.8), mi=0)
acc.done()
# barbettes
for s, r, top in ((49.5, 5.6, 17.1), (68.0, 5.6, 19.2), (177.3, 5.6, 19.3), (195.8, 5.6, 17.0)):
    acyl("BIS_barb%d" % int(s), r, top - zd(s) + 0.2, tuple(P(s, 0, (top + zd(s) - 0.2) / 2)), GREY, G, seg=40, smooth=False)
SEC = [(103.0, 14.7, 15.75, True), (103.0, -14.7, 15.75, True), (132.5, 14.7, 15.75, False), (132.5, -14.7, 15.75, False),
       (153.0, 10.3, 20.0, False), (153.0, -10.3, 20.0, False)]
for i, (s, x, b, aft) in enumerate(SEC):
    z0 = zd(s) if b < 19 else 19.5
    acyl("BIS_sbarb%d" % i, 3.0, b - z0 + 0.1, tuple(P(s, x, (b + z0 - 0.1) / 2)), GREY, G, seg=28, smooth=False)
# superstructure portholes and doors
acc = Acc("BIS_ports", G, ["M_SBlack", GREY])
for s0, s1, hwf in ((78.5, 95.0, None), (110.0, 116.0, None), (139.0, 145.5, None), (149.5, 157.5, None)):
    s = s0
    while s < s1:
        w = interp([(77.5, 9.0), (80, 10.5), (96, 12.5), (146, 12.5), (149, 13.5), (158, 13.5), (162, 9.0)], s)
        for sx in (1, -1):
            acc.rod(P(s, sx * (w - 0.02), 17.6), P(s, sx * (w + 0.04), 17.6), 0.17, seg=8)
        s += 1.6
for s in (84.0, 112.0, 141.0):
    for sx in (1, -1):
        w = interp([(77.5, 9.0), (80, 10.5), (96, 12.5), (146, 12.5)], s)
        acc.box((0.9, 0.08, 1.9), P(s, sx * (w + 0.03), 16.1), mi=1)
acc.done(dirt=False)

# boats: motor launches / pinnaces on the hangar deck and round the funnel
def boat(tag, s0, s1, x, z, beam=2.4, cabin=True):
    L = s1 - s0
    pts = []
    for k in range(9):
        t = k / 8
        s = s0 + L * t
        w = beam / 2 * (1 - (2 * t - 1) ** 4) ** 0.5 if 0 < t < 1 else (0.25 if t == 0 else 0.02)
        kz = 0.0 if 0.1 < t < 0.85 else 0.25
        pts += [tuple(P(s, x + w, z + 1.25)), tuple(P(s, x - w, z + 1.25)), tuple(P(s, x + w * 0.4, z + kz + 0.15)),
                tuple(P(s, x - w * 0.4, z + kz + 0.15))]
    pts += [tuple(P(s1 + 0.3, x, z + 1.4))]
    chull("BIS_boat_" + tag, pts, "M_BoatHull", G, bevel=0.03)
    abox("BIS_boatdk_" + tag, (beam * 0.8, L * 0.8, 0.06), tuple(P(s0 + L * 0.5, x, z + 1.27)), "M_Wood", G, bevel=0.0)
    if cabin:
        abox("BIS_boatcab_" + tag, (beam * 0.62, L * 0.28, 0.75), tuple(P(s0 + L * 0.62, x, z + 1.65)), "M_BoatHull", G, bevel=0.06)
        abox("BIS_boatwin_" + tag, (beam * 0.63, 0.05, 0.3), tuple(P(s0 + L * 0.76, x, z + 1.75)), "M_SGlass", G, bevel=0.0)
    for t in (0.22, 0.78):
        abox("BIS_boatcr_%s_%d" % (tag, int(t * 100)), (beam * 0.7, 0.25, 0.25), tuple(P(s0 + L * t, x, z + 0.12)), GREY, G, bevel=0.0)
for sx in (1, -1):
    boat("mm%d" % sx, 101.5, 111.5, sx * 6.0, 22.45)
    boat("hg%d" % sx, 100.5, 108.0, sx * 2.0, 26.75, beam=2.0, cabin=False)
    boat("fu%d" % sx, 131.3, 138.3, sx * 2.6, 22.45, beam=2.1)
    boat("fo%d" % sx, 129.0, 137.5, sx * 6.3, 22.45, beam=2.3, cabin=False)
# boat cranes either side of the funnel
for sx in (1, -1):
    b0 = P(121.5, sx * 7.4, 22.4)
    acyl("BIS_crp%d" % sx, 0.9, 2.3, tuple(b0 + Vector((0, 0, 1.15))), GREY, G, seg=14)
    abox("BIS_crh%d" % sx, (2.2, 2.8, 2.0), tuple(b0 + Vector((0, 0.2, 3.3))), GREY, G, bevel=0.05)
    t0, t1 = b0 + Vector((0, -0.5, 3.6)), P(136.0, sx * 5.6, 33.0)
    acc = Acc("BIS_jib%d" % sx, G, [GREY])
    side = Vector((1, 0, 0))
    for off in (-0.45, 0.45):
        acc.rod(t0 + side * off, t1 + side * off * 0.4, 0.14, seg=4)
    for k in range(9):
        a = t0.lerp(t1, k / 9)
        b = t0.lerp(t1, (k + 1) / 9)
        o = 0.45 * (1 - 0.6 * (k + 0.5) / 9)
        acc.rod(a + side * o, b - side * o, 0.06, seg=4)
    acc.done()
    rod("BIS_crw%d" % sx, t1, t1 + Vector((0, 0, -5.0)), 0.03, "M_SBlack", GR, seg=4)

# ================================================================================================ railings
acc = Acc("BIS_rails", G, ["M_SGrey"])
for sx in (1, -1):
    pts_hi, pts_lo = [], []
    s = 1.6
    while s <= 248.5:
        x = sx * max(0.2, hb(s) - 0.18)
        z = zd(s)
        acc.rod(P(s, x, z), P(s, x, z + 1.05), 0.03, seg=4)
        pts_hi.append(P(s, x, z + 1.05))
        pts_lo.append(P(s, x, z + 0.55))
        s += 2.4
    acc.tube(pts_hi, 0.018, seg=3)
    acc.tube(pts_lo, 0.014, seg=3)
# superstructure deck edges
for nm, outline, z in (("dh2a", rounded_rect(80, 96, 7.5, 2.0), 22.4), ("b1", rounded_rect(136.5, 148.5, 7.2, 1.5), 30.4),
                       ("b2", rounded_rect(137.5, 147.5, 5.0, 1.2), 37.55), ("b3", rounded_rect(133.5, 145, 5.5, 1.2), 40.55)):
    ring = [P(s, x * 0.98, z + 1.0) for s, x in outline] + [P(outline[0][0], outline[0][1] * 0.98, z + 1.0)]
    acc.tube(ring, 0.03, seg=3)
    for s, x in outline[::2]:
        acc.rod(P(s, x * 0.98, z), P(s, x * 0.98, z + 1.0), 0.03, seg=4)
acc.done(dirt=False)
# antenna / rigging wires
acc = Acc("BIS_antenna_wires", GR, ["M_SBlack"])
for sx in (1, -1):
    acc.rod(P(133.3, sx * 7.8, 30.8), P(106.3, sx * 5.8, 46.8), 0.025, seg=3)
    acc.rod(P(106.3, sx * 5.8, 46.8), P(1.5, sx * 1.0, 22.0), 0.025, seg=3)
    acc.rod(P(133.3, sx * 3.9, 45.0), P(250.0, sx * 0.3, 23.0), 0.025, seg=3)
acc.rod(P(106.3, 0, 57.5), P(133.3, 0, 52.0), 0.02, seg=3)
acc.done(dirt=False)

# ================================================================================================ propellers, rudders
def propeller(grp, c, r, nb=3, hand=1):
    lathe_y(grp + "_hub", [(-0.9, 0.0), (-0.75, 0.38), (0.0, 0.55), (0.9, 0.55)], tuple(c), ["M_Bronze"], grp, seg=14)
    for k in range(nb):
        a = 2 * PI * k / nb
        d = Vector((math.cos(a), 0, math.sin(a)))
        t = Vector((-math.sin(a), 0, math.cos(a)))
        pts = []
        for rr, ch in ((0.5, 0.9), (1.2, 1.5), (1.9, 1.35), (r, 0.5)):
            for u in (-0.5, 0.5):
                p = c + d * rr + t * (u * ch) + Vector((0, u * ch * 0.6 * hand, 0))
                pts += [tuple(p + Vector((0, 0.06, 0))), tuple(p - Vector((0, 0.06, 0)))]
        chull("%s_bl%d" % (grp, k), pts, "M_Bronze", grp, bevel=0.0)
PROPS = [("prop", P(26.2, 0, 2.4), 2.35, 1), ("prop_L", P(20.1, 7.0, 1.9), 2.35, 1), ("prop_R", P(20.1, -7.0, 1.9), 2.35, -1)]
for nm, c, r, hd in PROPS:
    propeller("BIS_" + nm, c, r, 3, hd)
    if nm != "prop":
        sx = 1 if c.x > 0 else -1
        rod("BIS_shaft" + nm, c + Vector((0, -0.8, 0)), P(46.0, sx * 6.2, 4.0), 0.32, "M_SDark", G, seg=10)
        for s_ in (24.0, 34.0):
            sh = Vector(c).lerp(P(46.0, sx * 6.2, 4.0), (s_ - 20.1) / 25.9)
            rod("BIS_brk%s%d" % (nm, int(s_)), sh, sh + Vector((-sx * 3.0, 0, 3.2)), 0.18, "M_SDark", G, seg=6)
            rod("BIS_brkb%s%d" % (nm, int(s_)), sh, sh + Vector((-sx * 4.0, 0, 1.4)), 0.18, "M_SDark", G, seg=6)
rod("BIS_shaft_c", P(26.9, 0, 2.4), P(31.8, 0, 2.6), 0.36, "M_SDark", G, seg=10)
RUD = [("rudder_L", 3.0), ("rudder_R", -3.0)]
for nm, x in RUD:
    pts = []
    for s, z0, z1 in ((12.9, 2.3, 5.6), (17.6, 1.9, 5.7)):
        for dx in (-0.3, 0.3):
            pts += [tuple(P(s, x + dx * (0.4 if s < 13 else 1.0), z0)), tuple(P(s, x + dx * (0.4 if s < 13 else 1.0), z1))]
    chull("BIS_" + nm, pts, "M_SRed", "BIS_" + nm, bevel=0.04)
    rod("BIS_rstock" + nm, P(17.2, x, 5.5), P(17.2, x, 6.6), 0.25, "M_SRed", "BIS_" + nm, seg=8)

# ================================================================================================ guns
def L(f, x, z):
    return Vector((x, -f, z))


def main_turret(T):
    """38 cm Drh LC/34 turret in its local frame (forward -Y, origin = rotation axis at the house floor)."""
    GY, GP = "BIS_t%s_y" % T, "BIS_t%s_p" % T
    pts = []
    for z, layer in ((0.0, [(-7.3, 4.4), (-7.0, 5.1), (5.0, 5.3), (6.7, 3.8)]), (3.3, [(-7.3, 4.4), (-7.0, 5.1), (4.8, 5.3), (6.3, 3.6)]),
                     (3.9, [(-7.3, 4.4), (-7.0, 5.1)]), (4.7, [(-6.0, 4.3), (-5.8, 4.75), (3.4, 4.85), (4.3, 3.4)])):
        for f, hw in layer:
            pts += [tuple(L(f, hw, z)), tuple(L(f, -hw, z))]
    chull("BIS_t%s_house" % T, pts, GREY, GY, bevel=0.05, uvscale=0.25)
    roof = []
    for f, hw in ((-5.9, 4.2), (-5.7, 4.65), (3.35, 4.75), (4.2, 3.3)):
        roof += [tuple(L(f, hw, 4.7)), tuple(L(f, -hw, 4.7)), tuple(L(f, hw * 0.99, 4.74)), tuple(L(f, -hw * 0.99, 4.74))]
    chull("BIS_t%s_roof" % T, roof, DARK, GY, bevel=0.0)
    acyl("BIS_t%s_skirt" % T, 5.45, 0.35, tuple(L(0, 0, -0.1)), GREY, GY, seg=36, smooth=False)
    # 10.5 m rangefinder across the rear of the roof with armoured end hoods
    acyl("BIS_t%s_rf" % T, 0.42, 10.4, tuple(L(-4.3, 0, 3.75)), GREY, GY, axis=0, seg=12)
    for sx in (1, -1):
        abox("BIS_t%s_rfh%d" % (T, sx), (0.9, 1.5, 1.2), tuple(L(-4.3, sx * 5.55, 3.75)), GREY, GY, bevel=0.1)
        abox("BIS_t%s_rfw%d" % (T, sx), (0.05, 0.5, 0.25), tuple(L(-3.9, sx * 6.01, 3.85)), "M_SBlack", GY, bevel=0.0, dirt=False)
        abox("BIS_t%s_peri%d" % (T, sx), (0.8, 1.0, 0.45), tuple(L(1.8, sx * 2.9, 4.95)), DARK, GY, bevel=0.08)
        abox("BIS_t%s_vent%d" % (T, sx), (0.6, 0.6, 0.3), tuple(L(-2.5, sx * 1.4, 4.85)), DARK, GY, bevel=0.05)
        for k in range(6):
            rod("BIS_t%s_rung%d_%d" % (T, sx, k), L(-7.38, sx * 2.6 - 0.25, 0.6 + k * 0.6), L(-7.38, sx * 2.6 + 0.25, 0.6 + k * 0.6), 0.03, GREY, GY, seg=4)
        rod("BIS_t%s_hr%d" % (T, sx), L(-5.4, sx * 3.9, 4.95), L(2.6, sx * 4.2, 4.95), 0.03, GREY, GY, seg=4)
        for f_ in (-5.4, -1.4, 2.6):
            xx = sx * (3.9 + 0.3 * (f_ + 5.4) / 8.0)
            rod("BIS_t%s_hrs%d_%d" % (T, sx, int(f_ * 10)), L(f_, xx, 4.95), L(f_, xx, 4.72), 0.03, GREY, GY, seg=4)
    abox("BIS_t%s_hatch" % T, (1.2, 1.4, 0.15), tuple(L(-4.8, 0, 4.78)), DARK, GY, bevel=0.03)
    abox("BIS_t%s_door" % T, (1.2, 0.06, 2.0), tuple(L(-7.33, 0, 1.4)), GREY, GY, bevel=0.0)
    # pitch group: mantlets with canvas blast bags
    for gi, gx in enumerate((1.9, -1.9)):
        lathe_y("BIS_t%s_bag%d" % (T, gi), [(-0.7, 0.55), (-0.45, 0.78), (0.0, 0.85), (0.45, 0.8), (0.7, 0.6)], tuple(L(6.4, gx, 2.7)),
                ["M_Bag"], GP, seg=14)
    abox("BIS_t%s_cradle" % T, (5.0, 1.4, 1.1), tuple(L(4.6, 0, 2.7)), GREY, GP, bevel=0.05)


def main_gun(T, gi, gx):
    grp = "BIS_t%s_g%d" % (T, gi)
    c = L(4.3, gx, 2.7)
    lathe_y(grp + "_tube", [(-15.75, 0.18), (-15.75, 0.36), (-15.25, 0.36), (-14.9, 0.32), (-8.0, 0.37), (-2.6, 0.47), (-1.8, 0.5),
                             (1.6, 0.5), (1.6, 0.0)], tuple(c), [GREY], grp, seg=18)
    acyl(grp + "_bore", 0.18, 0.04, tuple(c + Vector((0, -15.72, 0))), "M_SBlack", grp, axis=1, seg=12, dirt=False)


def sec_turret(i):
    GY, GP = "BIS_s%d_y" % i, "BIS_s%d_p" % i
    pts = []
    for z, layer in ((0.0, [(-4.4, 2.9), (-4.2, 3.3), (2.6, 3.3), (3.6, 2.3)]), (2.2, [(-4.4, 2.9), (-4.2, 3.3), (2.4, 3.3), (3.3, 2.2)]),
                     (3.0, [(-3.8, 2.8), (-3.6, 3.0), (1.9, 3.0), (2.6, 2.0)])):
        for f, hw in layer:
            pts += [tuple(L(f, hw, z)), tuple(L(f, -hw, z))]
    chull("BIS_s%d_house" % i, pts, GREY, GY, bevel=0.04, uvscale=0.3)
    roof = []
    for f, hw in ((-3.75, 2.75), (1.85, 2.95), (2.55, 1.95)):
        roof += [tuple(L(f, hw, 3.0)), tuple(L(f, -hw, 3.0)), tuple(L(f, hw, 3.03)), tuple(L(f, -hw, 3.03))]
    chull("BIS_s%d_roof" % i, roof, DARK, GY, bevel=0.0)
    acyl("BIS_s%d_rf" % i, 0.22, 6.4, tuple(L(-2.9, 0, 2.6)), GREY, GY, axis=0, seg=10)
    for sx in (1, -1):
        abox("BIS_s%d_rfh%d" % (i, sx), (0.5, 0.8, 0.7), tuple(L(-2.9, sx * 3.35, 2.6)), GREY, GY, bevel=0.06)
        abox("BIS_s%d_peri%d" % (i, sx), (0.5, 0.6, 0.3), tuple(L(1.0, sx * 1.8, 3.15)), DARK, GY, bevel=0.05)
    acyl("BIS_s%d_skirt" % i, 3.05, 0.25, tuple(L(0, 0, -0.08)), GREY, GY, seg=24, smooth=False)
    for gi, gx in enumerate((0.95, -0.95)):
        lathe_y("BIS_s%d_bag%d" % (i, gi), [(-0.35, 0.3), (0.0, 0.42), (0.35, 0.3)], tuple(L(3.4, gx, 1.6)), ["M_Bag"], GP, seg=10)
        lathe_y("BIS_s%d_tube%d" % (i, gi), [(-7.3, 0.08), (-7.3, 0.16), (-7.0, 0.15), (-3.0, 0.17), (-0.5, 0.22), (1.2, 0.24), (1.2, 0.0)],
                tuple(L(2.3, gx, 1.6)), [GREY], GP, seg=12)
        acyl("BIS_s%d_bore%d" % (i, gi), 0.08, 0.03, tuple(L(9.58, gx, 1.6)), "M_SBlack", GP, axis=1, seg=8, dirt=False)


def flak105(i):
    """10.5 cm SK C/33 twin (Dopp. LC/31 / C37): shielded, open-backed. Local origin on the deck."""
    GB, GY, GP = G, "BIS_h%d_y" % i, "BIS_h%d_p" % i
    acyl("BIS_h%d_ped" % i, 1.2, 0.6, (0, 0, 0.3), GREY, "BIS_h%d_base" % i, seg=20)
    acyl("BIS_h%d_tt" % i, 1.45, 0.25, (0, 0, 0.72), GREY, GY, seg=22)
    abox("BIS_h%d_shf" % i, (3.3, 0.08, 2.1), tuple(L(1.05, 0, 1.85)), GREY, GY, rot=(math.radians(-12), 0, 0), bevel=0.0)
    for sx in (1, -1):
        abox("BIS_h%d_shs%d" % (i, sx), (0.08, 1.9, 1.9), tuple(L(0.15, sx * 1.62, 1.75)), GREY, GY, rot=(0, 0, sx * math.radians(-6)), bevel=0.0)
        abox("BIS_h%d_frame%d" % (i, sx), (0.15, 1.2, 0.9), tuple(L(0.3, sx * 0.75, 1.15)), GREY, GY, bevel=0.03)
        abox("BIS_h%d_seat%d" % (i, sx), (0.35, 0.35, 0.08), tuple(L(-0.6, sx * 1.0, 1.3)), "M_SBlack", GY, bevel=0.0)
        rod("BIS_h%d_spost%d" % (i, sx), L(-0.6, sx * 1.0, 1.27), L(-0.6, sx * 1.0, 0.8), 0.04, GREY, GY, seg=4)
    abox("BIS_h%d_roof" % i, (3.3, 1.1, 0.06), tuple(L(0.55, 0, 2.87)), GREY, GY, bevel=0.0)
    abox("BIS_h%d_cradle" % i, (1.25, 1.6, 0.55), tuple(L(0.3, 0, 1.55)), GREY, GP, bevel=0.04)
    for gx in (0.33, -0.33):
        rod("BIS_h%d_tube%d" % (i, int(gx * 10)), L(-0.6, gx, 1.55), L(6.3, gx, 1.55), 0.1, GREY, GP, seg=10, r2=0.065)
        rod("BIS_h%d_rec%d" % (i, int(gx * 10)), L(-0.4, gx, 1.78), L(1.6, gx, 1.78), 0.08, GREY, GP, seg=8)
    abox("BIS_h%d_fuze" % i, (0.5, 0.6, 0.6), tuple(L(-0.7, 0, 1.4)), GREY, GP, bevel=0.03)


def flak37(i):
    GY, GP = "BIS_f%d_y" % i, "BIS_f%d_p" % i
    acyl("BIS_f%d_ped" % i, 0.42, 0.85, (0, 0, 0.42), GREY, "BIS_f%d_base" % i, seg=12)
    acyl("BIS_f%d_tt" % i, 0.55, 0.15, (0, 0, 0.9), GREY, GY, seg=14)
    for sx in (1, -1):
        abox("BIS_f%d_cheek%d" % (i, sx), (0.08, 0.7, 0.55), tuple(L(0.0, sx * 0.4, 1.2)), GREY, GY, bevel=0.0)
        abox("BIS_f%d_seat%d" % (i, sx), (0.3, 0.3, 0.06), tuple(L(-0.55, sx * 0.55, 0.95)), "M_SBlack", GY, bevel=0.0)
    abox("BIS_f%d_shield" % i, (1.4, 0.05, 0.6), tuple(L(0.55, 0, 1.35)), GREY, GY, bevel=0.0)
    abox("BIS_f%d_cradle" % i, (0.55, 0.9, 0.35), tuple(L(0.1, 0, 1.15)), GREY, GP, bevel=0.02)
    for gx in (0.2, -0.2):
        rod("BIS_f%d_tube%d" % (i, int(gx * 10)), L(-0.4, gx, 1.15), L(2.7, gx, 1.15), 0.045, GREY, GP, seg=6)
        rod("BIS_f%d_fh%d" % (i, int(gx * 10)), L(2.5, gx, 1.15), L(2.85, gx, 1.15), 0.065, "M_SBlack", GP, seg=6)


def flak20(i, quad=False):
    tag = ("q%d" if quad else "l%d") % i
    GY, GP = "BIS_%s_y" % tag, "BIS_%s_p" % tag
    if quad:
        acyl("BIS_%s_base" % tag, 1.1, 0.25, (0, 0, 0.12), GREY, "BIS_%s_b" % tag, seg=16)
        acyl("BIS_%s_tt" % tag, 0.9, 0.2, (0, 0, 0.35), GREY, GY, seg=14)
        abox("BIS_%s_shield" % tag, (1.7, 0.05, 0.8), tuple(L(0.75, 0, 1.0)), GREY, GY, bevel=0.0)
        abox("BIS_%s_seat" % tag, (0.4, 0.4, 0.06), tuple(L(-0.6, 0, 0.75)), "M_SBlack", GY, bevel=0.0)
        abox("BIS_%s_cradle" % tag, (0.9, 0.7, 0.6), tuple(L(0.0, 0, 1.0)), GREY, GP, bevel=0.02)
        for gx in (0.28, -0.28):
            for gz in (0.78, 1.22):
                rod("BIS_%s_t%d_%d" % (tag, int(gx * 10), int(gz * 10)), L(-0.3, gx, gz), L(1.9, gx, gz), 0.025, "M_SBlack", GP, seg=5)
    else:
        for k in range(3):
            a = 2 * PI * k / 3
            rod("BIS_%s_leg%d" % (tag, k), (0, 0, 0.7), (0.6 * math.cos(a), 0.6 * math.sin(a), 0.0), 0.04, GREY, "BIS_%s_b" % tag, seg=4)
        acyl("BIS_%s_tt" % tag, 0.2, 0.2, (0, 0, 0.75), GREY, GY, seg=8)
        abox("BIS_%s_shield" % tag, (0.75, 0.04, 0.55), tuple(L(0.45, 0, 1.0)), GREY, GY, bevel=0.0)
        abox("BIS_%s_cradle" % tag, (0.25, 0.7, 0.25), tuple(L(0.1, 0, 0.95)), GREY, GP, bevel=0.0)
        rod("BIS_%s_tube" % tag, L(-0.3, 0, 0.95), L(1.9, 0, 0.95), 0.025, "M_SBlack", GP, seg=5)
        abox("BIS_%s_mag" % tag, (0.08, 0.3, 0.25), tuple(L(0.2, 0.15, 1.12)), "M_SBlack", GP, bevel=0.0)


# --------------------------------------------------------------------------------------------- place the batteries
MAIN = [("A", 195.8, 17.0, False), ("B", 177.3, 19.3, False), ("C", 68.0, 19.2, True), ("D", 49.5, 17.1, True)]
H_POS = [(92.0, 10.0), (92.0, -10.0), (108.0, 11.0), (108.0, -11.0), (124.5, 11.0), (124.5, -11.0), (144.5, 11.3), (144.5, -11.3)]
F_POS = [(78.6, 7.4, 19.6), (78.6, -7.4, 19.6), (88.5, 5.0, 22.4), (88.5, -5.0, 22.4), (120.0, 14.6, 19.6), (120.0, -14.6, 19.6),
         (146.5, 3.0, 26.6), (146.5, -3.0, 26.6)]
L_POS = [(97.0, 11.6, 19.6), (97.0, -11.6, 19.6), (113.0, 11.8, 19.6), (113.0, -11.8, 19.6), (138.5, 9.5, 19.6), (138.5, -9.5, 19.6),
         (161.5, 7.0, 19.6), (161.5, -7.0, 19.6), (165.0, 4.0, 19.6), (165.0, -4.0, 19.6)]
Q_POS = [(144.5, 6.1, 30.4), (144.5, -6.1, 30.4)]
SPEC = {}                      # node -> (pivot world, aft)

for T, s, b, aft in MAIN:
    M_ = turret_matrix(s, 0, b, aft)
    place(new_objs(main_turret, T), M_)
    for gi, gx in enumerate((1.9, -1.9)):
        place(new_objs(main_gun, T, gi, gx), M_)
    SPEC["t%s" % T] = (M_, aft)
for i, (s, x, b, aft) in enumerate(SEC):
    M_ = turret_matrix(s, x, b, aft)
    place(new_objs(sec_turret, i), M_)
    SPEC["s%d" % i] = (M_, aft)
for i, (s, x) in enumerate(H_POS):
    M_ = turret_matrix(s, x, 19.6, False)
    place(new_objs(flak105, i), M_)
    SPEC["h%d" % i] = (M_, False)
for i, (s, x, z) in enumerate(F_POS):
    M_ = turret_matrix(s, x, z, False)
    place(new_objs(flak37, i), M_)
    SPEC["f%d" % i] = (M_, False)
for i, (s, x, z) in enumerate(L_POS):
    M_ = turret_matrix(s, x, z, False)
    place(new_objs(flak20, i), M_)
    SPEC["l%d" % i] = (M_, False)
for i, (s, x, z) in enumerate(Q_POS):
    M_ = turret_matrix(s, x, z, False)
    place(new_objs(flak20, i, True), M_)
    SPEC["q%d" % i] = (M_, False)

# rotating rangefinder cupolas (follow the main battery's bearing in game)
def rf_cupola(tag, span, z0, radar=False):
    g = "BIS_rf_" + tag
    acyl(g + "_drum", 1.9, 2.4, (0, 0, z0 + 1.2), GREY, g, seg=24, smooth=False)
    lathe_y(g + "_dome", [(0.0, 1.9), (0.5, 1.75), (0.9, 1.3), (1.1, 0.0)], (0, 0, z0 + 2.4), [DARK], g, seg=24, axis='Z')
    for sx in (1, -1):
        rod(g + "_arm%d" % sx, (sx * 1.75, 0.3, z0 + 1.7), (sx * span / 2, 0.3, z0 + 1.7), 0.38, GREY, g, seg=10)
    for sx in (1, -1):
        abox(g + "_hood%d" % sx, (0.8, 1.2, 1.0), (sx * span / 2, 0.3, z0 + 1.7), GREY, g, bevel=0.08)
    if radar:
        acc = Acc(g + "_fumo", g, [GREY, "M_SBlack"])
        acc.box((4.0, 0.15, 0.15), (0, -1.6, z0 + 3.0))
        acc.box((4.0, 0.15, 0.15), (0, -1.6, z0 + 5.0))
        for k in range(9):
            acc.box((0.06, 0.08, 2.0), (-2.0 + k * 0.5, -1.62, z0 + 4.0), mi=1)
        for k in range(5):
            acc.box((4.0, 0.06, 0.05), (0, -1.66, z0 + 3.2 + k * 0.4), mi=1)
        acc.rod((0, -1.0, z0 + 2.9), (0, -1.6, z0 + 3.1), 0.12, seg=6)
        acc.done()
    rod(g + "_ant", (0, 0.8, z0 + 2.8), (0, 0.8, z0 + 6.0 if tag != "aft" else z0 + 9.8), 0.06, GREY, g, seg=5)
RF = [("fore", 141.3, 40.55, 10.5, True), ("aft", 88.5, 26.3, 10.5, False), ("fwd", 152.5, 27.2, 7.0, True)]
for tag, s, z0, span, radar in RF:
    place(new_objs(rf_cupola, tag, span, 0.0, radar), Matrix.Translation(P(s, 0, z0)))

# Arado on the catapult trolley (faces port), launched / recovered in game -> node hidden while airborne
CAT_C = P(115.5, 0, 21.15)
place(new_objs(arado, "BIS_ar", "BIS_arado"), Matrix.Translation(CAT_C) @ Matrix.Rotation(PI / 2, 4, 'Z'))

# ================================================================================================ weathering, hierarchy
def ship_weather(w, n, ob):
    h = hash3(w * 1.7)
    k = 0.92 + 0.1 * h
    if n.z < -0.3:
        k *= 0.88
    if 121 < 125.5 - w.y < 129.5 and w.z > 31.5:          # funnel soot
        k *= 0.55 + 0.45 * min(1.0, (35.5 - w.z) / 4.0)
    if w.z < 10.8 and abs(w.x) > 2:                        # salt / rust stains near the boot-top
        k *= 0.95
    return k, k, k
weather(ship_weather)

rt = root("root")
group_under(G, "bis_body", rt)
group_under(GR, "bis_antenna_rig", rt)
group_under("BIS_arado", "arado_cat_geo", pivot("arado_cat", tuple(CAT_C), rt))
for nm, c, r, hd in PROPS:
    group_under("BIS_" + nm, nm + "_geo", pivot(nm, tuple(c), rt))
for nm, x in RUD:
    group_under("BIS_" + nm, nm + "_geo", pivot(nm, tuple(P(17.2, x, 4.0)), rt))
for tag, s, z0, span, radar in RF:
    group_under("BIS_rf_" + tag, "rf_%s_geo" % tag, pivot("rf_" + tag, tuple(P(s, 0, z0)), rt))
for T, s, b, aft in MAIN:
    M_ = SPEC["t%s" % T][0]
    y = pivot("t%s_yaw" % T, tuple(M_ @ Vector((0, 0, 0))), rt)
    group_under("BIS_t%s_y" % T, "t%s_yaw_geo" % T, y)
    p = pivot("t%s_pitch" % T, tuple(M_ @ L(4.3, 0, 2.7)), y)
    group_under("BIS_t%s_p" % T, "t%s_pitch_geo" % T, p)
    for gi, gx in enumerate((1.9, -1.9)):
        g = pivot("t%s_g%d" % (T, gi), tuple(M_ @ L(4.3, gx, 2.7)), p)
        group_under("BIS_t%s_g%d" % (T, gi), "t%s_g%d_geo" % (T, gi), g)
def yaw_pitch(tag, pitch_local):
    M_ = SPEC[tag][0]
    group_under("BIS_%s_base" % tag, "%s_base" % tag, rt) or group_under("BIS_%s_b" % tag, "%s_base" % tag, rt)
    y = pivot("%s_yaw" % tag, tuple(M_ @ Vector((0, 0, 0))), rt)
    group_under("BIS_%s_y" % tag, "%s_yaw_geo" % tag, y)
    p = pivot("%s_pitch" % tag, tuple(M_ @ pitch_local), y)
    group_under("BIS_%s_p" % tag, "%s_pitch_geo" % tag, p)
for i in range(6):
    yaw_pitch("s%d" % i, L(2.3, 0, 1.6))
for i in range(8):
    yaw_pitch("h%d" % i, L(0.3, 0, 1.55))
    yaw_pitch("f%d" % i, L(0.1, 0, 1.15))
for i in range(10):
    yaw_pitch("l%d" % i, L(0.1, 0, 0.95))
for i in range(2):
    yaw_pitch("q%d" % i, L(0.0, 0, 1.0))
left = [o.name for o in col_get("BUILD").objects if o.get("grp") and o.parent is None and o.name != "bis_body"]
print("UNPARENTED", len(left), left[:20])
rw_export("/home/user/.cache/rwveh/raw/bismarck.glb", "/home/user/models/rw_bismarck.blend", "Bismarck")
