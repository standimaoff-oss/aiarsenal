from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]/'mods/aiarsenal/src/main/java'

class ComfortPolicyTest(unittest.TestCase):
    def test_production_never_grants_blindness_or_nausea(self):
        for p in ROOT.rglob('*.java'):
            if 'gametest' in p.parts:continue
            for effect in ['MobEffects.BLINDNESS','MobEffects.CONFUSION']:
                self.assertNotIn(effect,p.read_text(),str(p))

    def test_visual_flash_and_smoke_paths_remain(self):
        for name in ['ru/aiforge/arsenal/client/NukeFx.java','ru/aiforge/realwar/client/fx/NukeFx.java']:
            text=(ROOT/name).read_text()
            self.assertIn('VolumetricEffects',text)
        smoke=(ROOT/'ru/aiforge/bm21grad/entity/SmokeScreenEntity.java').read_text()
        self.assertIn('P_SMOKE_WALL',smoke)
        self.assertIn('m.setTarget(null)',smoke)
