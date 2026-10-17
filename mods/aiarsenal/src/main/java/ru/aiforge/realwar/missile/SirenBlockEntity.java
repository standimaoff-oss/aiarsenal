/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientGamePacketListener
 *  net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.phys.AABB
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.missile.PadRegistry;
import ru.aiforge.realwar.registry.ModS3;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class SirenBlockEntity
extends BlockEntity {
    public static final int WAIL_TICKS = 84;
    private int alarm;
    private boolean powered;
    private boolean active;
    public float angle;
    public float angleO;
    public float speed;

    public SirenBlockEntity(BlockPos pos, BlockState st) {
        super((BlockEntityType)ModS3.SIREN_BE.get(), pos, st);
    }

    public boolean active() {
        return this.active;
    }

    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            PadRegistry.addSiren(this.level, this.worldPosition);
        }
    }

    public void setRemoved() {
        super.setRemoved();
        if (this.level != null) {
            PadRegistry.removeSiren(this.level, this.worldPosition);
        }
    }

    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.level != null) {
            PadRegistry.removeSiren(this.level, this.worldPosition);
        }
    }

    public static void alarmAround(ServerLevel sl, BlockPos c, double r, int ticks) {
        for (BlockPos p : PadRegistry.sirensNear((Level)sl, c, r)) {
            BlockEntity blockEntity = sl.getBlockEntity(p);
            if (!(blockEntity instanceof SirenBlockEntity)) continue;
            SirenBlockEntity s = (SirenBlockEntity)blockEntity;
            s.alarm = Math.max(ticks, 0);
        }
    }

    public void setPowered(boolean p) {
        this.powered = p;
    }

    public void toggleTest() {
        this.alarm = this.alarm > 0 ? 0 : 120;
    }

    public static void tick(Level l, BlockPos pos, BlockState st, SirenBlockEntity be) {
        boolean on;
        if (l.isClientSide) {
            be.angleO = be.angle;
            float target = be.active ? 16.0f : 0.0f;
            be.speed = be.speed + (target - be.speed) * (be.active ? 0.03f : 0.015f);
            be.angle = (be.angle + be.speed) % 360.0f;
            return;
        }
        if (be.alarm > 0) {
            --be.alarm;
        }
        boolean bl = on = be.alarm > 0 || be.powered;
        if (on && l.getGameTime() % 84L == 0L) {
            l.playSound(null, (double)pos.getX() + 0.5, (double)pos.getY() + 1.5, (double)pos.getZ() + 0.5, (SoundEvent)ModS3.SIREN_WAIL.get(), SoundSource.BLOCKS, 7.0f, 1.0f);
        }
        if (on != be.active) {
            be.active = on;
            be.setChanged();
            l.sendBlockUpdated(pos, st, st, 3);
        }
    }

    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putBoolean("Active", this.active);
        t.putInt("Alarm", this.alarm);
    }

    public void load(CompoundTag t) {
        super.load(t);
        this.active = t.getBoolean("Active");
        this.alarm = t.getInt("Alarm");
    }

    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Active", this.active);
        return t;
    }

    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create((BlockEntity)this);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).expandTowards(0.0, 2.0, 0.0).inflate(1.0);
    }
}

