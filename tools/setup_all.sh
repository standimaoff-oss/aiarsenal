#!/bin/bash
# Recreate the non-persisted toolchain after a sandbox reset: swap, JDK 17, Blender 5.2.2, apt deps, Forge server.
set -x
if [ ! -f /swapfile ]; then sudo fallocate -l 3G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile; fi
mkdir -p /home/user/.cache/mc/jdk /home/user/.cache/blender /home/user/.cache/air
if [ ! -d /home/user/.cache/mc/jdk/jdk-17.0.20.1+1 ]; then cd /home/user/.cache/mc/jdk && curl -fsSL -o jdk.tgz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" && tar xzf jdk.tgz && rm jdk.tgz; fi
ls /home/user/.cache/mc/jdk
if [ ! -x /home/user/.cache/blender/blender-5.2.2-linux-x64/blender ]; then cd /home/user/.cache/blender && curl -fsSL -o b.tar.xz https://download.blender.org/release/Blender5.2/blender-5.2.2-linux-x64.tar.xz && tar xJf b.tar.xz && rm b.tar.xz && echo BLENDER_OK; fi
sudo apt-get update -qq >/dev/null 2>&1; sudo apt-get install -y -qq xvfb xdotool x11-apps x11-utils imagemagick libgl1 libglx-mesa0 vorbis-tools rsync libxi6 libxxf86vm1 libxfixes3 libxrender1 libxkbcommon0 libsm6 >/dev/null 2>&1 && echo APT_OK
chmod +x /home/user/tools/*.sh /home/user/tools/r /home/user/mods/*/gradlew
echo ALL_DONE
