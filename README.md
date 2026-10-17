# AI Arsenal

Forge content mod for Minecraft 1.20.1. Adds vehicles, firearms, artillery, aircraft and equipment from several historical periods.

**Version:** `1.0.1-alpha.12` · **Minecraft:** `1.20.1` · **Forge:** `47.4.23` · **Java:** `17`

## WWI rebuild (work in progress)

Source baseline: standimaoff-oss/aimods v1.0.1-alpha.12, commit 0d9f75b2f298d0d043abaaaaf99fccffe0697924.

Changes: enclosed riveted track frames and wider track shoes; separate steel and paint textures; rebuilt shaped Gewehr 98 stock and lower iron sights, separate wood/steel textures. Registry IDs and ammunition stay compatible.

Restore baseline geometry before building:

```sh
python3 tools/restore_models.py
cd mods/aiarsenal
./gradlew build --no-daemon
```

The `WWI delivery` workflow builds a JAR and captures actual Minecraft F2 screenshots. A release is published only after both succeed. Offline developer renders are not gameplay evidence.

Minecraft 1.20.1, Forge 47.4.23, Java 17. Back up worlds before installing alpha builds.
