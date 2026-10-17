package ru.aiforge.realwar.craft;

/** Weapons carried by helicopters, boats and the submarine (reply 6). */
public enum CraftWeapon {
    /** 2A42 30 mm (Ka-52), 3UOF8 HE-I, 550 rds/min selectable -> 1 round / 2 ticks. */
    GUN_2A42("2a42", Type.CANNON, 2, 1),
    /** M230 30x113 chain gun (AH-64), M789 HEDP, 625 rds/min, slaved to the crew's line of sight (IHADSS). */
    GUN_M230("m230", Type.CANNON, 2, 1),
    /** 9K121 Vikhr: supersonic laser beam-riding ATGM (Ka-52), flies along the operator's sight line. */
    VIKHR("vikhr", Type.ATGM_BEAM, 30, 1),
    /** AGM-114 Hellfire: semi-active laser homing on the spot the gunner designates (AH-64), lofted trajectory. */
    HELLFIRE("hellfire", Type.ATGM_SAL, 30, 1),
    /** S-8KOM 80 mm unguided rockets from B-8V20 pods (Ka-52). */
    S8("s8", Type.ROCKET, 2, 1),
    /** Hydra 70 (Mk66 + M151) unguided rockets from M261 pods (AH-64). */
    HYDRA("hydra", Type.ROCKET, 2, 1),
    /** 14.5 mm KPVT heavy machine gun (Raptor remote weapon station). */
    MG_KPV("kpv", Type.MG, 2, 1),
    /** 12.7 mm M2HB in a remote weapon station (CB90). */
    MG_M2("m2", Type.MG, 2, 1),
    /** 533 mm homing torpedo (TEST-71M class) from the bow tubes of the pr. 636.3 submarine. */
    TORPEDO("torpedo", Type.TORPEDO, 60, 1),
    /** IR decoy flares (UV-26 / M130 dispensers): a pair per press, seduce IR-homing missiles (Igla). */
    FLARES("flares", Type.FLARE, 6, 2),
    /** 3M-14 Kalibr cruise missile, launched from the torpedo tubes at periscope depth or deeper (pr. 636.3). */
    KALIBR("kalibr", Type.CRUISE, 80, 1),
    // ---- battleship Bismarck (reply 10): per-gun reload timers live in BattleshipEntity, interval = trigger repeat
    /** 38 cm SK C/34, Psgr. L/4.4 (m.Hb.) armour-piercing, 800 kg, 820 m/s, 36.5 km at 30 deg. Half-salvos. */
    SK38_AP("sk38_ap", Type.NAVAL, 10, 1),
    /** 38 cm SK C/34, Sprgr. L/4.5 Bdz. high-explosive, 800 kg, 64 kg burster. */
    SK38_HE("sk38_he", Type.NAVAL, 10, 1),
    /** 15 cm SK C/28 secondary battery (6 twin turrets), 45.3 kg HE, 875 m/s, 23 km. */
    SK15("sk15", Type.NAVAL, 6, 1),
    /** 10.5 cm SK C/33 heavy flak (8 twin mounts), time-fuzed, fired automatically at air targets. */
    FLAK105("flak105", Type.FLAK, 1, 1),
    /** 3.7 cm SK C/30 (8 twin mounts), automatic. */
    FLAK37("flak37", Type.FLAK, 1, 1),
    /** 2 cm Flak 30/38 singles and the two Flakvierling 38 quadruple mounts, automatic. */
    FLAK20("flak20", Type.FLAK, 1, 1),
    /** Arado Ar 196 A-3 floatplane launched from the athwartships catapult (4 carried). */
    ARADO("arado", Type.PLANE, 40, 1),
    // ---- WW2 aircraft (reply 11). interval = ticks between rounds of the whole station (all barrels together)
    /** 20 mm MG 151/20 motor cannon firing through the propeller hub (Bf 109 G-6), ~700 rds/min. */
    MG151("mg151", Type.CANNON, 2, 1),
    /** Two synchronised 13 mm MG 131 over the engine (Bf 109 G-6), 2 x ~900 rds/min. */
    MG131("mg131", Type.MG, 1, 1),
    /** 20 mm ShVAK motor cannon (Yak-3), ~800 rds/min. */
    SHVAK("shvak", Type.CANNON, 2, 1),
    /** Two synchronised 12.7 mm UBS (Yak-3), 2 x ~800 rds/min. */
    UBS("ubs", Type.MG, 1, 1),
    /** 20 mm MG FF/M in the ventral gondola nose (He 111 H-6), fired by the pilot along the axis. */
    MGFF("mgff", Type.CANNON, 3, 1),
    /** SC 250 general purpose bombs, 8 hung nose-up in the He 111 internal cells. */
    SC250("sc250", Type.BOMB, 5, 1),
    /** SC 1000 "Hermann" on the two external ETC racks of the He 111. */
    SC1000("sc1000", Type.BOMB, 12, 1),
    /** FAB-250 sv in the Pe-8 bay (16). */
    FAB250W("fab250sv", Type.BOMB, 4, 1),
    /** FAB-1000 in the Pe-8 bay (4). */
    FAB1000W("fab1000", Type.BOMB, 10, 1),
    /** FAB-5000NG, the Pe-8 alone of the VVS bombers could carry it (1). */
    FAB5000W("fab5000w", Type.BOMB, 20, 1),
    /** Ammunition of the defensive gunners (MG 15 / MG 131 / MG 81Z, ShKAS / UBT / ShVAK turrets), fired automatically. */
    GUNNERS("gunners", Type.FLAK, 1, 1),
    /** r12 Su-57: GSh-30-1 30 mm automatic cannon (1800 rpm, 150 rounds) in the right wing root. */
    GSH301("gsh301", Type.CANNON, 1, 1),
    /** r12 Su-57: Kh-38ML guided air-to-surface missile - follows the pilot's line of sight (beam / laser spot). */
    KH38("kh38", Type.ATGM_BEAM, 24, 1),
    /** r12 Su-57: FAB-500M-62 general purpose bombs, internal bays + inner pylons. */
    FAB500S("fab500s", Type.BOMB, 6, 1);

    public enum Type { CANNON, MG, ATGM_BEAM, ATGM_SAL, ROCKET, TORPEDO, FLARE, CRUISE, NAVAL, FLAK, PLANE, BOMB }

    public final String id;
    public final Type type;
    /** Minimum ticks between shots (per weapon). */
    public final int interval;
    /** Rounds per trigger event. */
    public final int perShot;

    CraftWeapon(String id, Type type, int interval, int perShot) {
        this.id = id;
        this.type = type;
        this.interval = interval;
        this.perShot = perShot;
    }

    public boolean guided() {
        return type == Type.ATGM_BEAM || type == Type.ATGM_SAL || type == Type.TORPEDO || type == Type.CRUISE;
    }

    public String langKey() {
        return "craft.aiarsenal.weapon." + id;
    }
}
