package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import ru.aiforge.realwar.client.GunClient;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.handheld.FirearmItem;
import ru.aiforge.realwar.handheld.GunKind;

/**
 * r23: the hands are the player's own vanilla Minecraft arms (skin + sleeves, slim or classic), each drawn from its
 * shoulder (off screen, low at the side) to the hand anchor <id>.arm_r / <id>.arm_l on the weapon's grip, so they follow
 * the reload / bolt animations. RPG-7 aims over its iron sights (rpg7.irons) unless the PGO-7V3 is selected.
 * r22 small arms on screen. First person: the GLB placed by "<id>.fp" =
 * [hipX, hipY, hipZ, cant, eyeDz, eyeDy] from handheld_view.json, blended to the sight line while aiming, with recoil
 * kick, bolt travel, muzzle flash and the Blender reload animations. Other contexts: the gun alone (arms hidden).
 */
@Mod.EventBusSubscriber(modid = "aiarsenal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GunRenderer {
    private static Item inst;
    private static final Map<ResourceLocation, float[]> BOUNDS = new HashMap<>();
    private static final float[] FP_DEFAULT = {0.10f, -0.09f, -0.30f, -2f, 0.02f, 0f};

    private GunRenderer() {
    }

    public static BlockEntityWithoutLevelRenderer item() {
        if (inst == null) inst = new Item(Minecraft.getInstance());
        return inst;
    }

    static ResourceLocation glb(String id) {
        return new ResourceLocation("aiarsenal", "models/glb/" + id + ".glb");
    }

    // ------------------------------------------------------------------ first person

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || p.getVehicle() != null) return;
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof FirearmItem g)) return;
        e.setCanceled(true); // custom firearm hands; pistol support hand is used only during reload
        if (e.getHand() != InteractionHand.MAIN_HAND || GunClient.scoped()) return;
        GunKind k = g.kind;
        GlbAssets.Loaded lm = GlbAssets.get(glb(k.id));
        if (lm == null) return;
        float pt = e.getPartialTick();
        float a = GunClient.adsLerp(pt);
        float[] fp = HandheldView.raw(k.id + ".fp");
        if (fp == null || fp.length < 6) fp = FP_DEFAULT;
        // sight (rear aperture / optic eyepiece) in model space -> view space (x, y, z) -> (-x, y, -z)
        float[] s = node(lm, k == GunKind.RPG7 && !GunClient.rpgOptic() ? "rpg7.irons" : k.id + ".sight");
        float eye = 0.07f + fp[4];
        float ax = s[0], ay = -s[1] - fp[5], az = -eye + s[2];
        float tx = fp[0] + (ax - fp[0]) * a;
        float ty = fp[1] + (ay - fp[1]) * a;
        float tz = fp[2] + (az - fp[2]) * a;
        float since = GunClient.sinceShot();
        float kickT = k.launcher() ? 0.28f : 0.085f;
        float kick = Math.max(0f, 1f - since / kickT);
        kick = kick * kick;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        // Remove vanilla hand bob/sway as ADS settles. It must not move the sights off the camera ray.
        ps.last().pose().lerp(new Matrix4f(), a);
        ps.last().normal().set(new org.joml.Matrix3f(ps.last().pose()).invert().transpose());
        Matrix4f base = new Matrix4f(ps.last().pose());
        ps.pushPose();
        var motion = ru.aiforge.realwar.client.WeaponMotionClient.frame(p, pt, a, GunClient.reloading(), k.pistol());
        ps.translate(motion.x(), motion.y(), motion.z());
        ps.mulPose(Axis.XP.rotationDegrees(motion.pitch()));
        ps.mulPose(Axis.YP.rotationDegrees(motion.yaw()));
        ps.mulPose(Axis.ZP.rotationDegrees(motion.roll()));
        ps.translate(tx, ty - 0.55f * e.getEquipProgress() * (1f - a), tz + (k.launcher() ? 0.09f : 0.035f) * kick * (1f - 0.5f * a));
        ps.mulPose(Axis.ZP.rotationDegrees(fp[3] * (1f - a)));
        ps.mulPose(Axis.XP.rotationDegrees((k.launcher() ? 5f : 2.2f) * kick * (1f - 0.55f * a)));
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        Pose pose = lm.scratch();
        boolean rel = GunClient.reloading();
        if (rel) {
            GlbModel.Animation an = null;
            if (GunClient.reloadEmpty()) an = lm.model.animation(k.id + "_reload_empty");
            if (an == null) an = lm.model.animation(k.id + "_reload");
            if (an != null) pose.sample(an, Math.min(GunClient.reloadTime(), an.duration - 0.001f));
        } else if (k.manual() && since > 0.12f) {                                // T-5000 bolt / R870 pump after the shot
            GlbModel.Animation an = lm.model.animation(k.id + (k.boltAction() ? "_bolt" : "_pump"));
            if (an != null && since - 0.12f < an.duration) pose.sample(an, Math.min(since - 0.12f, an.duration - 0.001f));
        }
        if (k.flintlock()) {
            float progress = GunClient.reloadTime() / (k.reload / 20f);
            pose.hide(k.id + ".charge", !rel || progress < .15f || progress > .43f);
            pose.hide(k.id + ".ramrod", !rel || progress < .43f || progress > .9f);
            pose.hide(k.id + ".rod_stowed", rel && progress >= .43f && progress <= .9f);
            if (!rel && since < .25f) pose.rotate(k.id + ".cock", -.7f * (1 - since / .25f), 1, 0, 0);
        }
        if (k == GunKind.C96) pose.hide(k.id + ".clip", !rel);
        if (k.singleLoad()) pose.hide(k.id + ".round", !rel);
        boolean flash = since < (k.launcher() ? 0.09f : 0.045f);
        pose.hide(k.id + ".flash", !flash);
        if (flash) {
            pose.rotate(k.id + ".flash", (GunClient.shotCount() * 1.9f) % 6.283f, 0, 0, 1);
            float fs = 0.8f + (GunClient.shotCount() * 37 % 10) * 0.05f;
            pose.scale(k.id + ".flash", fs, fs, fs);
        }
        if (!k.launcher() && !k.manual() && !k.flintlock() && since < 0.06f) pose.translate(k.id + ".bolt", 0, 0, -(k.pistol() ? 0.032f : 0.075f) * (1f - since / 0.06f));
        // spent tube: no grenade (RPG-7) / open muzzle without the seeker dome or covers (MANPADS) until the fresh tube is on
        if (k.launcher() && FirearmItem.ammo(st) == 0 && (!rel || k.manpads() && GunClient.reloadTime() < k.reload / 20f * 0.4f))
            pose.hide(k.id + ".round", true);
        pose.compute();
        int light = flash ? 0xF000F0 : e.getPackedLight();
        GlbRenderer.render(lm, pose, ps, e.getMultiBufferSource(), light, OverlayTexture.NO_OVERLAY);
        Vector3f hr = anchor(lm, pose, ps, base, k.id + ".arm_r");
        Vector3f hl = anchor(lm, pose, ps, base, k.id + ".arm_l");
        ps.popPose();
        int armLight = e.getPackedLight();
        if (hr != null) arm(mc, p, ps, e.getMultiBufferSource(), armLight, hr, true, k.pistol() ? 0.82f : 1f);
        if (hl != null && (!k.pistol() || rel)) arm(mc, p, ps, e.getMultiBufferSource(), armLight, hl, false, k.pistol() ? 0.82f : 1f);
        ps.popPose();
    }

    // ------------------------------------------------------------------ vanilla arms

    /** shoulders in hand-view space (camera at 0, -Z forward): low at the sides, slightly behind the eye */
    private static final Vector3f SHOULDER_R = new Vector3f(0.30f, -0.62f, 0.16f);
    private static final Vector3f SHOULDER_L = new Vector3f(-0.36f, -0.66f, 0.10f);
    /** cross-section scale of the vanilla arm (4 px = 0.25 m at 1.0) */
    private static final float ARM_W = 0.34f;

    /** hand anchor (animated) -> hand-view space */
    private static Vector3f anchor(GlbAssets.Loaded lm, Pose pose, PoseStack ps, Matrix4f base, String name) {
        int i = lm.model.nodeIndex(name);
        if (i < 0) return null;
        Vector4f q = new Vector4f(0, 0, 0, 1);
        pose.world[i].transform(q);
        ps.last().pose().transform(q);
        new Matrix4f(base).invert().transform(q);
        return new Vector3f(q.x, q.y, q.z);
    }

    /**
     * The player's vanilla arm (+ sleeve layer) with its hand on the anchor: model +Y (shoulder -> hand) points from the
     * shoulder to the grip, the hand's palm faces the weapon (inwards / up), the arm is stretched along its length only
     * so that it always reaches back to the shoulder outside the screen.
     */
    private static void arm(Minecraft mc, LocalPlayer p, PoseStack ps, MultiBufferSource buf, int light, Vector3f hand, boolean right, float widthScale) {
        if ((p.getMainHandItem().getItem() instanceof FirearmItem f && f.kind.pistol())
                || (p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() instanceof ru.aiforge.realwar.item.UniformItem u
                    && ru.aiforge.realwar.item.HistoricalUniforms.contains(u.set))) {
            FirstPersonArms.draw(p, ps, buf, light, hand, right);
            return;
        }
        if (!(mc.getEntityRenderDispatcher().getRenderer(p) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr)) return;
        net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> m = pr.getModel();
        boolean slim = "slim".equals(p.getModelName());
        net.minecraft.client.model.geom.ModelPart armP = right ? m.rightArm : m.leftArm;
        net.minecraft.client.model.geom.ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
        // live-tunable "arms" = [width, rShX, rShY, rShZ, lShX, lShY, lShZ, gripPx] (handheld_view.json)
        float[] t = HandheldView.raw("arms");
        float armW = (t != null && t.length >= 8 ? t[0] : ARM_W) * widthScale;
        Vector3f sh = t != null && t.length >= 8 ? (right ? new Vector3f(t[1], t[2], t[3]) : new Vector3f(t[4], t[5], t[6])) : right ? SHOULDER_R : SHOULDER_L;
        float grip = t != null && t.length >= 8 ? t[7] : 9.6f;
        // the support hand holds the handguard from below: its arm axis runs under the anchor (palm up, fingers around)
        float drop = t != null && t.length >= 10 ? t[right ? 9 : 8] : right ? 0.0f : 0.045f;
        float roll = t != null && t.length >= 11 ? t[10] : 0f;
        hand = new Vector3f(hand.x, hand.y - drop, hand.z);
        Vector3f y = new Vector3f(hand).sub(sh);
        float len = y.length();
        if (len < 1e-3f) return;
        y.div(len);
        // outer side of the arm faces away from the body: start from "outwards" and make it perpendicular to the arm
        Vector3f out = new Vector3f(right ? 1f : -1f, -0.35f, 0f);
        Vector3f x = out.sub(new Vector3f(y).mul(out.dot(y))).normalize();
        if (!right) x.negate();
        if (roll != 0f) x.rotateAxis((float) Math.toRadians(right ? roll : -roll), y.x, y.y, y.z);
        Vector3f z = new Vector3f(x).cross(y).normalize();
        org.joml.Matrix3f rot = new org.joml.Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
        float cxPx = right ? (slim ? -0.5f : -1f) : (slim ? 0.5f : 1f);
        float lenScale = Math.max(armW, len * 16f / (grip + 2f));                 // y = -2 px (shoulder) .. 8.5 px (grip)
        ps.pushPose();
        ps.translate(hand.x, hand.y, hand.z);
        ps.mulPose(rot.getNormalizedRotation(new org.joml.Quaternionf()));
        ps.scale(armW, lenScale, armW);
        ps.translate(-cxPx / 16f, -grip / 16f, 0f);
        float ax = armP.x, ay = armP.y, az = armP.z, rx = armP.xRot, ry = armP.yRot, rz = armP.zRot;
        float sx = sleeve.x, sy = sleeve.y, sz = sleeve.z, srx = sleeve.xRot, sry = sleeve.yRot, srz = sleeve.zRot;
        boolean sv = sleeve.visible;
        float asx = armP.xScale, asy = armP.yScale, asz = armP.zScale;
        float ssx = sleeve.xScale, ssy = sleeve.yScale, ssz = sleeve.zScale;
        armP.xScale = armP.yScale = armP.zScale = 1f;
        sleeve.xScale = sleeve.yScale = sleeve.zScale = 1f;
        armP.setPos(0, 0, 0);
        armP.setRotation(0, 0, 0);
        sleeve.setPos(0, 0, 0);
        sleeve.setRotation(0, 0, 0);
        ResourceLocation skin = p.getSkinTextureLocation();
        armP.render(ps, buf.getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        sleeve.visible = p.isModelPartShown(right ? net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE
                : net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE);
        if (sleeve.visible) sleeve.render(ps, buf.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        armP.setPos(ax, ay, az);
        armP.setRotation(rx, ry, rz);
        sleeve.setPos(sx, sy, sz);
        sleeve.setRotation(srx, sry, srz);
        sleeve.visible = sv;
        armP.xScale = asx; armP.yScale = asy; armP.zScale = asz;
        sleeve.xScale = ssx; sleeve.yScale = ssy; sleeve.zScale = ssz;
        ps.popPose();
    }

    /** rest position of a node origin in model space */
    static float[] node(GlbAssets.Loaded lm, String name) {
        int i = lm.model.nodeIndex(name);
        if (i < 0) return new float[]{0, 0, 0};
        Vector4f q = new Vector4f(0, 0, 0, 1);
        lm.staticPose().world[i].transform(q);
        return new float[]{q.x, q.y, q.z};
    }

    /** AABB of the gun without the arms (for the inventory / item-frame fit) */
    static float[] bounds(ResourceLocation id, GlbAssets.Loaded lm) {
        return BOUNDS.computeIfAbsent(id, key -> {
            Pose p = lm.staticPose();
            float[] mn = {1e9f, 1e9f, 1e9f}, mx = {-1e9f, -1e9f, -1e9f};
            Vector4f q = new Vector4f();
            for (int i = 0; i < lm.model.nodes.size(); i++) {
                GlbModel.Node n = lm.model.nodes.get(i);
                if (n.mesh < 0 || n.name.contains(".arm_") || n.name.endsWith(".flash")) continue;
                for (GlbModel.Primitive pr : lm.model.meshes.get(n.mesh).prims) {
                    for (int j = 0; j + 2 < pr.pos.length; j += 3) {
                        q.set(pr.pos[j], pr.pos[j + 1], pr.pos[j + 2], 1f);
                        p.world[i].transform(q);
                        mn[0] = Math.min(mn[0], q.x); mn[1] = Math.min(mn[1], q.y); mn[2] = Math.min(mn[2], q.z);
                        mx[0] = Math.max(mx[0], q.x); mx[1] = Math.max(mx[1], q.y); mx[2] = Math.max(mx[2], q.z);
                    }
                }
            }
            if (mn[0] > mx[0]) return new float[]{0, 0, 0, 1};
            float ext = Math.max(mx[0] - mn[0], Math.max(mx[1] - mn[1], mx[2] - mn[2]));
            return new float[]{(mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2, Math.max(ext, 1e-3f)};
        });
    }

    public static void clear() {
        BOUNDS.clear();
    }

    // ------------------------------------------------------------------ inventory / third person / ground / frame

    static final class Item extends BlockEntityWithoutLevelRenderer {
        Item(Minecraft mc) {
            super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null) return;
            String id = key.getPath();
            ResourceLocation model = glb(id);
            GlbAssets.Loaded lm = GlbAssets.get(model);
            if (lm == null) return;
            float[] v = HandheldView.get(id, ctx, false);
            float[] b = bounds(model, lm);
            ps.pushPose();
            HandheldView.apply(ps, v, b);
            if (stack.getItem() instanceof FirearmItem
                    && (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                float[] grip = node(lm, id + ".arm_r");
                ps.translate(-grip[0], -grip[1], -grip[2]);
            }
            Pose pose = lm.scratch();
            pose.hide(id + ".arm_l", true);
            pose.hide(id + ".arm_r", true);
            pose.hide(id + ".flash", true);
            pose.hide(id + ".ramrod", true);
            pose.hide(id + ".charge", true);
            if (id.equals("c96")) pose.hide(id + ".clip", true);
            if (id.equals("mosin_pu") || id.equals("k98k_zf39")) pose.hide(id + ".round", true);
            if (stack.getItem() instanceof FirearmItem && FirearmItem.ammo(stack) == 0 && ctx != ItemDisplayContext.GUI) pose.hide(id + ".round", true);
            pose.compute();
            GlbRenderer.render(lm, pose, ps, buf, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
