#!/usr/bin/env python3
"""Capture authentic Minecraft F2 previews of the WWI vehicles and handheld equipment."""
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import verify_game as game  # noqa: E402


def run():
    game.configure("server", server=True)
    client_dir = game.RUNS / "client"
    client_dir.mkdir(parents=True, exist_ok=True)
    (client_dir / "options.txt").write_text("""version:3465
lang:en_us
tutorialStep:none
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
renderDistance:6
simulationDistance:5
maxFps:30
guiScale:2
fov:0.0
graphicsMode:0
renderClouds:\"false\"
pauseOnLostFocus:false
narrator:0
soundCategory_master:0.0
chatVisibility:0
chatOpacity:0.0
fullscreen:false
overrideWidth:1280
overrideHeight:800
""")

    server, server_log = game.launch("server", game.gradle("runServer"))
    game.wait_log(server, server_log, "Done (")
    client, client_log = game.launch(
        "client", game.gradle("runClient", "-PautoJoin=127.0.0.1:25565")
    )
    game.wait_log(server, server_log, "TestPlayer joined the game", 900)
    game.wait_log(client, client_log, "Loaded", 180)
    time.sleep(12)

    window = subprocess_window()
    game.key("windowsize", window, "1280", "800")
    game.key("windowmove", window, "0", "0")
    game.key("windowraise", window)
    game.key("windowfocus", "--sync", window)
    time.sleep(2)
    for cmd in [
        "time set 6000",
        "gamerule doDaylightCycle false",
        "weather clear",
        "gamerule doWeatherCycle false",
        "gamerule doMobSpawning false",
        "gamemode creative TestPlayer",
        "fill -32 -61 -32 32 -61 40 minecraft:grass_block",
        "fill -4 -61 4 4 -61 26 minecraft:coarse_dirt",
        "tp TestPlayer -4.2 -59.5 12.8 -55 4",
        "clear TestPlayer",
    ]:
        game.command(cmd)
    game.command(
        "summon aiarsenal:mark_i 0 -60 16 {PersistenceRequired:1b}",
        "Summoned new entity.aiarsenal.mark_i",
    )
    time.sleep(5)
    game.key("key", "F1")  # remove HUD for a clean, unaltered in-game view
    time.sleep(2)
    game.screenshot("mark-i-exterior")
    game.key("key", "F1")

    # A second Mark I set ablaze: proof that the fire is a volumetric flame, not flat sprites.
    game.command(
        "summon aiarsenal:mark_i -20 -60 -20 {PersistenceRequired:1b}",
        "Summoned new entity.aiarsenal.mark_i",
    )
    time.sleep(3)
    game.command("realwar damage @e[type=aiarsenal:mark_i,x=-20,y=-60,z=-20,distance=..4] fire")
    game.command("tp TestPlayer -13.5 -58.6 -13.5 135 10")
    time.sleep(7)
    game.key("key", "F1")
    time.sleep(2)
    game.screenshot("mark-i-burning")
    game.key("key", "F1")
    game.command("tp TestPlayer -6 -59 11 -50 2")
    time.sleep(2)

    for cmd in [
        "clear TestPlayer",
        "item replace entity TestPlayer hotbar.0 with aiarsenal:gewehr98{Ammo:5}",
        "tp TestPlayer 0 -60 0 0 0",
    ]:
        game.command(cmd)
    selected = game.command("data get entity TestPlayer SelectedItem")
    if "aiarsenal:gewehr98" not in selected:
        raise AssertionError(f"Gewehr 98 is not selected: {selected}")
    game.key("key", "1")
    time.sleep(4)
    game.screenshot("gewehr98-first-person")

    # Separate, unedited F2 previews for the equipment. Put the tank out of the background.
    game.command("tp TestPlayer 20 -60 0 0 0")
    game.key("mousedown", "3")
    time.sleep(2)
    game.screenshot("gewehr98-aim")
    game.key("mouseup", "3")
    for item in ("mills_bomb", "stielhandgranate", "wex", "wwi_fuel"):
        game.command("clear TestPlayer")
        game.command(f"item replace entity TestPlayer hotbar.0 with aiarsenal:{item}")
        game.key("key", "1")
        time.sleep(3)
        game.screenshot(item+"-in-hand")
    game.command("clear TestPlayer")
    game.command("item replace entity TestPlayer hotbar.0 with aiarsenal:wex")
    game.key("key", "1")
    time.sleep(2)
    game.key("mousedown", "3")
    time.sleep(1)
    game.screenshot("wex-flame")
    game.key("mouseup", "3")

    # Place each real item renderer in a staged Minecraft item_display. Tiny 1:1
    # hand grenades are otherwise only a few pixels wide at the player's hand.
    # F2 files remain untouched captures from the running client; per-item scale
    # keeps the full, metre-scale meshes legible without editing the screenshots.
    game.command("tp TestPlayer 20 -60 0 0 0")
    game.command("kill @e[type=aiarsenal:mark_i]")
    game.command("fill 16 -61 5 24 -55 5 minecraft:gray_concrete")
    game.key("key", "F1")
    display_settings = {
        "mills_bomb": (8.0, 1.5, "[0f,0f,0f,1f]"),
        "stielhandgranate": (3.2, 2.0, "[0f,0f,0f,1f]"),
        "wex": (3.0, 3.0, "[0f,0.258819f,0f,0.965926f]"),
        "wwi_fuel": (3.6, 2.0, "[0f,0f,0f,1f]"),
    }
    for item, (scale, distance, rotation) in display_settings.items():
        game.command("kill @e[type=minecraft:item_display]")
        scalar = f"{scale:g}f"
        summon = (
            f'execute at TestPlayer anchored eyes positioned ^ ^ ^{distance:g} run '
            f'summon minecraft:item_display ~ ~ ~ '
            f'{{item:{{id:"aiarsenal:{item}",Count:1b}},item_display:"fixed",'
            f'transformation:{{translation:[0f,0f,0f],left_rotation:{rotation},'
            f'scale:[{scalar},{scalar},{scalar}],right_rotation:[0f,0f,0f,1f]}}}}'
        )
        game.command(summon, "Summoned new Item Display")
        time.sleep(2)
        game.screenshot(item+"-showcase")
    game.command("kill @e[type=minecraft:item_display]")
    game.key("key", "F1")

    if client.poll() is not None or server.poll() is not None:
        raise RuntimeError("Minecraft exited during preview capture")


def subprocess_window():
    import subprocess
    windows = subprocess.check_output(
        ["xdotool", "search", "--name", "Minecraft"], text=True
    ).splitlines()
    if not windows:
        raise RuntimeError("Minecraft window not found")
    return windows[-1]


if __name__ == "__main__":
    try:
        run()
    except Exception as exc:
        message = str(exc).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print(f"::error title=Preview capture::{message}", flush=True)
        for path in sorted(game.LOGS.glob("*.log")):
            lines = path.read_text(errors="replace").splitlines()[-25:]
            if not lines:
                continue
            tail = "\n".join(lines)
            print(f"::group::Preview diagnostics: {path.name}", flush=True)
            print(tail, flush=True)
            print("::endgroup::", flush=True)
            safe_tail = tail[-3500:].replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
            print(f"::error title={path.name} diagnostic tail::{safe_tail}", flush=True)
        raise
    finally:
        game.cleanup()
