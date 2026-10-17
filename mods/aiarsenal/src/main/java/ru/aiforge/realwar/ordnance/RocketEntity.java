/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Position
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientGamePacketListener
 *  net.minecraft.network.syncher.EntityDataAccessor
 *  net.minecraft.network.syncher.EntityDataSerializer
 *  net.minecraft.network.syncher.EntityDataSerializers
 *  net.minecraft.network.syncher.SynchedEntityData
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.TicketType
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.projectile.ProjectileUtil
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.ClipContext
 *  net.minecraft.world.level.ClipContext$Block
 *  net.minecraft.world.level.ClipContext$Fluid
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.Level$ExplosionInteraction
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.EntityHitResult
 *  net.minecraft.world.phys.HitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.network.NetworkHooks
 */
package ru.aiforge.realwar.ordnance;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import java.util.Comparator;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class RocketEntity
extends Entity {
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(RocketEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH = SynchedEntityData.defineId(RocketEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BURN = SynchedEntityData.defineId(RocketEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(RocketEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final TicketType<ChunkPos> TICKET = TicketType.create((String)"aiarsenal_rw_rocket", Comparator.comparingLong(ChunkPos::toLong), (int)40);
    public static Consumer<RocketEntity> clientHook = e -> {};
    protected Ballistics.Flight flight;
    protected Vec3 origin = Vec3.ZERO;
    protected int sourceVehicle = -1;
    protected boolean exploded;
    public float roll;
    public float rollO;
    public Vec3 lastDir = new Vec3(0.0, 1.0, 0.0);

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static RocketEntity launch(ServerLevel level, Ballistics.Profile prof, Vec3 muzzle, Vec3 dir, int sourceVehicleId) {
        RocketEntity e = new RocketEntity((EntityType<? extends RocketEntity>)((EntityType)ModEntities.ROCKET.get()), (Level)level);
        e.init(prof, muzzle, dir, sourceVehicleId);
        level.addFreshEntity((Entity)e);
        return e;
    }

    protected void init(Ballistics.Profile prof, Vec3 muzzle, Vec3 dir, int sourceVehicleId) {
        Vec3 d = dir.normalize();
        this.flight = new Ballistics.Flight(prof, d.x, d.y, d.z);
        this.origin = muzzle;
        this.sourceVehicle = sourceVehicleId;
        this.setPos(muzzle.x, muzzle.y, muzzle.z);
        this.xo = muzzle.x;
        this.yo = muzzle.y;
        this.zo = muzzle.z;
        this.entityData.set(DATA_KIND, prof.ordinal());
        this.orient(d);
    }

    public Ballistics.Profile profile() {
        return Ballistics.Profile.values()[Mth.clamp((int)((Integer)this.entityData.get(DATA_KIND)), (int)0, (int)(Ballistics.Profile.values().length - 1))];
    }

    public boolean isBurning() {
        return (Boolean)this.entityData.get(DATA_BURN);
    }

    public float yawDeg() {
        return ((Float)this.entityData.get(DATA_YAW)).floatValue();
    }

    public float pitchDeg() {
        return ((Float)this.entityData.get(DATA_PITCH)).floatValue();
    }

    public Ballistics.Flight flight() {
        return this.flight;
    }

    protected void defineSynchedData() {
        this.entityData.define(DATA_YAW, Float.valueOf(0.0f));
        this.entityData.define(DATA_PITCH, Float.valueOf(-90.0f));
        this.entityData.define(DATA_BURN, false);
        this.entityData.define(DATA_KIND, 0);
    }

    protected void orient(Vec3 d) {
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        this.entityData.set(DATA_YAW, Float.valueOf((float)Math.toDegrees(Math.atan2(-d.x, d.z))));
        this.entityData.set(DATA_PITCH, Float.valueOf((float)(-Math.toDegrees(Math.atan2(d.y, h)))));
        this.lastDir = d;
    }

    protected void guide(int sub, int subs, double[] out) {
        out[2] = 0.0;
        out[1] = 0.0;
        out[0] = 0.0;
    }

    protected boolean afterSubstep(int sub, int subs, Vec3 pos) {
        return false;
    }

    public void tick() {
        Level level;
        super.tick();
        this.rollO = this.roll;
        if (this.level().isClientSide) {
            this.roll += this.profile() == Ballistics.Profile.GRAD ? 38.0f : (this.profile() == Ballistics.Profile.SMERCH ? 22.0f : 0.0f);
            clientHook.accept(this);
            return;
        }
        if (this.flight == null || !((level = this.level()) instanceof ServerLevel)) {
            this.discard();
            return;
        }
        ServerLevel sl = (ServerLevel)level;
        double k = Ballistics.scaleK();
        int n = Ballistics.substeps();
        Vec3 from = this.position();
        double[] acc = new double[3];
        boolean fuzed = false;
        for (int i = 0; i < n; ++i) {
            this.guide(i, n, acc);
            this.flight.step(Ballistics.dtStep(), acc[0], acc[1], acc[2]);
            Vec3 p = new Vec3(this.origin.x + this.flight.x / k, this.origin.y + this.flight.y / k, this.origin.z + this.flight.z / k);
            if (!this.afterSubstep(i, n, p)) continue;
            this.setPos(p.x, p.y, p.z);
            fuzed = true;
            break;
        }
        Vec3 to = fuzed ? this.position() : new Vec3(this.origin.x + this.flight.x / k, this.origin.y + this.flight.y / k, this.origin.z + this.flight.z / k);
        this.keepLoaded(sl, from, to);
        if (!fuzed) {
            HitResult hit = this.sweep(sl, from, to);
            if (hit != null) {
                Entity entity;
                Vec3 vec3 = hit.getLocation();
                if (hit instanceof EntityHitResult) {
                    EntityHitResult er = (EntityHitResult)hit;
                    entity = er.getEntity();
                } else {
                    entity = null;
                }
                this.impact(sl, vec3, entity);
                return;
            }
        } else {
            this.detonate(sl, to, null);
            return;
        }
        this.setPos(to.x, to.y, to.z);
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() > 1.0E-6) {
            this.orient(d.normalize());
        }
        this.entityData.set(DATA_BURN, this.flight.burning());
        if (to.y < (double)(this.level().getMinBuildHeight() - 64) || this.flight.t > this.maxFlightTime()) {
            if (this.flight.t > this.maxFlightTime()) {
                this.detonate(sl, to, null);
            } else {
                this.discard();
            }
        }
    }

    protected double maxFlightTime() {
        return 400.0;
    }

    private void keepLoaded(ServerLevel sl, Vec3 from, Vec3 to) {
        ChunkPos a = new ChunkPos(BlockPos.containing((Position)to));
        sl.getChunkSource().addRegionTicket(TICKET, a, 2, a);
        ChunkPos b = new ChunkPos(BlockPos.containing((Position)to.add(to.subtract(from))));
        if (!b.equals(a)) {
            sl.getChunkSource().addRegionTicket(TICKET, b, 2, b);
        }
    }

    private HitResult sweep(ServerLevel sl, Vec3 from, Vec3 to) {
        AABB box;
        EntityHitResult eh;
        BlockHitResult bh;
        boolean descending;
        HitResult best = null;
        double bestD = Double.MAX_VALUE;
        boolean bl = descending = this.flight.vy < 0.0;
        if ((descending || sl.hasChunkAt(BlockPos.containing((Position)to))) && (bh = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)this))).getType() != HitResult.Type.MISS) {
            best = bh;
            bestD = bh.getLocation().distanceToSqr(from);
        }
        if ((eh = ru.aiforge.realwar.damage.VehicleHit.first((Level)sl, (Entity)this, (Vec3)from, (Vec3)to, 0.3, e -> {
            if (!e.isAlive() || e.isSpectator() || e instanceof RocketEntity) {
                return false;
            }
            if (!e.isPickable() && !(e instanceof VehicleEntity)) {
                return false;
            }
            if (this.tickCount < 80) {
                if (e.getId() == this.sourceVehicle) {
                    return false;
                }
                Entity v = e.getVehicle();
                if (v != null && v.getId() == this.sourceVehicle) {
                    return false;
                }
            }
            return true;
        })) != null && eh.getLocation().distanceToSqr(from) < bestD) {
            best = eh;
        }
        return best;
    }

    protected void impact(ServerLevel sl, Vec3 pos, Entity direct) {
        this.detonate(sl, pos, direct);
    }

    protected void detonate(ServerLevel sl, Vec3 pos, Entity direct) {
        if (this.exploded) {
            return;
        }
        this.exploded = true;
        Ballistics.Profile p = this.profile();
        this.discard();
        if (direct != null && direct.isAlive()) {
            Vec3 fd = this.flight != null ? new Vec3(this.flight.vx, this.flight.vy, this.flight.vz) : Vec3.ZERO;
            ru.aiforge.realwar.damage.HitContext.hurt(direct, ModDamage.source((Level)sl, ModDamage.ORDNANCE), (float)Math.min(60.0, p.tntKg * 0.5 + 6.0), pos,
                    fd.lengthSqr() > 1e-6 ? fd.normalize() : fd, ru.aiforge.realwar.damage.HitContext.Kind.HE, Math.max(0.3, p.tntKg),
                    this instanceof ru.aiforge.realwar.ordnance.ShellEntity ? null
                            : new ru.aiforge.realwar.damage.HitContext.Shot(this.sourceVehicle, ru.aiforge.realwar.damage.Armour.Ammo.of(p), fd.length(), this.origin));
        }
        if (((Boolean)RWConfig.COMMON.rocketsDestroyBlocks.get()).booleanValue() && ((Boolean)RWConfig.COMMON.destroyBlocks.get()).booleanValue()) {
            ConventionalBlast.explode(sl, pos, p.tntKg, p.frag);
        } else {
            sl.explode(null, pos.x, pos.y, pos.z, (float)Math.min(6.0, 1.2 * Math.cbrt(p.tntKg)), false, Level.ExplosionInteraction.NONE);
        }
    }

    public boolean isPickable() {
        return false;
    }

    public boolean shouldBeSaved() {
        return false;
    }

    public boolean isAttackable() {
        return false;
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 262144.0;
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket((Entity)this);
    }
}

