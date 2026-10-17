import json
from pathlib import Path
import unittest
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mods/aiarsenal/src/main/resources/assets/aiarsenal'


class PistolAudio(unittest.TestCase):
    def test_legacy_ppsh_headroom(self):
        data, rate = sf.read(ASSETS / 'sounds/smg_ppsh.ogg')
        self.assertEqual(rate, 44100)
        self.assertLess(float(np.max(np.abs(data))), .22)
        self.assertGreater(float(np.sqrt(np.mean(data**2))), .02)

    def test_ppsh_packet_burst_mix_headroom(self):
        data, _ = sf.read(ASSETS / 'sounds/smg_ppsh.ogg')
        sample = json.loads((ASSETS / 'sounds.json').read_text())['smg_ppsh']['sounds'][0]
        # Eight coincident voices at full master volume, including the server's 3x gain.
        self.assertLess(float(np.max(np.abs(data))) * 3 * sample['volume'] * 8, .85)
        self.assertGreaterEqual(sample['attenuation_distance'], 48)

    def test_decodable_distinct_mono_sounds(self):
        signals = []
        definitions = json.loads((ASSETS / 'sounds.json').read_text())
        kinds = (ROOT / 'mods/aiarsenal/src/main/java/ru/aiforge/realwar/handheld/GunKind.java').read_text()
        for kind, name in [('C96', 'pistol_c96'), ('TT33', 'pistol_tt33')]:
            data, rate = sf.read(ASSETS / 'sounds' / (name + '.ogg'))
            self.assertEqual(data.ndim, 1)  # positional Minecraft sound must be mono
            self.assertEqual(rate, 44100)
            self.assertTrue(.3 < len(data) / rate < .6)
            self.assertTrue(np.isfinite(data).all())
            self.assertTrue(.1 < np.max(np.abs(data)) < .99)
            self.assertGreater(float(np.sqrt(np.mean(data**2))), .02)
            self.assertEqual(definitions[name]['sounds'][0]['name'], 'aiarsenal:' + name)
            declaration = next(line for line in kinds.splitlines() if kind + '(' in line)
            self.assertIn('"' + name + '"', declaration)
            self.assertNotIn('smg_ppsh', declaration)
            for lang in ('en_us', 'ru_ru'):
                self.assertIn(definitions[name]['subtitle'], json.loads((ASSETS / 'lang' / (lang+'.json')).read_text()))
            signals.append(data)
        self.assertNotEqual(len(signals[0]), len(signals[1]))
        self.assertFalse(np.allclose(signals[0][:1000], signals[1][:1000]))


if __name__ == '__main__':
    unittest.main()
