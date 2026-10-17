/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.math.Axis
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.client.renderer.entity.EntityRenderer
 *  net.minecraft.client.renderer.entity.EntityRendererProvider$Context
 *  net.minecraft.client.renderer.texture.OverlayTexture
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ru.aiforge.realwar.aircraft.AirBombEntity;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class AirBombRenderer
extends EntityRenderer<AirBombEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

    public AirBombRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    public void render(AirBombEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        GlbAssets.Loaded lm = GlbAssets.get(e.bombType().model());
        if (lm == null) {
            return;
        }
        Vec3 v = e.getDeltaMovement();
        double hv = Math.sqrt(v.x * v.x + v.z * v.z);
        float yawRad = hv > 0.02 ? (float)Math.atan2(v.x, v.z) : 0.0f;
        float pitch = (float)Math.atan2(-v.y, Math.max(hv, 1.0E-4));
        ps.pushPose();
        ps.translate(0.0, (double)e.getBbHeight() * 0.5, 0.0);
        ps.mulPose(Axis.YP.rotation(yawRad));
        ps.mulPose(Axis.XP.rotation(pitch));
        GlbRenderer.render(lm, lm.staticPose(), ps, buf, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    public ResourceLocation getTextureLocation(AirBombEntity e) {
        return WHITE;
    }
}

