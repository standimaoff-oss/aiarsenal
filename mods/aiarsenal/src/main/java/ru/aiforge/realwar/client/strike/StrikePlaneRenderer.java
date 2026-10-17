package ru.aiforge.realwar.client.strike;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import ru.aiforge.realwar.aircraft.AirBombType;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.registry.ModSounds;
import ru.aiforge.realwar.strike.StrikePlaneEntity;

/**
 * Strike aircraft (reply 19). Su-34: hung FAB-500 / FAB-1000 shown per remaining count (nodes fab500_i / fab1000_i), gear up,
 * afterburner on the climb-out. Tu-95MS / Tu-160 reuse the walkable bomber GLBs: gear retracted, Tu-160 wings at 65 deg sweep,
 * bomb-bay doors animated, Tu-95 propellers turning.
 */
public class StrikePlaneRenderer extends EntityRenderer<StrikePlaneEntity> {
    public StrikePlaneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(StrikePlaneEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    private static void anim(Pose pose, GlbModel m, String name, float t) {
        GlbModel.Animation a = m.animation(name);
        if (a == null || a.duration <= 0) return;
        pose.sample(a, Math.min(t, a.duration - 1e-3f));
    }

    @Override
    public void render(StrikePlaneEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (!e.soundStarted) {
            e.soundStarted = true;
            Minecraft.getInstance().getSoundManager().play(new EntityBoundSoundInstance(
                    e.kind() == StrikePlaneEntity.Kind.SU34 ? ModSounds.AIR_RUSH.get() : ModSounds.ENGINE_IDLE.get(), SoundSource.HOSTILE,
                    e.kind() == StrikePlaneEntity.Kind.SU34 ? 14f : 12f, e.kind() == StrikePlaneEntity.Kind.SU34 ? 0.7f : 0.45f, e, e.getId()));
        }
        StrikePlaneEntity.Kind k = e.kind();
        ResourceLocation model = k.model;
        GlbAssets.Loaded lm = GlbAssets.get(model);
        if (lm == null && k == StrikePlaneEntity.Kind.SU34) lm = GlbAssets.get(new ResourceLocation("aiarsenal", "models/glb/su57.glb"));
        if (lm == null) return;
        Pose pose = lm.scratch();
        pose.reset();
        GlbModel m = lm.model;
        float doors = Mth.lerp(pt, e.doorsO, e.doors);
        if (k == StrikePlaneEntity.Kind.SU34) {
            int ld = e.load(), n = e.stores();
            boolean big = ld == AirBombType.FAB1000.ordinal();
            for (int i = 0; i < 8; i++) pose.hide("fab500_" + i, big || i >= n);
            for (int i = 0; i < 4; i++) pose.hide("fab1000_" + i, !big || i >= n);
            boolean ab = e.flag(StrikePlaneEntity.F_AB);
            for (String sd : new String[]{"_L", "_R"}) {
                pose.hide("ab" + sd, !ab);
                pose.hide("abx" + sd, true);
            }
            pose.hide("gear_N", true);
            pose.hide("gear_L", true);
            pose.hide("gear_R", true);
            pose.hide("canopy_open", true);
            // fallback Su-57 airframe: hide its own stores
            for (int i = 0; i < 6; i++) pose.hide("fab_" + i, true);
            for (int i = 0; i < 6; i++) pose.hide("kh38_" + i, true);
            for (int i = 0; i < 4; i++) pose.hide("pod_" + i, true);
        } else {
            anim(pose, m, "gear", 2f);
            anim(pose, m, "bay_doors", doors);
            anim(pose, m, "elevator", 19f / 24f);
            anim(pose, m, "aileron", 1f);
            anim(pose, m, "rudder", 1f);
            anim(pose, m, "flaps", 0f);
            anim(pose, m, "slats", 0f);
            if (k == StrikePlaneEntity.Kind.TU160) anim(pose, m, "sweep", 2f);
            if (k == StrikePlaneEntity.Kind.TU95) {
                anim(pose, m, "turret", 0f);
                anim(pose, m, "hatch", 0f);
                float v = Mth.lerp(pt, e.propO, e.prop) * 0.13f;
                GlbModel.Animation a = m.animation("props");
                if (a != null) pose.sample(a, (v - (float) Math.floor(v)) * a.duration);
                for (int i = 1; i <= 4; i++) pose.hide("PropDisc_" + i + "_geo", false);
            }
            for (int i = 1; i <= 4; i++) pose.hide("AB_Flame_" + i + "_geo", true);
            pose.hide("Ladder_F_geo", true);
            pose.hide("Ladder_R_geo", true);
        }
        pose.compute();
        int li = LevelRenderer.getLightColor(e.level(), BlockPos.containing(e.getX(), Math.min(e.getY(), e.level().getMaxBuildHeight() - 1), e.getZ()));
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, e.xRotO, e.getXRot())));
        GlbRenderer.render(lm, pose, ps, buf, li, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(StrikePlaneEntity e) {
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }
}
