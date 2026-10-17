package ru.aiforge.realwar.craft;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.aircraft.AirBombEntity;
import ru.aiforge.realwar.aircraft.AirBombType;
import ru.aiforge.realwar.ordnance.AirTargets;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.BulletManager;
import ru.aiforge.realwar.ordnance.ShellEntity;
import ru.aiforge.realwar.registry.ModSounds;

/**
 * WW2 piston aircraft (reply 11): Bf 109 G-6, Yak-3, He 111 H-6, Pe-8.
 *
 * <p>Flight model in real units (m, kg, s), moved over the world at the Tu bombers' SPEED_SCALE:
 * <ul>
 *   <li>Lift from the angle of attack with a real stall (CL drops past the stall AoA, wing drop, nose drop),
 *       flaps add CLmax and drag; zero-lift drag derived from the sea-level top speed at full power;
 *       induced drag CL^2 / (pi e AR); side force from the fuselage and fin.</li>
 *   <li>Propeller thrust T = min(static, 0.8 P / V), engine spool, air density with height.</li>
 *   <li>Mouse = stick as a rate command limited by dynamic pressure (sluggish when slow) and by the structural
 *       G limit; A/D = rudder / tail wheel; W/S throttle. Hands off, the aircraft holds a 1 g flight path.</li>
 *   <li>Tail-dragger ground handling: sits on the tail wheel, lifts the tail with speed, wheel brakes (S at idle),
 *       side grip, prop strike when the nose digs in or on a belly landing.</li>
 *   <li>Bombers: defensive turrets aim and fire on their own at hostile aircraft (other teams' pilots, flying mobs),
 *       bomb-bay doors open when a bomb station is selected, R on the ground changes the bomb load.</li>
 * </ul>
 */
