package ru.aiforge.realwar.handheld;

/** The shoulder-fired weapons and their ballistics/warheads (speeds in blocks per tick, 1 block = 1 m). */
public enum HandheldKind {
    //          id         ammo              lockTicks cone(deg) range  speed  turn(deg/t) life  tntKg frag  direct
    RPG7("rpg7", "pg7v", 0, 0f, 500, 4.6, 0f, 125, 1.0, 0.3, 90f),
    IGLA("igla", "igla_missile", 30, 5f, 1200, 7.0, 11f, 220, 1.2, 0.9, 70f),
    JAVELIN("javelin", "javelin_missile", 50, 3.5f, 600, 3.4, 7f, 320, 9.0, 0.4, 260f),
    /** r12 BGM-71F TOW-2B from the Bradley turret: SACLOS (rides the gunner's sight line), 3750 m wire, ~280 m/s. */
    TOW("tow", "tow_missile", 1, 0f, 3750, 5.5, 2.5f, 340, 6.0, 0.4, 300f),
    /** r25 FIM-92 Stinger: Mach 2.2 (faster than the Igla-S in game scale), 3 kg HE-frag (1 kg filler), hit-to-kill
     *  impact fuze (2.5 m tolerance for the hitbox), self-destruct at 17 s in real life (12 s here) */
    STINGER("stinger", "stinger_missile", 24, 4.5f, 1000, 7.6, 12f, 240, 1.0, 1.0, 60f);

    public final String id, ammo;
    public final int lockTicks, range, life;
    public final float cone, turn, direct;
    public final double speed, tntKg, frag;

    HandheldKind(String id, String ammo, int lockTicks, float cone, int range, double speed, float turn, int life, double tntKg, double frag, float direct) {
        this.id = id;
        this.ammo = ammo;
        this.lockTicks = lockTicks;
        this.cone = cone;
        this.range = range;
        this.speed = speed;
        this.turn = turn;
        this.life = life;
        this.tntKg = tntKg;
        this.frag = frag;
        this.direct = direct;
    }

    /** IR homing surface-to-air missiles (Igla-S, Stinger) */
    public boolean air() {
        return this == IGLA || this == STINGER;
    }

    public boolean guided() {
        return this.lockTicks > 0;
    }

    public static HandheldKind byOrdinal(int i) {
        HandheldKind[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
