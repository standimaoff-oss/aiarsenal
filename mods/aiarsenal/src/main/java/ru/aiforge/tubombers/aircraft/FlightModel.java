package ru.aiforge.tubombers.aircraft;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Six-degree-of-freedom point-mass flight model with rate-command / attitude-hold control laws
 * (the Tu-160 is fly-by-wire; the Tu-95MS autopilot provides the same hands-off stability).
 * <p>
 * Forces are computed in real SI units: lift, induced + parasitic + wave drag, side force, thrust
 * (turboprop power curve or turbofan with afterburner), gravity, tyre friction and brakes.
 * Translation is applied to the world multiplied by {@code speedScale} so a 800 km/h bomber does not outrun
 * chunk loading; aircraft geometry stays 1:1.
 */
public final class FlightModel {
    public static final double G = 9.81;
    public static final double SEA_LEVEL = 63;

    private FlightModel() {
    }

    /** Terrain queries in world (block) coordinates. */
    public interface Env {
        /** Top of the highest collidable block at (x,z) not above y + 1, or -1e9. */
        double ground(double x, double y, double z);

        boolean solid(double x, double y, double z);
    }

    public static final class Inputs {
        public float pitch, roll, yaw, throttle;
        public boolean brake;

        public void set(Inputs o) {
            pitch = o.pitch;
            roll = o.roll;
            yaw = o.yaw;
            throttle = o.throttle;
            brake = o.brake;
        }
    }

    /** Mechanisation positions 0..1. */
    public static final class Config {
        public float gearUp, flaps, bay, sweep, extraMass;
        public boolean engines = true;
    }

    public static final class State {
        public double x, y, z;
        public final Vector3d vel = new Vector3d();     // m/s, world axes
        public final Quaternionf q = new Quaternionf();
        public float rollRate, pitchRate, yawRate;       // rad/s body
        public float thrust;                             // actual throttle position after spool lag
        public boolean onGround = true;
        // telemetry
        public float alpha, beta, mach, ias, gLoad = 1, agl, steer;
        public boolean stall, crashed, hardLanding, tailStrike;
        public String crashReason = "";
        public float impactSpeed;

        public void copyFrom(State o) {
            x = o.x;
            y = o.y;
            z = o.z;
            vel.set(o.vel);
            q.set(o.q);
            rollRate = o.rollRate;
            pitchRate = o.pitchRate;
            yawRate = o.yawRate;
            thrust = o.thrust;
            onGround = o.onGround;
        }

        public float yawDeg() {
            Vector3f f = q.transform(new Vector3f(0, 0, 1));
            return (float) Math.toDegrees(Math.atan2(-f.x, f.z));
        }

