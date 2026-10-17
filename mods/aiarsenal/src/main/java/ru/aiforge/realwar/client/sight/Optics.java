package ru.aiforge.realwar.client.sight;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;

/**
 * r21 night vision and thermal channels of the vehicle sights (key K in the sight):
 * <ul>
 *   <li>NV: the light map is lifted like the night vision effect and the picture goes through an image-intensifier
 *   post shader (green phosphor, gain, grain, vignette);</li>
 *   <li>thermal (white hot): every living body and running / burning vehicle near the target area is drawn once more
 *   in a magenta heat signature (red = blue = heat), the thermal post shader turns the world into dim greys and
 *   the signatures into white.</li>
 * </ul>
 * Modern vehicles have both channels, the launcher trucks only NV, WW2 vehicles none.
 */
public final class Optics {
    public static final int DAY = 0, NV = 1, THERMAL = 2;
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    public static int mode;
    @Nullable
    private static PostChain chain;
    private static int chainMode = -1, chainW, chainH;
    private static boolean ourEffect;

    private Optics() {
    }

    /** 0 = day only, 1 = + NV, 2 = + NV + thermal. */
    public static int channels(VehicleKind k) {
        return switch (k) {
            case T90M, ABRAMS, BMP3, BRADLEY, MSTA, TOS1A -> 2;
            case GRAD, SMERCH, S400, RADAR_MOBILE, YARS, ZU23 -> 1;
            default -> 0;
        };
    }

    @Nullable
    static LauncherVehicle sightVehicle(Minecraft mc) {
        if (mc.player == null || !SightClient.active()) return null;
        return mc.player.getVehicle() instanceof LauncherVehicle lv && SightClient.isOperator(mc, lv) ? lv : null;
    }

    /** Current effective channel (0 when not in the sight). */
    public static int active() {
        LauncherVehicle lv = sightVehicle(Minecraft.getInstance());
        if (lv == null) return DAY;
        return Math.min(mode, channels(lv.kind));
    }

