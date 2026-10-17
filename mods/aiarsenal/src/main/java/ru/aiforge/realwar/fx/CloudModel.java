/*
 * Decompiled with CFR 0.152.
 */
package ru.aiforge.realwar.fx;

import java.util.Locale;
import java.util.Random;

public final class CloudModel {
    public final double yieldKt;
    public final double burstY;
    public final double baseY;
    public final boolean surface;
    public final double height;
    public final double fireballMax;
    public final double capMajorMax;
    public final double capMinorMax;
    public final double stemR0;
    public final double stemR1;
    public final double tau;
    public final double skirtMax;
    private static final double[][] HOT = new double[][]{{0.0, 0.25, 0.2, 0.18}, {0.15, 0.6, 0.15, 0.05}, {0.4, 1.0, 0.4, 0.1}, {0.7, 1.0, 0.7, 0.3}, {1.0, 1.0, 0.97, 0.8}};

    public CloudModel(double yieldKt, double burstY, double baseY, boolean surface, double maxTopY) {
        this.yieldKt = yieldKt;
        this.burstY = burstY;
        this.baseY = baseY;
        this.surface = surface;
        double h = CloudModel.clamp(60.0 * Math.pow(yieldKt, 0.25), 30.0, 300.0);
        this.height = Math.max(24.0, Math.min(h, maxTopY - burstY - 4.0));
        this.fireballMax = 0.17 * this.height;
        this.capMajorMax = 0.25 * this.height;
        this.capMinorMax = 0.165 * this.height;
        this.stemR0 = 0.035 * this.height;
        this.stemR1 = 0.055 * this.height;
        this.tau = 8.0 + this.height / 20.0;
        this.skirtMax = (surface ? 0.45 : 0.22) * this.height;
    }

    public static double clamp(double v, double a, double b) {
        return v < a ? a : Math.min(v, b);
    }

