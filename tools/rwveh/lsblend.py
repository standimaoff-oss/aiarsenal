import bpy
for o in sorted(bpy.data.objects, key=lambda o: (o.parent.name if o.parent else "", o.name)):
    nt = sum(len(p.vertices)-2 for p in o.data.polygons) if o.type=='MESH' else 0
    print("%-22s %-6s par=%-18s loc=%s rot=%s tris=%d mats=%s" % (o.name, o.type, o.parent.name if o.parent else "-", tuple(round(v,3) for v in o.location), tuple(round(v,3) for v in o.rotation_euler), nt, [m.name for m in o.data.materials][:8] if o.type=='MESH' else ""))
print("ACTIONS", [a.name for a in bpy.data.actions][:40])
print("COLLS", [c.name for c in bpy.data.collections], "SCENES", [s.name for s in bpy.data.scenes])
