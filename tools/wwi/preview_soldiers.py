#!/usr/bin/env python3
"""r27: capture real Minecraft F2 previews of the AI soldiers, summon eggs,
weapon placement and the beefed-up explosion smoke.

Scenes:
  soldiers-on-foot   commander / assault / medic on the ground, no equipment
  soldier-in-zu23    tanker boards the ZU-23 PVO by himself (the old bug)
  soldier-in-truck   tanker boards the Ural driver seat (head visible over the cab)
  unit-eggs-in-hand  the four summon eggs in the player's hand
  unit-eggs-tab      the single creative section holding all four summon eggs
  weapon-in-hand-3p  third person, weapon gripped in the player's hand
  weapon-first-person first person with the Gewehr 98
  explosion-smoke    /realwar blast - volumetric smoke column
"""
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import verify_game as game  # noqa: E402

GROUND = -60  # flat world: grass top at -61, entities walk at -60


def window():
    return subprocess.check_output(
        ["xdotool", "search", "--name", "Minecraft"], text=True
    ).splitlines()[-1]


def run():
    game.configure("server", server=True)
    client_dir = game.RUNS / "client"
    client_dir.mkdir(parents=True, exist_ok=True)
    (client_dir / "options.txt").write_text("""version:3465
lang:ru_ru
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
renderClouds:"false"
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

    w = window()
    game.key("windowsize", w, "1280", "800")
    game.key("windowmove", w, "0", "0")
    game.key("windowraise", w)
    game.key("windowfocus", "--sync", w)
    time.sleep(2)
    for cmd in [
        "time set 6000",
        "gamerule doDaylightCycle false",
        "weather clear",
        "gamerule doWeatherCycle false",
        "gamerule doMobSpawning false",
        "gamemode creative TestPlayer",
        "clear TestPlayer",
    ]:
        game.command(cmd)
    game.key("key", "F1")  # clean view, no HUD

    # ------------------------------------------------------------------ 1. on foot
    game.command("summon aiarsenal:soldier 0 -60 0 {rw_class:0b,PersistenceRequired:1b}")  # commander
    game.command("summon aiarsenal:soldier 1.6 -60 0.6 {rw_class:2b,PersistenceRequired:1b}")  # assault
    game.command("summon aiarsenal:soldier -1.6 -60 0.6 {rw_class:3b,PersistenceRequired:1b}")  # medic
    time.sleep(20)  # let them take positions with the group (no equipment anywhere)
    game.command("tp TestPlayer 10 -60 10 135 0")   # look NW at the trio
    time.sleep(3)
    game.screenshot("soldiers-on-foot")

    # ------------------------------------------------- 2. tanker boards the PVO
    game.command("summon aiarsenal:zu23 30 -60 0 {PersistenceRequired:1b}")
    game.command("summon aiarsenal:soldier 33 -60 1.5 {rw_class:1b,PersistenceRequired:1b}")
    time.sleep(30)  # scan (10 ticks) + walk + board
    game.command("tp TestPlayer 30 -60 -14 0 0")     # look +Z at the ZU-23 and its seated gunner
    time.sleep(3)
    game.screenshot("soldier-in-zu23")

    # ------------------------------------------- 3. tanker boards the Ural
    # The Ural cab is open and the driver seat (dy 1.48) is within AI boarding
    # range, so the seated tanker's head reads clearly over the cab; the assault
    # soldier stays with the crew on foot - he must not take a seat.
    game.command("summon aiarsenal:ural -30 -60 0 {PersistenceRequired:1b}")
    game.command("summon aiarsenal:soldier -26 -60 4 {rw_class:1b,PersistenceRequired:1b}")
    game.command("summon aiarsenal:soldier -26 -60 8 {rw_class:2b,PersistenceRequired:1b}")
    time.sleep(35)  # scan (10 ticks) + walk + board the driver seat
    game.command("tp TestPlayer -30 -60 14 180 0")   # look -Z at the Ural
    time.sleep(3)
    game.screenshot("soldier-in-truck")

    # ------------------------------------------------------- 4. summon eggs tab
    for slot, egg in enumerate([
        "soldier_egg_commander", "soldier_egg_tanker",
        "soldier_egg_assault", "soldier_egg_medic"]):
        game.command(f"item replace entity TestPlayer hotbar.{slot} with aiarsenal:{egg}")
    game.command("tp TestPlayer 0 -60 -30 90 0")
    time.sleep(3)
    game.screenshot("unit-eggs-in-hand")
    game.view_action(client_log, "view_units")
    time.sleep(1)
    game.screenshot("unit-eggs-tab")
    game.key("key", "Escape")
    time.sleep(1.5)

    # ------------------------------------------------------ 5. weapon in hand
    game.command("item replace entity TestPlayer hotbar.0 with aiarsenal:gewehr98{Ammo:5}")
    game.key("key", "1")
    game.view_action(client_log, "view_back")
    game.command("tp TestPlayer 0 -60 -30 180 0")
    time.sleep(4)
    game.screenshot("weapon-in-hand-3p")
    game.view_action(client_log, "view_first")
    time.sleep(3)
    game.screenshot("weapon-first-person")

    # ------------------------------------------------- 6. explosion smoke
    game.command("tp TestPlayer 60 -60 0 0 0")       # look +Z at the blast point
    time.sleep(2)
    game.command("realwar blast 8 60 -59 7")
    time.sleep(1.2)  # the smoke column is rising, fireball already faded
    game.screenshot("explosion-smoke")
    time.sleep(4)
    game.screenshot("explosion-smoke-late")

    if client.poll() is not None or server.poll() is not None:
        raise RuntimeError("Minecraft exited during preview capture")


if __name__ == "__main__":
    run()
