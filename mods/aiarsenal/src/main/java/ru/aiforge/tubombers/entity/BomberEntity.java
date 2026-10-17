package ru.aiforge.tubombers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import ru.aiforge.tubombers.Config;
import ru.aiforge.tubombers.aircraft.AircraftData;
import ru.aiforge.tubombers.aircraft.AircraftType;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.aircraft.FlightModel;
import ru.aiforge.tubombers.fx.ImpactHandler;
import ru.aiforge.tubombers.fx.LevelEnv;
import ru.aiforge.tubombers.glb.GlbPose;
import ru.aiforge.tubombers.item.BombItem;
import ru.aiforge.tubombers.network.CrewSyncPacket;
import ru.aiforge.tubombers.network.ModNetwork;
import ru.aiforge.tubombers.registry.ModRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tu-95MS / Tu-160 strategic bomber. Server-authoritative flight model; the crew are passengers that walk
 * inside the fuselage (local model-space positions), climb the entrance ladder and occupy crew stations.
 */
public class BomberEntity extends Entity {
    // ------------------------------------------------------------------ synced data
    private static final EntityDataAccessor<Quaternionf> DATA_Q = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Vector3f> DATA_VEL = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_THROTTLE = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_THRUST = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LOAD = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CTRL = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TARGET = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    public static final int F_GEAR_UP = 1, F_BAY = 2, F_ENGINES = 4, F_LIGHTS = 8, F_BRAKES = 16, F_DESTROYED = 32,
            F_GROUND = 64, F_STALL = 128, F_DOOR = 256;
    private static final int SH_FLAPS = 10, SH_SWEEP = 12, SH_MODE = 14; // 2 bits each

    public static final int MODE_SINGLE = 0, MODE_SERIES = 1, MODE_SALVO = 2;
    public static final float[] SWEEP_LEVELS = {0f, 0.333f, 1f}; // 20 deg, 35 deg, 65 deg
    public static final float[] FLAP_LEVELS = {0f, 0.5f, 1f};

    // ------------------------------------------------------------------ actions (CrewActionPacket)
    public static final int A_SIT = 0, A_STAND = 1, A_EXIT = 2, A_GEAR = 3, A_FLAPS = 4, A_BAY = 5, A_SWEEP = 6, A_LIGHTS = 7,
            A_ENGINES = 8, A_RELEASE = 9, A_MODE = 10, A_LOAD = 11, A_TARGET = 12, A_CLEAR_TARGET = 13, A_BAIL = 14;

    /** Client-only behaviour injected by the client setup (walking input, sounds). */
    public interface ClientHooks {
        void walk(BomberEntity b, Crew c, Entity passenger);

        void tick(BomberEntity b);

        boolean isLocal(Entity e);
    }

    public static ClientHooks CLIENT;

    public final AircraftType kind;
    public final AircraftData data;
    public final FlightModel.State st = new FlightModel.State();
    public final FlightModel.Inputs in = new FlightModel.Inputs();
    private final FlightModel.Config cfg = new FlightModel.Config();
    public final Map<Integer, Crew> crew = new LinkedHashMap<>();
    private final BomberPart[] parts;
    private GlbPose physPose;
    private Vector3f[] hullNow;
    private float hullSweep = -1;
    private boolean initialized;
    private boolean positionAfterLoad;
    public boolean allowDismount;
    private boolean crewDirty;
    private int lastInputTick = -100, consentTick = -100;
    private int queueLeft, queueTimer;
    private boolean ccrpArmed;
    private int destroyedTicks;
    public int lastReleaseTick = -100;
    /** statistics for tests / status */
    public int releasedTotal;

    // animated mechanisation, both sides
    public float gear, gearO, bay, bayO, flaps, flapsO, sweep, sweepO, door = 1, doorO = 1;
    // client smoothing
    public final Quaternionf qPrev = new Quaternionf(), qCur = new Quaternionf();
    public float propAngle, propAngleO;
    private double lerpX, lerpY, lerpZ;
    private int lerpSteps;
    public int clientAge;

    public BomberEntity(EntityType<? extends BomberEntity> type, Level level) {
        super(type, level);
        this.kind = type == ModRegistry.TU160.get() ? AircraftType.TU160 : AircraftType.TU95;
        this.data = AircraftData.get(kind);
        List<BomberPart> list = new ArrayList<>();
        for (float[] p : data.parts)
            list.add(new BomberPart(this, p[0], p[1], p[2], p.length > 4 ? p[4] : AircraftData.CELL, p[3]));
        parts = list.toArray(new BomberPart[0]);
        noPhysics = true;
        blocksBuilding = false;
        setNoGravity(true);
    }

