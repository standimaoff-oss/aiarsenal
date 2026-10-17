#!/usr/bin/env python3
"""Fail-closed acceptance of the exact already-tested alpha.11 artifact.
A release-creation permission failure is not relabelled as a green workflow;
only the explicitly listed final publication steps may have failed.
"""
import json
from collections import Counter
from pathlib import Path
import sys

SOURCE='93af8c371117da14035d6fabf1225ecb9df34af5'
SHA256='dd4f997426c06dcbf1579909650cc40ae6435d7ba4c5d6b8f569a8a929a8fa82'
RUNS={
    38818319821: ('context', 'Contextual launchpad, craft, cannon and Javelin actions in installed Minecraft', 'Draft binary after successful contextual audit'),
    38818319773: ('stability', 'Every runtime item and all free-fall bombs in installed Minecraft', 'Draft binary only after successful audit'),
    38818319766: ('shaderpack', 'Real Photon client — reload, scopes, fallbacks and pack toggling', None),
}


def check_run(data, runtime_step, allowed_publication_failure):
    assert data['headSha']==SOURCE, 'wrong tested source'
    assert data['status']=='completed', 'workflow still running'
    jobs=data['jobs'];assert len(jobs)==1, 'unexpected jobs'
    steps=jobs[0]['steps']; names={s['name']:s for s in steps}
    assert len(names)==len(steps), 'duplicate step names'
    required=['Static and protocol tests','Build release JAR',runtime_step,
              'Archive evidence and binary','Preserve incremental evidence even on failure']
    if allowed_publication_failure:required.append('Minecraft GameTests')
    for name in required:
        assert names.get(name,{}).get('conclusion')=='success', 'acceptance step not successful: '+name
    bad=[s['name'] for s in steps if s['conclusion'] not in ('success','skipped')]
    assert not [name for name in bad if name!=allowed_publication_failure], 'non-publication failure: '+str(bad)
    assert data['conclusion'] in ('success','failure'), 'cancelled or unknown workflow'
    if data['conclusion']=='failure':assert bad==[allowed_publication_failure], 'unexplained workflow failure'
    else:assert not bad, 'inconsistent success metadata'
    return bad


def verify(metadata_folder, reports_folder):
    metadata_folder=Path(metadata_folder);reports_folder=Path(reports_folder);summary={}
    for run,(prefix,step,allowed) in RUNS.items():
        data=json.loads((metadata_folder/f'{run}.json').read_text())
        failures=check_run(data,step,allowed)
        folder=reports_folder/prefix/f'run-{run}'
        installed=json.loads((folder/'installed-provenance.json').read_text())
        assert installed['sha256']==SHA256, 'different installed JAR'
        summary[str(run)]={'workflowConclusion':data['conclusion'],'publicationFailures':failures,'gameplaySteps':'PASS'}
    context=json.loads((reports_folder/'context/run-38818319821/context-gameplay.json').read_text())
    assert context['runOutcome']['status']=='PASS' and len(context['cases'])==35
    assert len({row['id'] for row in context['cases']})==35 and all(row['status']=='PASS' for row in context['cases'])
    catalogue=json.loads((reports_folder/'stability/run-38818319773/catalogue-gameplay.json').read_text())
    assert catalogue['runOutcome']['status']=='PASS' and len(catalogue['items'])==448
    assert len({row['id'] for row in catalogue['items']})==448 and all(row['smoke']=='PASS' for row in catalogue['items'])
    assert Counter(row['functional'] for row in catalogue['items'])==dict(NOT_VERIFIED=160,EQUIP_PASS=132,
        PLACE_PASS=111,FIRE_RELOAD_CYCLE_PASS=21,MAIN_GUN_FIRE_RELOAD_REPEAT_PASS=18,CONSUME_PASS=4,MELEE_HIT_PASS=2)
    assert len(catalogue['bombs'])==14 and len({row['id'] for row in catalogue['bombs']})==14
    assert all(row['status']=='PASS' for row in catalogue['bombs'])
    assert context['recipes']['status']=='PASS' and context['recipes']['cases']==1271
    for report in [context,catalogue]:
        assert report['worldSaveReopen']['status']=='PASS' and report['worldSaveReopen']['archivedItems']==448
    shader=json.loads((reports_folder/'shaderpack/run-38818319766/shaderpack-gameplay.json').read_text())
    assert shader['result']=='PASS'
    return {'source':SOURCE,'sha256':SHA256,'runs':summary,
            'limits':'Not full acceptance of all 448 item modes; not Windows/NVIDIA/default-Photon-preset certification.'}


if __name__=='__main__':
    print(json.dumps(verify(*sys.argv[1:]),indent=2))
