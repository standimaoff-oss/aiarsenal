package ru.aiforge.bm21grad.ammo;

/**
 * 122-mm rocket types of the BM-21 (index 0 = empty tube).
 * band = RGB of the identification band painted on the warhead (drives the GLB material tint).
 */
public enum RocketType {
    NONE("none", 0, 0f, 0f, 0f, 0, false),
    /** 9M22U - high-explosive fragmentation, the standard round. */
    HE_FRAG("9m22u", 0xB01C14, 1.00f, 4.2f, 8.0f, 70, true),
    /** 9M28F - high-explosive (blast) with a heavier charge: bigger crater, fewer fragments. */
    HE_BLAST("9m28f", 0x1C1C1C, 0.95f, 6.0f, 6.0f, 30, true),
    /** 9M22S - incendiary with thermite elements. */
    INCENDIARY("9m22s", 0xE07A12, 1.00f, 2.4f, 4.0f, 10, true),
    /** 9M218 - cluster rocket with 45 HEAT-frag submunitions, air-burst over the target. */
    CLUSTER("9m218", 0x2D6FD0, 0.90f, 1.6f, 3.0f, 0, false),
    /** 9M43 - smoke screen rocket. */
    SMOKE("9m43", 0xE8E8E0, 1.00f, 1.0f, 0f, 0, false);

    public final String id;
    public final int band;
    /** range multiplier (heavier warheads fly a bit shorter) */
    public final float rangeFactor;
    public final float blastRadius;
    public final float fragmentDamage;
    public final int fragments;
    public final boolean blockDamage;

    RocketType(String id, int band, float rangeFactor, float blastRadius, float fragmentDamage, int fragments, boolean blockDamage) {
        this.id = id;
        this.band = band;
        this.rangeFactor = rangeFactor;
        this.blastRadius = blastRadius;
        this.fragmentDamage = fragmentDamage;
        this.fragments = fragments;
        this.blockDamage = blockDamage;
    }

    public float[] bandRgb() {
        return new float[]{((band >> 16) & 255) / 255f, ((band >> 8) & 255) / 255f, (band & 255) / 255f, 1f};
    }

    public String itemName() {
        return "rocket_" + id;
    }

    public String translationKey() {
        return "rocket.aiarsenal." + id;
    }

    public static RocketType byIndex(int i) {
        RocketType[] v = values();
        return i >= 0 && i < v.length ? v[i] : NONE;
    }

    public static RocketType byId(String id) {
        for (RocketType t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }
}
