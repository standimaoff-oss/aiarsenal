/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.vehicle;

import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.item.DesignatorItem;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.RocketEntity;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModSounds;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MlrsEntity
extends LauncherVehicle {
    private float[] cachedSolution;
    private int solveTick = -100;
    private boolean outOfRange;
    public boolean outOfSector;

    public MlrsEntity(EntityType<?> type, Level level, VehicleKind kind) {
        super(type, level, kind);
    }

    public Ballistics.Profile profile() {
        return this.kind == VehicleKind.GRAD ? Ballistics.Profile.GRAD : (this.kind == VehicleKind.BM13 ? Ballistics.Profile.M13 : Ballistics.Profile.SMERCH);
    }

    @Override
    protected Item ammoItem() {
        return (Item)(this.kind == VehicleKind.GRAD ? ModItems.GRAD_ROCKET : (this.kind == VehicleKind.BM13 ? ModItems.ROCKET_M13 : ModItems.SMERCH_ROCKET)).get();
    }

    @Override
    protected void onLock(ServerPlayer p, int seat) {
        if (seat != this.operatorSeat()) {
            return;
        }
        if (this.autoAim()) {
            this.entityData.set(AUTO, false);
            return;
        }
        if (this.mapTarget() != null || DesignatorItem.findTarget(p) != null) {
            this.entityData.set(AUTO, true);
            this.solveTick = -100;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.LOCK_DONE.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
        }
    }

    @Override
    protected float[] aiSolution() {
        Vec3 tg = this.aiAim;
        if (tg == null) return null;
        Vec3 from = this.position().add(0, this.spec.height() * 0.6, 0);
        double dx = tg.x - from.x, dz = tg.z - from.z;
        double k = Ballistics.scaleK();
        double el = Ballistics.solveElevation(this.profile(), Math.sqrt(dx * dx + dz * dz) * k, (tg.y - from.y) * k,
                Math.max(this.kind.elMin, 0.0f), this.kind.elMax);
        if (Double.isNaN(el)) el = Math.max(this.kind.elMin, 0.0f);
        return new float[]{this.azRelFromWorldYaw((float) Math.toDegrees(Math.atan2(-dx, dz))), (float) el};
    }

    @Override
    protected float[] aimTarget() {
        if (this.aiGunner()) {
            float[] ai = this.aiSolution();
            if (ai != null) this.outOfSector = Math.abs(ai[0]) > this.kind.traverse + 0.5f;
            return ai;
        }
        float[] t;
        ServerPlayer op = this.operator();
        if (op == null) {
            return null;
        }
        if (this.autoAim()) {
            t = this.solveDesignated(op);
            if (t == null) {
                this.entityData.set(AUTO, false);
                return null;
            }
        } else {
            t = new float[]{this.azRelFromWorldYaw(op.getYRot()), -op.getXRot()};
        }
        this.outOfSector = Math.abs(t[0]) > this.kind.traverse + 0.5f;
        return t;
    }

    private float[] solveDesignated(ServerPlayer op) {
        Vec3 tg;
        if (this.tickCount - this.solveTick < 10 && this.cachedSolution != null) {
            return this.cachedSolution;
        }
        Vec3 vec3 = tg = this.mapTarget() != null ? this.mapTarget() : DesignatorItem.findTarget(op);
        if (tg == null) {
            return null;
        }
        this.solveTick = this.tickCount;
        Vec3 from = this.muzzleWorld(Math.max(0, this.firstLoaded()));
        double dx = tg.x - from.x;
        double dz = tg.z - from.z;
        double k = Ballistics.scaleK();
        double rangeM = Math.sqrt(dx * dx + dz * dz) * k;
        double dyM = (tg.y - from.y) * k;
        double el = Ballistics.solveElevation(this.profile(), rangeM, dyM, this.kind.elMin, this.kind.elMax);
        this.outOfRange = Double.isNaN(el);
        if (this.outOfRange) {
            el = Ballistics.range(this.profile(), 45.0, dyM) < rangeM ? (double)(this.kind.elMax - 8.0f) : (double)this.kind.elMin;
        }
        float azWorld = (float)Math.toDegrees(Math.atan2(-dx, dz));
        this.cachedSolution = new float[]{this.azRelFromWorldYaw(azWorld), (float)el};
        return this.cachedSolution;
    }

    @Override
    protected void onTargetAssigned(ServerPlayer p) {
        this.solveTick = -100;
        super.onTargetAssigned(p);
    }

    public boolean outOfRange() {
        return this.outOfRange;
    }

    @Override
    protected void serverLaunch() {
        boolean ai = this.aiGunner();
        ServerPlayer op = this.operator();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (ai ? !this.aiTrigger : (op == null || !this.fireHeld(this.operatorSeat()))) {
            return;
        }
        ServerLevel sl = (ServerLevel) level;
        double spreadMul = ai ? this.aiSpread : 1.0;
        if (this.fireCd > 0) {
            return;
        }
        if (this.reloadTube() >= 0) {
            this.notifyOp("hud.aiarsenal.rw.reloading");
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
        if (this.outOfSector) {
            this.notifyOp("hud.aiarsenal.rw.out_of_sector");
            return;
        }
        if (!this.aimSettled(ai ? this.aiSolution() : this.aimTarget())) {
            return;
        }
        int k = this.firstLoaded();
        Vec3 muzzle = this.muzzleWorld(k);
        Vec3 dir = this.launchDir();
        double sig = (this.kind == VehicleKind.GRAD ? 0.0055 : (this.kind == VehicleKind.BM13 ? 0.009 : 0.0035)) * spreadMul;   // M-13 rail launch: wide salvo pattern
        Vec3 d = dir.add(sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig * 0.6, sl.random.nextGaussian() * sig).normalize();
        RocketEntity.launch(sl, this.profile(), muzzle.add(d.scale(0.6)), d, this.getId());
        this.markFired(k);
        this.fireCd = this.kind.fireInterval;
        SoundEvent snd = (SoundEvent)(this.kind == VehicleKind.SMERCH ? ModSounds.ROCKET_LAUNCH_LARGE : ModSounds.ROCKET_LAUNCH_SMALL).get();
        sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, snd, SoundSource.NEUTRAL, this.kind == VehicleKind.SMERCH ? 14.0f : 9.0f, (this.kind == VehicleKind.BM13 ? 0.8f : 0.95f) + sl.random.nextFloat() * 0.1f);
        this.backblast(sl, muzzle, dir);
    }

    private void backblast(ServerLevel sl, Vec3 muzzle, Vec3 dir) {
        Vec3 breech = muzzle.subtract(dir.scale((double)this.kind.tubeLen));
        Vec3 back = dir.scale(-1.0);
        double len = this.kind.backblast;
        AABB box = new AABB(breech, breech.add(back.scale(len))).inflate(len * 0.4);
        for (Entity e : sl.getEntities((Entity)this, box, x -> x instanceof LivingEntity && x.getVehicle() != this && x != this)) {
            double cos;
            Vec3 to = e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0).subtract(breech);
            double dist = to.length();
            if (dist < 0.5 || dist > len || (cos = to.normalize().dot(back)) < 0.92) continue;
            float dmg = (float)(22.0 * Math.pow(1.0 - dist / len, 1.2));
            e.hurt(ModDamage.source((Level)sl, ModDamage.BLAST), dmg);
            if (!(dist < len * 0.6)) continue;
            e.setSecondsOnFire(4);
        }
    }

    @Override
    protected void launcherPose(Pose pose, float pt) {
        int rt = this.reloadTube();
        for (int k = 0; k < this.kind.magazine; ++k) {
            pose.hide("plug_" + k, !this.isLoaded(k));
        }
        pose.hide("rocket_load", rt < 0);
        if (rt >= 0) {
            float p = Mth.clamp((float)(((float)this.reloadTicks() + pt) / (float)this.kind.reloadTicks), (float)0.0f, (float)1.0f);
            float e = p * p * (3.0f - 2.0f * p);
            float[] tb = this.muzzleLocal(rt);
            // the reload rocket mesh lies tail-at-origin, nose +Z; it slides in from the breech side towards the muzzle
            float ax = this.kind.fireAxisZ();
            float seated = tb[2] - ax * this.kind.tubeLen;
            if (ax < 0.0f) {
                pose.rotate("rocket_load", (float)Math.PI, 0.0f, 1.0f, 0.0f);
            }
            pose.translate("rocket_load", tb[0], tb[1], seated - ax * (1.0f - e) * (this.kind.tubeLen + 0.7f));
        }
    }
}

