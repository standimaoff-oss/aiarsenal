package ru.aiforge.bm21grad.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ru.aiforge.bm21grad.ammo.RocketType;

import java.util.List;
import java.util.function.Consumer;

/** 122-mm rocket round; right-click a BM-21 to put it into the vehicle's ammo rack. */
public class RocketItem extends Item {
    public final RocketType type;

    public RocketItem(RocketType type, Properties p) {
        super(p);
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack st, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(type.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.aiarsenal.rocket.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ru.aiforge.bm21grad.client.ClientItemExtensions.INSTANCE);
    }
}
