package ru.aiforge.realwar.handheld;

/**
 * r22 small arms, real data:
 *  AK-12 (6P70, 2018): 5.45x39, 945 mm (stock extended), barrel 415 mm, 700 rds/min, 900 m/s, 6L31 30-rd magazine.
 *  M4A1: 5.56x45, 838 mm, barrel 368 mm (14.5"), 700-950 rds/min, 880-910 m/s, STANAG 30-rd, Aimpoint CompM4.
 *  RPG-7V2: 40 mm tube, PG-7VL 93 mm HEAT (~600 mm RHA), 6.3 kg, 200 m point-blank / 500 m aimed with the PGO-7V3.
 * Speeds in m/s (1 block = 1 m); spreads are cone half-angles in radians; recoil in degrees per shot; reload in ticks.
 */
public enum GunKind {
    //    id      ammo item     cap  rpm  m/s   dmg   pen   hip     ads      rcP    rcY   rld rldE adsFov sound       auto
    AK12("ak12", "mag_ak12", 30, 700, 900, 7.5f, 7.0f, 0.030f, 0.0016f, 0.95f, 0.40f, 50, 63, 0.80f, "rifle_ak", true),
    M4A1("m4a1", "mag_stanag", 30, 800, 890, 7.0f, 6.0f, 0.028f, 0.0012f, 0.80f, 0.35f, 47, 53, 0.70f, "rifle_m4", true),
    RPG7("rpg7", "pg7v", 1, 0, 0, 0f, 0f, 0.020f, 0.0040f, 4.0f, 1.0f, 70, 70, 0.25f, "", false),     // PGO-7V3 4x (irons 0.8)
    // r23: ORSIS T-5000M .338 LM (bolt action, 700 mm barrel, 5 rds, ~905 m/s, 1 MOA) and PKP Pecheneg (7.62x54R,
    // 650 rds/min, 825 m/s, 100-rd belt box, full-auto only, open bolt)
    T5000("t5000", "mag_t5000", 5, 40, 905, 24f, 18f, 0.060f, 0.0003f, 4.5f, 0.9f, 60, 76, 0.125f, "rifle_t5000", false),
    PKP("pkp", "box_pkp", 100, 650, 825, 11f, 10f, 0.045f, 0.0025f, 1.05f, 0.55f, 120, 130, 0.80f, "mg_pkp", true),
    // r24: 12-gauge 00 buckshot (9 x 8.4 mm pellets, ~400 m/s). Damage / pen are per pellet; reload of the R870 is per
    // shell (13 ticks; 24 for the first one through the ejection port + pump). Saiga-12K: gas semi-auto, 8-rd box.
    SAIGA12("saiga12", "mag_saiga12", 8, 300, 400, 2.6f, 1.5f, 0.022f, 0.006f, 3.0f, 0.8f, 50, 63, 0.85f, "shotgun_saiga", false),
    R870("r870", "shell_12", 4, 70, 400, 2.6f, 1.5f, 0.022f, 0.006f, 3.4f, 0.9f, 13, 24, 0.85f, "shotgun_870", false),
    // r25 MANPADS: one missile per launch tube; the gripstock is moved onto a fresh tube (5 s). Hold RMB on the target:
    // the seeker cools (Igla-S 1.5 s, Stinger 1 s), then locks (tone); LMB launches only when locked.
    IGLA("igla", "igla_missile", 1, 0, 0, 0f, 0f, 0.020f, 0.006f, 2.0f, 0.6f, 100, 100, 0.80f, "", false),
    STINGER("stinger", "stinger_missile", 1, 0, 0, 0f, 0f, 0.020f, 0.006f, 2.0f, 0.6f, 100, 100, 0.80f, "", false),
    // r26 WW2: PPSh-41 (7.62x25 TT, 843 mm, 71-rd drum, ~1000 rds/min in game, 488 m/s, open bolt, selector in the
    // trigger guard); MP 40 (9x19, 833 mm stock extended, 32-rd box, 550 rds/min, ~400 m/s, open bolt, full-auto only);
    // Mosin 91/30 (7.62x54R, 1232 mm, 865 m/s, 5-rd stripper clip) and Kar98k (7.92x57, 1110 mm, 760 m/s, 5-rd clip):
    // manual bolts loaded through the open action from a stripper clip once the magazine is empty.
    PPSH41("ppsh41", "mag_ppsh", 71, 1000, 488, 5.0f, 2.5f, 0.032f, 0.0035f, 0.55f, 0.35f, 70, 84, 0.85f, "smg_ppsh", true),
    MP40("mp40", "mag_mp40", 32, 550, 400, 5.2f, 2.5f, 0.030f, 0.0030f, 0.50f, 0.30f, 50, 62, 0.85f, "smg_mp40", true),
    MOSIN("mosin", "clip_mosin", 5, 30, 865, 16f, 11f, 0.055f, 0.0006f, 3.6f, 0.8f, 50, 50, 0.75f, "rifle_mosin", false),
    K98K("k98k", "clip_k98", 5, 30, 760, 16f, 11f, 0.055f, 0.0006f, 3.4f, 0.8f, 46, 46, 0.75f, "rifle_k98", false),
    // Append only: existing GunFxPacket ordinals must remain stable.
    MOSIN_PU("mosin_pu", "round_762x54r", 5, 30, 865, 16f, 11f, 0.055f, 0.00045f, 3.6f, 0.8f, 24, 32, 1f / 3.5f, "rifle_mosin", false),
    K98K_ZF39("k98k_zf39", "round_792x57", 5, 30, 760, 16f, 11f, 0.055f, 0.00045f, 3.4f, 0.8f, 24, 32, 0.25f, "rifle_k98", false),
    C96("c96", "clip_c96", 10, 240, 425, 5.0f, 2.2f, 0.040f, 0.0040f, 1.5f, 0.5f, 60, 60, 0.85f, "pistol_c96", false),
    TT33("tt33", "mag_tt33", 8, 240, 420, 5.0f, 2.2f, 0.040f, 0.0040f, 1.4f, 0.5f, 38, 48, 0.85f, "pistol_tt33", false),
    MUSKET_1777("musket_1777", "paper_cartridge", 1, 3, 420, 18f, 2.0f, .085f, .012f, 4.0f, 1.1f, 160, 160, .82f, "musket_fire", false),
    MUSKET_1808("musket_1808", "paper_cartridge", 1, 3, 400, 18f, 2.0f, .085f, .013f, 4.0f, 1.1f, 160, 160, .82f, "musket_fire", false),
    PISTOL_AN13("pistol_an13", "pistol_cartridge", 1, 4, 240, 11f, 1f, .10f, .025f, 3.0f, 1.0f, 120, 120, .88f, "flintlock_fire", false),
    PISTOL_1809("pistol_1809", "pistol_cartridge", 1, 4, 230, 11f, 1f, .10f, .025f, 3.0f, 1.0f, 120, 120, .88f, "flintlock_fire", false),
    // Append-only WWI rifle: same 5-round stripper clip and 7.92x57 mm cartridge as the later Kar98k.
    GEWEHR98("gewehr98", "clip_k98", 5, 30, 760, 16f, 11f, 0.055f, 0.0006f, 3.4f, 0.8f, 46, 46, 0.75f, "rifle_k98", false);

