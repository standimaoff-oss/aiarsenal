package ru.aiforge.tubombers.aircraft;

/**
 * Bomber stores: Soviet M-54 pattern FAB bombs plus the arsenal stores carried internally on the MKU-6-5 rotary
 * launcher (Kh-101 / Kh-55 cruise missiles) and the free-fall nuclear bombs RDS-1 and AN602.
 */
public enum BombType {
    FAB250("fab250", "FAB250", 1.59f, 0.325f, 268f, 97f, 0.30f, 4.0f, 4.0f, 18f, 60f),
    FAB500("fab500", "FAB500", 2.37f, 0.45f, 477f, 201f, 0.30f, 5.5f, 5.5f, 26f, 80f),
    FAB1500("fab1500", "FAB1500", 3.00f, 0.58f, 1550f, 675f, 0.28f, 9.0f, 7.0f, 42f, 120f),
    KH101("kh101", "KH101", 7.45f, 0.742f, 2400f, 400f, 0.30f, 5.5f, 6.5f, 26f, 90f),
    KH55("kh55", "KH55", 6.04f, 0.514f, 1500f, 410f, 0.30f, 5.5f, 6.0f, 26f, 85f),
    RDS1("rds1", "RDS1", 3.25f, 1.52f, 4700f, 2300f, 0.22f, 0f, 0f, 0f, 0f),
    AN602("an602", "AN602", 8.00f, 2.10f, 26500f, 8000f, 0.25f, 0f, 0f, 0f, 0f);

    public final String id, node;
    public final float length, diameter, mass, explosive, cd, craterRadius, blastPower, damageRadius, maxDamage;

    BombType(String id, String node, float length, float diameter, float mass, float explosive, float cd,
             float craterRadius, float blastPower, float damageRadius, float maxDamage) {
        this.id = id;
        this.node = node;
        this.length = length;
        this.diameter = diameter;
        this.mass = mass;
        this.explosive = explosive;
        this.cd = cd;
        this.craterRadius = craterRadius;
        this.blastPower = blastPower;
        this.damageRadius = damageRadius;
        this.maxDamage = maxDamage;
    }

    /** Arsenal unit released instead of a {@code BombEntity} (null for FAB bombs). */
    public ru.aiforge.arsenal.core.Kind arsenal() {
        return switch (this) {
            case KH101 -> ru.aiforge.arsenal.core.Kind.KH101;
            case KH55 -> ru.aiforge.arsenal.core.Kind.KH55;
            case RDS1 -> ru.aiforge.arsenal.core.Kind.RDS1;
            case AN602 -> ru.aiforge.arsenal.core.Kind.AN602;
            default -> null;
        };
    }

    public boolean missile() {
        return this == KH101 || this == KH55;
    }

    public boolean nuclear() {
        return this == RDS1 || this == AN602;
    }

    /** GLB file holding the node for bay rendering. */
    public String glb() {
        return switch (this) {
            case KH101, KH55 -> "cruise";
            case RDS1, AN602 -> "nukes";
            default -> "bombs";
        };
    }

    /** drag area / mass, 1/m (ballistic coefficient inverse) */
    public float dragK() {
        float a = (float) (Math.PI * diameter * diameter / 4);
        return 0.5f * cd * a / mass;
    }

    public static BombType byIndex(int i) {
        BombType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }

    public String displayName() {
        return switch (this) {
            case FAB250 -> "FAB-250";
            case FAB500 -> "FAB-500";
            case FAB1500 -> "FAB-1500";
            case KH101 -> "Kh-101";
            case KH55 -> "Kh-55";
            case RDS1 -> "RDS-1";
            case AN602 -> "AN602";
        };
    }
}
