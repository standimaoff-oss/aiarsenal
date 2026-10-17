/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.phys.AABB
 */
package ru.aiforge.realwar.blockentity;

import ru.aiforge.realwar.block.BombBlock;
import ru.aiforge.realwar.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class BombBlockEntity
extends BlockEntity {
    private boolean armed;

    public BombBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)ModBlockEntities.BOMB.get(), pos, state);
    }

    public boolean isArmed() {
        return this.armed;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public BombBlock bombBlock() {
        return (BombBlock)this.getBlockState().getBlock();
    }

    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("armed", this.armed);
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        this.armed = tag.getBoolean("armed");
    }

    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create((BlockEntity)this);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).inflate(2.6);
    }
}

