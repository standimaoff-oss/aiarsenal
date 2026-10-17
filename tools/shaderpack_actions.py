"""Real client RMB cannon loading/ignition and R tank reload (no timer/NBT edits)."""
import time
import verify_game as g
import verify_context_game as context
import verify_catalogue_game as catalogue


def powder_cannon(row,index):
    context.reset_player(8000,500+index*128)
    row['placed']=context.qa('preparecannon '+row['id'])
    row['cycles']=[]
    charge=row['placed']['charge']
    g.command('item replace entity TestPlayer hotbar.0 with '+charge+' 2')
    g.command('item replace entity TestPlayer hotbar.1 with minecraft:flint_and_steel')

    def click(slot):
        state=context.qa('cannonstatus')
        # Explicit eye-to-hitbox aim: vanilla /tp facing uses a feet source anchor
        # unless overridden, which previously aimed ABOVE this low cannon.
        g.command(f"tp TestPlayer {state['x']} {state['y']} {state['z']-2.5} 0 20")
        g.key('key',str(slot));time.sleep(1)
        # A real teleport may take several seconds to deliver/render its chunks.
        # Never click until the actual client raycast sees the expected cannon.
        def aimed():
            aim=g.fx_action(g.LOGS/'client.log','status')
            row.setdefault('aimChecks',[]).append(aim)
            return 'target='+row['id'] in aim
        context.wait(aimed,60)
        g.key('mousedown','3');time.sleep(.25);g.key('mouseup','3')

    for cycle in range(2):
        click(1)
        context.wait(lambda:context.qa('cannonstatus')['loading']>0,15)
        loaded=context.qa('cannonstatus')
        remaining=g.command('clear TestPlayer '+charge+' 0')
        assert ('Found 1 matching' in remaining) if cycle==0 else ('No items were found' in remaining),remaining
        context.wait(lambda:context.qa('cannonstatus')['loading']==0,45)
        before=context.qa('cannonstatus');click(2)
        context.wait(lambda:context.qa('cannonstatus')['loading']==-1,15)
        after=context.qa('cannonstatus')
        assert sum(after['spawned'].values())==sum(before['spawned'].values())+1,after
        tool=g.command('data get entity TestPlayer SelectedItem')
        assert f'Damage: {cycle+1}' in tool,tool
        row['cycles'].append(dict(loaded=loaded,before=before,after=after,chargeRemaining=remaining,tool=tool))
        g.screenshot(row['id'].split(':')[1]+'-real-rmb-cycle-'+str(cycle))
    row['method']='real RMB charge / real 160-tick load / real RMB ignition, ammunition and tool wear asserted, repeated twice'
    row['result']='PASS'


def sturmtiger(row):
    g.command('gamemode creative TestPlayer')
    catalogue.equip(row)
    g.fx_action(g.LOGS/'client.log','status') # acknowledge queued client inventory/teleport packets
    g.command('execute as TestPlayer at @s run arsenal_stress use',expect='block=CONSUME')
    catalogue.vehicle(row)
    g.screenshot('sturmtiger-real-reload-repeat')
    g.command('ride TestPlayer dismount')
    g.command('kill @e[type=aiarsenal:sturmtiger]')
    row['result']='PASS'
