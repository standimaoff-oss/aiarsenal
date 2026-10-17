#!/usr/bin/env python3
"""Fail closed: approve only the reviewed JAR exercised by all four current audits.
No rebuild, no tolerated gameplay failures, no promotion of generic item smoke to
full feature acceptance. Matrix jobs and their installed hashes are checked individually.
"""
import json
import re
from collections import Counter
from pathlib import Path
import sys
import subprocess

PACKS={'photon','complementary','bsl'}
RUNTIME={
 'catalogue':'Every runtime item and all free-fall bombs in installed Minecraft',
 'context':'Contextual launchpad, craft, cannon and Javelin actions in installed Minecraft',
 'shader':'Real shader client — cannon and Sturmtiger reload, scopes, fallback and toggles',
}

def audit_sources(candidate):
    """QA-only reruns may differ in commit, never in the complete mod/build tree.
    Exact installed JAR equality is checked separately for every runtime below.
    Git tree IDs include Gradle files, resources and vendored build inputs, not
    merely Java files. Missing history fails closed (delivery fetches history).
    """
    overrides=candidate.get('auditSources',{})
    assert set(overrides)<=set(candidate['runs']),'unknown audit source'
    sources={kind:overrides.get(kind,candidate['source']) for kind in candidate['runs']}
    def tree(source):
        assert re.fullmatch('[0-9a-f]{40}',source),'invalid audit source'
        return subprocess.check_output(['git','rev-parse',source+':mods/aiarsenal'],text=True).strip()
    expected=tree(candidate['source'])
    for source in set(sources.values()):
        assert tree(source)==expected,'QA rerun changed the mod/build tree'
    return sources


def check_run(data,source,kind):
    assert data['headSha']==source,'wrong source'
    assert data['status']=='completed' and data['conclusion']=='success','audit is not successful and complete'
    expected={'verify ('+pack+')' for pack in PACKS} if kind=='shader' else {'verify'}
    assert {j['name'] for j in data['jobs']}==expected and len(data['jobs'])==len(expected),'wrong job matrix'
    for job in data['jobs']:
        assert job['conclusion']=='success','job failed'
        steps=job['steps'];names={s['name']:s for s in steps}
        assert len(names)==len(steps),'duplicate steps'
        required=['Minecraft GameTests','Preserve diagnostics even on failure','Archive full log'] if kind=='gametest' else [
            'Static and protocol tests','Build release JAR',RUNTIME[kind],
            'Archive evidence and binary','Preserve incremental evidence even on failure']
        if kind in ('catalogue','context'):required.append('Minecraft GameTests')
        assert all(names.get(n,{}).get('conclusion')=='success' for n in required),'required step did not pass'
        assert all(s['conclusion'] in ('success','skipped') for s in steps),'unexpected step failure'


def check_shader(report,pack):
    assert report['result']=='PASS' and report['pack']==pack
    assert set(report['guns'])=={'ppsh41','mosin_pu','musket_1777'}
    assert report['weaponReloadShaderRebuilds']==0 and report['unarmedShaderReload']=='PASS'
    actions={r['id']:r for r in report['actions']}
    assert len(actions)==len(report['actions'])==3
    for id in ('cannon_fr6','cannon_ri12'):
        row=actions['aiarsenal:'+id]
        assert row['result']=='PASS' and len(row['cycles'])==2
        assert len(row['aimChecks'])>=4 and any('target=aiarsenal:'+id in a for a in row['aimChecks'])
    tank=actions['aiarsenal:sturmtiger_vehicle']
    assert tank['result']=='PASS' and tank['functional']=='MAIN_GUN_FIRE_RELOAD_REPEAT_PASS'
    for key in ('mainGunShot','mainGunReload','mainGunRepeatShot','drive'):
        assert tank[key].startswith('PASS'),key
    observations=report['fallbackObservations']
    assert len(observations)==3 and {r['effect'] for r in observations}=={'blast','nuke','legacy_nuke'}
    for row in observations:
        key='safeCloudFrames' if row['effect']=='legacy_nuke' else 'safeParticles'
        old=int(re.search(key+r'=(\d+)',row['before'])[1]);new=int(re.search(key+r'=(\d+)',row['after'])[1])
        assert new>old,'fallback never executed'
    flashes=report['flashObservations']
    assert len(flashes)==2 and {r['effect'] for r in flashes}=={'nuke','legacy_nuke'}
    assert all('FX QA status:' in r['clientAck'] for r in flashes),'flash capture lacked client barrier'
    assert len(report['toggles'])==2 and {r['round'] for r in report['toggles']}=={0,1}
    assert all(r['framesAfter']<=r['framesBefore'] for r in report['toggles'])
    assert not {'minecraft:blindness','minecraft:nausea'}.intersection(report['playerEffects'])


