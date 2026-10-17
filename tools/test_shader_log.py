import unittest
from shader_log import scan

class ShaderLogTest(unittest.TestCase):
    def test_known_integration_diagnostic_is_retained_not_fatal(self):
        line='[Render thread/ERROR] [Embeddium-MixinTaintDetector/]: Mod mixin into Embeddium internals detected. This instance is now tainted.'
        self.assertEqual(scan(line),([],[line]))
    def test_other_renderer_failures_are_fatal(self):
        for line in ['[Render thread/ERROR] [Oculus/]: shader compilation failed',
                     '[Render thread/ERROR] [aiarsenal/]: Ошибка отрисовки частиц',
                     'OpenGL Error 1282','EXCEPTION_ACCESS_VIOLATION',
                     '[Render thread/ERROR] [Embeddium-MixinTaintDetector/]: unrelated failure']:
            self.assertEqual(scan(line),([line],[]))
