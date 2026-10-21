package ru.aiforge.realwar.soldier.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import ru.aiforge.realwar.soldier.SoldierEntity;

/**
 * r27: Steve skin as the base layer, the class uniform on top, the weapon in hand.
 */
public class SoldierRenderer extends MobRenderer<SoldierEntity, SoldierModel> {
    private static final ResourceLocation SKIN =
            new ResourceLocation("aiarsenal", "textures/entity/soldier/skin_steve.png");

    public SoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SoldierModel(ctx.bakeLayer(SoldierModel.LAYER)), 0.5f);
        this.addLayer(new SoldierUniformLayer(this));
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(SoldierEntity e) {
        return SKIN;
    }
}
