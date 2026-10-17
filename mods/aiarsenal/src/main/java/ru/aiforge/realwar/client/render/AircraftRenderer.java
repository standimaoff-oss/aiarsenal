/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.math.Axis
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.client.renderer.entity.EntityRenderer
 *  net.minecraft.client.renderer.entity.EntityRendererProvider$Context
 *  net.minecraft.client.renderer.texture.OverlayTexture
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ru.aiforge.realwar.aircraft.AirBombType;
import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.aircraft.BayLayout;
import ru.aiforge.realwar.aircraft.Tu95Spec;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.registry.BombType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class AircraftRenderer
extends EntityRenderer<AircraftEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    public static final ResourceLocation MODEL = new ResourceLocation("aiarsenal", "models/glb/tu95.glb");
    private static final String[] PROPS = new String[]{"l0", "l1", "r0", "r1"};

    public AircraftRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    private static float rad(double d) {
        return (float)Math.toRadians(d);
    }

    public void render(AircraftEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        int k;
        GlbAssets.Loaded lm = GlbAssets.get(MODEL);
        if (lm == null) {
            return;
        }
        Pose pose = lm.scratch();
        pose.reset();
        float roll = Mth.lerp((float)pt, (float)e.rollVisO, (float)e.rollVis);
        float pitch = Mth.lerp((float)pt, (float)e.pitchVisO, (float)e.pitchVis);
        float prop = Mth.lerp((float)pt, (float)e.propAngleO, (float)e.propAngle);
        float gear = Mth.lerp((float)pt, (float)e.gearAnimO, (float)e.gearAnim);
        float bay = Mth.lerp((float)pt, (float)e.bayAnimO, (float)e.bayAnim);
        float door = Mth.lerp((float)pt, (float)e.doorAnimO, (float)e.doorAnim);
        float flap = Mth.lerp((float)pt, (float)e.flapAnimO, (float)e.flapAnim);
        float inRoll = ((Float)e.getEntityData().get(AircraftEntity.IN_ROLL)).floatValue();
        float inPitch = ((Float)e.getEntityData().get(AircraftEntity.IN_PITCH)).floatValue();
        float inRud = ((Float)e.getEntityData().get(AircraftEntity.IN_RUDDER)).floatValue();
        float thr = e.throttle();
        float spd = e.speed();
        for (String t : PROPS) {
            pose.rotate("prop_" + t + "_a", prop, 0.0f, 0.0f, 1.0f);
            pose.rotate("prop_" + t + "_b", -prop * 1.0f, 0.0f, 0.0f, 1.0f);
        }
        float retract = (1.0f - gear) * AircraftRenderer.rad(90.0);
        pose.rotate("gear_ml", retract, 1.0f, 0.0f, 0.0f);
        pose.rotate("gear_mr", retract, 1.0f, 0.0f, 0.0f);
        pose.rotate("gear_nose", -retract, 1.0f, 0.0f, 0.0f);
        float wheel = (float)((double)((float)e.tickCount + pt) * 0.05 * (double)spd);
        for (k = 0; k < 4; ++k) {
            pose.rotate("wh_ml_" + k, wheel / 0.75f, 1.0f, 0.0f, 0.0f);
            pose.rotate("wh_mr_" + k, wheel / 0.75f, 1.0f, 0.0f, 0.0f);
        }
        for (k = 0; k < 2; ++k) {
            pose.rotate("wh_nose_" + k, wheel / 0.55f, 1.0f, 0.0f, 0.0f);
        }
        pose.rotate("flap_l", flap * AircraftRenderer.rad(32.0), Tu95Spec.FLAP_AXIS_L[0], Tu95Spec.FLAP_AXIS_L[1], Tu95Spec.FLAP_AXIS_L[2]);
        pose.rotate("flap_r", flap * AircraftRenderer.rad(32.0), Tu95Spec.FLAP_AXIS_R[0], Tu95Spec.FLAP_AXIS_R[1], Tu95Spec.FLAP_AXIS_R[2]);
        pose.rotate("ail_l", inRoll * AircraftRenderer.rad(18.0), Tu95Spec.AIL_AXIS_L[0], Tu95Spec.AIL_AXIS_L[1], Tu95Spec.AIL_AXIS_L[2]);
        pose.rotate("ail_r", -inRoll * AircraftRenderer.rad(18.0), Tu95Spec.AIL_AXIS_R[0], Tu95Spec.AIL_AXIS_R[1], Tu95Spec.AIL_AXIS_R[2]);
        pose.rotate("elev_l", -inPitch * AircraftRenderer.rad(22.0), Tu95Spec.ELEV_AXIS_L[0], Tu95Spec.ELEV_AXIS_L[1], Tu95Spec.ELEV_AXIS_L[2]);
        pose.rotate("elev_r", -inPitch * AircraftRenderer.rad(22.0), Tu95Spec.ELEV_AXIS_R[0], Tu95Spec.ELEV_AXIS_R[1], Tu95Spec.ELEV_AXIS_R[2]);
        pose.rotate("rudder", inRud * AircraftRenderer.rad(24.0), Tu95Spec.RUDDER_AXIS[0], Tu95Spec.RUDDER_AXIS[1], Tu95Spec.RUDDER_AXIS[2]);
        pose.rotate("bay_door_l", bay * AircraftRenderer.rad(72.0), 0.0f, 0.0f, 1.0f);
        pose.rotate("bay_door_r", -bay * AircraftRenderer.rad(72.0), 0.0f, 0.0f, 1.0f);
        pose.rotate("door", door * AircraftRenderer.rad(100.0), 0.0f, 1.0f, 0.0f);
        pose.rotate("yoke_l", inRoll * AircraftRenderer.rad(70.0), 0.0f, 0.0f, 1.0f);
        pose.rotate("yoke_r", inRoll * AircraftRenderer.rad(70.0), 0.0f, 0.0f, 1.0f);
        float pull = -inPitch * 0.08f;
        pose.translate("yoke_l", 0.0f, 0.0f, pull);
        pose.translate("yoke_r", 0.0f, 0.0f, pull);
        for (int k2 = 0; k2 < 4; ++k2) {
            pose.rotate("thr_" + k2, (thr - 0.5f) * AircraftRenderer.rad(50.0), 1.0f, 0.0f, 0.0f);
        }
        float[] ax = Tu95Spec.NEEDLE_AXIS;
        pose.rotate("n_speed", -Mth.clamp((float)(spd / 55.0f), (float)0.0f, (float)1.0f) * AircraftRenderer.rad(300.0), ax[0], ax[1], ax[2]);
        pose.rotate("n_alt", -Mth.clamp((float)((float)e.altitudeAboveGround() / 400.0f), (float)0.0f, (float)1.0f) * AircraftRenderer.rad(340.0), ax[0], ax[1], ax[2]);
        pose.rotate("n_vsi", -Mth.clamp((float)(e.pitch() / 30.0f), (float)-1.0f, (float)1.0f) * AircraftRenderer.rad(120.0), ax[0], ax[1], ax[2]);
        pose.rotate("n_fuel", -AircraftRenderer.rad(150.0), ax[0], ax[1], ax[2]);
        for (int k3 = 0; k3 < 4; ++k3) {
            pose.rotate("n_rpm" + k3, -(e.engineOn() ? 0.3f + 0.7f * thr : 0.0f) * AircraftRenderer.rad(270.0), ax[0], ax[1], ax[2]);
        }
        pose.compute();
        ps.pushPose();
        float ry = Mth.rotLerp((float)pt, (float)e.yRotO, (float)e.getYRot());
        ps.mulPose(Axis.YP.rotationDegrees(-ry));
        ps.translate(0.0f, 3.9f, 0.0f);
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.translate(0.0f, -3.9f, 0.0f);
        GlbRenderer.render(lm, pose, ps, buf, light, OverlayTexture.NO_OVERLAY);
        int[] c = e.counts();
        for (BayLayout.Slot s : BayLayout.slots(c)) {
            ResourceLocation mp = s.kind() == 0 ? BombType.RDS_4.modelPath() : AirBombType.values()[s.kind() == 1 ? 2 : (s.kind() == 2 ? 1 : 0)].model();
            GlbAssets.Loaded bm = GlbAssets.get(mp);
            if (bm == null) continue;
            Pose bp = bm.staticPose();
            if (s.kind() == 0) {
                bp = bm.scratch();
                bp.reset();
                bp.hide("cradle", true);
                bp.compute();
            }
            ps.pushPose();
            ps.translate(s.x(), s.y(), s.z());
            GlbRenderer.render(bm, bp, ps, buf, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    public ResourceLocation getTextureLocation(AircraftEntity e) {
        return WHITE;
    }
}

