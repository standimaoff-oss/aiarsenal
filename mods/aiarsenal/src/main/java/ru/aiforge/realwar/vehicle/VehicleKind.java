/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 */
package ru.aiforge.realwar.vehicle;

import ru.aiforge.realwar.vehicle.Specs;
import ru.aiforge.realwar.vehicle.VehicleSpec;
import net.minecraft.resources.ResourceLocation;

public enum VehicleKind {
    GRAD(Specs.GRAD, 200, 13700.0f, 132.0f, 75.0f, 1.6f, 40, 14, 4, 70.0f, 5.0f, 55.0f, 3.0f, 11.0f, 18.0f),
    SMERCH(Specs.SMERCH, 300, 43700.0f, 382.0f, 60.0f, 1.0f, 12, 50, 10, 30.0f, 5.0f, 55.0f, 7.6f, 20.0f, 280.0f),
    S400(Specs.S400, 300, 41000.0f, 382.0f, 60.0f, 1.0f, 4, 110, 40, 0.0f, 0.0f, 88.0f, 7.6f, 12.0f, 180.0f),
    ZU23(Specs.ZU23, 150, 950.0f, 0.0f, 0.0f, 0.0f, 100, 30, 1, 180.0f, -5.0f, 90.0f, 0.0f, 0.0f, 0.0f),
    RADAR_MOBILE(Specs.RADAR_MOBILE, 250, 38000.0f, 382.0f, 60.0f, 1.0f, 0, 100, 40, 0.0f, 0.0f, 88.0f, 7.6f, 0.0f, 0.0f),
    RADAR_POST(Specs.RADAR_POST, 400, 0.0f, 0.0f, 0.0f, 0.0f, 0, 100, 40, 0.0f, 0.0f, 88.0f, 0.0f, 0.0f, 0.0f),
    /** RS-24 Yars: ~120 t, YaMZ-847 588 kW, 45 km/h; one ICBM in the TPK, reload 10 s (crane crew abstraction). */
    YARS(Specs.YARS, 450, 120000.0f, 588.0f, 45.0f, 0.5f, 1, 200, 100, 0.0f, 0.0f, 90.0f, 22.0f, 0.0f, 1200.0f),
    /** T-90M: 46.5 t, V-92S2F 1130 hp, 60 km/h; carousel autoloader 22 rounds, 7 s cycle; 2A46M-4 -5..+14 deg. */
    T90M(Specs.T90M, 600, 46500.0f, 830.0f, 60.0f, 1.6f, 22, 40, 140, 180.0f, -5.0f, 14.0f, 4.75f, 0.0f, 3.4f),
    /** M1A2 SEPv3: 66.8 t, AGT1500 1500 hp, 67 km/h; 34 rounds in the bustle, human loader ~6 s; M256 -9..+20 deg. */
    ABRAMS(Specs.ABRAMS, 650, 66800.0f, 1120.0f, 67.0f, 1.4f, 34, 30, 120, 180.0f, -9.0f, 20.0f, 4.65f, 0.0f, 1.6f),
    /** BMP-3: 18.7 t, UTD-29 500 hp, 70 km/h; 2A70 100 mm (40 rds, 10 rds/min) + 2A72 30 mm; -6..+60 deg. */
    BMP3(Specs.BMP3, 300, 18700.0f, 368.0f, 70.0f, 2.0f, 40, 30, 120, 180.0f, -6.0f, 60.0f, 3.25f, 0.0f, 1.6f),
    /** 2S19 Msta-S: 42 t, V-84A 840 hp, 60 km/h; 2A64 152 mm, 50 rounds, 6-8 rds/min; -4..+68 deg, 360 traverse. */
    MSTA(Specs.MSTA, 400, 42000.0f, 618.0f, 60.0f, 1.3f, 50, 30, 120, 180.0f, -4.0f, 68.0f, 7.3f, 0.0f, 7.8f),
    /** D-30 (2A18) 122 mm towed howitzer on a three-trail carriage: 3.2 t, 360 traverse, -7..+70 deg, 7-8 rds/min. */
    D30(Specs.D30, 150, 3210.0f, 0.0f, 0.0f, 0.0f, 6, 24, 160, 180.0f, -7.0f, 70.0f, 4.35f, 0.0f, 3.6f),
    /** M777A2 155 mm lightweight towed howitzer: 4.2 t, +-22.5 deg on-carriage traverse, -3..+71.7 deg, 2-5 rds/min. */
    M777(Specs.M777, 150, 4200.0f, 0.0f, 0.0f, 0.0f, 6, 30, 200, 22.5f, -3.0f, 71.7f, 5.95f, 0.0f, 10.8f),
    // ---- WW2 (reply 8): real masses / engines / speeds / gun limits; ready rounds abstract the loader cycle.
    /** Pz.Kpfw. II Ausf. F: 9.5 t, Maybach HL 62 140 PS, 40 km/h; 2 cm KwK 30 (10-rd magazines) + MG 34; -9.5..+20 deg. */
    PZ2(Specs.PZ2, 220, 9500.0f, 103.0f, 40.0f, 1.5f, 10, 12, 6, 180.0f, -9.5f, 20.0f, 1.1f, 0.0f, 0.006f),
    /** Pz.Kpfw. IV Ausf. H: 25 t, HL 120 TRM 300 PS, 38 km/h; 7.5 cm KwK 40 L/48, 87 rds; -8..+20 deg. */
    PZ4(Specs.PZ4, 380, 25000.0f, 221.0f, 38.0f, 1.2f, 8, 30, 110, 180.0f, -8.0f, 20.0f, 3.6f, 0.0f, 0.69f),
    /** Tiger I Ausf. E: 57 t, HL 230 P45 700 PS, 45 km/h; 8.8 cm KwK 36 L/56, 92 rds; -9..+10 deg; hydraulic traverse 6 deg/s. */
    TIGER(Specs.TIGER, 560, 57000.0f, 515.0f, 45.0f, 0.9f, 8, 35, 130, 180.0f, -9.0f, 10.0f, 4.93f, 0.0f, 0.9f),
    /** Sturmtiger: 65 t, 38 cm RW 61 rocket mortar, 14 rounds, crane reload ~10 min (abstracted 20 s); 0..85 deg, +-10 traverse. */
    STURMTIGER(Specs.STURMTIGER, 600, 65000.0f, 515.0f, 40.0f, 0.8f, 1, 400, 60, 10.0f, 0.0f, 85.0f, 2.05f, 0.0f, 135.0f),
    /** Pz.Kpfw. VIII Maus: 188 t, DB 603 1080 kW-class (1250 PS), 20 km/h; 12.8 cm KwK 44 L/55 + coaxial 7.5 cm KwK 44 L/36.5. */
    MAUS(Specs.MAUS, 900, 188000.0f, 883.0f, 20.0f, 0.55f, 6, 45, 180, 180.0f, -7.0f, 23.0f, 7.0f, 0.0f, 3.6f),
    /** Landkreuzer P.1000 Ratte (project): 1000 t, 2x MAN V12Z32/44 diesel 2x 8500 PS, 40 km/h; 2x 28 cm SK C/34. */
    RATTE(Specs.RATTE, 3000, 1000000.0f, 12500.0f, 40.0f, 0.45f, 2, 160, 50, 180.0f, -5.0f, 40.0f, 15.26f, 0.0f, 16.9f),
    /** T-34-85 (1944): 32 t, V-2-34 500 hp, 55 km/h; 85 mm ZiS-S-53, 55-60 rds; -5..+22 deg. */
    T34(Specs.T34, 420, 32000.0f, 368.0f, 55.0f, 1.5f, 8, 30, 110, 180.0f, -5.0f, 22.0f, 4.645f, 0.0f, 0.74f),
    /** KV-2: 52 t, V-2K 600 hp, 34 km/h; 152 mm M-10T (separate loading, 36 rds, ~2 rds/min); -3..+18 deg. */
    KV2(Specs.KV2, 620, 52000.0f, 441.0f, 34.0f, 0.9f, 4, 60, 220, 180.0f, -3.0f, 18.0f, 3.7f, 0.0f, 6.0f),
    /** IS-2 (1944): 46 t, V-2-IS 520 hp, 37 km/h; 122 mm D-25T (28 rds, 2-3 rds/min); -3..+20 deg. */
    IS2(Specs.IS2, 650, 46000.0f, 382.0f, 37.0f, 1.0f, 4, 70, 240, 180.0f, -3.0f, 20.0f, 5.23f, 0.0f, 3.6f),
    /** KV-5 (Obj. 225 project): 100 t, 2x V-2 1200 hp, ~40 km/h; 107 mm ZiS-6; -5..+15 deg. */
    KV5(Specs.KV5, 900, 100000.0f, 883.0f, 40.0f, 0.7f, 6, 40, 150, 180.0f, -5.0f, 15.0f, 5.6f, 0.0f, 2.2f),
    /** BM-13N Katyusha on Studebaker US6: 7.2 t, Hercules JXD 95 hp, 69 km/h; 16x M-13 132 mm in 7-10 s, 4..45 deg, +-10 traverse. */
    BM13(Specs.BM13, 160, 7200.0f, 71.0f, 69.0f, 1.4f, 16, 30, 10, 10.0f, 4.0f, 45.0f, 5.0f, 12.0f, 4.9f),
    // ---- reply 12 (append only: ordinal-indexed arrays / saves)
    /** M2A3 Bradley: 30.4 t, Cummins VTA-903T 600 hp, 61 km/h; M242 25 mm (dual feed HEI-T / APFSDS-T, 200 rds/min,
     *  60 ready rounds per box section) + M240C coax + twin TOW-2B launcher (7 missiles); -10..+60 deg. */
    BRADLEY(Specs.BRADLEY, 420, 30400.0f, 447.0f, 61.0f, 1.5f, 60, 3, 6, 180.0f, -10.0f, 60.0f, 2.6f, 0.0f, 0.03f),
    /** TOS-1A Solntsepyok: 44.3 t (T-72A chassis), V-84 840 hp, 60 km/h; 24 x 220 mm MO.1.01.04M thermobaric rockets,
     *  salvo 0.5 s per rocket, launcher +-30 deg / 0..+65 deg, reload by the TZM crane ~40 s per rocket. */
    TOS1A(Specs.TOS1A, 560, 44300.0f, 618.0f, 60.0f, 1.3f, 24, 40, 5, 30.0f, 0.0f, 65.0f, 4.1f, 25.0f, 90.0f),
    /** Ural-4320-31: 6x6, 8.6 t curb / 15.2 t gross (troops: ~12 t), YaMZ-238M2 240 hp, 85 km/h; 2 cab + 12 bench seats. */
    URAL(Specs.URAL, 180, 12000.0f, 176.0f, 85.0f, 1.3f, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
    /** British Mark I Male (1916): 28.4 t, 78 kW, 6.4 km/h, two independently loaded QF 6-pdr sponson guns. */
    MARK_I(Specs.MARK_I, 420, 28400.0f, 78.0f, 6.4f, 0.55f, 2, 100, 60, 45.0f, -10.0f, 20.0f, 2.23f, 0.0f, 0.42f);

    public final VehicleSpec spec;
    public final int hp;
    public final float massKg;
    public final float powerKw;
    public final float vmaxKmh;
    public final float maxAccel;
    public final int magazine;
    public final int reloadTicks;
    public final int fireInterval;
    public final float traverse;
    public final float elMin;
    public final float elMax;
    public final float tubeLen;
    public final float backblast;
    public final float warheadKg;

    private VehicleKind(VehicleSpec spec, int hp, float massKg, float powerKw, float vmaxKmh, float maxAccel, int magazine, int reloadTicks, int fireInterval, float traverse, float elMin, float elMax, float tubeLen, float backblast, float warheadKg) {
        this.spec = spec;
        this.hp = hp;
        this.massKg = massKg;
        this.powerKw = powerKw;
        this.vmaxKmh = vmaxKmh;
        this.maxAccel = maxAccel;
        this.magazine = magazine;
        this.reloadTicks = reloadTicks;
        this.fireInterval = fireInterval;
        this.traverse = traverse;
        this.elMin = elMin;
        this.elMax = elMax;
        this.tubeLen = tubeLen;
        this.backblast = backblast;
        this.warheadKg = warheadKg;
    }

    public boolean drivable() {
        return this.powerKw > 0.0f;
    }

    public boolean isSam() {
        return this == S400;
    }

    public boolean selfRadar() {
        return false;
    }

    public boolean isRadar() {
        return this == RADAR_MOBILE || this == RADAR_POST;
    }

    public boolean isGun() {
        return this.ordinal() >= T90M.ordinal() && this != BM13 && this != URAL;
    }

    public boolean tracked() {
        return this == T90M || this == ABRAMS || this == BMP3 || this == MSTA || this == PZ2 || this == PZ4 || this == TIGER
                || this == STURMTIGER || this == MAUS || this == RATTE || this == T34 || this == KV2 || this == IS2 || this == KV5
                || this == BRADLEY || this == TOS1A || this == MARK_I;
    }

    public boolean artillery() {
        return this == MSTA || this == D30 || this == M777 || this == STURMTIGER || this == TOS1A;
    }

    /** Second World War vehicle (reply 8): MG coax instead of the 30 mm, WW2 sights in the HUD. */
    public boolean ww2() {
        return this.ordinal() >= PZ2.ordinal() && this.ordinal() <= BM13.ordinal();
    }

    public boolean isMlrs() {
        return this == GRAD || this == SMERCH || this == BM13;
    }

    public boolean isFlak() {
        return this == ZU23;
    }

    public ResourceLocation model() {
        return this.spec.model();
    }

    public String id() {
        return this.spec.id();
    }

    public float fireAxisZ() {
        return this == SMERCH ? -1.0f : 1.0f;   // BM-21 fires forward over the cab (cab shields), Smerch to the rear
    }

    // -------------------------------------------------------------------------------- Mark I sponsons
    /** Traverse of one QF 6-pdr sponson about its outward rest, degrees (real Mark I: a sector per sponson). */
    public float sponsonArc() {
        return this == MARK_I ? 35.0f : 0.0f;
    }

    /** Rest azimuth of a sponson in hull degrees (0 = bow): tube 0 = right sponson (-x), tube 1 = left (+x). */
    public float sponsonRest(int tube) {
        return tube == 0 ? -90.0f : 90.0f;
    }

    /**
     * The azimuth one sponson can actually reach: the shared aim line clamped to its historical sector, so the
     * barrel never swings along the hull side. Pose, ballistics and the barrel collider all use this.
     */
    public float sponsonAz(int tube, float az) {
        if (this != MARK_I) return az;
        float rest = sponsonRest(tube);
        float w = wrapSign(az - rest);
        return rest + Math.max(-sponsonArc(), Math.min(sponsonArc(), w));
    }

    private static float wrapSign(float d) {
        float w = d % 360.0f;
        if (w > 180.0f) w -= 360.0f;
        if (w < -180.0f) w += 360.0f;
        return w;
    }

    /** Elevation sector of the sponson guns (real Hotchkiss mount: -10..+20 deg). */
    public float sponsonEl(float el) {
        return this == MARK_I ? Math.max(-10.0f, Math.min(20.0f, el)) : el;
    }
}

