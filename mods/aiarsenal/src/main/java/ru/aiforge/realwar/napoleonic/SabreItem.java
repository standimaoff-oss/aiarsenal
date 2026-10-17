package ru.aiforge.realwar.napoleonic;
import net.minecraft.world.item.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;
public final class SabreItem extends SwordItem {
    public SabreItem(){super(Tiers.IRON,3,-2.4f,new Item.Properties());}
    @Override public void initializeClient(Consumer<IClientItemExtensions> c){c.accept(new IClientItemExtensions(){public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return ru.aiforge.realwar.client.render.GunRenderer.item();}});}
}
