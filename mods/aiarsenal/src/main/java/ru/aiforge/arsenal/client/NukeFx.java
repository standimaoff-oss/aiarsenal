package ru.aiforge.arsenal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ru.aiforge.arsenal.volumetric.client.render.ShaderPackCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import ru.aiforge.arsenal.AiArsenal;
import ru.aiforge.arsenal.entity.ArsEntity;
import ru.aiforge.arsenal.network.NukeFxPacket;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Client visuals of a nuclear detonation, all procedural (no textures): flash, fireball, condensation (Wilson)
 * cloud, ground shock ring, rising mushroom cloud (stem, toroidal cap with puffs, dome) and base surge, sound
 * delayed by distance (speed of sound in world units), camera shake when the wave passes, and the Geiger counter
 * inside the fallout zone.
 */
@Mod.EventBusSubscriber(modid = AiArsenal.MODID, value = Dist.CLIENT)
public final class NukeFx {
    private static final int LIFE = 3000;
    private static long shaderSafeFrames;
    public static long shaderSafeFrames() { return shaderSafeFrames; }
    private static final RenderType SAFE_CLOUD = RenderType.entityTranslucent(
            new ResourceLocation("aiarsenal", "textures/misc/fx_white.png"));
    private record CloudVertices(VertexConsumer sink, boolean textured) {}

    private static final class Fx {
        final double x, y, z, gy;
        final float r;
        int age;
        boolean heard;
        float vis;
        final long seed;

        Fx(NukeFxPacket p) {
            x = p.x();
            y = p.y();
            z = p.z();
            gy = p.groundY();
            r = p.radius();
            seed = (long) (x * 31 + z * 17);
        }
    }

    private static final class Zone {
        final double x, z;
        final float r;
        int ticks;

        Zone(double x, double z, float r, int ticks) {
            this.x = x;
            this.z = z;
            this.r = r;
            this.ticks = ticks;
        }
    }

    private static final List<Fx> FX = new ArrayList<>();
    private static final List<Zone> ZONES = new ArrayList<>();
    private static float shake;
    private static int geigerCd;
    private static final RandomSource RND = RandomSource.create();

    private NukeFx() {
    }

