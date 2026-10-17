package ru.aiforge.tubombers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.client.glb.GlbRenderer;
import ru.aiforge.tubombers.entity.BombEntity;
import ru.aiforge.tubombers.glb.GlbModel;
import ru.aiforge.tubombers.glb.GlbModels;
import ru.aiforge.tubombers.glb.GlbPose;

/** Falling FAB bomb from bombs.glb, nose along the velocity vector (weathercock stable). */
public class BombRenderer extends EntityRenderer<BombEntity> {
    private static final float[] WHITE = {1, 1, 1, 1};
    private GlbPose pose;

    public BombRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.3f;
    }

    @Override
    public void render(BombEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        GlbModel m = GlbModels.get(GlbModels.BOMBS);
        if (pose == null) pose = new GlbPose(m);
        pose.reset();
        BombType t = e.type();
        for (BombType o : BombType.values()) {
            int ni = m.node(o.node);
            if (ni >= 0) pose.setVisible(ni, o == t);
        }
        pose.computeGlobals();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.visYawO, e.visYaw)));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, e.visPitchO, e.visPitch)));
        GlbRenderer.of(m).render(pose, ps, buf, light, OverlayTexture.NO_OVERLAY, WHITE, null, false);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BombEntity e) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    }
}
