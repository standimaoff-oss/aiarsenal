#!/usr/bin/env bash
# Forge 1.20.1-47.4.23 dedicated server (TZ §6.1). usage: setup_server.sh {install|start|run|stop|restart|status|mods|deploy}
set -euo pipefail
MC_ROOT=${MC_ROOT:-/home/user/.cache/mc}
SRV=$MC_ROOT/server
FORGE=1.20.1-47.4.23
MODID=${MODID:-aiarsenal}
MOD_DIR=${MOD_DIR:-/home/user/mods/aiarsenal}
LOGS=/home/user/logs
RCON_PW=${RCON_PASSWORD:-bm21test}
JAVA_HOME=${JAVA_HOME:-$(ls -d $MC_ROOT/jdk/jdk-17* 2>/dev/null | head -1 || true)}
export JAVA_HOME PATH=$JAVA_HOME/bin:$PATH

dl() { # url out — 3 attempts, 5 s apart
  for i in 1 2 3; do curl -fsSL "$1" -o "$2" && return 0; sleep 5; done; return 1; }

install_java() {
  if [ -x "$JAVA_HOME/bin/java" ]; then return 0; fi
  # openjdk-17 is not packaged in Debian 13 (trixie) -> Temurin tarball
  if ! sudo apt-get install -y openjdk-17-jdk-headless >/dev/null 2>&1; then
    mkdir -p $MC_ROOT/jdk
    dl "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" /tmp/jdk17.tgz
    tar xzf /tmp/jdk17.tgz -C $MC_ROOT/jdk && rm /tmp/jdk17.tgz
  fi
}

install() {
  install_java
  export JAVA_HOME=$(ls -d $MC_ROOT/jdk/jdk-17* | head -1) PATH=$(ls -d $MC_ROOT/jdk/jdk-17* | head -1)/bin:$PATH
  mkdir -p $SRV/mods
  if [ ! -f $SRV/libraries/net/minecraftforge/forge/$FORGE/unix_args.txt ]; then
    dl "https://maven.minecraftforge.net/net/minecraftforge/forge/$FORGE/forge-$FORGE-installer.jar" $SRV/installer.jar
    (cd $SRV && java -jar installer.jar --installServer >$SRV/installer.log 2>&1) && rm -f $SRV/installer.jar $SRV/installer.jar.log
  fi
  echo "eula=true" > $SRV/eula.txt
  cat > $SRV/server.properties <<P
online-mode=false
enable-rcon=true
rcon.password=$RCON_PW
rcon.port=25575
server-ip=127.0.0.1
level-type=minecraft\:flat
level-seed=21122
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
view-distance=4
simulation-distance=4
max-players=5
spawn-protection=0
spawn-monsters=false
gamemode=creative
motd=AI Forge test server
P
  deploy
}

deploy() { # copy the freshly built jar (no -sources/-javadoc)
  mkdir -p $SRV/mods; rm -f $SRV/mods/$MODID-*.jar
  cp $(ls $MOD_DIR/build/libs/$MODID-*.jar | grep -v -e sources -e javadoc | head -1) $SRV/mods/
}

pid() { pgrep -f "forge/$FORGE/unix_args.txt" || true; }

run() { # foreground
  cd $SRV; mkdir -p $LOGS; ln -sfn $SRV/logs/latest.log $LOGS/$MODID-server.log
  exec java -Xmx768M -Xms512M -XX:+UseSerialGC -Dfml.earlyprogresswindow=false \
    @libraries/net/minecraftforge/forge/$FORGE/unix_args.txt nogui
}

start() {
  [ -n "$(pid)" ] && { echo "already running"; return 0; }
  rm -rf $SRV/crash-reports
  setsid nohup "$0" run >$SRV/console.log 2>&1 < /dev/null &
  for i in $(seq 1 150); do
    grep -q 'Done (' $SRV/logs/latest.log 2>/dev/null && [ $SRV/logs/latest.log -nt $SRV/console.log -o 1 ] && { grep 'Done (' $SRV/logs/latest.log | tail -1; return 0; }
    sleep 2
  done
  echo "server did not reach Done in 300 s"; return 1
}

stop() {
  local p; p=$(pid); [ -z "$p" ] && return 0
  python3 /home/user/tools/rcon.py --log "" stop >/dev/null 2>&1 || kill $p
  for i in $(seq 1 30); do [ -z "$(pid)" ] && return 0; sleep 1; done
  kill -9 $(pid) 2>/dev/null || true
}

case "${1:-status}" in
  install) install ;;
  deploy) deploy ;;
  run) run ;;
  start) start ;;
  stop) stop ;;
  restart) stop; deploy; start ;;
  status) [ -n "$(pid)" ] ;;
  mods) grep -oE "$MODID@[0-9.]+|Found mod file $MODID-[0-9.]+\.jar|mod file .*$MODID" $SRV/logs/debug.log | sort -u; python3 /home/user/tools/rcon.py --log "" forge mods 2>/dev/null || true ;;
  *) echo "usage: $0 {install|start|run|stop|restart|status|mods|deploy}"; exit 1 ;;
esac
