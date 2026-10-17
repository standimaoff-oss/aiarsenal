/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientGamePacketListener
 *  net.minecraft.network.syncher.EntityDataAccessor
 *  net.minecraft.network.syncher.EntityDataSerializer
 *  net.minecraft.network.syncher.EntityDataSerializers
 *  net.minecraft.network.syncher.SynchedEntityData
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.MoverType
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.network.NetworkHooks
 */
package ru.aiforge.realwar.entity;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.NuclearBlast;
import ru.aiforge.realwar.block.BombBlock;
import ru.aiforge.realwar.registry.BombType;
import ru.aiforge.realwar.registry.ModBlocks;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class FallingBombEntity
extends Entity {
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(FallingBombEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ARMED = SynchedEntityData.defineId(FallingBombEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FACING = SynchedEntityData.defineId(FallingBombEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BALLISTIC = SynchedEntityData.defineId(FallingBombEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    public static final int FUZE_ARM_DELAY = 30;

    public FallingBombEntity(EntityType<? extends FallingBombEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public static FallingBombEntity create(Level level, double x, double y, double z, BombType type, boolean armed, Direction facing) {
        FallingBombEntity e = new FallingBombEntity((EntityType<? extends FallingBombEntity>)((EntityType)ModEntities.FALLING_BOMB.get()), level);
        e.setPos(x, y, z);
        e.xo = x;
        e.yo = y;
        e.zo = z;
        e.entityData.set(DATA_TYPE, type.ordinal());
        e.entityData.set(DATA_ARMED, armed);
        e.entityData.set(DATA_FACING, facing.get2DDataValue());
        return e;
    }

    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE, 0);
        this.entityData.define(DATA_ARMED, false);
        this.entityData.define(DATA_FACING, 0);
        this.entityData.define(DATA_BALLISTIC, false);
    }

    public boolean isBallistic() {
        return (Boolean)this.entityData.get(DATA_BALLISTIC);
    }

    public static FallingBombEntity drop(Level level, Vec3 at, Vec3 velPerTick, BombType type, Direction facing) {
        FallingBombEntity e = FallingBombEntity.create(level, at.x, at.y, at.z, type, true, facing);
        e.entityData.set(DATA_BALLISTIC, true);
        e.setDeltaMovement(velPerTick);
        return e;
    }

    public BombType bombType() {
        return BombType.byOrdinal((Integer)this.entityData.get(DATA_TYPE));
    }

    public boolean isArmed() {
        return (Boolean)this.entityData.get(DATA_ARMED);
    }

    public Direction facing() {
        return Direction.from2DDataValue((int)((Integer)this.entityData.get(DATA_FACING)));
    }

    public void tick() {
        super.tick();
        if (this.isBallistic()) {
            Vec3 v = this.getDeltaMovement().add(0.0, -0.0245, 0.0);
            v = v.scale(1.0 - 4.0E-4 * v.length());
            this.setDeltaMovement(v);
            this.move(MoverType.SELF, v);
        } else {
            if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.05, 0.0));
            }
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        }
        if (this.level().isClientSide) {
            if (this.tickCount % 40 == 2 && this.getDeltaMovement().y < -0.6) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.BOMB_WHISTLE.get(), SoundSource.NEUTRAL, 3.0f, 1.0f, false);
            }
            return;
        }
        if (this.getY() < (double)(this.level().getMinBuildHeight() - 32)) {
            this.discard();
            return;
        }
        if (this.isArmed() && this.tickCount >= 30) {
            double ground = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor((double)this.getX()), Mth.floor((double)this.getZ()));
            double fuzeBlocks = this.bombType().burstHeightM / (Double)RWConfig.COMMON.metersPerBlock.get();
            if (this.getY() - ground <= fuzeBlocks) {
                this.detonate(false);
                return;
            }
        }
        if (this.onGround() || this.verticalCollision) {
            this.land();
        }
    }

    private void detonate(boolean surface) {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)level;
        Vec3 p = this.position();
        this.discard();
        NuclearBlast.start(sl, p, this.bombType().yieldKt, surface);
    }

    private void land() {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)level;
        if (this.isArmed() && ((Boolean)RWConfig.COMMON.nuclearEnabled.get()).booleanValue()) {
            this.detonate(true);
            return;
        }
        Direction f = this.facing();
        BlockPos mid = BlockPos.containing((double)this.getX(), (double)(this.getY() + 0.1), (double)this.getZ());
        BlockPos origin = mid.relative(f.getOpposite(), 1);
        boolean fits = true;
        for (int i = 0; i < 3 && fits; ++i) {
            BlockState s = sl.getBlockState(origin.relative(f, i));
            fits = s.isAir() || s.canBeReplaced();
        }
        BombType t = this.bombType();
        if (fits) {
            BlockState base = (BlockState)((Block)(t == BombType.LITTLE_BOY ? ModBlocks.LITTLE_BOY : ModBlocks.RDS_4).get()).defaultBlockState().setValue((Property)BombBlock.FACING, f);
            for (int i = 0; i < 3; ++i) {
                sl.setBlock(origin.relative(f, i), (BlockState)base.setValue((Property)BombBlock.PART, Integer.valueOf(i)), 3);
            }
        } else {
            Item item = (Item)(t == BombType.LITTLE_BOY ? ModItems.LITTLE_BOY : ModItems.RDS_4).get();
            this.spawnAtLocation(new ItemStack((ItemLike)item));
        }
        this.discard();
    }

    public boolean isPickable() {
        return false;
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_TYPE, tag.getInt("type"));
        this.entityData.set(DATA_ARMED, tag.getBoolean("armed"));
        this.entityData.set(DATA_FACING, tag.getInt("facing"));
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("type", ((Integer)this.entityData.get(DATA_TYPE)).intValue());
        tag.putBoolean("armed", this.isArmed());
        tag.putInt("facing", ((Integer)this.entityData.get(DATA_FACING)).intValue());
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket((Entity)this);
    }
}

