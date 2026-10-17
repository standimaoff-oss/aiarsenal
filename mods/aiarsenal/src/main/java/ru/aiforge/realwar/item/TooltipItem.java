/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.locale.Language
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  net.minecraft.world.level.Level
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class TooltipItem
extends Item {
    private final double radioactivity;

    public TooltipItem(Item.Properties props) {
        this(props, 0.0);
    }

    public TooltipItem(Item.Properties props, double radioactivity) {
        super(props);
        this.radioactivity = radioactivity;
    }

    public double radioactivity() {
        return this.radioactivity;
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        String key = this.getDescriptionId() + ".desc";
        if (Language.getInstance().has(key)) {
            tip.add((Component)Component.translatable((String)key).withStyle(ChatFormatting.GRAY));
        }
        if (this.radioactivity > 0.0) {
            tip.add((Component)Component.translatable((String)"tooltip.aiarsenal.rw.radioactive", (Object[])new Object[]{String.format("%.1f", this.radioactivity)}).withStyle(ChatFormatting.GREEN));
        }
    }
}

