package ru.aiforge.realwar.damage;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.aiforge.realwar.registry.ModEntities;

/**
 * r18: a part shot off a vehicle - tank turret (ammo rack detonation), unrolled track, helicopter rotor, aircraft wing,
 * battleship barrel / secondary turret. Rendered from the parent's GLB (only the listed node subtrees, posed as they
 * were on the vehicle at the moment of the hit). Simple rigid-body flight: gravity, drag, bounce, tumbling.
 */
public class DebrisEntity extends Entity {
    public static final int RIGID = 0, TRACK = 1, ROTOR = 2;
    private static final EntityDataAccessor<CompoundTag> SPEC = SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private int life = 1200;
    private int burn;
    // ---- client state (tumble, cached render data)
    public final Quaternionf q = new Quaternionf(), qO = new Quaternionf();
    public Vector3f spin;
    public boolean landed;
    public float trackPhase = Float.NaN, trackPitch = 0.15f;
    public Object renderCache;

    public DebrisEntity(EntityType<? extends DebrisEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SPEC, new CompoundTag());
    }

    public CompoundTag spec() {
        return this.entityData.get(SPEC);
    }

    /**
     * @param model  GLB resource ("aiarsenal:models/glb/t90m.glb")
     * @param nodes  node names (subtrees) to draw, comma separated; "re:" prefix = regex
     * @param pivot  model-space point that sits at the entity position (+ half the box height)
     * @param frame  yaw, pitch, roll, dy of the parent at the moment of the hit (render order Y(-yaw) X(-pitch) Z(roll))
     * @param pose   node pose overrides "node:axis:rad;..." (axis x / y / z) or "node:s:sx" scale along z
     * @param spin   initial angular velocity (rad/s, world axes)
     */
    public static DebrisEntity spawn(ServerLevel sl, String model, String nodes, int mode, Vec3 pivotWorld, Vec3 velMs,
                                     float[] pivot, float[] frame, String pose, Vec3 spin, float half, int lifeTicks, int burnTicks, int parent) {
        DebrisEntity d = new DebrisEntity(ModEntities.DEBRIS.get(), sl);
        CompoundTag t = new CompoundTag();
        t.putString("m", model);
        t.putString("n", nodes);
        t.putInt("mode", mode);
        t.putFloat("px", pivot[0]);
        t.putFloat("py", pivot[1]);
        t.putFloat("pz", pivot[2]);
        t.putFloat("yaw", frame[0]);
        t.putFloat("pitch", frame[1]);
        t.putFloat("roll", frame[2]);
        t.putFloat("dy", frame.length > 3 ? frame[3] : 0f);
        t.putString("pose", pose == null ? "" : pose);
        t.putFloat("sx", (float) spin.x);
        t.putFloat("sy", (float) spin.y);
        t.putFloat("sz", (float) spin.z);
        t.putFloat("w", half);
        t.putInt("burn", burnTicks);
        t.putInt("parent", parent);
        d.entityData.set(SPEC, t);
        d.life = lifeTicks;
        d.burn = burnTicks;
        d.refreshDimensions();
        double hh = mode == TRACK ? 0 : d.getBbHeight() * 0.5;
        d.setPos(pivotWorld.x, pivotWorld.y - hh, pivotWorld.z);
        d.setDeltaMovement(velMs.scale(0.05));
        sl.addFreshEntity(d);
        return d;
    }

    public int mode() {
        return this.spec().getInt("mode");
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float w = this.spec().getFloat("w");
        if (w <= 0) return super.getDimensions(pose);
        return EntityDimensions.scalable(Mth.clamp(w * 2f, 0.4f, 4f), Mth.clamp(w * 0.7f, 0.3f, 2f));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SPEC.equals(key)) this.refreshDimensions();
    }

    @Override
    public void tick() {
        super.tick();
        int mode = this.mode();
        if (this.level().isClientSide) {
            this.clientTick(mode);
            return;
        }
        if (--this.life <= 0) {
            this.discard();
            return;
        }
        if (this.burn > 0) this.burn--;
        if (mode == TRACK) return;
        Vec3 v = this.getDeltaMovement();
        boolean water = this.isInWater();
        if (!this.onGround()) v = v.add(0, water ? -0.004 : -0.04, 0);
        v = water ? v.scale(0.82) : v.scale(0.992);
        double vyB = v.y;
        this.move(MoverType.SELF, v);
        if (this.onGround() || this.verticalCollision) {
            double bounce = vyB < -0.18 ? -vyB * 0.22 : 0.0;
            v = new Vec3(v.x * 0.55, bounce, v.z * 0.55);
            if (vyB < -0.3 && this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), this.getX(), this.getY() + 0.2, this.getZ(), 4, 0.5, 0.1, 0.5, 0.01);
                sl.playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.NEUTRAL,
                        Math.min(3f, (float) -vyB * 2f), 0.5f);
            }
        }
        if (this.horizontalCollision) v = new Vec3(-v.x * 0.3, v.y, -v.z * 0.3);
        this.setDeltaMovement(v);
        if (this.getY() < this.level().getMinBuildHeight() - 20) this.discard();
    }

    private void clientTick(int mode) {
        CompoundTag s = this.spec();
        int burn = s.getInt("burn");
        if (burn > 0 && this.tickCount < burn && mode != TRACK) {
            Vec3 p = this.position().add(0, this.getBbHeight() * 0.5, 0);
            if (this.tickCount % 2 == 0) this.level().addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), p.x, p.y, p.z, 0, 0.06, 0);
            if (this.tickCount < burn / 2 && this.random.nextInt(2) == 0) this.level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.03, 0);
        }
        this.qO.set(this.q);
        if (mode == TRACK) return;
        if (this.spin == null) this.spin = new Vector3f(s.getFloat("sx"), s.getFloat("sy"), s.getFloat("sz"));
        boolean ground = this.onGround() || (this.getDeltaMovement().lengthSqr() < 1e-5 && this.tickCount > 10);
        if (!ground && !this.isInWater()) {
            float dt = 0.05f;
            float a = this.spin.length() * dt;
            if (a > 1e-5f) {
                Vector3f ax = new Vector3f(this.spin).normalize();
                this.q.premul(new Quaternionf().fromAxisAngleRad(ax.x, ax.y, ax.z, a)).normalize();
            }
            if (mode == ROTOR) this.spin.mul(0.985f);
        } else {
            this.spin.mul(0.6f);
            // settle flat: keep the heading, put the piece's own up axis vertical (or upside down)
            Quaternionf frame = frame(s);
            Quaternionf total = new Quaternionf(this.q).mul(frame);
            Vector3f up = total.transform(new Vector3f(0, 1, 0));
            Vector3f fw = total.transform(new Vector3f(0, 0, 1));
            float yaw = (float) Math.atan2(fw.x, fw.z);
            Quaternionf rest = new Quaternionf().rotationY(yaw);
            if (up.y < 0) rest.rotateZ((float) Math.PI);
            Quaternionf target = rest.mul(new Quaternionf(frame).invert());
            this.q.slerp(target, 0.25f).normalize();
            this.landed = true;
        }
    }

    /** Parent orientation at the moment of the hit, render order Y(-yaw) X(-pitch) Z(roll). */
    public static Quaternionf frame(CompoundTag s) {
        return new Quaternionf().rotationY((float) Math.toRadians(-s.getFloat("yaw")))
                .rotateX((float) Math.toRadians(-s.getFloat("pitch")))
                .rotateZ((float) Math.toRadians(s.getFloat("roll")));
    }

    /** Vanilla explosions: a piece thrown by the blast that created it must not get the knockback a second time. */
    @Override
    public boolean ignoreExplosion() {
        return this.tickCount < 4;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
