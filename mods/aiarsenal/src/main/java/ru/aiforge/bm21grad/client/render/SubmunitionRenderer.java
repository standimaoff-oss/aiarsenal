package ru.aiforge.bm21grad.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ru.aiforge.bm21grad.client.glb.GlbRenderer;
import ru.aiforge.bm21grad.entity.SubmunitionEntity;
import ru.aiforge.bm21grad.glb.GlbModel;
import ru.aiforge.bm21grad.glb.GlbModels;
import ru.aiforge.bm21grad.glb.GlbPose;

/** Bomblet: the rocket body GLB at 1/8 scale, nose down, tumbling. */
public class SubmunitionRenderer extends EntityRenderer<SubmunitionEntity> {
    private static final float[] TINT = {0.55f, 0.6f, 0.45f, 1};
    private GlbPose pose;

    public SubmunitionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SubmunitionEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        GlbModel m = GlbModels.get(GlbModels.ROCKET);
        if (pose == null) {
            pose = new GlbPose(m);
            pose.setVisible(m.node("Flame"), false);
            pose.apply("fins_deploy", 1f, false, 1f);
            pose.computeGlobals();
        }
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees((e.tickCount + pt) * 35f));
        ps.mulPose(Axis.XP.rotationDegrees(80f));
        ps.scale(0.125f, 0.125f, 0.125f);
        GlbRenderer.of(m).render(pose, ps, buf, light, OverlayTexture.NO_OVERLAY, TINT, null, false);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SubmunitionEntity e) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    }
}
