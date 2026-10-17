/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompoundTag
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
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.Vec3
 *  org.joml.Matrix4d
 *  org.joml.Vector3d
 */
package ru.aiforge.realwar.vehicle;

import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.network.TracerPacket;
import ru.aiforge.realwar.ordnance.BulletManager;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModSounds;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

public class FlakEntity
extends VehicleEntity {
    protected static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(FlakEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Boolean> FIRING = SynchedEntityData.defineId(FlakEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Float> TAZ = SynchedEntityData.defineId(FlakEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> TEL = SynchedEntityData.defineId(FlakEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Integer> RELOAD_T = SynchedEntityData.defineId(FlakEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    public static final int CAPACITY = 100;
    public static final int BOX_ROUNDS = 50;
    public static final int RELOAD_TICKS = 40;
    public static final double MUZZLE_SPEED = 10.0;
    public float azVis;
    public float azVisO;
    public float elVis;
    public float elVisO;
    private int reloadT;
    private UUID reloadHolder;
    private int reloadUntil;

    public FlakEntity(EntityType<?> type, Level level) {
        super(type, level, VehicleKind.ZU23);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(AMMO, 0);
        this.entityData.define(FIRING, false);
        this.entityData.define(TAZ, Float.valueOf(0.0f));
        this.entityData.define(TEL, Float.valueOf(8.0f));
        this.entityData.define(RELOAD_T, 0);
    }

    public int ammo() {
        return (Integer)this.entityData.get(AMMO);
    }

    public void setAmmo(int a) {
        this.entityData.set(AMMO, Mth.clamp((int)a, (int)0, (int)100));
    }

    public boolean firing() {
        return (Boolean)this.entityData.get(FIRING);
    }

    public float az() {
        return ((Float)this.entityData.get(TAZ)).floatValue();
    }

    public float el() {
        return ((Float)this.entityData.get(TEL)).floatValue();
    }

    public int reloadProgress() {
        return (Integer)this.entityData.get(RELOAD_T);
    }

    @Override
    protected double cookOffMassKg() {
        return 4.0 + (double)this.ammo() * 0.02;
    }

    @Override
    public Vec3 seatWorld(int seat) {
        Matrix4d m = this.turretFrame(this.az());
        Vector3d v = m.transformPosition(new Vector3d(0.0, 0.46, -0.85));
        return new Vec3(v.x, v.y, v.z);
    }

    private Matrix4d turretFrame(double az) {
        float[] yp = this.spec.yawPivot();
        return new Matrix4d().translate(this.getX(), this.getY(), this.getZ()).rotateY(-Math.toRadians(this.getYRot())).translate((double)yp[0], (double)yp[1], (double)yp[2]).rotateY(Math.toRadians(az));
    }

    public Vec3 barrelMuzzle(int barrel, double az, double el) {
        float[] pp = this.spec.pitchPivot();
        Matrix4d m = this.turretFrame(az).translate((double)pp[0], (double)pp[1], (double)pp[2]).rotateX(-Math.toRadians(el));
        Vector3d v = m.transformPosition(new Vector3d(barrel == 0 ? 0.23 : -0.23, 0.01, 2.55));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 aimDir(double az, double el) {
        float[] pp = this.spec.pitchPivot();
        Matrix4d m = this.turretFrame(az).translate((double)pp[0], (double)pp[1], (double)pp[2]).rotateX(-Math.toRadians(el));
        Vector3d d = m.transformDirection(new Vector3d(0.0, 0.0, 1.0));
        return new Vec3(d.x, d.y, d.z).normalize();
    }

    @Override
    protected boolean onItemUse(ServerPlayer p, ItemStack st) {
        if (st.is((Item)ModItems.FLAK_AMMO_BOX.get()) && this.ammo() + 50 <= 100) {
            this.reloadHolder = p.getUUID();
            this.reloadUntil = this.tickCount + 60;
            return true;
        }
        return super.onItemUse(p, st);
    }

    @Override
    protected void onRearClick(ServerPlayer p) {
        this.reloadHolder = p.getUUID();
        this.reloadUntil = this.tickCount + 60;
    }

    private boolean takeBox(ServerPlayer p) {
        if (p.getAbilities().instabuild) {
            return true;
        }
        for (ItemStack st : p.getInventory().items) {
            if (!st.is((Item)ModItems.FLAK_AMMO_BOX.get())) continue;
            st.shrink(1);
            return true;
        }
        if (p.getOffhandItem().is((Item)ModItems.FLAK_AMMO_BOX.get())) {
            p.getOffhandItem().shrink(1);
            return true;
        }
        return false;
    }

    private boolean hasBox(ServerPlayer p) {
        if (p.getAbilities().instabuild) {
            return true;
        }
        for (ItemStack st : p.getInventory().items) {
            if (!st.is((Item)ModItems.FLAK_AMMO_BOX.get())) continue;
            return true;
        }
        return p.getOffhandItem().is((Item)ModItems.FLAK_AMMO_BOX.get());
    }

    @Override
    protected void serverTick() {
        ServerPlayer hp;
        Player player;
        ServerPlayer sp;
        super.serverTick();
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)level;
        Entity entity = this.passengerIn(0);
        ServerPlayer op = entity instanceof ServerPlayer ? (sp = (ServerPlayer)entity) : null;
        boolean fire = false;
        if (op != null) {
            float tAz = Mth.wrapDegrees((float)(this.getYRot() - op.getYRot()));
            float tEl = Mth.clamp((float)(-op.getXRot()), (float)-5.0f, (float)90.0f);
            float a = this.az();
            float e = this.el();
            float na = a + Mth.clamp((float)Mth.wrapDegrees((float)(tAz - a)), (float)-9.0f, (float)9.0f);
            float ne = e + Mth.clamp((float)(tEl - e), (float)-6.0f, (float)6.0f);
            if (na != a) {
                this.entityData.set(TAZ, Float.valueOf(Mth.wrapDegrees((float)na)));
            }
            if (ne != e) {
                this.entityData.set(TEL, Float.valueOf(ne));
            }
            boolean bl = fire = this.fireHeld(0) && this.ammo() > 0 && this.reloadT == 0;
        }
        if ((Boolean)this.entityData.get(FIRING) != fire) {
            this.entityData.set(FIRING, fire);
        }
        if (fire) {
            int fired = 0;
            for (int b = 0; b < 2; ++b) {
                if (this.ammo() - fired <= 0 || (this.tickCount + b * 3) % 6 == 0) continue;
                Vec3 m = this.barrelMuzzle(b, this.az(), this.el());
                Vec3 d = this.aimDir(this.az(), this.el());
                double sig = 0.004;
                Vec3 dir = d.add(sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig).normalize();
                Vec3 v = dir.scale(10.0);
                boolean tracer = (this.tickCount * 2 + b) % 4 == 0;
                BulletManager.spawn(sl, m, v, this.getId(), tracer);
                if (tracer) {
                    ModNetwork.sendNear(sl, m.x, m.y, m.z, 260.0, new TracerPacket(m, v, BulletManager.lifeTicks()));
                }
                ++fired;
            }
            this.setAmmo(this.ammo() - fired);
            if (this.tickCount % 3 == 0) {
                sl.playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), (SoundEvent)ModSounds.FLAK_BURST.get(), SoundSource.NEUTRAL, 7.0f, 0.92f + sl.random.nextFloat() * 0.16f);
            }
        }
        ServerPlayer rp = null;
        if (op != null && this.reloadHeld(0)) {
            rp = op;
        } else if (this.reloadHolder != null && this.tickCount < this.reloadUntil && (player = sl.getPlayerByUUID(this.reloadHolder)) instanceof ServerPlayer && (hp = (ServerPlayer)player).distanceToSqr((Entity)this) < 400.0) {
            rp = hp;
        }
        if (rp != null && !fire && this.ammo() + 50 <= 100 && this.hasBox(rp)) {
            ++this.reloadT;
            if (this.reloadT == 1) {
                sl.playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 1.0f, 0.9f);
            }
            if (this.reloadT >= 40) {
                if (this.takeBox(rp)) {
                    this.setAmmo(this.ammo() + 50);
                    sl.playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 1.2f, 1.2f);
                    this.reloadUntil = Math.max(this.reloadUntil, this.tickCount + 30);
                }
                this.reloadT = 0;
            }
        } else {
            this.reloadT = 0;
        }
        if ((Integer)this.entityData.get(RELOAD_T) != this.reloadT) {
            this.entityData.set(RELOAD_T, this.reloadT);
        }
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
            this.azVis += Mth.wrapDegrees((float)(this.az() - this.azVis)) * 0.6f;
            this.elVis += (this.el() - this.elVis) * 0.6f;
        }
    }

    @Override
    public void applyPose(Pose pose, float pt) {
        this.applyCommonPose(pose, pt);
        float az = this.azVisO + Mth.wrapDegrees((float)(this.azVis - this.azVisO)) * pt;
        float el = Mth.lerp((float)pt, (float)this.elVisO, (float)this.elVis);
        pose.rotate("turret_yaw", (float)Math.toRadians(az), 0.0f, 1.0f, 0.0f);
        pose.rotate("gun_pitch", (float)(-Math.toRadians(el)), 1.0f, 0.0f, 0.0f);
        if (this.firing()) {
            for (int i = 0; i < 2; ++i) {
                float ph = (((float)this.tickCount + pt) * 0.9f + (float)i * 1.7f) % 1.0f;
                pose.translate("barrel_" + i, 0.0f, 0.0f, -0.13f * (ph < 0.3f ? ph / 0.3f : (1.0f - ph) / 0.7f));   // recoil back along the barrel axis (gun_pitch +Z)
            }
        }
        pose.hide("ammo_box_0", this.ammo() <= 0);
        pose.hide("ammo_box_1", this.ammo() <= 50);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setAmmo(tag.getInt("ammo"));
        this.entityData.set(TAZ, Float.valueOf(tag.getFloat("az")));
        this.entityData.set(TEL, Float.valueOf(tag.contains("el") ? tag.getFloat("el") : 8.0f));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ammo", this.ammo());
        tag.putFloat("az", this.az());
        tag.putFloat("el", this.el());
    }
}

