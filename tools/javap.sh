#!/bin/bash
# usage: javap.sh <fully.qualified.Class> [grep-pattern]  -- signatures from the mapped (official) MC+Forge jar
J=/home/user/.cache/mc/jdk/jdk-17.0.20.1+1/bin/javap
CP=/home/user/.cache/mc/gradle-home/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.23_mapped_official_1.20.1/forge-1.20.1-47.4.23_mapped_official_1.20.1.jar
CP=$CP:$(find /home/user/.cache/mc/gradle-home/caches/modules-2 -name "*.jar" | tr '\n' ':')
if [ -n "$2" ]; then $J -p -cp "$CP" "$1" | grep -E "$2"; else $J -p -cp "$CP" "$1"; fi
