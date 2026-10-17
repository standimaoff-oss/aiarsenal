/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Position
 *  net.minecraft.core.particles.ParticleOptions
 *  net.minecraft.core.particles.ParticleTypes
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
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.ClipContext
 *  net.minecraft.world.level.ClipContext$Block
 *  net.minecraft.world.level.ClipContext$Fluid
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.network.NetworkHooks
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.missile.MissileType;
import ru.aiforge.realwar.missile.Warheads;
import ru.aiforge.realwar.registry.ModS3;
import java.util.Comparator;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class MissileEntity
extends Entity {
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(MissileEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(MissileEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(MissileEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH = SynchedEntityData.defineId(MissileEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BURN = SynchedEntityData.defineId(MissileEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    private static final TicketType<ChunkPos> TICKET = TicketType.create((String)"aiarsenal_rw_missile", Comparator.comparingLong(ChunkPos::toLong), (int)60);
    public static Consumer<MissileEntity> clientHook = e -> {};
    private Vec3 start = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private double horiz;
    private double dirX;
    private double dirZ;
    private double apex;
    private double hv;
    private double speed;
    private double boost;
    private double massKg;
    private boolean targetFixed;
    private boolean exploded;
    private boolean separated;
    private int age;
    private int hiddenTicks;
    private int detonateAt = -1;
    private double cruiseY;
    private Vec3 lastDir = new Vec3(0.0, 1.0, 0.0);
    private Vec3 lastPos;
    private ChunkPos lastTicket;
    private Vec3 detonatePos;
    public float roll;
    public float rollO;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static MissileEntity launch(ServerLevel level, MissileType type, Vec3 pos, Vec3 target) {
        MissileEntity e = new MissileEntity((EntityType<? extends MissileEntity>)((EntityType)ModS3.MISSILE.get()), (Level)level);
        e.entityData.set(DATA_TYPE, type.ordinal());
        e.entityData.set(DATA_MODE, 0);
        e.setupPath(pos, target, type.cls.speed, type.cls.boostTicks, type.totalMassKg());
        // Acquire a bounded ticking corridor before publishing a new projectile.
        // A child must not depend on the soon-discarded parent's player/tickets.
        e.keepLoaded(level, pos, target.subtract(pos).normalize().scale(type.cls.speed * 40.0), 0.0);
        level.addFreshEntity((Entity)e);
        return e;
    }

    private static MissileEntity spawnRv(ServerLevel level, MissileType type, Vec3 pos, Vec3 target) {
        MissileEntity e = new MissileEntity((EntityType<? extends MissileEntity>)((EntityType)ModS3.MISSILE.get()), (Level)level);
        e.entityData.set(DATA_TYPE, type.ordinal());
        e.entityData.set(DATA_MODE, 1);
        e.setupPath(pos, target, type.cls.speed * 1.5, 1.0, type.massKg);
        e.separated = true;
        // Acquire a bounded ticking corridor before publishing a new projectile.
        // A child must not depend on the soon-discarded parent's player/tickets.
        e.keepLoaded(level, pos, target.subtract(pos).normalize().scale(type.cls.speed * 40.0), 0.0);
        level.addFreshEntity((Entity)e);
        return e;
    }

    private void setupPath(Vec3 s, Vec3 t, double v, double boostTicks, double mass) {
        this.start = s;
        this.target = t;
        double dx = t.x - s.x;
        double dz = t.z - s.z;
        this.horiz = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        this.dirX = dx / this.horiz;
        this.dirZ = dz / this.horiz;
        this.speed = v;
        this.boost = boostTicks;
        this.massKg = mass;
        MissileType.Cls c = this.type().cls;
        if (this.mode() == 0) {
            this.apex = Mth.clamp((double)(this.horiz * c.apexFrac), (double)c.apexMin, (double)c.apexMax);
            this.hv = Math.min(0.05 * this.horiz, 600.0);
            this.cruiseY = s.y;
        }
        this.setPos(s.x, s.y, s.z);
        this.xo = s.x;
        this.yo = s.y;
        this.zo = s.z;
        this.lastPos = s;
        this.entityData.set(DATA_YAW, Float.valueOf((float)Math.toDegrees(Math.atan2(-this.dirX, this.dirZ))));
        this.entityData.set(DATA_PITCH, Float.valueOf(this.mode() == 0 ? -90.0f : 0.0f));
    }

    public MissileType type() {
        MissileType t = MissileType.byOrdinal((Integer)this.entityData.get(DATA_TYPE));
        return t == null ? MissileType.TACTICAL_HE : t;
    }

    public int mode() {
        return (Integer)this.entityData.get(DATA_MODE);
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

    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE, 0);
        this.entityData.define(DATA_MODE, 0);
        this.entityData.define(DATA_YAW, Float.valueOf(0.0f));
        this.entityData.define(DATA_PITCH, Float.valueOf(-90.0f));
        this.entityData.define(DATA_BURN, false);
    }

    private double dist(int t) {
        return (double)t <= this.boost ? this.speed * (double)t * (double)t / (2.0 * this.boost) : this.speed * ((double)t - this.boost / 2.0);
    }

    private double groundAt(ServerLevel sl, double x, double z, double def) {
        int bz;
        int bx = Mth.floor((double)x);
        if (!sl.hasChunk(bx >> 4, (bz = Mth.floor((double)z)) >> 4)) {
            return def;
        }
        return sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    public void tick() {
        double y;
        ServerLevel sl;
        block20: {
            block19: {
                super.tick();
                this.rollO = this.roll;
                if (this.level().isClientSide) {
                    if (this.mode() == 1) {
                        this.roll += 14.0f;
                    }
                    clientHook.accept(this);
                    return;
                }
                Level level = this.level();
                if (!(level instanceof ServerLevel)) break block19;
                sl = (ServerLevel)level;
                if (this.start != null && !(this.massKg <= 0.0)) break block20;
            }
            this.discard();
            return;
        }
        if (this.mode() != 2) {
            double nextDistance = Math.min(this.dist(this.age + 1), this.horiz);
            Vec3 next = new Vec3(this.start.x + this.dirX * nextDistance, this.getY(),
                    this.start.z + this.dirZ * nextDistance);
            if (!sl.isPositionEntityTicking(BlockPos.containing(next))) {
                // Tickets request asynchronous readiness; they do not guarantee
                // it this tick. Never step out of the active entity section into
                // a FULL-but-not-ticking chunk (where our tick could disappear).
                // Hold flight age/position, not the server or chunk-generation thread.
                this.lastTicket = null;
                this.keepLoaded(sl, next, new Vec3(this.dirX * this.speed * 40, 0,
                        this.dirZ * this.speed * 40), nextDistance / this.horiz);
                return;
            }
        }
        ++this.age;
        MissileType type = this.type();
        if (this.mode() == 2) {
            this.hiddenTick(sl, type);
            return;
        }
        double d = this.dist(this.age);
        double u = d / this.horiz;
        Vec3 from = this.position();
        double x = this.start.x + this.dirX * Math.min(d, this.horiz);
        double z = this.start.z + this.dirZ * Math.min(d, this.horiz);
        if (this.mode() == 0 && type.cls == MissileType.Cls.CRUISE) {
            y = this.cruiseY(sl, type, x, z, u, d);
        } else {
            if (!this.targetFixed && u > 0.8) {
                this.targetFixed = true;
                double gy = this.groundAt(sl, this.target.x, this.target.z, this.target.y);
                this.target = new Vec3(this.target.x, gy, this.target.z);
            }
            double uc = Math.min(u, 1.0);
            if (this.mode() == 1) {
                y = this.target.y + (this.start.y - this.target.y) * (1.0 - uc * uc);
            } else {
                double r = 1.0 - Math.pow(1.0 - Math.min(uc / 0.03, 1.0), 2.0);
                y = this.start.y + (this.target.y - this.start.y) * uc + 4.0 * this.apex * uc * (1.0 - uc) + this.hv * r * (1.0 - uc);
            }
        }
        Vec3 to = new Vec3(x, y, z);
        this.keepLoaded(sl, to, to.subtract(from).scale(40.0), u);
        if (this.mode() == 0 && type.warheads > 1 && type.cls != MissileType.Cls.CRUISE && !this.separated && u >= 0.5) {
            this.separate(sl, type, to);
            return;
        }
        if (this.age > 20 && sl.hasChunkAt(BlockPos.containing((Position)to))) {
            BlockHitResult hit;
            boolean check;
            boolean bl = check = type.cls == MissileType.Cls.CRUISE || to.y < from.y || u > 0.6;
            if (check && (hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)this))).getType() != HitResult.Type.MISS) {
                this.arrive(sl, type, hit.getLocation());
                return;
            }
        }
        this.setPos(to.x, to.y, to.z);
        Vec3 dv = to.subtract(from);
        if (dv.lengthSqr() > 1.0E-8) {
            Vec3 nd = dv.normalize();
            this.lastDir = this.lastDir.scale(0.75).add(nd.scale(0.25));
            if (this.lastDir.lengthSqr() < 1.0E-6) {
                this.lastDir = nd;
            }
            this.lastDir = this.lastDir.normalize();
            double h = Math.sqrt(this.lastDir.x * this.lastDir.x + this.lastDir.z * this.lastDir.z);
            this.entityData.set(DATA_YAW, Float.valueOf((float)Math.toDegrees(Math.atan2(-this.lastDir.x, this.lastDir.z))));
            this.entityData.set(DATA_PITCH, Float.valueOf((float)(-Math.toDegrees(Math.atan2(this.lastDir.y, h)))));
        }
        this.entityData.set(DATA_BURN, this.mode() == 0 && (type.cls == MissileType.Cls.CRUISE || (double)this.age < this.boost * 1.35));
        if (u >= 1.0) {
            this.arrive(sl, type, to);
        } else if (this.age > 20000) {
            this.discard();
        }
    }

    private double cruiseY(ServerLevel sl, MissileType type, double x, double z, double u, double d) {
        double alt = 26.0;
        double ground = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 3; ++i) {
            ground = Math.max(ground, this.groundAt(sl, x + this.dirX * 9.0 * (double)i, z + this.dirZ * 9.0 * (double)i, Double.NEGATIVE_INFINITY));
        }
        double desired = ground > Double.NEGATIVE_INFINITY ? ground + alt : this.cruiseY;
        double rem = this.horiz - d;
        double endY = this.target.y + (type.warhead == MissileType.Warhead.CLUSTER ? 34.0 : 0.0);
        if (!this.targetFixed && rem < 150.0) {
            this.targetFixed = true;
            this.target = new Vec3(this.target.x, this.groundAt(sl, this.target.x, this.target.z, this.target.y), this.target.z);
            endY = this.target.y + (type.warhead == MissileType.Warhead.CLUSTER ? 34.0 : 0.0);
        }
        double k = Mth.clamp((double)(rem / 110.0), (double)0.0, (double)1.0);
        if (rem < 110.0) {
            desired = endY + (Math.max(desired, endY) - endY) * k;
        }
        double cap = this.age < 30 ? 1.1 : 0.9;
        double dy = Mth.clamp((double)(desired - this.cruiseY), (double)(rem < 110.0 ? -1.4 : -0.6), (double)cap);
        this.cruiseY += dy;
        return this.cruiseY;
    }

    /** Called from the level clock too: a hidden/non-ticking entity cannot
     * renew a lease from its own tick. Never advances flight or impacts. */
    void renewChunkLease(ServerLevel level) {
        if (isRemoved()) return;
        if (mode() == 2) {
            ChunkPos at = chunkPosition();
            level.getChunkSource().addRegionTicket(TICKET, at, 3, at);
            return;
        }
        lastTicket = null;
        keepLoaded(level, position(), new Vec3(dirX * speed * 20, 0, dirZ * speed * 20),
                horiz > 0 ? dist(age) / horiz : 0);
    }

    private void keepLoaded(ServerLevel sl, Vec3 pos, Vec3 ahead, double u) {
        ChunkPos a = new ChunkPos(BlockPos.containing((double)pos.x, (double)64.0, (double)pos.z));
        if (!a.equals(this.lastTicket) || this.age % 15 == 0) {
            this.lastTicket = a;
            // Two isolated tickets (current and 40 ticks ahead) left a gap.
            // Fast MIRV children could enter a non-entity-ticking edge chunk and
            // stop forever, so their own refresh callback would never run again.
            // Prewarm the intervening corridor, including a ticking neighbour.
            // At most 13 centres, temporary 60-tick tickets; no permanent forced
            // chunks and no synchronous getChunk()/generation in the flight tick.
            Vec3 lead = new Vec3(ahead.x, 0, ahead.z);
            double length = lead.length();
            if (length > 192.0) { lead = lead.scale(192.0 / length); length = 192.0; }
            int steps = Math.max(1, (int)Math.ceil(length / 16.0));
            for (int i = 0; i <= steps; i++) {
                Vec3 point = pos.add(lead.scale((double)i / steps));
                ChunkPos b = new ChunkPos(BlockPos.containing(point.x, 64.0, point.z));
                sl.getChunkSource().addRegionTicket(TICKET, b, 3, b);
            }
        }
        if (this.age % 20 == 1 && (u > 0.6 || this.horiz < 900.0)) {
            ChunkPos t = new ChunkPos(BlockPos.containing((double)this.target.x, (double)64.0, (double)this.target.z));
            sl.getChunkSource().addRegionTicket(TICKET, t, 3, t);
        }
    }

    private void separate(ServerLevel sl, MissileType type, Vec3 pos) {
        this.separated = true;
        RandomSourceHolder rnd = new RandomSourceHolder(sl);
        for (int i = 0; i < type.warheads; ++i) {
            double a = Math.PI * 2 * (double)i / (double)type.warheads + rnd.next() * 0.5;
            double r = type.footprint * (0.4 + 0.6 * rnd.next());
            Vec3 t = new Vec3(this.target.x + Math.cos(a) * r, this.target.y, this.target.z + Math.sin(a) * r);
            MissileEntity.spawnRv(sl, type, pos, t);
        }
        sl.sendParticles((ParticleOptions)ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        sl.sendParticles((ParticleOptions)ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 40, 1.5, 1.5, 1.5, 0.05);
        this.discard();
    }

    private void arrive(ServerLevel sl, MissileType type, Vec3 pos) {
        if (this.exploded) {
            return;
        }
        if (this.mode() == 1) {
            this.exploded = true;
            this.discard();
            ConventionalBlast.explode(sl, pos, this.massKg, 1.0);
            return;
        }
        switch (type.warhead) {
            case HE: 
            case MIRV: 
            case MIRV_HEAVY: {
                this.exploded = true;
                this.discard();
                ConventionalBlast.explode(sl, pos, this.massKg, 1.0);
                break;
            }
            case KINETIC: {
                this.exploded = true;
                this.discard();
                ConventionalBlast.explode(sl, pos.add(0.0, -0.6, 0.0), this.massKg * 0.6, 0.25);
                ConventionalBlast.explode(sl, pos.add(0.0, -3.5, 0.0), this.massKg * 0.5, 0.0);
                break;
            }
            case THERMOBARIC: {
                this.exploded = true;
                this.discard();
                Warheads.thermobaric(sl, pos.add(0.0, 0.8, 0.0), this.massKg);
                break;
            }
            case PENETRATOR: {
                Vec3 dp = Warheads.dig(sl, pos);
                this.setHidden(dp, 14);
                break;
            }
            case CLUSTER: {
                this.setHidden(pos, 50);
            }
        }
    }

    private void setHidden(Vec3 at, int ticks) {
        this.entityData.set(DATA_MODE, 2);
        this.entityData.set(DATA_BURN, false);
        this.detonatePos = at;
        this.detonateAt = ticks;
        this.hiddenTicks = 0;
        this.setPos(at.x, at.y + 1.0, at.z);
    }

    private void hiddenTick(ServerLevel sl, MissileType type) {
        ++this.hiddenTicks;
        ChunkPos a = new ChunkPos(BlockPos.containing((Position)this.detonatePos));
        if (this.hiddenTicks % 10 == 1) {
            sl.getChunkSource().addRegionTicket(TICKET, a, 3, a);
        }
        if (type.warhead == MissileType.Warhead.CLUSTER) {
            Warheads.clusterTick(sl, type, this.detonatePos, this.hiddenTicks, this.massKg / (double)type.warheads);
            if (this.hiddenTicks >= this.detonateAt) {
                this.discard();
            }
        } else if (this.hiddenTicks >= this.detonateAt) {
            this.exploded = true;
            this.discard();
            ConventionalBlast.explode(sl, this.detonatePos, this.massKg, 0.5);
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
        return d < 1048576.0;
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket((Entity)this);
    }

    private static final class RandomSourceHolder {
        final RandomSource r;

        RandomSourceHolder(ServerLevel l) {
            this.r = l.random;
        }

        double next() {
            return this.r.nextDouble();
        }
    }
}

