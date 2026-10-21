#!/usr/bin/env python3
"""r27: capture real Minecraft F2 previews of the AI soldiers, summon eggs,
weapon placement and the beefed-up explosion smoke.

Scenes:
  soldiers-on-foot   commander / assault / medic on the ground, no equipment
  soldier-in-zu23    tanker boards the ZU-23 PVO by himself (the old bug)
  soldier-in-truck   tanker boards the Ural driver seat (head visible over the cab)
  unit-eggs-in-hand  the four summon eggs in the player's hand
  unit-eggs-tab      the single creative section holding all four summon eggs
  soldier-weapon-3p  the AI soldier holding his rifle (hand placement)
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
fov:70.0
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
    # three classes standing together - close enough to see the Steve body under the
    # class uniform, the helmet and the weapon in hand
    game.command("summon aiarsenal:soldier 0.6 -60 0.6 {rw_class:0b,PersistenceRequired:1b}")   # commander
    game.command("summon aiarsenal:soldier 2.2 -60 0.6 {rw_class:2b,PersistenceRequired:1b}")   # assault
    game.command("summon aiarsenal:soldier -1.0 -60 0.6 {rw_class:3b,PersistenceRequired:1b}")  # medic
    game.command("summon aiarsenal:soldier 3.8 -60 0.6 {rw_class:1b,PersistenceRequired:1b}")   # tanker
    time.sleep(20)  # let them take positions with the group (no equipment anywhere)
    game.command("tp TestPlayer 1.4 -60 6.5 180 0")   # stand in front of the squad, look -Z
    time.sleep(3)
    game.screenshot("soldiers-on-foot")

    # ------------------------------------------------- 2. tanker boards the PVO
    game.command("summon aiarsenal:zu23 30 -60 0 {PersistenceRequired:1b}")
    game.command("summon aiarsenal:soldier 33 -60 1.5 {rw_class:1b,PersistenceRequired:1b}")
    time.sleep(30)  # scan (10 ticks) + walk + board
    game.command("tp TestPlayer 30 -60 -7 0 0")      # look +Z at the ZU-23 and its seated gunner
    time.sleep(3)
    game.screenshot("soldier-in-zu23")

    # ------------------------------------------- 3. tanker boards the Ural
    # The Ural cab is open and the driver seat (dy 1.48) is within AI boarding
    # range, so the seated tanker's head reads clearly over the cab; the assault
    # soldier stays with the crew on foot - he must not take a seat.
    game.command("summon aiarsenal:ural -30 -60 0 {PersistenceRequired:1b}")
    game.command("summon aiarsenal:soldier -27 -60 3 {rw_class:1b,PersistenceRequired:1b}")
    game.command("summon aiarsenal:soldier -27 -60 6 {rw_class:2b,PersistenceRequired:1b}")
    # wait for the tanker to actually take the driver seat (max 60 s), and log the result
    seated = False
    for _ in range(60):
        time.sleep(1)
        answer = game.command("data get entity @e[type=aiarsenal:ural,limit=1,sort=nearest] Passengers")
        if "Passengers" in answer and "has no" not in answer and "none" not in answer.lower():
            seated = True
            break
    print("tanker seated in the Ural:", seated, flush=True)
    game.command("tp TestPlayer -30 -60 12 180 0")   # look -Z at the Ural cab
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

    # --------------------------------- 5. weapon in the AI soldier's hands (3rd person)
    # The rifle transform is live-tunable through config/aiarsenal_view.json, so the run
    # captures a few candidates in one go; the best one is then baked into the resource.
    game.command("item replace entity TestPlayer hotbar.0 with aiarsenal:gewehr98{Ammo:5}")
    game.command("summon aiarsenal:soldier 2.5 -60 -34.5 {rw_class:0b,PersistenceRequired:1b,Rotation:[180f,0f]}")
    game.view_action(client_log, "view_back")
    game.command("tp TestPlayer 2.5 -60 -30.5 180 0")   # camera behind the player, soldier in front
    time.sleep(4)
    game.screenshot("soldier-weapon-3p")

    config = game.RUNS / "client" / "config" / "aiarsenal_view.json"
    config.parent.mkdir(parents=True, exist_ok=True)
    variants = {
        "a": [0, 0, 0, 0, 0, 0, 1.0, 0],
        "b": [0, 0, 0, 180, 0, 0, 1.0, 0],
        "c": [0, 0, 0, 0, -90, 0, 1.0, 0],
        "d": [0, 0, 0, 90, 0, 0, 1.0, 0],
        "e": [0, -0.06, 0.05, 0, -18, 0, 1.0, 0],
        "f": [0, 0, 0, 180, 0, 0, 1.0, 1],
    }
    for name, v in variants.items():
        config.write_text(
            '{"gewehr98.thirdperson_righthand": %s, "gewehr98.thirdperson_lefthand": %s}'
            % (v, v), encoding="utf-8")
        time.sleep(2)   # HandheldView re-reads the file once a second
        game.screenshot("weapon-variant-" + name)
    config.unlink(missing_ok=True)
    time.sleep(2)

    # ------------------------------------------------------ 6. weapon in hand
    game.view_action(client_log, "view_first")
    game.key("key", "1")
    game.command("tp TestPlayer 0 -60 -30 205 0")
    time.sleep(4)
    game.screenshot("weapon-first-person")
    game.view_action(client_log, "view_front")          # camera in front: the grip is visible
    time.sleep(3)
    game.screenshot("weapon-in-hand-3p")

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
