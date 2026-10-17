#!/usr/bin/env python3
"""CI-only third-party compatibility fixture. Download publisher files from Modrinth,
verify the published SHA-512, and record exact version/file metadata. No redistribution
of third-party JARs or shader packs in Git or our release. Not a hardware GPU test.
"""
import hashlib
import json
import os
import io
import re
import zipfile
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def get(url):
    with urlopen(Request(url, headers={'User-Agent': 'aimods-stability-qa/1.0 (github.com/standimaoff-oss/aimods)'}), timeout=120) as response:
        return response.read()


def install(game):
    client = game.RUNS / 'client'
    provenance = []
    # Exact publisher releases from the recorded three-pack fixture; downloaded
    # files are verified against the publisher SHA-512 and recorded for reproduction.
    pack = os.environ.get('QA_SHADERPACK', 'photon')
    projects = {'photon': ('photon-shader', '1.3b'),
                'complementary': ('complementary-reimagined', '5.9.3'),
                'bsl': ('bsl-shaders', '10.1.8')}
    project, fragment = projects[pack]
    for project, fragment, folder in [('oculus', '1.8.0', 'mods'),
                                       ('embeddium', '0.3.31', 'mods'),
                                       (project, fragment, 'shaderpacks')]:
        query = urlencode({'game_versions': json.dumps(['1.20.1'])})
        versions = json.loads(get('https://api.modrinth.com/v2/project/' + project + '/version?' + query))
        candidates = [(v, f) for v in versions for f in v['files']
                      if fragment in f['filename'].lower() and (folder != 'mods' or 'forge' in v['loaders'])]
        assert candidates, 'requested historical fixture unavailable: ' + project + ' ' + fragment
        version, file = next(((v, f) for v, f in candidates if f.get('primary')), candidates[0])
        data = get(file['url'])
        assert hashlib.sha512(data).hexdigest() == file['hashes']['sha512'], 'third-party checksum mismatch'
        dest = client / folder / file['filename']
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
        provenance.append({'project': project, 'version_id': version['id'], 'version': version['version_number'],
                           'file': file['filename'], 'url': file['url'], 'sha512': file['hashes']['sha512']})
        if folder == 'shaderpacks':
            config = client / 'config/oculus.properties'
            config.parent.mkdir(parents=True, exist_ok=True)
            config.write_text('enableShaders=true\nshaderPack=' + file['filename'] + '\n')
            if pack != 'photon':
                provenance[-1]['options']={}
                continue
            # llvmpipe exposes 32 KiB compute shared memory; Photon defaults need
            # 36,860 bytes for spherical-harmonic skylight. Use its public option,
            # not an edited shader. The default preset is explicitly NOT tested.
            with zipfile.ZipFile(io.BytesIO(data)) as pack:
                props=next(name for name in pack.namelist() if name.endswith('shaders/shaders.properties'))
                assert re.search(r'program\.world0/deferred4_a\.enabled\s*=\s*SH_SKYLIGHT\b',pack.read(props).decode())
            options='SH_SKYLIGHT=false\n'
            (dest.parent/(dest.name+'.txt')).write_text(options)
            (game.LOGS/'photon-settings.txt').write_text(options)
            provenance[-1]['options']={'SH_SKYLIGHT':False}
            provenance[-1]['optionReason']='llvmpipe 32768-byte compute shared memory limit; default Photon preset NOT VERIFIED'

    (game.LOGS / 'shaderpack-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
