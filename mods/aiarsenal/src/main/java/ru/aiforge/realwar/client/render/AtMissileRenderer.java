package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.aiforge.realwar.handheld.AtMissileEntity;

/** Draws PG-7VL / Igla / Javelin missiles from their Blender GLBs (model forward = glTF +Z). */
public class AtMissileRenderer extends EntityRenderer<AtMissileEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final ResourceLocation[] MODELS = {
            new ResourceLocation("aiarsenal", "models/glb/pg7v.glb"),
            new ResourceLocation("aiarsenal", "models/glb/igla_missile.glb"),
            new ResourceLocation("aiarsenal", "models/glb/javelin_missile.glb"),
            new ResourceLocation("aiarsenal", "models/glb/tow_missile.glb"),
            new ResourceLocation("aiarsenal", "models/glb/stinger_missile.glb")};

    public AtMissileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(AtMissileEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        GlbAssets.Loaded lm = GlbAssets.get(MODELS[e.kind().ordinal()]);
        if (lm == null) return;
        ps.pushPose();
        ps.translate(0, e.getBbHeight() / 2, 0);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, e.xRotO, e.getXRot())));
        ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + pt) * 18.0f));         // spin-stabilised
        GlbRenderer.render(lm, lm.staticPose(), ps, buf, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AtMissileEntity e) {
        return WHITE;
    }
}
