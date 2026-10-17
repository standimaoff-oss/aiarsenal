#!/usr/bin/env python3
"""Second-phase contextual actions in installed Minecraft, not catalogue smoke.
The launchpad and cannon interactions are invoked server-side through production
item/entity handlers. Craft triggers and Javelin locking use real mouse/keyboard.
No forced ammunition, lock, cooldown, flight progress or explosion packets.
"""
import json
import os
import re
import signal
import subprocess
import time
import traceback
import verify_game as g
from install_qa_client import prepare
from context_result import missile_complete, craft_fired, assert_complete

REPORT = dict(scope='contextual launchpad/craft/cannon/Javelin audit', expectedCases=0, cases=[],
              runOutcome={'status':'RUNNING'}, worldSaveReopen={'status':'NOT_RUN'},
              limitations=['Not complete acceptance of all 448 items or all craft weapons/flight modes.',
                           'Linux llvmpipe, not Windows NVIDIA/Oculus certification.'])
SERVER = CLIENT = None


def save():
    (g.LOGS/'context-gameplay.json').write_text(json.dumps(REPORT,indent=2)+'\n')


def wait(test, seconds=60):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        for process in [SERVER, CLIENT]:
            if process is not None and process.poll() is not None:
                raise RuntimeError('Minecraft JVM exited '+str(process.returncode))
        value=test()
        if value:return value
        time.sleep(.5)
    raise AssertionError('context action timed out')


def qa(command):
    return json.loads(g.command('execute as TestPlayer at @s run arsenal_context '+command))


def local(action):
    g.command('arsenal_visual_test client '+action+' TestPlayer')


def key(k):
    g.key('keydown',k);time.sleep(.2);g.key('keyup',k)


def reset_player(x,z,y=-60):
    g.key('mouseup','1');g.key('mouseup','3')
    g.command('ride TestPlayer dismount')
    g.command('gamemode creative TestPlayer')
    g.command(f'tp TestPlayer {x+.5} {y} {z+.5} 0 0')
    g.command('clear TestPlayer')
    g.key('key','1')
    g.command('effect give TestPlayer minecraft:resistance 99999 4 true')
    g.command('effect give TestPlayer minecraft:night_vision 99999 0 true')
    g.command('gamemode survival TestPlayer')
    local('view_first');time.sleep(1)


def missile(row,index):
    x,z=0,800+index*640
    reset_player(x,z)
    g.command(f'forceload add -16 {z-16} 32 {z+32}')
    row['load']=qa('preparemissile '+row['id'].split(':')[1]);save()
    wait(lambda:qa('missilestatus')['pad']=='READY',60)
    row['ready']='PASS';qa('launchmissile')
    wait(lambda:qa('missilestatus')['spawned']>0,60)
    row['launch']='PASS';g.screenshot('launch-'+row['id'].split(':')[1]);save()
    tx,tz=row['load']['targetX'],row['load']['targetZ']
    g.command('gamemode spectator TestPlayer')
    # Observe the target, outside the blast; do not touch projectile positions.
    g.command(f'tp TestPlayer {tx+75} 30 {tz-90} 40 30')
    def completed():
        row['observed']=qa('missilestatus')
        row['observed']['minimumTasks']=48 if row['id'].endswith('_cluster') else 2 if row['id'].endswith('_kinetic') else 1
        if row['id'].endswith('_cluster'): assert row['observed']['completedTasks']<=48, row['observed']
        save()
        return missile_complete(row['observed'])
    wait(completed,360)
    g.command('gamemode creative TestPlayer')
    impacts=[e for e in row['observed'].get('entities',[]) if e['mode']==1 and e['removed']]
    # MIRV craters are scattered around the nominal target; observe a real child
    # endpoint instead of photographing untouched grass at the footprint centre.
    ix,iz=(impacts[0]['x'],impacts[0]['z']) if impacts else (tx,tz)
    g.command(f'tp TestPlayer {ix+30} -50 {iz-30} 45 25')
    time.sleep(4)
    g.screenshot('impact-'+row['id'].split(':')[1])
    g.command(f'forceload remove -16 {z-16} 32 {z+32}')
    row['method']='survival pad item placement -> consume missile -> real 150-tick load -> schedule -> flight/warheads -> destructive jobs finish'


