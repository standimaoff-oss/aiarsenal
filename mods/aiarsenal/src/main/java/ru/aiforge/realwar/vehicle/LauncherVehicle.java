/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.syncher.EntityDataAccessor
 *  net.minecraft.network.syncher.EntityDataSerializer
 *  net.minecraft.network.syncher.EntityDataSerializers
 *  net.minecraft.network.syncher.SynchedEntityData
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.Vec3
 *  org.jetbrains.annotations.Nullable
 *  org.joml.Matrix4d
 *  org.joml.Vector3d
 */
package ru.aiforge.realwar.vehicle;

import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.registry.ModSounds;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3d;

public abstract class LauncherVehicle
extends VehicleEntity {
    protected static final EntityDataAccessor<Integer> LOADED_LO = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> LOADED_HI = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> AZ = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> EL = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> JACK = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Integer> RELOAD_TUBE = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> RELOAD_T = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> FIRED_SEQ = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> FIRED_TUBE = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Boolean> AUTO = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Optional<BlockPos>> TARGET = SynchedEntityData.defineId(LauncherVehicle.class, (EntityDataSerializer)EntityDataSerializers.OPTIONAL_BLOCK_POS);
    public static final int JACK_TICKS = 30;
    public float azVis;
    public float azVisO;
    public float elVis;
    public float elVisO;
    public float kick;
    protected int fireCd;
    protected UUID extReloader;
    protected int extUntil;
    protected java.lang.ref.WeakReference<ServerPlayer> extRef = new java.lang.ref.WeakReference<>(null);
    protected int lastMsgTick;
    public int seenSeq = -1;
    private int pendingFx = -1;

    protected LauncherVehicle(EntityType<?> type, Level level, VehicleKind kind) {
        super(type, level, kind);
        this.entityData.set(EL, Float.valueOf(kind.elMin));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(LOADED_LO, 0);
        this.entityData.define(LOADED_HI, 0);
        this.entityData.define(AZ, Float.valueOf(0.0f));
        this.entityData.define(EL, Float.valueOf(0.0f));
        this.entityData.define(JACK, Float.valueOf(0.0f));
        this.entityData.define(RELOAD_TUBE, -1);
        this.entityData.define(RELOAD_T, 0);
        this.entityData.define(FIRED_SEQ, 0);
        this.entityData.define(FIRED_TUBE, 0);
        this.entityData.define(AUTO, false);
        this.entityData.define(TARGET, Optional.empty());
    }

    @Nullable
    public Vec3 mapTarget() {
        return this.entityData.get(TARGET).map(b -> new Vec3((double)b.getX() + 0.5, (double)b.getY(), (double)b.getZ() + 0.5)).orElse(null);
    }

    public void setMapTarget(ServerPlayer p, @Nullable BlockPos pos) {
        if (pos == null) {
            this.entityData.set(TARGET, Optional.empty());
            this.entityData.set(AUTO, false);
            return;
        }
        int y = pos.getY();
        if (!this.usesAltitude() && this.level().hasChunkAt(pos)) {
            y = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        }
        this.entityData.set(TARGET, Optional.of(new BlockPos(pos.getX(), y, pos.getZ())));
        this.onTargetAssigned(p);
    }

    public boolean usesAltitude() {
        return false;
    }

    protected void onTargetAssigned(ServerPlayer p) {
        this.entityData.set(AUTO, true);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.LOCK_DONE.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
    }

    public long loadedMask() {
        return (long)((Integer)this.entityData.get(LOADED_LO)).intValue() & 0xFFFFFFFFL | (long)((Integer)this.entityData.get(LOADED_HI)).intValue() << 32;
    }

    public void setLoadedMask(long m) {
        this.entityData.set(LOADED_LO, ((int)m));
        this.entityData.set(LOADED_HI, ((int)(m >>> 32)));
    }

    public boolean isLoaded(int k) {
        return (this.loadedMask() >>> k & 1L) != 0L;
    }

    public int loadedCount() {
        return Long.bitCount(this.loadedMask());
    }

    public int firstLoaded() {
        long m = this.loadedMask();
        return m == 0L ? -1 : Long.numberOfTrailingZeros(m);
    }

    public int firstEmpty() {
        for (int k = 0; k < this.kind.magazine; ++k) {
            if (this.isLoaded(k)) continue;
            return k;
        }
        return -1;
    }

    public void fillMagazine() {
        this.setLoadedMask(this.kind.magazine >= 64 ? -1L : (1L << this.kind.magazine) - 1L);
    }

    public int reloadTube() {
        return (Integer)this.entityData.get(RELOAD_TUBE);
    }

    public int reloadTicks() {
        return (Integer)this.entityData.get(RELOAD_T);
    }

    public float az() {
        return ((Float)this.entityData.get(AZ)).floatValue();
    }

    public float el() {
        return ((Float)this.entityData.get(EL)).floatValue();
    }

    public float jack() {
        return ((Float)this.entityData.get(JACK)).floatValue();
    }

    public int firedSeq() {
        return (Integer)this.entityData.get(FIRED_SEQ);
    }

    public int firedTube() {
        return (Integer)this.entityData.get(FIRED_TUBE);
    }

    public boolean autoAim() {
        return (Boolean)this.entityData.get(AUTO);
    }

    protected abstract Item ammoItem();

    protected boolean isAmmo(ItemStack st) {
        return st.is(this.ammoItem());
    }

    @Override
    protected double cookOffMassKg() {
        return 10.0 + (double)((float)this.loadedCount() * this.kind.warheadKg) * 0.45;
    }

    public Matrix4d pitchFrame(double az, double el) {
        // r11: the turret ring rides on the tilted hull (bodyFrame), the gun itself is stabilised in the level frame
        float[] yp = this.spec.yawPivot();
        Vector3d base = this.bodyFrame().transformPosition(yp != null ? new Vector3d(yp[0], yp[1], yp[2]) : new Vector3d());
        Matrix4d m = new Matrix4d().translate(base.x, base.y, base.z).rotateY(-Math.toRadians(this.getYRot()));
        if (yp != null) {
            m.rotateY(Math.toRadians(az));
        }
        float[] pp = this.spec.pitchPivot();
        m.translate((double)pp[0], (double)pp[1], (double)pp[2]).rotateX((double)(-this.kind.fireAxisZ()) * Math.toRadians(el));
        return m;
    }

    /** r20: launcher sight (PG-1M panorama of the BM-21 / 9P140) in the pitch frame: at the top-left corner of the
     *  tube pack near the trunnions (0.3 m outside / 0.4 m above the outermost tubes), so the optic never looks out of a
     *  tube, at the cab roof or through the windscreen pillars. Null for vehicles without a traversing launcher. */
    public double[] launcherSightLocal() {
        float[][] tb = this.spec.tubes();
        if (this.spec.yawPivot() == null || tb.length == 0) return null;
        float mx = 0f, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, zs = 0f;
        for (float[] k : tb) {
            mx = Math.max(mx, Math.abs(k[0]));
            y0 = Math.min(y0, k[1]);
            y1 = Math.max(y1, k[1]);
            zs += k[2];
        }
        double sz = zs != 0f ? Math.signum(zs) : this.kind.fireAxisZ();
        // top-left corner of the pack: clear of the tubes and high enough to look over the cab roof at 0 deg
        return new double[]{mx + 0.3, y1 + 0.4, sz * 0.6};
    }

    public float[] muzzleLocal(int k) {
        float[][] arr = this.spec.tubes().length > 0 ? this.spec.tubes() : this.spec.canisters();
        return arr[Mth.clamp((int)k, (int)0, (int)(arr.length - 1))];
    }

    public Vec3 muzzleWorld(int k) {
        float[] q = this.muzzleLocal(k);
        Vector3d v = this.pitchFrame(this.az(), this.el()).transformPosition(new Vector3d((double)q[0], (double)q[1], (double)q[2]));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 launchDir() {
        return this.launchDir(this.az(), this.el());
    }

    public Vec3 launchDir(double az, double el) {
        Vector3d d = this.pitchFrame(az, el).transformDirection(new Vector3d(0.0, 0.0, (double)this.kind.fireAxisZ()));
        return new Vec3(d.x, d.y, d.z).normalize();
    }

    public static float yawOf(Vec3 d) {
        return (float)Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    @Override
    public boolean canDrive() {
        return this.jack() < 0.02f && this.deployedClear();
    }

    protected boolean deployedClear() {
        return true;
    }

    protected void updateJacks() {
        LivingEntity livingEntity;
        float nj;
        boolean driverWants;
        float j = this.jack();
        boolean wantDown = this.stationaryTicks >= 20;
        boolean bl = driverWants = Math.abs(this.throttle()) > 0.05f;
        if (driverWants || Math.abs(this.speedMs) > 0.6) {
            wantDown = false;
        }
        if (this.spec.jacks().length == 0) {
            wantDown = false;
        }
        if ((nj = Mth.clamp((float)(j + (wantDown ? 1.0f : -1.0f) / 30.0f), (float)0.0f, (float)1.0f)) != j) {
            this.entityData.set(JACK, Float.valueOf(nj));
            if (nj == 1.0f || nj == 0.0f || this.tickCount % 10 == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.HYDRAULIC.get(), SoundSource.NEUTRAL, 0.8f, nj == 1.0f || nj == 0.0f ? 1.3f : 1.0f);
            }
        }
        if (driverWants && (j > 0.02f || !this.deployedClear()) && (livingEntity = this.getControllingPassenger()) instanceof ServerPlayer) {
            ServerPlayer dp = (ServerPlayer)livingEntity;
            if (this.tickCount - this.lastMsgTick > 30) {
                this.lastMsgTick = this.tickCount;
                dp.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.vehicle_locked"), true);
            }
        }
    }

    // ------------------------------------------------------------------ r22 AI crew
    /** World point the AI gunner is laying the pack on. */
    @Nullable
    public Vec3 aiAim;
    public boolean aiTrigger;
    /** Wider salvo pattern for AI crews (imperfect gunnery). */
    public float aiSpread = 2.6f;

    public boolean aiGunner() {
        return this.passengerIn(this.operatorSeat()) instanceof ru.aiforge.realwar.npc.SoldierEntity;
    }

    /** Ballistic lay on {@link #aiAim}; launchers without a rocket profile keep the pack at rest. */
    @Nullable
    protected float[] aiSolution() {
        return null;
    }

    protected abstract float[] aimTarget();

    protected void updateAim() {
        boolean moved;
        float[] t = this.aimTarget();
        if (t == null) {
            return;
        }
        float az = this.az();
        float el = this.el();
        float nAz = this.spec.yawPivot() == null ? 0.0f : az + Mth.clamp((float)Mth.wrapDegrees((float)(t[0] - az)), (float)-1.4f, (float)1.4f);
        float nEl = el + Mth.clamp((float)(t[1] - el), (float)(-this.elevRate()), (float)this.elevRate());
        nAz = Mth.clamp((float)nAz, (float)(-this.kind.traverse), (float)this.kind.traverse);
        nEl = Mth.clamp((float)nEl, (float)this.kind.elMin, (float)this.kind.elMax);
        boolean bl = moved = Math.abs(nAz - az) > 0.02f || Math.abs(nEl - el) > 0.02f;
        if (moved) {
            this.entityData.set(AZ, Float.valueOf(nAz));
            this.entityData.set(EL, Float.valueOf(nEl));
            if (this.tickCount % 14 == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.HYDRAULIC.get(), SoundSource.NEUTRAL, 0.7f, 0.9f);
            }
        }
    }

    protected float elevRate() {
        return 0.8f;
    }

    protected float azRelFromWorldYaw(float worldYaw) {
        return Mth.wrapDegrees((float)(this.getYRot() + (this.kind.fireAxisZ() < 0.0f ? 180.0f : 0.0f) - worldYaw));
    }

    /** r9: every vehicle is run by one person from the driver's seat (WASD drive, mouse aim, LMB fire, RMB sight). */
    public int operatorSeat() {
        return 0;
    }

    protected ServerPlayer operator() {
        ServerPlayer sp;
        Entity e = this.passengerIn(this.operatorSeat());
        return e instanceof ServerPlayer ? (sp = (ServerPlayer)e) : null;
    }

    protected boolean hasAmmo(ServerPlayer p) {
        if (p.getAbilities().instabuild) {
            return true;
        }
        if (this.isAmmo(p.getMainHandItem()) || this.isAmmo(p.getOffhandItem())) {
            return true;
        }
        for (ItemStack st : p.getInventory().items) {
            if (!this.isAmmo(st)) continue;
            return true;
        }
        return false;
    }

    protected boolean takeAmmo(ServerPlayer p) {
        if (p.getAbilities().instabuild) {
            return true;
        }
        if (this.isAmmo(p.getMainHandItem())) {
            p.getMainHandItem().shrink(1);
            return true;
        }
        if (this.isAmmo(p.getOffhandItem())) {
            p.getOffhandItem().shrink(1);
            return true;
        }
        for (ItemStack st : p.getInventory().items) {
            if (!this.isAmmo(st)) continue;
            st.shrink(1);
            return true;
        }
        return false;
    }

    @Override
    protected void onRearClick(ServerPlayer p) {
        if (this.loadedCount() >= this.kind.magazine) {
            return;
        }
        if (!this.hasAmmo(p)) {
            p.displayClientMessage((Component)Component.translatable((String)"hud.aiarsenal.rw.no_ammo"), true);
            return;
        }
        this.extReloader = p.getUUID();
        this.extRef = new java.lang.ref.WeakReference<>(p);
        this.extUntil = this.tickCount + this.kind.reloadTicks + 40;   // covers a whole reload (Yars: 10 s)
    }

    @Override
    protected boolean onItemUse(ServerPlayer p, ItemStack st) {
        if (this.isAmmo(st) && this.loadedCount() < this.kind.magazine) {
            this.extReloader = p.getUUID();
            this.extRef = new java.lang.ref.WeakReference<>(p);
        this.extRef = new java.lang.ref.WeakReference<>(p);
            // the window must cover a whole reload: with the old fixed 40 ticks a Yars (200-tick reload) showed the
            // missile in the TPK for 2 s and then dropped it again - "the missile disappears when I load it"
            this.extUntil = this.tickCount + this.kind.reloadTicks + 40;
            return true;
        }
        return super.onItemUse(p, st);
    }

    protected boolean mayReload() {
        return Math.abs(this.speedMs) < 0.4 && this.fireCd <= 0;
    }

    protected void updateReload() {
        if (this.aiGunner()) {
            this.aiReload();
            return;
        }
        ServerPlayer ep;
        ServerLevel sl;
        Player player;
        Level level;
        int rt = this.reloadTube();
        ServerPlayer rp = null;
        ServerPlayer op = this.operator();
        if (op != null && this.reloadHeld(this.operatorSeat())) {
            rp = op;
        }
        if (rp == null && this.extReloader != null && this.tickCount < this.extUntil && (level = this.level()) instanceof ServerLevel && (player = (sl = (ServerLevel)level).getPlayerByUUID(this.extReloader) != null ? sl.getPlayerByUUID(this.extReloader) : this.extRef.get()) instanceof ServerPlayer && (ep = (ServerPlayer)player).distanceToSqr((Entity)this) < 400.0) {
            rp = ep;
        }
        if (rt < 0) {
            if (rp != null && this.mayReload() && this.loadedCount() < this.kind.magazine && this.hasAmmo(rp)) {
                this.entityData.set(RELOAD_TUBE, this.firstEmpty());
                this.entityData.set(RELOAD_T, 0);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 1.0f, 0.85f);
            }
            return;
        }
        // Client render stalls / packet jitter must not erase a 20-second
        // loading operation every 200 ms. Do NOT advance or consume ammunition
        // during the gap. Explicit key release, dismount or >5s silence cancels.
        if (rp == null && op != null && this.reloadInputInGrace(this.operatorSeat())
                && this.mayReload() && this.hasAmmo(op)) return;
        if (rp == null || !this.mayReload() || !this.hasAmmo(rp)) {
            this.entityData.set(RELOAD_TUBE, -1);
            this.entityData.set(RELOAD_T, 0);
            return;
        }
        if (ru.aiforge.realwar.damage.VehicleDamage.slowReload(this) && this.tickCount % 2 == 0) return;   // r21: loader out
        int t = this.reloadTicks() + 1;
        if (t >= this.kind.reloadTicks) {
            if (this.takeAmmo(rp)) {
                this.setLoadedMask(this.loadedMask() | 1L << rt);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 1.2f, 1.15f);
                if (this.extReloader != null) {
                    this.extUntil = Math.max(this.extUntil, this.tickCount + 20);
                }
            }
            this.entityData.set(RELOAD_TUBE, -1);
            this.entityData.set(RELOAD_T, 0);
        } else {
            this.entityData.set(RELOAD_T, t);
            if (t == this.kind.reloadTicks / 2) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.HYDRAULIC.get(), SoundSource.NEUTRAL, 0.6f, 1.1f);
            }
        }
    }

    // ------------------------------------------------------------------ r22 AI crew
    /** Rounds the AI crew still has in the racks (-1 = not initialised yet). */
    public int aiAmmo = -1;

    @Override
    protected void removePassenger(Entity p) {
        int seat = this.seatOf(p);
        super.removePassenger(p);
        if (seat == 0) {                       // the gunner left: no stale AI lay, no stale trigger
            this.aiAim = null;
            this.aiTrigger = false;
        }
    }

    /**
     * AI loader: reloads from the vehicle's own racks, without a player's inventory. The reserve is finite
     * (two magazines), so an AI crew can be worn down instead of firing forever.
     */
    private void aiReload() {
        if (this.aiAmmo < 0) this.aiAmmo = Math.max(2, this.kind.magazine * 2);
        int rt = this.reloadTube();
        if (rt < 0) {
            if (this.mayReload() && this.loadedCount() < this.kind.magazine && this.aiAmmo > 0) {
                this.entityData.set(RELOAD_TUBE, this.firstEmpty());
                this.entityData.set(RELOAD_T, 0);
            }
            return;
        }
        if (!this.mayReload()) return;
        if (ru.aiforge.realwar.damage.VehicleDamage.slowReload(this) && this.tickCount % 2 == 0) return;
        int t = this.reloadTicks() + 1;
        if (t >= this.kind.reloadTicks) {
            this.setLoadedMask(this.loadedMask() | 1L << rt);
            this.aiAmmo--;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 1.2f, 1.15f);
            this.entityData.set(RELOAD_TUBE, -1);
            this.entityData.set(RELOAD_T, 0);
        } else {
            this.entityData.set(RELOAD_T, t);
        }
    }

    protected boolean aimSettled(float[] t) {
        if (t == null) {
            return true;
        }
        boolean azOk = this.spec.yawPivot() == null || Math.abs(Mth.wrapDegrees((float)(t[0] - this.az()))) < 2.5f || Math.abs(Mth.clamp((float)t[0], (float)(-this.kind.traverse), (float)this.kind.traverse) - this.az()) < 2.5f;
        boolean elOk = Math.abs(Mth.clamp((float)t[1], (float)this.kind.elMin, (float)this.kind.elMax) - this.el()) < 2.0f;
        return azOk && elOk;
    }

    protected void notifyOp(String key) {
        ServerPlayer op = this.operator();
        if (op != null && this.tickCount - this.lastMsgTick > 15) {
            this.lastMsgTick = this.tickCount;
            op.displayClientMessage((Component)Component.translatable((String)key), true);
        }
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        if (this.fireCd > 0) {
            --this.fireCd;
        }
        this.updateJacks();
        // r21: no gunner (killed, replacement not yet in his seat) -> no aiming and no launches
        boolean gunner = ru.aiforge.realwar.damage.Crew.ok(this, ru.aiforge.realwar.damage.Crew.GUNNER);
        if (gunner) this.updateAim();
        this.updateReload();
        if (gunner) this.serverLaunch();
    }

    protected abstract void serverLaunch();

    protected void markFired(int k) {
        this.entityData.set(FIRED_TUBE, k);
        this.entityData.set(FIRED_SEQ, (this.firedSeq() + 1));
        this.setLoadedMask(this.loadedMask() & (1L << k ^ 0xFFFFFFFFFFFFFFFFL));
    }

    /** One synchronized muzzle event for a twin mount: consume both loaded positions but publish one salvo pulse. */
    protected void markFiredPair(int first, int second) {
        this.entityData.set(FIRED_TUBE, -2);
        this.entityData.set(FIRED_SEQ, this.firedSeq() + 1);
        this.setLoadedMask(this.loadedMask() & ~((1L << first) | (1L << second)));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.tickCount <= 1) {
                this.azVis = this.az();
                this.elVis = this.el();
            }
            this.azVisO = this.azVis;
            this.elVisO = this.elVis;
            this.azVis += (this.az() - this.azVis) * 0.55f;
            this.elVis += (this.el() - this.elVis) * 0.55f;
            this.kick *= 0.82f;
            if (this.seenSeq != this.firedSeq()) {
                if (this.seenSeq >= 0) {
                    this.kick = 1.0f;
                    this.pendingFx = this.firedTube();
                }
                this.seenSeq = this.firedSeq();
            }
        }
    }

    public int consumeFx() {
        int r = this.pendingFx;
        this.pendingFx = -1;
        return r;
    }

    @Override
    public void applyPose(Pose pose, float pt) {
        this.applyCommonPose(pose, pt);
        float az = Mth.lerp((float)pt, (float)this.azVisO, (float)this.azVis);
        float el = Mth.lerp((float)pt, (float)this.elVisO, (float)this.elVis);
        if (this.spec.yawPivot() != null && this.kind.fireAxisZ() > 0) {
            // r11: stabilised turret on a tilted hull - draw the barrel along the true line of fire
            float[] st = this.stabilised(az, el, this.pitchLerp(pt), this.rollLerp(pt));
            az = st[0];
            el = st[1];
        }
        float pitchDeg = -this.kind.fireAxisZ() * (el - this.kick * 0.8f);
        if (this.spec.yawPivot() != null) {
            pose.rotate("launcher_yaw", (float)Math.toRadians(az), 0.0f, 1.0f, 0.0f);
        }
        pose.rotate("launcher_pitch", (float)Math.toRadians(pitchDeg), 1.0f, 0.0f, 0.0f);
        float jk = this.jack();
        for (int i = 0; i < this.spec.jacks().length; ++i) {
            pose.translate("jack_" + i, 0.0f, -this.spec.jacks()[i][2] * jk, 0.0f);
        }
        this.launcherPose(pose, pt);
    }

    protected abstract void launcherPose(Pose var1, float var2);

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setLoadedMask(tag.contains("loaded") ? tag.getLong("loaded") : 0L);
        this.aiAmmo = tag.contains("aiAmmo") ? tag.getInt("aiAmmo") : -1;
        this.entityData.set(AZ, Float.valueOf(tag.getFloat("az")));
        this.entityData.set(EL, Float.valueOf(tag.contains("el") ? tag.getFloat("el") : this.kind.elMin));
        this.entityData.set(JACK, Float.valueOf(0.0f));
        this.azVis = this.az();
        this.elVis = this.el();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("loaded", this.loadedMask());
        tag.putInt("aiAmmo", this.aiAmmo);
        tag.putFloat("az", this.az());
        tag.putFloat("el", this.el());
    }
}

