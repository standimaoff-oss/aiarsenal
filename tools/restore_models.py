#!/usr/bin/env python3
"""Restore released binary models stored once in a compressed source archive."""
from pathlib import Path
import zipfile
ROOT = Path(__file__).resolve().parents[1]
with zipfile.ZipFile(ROOT / 'vendor/baseline-models.zip') as archive:
    for info in archive.infolist():
        path = (ROOT / info.filename).resolve()
        if not path.is_relative_to(ROOT / 'mods/aiarsenal/src/main/resources'):
            raise ValueError(f'Unsafe model path: {info.filename}')
        if not path.exists():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read(info))
print('Baseline models restored (existing edited assets preserved).')
