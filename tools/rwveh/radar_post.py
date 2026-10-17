# Stationary early-warning radar post: P-18-type antenna (2 rows x 8 Yagi arrays) on a lattice mast over an operator shelter.
# Nodes: root > launcher_pitch (empty), radar_yaw (0,15.08,0 glTF) > rotating antenna head.  Operator seat (0,0.67,1.6) inside.
reset_scene()
col_get("BUILD"); col_get("RIG")
rw_mats()
amat("M_Concrete", None, 0.9, 0.0, factor=(0.48, 0.47, 0.44, 1))
amat("M_Wall", None, 0.8, 0.0, factor=(0.36, 0.40, 0.30, 1))
amat("M_Floor", None, 0.8, 0.0, factor=(0.25, 0.22, 0.18, 1))
amat("M_Radome", None, 0.7, 0.0, factor=(0.55, 0.58, 0.50, 1))
amat("M_PPI", None, 0.2, 0.0, factor=(0.05, 0.6, 0.15, 1), emissive=(0.05, 0.75, 0.2, 1))
P = "RPOST"
G = P + "_base"
HX, HY, HZ = 4.2, 3.8, 3.8          # shelter half-width X, half-depth Y (Blender), wall height
T = 0.15

# ---------------------------------------------------------------- foundation + shelter (hollow, door at glTF -Z = Blender +Y)
abox(P + "_slab", (2 * HX + 0.6, 2 * HY + 0.6, 0.25), (0, 0, 0.125 - 0.1), "M_Concrete", G, bevel=0.03)
abox(P + "_floor", (2 * HX - 2 * T, 2 * HY - 2 * T, 0.05), (0, 0, 0.17), "M_Floor", G, bevel=0.0)
abox(P + "_roof", (2 * HX + 0.2, 2 * HY + 0.2, 0.3), (0, 0, HZ + 0.15), "M_Concrete", G, bevel=0.03)
abox(P + "_wF", (2 * HX, T, HZ), (0, -HY + T / 2, HZ / 2), "M_Wall", G, bevel=0.01)
for sx in (1, -1):
    # side walls with a window (glass) each
    abox(P + "_wSl%d" % sx, (T, 2 * HY, 1.2), (sx * (HX - T / 2), 0, 0.6), "M_Wall", G, bevel=0.01)
    abox(P + "_wSh%d" % sx, (T, 2 * HY, HZ - 2.2), (sx * (HX - T / 2), 0, (HZ + 2.2) / 2), "M_Wall", G, bevel=0.01)
    abox(P + "_wSf%d" % sx, (T, HY - 0.6, 1.0), (sx * (HX - T / 2), -(HY + 0.6) / 2, 1.7), "M_Wall", G, bevel=0.01)
    abox(P + "_wSr%d" % sx, (T, HY - 0.6, 1.0), (sx * (HX - T / 2), (HY + 0.6) / 2, 1.7), "M_Wall", G, bevel=0.01)
    abox(P + "_win%d" % sx, (0.03, 1.2, 1.0), (sx * (HX - T / 2), 0, 1.7), "M_CabGlass", G, bevel=0.0, dirt=False)
    abox(P + "_winf%d" % sx, (0.05, 1.3, 0.06), (sx * (HX + 0.0), 0, 1.17), "M_Plain", G, bevel=0.005)
# back wall (door side) with a door opening 1.0 x 2.2
abox(P + "_wB1", (HX - 0.5, T, HZ), ((HX + 0.5) / 2, HY - T / 2, HZ / 2), "M_Wall", G, bevel=0.01)
abox(P + "_wB2", (HX - 0.5, T, HZ), (-(HX + 0.5) / 2, HY - T / 2, HZ / 2), "M_Wall", G, bevel=0.01)
abox(P + "_wB3", (1.0, T, HZ - 2.2), (0, HY - T / 2, (HZ + 2.2) / 2), "M_Wall", G, bevel=0.01)
abox(P + "_dframe", (1.12, 0.2, 0.08), (0, HY, 2.24), "M_Plain", G, bevel=0.01)
for sx in (1, -1):
    abox(P + "_dframe%d" % sx, (0.06, 0.2, 2.2), (0.53 * sx, HY, 1.1), "M_Plain", G, bevel=0.005)
