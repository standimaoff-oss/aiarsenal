/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.InteractionResultHolder
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.context.UseOnContext
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.state.BlockState
 */
package ru.aiforge.realwar.item;

import ru.aiforge.realwar.block.BombBlock;
import ru.aiforge.realwar.blockentity.BombBlockEntity;
import ru.aiforge.realwar.item.TooltipItem;
import ru.aiforge.realwar.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RemoteDetonatorItem
extends TooltipItem {
    public RemoteDetonatorItem(Item.Properties props) {
        super(props);
    }

    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockState state = level.getBlockState(ctx.getClickedPos());
        if (player != null && player.isShiftKeyDown() && state.getBlock() instanceof ru.aiforge.realwar.field.IedBlock) {
            if (!level.isClientSide) {      // r17: link to an IED
                BlockPos p = ctx.getClickedPos();
                CompoundTag tag = ctx.getItemInHand().getOrCreateTag();
                tag.putInt("lx", p.getX());
                tag.putInt("ly", p.getY());
                tag.putInt("lz", p.getZ());
                tag.putString("ldim", level.dimension().location().toString());
                player.displayClientMessage(Component.translatable("message.aiarsenal.rw.linked", p.getX(), p.getY(), p.getZ()), true);
                level.playSound(null, p, ModSounds.ARM_BEEP.get(), SoundSource.PLAYERS, 0.8f, 1.6f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player == null || !(state.getBlock() instanceof BombBlock)) {
            return InteractionResult.PASS;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BlockPos origin = BombBlock.origin(ctx.getClickedPos(), state);
            CompoundTag tag = ctx.getItemInHand().getOrCreateTag();
            tag.putInt("lx", origin.getX());
            tag.putInt("ly", origin.getY());
            tag.putInt("lz", origin.getZ());
            tag.putString("ldim", level.dimension().location().toString());
            player.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.linked", (Object[])new Object[]{origin.getX(), origin.getY(), origin.getZ()}), true);
            level.playSound(null, origin, (SoundEvent)ModSounds.ARM_BEEP.get(), SoundSource.PLAYERS, 0.8f, 1.6f);
        }
        return InteractionResult.sidedSuccess((boolean)level.isClientSide);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        BombBlock bomb;
        Block block;
        net.minecraft.world.level.block.entity.BlockEntity bent;
        BlockState st;
        BlockPos origin;
        ServerLevel sl;
        ItemStack stack;
        block9: {
            block8: {
                stack = player.getItemInHand(hand);
                if (level.isClientSide) {
                    return InteractionResultHolder.success(stack);
                }
                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains("lx")) {
                    player.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.not_linked"), true);
                    return InteractionResultHolder.fail(stack);
                }
                if (!(level instanceof ServerLevel) || !(sl = (ServerLevel)level).dimension().location().toString().equals(tag.getString("ldim"))) {
                    player.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.wrong_dimension"), true);
                    return InteractionResultHolder.fail(stack);
                }
                origin = new BlockPos(tag.getInt("lx"), tag.getInt("ly"), tag.getInt("lz"));
                if (!sl.hasChunkAt(origin)) {
                    player.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.out_of_range"), true);
                    return InteractionResultHolder.fail(stack);
                }
                st = sl.getBlockState(origin);
                if (st.getBlock() instanceof ru.aiforge.realwar.field.IedBlock ied) {      // r17
                    if (!st.getValue(ru.aiforge.realwar.field.IedBlock.ARMED)) {
                        player.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.not_armed"), true);
                        return InteractionResultHolder.fail(stack);
                    }
                    player.getCooldowns().addCooldown((Item)this, 40);
                    ied.detonate(sl, origin, st);
                    return InteractionResultHolder.success(stack);
                }
                block = st.getBlock();
                if (!(block instanceof BombBlock)) break block8;
                bomb = (BombBlock)block;
                bent = sl.getBlockEntity(origin);
                if (bent instanceof BombBlockEntity) break block9;
            }
            player.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.target_gone"), true);
            return InteractionResultHolder.fail(stack);
        }
        BombBlockEntity be = (BombBlockEntity)bent;
        if (!be.isArmed()) {
            player.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.not_armed"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown((Item)this, 40);
        bomb.detonate(sl, origin, st);
        return InteractionResultHolder.success(stack);
    }
}

