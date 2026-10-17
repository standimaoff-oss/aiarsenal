/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  net.minecraft.client.model.PlayerModel
 *  net.minecraft.client.model.geom.ModelPart
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.client.renderer.entity.LivingEntityRenderer
 *  net.minecraft.client.renderer.entity.RenderLayerParent
 *  net.minecraft.client.renderer.entity.layers.RenderLayer
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.item.Item
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.registry.ModItems;
import java.util.function.Function;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

public final class HazmatLayer
extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation MODEL = new ResourceLocation("aiarsenal", "models/glb/hazmat.glb");
    private static final Piece[] PIECES = new Piece[]{new Piece("head", EquipmentSlot.HEAD, m -> m.head, 0.0f, 1.5f, 0.0f), new Piece("body", EquipmentSlot.CHEST, m -> m.body, 0.0f, 1.5f, 0.0f), new Piece("arm_r", EquipmentSlot.CHEST, m -> m.rightArm, -0.3125f, 1.375f, 0.0f), new Piece("arm_l", EquipmentSlot.CHEST, m -> m.leftArm, 0.3125f, 1.375f, 0.0f), new Piece("legs_r", EquipmentSlot.LEGS, m -> m.rightLeg, -0.119f, 0.75f, 0.0f), new Piece("legs_l", EquipmentSlot.LEGS, m -> m.leftLeg, 0.119f, 0.75f, 0.0f), new Piece("boot_r", EquipmentSlot.FEET, m -> m.rightLeg, -0.119f, 0.75f, 0.0f), new Piece("boot_l", EquipmentSlot.FEET, m -> m.leftLeg, 0.119f, 0.75f, 0.0f)};

    public HazmatLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    private static Item itemFor(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> (Item)ModItems.HAZMAT_HELMET.get();
            case CHEST -> (Item)ModItems.HAZMAT_CHESTPLATE.get();
            case LEGS -> (Item)ModItems.HAZMAT_LEGGINGS.get();
            default -> (Item)ModItems.HAZMAT_BOOTS.get();
        };
    }

    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer pl, float limb, float limbSwing, float pt, float age, float yaw, float pitch) {
        boolean any = false;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!pl.getItemBySlot(s).is(HazmatLayer.itemFor(s))) continue;
            any = true;
        }
        if (!any || pl.isInvisible()) {
            return;
        }
        GlbAssets.Loaded lm = GlbAssets.get(MODEL);
        if (lm == null) {
            return;
        }
        PlayerModel m = (PlayerModel)this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords((LivingEntity)pl, (float)0.0f);
        for (Piece pc : PIECES) {
            if (!pl.getItemBySlot(pc.slot).is(HazmatLayer.itemFor(pc.slot))) continue;
            int ni = -1;
            for (int i = 0; i < lm.model.nodes.size(); ++i) {
                if (!lm.model.nodes.get((int)i).name.equals(pc.node)) continue;
                ni = i;
            }
            if (ni < 0) continue;
            ps.pushPose();
            pc.bone.apply((PlayerModel<AbstractClientPlayer>)m).translateAndRotate(ps);
            ps.scale(1.0f, -1.0f, -1.0f);
            ps.translate(-pc.px, -pc.py, -pc.pz);
            GlbRenderer.renderNode(lm, ni, ps, buffers, light, overlay);
            ps.popPose();
        }
    }

    private record Piece(String node, EquipmentSlot slot, Function<PlayerModel<AbstractClientPlayer>, ModelPart> bone, float px, float py, float pz) {
    }
}

