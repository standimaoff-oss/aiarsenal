#!/usr/bin/env python3
"""Destructive catalogue QA in a NEW disposable world, installed Forge/JAR only.
Every runtime item is enumerated, held, saved/loaded as NBT and its generic use paths
attempted. Those smoke checks are NOT full vehicle/tool acceptance. Firearm actions
use real mouse/R input; free-fall bombs use real entity physics, damage and blast jobs.
Reports remain incremental when an individual assertion fails or the JVM crashes.
"""
import json
import os
import re
import signal
import subprocess
import time
from pathlib import Path
import verify_game as g
from install_qa_client import prepare

ROWS = []
BOMBS = []
WORLD = {}
RUN_OUTCOME = {'status': 'RUNNING'}
CAPTURED_CLASSES = set()
REPORT = g.LOGS / 'catalogue-gameplay.json'


def save():
    REPORT.write_text(json.dumps({'scope': 'catalogue smoke + equip/place/consume/melee + firearm cycles + free-fall bombs',
        'screenshotPolicy': 'every item' if os.environ.get('QA_ALL_F2')=='1' else 'each item class, all firearms and free-fall bombs',
        'items': ROWS, 'bombs': BOMBS, 'worldSaveReopen': WORLD, 'runOutcome': RUN_OUTCOME,
        'limitations': ['Generic use is not proof of all contextual tool/vehicle features.',
            'Gun-vehicle main-round reload/fire and driving are tested; alternate shells, other vehicle families, guided ordnance and remote-control cycles remain separate acceptance work.',
            'Linux llvmpipe cannot certify the Windows NVIDIA crash is fixed.']}, indent=2) + '\n')


def wait(test, seconds=30):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if test():
            return
        time.sleep(.4)
    raise AssertionError('action did not reach its expected state before timeout')


def local(action):
    g.command('arsenal_visual_test client ' + action + ' TestPlayer')


def number(key):
    text = g.command('data get entity TestPlayer SelectedItem')
    match = re.search(r'\b' + key + r': (\d+)', text)
    return int(match[1]) if match else 0


def entity_health(selector):
    result=g.command('data get entity '+selector+' Health')
    match=re.search(r'data: ([0-9.]+)f',result)
    assert match, result
    return float(match[1])


def input_key(key):
    g.key('keydown', key)
    time.sleep(.3)
    g.key('keyup', key)


def fire():
    g.key('mousedown', '1')
    time.sleep(.22)
    g.key('mouseup', '1')


def fire_round(before):
    g.key('mousedown','1')
    try:
        wait(lambda:number('Ammo')<before,20)
    finally:
        g.key('mouseup','1')


def equip(row, nbt=''):
    g.key('mouseup', '1'); g.key('mouseup', '3')
    local('view_first')
    g.command('ride TestPlayer dismount')
    g.command('tp TestPlayer 0.5 -60 0.5 0 0')
    g.command('clear TestPlayer')
    g.command('item replace entity TestPlayer hotbar.0 with ' + row['id'] + nbt)
    g.key('key', '1')
    # Real key delivery can lag behind a cross-world teleport on software GL.
    # Do not invoke use/useOn on the previous hotbar slot.
    wait(lambda: row['id'] in g.command('data get entity TestPlayer SelectedItem'),30)


def gun(row):
    equip(row, '{Ammo:0}')
    g.command('gamemode survival TestPlayer')
    g.command('effect give TestPlayer minecraft:resistance 9999 4 true')
    g.command('give TestPlayer ' + row['ammo'] + ' 16')
    # Genuine empty reload and state from the installed server, not an injected timer.
    input_key('r')
    wait(lambda: number('Ammo') > 0, max(20, row['reloadTicks'] / 20 + 10))
    if row['incremental']:
        time.sleep(row['capacity'] * row['reloadTicks'] / 20 + 3)
    row['emptyReload'] = 'PASS'
    g.screenshot(row['id'].split(':')[1] + '-loaded')
    if row['manpads']:
        # Engine-independent tagged aerial target, held stationary in a loaded chunk.
        # Seeking is real; Lock/Cool are never injected into the gun NBT.
        g.command('summon minecraft:bee 0.5 -48 30.5 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b}')
        g.command('tp TestPlayer 0.5 -60 0.5 0 -19')
        g.key('mousedown', '3')
        wait(lambda: number('Lock') >= 100, 25)
    else:
        g.key('mousedown', '3')
        time.sleep(.6)
    before = number('Ammo')
    fire_round(before)
    row['shot'] = 'PASS'
    g.key('mouseup', '3')
    # Exercise the full empty->reload->fire cycle again, without manufacturing Ammo.
    # Large magazines are not artificially emptied: tactical reload is a distinct case.
    if row['capacity'] == 1:
        input_key('r')
        wait(lambda: number('Ammo') == 1, 20)
        row['repeatReload'] = 'PASS'
        if not row['manpads']:
            time.sleep(2)
            fire_round(1)
            row['repeatShot'] = 'PASS'
    else:
        # Clip-fed weapons correctly reject partially-full reloads; existing logic tests
        # cover that contract. Do not call a rejected reload a successful reload.
        row['repeatReload'] = 'NOT_RUN (partial-magazine rules differ)'
        time.sleep(2)
        before = number('Ammo'); fire_round(before)
        row['repeatShot'] = 'PASS'
    g.command('gamemode creative TestPlayer')


