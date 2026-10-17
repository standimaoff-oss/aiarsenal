"""Runs inside the installed-client driver; all screenshots are real Minecraft F2.
Remote holder is a SECOND installed Forge client connected to the same dedicated server.
"""
import hashlib,json,os,re,shutil,time,uuid
from historical_uniforms import SETS,ROOT

def run(g, server, server_log, client_log, main_window):
 def view(action):g.view_action(client_log,action);time.sleep(.8)
 def equip(player,name):
  for slot in ['head','chest','legs','feet']:
   g.command(f'item replace entity {player} armor.{slot} with aiarsenal:uniform_{name}_{slot}')
  text=g.command(f'data get entity {player} Inventory')
  for slot in ['head','chest','legs','feet']:assert 'uniform_'+name+'_'+slot in text
 g.key('key','Escape');g.key('mouseup','3');g.key('mouseup','1')
 view('view_first');view('view_fov45');g.command('clear TestPlayer')
 g.command('tp TestPlayer 0 -60 0 0 0')
 for name in SETS:
  equip('TestPlayer',name)
  view('view_front');view('view_ready');time.sleep(1.2);g.screenshot('uniform-'+name+'-front')
  view('view_back');view('view_ready');time.sleep(.6);g.screenshot('uniform-'+name+'-back')
 # The new era grids must also be reachable in the actual creative screen.
 for action, name in [('view_ww2wear','ww2'),('view_napwear','1812')]:
  view(action);g.screenshot('catalog-'+name+'-uniforms');g.key('key','Escape')
 view('view_front');g.key('keydown','Shift_L');time.sleep(1);g.screenshot('uniform-crouching');g.key('keyup','Shift_L')
 # A real resource reload with the new garment still equipped.
 view('view_front');start=client_log.stat().st_size;view('view_reload')
 deadline=time.monotonic()+120
 while time.monotonic()<deadline:
  with client_log.open('rb') as log:log.seek(start);fresh=log.read().decode('utf-8',errors='replace')
  if 'VIEW RELOAD PASS' in fresh:break
  time.sleep(.2)
 else:raise AssertionError('historical uniform resource reload did not complete')
 time.sleep(2);view('view_ready');g.screenshot('uniform-resource-reload')
 equip('TestPlayer','ww2_su_officer');view('view_first');view('view_fov70')
 for gun in ['c96','tt33']:
  g.command(f'item replace entity TestPlayer hotbar.0 with aiarsenal:{gun}'+('{Ammo:10}' if gun=='c96' else '{Ammo:8}'))
  g.key('key','1');time.sleep(2);g.screenshot('grip-'+gun+'-uniform-hip')
  g.key('mousedown','3');time.sleep(2);g.screenshot('grip-'+gun+'-uniform-ads');g.key('mouseup','3')
 # Launch an independent client with its own options/logs/mods, shared read-only installed libraries.
 guest=g.RUNS/'observer';guest.mkdir()
 shutil.copytree(g.RUNS/'client/mods',guest/'mods')
 options=(g.RUNS/'client/options.txt').read_text()
 options=re.sub(r'soundCategory_master:.*','soundCategory_master:0.0',options)
 options=re.sub(r'maxFps:.*','maxFps:10',options)
 (guest/'options.txt').write_text(options)
 cmd=json.loads((g.LOGS/'installed-command.json').read_text())
 def arg(key,value):
  assert key in cmd,key;cmd[cmd.index(key)+1]=value
 arg('--username','Observer');arg('--gameDir',str(guest))
 arg('--uuid',str(uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:Observer').digest(),version=3)))
 cmd=[x.replace('-Xmx2048M','-Xmx1536M') for x in cmd]
 observer,observer_log=g.launch('observer',cmd,cwd=guest)
 g.wait_log(server,server_log,'Observer joined the game',900)
 time.sleep(8);g.command('gamemode creative Observer');g.command('clear Observer')
 g.key('windowfocus','--sync',main_window)
 view('view_first');view('view_fov45');g.command('clear TestPlayer')
 g.command('tp TestPlayer 0 -60 0 0 7')
 equip('Observer','ww2_de_officer');g.command('tp Observer 0 -60 3.8 145 0')
 g.command('list','Observer')
 kinds=re.findall(r'^\s+\w+\("([^"]+)"',(ROOT/'mods/aiarsenal/src/main/java/ru/aiforge/realwar/handheld/GunKind.java').read_text(),re.M)
 for name in kinds:
  g.command(f'item replace entity Observer weapon.mainhand with aiarsenal:{name}'+'{Ammo:1}')
  g.command('data get entity Observer SelectedItem',f'aiarsenal:{name}')
  time.sleep(1.6);g.screenshot('remote-'+name+'-grip')
 # Profile views distinguish actual size/hand contact from front-view foreshortening.
 g.command('tp Observer 0 -60 3.8 90 0')
 for name in ['c96','tt33','ppsh41','mosin_pu']:
  g.command(f'item replace entity Observer weapon.mainhand with aiarsenal:{name}'+'{Ammo:1}')
  time.sleep(1.6);g.screenshot('remote-'+name+'-side-grip')
 g.command('tp Observer 0 -60 3.8 145 0')
 # Left-handed remote profile travels through real client settings packets.
 windows=g.subprocess.check_output(['xdotool','search','--name','Minecraft'],text=True).splitlines()
 guest_window=next(w for w in reversed(windows) if w!=main_window)
 g.command('arsenal_visual_test client view_left Observer')
 g.command('item replace entity Observer weapon.mainhand with aiarsenal:tt33{Ammo:8}')
 time.sleep(2);g.screenshot('remote-tt33-left-handed')
 g.command('arsenal_visual_test client view_right Observer')
 for name in ['ww2_su_pilot','ww2_de_tanker','nap_fr_infantry','nap_ri_general']:
  equip('Observer',name);g.command('item replace entity Observer weapon.mainhand with minecraft:air')
  g.command('tp Observer 0 -60 3.8 180 0');time.sleep(1.5);g.screenshot('remote-uniform-'+name)
 # Ensure the real second client did not disconnect/crash during inspection.
 assert observer.poll() is None,'second client exited'
 g.command('list','Observer')
 (g.LOGS/'stage2-multiplayer.json').write_text(json.dumps({'result':'PASS','real_clients':['TestPlayer','Observer'],
  'uniform_sets':list(SETS),'remote_firearms':kinds,'left_handed_remote':'tt33',
  'same_server':'installed Forge dedicated server; loopback QA only'},indent=2)+'\n')
 if os.environ.get('QA_STAGE3')=='1':
  from verify_stage3_game import run as motion_run
  motion_run(g,client_log,observer_log,main_window)
 print('STAGE2 PASS: 16 full wearable sets, resource reload, pistol sleeves, real second-client firearm views',flush=True)
