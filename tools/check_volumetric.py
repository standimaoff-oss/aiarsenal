#!/usr/bin/env python3
"""Static port checks; NOT a replacement for real client rendering tests."""
from pathlib import Path
import hashlib
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'mods/aiarsenal/src/main/java/ru/aiforge'
PORT = JAVA / 'arsenal/volumetric'
SHADERS = ROOT / 'mods/aiarsenal/src/main/resources/assets/aiarsenal/shaders/volpyro'

class PortTests(unittest.TestCase):
    def test_upstream_shader_bytes_match_recorded_hashes(self):
        # The original third-party JAR is intentionally not redistributed.
        # These digests pin the four upstream shader files documented in THIRD_PARTY_NOTICES.md.
        expected = {
            'volumetric_composite.fsh': 'eafb1d54026164bbbe3995e35307aae3372b2ee6a7b68300602510e1ae4a54fa',
            'volumetric_composite.vsh': 'c5f9acc8397881538769ee9fe8b4af7d99e3aee3ef761644887e717985c3879a',
            'volumetric_raymarch.fsh': '643b7b99956505e6180fe2e3974781ea0d6a788208e76340a292b2440317b43a',
            'volumetric_raymarch.vsh': 'c5f9acc8397881538769ee9fe8b4af7d99e3aee3ef761644887e717985c3879a',
        }
        for name, digest in expected.items():
            with self.subTest(shader=name):
                self.assertEqual(hashlib.sha256((SHADERS / name).read_bytes()).hexdigest(), digest)

    def test_no_donor_gameplay_or_unmapped_symbols(self):
        for path in PORT.rglob('*.java'):
            text = path.read_text()
            self.assertNotRegex(text, r'\b[mf]_\d+_\b|com\.example\.volpyro|@Mod\("volpyro"\)')
            self.assertNotIn('VolPyroCommonConfig', text)
        self.assertNotIn('client.', (PORT / 'VolumetricParticles.java').read_text())
        self.assertNotIn('net.minecraft.client', (PORT / 'config/VolPyroClientConfig.java').read_text())

    def test_mod_smoke_emitters_are_routed(self):
        for path in JAVA.rglob('*.java'):
            if 'volumetric' in path.parts:
                continue
            self.assertNotRegex(path.read_text(), r'ParticleTypes\.(SMOKE|LARGE_SMOKE|CAMPFIRE_COSY_SMOKE|CAMPFIRE_SIGNAL_SMOKE)\b')
            self.assertNotIn('net.minecraft.core.particles.ru.', path.read_text())

    def test_qa_bridge_is_opt_in_and_clientbound(self):
        packet = (PORT / 'VisualQaPacket.java').read_text()
        self.assertIn('Boolean.getBoolean("aiarsenal.visualQa") && EFFECTS.contains(effect)', packet)
        self.assertIn('readUtf(16)', packet)
        network = (JAVA / 'arsenal/network/ArsenalNetwork.java').read_text()
        self.assertIn('VisualQaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT', network)
        commands = (PORT / 'VisualTestCommand.java').read_text()
        self.assertIn('requires(s -> s.hasPermission(2))', commands)

    def test_renderer_has_failure_and_resource_cleanup(self):
        text = (PORT / 'client/render/VolumeGlRenderer.java').read_text()
        self.assertIn('try (GlStateSnapshot state', text)
        self.assertIn('public static void reset()', text)
        self.assertIn('GL20.glDeleteProgram', text)
        self.assertIn('failed = true', text)
        self.assertIn('Math.min(16, n2)', text)
        self.assertIn('if (list.isEmpty() && warmed) return;', text)
        self.assertIn('warmed = false;', text)
        manager = (PORT / 'client/sim/VolumetricFxManager.java').read_text()
        self.assertIn('MAX_RENDER_VOLUMES', manager)
        self.assertIn('rocketTracks.size() >= 64', manager)

if __name__ == '__main__':
    unittest.main(verbosity=2)
