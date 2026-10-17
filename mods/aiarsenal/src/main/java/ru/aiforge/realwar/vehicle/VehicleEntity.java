/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientGamePacketListener
 *  net.minecraft.network.syncher.EntityDataAccessor
 *  net.minecraft.network.syncher.EntityDataSerializer
 *  net.minecraft.network.syncher.EntityDataSerializers
 *  net.minecraft.network.syncher.SynchedEntityData
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.tags.DamageTypeTags
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.Entity$MoveFunction
 *  net.minecraft.world.entity.EntityDimensions
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.MoverType
 *  net.minecraft.world.entity.Pose
 *  net.minecraft.world.entity.item.ItemEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.network.NetworkHooks
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.vehicle;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.network.VehicleSnapPacket;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModSounds;
import ru.aiforge.realwar.vehicle.VehicleKind;
import ru.aiforge.realwar.vehicle.VehicleSpec;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public abstract class VehicleEntity
extends Entity implements ru.aiforge.realwar.damage.Damageable {
    protected static final EntityDataAccessor<net.minecraft.nbt.CompoundTag> DMG = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** r18: War Thunder style module damage (tracks, engine, barrel, turret, ammo, fire, holes). */
    public final ru.aiforge.realwar.damage.DamageState dmg = new ru.aiforge.realwar.damage.DamageState();
    private net.minecraft.nbt.CompoundTag dmgSeen;

    @Override
    public ru.aiforge.realwar.damage.DamageState dmg() {
        return this.dmg;
    }

    @Override
    public void blastNear(Vec3 centre, double tntKg, double dist) {
        ru.aiforge.realwar.damage.VehicleDamage.blastNear(this, centre, tntKg, dist);
    }

    public void publishDamage() {
        this.dmg.dirty = false;
        this.dmg.dirtyNow = false;
        this.entityData.set(DMG, this.dmg.sync());
    }

    public void setHp(float h) {
        this.entityData.set(HP, Float.valueOf(h));
    }

    protected static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(VehicleEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Boolean> ENGINE = SynchedEntityData.defineId(VehicleEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(VehicleEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(VehicleEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(VehicleEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Optional<UUID>> SEAT0 = SynchedEntityData.defineId(VehicleEntity.class, (EntityDataSerializer)EntityDataSerializers.OPTIONAL_UUID);
    protected static final EntityDataAccessor<Optional<UUID>> SEAT1 = SynchedEntityData.defineId(VehicleEntity.class, (EntityDataSerializer)EntityDataSerializers.OPTIONAL_UUID);
    public static final float SEAT_FEET = 0.85f;
    public static final float[] GEAR_TOP = new float[]{0.12f, 0.25f, 0.42f, 0.65f, 1.0f};
    public static Function<VehicleEntity, float[]> clientInput = e -> new float[3];
    public static Consumer<VehicleEntity> clientHook = e -> {};
    public static Consumer<VehicleEntity> statePublisher = e -> {};
    public static Predicate<VehicleEntity> localDriver = e -> false;
    private int rejected;
    public final VehicleKind kind;
    public final VehicleSpec spec;
    public double speedMs;
    public float steerVis;
    public float steerVisO;
    public double rollDist;
    public double rollDistO;
    public int gear = 1;
    public float rpm;
    public float rpmO;
    public float temp = 0.25f;
    protected double vy;
    protected float[] forced;
    protected int stationaryTicks;
    protected int pendingSeat = -1;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;
    protected final boolean[] fireHeld = new boolean[2];
    protected final boolean[] reloadHeld = new boolean[2];
    protected final int[] inputTick = new int[2];

    protected VehicleEntity(EntityType<?> type, Level level, VehicleKind kind) {
        super(type, level);
        this.kind = kind;
        this.spec = kind.spec;
        this.entityData.set(HP, Float.valueOf(kind.hp));
        this.blocksBuilding = true;
        this.noCulling = true;
    }

    protected void defineSynchedData() {
        this.entityData.define(HP, Float.valueOf(1.0f));
        this.entityData.define(ENGINE, false);
        this.entityData.define(THROTTLE, Float.valueOf(0.0f));
        this.entityData.define(STEER, Float.valueOf(0.0f));
        this.entityData.define(FUEL, Float.valueOf(0.85f));
        this.entityData.define(SEAT0, Optional.empty());
        this.entityData.define(SEAT1, Optional.empty());
        this.entityData.define(DMG, new net.minecraft.nbt.CompoundTag());
    }

    public float hp() {
        return ((Float)this.entityData.get(HP)).floatValue();
    }

    public boolean engineOn() {
        return (Boolean)this.entityData.get(ENGINE);
    }

    public float throttle() {
        return ((Float)this.entityData.get(THROTTLE)).floatValue();
    }

    public float steerCmd() {
        return ((Float)this.entityData.get(STEER)).floatValue();
    }

    public float fuel() {
        return ((Float)this.entityData.get(FUEL)).floatValue();
    }

    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable((float)this.spec.aabbW(), (float)this.spec.aabbH());
    }

    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate((double)this.spec.length() * 0.5 + 2.0);
    }

    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 65536.0;
    }

    public float maxUpStep() {
        return 1.05f;
    }

    public UUID seatUuid(int seat) {
        return (seat == 0 ? this.entityData.get(SEAT0) : this.entityData.get(SEAT1)).orElse(null);
    }

    protected void setSeatUuid(int seat, UUID id) {
        this.entityData.set(seat == 0 ? SEAT0 : SEAT1, Optional.ofNullable(id));
    }

    public int seatOf(Entity e) {
        UUID u = e.getUUID();
        for (int i = 0; i < this.spec.seatCount(); ++i) {
            if (!u.equals(this.seatUuid(i))) continue;
            return i;
        }
        return -1;
    }

    @Nullable
    public Entity passengerIn(int seat) {
        UUID u = this.seatUuid(seat);
        if (u == null) {
            return null;
        }
        for (Entity p : this.getPassengers()) {
            if (!p.getUUID().equals(u)) continue;
            return p;
        }
        return null;
    }

    public String seatRole(int seat) {
        return this.spec.seatRoles()[Mth.clamp((int)seat, (int)0, (int)(this.spec.seatCount() - 1))];
    }

    @Nullable
    public LivingEntity getControllingPassenger() {
        LivingEntity le;
        if (!this.kind.drivable()) {
            return null;
        }
        Entity e = this.passengerIn(0);
        return e instanceof LivingEntity ? (le = (LivingEntity)e) : null;
    }

    protected boolean canAddPassenger(Entity p) {
        return this.getPassengers().size() < this.spec.seatCount();
    }

    protected void addPassenger(Entity p) {
        super.addPassenger(p);
        if (this.level().isClientSide) {
            return;
        }
        int s = this.pendingSeat;
        this.pendingSeat = -1;
        if (s < 0 || this.seatUuid(s) != null) {
            s = -1;
            for (int i = 0; i < this.spec.seatCount(); ++i) {
                if (this.seatUuid(i) != null) continue;
                s = i;
                break;
            }
        }
        if (s >= 0) {
            this.setSeatUuid(s, p.getUUID());
        }
        if (s == 0 && this.kind.drivable()) {
            this.entityData.set(THROTTLE, Float.valueOf(0.0f));
        }
    }

    protected void removePassenger(Entity p) {
        super.removePassenger(p);
        if (this.level().isClientSide) {
            return;
        }
        int s = this.seatOf(p);
        if (s >= 0) {
            this.setSeatUuid(s, null);
        }
        if (s >= 0) {
            this.fireHeld[s] = false;
            this.reloadHeld[s] = false;
        }
        if (s == 0) {
            this.entityData.set(THROTTLE, Float.valueOf(0.0f));
            this.entityData.set(STEER, Float.valueOf(0.0f));
        }
    }

    public boolean enterSeat(ServerPlayer p, int seat) {
        if (p.isPassenger() || seat < 0 || seat >= this.spec.seatCount()) {
            return false;
        }
        if (this.seatUuid(seat) != null) {
            p.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.seat_taken"), true);
            return false;
        }
        this.pendingSeat = seat;
        boolean ok = p.startRiding((Entity)this);
        this.pendingSeat = -1;
        return ok;
    }

    public boolean switchSeat(Entity p) {
        int cur = this.seatOf(p);
        if (cur < 0 || this.spec.seatCount() < 2) {
            return false;
        }
        int other = 1 - cur;
        if (this.seatUuid(other) != null) {
            if (p instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer)p;
                sp.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.seat_taken"), true);
            }
            return false;
        }
        this.setSeatUuid(cur, null);
        this.setSeatUuid(other, p.getUUID());
        this.fireHeld[cur] = false;
        this.reloadHeld[cur] = false;
        if (cur == 0) {
            this.entityData.set(THROTTLE, Float.valueOf(0.0f));
            this.entityData.set(STEER, Float.valueOf(0.0f));
        }
        return true;
    }

    public Vec3 seatWorld(int seat) {
        float[] s = this.spec.seats()[seat];
        return this.localToWorld(s[0], s[1], s[2]);
    }

    public Vec3 localToWorld(double lx, double ly, double lz) {
        if (this.bodyPitch != 0.0f || this.bodyRoll != 0.0f || this.bodyDy != 0.0f) {
            org.joml.Vector3d v = this.bodyFrame().transformPosition(new org.joml.Vector3d(lx, ly, lz));
            return new Vec3(v.x, v.y, v.z);
        }
        double yaw = Math.toRadians(this.getYRot());
        double c = Math.cos(yaw);
        double s = Math.sin(yaw);
        return new Vec3(this.getX() + lx * c - lz * s, this.getY() + ly, this.getZ() + lx * s + lz * c);
    }

    public Vec3 localDirToWorld(double lx, double ly, double lz) {
        double yaw = Math.toRadians(this.getYRot());
        double c = Math.cos(yaw);
        double s = Math.sin(yaw);
        return new Vec3(lx * c - lz * s, ly, lx * s + lz * c);
    }

    protected void positionRider(Entity p, Entity.MoveFunction f) {
        if (!this.hasPassenger(p)) {
            return;
        }
        int seat = this.seatOf(p);
        if (seat < 0) {
            seat = Math.min(this.getPassengers().indexOf(p), this.spec.seatCount() - 1);
        }
        Vec3 w = this.seatWorld(Math.max(seat, 0));
        f.accept(p, w.x, w.y - (double)0.85f, w.z);
    }

    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        double[][] cand;
        int seat = Math.max(0, this.seatOf((Entity)p));
        float[] s = this.spec.seats()[seat];
        double side = s[0] >= 0.0f ? 1.0 : -1.0;
        double half = (double)(this.spec.aabbW() / 2.0f) + 0.9;
        for (double[] c : cand = new double[][]{{side * half, s[2]}, {-side * half, s[2]}, {side * half, (double)s[2] - 2.5}, {0.0, (double)(-this.spec.length() / 2.0f) - 1.5}}) {
            Vec3 w = this.localToWorld(c[0], 0.0, c[1]);
            for (int dy = 0; dy <= 2; ++dy) {
                AABB box = p.getBoundingBox().move(w.x - p.getX(), this.getY() + (double)dy - p.getY(), w.z - p.getZ());
                if (!this.level().noCollision((Entity)p, box)) continue;
                return new Vec3(w.x, this.getY() + (double)dy, w.z);
            }
        }
        return this.localToWorld(0.0, (double)this.spec.height() + 0.5, 0.0);
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.dmg.dead()) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)player;
            if (this.onItemUse(sp, sp.getItemInHand(hand))) {
                return InteractionResult.CONSUME;
            }
            for (int i = 0; i < this.spec.seatCount(); ++i) {
                if (this.seatUuid(i) != null || !this.enterSeat(sp, i)) continue;
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.CONSUME;
    }

    public void interactPart(ServerPlayer p, int part, boolean aimedAtRear) {
        String k;
        if (p.distanceToSqr((Entity)this) > 324.0 || p.isPassenger()) {
            return;
        }
        String string = k = part >= 0 && part < this.spec.partKinds().length ? this.spec.partKinds()[part] : "body";
        if (this.onItemUse(p, p.getMainHandItem())) {
            return;
        }
        switch (k) {
            case "door_driver": {
                this.enterSeat(p, 0);
                break;
            }
            case "door_gunner": {
                this.enterSeat(p, this.spec.seatCount() > 1 ? 1 : 0);
                break;
            }
            case "rear": 
            case "launcher": 
            case "barrels": {
                this.onRearClick(p);
                break;
            }
            default: {
                for (int i = 0; i < this.spec.seatCount(); ++i) {
                    if (this.seatUuid(i) != null || !this.enterSeat(p, i)) continue;
                    return;
                }
            }
        }
    }

    protected boolean onItemUse(ServerPlayer p, ItemStack st) {
        if (this.kind.drivable() && (st.is(Items.COAL) || st.is(Items.CHARCOAL)) && this.fuel() < 0.97f) {
            this.entityData.set(FUEL, Float.valueOf(Math.min(1.0f, this.fuel() + 0.08f)));
            if (!p.getAbilities().instabuild) {
                st.shrink(1);
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 0.6f, 1.6f);
            return true;
        }
        return false;
    }

    protected void onRearClick(ServerPlayer p) {
    }

    public void handleControl(ServerPlayer p, float throttle, float steer, boolean fire, boolean reload, int actions) {
        int seat = this.seatOf((Entity)p);
        if (seat < 0) {
            return;
        }
        this.fireHeld[seat] = fire;
        this.reloadHeld[seat] = reload;
        this.inputTick[seat] = this.tickCount;
        if (seat == 0 && this.kind.drivable()) {
            this.entityData.set(THROTTLE, Float.valueOf(Mth.clamp((float)throttle, (float)-1.0f, (float)1.0f)));
            this.entityData.set(STEER, Float.valueOf(Mth.clamp((float)steer, (float)-1.0f, (float)1.0f)));
        }
        if ((actions & 1) != 0 && seat == 0 && this.kind.drivable()) {
            this.toggleEngine();
        }
        if ((actions & 2) != 0) {
            this.onLock(p, seat);
        }
        if ((actions & 4) != 0) {
            this.switchSeat((Entity)p);
        }
        if ((actions & 8) != 0 && this instanceof GunVehicle gv) {
            gv.fireSmoke(p);                   // r20: smoke grenade salvo
        }
    }

    protected void toggleEngine() {
        boolean on;
        boolean bl = on = !this.engineOn();
        if (on && this.fuel() <= 0.001f) {
            return;
        }
        this.entityData.set(ENGINE, on);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.ENGINE_START.get(), SoundSource.NEUTRAL, on ? 2.2f : 1.0f, on ? 1.0f : 0.6f);
    }

    protected void onLock(ServerPlayer p, int seat) {
    }

    public boolean fireHeld(int seat) {
        return seat >= 0 && seat < 2 && this.fireHeld[seat] && this.tickCount - this.inputTick[seat] <= 4;
    }

    public boolean reloadHeld(int seat) {
        return seat >= 0 && seat < 2 && this.reloadHeld[seat] && this.tickCount - this.inputTick[seat] <= 4;
    }

    // A measured software-GL client delivered input in 2-second bursts. Keep the
    // operation, not the input permission, across a bounded render/GC stall.
    public static final int RELOAD_INPUT_GRACE_TICKS = 100;
    /** A packet gap pauses an in-progress reload; it never grants input. */
    public boolean reloadInputInGrace(int seat) {
        return seat >= 0 && seat < 2 && this.reloadHeld[seat]
                && controlInputAge(seat) > 4 && controlInputAge(seat) <= RELOAD_INPUT_GRACE_TICKS;
    }

    public int controlInputAge(int seat) {
        return seat >= 0 && seat < 2 ? this.tickCount - this.inputTick[seat] : Integer.MAX_VALUE;
    }

    public boolean isPickable() {
        return !this.isRemoved();
    }

    public boolean canBeCollidedWith() {
        // r20: the Ratte is a walk-in vehicle - its side door lies on the face of the 14 x 14 x 11 m entity box, so the
        // vanilla box shut the door from outside. Its walls, floors and ceilings come from the deck shape (Decks);
        // other vehicles still collide with it through canCollideWith below.
        return !this.walkIn();
    }

    /** r20: vehicles whose interior is entered on foot (deck shape only, no solid vanilla box). */
    public boolean walkIn() {
        return this.spec == Specs.RATTE;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        if (other instanceof VehicleEntity ve && ve.walkIn()) return !this.isPassengerOfSameVehicle(other);
        return super.canCollideWith(other);
    }

    public boolean isPushable() {
        return false;
    }

    public boolean isAttackable() {
        return true;
    }

    public boolean hurt(DamageSource src, float amount) {
        float eff;
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        Entity att = src.getEntity();
        if (att != null && att.getVehicle() == this) {
            return false;
        }
        if (att instanceof Player) {
            Player pl = (Player)att;
            if (pl.getAbilities().instabuild) {
                this.spawnAtLocation(new ItemStack((ItemLike)ModItems.vehicleItem(this.kind).get()));
                this.ejectPassengers();
                this.discard();
                return true;
            }
        }
        if (!((Boolean)RWConfig.COMMON.vehicleDamage.get()).booleanValue() || src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        if (this.dmg.dead() && this.hp() <= 0.0f) {
            return true;                                    // already a wreck
        }
        // r21: a vehicle knocked out by crew losses (DEAD, hull HP left) still takes hull damage and can be blown apart
        float armourMul = this.dmg.dead() ? 1.0f : ru.aiforge.realwar.damage.VehicleDamage.onHurt(this, src, amount);
        if (this.dmg.dead() && this.hp() <= 0.0f) {
            ru.aiforge.realwar.damage.VehicleDamage.flushCam(this);
            return true;
        }
        float f = eff = att instanceof Player ? amount * 0.05f : amount * 0.6f;
        if (src.is(DamageTypeTags.IS_EXPLOSION) || src.getMsgId().contains("blast") || src.getMsgId().contains("ordnance")) {
            eff = amount;
        }
        eff *= armourMul;
        float h = this.hp() - eff;
        this.entityData.set(HP, Float.valueOf(Math.max(0.0f, h)));
        ru.aiforge.realwar.damage.VehicleDamage.flushCam(this);
        if (h <= 0.0f) {
            if (ru.aiforge.realwar.damage.VehicleDamage.handlesDeath(this) && this.level() instanceof ServerLevel dsl) {
                this.dmg.setBroken(ru.aiforge.realwar.damage.DamageState.DEAD, false);   // knocked out -> now a burning wreck
                ru.aiforge.realwar.damage.VehicleDamage.kill(dsl, this, true);
            } else {
                this.destroy();
            }
        } else {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f, 1.8f);
        }
        return true;
    }

    /** r21: hull damage of a near-miss blast (no DamageSource: the blast itself was already resolved). */
    public void blastHull(float eff) {
        if (this.level().isClientSide || this.isRemoved() || this.dmg.dead() && this.hp() <= 0f || eff <= 0f) return;
        if (!((Boolean)RWConfig.COMMON.vehicleDamage.get()).booleanValue()) return;
        float h = this.hp() - eff;
        this.entityData.set(HP, Float.valueOf(Math.max(0.0f, h)));
        if (h <= 0.0f) {
            if (ru.aiforge.realwar.damage.VehicleDamage.handlesDeath(this) && this.level() instanceof ServerLevel dsl) {
                this.dmg.setBroken(ru.aiforge.realwar.damage.DamageState.DEAD, false);
                ru.aiforge.realwar.damage.VehicleDamage.kill(dsl, this, true);
            } else {
                this.destroy();
            }
        }
    }

    /** r21: cached ground type under the vehicle (Terrain.*), -1 = unknown. */
    protected int groundCache = -1;

    protected double cookOffMassKg() {
        return 25.0;
    }

    protected void destroy() {
        int i;
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)level;
        Vec3 c = this.position().add(0.0, (double)this.spec.height() * 0.4, 0.0);
        double mass = 25.0 + this.cookOffMassKg();
        this.ejectPassengers();
        this.discard();
        ConventionalBlast.explode(sl, c, mass, 1.0);
        for (i = 0; i < 6 + sl.random.nextInt(5); ++i) {
            ItemEntityDrop.drop(sl, c, new ItemStack((ItemLike)ModItems.STEEL_PLATE.get()));
        }
        for (i = 0; i < 2; ++i) {
            ItemEntityDrop.drop(sl, c, new ItemStack((ItemLike)ModItems.TRUCK_WHEEL.get()));
        }
    }

    // ------------------------------------------------------------------ r11: terrain-following body pose
    /** Body pitch (deg, + nose up), roll (deg, + right side down) and visual height offset (m) over the terrain. */
    public float bodyPitch, bodyPitchO, bodyRoll, bodyRollO, bodyDy, bodyDyO;
    private float[] contact;
    private boolean wasGrounded;

    /** Contact patch of the running gear in model space {xMin, xMax, zRear, zFront} (tracks / wheels). */
    public float[] contactPatch() {
        if (this.contact != null) return this.contact;
        float[] c = null;
        float[][] w = this.spec.wheels();
        if (w.length >= 2) {
            float x0 = 1e9f, x1 = -1e9f, z0 = 1e9f, z1 = -1e9f;
            for (float[] q : w) { x0 = Math.min(x0, q[0]); x1 = Math.max(x1, q[0]); z0 = Math.min(z0, q[2]); z1 = Math.max(z1, q[2]); }
            if (z1 - z0 > 0.8f) c = new float[]{x0, x1, z0, z1};
        }
        if (c == null) {
            // tracked hulls: the lowest boxes of the walkable shape are the track runs
            ru.aiforge.realwar.deck.DeckShapes.Deck d = ru.aiforge.realwar.deck.DeckShapes.get(this.spec.model().getPath());
            if (d != null) {
                float x0 = 1e9f, x1 = -1e9f, z0 = 1e9f, z1 = -1e9f;
                float[] b = d.b;
                for (int i = 0; i < b.length; i += 7) {
                    if (b[i] != 0 || b[i + 2] > 0.7f) continue;
                    x0 = Math.min(x0, b[i + 1]); x1 = Math.max(x1, b[i + 4]); z0 = Math.min(z0, b[i + 3]); z1 = Math.max(z1, b[i + 6]);
                }
                if (z1 - z0 > 1.0f) c = new float[]{x0 + 0.3f, x1 - 0.3f, z0 + 0.25f, z1 - 0.25f};
            }
        }
        if (c == null) {
            float hl = Math.max(1.0f, this.spec.length() * 0.36f), hw = Math.max(0.6f, this.spec.aabbW() * 0.42f);
            c = new float[]{-hw, hw, -hl, hl};
        }
        return this.contact = c;
    }

    /** Top of the collision under (x, z), searched from ref + up down to ref - down; NaN if nothing. */
    protected double groundTop(double x, double ref, double z, double up, double down) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int x0 = Mth.floor(x), z0 = Mth.floor(z);
        for (int y = Mth.floor(ref + up); y >= Mth.floor(ref - down); y--) {
            mp.set(x0, y, z0);
            net.minecraft.world.level.block.state.BlockState st = this.level().getBlockState(mp);
            if (st.isAir()) continue;
            net.minecraft.world.phys.shapes.VoxelShape sh = st.getCollisionShape(this.level(), mp);
            if (sh.isEmpty()) continue;
            double top = y + sh.max(net.minecraft.core.Direction.Axis.Y);
            if (top <= ref + up + 1e-3) return top;
        }
        return Double.NaN;
    }

    private double groundAt(double lx, double lz, double ref, double up) {
        double yaw = Math.toRadians(this.getYRot());
        double c = Math.cos(yaw), s = Math.sin(yaw);
        double wx = this.getX() + lx * c - lz * s, wz = this.getZ() + lx * s + lz * c;
        return this.groundTop(wx, ref, wz, up, 3.0);
    }

    /**
     * r11: the hull rests on its running gear: front and rear (left and right) support heights are sampled under the
     * contact patch, the body pitches / rolls between them and its centre rides at their mean - so the front wheels
     * or track climb a block first and the rear follows, instead of the whole vehicle popping up the step.
     */
    protected void updateTerrainPose() {
        this.bodyPitchO = this.bodyPitch;
        this.bodyRollO = this.bodyRoll;
        this.bodyDyO = this.bodyDy;
        boolean grounded = this.onGround() || this.isInWater();
        boolean was = this.wasGrounded;
        this.wasGrounded = grounded;
        if (!grounded) return;                                   // airborne: keep the attitude
        float[] cp = this.contactPatch();
        double ref = this.getY();
        double up = Math.max(1.2, this.maxUpStep() + 0.6);
        double zf = cp[3], zr = cp[2], zf2 = zr + (zf - zr) * 0.72, zr2 = zr + (zf - zr) * 0.28;
        double xl = cp[1], xr = cp[0];
        double[] h = new double[8];
        double[][] pts = {{xl, zf}, {xr, zf}, {xl, zf2}, {xr, zf2}, {xl, zr}, {xr, zr}, {xl, zr2}, {xr, zr2}};
        for (int i = 0; i < 8; i++) {
            double g = this.groundAt(pts[i][0], pts[i][1], ref, up);
            h[i] = Double.isNaN(g) ? ref - 1.0 : Math.max(g, ref - 1.6);
        }
        double fL = Math.max(h[0], h[2]), fR = Math.max(h[1], h[3]), rL = Math.max(h[4], h[6]), rR = Math.max(h[5], h[7]);
        double front = Math.max(fL, fR), rear = Math.max(rL, rR);
        double left = Math.max(fL, rL), right = Math.max(fR, rR);
        double len = Math.max(0.5, zf - zr), wid = Math.max(0.5, xl - xr);
        float tp = (float) Mth.clamp(Math.toDegrees(Math.atan2(front - rear, len)), -24.0, 24.0);
        float tr = (float) Mth.clamp(Math.toDegrees(Math.atan2(left - right, wid)), -16.0, 16.0);
        double centre = rear + (front - rear) * ((0.0 - zr) / len);
        double side = right + (left - right) * ((0.0 - xr) / wid);
        float ty = (float) Mth.clamp(Math.max(centre, side) - ref, -1.6, 2.4);
        if (this.isInWater()) { tp *= 0.3f; tr *= 0.3f; ty = 0f; }
        // suspension: quick but rate-limited response (a block is climbed in ~4-6 ticks)
        this.bodyPitch += Mth.clamp((tp - this.bodyPitch) * 0.45f, -3.2f, 3.2f);
        this.bodyRoll += Mth.clamp((tr - this.bodyRoll) * 0.45f, -2.4f, 2.4f);
        float dy = ty - this.bodyDy;
        // the physical step-up moves the entity by a whole block in one tick: shift the visual offset with it
        double jump = this.getY() - this.yo;
        if (was && Math.abs(jump) > 0.05 && Math.abs(jump) < 3.0) this.bodyDy -= (float) jump;
        this.bodyDy += Mth.clamp((ty - this.bodyDy) * 0.45f, -0.35f, 0.35f);
        if (Math.abs(dy) > 4f) this.bodyDy = ty;
    }

    public float pitchLerp(float pt) { return Mth.lerp(pt, this.bodyPitchO, this.bodyPitch); }
    public float rollLerp(float pt) { return Mth.lerp(pt, this.bodyRollO, this.bodyRoll); }
    public float dyLerp(float pt) { return Mth.lerp(pt, this.bodyDyO, this.bodyDy); }

    /** Model -> world frame including the terrain pitch / roll (pivot: model origin on the ground). */
    public org.joml.Matrix4d bodyFrame() {
        return new org.joml.Matrix4d().translate(this.getX(), this.getY() + this.bodyDy, this.getZ())
                .rotateY(-Math.toRadians(this.getYRot())).rotateX(-Math.toRadians(this.bodyPitch)).rotateZ(Math.toRadians(this.bodyRoll));
    }

    /**
     * Turret angles in the tilted hull frame that keep a stabilised gun on the given level-frame bearing az (deg, +
     * left of the bow) / elevation el (deg): the renderer turns the turret / barrel by these so the drawn barrel
     * matches the true line of fire on a slope.
     */
    public float[] stabilised(float az, float el, float pitchDeg, float rollDeg) {
        if (Math.abs(pitchDeg) < 0.05f && Math.abs(rollDeg) < 0.05f) return new float[]{az, el};
        org.joml.Vector3d d = new org.joml.Vector3d(0, 0, 1).rotateX(-Math.toRadians(el)).rotateY(Math.toRadians(az));
        d.rotateX(Math.toRadians(pitchDeg)).rotateZ(-Math.toRadians(rollDeg));   // d_hull = Rz(-roll) Rx(pitch) d_level
        float a = (float) Math.toDegrees(Math.atan2(d.x, d.z));
        float e = (float) Math.toDegrees(Math.asin(Mth.clamp(d.y, -1.0, 1.0)));
        return new float[]{az + Mth.wrapDegrees(a - az), e};
    }

    public void tick() {
        boolean authority;
        super.tick();
        this.rollDistO = this.rollDist;
        this.steerVisO = this.steerVis;
        this.rpmO = this.rpm;
        Vec3 before = this.position();
        float yawBefore = this.getYRot();
        if (this.level().isClientSide) {
            this.tickLerp();
        }
        boolean local = this.level().isClientSide && this.isLocalDriver();
        boolean bl = authority = this.kind.drivable() && (local || !this.level().isClientSide && this.getControllingPassenger() == null);
        if (authority) {
            float[] fArray;
            if (local) {
                fArray = clientInput.apply(this);
            } else if (this.forced != null) {
                fArray = this.forced;
            } else {
                float[] fArray2 = new float[3];
                fArray2[0] = 0.0f;
                fArray2[1] = 0.0f;
                fArray = fArray2;
                fArray2[2] = 1.0f;
            }
            float[] in = fArray;
            this.physics(in[0], in[1], in[2] > 0.5f);
            if (local) {
                statePublisher.accept(this);
            }
        } else if (!(this.level().isClientSide || this.kind.drivable() || this.isNoGravity())) {
            this.gravityOnly();
        }
        double dist = this.position().subtract(before).dot(this.forward());
        if (this.kind.drivable() && !authority) {
            this.speedMs = dist / 0.05;
            this.steerVis += (this.steerCmdDeg() - this.steerVis) * 0.4f;
        }
        this.rollDist += dist;
        float dyaw = Mth.wrapDegrees((float)(this.getYRot() - yawBefore));
        if (dyaw != 0.0f) {
            this.turnPassengers(dyaw);
        }
        this.updateGear();
        this.updateTerrainPose();
        if (this.level().isClientSide) {
            clientHook.accept(this);
            net.minecraft.nbt.CompoundTag dt = this.entityData.get(DMG);
            if (dt != this.dmgSeen) {
                this.dmgSeen = dt;
                this.dmg.readSync(dt);
            }
            ru.aiforge.realwar.damage.DamageFx.vehicle(this);
        } else {
            this.serverTick();
            ru.aiforge.realwar.damage.VehicleDamage.tick(this);
        }
    }

    protected void serverTick() {
        boolean occupied;
        boolean bl = occupied = !this.getPassengers().isEmpty();
        if (this.engineOn()) {
            if (this.fuel() <= 0.0f) {
                this.entityData.set(ENGINE, false);
            } else if (this.tickCount % 20 == 0) {
                float use = 8.0E-5f + 4.0E-4f * Math.abs(this.throttle());
                this.entityData.set(FUEL, Float.valueOf(Math.max(0.0f, this.fuel() - use)));
            }
        }
        this.stationaryTicks = Math.abs(this.speedMs) < 0.25 && Math.abs(this.throttle()) < 0.05f ? ++this.stationaryTicks : 0;
        if (this.getY() < (double)(this.level().getMinBuildHeight() - 40)) {
            this.discard();
        }
        if (!occupied && this.engineOn() && this.tickCount % 200 == 0) {
            this.entityData.set(ENGINE, false);
        }
        if (this.kind.drivable() && Math.abs(this.speedMs) > 4.0 && this.tickCount % 2 == 0) {
            this.runOver();
        }
    }

    private void runOver() {
        Vec3 f = this.forward().scale(Math.signum(this.speedMs));
        AABB box = this.getBoundingBox().move(f.scale(1.2)).inflate(0.1);
        for (Entity e : this.level().getEntities((Entity)this, box, x -> x instanceof LivingEntity && !this.hasPassenger((Entity)x) && x.getVehicle() != this)) {
            float dmg = (float)Math.min(40.0, Math.abs(this.speedMs) * 1.3);
            e.hurt(this.level().damageSources().generic(), dmg);
            e.setDeltaMovement(e.getDeltaMovement().add(f.x * 0.8, 0.35, f.z * 0.8));
        }
    }

    public Vec3 forward() {
        double y = Math.toRadians(this.getYRot());
        return new Vec3(-Math.sin(y), 0.0, Math.cos(y));
    }

    protected void turnPassengers(float dyaw) {
        for (Entity p : this.getPassengers()) {
            p.setYRot(p.getYRot() + dyaw);
            p.setYHeadRot(p.getYHeadRot() + dyaw);
            if (!(p instanceof LivingEntity)) continue;
            LivingEntity le = (LivingEntity)p;
            le.yBodyRot += dyaw;
        }
    }

    public boolean canDrive() {
        return true;
    }

    protected void gravityOnly() {
        this.vy = this.onGround() ? -0.05 : (this.vy - 0.08) * 0.98;
        this.move(MoverType.SELF, new Vec3(0.0, this.vy, 0.0));
        if (this.verticalCollision) {
            this.vy = 0.0;
        }
    }

    private float steerCmdDeg() {
        return this.steerCmd() * this.maxSteerDeg(Math.abs(this.speedMs));
    }

    protected float maxSteerDeg(double v) {
        return (float)(4.0 + 28.0 / (1.0 + v / 7.0 * (v / 7.0)));
    }

    public void physics(float throttleIn, float steerIn, boolean handbrake) {
        float target;
        double dt = 0.05;
        double mass = this.kind.massKg;
        int ground = this.tickCount % 10 == 0 || this.groundCache < 0 ? (this.groundCache = Terrain.under(this)) : this.groundCache;   // r21 mud / snow
        double vmax = (double)this.kind.vmaxKmh / 3.6 * Terrain.vmaxMul(ground, false);
        boolean run = this.engineOn() && this.fuel() > 0.0f && this.canDrive() && ru.aiforge.realwar.damage.VehicleDamage.canDrive(this);
        float thr = run ? throttleIn : 0.0f;
        double v = this.speedMs;
        double F = 0.0;
        double power = (double)this.kind.powerKw * 1000.0 * 0.82 * ru.aiforge.realwar.damage.VehicleDamage.powerFactor(this);
        double fmax = mass * (double)this.kind.maxAccel;
        if (thr > 0.01f) {
            F = v >= -0.3 ? Math.min(fmax, power / Math.max(Math.abs(v), 1.5)) * (double)thr : mass * 5.0 * (double)thr;
        } else if (thr < -0.01f) {
            F = v > 0.5 ? -mass * 5.0 * (double)(-thr) : -Math.min(fmax * 0.6, power / Math.max(Math.abs(v), 2.0)) * (double)(-thr);
        }
        double roll = 0.018 * mass * 9.81 * Terrain.rollMul(ground, false);
        double aero = 3.7800000000000002 * v * v;
        double resist = roll + aero + (thr == 0.0f ? 0.03 * mass * 9.81 * Math.min(1.0, Math.abs(v)) : 0.0);
        if (handbrake || !run && thr == 0.0f) {
            resist += mass * 4.5 * Math.min(1.0, Math.abs(v));
        }
        if (Math.abs(v) < 0.05 && Math.abs(F) < resist) {
            v = 0.0;
            double a = 0.0;
        } else {
            double a = (F - Math.signum(v == 0.0 ? F : v) * resist) / mass;
            double nv = v + a * 0.05;
            if (v != 0.0 && Math.signum(nv) != Math.signum(v) && Math.abs(F) < resist + 1.0E-6) {
                nv = 0.0;
            }
            v = nv;
        }
        v = Mth.clamp((double)v, (double)-5.5, (double)vmax);
        if (this.isInWater()) {
            v *= 0.93;
        }
        float rate = Math.abs(target = steerIn * this.maxSteerDeg(Math.abs(v))) > Math.abs(this.steerVis) ? 5.0f : 8.0f;
        this.steerVis += Mth.clamp((float)(target - this.steerVis), (float)(-rate), (float)rate);
        double yawRate = v / (double)this.spec.wheelbase() * Math.tan(Math.toRadians(this.steerVis));
        this.setYRot(this.getYRot() - (float)Math.toDegrees(yawRate * 0.05));
        Vec3 fwd = this.forward();
        this.vy = this.onGround() ? -0.05 : (this.vy - 0.08) * 0.98;
        Vec3 mv = new Vec3(fwd.x * v * 0.05, this.vy, fwd.z * v * 0.05);
        Vec3 before = this.position();
        this.move(MoverType.SELF, mv);
        if (this.verticalCollision) {
            this.vy = 0.0;
        }
        double got = this.position().subtract(before).horizontalDistance();
        double want = Math.abs(v * 0.05);
        if (want > 0.02 && got < want * 0.4) {
            if (Math.abs(v) > 6.0 && !this.level().isClientSide) {
                this.hurtByCrash(Math.abs(v));
            }
            v *= 0.25;
        }
        this.speedMs = v;
        float heat = run ? 0.45f + 0.35f * Math.abs(thr) : 0.15f;
        this.temp += (heat - this.temp) * 0.003f;
    }

    protected void hurtByCrash(double v) {
        this.hurt(this.level().damageSources().generic(), (float)(v * 0.4));
    }

    private void updateGear() {
        double kmh = Math.abs(this.speedMs) * 3.6;
        double vmax = Math.max((double)this.kind.vmaxKmh, 1.0);
        if (this.speedMs < -0.3) {
            this.gear = 0;
        } else {
            if (this.gear == 0) {
                this.gear = 1;
            }
            while (this.gear < 5 && kmh > (double)GEAR_TOP[this.gear - 1] * vmax * 0.92) {
                ++this.gear;
            }
            while (this.gear > 1 && kmh < (double)GEAR_TOP[this.gear - 2] * vmax * 0.55) {
                --this.gear;
            }
        }
        double top = (double)GEAR_TOP[Math.max(this.gear, 1) - 1] * vmax;
        float tgt = 0.0f;
        if (this.engineOn()) {
            tgt = (float)(700.0 + 2100.0 * Mth.clamp((double)(kmh / Math.max(top, 1.0)), (double)0.0, (double)1.0) + 300.0 * (double)Math.abs(this.throttle()));
        }
        this.rpm += (tgt - this.rpm) * 0.2f;
    }

    public boolean isControlledByLocalInstance() {
        return false;
    }

    public boolean isLocalDriver() {
        return this.level().isClientSide && this.kind.drivable() && localDriver.test(this);
    }

    public void acceptClientState(ServerPlayer p, double x, double y, double z, float yaw, float speed, float steer) {
        double maxStep;
        double dz;
        double dy;
        if (!this.kind.drivable() || this.getControllingPassenger() != p) {
            return;
        }
        if (!Double.isFinite(x + y + z + (double)yaw + (double)speed) || y < (double)(this.level().getMinBuildHeight() - 32) || y > (double)(this.level().getMaxBuildHeight() + 64)) {
            return;
        }
        double dx = x - this.getX();
        double d2 = dx * dx + (dy = y - this.getY()) * dy + (dz = z - this.getZ()) * dz;
        if (d2 > (maxStep = 2.0 + (double)this.kind.vmaxKmh / 3.6 * 0.05 * 6.0) * maxStep || !this.level().hasChunkAt(BlockPos.containing((double)x, (double)y, (double)z))) {
            if (++this.rejected > 3) {
                this.rejected = 0;
                ModNetwork.sendToPlayer(p, new VehicleSnapPacket(this.getId(), this.getX(), this.getY(), this.getZ(), this.getYRot()));
            }
            return;
        }
        this.rejected = 0;
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.setXRot(0.0f);
        this.speedMs = speed;
        this.steerVis = steer;
    }

    public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
        if (this.isLocalDriver()) {
            return;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yr;
        this.lerpPitch = xr;
        this.lerpSteps = 4;
    }

    private void tickLerp() {
        if (this.isLocalDriver()) {
            this.lerpSteps = 0;
            return;
        }
        if (this.lerpSteps > 0) {
            double nx = this.getX() + (this.lerpX - this.getX()) / (double)this.lerpSteps;
            double ny = this.getY() + (this.lerpY - this.getY()) / (double)this.lerpSteps;
            double nz = this.getZ() + (this.lerpZ - this.getZ()) / (double)this.lerpSteps;
            double dyaw = Mth.wrapDegrees((double)(this.lerpYaw - (double)this.getYRot()));
            this.setYRot(this.getYRot() + (float)dyaw / (float)this.lerpSteps);
            --this.lerpSteps;
            this.setPos(nx, ny, nz);
            this.setRot(this.getYRot(), this.getXRot());
        }
    }

    public void applyCommonPose(ru.aiforge.realwar.glb.Pose pose, float pt) {
        float roll = (float)Mth.lerp((double)pt, (double)this.rollDistO, (double)this.rollDist);
        float steer = Mth.lerp((float)pt, (float)this.steerVisO, (float)this.steerVis);
        for (int i = 0; i < this.spec.wheels().length; ++i) {
            float[] w = this.spec.wheels()[i];
            String n = "wheel_" + i;
            if (w[4] > 0.5f) {
                pose.rotate(n, (float)Math.toRadians(steer), 0.0f, 1.0f, 0.0f);
            }
            pose.rotate(n, -roll / w[3], 1.0f, 0.0f, 0.0f);
        }
        if (this.kind.drivable()) {
            float lock = steer / this.maxSteerDeg(0.0);
            pose.rotate("steering_wheel", lock * (float)Math.toRadians(540.0), 0.0f, 0.0f, 1.0f);
            float kmh = (float)Math.abs(this.speedMs) * 3.6f;
            VehicleEntity.needle(pose, "needle_speed", kmh / 100.0f);
            VehicleEntity.needle(pose, "needle_rpm", Mth.lerp((float)pt, (float)this.rpmO, (float)this.rpm) / 3200.0f);
            VehicleEntity.needle(pose, "needle_fuel", this.fuel());
            VehicleEntity.needle(pose, "needle_temp", this.temp);
        }
    }

    private static void needle(ru.aiforge.realwar.glb.Pose pose, String node, float f) {
        pose.rotate(node, (float)Math.toRadians(-135.0 + 270.0 * (double)Mth.clamp((float)f, (float)0.0f, (float)1.0f)), 0.0f, 0.0f, 1.0f);
    }

    public void applyPose(ru.aiforge.realwar.glb.Pose pose, float pt) {
        this.applyCommonPose(pose, pt);
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(HP, Float.valueOf(tag.contains("hp") ? tag.getFloat("hp") : (float)this.kind.hp));
        this.entityData.set(ENGINE, false);
        this.entityData.set(FUEL, Float.valueOf(tag.contains("fuel") ? tag.getFloat("fuel") : 0.85f));
        this.dmg.load(tag);
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("hp", this.hp());
        tag.putFloat("fuel", this.fuel());
        this.dmg.save(tag);
    }

    public ItemStack getPickResult() {
        return new ItemStack((ItemLike)ModItems.vehicleItem(this.kind).get());
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket((Entity)this);
    }

    public void setForcedInput(float throttle, float steer, boolean handbrake) {
        this.forced = new float[]{throttle, steer, handbrake ? 1.0f : 0.0f};
    }

    public void setEngine(boolean on) {
        this.entityData.set(ENGINE, on);
    }

    public void setFuel(float f) {
        this.entityData.set(FUEL, Float.valueOf(f));
    }

    public BlockPos blockPosition0() {
        return this.blockPosition();
    }

    static final class ItemEntityDrop {
        ItemEntityDrop() {
        }

        static void drop(ServerLevel l, Vec3 c, ItemStack st) {
            ItemEntity ie = new ItemEntity((Level)l, c.x, c.y, c.z, st, (l.random.nextDouble() - 0.5) * 0.6, 0.3 + l.random.nextDouble() * 0.4, (l.random.nextDouble() - 0.5) * 0.6);
            l.addFreshEntity((Entity)ie);
        }
    }
}

