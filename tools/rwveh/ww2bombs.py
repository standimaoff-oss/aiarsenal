# Reply 11: free-fall WW2 bombs dropped by the He 111 / Pe-8 (item + falling entity models). Real sizes:
# SC 250 L 1.64 m d 0.37 m (yellow tail band = HE); SC 1000 "Hermann" L 2.58 m d 0.66 m with ring tail;
# FAB-250sv L 1.50 m d 0.29 m; FAB-1000 L 2.50 m d 0.63 m. Nose to +Z, centred on the origin.
exec(open("/home/user/tools/rwveh/craft_common.py").read())
exec(open("/home/user/tools/rwveh/plane_lib.py").read())
SPEC = [("sc250", 1.64, 0.37, "DE", True), ("sc1000", 2.58, 0.66, "DE", True),
        ("fab250sv", 1.50, 0.29, "SU", False), ("fab1000", 2.50, 0.63, "SU", True)]
for bid, L, dia, nat, ring in SPEC:
    reset_scene()
    col_get("BUILD"); col_get("RIG")
    if nat == "DE":
        plane_mats("T_PlRLM7071", "T_PlRLM7071", "T_PlRLM65", "T_PlInteriorDE", frame=(0.17, 0.21, 0.16))
    else:
        plane_mats("T_PlAMT46", "T_PlAMT46", "T_PlAMT6", "T_PlInteriorSU", frame=(0.20, 0.23, 0.15))
    amat("M_BandY", None, 0.55, 0.0, factor=(0.85, 0.66, 0.12, 1))
    amat("M_BandR", None, 0.55, 0.0, factor=(0.62, 0.10, 0.08, 1))
    G = "B_" + bid
    tl = L * 0.36
    bomb_geo(bid, (0, 0, tl * 0.35), L, dia, G, mat="M_Bomb" + nat, axis='Y', ring=ring)
    r = dia / 2
    # colour band at the body/tail joint + suspension lug + fuse boss
    bl = L - tl
    zb = tl * 0.35 - tl / 2 - bl / 2 + bl * 0.10     # kind z of the band, aft part of the body
    lathe_y(bid + "_band", [(-0.03, r * 1.004), (-0.03, r * 1.012), (0.03, r * 1.012), (0.03, r * 1.004)],
            tuple(KV((0, 0, zb + bl * 0.06))), ["M_BandY" if nat == "DE" else "M_BandR"], G, seg=16, axis='Y')
    abox(bid + "_lug", (0.035, 0.07, 0.05), tuple(KV((0, r + 0.02, tl * 0.35 - tl / 2 + 0.05))), "M_Steel", G, bevel=0.005)
    lathe_y(bid + "_fuse", [(-0.025, 0.0), (-0.025, r * 0.18), (0.025, r * 0.18), (0.025, 0.0)],
            tuple(KV((0, 0, tl * 0.35 - tl / 2 - bl / 2 + bl * 0.42))), ["M_Steel"], G, seg=10, axis='Y')
    rt = root("root")
    group_under(G, bid + "_body", rt)
    rw_export("/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb/%s.glb" % bid,
              "/home/user/models/ww2bomb_%s.blend" % bid, "BOMB_" + bid.upper())
