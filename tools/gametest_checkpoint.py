#!/usr/bin/env python3
"""Preserve early diagnostics without waiting for the destructive client audit.
This log checkpoint is not a CI/pass verdict; consult the workflow steps too.
"""
import gzip
import hashlib
import json
from pathlib import Path
import sys


def preserve(log, destination, source):
    raw = Path(log).read_bytes()
    lines = raw.decode(errors='replace').splitlines()
    folder = Path(destination)
    folder.mkdir(parents=True, exist_ok=True)
    summary = dict(source=source, bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(),
                   failures=[line for line in lines if ' failed!' in line],
                   totals=[line for line in lines if 'required tests' in line or 'TESTS COMPLETE' in line],
                   scope='Early log checkpoint only; not a workflow verdict')
    (folder/'gametest-early.log.gz').write_bytes(gzip.compress(raw, mtime=0))
    (folder/'gametest-early.json').write_text(json.dumps(summary, indent=2)+'\n')
    return summary


if __name__ == '__main__':
    preserve(*sys.argv[1:])
