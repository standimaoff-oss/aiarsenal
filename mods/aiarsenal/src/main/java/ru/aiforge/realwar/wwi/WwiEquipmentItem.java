package ru.aiforge.realwar.wwi;

import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ru.aiforge.realwar.missile.GlbItemExt;

/** Simple inventory item whose client model is supplied by the embedded WWI GLB asset. */
public final class WwiEquipmentItem extends Item {
    public WwiEquipmentItem(Properties properties) {
        super(properties);
    }

    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        GlbItemExt.apply(consumer);
    }
}
