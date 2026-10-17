package ru.aiforge.tubombers.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ru.aiforge.tubombers.aircraft.BombType;

import java.util.List;

/** Bomb item: right-click a parked bomber to load its bays. */
public class BombItem extends Item {
    public final BombType type;

    public BombItem(BombType type, Properties p) {
        super(p);
        this.type = type;
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        if (type.nuclear()) return ru.aiforge.arsenal.ArsenalHooks.placeCharge(ctx, type.arsenal());
        return super.useOn(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        if (type.arsenal() != null) {
            ru.aiforge.arsenal.ArsenalHooks.tooltip(type.arsenal(), tip);
            return;
        }
        tip.add(Component.translatable("tooltip.aiarsenal.bomb", (int) type.mass, (int) type.explosive, type.length, (int) (type.diameter * 1000))
                .withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.aiarsenal.bomb_load").withStyle(ChatFormatting.DARK_GRAY));
    }
}
