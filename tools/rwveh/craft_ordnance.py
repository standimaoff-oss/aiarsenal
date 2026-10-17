# Flying ordnance for the helicopters / submarine: one node per CraftOrdnance.Type, centred at the origin, nose -> -Y
# (glTF +Z). Nodes: vikhr (9K121, wings deployed), hellfire (AGM-114), s8 (S-8KOM, fins open), hydra (Hydra 70), torpedo (53 cm).
exec(open("/home/user/tools/rwveh/craft_common.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
craft_mats()
amat("M_Torp", None, 0.5, 0.3, factor=(0.12, 0.13, 0.13, 1))

def fins(tag, grp, y, r, span, chord, n=4, mat="M_Olive", phase=45, th=0.008):
    for k in range(n):
        a = math.radians(phase + 360 / n * k)
        abox("%s_f%d" % (tag, k), (th, chord, span), (math.cos(a) * (r + span / 2), y, math.sin(a) * (r + span / 2)), mat, grp,
             rot=(0, -(a - PI / 2), 0), bevel=0.0)

# Vikhr: 2.75 m, d 130 mm, canard nose, 4 rear wings
G = "O_vikhr"
lathe_y("vk_body", [(-1.375, 0.0), (-1.33, 0.03), (-1.2, 0.055), (-1.0, 0.065), (1.3, 0.065), (1.375, 0.05), (1.375, 0.0)], (0, 0, 0), ["M_Olive"], G, seg=14)
lathe_y("vk_band", [(-0.05, 0.066), (0.05, 0.066)], (0, -0.4, 0), ["M_Red"], G, seg=14)
fins("vk_w", G, 1.05, 0.065, 0.16, 0.38)
fins("vk_c", G, -0.95, 0.06, 0.06, 0.12, phase=45)
acyl("vk_noz", 0.04, 0.04, (0, 1.39, 0), "M_Nozzle", G, axis=1, seg=10)
# Hellfire: 1.63 m, d 178 mm
G = "O_hellfire"
lathe_y("hf_body", [(-0.815, 0.0), (-0.8, 0.05), (-0.72, 0.085), (-0.6, 0.089), (0.815, 0.089), (0.815, 0.0)], (0, 0, 0), ["M_Olive"], G, seg=14)
acyl("hf_seek", 0.05, 0.02, (0, -0.81, 0), "M_Lens", G, axis=1, seg=10, dirt=False)
lathe_y("hf_band", [(-0.03, 0.09), (0.03, 0.09)], (0, -0.35, 0), ["M_BladeTip"], G, seg=14)
fins("hf_w", G, 0.55, 0.089, 0.1, 0.24)
fins("hf_c", G, -0.45, 0.085, 0.06, 0.12)
acyl("hf_noz", 0.05, 0.03, (0, 0.83, 0), "M_Nozzle", G, axis=1, seg=10)
# S-8: 1.57 m, d 80 mm, 6 fold-out rear blades
G = "O_s8"
lathe_y("s8_body", [(-0.785, 0.0), (-0.7, 0.025), (-0.5, 0.04), (0.785, 0.04), (0.785, 0.0)], (0, 0, 0), ["M_Rocket"], G, seg=10)
lathe_y("s8_war", [(-0.785, 0.0), (-0.7, 0.026), (-0.5, 0.041), (-0.35, 0.041), (-0.35, 0.0)], (0, 0, 0), ["M_Warhead"], G, seg=10)
fins("s8_f", G, 0.72, 0.04, 0.09, 0.14, n=6, mat="M_Rocket", phase=0)
# Hydra 70: 1.06 m, d 70 mm
G = "O_hydra"
lathe_y("hy_body", [(-0.53, 0.0), (-0.45, 0.022), (-0.3, 0.035), (0.53, 0.035), (0.53, 0.0)], (0, 0, 0), ["M_Rocket"], G, seg=10)
lathe_y("hy_war", [(-0.53, 0.0), (-0.45, 0.023), (-0.3, 0.036), (-0.15, 0.036), (-0.15, 0.0)], (0, 0, 0), ["M_Olive"], G, seg=10)
fins("hy_f", G, 0.48, 0.035, 0.07, 0.1, n=4, mat="M_Rocket", phase=45)
# Torpedo 53 cm: 7.9 m, contra-rotating props + cruciform tail
G = "O_torpedo"
lathe_y("tp_body", [(-3.95, 0.0), (-3.9, 0.12), (-3.75, 0.22), (-3.5, 0.265), (2.9, 0.265), (3.5, 0.19), (3.8, 0.1), (3.85, 0.0)], (0, 0, 0), ["M_Torp"], G, seg=18)
acyl("tp_nose", 0.12, 0.02, (0, -3.9, 0), "M_Steel", G, axis=1, seg=12, dirt=False)
lathe_y("tp_band", [(-0.05, 0.267), (0.05, 0.267)], (0, -2.0, 0), ["M_Red"], G, seg=18)
fins("tp_t", G, 3.45, 0.2, 0.18, 0.4, mat="M_Torp", phase=0, th=0.02)
for k, y in enumerate((3.88, 3.98)):
    for b in range(4 if k == 0 else 5):
        a = 2 * PI * b / (4 if k == 0 else 5) + k * 0.3
        abox("tp_p%d_%d" % (k, b), (0.01, 0.05, 0.16), (math.cos(a) * 0.1, y, math.sin(a) * 0.1), "M_Brass", G, rot=(0.4, -(a - PI / 2), 0), bevel=0.0)
acyl("tp_hub", 0.04, 0.2, (0, 3.93, 0), "M_Brass", G, axis=1, seg=8)

rt = root("root")
for n in ("vikhr", "hellfire", "s8", "hydra", "torpedo"):
    group_under("O_" + n, n, rt)
rw_export("/home/user/.cache/rwveh/raw/craft_ordnance.glb", "/home/user/models/craft_ordnance.blend", "CRAFT_ORDNANCE")
