package ru.aiforge.realwar.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.entity.IcbmEntity;
import ru.aiforge.arsenal.registry.ArsenalRegistry;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.radar.RadarNet;
import ru.aiforge.realwar.registry.ModSounds;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * RS-24 "Yars" mobile ICBM system on the MZKT-79221 16x16 chassis — drivable Real War vehicle.
 * Seat 0 = driver (drive mode), seat 1 = launch officer (fire mode); switch with the seat key.
 * Firing: the officer opens the radar map (M), picks a point on the 5x5 km map or a ground contact reported by a linked
 * radar, presses "ПУСК" (or LOCK + fire). The TEL stops, lowers its 4 outriggers, erects the TPK to vertical
 * (yarstel_erect clip driven by the elevation), blows the cap and cold-launches the missile (arsenal IcbmEntity).
 * The vehicle cannot move while the TPK is up; after the launch it lowers the empty TPK automatically.
 */
public class YarsVehicle extends LauncherVehicle {
    private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(YarsVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Missile base in the pitch frame (hinge origin): the canister axis is +Z, the missile node sits 10.6 m up it. */
    public static final float[] MISSILE_BASE = {0f, 1.17f, 10.6f};
    private int trackId = -1;
    private int lowerAt = -1;
    @Nullable
    private UUID launchedBy;

    public YarsVehicle(EntityType<?> type, Level level) {
        super(type, level, VehicleKind.YARS);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ARMED, false);
    }

    /** Launch armed: the TEL fires by itself as soon as the TPK is vertical. */
    public boolean armed() {
        return this.entityData.get(ARMED);
    }

    @Override
    protected Item ammoItem() {
        return ArsenalRegistry.item(Kind.YARS);
    }

    /** 0..1 TPK erection (synced elevation / 90 deg). */
    public float erectFrac() {
        return Mth.clamp(this.el() / 90f, 0f, 1f);
    }

    /** Readable state for the HUD / radar map: 0 empty, 1 no target, 2 march, 3 jacks, 4 erecting, 5 ready, 6 lowering. */
    public int stage() {
        if (this.loadedCount() == 0) return this.el() > 0.5f ? 6 : 0;
        if (this.mapTarget() == null) return this.el() > 0.5f ? 6 : 1;
        if (!this.autoAim()) return this.el() > 0.5f ? 6 : 2;
        if (this.jack() < 0.97f) return 3;
        if (this.el() < 89.5f) return 4;
        return 5;
    }

    // ------------------------------------------------------------------ targeting
    @Override
    protected void onLock(ServerPlayer p, int seat) {
        if (seat != this.operatorSeat()) return;
        if (this.autoAim()) {
            this.entityData.set(AUTO, false);
            this.entityData.set(ARMED, false);
            return;
        }
        if (this.mapTarget() != null) {
            this.entityData.set(AUTO, true);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.LOCK_DONE.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
        } else {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.a_no_target"), true);
        }
    }

    @Override
    public void setMapTarget(ServerPlayer p, @Nullable BlockPos pos) {
        this.trackId = -1;
        if (pos == null) this.entityData.set(ARMED, false);
        super.setMapTarget(p, pos);
    }

