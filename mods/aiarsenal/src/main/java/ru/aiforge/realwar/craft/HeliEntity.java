package ru.aiforge.realwar.craft;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Attack helicopter flight model (Ka-52 coaxial / AH-64 single rotor + tail rotor).
 *
 * <ul>
 *   <li>Rotor thrust along the body up axis: T = collective * Tmax * Omega^2 * ground effect * ETL * inflow (climb)
 *       * vortex-ring loss * retreating-blade-stall loss * air density.</li>
 *   <li>Quadratic drag per body axis (front / side / vertical drag areas).</li>
 *   <li>Mouse = cyclic as a rate command (roll/pitch), attitude is held when the mouse stops; A/D pedals; W/S collective.</li>
 *   <li>Weathervane into the relative wind at speed, main-rotor torque reaction (AH-64), engine spool-up, autorotation
 *       with engine off (rotor RPM driven by the upflow), vortex ring state, blade stall near Vne.</li>
 *   <li>Stabilisation ("hover") mode: roll locked level, mouse X yaws, mouse Y sets a pitch attitude,
 *       W/S command vertical speed, altitude hold on release, lateral drift damped.</li>
 *   <li>Crash loads: hard landing, rotor strike (leaves are chopped), dynamic rollover, horizontal collision.</li>
 * </ul>
 */
public class HeliEntity extends CraftEntity {
    private double holdAlt = Double.NaN;
    private float pitchCmd;
    private boolean wasStab;
    private int rolloverTicks;
    private int strikeCooldown;
    public float gearPos = 1f, gearPosO = 1f;

    public HeliEntity(EntityType<? extends HeliEntity> type, Level level, CraftKind kind) {
        super(type, level, kind);
    }

    @Override
    public float pivotY() {
        return 1.6f;
    }

    public float collective() { return this.aux2; }
    public float rotor() { return this.aux; }

