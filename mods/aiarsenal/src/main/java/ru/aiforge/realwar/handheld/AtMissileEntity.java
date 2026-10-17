package ru.aiforge.realwar.handheld;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.registry.ModSounds;

/**
 * Shoulder-fired munition: PG-7VL (unguided, light drop), Igla (IR homing, lead pursuit, proximity fuze), Javelin
 * (soft launch, top-attack climb then dive onto the locked vehicle or point). Yaw/pitch are kept from the velocity
 * (yaw = atan2(vx, vz), pitch = up-positive) and used by the GLB renderer.
 */
public class AtMissileEntity extends Projectile {
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(AtMissileEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(AtMissileEntity.class, EntityDataSerializers.INT);

    private Vec3 aimPoint;            // Javelin point lock / last known target position
    private boolean diving;
    private double apex;
    public static int detonations;    // gametest counter
    public static String lastDetonation = "";

    public AtMissileEntity(EntityType<? extends AtMissileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static AtMissileEntity launch(Level level, Entity shooter, HandheldKind k, Vec3 pos, Vec3 dir, int targetId, Vec3 point) {
        AtMissileEntity m = new AtMissileEntity(ru.aiforge.realwar.registry.ModEntities.AT_MISSILE.get(), level);
        m.setOwner(shooter);
        m.entityData.set(KIND, (byte) k.ordinal());
        m.entityData.set(TARGET, targetId);
        m.aimPoint = point;
        m.setPos(pos.x, pos.y, pos.z);
        double v0 = k == HandheldKind.JAVELIN ? 0.9 : (k.air() ? 1.6 : (k == HandheldKind.TOW ? 1.2 : 2.2));   // ejection, then the motor
        m.setDeltaMovement(dir.normalize().scale(v0));
        m.faceVelocity();
        m.yRotO = m.getYRot();
        m.xRotO = m.getXRot();
        level.addFreshEntity(m);
        return m;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(KIND, (byte) 0);
        this.entityData.define(TARGET, -1);
    }

    public HandheldKind kind() {
        return HandheldKind.byOrdinal(this.entityData.get(KIND));
    }

    public int targetId() {
        return this.entityData.get(TARGET);
    }

    private void faceVelocity() {
        Vec3 v = this.getDeltaMovement();
        if (v.lengthSqr() < 1e-8) return;
        this.setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    @Override
    public void tick() {
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        super.tick();
        HandheldKind k = this.kind();
        Vec3 v = this.getDeltaMovement();
        double sp = v.length();
        // motor: ramp to cruise speed (Javelin ignites its flight motor after the soft launch)
        int ign = k == HandheldKind.JAVELIN ? 4 : 1;
        if (this.tickCount >= ign && sp < k.speed) sp = Math.min(k.speed, sp + (k == HandheldKind.RPG7 ? 0.8 : 0.7));
        Vec3 dir = sp > 1e-6 ? v.scale(1.0 / v.length()) : new Vec3(0, 0, 1);
        if (!this.level().isClientSide) {
            Vec3 want = this.guidance(k, dir, sp);
            // terminal dive: the Javelin pitches over hard (fins fully deflected)
            if (want != null) dir = turnToward(dir, want, Math.toRadians(k.turn * (this.diving ? 2.8 : 1.0)));
        }
        v = dir.scale(sp);
        if (k == HandheldKind.RPG7 && this.tickCount > 6) v = v.add(0, -0.010, 0);           // sustainer out: gentle drop
        if (k == HandheldKind.JAVELIN && this.tickCount < ign) v = v.add(0, -0.03, 0);         // ejected tube falls slightly before ignition
        this.setDeltaMovement(v);

        if (!this.level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                this.setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                this.detonate(hit instanceof EntityHitResult eh ? eh.getEntity() : null);
                return;
            }
            if (k.air()) {
                // proximity fuse (Stinger: impact fuze -> only the hitbox tolerance) over the whole tick segment (7 blocks/tick would otherwise skip past)
                Entity t = this.level().getEntity(this.targetId());
                if (t != null && this.tickCount > 8) {
                    Vec3 c = t.getBoundingBox().getCenter(), a = this.position();
                    double s = v.lengthSqr() < 1e-9 ? 0 : Mth.clamp(c.subtract(a).dot(v) / v.lengthSqr(), 0.0, 1.0);
                    Vec3 q = a.add(v.scale(s));
                    if (q.distanceTo(c) < (k == HandheldKind.STINGER ? 2.5 : 4.0)) {
                        this.setPos(q.x, q.y, q.z);
                        this.detonate(t);
                        return;
                    }
                }
            }
            if (this.tickCount > k.life || this.getY() < this.level().getMinBuildHeight() - 16) {
                this.detonate(null);
                return;
            }
        }
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        this.faceVelocity();
        if (this.level().isClientSide) this.trail(k, v);
    }

    /** Desired flight direction (unit) or null to fly on. */
    private Vec3 guidance(HandheldKind k, Vec3 dir, double sp) {
        if (k == HandheldKind.TOW) return this.saclos(sp);
        if (k == HandheldKind.RPG7 || this.tickCount < (k == HandheldKind.JAVELIN ? 5 : 3)) return null;
        Entity t = this.targetId() >= 0 ? this.level().getEntity(this.targetId()) : null;
        if (k.air() && t != null && !(t instanceof ru.aiforge.realwar.craft.CraftOrdnance)) {
            // IR seeker vs decoy flares: a flare inside the seeker's field of view may steal the lock
            Entity decoy = ru.aiforge.realwar.craft.Flares.decoyFor(this, t);
            if (decoy != null) {
                this.entityData.set(TARGET, decoy.getId());
                t = decoy;
            }
        }
        Vec3 tp;
        Vec3 tv = Vec3.ZERO;
        if (t != null && t.isAlive()) {
            tp = t.getBoundingBox().getCenter();
            tv = t.getDeltaMovement();
            this.aimPoint = tp;
        } else if (this.aimPoint != null) {
            tp = this.aimPoint;
        } else {
            return null;
        }
        Vec3 to = tp.subtract(this.position());
        double dist = to.length();
        if (k.air()) {
            double tgo = dist / Math.max(1.0, sp);
            Vec3 lead = tp.add(tv.scale(Math.min(tgo, 40.0)));
            return lead.subtract(this.position()).normalize();
        }
        // Javelin top attack: climb to an apex above the target, then dive steeply (direct attack when close)
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        if (this.apex == 0) {
            // profile chosen once at the first guidance tick: close targets get a direct attack
            this.apex = Math.max(tp.y, this.getY()) + Mth.clamp(horiz * 0.35, 14.0, 60.0);
            // like the real CLU: top attack needs room to climb, short shots fly direct (tools/sim/javelin_sim.py)
            if (horiz < 45.0) this.diving = true;
        }
        // pitch-over early enough that the turn radius still lands the dive on the target
        if (!this.diving && horiz < (this.getY() - tp.y) + sp * 2.0) this.diving = true;
        if (this.diving) return to.scale(1.0 / Math.max(1e-6, dist));
        Vec3 flat = new Vec3(to.x, 0, to.z).normalize();
        Vec3 climbTo = new Vec3(this.getX(), this.apex, this.getZ()).add(flat.scale(Math.max(10.0, horiz * 0.45)));
        return climbTo.subtract(this.position()).normalize();
    }

    /**
     * r12 TOW: semi-automatic command to line of sight. The launcher's sight line (turret pivot + gun direction of the
     * Bradley the shooter sits in) is the beam; the missile steers to a point a little ahead of its own projection on that
     * line, so it converges onto the beam and then flies along wherever the gunner keeps the crosshair. Wire cut
     * (shooter gone / out of the vehicle) -> flies on ballistically.
     */
    private Vec3 saclos(double sp) {
        if (this.tickCount < 3) return null;
        Entity o = this.getOwner();
        Entity veh = o == null ? null : (o instanceof ru.aiforge.realwar.vehicle.GunVehicle ? o : o.getVehicle());
        if (!(veh instanceof ru.aiforge.realwar.vehicle.GunVehicle g) || !g.isAlive()) return null;
        Vec3 origin = g.pivotWorld();
        Vec3 beam = g.launchDir();
        double t = this.position().subtract(origin).dot(beam);
        if (t < 0) return null;
        Vec3 aim = origin.add(beam.scale(t + Math.max(6.0, sp * 3.0)));
        return aim.subtract(this.position()).normalize();
    }

    static Vec3 turnToward(Vec3 cur, Vec3 want, double maxRad) {
        double dot = Mth.clamp(cur.dot(want), -1.0, 1.0);
        double ang = Math.acos(dot);
        if (ang <= maxRad || ang < 1e-6) return want;
        double f = maxRad / ang;
        return cur.scale(1 - f).add(want.scale(f)).normalize();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (e instanceof AtMissileEntity) return false;
        if (this.tickCount < 6 && (e == this.getOwner() || (this.getOwner() != null && e == this.getOwner().getVehicle()))) return false;
        return super.canHitEntity(e) || e instanceof ru.aiforge.realwar.vehicle.VehicleEntity;
    }

    private void detonate(Entity direct) {
        if (!(this.level() instanceof ServerLevel sl) || this.isRemoved()) return;
        HandheldKind k = this.kind();
        detonations++;
        lastDetonation = k + " age " + this.tickCount + " at " + this.blockPosition().toShortString() + " direct " + direct;
        if (direct != null) {
            direct.invulnerableTime = 0;
            direct.hurt(ModDamage.source(sl, ModDamage.ORDNANCE), k.direct);
        }
        ConventionalBlast.explode(sl, this.position(), k.tntKg, k.frag);
        sl.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BLAST_SMALL.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
        this.discard();
    }

    private void trail(HandheldKind k, Vec3 v) {
        Vec3 back = this.position().subtract(v.normalize().scale(0.6));
        Level l = this.level();
        boolean motor = k != HandheldKind.RPG7 || this.tickCount < 8;
        if (motor) l.addParticle(ParticleTypes.FLAME, back.x, back.y, back.z, 0, 0, 0);
        for (int i = 0; i < (k == HandheldKind.RPG7 ? 2 : 3); i++) {
            double f = i / 3.0;
            l.addParticle(motor ? ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get() : ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), back.x - v.x * f, back.y - v.y * f, back.z - v.z * f,
                    (this.random.nextDouble() - 0.5) * 0.02, 0.01, (this.random.nextDouble() - 0.5) * 0.02);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256.0 * 256.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        this.entityData.set(KIND, t.getByte("Kind"));
        this.entityData.set(TARGET, -1);
        if (t.contains("AimX")) this.aimPoint = new Vec3(t.getDouble("AimX"), t.getDouble("AimY"), t.getDouble("AimZ"));
        this.diving = t.getBoolean("Dive");
        this.apex = t.getDouble("Apex");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putByte("Kind", this.entityData.get(KIND));
        if (this.aimPoint != null) {
            t.putDouble("AimX", this.aimPoint.x);
            t.putDouble("AimY", this.aimPoint.y);
            t.putDouble("AimZ", this.aimPoint.z);
        }
        t.putBoolean("Dive", this.diving);
        t.putDouble("Apex", this.apex);
    }
}