public class PlaneEntity extends CraftEntity {
    private static final EntityDataAccessor<CompoundTag> TUR = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.COMPOUND_TAG);
    public static final float RANGE = 220f, FIRE_RANGE = 170f;

    public float gearPos = 1f, gearPosO = 1f, flapPos, flapPosO, bayPos, bayPosO;
    public float elevVis, elevVisO, ailVis, ailVisO, rudVis, rudVisO;
    /** HUD telemetry (pilot side): angle of attack and sideslip, deg; stall speed m/s. */
    public float aoa, slip, vStall;
    public final float[] turAz, turEl, turAzO, turElO;
    private final int[] turTarget, turCd;
    private int gunnerSpent, preset;
    /** r12 jets: afterburner fraction (client: from the synced throttle) and the current thrust. */
    public float ab, thrustN, abVis, abVisO;
    public static final double JET_AB = 1.67, AB_THR = 0.86;
    private final double cd0, kInd, slope0;

    public PlaneEntity(EntityType<? extends PlaneEntity> type, Level level, CraftKind kind) {
        super(type, level, kind);
        CraftKind.Plane p = kind.plane;
        int n = p.turretCount();
        this.turAz = new float[n];
        this.turEl = new float[n];
        this.turAzO = new float[n];
        this.turElO = new float[n];
        this.turTarget = new int[n];
        this.turCd = new int[n];
        for (int i = 0; i < n; i++) {
            this.turAz[i] = this.turAzO[i] = p.turrets()[i][3];
            this.turTarget[i] = -1;
        }
        double ar = p.span() * p.span() / p.area();
        this.kInd = 1.0 / (Math.PI * 0.78 * ar);
        double q = 0.5 * RHO_AIR * p.vmax() * p.vmax();
        double clc = (p.mass() + 0.6 * p.fuel()) * G / (q * p.area());
        double tMax = p.jet() ? JET_AB * p.power() : 0.8 * p.power() / p.vmax();
        this.cd0 = Math.max(0.014, tMax / (q * p.area()) - this.kInd * clc * clc);
        this.slope0 = 2 * Math.PI * ar / (ar + 2) * Math.PI / 180.0;
        this.noCulling = false;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TUR, new CompoundTag());
    }

    /** World distance per real metre (shared with the Tu bombers' config). */
    public static double scaleCfg() {
        try {
            return ru.aiforge.tubombers.Config.SPEED_SCALE.get();
        } catch (Throwable t) {
            return 0.5;
        }
    }

    @Override
    protected double worldScale() {
        return scaleCfg();
    }

    @Override
    public float pivotY() {
        return this.kind == CraftKind.PE8 ? 3.0f : this.kind == CraftKind.HE111 ? 2.4f : this.kind == CraftKind.SU57 ? 1.9f : 1.6f;
    }

    public CraftKind.Plane spec() {
        return this.kind.plane;
    }

    public float throttle() { return this.aux2; }
    public float rpm() { return this.aux; }

    /** Wheels on the ground (local physics contact, or a short probe for other clients). */
    public boolean contactOrGround() {
        return this.contact || this.heightAbove(this.position().add(0, 0.3, 0), 3) < 0.6;
    }

    public boolean gearDown() { return this.flag(FLAG_GEAR); }
    public boolean flapsDown() { return this.flag(FLAG_STAB); }

    static AirBombType bombOf(CraftWeapon w) {
        return switch (w) {
            case SC250 -> AirBombType.SC250;
            case SC1000 -> AirBombType.SC1000;
            case FAB250W -> AirBombType.FAB250SV;
            case FAB1000W -> AirBombType.FAB1000;
            case FAB5000W -> AirBombType.FAB5000;
            case FAB500S -> AirBombType.FAB500;
            default -> null;
        };
    }

    public double mass() {
        CraftKind.Plane p = this.kind.plane;
        double m = p.mass() + p.fuel() * this.fuel();
        for (int i = 0; i < this.kind.loads.length; i++) {
            AirBombType b = bombOf(this.kind.loads[i].weapon());
            if (b != null) m += b.massKg * this.ammo(i);
        }
        return m;
    }

    /** True when the selected station is an internal bomb (bay doors must be open). */
    public boolean bayWanted() {
        int li = this.weaponFor(0);
        if (li < 0) return false;
        CraftWeapon w = this.kind.loads[li].weapon();
        return w.type == CraftWeapon.Type.BOMB && w != CraftWeapon.SC1000;
    }

    public boolean airborne() {
        return this.heightAbove(this.position().add(0, 0.3, 0), 6) > 2.0;
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Mth.clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    /** Lift coefficient vs angle of attack (deg): linear to the stall, then a sharp break and a flat-plate tail. */
    double cl(double a, double fl) {
        CraftKind.Plane p = this.kind.plane;
        double clMax = p.clMax() + 0.45 * fl;
        double cl0 = 0.22 + 0.35 * fl;
        double as = p.stallDeg() - 1.5 * fl;
        double slope = Math.min(this.slope0, (clMax - cl0) / as);
        double asN = -0.8 * as;
        double flat = 0.9 * Math.sin(Math.toRadians(2 * a));
        if (a >= asN && a <= as) return cl0 + slope * a;
        if (a > as) return Math.max(clMax * (0.72 - 0.012 * (a - as)), flat);
        double neg = cl0 + slope * asN;
        return Math.min(neg * (0.72 - 0.012 * (asN - a)), flat);
    }

    // ------------------------------------------------------------------------------------------ flight model

    @Override
    protected void physics(float[] in) {
        CraftKind.Plane p = this.kind.plane;
        double S = this.worldScale();
        double m = this.mass();
        double W = m * G;
        boolean engine = this.engineOn() && this.fuel() > 0 && ru.aiforge.realwar.damage.CraftDamage.engineWorks(this);
        // throttle lever and engine output (spool)
        double thr = Mth.clamp(this.aux2 + in[0] * 0.5 * DT, 0.0, 1.0);
        this.aux2 = (float) thr;
        boolean jet = p.jet();
        boolean mnv = jet && this.flapsDown();           // jets: N = super-manoeuvre mode (TVC, no AoA limiter)
        double tgt = engine ? (jet ? 0.12 + 0.88 * Math.min(1.0, thr / AB_THR) : 0.18 + 0.82 * thr) : 0.0;
        this.aux += (float) ((tgt - this.aux) * (1.0 - Math.exp(-DT / (engine ? (jet ? 2.2 : 1.1) : 3.5))));
        double abT = engine && jet && thr > AB_THR && this.aux > 0.9 ? 1.0 : 0.0;
        this.ab += (float) ((abT - this.ab) * (1.0 - Math.exp(-DT / 0.6)));

        Vec3[] b = this.basis();
        Vec3 f = b[0], u = b[1], r = b[2];
        Vec3 v = this.vel;
        double V = v.length();
        double vf = v.dot(f), vu = v.dot(u), vr = v.dot(r);
        double alpha = V > 1.0 ? Math.toDegrees(Math.atan2(-vu, Math.max(vf, 0.5))) : 0.0;
        double beta = V > 1.0 ? Math.toDegrees(Math.atan2(vr, Math.max(Math.abs(vf), 1.0))) : 0.0;
        this.aoa = (float) alpha;
        this.slip = (float) beta;
        double rho = Math.exp(-Math.max(0, this.getY() - 63) / 8500.0) * Mth.clamp(1.0 - (this.getY() - 300.0) / 160.0, 0.15, 1.0);
        double q = 0.5 * RHO_AIR * rho * V * V;
        double fl = jet ? 0.0 : this.flapPos, gr = this.gearPos;
        double as = p.stallDeg() - 1.5 * fl;
        this.vStall = (float) Math.sqrt(2 * W / (RHO_AIR * rho * p.area() * (p.clMax() + 0.45 * fl)));
        double CL = this.cl(alpha, fl);
        double over = Math.max(0, Math.abs(alpha) - as);
        double CD = this.cd0 + p.cd0Flaps() * fl + 0.016 * gr + 0.006 * this.bayPos + this.kInd * CL * CL
                + 1.1 * Math.pow(Math.sin(Math.toRadians(Math.min(over, 90))), 2);
        double st = smooth(as - 3.0, as + 1.0, alpha);
        this.warnStall = (float) st;
        Vec3 vh = V > 0.5 ? v.scale(1.0 / V) : f;
        Vec3 ld = u.subtract(vh.scale(u.dot(vh)));
        ld = ld.lengthSqr() > 1e-6 ? ld.normalize() : u;
        // ground effect: up to +12 % lift within half a span of the ground
        double ge = 1.0 + 0.12 * Mth.clamp(1.0 - this.agl / (0.5 * p.span()), 0.0, 1.0);
        Vec3 aero = ld.scale(q * p.area() * CL * ge * ru.aiforge.realwar.damage.CraftDamage.liftFactor(this)).subtract(vh.scale(q * p.area() * CD));
        aero = aero.add(r.scale(-0.5 * RHO_AIR * rho * V * vr * p.area() * 0.10));
        double T = !engine ? 0.0 : jet
                ? p.power() * this.aux * (1.0 + (JET_AB - 1.0) * this.ab) * Math.pow(rho, 0.75) * (1.0 + 0.12 * Math.min(1.0, V / 300.0))
                : this.aux * Math.min(0.0105 * p.power(), 0.8 * p.power() / Math.max(V, 1.0)) * Math.pow(rho, 0.7);
        T *= ru.aiforge.realwar.damage.CraftDamage.thrustFactor(this);
        this.thrustN = (float) T;
        aero = aero.add(f.scale(T));
        this.gForce = (float) (aero.dot(u) / W);
        Vec3 acc = aero.scale(1.0 / m).add(0, -G, 0);
        boolean brake = in[0] < -0.5f && thr < 0.02;
        if (this.contact) {
            double N = Math.max(0.0, W - aero.y);
            double mu = this.gearDown() ? (brake ? 0.32 : 0.025) : 0.45;
            Vec3 hv = new Vec3(v.x, 0, v.z);
            double hs = hv.length();
            if (hs > 0.01) acc = acc.subtract(hv.scale(Math.min(mu * N / m, hs / DT) / hs));
        }
        boolean wet = this.level().getFluidState(BlockPos.containing(this.getX(), this.getY() + 0.5, this.getZ())).is(FluidTags.WATER);
        if (wet) acc = acc.add(0, G * 1.15, 0).subtract(v.scale(1.5));
        v = v.add(acc.scale(DT));

        // --- attitude: stick rate commands, authority from dynamic pressure
        double qRef = 0.5 * RHO_AIR * Math.pow(0.5 * p.vmax(), 2);
        double eff = Mth.clamp(q / qRef, 0.0, 1.0);
        double wash = this.contact ? 0.25 * this.aux : 0.0;          // prop wash over the tail on the ground
        double mx = in[2], my = in[3];
        if (jet) {
            // fly-by-wire jet: full authority from 1/3 of the reference pressure; thrust vectoring keeps the nose alive
            // at any speed in super-manoeuvre mode (the TVC nozzles deflect +-15 deg with the engines' thrust)
            eff = Mth.clamp(q / (0.33 * qRef), 0.0, 1.0);
            double tvc = engine ? Mth.clamp(this.aux * 1.1, 0.0, 1.0) : 0.0;
            wash = Math.max(wash, (mnv ? 0.9 : 0.25) * tvc);
        }
        double pitchMax = p.pitchRate() * (mnv ? 2.2 : 1.0);
        double pT = Mth.clamp(mx * (jet ? 12.0 : 7.0), -p.rollRate(), p.rollRate()) * (jet ? Math.max(eff, 0.5 * wash) : eff);
        double qT = Mth.clamp(my * (mnv ? 9.0 : 5.0), -pitchMax, pitchMax) * Math.max(eff, wash);
        double rT = in[1] * p.yawRate() * (mnv ? 2.5 : 1.0) * Math.max(eff, wash);
        if (jet && !mnv && !this.contact) {
            // FBW angle-of-attack limiter (24 deg); the manoeuvre mode switches it off
            if (alpha > 20 && qT > 0) qT *= Math.max(0.0, 1.0 - (alpha - 20) / 4.0);
            if (alpha < -10 && qT < 0) qT *= Math.max(0.0, 1.0 - (-10 - alpha) / 4.0);
        }
        double gMax = p.maxG() * (mnv ? 1.25 : 1.0);
        if (this.gForce > gMax && qT > 0) qT *= Math.max(0.0, 1.0 - (this.gForce - gMax) / 1.5);
        if (this.gForce < -0.45 * p.maxG() && qT < 0) qT *= Math.max(0.0, 1.0 - (-0.45 * p.maxG() - this.gForce) / 1.0);
        if (!this.contact && V > 3) {
            rT += beta * 1.6 * Math.max(eff, 0.2);                      // directional stability
            if (Math.abs(my) < 0.05 && st < 0.3 && V > 0.8 * this.vStall) {
                // trimmed: hands off the aircraft keeps a 1 g flight path
                double cosB = Math.max(0.2, Math.cos(Math.toRadians(this.roll)));
                double want = W * cosB / Math.max(q * p.area(), 1.0);
                double aw = Mth.clamp((want - (0.22 + 0.35 * fl)) / Math.max(1e-3, (p.clMax() + 0.45 * fl - 0.22 - 0.35 * fl) / as), -6.0, as - 2.5);
                qT += (aw - alpha) * 1.1 * eff;
            }
            if (alpha > as && !jet) {
                java.util.Random rnd = new java.util.Random(this.tickCount * 31L + this.getId());
                qT -= (alpha - as) * 2.5;                                 // nose drops
                pT += rnd.nextGaussian() * 28 * st + (this.getId() % 2 == 0 ? 18 : -18) * st;   // wing drop
            }
        }
        if (this.contact) {
            pT = -this.roll * 5.0;
            double tail = p.tailDeg() - this.pitch;
            qT = qT + tail * 2.5 * (1.0 - eff);
            rT = in[1] * (24.0 * (1.0 - smooth(5, 35, V)) + p.yawRate() * eff);
        }
        if (this.dmg.any() && !this.contact) {
            // r18: wing shot off -> violent roll to that side; tail damage -> little pitch / yaw authority + flutter
            pT = ru.aiforge.realwar.damage.CraftDamage.planeRoll(this, pT, eff);
            qT *= ru.aiforge.realwar.damage.CraftDamage.pitchAuth(this);
            rT *= ru.aiforge.realwar.damage.CraftDamage.yawAuth(this);
            if (this.dmg.broken(ru.aiforge.realwar.damage.DamageState.TAIL)) {
                java.util.Random fr = new java.util.Random(this.tickCount * 17L + this.getId());
                qT += fr.nextGaussian() * 9.0 - 4.0;
                rT += fr.nextGaussian() * 7.0;
            }
        }
        // r12 jets: light FBW response, heavier (more inertia) when the nose is thrown around in manoeuvre mode
        double tau = jet ? (mnv ? 0.30 : 0.16) : p.engines() >= 2 ? 0.32 : 0.13;
        double k = 1.0 - Math.exp(-DT / tau);
        this.rateP += (float) ((pT - this.rateP) * k);
        this.rateQ += (float) ((qT - this.rateQ) * k);
        this.rateR += (float) ((rT - this.rateR) * k);
        this.integrateAttitude(DT);
        if (this.contact) {
            float rotMax = jet ? 14f : p.tailDeg();          // tricycle jets rotate on the main wheels
            if (this.pitch > rotMax) {
                this.pitch = rotMax;
                this.rateQ = Math.min(this.rateQ, 0f);
            }
            if (this.pitch < -5f) this.pitch = -5f;
        }

        // --- move & collide
        double vyB = v.y, hB = Math.sqrt(v.x * v.x + v.z * v.z);
        this.move(MoverType.SELF, v.scale(DT * S));
        this.contact = this.onGround();
        if (this.verticalCollision) {
            if (vyB < 0) {
                float land = (float) -vyB;
                if (!this.gearDown()) land += 2.5f + (float) hB * 0.04f;
                if (Math.abs(this.roll) > 12) land += 3f;
                if (this.pitch < -6) land += 4f;
                this.impact = Math.max(this.impact, land);
            }
            v = new Vec3(v.x, 0, v.z);
        }
        if (this.horizontalCollision) {
            if (hB > 10) this.impact = Math.max(this.impact, (float) (hB * 0.9));
            v = new Vec3(v.x * -0.1, v.y, v.z * -0.1);
        }
        if (this.contact && this.gearDown()) {
            Vec3 rh = new Vec3(r.x, 0, r.z);
            if (rh.lengthSqr() > 1e-6) {
                rh = rh.normalize();
                v = v.subtract(rh.scale(v.dot(rh) * 0.5));                  // tyre side grip
            }
        }
        this.vel = v;
        this.agl = (float) this.heightAbove(this.position().add(0, 0.2, 0), 300);
    }

    @Override
    protected void animate() {
        this.rotorAngle = (this.rotorAngle + this.aux * 95f) % 360f;
        this.gearPosO = this.gearPos;
        this.flapPosO = this.flapPos;
        this.bayPosO = this.bayPos;
        float gs = this.kind.plane.engines() >= 2 ? 0.012f : 0.022f;
        this.gearPos += Mth.clamp((this.gearDown() ? 1f : 0f) - this.gearPos, -gs, gs);
        this.flapPos += Mth.clamp((this.flapsDown() ? 1f : 0f) - this.flapPos, -0.025f, 0.025f);
        this.abVisO = this.abVis;
        if (this.kind.plane.jet()) {
            float abt = this.engineOn() && this.syncedAux2() > AB_THR && this.aux > 0.85f ? 1f : 0f;
            this.abVis += (abt - this.abVis) * 0.12f;
        }
        this.bayPos += Mth.clamp((this.bayWanted() ? 1f : 0f) - this.bayPos, -0.03f, 0.03f);
        CraftKind.Plane p = this.kind.plane;
        this.elevVisO = this.elevVis;
        this.ailVisO = this.ailVis;
        this.rudVisO = this.rudVis;
        this.elevVis += (Mth.clamp(this.rateQ / p.pitchRate(), -1f, 1f) - this.elevVis) * 0.4f;
        this.ailVis += (Mth.clamp(this.rateP / p.rollRate(), -1f, 1f) - this.ailVis) * 0.4f;
        this.rudVis += (Mth.clamp(this.rateR / p.yawRate(), -1f, 1f) - this.rudVis) * 0.4f;
        int n = p.turretCount();
        if (this.level().isClientSide && n > 0) {
            int[] a = this.entityData.get(TUR).getIntArray("t");
            for (int i = 0; i < n; i++) {
                this.turAzO[i] = this.turAz[i];
                this.turElO[i] = this.turEl[i];
                if (a.length == n) {
                    float ta = (short) (a[i] >> 16) / 10f, te = (short) (a[i] & 0xFFFF) / 10f;
                    this.turAz[i] += (ta - this.turAz[i]) * 0.5f;
                    this.turEl[i] += (te - this.turEl[i]) * 0.5f;
                }
            }
        }
    }

    @Override
    protected float fuelBurnPerTick() {
        CraftKind.Plane p = this.kind.plane;
        double kgPerTick = p.burn() / 3600.0 / 20.0 * (0.3 + 0.7 * this.syncedAux2()) * 2.0
                * (p.jet() && this.syncedAux2() > AB_THR ? 3.0 : 1.0);
        return (float) (kgPerTick / p.fuel());
    }

    @Override
    protected void crashDamage(float dv) {
        float safe = 5.5f;
        if (dv <= safe) return;
        float dmg = (dv - safe) * (dv - safe) * (this.kind.plane.engines() >= 2 ? 6f : 2.4f);
        this.damage(dmg);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), this.getX(), this.getY() + 1, this.getZ(), 14, 1.5, 0.5, 1.5, 0.02);
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, Math.min(3f, dv / 6f), 1.2f);
        }
        for (Entity e : this.getPassengers()) e.hurt(this.level().damageSources().fall(), Math.max(0, dv - 9f) * 1.6f);
    }

    @Override
    protected double cookOffKg() {
        double kg = this.kind.plane.fuel() * this.fuel() * 0.02 + 10;
        for (int i = 0; i < this.kind.loads.length; i++) {
            AirBombType b = bombOf(this.kind.loads[i].weapon());
            if (b != null) kg += b.tntKg * this.ammo(i) * 0.5;
        }
        return Math.min(kg, 1500);
    }

    // ------------------------------------------------------------------------------------------ loads

    /** Bomb-load presets per bomber (counts per bomb station, in load order). */
    private int[][] presets() {
        return switch (this.kind) {
            case HE111 -> new int[][]{{8, 0}, {0, 2}, {4, 1}};
            case PE8 -> new int[][]{{16, 0, 0}, {0, 4, 0}, {0, 0, 1}};
            // Su-57: counts for EVERY station (-1 = full): gun, S-8, Kh-38, FAB-500, flares
            case SU57 -> new int[][]{{-1, 40, 2, 2, -1}, {-1, 0, 2, 6, -1}, {-1, 0, 6, 0, -1}, {-1, 80, 0, 4, -1}};
            default -> new int[0][];
        };
    }

    public int presetIndex() { return this.preset; }

    private void applyPreset(int idx) {
        int[][] ps = this.presets();
        if (ps.length == 0) return;
        this.preset = Math.floorMod(idx, ps.length);
        int[] a = this.ammoArray();
        if (this.kind == CraftKind.SU57) {
            for (int i = 0; i < a.length && i < ps[this.preset].length; i++)
                a[i] = ps[this.preset][i] < 0 ? this.kind.loads[i].full() : ps[this.preset][i];
            this.setAmmoArray(a);
            return;
        }
        int k = 0;
        for (int i = 0; i < a.length; i++) {
            if (this.kind.loads[i].weapon().type != CraftWeapon.Type.BOMB) continue;
            a[i] = k < ps[this.preset].length ? ps[this.preset][k] : 0;
            k++;
        }
        this.setAmmoArray(a);
    }

    @Override
    public void initFresh() {
        super.initFresh();
        this.applyPreset(0);
        this.pitch = this.pitchO = this.kind.plane.tailDeg();
    }

    @Override
    public void service(float repair) {
        super.service(repair);
        this.applyPreset(this.preset);
    }

    @Override
    public boolean canUse(int seat, int load) {
        CraftWeapon w = this.kind.loads[load].weapon();
        if (w == CraftWeapon.GUNNERS) return false;
        return super.canUse(seat, load);
    }

    @Override
    public void handleControl(ServerPlayer p, boolean fire, int actions) {
        int seat = this.seatOf(p);
        boolean ground = !this.airborne();
        if ((actions & CraftControlPacket.A_GEAR) != 0 && seat == 0 && ground && this.gearDown()) {
            p.displayClientMessage(Component.translatable("craft.aiarsenal.gear_ground"), true);
            actions &= ~CraftControlPacket.A_GEAR;
        }
        if ((actions & CraftControlPacket.A_LOADOUT) != 0 && seat == 0) {
            if (this.presets().length == 0) {
                // fighters: nothing to change
            } else if (!ground || this.vel.length() > 1.0) {
                p.displayClientMessage(Component.translatable("craft.aiarsenal.load_ground"), true);
            } else {
                this.applyPreset(this.preset + 1);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 1.5f, 0.8f);
                p.displayClientMessage(Component.translatable("craft.aiarsenal.load_" + this.kind.id + "_" + this.preset), true);
            }
        }
        super.handleControl(p, fire, actions);
        if ((actions & CraftControlPacket.A_GEAR) != 0 && seat == 0)
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.HYDRAULIC.get(), SoundSource.NEUTRAL, 1.2f, 1.1f);
    }

    private static Vec3 jit(Vec3 d, double s, net.minecraft.util.RandomSource r) {
        return d.add(r.nextGaussian() * s, r.nextGaussian() * s, r.nextGaussian() * s).normalize();
    }

    /** Fixed guns: all barrels of a station fire together (r11 salvo rule); bombs ripple from the last cell. */
    @Override
    protected void fire(int li, @Nullable Entity shooter) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        CraftKind.Load l = this.kind.loads[li];
        CraftWeapon w = l.weapon();
        Vec3 inherit = this.vel.scale(DT * this.worldScale());
        Vec3 f = this.basis()[0];
        float[][] mz = l.muzzles();
        switch (w.type) {
            case CANNON -> {
                Ballistics.Profile prof = w == CraftWeapon.GSH301 ? Ballistics.Profile.A42_HE
                        : w == CraftWeapon.SHVAK ? Ballistics.Profile.SHVAK_HE
                        : w == CraftWeapon.MGFF ? Ballistics.Profile.MGFF_HE : Ballistics.Profile.MG151_HE;
                for (float[] m0 : mz) {
                    Vec3 m = this.localToWorld(m0[0], m0[1], m0[2]).add(inherit);
                    ShellEntity.fire(sl, prof, m, jit(f, 0.0022, sl.random), this.getId());
                }
                this.sound(ModSounds.CRAFT_CANNON.get(), 4.0f, w == CraftWeapon.GSH301 ? 0.95f : w == CraftWeapon.MGFF ? 1.25f : 1.4f);
            }
            case MG -> {
                int[] a = this.ammoArray();
                for (int k = 0; k < mz.length; k++) {
                    if (k > 0) {
                        if (a[li] <= 0) break;
                        a[li]--;
                    }
                    Vec3 m = this.localToWorld(mz[k][0], mz[k][1], mz[k][2]);
                    BulletManager.spawn(sl, m, jit(f, 0.0028, sl.random).scale(w == CraftWeapon.MG131 ? 29.0 : 30.0).add(inherit), this.getId(),
                            (this.tickCount + k) % 3 == 0);
                }
                if (mz.length > 1) this.setAmmoArray(a);
                this.sound(ModSounds.CRAFT_MG.get(), 3.0f, w == CraftWeapon.MG131 ? 1.15f : 1.2f);
            }
            case BOMB -> {
                if (w != CraftWeapon.SC1000 && this.bayPos < 0.85f) {
                    // doors still opening: give the round back and wait
                    int[] a = this.ammoArray();
                    a[li]++;
                    this.setAmmoArray(a);
                    this.cooldown[li] = 2;
                    return;
                }
                int idx = Mth.clamp(this.ammo(li), 0, mz.length - 1);
                Vec3 m = this.localToWorld(mz[idx][0], mz[idx][1] - 0.4, mz[idx][2]);
                AirBombType bt = bombOf(w);
                AirBombEntity bomb = AirBombEntity.drop(sl, m, inherit.add(0, -0.05, 0), bt);
                bomb.armAt = 12;
                sl.addFreshEntity(bomb);
                sl.playSound(null, m.x, m.y, m.z, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.NEUTRAL, 1.5f, 0.6f);
            }
            case ROCKET -> {
                // Su-57: S-8 from the pods that are hung (20 rockets per B-8M1 pod), alternating left / right
                int pods = Mth.clamp((this.ammo(li) + 20) / 20, 1, mz.length);
                int k = this.tickCount % pods;
                Vec3 m = this.localToWorld(mz[k][0], mz[k][1], mz[k][2] + 0.4).add(inherit);
                CraftOrdnance.launch(sl, CraftOrdnance.Type.S8, this, m, jit(f, 0.005, sl.random), inherit, -1);
                this.sound(ModSounds.ROCKET_LAUNCH_SMALL.get(), 3.0f, 1.25f);
                sl.broadcastEntityEvent(this, (byte) (160 + li * 8 + Math.min(k, 7)));
            }
            case ATGM_BEAM -> {
                // Kh-38 leaves the last loaded pylon and rides the pilot's line of sight
                int idx = Mth.clamp(this.ammo(li), 0, mz.length - 1);
                Vec3 m = this.localToWorld(mz[idx][0], mz[idx][1] - 0.3, mz[idx][2]);
                Entity op = this.guidanceOperator();
                CraftOrdnance.launch(sl, CraftOrdnance.Type.VIKHR, this, m, f.add(0, -0.01, 0).normalize(), inherit, op != null ? op.getId() : -1);
                this.sound(ModSounds.SAM_LAUNCH.get(), 3.0f, 1.2f);
            }
            default -> super.fire(li, shooter);
        }
    }

    // ------------------------------------------------------------------------------------------ server

    @Override
    protected void serverTick() {
        super.serverTick();
        if (!(this.level() instanceof ServerLevel sl)) return;
        boolean ground = !this.airborne();
        // prop strike: nose down on the ground or a belly landing with the engine turning
        if (ground && !this.kind.plane.jet() && this.aux > 0.3f && (this.pitch < -3.5f || !this.gearDown() && this.heightAbove(this.position().add(0, 0.3, 0), 3) < 0.8)) {
            if (this.engineOn()) {
                this.setEngine(false);
                this.damage(this.kind.plane.engines() >= 2 ? 40f : 20f);
                sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 2.5f, 0.5f);
                for (Entity e : this.getPassengers())
                    if (e instanceof Player pl) pl.displayClientMessage(Component.translatable("craft.aiarsenal.prop_strike"), true);
            }
        }
        if (this.level().getFluidState(BlockPos.containing(this.localToWorld(0, 1.0, 1.5))).is(FluidTags.WATER)) {
            if (this.engineOn()) this.setEngine(false);
            if (this.tickCount % 10 == 0) this.damage(3f);
        }
        if (this.engineOn() && this.hp() < this.kind.hp * 0.25f && this.random.nextFloat() < 0.003f) {
            this.setEngine(false);
            for (Entity e : this.getPassengers())
                if (e instanceof Player pl) pl.displayClientMessage(Component.translatable("craft.aiarsenal.flameout"), true);
        }
        this.gunners(sl);
    }

    private boolean hostile(Entity e, @Nullable Player pil) {
        if (e == this || e.getRootVehicle() == this || !e.isAlive()) return false;
        if (e instanceof Enemy && (e instanceof FlyingMob || e instanceof Blaze) && !e.onGround()) return true;
        if (e instanceof ShellEntity || e instanceof ru.aiforge.realwar.ordnance.RocketEntity || e instanceof CraftOrdnance) return false;
        if (!AirTargets.isAirTarget(e)) return false;
        Entity driver = e instanceof Player ? e : e.getFirstPassenger();
        if (e instanceof CraftEntity c) driver = c.passengerIn(0);
        if (driver == null) return false;                       // unmanned / parked
        if (pil != null && (driver == pil || driver.isAlliedTo(pil))) return false;
        return true;
    }

    private void gunners(ServerLevel sl) {
        CraftKind.Plane p = this.kind.plane;
        int n = p.turretCount();
        if (n == 0) return;
        int gl = this.kind.loadIndex(CraftWeapon.GUNNERS);
        Player pil = this.pilot();
        boolean crew = pil != null && gl >= 0;
        Vec3[] bs = this.basis();
        Vec3 f = bs[0], u = bs[1], r = bs[2];
        if (crew && this.tickCount % 10 == 0) {
            List<Entity> list = sl.getEntities(this, this.getBoundingBox().inflate(RANGE), e -> this.hostile(e, pil));
            for (int i = 0; i < n; i++) {
                float[] t = p.turrets()[i];
                Vec3 piv = this.localToWorld(t[0], t[1], t[2]);
                Entity best = null;
                double bd = RANGE * RANGE;
                for (Entity e : list) {
                    Vec3 d = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(piv);
                    double dd = d.lengthSqr();
                    if (dd >= bd) continue;
                    float[] ae = this.localAngles(d.normalize(), f, u, r);
                    if (!inArc(t, ae[0], ae[1])) continue;
                    best = e;
                    bd = dd;
                }
                this.turTarget[i] = best == null ? -1 : best.getId();
            }
        }
        int[] ammo = gl >= 0 ? this.ammoArray() : null;
        int left = ammo != null ? ammo[gl] - this.gunnerSpent : 0;
        Vec3 own = this.vel.scale(DT * this.worldScale());
        boolean fired = false;
        for (int i = 0; i < n; i++) {
            float[] t = p.turrets()[i];
            if (this.turCd[i] > 0) this.turCd[i]--;
            Entity e = crew && this.turTarget[i] >= 0 ? sl.getEntity(this.turTarget[i]) : null;
            float wantAz = t[3], wantEl = 0f;
            Vec3 piv = this.localToWorld(t[0], t[1], t[2]);
            boolean onTarget = false;
            double dist = 0;
            if (e != null && e.isAlive()) {
                Vec3 aim = e.position().add(0, e.getBbHeight() * 0.5, 0);
                Vec3 tv = e.getDeltaMovement().subtract(own);
                double spd = t[8] >= 2 ? 8.0 : 26.0;     // shells (scaled ballistics) vs bullets, blocks/tick
                Vec3 lead = aim;
                for (int it = 0; it < 2; it++) {
                    double tt = lead.distanceTo(piv) / spd;
                    lead = aim.add(tv.scale(tt)).add(0, t[8] >= 2 ? 0.0 : 0.01 * tt * tt, 0);
                }
                Vec3 d = lead.subtract(piv);
                dist = d.length();
                float[] ae = this.localAngles(d.normalize(), f, u, r);
                if (inArc(t, ae[0], ae[1])) {
                    wantAz = ae[0];
                    wantEl = ae[1];
                    onTarget = true;
                }
            }
            float ca = this.turAz[i];
            float da = Mth.wrapDegrees(wantAz - ca);
            this.turAz[i] = ca + Mth.clamp(da, -4f, 4f);
            this.turEl[i] += Mth.clamp(wantEl - this.turEl[i], -3f, 3f);
            if (onTarget && Math.abs(da) < 2.5f && Math.abs(wantEl - this.turEl[i]) < 2.5f && dist < FIRE_RANGE && this.turCd[i] <= 0 && left > 0) {
                Vec3 d = this.dirFromAngles(this.turAz[i], this.turEl[i]);
                Vec3 mz = piv.add(d.scale(t[9]));
                int cal = (int) t[8];
                if (cal >= 2) {
                    ShellEntity.fire(sl, Ballistics.Profile.SHVAK_HE, mz.add(own), jit(d, 0.004, sl.random), this.getId());
                    this.turCd[i] = 3;
                    sl.playSound(null, mz.x, mz.y, mz.z, ModSounds.CRAFT_CANNON.get(), SoundSource.NEUTRAL, 3.0f, 1.45f);
                } else {
                    BulletManager.spawn(sl, mz, jit(d, cal == 1 ? 0.005 : 0.007, sl.random).scale(26.0).add(own), this.getId(), (this.tickCount + i) % 3 == 0);
                    this.turCd[i] = cal == 1 ? 2 : 1;
                    if (this.tickCount % 2 == 0) sl.playSound(null, mz.x, mz.y, mz.z, ModSounds.CRAFT_MG.get(), SoundSource.NEUTRAL, 2.5f, cal == 1 ? 1.1f : 1.35f);
                }
                left--;
                this.gunnerSpent++;
                fired = true;
            }
        }
        if (ammo != null && this.gunnerSpent > 0 && (this.tickCount % 10 == 0 || left <= 0)) {
            ammo[gl] = Math.max(0, ammo[gl] - this.gunnerSpent);
            this.gunnerSpent = 0;
            this.setAmmoArray(ammo);
        }
        if (this.tickCount % 2 == 0 || fired) {
            int[] a = new int[n];
            for (int i = 0; i < n; i++)
                a[i] = (((short) Math.round(Mth.wrapDegrees(this.turAz[i]) * 10)) << 16) | (((short) Math.round(this.turEl[i] * 10)) & 0xFFFF);
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("t", a);
            if (!java.util.Arrays.equals(a, this.entityData.get(TUR).getIntArray("t"))) this.entityData.set(TUR, tag);
        }
    }

    /** World direction to turret angles {az (+ left), el}. */
    private float[] localAngles(Vec3 d, Vec3 f, Vec3 u, Vec3 r) {
        double lf = d.dot(f), ll = -d.dot(r), lu = d.dot(u);
        return new float[]{(float) Math.toDegrees(Math.atan2(ll, lf)), (float) Math.toDegrees(Math.atan2(lu, Math.sqrt(lf * lf + ll * ll)))};
    }

    static boolean inArc(float[] t, float az, float el) {
        float c = (t[4] + t[5]) * 0.5f, half = (t[5] - t[4]) * 0.5f;
        if (half < 179f && Math.abs(Mth.wrapDegrees(az - c)) > half) return false;
        return el >= t[6] && el <= t[7];
    }

    public int turretTarget(int i) {
        return i >= 0 && i < this.turTarget.length ? this.turTarget[i] : -1;
    }

    // ------------------------------------------------------------------------------------------ save

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.preset = tag.getInt("preset");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("preset", this.preset);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        double r = Math.max(300.0, this.kind.plane.span() * 12.0);
        return d < r * r;
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        double h = this.kind.plane.span() * 0.5 + 1;
        return new net.minecraft.world.phys.AABB(this.getX() - h, this.getY() - 2, this.getZ() - h, this.getX() + h, this.getY() + h, this.getZ() + h);
    }

    /** Test hook: put the aircraft into level flight at the given true airspeed. */
    public void setFlying(double speed) {
        this.setEngine(true);
        this.aux = this.aux2 = 0.9f;
        this.setFlag(FLAG_GEAR, false);
        this.gearPos = 0f;
        this.pitch = 2f;
        this.roll = 0f;
        this.vel = this.basis()[0].scale(speed);
    }
}
