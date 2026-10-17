package ru.aiforge.tubombers.aircraft;

import ru.aiforge.tubombers.glb.GlbModels;

/**
 * Static data of both bombers. Units are real (kg, m, m^2, N, W). Geometry uses the model space of the GLB:
 * x = aircraft left, y = up, z = forward (nose +z), origin on the ground under the main gear axle.
 */
public enum AircraftType {
    TU95("tu95ms", GlbModels.TU95, "Tu95MS", false,
            140_000, 289.9f, 8.39f, 8.39f, 0.80f,
            0.30f, 5.0f, 5.0f, 1.35f, 1.35f, 0.60f, 14f,
            0.021f, 0.018f, 0.035f, 0.008f, 0.78f, 0.78f,
            4 * 11_033_000f, 500_000f, 0f, 1.2f,
            6f, 18f, 4f, 2.5f, 9f, 400f,
            new Bay[]{new Bay(0.92f, 2.42f, 4.10f, -5.35f, 5.35f)},
            new int[]{36, 16, 6, 6, 6, 1, 1}, 8),
    TU160("tu160", GlbModels.TU160, "Tu160", true,
            210_000, 360f, 7.8f, 3.0f, 0.82f,
            0.15f, 5.0f, 3.0f, 1.30f, 0.85f, 0.75f, 15f,
            0.016f, 0.020f, 0.040f, 0.010f, 0.85f, 0.95f,
            4 * 137_300f, 4 * 137_300f, 4 * 245_000f, 2.0f,
            8f, 30f, 5f, 2.0f, 11f, 380f,
            new Bay[]{new Bay(0.92f, 2.80f, 4.90f, 2.35f, 13.35f), new Bay(0.92f, 2.80f, 4.90f, -11.75f, -0.75f)},
            new int[]{72, 32, 8, 12, 12, 2, 0}, 6);

    /** A bomb bay volume in model space. */
    public record Bay(float xHalf, float yBottom, float yTop, float z0, float z1) {
    }

    public final String id, glb, rootNode;
    public final boolean swingWing;
    public final float mass, wingArea, aspectSpread, aspectSwept, oswald;
    public final float cl0, clAlphaSpread, clAlphaSwept, clMaxSpread, clMaxSwept, clFlaps, stallAlphaDeg;
    public final float cd0, cdGear, cdFlaps, cdBay, mCritSpread, mCritSwept;
    /** turboprop: shaft power (W) and static thrust cap (N); jet: dry thrust (N), afterburner thrust (N). */
    public final float power, thrustStatic, thrustAfterburner, spoolTime;
    public final float maxPitchRate, maxRollRate, maxYawRate, maxG, tailStrikeDeg, health;
    public final Bay[] bays;
    /** max number of FAB-250 / FAB-500 / FAB-1500 */
    public final int[] capacity;
    public final int maxCrew;

    AircraftType(String id, String glb, String rootNode, boolean swingWing,
                 float mass, float wingArea, float aspectSpread, float aspectSwept, float oswald,
                 float cl0, float clAlphaSpread, float clAlphaSwept, float clMaxSpread, float clMaxSwept, float clFlaps, float stallAlphaDeg,
                 float cd0, float cdGear, float cdFlaps, float cdBay, float mCritSpread, float mCritSwept,
                 float power, float thrustStatic, float thrustAfterburner, float spoolTime,
                 float maxPitchRate, float maxRollRate, float maxYawRate, float maxG, float tailStrikeDeg, float health,
                 Bay[] bays, int[] capacity, int maxCrew) {
        this.id = id;
        this.glb = glb;
        this.rootNode = rootNode;
        this.swingWing = swingWing;
        this.mass = mass;
        this.wingArea = wingArea;
        this.aspectSpread = aspectSpread;
        this.aspectSwept = aspectSwept;
        this.oswald = oswald;
        this.cl0 = cl0;
        this.clAlphaSpread = clAlphaSpread;
        this.clAlphaSwept = clAlphaSwept;
        this.clMaxSpread = clMaxSpread;
        this.clMaxSwept = clMaxSwept;
        this.clFlaps = clFlaps;
        this.stallAlphaDeg = stallAlphaDeg;
        this.cd0 = cd0;
        this.cdGear = cdGear;
        this.cdFlaps = cdFlaps;
        this.cdBay = cdBay;
        this.mCritSpread = mCritSpread;
        this.mCritSwept = mCritSwept;
        this.power = power;
        this.thrustStatic = thrustStatic;
        this.thrustAfterburner = thrustAfterburner;
        this.spoolTime = spoolTime;
        this.maxPitchRate = maxPitchRate;
        this.maxRollRate = maxRollRate;
        this.maxYawRate = maxYawRate;
        this.maxG = maxG;
        this.tailStrikeDeg = tailStrikeDeg;
        this.health = health;
        this.bays = bays;
        this.capacity = capacity;
        this.maxCrew = maxCrew;
    }

    public boolean turboprop() {
        return this == TU95;
    }

    public float maxThrottle() {
        return thrustAfterburner > 0 ? 1.25f : 1.0f;
    }

    public String displayKey() {
        return "entity.aiarsenal." + id;
    }
}