def craft(row,index):
    x,z=5000+index*192,0
    reset_player(x,z)
    if not row['air']:
        g.command('gamemode creative TestPlayer')
        for y in range(-60,-50):
            g.command(f'fill {x-48} {y} -48 {x+48} {y} 48 minecraft:water')
        g.command(f'tp TestPlayer {x+.5} -49 .5 0 90')
        g.command('gamemode survival TestPlayer')
    else:
        g.command(f'tp TestPlayer {x+.5} -58 .5 0 90')
    row['placed']=qa('preparecraft '+row['id']);save()
    local('view_back');time.sleep(3)
    initial=qa('craftstatus');assert initial['riding'] and initial['alive'],initial
    key('h');wait(lambda:qa('craftstatus')['engine'],30)
    row['engine']='PASS'
    def shoot():
        before=qa('craftstatus');assert before['selected']>=0,before
        assert before['loads'][before['selected']]['ammo']>0,before
        latest={}
        def fired():
            latest.update(qa('craftstatus'))
            row['lastCraftState']=dict(latest)
            return craft_fired(before,latest)
        attempts=1
        if row['id']=='aiarsenal:bismarck_ship':
            # Main battery is semiautomatic: an early click before the mounts
            # are laid latches even without a discharge. Retry REAL clicks,
            # not ammo/turret/cooldown writes; still require ammo + projectile.
            from qa_clicks import click_until
            attempts=click_until(g.key,fired,lambda:g.view_action(g.LOGS/'client.log','view_ready'),75)
        else:
            g.key('mousedown','1')
            try:wait(fired,75)
            finally:g.key('mouseup','1')
        return {'before':before,'after':latest,'mouseDownAttempts':attempts}
    row['firstShot']=shoot();save()
    row['service']=qa('servicecraft')
    slot=row['firstShot']['before']['selected']
    assert row['service']['loads'][slot]['ammo']==row['placed']['loads'][slot]['ammo'],row
    assert row['service']['fuel']>.99 and row['service']['kitsRemaining']==1,row
    time.sleep(2);row['repeatShot']=shoot();save()
    g.screenshot('craft-'+row['id'].split(':')[1])
    row['method']='survival item placement/boarding; real H and LMB; production service-kit interaction; real repeat LMB plus projectile/bullet proof'
    row['unverified']='other weapon slots, full flight/navigation, defensive AI, alternate presets, GUI servicing'
    g.command('ride TestPlayer dismount')


def cannon(row,index):
    reset_player(8000,500+index*128)
    row['placed']=qa('preparecannon '+row['id']);row['cycles']=[]
    for cycle in range(2):
        loaded=qa('loadcannon');assert loaded['loading']>0,loaded
        wait(lambda:qa('cannonstatus')['loading']==0,45)
        before=qa('cannonstatus');after=qa('firecannon')
        assert after['loading']==-1,after
        assert sum(after['spawned'].values())==sum(before['spawned'].values())+1,after
        row['cycles'].append(dict(loaded=loaded,before=before,after=after));save()
    g.command(f'tp TestPlayer 8000.5 -60 {500+index*128+.5} -45 10')
    time.sleep(2)
    g.screenshot('cannon-'+row['id'].split(':')[1])
    row['method']='survival cannon item placement -> real charge consumption/160-tick load -> ignition/tool wear -> cannonball spawn; twice'


def javelin(row,index):
    from verify_catalogue_game import number
    x,z=9000,800
    reset_player(x,z)
    g.command('item replace entity TestPlayer hotbar.0 with aiarsenal:javelin')
    g.command('give TestPlayer aiarsenal:javelin_missile 2')
    row['cycles']=[]
    for n in range(2):
        tag='context_javelin_'+str(n)
        g.command(f'summon minecraft:cow {x+.5} -60 {z+40.5} {{Tags:["{tag}"],NoAI:1b,PersistenceRequired:1b}}')
        g.command(f'tp TestPlayer {x+.5} -60 {z+.5} 0 1')
        g.key('mousedown','3')
        try:wait(lambda:number('Lock')>=100,30)
        finally:g.key('mouseup','3')
        def consumed():
            text=g.command('clear TestPlayer aiarsenal:javelin_missile 0')
            return ('Found 1 matching' in text) if n==0 else ('No items were found' in text)
        wait(consumed,20)
        wait(lambda:'Test passed' in g.command(f'execute unless entity @e[tag={tag}]'),45)
        row['cycles'].append(dict(lock=100,remainingAmmo=1-n,targetGone=True));save()
        time.sleep(2)
    g.screenshot('javelin-repeat')
    row['method']='actual RMB hold -> seeker lock -> release -> ammunition consumed -> target killed; twice'


