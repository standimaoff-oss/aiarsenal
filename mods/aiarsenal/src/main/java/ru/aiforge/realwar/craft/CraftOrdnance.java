package ru.aiforge.realwar.craft;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.registry.ModSounds;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/**
 * Helicopter / naval ordnance: guided ATGMs (Vikhr beam rider, Hellfire semi-active laser), unguided S-8 / Hydra
 * rockets, acoustic homing torpedo and IR decoy flares. Velocities in m/s internally, 1 block = 1 m.
 */
public class CraftOrdnance extends Entity {
    public enum Type {
        //       speed  boost burn  life  turn  tnt   direct grav  model
        VIKHR(   500,   60,    20,  340,  2.5f, 4.0,  260f,  0.0, "vikhr"),
        HELLFIRE(425,   40,    30,  400,  3.0f, 4.5,  260f,  0.0, "hellfire"),
        S8(      450,   40,    14,  140,  0f,   1.2,  45f,   1.0, "s8"),
        HYDRA(   600,   40,    22,  150,  0f,   1.4,  50f,   1.0, "hydra"),
        TORPEDO( 25.7,  12,    10, 2400,  1.2f, 300,  1500f, 0.0, "torpedo"),
        FLARE(   25,    25,     0,   70,  0f,   0,    0f,    0.3, ""),
        /** 3M-14 Kalibr land-attack cruise missile from a submerged 533 mm tube (6.2 m x 0.533 m - shares the
         *  torpedo-tube sized body node): capsule ascent, booster at the surface, 40 m terrain-following cruise. */
        KALIBR(  240,   30,    60, 1400,  2.0f, 450,  2000f, 0.0, "torpedo");

        public final double speed, boost, gravity, tntKg;
        public final int burn, life;
        public final float turn, direct;
        public final String node;

        Type(double speed, double boost, int burn, int life, float turn, double tntKg, float direct, double gravity, String node) {
            this.speed = speed;
            this.boost = boost;
            this.burn = burn;
            this.life = life;
            this.turn = turn;
            this.tntKg = tntKg;
            this.direct = direct;
            this.gravity = gravity;
            this.node = node;
        }
    }

    private static final EntityDataAccessor<Byte> TYPE = SynchedEntityData.defineId(CraftOrdnance.class, EntityDataSerializers.BYTE);

    /** Velocity m/s. */
    public Vec3 v = Vec3.ZERO;
    private int sourceId = -1;
    private int operatorId = -1;
    private int targetId = -1;
    @Nullable
    private Vec3 spot;
    private int ignitedAt = -1;
    /** Test hook: fixed beam origin+direction for the Vikhr (no operator needed). */
    @Nullable
    private Vec3 beamO, beamD;

    public static int detonations;
    public static String lastDetonation = "";
    public static Vec3 lastDetonationPos = Vec3.ZERO;

    public CraftOrdnance(EntityType<? extends CraftOrdnance> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static CraftOrdnance launch(ServerLevel sl, Type type, @Nullable Entity source, Vec3 pos, Vec3 dir, Vec3 inheritPerTick, int operatorId) {
        CraftOrdnance e = new CraftOrdnance(CraftRegistry.ORDNANCE.get(), sl);
        e.entityData.set(TYPE, (byte) type.ordinal());
        e.setPos(pos.x, pos.y, pos.z);
        e.sourceId = source != null ? source.getId() : -1;
        e.operatorId = operatorId;
        e.v = dir.normalize().scale(type.boost).add(inheritPerTick.scale(20.0));
        e.setDeltaMovement(e.v.scale(0.05));
        e.faceVelocity();
        e.xRotO = e.getXRot();
        e.yRotO = e.getYRot();
        sl.addFreshEntity(e);
        return e;
    }

    public Type type() {
        int i = this.entityData.get(TYPE);
        Type[] t = Type.values();
        return t[Mth.clamp(i, 0, t.length - 1)];
    }

    public int sourceId() { return this.sourceId; }

    public void setBeam(Vec3 origin, Vec3 dir) {
        this.beamO = origin;
        this.beamD = dir.normalize();
    }

    public void setSpot(Vec3 spot) {
        this.spot = spot;
    }

    public void setTargetId(int id) {
        this.targetId = id;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TYPE, (byte) 0);
    }

