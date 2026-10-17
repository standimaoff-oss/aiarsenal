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
 *  net.minecraftforge.client.extensions.common.IClientItemExtensions
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.missile.GlbItemExt;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

public class GlbBlockItem
extends BlockItem {
    public GlbBlockItem(Block b, Item.Properties p) {
        super(b, p);
    }

    public void initializeClient(Consumer<IClientItemExtensions> c) {
        GlbItemExt.apply(c);
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        String key = this.getDescriptionId() + ".desc";
        tip.add((Component)Component.translatable((String)key).withStyle(ChatFormatting.GRAY));
        for (int i = 1; i <= 4; ++i) {
            String k = key + i;
            if (!Language.getInstance().has(k)) continue;
            tip.add((Component)Component.translatable((String)k).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

