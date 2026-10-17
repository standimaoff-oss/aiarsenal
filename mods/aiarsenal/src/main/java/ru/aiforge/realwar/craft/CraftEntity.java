package ru.aiforge.realwar.craft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.BulletManager;
import ru.aiforge.realwar.ordnance.ShellEntity;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModSounds;

/**
 * Base of helicopters, boats and the submarine (reply 6).
 *
 * <p>Full 3-D attitude (yaw / pitch / roll) integrated from body rates through a quaternion, velocity in m/s,
 * forces and torques in SI units. Physics runs on the pilot's client (smooth, latency-free control) and is
 * mirrored to the server with {@link CraftStatePacket}; without a pilot the server runs the same physics.
 * Sign convention: pitch + = nose up, roll + = right side down, yaw = Minecraft yRot (increasing = turn right).
 * Model frame: +X left, +Y up, +Z forward; render order Y(-yaw) X(-pitch) Z(roll) around {@link #pivotY()}.
 */
public abstract class CraftEntity extends Entity implements ru.aiforge.realwar.damage.Damageable {
    private static final EntityDataAccessor<CompoundTag> DMG = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** r18: module damage (rotor, tail rotor, wings, tail, engine, guns, fire, hull holes + flooding). */
    public final ru.aiforge.realwar.damage.DamageState dmg = new ru.aiforge.realwar.damage.DamageState();
    private CompoundTag dmgSeen;

    @Override
    public ru.aiforge.realwar.damage.DamageState dmg() {
        return this.dmg;
    }

    @Override
    public void blastNear(Vec3 centre, double tntKg, double dist) {
        ru.aiforge.realwar.damage.CraftDamage.blastNear(this, centre, tntKg, dist);
    }

    public void publishDamage() {
        this.dmg.dirty = false;
        this.dmg.dirtyNow = false;
        this.entityData.set(DMG, this.dmg.sync());
    }

    /** r18: a shot-down aircraft hits the ground. */
    public void destroyNow() {
        this.destroy();
    }

    public Vec3 localToWorld(Vec3 p) {
        return this.localToWorld(p.x, p.y, p.z);
    }

    private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ENGINE = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AUX = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AUX2 = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AUX3 = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GUN_YAW = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GUN_PITCH = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> SEL = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> AMMO = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> SEATS = SynchedEntityData.defineId(CraftEntity.class, EntityDataSerializers.COMPOUND_TAG);

    public static final double DT = 0.05;
    public static final double G = 9.81;
    public static final double RHO_AIR = 1.225;
    public static final double RHO_WATER = 1000.0;

    public static final int FLAG_STAB = 1;     // heli: stabilisation / hover mode (roll locked); sub: depth hold
    public static final int FLAG_GEAR = 2;     // heli: landing gear down
    public static final int FLAG_LASER = 4;    // laser designator / rangefinder on
    public static final int FLAG_PERISCOPE = 8;

    /** Client hooks (installed by CraftClient; common code must not reference client classes). */
    public static Function<CraftEntity, float[]> clientInput = c -> null;
    public static Consumer<CraftEntity> statePublisher = c -> {};
    public static Predicate<CraftEntity> localPilot = c -> false;
    public static Consumer<CraftEntity> clientFx = c -> {};
    public static BiConsumer<CraftEntity, Integer> clientEvent = (c, i) -> {};
    public static Consumer<CraftOrdnance> clientOrdnanceFx = o -> {};
    public static Consumer<CraftSnapPacket> snapHandler = m -> {};

    public final CraftKind kind;
    /** Velocity, m/s (world frame). */
    public Vec3 vel = Vec3.ZERO;
    public float pitch, roll, pitchO, rollO;
    /** Body rates, deg/s: roll (about forward), pitch (nose up), yaw (turn right). */
    public float rateP, rateQ, rateR;
    /** Heli: rotor speed fraction; boats: actual engine output; sub: ballast fill fraction. */
    public float aux, auxO;
    /** Heli: collective; boats/sub: throttle lever. */
    public float aux2, aux2O;
    /** Heli: rotor azimuth (render only); boats: rudder; sub: battery. */
    public float aux3;
    public float rotorAngle, rotorAngleO;
    public float gunYawO, gunPitchO, gunYawC, gunPitchC;
    /** Collision speed lost this tick (m/s) - client reports it, the server converts it into damage. */
    public float impact;
    /** HUD telemetry (local). */
    public float agl, warnVrs, warnStall, gForce = 1f;
    public boolean onWater;
    protected float[] forced;
    /** Ground contact from our own last move() - NOT onGround(): on the client that flag is overwritten by the
     *  server's movement packets (stale while a pilot drives client-side), which broke hover-mode altitude hold. */
    protected boolean contact;
    /** Last Kalibr aim point (HUD / tests). */
    public Vec3 lastKalibrTarget;
    private boolean seeded;
    protected int pendingSeat = -1;
    protected final int[] cooldown = new int[8];
    private final int[] muzzleNext = new int[8];
    private final boolean[] fireHeld = new boolean[6];
    private final int[] inputTick = new int[6];
    protected final boolean[] fireLatch = new boolean[6];
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYaw;
    private int rejected;
    private final Map<Long, Double> waterCache = new HashMap<>();

    protected CraftEntity(EntityType<? extends CraftEntity> type, Level level, CraftKind kind) {
        super(type, level);
        this.kind = kind;
        this.blocksBuilding = true;
        this.setMaxUpStep(kind.isAir() ? 0.6f : 1.1f);
    }

    // ------------------------------------------------------------------------------------------ synched state

    @Override
    protected void defineSynchedData() {
        this.entityData.define(HP, 1f);
        this.entityData.define(FUEL, 1f);
        this.entityData.define(ENGINE, false);
        this.entityData.define(PITCH, 0f);
        this.entityData.define(ROLL, 0f);
        this.entityData.define(AUX, 0f);
        this.entityData.define(AUX2, 0f);
        this.entityData.define(AUX3, 0f);
        this.entityData.define(GUN_YAW, 0f);
        this.entityData.define(GUN_PITCH, 0f);
        this.entityData.define(SPEED, 0f);
        this.entityData.define(FLAGS, (byte) FLAG_GEAR);
        this.entityData.define(SEL, 0);
        this.entityData.define(AMMO, new CompoundTag());
        this.entityData.define(SEATS, new CompoundTag());
        this.entityData.define(DMG, new CompoundTag());
    }

