#!/usr/bin/env python3
"""Actual old-save upgrade: build pre-retirement source, save old blocks/items,
back up the disposable world, then open AND re-open it with current code.
Never registers test ores in the shipped mod and never touches a player's save.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import time
import verify_game as game

BASELINE = '4ced8a44d9d78cc5003e143cc3927cfd80d4275c'


def stopped(process):
    game.command('save-all flush', 'Saved')
    game.command('stop')
    assert process.wait(timeout=120) == 0, 'server did not shut down cleanly'


def start(label, installed=False):
    process, log = game.launch(label, ['bash', 'run.sh', 'nogui'] if installed else game.gradle('runServer'))
    game.wait_log(process, log, 'Done (', 900)
    game.wait_log(process, log, 'RCON running', 60)
    game.command('forceload add 0 0')
    time.sleep(2)
    return process


def check_migrated():
    for x, block in [(0, 'stone'), (1, 'deepslate')]:
        marker = 'MIGRATED_' + block.upper()
        game.command(f'execute if block {x} -60 0 minecraft:{block}', 'Test passed')
        print(marker,flush=True)
    for slot, item, count in [(0, 'iron_nugget', 9), (1, 'iron_ingot', 7), (2, 'stone', 3), (3, 'deepslate', 2)]:
        marker = 'MIGRATED_SLOT_' + str(slot)
        query = '{Slot:' + str(slot) + 'b,id:"minecraft:' + item + '",Count:' + str(count) + 'b}'
        game.command('execute if data block 2 -60 0 Items[' + query + ']', 'Test passed')
        print(marker,flush=True)
    # An unrelated retained item must survive in the SAME persisted chest, not only in a registry assertion.
    game.command('execute if data block 2 -60 0 Items[{Slot:4b,id:"aiarsenal:rds1",Count:1b}]', 'Test passed')
    game.command('execute if data block 2 -60 0 Items[{Slot:5b,id:"aiarsenal:shahed136",Count:1b}]', 'Test passed')
    game.command('data get block 2 -60 0 Items')


def main():
    current = game.MOD
    work = Path(tempfile.mkdtemp(prefix='arsenal-legacy-', dir=os.environ.get('RUNNER_TEMP')))
    # Archive, not checkout: never changes the Arena branch or the current worktree.
    archive = work / 'source.tar'
    subprocess.run(['git', 'fetch', '--no-tags', 'origin', BASELINE], cwd=game.ROOT, check=True)
    with archive.open('wb') as f:
        subprocess.run(['git', 'archive', BASELINE, 'mods/aiarsenal'], cwd=game.ROOT, stdout=f, check=True)
    subprocess.run(['tar', '-xf', str(archive), '-C', str(work)], check=True)
    game.RUNS = work / 'runtime'
    os.environ['MC_RUNS'] = str(game.RUNS)
    game.configure('server', server=True)  # refuses a pre-existing world
    game.MOD = work / 'mods/aiarsenal'
    old = start('migration-old-server')
    game.command('setblock 0 -60 0 aiarsenal:uranium_ore', 'Changed')
    game.command('setblock 1 -60 0 aiarsenal:deepslate_uranium_ore', 'Changed')
    contents = [(0, 'raw_uranium', 9), (1, 'uranium_ingot', 7), (2, 'uranium_ore', 3),
                (3, 'deepslate_uranium_ore', 2), (4, 'rds1', 1), (5, 'shahed136', 1)]
    items = ','.join('{Slot:%db,id:"aiarsenal:%s",Count:%db}' % row for row in contents)
    game.command('setblock 2 -60 0 minecraft:chest{Items:[' + items + ']}', 'Changed')
    game.command('data get block 2 -60 0 Items')
    stopped(old)
    backup = game.OUT / 'legacy-world-before-upgrade.tar.gz'
    with tarfile.open(backup, 'w:gz') as f:
        f.add(game.RUNS / 'server/world', arcname='world')
    report = {'baseline': BASELINE, 'current': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=game.ROOT, text=True).strip(),
              'backup_sha256': hashlib.sha256(backup.read_bytes()).hexdigest()}
    (game.LOGS / 'migration-provenance.json').write_text(json.dumps(report, indent=2) + '\n')
    # The upgraded server is an ACTUAL Forge installation with the built/reobfuscated release JAR,
    # not a second userdev launch. Thus migration also detects server-side packaging/classloading errors.
    folder = game.RUNS / 'server'
    installer = work / 'forge-installer.jar'
    url = 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-installer.jar'
    subprocess.run(['curl','--fail','--retry','3','-L',url,'-o',str(installer)],check=True)
    expected = subprocess.check_output(['curl','--fail','--retry','3','-L',url+'.sha1'],text=True).split()[0]
    assert hashlib.sha1(installer.read_bytes()).hexdigest() == expected, 'Forge installer checksum mismatch'
    process, log = game.launch('forge-installer', ['java','-jar',str(installer),'--installServer',str(folder)])
    assert process.wait(timeout=900) == 0, 'Forge installation failed: '+str(log)
    (folder/'user_jvm_args.txt').write_text('-Xmx1024M\n-XX:+UseSerialGC\n')
    (folder/'mods').mkdir(exist_ok=True)
    jars = [p for p in (current/'build/libs').glob('aiarsenal-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar'))]
    assert len(jars) == 1, jars
    shutil.copy2(jars[0],folder/'mods'/jars[0].name)
    report['installed_jar'] = jars[0].name
    report['installed_jar_sha256'] = hashlib.sha256(jars[0].read_bytes()).hexdigest()
    report['forge_installer_sha1'] = expected
    (game.LOGS / 'migration-provenance.json').write_text(json.dumps(report,indent=2)+'\n')
    game.MOD = folder
    upgraded = start('migration-upgraded-server', installed=True)
    check_migrated()
    stopped(upgraded)
    reopened = start('migration-reopened-server', installed=True)
    check_migrated()
    stopped(reopened)
    report['result'] = 'PASS: upgrade and second reopen; blocks + item counts + retained nuclear/drone items'
    (game.LOGS / 'migration-provenance.json').write_text(json.dumps(report, indent=2) + '\n')
    print('OLD WORLD MIGRATION PASS — installed release JAR, real saved chunks and chest, then a second server restart', flush=True)


if __name__ == '__main__':
    try:
        main()
    finally:
        game.cleanup()