# open steel door leaf (swung outwards 100 deg), steps, canopy, lamp
abox(P + "_door", (0.96, 0.05, 2.1), (0.5 + 0.08, HY + 0.48, 1.08), "M_Plain", G, rot=(0, 0, math.radians(-80)), bevel=0.01)
for k in range(2):
    abox("%s_step%d" % (P, k), (1.3, 0.35, 0.12), (0, HY + 0.3 + 0.35 * k, 0.12 - 0.06 * k), "M_Concrete", G, bevel=0.01)
abox(P + "_canopy", (1.6, 0.8, 0.06), (0, HY + 0.4, 2.55), "M_Plain", G, rot=(-0.1, 0, 0), bevel=0.01)
acyl(P + "_lamp", 0.08, 0.1, (0, HY + 0.1, 2.4), "M_HeadLamp", G, seg=10, dirt=False)
# exterior: AC units, cable trays, generator, signs, gutters
for sx in (1, -1):
    abox(P + "_ac%d" % sx, (0.25, 0.9, 0.7), (sx * (HX + 0.12), -HY + 1.2, 2.6), "M_Plain", G, bevel=0.03)
    acyl(P + "_acfan%d" % sx, 0.25, 0.04, (sx * (HX + 0.26), -HY + 1.2, 2.6), "M_Black", G, axis=0, seg=16)
    rod(P + "_gutter%d" % sx, Vector((sx * (HX + 0.15), HY + 0.05, HZ + 0.25)), Vector((sx * (HX + 0.15), HY + 0.05, 0.1)), 0.04, "M_Steel", G, seg=8)
abox(P + "_gen", (1.6, 0.9, 1.0), (-HX - 1.4, 1.5, 0.5), "M_Body", G, bevel=0.05)
acyl(P + "_genexh", 0.06, 0.6, (-HX - 1.0, 1.5, 1.3), "M_Dark", G, seg=10)
abox(P + "_genslab", (2.0, 1.2, 0.1), (-HX - 1.4, 1.5, 0.0), "M_Concrete", G, bevel=0.01)
rod(P + "_gencable", Vector((-HX - 0.6, 1.5, 0.3)), Vector((-HX - 0.02, 1.5, 0.6)), 0.03, "M_Black", G, seg=6)
abox(P + "_sign", (0.6, 0.02, 0.4), (1.2, HY + 0.01, 1.6), "M_Stencil", G, bevel=0.0, dirt=False)

# ---------------------------------------------------------------- interior: operator console with PPI scope, cabinets, chair
S = gB(0, 0.67, 1.6)            # rider seat point (hips ~ seat - 0.1)
abox(P + "_chair", (0.5, 0.5, 0.08), (S.x, S.y, S.z - 0.16), "M_Seat", G, bevel=0.02)
abox(P + "_chairb", (0.5, 0.07, 0.6), (S.x, S.y + 0.27, S.z + 0.15), "M_Seat", G, rot=(-0.15, 0, 0), bevel=0.02)
rod(P + "_chairpost", Vector((S.x, S.y, S.z - 0.2)), Vector((S.x, S.y, 0.25)), 0.04, "M_Dark", G, seg=8)
for k in range(5):
    a = 2 * PI * k / 5
    rod("%s_chairleg%d" % (P, k), Vector((S.x, S.y, 0.25)), Vector((S.x + math.cos(a) * 0.3, S.y + math.sin(a) * 0.3, 0.22)), 0.02, "M_Dark", G, seg=6)
