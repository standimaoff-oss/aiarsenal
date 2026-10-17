#!/usr/bin/env bash
# usage: screenshot.sh out.png — F2 in game (xdotool), fallback X root grab; verifies "not black" (TZ §8).
set -euo pipefail
OUT=$1; export DISPLAY=${DISPLAY:-:99}
SHOTS=/home/user/.cache/mc/runs/client/screenshots
mkdir -p "$(dirname "$OUT")" $SHOTS
before=$(ls -t $SHOTS 2>/dev/null | head -1 || true)
WID=$(xdotool search --name "Minecraft" 2>/dev/null | head -1 || true)
got=""
if [ -n "$WID" ]; then
  xdotool windowfocus --sync "$WID" 2>/dev/null || true
  xdotool key --window "$WID" F2 || true
  for i in $(seq 1 30); do
    now=$(ls -t $SHOTS 2>/dev/null | head -1 || true)
    if [ -n "$now" ] && [ "$now" != "$before" ]; then sleep 0.5; cp "$SHOTS/$now" "$OUT"; got=f2; break; fi
    sleep 1
  done
fi
[ -z "$got" ] && { import -display "$DISPLAY" -window root "$OUT"; got=import; }
# 800x600, <=400 KB
convert "$OUT" -resize '800x600!' "$OUT"
if [ $(stat -c%s "$OUT") -gt 409600 ]; then convert "$OUT" -quality 95 -define png:compression-level=9 "$OUT"; fi
if [ $(stat -c%s "$OUT") -gt 409600 ]; then convert "$OUT" -colors 4096 "$OUT" || true; fi
read mean k <<< "$(identify -format "%[fx:mean] %k" "$OUT")"
echo "$OUT via $got mean=$mean colors=$k size=$(stat -c%s "$OUT")"
python3 -c "import sys; sys.exit(0 if float('$mean')>0.02 and int('$k')>1000 else 5)" || { echo "BLACK/EMPTY FRAME"; exit 5; }
