package ru.aiforge.bm21grad.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.client.Bm21ClientState;
import ru.aiforge.bm21grad.client.glb.GlbRenderer;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.glb.GlbModel;
import ru.aiforge.bm21grad.glb.GlbPose;

/**
 * Renders the BM-21 GLB. Every moving part is driven by the animation clips stored in the GLB
 * (wheels_roll, steer, turret_traverse, launcher_elevate, shields_lower, fire_recoil, engine_idle);
 * rocket visibility / loading slide are node overrides on top.
 */
public class Bm21Renderer extends EntityRenderer<Bm21Entity> {
    private static final float[] NORMAL = {1, 1, 1, 1};
    private static final float[] BURNT = {0.2f, 0.19f, 0.17f, 1};
    private int[] rocketNodes;

    public Bm21Renderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 2.6f;
        shadowStrength = 0.8f;
    }

    public static void poseVehicle(GlbPose pose, Bm21Entity e, Bm21ClientState st, float pt, int[] rocketNodes) {
        pose.reset();
        double wheel = Mth.lerp(pt, st.prevWheel, st.wheel);
        pose.apply("wheels_roll", (float) (wheel - Math.floor(wheel)), true, 1f);
        float steer = Mth.lerp(pt, st.prevSteerVis, st.steerVis);
        pose.apply("steer", 1f - steer, false, 1f);
        float turret = e.prevTurret + Mth.wrapDegrees(e.getTurret() - e.prevTurret) * pt;
        float elev = Mth.lerp(pt, e.prevElev, e.getElevation());
        Bm21Entity.poseLauncher(pose, turret, elev);
        pose.apply("shields_lower", Mth.lerp(pt, e.prevShieldProgress, e.shieldProgress), false, 1f);
        if (e.hasState(Bm21Entity.ST_ENGINE)) pose.apply("engine_idle", (st.age + pt) / 20f, true, 1f);
        if (e.recoilTicks < 10) pose.apply("fire_recoil", (e.recoilTicks + pt) / 20f, false, 1f);
        int loading = e.getLoadingTube();
        float prog = e.getLoadingProgress();
        for (int i = 0; i < Bm21Entity.TUBES; i++) {
            int n = rocketNodes[i];
            if (n < 0) continue;
            boolean loaded = e.tube(i) != RocketType.NONE;
            if (i == loading && !loaded) {
                pose.setVisible(n, prog > 0.02f);
                pose.translate(n, 0, 0, -(1f - prog) * 2.9f);
            } else pose.setVisible(n, loaded);
        }
        pose.computeGlobals();
    }

    @Override
    public void render(Bm21Entity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        GlbModel m = Bm21Entity.model();
        if (rocketNodes == null) {
            rocketNodes = new int[Bm21Entity.TUBES];
            for (int i = 0; i < Bm21Entity.TUBES; i++) rocketNodes[i] = Bm21Entity.rocketNode(m, i);
        }
        Bm21ClientState st = Bm21ClientState.of(e);
        poseVehicle(st.pose, e, st, pt, rocketNodes);
        ps.pushPose();
        float bodyYaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        // terrain following: rotate about the middle axle
        ps.translate(0, 0.6, -1.075);
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, st.prevPitch, st.pitch)));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, st.prevRoll, st.roll)));
        ps.translate(0, -0.6, 1.075);
        GlbRenderer.of(m).render(st.pose, ps, buf, light, OverlayTexture.NO_OVERLAY,
                e.isDestroyed() ? BURNT : NORMAL, null, e.hasState(Bm21Entity.ST_LIGHTS));
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Bm21Entity e) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    }
}