def verify(candidate,metadata,root):
    c=json.loads(Path(candidate).read_text());metadata=Path(metadata);root=Path(root)
    assert c['reviewed'] is True,'candidate has not passed visual/functional review'
    assert re.fullmatch('[0-9a-f]{40}',c['source'])
    assert re.fullmatch('[0-9a-f]{64}',c['sha256'] or ''),'no reviewed installed hash'
    assert set(c['runs'])=={'gametest','catalogue','context','shader'}
    sources=audit_sources(c)
    for kind,run in c['runs'].items():check_run(json.loads((metadata/f'{run}.json').read_text()),sources[kind],kind)
    def load(folder,file):return json.loads((root/folder/file).read_text())
    cat=Path('stability')/f"run-{c['runs']['catalogue']}"
    ctx=Path('context')/f"run-{c['runs']['context']}"
    gt=load(Path('gametest')/f"run-{c['runs']['gametest']}",'gametest-early.json')
    assert gt['source']==sources['gametest'] and not gt['failures']
    assert any('All 183 required tests passed' in line for line in gt['totals'])
    folders=[(cat,'catalogue'),(ctx,'context')]+[(Path('shaderpack')/f"run-{c['runs']['shader']}-{p}",'shader') for p in sorted(PACKS)]
    for folder,kind in folders:
        assert load(folder,'installed-provenance.json')['sha256']==c['sha256'],'different installed bytes'
        assert sources[kind] in (root/folder/'provenance.txt').read_text(),'wrong evidence provenance'
    catalogue=load(cat,'catalogue-gameplay.json');inventory=load(cat,'catalogue-inventory.json')
    context=load(ctx,'context-gameplay.json')
    assert catalogue['runOutcome']['status']=='PASS' and context['runOutcome']['status']=='PASS'
    items=catalogue['items'];assert len(items)==448 and all(r['smoke']=='PASS' for r in items)
    assert len({r['id'] for r in items})==448 and {r['id'] for r in items}=={r['id'] for r in inventory['items']}
    assert Counter(r['functional'] for r in items)==dict(NOT_VERIFIED=160,EQUIP_PASS=132,PLACE_PASS=111,
        FIRE_RELOAD_CYCLE_PASS=21,MAIN_GUN_FIRE_RELOAD_REPEAT_PASS=18,CONSUME_PASS=4,MELEE_HIT_PASS=2)
    bombs=catalogue['bombs'];assert len(bombs)==14 and all(r['status']=='PASS' for r in bombs)
    assert len({r['id'] for r in bombs})==14 and {r['id'] for r in bombs}==set(inventory['freeFallBombs'])
    assert all(not {'minecraft:blindness','minecraft:nausea'}.intersection(r['playerEffects']) for r in bombs)
    assert len(context['cases'])==35 and len({r['id'] for r in context['cases']})==35
    assert all(r['status']=='PASS' for r in context['cases'])
    assert context['recipes']['status']=='PASS' and context['recipes']['cases']==1271
    for report in (context,catalogue):
        assert report['worldSaveReopen']['status']=='PASS' and report['worldSaveReopen']['archivedItems']==448
    for pack in PACKS:check_shader(load(Path('shaderpack')/f"run-{c['runs']['shader']}-{pack}",'shaderpack-gameplay.json'),pack)
    return dict(source=c['source'],auditSources=sources,sha256=c['sha256'],runs=c['runs'],result='ACCEPTED_FOR_SCOPED_BETA',
        reviewLimits=c.get('reviewLimits',[]),
        limits='Not full functional acceptance of every mode of 448 items. Linux llvmpipe, not Windows/NVIDIA; Photon SH_SKYLIGHT=false. Three specific packs, not all shaderpacks.')

if __name__=='__main__':
    print(json.dumps(verify(*sys.argv[1:]),indent=2))