cy = S.y - 0.75
abox(P + "_desk", (2.6, 0.8, 0.05), (0, cy, 0.78), "M_Interior", G, bevel=0.01)
abox(P + "_console", (2.6, 0.55, 1.1), (0, cy - 0.15, 1.35), "M_Dash", G, rot=(0.15, 0, 0), bevel=0.02)
acyl(P + "_ppibz", 0.3, 0.05, (0, cy + 0.12, 1.45), "M_Chrome", G, axis=1, seg=28, rot=(PI / 2 + 0.15, 0, 0), dirt=False)
acyl(P + "_ppi", 0.27, 0.02, (0, cy + 0.15, 1.45), "M_PPI", G, axis=1, seg=28, rot=(PI / 2 + 0.15, 0, 0), dirt=False)
for k in range(2):
    abox("%s_scr%d" % (P, k), (0.45, 0.02, 0.32), ((k * 2 - 1) * 0.85, cy + 0.12, 1.5), "M_Screen", G, rot=(0.15, 0, 0), bevel=0.0, dirt=False)
for k in range(10):
    acyl("%s_btn%d" % (P, k), 0.015, 0.02, (-0.9 + 0.2 * k, cy + 0.2, 0.83), "M_Amber" if k % 3 else "M_NavRed", G, seg=8, dirt=False)
abox(P + "_phone", (0.2, 0.15, 0.08), (1.05, cy + 0.25, 0.85), "M_Black", G, bevel=0.01)
for k in range(4):
    abox("%s_cab%d" % (P, k), (0.7, 0.6, 2.0), (-HX + 0.55 + 0.75 * k + (4.2 if k > 1 else 0), HY - 0.5, 1.0), "M_Plain", G, bevel=0.02)
    for r_ in range(4):
        abox("%s_cabl%d_%d" % (P, k, r_), (0.5, 0.01, 0.05), (-HX + 0.55 + 0.75 * k + (4.2 if k > 1 else 0), HY - 0.81, 0.5 + 0.4 * r_), "M_Amber" if r_ == 3 else "M_Dark", G, bevel=0.0, dirt=False)
abox(P + "_table", (1.4, 0.8, 0.05), (HX - 1.0, 0, 0.75), "M_Interior", G, bevel=0.01)
abox(P + "_map", (1.0, 0.6, 0.005), (HX - 1.0, 0, 0.78), "M_Stencil", G, bevel=0.0, dirt=False)
acyl(P + "_ceil", 0.15, 0.05, (0, 0, HZ - 0.05), "M_HeadLamp", G, seg=14, dirt=False)
acyl(P + "_ext", 0.07, 0.45, (HX - 0.3, HY - 0.4, 0.4), "M_NavRed", G, seg=10)

# ---------------------------------------------------------------- lattice mast (roof 3.95 -> 15.0), ladder, platform
z0, z1 = HZ + 0.3, 15.0
w0, w1 = 1.3, 0.7
def leg_pt(i, z):
    f = (z - z0) / (z1 - z0)
    w = w0 + (w1 - w0) * f
    return Vector(((1 if i in (0, 3) else -1) * w / 2, (1 if i in (0, 1) else -1) * w / 2, z))
for i in range(4):
    rod("%s_leg%d" % (P, i), leg_pt(i, z0), leg_pt(i, z1), 0.06, "M_Plain", G, seg=8)
    abox("%s_legbase%d" % (P, i), (0.3, 0.3, 0.08), tuple(leg_pt(i, z0) + Vector((0, 0, 0.04))), "M_Steel", G, bevel=0.01)
NL = 9
for k in range(NL + 1):
    z = z0 + (z1 - z0) * k / NL
    for i in range(4):
        rod("%s_ring%d_%d" % (P, k, i), leg_pt(i, z), leg_pt((i + 1) % 4, z), 0.03, "M_Plain", G, seg=6)
        if k < NL:
            zn = z0 + (z1 - z0) * (k + 1) / NL
            rod("%s_diag%d_%d" % (P, k, i), leg_pt(i, z), leg_pt((i + 1) % 4, zn), 0.022, "M_Plain", G, seg=6)
