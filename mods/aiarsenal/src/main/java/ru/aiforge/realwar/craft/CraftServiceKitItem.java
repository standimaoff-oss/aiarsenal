package ru.aiforge.realwar.craft;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Right-click a helicopter/boat/submarine: full ammunition, fuel, battery and a 30 % structural repair. */
public class CraftServiceKitItem extends Item {
    public CraftServiceKitItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.aiarsenal.craft_service_kit.tip").withStyle(ChatFormatting.GRAY));
    }
}
