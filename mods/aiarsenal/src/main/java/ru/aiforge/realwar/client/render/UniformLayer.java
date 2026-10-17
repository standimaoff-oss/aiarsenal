package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderArmEvent;
import ru.aiforge.realwar.item.UniformItem;

/**
 * Reply 15: draws the Blender uniforms (models/glb/uniforms.glb, tools/blender/uniforms.py) on players wearing
 * {@link UniformItem}s. Like the hazmat layer every garment node is bound to its skin bone: the node geometry is in
 * absolute player coordinates (glTF y up, front +z), so the bone transform is followed by a flip and the bone pivot.
 * Each piece may consist of several nodes ("ru_general.arm_l", "ru_general.arm_l.board", ...). Pieces of different
 * sets can be mixed freely. Also draws the sleeve on the first-person arm.
 */
public final class UniformLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation MODEL = new ResourceLocation("aiarsenal", "models/glb/uniforms.glb");
    private static final ResourceLocation HISTORICAL = new ResourceLocation("aiarsenal", "models/glb/historical_uniforms.glb");
    private static ResourceLocation modelFor(String set) {
        return ru.aiforge.realwar.item.HistoricalUniforms.contains(set) ? HISTORICAL : MODEL;
    }
    private static final Piece[] PIECES = {
            new Piece("head", EquipmentSlot.HEAD, m -> m.head, 0f, 1.5f, 0f),
            new Piece("body", EquipmentSlot.CHEST, m -> m.body, 0f, 1.5f, 0f),
            new Piece("arm_r", EquipmentSlot.CHEST, m -> m.rightArm, -0.3125f, 1.375f, 0f),
            new Piece("arm_l", EquipmentSlot.CHEST, m -> m.leftArm, 0.3125f, 1.375f, 0f),
            new Piece("leg_r", EquipmentSlot.LEGS, m -> m.rightLeg, -0.119f, 0.75f, 0f),
            new Piece("leg_l", EquipmentSlot.LEGS, m -> m.leftLeg, 0.119f, 0.75f, 0f),
            new Piece("boot_r", EquipmentSlot.FEET, m -> m.rightLeg, -0.119f, 0.75f, 0f),
            new Piece("boot_l", EquipmentSlot.FEET, m -> m.leftLeg, 0.119f, 0.75f, 0f)};
    /** node indices per "set.piece", rebuilt when the model is reloaded */
    private record NodeIndex(Object model, Map<String, int[]> nodes) {}
    // At most one current index per asset. Mixed historical/modern outfits must not rebuild
    // the entire node table on every arm/leg, nor retain every model across resource reloads.
    private static final Map<ResourceLocation, NodeIndex> INDICES = new HashMap<>();

    public UniformLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    private static int[] nodes(GlbAssets.Loaded lm, String key) {
        ResourceLocation asset = modelFor(key.substring(0, key.indexOf('.')));
        NodeIndex index = INDICES.get(asset);
        if (index == null || index.model() != lm) {
            Map<String, List<Integer>> tmp = new HashMap<>();
            for (int i = 0; i < lm.model.nodes.size(); i++) {
                String n = lm.model.nodes.get(i).name;
                if (n == null) continue;
                String[] p = n.split("\\.");
                if (p.length < 2) continue;
                tmp.computeIfAbsent(p[0] + "." + p[1], k -> new ArrayList<>()).add(i);
            }
            Map<String, int[]> nodes = new HashMap<>();
            tmp.forEach((k, v) -> nodes.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
            index = new NodeIndex(lm, nodes);
            INDICES.put(asset, index);
        }
        return index.nodes().getOrDefault(key, new int[0]);
    }

    private static String setOf(ItemStack st) {
        return st.getItem() instanceof UniformItem u ? u.set : null;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, T pl, float limb, float limbSwing, float pt, float age, float yaw, float pitch) {
        if (pl.isInvisible()) return;
        String[] sets = new String[4];
        boolean any = false;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            sets[s.getIndex()] = setOf(pl.getItemBySlot(s));
            any |= sets[s.getIndex()] != null;
        }
        if (!any) return;
        M m = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(pl, 0f);
        for (Piece pc : PIECES) {
            String set = sets[pc.slot.getIndex()];
            if (set == null) continue;
            GlbAssets.Loaded lm = GlbAssets.get(modelFor(set));
            if (lm == null) continue;
            drawPiece(lm, set, pc, pc.bone.apply(m), ps, buf, light, overlay, pl instanceof AbstractClientPlayer ap && "slim".equals(ap.getModelName()));
        }
    }

    private static void drawPiece(GlbAssets.Loaded lm, String set, Piece pc, ModelPart bone, PoseStack ps, MultiBufferSource buf, int light, int overlay, boolean slim) {
        int[] ns = nodes(lm, set + "." + pc.node);
        if (ns.length == 0) return;
        ps.pushPose();
        bone.translateAndRotate(ps);
        boolean arm = pc.node.startsWith("arm_");
        if (slim && arm) ps.translate(0, -.03125, 0);
        ps.scale(1f, -1f, -1f);
        ps.translate(-pc.px, -pc.py, -pc.pz);
        if (slim && arm) {
            float center = pc.node.equals("arm_r") ? -.375f : .375f;
            float inset = pc.node.equals("arm_r") ? .03125f : -.03125f;
            ps.translate(center + inset, 0, 0); ps.scale(.75f, 1, 1); ps.translate(-center, 0, 0);
        }
        for (int ni : ns) GlbRenderer.renderNode(lm, ni, ps, buf, light, overlay);
        ps.popPose();
    }

    /** First person: vanilla arm (skin + sleeve layer) followed by the uniform sleeve, same pose as PlayerRenderer. */
    public static void onRenderArm(RenderArmEvent e) {
        AbstractClientPlayer pl = e.getPlayer();
        String set = setOf(pl.getItemBySlot(EquipmentSlot.CHEST));
        if (set == null) return;
        GlbAssets.Loaded lm = GlbAssets.get(modelFor(set));
        if (lm == null) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(pl) instanceof PlayerRenderer pr)) return;
        e.setCanceled(true);
        PlayerModel<AbstractClientPlayer> m = pr.getModel();
        boolean right = e.getArm() == HumanoidArm.RIGHT;
        ModelPart arm = right ? m.rightArm : m.leftArm, sleeve = right ? m.rightSleeve : m.leftSleeve;
        PoseStack ps = e.getPoseStack();
        MultiBufferSource buf = e.getMultiBufferSource();
        int light = e.getPackedLight();
        m.rightArm.yScale = m.leftArm.yScale = m.rightSleeve.yScale = m.leftSleeve.yScale = 1f;
        m.rightArmPose = m.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        m.attackTime = 0f;
        m.crouching = false;
        m.swimAmount = 0f;
        m.setupAnim(pl, 0f, 0f, 0f, 0f, 0f);
        arm.xRot = 0f;
        sleeve.xRot = 0f;
        arm.visible = true;
        sleeve.visible = false; // The uniform supplies the outer sleeve, not the skin jacket overlay.
        arm.render(ps, buf.getBuffer(RenderType.entitySolid(pl.getSkinTextureLocation())), light, OverlayTexture.NO_OVERLAY);
        drawPiece(lm, set, right ? PIECES[2] : PIECES[3], arm, ps, buf, light, OverlayTexture.NO_OVERLAY, "slim".equals(pl.getModelName()));
    }

    private record Piece(String node, EquipmentSlot slot, Function<HumanoidModel<?>, ModelPart> bone, float px, float py, float pz) {
    }
}