    public final String id, ammo, sound;
    public final int cap, rpm, reload, reloadEmpty;
    public final float speed, damage, pen, hipSpread, adsSpread, recoilPitch, recoilYaw, adsFov;
    public final boolean auto;

    GunKind(String id, String ammo, int cap, int rpm, float speed, float damage, float pen, float hip, float ads, float rcP, float rcY,
            int reload, int reloadEmpty, float adsFov, String sound, boolean auto) {
        this.id = id;
        this.ammo = ammo;
        this.cap = cap;
        this.rpm = rpm;
        this.speed = speed;
        this.damage = damage;
        this.pen = pen;
        this.hipSpread = hip;
        this.adsSpread = ads;
        this.recoilPitch = rcP;
        this.recoilYaw = rcY;
        this.reload = reload;
        this.reloadEmpty = reloadEmpty;
        this.adsFov = adsFov;
        this.sound = sound;
        this.auto = auto;
    }

    public boolean launcher() {
        return this == RPG7 || this.manpads();
    }

    /** r25 IR-homing shoulder-launched SAMs (Igla-S, Stinger) */
    public boolean manpads() {
        return this == IGLA || this == STINGER;
    }

    /** the munition this launcher fires */
    public HandheldKind missile() {
        return this == IGLA ? HandheldKind.IGLA : this == STINGER ? HandheldKind.STINGER : HandheldKind.RPG7;
    }