def vehicle(row):
    def state():
        observed=json.loads(g.command('execute as TestPlayer run arsenal_stress vehiclestatus'))
        row['lastVehicleState']=observed
        return observed
    g.command('execute as TestPlayer at @s run arsenal_stress mount',expect='MOUNTED')
    g.command('gamemode survival TestPlayer')
    g.command('effect give TestPlayer minecraft:resistance 9999 4 true')
    g.command('give TestPlayer '+row['vehicleAmmo']+' 16')
    local('view_back'); time.sleep(2)
    initial=state();assert initial['loaded']>0,initial
    def fire_until(previous):
        # A click during the production cooldown is correctly ignored. Hold LMB
        # until a real discharge, without overriding cooldown or loaded state.
        g.key('mousedown','1')
        try:
            wait(lambda:state()['fired']>previous,20)
        finally:
            g.key('mouseup','1')
    fire_until(initial['fired'])
    wait(lambda:state()['loaded']<initial['loaded'],5)
    row['mainGunShot']='PASS'
    g.screenshot(row['id'].split(':')[1]+'-fired')
    # Complementary/llvmpipe measured 369/400 active ticks in 270s, with
    # no resets and input gaps up to 72 server ticks. Allow slower wall time,
    # never substitute a timeout extension for an actual loaded/ammo assertion.
    row['reloadTimeBudgetSeconds']=max(80,initial['reloadRequired']/20*24+60)
    reload_started=time.monotonic()
    g.key('keydown','r')
    try:
        wait(lambda:state()['loaded']==initial['loaded'],row['reloadTimeBudgetSeconds'])
    finally:
        g.key('keyup','r')
    row['reloadWallSeconds']=round(time.monotonic()-reload_started,3)
    reloaded=state();assert reloaded['supply']<16,reloaded
    row['mainGunReload']='PASS (real R, ammunition consumed)'
    fire_until(reloaded['fired'])
    row['mainGunRepeatShot']='PASS'
    if row['drivable']:
        if not state()['engine']:
            input_key('h');wait(lambda:state()['engine'],8)
        start=state();g.key('keydown','w')
        try:
            wait(lambda:(state()['x']-start['x'])**2+(state()['z']-start['z'])**2>.25,15)
        finally:
            g.key('keyup','w')
        row['drive']='PASS (real H/W controls)'
    else:
        row['drive']='N/A (towed gun)'
    row['functional']='MAIN_GUN_FIRE_RELOAD_REPEAT_PASS'


