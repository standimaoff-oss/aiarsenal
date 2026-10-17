#!/usr/bin/env python3
"""CI driver of a real live Blender-MCP socket, not bpy/headless export."""
import json,os,socket,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'verification';(OUT/'blender').mkdir(parents=True,exist_ok=True)
LOG=OUT/'logs/mcp-commands.jsonl';LOG.parent.mkdir(parents=True,exist_ok=True)
def call(kind,**params):
    with socket.create_connection(('127.0.0.1',9876),timeout=180) as conn:
        conn.settimeout(300);conn.sendall(json.dumps({'type':kind,'params':params}).encode());data=b''
        while True:
            chunk=conn.recv(65536)
            if not chunk:raise RuntimeError('MCP disconnected')
            data+=chunk
            try:result=json.loads(data);break
            except json.JSONDecodeError:continue
    with LOG.open('a') as f:f.write(json.dumps({'type':kind,'params':params,'response':result})+'\n')
    if result.get('status')=='error' or 'error' in result.get('result',{}):raise RuntimeError(result)
    print(kind,result,flush=True);return result
end=time.monotonic()+120
while True:
    try:
        with socket.create_connection(('127.0.0.1',9876),timeout=1):break
    except OSError:
        if time.monotonic()>end:raise
        time.sleep(1)
call('get_scene_info')
path=str(ROOT/'tools/blender/weapons_stage1c.py')
call('execute_code',code=f"import importlib.util\nspec=importlib.util.spec_from_file_location('stage1c',{path!r})\nstage1c=importlib.util.module_from_spec(spec)\nspec.loader.exec_module(stage1c)\nstage1c.build()\nbpy.app.driver_namespace['stage1c']=stage1c")
for name in ['mosin_pu','k98k_zf39','c96','tt33']:
    call('execute_code',code=f"bpy.app.driver_namespace['stage1c'].viewport({name!r})")
    time.sleep(2)
    call('get_viewport_screenshot',max_size=1400,filepath=str(OUT/'blender'/f'{name}-viewport.png'))
# Re-save with review view, but without hiding item parts in the deliverable.
call('execute_code',code="for sc in bpy.data.scenes:\n    for ob in sc.objects: ob.hide_viewport=False\nbpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath, compress=True)")
assert all((OUT/'blender'/f'{name}-viewport.png').exists() for name in ['mosin_pu','k98k_zf39','c96','tt33'])
