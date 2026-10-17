package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import ru.aiforge.realwar.handheld.FirearmItem;

/** Common player poses: no local-player ADS state, so remote players use the same grip. */
public final class PistolPose {
    private PistolPose() {}
    private static void locomotion(net.minecraft.client.model.geom.ModelPart hand,
                                   net.minecraft.world.entity.LivingEntity entity, HumanoidArm arm, boolean pistol) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean local = entity == mc.player;
        var f = ru.aiforge.realwar.client.WeaponMotionClient.frame(entity, mc.getFrameTime(),
                local ? ru.aiforge.realwar.client.GunClient.adsLerp(mc.getFrameTime()) : 0,
                local && ru.aiforge.realwar.client.GunClient.reloading(), pistol);
        float sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        hand.xRot -= (float)Math.toRadians(f.pitch());
        hand.yRot += (float)Math.toRadians(f.yaw() * sign);
        hand.zRot += (float)Math.toRadians(f.roll() * sign * .5f);
    }
    public static final HumanoidModel.ArmPose HOLD = HumanoidModel.ArmPose.create("AIARSENAL_PISTOL", false,
            (model, entity, arm) -> {
                var hand = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                hand.xRot = -(float)Math.PI / 2f + model.head.xRot;
                hand.yRot = model.head.yRot;
                hand.zRot = 0;
                locomotion(hand, entity, arm, true);
            });
    public static final HumanoidModel.ArmPose LONG_GUN = HumanoidModel.ArmPose.create("AIARSENAL_LONG_GUN", true,
            (model, entity, arm) -> {
                boolean right = arm == HumanoidArm.RIGHT;
                var main = right ? model.rightArm : model.leftArm;
                var support = right ? model.leftArm : model.rightArm;
                main.xRot = -(float)Math.PI / 2f + model.head.xRot;
                main.yRot = model.head.yRot;
                main.zRot = 0;
                locomotion(main, entity, arm, false);
                if (!(entity.getMainHandItem().getItem() instanceof FirearmItem gun)) return;
                var lm = GlbAssets.get(GunRenderer.glb(gun.kind.id));
                if (lm == null) return;
                // Same chain as ItemInHandLayer + GunRenderer.Item, in model units (metres).
                var ps = new PoseStack(); main.translateAndRotate(ps);
                ps.mulPose(Axis.XP.rotationDegrees(-90)); ps.mulPose(Axis.YP.rotationDegrees(180));
                ps.translate(right ? .0625 : -.0625, .125, -.625);
                var ctx = right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                ps.translate(-.5, -.5, -.5);
                HandheldView.apply(ps, HandheldView.get(gun.kind.id, ctx, false), GunRenderer.bounds(GunRenderer.glb(gun.kind.id), lm));
                float[] grip = GunRenderer.node(lm, gun.kind.id + ".arm_r");
                float[] fore = GunRenderer.node(lm, gun.kind.id + ".arm_l");
                ps.translate(-grip[0], -grip[1], -grip[2]);
                Vector3f target = ps.last().pose().transformPosition(new Vector3f(fore[0], fore[1], fore[2]));
                target.sub(support.x / 16f, support.y / 16f, support.z / 16f);
                float distance = target.length(); if (distance < .01f) return;
                target.div(distance);
                support.xRot = -(float)Math.acos(Math.max(-1, Math.min(1, target.y)));
                support.yRot = (float)Math.atan2(-target.x, -target.z);
                support.zRot = 0;
                // Vanilla has one arm bone: fit its palm to the actual foregrip, rather than a crossbow pose.
                support.yScale = Math.min(1.8f, distance / .535f);
            });
}
