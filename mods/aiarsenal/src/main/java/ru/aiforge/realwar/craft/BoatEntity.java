package ru.aiforge.realwar.craft;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Displacement / planing hull physics shared by the boats and the submarine.
 *
 * <p>The hull is split into stations along its length, each into a port and a starboard column. Every tick the
 * submerged depth of each column is measured against the real water surface (block fluid height + small waves),
 * giving Archimedes force rho*g*V at the column's centre of buoyancy. The sum of these forces and their torques about
 * the centre of mass drives heave, pitch and roll, so the boat floats at its design draft, trims, rolls in waves,
 * lists when it beaches and the submarine sinks or surfaces with its ballast.
 *
 * <p>Hydrodynamics: axial drag with a hump at displacement speed and a drop once planing, strong lateral (keel) drag,
 * heave damping, power-limited propeller/waterjet thrust (T = min(bollard, eta*P/v)), rudder authority growing with
 * speed (waterjets turn at rest), heel into turns, bow-up trim at hump speed.
 * Submarine: ballast tanks (Space blow / C flood), hydroplanes, depth hold, battery submerged / diesel surfaced.
 */
public class BoatEntity extends CraftEntity {
    /** lx, lz, bottomY, height, area per column. */
    private final double[][] cols;
    private final double kWaterplane;
    private double holdY = Double.NaN;
    private boolean wasHold;
    /** submarine automatic trim system (trim tanks), pitch torque N*m - local physics state */
    private double trimT;
    private double comZ;
    public float subFrac;
    public float rudderVis, rudderVisO;
    public float depth;
    private double surfaceY = Double.NaN;

