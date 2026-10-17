# Arado Ar 196 A-3 shipboard floatplane (Bismarck carried four): 11.0 m, span 12.44 m, BMW 132K 960 hp,
# 2 x 20 mm MG FF + 1 x 7.92 mm MG 17 forward, twin MG 15 aft, 2 x SC 50 bombs. Same geometry as the catapult aircraft
# on the battleship (bis_lib.arado). Nodes: prop (spins about glTF Z). Frame converted to glTF: nose = +Z.
exec(open("/home/user/tools/rwveh/bis_lib.py").read())
reset_scene()
col_get("BUILD"); col_get("RIG")
ww2_mats()
ar_mats()
hub = arado("AR", "AR_body", "AR_prop")
weather(gen_weather([(0.0, -4.2, 2.6)], 0.9))
rt = root("root")
group_under("AR_body", "ar196_body", rt)
group_under("AR_prop", "prop_geo", pivot("prop", tuple(hub), rt))
rw_export("/home/user/.cache/rwveh/raw/ar196.glb", "/home/user/models/rw_ar196.blend", "Ar196")
