/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.context.UseOnContext
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockState
 */
package ru.aiforge.realwar.item;

import ru.aiforge.realwar.block.BombBlock;
import ru.aiforge.realwar.blockentity.BombBlockEntity;
import ru.aiforge.realwar.item.TooltipItem;
import ru.aiforge.realwar.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ArmingPlugItem
extends TooltipItem {
    public ArmingPlugItem(Item.Properties props) {
        super(props);
    }

    public InteractionResult useOn(UseOnContext ctx) {
        BlockPos pos;
        Level level = ctx.getLevel();
        BlockState state = level.getBlockState(pos = ctx.getClickedPos());
        Block block = state.getBlock();
        if (!(block instanceof BombBlock)) {
            return InteractionResult.PASS;
        }
        BombBlock bomb = (BombBlock)block;
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos origin = BombBlock.origin(pos, state);
        BlockEntity blockEntity = level.getBlockEntity(origin);
        if (blockEntity instanceof BombBlockEntity) {
            BombBlockEntity be = (BombBlockEntity)blockEntity;
            boolean now = !be.isArmed();
            be.setArmed(now);
            level.playSound(null, origin, (SoundEvent)ModSounds.ARM_BEEP.get(), SoundSource.BLOCKS, 1.0f, now ? 1.3f : 0.8f);
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage((Component)Component.translatable((String)(now ? "message.aiarsenal.rw.armed" : "message.aiarsenal.rw.disarmed"), (Object[])new Object[]{Component.translatable((String)bomb.getDescriptionId())}), true);
            }
        }
        return InteractionResult.CONSUME;
    }
}

