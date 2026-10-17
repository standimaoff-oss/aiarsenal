package ru.aiforge.realwar.strike;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

/**
 * Display-only strike aircraft (reply 19). The flight path is owned by {@link StrikeManager} (it keeps flying through unloaded
 * chunks); this entity only exists while its position is entity-ticking. Both sides advance it by the synced per-tick velocity,
 * the server additionally snaps it to the manager's path every tick. Never saved.
 */
public class StrikePlaneEntity extends Entity {
    public enum Kind {
        SU34("models/glb/su34.glb", 23.3f, 14.7f),
        TU95("glb/tu95ms.glb", 49.5f, 50.0f),
        TU160("glb/tu160.glb", 54.1f, 35.6f);

        public final ResourceLocation model;
        public final float length, span;

        Kind(String path, float length, float span) {
            this.model = new ResourceLocation("aiarsenal", path);
            this.length = length;
            this.span = span;
        }
    }

    public static final int F_DOORS = 1, F_AB = 2;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(StrikePlaneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> VEL = SynchedEntityData.defineId(StrikePlaneEntity.class, EntityDataSerializers.VECTOR3);
    /** AirBombType ordinal of the hung stores, -1 none / internal */
    private static final EntityDataAccessor<Integer> LOAD = SynchedEntityData.defineId(StrikePlaneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STORES = SynchedEntityData.defineId(StrikePlaneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(StrikePlaneEntity.class, EntityDataSerializers.BYTE);

    /** server: game time of the last manager update (orphans are removed) */
    public long touched;
    /** client animation state */
    public float doors, doorsO, prop, propO, bankO, bank;
    public boolean soundStarted;

    public StrikePlaneEntity(EntityType<? extends StrikePlaneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, 0);
        entityData.define(VEL, new Vector3f());
        entityData.define(LOAD, -1);
        entityData.define(STORES, 0);
        entityData.define(FLAGS, (byte) 0);
    }

    public Kind kind() {
        Kind[] v = Kind.values();
        return v[Math.floorMod(entityData.get(KIND), v.length)];
    }

    public void setKind(Kind k) {
        entityData.set(KIND, k.ordinal());
    }

    public Vec3 vel() {
        Vector3f v = entityData.get(VEL);
        return new Vec3(v.x, v.y, v.z);
    }

    public void setVel(Vec3 v) {
        Vector3f c = entityData.get(VEL);
        if (Math.abs(c.x - v.x) + Math.abs(c.y - v.y) + Math.abs(c.z - v.z) > 1e-3)
            entityData.set(VEL, new Vector3f((float) v.x, (float) v.y, (float) v.z));
        float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
        float pitch = (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
        setYRot(yaw);
        setXRot(pitch);
    }

    public int load() {
        return entityData.get(LOAD);
    }

    public void setLoad(int airBombOrdinal, int stores) {
        entityData.set(LOAD, airBombOrdinal);
        if (entityData.get(STORES) != stores) entityData.set(STORES, stores);
    }

    public int stores() {
        return entityData.get(STORES);
    }

    public boolean flag(int f) {
        return (entityData.get(FLAGS) & f) != 0;
    }

    public void setFlag(int f, boolean on) {
        byte b = entityData.get(FLAGS);
        byte n = (byte) (on ? b | f : b & ~f);
        if (n != b) entityData.set(FLAGS, n);
    }

    @Override
    public void tick() {
        xo = getX();
        yo = getY();
        zo = getZ();
        yRotO = getYRot();
        xRotO = getXRot();
        tickCount++;
        Vec3 v = vel();
        if (level().isClientSide) {
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            setVel(v);
            doorsO = doors;
            doors += Math.max(-0.05f, Math.min(0.05f, (flag(F_DOORS) ? 1f : 0f) - doors));
            propO = prop;
            prop += 0.37f;
            bankO = bank;
        } else if (tickCount > 3600 || level().getGameTime() - touched > 40) {
            discard();
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        // self-propelled on the client; only correct real drift
        if (distanceToSqr(x, y, z) > 36.0) {
            setPos(x, y, z);
            xo = x;
            yo = y;
            zo = z;
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 1200.0 * 1200.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
