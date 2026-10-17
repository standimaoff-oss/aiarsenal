package ru.aiforge.bm21grad.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.Config;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.ballistics.Ballistics;
import ru.aiforge.bm21grad.glb.GlbModel;
import ru.aiforge.bm21grad.glb.GlbModels;
import ru.aiforge.bm21grad.glb.GlbPose;
import ru.aiforge.bm21grad.item.RocketItem;
import ru.aiforge.bm21grad.registry.ModRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * BM-21 "Grad" multiple rocket launcher on the Ural-375D chassis.
 * <ul>
 *   <li>Driving: client-authoritative for the driver (like boats), lerped for everybody else.</li>
 *   <li>Fire control (server): target -> ballistic solution -> laying (turret/elevation slew, cab shield lowered)
 *   -> ripple fire with a fixed interval from the real tube positions of the GLB model.</li>
 *   <li>Reload: tube by tube from the vehicle's ammo rack (filled by right-clicking with rocket items).</li>
 *   <li>Damage: health, destruction with ammunition cook-off, burnt wreck.</li>
 * </ul>
 */
public class Bm21Entity extends Entity {
    public static final int TUBES = 40;
    public static final int RACK_MAX = 120;
    public static final int SHIELD_TICKS = 20;
    public static final float TURRET_SPEED = 0.9f;  // deg/tick
    public static final float ELEV_SPEED = 0.45f;   // deg/tick
    public static final double WHEEL_RADIUS = 0.6;
    public static final double TURN_RADIUS = 10.5;
    public static final int EV_FIRE_BASE = 160;     // entity events 160..199 (unsigned) = tube fired; avoids vanilla-global ids 21/35/63
    public static final byte EV_DESTROYED = 10;
    public static final byte EV_LOADED = 11;

    public static final int ST_SHIELDS = 1, ST_ENGINE = 2, ST_LIGHTS = 4, ST_DESTROYED = 8, ST_RELOADING = 16, ST_GUNNER = 32;
    public static final int PH_IDLE = 0, PH_LAYING = 1, PH_FIRING = 2;

    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TURRET = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ELEV = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> DATA_TUBES = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TARGET = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_LOAD = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_AMMO = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_MISSION = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RANGE_SCALE = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(Bm21Entity.class, EntityDataSerializers.FLOAT);

    // ---------------------------------------------------------------- common runtime state
    public float shieldProgress, prevShieldProgress;
    public float prevTurret, prevElev;
    public float speed;          // signed, blocks/tick along the body axis (driver side)
    public float steer;          // -1..1, positive = left
    public int recoilTicks = 100;
    public int lastFiredTube = -1;
    private final int[] rack = new int[RocketType.values().length];

    // lerp (remote copies)
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot;

    // server-only
    private int phase = PH_IDLE;
    private int shotsLeft;
    private int shotTimer;
    private RocketType missionType = RocketType.NONE;
    private double solElev;
    private int solFlight;
    private float desiredTurret, desiredElev;
    private boolean manualAim;
    private int reloadProgress, reloadTube = -1;
    private boolean freeAmmo;
    private int wreckTicks;
    private final List<int[]> cookOff = new ArrayList<>();
    private Vec3 lastServerPos;
    private GlbPose pose;
    private int[] rocketNodes;

    public Bm21Entity(EntityType<? extends Bm21Entity> type, Level level) {
        super(type, level);
        setMaxUpStep(1.1f);
        blocksBuilding = true;
    }

