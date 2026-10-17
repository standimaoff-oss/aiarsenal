"""r12 ordnance: BGM-71F TOW-2B missile (Bradley) and MO.1.01.04M 220 mm thermobaric rocket (TOS-1A).
   blender -b --factory-startup --python tools/blender/ordnance_r12.py
Re-uses the helper section of handheld.py (materials, cyl/box/fins/join). Nose = Blender -Y (= glTF +Z), centred on origin."""
import bpy, os
SRC = open("/home/user/tools/blender/handheld.py").read()
exec(SRC[:SRC.index("# ================================================================== RPG-7")])


def tow_missile():
    scene("TOW_M")
    r = root("TOW_M")
    P = []
    L = 1.17                                                                                    # BGM-71F length, 152 mm body
    P.append(cyl(0.076, 0.80, (0, 0.05, 0), "OliveDark", v=28))                                 # main body
    P.append(cyl(0.076, 0.16, (0, -0.43, 0), "OliveDark", r2=0.062, v=28))                     # forward taper
    P.append(cyl(0.062, 0.05, (0, -0.535, 0), "Black", r2=0.045, v=28))                        # blunt EFP-sensor nose
    P.append(cyl(0.020, 0.02, (0, -0.565, 0), "Glass", v=12))                                  # laser/magnetic sensor window
    P.append(cyl(0.077, 0.018, (0, -0.22, 0), "Tan", v=28, smooth=False))                      # yellow HE band (tan stencil)
    P.append(cyl(0.077, 0.012, (0, 0.10, 0), "Black", v=28, smooth=False))
    P.append(cyl(0.079, 0.08, (0, -0.02, 0), "Gunmetal", v=28))                                # motor nozzle ring (side-venting)
    for i in range(2):                                                                          # twin lateral flight-motor nozzles
        x = 0.085 if i == 0 else -0.085
        P.append(cyl(0.022, 0.05, (x, -0.02, 0), "Black", rot=X, v=12))
    P += fins(4, -0.08, 0.14, 0.11, 0.076, "Black", thick=0.004, phase=45)                     # mid wings (spring-out)
    P += fins(4, 0.50, 0.07, 0.07, 0.076, "Black", thick=0.004, phase=0)                       # tail control surfaces
    P.append(cyl(0.05, 0.05, (0, 0.475, 0), "Gunmetal", v=20, r2=0.06))                        # boost-motor tail
    P.append(cyl(0.010, 0.06, (0.03, 0.53, 0), "Gunmetal", v=8))                                # wire-dispenser spools
    P.append(cyl(0.010, 0.06, (-0.03, 0.53, 0), "Gunmetal", v=8))
    P.append(cyl(0.012, 0.03, (0, 0.53, 0.035), "Glass", v=8))                                  # IR beacon/xenon lamp
    join(P, "TOW_M_Body", r)


def rocket_tos220():
    scene("TOS220")
    r = root("TOS220")
    P = []
    R = 0.110                                                                                    # 220 mm, 3.72 m
    P.append(cyl(R, 2.30, (0, 0.38, 0), "OliveDark", v=32))                                     # motor case
    P.append(cyl(R, 0.70, (0, -1.12, 0), "Olive", v=32))                                        # thermobaric warhead
    P.append(cyl(R, 0.36, (0, -1.65, 0), "Olive", r2=0.040, v=32))                              # ogive
    P.append(cyl(0.040, 0.05, (0, -1.855, 0), "Gunmetal", r2=0.020, v=16))                     # nose fuze
    P.append(cyl(R + 0.002, 0.03, (0, -0.78, 0), "Black", v=32, smooth=False))                 # joint rings
    P.append(cyl(R + 0.002, 0.03, (0, -1.30, 0), "Tan", v=32, smooth=False))                   # stencil band
    P.append(cyl(R + 0.004, 0.04, (0, 1.10, 0), "Gunmetal", v=32, smooth=False))
    P.append(cyl(0.012, 0.10, (0, -0.30, R + 0.006), "Gunmetal", rot=Y, v=8))                   # launch shoe / guide pin
    P.append(cyl(0.012, 0.10, (0, 1.20, R + 0.006), "Gunmetal", rot=Y, v=8))
    P += fins(4, 1.62, 0.17, 0.30, 0.095, "OliveDark", thick=0.006, phase=45)                   # wrap-around tail fins (deployed)
    P.append(cyl(0.095, 0.30, (0, 1.62, 0), "OliveDark", v=28))                                 # fin hub
    P.append(cyl(0.085, 0.12, (0, 1.80, 0), "Black", r2=0.095, v=28))                           # nozzle
    P.append(cyl(0.060, 0.02, (0, 1.855, 0), "Gunmetal", v=20))
    join(P, "TOS220_Body", r)


def shells25():
    """Icon-only prop (not exported): a short 25x137 mm belt, HEI-T + APFSDS-T."""
    scene("SHELL25")
    r = root("SHELL25")
    P = []
    for i in range(5):
        x = (i - 2) * 0.034
        P.append(cyl(0.0135, 0.137, (x, 0, 0), "Tan", v=20))                                     # brass case
        P.append(cyl(0.0145, 0.006, (x, 0.0685, 0), "Tan", v=20, smooth=False))                  # rim
        if i % 2 == 0:
            P.append(cyl(0.0125, 0.06, (x, -0.098, 0), "OliveDark", r2=0.004, v=20))              # HEI-T
        else:
            P.append(cyl(0.008, 0.07, (x, -0.103, 0), "Gunmetal", r2=0.003, v=16))                # APFSDS dart
            P.append(cyl(0.0125, 0.02, (x, -0.075, 0), "Black", v=16))                            # sabot
        P.append(box((0.03, 0.03, 0.006), (x, 0.03, 0.016), "Gunmetal", bevel=0.0))               # link
    join(P, "SHELL25_Body", r)


tow_missile(); rocket_tos220(); shells25()
OUTN = {"TOW_M": "tow_missile", "TOS220": "rocket_tos220"}
for sname, fname in OUTN.items():
    sc = SCENES[sname]
    bpy.context.window.scene = sc
    tris = sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in sc.objects if o.type == "MESH")
    ys = [(o.matrix_world @ Vector(c)).y for o in sc.objects if o.type == "MESH" for c in o.bound_box]
    path = os.path.join(GLB, fname + ".glb")
    with bpy.context.temp_override(scene=sc, view_layer=sc.view_layers[0]):
        bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", export_yup=True, export_apply=True, export_animations=False,
                                  export_extras=False, export_cameras=False, export_lights=False, use_active_scene=True)
    print("ORD", fname, tris, os.path.getsize(path), "len %.3f y %.3f..%.3f" % (max(ys) - min(ys), min(ys), max(ys)))
for s in list(bpy.data.scenes):
    if s.name not in SCENES:
        bpy.data.scenes.remove(s)
bpy.context.window.scene = SCENES["TOW_M"]
bpy.ops.wm.save_as_mainfile(filepath=ROOT + "/models/ordnance_r12.blend", compress=True)
print("ORD_DONE")
