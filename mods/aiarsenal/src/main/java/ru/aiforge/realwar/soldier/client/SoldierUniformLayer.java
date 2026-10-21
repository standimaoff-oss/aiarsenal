package ru.aiforge.realwar.soldier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import ru.aiforge.realwar.soldier.SoldierEntity;

/**
 * r27: the class uniform (tunic, sleeves, trousers, boots, headgear) drawn over
 * the Steve skin.  The parent humanoid model is re-rendered a second time with the
 * uniform texture: transparent regions of the overlay leave the skin visible, so
 * the head and hands always keep the player's own texture.  1.20.1 RenderLayer API.
 */
public class SoldierUniformLayer extends RenderLayer<SoldierEntity, SoldierModel> {
    public SoldierUniformLayer(RenderLayerParent<SoldierEntity, SoldierModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, SoldierEntity e,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float age, float yaw, float pitch) {
        if (e.isInvisible()) return;
        SoldierModel m = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0f);
        // cutout: alpha-tested, so the transparent uniform regions show the skin underneath
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(e.soldierClass().uniformTexture()));
        m.head.render(ps, vc, light, overlay);
        m.body.render(ps, vc, light, overlay);
        m.rightArm.render(ps, vc, light, overlay);
        m.leftArm.render(ps, vc, light, overlay);
        m.rightLeg.render(ps, vc, light, overlay);
        m.leftLeg.render(ps, vc, light, overlay);
    }
}
