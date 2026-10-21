package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import ru.aiforge.realwar.client.GunAnimClient;
import ru.aiforge.realwar.client.fx.MuzzleFx;
import ru.aiforge.realwar.handheld.FirearmItem;
import ru.aiforge.realwar.handheld.GunKind;
import ru.aiforge.realwar.network.PlayerGunStatePacket;

/**
 * Third-person firearms for players, AI soldiers and anyone else on the player skeleton.
 *
 * <p>The vanilla hands stay where the vanilla model puts them (so the walk cycle, the skin sleeves and the uniform
 * garment all keep working); what this layer does is draw the weapon itself at the hand anchor — the same anchor the
 * vanilla item layer would use — and animate it from the state the server synchronises for every holder:
 * aiming brings it up, a shot kicks it back, and the Blender reload animation plays while he is loading.</p>
 *
 * <p>Only presentation, driven by {@link PlayerGunStatePacket}; a missed packet costs one animation, never gameplay.</p>
 */
public final class GunHoldLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    /** [aimLift, aimSlide, recoilKick, reloadRoll, reloadPitch, reloadDrop] — tunable from handheld_view.json ("tp") */
    private static final float[] DEFAULT = {0.055f, 0.075f, 5.0f, 26.0f, 16.0f, 0.035f};

    public GunHoldLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limbSwing, float limbSwingAmount,
                       float partial, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = e.getMainHandItem();
        if (!(stack.getItem() instanceof FirearmItem gun)) return;
        GunKind k = gun.kind;
        if (GunRenderer.model(gun) == null) return;

        float aim = GunAnimClient.known(e) ? GunAnimClient.aim(e, partial) : (e.isUsingItem() ? 1f : 0f);
        float shot = GunAnimClient.shotAge(e);
        float kick = shot < 0f ? 0f : 1f - shot / 0.09f;
        kick = kick * kick;
        float reloadTime = GunAnimClient.reloadTime(e);
        boolean reloading = reloadTime >= 0f;
        float reloadTotal = Math.max(0.05f, k.reload / 20f);
        float progress = reloading ? Math.min(1f, reloadTime / reloadTotal) : 0f;
        // in and out, so the weapon settles back into the shoulder at the end of the reload
        float swing = (float) Math.sin(Math.PI * Math.min(1f, progress * 1.05f));

        float[] t = HandheldView.raw("tp");
        if (t == null || t.length < 6) t = DEFAULT;

        ps.pushPose();
        // ---- exactly the transform the vanilla item-in-hand layer uses
        HumanoidArm arm = e.getMainArm();
        this.getParentModel().translateToHand(arm, ps);
        ps.mulPose(Axis.XP.rotationDegrees(-90f));
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        boolean left = arm == HumanoidArm.LEFT;
        ps.translate((left ? -1f : 1f) / 16f, 0.125f, -0.625f);

        // ---- animation on top of it
        if (aim > 0f) {
            ps.translate(0f, t[0] * aim, -t[1] * aim);
            ps.mulPose(Axis.XP.rotationDegrees(-10f * aim));
        }
        if (kick > 0f) {
            ps.mulPose(Axis.XP.rotationDegrees(t[2] * kick));
            ps.translate(0f, 0f, t[5] * kick);
        }
        if (reloading) {
            ps.mulPose(Axis.ZP.rotationDegrees(t[3] * swing * (left ? -1f : 1f)));
            ps.mulPose(Axis.XP.rotationDegrees(t[4] * swing));
            ps.translate(0f, -t[5] * 1.4f * swing, 0f);
        }

        float[] muzzle = GunRenderer.renderHeld(stack, ps, buf, light, aim, reloading ? reloadTime : -1f,
                GunAnimClient.reloadEmpty(e), shot, GunAnimClient.shotSeq(e),
                left ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        ps.popPose();

        if (shot >= 0f && muzzle != null) {
            MuzzleFx.remote(e, muzzle[0], muzzle[1], muzzle[2],
                    new Vector3f(muzzle[3], muzzle[4], muzzle[5]), k, GunAnimClient.shotSeq(e));
        }
    }
}
