package ru.aiforge.realwar.soldier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import ru.aiforge.realwar.soldier.SoldierEntity;

/**
 * r27: the class uniform (tunic, sleeves, trousers, boots, headgear) drawn over the
 * Steve skin.  Transparent regions of the overlay leave the skin visible, so the
 * head and hands always keep the player's own texture.
 */
public class SoldierUniformLayer extends RenderLayer<SoldierEntity, SoldierModel> {
    public SoldierUniformLayer(RenderLayerParent<SoldierEntity, SoldierModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, SoldierEntity e,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netSwing, float headPitch, float headYaw) {
        if (e == null) return;
        ((SoldierModel) this.parentModel()).renderOverlay(ps, buf, light, e.soldierClass().uniformTexture());
    }
}
