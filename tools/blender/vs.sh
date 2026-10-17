#!/bin/bash
# viewport screenshot through MCP: vs.sh <out.png> <yaw> <pitch> <dist> <tx> <ty> <tz> [SOLID|MATERIAL] [lens]
cd /home/user/tools/blender
OUT=$1; Y=$2; P=$3; D=$4; TX=$5; TY=$6; TZ=$7; SH=${8:-MATERIAL}; L=${9:-50}
python3 ../mcp.py code "
exec(open('/home/user/tools/blender/view.py').read())
set_view($Y, $P, $D, ($TX, $TY, $TZ), '$SH')
for area in bpy.context.screen.areas:
    if area.type == 'VIEW_3D':
        area.spaces.active.lens = $L
        area.spaces.active.clip_end = 2000
        area.spaces.active.clip_start = 0.05
        area.spaces.active.shading.show_backface_culling = ('${CULL:-0}' == '1')
" >/dev/null 2>&1
sleep 1
python3 ../mcp.py shot "$OUT" 1000 >/dev/null 2>&1; sleep 0.5; python3 ../mcp.py shot "$OUT" 1000 >/dev/null 2>&1
ls -la "$OUT" | awk '{print $5, $9}'
