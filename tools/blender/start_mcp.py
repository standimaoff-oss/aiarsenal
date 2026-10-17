# Starts the Blender-MCP socket server (addon.py from ahujasid/blender-mcp) inside a live GUI session.
import sys, importlib.util, bpy
import os
_p = os.environ.get("BLENDER_MCP_ADDON", "/home/user/.cache/blender/mcp/addon.py")
if not os.path.exists(_p):
    _p = "/home/user/.config/blender/5.2/scripts/addons/addon.py"
spec = importlib.util.spec_from_file_location("blender_mcp_addon", _p)
m = importlib.util.module_from_spec(spec); sys.modules["blender_mcp_addon"] = m; spec.loader.exec_module(m)
m.register()
def _go():
    if not getattr(bpy.types, "blendermcp_server", None):
        srv = m.BlenderMCPServer(port=9876); srv.start(); bpy.types.blendermcp_server = srv
        print("MCP_SERVER_STARTED", flush=True)
    return None
bpy.app.timers.register(_go, first_interval=1.0)