def drone(row,index):
    def d(action='status'):
        text=g.command('execute as TestPlayer at @s run arsenal_drone_context '+action)
        try:state=json.loads(text)
        except ValueError:raise AssertionError(action+' -> '+text)
        if action=='status':row['lastObserved']=state;save()
        return state
    def restored():
        state=d();return (not state['session'] and state['cameraPlayer'] and state['mode']=='survival'
                         and state['originDistance']<2 and not state['dummyPresent'])
    reset_player(10000+index*512,2000)
    row['placed']=d('prepare '+row['id']);save()
    # Real players interact only after the spawned entity has been tracked. Do
    # not send camera/remote input in the same server tick as entity creation.
    time.sleep(3)
    marker='REMOTE CLIENT QA READY id='+str(row['placed']['entityId'])
    def client_count(text):return (g.LOGS/'client.log').read_text(errors='replace').count(text)
    ack_before=client_count(marker)
    stop_before=client_count('REMOTE CLIENT QA STOP')
    row['start']=d('link');save()
    try:
        wait(lambda:client_count(marker)>ack_before,45)
        row['clientCameraReady']='PASS'
        g.key('keydown','space')
        try:
            def airborne():
                state=d();assert state['session'], 'remote session ended before takeoff: '+str(state)
                return state['y']>row['start']['y']+3
            wait(airborne,90)
        finally:g.key('keyup','space')
        row['flying']=d();g.screenshot('drone-'+row['id'].split(':')[1]);save()
        if 'fpv' in row['id']:
            key('f')
            wait(lambda:d()['removed'] and d()['completedTasks']>0 and d()['activeBlasts']==0,90)
            wait(restored,30)
            wait(lambda:client_count('REMOTE CLIENT QA STOP')>stop_before,30)
            row['detonation']=d()
            row['method']='survival placement -> goggles link/camera -> actual Space takeoff -> F detonation -> automatic operator/camera/dummy restoration'
        else:
            wait(lambda:d()['state']==2,90)
            row['designation']=d('designate')
            for n in range(2):
                before=d();key('f')
                wait(lambda:d()['ammo']<before['ammo'] and d()['projectiles']>before['projectiles'],30)
                time.sleep(2)
            wait(lambda:d()['completedTasks']>=2 and d()['activeBlasts']==0,120)
            row['fired']=d();key('BackSpace');wait(restored,30)
            wait(lambda:client_count('REMOTE CLIENT QA STOP')>stop_before,30)
            row['firstExit']=d();ack_before=client_count(marker)
            stop_before=client_count('REMOTE CLIENT QA STOP')
            d('reconnect');assert d()['session']
            wait(lambda:client_count(marker)>ack_before,45)
            waiting_before=client_count('REMOTE CLIENT QA WAITING')
            time.sleep(2)
            g.screenshot('drone-reconnect-'+row['id'].split(':')[1])
            assert client_count('REMOTE CLIENT QA WAITING')==waiting_before, 'camera tracking lost during reconnect capture'
            key('BackSpace');wait(restored,30)
            wait(lambda:client_count('REMOTE CLIENT QA STOP')>stop_before,30)
            row['secondExit']=d()
            row['method']='survival placement -> tablet link -> actual Space takeoff -> production designation -> actual F twice/projectiles/impacts -> Backspace restore -> controller reconnect -> Backspace restore'
            row['unverified']='full RTB/landing, rearming and long-range loss-of-link'
    finally:
        g.key('keyup','space')
        g.command('execute as TestPlayer run arsenal_drone_context cleanup')


def tool(row,index):
    def t(action):return json.loads(g.command('execute as TestPlayer at @s run arsenal_tool_context '+action))
    reset_player(13000+index*128,2000)
    if index==0:
        row['start']=t('trench')
        wait(lambda:t('trenchstatus')['floors']==5 and t('trenchstatus')['open']==5 and t('trenchstatus')['pending']==0,30)
        row['result']=t('trenchstatus');assert row['result']['toolDamage']>0
        row['method']='production two-point tool interactions -> five actual dug cells and duckboard floors -> durability loss'
    elif index==1:
        row['result']=t('arming');assert row['result']['armedThenDisarmed'] and row['result']['plugCount']==1
        row['method']='survival RDS-4 item placement -> production arming plug interaction -> armed -> disarmed'
    else:
        row['start']=t('ied');wait(lambda:t('iedstatus')['armed'],30)
        g.key('mousedown','3');time.sleep(.6);g.key('mouseup','3')
        wait(lambda:not t('iedstatus')['present'] and t('iedstatus')['completedTasks']>0 and t('iedstatus')['changedBlocks']>0 and t('iedstatus')['activeBlasts']==0,60)
        row['result']=t('iedstatus')
        row['method']='survival IED placement -> production sneak-link -> actual arming delay -> real RMB remote trigger -> completed destructive blast'
    g.screenshot('tool-'+row['id'].split(':')[1])


