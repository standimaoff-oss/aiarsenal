#!/bin/bash
# one rcon call per line of stdin (rcon.py joins its args into ONE command)
while IFS= read -r c; do [ -z "$c" ] && continue; out=$(python3 /home/user/tools/rcon.py --password bm21test "$c" 2>&1); [ -n "$out" ] && echo "[$c] $out"; done