    /** Top of the water column above (x, y, z) or NaN if the point is not in water. */
    public double surfaceAbove(double y, int max) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(this.getX()), bz = Mth.floor(this.getZ()), by = Mth.floor(y);
        double top = Double.NaN;
        for (int i = 0; i < max; i++) {
            mp.set(bx, by + i, bz);
            net.minecraft.world.level.material.FluidState fs = this.level().getFluidState(mp);
            if (!fs.is(net.minecraft.tags.FluidTags.WATER)) break;
            top = by + i + fs.getHeight(this.level(), mp);
        }
        return top;
    }

    public BoatEntity(EntityType<? extends BoatEntity> type, Level level, CraftKind kind) {
        super(type, level, kind);
        CraftKind.Water w = kind.water;
        int n = kind.isSub() ? 12 : 6;
        double dl = w.length() / n;
        double[][] c = new double[n * 2][];
        double sumDraft = 0, sumFull = 0, sumArea = 0, sumFullZ = 0;
        for (int i = 0; i < n; i++) {
            double z = -w.length() / 2 + (i + 0.5) * dl;
            double s = z / (w.length() / 2);
            double bottom, height, width;
            if (kind.isSub()) {
                double shape;
                if (s > 0.75) shape = Math.sqrt(Math.max(0.05, 1 - Math.pow((s - 0.75) / 0.27, 2)));
                else if (s < -0.55) shape = Math.max(0.12, 1 - Math.pow((-0.55 - s) / 0.47, 1.6));
                else shape = 1.0;
                double R = w.beam() / 2 * shape;
                bottom = w.axisY() - R;
                height = 2 * R;
                width = 2 * R * 0.785;
            } else {
                double bow = Math.max(0, s);
                bottom = w.bowRise() * bow * bow;
                height = w.hullH() - bottom * 0.3;
                width = w.beam() * (1 - 0.55 * bow * bow);
            }
            for (int side = 0; side < 2; side++) {
                double lx = (side == 0 ? 1 : -1) * width / 4;
                double area = width / 2 * dl;
                c[i * 2 + side] = new double[]{lx, z, bottom, height, area};
                sumDraft += area * Mth.clamp(w.draft() - bottom, 0, height);
                sumFull += area * height;
                sumFullZ += area * height * z;
                sumArea += area;
            }
        }
        // scale column areas so the hull floats at its real draft (boats) / is neutral with full ballast (submarine)
        double k = kind.isSub() ? (w.mass() + w.ballast()) / RHO_WATER / sumFull : w.mass() / RHO_WATER / sumDraft;
        for (double[] col : c) col[4] *= k;
        this.cols = c;
        // submarine: centre of gravity designed under the centre of buoyancy (short bow / long stern taper put the CB
        // ~2 m forward of mid-length; with only ~0.6 m BG that stood the boat nose-up at 30-40 deg when submerged)
        this.comZ = kind.isSub() ? sumFullZ / sumFull : 0;
        this.kWaterplane = RHO_WATER * G * sumArea * k;
    }

    @Override
    public float pivotY() {
        return this.kind.water.comY();
    }

    public float throttle() { return this.aux2; }
    public double trimTorque() { return this.trimT; }
    public float ballast() { return this.kind.isSub() ? this.aux : 0f; }
    public float battery() { return this.kind.isSub() ? this.aux3 : 1f; }
    public float output() { return this.kind.isSub() ? this.aux2 : this.aux; }

    private double mass() {
        CraftKind.Water w = this.kind.water;
        return w.mass() + (this.kind.isSub() ? w.ballast() * this.aux : 0);
    }

    @Override
    protected void physics(float[] in) {
        CraftKind.Water w = this.kind.water;
        boolean sub = this.kind.isSub();
        double m = this.mass();
        Vec3[] b = this.basis();
        Vec3 f = b[0], u = b[1], r = b[2];
        Vec3 v = this.vel;
        Vec3 com = this.localToWorld(0, w.comY(), this.comZ);

        // ---------------- buoyancy
        double fy = -m * G;
        Vec3 torque = Vec3.ZERO;
        double wetArea = 0, areaSum = 0;
        boolean sternWet = false;
        double surfAtSail = Double.NaN;
        for (int i = 0; i < this.cols.length; i++) {
            double[] c = this.cols[i];
            Vec3 p = this.localToWorld(c[0], c[2], c[1]);
            double surf = this.waterSurface(p.x, p.z, p.y, p.y + c[3]);
            double d = 0;
            if (!Double.isNaN(surf)) {
                if (surf > 1e8) d = c[3];
                else d = Mth.clamp(surf + (sub ? 0 : this.wave(p.x, p.z)) - p.y, 0, c[3]);
                if (surf < 1e8 && i >= this.cols.length / 2 - 2 && i < this.cols.length / 2 + 2) surfAtSail = surf;
            }
            double fb = RHO_WATER * G * c[4] * d;
            fy += fb;
            Vec3 arm = new Vec3(p.x - com.x, p.y + d / 2 - com.y, p.z - com.z);
            torque = torque.add(arm.cross(new Vec3(0, fb, 0)));
            // ground reaction where the hull column bottom is inside the sea bed / a beach: the collision box only
            // covers the hull centre, so without this the ends sank into the ground (lost buoyancy there) and a sub on
            // the bottom stood on its tail at ~40 deg
            BlockPos gp = BlockPos.containing(p);
            if (!this.level().getBlockState(gp).getCollisionShape(this.level(), gp).isEmpty()) {
                double pen = Mth.clamp(gp.getY() + 1.0 - p.y, 0.05, 1.5);
                double fs = m * G * 1.6 / this.cols.length * pen - m / this.cols.length * 4.0 * Math.min(0, v.y);
                fy += fs;
                Vec3 garm = new Vec3(p.x - com.x, p.y - com.y, p.z - com.z);
                torque = torque.add(garm.cross(new Vec3(0, fs, 0)));
            }
            wetArea += c[4] * d / c[3];
            areaSum += c[4];
            if (i < 2 && d > 0.15) sternWet = true;
        }
        // r18: flood water through hull holes - weight at the flooded compartments (list / trim towards the holes)
        double flood = this.dmg.flood;
        if (flood > 1.0) {
            Vec3 fp = this.localToWorld(this.dmg.floodX, w.comY() * 0.5, this.dmg.floodZ);
            fy -= flood * G;
            torque = torque.add(new Vec3(fp.x - com.x, fp.y - com.y, fp.z - com.z).cross(new Vec3(0, -flood * G, 0)));
        }
        double s = wetArea / areaSum;
        this.subFrac = (float) s;
        this.onWater = s > 0.02;
        double wf = Math.min(1.0, s * 3.0);
        boolean submerged = sub && s > 0.97;

        // ---------------- controls
        double lever = this.aux2;
        lever = Mth.clamp(lever + in[0] * 0.5 * DT, -0.5, 1.0);
        if (Math.abs(lever) < 0.04 && in[0] == 0) lever = 0;
        this.aux2 = (float) lever;
        boolean engine = this.engineOn() && this.fuel() > 0 && ru.aiforge.realwar.damage.CraftDamage.boatThrust(this) > 0;
        double out;
        if (sub) {
            out = lever;
        } else {
            double tgt = engine ? lever : 0;
            this.aux += (float) ((tgt - this.aux) * (1 - Math.exp(-DT / 1.2)));
            out = this.aux;
        }
        double vf = v.dot(f), vr = v.dot(r), vu = v.dot(u);

        // ---------------- submarine: ballast, battery, hydroplanes, depth hold
        double planes = 0;
        if (sub) {
            double bal = this.aux;
            boolean hold = this.flag(FLAG_STAB);
            if (hold && !this.wasHold) this.holdY = this.getY();
            this.wasHold = hold;
            if (hold) {
                if (in[4] > 0) this.holdY += 2.0 * DT;
                if (in[5] > 0) this.holdY -= 2.0 * DT;
                double err = this.holdY - this.getY();
                planes = Mth.clamp(err * 0.4 - v.y * 1.2 - this.pitch * 0.08, -1, 1);
                bal += Mth.clamp(-(err * 0.004 + v.y * 0.01), -0.02, 0.02) * DT * 20 * 0.05;
            } else {
                if (in[4] > 0) { bal -= 0.10 * DT; planes = 1; }
                if (in[5] > 0) { bal += 0.05 * DT; planes = -1; }
            }
            this.aux = (float) Mth.clamp(bal, 0.0, 1.03);
            // battery submerged, diesels at the surface / snorkel depth (sail top within 1 m of the surface)
            if (this.tickCount % 5 == 0 || Double.isNaN(this.surfaceY)) this.surfaceY = this.surfaceAbove(this.getY() + 1, 320);
            double sailTop = this.getY() + 13.4;
            boolean snorkel = !submerged || (!Double.isNaN(this.surfaceY) && sailTop > this.surfaceY - 1.0);
            double bat = this.aux3;
            if (engine && submerged && !snorkel) bat -= Math.abs(out) * Math.abs(out) * DT / (25 * 60.0) + DT / (3 * 3600.0);
            else if (engine && Math.abs(out) < 0.75) bat += DT / (12 * 60.0);
            this.aux3 = (float) Mth.clamp(bat, 0, 1);
            this.depth = (float) (Double.isNaN(this.surfaceY) ? 0 : Math.max(0, this.surfaceY - this.getY()));
        }

        // ---------------- hydrodynamic forces (body axes)
        double kAx;
        if (sub) {
            double kSub = 0.6 * w.power() / Math.pow(w.vSub(), 3);
            double full = Mth.clamp((s - 0.85) / 0.15, 0, 1);
            kAx = w.kPlane() + (kSub - w.kPlane()) * full;
        } else {
            double av = Math.abs(vf);
            kAx = w.kPlane() + w.kHump() * Math.exp(-Math.pow((av - w.vHump()) / 4.5, 2)) + (av < w.vHump() ? w.kHump() * 0.35 : 0);
        }
        double kLat = w.kLat();
        double kVert = 0.5 * RHO_WATER * w.length() * w.beam() * 0.6;
        double cHeave = 2 * 0.4 * Math.sqrt(this.kWaterplane * m);
        Vec3 F = new Vec3(0, fy, 0)
                .add(f.scale(-kAx * vf * Math.abs(vf) * wf))
                .add(r.scale(-(kLat * vr * Math.abs(vr) + kLat * 0.6 * vr) * wf))
                .add(u.scale(-kVert * vu * Math.abs(vu) * wf))
                .add(0, -cHeave * v.y * wf, 0);
        // thrust
        boolean propWet = sub ? s > 0.3 : sternWet;
        if (engine && propWet && Math.abs(out) > 0.001) {
            double P = w.power();
            if (sub) P = submerged ? (this.aux3 > 0 ? P : 0) : P * 0.46;
            double tMag = Math.min(w.thrust(), 0.55 * P / Math.max(2.0, Math.abs(vf))) * Math.abs(out) * (out < 0 ? 0.6 : 1.0) * ru.aiforge.realwar.damage.CraftDamage.boatThrust(this);
            Vec3 dir = sub ? f : new Vec3(f.x, 0, f.z).normalize();
            F = F.add(dir.scale(Math.signum(out) * tMag));
        }
        Vec3 acc = F.scale(1.0 / m);
        if (!this.onWater) acc = new Vec3(acc.x, acc.y, acc.z).subtract(v.scale(0.02));
        v = v.add(acc.scale(DT));

        // ---------------- rotation: buoyancy torques + control moments
        double L = w.length(), B = w.beam();
        double Ipitch = m * L * L / 12.0 * 0.8;
        double Iroll = m * (B * B + w.hullH() * w.hullH()) / 12.0;
        double steer = in[1];
        this.rudderVisO = this.rudderVis;
        this.rudderVis += (float) ((steer - this.rudderVis) * 0.25);
        if (!sub) this.aux3 = this.rudderVis;
        double tRoll = torque.dot(f), tPitch = torque.dot(r);
        double rT;
        if (w.jets()) {
            rT = this.rudderVis * w.turn() * (0.3 + 0.7 * Math.min(1, Math.abs(out) + Math.abs(vf) / 10)) * (vf < -0.5 ? -1 : 1);
        } else {
            rT = this.rudderVis * w.turn() * Mth.clamp(Math.abs(vf) / (sub ? w.vSub() : 10.0), 0, 1) * Math.signum(vf);
        }
        if (!this.onWater) rT = 0;
        double turnFrac = Mth.clamp(this.rateR / Math.max(1, w.turn()), -1, 1);
        // heel: planing boats bank into the turn, the submarine snaps outward a little
        tRoll += m * G * 0.05 * B * turnFrac * Math.min(1, Math.abs(vf) / 12.0) * (sub ? -0.4 : 1.0) * wf;
        if (!sub) {
            double av = Math.abs(vf);
            tPitch += m * G * L * 0.010 * Math.exp(-Math.pow((av - w.vHump() * 1.25) / 4.0, 2)) * wf;    // hump bow-up
            tPitch += m * G * L * 0.0025 * Mth.clamp((av - w.vHump()) / 10, 0, 1) * wf;                // planing trim
        } else {
            tPitch += planes * 62500.0 * vf * Math.abs(vf) * wf;
        }
        if (this.onWater) {
            // metacentric righting moment (GM: ~1.2 m patrol boats, ~0.6 m submarine BG) - the column model alone loses
            // its righting arm once one side lifts out, and the boats rolled over far too easily
            double gmT = sub ? 0.6 : 1.2, gmL = sub ? 0.6 : 0.12 * L;
            double rr = Math.toRadians(this.roll), pr = Math.toRadians(this.pitch);
            tRoll -= m * G * gmT * Math.sin(rr) * Math.max(wf, sub ? 1.0 : 0.0);
            tPitch -= m * G * gmL * Math.sin(pr) * (sub ? 1.0 : wf);
            // progressive stops: heel / trim beyond 35 / 25 degrees are fought hard (game: hulls never capsize)
            double ar = Math.abs(this.roll) - 35.0, ap = Math.abs(this.pitch) - (sub ? 30.0 : 25.0);
            if (ar > 0) tRoll -= Math.signum(this.roll) * m * G * B * 0.6 * Math.min(1.0, ar / 15.0);
            if (ap > 0) tPitch -= Math.signum(this.pitch) * m * G * L * 0.08 * Math.min(1.0, ap / 15.0);
            if (sub) {
                // trim tanks: submerged, the only pitch stiffness is the ~0.6 m BG, so a 0.1 m CB/CG offset alone gave a
                // 10 deg trim (38 deg standing on the bottom). Pumping slowly levels the boat; frozen while the
                // hydroplanes are deflected so planed dives / climbs still work.
                double lim = m * G * L * 0.012;
                if (Math.abs(planes) < 0.15) this.trimT = Mth.clamp(this.trimT - this.pitch * m * G * L * 1.5e-4 * DT, -lim, lim);
                tPitch += this.trimT;
            }
        }
        double dampR = this.onWater ? 1.1 + 1.5 * wf : 0.2, dampP = this.onWater ? 2.0 + 2.0 * wf : 0.2;
        this.rateP += (float) (Math.toDegrees(tRoll / Iroll) * DT);
        this.rateQ += (float) (Math.toDegrees(tPitch / Ipitch) * DT);
        this.rateP *= (float) Math.exp(-dampR * DT);
        this.rateQ *= (float) Math.exp(-dampP * DT);
        // yaw response: patrol boats answer the helm at once, a 50,000 t battleship needs ~10 s to settle into a turn
        double tauYaw = sub ? 3.0 : (L > 100 ? 10.0 : 0.6);
        this.rateR += (float) ((rT - this.rateR) * (1 - Math.exp(-DT / tauYaw)));
        this.integrateAttitude(DT);

        // ---------------- move, bow/stern terrain probes
        Vec3 step = v.scale(DT);
        double half = L / 2 - 0.5;
        for (int end = -1; end <= 1; end += 2) {
            double along = v.dot(f) * end;
            if (along <= 0.2) continue;
            Vec3 tip = this.localToWorld(0, w.comY() + 0.5, end * half).add(step);
            BlockPos bp = BlockPos.containing(tip);
            if (!this.level().getBlockState(bp).getCollisionShape(this.level(), bp).isEmpty()) {
                if (along > 3) this.impact = Math.max(this.impact, (float) (along * 0.9));
                v = v.subtract(f.scale(end * along * 1.15));
                step = v.scale(DT);
            }
        }
        double vyBefore = v.y;
        this.move(MoverType.SELF, step);
        this.contact = this.onGround();
        if (this.verticalCollision) {
            if (vyBefore < -6) this.impact = Math.max(this.impact, (float) -vyBefore);
            v = new Vec3(v.x * 0.9, 0, v.z * 0.9);
        }
        if (this.horizontalCollision) {
            double hs = Math.sqrt(v.x * v.x + v.z * v.z);
            if (hs > 4) this.impact = Math.max(this.impact, (float) hs);
            v = new Vec3(v.x * -0.2, v.y, v.z * -0.2);
        }
        if (this.contact && !this.onWater) v = new Vec3(v.x * 0.7, v.y, v.z * 0.7);
        this.vel = v;
    }

    @Override
    protected void animate() {
        float out = this.kind.isSub() ? this.aux2 : this.aux;
        this.rotorAngle = (this.rotorAngle + out * (this.kind.isSub() ? 12f : 45f)) % 360f;
        if (this.level().isClientSide && !this.isLocalPilot()) {
            this.rudderVisO = this.rudderVis;
            if (!this.kind.isSub()) this.rudderVis = this.aux3;
        }
    }

    @Override
    protected float fuelBurnPerTick() {
        // boats: ~2.5 h at full power (game x2), the submarine's diesel endurance is effectively unlimited here
        if (this.kind.isSub()) return 1f / (20f * 3600f * 6f);
        return (float) ((0.25 + 0.75 * Math.abs(this.syncedAux())) / (20.0 * 3600.0 * 1.2));
    }

    public void setThrottle(float t) {
        this.aux2 = t;
    }

    public void setBallast(float b) {
        if (this.kind.isSub()) this.aux = b;
    }
}
