package ru.aiforge.arsenal.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ru.aiforge.arsenal.ArsenalHooks;
import ru.aiforge.arsenal.core.Kind;

import java.util.List;

/** Launcher ammunition (Shahed drones for the rail, Kalibr / Tomahawk, Yars): right-click a launcher to load. */
public class AmmoItem extends Item {
    public final Kind kind;

    public AmmoItem(Kind kind, Properties p) {
        super(p);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        ArsenalHooks.tooltip(kind, tip);
    }
}
