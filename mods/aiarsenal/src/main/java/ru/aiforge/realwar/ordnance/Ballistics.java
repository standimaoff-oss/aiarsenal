/*
 * Decompiled with CFR 0.152.
 */
package ru.aiforge.realwar.ordnance;

import ru.aiforge.realwar.RWConfig;

public final class Ballistics {
    public static final double G = 9.80665;

    private Ballistics() {
    }

    public static double scaleK() {
        return (Double)RWConfig.COMMON.ordnanceScale.get();
    }

    public static int substeps() {
        return Math.max(1, (int)Math.round(Math.sqrt(Ballistics.scaleK())));
    }

    public static double dtStep() {
        return 0.05;
    }

    public static double rho(double alt) {
        return 1.225 * Math.exp(-Math.max(alt, 0.0) / 8500.0);
    }

    public static double range(Profile p, double elevDeg, double dy) {
        double e = Math.toRadians(elevDeg);
        Flight f = new Flight(p, Math.cos(e), Math.sin(e), 0.0);
        double prevX = 0.0;
        double prevY = 0.0;
        boolean peaked = false;
        for (int i = 0; i < 20000; ++i) {
            f.step(0.05, 0.0, 0.0, 0.0);
            if (f.vy < 0.0) {
                peaked = true;
            }
            if (peaked && f.y <= dy) {
                double k = (prevY - dy) / Math.max(1.0E-9, prevY - f.y);
                return prevX + (f.x - prevX) * Math.min(1.0, Math.max(0.0, k));
            }
            prevX = f.x;
            prevY = f.y;
        }
        return prevX;
    }

    public static double solveElevation(Profile p, double rangeM, double dyM, double elMin, double elMax) {
        double lo = elMin;
        double hi = elMax;
        double best = lo;
        double bestR = Ballistics.range(p, lo, dyM);
        for (double e = lo; e <= hi; e += 2.5) {
            double r = Ballistics.range(p, e, dyM);
            if (!(r > bestR)) continue;
            bestR = r;
            best = e;
        }
        if (rangeM > bestR) {
            return Double.NaN;
        }
        if (Ballistics.range(p, lo, dyM) >= rangeM) {
            return lo;
        }
        double a = lo;
        double b = best;
        for (int i = 0; i < 28; ++i) {
            double mid = 0.5 * (a + b);
            if (Ballistics.range(p, mid, dyM) < rangeM) {
                a = mid;
                continue;
            }
            b = mid;
        }
        return 0.5 * (a + b);
    }

    public static final class Flight {
        public final Profile p;
        public double x;
        public double y;
        public double z;
        public double vx;
        public double vy;
        public double vz;
        public double t;
        public final double ax;
        public final double ay;
        public final double az;
        public double altitudeBase;

        public Flight(Profile p, double ax, double ay, double az) {
            this.p = p;
            double n = Math.sqrt(ax * ax + ay * ay + az * az);
            this.ax = ax / n;
            this.ay = ay / n;
            this.az = az / n;
            this.vx = this.ax * p.ejectSpeed;
            this.vy = this.ay * p.ejectSpeed;
            this.vz = this.az * p.ejectSpeed;
        }

        public double speed() {
            return Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
        }

        public boolean burning() {
            return this.t >= this.p.igniteDelay && this.t < this.p.igniteDelay + this.p.burn;
        }

        public void step(double dt, double gx, double gy, double gz) {
            double dz;
            double dy;
            double dx;
            double sp = this.speed();
            if (sp > 30.0) {
                dx = this.vx / sp;
                dy = this.vy / sp;
                dz = this.vz / sp;
            } else {
                dx = this.ax;
                dy = this.ay;
                dz = this.az;
            }
            boolean burn = this.burning();
            double burned = Math.min(Math.max(this.t - this.p.igniteDelay, 0.0), this.p.burn);
            double m = this.p.m0 - this.p.mdot() * burned;
            double thrust = burn ? this.p.mdot() * this.p.ve : 0.0;
            double drag = 0.5 * Ballistics.rho(this.altitudeBase + this.y) * sp * this.p.cd * this.p.area;
            double inv = 1.0 / m;
            double axx = thrust * inv * dx - drag * inv * this.vx + gx;
            double ayy = thrust * inv * dy - drag * inv * this.vy - 9.80665 + gy;
            double azz = thrust * inv * dz - drag * inv * this.vz + gz;
            this.vx += axx * dt;
            this.vy += ayy * dt;
            this.vz += azz * dt;
            this.x += this.vx * dt;
            this.y += this.vy * dt;
            this.z += this.vz * dt;
            this.t += dt;
        }
    }

