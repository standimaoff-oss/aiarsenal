/*
 * Decompiled with CFR 0.152.
 */
package ru.aiforge.realwar.blast;

public final class BlastPhysics {
    public static final double P0 = 101.325;
    public static final double C0 = 343.0;
    public static final double KG_PER_KT = 1000000.0;

    private BlastPhysics() {
    }

    public static double sadovsky(double z) {
        z = Math.max(z, 0.25);
        return 84.0 / z + 270.0 / (z * z) + 700.0 / (z * z * z);
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.min(1.0, Math.max(0.0, (x - e0) / (e1 - e0)));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double overpressure(double rMeters, double wKg, boolean surface) {
        rMeters = Math.max(rMeters, 0.05);
        if (surface) {
            double z = rMeters / Math.cbrt(wKg * 1.8);
            return BlastPhysics.sadovsky(z);
        }
        double z = rMeters / Math.cbrt(wKg);
        return BlastPhysics.sadovsky(z) * (1.0 + 0.8 * BlastPhysics.smooth(1.5, 6.0, z));
    }

    public static double shockSpeed(double pKPa) {
        return 343.0 * Math.sqrt(1.0 + 0.857 * pKPa / 101.325);
    }

    public static double rangeFor(double pKPa, double wKg, boolean surface) {
        double lo = 0.05;
        double hi = 1.0E7;
        for (int i = 0; i < 90; ++i) {
            double mid = Math.sqrt(lo * hi);
            if (BlastPhysics.overpressure(mid, wKg, surface) > pKPa) {
                lo = mid;
                continue;
            }
            hi = mid;
        }
        return hi;
    }

    public static double durationFactor(double wKg) {
        double f = 1.0 / (1.0 + 0.6 * Math.log10(1.0 + wKg / 1000.0));
        return Math.max(0.25, Math.min(1.0, f));
    }

    public static double blockThresholdKPa(double resistance, double durationFactor) {
        double t = 10.0 * Math.pow(Math.max(resistance, 0.05), 1.2) * durationFactor;
        return Math.max(t, 1.5);
    }

    public static double thermalFluence(double yieldKt, double rMeters) {
        rMeters = Math.max(rMeters, 1.0);
        double e = yieldKt * 1.0E12 * 0.35;
        double rCm = rMeters * 100.0;
        double tau = Math.exp(-rMeters / 7000.0);
        return e * tau / (Math.PI * 4 * rCm * rCm);
    }

    public static double fireballRadiusM(double yieldKt) {
        return 38.0 * Math.pow(yieldKt, 0.4);
    }

    public static double cloudTopKm(double yieldKt) {
        return 4.0 * Math.pow(yieldKt, 0.25);
    }

    public static float blastDamage(double pKPa) {
        if (pKPa < 6.0) {
            return 0.0f;
        }
        if (pKPa < 35.0) {
            return (float)((pKPa - 6.0) * 0.15);
        }
        if (pKPa < 100.0) {
            return (float)(4.35 + (pKPa - 35.0) * 0.25);
        }
        return (float)(20.6 + (pKPa - 100.0) * 0.4);
    }

    public static int craterRadiusBlocks(double yieldKt, double metersPerBlock, double scale) {
        double r = scale * 11.35 * Math.cbrt(yieldKt) * Math.sqrt(17.5 / metersPerBlock);
        return (int)Math.max(0L, Math.round(r));
    }
}