    public static void cycle() {
        Minecraft mc = Minecraft.getInstance();
        LauncherVehicle lv = sightVehicle(mc);
        if (lv == null) return;
        int n = channels(lv.kind);
        if (n == 0) {
            mc.player.displayClientMessage(Component.translatable("hud.aiarsenal.rw.optics.none"), true);
            return;
        }
        mode = (Math.min(mode, n) + 1) % (n + 1);
        mc.player.displayClientMessage(Component.translatable("hud.aiarsenal.rw.optics." + new String[]{"day", "nv", "thermal"}[mode]), true);
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        int a = active();
        MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
        if (a != DAY) {
            if (cur == null || ourEffect && cur.getDuration() < 300) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, false));
                ourEffect = true;
            }
        } else if (ourEffect) {
            if (cur != null && cur.getDuration() <= 400) mc.player.removeEffect(MobEffects.NIGHT_VISION);
            ourEffect = false;
        }
    }

    /** Called from the sight overlay before the reticle: runs the post shader over the rendered world. */
    public static void post(float pt) {
        int a = active();
        if (a == DAY) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        try {
            if (chain == null || chainMode != a) {
                if (chain != null) chain.close();
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
                        new ResourceLocation("aiarsenal", "shaders/post/" + (a == NV ? "nv" : "thermal") + ".json"));
                chainMode = a;
                chainW = chainH = -1;
            }
            if (w != chainW || h != chainH) {
                chain.resize(w, h);
                chainW = w;
                chainH = h;
            }
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(pt);
        } catch (Exception ex) {
            ru.aiforge.realwar.RealWar.LOGGER.warn("sight post shader failed: {}", ex.toString());
            mode = DAY;
            if (chain != null) chain.close();
            chain = null;
        } finally {
            mc.getMainRenderTarget().bindWrite(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    // ========================================================================================== heat signatures

    static float heat(Entity e) {
        if (e instanceof LivingEntity le) {
            if (le.isDeadOrDying()) return 0.5f;
            return le.getMobType() == MobType.UNDEAD ? 0.55f : 1.0f;
        }
        if (e instanceof VehicleEntity v) {
            if (v.dmg.burning()) return 1.0f;
            if (v.dmg.dead()) return 0.6f;
            return v.engineOn() ? 0.85f : 0.2f;
        }
        if (e instanceof ru.aiforge.realwar.craft.CraftEntity c) return c.engineOn() ? 0.9f : 0.2f;
        return 0f;
    }

    /** Re-renders the warm entities in the heat colour (render stage after particles). */
    public static void renderHeat(PoseStack ps, Camera cam, float pt) {
        if (active() != THERMAL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 c = cam.getPosition();
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource real = mc.renderBuffers().bufferSource();
        Entity own = mc.player.getVehicle();
        boolean shadows = mc.options.entityShadows().get();
        d.setRenderShadow(false);
        int n = 0;
        try {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || e == own || e.isPassengerOfSameVehicle(mc.player) && e != own && e instanceof LivingEntity) continue;
                float h = heat(e);
                if (h <= 0f || e.distanceToSqr(c) > 200 * 200) continue;
                if (++n > 64) break;
                Heat src = new Heat(real, h);
                double x = e.xOld + (e.getX() - e.xOld) * pt - c.x, y = e.yOld + (e.getY() - e.yOld) * pt - c.y, z = e.zOld + (e.getZ() - e.zOld) * pt - c.z;
                float yaw = e.yRotO + (e.getYRot() - e.yRotO) * pt;
                d.render(e, x, y, z, yaw, pt, ps, src, 0xF000F0);
            }
        } catch (Throwable ex) {
            ru.aiforge.realwar.RealWar.LOGGER.warn("thermal pass failed: {}", ex.toString());
        } finally {
            d.setRenderShadow(shadows);
        }
        real.endBatch(RenderType.entityTranslucentEmissive(WHITE));
    }

    /** Buffer source that sends every entity layer into one emissive white layer with the heat colour. */
    static final class Heat implements MultiBufferSource {
        private final MultiBufferSource.BufferSource real;
        private final float h;

        Heat(MultiBufferSource.BufferSource real, float h) {
            this.real = real;
            this.h = h;
        }

        @Override
        public VertexConsumer getBuffer(RenderType rt) {
            String n = rt.toString();                         // "RenderType[<name>:CompositeState[...]]"
            int i = n.indexOf('['), j = n.indexOf(':');
            if (i >= 0 && j > i) n = n.substring(i + 1, j);
            if (n.contains("text") || n.contains("shadow") || n.contains("lines") || n.contains("lightning") || n.contains("leash")) return NOOP;
            return new HeatVc(this.real.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), this.h);
        }
    }

    static final VertexConsumer NOOP = new VertexConsumer() {
        public VertexConsumer vertex(double x, double y, double z) { return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() { }
        public void defaultColor(int r, int g, int b, int a) { }
        public void unsetDefaultColor() { }
    };

    static final class HeatVc implements VertexConsumer {
        private final VertexConsumer d;
        private final int c;

        HeatVc(VertexConsumer d, float h) {
            this.d = d;
            this.c = Math.round(h * 255f);
        }

        public VertexConsumer vertex(double x, double y, double z) { this.d.vertex(x, y, z); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { this.d.color(this.c, 0, this.c, 255); return this; }
        public VertexConsumer uv(float u, float v) { this.d.uv(0.5f, 0.5f); return this; }
        public VertexConsumer overlayCoords(int u, int v) { this.d.overlayCoords(0, 10); return this; }
        public VertexConsumer uv2(int u, int v) { this.d.uv2(240, 240); return this; }
        public VertexConsumer normal(float x, float y, float z) { this.d.normal(x, y, z); return this; }
        public void endVertex() { this.d.endVertex(); }
        public void defaultColor(int r, int g, int b, int a) { }
        public void unsetDefaultColor() { }
    }
}
