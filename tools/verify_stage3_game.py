"""Genuine inputs -> vanilla movement -> real network player -> F2 and continuous video.
No forced renderer state is used to pass the locomotion checks.
"""
import json,re,time
from historical_uniforms import SETS
from qa_recording import Recording

def run(g,log,observer_log,window):
 def view(action):g.view_action(log,action)
 def status(player,expected):
  start=log.stat().st_size;view('view_motion')
  with log.open('rb') as f:f.seek(start);text=f.read().decode(errors='replace')
  assert f'MOTION QA {player}:{expected}:' in text,(player,expected,text[-2500:])
  return next(line.split('MOTION QA ')[1] for line in text.splitlines() if 'MOTION QA '+player+':' in line)
 def release():
  for key in ['w','Control_L','Shift_L','Alt_L']:g.key('keyup',key)
  g.key('mouseup','1');g.key('mouseup','3')
 def guest(mode):g.command('arsenal_visual_test client view_move_'+mode+' Observer','Client QA sent:')
 rows=[]
 g.key('windowfocus','--sync',window)
 release();view('view_first');view('view_fov70');g.command('gamemode survival TestPlayer')
 g.command('effect give TestPlayer minecraft:saturation 9999 10 true')
 g.command('effect give TestPlayer minecraft:resistance 9999 4 true')
 for slot in ['head','chest','legs','feet']:
  g.command(f'item replace entity TestPlayer armor.{slot} with aiarsenal:uniform_ww2_su_officer_{slot}')
 for gun,ammo in [('tt33',8),('ppsh41',71),('mosin_pu',5)]:
  g.command(f'item replace entity TestPlayer weapon.mainhand with aiarsenal:{gun}'+'{Ammo:'+str(ammo)+'}')
  recording=Recording(g,'motion-'+gun,window=window)
  for mode,keys,expected in [('idle',[],'IDLE'),('slow',['Alt_L','w'],'SLOW'),('walk',['w'],'WALK'),
                             ('run',['Control_L','w'],'SPRINT'),('crouch',['Shift_L','w'],'CROUCH')]:
   release();g.command('tp TestPlayer 0 -60 0 0 0');time.sleep(.6)
   for k in keys:g.key('keydown',k)
   time.sleep(1.2)
   rows.append({'gun':gun,'view':'first','mode':mode,'observed':status('TestPlayer',expected)})
   g.screenshot('motion-'+gun+'-'+mode)
   if gun=='ppsh41':
    view('view_back');time.sleep(.3);g.screenshot('motion-third-'+gun+'-'+mode);view('view_first')
  release();g.command('tp TestPlayer 0 -60 0 0 0');time.sleep(.6)
  g.key('mousedown','3');g.key('keydown','w');time.sleep(1)
  g.screenshot('motion-'+gun+'-walking-ads')
  # Real shot + reload exercise compositing and ensure the continuous recording has actual game audio.
  g.key('mousedown','1');time.sleep(.8);g.key('mouseup','1');release()
  g.command(f'give TestPlayer aiarsenal:'+{'tt33':'mag_tt33','ppsh41':'mag_ppsh','mosin_pu':'round_762x54r'}[gun]+' 4')
  recording.reload();g.key('key','r');g.key('keydown','w');time.sleep(1)
  g.screenshot('motion-'+gun+'-walking-reload');time.sleep(6);release();recording.stop()
 g.command('gamemode creative TestPlayer');g.command('clear TestPlayer')
 g.command('tp TestPlayer 0 -60 0 0 7');view('view_fov45')
 # Main window remains focused. The QA action presses vanilla bindings in the second client;
 # it does NOT mutate its position, sprint flag or animation state. Server movement is genuine.
 for gun in ['tt33','ppsh41']:
  g.command(f'item replace entity Observer weapon.mainhand with aiarsenal:{gun}'+'{Ammo:1}')
  for mode,expected in [('idle','IDLE'),('slow','SLOW'),('walk','WALK'),('run','SPRINT'),('crouch','CROUCH')]:
   guest('idle');g.command('tp Observer 0 -60 3.8 0 0');time.sleep(.6);guest(mode);time.sleep(1.2)
   rows.append({'gun':gun,'view':'remote','mode':mode,'observed':status('Observer',expected)})
   g.screenshot('motion-remote-'+gun+'-'+mode);guest('idle')
 guest('idle');release()
 # Same scene, same camera, same geometry, same render pipeline. ABBA avoids one-sided warm-up bias.
 g.command('tp Observer 50 -60 50 0 0');g.command('clear TestPlayer')
 g.command('tp TestPlayer 0 -60 0 0 8');view('view_fov70')
 for i,name in enumerate(SETS):
  armor=[{'id':f'aiarsenal:uniform_{name}_{slot}','Count':1} for slot in ['feet','legs','chest','head']]
  armor='['+','.join('{id:"'+a['id']+'",Count:1b}' for a in armor)+']'
  g.command(f'summon minecraft:armor_stand {(i%4-1.5)*2} -60 {7+i//4*2} '+'{NoGravity:1b,ShowArms:1b,Tags:["qa_motion_bench"],ArmorItems:'+armor+'}')
 time.sleep(3);view('view_index');g.screenshot('motion-benchmark-scene')
 samples=[]
 for mode in ['old','new','new','old']:
  start=log.stat().st_size;view('view_perf_'+mode);deadline=time.monotonic()+180
  while time.monotonic()<deadline:
   with log.open('rb') as f:f.seek(start);fresh=f.read().decode(errors='replace')
   match=re.search(r'FRAME QA mode=(\w+) frames=(\d+) renderMedianMs=([\d.]+) renderP95Ms=([\d.]+) intervalMedianMs=([\d.]+)',fresh)
   if match:break
   time.sleep(.5)
  else:raise AssertionError('frame benchmark did not finish')
  samples.append(dict(zip(['mode','frames','render_median_ms','render_p95_ms','interval_median_ms'],match.groups())))
 view('view_perf_end');g.command('kill @e[tag=qa_motion_bench]')
 # Rebuild baked indexes after an actual resource reload, then revalidate identity/order.
 start=log.stat().st_size;view('view_reload');deadline=time.monotonic()+120
 while time.monotonic()<deadline:
  with log.open('rb') as f:f.seek(start);fresh=f.read().decode(errors='replace')
  if 'VIEW RELOAD PASS' in fresh:break
  time.sleep(.2)
 else:raise AssertionError('reload did not finish')
 time.sleep(2);view('view_ready');view('view_index')
 (g.LOGS/'stage3-motion.json').write_text(json.dumps({'result':'PASS','observations':rows,'render_abba':samples,
  'limitations':'llvmpipe, fixed scene, existing FPS cap; render event timings are not hardware GPU/FPS certification'},indent=2)+'\n')
 print('STAGE3 PASS: real movement inputs, remote states, ADS/reload, exact render index, same-scene ABBA',flush=True)
