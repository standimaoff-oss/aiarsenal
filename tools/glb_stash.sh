#!/usr/bin/env bash
# The binary GLB assets live ONCE in the workspace: inside release/aiarsenal-1.0.0.jar (128 MB snapshot cap).
#   restore : unpack every *.glb from the jar into src/main/resources that is missing there (build.sh calls this)
#   strip   : delete src *.glb files whose CRC32 matches the jar entry (only true duplicates are removed)
# Models can always be regenerated from the .blend files in models/ (tools/rwveh/*.py, tools/blender/*).
set -euo pipefail
JAR=/home/user/release/aiarsenal-1.0.0.jar
RES=/home/user/mods/aiarsenal/src/main/resources
cmd=${1:-restore}
python3 - "$cmd" "$JAR" "$RES" <<'PY'
import sys, zipfile, zlib, os
cmd, jar, res = sys.argv[1:4]
if not os.path.exists(jar):
    print("glb_stash: no jar, nothing to do"); sys.exit(0)
z = zipfile.ZipFile(jar)
n = kept = 0
for i in z.infolist():
    if not i.filename.endswith('.glb'):
        continue
    p = os.path.join(res, i.filename)
    if cmd == 'restore':
        if not os.path.exists(p):
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, 'wb') as f: f.write(z.read(i))
            n += 1
    elif cmd == 'strip' and os.path.exists(p):
        if zlib.crc32(open(p, 'rb').read()) & 0xffffffff == i.CRC:
            os.remove(p); n += 1
        else:
            kept += 1
print("glb_stash %s: %d files (%d differing kept)" % (cmd, n, kept))
PY