    public static enum Profile {
        GRAD("9M22M", 66.0, 20.5, 1.8, 1930.0, 0.47, 0.122, 0.0, 0.0, 12.0, 0.8),
        SMERCH("9M55F", 800.0, 280.0, 6.0, 2450.0, 0.28, 0.3, 0.0, 0.0, 240.0, 1.0),
        SAM_48N6("48N6E2", 1900.0, 1000.0, 12.0, 2500.0, 0.3, 0.51, 0.9, 30.0, 180.0, 1.3),
        // ---- gun projectiles (no motor: mp = 0, burn > 0 avoids 0/0; ejectSpeed = muzzle velocity, m/s). tntKg 0 = kinetic (AP).
        T125_HE("3OF26", 23.0, 0.0, 0.001, 0.0, 0.30, 0.125, 0.0, 850.0, 3.4, 1.0),
        T125_AP("3BM60", 4.85, 0.0, 0.001, 0.0, 0.60, 0.032, 0.0, 1660.0, 0.0, 0.0),
        T120_HE("M830A1", 11.4, 0.0, 0.001, 0.0, 0.35, 0.120, 0.0, 1410.0, 1.6, 0.8),
        T120_AP("M829A4", 4.6, 0.0, 0.001, 0.0, 0.60, 0.030, 0.0, 1650.0, 0.0, 0.0),
        B100_HE("3OF32", 15.8, 0.0, 0.001, 0.0, 0.30, 0.100, 0.0, 250.0, 1.6, 0.9),
        D30_HE("OF-462", 21.76, 0.0, 0.001, 0.0, 0.29, 0.122, 0.0, 690.0, 3.6, 1.0),
        MSTA_HE("3OF45", 43.56, 0.0, 0.001, 0.0, 0.27, 0.152, 0.0, 810.0, 7.8, 1.1),
        M777_HE("M795", 46.7, 0.0, 0.001, 0.0, 0.30, 0.155, 0.0, 827.0, 10.8, 1.1),
        // ---- helicopter cannons (reply 6): 30x165 3UOF8 HE-I (2A42), 30x113 M789 HEDP (M230)
        A42_HE("3UOF8", 0.39, 0.0, 0.001, 0.0, 0.32, 0.030, 0.0, 960.0, 0.049, 0.6),
        M230_HE("M789", 0.23, 0.0, 0.001, 0.0, 0.34, 0.030, 0.0, 805.0, 0.022, 0.6),
        // ---- WW2 (reply 8). Real projectile masses / muzzle velocities; tntKg = bursting charge (TNT eq.).
        KW20_HE("2cm Sprgr", 0.148, 0.0, 0.001, 0.0, 0.35, 0.020, 0.0, 780.0, 0.006, 0.4),
        KW20_AP("2cm Pzgr 40", 0.100, 0.0, 0.001, 0.0, 0.42, 0.020, 0.0, 1050.0, 0.0, 0.0),
        K75_HE("7.5cm Sprgr 34", 5.74, 0.0, 0.001, 0.0, 0.30, 0.075, 0.0, 550.0, 0.69, 0.9),
        K75_AP("7.5cm Pzgr 39", 6.8, 0.0, 0.001, 0.0, 0.35, 0.075, 0.0, 790.0, 0.0, 0.0),
        K88_HE("8.8cm Sprgr", 9.5, 0.0, 0.001, 0.0, 0.30, 0.088, 0.0, 820.0, 0.9, 1.0),
        K88_AP("8.8cm Pzgr 39", 10.2, 0.0, 0.001, 0.0, 0.35, 0.088, 0.0, 773.0, 0.0, 0.0),
        S85_HE("O-365", 9.54, 0.0, 0.001, 0.0, 0.30, 0.085, 0.0, 793.0, 0.74, 1.0),
        S85_AP("BR-365", 9.2, 0.0, 0.001, 0.0, 0.35, 0.085, 0.0, 792.0, 0.0, 0.0),
        S107_HE("OF-420", 18.8, 0.0, 0.001, 0.0, 0.30, 0.107, 0.0, 830.0, 2.2, 1.0),
        K128_HE("12.8cm Sprgr L/5", 28.0, 0.0, 0.001, 0.0, 0.30, 0.128, 0.0, 750.0, 3.6, 1.0),
        K128_AP("12.8cm Pzgr 43", 28.3, 0.0, 0.001, 0.0, 0.35, 0.128, 0.0, 920.0, 0.0, 0.0),
        K75S_HE("7.5cm Sprgr KwK 44", 5.74, 0.0, 0.001, 0.0, 0.30, 0.075, 0.0, 420.0, 0.69, 0.9),
        SK280_HE("28cm Sprgr L/4.4", 315.0, 0.0, 0.001, 0.0, 0.30, 0.283, 0.0, 890.0, 16.9, 1.4),
        KV2_HE("OF-530", 40.0, 0.0, 0.001, 0.0, 0.30, 0.152, 0.0, 436.0, 6.0, 1.1),
        IS2_HE("OF-471", 25.0, 0.0, 0.001, 0.0, 0.29, 0.122, 0.0, 800.0, 3.6, 1.0),
        IS2_AP("BR-471", 25.0, 0.0, 0.001, 0.0, 0.35, 0.122, 0.0, 795.0, 0.0, 0.0),
        /** Sturmtiger 38 cm R-Sprgr 4581: 345 kg, 135 kg charge. Short boost (~300 m/s) folded into the launch speed. */
        RW61("38cm R-Sprgr 4581", 345.0, 0.0, 0.001, 0.0, 0.33, 0.380, 0.0, 300.0, 135.0, 1.4),
        /** BM-13 M-13 132 mm: 42.5 kg, 7.1 kg cordite, 0.7 s burn -> ~355 m/s, 4.9 kg TNT, range 8.5 km. */
        M13("M-13", 42.5, 7.1, 0.7, 2060.0, 0.40, 0.132, 0.0, 0.0, 4.9, 0.9),
        // ---- Bismarck (reply 10). cd fitted to the published ranges: 38 cm 36.5 km at 30 deg, 15 cm 23 km at 40 deg,
        // 10.5 cm 17.7 km at 45 deg (same point-mass drag model as Flight, exponential atmosphere).
        SK38_AP("38cm Psgr L/4.4", 800.0, 0.0, 0.001, 0.0, 0.289, 0.38, 0.0, 820.0, 0.0, 0.0),
        SK38_HE("38cm Sprgr L/4.5 Bdz", 800.0, 0.0, 0.001, 0.0, 0.289, 0.38, 0.0, 820.0, 64.0, 1.5),
        SK15_HE("15cm Sprgr L/4.6 KZ", 45.3, 0.0, 0.001, 0.0, 0.352, 0.149, 0.0, 875.0, 3.9, 1.1),
        FLAK105_HE("10.5cm Sprgr L/4.4 Zt.Z", 15.1, 0.0, 0.001, 0.0, 0.348, 0.105, 0.0, 900.0, 1.4, 1.0),
        // ---- WW2 aircraft cannons (reply 11): 20x82 MG 151/20 Minengeschoss, 20x99R ShVAK OFZ, 20x80RB MG FF/M
        MG151_HE("2cm M-Gesch. MG 151/20", 0.092, 0.0, 0.001, 0.0, 0.36, 0.020, 0.0, 805.0, 0.018, 0.5),
        SHVAK_HE("20mm OFZ ShVAK", 0.096, 0.0, 0.001, 0.0, 0.34, 0.020, 0.0, 790.0, 0.006, 0.6),
        MGFF_HE("2cm M-Gesch. MG FF/M", 0.092, 0.0, 0.001, 0.0, 0.38, 0.020, 0.0, 700.0, 0.018, 0.5),
        // ---- reply 12: M2A3 Bradley 25x137 M792 HEI-T / M919 APFSDS-T, TOW-2B (reticle only: the real missile is SACLOS),
        // TOS-1A MO.1.01.04M 220 mm thermobaric rocket: 217 kg, 3.72 m, motor ~1.2 s -> ~285 m/s, 600..6000 m
        M242_HE("M792 HEI-T", 0.183, 0.0, 0.001, 0.0, 0.34, 0.025, 0.0, 1100.0, 0.030, 0.5),
        M242_AP("M919 APFSDS-T", 0.134, 0.0, 0.001, 0.0, 0.55, 0.025, 0.0, 1385.0, 0.0, 0.0),
        TOW2B("BGM-71F TOW-2B", 22.6, 0.0, 0.001, 0.0, 0.25, 0.152, 0.0, 300.0, 6.0, 0.4),
        TOS220("MO.1.01.04M", 217.0, 30.0, 1.2, 1900.0, 0.42, 0.220, 0.0, 0.0, 90.0, 1.6),
        // ---- WWI vertical slice: QF 6-pdr 6 cwt sponson cannon, 57 mm / 6 lb shell, ~538 m/s.
        QF6_HE("QF 6-pdr HE", 2.72, 0.0, 0.001, 0.0, 0.42, 0.057, 0.0, 538.0, 0.10, 0.8),
        QF6_AP("QF 6-pdr AP", 2.72, 0.0, 0.001, 0.0, 0.48, 0.057, 0.0, 538.0, 0.0, 0.0);

        public boolean isShell() {
            return this.mp <= 0.0;   // no motor = gun projectile (r8: rocket profiles may follow shells in the enum)
        }

        public boolean kinetic() {
            return this.isShell() && this.tntKg <= 0.0;
        }

        public final String title;
        public final double m0;
        public final double mp;
        public final double burn;
        public final double ve;
        public final double cd;
        public final double d;
        public final double igniteDelay;
        public final double ejectSpeed;
        public final double tntKg;
        public final double frag;
        public final double area;

        private Profile(String title, double m0, double mp, double burn, double ve, double cd, double d, double igniteDelay, double ejectSpeed, double tntKg, double frag) {
            this.title = title;
            this.m0 = m0;
            this.mp = mp;
            this.burn = burn;
            this.ve = ve;
            this.cd = cd;
            this.d = d;
            this.igniteDelay = igniteDelay;
            this.ejectSpeed = ejectSpeed;
            this.tntKg = tntKg;
            this.frag = frag;
            this.area = Math.PI * d * d / 4.0;
        }

        public double mdot() {
            return this.mp / this.burn;
        }
    }
}