    /** Called by the factory after construction (fills ammo, hp). */
    public void initFresh() {
        this.entityData.set(HP, this.kind.hp);
        this.entityData.set(FUEL, 1f);
        int[] a = new int[this.kind.loads.length];
        for (int i = 0; i < a.length; i++) a[i] = this.kind.loads[i].full();
        this.setAmmoArray(a);
        if (this.kind.isSub()) {
            this.aux3 = 1f;
            this.entityData.set(AUX3, 1f);
        }
    }

    public float hp() { return this.entityData.get(HP); }

    /** Crew stays aboard under water (Forge's default ejects riders whose eyes are submerged: the submarine crew was
     *  thrown out on diving and drowned; a heeling boat dumped its crew). Air is refreshed in serverTick. */
    @Override
    public boolean canBeRiddenUnderFluidType(net.minecraftforge.fluids.FluidType type, Entity rider) {
        return true;
    }
    public float fuel() { return this.entityData.get(FUEL); }
    public boolean engineOn() { return this.entityData.get(ENGINE); }
    public int flags() { return this.entityData.get(FLAGS); }
    public boolean flag(int f) { return (this.flags() & f) != 0; }
    public float syncedSpeed() { return this.entityData.get(SPEED); }
    public float gunYaw() { return this.entityData.get(GUN_YAW); }
    public float gunPitch() { return this.entityData.get(GUN_PITCH); }

    public void setFlag(int f, boolean on) {
        int v = this.flags();
        this.entityData.set(FLAGS, (byte) (on ? v | f : v & ~f));
    }

    public void setEngine(boolean on) {
        if (on && this.fuel() <= 0.0005f) return;
        this.entityData.set(ENGINE, on);
    }

    public void setFuel(float f) { this.entityData.set(FUEL, Mth.clamp(f, 0f, 1f)); }
    public void setHp(float h) { this.entityData.set(HP, Mth.clamp(h, 0f, this.kind.hp)); }

    public int[] ammoArray() {
        // copy: getIntArray returns the tag's backing array - mutating it in place made set() see "no change"
        // and the ammo counters never synced to clients
        int[] a = this.entityData.get(AMMO).getIntArray("a");
        return a.length == this.kind.loads.length ? a.clone() : new int[this.kind.loads.length];
    }

    protected void setAmmoArray(int[] a) {
        CompoundTag t = new CompoundTag();
        t.putIntArray("a", a.clone());
        this.entityData.set(AMMO, t);
    }

    public int ammo(int load) {
        int[] a = this.ammoArray();
        return load >= 0 && load < a.length ? a[load] : 0;
    }

    public int selected(int seat) {
        int v = (this.entityData.get(SEL) >> (seat * 4)) & 15;
        return Math.min(v, this.kind.loads.length - 1);
    }

    private void setSelected(int seat, int idx) {
        int v = this.entityData.get(SEL) & ~(15 << (seat * 4));
        this.entityData.set(SEL, v | ((idx & 15) << (seat * 4)));
    }

    /** Persistent throttle lever etc. synced for the HUD of other crew members. */
    public float syncedAux() { return this.entityData.get(AUX); }
    public float syncedAux2() { return this.entityData.get(AUX2); }
    public float syncedAux3() { return this.entityData.get(AUX3); }

    // ------------------------------------------------------------------------------------------ orientation

    /** Height of the rotation pivot above the entity origin. */
    public abstract float pivotY();

    public static Quaterniond orientation(double yawDeg, double pitchDeg, double rollDeg) {
        return new Quaterniond().rotateY(Math.toRadians(-yawDeg)).rotateX(Math.toRadians(-pitchDeg)).rotateZ(Math.toRadians(rollDeg));
    }

    /** {forward, up, right} unit vectors in world space. */
    public static Vec3[] basis(double yawDeg, double pitchDeg, double rollDeg) {
        double y = Math.toRadians(yawDeg), t = Math.toRadians(pitchDeg), p = Math.toRadians(rollDeg);
        Vec3 f0 = new Vec3(-Math.sin(y), 0, Math.cos(y));
        Vec3 r0 = new Vec3(-Math.cos(y), 0, -Math.sin(y));
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 f1 = f0.scale(Math.cos(t)).add(up.scale(Math.sin(t)));
        Vec3 u1 = up.scale(Math.cos(t)).subtract(f0.scale(Math.sin(t)));
        Vec3 r2 = r0.scale(Math.cos(p)).subtract(u1.scale(Math.sin(p)));
        Vec3 u2 = u1.scale(Math.cos(p)).add(r0.scale(Math.sin(p)));
        return new Vec3[]{f1, u2, r2};
    }

    public Vec3[] basis() {
        return basis(this.getYRot(), this.pitch, this.roll);
    }

    /** Model-frame point (x left, y up, z forward) to world coordinates. */
    public Vec3 localToWorld(double lx, double ly, double lz) {
        Vec3[] b = this.basis();
        double py = this.pivotY();
        double y = ly - py;
        return new Vec3(this.getX(), this.getY() + py, this.getZ())
                .add(b[2].scale(-lx)).add(b[1].scale(y)).add(b[0].scale(lz));
    }

