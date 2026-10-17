#!/usr/bin/env python3
"""live-tune weapon placement: gun_view.py '<python dict>'  -> merges into the client's config/aiarsenal_view.json"""
import json, sys, os
p = '/home/user/.cache/mc/runs/client/config/aiarsenal_view.json'
d = json.load(open(p)) if os.path.exists(p) else {}
d.update(eval(sys.argv[1]))
json.dump(d, open(p, 'w'), indent=1)
