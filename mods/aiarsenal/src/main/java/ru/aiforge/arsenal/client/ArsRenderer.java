package ru.aiforge.arsenal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Nodes;
import ru.aiforge.arsenal.entity.ArsEntity;
import ru.aiforge.arsenal.entity.FpvDrone;
import ru.aiforge.arsenal.entity.IcbmEntity;
import ru.aiforge.arsenal.entity.LauncherEntity;
import ru.aiforge.arsenal.entity.MissileEntity;
import ru.aiforge.arsenal.entity.NukeEntity;
import ru.aiforge.arsenal.entity.ReconDrone;
import ru.aiforge.arsenal.entity.ShahedDrone;
import ru.aiforge.tubombers.client.glb.GlbRenderer;
import ru.aiforge.tubombers.glb.GlbModel;
import ru.aiforge.tubombers.glb.GlbModels;
import ru.aiforge.tubombers.glb.GlbPose;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders every arsenal entity from its GLB: root isolation, spinning props, gear / wing deploy / erector clips,
 * stage separation, stores, boosters, parachute, drones sitting on the launch rail.
 */
public class ArsRenderer<T extends ArsEntity> extends EntityRenderer<T> {
    private static final float[] WHITE = {1, 1, 1, 1};
    private final Map<String, GlbPose> poses = new HashMap<>();

    public ArsRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.5f;
    }

    private GlbPose pose(GlbModel m) {
        return poses.computeIfAbsent(m.name, k -> new GlbPose(m));
    }

    private static float clip(GlbModel m, String name, float progress) {
        GlbModel.Animation a = m.anim(name);
        return a == null ? 0 : a.duration * Mth.clamp(progress, 0, 1);
    }

    @Override
    public void render(T e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Kind k = e.kind();
        GlbModel m = GlbModels.get(k.glb);
        GlbPose p = pose(m);
        p.reset();
        Nodes.isolate(p, k.root);
        float t = (e.tickCount + pt) / 20f;
        float zShift = 0f;
        switch (k.cat) {
            case RECON -> {
                ReconDrone d = (ReconDrone) e;
                String id = k == Kind.MQ9 ? "mq9" : "orion";
                if (m.anim(id + "_gear") != null) p.apply(id + "_gear", clip(m, id + "_gear", d.gear()), false, 1f);
                if (d.state() != ReconDrone.PARKED) p.rotate(m.node(k.root + "_Prop"), t * 2f * (float) Math.PI * 28f, 0, 0, 1);
                if (ArsClient.sessionEntity() == e) {
                    p.rotate(m.node(k.root + "_BallYaw"), (float) Math.toRadians(-(Mth.wrapDegrees(ArsClient.camYaw() - e.getYRot()))), 0, 1, 0);
                    p.rotate(m.node(k.root + "_BallPitch"), (float) Math.toRadians(ArsClient.camPitch()), 1, 0, 0);
                }
                int fired = d.maxAmmo() - d.ammo();
                int[] order = {1, 3, 0, 2};
                int per = k == Kind.MQ9 ? 2 : 1;
                for (int i = 0; i < 4; i++) p.setVisible(m.node(k.root + "_Store" + order[i]), fired < (i + 1) * per);
            }
            case SHAHED -> {
                ShahedDrone d = (ShahedDrone) e;
                p.setVisible(m.node(k.root + "_Booster"), d.boosterAttached());
                if (d.engineRunning() && k != Kind.S238) p.rotate(m.node(k.root + "_Prop"), t * 2f * (float) Math.PI * 45f, 0, 0, 1);
            }
            case FPV -> {
                FpvDrone d = (FpvDrone) e;
                float spin = d.state() == FpvDrone.IDLE ? 0f : t * 2f * (float) Math.PI * (30f + 60f * d.throttleSynced());
                for (int i = 1; i <= 4; i++) p.rotate(m.node(k.root + "_Prop" + i), (i % 2 == 0 ? spin : -spin), 0, 1, 0);
            }
            case MISSILE -> {
                MissileEntity d = (MissileEntity) e;
                String c = k.id + "_deploy";
                if (m.anim(c) != null) p.apply(c, clip(m, c, d.deploy()), false, 1f);
                p.setVisible(m.node(k.root + "_Booster"), d.booster());
            }
            case ICBM -> {
                int a = e.aux();
                p.setVisible(m.node("YARS_S1"), (a & IcbmEntity.B_S1) == 0);
                p.setVisible(m.node("YARS_S2"), (a & IcbmEntity.B_S2) == 0);
                p.setVisible(m.node("YARS_S3"), (a & IcbmEntity.B_S3) == 0);
                if (e.state() == IcbmEntity.REENTRY) {
                    p.setVisible(m.node("YARS_Bus"), false);
                    zShift = -10.2f;
                }
            }
            case NUKE -> {
                if (k == Kind.AN602)
                    p.setVisible(m.node("AN602_Chute"), e.state() == NukeEntity.FALLING && e.tickCount > 30);
                if (k == Kind.YARS) {
                    for (String n : new String[]{"YARS_S1", "YARS_S2", "YARS_S3", "YARS_Bus"}) p.setVisible(m.node(n), false);
                    zShift = -10.2f;
                }
            }
            case LAUNCHER -> {
                LauncherEntity l = (LauncherEntity) e;
                if (k == Kind.CRUISE_LAUNCHER) {
                    p.apply("launcher_erect", clip(m, "launcher_erect", l.erect()), false, 1f);
                    p.apply("launcher_lids", clip(m, "launcher_lids", l.lids()), false, 1f);
                } else if (k == Kind.YARS_TEL) {
                    // outriggers go down during the first part of the erect cycle, then the TPK rises
                    if (m.anim("yarstel_jacks") != null)
                        p.apply("yarstel_jacks", clip(m, "yarstel_jacks", Math.min(1f, l.erect() * 6f)), false, 1f);
                    p.apply("yarstel_erect", clip(m, "yarstel_erect", l.erect()), false, 1f);
                    p.setVisible(m.node("YARSTEL_Cap"), l.lids() < 1f);
                }
            }
            default -> {
            }
        }
        p.computeGlobals();
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float pitch = -Mth.lerp(pt, e.xRotO, e.getXRot());
        float roll = Mth.lerp(pt, e.rollO, e.roll());
        ps.pushPose();
        ps.translate(0, k.lift(), 0);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        if (zShift != 0) ps.translate(0, 0, zShift);
        GlbRenderer.of(m).render(p, ps, buf, light, OverlayTexture.NO_OVERLAY, WHITE, null, false);
        ps.popPose();
        // drone waiting on the launch rail
        if (e instanceof LauncherEntity l && k == Kind.RAIL && l.ammoKind() != null) {
            Kind a = l.ammoKind();
            GlbModel sm = GlbModels.get(a.glb);
            GlbPose sp = pose(sm);
            sp.reset();
            Nodes.isolate(sp, a.root);
            sp.computeGlobals();
            Vector3f seat = Nodes.node(Kind.RAIL, "RAIL_Seat", null, 0, null);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(seat.x, seat.y + a.lift() * 0.9f, seat.z);
            ps.mulPose(Axis.XP.rotationDegrees(-15f));
            GlbRenderer.of(sm).render(sp, ps, buf, light, OverlayTexture.NO_OVERLAY, WHITE, null, false);
            ps.popPose();
        }
        super.render(e, entityYaw, pt, ps, buf, light);
    }

    @Override
    public boolean shouldRender(T e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.distanceToSqr(x, y, z) < 600 * 600 && (f.isVisible(e.getBoundingBox().inflate(12)) || e.kind().cat == Kind.Cat.ICBM);
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    }
}
