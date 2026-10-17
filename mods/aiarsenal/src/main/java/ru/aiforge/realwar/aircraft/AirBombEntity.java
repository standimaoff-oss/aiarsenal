/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientGamePacketListener
 *  net.minecraft.network.syncher.EntityDataAccessor
 *  net.minecraft.network.syncher.EntityDataSerializer
 *  net.minecraft.network.syncher.EntityDataSerializers
 *  net.minecraft.network.syncher.SynchedEntityData
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.MoverType
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.network.NetworkHooks
 */
package ru.aiforge.realwar.aircraft;

import ru.aiforge.realwar.aircraft.AirBombType;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class AirBombEntity
extends Entity {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(AirBombEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    public static final double G = 0.0245;
    public static final int ARM_TICKS = 30;
    /** Ticks after release before the fuze is armed (reply 11: low-level WW2 drops arm sooner). Server only. */
    public int armAt = 30;

    public AirBombEntity(EntityType<? extends AirBombEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static AirBombEntity drop(Level level, Vec3 at, Vec3 velPerTick, AirBombType type) {
        AirBombEntity e = new AirBombEntity((EntityType<? extends AirBombEntity>)((EntityType)ModEntities.AIR_BOMB.get()), level);
        e.setPos(at.x, at.y, at.z);
        e.xo = at.x;
        e.yo = at.y;
        e.zo = at.z;
        e.entityData.set(TYPE, type.ordinal());
        e.setDeltaMovement(velPerTick);
        return e;
    }

    public AirBombType bombType() {
        return AirBombType.byOrdinal((Integer)this.entityData.get(TYPE));
    }

    protected void defineSynchedData() {
        this.entityData.define(TYPE, 0);
    }

    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement().add(0.0, -0.0245, 0.0);
        v = v.scale(1.0 - 4.0E-4 * v.length());
        this.setDeltaMovement(v);
        this.move(MoverType.SELF, v);
        if (this.level().isClientSide) {
            return;
        }
        if (this.getY() < (double)(this.level().getMinBuildHeight() - 32)) {
            this.discard();
            return;
        }
        if ((this.onGround() || this.horizontalCollision || this.verticalCollision) && this.tickCount >= this.armAt) {
            this.detonate();
        } else if (this.onGround() || this.verticalCollision) {
            this.discard();
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel)level;
                Item it = (Item)ModItems.airBomb(this.bombType()).get();
                this.spawnAtLocation(new ItemStack((ItemLike)it));
            }
        }
    }

    private void detonate() {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)level;
        Vec3 p = this.position().add(0.0, 0.2, 0.0);
        AirBombType t = this.bombType();
        this.discard();
        ConventionalBlast.explode(sl, p, t.tntKg, t.frag);
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(TYPE, tag.getInt("type"));
        this.armAt = tag.contains("ArmAt") ? Math.max(0, Math.min(1200, tag.getInt("ArmAt"))) : ARM_TICKS;
        this.tickCount = Math.max(0, Math.min(120000, tag.getInt("FuzeAge")));
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("type", ((Integer)this.entityData.get(TYPE)).intValue());
        tag.putInt("ArmAt", this.armAt);
        tag.putInt("FuzeAge", this.tickCount);
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket((Entity)this);
    }
}

