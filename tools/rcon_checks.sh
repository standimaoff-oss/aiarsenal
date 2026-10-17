#!/usr/bin/env bash
# Self-checking RCON checklist for bm21grad (TZ §6.3 / §10.4). Every step: command + expected substring.
set -uo pipefail
ITER=${ITER:-1}
export RCON_LOG=/home/user/logs/bm21grad-$ITER-rcon.txt
: > "$RCON_LOG"
R="python3 /home/user/tools/rcon.py"
PASS=0; FAIL=0
chk() { # name expect cmd...
  local name=$1 exp=$2; shift 2
  local out; out=$($R --expect "$exp" "$@"); local rc=$?
  if [ $rc -eq 0 ]; then PASS=$((PASS+1)); printf 'PASS  %-28s %s\n' "$name" "${out:0:150}";
  else FAIL=$((FAIL+1)); printf 'FAIL  %-28s rc=%d %s\n' "$name" $rc "${out:0:300}"; fi
}
do_() { $R "$@" >/dev/null; }
V='@e[type=bm21grad:bm21,limit=1]'

do_ kill @e[type=!player]
do_ time set day; do_ weather clear
do_ forceload add -32 -32 32 32
do_ forceload add -24 -330 24 -270
do_ forceload add 40 -330 88 -270
do_ fill -20 -61 -20 20 -61 20 minecraft:stone
chk mod-loaded            "bm21grad"          forge mods
# items registered + display names
for it in bm21 rangefinder rocket_9m22u rocket_9m28f rocket_9m22s rocket_9m218 rocket_9m43; do
  do_ summon item 8 -59 8 "{Item:{id:\"bm21grad:$it\",Count:1b},PickupDelay:32767,Age:-32768}"
  chk "item-$it"          "Test passed"       execute if entity "@e[type=item,nbt={Item:{id:\"bm21grad:$it\"}}]"
done
do_ kill @e[type=item]
# vehicle
chk spawn                 "deployed"          bm21 spawn 0 -60 0 0
chk vehicle-exists        "Test passed, count: 1" execute if entity @e[type=bm21grad:bm21]
chk status-empty          "loaded=0/40"       bm21 status $V
chk load-he               "40"                bm21 load $V 9m22u
chk status-loaded         "loaded=40/40"      bm21 status $V
chk nbt-tubes             "Tubes"             data get entity $V
chk solve-300             "ok=true"           bm21 solve 300
chk solve-out-of-range    "ok=false"          bm21 solve 5000
chk mode-4                "4"                 bm21 mode $V 4
chk target                "Target 0 -61 -300"         bm21 target $V 0 -61 -300
chk aim-manual            "elevation"         bm21 aim $V 90 30
sleep 6
chk laid                  "turret=90.0 elev=30.0" bm21 status $V
chk stow                  ""                  bm21 stow $V
chk stats-before          "detonations="      bm21 stats
chk fire-4                "Fire mission"      bm21 fire $V 4
sleep 30
chk fired-36-left         "loaded=36/40"      bm21 status $V
chk detonations           "detonations=4"     bm21 stats
chk crater                "Test passed"       execute unless blocks -24 -63 -330 24 -61 -270 40 -63 -330 all
# strikes of every type
chk strike-blast          "Strike"            bm21 strike 9m28f 0 -61 -290 1
chk strike-incendiary     "Strike"            bm21 strike 9m22s 10 -61 -300 1
chk strike-cluster        "Strike"            bm21 strike 9m218 -10 -61 -300 1
chk strike-smoke          "Strike"            bm21 strike 9m43 0 -61 -320 1
sleep 25
chk smoke-screen          "Test passed"       execute if entity @e[type=bm21grad:smoke_screen]
chk detonations-total     "detonations=53"     bm21 stats
# reload from rack
chk ammo-select           "9M43"              bm21 ammo $V 9m43
chk reload                ""                  bm21 reload $V
sleep 5
chk reloading             "reload=true"       bm21 status $V
chk cease                 ""                  bm21 cease $V
# destruction
chk damage                "destroyed=true"    bm21 damage $V 5000
chk wreck-persists        "Test passed"       execute if entity @e[type=bm21grad:bm21]
chk tps                   "Mean TPS"          forge tps
echo "RCON: PASS=$PASS FAIL=$FAIL (log $RCON_LOG)"
[ $FAIL -eq 0 ]
