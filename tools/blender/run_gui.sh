#!/bin/bash
# Live Blender GUI session (Xvfb :98) with the MCP server on localhost:9876
pgrep -f "Xvfb :98" >/dev/null || { Xvfb :98 -screen 0 1600x1000x24 -nolisten tcp >/tmp/xvfb98.log 2>&1 & sleep 2; }
export DISPLAY=:98 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
exec /home/user/.cache/blender/blender-5.2.2-linux-x64/blender ${1:-} --python /home/user/tools/blender/start_mcp.py