def run():
    g.configure('server', server=True)
    client = g.RUNS / 'client'
    client.mkdir(parents=True, exist_ok=True)
    (client / 'options.txt').write_text('''version:3465
lang:ru_ru
tutorialStep:none
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
renderDistance:3
simulationDistance:5
maxFps:30
guiScale:2
fov:0.0
graphicsMode:0
renderClouds:"false"
pauseOnLostFocus:false
narrator:0
soundCategory_master:0.5
chatVisibility:0
chatOpacity:0.0
fullscreen:false
overrideWidth:1000
overrideHeight:700
''')
    cmd = prepare(g)
    args = g.RUNS / 'server/user_jvm_args.txt'
    args.write_text('-Xmx2048M\n-XX:+UseSerialGC\n-Daiarsenal.stressQa=true\n')
    server, server_log = g.launch('server', ['bash', 'run.sh', 'nogui'], cwd=g.RUNS / 'server')
    g.wait_log(server, server_log, 'Done (')
    player, client_log = g.launch('client', cmd, cwd=client)
    g.wait_log(server, server_log, 'TestPlayer joined the game')
    time.sleep(15)
    window = subprocess.check_output(['xdotool','search','--name','Minecraft'], text=True).splitlines()[-1]
    g.key('windowfocus','--sync',window)
    for command in ['time set 6000','gamerule doDaylightCycle false','weather clear','gamerule doWeatherCycle false',
                    'gamerule doMobSpawning false','gamerule naturalRegeneration false','gamerule keepInventory true','difficulty peaceful',
                    'gamemode creative TestPlayer','forceload add -32 -32 48 64',
                    'fill -12 -61 -8 12 -61 38 minecraft:smooth_stone','tp TestPlayer 0.5 -60 0.5 0 0']:
        g.command(command)
    local('unpack')
    g.wait_log(player, client_log, 'FX UNPACK PASS', 60)
    g.command('arsenal_stress catalogue', expect='CATALOGUE')
    catalogue = json.loads((g.RUNS / 'server/logs/arsenal-catalogue.json').read_text())
    (g.LOGS / 'catalogue-inventory.json').write_text(json.dumps(catalogue, indent=2) + '\n')
    for entry in catalogue['items']:
        row = dict(entry, smoke='RUNNING', functional='NOT_VERIFIED')
        ROWS.append(row); save()
        try:
            assert row['nbtRoundtrip'], row
            equip(row)
            # Every item is held; representative class F2s avoid duplicating hundreds
            # of identical passive-item views. QA_ALL_F2=1 captures every entry.
            if os.environ.get('QA_ALL_F2')=='1' or row['class'] not in CAPTURED_CLASSES:
                row['screenshot']='item-'+row['id'].split(':')[1]
                g.screenshot(row['screenshot']); CAPTURED_CLASSES.add(row['class'])
            if 'ammo' in row:
                gun(row)
                row['functional'] = 'FIRE_RELOAD_CYCLE_PASS'
            elif row['melee']:
                g.command('summon minecraft:cow 0.5 -60 2.5 {Tags:["qa_melee"],NoAI:1b,PersistenceRequired:1b}')
                g.command('tp TestPlayer 0.5 -60 0.5 0 15')
                time.sleep(1)
                row['targetHealthBefore']=entity_health('@e[tag=qa_melee,limit=1]')
                fire(); time.sleep(.5)
                row['targetHealthAfter']=entity_health('@e[tag=qa_melee,limit=1]')
                assert 0 <= row['targetHealthAfter'] < row['targetHealthBefore'], row
                row['functional']='MELEE_HIT_PASS'
            else:
                if row['consumable']:
                    g.key('mousedown','3')  # prevent the real client from cancelling a timed use
                row['genericUse'] = g.command('execute as TestPlayer at @s run arsenal_stress use', expect='USE ')
                time.sleep(.5)
                if row.get('gunVehicle'):
                    vehicle(row)
                elif row['armor']:
                    equipped=g.command('data get entity TestPlayer Inventory[{Slot:'+str(row['armorSlot'])+'b}].id')
                    assert row['id'] in equipped,equipped
                    row['functional']='EQUIP_PASS'
                elif row['consumable']:
                    def consumed():
                        state=json.loads(g.command('execute as TestPlayer run arsenal_stress playerstatus'))
                        return not state['using'] and (state['item']!=row['id'] or state['count']==0)
                    wait(consumed,12)
                    row['functional']='CONSUME_PASS'
                elif row['block']:
                    g.command('execute if block 0 -60 2 '+row['placedBlock'],expect='Test passed')
                    row['functional']='PLACE_PASS'
            assert player.poll() is None and server.poll() is None, 'Minecraft process exited'
            row['smoke'] = 'PASS'
        except Exception as ex:
            row['smoke'] = 'FAIL'; row['error'] = str(ex)
            if player.poll() is not None or server.poll() is not None:
                save(); raise
        finally:
            save()
        g.key('mouseup','1'); g.key('mouseup','3'); g.key('keyup','r'); g.key('keyup','w'); local('view_first')
        g.command('gamemode creative TestPlayer')
        g.command('ride TestPlayer dismount')
        # A fresh fixture for each item. QA never points at an existing player world.
        g.command('kill @e[type=!minecraft:player]')
        g.command('fill -10 -60 -6 10 -46 35 minecraft:air')
        g.command('fill -12 -61 -8 12 -61 38 minecraft:smooth_stone')
    local('view_first'); g.command('clear TestPlayer')
    for name in catalogue['freeFallBombs']:
        row = {'id': name, 'status': 'RUNNING'}; BOMBS.append(row); save()
        try:
            g.command('tp TestPlayer 0.5 -60 0.5 0 -10')
            g.command('fill -16 -61 -16 16 -61 48 minecraft:smooth_stone')
            g.command('execute as TestPlayer at @s run arsenal_stress drop ' + name, expect='DROPPED')
            row['witnessHealthBefore']=entity_health('@e[type=minecraft:pig,limit=1]')
            last = {}
            def complete():
                last.update(json.loads(g.command('arsenal_stress bombstatus')))
                return last['removed'] and not last['activeBlasts'] and not last['legacyBusy']
            wait(complete, 240)
            row.update(last)
            row['playerEffects']=json.loads(g.command('execute as TestPlayer run arsenal_stress playerstatus'))['effects']
            assert not {'minecraft:blindness','minecraft:nausea'}.intersection(row['playerEffects']),row['playerEffects']
            assert last['removalReason'] == 'DISCARDED', last
            assert last['completedTasks']+last['tuDetonations']+last['arsenalDetonations']>0, 'no production detonation recorded'
            assert last['witnessDead'] or 0 <= last['witnessHealth'] < row['witnessHealthBefore'], 'bomb removed without damage to live witness'
            if name.startswith(('rw:','ars:')):
                g.command('tp TestPlayer 0.5 -60 -200.5 0 -12')  # exterior view, not an opaque frame inside the cloud
            time.sleep(1)
            g.screenshot('bomb-' + name.lower().replace(':','-'))
            row['status'] = 'PASS'
        except Exception as ex:
            row['status'] = 'FAIL'; row['error'] = str(ex)
            if player.poll() is not None or server.poll() is not None:
                save(); raise
        finally:
            save()
    g.command('arsenal_stress archive', expect='ARCHIVE SAVED')
    g.command('save-all flush', expect='Saved')
    g.command('stop'); server.wait(timeout=120)
    os.killpg(player.pid, signal.SIGTERM); player.wait(timeout=45)
    server, server_log = g.launch('server-reopen', ['bash','run.sh','nogui'], cwd=g.RUNS/'server')
    g.wait_log(server,server_log,'Done (')
    player, reopen_log = g.launch('client-reopen',cmd,cwd=client)
    g.wait_log(server,server_log,'TestPlayer joined the game')
    time.sleep(10)
    g.command('arsenal_stress archivecheck', expect='ARCHIVE VERIFIED ' + str(len(ROWS)))
    WORLD.update(status='PASS', archivedItems=len(ROWS), method='barrel block entities saved to region files; both JVMs stopped, world reopened, real client rejoined')
    save()
    logs = client_log.read_text(errors='replace') + reopen_log.read_text(errors='replace')
    server_text=(g.LOGS/'server.log').read_text(errors='replace') + server_log.read_text(errors='replace')
    assert 'Failed to store chunk' not in server_text, 'chunk save failure (must not be hidden by successful archive reopening)'
    assert 'Ошибка в задаче взрыва' not in server_text, 'blast task threw an exception'
    assert 'Exception ticking' not in server_text and 'Exception in server tick loop' not in server_text
    fatal = re.findall(r'^.*(?:\[Render thread/ERROR\]|OutOfMemoryError|EXCEPTION_ACCESS_VIOLATION|GL renderer disabled|OpenGL Error|GL_INVALID).*$' ,logs,re.M)
    assert not fatal, '\n'.join(fatal)
    g.command('stop'); server.wait(timeout=120)
    failures = [r for r in ROWS if r['smoke'] != 'PASS'] + [r for r in BOMBS if r['status'] != 'PASS']
    assert not failures, json.dumps(failures, indent=2)
    print('CATALOGUE PASS', len(ROWS), 'item smoke checks;', len(BOMBS), 'real free-fall detonations', flush=True)


if __name__ == '__main__':
    try:
        run()
        RUN_OUTCOME.update(status='PASS')
    except BaseException as error:
        RUN_OUTCOME.update(status='FAIL', error=type(error).__name__+': '+str(error))
        raise
    finally:
        save()
        g.cleanup()
