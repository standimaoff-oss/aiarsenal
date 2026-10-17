package ru.aiforge.bm21grad.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.client.glb.GlbRenderer;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.glb.GlbModel;
import ru.aiforge.bm21grad.glb.GlbModels;
import ru.aiforge.bm21grad.glb.GlbPose;
import ru.aiforge.bm21grad.item.Bm21SpawnerItem;
import ru.aiforge.bm21grad.item.RocketItem;

/** Draws the actual GLB models as item icons / in hand. */
public class GlbItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float[] WHITE = {1, 1, 1, 1};
    private GlbPose vehiclePose, rocketPose;

    public GlbItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        if (stack.getItem() instanceof Bm21SpawnerItem) {
            GlbModel m = GlbModels.get(GlbModels.BM21);
            if (vehiclePose == null) {
                vehiclePose = new GlbPose(m);
                Bm21Entity.poseLauncher(vehiclePose, 180f, 25f);
                vehiclePose.apply("shields_lower", 1f, false, 1f);
                vehiclePose.computeGlobals();
            }
            float s = 0.125f;
            ps.scale(s, s, s);
            ps.translate(0, -1.6, 0.4);
            GlbRenderer.of(m).render(vehiclePose, ps, buf, light, overlay, WHITE, null, false);
        } else if (stack.getItem() instanceof RocketItem ri) {
            GlbModel m = GlbModels.get(GlbModels.ROCKET);
            if (rocketPose == null) {
                rocketPose = new GlbPose(m);
                RocketRenderer.poseRocket(rocketPose, 20f, false);
            }
            ps.mulPose(Axis.ZP.rotationDegrees(45));
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            float s = 0.32f;
            // a 122 mm x 2.87 m rocket is a 1-pixel line at icon size: fatten it 3x in inventory-like contexts only
            float k = (ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.FIXED || ctx == ItemDisplayContext.GROUND) ? 3.0f : 1.4f;
            ps.scale(s * k, s * k, s);
            RocketType t = ri.type;
            GlbRenderer.of(m).render(rocketPose, ps, buf, light, overlay, WHITE, RocketRenderer.bandTint(t), false);
        }
        ps.popPose();
    }
}
