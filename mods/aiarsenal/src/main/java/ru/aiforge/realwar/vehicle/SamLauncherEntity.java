/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
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
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.vehicle;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.ordnance.AirTargets;
import ru.aiforge.realwar.ordnance.SamMissileEntity;
import ru.aiforge.realwar.radar.RadarNet;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModSounds;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleKind;
import java.util.Comparator;
import java.util.List;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SamLauncherEntity
extends LauncherVehicle
implements RadarNet.RadarEmitter {
    protected static final EntityDataAccessor<Integer> LOCK = SynchedEntityData.defineId(SamLauncherEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> LOCK_T = SynchedEntityData.defineId(SamLauncherEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    public static final int LOCK_FULL = 40;
    public static final float ERECT_EL = 88.0f;
    private int idle;
    private boolean autoFire;
    private int autoFireT;

    public SamLauncherEntity(EntityType<?> type, Level level, VehicleKind kind) {
        super(type, level, kind);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(LOCK, -1);
        this.entityData.define(LOCK_T, 0);
    }

    @Override
    public boolean radarActive() {
        return this.kind.selfRadar() && this.isAlive();
    }

    @Override
    public double radarRange() {
        return (double)((Integer)RWConfig.COMMON.radarRange.get()).intValue() * 0.5;
    }

    public int lockId() {
        return (Integer)this.entityData.get(LOCK);
    }

    public int lockProgress() {
        return (Integer)this.entityData.get(LOCK_T);
    }

    public boolean locked() {
        return this.lockId() >= 0 && this.lockProgress() >= 40;
    }

    @Override
    protected Item ammoItem() {
        return (Item)ModItems.SAM_48N6.get();
    }

    @Override
    protected float elevRate() {
        return 0.9f;
    }

    @Override
    protected boolean deployedClear() {
        return this.el() < 3.5f;
    }

    public List<Entity> radarTargets() {
        return RadarNet.picture(this);
    }

    public void fireAt(ServerPlayer p, int targetId) {
        if (this.operator() != p) {
            return;
        }
        if (RadarNet.sources(this).isEmpty()) {
            p.displayClientMessage((Component)Component.translatable((String)"hud.aiarsenal.rw.no_radar"), true);
            return;
        }
        Entity t = null;
        for (Entity e : RadarNet.picture(this)) {
            if (e.getId() != targetId) continue;
            t = e;
        }
        if (t == null) {
            p.displayClientMessage((Component)Component.translatable((String)"hud.aiarsenal.rw.no_target"), true);
            return;
        }
        this.entityData.set(LOCK, targetId);
        this.entityData.set(LOCK_T, 0);
        this.autoFire = true;
        this.autoFireT = 400;
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.LOCK_TONE.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    @Override
    public boolean usesAltitude() {
        return true;
    }

    @Override
    protected void onTargetAssigned(ServerPlayer p) {
        Vec3 c = this.mapTarget();
        Entity best = null;
        double bd = 22500.0;
        if (c != null) {
            for (Entity e : this.radarTargets()) {
                double d = e.position().distanceToSqr(c);
                if (!(d < bd)) continue;
                bd = d;
                best = e;
            }
        }
        if (best == null) {
            p.displayClientMessage((Component)Component.translatable((String)"hud.aiarsenal.rw.cue_empty"), true);
            return;
        }
        this.entityData.set(LOCK, best.getId());
        this.entityData.set(LOCK_T, 0);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.LOCK_TONE.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    @Override
    protected void onLock(ServerPlayer p, int seat) {
        if (seat != this.operatorSeat()) {
            return;
        }
        List<Entity> list = this.radarTargets();
        if (list.isEmpty()) {
            this.entityData.set(LOCK, -1);
            this.entityData.set(LOCK_T, 0);
            p.displayClientMessage((Component)Component.translatable((String)"hud.aiarsenal.rw.no_target"), true);
            return;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        list.sort(Comparator.comparingDouble(e -> -look.dot(e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0).subtract(eye).normalize())));
        int cur = this.lockId();
        int idx = 0;
        if (list.get(0).getId() == cur && list.size() > 1) {
            idx = 1;
        }
        this.entityData.set(LOCK, list.get(idx).getId());
        this.entityData.set(LOCK_T, 0);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.LOCK_TONE.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    private Entity lockedEntity() {
        int id = this.lockId();
        if (id < 0) {
            return null;
        }
        Entity e = this.level().getEntity(id);
        return e != null && AirTargets.isAirTarget(e) ? e : null;
    }

    @Override
    protected float[] aimTarget() {
        float[] fArray;
        boolean lower;
        boolean want;
        boolean bl = want = this.lockId() >= 0 || this.fireHeld(this.operatorSeat()) || this.autoFire;
        if (want) {
            this.idle = 0;
            return new float[]{0.0f, 88.0f};
        }
        ++this.idle;
        boolean bl2 = lower = this.reloadHeld(this.operatorSeat()) || Math.abs(this.throttle()) > 0.05f || this.idle > 400;
        if (lower) {
            float[] fArray2 = new float[2];
            fArray2[0] = 0.0f;
            fArray = fArray2;
            fArray2[1] = 0.0f;
        } else {
            fArray = null;
        }
        return fArray;
    }

    @Override
    protected boolean mayReload() {
        return super.mayReload() && this.el() < 3.5f;
    }

    @Override
    protected void serverLaunch() {
        ServerLevel sl;
        block18: {
            block17: {
                Level level;
                boolean trigger;
                int id = this.lockId();
                if (id >= 0 && this.tickCount % 20 == 0 && RadarNet.sources(this).isEmpty()) {
                    this.entityData.set(LOCK, -1);
                    this.entityData.set(LOCK_T, 0);
                    this.autoFire = false;
                    ServerPlayer o = this.operator();
                    if (o != null) {
                        o.displayClientMessage((Component)Component.translatable((String)"hud.aiarsenal.rw.radar_lost"), true);
                    }
                    id = -1;
                }
                if (this.autoFire && (--this.autoFireT <= 0 || this.lockId() < 0)) {
                    this.autoFire = false;
                }
                if (id >= 0) {
                    Entity t = this.lockedEntity();
                    if (t == null || t.distanceToSqr((Entity)this) > 810000.0) {
                        this.entityData.set(LOCK, -1);
                        this.entityData.set(LOCK_T, 0);
                    } else if (this.lockProgress() < 40) {
                        this.entityData.set(LOCK_T, (this.lockProgress() + 1));
                        if (this.lockProgress() == 40) {
                            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.LOCK_DONE.get(), SoundSource.NEUTRAL, 1.2f, 1.0f);
                        } else if (this.lockProgress() % 10 == 0) {
                            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.LOCK_TONE.get(), SoundSource.NEUTRAL, 0.6f, 1.0f + (float)this.lockProgress() / 80.0f);
                        }
                    }
                }
                ServerPlayer op = this.operator();
                boolean bl = trigger = this.fireHeld(this.operatorSeat()) || this.autoFire;
                if (op == null || !trigger || !((level = this.level()) instanceof ServerLevel)) break block17;
                sl = (ServerLevel)level;
                if (this.fireCd <= 0) break block18;
            }
            return;
        }
        if (!this.locked()) {
            this.notifyOp(this.lockId() >= 0 ? "hud.aiarsenal.rw.locking" : "hud.aiarsenal.rw.no_target");
            return;
        }
        if (this.loadedCount() == 0) {
            this.notifyOp("hud.aiarsenal.rw.no_ammo");
            return;
        }
        if (this.jack() < 0.97f || Math.abs(this.speedMs) > 0.4) {
            this.notifyOp("hud.aiarsenal.rw.jacks_moving");
            return;
        }
        if (this.el() < 84.0f) {
            this.notifyOp("hud.aiarsenal.rw.raising");
            return;
        }
        int k = this.firstLoaded();
        Vec3 muzzle = this.muzzleWorld(k);
        Vec3 dir = this.launchDir();
        SamMissileEntity.launch(sl, muzzle.add(dir.scale(0.5)), dir, this.lockId(), this.getId());
        this.markFired(k);
        this.autoFire = false;
        this.fireCd = this.kind.fireInterval;
        sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, (SoundEvent)ModSounds.SAM_LAUNCH.get(), SoundSource.NEUTRAL, 14.0f, 1.0f);
    }

    @Override
    protected void launcherPose(Pose pose, float pt) {
        int rt = this.reloadTube();
        float open = Mth.clamp((float)((Mth.lerp((float)pt, (float)this.elVisO, (float)this.elVis) - 50.0f) / 30.0f), (float)0.0f, (float)1.0f);
        for (int k = 0; k < this.kind.magazine; ++k) {
            boolean present = this.isLoaded(k) || k == rt;
            pose.hide("canister_" + k, !present);
            pose.hide("lid_" + k, !present);
            pose.rotate("lid_" + k, (float)(-Math.toRadians(100.0 * (double)open)), 1.0f, 0.0f, 0.0f);
            if (k != rt) continue;
            float p = Mth.clamp((float)(((float)this.reloadTicks() + pt) / (float)this.kind.reloadTicks), (float)0.0f, (float)1.0f);
            float e = p * p * (3.0f - 2.0f * p);
            pose.translate("canister_" + k, 0.0f, (1.0f - e) * 2.6f, -(1.0f - e) * 9.5f);
            pose.translate("lid_" + k, 0.0f, (1.0f - e) * 2.6f, -(1.0f - e) * 9.5f);
        }
        pose.hide("rocket_load", true);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
    }
}

