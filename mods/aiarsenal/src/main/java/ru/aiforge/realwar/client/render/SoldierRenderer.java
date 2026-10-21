package ru.aiforge.realwar.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import ru.aiforge.realwar.npc.SoldierEntity;

/**
 * Renders an AI soldier on the vanilla player skeleton: a plain field-uniform skin per army and period, plus the
 * mod's own {@link UniformLayer}, so the four armour pieces carried by the soldier draw the same Blender garment as
 * they do on a player.
 */
public class SoldierRenderer extends HumanoidMobRenderer<SoldierEntity, HumanoidModel<SoldierEntity>> {
    private static final ResourceLocation NATO = tex("nato");
    private static final ResourceLocation RUSSIA = tex("russia");
    private static final ResourceLocation USSR = tex("ussr");
    private static final ResourceLocation GERMANY = tex("germany");
    private static final ResourceLocation FRANCE = tex("france");
    private static final ResourceLocation RUS_EMPIRE = tex("rus_empire");

    public SoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new UniformLayer<>(this));
        this.addLayer(new GunHoldLayer<>(this));
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("aiarsenal", "textures/entity/soldier/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(SoldierEntity e) {
        return switch (e.faction()) {
            case NATO -> NATO;
            case RUSSIA -> switch (e.era()) {
                case MODERN -> RUSSIA;
                case WW2 -> USSR;
                case NAPOLEONIC -> RUS_EMPIRE;
            };
            case GERMANY -> GERMANY;
            case FRANCE -> FRANCE;
        };
    }
}