    /** Picking a point only designates the target: erection starts on ПУСК (radar map) or on the lock key. */
    @Override
    protected void onTargetAssigned(ServerPlayer p) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.LOCK_DONE.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
    }

    @Override
    public boolean usesAltitude() {
        return false;
    }

    /**
     * Radar map "ПУСК": targetId >= 0 = a ground contact of a linked radar (tracked until lift-off), -1 = the map point
     * already set. Arms the launch.
     */
    public void radarLaunch(ServerPlayer p, int targetId) {
        if (targetId >= 0) {
            Entity t = this.level().getEntity(targetId);
            boolean seen = false;
            if (t != null) for (Entity e : RadarNet.picture(this)) if (e == t) { seen = true; break; }
            if (!seen) {
                p.displayClientMessage(Component.translatable("msg.aiarsenal.rw.yars_lost"), true);
                return;
            }
            super.setMapTarget(p, t.blockPosition());
            this.trackId = targetId;
        }
        if (this.mapTarget() == null) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.a_no_target"), true);
            return;
        }
        if (this.loadedCount() == 0) {
            p.displayClientMessage(Component.translatable("hud.aiarsenal.rw.no_ammo"), true);
            return;
        }
        this.entityData.set(AUTO, true);
        this.entityData.set(ARMED, true);
        this.launchedBy = p.getUUID();
        p.displayClientMessage(Component.translatable("msg.aiarsenal.rw.yars_armed"), true);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.LOCK_DONE.get(), SoundSource.NEUTRAL, 1.2f, 0.7f);
    }

    @Override
    protected float[] aimTarget() {
        if (this.autoAim() && this.mapTarget() != null && this.loadedCount() > 0) {
            // the TPK only rises on lowered outriggers and a stopped chassis
            return this.jack() >= 0.97f && Math.abs(this.speedMs) < 0.3 ? new float[]{0f, 90f} : new float[]{0f, this.el()};
        }
        return new float[]{0f, 0f};
    }

    @Override
    protected float elevRate() {
        return 0.6f;          // 0 -> 90 deg in 7.5 s
    }

    @Override
    protected boolean deployedClear() {
        return this.el() < 0.5f;
    }

    @Override
    protected void updateJacks() {
        if (this.el() > 0.5f) return;       // outriggers stay down while the TPK is up
        super.updateJacks();
    }

    @Override
    protected boolean mayReload() {
        return super.mayReload() && this.el() < 0.5f;
    }

    // ------------------------------------------------------------------ launch
    @Override
    protected void serverLaunch() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        if (this.lowerAt > 0 && this.tickCount >= this.lowerAt) {
            this.lowerAt = -1;
            this.entityData.set(AUTO, false);
        }
        if (this.trackId >= 0 && this.tickCount % 10 == 0) {
            Entity t = sl.getEntity(this.trackId);
            if (t != null && t.isAlive()) this.entityData.set(TARGET, java.util.Optional.of(t.blockPosition()));
        }
        ServerPlayer op = this.operator();
        boolean trigger = this.armed() || (op != null && this.fireHeld(this.operatorSeat()));
        if (!trigger || this.fireCd > 0) return;
        Vec3 tg = this.mapTarget();
        if (this.loadedCount() == 0 || tg == null) {
            if (op != null && this.fireHeld(this.operatorSeat())) this.notifyOp(this.loadedCount() == 0 ? "hud.aiarsenal.rw.no_ammo" : "msg.aiarsenal.a_no_target");
            return;
        }
        if (!this.autoAim()) {
            if (op != null) this.notifyOp("msg.aiarsenal.rw.yars_need_lock");
            return;
        }
        if (this.el() < 89.5f) {
            if (op != null && this.fireHeld(this.operatorSeat())) this.notifyOp(this.jack() < 0.97f ? "hud.aiarsenal.rw.jacks_moving" : "msg.aiarsenal.rw.yars_erecting");
            return;
        }
        Vec3 base = this.missileWorld();
        UUID owner = this.launchedBy != null ? this.launchedBy : op != null ? op.getUUID() : null;
        IcbmEntity.launch(sl, base.subtract(0, 1.0, 0), tg, owner);
        this.markFired(0);
        this.entityData.set(ARMED, false);
        this.trackId = -1;
        this.fireCd = this.kind.fireInterval;
        this.lowerAt = this.tickCount + 100;
        Vec3 top = this.muzzleWorld(0);
        sl.sendParticles(ParticleTypes.EXPLOSION, top.x, top.y, top.z, 3, 0.6, 0.6, 0.6, 0);
        sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), base.x, this.getY() + 1, base.z, 80, 4, 1, 4, 0.03);
        sl.sendParticles(ParticleTypes.CLOUD, base.x, this.getY() + 0.5, base.z, 60, 5, 0.4, 5, 0.08);
    }

    /** World position of the missile node inside the TPK (follows the erection). */
    public Vec3 missileWorld() {
        Vector3d v = this.pitchFrame(this.az(), this.el()).transformPosition(new Vector3d(MISSILE_BASE[0], MISSILE_BASE[1], MISSILE_BASE[2]));
        return new Vec3(v.x, v.y, v.z);
    }

    // ------------------------------------------------------------------ pose (client)
    private static final int[] STEER_AXLES = {0, 1, 2, 5, 6, 7};

    @Override
    public void applyPose(Pose pose, float pt) {
        this.applyCommonPose(pose, pt);
        GlbModel m = pose.model();
        float el = Mth.lerp(pt, this.elVisO, this.elVis);
        GlbModel.Animation erect = m.animation("yarstel_erect");
        if (erect != null) pose.sample(erect, Mth.clamp(el / 90f, 0f, 1f) * erect.duration * 0.9999f);
        GlbModel.Animation jacks = m.animation("yarstel_jacks");
        if (jacks != null) pose.sample(jacks, Mth.clamp(this.jack(), 0f, 1f) * jacks.duration * 0.9999f);
        boolean loaded = this.isLoaded(0) || this.reloadTube() >= 0;
        pose.hide("YARSTEL_Cap", !loaded);
        pose.hide("YARSTEL_Missile", !loaded);
        // 8 axles: front three steer with the wheel, rear three counter-steer (crab-free all-wheel steering)
        float roll = (float) Mth.lerp(pt, this.rollDistO, this.rollDist);
        float steer = Mth.lerp(pt, this.steerVisO, this.steerVis);
        float r = this.spec.wheels()[0][3];
        for (int a = 0; a < 8; a++) {
            float k = switch (a) {
                case 0 -> 1f;
                case 1 -> 0.78f;
                case 2 -> 0.5f;
                case 5 -> -0.35f;
                case 6 -> -0.62f;
                case 7 -> -0.85f;
                default -> 0f;
            };
            for (String sn : new String[]{"L", "R"}) {
                if (k != 0f) pose.rotate("YARSTEL_St" + a + sn, (float) Math.toRadians(steer * k), 0f, 1f, 0f);
                pose.rotate("YARSTEL_W" + a + sn, -roll / r, 1f, 0f, 0f);
            }
        }
    }

    @Override
    protected void launcherPose(Pose pose, float pt) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(ARMED, false);
        this.entityData.set(AUTO, false);
        this.entityData.set(EL, 0f);
        this.elVis = 0f;
        if (tag.contains("tx")) this.entityData.set(TARGET, java.util.Optional.of(new BlockPos(tag.getInt("tx"), tag.getInt("ty"), tag.getInt("tz"))));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        Vec3 t = this.mapTarget();
        if (t != null) {
            tag.putInt("tx", Mth.floor(t.x));
            tag.putInt("ty", Mth.floor(t.y));
            tag.putInt("tz", Mth.floor(t.z));
        }
    }
}
