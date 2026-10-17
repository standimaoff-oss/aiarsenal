package ru.aiforge.arsenal.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Warheads;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Guided missiles.
 * <ul>
 * <li>Kh-101 / Kh-55 (air launched): free fall clear of the bomber, wings / fins / engine pod deploy, turbofan start</li>
 * <li>Kalibr / Tomahawk (ground launched): solid booster along the tube axis, booster separation, wing deploy</li>
 * <li>cruise: terrain following (look-ahead over the heightmap) at low altitude, pop-up and terminal dive</li>
 * <li>AGM-114 Hellfire: rocket motor, semi-active laser homing on the designated point / entity</li>
 * <li>KAB-50: guided glide bomb steering toward the designated point</li>
 * </ul>
 */
public class MissileEntity extends ArsEntity {
    public static final int DROP = 0, BOOST = 1, CRUISE = 2, TERMINAL = 3;
    /** aux bits */
    public static final int B_BOOSTER = 1, B_ENGINE = 2;
    private int phaseT;
    private double speed;
    @Nullable
    private UUID targetEntity;
    private int fuel = 24000;

    public MissileEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static MissileEntity create(ServerLevel sl, Kind k, Vec3 pos, Vec3 vel, @Nullable BlockPos target, @Nullable UUID targetEntity,
                                       @Nullable UUID owner, int phase) {
        MissileEntity m = new MissileEntity(ArsenalRegistry.MISSILE.get(), sl);
        m.setKind(k);
        m.setPos(pos);
        m.setDeltaMovement(vel);
        m.speed = vel.length();
        m.setYRot(yawOf(vel));
        m.setPitch(pitchOf(vel));
        m.setTarget(target);
        m.targetEntity = targetEntity;
        m.owner = owner;
        m.setState(phase);
        m.fuel = switch (k) {
            case HELLFIRE -> 500;
            case KAB50 -> 1500;
            default -> 24000;
        };
        int aux = 0;
        if (phase == BOOST && (k == Kind.KALIBR || k == Kind.TOMAHAWK)) aux |= B_BOOSTER;
        if (k == Kind.HELLFIRE) aux |= B_ENGINE;
        m.setAux(aux);
        if (k == Kind.HELLFIRE || k == Kind.KAB50) m.setF1(1f);
        sl.addFreshEntity(m);
        return m;
    }

    /** Wing / fin deployment progress 0..1 (drives the {@code <id>_deploy} clip). */
    public float deploy() {
        return f1();
    }

    public boolean booster() {
        return (aux() & B_BOOSTER) != 0;
    }

    public boolean engine() {
        return (aux() & B_ENGINE) != 0;
    }

    @Nullable
    private Vec3 aim(ServerLevel sl) {
        if (targetEntity != null) {
            Entity e = sl.getEntity(targetEntity);
            if (e != null && e.isAlive()) return e.position().add(0, e.getBbHeight() * 0.5, 0);
        }
        BlockPos t = target();
        return t == null ? null : Vec3.atCenterOf(t);
    }

