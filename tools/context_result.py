"""Acceptance predicates for contextual QA. No disappearing/unloaded entity passes."""

def missile_complete(s):
    return (s['expectedEntities'] > 0 and s['spawned'] == s['expectedEntities']
            and s['removed'] == s['spawned'] and s['activeBlasts'] == 0
            and s['completedTasks'] >= max(1, s['expectedEntities'] - 1, s.get('minimumTasks', 1))
            and s['changedBlocks'] > 0)


def craft_fired(before, after):
    i = before['selected']
    if i < 0 or after['selected'] != i or not after['alive'] or not after['riding']:
        return False
    # A consumed round alone isn't enough. Require a projectile or live ballistic
    # bullet, as well as the authoritative ammunition decrement.
    projectile = (after['bulletsAlive'] > before['bulletsAlive'] or
        any(n > before['spawned'].get(k, 0) for k, n in after['spawned'].items()))
    return after['loads'][i]['ammo'] < before['loads'][i]['ammo'] and projectile


def assert_complete(report):
    expected = report['expectedCases']
    rows = report['cases']
    assert len(rows) == expected, f'incomplete audit: {len(rows)}/{expected}'
    assert len({r['id'] for r in rows}) == expected, 'duplicate case IDs'
    assert all(r['status'] == 'PASS' for r in rows), 'one or more contextual cases failed'
    assert report['worldSaveReopen']['status'] == 'PASS', 'world not reopened'