    /** manual bolt: one aimed shot, then the bolt is cycled (~1.3 s) */
    public boolean boltAction() {
        return this == T5000 || this == MOSIN || this == K98K || this == GEWEHR98 || this.singleLoad();
    }

    /** r26: WW2 bolt rifles charged with a 5-round stripper clip through the open action (only when empty) */
    public boolean clipFed() {
        return this == MOSIN || this == K98K || this == GEWEHR98 || this == C96;
    }

    /** rounds one ammo item adds (a stripper clip holds 5) */
    public int roundsPerItem() {
        return this.clipFed() ? this.cap : 1;
    }

    /** r26 open-bolt submachine guns (no round stays chambered on a reload) */
    public boolean openBolt() {
        return this == PPSH41 || this == MP40;
    }

    /** fire selector (B): AK-12 / M4A1; the PKP is full-auto only */
    public boolean selector() {
        return this.auto && this != PKP && this != MP40;
    }

    /** closed-bolt guns keep a round in the chamber on a tactical reload; the belt-fed PKP fires from an open bolt */
    public boolean closedBolt() {
        return !this.flintlock() && !this.launcher() && this != PKP && !this.openBolt() && !this.clipFed() && !this.singleLoad();
    }

    /** variable-power riflescope overlay (T-5000: 5-25x) */
    public boolean scope() {
        return this == T5000 || this.singleLoad();
    }

    /** Scopes obstruct stripper clips: load individual rounds into the internal magazine. */
    public boolean singleLoad() { return this == MOSIN_PU || this == K98K_ZF39; }
    public boolean incrementalReload() { return this.tube() || this.singleLoad(); }
    public boolean pistol() { return this == C96 || this == TT33 || this == PISTOL_AN13 || this == PISTOL_1809; }
    public boolean flintlock() { return this == MUSKET_1777 || this == MUSKET_1808 || this == PISTOL_AN13 || this == PISTOL_1809; }
    public float fixedMagnification() { return this == MOSIN_PU ? 3.5f : this == K98K_ZF39 ? 4f : 1f; }

    /** rifles keep one round in the chamber on a tactical reload (30 + 1) */
    public int maxLoaded(boolean chambered) {
        return this.closedBolt() ? this.cap + (chambered ? 1 : 0) : this.cap;
    }

    /** pellets per shot (00 buckshot) */
    public int pellets() {
        return this == SAIGA12 || this == R870 ? 9 : 1;
    }

    /** pellet cone half-angle (rad): cylinder bore ~1 m pattern at 25 m */
    public float choke() {
        return this == SAIGA12 ? 0.040f : this == R870 ? 0.034f : 0f;
    }

    /** tube magazine loaded one loose round at a time (Remington 870) */
    public boolean tube() {
        return this == R870;
    }

    /** pump action: the pump is cycled after every shot */
    public boolean pumpAction() {
        return this == R870;
    }

    /** manually cycled after each shot (bolt or pump) */
    public boolean manual() {
        return this.boltAction() || this.pumpAction();
    }

    /** reloaded from loose rounds (PG-7VL grenades, 12 ga shells) instead of magazines */
    public boolean looseAmmo() {
        return this.flintlock() || this.launcher() || this.tube() || this.clipFed() || this.singleLoad();
    }

    /** ticks between two semi-automatic shots */
    public int semiCooldown() {
        return this.launcher() ? 10 : this.boltAction() ? 26 : this.pumpAction() ? 14 : this == SAIGA12 ? 4 : 2;
    }

    /** shots per tick at the cyclic rate */
    public float perTick() {
        return this.rpm / 1200f;
    }

    public static GunKind byOrdinal(int i) {
        GunKind[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
