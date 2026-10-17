package ru.aiforge.arsenal.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import ru.aiforge.arsenal.ArsenalConfig;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Tickets;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Base for every arsenal entity: synced kind / health / roll / state, owner, Boat-style client interpolation, swept
 * collision for fast movers and chunk tickets so that a unit keeps flying far away from players.
 * <p>Orientation: {@code getYRot()} = vanilla yaw (0 = +Z), {@link #pitch()} = nose-up degrees (stored as -xRot so the
 * vanilla move packets carry it), {@link #roll()} = right-wing-down degrees.
 */
public abstract class ArsEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(ArsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HP = SynchedEntityData.defineId(ArsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(ArsEntity.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(ArsEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_AUX = SynchedEntityData.defineId(ArsEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> DATA_F1 = SynchedEntityData.defineId(ArsEntity.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> DATA_F2 = SynchedEntityData.defineId(ArsEntity.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Optional<BlockPos>> DATA_TARGET = SynchedEntityData.defineId(ArsEntity.class,
            EntityDataSerializers.OPTIONAL_BLOCK_POS);

    @Nullable
    public UUID owner;
    /** Client: previous roll for interpolation. */
    public float rollO;
    public int age;
    private double lx, ly, lz;
    private float lyaw, lpitch;
    private int lsteps;
    /** Controlled by a remote operator (server). */
    @Nullable
    public UUID operator;

    protected ArsEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_KIND, 0);
        entityData.define(DATA_HP, 10f);
        entityData.define(DATA_ROLL, 0f);
        entityData.define(DATA_STATE, 0);
        entityData.define(DATA_AUX, 0);
        entityData.define(DATA_F1, 0f);
        entityData.define(DATA_F2, 0f);
        entityData.define(DATA_TARGET, Optional.empty());
    }

    // ------------------------------------------------------------------ accessors
    public Kind kind() {
        return Kind.byIndex(entityData.get(DATA_KIND));
    }

    public void setKind(Kind k) {
        entityData.set(DATA_KIND, k.ordinal());
        entityData.set(DATA_HP, k.hp);
        refreshDimensions();
    }

    public float hp() {
        return entityData.get(DATA_HP);
    }

    public float roll() {
        return entityData.get(DATA_ROLL);
    }

    public void setRoll(float r) {
        entityData.set(DATA_ROLL, r);
    }

    public float pitch() {
        return -getXRot();
    }

    public void setPitch(float p) {
        setXRot(Mth.clamp(-p, -90f, 90f));
    }

    public int state() {
        return entityData.get(DATA_STATE);
    }

    public void setState(int s) {
        entityData.set(DATA_STATE, s);
    }

    public int aux() {
        return entityData.get(DATA_AUX);
    }

    public void setAux(int a) {
        entityData.set(DATA_AUX, a);
    }

    public float f1() {
        return entityData.get(DATA_F1);
    }

    public float f2() {
        return entityData.get(DATA_F2);
    }

    public void setF1(float v) {
        entityData.set(DATA_F1, v);
    }

    public void setF2(float v) {
        entityData.set(DATA_F2, v);
    }

    @Nullable
    public BlockPos target() {
        return entityData.get(DATA_TARGET).orElse(null);
    }

    public void setTarget(@Nullable BlockPos p) {
        entityData.set(DATA_TARGET, Optional.ofNullable(p));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_KIND.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        Kind k = kind();
        return EntityDimensions.scalable(k.width, k.height);
    }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dim) {
        // sensor position above the entity origin (model bottom): recon gimbal ball under the nose, FPV camera on the nose.
        // Must stay clearly above the ground plane, otherwise a parked drone's camera sees the terrain edge-on.
        Kind k = kind();
        return switch (k.cat) {
            case RECON -> Math.max(0.45f, k.lift() * 0.55f);
            case FPV -> k.lift() + 0.06f;
            default -> Math.max(0.3f, k.lift());
        };
    }

    /** World speed scale (real m/s -> blocks/tick multiply by scale/20). */
    public static float scale() {
        return ArsenalConfig.SPEED_SCALE.get().floatValue();
    }

    /** Real speed in m/s to blocks per tick. */
    public static double bpt(double mps) {
        return mps * scale() / 20.0;
    }

    /** Unit vector for vanilla yaw and nose-up pitch (degrees). */
    public static Vec3 dir(float yaw, float pitchUp) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitchUp);
        return new Vec3(-Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    public static float yawOf(Vec3 v) {
        return (float) Math.toDegrees(Math.atan2(-v.x, v.z));
    }

    public static float pitchOf(Vec3 v) {
        return (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        age++;
        rollO = roll();
        if (level().isClientSide) {
            if (lsteps > 0) {
                double f = 1.0 / lsteps;
                setPos(getX() + (lx - getX()) * f, getY() + (ly - getY()) * f, getZ() + (lz - getZ()) * f);
                setYRot(getYRot() + Mth.wrapDegrees(lyaw - getYRot()) * (float) f);
                setXRot(getXRot() + (lpitch - getXRot()) * (float) f);
                lsteps--;
            } else if (!isNoGravityClientStill()) {
                setPos(getX() + getDeltaMovement().x, getY() + getDeltaMovement().y, getZ() + getDeltaMovement().z);
            }
            clientTick();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (keepsChunks() && (age % 5 == 1)) Tickets.keepPath(sl, position(), getDeltaMovement());
        serverTick(sl);
    }

    /** Client extrapolates with the synced velocity between position packets unless stationary. */
    protected boolean isNoGravityClientStill() {
        return getDeltaMovement().lengthSqr() < 1e-6;
    }

    protected void clientTick() {
    }

    protected abstract void serverTick(ServerLevel sl);

    /** Whether this entity keeps its chunks loaded (flying units). */
    protected boolean keepsChunks() {
        return getDeltaMovement().lengthSqr() > 0.01;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lx = x;
        ly = y;
        lz = z;
        lyaw = yaw;
        lpitch = pitch;
        lsteps = Math.max(2, steps);
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        setDeltaMovement(x, y, z);
    }

    /**
     * Swept move by {@code v}: returns the first block / entity hit (position is set to the hit point) or null after
     * moving the full distance.
     */
    @Nullable
    protected HitResult sweep(Vec3 v, boolean entities) {
        Vec3 a = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 b = a.add(v);
        BlockHitResult bh = level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 end = bh.getType() == HitResult.Type.MISS ? b : bh.getLocation();
        if (entities) {
            AABB box = getBoundingBox().expandTowards(v).inflate(1.0);
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level(), this, a, end, box, this::canHitEntity);
            if (eh != null) {
                setPos(eh.getLocation().subtract(0, getBbHeight() * 0.5, 0));
                return eh;
            }
        }
        if (bh.getType() != HitResult.Type.MISS) {
            setPos(bh.getLocation().subtract(0, getBbHeight() * 0.5, 0));
            return bh;
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        return null;
    }

    protected boolean canHitEntity(Entity e) {
        if (!e.isPickable() || e.isSpectator() || e == this) return false;
        if (owner != null && e.getUUID().equals(owner) && age < 40) return false;
        if (e instanceof ArsEntity a && a.kind().cat == Kind.Cat.LAUNCHER && age < 40) return false;
        return !(e instanceof ru.aiforge.tubombers.entity.BomberEntity) || age > 60;
    }

    /** Ground height (top non-leaf solid block +1) at x/z, from the heightmap; loads the chunk if needed. */
    public static int ground(Level lv, double x, double z) {
        return lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /** Max terrain height along a straight line ahead (terrain-following look-ahead). */
    public static int groundAhead(Level lv, Vec3 p, Vec3 dirH, double dist, double step) {
        int h = ground(lv, p.x, p.z);
        for (double s = step; s <= dist; s += step) h = Math.max(h, ground(lv, p.x + dirH.x * s, p.z + dirH.z * s));
        return h;
    }

    // ------------------------------------------------------------------ damage
    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (isInvulnerableTo(src) || level().isClientSide || isRemoved()) return false;
        if (src.getEntity() instanceof ServerPlayer sp && sp.isCreative() && sp.isShiftKeyDown() && kind().cat != Kind.Cat.NUKE) {
            onPickedUp(sp);
            return true;
        }
        float hp = hp() - amount;
        entityData.set(DATA_HP, hp);
        markHurt();
        if (hp <= 0) onDestroyed(src);
        return true;
    }

    /** Creative sneak-attack: remove without detonation (returns the item where applicable). */
    protected void onPickedUp(ServerPlayer p) {
        discard();
    }

    /** Shot down. */
    protected abstract void onDestroyed(DamageSource src);

    @Nullable
    public Entity ownerEntity() {
        return owner == null || !(level() instanceof ServerLevel sl) ? null : sl.getEntity(owner);
    }

    // ------------------------------------------------------------------ persistence
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(DATA_KIND, t.getInt("Kind"));
        entityData.set(DATA_HP, t.contains("Hp") ? t.getFloat("Hp") : kind().hp);
        entityData.set(DATA_STATE, t.getInt("State"));
        entityData.set(DATA_AUX, t.getInt("Aux"));
        entityData.set(DATA_ROLL, t.getFloat("Roll"));
        if (t.hasUUID("Owner")) owner = t.getUUID("Owner");
        if (t.contains("Tgt")) setTarget(BlockPos.of(t.getLong("Tgt")));
        age = t.getInt("Age");
        readMore(t);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("Kind", kind().ordinal());
        t.putFloat("Hp", hp());
        t.putInt("State", state());
        t.putInt("Aux", aux());
        t.putFloat("Roll", roll());
        if (owner != null) t.putUUID("Owner", owner);
        if (target() != null) t.putLong("Tgt", target().asLong());
        t.putInt("Age", age);
        saveMore(t);
    }

    protected void readMore(CompoundTag t) {
    }

    protected void saveMore(CompoundTag t) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 512 * 512;
    }
}