    public static Bm21Entity create(Level level, double x, double y, double z, float yaw) {
        Bm21Entity e = new Bm21Entity(ModRegistry.BM21.get(), level);
        e.moveTo(x, y, z, yaw, 0);
        e.setHealth(Config.VEHICLE_HEALTH.get().floatValue());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_HEALTH, 300f);
        entityData.define(DATA_TURRET, 0f);
        entityData.define(DATA_ELEV, 0f);
        entityData.define(DATA_STATE, 0);
        entityData.define(DATA_TUBES, new CompoundTag());
        entityData.define(DATA_TARGET, Optional.empty());
        entityData.define(DATA_LOAD, -1);
        entityData.define(DATA_MODE, TUBES);
        entityData.define(DATA_AMMO, (byte) RocketType.HE_FRAG.ordinal());
        entityData.define(DATA_MISSION, 0);
        entityData.define(DATA_RANGE_SCALE, 1f);
        entityData.define(DATA_SPEED, 0f);
    }

    // ---------------------------------------------------------------- synced accessors
    public float getHealth() { return entityData.get(DATA_HEALTH); }
    public void setHealth(float h) { entityData.set(DATA_HEALTH, h); }
    public float getTurret() { return entityData.get(DATA_TURRET); }
    public float getElevation() { return entityData.get(DATA_ELEV); }
    public int getState() { return entityData.get(DATA_STATE); }
    public boolean hasState(int bit) { return (getState() & bit) != 0; }
    public boolean isDestroyed() { return hasState(ST_DESTROYED); }
    public Optional<BlockPos> getTarget() { return entityData.get(DATA_TARGET); }
    public int getFireMode() { return entityData.get(DATA_MODE); }
    public RocketType getAmmoSelection() { return RocketType.byIndex(entityData.get(DATA_AMMO)); }
    public float getRangeScale() { return entityData.get(DATA_RANGE_SCALE); }
    public float getSyncedSpeed() { return entityData.get(DATA_SPEED); }

    /** Speed in blocks/tick: the driver's client simulates the vehicle itself, everyone else sees the server-measured value. */
    public float displaySpeed() { return isControlledByLocalInstance() ? speed : getSyncedSpeed(); }
    /** phase << 8 | shots left */
    public int getMission() { return entityData.get(DATA_MISSION); }
    public int getLoadingTube() { int v = entityData.get(DATA_LOAD); return v < 0 ? -1 : v >> 16; }
    public float getLoadingProgress() { int v = entityData.get(DATA_LOAD); return v < 0 ? 0 : (v & 0xFFFF) / 1000f; }

    private void setState(int bit, boolean on) {
        int s = getState();
        entityData.set(DATA_STATE, on ? s | bit : s & ~bit);
    }

    public RocketType tube(int i) {
        byte[] b = entityData.get(DATA_TUBES).getByteArray("t");
        return i < b.length ? RocketType.byIndex(b[i]) : RocketType.NONE;
    }

    public byte[] tubes() {
        byte[] b = entityData.get(DATA_TUBES).getByteArray("t");
        return b.length == TUBES ? b.clone() : new byte[TUBES];
    }

    private void setTubes(byte[] b) {
        CompoundTag t = new CompoundTag();
        t.putByteArray("t", b);
        entityData.set(DATA_TUBES, t);
    }

    public int loadedCount() {
        int n = 0;
        for (byte v : tubes()) if (v != 0) n++;
        return n;
    }

    public int loadedCount(RocketType t) {
        int n = 0;
        for (byte v : tubes()) if (v == t.ordinal()) n++;
        return n;
    }

    public int rackCount(RocketType t) { return rack[t.ordinal()]; }
    public int rackTotal() { int n = 0; for (int v : rack) n += v; return n; }

    // ---------------------------------------------------------------- geometry
    public Vec3 toWorld(double x, double y, double z) {
        return new Vec3(x, y, z).yRot((float) Math.toRadians(-getYRot())).add(position());
    }

    public Vec3 forward() {
        return Vec3.directionFromRotation(0, getYRot());
    }

    /** Launcher pivot (Launcher_Pitch) in world space. */
    public Vec3 launcherPivot() {
        return toWorld(0, 2.45, -2.62);
    }

    public static GlbModel model() {
        return GlbModels.get(GlbModels.BM21);
    }

    /** Writes turret traverse and launcher elevation into a pose via the model's own animation clips. */
    public static void poseLauncher(GlbPose p, float turretRel, float elev) {
        float theta = Mth.positiveModulo(-turretRel, 360f);
        p.apply("turret_traverse", theta / 90f, false, 1f);
        p.apply("launcher_elevate", Mth.clamp(elev, 0, 55) / 24f, false, 1f);
    }

    public static int rocketNode(GlbModel m, int tube) {
        return m.node(String.format("Rocket_%02d", tube + 1));
    }

    private GlbPose serverPose() {
        if (pose == null) {
            GlbModel m = model();
            pose = new GlbPose(m);
            rocketNodes = new int[TUBES];
            for (int i = 0; i < TUBES; i++) rocketNodes[i] = rocketNode(m, i);
        }
        return pose;
    }

    private static final double[][] SEATS = {{0.50, 0.86, 1.02}, {-0.48, 0.86, 1.02}, {-0.02, 0.86, 1.02}}; // on the cab seats; eye (+1.62-0.35) ~2.13 m = mid windscreen

    // ---------------------------------------------------------------- tick
    @Override
    public void tick() {
        super.tick();
        prevShieldProgress = shieldProgress;
        prevTurret = getTurret();
        prevElev = getElevation();
        if (recoilTicks < 100) recoilTicks++;
        float shTarget = hasState(ST_SHIELDS) ? 1f : 0f;
        shieldProgress = Mth.approach(shieldProgress, shTarget, 1f / SHIELD_TICKS);

        tickLerp();
        if (isControlledByLocalInstance()) {
            drive();
        } else if (!level().isClientSide) {
            coast();
        }
        if (!level().isClientSide) serverTick();
        else clientParticles();
    }

    private void drive() {
        LivingEntity driver = getControllingPassenger();
        float fwd = 0, side = 0;
        boolean canDrive = !isDestroyed() && shieldProgress <= 0f && getMissionPhase() == PH_IDLE && !hasState(ST_GUNNER);
        if (driver != null && canDrive) {
            fwd = driver.zza;
            side = driver.xxa;
        }
        float max = Config.MAX_SPEED.get().floatValue();
        if (fwd > 0) speed = Math.min(max, speed + (speed < 0 ? 0.05f : 0.012f) * fwd);
        else if (fwd < 0) speed = Math.max(-max * 0.25f, speed + (speed > 0 ? 0.05f : 0.008f) * fwd);
        else speed *= 0.97f;
        if (Math.abs(speed) < 0.002f) speed = 0;
        steer = Mth.approach(steer, Mth.clamp(side, -1, 1), 0.08f);
        if (onGround()) {
            float yawRate = (float) Math.toDegrees(speed / TURN_RADIUS) * steer;
            setYRot(getYRot() - yawRate);
        }
        applyMotion();
    }

    private void coast() {
        speed *= 0.9f;
        if (Math.abs(speed) < 0.002f) speed = 0;
        steer *= 0.9f;
        applyMotion();
    }

    private void applyMotion() {
        Vec3 f = forward();
        double vy = getDeltaMovement().y;
        vy = onGround() ? -0.04 : Math.max(-3.0, (vy - 0.08) * 0.98);
        if (isInWater()) {
            vy = Math.min(vy + 0.06, 0.02);
            speed *= 0.9f;
        }
        setDeltaMovement(f.x * speed, vy, f.z * speed);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) speed *= 0.5f;
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            double x = getX() + (lerpX - getX()) / lerpSteps;
            double y = getY() + (lerpY - getY()) / lerpSteps;
            double z = getZ() + (lerpZ - getZ()) / lerpSteps;
            setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
            lerpSteps--;
            setPos(x, y, z);
            setRot(getYRot(), getXRot());
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 3;
    }

    private void clientParticles() {
        if (isDestroyed()) {
            if (random.nextInt(2) == 0) {
                Vec3 p = toWorld((random.nextDouble() - 0.5) * 2.2, 2.2, (random.nextDouble() - 0.5) * 6);
                level().addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, p.x, p.y, p.z, 0, 0.07, 0);
            }
            if (random.nextInt(4) == 0) {
                Vec3 p = toWorld((random.nextDouble() - 0.5) * 2, 1.6, (random.nextDouble() - 0.5) * 5);
                level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.02, 0);
            }
            return;
        }
        if (hasState(ST_ENGINE) && tickCount % 3 == 0) {
            Vec3 p = toWorld(-1.12, 1.64, -1.12);
            float load = Math.min(1f, Math.abs(displaySpeed()) * 2f);
            level().addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), p.x, p.y, p.z, 0, 0.03 + load * 0.05, 0);
            if (load > 0.5f) level().addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), p.x, p.y, p.z, 0, 0.05, 0);
        }
    }

    // ---------------------------------------------------------------- server logic
    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        entityData.set(DATA_RANGE_SCALE, Config.RANGE_SCALE.get().floatValue());
        Vec3 pos = position();
        float measured = lastServerPos == null ? 0f : (float) Math.sqrt(Math.pow(pos.x - lastServerPos.x, 2) + Math.pow(pos.z - lastServerPos.z, 2));
        if (lastServerPos != null && pos.subtract(lastServerPos).dot(forward()) < 0) measured = -measured;
        lastServerPos = pos;
        entityData.set(DATA_SPEED, measured);
        setState(ST_ENGINE, !isDestroyed() && getControllingPassenger() != null);

        if (isDestroyed()) {
            tickCookOff(sl);
            if (++wreckTicks > 12000) discard();
            return;
        }
        if (Math.abs(measured) > 0.3f) ram(sl, measured);

        // ---- aim
        float curT = getTurret(), curE = getElevation();
        float wantT, wantE;
        if (phase != PH_IDLE || manualAim) {
            wantT = desiredTurret;
            wantE = desiredElev;
        } else {
            wantT = 0;
            wantE = 0;
        }
        float dT = Mth.wrapDegrees(wantT - curT);
        float nT = Mth.wrapDegrees(curT + Mth.clamp(dT, -TURRET_SPEED, TURRET_SPEED));
        float nE = Mth.approach(curE, wantE, ELEV_SPEED);
        if (nT != curT) entityData.set(DATA_TURRET, nT);
        if (nE != curE) entityData.set(DATA_ELEV, nE);
        boolean moving = Math.abs(dT) > 0.05f || Math.abs(nE - wantE) > 0.05f;
        if (moving && tickCount % 8 == 0)
            sl.playSound(null, getX(), getY() + 2, getZ(), ModRegistry.SND_HYDRAULICS.get(), SoundSource.NEUTRAL, 0.35f, 1.2f);

        // ---- mission
        if (phase == PH_LAYING) {
            if (!hasState(ST_SHIELDS)) setState(ST_SHIELDS, true);
            boolean laid = Math.abs(Mth.wrapDegrees(desiredTurret - getTurret())) < 0.3f
                    && Math.abs(desiredElev - getElevation()) < 0.2f && shieldProgress >= 1f && Math.abs(measured) < 0.05f;
            if (laid) {
                phase = PH_FIRING;
                shotTimer = 0;
            }
        } else if (phase == PH_FIRING) {
            if (--shotTimer <= 0) {
                int t = nextTube(missionType);
                if (t < 0 || shotsLeft <= 0) {
                    endMission("msg.aiarsenal.mission_complete");
                } else {
                    fireTube(sl, t, false);
                    shotsLeft--;
                    shotTimer = Config.SALVO_INTERVAL.get();
                    if (shotsLeft <= 0 || nextTube(missionType) < 0) endMission("msg.aiarsenal.mission_complete");
                }
            }
        }
        entityData.set(DATA_MISSION, phase << 8 | Math.max(0, shotsLeft));

        // ---- reload
        if (hasState(ST_RELOADING)) tickReload(sl);
        else entityData.set(DATA_LOAD, -1);
    }

    private void ram(ServerLevel sl, float v) {
        AABB box = getBoundingBox().inflate(0.4, 0, 0.4).move(forward().scale(Math.signum(v) * 2.2));
        for (Entity e : sl.getEntities(this, box, e -> e instanceof LivingEntity && !hasPassenger(e) && e.isAlive())) {
            float dmg = Math.abs(v) * 14f;
            if (e.hurt(new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.FLY_INTO_WALL), this, getControllingPassenger()), dmg)) {
                Vec3 push = forward().scale(Math.signum(v) * Math.abs(v) * 1.4).add(0, 0.35, 0);
                e.push(push.x, push.y, push.z);
            }
        }
    }

    public int getMissionPhase() {
        return level().isClientSide ? getMission() >> 8 : phase;
    }

    private int nextTube(RocketType type) {
        byte[] b = tubes();
        // ripple order: alternate left/right from the bottom row up (reduces rocking)
        for (int k = 0; k < TUBES; k++) {
            int row = k / 10, j = k % 10;
            int col = (j % 2 == 0) ? j / 2 : 9 - j / 2;
            int i = row * 10 + col;
            if (b[i] != 0 && (type == RocketType.NONE || b[i] == type.ordinal())) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- commands
    public Component setTarget(BlockPos p) {
        if (p == null) {
            entityData.set(DATA_TARGET, Optional.empty());
            return Component.translatable("msg.aiarsenal.target_cleared");
        }
        entityData.set(DATA_TARGET, Optional.of(p.immutable()));
        if (!hasState(ST_GUNNER)) manualAim = false; // in gunner mode the launcher stays on the gunner's laying
        Ballistics.Solution s = solve(p, fireType());
        if (!s.ok())
            return Component.translatable("msg.aiarsenal.target_out", p.getX(), p.getY(), p.getZ(),
                    Component.translatable("msg.aiarsenal.reason." + s.reason()));
        double dist = horizontalDistanceTo(p);
        return Component.translatable("msg.aiarsenal.target_set", p.getX(), p.getY(), p.getZ(), (int) dist,
                Ballistics.mils(Ballistics.yawTo(launcherPivot(), p.getX() + 0.5, p.getZ() + 0.5)),
                String.format("%.1f", s.elevationDeg()), String.format("%.1f", s.flightTicks() / 20.0));
    }

    public double horizontalDistanceTo(BlockPos p) {
        Vec3 l = launcherPivot();
        return Math.sqrt(Math.pow(p.getX() + 0.5 - l.x, 2) + Math.pow(p.getZ() + 0.5 - l.z, 2));
    }

    public RocketType fireType() {
        RocketType sel = getAmmoSelection();
        if (loadedCount(sel) > 0) return sel;
        for (byte v : tubes()) if (v != 0) return RocketType.byIndex(v);
        return sel;
    }

    /** Low-angle solution accounting for the ~2.4 block tube length between pivot and nose. */
    public Ballistics.Solution solve(BlockPos target, RocketType type) {
        Vec3 l = launcherPivot();
        double dist = Math.sqrt(Math.pow(target.getX() + 0.5 - l.x, 2) + Math.pow(target.getZ() + 0.5 - l.z, 2));
        double dy = target.getY() + 1.0 - l.y;
        double thrust = Ballistics.thrust(getRangeScale(), type == RocketType.NONE ? 1 : type.rangeFactor);
        Ballistics.Solution s = Ballistics.solve(dist, dy, thrust, 0);
        for (int i = 0; i < 2 && s.ok(); i++) {
            double e = Math.toRadians(s.elevationDeg());
            s = Ballistics.solve(dist - 2.4 * Math.cos(e), dy - 2.4 * Math.sin(e), thrust, 0);
        }
        return s;
    }

    public Component startFire(ServerPlayer by) {
        if (isDestroyed()) return Component.translatable("msg.aiarsenal.destroyed");
        if (phase != PH_IDLE) return Component.translatable("msg.aiarsenal.busy");
        if (getTarget().isEmpty()) return Component.translatable("msg.aiarsenal.no_target");
        if (loadedCount() == 0) return Component.translatable("msg.aiarsenal.bm21_empty");
        if (Math.abs(getSyncedSpeed()) > 0.05f) return Component.translatable("msg.aiarsenal.moving");
        RocketType t = fireType();
        BlockPos target = getTarget().get();
        Ballistics.Solution s = solve(target, t);
        if (!s.ok()) return Component.translatable("msg.aiarsenal.target_out", target.getX(), target.getY(), target.getZ(),
                Component.translatable("msg.aiarsenal.reason." + s.reason()));
        Vec3 l = launcherPivot();
        double worldYaw = Ballistics.yawTo(l, target.getX() + 0.5, target.getZ() + 0.5);
        float rel = Mth.wrapDegrees((float) (worldYaw - getYRot()));
        if (Math.abs(rel) < 30 && s.elevationDeg() < 12)
            return Component.translatable("msg.aiarsenal.reason.cab_sector");
        setState(ST_RELOADING, false);
        missionType = t;
        solElev = s.elevationDeg();
        solFlight = s.flightTicks();
        desiredTurret = rel;
        desiredElev = (float) solElev;
        shotsLeft = Math.min(getFireMode(), loadedCount(t));
        phase = PH_LAYING;
        setState(ST_SHIELDS, true);
        Bm21Grad.LOGGER.debug("BM-21 {} fire mission {} x{} elev {} flight {}t", getId(), t, shotsLeft, solElev, solFlight);
        return Component.translatable("msg.aiarsenal.fire_mission", shotsLeft,
                Component.translatable(t.translationKey()), String.format("%.1f", solElev), String.format("%.1f", solFlight / 20.0));
    }

    public Component ceaseFire() {
        if (phase == PH_IDLE) return Component.translatable("msg.aiarsenal.idle");
        endMission(null);
        return Component.translatable("msg.aiarsenal.cease");
    }

    private void endMission(String msgKey) {
        phase = PH_IDLE;
        shotsLeft = 0;
        manualAim = true; // keep the launcher laid until the crew stows it
        desiredTurret = getTurret();
        desiredElev = getElevation();
        if (msgKey != null) for (Entity p : getPassengers()) if (p instanceof Player pl) pl.displayClientMessage(Component.translatable(msgKey), true);
    }

    /** Stow: launcher to travel position and shield up. */
    public Component toggleShields() {
        if (phase != PH_IDLE) return Component.translatable("msg.aiarsenal.busy");
        boolean down = !hasState(ST_SHIELDS);
        setState(ST_SHIELDS, down);
        if (!down) {
            manualAim = false;
            setState(ST_GUNNER, false); // stowing the launcher ends gunner mode
        }
        return Component.translatable(down ? "msg.aiarsenal.combat_position" : "msg.aiarsenal.travel_position");
    }

    public Component aim(float turretRel, float elev) {
        if (phase != PH_IDLE) return Component.translatable("msg.aiarsenal.busy");
        manualAim = true;
        desiredTurret = Mth.wrapDegrees(turretRel);
        desiredElev = Mth.clamp(elev, 0, 55);
        setState(ST_SHIELDS, true);
        return Component.translatable("msg.aiarsenal.aim", String.format("%.1f", desiredTurret), String.format("%.1f", desiredElev));
    }

    // ---------------------------------------------------------------- gunner (first-person laying) mode
    public boolean isGunnerMode() {
        return hasState(ST_GUNNER);
    }

    /** Toggles DRIVE <-> GUNNER. Gunner mode brakes the truck, lowers the cab shield and hands the launcher to the crew's view. */
    public Component toggleGunner() {
        if (isDestroyed()) return Component.translatable("msg.aiarsenal.destroyed");
        if (!hasState(ST_GUNNER)) {
            if (Math.abs(getSyncedSpeed()) > 0.05f) return Component.translatable("msg.aiarsenal.moving");
            setState(ST_GUNNER, true);
            setState(ST_SHIELDS, true);
            if (phase == PH_IDLE) {
                manualAim = true;
                desiredTurret = getTurret();
                desiredElev = getElevation();
            }
            return Component.translatable("msg.aiarsenal.gunner_on");
        }
        if (phase != PH_IDLE) return Component.translatable("msg.aiarsenal.busy");
        setState(ST_GUNNER, false);
        setState(ST_SHIELDS, false);
        manualAim = false;
        return Component.translatable("msg.aiarsenal.gunner_off");
    }

    /** Continuous laying from the gunner's view (ignored outside gunner mode or during a fire mission). */
    public void aimDirect(float turretRel, float elev) {
        if (!hasState(ST_GUNNER) || phase != PH_IDLE || isDestroyed()) return;
        manualAim = true;
        desiredTurret = Mth.wrapDegrees(turretRel);
        desiredElev = Mth.clamp(elev, 0, 55);
    }

    public static int packAim(float turretRel, float elev) {
        int t = Mth.clamp(Math.round((Mth.wrapDegrees(turretRel) + 180f) * 10f), 0, 3600);
        int e = Mth.clamp(Math.round(Mth.clamp(elev, 0, 55) * 10f), 0, 1023);
        return t << 10 | e;
    }

    public static float unpackTurret(int v) { return (v >> 10) / 10f - 180f; }

    public static float unpackElev(int v) { return (v & 1023) / 10f; }

    /**
     * Ground impact of a rocket laid at (turretRel, elev), flown with the same integrator as the real rocket.
     * Unloaded columns are assumed level with the vehicle. Returns null if it never comes down (should not happen).
     */
    public Vec3 predictImpact(float turretRel, float elev) {
        double fallback = getY();
        return predictImpact(turretRel, elev, (x, z) -> {
            BlockPos col = new BlockPos(x, 0, z);
            if (!level().hasChunkAt(col)) return fallback;
            // clients only receive the MOTION_BLOCKING / WORLD_SURFACE heightmaps
            double g = level().isClientSide ? level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : groundHeightAt(x, z);
            return g <= level().getMinBuildHeight() + 1 ? fallback : g; // column not known here (empty client chunk)
        });
    }

    /** Same, against an arbitrary terrain height function (x, z) -> surface y. Hills are hit on the way up as well. */
    public Vec3 predictImpact(float turretRel, float elev, java.util.function.BiFunction<Integer, Integer, Double> ground) {
        RocketType t = fireType();
        double thrust = Ballistics.thrust(getRangeScale(), t == RocketType.NONE ? 1 : t.rangeFactor);
        Vec3 dir = Vec3.directionFromRotation(-Mth.clamp(elev, 0, 55), getYRot() + turretRel);
        Vec3 p0 = launcherPivot().add(dir.scale(2.4));
        double[] s = {p0.x, p0.y, p0.z, dir.x * Ballistics.MUZZLE, dir.y * Ballistics.MUZZLE, dir.z * Ballistics.MUZZLE};
        for (int k = 0; k < 2400; k++) {
            Ballistics.step(s, k, thrust);
            double g = ground.apply(Mth.floor(s[0]), Mth.floor(s[2]));
            if (s[1] <= g) return new Vec3(s[0], g, s[2]);
        }
        return null;
    }

    public Component setFireMode(int n) {
        entityData.set(DATA_MODE, Mth.clamp(n, 1, TUBES));
        return Component.translatable("msg.aiarsenal.mode", getFireMode());
    }

    public Component setAmmoSelection(RocketType t) {
        if (t == RocketType.NONE) t = RocketType.HE_FRAG;
        entityData.set(DATA_AMMO, (byte) t.ordinal());
        return Component.translatable("msg.aiarsenal.ammo", Component.translatable(t.translationKey()));
    }

    public Component toggleLights() {
        setState(ST_LIGHTS, !hasState(ST_LIGHTS));
        return Component.translatable(hasState(ST_LIGHTS) ? "msg.aiarsenal.lights_on" : "msg.aiarsenal.lights_off");
    }

    public Component startReload(boolean free) {
        if (isDestroyed()) return Component.translatable("msg.aiarsenal.destroyed");
        if (phase != PH_IDLE) return Component.translatable("msg.aiarsenal.busy");
        if (loadedCount() >= TUBES) return Component.translatable("msg.aiarsenal.bm21_full");
        if (hasState(ST_RELOADING)) {
            setState(ST_RELOADING, false);
            return Component.translatable("msg.aiarsenal.reload_stop");
        }
        freeAmmo = free && Config.CREATIVE_FREE_AMMO.get();
        if (!freeAmmo && rackTotal() == 0) return Component.translatable("msg.aiarsenal.no_ammo");
        manualAim = false;
        setState(ST_SHIELDS, false);
        setState(ST_RELOADING, true);
        reloadTube = -1;
        reloadProgress = 0;
        return Component.translatable("msg.aiarsenal.reload_start", TUBES - loadedCount());
    }

    /** Puts rockets into the ammo rack; returns how many fitted. */
    public int addToRack(RocketType t, int n) {
        int k = Math.max(0, Math.min(n, RACK_MAX - rackTotal()));
        rack[t.ordinal()] += k;
        return k;
    }

    /** Sets turret/elevation instantly (tests / commands). */
    public void snapLauncher(float turretRel, float elev) {
        entityData.set(DATA_TURRET, Mth.wrapDegrees(turretRel));
        entityData.set(DATA_ELEV, Mth.clamp(elev, 0, 55));
        desiredTurret = getTurret();
        desiredElev = getElevation();
        manualAim = true;
    }

    /** Instantly fills all tubes (commands/tests). */
    public void loadAll(RocketType t) {
        byte[] b = new byte[TUBES];
        java.util.Arrays.fill(b, (byte) t.ordinal());
        setTubes(b);
    }

    private void tickReload(ServerLevel sl) {
        if (Math.abs(getTurret()) > 0.5f || getElevation() > 0.5f) {
            entityData.set(DATA_LOAD, -1);
            return; // launcher returns to the loading position first
        }
        byte[] b = tubes();
        if (reloadTube < 0 || b[reloadTube] != 0) {
            reloadTube = -1;
            for (int i = 0; i < TUBES; i++) if (b[i] == 0) { reloadTube = i; break; }
            reloadProgress = 0;
            if (reloadTube < 0) {
                setState(ST_RELOADING, false);
                announce("msg.aiarsenal.reload_done");
                return;
            }
        }
        RocketType t = pickAmmo();
        if (t == null) {
            setState(ST_RELOADING, false);
            announce("msg.aiarsenal.no_ammo");
            return;
        }
        int total = Config.RELOAD_TICKS.get();
        reloadProgress++;
        entityData.set(DATA_LOAD, reloadTube << 16 | Math.min(1000, reloadProgress * 1000 / total));
        if (reloadProgress >= total) {
            b[reloadTube] = (byte) t.ordinal();
            setTubes(b);
            if (!(freeAmmo && rack[t.ordinal()] == 0)) rack[t.ordinal()]--;
            sl.playSound(null, getX(), getY() + 2, getZ(), ModRegistry.SND_RELOAD.get(), SoundSource.NEUTRAL, 0.8f, 0.9f + random.nextFloat() * 0.2f);
            level().broadcastEntityEvent(this, EV_LOADED);
            reloadTube = -1;
            reloadProgress = 0;
        }
    }

    private RocketType pickAmmo() {
        RocketType sel = getAmmoSelection();
        if (rack[sel.ordinal()] > 0) return sel;
        for (RocketType t : RocketType.values()) if (t != RocketType.NONE && rack[t.ordinal()] > 0) return t;
        return freeAmmo ? sel : null;
    }

    private void announce(String key) {
        for (Entity p : getPassengers()) if (p instanceof Player pl) pl.displayClientMessage(Component.translatable(key), true);
    }

    // ---------------------------------------------------------------- firing
    /** Launch position and direction of a tube in world space (from the GLB pose). */
    public Vec3[] tubeMuzzle(int tube) {
        GlbPose p = serverPose().reset();
        poseLauncher(p, getTurret(), getElevation());
        p.computeGlobals();
        int n = rocketNodes[tube];
        Vector3f pos = p.nodePos(n, new Vector3f());
        Vector3f dir = p.nodeAxis(n, 0, 0, 1, new Vector3f());
        float yr = (float) Math.toRadians(-getYRot());
        Vec3 wp = new Vec3(pos.x, pos.y, pos.z).yRot(yr).add(position());
        Vec3 wd = new Vec3(dir.x, dir.y, dir.z).yRot(yr).normalize();
        return new Vec3[]{wp, wd};
    }

    private void fireTube(ServerLevel sl, int tube, boolean cookOffShot) {
        byte[] b = tubes();
        RocketType type = RocketType.byIndex(b[tube]);
        if (type == RocketType.NONE) return;
        b[tube] = 0;
        setTubes(b);
        Vec3[] m = tubeMuzzle(tube);
        Vec3 dir = m[1];
        double disp = Config.DISPERSION.get();
        double thrust = Ballistics.thrust(getRangeScale(), type.rangeFactor);
        if (cookOffShot) {
            dir = new Vec3(random.nextGaussian(), 0.6 + random.nextDouble(), random.nextGaussian()).normalize();
            thrust *= 0.3 + random.nextDouble() * 0.5;
        } else {
            double yawErr = random.nextGaussian() * disp * 0.5;
            double pitchErr = random.nextGaussian() * disp * 0.5;
            dir = dir.yRot((float) yawErr).add(0, pitchErr, 0).normalize();
            thrust *= 1 + random.nextGaussian() * disp * 0.8;
        }
        Entity owner = getControllingPassenger();
        if (owner == null && !getPassengers().isEmpty()) owner = getPassengers().get(0);
        RocketEntity r = RocketEntity.launch(sl, type, m[0], dir.scale(Ballistics.MUZZLE), thrust, owner, this);
        getTarget().ifPresent(t -> r.groundHint = t.getY());
        sl.addFreshEntity(r);
        // launch FX: exhaust blast behind the tube, dust around the vehicle
        Vec3 breech = m[0].subtract(m[1].scale(3.1));
        sl.sendParticles(ParticleTypes.FLAME, breech.x, breech.y, breech.z, 12, 0.25, 0.25, 0.25, 0.05);
        sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), breech.x, breech.y, breech.z, 10, 0.4, 0.3, 0.4, 0.06);
        sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), breech.x, breech.y - 1, breech.z, 6, 1.2, 0.3, 1.2, 0.02);
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 14, 2.5, 0.1, 3.5, 0.08);
        sl.playSound(null, m[0].x, m[0].y, m[0].z, ModRegistry.SND_LAUNCH.get(), SoundSource.HOSTILE, 8f, 0.9f + random.nextFloat() * 0.2f);
        lastFiredTube = tube;
        recoilTicks = 0;
        level().broadcastEntityEvent(this, (byte) (EV_FIRE_BASE + tube));
    }

    @Override
    public void handleEntityEvent(byte id) {
        int v = id & 0xFF;
        if (v >= EV_FIRE_BASE && v < EV_FIRE_BASE + TUBES) {
            lastFiredTube = v - EV_FIRE_BASE;
            recoilTicks = 0;
        } else if (id == EV_DESTROYED) {
            recoilTicks = 0;
        } else super.handleEntityEvent(id);
    }

    // ---------------------------------------------------------------- damage
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (isInvulnerableTo(src) || level().isClientSide || isRemoved()) return !level().isClientSide;
        Entity attacker = src.getEntity();
        if (attacker instanceof Player p && src.getDirectEntity() == p) {
            if (p.getAbilities().instabuild && p.isShiftKeyDown()) {
                discard();
                return true;
            }
            if (isDestroyed()) {
                spawnAtLocation(new ItemStack(Items.IRON_INGOT, 4 + random.nextInt(6)));
                discard();
                return true;
            }
        }
        if (isDestroyed()) return false;
        float mult = src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? 1.0f : src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) ? 0.1f : 0.35f;
        setHealth(getHealth() - amount * mult);
        markHurt();
        if (getHealth() <= 0) destroy();
        return true;
    }

    private void destroy() {
        ServerLevel sl = (ServerLevel) level();
        setHealth(0);
        setState(ST_DESTROYED, true);
        setState(ST_RELOADING, false);
        setState(ST_ENGINE, false);
        phase = PH_IDLE;
        ejectPassengers();
        level().broadcastEntityEvent(this, EV_DESTROYED);
        sl.explode(this, getX(), getY() + 1.5, getZ(), 4.0f, true, Level.ExplosionInteraction.NONE);
        for (int i = 0; i < TUBES; i++) if (tube(i) != RocketType.NONE) cookOff.add(new int[]{i, 10 + random.nextInt(160)});
        for (RocketType t : RocketType.values()) rack[t.ordinal()] = 0;
        Bm21Grad.LOGGER.info("BM-21 {} destroyed at {}", getId(), blockPosition());
    }

    private void tickCookOff(ServerLevel sl) {
        for (int k = cookOff.size() - 1; k >= 0; k--) {
            int[] c = cookOff.get(k);
            if (--c[1] <= 0) {
                cookOff.remove(k);
                if (random.nextInt(3) == 0) fireTube(sl, c[0], true);
                else {
                    byte[] b = tubes();
                    b[c[0]] = 0;
                    setTubes(b);
                    Vec3 m = tubeMuzzle(c[0])[0];
                    sl.explode(this, m.x, m.y, m.z, 1.5f, true, Level.ExplosionInteraction.NONE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- interaction / riding
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (isDestroyed()) return InteractionResult.PASS;
        ItemStack st = player.getItemInHand(hand);
        if (st.getItem() instanceof RocketItem ri) {
            if (!level().isClientSide) {
                int room = RACK_MAX - rackTotal();
                int n = Math.min(room, player.getAbilities().instabuild ? st.getCount() : st.getCount());
                if (n <= 0) {
                    player.displayClientMessage(Component.translatable("msg.aiarsenal.rack_full"), true);
                } else {
                    rack[ri.type.ordinal()] += n;
                    if (!player.getAbilities().instabuild) st.shrink(n);
                    playSound(ModRegistry.SND_RELOAD.get(), 0.6f, 1.2f);
                    player.displayClientMessage(Component.translatable("msg.aiarsenal.rack_add", n,
                            Component.translatable(ri.type.translationKey()), rackTotal()), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive()) {
            if (st.isEmpty() && getPassengers().isEmpty()) {
                if (!level().isClientSide) packUp(player);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (!canAddPassenger(player)) return InteractionResult.PASS;
        if (!level().isClientSide) return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    private void packUp(Player player) {
        ItemStack item = new ItemStack(ModRegistry.BM21_ITEM.get());
        CompoundTag tag = new CompoundTag();
        saveVehicle(tag);
        item.getOrCreateTag().put("Vehicle", tag);
        if (!player.getAbilities().instabuild || !player.getInventory().add(item)) spawnAtLocation(item);
        discard();
    }

    @Override
    protected boolean canAddPassenger(Entity p) {
        return getPassengers().size() < SEATS.length && !isDestroyed();
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = getFirstPassenger();
        return e instanceof Player p ? p : null;
    }

    @Override
    protected void positionRider(Entity p, MoveFunction fn) {
        if (!hasPassenger(p)) return;
        int i = Math.max(0, getPassengers().indexOf(p));
        double[] s = SEATS[Math.min(i, SEATS.length - 1)];
        Vec3 w = toWorld(s[0], s[1] + p.getMyRidingOffset(), s[2]);
        fn.accept(p, w.x, w.y, w.z);
        if (p instanceof LivingEntity le) {
            float dy = getYRot() - yRotO;
            if (dy != 0) {
                p.setYRot(p.getYRot() + dy);
                le.setYHeadRot(le.getYHeadRot() + dy);
            }
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.3;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        int i = Math.max(0, getPassengers().indexOf(p));
        double side = i == 1 ? -1 : 1;
        for (double[] c : new double[][]{{side * 2.3, 1.15}, {side * 2.3, -1.0}, {-side * 2.3, 1.15}, {0, -4.6}, {0, 4.3}}) {
            Vec3 w = toWorld(c[0], 0, c[1]);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos bp = BlockPos.containing(w.x, getY() + dy, w.z);
                Vec3 v = net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(p.getType(), level(), bp, true);
                if (v != null) return v;
            }
        }
        return super.getDismountLocationForPassenger(p);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return (other.canBeCollidedWith() || other.isPushable()) && !isPassengerOfSameVehicle(other);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4.5, 2.5, 4.5);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModRegistry.BM21_ITEM.get());
    }

    // ---------------------------------------------------------------- persistence
    public void saveVehicle(CompoundTag t) {
        t.putFloat("Health", getHealth());
        t.putFloat("Turret", getTurret());
        t.putFloat("Elev", getElevation());
        t.putInt("State", getState() & ~(ST_ENGINE | ST_RELOADING));
        t.putByteArray("Tubes", tubes());
        t.putIntArray("Rack", rack);
        t.putInt("Mode", getFireMode());
        t.putByte("Ammo", entityData.get(DATA_AMMO));
        getTarget().ifPresent(p -> t.put("Target", NbtUtils.writeBlockPos(p)));
    }

    public void loadVehicle(CompoundTag t) {
        if (t.contains("Health")) setHealth(t.getFloat("Health"));
        entityData.set(DATA_TURRET, t.getFloat("Turret"));
        entityData.set(DATA_ELEV, t.getFloat("Elev"));
        entityData.set(DATA_STATE, t.getInt("State"));
        if (t.contains("Tubes")) {
            byte[] b = t.getByteArray("Tubes");
            if (b.length == TUBES) setTubes(b);
        }
        int[] r = t.getIntArray("Rack");
        for (int i = 0; i < Math.min(r.length, rack.length); i++) rack[i] = r[i];
        if (t.contains("Mode")) entityData.set(DATA_MODE, Mth.clamp(t.getInt("Mode"), 1, TUBES));
        if (t.contains("Ammo")) entityData.set(DATA_AMMO, t.getByte("Ammo"));
        entityData.set(DATA_TARGET, t.contains("Target") ? Optional.of(NbtUtils.readBlockPos(t.getCompound("Target"))) : Optional.empty());
        shieldProgress = prevShieldProgress = hasState(ST_SHIELDS) ? 1 : 0;
        if (hasState(ST_SHIELDS)) {
            manualAim = true;
            desiredTurret = getTurret();
            desiredElev = getElevation();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        loadVehicle(t);
        wreckTicks = t.getInt("WreckTicks");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        saveVehicle(t);
        t.putInt("WreckTicks", wreckTicks);
    }

    /** One-line status for commands / tests. */
    public String statusLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("hp=").append(Math.round(getHealth()))
                .append(" loaded=").append(loadedCount()).append('/').append(TUBES)
                .append(" rack=").append(rackTotal())
                .append(" turret=").append(String.format("%.1f", getTurret()))
                .append(" elev=").append(String.format("%.1f", getElevation()))
                .append(" phase=").append(new String[]{"IDLE", "LAYING", "FIRING"}[getMissionPhase()])
                .append(" shields=").append(hasState(ST_SHIELDS) ? "down" : "up")
                .append(" reload=").append(hasState(ST_RELOADING))
                .append(" mode=").append(getFireMode())
                .append(" ammo=").append(getAmmoSelection().id)
                .append(" destroyed=").append(isDestroyed());
        getTarget().ifPresent(p -> sb.append(" target=").append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()));
        return sb.toString();
    }

    /** Target on the terrain surface at x/z (loads the chunk on the server if needed). */
    public BlockPos resolveGround(BlockPos p) {
        return new BlockPos(p.getX(), groundHeightAt(p.getX(), p.getZ()) - 1, p.getZ());
    }

    public int groundHeightAt(int x, int z) {
        return level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }
}
