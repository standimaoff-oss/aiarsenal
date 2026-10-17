#!/usr/bin/env python3
"""Render phase-two evidence without turning partial successes into a green run."""
import json
from pathlib import Path
import sys


def render(report):
    outcome=report.get('runOutcome',{}).get('status','UNKNOWN')
    rows=report.get('cases',[])
    counts={state:sum(r.get('status')==state for r in rows) for state in ['PASS','FAIL','RUNNING']}
    lines=['# Contextual Minecraft action audit','',f'**Process outcome: {outcome}**',
           f"Recorded cases: {len(rows)}/{report.get('expectedCases','?')}; PASS {counts['PASS']}; FAIL {counts['FAIL']}; RUNNING {counts['RUNNING']}.",
           '', '**This is not full acceptance of all 448 items, all weapon slots, or Windows/NVIDIA stability.**',
           'CI build/GameTest/upload outcomes must additionally be checked on the linked workflow run.', '',
           '| Item | Result | Executed action / failure |','|---|---|---|']
    for row in rows:
        detail=row.get('error',row.get('method','incomplete'))
        lines.append('| '+row['id']+' | '+row['status']+' | '+detail.replace('|','/').replace('\n',' ')+' |')
    lines+=['','## Separate checks',
            '* Save/reopen: '+json.dumps(report.get('worldSaveReopen',{}),ensure_ascii=False),
            '* Recipe-context checks: '+json.dumps(report.get('recipes',{}),ensure_ascii=False),
            '* Recipes test loaded ingredient grids, matching, assembly and remainder sizes; they do not prove use of the crafted output.',
            '* Craft cases test primary-weapon fire, service-kit consumption/rearm and repeat fire, not every alternate slot or flight mode.',
            '* Drone cases distinguish real client input from server-side placement/link/designation fixtures.',
            '* Launchpad cases retain references to every missile/warhead; unloading is not mistaken for successful impact.',
            '* Screenshots are original Minecraft F2 captures, not fabricated gameplay images.','']
    if 'error' in report.get('runOutcome',{}):lines+=['## Process failure','',report['runOutcome']['error'],'']
    return '\n'.join(lines)


if __name__=='__main__':
    folder=Path(sys.argv[1])
    (folder/'CONTEXT.md').write_text(render(json.loads((folder/'context-gameplay.json').read_text())))
