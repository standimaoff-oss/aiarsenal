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
 *  net.minecraft.util.Mth
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class VehicleRenderer<T extends VehicleEntity>
extends EntityRenderer<T> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

    public VehicleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.2f;
    }

    public void render(T e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        GlbAssets.Loaded lm = GlbAssets.get(((VehicleEntity)(e)).spec.model());
        if (lm == null) {
            return;
        }
        Pose pose = lm.scratch();
        ((VehicleEntity)(e)).applyPose(pose, partial);
        DamageRender.vehicle((VehicleEntity) e, pose);
        pose.compute();
        ps.pushPose();
        float ry = Mth.rotLerp((float)partial, (float)((VehicleEntity)(e)).yRotO, (float)e.getYRot());
        VehicleEntity ve = (VehicleEntity) e;
        ps.translate(0.0f, ve.dyLerp(partial), 0.0f);
        ps.mulPose(Axis.YP.rotationDegrees(-ry));
        // r11: terrain pitch / roll about the model origin (ground under the hull centre)
        ps.mulPose(Axis.XP.rotationDegrees(-ve.pitchLerp(partial)));
        ps.mulPose(Axis.ZP.rotationDegrees(ve.rollLerp(partial)));
        GlbRenderer.TINT = ve.dmg.dead() ? 0.28f : 1f;
        GlbRenderer.render(lm, pose, ps, buf, light, OverlayTexture.NO_OVERLAY);
        GlbRenderer.TINT = 1f;
        DamageRender.holes(ve.dmg, ps, buf, light);
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    public ResourceLocation getTextureLocation(T e) {
        return WHITE;
    }
}

