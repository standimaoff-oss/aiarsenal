package ru.aiforge.realwar.client.craft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.craft.BattleshipEntity;
import ru.aiforge.realwar.craft.BoatEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.craft.CraftWeapon;
import ru.aiforge.realwar.craft.HeliEntity;
import ru.aiforge.realwar.craft.PlaneEntity;
import ru.aiforge.realwar.glb.Pose;

/** GLB renderer with full yaw / pitch / roll and animated rotors, turrets, gear, rudders, hydroplanes. */
public class CraftRenderer<T extends CraftEntity> extends EntityRenderer<T> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

    public CraftRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.0f;
    }

    @Override
    public void render(T e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        GlbAssets.Loaded lm = GlbAssets.get(new ResourceLocation("aiarsenal", e.kind.model));
        if (lm == null) return;
        if (e.kind == CraftKind.BISMARCK && lm.lodDist() != 150.0) lm.setLodDistance(150.0);
        Pose pose = lm.scratch();
        applyPose(e, pose, pt);
        ru.aiforge.realwar.client.render.DamageRender.craft(e, pose);
        pose.compute();
        ps.pushPose();
        float py = e.pivotY();
        ps.translate(0, py, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(-e.pitchLerp(pt)));
        ps.mulPose(Axis.ZP.rotationDegrees(e.rollLerp(pt)));
        ps.translate(0, -py, 0);
        GlbRenderer.TINT = e.dmg.dead() ? 0.3f : 1f;
        GlbRenderer.render(lm, pose, ps, buf, light, OverlayTexture.NO_OVERLAY);
        GlbRenderer.TINT = 1f;
        ru.aiforge.realwar.client.render.DamageRender.holes(e.dmg, ps, buf, light);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    static void applyPose(CraftEntity e, Pose pose, float pt) {
        float rot = (float) Math.toRadians(e.rotorAngleO + Mth.wrapDegrees(e.rotorAngle - e.rotorAngleO) * pt);
        float om = Mth.lerp(pt, e.auxO, e.aux);
        float gy = (float) Math.toRadians(Mth.lerp(pt, e.gunYawO, e.gunYawC));
        float gp = (float) Math.toRadians(Mth.lerp(pt, e.gunPitchO, e.gunPitchC));
        if (e instanceof HeliEntity h) {
            boolean blur = om > 0.62f;
            if (h.kind == CraftKind.KA52) {
                pose.rotate("rotor_upper", rot, 0, 1, 0);
                pose.rotate("rotor_lower", -rot, 0, 1, 0);
            } else {
                pose.rotate("rotor_main", rot, 0, 1, 0);
                pose.rotate("rotor_tail", rot * 4.6f, 1, 0, 0);
            }
            pose.hide("rotor_disc", !blur);
            pose.hide("rotor_disc2", !blur);
            pose.hide("tail_disc", !blur);
            pose.rotate("gun_yaw", gy, 0, 1, 0);
            pose.rotate("gun_pitch", -gp, 1, 0, 0);
            float gear = Mth.lerp(pt, h.gearPosO, h.gearPos);
            float ret = (float) Math.toRadians((1 - gear) * 95);
            pose.rotate("gear_nose", ret, 1, 0, 0);
            pose.rotate("gear_L", ret, 1, 0, 0);
            pose.rotate("gear_R", ret, 1, 0, 0);
            pose.rotate("tads", gy * 0.9f, 0, 1, 0);
        } else if (e instanceof PlaneEntity pl) {
            posePlane(pl, pose, pt, rot, om);
        } else if (e instanceof BoatEntity b) {
            float rud = (float) Math.toRadians(Mth.lerp(pt, b.rudderVisO, b.rudderVis) * 30);
            pose.rotate("rudder", -rud, 0, 1, 0);
            pose.rotate("rudder_L", -rud, 0, 1, 0);
            pose.rotate("rudder_R", -rud, 0, 1, 0);
            pose.rotate("jet_L", -rud * 0.8f, 0, 1, 0);
            pose.rotate("jet_R", -rud * 0.8f, 0, 1, 0);
            pose.rotate("prop", rot, 0, 0, 1);
            pose.rotate("prop_L", rot, 0, 0, 1);
            pose.rotate("prop_R", -rot, 0, 0, 1);
            pose.rotate("turret_yaw", gy, 0, 1, 0);
            pose.rotate("turret_pitch", -gp, 1, 0, 0);
            float t = (e.tickCount + pt) * 0.12f;
            pose.rotate("radar", t, 0, 1, 0);
            if (b instanceof BattleshipEntity bs) poseBattleship(bs, pose, pt);
            if (b.kind.isSub()) {
                float planes = (float) Math.toRadians(Mth.clamp(b.rateQ / 2.0f, -1f, 1f) * 20f);
                pose.rotate("planes_bow", -planes, 1, 0, 0);
                pose.rotate("planes_stern", planes, 1, 0, 0);
                boolean up = b.flag(CraftEntity.FLAG_PERISCOPE);
                pose.translate("periscope", 0, up ? 2.6f : 0f, 0);
                pose.translate("snorkel", 0, b.engineOn() && b.subFrac > 0.97f ? 2.2f : 0f, 0);
            }
        }
        // expended missiles disappear from the launchers
        for (int li = 0; li < e.kind.loads.length; li++) {
            CraftKind.Load l = e.kind.loads[li];
            if (l.nodePrefix() == null) continue;
            int left = e.ammo(li);
            for (int i = 0; i < l.full(); i++) pose.hide(l.nodePrefix() + i, i >= left);
        }
    }

    /** WW2 aeroplanes: propellers (blur disc above 30 % rpm), gear, flaps, control surfaces, bomb bay, gun turrets. */
    static void posePlane(PlaneEntity e, Pose pose, float pt, float rot, float om) {
        CraftKind.Plane p = e.spec();
        boolean blur = om > 0.3f;
        if (p.engines() == 1) {
            pose.rotate("prop", rot, 0, 0, 1);
            pose.hide("prop_disc", !blur);
        } else {
            for (int i = 0; i < p.engines(); i++) {
                pose.rotate("prop_" + i, rot + i * 0.7f, 0, 0, 1);
                pose.hide("prop_disc_" + i, !blur);
            }
        }
        float gear = Mth.lerp(pt, e.gearPosO, e.gearPos);
        float ret = (float) Math.toRadians((1 - gear) * 88);
        if (p.gearFold() == 0) {
            pose.rotate("gear_L", ret, 1, 0, 0);
            pose.rotate("gear_R", ret, 1, 0, 0);
        } else {
            pose.rotate("gear_L", p.gearFold() * ret, 0, 0, 1);
            pose.rotate("gear_R", -p.gearFold() * ret, 0, 0, 1);
        }
        if (p.tailRetract()) pose.rotate("gear_T", ret, 1, 0, 0);
        float flap = (float) Math.toRadians(Mth.lerp(pt, e.flapPosO, e.flapPos) * 40);
        pose.rotate("flap_L", -flap, 1, 0, 0);
        pose.rotate("flap_R", -flap, 1, 0, 0);
        float ail = (float) Math.toRadians(Mth.lerp(pt, e.ailVisO, e.ailVis) * 18);
        pose.rotate("ail_L", -ail, 1, 0, 0);
        pose.rotate("ail_R", ail, 1, 0, 0);
        pose.rotate("elev", (float) Math.toRadians(Mth.lerp(pt, e.elevVisO, e.elevVis) * 22), 1, 0, 0);
        pose.rotate("rud", (float) Math.toRadians(Mth.lerp(pt, e.rudVisO, e.rudVis) * 25), 0, 1, 0);
        float bay = (float) Math.toRadians(Mth.lerp(pt, e.bayPosO, e.bayPos) * 85);
        pose.rotate("bay_L", bay, 0, 0, 1);
        pose.rotate("bay_R", -bay, 0, 0, 1);
        for (int i = 0; i < p.turretCount(); i++) {
            float az = e.turAzO[i] + Mth.wrapDegrees(e.turAz[i] - e.turAzO[i]) * pt;
            float el = Mth.lerp(pt, e.turElO[i], e.turEl[i]);
            pose.rotate("tur_" + i + "_yaw", (float) Math.toRadians(az), 0, 1, 0);
            pose.rotate("tur_" + i + "_pitch", (float) Math.toRadians(-el), 1, 0, 0);
        }
        if (p.jet()) poseJet(e, pose, pt, ret);
    }

    /**
     * r12 Su-57: nose gear folds forward, canted all-moving fins about their own axes, LEVCONs, thrust-vectoring
     * nozzles (pitch with the stick, yaw with the pedals - stronger in manoeuvre mode), afterburner flames,
     * S-8 pods hung per preset (one per 20 rockets).
     */
    static void poseJet(PlaneEntity e, Pose pose, float pt, float ret) {
        pose.rotate("gear_N", -ret, 1, 0, 0);
        boolean up = ret > (float) Math.toRadians(86);       // fully retracted: legs stowed, flush doors closed
        pose.hide("gear_N", up);
        pose.hide("gear_L", up);
        pose.hide("gear_R", up);
        pose.hide("gear_door_closed", !up);
        float ev = Mth.lerp(pt, e.elevVisO, e.elevVis), rv = Mth.lerp(pt, e.rudVisO, e.rudVis), av = Mth.lerp(pt, e.ailVisO, e.ailVis);
        float c = (float) Math.toRadians(26), sc = (float) Math.sin(c), cc = (float) Math.cos(c);
        float rud = (float) Math.toRadians(rv * 20);
        pose.rotate("rud_L", rud, sc, cc, 0);
        pose.rotate("rud_R", rud, -sc, cc, 0);
        // all-moving stabilators: pitch + differential roll
        pose.rotate("elev_L", (float) Math.toRadians(ev * 20 - av * 8), 1, 0, 0);
        pose.rotate("elev_R", (float) Math.toRadians(ev * 20 + av * 8), 1, 0, 0);
        float lev = (float) Math.toRadians(Mth.clamp(e.aoa, 0f, 30f) * 0.9f - ev * 6);
        pose.rotate("levcon_L", -lev, 1, 0, 0);
        pose.rotate("levcon_R", -lev, 1, 0, 0);
        boolean mnv = e.flapsDown();
        float tvcP = (float) Math.toRadians(ev * (mnv ? 15 : 6)), tvcY = (float) Math.toRadians(rv * (mnv ? 12 : 4));
        pose.rotate("noz_L", tvcP, 1, 0, 0);
        pose.rotate("noz_R", tvcP, 1, 0, 0);
        pose.rotate("nozy_L", tvcY, 0, 1, 0);
        pose.rotate("nozy_R", tvcY, 0, 1, 0);
        float ab = Mth.lerp(pt, e.abVisO, e.abVis);
        boolean on = e.aux > 0.05f;
        for (String sd : new String[]{"_L", "_R"}) {
            pose.hide("glow" + sd, !on);
            pose.hide("ab" + sd, ab < 0.25f);
            pose.hide("abx" + sd, ab < 0.75f);
        }
        int li = e.kind.loadIndex(CraftWeapon.S8);
        int s8 = li >= 0 ? e.ammo(li) : 0;
        int pods = s8 <= 0 ? 0 : Math.min(4, (s8 + 19) / 20);
        for (int k = 0; k < 4; k++) pose.hide("pod_" + k, k >= pods);
        pose.hide("canopy_open", true);
    }


    /** Bismarck: 38 mounts train / elevate individually, barrels recoil, rangefinders follow their turrets. */
    static void poseBattleship(BattleshipEntity b, Pose pose, float pt) {
        for (int i = 0; i < BattleshipEntity.M.length; i++) {
            BattleshipEntity.Mount m = BattleshipEntity.M[i];
            float az = b.azO[i] + Mth.wrapDegrees(b.az[i] - b.azO[i]) * pt;
            float el = Mth.lerp(pt, b.elO[i], b.el[i]);
            pose.rotate(m.tag() + "_yaw", (float) Math.toRadians(az - m.rest()), 0, 1, 0);
            pose.rotate(m.tag() + "_pitch", (float) Math.toRadians(m.aft() ? el : -el), 1, 0, 0);
        }
        for (int t = 0; t < BattleshipEntity.N_MAIN; t++) {
            String tag = BattleshipEntity.M[t].tag();
            float sign = BattleshipEntity.M[t].aft() ? 1f : -1f;
            for (int g = 0; g < 2; g++) {
                float r = b.recoil[t * 2 + g];
                if (r > 0) pose.translate(tag + "_g" + g, 0, 0, sign * 1.05f * r);
            }
        }
        float aB = b.azO[1] + Mth.wrapDegrees(b.az[1] - b.azO[1]) * pt;
        float aC = b.azO[2] + Mth.wrapDegrees(b.az[2] - b.azO[2]) * pt;
        pose.rotate("rf_fore", (float) Math.toRadians(aB), 0, 1, 0);
        pose.rotate("rf_fwd", (float) Math.toRadians(aB), 0, 1, 0);
        pose.rotate("rf_aft", (float) Math.toRadians(aC - 180f), 0, 1, 0);
        int li = b.kind.loadIndex(CraftWeapon.ARADO);
        pose.hide("arado_cat", b.aradosAirborne() > 0 || (li >= 0 && b.ammo(li) <= 0));
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return WHITE;
    }
}
