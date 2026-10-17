/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.math.Axis
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.client.renderer.culling.Frustum
 *  net.minecraft.client.renderer.entity.EntityRenderer
 *  net.minecraft.client.renderer.entity.EntityRendererProvider$Context
 *  net.minecraft.client.renderer.texture.OverlayTexture
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.missile.MissileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class MissileRenderer
extends EntityRenderer<MissileEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final ResourceLocation RV = new ResourceLocation("aiarsenal", "models/glb/missile_rv.glb");

    public MissileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    public boolean shouldRender(MissileEntity e, Frustum f, double x, double y, double z) {
        return e.mode() != 2;
    }

    public void render(MissileEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        if (e.mode() == 2) {
            return;
        }
        GlbAssets.Loaded lm = GlbAssets.get(e.mode() == 1 ? RV : e.type().model());
        if (lm == null) {
            return;
        }
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-e.yawDeg()));
        ps.mulPose(Axis.XP.rotationDegrees(e.pitchDeg()));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp((float)partial, (float)e.rollO, (float)e.roll)));
        GlbRenderer.render(lm, lm.staticPose(), ps, buf, 0xF000F0, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    public ResourceLocation getTextureLocation(MissileEntity e) {
        return WHITE;
    }
}

