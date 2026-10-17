/*
 * Decompiled with CFR 0.152.
 */
package ru.aiforge.realwar.blast;

import ru.aiforge.realwar.blast.BlastPhysics;

public final class NuclearProfile {
    public final double yieldKt;
    public final double wKg;
    public final double metersPerBlock;
    public final boolean surface;
    public final int maxR;
    public final float[] pressure;
    public final int[] arrival;

    private NuclearProfile(double yieldKt, boolean surface, double mpb, int maxR, float[] pressure, int[] arrival) {
        this.yieldKt = yieldKt;
        this.wKg = yieldKt * 1000000.0;
        this.surface = surface;
        this.metersPerBlock = mpb;
        this.maxR = maxR;
        this.pressure = pressure;
        this.arrival = arrival;
    }

    public static NuclearProfile create(double yieldKt, boolean surface, double mpb, int maxRCap) {
        double w = yieldKt * 1000000.0;
        int byPhysics = (int)(BlastPhysics.rangeFor(2.0, w, surface) / mpb);
        int maxR = Math.max(12, Math.min(maxRCap, byPhysics));
        float[] p = new float[maxR + 2];
        int[] arr = new int[maxR + 2];
        double t = 0.0;
        for (int i = 0; i <= maxR + 1; ++i) {
            double rM = Math.max(0.5, ((double)i + 0.5) * mpb);
            double pk = BlastPhysics.overpressure(rM, w, surface);
            p[i] = (float)Math.min(pk, 50000.0);
            arr[i] = (int)Math.round((t += mpb / BlastPhysics.shockSpeed(pk)) * 20.0);
        }
        return new NuclearProfile(yieldKt, surface, mpb, maxR, p, arr);
    }

    public int frontAt(int age) {
        int lo = 0;
        int hi = this.maxR;
        if (this.arrival[0] > age) {
            return -1;
        }
        while (lo < hi) {
            int mid = lo + hi + 1 >>> 1;
            if (this.arrival[mid] <= age) {
                lo = mid;
                continue;
            }
            hi = mid - 1;
        }
        return lo;
    }

    public float pressureAtBlocks(double blocks) {
        int i = (int)Math.max(0.0, Math.min((double)this.maxR, blocks));
        return this.pressure[i];
    }

    public double arrivalTicksAt(double blocks) {
        int i = (int)Math.max(0.0, Math.min((double)this.maxR, blocks));
        return this.arrival[i];
    }

    public double thermalRadiusBlocks(double q) {
        double lo = 0.5;
        double hi = 200000.0;
        for (int i = 0; i < 70; ++i) {
            double mid = Math.sqrt(lo * hi);
            if (BlastPhysics.thermalFluence(this.yieldKt, mid * this.metersPerBlock) > q) {
                lo = mid;
                continue;
            }
            hi = mid;
        }
        return hi;
    }

    public double fireballRadiusBlocks() {
        return BlastPhysics.fireballRadiusM(this.yieldKt) / this.metersPerBlock;
    }
}

