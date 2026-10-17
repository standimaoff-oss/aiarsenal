/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.InteractionResultHolder
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.context.UseOnContext
 *  net.minecraft.world.level.ClipContext
 *  net.minecraft.world.level.ClipContext$Block
 *  net.minecraft.world.level.ClipContext$Fluid
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.item;

import ru.aiforge.realwar.item.TooltipItem;
import ru.aiforge.realwar.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class DesignatorItem
extends TooltipItem {
    public DesignatorItem(Item.Properties props) {
        super(props);
    }

    @Nullable
    public static Vec3 target(ItemStack st) {
        CompoundTag t = st.getTag();
        if (t == null || !t.getBoolean("HasTarget")) {
            return null;
        }
        return new Vec3(t.getDouble("TX"), t.getDouble("TY"), t.getDouble("TZ"));
    }

    public static void setTarget(ItemStack st, Vec3 v) {
        CompoundTag t = st.getOrCreateTag();
        t.putBoolean("HasTarget", true);
        t.putDouble("TX", v.x);
        t.putDouble("TY", v.y);
        t.putDouble("TZ", v.z);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(1500.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)player));
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(st);
        }
        if (!level.isClientSide) {
            Vec3 p = hit.getLocation();
            DesignatorItem.setTarget(st, p);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), (SoundEvent)ModSounds.LOCK_DONE.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
            player.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.designator_set", (Object[])new Object[]{(int)p.x, (int)p.z}), true);
        }
        return InteractionResultHolder.sidedSuccess(st, (boolean)level.isClientSide);
    }

    public InteractionResult useOn(UseOnContext ctx) {
        return InteractionResult.PASS;
    }

    @Nullable
    public static Vec3 findTarget(ServerPlayer p) {
        Vec3 v = DesignatorItem.target(p.getMainHandItem());
        if (v != null) {
            return v;
        }
        v = DesignatorItem.target(p.getOffhandItem());
        if (v != null) {
            return v;
        }
        for (ItemStack st : p.getInventory().items) {
            if (!(st.getItem() instanceof DesignatorItem) || (v = DesignatorItem.target(st)) == null) continue;
            return v;
        }
        return null;
    }
}