# ladder on the door side of the mast with safety hoops
lx = 0.25
for sx in (1, -1):
    rod(P + "_lrail%d" % sx, Vector((sx * lx * 0.5, w0 / 2 + 0.25, z0)), Vector((sx * lx * 0.5, w1 / 2 + 0.25, z1)), 0.02, "M_Steel", G, seg=6)
for k in range(36):
    z = z0 + 0.15 + k * 0.3
    if z > z1:
        break
    f = (z - z0) / (z1 - z0)
    yy = (w0 + (w1 - w0) * f) / 2 + 0.25
    rod("%s_lrung%d" % (P, k), Vector((-lx * 0.5, yy, z)), Vector((lx * 0.5, yy, z)), 0.012, "M_Steel", G, seg=5)
for k in range(8):
    z = z0 + 2.5 + k * 1.1
    f = (z - z0) / (z1 - z0)
    yy = (w0 + (w1 - w0) * f) / 2 + 0.25
    lathe_y("%s_hoop%d" % (P, k), [(0.012 * math.cos(2 * PI * j / 6), 0.38 + 0.012 * math.sin(2 * PI * j / 6)) for j in range(7)],
            (0, yy + 0.25, z), ["M_Steel"], G, seg=16, axis='Z')
abox(P + "_plat", (1.8, 1.8, 0.06), (0, 0, z1 - 0.05), "M_Plain", G, bevel=0.01)
for i in range(4):
    a = PI / 4 + i * PI / 2
    rod("%s_platrail%d" % (P, i), Vector((math.cos(a) * 1.27, math.sin(a) * 1.27, z1 + 0.9)), Vector((math.cos(a + PI / 2) * 1.27, math.sin(a + PI / 2) * 1.27, z1 + 0.9)), 0.02, "M_Steel", G, seg=6)
    rod("%s_platpost%d" % (P, i), Vector((math.cos(a) * 1.27, math.sin(a) * 1.27, z1)), Vector((math.cos(a) * 1.27, math.sin(a) * 1.27, z1 + 0.9)), 0.02, "M_Steel", G, seg=6)
abox(P + "_drive", (0.8, 0.8, 0.5), (0, 0, z1 + 0.25), "M_Body", G, bevel=0.03)
rod(P + "_cabletray", Vector((-0.5, -0.5, z0)), Vector((-0.3, -0.3, z1)), 0.05, "M_Black", G, seg=6)
weather(gen_weather([(-HX - 1.0, 1.5, 1.6)], 0.5))

# ---------------------------------------------------------------- rotating head (radar_yaw): 2 rows x 8 Yagi arrays
GR = P + "_head"
RY = gB(0, 15.08, 0)
lathe_y(P + "_rot", [(0.0, 0.0), (0.0, 0.45), (0.2, 0.45), (0.25, 0.2), (0.25, 0.0)], tuple(RY + Vector((0, 0, 0.42))), ["M_Plain"], GR, seg=24, axis='Z')
acyl(P + "_coax", 0.18, 0.6, tuple(RY + Vector((0, 0, 0.9))), "M_Dark", GR, seg=14)
AW = 4.54
zA, zB_ = 16.3, 18.5                         # Yagi row heights
yb0, yb1 = 1.55, -1.1                        # boom rear (Blender +Y) -> front (Blender -Y, glTF +Z)
# main truss: centre post + two horizontal box trusses + diagonal stays
rod(P + "_cpost", RY + Vector((0, 0, 0.6)), Vector((0, 0.9, 19.5)), 0.09, "M_Plain", GR, seg=10)
for zz in (zA, zB_):
    for dz in (-0.18, 0.18):
        rod("%s_tr%d_%d" % (P, int(zz), int(dz * 100)), Vector((-AW, 0.9, zz + dz)), Vector((AW, 0.9, zz + dz)), 0.04, "M_Plain", GR, seg=8)
    for k in range(17):
        x = -AW + 2 * AW * k / 16
        rod("%s_trv%d_%d" % (P, int(zz), k), Vector((x, 0.9, zz - 0.18)), Vector((x, 0.9, zz + 0.18)), 0.02, "M_Plain", GR, seg=5)
        if k < 16:
            rod("%s_trd%d_%d" % (P, int(zz), k), Vector((x, 0.9, zz - 0.18)), Vector((x + 2 * AW / 16, 0.9, zz + 0.18)), 0.016, "M_Plain", GR, seg=5)