    public static void add(NukeFxPacket p) {
        Fx f = new Fx(p);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 b = new Vec3(f.x, f.y, f.z);
            double d = eye.distanceTo(b);
            HitResult h = mc.level.clip(new ClipContext(eye, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            boolean los = h.getType() == HitResult.Type.MISS || h.getLocation().distanceTo(b) < 4 || d > 300;
            f.vis = (float) Mth.clamp(6 * f.r / Math.max(1, d), 0, 1) * (los ? 1f : 0.35f);
        }
        ru.aiforge.arsenal.volumetric.client.VolumetricEffects.nuclear(p.x(), p.y(), p.z(), p.radius(), f.seed);
        FX.add(f);
        if (f.y - f.gy < 0.6 * f.r) ZONES.add(new Zone(f.x, f.z, 1.4f * f.r, 6000));
    }

    static void tick(Minecraft mc) {
        if (mc.level == null) {
            FX.clear();
            ZONES.clear();
            return;
        }
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        double c = 343.0 * ArsEntity.scale() / 20.0;   // blocks per tick
        for (Fx f : FX) {
            f.age++;
            double d = ear.distanceTo(new Vec3(f.x, f.y, f.z));
            if (!f.heard && f.age >= d / c) {
                f.heard = true;
                boolean near = d < 4 * f.r;
                float vol = (float) Mth.clamp(1.6 - d / (25 * f.r), 0.25, 1.6);
                mc.getSoundManager().play(new SimpleSoundInstance(near ? ArsenalRegistry.SND_NUKE.get() : ArsenalRegistry.SND_NUKE_FAR.get(),
                        SoundSource.MASTER, vol, near ? 1f : 0.85f, RND, ear.x, ear.y, ear.z) {
                    {
                        this.attenuation = SoundInstance.Attenuation.NONE;
                        this.relative = false;
                    }
                });
                if (d < 6 * f.r) shake = Math.max(shake, (float) Mth.clamp(5 * f.r / Math.max(1, d), 0.4, 5));
            }
        }
        FX.removeIf(f -> f.age > LIFE);
        shake *= 0.94f;
        // fallout geiger
        ZONES.removeIf(z -> --z.ticks <= 0);
        if (mc.player != null && --geigerCd <= 0) {
            float k = 0;
            for (Zone z : ZONES) {
                double d = Math.hypot(mc.player.getX() - z.x, mc.player.getZ() - z.z);
                if (d < z.r) k = Math.max(k, (float) (1 - d / z.r));
            }
            if (k > 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ArsenalRegistry.SND_GEIGER.get(), 0.9f + k * 0.4f, 0.3f + 0.7f * k));
                geigerCd = 20;
            } else geigerCd = 10;
        }
    }

    static void shake(ViewportEvent.ComputeCameraAngles e) {
        if (shake < 0.02f) return;
        e.setPitch(e.getPitch() + (RND.nextFloat() - 0.5f) * shake);
        e.setYaw(e.getYaw() + (RND.nextFloat() - 0.5f) * shake);
        e.setRoll(e.getRoll() + (RND.nextFloat() - 0.5f) * shake * 1.5f);
    }

    /** Flash overlay (blinding white, then an orange afterglow). */
    static void hud(GuiGraphics g, int w, int h, float pt) {
        for (Fx f : FX) {
            float t = f.age + pt;
            if (t > 70 || f.vis <= 0) continue;
            float a = t < 3 ? 1f : (float) Math.pow(Math.max(0, 1 - (t - 3) / 67f), 2.2);
            a *= f.vis;
            int col = t < 12 ? 0xFFFFFF : 0xFFE0A0;
            g.fill(0, 0, w, h, ((int) (a * 255) << 24) | col);
        }
    }

    // ------------------------------------------------------------------ mushroom cloud rendering
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (ru.aiforge.arsenal.volumetric.client.VolumetricEffects.active()) return;
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || FX.isEmpty()) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        float pt = e.getPartialTick();
        boolean safe = ShaderPackCompatibility.blocked();
        if (!safe) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
        for (Fx f : FX) {
            ps.pushPose();
            ps.translate(f.x - cam.x, 0 - cam.y, f.z - cam.z);
            Matrix4f m = ps.last().pose();
            draw(m, f, f.age + pt, cam.subtract(f.x, 0, f.z), safe);
            ps.popPose();
        }
        if (safe) {
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch(SAFE_CLOUD);
            shaderSafeFrames++;
        }
        else {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static float[] lerpCol(float[] a, float[] b, float t) {
        t = Mth.clamp(t, 0, 1);
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static void draw(Matrix4f m, Fx f, float t, Vec3 camRel, boolean safe) {
        float r = f.r;
        double hb = Math.max(0, f.y - f.gy);
        double ground = f.gy;
        float fade = t > LIFE - 600 ? (LIFE - t) / 600f : 1f;
        double rise = 2.4 * r * (1 - Math.exp(-t / 260.0));
        double hc = f.y + rise;                              // cloud centre height
        double grow = 1 - Math.exp(-t / 300.0);
        float a1 = (float) (0.25 * r + 0.55 * r * grow);     // cap major radius
        float b1 = (float) (0.2 * r + 0.25 * r * grow);      // cap minor radius
        // colours
        float[] white = {1f, 1f, 0.95f}, orange = {1f, 0.55f, 0.12f}, red = {0.75f, 0.22f, 0.06f}, smoke = {0.42f, 0.36f, 0.32f},
                grey = {0.72f, 0.69f, 0.66f}, dust = {0.58f, 0.5f, 0.42f};
        float[] core = t < 8 ? white : t < 40 ? lerpCol(white, orange, (t - 8) / 32f) : t < 180 ? lerpCol(orange, red, (t - 40) / 140f)
                : lerpCol(red, smoke, (t - 180) / 200f);
        float[] cloud = t < 200 ? lerpCol(smoke, smoke, 0) : lerpCol(smoke, grey, (t - 200) / 900f);
        BufferBuilder raw = safe ? null : Tesselator.getInstance().getBuilder();
        if (raw != null) raw.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        CloudVertices bb = new CloudVertices(safe
                ? Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(SAFE_CLOUD) : raw, safe);
        // ---- shock ring on the ground
        double rs = 343.0 * ArsEntity.scale() / 20.0 * t;
        if (rs < 3 * r) {
            float a = (float) (0.4 * (1 - rs / (3 * r)));
            ring(bb, m, ground + 0.6, rs, Math.max(2, rs * 0.04), new float[]{0.95f, 0.95f, 0.9f}, a);
        }
        // ---- condensation (Wilson) cloud
        if (t > 6 && t < 70) {
            float a = (float) (0.35 * Math.sin(Math.PI * (t - 6) / 64.0));
            sphere(bb, m, 0, f.y, 0, (float) (0.4 * r + (t - 6) * r * 0.012), 1f, new float[]{0.96f, 0.96f, 0.98f}, a, f.seed, t, 0.05f, 16, 10);
        }
        // ---- base surge / dust skirt
        if (t > 10 && hb < 0.6 * r) {
            double ra = 0.3 * r + 1.1 * r * (1 - Math.exp(-t / 120.0));
            float a = (float) (0.75 * Math.min(1, (t - 10) / 30.0) * (t > 700 ? Math.max(0, 1 - (t - 700) / 900.0) : 1)) * fade;
            torus(bb, m, ground + 0.05 * r, ra, 0.07 * r + 0.06 * r * grow, dust, a, f.seed + 7, t, 32, 6);
        }
        // ---- stem
        if (t > 15) {
            double top = hc - b1 * 0.6;
            double bot = hb < 0.6 * r ? ground : f.y + rise * 0.3;
            float rb = (float) (0.16 * r * Math.min(1, (t - 15) / 80.0)), rt = (float) (0.1 * r * Math.min(1, (t - 15) / 60.0));
            if (top > bot) cylinder(bb, m, bot, top, rb, rt, dust, 0.85f * fade, f.seed + 3, t, 18, 8);
        }
        // ---- cap: torus + dome + puffs
        float capA = 0.95f * fade;
        float[] capCol = t < 220 ? lerpCol(core, cloud, Math.max(0, (t - 60) / 160f)) : cloud;
        torus(bb, m, hc, a1, b1, capCol, capA, f.seed + 11, t, 28, 12);
        sphere(bb, m, 0, hc + b1 * 0.45, 0, a1 * 0.85f, 0.55f, capCol, capA, f.seed + 13, t, 0.12f, 20, 10);
        RandomSource pr = RandomSource.create(f.seed);
        for (int i = 0; i < 12; i++) {
            double ang = i / 12.0 * Math.PI * 2 + pr.nextFloat() * 0.4;
            float pr2 = b1 * (0.55f + pr.nextFloat() * 0.35f);
            double px = Math.cos(ang) * a1 * (0.9 + pr.nextFloat() * 0.3), pz = Math.sin(ang) * a1 * (0.9 + pr.nextFloat() * 0.3);
            sphere(bb, m, (float) px, hc + (pr.nextFloat() - 0.3) * b1 * 0.8, (float) pz, pr2, 0.8f, capCol, capA, f.seed + 20 + i, t, 0.15f, 10, 6);
        }
        if (raw != null) BufferUploader.drawWithShader(raw.end());
        // ---- glowing fireball (additive)
        if (t < 260) {
            if (raw != null) {
                RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                        com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                raw.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            float rf = (float) (0.32 * r * Math.min(1, Math.sqrt(t / 8.0)));
            float a = (float) (t < 10 ? 1 : Math.max(0, 1 - (t - 10) / 250.0));
            double fy = t < 40 ? f.y : hc;
            sphere(bb, m, 0, fy, 0, rf, 1f, core, a, f.seed + 1, t, 0.08f, 18, 12);
            if (t < 40) sphere(bb, m, 0, f.y, 0, rf * 1.4f, 1f, white, a * 0.35f, f.seed + 2, t, 0.02f, 14, 8);
            if (raw != null) BufferUploader.drawWithShader(raw.end());
            if (raw != null) RenderSystem.defaultBlendFunc();
        }
    }

    // ------------------------------------------------------------------ primitives
    private static float noise(long seed, int i, int j, float t) {
        long h = seed * 6364136223846793005L + i * 1442695040888963407L + j * 2862933555777941757L;
        h ^= (h >>> 33);
        float base = ((h & 0xFFFF) / 65535f) - 0.5f;
        return base + 0.3f * (float) Math.sin(t * 0.01 + i * 1.7 + j * 2.3);
    }

    private static void v(CloudVertices bb, Matrix4f m, double x, double y, double z, float[] c, float shade, float a) {
        VertexConsumer vertex = bb.sink.vertex(m, (float) x, (float) y, (float) z)
                .color(c[0] * shade, c[1] * shade, c[2] * shade, a);
        if (bb.textured) vertex.uv(.5f, .5f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0);
        vertex.endVertex();
    }

    /** Ellipsoid (yScale) with noisy surface; shading darkens the underside (sun from above). */
    private static void sphere(CloudVertices bb, Matrix4f m, float cx, double cy, float cz, float r, float yScale, float[] c, float a,
                               long seed, float t, float bump, int seg, int rings) {
        for (int i = 0; i < rings; i++)
            for (int j = 0; j < seg; j++) {
                double[][] p = new double[4][];
                float[] sh = new float[4];
                int[][] ij = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int q = 0; q < 4; q++) {
                    int ii = ij[q][0], jj = ij[q][1] % seg;
                    double th = Math.PI * ii / rings, ph = 2 * Math.PI * jj / seg;
                    float rr = r * (1 + bump * noise(seed, ii, jj, t));
                    double ny = Math.cos(th);
                    p[q] = new double[]{cx + Math.sin(th) * Math.cos(ph) * rr, cy + ny * rr * yScale, cz + Math.sin(th) * Math.sin(ph) * rr};
                    sh[q] = (float) (0.62 + 0.38 * (ny * 0.5 + 0.5));
                }
                for (int q = 0; q < 4; q++) v(bb, m, p[q][0], p[q][1], p[q][2], c, sh[q], a);
            }
    }

    private static void torus(CloudVertices bb, Matrix4f m, double cy, double big, double small, float[] c, float a, long seed, float t, int seg,
                              int sides) {
        for (int i = 0; i < seg; i++)
            for (int j = 0; j < sides; j++) {
                int[][] ij = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int q = 0; q < 4; q++) {
                    int ii = ij[q][0] % seg, jj = ij[q][1] % sides;
                    double u = 2 * Math.PI * ii / seg, w = 2 * Math.PI * jj / sides;
                    double rr = small * (1 + 0.18 * noise(seed, ii, jj, t));
                    double rad = big + Math.cos(w) * rr;
                    double ny = Math.sin(w);
                    v(bb, m, Math.cos(u) * rad, cy + ny * rr, Math.sin(u) * rad, c, (float) (0.6 + 0.4 * (ny * 0.5 + 0.5)), a);
                }
            }
    }

    private static void cylinder(CloudVertices bb, Matrix4f m, double y0, double y1, float r0, float r1, float[] c, float a, long seed, float t,
                                 int seg, int rings) {
        for (int i = 0; i < rings; i++)
            for (int j = 0; j < seg; j++) {
                int[][] ij = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int q = 0; q < 4; q++) {
                    int ii = ij[q][0], jj = ij[q][1] % seg;
                    double k = ii / (double) rings;
                    double y = y0 + (y1 - y0) * k;
                    double rr = (r0 + (r1 - r0) * k) * (1 + 0.15 * noise(seed, ii, jj, t));
                    double ph = 2 * Math.PI * jj / seg;
                    v(bb, m, Math.cos(ph) * rr, y, Math.sin(ph) * rr, c, (float) (0.7 + 0.3 * Math.cos(ph)), a);
                }
            }
    }

    private static void ring(CloudVertices bb, Matrix4f m, double y, double r, double w, float[] c, float a) {
        int seg = 64;
        for (int j = 0; j < seg; j++) {
            double p0 = 2 * Math.PI * j / seg, p1 = 2 * Math.PI * (j + 1) / seg;
            v(bb, m, Math.cos(p0) * (r - w), y, Math.sin(p0) * (r - w), c, 1, 0);
            v(bb, m, Math.cos(p0) * r, y + w * 0.3, Math.sin(p0) * r, c, 1, a);
            v(bb, m, Math.cos(p1) * r, y + w * 0.3, Math.sin(p1) * r, c, 1, a);
            v(bb, m, Math.cos(p1) * (r - w), y, Math.sin(p1) * (r - w), c, 1, 0);
        }
    }
}
