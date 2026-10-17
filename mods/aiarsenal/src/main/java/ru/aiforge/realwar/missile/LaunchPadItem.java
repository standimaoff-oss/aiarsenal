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
 *  net.minecraft.world.item.context.BlockPlaceContext
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraftforge.client.extensions.common.IClientItemExtensions
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.missile.GlbItemExt;
import ru.aiforge.realwar.missile.LaunchPadBlock;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

public class LaunchPadItem
extends BlockItem {
    public LaunchPadItem(LaunchPadBlock b, Item.Properties p) {
        super((Block)b, p);
    }

    public void initializeClient(Consumer<IClientItemExtensions> c) {
        GlbItemExt.apply(c);
    }

    protected boolean canPlace(BlockPlaceContext ctx, BlockState st) {
        LaunchPadBlock b = (LaunchPadBlock)this.getBlock();
        if (!b.canFit(ctx.getLevel(), ctx.getClickedPos())) {
            if (ctx.getPlayer() != null && ctx.getLevel().isClientSide) {
                LaunchPadBlock.noRoom(ctx.getPlayer(), b.level);
            }
            return false;
        }
        return super.canPlace(ctx, st);
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

