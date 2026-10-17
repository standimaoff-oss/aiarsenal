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
import ru.aiforge.realwar.craft.CraftOrdnance;
import ru.aiforge.realwar.glb.Pose;

/** Missiles, rockets and torpedoes (nodes of models/glb/craft_ordnance.glb); flares are particles only. */
public class CraftOrdnanceRenderer extends EntityRenderer<CraftOrdnance> {
    private static final ResourceLocation MODEL = new ResourceLocation("aiarsenal", "models/glb/craft_ordnance.glb");
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

    public CraftOrdnanceRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(CraftOrdnance e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        CraftOrdnance.Type t = e.type();
        if (t == CraftOrdnance.Type.FLARE) return;
        GlbAssets.Loaded lm = GlbAssets.get(MODEL);
        if (lm == null) return;
        Pose pose = lm.scratch();
        for (CraftOrdnance.Type o : CraftOrdnance.Type.values()) if (!o.node.isEmpty()) pose.hide(o.node, true);
        pose.hide(t.node, false);   // several types may share a node (Kalibr uses the 533 mm body)
        pose.compute();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
        GlbRenderer.render(lm, pose, ps, buf, t == CraftOrdnance.Type.TORPEDO ? light : 0xF000F0, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CraftOrdnance e) {
        return WHITE;
    }
}
