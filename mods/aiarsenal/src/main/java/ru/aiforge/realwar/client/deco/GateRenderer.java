package ru.aiforge.realwar.client.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import ru.aiforge.realwar.deco.GateBlock;
import ru.aiforge.realwar.deco.GateBlockEntity;
import ru.aiforge.realwar.deco.GateKind;

/** r16: moving parts of the multi-block gates (pivots / travel from tools/blender/door_blocks.py). */
public class GateRenderer implements BlockEntityRenderer<GateBlockEntity> {
    public GateRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(GateBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        BlockState s = be.getBlockState();
        if (!(s.getBlock() instanceof GateBlock g)) return;
        GateKind k = g.kind;
        float t = Mth.lerp(pt, be.prevProg, be.prog);
        float e = t * t * (3f - 2f * t);
        ps.pushPose();
        AnimDoorRenderer.rotY(ps, AnimDoorRenderer.rot(s.getValue(GateBlock.FACING)));
        switch (k) {
            case SLIDING -> {
                ps.translate(-4.6f * e, 0, 0);
                DecoModels.drawNamed(k.part(0), ps, buf, s, light, false);
            }
            case SWING -> {
                pivotY(ps, 0.32f, 0.5f, 90f * e);
                DecoModels.drawNamed(k.part(0), ps, buf, s, light, false);
                ps.popPose();
                ps.pushPose();
                AnimDoorRenderer.rotY(ps, AnimDoorRenderer.rot(s.getValue(GateBlock.FACING)));
                pivotY(ps, 3.68f, 0.5f, -90f * e);
                DecoModels.drawNamed(k.part(1), ps, buf, s, light, false);
            }
            case BOOM -> {
                ps.translate(0.5, 0.95, 0.24);
                ps.mulPose(Axis.ZP.rotationDegrees(85f * e));
                ps.translate(-0.5, -0.95, -0.24);
                DecoModels.drawNamed(k.part(0), ps, buf, s, light, false);
            }
            case BLAST -> {
                ps.pushPose();
                ps.translate(-1.45f * e, 0, 0);
                DecoModels.drawNamed(k.part(0), ps, buf, s, light, false);
                ps.popPose();
                ps.translate(1.45f * e, 0, 0);
                DecoModels.drawNamed(k.part(1), ps, buf, s, light, false);
            }
        }
        ps.popPose();
    }

    private static void pivotY(PoseStack ps, float x, float z, float deg) {
        ps.translate(x, 0, z);
        ps.mulPose(Axis.YP.rotationDegrees(deg));
        ps.translate(-x, 0, -z);
    }

    @Override
    public boolean shouldRenderOffScreen(GateBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