def run():
    global SERVER,CLIENT
    g.configure('server',server=True)
    client=g.RUNS/'client';client.mkdir(parents=True,exist_ok=True)
    (client/'options.txt').write_text('''version:3465
lang:ru_ru
tutorialStep:none
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
renderDistance:4
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
    cmd=prepare(g)
    (g.RUNS/'server/user_jvm_args.txt').write_text('-Xmx2560M\n-XX:+UseSerialGC\n-Daiarsenal.stressQa=true\n')
    SERVER,server_log=g.launch('server',['bash','run.sh','nogui'],cwd=g.RUNS/'server');g.wait_log(SERVER,server_log,'Done (')
    CLIENT,client_log=g.launch('client',cmd,cwd=client);g.wait_log(SERVER,server_log,'TestPlayer joined the game');time.sleep(15)
    window=subprocess.check_output(['xdotool','search','--name','Minecraft'],text=True).splitlines()[-1]
    g.key('windowfocus','--sync',window)
    for command in ['time set 6000','gamerule doDaylightCycle false','weather clear','gamerule doWeatherCycle false',
                    'gamerule doMobSpawning false','gamerule naturalRegeneration false','gamerule keepInventory true','difficulty peaceful']:
        g.command(command)
    local('unpack');g.wait_log(CLIENT,client_log,'FX UNPACK PASS',60)
    inventory=qa('inventory');REPORT['inventory']=inventory
    cases=([(dict(id='aiarsenal:'+id),missile,i) for i,id in enumerate(inventory['missiles'])]+
           [(dict(row),craft,i) for i,row in enumerate(inventory['craft'])]+
           [(dict(id='aiarsenal:'+id),cannon,i) for i,id in enumerate(['cannon_fr6','cannon_ri12'])]+
           [(dict(id='aiarsenal:javelin'),javelin,0)]+
           [(dict(id='aiarsenal:'+id),drone,i) for i,id in enumerate(['fpv7','fpv10','mq9','orion'])]+
           [(dict(id='aiarsenal:'+id),tool,i) for i,id in enumerate(['entrenching_tool','arming_plug','remote_detonator'])])
    REPORT['expectedCases']=len(cases);save()
    for row,action,index in cases:
        row['status']='RUNNING';REPORT['cases'].append(row);save()
        try:
            action(row,index);row['status']='PASS'
            print('CONTEXT PASS',row['id'],flush=True)
        except Exception as error:
            row.update(status='FAIL',error=repr(error),traceback=traceback.format_exc())
            print('CONTEXT FAIL',row['id'],repr(error),flush=True)
            if SERVER.poll() is not None or CLIENT.poll() is not None:raise
        finally:
            g.key('mouseup','1');g.key('mouseup','3')
            if action is missile and row['status']=='FAIL' and SERVER.poll() is None:
                row['failedCaseCleanup']=qa('abortmissiles')
            save()
    REPORT['recipes']=json.loads(g.command('execute as TestPlayer run arsenal_tool_context recipes'))
    recipe_file=g.RUNS/'server/logs/context-recipes.json'
    (g.LOGS/'context-recipes.json').write_bytes(recipe_file.read_bytes());save()
    assert REPORT['recipes']['status']=='PASS', REPORT['recipes']
    g.command('arsenal_stress catalogue',expect='CATALOGUE')
    g.command('arsenal_stress archive',expect='ARCHIVE SAVED')
    g.command('save-all flush',expect='Saved');g.command('stop');SERVER.wait(timeout=120)
    os.killpg(CLIENT.pid,signal.SIGTERM);CLIENT.wait(timeout=45)
    SERVER,reopen_server=g.launch('server-reopen',['bash','run.sh','nogui'],cwd=g.RUNS/'server');g.wait_log(SERVER,reopen_server,'Done (')
    CLIENT,reopen_client=g.launch('client-reopen',cmd,cwd=client);g.wait_log(SERVER,reopen_server,'TestPlayer joined the game');time.sleep(10)
    g.command('arsenal_stress archivecheck',expect='ARCHIVE VERIFIED 448')
    REPORT['worldSaveReopen']=dict(status='PASS',archivedItems=448);save()
    for path in [server_log,reopen_server,client_log,reopen_client]:
        text=path.read_text(errors='replace')
        bad=re.findall(r'^.*(?:Failed to store chunk|ConcurrentModificationException|Exception ticking|Exception in server tick loop|OutOfMemoryError|EXCEPTION_ACCESS_VIOLATION|GL renderer disabled|OpenGL Error|GL_INVALID|\[Render thread/ERROR\]|Ошибка в задаче взрыва).*$',text,re.M)
        assert not bad,path.name+': '+'\n'.join(bad[-20:])
    g.command('stop');SERVER.wait(timeout=120)
    assert_complete(REPORT)


if __name__=='__main__':
    try:
        run();REPORT['runOutcome']={'status':'PASS'}
    except BaseException as error:
        REPORT['runOutcome']={'status':'FAIL','error':repr(error)};raise
    finally:
        save();g.cleanup()
