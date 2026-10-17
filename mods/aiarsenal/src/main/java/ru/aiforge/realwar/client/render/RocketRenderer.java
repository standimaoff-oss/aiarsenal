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
 *  net.minecraft.world.entity.Entity
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.ordnance.RocketEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class RocketRenderer
extends EntityRenderer<RocketEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final ResourceLocation[] MODELS = new ResourceLocation[]{new ResourceLocation("aiarsenal", "models/glb/rocket_grad.glb"), new ResourceLocation("aiarsenal", "models/glb/rocket_smerch.glb"), new ResourceLocation("aiarsenal", "models/glb/sam_48n6.glb")};

    private static final ResourceLocation M13 = new ResourceLocation("aiarsenal", "models/glb/rocket_m13.glb");
    private static final ResourceLocation TOS = new ResourceLocation("aiarsenal", "models/glb/rocket_tos220.glb");

    public RocketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    public void render(RocketEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        ResourceLocation rl = e.profile() == ru.aiforge.realwar.ordnance.Ballistics.Profile.M13 ? M13
                : e.profile() == ru.aiforge.realwar.ordnance.Ballistics.Profile.TOS220 ? TOS
                : (e.profile().ordinal() < MODELS.length ? MODELS[e.profile().ordinal()] : null);
        if (rl == null) {
            return;
        }
        GlbAssets.Loaded lm = GlbAssets.get(rl);
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

    public ResourceLocation getTextureLocation(RocketEntity e) {
        return WHITE;
    }
}

