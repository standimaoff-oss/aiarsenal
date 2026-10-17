package ru.aiforge.realwar.client.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import ru.aiforge.realwar.deco.AnimDoorBlockEntity;
import ru.aiforge.realwar.deco.DoorKind;
import ru.aiforge.realwar.deco.HasDoorKind;

/** r16: animated door leaf / hatch lid (+ handwheel that turns before the leaf moves). Canonical model space = facing north,
 *  hinge left; rotated into place like the blockstate (north 0, east 90, south 180, west 270 clockwise). */
public class AnimDoorRenderer implements BlockEntityRenderer<AnimDoorBlockEntity> {
    public AnimDoorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    static int rot(Direction f) {
        return ((f.get2DDataValue() + 2) % 4) * 90;
    }

    static void rotY(PoseStack ps, int deg) {
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-deg));
        ps.translate(-0.5, 0, -0.5);
    }

    @Override
    public void render(AnimDoorBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        BlockState s = be.getBlockState();
        if (!(s.getBlock() instanceof HasDoorKind h)) return;
        DoorKind k = h.doorKind();
        float p = Mth.lerp(pt, be.prevProg, be.prog);
        float leaf = k.leafT(p), wheel = k.wheelT(p);
        ps.pushPose();
        if (!k.hatch) {
            boolean right = s.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT;
            rotY(ps, rot(s.getValue(DoorBlock.FACING)));
            float px = right ? 1f - k.pa : k.pa;
            ps.translate(px, 0, k.pz);
            ps.mulPose(Axis.YP.rotationDegrees((right ? 90f : -90f) * leaf));
            ps.translate(-px, 0, -k.pz);
            DecoModels.drawNamed(k.leaf(right), ps, buf, s, light, k.translucent);
            if (k.wheel != null) {
                ps.translate(k.wx, k.wy, k.wz);
                ps.mulPose(Axis.ZP.rotationDegrees(-540f * wheel));
                ps.translate(-k.wx, -k.wy, -k.wz);
                DecoModels.drawNamed(k.wheel, ps, buf, s, light, false);
            }
        } else {
            boolean top = s.getValue(TrapDoorBlock.HALF) == Half.TOP;
            rotY(ps, rot(s.getValue(TrapDoorBlock.FACING).getOpposite()));
            if (top) ps.translate(0, 0.89, 0);
            ps.translate(0, k.pa, k.pz);
            ps.mulPose(Axis.XP.rotationDegrees((top ? 90f : -90f) * leaf));
            ps.translate(0, -k.pa, -k.pz);
            DecoModels.drawNamed(k.leaf(false), ps, buf, s, light, false);
            if (k.wheel != null) {
                ps.translate(k.wx, k.wy, k.wz);
                ps.mulPose(Axis.YP.rotationDegrees(-540f * wheel));
                ps.translate(-k.wx, -k.wy, -k.wz);
                DecoModels.drawNamed(k.wheel, ps, buf, s, light, false);
            }
        }
        ps.popPose();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