    static Vec3 turn(Vec3 cur, Vec3 want, double maxRad) {
        cur = cur.normalize();
        want = want.normalize();
        double dot = Mth.clamp(cur.dot(want), -1, 1);
        double ang = Math.acos(dot);
        if (ang <= maxRad || ang < 1e-6) return want;
        Vec3 rel = want.subtract(cur.scale(dot));
        if (rel.lengthSqr() < 1e-9) rel = new Vec3(0, 1, 0);
        rel = rel.normalize();
        return cur.scale(Math.cos(maxRad)).add(rel.scale(Math.sin(maxRad))).normalize();
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        phaseT++;
        if (--fuel <= 0 && kind() != Kind.KAB50) {
            setAux(aux() & ~B_ENGINE);
        }
        Kind k = kind();
        Vec3 v = getDeltaMovement();
        Vec3 aim = aim(sl);
        double g = 9.81 * scale() / 400.0;
        float yaw0 = getYRot();
        switch (k) {
            case HELLFIRE -> v = hellfire(v, aim);
            case KAB50 -> v = glide(v, aim, g);
            default -> v = cruise(sl, v, aim, g);
        }
        setDeltaMovement(v);
        if (v.lengthSqr() > 1e-6) {
            setYRot(yawOf(v));
            setPitch(pitchOf(v));
        }
        float dy = Mth.wrapDegrees(getYRot() - yaw0);
        setRoll(Mth.clamp(roll() * 0.8f + (-dy * 10f) * 0.2f, -70f, 70f));
        // exhaust
        Vec3 back = position().subtract(v.normalize().scale(k.width + 1.5));
        if (booster()) {
            sl.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 3, 0.1, 0.1, 0.1, 0.01);
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), back.x, back.y, back.z, 3, 0.2, 0.2, 0.2, 0.02);
        } else if (engine() && k == Kind.HELLFIRE) {
            sl.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 1, 0.02, 0.02, 0.02, 0.0);
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), back.x, back.y, back.z, 2, 0.05, 0.05, 0.05, 0.0);
        } else if (engine() && age % 2 == 0) {
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), back.x, back.y, back.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        HitResult h = sweep(v, true);
        if (h != null) {
            detonate(sl, h instanceof EntityHitResult eh ? eh.getEntity() : null);
            return;
        }
        if (getY() < sl.getMinBuildHeight() - 10 || age > 30000) discard();
    }

    private Vec3 hellfire(Vec3 v, @Nullable Vec3 aim) {
        speed = Math.min(bpt(425), speed + bpt(425) / 30.0);
        Vec3 d = v.lengthSqr() < 1e-6 ? dir(getYRot(), pitch()) : v;
        if (aim != null && phaseT > 4) d = turn(d, aim.subtract(position()), Math.toRadians(9));
        else d = d.add(0, -0.01, 0);
        if (!engine()) return d.normalize().scale(speed * 0.98).add(0, -0.02, 0);
        return d.normalize().scale(speed);
    }

    private Vec3 glide(Vec3 v, @Nullable Vec3 aim, double g) {
        v = v.add(0, -g, 0);
        double sp = v.length();
        v = v.scale(1 - 0.004);
        if (aim != null && phaseT > 6 && sp > 1e-3) {
            Vec3 want = aim.subtract(position());
            // glide: keep a shallower line until close, then point at the target; turn limited by available lift
            Vec3 nd = turn(v, want, Math.toRadians(2.5));
            v = nd.scale(sp);
        }
        return v;
    }

    private Vec3 cruise(ServerLevel sl, Vec3 v, @Nullable Vec3 aim, double g) {
        Kind k = kind();
        int st = state();
        double vCruise = bpt(k.speed);
        if (st == DROP) {
            v = v.add(0, -g, 0).scale(0.995);
            if (phaseT > 10) setF1(Math.min(1f, f1() + 0.05f));
            if (phaseT >= 34) {
                setAux(aux() | B_ENGINE);
                setState(CRUISE);
                phaseT = 0;
                sl.playSound(null, getX(), getY(), getZ(), ArsenalRegistry.SND_JET.get(), SoundSource.NEUTRAL, 3f, 1.4f);
            }
            speed = v.length();
            return v;
        }
        if (st == BOOST) {
            speed = Math.min(bpt(260), speed + bpt(260) / 35.0);
            Vec3 d = v.lengthSqr() < 1e-6 ? dir(getYRot(), pitch()) : v.normalize();
            if (phaseT > 20) {
                // pitch over toward the cruise heading while boosting
                Vec3 want = aim == null ? new Vec3(d.x, 0.1, d.z) : aim.subtract(position()).multiply(1, 0, 1).normalize().add(0, 0.12, 0);
                d = turn(d, want, Math.toRadians(1.6));
            }
            if (phaseT >= 70) {
                setAux((aux() & ~B_BOOSTER) | B_ENGINE);
                setState(CRUISE);
                phaseT = 0;
                sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            }
            return d.scale(speed);
        }
        // CRUISE / TERMINAL
        if (f1() < 1f) setF1(Math.min(1f, f1() + 0.05f));
        if (engine()) speed += (vCruise - speed) * 0.03;
        else speed *= 0.995;
        Vec3 d = v.lengthSqr() < 1e-6 ? dir(getYRot(), 0) : v.normalize();
        Vec3 dh = new Vec3(d.x, 0, d.z);
        if (dh.lengthSqr() < 1e-6) dh = dir(getYRot(), 0);
        dh = dh.normalize();
        double hd = aim == null ? 1e9 : Math.hypot(aim.x - getX(), aim.z - getZ());
        if (aim != null && (st == TERMINAL || hd < Math.max(70, speed * 16))) {
            if (st != TERMINAL) {
                setState(TERMINAL);
                phaseT = 0;
            }
            // pop-up for the first 10 ticks, then dive onto the aim point
            Vec3 want = phaseT < 10 && hd > 40 ? new Vec3(aim.x - getX(), hd * 0.5, aim.z - getZ()) : aim.subtract(position());
            d = turn(d, want, Math.toRadians(5));
        } else {
            Vec3 wantH = aim == null ? dh : new Vec3(aim.x - getX(), 0, aim.z - getZ()).normalize();
            Vec3 nh = turn(dh, wantH, Math.toRadians(2.2));
            double agl = k == Kind.TOMAHAWK || k == Kind.KALIBR ? 18 : 25;
            int gr = groundAhead(sl, position(), nh, Math.min(220, speed * 32), 7);
            double yDes = gr + agl;
            double vz = Mth.clamp((yDes - getY()) * 0.06, -0.35, 0.45);
            if (!engine()) vz = -0.3;
            d = turn(d, nh.add(0, vz, 0), Math.toRadians(3));
        }
        if (!engine()) v = d.scale(speed).add(0, -g * 0.5, 0);
        else v = d.scale(speed);
        return v;
    }

    public void detonate(ServerLevel sl, @Nullable Entity hit) {
        if (isRemoved()) return;
        Vec3 p = position();
        discard();
        Warheads.detonate(sl, p, kind(), this, ownerEntity(), hit);
    }

    @Override
    protected void onDestroyed(DamageSource src) {
        if (level() instanceof ServerLevel sl) {
            // shot down: the warhead usually goes off (low-order)
            Vec3 p = position();
            discard();
            ru.aiforge.tubombers.fx.ImpactHandler.crash(sl, p, 2.5f, this);
        }
    }

    @Override
    protected void readMore(CompoundTag t) {
        phaseT = t.getInt("PhaseT");
        speed = t.getDouble("Speed");
        fuel = t.getInt("Fuel");
        setF1(t.getFloat("Deploy"));
        if (t.hasUUID("TgtE")) targetEntity = t.getUUID("TgtE");
    }

    @Override
    protected void saveMore(CompoundTag t) {
        t.putInt("PhaseT", phaseT);
        t.putDouble("Speed", speed);
        t.putInt("Fuel", fuel);
        t.putFloat("Deploy", f1());
        if (targetEntity != null) t.putUUID("TgtE", targetEntity);
    }

    /** Exhaust smoke / launch blast (Real War particle system), client only. */
    @Override
    protected void clientTick() {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> ru.aiforge.arsenal.client.ArsTrails.missile(this));
    }
}