    @Override
    public void tick() {
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        super.tick();
        Type t = this.type();
        if (this.level().isClientSide) {
            // client: integrate the synced per-tick motion, effects
            Vec3 d = this.getDeltaMovement();
            this.setPos(this.getX() + d.x, this.getY() + d.y, this.getZ() + d.z);
            if (d.lengthSqr() > 1e-6) this.faceVelocity();
            CraftEntity.clientOrdnanceFx.accept(this);
            return;
        }
        ServerLevel sl = (ServerLevel) this.level();
        double dt = 0.05;
        boolean inWater = this.level().getFluidState(this.blockPosition()).is(FluidTags.WATER);
        double sp = this.v.length();
        Vec3 dir = sp > 1e-6 ? this.v.scale(1 / sp) : new Vec3(0, 0, 1);
        switch (t) {
            case VIKHR, HELLFIRE -> {
                if (this.tickCount < t.burn) sp = Math.min(t.speed, sp + (t.speed - t.boost) / t.burn);
                Vec3 want = t == Type.VIKHR ? this.beamGuidance() : this.laserGuidance(sl);
                if (want != null && this.tickCount > 3) dir = turnToward(dir, want, Math.toRadians(t.turn));
                this.v = dir.scale(sp);
            }
            case S8, HYDRA -> {
                if (this.tickCount < t.burn) sp = Math.min(t.speed, sp + (t.speed - t.boost) / t.burn);
                else sp *= 0.996;
                this.v = dir.scale(sp).add(0, -9.81 * dt * t.gravity, 0);
            }
            case TORPEDO -> {
                if (inWater) {
                    sp = Math.min(t.speed, sp + 3.0);
                    Vec3 want = this.torpedoGuidance(sl);
                    if (want == null) {
                        // run level at ~4 m below the surface
                        double surf = this.surfaceAbove();
                        double err = Double.isNaN(surf) ? 0 : (surf - 4.0) - this.getY();
                        want = new Vec3(dir.x, Mth.clamp(err * 0.08, -0.25, 0.25), dir.z).normalize();
                    }
                    dir = turnToward(dir, want, Math.toRadians(t.turn));
                    this.v = dir.scale(sp);
                } else {
                    this.v = this.v.add(0, -9.81 * dt, 0).scale(0.99);  // air drop / broach
                }
            }
            case KALIBR -> {
                if (inWater && this.ignitedAt < 0) {
                    // capsule ascent: buoyant, nose swings up, no motor underwater
                    dir = turnToward(dir, new Vec3(dir.x * 0.3, 1, dir.z * 0.3).normalize(), Math.toRadians(4.0));
                    this.v = dir.scale(Math.min(14.0, sp + 1.0));
                } else {
                    if (this.ignitedAt < 0) this.ignitedAt = this.tickCount;
                    int age = this.tickCount - this.ignitedAt;
                    sp = age < t.burn ? Math.min(t.speed, sp + (t.speed - t.boost) / t.burn + 2.0) : t.speed;
                    Vec3 want = this.cruiseGuidance(sl, age);
                    if (want != null) dir = turnToward(dir, want, Math.toRadians(age < 30 ? 4.0 : t.turn));
                    this.v = dir.scale(sp);
                }
            }
            case FLARE -> {
                this.v = this.v.scale(0.88).add(0, -9.81 * dt * t.gravity, 0);
                if (inWater) this.discard();
            }
        }
        this.setDeltaMovement(this.v.scale(dt));
        if (t != Type.FLARE && this.tickCount > 1) {
            HitResult hit = this.hitScan();
            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                Vec3 at = hit.getLocation();
                this.setPos(at.x, at.y, at.z);
                this.detonate(sl, hit instanceof EntityHitResult eh ? eh.getEntity() : null);
                return;
            }
        } else if (t == Type.FLARE) {
            BlockHitResult bh = this.level().clip(new ClipContext(this.position(), this.position().add(this.getDeltaMovement()),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (bh.getType() != HitResult.Type.MISS) {
                this.v = Vec3.ZERO;
                this.setDeltaMovement(Vec3.ZERO);
            }
        }
        if (this.tickCount > t.life || this.getY() < this.level().getMinBuildHeight() - 16) {
            if (t == Type.FLARE) this.discard();
            else this.detonate(sl, null);
            return;
        }
        if (t == Type.TORPEDO && this.tickCount % 20 == 0) sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, this.getX(), this.getY(), this.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
        Vec3 d = this.getDeltaMovement();
        this.setPos(this.getX() + d.x, this.getY() + d.y, this.getZ() + d.z);
        this.faceVelocity();
    }

    private double surfaceAbove() {
        BlockPos.MutableBlockPos mp = this.blockPosition().mutable();
        for (int i = 0; i < 64; i++) {
            if (!this.level().getFluidState(mp).is(FluidTags.WATER)) return mp.getY();
            mp.move(0, 1, 0);
        }
        return Double.NaN;
    }

    @Nullable
    private HitResult hitScan() {
        Vec3 a = this.position(), b = a.add(this.getDeltaMovement());
        ClipContext.Fluid fl = this.type() == Type.TORPEDO ? ClipContext.Fluid.NONE : ClipContext.Fluid.NONE;
        BlockHitResult bh = this.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, fl, this));
        Vec3 end = bh.getType() == HitResult.Type.MISS ? b : bh.getLocation();
        AABB box = this.getBoundingBox().expandTowards(end.subtract(a)).inflate(1.0);
        EntityHitResult eh = ru.aiforge.realwar.damage.VehicleHit.first(this.level(), this, a, end, 0.3, this::canHit);   // r20: real vehicle shapes
        if (eh != null) return eh;
        // water entry of air-launched rockets/missiles detonates them
        if (this.type() != Type.TORPEDO && !(this.type() == Type.KALIBR && this.ignitedAt < 0)) {
            BlockHitResult wh = this.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, this));
            if (wh.getType() != HitResult.Type.MISS) return wh;
        }
        return bh.getType() == HitResult.Type.MISS ? null : bh;
    }

    private boolean canHit(Entity e) {
        if (!e.isPickable() || e instanceof CraftOrdnance || e.isSpectator()) return false;
        if (e.getId() == this.sourceId) return false;
        Entity root = e.getRootVehicle();
        if (root != null && root.getId() == this.sourceId) return false;
        return this.tickCount > 2 || !(e instanceof CraftEntity);
    }

    private void detonate(ServerLevel sl, @Nullable Entity direct) {
        if (this.isRemoved()) return;
        Type t = this.type();
        detonations++;
        lastDetonationPos = this.position();
        lastDetonation = t + " age " + this.tickCount + " at " + this.blockPosition().toShortString() + " direct " + direct;
        this.discard();
        if (direct != null) {
            direct.invulnerableTime = 0;
            Vec3 od = this.getDeltaMovement();
            String title = switch (t) {
                case VIKHR -> "9M127 Vikhr";
                case HELLFIRE -> "AGM-114K Hellfire";
                case S8 -> "S-8KOM";
                case HYDRA -> "Hydra 70 M151";
                case KALIBR -> "3M-14 Kalibr";
                default -> "53-65K";
            };
            ru.aiforge.realwar.damage.HitContext.hurt(direct, ModDamage.source(sl, ModDamage.ORDNANCE), t.direct, this.position(), od.lengthSqr() > 1e-8 ? od.normalize() : null,
                    ru.aiforge.realwar.damage.HitContext.Kind.HE, Math.max(0.5, t.direct / 6.0),
                    new ru.aiforge.realwar.damage.HitContext.Shot(this.sourceId, ru.aiforge.realwar.damage.Armour.Ammo.ofCraft(t.name(), title, t.tntKg), od.length() * 20.0, null));
        }
        if (t == Type.TORPEDO) {
            // underwater blast: a direct hit breaks the keel, the gas bubble damages everything nearby
            for (Entity e : sl.getEntities(this, this.getBoundingBox().inflate(14), x -> x instanceof CraftEntity || x instanceof VehicleEntity)) {
                if (e == direct) continue;
                double d = e.position().distanceTo(this.position());
                e.hurt(ModDamage.source(sl, ModDamage.BLAST), (float) (t.direct * Mth.clamp(1.0 - d / 14.0, 0, 1) * 0.6));
            }
            sl.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 4, this.getZ(), 60, 3, 6, 3, 0.2);
            sl.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 6, this.getZ(), 300, 4, 3, 4, 0.5);
            ConventionalBlast.explode(sl, this.position(), 60.0, 0.4);
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BLAST_LARGE.get(), SoundSource.HOSTILE, 8f, 0.7f);
            return;
        }
        if (t == Type.KALIBR) {
            ConventionalBlast.explode(sl, this.position(), t.tntKg, 0.8);
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BLAST_LARGE.get(), SoundSource.HOSTILE, 10f, 0.8f);
            return;
        }
        ConventionalBlast.explode(sl, this.position(), t.tntKg, t == Type.S8 || t == Type.HYDRA ? 1.1 : 0.5);
        sl.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BLAST_SMALL.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
    }

    // --------------------------------------------------------------------------------------- guidance

    /** Vikhr: laser beam riding - the missile steers onto the operator's line of sight, 40 m ahead of itself. */
    @Nullable
    private Vec3 beamGuidance() {
        Vec3 o, d;
        if (this.beamO != null) {
            o = this.beamO;
            d = this.beamD;
        } else {
            Entity op = this.operatorId >= 0 ? this.level().getEntity(this.operatorId) : null;
            if (op == null || !op.isAlive() || op.getRootVehicle().getId() != this.sourceId) return null;
            o = op.getEyePosition();
            d = op.getLookAngle();
        }
        Vec3 rel = this.position().subtract(o);
        double along = rel.dot(d);
        Vec3 aim = o.add(d.scale(Math.max(along, 0) + 40.0));
        return aim.subtract(this.position()).normalize();
    }

    /** Kalibr: zoom-climb to ~40 m over the terrain/sea, cruise, terminal dive onto the designated point. */
    @Nullable
    private Vec3 cruiseGuidance(ServerLevel sl, int age) {
        if (this.spot == null) return null;
        Vec3 p = this.position();
        Vec3 to = this.spot.subtract(p);
        double hd = Math.sqrt(to.x * to.x + to.z * to.z);
        if (hd < 450) return to.normalize();                      // terminal dive
        Vec3 h = new Vec3(to.x / hd, 0, to.z / hd);
        Vec3 ahead = p.add(h.scale(60));
        int gx = net.minecraft.util.Mth.floor(ahead.x), gz = net.minecraft.util.Mth.floor(ahead.z);
        double ground = sl.hasChunkAt(BlockPos.containing(ahead)) ? sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, gx, gz) : p.y - 40;
        double wantY = Math.max(ground, sl.getSeaLevel()) + 40.0;
        double climb = net.minecraft.util.Mth.clamp((wantY - p.y) * 0.04, age < 40 ? 0.2 : -0.35, 0.6);
        return h.add(0, climb, 0).normalize();
    }

    /** Hellfire: semi-active laser - homes on the spot the gunner illuminates (lock-on after launch, lofted). */
    @Nullable
    private Vec3 laserGuidance(ServerLevel sl) {
        Entity op = this.operatorId >= 0 ? sl.getEntity(this.operatorId) : null;
        if (this.tickCount % 2 == 0 && op != null && op.isAlive() && op.getRootVehicle().getId() == this.sourceId) {
            Vec3 eye = op.getEyePosition(), look = op.getLookAngle(), end = eye.add(look.scale(1000));
            BlockHitResult bh = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, op));
            Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
            Entity src = sl.getEntity(this.sourceId);
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(sl, op, eye, stop, new AABB(eye, stop).inflate(2),
                    x -> x.isPickable() && x != src && x.getRootVehicle() != src && !(x instanceof CraftOrdnance));
            this.spot = eh != null ? eh.getEntity().getBoundingBox().getCenter() : (bh.getType() == HitResult.Type.MISS ? null : stop);
            if (eh != null) this.targetId = eh.getEntity().getId();
        }
        Vec3 tp = this.spot;
        if (this.targetId >= 0) {
            Entity t = sl.getEntity(this.targetId);
            if (t != null && t.isAlive() && this.spot != null && t.getBoundingBox().getCenter().distanceTo(this.spot) < 6) tp = t.getBoundingBox().getCenter();
        }
        if (tp == null) return null;
        Vec3 to = tp.subtract(this.position());
        double hd = Math.sqrt(to.x * to.x + to.z * to.z);
        if (hd > 250) tp = tp.add(0, Math.min(60, hd * 0.12), 0);     // loft for a top attack
        return tp.subtract(this.position()).normalize();
    }

    /** Torpedo: active/passive acoustic seeker, 45 deg cone, 300 m, after a 40-tick safe run. */
    @Nullable
    private Vec3 torpedoGuidance(ServerLevel sl) {
        if (this.tickCount < 40) return null;
        Entity t = this.targetId >= 0 ? sl.getEntity(this.targetId) : null;
        if (t == null || !t.isAlive()) {
            this.targetId = -1;
            if (this.tickCount % 10 != 0) return null;
            Vec3 dir = this.v.normalize();
            double best = 1e9;
            List<Entity> list = sl.getEntities(this, this.getBoundingBox().inflate(300), e -> (e instanceof CraftEntity c && !c.kind.isAir()) && e.getId() != this.sourceId);
            for (Entity e : list) {
                Vec3 to = e.getBoundingBox().getCenter().subtract(this.position());
                double dist = to.length();
                if (dist > 300 || to.normalize().dot(dir) < Math.cos(Math.toRadians(45))) continue;
                if (dist < best) { best = dist; t = e; }
            }
            if (t == null) return null;
            this.targetId = t.getId();
        }
        Vec3 c = t instanceof CraftEntity ce ? ce.localToWorld(0, ce.kind.water.draft() * 0.5, 0) : t.position();
        return c.subtract(this.position()).normalize();
    }

    public static Vec3 turnToward(Vec3 dir, Vec3 want, double maxRad) {
        double cos = Mth.clamp(dir.dot(want), -1.0, 1.0);
        double ang = Math.acos(cos);
        if (ang <= maxRad || ang < 1e-6) return want;
        double k = maxRad / ang;
        return dir.scale(1 - k).add(want.scale(k)).normalize();
    }

    private void faceVelocity() {
        Vec3 d = this.getDeltaMovement();
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        if (d.lengthSqr() < 1e-9) return;
        this.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        this.setXRot((float) -Math.toDegrees(Math.atan2(d.y, h)));
    }

    /** Is this a burning IR decoy? */
    public boolean isHotFlare() {
        return this.type() == Type.FLARE && this.tickCount < Type.FLARE.life;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 300 * 300;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
