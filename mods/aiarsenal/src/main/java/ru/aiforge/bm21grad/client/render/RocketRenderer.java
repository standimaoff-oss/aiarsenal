package ru.aiforge.bm21grad.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.ballistics.Ballistics;
import ru.aiforge.bm21grad.client.glb.GlbRenderer;
import ru.aiforge.bm21grad.entity.RocketEntity;
import ru.aiforge.bm21grad.glb.GlbModel;
import ru.aiforge.bm21grad.glb.GlbModels;
import ru.aiforge.bm21grad.glb.GlbPose;

/** 9M22U-family rocket from rocket_9m22u.glb: fins_deploy + spin clips, motor flame while burning, type band colour. */
public class RocketRenderer extends EntityRenderer<RocketEntity> {
    private static final float[] WHITE = {1, 1, 1, 1};
    public static final float NOSE_Z = 1.305f;
    private GlbPose pose;

    public RocketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    public static void poseRocket(GlbPose pose, float age, boolean flame) {
        pose.reset();
        pose.apply("fins_deploy", age / 20f, false, 1f);
        pose.apply("spin", age / 20f, true, 1f);
        pose.setVisible(pose.model.node("Flame"), flame);
        pose.computeGlobals();
    }

    public static GlbRenderer.MaterialTint bandTint(RocketType t) {
        float[] c = t.bandRgb();
        return (idx, mat) -> mat != null && mat.name.startsWith("M_Rocket_Band") ? c : null;
    }

    @Override
    public void render(RocketEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        GlbModel m = GlbModels.get(GlbModels.ROCKET);
        if (pose == null) pose = new GlbPose(m);
        float age = e.getFlightAge() + pt;
        poseRocket(pose, age, age < Ballistics.BURN_TICKS + 1);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
        ps.translate(0, 0, -NOSE_Z);
        GlbRenderer.of(m).render(pose, ps, buf, light, OverlayTexture.NO_OVERLAY, WHITE, bandTint(e.getRocketType()), true);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RocketEntity e) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    }
}
