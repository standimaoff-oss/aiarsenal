#!/usr/bin/env python3
"""Real installed Oculus/Embeddium/Photon client. Exercise pack on/off, actual gun
reload with R, scopes, ordinary and nuclear visual packet fallbacks. The destructive
bomb/registry audit is separate. Software OpenGL does NOT reproduce an NVIDIA GPU.
"""
import json
import os
import traceback
import re
import subprocess
import time
import verify_game as g
from install_qa_client import prepare
from install_shaderpack_qa import install
from qa_log import tail_from
from shader_log import scan
import verify_context_game as context
from shaderpack_actions import powder_cannon, sturmtiger

PACK=os.environ.get('QA_SHADERPACK','photon')
REPORT={'result':'RUNNING','pack':PACK,'actions':[], 'toggles':[]}

def save():
    (g.LOGS/'shaderpack-gameplay.json').write_text(json.dumps(REPORT,indent=2)+'\n')



def wait(test, seconds=180):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        value=test()
        if value:
            return value
        time.sleep(1)
    raise AssertionError('shader compatibility condition timed out')


def run():
    g.configure('server',server=True)
    client=g.RUNS/'client'; client.mkdir(parents=True,exist_ok=True)
    (client/'options.txt').write_text('''version:3465
lang:ru_ru
tutorialStep:none
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
renderDistance:2
simulationDistance:5
maxFps:20
guiScale:2
fov:0.0
graphicsMode:0
pauseOnLostFocus:false
soundCategory_master:0.5
chatOpacity:0.0
fullscreen:false
overrideWidth:900
overrideHeight:600
''')
    cmd=prepare(g); install(g)
    (g.RUNS/'server/user_jvm_args.txt').write_text('-Xmx2560M\n-XX:+UseSerialGC\n-Daiarsenal.stressQa=true\n')
    server,slog=g.launch('server',['bash','run.sh','nogui'],cwd=g.RUNS/'server')
    g.wait_log(server,slog,'Done (')
    player,log=g.launch('client',cmd,cwd=client)
    context.SERVER,context.CLIENT=server,player
    g.wait_log(server,slog,'TestPlayer joined the game')
    time.sleep(15)
    window=subprocess.check_output(['xdotool','search','--name','Minecraft'],text=True).splitlines()[-1]
    g.key('windowfocus','--sync',window)
    for command in ['time set 6000','gamerule doDaylightCycle false','gamerule doMobSpawning false',
                    'gamemode creative TestPlayer','fill -12 -61 -8 12 -61 30 minecraft:smooth_stone',
                    'tp TestPlayer 0.5 -60 0.5 0 0','clear TestPlayer']:
        g.command(command)

    def local(effect):
        offset=log.stat().st_size
        g.command('arsenal_visual_test client '+effect+' TestPlayer')
        return wait(lambda: next((line for line in tail_from(log,offset).splitlines()
                                  if 'FX QA '+effect+':' in line), None))

    def status(active):
        line=local('status')
        assert 'active='+str(active).lower() in line,line
        return int(re.search(r'frames=(\d+)',line)[1])

    status(False)
    assert 'reason=active-shaderpack' in log.read_text(errors='replace'), 'pack failed to compile / API not detected'
    assert status(False)==0, 'raw volumetric draw ran with an active pack'
    # Pack compilation can outlast ReceivingLevelScreen. An acknowledged FX
    # packet alone does not mean gameplay input is enabled; verify the UI too.
    g.view_action(log,'view_first')
    g.view_action(log,'view_ready')
    time.sleep(3)
    g.screenshot(PACK+'-initial')
    reload_log_start=log.stat().st_size
    for weapon in ['ppsh41','mosin_pu','musket_1777']:
        ammo={'ppsh41':'mag_ppsh','mosin_pu':'round_762x54r','musket_1777':'paper_cartridge'}[weapon]
        g.command('clear TestPlayer')
        g.command('item replace entity TestPlayer hotbar.0 with aiarsenal:'+weapon+'{Ammo:0}')
        g.command('give TestPlayer aiarsenal:'+ammo+' 8')
        g.key('key','1');time.sleep(2)
        wait(lambda: 'aiarsenal:'+weapon in g.command('data get entity TestPlayer SelectedItem'),30)
        local('status') # client packet barrier before a real R; no shader rebind
        # Keep Oculus defaults. Production context arbitration must prevent shader
        # rebuilds here; never quietly rebind another mod to make this test pass.
        g.key('keydown','r');time.sleep(.3);g.key('keyup','r')
        capacity={'ppsh41':71,'mosin_pu':5,'musket_1777':1}[weapon]
        def fully_loaded():
            match=re.search(r'Ammo: (\d+)',g.command('data get entity TestPlayer SelectedItem'))
            return match and int(match[1])==capacity
        wait(fully_loaded)  # scoped Mosin loads individual rounds; first round is not completion
        local('status')  # wait for the client, including any shader reload, to respond
        g.key('mousedown','3');time.sleep(3)
        g.screenshot(PACK+'-'+weapon+'-ads')
        if weapon=='mosin_pu':
            g.assert_scope_mask(PACK+'-'+weapon+'-ads')
        before=int(re.search(r'Ammo: (\d+)',g.command('data get entity TestPlayer SelectedItem'))[1])
        g.key('mousedown','1')
        try:
            wait(lambda:int(re.search(r'Ammo: (\d+)',g.command('data get entity TestPlayer SelectedItem'))[1])<before,30)
        finally:
            g.key('mouseup','1');g.key('mouseup','3')
        time.sleep(3)
    for index,id in enumerate(['cannon_fr6','cannon_ri12']):
        row={'id':'aiarsenal:'+id,'result':'RUNNING'};REPORT['actions'].append(row);save()
        powder_cannon(row,index);save()
    row={'id':'aiarsenal:sturmtiger_vehicle','vehicleAmmo':'aiarsenal:rocket_380','drivable':True,'result':'RUNNING'}
    REPORT['actions'].append(row);save()
    sturmtiger(row);save()
    reload_log=tail_from(log,reload_log_start)
    assert 'Destroying pipeline' not in reload_log,'R rebuilt Oculus pipeline while reloading a weapon'
    assert 'Shader reload key conflict:' in reload_log,'default R conflict was not exercised'
    REPORT['weaponReloadShaderRebuilds']=0;save()
    g.command('gamemode creative TestPlayer')
    g.command('clear TestPlayer')
    g.command('tp TestPlayer 0.5 -60 0.5 0 0')
    g.view_action(log,'view_first')
    REPORT['fallbackObservations']=[]
    for effect in ['blast','nuke','legacy_nuke']:
        before=local('status')
        g.command('arsenal_visual_test '+effect+' 0 -60 22')
        if effect != 'blast':
            # RCON confirms sending, not client receipt. The old .2s capture
            # sometimes photographed the PREVIOUS effect before this packet.
            ack=local('status')
            g.screenshot(PACK+'-flash-'+effect)
            REPORT.setdefault('flashObservations',[]).append(dict(effect=effect,clientAck=ack))
            save()
        time.sleep(4)
        g.screenshot(PACK+'-fallback-'+effect)
        after=local('status')
        counter='safeCloudFrames' if effect=='legacy_nuke' else 'safeParticles'
        old=int(re.search(counter+r'=(\d+)',before)[1]);new=int(re.search(counter+r'=(\d+)',after)[1])
        assert new>old,{'effect':effect,'before':before,'after':after}
        REPORT['fallbackObservations'].append(dict(effect=effect,before=before,after=after));save()
    assert status(False)==0, 'raw GL pass executed under Photon'
    # With empty hands and no vehicle, R must still perform Oculus' own action.
    offset=log.stat().st_size
    g.key('keydown','r');time.sleep(.2);g.key('keyup','r')
    wait(lambda:'Creating pipeline for dimension' in tail_from(log,offset))
    status(False)
    REPORT['unarmedShaderReload']='PASS';save()
    toggles=REPORT['toggles']
    for iteration in range(2):
        g.key('key','k')
        wait(lambda: 'active=true' in local('status'))
        local('unpack')
        assert 'FX UNPACK PASS' in log.read_text(errors='replace')
        local('smoke');time.sleep(3)
        assert status(True)>0, 'vanilla volumetrics did not resume when shaderpack was disabled'
        g.screenshot(PACK+'-disabled-'+str(iteration))
        g.key('key','k')
        wait(lambda: 'active=false' in local('status'))
        before=status(False)
        g.command('arsenal_visual_test nuke 0 -60 22');time.sleep(3)
        after=status(False)
        assert after<=before,'raw GL draws continued after enabling the pack'
        toggles.append({'round':iteration,'framesBefore':before,'framesAfter':after});save()
        g.screenshot(PACK+'-reenabled-'+str(iteration))
    text=log.read_text(errors='replace')
    assert player.poll() is None and server.poll() is None
    bad,diagnostics=scan(text)
    REPORT['thirdPartyDiagnostics']=diagnostics
    assert not bad,'\n'.join(bad)
    effects=json.loads(g.command('execute as TestPlayer run arsenal_stress playerstatus'))['effects']
    assert not {'minecraft:blindness','minecraft:nausea'}.intersection(effects),effects
    REPORT.update(result='PASS',guns=['ppsh41','mosin_pu','musket_1777'],
        reloadInput='real R, Oculus defaults unchanged; cannons use real RMB',
        fallbackPackets=['blast','nuke','legacy_nuke'],playerEffects=effects,
        limits='Linux llvmpipe, not Windows/NVIDIA; Photon SH_SKYLIGHT=false (32768-byte shared memory limit), other packs publisher defaults; visual packets do not certify bomb physics or damage')
    save()
    g.command('save-all flush')
    print('SHADERPACK COMPATIBILITY PASS',flush=True)


if __name__=='__main__':
    try:
        run()
    except BaseException as error:
        REPORT.update(result='FAIL',error=type(error).__name__+': '+str(error),traceback=traceback.format_exc())
        if os.environ.get('GITHUB_ACTIONS')=='true':
            print('::error title=Shaderpack gameplay::'+REPORT['error'].replace('%','%25').replace('\n','%0A').replace('\r','%0D'),flush=True)
        for row in REPORT['actions']:
            if row.get('result')=='RUNNING':row.update(result='FAIL',error=REPORT['error'])
        raise
    finally:
        save()
        if REPORT['result']=='FAIL' and context.CLIENT is not None and context.CLIENT.poll() is None:
            try:g.screenshot(PACK+'-failure')
            except Exception:pass
        g.cleanup()