for sx in (1, -1):
    rod(P + "_stay%d" % sx, RY + Vector((0, 0, 0.7)), Vector((sx * AW * 0.75, 0.9, zA - 0.18)), 0.05, "M_Plain", GR, seg=8)
    rod(P + "_stay2%d" % sx, Vector((0, 0.9, 19.4)), Vector((sx * AW * 0.75, 0.9, zB_ + 0.18)), 0.04, "M_Plain", GR, seg=8)
    rod(P + "_vtie%d" % sx, Vector((sx * AW * 0.98, 0.9, zA)), Vector((sx * AW * 0.98, 0.9, zB_)), 0.035, "M_Plain", GR, seg=8)
    rod(P + "_vtiem%d" % sx, Vector((sx * AW * 0.5, 0.9, zA)), Vector((sx * AW * 0.5, 0.9, zB_)), 0.03, "M_Plain", GR, seg=8)
NE = 11
for zz in (zA, zB_):
    for k in range(8):
        x = -AW + AW / 8 + 2 * AW * k / 8
        rod("%s_boom%d_%d" % (P, int(zz), k), Vector((x, yb0, zz)), Vector((x, yb1, zz)), 0.025, "M_Steel", GR, seg=6)
        # reflector, driven dipole (with balun box), directors - horizontal elements across the boom
        for e in range(NE):
            y = yb0 - 0.15 - (yb0 - yb1 - 0.2) * e / (NE - 1)
            half = 0.5 if e == 0 else (0.46 if e == 1 else 0.42 - 0.012 * e)
            rod("%s_el%d_%d_%d" % (P, int(zz), k, e), Vector((x - half, y, zz)), Vector((x + half, y, zz)), 0.008 if e > 1 else 0.012, "M_Chrome", GR, seg=4)
        abox("%s_balun%d_%d" % (P, int(zz), k), (0.08, 0.06, 0.1), (x, yb0 - 0.4, zz - 0.06), "M_Dark", GR, bevel=0.005)
        rod("%s_feed%d_%d" % (P, int(zz), k), Vector((x, yb0 - 0.4, zz - 0.1)), Vector((x * 0.5, 0.9, zz - 0.18)), 0.008, "M_Black", GR, seg=4)
# IFF antenna on top + warning beacon
rod(P + "_iffb", Vector((0, 0.9, 19.5)), Vector((0, 0.2, 19.55)), 0.03, "M_Plain", GR, seg=6)
abox(P + "_iff", (2.4, 0.1, 0.3), (0, 0.2, 19.55), "M_Radome", GR, bevel=0.02)
acyl(P + "_beacon", 0.06, 0.1, (0, 0.9, 19.6), "M_NavRed", GR, seg=10, dirt=False)

# ---------------------------------------------------------------- hierarchy + export
rt = root("root")
group_under(G, P + "_base_geo", rt)
pivot("launcher_pitch", tuple(gB(0, 2.0, 0)), rt)
ry = pivot("radar_yaw", tuple(RY), rt)
group_under(GR, "radar_yaw_geo", ry)
rw_export("/home/user/models/rw_radar_post.glb", "/home/user/models/rw_radar_post.blend", "RW_RadarPost")
