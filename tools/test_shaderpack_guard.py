"""Compile the actual optional-API guard against minimal API fixtures (not Minecraft).
The installed-client test is separate: these tests only certify fail-closed detection
and live changes in API answers. Requires JDK 17, supplied by Actions.
"""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'mods/aiarsenal/src/main/java/ru/aiforge/arsenal/volumetric/client/render/ShaderPackCompatibility.java'


@unittest.skipUnless(shutil.which('javac'), 'JDK required; run in the verification workflow')
class GuardTests(unittest.TestCase):
    def check_case(self, api, body):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            sources = {
                'net/minecraftforge/fml/ModList.java': '''package net.minecraftforge.fml;
                    public class ModList {
                        public static boolean loaded;
                        public static ModList get() { return new ModList(); }
                        public boolean isLoaded(String id) { return loaded; }
                    }''',
                'ru/aiforge/arsenal/AiArsenal.java': '''package ru.aiforge.arsenal;
                    public class AiArsenal {
                        public static final Log LOGGER=new Log();
                        public static class Log {
                            public void warn(String s,Object... args) {}
                            public void info(String s,Object... args) {}
                        }
                    }''',
                'ru/aiforge/arsenal/volumetric/client/render/ShaderPackCompatibility.java': SOURCE.read_text(),
                'GuardMain.java': '''import ru.aiforge.arsenal.volumetric.client.render.ShaderPackCompatibility;
                    import net.minecraftforge.fml.ModList;
                    public class GuardMain {
                        static void expect(boolean b) {
                            if(ShaderPackCompatibility.blocked()!=b) throw new AssertionError("expected "+b);
                        }
                        public static void main(String[] args) {''' + body + '}}',
            }
            if api:
                sources['net/irisshaders/iris/api/v0/IrisApi.java'] = api
            files = []
            for name, text in sources.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text); files.append(str(path))
            subprocess.run(['javac', '-d', str(root), *files], check=True, capture_output=True, timeout=30)
            subprocess.run(['java', '-cp', str(root), 'GuardMain'], check=True, capture_output=True, timeout=10)

    def test_vanilla_does_not_require_iris(self):
        self.check_case(None, 'expect(false); expect(false);')

    def test_missing_api_with_loader_fails_closed(self):
        self.check_case(None, 'ModList.loaded=true; expect(true);')

    def test_live_enable_disable(self):
        self.check_case('''package net.irisshaders.iris.api.v0;
            public class IrisApi {
                public static boolean active;
                public static IrisApi getInstance(){return new IrisApi();}
                public boolean isShaderPackInUse(){return active;}
            }''', '''ModList.loaded=true; expect(false);
                net.irisshaders.iris.api.v0.IrisApi.active=true; expect(true);
                net.irisshaders.iris.api.v0.IrisApi.active=false; expect(false);''')

    def test_broken_api_fails_closed(self):
        self.check_case('''package net.irisshaders.iris.api.v0;
            public class IrisApi {
                public static IrisApi getInstance(){return new IrisApi();}
                public boolean isShaderPackInUse(){throw new IllegalStateException("fixture");}
            }''', 'ModList.loaded=true; expect(true); expect(true);')

    def test_null_api_fails_closed(self):
        self.check_case('''package net.irisshaders.iris.api.v0;
            public class IrisApi {
                public static IrisApi getInstance(){return null;}
                public boolean isShaderPackInUse(){return false;}
            }''', 'ModList.loaded=true; expect(true);')

    def test_missing_method_fails_closed(self):
        self.check_case('''package net.irisshaders.iris.api.v0;
            public class IrisApi {public static IrisApi getInstance(){return new IrisApi();}}''',
            'ModList.loaded=true; expect(true);')


class WiringTests(unittest.TestCase):
    def test_guard_precedes_raw_gl(self):
        text = SOURCE.with_name('VolumeGlRenderer.java').read_text()
        start = text.index('public static void render(')
        end = text.index('try (GlStateSnapshot', start)
        self.assertIn('&& available()', text[start:end])
        self.assertIn('&& !ShaderPackCompatibility.blocked()', text)

    def test_native_upload_state_is_scoped(self):
        folder=SOURCE.parent
        pixel=(folder/'PixelUnpackState.java').read_text()
        for parameter in ['GL_PIXEL_UNPACK_BUFFER','GL_UNPACK_ROW_LENGTH','GL_UNPACK_SKIP_ROWS',
                          'GL_UNPACK_SKIP_PIXELS','GL_UNPACK_ALIGNMENT','GL_UNPACK_SWAP_BYTES']:
            self.assertIn(parameter,pixel)
        snapshot=(folder/'GlStateSnapshot.java').read_text()
        self.assertIn('PixelUnpackState.tightlyPackedClientMemory();',snapshot)
        self.assertIn('unpack.restore();',snapshot)
        renderer=(folder/'VolumeGlRenderer.java').read_text()
        probe=renderer[renderer.index('public static void verifyPixelUnpackForQa'):]
        self.assertLess(probe.index('PixelUnpackState.assertTightlyPacked();'),probe.index('uploadVolumes('))
        self.assertIn('hostile.assertRestored();',probe)

    def test_shader_fallbacks_use_managed_buffers(self):
        root=ROOT/'mods/aiarsenal/src/main/java'
        system=(root/'ru/aiforge/realwar/client/particle/FxSystem.java').read_text()
        particle=(root/'ru/aiforge/realwar/client/particle/FxParticle.java').read_text()
        nuke=(root/'ru/aiforge/arsenal/client/NukeFx.java').read_text()
        self.assertIn('Minecraft.getInstance().particleEngine.add(p)',system)
        self.assertIn('if (ShaderPackCompatibility.blocked() || ALL.isEmpty()',system)
        self.assertIn('shaderSafe ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT',particle)
        self.assertIn('RenderType.entityTranslucent(',nuke)
        self.assertIn('BufferBuilder raw = safe ? null',nuke)
        self.assertIn('if (raw != null) BufferUploader.drawWithShader(raw.end())',nuke)
        self.assertIn('g.fill(0, 0, w, h,',nuke) # HUD flash stays a vanilla GUI pass

    def test_destructive_commands_are_opt_in(self):
        text = (ROOT / 'mods/aiarsenal/src/main/java/ru/aiforge/arsenal/qa/CatalogueQa.java').read_text()
        self.assertIn('if (!Boolean.getBoolean("aiarsenal.stressQa")) return;', text)
        self.assertIn('.requires(s -> s.hasPermission(2))', text)


if __name__ == '__main__':
    unittest.main()
