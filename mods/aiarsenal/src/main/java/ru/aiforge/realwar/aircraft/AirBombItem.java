/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  net.minecraft.world.level.Level
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.aircraft;

import ru.aiforge.realwar.aircraft.AirBombType;
import ru.aiforge.realwar.item.TooltipItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class AirBombItem
extends TooltipItem {
    public final AirBombType type;

    public AirBombItem(AirBombType type, Item.Properties props) {
        super(props.stacksTo(16).fireResistant());
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add((Component)Component.translatable((String)"tooltip.aiarsenal.rw.airbomb", (Object[])new Object[]{this.type.massKg, (int)this.type.tntKg, String.format("%.1f", Float.valueOf(this.type.lengthM))}).withStyle(ChatFormatting.AQUA));
    }
}

