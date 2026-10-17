package ru.aiforge.arsenal.core;

/**
 * Every arsenal unit: GLB file + root node, real-world performance numbers (converted to world units with
 * {@code ArsenalConfig.SPEED_SCALE}) and gameplay parameters.
 * <p>Speeds are real m/s; warhead "power" is a vanilla explosion power, "dmg" the max overpressure damage.
 */
public enum Kind {
    // ---------------------------------------------------------------- recon / strike MALE drones
    MQ9("mq9", "recon", "MQ9", Cat.RECON, 60f, 1.6f, 0.9f, 87f, 0f, 0f, 4),
    ORION("orion", "recon", "ORION", Cat.RECON, 50f, 1.4f, 0.8f, 56f, 0f, 0f, 4),
    // ---------------------------------------------------------------- loitering munitions
    S136("shahed136", "shahed", "S136", Cat.SHAHED, 12f, 1.6f, 0.6f, 51f, 5.5f, 45f, 0),
    S131("shahed131", "shahed", "S131", Cat.SHAHED, 10f, 1.4f, 0.5f, 47f, 3.8f, 35f, 0),
    S238("shahed238", "shahed", "S238", Cat.SHAHED, 14f, 1.6f, 0.6f, 140f, 5.5f, 45f, 0),
    LIUTYI("liutyi", "shahed", "LIUTYI", Cat.SHAHED, 16f, 2.2f, 0.8f, 55f, 6.0f, 50f, 0),
    // ---------------------------------------------------------------- FPV kamikaze
    FPV7("fpv7", "fpv", "FPV7", Cat.FPV, 4f, 0.45f, 0.25f, 33f, 2.6f, 60f, 0),
    FPV10("fpv10", "fpv", "FPV10", Cat.FPV, 5f, 0.6f, 0.3f, 28f, 6.0f, 45f, 0),
    // ---------------------------------------------------------------- cruise missiles / small guided munitions
    KH101("kh101", "cruise", "KH101", Cat.MISSILE, 8f, 1.0f, 0.8f, 200f, 6.5f, 90f, 0),
    KH55("kh55", "cruise", "KH55", Cat.MISSILE, 8f, 0.8f, 0.6f, 215f, 6.0f, 85f, 0),
    KALIBR("kalibr", "cruise", "KALIBR", Cat.MISSILE, 8f, 0.8f, 0.6f, 240f, 6.0f, 85f, 0),
    TOMAHAWK("tomahawk", "cruise", "TOMAHAWK", Cat.MISSILE, 8f, 0.8f, 0.6f, 245f, 6.0f, 85f, 0),
    HELLFIRE("hellfire", "cruise", "HELLFIRE", Cat.MISSILE, 2f, 0.3f, 0.3f, 425f, 2.8f, 70f, 0),
    KAB50("kab50", "cruise", "KAB50", Cat.MISSILE, 2f, 0.4f, 0.4f, 120f, 3.5f, 55f, 0),
    // ---------------------------------------------------------------- strategic
    YARS("yars", "nukes", "YARS", Cat.ICBM, 30f, 2.0f, 2.0f, 2000f, 0f, 0f, 0),
    RDS1("rds1", "nukes", "RDS1", Cat.NUKE, 40f, 1.5f, 1.5f, 0f, 0f, 0f, 0),
    AN602("an602", "nukes", "AN602", Cat.NUKE, 60f, 2.1f, 2.1f, 0f, 0f, 0f, 0),
    // ---------------------------------------------------------------- launchers
    RAIL("shahed_rail", "shahed", "RAIL", Cat.LAUNCHER, 80f, 2.0f, 2.0f, 0f, 0f, 0f, 1),
    CRUISE_LAUNCHER("cruise_launcher", "cruise", "LAUNCHER", Cat.LAUNCHER, 200f, 3.0f, 3.0f, 0f, 0f, 0f, 4),
    YARS_TEL("yars_tel", "yarstel", "YARSTEL", Cat.LAUNCHER, 300f, 3.5f, 4.0f, 0f, 0f, 0f, 1);

    public enum Cat {RECON, SHAHED, FPV, MISSILE, ICBM, NUKE, LAUNCHER}

    public final String id, glb, root;
    public final Cat cat;
    public final float hp, width, height, speed, power, dmg;
    public final int slots;

    Kind(String id, String glb, String root, Cat cat, float hp, float width, float height, float speed, float power, float dmg,
         int slots) {
        this.id = id;
        this.glb = glb;
        this.root = root;
        this.cat = cat;
        this.hp = hp;
        this.width = width;
        this.height = height;
        this.speed = speed;
        this.power = power;
        this.dmg = dmg;
        this.slots = slots;
    }

    public static Kind byIndex(int i) {
        Kind[] v = values();
        return v[Math.floorMod(i, v.length)];
    }

    public static Kind byId(String id) {
        for (Kind k : values()) if (k.id.equals(id)) return k;
        return null;
    }

    /** Nuclear yield in kilotons (0 for conventional). */
    public float yieldKt() {
        return switch (this) {
            case RDS1 -> 22f;
            case AN602 -> 50000f;
            case YARS -> 550f;
            default -> 0f;
        };
    }

    /** Model lowest point below the origin (the model is lifted so it stands on the entity position). */
    public float lift() {
        return switch (this) {
            case MQ9, ORION -> 1.12f;
            case LIUTYI -> 0.70f;
            case S136, S238 -> 0.40f;
            case S131 -> 0.34f;
            case FPV7 -> 0.11f;
            case FPV10 -> 0.21f;
            case RDS1 -> 0.78f;
            case AN602 -> 1.05f;
            case YARS -> 0.98f;
            case KH101, KH55, KALIBR, TOMAHAWK -> 0.5f;
            case HELLFIRE, KAB50 -> 0.15f;
            default -> 0f;
        };
    }

    public boolean nuclear() {
        return yieldKt() > 0;
    }

    public String translationKey() {
        return "item.aiarsenal." + id;
    }
}
