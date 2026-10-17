package ru.aiforge.realwar.client.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/** Small helpers for additive glow geometry. */
final class Glow {
    private Glow() {
    }

    /** Camera-facing soft disc (8 triangles as quads) at the current pose origin. */
    static void halo(PoseStack ps, VertexConsumer vc, float r, int cr, int cg, int cb, int a) {
        ps.pushPose();
        ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        Matrix4f m = ps.last().pose();
        int n = 12;
        for (int i = 0; i < n; ++i) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            vc.vertex(m, 0, 0, 0).color(cr, cg, cb, a).endVertex();
            vc.vertex(m, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, 0).color(cr, cg, cb, 0).endVertex();
            vc.vertex(m, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, 0).color(cr, cg, cb, 0).endVertex();
            vc.vertex(m, 0, 0, 0).color(cr, cg, cb, a).endVertex();
        }
        ps.popPose();
    }

    /** Cone along local -Z from radius r0 at 0 to r1 at len; alpha a0 -> 0 with (1-s)^1.6 falloff, 4 length slices. */
    static void cone(Matrix4f m, VertexConsumer vc, float r0, float r1, float len, int cr, int cg, int cb, int a0, int seg) {
        int sl = 5;
        for (int k = 0; k < sl; ++k) {
            float s0 = (float) k / sl, s1 = (float) (k + 1) / sl;
            float z0 = -len * s0, z1 = -len * s1;
            float ra = r0 + (r1 - r0) * s0, rb = r0 + (r1 - r0) * s1;
            int aa = (int) (a0 * Math.pow(1 - s0, 1.6)), ab = (int) (a0 * Math.pow(1 - s1, 1.6));
            for (int i = 0; i < seg; ++i) {
                double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 1) / seg;
                float c0 = (float) Math.cos(t0), n0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), n1 = (float) Math.sin(t1);
                vc.vertex(m, c0 * ra, n0 * ra, z0).color(cr, cg, cb, aa).endVertex();
                vc.vertex(m, c1 * ra, n1 * ra, z0).color(cr, cg, cb, aa).endVertex();
                vc.vertex(m, c1 * rb, n1 * rb, z1).color(cr, cg, cb, ab).endVertex();
                vc.vertex(m, c0 * rb, n0 * rb, z1).color(cr, cg, cb, ab).endVertex();
            }
        }
    }
}
