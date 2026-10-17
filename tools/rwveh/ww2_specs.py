#!/usr/bin/env python3
"""Generates the reply-8 WW2 VehicleSpec entries (Specs.java) from the Blender-space numbers of tools/rwveh/<id>.py.
Blender: front = -Y, +X = vehicle left, Z up.  glTF / vehicle space: (x, z, -y).  Run: python3 ww2_specs.py --apply"""
import sys, re
G = lambda v: (v[0], v[2], -v[1])
def f(x): return ("%.3f" % x).rstrip('0').rstrip('.') .replace('-0', '-0') + 'f' if abs(x) > 1e-6 else '0.0f'
def arr(v): return "{" + ", ".join(f(c) for c in v) + "}"
def arr2(vs): return "new float[][]{" + ", ".join(arr(v) for v in vs) + "}" if vs else "new float[0][]"

# id: W (track span), H (overall height), L (overall incl. gun), HL (hull half length), roof z, driver hatch (x, y),
#     cupola (x, y, z top), TC, TRN, muzzles [main, (second gun / MG) ...]
T = {
 "PZ2":  dict(id="pz2", W=2.28, H=2.15, L=4.81, HL=2.405, roof=1.50, drv=(0.48, -1.85), cup=(0.13, -0.05, 2.10),
             TC=(0.08, -0.30, 1.50), TRN=(0.08, -1.00, 1.74), M=[(-0.02, -2.42, 1.74), (0.21, -1.57, 1.74)]),
 "PZ4":  dict(id="pz4", W=2.88, H=2.68, L=7.02, HL=2.96, roof=1.72, drv=(0.55, -1.95), cup=(0.0, 0.35, 2.62),
             TC=(0.0, -0.45, 1.70), TRN=(0.0, -1.36, 2.05), M=[(0.0, -4.06, 2.05), (0.24, -1.56, 2.03)]),
 "TIGER": dict(id="tiger", W=3.705, H=3.0, L=8.45, HL=3.158, roof=1.86, drv=(0.62, -2.05), cup=(0.62, 0.32, 2.95),
             TC=(0.0, -0.30, 1.86), TRN=(0.0, -1.36, 2.24), M=[(0.0, -5.292, 2.24), (0.30, -1.68, 2.30)]),
 "STURMTIGER": dict(id="sturmtiger", W=3.705, H=2.85, L=6.28, HL=3.158, roof=2.62, drv=(0.62, -2.05), cup=(0.75, -0.95, 2.62),
             TC=(-0.30, -2.18, 1.86), TRN=(-0.30, -2.18, 1.86), M=[(-0.30, -3.14, 1.86)]),
 "MAUS": dict(id="maus", W=3.67, H=3.63, L=10.09, HL=4.515, roof=2.20, drv=(0.75, -3.0), cup=(0.55, 1.40, 3.58),
             TC=(0.0, 1.25, 2.20), TRN=(0.0, -0.62, 2.80), M=[(0.0, -5.575, 2.80), (-0.42, -3.01, 2.82), (0.36, -0.86, 2.85)]),
 "RATTE": dict(id="ratte", W=14.0, H=11.0, L=39.0, HL=17.5, roof=7.0, drv=(0.0, -12.5), cup=(0.0, 0.4, 10.92),
             TC=(0.0, -3.0, 7.0), TRN=(0.0, -7.4, 8.7), M=[(1.85, -21.6, 8.7), (-1.85, -21.6, 8.7), (3.7, -7.4, 11.55)]),
 "T34":  dict(id="t34", W=3.0, H=2.72, L=8.15, HL=3.05, roof=1.55, drv=(0.45, -2.40), cup=(0.45, -0.30, 2.66),
             TC=(0.0, -0.85, 1.55), TRN=(0.0, -1.86, 2.03), M=[(0.0, -5.10, 2.03), (0.30, -2.13, 2.03)]),
 "KV2":  dict(id="kv2", W=3.32, H=3.24, L=6.95, HL=3.375, roof=1.60, drv=(0.0, -2.20), cup=(0.0, 0.40, 3.20),
             TC=(0.0, -0.38, 1.60), TRN=(0.0, -1.43, 2.32), M=[(0.0, -3.70, 2.32), (0.32, -1.78, 2.20)]),
 "IS2":  dict(id="is2", W=3.07, H=2.73, L=9.90, HL=3.385, roof=1.55, drv=(0.0, -2.25), cup=(0.50, -0.90, 2.67),
             TC=(0.0, -1.20, 1.55), TRN=(0.0, -2.20, 2.05), M=[(0.0, -6.515, 2.05), (0.30, -2.80, 2.05)]),
 "KV5":  dict(id="kv5", W=4.0, H=3.3, L=10.45, HL=4.45, roof=1.88, drv=(0.95, -3.0), cup=(0.55, 0.70, 3.21),
             TC=(0.0, 0.15, 1.88), TRN=(0.0, -1.15, 2.42), M=[(0.0, -6.0, 2.42), (0.36, -1.55, 2.42)]),
}
out = []
for K, d in T.items():
    tc, trn = d["TC"], d["TRN"]
    yaw = G(tc)
    pit = G(tuple(trn[i] - tc[i] for i in range(3)))
    tubes = [G(tuple(m[i] - trn[i] for i in range(3))) for m in d["M"]]
    s = d["W"] / 2
    # r9: one person runs the tank from seat 0 = the commander's hatch (best view for driving + aiming); seat 1 = passenger
    seats = [(d["cup"][0], d["cup"][2] - 0.05, -d["cup"][1]), (d["drv"][0], d["roof"] - 0.55, -d["drv"][1])]
    parts = [(d["drv"][0], d["roof"], -d["drv"][1], 0.4 * max(1, d["W"] / 6), 0.25, 0.4 * max(1, d["W"] / 6)),
             (tc[0], (tc[2] + d["H"]) / 2, -tc[1], min(1.2, s * 0.6) * max(1, d["W"] / 7), (d["H"] - tc[2]) / 2, min(1.4, d["HL"] * 0.4) * max(1, d["W"] / 7)),
             (0.0, d["roof"] * 0.6, -d["HL"], s * 0.8, d["roof"] * 0.3, 0.3),
             (0.0, d["roof"] / 2, 0.0, s, d["roof"] / 2, d["HL"])]
    out.append('    public static final VehicleSpec %s = new VehicleSpec("%s", %s, %s, %s, %s, new float[0][],\n'
               '            %s, new String[]{"driver", "gunner"},\n            %s,\n'
               '            new String[]{"door_driver", "door_gunner", "rear", "body"}, %s, new float[0][],\n'
               '            new float[]%s, new float[]%s, new float[0][]);' % (
        K, d["id"], f(min(d["W"], 14.0)), f(d["H"]), f(d["L"]), f(d["H"]), arr2(seats), arr2(parts), arr2(tubes), arr(yaw), arr(pit)))