    public static double smooth(double e0, double e1, double x) {
        double t = CloudModel.clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public double rise(double s) {
        return 1.0 - Math.exp(-s / this.tau);
    }

    public double capTopY(double s) {
        return this.burstY + this.height * this.rise(s);
    }

    public double capMinor(double s) {
        double settle = 1.0 - 0.22 * (1.0 - Math.exp(-Math.max(0.0, s - 20.0) / 60.0));
        return this.capMinorMax * (1.0 - Math.exp(-s / (0.5 * this.tau))) * settle;
    }

    public double capMajor(double s) {
        double spread = 1.0 + 0.3 * (1.0 - Math.exp(-s / 50.0));
        return this.capMajorMax * Math.pow(1.0 - Math.exp(-s / (0.55 * this.tau)), 0.9) * spread;
    }

    public double capCenterY(double s) {
        return this.capTopY(s) - this.capMinor(s) * 1.1;
    }

    public double fireballRadius(double s) {
        double grow = Math.pow(1.0 - Math.exp(-s / 1.1), 0.6);
        double fade = s < 3.0 ? 1.0 : Math.exp(-(s - 3.0) / 9.0);
        return this.fireballMax * grow * fade;
    }

    public double heat(double s) {
        return Math.exp(-s / (4.0 + 0.1 * this.height));
    }

    public double stemTopY(double s) {
        return this.capCenterY(s) - this.capMinor(s) * 0.6;
    }

    public double stemRadius(double u, double s) {
        double base = this.stemR0 + (this.stemR1 - this.stemR0) * Math.pow(u, 0.8);
        double flare = 1.0 + 1.6 * Math.exp(-u * 10.0);
        double growth = 1.0 - Math.exp(-s / 5.0);
        return base * flare * growth;
    }

    public double skirtRadius(double s) {
        return this.skirtMax * (1.0 - Math.exp(-s / 5.0));
    }

    public void capPos(double s, double theta, double rho, double phi0, double[] out) {
        double phi = phi0 - 0.16 * s * (1.0 + 0.6 * (1.0 - rho));
        double major = this.capMajor(s);
        double minor = this.capMinor(s) * rho;
        double r = major + minor * Math.cos(phi);
        double th = theta + 0.01 * s * (1.0 - rho);
        out[0] = r * Math.cos(th);
        double sy = Math.sin(phi);
        out[1] = this.capCenterY(s) + minor * (sy > 0.0 ? sy * 1.1 : sy * 0.6);
        out[2] = r * Math.sin(th);
        if (out.length > 3) {
            out[3] = phi;
        }
    }

    public void stemPos(double s, double u, double theta, double jitter, double swirlPhase, double[] out) {
        double top = this.stemTopY(s);
        double y = this.baseY + u * Math.max(0.0, top - this.baseY);
        double r = this.stemRadius(u, s) * (0.25 + 0.85 * jitter);
        double wob = 1.0 + 0.12 * Math.sin(s * 0.7 + swirlPhase + u * 9.0);
        double th = theta + s * 0.12 * (1.0 - 0.6 * u);
        out[0] = r * wob * Math.cos(th);
        out[1] = y;
        out[2] = r * wob * Math.sin(th);
    }

    public void fireballPos(double s, double dx, double dy, double dz, double rho, double[] out) {
        double r = this.fireballRadius(s) * rho;
        out[0] = dx * r;
        out[1] = this.capCenterY(s) + dy * r * 0.9;
        out[2] = dz * r;
    }

    public void skirtPos(double s, double theta, double frac, double h, double[] out) {
        double r = this.skirtRadius(s) * (0.45 + 0.55 * frac);
        double lift = h * (1.0 + 2.0 * (1.0 - Math.exp(-s / 6.0)));
        out[0] = r * Math.cos(theta);
        out[1] = this.baseY + 1.0 + lift;
        out[2] = r * Math.sin(theta);
    }

    public static void hotColor(double heat, float[] out) {
        heat = CloudModel.clamp(heat, 0.0, 1.0);
        for (int i = 0; i < HOT.length - 1; ++i) {
            if (!(heat <= HOT[i + 1][0])) continue;
            double f = (heat - HOT[i][0]) / (HOT[i + 1][0] - HOT[i][0]);
            for (int k = 0; k < 3; ++k) {
                out[k] = (float)(HOT[i][k + 1] + (HOT[i + 1][k + 1] - HOT[i][k + 1]) * f);
            }
            return;
        }
        for (int k = 0; k < 3; ++k) {
            out[k] = (float)HOT[HOT.length - 1][k + 1];
        }
    }

    public static void main(String[] args) {
        int i;
        if (args.length < 3 || !args[0].equals("dump")) {
            System.err.println("usage: dump <yieldKt> <seconds> [surface|air]");
            return;
        }
        double y = Double.parseDouble(args[1]);
        double s = Double.parseDouble(args[2]);
        boolean surface = args.length < 4 || args[3].equals("surface");
        double ground = 64.0;
        double burst = surface ? ground + 0.5 : ground + 220.0 * Math.cbrt(y) / 17.5;
        CloudModel m = new CloudModel(y, burst, ground, surface, 320.0);
        Random rnd = new Random(7L);
        double[] o = new double[4];
        StringBuilder sb = new StringBuilder();
        for (i = 0; i < 2600; ++i) {
            m.capPos(s, rnd.nextDouble() * Math.PI * 2.0, Math.sqrt(rnd.nextDouble()), rnd.nextDouble() * Math.PI * 2.0, o);
            sb.append(String.format(Locale.ROOT, "%.2f %.2f %.2f cap%n", o[0], o[1], o[2]));
        }
        for (i = 0; i < 1100; ++i) {
            m.stemPos(s, rnd.nextDouble(), rnd.nextDouble() * Math.PI * 2.0, rnd.nextDouble(), rnd.nextDouble() * 6.28, o);
            sb.append(String.format(Locale.ROOT, "%.2f %.2f %.2f stem%n", o[0], o[1], o[2]));
        }
        for (i = 0; i < 500; ++i) {
            double a = rnd.nextDouble() * 6.283;
            double b = Math.acos(2.0 * rnd.nextDouble() - 1.0);
            m.fireballPos(s, Math.sin(b) * Math.cos(a), Math.cos(b), Math.sin(b) * Math.sin(a), Math.cbrt(rnd.nextDouble()), o);
            sb.append(String.format(Locale.ROOT, "%.2f %.2f %.2f fire%n", o[0], o[1], o[2]));
        }
        for (i = 0; i < 500; ++i) {
            m.skirtPos(s, rnd.nextDouble() * 6.283, rnd.nextDouble(), rnd.nextDouble() * 3.0, o);
            sb.append(String.format(Locale.ROOT, "%.2f %.2f %.2f skirt%n", o[0], o[1], o[2]));
        }
        ru.aiforge.realwar.RealWar.LOGGER.info("{}", sb);
    }
}

