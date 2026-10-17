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
 *  net.minecraftforge.client.extensions.common.IClientItemExtensions
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.item.TooltipItem;
import ru.aiforge.realwar.missile.GlbItemExt;
import ru.aiforge.realwar.missile.MissileType;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

public class MissileItem
extends TooltipItem {
    public final MissileType type;

    public MissileItem(MissileType type, Item.Properties props) {
        super(props.stacksTo(1).fireResistant());
        this.type = type;
    }

    public void initializeClient(Consumer<IClientItemExtensions> c) {
        GlbItemExt.apply(c);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        MissileType.Cls c = this.type.cls;
        tip.add((Component)Component.translatable((String)"tooltip.aiarsenal.rw.missile.class", (Object[])new Object[]{Component.translatable((String)("missile.aiarsenal.rw.class." + c.id))}).withStyle(ChatFormatting.AQUA));
        tip.add((Component)Component.translatable((String)"tooltip.aiarsenal.rw.missile.range", (Object[])new Object[]{(int)c.minRange, (int)c.maxRange, String.format("%.0f", c.minRange * c.kmPerBlock), String.format("%.0f", c.maxRange * c.kmPerBlock)}).withStyle(ChatFormatting.GRAY));
        String w = this.type.warheads > 1 ? this.type.warheads + " \u00d7 " + (int)this.type.massKg : String.valueOf((int)this.type.massKg);
        tip.add((Component)Component.translatable((String)"tooltip.aiarsenal.rw.missile.warhead", (Object[])new Object[]{w}).withStyle(ChatFormatting.GRAY));
        tip.add((Component)Component.translatable((String)"tooltip.aiarsenal.rw.missile.pad", (Object[])new Object[]{c.padLevel + "\u00d7" + c.padLevel}).withStyle(ChatFormatting.YELLOW));
    }
}

