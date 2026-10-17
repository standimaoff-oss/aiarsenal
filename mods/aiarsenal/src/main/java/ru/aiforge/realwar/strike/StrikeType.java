package ru.aiforge.realwar.strike;

import ru.aiforge.realwar.aircraft.AirBombType;

/**
 * Radio-called fire support (reply 19). Append-only: the ordinal travels in {@link StrikeRequestPacket}.
 * planes x bombsPerPlane bombs of {@code bomb}; lateral / along-track spacing gives the carpet footprint.
 */
public enum StrikeType {
    /** 2S19 Msta-S battery: 3 salvos x 6 guns, 152 mm 3OF45, CEP ~ 25 m at long range. */
    ARTILLERY(null, 0, 0, 60, 0, 0, 0f, 0f, 0f),
    /** Flight of four Su-34, eight FAB-500 each (MBD3-U6-68 racks, ripple release). */
    FAB500(StrikePlaneEntity.Kind.SU34, 4, 8, 180, 180, 160, 22f, 14f, 0f),
    /** Pair of Su-34, four FAB-1000 each. */
    FAB1000(StrikePlaneEntity.Kind.SU34, 2, 4, 240, 190, 160, 30f, 22f, 0f),
    /** Single Tu-95 with one FAB-5000 in the bomb bay. */
    FAB5000(StrikePlaneEntity.Kind.TU95, 1, 1, 400, 250, 150, 0f, 0f, 0f),
    /** Single Tu-160, one free-fall nuclear bomb (arsenal RDS-1 charge, 22 kt) - air burst or remote fuze. */
    NUCLEAR(StrikePlaneEntity.Kind.TU160, 1, 1, 1200, 320, 230, 0f, 0f, 0f);

    public final StrikePlaneEntity.Kind plane;
    public final int planes, bombsPerPlane, cooldownS;
    /** height above the target (m) and true airspeed (m/s) of the bomb run */
    public final double altM, speedMs;
    /** lateral distance between aircraft (m), along-track bomb spacing (m), unused */
    public final float lateralM, spacingM, spare;

    StrikeType(StrikePlaneEntity.Kind plane, int planes, int perPlane, int cooldownS, double altM, double speedMs, float lateralM, float spacingM, float spare) {
        this.plane = plane;
        this.planes = planes;
        this.bombsPerPlane = perPlane;
        this.cooldownS = cooldownS;
        this.altM = altM;
        this.speedMs = speedMs;
        this.lateralM = lateralM;
        this.spacingM = spacingM;
        this.spare = spare;
    }

    public AirBombType bomb() {
        return switch (this) {
            case FAB500 -> AirBombType.FAB500;
            case FAB1000 -> AirBombType.FAB1000;
            case FAB5000 -> AirBombType.FAB5000;
            default -> null;
        };
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static StrikeType byOrdinal(int i) {
        StrikeType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