# BM-13N (Studebaker US6): see tools/rwveh/bm13.py node contract
rx = [-0.70 + 0.2 * i for i in range(8)]
tubes = [(x, 0.16 if s == 0 else -0.16, 5.0) for x in rx for s in (0, 1)]
wheels = [(0.795, 0.475, 2.10, 0.475, 1), (-0.795, 0.475, 2.10, 0.475, 1), (0.855, 0.475, -1.46, 0.475, 0), (-0.855, 0.475, -1.46, 0.475, 0),
          (0.855, 0.475, -2.58, 0.475, 0), (-0.855, 0.475, -2.58, 0.475, 0)]
parts = [(0, 1.2, -1.6, 1.1, 0.5, 1.7), (0, 1.35, 2.3, 0.6, 0.45, 0.8), (0, 1.75, 0.85, 1.0, 0.55, 0.6), (0.98, 1.6, 0.9, 0.2, 0.5, 0.45),
         (-0.98, 1.6, 0.9, 0.2, 0.5, 0.45), (0, 2.55, -0.6, 0.9, 0.35, 2.6), (0, 1.0, -3.2, 1.0, 0.35, 0.3)]
out.append('    public static final VehicleSpec BM13 = new VehicleSpec("bm13", 2.24f, 2.9f, 6.55f, 2.9f, %s,\n'
           '            new float[][]{{0.45f, 1.45f, 0.62f}, {-0.45f, 1.45f, 0.62f}}, new String[]{"driver", "gunner"},\n            %s,\n'
           '            new String[]{"body", "hood", "cab", "door_driver", "door_gunner", "launcher", "rear"}, %s,\n'
           '            new float[][]{{0.95f, -3.05f, 0.45f}, {-0.95f, -3.05f, 0.45f}}, new float[]{0.0f, 1.18f, -1.95f}, new float[]{0.0f, 1.32f, -1.0f}, new float[0][]);'
           % (arr2(wheels), arr2(parts), arr2(tubes)))
code = "\n    // ---------------------------------------------------------------- WW2 (reply 8), generated by tools/rwveh/ww2_specs.py\n" + "\n".join(out) + "\n"
if "--apply" in sys.argv:
    p = "/home/user/mods/aiarsenal/src/main/java/ru/aiforge/realwar/vehicle/Specs.java"
    s = open(p).read()
    s = re.sub(r"\n    // -+ WW2 \(reply 8\).*?(?=\n\n    /\*\* RS-24)", "", s, flags=re.S)
    anchor = "\n\n    /** RS-24 Yars TEL"
    assert anchor in s
    s = s.replace(anchor, code.rstrip("\n") + anchor, 1)
    open(p, "w").write(s)
    print("applied", len(out), "specs")
else:
    print(code)