    /** Integrate body rates (deg/s) for one tick through a quaternion and write yaw/pitch/roll back. */
    protected void integrateAttitude(double dt) {
        Quaterniond q = orientation(this.getYRot(), this.pitch, this.roll);
        q.rotateZ(Math.toRadians(this.rateP * dt));
        q.rotateX(Math.toRadians(-this.rateQ * dt));
        q.rotateY(Math.toRadians(-this.rateR * dt));
        q.normalize();
        Vector3d f = q.transform(new Vector3d(0, 0, 1));
        Vector3d u = q.transform(new Vector3d(0, 1, 0));
        double yaw = Math.toDegrees(Math.atan2(-f.x, f.z));
        double pit = Math.toDegrees(Math.asin(Mth.clamp(f.y, -1.0, 1.0)));
        double yr = Math.toRadians(yaw), tr = Math.toRadians(pit);
        Vec3 f0 = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
        Vec3 r0 = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
        Vec3 u1 = new Vec3(0, Math.cos(tr), 0).subtract(f0.scale(Math.sin(tr)));
        Vec3 uu = new Vec3(u.x, u.y, u.z);
        double rol = Math.toDegrees(Math.atan2(uu.dot(r0), uu.dot(u1)));
        float prevYaw = this.getYRot();
        this.setYRot(prevYaw + Mth.wrapDegrees((float) yaw - prevYaw));
        this.pitch = (float) pit;
        this.roll = (float) rol;
    }

    public float pitchLerp(float pt) { return Mth.lerp(pt, this.pitchO, this.pitch); }
    public float rollLerp(float pt) { return this.rollO + Mth.wrapDegrees(this.roll - this.rollO) * pt; }

    // ------------------------------------------------------------------------------------------ seats

    @Nullable
    public UUID seatUuid(int seat) {
        CompoundTag t = this.entityData.get(SEATS);
        return t.hasUUID("s" + seat) ? t.getUUID("s" + seat) : null;
    }

    private void setSeatUuid(int seat, @Nullable UUID id) {
        CompoundTag t = this.entityData.get(SEATS).copy();
        if (id == null) t.remove("s" + seat); else t.putUUID("s" + seat, id);
        this.entityData.set(SEATS, t);
    }

    public int seatOf(Entity e) {
        for (int i = 0; i < this.kind.seats.length; i++) if (e.getUUID().equals(this.seatUuid(i))) return i;
        return -1;
    }

    @Nullable
    public Entity passengerIn(int seat) {
        UUID id = this.seatUuid(seat);
        if (id == null) return null;
        for (Entity e : this.getPassengers()) if (e.getUUID().equals(id)) return e;
        return null;
    }

    @Nullable
    public Player pilot() {
        return this.passengerIn(0) instanceof Player p ? p : null;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.passengerIn(0) instanceof LivingEntity le ? le : null;
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return false;
    }

    public boolean isLocalPilot() {
        return this.level().isClientSide && localPilot.test(this);
    }

    @Override
    protected boolean canAddPassenger(Entity p) {
        return this.getPassengers().size() < this.kind.seats.length;
    }

    @Override
    protected void addPassenger(Entity p) {
        super.addPassenger(p);
        if (this.level().isClientSide) return;
        int s = this.pendingSeat;
        this.pendingSeat = -1;
        if (s < 0 || this.seatUuid(s) != null) {
            s = -1;
            for (int i = 0; i < this.kind.seats.length; i++) if (this.seatUuid(i) == null) { s = i; break; }
        }
        if (s >= 0) this.setSeatUuid(s, p.getUUID());
    }

    @Override
    protected void removePassenger(Entity p) {
        super.removePassenger(p);
        if (this.level().isClientSide) return;
        int s = this.seatOf(p);
        if (s >= 0) {
            this.setSeatUuid(s, null);
            this.fireHeld[s] = false;
        }
    }

    public boolean enterSeat(ServerPlayer p, int seat) {
        if (seat < 0 || seat >= this.kind.seats.length || this.seatUuid(seat) != null) return false;
        this.pendingSeat = seat;
        boolean ok = p.startRiding(this);
        this.pendingSeat = -1;
        return ok;
    }

    public void switchSeat(Entity p) {
        int cur = this.seatOf(p);
        if (cur < 0) return;
        for (int k = 1; k < this.kind.seats.length; k++) {
            int s = (cur + k) % this.kind.seats.length;
            if (this.seatUuid(s) == null) {
                this.setSeatUuid(cur, null);
                this.setSeatUuid(s, p.getUUID());
                this.fireHeld[cur] = false;
                return;
            }
        }
    }

    public Vec3 seatWorld(int seat) {
        float[] s = this.kind.seats[Mth.clamp(seat, 0, this.kind.seats.length - 1)];
        return this.localToWorld(s[0], s[1], s[2]);
    }

