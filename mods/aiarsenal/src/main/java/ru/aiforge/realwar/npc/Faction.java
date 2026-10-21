package ru.aiforge.realwar.npc;

import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.vehicle.VehicleKind;

/**
 * Armies that field AI soldiers. Every faction only draws equipment, uniforms, ground vehicles and aircraft of
 * its own nation and era; boarding anything else is refused by {@link NpcGear}.
 */
public enum Faction {
    NATO("nato", 0x4C7BB5),
    RUSSIA("russia", 0xB5433B),
    GERMANY("germany", 0x6B6F5A),
    FRANCE("france", 0x3E5C9A),
    /** Villagers recruited and equipped by a player: his private army, hostile to every AI faction. */
    PLAYER("player", 0x3FA34D);

    public final String id;
    public final int colour;

    Faction(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    /** Can this army be raised for the era? */
    public boolean allows(Era era) {
        return switch (this) {
            case NATO -> era == Era.MODERN;
            case GERMANY -> era == Era.WW2;
            case FRANCE -> era == Era.NAPOLEONIC;
            case PLAYER -> true;              // a player's recruits take whatever period he equips them for
            case RUSSIA -> true;              // Russian Empire 1812, USSR 1943, Russian Federation today
        };
    }

    public boolean hostileTo(Faction other) {
        return other != null && other != this;
    }

    public String langKey() {
        return "npc.aiarsenal.faction." + this.id;
    }

    /** Weapon item id (namespace aiarsenal) carried by the rank in this era. */
    public String weaponId(Era era, Rank rank) {
        return switch (this) {
            case NATO -> "m4a1";
            case RUSSIA -> switch (era) {
                case MODERN -> rank == Rank.SERGEANT ? "pkp" : "ak12";
                case WW2 -> rank == Rank.SERGEANT ? "ppsh41" : rank == Rank.OFFICER ? "tt33" : "mosin";
                case NAPOLEONIC -> rank == Rank.OFFICER ? "pistol_1809" : "musket_1808";
            };
            case GERMANY -> rank == Rank.SERGEANT ? "mp40" : rank == Rank.OFFICER ? "c96" : "k98k";
            case PLAYER -> rank == Rank.COMMANDER || rank == Rank.OFFICER ? "tt33" : "ak12";
            case FRANCE -> rank == Rank.OFFICER ? "pistol_an13" : "musket_1777";
        };
    }

    /** Cold steel for the bayonet charge / close quarters. */
    public String meleeId(Era era) {
        if (era != Era.NAPOLEONIC) return "";
        return this == Faction.FRANCE ? "sabre_briquet" : this == Faction.RUSSIA ? "sabre_1809" : "";
    }

    /** Uniform set of the nation and era (see {@link ru.aiforge.realwar.item.UniformItem#SETS}). */
    public String uniformSet(Era era, Rank rank) {
        return switch (this) {
            case NATO -> switch (rank) {
                case SERGEANT -> "us_sergeant";
                case OFFICER -> "us_captain";
                default -> "us_private";
            };
            case RUSSIA -> switch (era) {
                case MODERN -> switch (rank) {
                    case SERGEANT -> "ru_sergeant";
                    case OFFICER -> "ru_captain";
                    case PILOT -> "ru_pilot";
                    case CREW -> "ru_vdv";
                    default -> "ru_private";
                };
                case WW2 -> switch (rank) {
                    case OFFICER -> "ww2_su_officer";
                    case CREW -> "ww2_su_tanker";
                    case PILOT -> "ww2_su_pilot";
                    default -> "ww2_su_infantry";
                };
                case NAPOLEONIC -> rank == Rank.OFFICER ? "nap_ri_officer" : "nap_ri_infantry";
            };
            case GERMANY -> switch (rank) {
                case OFFICER -> "ww2_de_officer";
                case CREW -> "ww2_de_tanker";
                case PILOT -> "ww2_de_pilot";
                default -> "ww2_de_infantry";
            };
            case PLAYER -> switch (rank) {
                case SERGEANT -> "us_sergeant";
                case OFFICER, COMMANDER -> "us_captain";
                case PILOT -> "ru_pilot";
                case CREW -> "ru_vdv";
                default -> "us_private";
            };
            case FRANCE -> rank == Rank.OFFICER ? "nap_fr_officer" : "nap_fr_infantry";
        };
    }

    public VehicleKind[] vehicles(Era era) {
        return switch (this) {
            case NATO -> era == Era.MODERN
                    ? new VehicleKind[]{VehicleKind.ABRAMS, VehicleKind.BRADLEY, VehicleKind.M777}
                    : new VehicleKind[0];
            case RUSSIA -> switch (era) {
                case WW2 -> new VehicleKind[]{VehicleKind.T34, VehicleKind.KV2, VehicleKind.IS2, VehicleKind.KV5, VehicleKind.BM13};
                case MODERN -> new VehicleKind[]{VehicleKind.T90M, VehicleKind.BMP3, VehicleKind.MSTA, VehicleKind.TOS1A,
                        VehicleKind.GRAD, VehicleKind.SMERCH, VehicleKind.ZU23};
                case NAPOLEONIC -> new VehicleKind[0];
            };
            case GERMANY -> era == Era.WW2
                    ? new VehicleKind[]{VehicleKind.PZ4, VehicleKind.TIGER, VehicleKind.STURMTIGER, VehicleKind.PZ2, VehicleKind.MAUS}
                    : new VehicleKind[0];
            case PLAYER -> era == Era.MODERN
                    ? new VehicleKind[]{VehicleKind.ABRAMS, VehicleKind.BRADLEY, VehicleKind.ZU23, VehicleKind.M777}
                    : new VehicleKind[0];
            case FRANCE -> new VehicleKind[0];
        };
    }

    public CraftKind[] aircraft(Era era) {
        return switch (this) {
            case NATO -> era == Era.MODERN ? new CraftKind[]{CraftKind.AH64} : new CraftKind[0];
            case RUSSIA -> switch (era) {
                case MODERN -> new CraftKind[]{CraftKind.KA52, CraftKind.SU57};
                case WW2 -> new CraftKind[]{CraftKind.YAK3, CraftKind.PE8};
                case NAPOLEONIC -> new CraftKind[0];
            };
            case GERMANY -> era == Era.WW2 ? new CraftKind[]{CraftKind.BF109, CraftKind.HE111} : new CraftKind[0];
            case PLAYER -> era == Era.MODERN ? new CraftKind[]{CraftKind.AH64} : new CraftKind[0];
            case FRANCE -> new CraftKind[0];
        };
    }

    public CraftKind aircraft(Era era, boolean helicopter) {
        for (CraftKind k : aircraft(era)) {
            if (k.isHeli() == helicopter) return k;
        }
        return null;
    }

    /** Napoleonic powder gun of this army: France fields the 6-pounder, Russia the 12-pounder. */
    public boolean ownsCannon(boolean frenchCannon) {
        return this == Faction.FRANCE ? frenchCannon : this == Faction.RUSSIA ? !frenchCannon : false;
    }

    public static Faction byId(String id) {
        for (Faction f : values()) {
            if (f.id.equalsIgnoreCase(id)) return f;
        }
        return null;
    }
}