    private double mass() {
        CraftKind.Heli h = this.kind.heli;
        return h.mass() - h.fuel() * (1.0 - this.fuel()) * 0.9;
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Mth.clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    @Override
    protected void physics(float[] in) {
        CraftKind.Heli h = this.kind.heli;
        double m = this.mass();
        double W = m * G;
        boolean stab = this.flag(FLAG_STAB);
        boolean engine = this.engineOn() && this.fuel() > 0 && ru.aiforge.realwar.damage.CraftDamage.engineWorks(this);
        Vec3[] b = this.basis();
        Vec3 f = b[0], u = b[1], r = b[2];
        Vec3 v = this.vel;
        double vh = Math.sqrt(v.x * v.x + v.z * v.z);
        double vf = v.dot(f), vr = v.dot(r), vu = v.dot(u);

        Vec3 hub = this.localToWorld(0, h.hubY(), 0);
        double hr = this.heightAbove(hub, 2 * h.rotorR() + 2);
        this.agl = (float) this.heightAbove(this.position().add(0, 0.2, 0), 250) ;

        // --- rotor speed: governor with engine; torque balance (autorotation) without
        double c = this.aux2;
        double load = (0.35 * c + 0.05) * this.aux * this.aux;
        double drive = 0.0145 * Math.max(0, -vu);
        if (engine) {
            double gov = Mth.clamp((1.0 - this.aux) * 2.0, -0.5, 1.0 / h.spool());
            if (this.aux < 0.25) gov = 0.6 / h.spool();
            this.aux += (float) ((gov + drive * 0.3) * DT);
            if (c > 0.97) this.aux -= (float) (0.02 * DT);     // power-limited droop at full collective
        } else {
            this.aux += (float) ((drive - load - 0.004) * DT);
        }
        this.aux = Mth.clamp(this.aux, 0f, 1.08f);
        double om2 = this.aux * this.aux;

        // --- thrust factors
        double Tmax = h.thrustRatio() * h.mass() * G;
        double ge = 1.0 + 0.15 * Mth.clamp(1.0 - hr / (2.0 * h.rotorR()), 0.0, 1.0);
        double etl = 1.0 + 0.12 * smooth(8, 20, vh);
        double climb = Mth.clamp(1.0 - vu / 35.0, 0.55, 1.45);
        double vd = -vu;
        double vrs = Mth.clamp((vd - 5.0) / 5.0, 0, 1) * Mth.clamp((13.0 - vh) / 6.0, 0, 1) * (c > 0.35 && this.aux > 0.6 ? 1 : 0);
        this.warnVrs = (float) vrs;
        double st = Mth.clamp((vh - 0.92 * h.vne()) / (0.18 * h.vne()), 0, 1);
        this.warnStall = (float) st;
        double rho = Math.exp(-Math.max(0, this.getY() - 63) / 8500.0);
        // game ceiling: Minecraft's sky is only ~320 blocks tall, so the thin-air band is compressed to y 256..420
        // (rotor thrust fades out there - hover ceiling ~y 330-380 depending on weight)
        rho *= Mth.clamp(1.0 - (this.getY() - 256.0) / 164.0, 0.0, 1.0);
        double tUnit = Tmax * om2 * ge * etl * climb * (1 - 0.45 * vrs) * (1 - 0.3 * st) * rho;
        tUnit *= ru.aiforge.realwar.damage.CraftDamage.rotorLift(this);              // r18: blades shot off -> no lift

        // --- collective
        if (stab && !this.wasStab) {
            this.holdAlt = this.getY();
            this.pitchCmd = this.pitch;
        }
        this.wasStab = stab;
        if (!stab) {
            c += in[0] * 0.6 * DT;
        } else {
            double vsCmd;
            if (Math.abs(in[0]) > 0.01) {
                vsCmd = in[0] * 8.0;
                this.holdAlt = this.getY();
            } else {
                if (Double.isNaN(this.holdAlt)) this.holdAlt = this.getY();
                vsCmd = Mth.clamp((this.holdAlt - this.getY()) * 0.8, -4.0, 4.0);
            }
            double need = m * (G + 1.6 * (vsCmd - v.y));
            double want = need / Math.max(tUnit * Math.max(0.3, u.y), 1.0);
            if (this.contact && in[0] <= 0.01) {
                want = 0.0;
                this.holdAlt = this.getY();
            }
            c += (Mth.clamp(want, 0, 1) - c) * 0.35;
        }
        c = Mth.clamp(c, 0.0, 1.0);
        this.aux2 = (float) c;
        double T = c * tUnit;

        // --- forces
        double q = 0.5 * RHO_AIR * rho;
        Vec3 drag = f.scale(-q * h.cdaF() * vf * Math.abs(vf))
                .add(r.scale(-q * h.cdaS() * vr * Math.abs(vr)))
                .add(u.scale(-q * h.cdaT() * vu * Math.abs(vu)));
        Vec3 aero = u.scale(T).add(drag);
        if (stab) aero = aero.add(r.scale(-vr * m * 0.6));       // SAS: kills lateral drift without banking
        this.gForce = (float) (aero.dot(u) / W);
        Vec3 acc = aero.scale(1.0 / m).add(0, -G, 0);
        // water: fuselage floats briefly, heavy drag
        boolean wet = this.level().getFluidState(BlockPos.containing(this.getX(), this.getY() + 0.6, this.getZ())).is(FluidTags.WATER);
        if (wet) acc = acc.add(0, G * 1.1, 0).subtract(v.scale(1.8));
        v = v.add(acc.scale(DT));

        // --- attitude (body rates, deg/s)
        double mx = in[2], my = in[3];
        double pT, qT, rT;
        double auth = Mth.clamp(om2 * 1.2, 0.0, 1.0);
        if (!stab) {
            pT = Mth.clamp(mx * 8.0, -h.rollRate(), h.rollRate());
            qT = Mth.clamp(my * 6.0, -h.pitchRate(), h.pitchRate());
            rT = in[1] * h.yawRate() * (1.0 - 0.6 * smooth(15, 50, vh));
            if (!h.coaxial()) rT += (c - 0.62) * 25.0 * this.aux;          // main rotor torque reaction (no SAS)
        } else {
            rT = Mth.clamp(mx * 7.0, -h.yawRate(), h.yawRate()) + in[1] * h.yawRate() * 0.6;
            this.pitchCmd = (float) Mth.clamp(this.pitchCmd + my * 0.6, -25.0, 20.0);
            pT = Mth.clamp(-this.roll * 3.0, -h.rollRate(), h.rollRate());
            qT = Mth.clamp((this.pitchCmd - this.pitch) * 3.0, -h.pitchRate(), h.pitchRate());
        }
        if (vh > 8) {
            double beta = Math.toDegrees(Math.atan2(vr, Math.max(1.0, Math.abs(vf))));
            rT += beta * 1.2 * Math.min(1.0, vh / 40.0);                     // weathervane / directional stability
        }
        rT = ru.aiforge.realwar.damage.CraftDamage.heliYaw(this, rT, om2);           // r18: tail rotor lost -> torque spin
        java.util.Random rnd = this.random();
        if (this.dmg.broken(ru.aiforge.realwar.damage.DamageState.ROTOR)) {
            pT += rnd.nextGaussian() * 40;
            qT += rnd.nextGaussian() * 30;
        }
        if (vrs > 0) {
            pT += rnd.nextGaussian() * vrs * 25;
            qT += rnd.nextGaussian() * vrs * 15;
        }
        if (st > 0) {
            qT += 30 * st + rnd.nextGaussian() * 10 * st;
            if (!h.coaxial()) pT -= 35 * st;                               // retreating blade side drops
        }
        pT *= auth;
        qT *= auth;
        rT *= Math.max(auth, this.contact ? 0.0 : 0.25);
        boolean grounded = this.contact && T < 0.92 * W;
        if (grounded) {
            pT = -this.roll * 4.0;
            qT = (1.2 - this.pitch) * 4.0;
            rT = in[1] * 15.0 * auth;
        }
        double k = 1.0 - Math.exp(-DT / 0.15);
        this.rateP += (float) ((pT - this.rateP) * k);
        this.rateQ += (float) ((qT - this.rateQ) * k);
        this.rateR += (float) ((rT - this.rateR) * k);
        this.integrateAttitude(DT);

        // --- move & collide
        double vyBefore = v.y, hBefore = Math.sqrt(v.x * v.x + v.z * v.z);
        this.move(MoverType.SELF, v.scale(DT));
        this.contact = this.onGround();
        if (this.verticalCollision) {
            if (vyBefore < 0) {
                float land = (float) -vyBefore;
                if (!this.flag(FLAG_GEAR) && this.kind == CraftKind.KA52) land += 3f;   // belly landing
                if (Math.abs(this.roll) > 15 || this.pitch < -12) land += 3f;
                this.impact = Math.max(this.impact, land);
            }
            v = new Vec3(v.x, 0, v.z);
        }
        if (this.horizontalCollision) {
            if (hBefore > 8) this.impact = Math.max(this.impact, (float) (hBefore * 0.8));
            v = new Vec3(v.x * -0.1, v.y, v.z * -0.1);
        }
        if (this.contact) {
            double fr = c < 0.15 ? 0.85 : 0.97;
            v = new Vec3(v.x * fr, v.y, v.z * fr);
            if (Math.abs(this.roll) > 30 && this.aux > 0.4) {
                if (++this.rolloverTicks > 8) this.impact = Math.max(this.impact, 16f);
            } else {
                this.rolloverTicks = 0;
            }
        } else {
            this.rolloverTicks = 0;
        }
        this.vel = v;
    }

    private java.util.Random random() {
        return new java.util.Random(this.tickCount * 31L + this.getId());
    }

    @Override
    protected void animate() {
        this.rotorAngle = (this.rotorAngle + this.aux * 72f) % 360f;
        this.gearPosO = this.gearPos;
        float tgt = this.kind == CraftKind.AH64 || this.flag(FLAG_GEAR) ? 1f : 0f;
        this.gearPos += Mth.clamp(tgt - this.gearPos, -0.04f, 0.04f);
    }

    @Override
    protected float fuelBurnPerTick() {
        CraftKind.Heli h = this.kind.heli;
        double kgPerTick = h.burn() / 3600.0 / 20.0 * (0.45 + 0.55 * this.syncedAux2()) * 2.0;   // x2 game time factor
        return (float) (kgPerTick / h.fuel());
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        if (!(this.level() instanceof ServerLevel sl)) return;
        CraftKind.Heli h = this.kind.heli;
        float om = this.aux;
        // hub in the water: engines flood out
        Vec3 hub = this.localToWorld(0, h.hubY(), 0);
        if (this.level().getFluidState(BlockPos.containing(hub)).is(FluidTags.WATER)) {
            if (this.engineOn()) this.setEngine(false);
            if (this.tickCount % 10 == 0) this.damage(4f);
        }
        // heavy damage: an engine may flame out (the pilot can try to relight with H, or autorotate)
        if (this.engineOn() && this.hp() < this.kind.hp * 0.3f && this.random.nextFloat() < 0.004f) {
            this.setEngine(false);
            for (Entity pass : this.getPassengers())
                if (pass instanceof Player pl) pl.displayClientMessage(Component.translatable("craft.aiarsenal.flameout"), true);
        }
        if (this.strikeCooldown > 0) this.strikeCooldown--;
        if (om > 0.3f) {
            double R = h.rotorR() * 0.95;
            double[] hubs = h.coaxial() ? new double[]{h.hubY(), h.hub2Y()} : new double[]{h.hubY()};
            for (double hy : hubs) {
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0 + 22.5);
                    for (double rr : new double[]{R, R * 0.6}) {
                        Vec3 p = this.localToWorld(Math.sin(a) * rr, hy, Math.cos(a) * rr);
                        this.bladeHit(sl, BlockPos.containing(p), om);
                    }
                }
            }
            if (!h.coaxial() && h.tailR() > 0) {
                Vec3 tp = this.localToWorld(0.45, 3.1, h.tailZ());
                this.bladeHit(sl, BlockPos.containing(tp), om);
            }
        }
    }

    private void bladeHit(ServerLevel sl, BlockPos bp, float om) {
        BlockState bs = sl.getBlockState(bp);
        if (bs.isAir()) return;
        if (bs.is(BlockTags.LEAVES) || bs.canBeReplaced() || bs.is(BlockTags.FLOWERS) || bs.is(BlockTags.SAPLINGS)) {
            if (bs.getDestroySpeed(sl, bp) >= 0) sl.destroyBlock(bp, false, this);
            return;
        }
        if (bs.getCollisionShape(sl, bp).isEmpty() || this.strikeCooldown > 0) return;
        this.strikeCooldown = 10;
        sl.playSound(null, bp, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 2.5f, 0.6f);
        sl.levelEvent(2001, bp, net.minecraft.world.level.block.Block.getId(bs));
        this.damage(55f * om);
    }

    /** Test hook: put the helicopter into a trimmed state. */
    public void setRotorState(float rotor, float collective) {
        this.aux = rotor;
        this.aux2 = collective;
    }
}
