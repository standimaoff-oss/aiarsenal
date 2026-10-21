package ru.aiforge.realwar.soldier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ru.aiforge.realwar.soldier.SoldierEntity;

/**
 * r27: the AI soldier body - the classic Steve shape (the player's own head and
 * body geometry).  The base pass draws the Steve skin; the uniform is a second
 * overlay pass over the same geometry (see {@link SoldierUniformLayer}).
 */
public class SoldierModel extends HumanoidModel<SoldierEntity> {
    public SoldierModel(ModelPart root) {
        super(root);
    }

    /** Renders the whole humanoid silhouette with the given (overlay) texture. */
    public void renderOverlay(PoseStack ps, MultiBufferSource buf, int light, ResourceLocation tex) {
        this.renderToBuffer(ps, buf, light, OverlayTexture.NO_OVERLAY, tex, 1f, 1f, 1f, 1f);
    }
}