    // ================================================================== data
    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_Q, new Quaternionf());
        entityData.define(DATA_VEL, new Vector3f());
        entityData.define(DATA_FLAGS, F_ENGINES | F_DOOR | F_GROUND | F_BRAKES);
        entityData.define(DATA_THROTTLE, 0f);
        entityData.define(DATA_THRUST, 0f);
        entityData.define(DATA_LOAD, 0);
        entityData.define(DATA_HEALTH, 400f);
        entityData.define(DATA_SCALE, 0.5f);
        entityData.define(DATA_TARGET, Optional.empty());
        entityData.define(DATA_CTRL, 0);
    }

    public int flags() {
        return entityData.get(DATA_FLAGS);
    }

    public boolean hasFlag(int f) {
        return (flags() & f) != 0;
    }

    public void setFlag(int f, boolean on) {
        int v = flags();
        entityData.set(DATA_FLAGS, on ? v | f : v & ~f);
    }

    private int bits(int shift) {
        return (flags() >> shift) & 3;
    }

    private void setBits(int shift, int val) {
        entityData.set(DATA_FLAGS, (flags() & ~(3 << shift)) | ((val & 3) << shift));
    }

    public int flapLevel() {
        return bits(SH_FLAPS);
    }

    public int sweepLevel() {
        return bits(SH_SWEEP);
    }

    public int releaseMode() {
        return bits(SH_MODE);
    }

    public float throttle() {
        return entityData.get(DATA_THROTTLE);
    }

    public float thrust() {
        return entityData.get(DATA_THRUST);
    }

    public Vector3f syncedVel() {
        return entityData.get(DATA_VEL);
    }

    public Quaternionf syncedQ() {
        return entityData.get(DATA_Q);
    }

    /** control surface deflections -1..1: 0 = elevator (pitch), 1 = ailerons (roll), 2 = rudder (yaw) */
    public float control(int axis) {
        return ((byte) (entityData.get(DATA_CTRL) >> (axis * 8))) / 127f;
    }

    public float health() {
        return entityData.get(DATA_HEALTH);
    }

    public float speedScale() {
        return entityData.get(DATA_SCALE);
    }

    public BombType bombType() {
        return BombType.byIndex(entityData.get(DATA_LOAD) >> 16);
    }

    public int bombCount() {
        return entityData.get(DATA_LOAD) & 0xFFFF;
    }

    public void setLoad(BombType t, int count) {
        entityData.set(DATA_LOAD, (t.ordinal() << 16) | Math.max(0, Math.min(count, kind.capacity[t.ordinal()])));
    }

    public Optional<BlockPos> target() {
        return entityData.get(DATA_TARGET);
    }

    public boolean isDestroyed() {
        return hasFlag(F_DESTROYED);
    }

    public boolean onGroundState() {
        return hasFlag(F_GROUND);
    }

    // ================================================================== lifecycle
    public void initState(double x, double y, double z, float yaw) {
        setPos(x, y, z);
        setYRot(yaw);
        st.x = x;
        st.y = y;
        st.z = z;
        st.setYPR(yaw, 0, 0);
        st.vel.set(0);
        initialized = true;
        positionAfterLoad = false;
        entityData.set(DATA_HEALTH, kind.health);
        entityData.set(DATA_SCALE, Config.SPEED_SCALE.get().floatValue());
        entityData.set(DATA_Q, new Quaternionf(st.q));
        qCur.set(st.q);
        qPrev.set(st.q);
    }

    /** Puts the aircraft in the air (tests, /tubombers spawn ... air). */
    public void startAirborne(double speed) {
        float yaw = st.yawDeg();
        st.setYPR(yaw, 0, 0);
        Vector3f f = st.q.transform(new Vector3f(0, 0, 1));
        st.vel.set(f.x * speed, 0, f.z * speed);
        st.onGround = false;
        st.thrust = 0.75f;
        in.throttle = 0.75f;
        entityData.set(DATA_THROTTLE, 0.75f);
        gear = gearO = 1;
        door = doorO = 0;
        setFlag(F_GEAR_UP, true);
        setFlag(F_GROUND, false);
        setFlag(F_BRAKES, false);
        setFlag(F_DOOR, false);
        if (kind.swingWing) {
            setBits(SH_SWEEP, 1);
            sweep = sweepO = SWEEP_LEVELS[1];
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket p) {
        super.recreateFromPacket(p);
        for (int i = 0; i < parts.length; i++) parts[i].setId(i + p.getId() + 1);
        st.x = p.getX();
        st.y = p.getY();
        st.z = p.getZ();
        st.setYPR(p.getYRot(), 0, 0);
        qCur.set(st.q);
        qPrev.set(st.q);
        lerpX = p.getX();
        lerpY = p.getY();
        lerpZ = p.getZ();
        initialized = true;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(36, 14, 36);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 700 * 700;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(kind == AircraftType.TU160 ? ModRegistry.TU160_ITEM.get() : ModRegistry.TU95_ITEM.get());
    }

    // ================================================================== tick
    @Override
    public void tick() {
        if (!initialized) initState(getX(), getY(), getZ(), getYRot());
        // /summon applies its position AFTER readAdditionalSaveData. Reconcile once,
        // before physics, without resetting saved attitude, velocity, health or controls.
        if (positionAfterLoad) {
            st.x = getX();
            st.y = getY();
            st.z = getZ();
            positionAfterLoad = false;
        }
        super.tick();
        gearO = gear;
        bayO = bay;
        flapsO = flaps;
        sweepO = sweep;
        doorO = door;
        gear = approach(gear, hasFlag(F_GEAR_UP) ? 1 : 0, 1 / 200f);           // 10 s cycle
        bay = approach(bay, hasFlag(F_BAY) ? 1 : 0, 1 / 80f);                  // 4 s
        flaps = approach(flaps, FLAP_LEVELS[Math.min(2, flapLevel())], 1 / 240f); // 12 s full travel
        sweep = approach(sweep, SWEEP_LEVELS[Math.min(2, sweepLevel())], 1 / 400f); // 20 s from 20 to 65 deg
        door = approach(door, hasFlag(F_DOOR) ? 1 : 0, 1 / 60f);
        if (level().isClientSide) clientTick();
        else serverTick();
        updateParts();
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    private void updateParts() {
        Vector3d w = new Vector3d();
        Quaternionf q = level().isClientSide ? qCur : st.q;
        for (BomberPart p : parts) {
            Vector3f l = q.transform(new Vector3f(p.lx, p.ly, p.lz));
            w.set(getX() + l.x, getY() + l.y, getZ() + l.z);
            p.xo = p.getX();
            p.yo = p.getY();
            p.zo = p.getZ();
            p.setPos(w.x, w.y, w.z);
        }
    }

    private Vector3f[] hull() {
        if (hullNow != null && Math.abs(hullSweep - sweep) < 1e-3) return hullNow;
        if (physPose == null) physPose = new GlbPose(data.model);
        physPose.reset();
        if (kind.swingWing) physPose.apply("sweep", sweep * 2f, false, 1f);
        physPose.computeGlobals();
        Vector3f[] out = new Vector3f[data.hull.size()];
        for (int i = 0; i < out.length; i++) {
            AircraftData.HullPoint h = data.hull.get(i);
            out[i] = physPose.global[h.node()].transformPosition(new Vector3f(h.local()));
        }
        hullSweep = sweep;
        return hullNow = out;
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        float scale = Config.SPEED_SCALE.get().floatValue();
        if (speedScale() != scale) entityData.set(DATA_SCALE, scale);
        boolean dead = isDestroyed();
        if ((tickCount - lastInputTick > 10 && lastInputTick <= tickCount) || dead) {
            in.pitch = in.roll = in.yaw = 0;
        }
        if (dead) in.throttle = 0;
        in.brake = hasFlag(F_BRAKES);
        cfg.gearUp = gear;
        cfg.flaps = flaps;
        cfg.bay = bay;
        cfg.sweep = sweep;
        cfg.engines = hasFlag(F_ENGINES) && !dead;
        cfg.extraMass = bombCount() * bombType().mass;
        boolean wasGround = st.onGround;
        FlightModel.step(st, kind, data, in, cfg, new LevelEnv(sl), 0.05, scale, hull());
        if (st.crashed) {
            crash(st.crashReason);
            return;
        }
        if (st.hardLanding) hurtInternal((float) Math.max(5, st.impactSpeed * st.impactSpeed * 1.5), null);
        if (st.tailStrike && st.vel.length() > 30 && tickCount % 10 == 0) hurtInternal(4, null);
        if (!wasGround && st.onGround) {
            playSound(net.minecraft.sounds.SoundEvents.GENERIC_SMALL_FALL, 4f, 0.5f);
        }
        setPos(st.x, st.y, st.z);
        setYRot(st.yawDeg());
        setXRot(-st.pitchDeg());
        double k = 0.05 * scale;
        setDeltaMovement(st.vel.x * k, st.vel.y * k, st.vel.z * k);
        entityData.set(DATA_Q, new Quaternionf(st.q));
        entityData.set(DATA_VEL, new Vector3f((float) st.vel.x, (float) st.vel.y, (float) st.vel.z));
        entityData.set(DATA_THRUST, st.thrust);
        entityData.set(DATA_CTRL, (Math.round(in.pitch * 127) & 255) | ((Math.round(in.roll * 127) & 255) << 8) | ((Math.round(in.yaw * 127) & 255) << 16));
        int f = flags();
        int nf = f;
        nf = st.onGround ? nf | F_GROUND : nf & ~F_GROUND;
        nf = st.stall ? nf | F_STALL : nf & ~F_STALL;
        // entrance hatch / ladder: open only when stopped on the ground
        boolean slow = st.vel.length() < 2.5;
        nf = (st.onGround && slow) ? nf | F_DOOR : nf & ~F_DOOR;
        if (nf != f) entityData.set(DATA_FLAGS, nf);
        // gear handle interlock: weight on wheels prevents retraction
        if (st.onGround && hasFlag(F_GEAR_UP) && gear < 0.05f) setFlag(F_GEAR_UP, false);
        tickBombs(sl);
        tickCrew(sl);
        if (dead) {
            destroyedTicks++;
            if (tickCount % 3 == 0)
                sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), getX(), getY() + 4, getZ(), 6, 3, 1, 3, 0.02);
            if (st.onGround && destroyedTicks > 60) crash("destroyed");
        }
        if (getY() < level().getMinBuildHeight() - 64) discard();
    }

    private void clientTick() {
        clientAge++;
        qPrev.set(qCur);
        qCur.slerp(syncedQ(), 0.5f).normalize();
        st.q.set(qCur);
        Vector3f v = syncedVel();
        st.vel.set(v.x, v.y, v.z);
        // dead reckoning with the synced velocity, corrected towards the last server position
        double k = 0.05 * speedScale();
        double nx = getX() + v.x * k, ny = getY() + v.y * k, nz = getZ() + v.z * k;
        if (lerpSteps > 0) {
            lerpX += v.x * k;
            lerpY += v.y * k;
            lerpZ += v.z * k;
            double a = 0.35;
            nx += (lerpX - nx) * a;
            ny += (lerpY - ny) * a;
            nz += (lerpZ - nz) * a;
            if (new Vec3(lerpX, lerpY, lerpZ).distanceToSqr(nx, ny, nz) > 30 * 30) {
                nx = lerpX;
                ny = lerpY;
                nz = lerpZ;
            }
        }
        setPos(nx, ny, nz);
        st.x = nx;
        st.y = ny;
        st.z = nz;
        setYRot(st.yawDeg());
        propAngleO = propAngle;
        if (hasFlag(F_ENGINES) && !isDestroyed()) propAngle += 0.25f + thrust() * 0.6f;
        for (Crew c : crew.values()) {
            c.px = c.x;
            c.py = c.y;
            c.pz = c.z;
        }
        if (CLIENT != null) CLIENT.tick(this);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = Math.max(1, steps);
    }

    // ================================================================== crew
    private void tickCrew(ServerLevel sl) {
        crew.keySet().removeIf(id -> {
            Entity e = sl.getEntity(id);
            boolean gone = e == null || e.getVehicle() != this;
            if (gone) crewDirty = true;
            return gone;
        });
        if (crewDirty) {
            crewDirty = false;
            syncCrew(null);
        }
    }

    /** Sends the crew table to everyone tracking the aircraft; {@code force} = the owner must adopt it too. */
    public void syncCrew(ServerPlayer forced) {
        if (level().isClientSide) return;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
                new CrewSyncPacket(getId(), new LinkedHashMap<>(crew), forced == null ? -1 : forced.getId()));
        if (forced != null)
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> forced),
                    new CrewSyncPacket(getId(), new LinkedHashMap<>(crew), forced.getId()));
    }

    public Crew crewOf(Entity e) {
        return crew.get(e.getId());
    }

    public AircraftData.Seat seatOf(Entity e) {
        Crew c = crewOf(e);
        return c != null && c.mode == Crew.SEAT && c.index < data.seats.size() ? data.seats.get(c.index) : null;
    }

    public Entity occupant(int seat) {
        for (Map.Entry<Integer, Crew> en : crew.entrySet())
            if (en.getValue().mode == Crew.SEAT && en.getValue().index == seat) return level().getEntity(en.getKey());
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().size() < kind.maxCrew && !isDestroyed();
    }

    @Override
    protected void addPassenger(Entity e) {
        super.addPassenger(e);
        if (!crew.containsKey(e.getId())) {
            // re-mounted from a save: stand in the first walk zone
            AircraftData.Box b = data.walk.isEmpty() ? null : data.walk.get(0);
            crew.put(e.getId(), b == null ? new Crew(Crew.WALK, 0, 0, 3, 0) : new Crew(Crew.WALK, 0, b.cx(), b.floor(), b.cz()));
        }
        crewDirty = true;
    }

    @Override
    protected void removePassenger(Entity e) {
        super.removePassenger(e);
        crew.remove(e.getId());
        crewDirty = true;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return null; // flight controls arrive as packets, the server integrates the flight model
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0;
    }

    @Override
    public boolean shouldRiderSit() {
        return RENDERING_RIDER != null && seatOf(RENDERING_RIDER) != null;
    }

    /** set by the client renderer hook right before a passenger is drawn */
    public static Entity RENDERING_RIDER;

    public Vector3f crewLocal(Entity e, float pt) {
        Crew c = crewOf(e);
        if (c == null) {
            AircraftData.Ladder l = data.ladders.isEmpty() ? null : data.ladders.get(0);
            return l == null ? new Vector3f(0, 3, 0) : new Vector3f(l.box().cx(), 0, l.box().cz());
        }
        if (c.mode == Crew.SEAT && c.index < data.seats.size()) {
            AircraftData.Seat s = data.seats.get(c.index);
            return new Vector3f(s.pos()).add(0, -0.82f, 0);
        }
        return new Vector3f(Mth.lerp(pt, c.px, c.x), Mth.lerp(pt, c.py, c.y), Mth.lerp(pt, c.pz, c.z));
    }

    @Override
    protected void positionRider(Entity e, MoveFunction move) {
        if (!hasPassenger(e)) return;
        Crew c = crewOf(e);
        if (level().isClientSide && c != null && CLIENT != null && CLIENT.isLocal(e)) CLIENT.walk(this, c, e);
        Vector3f l = crewLocal(e, 1f);
        Quaternionf q = level().isClientSide ? qCur : st.q;
        Vector3f w = q.transform(l);
        move.accept(e, getX() + w.x, getY() + w.y, getZ() + w.z);
        float dYaw = Mth.wrapDegrees(getYRot() - yRotO);
        if (Math.abs(dYaw) > 1e-4f) {
            e.setYRot(e.getYRot() + dYaw);
            e.setYHeadRot(e.getYHeadRot() + dYaw);
        }
        AircraftData.Seat s = seatOf(e);
        if (s != null && e instanceof LivingEntity le) le.yBodyRot = getYRot() + s.yaw();
        e.fallDistance = 0;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        Vector3f l = nearestExit(e);
        Vector3d w = st.toWorld(l, new Vector3d());
        return new Vec3(w.x, w.y + 0.05, w.z);
    }

    private Vector3f nearestExit(Entity e) {
        Vector3f best = new Vector3f(0, 0, 0);
        double bd = 1e18;
        Vector3f me = crewLocal(e, 1f);
        for (AircraftData.Ladder l : data.ladders) {
            double d = l.exit().distanceSquared(me);
            if (d < bd) {
                bd = d;
                best = new Vector3f(l.exit());
            }
        }
        // step away from the ladder towards the aircraft's side so the player is not inside the gear leg
        return best.add(1.2f, 0, 0);
    }

    /** Client walking packet: validated against the walk zones and ladders. */
    public void handleCrewMove(ServerPlayer p, int mode, int index, float x, float y, float z) {
        Crew c = crewOf(p);
        if (c == null || c.mode == Crew.SEAT || mode == Crew.SEAT) return;
        if (Math.abs(x - c.x) + Math.abs(y - c.y) + Math.abs(z - c.z) > 3) return;
        boolean ok;
        if (mode == Crew.LADDER) {
            ok = index >= 0 && index < data.ladders.size() && data.ladders.get(index).box().containsXZ(x, z, 0.3) && y >= -0.1
                    && y <= data.ladders.get(index).box().y1() + 0.1;
        } else {
            ok = !Float.isNaN(data.floorAt(x, y, z, 0.3)) || data.ladderAt(x, z, 0.3) != null;
        }
        if (!ok) return;
        c.set(mode, index, x, y, z);
        crewDirty = true;
    }

    public void crewAction(ServerPlayer p, int action, int arg, double tx, double ty, double tz) {
        Crew c = crewOf(p);
        if (c == null) return;
        AircraftData.Seat seat = seatOf(p);
        switch (action) {
            case A_SIT -> {
                if (c.mode != Crew.WALK) return;
                AircraftData.Seat best = null;
                double bd = 1.6 * 1.6;
                for (AircraftData.Seat s : data.seats) {
                    double d = (s.pos().x - c.x) * (s.pos().x - c.x) + (s.pos().z - c.z) * (s.pos().z - c.z);
                    if (d < bd && occupant(s.index()) == null && Math.abs(s.pos().y - 0.5 - c.y) < 1.2) {
                        bd = d;
                        best = s;
                    }
                }
                if (best == null) {
                    p.displayClientMessage(Component.translatable("msg.aiarsenal.no_seat"), true);
                    return;
                }
                c.set(Crew.SEAT, best.index(), best.pos().x, best.pos().y - 0.5f, best.pos().z);
                p.setYRot(getYRot() + best.yaw());
                p.setYHeadRot(p.getYRot());
                syncCrew(p);
                p.displayClientMessage(Component.translatable("msg.aiarsenal.seat", Component.translatable("seat.aiarsenal." + best.role().toLowerCase())), true);
            }
            case A_STAND -> {
                if (seat == null) return;
                Vector3f sp = standPoint(seat);
                c.set(Crew.WALK, 0, sp.x, sp.y, sp.z);
                syncCrew(p);
            }
            case A_EXIT -> {
                if (c.mode != Crew.LADDER || c.y > 0.4f || !hasFlag(F_DOOR)) return;
                dismountCrew(p);
            }
            case A_BAIL -> {
                if (st.onGround) return;
                boolean atHatch = c.mode == Crew.LADDER;
                boolean eject = seat != null && kind == AircraftType.TU160;
                if (!atHatch && !eject) return;
                Vector3d w = st.toWorld(eject ? new Vector3f(seat.pos()).add(0, 3.5f, 0) : new Vector3f(c.x, -1.5f, c.z), new Vector3d());
                allowDismount = true;
                p.stopRiding();
                allowDismount = false;
                p.teleportTo(w.x, w.y, w.z);
                double k = 0.05 * speedScale() * 0.6;
                p.setDeltaMovement(st.vel.x * k, eject ? 1.4 : st.vel.y * k, st.vel.z * k);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 120, 0));
                p.displayClientMessage(Component.translatable(eject ? "msg.aiarsenal.ejected" : "msg.aiarsenal.bailed"), true);
                level().playSound(null, w.x, w.y, w.z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 3f, 0.6f);
            }
            case A_GEAR -> {
                if (seat == null || !seat.canFly()) return;
                if (st.onGround && !hasFlag(F_GEAR_UP)) {
                    p.displayClientMessage(Component.translatable("msg.aiarsenal.gear_locked"), true);
                    return;
                }
                setFlag(F_GEAR_UP, !hasFlag(F_GEAR_UP));
                level().playSound(null, getX(), getY() + 2, getZ(), ModRegistry.SND_GEAR.get(), SoundSource.NEUTRAL, 3f, 1f);
            }
            case A_FLAPS -> {
                if (seat == null || !seat.canFly()) return;
                if (kind.swingWing && sweepLevel() > 0) {
                    p.displayClientMessage(Component.translatable("msg.aiarsenal.flaps_sweep"), true);
                    return;
                }
                setBits(SH_FLAPS, (flapLevel() + (arg < 0 ? 2 : 1)) % 3);
                level().playSound(null, getX(), getY() + 2, getZ(), ModRegistry.SND_HYDRAULICS.get(), SoundSource.NEUTRAL, 2f, 1.2f);
            }
            case A_BAY -> {
                if (seat == null || !(seat.canFly() || seat.bombardier())) return;
                setFlag(F_BAY, !hasFlag(F_BAY));
                level().playSound(null, getX(), getY() + 2, getZ(), ModRegistry.SND_BAY.get(), SoundSource.NEUTRAL, 3f, 1f);
            }
            case A_SWEEP -> {
                if (!kind.swingWing || seat == null || !seat.canFly()) return;
                int lv = Mth.clamp(sweepLevel() + (arg < 0 ? -1 : 1), 0, 2);
                if (lv > 0 && flapLevel() > 0) {
                    p.displayClientMessage(Component.translatable("msg.aiarsenal.sweep_flaps"), true);
                    return;
                }
                setBits(SH_SWEEP, lv);
                level().playSound(null, getX(), getY() + 4, getZ(), ModRegistry.SND_HYDRAULICS.get(), SoundSource.NEUTRAL, 3f, 0.7f);
            }
            case A_LIGHTS -> setFlag(F_LIGHTS, !hasFlag(F_LIGHTS));
            case A_ENGINES -> {
                if (seat == null || !seat.canFly()) return;
                setFlag(F_ENGINES, !hasFlag(F_ENGINES));
            }
            case A_RELEASE -> {
                if (seat == null || !(seat.canFly() || seat.bombardier())) return;
                startRelease(p);
            }
            case A_MODE -> {
                if (seat == null || !(seat.canFly() || seat.bombardier())) return;
                setBits(SH_MODE, (releaseMode() + 1) % 3);
            }
            case A_LOAD -> {
                if (!p.getAbilities().instabuild) return;
                if (!st.onGround || st.vel.length() > 1) {
                    p.displayClientMessage(Component.translatable("msg.aiarsenal.load_ground"), true);
                    return;
                }
                BombType t = bombCount() == 0 ? bombType() : BombType.byIndex(bombType().ordinal() + 1);
                setLoad(t, kind.capacity[t.ordinal()]);
                level().playSound(null, getX(), getY() + 1, getZ(), ModRegistry.SND_RELEASE.get(), SoundSource.NEUTRAL, 2f, 0.7f);
            }
            case A_TARGET -> {
                if (seat == null || !(seat.canFly() || seat.bombardier())) return;
                entityData.set(DATA_TARGET, Optional.of(BlockPos.containing(tx, ty, tz)));
                p.displayClientMessage(Component.translatable("msg.aiarsenal.target", (int) tx, (int) ty, (int) tz), true);
            }
            case A_CLEAR_TARGET -> entityData.set(DATA_TARGET, Optional.empty());
            default -> {
            }
        }
    }

    /** Commands / tests: actions without a seated crew member. */
    public void crewActionAsServer(int action) {
        if (action == A_RELEASE) startRelease(null);
    }

    /** Commands / tests: direct control of the mechanisation and the autopilot. */
    public void setControl(String what, float v) {
        switch (what) {
            case "gear" -> setFlag(F_GEAR_UP, v > 0.5f);
            case "bay" -> setFlag(F_BAY, v > 0.5f);
            case "flaps" -> setBits(SH_FLAPS, Mth.clamp(Math.round(v), 0, 2));
            case "sweep" -> setBits(SH_SWEEP, Mth.clamp(Math.round(v), 0, 2));
            case "mode" -> setBits(SH_MODE, Mth.clamp(Math.round(v), 0, 2));
            case "brakes" -> setFlag(F_BRAKES, v > 0.5f);
            case "engines" -> setFlag(F_ENGINES, v > 0.5f);
            case "throttle" -> {
                in.throttle = Mth.clamp(v, 0, kind.maxThrottle());
                entityData.set(DATA_THROTTLE, in.throttle);
            }
            case "pitch" -> {
                in.pitch = Mth.clamp(v, -1, 1);
                lastInputTick = tickCount + 100000;
            }
            case "roll" -> {
                in.roll = Mth.clamp(v, -1, 1);
                lastInputTick = tickCount + 100000;
            }
            case "release_now" -> {
                // instant mechanisation for tests
                gear = gearO = hasFlag(F_GEAR_UP) ? 1 : 0;
                bay = bayO = hasFlag(F_BAY) ? 1 : 0;
                sweep = sweepO = SWEEP_LEVELS[Math.min(2, sweepLevel())];
                flaps = flapsO = FLAP_LEVELS[Math.min(2, flapLevel())];
            }
            case "consent" -> consentTick = v > 0.5f ? tickCount + 100000 : -100;
            default -> {
            }
        }
    }

    public void setTarget(BlockPos p) {
        entityData.set(DATA_TARGET, Optional.ofNullable(p));
    }

    private Vector3f standPoint(AircraftData.Seat s) {
        // nearest point inside a walk zone next to the seat
        Vector3f best = new Vector3f(s.pos()).add(0, -0.5f, 0);
        double bd = 1e9;
        for (AircraftData.Box b : data.walk) {
            float x = Mth.clamp(s.pos().x, b.x0() + 0.15f, b.x1() - 0.15f);
            float z = Mth.clamp(s.pos().z - 0.45f, b.z0() + 0.15f, b.z1() - 0.15f);
            double d = (x - s.pos().x) * (x - s.pos().x) + (z - s.pos().z) * (z - s.pos().z) + Math.abs(b.y0() - (s.pos().y - 0.5f)) * 4;
            if (d < bd) {
                bd = d;
                best = new Vector3f(x, b.y0(), z);
            }
        }
        return best;
    }

    public void dismountCrew(ServerPlayer p) {
        Vec3 out = getDismountLocationForPassenger(p);
        allowDismount = true;
        p.stopRiding();
        allowDismount = false;
        p.teleportTo(out.x, out.y, out.z);
    }

    /** Boards a player at the bottom of the given ladder (or straight into a seat for tests/commands). */
    public boolean board(Player p, int ladder, String seatRole) {
        if (!canAddPassenger(p)) return false;
        Crew c;
        AircraftData.Seat s = seatRole == null ? null : data.seat(seatRole);
        if (s != null && occupant(s.index()) == null) c = new Crew(Crew.SEAT, s.index(), s.pos().x, s.pos().y - 0.5f, s.pos().z);
        else {
            AircraftData.Ladder l = data.ladders.get(Mth.clamp(ladder, 0, data.ladders.size() - 1));
            c = new Crew(Crew.LADDER, data.ladders.indexOf(l), l.box().cx(), 0f, l.box().cz());
        }
        crew.put(p.getId(), c);
        if (!p.startRiding(this, true)) {
            crew.remove(p.getId());
            return false;
        }
        if (p instanceof ServerPlayer sp) syncCrew(sp);
        return true;
    }

    // ================================================================== controls
    public void pilotInput(ServerPlayer p, float pitch, float roll, float yaw, float throttle, boolean brake, boolean consent) {
        AircraftData.Seat s = seatOf(p);
        if (s == null) return;
        if (consent && (s.canFly() || s.bombardier())) consentTick = tickCount;
        if (!s.canFly()) return;
        // the co-pilot only flies when the pilot seat is empty
        if (!s.pilot()) {
            AircraftData.Seat ps = data.seat("PILOT");
            if (ps != null && occupant(ps.index()) != null) return;
        }
        in.pitch = Mth.clamp(pitch, -1, 1);
        in.roll = Mth.clamp(roll, -1, 1);
        in.yaw = Mth.clamp(yaw, -1, 1);
        in.throttle = Mth.clamp(throttle, 0, kind.maxThrottle());
        entityData.set(DATA_THROTTLE, in.throttle);
        if (brake != hasFlag(F_BRAKES)) setFlag(F_BRAKES, brake);
        lastInputTick = tickCount;
    }

    // ================================================================== bombs
    public int seriesLength() {
        int n = bombCount();
        return switch (releaseMode()) {
            case MODE_SERIES -> Math.min(n, bombType().nuclear() ? 1 : bombType().missile() ? 2 : bombType() == BombType.FAB1500 ? 2 : 6);
            case MODE_SALVO -> n;
            default -> Math.min(n, 1);
        };
    }

    public int releaseInterval() {
        return switch (bombType()) {
            case FAB250 -> 3;
            case FAB500 -> 4;
            case FAB1500 -> 6;
            case KH101, KH55 -> 30;     // MKU-6-5 rotary launcher indexes to the next station
            case RDS1, AN602 -> 20;
        };
    }

    public boolean bayReady() {
        return bay > 0.95f;
    }

    private void startRelease(ServerPlayer p) {
        if (queueLeft > 0) return;
        if (bombCount() <= 0) {
            if (p != null) p.displayClientMessage(Component.translatable("msg.aiarsenal.empty"), true);
            return;
        }
        if (!bayReady()) {
            if (p != null) p.displayClientMessage(Component.translatable("msg.aiarsenal.bay_closed"), true);
            return;
        }
        if (st.onGround) {
            if (p != null) p.displayClientMessage(Component.translatable("msg.aiarsenal.on_ground"), true);
            return;
        }
        queueLeft = seriesLength();
        queueTimer = 0;
    }

    /** next bomb's release point in model space */
    public Vector3f nextStation() {
        List<Vector3f> l = data.stations.get(bombType());
        int i = kind.capacity[bombType().ordinal()] - bombCount();
        return l.isEmpty() ? new Vector3f(0, 3, 0) : new Vector3f(l.get(Mth.clamp(i, 0, l.size() - 1)));
    }

    private void tickBombs(ServerLevel sl) {
        if (queueLeft > 0) {
            if (!bayReady() || bombCount() <= 0) queueLeft = 0;
            else if (queueTimer-- <= 0) {
                releaseOne(sl);
                queueLeft--;
                queueTimer = releaseInterval() - 1;
            }
            return;
        }
        // CCRP: automatic release while the crew holds consent and the solution says "now"
        Optional<BlockPos> tg = target();
        if (tg.isPresent() && (tickCount - consentTick <= 3 || consentTick > tickCount) && bayReady() && bombCount() > 0 && !st.onGround) {
            double[] lead = ccrp(new LevelEnv(sl), tg.get());
            if (lead != null) {
                // one automatic series per pass: re-armed only while the release point is still ahead
                // the series starts on the next tick: lead the trigger by 1.5 ticks of ground travel
                double tickRun = Math.hypot(st.vel.x, st.vel.z) * 0.05 * speedScale() * 1.5;
                if (lead[0] > lead[1] + tickRun + 8) ccrpArmed = true;
                else if (ccrpArmed && lead[0] <= lead[1] + tickRun && lead[0] > -lead[1] - 30) {
                    ccrpArmed = false;
                    startRelease(null);
                }
            }
        }
    }

    /**
     * CCRP solution: {remaining along-track distance to the release point, half series length} in blocks,
     * plus cross-track error and time to release.
     */
    public double[] ccrp(FlightModel.Env env, BlockPos target) {
        Vector3d start = st.toWorld(nextStation(), new Vector3d());
        Vector3d impact = FlightModel.predictImpact(start, new Vector3d(st.vel).add(0, -1.5, 0), bombType(), speedScale(), env, null);
        if (impact == null) return null;
        double gx = st.vel.x, gz = st.vel.z, gs = Math.sqrt(gx * gx + gz * gz);
        if (gs < 5) return null;
        double dx = target.getX() + 0.5 - impact.x, dz = target.getZ() + 0.5 - impact.z;
        double along = (dx * gx + dz * gz) / gs, cross = (dx * -gz + dz * gx) / gs;
        double spacing = gs * releaseInterval() * 0.05 * speedScale();
        double half = (seriesLength() - 1) / 2.0 * spacing;
        double ttr = (along - half) / (gs * speedScale());
        return new double[]{along, half, cross, ttr, impact.x, impact.y, impact.z};
    }

    private void releaseOne(ServerLevel sl) {
        BombType t = bombType();
        Vector3f loc = nextStation();
        Vector3d w = st.toWorld(loc, new Vector3d());
        Vector3f down = st.q.transform(new Vector3f(0, -1.5f, 0));
        Vector3d v = new Vector3d(st.vel).add(down.x, down.y, down.z);
        if (t.arsenal() != null) {
            double k = 0.05 * speedScale();
            ru.aiforge.arsenal.ArsenalHooks.releaseFromBomber(sl, this, t.arsenal(), new net.minecraft.world.phys.Vec3(w.x, w.y, w.z),
                    new net.minecraft.world.phys.Vec3(v.x * k, v.y * k, v.z * k), target().orElse(null));
        } else {
            BombEntity b = BombEntity.create(sl, t, w, v, this);
            sl.addFreshEntity(b);
        }
        setLoad(t, bombCount() - 1);
        releasedTotal++;
        lastReleaseTick = tickCount;
        sl.playSound(null, w.x, w.y, w.z, ModRegistry.SND_RELEASE.get(), SoundSource.NEUTRAL, 2.5f, t == BombType.FAB1500 ? 0.7f : 1f);
    }

    /** Survival loading with bomb items while parked. */
    private InteractionResult loadFromItem(Player p, ItemStack stack, BombItem bi) {
        if (!st.onGround || st.vel.length() > 1) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.load_ground"), true);
            return InteractionResult.FAIL;
        }
        if (bombCount() > 0 && bombType() != bi.type) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.load_mixed", bombType().displayName()), true);
            return InteractionResult.FAIL;
        }
        int cap = kind.capacity[bi.type.ordinal()];
        int room = cap - (bombCount() > 0 ? bombCount() : 0);
        int n = p.getAbilities().instabuild ? room : Math.min(room, stack.getCount());
        if (n <= 0) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.full"), true);
            return InteractionResult.FAIL;
        }
        setLoad(bi.type, (bombCount() > 0 ? bombCount() : 0) + n);
        if (!p.getAbilities().instabuild) stack.shrink(n);
        level().playSound(null, p.getX(), p.getY(), p.getZ(), ModRegistry.SND_RELEASE.get(), SoundSource.NEUTRAL, 1.5f, 0.6f);
        p.displayClientMessage(Component.translatable("msg.aiarsenal.loaded", bombCount(), bi.type.displayName()), true);
        return InteractionResult.CONSUME;
    }

    // ================================================================== interaction & damage
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        return interactFromPart(p, hand);
    }

    public InteractionResult interactFromPart(Player p, InteractionHand hand) {
        if (p.getVehicle() == this) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = p.getItemInHand(hand);
        if (stack.getItem() instanceof BombItem bi) return loadFromItem(p, stack, bi);
        if (p.isSecondaryUseActive() && stack.isEmpty() && getPassengers().isEmpty() && st.onGround && st.vel.length() < 1) {
            if (!p.getAbilities().instabuild) p.getInventory().placeItemBackInInventory(getPickResult());
            discard();
            return InteractionResult.CONSUME;
        }
        int ladder = -1;
        double bd = 4.5 * 4.5;
        for (int i = 0; i < data.ladders.size(); i++) {
            Vector3d w = st.toWorld(data.ladders.get(i).exit(), new Vector3d());
            double d = p.distanceToSqr(w.x, w.y, w.z);
            if (d < bd) {
                bd = d;
                ladder = i;
            }
        }
        if (ladder < 0) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.go_ladder"), true);
            return InteractionResult.FAIL;
        }
        if (!hasFlag(F_DOOR)) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.door_closed"), true);
            return InteractionResult.FAIL;
        }
        if (!board(p, ladder, null)) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.full_crew"), true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return hurtFromPart(null, src, amount);
    }

    public boolean hurtFromPart(BomberPart part, DamageSource src, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        if (src.getEntity() instanceof Player pl && pl.getAbilities().instabuild && pl.isShiftKeyDown() && src.getDirectEntity() == pl) {
            for (Entity e : List.copyOf(getPassengers())) if (e instanceof ServerPlayer sp) dismountCrew(sp);
            discard();
            return true;
        }
        if (src.getEntity() != null && hasPassenger(src.getEntity())) return false;
        float k = src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? 1.0f : 0.25f;
        hurtInternal(amount * k, src);
        return true;
    }

    private void hurtInternal(float amount, DamageSource src) {
        float h = health() - amount;
        entityData.set(DATA_HEALTH, Math.max(0, h));
        if (h <= 0 && !isDestroyed()) {
            setFlag(F_DESTROYED, true);
            setFlag(F_ENGINES, false);
            level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 6f, 0.6f);
        }
    }

    private void crash(String why) {
        if (isRemoved()) return;
        ServerLevel sl = (ServerLevel) level();
        TuBombersLog.crash(this, why);
        List<Entity> riders = List.copyOf(getPassengers());
        allowDismount = true;
        ejectPassengers();
        allowDismount = false;
        for (Entity e : riders) e.hurt(sl.damageSources().fall(), 1000f);
        double speed = st.vel.length();
        int n = 4;
        for (int i = 0; i < n; i++) {
            Vector3d w = st.toWorld(new Vector3f(0, 3, -15 + 10 * i), new Vector3d());
            ImpactHandler.crash(sl, new Vec3(w.x, w.y, w.z), (float) (5 + Math.min(4, speed / 40)), this);
        }
        discard();
    }

    // ================================================================== save
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.contains("qx")) {
            st.q.set(t.getFloat("qx"), t.getFloat("qy"), t.getFloat("qz"), t.getFloat("qw"));
            st.vel.set(t.getDouble("vx"), t.getDouble("vy"), t.getDouble("vz"));
            st.thrust = t.getFloat("thrust");
            st.onGround = t.getBoolean("ground");
        }
        st.x = getX();
        st.y = getY();
        st.z = getZ();
        initialized = true;
        positionAfterLoad = true;
        entityData.set(DATA_Q, new Quaternionf(st.q));
        entityData.set(DATA_FLAGS, t.getInt("flags") & ~F_STALL);
        in.throttle = t.getFloat("throttle");
        entityData.set(DATA_THROTTLE, in.throttle);
        entityData.set(DATA_HEALTH, t.contains("health") ? t.getFloat("health") : kind.health);
        entityData.set(DATA_LOAD, t.getInt("load"));
        gear = gearO = t.getFloat("gear");
        bay = bayO = t.getFloat("bay");
        flaps = flapsO = t.getFloat("flaps");
        sweep = sweepO = t.getFloat("sweep");
        if (t.contains("tx")) setTarget(new BlockPos(t.getInt("tx"), t.getInt("ty"), t.getInt("tz")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putFloat("qx", st.q.x);
        t.putFloat("qy", st.q.y);
        t.putFloat("qz", st.q.z);
        t.putFloat("qw", st.q.w);
        t.putDouble("vx", st.vel.x);
        t.putDouble("vy", st.vel.y);
        t.putDouble("vz", st.vel.z);
        t.putFloat("thrust", st.thrust);
        t.putBoolean("ground", st.onGround);
        t.putInt("flags", flags());
        t.putFloat("throttle", in.throttle);
        t.putFloat("health", health());
        t.putInt("load", entityData.get(DATA_LOAD));
        t.putFloat("gear", gear);
        t.putFloat("bay", bay);
        t.putFloat("flaps", flaps);
        t.putFloat("sweep", sweep);
        target().ifPresent(p -> {
            t.putInt("tx", p.getX());
            t.putInt("ty", p.getY());
            t.putInt("tz", p.getZ());
        });
    }

    /** small logging helper kept out of the hot path */
    private static final class TuBombersLog {
        static void crash(BomberEntity b, String why) {
            ru.aiforge.tubombers.TuBombers.LOGGER.info("{} crashed ({}) at {} speed {} m/s", b.kind.id, why, b.blockPosition(),
                    String.format("%.1f", b.st.vel.length()));
        }
    }
}
