/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.blaze3d.vertex.BufferBuilder
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.blaze3d.vertex.Tesselator
 *  com.mojang.blaze3d.vertex.VertexConsumer
 *  net.minecraft.client.Camera
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.particle.ParticleRenderType
 *  net.minecraft.client.renderer.LightTexture
 *  net.minecraft.client.renderer.culling.Frustum
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ru.aiforge.realwar.RealWar;
import ru.aiforge.arsenal.volumetric.client.render.ShaderPackCompatibility;
import ru.aiforge.realwar.client.particle.FxParticle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;

public final class FxSystem {
    private static final List<FxParticle> ALL = new ArrayList<FxParticle>();
    private static final List<FxParticle> VISIBLE = new ArrayList<FxParticle>();
    private static boolean renderFailed;
    private static long shaderSafeParticles;
    public static long shaderSafeParticles() { return shaderSafeParticles; }

    private FxSystem() {
    }

    private static void handToParticleEngine(FxParticle p) {
        p.useShaderSafeRendering();
        shaderSafeParticles++;
        Minecraft.getInstance().particleEngine.add(p);
    }

    public static void add(FxParticle p) {
        if (ShaderPackCompatibility.blocked()) {
            handToParticleEngine(p);
            return;
        }
        if (p.volumetricSmoke() && ru.aiforge.arsenal.volumetric.client.VolumetricEffects.smoke(p.px(), p.py(), p.pz(), p.size, p.lifeSeconds())) return;
        ALL.add(p);
    }

    public static int count() {
        return ALL.size();
    }

    public static void clear() {
        ALL.clear();
        VISIBLE.clear();
    }

    public static void tick() {
        if (ShaderPackCompatibility.blocked()) {
            // Transfer ownership exactly once when a pack is enabled mid-effect.
            // The vanilla engine now owns both ticking and rendering.
            for (FxParticle p : ALL) handToParticleEngine(p);
            ALL.clear();VISIBLE.clear();
            return;
        }
        Iterator<FxParticle> it = ALL.iterator();
        while (it.hasNext()) {
            FxParticle p = it.next();
            p.tick();
            if (p.isAlive()) continue;
            it.remove();
        }
    }

    public static void render(PoseStack levelPose, Camera camera, float partial, Frustum frustum) {
        if (ShaderPackCompatibility.blocked() || ALL.isEmpty() || renderFailed) {
            return;
        }
        try {
            FxSystem.renderSorted(levelPose, camera, partial, frustum);
        }
        catch (Throwable t) {
            renderFailed = true;
            RealWar.LOGGER.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u043e\u0442\u0440\u0438\u0441\u043e\u0432\u043a\u0438 \u0447\u0430\u0441\u0442\u0438\u0446 \u0432\u0437\u0440\u044b\u0432\u0430 \u2014 \u043e\u0442\u0440\u0438\u0441\u043e\u0432\u043a\u0430 \u044d\u0444\u0444\u0435\u043a\u0442\u043e\u0432 \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u0430 \u0434\u043e \u043f\u0435\u0440\u0435\u0437\u0430\u043f\u0443\u0441\u043a\u0430", t);
            RenderSystem.depthMask((boolean)true);
            RenderSystem.disableBlend();
        }
    }

    private static void renderSorted(PoseStack levelPose, Camera camera, float partial, Frustum frustum) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cp = camera.getPosition();
        VISIBLE.clear();
        for (FxParticle p : ALL) {
            if (frustum != null && !frustum.isVisible(p.getBoundingBox())) continue;
            double dx = p.lerpX(partial) - cp.x;
            double dy = p.lerpY(partial) - cp.y;
            double dz = p.lerpZ(partial) - cp.z;
            p.sortKey = dx * dx + dy * dy + dz * dz;
            VISIBLE.add(p);
        }
        if (VISIBLE.isEmpty()) {
            return;
        }
        VISIBLE.sort((a, b) -> Double.compare(b.sortKey, a.sortKey));
        LightTexture lt = mc.gameRenderer.lightTexture();
        lt.turnOnLightLayer();
        RenderSystem.enableDepthTest();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.mulPoseMatrix(levelPose.last().pose());
        RenderSystem.applyModelViewMatrix();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        ParticleRenderType current = null;
        for (FxParticle p : VISIBLE) {
            ParticleRenderType type = p.getRenderType();
            if (type != current) {
                if (current != null) {
                    current.end(tess);
                }
                current = type;
                current.begin(buf, mc.getTextureManager());
            }
            p.render((VertexConsumer)buf, camera, partial);
        }
        if (current != null) {
            current.end(tess);
        }
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.disableBlend();
        lt.turnOffLightLayer();
    }
}

