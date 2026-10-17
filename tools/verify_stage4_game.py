"""Installed Minecraft: actual LMB/R/interaction, server ammo/NBT, F2, continuous audio/video.
No command directly spawns the new shot/exhaust/blast particles. Disposable QA world only.
"""
import time,re,json,math
from qa_recording import Recording

def run(g,log,window):
 def view(action):g.view_action(log,action)
 def wait(predicate,seconds=18):
  end=time.monotonic()+seconds
  while time.monotonic()<end:
   if predicate():return
   time.sleep(.2)
  raise AssertionError('Stage4 server state did not reach expected result')
 def ammo():
  text=g.command('data get entity TestPlayer SelectedItem');m=re.search(r'Ammo: (\d+)',text)
  return int(m[1]) if m else 0
 def use():g.key('mousedown','3');time.sleep(.35);g.key('mouseup','3')
 def equip(name,nbt=''):g.command('item replace entity TestPlayer hotbar.0 with aiarsenal:'+name+nbt);g.key('key','1');time.sleep(.7)
 def aim(x,y,z,tx,ty,tz):
  dx=tx-x;dz=tz-z;dy=ty-y-1.62
  g.command(f'tp TestPlayer {x:.3f} {y:.3f} {z:.3f} {-math.degrees(math.atan2(dx,dz)):.3f} {-math.degrees(math.atan2(dy,math.hypot(dx,dz))):.3f}')
 g.key('mouseup','3');g.key('mouseup','1');view('view_first');view('view_fov70');view('view_ready')
 g.command('clear TestPlayer');g.command('gamemode survival TestPlayer');g.command('difficulty normal');g.command('gamerule doMobLoot false')
 g.command('effect give TestPlayer minecraft:resistance 9999 4 true');g.command('effect give TestPlayer minecraft:saturation 9999 10 true')
 for name,supply,country in [('musket_1777','paper_cartridge','fr'),('musket_1808','paper_cartridge','ri'),('pistol_an13','pistol_cartridge','fr'),('pistol_1809','pistol_cartridge','ri')]:
  g.command('clear TestPlayer');g.command('tp TestPlayer 0.5 -60.0 0.5 0 0')
  for slot in ['head','chest','legs','feet']:g.command(f'item replace entity TestPlayer armor.{slot} with aiarsenal:uniform_nap_{country}_infantry_{slot}')
  g.command('kill @e[tag=qa_nap_target]')
  g.command('summon minecraft:husk 0.5 -60.0 8.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["qa_nap_target"],Attributes:[{Name:"minecraft:generic.max_health",Base:1000d}],Health:1000f}', 'Summoned')
  equip(name,'{Ammo:1}');g.command('give TestPlayer aiarsenal:'+supply+' 4')
  record=Recording(g,'1812-'+name,window=window)
  g.screenshot(name+'-hip');g.key('mousedown','3');time.sleep(1);g.screenshot(name+'-ads')
  start=log.stat().st_size
  try:g.key('mousedown','1');wait(lambda:ammo()==0,5)
  finally:g.key('mouseup','1')
  def powder_received():
   with log.open('rb') as f:f.seek(start);return 'FX EMITTER profile=2' in f.read().decode(errors='replace')
  wait(powder_received,10)
  # F2 is handled before rendering: let the client render the received discharge,
  # not the last pre-shot framebuffer while RCON is already reporting Ammo=0.
  time.sleep(1);g.screenshot(name+'-powder-shot');g.key('mouseup','3')
  with log.open('rb') as f:f.seek(start);text=f.read().decode(errors='replace')
  assert 'FX EMITTER profile=2' in text,'actual discharge did not create powder volume'
  hp=g.command('data get entity @e[tag=qa_nap_target,limit=1] Health');match=re.search(r'data: ([0-9.]+)f',hp)
  assert match and float(match[1])<1000, 'flintlock did not hit live server target: '+hp
  time.sleep(.5);record.reload();g.key('keydown','r');time.sleep(.3);g.key('keyup','r')
  time.sleep(1.4);g.screenshot(name+'-loading-paper');time.sleep(2.4);g.screenshot(name+'-ramrod')
  wait(lambda:ammo()==1,15);g.screenshot(name+'-ready');record.stop()
 g.command('kill @e[tag=qa_nap_target]');g.command('difficulty peaceful')
 for name,country in [('sabre_briquet','fr'),('sabre_1809','ri')]:
  for slot in ['head','chest','legs','feet']:g.command(f'item replace entity TestPlayer armor.{slot} with aiarsenal:uniform_nap_{country}_infantry_{slot}')
  equip(name);time.sleep(3);g.screenshot(name+'-hand');view('view_fov45');view('view_front');g.command('tp TestPlayer 0.5 -60.0 0.5 25 3');g.screenshot(name+'-third');view('view_first');view('view_fov70')
 for name,charge in [('cannon_fr6','charge_fr6'),('cannon_ri12','charge_ri12')]:
  g.command('kill @e[tag=qa_cannon]');g.command(f'summon aiarsenal:{name} 0.5 -60.0 5.5 '+'{Tags:["qa_cannon"],Rotation:[0f,0f]}')
  aim(2.2,-60,3.7,.5,-59.3,5.5);equip(charge)
  selector='@e[tag=qa_cannon,limit=1]'
  def loaded():return re.search(r'data: 0\b',g.command('data get entity '+selector+' LoadTicks'))
  record=Recording(g,'1812-'+name,window=window)
  try:
   g.key('mousedown','3');wait(lambda:bool(re.search(r'data: [1-9]\d*\b',g.command('data get entity '+selector+' LoadTicks'))),5)
  finally:g.key('mouseup','3')
  g.screenshot(name+'-load')
  wait(loaded,20);g.command('item replace entity TestPlayer hotbar.0 with minecraft:flint_and_steel');time.sleep(.7)
  g.screenshot(name+'-ready');start=log.stat().st_size
  try:
   g.key('mousedown','3');wait(lambda:bool(re.search(r'data: -1\b',g.command('data get entity '+selector+' LoadTicks'))),5)
  finally:g.key('mouseup','3')
  time.sleep(.3);g.screenshot(name+'-fire')
  assert re.search(r'data: -1\b',g.command('data get entity '+selector+' LoadTicks')),'cannon did not fire'
  with log.open('rb') as f:f.seek(start);text=f.read().decode(errors='replace')
  assert 'FX EMITTER profile=3' in text,'actual cannon ignition did not create volume'
  time.sleep(2);g.screenshot(name+'-smoke');time.sleep(3);record.stop()
 g.command('kill @e[tag=qa_cannon]');g.command('gamemode creative TestPlayer');g.command('clear TestPlayer')
 for action,label in [('view_naparms','weapons'),('view_napguns','artillery'),('view_napammo','ammo')]:
  view(action);g.screenshot('1812-catalog-'+label);g.key('key','Escape')
 # Actual RPG projectile hits the wall and detonates through gameplay; not a showcase packet.
 g.command('tp TestPlayer 0.5 -60.0 0.5 0 -5');view('view_first');view('view_fov70')
 equip('rpg7','{Ammo:1}');g.command('tp TestPlayer 0.5 -60.0 0.5 0 0')
 record=Recording(g,'custom-ordinary-explosion',window=window);start=log.stat().st_size
 try:
  g.key('mousedown','1');wait(lambda:ammo()==0,5)
 finally:g.key('mouseup','1')
 time.sleep(1.5);g.screenshot('custom-rpg-impact');time.sleep(3);g.screenshot('custom-rpg-smoke');time.sleep(3);record.stop()
 with log.open('rb') as f:f.seek(start);text=f.read().decode(errors='replace')
 assert 'FX ARSENAL BLAST' in text,'actual RPG impact did not use custom explosion smoke'
 g.command('clear TestPlayer');g.command('forceload add -32 16 32 80');time.sleep(2)
 # Real turbine flags drive live engine callbacks on both bomber types, preserving their flight physics.
 for name in ['tu95ms','tu160']:
  g.command('kill @e[tag=qa_engine]');g.command('fill -35 -61 16 35 -61 80 minecraft:smooth_stone')
  # Exercise vanilla summon again: production now reconciles its final location
  # before the first physics step, rather than relying on the placement-command path.
  g.command(f'summon aiarsenal:{name} 0.5 -60.0 48.5 '+'{Tags:["qa_engine"],flags:84,qx:0f,qy:0f,qz:0f,qw:1f,throttle:0.2f,thrust:0.2f,ground:1b}', 'Summoned')
  aim(16.5,-60,24.5,0.5,-56,41.5);start=log.stat().st_size
  record=Recording(g,'engine-'+name,window=window);time.sleep(5)
  state=g.command('data get entity @e[tag=qa_engine,limit=1]');assert 'No entity' not in state,state
  pos=g.command('data get entity @e[tag=qa_engine,limit=1] Pos')
  xyz=re.search(r'data: \[([-0-9.Ee]+)d, ([-0-9.Ee]+)d, ([-0-9.Ee]+)d\]',pos)
  assert xyz and abs(float(xyz[1])-.5)<2 and abs(float(xyz[2])+60)<2 and abs(float(xyz[3])-48.5)<2, 'bomber moved away from its spawn: '+pos
  health=re.search(r'health: ([0-9.]+)f',state);assert health and float(health[1])>0,state
  flags=re.search(r'flags: (\d+)',state);assert flags and int(flags[1])&4 and not int(flags[1])&32,state
  g.screenshot('engine-'+name+'-exhaust');time.sleep(2);record.stop()
  g.command('data get entity @e[tag=qa_engine,limit=1] health','entity data:')
  with log.open('rb') as f:f.seek(start);text=f.read().decode(errors='replace')
  assert 'FX EMITTER profile=4' in text,'live bomber engines did not emit'
 g.command('kill @e[tag=qa_engine]');g.command('tp TestPlayer 0.5 -60.0 0.5 0 0');g.fx_action(log,'clear');g.command('forceload remove -32 16 32 80')
 # Restore only the disposable QA fixture after the real destructive bomb impact.
 g.command('fill -12 -61 -8 12 -61 25 minecraft:smooth_stone')
 g.command('fill -8 -60 22 8 -53 22 minecraft:white_concrete')
 g.command('fill -1 -57 21 1 -55 21 minecraft:red_concrete')
 (g.LOGS/'stage4-gameplay.json').write_text(json.dumps({'result':'PASS','single_shot_firearms':4,'manually_loaded_cannons':2,'bombers_with_live_exhaust':2,'ordinary_explosion':'live RPG-7 projectile impacting the target wall; actual damage path','screenshots':'Minecraft F2','videos':'visible X11 game window and native PulseAudio'},indent=2)+'\n')
