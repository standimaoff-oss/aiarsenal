/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 */
package ru.aiforge.realwar.missile;

import net.minecraft.resources.ResourceLocation;

public enum MissileType {
    TACTICAL_HE(Cls.TACTICAL, Warhead.HE, 500.0, 1, 0.0),
    TACTICAL_KINETIC(Cls.TACTICAL, Warhead.KINETIC, 260.0, 1, 0.0),
    TACTICAL_MIRV(Cls.TACTICAL, Warhead.MIRV, 160.0, 3, 26.0),
    TACTICAL_THERMOBARIC(Cls.TACTICAL, Warhead.THERMOBARIC, 380.0, 1, 0.0),
    OPERATIONAL_HE(Cls.OPERATIONAL, Warhead.HE, 1600.0, 1, 0.0),
    OPERATIONAL_KINETIC(Cls.OPERATIONAL, Warhead.KINETIC, 1000.0, 1, 0.0),
    OPERATIONAL_MIRV(Cls.OPERATIONAL, Warhead.MIRV, 420.0, 6, 60.0),
    OPERATIONAL_PENETRATOR(Cls.OPERATIONAL, Warhead.PENETRATOR, 1500.0, 1, 0.0),
    CRUISE_HE(Cls.CRUISE, Warhead.HE, 450.0, 1, 0.0),
    CRUISE_CLUSTER(Cls.CRUISE, Warhead.CLUSTER, 14.0, 48, 34.0),
    ICBM_HE(Cls.ICBM, Warhead.HE, 9000.0, 1, 0.0),
    ICBM_KINETIC(Cls.ICBM, Warhead.KINETIC, 6500.0, 1, 0.0),
    ICBM_MIRV(Cls.ICBM, Warhead.MIRV, 1300.0, 8, 150.0),
    ICBM_MIRV_HEAVY(Cls.ICBM, Warhead.MIRV_HEAVY, 3200.0, 8, 230.0);

    public final Cls cls;
    public final Warhead warhead;
    public final double massKg;
    public final int warheads;
    public final double footprint;

    private MissileType(Cls cls, Warhead w, double massKg, int warheads, double footprint) {
        this.cls = cls;
        this.warhead = w;
        this.massKg = massKg;
        this.warheads = warheads;
        this.footprint = footprint;
    }

    public String id() {
        return this.cls.id + "_" + this.warhead.name().toLowerCase();
    }

    public String itemName() {
        return "missile_" + this.id();
    }

    public ResourceLocation model() {
        return new ResourceLocation("aiarsenal", "models/glb/missile_" + this.id() + ".glb");
    }

    public int padLevel() {
        return this.cls.padLevel;
    }

    public double totalMassKg() {
        return this.massKg * (double)this.warheads;
    }

    public static MissileType byOrdinal(int i) {
        MissileType[] v = MissileType.values();
        return i >= 0 && i < v.length ? v[i] : null;
    }

    public static enum Cls {
        TACTICAL("tactical", 1, 3.0, 150.0, 2500.0, 0.2, 2.4, 0.26, 50.0, 0.18, 700.0, 40.0, 2.2),
        OPERATIONAL("operational", 2, 4.5, 800.0, 7000.0, 0.5, 4.2, 0.45, 80.0, 0.25, 1800.0, 100.0, 3.2),
        CRUISE("cruise", 1, 1.8, 100.0, 5000.0, 0.5, 2.2, 0.14, 25.0, 0.0, 0.0, 0.0, 1.3),
        ICBM("icbm", 3, 6.5, 3000.0, 16000.0, 0.65, 7.0, 0.8, 150.0, 0.3, 3500.0, 400.0, 5.0);

        public final String id;
        public final int padLevel;
        public final double speed;
        public final double minRange;
        public final double maxRange;
        public final double kmPerBlock;
        public final double length;
        public final double radius;
        public final double boostTicks;
        public final double apexFrac;
        public final double apexMax;
        public final double apexMin;
        public final double trail;

        private Cls(String id, int padLevel, double speed, double minRange, double maxRange, double kmPerBlock, double length, double radius, double boostTicks, double apexFrac, double apexMax, double apexMin, double trail) {
            this.id = id;
            this.padLevel = padLevel;
            this.speed = speed;
            this.minRange = minRange;
            this.maxRange = maxRange;
            this.kmPerBlock = kmPerBlock;
            this.length = length;
            this.radius = radius;
            this.boostTicks = boostTicks;
            this.apexFrac = apexFrac;
            this.apexMax = apexMax;
            this.apexMin = apexMin;
            this.trail = trail;
        }
    }

    public static enum Warhead {
        HE,
        KINETIC,
        MIRV,
        MIRV_HEAVY,
        THERMOBARIC,
        PENETRATOR,
        CLUSTER;

    }
}

