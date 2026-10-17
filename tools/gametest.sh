#!/bin/bash
export GRADLE_USER_HOME=/home/user/.cache/mc/gradle-home
# Deterministic gametest run: fresh superflat world with a fixed seed (the GameTestServer otherwise falls back to a
# random-seed noise world, which made terrain-dependent tests flaky).  usage: tools/gametest.sh [modid]
MODID=${1:-aiarsenal}
RUN=/home/user/.cache/mc/runs/gameTestServer
mkdir -p "$RUN" /home/user/logs
rm -rf "$RUN/world"
cat > "$RUN/server.properties" <<P
level-type=minecraft\:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","features":false,"lakes":false}
level-seed=20261001
generate-structures=false
spawn-protection=0
P
export JAVA_HOME=/home/user/.cache/mc/jdk/jdk-17.0.20.1+1 PATH=/home/user/.cache/mc/jdk/jdk-17.0.20.1+1/bin:$PATH
cd /home/user/mods/$MODID
timeout 1500 ./gradlew --no-daemon --project-cache-dir /home/user/.cache/mc/gradle --gradle-user-home /home/user/.cache/mc/gradle-home \
  -Dorg.gradle.jvmargs="-Xmx700m" runGameTestServer > /home/user/logs/${MODID}_gametest.log 2>&1
grep -E "required tests|failed!|All .* passed" /home/user/logs/${MODID}_gametest.log | cut -c1-400