    @Override
    protected void positionRider(Entity p, Entity.MoveFunction f) {
        if (!this.hasPassenger(p)) return;
        int seat = this.seatOf(p);
        if (seat < 0) seat = Math.min(this.getPassengers().indexOf(p), this.kind.seats.length - 1);
        Vec3 w = this.seatWorld(Math.max(seat, 0));
        f.accept(p, w.x, w.y - 0.85, w.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        int seat = Math.max(0, this.seatOf(p));
        float[] s = this.kind.seats[seat];
        double side = s[0] >= 0 ? 1 : -1;
        double half = this.kind.width / 2.0 + 0.9;
        double[][] cand = {{side * half, s[2]}, {-side * half, s[2]}, {side * half, s[2] - 2.5}, {0, s[2] + 3}};
        for (double[] c : cand) {
            Vec3 w = this.localToWorld(c[0], 0.2, c[1]);
            for (int dy = 0; dy <= 3; dy++) {
                AABB box = p.getBoundingBox().move(w.x - p.getX(), w.y + dy - p.getY(), w.z - p.getZ());
                if (this.level().noCollision(p, box)) return new Vec3(w.x, w.y + dy, w.z);
            }
        }
        Vec3 top = this.seatWorld(seat);
        return new Vec3(top.x, top.y + 1.2, top.z);
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    // ------------------------------------------------------------------------------------------ interaction / damage

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.dmg.dead()) return InteractionResult.PASS;
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            if (this.onItemUse(sp, sp.getItemInHand(hand))) return InteractionResult.CONSUME;
            for (int i = 0; i < this.kind.seats.length; i++) if (this.seatUuid(i) == null && this.enterSeat(sp, i)) break;
        }
        return InteractionResult.CONSUME;
    }

    /** Service kit: rearm everything, refuel, patch 30 % of the structure. Coal still adds fuel. */
    protected boolean onItemUse(ServerPlayer p, ItemStack st) {
        if (st.getItem() instanceof CraftServiceKitItem) {
            this.service(0.3f);
            if (!p.getAbilities().instabuild) st.shrink(1);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 1.0f, 0.9f);
            return true;
        }
        if ((st.is(net.minecraft.world.item.Items.COAL) || st.is(net.minecraft.world.item.Items.CHARCOAL)) && this.fuel() < 0.97f) {
            this.setFuel(this.fuel() + 0.08f);
            if (!p.getAbilities().instabuild) st.shrink(1);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 0.6f, 1.6f);
            return true;
        }
        return false;
    }

    public void service(float repair) {
        int[] a = new int[this.kind.loads.length];
        for (int i = 0; i < a.length; i++) a[i] = this.kind.loads[i].full();
        this.setAmmoArray(a);
        this.setFuel(1f);
        this.setHp(this.hp() + this.kind.hp * repair);
        if (this.kind.isSub()) this.entityData.set(AUX3, 1f);
        if (!this.dmg.dead()) {
            this.dmg.clearAll();
            this.publishDamage();
        }
    }

    @Override
    public boolean isPickable() { return !this.isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return !this.kind.isAir(); }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isAttackable() { return true; }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (this.level().isClientSide || this.isRemoved()) return false;
        Entity att = src.getEntity();
        if (att != null && att.getVehicle() == this && src.getDirectEntity() == att) return false;
        if (att instanceof Player pl && pl.getAbilities().instabuild && src.getDirectEntity() == pl) {
            this.spawnAtLocation(new ItemStack(CraftRegistry.item(this.kind).get()));
            this.ejectPassengers();
            this.discard();
            return true;
        }
        if (!RWConfig.COMMON.vehicleDamage.get() || src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_FALL)
                || src.is(DamageTypeTags.IS_DROWNING)) return false;
        if (this.dmg.dead()) return true;
        ru.aiforge.realwar.damage.CraftDamage.onHurt(this, src, amount);
        if (this.isRemoved() || this.dmg.dead()) return true;
        float eff = att instanceof Player && src.getDirectEntity() == att ? amount * 0.05f : amount * 0.6f;
        String id = src.getMsgId();
        if (src.is(DamageTypeTags.IS_EXPLOSION) || id.contains("blast") || id.contains("ordnance") || id.contains("crash")) eff = amount;
        if (id.contains("bullet")) eff = amount * (this.kind.isAir() ? 0.5f : 0.25f);
        this.damage(eff);
        return true;
    }

    public void damage(float eff) {
        if (this.level().isClientSide || this.isRemoved() || eff <= 0) return;
        float h = this.hp() - eff;
        this.entityData.set(HP, Math.max(0f, h));
        if (h <= 0f) {
            if (ru.aiforge.realwar.damage.CraftDamage.handlesDeath(this) && !this.dmg.dead()) ru.aiforge.realwar.damage.CraftDamage.kill(this);
            else this.destroy();
        }
        else if (eff > 2f) this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f, 1.8f);
    }

    /** TNT-equivalent of fuel + ammunition cook-off. */
    protected double cookOffKg() {
        return this.kind.isSub() ? 300 : this.kind.isHeli() ? 45 : 25;
    }

    protected void destroy() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 c = this.localToWorld(0, this.kind.height * 0.5, 0);
        this.ejectPassengers();
        this.discard();
        ConventionalBlast.explode(sl, c, this.cookOffKg(), 1.0);
        for (int i = 0; i < 5 + sl.random.nextInt(5); i++) {
            ItemEntity it = new ItemEntity(sl, c.x, c.y, c.z, new ItemStack(ModItems.STEEL_PLATE.get()));
            it.setDeltaMovement(sl.random.nextGaussian() * 0.3, 0.4 + sl.random.nextDouble() * 0.4, sl.random.nextGaussian() * 0.3);
            sl.addFreshEntity(it);
        }
    }

    /** Collision / landing loads reported as velocity lost in one tick. */
    protected void crashDamage(float dv) {
        if (this.dmg.dead() && this.kind.isAir() && dv > 2f) {
            this.destroy();
            return;
        }
        float safe = this.kind.isHeli() ? 4.0f : 6.0f;
        if (dv <= safe) return;
        float dmg = (dv - safe) * (dv - safe) * (this.kind.isHeli() ? 3.2f : 1.4f);
        this.damage(dmg);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), this.getX(), this.getY() + 1, this.getZ(), 10, 1, 0.5, 1, 0.02);
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, Math.min(3f, dv / 6f), 1.3f);
        }
        for (Entity e : this.getPassengers()) e.hurt(this.level().damageSources().fall(), Math.max(0, dv - 9f) * 1.6f);
    }

    // ------------------------------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        this.pitchO = this.pitch;
        this.rollO = this.roll;
        this.auxO = this.aux;
        this.aux2O = this.aux2;
        this.rotorAngleO = this.rotorAngle;
        this.gunYawO = this.gunYawC;
        this.gunPitchO = this.gunPitchC;
        this.waterCache.clear();
        boolean client = this.level().isClientSide;
        boolean local = client && this.isLocalPilot();
        if (client && !local) {
            this.tickLerp();
            this.pitch += Mth.wrapDegrees(this.entityData.get(PITCH) - this.pitch) * 0.5f;
            this.roll += Mth.wrapDegrees(this.entityData.get(ROLL) - this.roll) * 0.5f;
            this.aux += (this.entityData.get(AUX) - this.aux) * 0.5f;
            this.aux2 = this.entityData.get(AUX2);
            this.aux3 = this.entityData.get(AUX3);
            this.vel = this.getDeltaMovement().scale(20.0 / this.worldScale());
        }
        if (local && !this.seeded) {
            // a client copy that is (re)created while we pilot it (lag spike, re-tracking, chunk reload) must start
            // from the server's state - not from constructor defaults (rotor 0 = a helicopter falling out of a hover)
            this.pitch = this.pitchO = this.entityData.get(PITCH);
            this.roll = this.rollO = this.entityData.get(ROLL);
            this.aux = this.auxO = this.entityData.get(AUX);
            this.aux2 = this.aux2O = this.entityData.get(AUX2);
            this.aux3 = this.entityData.get(AUX3);
            this.vel = this.getDeltaMovement().scale(20.0 / this.worldScale());
        }
        this.seeded = local;
        boolean authority = local || (!client && this.pilot() == null);
        if (authority) {
            Vec3 before = this.position();
            float[] in = local ? clientInput.apply(this) : this.forced;
            if (in == null || this.dmg.dead()) in = new float[8];
            this.impact = 0;
            this.physics(in);
            if (local) {
                this.setDeltaMovement(this.position().subtract(before));
                statePublisher.accept(this);
            } else {
                this.publishServer();
                if (this.impact > 0) this.crashDamage(this.impact);
            }
        }
        this.gunYawC += Mth.wrapDegrees(this.gunYaw() - this.gunYawC) * 0.5f;
        this.gunPitchC += (this.gunPitch() - this.gunPitchC) * 0.5f;
        this.animate();
        if (!client) {
            this.serverTick();
            if (!this.isRemoved()) ru.aiforge.realwar.damage.CraftDamage.tick(this);
        } else {
            clientFx.accept(this);
            CompoundTag dt = this.entityData.get(DMG);
            if (dt != this.dmgSeen) {
                this.dmgSeen = dt;
                this.dmg.readSync(dt);
            }
            ru.aiforge.realwar.damage.DamageFx.craft(this);
        }
    }

    private void publishServer() {
        this.entityData.set(PITCH, this.pitch);
        this.entityData.set(ROLL, this.roll);
        this.entityData.set(AUX, this.aux);
        this.entityData.set(AUX2, this.aux2);
        this.entityData.set(AUX3, this.aux3);
        this.entityData.set(SPEED, (float) this.vel.length());
        this.setDeltaMovement(this.vel.scale(DT * this.worldScale()));
    }

    /** World blocks moved per real metre of the flight model (aeroplanes run at the Tu bombers' speed scale). */
    protected double worldScale() {
        return 1.0;
    }

    /** Render-side animation state (rotor azimuth etc.), runs on both sides. */
    protected void animate() {}

    /** One physics step (dt = 0.05 s) with the given input; see {@link #inputLayout()}. */
    protected abstract void physics(float[] in);

    /** Input array: 0 W/S, 1 A/D, 2 mouse dx (deg/tick), 3 mouse dy up (deg/tick), 4 Space, 5 C. */
    public static String inputLayout() {
        return "throttle,steer,mouseX,mouseY,up,down";
    }

    public void setForcedInput(float... in) {
        float[] f = new float[8];
        System.arraycopy(in, 0, f, 0, Math.min(8, in.length));
        this.forced = f;
    }

    protected void serverTick() {
        // fuel
        if (this.engineOn()) {
            float burnPerTick = this.fuelBurnPerTick();
            this.setFuel(this.fuel() - burnPerTick);
            if (this.fuel() <= 0f) this.entityData.set(ENGINE, false);
        }
        for (int i = 0; i < this.cooldown.length; i++) if (this.cooldown[i] > 0) this.cooldown[i]--;
        this.updateTurretAim();
        for (int s = 0; s < this.kind.seats.length && s < this.fireHeld.length; s++) {
            boolean held = this.fireHeld[s] && this.tickCount - this.inputTick[s] <= 4;
            if (!held) { this.fireLatch[s] = false; continue; }
            this.tryFire(s);
        }
        // submarine crews breathe; helicopter in the water drowns its engine
        for (Entity e : this.getPassengers()) if (this.kind.isSub() || !this.kind.isAir()) e.setAirSupply(e.getMaxAirSupply());
        if (this.tickCount % 20 == 0 && this.hp() < this.kind.hp * 0.3f && this.level() instanceof ServerLevel sl) {
            Vec3 c = this.localToWorld(0, this.kind.height * 0.8, -1);
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.02);
        }
    }

    protected float fuelBurnPerTick() {
        return 1f / (20f * 60f * 40f);
    }

    // ------------------------------------------------------------------------------------------ network

    public void acceptClientState(ServerPlayer p, double x, double y, double z, double vx, double vy, double vz,
                                  float yaw, float pit, float rol, float a1, float a2, float a3, float imp) {
        if (this.pilot() != p) return;
        if (!Double.isFinite(x + y + z + vx + vy + vz + yaw + pit + rol + a1 + a2 + a3 + imp)) return;
        if (y < this.level().getMinBuildHeight() - 32 || y > this.level().getMaxBuildHeight() + 256) return;
        double dx = x - this.getX(), dy = y - this.getY(), dz = z - this.getZ();
        double max = 15.0;
        if (dx * dx + dy * dy + dz * dz > max * max || !this.level().hasChunkAt(BlockPos.containing(x, y, z))) {
            if (++this.rejected > 3) {
                this.rejected = 0;
                ModNetwork.sendToPlayer(p, new CraftSnapPacket(this.getId(), this.getX(), this.getY(), this.getZ()));
            }
            return;
        }
        this.rejected = 0;
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.pitch = pit;
        this.roll = rol;
        this.vel = new Vec3(vx, vy, vz);
        this.aux = a1;
        this.aux2 = a2;
        this.aux3 = a3;
        this.publishServer();
        if (imp > 0) this.crashDamage(Math.min(imp, 60f));
    }

    /** Control packet (all crew): fire trigger and one-shot actions. */
    public void handleControl(ServerPlayer p, boolean fire, int actions) {
        int seat = this.seatOf(p);
        if (seat < 0 || seat >= this.fireHeld.length) return;
        this.fireHeld[seat] = fire;
        this.inputTick[seat] = this.tickCount;
        if ((actions & CraftControlPacket.A_ENGINE) != 0 && seat == 0) {
            boolean on = !this.engineOn();
            this.setEngine(on);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.ENGINE_START.get(), SoundSource.NEUTRAL, on ? 2.2f : 1.0f, on ? 0.8f : 0.5f);
        }
        if ((actions & CraftControlPacket.A_WEAPON) != 0) this.cycleWeapon(seat);
        if ((actions & CraftControlPacket.A_MODE) != 0 && seat == 0) this.setFlag(FLAG_STAB, !this.flag(FLAG_STAB));
        if ((actions & CraftControlPacket.A_SEAT) != 0) this.switchSeat(p);
        if ((actions & CraftControlPacket.A_FLARE) != 0) this.fireFlares();
        if ((actions & CraftControlPacket.A_GEAR) != 0 && seat == 0 && this.kind.isAir()) this.setFlag(FLAG_GEAR, !this.flag(FLAG_GEAR));
        if ((actions & CraftControlPacket.A_LASER) != 0) this.setFlag(FLAG_LASER, !this.flag(FLAG_LASER));
        if ((actions & CraftControlPacket.A_PERISCOPE) != 0) this.setFlag(FLAG_PERISCOPE, !this.flag(FLAG_PERISCOPE));
    }

    // ------------------------------------------------------------------------------------------ weapons

    /** Weapons this seat may operate right now (gunner-only weapons go to the pilot when he flies alone). */
    public boolean canUse(int seat, int load) {
        CraftKind.Load l = this.kind.loads[load];
        if (l.weapon() == CraftWeapon.FLARES) return false;    // flares have their own key
        // r9: one person runs everything - the pilot / skipper (seat 0) operates every weapon, other seats ride along
        return seat == 0;
    }

    public void cycleWeapon(int seat) {
        int n = this.kind.loads.length;
        int cur = this.selected(seat);
        for (int k = 1; k <= n; k++) {
            int i = (cur + k) % n;
            if (this.canUse(seat, i)) { this.setSelected(seat, i); return; }
        }
    }

    /** r20: number keys - slot = position on the HUD slot bar (usable weapons + flares, in load order). */
    public void selectSlot(int seat, int slot) {
        int k = 0;
        for (int i = 0; i < this.kind.loads.length; i++) {
            CraftKind.Load l = this.kind.loads[i];
            if (!(l.weapon() == CraftWeapon.FLARES || this.canUse(seat, i))) continue;
            if (k++ != slot) continue;
            if (l.weapon() == CraftWeapon.FLARES) this.fireFlares();
            else this.setSelected(seat, i);
            return;
        }
    }

    /** Effective selection (falls back to the first usable weapon). */
    public int weaponFor(int seat) {
        int s = this.selected(seat);
        if (this.canUse(seat, s)) return s;
        for (int i = 0; i < this.kind.loads.length; i++) if (this.canUse(seat, i)) return i;
        return -1;
    }

    /** Seat whose occupant aims the turret: the gunner, else the pilot. */
    protected int aimingSeat() {
        return 0;
    }

    /** Clamp a world look direction into a turret's limits; returns {az (deg, + left), el (deg)}. */
    public float[] turretAngles(Vec3 look, CraftKind.Load l) {
        Vec3[] b = this.basis();
        double lf = look.dot(b[0]), ll = -look.dot(b[2]), lu = look.dot(b[1]);
        double az = Math.toDegrees(Math.atan2(ll, lf));
        double el = Math.toDegrees(Math.atan2(lu, Math.sqrt(lf * lf + ll * ll)));
        az = Mth.clamp(az, -l.az(), l.az());
        el = Mth.clamp(el, l.elMin(), l.elMax());
        return new float[]{(float) az, (float) el};
    }

    public Vec3 dirFromAngles(float az, float el) {
        Vec3[] b = this.basis();
        double a = Math.toRadians(az), e = Math.toRadians(el);
        return b[0].scale(Math.cos(e) * Math.cos(a)).add(b[2].scale(-Math.cos(e) * Math.sin(a))).add(b[1].scale(Math.sin(e))).normalize();
    }

    protected void updateTurretAim() {
        for (CraftKind.Load l : this.kind.loads) {
            if (!l.turret()) continue;
            Entity e = this.passengerIn(this.aimingSeat());
            if (e == null) return;
            float[] a = this.turretAngles(e.getLookAngle(), l);
            // turret slew rate limit: 90 deg/s
            float cy = this.gunYaw(), cp = this.gunPitch();
            float ny = cy + Mth.clamp(a[0] - cy, -4.5f, 4.5f), np = cp + Mth.clamp(a[1] - cp, -3.5f, 3.5f);
            this.entityData.set(GUN_YAW, ny);
            this.entityData.set(GUN_PITCH, np);
            return;
        }
    }

    /** Line of sight of the crew member guiding missiles (gunner, else pilot); null when nobody aboard. */
    @Nullable
    public Entity guidanceOperator() {
        return this.passengerIn(0);
    }

    protected void tryFire(int seat) {
        int li = this.weaponFor(seat);
        if (li < 0) return;
        CraftKind.Load l = this.kind.loads[li];
        CraftWeapon w = l.weapon();
        boolean semi = w.guided() || w.type == CraftWeapon.Type.TORPEDO;
        if (semi && this.fireLatch[seat]) return;
        if (this.cooldown[li] > 0) return;
        int[] a = this.ammoArray();
        if (a[li] <= 0) {
            if (!this.fireLatch[seat]) this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.NEUTRAL, 1f, 1.2f);
            this.fireLatch[seat] = true;
            return;
        }
        if (this.kind.isSub() && !this.underwaterForTorpedo()) return;
        Entity shooter = this.passengerIn(seat);
        a[li]--;
        this.setAmmoArray(a);
        this.cooldown[li] = w.interval;
        this.fireLatch[seat] = true;
        this.fire(li, shooter);
    }

    protected boolean underwaterForTorpedo() {
        float[] m = this.kind.loads[0].muzzles()[0];
        Vec3 p = this.localToWorld(m[0], m[1], m[2] + 1.5);
        // tube mouth in water, or buried in the bed with water above it (sub resting on the bottom); never in air
        BlockPos.MutableBlockPos mp = BlockPos.containing(p).mutable();
        for (int i = 0; i < 4; i++, mp.move(0, 1, 0)) {
            if (this.level().getFluidState(mp).is(FluidTags.WATER)) return true;
            if (this.level().getBlockState(mp).isAir()) return false;
        }
        return false;
    }

    protected Vec3 weaponDir(CraftKind.Load l, @Nullable Entity shooter) {
        if (l.turret()) return this.dirFromAngles(this.gunYaw(), this.gunPitch());
        Vec3 f = this.basis()[0];
        if (l.weapon().type == CraftWeapon.Type.CANNON && shooter != null) {
            // limited-traverse gun (Ka-52 2A42): follows the sight within its small azimuth/elevation window
            return f;
        }
        return f;
    }

    protected void fire(int li, @Nullable Entity shooter) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        CraftKind.Load l = this.kind.loads[li];
        CraftWeapon w = l.weapon();
        float[][] mz = l.muzzles();
        int mi = this.muzzleNext[li] % mz.length;
        this.muzzleNext[li] = mi + 1;
        Vec3 dir = this.weaponDir(l, shooter);
        Vec3 m = this.localToWorld(mz[mi][0], mz[mi][1], mz[mi][2]);
        if (l.turret()) m = m.add(dir.scale(w.type == CraftWeapon.Type.CANNON ? 1.4 : 1.0));
        Vec3 inherit = this.vel.scale(DT * this.worldScale());
        switch (w.type) {
            case CANNON -> {
                Vec3 d = jitter(dir, 0.0025);
                ShellEntity.fire(sl, w == CraftWeapon.GUN_2A42 ? Ballistics.Profile.A42_HE : Ballistics.Profile.M230_HE, m, d, this.getId());
                this.sound(ModSounds.CRAFT_CANNON.get(), 4.0f, w == CraftWeapon.GUN_2A42 ? 0.9f : 1.05f);
            }
            case MG -> {
                Vec3 d = jitter(dir, 0.003);
                BulletManager.spawn(sl, m, d.scale(w == CraftWeapon.MG_KPV ? 32.0 : 28.0).add(inherit), this.getId(), this.tickCount % 3 == 0);
                this.sound(ModSounds.CRAFT_MG.get(), 3.0f, w == CraftWeapon.MG_KPV ? 0.85f : 1.0f);
            }
            case ROCKET -> {
                Vec3 d = jitter(dir, 0.006);
                CraftOrdnance.launch(sl, w == CraftWeapon.S8 ? CraftOrdnance.Type.S8 : CraftOrdnance.Type.HYDRA, this, m, d, inherit, -1);
                this.sound(ModSounds.ROCKET_LAUNCH_SMALL.get(), 3.0f, 1.25f);
            }
            case ATGM_BEAM, ATGM_SAL -> {
                Entity op = this.guidanceOperator();
                CraftOrdnance.launch(sl, w == CraftWeapon.VIKHR || w == CraftWeapon.KH38 ? CraftOrdnance.Type.VIKHR : CraftOrdnance.Type.HELLFIRE, this, m,
                        dir.add(0, 0.02, 0).normalize(), inherit, op != null ? op.getId() : -1);
                this.sound(ModSounds.SAM_LAUNCH.get(), 3.0f, 1.4f);
            }
            case CRUISE -> {
                // target: the point under the commander's sight (periscope), else 3 km along the sight line
                Vec3 eye = shooter != null ? shooter.getEyePosition() : m;
                Vec3 look = shooter != null ? shooter.getLookAngle() : this.basis()[0];
                Vec3 far = eye.add(look.scale(3000));
                net.minecraft.world.phys.BlockHitResult bh = sl.clip(new net.minecraft.world.level.ClipContext(eye, far,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
                Vec3 spot = bh.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? new Vec3(far.x, eye.y, far.z) : bh.getLocation();
                if (spot.distanceTo(m) < 400) spot = m.add(new Vec3(look.x, 0, look.z).normalize().scale(1500)); // minimum range
                CraftOrdnance k = CraftOrdnance.launch(sl, CraftOrdnance.Type.KALIBR, this, m, this.basis()[0], inherit, -1);
                k.v = this.basis()[0].scale(4.0).add(0, 6.0, 0);
                k.setSpot(spot);
                this.lastKalibrTarget = spot;
                this.sound(ModSounds.TORPEDO_LAUNCH.get(), 4.0f, 0.8f);
            }
            case TORPEDO -> {
                CraftOrdnance.launch(sl, CraftOrdnance.Type.TORPEDO, this, m, this.basis()[0], inherit, -1);
                this.sound(ModSounds.TORPEDO_LAUNCH.get(), 4.0f, 1.0f);
            }
            case FLARE -> { }
        }
        sl.broadcastEntityEvent(this, (byte) (160 + li * 8 + mi));
    }

    public void fireFlares() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        int li = this.kind.loadIndex(CraftWeapon.FLARES);
        if (li < 0 || this.cooldown[li] > 0) return;
        int[] a = this.ammoArray();
        if (a[li] <= 0) return;
        int n = Math.min(a[li], 4);
        a[li] -= n;
        this.setAmmoArray(a);
        this.cooldown[li] = CraftWeapon.FLARES.interval;
        Vec3[] b = this.basis();
        float[][] mz = this.kind.loads[li].muzzles();
        for (int i = 0; i < n; i++) {
            float[] p = mz[i % mz.length];
            Vec3 m = this.localToWorld(p[0], p[1], p[2]);
            double side = p[0] >= 0 ? -1 : 1;    // left dispenser throws left (+X local = -right)
            Vec3 d = b[2].scale(side * 0.8).add(b[1].scale(-0.45)).add(b[0].scale(-0.25 - 0.15 * i)).normalize();
            CraftOrdnance.launch(sl, CraftOrdnance.Type.FLARE, this, m, d, this.vel.scale(DT), -1);
        }
        this.sound(ModSounds.FLARE_POP.get(), 2.5f, 1.0f);
    }

    private static Vec3 jitter(Vec3 d, double s) {
        java.util.Random r = new java.util.Random();
        return d.add(r.nextGaussian() * s, r.nextGaussian() * s, r.nextGaussian() * s).normalize();
    }

    protected void sound(SoundEvent e, float vol, float pitch) {
        this.level().playSound(null, this.getX(), this.getY() + 1, this.getZ(), e, SoundSource.NEUTRAL, vol, pitch);
    }

    @Override
    public void handleEntityEvent(byte id) {
        int v = id & 0xFF;
        if (v >= 160 && v < 240) clientEvent.accept(this, v - 160);
        else super.handleEntityEvent(id);
    }

    // ------------------------------------------------------------------------------------------ environment

    /** Water surface height above a column or NaN; samples blocks from yLow up to yHigh (+1). */
    public double waterSurface(double x, double z, double yLow, double yHigh) {
        int bx = Mth.floor(x), bz = Mth.floor(z), y0 = Mth.floor(yLow), y1 = Mth.floor(yHigh) + 1;
        long key = (((long) bx) << 38) ^ (((long) bz) << 12) ^ (y0 & 0xFFF) ^ ((long) y1 << 50);
        Double c = this.waterCache.get(key);
        if (c != null) return c;
        double surf = Double.NaN;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        boolean seen = false;
        int bed = 0;
        for (int by = y0; by <= y1; by++) {
            mp.set(bx, by, bz);
            FluidState fs = this.level().getFluidState(mp);
            if (fs.is(FluidTags.WATER)) {
                seen = true;
                surf = by + fs.getHeight(this.level(), mp);
            } else if (seen) {
                break;
            } else if (bed < 3 && !this.level().getBlockState(mp).isAir()) {
                bed++;      // hull resting on / dug into the sea bed: the water starts above it (sub lost all buoyancy on the bottom)
            } else if (by > y0) {
                break;
            }
        }
        if (seen && surf >= y1 - 0.001) surf = 1e9;   // fully submerged column
        this.waterCache.put(key, surf);
        return surf;
    }

    /** Height above the ground (or water) below a point, capped at max. */
    public double heightAbove(Vec3 p, double max) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(p.x), bz = Mth.floor(p.z), top = Mth.floor(p.y);
        for (int dy = 0; dy <= (int) max; dy++) {
            mp.set(bx, top - dy, bz);
            if (!this.level().getBlockState(mp).getCollisionShape(this.level(), mp).isEmpty()
                    || this.level().getFluidState(mp).is(FluidTags.WATER)) {
                double surf = top - dy + 1.0;
                return Math.max(0, p.y - surf);
            }
        }
        return max;
    }

    /** Small wind-driven waves on open water (m). */
    protected double wave(double x, double z) {
        double t = (this.level().getGameTime() + (this.level().isClientSide ? 0 : 0)) * 0.05;
        double amp = this.level().isThundering() ? 0.45 : this.level().isRaining() ? 0.22 : 0.08;
        return amp * (Math.sin(t * 1.1 + x * 0.21) * 0.6 + Math.sin(t * 0.7 + z * 0.17 + 1.3) * 0.4);
    }

    // ------------------------------------------------------------------------------------------ lerp / save

    @Override
    public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
        if (this.isLocalPilot()) return;
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yr;
        this.lerpSteps = 3;
    }

    private void tickLerp() {
        if (this.lerpSteps > 0) {
            double nx = this.getX() + (this.lerpX - this.getX()) / this.lerpSteps;
            double ny = this.getY() + (this.lerpY - this.getY()) / this.lerpSteps;
            double nz = this.getZ() + (this.lerpZ - this.getZ()) / this.lerpSteps;
            double dyaw = Mth.wrapDegrees(this.lerpYaw - this.getYRot());
            this.setYRot(this.getYRot() + (float) dyaw / this.lerpSteps);
            --this.lerpSteps;
            this.setPos(nx, ny, nz);
        }
    }

    /** Server snap (rejected client state): put the local pilot back where the server has us. */
    public void snapTo(double x, double y, double z) {
        this.setPos(x, y, z);
        this.vel = Vec3.ZERO;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(HP, tag.contains("hp") ? tag.getFloat("hp") : this.kind.hp);
        this.entityData.set(FUEL, tag.contains("fuel") ? tag.getFloat("fuel") : 1f);
        this.pitch = tag.getFloat("pitch");
        this.roll = tag.getFloat("roll");
        if (tag.contains("ammo")) {
            int[] a = tag.getIntArray("ammo");
            if (a.length == this.kind.loads.length) {
                this.setAmmoArray(a.clone());
            } else {
                // saved before a weapon was added (e.g. Kalibr on the 636.3): keep the old counts, new loads full
                int[] f = new int[this.kind.loads.length];
                for (int i = 0; i < f.length; i++) f[i] = i < a.length ? a[i] : this.kind.loads[i].full();
                this.setAmmoArray(f);
            }
        } else {
            this.initFresh();
        }
        this.aux2 = tag.getFloat("aux2");
        this.aux = tag.getFloat("aux");
        this.aux3 = tag.contains("aux3") ? tag.getFloat("aux3") : (this.kind.isSub() ? 1f : 0f);
        this.entityData.set(AUX3, this.aux3);
        // the synced copies too: a pilot reconnecting seeds its local physics from them (ballast / throttle / rotor
        // state used to come back as 0 after every rejoin - a submerged sub blew itself to the surface)
        this.entityData.set(AUX, this.aux);
        this.entityData.set(AUX2, this.aux2);
        this.entityData.set(PITCH, this.pitch);
        this.entityData.set(ROLL, this.roll);
        this.entityData.set(FLAGS, tag.contains("flags") ? tag.getByte("flags") : (byte) FLAG_GEAR);
        this.dmg.load(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("hp", this.hp());
        tag.putFloat("fuel", this.fuel());
        tag.putFloat("pitch", this.pitch);
        tag.putFloat("roll", this.roll);
        tag.putIntArray("ammo", this.ammoArray());
        this.dmg.save(tag);
        tag.putFloat("aux", this.aux);
        tag.putFloat("aux2", this.aux2);
        tag.putFloat("aux3", this.aux3);
        tag.putByte("flags", (byte) this.flags());
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(CraftRegistry.item(this.kind).get());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        double r = this.kind == CraftKind.BISMARCK ? 900 : this.kind.isSub() ? 400 : 300;
        return d < r * r;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = this.kind.extent() / 2 + 1;
        return this.getBoundingBox().inflate(r, this.kind == CraftKind.BISMARCK ? 45 : 3, r);
    }

    /** Entities in the way of a long hull: probe solid blocks ahead (boats/sub). */
    protected List<Entity> nearbyCrafts(double r) {
        return this.level().getEntities(this, this.getBoundingBox().inflate(r), e -> e instanceof CraftEntity);
    }
}
