package ru.aiforge.bm21grad.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.ballistics.Ballistics;
import ru.aiforge.bm21grad.fx.GhostManager;
import ru.aiforge.bm21grad.fx.ImpactHandler;
import ru.aiforge.bm21grad.registry.ModRegistry;

import java.util.UUID;

/**
 * 122-mm unguided rocket. Flies the exact same point-mass model as the fire-control solver
 * ({@link Ballistics#step}). When it leaves entity-ticking chunks it continues as a server-side
 * "ghost" (see {@link GhostManager}) so long-range fire works without chunk loaders.
 */
public class RocketEntity extends Projectile {
    private static final EntityDataAccessor<Byte> DATA_TYPE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);

    public double thrust = Ballistics.thrust(1, 1);
    public int age;
    public UUID launcherId;
    public double groundHint = Double.NaN;
    public boolean whistled;
    public boolean roared;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    public static RocketEntity launch(ServerLevel level, RocketType type, Vec3 pos, Vec3 vel, double thrust, Entity owner, Entity launcher) {
        RocketEntity r = new RocketEntity(ModRegistry.ROCKET.get(), level);
        r.setRocketType(type);
        r.setPos(pos.x, pos.y, pos.z);
        r.setDeltaMovement(vel);
        r.thrust = thrust;
        r.setOwner(owner);
        r.launcherId = launcher != null ? launcher.getUUID() : null;
        r.orient();
        r.xRotO = r.getXRot();
        r.yRotO = r.getYRot();
        return r;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_TYPE, (byte) RocketType.HE_FRAG.ordinal());
        entityData.define(DATA_AGE, 0);
    }

    public RocketType getRocketType() {
        return RocketType.byIndex(entityData.get(DATA_TYPE));
    }

    public void setRocketType(RocketType t) {
        entityData.set(DATA_TYPE, (byte) t.ordinal());
    }

    /** Flight age as known by the server (drives motor flame / fins on the client). */
    public int getFlightAge() {
        return entityData.get(DATA_AGE);
    }

    public void orient() {
        Vec3 v = getDeltaMovement();
        double h = v.horizontalDistance();
        setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        setXRot((float) Math.toDegrees(Math.atan2(-v.y, h)));
    }

    @Override
    public void tick() {
        super.tick();
        xRotO = getXRot();
        yRotO = getYRot();
        Vec3 from = position();
        double[] s = {from.x, from.y, from.z, getDeltaMovement().x, getDeltaMovement().y, getDeltaMovement().z};
        int a = level().isClientSide ? getFlightAge() : age;
        Ballistics.step(s, a, thrust);
        Vec3 to = new Vec3(s[0], s[1], s[2]);
        Vec3 vel = new Vec3(s[3], s[4], s[5]);

        if (level().isClientSide) {
            setPos(to.x, to.y, to.z);
            setDeltaMovement(vel);
            orient();
            trail(from, to, a);
            entityData.set(DATA_AGE, a + 1);
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        age++;
        entityData.set(DATA_AGE, age);
        if (!roared) {
            roared = true;
            sl.playSound(null, this, ModRegistry.SND_ROCKET_FLIGHT.get(), SoundSource.HOSTILE, 5f, 0.95f + random.nextFloat() * 0.1f);
        }
        if (to.y < level().getMinBuildHeight() - 64 || age > 3000) {
            discard();
            return;
        }
        if (age > 2 && !sl.isPositionEntityTicking(BlockPos.containing(to))) {
            GhostManager.add(sl, this, to, vel);
            discard();
            return;
        }
        // block hit
        HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 end = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : to;
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level(), this, from, end,
                getBoundingBox().expandTowards(end.subtract(from)).inflate(1.0), this::canHitEntity);
        if (eh != null) hit = eh;
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 p = hit.getLocation();
            if (hit instanceof BlockHitResult bh) p = p.add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.3));
            ImpactHandler.detonate(sl, p, getRocketType(), this, getOwner(), vel);
            discard();
            return;
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(vel);
        orient();

        if (vel.y < 0) {
            int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(to.x), Mth.floor(to.z));
            double agl = to.y - ground;
            if (getRocketType() == RocketType.CLUSTER && agl < 38) {
                ImpactHandler.clusterBurst(sl, to, vel, this, getOwner());
                discard();
                return;
            }
            if (!whistled && agl < -vel.y * 70) {
                whistled = true;
                sl.playSound(null, this, ModRegistry.SND_INCOMING.get(), SoundSource.HOSTILE, 6f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
    }

    private void trail(Vec3 from, Vec3 to, int a) {
        if (ru.aiforge.arsenal.volumetric.client.VolumetricEffects.active()) {
            ru.aiforge.arsenal.volumetric.client.sim.VolumetricFxManager.recordRocket(getId(), to, a < Ballistics.BURN_TICKS + 2 ? to.subtract(from) : Vec3.ZERO);
            return;
        }
        Vec3 d = to.subtract(from);
        Vec3 back = getDeltaMovement().normalize().scale(-1.6);
        boolean burning = a < Ballistics.BURN_TICKS + 2;
        int n = burning ? 6 : 2;
        for (int i = 0; i < n; i++) {
            double f = i / (double) n;
            double x = from.x + d.x * f + back.x, y = from.y + d.y * f + back.y, z = from.z + d.z * f + back.z;
            if (burning) {
                level().addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, x, y, z,
                        (random.nextDouble() - 0.5) * 0.03, 0.01, (random.nextDouble() - 0.5) * 0.03);
                level().addParticle(ParticleTypes.FLAME, x, y, z, back.x * 0.05, back.y * 0.05, back.z * 0.05);
            } else if (a < 200) {
                level().addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), x, y, z, 0, 0.005, 0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (!e.isAlive() || !e.isPickable() || e.isSpectator()) return false;
        if (e instanceof RocketEntity || e instanceof SubmunitionEntity) return false;
        if (age < 30 && launcherId != null) {
            if (launcherId.equals(e.getUUID())) return false;
            Entity v = e.getVehicle();
            if (v != null && launcherId.equals(v.getUUID())) return false;
        }
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 512 * 512;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putByte("Type", entityData.get(DATA_TYPE));
        t.putDouble("Thrust", thrust);
        t.putInt("Age", age);
        if (launcherId != null) t.putUUID("Launcher", launcherId);
        t.putBoolean("Whistled", whistled);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(DATA_TYPE, t.getByte("Type"));
        thrust = t.getDouble("Thrust");
        age = t.getInt("Age");
        if (t.hasUUID("Launcher")) launcherId = t.getUUID("Launcher");
        whistled = t.getBoolean("Whistled");
        roared = true;
    }
}
