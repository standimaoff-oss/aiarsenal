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
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.level.BlockAndTintGetter
 *  net.minecraft.world.level.block.state.properties.Property
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.missile.LaunchComputerBlock;
import ru.aiforge.realwar.missile.LaunchComputerBlockEntity;
import ru.aiforge.realwar.missile.SirenBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.properties.Property;

public final class MachineGlbRenderers {
    public static final ResourceLocation COMPUTER = new ResourceLocation("aiarsenal", "models/glb/launch_computer.glb");
    public static final ResourceLocation SIREN = new ResourceLocation("aiarsenal", "models/glb/siren.glb");

    private MachineGlbRenderers() {
    }

    public static class Siren
    implements BlockEntityRenderer<SirenBlockEntity> {
        public Siren(BlockEntityRendererProvider.Context c) {
        }

        public void render(SirenBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int ov) {
            GlbAssets.Loaded lm = GlbAssets.get(SIREN);
            if (lm == null || be.getLevel() == null) {
                return;
            }
            int packed = LevelRenderer.getLightColor((BlockAndTintGetter)be.getLevel(), (BlockPos)be.getBlockPos().above(2));
            Pose pose = lm.scratch();
            float a = be.angleO + (be.angle - be.angleO + (be.angle < be.angleO ? 360.0f : 0.0f)) * pt;
            pose.rotate("horns", (float)Math.toRadians(a), 0.0f, 1.0f, 0.0f);
            pose.compute();
            GlbRenderer.render(lm, pose, ps, buf, packed, OverlayTexture.NO_OVERLAY);
        }

        public boolean shouldRenderOffScreen(SirenBlockEntity be) {
            return true;
        }
    }

    public static class Computer
    implements BlockEntityRenderer<LaunchComputerBlockEntity> {
        public Computer(BlockEntityRendererProvider.Context c) {
        }

        public void render(LaunchComputerBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int ov) {
            GlbAssets.Loaded lm = GlbAssets.get(COMPUTER);
            if (lm == null || be.getLevel() == null) {
                return;
            }
            Direction f = (Direction)be.getBlockState().getValue((Property)LaunchComputerBlock.FACING);
            int packed = LevelRenderer.getLightColor((BlockAndTintGetter)be.getLevel(), (BlockPos)be.getBlockPos().above());
            ps.pushPose();
            ps.translate(0.5, 0.0, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(180.0f - f.toYRot()));
            ps.translate(-0.5, 0.0, -0.5);
            GlbRenderer.render(lm, lm.staticPose(), ps, buf, packed, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}

