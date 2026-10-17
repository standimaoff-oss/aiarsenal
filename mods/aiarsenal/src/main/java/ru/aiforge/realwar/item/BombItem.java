/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.network.chat.Component
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.item.BlockItem
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  net.minecraft.world.item.context.BlockPlaceContext
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.level.gameevent.GameEvent
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.item;

import ru.aiforge.realwar.block.BombBlock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

public class BombItem
extends BlockItem {
    private final BombBlock bomb;

    public BombItem(BombBlock block, Item.Properties props) {
        super((Block)block, props);
        this.bomb = block;
    }

    public InteractionResult place(BlockPlaceContext ctx) {
        if (!ctx.canPlace()) {
            return InteractionResult.FAIL;
        }
        Level level = ctx.getLevel();
        Direction facing = ctx.getHorizontalDirection();
        BlockPos origin = ctx.getClickedPos();
        for (int i = 0; i < 3; ++i) {
            BlockPos p = origin.relative(facing, i);
            if (level.isOutsideBuildHeight(p) || !level.getWorldBorder().isWithinBounds(p)) {
                return InteractionResult.FAIL;
            }
            BlockState s = level.getBlockState(p);
            if (i <= 0 || s.isAir() || s.canBeReplaced()) continue;
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            BlockState base = (BlockState)this.bomb.defaultBlockState().setValue((Property)BombBlock.FACING, facing);
            for (int i = 0; i < 3; ++i) {
                level.setBlock(origin.relative(facing, i), (BlockState)base.setValue((Property)BombBlock.PART, Integer.valueOf(i)), 3);
            }
            level.gameEvent((Entity)ctx.getPlayer(), GameEvent.BLOCK_PLACE, origin);
        }
        level.playSound(ctx.getPlayer(), origin, this.bomb.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess((boolean)level.isClientSide);
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add((Component)Component.translatable((String)"tooltip.aiarsenal.rw.bomb.yield", (Object[])new Object[]{String.format("%.0f", this.bomb.type.yieldKt)}).withStyle(ChatFormatting.GOLD));
        tip.add((Component)Component.translatable((String)"tooltip.aiarsenal.rw.bomb.dims", (Object[])new Object[]{this.bomb.type.massKg, String.format("%.2f", Float.valueOf(this.bomb.type.lengthM)), String.format("%.2f", Float.valueOf(this.bomb.type.diameterM))}).withStyle(ChatFormatting.GRAY));
        tip.add((Component)Component.translatable((String)"tooltip.aiarsenal.rw.bomb.fuze", (Object[])new Object[]{String.format("%.0f", this.bomb.type.burstHeightM)}).withStyle(ChatFormatting.GRAY));
        tip.add((Component)Component.translatable((String)(this.getDescriptionId() + ".desc")).withStyle(ChatFormatting.DARK_GRAY));
    }
}

