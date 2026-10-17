package ru.aiforge.tubombers.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.joml.Vector3d;
import org.joml.Vector3f;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.aircraft.FlightModel;
import ru.aiforge.tubombers.fx.ImpactHandler;
import ru.aiforge.tubombers.registry.ModRegistry;

/** Free-fall bomb: same ballistic integrator as the CCIP/CCRP prediction, contact fuze. */
public class BombEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(BombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_VEL = SynchedEntityData.defineId(BombEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(BombEntity.class, EntityDataSerializers.FLOAT);

    public final Vector3d vel = new Vector3d();
    private int ownerId = -1;
    private boolean velInit;
    public float visPitch, visPitchO, visYaw, visYawO;
    private boolean whistle;

    public BombEntity(EntityType<? extends BombEntity> t, Level level) {
        super(t, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static BombEntity create(ServerLevel level, BombType type, Vector3d pos, Vector3d vel, BomberEntity owner) {
        BombEntity b = new BombEntity(ModRegistry.BOMB.get(), level);
        b.setPos(pos.x, pos.y, pos.z);
        b.vel.set(vel);
        b.velInit = true;
        b.ownerId = owner.getId();
        b.entityData.set(DATA_TYPE, type.ordinal());
        b.entityData.set(DATA_VEL, new Vector3f((float) vel.x, (float) vel.y, (float) vel.z));
        b.entityData.set(DATA_SCALE, owner.speedScale());
        b.setYRot(owner.getYRot());
        b.orient();
        b.visPitchO = b.visPitch;
        b.visYawO = b.visYaw;
        return b;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_TYPE, 0);
        entityData.define(DATA_VEL, new Vector3f());
        entityData.define(DATA_SCALE, 0.5f);
    }

    public BombType type() {
        return BombType.byIndex(entityData.get(DATA_TYPE));
    }

    private void orient() {
        double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        visYaw = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
        visPitch = (float) Math.toDegrees(Math.atan2(vel.y, Math.max(1e-3, h)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!velInit) {
            Vector3f v = entityData.get(DATA_VEL);
            vel.set(v.x, v.y, v.z);
            velInit = true;
        }
        visPitchO = visPitch;
        visYawO = visYaw;
        double scale = entityData.get(DATA_SCALE);
        Vector3d p = new Vector3d(getX(), getY(), getZ());
        Vec3 from = position();
        FlightModel.bombStep(p, vel, type(), 0.05, scale);
        Vec3 to = new Vec3(p.x, p.y, p.z);
        orient();
        if (level().isClientSide) {
            setPos(to);
            if (!whistle && tickCount > 15 && ru.aiforge.tubombers.entity.BomberEntity.CLIENT != null) {
                whistle = true;
                BombSounds.start(this);
            }
            return;
        }
        HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
        if (tickCount > 30) {
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level(), this, from, end, new AABB(from, end).inflate(1),
                    e -> e.isPickable() && !e.isSpectator() && !(e instanceof BombEntity) && !isOwnerPart(e));
            if (eh != null) hit = eh;
        }
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 at = hit.getLocation();
            if (hit instanceof BlockHitResult bh) at = at.add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.3));
            ImpactHandler.bomb((ServerLevel) level(), at, type(), this, level().getEntity(ownerId), new Vec3(vel.x, vel.y, vel.z));
            discard();
            return;
        }
        setPos(to);
        if (tickCount > 20 * 120 || getY() < level().getMinBuildHeight() - 32) discard();
    }

    private boolean isOwnerPart(Entity e) {
        Entity o = level().getEntity(ownerId);
        if (o == null) return false;
        if (e == o || e.getVehicle() == o) return true;
        return e instanceof BomberPart bp && bp.getParent() == o;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean tp) {
        // client simulates the same ballistic path; only correct big drift
        if (position().distanceToSqr(x, y, z) > 4) setPos(x, y, z);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 400 * 400;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(DATA_TYPE, t.getInt("type"));
        vel.set(t.getDouble("vx"), t.getDouble("vy"), t.getDouble("vz"));
        entityData.set(DATA_VEL, new Vector3f((float) vel.x, (float) vel.y, (float) vel.z));
        if (t.contains("scale")) entityData.set(DATA_SCALE, t.getFloat("scale"));
        velInit = true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("type", entityData.get(DATA_TYPE));
        t.putDouble("vx", vel.x);
        t.putDouble("vy", vel.y);
        t.putDouble("vz", vel.z);
        t.putFloat("scale", entityData.get(DATA_SCALE));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /** indirection so the common class never touches client sound classes */
    public static final class BombSounds {
        public static java.util.function.Consumer<BombEntity> STARTER;

        static void start(BombEntity b) {
            if (STARTER != null) STARTER.accept(b);
        }
    }
}
