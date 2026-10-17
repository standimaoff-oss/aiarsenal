/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.locale.Language
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.BlockItem
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public class TooltipBlockItem
extends BlockItem {
    public TooltipBlockItem(Block b, Item.Properties p) {
        super(b, p);
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        String key = this.getDescriptionId() + ".desc";
        Language lang = Language.getInstance();
        for (int i = 1; i <= 6; ++i) {
            String k = key + i;
            if (!lang.has(k)) continue;
            tip.add((Component)Component.translatable((String)k).withStyle(i == 1 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}

