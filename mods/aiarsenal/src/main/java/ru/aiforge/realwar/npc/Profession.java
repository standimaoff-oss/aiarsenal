package ru.aiforge.realwar.npc;

/**
 * A trade the player can give a recruited villager (3 emeralds, see {@link VillagerRecruit}).
 * Most professions are a {@link Rank} plus a habit: the medic heals, the AA gunner looks for a flak gun,
 * the artilleryman and the tankist crew guns and hulls, the pilot takes an aircraft.
 */
public enum Profession {
    ASSAULT("assault", Rank.PRIVATE),
    MEDIC("medic", Rank.PRIVATE),
    ARTILLERY("artillery", Rank.CREW),
    TANK_CREW("tank_crew", Rank.CREW),
    AA_GUNNER("aa_gunner", Rank.CREW),
    PILOT("pilot", Rank.PILOT),
    COMMANDER("commander", Rank.COMMANDER);

    public final String id;
    public final Rank rank;

    Profession(String id, Rank rank) {
        this.id = id;
        this.rank = rank;
    }

    public String langKey() {
        return "npc.aiarsenal.profession." + this.id;
    }

    public static Profession byId(String id) {
        for (Profession p : values()) if (p.id.equalsIgnoreCase(id)) return p;
        return null;
    }
}
