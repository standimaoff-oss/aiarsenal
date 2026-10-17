#!/usr/bin/env python3
"""Regression checks on the *exported* GLBs, not just the Blender generator.
Run after: blender -b --python tools/blender/weapons_r22.py -- --only-ppsh
Coordinates: glTF X = width, Y = height, Z = bore axis (metres).
"""
import json
import math
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mods/aiarsenal/src/main/resources/assets/aiarsenal/models/glb'


def load_glb(path):
    data = path.read_bytes()
    magic, version, length = struct.unpack_from('<4sII', data)
    assert (magic, version, length) == (b'glTF', 2, len(data))
    size, kind = struct.unpack_from('<I4s', data, 12)
    assert kind == b'JSON'
    doc = json.loads(data[20:20 + size])
    offset = 20 + size
    size, kind = struct.unpack_from('<I4s', data, offset)
    assert kind == b'BIN\x00'
    binary = data[offset + 8:offset + 8 + size]
    assert len(binary) == size
    return doc, binary


def vectors(doc, binary, index):
    a = doc['accessors'][index]
    assert a['componentType'] == 5126 and a['type'] == 'VEC3'
    view = doc['bufferViews'][a['bufferView']]
    start = view.get('byteOffset', 0) + a.get('byteOffset', 0)
    stride = view.get('byteStride', 12)
    return [struct.unpack_from('<3f', binary, start + i * stride) for i in range(a['count'])]


class PpshExportTests(unittest.TestCase):
    def check_model(self, filename, node_name, expected_animations):
        doc, binary = load_glb(ASSETS / filename)
        node = next(n for n in doc['nodes'] if n['name'] == node_name)
        # No node-space corrective rotation: animation must work in the original axes.
        self.assertEqual(node.get('rotation', [0, 0, 0, 1]), [0, 0, 0, 1])
        points = []
        mesh = doc['meshes'][node['mesh']]
        for primitive in mesh['primitives']:
            self.assertIn('TEXCOORD_0', primitive['attributes'])
            points.extend(vectors(doc, binary, primitive['attributes']['POSITION']))
        self.assertTrue(all(math.isfinite(v) for point in points for v in point))
        extents = [max(p[i] for p in points) - min(p[i] for p in points) for i in range(3)]
        self.assertAlmostEqual(extents[0], 0.1532, delta=0.002, msg='drum must be wide across weapon')
        self.assertLess(extents[2], 0.070, 'drum must be shallow along bore, not sideways')
        self.assertGreater(extents[1], 0.150)
        # Textures must be real embedded PNGs, not paths to a developer machine.
        self.assertGreaterEqual(len(doc['images']), 4)
        for image in doc['images']:
            self.assertNotIn('uri', image)
            view = doc['bufferViews'][image['bufferView']]
            self.assertEqual(binary[view['byteOffset']:view['byteOffset'] + 8], b'\x89PNG\r\n\x1a\n')
        names = {a['name'] for a in doc.get('animations', [])}
        self.assertEqual(names, expected_animations)
        for animation in doc.get('animations', []):
            targets = {(doc['nodes'][c['target']['node']]['name'], c['target']['path']) for c in animation['channels']}
            self.assertIn(('ppsh41.mag', 'translation'), targets)
            self.assertIn(('ppsh41.arm_l', 'translation'), targets)
        return doc, binary, node

    def test_weapon_drum_and_animation(self):
        doc, binary, node = self.check_model('ppsh41.glb', 'ppsh41.mag', {'ppsh41_reload', 'ppsh41_reload_empty'})
        self.assertEqual([round(v, 3) for v in node['translation']], [0, -0.06, 0.066])
        names = {n['name'] for n in doc['nodes']}
        self.assertTrue({'ppsh41.sight', 'ppsh41.muzzle', 'ppsh41.bolt'} <= names)

    def test_standalone_magazine(self):
        self.check_model('mag_ppsh.glb', 'mag_ppsh', set())


if __name__ == '__main__':
    unittest.main(verbosity=2)
