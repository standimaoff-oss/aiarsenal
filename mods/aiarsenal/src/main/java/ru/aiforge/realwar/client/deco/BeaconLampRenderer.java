package ru.aiforge.realwar.client.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.block.state.BlockState;
import ru.aiforge.realwar.deco.BeaconLampBlock;
import ru.aiforge.realwar.deco.BeaconLampBlockEntity;
import ru.aiforge.realwar.deco.LampBlock;

/** Red obstruction light: 1 Hz soft blink halo. Rotating beacon: spinning mirror (baked OBJ) + sweeping light fan + halo. */
public class BeaconLampRenderer implements BlockEntityRenderer<BeaconLampBlockEntity> {
    public BeaconLampRenderer(BlockEntityRendererProvider.Context c) {
    }

    @Override
    public void render(BeaconLampBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (be.getLevel() == null) return;
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof BeaconLampBlock b)) return;
        boolean lit = st.getValue(LampBlock.LIT);
        double t = be.getLevel().getGameTime() + pt;
        if (b.rotating) {
            float ang = lit ? (float) (t * 15.0 % 360.0) : 0f;
            ps.pushPose();
            ps.translate(0.5, 0, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(-ang));
            ps.translate(-0.5, 0, -0.5);
            DecoModels.draw(DecoModels.REFLECTOR, ps, buf, st, lit ? LightTexture.FULL_BRIGHT : light);
            if (lit) {
                VertexConsumer vc = buf.getBuffer(DecoRenderTypes.GLOW);
                ps.translate(0.5, 0.135, 0.5);
                Glow.cone(ps.last().pose(), vc, 0.07f, 0.75f, 2.8f, 255, 140, 20, 70, 8);
                Glow.halo(ps, vc, 0.32f, 255, 150, 30, 150);
            }
            ps.popPose();
        } else if (lit) {
            double s = 0.5 + 0.5 * Math.cos(t * Math.PI * 2 / 20.0);
            float k = (float) Math.pow(s, 3);
            if (k > 0.02f) {
                ps.pushPose();
                ps.translate(0.5, 0.11, 0.5);
                Glow.halo(ps, buf.getBuffer(DecoRenderTypes.GLOW), 0.12f + 0.28f * k, 255, 30, 20, (int) (220 * k));
                ps.popPose();
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
