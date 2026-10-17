package ru.aiforge.realwar.client.craft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.craft.AradoEntity;
import ru.aiforge.realwar.glb.Pose;

/** Arado Ar 196 floatplane (models/glb/ar196.glb): yaw / pitch / bank about the centre of gravity, spinning prop. */
public class AradoRenderer extends EntityRenderer<AradoEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation("aiarsenal", "models/glb/ar196.glb");
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

    public AradoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.5f;
    }

    @Override
    public void render(AradoEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        GlbAssets.Loaded lm = GlbAssets.get(MODEL);
        if (lm == null) return;
        Pose pose = lm.scratch();
        float prop = e.propAngleO + Mth.wrapDegrees(e.propAngle - e.propAngleO) * pt;
        pose.rotate("prop", (float) Math.toRadians(prop), 0, 0, 1);
        pose.compute();
        ps.pushPose();
        ps.translate(0, 1.6, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, e.rollO, e.rollC())));
        ps.translate(0, -1.6, 0);
        GlbRenderer.render(lm, pose, ps, buf, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AradoEntity e) {
        return WHITE;
    }
}
