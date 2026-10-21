// Adapted from supplied VolPyro 1.5.6 (MIT), VolPyro Contributors. See THIRD_PARTY_NOTICES.md.
package ru.aiforge.arsenal.volumetric.client.render;

import ru.aiforge.arsenal.AiArsenal;
import ru.aiforge.arsenal.volumetric.client.sim.VolumeInstance;
import ru.aiforge.arsenal.volumetric.client.sim.VolumetricFxManager;
import ru.aiforge.arsenal.volumetric.config.VolPyroClientConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

public final class VolumeGlRenderer {
    private static final int DATA_SIZE = 64;
    private static final int MAX_INSTANCES = 64;
    private static int depthFbo;
    private static int depthTexture;
    private static int volumeFbo;
    private static int volumeTexture;
    private static int dataTexture;
    private static int vao;
    private static int vbo;
    private static int rayProgram;
    private static int compositeProgram;
    private static int fullWidth;
    private static int fullHeight;
    private static int lowWidth;
    private static int lowHeight;
    private static boolean failed;
    private static long retryAtMillis;
    private static boolean qaFailNextShader;
    private static boolean warmed;
    private static boolean loggedRenderer;
    private static boolean softwareRenderer;
    private static long lastDrawNanos;
    private static long frameCounter;
    private static final FloatBuffer DATA_BUFFER;
    private static final int GL12_GL_CLAMP_TO_EDGE = 33071;
    private static final int ARRAY_BUFFER = 34962;
    private static final int STATIC_DRAW = 35044;

    private VolumeGlRenderer() {
    }

