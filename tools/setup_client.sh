#!/usr/bin/env bash
# Minecraft 1.20.1 + Forge 47.4.23 client under Xvfb (TZ §6.4/6.5). usage: setup_client.sh {install|start|run|stop|status}
# Client = ForgeGradle runClient (MDK userdev, same Forge build); auto-joins the local dedicated server.
set -euo pipefail
MC_ROOT=${MC_ROOT:-/home/user/.cache/mc}
MOD_DIR=${MOD_DIR:-/home/user/mods/aiarsenal}
DISPLAY_NUM=${DISPLAY_NUM:-99}
JAVA_HOME=${JAVA_HOME:-$(ls -d $MC_ROOT/jdk/jdk-17* 2>/dev/null | head -1 || true)}
export JAVA_HOME PATH=$JAVA_HOME/bin:$PATH GRADLE_USER_HOME=$MC_ROOT/gradle-home
G="$MOD_DIR/gradlew --project-cache-dir $MC_ROOT/gradle --gradle-user-home $MC_ROOT/gradle-home"

install() {
  sudo apt-get install -y xvfb xdotool x11-apps x11-utils imagemagick libgl1 libglx-mesa0 >/dev/null
  (cd $MOD_DIR && $G downloadAssets -q)
  mkdir -p $MC_ROOT/runs/client
}
xvfb() {
  if ! pgrep -f "Xvfb :$DISPLAY_NUM" >/dev/null; then
    Xvfb :$DISPLAY_NUM -screen 0 800x600x24 -nolisten tcp >/tmp/xvfb.log 2>&1 &
    sleep 2
  fi
}
run() { # foreground
  xvfb
  export DISPLAY=:$DISPLAY_NUM LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
  cd $MOD_DIR && exec $G runClient -PautoJoin=${JOIN:-127.0.0.1:25565} -Dorg.gradle.jvmargs="-Xmx384m -XX:+UseSerialGC"
}
start() { setsid nohup "$0" run >$MC_ROOT/runs/client/console.log 2>&1 </dev/null & }
stop() { pkill -f 'net.minecraftforge.bootstrap|forgeclientuserdev|GradleStart|runClient' || true; }
status() { pgrep -f forgeclientuserdev >/dev/null; }
case "${1:-status}" in install) install;; start) start;; run) run;; stop) stop;; status) status;; *) echo "usage"; exit 1;; esac