        public float pitchDeg() {
            Vector3f f = q.transform(new Vector3f(0, 0, 1));
            return (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, f.y))));
        }

        public float rollDeg() {
            Vector3f r = q.transform(new Vector3f(-1, 0, 0));
            Vector3f u = q.transform(new Vector3f(0, 1, 0));
            return (float) Math.toDegrees(Math.atan2(-r.y, u.y));
        }

        public void setYPR(float yawDeg, float pitchDeg, float rollDeg) {
            q.identity().rotateY((float) Math.toRadians(-yawDeg)).rotateX((float) Math.toRadians(-pitchDeg))
                    .rotateZ((float) Math.toRadians(rollDeg));
        }

        /** model space (m) to world (blocks) */
        public Vector3d toWorld(Vector3f local, Vector3d out) {
            Vector3f w = q.transform(new Vector3f(local));
            return out.set(x + w.x, y + w.y, z + w.z);
        }
    }

    public static double density(double y, double speedScale) {
        double alt = Math.max(0, (y - SEA_LEVEL) / Math.max(0.05, speedScale));
        return 1.225 * Math.exp(-alt / 8500.0);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    /**
     * Advances the state by dt seconds.
     *
     * @param hull model-space hull probes resolved to the current animation pose (may be null)
     */
    public static void step(State s, AircraftType t, AircraftData d, Inputs in, Config c, Env env, double dt, double speedScale,
                            Vector3f[] hull) {
        s.hardLanding = false;
        s.tailStrike = false;
        Vector3f f = s.q.transform(new Vector3f(0, 0, 1));
        Vector3f u = s.q.transform(new Vector3f(0, 1, 0));
        Vector3f r = s.q.transform(new Vector3f(-1, 0, 0));
        Vector3d v = s.vel;
        double V = v.length();
        double rho = density(s.y, speedScale);
        double qd = 0.5 * rho * V * V;
        float sw = t.swingWing ? c.sweep : 0f;
        float flapEff = c.flaps * (1 - sw);
        float S = t.swingWing ? lerp(t.wingArea, t.wingArea * 0.83f, sw) : t.wingArea;
        float AR = lerp(t.aspectSpread, t.aspectSwept, sw);
        float cla = lerp(t.clAlphaSpread, t.clAlphaSwept, sw);
        float clMax = lerp(t.clMaxSpread, t.clMaxSwept, sw) + flapEff * t.clFlaps;
        float alphaStall = (float) Math.toRadians(t.stallAlphaDeg + 2 * flapEff);
        double vf = v.x * f.x + v.y * f.y + v.z * f.z, vu = v.x * u.x + v.y * u.y + v.z * u.z, vr = v.x * r.x + v.y * r.y + v.z * r.z;
        float alpha = V > 2 ? (float) Math.atan2(-vu, vf) : 0f;
        float beta = V > 2 ? (float) Math.atan2(vr, Math.abs(vf)) : 0f;
        float cl = t.cl0 + flapEff * t.clFlaps * 0.55f + cla * alpha;
        boolean stall = Math.abs(alpha) > alphaStall;
        if (stall) cl = Math.signum(alpha) * Math.max(0.45f * clMax, clMax - (Math.abs(alpha) - alphaStall) * 3f);
        cl = clamp(cl, -clMax * 0.8f, clMax);
        float mach = (float) (V / 340.0);
        float mcrit = lerp(t.mCritSpread, t.mCritSwept, sw);
        float wave = 0;
        if (mach > mcrit) {
            float k = Math.min(1f, (mach - mcrit) / 0.25f);
            wave = k * k * lerp(0.050f, 0.024f, sw);
            if (mach > 1.3f) wave *= Math.max(0.6f, 1f - (mach - 1.3f) * 0.5f);
        }
        float gearOut = 1 - c.gearUp;
        float cd = t.cd0 + wave + (float) (cl * cl / (Math.PI * t.oswald * AR)) + gearOut * t.cdGear + flapEff * t.cdFlaps + c.bay * t.cdBay;
        double mass = t.mass + c.extraMass;
        // thrust with spool lag
        float cmd = c.engines ? clamp(in.throttle, 0, t.maxThrottle()) : 0;
        s.thrust += (cmd - s.thrust) * Math.min(1f, (float) dt / t.spoolTime);
        double sigma = rho / 1.225;
        double T;
        if (t.turboprop()) {
            double eta = 0.82 * (mach < 0.62 ? 1 : Math.max(0.25, 1 - (mach - 0.62) * 2.6));
            double tmax = Math.min(t.thrustStatic, t.power * eta * Math.pow(sigma, 0.8) / Math.max(V, 1));
            T = tmax * Math.max(0.04, s.thrust);
            if (!c.engines) T = -0.02 * qd * S; // feathering failure: windmilling drag
        } else {
            double dry = t.thrustStatic * Math.min(1, s.thrust) * (1 + 0.25 * mach) * Math.pow(sigma, 0.7);
            double ab = s.thrust > 1 ? (t.thrustAfterburner - t.thrustStatic) * (s.thrust - 1) / 0.25 * (1 + 0.4 * mach) * Math.pow(sigma, 0.7) : 0;
            T = Math.max(0.03 * t.thrustStatic * (c.engines ? 1 : 0), dry + ab);
        }
        // aerodynamic forces
        Vector3d F = new Vector3d(0, -mass * G, 0);
        double L = qd * S * cl;
        if (V > 1) {
            Vector3d vh = new Vector3d(v).div(V);
            Vector3d liftDir = new Vector3d(r.x, r.y, r.z).cross(vh);
            if (liftDir.lengthSquared() > 1e-8) F.fma(L, liftDir.normalize());
            F.fma(-qd * S * cd, vh);
            Vector3d side = new Vector3d(r.x, r.y, r.z).fma(-(r.x * vh.x + r.y * vh.y + r.z * vh.z), vh);
            if (side.lengthSquared() > 1e-8) F.fma(-qd * S * 0.9 * beta, side.normalize());
        }
        F.add(f.x * T, f.y * T, f.z * T);
        // ground reaction / tyre forces (from previous contact state)
        float steer = 0;
        if (s.onGround) {
            double N = Math.max(0, mass * G - L * u.y);
            Vector3d fwd = new Vector3d(f.x, 0, f.z);
            if (fwd.lengthSquared() > 1e-6) fwd.normalize();
            double vAlong = v.x * fwd.x + v.z * fwd.z;
            double mu = (gearOut > 0.9f ? 0.022 : 0.45) + (in.brake && gearOut > 0.9f ? 0.32 : 0);
            double fr = Math.min(mu * N, Math.abs(vAlong) * mass / dt);
            F.fma(-Math.signum(vAlong) * fr, fwd);
            // tyres kill lateral sliding
            Vector3d lat = new Vector3d(v.x - fwd.x * vAlong, 0, v.z - fwd.z * vAlong);
            F.fma(-Math.min(1.0, 0.6) * mass / dt * 0.5, lat);
            if (F.y < 0 && N > 0) F.y = Math.min(0, F.y + N); // the ground carries the weight
            steer = clamp(in.yaw, -1, 1) * (float) Math.toRadians(Math.max(4, 55 - V * 1.6));
        }
        v.fma(dt / mass, F);
        // ---------------------------------------------------------- rotation (control laws)
        float auth = clamp((float) (qd / (0.5 * 1.225 * 75 * 75)), 0.06f, 1f);
        float maxP = (float) Math.toRadians(t.maxPitchRate), maxR = (float) Math.toRadians(t.maxRollRate), maxY = (float) Math.toRadians(t.maxYawRate);
        float gamma = V > 1 ? (float) Math.asin(clamp((float) (v.y / V), -1, 1)) : 0;
        float cosBank = clamp(u.y / (float) Math.sqrt(Math.max(1e-4, 1 - f.y * f.y)), -1, 1);
        float gLoad = (float) (L / (mass * G));
        float pitchCmd, rollCmd, yawCmd;
        if (s.onGround) {
            pitchCmd = in.pitch * maxP * auth * 1.4f;
            if (in.pitch <= 0) pitchCmd -= 0.25f; // nose settles on the nose gear
            rollCmd = 0;
            yawCmd = (float) (V * Math.tan(steer) / Math.max(5, d.wheelBase)) + in.yaw * maxY * auth;
        } else {
            float clNeeded = (float) (mass * G * Math.cos(gamma) / Math.max(0.35, cosBank) / Math.max(1, qd * S));
            float aNeed = (clNeeded - t.cl0 - flapEff * t.clFlaps * 0.55f) / cla;
            aNeed = clamp(aNeed, -0.08f, alphaStall - (float) Math.toRadians(1.5));
            float hold = clamp((aNeed - alpha) * 1.6f, -maxP, maxP);
            float ip = clamp(in.pitch, -1, 1);
            pitchCmd = ip * maxP * auth + (1 - Math.abs(ip)) * hold;
            if (gLoad > t.maxG && pitchCmd > 0) pitchCmd = -0.02f;
            if (gLoad < -1f && pitchCmd < 0) pitchCmd = 0.02f;
            if (alpha > alphaStall && pitchCmd > 0) pitchCmd = -0.05f; // stall protection / natural nose drop
            rollCmd = clamp(in.roll, -1, 1) * maxR * auth;
            yawCmd = clamp(in.yaw, -1, 1) * maxY * auth + beta * 1.8f;
        }
        float lag = Math.min(1f, (float) dt / 0.45f);
        s.pitchRate += (pitchCmd - s.pitchRate) * lag;
        s.rollRate += (rollCmd - s.rollRate) * Math.min(1f, (float) dt / 0.6f);
        s.yawRate += (yawCmd - s.yawRate) * lag;
        s.q.rotateX((float) (-s.pitchRate * dt)).rotateY((float) (-s.yawRate * dt)).rotateZ((float) (s.rollRate * dt)).normalize();
        // ---------------------------------------------------------- integrate position
        double k = dt * speedScale;
        s.x += v.x * k;
        s.y += v.y * k;
        s.z += v.z * k;
        // ---------------------------------------------------------- ground contact
        boolean wasGround = s.onGround;
        s.onGround = false;
        Vector3d ml = s.toWorld(d.mainGearL, new Vector3d()), mr = s.toWorld(d.mainGearR, new Vector3d()), nz = s.toWorld(d.noseGear, new Vector3d());
        double gl = env.ground(ml.x, ml.y, ml.z), gr = env.ground(mr.x, mr.y, mr.z);
        double gMain = Math.max(gl, gr);
        double yMain = Math.min(ml.y, mr.y);
        if (gearOut > 0.98f && gMain > -1e8 && yMain <= gMain + 0.03) {
            s.onGround = true;
            if (!wasGround) {
                s.impactSpeed = (float) -v.y;
                if (v.y < -7.5) crash(s, "hard_landing");
                else if (v.y < -3.2) s.hardLanding = true;
            }
            float yaw = s.yawDeg(), pitch = s.pitchDeg();
            double gN = env.ground(nz.x, nz.y, nz.z);
            float groundPitch = gN > -1e8 ? (float) Math.toDegrees(Math.atan2(gN - gMain, Math.max(1, d.wheelBase))) : 0;
            if (pitch < groundPitch) {
                pitch = groundPitch;
                s.pitchRate = Math.max(0, s.pitchRate);
            }
            float tail = Math.min(t.tailStrikeDeg, d.tailLimitDeg);
            if (pitch > groundPitch + tail) {
                pitch = groundPitch + tail;
                s.tailStrike = true;
                s.pitchRate = Math.min(0, s.pitchRate);
            }
            s.setYPR(yaw, pitch, 0);
            s.rollRate = 0;
            ml = s.toWorld(d.mainGearL, ml);
            mr = s.toWorld(d.mainGearR, mr);
            s.y += gMain - Math.min(ml.y, mr.y);
            if (v.y < 0) v.y = 0;
            // lift-off: let the aircraft climb only when lift exceeds weight
            if (L * u.y > mass * G * 1.02 && v.y >= 0) {
                s.onGround = false;
                v.y = Math.max(v.y, 0.3);
            }
        }
        // hull probes: belly landing or crash
        if (hull != null && !s.crashed) {
            Vector3d w = new Vector3d();
            for (Vector3f hp : hull) {
                s.toWorld(hp, w);
                if (!env.solid(w.x, w.y, w.z)) continue;
                double sp = v.length();
                s.impactSpeed = (float) sp;
                if (sp > 28 || v.y < -6) {
                    crash(s, "terrain");
                } else {
                    // scraping contact: push out of the block and bleed speed
                    double gh = env.ground(w.x, w.y + 1, w.z);
                    if (gh > -1e8 && gh - w.y < 1.5) s.y += gh - w.y + 0.01;
                    v.mul(0.92);
                    if (v.y < 0) v.y = 0;
                    s.onGround = true;
                    s.hardLanding = true;
                }
                break;
            }
        }
        // telemetry
        s.alpha = alpha;
        s.beta = beta;
        s.mach = mach;
        s.ias = (float) (V * Math.sqrt(sigma));
        s.gLoad = s.onGround ? 1 : gLoad;
        s.stall = stall && !s.onGround;
        s.steer = steer;
        double gnd = env.ground(s.x, s.y, s.z);
        s.agl = gnd > -1e8 ? (float) (s.y - gnd) : 999;
    }

    private static void crash(State s, String why) {
        if (s.crashed) return;
        s.crashed = true;
        s.crashReason = why;
    }

    // ------------------------------------------------------------------ bombs
    /** One ballistic step of a free-fall bomb: gravity + quadratic drag (world (block) position, real velocity). */
    public static void bombStep(Vector3d p, Vector3d v, BombType b, double dt, double speedScale) {
        double V = v.length();
        double kd = b.dragK() * density(p.y, speedScale) * V;
        v.x += (-kd * v.x) * dt;
        v.y += (-G - kd * v.y) * dt;
        v.z += (-kd * v.z) * dt;
        p.x += v.x * dt * speedScale;
        p.y += v.y * dt * speedScale;
        p.z += v.z * dt * speedScale;
    }

    /**
     * Predicted impact point of a bomb released now (CCIP / CCRP solution). Integrated with the same
     * 20 Hz step the bomb entity uses, so the prediction matches the real fall exactly in calm air.
     *
     * @return impact point, or null if it does not hit within 90 s
     */
    public static Vector3d predictImpact(Vector3d start, Vector3d vel, BombType b, double speedScale, Env env, double[] timeOut) {
        Vector3d p = new Vector3d(start), v = new Vector3d(vel), prev = new Vector3d();
        for (int i = 0; i < 1800; i++) {
            prev.set(p);
            bombStep(p, v, b, 0.05, speedScale);
            double g = env.ground(p.x, prev.y, p.z);
            if (g > -1e8 && p.y <= g) {
                double f = (prev.y - g) / Math.max(1e-6, prev.y - p.y);
                if (timeOut != null) timeOut[0] = (i + f) * 0.05;
                return new Vector3d(prev).lerp(p, Math.max(0, Math.min(1, f)));
            }
            if (p.y < -64) return null;
        }
        return null;
    }
}
