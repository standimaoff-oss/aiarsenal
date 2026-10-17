package ru.aiforge.realwar.client.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import ru.aiforge.realwar.deco.CrateBlock;
import ru.aiforge.realwar.deco.CrateBlockEntity;
import ru.aiforge.realwar.deco.CrateKind;

/** r17: crate lid hinged on the back top edge, swings up to ~100 degrees with an ease-out like a chest. */
public class CrateRenderer implements BlockEntityRenderer<CrateBlockEntity> {
    public CrateRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CrateBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        BlockState s = be.getBlockState();
        if (!(s.getBlock() instanceof CrateBlock cb)) return;
        CrateKind k = cb.kind;
        float t = Mth.lerp(pt, be.prevOpen, be.open);
        t = 1f - (1f - t) * (1f - t) * (1f - t);
        ps.pushPose();
        AnimDoorRenderer.rotY(ps, AnimDoorRenderer.rot(s.getValue(CrateBlock.FACING)));
        ps.translate(0, k.pivotY, k.pivotZ);
        ps.mulPose(Axis.XP.rotationDegrees(-100f * t));
        ps.translate(0, -k.pivotY, -k.pivotZ);
        DecoModels.drawNamed(k.lid(), ps, buf, s, light, false);
        ps.popPose();
    }
}
