#!/usr/bin/env bash
# usage: start_novnc.sh {start|stop} — x11vnc on :99 + noVNC on 6080 (only on request; costs CPU).
set -euo pipefail
case "${1:-start}" in
  start) command -v x11vnc >/dev/null || sudo apt-get install -y x11vnc novnc websockify >/dev/null
         pgrep x11vnc >/dev/null || x11vnc -display :99 -forever -shared -localhost -rfbport 5900 -bg -o /tmp/x11vnc.log
         pgrep -f 'websockify.*6080' >/dev/null || setsid nohup websockify --web /usr/share/novnc 0.0.0.0:6080 localhost:5900 >/tmp/novnc.log 2>&1 & ;;
  stop)  pkill x11vnc || true; pkill -f 'websockify.*6080' || true ;;
esac
