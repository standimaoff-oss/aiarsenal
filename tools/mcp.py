#!/usr/bin/env python3
"""Minimal Blender-MCP client (same wire protocol as `uvx blender-mcp`):
JSON command -> TCP localhost:9876 -> addon.py inside a live Blender GUI session.

usage:
  mcp.py ping
  mcp.py scene                       # get_scene_info
  mcp.py object <name>               # get_object_info
  mcp.py exec <file.py> [more.py]    # execute_code (files are concatenated)
  mcp.py shot <out.png> [max_size]   # get_viewport_screenshot
  mcp.py export <out.glb> [names..]  # export_scene
"""
import json, socket, sys, time

HOST, PORT = "localhost", 9876


def call(cmd_type, params=None, timeout=1800):
    s = socket.create_connection((HOST, PORT), timeout=30)
    s.settimeout(timeout)
    s.sendall(json.dumps({"type": cmd_type, "params": params or {}}).encode("utf-8"))
    buf = b""
    while True:
        chunk = s.recv(65536)
        if not chunk:
            break
        buf += chunk
        try:
            res = json.loads(buf.decode("utf-8"))
            break
        except Exception:
            continue
    s.close()
    return res


def main():
    a = sys.argv[1:]
    if not a:
        print(__doc__); sys.exit(1)
    op = a[0]
    t0 = time.time()
    if op == "ping":
        r = call("get_scene_info")
    elif op == "scene":
        r = call("get_scene_info")
    elif op == "object":
        r = call("get_object_info", {"name": a[1]})
    elif op == "exec":
        code = "\n".join(open(f, encoding="utf-8").read() for f in a[1:])
        r = call("execute_code", {"code": code})
    elif op == "code":
        r = call("execute_code", {"code": a[1]})
    elif op == "shot":
        r = call("get_viewport_screenshot", {"filepath": a[1], "max_size": int(a[2]) if len(a) > 2 else 800})
    elif op == "export":
        r = call("export_scene", {"filepath": a[1], "format": "glb", "object_names": a[2:] or None})
    else:
        print("unknown op"); sys.exit(1)
    if isinstance(r, dict) and r.get("status") == "success" and isinstance(r.get("result"), dict) and "result" in r["result"]:
        print(r["result"]["result"])
    else:
        print(json.dumps(r, ensure_ascii=False, indent=1)[:20000])
    ok = isinstance(r, dict) and r.get("status") != "error"
    print(f"[mcp] {op} {'OK' if ok else 'ERROR'} in {time.time()-t0:.1f}s", file=sys.stderr)
    sys.exit(0 if ok else 2)


if __name__ == "__main__":
    main()
