/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.blaze3d.vertex.VertexConsumer
 *  com.mojang.math.Axis
 *  net.minecraft.client.renderer.LevelRenderer
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.client.renderer.RenderType
 *  net.minecraft.client.renderer.blockentity.BlockEntityRenderer
 *  net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider$Context
 *  net.minecraft.client.renderer.texture.OverlayTexture
 *  net.minecraft.core.BlockPos
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.util.Mth
 *  net.minecraft.world.level.BlockAndTintGetter
 *  net.minecraft.world.level.block.Block
 *  org.joml.Matrix3f
 *  org.joml.Matrix4f
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.missile.LaunchPadBlock;
import ru.aiforge.realwar.missile.LaunchPadBlockEntity;
import ru.aiforge.realwar.missile.MissileType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class LaunchPadRenderer
implements BlockEntityRenderer<LaunchPadBlockEntity> {
    private static final ResourceLocation[] MODELS = new ResourceLocation[]{null, new ResourceLocation("aiarsenal", "models/glb/launch_pad_1.glb"), new ResourceLocation("aiarsenal", "models/glb/launch_pad_2.glb"), new ResourceLocation("aiarsenal", "models/glb/launch_pad_3.glb")};
    static final double[] TX = new double[]{0.0, 0.9, 1.78, 2.68};
    static final double[] TOWER = new double[]{0.0, 3.9, 6.4, 9.6};
    static final double[] PAD_TR = new double[]{0.0, 0.26, 0.45, 0.8};
    static final double TABLE_H = 0.22;
    static final double AWAY = -85.0;

    public LaunchPadRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private static double smooth(double x) {
        x = Mth.clamp((double)x, (double)0.0, (double)1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    public void render(LaunchPadBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        GlbAssets.Loaded ml;
        if (be.getLevel() == null) {
            return;
        }
        Block block = be.getBlockState().getBlock();
        if (!(block instanceof LaunchPadBlock)) {
            return;
        }
        LaunchPadBlock pb = (LaunchPadBlock)block;
        int n = pb.level;
        GlbAssets.Loaded lm = GlbAssets.get(MODELS[n]);
        if (lm == null) {
            return;
        }
        double now = (float)be.getLevel().getGameTime() + partial;
        LaunchPadBlockEntity.State st = be.state();
        double t = now - (double)be.stateStart();
        MissileType kind = be.kind();
        double h = LaunchPadBlock.HEIGHT[n];
        double cx = (double)n / 2.0;
        double tx = TX[n];
        double H = TOWER[n];
        double reach = tx - cx;
        double len = kind == null ? 0.0 : kind.cls.length;
        double mr = kind == null ? 0.0 : kind.cls.radius;
        double gripH = 0.6 * len;
        int packed = LevelRenderer.getLightColor((BlockAndTintGetter)be.getLevel(), (BlockPos)be.getBlockPos().above((int)Math.ceil(h) + 1));
        double yaw = -85.0;
        double umb = -70.0;
        double clampOpen = 1.0;
        boolean holding = false;
        boolean showMissile = false;
        double baseX = cx;
        double baseY = h + 0.22 + 0.02;
        double theta = 0.0;
        double gripLift = 0.0;
        boolean gripOnMissile = false;
        boolean gripFree = false;
        switch (st) {
            case LOADING: {
                showMissile = true;
                yaw = -85.0 * (1.0 - LaunchPadRenderer.smooth(t / 18.0));
                double s1 = LaunchPadRenderer.smooth((t - 18.0) / 80.0);
                double s2 = LaunchPadRenderer.smooth((t - 98.0) / 40.0);
                double Yh = h + 0.22 + 0.7;
                theta = 90.0 * (1.0 - s1);
                baseX = cx + (1.0 - s1) * 0.3;
                baseY = (Yh + (1.0 - s1) * 0.6) * (1.0 - s2) + (h + 0.22 + 0.02) * s2;
                umb = -70.0 * (1.0 - LaunchPadRenderer.smooth((t - 125.0) / 20.0));
                clampOpen = 1.0 - LaunchPadRenderer.smooth((t - 130.0) / 18.0);
                gripOnMissile = true;
                holding = true;
                break;
            }
            case READY: {
                showMissile = true;
                yaw = 0.0;
                umb = 0.0;
                clampOpen = 0.0;
                holding = true;
                gripOnMissile = true;
                break;
            }
            case PREP: {
                showMissile = true;
                double lift = LaunchPadRenderer.smooth(t / 22.0);
                gripOnMissile = t < 22.0;
                gripLift = lift;
                yaw = -85.0 * LaunchPadRenderer.smooth((t - 18.0) / 24.0);
                umb = -70.0 * LaunchPadRenderer.smooth((t - 4.0) / 24.0);
                clampOpen = LaunchPadRenderer.smooth((t - 36.0) / 18.0);
                holding = true;
                gripFree = true;
                break;
            }
            case LAUNCH: {
                yaw = -85.0;
                umb = -70.0;
                clampOpen = 1.0;
                break;
            }
        }
        Pose pose = lm.scratch();
        for (int k = 0; k < 40; ++k) {
            pose.hide("cable_" + k, true);
        }
        pose.rotate("crane_turret", (float)Math.toRadians(yaw), 0.0f, 1.0f, 0.0f);
        pose.rotate("umb", (float)Math.toRadians(umb), 0.0f, 1.0f, 0.0f);
        double inward = holding ? Math.max(0.0, PAD_TR[n] - mr) : 0.0;
        for (int k = 0; k < 4; ++k) {
            double a = Math.toRadians(45 + 90 * k);
            double d = clampOpen * 0.16 - (1.0 - clampOpen) * inward;
            pose.translate("clamp_" + k, (float)(Math.cos(a) * d), 0.0f, (float)(Math.sin(a) * d));
        }
        double ya = Math.toRadians(yaw);
        double tipX = tx + Math.cos(ya) * -reach;
        double tipZ = cx - Math.sin(ya) * -reach;
        double tipY = h + H + 0.08;
        boolean showGrip = showMissile && (st == LaunchPadBlockEntity.State.LOADING || st == LaunchPadBlockEntity.State.READY || st == LaunchPadBlockEntity.State.PREP);
        double gx = 0.0;
        double gy = 0.0;
        double gz = cx;
        double sinT = Math.sin(Math.toRadians(theta));
        double cosT = Math.cos(Math.toRadians(theta));
        if (showGrip) {
            if (st == LaunchPadBlockEntity.State.PREP && gripLift > 0.0) {
                double baseGy = baseY + gripH;
                double topY = tipY - 0.45;
                gy = Mth.lerp((double)gripLift, (double)baseGy, (double)topY);
                gx = Mth.lerp((double)LaunchPadRenderer.smooth((t - 18.0) / 24.0), (double)cx, (double)tipX);
                gz = Mth.lerp((double)LaunchPadRenderer.smooth((t - 18.0) / 24.0), (double)cx, (double)tipZ);
                if (t > 18.0) {
                    gx = tipX;
                    gz = tipZ;
                    gy = topY;
                }
            } else {
                gx = baseX - gripH * sinT;
                gy = baseY + gripH * cosT;
                gz = cx;
            }
            pose.translate("grip", (float)(gx - cx), (float)(gy - h), (float)(gz - cx));
            if (st == LaunchPadBlockEntity.State.LOADING && theta > 0.01) {
                pose.rotate("grip", (float)Math.toRadians(theta), 0.0f, 0.0f, 1.0f);
            }
        } else {
            pose.hide("grip", true);
        }
        if (st == LaunchPadBlockEntity.State.PREP && t > 30.0) {
            pose.hide("grip", false);
        }
        pose.compute();
        ps.pushPose();
        GlbRenderer.render(lm, pose, ps, buf, packed, OverlayTexture.NO_OVERLAY);
        if (showMissile && kind != null && (ml = GlbAssets.get(kind.model())) != null) {
            ps.pushPose();
            ps.translate(baseX, baseY, cx);
            ps.mulPose(Axis.ZP.rotationDegrees((float)theta));
            ps.translate(0.0, len / 2.0, 0.0);
            ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
            GlbRenderer.render(ml, ml.staticPose(), ps, buf, packed, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        if (showGrip) {
            double hz;
            double hy;
            double hx;
            VertexConsumer vc = buf.getBuffer(RenderType.lines());
            Matrix4f m = ps.last().pose();
            Matrix3f nm = ps.last().normal();
            double hookY = 0.29;
            if (st == LaunchPadBlockEntity.State.PREP && gripLift > 0.0) {
                hx = gx;
                hy = gy + hookY;
                hz = gz;
            } else {
                hx = gx - hookY * sinT;
                hy = gy + hookY * cosT;
                hz = gz;
            }
            LaunchPadRenderer.line(vc, m, nm, tipX, tipY, tipZ, hx, hy, hz);
            double ar = PAD_TR[n] + 0.08;
            double[][] ears = new double[][]{{ar, 0.22, 0.0}, {-ar, 0.22, 0.0}, {0.0, 0.22, ar}, {0.0, 0.22, -ar}};
            double ct = st == LaunchPadBlockEntity.State.PREP && gripLift > 0.0 ? 1.0 : cosT;
            double stt = st == LaunchPadBlockEntity.State.PREP && gripLift > 0.0 ? 0.0 : sinT;
            for (double[] e : ears) {
                double lx = e[0] * ct - e[1] * stt;
                double ly = e[0] * stt + e[1] * ct;
                LaunchPadRenderer.line(vc, m, nm, hx, hy, hz, gx + lx, gy + ly, gz + e[2]);
            }
        }
        ps.popPose();
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f nm, double x0, double y0, double z0, double x1, double y1, double z1) {
        float dx = (float)(x1 - x0);
        float dy = (float)(y1 - y0);
        float dz = (float)(z1 - z0);
        float l = Mth.sqrt((float)(dx * dx + dy * dy + dz * dz));
        if (l < 1.0E-4f) {
            return;
        }
        vc.vertex(m, (float)x0, (float)y0, (float)z0).color(20, 20, 20, 255).normal(nm, dx /= l, dy /= l, dz /= l).endVertex();
        vc.vertex(m, (float)x1, (float)y1, (float)z1).color(20, 20, 20, 255).normal(nm, dx, dy, dz).endVertex();
    }

    public boolean shouldRenderOffScreen(LaunchPadBlockEntity be) {
        return true;
    }

    public int getViewDistance() {
        return 160;
    }
}

