# r19: R-187P1 "Azart" handheld VHF tactical radio (Russian Army, 2012+), the fire-support radio item.
# Real size: body 70 x 40 x 190 mm with the battery, 0.9 kg; flexible whip antenna ~ 230 mm. Olive-drab plastic body,
# monochrome LCD + 4x3 keypad on the front, channel / volume knobs and the antenna socket on the top, large PTT on the left.
# Blender frame: Z up, front faces -Y, 1 unit = 1 m. Run: tools/rwveh/run.sh tools/blender/radio.py
reset_scene()
col_get("BUILD"); col_get("RIG")
amat("M_RBody", None, 0.75, 0.0, factor=(0.21, 0.23, 0.17, 1))
amat("M_RBatt", None, 0.8, 0.0, factor=(0.16, 0.17, 0.13, 1))
amat("M_RRub", None, 0.9, 0.0, factor=(0.05, 0.05, 0.05, 1))
amat("M_RKey", None, 0.7, 0.0, factor=(0.30, 0.31, 0.28, 1))
amat("M_RLcd", None, 0.3, 0.0, factor=(0.42, 0.50, 0.40, 1), emissive=(0.12, 0.16, 0.10, 1))
amat("M_RMetal", None, 0.4, 0.8, factor=(0.55, 0.55, 0.55, 1))
amat("M_ROrange", None, 0.6, 0.0, factor=(0.85, 0.40, 0.08, 1))
amat("M_RWhite", None, 0.6, 0.0, factor=(0.85, 0.85, 0.80, 1))
G = "RAD"
W, D, HB, HT = 0.070, 0.040, 0.055, 0.135      # width, depth, battery height, transceiver height
abox("rad_batt", (W - 0.002, D + 0.002, HB), (0, 0, HB / 2), "M_RBatt", G, bevel=0.006)
abox("rad_body", (W, D, HT), (0, 0, HB + HT / 2), "M_RBody", G, bevel=0.007)
# battery latch + ribs
abox("rad_latch", (0.030, 0.006, 0.010), (0, -D / 2 - 0.002, HB - 0.006), "M_RRub", G, bevel=0.002)
for k in range(4):
    abox("rad_brib%d" % k, (W - 0.006, 0.003, 0.003), (0, -D / 2 - 0.001, 0.010 + k * 0.009), "M_RBatt", G, bevel=0.0)
# front: LCD in a bezel, 4 x 3 keypad, speaker grille
yf = -D / 2 - 0.0015
abox("rad_bezel", (0.052, 0.003, 0.030), (0, yf, HB + HT - 0.026), "M_RRub", G, bevel=0.002)
abox("rad_lcd", (0.044, 0.002, 0.022), (0, yf - 0.0015, HB + HT - 0.026), "M_RLcd", G, bevel=0.0, dirt=False)
for r in range(4):
    for c in range(3):
        abox("rad_key%d_%d" % (r, c), (0.012, 0.004, 0.007), (-0.016 + c * 0.016, yf - 0.001, HB + HT - 0.052 - r * 0.011),
             "M_ROrange" if (r, c) == (0, 2) else "M_RKey", G, bevel=0.0015, dirt=False)
for k in range(6):
    abox("rad_grill%d" % k, (0.040, 0.002, 0.0022), (0, yf, HB + 0.012 + k * 0.0045), "M_RRub", G, bevel=0.0, dirt=False)
abox("rad_label", (0.030, 0.0015, 0.005), (0, yf - 0.0005, HB + HT - 0.006), "M_RWhite", G, bevel=0.0, dirt=False)
# top: antenna socket + whip, channel selector, volume knob, accessory connector with cap
zt = HB + HT
acyl("rad_sock", 0.008, 0.016, (0.018, 0.004, zt + 0.008), "M_RMetal", G, axis=2, seg=14)
acyl("rad_ant0", 0.0065, 0.030, (0.018, 0.004, zt + 0.031), "M_RRub", G, axis=2, seg=12)
rod("rad_whip", Vector((0.018, 0.004, zt + 0.045)), Vector((0.018, 0.004, zt + 0.235)), 0.0045, "M_RRub", G, seg=10, r2=0.0028)
s = bm_sphere(0.0042, Vector((0.018, 0.004, zt + 0.237)), 10, 6)
aobj("rad_tip", s, "M_RRub", G, smooth=True, dirt=False)
acyl("rad_ch", 0.009, 0.012, (-0.016, 0.004, zt + 0.006), "M_RRub", G, axis=2, seg=16)
abox("rad_chm", (0.002, 0.007, 0.002), (-0.016, -0.003, zt + 0.0125), "M_RWhite", G, bevel=0.0, dirt=False)
acyl("rad_vol", 0.006, 0.010, (-0.002, 0.008, zt + 0.005), "M_RRub", G, axis=2, seg=14)
acyl("rad_acc", 0.0065, 0.006, (0.002, -0.009, zt + 0.003), "M_RRub", G, axis=2, seg=12)
# left side: large ribbed PTT + two small buttons; right side: accessory flap
xl = W / 2 + 0.0015
abox("rad_ptt", (0.005, 0.022, 0.034), (xl, 0, HB + HT - 0.060), "M_RRub", G, bevel=0.002)
for k in range(5):
    abox("rad_pttr%d" % k, (0.002, 0.020, 0.0018), (xl + 0.003, 0, HB + HT - 0.074 + k * 0.007), "M_RRub", G, bevel=0.0, dirt=False)
for k in range(2):
    abox("rad_sb%d" % k, (0.004, 0.010, 0.008), (xl, 0, HB + HT - 0.030 + k * 0.012), "M_RKey", G, bevel=0.0015)
abox("rad_flap", (0.003, 0.026, 0.040), (-xl, 0, HB + HT - 0.050), "M_RRub", G, bevel=0.002)
# back: belt clip
abox("rad_clipb", (0.030, 0.004, 0.060), (0, D / 2 + 0.002, HB + HT - 0.040), "M_RMetal", G, bevel=0.002)
abox("rad_clip", (0.026, 0.003, 0.070), (0, D / 2 + 0.006, HB + HT - 0.050), "M_RRub", G, rot=(math.radians(4), 0, 0), bevel=0.002)
auto_attach([G], bracket_groups=())
rt = root("root")
group_under(G, "radio_body", rt)
rw_export("/home/user/.cache/rwveh/raw/radio.glb", "/home/user/models/radio.blend", "RADIO")
