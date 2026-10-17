/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.math.Axis
 *  net.minecraft.client.renderer.LevelRenderer
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.client.renderer.blockentity.BlockEntityRenderer
 *  net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider$Context
 *  net.minecraft.client.renderer.texture.OverlayTexture
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.world.level.BlockAndTintGetter
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.properties.Property
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ru.aiforge.realwar.block.BombBlock;
import ru.aiforge.realwar.blockentity.BombBlockEntity;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class BombRenderer
implements BlockEntityRenderer<BombBlockEntity> {
    public BombRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    public void render(BombBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        BombBlock bb;
        BlockState st;
        block5: {
            block4: {
                st = be.getBlockState();
                Block block = st.getBlock();
                if (!(block instanceof BombBlock)) break block4;
                bb = (BombBlock)block;
                if (be.getLevel() != null) break block5;
            }
            return;
        }
        GlbAssets.Loaded lm = GlbAssets.get(bb.type.modelPath());
        if (lm == null) {
            return;
        }
        Direction f = (Direction)st.getValue((Property)BombBlock.FACING);
        Direction nose = f.getOpposite();
        int packed = LevelRenderer.getLightColor((BlockAndTintGetter)be.getLevel(), (BlockPos)be.getBlockPos().relative(f));
        ps.pushPose();
        ps.translate(0.5 + (double)f.getStepX(), 0.5, 0.5 + (double)f.getStepZ());
        ps.mulPose(Axis.YP.rotationDegrees(-nose.toYRot()));
        GlbRenderer.render(lm, lm.staticPose(), ps, buf, packed, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    public boolean shouldRenderOffScreen(BombBlockEntity be) {
        return true;
    }

    public int getViewDistance() {
        return 128;
    }
}

