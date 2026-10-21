package ru.aiforge.realwar.npc;

import java.util.List;

/**
 * The spawn-egg catalogue of the AI armies, in the order the eggs lie in the single Troops tab:
 * France 1812, the Russian Empire, the Wehrmacht, the USSR, NATO, and modern Russia.
 * Every army has its own rifleman egg plus one egg per profession — its own commanders,
 * assault troops, medics, artillerymen, tank crews, AA gunners and pilots.
 */
public final class NpcEggs {
    public record Army(String key, Faction faction, Era era, String eggId, int bg, int hl) {
    }

    public static final List<Army> ARMIES = List.of(
            new Army("fr", Faction.FRANCE, Era.NAPOLEONIC, "soldier_france_spawn_egg", 0x3E5C9A, 0xEDE6D6),
            new Army("ri", Faction.RUSSIA, Era.NAPOLEONIC, "soldier_rus_empire_spawn_egg", 0x44543C, 0xC9A86A),
            new Army("de", Faction.GERMANY, Era.WW2, "soldier_germany_spawn_egg", 0x5A5F50, 0xB8B29C),
            new Army("su", Faction.RUSSIA, Era.WW2, "soldier_ussr_spawn_egg", 0x6E7A57, 0xC8B99A),
            new Army("nato", Faction.NATO, Era.MODERN, "soldier_nato_spawn_egg", 0x4C7BB5, 0xD9D2C3),
            new Army("ru", Faction.RUSSIA, Era.MODERN, "soldier_russia_spawn_egg", 0xB5433B, 0xE3D8C4));

    /** Professions in tab order: commander first, then the fighting trades. */
    public static final List<Profession> PROFESSIONS = List.of(
            Profession.COMMANDER, Profession.ASSAULT, Profession.MEDIC, Profession.ARTILLERY,
            Profession.TANK_CREW, Profession.AA_GUNNER, Profession.PILOT);

    public static String profEggId(Army a, Profession p) {
        return "egg_" + a.key() + "_" + p.id;
    }

    private NpcEggs() {
    }
}
