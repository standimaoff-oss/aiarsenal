package ru.aiforge.bm21grad.ballistics;

import net.minecraft.world.phys.Vec3;

/**
 * Deterministic point-mass model of an unguided 122-mm rocket (units: blocks, ticks).
 * Boost phase (thrust along velocity) -> ballistic phase with quadratic drag and gravity.
 * The same integrator is used by the fire-control solver and by the flying projectile, so the
 * computed elevation hits the target exactly (before dispersion).
 */
public final class Ballistics {
    public static final double GRAVITY = 0.0245;   // 9.81 m/s^2 in blocks/tick^2
    public static final double MUZZLE = 1.0;       // leaving the tube, blocks/tick
    public static final int BURN_TICKS = 20;       // ~1 s boost (the real motor burns 1.8-2 s)
    public static final double DRAG = 0.0004;
    public static final double MAX_ELEV = 55.0;
    public static final double MIN_ELEV = 0.0;

    private Ballistics() {
    }

    /** Thrust acceleration per tick for a given range scale (config) and rocket type factor. */
    public static double thrust(double rangeScale, double typeFactor) {
        return 0.20 * Math.sqrt(Math.max(0.05, rangeScale * typeFactor));
    }

    /** One integration step. state = {x,y,z,vx,vy,vz}; age in ticks since launch. */
    public static void step(double[] s, int age, double thrust) {
        double vx = s[3], vy = s[4], vz = s[5];
        double sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (age < BURN_TICKS && sp > 1e-6) {
            vx += thrust * vx / sp;
            vy += thrust * vy / sp;
            vz += thrust * vz / sp;
            sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
        }
        double d = DRAG * sp;
        vx -= vx * d;
        vy -= vy * d;
        vz -= vz * d;
        vy -= GRAVITY;
        s[0] += vx;
        s[1] += vy;
        s[2] += vz;
        s[3] = vx;
        s[4] = vy;
        s[5] = vz;
    }

    public record Shot(double horizontalDistance, int flightTicks, double apex, boolean reached) {
    }

    /** Flies a shot in a vertical plane until it descends to {@code targetDy} (relative height). */
    public static Shot simulate(double elevDeg, double thrust, double launchHeight, double targetDy) {
        double e = Math.toRadians(elevDeg);
        double[] s = {0, launchHeight, 0, MUZZLE * Math.cos(e), MUZZLE * Math.sin(e), 0};
        double apex = s[1];
        for (int t = 0; t < 2400; t++) {
            double px = s[0], py = s[1];
            step(s, t, thrust);
            apex = Math.max(apex, s[1]);
            if (s[4] < 0 && s[1] <= targetDy) {
                double f = (py - targetDy) / Math.max(1e-6, py - s[1]);
                return new Shot(px + (s[0] - px) * f, t + 1, apex, true);
            }
        }
        return new Shot(s[0], 2400, apex, false);
    }

    public record Solution(boolean ok, double elevationDeg, int flightTicks, double apex, String reason) {
    }

    /** Elevation of the first maximum of range(elev) (with drag it is below 45 deg). */
    public static double maxRangeElevation(double thrust, double h, double dy) {
        double best = 0, bestE = MAX_ELEV;
        for (double e = 20; e <= MAX_ELEV; e += 0.5) {
            Shot s = simulate(e, thrust, h, dy);
            if (s.reached && s.horizontalDistance > best) {
                best = s.horizontalDistance;
                bestE = e;
            }
        }
        return bestE;
    }

    public static double maxRange(double thrust, double h, double dy) {
        return simulate(maxRangeElevation(thrust, h, dy), thrust, h, dy).horizontalDistance;
    }

    /** Low-angle solution by bisection (range is monotonic on [MIN_ELEV, eMax]). */
    public static Solution solve(double distance, double dy, double thrust, double launchHeight) {
        double eMax = maxRangeElevation(thrust, launchHeight, dy);
        Shot far = simulate(eMax, thrust, launchHeight, dy);
        if (!far.reached || far.horizontalDistance < distance)
            return new Solution(false, eMax, far.flightTicks, far.apex, "too_far");
        Shot near = simulate(MIN_ELEV, thrust, launchHeight, dy);
        if (near.reached && near.horizontalDistance > distance)
            return new Solution(false, MIN_ELEV, near.flightTicks, near.apex, "too_close");
        double lo = MIN_ELEV, hi = eMax;
        Shot mid = far;
        for (int i = 0; i < 40; i++) {
            double m = (lo + hi) / 2;
            mid = simulate(m, thrust, launchHeight, dy);
            if (!mid.reached || mid.horizontalDistance < distance) lo = m;
            else hi = m;
        }
        return new Solution(true, (lo + hi) / 2, mid.flightTicks, mid.apex, "ok");
    }

    /** Azimuth in degrees, Minecraft yaw convention (0 = +Z south, 90 = -X west). */
    public static double yawTo(Vec3 from, double tx, double tz) {
        return Math.toDegrees(Math.atan2(-(tx - from.x), tz - from.z));
    }

    /** Soviet artillery goniometer: 6000 mils per circle, printed as "dd-dd" (e.g. 15-00). */
    public static String mils(double yawDeg) {
        // true bearing from north, clockwise: north(-Z)=0, east(+X)=90
        double bearing = ((yawDeg + 180.0) % 360.0 + 360.0) % 360.0;
        int m = (int) Math.round(bearing / 360.0 * 6000.0) % 6000;
        return String.format("%02d-%02d", m / 100, m % 100);
    }
}
