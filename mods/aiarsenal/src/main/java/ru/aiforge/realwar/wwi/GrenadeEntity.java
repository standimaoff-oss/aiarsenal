package ru.aiforge.realwar.wwi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import ru.aiforge.realwar.registry.ModItems;

/** Timed projectile; impact does not detonate it. Fuse and resting state survive chunk saves. */
public final class GrenadeEntity extends ThrowableItemProjectile {
    public static final int FUSE_TICKS = 80;
    private int fuse = FUSE_TICKS;
    private boolean resting;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) { super(type, level); }
    @Override protected Item getDefaultItem() { return ModItems.MILLS_BOMB.get(); }
    @Override protected float getGravity() { return .04f; }
    public int fuseTicks() { return fuse; }

    @Override public void tick() {
        if (resting) { setDeltaMovement(Vec3.ZERO); setNoGravity(true); }
        super.tick();
        if (!level().isClientSide && --fuse <= 0) {
            // MOB interaction respects mobGriefing; never unconditionally destroys protected terrain.
            level().explode(this, getX(), getY(), getZ(), 2.2f, Level.ExplosionInteraction.MOB);
            // r27: volumetric smoke column on top of the vanilla flash (FX-only packet -
            // damage and terrain are already handled by the explosion above).
            ru.aiforge.realwar.network.ModNetwork.sendNear(level(), getX(), getY(), getZ(), 120.0,
                    new ru.aiforge.realwar.network.ConventionalEffectPacket(getX(), getY(), getZ(), 1.2f, true, level().random.nextLong()));
            discard();
        }
    }

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide) {
            setPos(hit.getLocation());
            setDeltaMovement(Vec3.ZERO);
            resting = true;
            setNoGravity(true);
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Fuse", fuse);
        tag.putBoolean("Resting", resting);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fuse = tag.contains("Fuse") ? Math.max(1, Math.min(FUSE_TICKS, tag.getInt("Fuse"))) : FUSE_TICKS;
        resting = tag.getBoolean("Resting");
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
