#!/usr/bin/env bash
# Render item icons for the arsenal kinds from the delivered .blend files (Blender, Cycles CPU).
B=/home/user/.cache/blender/blender-5.2.2-linux-x64/blender
O=/home/user/.cache/icons; mkdir -p $O
r(){ $B -b /home/user/models/$1.blend --python /home/user/tools/blender/icons.py -- $O/$2.png $3 $4 $5 > $O/$2.log 2>&1; grep -q ICON_OK $O/$2.log && echo "OK $2" || echo "FAIL $2"; }
r recon mq9 215 38 MQ9
r recon orion 215 38 ORION
r shahed shahed136 200 50 S136
r shahed shahed131 200 50 S131
r shahed shahed238 200 50 S238
r shahed liutyi 215 40 LIUTYI
r shahed shahed_rail 240 25 RAIL
r fpv fpv7 210 40 FPV7
r fpv fpv10 210 40 FPV10
r cruise kh101 235 25 KH101
r cruise kh55 235 25 KH55
r cruise kalibr 235 25 KALIBR
r cruise tomahawk 235 25 TOMAHAWK
r cruise cruise_launcher 230 25 LAUNCHER
r nukes rds1 240 20 RDS1
r nukes an602 240 20 AN602
r nukes yars 235 20 YARS
r nukes yars_tel 230 25 YARSTEL
echo ALLDONE