    public static void render(RenderLevelStageEvent renderLevelStageEvent) {
        if (renderLevelStageEvent.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES && available()) {
            RenderTarget renderTarget;
            List<VolumeInstance> list;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null && (renderTarget = minecraft.getMainRenderTarget()) != null && renderTarget.width > 0 && renderTarget.height > 0 && renderTarget.getDepthTextureId() > 0) {
                list = VolumetricFxManager.snapshot();
                // No GL queries or state capture during idle frames after the initial draw.
                if (list.isEmpty() && warmed) return;
                RenderSystem.assertOnRenderThread();
                long l = System.nanoTime();
                try (GlStateSnapshot state = new GlStateSnapshot()) {
                    VolumeGlRenderer.detectRenderer();
                    float f = ((Double)VolPyroClientConfig.RESOLUTION_SCALE.get()).floatValue();
                    if (softwareRenderer || VolPyroClientConfig.QUALITY_PRESET.get() == VolPyroClientConfig.QualityPreset.CI_SOFTWARE) {
                        f = Math.min(f, 0.25f);
                    }
                    VolumeGlRenderer.ensureResources(renderTarget.width, renderTarget.height, f);
                    if (rayProgram == 0 || compositeProgram == 0 || volumeFbo == 0 || depthFbo == 0) {
                        return;
                    }
                    // Some drivers defer compilation until glDrawArrays, not glLinkProgram.
                    // Draw one transparent (zero-volume) pass on world entry, before any trigger press.
                    // It is deliberately NOT counted as an actual effect frame below.
                    VolumeGlRenderer.uploadVolumes(list, renderLevelStageEvent.getCamera().getPosition());
                    VolumeGlRenderer.copyDepth(renderTarget);
                    VolumeGlRenderer.renderLowResolution(renderLevelStageEvent, list.size());
                    VolumeGlRenderer.composite(renderTarget);
                    warmed = true;
                    if (!list.isEmpty()) {
                        lastDrawNanos = System.nanoTime() - l;
                        ++frameCounter;
                    }
                }
                catch (Throwable throwable) {
                    // Never degrade to vanilla particles: pause, then retry automatically.
                    failed = true;
                    retryAtMillis = System.currentTimeMillis() + 4000L;
                    AiArsenal.LOGGER.error("VolPyro GL frame failed; volumetric rendering retries in 4 s (no particle fallback).", throwable);
                    VolumetricFxManager.clear();
                }
            }
        }
    }

    public static long lastDrawNanos() {
        return lastDrawNanos;
    }

    public static long frameCounter() {
        return frameCounter;
    }

    public static String rendererName() {
        return softwareRenderer ? "software" : "hardware/unknown";
    }

    private static void detectRenderer() {
        if (!loggedRenderer) {
            String string = GL11.glGetString((int)7937);
            String string2 = string == null ? "" : string.toLowerCase(Locale.ROOT);
            softwareRenderer = string2.contains("llvmpipe") || string2.contains("softpipe") || string2.contains("swiftshader") || string2.contains("software rasterizer");
            AiArsenal.LOGGER.info("VolPyro OpenGL renderer: {} (software={})", (Object)string, (Object)softwareRenderer);
            loggedRenderer = true;
        }
    }

    private static void ensureResources(int n, int n2, float f) throws Exception {
        boolean bl;
        int n3 = Math.max(1, Math.round((float)n * f));
        int n4 = Math.max(1, Math.round((float)n2 * f));
        boolean bl2 = bl = n != fullWidth || n2 != fullHeight || n3 != lowWidth || n4 != lowHeight;
        if (vao == 0) {
            VolumeGlRenderer.createFullscreenGeometry();
        }
        if (dataTexture == 0) {
            VolumeGlRenderer.createDataTexture();
        }
        if (rayProgram == 0) {
            rayProgram = VolumeGlRenderer.createProgram("volumetric_raymarch.vsh", "volumetric_raymarch.fsh");
        }
        if (compositeProgram == 0) {
            compositeProgram = VolumeGlRenderer.createProgram("volumetric_composite.vsh", "volumetric_composite.fsh");
        }
        if (bl || volumeFbo == 0 || depthFbo == 0) {
            VolumeGlRenderer.deleteFramebuffers();
            fullWidth = n;
            fullHeight = n2;
            lowWidth = n3;
            lowHeight = n4;
            depthTexture = GL11.glGenTextures();
            GL11.glBindTexture((int)3553, (int)depthTexture);
            GL11.glTexParameteri((int)3553, (int)10241, (int)9728);
            GL11.glTexParameteri((int)3553, (int)10240, (int)9728);
            GL11.glTexParameteri((int)3553, (int)10242, (int)33071);
            GL11.glTexParameteri((int)3553, (int)10243, (int)33071);
            GL11.glTexImage2D((int)3553, (int)0, (int)33190, (int)n, (int)n2, (int)0, (int)6402, (int)5125, (ByteBuffer)null);
            depthFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer((int)36160, (int)depthFbo);
            GL30.glFramebufferTexture2D((int)36160, (int)36096, (int)3553, (int)depthTexture, (int)0);
            GL11.glDrawBuffer((int)0);
            GL11.glReadBuffer((int)0);
            if (GL30.glCheckFramebufferStatus((int)36160) != 36053) {
                throw new IllegalStateException("Depth snapshot framebuffer is incomplete");
            }
            volumeTexture = GL11.glGenTextures();
            GL11.glBindTexture((int)3553, (int)volumeTexture);
            VolumeGlRenderer.setTextureSampling(3553, 9729);
            GL11.glTexImage2D((int)3553, (int)0, (int)34842, (int)lowWidth, (int)lowHeight, (int)0, (int)6408, (int)5126, (ByteBuffer)null);
            volumeFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer((int)36160, (int)volumeFbo);
            GL30.glFramebufferTexture2D((int)36160, (int)36064, (int)3553, (int)volumeTexture, (int)0);
            GL11.glDrawBuffer((int)36064);
            if (GL30.glCheckFramebufferStatus((int)36160) != 36053) {
                throw new IllegalStateException("Half-resolution volume framebuffer is incomplete");
            }
            GL11.glBindTexture((int)3553, (int)0);
        }
    }

    private static void createDataTexture() {
        dataTexture = GL11.glGenTextures();
        GL11.glBindTexture((int)3553, (int)dataTexture);
        GL11.glTexParameteri((int)3553, (int)10241, (int)9728);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9728);
        GL11.glTexParameteri((int)3553, (int)10242, (int)33071);
        GL11.glTexParameteri((int)3553, (int)10243, (int)33071);
        GL11.glTexImage2D((int)3553, (int)0, (int)34836, (int)64, (int)64, (int)0, (int)6408, (int)5126, (ByteBuffer)null);
        GL11.glBindTexture((int)3553, (int)0);
    }

    private static void createFullscreenGeometry() {
        vao = GL30.glGenVertexArrays();
        vbo = VolumeGlRenderer.GL15_glGenBuffers();
        GL30.glBindVertexArray((int)vao);
        VolumeGlRenderer.GL15_glBindBuffer(34962, vbo);
        FloatBuffer floatBuffer = BufferUtils.createFloatBuffer((int)6);
        floatBuffer.put(new float[]{0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 2.0f}).flip();
        VolumeGlRenderer.GL15_glBufferData(34962, floatBuffer, 35044);
        int n = 0;
        GL20.glEnableVertexAttribArray((int)n);
        GL20.glVertexAttribPointer((int)n, (int)2, (int)5126, (boolean)false, (int)0, (long)0L);
        GL30.glBindVertexArray((int)0);
    }

    private static int GL15_glGenBuffers() {
        return GL15.glGenBuffers();
    }

    private static void GL15_glBindBuffer(int n, int n2) {
        GL15.glBindBuffer((int)n, (int)n2);
    }

    private static void GL15_glBufferData(int n, FloatBuffer floatBuffer, int n2) {
        GL15.glBufferData((int)n, (FloatBuffer)floatBuffer, (int)n2);
    }

    private static void uploadVolumes(List<VolumeInstance> list, Vec3 vec3) {
        DATA_BUFFER.clear();
        int n = Math.min(64, list.size());
        for (int i = 0; i < 64; ++i) {
            VolumeInstance volumeInstance;
            VolumeInstance volumeInstance2 = volumeInstance = i < n ? list.get(i) : null;
            if (volumeInstance == null) {
                VolumeGlRenderer.put4(0.0f, 0.0f, 0.0f, 0.0f);
                VolumeGlRenderer.put4(0.0f, 0.0f, 0.0f, 0.0f);
                VolumeGlRenderer.put4(0.0f, 0.0f, 0.0f, 0.0f);
                VolumeGlRenderer.put4(0.0f, 0.0f, 0.0f, 0.0f);
                continue;
            }
            double d = volumeInstance.center.x - vec3.x;
            double d2 = volumeInstance.center.y - vec3.y;
            double d3 = volumeInstance.center.z - vec3.z;
            VolumeGlRenderer.put4((float)d, (float)d2, (float)d3, volumeInstance.boundRadius);
            VolumeGlRenderer.put4(volumeInstance.type, volumeInstance.age, volumeInstance.lifetime, volumeInstance.seed);
            VolumeGlRenderer.put4(volumeInstance.density, volumeInstance.temperature, volumeInstance.expansion, volumeInstance.auxiliary);
            Vec3 vec32 = volumeInstance.axis == null ? Vec3.ZERO : volumeInstance.axis;
            VolumeGlRenderer.put4((float)vec32.x, (float)vec32.y, (float)vec32.z, volumeInstance.halfLength);
        }
        while (DATA_BUFFER.hasRemaining()) {
            DATA_BUFFER.put(0.0f);
        }
        DATA_BUFFER.flip();
        GL13.glActiveTexture((int)33985);
        GL11.glBindTexture((int)3553, (int)dataTexture);
        GL11.glTexSubImage2D((int)3553, (int)0, (int)0, (int)0, (int)64, (int)64, (int)6408, (int)5126, (FloatBuffer)DATA_BUFFER);
        GL11.glBindTexture((int)3553, (int)0);
        GL13.glActiveTexture((int)33984);
    }

    private static void put4(float f, float f2, float f3, float f4) {
        DATA_BUFFER.put(f).put(f2).put(f3).put(f4);
    }

    private static void copyDepth(RenderTarget renderTarget) {
        GL30.glBindFramebuffer((int)36008, (int)renderTarget.frameBufferId);
        GL30.glBindFramebuffer((int)36009, (int)depthFbo);
        GL30.glBlitFramebuffer((int)0, (int)0, (int)fullWidth, (int)fullHeight, (int)0, (int)0, (int)fullWidth, (int)fullHeight, (int)256, (int)9728);
        GL30.glBindFramebuffer((int)36160, (int)renderTarget.frameBufferId);
    }

    private static void renderLowResolution(RenderLevelStageEvent renderLevelStageEvent, int n) {
        GL30.glBindFramebuffer((int)36160, (int)volumeFbo);
        GL11.glViewport((int)0, (int)0, (int)lowWidth, (int)lowHeight);
        GL11.glDisable((int)2929);
        GL11.glDisable((int)2884);
        GL11.glDepthMask((boolean)false);
        GL11.glDisable((int)3042);
        GL11.glClearColor((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f);
        GL11.glClear((int)16384);
        GL20.glUseProgram((int)rayProgram);
        VolumeGlRenderer.bindTexture(0, depthTexture);
        VolumeGlRenderer.bindTexture(1, dataTexture);
        VolumeGlRenderer.uniform1i(rayProgram, "uDepthTex", 0);
        VolumeGlRenderer.uniform1i(rayProgram, "uVolumeDataTex", 1);
        VolumeGlRenderer.uniform1i(rayProgram, "uVolumeCount", Math.min(64, n));
        int n2 = (Integer)VolPyroClientConfig.RAYMARCH_STEPS.get();
        if (softwareRenderer || VolPyroClientConfig.QUALITY_PRESET.get() == VolPyroClientConfig.QualityPreset.CI_SOFTWARE) {
            n2 = Math.min(16, n2);
        }
        if (VolPyroClientConfig.QUALITY_PRESET.get() == VolPyroClientConfig.QualityPreset.MEDIUM) {
            n2 = Math.min(32, n2);
        }
        VolumeGlRenderer.uniform1i(rayProgram, "uStepCount", n2);
        Camera camera = renderLevelStageEvent.getCamera();
        Vector3f vector3f = new Vector3f((Vector3fc)camera.getLookVector()).normalize();
        Vector3f vector3f2 = new Vector3f((Vector3fc)camera.getLeftVector()).normalize();
        Vector3f vector3f3 = new Vector3f((Vector3fc)camera.getUpVector()).normalize();
        VolumeGlRenderer.uniform3f(rayProgram, "uCameraForward", vector3f.x, vector3f.y, vector3f.z);
        VolumeGlRenderer.uniform3f(rayProgram, "uCameraRight", -vector3f2.x, -vector3f2.y, -vector3f2.z);
        VolumeGlRenderer.uniform3f(rayProgram, "uCameraUp", vector3f3.x, vector3f3.y, vector3f3.z);
        VolumeGlRenderer.uniform3f(rayProgram, "uSunDir", 0.31f, 0.83f, 0.46f);
        VolumeGlRenderer.uniform3f(rayProgram, "uSunColor", 1.1f, 0.98f, 0.8f);
        VolumeGlRenderer.uniform1f(rayProgram, "uAspect", (float)fullWidth / (float)fullHeight);
        Matrix4f matrix4f = renderLevelStageEvent.getProjectionMatrix();
        float f = Math.max(Math.abs(matrix4f.m11()), 0.001f);
        VolumeGlRenderer.uniform1f(rayProgram, "uTanHalfFov", 1.0f / f);
        float f2 = matrix4f.m22();
        float f3 = matrix4f.m32();
        float f4 = Math.abs(f3 / (f2 - 1.0f));
        float f5 = Math.abs(f3 / (f2 + 1.0f));
        if (!Float.isFinite(f4) || f4 < 0.001f) {
            f4 = 0.05f;
        }
        if (!Float.isFinite(f5) || f5 < f4 + 1.0f) {
            f5 = 4096.0f;
        }
        VolumeGlRenderer.uniform1f(rayProgram, "uNear", f4);
        VolumeGlRenderer.uniform1f(rayProgram, "uFar", Math.min(f5, 8192.0f));
        VolumeGlRenderer.uniform1f(rayProgram, "uTime", (float)Minecraft.getInstance().level.getGameTime() + renderLevelStageEvent.getPartialTick());
        VolumeGlRenderer.drawTriangle();
        GL20.glUseProgram((int)0);
        VolumeGlRenderer.unbindTextures();
    }

    private static void composite(RenderTarget renderTarget) {
        renderTarget.bindWrite(true);
        GL11.glViewport((int)0, (int)0, (int)fullWidth, (int)fullHeight);
        GL11.glDisable((int)2929);
        GL11.glDisable((int)2884);
        GL11.glDepthMask((boolean)false);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate((int)1, (int)771, (int)1, (int)771);
        GL20.glUseProgram((int)compositeProgram);
        VolumeGlRenderer.bindTexture(0, volumeTexture);
        VolumeGlRenderer.bindTexture(1, depthTexture);
        VolumeGlRenderer.uniform1i(compositeProgram, "uVolumeTex", 0);
        VolumeGlRenderer.uniform1i(compositeProgram, "uDepthTex", 1);
        VolumeGlRenderer.uniform2f(compositeProgram, "uFullTexel", 1.0f / (float)fullWidth, 1.0f / (float)fullHeight);
        VolumeGlRenderer.uniform1f(compositeProgram, "uDepthSharpness", 700.0f);
        VolumeGlRenderer.drawTriangle();
        GL20.glUseProgram((int)0);
        VolumeGlRenderer.unbindTextures();
        GL11.glDisable(GL11.GL_BLEND);

    }

    private static void bindTexture(int n, int n2) {
        GL13.glActiveTexture((int)(33984 + n));
        GL11.glBindTexture((int)3553, (int)n2);
    }

    private static void unbindTextures() {
        for (int i = 1; i >= 0; --i) {
            GL13.glActiveTexture((int)(33984 + i));
            GL11.glBindTexture((int)3553, (int)0);
        }
        GL13.glActiveTexture((int)33984);
    }

    private static void drawTriangle() {
        GL30.glBindVertexArray((int)vao);
        GL11.glDrawArrays((int)4, (int)0, (int)3);
        GL30.glBindVertexArray((int)0);
    }

    private static void setTextureSampling(int n, int n2) {
        GL11.glTexParameteri((int)n, (int)10241, (int)n2);
        GL11.glTexParameteri((int)n, (int)10240, (int)n2);
        GL11.glTexParameteri((int)n, (int)10242, (int)33071);
        GL11.glTexParameteri((int)n, (int)10243, (int)33071);
    }

    private static int createProgram(String vertexFile, String fragmentFile) throws Exception {
        int vertex = 0, fragment = 0, program = 0;
        boolean linked = false;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, loadShader(vertexFile));
            fragment = compile(GL20.GL_FRAGMENT_SHADER, loadShader(fragmentFile));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glBindAttribLocation(program, 0, "Position");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException("VolPyro shader link: " + GL20.glGetProgramInfoLog(program));
            linked = true;
            AiArsenal.LOGGER.info("VolPyro shader ready: {}", fragmentFile);
            return program;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
            if (!linked && program != 0) GL20.glDeleteProgram(program);
        }
    }

    public static boolean available() {
        if (failed && System.currentTimeMillis() >= retryAtMillis) {
            failed = false;
            warmed = false;
            AiArsenal.LOGGER.info("VolPyro volumetric renderer retrying after a failed frame.");
        }
        return !failed && VolPyroClientConfig.ENABLE_VOLUMETRIC_RENDERER.get();
    }

    /** Render-thread resource reload / world teardown; also permits retry after a shader fix. */
    public static void reset() {
        RenderSystem.assertOnRenderThread();
        deleteFramebuffers();
        if (dataTexture != 0) GL11.glDeleteTextures(dataTexture);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (vbo != 0) GL15.glDeleteBuffers(vbo);
        if (rayProgram != 0) GL20.glDeleteProgram(rayProgram);
        if (compositeProgram != 0) GL20.glDeleteProgram(compositeProgram);
        dataTexture = vao = vbo = rayProgram = compositeProgram = 0;
        fullWidth = fullHeight = lowWidth = lowHeight = 0;
        failed = false;
        qaFailNextShader = false;
        warmed = false;
        frameCounter = lastDrawNanos = 0;
    }

    private static int compile(int n, String string) {
        int n2 = GL20.glCreateShader((int)n);
        GL20.glShaderSource((int)n2, (CharSequence)string);
        GL20.glCompileShader((int)n2);
        int n3 = GL20.glGetShaderi((int)n2, (int)35713);
        String string2 = GL20.glGetShaderInfoLog((int)n2);
        if (n3 == 0) {
            GL20.glDeleteShader((int)n2);
            throw new IllegalStateException("GLSL compile failed: " + string2);
        }
        return n2;
    }

    /** Real GL regression: hostile inherited upload state must be normalized BEFORE
     * touching a compact native buffer, then restored exactly for the caller. */
    public static void verifyPixelUnpackForQa() {
        if (!Boolean.getBoolean("aiarsenal.visualQa") || !available()) throw new IllegalStateException("QA requires an active vanilla volumetric renderer");
        RenderSystem.assertOnRenderThread();
        PixelUnpackState original = new PixelUnpackState();
        int pbo = GL15.glGenBuffers();
        try {
            for (int mode=0;mode<3;mode++) {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
                GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, 32L, GL15.GL_STATIC_DRAW);
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, mode==0 ? 0 : pbo);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,8);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,mode==1 ? 0 : 256);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,mode==1 ? 0 : 7);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,mode==1 ? 0 : 11);
                GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES,1);
                GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST,1);
                GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT,128);
                GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES,2);
                PixelUnpackState hostile = new PixelUnpackState();
                try (GlStateSnapshot state = new GlStateSnapshot()) {
                    // Assert BEFORE the real upload, so a regression fails in Java
                    // instead of deliberately attempting an out-of-bounds native read.
                    PixelUnpackState.assertTightlyPacked();
                    if (dataTexture==0) createDataTexture();
                    uploadVolumes(java.util.List.of(),Vec3.ZERO);
                    int error=GL11.glGetError();
                    if(error!=GL11.GL_NO_ERROR) throw new IllegalStateException("pixel upload GL error: "+error);
                }
                hostile.assertRestored();
            }
        } finally {
            original.restore();
            GL15.glDeleteBuffers(pbo);
        }
        original.assertRestored();
        AiArsenal.LOGGER.info("FX UNPACK PASS cases=3 real64x64Upload=true callerStateRestored=true");
    }

    /** Test the REAL compiler-error/fallback path, only in an explicitly opted-in client. */
    public static void failNextShaderForQa() {
        if (!Boolean.getBoolean("aiarsenal.visualQa")) throw new IllegalStateException("QA is disabled");
        reset();
        qaFailNextShader = true;
        AiArsenal.LOGGER.info("VIEW QA forcing one shader compiler failure");
    }

    private static String loadShader(String string) throws Exception {
        if (qaFailNextShader) {
            qaFailNextShader = false;
            return "#version 150\nthis is intentionally invalid GLSL for opt-in QA;";
        }
        String string2;
        ResourceLocation resourceLocation = new ResourceLocation("aiarsenal", "shaders/volpyro/" + string);
        try (InputStream inputStream = ((Resource)Minecraft.getInstance().getResourceManager().getResource(resourceLocation).orElseThrow()).open();){
            string2 = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (string.equals("volumetric_raymarch.fsh")) {
            // Extend only a new volume type. The supplied shader files and all nuclear branches stay intact.
            String function = "vec2 evaluateVolume(vec3 p, vec4 h0, vec4 h1, vec4 h2, vec4 h3) {";
            String noise = "    float n=billowFbm(local*1.45";
            String colour = "if (vtype==1) albedo=vec3(0.24,0.25,0.26);";
            if (!string2.contains(function) || !string2.contains(noise) || !string2.contains(colour))
                throw new IllegalStateException("Arsenal smoke extension: incompatible shader resource");
            string2 = string2.replace(function, loadShader("arsenal_smoke.glsl") + "\n" + function)
                .replace(noise, "    if (type == 11) return arsenalSmokeField(local,h1,h2,h3);\n" + noise)
                .replace(colour, "if (vtype==11) albedo=arsenalSmokeAlbedo(h2.w); else " + colour);
        }
        return string2;
    }

    private static int uniform(int n, String string) {
        return GL20.glGetUniformLocation((int)n, (CharSequence)string);
    }

    private static void uniform1i(int n, String string, int n2) {
        int n3 = VolumeGlRenderer.uniform(n, string);
        if (n3 >= 0) {
            GL20.glUniform1i((int)n3, (int)n2);
        }
    }

    private static void uniform1f(int n, String string, float f) {
        int n2 = VolumeGlRenderer.uniform(n, string);
        if (n2 >= 0) {
            GL20.glUniform1f((int)n2, (float)f);
        }
    }

    private static void uniform2f(int n, String string, float f, float f2) {
        int n2 = VolumeGlRenderer.uniform(n, string);
        if (n2 >= 0) {
            GL20.glUniform2f((int)n2, (float)f, (float)f2);
        }
    }

    private static void uniform3f(int n, String string, float f, float f2, float f3) {
        int n2 = VolumeGlRenderer.uniform(n, string);
        if (n2 >= 0) {
            GL20.glUniform3f((int)n2, (float)f, (float)f2, (float)f3);
        }
    }

    private static void deleteFramebuffers() {
        if (depthFbo != 0) {
            GL30.glDeleteFramebuffers((int)depthFbo);
        }
        if (volumeFbo != 0) {
            GL30.glDeleteFramebuffers((int)volumeFbo);
        }
        if (depthTexture != 0) {
            GL11.glDeleteTextures((int)depthTexture);
        }
        if (volumeTexture != 0) {
            GL11.glDeleteTextures((int)volumeTexture);
        }
        volumeTexture = 0;
        depthTexture = 0;
        volumeFbo = 0;
        depthFbo = 0;
    }

    static {
        DATA_BUFFER = BufferUtils.createFloatBuffer((int)16384);
    }
}

